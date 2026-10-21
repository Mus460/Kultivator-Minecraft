package dev.kultivator.desa;

import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.musuh.SectRaids;
import dev.kultivator.cultivation.DaoProtectors;
import dev.kultivator.goals.Goals;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Permohonan Bantuan / help requests (DESIGN 25.6.6): one request per vassal every 2-4 in-game days, expiring after 2.
 * Each comes as a chat line, a toast and a Surat Permohonan whose needle points at the target. The quests run only while
 * the owner is within 64 blocks of the target: a hardened elite in the fields at night (never a zombie, drowned, husk or
 * illager, so no villager is ever converted), a bandit camp of rogue cultivators (module B's {@code spawnBand}), healing
 * pills or food handed in at the plaque, or another Malam Penjagaan. A finished request leaves vanilla
 * {@code MINOR_POSITIVE} gossip (+5, max 25, decays daily). The requests and numbers are game design.
 */
public final class Requests {

	public static final String QUEST_TAG = "kultivator.desa_quest";
	public static final double QUEST_RANGE = 64.0D;
	public static final double CAMP_TRIGGER = 48.0D;
	public static final int EXPIRE_DAYS = 2;
	public static final int HARVEST_FOOD = 32;
	public static final int PLAGUE_HEALING = 2;
	private static final int[] WEIGHTS = {35, 25, 15, 15, 10};
	private static final List<EntityType<? extends Mob>> BEASTS = List.of(EntityTypes.SPIDER, EntityTypes.SKELETON, EntityTypes.STRAY,
			EntityTypes.BOGGED);

	/** The live quest elite of a village (key owner:id). */
	private static final Map<String, UUID> BEAST_ALIVE = new HashMap<>();
	/** Camps whose band is out now (key owner:id). */
	private static final Set<String> CAMP_ACTIVE = new HashSet<>();
	/** Every live quest entity, so it is refused when it would load from disk. */
	private static final Set<UUID> ALIVE = new HashSet<>();

	private static String key(UUID owner, int id) {
		return owner + ":" + id;
	}

	/** True for a quest entity that is still wanted (ALLOW_LOAD lets only these back in). */
	static boolean wanted(Entity entity) {
		return ALIVE.contains(entity.getUUID());
	}

	// ================================================================== schedule

	/** A new request when one is due; an expired one fails. True when something changed. */
	static boolean schedule(MinecraftServer server, UUID owner, Village village, long day, @Nullable ServerPlayer player) {
		if (!village.pledged || village.lost) {
			return false;
		}
		if (village.hasRequest()) {
			if (day > village.reqExpires) {
				fail(server, owner, village, "kultivator.desa.request.expired");
				return true;
			}
			return false;
		}
		if (day < village.nextRequest) {
			return false;
		}
		RandomSource random = server.overworld().getRandom();
		// On Peaceful no beast, camp band or night raid can appear: only the plague and harvest requests are rolled.
		boolean peaceful = server.overworld().getDifficulty() == Difficulty.PEACEFUL;
		int total = 0;
		for (int i = 0; i < WEIGHTS.length; i++) {
			total += rollable(i, peaceful) ? WEIGHTS[i] : 0;
		}
		int roll = random.nextInt(Math.max(1, total));
		int index = indexOf(peaceful ? Village.REQ_HARVEST : Village.REQ_BEAST);
		for (int i = 0; i < WEIGHTS.length; i++) {
			if (!rollable(i, peaceful)) {
				continue;
			}
			roll -= WEIGHTS[i];
			if (roll < 0) {
				index = i;
				break;
			}
		}
		open(server, owner, village, Village.REQUESTS[index], player);
		return true;
	}

	/** True when request {@code index} of {@link Village#REQUESTS} may be rolled (Peaceful: plague and harvest only). */
	private static boolean rollable(int index, boolean peaceful) {
		String type = Village.REQUESTS[index];
		return !peaceful || Village.REQ_PLAGUE.equals(type) || Village.REQ_HARVEST.equals(type);
	}

	/** Opens a request of {@code type} now and sends the letter. */
	static void open(MinecraftServer server, UUID owner, Village village, String type, @Nullable ServerPlayer player) {
		clear(server, owner, village);
		long day = Vassals.day();
		village.reqType = type;
		village.reqProgress = 0;
		village.reqExpires = day + EXPIRE_DAYS;
		village.reqPos = switch (type) {
			case Village.REQ_BEAST -> village.bell;
			case Village.REQ_CAMP -> campSpot(server.overworld(), village.bell);
			default -> village.plaque;
		};
		Vassals.changed();
		Desa.dirty(owner);
		Halls.log(owner, Component.translatable("kultivator.desa.request.log", village.name, Component.translatable("kultivator.desa.request." + type)));
		if (player != null) {
			Msg.chat(player, Component.translatable("kultivator.desa.request.new", village.name, Component.translatable("kultivator.desa.request." + type))
					.withStyle(ChatFormatting.GOLD));
			Msg.chat(player, Component.translatable("kultivator.desa.request." + type + ".task").withStyle(ChatFormatting.GRAY));
			Fx.sendTo(player, player.position(), player.position(), FxType.DESA, DesaFxIds.TOAST, indexOf(type), 0.0F);
			Desa.give(player, letter(village));
		}
	}

	private static int indexOf(String type) {
		for (int i = 0; i < Village.REQUESTS.length; i++) {
			if (Village.REQUESTS[i].equals(type)) {
				return i;
			}
		}
		return 0;
	}

	/**
	 * A point 64-160 blocks from the bell (no blocks: only a data point and a smoke column), on dry land: a loaded spot
	 * on water is picked again; an unloaded one is checked once more when the camp appears ({@link #landNear}).
	 */
	private static BlockPos campSpot(ServerLevel level, BlockPos bell) {
		RandomSource random = level.getRandom();
		BlockPos unloaded = null;
		BlockPos wet = null;
		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double distance = 64.0D + random.nextDouble() * 96.0D;
			int x = (int) Math.floor(bell.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(bell.getZ() + Math.sin(angle) * distance);
			BlockPos column = new BlockPos(x, bell.getY(), z);
			if (!level.isLoaded(column)) {
				if (unloaded == null) {
					unloaded = column;
				}
				continue;
			}
			BlockPos spot = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (dry(level, spot)) {
				return spot;
			}
			if (wet == null) {
				wet = spot;
			}
		}
		return unloaded != null ? unloaded : wet != null ? wet : bell;
	}

	/** True when the surface spot {@code spot} (feet) stands on no water, lava or other fluid. */
	private static boolean dry(ServerLevel level, BlockPos spot) {
		return level.getFluidState(spot).isEmpty() && level.getFluidState(spot.below()).isEmpty();
	}

	/** The nearest dry loaded surface within 32 blocks of {@code camp} (rings of 4 blocks), or null. */
	private static @Nullable BlockPos landNear(ServerLevel level, BlockPos camp) {
		for (int ring = 4; ring <= 32; ring += 4) {
			for (int step = 0; step < 16; step++) {
				double angle = step * Math.PI / 8.0D;
				int x = camp.getX() + (int) Math.round(Math.cos(angle) * ring);
				int z = camp.getZ() + (int) Math.round(Math.sin(angle) * ring);
				BlockPos column = new BlockPos(x, camp.getY(), z);
				if (!level.isLoaded(column)) {
					continue;
				}
				BlockPos spot = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
				if (dry(level, spot)) {
					return spot;
				}
			}
		}
		return null;
	}

	/** The Surat Permohonan of the village's open request (needle at the target). */
	static ItemStack letter(Village village) {
		ItemStack stack = new ItemStack(DesaItems.VILLAGE_LETTER);
		CompoundTag tag = new CompoundTag();
		tag.putString("village", village.name);
		tag.putString("type", village.reqType);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(Level.OVERWORLD, village.reqPos)), false));
		return stack;
	}

	// ================================================================== live quests

	/** Every 20 ticks with the owner online: the quest elite, the camp band and its smoke, the night watch. */
	static void tick(MinecraftServer server, UUID owner, Village village, ServerPlayer player, long gameTime) {
		if (!village.hasRequest() || player.level() != server.overworld()) {
			return;
		}
		ServerLevel level = server.overworld();
		double distSq = player.position().distanceToSqr(Vec3.atCenterOf(village.reqPos));
		boolean near = distSq <= QUEST_RANGE * QUEST_RANGE;
		switch (village.reqType) {
			case Village.REQ_BEAST -> tickBeast(level, owner, village, near);
			case Village.REQ_CAMP -> {
				if (near && gameTime % 40L == 0L) {
					Fx.send(level, Vec3.atBottomCenterOf(village.reqPos), FxType.DESA, DesaFxIds.CAMP_SMOKE, 0, 0.0F);
				}
				if (distSq <= CAMP_TRIGGER * CAMP_TRIGGER && !CAMP_ACTIVE.contains(key(owner, village.id)) && level.getDifficulty() != Difficulty.PEACEFUL) {
					spawnCamp(level, owner, village, player);
				}
			}
			case Village.REQ_WATCH -> {
				if (Vassals.night() && Desa.active(player) && player.position().distanceToSqr(Vec3.atCenterOf(village.plaque))
						<= Pledge.TRUST_RANGE * Pledge.TRUST_RANGE && VillageRaids.siegeAt(owner, village.id) == null) {
					VillageRaids.start(server, owner, village, VillageRaids.KIND_WATCH);
				}
			}
			default -> {
			}
		}
	}

	private static void tickBeast(ServerLevel level, UUID owner, Village village, boolean near) {
		String key = key(owner, village.id);
		UUID alive = BEAST_ALIVE.get(key);
		Entity entity = alive == null ? null : level.getEntity(alive);
		if (alive != null && (entity == null || !entity.isAlive())) {
			if (entity == null) {
				// Unloaded or gone without a kill: it may come again.
				BEAST_ALIVE.remove(key);
				ALIVE.remove(alive);
			}
			return;
		}
		if (entity != null) {
			if (!Vassals.night() || !near) {
				// The beast slinks off at dawn or when the master leaves; it may come back another night.
				Fx.burst(level, entity.position().add(0.0D, 0.8D, 0.0D), 0x5A3A5E, 10, 1.0F);
				entity.discard();
				BEAST_ALIVE.remove(key);
				ALIVE.remove(alive);
			}
			return;
		}
		if (!near || !Vassals.night() || level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		spawnBeast(level, owner, village);
	}

	private static void spawnBeast(ServerLevel level, UUID owner, Village village) {
		RandomSource random = level.getRandom();
		EntityType<? extends Mob> type = BEASTS.get(random.nextInt(BEASTS.size()));
		Mob mob = type.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return;
		}
		Vec3 spot = null;
		for (int attempt = 0; attempt < 8 && spot == null; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double distance = 16.0D + random.nextDouble() * 24.0D;
			int x = (int) Math.floor(village.bell.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(village.bell.getZ() + Math.sin(angle) * distance);
			BlockPos column = new BlockPos(x, village.bell.getY(), z);
			if (!level.isPositionEntityTicking(column)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			spot = Targeting.safeSpot(level, mob, new Vec3(x + 0.5D, y, z + 0.5D));
		}
		if (spot == null) {
			return;
		}
		mob.snapTo(spot.x, spot.y, spot.z, random.nextFloat() * 360.0F, 0.0F);
		DaoProtectors.harden(mob);
		mob.addTag(NoGrief.TAG);
		mob.addTag(QUEST_TAG);
		mob.addTag(QUEST_TAG + "." + owner + "." + village.id);
		if (!level.addFreshEntity(mob)) {
			return;
		}
		int tier = Halls.level(owner) >= 5 ? 2 : 1;
		EliteMonsters.makeElite(mob, tier, null, Math.max(1, Math.min(8, SectRaids.raidLevel(owner))));
		ALIVE.add(mob.getUUID());
		BEAST_ALIVE.put(key(owner, village.id), mob.getUUID());
		level.playSound(null, mob.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 0.8F);
	}

	private static void spawnCamp(ServerLevel level, UUID owner, Village village, ServerPlayer player) {
		RandomSource random = level.getRandom();
		int raidLevel = Math.max(1, Math.min(8, SectRaids.raidLevel(owner)));
		int count = 3 + random.nextInt(3);
		int[] tiers = new int[count];
		for (int i = 0; i < count; i++) {
			tiers[i] = i == 0 && raidLevel >= 5 ? 3 : i < 2 ? 2 : 1;
		}
		BlockPos camp = village.reqPos;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, camp.getX(), camp.getZ());
		if (!dry(level, new BlockPos(camp.getX(), y, camp.getZ()))) {
			// The camp was picked on water (or before its chunk loaded): it moves to the nearest dry land.
			BlockPos land = landNear(level, camp);
			if (land != null) {
				camp = land;
				y = land.getY();
				village.reqPos = land;
				Vassals.changed();
			}
		}
		Vec3 center = new Vec3(camp.getX() + 0.5D, y, camp.getZ() + 0.5D);
		String key = key(owner, village.id);
		int spawned = RogueCultivators.spawnBand(level, center, tiers, raidLevel, RogueCultivators.ROLE_CAMP, camp, cleared -> {
			CAMP_ACTIVE.remove(key);
			MinecraftServer server = Vassals.server();
			Village current = Vassals.find(owner, village.id);
			if (cleared && server != null && current != null && Village.REQ_CAMP.equals(current.reqType)) {
				complete(server, owner, current, server.getPlayerList().getPlayer(owner));
			}
		});
		if (spawned > 0) {
			CAMP_ACTIVE.add(key);
			Msg.chat(player, Component.translatable("kultivator.desa.camp.ambush", spawned).withStyle(ChatFormatting.RED));
		}
	}

	/** A living entity died: a village's quest elite completes its request. */
	static void onDeath(MinecraftServer server, Entity victim) {
		if (!victim.entityTags().contains(QUEST_TAG)) {
			return;
		}
		ALIVE.remove(victim.getUUID());
		for (Map.Entry<String, UUID> entry : new HashMap<>(BEAST_ALIVE).entrySet()) {
			if (!entry.getValue().equals(victim.getUUID())) {
				continue;
			}
			BEAST_ALIVE.remove(entry.getKey());
			String[] parts = entry.getKey().split(":");
			try {
				UUID owner = UUID.fromString(parts[0]);
				Village village = Vassals.find(owner, Integer.parseInt(parts[1]));
				if (village != null && Village.REQ_BEAST.equals(village.reqType)) {
					complete(server, owner, village, server.getPlayerList().getPlayer(owner));
				}
			} catch (RuntimeException ignored) {
				// A malformed key cannot happen; nothing to complete.
			}
		}
	}

	// ================================================================== hand-ins at the plaque

	/** Sneak-use on the plaque with a pill or food while Wabah or Panen Gagal is open; true when the item was taken. */
	static boolean handIn(MinecraftServer server, UUID owner, Village village, ServerPlayer player, ItemStack held) {
		if (Village.REQ_PLAGUE.equals(village.reqType)) {
			int healing = Desa.isPill(held, "healing_pill") ? 1 : 0;
			boolean purge = Desa.isPill(held, "toxin_purging_pill");
			if (healing == 0 && !purge) {
				return false;
			}
			held.consume(1, player);
			village.reqProgress += purge ? PLAGUE_HEALING : 1;
			Vassals.changed();
			if (village.reqProgress >= PLAGUE_HEALING) {
				complete(server, owner, village, player);
			} else {
				Msg.actionBar(player, Component.translatable("kultivator.desa.plague.progress", village.reqProgress, PLAGUE_HEALING));
			}
			return true;
		}
		if (Village.REQ_HARVEST.equals(village.reqType)) {
			int points = Desa.foodPoints(held);
			if (points <= 0) {
				return false;
			}
			int need = HARVEST_FOOD - village.reqProgress;
			int take = Math.max(1, Math.min(held.getCount(), (need + points - 1) / points));
			held.consume(take, player);
			village.reqProgress += take * points;
			Vassals.changed();
			if (village.reqProgress >= HARVEST_FOOD) {
				complete(server, owner, village, player);
			} else {
				Msg.actionBar(player, Component.translatable("kultivator.desa.harvest.progress", village.reqProgress, HARVEST_FOOD));
			}
			return true;
		}
		return false;
	}

	// ================================================================== outcomes

	/** The request is done: loyalty, the type's reward, gossip, the chore counter and the next request day. */
	static void complete(MinecraftServer server, UUID owner, Village village, @Nullable ServerPlayer player) {
		String type = village.reqType;
		if (type.isEmpty()) {
			return;
		}
		int loyalty = switch (type) {
			case Village.REQ_BEAST, Village.REQ_PLAGUE -> 10;
			case Village.REQ_CAMP -> 15;
			case Village.REQ_HARVEST -> 8;
			default -> 12;
		};
		clear(server, owner, village);
		Desa.addLoyalty(server, owner, village, loyalty);
		switch (type) {
			case Village.REQ_BEAST -> {
				if (player != null) {
					Desa.give(player, new ItemStack(Items.EMERALD, 4));
				}
			}
			case Village.REQ_CAMP -> {
				Halls.addPool(owner, 40);
				if (player != null) {
					Desa.give(player, Desa.registryStack("loot_pouch", 2));
				}
			}
			case Village.REQ_HARVEST -> Tribute.pay(server, owner, village, 1.5D);
			default -> {
			}
		}
		ServerLevel level = server.overworld();
		if (level.isLoaded(village.bell)) {
			VillageScan.gossip(level, village.bell, owner);
		}
		level.playSound(null, village.plaque, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
		Fx.send(level, Vec3.atBottomCenterOf(village.plaque), FxType.DESA, DesaFxIds.REQUEST_DONE, 0, 0.0F);
		village.nextRequest = Vassals.day() + 2 + level.getRandom().nextInt(3);
		Vassals.changed();
		Desa.dirty(owner);
		Component line = Component.translatable("kultivator.desa.request.done", village.name, Component.translatable("kultivator.desa.request." + type),
				loyalty);
		Halls.log(owner, line);
		if (player != null) {
			Msg.chat(player, line.copy().withStyle(ChatFormatting.GREEN));
			Goals.addWorldCounter(player, Goals.W_REQUESTS, 1);
		}
	}

	/** The request failed or expired: loyalty -10 and the next request day. */
	static void fail(MinecraftServer server, UUID owner, Village village, String reasonKey) {
		if (!village.hasRequest()) {
			return;
		}
		String type = village.reqType;
		clear(server, owner, village);
		Desa.addLoyalty(server, owner, village, -10);
		village.nextRequest = Vassals.day() + 2 + server.overworld().getRandom().nextInt(3);
		Vassals.changed();
		Desa.dirty(owner);
		Component line = Component.translatable(reasonKey, village.name, Component.translatable("kultivator.desa.request." + type));
		Halls.log(owner, line);
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			Msg.chat(player, line.copy().withStyle(ChatFormatting.RED));
		}
	}

	/** Closes the request without an outcome: its elite leaves, the camp forgets its band. */
	static void clear(MinecraftServer server, UUID owner, Village village) {
		String key = key(owner, village.id);
		UUID alive = BEAST_ALIVE.remove(key);
		if (alive != null) {
			ALIVE.remove(alive);
			Entity entity = server.overworld().getEntity(alive);
			if (entity != null) {
				entity.discard();
			}
		}
		CAMP_ACTIVE.remove(key);
		if (VillageRaids.siegeAt(owner, village.id) != null && Village.REQ_WATCH.equals(village.reqType)) {
			VillageRaids.stop(owner, village.id);
		}
		village.reqType = "";
		village.reqProgress = 0;
	}

	static void clearAll() {
		BEAST_ALIVE.clear();
		CAMP_ACTIVE.clear();
		ALIVE.clear();
	}

	private Requests() {
	}
}
