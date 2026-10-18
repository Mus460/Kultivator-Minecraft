package dev.kultivator.sect.build.blueprint;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Random;

/**
 * A sculpted mountain (DESIGN 21.8 rule 5, 21.9 {@code Peak}) for the big templates: a radial falloff plus seeded noise
 * from {@code base} at radius {@code r} up to {@code top} in the middle, cliff faces striped in stone, andesite, tuff and
 * calcite, a grass and moss skin, pines on the ledges. Natural terrain higher than the design is kept (only pads and stairs
 * are carved, by their own parts), so a peak never flattens a real hill. Template-local, centred on (x, z); building pads
 * are {@link TerrainZone}s or {@link Terrace}s added after the peak in the same component.
 */
public final class Peak implements Part {

	private static final Block[] STRATA = {Blocks.STONE, Blocks.ANDESITE, Blocks.TUFF, Blocks.STONE, Blocks.CALCITE, Blocks.ANDESITE};

	private final int x;
	private final int z;
	private final int r;
	private final Lv base;
	private final Lv top;
	private int pines = 6;

	public Peak(int x, int z, int r, Lv base, Lv top) {
		this.x = x;
		this.z = z;
		this.r = Math.max(4, r);
		this.base = base;
		this.top = top;
	}

	/** Pines on ledges (default 6). */
	public Peak pines(int count) {
		this.pines = Math.max(0, count);
		return this;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		return new BoundingBox(x - r, ctx.level(base) - 2, z - r, x + r, ctx.level(top) + 1, z + r);
	}

	@Override
	public String kind() {
		return "peak";
	}

	/** The design surface height (standing level) of column (px, pz). */
	public int height(Ctx ctx, int px, int pz) {
		int b = ctx.level(base);
		int t = ctx.level(top);
		double d = Math.sqrt((px - x) * (px - x) + (pz - z) * (pz - z)) / r;
		if (d >= 1.0D) {
			return b;
		}
		double falloff = 1.0D - d * d * (3.0D - 2.0D * d);
		Random random = new Random(ctx.seed() ^ (px * 341873128712L + pz * 132897987541L));
		double noise = (random.nextDouble() - 0.5D) * 1.6D;
		return b + (int) Math.round((t - b) * falloff + noise * (1.0D - d));
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		Random random = ctx.random("peak" + x + "," + z);
		int planted = 0;
		for (int px = x - r; px <= x + r; px++) {
			for (int pz = z - r; pz <= z + r; pz++) {
				int h = height(ctx, px, pz);
				int ground = ctx.terrain().ground(px, pz);
				if (ground >= h - 1) {
					// Higher natural terrain is kept.
					continue;
				}
				sink.phase(Phase.CLEAR);
				for (int y = ground + 1; y <= Math.min(ctx.terrain().top(px, pz), h + 8); y++) {
					sink.air(px, y, pz);
				}
				sink.phase(Phase.FILL);
				for (int y = Math.max(ground + 1, h - 24); y <= h - 2; y++) {
					sink.set(px, y, pz, Palette.st(STRATA[Math.floorMod(y / 2, STRATA.length)]));
				}
				boolean cliff = false;
				for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					if (height(ctx, px + n[0], pz + n[1]) < h - 1) {
						cliff = true;
					}
				}
				sink.set(px, h - 1, pz, Palette.st(cliff ? (random.nextBoolean() ? Blocks.MOSS_BLOCK : Blocks.ANDESITE) : Blocks.GRASS_BLOCK));
				if (!cliff && planted < pines && random.nextInt(40) == 0) {
					new Tree(Tree.Kind.PINE, px, pz, Lv.at(h)).force(true).emit(ctx, sink);
					planted++;
				} else if (!cliff && random.nextInt(9) == 0) {
					sink.phase(Phase.GARDEN);
					sink.setIfAbsent(px, h, pz, Palette.st(random.nextBoolean() ? Blocks.FERN : Blocks.SHORT_GRASS));
				}
			}
		}
	}
}
