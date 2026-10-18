package dev.kultivator.kehancuran;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * The shapes of Mode Kehancuran (DESIGN 20.3.21, 22.11.4): read-only geometry that lists the positions a destructive
 * cast may change, in the order {@link WorldBreaker} works through them (spheres, bowls and columns centre-out, trenches,
 * pits and discs top-down, fissures and fans near to far), so a cast cut at the block cap still looks like a crater.
 * Surfaces are read from the heightmap of loaded chunks only (nothing is ever loaded or generated here).
 */
public final class Shapes {

	/** Every shape kind; the ordinal is sent to clients for the preview outline (never reorder; new kinds are appended). */
	public enum Kind {
		TRENCH("trench"),
		SPHERE("sphere"),
		DISC("disc"),
		WITHER("wither"),
		PIT("pit"),
		COLUMN("column"),
		NINE("nine"),
		BOWL("bowl"),
		RING("ring"),
		TUNNEL("tunnel"),
		FISSURE("fissure"),
		SCATTER("scatter"),
		ARC("arc"),
		FAN("fan"),
		STRIP("strip");

		private final String id;

		Kind(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		public static @Nullable Kind byId(String id) {
			for (Kind kind : values()) {
				if (kind.id.equals(id)) {
					return kind;
				}
			}
			return null;
		}

		public static Kind byOrdinal(int ordinal) {
			Kind[] values = values();
			return ordinal < 0 || ordinal >= values.length ? SPHERE : values[ordinal];
		}
	}

	/** {@code #kultivator:strippable}: the foliage STRIP shapes may remove (leaves, flowers, grasses, ferns, vines...). */
	public static final TagKey<Block> STRIPPABLE = TagKey.create(Registries.BLOCK, Kultivator.id("strippable"));

	/**
	 * The size of one shape: {@code radius} (sphere, disc, wither, column, nine, bowl, ring outer, tunnel, arc centreline,
	 * strip; the pit's half width), {@code depth} (blocks below the surface), {@code length} and {@code width} (trench,
	 * fissure, tunnel, fan, arc), {@code spacing} (nine spheres), {@code naturalOnly} (natural ground only), {@code inner}
	 * (the ring's inner radius) and {@code angle} (the arc's degrees, the fan's half-angle).
	 */
	public record Spec(Kind kind, int radius, int depth, int length, int width, int spacing, boolean naturalOnly, int inner, double angle) {
		public static Spec sphere(int radius) {
			return new Spec(Kind.SPHERE, radius, 0, 0, 0, 0, false, 0, 0.0D);
		}

		public static Spec trench(int length, int width, int depth) {
			return new Spec(Kind.TRENCH, width / 2, depth, length, width, 0, false, 0, 0.0D);
		}

		public static Spec disc(int radius, int depth, boolean naturalOnly) {
			return new Spec(Kind.DISC, radius, depth, 0, 0, 0, naturalOnly, 0, 0.0D);
		}

		public static Spec wither(int radius) {
			return new Spec(Kind.WITHER, radius, 0, 0, 0, 0, false, 0, 0.0D);
		}

		public static Spec pit(int halfWidth, int depth) {
			return new Spec(Kind.PIT, halfWidth, depth, 0, 0, 0, false, 0, 0.0D);
		}

		public static Spec column(int radius, int depth) {
			return new Spec(Kind.COLUMN, radius, depth, 0, 0, 0, false, 0, 0.0D);
		}

		public static Spec nine(int radius, int spacing) {
			return new Spec(Kind.NINE, radius, 0, 0, 0, spacing, false, 0, 0.0D);
		}

		/** {@code BOWL r}: a half-ellipsoid of depth ceil(0.6 r) under the impact block, plus a sphere of r/2 above it. */
		public static Spec bowl(int radius) {
			return new Spec(Kind.BOWL, radius, (int) Math.ceil(0.6D * radius), 0, 0, 0, false, 0, 0.0D);
		}

		/** {@code RING ro/ri/d}. */
		public static Spec ring(int outer, int inner, int depth) {
			return new Spec(Kind.RING, outer, depth, 0, 0, 0, false, inner, 0.0D);
		}

