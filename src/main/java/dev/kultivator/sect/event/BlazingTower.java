package dev.kultivator.sect.event;

import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Menara Langit Membara, the Blazing Sky Qi Refining Tower (DESIGN 20.3.15). Rooms are built by the player and only read:
 * a lit Soul Campfire standing on Crying Obsidian within 32 blocks of the stele (at most 7; the highest is floor 1, the
 * deepest floor n), found through the loaded chunks' block entities. A disciple trains in room n (activity 6): meditation
 * XP x(1.2 + 0.2n), comprehension +0.02n per second, n of its own PK per 1,200 ticks (free on the shared training day
 * {@code d % 7 == 3}), and every 60 s a heart-fire surge with chance 4% x n.
 * <p>
 * Sumber: Battle Through the Heavens ch. 491, 549, 622 (training rooms over the sealed Fallen Heart Flame, stronger deeper
 * down; students pay Fire Energy for time and newcomers get "grilled"; a monthly collective training day is free).
 */
final class BlazingTower {

	public static final int SEARCH_RADIUS = 32;
	public static final int MAX_ROOMS = 7;
	public static final double XP_BASE = 1.2D;
	public static final double XP_PER_FLOOR = 0.2D;
	public static final double COMPREHENSION_PER_FLOOR = 0.02D;
	public static final int PAY_INTERVAL = 1200;
	public static final int SURGE_INTERVAL = 1200;
	public static final double SURGE_CHANCE_PER_FLOOR = 0.04D;
	public static final double SURGE_RESIST_PER_FLOOR = 10.0D;
	public static final double SURGE_DAMAGE = 0.08D;
	public static final double QUALITY_STEP = 0.01D;
	public static final double QUALITY_MAX = 0.10D;
	public static final int THROWN_OUT_TICKS = 600;
	public static final int RECHECK_TICKS = 100;
	/** The shared training day: {@code d % 7 == 3}. */
	public static final int FREE_DAY = 3;

	private static final class Training {
		final UUID owner;
		final UUID soul;
		final int floor;
		final BlockPos room;
		final BlockPos spot;
		int age;
		int pausedUntilAge;

		Training(UUID owner, UUID soul, int floor, BlockPos room, BlockPos spot) {
			this.owner = owner;
			this.soul = soul;
			this.floor = floor;
			this.room = room;
			this.spot = spot;
		}
	}

	private static final Map<UUID, Training> TRAINING = new HashMap<>();
	/** Owner -> the rooms found last (sorted, highest first), with the game time of the scan. */
	private static final Map<UUID, List<BlockPos>> ROOMS = new HashMap<>();
	private static final Map<UUID, Long> SCANNED = new HashMap<>();

	/** Called once from {@link SectEvents#init()}. */
	static void init() {
		DiscipleHooks.addXpFactor((entity, data, source) -> {
			if (source != XpSource.MEDITATION || entity == null || entity.activity() != DiscipleEntity.ACTIVITY_TOWER) {
				return 1.0D;
			}
			Training training = TRAINING.get(entity.soulId());
			return training == null ? 1.0D : XP_BASE + XP_PER_FLOOR * training.floor;
		});
		DiscipleHooks.addQualityBonus((entity, target) -> qualityBonus(entity.data()));
		DiscipleHooks.addBreakthroughListener(new DiscipleHooks.BreakthroughListener() {
			@Override
			public void onEnd(DiscipleEntity entity, boolean success, dev.kultivator.disciple.DiscipleBreakthrough.Kind kind) {
				// The heart-fire bonus is for its next breakthrough only.
				EventUtil.extra(entity.data()).remove("tower_q");
			}
		});
	}

	static double qualityBonus(DiscipleData data) {
		return Math.min(QUALITY_MAX, data.extra().getCompoundOrEmpty(Duties.EXTRA).getDoubleOr("tower_q", 0.0D));
	}

	static boolean freeDay() {
		return Math.floorMod(EventUtil.day(), 7) == FREE_DAY;
	}

	// ================================================================== rooms (read-only)

