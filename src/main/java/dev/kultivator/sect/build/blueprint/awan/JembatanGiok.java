package dev.kultivator.sect.build.blueprint.awan;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A jade bridge between two islands (DESIGN 37.4.3 {@code JembatanGiok}): a 3-wide deck of jade (waxed oxidised cut
 * copper) slabs, level until it has cleared the outer terraces, then descending or rising 1:2 in half steps; pale-oak
 * rails (swapped for jade lattice at T6, {@link #kisi}); quartz-pillar posts every six cells with a soul lantern hanging
 * under the deck, and from 17 cells on the chained style ({@code RANTAI}): gate posts with tiled caps at both ends and
 * jade chains hanging from the posts. It opens the rim fence where it lands (light swap) and keeps clear of the deck it
 * crosses. Template-local, axis-aligned (the layout lays every bridge along x or z).
 */
public final class JembatanGiok implements Part {

	private final String fungsi;
	private boolean lattice;

	/** The bridge of satellite {@code fungsi} (its {@link AwanLayout.Jembatan}). */
	public JembatanGiok(String fungsi) {
		this.fungsi = fungsi;
	}

	/** The T6 swap: only the rails, as jade lattice (copper bars), over the old fence rails. */
	public JembatanGiok kisi() {
		this.lattice = true;
		return this;
	}

	private AwanLayout.Jembatan bridge(Ctx ctx) {
		AwanLayout layout = AwanLayout.of(ctx.template());
		for (AwanLayout.Jembatan b : layout.bridges) {
			if (b.id().equals("jembatan_" + fungsi)) {
				return b;
			}
		}
		return null;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		AwanLayout.Jembatan b = bridge(ctx);
		if (b == null) {
			return new BoundingBox(0, 0, 0, 0, 0, 0);
		}
		int lo = Math.min(b.levelA(), b.levelB());
		int hi = Math.max(b.levelA(), b.levelB());
		return new BoundingBox(Math.min(b.xA(), b.xB()) - 2, lo - 3, Math.min(b.zA(), b.zB()) - 2, Math.max(b.xA(), b.xB()) + 2, hi + 5,
				Math.max(b.zA(), b.zB()) + 2);
	}

	@Override
	public String kind() {
		return "bridge";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		AwanLayout.Jembatan b = bridge(ctx);
		if (b == null) {
			return;
		}
		int length = b.length();
		int dx = Integer.signum(b.xB() - b.xA());
		int dz = Integer.signum(b.zB() - b.zA());
		// The side vector (across the deck).
		int sx = dz != 0 ? 1 : 0;
		int sz = dx != 0 ? 1 : 0;
		boolean chained = length > 16;
		Phase previous = sink.phase();
		// Bridges built from T6 on get the jade lattice straight away (the earlier ones are swapped at T6).
		BlockState rail = lattice || b.tier() >= 6 ? Palette.st(Palette.jade(Blocks.COPPER_BARS)) : Palette.st(AwanPalette.PAGAR_KAYU);
		if (lattice) {
			sink.phase(Phase.WALL);
			for (int i = 1; i < length; i++) {
				int x = b.xA() + dx * i;
				int z = b.zA() + dz * i;
				if (i % 6 == 0) {
					continue;
				}
				int y = standY(b, i);
				for (int side : new int[]{-2, 2}) {
					sink.set(x + sx * side, y, z + sz * side, rail);
				}
			}
			sink.phase(previous);
			return;
		}
		// The landings: open the rim fences (and their caps) the deck passes through.
		sink.phase(Phase.FRAME);
		for (int end : new int[]{0, length}) {
			int x = b.xA() + dx * end;
			int z = b.zA() + dz * end;
			int y = Math.floorDiv(b.halfBlocks(end), 2);
			for (int w = -2; w <= 2; w++) {
				// The rail columns start at the landing cell: one further in is the island's own ground (its lanterns).
				for (int k = Math.abs(w) == 2 ? 0 : -1; k <= 2; k++) {
					int ax = x + sx * w - dx * (end == 0 ? -k : k);
					int az = z + sz * w - dz * (end == 0 ? -k : k);
					sink.demolish(ax, y, az);
					sink.demolish(ax, y + 1, az);
				}
			}
		}
		// The deck: a full quartz block on the centre line where it is level (lanterns hang under it), jade slabs beside;
		// all bottom slabs on a half step.
		sink.phase(Phase.PLINTH);
		for (int i = 0; i <= length; i++) {
			int x = b.xA() + dx * i;
			int z = b.zA() + dz * i;
			int hb = b.halfBlocks(i);
			boolean half = (hb & 1) != 0;
			int y = half ? (hb - 1) / 2 : hb / 2 - 1;
			for (int w = -1; w <= 1; w++) {
				BlockState state = half ? AwanPalette.giokLantai(false) : w == 0 ? Palette.st(Blocks.SMOOTH_QUARTZ) : AwanPalette.giokLantai(true);
				sink.set(x + sx * w, y, z + sz * w, state);
			}
			if (!half) {
				// The keel: stone-brick slabs under the outer edges (the deck reads as a bridge from below).
				for (int w : new int[]{-1, 1}) {
					sink.setIfAbsent(x + sx * w, y - 1, z + sz * w, Palette.slab(Blocks.STONE_BRICK_SLAB, true));
				}
			}
		}
		// Rails and posts.
		sink.phase(Phase.WALL);
		for (int i = 1; i < length; i++) {
			int x = b.xA() + dx * i;
			int z = b.zA() + dz * i;
			int y = standY(b, i);
			boolean post = i % 6 == 0;
			for (int side : new int[]{-2, 2}) {
				int px = x + sx * side;
				int pz = z + sz * side;
				// The rail stands on the deck's edge cell row extended one out: give it a footing slab.
				sink.set(px, y - 1, pz, Palette.slab(Blocks.SMOOTH_QUARTZ_SLAB, true));
				if (post) {
					sink.set(px, y, pz, Palette.st(Blocks.QUARTZ_PILLAR));
					sink.set(px, y + 1, pz, Palette.slab(AwanPalette.PAGAR_TUTUP, false));
				} else {
					sink.set(px, y, pz, rail);
				}
			}
		}
		// Lights: a soul lantern hanging under the deck at every post, and the chained style's gate posts and chains.
		sink.phase(Phase.ORNAMENT);
		for (int i = 6; i < length; i += 6) {
			int x = b.xA() + dx * i;
			int z = b.zA() + dz * i;
			int hb = b.halfBlocks(i);
			int under = (hb & 1) != 0 ? (hb - 1) / 2 - 1 : hb / 2 - 2;
			sink.setIfAbsent(x, under, z, Palette.lantern(Blocks.SOUL_LANTERN, true));
		}
		if (chained) {
			for (int end : new int[]{1, length - 1}) {
				int x = b.xA() + dx * end;
				int z = b.zA() + dz * end;
				int y = standY(b, end);
				for (int side : new int[]{-2, 2}) {
					int px = x + sx * side;
					int pz = z + sz * side;
					for (int h = 0; h < 4; h++) {
						sink.set(px, y + h, pz, Palette.st(Blocks.QUARTZ_PILLAR));
					}
					sink.set(px, y + 4, pz, Palette.st(Palette.jadeBlock()));
					sink.set(px, y + 5, pz, Palette.slab(Palette.jadeSlab(), false));
				}
				// A lintel with a hanging lantern between the two gate posts.
				for (int w = -1; w <= 1; w++) {
					sink.set(x + sx * w, y + 4, z + sz * w, Palette.st(Palette.BEAM));
				}
				sink.set(x, y + 3, z, Palette.lantern(Blocks.SOUL_LANTERN, true));
			}
			// Jade chains hanging from the post caps (vertical, decorative).
			for (int i = 12; i < length - 6; i += 12) {
				int x = b.xA() + dx * i;
				int z = b.zA() + dz * i;
				int y = standY(b, i);
				for (int side : new int[]{-2, 2}) {
					int px = x + sx * side;
					int pz = z + sz * side;
					for (int h = 1; h <= 3; h++) {
						sink.setIfAbsent(px, y - 1 - h, pz, AwanPalette.giokRantai());
					}
				}
			}
		}
		sink.phase(previous);
	}

	/** The cell a person's feet are in at step i (the rail cell). */
	private static int standY(AwanLayout.Jembatan b, int i) {
		return Math.floorDiv(b.halfBlocks(i), 2);
	}

	/** The template-local facing along the deck (for parts that need it). */
	static Direction along(AwanLayout.Jembatan b) {
		if (b.alongX()) {
			return b.xB() > b.xA() ? Direction.EAST : Direction.WEST;
		}
		return b.zB() > b.zA() ? Direction.SOUTH : Direction.NORTH;
	}
}
