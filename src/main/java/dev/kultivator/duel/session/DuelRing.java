package dev.kultivator.duel.session;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The ring of a duel (DESIGN 35.2.3): only data. Posts, rope, ink circle and talismans are drawn by the client (module V)
 * from the {@code DuelSessionSync}; no entity, no block. The ring line is {@code radius + 1.5}; standing beyond it for
 * {@code ringOutTicks} in a row loses the duel. The server never pushes or teleports anyone back.
 *
 * @param level  where it is
 * @param center the centre, on the ground (found with read-only rays)
 * @param radius 6, 9 or 12
 */
public record DuelRing(ServerLevel level, Vec3 center, float radius) {

	/** The distance of each start mark from the centre (the marks are 6 blocks apart, facing each other). */
	public static final double MARK_OFFSET = 3.0D;
	/** How far the centre may move to find flatter ground. */
	private static final int SEARCH = 6;
	private static final int STEP = 3;
	/** The arena of the sect: a centre this close to the hall stele moves onto it. */
	public static final double SECT_SNAP = 24.0D;

	/** The ring-out line. */
	public double outLine() {
		return radius + 1.5D;
	}

	/** True when {@code entity} stands beyond the ring line (or far above/below the ring). */
	public boolean outside(Entity entity) {
		double dx = entity.getX() - center.x;
		double dz = entity.getZ() - center.z;
		double line = outLine();
		return dx * dx + dz * dz > line * line || Math.abs(entity.getY() - center.y) > 8.0D;
	}

	/** The two start marks on the line through the centre along {@code axis} (A's mark toward A's side). */
	public Vec3[] marks(Vec3 aPos, Vec3 bPos) {
		Vec3 axis = new Vec3(aPos.x - bPos.x, 0.0D, aPos.z - bPos.z);
		if (axis.lengthSqr() < 1.0E-4D) {
			axis = new Vec3(1.0D, 0.0D, 0.0D);
		}
		axis = axis.normalize();
		Vec3 markA = center.add(axis.scale(MARK_OFFSET));
		Vec3 markB = center.subtract(axis.scale(MARK_OFFSET));
		return new Vec3[] {ground(level, markA, center.y), ground(level, markB, center.y)};
	}

	/**
	 * The ring for two fighters: the point between them moved to the flattest ground within 6 blocks (read-only rays;
	 * nothing is changed), or {@code preferred} (the sect stele) when given.
	 */
	public static DuelRing between(ServerLevel level, Vec3 a, Vec3 b, float radius, @Nullable Vec3 preferred) {
		if (preferred != null) {
			return new DuelRing(level, ground(level, preferred, preferred.y), radius);
		}
		Vec3 mid = a.add(b).scale(0.5D);
		Vec3 best = ground(level, mid, mid.y);
		double bestScore = roughness(level, best);
		for (int dx = -SEARCH; dx <= SEARCH; dx += STEP) {
			for (int dz = -SEARCH; dz <= SEARCH; dz += STEP) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				Vec3 at = ground(level, mid.add(dx, 0.0D, dz), mid.y);
				// Flatness first, then nearness to the middle (a small pull so it does not wander for nothing).
				double score = roughness(level, at) + 0.04D * Math.sqrt(dx * dx + dz * dz);
				if (score < bestScore - 0.05D) {
					bestScore = score;
					best = at;
				}
			}
		}
		return new DuelRing(level, best, radius);
	}

	/** The ground under {@code at}: a ray from 3 above to 8 below {@code nearY} (read-only); {@code at} itself when none. */
	public static Vec3 ground(ServerLevel level, Vec3 at, double nearY) {
		Vec3 from = new Vec3(at.x, nearY + 3.0D, at.z);
		Vec3 to = new Vec3(at.x, nearY - 8.0D, at.z);
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, noEntity()));
		if (hit.getType() == HitResult.Type.MISS) {
			return new Vec3(at.x, nearY, at.z);
		}
		return new Vec3(at.x, hit.getLocation().y, at.z);
	}

	/** The spread of the ground heights on a cross of radius 3 around {@code at} (0 = flat). */
	private static double roughness(ServerLevel level, Vec3 at) {
		double[] heights = new double[5];
		heights[0] = at.y;
		heights[1] = ground(level, at.add(3.0D, 0.0D, 0.0D), at.y).y;
		heights[2] = ground(level, at.add(-3.0D, 0.0D, 0.0D), at.y).y;
		heights[3] = ground(level, at.add(0.0D, 0.0D, 3.0D), at.y).y;
		heights[4] = ground(level, at.add(0.0D, 0.0D, -3.0D), at.y).y;
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		for (double h : heights) {
			min = Math.min(min, h);
			max = Math.max(max, h);
		}
		return max - min;
	}

	/** No entity context for the ground rays (blocks only). */
	private static net.minecraft.world.phys.shapes.CollisionContext noEntity() {
		return net.minecraft.world.phys.shapes.CollisionContext.empty();
	}
}
