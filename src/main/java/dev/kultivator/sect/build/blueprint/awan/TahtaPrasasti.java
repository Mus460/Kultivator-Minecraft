package dev.kultivator.sect.build.blueprint.awan;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.LanternPost;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The Cloud Throne (Tahta Prasasti, DESIGN 37.4.5 T1): the plaza of white quartz around the empty stele cell, a jade
 * road on the axis to the gate, a lattice of quartz bricks, a chiseled-quartz border and a chiseled-quartz ring round the
 * throne; under the stele cell the jade base the hall stele is carried up onto at the Ascension (the only plan block
 * ever allowed under a stele: {@link Sink#steleBase}); jade lanterns at the corners. Anchors {@code tahta} (the throne
 * cell) and {@code arrival} (six in front of it, where arrivals step out of the clouds). The duty posts stay walkable.
 */
public final class TahtaPrasasti implements Part {

	private final int half;
	private final int back;
	private final int front;

	/** A plaza x -half..half, z back..front at level 0. */
	public TahtaPrasasti(int half, int back, int front) {
		this.half = half;
		this.back = back;
		this.front = front;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		return new BoundingBox(-half, -2, back, half, 4, front);
	}

	@Override
	public String kind() {
		return "throne";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		Phase previous = sink.phase();
		sink.phase(Phase.PLINTH);
		BlockState deck = Palette.st(AwanPalette.AWAN_LANTAI);
		BlockState lattice = Palette.st(Blocks.QUARTZ_BRICKS);
		BlockState border = Palette.st(AwanPalette.AWAN_TEPI);
		BlockState jade = AwanPalette.giok();
		for (int x = -half; x <= half; x++) {
			for (int z = back; z <= front; z++) {
				boolean edge = Math.abs(x) == half || z == back || z == front;
				int ring = Math.max(Math.abs(x), Math.abs(z));
				BlockState state;
				if (edge) {
					state = border;
				} else if (ring == 1) {
					state = border;
				} else if (x == 0 && z >= 2) {
					state = jade;
				} else if (Math.floorMod(x + z, 4) == 0 && Math.floorMod(x - z, 4) == 0) {
					state = lattice;
				} else {
					state = deck;
				}
				sink.set(x, -1, z, state);
				// A second course under the deck so the plaza never shows grass at its edge.
				sink.set(x, -2, z, Palette.st(Blocks.CALCITE));
			}
		}
		// The jade base of the throne: the stele is carried up onto it (37.3.5).
		sink.steleBase(true);
		sink.set(0, -1, 0, jade);
		sink.steleBase(false);
		sink.set(0, -2, 0, Palette.st(Blocks.CALCITE));
		sink.phase(Phase.ORNAMENT);
		int lx = half - 1;
		int backZ = back + 1;
		int frontZ = front - 1;
		for (int[] spot : new int[][]{{-lx, backZ}, {lx, backZ}, {-lx, frontZ}, {lx, frontZ}}) {
			LanternPost.emitOne(sink, spot[0], 0, spot[1], LanternPost.Style.JADE, Direction.SOUTH);
		}
		sink.phase(previous);
		sink.anchor("tahta", 0, 0, 0);
		sink.anchor("arrival", 0, 0, Math.min(front - 1, 6));
	}
}
