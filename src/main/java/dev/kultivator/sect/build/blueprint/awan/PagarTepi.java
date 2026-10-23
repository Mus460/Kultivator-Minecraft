package dev.kultivator.sect.build.blueprint.awan;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * The rim fence of one growth stage of an island (DESIGN 37.4.1 {@code PagarTepi}, 37.5.7: disciples never fall): a
 * pale-oak fence (T1-T3), a white balustrade of diorite walls with quartz-pillar posts and quartz caps (the plateau edge
 * from T4 on), or the marble balustrade of the outer rim (T6). Openings are automatic: the axis to the gate, every
 * bridge, every waterfall cleft, the docks, the sword terrace, the ferry stop and the meditation pavilions. When the
 * island grows past a fence, the new ring's component takes the old fence down first ({@link #bongkar}: the light swap,
 * refunded) so no fence is ever left standing inside the island.
 */
public final class PagarTepi implements Part {

	/** Fence styles. */
	public enum Gaya {
		KAYU,
		BATU,
		MARMER
	}

	private final String island;
	private final int ring;
	private final Gaya gaya;
	private boolean demolish;

	public PagarTepi(String island, int ring, Gaya gaya) {
		this.island = island;
		this.ring = ring;
		this.gaya = gaya;
	}

	/** Takes this fence down instead (air in FRAME over its cells). */
	public PagarTepi bongkar() {
		this.demolish = true;
		return this;
	}

	@Override
	public String kind() {
		return demolish ? "demolish" : "fence";
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		AwanLayout layout = AwanLayout.of(ctx.template());
		AwanLayout.Pulau p = PulauAwan.islandOf(layout, island);
		int reach = (int) Math.ceil(p.radius() * 1.16D) + 1;
		return new BoundingBox(p.cx - reach, p.level - 5, p.cz - reach, p.cx + reach, p.level + 3, p.cz + reach);
	}

	/** The fence cells (x, standing y, z) of a ring's rim with its openings left out. */
	static List<int[]> cells(AwanLayout layout, AwanLayout.Pulau p, int ring) {
		List<int[]> list = new ArrayList<>();
		int reach = (int) Math.ceil(p.radius() * 1.16D) + 1;
		int max = p.main ? ring : 0;
		for (int x = p.cx - reach; x <= p.cx + reach; x++) {
			for (int z = p.cz - reach; z <= p.cz + reach; z++) {
				if (!PulauAwan.exists(layout, p, x, z, max)) {
					continue;
				}
				boolean rim = !PulauAwan.exists(layout, p, x + 1, z, max) || !PulauAwan.exists(layout, p, x - 1, z, max)
						|| !PulauAwan.exists(layout, p, x, z + 1, max) || !PulauAwan.exists(layout, p, x, z - 1, max);
				if (!rim || opening(layout, p, x, z)) {
					continue;
				}
				list.add(new int[]{x, PulauAwan.levelOf(layout, p, x, z), z});
			}
		}
		return list;
	}

	private static boolean opening(AwanLayout layout, AwanLayout.Pulau p, int x, int z) {
		for (AwanLayout.Jembatan b : layout.bridges) {
			if (b.covers(x, z, 2)) {
				return true;
			}
		}
		if (!p.main) {
			return false;
		}
		if (Math.abs(x) <= 3 && z > layout.cz) {
			return true;
		}
		for (AwanLayout.Titik lip : layout.takik) {
			if (Math.abs(x - lip.x()) + Math.abs(z - lip.z()) <= 2) {
				return true;
			}
		}
		for (AwanLayout.Dermaga dock : layout.docks) {
			if (x >= dock.deck().x0() - 1 && x <= dock.deck().x1() + 1 && z >= dock.deck().z0() - 1 && z <= dock.deck().z1() + 1) {
				return true;
			}
		}
		List<AwanLayout.Titik> spots = new ArrayList<>(layout.meditasi);
		spots.add(layout.panggung);
		spots.add(layout.halte);
		for (AwanLayout.Titik t : spots) {
			if (Math.abs(x - t.x()) <= 3 && Math.abs(z - t.z()) <= 3) {
				return true;
			}
		}
		AwanLayout.Box stair = layout.plot("tangga");
		return stair != null && stair.contains(x, z);
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		AwanLayout layout = AwanLayout.of(ctx.template());
		AwanLayout.Pulau p = PulauAwan.islandOf(layout, island);
		List<int[]> cells = cells(layout, p, ring);
		Phase previous = sink.phase();
		if (demolish) {
			sink.phase(Phase.FRAME);
			for (int[] c : cells) {
				sink.demolish(c[0], c[1], c[2]);
				sink.demolish(c[0], c[1] + 1, c[2]);
			}
			sink.phase(previous);
			return;
		}
		sink.phase(Phase.WALL);
		int n = 0;
		for (int[] c : cells) {
			int x = c[0];
			int y = c[1];
			int z = c[2];
			boolean post = Math.floorMod(x * 7 + z * 13, 5) == 0;
			switch (gaya) {
				case KAYU -> sink.set(x, y, z, Palette.st(AwanPalette.PAGAR_KAYU));
				case BATU -> {
					if (post) {
						sink.set(x, y, z, Palette.st(Blocks.QUARTZ_PILLAR));
						sink.set(x, y + 1, z, Palette.slab(AwanPalette.PAGAR_TUTUP, false));
					} else {
						sink.set(x, y, z, Palette.st(AwanPalette.PAGAR_BATU));
					}
				}
				default -> {
					if (post) {
						sink.set(x, y, z, Palette.st(Blocks.QUARTZ_PILLAR));
						boolean light = Math.floorMod(x * 3 + z * 5, 3) == 0;
						sink.set(x, y + 1, z, light ? Palette.lantern(Blocks.SOUL_LANTERN, false) : Palette.slab(AwanPalette.PAGAR_TUTUP, false));
					} else {
						sink.set(x, y, z, Palette.st(Blocks.DIORITE_WALL));
					}
				}
			}
			n++;
		}
		sink.phase(previous);
	}
}
