package dev.kultivator.pusakalangit;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.Cultivators;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.kehancuran.WorldBreaker;
import dev.kultivator.network.Payloads;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The saved registry of planted Titah weapons ({@code kultivator:titah_monuments} in the overworld data, DESIGN 29.6):
 * one record per {@link TitahMonumentEntity} (owner, place, model, born, expires, the rewarded and test flags) plus the
 * Batu Pusaka / Urat Pusaka cells of its block monument, and the pending clear requests of block monuments whose weapon
 * left. Records expire by game time even when their chunk is never loaded again (stale records never block the caps);
 * the per-player ({@code titahMonumentsPerPlayer}) and per-world ({@code titahMonumentsPerWorld}) caps revoke the oldest.
 * A clear request stays saved until its WorldBreaker clear job has finished, so a restart (which loses the job queue and
 * the in-memory undo window) never leaves an orphan monument behind. Server only.
 */
public final class TitahMonuments extends SavedData {

	public enum Status { LIVE, EXPIRED, REVOKED, MISSING }

	public static final Codec<TitahMonuments> CODEC = CompoundTag.CODEC.xmap(TitahMonuments::new, TitahMonuments::write);
	public static final SavedDataType<TitahMonuments> TYPE = new SavedDataType<>(Kultivator.id("titah_monuments"), TitahMonuments::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** A pull-out is rewarded only from this age (29.6.2). */
	public static final int REWARD_AGE = 600;
	/** The pull-out reward's qi refill (share of max qi). */
	public static final double REWARD_QI = 0.20D;
	/** Pending clears are (re)submitted at most this often. */
	private static final int CLEAR_RETRY_TICKS = 100;

	/** One planted weapon. */
	static final class Planted {
		int id;
		UUID owner;
		String ownerName = "";
		ResourceKey<Level> dimension = Level.OVERWORLD;
		BlockPos pos = BlockPos.ZERO;
		@Nullable UUID entity;
		byte model;
		float scale;
		long born;
		long expires;
		boolean rewarded;
		boolean test;
		boolean ultimate;
		boolean revoked;
		long[] blocks = new long[0];
		byte[] kinds = new byte[0];
		// ---- Phase 10b Tingkat (DESIGN 29.16.11, C10b.9): what far clients need to draw it
		double x;
		double y;
		double z;
		float yaw;
		float tilt;
		int tier = 1;
		/** Session only: the entity's last state (0 settling, 1 standing, 2 pulling, 3 leaving). */
		byte state = TitahMonumentEntity.STATE_STANDING;
	}

	/** Far monuments of one dimension are sent at most this many (contract C10b.9). */
	public static final int FAR_MAX = 24;
	/** Dimensions whose far list changed this tick (sent once at the end of the tick). */
	private static final java.util.Set<ResourceKey<Level>> FAR_DIRTY = new java.util.HashSet<>();
	/** The dimension each player was last sent a far list for (checked every 100 ticks). */
	private static final Map<UUID, ResourceKey<Level>> FAR_SENT = new java.util.HashMap<>();

	/** A block monument to clear (its weapon left); saved until its clear job finished. */
	static final class Clear {
		int id;
		UUID owner;
		ResourceKey<Level> dimension = Level.OVERWORLD;
		long[] positions = new long[0];
		byte[] kinds = new byte[0];
		/** Session only: a clear job runs for it now. */
		boolean inFlight;
		long nextTry;
	}

	private final Map<Integer, Planted> records = new LinkedHashMap<>();
	private final List<Clear> clears = new ArrayList<>();
	private int nextId = 1;
	private int nextClear = 1;

	public TitahMonuments() {
	}

	private TitahMonuments(CompoundTag tag) {
		nextId = Math.max(1, tag.getIntOr("next", 1));
		nextClear = Math.max(1, tag.getIntOr("next_clear", 1));
		for (Tag entry : tag.getListOrEmpty("records")) {
			if (!(entry instanceof CompoundTag r)) {
				continue;
			}
			try {
				Planted record = new Planted();
				record.id = r.getIntOr("id", -1);
				record.owner = UUID.fromString(r.getStringOr("owner", ""));
				record.ownerName = r.getStringOr("name", "");
				record.dimension = dimension(r.getStringOr("dim", "minecraft:overworld"));
				record.pos = new BlockPos(r.getIntOr("x", 0), r.getIntOr("y", 0), r.getIntOr("z", 0));
				String entity = r.getStringOr("entity", "");
				record.entity = entity.isEmpty() ? null : UUID.fromString(entity);
				record.model = r.getByteOr("model", (byte) 0);
				record.scale = r.getFloatOr("scale", 12.0F);
				record.born = r.getLongOr("born", 0L);
				record.expires = r.getLongOr("expires", 0L);
				record.rewarded = r.getBooleanOr("rewarded", false);
				record.test = r.getBooleanOr("test", false);
				record.ultimate = r.getBooleanOr("ultimate", false);
				record.revoked = r.getBooleanOr("revoked", false);
				record.blocks = r.getLongArray("blocks").orElse(new long[0]);
				record.kinds = r.getByteArray("kinds").orElse(new byte[0]);
				record.x = r.getDoubleOr("fx", record.pos.getX() + 0.5D);
				record.y = r.getDoubleOr("fy", record.pos.getY());
				record.z = r.getDoubleOr("fz", record.pos.getZ() + 0.5D);
				record.yaw = r.getFloatOr("yaw", 0.0F);
				record.tilt = r.getFloatOr("tilt", record.model == TitahMonumentEntity.MODEL_PILLAR ? 0.0F : 10.0F);
				record.tier = Math.max(1, Math.min(9, r.getIntOr("tier", 1)));
				if (record.id >= 0) {
					records.put(record.id, record);
					nextId = Math.max(nextId, record.id + 1);
				}
			} catch (RuntimeException ignored) {
				// a broken entry is dropped
			}
		}
		for (Tag entry : tag.getListOrEmpty("clears")) {
			if (!(entry instanceof CompoundTag c)) {
				continue;
			}
			try {
				Clear clear = new Clear();
				clear.id = c.getIntOr("id", nextClear++);
				clear.owner = UUID.fromString(c.getStringOr("owner", ""));
				clear.dimension = dimension(c.getStringOr("dim", "minecraft:overworld"));
				clear.positions = c.getLongArray("positions").orElse(new long[0]);
				clear.kinds = c.getByteArray("kinds").orElse(new byte[0]);
				if (clear.positions.length > 0) {
					clears.add(clear);
					nextClear = Math.max(nextClear, clear.id + 1);
				}
			} catch (RuntimeException ignored) {
				// a broken entry is dropped
			}
		}
	}

	private static ResourceKey<Level> dimension(String id) {
		return ResourceKey.create(Registries.DIMENSION, Identifier.parse(id));
	}

	private CompoundTag write() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("next", nextId);
		tag.putInt("next_clear", nextClear);
		ListTag list = new ListTag();
		for (Planted record : records.values()) {
			CompoundTag r = new CompoundTag();
			r.putInt("id", record.id);
			r.putString("owner", record.owner.toString());
			r.putString("name", record.ownerName);
			r.putString("dim", record.dimension.identifier().toString());
			r.putInt("x", record.pos.getX());
			r.putInt("y", record.pos.getY());
			r.putInt("z", record.pos.getZ());
			r.putString("entity", record.entity == null ? "" : record.entity.toString());
			r.putByte("model", record.model);
			r.putFloat("scale", record.scale);
			r.putLong("born", record.born);
			r.putLong("expires", record.expires);
			r.putBoolean("rewarded", record.rewarded);
			r.putBoolean("test", record.test);
			r.putBoolean("ultimate", record.ultimate);
			r.putBoolean("revoked", record.revoked);
			r.putLongArray("blocks", record.blocks);
			r.putByteArray("kinds", record.kinds);
			r.putDouble("fx", record.x);
			r.putDouble("fy", record.y);
			r.putDouble("fz", record.z);
			r.putFloat("yaw", record.yaw);
			r.putFloat("tilt", record.tilt);
			r.putInt("tier", record.tier);
			list.add(r);
		}
		tag.put("records", list);
		ListTag clearList = new ListTag();
		for (Clear clear : clears) {
			CompoundTag c = new CompoundTag();
			c.putInt("id", clear.id);
			c.putString("owner", clear.owner.toString());
			c.putString("dim", clear.dimension.identifier().toString());
			c.putLongArray("positions", clear.positions);
			c.putByteArray("kinds", clear.kinds);
			clearList.add(c);
		}
		tag.put("clears", clearList);
		return tag;
	}

