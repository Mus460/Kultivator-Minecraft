package dev.kultivator.formasi.effect.a;

import dev.kultivator.cultivation.Deviation;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.formasi.FormasiFxIds;
import dev.kultivator.formasi.effect.CoreContext;
import dev.kultivator.formasi.effect.CoreContextImpl;
import dev.kultivator.formasi.effect.FormationEffect;
import dev.kultivator.formasi.own.Ownership;
import dev.kultivator.formasi.stack.FieldSink;
import dev.kultivator.formasi.stack.Stacking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * M4 Formasi Pemelihara Roh (DESIGN 24.4.3): every 100 ticks, members, their pets and spirit beasts inside get
 * Regeneration I for 6 s (II from hall level 7 or with a Life/Wood keeper; players at most amplifier 1); disciples heal
 * through {@code DiscipleHooks.addRegenFactor}. Members' qi regeneration ×(1 + 0.3 × P) and the Qi Deviation timer ×1.33.
 * Hostiles are never touched; the effect pauses during the target's own tribulation. <i>Terinspirasi dari</i> RMJ:
 * Immortal Realm ch. 216 (a gathering array around a huge white egg); the healing numbers are game design.
 */
public final class HealEffect implements FormationEffect {

	public static final int REGEN_TICKS = 120;
	public static final int STRONG_HALL_LEVEL = 7;
	public static final double QI_REGEN = 0.3D;
	public static final double DEVIATION = 1.33D;

	static int amplifier(CoreContextImpl ctx) {
		return ctx.hallLevel() >= STRONG_HALL_LEVEL || (ctx.record().keeperAffinity && ctx.record().keepersAtPost > 0) ? 1 : 0;
	}

	@Override
	public void pulse(CoreContext ctx) {
		CoreContextImpl impl = (CoreContextImpl) ctx;
		int amp = Math.min(Stacking.HEAL_AMP_CAP, amplifier(impl));
		boolean any = false;
		for (LivingEntity ally : ctx.allies(16)) {
			if (ally instanceof DiscipleEntity) {
				continue;
			}
			if (ally instanceof ServerPlayer player && Tribulation.isActive(player)) {
				continue;
			}
			ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_TICKS, amp, true, true));
			any = true;
		}
		if (any) {
			ctx.fx(FormasiFxIds.PULSE, ctx.centre(), ctx.spec().family().ordinal(), (float) Math.min(48.0D, ctx.radius()));
		}
	}

	@Override
	public void field(CoreContext ctx, FieldSink sink) {
		CoreContextImpl impl = (CoreContextImpl) ctx;
		Entity subject = impl.subject();
		if (subject instanceof ServerPlayer player && Tribulation.isActive(player)) {
			return;
		}
		sink.heal(amplifier(impl), 1.0D + QI_REGEN * ctx.power(), DEVIATION);
	}

	@Override
	public boolean idle(CoreContext ctx) {
		for (LivingEntity ally : ctx.allies(16)) {
			if (ally.getHealth() < ally.getMaxHealth() || Ownership.spiritBeast(ally)) {
				return false;
			}
			if (ally instanceof ServerPlayer player && Deviation.ticksLeft(player) > 0) {
				return false;
			}
		}
		return true;
	}
}