		/** {@code TUNNEL r L}: a cylinder along the 3D direction from the first block hit. */
		public static Spec tunnel(int radius, int length) {
			return new Spec(Kind.TUNNEL, radius, 0, length, 0, 0, false, 0, 0.0D);
		}

		/** {@code FISSURE L×w×d}: a lens-shaped vertical slab along the horizontal aim. */
		public static Spec fissure(int length, int width, int depth) {
			return new Spec(Kind.FISSURE, width / 2, depth, length, width, 0, false, 0, 0.0D);
		}

		/** {@code SCATTER r}: built as a BOWL r at one point (the runner places one per impact). */
		public static Spec scatter(int radius) {
			return new Spec(Kind.SCATTER, radius, (int) Math.ceil(0.6D * radius), 0, 0, 0, false, 0, 0.0D);
		}

		/** {@code ARC r° w d}: a crescent trench in front of the caster. */
		public static Spec arc(int radius, int width, int depth, double degrees) {
			return new Spec(Kind.ARC, radius, depth, 0, width, 0, false, 0, degrees);
		}

		/** {@code FAN L° d}: a cone scoop from the caster along the horizontal look (half-angle {@code degrees}). */
		public static Spec fan(int length, int depth, double degrees) {
			return new Spec(Kind.FAN, 0, depth, length, 0, 0, false, 0, degrees);
		}

		/** {@code STRIP r}: foliage only, in a disc. */
		public static Spec strip(int radius) {
			return new Spec(Kind.STRIP, radius, 0, 0, 0, 0, false, 0, 0.0D);
		}

		/** {@code STRIP fan L}: foliage only, in a fan of length L and half-angle {@code degrees}. */
		public static Spec stripFan(int length, double degrees) {
			return new Spec(Kind.STRIP, 0, 0, length, 0, 0, false, 0, degrees);
		}

		/** The same shape, natural blocks only. */
		public Spec natural() {
			return new Spec(kind, radius, depth, length, width, spacing, true, inner, angle);
		}
	}

	/** A position and what it becomes (null = air; Kitab Kehancuran Agung turns grass into coarse dirt). */
	public record Change(BlockPos pos, @Nullable BlockState to) {
	}

	/**
	 * One shape of a player's own technique placed in the world for {@code Kehancuran.submitSelf} (DESIGN 22.11.5):
	 * {@code direction} is the horizontal aim for trenches, fissures, arcs and fans, the 3D beam direction for tunnels.
	 */
	public record Placed(Spec spec, BlockPos anchor, Vec3 direction) {
	}

	/** Blocks of the surface withering looked at below the top of each column. */
	private static final int WITHER_SCAN = 14;
	/** The impact block is searched this far below the anchor. */
	private static final int IMPACT_SCAN = 8;
	/** A ring's column surfaces are taken within this many blocks of the anchor's height. */
	private static final int RING_SURFACE_SPAN = 4;

	/**
	 * The ordered positions of {@code spec} at {@code centre} ({@code direction}: the horizontal aim of trenches, fissures,
	 * arcs and fans, the 3D direction of tunnels). Pure geometry plus read-only surface lookups; filtering (protected zones,
	 * never-touched blocks, the cap) is done by the caller.
	 */
	public static List<Change> build(ServerLevel level, Spec spec, BlockPos centre, Vec3 direction) {
		return build(level, spec, centre, direction, Integer.MAX_VALUE);
	}

	/** Block-state reads left for the build running now (server thread only). */
	private static int readsLeft = Integer.MAX_VALUE;
	private static int lastReads;

	/**
	 * As {@link #build(ServerLevel, Spec, BlockPos, Vec3)} with at most {@code readBudget} block-state reads for the
	 * column scans (22.15); a scan that runs out stops there and the shape is simply smaller. {@link #lastReads} tells
	 * how many were used.
	 */
	public static List<Change> build(ServerLevel level, Spec spec, BlockPos centre, Vec3 direction, int readBudget) {
		int budget = Math.max(0, readBudget);
		readsLeft = budget;
		try {
			return buildShape(level, spec, centre, direction);
		} finally {
			lastReads = budget == Integer.MAX_VALUE ? 0 : budget - Math.max(0, readsLeft);
			readsLeft = Integer.MAX_VALUE;
		}
	}

