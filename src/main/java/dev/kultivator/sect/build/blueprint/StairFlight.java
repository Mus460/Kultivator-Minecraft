package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A flight of stairs (DESIGN 21.9 {@code StairFlight}): from template-local (x, z) on level {@code from} it climbs
 * {@code toward} up to level {@code to}, one cell per step, a flat landing after every {@link #landingEvery} steps,
 * {@code width} wide (odd) between stone cheeks with a low balustrade, filled solid underneath. Style MARBLE uses smooth
 * quartz stairs and an optional {@link #ramp} (the dragon ramp: a chiseled-quartz panel splitting the stair in two lanes,
 * the emperor's way), STONE uses stone-brick stairs. Ends where the next level begins: the last step's top is the
 * {@code to} level, so put the flight's foot just outside the terrace face.
 */
public final class StairFlight implements Part {

	private final int x;
	private final int z;
	private final Direction toward;
	private final Lv from;
	private final Lv to;
	private int width = 5;
	private Terrace.Style style = Terrace.Style.STONE;
	private int ramp;
	private int landingEvery = 4;
	private boolean cheeks = true;

	public StairFlight(int x, int z, Direction toward, Lv from, Lv to) {
		this.x = x;
		this.z = z;
		this.toward = toward.getAxis().isHorizontal() ? toward : Direction.NORTH;
		this.from = from;
		this.to = to;
	}

	public StairFlight width(int width) {
		this.width = Math.max(1, width | 1);
		return this;
	}

	public StairFlight style(Terrace.Style style) {
		this.style = style;
		return this;
	}

	/** A dragon ramp {@code width} cells wide in the middle (marble main stairs). */
	public StairFlight ramp(int width) {
		this.ramp = Math.max(0, width);
		return this;
	}

	public StairFlight landingEvery(int steps) {
		this.landingEvery = Math.max(2, steps);
		return this;
	}

	public StairFlight cheeks(boolean cheeks) {
		this.cheeks = cheeks;
		return this;
	}

	/** Cells the flight runs along its direction (steps plus landings). */
	public int run(Ctx ctx) {
		int rise = Math.abs(ctx.level(to) - ctx.level(from));
		return rise + Math.max(0, (rise - 1) / landingEvery);
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int half = width / 2 + (cheeks ? 1 : 0);
		int run = run(ctx);
		int y0 = Math.min(ctx.level(from), ctx.level(to));
		int y1 = Math.max(ctx.level(from), ctx.level(to));
		// Canonical: across x, the run along +z (the climb direction) from the foot cell.
		return Part.box(x, z, toward, -half, y0 - 2, 0, half, y1 + 2, Math.max(0, run - 1));
	}

	@Override
	public String kind() {
		return "stairs";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int y0 = ctx.level(from);
		int y1 = ctx.level(to);
		int rise = y1 - y0;
		if (rise == 0) {
			return;
		}
		boolean up = rise > 0;
		// Frame: canonical +z = the climb direction, origin at the foot cell.
		sink.pushFacing(x, 0, z, toward);
		int half = width / 2;
		boolean marble = style == Terrace.Style.MARBLE;
		Block stairs = marble ? Palette.MARBLE_STAIRS : Palette.STONE_STAIRS;
		Block fill = marble ? Palette.MARBLE_FACE : Palette.STONE;
		int steps = Math.abs(rise);
		int level = y0;
		int cell = 0;
		sink.phase(Phase.PLINTH);
		for (int s = 0; s < steps; s++) {
			if (s > 0 && s % landingEvery == 0) {
				// A landing: one flat cell at the current level.
				row(ctx, sink, cell, level - 1, half, null, fill, marble, false);
				cell++;
			}
			int stepTop = up ? level + 1 : level;
			int stepY = up ? level : level - 1;
			row(ctx, sink, cell, stepY, half, Palette.stair(stairs, up ? Direction.SOUTH : Direction.NORTH, false), fill, marble, true);
			level = up ? stepTop : level - 1;
			cell++;
		}
		sink.pop();
	}

	private void row(Ctx ctx, Sink sink, int cell, int y, int half, net.minecraft.world.level.block.state.BlockState stair, Block fill, boolean marble,
			boolean step) {
		for (int dx = -half - (cheeks ? 1 : 0); dx <= half + (cheeks ? 1 : 0); dx++) {
			boolean cheek = Math.abs(dx) > half;
			int ground = sink.ground(ctx, dx, cell);
			for (int fy = Math.max(ground + 1, y - 12); fy < y; fy++) {
				sink.set(dx, fy, cell, Palette.st(fill));
			}
			for (int fy = y + 1; fy <= y + 3; fy++) {
				if (!cheek) {
					sink.air(dx, fy, cell);
				}
			}
			if (cheek) {
				sink.set(dx, y, cell, Palette.st(fill));
				sink.set(dx, y + 1, cell, marble ? Palette.st(Palette.MARBLE_RAIL) : Palette.st(Blocks.STONE_BRICK_WALL));
				continue;
			}
			boolean rampCell = ramp > 0 && Math.abs(dx) <= (ramp - 1) / 2;
			if (rampCell && step) {
				sink.set(dx, y, cell, Palette.st(Palette.MARBLE_FRET));
			} else if (stair != null) {
				sink.set(dx, y, cell, stair);
			} else {
				sink.set(dx, y, cell, Palette.st(marble ? Palette.MARBLE_DECK : Palette.PLINTH));
			}
		}
	}
}
