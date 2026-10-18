package dev.kultivator.sect.build.blueprint.templates.t7t9;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A glazed screen wall (琉璃影壁, DESIGN 21.3 template 8 T6 "7-roof paifang with a glazed screen wall"): a free-standing
 * wall across x, one cell thick, {@code 2·half+1} long, on a stepped stone plinth (须弥座); a cinnabar-brick frame around a
 * field of yellow glazed cloud panels with a cyan glazed medallion in the middle and cyan corner pieces, a teal beam band
 * with cyan beam ends and a small tile roof (jade trim, drips on both sides, a ridge with upturned ends). Template-local,
 * centred on (x, z). Clouds are carved only as yellow glazed panels on gates and screens (DESIGN 21.5). Art direction from
 * the Nine-Dragon Walls of Beijing and Datong, not novel canon.
 */
public final class GlazedScreen implements Part {

	private static final int BODY = 5;

	private final int x;
	private final int z;
	private final Lv level;
	private final int half;

	public GlazedScreen(int x, int z, Lv level, int half) {
		this.x = x;
		this.z = z;
		this.level = level;
		this.half = Math.max(3, half);
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		return new BoundingBox(x - half - 2, y - 2, z - 2, x + half + 2, y + BODY + 4, z + 2);
	}

	@Override
	public String kind() {
		return "screen_wall";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int y0 = ctx.level(level);
		sink.push(x, y0, z, 0);
		Phase previous = sink.phase();
		// The stepped plinth: a wide stone foot, a chiseled waist and a polished cap course.
		sink.phase(Phase.PLINTH);
		for (int dx = -half - 1; dx <= half + 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int ground = sink.ground(ctx, dx, dz);
				for (int fy = Math.max(ground + 1, -5); fy < 0; fy++) {
					sink.set(dx, fy, dz, Palette.st(Palette.STONE));
				}
				boolean outer = Math.abs(dx) == half + 1 || dz != 0;
				sink.set(dx, 0, dz, outer ? Palette.slab(Blocks.SMOOTH_STONE_SLAB, false) : Palette.st(Blocks.CHISELED_STONE_BRICKS));
			}
		}
		// The body: a cinnabar-brick frame around the glazed field.
		sink.phase(Phase.WALL);
		for (int dx = -half; dx <= half; dx++) {
			for (int fy = 1; fy <= BODY; fy++) {
				boolean frame = Math.abs(dx) == half || fy == 1 || fy == BODY;
				boolean medallion = Math.abs(dx) <= 1 && fy >= 2 && fy <= BODY - 1;
				boolean corner = (Math.abs(dx) == half - 1) && (fy == 2 || fy == BODY - 1);
				sink.set(dx, fy, 0, Palette.st(frame ? Blocks.CINNABAR_BRICKS
						: medallion || corner ? Palette.glazed(DyeColor.CYAN) : Palette.glazed(DyeColor.YELLOW)));
			}
		}
		// Beam band with cyan beam ends.
		sink.phase(Phase.FRAME);
		for (int dx = -half; dx <= half; dx++) {
			sink.set(dx, BODY + 1, 0, Palette.st(Math.abs(dx) == half ? Palette.glazed(DyeColor.CYAN) : Palette.BEAM));
		}
		// The small roof: jade trim drips both sides, a tile ridge, upturned ends.
		sink.phase(Phase.ROOF);
		for (int dx = -half - 1; dx <= half + 1; dx++) {
			boolean end = Math.abs(dx) == half + 1;
			sink.set(dx, BODY + 2, -1, Palette.slab(Palette.jadeSlab(), end));
			sink.set(dx, BODY + 2, 1, Palette.slab(Palette.jadeSlab(), end));
			sink.set(dx, BODY + 2, 0, Palette.st(Blocks.DEEPSLATE_TILES));
			if (!end) {
				sink.set(dx, BODY + 3, 0, Palette.slab(Blocks.DEEPSLATE_TILE_SLAB, false));
			}
		}
		// Chiwen hooks: an outward trim stair on the last ridge block.
		sink.set(-half - 1, BODY + 3, 0, Palette.stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.WEST, false));
		sink.set(half + 1, BODY + 3, 0, Palette.stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.EAST, false));
		sink.phase(previous);
		sink.pop();
	}
}