	/** Reads used by the last budgeted {@link #build}. */
	public static int lastReads() {
		return lastReads;
	}

	/** Takes one read from the budget; false when it ran out. */
	private static boolean read() {
		if (readsLeft <= 0) {
			return false;
		}
		if (readsLeft != Integer.MAX_VALUE) {
			readsLeft--;
		}
		return true;
	}

	private static List<Change> buildShape(ServerLevel level, Spec spec, BlockPos centre, Vec3 direction) {
		return switch (spec.kind()) {
			case SPHERE -> sphere(centre, spec.radius());
			case TRENCH -> trench(level, centre, direction, spec);
			case DISC -> disc(level, centre, spec.radius(), spec.depth());
			case WITHER -> wither(level, centre, spec.radius());
			case PIT -> pit(level, centre, spec.radius(), spec.depth());
			case COLUMN -> column(level, centre, spec.radius(), spec.depth());
			case NINE -> nine(centre, spec.radius(), spec.spacing());
			case BOWL, SCATTER -> bowl(level, centre, spec.radius(), Math.max(1, spec.depth()));
			case RING -> ring(level, centre, spec.radius(), spec.inner(), spec.depth());
			case TUNNEL -> tunnel(centre, direction, spec.radius(), spec.length());
			case FISSURE -> fissure(level, centre, direction, spec);
			case ARC -> arc(level, centre, direction, spec);
			case FAN -> fan(level, centre, direction, spec);
			case STRIP -> strip(level, centre, direction, spec);
		};
	}