	public static TitahMonuments get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	// ================================================================== planting (29.6.1, caps 29.6.2)

	/**
	 * Plants a weapon at {@code at} for {@code owner} and records it; beyond a cap the oldest record (of the player, then
	 * of the world) is revoked first. Returns the record id, or -1 when it could not be spawned.
	 */
	static int create(ServerLevel level, ServerPlayer owner, byte model, Vec3 at, float yaw, float scale, float tilt, boolean test, boolean ultimate) {
		return create(level, owner, model, at, yaw, scale, tilt, test, ultimate, 1);
	}

	/** As above at Tingkat {@code tier} (29.16.11: synced to the entity and to far clients). */
	static int create(ServerLevel level, ServerPlayer owner, byte model, Vec3 at, float yaw, float scale, float tilt, boolean test, boolean ultimate,
			int tier) {
		MinecraftServer server = level.getServer();
		TitahMonuments data = get(server);
		KultivatorConfig config = KultivatorConfig.get();
		long now = now(server);
		data.revokeOver(server, owner.getUUID(), config.titahMonumentsPerPlayer - 1, now);
		data.revokeOver(server, null, config.titahMonumentsPerWorld - 1, now);
		TitahMonumentEntity entity = TitahItems.MONUMENT.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
		if (entity == null) {
			return -1;
		}
		int id = data.nextId++;
		long expires = now + config.titahMonumentDays * 24000L;
		entity.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		entity.setYRot(yaw);
		int t = Math.max(1, Math.min(9, tier));
		entity.setup(id, owner.getUUID(), owner.getName().getString(), model, scale, tilt, now, expires, test, ultimate, t);
		if (!level.addFreshEntity(entity)) {
			return -1;
		}
		Planted record = new Planted();
		record.id = id;
		record.owner = owner.getUUID();
		record.ownerName = owner.getName().getString();
		record.dimension = level.dimension();
		record.pos = BlockPos.containing(at);
		record.entity = entity.getUUID();
		record.model = model;
		record.scale = scale;
		record.born = now;
		record.expires = expires;
		record.test = test;
		record.ultimate = ultimate;
		record.x = at.x;
		record.y = at.y;
		record.z = at.z;
		record.yaw = yaw;
		record.tilt = tilt;
		record.tier = t;
		record.state = TitahMonumentEntity.STATE_SETTLING;
		data.records.put(id, record);
		data.setDirty();
		markFar(record.dimension);
		return id;
	}

