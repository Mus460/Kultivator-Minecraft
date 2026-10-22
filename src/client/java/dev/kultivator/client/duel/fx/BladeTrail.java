package dev.kultivator.client.duel.fx;

import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/**
 * The trail of one blade through one swing (DESIGN 35.4.4): a history of (grip, tip) samples of the blade as it was
 * drawn ({@link DuelLinks#drawn}), taken from shortly before the impact to the start of the recovery, and drawn as a
 * sheet from 10 % up the blade to the tip: transparent at the grip side, dense near the tip, fading with the age of each
 * sample (≤ 6 presentation ticks) and with the blade's speed between samples (a slow blade leaves nothing), plus a thin
 * additive edge line along the tip path that is brightest at the head.
 * <p>
 * Between two samples the blade is swept by turning its direction about the grip (nlerp, one step per 12°), so even at
 * 20 frames per second a 2-tick strike draws a smooth arc instead of a fan of planks. Colours: steel {@code 0xE8F0FF}
 * at α 0.35, qi colour at α 0.8 with a white core, perilous red {@code 0xFF3030}, gold for a riposte and a deathblow.
 */
final class BladeTrail {

	static final int MAX = 24;
	/** Samples older than this (presentation ticks) are gone. */
	static final float MAX_AGE = 6.0F;
	/** A new sample at most every this many presentation ticks (closer ones replace the newest). */
	static final double STEP = 0.2D;

	final int entityId;
	private final double[] gx = new double[MAX];
	private final double[] gy = new double[MAX];
	private final double[] gz = new double[MAX];
	private final double[] tx = new double[MAX];
	private final double[] ty = new double[MAX];
	private final double[] tz = new double[MAX];
	private final double[] at = new double[MAX];
	private int count;

	int rgb = SparkBurst.STEEL;
	float alpha = 0.35F;
	int core = SparkBurst.STEEL;
	boolean qi;
	/** Server ticks in which the trail records. */
	double activeFrom;
	double activeUntil;
	/** A cancelled swing (feint, rejected): the trail fades twice as fast. */
	boolean fadeFast;
	/** Presentation time of the last sample. */
	double lastSample = -1.0E9D;

	BladeTrail(int entityId) {
		this.entityId = entityId;
	}

	/** Takes the colours of a swing (35.4.4). */
	void style(DuelFx.SwingView swing) {
		qi = swing.has(DuelCodes.SWING_QI);
		fadeFast = false;
		if (swing.kind == DuelCodes.KIND_PENENTU || swing.kind == DuelCodes.KIND_BALIK || swing.has(DuelCodes.SWING_RIPOSTE)) {
			rgb = SparkBurst.GOLD;
			core = 0xFFF4D0;
			alpha = 0.65F;
		} else if (swing.has(DuelCodes.SWING_PERILOUS)) {
			rgb = 0xFF3030;
			core = 0xFFB0A0;
			alpha = 0.6F;
		} else if (qi) {
			rgb = swing.qiRgb != 0 ? swing.qiRgb : 0x9FD8FF;
			core = 0xFFFFFF;
			alpha = 0.8F;
		} else {
			rgb = SparkBurst.STEEL;
			core = 0xFFFFFF;
			alpha = 0.35F;
		}
		if (!swing.charging()) {
			double impact = swing.impact();
			activeFrom = impact - 3.0D;
			activeUntil = impact + 5.0D;
		} else {
			activeFrom = Double.MAX_VALUE;
			activeUntil = -1.0D;
		}
	}

	boolean recording(double serverTick) {
		return serverTick >= activeFrom && serverTick <= activeUntil;
	}

	void stop(double serverTick) {
		activeUntil = Math.min(activeUntil, serverTick);
		fadeFast = true;
	}

	void push(DuelLinks.Blade blade, double time) {
		Vec3 g = blade.grip();
		Vec3 t = blade.tip();
		if (count > 0 && time - at[count - 1] < STEP) {
			// replace the newest sample: the head stays fresh at any frame rate
			set(count - 1, g, t, time);
			return;
		}
		if (count == MAX) {
			System.arraycopy(gx, 1, gx, 0, MAX - 1);
			System.arraycopy(gy, 1, gy, 0, MAX - 1);
			System.arraycopy(gz, 1, gz, 0, MAX - 1);
			System.arraycopy(tx, 1, tx, 0, MAX - 1);
			System.arraycopy(ty, 1, ty, 0, MAX - 1);
			System.arraycopy(tz, 1, tz, 0, MAX - 1);
			System.arraycopy(at, 1, at, 0, MAX - 1);
			count--;
		}
		set(count, g, t, time);
		count++;
		lastSample = time;
	}

	private void set(int i, Vec3 g, Vec3 t, double time) {
		gx[i] = g.x;
		gy[i] = g.y;
		gz[i] = g.z;
		tx[i] = t.x;
		ty[i] = t.y;
		tz[i] = t.z;
		at[i] = time;
	}

	/** True when nothing of the trail can show any more. */
	boolean dead(double now, double serverTick) {
		return serverTick > activeUntil && (count == 0 || now - at[count - 1] > MAX_AGE);
	}

	/** The tip velocity of the last two samples in blocks per presentation tick, or null. */
	Vec3 tipVelocity(double now) {
		if (count < 2 || now - at[count - 1] > 3.0D) {
			return null;
		}
		double dt = at[count - 1] - at[count - 2];
		if (dt < 0.01D) {
			return null;
		}
		return new Vec3((tx[count - 1] - tx[count - 2]) / dt, (ty[count - 1] - ty[count - 2]) / dt, (tz[count - 1] - tz[count - 2]) / dt);
	}

