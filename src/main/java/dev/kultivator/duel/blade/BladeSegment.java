package dev.kultivator.duel.blade;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One blade in world space (DESIGN 35.5.3): {@code grip} is the base of the blade (the guard; a mace's haft below the
 * head, a fist's wrist) and {@code tip} its point. Shared by the server's arc tests and the client's contact solver.
 * Pure geometry, no state.
 */
public record BladeSegment(Vec3 grip, Vec3 tip) {

	/** From grip to tip (not normalised). */
	public Vec3 axis() {
		return tip.subtract(grip);
	}

	public double length() {
		return tip.distanceTo(grip);
	}

	/** The point at {@code t} along the blade (0 = grip, 1 = tip). */
	public Vec3 at(double t) {
		return new Vec3(grip.x + (tip.x - grip.x) * t, grip.y + (tip.y - grip.y) * t, grip.z + (tip.z - grip.z) * t);
	}

	/** The blade moved by {@code offset} (camera-relative or world). */
	public BladeSegment add(Vec3 offset) {
		return new BladeSegment(grip.add(offset), tip.add(offset));
	}

	/** Linear blend of two samples of the same blade (frame interpolation). */
	public static BladeSegment lerp(BladeSegment a, BladeSegment b, double t) {
		return new BladeSegment(a.grip.lerp(b.grip, t), a.tip.lerp(b.tip, t));
	}

	/**
	 * The closest pair of points of two segments (the classic clamped segment-segment solution): returns
	 * {@code {s, t, distance}} with {@code s} along {@code a} and {@code t} along {@code b}, both in [0, 1].
	 */
	public static double[] closest(BladeSegment a, BladeSegment b) {
		Vec3 d1 = a.axis();
		Vec3 d2 = b.axis();
		Vec3 r = a.grip.subtract(b.grip);
		double aa = d1.dot(d1);
		double ee = d2.dot(d2);
		double ff = d2.dot(r);
		double s;
		double t;
		final double eps = 1.0E-9D;
		if (aa <= eps && ee <= eps) {
			s = 0.0D;
			t = 0.0D;
		} else if (aa <= eps) {
			s = 0.0D;
			t = clamp01(ff / ee);
		} else {
			double c = d1.dot(r);
			if (ee <= eps) {
				t = 0.0D;
				s = clamp01(-c / aa);
			} else {
				double bb = d1.dot(d2);
				double denom = aa * ee - bb * bb;
				s = denom > eps ? clamp01((bb * ff - c * ee) / denom) : 0.0D;
				t = (bb * s + ff) / ee;
				if (t < 0.0D) {
					t = 0.0D;
					s = clamp01(-c / aa);
				} else if (t > 1.0D) {
					t = 1.0D;
					s = clamp01((bb - c) / aa);
				}
			}
		}
		Vec3 p = a.at(s);
		Vec3 q = b.at(t);
		return new double[] {s, t, p.distanceTo(q)};
	}

	/** The midpoint of the closest pair of points of two blades (where they meet, or nearly do). */
	public static Vec3 contact(BladeSegment a, BladeSegment b) {
		double[] st = closest(a, b);
		Vec3 p = a.at(st[0]);
		Vec3 q = b.at(st[1]);
		return p.add(q).scale(0.5D);
	}

	/** The point of this blade closest to {@code point}. */
	public Vec3 nearest(Vec3 point) {
		Vec3 d = axis();
		double len2 = d.dot(d);
		if (len2 < 1.0E-9D) {
			return grip;
		}
		return at(clamp01(point.subtract(grip).dot(d) / len2));
	}

	/**
	 * Slab test: true when the blade crosses {@code box} (grown by {@code inflate}). Returns the entry parameter along
	 * the blade through {@link #hitParam}; cheap enough for the server's per-tick arc samples.
	 */
	public boolean hits(AABB box, double inflate) {
		return hitParam(box, inflate) >= 0.0D;
	}

	/** The first parameter in [0, 1] where the blade enters {@code box} (grown by {@code inflate}), or -1. */
	public double hitParam(AABB box, double inflate) {
		double minX = box.minX - inflate, minY = box.minY - inflate, minZ = box.minZ - inflate;
		double maxX = box.maxX + inflate, maxY = box.maxY + inflate, maxZ = box.maxZ + inflate;
		double t0 = 0.0D;
		double t1 = 1.0D;
		double[] o = {grip.x, grip.y, grip.z};
		double[] d = {tip.x - grip.x, tip.y - grip.y, tip.z - grip.z};
		double[] lo = {minX, minY, minZ};
		double[] hi = {maxX, maxY, maxZ};
		for (int i = 0; i < 3; i++) {
			if (Math.abs(d[i]) < 1.0E-9D) {
				if (o[i] < lo[i] || o[i] > hi[i]) {
					return -1.0D;
				}
				continue;
			}
			double inv = 1.0D / d[i];
			double near = (lo[i] - o[i]) * inv;
			double far = (hi[i] - o[i]) * inv;
			if (near > far) {
				double swap = near;
				near = far;
				far = swap;
			}
			t0 = Math.max(t0, near);
			t1 = Math.min(t1, far);
			if (t0 > t1) {
				return -1.0D;
			}
		}
		return t0;
	}

	private static double clamp01(double v) {
		return v < 0.0D ? 0.0D : v > 1.0D ? 1.0D : v;
	}
}
