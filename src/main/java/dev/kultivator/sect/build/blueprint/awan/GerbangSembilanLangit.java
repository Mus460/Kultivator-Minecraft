package dev.kultivator.sect.build.blueprint.awan;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.RoofBuilder;
import dev.kultivator.sect.build.blueprint.RoofMat;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The Gate of the Nine Heavens (Gerbang Sembilan Langit, DESIGN 37.4.5 T9) at the front rim of the main island: a white
 * marble gate block with an arched passage, then a tower of stacked eaves, one per heaven, each eave glazed in the colour
 * of its heaven from gold through orange, red, pink, lilac, violet, blue and jade to white at the top (three eaves on the
 * smallest islands), under a gilded spire; a cantilevered marble terrace six blocks out over the clouds with a
 * balustrade. The plaque reads 九天门 · Gerbang Sembilan Langit. It replaces the T6 paifang there (taken down first) and
 * moves the gate banner poles to its foot (anchors {@code panji_gerbang_0/1}); anchor {@code gerbang_langit} under the
 * arch.
 * <p>
 * Sumber: <i>Journey to the West</i> ch. 4: the guardians of the Southern Heavenly Gate let no one pass whose name is not
 * in the register of immortals. The nine colours are art direction.
 */
public final class GerbangSembilanLangit implements Part {

	/** The nine heavens' colours, lowest eave first. */
	static final DyeColor[] WARNA = {DyeColor.YELLOW, DyeColor.ORANGE, DyeColor.RED, DyeColor.PINK, DyeColor.MAGENTA, DyeColor.PURPLE,
			DyeColor.BLUE, DyeColor.CYAN, DyeColor.WHITE};

	private final int z;
	private final int half;
	private final int baseHeight;
	private final int tiers;
	private final int tierHeight;
	private final int terraceZ;

	/**
	 * @param z        the gate's centre line (on the island)
	 * @param terraceZ the last island cell in front (the terrace juts out from it)
	 */
	public GerbangSembilanLangit(int z, int terraceZ, int kelas) {
		this.z = z;
		this.terraceZ = terraceZ;
		this.half = kelas == 0 ? 4 : kelas == 1 ? 6 : 7;
		this.baseHeight = kelas == 0 ? 5 : kelas == 1 ? 7 : 9;
		this.tiers = kelas == 0 ? 3 : 9;
		this.tierHeight = kelas == 2 ? 4 : 3;
	}

	/** The total height above the plaza (with the top roof, the gold finial and the end rod). */
	public int height() {
		return height(baseHeight == 5 ? 0 : baseHeight == 7 ? 1 : 2);
	}

	/** The total height above the plaza of the gate of a size class. */
	public static int height(int kelas) {
		int base = kelas == 0 ? 5 : kelas == 1 ? 7 : 9;
		int tiers = kelas == 0 ? 3 : 9;
		int h = kelas == 2 ? 4 : 3;
		return base + tiers * (h + 1) + 14;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		return new BoundingBox(-half - 3, -3, z - half - 3, half + 3, height(), Math.max(z + half + 3, terraceZ + 7));
	}

	@Override
	public String kind() {
		return "nine_heavens_gate";
	}

