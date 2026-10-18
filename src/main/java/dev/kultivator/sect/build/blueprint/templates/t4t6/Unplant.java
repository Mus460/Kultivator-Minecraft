package dev.kultivator.sect.build.blueprint.templates.t4t6;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Sink;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Takes back this component's own plans for the standing cells of a rectangle (templates 4-6): a library
 * {@code Terrace} plants azaleas and ferns along its whole foot, and where that foot runs beside an older pond or stream
 * those plants would stand over the water. Add it <b>after</b> the part that planted them, in the same component; it
 * places nothing and only forgets cells this component planned at {@code level} (the world keeps what it has there).
 * Template-local.
 */
public final class Unplant implements Part {

	private final int x0;
	private final int z0;
	private final int x1;
	private final int z1;
	private final Lv level;

	public Unplant(int x0, int z0, int x1, int z1, Lv level) {
		this.x0 = Math.min(x0, x1);
		this.z0 = Math.min(z0, z1);
		this.x1 = Math.max(x0, x1);
		this.z1 = Math.max(z0, z1);
		this.level = level;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		return new BoundingBox(x0, y, z0, x1, y, z1);
	}

	@Override
	public String kind() {
		return "unplant";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int y = ctx.level(level);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				sink.forget(x, y, z);
			}
		}
	}
}
