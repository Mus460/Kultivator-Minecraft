package dev.kultivator.util;

import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Loaded paths (DESIGN 22.3.5 rule 6): every ray, teleport and stepped travel first walks its path through the chunk grid
 * with {@code hasChunk} and is cut at the first chunk that is not loaded. Nothing is ever loaded or generated here; the
 * block clip of {@link #ray} is read-only.
 */
public final class LoadedPath {

	/** The farthest point from {@code from} towards {@code to} that stays inside loaded chunks ({@code from} if its own chunk is not loaded). */
	public static Vec3 clip(Level level, Vec3 from, Vec3 to) {
		double t = loadedFraction(level, from, to);
		return t >= 1.0D ? to : from.add(to.subtract(from).scale(t));
	}

	/** True when the whole segment runs through loaded chunks. */
	public static boolean loaded(Level level, Vec3 from, Vec3 to) {
		return loadedFraction(level, from, to) >= 1.0D;
	}

	/**
	 * A read-only block clip (colliders, no fluids) of the loaded part of the segment. When the path is cut, the result
	 * is a MISS at the cut point.
	 */
	public static BlockHitResult ray(Level level, Vec3 from, Vec3 to, @Nullable Entity entity) {
		Vec3 end = clip(level, from, to);
		if (end.distanceToSqr(from) < 1.0E-8D) {
			return BlockHitResult.miss(from, Direction.UP, net.minecraft.core.BlockPos.containing(from));
		}
		ClipContext context = entity == null
				? new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty())
				: new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity);
		BlockHitResult hit = level.clip(context);
		if (hit.getType() == HitResult.Type.MISS && end != to) {
			return BlockHitResult.miss(end, Direction.UP, net.minecraft.core.BlockPos.containing(end));
		}
		return hit;
	}

	/** The share 0-1 of the segment inside loaded chunks, walking the chunk grid (2D DDA over chunk columns). */
	private static double loadedFraction(Level level, Vec3 from, Vec3 to) {
		int cx = SectionPos.blockToSectionCoord(from.x);
		int cz = SectionPos.blockToSectionCoord(from.z);
		if (!level.getChunkSource().hasChunk(cx, cz)) {
			return 0.0D;
		}
		int endX = SectionPos.blockToSectionCoord(to.x);
		int endZ = SectionPos.blockToSectionCoord(to.z);
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		int stepX = dx > 0.0D ? 1 : -1;
		int stepZ = dz > 0.0D ? 1 : -1;
		double tDeltaX = dx == 0.0D ? Double.POSITIVE_INFINITY : 16.0D / Math.abs(dx);
		double tDeltaZ = dz == 0.0D ? Double.POSITIVE_INFINITY : 16.0D / Math.abs(dz);
		double tMaxX = dx == 0.0D ? Double.POSITIVE_INFINITY : ((stepX > 0 ? (cx + 1) * 16.0D : cx * 16.0D) - from.x) / dx;
		double tMaxZ = dz == 0.0D ? Double.POSITIVE_INFINITY : ((stepZ > 0 ? (cz + 1) * 16.0D : cz * 16.0D) - from.z) / dz;
		int guard = 0;
		while ((cx != endX || cz != endZ) && guard++ < 4096) {
			double crossing;
			if (tMaxX < tMaxZ) {
				crossing = tMaxX;
				cx += stepX;
				tMaxX += tDeltaX;
			} else {
				crossing = tMaxZ;
				cz += stepZ;
				tMaxZ += tDeltaZ;
			}
			if (crossing >= 1.0D) {
				break;
			}
			if (!level.getChunkSource().hasChunk(cx, cz)) {
				return Math.max(0.0D, crossing - 1.0E-4D);
			}
		}
		return 1.0D;
	}

	private LoadedPath() {
	}
}