	/** Draws the sheet and the edge line; returns the quads used. */
	void render(DuelGeo frame) {
		if (count < 2) {
			return;
		}
		double now = frame.time;
		float maxAge = fadeFast ? MAX_AGE * 0.5F : MAX_AGE;
		for (int i = count - 1; i > 0; i--) {
			int newer = i;
			int older = i - 1;
			float ageNew = (float) (now - at[newer]);
			float ageOld = (float) (now - at[older]);
			if (ageOld > maxAge && ageNew > maxAge) {
				break;
			}
			double dt = Math.max(0.05D, at[newer] - at[older]);
			double moved = Math.sqrt(sq(tx[newer] - tx[older]) + sq(ty[newer] - ty[older]) + sq(tz[newer] - tz[older]));
			float speed = (float) Math.max(0.0D, Math.min(1.0D, (moved / dt - 0.12D) / 0.5D));
			if (speed <= 0.02F) {
				continue;
			}
			float fadeNew = Math.max(0.0F, 1.0F - ageNew / maxAge);
			float fadeOld = Math.max(0.0F, 1.0F - ageOld / maxAge);
			Vec3 gN = new Vec3(gx[newer], gy[newer], gz[newer]);
			Vec3 gO = new Vec3(gx[older], gy[older], gz[older]);
			Vec3 dN = new Vec3(tx[newer] - gx[newer], ty[newer] - gy[newer], tz[newer] - gz[newer]);
			Vec3 dO = new Vec3(tx[older] - gx[older], ty[older] - gy[older], tz[older] - gz[older]);
			double lN = dN.length();
			double lO = dO.length();
			if (lN < 1.0E-4D || lO < 1.0E-4D) {
				continue;
			}
			Vec3 uN = dN.scale(1.0D / lN);
			Vec3 uO = dO.scale(1.0D / lO);
			double angle = Math.acos(Math.max(-1.0D, Math.min(1.0D, uN.dot(uO))));
			int steps = (int) Math.max(1, Math.min(6, Math.ceil(angle / Math.toRadians(12.0D))));
			if (!frame.take(steps * 3)) {
				return;
			}
			for (int j = 0; j < steps; j++) {
				double s0 = j / (double) steps;
				double s1 = (j + 1) / (double) steps;
				// s = 0 at the older sample, 1 at the newer
				float a0 = alpha * speed * lerp(fadeOld, fadeNew, (float) s0);
				float a1 = alpha * speed * lerp(fadeOld, fadeNew, (float) s1);
				float u0 = Math.min(1.0F, lerp(ageOld, ageNew, (float) s0) / maxAge);
				float u1 = Math.min(1.0F, lerp(ageOld, ageNew, (float) s1) / maxAge);
				Vec3 g0 = gO.lerp(gN, s0);
				Vec3 g1 = gO.lerp(gN, s1);
				Vec3 d0 = dir(uO, uN, s0).scale(lO + (lN - lO) * s0);
				Vec3 d1 = dir(uO, uN, s1).scale(lO + (lN - lO) * s1);
				Vec3 low0 = g0.add(d0.scale(0.1D));
				Vec3 mid0 = g0.add(d0.scale(0.55D));
				Vec3 tip0 = g0.add(d0);
				Vec3 low1 = g1.add(d1.scale(0.1D));
				Vec3 mid1 = g1.add(d1.scale(0.55D));
				Vec3 tip1 = g1.add(d1);
				int light = LightCoordsUtil.FULL_BRIGHT;
				// the grip half: transparent at 10 % of the blade, half dense at the middle
				frame.quad(frame.trail, low0, mid0, mid1, low1, u0, 0.0F, u1, 0.5F, DuelGeo.argb(0.0F, rgb), DuelGeo.argb(a0 * 0.5F, rgb),
						DuelGeo.argb(a1 * 0.5F, rgb), DuelGeo.argb(0.0F, rgb), light);
				// the tip half: dense, the colour lifting toward the core at the very edge
				int edge = qi ? DuelGeo.mix(rgb, core, 0.45F) : rgb;
				frame.quad(frame.trail, mid0, tip0, tip1, mid1, u0, 0.5F, u1, 1.0F, DuelGeo.argb(a0 * 0.5F, rgb), DuelGeo.argb(a0, edge),
						DuelGeo.argb(a1, edge), DuelGeo.argb(a1 * 0.5F, rgb), light);
				// the hot edge: a thin additive line on the tip path, brightest at the head
				float w0 = 0.02F + 0.03F * (1.0F - u0);
				float w1 = 0.02F + 0.03F * (1.0F - u1);
				frame.segment(frame.add, tip0, tip1, w0, w1, DuelGeo.argb(Math.min(1.0F, a0 * 1.1F), core), DuelGeo.argb(Math.min(1.0F, a1 * 1.1F), core),
						0.0F, 1.0F);
			}
		}
	}

	/** nlerp of two unit directions at {@code s}. */
	private static Vec3 dir(Vec3 a, Vec3 b, double s) {
		Vec3 v = a.scale(1.0D - s).add(b.scale(s));
		double length = v.length();
		return length < 1.0E-6D ? b : v.scale(1.0D / length);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static double sq(double v) {
		return v * v;
	}
}
