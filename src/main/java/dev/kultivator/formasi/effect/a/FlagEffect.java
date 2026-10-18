package dev.kultivator.formasi.effect.a;

import dev.kultivator.formasi.FormasiFxIds;
import dev.kultivator.formasi.core.CoreRecord;
import dev.kultivator.formasi.core.CoreTicker;
import dev.kultivator.formasi.effect.CoreContext;
import dev.kultivator.formasi.effect.CoreContextImpl;
import dev.kultivator.formasi.effect.FormationEffect;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * L6 Formasi Panji Larangan (DESIGN 24.4.6): amplifies the nearest registered 7c core of the same owner whose eye lies
 * within 8 blocks (never an Abadi array or another flag; one flag per target): P +0.25, radius +2, upkeep ×1.10, and
 * "Mengisi Retakan": a target with exactly one missing block keeps working at P × 0.5 for 10 min (20 with a keeper at the
 * flag). The pairing is made once per second by the ticker; the block itself is never restored. Sumber: <i>Renegade
 * Immortal</i> ch. 291, 527, 233.
 */
public final class FlagEffect implements FormationEffect {

	@Override
	public void pulse(CoreContext ctx) {
		CoreContextImpl impl = (CoreContextImpl) ctx;
		CoreRecord target = target(impl);
		if (target != null) {
			Fx.send(ctx.level(), ctx.centre().add(0.0D, 1.5D, 0.0D), Vec3.atCenterOf(target.eye), FxType.FORMASI, FormasiFxIds.PULSE,
					ctx.spec().family().ordinal(), 1.0F);
		}
	}

	@Override
	public boolean idle(CoreContext ctx) {
		return target((CoreContextImpl) ctx) == null;
	}

	@Override
	public void sync(CoreContext ctx, CompoundTag out) {
		CoreRecord target = target((CoreContextImpl) ctx);
		if (target != null) {
			out.putInt("tgt", target.id);
		}
	}

	private static CoreRecord target(CoreContextImpl ctx) {
		int id = ctx.record().flagTarget;
		return id < 0 ? null : CoreTicker.store(ctx.level()).byId(id);
	}
}
