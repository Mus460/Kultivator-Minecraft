package dev.kultivator.dunia.naga;

import dev.kultivator.beast.BeastData;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.fx.Fx;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Penjaga Raga (DESIGN 34.4.3 {@code SOUL_JOURNEY}: "B: beasts guard the body"). While a cultivator's soul walks Dunia
 * Bawah, its body stays behind as module D's mannequin, and a hit on it pulls the soul back. The owner's spirit beasts
 * that are loaded within 64 blocks of the body gather around it and go for every monster within 12 blocks; a summoned
 * Jiao stays behind instead of returning to its pearl, circles over the body and breathes at the monsters. Module D's
 * body is found once, at the start, by its tag {@code kultivator.tubuh.<uuid>} (data only: the modules never import each
 * other). Entity goals only (targets, paths, the breath); no block is touched. Config {@code naga.guardBody}.
 * <p>
 * {@code uji naga jaga} starts a 60 s guard on the spot the tester stands on, without a soul journey.
 */
final class BodyGuards {

	/** Module D's tag on the body: {@code kultivator.tubuh.<owner uuid>}. */
	static final String BODY_TAG_PREFIX = "kultivator.tubuh.";
	/** Beasts closer than this to the body just stay; farther ones walk back. */
	static final double GATHER = 6.0D;
	/** Monsters within this of the body are attacked. */
	static final double WATCH = 12.0D;
	/** The owner's beasts within this of the body answer the call. */
	static final double REACH = 64.0D;
	public static final int TEST_TICKS = 1200;

	private static final class Guard {
		final UUID owner;
		final ResourceKey<Level> dimension;
		final Vec3 body;
		final long until;
		int driven;
		int passes;

		Guard(UUID owner, ResourceKey<Level> dimension, Vec3 body, long until) {
			this.owner = owner;
			this.dimension = dimension;
			this.body = body;
			this.until = until;
		}
	}

	private static final Map<UUID, Guard> GUARDS = new HashMap<>();
	/** Owners whose summoned Jiao stayed behind with the body (set at the level change, before the body is known). */
	private static final Set<UUID> JIAO_STAYS = new HashSet<>();

	static void init() {
		DuniaEvents.SOUL_JOURNEY.register((player, entered) -> {
			if (entered) {
				start(player);
			} else {
				stop(player.getUUID(), player.level().getServer(), true);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(BodyGuards::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			GUARDS.clear();
			JIAO_STAYS.clear();
		});
	}

	/**
	 * At a level change: true when {@code owner} is a soul leaving its body (module D's service) and the guard is on; its
	 * summoned Jiao then stays behind instead of going back into the pearl.
	 */
	static boolean jiaoStays(ServerPlayer owner) {
		if (!Naga.config().guardBody || !DuniaApi.souls().isSoul(owner)) {
			return false;
		}
		JIAO_STAYS.add(owner.getUUID());
		return true;
	}

	/** True while {@code owner}'s Jiao guards a body in another level. */
	static boolean jiaoGuards(UUID owner) {
		return JIAO_STAYS.contains(owner);
	}

	/** The body {@code owner}'s beasts guard in {@code level}, or null. */
	static @Nullable Vec3 bodyOf(UUID owner, ServerLevel level) {
		Guard guard = GUARDS.get(owner);
		return guard != null && guard.dimension == level.dimension() ? guard.body : null;
	}

	// ================================================================== start and stop

	private static void start(ServerPlayer player) {
		if (!Naga.config().guardBody) {
			JIAO_STAYS.remove(player.getUUID());
			return;
		}
		MinecraftServer server = player.level().getServer();
		String tag = BODY_TAG_PREFIX + player.getUUID();
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity body : level.getEntities(EntityTypes.MANNEQUIN, entity -> entity.entityTags().contains(tag))) {
				begin(player, level, body.position(), Long.MAX_VALUE);
				return;
			}
		}
		// no body found (another module's journey, or it was removed): nothing to guard
		JIAO_STAYS.remove(player.getUUID());
	}

	/** {@code uji naga jaga}: a 60 s guard on the tester's spot; returns how many guard it. */
	static int startTest(ServerPlayer player) {
		ServerLevel level = player.level();
		return begin(player, level, player.position(), level.getServer().getTickCount() + (long) TEST_TICKS);
	}

	private static int begin(ServerPlayer player, ServerLevel level, Vec3 body, long until) {
		Guard guard = new Guard(player.getUUID(), level.dimension(), body, until);
		GUARDS.put(player.getUUID(), guard);
		int count = guardsOf(level, guard).size();
		NagaLiarEntity jiao = TamedJiao.active(player);
		if (jiao != null && jiao.level() == level) {
			count++;
		}
		if (count > 0) {
			Msg.chat(player, Component.translatable("kultivator.naga.guard.start", count).withStyle(ChatFormatting.GOLD));
			level.playSound(null, body.x, body.y, body.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 0.8F, 1.2F);
			Fx.ring(level, body.add(0.0D, 0.1D, 0.0D), 0xFFE08A, 3.0F);
			NagaPayloads.moment(level, NagaPayloads.MOMENT_GUARD, -1, body, 0, count, 0, 0, null, 48.0D);
		} else {
			Msg.chat(player, Component.translatable("kultivator.naga.guard.none").withStyle(ChatFormatting.GRAY));
		}
		return count;
	}

