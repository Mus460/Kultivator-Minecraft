package dev.kultivator.sect.event;

import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DuelRules;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One bout at a time in the particle ring at a hall's stele (DESIGN 20.3.12), on top of the spine's {@link DuelRules}:
 * the 10% health end and the time limit are DuelRules', this adds the ring (out of {@code outRadius} for 3 s loses),
 * two vanilla boss bars with the pairing and both health bars, one spark per landed hit and the underdog bonus (+15%
 * damage per realm below the opponent, at most +45%). Used by tournaments, the Hundred Dynasties War and the Void Realm.
 * Entities only; nothing here touches a block.
 */
final class DuelArena {

	/** The ring drawn at the stele and the line whose crossing for 3 s loses a bout. */
	public static final float RING_RADIUS = 9.0F;
	public static final double OUT_RADIUS = 11.0D;
	public static final int OUT_TICKS = 60;
	public static final double UNDERDOG_PER_REALM = 0.15D;
	public static final double UNDERDOG_MAX = 0.45D;
	/** Boss bars are shown to players this close to the ring. */
	private static final double VIEW_RANGE = 48.0D;

	/** Told once when the bout ends (winner and loser are null when it was cancelled). */
	@FunctionalInterface
	interface Listener {
		void onEnd(@Nullable LivingEntity winner, @Nullable LivingEntity loser, DuelRules.Reason reason);
	}

	static final class Bout {
		final UUID owner;
		final ServerLevel level;
		final Vec3 center;
		final LivingEntity a;
		final LivingEntity b;
		final double outRadius;
		final boolean underdog;
		final Listener listener;
		final ServerBossEvent barA;
		final ServerBossEvent barB;
		int outA;
		int outB;
		int age;
		boolean ended;

		Bout(UUID owner, ServerLevel level, Vec3 center, LivingEntity a, LivingEntity b, double outRadius, boolean underdog, Listener listener) {
			this.owner = owner;
			this.level = level;
			this.center = center;
			this.a = a;
			this.b = b;
			this.outRadius = outRadius;
			this.underdog = underdog;
			this.listener = listener;
			this.barA = new ServerBossEvent(Mth.createInsecureUUID(level.getRandom()), barName(a, b), BossEvent.BossBarColor.BLUE,
					BossEvent.BossBarOverlay.NOTCHED_10);
			this.barB = new ServerBossEvent(Mth.createInsecureUUID(level.getRandom()), barName(b, a), BossEvent.BossBarColor.RED,
					BossEvent.BossBarOverlay.NOTCHED_10);
		}
	}

	private static final Map<UUID, Bout> BOUTS = new HashMap<>();
	/** Entities fighting a bout with the underdog bonus. */
	private static final Set<UUID> UNDERDOGS = new HashSet<>();

	static boolean busy(UUID owner) {
		return BOUTS.containsKey(owner);
	}

	static @Nullable Bout bout(UUID owner) {
		return BOUTS.get(owner);
	}

