package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Bridges (DESIGN 21.9 {@code Bridge}; mix them, never repeat one in a view, 21.8 rule 6), template-local, spanning from
 * (x0, z0) to (x1, z1) along one axis, the deck {@code width} wide (odd), the ends on {@code level}.
 * <ul>
 * <li>{@link Type#MOON_ARCH}: a half-step arch (slabs and blocks, walkable) of stone bricks with a quartz-post
 * balustrade; the arch underside stays open over the water.</li>
 * <li>{@link Type#FLAT}: a flat stone-slab bridge one block above the water.</li>
 * <li>{@link Type#STRAIGHT_MARBLE}: a level marble deck with quartz posts every 3rd cell, pale-oak rails and a quartz
 * slab cap (the lake bridge of template 7).</li>
 * <li>{@link Type#ZIGZAG}: the nine-turn bridge: a 1-wide wooden plank walk zigzagging over the water with low rails.</li>
 * <li>{@link Type#STEPPING}: stepping stones (汀步) at the water surface every other cell.</li>
 * <li>{@link Type#COVERED}: a straight deck under a gallery roof (pillars, benches, rolled ridge).</li>
 * <li>{@link Type#SKY}: a high arch between two levels ({@code level} at the start, {@link #endLevel} at the end), jade
 * chains hanging under its middle. The chains and lantern hang 2-5 below the deck middle, so use it only over a valley,
 * never for a short pond or stream crossing (they would land in the water).</li>
 * </ul>
 */
public final class Bridge implements Part {

	public enum Type {
		MOON_ARCH,
		FLAT,
		STRAIGHT_MARBLE,
		ZIGZAG,
		STEPPING,
		COVERED,
		SKY
	}

	private final Type type;
	private final int x0;
	private final int z0;
	private final int x1;
	private final int z1;
	private final Lv level;
	private int width = 3;
	private Lv endLevel;

	public Bridge(Type type, int x0, int z0, int x1, int z1, Lv level) {
		this.type = type;
		this.x0 = x0;
		this.z0 = z0;
		this.x1 = x1;
		this.z1 = z1;
		this.level = level;
		this.endLevel = level;
	}

	public Bridge width(int width) {
		this.width = Math.max(1, width | 1);
		return this;
	}

	/** The level at the far end (sky bridges between peaks). */
	public Bridge endLevel(Lv end) {
		this.endLevel = end;
		return this;
	}

	private boolean alongZ() {
		return x0 == x1 || Math.abs(z1 - z0) >= Math.abs(x1 - x0);
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = Math.min(ctx.level(level), ctx.level(endLevel));
		int top = Math.max(ctx.level(level), ctx.level(endLevel)) + (type == Type.COVERED ? 6 : 4);
		int half = width / 2 + 1;
		boolean z = alongZ();
		return new BoundingBox(Math.min(x0, x1) - (z ? half : 0), y - 3, Math.min(z0, z1) - (z ? 0 : half), Math.max(x0, x1) + (z ? half : 0),
				top, Math.max(z0, z1) + (z ? 0 : half));
	}

	@Override
	public String kind() {
		return "bridge";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		boolean z = alongZ();
		int length = z ? Math.abs(z1 - z0) : Math.abs(x1 - x0);
		Direction along = z ? (z1 >= z0 ? Direction.SOUTH : Direction.NORTH) : (x1 >= x0 ? Direction.EAST : Direction.WEST);
		// Draw in a frame whose +z runs along the span from (x0, z0).
		sink.pushFacing(x0, 0, z0, along);
		int y0 = ctx.level(level);
		int y1 = ctx.level(endLevel);
		int half = width / 2;
		switch (type) {
			case MOON_ARCH, SKY -> arch(sink, length, y0, y1, half, type == Type.SKY);
			case FLAT -> flat(sink, length, y0, half, Palette.slab(Blocks.SMOOTH_STONE_SLAB, false), false);
			case STRAIGHT_MARBLE -> flat(sink, length, y0, half, Palette.st(Palette.MARBLE_DECK), true);
			case ZIGZAG -> zigzag(sink, length, y0);
			case STEPPING -> {
				sink.phase(Phase.PLINTH);
				for (int i = 0; i <= length; i += 2) {
					sink.set(0, y0 - 2, i, Palette.st(Blocks.TUFF_BRICKS));
					sink.set(0, y0 - 1, i, Palette.slab(Palette.STEPPING, false));
				}
			}
			case COVERED -> {
				flat(sink, length, y0, half, Palette.st(Palette.WOOD), false);
				Corridor.roofOver(ctx, sink, -half - 1, half + 1, 0, length, y0, true);
			}
		}
		sink.pop();
	}

	private void arch(Sink sink, int length, int y0, int y1, int half, boolean sky) {
		int rise = sky ? Math.max(3, length / 5) : Math.max(1, Math.min(4, length / 4));
		sink.phase(Phase.PLINTH);
		for (int i = 0; i <= length; i++) {
			double t = length == 0 ? 0.0D : i / (double) length;
			double base = y0 + (y1 - y0) * t;
			int s = (int) Math.round(2.0D * (base - y0) + 2.0D * rise * Math.sin(Math.PI * t));
			// Deck: s half-blocks above the start path (top at y0 + s/2).
			for (int dx = -half; dx <= half; dx++) {
				BlockState deck;
				int deckY;
				if (s % 2 == 0) {
					deckY = y0 - 1 + s / 2;
					deck = Palette.st(sky ? Palette.MARBLE_DECK : Blocks.STONE_BRICKS);
				} else {
					deckY = y0 + (s - 1) / 2;
					deck = Palette.slab(sky ? Palette.MARBLE_CAP : Blocks.STONE_BRICK_SLAB, false);
				}
				sink.set(dx, deckY, i, deck);
				// Abutments at the ends, an open arch in the middle.
				if (i <= 1 || i >= length - 1) {
					for (int fy = deckY - 1; fy >= y0 - 3; fy--) {
						sink.set(dx, fy, i, Palette.st(Blocks.STONE_BRICKS));
					}
				} else {
					sink.set(dx, deckY - 1, i, Palette.stair(Blocks.STONE_BRICK_STAIRS, i < length / 2 ? Direction.SOUTH : Direction.NORTH, true));
				}
				if (Math.abs(dx) == half && half > 0) {
					sink.phase(Phase.ORNAMENT);
					boolean post = i % 3 == 0 || i == length;
					int railY = s % 2 == 0 ? deckY + 1 : deckY + 1;
					sink.set(dx, railY, i, post ? Palette.axis(Palette.MARBLE_POST, Direction.Axis.Y) : Palette.st(Palette.MARBLE_RAIL));
					sink.phase(Phase.PLINTH);
				}
			}
			if (sky && i > length / 4 && i < length * 3 / 4 && i % 4 == 0) {
				sink.phase(Phase.ORNAMENT);
				int under = (s % 2 == 0 ? y0 - 1 + s / 2 : y0 + (s - 1) / 2) - 2;
				for (int c = 0; c < 3; c++) {
					sink.set(0, under - c, i, Palette.jadeChain());
				}
				sink.set(0, under - 3, i, Palette.lantern(Palette.jade(Blocks.COPPER_LANTERN), true));
				sink.phase(Phase.PLINTH);
			}
		}
	}

	private void flat(Sink sink, int length, int y, int half, BlockState deck, boolean marble) {
		sink.phase(Phase.PLINTH);
		for (int i = 0; i <= length; i++) {
			for (int dx = -half; dx <= half; dx++) {
				sink.set(dx, y - 1, i, deck);
				if (i % 4 == 0 || i == length) {
					sink.set(dx, y - 2, i, Palette.st(Blocks.STONE_BRICKS));
					sink.set(dx, y - 3, i, Palette.st(Blocks.STONE_BRICKS));
				}
				if (Math.abs(dx) == half && half > 0) {
					sink.phase(Phase.ORNAMENT);
					boolean post = i % 3 == 0 || i == length;
					Block rail = marble ? Palette.MARBLE_RAIL : Palette.FENCE;
					sink.set(dx, y, i, post ? (marble ? Palette.axis(Palette.MARBLE_POST, Direction.Axis.Y) : Palette.st(Blocks.STONE_BRICK_WALL))
							: Palette.st(rail));
					sink.phase(Phase.PLINTH);
				}
			}
		}
	}

	private void zigzag(Sink sink, int length, int y) {
		sink.phase(Phase.PLINTH);
		int x = 0;
		int dir = 1;
		for (int i = 0; i <= length; i++) {
			if (i > 0 && i % 3 == 0) {
				// A turn: two cells sideways.
				for (int k = 0; k < 2; k++) {
					x += dir;
					sink.set(x, y - 1, i, Palette.slab(Palette.WOOD_SLAB, true));
					sink.set(x, y - 2, i, Palette.st(Blocks.STRIPPED_MANGROVE_LOG));
				}
				dir = -dir;
			}
			sink.set(x, y - 1, i, Palette.slab(Palette.WOOD_SLAB, true));
			if (i % 2 == 0) {
				sink.set(x, y - 2, i, Palette.st(Blocks.STRIPPED_MANGROVE_LOG));
				sink.set(x, y - 3, i, Palette.st(Blocks.STRIPPED_MANGROVE_LOG));
			}
		}
	}
}
