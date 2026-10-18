package dev.kultivator.sect.build.blueprint.templates.t7t9;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Interiors;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Rank;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Tiny placement parts relative to a (possibly terrain-fitted) level, which {@code Marks.block} cannot do because it
 * takes an absolute y: a stack of states at one column ({@link #stack}), and an {@link Interiors} preset drawn in any
 * frame ({@link #interior}: the alchemy floor of the Menara Pil, a bell inside a tower). Template-local.
 */
public final class Put {

	/** {@code states} stacked upward from {@code level + dy} at (x, z), in {@code phase}. */
	public static Part stack(int x, int z, Lv level, int dy, Phase phase, BlockState... states) {
		BlockState[] copy = states.clone();
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				int y = ctx.level(level) + dy;
				return new BoundingBox(x, y, z, x, y + Math.max(0, copy.length - 1), z);
			}

			@Override
			public String kind() {
				return "block";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				Phase previous = sink.phase();
				sink.phase(phase);
				int y = ctx.level(level) + dy;
				for (int i = 0; i < copy.length; i++) {
					if (copy[i] != null) {
						sink.set(x, y + i, z, copy[i]);
					}
				}
				sink.phase(previous);
			}
		};
	}

	/**
	 * An interior preset in a frame at (x, z) facing {@code front} whose people stand at {@code level + dy}, inside the frame
	 * rectangle {@code x0..x1 × z0..z1} (door toward +z), {@code height} of clear room.
	 */
	public static Part interior(int x, int z, Direction front, Lv level, int dy, Interiors.Preset preset, int count, int x0, int x1, int z0, int z1,
			int height, Rank rank) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				int y = ctx.level(level) + dy;
				return Part.box(x, z, front, x0, y - 1, z0, x1, y + height, z1);
			}

			@Override
			public String kind() {
				return "interior";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				sink.pushFacing(x, ctx.level(level) + dy, z, front);
				Interiors.emit(preset, count, ctx, sink, x0, x1, z0, z1, height, rank);
				sink.pop();
			}
		};
	}

	/** Extra brewing stands and a second lectern (the exact items of templates 4-9) in a frame like {@link #interior}. */
	public static Part fittings(int x, int z, Direction front, Lv level, int dy, int[][] brewing, int[][] lecterns) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				int y = ctx.level(level) + dy;
				return Part.box(x, z, front, -6, y, -6, 6, y + 1, 6);
			}

			@Override
			public String kind() {
				return "fittings";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				sink.pushFacing(x, ctx.level(level) + dy, z, front);
				Phase previous = sink.phase();
				sink.phase(Phase.INTERIOR);
				for (int[] at : brewing) {
					Interiors.brewingStand(sink, at[0], at[1]);
				}
				for (int[] at : lecterns) {
					Interiors.secondLectern(sink, at[0], at[1]);
				}
				sink.phase(previous);
				sink.pop();
			}
		};
	}

	/** Air over x0..x1 × z0..z1 from {@code level + dy0} to {@code level + dy1} (opens a bench, a pass-through), phase WALL. */
	public static Part air(int x0, int z0, int x1, int z1, Lv level, int dy0, int dy1) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				int y = ctx.level(level);
				return new BoundingBox(Math.min(x0, x1), y + Math.min(dy0, dy1), Math.min(z0, z1), Math.max(x0, x1), y + Math.max(dy0, dy1), Math.max(z0, z1));
			}

			@Override
			public String kind() {
				return "opening";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				Phase previous = sink.phase();
				sink.phase(Phase.WALL);
				int y = ctx.level(level);
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
						for (int dy = Math.min(dy0, dy1); dy <= Math.max(dy0, dy1); dy++) {
							sink.air(x, y + dy, z);
						}
					}
				}
				sink.phase(previous);
			}
		};
	}

	/**
	 * A library room (DESIGN 21.2 T3) in a frame at (x, z) facing {@code front}, people standing at {@code level + dy}: exactly
	 * {@code shelves} bookshelves along the back and side walls of the room x0..x1 × z0..z1 (up to {@code rows} high, never
	 * in the two front rows), {@code lecterns} lecterns (1-2) near the door with the anchor "library" between them, and
	 * reading tables. Used where the anchor must stay within 16 of the stele while the building stands farther out.
	 */
	public static Part library(int x, int z, Direction front, Lv level, int dy, int x0, int x1, int z0, int z1, int shelves, int lecterns, int rows) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				int y = ctx.level(level) + dy;
				return Part.box(x, z, front, x0, y - 1, z0, x1, y + rows, z1);
			}

			@Override
			public String kind() {
				return "library";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				sink.pushFacing(x, ctx.level(level) + dy, z, front);
				Phase previous = sink.phase();
				sink.phase(Phase.INTERIOR);
				int left = shelves;
				for (int y = 0; y < rows && left > 0; y++) {
					for (int fx = x0; fx <= x1 && left > 0; fx++) {
						sink.set(fx, y, z0, Palette.st(Blocks.BOOKSHELF));
						left--;
					}
					for (int fz = z0 + 1; fz <= z1 - 2 && left > 0; fz++) {
						for (int fx : new int[]{x0, x1}) {
							if (left > 0) {
								sink.set(fx, y, fz, Palette.st(Blocks.BOOKSHELF));
								left--;
							}
						}
					}
				}
				// Free-standing shelf rows when the walls are full.
				for (int fz = z0 + 2; fz <= z1 - 3 && left > 0; fz += 2) {
					for (int fx = x0 + 2; fx <= x1 - 2 && left > 0; fx++) {
						if (Math.abs(fx) <= 1) {
							continue;
						}
						sink.set(fx, 0, fz, Palette.st(Blocks.BOOKSHELF));
						left--;
					}
				}
				int lz = z1 - 1;
				sink.set(-2, 0, lz, Palette.facing(Blocks.LECTERN, Direction.SOUTH));
				if (lecterns >= 2) {
					sink.set(2, 0, lz, Palette.facing(Blocks.LECTERN, Direction.SOUTH));
				}
				sink.air(0, 0, lz);
				sink.air(0, 1, lz);
				if (z1 - z0 >= 5) {
					sink.set(0, 0, z0 + 2, Palette.slab(Blocks.DARK_OAK_SLAB, true));
					sink.set(0, 1, z0 + 2, Palette.candles(Palette.candle(DyeColor.RED), 2, true));
				}
				sink.anchor("library", 0, 0, lz);
				sink.phase(previous);
				sink.pop();
			}
		};
	}

	private Put() {
	}
}
