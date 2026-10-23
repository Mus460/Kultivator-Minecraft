package dev.kultivator.sekte.langit.gerak;

import dev.kultivator.sekte.langit.api.SkyHallView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The flight path of the Cloud Ferry (DESIGN 37.5.5), computed once per call from the hall's boxes and two stops; pure
 * geometry, no world access. Upward (the reverse is the same points backwards):
 * <ol>
 * <li>from the ground stop {@code feri_bawah} straight up to {@code skyFloorY - 6} (under the island's lowest block);</li>
 * <li>horizontally out from under the island until 4 blocks past its footprint box, along the first of a few bearings
 * (radial from the throne first) that keeps 6 blocks off the Tangga Awan column;</li>
 * <li>up the outside of the box to 3 blocks over the sky stop {@code feri_atas}, then round the box's corners (4 blocks
 * off it) to the point straight out from the stop;</li>
 * <li>in over the rim and down onto the stop.</li>
 * </ol>
 * The box is the whole footprint column, so the ferry never passes through the solid island. Every point is clamped
 * under {@code maxY - 4}.
 */
public final class FeriRute {

	/** Gap kept under the island's lowest block. */
	public static final int UNDER = 6;
	/** Gap kept outside the footprint box. */
	public static final int OUTSIDE = 4;
	/** Height over the sky stop at which the ferry crosses the rim. */
	public static final double OVER_RIM = 3.0D;
	/** Distance kept from the stair column's centre. */
	public static final double STAIR_CLEAR = 6.0D;
	/** Hover of the hull bottom over a stop. */
	public static final double HOVER = 0.15D;

	private final List<Vec3> points;
	private final double[] along;

	private FeriRute(List<Vec3> points) {
		this.points = List.copyOf(points);
		this.along = new double[this.points.size()];
		double total = 0.0D;
		for (int i = 1; i < this.points.size(); i++) {
			total += this.points.get(i).distanceTo(this.points.get(i - 1));
			along[i] = total;
		}
	}

	/** The route from {@code bottom} (ground stop) to {@code top} (sky stop), or null when the stops make no sense. */
	public static @Nullable FeriRute plan(SkyHallView hall, BlockPos bottom, BlockPos top, int maxY) {
		BoundingBox box = hall.islandBox();
		double ceiling = maxY - 4.0D;
		Vec3 start = new Vec3(bottom.getX() + 0.5D, bottom.getY() + HOVER, bottom.getZ() + 0.5D);
		Vec3 end = new Vec3(top.getX() + 0.5D, Math.min(ceiling, top.getY() + HOVER), top.getZ() + 0.5D);
		double under = Math.min(ceiling, hall.skyFloorY() - UNDER);
		if (under <= start.y + 2.0D || end.y <= under) {
			return null;
		}
		double minX = box.minX() - OUTSIDE;
		double maxX = box.maxX() + 1 + OUTSIDE;
		double minZ = box.minZ() - OUTSIDE;
		double maxZ = box.maxZ() + 1 + OUTSIDE;
		double cx = hall.origin().getX() + 0.5D;
		double cz = hall.origin().getZ() + 0.5D;
		// the stair column to keep clear of on the way out
		double sx = Double.NaN;
		double sz = Double.NaN;
		BoundingBox kaki = hall.kakiBox();
		if (kaki != null) {
			sx = (kaki.minX() + kaki.maxX() + 1) * 0.5D;
			sz = (kaki.minZ() + kaki.maxZ() + 1) * 0.5D;
		}
		Vec3 up = new Vec3(start.x, under, start.z);
		Vec3 out = outward(start.x, start.z, cx, cz, sx, sz, minX, maxX, minZ, maxZ);
		Vec3 low = new Vec3(out.x, under, out.z);
		double rimY = Math.min(ceiling, end.y + OVER_RIM);
		Vec3 high = new Vec3(out.x, rimY, out.z);
		Vec3 door = exit(end.x, end.z, cx, cz, minX, maxX, minZ, maxZ);
		List<Vec3> path = new ArrayList<>();
		path.add(start);
		path.add(up);
		path.add(low);
		path.add(high);
		for (Vec3 corner : around(high.x, high.z, door.x, door.z, minX, maxX, minZ, maxZ)) {
			path.add(new Vec3(corner.x, rimY, corner.z));
		}
		path.add(new Vec3(door.x, rimY, door.z));
		path.add(new Vec3(end.x, rimY, end.z));
		path.add(end);
		return new FeriRute(dedupe(path));
	}

