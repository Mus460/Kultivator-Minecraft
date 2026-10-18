package dev.kultivator.sect.build.blueprint.templates.t7t9;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Glaze;
import dev.kultivator.sect.build.blueprint.Lv;
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
 * The Aula Dao Langit of template 9 (DESIGN 21.3 T9, 21.6.5 "Round", 21.8 "Sky elements"): a round, triple-eave hall in
 * Langit blue whatever the plan's glaze, after the Hall of Prayer for Good Harvests: a three-tier round marble terrace
 * with quartz balustrades and steps on the four sides, a ring of cinnabar pillars with jade lattice and four open doors,
 * four inner "dragon well" pillars, three stacked drums each crowned by a round lean-to eave (blue field, yellow trim,
 * lanterns under the eave) and a conical top roof with the gold finial. Template-local, centred on (x, z), standing on
 * {@code level}. Art direction from real architecture, not novel canon.
 */
public final class RoundHall implements Part {

	private static final int H1 = 6;

	private final int x;
	private final int z;
	private final Lv level;
	private int r = 8;
	private int tiers = 3;

	public RoundHall(int x, int z, Lv level) {
		this.x = x;
		this.z = z;
		this.level = level;
	}

	/** The pillar circle radius (6-10, default 8). */
	public RoundHall radius(int radius) {
		this.r = Math.max(6, Math.min(10, radius));
		return this;
	}

	/** Round marble terrace tiers under the hall (0-3, default 3). */
	public RoundHall tiers(int tiers) {
		this.tiers = Math.max(0, Math.min(3, tiers));
		return this;
	}

	private int outer() {
		return r + 3 + 2 * tiers;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		int o = outer() + 1;
		return new BoundingBox(x - o, y - 1, z - o, x + o, y + tiers + H1 + 16 + r + 4, z + o);
	}

	@Override
	public String kind() {
		return "round_hall";
	}

