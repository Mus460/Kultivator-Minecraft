package dev.kultivator.sect.build.blueprint.templates.t7t9;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Random;

/**
 * A solid citadel bastion (template 8, DESIGN 21.3 T5 "three-storey corner towers", 21.5 "Template 8 citadel"): a square
 * block of deepslate bricks with about 20% cracked bricks, a chiseled band near the top and a polished deepslate deck, so
 * a two-storey tower hall set on it reads as a three-storey corner tower. Template-local, centred on (x, z), rising
 * {@code height} from {@code level} (the deck is the
 * standing level {@code level + height}); its footing reaches down to the natural ground.
 */
public final class Bastion implements Part {

	private final int x;
	private final int z;
	private final int half;
	private final Lv level;
	private final int height;

	public Bastion(int x, int z, int half, Lv level, int height) {
		this.x = x;
		this.z = z;
		this.half = Math.max(2, half);
		this.level = level;
		this.height = Math.max(3, height);
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		return new BoundingBox(x - half, y - 2, z - half, x + half, y + height + 1, z + half);
	}

	@Override
	public String kind() {
		return "bastion";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int y0 = ctx.level(level);
		Random random = ctx.random("bastion" + x + "," + z);
		Phase previous = sink.phase();
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				int ground = ctx.terrain().ground(x + dx, z + dz);
				boolean face = Math.abs(dx) == half || Math.abs(dz) == half;
				sink.phase(Phase.CLEAR);
				for (int y = Math.max(ground + 1, y0); y <= Math.min(ctx.terrain().top(x + dx, z + dz), y0 + height + 4); y++) {
					sink.air(x + dx, y, z + dz);
				}
				sink.phase(Phase.PLINTH);
				for (int y = Math.max(ground + 1, y0 - 6); y < y0 + height - 1; y++) {
					if (!face) {
						sink.set(x + dx, y, z + dz, Palette.st(Palette.FILL_STONE));
						continue;
					}
					boolean band = y == y0 + height - 3;
					sink.set(x + dx, y, z + dz, Palette.st(band ? Blocks.CHISELED_DEEPSLATE
							: random.nextInt(5) == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS : Blocks.DEEPSLATE_BRICKS));
				}
				sink.set(x + dx, y0 + height - 1, z + dz, Palette.st(face ? Blocks.DEEPSLATE_BRICKS : Blocks.POLISHED_DEEPSLATE));
			}
		}
		sink.phase(previous);
	}
}