	/** Centre-out shells. */
	static List<Change> sphere(BlockPos centre, int radius) {
		List<BlockPos> list = new ArrayList<>();
		double limit = (radius + 0.5D) * (radius + 0.5D);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx * dx + dy * dy + dz * dz <= limit) {
						list.add(centre.offset(dx, dy, dz));
					}
				}
			}
		}
		// Centre-out by column, each column top-down (WorldBreaker never carves under a block it had to leave).
		list.sort(Comparator.comparingDouble((BlockPos pos) -> horizontal(pos, centre)).thenComparingInt(pos -> -pos.getY()));
		return air(list);
	}

	/**
	 * The top solid block of a loaded column (heightmap without leaves), or null when the chunk is not loaded, the read
	 * budget ran out or the column's top is a fluid (the heightmap counts water: a shape never starts on a lake and only
	 * cuts the hidden part under its bed). One block-state read.
	 */
	static @Nullable Integer surface(ServerLevel level, int x, int z) {
		BlockPos probe = new BlockPos(x, level.getMinY(), z);
		if (!level.isLoaded(probe)) {
			return null;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		if (!read() || !level.getBlockState(new BlockPos(x, top, z)).getFluidState().isEmpty()) {
			return null;
		}
		return top;
	}

	/**
	 * The impact block: the first solid block at or below the anchor (≤ 8 blocks), else the column surface; null when the
	 * scan meets a fluid before a solid block (a bowl never sinks to a lake bed).
	 */
	static @Nullable BlockPos impact(ServerLevel level, BlockPos anchor) {
		if (!level.isLoaded(anchor)) {
			return anchor;
		}
		BlockPos.MutableBlockPos cursor = anchor.mutable();
		for (int d = 0; d <= IMPACT_SCAN && read(); d++) {
			cursor.setY(anchor.getY() - d);
			BlockState state = level.getBlockState(cursor);
			if (!state.getFluidState().isEmpty()) {
				return null;
			}
			if (!state.isAir()) {
				return cursor.immutable();
			}
		}
		Integer top = surface(level, anchor.getX(), anchor.getZ());
		return top == null ? null : new BlockPos(anchor.getX(), top, anchor.getZ());
	}

	/** Along the aim from the impact point: {@code width} wide, {@code depth} below the surface; top-down. */
	static List<Change> trench(ServerLevel level, BlockPos start, Vec3 direction, Spec spec) {
		Vec3 dir = new Vec3(direction.x, 0.0D, direction.z);
		dir = dir.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : dir.normalize();
		Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
		List<BlockPos> list = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		int half = spec.width() / 2;
		for (int step = 0; step < spec.length(); step++) {
			for (int lateral = -half; lateral <= half; lateral++) {
				double x = start.getX() + 0.5D + dir.x * step + side.x * lateral;
				double z = start.getZ() + 0.5D + dir.z * step + side.z * lateral;
				int bx = (int) Math.floor(x);
				int bz = (int) Math.floor(z);
				Integer top = surface(level, bx, bz);
				if (top == null) {
					continue;
				}
				for (int d = 0; d < spec.depth(); d++) {
					BlockPos pos = new BlockPos(bx, top - d, bz);
					if (seen.add(pos.asLong())) {
						list.add(pos);
					}
				}
			}
		}
		list.sort(Comparator.comparingInt((BlockPos pos) -> -pos.getY()).thenComparingDouble(pos -> pos.distSqr(start)));
		return air(list);
	}

	/** A disc {@code depth} deep below each column's surface; top-down. */
	static List<Change> disc(ServerLevel level, BlockPos centre, int radius, int depth) {
		List<BlockPos> list = new ArrayList<>();
		double limit = (radius + 0.5D) * (radius + 0.5D);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > limit) {
					continue;
				}
				Integer top = surface(level, centre.getX() + dx, centre.getZ() + dz);
				if (top == null) {
					continue;
				}
				for (int d = 0; d < depth; d++) {
					list.add(new BlockPos(centre.getX() + dx, top - d, centre.getZ() + dz));
				}
			}
		}
		list.sort(Comparator.comparingInt((BlockPos pos) -> -pos.getY()).thenComparingDouble(pos -> pos.distSqr(centre)));
		return air(list);
	}

	/** A 2r+1 square pit {@code depth} deep below each column's surface; top-down. */
	static List<Change> pit(ServerLevel level, BlockPos centre, int half, int depth) {
		List<BlockPos> list = new ArrayList<>();
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				Integer top = surface(level, centre.getX() + dx, centre.getZ() + dz);
				if (top == null) {
					continue;
				}
				for (int d = 0; d < depth; d++) {
					list.add(new BlockPos(centre.getX() + dx, top - d, centre.getZ() + dz));
				}
			}
		}
		list.sort(Comparator.comparingInt((BlockPos pos) -> -pos.getY()).thenComparingDouble(pos -> pos.distSqr(centre)));
		return air(list);
	}

	/** A column of radius r from the target's surface down {@code depth}; centre-out by distance from the axis. */
	static List<Change> column(ServerLevel level, BlockPos centre, int radius, int depth) {
		List<BlockPos> list = new ArrayList<>();
		double limit = (radius + 0.5D) * (radius + 0.5D);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > limit) {
					continue;
				}
				// Each column from its own surface, so higher ground around the centre never keeps its top layer.
				Integer top = surface(level, centre.getX() + dx, centre.getZ() + dz);
				if (top == null) {
					continue;
				}
				for (int d = 0; d < depth; d++) {
					list.add(new BlockPos(centre.getX() + dx, top - d, centre.getZ() + dz));
				}
			}
		}
		list.sort(Comparator.comparingDouble((BlockPos pos) -> horizontal(pos, centre)).thenComparingInt(pos -> -pos.getY()));
		return air(list);
	}

	/** Nine spheres in a 3x3 grid {@code spacing} apart, the middle one first, each centre-out. */
	static List<Change> nine(BlockPos centre, int radius, int spacing) {
		List<Change> list = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		int[][] order = {{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
		for (int[] cell : order) {
			for (Change change : sphere(centre.offset(cell[0] * spacing, 0, cell[1] * spacing), radius)) {
				if (seen.add(change.pos().asLong())) {
					list.add(change);
				}
			}
		}
		return list;
	}

	/**
	 * BOWL r: a half-ellipsoid under the impact block ((dx² + dz²)/r² + (dy/d)² ≤ 1 for dy in [−d, 0]) plus everything above
	 * it within a sphere of r/2 (trees and walls in the blast); every column also opens up to its own ground (at most r
	 * above the centre), so higher ground never keeps a lid over the hollow. Centre-out by distance.
	 */
	static List<Change> bowl(ServerLevel level, BlockPos anchor, int radius, int depth) {
		BlockPos centre = impact(level, anchor);
		if (centre == null) {
			return List.of();
		}
		List<BlockPos> list = new ArrayList<>();
		double r2 = (radius + 0.5D) * (radius + 0.5D);
		double up = radius * 0.5D + 0.5D;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double h = dx * dx + dz * dz;
				if (h > r2) {
					continue;
				}
				int floor = -(int) Math.floor((depth + 0.5D) * Math.sqrt(Math.max(0.0D, 1.0D - h / r2)));
				int cap = h <= up * up ? (int) Math.floor(Math.sqrt(up * up - h)) : 0;
				Integer ground = surface(level, centre.getX() + dx, centre.getZ() + dz);
				if (ground == null) {
					continue;
				}
				int roof = Math.max(cap, Math.min(ground - centre.getY(), radius));
				for (int dy = roof; dy >= floor; dy--) {
					list.add(centre.offset(dx, dy, dz));
				}
			}
		}
		// Centre-out by column, each column top-down (WorldBreaker never carves under a block it had to leave).
		list.sort(Comparator.comparingDouble((BlockPos pos) -> horizontal(pos, centre)).thenComparingInt(pos -> -pos.getY()));
		return air(list);
	}

	/** RING ro/ri/d: an annulus {@code d} deep below each column's surface (taken within ±4 of the anchor); inner edge outward. */
	static List<Change> ring(ServerLevel level, BlockPos centre, int outer, int inner, int depth) {
		List<BlockPos> list = new ArrayList<>();
		double max = (outer + 0.5D) * (outer + 0.5D);
		double min = inner * inner;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -outer; dx <= outer; dx++) {
			for (int dz = -outer; dz <= outer; dz++) {
				double h = dx * dx + dz * dz;
				if (h > max || h < min) {
					continue;
				}
				int x = centre.getX() + dx;
				int z = centre.getZ() + dz;
				if (!level.isLoaded(new BlockPos(x, centre.getY(), z))) {
					continue;
				}
				Integer top = null;
				for (int y = centre.getY() + RING_SURFACE_SPAN; y >= centre.getY() - RING_SURFACE_SPAN && read(); y--) {
					cursor.set(x, y, z);
					BlockState state = level.getBlockState(cursor);
					if (!state.isAir() && state.getFluidState().isEmpty() && state.canOcclude()) {
						top = y;
						break;
					}
				}
				if (top == null) {
					continue;
				}
				for (int d = 0; d < depth; d++) {
					list.add(new BlockPos(x, top - d, z));
				}
			}
		}
		list.sort(Comparator.comparingDouble((BlockPos pos) -> horizontal(pos, centre)).thenComparingInt(pos -> -pos.getY()));
		return air(list);
	}

	/** TUNNEL r L: a cylinder of radius r along {@code direction} from the first block hit, length L; near to far. */
	static List<Change> tunnel(BlockPos start, Vec3 direction, int radius, int length) {
		Vec3 dir = direction.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : direction.normalize();
		Vec3 origin = Vec3.atCenterOf(start);
		Vec3 end = origin.add(dir.scale(length));
		int minX = (int) Math.floor(Math.min(origin.x, end.x) - radius - 1);
		int maxX = (int) Math.ceil(Math.max(origin.x, end.x) + radius + 1);
		int minY = (int) Math.floor(Math.min(origin.y, end.y) - radius - 1);
		int maxY = (int) Math.ceil(Math.max(origin.y, end.y) + radius + 1);
		int minZ = (int) Math.floor(Math.min(origin.z, end.z) - radius - 1);
		int maxZ = (int) Math.ceil(Math.max(origin.z, end.z) + radius + 1);
		double r2 = (radius + 0.5D) * (radius + 0.5D);
		List<BlockPos> list = new ArrayList<>();
		List<Double> along = new ArrayList<>();
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					Vec3 rel = new Vec3(x + 0.5D - origin.x, y + 0.5D - origin.y, z + 0.5D - origin.z);
					double t = rel.dot(dir);
					if (t < -0.5D || t > length + 0.5D) {
						continue;
					}
					if (rel.subtract(dir.scale(t)).lengthSqr() <= r2) {
						list.add(new BlockPos(x, y, z));
						along.add(t);
					}
				}
			}
		}
		Integer[] index = new Integer[list.size()];
		for (int i = 0; i < index.length; i++) {
			index[i] = i;
		}
		java.util.Arrays.sort(index, Comparator.comparingDouble(along::get));
		List<BlockPos> sorted = new ArrayList<>(list.size());
		for (Integer i : index) {
			sorted.add(list.get(i));
		}
		return air(sorted);
	}

	/**
	 * FISSURE L×w×d: a vertical slab along the horizontal aim, w wide (1-3); the depth at fraction f of the length is
	 * max(1, round(d × sin(πf))) below each column's surface ("the earth splits"); near to far, then top-down.
	 */
	static List<Change> fissure(ServerLevel level, BlockPos start, Vec3 direction, Spec spec) {
		Vec3 dir = new Vec3(direction.x, 0.0D, direction.z);
		dir = dir.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : dir.normalize();
		Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
		int width = Math.max(1, Math.min(3, spec.width()));
		int length = Math.max(2, spec.length());
		List<BlockPos> list = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (int step = 0; step < length; step++) {
			double f = (step + 0.5D) / length;
			int depth = Math.max(1, (int) Math.round(spec.depth() * Math.sin(Math.PI * f)));
			for (int lateral = 0; lateral < width; lateral++) {
				double offset = lateral - (width - 1) / 2.0D;
				int bx = (int) Math.floor(start.getX() + 0.5D + dir.x * step + side.x * offset);
				int bz = (int) Math.floor(start.getZ() + 0.5D + dir.z * step + side.z * offset);
				Integer top = surface(level, bx, bz);
				if (top == null) {
					continue;
				}
				for (int d = 0; d < depth; d++) {
					BlockPos pos = new BlockPos(bx, top - d, bz);
					if (seen.add(pos.asLong())) {
						list.add(pos);
					}
				}
			}
		}
		return air(list);
	}

	/** ARC r° w d: an annular sector (centreline radius r, width w, arc ° centred on the look yaw), d deep; middle outward. */
	static List<Change> arc(ServerLevel level, BlockPos centre, Vec3 direction, Spec spec) {
		Vec3 dir = new Vec3(direction.x, 0.0D, direction.z);
		dir = dir.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : dir.normalize();
		double halfAngle = Math.max(5.0D, spec.angle()) / 2.0D;
		double innerR = Math.max(0.0D, spec.radius() - spec.width() / 2.0D);
		double outerR = spec.radius() + spec.width() / 2.0D + 0.5D;
		int reach = (int) Math.ceil(outerR);
		List<BlockPos> list = new ArrayList<>();
		List<Double> offsets = new ArrayList<>();
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				double h = Math.sqrt(dx * dx + dz * dz);
				if (h < innerR || h > outerR || h < 1.0E-3D) {
					continue;
				}
				double cos = (dx * dir.x + dz * dir.z) / h;
				double angle = Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, cos))));
				if (angle > halfAngle) {
					continue;
				}
				Integer top = surface(level, centre.getX() + dx, centre.getZ() + dz);
				if (top == null) {
					continue;
				}
				for (int d = 0; d < Math.max(1, spec.depth()); d++) {
					list.add(new BlockPos(centre.getX() + dx, top - d, centre.getZ() + dz));
					offsets.add(angle * 100.0D + d);
				}
			}
		}
		return air(sortBy(list, offsets));
	}

	/** FAN L° d: a cone scoop from the caster safe radius + 1 along the horizontal look; near to far. */
	static List<Change> fan(ServerLevel level, BlockPos centre, Vec3 direction, Spec spec) {
		Vec3 dir = new Vec3(direction.x, 0.0D, direction.z);
		dir = dir.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : dir.normalize();
		double halfAngle = Math.max(5.0D, spec.angle());
		int start = KultivatorConfig.get().kehancuranCasterSafeRadius + 1;
		int length = Math.max(start + 1, spec.length());
		List<BlockPos> list = new ArrayList<>();
		List<Double> distances = new ArrayList<>();
		for (int dx = -length; dx <= length; dx++) {
			for (int dz = -length; dz <= length; dz++) {
				double h = Math.sqrt(dx * dx + dz * dz);
				if (h < start || h > length + 0.5D) {
					continue;
				}
				double cos = (dx * dir.x + dz * dir.z) / h;
				if (Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, cos)))) > halfAngle) {
					continue;
				}
				Integer top = surface(level, centre.getX() + dx, centre.getZ() + dz);
				if (top == null) {
					continue;
				}
				for (int d = 0; d < Math.max(1, spec.depth()); d++) {
					list.add(new BlockPos(centre.getX() + dx, top - d, centre.getZ() + dz));
					distances.add(h * 100.0D + d);
				}
			}
		}
		return air(sortBy(list, distances));
	}

	/**
	 * STRIP r / STRIP fan L: foliage only ({@code #kultivator:strippable}) from each column's top down to the surface
	 * (14-block scan), in a disc r or a fan of length L with the spec's half-angle; top-down, centre-out. Never logs, soil,
	 * saplings, crops or farmland (the tag lists none of them).
	 */
	static List<Change> strip(ServerLevel level, BlockPos centre, Vec3 direction, Spec spec) {
		boolean fan = spec.length() > 0 && spec.angle() > 0.0D;
		int reach = fan ? spec.length() : spec.radius();
		Vec3 dir = new Vec3(direction.x, 0.0D, direction.z);
		dir = dir.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : dir.normalize();
		List<BlockPos> list = new ArrayList<>();
		double limit = (reach + 0.5D) * (reach + 0.5D);
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				double h2 = dx * dx + dz * dz;
				if (h2 > limit) {
					continue;
				}
				if (fan && h2 > 0.0D) {
					double cos = (dx * dir.x + dz * dir.z) / Math.sqrt(h2);
					if (Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, cos)))) > spec.angle()) {
						continue;
					}
				}
				int x = centre.getX() + dx;
				int z = centre.getZ() + dz;
				if (!level.isLoaded(new BlockPos(x, level.getMinY(), z))) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				for (int d = 0; d <= WITHER_SCAN && read(); d++) {
					BlockPos pos = new BlockPos(x, top - d, z);
					BlockState state = level.getBlockState(pos);
					if (state.isAir()) {
						continue;
					}
					if (state.is(STRIPPABLE)) {
						list.add(pos);
					} else if (state.canOcclude()) {
						break;
					}
				}
			}
		}
		list.sort(Comparator.comparingInt((BlockPos pos) -> -pos.getY()).thenComparingDouble(pos -> horizontal(pos, centre)));
		return air(list);
	}

	/**
	 * Surface withering within radius r: grass block, podzol, mycelium and rooted dirt become coarse dirt; leaves, grass,
	 * ferns, flowers and vines are removed; crops and farmland are skipped; no holes are dug.
	 */
	static List<Change> wither(ServerLevel level, BlockPos centre, int radius) {
		List<Change> list = new ArrayList<>();
		double limit = (radius + 0.5D) * (radius + 0.5D);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > limit) {
					continue;
				}
				int x = centre.getX() + dx;
				int z = centre.getZ() + dz;
				BlockPos probe = new BlockPos(x, level.getMinY(), z);
				if (!level.isLoaded(probe)) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				for (int d = 0; d <= WITHER_SCAN && read(); d++) {
					BlockPos pos = new BlockPos(x, top - d, z);
					BlockState state = level.getBlockState(pos);
					if (state.isAir()) {
						continue;
					}
					BlockState to = witherInto(state);
					if (to != null) {
						list.add(new Change(pos, to.isAir() ? null : to));
						if (!to.isAir()) {
							break;
						}
					} else if (state.is(BlockTags.CROPS) || state.is(Blocks.FARMLAND) || state.canOcclude()) {
						break;
					}
				}
			}
		}
		list.sort(Comparator.comparingInt((Change change) -> -change.pos().getY()).thenComparingDouble(change -> change.pos().distSqr(centre)));
		return list;
	}

	/** What a block withers into (air for plants, coarse dirt for living ground), or null when it stays. */
	public static @Nullable BlockState witherInto(BlockState state) {
		if (state.is(BlockTags.CROPS) || state.is(Blocks.FARMLAND)) {
			return null;
		}
		if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.ROOTED_DIRT)) {
			return Blocks.COARSE_DIRT.defaultBlockState();
		}
		if (state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
				|| state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.VINE) || state.is(BlockTags.CAVE_VINES)) {
			return Blocks.AIR.defaultBlockState();
		}
		return null;
	}

	/** Natural ground Getaran Bumi may shatter: stone and netherrack bases, dirt, sand, red sand, gravel, end stone. */
	public static boolean natural(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.DIRT)
				|| state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.END_STONE);
	}

	// ================================================================== no sealed hollows

	/** Column key of a position ({@code BlockPos.asLong(x, 0, z)}). */
	public static long columnKey(BlockPos pos) {
		return BlockPos.asLong(pos.getX(), 0, pos.getZ());
	}

	/** True for a block that would roof a hollow: solid (occluding) or a fluid. */
	public static boolean roofs(BlockState state) {
		return !state.isAir() && (state.canOcclude() || !state.getFluidState().isEmpty());
	}

	/** True for the point-blast shapes that may open sideways (a wall hit) and so skip the open-top column check. */
	public static boolean pointBlast(Kind kind) {
		return kind == Kind.TUNNEL || kind == Kind.SPHERE || kind == Kind.NINE;
	}

	/**
	 * Drops every planned column whose highest planned block has a solid or fluid, unplanned block right above it (one read
	 * per column, loaded chunks only; an unloaded or unread top drops the column too). {@code check[i]} false leaves
	 * position i out of the test (point blasts, wither, strip). Returns the kept indices in their order.
	 */
	public static List<Integer> openColumns(ServerLevel level, List<BlockPos> planned, boolean[] check) {
		Map<Long, Integer> highest = new HashMap<>();
		Set<Long> all = new HashSet<>(planned.size() * 2);
		for (int i = 0; i < planned.size(); i++) {
			BlockPos pos = planned.get(i);
			all.add(pos.asLong());
			if (check[i]) {
				highest.merge(columnKey(pos), pos.getY(), Math::max);
			}
		}
		Set<Long> sealed = new HashSet<>();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (Map.Entry<Long, Integer> entry : highest.entrySet()) {
			BlockPos column = BlockPos.of(entry.getKey());
			cursor.set(column.getX(), entry.getValue() + 1, column.getZ());
			if (all.contains(cursor.asLong())) {
				continue;
			}
			if (!level.isLoaded(cursor) || roofs(level.getBlockState(cursor))) {
				sealed.add(entry.getKey());
			}
		}
		List<Integer> kept = new ArrayList<>(planned.size());
		for (int i = 0; i < planned.size(); i++) {
			if (!check[i] || !sealed.contains(columnKey(planned.get(i)))) {
				kept.add(i);
			}
		}
		return kept;
	}

	private static double horizontal(BlockPos pos, BlockPos centre) {
		double dx = pos.getX() - centre.getX();
		double dz = pos.getZ() - centre.getZ();
		return dx * dx + dz * dz;
	}

	private static List<BlockPos> sortBy(List<BlockPos> list, List<Double> keys) {
		Integer[] index = new Integer[list.size()];
		for (int i = 0; i < index.length; i++) {
			index[i] = i;
		}
		java.util.Arrays.sort(index, Comparator.comparingDouble(keys::get));
		List<BlockPos> sorted = new ArrayList<>(list.size());
		for (Integer i : index) {
			sorted.add(list.get(i));
		}
		return sorted;
	}

	private static List<Change> air(List<BlockPos> positions) {
		List<Change> list = new ArrayList<>(positions.size());
		for (BlockPos pos : positions) {
			list.add(new Change(pos, null));
		}
		return list;
	}

	private Shapes() {
	}
}
