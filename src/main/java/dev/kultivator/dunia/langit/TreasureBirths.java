package dev.kultivator.dunia.langit;

import dev.kultivator.Kultivator;
import dev.kultivator.alam.Alam;
import dev.kultivator.ascension.Ascension;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.DuniaSpine;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.api.TreasureBirth;
import dev.kultivator.network.Net;
import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pertanda Harta Lahir (DESIGN 34.5.9): every {@code treasureDays} (± 1) in-game days per dimension with active players,
 * a treasure is born 300-900 blocks from a focus player (active, not busy, outdoors). The spot comes from noise sampling
 * only ({@code ChunkGenerator.getBaseHeight}, the {@code abadi/ArrivalIsland} method, at most 16 samples per tick, no
 * chunk is loaded): never in a town or Kota Abadi (live and saved {@code kota/*} sites), a sect territory or site, or water
 * deeper than 3. A 30-second tremor comes first, then the Pilar Pelangi stands for {@code durationTicks} or until
 * claimed; one live birth per dimension.
 * <p>
 * The treasure itself ({@link HartaLahirEntity}) exists only while a player is within 64 blocks; the first time it
 * appears its guardians may wake ({@code guardianChance}, {@link TreasureGuardians}: elite beasts of the tier that seal the
 * lotus while they live near it). Claim: right-click and
 * channel 5 seconds within 2 blocks (2.5 before it breaks); a hit or a step away breaks it. {@code TREASURE_BORN} fires at the birth,
 * {@code TREASURE_CLAIMED} when a player wins, a rival group takes it ({@link #claimByRival}) or it fades. Births are
 * memory-only (a live site of kind {@code langit/harta}); the next birth day is saved in {@link LangitData}.
 */
public final class TreasureBirths {

	public static final int TREMOR_TICKS = 600;
	public static final int TEST_TREMOR_TICKS = 100;
	public static final int CLAIM_TICKS = 100;
	public static final double CLAIM_RANGE = 2.0D;
	public static final double PRESENCE = 64.0D;
	public static final double ABSENCE = 80.0D;
	public static final double TIER_RANGE = 1000.0D;
	public static final double TREMOR_RANGE = 1000.0D;
	public static final int SAMPLES_PER_TICK = 16;
	public static final int MAX_CANDIDATES = 48;
	public static final int MAX_WATER_DEPTH = 3;
	public static final String GUARDIAN_TAG = "kultivator.harta_guardian";
	public static final String SITE_KIND = "langit/harta";

	/** A live birth. */
	static final class Birth {
		TreasureBirth record;
		final List<ItemStack> rewards;
		@Nullable HartaLahirEntity entity;
		boolean guardianDone;
		@Nullable UUID channeler;
		long channelStart;

		Birth(TreasureBirth record, List<ItemStack> rewards) {
			this.record = record;
			this.rewards = rewards;
		}

		ItemStack display() {
			return rewards.isEmpty() ? ItemStack.EMPTY : rewards.getFirst();
		}
	}

	/** The tremor before a birth. */
	private record Pending(BlockPos pos, long bornAt, int tier, @Nullable String kind, boolean pulsed) {
	}

	/** The spot search of a scheduled birth (or {@code uji harta_lahir jadwal}'s forced one). */
	private static final class Search {
		final UUID focus;
		int candidates;

		Search(UUID focus) {
			this.focus = focus;
		}
	}

	private static final Map<ResourceKey<Level>, Birth> LIVE = new HashMap<>();
	private static final Map<ResourceKey<Level>, Pending> PENDING = new HashMap<>();
	private static final Map<ResourceKey<Level>, Search> SEARCH = new HashMap<>();

	// ================================================================== the server tick

	/** Every server tick from {@link Langit}: the spot search, the tremor, the live births (every 5th tick), the schedule. */
	static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		if (!SEARCH.isEmpty()) {
			for (ServerLevel level : server.getAllLevels()) {
				Search search = SEARCH.get(level.dimension());
				if (search != null) {
					search(level, search);
				}
			}
		}
		if (!PENDING.isEmpty()) {
			for (ServerLevel level : server.getAllLevels()) {
				Pending pending = PENDING.get(level.dimension());
				if (pending != null) {
					tremor(level, pending);
				}
			}
		}
		if (tick % 5 == 2 && !LIVE.isEmpty()) {
			for (ServerLevel level : server.getAllLevels()) {
				Birth birth = LIVE.get(level.dimension());
				if (birth != null) {
					tickLive(level, birth);
				}
			}
		}
		if (tick % 100 == 61) {
			schedule(server);
		}
	}

	/** Every 100 ticks: starts the spot search of a dimension whose birth day has come. */
	private static void schedule(MinecraftServer server) {
		LangitConfig config = LangitConfig.get();
		if (!config.treasureBirth || !DuniaSpine.enabled() || DuniaBudget.overloaded(server)) {
			return;
		}
		LangitData data = LangitData.get(server);
		for (ServerLevel level : server.getAllLevels()) {
			if (!SkyEvents.eligible(level) || busy(level.dimension())) {
				continue;
			}
			LangitData.Dim dim = data.dim(SkyEvents.key(level));
			long day = Math.floorDiv(level.getOverworldClockTime(), (long) SkyEvents.DAY);
			if (dim.nextBirthDay < 0L) {
				dim.nextBirthDay = day + interval(config, level.getRandom());
				data.changed();
				continue;
			}
			if (day < dim.nextBirthDay) {
				continue;
			}
			ServerPlayer focus = focus(level);
			if (focus == null) {
				continue;
			}
			dim.nextBirthDay = day + interval(config, level.getRandom());
			data.changed();
			SEARCH.put(level.dimension(), new Search(focus.getUUID()));
		}
	}

	private static int interval(LangitConfig config, RandomSource random) {
		return Math.max(1, config.treasureDays - 1 + random.nextInt(3));
	}

	/** A random active player of {@code level} who is outdoors and not busy, or null. */
	private static @Nullable ServerPlayer focus(ServerLevel level) {
		List<ServerPlayer> candidates = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive() || busy(player) || !level.canSeeSky(player.blockPosition().above())) {
				continue;
			}
			candidates.add(player);
		}
		return candidates.isEmpty() ? null : candidates.get(level.getRandom().nextInt(candidates.size()));
	}

	private static boolean busy(ServerPlayer player) {
		return Alam.inRun(player) || Tribulation.isActive(player) || Ascension.isActive(player);
	}

	private static boolean busy(ResourceKey<Level> dim) {
		return LIVE.containsKey(dim) || PENDING.containsKey(dim) || SEARCH.containsKey(dim);
	}

	// ================================================================== the spot search (noise only)

	private static void search(ServerLevel level, Search search) {
		ServerPlayer focus = level.getServer().getPlayerList().getPlayer(search.focus);
		if (focus == null || focus.level() != level) {
			SEARCH.remove(level.dimension());
			return;
		}
		LangitConfig config = LangitConfig.get();
		RandomSource random = level.getRandom();
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState noise = level.getChunkSource().randomState();
		// two height samples per candidate
		for (int i = 0; i < SAMPLES_PER_TICK / 2; i++) {
			if (++search.candidates > MAX_CANDIDATES) {
				SEARCH.remove(level.dimension());
				Kultivator.LOGGER.debug("No spot for a treasure birth in {}", SkyEvents.key(level));
				return;
			}
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double reach = config.distanceMin + random.nextDouble() * (config.distanceMax - config.distanceMin);
			int x = (int) Math.floor(focus.getX() + Math.cos(angle) * reach);
			int z = (int) Math.floor(focus.getZ() + Math.sin(angle) * reach);
			int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, noise);
			int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, noise);
			if (surface <= level.getMinY() + 2 || surface >= level.getMaxY() - 4 || surface - floor > MAX_WATER_DEPTH) {
				continue;
			}
			BlockPos pos = new BlockPos(x, surface, z);
			if (!allowed(level, pos)) {
				continue;
			}
			SEARCH.remove(level.dimension());
			startTremor(level, pos, TREMOR_TICKS, -1, null);
			return;
		}
	}

	/** Not in a town (or Kota Abadi), a sect territory or a sect site. */
	static boolean allowed(ServerLevel level, BlockPos pos) {
		if (!DuniaSites.near(level, pos, 128, "kota/").isEmpty()) {
			return false;
		}
		if (SectBuild.insideAnySite(level, pos, 64)) {
			return false;
		}
		Vec3 at = Vec3.atCenterOf(pos);
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			if (Halls.inTerritory(player.getUUID(), level, at)) {
				return false;
			}
		}
		return true;
	}

	// ================================================================== tremor and birth

	private static void startTremor(ServerLevel level, BlockPos pos, int ticks, int tier, @Nullable String kind) {
		PENDING.put(level.dimension(), new Pending(pos.immutable(), level.getGameTime() + ticks, tier, kind, false));
		tremorPulse(level, pos);
	}

	private static void tremorPulse(ServerLevel level, BlockPos pos) {
		Vec3 at = Vec3.atCenterOf(pos);
		for (ServerPlayer player : List.copyOf(level.players())) {
			double distance = horizontal(player.position(), at);
			if (distance > TREMOR_RANGE) {
				continue;
			}
			Net.send(player, LangitFxPayload.at(LangitFxPayload.KIND_TREMOR, at.x, at.y, at.z, (int) distance));
			if (Langit.once(player, "tremor", 400)) {
				Msg.chat(player, Component.translatable("kultivator.langit.harta.tremor").withColor(0xC8B48A));
			}
		}
	}

	private static void tremor(ServerLevel level, Pending pending) {
		long now = level.getGameTime();
		if (!pending.pulsed() && pending.bornAt() - now <= TREMOR_TICKS / 2) {
			PENDING.put(level.dimension(), new Pending(pending.pos(), pending.bornAt(), pending.tier(), pending.kind(), true));
			tremorPulse(level, pending.pos());
		}
		if (now < pending.bornAt()) {
			return;
		}
		PENDING.remove(level.dimension());
		int tier = pending.tier() > 0 ? pending.tier() : tierFor(level, pending.pos());
		String kind = pending.kind() != null ? pending.kind() : TreasureRewards.randomKind(level.getRandom());
		born(level, pending.pos(), tier, kind);
	}

	/** The birth itself: the live record, the site, the sync, the announcement, {@code TREASURE_BORN}. */
	private static TreasureBirth born(ServerLevel level, BlockPos pos, int tier, String kind) {
		LangitConfig config = LangitConfig.get();
		long now = level.getGameTime();
		long id = DuniaSites.idOf(level.dimension(), pos, SITE_KIND);
		TreasureBirth record = new TreasureBirth(id, level.dimension(), pos, tier, kind, now, now + config.durationTicks);
		Birth birth = new Birth(record, TreasureRewards.roll(tier, kind, level.getRandom()));
		LIVE.put(level.dimension(), birth);
		DuniaSites.publishLive(level, SITE_KIND, pos, tier, record.ends());
		syncAll(level, birth, LangitHartaPayload.LIVE);
		Vec3 at = Vec3.atCenterOf(pos);
		for (ServerPlayer player : List.copyOf(level.players())) {
			double distance = horizontal(player.position(), at);
			if (distance > TREMOR_RANGE * 1.5D) {
				continue;
			}
			Msg.chat(player, Component.translatable("kultivator.langit.harta.born", tierName(tier), direction(player.position(), at),
					(int) Math.round(distance)).withColor(TreasureRewards.tierColor(tier)));
			Langit.heaven(player, "langit.treasure_born", "witness");
			// a treasure above the player's own realm: its aura presses on them (the guardians match the tier, not them)
			int own = tierOf(Cultivators.get(player).realm());
			if (own < tier) {
				Msg.chat(player, Component.translatable("kultivator.langit.harta.above", tierName(tier))
						.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
		}
		Kultivator.LOGGER.info("A treasure (tier {}, {}) was born at {} in {}", tier, kind, pos.toShortString(), SkyEvents.key(level));
		DuniaEvents.fireTreasureBorn(level, record);
		return record;
	}

	/** The tier from the strongest active player within 1 000 blocks (T1 when nobody is near). */
	static int tierFor(ServerLevel level, BlockPos pos) {
		Vec3 at = Vec3.atCenterOf(pos);
		Realm best = Realm.MORTAL;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || horizontal(player.position(), at) > TIER_RANGE) {
				continue;
			}
			Realm realm = Cultivators.get(player).realm();
			if (realm.ordinal() > best.ordinal()) {
				best = realm;
			}
		}
		return tierOf(best);
	}

	static int tierOf(Realm realm) {
		if (realm.atLeast(Realm.IMMORTAL_ASCENSION)) {
			return 4;
		}
		if (realm.atLeast(Realm.DEITY_TRANSFORMATION)) {
			return 3;
		}
		if (realm.atLeast(Realm.GOLDEN_CORE)) {
			return 2;
		}
		return 1;
	}

	// ================================================================== the live birth

	private static void tickLive(ServerLevel level, Birth birth) {
		long now = level.getGameTime();
		TreasureBirth record = birth.record;
		if (now >= record.ends()) {
			finish(level, birth, LangitHartaPayload.FADED, null, null);
			return;
		}
		BlockPos pos = record.pos();
		Vec3 at = Vec3.atCenterOf(pos);
		double nearest = Double.MAX_VALUE;
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator()) {
				nearest = Math.min(nearest, player.position().distanceTo(at));
			}
		}
		HartaLahirEntity entity = birth.entity;
		if (entity != null && (entity.isRemoved() || !entity.isAlive())) {
			birth.entity = null;
			entity = null;
		}
		boolean loaded = level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
		if (entity == null && nearest <= PRESENCE && loaded) {
			entity = spawnTreasure(level, birth);
		} else if (entity != null && nearest > ABSENCE) {
			entity.discard();
			birth.entity = null;
			birth.channeler = null;
			return;
		}
		if (entity == null) {
			return;
		}
		if (level.getServer().getTickCount() % 20 == 2) {
			TreasureGuardians.tick(level, birth.record, now);
		}
		tickChannel(level, birth, entity, now);
	}

	private static @Nullable HartaLahirEntity spawnTreasure(ServerLevel level, Birth birth) {
		BlockPos pos = birth.record.pos();
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		if (top > level.getMinY() && Math.abs(top - pos.getY()) > 1) {
			// the noise height missed trees, builds or carvers: the treasure rests on the real ground
			TreasureBirth old = birth.record;
			BlockPos real = new BlockPos(pos.getX(), top, pos.getZ());
			birth.record = new TreasureBirth(old.id(), old.dim(), real, old.tier(), old.kind(), old.born(), old.ends());
			syncAll(level, birth, LangitHartaPayload.LIVE);
			pos = real;
		}
		HartaLahirEntity entity = LangitItems.HARTA.create(level, EntitySpawnReason.EVENT);
		if (entity == null) {
			return null;
		}
		entity.setup(birth.record.id(), birth.display(), birth.record.tier());
		entity.setPos(pos.getX() + 0.5D, pos.getY() + 0.1D, pos.getZ() + 0.5D);
		if (!level.addFreshEntity(entity)) {
			return null;
		}
		birth.entity = entity;
		if (!birth.guardianDone) {
			birth.guardianDone = true;
			if (level.getRandom().nextDouble() < LangitConfig.get().guardianChance) {
				TreasureGuardians.spawn(level, birth.record);
			}
		}
		return entity;
	}

	// ================================================================== the claim channel

	/** Right-click on the treasure: starts the 5-second channel when close enough and nobody else channels. */
	static void startChannel(ServerLevel level, ServerPlayer player, HartaLahirEntity entity) {
		Birth birth = LIVE.get(level.dimension());
		if (birth == null || birth.entity != entity || player.isSpectator()) {
			return;
		}
		if (player.position().distanceTo(entity.position()) > CLAIM_RANGE + 0.5D) {
			Msg.actionBar(player, Component.translatable("kultivator.langit.harta.closer").withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (birth.channeler != null && !birth.channeler.equals(player.getUUID())) {
			ServerPlayer other = level.getServer().getPlayerList().getPlayer(birth.channeler);
			if (other != null && other.isAlive() && other.level() == level) {
				Msg.actionBar(player, Component.translatable("kultivator.langit.harta.taken", other.getDisplayName()).withStyle(ChatFormatting.YELLOW));
				return;
			}
		}
		if (player.getUUID().equals(birth.channeler)) {
			return;
		}
		int guards = TreasureGuardians.guarding(level, birth.record);
		if (guards > 0) {
			// the guardians seal the lotus while they live near it
			Msg.actionBar(player, Component.translatable("kultivator.langit.harta.guarded", guards).withStyle(ChatFormatting.GOLD));
			level.playSound(null, entity.getX(), entity.getY() + 0.8D, entity.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.4F);
			return;
		}
		birth.channeler = player.getUUID();
		birth.channelStart = level.getGameTime();
		entity.setChannel(1, player.getId());
		Msg.actionBar(player, Component.translatable("kultivator.langit.harta.channel", CLAIM_TICKS / 20).withColor(TreasureRewards.tierColor(birth.record.tier())));
		level.playSound(null, entity.getX(), entity.getY() + 0.8D, entity.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.9F, 1.3F);
	}

	private static void tickChannel(ServerLevel level, Birth birth, HartaLahirEntity entity, long now) {
		if (birth.channeler == null) {
			if (entity.channel() != 0) {
				entity.setChannel(0, -1);
			}
			return;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(birth.channeler);
		if (player == null || !player.isAlive() || player.level() != level
				|| player.position().distanceTo(entity.position()) > CLAIM_RANGE + 0.5D) {
			breakChannel(level, birth, player);
			return;
		}
		int progress = (int) Math.min(100L, (now - birth.channelStart) * 100L / CLAIM_TICKS);
		entity.setChannel(Math.max(1, progress), player.getId());
		if (progress >= 100) {
			claimFor(level, birth, player);
		}
	}

	private static void breakChannel(ServerLevel level, Birth birth, @Nullable ServerPlayer player) {
		birth.channeler = null;
		if (birth.entity != null) {
			birth.entity.setChannel(0, -1);
		}
		if (player != null) {
			Msg.actionBar(player, Component.translatable("kultivator.langit.harta.broken").withStyle(ChatFormatting.RED));
			HartaLahirEntity entity = birth.entity;
			Vec3 at = entity != null ? entity.position() : Vec3.atCenterOf(birth.record.pos());
			Net.send(player, LangitFxPayload.at(LangitFxPayload.KIND_CHANNEL_BROKEN, at.x, at.y, at.z, birth.record.tier()));
		}
	}

	/** {@code ServerLivingEntityEvents.AFTER_DAMAGE}: a hit breaks the victim's channel. */
	static void onDamaged(ServerPlayer player, float taken) {
		if (!(taken > 0.0F) || LIVE.isEmpty()) {
			return;
		}
		Birth birth = LIVE.get(player.level().dimension());
		if (birth != null && player.getUUID().equals(birth.channeler)) {
			breakChannel(player.level(), birth, player);
		}
	}

	/** The claim: every reward to the player (dropped at their feet when the inventory is full), the burst, the event. */
	private static void claimFor(ServerLevel level, Birth birth, ServerPlayer player) {
		List<MutableComponent> names = new ArrayList<>();
		for (ItemStack reward : birth.rewards) {
			if (reward.isEmpty()) {
				continue;
			}
			names.add(Component.literal(reward.getCount() > 1 ? reward.getCount() + "× " : "").append(reward.getHoverName()));
			give(player, reward.copy());
		}
		int tier = birth.record.tier();
		Msg.chat(player, Component.translatable("kultivator.langit.harta.claimed", tierName(tier)).withColor(TreasureRewards.tierColor(tier)));
		for (MutableComponent name : names) {
			Msg.chat(player, Component.literal("  ✦ ").withColor(TreasureRewards.tierColor(tier)).append(name.withStyle(ChatFormatting.WHITE)));
		}
		Advancements.grant(player, "dunia/treasure_claimed");
		if (tier >= 3) {
			Advancements.grant(player, "dunia/treasure_heaven");
		}
		Langit.heaven(player, null, "witness");
		Vec3 at = birth.entity != null ? birth.entity.position() : Vec3.atCenterOf(birth.record.pos());
		level.playSound(null, at.x, at.y + 1.0D, at.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.playSound(null, at.x, at.y + 1.0D, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.6F, 1.2F);
		Net.send(player, LangitFxPayload.at(LangitFxPayload.KIND_CLAIM, at.x, at.y, at.z, tier));
		for (ServerPlayer viewer : PlayerLookup.around(level, at, 192.0D)) {
			if (viewer != player) {
				Net.send(viewer, LangitFxPayload.at(LangitFxPayload.KIND_CLAIM_OTHER, at.x, at.y, at.z, tier));
				Msg.chat(viewer, Component.translatable("kultivator.langit.harta.claimed_by", player.getDisplayName()).withStyle(ChatFormatting.GRAY));
			}
		}
		finish(level, birth, LangitHartaPayload.CLAIMED, player, null);
	}

	/** Into the inventory, the rest dropped at the player's feet (only they can pick it up at first). */
	static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().add(stack);
		if (!stack.isEmpty()) {
			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), stack);
			item.setTarget(player.getUUID());
			item.setPickUpDelay(10);
			player.level().addFreshEntity(item);
		}
	}

	/** Ends a birth: removes the treasure, ends the live site, syncs {@code phase}, fires {@code TREASURE_CLAIMED}. */
	private static void finish(ServerLevel level, Birth birth, int phase, @Nullable ServerPlayer winner, @Nullable String rivalId) {
		LIVE.remove(level.dimension(), birth);
		TreasureGuardians.release(level);
		if (birth.entity != null) {
			birth.entity.discard();
			birth.entity = null;
		}
		DuniaSites.endLive(birth.record.id());
		syncAll(level, birth, phase);
		if (phase == LangitHartaPayload.FADED) {
			for (ServerPlayer player : List.copyOf(level.players())) {
				if (horizontal(player.position(), Vec3.atCenterOf(birth.record.pos())) <= TREMOR_RANGE) {
					Msg.chat(player, Component.translatable("kultivator.langit.harta.faded").withStyle(ChatFormatting.GRAY));
				}
			}
		}
		DuniaEvents.fireTreasureClaimed(level, birth.record, winner, rivalId);
	}

	// ================================================================== service and commands

	/**
	 * {@code TreasureService.spawn}: a birth at once (no tremor), in any dimension (D's sea treasures, R's re-sealed
	 * prisons); null when switched off or one is already live there. Only the schedule is limited to two dimensions.
	 */
	static @Nullable TreasureBirth spawnNow(ServerLevel level, BlockPos pos, int tier, String kind, boolean test) {
		if (!test && (!LangitConfig.get().treasureBirth || !DuniaSpine.enabled())) {
			return null;
		}
		if (LIVE.containsKey(level.dimension())) {
			if (!test) {
				return null;
			}
			clear(level);
		}
		PENDING.remove(level.dimension());
		SEARCH.remove(level.dimension());
		int t = Math.max(1, Math.min(4, tier <= 0 ? tierFor(level, pos) : tier));
		String k = TreasureRewards.validKind(kind) ? kind : TreasureRewards.randomKind(level.getRandom());
		return born(level, pos, t, k);
	}

	/** {@code uji harta_lahir}: a tremor of 5 s, then a birth at {@code pos}. */
	static void testBirth(ServerLevel level, BlockPos pos, int tier, @Nullable String kind) {
		clear(level);
		startTremor(level, pos, TEST_TREMOR_TICKS, tier, kind);
	}

	/** {@code TreasureService.live}. */
	static List<TreasureBirth> live(ServerLevel level) {
		Birth birth = LIVE.get(level.dimension());
		return birth == null ? List.of() : List.of(birth.record);
	}

	/** {@code TreasureService.claim}: gives birth {@code id} to {@code player} at once. */
	static boolean claim(ServerPlayer player, long id) {
		Birth birth = LIVE.get(player.level().dimension());
		if (birth == null || birth.record.id() != id) {
			return false;
		}
		claimFor(player.level(), birth, player);
		return true;
	}

	/** {@code TreasureService.claimByRival}: a rival group takes birth {@code id}. */
	static boolean claimByRival(ServerLevel level, long id, String rivalId) {
		Birth birth = LIVE.get(level.dimension());
		if (birth == null || birth.record.id() != id) {
			return false;
		}
		Component rival = DuniaApi.rivals().name(rivalId);
		Component name = rival != null ? rival : Component.literal(rivalId == null ? "?" : rivalId);
		Vec3 at = Vec3.atCenterOf(birth.record.pos());
		for (ServerPlayer player : PlayerLookup.around(level, at, 256.0D)) {
			Msg.chat(player, Component.translatable("kultivator.langit.harta.rival", name).withStyle(ChatFormatting.RED));
		}
		finish(level, birth, LangitHartaPayload.RIVAL, null, rivalId);
		return true;
	}

	/** True when {@code entity} is the treasure of the live birth of its level. */
	static boolean owns(ServerLevel level, HartaLahirEntity entity) {
		Birth birth = LIVE.get(level.dimension());
		return birth != null && birth.entity == entity && birth.record.id() == entity.birthId();
	}

	/** Ends every birth, tremor and search of {@code level} (no event for a tremor). True when something ended. */
	static boolean clear(ServerLevel level) {
		boolean any = PENDING.remove(level.dimension()) != null | SEARCH.remove(level.dimension()) != null;
		Birth birth = LIVE.get(level.dimension());
		if (birth != null) {
			finish(level, birth, LangitHartaPayload.FADED, null, null);
			any = true;
		}
		return any;
	}

	/** {@code uji harta_lahir jadwal}: starts the search now and describes the schedule. */
	static Component scheduleNow(ServerPlayer player) {
		ServerLevel level = player.level();
		LangitData data = LangitData.get(level.getServer());
		LangitData.Dim dim = data.dim(SkyEvents.key(level));
		long day = Math.floorDiv(level.getOverworldClockTime(), (long) SkyEvents.DAY);
		Birth birth = LIVE.get(level.dimension());
		if (birth != null) {
			return Component.translatable("kultivator.langit.cmd.harta_live", tierName(birth.record.tier()), kindName(birth.record.kind()),
					birth.record.pos().toShortString(), Math.max(0L, (birth.record.ends() - level.getGameTime()) / 20L));
		}
		if (!busy(level.dimension())) {
			SEARCH.put(level.dimension(), new Search(player.getUUID()));
		}
		return Component.translatable("kultivator.langit.cmd.harta_schedule", dim.nextBirthDay < 0 ? "-" : String.valueOf(dim.nextBirthDay - day),
				LangitConfig.get().treasureBirth ? Component.translatable("options.on") : Component.translatable("options.off"));
	}

	// ================================================================== sync

	static LangitHartaPayload payload(Birth birth, int phase) {
		TreasureBirth r = birth.record;
		return new LangitHartaPayload(r.id(), phase, r.pos().getX(), r.pos().getY(), r.pos().getZ(), r.tier(), r.kind(), r.born(), r.ends());
	}

	private static void syncAll(ServerLevel level, Birth birth, int phase) {
		LangitHartaPayload payload = payload(birth, phase);
		for (ServerPlayer player : List.copyOf(level.players())) {
			Net.send(player, payload);
		}
	}

	/** A joining or arriving player learns the live birth of their dimension. */
	static void sync(ServerPlayer player) {
		Birth birth = LIVE.get(player.level().dimension());
		if (birth != null) {
			Net.send(player, payload(birth, LangitHartaPayload.LIVE));
		}
	}

	/** The master switch went off: every birth, tremor and search ends (the births fade with {@code TREASURE_CLAIMED}). */
	static void shutdown(MinecraftServer server) {
		if (LIVE.isEmpty() && PENDING.isEmpty() && SEARCH.isEmpty()) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			clear(level);
		}
	}

	static void clearAll() {
		LIVE.clear();
		PENDING.clear();
		SEARCH.clear();
	}

	// ================================================================== text helpers

	static Component tierName(int tier) {
		return Component.translatable("kultivator.langit.harta.tier." + Math.max(1, Math.min(4, tier))).withColor(TreasureRewards.tierColor(tier));
	}

	static Component kindName(String kind) {
		return Component.translatable("kultivator.langit.harta.kind." + (TreasureRewards.validKind(kind) ? kind : "herba"));
	}

	/** "barat laut" etc.: the compass direction from {@code from} to {@code to}. */
	static Component direction(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		// 0 = north (-z), clockwise
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		int sector = (int) Math.floorMod(Math.round(angle / 45.0D), 8L);
		String[] ids = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
		return Component.translatable("kultivator.langit.dir." + ids[sector]);
	}

	static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private TreasureBirths() {
	}
}
