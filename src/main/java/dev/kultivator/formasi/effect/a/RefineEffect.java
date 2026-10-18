package dev.kultivator.formasi.effect.a;

import dev.kultivator.formasi.FormasiFxIds;
import dev.kultivator.formasi.effect.CoreContext;
import dev.kultivator.formasi.effect.CoreContextImpl;
import dev.kultivator.formasi.effect.FormationEffect;
import dev.kultivator.formasi.spec.Params;
import dev.kultivator.formasi.stack.FieldSink;

/**
 * K3 Formasi Api Bumi and B7 Formasi Api Paru Bumi (DESIGN 24.4.4): furnaces in range get success {@code +chance × P},
 * quality steps and a shorter refining; inscriptions by members in range {@code +inscribe × P}. B7 is the upgrade on the
 * same eye; the two never stack (the strongest per furnace counts, 24.5). Standby: no furnace and no inscription asked
 * for the bonus in the last 15 s.
 */
public final class RefineEffect implements FormationEffect {

	/** A furnace or inscription that asked within this many ticks keeps the core out of standby. */
	public static final int USE_WINDOW = 300;

	@Override
	public void field(CoreContext ctx, FieldSink sink) {
		Params params = ctx.spec().params();
		sink.refine(params.get("chance") * ctx.power(), params.getInt("steps", 0), params.get("time", 1.0D), params.get("inscribe") * ctx.power());
	}

	@Override
	public void pulse(CoreContext ctx) {
		if (!idle(ctx)) {
			ctx.fx(FormasiFxIds.PULSE, ctx.centre(), ctx.spec().family().ordinal(), (float) ctx.radius());
		}
	}

	@Override
	public boolean idle(CoreContext ctx) {
		return ctx.now() - ((CoreContextImpl) ctx).record().lastUse > USE_WINDOW;
	}
}
