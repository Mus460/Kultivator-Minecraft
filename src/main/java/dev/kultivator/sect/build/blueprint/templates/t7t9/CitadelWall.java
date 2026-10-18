package dev.kultivator.sect.build.blueprint.templates.t7t9;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The dark citadel wall of template 8 (DESIGN 21.3 T4, 21.5 "Template 8 citadel", 21.8 "Template 8's citadel walls are
 * the deliberate exception" to the 4-block rule): a rampart three cells thick along axis-aligned centre lines. Both faces
 * are deepslate bricks with about 20% cracked bricks and a chiseled band under the wall walk, the core is hidden stone, the
 * walk is polished deepslate; the outer parapet alternates merlons and embrasures, the inner one is a low deepslate-brick
 * wall with a lantern every 8 cells. {@link #gap}s leave the openings that gate towers and side gates fill. The inner side
 * is the side facing the stele. Template-local; the walk is the standing level {@code level + height}.
 * <p>
 * Art direction from Ming city walls (Xi'an, Pingyao), not novel canon.
 */
public final class CitadelWall implements Part {

	private final Lv level;
	private final int height;
	private final List<int[]> segments = new ArrayList<>();
	private final List<int[]> gaps = new ArrayList<>();

	/** A wall whose walk stands {@code height} above {@code level}. */
	public CitadelWall(Lv level, int height) {
		this.level = level;
		this.height = Math.max(4, height);
	}

	/** An axis-aligned centre line (inclusive); the wall is the line ±1. */
	public CitadelWall line(int x0, int z0, int x1, int z1) {
		segments.add(new int[]{x0, z0, x1, z1});
		return this;
	}

	/** The closed rectangle of centre lines x0..x1 × z0..z1. */
	public CitadelWall ring(int x0, int z0, int x1, int z1) {
		line(x0, z0, x1, z0);
		line(x1, z0, x1, z1);
		line(x0, z1, x1, z1);
		line(x0, z0, x0, z1);
		return this;
	}

	/** No wall within {@code half} of (x, z) along the line (a gate stands there). */
	public CitadelWall gap(int x, int z, int half) {
		gaps.add(new int[]{x, z, half});
		return this;
	}

	private boolean inGap(int x, int z) {
		for (int[] gap : gaps) {
			if (Math.abs(x - gap[0]) <= gap[2] && Math.abs(z - gap[1]) <= gap[2]) {
				return true;
			}
		}
		return false;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		BoundingBox box = null;
		for (int[] s : segments) {
			BoundingBox each = new BoundingBox(Math.min(s[0], s[2]) - 1, y - 2, Math.min(s[1], s[3]) - 1, Math.max(s[0], s[2]) + 1, y + height + 2,
					Math.max(s[1], s[3]) + 1);
			box = box == null ? each : BoundingBox.encapsulating(box, each);
		}
		return box == null ? new BoundingBox(0, y, 0, 0, y, 0) : box;
	}

	@Override
	public String kind() {
		return "wall";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int y0 = ctx.level(level);
		int walk = y0 + height - 1;
		Random random = ctx.random("citadel" + segments.size());
		Phase previous = sink.phase();
		int index = 0;
		for (int[] s : segments) {
			int dx = Integer.signum(s[2] - s[0]);
			int dz = Integer.signum(s[3] - s[1]);
			boolean alongX = dx != 0;
			int length = Math.max(Math.abs(s[2] - s[0]), Math.abs(s[3] - s[1]));
			for (int i = 0; i <= length; i++) {
				int cx = s[0] + dx * i;
				int cz = s[1] + dz * i;
				index++;
				if (inGap(cx, cz)) {
					continue;
				}
				// The outer side points away from the stele.
				Direction out = alongX ? (cz >= 0 ? Direction.SOUTH : Direction.NORTH) : (cx >= 0 ? Direction.EAST : Direction.WEST);
				for (int w = -1; w <= 1; w++) {
					int x = cx + (alongX ? 0 : w);
					int z = cz + (alongX ? w : 0);
					int side = alongX ? w * out.getStepZ() : w * out.getStepX();
					boolean outer = side > 0;
					boolean inner = side < 0;
					int ground = ctx.terrain().ground(x, z);
					sink.phase(Phase.CLEAR);
					for (int y = Math.max(ground + 1, y0); y <= Math.min(ctx.terrain().top(x, z), walk + 4); y++) {
						sink.air(x, y, z);
					}
					sink.phase(Phase.PLINTH);
					for (int y = Math.max(ground + 1, y0 - 6); y < walk; y++) {
						if (!outer && !inner) {
							sink.set(x, y, z, Palette.st(Palette.FILL_STONE));
							continue;
						}
						boolean band = y == walk - 2;
						boolean footing = y < y0;
						sink.set(x, y, z, Palette.st(band ? Blocks.CHISELED_DEEPSLATE : footing ? Blocks.COBBLED_DEEPSLATE
								: random.nextInt(5) == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.DEEPSLATE_BRICKS));
					}
					sink.set(x, walk, z, Palette.st(outer || inner ? Blocks.DEEPSLATE_BRICKS : Blocks.POLISHED_DEEPSLATE));
					sink.phase(Phase.ORNAMENT);
					if (outer) {
						if (index % 3 != 0) {
							sink.set(x, walk + 1, z, Palette.st(Blocks.DEEPSLATE_BRICKS));
							sink.set(x, walk + 2, z, Palette.slab(Blocks.DEEPSLATE_TILE_SLAB, false));
						} else {
							sink.set(x, walk + 1, z, Palette.st(Blocks.DEEPSLATE_BRICK_WALL));
						}
					} else if (inner) {
						sink.set(x, walk + 1, z, Palette.st(Blocks.DEEPSLATE_BRICK_WALL));
						if (index % 8 == 4) {
							sink.set(x, walk + 2, z, Palette.lantern(Blocks.LANTERN, false));
						}
					}
				}
			}
		}
		sink.phase(previous);
	}
}
