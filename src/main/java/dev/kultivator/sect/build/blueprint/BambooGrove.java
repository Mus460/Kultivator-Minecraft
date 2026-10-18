package dev.kultivator.sect.build.blueprint;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Random;

/**
 * A bamboo grove or screen (DESIGN 21.8 tree table, 21.9 {@code BambooGrove}): clumps of 5-12 stalks in 3×3 to 5×5
 * clusters, heights 6-12, STAGE=1 with large leaves on the top 2 and small leaves under them (DESIGN 21.10), on podzol or
 * moss. Area-based, template-local; {@code level} null = on the natural ground.
 */
public final class BambooGrove implements Part {

	private final int x0;
	private final int z0;
	private final int x1;
	private final int z1;
	private final Lv level;
	private int maxHeight = 10;

	public BambooGrove(int x0, int z0, int x1, int z1, Lv level) {
		this.x0 = Math.min(x0, x1);
		this.z0 = Math.min(z0, z1);
		this.x1 = Math.max(x0, x1);
		this.z1 = Math.max(z0, z1);
		this.level = level;
	}

	/** The tallest stalks (6-12; a screen inside a court wants 6-7). */
	public BambooGrove maxHeight(int height) {
		this.maxHeight = Math.max(6, Math.min(12, height));
		return this;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = level == null ? ctx.terrain().ground(x0, z0) + 1 : ctx.level(level);
		return new BoundingBox(x0, y - 1, z0, x1, y + maxHeight, z1);
	}

	@Override
	public String kind() {
		return "bamboo";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		Random random = ctx.random("bamboo" + x0 + "," + z0);
		Phase previous = sink.phase();
		sink.phase(Phase.GARDEN);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int y = level == null ? ctx.terrain().ground(x, z) + 1 : ctx.level(level);
				sink.set(x, y - 1, z, Palette.st(random.nextInt(3) == 0 ? Blocks.MOSS_BLOCK : Blocks.PODZOL));
				if (random.nextInt(100) < 45) {
					stalk(sink, random, x, y, z, 6 + random.nextInt(Math.max(1, maxHeight - 5)));
				}
			}
		}
		sink.phase(previous);
	}

	/** A small clump of radius {@code r} around (x, y, z) (used by {@link Tree.Kind#BAMBOO}). */
	public static void clump(Sink sink, Random random, int x, int y, int z, int r) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r + 1) {
					continue;
				}
				sink.set(x + dx, y - 1, z + dz, Palette.st(Blocks.PODZOL));
				if (random.nextInt(100) < 55 || dx == 0 && dz == 0) {
					stalk(sink, random, x + dx, y, z + dz, 6 + random.nextInt(6));
				}
			}
		}
	}

	private static void stalk(Sink sink, Random random, int x, int y, int z, int height) {
		for (int i = 0; i < height; i++) {
			boolean big = i >= height - 2;
			boolean small = i == height - 3;
			sink.set(x, y + i, z, Palette.bamboo(big, small));
		}
	}
}
