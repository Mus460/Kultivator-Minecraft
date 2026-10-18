package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A waterfall (DESIGN 21.9 {@code Waterfall}, 21.10 "Fluids"): one water source in a notch walled on 3 sides at the top
 * of a terrace face, falling {@code toward} into a pool the template provides below (a {@link Pond} whose water cells
 * lie under the fall column). The only water the engine gives a fluid tick (the anchor "waterfall*"), so it actually
 * falls; it lands in a source pool and stays contained. Template-local: (x, z) is the notch cell, {@code top} its standing
 * level, {@code bottom} the pool's path level.
 * <p>In game, GARDEN, ORNAMENT and WATER ops never replace blocks an earlier component placed; the notch walls and the
 * fall column are PLINTH (so they may), and the placer lets this source (its "waterfall*" anchor) replace an earlier
 * component's block in the notch cell. Any other water a part emits over an earlier component's deck stays dry.
 */
public final class Waterfall implements Part {

	private final int x;
	private final int z;
	private final Lv top;
	private final Lv bottom;
	private final Direction toward;
	private final String id;

	public Waterfall(String id, int x, int z, Lv top, Lv bottom, Direction toward) {
		this.id = id;
		this.x = x;
		this.z = z;
		this.top = top;
		this.bottom = bottom;
		this.toward = toward.getAxis().isHorizontal() ? toward : Direction.SOUTH;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int ty = ctx.level(top);
		int by = ctx.level(bottom);
		return new BoundingBox(x - 1, Math.min(ty, by) - 3, z - 1, x + 1, Math.max(ty, by) + 1, z + 1);
	}

	@Override
	public String kind() {
		return "waterfall";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int ty = ctx.level(top) - 1;
		int poolSurface = ctx.level(bottom) - 2;
		Direction back = toward.getOpposite();
		Direction left = toward.getCounterClockWise();
		Direction right = toward.getClockWise();
		sink.phase(Phase.PLINTH);
		// The notch: a stone floor, a back wall and two side walls, mossy.
		sink.set(x, ty - 1, z, Palette.st(Palette.STONE_MOSSY));
		for (Direction side : new Direction[]{back, left, right}) {
			sink.set(x + side.getStepX(), ty, z + side.getStepZ(), Palette.st(Palette.STONE_MOSSY));
			sink.set(x + side.getStepX(), ty + 1, z + side.getStepZ(), Palette.st(Palette.STONE));
		}
		// The fall column stays open down to the pool.
		int fx = x + toward.getStepX();
		int fz = z + toward.getStepZ();
		for (int y = ty; y > poolSurface; y--) {
			sink.air(fx, y, fz);
		}
		sink.air(x, ty + 1, z);
		sink.phase(Phase.WATER);
		sink.layer(10_000).set(x, ty, z, Palette.st(Blocks.WATER));
		sink.layerAuto();
		sink.anchor("waterfall_" + id, x, ty, z);
	}
}
