package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Covered galleries (DESIGN 21.9 {@code Corridor}; straight, climbing 爬山廊 and waterside, 21.8 rule 6): a 3-wide walk
 * between cinnabar posts every 3 cells, benches between the posts, a rolled-ridge (卷棚) rank A roof, lanterns every
 * 6 cells. Template-local: the centre line runs from (x0, z0) to (x1, z1) along one axis; {@code level} at the start,
 * {@link #to} at the end (a climbing gallery steps up with stairs and a stepped roof). {@link #wall} closes one side with
 * a plaster wall and leak windows ({@code -1} the left of the walking direction, {@code 1} the right).
 */
public final class Corridor implements Part {

	public static final int POST_SPACING = 3;
	public static final int POST_HEIGHT = 3;

	private final int x0;
	private final int z0;
	private final int x1;
	private final int z1;
	private final Lv level;
	private Lv end;
	private int wallSide;
	private int[] openings = new int[0];

	public Corridor(int x0, int z0, int x1, int z1, Lv level) {
		this.x0 = x0;
		this.z0 = z0;
		this.x1 = x1;
		this.z1 = z1;
		this.level = level;
		this.end = level;
	}

	/** The level at the far end (climbing gallery). */
	public Corridor to(Lv end) {
		this.end = end;
		return this;
	}

	/** A plaster wall with leak windows on one side (-1 left, 1 right, 0 none). */
	public Corridor wall(int side) {
		this.wallSide = Integer.signum(side);
		return this;
	}

	/** Cells along the walk (from the start, 0-based) left open sideways (no bench or wall) for paths crossing it. */
	public Corridor openings(int... cells) {
		this.openings = cells.clone();
		return this;
	}

	private boolean alongZ() {
		return x0 == x1 || Math.abs(z1 - z0) >= Math.abs(x1 - x0);
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = Math.min(ctx.level(level), ctx.level(end));
		int top = Math.max(ctx.level(level), ctx.level(end)) + POST_HEIGHT + 5;
		boolean z = alongZ();
		return new BoundingBox(Math.min(x0, x1) - (z ? 3 : 1), y - 2, Math.min(z0, z1) - (z ? 1 : 3), Math.max(x0, x1) + (z ? 3 : 1), top,
				Math.max(z0, z1) + (z ? 1 : 3));
	}

	@Override
	public String kind() {
		return "gallery";
	}

	private boolean open(int i) {
		for (int cell : openings) {
			if (cell == i) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		boolean z = alongZ();
		int length = z ? Math.abs(z1 - z0) : Math.abs(x1 - x0);
		Direction along = z ? (z1 >= z0 ? Direction.SOUTH : Direction.NORTH) : (x1 >= x0 ? Direction.EAST : Direction.WEST);
		sink.pushFacing(x0, 0, z0, along);
		int ya = ctx.level(level);
		int yb = ctx.level(end);
		int runStart = 0;
		int runLevel = ya;
		for (int i = 0; i <= length; i++) {
			int y = length == 0 ? ya : ya + (int) Math.round((yb - ya) * (i / (double) length));
			int prev = i == 0 ? y : ya + (int) Math.round((yb - ya) * ((i - 1) / (double) length));
			sink.phase(Phase.PLINTH);
			for (int dx = -2; dx <= 2; dx++) {
				int ground = sink.ground(ctx, dx, i);
				for (int fy = Math.max(ground + 1, y - 6); fy < y - 1; fy++) {
					sink.set(dx, fy, i, Palette.st(Palette.STONE));
				}
				if (y > prev && Math.abs(dx) <= 1) {
					sink.set(dx, y - 1, i, Palette.stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
					sink.set(dx, y - 2, i, Palette.st(Palette.STONE));
				} else {
					sink.set(dx, y - 1, i, Palette.st(Math.abs(dx) == 2 ? Palette.STONE : Palette.PLINTH));
				}
				for (int fy = y; fy < y + POST_HEIGHT; fy++) {
					if (Math.abs(dx) <= 1) {
						sink.air(dx, fy, i);
					}
				}
			}
			sink.phase(Phase.FRAME);
			boolean post = i % POST_SPACING == 0 || i == length;
			for (int side : new int[]{-2, 2}) {
				if (post) {
					for (int fy = y; fy < y + POST_HEIGHT; fy++) {
						sink.set(side, fy, i, Palette.st(fy == y ? Palette.PILLAR_BASE : Palette.PILLAR_THIN));
					}
				} else if (!open(i)) {
					boolean walled = side == 2 * wallSide;
					sink.phase(Phase.WALL);
					if (walled) {
						for (int fy = y; fy < y + POST_HEIGHT; fy++) {
							boolean leak = fy == y + 1 && i % 6 == 3;
							sink.set(side, fy, i, Palette.st(leak ? Palette.jade(Blocks.COPPER_BARS) : fy == y ? Palette.PLINTH : Palette.PLASTER));
						}
					} else {
						sink.set(side, y, i, Palette.slab(Palette.WOOD_SLAB, false));
					}
					sink.phase(Phase.FRAME);
				}
				sink.set(side, y + POST_HEIGHT, i, Palette.st(Palette.BEAM));
			}
			if (i % 6 == 3) {
				sink.phase(Phase.ORNAMENT);
				sink.set(0, y + POST_HEIGHT, i, Palette.ironChain());
				sink.set(0, y + POST_HEIGHT - 1, i, Palette.lantern(Blocks.LANTERN, true));
			}
			// Close a roof run where the level changes or at the end.
			boolean last = i == length;
			int nextY = last ? y : ya + (int) Math.round((yb - ya) * ((i + 1) / (double) length));
			if (last || nextY != y) {
				roofOver(ctx, sink, -2, 2, runStart, i, runLevel, true);
				runStart = i + 1;
				runLevel = nextY;
			}
		}
		sink.pop();
	}

	/**
	 * A rolled-ridge gallery roof (rank A) over posts on x = {@code xa} and {@code xb} from {@code za} to {@code zb}
	 * (canonical frame, the run along z) whose posts stand at standing level {@code y}: the beams at y + 3, the eave one
	 * beyond the posts, the ring top slabs closed into full blocks so no slit shows.
	 */
	public static void roofOver(Ctx ctx, Sink sink, int xa, int xb, int za, int zb, int y, boolean rolled) {
		int base = y + POST_HEIGHT + 1;
		RoofBuilder roof = new RoofBuilder(RoofBuilder.Type.GABLE, xa - 1, xb + 1, za - 1, zb + 1, base - 1, ctx.roof(Rank.A)).overhang(1)
				.wall(xa, xb, za - 1, zb + 1).rolled(rolled).ridgeLight(false);
		roof.emit(sink);
	}
}
