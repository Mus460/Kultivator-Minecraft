package dev.kultivator.sect.build.blueprint;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Random;

/**
 * Rocks of the gardens (DESIGN 21.8 "Rocks and paths", 21.9 {@code Rock}, {@code RockHill}): scholar's rocks are vertical
 * stacks of 2-4 with lateral offsets and random voids (thin, perforated, open, wrinkled), of calcite, diorite, tuff,
 * andesite and mossy cobblestone; a {@link #hill} is a rockery mound of radius r with taihu holes, ferns and azaleas and an
 * optional pavilion pad on top ({@link #topY}); a {@link #cluster} rings a pond edge or a terrace foot. Template-local;
 * {@code level} null = on the natural ground.
 */
public final class Rock implements Part {

	private static final Block[] STONES = {Blocks.CALCITE, Blocks.DIORITE, Blocks.TUFF, Blocks.ANDESITE, Blocks.MOSSY_COBBLESTONE};

	private final int x;
	private final int z;
	private final Lv level;
	private final int size;
	private final int mode;

	private Rock(int x, int z, Lv level, int size, int mode) {
		this.x = x;
		this.z = z;
		this.level = level;
		this.size = size;
		this.mode = mode;
	}

	/** A scholar's rock 2-4 high. */
	public static Rock scholar(int x, int z, Lv level, int height) {
		return new Rock(x, z, level, Math.max(2, Math.min(4, height)), 0);
	}

	/** A low cluster of 3-6 rocks within radius {@code r} (pond banks, terrace feet, bridge heads). */
	public static Rock cluster(int x, int z, Lv level, int r) {
		return new Rock(x, z, level, Math.max(1, r), 1);
	}

	/** A rockery hill of radius {@code r} (about r/2 + 2 high) with holes, ferns and azaleas. */
	public static Rock hill(int x, int z, Lv level, int r) {
		return new Rock(x, z, level, Math.max(3, r), 2);
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = baseY(ctx, x, z);
		int r = mode == 0 ? 1 : size;
		int h = mode == 0 ? size : mode == 1 ? 2 : size / 2 + 3;
		return new BoundingBox(x - r, y - 1, z - r, x + r, y + h, z + r);
	}

	@Override
	public String kind() {
		return mode == 2 ? "rock_hill" : "rock";
	}

	private int baseY(Ctx ctx, int px, int pz) {
		return level != null ? ctx.level(level) : ctx.terrain().ground(px, pz) + 1;
	}

	/** The standing level on top of a hill (for a ting placed there). */
	public int topY(Ctx ctx) {
		return baseY(ctx, x, z) + size / 2 + 2;
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		Random random = ctx.random("rock" + mode + x + "," + z);
		Phase previous = sink.phase();
		sink.phase(Phase.GARDEN);
		switch (mode) {
			case 0 -> scholarRock(sink, random, x, baseY(ctx, x, z), z, size);
			case 1 -> {
				int count = 3 + random.nextInt(4);
				for (int i = 0; i < count; i++) {
					int rx = x + random.nextInt(2 * size + 1) - size;
					int rz = z + random.nextInt(2 * size + 1) - size;
					int y = baseY(ctx, rx, rz);
					sink.set(rx, y, rz, stone(random));
					if (random.nextInt(3) == 0) {
						sink.set(rx, y + 1, rz, random.nextBoolean() ? stone(random) : Palette.st(Blocks.FERN));
					}
				}
			}
			default -> hill(ctx, sink, random);
		}
		sink.phase(previous);
	}

	/** One scholar's rock: a leaning stack with voids. */
	public static void scholarRock(Sink sink, Random random, int x, int y, int z, int height) {
		int ox = 0;
		int oz = 0;
		for (int i = 0; i < height; i++) {
			if (i > 0 && random.nextInt(2) == 0) {
				ox += random.nextInt(3) - 1;
				oz += random.nextInt(3) - 1;
				ox = Math.max(-1, Math.min(1, ox));
				oz = Math.max(-1, Math.min(1, oz));
			}
			sink.set(x + ox, y + i, z + oz, stone(random));
			if (i == 0) {
				sink.set(x + 1, y, z, random.nextInt(3) == 0 ? Palette.st(Blocks.MOSS_CARPET) : stone(random));
			}
		}
	}

	private void hill(Ctx ctx, Sink sink, Random random) {
		int y0 = baseY(ctx, x, z);
		int peak = size / 2 + 2;
		for (int dx = -size; dx <= size; dx++) {
			for (int dz = -size; dz <= size; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > size + 0.4D) {
					continue;
				}
				int h = (int) Math.round(peak * (1.0D - d / (size + 1.0D))) + (random.nextInt(4) == 0 ? 1 : 0);
				for (int dy = 0; dy < h; dy++) {
					boolean surface = dy == h - 1;
					// Taihu holes: some outer cells stay open at the lower layers.
					if (!surface && d > size - 1.5D && dy > 0 && random.nextInt(5) == 0) {
						continue;
					}
					sink.set(x + dx, y0 + dy, z + dz, stone(random));
				}
				if (h > 0 && d > 1.5D && random.nextInt(6) == 0) {
					sink.setIfAbsent(x + dx, y0 + h, z + dz, Palette.st(random.nextBoolean() ? Blocks.FERN : Blocks.FLOWERING_AZALEA));
				}
			}
		}
		// A flat pad on top for a ting.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				sink.set(x + dx, y0 + peak - 1, z + dz, Palette.st(Blocks.POLISHED_ANDESITE));
				sink.air(x + dx, y0 + peak, z + dz);
			}
		}
	}

	private static BlockState stone(Random random) {
		return Palette.st(STONES[random.nextInt(STONES.length)]);
	}
}
