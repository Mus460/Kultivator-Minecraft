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
 * The hanging crystals of T9 (DESIGN 37.4.2 step 7): clusters under the main island's belly in the Liangfeng band, each
 * a calcite collar against the shell, then a tapering crystal 3-7 long (3×3, 2×2, 1×1 sections) of tinted glass round an
 * amethyst spine (one exact amethyst block per layer), large buds on the flanks, an amethyst cluster at the tip pointing
 * down, and an end rod under the largest one (its light). Never budding amethyst (it would grow).
 */
public final class KristalGantung implements Part {

	private final int count;

	public KristalGantung(int count) {
		this.count = count;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		AwanLayout layout = AwanLayout.of(ctx.template());
		AwanLayout.Pulau p = layout.main;
		int reach = (int) Math.ceil(p.radius()) + 1;
		return new BoundingBox(p.cx - reach, p.deepest() - 10, p.cz - reach, p.cx + reach, -2, p.cz + reach);
	}

	@Override
	public String kind() {
		return "crystals";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		AwanLayout layout = AwanLayout.of(ctx.template());
		AwanLayout.Pulau p = layout.main;
		Phase previous = sink.phase();
		sink.phase(Phase.PLINTH);
		for (int i = 0; i < count; i++) {
			double theta = (i + 0.37D) / count * Math.PI * 2.0D + p.shape.roll(i, 0, 0, 31) * 0.4D;
			double n = 0.28D + 0.42D * p.shape.roll(i, 1, 0, 32);
			int x = p.cx + (int) Math.round(Math.sin(theta) * n * p.radius());
			int z = p.cz + (int) Math.round(Math.cos(theta) * n * p.radius());
			if (!PulauAwan.exists(layout, p, x, z, 3)) {
				continue;
			}
			int top = PulauAwan.levelOf(layout, p, x, z) - 1;
			int bottom = PulauAwan.bottomOf(p, x, z, top);
			int length = 3 + (int) Math.round(4.0D * (1.0D - (double) i / Math.max(1, count)));
			crystal(sink, x, bottom - 1, z, length, i == 0, p.shape, i);
		}
		sink.phase(previous);
	}

	private static void crystal(Sink sink, int x, int y, int z, int length, boolean largest, AwanShape shape, int salt) {
		BlockState collar = Palette.st(Blocks.CALCITE);
		BlockState glass = Palette.st(AwanPalette.KRISTAL_KACA);
		BlockState spine = Palette.st(AwanPalette.KRISTAL);
		// The collar against the shell: a plus-shaped calcite ring.
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) + Math.abs(dz) <= 3 && Math.max(Math.abs(dx), Math.abs(dz)) == 2) {
					sink.set(x + dx, y, z + dz, collar);
				}
			}
		}
		int third = Math.max(1, length / 3);
		for (int i = 0; i < length; i++) {
			int cy = y - i;
			if (i < third) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						sink.set(x + dx, cy, z + dz, dx == 0 && dz == 0 ? spine : glass);
					}
				}
				// Buds on the flanks.
				for (Direction d : Direction.Plane.HORIZONTAL) {
					if (shape.roll(x, cy, z, salt * 7 + d.ordinal()) < 0.35D) {
						sink.setIfAbsent(x + 2 * d.getStepX(), cy, z + 2 * d.getStepZ(), AwanPalette.kristalUjung(AwanPalette.KRISTAL_TUNAS, d));
					}
				}
			} else if (i < 2 * third) {
				sink.set(x, cy, z, spine);
				sink.set(x + 1, cy, z, glass);
				sink.set(x, cy, z + 1, glass);
				sink.set(x + 1, cy, z + 1, glass);
			} else {
				sink.set(x, cy, z, spine);
			}
		}
		int tip = y - length;
		if (largest) {
			sink.set(x, tip, z, Palette.facing(Blocks.END_ROD, Direction.DOWN));
		} else {
			sink.set(x, tip, z, AwanPalette.kristalUjung(AwanPalette.KRISTAL_UJUNG, Direction.DOWN));
		}
	}
}