	/** The same points backwards (sky stop to ground stop). */
	public FeriRute reversed() {
		List<Vec3> copy = new ArrayList<>(points);
		Collections.reverse(copy);
		return new FeriRute(copy);
	}

	public double length() {
		return along[along.length - 1];
	}

	public Vec3 start() {
		return points.get(0);
	}

	public Vec3 end() {
		return points.get(points.size() - 1);
	}

	/** The point {@code s} blocks along the route (clamped). */
	public Vec3 at(double s) {
		if (s <= 0.0D) {
			return start();
		}
		for (int i = 1; i < points.size(); i++) {
			if (s <= along[i]) {
				double seg = along[i] - along[i - 1];
				double t = seg <= 1.0E-6D ? 1.0D : (s - along[i - 1]) / seg;
				return points.get(i - 1).lerp(points.get(i), t);
			}
		}
		return end();
	}

	/** The horizontal heading (degrees, Minecraft yaw) of the leg at {@code s}, or NaN on a vertical leg. */
	public float yawAt(double s) {
		for (int i = 1; i < points.size(); i++) {
			if (s <= along[i] || i == points.size() - 1) {
				Vec3 a = points.get(i - 1);
				Vec3 b = points.get(i);
				double dx = b.x - a.x;
				double dz = b.z - a.z;
				if (dx * dx + dz * dz < 1.0E-4D) {
					return Float.NaN;
				}
				return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
			}
		}
		return Float.NaN;
	}

	/** Every point (for tests and the debug line). */
	public List<Vec3> points() {
		return points;
	}

	// ================================================================== geometry

	/** The first bearing out of the box from (x, z) that keeps clear of the stair column. */
	private static Vec3 outward(double x, double z, double cx, double cz, double sx, double sz, double minX, double maxX, double minZ,
			double maxZ) {
		double base = Math.atan2(z - cz, x - cx);
		if (Math.abs(z - cz) < 1.0E-3D && Math.abs(x - cx) < 1.0E-3D) {
			base = 0.0D;
		}
		double[] turns = {0.0D, 0.5236D, -0.5236D, 1.0472D, -1.0472D, 1.5708D, -1.5708D, 2.0944D, -2.0944D};
		Vec3 fallback = null;
		for (double turn : turns) {
			double a = base + turn;
			Vec3 hit = boxExit(x, z, Math.cos(a), Math.sin(a), minX, maxX, minZ, maxZ);
			if (fallback == null) {
				fallback = hit;
			}
			if (Double.isNaN(sx) || segmentDistance(sx, sz, x, z, hit.x, hit.z) >= STAIR_CLEAR) {
				return hit;
			}
		}
		return fallback;
	}

