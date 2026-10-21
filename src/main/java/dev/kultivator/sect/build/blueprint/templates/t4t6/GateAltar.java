package dev.kultivator.sect.build.blueprint.templates.t4t6;

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
 * A gate formation altar (DESIGN 21.1 Table B "Gate altars", 21.2 T5, 21.11): an optional extra Protective Formation of
 * DESIGN 10 laid flush in a paved dais beside the gate, so it never blocks a path: a lodestone in the middle and crying
 * obsidian at (±4, 0) and (0, ±4) on the same level (exactly what {@code FormationPattern} reads), placed inactive (the
 * owner registers it with an amethyst shard). The dais is polished andesite with a chiseled-tuff-brick octagon ring at r 3,
 * gilded-blackstone points on the diagonals, a stone-brick rim and four stone lanterns at its corners. Exact items: 1
 * lodestone and 4 crying obsidian; with {@link #emptySockets()} none: the five stones are left as empty sockets over a
 * chiseled polished blackstone base (like the template 7-9 altar), so the tier completes without them and the owner may
 * set their own stones in later. Template-local, centred on (x, z) on {@code level}.
 */
public final class GateAltar implements Part {

	/** Half size of the dais (the crying obsidian sits at 4, the rim at 5). */
	public static final int HALF = 5;

	private final int x;
	private final int z;
	private final Lv level;
	private boolean empty;

	public GateAltar(int x, int z, Lv level) {
		this.x = x;
		this.z = z;
		this.level = level;
	}

	/** Leaves the lodestone and crying obsidian out: five empty sockets the owner may fill (an optional formation). */
	public GateAltar emptySockets() {
		this.empty = true;
		return this;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		return new BoundingBox(x - HALF, y - 2, z - HALF, x + HALF, y + 4, z + HALF);
	}

	@Override
	public String kind() {
		return "gate_altar";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int y = ctx.level(level);
		sink.push(x, y, z, 0);
		sink.phase(Phase.PLINTH);
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				boolean rim = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
				int ground = sink.ground(ctx, dx, dz);
				for (int fy = Math.max(ground + 1, -6); fy <= -2; fy++) {
					sink.set(dx, fy, dz, Palette.st(Palette.FILL_STONE));
				}
				int chamfered = Math.max(Math.max(Math.abs(dx), Math.abs(dz)), (Math.abs(dx) + Math.abs(dz) + 1) * 2 / 3);
				var floor = rim ? Palette.st(Palette.STONE) : chamfered == 3 ? Palette.st(Palette.STONE_FRET) : Palette.st(Palette.PLINTH);
				sink.set(dx, -1, dz, floor);
				for (int fy = 0; fy <= 2; fy++) {
					if (!rim) {
						sink.air(dx, fy, dz);
					}
				}
			}
		}
		for (int[] diagonal : new int[][]{{2, 2}, {2, -2}, {-2, 2}, {-2, -2}}) {
			sink.set(diagonal[0], -1, diagonal[1], Palette.st(Palette.PLAQUE_FIELD));
		}
		// The Protective Formation itself (DESIGN 10): all on one level.
		if (empty) {
			int[][] sockets = {{0, 0}, {4, 0}, {-4, 0}, {0, 4}, {0, -4}};
			for (int[] socket : sockets) {
				sink.set(socket[0], -2, socket[1], Palette.st(Blocks.CHISELED_POLISHED_BLACKSTONE));
				sink.air(socket[0], -1, socket[1]);
			}
		} else {
			sink.set(0, -1, 0, Palette.st(Blocks.LODESTONE));
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				sink.set(4 * direction.getStepX(), -1, 4 * direction.getStepZ(), Palette.st(Blocks.CRYING_OBSIDIAN));
			}
		}
		sink.phase(Phase.ORNAMENT);
		for (int[] corner : new int[][]{{HALF, HALF}, {HALF, -HALF}, {-HALF, HALF}, {-HALF, -HALF}}) {
			LanternPost.emitOne(sink, corner[0], 0, corner[1], LanternPost.Style.STONE, Direction.SOUTH);
		}
		sink.anchor("gate_altar_" + (x >= 0 ? "e" : "w"), 0, 0, 1);
		sink.pop();
	}
}
