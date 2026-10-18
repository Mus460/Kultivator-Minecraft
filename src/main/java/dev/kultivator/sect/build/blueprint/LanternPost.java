package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Lantern posts (DESIGN 21.5 "Lights", 21.9 {@code LanternPost}) at one or more template-local spots on a level. Walkable
 * lights stand at most 12 apart (the light rule of 21.10); emitters of light 12 or more stay 4 inside the footprint edge.
 * <ul>
 * <li>{@link Style#STONE}: two andesite walls, a lantern and a deepslate-tile slab hood (stone lanterns of the court).</li>
 * <li>{@link Style#RED_POST}: two mangrove fences, a shroomlight and a cinnabar slab cap (gate posts).</li>
 * <li>{@link Style#HANGING}: a 3-high fence post with a fence arm and a lantern hanging on a chain (paths, bridges).</li>
 * <li>{@link Style#PALACE}: a tall cinnabar post with two stacked hanging shroomlights on chains (axis rows).</li>
 * <li>{@link Style#JADE}: a waxed oxidized copper lantern on a fence post (gardens).</li>
 * <li>{@link Style#PAGODA}: a 4-high stone pagoda-lantern (stair steps with a lantern core) for terrace stairs.</li>
 * </ul>
 */
public final class LanternPost implements Part {

	public enum Style {
		STONE,
		RED_POST,
		HANGING,
		PALACE,
		JADE,
		PAGODA
	}

	private final Style style;
	private final Lv level;
	private final List<int[]> spots = new ArrayList<>();

	public LanternPost(Style style, Lv level) {
		this.style = style;
		this.level = level;
	}

	/** Adds a post at template-local (x, z); the arm of a HANGING post points toward {@code arm}. */
	public LanternPost at(int x, int z) {
		spots.add(new int[]{x, z, Direction.SOUTH.get3DDataValue()});
		return this;
	}

	public LanternPost at(int x, int z, Direction arm) {
		spots.add(new int[]{x, z, arm.get3DDataValue()});
		return this;
	}

	/** Adds a mirrored pair (x, z) and (-x, z). */
	public LanternPost pair(int x, int z) {
		at(x, z);
		if (x != 0) {
			at(-x, z);
		}
		return this;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		BoundingBox box = null;
		for (int[] spot : spots) {
			BoundingBox each = new BoundingBox(spot[0] - 1, y, spot[1] - 1, spot[0] + 1, y + 5, spot[1] + 1);
			box = box == null ? each : BoundingBox.encapsulating(box, each);
		}
		return box == null ? new BoundingBox(0, y, 0, 0, y, 0) : box;
	}

	@Override
	public String kind() {
		return "lantern";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		Phase previous = sink.phase();
		sink.phase(Phase.ORNAMENT);
		int y = ctx.level(level);
		for (int[] spot : spots) {
			emitOne(sink, spot[0], y, spot[1], style, Direction.from3DDataValue(spot[2]));
		}
		sink.phase(previous);
	}

	/** Draws one post in the current frame at frame (x, standing y, z). */
	public static void emitOne(Sink sink, int x, int y, int z, Style style, Direction arm) {
		switch (style) {
			case STONE -> {
				sink.set(x, y, z, Palette.st(Blocks.ANDESITE_WALL));
				sink.set(x, y + 1, z, Palette.st(Blocks.ANDESITE_WALL));
				sink.set(x, y + 2, z, Palette.lantern(Blocks.LANTERN, false));
				sink.set(x, y + 3, z, Palette.slab(Blocks.DEEPSLATE_TILE_SLAB, false));
			}
			case RED_POST -> {
				sink.set(x, y, z, Palette.st(Palette.FENCE));
				sink.set(x, y + 1, z, Palette.st(Palette.FENCE));
				sink.set(x, y + 2, z, Palette.st(Palette.LIGHT));
				sink.set(x, y + 3, z, Palette.slab(Blocks.POLISHED_CINNABAR_SLAB, false));
			}
			case HANGING -> {
				for (int i = 0; i < 3; i++) {
					sink.set(x, y + i, z, Palette.st(Palette.FENCE));
				}
				int ax = x + arm.getStepX();
				int az = z + arm.getStepZ();
				sink.set(ax, y + 3, az, Palette.st(Palette.FENCE));
				sink.set(x, y + 3, z, Palette.st(Palette.FENCE));
				sink.set(ax, y + 2, az, Palette.ironChain());
				sink.set(ax, y + 1, az, Palette.lantern(Blocks.LANTERN, true));
			}
			case PALACE -> {
				for (int i = 0; i < 5; i++) {
					sink.set(x, y + i, z, Palette.st(Palette.PILLAR_THIN));
				}
				int ax = x + arm.getStepX();
				int az = z + arm.getStepZ();
				sink.set(x, y + 5, z, Palette.st(Palette.BEAM));
				sink.set(ax, y + 5, az, Palette.slab(Palette.BEAM == Blocks.WARPED_PLANKS ? Blocks.WARPED_SLAB : Blocks.MANGROVE_SLAB, false));
				sink.set(ax, y + 4, az, Palette.ironChain());
				sink.set(ax, y + 3, az, Palette.st(Palette.LIGHT));
				sink.set(ax, y + 2, az, Palette.st(Palette.LIGHT));
				sink.set(ax, y + 1, az, Palette.slab(Blocks.POLISHED_CINNABAR_SLAB, true));
			}
			case JADE -> {
				sink.set(x, y, z, Palette.st(Palette.FENCE));
				sink.set(x, y + 1, z, Palette.lantern(Palette.jade(Blocks.COPPER_LANTERN), false));
			}
			case PAGODA -> {
				sink.set(x, y, z, Palette.st(Blocks.STONE_BRICKS));
				sink.set(x, y + 1, z, Palette.st(Blocks.STONE_BRICK_WALL));
				sink.set(x, y + 2, z, Palette.lantern(Blocks.LANTERN, false));
				sink.set(x, y + 3, z, Palette.slab(Blocks.DEEPSLATE_TILE_SLAB, false));
				sink.set(x, y + 4, z, Palette.st(Blocks.DEEPSLATE_TILE_WALL));
			}
		}
	}
}
