package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Random;

/**
 * Template-specific set pieces of DESIGN 21.9 ({@code SpiritWay}, {@code StoneBoat}, {@code GrottoHill}). Template-local.
 * <ul>
 * <li>{@link #spiritWay}: pairs of stone guardians (lions and stelae) facing the axis every {@code spacing} cells along z,
 * with ancient pines behind them (template 8, after the Ming tombs' spirit way).</li>
 * <li>{@link #stoneBoat}: a marble boat moored in a pond with a small ting on deck (template 7, after the Summer Palace).</li>
 * <li>{@link #grottoHill}: the Cave-Heaven grotto hill (template 7): a rockery hill of radius r with a carved cave gate
 * facing {@code gate}; the tower well is placed inside it by a {@link TowerShaft} with a GROTTO entrance.</li>
 * </ul>
 * Art direction from real architecture, not novel canon (the Cave Heaven names come from DESIGN 21.1 template 7).
 */
public final class SetPieces {

	/** Guardian pairs at x = ±{@code offset} from z0 to z1 every {@code spacing}, pines 3 further out. */
	public static Part spiritWay(int offset, int z0, int z1, int spacing, Lv level) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				int y = ctx.level(level);
				return new BoundingBox(-offset - 6, y - 1, Math.min(z0, z1), offset + 6, y + 12, Math.max(z0, z1));
			}

			@Override
			public String kind() {
				return "spirit_way";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				int step = Math.max(3, spacing);
				int i = 0;
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z += step) {
					for (int side : new int[]{-1, 1}) {
						Direction face = side < 0 ? Direction.EAST : Direction.WEST;
						Part guardian = i % 2 == 0 ? Stamp.lion(side * offset, z, face, level) : Stamp.boundaryStone(side * offset, z, face, level);
						guardian.emit(ctx, sink);
						if (i % 2 == 1) {
							new Tree(Tree.Kind.PINE, side * (offset + 4), z, level).force(true).emit(ctx, sink);
						}
					}
					i++;
				}
			}
		};
	}

	/** A marble boat at (x, z) along z, {@code length} long, floating in a pond whose path level is {@code level}. */
	public static Part stoneBoat(int x, int z, int length, Lv level) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				int y = ctx.level(level);
				return new BoundingBox(x - 3, y - 4, z - length / 2, x + 3, y + 9, z + length / 2);
			}

			@Override
			public String kind() {
				return "stone_boat";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				int y = ctx.level(level);
				int half = length / 2;
				sink.phase(Phase.PLINTH);
				for (int dz = -half; dz <= half; dz++) {
					int w = Math.abs(dz) >= half - 1 ? 1 : 2;
					for (int dx = -w; dx <= w; dx++) {
						for (int fy = y - 4; fy <= y - 2; fy++) {
							sink.set(x + dx, fy, z + dz, Palette.st(Math.abs(dx) == w ? Palette.MARBLE_FACE : Palette.FILL_STONE));
						}
						sink.set(x + dx, y - 1, z + dz, Palette.st(Palette.MARBLE_DECK));
						if (Math.abs(dx) == w) {
							sink.set(x + dx, y, z + dz, Palette.st(Palette.MARBLE_RAIL));
						}
					}
				}
				sink.set(x, y, z - half, Palette.stair(Palette.MARBLE_STAIRS, Direction.SOUTH, false));
				sink.set(x, y, z + half, Palette.stair(Palette.MARBLE_STAIRS, Direction.NORTH, false));
				new Pavilion(x, z, level).size(3).rank(Rank.B).emit(ctx, sink);
			}
		};
	}

	/** The grotto hill: a rockery of radius r with a cave gate 3 wide × 3 high facing {@code gate}. */
	public static Part grottoHill(int x, int z, int r, Lv level, Direction gate) {
		Rock hill = Rock.hill(x, z, level, r);
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				return hill.bounds(ctx);
			}

			@Override
			public String kind() {
				return "grotto_hill";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				hill.emit(ctx, sink);
				int y = ctx.level(level);
				Random random = ctx.random("grotto" + x + "," + z);
				sink.phase(Phase.GARDEN);
				// The carved cave gate: a dark arch into the hill with a chiseled frame and a hanging soul lantern.
				for (int d = 0; d <= r - 1; d++) {
					for (int w = -1; w <= 1; w++) {
						int gx = x + gate.getStepX() * d + gate.getClockWise().getStepX() * w;
						int gz = z + gate.getStepZ() * d + gate.getClockWise().getStepZ() * w;
						for (int h = 0; h < 3; h++) {
							sink.air(gx, y + h, gz);
						}
						sink.set(gx, y - 1, gz, Palette.st(Blocks.POLISHED_TUFF));
						sink.set(gx, y + 3, gz, Palette.st(d == r - 1 ? Blocks.CHISELED_TUFF_BRICKS : Blocks.TUFF));
					}
				}
				int fx = x + gate.getStepX() * (r - 1);
				int fz = z + gate.getStepZ() * (r - 1);
				sink.set(fx, y + 2, fz, Palette.lantern(Blocks.SOUL_LANTERN, true));
				if (random.nextBoolean()) {
					sink.setIfAbsent(fx + gate.getClockWise().getStepX() * 2, y, fz + gate.getClockWise().getStepZ() * 2, Palette.st(Blocks.FERN));
				}
				sink.anchor("grotto", x, y, z);
			}
		};
	}

	private SetPieces() {
	}
}