	/**
	 * Starts a bout between {@code a} and {@code b} at {@code center} (both must already stand there). Null when the arena
	 * is busy or DuelRules refused (one of them already duels, different levels...).
	 */
	static @Nullable Bout start(UUID owner, ServerLevel level, Vec3 center, LivingEntity a, LivingEntity b, int maxTicks, double outRadius,
			boolean underdog, Listener listener) {
		if (BOUTS.containsKey(owner)) {
			return null;
		}
		Bout bout = new Bout(owner, level, center, a, b, outRadius, underdog, listener);
		if (!DuelRules.begin(a, b, maxTicks, (winner, loser, reason) -> finish(bout, winner, loser, reason))) {
			return null;
		}
		BOUTS.put(owner, bout);
		if (underdog) {
			UNDERDOGS.add(a.getUUID());
			UNDERDOGS.add(b.getUUID());
		}
		updateBars(bout, true);
		EventUtil.fx(level, center, SectEventFxIds.DUEL_RING, 120, (float) RING_RADIUS);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.0F, 0.8F);
		return bout;
	}

	/** Ends the owner's running bout without a result. */
	static void cancel(UUID owner) {
		Bout bout = BOUTS.get(owner);
		if (bout != null) {
			DuelRules.cancel(bout.a);
			if (!bout.ended) {
				finish(bout, null, null, DuelRules.Reason.CANCELLED);
			}
		}
	}

	/** Every server tick: the ring line, the boss bars, the ring particles. */
	static void tick(MinecraftServer server) {
		if (BOUTS.isEmpty()) {
			return;
		}
		for (Bout bout : new ArrayList<>(BOUTS.values())) {
			if (bout.ended) {
				continue;
			}
			bout.age++;
			if (!DuelRules.dueling(bout.a, bout.b)) {
				// DuelRules ended it on its own path already (listener ran) or lost it: make sure nothing is left behind.
				if (!bout.ended) {
					finish(bout, null, null, DuelRules.Reason.CANCELLED);
				}
				continue;
			}
			bout.outA = outside(bout, bout.a) ? bout.outA + 1 : 0;
			bout.outB = outside(bout, bout.b) ? bout.outB + 1 : 0;
			if (bout.outA >= OUT_TICKS) {
				DuelRules.end(bout.a, null, DuelRules.Reason.LEFT_RING);
				continue;
			}
			if (bout.outB >= OUT_TICKS) {
				DuelRules.end(bout.b, null, DuelRules.Reason.LEFT_RING);
				continue;
			}
			if (bout.age % 5 == 0) {
				updateBars(bout, bout.age % 20 == 0);
			}
			if (bout.age % 100 == 0) {
				EventUtil.fx(bout.level, bout.center, SectEventFxIds.DUEL_RING, 120, (float) RING_RADIUS);
			}
		}
	}

	private static boolean outside(Bout bout, LivingEntity fighter) {
		double dx = fighter.getX() - bout.center.x;
		double dz = fighter.getZ() - bout.center.z;
		return dx * dx + dz * dz > bout.outRadius * bout.outRadius || Math.abs(fighter.getY() - bout.center.y) > 8.0D;
	}

	private static void updateBars(Bout bout, boolean viewers) {
		bout.barA.setProgress(fraction(bout.a));
		bout.barB.setProgress(fraction(bout.b));
		if (viewers) {
			for (ServerPlayer player : PlayerLookup.around(bout.level, bout.center, VIEW_RANGE)) {
				bout.barA.addPlayer(player);
				bout.barB.addPlayer(player);
			}
			for (ServerPlayer player : List.copyOf(bout.barA.getPlayers())) {
				if (player.level() != bout.level || player.distanceToSqr(bout.center) > VIEW_RANGE * VIEW_RANGE * 1.5D) {
					bout.barA.removePlayer(player);
					bout.barB.removePlayer(player);
				}
			}
		}
	}

	private static float fraction(LivingEntity entity) {
		return Math.max(0.0F, Math.min(1.0F, entity.getHealth() / Math.max(1.0F, entity.getMaxHealth())));
	}

	private static Component barName(LivingEntity self, LivingEntity other) {
		return Component.translatable("kultivator.sectevent.duel.bar", self.getDisplayName(), other.getDisplayName());
	}

	private static void finish(Bout bout, @Nullable LivingEntity winner, @Nullable LivingEntity loser, DuelRules.Reason reason) {
		if (bout.ended) {
			return;
		}
		bout.ended = true;
		BOUTS.remove(bout.owner, bout);
		UNDERDOGS.remove(bout.a.getUUID());
		UNDERDOGS.remove(bout.b.getUUID());
		bout.barA.removeAllPlayers();
		bout.barB.removeAllPlayers();
		bout.barA.setVisible(false);
		bout.barB.setVisible(false);
		try {
			bout.listener.onEnd(winner, loser, reason);
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.error("A sect bout listener failed", exception);
		}
	}

	/** One spark per landed hit between the two fighters of a bout. */
	static void onDamage(LivingEntity victim, DamageSource source, float taken) {
		if (BOUTS.isEmpty() || !(taken > 0.0F)) {
			return;
		}
		Entity attacker = source.getEntity();
		if (attacker == null || !DuelRules.dueling(attacker, victim)) {
			return;
		}
		for (Bout bout : BOUTS.values()) {
			if (!bout.ended && (victim == bout.a || victim == bout.b)) {
				int rgb = attacker instanceof DiscipleEntity disciple ? disciple.data().rootColor() : 0xFFE08A;
				EventUtil.fx(bout.level, victim.position().add(0.0D, victim.getBbHeight() * 0.6D, 0.0D), SectEventFxIds.HIT_SPARK, rgb, 0.0F);
				return;
			}
		}
	}

	/** The underdog bonus of a bout fighter against its partner (1 outside an underdog bout). */
	static double underdogFactor(DiscipleEntity entity, LivingEntity target) {
		if (UNDERDOGS.isEmpty() || !UNDERDOGS.contains(entity.getUUID()) || !DuelRules.dueling(entity, target)) {
			return 1.0D;
		}
		int gap = realmOf(target) - entity.data().realm().ordinal();
		return gap <= 0 ? 1.0D : 1.0D + Math.min(UNDERDOG_MAX, UNDERDOG_PER_REALM * gap);
	}

	private static int realmOf(LivingEntity entity) {
		if (entity instanceof DiscipleEntity disciple) {
			return disciple.data().realm().ordinal();
		}
		if (entity instanceof ServerPlayer player) {
			return Cultivators.get(player).realm().ordinal();
		}
		return 0;
	}

	/** The server stopped: nothing survives it. */
	static void clear() {
		for (Bout bout : BOUTS.values()) {
			bout.barA.removeAllPlayers();
			bout.barB.removeAllPlayers();
		}
		BOUTS.clear();
		UNDERDOGS.clear();
	}

	private DuelArena() {
	}
}
