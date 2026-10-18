package dev.kultivator.formasi.effect.a;

import dev.kultivator.formasi.FormasiB;
import dev.kultivator.formasi.FormasiFxIds;
import dev.kultivator.formasi.core.CoreRecord;
import dev.kultivator.formation.Formation;
import dev.kultivator.formation.FormationData;
import dev.kultivator.formasi.effect.CoreContext;
import dev.kultivator.formasi.effect.CoreContextImpl;
import dev.kultivator.formasi.effect.FormationEffect;
import dev.kultivator.formasi.stack.FieldSink;
import dev.kultivator.sect.hall.GrandFormation;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.technique.spec.run.ExternalControl;
import dev.kultivator.util.Advancements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * L1 Perisai Larangan Lima Lapis (DESIGN 24.4.1): its own layers on top of the Formasi Agung Sekte, which it never edits.
 * Lapis 5 (always): hostile-owned projectiles that cross the shell inward are discarded ({@code ProjectileTracker}).
 * Lapis 6 (a keeper at its post or A1 complete): intruders get Weakness I for 3 s every 40 ticks and creepers are held
 * through {@code ExternalControl}, so they never explode inside. While it runs, the Agung's lodestone does not drain
 * amethyst. Needs the hall's active Agung and the eye within 32 blocks of the stele. Sumber: ISSTH Book 5 ch. 735, RMJ
 * ch. 346, AWE ch. 877.
 */
public final class ShieldEffect implements FormationEffect {

	public static final double STELE_RANGE = 32.0D;
	public static final int WEAKNESS_TICKS = 60;
	public static final int CREEPER_HOLD = 60;
	/** Standby after this many ticks without an intruder or a tracked projectile. */
	public static final int QUIET_TICKS = 1200;

	/** True when Lapis Inti runs. */
	public static boolean inner(CoreContextImpl ctx) {
		return ctx.record().keepersAtPost > 0 || FormasiB.grandComplete(ctx.owner());
	}

	@Override
	public void pulse(CoreContext ctx) {
		CoreContextImpl impl = (CoreContextImpl) ctx;
		boolean inner = inner(impl);
		boolean seen = false;
		for (Mob mob : ctx.hostiles(24)) {
			seen = true;
			if (!inner) {
				continue;
			}
			mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS, 0, true, true));
			if (mob instanceof Creeper creeper) {
				ExternalControl.root(creeper, CREEPER_HOLD, CREEPER_HOLD);
			}
		}
		if (seen) {
			activity(impl.record(), ctx.now());
			ctx.fx(FormasiFxIds.RIPPLE, ctx.centre(), ctx.spec().family().ordinal(), (float) ctx.radius());
		}
		if (inner) {
			ServerPlayer owner = ctx.ownerOnline();
			if (owner != null) {
				Advancements.grant(owner, "five_layer_shield");
			}
		}
	}

	@Override
	public void field(CoreContext ctx, FieldSink sink) {
		sink.layers(inner((CoreContextImpl) ctx) ? 2 : 1);
	}

	@Override
	public boolean idle(CoreContext ctx) {
		return ctx.now() - ((CoreContextImpl) ctx).record().scratch.getLongOr("act", -100_000L) > QUIET_TICKS;
	}

	@Override
	public @Nullable Component blocker(CoreContext ctx) {
		CoreContextImpl impl = (CoreContextImpl) ctx;
		if (impl.record().virtual && impl.record().forced) {
			return null;
		}
		Halls.HallView hall = Halls.hallOf(ctx.owner());
		if (hall == null || !hall.active() || !ctx.level().dimension().equals(hall.dim())
				|| Vec3.atCenterOf(hall.pos()).distanceTo(ctx.centre()) > STELE_RANGE) {
			return Component.translatable("kultivator.formasi.reason.shield_stele", (int) STELE_RANGE);
		}
		return agung(ctx.level(), ctx.owner()) == null ? Component.translatable("kultivator.formasi.reason.shield_agung") : null;
	}

	@Override
	public void sync(CoreContext ctx, CompoundTag out) {
		out.putBoolean("inner", inner((CoreContextImpl) ctx));
	}

	/** Marks shield activity (an intruder or a discarded projectile). */
	public static void activity(CoreRecord record, long now) {
		record.scratch.putLong("act", now);
	}

	/** The owner's active Formasi Agung Sekte in {@code level}, or null. */
	public static @Nullable Formation agung(ServerLevel level, UUID owner) {
		FormationData data = FormationData.get(level);
		if (data.isEmpty()) {
			return null;
		}
		for (Formation formation : data.all()) {
			if (formation.active() && formation.owner().equals(owner)
					&& GrandFormation.state(level, formation, level.getServer().getTickCount()).grand()) {
				return formation;
			}
		}
		return null;
	}

	/** The layers of the owner's Agung (0 without one). */
	public static int agungLayers(ServerLevel level, UUID owner) {
		Formation formation = agung(level, owner);
		return formation == null ? 0 : GrandFormation.state(level, formation, level.getServer().getTickCount()).layers();
	}
}
