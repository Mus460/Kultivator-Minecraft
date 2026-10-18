package dev.kultivator.sect.build.blueprint.templates.t4t6;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Sink;
import dev.kultivator.sect.build.blueprint.StairFlight;
import dev.kultivator.sect.build.blueprint.Terrace;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A {@link StairFlight} anchored at its <b>top</b> cell (templates 4-6): the last step lies at template-local (x, z), next
 * to the upper level, and the foot moves out along the opposite of {@code toward} as far as the fitted rise needs. Used
 * where the upper or lower level is a terrain-fitted zone, so the flight always meets the terrace edge whatever level the
 * fitter chose (a flight anchored at its foot would leave a gap or run into the terrace). Nothing is emitted when both
 * levels are equal.
 */
public final class Flight implements Part {

	private final int x;
	private final int z;
	private final Direction toward;
	private final Lv from;
	private final Lv to;
	private int width = 3;
	private Terrace.Style style = Terrace.Style.STONE;
	private int ramp;
	private int landingEvery = 4;

	/** The top step at (x, z); the flight climbs {@code toward} from level {@code from} up to level {@code to}. */
	public Flight(int x, int z, Direction toward, Lv from, Lv to) {
		this.x = x;
		this.z = z;
		this.toward = toward.getAxis().isHorizontal() ? toward : Direction.NORTH;
		this.from = from;
		this.to = to;
	}

	public Flight width(int width) {
		this.width = width;
		return this;
	}

	public Flight style(Terrace.Style style) {
		this.style = style;
		return this;
	}

	public Flight ramp(int ramp) {
		this.ramp = ramp;
		return this;
	}

	public Flight landingEvery(int steps) {
		this.landingEvery = Math.max(2, steps);
		return this;
	}

	private int rise(Ctx ctx) {
		return Math.abs(ctx.level(to) - ctx.level(from));
	}

	private StairFlight flight(Ctx ctx) {
		int rise = rise(ctx);
		int run = Math.max(1, rise + Math.max(0, (rise - 1) / landingEvery));
		int fx = x - toward.getStepX() * (run - 1);
		int fz = z - toward.getStepZ() * (run - 1);
		return new StairFlight(fx, fz, toward, from, to).width(width).style(style).ramp(ramp).landingEvery(landingEvery);
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		if (rise(ctx) == 0) {
			int y = ctx.level(from);
			return new BoundingBox(x, y - 1, z, x, y, z);
		}
		return flight(ctx).bounds(ctx);
	}

	@Override
	public String kind() {
		return "stairs";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		if (rise(ctx) != 0) {
			flight(ctx).emit(ctx, sink);
		}
	}
}
