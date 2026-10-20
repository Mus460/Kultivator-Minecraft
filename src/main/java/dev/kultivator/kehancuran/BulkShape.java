package dev.kultivator.kehancuran;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The read-only geometry of a fast bulk carve ("pahat cepat", DESIGN 29.16.7): a union of analytic column profiles around
 * one impact. For a world column (x, z) with its surface top ({@code chunk.getHeight(WORLD_SURFACE, x, z)}, the highest
 * block) {@link #span} gives one span [lo, hi] and a mode (REMOVE before NATURAL before STRIP where profiles overlap), or
 * none. Nothing here changes a block; {@link WorldBreaker} walks the spans chunk by chunk from the centre out.
 * <p>
 * Every span is clamped to [{@code minY + 6}, {@code maxY}]. The walk always starts at the column's top, so a span that
 * ends below the top (an overhang over a bowl) only carves when nothing solid is kept above it: no sealed hollows.
 */
public final class BulkShape {

	/** Per-column modes (the same values as {@code WorldBreaker}'s per-position modes). */
	public static final byte REMOVE = WorldBreaker.MODE_REMOVE;
	public static final byte NATURAL = WorldBreaker.MODE_NATURAL;
	public static final byte STRIP = WorldBreaker.MODE_STRIP;
	/** Never below the world's floor plus this (bedrock and its margin are never touched). */
	public static final int FLOOR_MARGIN = 6;
	/** A STRIP column looks this far down from its top (as {@code Shapes.strip}). */
	public static final int STRIP_SCAN = 14;
	/** A STEPPED_PIT column higher than its base by more than this is left (no lids, no cliffs; as {@code Shapes}). */
	public static final int PIT_HILL = 6;

	/** One analytic column profile. */
	public sealed interface Profile permits Bowl, Fissure, Shave, Strip, SteppedPit, Trench {
	}

	/** A half-ellipsoid bowl: floor = impactY − depth·sqrt(1 − h²/r²); hi = min(top, impactY + r). REMOVE. */
	public record Bowl(double cx, double cz, int impactY, double r, double depth) implements Profile {
	}

	/** A lens-shaped fissure along {@code dir} from {@code start}: depth·sin(π·f) below each column's top. REMOVE. */
	public record Fissure(Vec3 start, Vec3 dir, double length, double width, double depth) implements Profile {
		public Fissure {
			dir = flat(dir);
		}
	}

	/** The flattening shock ring: NATURAL blocks from the top down at most {@code h}, never below impactY + 1. */
	public record Shave(double cx, double cz, int impactY, double r, int h) implements Profile {
	}

	/** Foliage only ({@code #kultivator:strippable}) from each column's top down while it lasts. STRIP. */
	public record Strip(double cx, double cz, double r) implements Profile {
	}

	/** Square terraces below {@code baseY}: a column at Chebyshev distance c goes down depth × (terraces covering it). REMOVE. */
	public record SteppedPit(double cx, double cz, int baseY, double half, int steps, int depth) implements Profile {
	}

	/** A straight trench between two points, {@code width} wide and {@code depth} deep below each column's top. REMOVE. */
	public record Trench(Vec3 from, Vec3 to, double width, int depth) implements Profile {
	}

	private final BlockPos impact;
	private final Vec3 dir;
	private final List<Profile> profiles;
	private final int outer;

	private BulkShape(BlockPos impact, Vec3 dir, List<Profile> profiles) {
		this.impact = impact.immutable();
		this.dir = flat(dir);
		this.profiles = List.copyOf(profiles);
		this.outer = computeOuter();
	}

	/** A shape of {@code profiles} around {@code impact} (the impact block; {@code dir} the horizontal cast direction). */
	public static BulkShape of(BlockPos impact, Vec3 dir, List<Profile> profiles) {
		return new BulkShape(impact, dir == null ? new Vec3(0.0D, 0.0D, 1.0D) : dir, profiles == null ? List.of() : profiles);
	}

	/**
	 * The impact block's y under {@code anchor}, read-only (as the classic shapes find it: the first solid block at or
	 * below the anchor within 8, else the column surface); {@code anchor.getY() - 1} when a fluid is met first.
	 */
	public static int impactY(ServerLevel level, BlockPos anchor) {
		BlockPos centre = Shapes.impact(level, anchor);
		return centre == null ? anchor.getY() - 1 : centre.getY();
	}

	public BlockPos impact() {
		return impact;
	}

	public Vec3 dir() {
		return dir;
	}

	public List<Profile> profiles() {
		return profiles;
	}

	public boolean isEmpty() {
		return profiles.isEmpty();
	}

	/** The largest horizontal reach of any profile from the impact column (blocks, rounded up). */
	public int outer() {
		return outer;
	}

	// ================================================================== spans

	/**
	 * The span of column (x, z) with surface {@code top}; false when none. {@code out[0]} = lo, {@code out[1]} = hi,
	 * {@code out[2]} = mode. Clamped to [{@code minY + 6}, {@code maxY}].
	 */
	public boolean span(int x, int z, int top, int minY, int maxY, int[] out) {
		if (top < minY + FLOOR_MARGIN) {
			return false;
		}
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		int mode = Integer.MAX_VALUE;
		int[] one = new int[3];
		for (Profile profile : profiles) {
			if (!profileSpan(profile, x, z, top, minY, one)) {
				continue;
			}
			lo = Math.min(lo, one[0]);
			hi = Math.max(hi, one[1]);
			// REMOVE (0) before NATURAL (1) before STRIP (2)
			mode = Math.min(mode, one[2]);
		}
		if (mode == Integer.MAX_VALUE) {
			return false;
		}
		lo = Math.max(lo, minY + FLOOR_MARGIN);
		hi = Math.min(hi, maxY);
		if (hi < lo) {
			return false;
		}
		out[0] = lo;
		out[1] = hi;
		out[2] = mode;
		return true;
	}

	/** The cells of column (x, z) the union would look at ({@code hi − lo + 1}, 0 when none): the volume estimate. */
	public int estimate(int x, int z, int top, int minY, int maxY) {
		int[] out = new int[3];
		return span(x, z, top, minY, maxY, out) ? out[1] - out[0] + 1 : 0;
	}

	private static boolean profileSpan(Profile profile, int x, int z, int top, int minY, int[] out) {
		double px = x + 0.5D;
		double pz = z + 0.5D;
		switch (profile) {
			case Bowl bowl -> {
				double dx = px - bowl.cx();
				double dz = pz - bowl.cz();
				double h = dx * dx + dz * dz;
				double r2 = (bowl.r() + 0.5D) * (bowl.r() + 0.5D);
				if (h > r2) {
					return false;
				}
				int floor = bowl.impactY() - (int) Math.floor((bowl.depth() + 0.5D) * Math.sqrt(Math.max(0.0D, 1.0D - h / r2)));
				int hi = Math.min(top, bowl.impactY() + (int) Math.ceil(bowl.r()));
				return set(out, floor, hi, REMOVE);
			}
			case Fissure fissure -> {
				Vec3 d = fissure.dir();
				double rx = px - fissure.start().x;
				double rz = pz - fissure.start().z;
				double t = rx * d.x + rz * d.z;
				double lateral = -rx * d.z + rz * d.x;
				double length = Math.max(2.0D, fissure.length());
				if (t < -0.5D || t >= length - 0.5D || Math.abs(lateral) > Math.max(0.5D, fissure.width() / 2.0D)) {
					return false;
				}
				double f = Math.max(0.0D, Math.min(1.0D, (t + 0.5D) / length));
				int depth = Math.max(1, (int) Math.round(fissure.depth() * Math.sin(Math.PI * f)));
				return set(out, top - depth + 1, top, REMOVE);
			}
			case Shave shave -> {
				double dx = px - shave.cx();
				double dz = pz - shave.cz();
				if (dx * dx + dz * dz > (shave.r() + 0.5D) * (shave.r() + 0.5D)) {
					return false;
				}
				int floor = shave.impactY() + 1;
				if (top < floor) {
					return false;
				}
				return set(out, Math.max(floor, top - Math.max(1, shave.h()) + 1), top, NATURAL);
			}
			case Strip strip -> {
				double dx = px - strip.cx();
				double dz = pz - strip.cz();
				if (dx * dx + dz * dz > (strip.r() + 0.5D) * (strip.r() + 0.5D)) {
					return false;
				}
				return set(out, Math.max(minY, top - STRIP_SCAN), top, STRIP);
			}
			case SteppedPit pit -> {
				if (pit.half() <= 0.0D) {
					return false;
				}
				double c = Math.max(Math.abs(Math.floor(px) - Math.floor(pit.cx())), Math.abs(Math.floor(pz) - Math.floor(pit.cz())));
				int n = Math.max(1, pit.steps());
				int covering = 0;
				for (int i = 0; i < n; i++) {
					if (c <= pit.half() * (1.0D - (double) i / n) + 1.0E-6D) {
						covering++;
					}
				}
				if (covering <= 0 || top > pit.baseY() + PIT_HILL) {
					return false;
				}
				return set(out, pit.baseY() - covering * Math.max(1, pit.depth()) + 1, top, REMOVE);
			}
			case Trench trench -> {
				double ax = trench.from().x;
				double az = trench.from().z;
				double bx = trench.to().x - ax;
				double bz = trench.to().z - az;
				double len = bx * bx + bz * bz;
				double t = len < 1.0E-9D ? 0.0D : ((px - ax) * bx + (pz - az) * bz) / len;
				t = Math.max(0.0D, Math.min(1.0D, t));
				double dx = px - (ax + bx * t);
				double dz = pz - (az + bz * t);
				double reach = Math.max(0.5D, trench.width() / 2.0D) + 0.5D;
				if (dx * dx + dz * dz > reach * reach) {
					return false;
				}
				return set(out, top - Math.max(1, trench.depth()) + 1, top, REMOVE);
			}
		}
	}

	private static boolean set(int[] out, int lo, int hi, byte mode) {
		if (hi < lo) {
			return false;
		}
		out[0] = lo;
		out[1] = hi;
		out[2] = mode;
		return true;
	}

	// ================================================================== reach, scaling, chunks

	private int computeOuter() {
		double ix = impact.getX() + 0.5D;
		double iz = impact.getZ() + 0.5D;
		double max = 0.0D;
		for (Profile profile : profiles) {
			double reach = switch (profile) {
				case Bowl bowl -> dist(ix, iz, bowl.cx(), bowl.cz()) + bowl.r() + 1.0D;
				case Fissure fissure -> dist(ix, iz, fissure.start().x, fissure.start().z) + fissure.length() + fissure.width();
				case Shave shave -> dist(ix, iz, shave.cx(), shave.cz()) + shave.r() + 1.0D;
				case Strip strip -> dist(ix, iz, strip.cx(), strip.cz()) + strip.r() + 1.0D;
				case SteppedPit pit -> dist(ix, iz, pit.cx(), pit.cz()) + pit.half() * Math.sqrt(2.0D) + 1.0D;
				case Trench trench -> Math.max(dist(ix, iz, trench.from().x, trench.from().z), dist(ix, iz, trench.to().x, trench.to().z))
						+ trench.width() + 1.0D;
			};
			max = Math.max(max, reach);
		}
		return (int) Math.ceil(max);
	}

	/**
	 * The same shape shrunk about the impact by {@code f} (0-1): radii, lengths and the profiles' distances from the impact
	 * scale by f, so a radial profile keeps its rim and a fissure or trench keeps its taper; depths and widths are kept.
	 */
	public BulkShape scaled(double f) {
		double k = Math.max(0.01D, Math.min(1.0D, f));
		if (k >= 0.9999D) {
			return this;
		}
		double ix = impact.getX() + 0.5D;
		double iz = impact.getZ() + 0.5D;
		List<Profile> out = new ArrayList<>(profiles.size());
		for (Profile profile : profiles) {
			out.add(switch (profile) {
				case Bowl b -> new Bowl(ix + (b.cx() - ix) * k, iz + (b.cz() - iz) * k, b.impactY(), Math.max(1.0D, b.r() * k), b.depth());
				case Fissure f2 -> new Fissure(new Vec3(ix + (f2.start().x - ix) * k, f2.start().y, iz + (f2.start().z - iz) * k), f2.dir(),
						Math.max(2.0D, f2.length() * k), f2.width(), f2.depth());
				case Shave s -> new Shave(ix + (s.cx() - ix) * k, iz + (s.cz() - iz) * k, s.impactY(), Math.max(1.0D, s.r() * k), s.h());
				case Strip s -> new Strip(ix + (s.cx() - ix) * k, iz + (s.cz() - iz) * k, Math.max(1.0D, s.r() * k));
				case SteppedPit p -> new SteppedPit(ix + (p.cx() - ix) * k, iz + (p.cz() - iz) * k, p.baseY(), Math.max(1.0D, p.half() * k),
						p.steps(), p.depth());
				case Trench t -> new Trench(new Vec3(ix + (t.from().x - ix) * k, t.from().y, iz + (t.from().z - iz) * k),
						new Vec3(ix + (t.to().x - ix) * k, t.to().y, iz + (t.to().z - iz) * k), t.width(), t.depth());
			});
		}
		return new BulkShape(impact, dir, out);
	}

	/** Roughly how many chunks {@link #chunksCentreOut} would list (the disc of {@link #outer} plus one chunk ring). */
	public long chunkEstimate() {
		double r = outer / 16.0D + 1.0D;
		return (long) Math.ceil(Math.PI * r * r);
	}

	/**
	 * Every chunk the union may touch (its nearest point within {@link #outer} of the impact column), ring by ring from the
	 * impact chunk, nearest first within a ring. Read-only, loads nothing.
	 */
	public ChunkPos[] chunksCentreOut() {
		int icx = impact.getX() >> 4;
		int icz = impact.getZ() >> 4;
		double ix = impact.getX() + 0.5D;
		double iz = impact.getZ() + 0.5D;
		int rings = outer / 16 + 1;
		double limit = (double) outer * outer;
		List<ChunkPos> list = new ArrayList<>();
		List<ChunkPos> ring = new ArrayList<>();
		for (int k = 0; k <= rings; k++) {
			ring.clear();
			for (int dx = -k; dx <= k; dx++) {
				for (int dz = -k; dz <= k; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != k) {
						continue;
					}
					int cx = icx + dx;
					int cz = icz + dz;
					if (nearestSq(ix, iz, cx, cz) <= limit) {
						ring.add(new ChunkPos(cx, cz));
					}
				}
			}
			ring.sort(Comparator.comparingDouble(pos -> nearestSq(ix, iz, pos.x(), pos.z())));
			list.addAll(ring);
		}
		return list.toArray(new ChunkPos[0]);
	}

	/** The squared horizontal distance from (ix, iz) to the nearest point of chunk (cx, cz). */
	public static double nearestSq(double ix, double iz, int cx, int cz) {
		double minX = cx * 16.0D;
		double minZ = cz * 16.0D;
		double dx = ix < minX ? minX - ix : ix > minX + 16.0D ? ix - minX - 16.0D : 0.0D;
		double dz = iz < minZ ? minZ - iz : iz > minZ + 16.0D ? iz - minZ - 16.0D : 0.0D;
		return dx * dx + dz * dz;
	}

	private static double dist(double ax, double az, double bx, double bz) {
		double dx = ax - bx;
		double dz = az - bz;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static Vec3 flat(Vec3 dir) {
		Vec3 flat = dir == null ? Vec3.ZERO : new Vec3(dir.x, 0.0D, dir.z);
		return flat.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
	}
}
