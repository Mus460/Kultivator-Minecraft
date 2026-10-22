package dev.kultivator.client.duel.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicClock;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.film.BulletTime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Hit-stop, slow motion and the presentation clock of the duel visuals (DESIGN 35.4.6). Everything here is presentation:
 * it asks {@link EpicClock} for σ windows (which only 7b and the duel visuals read: poses, sparks, trails, rings, decals,
 * the duel camera); vanilla entities and particles, {@code DeltaTracker}, the server and its tick rate are never touched.
 * A window needs a live 7b scene for the clock to tick, so every request first opens or extends the duel scene of the
 * pair ({@link EpicDirector#duelScene}). Only the duellists and onlookers within 24 b get hit-stops and slow motion;
 * "Kurangi gerak", "Gerak lambat" off, or the duel's own "Hit-stop" / "Gerak lambat duel" off drop them.
 */
public final class DuelTime {

	/** The slow-motion priority of the duel: below tribulations and ultimates (35.4.6). */
	public static final int SLOW_PRIO = 40;
	/** Hit-stop and slow motion reach onlookers within this distance. */
	public static final double REACH = 24.0D;

	/** Presentation ticks: advances by σ every client tick (never while paused). */
	private static double time;
	private static float sigma = 1.0F;

	/** Once per client tick, after the 7b director ticked its clock. */
	static void tick(Minecraft minecraft) {
		if (minecraft.isPaused()) {
			return;
		}
		sigma = EpicClock.sigma();
		time += sigma;
	}

	/** Presentation time now (with the frame's partial tick × σ). */
	public static double now(float partial) {
		return time + partial * sigma;
	}

	/** Presentation time at the last tick. */
	public static double now() {
		return time;
	}

	/** σ of this tick (0 during a hit-stop). */
	public static float sigma() {
		return sigma;
	}

	/** True while a hit-stop or bullet time holds the duel visuals. */
	public static boolean frozen() {
		return EpicClock.frozen() || sigma <= 0.001F;
	}

	/** A hit-stop of {@code ticks} (1-6) for the pair {@code aId}/{@code bId} at {@code at}. */
	public static void hitStop(int ticks, int aId, int bId, Vec3 at) {
		if (!allowed(aId, bId, at) || !DuelFxSettings.hitStop()) {
			return;
		}
		if (EpicDirector.duelScene(aId, bId, at, aId * 31 + bId, 60) != null) {
			EpicClock.hitStop(Math.max(1, Math.min(6, ticks)));
		}
	}

	/** A Gerak lambat window of {@code realTicks} at σ 0.35 (≤ 30, ≥ 6 s after the last window). */
	public static boolean slowmo(int realTicks, int aId, int bId, Vec3 at) {
		if (!allowed(aId, bId, at) || !DuelFxSettings.slowmo()) {
			return false;
		}
		if (EpicDirector.duelScene(aId, bId, at, aId * 31 + bId, realTicks + 40) == null) {
			return false;
		}
		return EpicClock.slowmo(realTicks, SLOW_PRIO);
	}

	/** Bullet time of the finisher (σ ≈ 0.06 for ≤ 24 real ticks; "Waktu beku" and the duel's slow motion must be on). */
	public static boolean bullet(float slow, int realTicks, int aId, int bId, Vec3 at) {
		if (!allowed(aId, bId, at) || !DuelFxSettings.slowmo()) {
			return false;
		}
		if (EpicDirector.duelScene(aId, bId, at, aId * 31 + bId, realTicks + 40) == null) {
			return false;
		}
		return BulletTime.start(slow, realTicks);
	}

	private static boolean allowed(int aId, int bId, Vec3 at) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || EffectQuality.reduceMotion() || !EpicSettings.slowmo()) {
			return false;
		}
		int self = minecraft.player.getId();
		return self == aId || self == bId || minecraft.player.position().distanceToSqr(at) <= REACH * REACH;
	}

	/** World change, disconnect. */
	static void clear() {
		sigma = 1.0F;
	}

	private DuelTime() {
	}
}
