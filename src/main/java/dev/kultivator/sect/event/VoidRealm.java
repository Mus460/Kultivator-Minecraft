package dev.kultivator.sect.event;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntities;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Alam Dewa Hampa, the Void God Realm, an endless trial tower (DESIGN 20.3.14). A disciple of rank Dalam or higher sits
 * at the stele as a spirit body (activity 7) and fights a phantom of the floor (role PHANTOM, a {@link DuelRules} pair,
 * never saved): health {@code 20 x 1.12^f}, damage {@code 3 x 1.10^f}, armour {@code min(20, f / 3)}, and from floor 10
 * one rival art every 8 s. One climb per disciple per in-game day, up to 5 floors in a row with a 3 s pause and a 25%
 * heal between floors; one phantom per hall at a time (climbs queue). A loss leaves Luka Jiwa for 5 minutes (-20% damage,
 * no kill XP) and nobody dies.
 * <p>
 * Sumber: Perfect World ch. 339-345 (fighting in spirit in the Void God Realm, injuries carried over, the Starting
 * Ground and the long stone stairway) and ch. 261 (the Devil Refinement Cave); Tales of Demons and Gods ch. 48 (the
 * Heavenly Sacred Border, passed by only three in centuries).
 */
public final class VoidRealm {

	public static final int MIN_RANK = 2;
	public static final int FLOORS_PER_CLIMB = 5;
	public static final int PAUSE_TICKS = 60;
	public static final float HEAL_BETWEEN = 0.25F;
	public static final int BOUT_TICKS = 1800;
	public static final double RING_OUT = 12.0D;
	public static final double PHANTOM_DISTANCE = 4.0D;
	public static final long INJURY_TICKS = 6000L;
	public static final double INJURY_DAMAGE = 0.8D;
	/** First clear of floor f: {@code 4% x (1 + f / 20)} of a bar; a repeat clear 25% of that. */
	public static final double FIRST_CLEAR_SHARE = 0.04D;
	public static final double REPEAT_SHARE = 0.25D;
	public static final int SACRED_BORDER = 50;
	public static final int DEVIL_CAVE = 60;
	/** Attribute caps: health above 1,024 turns into damage reduction. */
	private static final double HEALTH_CAP = 1024.0D;
	private static final Identifier HEALTH_ID = Kultivator.id("void_phantom_health");
	private static final Identifier ATTACK_ID = Kultivator.id("void_phantom_attack");
	private static final Identifier ARMOR_ID = Kultivator.id("void_phantom_armor");

	private static final class Climb {
		final UUID owner;
		final UUID soul;
		final DiscipleEntity body;
		final ServerLevel level;
		final Vec3 center;
		final BlockPos spot;
		int floor;
		int cleared;
		int pause;
		@Nullable DiscipleEntity phantom;

		Climb(UUID owner, UUID soul, DiscipleEntity body, ServerLevel level, Vec3 center, BlockPos spot, int floor) {
			this.owner = owner;
			this.soul = soul;
			this.body = body;
			this.level = level;
			this.center = center;
			this.spot = spot;
			this.floor = floor;
		}
	}

	/** Phantom entity UUID -> its floor. */
	private static final Map<UUID, Integer> PHANTOMS = new HashMap<>();
	private static final Map<UUID, Deque<UUID>> QUEUES = new HashMap<>();
	private static final Map<UUID, Climb> CLIMBS = new HashMap<>();

	/** Called once from {@link SectEvents#init()}. */
	static void init() {
		DiscipleHooks.addStats((entity, sink) -> {
			Integer floor = entity.role() == DiscipleEntity.Role.PHANTOM ? PHANTOMS.get(entity.getUUID()) : null;
			if (floor == null) {
				return;
			}
			sink.add(Attributes.MAX_HEALTH, HEALTH_ID, Math.min(HEALTH_CAP, phantomHealth(floor)) - DiscipleEntities.BASE_HEALTH,
					AttributeModifier.Operation.ADD_VALUE);
			sink.add(Attributes.ATTACK_DAMAGE, ATTACK_ID, phantomDamage(floor) - DiscipleEntities.BASE_ATTACK, AttributeModifier.Operation.ADD_VALUE);
			sink.add(Attributes.ARMOR, ARMOR_ID, Math.min(20.0D, floor / 3.0D) - DiscipleEntities.BASE_ARMOR, AttributeModifier.Operation.ADD_VALUE);
		});
		// Health above the attribute cap becomes damage reduction.
		DiscipleHooks.addHurtFactor((entity, source) -> {
			Integer floor = entity.role() == DiscipleEntity.Role.PHANTOM ? PHANTOMS.get(entity.getUUID()) : null;
			double health = floor == null ? 0.0D : phantomHealth(floor);
			return health > HEALTH_CAP ? Math.max(0.02D, HEALTH_CAP / health) : 1.0D;
		});
		// Luka Jiwa: -20% damage and no kill XP for 5 minutes.
		DiscipleHooks.addDamageFactor((entity, target, technique) -> injured(entity.data()) ? INJURY_DAMAGE : 1.0D);
		DiscipleHooks.addXpFactor((entity, data, source) -> source == dev.kultivator.data.XpSource.COMBAT && injured(data) ? 0.0D : 1.0D);
	}

