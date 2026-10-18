package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Gates of DESIGN 21.9 ({@code InnerGate}, {@code GateHall}, {@code GateTower}, {@code CornerTower}). Template-local.
 * <ul>
 * <li>{@link #innerGate}: the T1 wooden gateway or chuihuamen (垂花门): two cinnabar posts, a stripped-mangrove lintel,
 * a small tile roof with lifted tips, hanging pendant posts with jade lanterns ({@code chuihua} true) or hanging lanterns
 * on the posts; {@code halfWidth} is the clear half opening (2 = 5 wide).</li>
 * <li>{@link #gateHall}: a shanmen straddling a wall line: a rank B {@link Hall} whose centre bay is a passage, on a low
 * platform; bays 3 or 5.</li>
 * <li>{@link #gateTower}: template 8's three-storey gate tower (城楼): a stone base with an arched passage and a two-storey
 * hall on top.</li>
 * <li>{@link #cornerTower}: a 2-3 storey corner or watch tower (square, xieshan roof).</li>
 * </ul>
 */
public final class Gates {

	/** A wooden gateway / chuihuamen across the axis at (x, z), the opening facing ±z. */
	public static Part innerGate(int x, int z, Lv level, int halfWidth, boolean chuihua) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				int y = ctx.level(level);
				return new BoundingBox(x - halfWidth - 3, y - 1, z - 2, x + halfWidth + 3, y + 8, z + 2);
			}

			@Override
			public String kind() {
				return "inner_gate";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				int y = ctx.level(level);
				sink.push(x, y, z, 0);
				int post = halfWidth + 1;
				int h = 4;
				RoofMat mat = ctx.roof(Rank.B);
				sink.phase(Phase.PLINTH);
				for (int fx = -post; fx <= post; fx++) {
					sink.set(fx, -1, 0, Palette.st(Palette.PLINTH));
				}
				sink.phase(Phase.FRAME);
				for (int side : new int[]{-post, post}) {
					sink.set(side, 0, 0, Palette.st(Palette.PILLAR_BASE));
					for (int fy = 1; fy <= h; fy++) {
						sink.set(side, fy, 0, Palette.st(Palette.PILLAR_THIN));
					}
					sink.set(side, 0, -1, Palette.stair(Palette.STONE_STAIRS, Direction.SOUTH, false));
					sink.set(side, 0, 1, Palette.stair(Palette.STONE_STAIRS, Direction.NORTH, false));
				}
				for (int fx = -post; fx <= post; fx++) {
					sink.set(fx, h + 1, 0, Palette.axis(Palette.LINTEL, Direction.Axis.X));
				}
				// Small roof: brackets, slopes on ±z, tile ridge, lifted tips.
				sink.phase(Phase.ROOF);
				for (int fx = -post - 1; fx <= post + 1; fx++) {
					sink.layer(1).set(fx, h + 2, -1, Palette.stair(mat.fieldStairs(), Direction.SOUTH, false));
					sink.layer(1).set(fx, h + 2, 1, Palette.stair(mat.fieldStairs(), Direction.NORTH, false));
					sink.layer(2).set(fx, h + 2, 0, Palette.st(mat.fieldFull()));
					sink.layer(3).set(fx, h + 3, 0, Palette.slab(mat.trimSlab(), false));
				}
				for (int fx : new int[]{-post - 2, post + 2}) {
					sink.layer(1).set(fx, h + 2, -1, Palette.slab(mat.trimSlab(), true));
					sink.layer(1).set(fx, h + 2, 1, Palette.slab(mat.trimSlab(), true));
					sink.layer(1).set(fx, h + 2, 0, Palette.slab(mat.trimSlab(), false));
				}
				sink.layerAuto();
				sink.phase(Phase.ORNAMENT);
				if (chuihua) {
					// Hanging flower pendants: short posts hanging from the lintel ends, each with a jade lantern.
					for (int side : new int[]{-post + 1, post - 1}) {
						for (int dz : new int[]{-1, 1}) {
							sink.set(side, h + 1, dz, Palette.st(Palette.BEAM));
							sink.set(side, h, dz, Palette.st(Palette.FENCE));
							sink.set(side, h - 1, dz, Palette.lantern(Palette.jade(Blocks.COPPER_LANTERN), true));
						}
					}
				} else {
					for (int side : new int[]{-halfWidth, halfWidth}) {
						sink.set(side, h, 0, Palette.ironChain());
						sink.set(side, h - 1, 0, Palette.lantern(Blocks.LANTERN, true));
					}
				}
				sink.pop();
			}
		};
	}

	/**
	 * A shanmen of {@code bays} (3 or 5) straddling a wall line at z, centred on x: a rank B passage hall, platform 1,
	 * depth 5, front toward +z (the gate).
	 */
	public static Hall gateHall(int x, int z, Lv level, int bays) {
		int[] spacing = bays >= 5 ? new int[]{3, 3, 6, 3, 3} : new int[]{3, 6, 3};
		return new Hall(x, z).site(level).bays(spacing).depth(5).pillar(5).platform(1).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1)
				.passage(true).porch(0).gableField(Palette.st(Palette.PILLAR));
	}

	/** A 2-3 storey square watch / corner tower of half size {@code half} (core 2·half+1), xieshan roof, rank B. */
	public static Hall cornerTower(int x, int z, Lv level, int half, int storeys, Direction front) {
		int h = Math.max(2, half);
		// Three bays with an even total (narrow end bays): the doorway stays on the axis, never a pillar.
		return new Hall(x, z).site(level).front(front).bays(1, 2 * h - 2, 1).depth(2 * h + 1).pillar(4).platform(1).rank(Rank.B)
				.roof(RoofBuilder.Type.XIESHAN, 1).storeys(storeys >= 2 ? 2 : 1, 4).doors(Hall.Doors.NONE).porch(0).steps(false, false);
	}

	/**
	 * Template 8's gate tower (城楼): a stone-brick base {@code baseHeight} high and 2·half+1 wide across the wall line with
	 * an arched passage 5 wide, and a two-storey rank B hall on top.
	 */
	public static Part gateTower(int x, int z, Lv level, int half, int baseHeight) {
		Hall top = new Hall(x, z).site(level.plus(baseHeight)).bays(4, 6, 4).depth(7).pillar(5).platform(0).rank(Rank.B)
				.roof(RoofBuilder.Type.XIESHAN, 1).storeys(2, 4).doors(Hall.Doors.NONE);
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				return BoundingBox.encapsulating(top.bounds(ctx), new BoundingBox(x - half, ctx.level(level) - 1, z - 4, x + half, ctx.level(level) + baseHeight, z + 4));
			}

			@Override
			public String kind() {
				return "gate_tower";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				int y = ctx.level(level);
				sink.push(x, y, z, 0);
				sink.phase(Phase.PLINTH);
				for (int fx = -half; fx <= half; fx++) {
					for (int fz = -4; fz <= 4; fz++) {
						for (int fy = -1; fy < baseHeight; fy++) {
							boolean arch = Math.abs(fx) <= 2 && fy >= 0 && fy <= 3 + (Math.abs(fx) <= 1 ? 1 : 0);
							if (arch) {
								sink.air(fx, fy, fz);
							} else {
								boolean face = Math.abs(fz) == 4 || Math.abs(fx) == half;
								sink.set(fx, fy, fz, Palette.st(face ? Blocks.DEEPSLATE_BRICKS : Palette.FILL_STONE));
							}
						}
						sink.set(fx, baseHeight - 1, fz, Palette.st(Palette.PLINTH));
					}
				}
				sink.pop();
				top.emit(ctx, sink);
				// The hall's platform foundation reaches down into the base and would refill the arched passage: clear it
				// again after the hall (the last write to a cell wins).
				sink.push(x, y, z, 0);
				sink.phase(Phase.PLINTH);
				for (int fx = -2; fx <= 2; fx++) {
					for (int fz = -4; fz <= 4; fz++) {
						for (int fy = 0; fy <= 3 + (Math.abs(fx) <= 1 ? 1 : 0) && fy < baseHeight; fy++) {
							sink.air(fx, fy, fz);
						}
					}
				}
				sink.pop();
			}

			@Override
			public void decorate(Ctx ctx, Sink sink, int tier) {
				top.decorate(ctx, sink, tier);
			}
		};
	}

	private Gates() {
	}
}
