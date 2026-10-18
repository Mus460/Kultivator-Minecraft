package dev.kultivator.formasi.effect.a;

import dev.kultivator.cultivation.Meditation;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.formasi.FormasiFxIds;
import dev.kultivator.formasi.effect.CoreContext;
import dev.kultivator.formasi.effect.CoreContextImpl;
import dev.kultivator.formasi.effect.FormationEffect;
import dev.kultivator.formasi.own.Ownership;
import dev.kultivator.formasi.stack.FieldSink;
import dev.kultivator.formasi.spec.Params;
import net.minecraft.server.level.ServerPlayer;

/**
 * K2 Formasi Pengumpul Roh Kecil and B5 Formasi Pengumpul Roh Agung Sekte (DESIGN 24.4.2): members meditating inside get
 * the sect gather factor {@code 1 + base × P} (B5: 0.25 when the eye is at Y ≥ 120), the owner's disciples their own
 * meditation XP × the same factor (through the field), and the vein drain inside ×{@code drain} (B5 with a Murid Dalam+
 * keeper ×0.7). It never raises a vein. Standby: nobody of the sect cultivating inside.
 */
public final class GatherEffect implements FormationEffect {

	/** B5 gathers more when its eye stands this high (RMJ ch. 391). */
	public static final int HIGH_Y = 120;

	@Override
	public void field(CoreContext ctx, FieldSink sink) {
		Params params = ctx.spec().params();
		double base = params.get("gather", 0.10D);
		if (params.has("gather_high") && ctx.centre().y >= HIGH_Y) {
			base = params.get("gather_high");
		}
		sink.gather(1.0D + base * ctx.power());
		CoreContextImpl impl = (CoreContextImpl) ctx;
		double drain = params.get("drain", 1.0D);
		if (params.has("drain_keeper") && impl.record().keeperDalam && impl.record().keepersAtPost > 0) {
			drain = Math.min(drain, params.get("drain_keeper"));
		}
		sink.drain(drain);
	}

	@Override
	public void pulse(CoreContext ctx) {
		if (!idle(ctx)) {
			ctx.fx(FormasiFxIds.PULSE, ctx.centre(), ctx.spec().family().ordinal(), (float) Math.min(48.0D, ctx.radius()));
		}
	}

	@Override
	public boolean idle(CoreContext ctx) {
		return !anyoneCultivating((CoreContextImpl) ctx);
	}

	/** A member meditating inside, or one of the owner's disciples meditating inside. */
	static boolean anyoneCultivating(CoreContextImpl ctx) {
		for (ServerPlayer player : ctx.level().players()) {
			if (Meditation.isMeditating(player) && Ownership.isMember(ctx.level().getServer(), ctx.owner(), player) && ctx.covers(player.position())) {
				return true;
			}
		}
		for (DiscipleEntity disciple : Disciples.loaded(ctx.owner())) {
			if (disciple.level() == ctx.level() && disciple.isMeditatingNow() && ctx.covers(disciple.position())) {
				return true;
			}
		}
		return false;
	}
}
