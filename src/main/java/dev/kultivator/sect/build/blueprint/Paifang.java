package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The ceremonial archway (paifang, DESIGN 21.7, 21.9 {@code Paifang}) across the axis, at least 8 blocks in front of a
 * gate hall. A paifang without roofs is forbidden (it would read as a torii). Template-local, centred on (x, z), the
 * opening facing ±z (or ±x with {@link #front} east/west).
 * <ul>
 * <li>{@link #bays}: 1 (two posts at ±3), 3 (four posts at ±3, ±7) or 5 (six posts at ±3, ±7, ±11).</li>
 * <li>{@link #roofs}: 1, 3, 5 or 7. The centre roof is always there; 3 adds the side-bay roofs butting against the centre
 * posts; 5 adds small roofs over the outer posts; 7 adds small roofs over the centre posts too.</li>
 * <li>{@link #material}: {@link Material#RED_WOOD} (cinnabar posts, warped lintels) or {@link Material#WHITE_STONE} (quartz
 * pillars, chiseled quartz lintels); roofs always in the plan's glaze.</li>
 * </ul>
 * Centre opening 5 wide × 6 high, side openings 3 × 4; post feet clamped by drum stones (stone-brick stairs against the
 * post); a 3×2 plaque with yellow glazed cloud panels; brackets under every roof; a gold pearl on the centre ridge.
 */
public final class Paifang implements Part {

	public enum Material {
		RED_WOOD,
		WHITE_STONE
	}

	private final int x;
	private final int z;
	private final Lv level;
	private int bays = 3;
	private int roofs = 3;
	private Material material = Material.WHITE_STONE;
	private Direction front = Direction.SOUTH;

	public Paifang(int x, int z, Lv level) {
		this.x = x;
		this.z = z;
		this.level = level;
	}

	public Paifang bays(int bays) {
		this.bays = bays >= 5 ? 5 : bays >= 3 ? 3 : 1;
		return this;
	}

	public Paifang roofs(int roofs) {
		this.roofs = roofs >= 7 ? 7 : roofs >= 5 ? 5 : roofs >= 3 ? 3 : 1;
		return this;
	}

	public Paifang material(Material material) {
		this.material = material;
		return this;
	}

	public Paifang front(Direction front) {
		this.front = front;
		return this;
	}

	private int halfWidth() {
		return bays == 5 ? 12 : bays == 3 ? 8 : 4;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		int hw = halfWidth() + 1;
		return Part.box(x, z, front, -hw, y - 1, -2, hw, y + 14, 2);
	}

	@Override
	public String kind() {
		return "paifang";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int y = ctx.level(level);
		sink.pushFacing(x, y, z, front);
		boolean wood = material == Material.RED_WOOD;
		Block post = wood ? Palette.PILLAR : Blocks.QUARTZ_PILLAR;
		Block lintel = wood ? Palette.BEAM : Palette.MARBLE_FRET;
		RoofMat mat = ctx.roof(Rank.D);
		// Foundations under every post and a paved threshold.
		sink.phase(Phase.PLINTH);
		int hw = halfWidth();
		for (int fx = -hw; fx <= hw; fx++) {
			sink.set(fx, -1, 0, Palette.st(Palette.PLINTH));
		}
		int[] centrePosts = {-3, 3};
		int[] sidePosts = bays >= 3 ? new int[]{-7, 7} : new int[0];
		int[] outerPosts = bays >= 5 ? new int[]{-11, 11} : new int[0];
		// Posts with drum stones.
		sink.phase(Phase.FRAME);
		for (int px : centrePosts) {
			column(sink, px, 10, post);
		}
		for (int px : sidePosts) {
			column(sink, px, 5, post);
		}
		for (int px : outerPosts) {
			column(sink, px, 4, post);
		}
		// Lintels, plaque and cloud panels.
		for (int fx = -3; fx <= 3; fx++) {
			sink.set(fx, 6, 0, Palette.st(lintel));
			sink.set(fx, 9, 0, Palette.st(lintel));
		}
		sink.phase(Phase.ORNAMENT);
		for (int fx = -2; fx <= 2; fx++) {
			for (int fy = 7; fy <= 8; fy++) {
				sink.set(fx, fy, 0, Math.abs(fx) == 2 ? Palette.st(Palette.glazed(DyeColor.YELLOW)) : Palette.st(Palette.PLAQUE_FIELD));
			}
		}
		sink.set(0, 8, 1, Palette.facing(Blocks.DARK_OAK_WALL_SIGN, Direction.SOUTH));
		sink.phase(Phase.FRAME);
		if (bays >= 3) {
			for (int side : new int[]{-1, 1}) {
				for (int fx = 4; fx <= 6; fx++) {
					sink.set(side * fx, 4, 0, Palette.st(lintel));
				}
			}
		}
		if (bays >= 5) {
			for (int side : new int[]{-1, 1}) {
				for (int fx = 8; fx <= 10; fx++) {
					sink.set(side * fx, 3, 0, Palette.st(lintel));
				}
			}
		}
		// Centre roof over the centre bay (x -4..4) on brackets at y 10.
		miniRoof(sink, mat, -4, 4, 10, roofs < 7, true, true, true);
		if (roofs >= 3 && bays >= 3) {
			miniRoof(sink, mat, -7, -4, 5, true, false, true, false);
			miniRoof(sink, mat, 4, 7, 5, true, false, false, true);
		}
		if (roofs >= 3 && bays >= 5) {
			miniRoof(sink, mat, -11, -8, 4, true, false, true, false);
			miniRoof(sink, mat, 8, 11, 4, true, false, false, true);
		}
		if (roofs >= 5 && bays >= 3) {
			miniRoof(sink, mat, -8, -6, 8, false, false, true, true);
			miniRoof(sink, mat, 6, 8, 8, false, false, true, true);
		}
		if (bays == 1 && roofs >= 3) {
			// A 1-bay paifang with 3 roofs: small roofs over both posts, flanking the centre roof.
			miniRoof(sink, mat, -6, -4, 8, true, false, true, false);
			miniRoof(sink, mat, 4, 6, 8, true, false, false, true);
			for (int side : new int[]{-1, 1}) {
				for (int fx = 4; fx <= 6; fx++) {
					sink.phase(Phase.FRAME).set(side * fx, 7, 0, Palette.st(lintel));
				}
				sink.set(side * 6, 6, 0, Palette.stair(Palette.BRACKET, side < 0 ? Direction.EAST : Direction.WEST, true));
			}
		}
		if (roofs >= 7) {
			miniRoof(sink, mat, -5, -3, 13, false, false, true, true);
			miniRoof(sink, mat, 3, 5, 13, false, false, true, true);
		}
		sink.pop();
	}

	/** A post from the threshold up to {@code top} with drum stones clamping its foot. */
	private static void column(Sink sink, int px, int top, Block post) {
		for (int fy = 0; fy <= top; fy++) {
			sink.set(px, fy, 0, post == Blocks.QUARTZ_PILLAR ? Palette.axis(post, Direction.Axis.Y) : Palette.st(post));
		}
		sink.set(px, 0, -1, Palette.stair(Palette.STONE_STAIRS, Direction.SOUTH, false));
		sink.set(px, 0, 1, Palette.stair(Palette.STONE_STAIRS, Direction.NORTH, false));
		sink.set(px, -1, -1, Palette.st(Palette.STONE));
		sink.set(px, -1, 1, Palette.st(Palette.STONE));
	}

	/**
	 * A 3-deep mini roof over x0..x1 on a bracket row at {@code by}: brackets (warped stairs, tall side to the middle),
	 * stair slopes on z = ±1 facing the ridge, the ridge in trim, lifted tip slabs and chiwen hooks at both ends; the centre
	 * roof gets the gold pearl.
	 */
	private static void miniRoof(Sink sink, RoofMat mat, int x0, int x1, int by, boolean chiwen, boolean pearl, boolean tipLeft,
			boolean tipRight) {
		sink.phase(Phase.FRAME);
		for (int fx = x0; fx <= x1; fx++) {
			sink.set(fx, by, -1, Palette.stair(Palette.BRACKET, Direction.SOUTH, true));
			sink.set(fx, by, 0, Palette.st(Palette.BEAM));
			sink.set(fx, by, 1, Palette.stair(Palette.BRACKET, Direction.NORTH, true));
		}
		sink.phase(Phase.ROOF);
		for (int fx = x0; fx <= x1; fx++) {
			sink.layer(1).set(fx, by + 1, -1, Palette.stair(mat.fieldStairs(), Direction.SOUTH, false));
			sink.layer(1).set(fx, by + 1, 1, Palette.stair(mat.fieldStairs(), Direction.NORTH, false));
			sink.layer(2).set(fx, by + 1, 0, Palette.st(mat.fieldFull()));
			sink.layer(3).set(fx, by + 2, 0, Palette.st(mat.trimFull()));
		}
		// Lifted tips one beyond each free end (never into a post).
		for (int fx : new int[]{x0 - 1, x1 + 1}) {
			if (fx == x0 - 1 && !tipLeft || fx == x1 + 1 && !tipRight) {
				continue;
			}
			sink.layer(1).set(fx, by + 1, -1, Palette.slab(mat.trimSlab(), true));
			sink.layer(1).set(fx, by + 1, 1, Palette.slab(mat.trimSlab(), true));
			sink.layer(1).set(fx, by + 1, 0, Palette.slab(mat.trimSlab(), false));
		}
		if (chiwen) {
			if (tipLeft) {
				sink.layer(4).set(x0, by + 3, 0, Palette.stair(mat.trimStairs(), Direction.WEST, false));
				sink.layer(4).set(x0 - 1, by + 3, 0, Palette.slab(mat.trimSlab(), true));
			}
			if (tipRight) {
				sink.layer(4).set(x1, by + 3, 0, Palette.stair(mat.trimStairs(), Direction.EAST, false));
				sink.layer(4).set(x1 + 1, by + 3, 0, Palette.slab(mat.trimSlab(), true));
			}
		}
		if (pearl) {
			int cx = (x0 + x1) / 2;
			sink.phase(Phase.ORNAMENT);
			sink.set(cx, by + 3, 0, Palette.st(Palette.GOLD));
			sink.set(cx - 1, by + 3, 0, Palette.stair(mat.trimStairs(), Direction.EAST, false));
			sink.set(cx + 1, by + 3, 0, Palette.stair(mat.trimStairs(), Direction.WEST, false));
		}
		sink.layerAuto();
	}
}