	/** The point straight out from the sky stop (radial from the throne) on the outside ring. */
	private static Vec3 exit(double x, double z, double cx, double cz, double minX, double maxX, double minZ, double maxZ) {
		double dx = x - cx;
		double dz = z - cz;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0E-3D) {
			dx = 0.0D;
			dz = 1.0D;
		} else {
			dx /= len;
			dz /= len;
		}
		return boxExit(x, z, dx, dz, minX, maxX, minZ, maxZ);
	}

	/** Where the ray from (x, z) along (dx, dz) leaves the rectangle (the point itself when already outside). */
	private static Vec3 boxExit(double x, double z, double dx, double dz, double minX, double maxX, double minZ, double maxZ) {
		if (x <= minX || x >= maxX || z <= minZ || z >= maxZ) {
			return new Vec3(x, 0.0D, z);
		}
		double t = Double.MAX_VALUE;
		if (dx > 1.0E-6D) {
			t = Math.min(t, (maxX - x) / dx);
		} else if (dx < -1.0E-6D) {
			t = Math.min(t, (minX - x) / dx);
		}
		if (dz > 1.0E-6D) {
			t = Math.min(t, (maxZ - z) / dz);
		} else if (dz < -1.0E-6D) {
			t = Math.min(t, (minZ - z) / dz);
		}
		if (t == Double.MAX_VALUE) {
			t = 0.0D;
		}
		return new Vec3(x + dx * t, 0.0D, z + dz * t);
	}

	/**
	 * The corners of the rectangle (minX..maxX × minZ..maxZ) passed going from boundary point a to boundary point b the
	 * short way round; empty when both lie on the same side.
	 */
	private static List<Vec3> around(double ax, double az, double bx, double bz, double minX, double maxX, double minZ, double maxZ) {
		double w = maxX - minX;
		double d = maxZ - minZ;
		double perimeter = 2.0D * (w + d);
		double pa = perimeterPos(ax, az, minX, maxX, minZ, maxZ);
		double pb = perimeterPos(bx, bz, minX, maxX, minZ, maxZ);
		double forward = Math.floorMod((long) Math.round((pb - pa) * 1000.0D), (long) Math.round(perimeter * 1000.0D)) / 1000.0D;
		boolean ahead = forward <= perimeter - forward;
		double[] corners = {0.0D, w, w + d, 2.0D * w + d};
		Vec3[] points = {new Vec3(minX, 0.0D, minZ), new Vec3(maxX, 0.0D, minZ), new Vec3(maxX, 0.0D, maxZ), new Vec3(minX, 0.0D, maxZ)};
		List<Vec3> out = new ArrayList<>();
		double span = ahead ? forward : perimeter - forward;
		// walk the corners in travel order
		List<double[]> passed = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			double offset = ahead ? corners[i] - pa : pa - corners[i];
			offset = ((offset % perimeter) + perimeter) % perimeter;
			if (offset > 1.0E-6D && offset < span - 1.0E-6D) {
				passed.add(new double[]{offset, i});
			}
		}
		passed.sort((p, q) -> Double.compare(p[0], q[0]));
		for (double[] p : passed) {
			out.add(points[(int) p[1]]);
		}
		return out;
	}

	/** Position along the rectangle's boundary, clockwise from (minX, minZ) along +x. */
	private static double perimeterPos(double x, double z, double minX, double maxX, double minZ, double maxZ) {
		double w = maxX - minX;
		double d = maxZ - minZ;
		double cx = Math.max(minX, Math.min(maxX, x));
		double cz = Math.max(minZ, Math.min(maxZ, z));
		double toTop = Math.abs(cz - minZ);
		double toRight = Math.abs(maxX - cx);
		double toBottom = Math.abs(maxZ - cz);
		double toLeft = Math.abs(cx - minX);
		double best = Math.min(Math.min(toTop, toRight), Math.min(toBottom, toLeft));
		if (best == toTop) {
			return cx - minX;
		}
		if (best == toRight) {
			return w + (cz - minZ);
		}
		if (best == toBottom) {
			return w + d + (maxX - cx);
		}
		return 2.0D * w + d + (maxZ - cz);
	}

	/** Distance from (px, pz) to the segment (ax, az)-(bx, bz). */
	private static double segmentDistance(double px, double pz, double ax, double az, double bx, double bz) {
		double vx = bx - ax;
		double vz = bz - az;
		double len = vx * vx + vz * vz;
		double t = len < 1.0E-9D ? 0.0D : Math.max(0.0D, Math.min(1.0D, ((px - ax) * vx + (pz - az) * vz) / len));
		double dx = ax + vx * t - px;
		double dz = az + vz * t - pz;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static List<Vec3> dedupe(List<Vec3> path) {
		List<Vec3> out = new ArrayList<>();
		for (Vec3 p : path) {
			if (out.isEmpty() || out.get(out.size() - 1).distanceToSqr(p) > 1.0E-4D) {
				out.add(p);
			}
		}
		return out;
	}
}
