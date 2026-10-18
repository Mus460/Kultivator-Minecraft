package dev.kultivator.sect.build.blueprint.templates.t4t6;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import dev.kultivator.sect.build.blueprint.Stamp;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Boundary stone pillars (界碑, DESIGN 21.8 rule 4) for templates without a perimeter wall: a carved stele with a tile cap
 * on a small andesite foot, each standing on the <b>natural ground</b> of its own column (the rim of the footprint stays
 * natural woodland, so a fixed level would bury or float them), with a fern or two at the foot. Template-local spots.
 */
public final class BoundaryStones implements Part {

	private static final Stamp STONE = Stamp.of("boundary_stone").layer("P").layer("C").layer("C").layer("T")
			.legend('P', Palette.st(Blocks.POLISHED_ANDESITE))
			.legend('C', Palette.st(Blocks.CHISELED_STONE_BRICKS))
			.legend('T', Palette.slab(Blocks.DEEPSLATE_TILE_SLAB, false));

	private final List<int[]> spots = new ArrayList<>();

	/** Adds a stone at (x, z) whose carved face looks toward {@code face}. */
	public BoundaryStones at(int x, int z, Direction face) {
		spots.add(new int[]{x, z, face.get2DDataValue()});
		return this;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		BoundingBox box = null;
		for (int[] spot : spots) {
			int y = ctx.terrain().ground(spot[0], spot[1]) + 1;
			BoundingBox each = new BoundingBox(spot[0], y - 1, spot[1], spot[0], y + 3, spot[1]);
			box = box == null ? each : BoundingBox.encapsulating(box, each);
		}
		return box == null ? new BoundingBox(0, 0, 0, 0, 0, 0) : box;
	}

	@Override
	public String kind() {
		return "boundary_stones";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		for (int[] spot : spots) {
			int y = ctx.terrain().ground(spot[0], spot[1]) + 1;
			sink.pushFacing(spot[0], y, spot[1], Direction.from2DDataValue(spot[2]));
			sink.phase(Phase.PLINTH);
			sink.set(0, -1, 0, Palette.st(Blocks.POLISHED_ANDESITE));
			STONE.draw(sink, 0, 0, 0);
			sink.phase(Phase.GARDEN);
			sink.setIfAbsent(1, 0, 0, Palette.st(Blocks.FERN));
			sink.setIfAbsent(-1, 0, 1, Palette.st(Blocks.FERN));
			sink.pop();
		}
	}
}
