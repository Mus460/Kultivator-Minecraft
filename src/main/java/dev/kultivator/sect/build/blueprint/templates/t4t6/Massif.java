package dev.kultivator.sect.build.blueprint.templates.t4t6;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import dev.kultivator.sect.build.blueprint.TerrainView;
import dev.kultivator.sect.build.blueprint.Tree;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Random;

/**
 * A sculpted mountain with a level summit (templates 4-6, DESIGN 21.8 rule 5): the library {@code Peak} rises to a point,
 * so a building pad cut into it leaves a walled stone block on a low mound. A massif instead carries a level plateau over
 * its pad rectangle (people stand at {@code top}) and falls away on each side over that side's own {@link #spread}: 0 is a
 * sheer cliff (a gorge wall), 1-3 a craggy face with ledges, 6 or more a wooded slope. The fall is a smoothstep with
 * seeded noise; the body is plain stone inside, and every exposed face shows striped strata (stone, andesite, tuff, a pale
 * calcite band) weathered with mossy cobblestone, under a grass skin on gentle ground and bare rock with moss on steep
 * ground, with pines on the gentle ledges and ferns and grass on the rest. Natural ground higher than the design is kept.
 * <p>
 * Pad cells get no skin: add the pad part ({@code TerrainZone} with edge NONE, paving, a terrace) <b>before</b> the
 * massif in the same component, so the massif replaces the pad's earth fill with rock (no dirt band on a cliff top) but
 * leaves its floor alone. Spreads must stay inside the footprint (the template's job). Template-local.
 */
public final class Massif implements Part {

	private static final Block[] STRATA = {Blocks.STONE, Blocks.ANDESITE, Blocks.TUFF, Blocks.STONE, Blocks.CALCITE, Blocks.ANDESITE, Blocks.STONE,
			Blocks.TUFF};

	private final int x0;
	private final int z0;
	private final int x1;
	private final int z1;
	private final Lv top;
	private final Lv base;
	private int west = 4;
	private int east = 4;
	private int north = 4;
	private int south = 4;
	private int pines = 4;

	/** The plateau rectangle (inclusive) whose people stand at {@code top}; the slopes end at {@code base}. */
	public Massif(int x0, int z0, int x1, int z1, Lv top, Lv base) {
		this.x0 = Math.min(x0, x1);
		this.z0 = Math.min(z0, z1);
		this.x1 = Math.max(x0, x1);
		this.z1 = Math.max(z0, z1);
		this.top = top;
		this.base = base;
	}

	/** Cells the slope runs beyond each pad edge (0 = a sheer cliff on that side). */
	public Massif spread(int west, int east, int north, int south) {
		this.west = Math.max(0, west);
		this.east = Math.max(0, east);
		this.north = Math.max(0, north);
		this.south = Math.max(0, south);
		return this;
	}

	/** Pines on the gentle ledges (default 4). */
	public Massif pines(int count) {
		this.pines = Math.max(0, count);
		return this;
	}

	private boolean inPad(int px, int pz) {
		return px >= x0 && px <= x1 && pz >= z0 && pz <= z1;
	}

	private boolean inExtent(int px, int pz) {
		return px >= x0 - west && px <= x1 + east && pz >= z0 - north && pz <= z1 + south;
	}

	/** The design standing level of column (px, pz): {@code top} on the pad, falling to {@code base} at the spread. */
	public int height(Ctx ctx, int px, int pz) {
		int t = ctx.level(top);
		int b = ctx.level(base);
		int dx = px < x0 ? x0 - px : px > x1 ? px - x1 : 0;
		int dz = pz < z0 ? z0 - pz : pz > z1 ? pz - z1 : 0;
		if (dx == 0 && dz == 0) {
			return t;
		}
		int sx = px < x0 ? west : east;
		int sz = pz < z0 ? north : south;
		if (dx > sx || dz > sz) {
			return b;
		}
		double nx = dx / (sx + 1.0D);
		double nz = dz / (sz + 1.0D);
		double d = Math.sqrt(nx * nx + nz * nz);
		if (d >= 1.0D) {
			return b;
		}
		double fall = 1.0D - d * d * (3.0D - 2.0D * d);
		Random random = new Random(ctx.seed() ^ (px * 341873128712L + pz * 132897987541L) ^ 0x2545F4914F6CDD1DL);
		double noise = (random.nextDouble() - 0.5D) * 4.8D * d * (1.0D - d);
		int h = b + (int) Math.round((t - b) * fall + noise);
		return Math.max(b, Math.min(t, h));
	}

	/** The planned or natural standing level of a column (natural ground higher than the design wins). */
	private int surface(Ctx ctx, int px, int pz) {
		TerrainView terrain = ctx.terrain();
		int natural = terrain.ground(px, pz) + 1;
		int planned = inExtent(px, pz) ? height(ctx, px, pz) : ctx.level(base);
		return Math.max(natural, planned);
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		return new BoundingBox(x0 - west, ctx.level(base) - 2, z0 - north, x1 + east, ctx.level(top) + 12, z1 + south);
	}

	@Override
	public String kind() {
		return "peak";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		TerrainView terrain = ctx.terrain();
		Random random = ctx.random("massif" + x0 + "," + z0);
		int planted = 0;
		for (int px = x0 - west; px <= x1 + east; px++) {
			for (int pz = z0 - north; pz <= z1 + south; pz++) {
				int h = height(ctx, px, pz);
				int ground = terrain.ground(px, pz);
				if (ground >= h - 1) {
					// Higher natural terrain is kept.
					continue;
				}
				boolean pad = inPad(px, pz);
				// Plants and trees above the new surface (the pad part clears its own cells and has already placed above them).
				if (!pad) {
					sink.phase(Phase.CLEAR);
					for (int y = Math.max(ground + 1, h); y <= Math.min(terrain.top(px, pz), h + 8); y++) {
						sink.air(px, y, pz);
					}
				}
				// The neighbours' surfaces decide which cells of this column show a face.
				int lowest = h;
				int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
				for (int[] n : around) {
					lowest = Math.min(lowest, surface(ctx, px + n[0], pz + n[1]));
				}
				sink.phase(Phase.FILL);
				for (int y = Math.max(ground + 1, h - 24); y <= h - 2; y++) {
					Block block;
					if (y >= lowest) {
						block = random.nextInt(7) == 0 ? Blocks.MOSSY_COBBLESTONE : STRATA[Math.floorMod(y / 2, STRATA.length)];
					} else {
						block = Palette.FILL_STONE;
					}
					sink.set(px, y, pz, Palette.st(block));
				}
				if (pad) {
					continue;
				}
				boolean steep = lowest < h - 1;
				Block skin = steep ? (random.nextInt(3) == 0 ? Blocks.MOSS_BLOCK : random.nextBoolean() ? Blocks.ANDESITE : Blocks.STONE)
						: random.nextInt(5) == 0 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK;
				sink.set(px, h - 1, pz, Palette.st(skin));
				if (steep) {
					continue;
				}
				if (planted < pines && random.nextInt(26) == 0) {
					new Tree(Tree.Kind.PINE, px, pz, Lv.at(h)).force(true).emit(ctx, sink);
					planted++;
				} else if (random.nextInt(6) == 0) {
					sink.phase(Phase.GARDEN);
					sink.setIfAbsent(px, h, pz, Palette.st(random.nextInt(3) == 0 ? Blocks.SHORT_GRASS : Blocks.FERN));
				}
			}
		}
	}
}