	// ================================================================== API (DESIGN 20.4.5)

	/** The best floor the disciple cleared (0 none). */
	public static int bestFloor(DiscipleData data) {
		return voidTag(data).getIntOr("best", 0);
	}

	/** True once floor 60, the Devil Refinement Cave, was cleared. */
	public static boolean clearedDevilCave(DiscipleData data) {
		return voidTag(data).getBooleanOr("devil", false);
	}

	private static CompoundTag voidTag(DiscipleData data) {
		return data.extra().getCompoundOrEmpty(Duties.EXTRA).getCompoundOrEmpty("void");
	}

	// ================================================================== numbers

	public static double phantomHealth(int floor) {
		return 20.0D * Math.pow(1.12D, floor);
	}

	public static double phantomDamage(int floor) {
		return 3.0D * Math.pow(1.10D, floor);
	}

	/** The floor's name key: Tanah Permulaan ... Hampa Tanpa Dasar N. */
	public static Component floorName(int floor) {
		if (floor <= 9) {
			return Component.translatable("kultivator.sectevent.void.floor.start");
		}
		if (floor <= 19) {
			return Component.translatable("kultivator.sectevent.void.floor.stairs");
		}
		if (floor <= 29) {
			return Component.translatable("kultivator.sectevent.void.floor.clouds");
		}
		if (floor <= 39) {
			return Component.translatable("kultivator.sectevent.void.floor.palace");
		}
		if (floor <= 49) {
			return Component.translatable("kultivator.sectevent.void.floor.peak");
		}
		if (floor == SACRED_BORDER) {
			return Component.translatable("kultivator.sectevent.void.floor.border");
		}
		if (floor <= 59) {
			return Component.translatable("kultivator.sectevent.void.floor.beyond");
		}
		if (floor == DEVIL_CAVE) {
			return Component.translatable("kultivator.sectevent.void.floor.devil");
		}
		return Component.translatable("kultivator.sectevent.void.floor.bottomless", floor - DEVIL_CAVE);
	}

	static int phantomFloor(DiscipleEntity phantom) {
		return PHANTOMS.getOrDefault(phantom.getUUID(), 0);
	}

	static boolean injured(DiscipleData data) {
		return data.extra().getCompoundOrEmpty(Duties.EXTRA).getLongOr("injury_until", 0L) > EventUtil.gameTime();
	}

	// ================================================================== entering and the queue

	static @Nullable String blocker(DiscipleEntity disciple) {
		if (Expeditions.refuses(disciple)) {
			return Expeditions.REFUSES;
		}
		DiscipleData data = disciple.data();
		if (SectRanks.rank(data) < MIN_RANK) {
			return "kultivator.sectevent.void.need_inner";
		}
		if (voidTag(data).getLongOr("day", Long.MIN_VALUE) == EventUtil.day()) {
			return "kultivator.sectevent.void.today";
		}
		return null;
	}