	/**
	 * Phase 10b (DESIGN 29.0, 28.10 amended): with {@code titahUltimateMonuments} (default false) the Dewa sword
	 * ultimates whose Dampak monument is {@code heaven_punishing_sword} or {@code zhuxian_sword} leave a planted weapon
	 * entity at their aim too. Never a reward, same caps and expiry; any other monument id stays a client prop only.
	 */
	public static void ultimateMonument(ServerPlayer owner, String monument, float scale, Vec3 aim) {
		KultivatorConfig config = KultivatorConfig.get();
		if (!config.titahUltimateMonuments || !config.titahEnabled || monument == null) {
			return;
		}
		byte model = TitahMonumentEntity.MODELS[TitahMonumentEntity.MODEL_PUNISHING].equals(monument) ? TitahMonumentEntity.MODEL_PUNISHING
				: TitahMonumentEntity.MODELS[TitahMonumentEntity.MODEL_ZHUXIAN].equals(monument) ? TitahMonumentEntity.MODEL_ZHUXIAN : -1;
		if (model < 0) {
			return;
		}
		try {
			ServerLevel level = owner.level();
			BlockPos column = BlockPos.containing(aim);
			if (!level.isLoaded(column)) {
				return;
			}
			int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
			Vec3 at = Math.abs(top - aim.y) > 48.0D ? aim : new Vec3(aim.x, top, aim.z);
			float yaw = (float) Math.toDegrees(Math.atan2(-(owner.getX() - at.x), owner.getZ() - at.z));
			create(level, owner, model, at, yaw, Math.max(4.0F, Math.min(24.0F, scale)), 10.0F, false, true);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kultivator: ultimate monument failed", exception);
		}
	}

