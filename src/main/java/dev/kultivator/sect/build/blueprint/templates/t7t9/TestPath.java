package dev.kultivator.sect.build.blueprint.templates.t7t9;

import dev.kultivator.sect.build.blueprint.BambooGrove;
import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Random;

/**
 * The candidates' test path (DESIGN 21.3, templates 7-9; 20.3.3): stepping stones through a bamboo grove, then a rough
 * slope of uneven rubble steps, then a short cliff climbed by a ladder between hanging iron chains, ending on the
 * {@code top} level. Template-local: it starts at (x, z) on {@code base} and runs {@code toward}, {@code bamboo} cells of
 * grove first. Decoration only (the trial itself is phase 6 game logic).
 * <p>
 * Source: <i>A Record of a Mortal's Journey</i> ch. 1-5, the Seven Mysteries climb (DESIGN 21.3); the look is art
 * direction.
 */
public final class TestPath implements Part {

	private static final int CLIFF = 3;

	private final int x;
	private final int z;
	private final Direction toward;
	private final Lv base;
	private final Lv top;
	private final int bamboo;

	public TestPath(int x, int z, Direction toward, Lv base, Lv top, int bamboo) {
		this.x = x;
		this.z = z;
		this.toward = toward.getAxis().isHorizontal() ? toward : Direction.NORTH;
		this.base = base;
		this.top = top;
		this.bamboo = Math.max(4, bamboo);
	}

	/** Cells along the path (grove, rough slope, the ladder cell). */
	public int length(Ctx ctx) {
		int rise = Math.max(CLIFF, ctx.level(top) - ctx.level(base));
		return bamboo + 2 * (rise - CLIFF) + 2;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y0 = ctx.level(base);
		int y1 = Math.max(y0, ctx.level(top));
		return Part.box(x, z, toward, -4, y0 - 2, 0, 4, y1 + 12, length(ctx));
	}

	@Override
	public String kind() {
		return "test_path";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int y0 = ctx.level(base);
		int y1 = ctx.level(top);
		int rise = Math.max(CLIFF, y1 - y0);
		Random random = ctx.random("testpath" + x + "," + z);
		sink.pushFacing(x, y0, z, toward);
		Phase previous = sink.phase();
		// 1. Stepping stones on moss through the bamboo.
		sink.phase(Phase.PLINTH);
		for (int i = 0; i < bamboo; i++) {
			for (int w = -1; w <= 1; w++) {
				sink.set(w, -1, i, Palette.st(Palette.MOSS));
				sink.air(w, 0, i);
				sink.air(w, 1, i);
			}
			if (i % 2 == 0) {
				sink.set(random.nextInt(3) - 1 == 0 ? 0 : (i % 4 == 0 ? -1 : 1), 0, i, Palette.slab(Palette.STEPPING, false));
			}
		}
		sink.phase(Phase.GARDEN);
		for (int i = 0; i < bamboo; i += 3) {
			for (int side : new int[]{-1, 1}) {
				int bx = side * (3 + random.nextInt(2));
				BambooGrove.clump(sink, random, bx, 0, i, 1);
			}
		}
		// 2. The rough slope: uneven rubble steps, one up per two cells, filled solid under.
		Block[] rubble = {Blocks.MOSSY_COBBLESTONE, Blocks.TUFF, Blocks.ANDESITE, Blocks.COBBLESTONE};
		int cell = bamboo;
		int level = 0;
		for (int s = 0; s < rise - CLIFF; s++) {
			level++;
			for (int k = 0; k < 2; k++) {
				for (int w = -2; w <= 2; w++) {
					sink.phase(Phase.PLINTH);
					for (int fy = Math.max(sink.ground(ctx, w, cell) + 1, level - 10); fy < level - 1; fy++) {
						sink.set(w, fy, cell, Palette.st(Palette.FILL_STONE));
					}
					if (Math.abs(w) == 2) {
						// Rough edges: rubble a little higher than the walk, with a fern or a stone on top now and then.
						sink.set(w, level - 1, cell, Palette.st(rubble[random.nextInt(rubble.length)]));
						sink.phase(Phase.GARDEN);
						if (random.nextInt(3) == 0) {
							sink.setIfAbsent(w, level, cell, Palette.st(random.nextBoolean() ? Blocks.FERN : Blocks.MOSSY_COBBLESTONE));
						}
						continue;
					}
					boolean slab = k == 0;
					sink.set(w, level - 1, cell, slab ? Palette.slab(Blocks.MOSSY_COBBLESTONE_SLAB, true) : Palette.st(rubble[random.nextInt(rubble.length)]));
					sink.air(w, level, cell);
					sink.air(w, level + 1, cell);
				}
				cell++;
			}
		}
		// 3. The cliff with a ladder between hanging iron chains.
		sink.phase(Phase.PLINTH);
		for (int w = -2; w <= 2; w++) {
			for (int fy = Math.max(sink.ground(ctx, w, cell + 1) + 1, level - 10); fy < rise; fy++) {
				sink.set(w, fy, cell + 1, Palette.st(rubble[random.nextInt(rubble.length)]));
			}
			sink.set(w, rise - 1, cell + 1, Palette.st(Palette.MOSS));
		}
		sink.phase(Phase.WALL);
		for (int fy = level; fy < rise; fy++) {
			sink.set(0, fy, cell, Palette.facing(Blocks.LADDER, Direction.NORTH));
			sink.set(-1, fy, cell, Palette.ironChain());
			sink.set(1, fy, cell, Palette.ironChain());
		}
		sink.set(0, rise, cell + 1, Palette.air());
		sink.set(0, rise + 1, cell + 1, Palette.air());
		sink.phase(previous);
		sink.pop();
	}
}
