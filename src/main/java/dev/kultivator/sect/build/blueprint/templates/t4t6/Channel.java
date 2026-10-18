package dev.kultivator.sect.build.blueprint.templates.t4t6;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Pond;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A sealed stream channel that can flow into a lake of the same component (templates 4-6, DESIGN 21.8 "Water", 21.10
 * "Fluids"): the same section as the library's {@code Pond.stream} (water 1 deep at path level - 2 over a clay bed, rock
 * banks with ferns and azaleas), drawn along axis-aligned legs, but it never lays its bank over the water of a
 * {@link #join joined} pond and it opens that pond's rim where it crosses it, so stream and lake form one body of water.
 * Add it <b>after</b> the joined {@code Pond} in the same component. Every water cell still ends in a planned solid on
 * every side (containment holds). Template-local.
 */
public final class Channel implements Part {

	private final Lv level;
	private final int width;
	private final int[][] path;
	private final List<Pond> joins = new ArrayList<>();

	/** A channel {@code width} wide (1-3) through the points (axis-aligned legs). */
	public Channel(Lv level, int width, int... xzPairs) {
		this.level = level;
		this.width = Math.max(1, Math.min(3, width));
		int n = xzPairs.length / 2;
		this.path = new int[n][2];
		for (int i = 0; i < n; i++) {
			path[i][0] = xzPairs[2 * i];
			path[i][1] = xzPairs[2 * i + 1];
		}
	}

	/** A pond whose water this channel flows into. */
	public Channel join(Pond pond) {
		joins.add(pond);
		return this;
	}

	private boolean water(int x, int z) {
		int half = (width - 1) / 2;
		int extra = width % 2 == 0 ? 1 : 0;
		for (int i = 0; i + 1 < path.length; i++) {
			int ax = Math.min(path[i][0], path[i + 1][0]);
			int bx = Math.max(path[i][0], path[i + 1][0]);
			int az = Math.min(path[i][1], path[i + 1][1]);
			int bz = Math.max(path[i][1], path[i + 1][1]);
			if (x >= ax - half && x <= bx + half + extra && z >= az - half && z <= bz + half + extra) {
				return true;
			}
		}
		return false;
	}

	private boolean joined(int x, int z) {
		for (Pond pond : joins) {
			if (pond.isWater(x, z)) {
				return true;
			}
		}
		return false;
	}

	private int[] extent() {
		int ax = Integer.MAX_VALUE;
		int az = Integer.MAX_VALUE;
		int bx = Integer.MIN_VALUE;
		int bz = Integer.MIN_VALUE;
		for (int[] point : path) {
			ax = Math.min(ax, point[0]);
			az = Math.min(az, point[1]);
			bx = Math.max(bx, point[0]);
			bz = Math.max(bz, point[1]);
		}
		return new int[]{ax - width - 1, az - width - 1, bx + width + 1, bz + width + 1};
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		int[] e = extent();
		return new BoundingBox(e[0], y - 4, e[1], e[2], y + 1, e[3]);
	}

	@Override
	public String kind() {
		return "stream";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int y = ctx.level(level);
		int surface = y - 2;
		int floor = surface - 1;
		Random random = ctx.random("channel" + path[0][0] + "," + path[0][1]);
		Block[] rocks = {Blocks.CALCITE, Blocks.TUFF, Blocks.ANDESITE, Blocks.MOSSY_COBBLESTONE};
		int[] e = extent();
		for (int x = e[0]; x <= e[2]; x++) {
			for (int z = e[1]; z <= e[3]; z++) {
				boolean wet = water(x, z);
				if (joined(x, z)) {
					continue;
				}
				boolean bank = !wet && (water(x + 1, z) || water(x - 1, z) || water(x, z + 1) || water(x, z - 1) || water(x + 1, z + 1)
						|| water(x - 1, z - 1) || water(x + 1, z - 1) || water(x - 1, z + 1));
				if (!wet && !bank) {
					continue;
				}
				int ground = ctx.terrain().ground(x, z);
				sink.phase(Phase.CLEAR);
				for (int cy = Math.max(ground + 1, y); cy <= Math.min(ctx.terrain().top(x, z), y + 6); cy++) {
					sink.air(x, cy, z);
				}
				sink.phase(Phase.CUT);
				for (int cy = y; cy <= Math.min(ground, y + 4); cy++) {
					sink.air(x, cy, z);
				}
				if (wet) {
					for (int cy = Math.min(ground, y - 1); cy > floor; cy--) {
						sink.air(x, cy, z);
					}
					sink.phase(Phase.PLINTH);
					sink.air(x, y - 1, z);
					sink.air(x, y, z);
					sink.phase(Phase.SHELL);
					sink.set(x, floor, z, Palette.st(Blocks.CLAY));
					sink.set(x, floor - 1, z, Palette.st(Palette.FILL_STONE));
					for (int cy = Math.max(ground + 1, floor - 6); cy < floor - 1; cy++) {
						sink.set(x, cy, z, Palette.st(Palette.FILL_STONE));
					}
				} else {
					sink.phase(Phase.SHELL);
					for (int cy = Math.max(ground + 1, floor - 5); cy <= y - 2; cy++) {
						sink.set(x, cy, z, Palette.st(random.nextInt(4) == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS));
					}
					sink.phase(Phase.PLINTH);
					sink.set(x, y - 1, z, Palette.st(random.nextInt(3) == 0 ? Palette.MOSS : Blocks.GRASS_BLOCK));
					sink.phase(Phase.GARDEN);
					int roll = random.nextInt(9);
					if (roll < 2) {
						sink.set(x, y, z, Palette.st(rocks[random.nextInt(rocks.length)]));
					} else if (roll == 2) {
						sink.set(x, y, z, Palette.st(Blocks.FLOWERING_AZALEA));
					} else if (roll == 3) {
						sink.set(x, y, z, Palette.st(Blocks.FERN));
					}
				}
			}
		}
		for (int x = e[0]; x <= e[2]; x++) {
			for (int z = e[1]; z <= e[3]; z++) {
				if (!water(x, z) || joined(x, z)) {
					continue;
				}
				sink.phase(Phase.GARDEN);
				if (random.nextInt(9) == 0) {
					sink.set(x, floor + 1, z, Palette.st(Blocks.SEAGRASS));
					continue;
				}
				sink.phase(Phase.WATER);
				sink.set(x, surface, z, Palette.st(Blocks.WATER));
			}
		}
	}
}
