package dev.kultivator.client.epic;

import dev.kultivator.client.EffectQuality;

/**
 * The slow-motion feel of 7b (DESIGN 23.11.2), presentation only: {@code σ} is eased by requests and hit-stops and only
 * 7b reads it (bolt flicker, vortex spin, pillar descent, ring growth, figure fades, shot progress, 7b's own particles).
 * Vanilla particles, entities, {@code DeltaTracker}, the tick-rate managers and damage are never touched. "Kurangi gerak"
 * or "Gerak lambat" off keeps σ at 1 with no hit-stop. Client thread only.
 */
public final class EpicClock {

	/** σ of a Gerak lambat window. */
	public static final float SLOW = 0.35F;
	/** Longest window in real ticks (1.5 s) and the shortest gap between two windows (6 s). */
	public static final int MAX_WINDOW = 30;
	public static final int MIN_GAP = 120;
	/** A hit-stop holds σ at 0 for this many ticks, then ramps back over {@link #HIT_RAMP}. */
	public static final int HIT_TICKS = 3;
	public static final int HIT_RAMP = 20;

	private static float sigma = 1.0F;
	private static float target = 1.0F;
	private static int phase;
	private static int inTicks;
	private static int holdTicks;
	private static int outTicks;
	private static int age;
	private static int priority = -1;
	private static int hitLeft;
	private static int hitRamp;
	private static long lastWindowEnd = -10_000L;
	private static long ticks;
	/** Presentation time in ticks (advances by σ per tick). */
	private static double time;

	/** Asks for a slow-motion window; dropped when a higher one runs, too soon after the last, or switched off. */
	public static boolean request(float slow, int in, int hold, int out, int prio) {
		if (!enabled()) {
			return false;
		}
		if (active() && prio < priority) {
			return false;
		}
		if (!active() && ticks - lastWindowEnd < MIN_GAP) {
			return false;
		}
		int total = Math.max(1, in) + Math.max(0, hold) + Math.max(1, out);
		if (total > MAX_WINDOW) {
			hold = Math.max(0, MAX_WINDOW - Math.max(1, in) - Math.max(1, out));
		}
		target = Math.max(0.1F, Math.min(1.0F, slow));
		inTicks = Math.max(1, in);
		holdTicks = Math.max(0, hold);
		outTicks = Math.max(1, out);
		age = 0;
		phase = 1;
		priority = prio;
		return true;
	}

	/** A Gerak lambat window of {@code realTicks} at σ 0.35 (≤ 1.5 s). */
	public static boolean slowmo(int realTicks, int prio) {
		int hold = Math.max(0, Math.min(MAX_WINDOW, realTicks) - 8);
		return request(SLOW, 4, hold, 4, prio);
	}

	/** σ = 0 for {@link #HIT_TICKS} ticks, then a ramp back (final strike, Dewa impacts, the demon's shatter). */
	public static void hitStop() {
		if (!enabled()) {
			return;
		}
		hitLeft = HIT_TICKS;
		hitRamp = 0;
	}

	/** Ends every window at once (skip, death, dimension change, disconnect, {@code uji momen berhenti}). */
	public static void reset() {
		sigma = 1.0F;
		phase = 0;
		priority = -1;
		hitLeft = 0;
		hitRamp = 0;
	}

	/** Once per client tick. */
	static void tick() {
		ticks++;
		if (!enabled()) {
			if (sigma != 1.0F || phase != 0) {
				reset();
			}
			time += 1.0D;
			return;
		}
		float window = 1.0F;
		if (phase != 0) {
			age++;
			if (age <= inTicks) {
				window = 1.0F + (target - 1.0F) * age / inTicks;
			} else if (age <= inTicks + holdTicks) {
				window = target;
			} else if (age <= inTicks + holdTicks + outTicks) {
				window = target + (1.0F - target) * (age - inTicks - holdTicks) / outTicks;
			} else {
				phase = 0;
				priority = -1;
				lastWindowEnd = ticks;
			}
		}
		float hit = 1.0F;
		if (hitLeft > 0) {
			hitLeft--;
			hit = 0.0F;
			if (hitLeft == 0) {
				hitRamp = HIT_RAMP;
			}
		} else if (hitRamp > 0) {
			hitRamp--;
			hit = 1.0F - hitRamp / (float) HIT_RAMP;
		}
		sigma = Math.min(window, hit);
		time += sigma;
	}

	/** σ now (1 = normal speed). */
	public static float sigma() {
		return sigma;
	}

	/** Presentation time with the frame's partial tick scaled by σ. */
	public static double time(float partial) {
		return time + partial * sigma;
	}

	/** True while a window or a hit-stop holds σ below 1. */
	public static boolean slowed() {
		return sigma < 0.999F;
	}

	/** True while a hit-stop freezes 7b (σ = 0). */
	public static boolean frozen() {
		return hitLeft > 0;
	}

	private static boolean active() {
		return phase != 0;
	}

	private static boolean enabled() {
		return EpicSettings.slowmo() && !EffectQuality.reduceMotion();
	}

	private EpicClock() {
	}
}