	/** "Masuk Alam Hampa": queues the disciple for a climb. */
	static boolean enter(ServerPlayer player, DiscipleEntity disciple) {
		UUID owner = player.getUUID();
		String why = blocker(disciple);
		if (why != null) {
			EventUtil.chat(player, Component.translatable(why).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		Deque<UUID> queue = QUEUES.computeIfAbsent(owner, id -> new ArrayDeque<>());
		Climb climb = CLIMBS.get(owner);
		if (queue.contains(disciple.soulId()) || climb != null && climb.soul.equals(disciple.soulId())) {
			return false;
		}
		queue.add(disciple.soulId());
		EventUtil.chat(player, Component.translatable("kultivator.sectevent.void.queued", disciple.getDisplayName(), queue.size())
				.withStyle(ChatFormatting.DARK_PURPLE));
		return true;
	}

	static void dequeue(UUID owner, UUID soul) {
		Deque<UUID> queue = QUEUES.get(owner);
		if (queue != null) {
			queue.remove(soul);
		}
	}

	public static boolean climbing(UUID owner) {
		return CLIMBS.containsKey(owner);
	}

	/** Test command: the disciple's next climb starts at {@code floor} (the climb of today is allowed again). */
	static void setStart(DiscipleEntity disciple, int floor) {
		CompoundTag tag = EventUtil.sub(EventUtil.extra(disciple.data()), "void");
		tag.putInt("start", Math.max(1, floor));
		tag.remove("day");
	}

	// ================================================================== ticking

	static void tick(MinecraftServer server) {
		if (!CLIMBS.isEmpty()) {
			for (Climb climb : new ArrayList<>(CLIMBS.values())) {
				tickClimb(climb);
			}
		}
		if (QUEUES.isEmpty() || server.getTickCount() % 20 != 0) {
			return;
		}
		for (Map.Entry<UUID, Deque<UUID>> entry : new ArrayList<>(QUEUES.entrySet())) {
			UUID owner = entry.getKey();
			Deque<UUID> queue = entry.getValue();
			if (queue.isEmpty()) {
				QUEUES.remove(owner);
				continue;
			}
			if (CLIMBS.containsKey(owner) || DuelArena.busy(owner) || Tournament.running(owner) || HundredDynasties.running(owner)
					|| PromotionTrial.running(owner)) {
				continue;
			}
			UUID soul = queue.poll();
			if (soul != null) {
				start(owner, soul);
			}
		}
	}

	private static void start(UUID owner, UUID soul) {
		ServerPlayer player = EventUtil.player(owner);
		Halls.HallView hall = Halls.hallOf(owner);
		DiscipleEntity body = EventUtil.bySoul(owner, soul);
		if (hall == null || !EventUtil.hallLoaded(hall) || body == null || !EventUtil.free(body) || !EventUtil.nearHall(body, hall, 64.0D)) {
			if (body != null) {
				EventUtil.chat(player, Component.translatable("kultivator.sectevent.void.not_ready", body.getDisplayName()).withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		ServerLevel level = EventUtil.hallLevel(hall);
		Vec3 center = EventUtil.center(hall);
		Vec3 spotVec = Targeting.safeSpot(level, body, center.add(2.0D, 0.0D, 0.0D));
		if (spotVec == null) {
			spotVec = Targeting.safeSpot(level, body, center.add(-2.0D, 0.0D, 0.0D));
		}
		if (spotVec == null) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.arena.blocked").withStyle(ChatFormatting.YELLOW));
			return;
		}
		Duties.release(owner, soul, null);
		BlazingTower.stop(owner, soul, false);
		body.setTarget(null);
		body.snapTo(spotVec.x, spotVec.y, spotVec.z, body.getYRot(), 0.0F);
		body.getNavigation().stop();
		BlockPos spot = BlockPos.containing(spotVec);
		body.setActivity(DiscipleEntity.ACTIVITY_VOID_BODY, spot);
		CompoundTag tag = EventUtil.sub(EventUtil.extra(body.data()), "void");
		int floor = tag.contains("start") ? tag.getIntOr("start", 1) : bestFloor(body.data()) + 1;
		tag.remove("start");
		tag.putLong("day", EventUtil.day());
		Climb climb = new Climb(owner, soul, body, level, center, spot, Math.max(1, floor));
		climb.pause = PAUSE_TICKS;
		CLIMBS.put(owner, climb);
		EventUtil.fx(level, spotVec, SectEventFxIds.VOID_PORTAL, PAUSE_TICKS + 40, 1.6F);
		level.playSound(null, spotVec.x, spotVec.y, spotVec.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 1.0F, 0.5F);
		EventUtil.chat(player, Component.translatable("kultivator.sectevent.void.enter", body.getDisplayName(), climb.floor, floorName(climb.floor))
				.withStyle(ChatFormatting.DARK_PURPLE));
	}

	private static void tickClimb(Climb climb) {
		DiscipleEntity body = climb.body;
		if (body.isRemoved() || !body.isAlive() || body.level() != climb.level || EventUtil.player(climb.owner) == null) {
			end(climb, false);
			return;
		}
		if (climb.phantom != null) {
			return;
		}
		if (body.activity() != DiscipleEntity.ACTIVITY_VOID_BODY) {
			// Called away ("murid panggil"): the climb ends without a result.
			end(climb, false);
			return;
		}
		if (--climb.pause > 0) {
			return;
		}
		spawnPhantom(climb);
	}

	private static void spawnPhantom(Climb climb) {
		DiscipleEntity body = climb.body;
		ServerLevel level = climb.level;
		Vec3 spot = null;
		for (int attempt = 0; attempt < 8 && spot == null; attempt++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
			Vec3 near = body.position().add(Math.cos(angle) * PHANTOM_DISTANCE, 0.0D, Math.sin(angle) * PHANTOM_DISTANCE);
			if (near.distanceToSqr(climb.center) <= 8.0D * 8.0D && level.isLoaded(BlockPos.containing(near))) {
				spot = Targeting.safeSpot(level, body, near);
			}
		}
		if (spot == null) {
			EventUtil.chat(EventUtil.player(climb.owner), Component.translatable("kultivator.sectevent.arena.blocked").withStyle(ChatFormatting.YELLOW));
			end(climb, false);
			return;
		}
		int floor = climb.floor;
		DiscipleData data = new DiscipleData();
		Component name = Component.translatable("kultivator.sectevent.void.phantom", floor);
		DiscipleEntity phantom = Disciples.spawnUnowned(level, spot, data, name, body.skinIndex(), body.slimSkin(), DiscipleEntity.Role.PHANTOM,
				BlockPos.containing(climb.center));
		if (phantom == null) {
			end(climb, false);
			return;
		}
		PHANTOMS.put(phantom.getUUID(), floor);
		phantom.refreshSynced();
		phantom.setHealth(phantom.getMaxHealth());
		climb.phantom = phantom;
		EventUtil.fx(level, spot, SectEventFxIds.VOID_PORTAL, 40, 1.0F);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 0.8F, 0.7F);
		DuelArena.Bout bout = DuelArena.start(climb.owner, level, climb.center, body, phantom, BOUT_TICKS, RING_OUT, false,
				(winner, loser, reason) -> boutEnded(climb, winner, reason));
		if (bout == null) {
			dissolve(climb);
			end(climb, false);
			return;
		}
		ServerPlayer player = EventUtil.player(climb.owner);
		if (player != null) {
			Msg.actionBar(player, Component.translatable("kultivator.sectevent.void.floor", floor, floorName(floor)).withStyle(ChatFormatting.DARK_PURPLE));
		}
	}

	private static void boutEnded(Climb climb, @Nullable LivingEntity winner, DuelRules.Reason reason) {
		dissolve(climb);
		if (CLIMBS.get(climb.owner) != climb) {
			return;
		}
		ServerPlayer player = EventUtil.player(climb.owner);
		DiscipleEntity body = climb.body;
		if (reason == DuelRules.Reason.CANCELLED || winner == null) {
			end(climb, false);
			return;
		}
		if (winner != body) {
			EventUtil.extra(body.data()).putLong("injury_until", EventUtil.gameTime() + INJURY_TICKS);
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.void.lost", body.getDisplayName(), climb.floor,
					(int) (INJURY_TICKS / 1200L)).withStyle(ChatFormatting.RED));
			end(climb, true);
			return;
		}
		cleared(climb, player);
		climb.cleared++;
		if (climb.cleared >= FLOORS_PER_CLIMB) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.void.climb_done", body.getDisplayName(), climb.floor)
					.withStyle(ChatFormatting.LIGHT_PURPLE));
			end(climb, true);
			return;
		}
		body.heal(body.getMaxHealth() * HEAL_BETWEEN);
		climb.floor++;
		climb.pause = PAUSE_TICKS;
		body.setTarget(null);
	}

	/** Rewards of a cleared floor (first clear or repeat). */
	private static void cleared(Climb climb, @Nullable ServerPlayer player) {
		DiscipleEntity body = climb.body;
		DiscipleData data = body.data();
		int floor = climb.floor;
		CompoundTag tag = EventUtil.sub(EventUtil.extra(data), "void");
		int best = tag.getIntOr("best", 0);
		double share = FIRST_CLEAR_SHARE * (1.0D + floor / 20.0D);
		if (floor > best) {
			tag.putInt("best", floor);
			DisciplePaths.grantBarShare(data, share);
			if (floor % 10 == 0) {
				data.addComprehension(2.0D);
			}
			if (floor == SACRED_BORDER) {
				EventUtil.sub(EventUtil.extra(data), "titles").putLong(Tournament.TITLE_SACRED_BORDER, Long.MAX_VALUE);
				if (player != null) {
					Advancements.grant(player, "batas_suci");
					Msg.broadcast(player.level().getServer(), Component.translatable("kultivator.sectevent.void.border_broadcast",
							body.getDisplayName(), player.getDisplayName()).withStyle(ChatFormatting.GOLD));
				}
			}
			if (floor == DEVIL_CAVE) {
				tag.putBoolean("devil", true);
				EventUtil.chat(player, Component.translatable("kultivator.sectevent.void.devil", body.getDisplayName()).withStyle(ChatFormatting.DARK_RED));
			}
			if (floor >= 10 && player != null) {
				Advancements.grant(player, "void_floor_10");
			}
			if ((floor == SACRED_BORDER || floor == DEVIL_CAVE) && body.level() instanceof net.minecraft.server.level.ServerLevel bodyLevel) {
				dev.kultivator.epic.EpicHooks.extra(bodyLevel, dev.kultivator.epic.EpicIds.EXTRA_VOID_BORDER, player, body, body.position(), floor, 0);
			}
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.void.first_clear", body.getDisplayName(), floor,
					(int) Math.round(share * 100.0D)).withStyle(ChatFormatting.LIGHT_PURPLE));
		} else {
			DisciplePaths.grantBarShare(data, share * REPEAT_SHARE);
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.void.repeat_clear", body.getDisplayName(), floor)
					.withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		body.refreshSynced();
		CompoundTag board = EventUtil.sub(EventUtil.section(climb.owner), "void_board");
		CompoundTag entry = EventUtil.sub(board, climb.soul.toString());
		entry.putInt("best", Math.max(entry.getIntOr("best", 0), floor));
		entry.store("name", ComponentSerialization.CODEC, body.getName());
		EventUtil.dirty();
	}

	private static void dissolve(Climb climb) {
		DiscipleEntity phantom = climb.phantom;
		climb.phantom = null;
		if (phantom != null) {
			PHANTOMS.remove(phantom.getUUID());
			RivalSkills.forget(phantom.getUUID());
			if (!phantom.isRemoved()) {
				EventUtil.fx(climb.level, phantom.position(), SectEventFxIds.PHANTOM_DISSOLVE, 0, 0.0F);
				climb.level.playSound(null, phantom.getX(), phantom.getY(), phantom.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.0F, 0.6F);
				phantom.discard();
			}
		}
	}

	private static void end(Climb climb, boolean finished) {
		if (climb.phantom != null) {
			DuelRules.cancel(climb.body);
			dissolve(climb);
		}
		CLIMBS.remove(climb.owner, climb);
		DiscipleEntity body = climb.body;
		if (!body.isRemoved() && body.activity() == DiscipleEntity.ACTIVITY_VOID_BODY) {
			body.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
		if (!finished && climb.cleared == 0 && body.isAlive()) {
			// A climb that ended without a fight result and without a cleared floor does not use up the day.
			EventUtil.sub(EventUtil.extra(body.data()), "void").remove("day");
		}
	}

	/** The queue and the current climb for the Arena tablet, plus the best-floor board. */
	static CompoundTag forClient(UUID owner) {
		CompoundTag tag = new CompoundTag();
		Climb climb = CLIMBS.get(owner);
		if (climb != null) {
			tag.store("climber", ComponentSerialization.CODEC, climb.body.getName());
			tag.putInt("floor", climb.floor);
			tag.putInt("cleared", climb.cleared);
		}
		ListTag queue = new ListTag();
		Deque<UUID> waiting = QUEUES.get(owner);
		if (waiting != null) {
			for (UUID soul : waiting) {
				DiscipleEntity body = EventUtil.bySoul(owner, soul);
				if (body != null) {
					CompoundTag entry = new CompoundTag();
					entry.store("name", ComponentSerialization.CODEC, body.getName());
					queue.add(entry);
				}
			}
		}
		tag.put("queue", queue);
		ListTag board = new ListTag();
		CompoundTag saved = EventUtil.section(owner).getCompoundOrEmpty("void_board");
		List<CompoundTag> entries = new ArrayList<>();
		for (String key : saved.keySet()) {
			entries.add(saved.getCompoundOrEmpty(key));
		}
		entries.sort((a, b) -> Integer.compare(b.getIntOr("best", 0), a.getIntOr("best", 0)));
		for (int i = 0; i < Math.min(10, entries.size()); i++) {
			board.add(entries.get(i).copy());
		}
		tag.put("board", board);
		return tag;
	}

	static void clear() {
		PHANTOMS.clear();
		QUEUES.clear();
		CLIMBS.clear();
	}

	private VoidRealm() {
	}
}
