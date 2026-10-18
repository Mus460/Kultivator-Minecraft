package dev.kultivator.sect.build.blueprint.templates.t7t9;

import dev.kultivator.sect.build.blueprint.Corridor;
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
 * Stone piers under a covered gallery bridge (DESIGN 21.3 template 9 T8 "2 covered gallery bridges"; 21.8 rule 6
 * "covered" bridges): a {@link Corridor} carries its own walk on a 5-cell solid band down to 6 below its floor, so over a
 * deeper valley this part adds a pier every {@code spacing} cells along the same line, from the natural ground up to the
 * band, with corbelled shoulders under the band either side (a viaduct). The walk level runs linearly from {@code from} at
 * (x0, z0) to {@code to} at (x1, z1), exactly as the corridor rounds it. Template-local; read-only on {@link Ctx}.
 */
public final class Piers implements Part {

	private final int x0;
	private final int z0;
	private final int x1;
	private final int z1;
	private final Lv from;
	private final Lv to;
	private final int spacing;

	public Piers(int x0, int z0, int x1, int z1, Lv from, Lv to, int spacing) {
		this.x0 = x0;
		this.z0 = z0;
		this.x1 = x1;
		this.z1 = z1;
		this.from = from;
		this.to = to;
		this.spacing = Math.max(3, spacing);
	}

	private boolean alongZ() {
		return x0 == x1 || Math.abs(z1 - z0) >= Math.abs(x1 - x0);
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int top = Math.max(ctx.level(from), ctx.level(to));
		boolean z = alongZ();
		return new BoundingBox(Math.min(x0, x1) - (z ? 2 : 0), ctx.spec().below() > 0 ? -1 - ctx.spec().below() : -1, Math.min(z0, z1) - (z ? 0 : 2),
				Math.max(x0, x1) + (z ? 2 : 0), top - 6, Math.max(z0, z1) + (z ? 0 : 2));
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
		int ya = ctx.level(from);
		int yb = ctx.level(to);
		Phase previous = sink.phase();
		sink.phase(Phase.PLINTH);
		for (int i = spacing; i < length; i += spacing) {
			int y = length == 0 ? ya : ya + (int) Math.round((yb - ya) * (i / (double) length));
			int top = y - 7;
			for (int dx = -2; dx <= 2; dx++) {
				int ground = sink.ground(ctx, dx, i);
				for (int fy = ground + 1; fy <= top; fy++) {
					boolean base = fy <= ground + 2;
					boolean cap = fy == top;
					sink.set(dx, fy, i, Palette.st(cap ? Blocks.CHISELED_STONE_BRICKS : base ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS));
				}
				// Corbelled shoulders under the band on both sides along the span.
				if (top > ground + 2) {
					sink.set(dx, top, i - 1, Palette.stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, true));
					sink.set(dx, top, i + 1, Palette.stair(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, true));
				}
			}
		}
		sink.phase(previous);
		sink.pop();
	}
}
