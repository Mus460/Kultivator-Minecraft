package dev.kultivator.sect.build.blueprint.awan;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.LanternPost;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A cloud dock (Dermaga Awan, DESIGN 37.4.1, 37.5.4): a cantilevered deck the size of its ship class plus 2 (PERAHU
 * 9×15, SEKTE 13×27, BAHTERA 19×39) jutting from the outer terrace at the back of the main island: smooth-stone slabs
 * on mangrove beams, pale-oak rails along the sides with boarding gaps every five cells, blackstone mooring posts with
 * chains at the outer corners, stone lanterns at the inner corners and a quartz stair up to the plateau. A ship lands on
 * it by the ordinary dock rule (the deck is "ground" in the heightmap). Anchor {@code dermaga_<i>_<p|s|b>_<d>} at the
 * deck's centre (the direction letter is template-local; the sky API turns it to the world compass).
 */
public final class DermagaAwan implements Part {

	private final int index;

	public DermagaAwan(int index) {
		this.index = index;
	}

	private static AwanLayout.Dermaga dock(Ctx ctx, int index) {
		return AwanLayout.of(ctx.template()).docks.get(index);
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		AwanLayout.Dermaga d = dock(ctx, index);
		return new BoundingBox(d.deck().x0() - 1, d.level() - 3, d.deck().z0() - 1, d.deck().x1() + 1, 4, d.deck().z1() + 12);
	}

	@Override
	public String kind() {
		return "dock";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		AwanLayout layout = AwanLayout.of(ctx.template());
		AwanLayout.Dermaga d = dock(ctx, index);
		AwanLayout.Box deck = d.deck();
		int y = d.level();
		Phase previous = sink.phase();
		// The deck and its beams.
		sink.phase(Phase.PLINTH);
		for (int x = deck.x0(); x <= deck.x1(); x++) {
			for (int z = deck.z0(); z <= deck.z1(); z++) {
				sink.set(x, y - 1, z, Palette.slab(Blocks.SMOOTH_STONE_SLAB, true));
				boolean edgeBeam = x == deck.x0() || x == deck.x1();
				boolean crossBeam = Math.floorMod(z - deck.z1(), 6) == 0;
				if (edgeBeam) {
					sink.set(x, y - 2, z, Palette.axis(Blocks.STRIPPED_MANGROVE_LOG, Direction.Axis.Z));
				} else if (crossBeam) {
					sink.set(x, y - 2, z, Palette.axis(Blocks.STRIPPED_MANGROVE_LOG, Direction.Axis.X));
				}
			}
		}
		// Rails with boarding gaps, mooring posts and lanterns.
		sink.phase(Phase.WALL);
		for (int z = deck.z0(); z <= deck.z1() - 2; z++) {
			if (Math.floorMod(z - deck.z0(), 5) == 2) {
				continue;
			}
			sink.set(deck.x0(), y, z, Palette.st(AwanPalette.PAGAR_KAYU));
			sink.set(deck.x1(), y, z, Palette.st(AwanPalette.PAGAR_KAYU));
		}
		for (int x = deck.x0() + 1; x < deck.x1(); x++) {
			if (Math.abs(x - deck.cx()) > 1) {
				sink.set(x, y, deck.z0(), Palette.st(AwanPalette.PAGAR_KAYU));
			}
		}
		sink.phase(Phase.FRAME);
		for (int x : new int[]{deck.x0(), deck.x1()}) {
			sink.set(x, y, deck.z0(), Palette.st(Blocks.POLISHED_BLACKSTONE_WALL));
			sink.set(x, y + 1, deck.z0(), Palette.st(Blocks.POLISHED_BLACKSTONE_WALL));
			sink.set(x, y + 2, deck.z0(), Palette.ironChain());
		}
		sink.phase(Phase.ORNAMENT);
		LanternPost.emitOne(sink, deck.x0() + 1, y, deck.z1() - 1, LanternPost.Style.STONE, Direction.SOUTH);
		LanternPost.emitOne(sink, deck.x1() - 1, y, deck.z1() - 1, LanternPost.Style.STONE, Direction.SOUTH);
		// The stair up to the plateau, from the deck's inner edge along +z.
		AwanHias.stairDown(layout, sink, deck.cx(), deck.z1(), Direction.NORTH, y);
		sink.phase(previous);
		sink.anchor(d.anchor(), deck.cx(), y, deck.cz());
	}
}
