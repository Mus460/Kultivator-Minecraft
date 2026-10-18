package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Random;

/**
 * Area-based grounds of DESIGN 21.9 ({@code HerbBeds}, {@code DrillGround}, {@code ArcheryRange}, {@code Arena},
 * {@code BeastGarden}) and the {@code ScreenWall} rock, each a {@link Part} made by a factory method. Template-local
 * rectangles on a level; targets are {@code TARGET} on stone posts, fence dummies or framed boards (exact items).
 * <ul>
 * <li>{@link #herbBeds}: the Hundred Medicine Garden (<i>A Record of a Mortal's Journey</i> ch. 150-151): raised moss beds
 * of peony, lilac, lily of the valley, torchflower, pitcher plant and fern between stepping paths, a stone-slab curb;
 * anchor "post_garden".</li>
 * <li>{@link #drillGround}: compacted paving with {@code targets} posts and dummies along the far side, weapon racks,
 * banners at the corners; anchor "arena" (the scan's Aula Latihan needs at least 4 targets).</li>
 * <li>{@link #archery}: a paved lane with {@code targets} target posts at the far end.</li>
 * <li>{@link #arena}: a raised square leitai stage with a cinnabar rim, banners and drums at the corners.</li>
 * <li>{@link #beastGarden}: a fenced meadow with a rock, a pond-free trough of hay and a small grove (<i>A Record of a
 * Mortal's Journey</i>, Spirit Beast Mountain: name only).</li>
 * <li>{@link #screenWall}: the screen-wall rock (影壁) where the axis ends before T6 and the terrace stair foot after.</li>
 * </ul>
 */
public final class Grounds {

	private static final Block[] HERBS = {Blocks.PEONY, Blocks.LILAC, Blocks.LILY_OF_THE_VALLEY, Blocks.TORCHFLOWER, Blocks.PITCHER_PLANT, Blocks.FERN};

	/** A rectangle part with a custom emitter. */
	private record Area(String kind, int x0, int z0, int x1, int z1, Lv level, int height, Emitter emitter) implements Part {
		@Override
		public BoundingBox bounds(Ctx ctx) {
			int y = ctx.level(level);
			return new BoundingBox(Math.min(x0, x1), y - 1, Math.min(z0, z1), Math.max(x0, x1), y + height, Math.max(z0, z1));
		}

