package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A Qi gathering seat exactly as DESIGN 4 / {@code QiFormations} reads it (DESIGN 21.9 {@code QiSeat}), inlaid in a floor:
 * the seat is the block the master sits on (the floor cell under the standing cell).
 * <ul>
 * <li>Tier 1: amethyst blocks at (±3, 0) and (0, ±3) on the seat's level.</li>
 * <li>Tier 2: tier 1 + amethyst at (±3, ±3) and the seat is a gold block.</li>
 * <li>Tier 3: tier 2 + end rods standing on the four cardinal amethyst blocks + a lodestone under the gold seat.</li>
 * </ul>
 * Exact items (DESIGN 21.11): 4 amethyst (tier 1), 8 (tier 2), 8 + 4 end rods + 1 lodestone (tier 3).
 */
public final class QiSeat implements Part {

	private final int x;
	private final int z;
	private final Lv level;
	private final int tier;

	/** A seat at template-local (x, z) whose standing level is {@code level}. */
	public QiSeat(int x, int z, Lv level, int tier) {
		this.x = x;
		this.z = z;
		this.level = level;
		this.tier = Math.max(1, Math.min(3, tier));
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		return new BoundingBox(x - 3, y - 2, z - 3, x + 3, y + 1, z + 3);
	}

	@Override
	public String kind() {
		return "qi_seat";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		Phase previous = sink.phase();
		sink.phase(Phase.ORNAMENT);
		emitAt(sink, x, ctx.level(level), z, tier);
		sink.anchor("meditation", x, ctx.level(level), z + 1);
		sink.phase(previous);
	}

	/** Draws a seat in the current frame at frame (sx, standing y, sz). */
	public static void emitAt(Sink sink, int sx, int y, int sz, int tier) {
		int floor = y - 1;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			sink.set(sx + 3 * direction.getStepX(), floor, sz + 3 * direction.getStepZ(), Palette.st(Blocks.AMETHYST_BLOCK));
			sink.air(sx + 3 * direction.getStepX(), y, sz + 3 * direction.getStepZ());
		}
		if (tier >= 2) {
			sink.set(sx, floor, sz, Palette.st(Palette.GOLD));
			for (int[] corner : new int[][]{{3, 3}, {3, -3}, {-3, 3}, {-3, -3}}) {
				sink.set(sx + corner[0], floor, sz + corner[1], Palette.st(Blocks.AMETHYST_BLOCK));
			}
		}
		if (tier >= 3) {
			sink.set(sx, floor - 1, sz, Palette.st(Blocks.LODESTONE));
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				sink.set(sx + 3 * direction.getStepX(), y, sz + 3 * direction.getStepZ(), Palette.facing(Blocks.END_ROD, Direction.UP));
			}
		}
		sink.air(sx, y, sz);
		sink.air(sx, y + 1, sz);
	}
}