	/** The tower rooms around the hall, highest first (cached; rescanned when asked with {@code fresh}). */
	static List<BlockPos> rooms(UUID owner, Halls.HallView hall, boolean fresh) {
		long now = EventUtil.gameTime();
		List<BlockPos> cached = ROOMS.get(owner);
		if (cached != null && !fresh && now - SCANNED.getOrDefault(owner, 0L) < RECHECK_TICKS) {
			return cached;
		}
		List<BlockPos> rooms = new ArrayList<>();
		ServerLevel level = EventUtil.hallLevel(hall);
		if (level != null && hall.active()) {
			BlockPos stele = hall.pos();
			int minX = (stele.getX() - SEARCH_RADIUS) >> 4;
			int maxX = (stele.getX() + SEARCH_RADIUS) >> 4;
			int minZ = (stele.getZ() - SEARCH_RADIUS) >> 4;
			int maxZ = (stele.getZ() + SEARCH_RADIUS) >> 4;
			for (int cx = minX; cx <= maxX; cx++) {
				for (int cz = minZ; cz <= maxZ; cz++) {
					LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
					if (chunk == null) {
						continue;
					}
					for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
						BlockPos pos = entry.getKey();
						if (entry.getValue() instanceof CampfireBlockEntity && pos.distSqr(stele) <= (double) SEARCH_RADIUS * SEARCH_RADIUS
								&& isRoom(level, pos)) {
							rooms.add(pos.immutable());
						}
					}
				}
			}
		}
		BlockPos stele = hall.pos();
		Comparator<BlockPos> highestFirst = Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed();
		rooms.sort(highestFirst.thenComparingDouble((BlockPos pos) -> pos.distSqr(stele)));
		if (rooms.size() > MAX_ROOMS) {
			rooms = new ArrayList<>(rooms.subList(0, MAX_ROOMS));
		}
		ROOMS.put(owner, rooms);
		SCANNED.put(owner, now);
		return rooms;
	}

	/** A lit Soul Campfire standing on Crying Obsidian (read-only). */
	static boolean isRoom(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(Blocks.SOUL_CAMPFIRE) && CampfireBlock.isLitCampfire(state) && level.getBlockState(pos.below()).is(Blocks.CRYING_OBSIDIAN);
	}

	// ================================================================== training

	static @Nullable String blocker(DiscipleEntity disciple, int floor) {
		if (SectRanks.rank(disciple.data()) < SectRanks.DALAM) {
			// A Murid Dalam perk (DESIGN 20.3.6: missions up to tier 5, the Void Realm, the tower).
			return "kultivator.sectevent.tower.rank";
		}
		if (disciple.data().realm().ordinal() < floor - 1) {
			return "kultivator.sectevent.tower.too_weak";
		}
		if (!freeDay() && SectRanks.pk(disciple.data()) < floor) {
			return "kultivator.sectevent.tower.no_pk";
		}
		return null;
	}

	/** "Latihan di Menara (lantai n)": the disciple walks to room n and meditates there. */
	static boolean train(ServerPlayer player, DiscipleEntity disciple, int floor) {
		UUID owner = player.getUUID();
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || !EventUtil.hallLoaded(hall)) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.no_hall").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		List<BlockPos> rooms = rooms(owner, hall, true);
		if (floor < 1 || floor > rooms.size()) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.tower.no_room", floor).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		String why = blocker(disciple, floor);
		if (why != null) {
			EventUtil.chat(player, Component.translatable(why, floor).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		ServerLevel level = EventUtil.hallLevel(hall);
		BlockPos room = rooms.get(floor - 1);
		BlockPos spot = null;
		for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) {
			Vec3 safe = Targeting.safeSpot(level, disciple, new Vec3(room.getX() + offset[0] + 0.5D, room.getY(), room.getZ() + offset[1] + 0.5D));
			if (safe != null) {
				spot = BlockPos.containing(safe);
				break;
			}
		}
		if (spot == null) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.tower.blocked", floor).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		Duties.release(owner, disciple.soulId(), null);
		stop(owner, disciple.soulId(), false);
		Training training = new Training(owner, disciple.soulId(), floor, room, spot);
		TRAINING.put(disciple.soulId(), training);
		disciple.setActivity(DiscipleEntity.ACTIVITY_TOWER, spot);
		EventUtil.extra(disciple.data()).putInt("tower", floor);
		EventUtil.chat(player, Component.translatable("kultivator.sectevent.tower.start", disciple.getDisplayName(), floor,
				String.format(java.util.Locale.ROOT, "%.1f", XP_BASE + XP_PER_FLOOR * floor)).withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** Ends the disciple's training (by the master, an errand, or when the PK ran out). */
	static void stop(UUID owner, UUID soul, boolean tell) {
		Training training = TRAINING.remove(soul);
		if (training == null) {
			return;
		}
		DiscipleEntity disciple = EventUtil.bySoul(owner, soul);
		if (disciple != null) {
			EventUtil.extra(disciple.data()).remove("tower");
			if (disciple.activity() == DiscipleEntity.ACTIVITY_TOWER) {
				disciple.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			}
			if (tell) {
				EventUtil.chat(EventUtil.player(owner), Component.translatable("kultivator.sectevent.tower.stopped", disciple.getDisplayName())
						.withStyle(ChatFormatting.GRAY));
			}
		}
	}

	static boolean training(UUID soul) {
		return TRAINING.containsKey(soul);
	}

	/** Every server tick: cheap counters; the real work runs once a second per trainee. */
	static void tick(MinecraftServer server) {
		if (TRAINING.isEmpty()) {
			return;
		}
		for (Training training : new ArrayList<>(TRAINING.values())) {
			training.age++;
			if (training.age % 20 == 0) {
				second(training);
			}
		}
	}

	private static void second(Training training) {
		DiscipleEntity disciple = EventUtil.bySoul(training.owner, training.soul);
		Halls.HallView hall = Halls.hallOf(training.owner);
		if (disciple == null || hall == null || !EventUtil.hallLoaded(hall) || EventUtil.hallLevel(hall) != disciple.level()) {
			TRAINING.remove(training.soul);
			if (disciple != null) {
				EventUtil.extra(disciple.data()).remove("tower");
			}
			return;
		}
		ServerLevel level = (ServerLevel) disciple.level();
		if (training.age < training.pausedUntilAge) {
			return;
		}
		if (training.pausedUntilAge > 0 && training.age >= training.pausedUntilAge) {
			// Back in after being thrown out.
			training.pausedUntilAge = 0;
			disciple.setActivity(DiscipleEntity.ACTIVITY_TOWER, training.spot);
		}
		if (disciple.activity() != DiscipleEntity.ACTIVITY_TOWER) {
			// Called away or given another task.
			stop(training.owner, training.soul, false);
			return;
		}
		if (training.age % RECHECK_TICKS == 0 && !isRoom(level, training.room)) {
			EventUtil.chat(EventUtil.player(training.owner), Component.translatable("kultivator.sectevent.tower.room_gone", training.floor)
					.withStyle(ChatFormatting.YELLOW));
			stop(training.owner, training.soul, false);
			return;
		}
		DiscipleData data = disciple.data();
		int n = training.floor;
		if (disciple.isMeditatingNow()) {
			data.addComprehension(COMPREHENSION_PER_FLOOR * n);
		}
		if (training.age % PAY_INTERVAL == 0 && !freeDay()) {
			if (SectRanks.pk(data) < n) {
				EventUtil.chat(EventUtil.player(training.owner), Component.translatable("kultivator.sectevent.tower.pk_out", disciple.getDisplayName())
						.withStyle(ChatFormatting.YELLOW));
				stop(training.owner, training.soul, false);
				return;
			}
			SectRanks.addPk(data, -n, "kultivator.sectevent.pk.tower");
		}
		if (training.age % SURGE_INTERVAL == 0 && level.getRandom().nextDouble() < SURGE_CHANCE_PER_FLOOR * n) {
			surge(training, disciple, level, data, n);
		}
	}

	/** A heart-fire surge: a strong mind resists and grows, a weak one is burnt and thrown out for 30 s. */
	private static void surge(Training training, DiscipleEntity disciple, ServerLevel level, DiscipleData data, int n) {
		ServerPlayer player = EventUtil.player(training.owner);
		Vec3 at = disciple.position();
		if (data.comprehension() >= SURGE_RESIST_PER_FLOOR * n) {
			data.addComprehension(2.0D);
			CompoundTag extra = EventUtil.extra(data);
			extra.putDouble("tower_q", Math.min(QUALITY_MAX, extra.getDoubleOr("tower_q", 0.0D) + QUALITY_STEP));
			EventUtil.fx(level, at, SectEventFxIds.HEART_FIRE, 1, 0.0F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 0.7F);
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.tower.resisted", disciple.getDisplayName()).withStyle(ChatFormatting.AQUA));
			return;
		}
		float damage = disciple.getMaxHealth() * (float) SURGE_DAMAGE;
		float floor = disciple.getMaxHealth() * 0.5F;
		float health = disciple.getHealth();
		if (health > floor) {
			// Heart fire is felt, never fatal: at most down to half health (no damage source, nothing to block).
			disciple.setHealth(Math.max(floor, health - damage));
		}
		disciple.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		training.pausedUntilAge = training.age + THROWN_OUT_TICKS;
		EventUtil.fx(level, at, SectEventFxIds.HEART_FIRE, 0, 0.0F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.8F, 0.6F);
		EventUtil.chat(player, Component.translatable("kultivator.sectevent.tower.burnt", disciple.getDisplayName()).withStyle(ChatFormatting.RED));
	}

	/** Rooms and trainees for the Arena tablet. */
	static CompoundTag forClient(UUID owner, @Nullable Halls.HallView hall) {
		CompoundTag tag = new CompoundTag();
		ListTag rooms = new ListTag();
		if (hall != null) {
			for (BlockPos room : rooms(owner, hall, false)) {
				CompoundTag entry = new CompoundTag();
				entry.putInt("y", room.getY());
				rooms.add(entry);
			}
		}
		tag.put("rooms", rooms);
		tag.putBoolean("free", freeDay());
		ListTag trainees = new ListTag();
		for (Training training : TRAINING.values()) {
			if (training.owner.equals(owner)) {
				DiscipleEntity disciple = EventUtil.bySoul(owner, training.soul);
				if (disciple != null) {
					CompoundTag entry = new CompoundTag();
					entry.store("name", ComponentSerialization.CODEC, disciple.getName());
					entry.putInt("floor", training.floor);
					entry.putBoolean("out", training.pausedUntilAge > training.age);
					trainees.add(entry);
				}
			}
		}
		tag.put("trainees", trainees);
		return tag;
	}

	static void clear() {
		TRAINING.clear();
		ROOMS.clear();
		SCANNED.clear();
	}

	private BlazingTower() {
	}
}