		@Override
		public void emit(Ctx ctx, Sink sink) {
			Phase previous = sink.phase();
			emitter.emit(ctx, sink, Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), ctx.level(level));
			sink.phase(previous);
		}
	}

	@FunctionalInterface
	private interface Emitter {
		void emit(Ctx ctx, Sink sink, int x0, int z0, int x1, int z1, int y);
	}

	/** Herb beds (Hundred Medicine Garden) over the rectangle; beds run along the longer side. */
	public static Part herbBeds(int x0, int z0, int x1, int z1, Lv level) {
		return new Area("herb_beds", x0, z0, x1, z1, level, 2, (ctx, sink, ax, az, bx, bz, y) -> {
			Random random = ctx.random("herbs" + ax + "," + az);
			boolean alongX = bx - ax >= bz - az;
			sink.phase(Phase.GARDEN);
			for (int x = ax; x <= bx; x++) {
				for (int z = az; z <= bz; z++) {
					int across = alongX ? z - az : x - ax;
					boolean path = across % 3 == 2;
					boolean edge = x == ax || x == bx || z == az || z == bz;
					if (path) {
						sink.set(x, y - 1, z, Palette.st(Palette.MOSS));
						if (Math.floorMod(x + z, 2) == 0) {
							sink.set(x, y, z, Palette.slab(Palette.STEPPING, false));
						}
						continue;
					}
					sink.set(x, y - 1, z, Palette.st(edge ? Blocks.ROOTED_DIRT : Palette.MOSS));
					Block herb = HERBS[random.nextInt(HERBS.length)];
					if (herb == Blocks.PEONY || herb == Blocks.LILAC || herb == Blocks.PITCHER_PLANT) {
						if (sink.get(x, y + 1, z) == null) {
							sink.set(x, y, z, Palette.tallPlant(herb, false));
							sink.set(x, y + 1, z, Palette.tallPlant(herb, true));
						}
					} else {
						sink.set(x, y, z, Palette.st(herb));
					}
				}
			}
			// The gardener stands on a path row when there is one, else just beside the beds.
			int across = alongX ? bz - az : bx - ax;
			if (across >= 2) {
				int px = alongX ? (ax + bx) / 2 : ax + 2;
				int pz = alongX ? az + 2 : (az + bz) / 2;
				sink.air(px, y, pz);
				sink.anchor("post_garden", px, y, pz);
			} else {
				sink.anchor("post_garden", alongX ? bx + 1 : (ax + bx) / 2, y, alongX ? (az + bz) / 2 : bz + 1);
			}
		});
	}

	/** A drill ground with {@code targets} posts (and as many dummies) along the far (back, -z) side. */
	public static Part drillGround(int x0, int z0, int x1, int z1, Lv level, int targets, boolean dummies) {
		return new Area("drill_ground", x0, z0, x1, z1, level, 6, (ctx, sink, ax, az, bx, bz, y) -> {
			sink.phase(Phase.PLINTH);
			for (int x = ax; x <= bx; x++) {
				for (int z = az; z <= bz; z++) {
					boolean border = x == ax || x == bx || z == az || z == bz;
					sink.set(x, y - 1, z, Palette.st(border ? Blocks.STONE_BRICKS : (Math.floorMod(x * 7 + z * 3, 11) == 0 ? Blocks.COBBLESTONE : Blocks.POLISHED_ANDESITE)));
				}
			}
			sink.phase(Phase.ORNAMENT);
			int n = Math.max(1, targets);
			// At most one target every other cell of the interior width; the rest start a second row 2 cells further in, so
			// every requested target stands on its own cell.
			int width = Math.max(1, bx - ax - 1);
			int perRow = Math.max(1, Math.min(n, (width + 1) / 2));
			for (int i = 0; i < n; i++) {
				int row = i / perRow;
				int inRow = Math.min(perRow, n - row * perRow);
				int col = i % perRow;
				int tx = ax + 1 + (int) Math.round((col + 0.5D) * width / inRow - 0.5D);
				int tz = az + 2 * row;
				if (tz >= bz) {
					break;
				}
				if (dummies && i % 2 == 1) {
					sink.set(tx, y, tz, Palette.st(Blocks.DARK_OAK_FENCE));
					sink.set(tx, y + 1, tz, Palette.st(Blocks.DARK_OAK_FENCE));
					sink.set(tx, y + 2, tz, Palette.st(Blocks.TARGET));
				} else {
					sink.set(tx, y, tz, Palette.st(Blocks.STONE_BRICK_WALL));
					sink.set(tx, y + 1, tz, Palette.st(Blocks.TARGET));
				}
			}
			for (int[] corner : new int[][]{{ax, bz}, {bx, bz}}) {
				for (int i = 0; i < 4; i++) {
					sink.set(corner[0], y + i, corner[1], Palette.st(Palette.FENCE));
				}
				sink.set(corner[0], y + 4, corner[1], Palette.banner(DyeColor.RED, Direction.SOUTH));
			}
			sink.anchor("arena", (ax + bx) / 2, y, (az + bz) / 2);
		});
	}

	/** An archery lane: {@code targets} target posts along the west (-x) end. */
	public static Part archery(int x0, int z0, int x1, int z1, Lv level, int targets) {
		return new Area("archery", x0, z0, x1, z1, level, 3, (ctx, sink, ax, az, bx, bz, y) -> {
			sink.phase(Phase.PLINTH);
			for (int x = ax; x <= bx; x++) {
				for (int z = az; z <= bz; z++) {
					sink.set(x, y - 1, z, Palette.st(Math.floorMod(x, 4) == 0 ? Blocks.STONE_BRICKS : Blocks.SMOOTH_STONE));
				}
			}
			sink.phase(Phase.ORNAMENT);
			int n = Math.max(1, targets);
			for (int i = 0; i < n; i++) {
				int tz = az + (int) Math.round((i + 0.5D) * (bz - az + 1) / n - 0.5D);
				sink.set(ax, y, tz, Palette.st(Blocks.STONE_BRICK_WALL));
				sink.set(ax, y + 1, tz, Palette.st(Blocks.TARGET));
			}
		});
	}

	/** A leitai arena stage {@code height} high (1-2) over the rectangle. */
	public static Part arena(int x0, int z0, int x1, int z1, Lv level, int height) {
		return new Area("arena", x0, z0, x1, z1, level, height + 6, (ctx, sink, ax, az, bx, bz, y) -> {
			sink.phase(Phase.PLINTH);
			for (int x = ax; x <= bx; x++) {
				for (int z = az; z <= bz; z++) {
					boolean border = x == ax || x == bx || z == az || z == bz;
					for (int i = 0; i < height; i++) {
						sink.set(x, y + i - 1, z, Palette.st(border ? Blocks.CINNABAR_BRICKS : Blocks.STONE_BRICKS));
					}
					sink.set(x, y + height - 1, z, Palette.st(border ? Blocks.POLISHED_CINNABAR : Blocks.POLISHED_ANDESITE));
				}
			}
			int cx = (ax + bx) / 2;
			for (int i = 0; i < height; i++) {
				sink.set(cx, y + i - 1, bz + 1 + (height - 1 - i), Palette.stair(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false));
			}
			sink.phase(Phase.ORNAMENT);
			for (int[] corner : new int[][]{{ax, az}, {bx, az}, {ax, bz}, {bx, bz}}) {
				for (int i = 0; i < 4; i++) {
					sink.set(corner[0], y + height + i, corner[1], Palette.st(Palette.FENCE));
				}
				sink.set(corner[0], y + height + 4, corner[1], Palette.banner(DyeColor.RED, Direction.SOUTH));
			}
			sink.anchor("arena", cx, y + height, (az + bz) / 2);
		});
	}

	/** A fenced beast meadow with a rock, a hay trough and a few trees. */
	public static Part beastGarden(int x0, int z0, int x1, int z1, Lv level) {
		return new Area("beast_garden", x0, z0, x1, z1, level, 6, (ctx, sink, ax, az, bx, bz, y) -> {
			Random random = ctx.random("beasts" + ax + "," + az);
			sink.phase(Phase.GARDEN);
			for (int x = ax; x <= bx; x++) {
				for (int z = az; z <= bz; z++) {
					boolean border = x == ax || x == bx || z == az || z == bz;
					sink.set(x, y - 1, z, Palette.st(Blocks.GRASS_BLOCK));
					if (border) {
						boolean gate = z == bz && Math.abs(x - (ax + bx) / 2) <= 0;
						sink.set(x, y, z, gate ? Palette.facing(Blocks.SPRUCE_FENCE_GATE, Direction.SOUTH) : Palette.st(Blocks.SPRUCE_FENCE));
					} else if (random.nextInt(6) == 0) {
						sink.set(x, y, z, Palette.st(random.nextBoolean() ? Blocks.SHORT_GRASS : Blocks.FERN));
					}
				}
			}
			Rock.scholarRock(sink, random, ax + 2, y, az + 2, 3);
			sink.set(bx - 2, y, az + 2, Palette.st(Blocks.HAY_BLOCK));
			sink.set(bx - 3, y, az + 2, Palette.st(Blocks.HAY_BLOCK));
		});
	}

	/** The screen-wall rock: a low rockery wall across x, {@code halfWidth} each side, with a moss cap and ferns. */
	public static Part screenWall(int x, int z, Lv level, int halfWidth) {
		return new Area("screen_wall", x - halfWidth, z, x + halfWidth, z + 1, level, 4, (ctx, sink, ax, az, bx, bz, y) -> {
			Random random = ctx.random("screen" + ax + "," + az);
			sink.phase(Phase.GARDEN);
			for (int px = ax; px <= bx; px++) {
				int h = 2 + (Math.abs(px - x) < halfWidth ? 1 : 0) + (random.nextInt(3) == 0 ? 1 : 0);
				for (int pz = az; pz <= bz; pz++) {
					for (int i = 0; i < h; i++) {
						BlockState stone = Palette.st(i == 0 ? Blocks.MOSSY_COBBLESTONE : random.nextBoolean() ? Blocks.CALCITE : Blocks.TUFF);
						if (i == h - 1 && pz == az && random.nextInt(3) == 0) {
							continue;
						}
						sink.set(px, y + i, pz, stone);
					}
				}
				if (random.nextInt(3) == 0) {
					sink.setIfAbsent(px, y, bz + 1, Palette.st(Blocks.FERN));
				}
			}
		});
	}

	private Grounds() {
	}
}