	/** Revokes the oldest live records of {@code owner} (null = everyone) until at most {@code keep} are left. */
	private void revokeOver(MinecraftServer server, @Nullable UUID owner, int keep, long now) {
		List<Planted> live = new ArrayList<>();
		for (Planted record : records.values()) {
			if (!record.revoked && now < record.expires && (owner == null || owner.equals(record.owner))) {
				live.add(record);
			}
		}
		live.sort(Comparator.comparingLong((Planted record) -> record.born).thenComparingInt(record -> record.id));
		for (int i = 0; i < live.size() - Math.max(0, keep); i++) {
			revoke(server, live.get(i));
		}
	}

	/** A loaded weapon leaves at once, an unloaded one when its chunk loads; its block monument is cleared now. */
	private void revoke(MinecraftServer server, Planted record) {
		if (record.revoked) {
			return;
		}
		record.revoked = true;
		markFar(record.dimension);
		queueClear(record);
		TitahMonumentEntity entity = loaded(server, record);
		if (entity != null) {
			entity.leave();
		}
		ServerPlayer player = server.getPlayerList().getPlayer(record.owner);
		if (player != null) {
			Msg.chat(player, Component.translatable("kultivator.pusaka_langit.monument.revoked", record.pos.getX(), record.pos.getY(), record.pos.getZ())
					.withStyle(ChatFormatting.GRAY));
		}
		setDirty();
	}

	private static @Nullable TitahMonumentEntity loaded(MinecraftServer server, Planted record) {
		if (record.entity == null) {
			return null;
		}
		ServerLevel level = server.getLevel(record.dimension);
		Entity entity = level == null ? null : level.getEntity(record.entity);
		return entity instanceof TitahMonumentEntity monument && !monument.isRemoved() ? monument : null;
	}

	// ================================================================== the entity's questions

	/** True while the record stands (not revoked, not expired): the carve job may still build its block monument. */
	public static boolean live(MinecraftServer server, int id) {
		if (id < 0) {
			return false;
		}
		Planted record = get(server).records.get(id);
		return record != null && !record.revoked && now(server) < record.expires;
	}

	/** The state of a weapon's record (checked by the entity every 100 ticks). */
	static Status status(MinecraftServer server, int id, UUID entity, long gameTime) {
		Planted record = get(server).records.get(id);
		if (record == null || record.entity != null && !record.entity.equals(entity)) {
			return Status.MISSING;
		}
		if (record.revoked) {
			return Status.REVOKED;
		}
		return gameTime >= record.expires ? Status.EXPIRED : Status.LIVE;
	}

	/**
	 * The weapon left for good (pulled, expired, revoked, {@code /kill}): drops the record and queues its block monument's
	 * clear. Idempotent: a second call (our own {@code discard()} after a pull also arrives as DISCARDED) does nothing.
	 */
	public static void release(MinecraftServer server, int id) {
		TitahMonuments data = get(server);
		Planted record = data.records.remove(id);
		if (record == null) {
			return;
		}
		markFar(record.dimension);
		data.queueClear(record);
		data.setDirty();
	}

