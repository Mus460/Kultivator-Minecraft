package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Boundary and garden walls (DESIGN 21.9 {@code Wall}, 21.5 "Palace walls"): axis-aligned segments of a polyline,
 * template-local, standing on a level (each column follows the ground under it when {@code level} is null).
 * <ul>
 * <li>{@link Style#BAMBOO_FENCE}: the humble T1 fence of template 1 (two bamboo fence rails); a T4 wall replaces it
 * (a light swap, DESIGN 21.2 rule 9).</li>
 * <li>{@link Style#WHITE_GARDEN}: white plaster on an andesite plinth with a dark tile cap and drips; moon gates (Ø5) and
 * leak windows (jade copper bars) frame views (Ji Cheng's borrowed scenery).</li>
 * <li>{@link Style#RED_PALACE}: polished cinnabar on a stone-brick footing, cinnabar bricks every 6 for texture, capped
 * with the rank B jade trim.</li>
 * <li>{@link Style#CITADEL}: deepslate bricks with 20% cracked and crenels (template 8).</li>
 * </ul>
 * {@link #gap} leaves an opening (gates are separate parts), {@link #moonGate} cuts a round gate, {@link #leakWindows}
 * puts a window every n cells, {@link #drips} sets the tile drips (both sides, or only the side facing the stele for
 * boundary walls on the footprint edge).
 */
public final class Wall implements Part {

	public enum Style {
		BAMBOO_FENCE,
		WHITE_GARDEN,
		RED_PALACE,
		CITADEL
	}

	private final Style style;
	private final Lv level;
	private final int height;
	private final List<int[]> segments = new ArrayList<>();
	private final List<int[]> gaps = new ArrayList<>();
	private final List<int[]> moons = new ArrayList<>();
	private int leakEvery;
	private int drips = 1;

	/** A wall of {@code style} {@code height} blocks high (fence: 2) standing on {@code level} (null: on the ground). */
	public Wall(Style style, Lv level, int height) {
		this.style = style;
		this.level = level;
		this.height = Math.max(2, height);
	}

	/** An axis-aligned segment (inclusive). */
	public Wall line(int x0, int z0, int x1, int z1) {
		segments.add(new int[]{x0, z0, x1, z1});
		return this;
	}

	/** The closed rectangle ring x0..x1 × z0..z1. */
	public Wall ring(int x0, int z0, int x1, int z1) {
		line(x0, z0, x1, z0);
		line(x1, z0, x1, z1);
		line(x0, z1, x1, z1);
		line(x0, z0, x0, z1);
		return this;
	}

	/** True when (x, z) lies within {@code margin} of one of the wall's segments (other parts keep their earthwork off it). */
	public boolean near(int x, int z, int margin) {
		for (int[] s : segments) {
			int ax = Math.min(s[0], s[2]) - margin;
			int bx = Math.max(s[0], s[2]) + margin;
			int az = Math.min(s[1], s[3]) - margin;
			int bz = Math.max(s[1], s[3]) + margin;
			if (x >= ax && x <= bx && z >= az && z <= bz) {
				return true;
			}
		}
		return false;
	}

	/** An opening of {@code halfWidth} each side of (x, z) on the wall line. */
	public Wall gap(int x, int z, int halfWidth) {
		gaps.add(new int[]{x, z, halfWidth});
		return this;
	}

	/** A round moon gate Ø5 centred on (x, z) of a garden wall (the wall must be 6 or more high). */
	public Wall moonGate(int x, int z) {
		moons.add(new int[]{x, z});
		return this;
	}

	/** A leak window (jade copper bars) every {@code every} cells of plaster walls (0 = none). */
	public Wall leakWindows(int every) {
		this.leakEvery = Math.max(0, every);
		return this;
	}

	/** Tile drips: 0 none, 1 the side facing the stele (default), 2 both sides. */
	public Wall drips(int mode) {
		this.drips = Math.max(0, Math.min(2, mode));
		return this;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		BoundingBox box = null;
		int y = level == null ? 0 : ctx.level(level);
		for (int[] s : segments) {
			BoundingBox each = new BoundingBox(Math.min(s[0], s[2]), y - 2, Math.min(s[1], s[3]), Math.max(s[0], s[2]), y + height + 1,
					Math.max(s[1], s[3]));
			box = box == null ? each : BoundingBox.encapsulating(box, each);
		}
		return box == null ? new BoundingBox(0, 0, 0, 0, 0, 0) : box;
	}

	@Override
	public String kind() {
		return "wall";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		Random random = ctx.random("wall" + style + segments.size());
		Phase previous = sink.phase();
		int index = 0;
		for (int[] s : segments) {
			int dx = Integer.signum(s[2] - s[0]);
			int dz = Integer.signum(s[3] - s[1]);
			int length = Math.max(Math.abs(s[2] - s[0]), Math.abs(s[3] - s[1]));
			for (int i = 0; i <= length; i++) {
				int x = s[0] + dx * i;
				int z = s[1] + dz * i;
				index++;
				if (inGap(x, z)) {
					continue;
				}
				int y = level == null ? ctx.terrain().ground(x, z) + 1 : ctx.level(level);
				// The side facing the stele (inner side) for drips.
				Direction inner = dx != 0 ? (z > 0 ? Direction.NORTH : Direction.SOUTH) : (x > 0 ? Direction.WEST : Direction.EAST);
				column(ctx, sink, random, x, y, z, inner, index, dx != 0);
			}
		}
		sink.phase(previous);
	}

	private boolean inGap(int x, int z) {
		for (int[] gap : gaps) {
			if (Math.abs(x - gap[0]) <= gap[2] && Math.abs(z - gap[1]) <= gap[2] && (x == gap[0] || z == gap[1])) {
				return true;
			}
		}
		return false;
	}

	/** True when (x, y - base, z) lies in a moon gate's circle. */
	private boolean inMoon(int x, int dy, int z) {
		for (int[] moon : moons) {
			int along = moon[0] == x ? z - moon[1] : x - moon[0];
			if (moon[0] != x && moon[1] != z) {
				continue;
			}
			double cy = 2.5D;
			double d = Math.sqrt(along * along + (dy - cy) * (dy - cy));
			if (dy < 5 && d <= 2.6D && Math.abs(along) <= 2) {
				return true;
			}
		}
		return false;
	}

	private void column(Ctx ctx, Sink sink, Random random, int x, int y, int z, Direction inner, int index, boolean alongX) {
		// A footing down to the ground so the wall never floats.
		int ground = ctx.terrain().ground(x, z);
		sink.phase(Phase.PLINTH);
		for (int fy = Math.max(ground + 1, y - 4); fy < y; fy++) {
			sink.set(x, fy, z, Palette.st(Palette.STONE));
		}
		sink.set(x, y - 1, z, Palette.st(style == Style.CITADEL ? Blocks.DEEPSLATE_BRICKS : Palette.STONE));
		sink.phase(Phase.WALL);
		switch (style) {
			case BAMBOO_FENCE -> {
				sink.set(x, y, z, Palette.st(Blocks.BAMBOO_FENCE));
				sink.set(x, y + 1, z, Palette.st(Blocks.BAMBOO_FENCE));
			}
			case WHITE_GARDEN -> {
				boolean leak = leakEvery > 0 && index % leakEvery == leakEvery / 2;
				for (int dy = 0; dy < height; dy++) {
					if (inMoon(x, dy, z)) {
						sink.air(x, y + dy, z);
						continue;
					}
					BlockState state;
					if (dy == 0) {
						state = Palette.st(Palette.PLINTH);
					} else if (leak && (dy == 2 || dy == 3) && height >= 5) {
						state = Palette.st(Palette.jade(Blocks.COPPER_BARS));
					} else {
						state = Palette.st(Palette.PLASTER);
					}
					sink.set(x, y + dy, z, state);
				}
				cap(sink, x, y + height, z, inner, Palette.st(Blocks.DEEPSLATE_TILES), Palette.slab(Blocks.DEEPSLATE_TILE_SLAB, false),
						Blocks.DEEPSLATE_TILE_SLAB);
			}
			case RED_PALACE -> {
				for (int dy = 0; dy < height; dy++) {
					BlockState state = dy == 0 ? Palette.st(Palette.STONE)
							: Palette.st(index % 6 == 0 && dy > 1 ? Palette.PALACE_WALL_TEXTURE : Palette.PALACE_WALL);
					sink.set(x, y + dy, z, state);
				}
				cap(sink, x, y + height, z, inner, Palette.st(Palette.jadeBlock()), Palette.slab(Palette.jadeSlab(), false), Palette.jadeSlab());
			}
			case CITADEL -> {
				for (int dy = 0; dy < height; dy++) {
					sink.set(x, y + dy, z, Palette.st(random.nextInt(5) == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.DEEPSLATE_BRICKS));
				}
				if (index % 2 == 0) {
					sink.set(x, y + height, z, Palette.st(Blocks.DEEPSLATE_BRICK_WALL));
				}
			}
		}
	}

	private void cap(Sink sink, int x, int y, int z, Direction inner, BlockState ridge, BlockState top, net.minecraft.world.level.block.Block drip) {
		sink.phase(Phase.ROOF);
		sink.set(x, y, z, ridge);
		sink.set(x, y + 1, z, top);
		if (drips >= 1) {
			sink.set(x + inner.getStepX(), y, z + inner.getStepZ(), Palette.slab(drip, true));
		}
		if (drips >= 2) {
			sink.set(x - inner.getStepX(), y, z - inner.getStepZ(), Palette.slab(drip, true));
		}
	}
}
