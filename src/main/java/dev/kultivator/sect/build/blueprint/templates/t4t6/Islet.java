package dev.kultivator.sect.build.blueprint.templates.t4t6;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * An island in a pond of the same component (templates 4-6, DESIGN 21.8 "Water"): a solid stone core from the pond floor
 * up, a turf top at the path level (grass with moss), and a ring of taihu rocks, ferns and flowering azalea on its edge,
 * open at the {@link #landing}s where bridges arrive. Add it <b>after</b> the {@code Pond} part in the same component, so
 * it replaces the planned water cells (the pond stays sealed: the island is solid). Template-local, an ellipse inscribed in
 * the rectangle.
 */
public final class Islet implements Part {

	private final int x0;
	private final int z0;
	private final int x1;
	private final int z1;
	private final Lv level;
	private final List<int[]> landings = new ArrayList<>();
	private boolean rocks = true;

	public Islet(int x0, int z0, int x1, int z1, Lv level) {
		this.x0 = Math.min(x0, x1);
		this.z0 = Math.min(z0, z1);
		this.x1 = Math.max(x0, x1);
		this.z1 = Math.max(z0, z1);
		this.level = level;
	}

	/** Keeps the edge clear within 1 of (x, z) (a bridge or stepping stones arrive there). */
	public Islet landing(int x, int z) {
		landings.add(new int[]{x, z});
		return this;
	}

	public Islet rocks(boolean rocks) {
		this.rocks = rocks;
		return this;
	}

	private boolean inside(int x, int z) {
		double cx = (x0 + x1) / 2.0D;
		double cz = (z0 + z1) / 2.0D;
		double rx = (x1 - x0) / 2.0D + 0.5D;
		double rz = (z1 - z0) / 2.0D + 0.5D;
		double ex = (x - cx) / rx;
		double ez = (z - cz) / rz;
		return ex * ex + ez * ez <= 1.0D;
	}

	private boolean nearLanding(int x, int z) {
		for (int[] landing : landings) {
			if (Math.abs(x - landing[0]) <= 1 && Math.abs(z - landing[1]) <= 1) {
				return true;
			}
		}
		return false;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		return new BoundingBox(x0, y - 5, z0, x1, y + 2, z1);
	}

	@Override
	public String kind() {
		return "islet";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int y = ctx.level(level);
		Random random = ctx.random("islet" + x0 + "," + z0);
		Block[] stones = {Blocks.CALCITE, Blocks.TUFF, Blocks.ANDESITE, Blocks.MOSSY_COBBLESTONE, Blocks.DIORITE};
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (!inside(x, z)) {
					continue;
				}
				boolean edge = !inside(x + 1, z) || !inside(x - 1, z) || !inside(x, z + 1) || !inside(x, z - 1);
				sink.phase(Phase.FILL);
				for (int fy = y - 5; fy <= y - 2; fy++) {
					sink.set(x, fy, z, Palette.st(edge ? Blocks.MOSSY_STONE_BRICKS : Palette.FILL_STONE));
				}
				sink.phase(Phase.PLINTH);
				sink.set(x, y - 1, z, Palette.st(random.nextInt(3) == 0 ? Palette.MOSS : Blocks.GRASS_BLOCK));
				if (!edge || !rocks || nearLanding(x, z)) {
					continue;
				}
				sink.phase(Phase.GARDEN);
				int roll = random.nextInt(6);
				if (roll <= 1) {
					sink.set(x, y, z, Palette.st(stones[random.nextInt(stones.length)]));
				} else if (roll == 2) {
					sink.set(x, y, z, Palette.st(Blocks.FLOWERING_AZALEA));
				} else if (roll == 3) {
					sink.set(x, y, z, Palette.st(Blocks.FERN));
				}
			}
		}
	}
}
