package dev.kultivator.sect.build.blueprint.awan;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The island generator (DESIGN 37.4.2): one growth ring of one island of the archipelago ({@link AwanLayout}), optionally
 * one quarter of it ({@link #sektor}, the split of a ring past the per-component op cap: {@code _u/_s/_t/_b}).
 * <ol>
 * <li><b>Rim</b>: the island's {@link AwanShape} (periodic noise, fixed per template) bends every ring the same way, so
 * terraces stay concentric with the coast.</li>
 * <li><b>Top</b>: grass with moss and coarse-dirt patches (Gaya TAMAN, PUNCAK, BATU), one dirt layer under it; the
 * plateau is level 0, the pagoda hill rises in ring 2, the outer ring steps down in terraces except in the level front
 * band; cells under the deck of an earlier bridge keep the deck (their grass is left out).</li>
 * <li><b>Shell only</b>: a column is solid in its top two cells, its bottom two cells and wherever a neighbour column of
 * the rings built so far does not cover that height (cliff faces in calcite-banded strata of stone, andesite and tuff).
 * The belly between stays air: never billed, never placed. Its depth is an inverted cone whose tip leans to one side,
 * dirt and rooted dirt under the turf, then stone and andesite, tuff with calcite streaks, deepslate at the tip.</li>
 * <li><b>Turtle</b> ({@link #kura}, A7-A9, the last ring): a mossy turtle head carved under the front of the belly, the
 * head of one of the fifteen giant turtles of the <i>Liezi</i> that hold the drifting mountains.</li>
 * </ol>
 * Built in FILL from the bottom up, so an island rises from its root to its turf. Reads only the layout (pure).
 * <p>
 * Sumber: <i>Liezi</i>, "Tang Wen" (the five mountains of Guixu borne on the heads of giant turtles); the three levels of
 * Kunlun in the <i>Huainanzi</i> (the terraces). Shapes are art direction.
 */
public final class PulauAwan implements Part {

	/** Top surface styles: share of grass. */
	public enum Gaya {
		TAMAN(0.78D, 0.18D),
		PUNCAK(0.60D, 0.25D),
		BATU(0.25D, 0.20D);

		final double grass;
		final double moss;

		Gaya(double grass, double moss) {
			this.grass = grass;
			this.moss = moss;
		}
	}

	/** The belly shell's thickness (the hollow inside is never placed). */
	private static final int KULIT = 1;

	private final String island;
	private final int ring;
	private int sector = -1;
	private boolean turtle;
	private Gaya gaya = Gaya.TAMAN;

	/** Ring {@code ring} of island {@code island} ("main" or "sat_&lt;fungsi&gt;"). */
	public PulauAwan(String island, int ring) {
		this.island = island;
		this.ring = ring;
	}

	/** Only one quarter: 0 north (-z, "u"), 1 south (+z, "s"), 2 east (+x, "t"), 3 west (-x, "b"). */
	public PulauAwan sektor(int quarter) {
		this.sector = quarter;
		return this;
	}

	public PulauAwan kura(boolean turtle) {
		this.turtle = turtle;
		return this;
	}

	public PulauAwan gaya(Gaya gaya) {
		this.gaya = gaya;
		return this;
	}

	@Override
	public String kind() {
		return "island";
	}

	static AwanLayout.Pulau islandOf(AwanLayout layout, String id) {
		if ("main".equals(id)) {
			return layout.main;
		}
		for (AwanLayout.Satelit s : layout.satellites) {
			if (s.pulau().id.equals(id)) {
				return s.pulau();
			}
		}
		return layout.main;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		AwanLayout layout = AwanLayout.of(ctx.template());
		AwanLayout.Pulau p = islandOf(layout, island);
		int reach = (int) Math.ceil(p.radius() * 1.16D) + 1;
		int top = p.level + (p.main ? Math.max(1, layout.row.bukit()) : 1);
		return new BoundingBox(p.cx - reach, p.deepest() - 1, p.cz - reach, p.cx + reach, top, p.cz + reach);
	}

	// ================================================================== shared column model (also AwanTerrain)

	/** True when (x, z) is a cell of island {@code p} in rings 0..maxRing (clefts of the main island excluded). */
	static boolean exists(AwanLayout layout, AwanLayout.Pulau p, int x, int z, int maxRing) {
		int r = p.ring(x, z);
		if (r < 0 || r > maxRing) {
			return false;
		}
		return !(p.main && r >= 2 && layout.cleft(x, z));
	}

	/** The standing level of an island cell (its top block is at level - 1). */
	static int levelOf(AwanLayout layout, AwanLayout.Pulau p, int x, int z) {
		if (p.main) {
			int r = p.ring(x, z);
			return layout.levelInRing(x, z, Math.max(0, r));
		}
		return p.level;
	}

	/** The y of the lowest block of the column (the belly), always at least two below its top block. */
	static int bottomOf(AwanLayout.Pulau p, int x, int z, int top) {
		double rad = p.radius();
		double nx = (x - p.cx) - p.shape.tipX() * rad;
		double nz = (z - p.cz) - p.shape.tipZ() * rad;
		double n = Math.min(1.0D, p.shape.norm(nx, nz) / rad);
		double k = 0.85D + 0.30D * p.shape.noise2(x, z, 7);
		int d = (int) Math.round(p.depth * (1.0D - Math.pow(n, 1.6D)) * k) + 2;
		return Math.min(p.level - 1 - d, top - 2);
	}

	// ================================================================== emission

	@Override
	public void emit(Ctx ctx, Sink sink) {
		AwanLayout layout = AwanLayout.of(ctx.template());
		AwanLayout.Pulau p = islandOf(layout, island);
		int reach = (int) Math.ceil(p.radius() * 1.16D) + 1;
		int size = reach * 2 + 3;
		int ox = p.cx - reach - 1;
		int oz = p.cz - reach - 1;
		int maxRing = p.main ? ring : 0;
		// Column tops and bottoms of the rings built so far (this ring included); Integer.MIN_VALUE = no island.
		int[] top = new int[size * size];
		int[] bottom = new int[size * size];
		boolean[] mine = new boolean[size * size];
		java.util.Arrays.fill(top, Integer.MIN_VALUE);
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				int x = ox + i;
				int z = oz + j;
				if (!exists(layout, p, x, z, maxRing)) {
					continue;
				}
				int t = levelOf(layout, p, x, z) - 1;
				top[i * size + j] = t;
				bottom[i * size + j] = bottomOf(p, x, z, t);
				int r = p.main ? p.ring(x, z) : 0;
				mine[i * size + j] = r == ring && inSector(p, x, z);
			}
		}
		int tier = p.tiers[Math.min(ring, p.tiers.length - 1)];
		Phase previous = sink.phase();
		sink.phase(Phase.FILL);
		for (int i = 1; i < size - 1; i++) {
			for (int j = 1; j < size - 1; j++) {
				int c = i * size + j;
				if (!mine[c]) {
					continue;
				}
				int x = ox + i;
				int z = oz + j;
				int t = top[c];
				int b = bottom[c];
				boolean deck = p.main && layout.corridor(x, z, tier);
				for (int y = b; y <= t; y++) {
					boolean solid = y == t || y <= b + KULIT - 1 || face(top, bottom, size, i, j, y);
					if (!solid || deck && y == t) {
						continue;
					}
					sink.set(x, y, z, material(layout, p, x, y, z, t, b));
				}
			}
		}
		if (turtle && p.main) {
			turtleHead(layout, p, sink);
		}
		sink.phase(previous);
	}

	private boolean inSector(AwanLayout.Pulau p, int x, int z) {
		if (sector < 0) {
			return true;
		}
		return quarter(x - p.cx, z - p.cz) == sector;
	}

	/** 0 north, 1 south, 2 east, 3 west of an offset from the centre. */
	public static int quarter(int dx, int dz) {
		if (Math.abs(dz) >= Math.abs(dx)) {
			return dz < 0 ? 0 : 1;
		}
		return dx > 0 ? 2 : 3;
	}

	/** True when a horizontal neighbour column does not cover height y (an exposed face). */
	private static boolean face(int[] top, int[] bottom, int size, int i, int j, int y) {
		int[][] n = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] d : n) {
			int c = (i + d[0]) * size + (j + d[1]);
			if (top[c] == Integer.MIN_VALUE || top[c] < y || bottom[c] > y) {
				return true;
			}
		}
		return false;
	}

	private BlockState material(AwanLayout layout, AwanLayout.Pulau p, int x, int y, int z, int t, int b) {
		AwanShape shape = p.shape;
		if (y == t) {
			if (p.main && layout.frontBand(x, z) && Math.abs(x) <= 2 && z > layout.plazaFront) {
				// The approach: a dressed-stone walk along the axis to the gate.
				return Palette.st(Math.abs(x) == 2 ? Palette.AXIS_ACCENT : Palette.AXIS_PAVE);
			}
			double roll = shape.roll(x, y, z, 1);
			if (roll < gaya.grass) {
				return Palette.st(Blocks.GRASS_BLOCK);
			}
			if (roll < gaya.grass + gaya.moss) {
				return Palette.st(Blocks.MOSS_BLOCK);
			}
			return Palette.st(gaya == Gaya.BATU ? Blocks.STONE : roll > 0.97D ? Blocks.PODZOL : Blocks.COARSE_DIRT);
		}
		if (y == t - 1) {
			return Palette.st(gaya == Gaya.BATU ? Blocks.ANDESITE : Blocks.DIRT);
		}
		if (y <= b + KULIT - 1) {
			// The belly shell: darker with depth.
			int depth = Math.max(1, p.level - 1 - b);
			double q = (double) (p.level - 1 - y) / depth;
			double roll = shape.roll(x, y, z, 2);
			if (q < 0.15D) {
				return Palette.st(roll < 0.5D ? Blocks.DIRT : AwanPalette.AKAR_TANAH);
			}
			if (q < 0.55D) {
				return Palette.st(roll < 0.6D ? Blocks.STONE : Blocks.ANDESITE);
			}
			if (q < 0.85D) {
				return Palette.st(roll < 0.8D ? Blocks.TUFF : Blocks.CALCITE);
			}
			return Palette.st(roll < 0.8D ? Blocks.DEEPSLATE : Blocks.TUFF);
		}
		// A cliff face: strata bands, a calcite band every fifth layer.
		Block band = AwanPalette.TEBING[Math.floorMod(y + shape.band, AwanPalette.TEBING.length)];
		if (band == Blocks.STONE && shape.roll(x, y, z, 3) < 0.25D) {
			band = Blocks.ANDESITE;
		}
		return Palette.st(band);
	}

	/** The turtle head under the front of the belly (faces +z), at about 0.6 of the depth. */
	private static void turtleHead(AwanLayout layout, AwanLayout.Pulau p, Sink sink) {
		int y0 = p.level - 1 - (int) Math.round(p.depth * 0.6D);
		// The belly's front face at that height along the axis.
		int front = p.cz;
		for (int z = p.cz; z <= p.cz + (int) Math.ceil(p.radius() * 1.2D); z++) {
			if (exists(layout, p, 0, z, 3) && bottomOf(p, 0, z, levelOf(layout, p, 0, z) - 1) <= y0 - 3) {
				front = z;
			}
		}
		BlockState shell = Palette.st(Blocks.MOSSY_COBBLESTONE);
		BlockState skin = Palette.st(Blocks.MOSS_BLOCK);
		// Neck and head: 5 wide, 4 high, 7 long, tapering.
		for (int dz = -1; dz <= 6; dz++) {
			int half = dz < 4 ? 2 : 1;
			int high = dz < 5 ? 3 : 2;
			for (int dx = -half; dx <= half; dx++) {
				for (int dy = 0; dy <= high; dy++) {
					boolean edge = Math.abs(dx) == half || dy == 0 || dy == high;
					sink.set(dx, y0 + dy, front + dz, edge ? shell : skin);
				}
			}
		}
		// Eyes (hidden lights) and the beak.
		sink.set(-2, y0 + 2, front + 3, Palette.st(Palette.HIDDEN_LIGHT));
		sink.set(2, y0 + 2, front + 3, Palette.st(Palette.HIDDEN_LIGHT));
		sink.set(0, y0 + 1, front + 7, Palette.stair(Blocks.MOSSY_COBBLESTONE_STAIRS, Direction.NORTH, false));
		sink.set(-1, y0 + 1, front + 7, Palette.slab(Blocks.MOSSY_COBBLESTONE_SLAB, true));
		sink.set(1, y0 + 1, front + 7, Palette.slab(Blocks.MOSSY_COBBLESTONE_SLAB, true));
	}
}
