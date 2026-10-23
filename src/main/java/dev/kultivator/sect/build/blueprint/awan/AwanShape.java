package dev.kultivator.sect.build.blueprint.awan;

import java.util.Random;

/**
 * The deterministic shape noise of the Awan islands (DESIGN 37.4.2 steps 1 and 4): a periodic 1D value noise of the rim
 * (16 and 8 control points) and a 2D value noise of the belly. Pure functions of (plan seed, island id), so the island
 * emission ({@link PulauAwan}) and the island terrain the other parts stand on ({@link AwanTerrain}) agree cell by cell,
 * on any thread.
 */
public final class AwanShape {

	private final double[] rim1 = new double[16];
	private final double[] rim2 = new double[8];
	private final long seed2d;
	private final double tipX;
	private final double tipZ;
	private final double lo;
	private final double hi;
	/** Strata offset of this island's cliffs. */
	public final int band;

	/**
	 * @param seed the plan seed
	 * @param id   the island id ("main", "sat_kebun"...)
	 * @param lo   lowest rim factor
	 * @param hi   highest rim factor
	 */
	public AwanShape(long seed, String id, double lo, double hi) {
		Random random = new Random(seed * 0x9E3779B97F4A7C15L ^ id.hashCode() * 0x632BE59BD9B4E019L);
		for (int i = 0; i < rim1.length; i++) {
			rim1[i] = random.nextDouble() * 2.0D - 1.0D;
		}
		for (int i = 0; i < rim2.length; i++) {
			rim2[i] = random.nextDouble() * 2.0D - 1.0D;
		}
		this.seed2d = random.nextLong();
		double angle = random.nextDouble() * Math.PI * 2.0D;
		double off = 0.08D + random.nextDouble() * 0.07D;
		this.tipX = Math.sin(angle) * off;
		this.tipZ = Math.cos(angle) * off;
		this.band = random.nextInt(5);
		this.lo = lo;
		this.hi = hi;
	}

	/** The rim factor at angle {@code theta} (radians, 0 = +z, clockwise seen from above toward +x). */
	public double rim(double theta) {
		double f = 1.0D + 0.10D * periodic(rim1, theta) + 0.05D * periodic(rim2, theta * 3.0D);
		return Math.max(lo, Math.min(hi, f));
	}

	/** The normalised radius ρ' = ρ / rim(θ) of offset (dx, dz) from the island centre. */
	public double norm(double dx, double dz) {
		double rho = Math.sqrt(dx * dx + dz * dz);
		if (rho < 1.0E-6D) {
			return 0.0D;
		}
		return rho / rim(Math.atan2(dx, dz));
	}

	/** The belly tip offset (fraction of the radius) along x and z. */
	public double tipX() {
		return tipX;
	}

	public double tipZ() {
		return tipZ;
	}

	/** 2D value noise in [0, 1] with cells of {@code scale} blocks. */
	public double noise2(int x, int z, int scale) {
		double fx = (double) x / scale;
		double fz = (double) z / scale;
		int ix = (int) Math.floor(fx);
		int iz = (int) Math.floor(fz);
		double tx = smooth(fx - ix);
		double tz = smooth(fz - iz);
		double a = hash(ix, iz);
		double b = hash(ix + 1, iz);
		double c = hash(ix, iz + 1);
		double d = hash(ix + 1, iz + 1);
		return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
	}

	/** A deterministic 0..1 value of one cell (decoration rolls). */
	public double roll(int x, int y, int z, int salt) {
		long h = seed2d ^ (x * 0x2545F4914F6CDD1DL) ^ (y * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}

	private double hash(int x, int z) {
		long h = seed2d ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 11) * 0x1.0p-53;
	}

	private static double periodic(double[] points, double theta) {
		double t = theta / (Math.PI * 2.0D) * points.length;
		t = t - Math.floor(t / points.length) * points.length;
		int i = (int) Math.floor(t);
		double f = smooth(t - i);
		double a = points[i % points.length];
		double b = points[(i + 1) % points.length];
		return lerp(a, b, f);
	}

	private static double smooth(double t) {
		return t * t * (3.0D - 2.0D * t);
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}
}
