package dev.kultivator.formasi.effect.a;

import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.formasi.FormasiFxIds;
import dev.kultivator.formasi.effect.CoreContext;
import dev.kultivator.formasi.effect.CoreContextImpl;
import dev.kultivator.formasi.effect.FormationEffect;
import dev.kultivator.formasi.own.Ownership;
import dev.kultivator.formasi.stack.FieldSink;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * B6 Formasi Jaring Petir Emas (DESIGN 24.4.5): during their own strikes, members inside get ×0.90 (×0.85 with a keeper
 * at its post, who is Inti Emas or higher, or with Lightning affinity); disciples' calamity strikes inside get the same.
 * All formations together never go below {@code TRIB_FORMATION_FLOOR} (the bridge applies it). Standby except during a
 * member's omen or strikes inside. <i>Terinspirasi dari</i> RMJ ch. 1468 (a golden net devours the tribulation
 * lightning).
 */
public final class WardEffect implements FormationEffect {

	/** The golden net opens this high over the eye. */
	public static final double NET_HEIGHT = 20.0D;

	@Override
	public void field(CoreContext ctx, FieldSink sink) {
		CoreContextImpl impl = (CoreContextImpl) ctx;
		boolean strong = impl.record().keepersAtPost > 0 || impl.record().keeperAffinity;
		sink.tribulation(strong ? ctx.spec().params().get("strong", 0.85D) : ctx.spec().params().get("factor", 0.90D));
	}

	@Override
	public void pulse(CoreContext ctx) {
		if (!idle(ctx)) {
			Vec3 net = ctx.centre().add(0.0D, NET_HEIGHT, 0.0D);
			ctx.fx(FormasiFxIds.PULSE, net, ctx.spec().family().ordinal(), (float) ctx.radius());
		}
	}

	@Override
	public boolean idle(CoreContext ctx) {
		CoreContextImpl impl = (CoreContextImpl) ctx;
		for (ServerPlayer player : ctx.level().players()) {
			if (Tribulation.isActive(player) && Ownership.isMember(ctx.level().getServer(), ctx.owner(), player) && impl.covers(player.position())) {
				return false;
			}
		}
		for (DiscipleEntity disciple : Disciples.loaded(ctx.owner())) {
			if (disciple.level() == ctx.level() && (disciple.syncedFlags() & DiscipleEntity.FLAG_BREAKING_THROUGH) != 0
					&& impl.covers(disciple.position())) {
				return false;
			}
		}
		return true;
	}
}
