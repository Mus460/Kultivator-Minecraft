package dev.kultivator.sect.build.blueprint.templates.t7t9;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.LanternPost;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * An optional gate formation altar (DESIGN 21.1 "Size perks", 21.2 T5): a low octagonal stone dais with five empty
 * sockets in the exact shape of a Protective Formation (DESIGN 10: the lodestone in the middle, crying obsidian at ±4 on
 * the same level). The plan places <b>no</b> lodestone or crying obsidian, so the tier completes without them; the owner
 * may set their own stones into the sockets and register the formation with an amethyst shard. Stone lanterns stand at
 * the four diagonals, quartz steps at the four sides, a chiseled-tuff rim. Template-local, centred on (x, z), standing on {@code level}.
 */
public final class GateAltar implements Part {

	/** Socket distance from the middle socket (the {@code FormationPattern} pillar distance). */
	public static final int SOCKET = 4;

	private final int x;
	private final int z;
	private final Lv level;

	public GateAltar(int x, int z, Lv level) {
		this.x = x;
		this.z = z;
		this.level = level;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		return new BoundingBox(x - 6, y - 2, z - 6, x + 6, y + 4, z + 6);
	}

	@Override
	public String kind() {
		return "gate_altar";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int y = ctx.level(level);
		sink.push(x, y, z, 0);
		Phase previous = sink.phase();
		sink.phase(Phase.PLINTH);
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				int a = Math.abs(dx);
				int b = Math.abs(dz);
				if (a + b > 8) {
					continue;
				}
				boolean rim = a == 5 || b == 5 || a + b == 8;
				sink.set(dx, -1, dz, Palette.st(Palette.STONE));
				sink.set(dx, 0, dz, Palette.st(rim ? Palette.STONE_FRET : (a + b) % 4 == 0 ? Palette.STONE : Palette.PLINTH));
			}
		}
		// Five sockets: dark collars around empty holes on the deck level.
		int[][] sockets = {{0, 0}, {SOCKET, 0}, {-SOCKET, 0}, {0, SOCKET}, {0, -SOCKET}};
		for (int[] socket : sockets) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				int cx = socket[0] + side.getStepX();
				int cz = socket[1] + side.getStepZ();
				if (Math.abs(cx) + Math.abs(cz) <= 8 && Math.abs(cx) <= 5 && Math.abs(cz) <= 5) {
					sink.set(cx, 0, cz, Palette.st(Blocks.POLISHED_BLACKSTONE_BRICKS));
				}
			}
		}
		for (int[] socket : sockets) {
			sink.set(socket[0], -1, socket[1], Palette.st(Blocks.CHISELED_POLISHED_BLACKSTONE));
			sink.air(socket[0], 0, socket[1]);
			sink.air(socket[0], 1, socket[1]);
		}
		// Steps on the four sides.
		for (Direction side : Direction.Plane.HORIZONTAL) {
			for (int w = -1; w <= 1; w++) {
				int sx = side.getStepX() * 6 + (side.getAxis() == Direction.Axis.Z ? w : 0);
				int sz = side.getStepZ() * 6 + (side.getAxis() == Direction.Axis.X ? w : 0);
				sink.set(sx, 0, sz, Palette.stair(Palette.MARBLE_STAIRS, side.getOpposite(), false));
			}
		}
		// Stone lanterns at the diagonals.
		sink.phase(Phase.ORNAMENT);
		for (int[] corner : new int[][]{{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
			LanternPost.emitOne(sink, corner[0], 1, corner[1], LanternPost.Style.STONE, Direction.SOUTH);
		}
		sink.phase(previous);
		sink.pop();
	}
}
