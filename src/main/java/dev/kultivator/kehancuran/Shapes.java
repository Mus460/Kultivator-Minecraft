package dev.kultivator.kehancuran;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The shapes of Mode Kehancuran (DESIGN 20.3.21): read-only geometry that lists the positions a destructive cast may
 * change, in the order {@link WorldBreaker} works through them: spheres and columns centre-out in shells, trenches, pits
 * and discs top-down, so a cast cut at the block cap still looks like a crater. Surfaces are read from the heightmap of
 * loaded chunks only (nothing is ever loaded or generated here).
 */
public final class Shapes {

	/** Every shape kind; the ordinal is sent to clients for the preview outline (never reorder). */
	public enum Kind {
		TRENCH("trench"),
		SPHERE("sphere"),
		DISC("disc"),
		WITHER("wither"),
		PIT("pit"),
		COLUMN("column"),
		NINE("nine");

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

	/**
	 * The size of one shape: {@code radius} (sphere, disc, wither, column, nine; the pit's half width), {@code depth}
	 * (blocks below the surface), {@code length} and {@code width} (trench), {@code spacing} (nine spheres) and whether
	 * only natural ground may go ({@code naturalOnly}, Getaran Bumi).
	 */
	public record Spec(Kind kind, int radius, int depth, int length, int width, int spacing, boolean naturalOnly) {
		public static Spec sphere(int radius) {
			return new Spec(Kind.SPHERE, radius, 0, 0, 0, 0, false);
		}

		public static Spec trench(int length, int width, int depth) {
			return new Spec(Kind.TRENCH, width / 2, depth, length, width, 0, false);
		}

		public static Spec disc(int radius, int depth, boolean naturalOnly) {
			return new Spec(Kind.DISC, radius, depth, 0, 0, 0, naturalOnly);
		}

		public static Spec wither(int radius) {
			return new Spec(Kind.WITHER, radius, 0, 0, 0, 0, false);
		}

		public static Spec pit(int halfWidth, int depth) {
			return new Spec(Kind.PIT, halfWidth, depth, 0, 0, 0, false);
		}

		public static Spec column(int radius, int depth) {
			return new Spec(Kind.COLUMN, radius, depth, 0, 0, 0, false);
		}

		public static Spec nine(int radius, int spacing) {
			return new Spec(Kind.NINE, radius, 0, 0, 0, spacing, false);
		}
	}

	/** A position and what it becomes (null = air; Kitab Kehancuran Agung turns grass into coarse dirt). */
	public record Change(BlockPos pos, @Nullable BlockState to) {
	}

	/** Blocks of the surface withering looked at below the top of each column. */
	private static final int WITHER_SCAN = 14;

	/**
	 * The ordered positions of {@code spec} at {@code centre} ({@code direction}: the trench's horizontal aim). Pure
	 * geometry plus read-only surface lookups; filtering (protected zones, never-touched blocks, the cap) is done by the
	 * caller.
	 */
	public static List<Change> build(ServerLevel level, Spec spec, BlockPos centre, Vec3 direction) {
		return switch (spec.kind()) {
			case SPHERE -> sphere(centre, spec.radius());
			case TRENCH -> trench(level, centre, direction, spec);
			case DISC -> disc(level, centre, spec.radius(), spec.depth());
			case WITHER -> wither(level, centre, spec.radius());
			case PIT -> pit(level, centre, spec.radius(), spec.depth());
			case COLUMN -> column(level, centre, spec.radius(), spec.depth());
			case NINE -> nine(centre, spec.radius(), spec.spacing());
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
		list.sort(Comparator.comparingDouble(pos -> pos.distSqr(centre)));
		return air(list);
	}

	/** The top solid block of a loaded column (heightmap without leaves), or null when the chunk is not loaded. */
	static @Nullable Integer surface(ServerLevel level, int x, int z) {
		BlockPos probe = new BlockPos(x, level.getMinY(), z);
		if (!level.isLoaded(probe)) {
			return null;
		}
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
	}

	/** Along the aim from the impact point: {@code width} wide, {@code depth} below the surface; top-down. */
	static List<Change> trench(ServerLevel level, BlockPos start, Vec3 direction, Spec spec) {
		Vec3 dir = new Vec3(direction.x, 0.0D, direction.z);
		dir = dir.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : dir.normalize();
		Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
		List<BlockPos> list = new ArrayList<>();
		java.util.Set<Long> seen = new java.util.HashSet<>();
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
		Integer top = surface(level, centre.getX(), centre.getZ());
		int from = top == null ? centre.getY() : top;
		List<BlockPos> list = new ArrayList<>();
		double limit = (radius + 0.5D) * (radius + 0.5D);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > limit) {
					continue;
				}
				for (int d = 0; d < depth; d++) {
					list.add(new BlockPos(centre.getX() + dx, from - d, centre.getZ() + dz));
				}
			}
		}
		list.sort(Comparator.comparingDouble((BlockPos pos) -> horizontal(pos, centre)).thenComparingInt(pos -> -pos.getY()));
		return air(list);
	}

	/** Nine spheres in a 3x3 grid {@code spacing} apart, the middle one first, each centre-out. */
	static List<Change> nine(BlockPos centre, int radius, int spacing) {
		List<Change> list = new ArrayList<>();
		java.util.Set<Long> seen = new java.util.HashSet<>();
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
				for (int d = 0; d <= WITHER_SCAN; d++) {
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

	private static double horizontal(BlockPos pos, BlockPos centre) {
		double dx = pos.getX() - centre.getX();
		double dz = pos.getZ() - centre.getZ();
		return dx * dx + dz * dz;
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