	private static void stop(UUID owner, @Nullable MinecraftServer server, boolean returned) {
		JIAO_STAYS.remove(owner);
		Guard guard = GUARDS.remove(owner);
		if (guard == null || server == null) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			Msg.chat(player, Component.translatable(returned ? "kultivator.naga.guard.end" : "kultivator.naga.guard.test_end", guard.driven)
					.withStyle(ChatFormatting.GOLD));
		}
		ServerLevel level = server.getLevel(guard.dimension);
		if (level != null) {
			for (Mob beast : guardsOf(level, guard)) {
				beast.setTarget(null);
			}
		}
	}

	// ================================================================== the watch (once a second)

	private static void tick(MinecraftServer server) {
		if (GUARDS.isEmpty() || server.getTickCount() % 20 != 7) {
			return;
		}
		long now = server.getTickCount();
		for (Guard guard : new ArrayList<>(GUARDS.values())) {
			if (now > guard.until) {
				stop(guard.owner, server, false);
				continue;
			}
			ServerLevel level = server.getLevel(guard.dimension);
			if (level == null || !level.hasChunkAt(BlockPos.containing(guard.body))) {
				continue;
			}
			guard.passes++;
			List<Mob> beasts = guardsOf(level, guard);
			if (beasts.isEmpty()) {
				continue;
			}
			List<Mob> foes = foesNear(level, guard.body);
			for (Mob beast : beasts) {
				if (beast instanceof TamableAnimal tame && tame.isOrderedToSit()) {
					continue;
				}
				if (!foes.isEmpty()) {
					Mob foe = foes.get(0);
					double best = beast.distanceToSqr(foe);
					for (Mob other : foes) {
						double d = beast.distanceToSqr(other);
						if (d < best) {
							best = d;
							foe = other;
						}
					}
					if (beast.getTarget() != foe) {
						beast.setTarget(foe);
					}
				} else if (beast.distanceToSqr(guard.body) > GATHER * GATHER) {
					beast.setTarget(null);
					beast.getNavigation().moveTo(guard.body.x, guard.body.y, guard.body.z, 1.25D);
				}
			}
			if (guard.passes % 4 == 0) {
				Fx.ring(level, guard.body.add(0.0D, 0.1D, 0.0D), SpiritBeasts.tierColor(BeastData.WOLF, 4), 2.6F);
			}
		}
	}

	/** The owner's loaded spirit beasts in {@code level} within {@link #REACH} of the body. */
	private static List<Mob> guardsOf(ServerLevel level, Guard guard) {
		List<Mob> list = new ArrayList<>();
		for (Mob beast : SpiritBeasts.loadedBeasts(guard.owner)) {
			if (beast.level() == level && beast.isAlive() && beast.distanceToSqr(guard.body) <= REACH * REACH) {
				list.add(beast);
			}
		}
		return list;
	}

	/** Monsters within {@link #WATCH} of {@code body}, nearest first. */
	static List<Mob> foesNear(ServerLevel level, Vec3 body) {
		List<Mob> foes = level.getEntitiesOfClass(Mob.class, new AABB(body, body).inflate(WATCH, 6.0D, WATCH),
				mob -> mob instanceof Enemy && mob.isAlive() && !(mob instanceof NagaLiarEntity));
		foes.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(body)));
		return foes;
	}

	/** A guard killed {@code killed}: counted, told to the owner (who may be in Dunia Bawah), and the advancement. */
	static void onKill(ServerLevel level, Entity killer, LivingEntity killed) {
		if (GUARDS.isEmpty() || !(killed instanceof Enemy)) {
			return;
		}
		UUID owner = null;
		if (killer instanceof NagaLiarEntity dragon && dragon.isTamed()) {
			owner = dragon.ownerId();
		} else if (killer instanceof Mob mob) {
			BeastData data = SpiritBeasts.data(mob);
			owner = data == null ? null : data.owner();
		}
		Guard guard = owner == null ? null : GUARDS.get(owner);
		if (guard == null || guard.dimension != level.dimension() || killed.position().distanceToSqr(guard.body) > (WATCH + 8.0D) * (WATCH + 8.0D)) {
			return;
		}
		guard.driven++;
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		if (player != null) {
			Msg.chat(player, Component.translatable("kultivator.naga.guard.driven", killer.getDisplayName(), killed.getDisplayName())
					.withStyle(ChatFormatting.YELLOW));
			NagaFeats.guarded(player);
		}
	}

	/** {@code uji naga info}: the guards alive. */
	static List<String> describe() {
		List<String> lines = new ArrayList<>();
		for (Guard guard : GUARDS.values()) {
			lines.add(guard.owner.toString().substring(0, 8) + " @ " + BlockPos.containing(guard.body).toShortString() + " driven " + guard.driven
					+ (guard.until == Long.MAX_VALUE ? " (soul)" : " (test)"));
		}
		return lines;
	}

	private BodyGuards() {
	}
}
