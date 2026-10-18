package dev.kultivator.sect.build.blueprint.templates.t4t6;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Stone piers under a high gallery or bridge deck (templates 4-6): the library {@code Corridor} fills only 6 below its
 * walk, so a covered gallery bridge over a gorge would float. This part follows the same centre line and level
 * interpolation as the {@code Corridor} it supports and raises a pier of stone bricks (a chiseled course every 4, mossy at
 * the foot) under every post cell, from the natural ground up to the walk, plus an upside-down stair corbel on each side of
 * the pier head. Add it in the same component after the gallery. Template-local, one axis only.
 */
public final class Piers implements Part {

	private final int x0;
	private final int z0;
	private final int x1;
	private final int z1;
	private final Lv start;
	private final Lv end;
	private int spacing = 3;
	private int half = 2;
	private int from;
	private int to = Integer.MAX_VALUE;

	public Piers(int x0, int z0, int x1, int z1, Lv start, Lv end) {
		this.x0 = x0;
		this.z0 = z0;
		this.x1 = x1;
		this.z1 = z1;
		this.start = start;
		this.end = end;
	}

	/** Cells between piers (default 3, the gallery's post spacing). */
	public Piers spacing(int spacing) {
		this.spacing = Math.max(2, spacing);
		return this;
	}

	/** Half width of a pier across the walk (default 2: the gallery's post lines). */
	public Piers half(int half) {
		this.half = Math.max(0, half);
		return this;
	}

	/** Piers only {@code from}..{@code to} cells from the start (the ends rest on terraces or rock of their own). */
	public Piers range(int from, int to) {
		this.from = Math.max(0, from);
		this.to = Math.max(this.from, to);
		return this;
	}

	private boolean alongZ() {
		return x0 == x1 || Math.abs(z1 - z0) >= Math.abs(x1 - x0);
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int low = Math.min(ctx.level(start), ctx.level(end)) - 24;
		int high = Math.max(ctx.level(start), ctx.level(end));
		boolean z = alongZ();
		return new BoundingBox(Math.min(x0, x1) - (z ? half : 0), low, Math.min(z0, z1) - (z ? 0 : half), Math.max(x0, x1) + (z ? half : 0), high,
				Math.max(z0, z1) + (z ? 0 : half));
	}

	@Override
	public String kind() {
		return "piers";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		boolean z = alongZ();
		int length = z ? Math.abs(z1 - z0) : Math.abs(x1 - x0);
		Direction along = z ? (z1 >= z0 ? Direction.SOUTH : Direction.NORTH) : (x1 >= x0 ? Direction.EAST : Direction.WEST);
		sink.pushFacing(x0, 0, z0, along);
		int ya = ctx.level(start);
		int yb = ctx.level(end);
		sink.phase(Phase.PLINTH);
		for (int i = 0; i <= length; i++) {
			if ((i % spacing != 0 && i != length) || i < from || i > to) {
				continue;
			}
			int y = length == 0 ? ya : ya + (int) Math.round((yb - ya) * (i / (double) length));
			for (int dx = -half; dx <= half; dx++) {
				int ground = sink.ground(ctx, dx, i);
				for (int fy = ground + 1; fy <= y - 2; fy++) {
					int course = y - 2 - fy;
					var state = fy <= ground + 1 ? Palette.st(Blocks.MOSSY_STONE_BRICKS)
							: course % 4 == 3 ? Palette.st(Blocks.CHISELED_STONE_BRICKS) : Palette.st(Blocks.STONE_BRICKS);
					sink.set(dx, fy, i, state);
				}
			}
			// Corbels on both faces of the pier head.
			if (y - 3 > sink.ground(ctx, 0, i) + 1) {
				for (int dx = -half; dx <= half; dx++) {
					if (i > 0) {
						sink.setIfAbsent(dx, y - 2, i - 1, Palette.stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, true));
					}
					if (i < length) {
						sink.setIfAbsent(dx, y - 2, i + 1, Palette.stair(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, true));
					}
				}
			}
		}
		sink.pop();
	}
}