	/**
	 * The owner pulled the weapon out (29.6.2): one modest gift if eligible (a real cast, age ≥ 600 ticks, not yet
	 * rewarded, under {@code titahRewardsPerDay} per game day, not an ultimate monument): 1 high spirit stone (made now) and
	 * 20 % of max qi. Then the PULL beat and the release. True when the gift was given.
	 */
	static boolean pulled(TitahMonumentEntity entity, ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		TitahMonuments data = get(server);
		Planted record = data.records.get(entity.recordId());
		long now = now(server);
		boolean gift = false;
		if (record != null && !record.rewarded && !record.test && !record.ultimate && !record.revoked && !entity.test() && !entity.ultimate()
				&& !entity.rewarded() && now - record.born >= REWARD_AGE && TitahSettings.takeReward(player, now)) {
			record.rewarded = true;
			gift = true;
			TitahItems.give(player, SpiritStones.stack(SpiritStones.HIGH, 1));
			Qi.restore(player, Qi.max(Cultivators.get(player)) * REWARD_QI);
		}
		Advancements.grant(player, "titah_pull");
		Vec3 at = entity.position();
		EpicHooks.extra(player.level(), EpicIds.EXTRA_TITAH, player, player, at, Math.max(0, Math.min(3, entity.model())), EpicIds.TITAH_PULL);
		player.level().playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.4F, 1.2F);
		Msg.actionBar(player, Component.translatable(gift ? "kultivator.pusaka_langit.pull.done_gift" : "kultivator.pusaka_langit.pull.done")
				.withColor(0xFFF4D0));
		release(server, entity.recordId());
		return gift;
	}

	/** The carve job finished (29.7.1 DONE callback): the planted weapon re-seats onto the finished crater. */
	public static void onJobDone(MinecraftServer server, int recordId, int jobId) {
		Planted record = recordId < 0 ? null : get(server).records.get(recordId);
		if (record == null) {
			return;
		}
		TitahMonumentEntity entity = loaded(server, record);
		if (entity != null) {
			entity.jobDone();
		}
	}

	/**
	 * The carve job built its block monument (29.6.3): the cells go into the saved record. When the record is gone
	 * already (pulled or revoked meanwhile), the cells are queued for clearing at once.
	 */
	public static void onMonumentPlaced(MinecraftServer server, int recordId, UUID owner, ResourceKey<Level> dimension, long[] positions,
			byte[] kinds) {
		if (positions == null || positions.length == 0) {
			return;
		}
		TitahMonuments data = get(server);
		Planted record = recordId < 0 ? null : data.records.get(recordId);
		if (record == null || record.revoked) {
			Clear clear = new Clear();
			clear.id = data.nextClear++;
			clear.owner = owner;
			clear.dimension = dimension;
			clear.positions = positions.clone();
			clear.kinds = kinds.clone();
			data.clears.add(clear);
		} else {
			record.blocks = concat(record.blocks, positions);
			record.kinds = concat(record.kinds, kinds);
		}
		data.setDirty();
	}

	/** A clear job finished: its request is done; what it could not reach (unloaded chunks) becomes a new request. */
	public static void onClearFinished(MinecraftServer server, int clearId, UUID owner, ResourceKey<Level> dimension, long[] left,
			BlockState[] leftStates) {
		TitahMonuments data = get(server);
		data.clears.removeIf(clear -> clear.id == clearId);
		if (left != null && left.length > 0) {
			Clear clear = new Clear();
			clear.id = data.nextClear++;
			clear.owner = owner;
			clear.dimension = dimension;
			clear.positions = left.clone();
			clear.kinds = new byte[left.length];
			for (int i = 0; i < left.length; i++) {
				clear.kinds[i] = i < leftStates.length && leftStates[i] != null && leftStates[i].is(TitahItems.URAT_PUSAKA) ? (byte) 1 : (byte) 0;
			}
			clear.nextTry = now(server) + CLEAR_RETRY_TICKS;
			data.clears.add(clear);
		}
		data.setDirty();
	}

	/**
	 * The owner of the standing block monument that has a cell at {@code pos} (a live, unrevoked record), or null for a free block.
	 * shortcut: a linear scan of the records' cells, only run when a Batu/Urat Pusaka is mined; index them if records grow into the thousands.
	 */
	static @Nullable UUID ownerAt(ServerLevel level, BlockPos pos) {
		long cell = pos.asLong();
		for (Planted record : get(level.getServer()).records.values()) {
			if (record.revoked || !record.dimension.equals(level.dimension())) {
				continue;
			}
			for (long block : record.blocks) {
				if (block == cell) {
					return record.owner;
				}
			}
		}
		return null;
	}

	private void queueClear(Planted record) {
		if (record.blocks.length == 0) {
			return;
		}
		Clear clear = new Clear();
		clear.id = nextClear++;
		clear.owner = record.owner;
		clear.dimension = record.dimension;
		clear.positions = record.blocks;
		clear.kinds = record.kinds;
		clears.add(clear);
		record.blocks = new long[0];
		record.kinds = new byte[0];
	}

	// ================================================================== upkeep (every 100 ticks)

	/** Prunes expired records (their block monuments are queued for clearing) and submits the pending clears. */
	static void tick(MinecraftServer server) {
		tickFar(server);
		if (server.getTickCount() % CLEAR_RETRY_TICKS != 7) {
			return;
		}
		TitahMonuments data = get(server);
		if (data.records.isEmpty() && data.clears.isEmpty()) {
			return;
		}
		long now = now(server);
		boolean dirty = false;
		for (Iterator<Planted> it = data.records.values().iterator(); it.hasNext(); ) {
			Planted record = it.next();
			if (now >= record.expires) {
				it.remove();
				markFar(record.dimension);
				data.queueClear(record);
				TitahMonumentEntity entity = loaded(server, record);
				if (entity != null) {
					entity.leave();
				}
				dirty = true;
			}
		}
		for (Clear clear : data.clears) {
			if (clear.inFlight || now < clear.nextTry) {
				continue;
			}
			BlockState[] states = new BlockState[clear.positions.length];
			for (int i = 0; i < states.length; i++) {
				states[i] = TitahItems.monumentState(i < clear.kinds.length ? clear.kinds[i] : 0);
			}
			if (WorldBreaker.submitClear(clear.owner, clear.dimension, clear.positions, states, clear.id)) {
				clear.inFlight = true;
			} else {
				clear.nextTry = now + CLEAR_RETRY_TICKS;
			}
		}
		if (dirty) {
			data.setDirty();
		}
	}

	// ================================================================== lists and tests

	/** One line per record: id, owner, model, position, days left, flags. */
	record View(int id, UUID owner, String ownerName, String dimension, BlockPos pos, byte model, double daysLeft, boolean revoked, boolean test,
			int blocks) {
	}

	static List<View> list(MinecraftServer server, @Nullable UUID owner) {
		long now = now(server);
		List<View> out = new ArrayList<>();
		for (Planted record : get(server).records.values()) {
			if (owner == null || owner.equals(record.owner)) {
				out.add(new View(record.id, record.owner, record.ownerName, record.dimension.identifier().toString(), record.pos, record.model,
						Math.max(0.0D, (record.expires - now) / 24000.0D), record.revoked, record.test, record.blocks.length));
			}
		}
		return out;
	}

	/** {@code monumen hapus_semua}: every weapon leaves, every block monument is queued for clearing. Returns how many. */
	static int removeAll(MinecraftServer server) {
		TitahMonuments data = get(server);
		List<Planted> all = new ArrayList<>(data.records.values());
		for (Planted record : all) {
			TitahMonumentEntity entity = loaded(server, record);
			data.records.remove(record.id);
			markFar(record.dimension);
			data.queueClear(record);
			if (entity != null) {
				entity.leave();
			}
		}
		data.setDirty();
		return all.size();
	}

	/** {@code monumen umur <hari>}: ages every record by {@code days} game days (an expiry test). */
	static int age(MinecraftServer server, int days) {
		TitahMonuments data = get(server);
		for (Planted record : data.records.values()) {
			record.born -= days * 24000L;
			record.expires -= days * 24000L;
			markFar(record.dimension);
		}
		data.setDirty();
		return data.records.size();
	}

	// ================================================================== far monuments (DESIGN 29.16.11, contract C10b.9)

	/** The entity's state changed (settling, standing, pulling, leaving): far clients hear of it. */
	static void noteState(MinecraftServer server, int id, byte state) {
		Planted record = get(server).records.get(id);
		if (record != null && record.state != state) {
			record.state = state;
			markFar(record.dimension);
		}
	}

	/** The entity re-seated onto a crater floor by at least 2 blocks: the record's exact place follows. */
	static void noteSeat(MinecraftServer server, int id, double x, double y, double z) {
		TitahMonuments data = get(server);
		Planted record = data.records.get(id);
		if (record == null) {
			return;
		}
		record.x = x;
		record.y = y;
		record.z = z;
		record.pos = BlockPos.containing(x, y, z);
		data.setDirty();
		markFar(record.dimension);
	}

	private static void markFar(ResourceKey<Level> dimension) {
		if (dimension != null) {
			FAR_DIRTY.add(dimension);
		}
	}

	/**
	 * Every tick: the dimensions whose list changed go out to all their players, and so does a player whose dimension
	 * differs from the last one sent (a join, a portal; checked every tick so a quick round trip is not missed, no event
	 * API needed).
	 */
	private static void tickFar(MinecraftServer server) {
		if (!FAR_DIRTY.isEmpty()) {
			List<ResourceKey<Level>> dims = new ArrayList<>(FAR_DIRTY);
			FAR_DIRTY.clear();
			for (ResourceKey<Level> dimension : dims) {
				ServerLevel level = server.getLevel(dimension);
				if (level == null || level.players().isEmpty()) {
					continue;
				}
				CompoundTag tag = farList(server, dimension);
				for (ServerPlayer player : level.players()) {
					Payloads.TitahFar.send(player, tag);
					FAR_SENT.put(player.getUUID(), dimension);
				}
			}
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ResourceKey<Level> dimension = player.level().dimension();
			if (!dimension.equals(FAR_SENT.get(player.getUUID()))) {
				Payloads.TitahFar.send(player, farList(server, dimension));
				FAR_SENT.put(player.getUUID(), dimension);
			}
		}
	}

	/**
	 * The {@code TitahFar} data of {@code dimension}: {@code m} = up to 24 live monuments (newest first) as
	 * {id, x, y, z, model, scale, yaw, tilt, state, born, tier}.
	 */
	static CompoundTag farList(MinecraftServer server, ResourceKey<Level> dimension) {
		long now = now(server);
		List<Planted> live = new ArrayList<>();
		for (Planted record : get(server).records.values()) {
			if (!record.revoked && now < record.expires && dimension.equals(record.dimension)) {
				live.add(record);
			}
		}
		live.sort(Comparator.comparingLong((Planted record) -> -record.born).thenComparingInt(record -> -record.id));
		ListTag list = new ListTag();
		for (int i = 0; i < live.size() && i < FAR_MAX; i++) {
			Planted record = live.get(i);
			CompoundTag m = new CompoundTag();
			m.putInt("id", record.id);
			m.putDouble("x", record.x);
			m.putDouble("y", record.y);
			m.putDouble("z", record.z);
			m.putByte("model", record.model);
			m.putFloat("scale", record.scale);
			m.putFloat("yaw", record.yaw);
			m.putFloat("tilt", record.tilt);
			m.putByte("state", record.state);
			m.putLong("born", record.born);
			m.putByte("tier", (byte) record.tier);
			list.add(m);
		}
		CompoundTag tag = new CompoundTag();
		tag.putString("dim", dimension.identifier().toString());
		tag.put("m", list);
		return tag;
	}

	/** Session cleanup (server stop, a player leaving). */
	static void forgetFar(@Nullable UUID player) {
		if (player == null) {
			FAR_DIRTY.clear();
			FAR_SENT.clear();
		} else {
			FAR_SENT.remove(player);
		}
	}

	/** {records, pending clears, block cells held} for {@code beban}. */
	static int[] counts(MinecraftServer server) {
		TitahMonuments data = get(server);
		int cells = 0;
		for (Planted record : data.records.values()) {
			cells += record.blocks.length;
		}
		return new int[]{data.records.size(), data.clears.size(), cells};
	}

	private static long[] concat(long[] a, long[] b) {
		long[] out = java.util.Arrays.copyOf(a, a.length + b.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}

	private static byte[] concat(byte[] a, byte[] b) {
		byte[] out = java.util.Arrays.copyOf(a, a.length + b.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}
}