	private static double dist(int dx, int dz) {
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static boolean onRing(int dx, int dz, int radius) {
		double d = dist(dx, dz);
		return d > radius - 0.5D && d <= radius + 0.5D;
	}

	private static boolean pillarAngle(int dx, int dz, int radius, int count) {
		double angle = Math.toDegrees(Math.atan2(dz, dx));
		double step = 360.0D / count;
		double offset = step / 2.0D;
		double rem = Math.floorMod((long) Math.round((angle - offset) * 100), Math.round(step * 100)) / 100.0D;
		double off = Math.min(rem, step - rem);
		return off < Math.toDegrees(0.55D / radius);
	}

	private static boolean door(int dx, int dz) {
		return Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int y0 = ctx.level(level);
		sink.push(x, y0, z, 0);
		Phase previous = sink.phase();
		RoofMat mat = Glaze.LANGIT.roof();
		int floor = tiers;
		int o = outer();
		// The round marble terrace (solid inside), tier by tier.
		sink.phase(Phase.PLINTH);
		for (int dx = -o; dx <= o; dx++) {
			for (int dz = -o; dz <= o; dz++) {
				double d = dist(dx, dz);
				int top = -1;
				for (int k = tiers - 1; k >= 0; k--) {
					if (d <= r + 3 + 2 * (tiers - 1 - k) + 0.5D) {
						top = k;
						break;
					}
				}
				if (top < 0) {
					continue;
				}
				for (int fy = -1; fy < top; fy++) {
					boolean face = d > r + 3 + 2 * (tiers - 1 - top) - 0.5D;
					sink.set(dx, fy, dz, Palette.st(face ? Palette.MARBLE_FACE : Palette.FILL_STONE));
				}
				sink.set(dx, top, dz, Palette.st(Palette.MARBLE_DECK));
			}
		}
		// Balustrades on every tier edge (open at the four stairs) and the steps.
		for (int k = 0; k < tiers; k++) {
			int radius = r + 3 + 2 * (tiers - 1 - k);
			sink.phase(Phase.ORNAMENT);
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (!onRing(dx, dz, radius) || door(dx, dz)) {
						continue;
					}
					boolean post = pillarAngle(dx, dz, radius, 24);
					sink.set(dx, k + 1, dz, post ? Palette.axis(Palette.MARBLE_POST, Direction.Axis.Y) : Palette.st(Palette.MARBLE_RAIL));
				}
			}
			sink.phase(Phase.PLINTH);
			for (Direction side : Direction.Plane.HORIZONTAL) {
				for (int w = -1; w <= 1; w++) {
					int sx = side.getStepX() * (radius + 1) + (side.getAxis() == Direction.Axis.Z ? w : 0);
					int sz = side.getStepZ() * (radius + 1) + (side.getAxis() == Direction.Axis.X ? w : 0);
					sink.set(sx, k, sz, Palette.stair(Palette.MARBLE_STAIRS, side.getOpposite(), false));
				}
			}
		}
		// The hall floor.
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dist(dx, dz) <= r + 0.5D) {
					sink.set(dx, floor - 1, dz, Palette.st(dist(dx, dz) < 2.5D ? Palette.MARBLE_FRET : Palette.FLOOR));
				}
			}
		}
		// Drum 1: pillars, plinth, jade lattice, open doors on the four sides, a beam band.
		drum(sink, r, floor, H1, 12, true);
		// Four inner dragon-well pillars up to the second drum's beam.
		sink.phase(Phase.FRAME);
		int inner = Math.max(2, r / 2 - 1);
		for (int[] p : new int[][]{{inner, inner}, {-inner, inner}, {inner, -inner}, {-inner, -inner}}) {
			sink.set(p[0], floor, p[1], Palette.st(Palette.PILLAR_BASE));
			for (int fy = floor + 1; fy < floor + H1 + 5; fy++) {
				sink.set(p[0], fy, p[1], Palette.st(Palette.PILLAR));
			}
		}
		// Eave 1 over drum 1, leaning on drum 2 (whose lattice shows above it).
		int base1 = floor + H1 + 1;
		ring(sink, mat, r + 3, r - 2, base1, r, floor + H1);
		drum(sink, r - 2, floor + H1 + 1, 6, 8, false);
		// Eave 2 over drum 2, leaning on drum 3.
		int base2 = floor + H1 + 7;
		ring(sink, mat, r + 1, r - 4, base2, r - 2, base2);
		drum(sink, r - 4, floor + H1 + 7, 8, 8, false);
		// The conical top roof with the gold finial.
		int topR = r - 1;
		RoofBuilder roof = new RoofBuilder(RoofBuilder.Type.ROUND, -topR, topR, -topR, topR, floor + H1 + 14, mat).overhang(2)
				.profile(RoofBuilder.Profile.PAVILION).ridgeLight(true);
		roof.emit(sink);
		roof.gild(sink, true, false, null);
		// A palace lantern down the open middle, an altar with candles under it.
		sink.phase(Phase.INTERIOR);
		int under = roof.underside(0, 0);
		if (under != Integer.MIN_VALUE) {
			for (int fy = under; fy > floor + 5; fy--) {
				sink.set(0, fy, 0, Palette.ironChain());
			}
			sink.set(0, floor + 5, 0, Palette.st(Palette.LIGHT));
			sink.set(0, floor + 4, 0, Palette.st(Palette.LIGHT));
			sink.set(0, floor + 3, 0, Palette.slab(Blocks.POLISHED_CINNABAR_SLAB, true));
		}
		sink.set(0, floor, 0, Palette.st(Palette.MARBLE_FRET));
		sink.set(0, floor + 1, 0, Palette.candles(Palette.candle(DyeColor.RED), 3, true));
		for (Direction side : Direction.Plane.HORIZONTAL) {
			sink.set(side.getStepX(), floor, side.getStepZ(), Palette.stair(Blocks.POLISHED_DEEPSLATE_STAIRS, side.getOpposite(), false));
		}
		sink.anchor("aula_dao_langit", 0, floor, 2);
		sink.phase(previous);
		sink.pop();
	}

	/** One drum: pillars at {@code pillars} angles, plinth, jade lattice, beam band on top (doors open in the first drum). */
	private void drum(Sink sink, int radius, int bottom, int height, int pillars, boolean doors) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (!onRing(dx, dz, radius)) {
					continue;
				}
				boolean pillar = pillarAngle(dx, dz, radius, pillars);
				boolean opening = doors && door(dx, dz) && !pillar;
				Direction face = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
				for (int fy = bottom; fy < bottom + height; fy++) {
					int k = fy - bottom;
					BlockState state;
					if (pillar) {
						sink.phase(Phase.FRAME);
						state = Palette.st(k == 0 && doors ? Palette.PILLAR_BASE : Palette.PILLAR);
					} else if (k == height - 1) {
						sink.phase(Phase.FRAME);
						state = Palette.st(Palette.BEAM);
					} else if (opening && k <= 2) {
						sink.phase(Phase.WALL);
						state = Palette.air();
					} else if (k == 0) {
						sink.phase(Phase.WALL);
						state = Palette.st(Palette.PLINTH);
					} else {
						sink.phase(Phase.WALL);
						state = Palette.lattice(Palette.jade(Blocks.COPPER_TRAPDOOR), face);
					}
					sink.set(dx, fy, dz, state);
				}
				sink.phase(Phase.FRAME);
				sink.set(dx, bottom + height - 1, dz, Palette.st(pillar ? Palette.GILT_CAP : Palette.BEAM));
			}
		}
	}

	/**
	 * A round lean-to eave from the eave edge at {@code outerR} up to the drum at {@code innerR} (all +1 slabs, 27°, a
	 * trim eave row and a trim flashing ring against the drum), closed underneath inside the wall radius {@code wallR},
	 * with a lantern under the eave at eight points.
	 */
	private static void ring(Sink sink, RoofMat mat, int outerR, int innerR, int base, int wallR, int fillFrom) {
		sink.phase(Phase.ROOF);
		for (int dx = -outerR - 1; dx <= outerR + 1; dx++) {
			for (int dz = -outerR - 1; dz <= outerR + 1; dz++) {
				double d = dist(dx, dz);
				if (d > outerR + 0.5D || d <= innerR + 0.5D) {
					continue;
				}
				int t = (int) Math.floor(outerR + 0.5D - d);
				int s = 1 + t;
				int y = s % 2 == 1 ? base + (s - 1) / 2 : base + s / 2 - 1;
				boolean flashing = d <= innerR + 1.5D;
				BlockState piece;
				if (flashing) {
					piece = Palette.st(mat.trimFull());
				} else if (s % 2 == 1) {
					piece = Palette.slab(t == 0 ? mat.trimSlab() : mat.fieldSlab(), false);
				} else {
					piece = Palette.slab(t == 0 ? mat.trimSlab() : mat.fieldSlab(), true);
				}
				sink.layer(s).set(dx, y, dz, piece);
				if (d <= wallR + 0.5D) {
					for (int fy = fillFrom; fy < y; fy++) {
						sink.layer(Math.max(1, 2 * (fy - base) + 1)).set(dx, fy, dz, Palette.st(mat.fieldFull()));
					}
				}
			}
		}
		sink.layerAuto();
		sink.phase(Phase.ORNAMENT);
		for (int[] p : new int[][]{{outerR - 1, 0}, {-(outerR - 1), 0}, {0, outerR - 1}, {0, -(outerR - 1)}}) {
			sink.set(p[0], base - 1, p[1], Palette.ironChain());
			sink.set(p[0], base - 2, p[1], Palette.lantern(Blocks.LANTERN, true));
		}
		int k = (int) Math.round((outerR - 1) / Math.sqrt(2.0D));
		for (int[] p : new int[][]{{k, k}, {-k, k}, {k, -k}, {-k, -k}}) {
			sink.set(p[0], base - 1, p[1], Palette.ironChain());
			sink.set(p[0], base - 2, p[1], Palette.lantern(Blocks.LANTERN, true));
		}
	}
}
