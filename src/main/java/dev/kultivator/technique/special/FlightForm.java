package dev.kultivator.technique.special;

import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.technique.FlightLease;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Bonus;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.run.CastContext;
import dev.kultivator.technique.spec.run.EndReason;
import dev.kultivator.technique.spec.run.SpecialTechnique;
import dev.kultivator.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@code flight_form} (DESIGN 22.2.7): the real flight of {@code lei_zhenzi_wings} (Sayap Lei Zhenzi). Flight is never
 * granted here directly: the handler takes the shared {@link FlightLease} (owned by {@code FlyingSword}), so the Flying
 * Sword and the wings never revoke each other's {@code mayfly}. The form's buffs, the melee hook and the stage-2 dive
 * are timeline steps of the spec, which the runner schedules exactly once (stage 1 when the cast starts, the dive on the
 * recast); the handler adds the flight, pays {@link #QI_PER_SECOND} qi per second (reduced by the realm
 * gap and mastery like any qi cost) and gives the flight back when the cast ends for any reason (the form ending ends
 * it), out of qi, or when the form's time is over. A player who lands this way in mid-air gets Slow Falling. Players
 * only (never taught to disciples: {@code supportsDisciples} stays false).
 */
public final class FlightForm implements SpecialTechnique {

	public static final String ID = "flight_form";
	/** The lease source (the spec id). */
	public static final String SOURCE = "lei_zhenzi_wings";
	/** The TRANSFORM form id of the wings. */
	public static final String FORM = "lei_zhenzi_wings";
	/** TRANSFORM 400t. */
	public static final int FORM_TICKS = 400;
	/** "qi 250 + 3/s". */
	public static final double QI_PER_SECOND = 3.0D;
	public static final int SLOW_FALLING_TICKS = 160;
	/** The flight never outlasts the form by more than this, even if the form's end is never reported. */
	public static final int GRACE_TICKS = 20;
	/** Upward speed that lifts a grounded player into flight (as the Flying Sword does). */
	private static final double LIFT_OFF = 0.45D;
	private static final String KEY_MAX = "flight_max";

	/** The lease each flying player holds through this handler (session only). */
	private static final Map<UUID, FlightLease> LEASES = new HashMap<>();

	@Override
	public @Nullable Component blocker(CastContext ctx) {
		ServerPlayer player = ctx.player();
		if (player == null) {
			return Component.translatable("kultivator.technique.lei_zhenzi_wings.player_only");
		}
		if (ctx.stage() >= 2) {
			// The recast dash: the flight of stage 1 simply continues.
			return null;
		}
		if (player.isPassenger()) {
			return Component.translatable("kultivator.technique.lei_zhenzi_wings.riding");
		}
		FlightLease holder = FlightLease.holder(player);
		if (holder != null && !SOURCE.equals(holder.source())) {
			return Component.translatable("kultivator.technique.lei_zhenzi_wings.busy");
		}
		return null;
	}

	@Override
	public boolean cast(CastContext ctx) {
		ServerPlayer player = ctx.player();
		if (player == null) {
			return false;
		}
		if (ctx.stage() >= 2) {
			// The recast: the runner schedules that stage's steps (the dive); the flight of stage 1 simply continues.
			return true;
		}
		FlightLease lease = FlightLease.holder(player);
		if (lease == null || !SOURCE.equals(lease.source())) {
			lease = FlightLease.acquire(player, SOURCE);
		}
		if (lease == null) {
			Msg.actionBar(player, Component.translatable("kultivator.technique.lei_zhenzi_wings.unavailable"));
			return false;
		}
		LEASES.put(player.getUUID(), lease);
		ctx.state().putInt(KEY_MAX, maxTicks(ctx));
		liftOff(player);
		// The handler sends its own CAST event (which also plays the CAST sounds); the runner schedules the stage-1 steps
		// once when the cast starts: the form with its staff buffs and the melee hook. The form keeps the cast (and so the
		// lease) alive.
		ctx.fx(Phase.CAST, player.position(), ctx.aim(), 1.0F, 0.0F, 1, FORM_TICKS);
		return true;
	}

	@Override
	public void tick(CastContext ctx) {
		ServerPlayer player = ctx.player();
		if (player == null) {
			return;
		}
		FlightLease lease = LEASES.get(player.getUUID());
		if (lease == null) {
			return;
		}
		if (FlightLease.holder(player) != lease) {
			// Released or taken over elsewhere (a game mode change, the lease owner's own rules): nothing left to give back.
			LEASES.remove(player.getUUID());
			return;
		}
		int age = ctx.age();
		if (age >= ctx.state().getIntOr(KEY_MAX, FORM_TICKS + GRACE_TICKS)) {
			land(player, true);
			return;
		}
		if (age > 0 && age % 20 == 0 && !ctx.options().free()) {
			double cost = QI_PER_SECOND * Math.max(0.35D, 1.0D - 0.13D * ctx.factors().tierGap()) * TechniquePower.masteryQi(ctx.mastery());
			if (!Qi.trySpend(player, cost)) {
				land(player, true);
				Msg.actionBar(player, Component.translatable("kultivator.technique.lei_zhenzi_wings.no_qi"));
				ctx.end(EndReason.CANCELLED);
			}
		}
	}

	@Override
	public void end(CastContext ctx, EndReason reason) {
		ServerPlayer player = ctx.player();
		if (player != null) {
			land(player, reason != EndReason.DEATH && reason != EndReason.LOGOUT);
		}
	}

	/** Gives the flight back (only the lease this handler took); Slow Falling when it ends in mid-air. */
	private static void land(ServerPlayer player, boolean slowFalling) {
		FlightLease lease = LEASES.remove(player.getUUID());
		if (lease == null) {
			return;
		}
		boolean airborne = !player.onGround();
		if (FlightLease.holder(player) == lease) {
			lease.release();
		}
		if (slowFalling && airborne && !player.isCreative() && !player.isSpectator()) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALLING_TICKS, 0, false, true));
		}
	}

	/** Starts flying at once (the lease grants {@code mayfly}); a grounded player is lifted first, as the client drops flight on the ground. */
	private static void liftOff(ServerPlayer player) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		Abilities abilities = player.getAbilities();
		if (!abilities.mayfly) {
			return;
		}
		abilities.flying = true;
		player.onUpdateAbilities();
		if (player.onGround()) {
			Vec3 motion = player.getDeltaMovement();
			player.setDeltaMovement(motion.x, LIFT_OFF, motion.z);
			player.connection.send(new ClientboundSetEntityMotionPacket(player));
		}
	}

	/** The form's length with the spec's own mastery bonuses (level 3 DURATION_TICKS / DURATION, level 4 DURATION), plus grace. */
	static int maxTicks(CastContext ctx) {
		double ticks = FORM_TICKS;
		if (!ctx.arena()) {
			TechniqueSpec spec = ctx.spec();
			if (ctx.mastery() >= 3) {
				ticks = withDuration(ticks, spec.perfection());
			}
			if (ctx.mastery() >= 4) {
				ticks = withDuration(ticks, spec.peak());
			}
		}
		return (int) Math.round(ticks) + GRACE_TICKS;
	}

	private static double withDuration(double ticks, java.util.List<Bonus> bonuses) {
		double result = ticks;
		for (Bonus bonus : bonuses) {
			if (bonus.kind() == Bonus.Kind.DURATION_TICKS) {
				result += bonus.ticks();
			} else if (bonus.kind() == Bonus.Kind.DURATION) {
				result *= 1.0D + Math.max(0.0D, bonus.value());
			}
		}
		return result;
	}
}