	private static RoofMat mat(DyeColor colour) {
		return new RoofMat(Palette.concreteStairs(colour), Palette.concreteSlab(colour), Palette.concrete(colour), Palette.concreteStairs(DyeColor.YELLOW),
				Palette.concreteSlab(DyeColor.YELLOW), Palette.concrete(DyeColor.YELLOW), Palette.glazed(colour),
				Palette.slab(Palette.concreteSlab(DyeColor.YELLOW), false));
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		Phase previous = sink.phase();
		// ---- the terrace jutting out over the clouds, x -4..4, from the rim to 6 beyond
		sink.phase(Phase.PLINTH);
		for (int tz = z + 4; tz <= terraceZ + 6; tz++) {
			for (int tx = -4; tx <= 4; tx++) {
				boolean beyond = tz > terraceZ;
				sink.set(tx, -1, tz, Palette.st(Math.abs(tx) == 4 ? AwanPalette.AWAN_TEPI : AwanPalette.AWAN_LANTAI));
				if (beyond && (Math.abs(tx) == 4 || tz % 2 == 0)) {
					sink.set(tx, -2, tz, Palette.stair(Blocks.QUARTZ_STAIRS, Direction.NORTH, true));
				}
			}
		}
		sink.phase(Phase.WALL);
		for (int tz = terraceZ + 1; tz <= terraceZ + 6; tz++) {
			for (int tx : new int[]{-4, 4}) {
				sink.set(tx, 0, tz, Palette.st((tz - terraceZ) % 3 == 0 ? Blocks.QUARTZ_PILLAR : AwanPalette.PAGAR_BATU));
			}
		}
		for (int tx = -3; tx <= 3; tx++) {
			sink.set(tx, 0, terraceZ + 6, Palette.st(Math.abs(tx) == 3 ? Blocks.QUARTZ_PILLAR : AwanPalette.PAGAR_BATU));
		}
		// ---- the marble gate block with its arch
		sink.phase(Phase.PLINTH);
		for (int fx = -half; fx <= half; fx++) {
			for (int fz = z - 3; fz <= z + 3; fz++) {
				for (int fy = -1; fy < baseHeight; fy++) {
					boolean arch = Math.abs(fx) <= 2 && fy >= 0 && fy <= 3 + (Math.abs(fx) <= 1 ? 1 : 0);
					if (arch) {
						sink.air(fx, fy, fz);
						continue;
					}
					boolean face = Math.abs(fz - z) == 3 || Math.abs(fx) == half;
					BlockState state = fy == baseHeight - 1 ? Palette.st(AwanPalette.AWAN_TEPI)
							: face ? Palette.st(fy % 4 == 3 ? Blocks.CHISELED_QUARTZ_BLOCK : Blocks.QUARTZ_BRICKS) : Palette.st(Blocks.CALCITE);
					sink.set(fx, fy, fz, state);
				}
			}
		}
		// The arch's gilded keystone band and the plaque above the arch on both faces.
		sink.phase(Phase.ORNAMENT);
		for (int fx = -2; fx <= 2; fx++) {
			sink.set(fx, 5, z + 3, Palette.st(Palette.GILT_CAP));
			sink.set(fx, 5, z - 3, Palette.st(Palette.GILT_CAP));
		}
		for (int fx = -1; fx <= 1; fx++) {
			sink.set(fx, 6, z + 3, Palette.st(Palette.PLAQUE_FIELD));
		}
		sink.set(0, 6, z + 4, Palette.facing(Blocks.PALE_OAK_WALL_SIGN, Direction.SOUTH));
		// ---- the tower of nine eaves
		int y = baseHeight;
		int top = Math.max(2, half - 2);
		for (int t = 0; t < tiers; t++) {
			int c = Math.max(2, half - 1 - (int) Math.round((double) (half - 1 - top) * t / Math.max(1, tiers - 1)));
			int h = tierHeight;
			RoofMat mat = mat(WARNA[Math.min(WARNA.length - 1, tiers == 3 ? t * 4 : t)]);
			for (int dx = -c; dx <= c; dx++) {
				for (int dz = -c; dz <= c; dz++) {
					boolean ring = Math.max(Math.abs(dx), Math.abs(dz)) == c;
					if (!ring) {
						sink.phase(Phase.FRAME).set(dx, y - 1, z + dz, Palette.st(Palette.WOOD));
						continue;
					}
					boolean corner = Math.abs(dx) == c && Math.abs(dz) == c;
					for (int py = 0; py < h; py++) {
						if (corner) {
							sink.phase(Phase.FRAME).set(dx, y + py, z + dz, Palette.st(py == 0 ? Palette.PILLAR_BASE : Palette.PILLAR));
						} else {
							boolean middle = Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
							Direction side = Math.abs(dz) >= Math.abs(dx) ? (dz > 0 ? Direction.SOUTH : Direction.NORTH)
									: (dx > 0 ? Direction.EAST : Direction.WEST);
							sink.phase(Phase.WALL).set(dx, y + py, z + dz, middle && py >= 1 && py <= h - 2
									? Palette.lattice(Palette.jade(Blocks.COPPER_TRAPDOOR), side) : Palette.st(Palette.PALACE_WALL));
						}
					}
					sink.phase(Phase.FRAME).set(dx, y + h, z + dz, Palette.st(Palette.BEAM));
				}
			}
			sink.phase(Phase.INTERIOR);
			sink.set(0, y + h - 1, z, Palette.lantern(Blocks.SOUL_LANTERN, true));
			sink.set(0, y + h, z, Palette.ironChain());
			int o = 2;
			sink.push(0, 0, z, 0);
			if (t < tiers - 1) {
				new RoofBuilder(RoofBuilder.Type.SKIRT, -c - o, c + o, -c - o, c + o, y + h - 1, mat).inner(-c, c, -c, c).overhang(o).wall(-c, c, -c, c)
						.emit(sink);
				y += h + 1;
			} else {
				RoofBuilder roof = new RoofBuilder(RoofBuilder.Type.PYRAMID, -c - o, c + o, -c - o, c + o, y + h - 1, mat).overhang(o).wall(-c, c, -c, c)
						.profile(RoofBuilder.Profile.PAVILION).ridgeLight(true);
				roof.emit(sink);
				int apex = roof.ridgeTop();
				sink.phase(Phase.ORNAMENT);
				sink.set(0, apex + 1, 0, Palette.st(Palette.GOLD));
				sink.set(0, apex + 2, 0, Palette.st(Palette.GOLD));
				sink.set(0, apex + 3, 0, Palette.ironChain());
				sink.set(0, apex + 4, 0, Palette.facing(Blocks.END_ROD, Direction.UP));
			}
			sink.pop();
		}
		sink.phase(previous);
		sink.anchor("gerbang_langit", 0, 0, z);
	}
}
