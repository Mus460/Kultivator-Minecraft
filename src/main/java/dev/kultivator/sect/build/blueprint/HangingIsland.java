package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A hanging mountain island (DESIGN 21.8 "Sky elements", 21.9 {@code HangingIsland}): an irregular top of radius r at
 * standing level {@code level} with grass and moss, a pearlescent froglight under the turf (the light rule), irregular
 * drips 1.5-2.5 × r deep of tuff, andesite, stone and calcite with hanging roots, pines leaning out from the rim, an
 * optional pad for a ting ({@link #pad}), and only <b>vertical</b> chains ({@link #tether}: iron or jade copper) down to
 * polished-blackstone bollards on the ground below. {@link #turtle} carves a turtle head under the island (the largest one).
 * Mist instead of water: no water ever flows off an island. At most 2 spore blossoms per sect ({@link #sporeBlossom}).
 * <p>
 * Source: <i>Liezi</i>, "Tang Wen": the drifting mountains held on the heads of giant turtles (the turtle head nods to it).
 * The chains are the user's request and art direction, not canon (DESIGN 21.3).
 */
public final class HangingIsland implements Part {

	private final int x;
	private final int z;
	private final int r;
	private final Lv level;
	private final List<int[]> tethers = new ArrayList<>();
	private boolean jade;
	private boolean turtle;
	private boolean pad;
	private boolean spore;

	public HangingIsland(int x, int z, int r, Lv level) {
		this.x = x;
		this.z = z;
		this.r = Math.max(3, r);
		this.level = level;
	}

	/** A vertical chain from the island's underside at (tx, tz) down to a bollard standing on level {@code ground}. */
	public HangingIsland tether(int tx, int tz, Lv ground) {
		tethers.add(new int[]{tx, tz, 0});
		groundLevels.add(ground);
		return this;
	}

	private final List<Lv> groundLevels = new ArrayList<>();

	public HangingIsland jadeChains(boolean jade) {
		this.jade = jade;
		return this;
	}

	public HangingIsland turtle(boolean turtle) {
		this.turtle = turtle;
		return this;
	}

	/** A flat 5×5 pad in the middle for a ting. */
	public HangingIsland pad(boolean pad) {
		this.pad = pad;
		return this;
	}

	public HangingIsland sporeBlossom(boolean spore) {
		this.spore = spore;
		return this;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		int low = y - (int) Math.ceil(2.5D * r);
		for (Lv ground : groundLevels) {
			low = Math.min(low, ctx.level(ground) - 1);
		}
		return new BoundingBox(x - r - 1, low, z - r - 1, x + r + 1, y + 10, z + r + 1);
	}

	@Override
	public String kind() {
		return "island";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		Random random = ctx.random("island" + x + "," + z);
		int top = ctx.level(level) - 1;
		Block[] drip = {Blocks.TUFF, Blocks.ANDESITE, Blocks.STONE, Blocks.CALCITE};
		sink.phase(Phase.FILL);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz) + random.nextDouble() * 0.8D;
				if (d > r + 0.3D) {
					continue;
				}
				double t = 1.0D - d / (r + 1.0D);
				int depth = 1 + (int) Math.round(t * t * (1.5D + random.nextDouble()) * r);
				for (int k = 0; k <= depth; k++) {
					int y = top - k;
					sink.set(x + dx, y, z + dz, Palette.st(k == 0 ? (random.nextInt(3) == 0 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK)
							: k == 1 ? Blocks.DIRT : drip[random.nextInt(drip.length)]));
				}
				if (random.nextInt(4) == 0) {
					sink.setIfAbsent(x + dx, top - depth - 1, z + dz, Palette.st(Blocks.HANGING_ROOTS));
				}
			}
		}
		// The light rule: a froglight under the turf in the middle.
		sink.set(x, top - 1, z, Palette.st(Palette.RIDGE_LIGHT));
		if (pad) {
			sink.phase(Phase.PLINTH);
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					sink.set(x + dx, top, z + dz, Palette.st(Palette.PLINTH));
				}
			}
		}
		// Pines leaning out from the rim.
		int pines = 1 + r / 3;
		for (int i = 0; i < pines; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			int px = x + (int) Math.round(Math.cos(angle) * (r - 1));
			int pz = z + (int) Math.round(Math.sin(angle) * (r - 1));
			new Tree(Tree.Kind.PINE, px, pz, Lv.at(top + 1)).force(true).emit(ctx, sink);
		}
		if (spore) {
			sink.phase(Phase.GARDEN);
			sink.setIfAbsent(x + 1, top - 2 - r, z, Palette.st(Blocks.SPORE_BLOSSOM));
		}
		// Vertical chains to bollards.
		sink.phase(Phase.ORNAMENT);
		for (int i = 0; i < tethers.size(); i++) {
			int[] tether = tethers.get(i);
			int groundY = ctx.level(groundLevels.get(i));
			int under = top - 2;
			while (under > groundY && sink.solid(tether[0], under, tether[1])) {
				under--;
			}
			for (int y = groundY + 1; y <= under; y++) {
				sink.set(tether[0], y, tether[1], jade ? Palette.jadeChain() : Palette.ironChain());
			}
			sink.set(tether[0], groundY, tether[1], Palette.st(Blocks.POLISHED_BLACKSTONE));
			sink.set(tether[0], groundY - 1, tether[1], Palette.st(Blocks.POLISHED_BLACKSTONE));
		}
		if (turtle) {
			// A carved turtle head under the island, facing south.
			int hy = top - (int) Math.round(1.5D * r);
			sink.phase(Phase.FILL);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = 0; dy <= 1; dy++) {
					for (int dz = r - 2; dz <= r; dz++) {
						sink.set(x + dx, hy + dy, z + dz, Palette.st(Blocks.MOSSY_COBBLESTONE));
					}
				}
			}
			sink.set(x - 1, hy + 1, z + r + 1, Palette.st(Blocks.POLISHED_BLACKSTONE));
			sink.set(x + 1, hy + 1, z + r + 1, Palette.st(Blocks.POLISHED_BLACKSTONE));
			sink.set(x, hy, z + r + 1, Palette.stair(Blocks.MOSSY_COBBLESTONE_STAIRS, Direction.NORTH, true));
		}
	}
}
