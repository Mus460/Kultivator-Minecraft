package dev.kultivator.technique.special;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.disciple.power.DisciplePowers;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Anchor;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.Step;
import dev.kultivator.technique.spec.run.CastContext;
import dev.kultivator.technique.spec.run.SpecialTechnique;
import dev.kultivator.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static dev.kultivator.technique.spec.dsl.Dsl.*;

/**
 * {@code fusion_pair}, the special of {@code netherworld_white_tiger} (DESIGN 22.2.7, 22.9.9, 22.13.5; Soul Land: Dai
 * Mubai and Zhu Zhuqing's martial soul fusion skill). The master casts it together with a bonded own disciple of another
 * Dao within {@link #RANGE} blocks ({@code DisciplePowers.pairPartner}): both glow for {@link #CHARGE_TICKS} ticks, then a
 * charging white tiger runs as a FRONT dealing ({@link #BASE_COEF} + {@link #U_COEF} × U/P) × P, where U is the
 * disciple's P_disciple, plus KNK 1 and the disciple Dao's domain pulse (22.10.1). The disciple's {@code damageFactor}
 * adds at most ×{@link #DISCIPLE_FACTOR_CAP}; the heir adds ×{@link #HEIR_FACTOR}. The disciple pays
 * {@link #DISCIPLE_COST_SHARE} of the cost from its own pool ({@code DisciplePowers.spendPairQi}) and the cooldown is
 * shared with the disciple's key of this spec.
 * <p>
 * Contract with the runner: the spec's own timeline holds only an empty keep-alive step at {@link #KEEP_ALIVE_TICKS};
 * the wind-up and the strike run here through {@link CastContext}. Mastery level 4 re-runs the tiger as an ECHO at
 * ×{@link #ECHO_FACTOR} after {@link #ECHO_DELAY} ticks with no carving.
 */
public final class FusionPair implements SpecialTechnique {

	public static final String ID = "fusion_pair";
	public static final double RANGE = 16.0D;
	public static final int MIN_BOND = 50;
	/** Share of the cost the disciple pays from its own pool (22.13.5). */
	public static final double DISCIPLE_COST_SHARE = 0.5D;
	public static final int CHARGE_TICKS = 20;
	public static final double BASE_COEF = 2.0D;
	public static final double U_COEF = 2.0D;
	public static final double KNOCK = 1.0D;
	public static final double HEIR_FACTOR = 1.5D;
	public static final double DISCIPLE_FACTOR_CAP = 1.25D;
	public static final double TARGET_RANGE = 32.0D;
	public static final double BOLT_RANGE = 48.0D;
	public static final double BOLT_RADIUS = 12.0D;
	public static final int KEEP_ALIVE_TICKS = 60;
	public static final int ECHO_DELAY = 10;
	public static final double ECHO_FACTOR = 0.4D;

	private static final String KEY = "kultivator.technique.netherworld_white_tiger.why.";
	private static final String STATE_PARTNER = "fusion_partner";

	@Override
	public @Nullable Component blocker(CastContext ctx) {
		ServerPlayer player = ctx.player();
		if (player == null) {
			return Component.translatable(KEY + "player");
		}
		if (partner(player) == null) {
			return Component.translatable(KEY + "partner");
		}
		if (!ctx.options().forceVisual() && ctx.hostiles(ctx.caster().position(), TARGET_RANGE, false).isEmpty()) {
			return Component.translatable(KEY + "no_target");
		}
		return null;
	}

	@Override
	public boolean cast(CastContext ctx) {
		ServerPlayer player = ctx.player();
		if (player == null) {
			return false;
		}
		DiscipleEntity partner = partner(player);
		if (partner == null) {
			return false;
		}
		if (!DisciplePowers.spendPairQi(partner, DISCIPLE_COST_SHARE)) {
			Msg.actionBar(player, Component.translatable(KEY + "qi"));
			return false;
		}
		CultivationData data = Cultivators.get(player);
		partner.data().setCooldown(ctx.spec().id(), TechniquePower.cooldownTicks(ctx.spec(), data, ctx.mastery()));
		ctx.state().putInt(STATE_PARTNER, partner.getId());
		Vec3 from = player.position().add(0.0D, 1.0D, 0.0D);
		Vec3 to = partner.position().add(0.0D, 1.0D, 0.0D);
		ctx.fx(Phase.CHARGE, from, to, 1.0F, (float) from.distanceTo(to), partner.getId(), CHARGE_TICKS);
		ctx.sound(Phase.CHARGE, from);
		ctx.schedule(CHARGE_TICKS, () -> strike(ctx, 1.0D, true));
		if (ctx.mastery() >= 4 && !ctx.arena() && !ctx.echo()) {
			ctx.schedule(CHARGE_TICKS + ECHO_DELAY, () -> strike(ctx, ECHO_FACTOR, false));
		}
		return true;
	}

	/** The bonded partner of {@code master}: from the spine's pair API, of another Dao and bonded at least {@link #MIN_BOND}. */
	private static @Nullable DiscipleEntity partner(ServerPlayer master) {
		DiscipleEntity partner = DisciplePowers.pairPartner(master, RANGE);
		if (partner == null || partner.isRemoved() || !partner.isAlive()) {
			return null;
		}
		Dao own = Cultivators.get(master).dao();
		Dao theirs = partner.data().dao();
		if (theirs == null || theirs == own || DisciplePaths.bond(partner.data()) < MIN_BOND) {
			return null;
		}
		return partner;
	}

	/** The tiger: one FRONT along the look with the pair damage, KNK and the disciple Dao's pulse; carving only once. */
	private static void strike(CastContext ctx, double factor, boolean carve) {
		if (ctx.ended()) {
			return;
		}
		Entity found = ctx.level().getEntity(ctx.state().getIntOr(STATE_PARTNER, -1));
		DiscipleEntity partner = found instanceof DiscipleEntity disciple && disciple.isAlive() ? disciple : null;
		double power = Math.max(1.0E-6D, ctx.power());
		double u = partner == null ? 0.0D : TechniquePower.power(partner.data());
		double multiplier = 1.0D;
		Dao dao = null;
		if (partner != null) {
			double discipleFactor = DiscipleHooks.damageFactor(partner, partner, true);
			multiplier = Double.isFinite(discipleFactor) ? Math.max(1.0D, Math.min(DISCIPLE_FACTOR_CAP, discipleFactor)) : 1.0D;
			if (DisciplePaths.isHeir(partner.data())) {
				multiplier *= HEIR_FACTOR;
			}
			dao = partner.data().dao();
		}
		MysteryFusion.Pulse pulse = dao == null ? null : MysteryFusion.pulse(dao, factor, ctx.tier());
		double coef = (BASE_COEF + U_COEF * u / power) * multiplier * factor + (pulse == null ? 0.0D : pulse.damage());
		List<Payload.Hit> hits = new ArrayList<>();
		hits.add(damage(coef));
		hits.add(knock(KNOCK));
		if (pulse != null) {
			hits.addAll(pulse.hits());
			if (pulse.drainDamage() > 0.0D && coef > 0.0D) {
				hits.add(drain(Math.min(1.0D, pulse.drainDamage() / coef)));
			}
		}
		Step tiger = at(0).shape(front().length(24, 48).width(2, 4).height(3).speed(3)).hit(hits.toArray(new Payload.Hit[0]));
		if (pulse != null && !pulse.self().isEmpty()) {
			tiger = tiger.self(pulse.self().toArray(new Payload.Boon[0]));
		}
		if (pulse != null && !pulse.ally().isEmpty()) {
			tiger = tiger.ally(pulse.ally().toArray(new Payload.Boon[0]));
		}
		Vec3 start = ctx.caster().position();
		Vec3 look = ctx.look();
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		Vec3 direction = flat.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
		// The runner sends the FRONT's TRAVEL event (the tiger) when it runs the step.
		ctx.run(tiger);
		if (pulse != null && pulse.bolts() > 0) {
			ctx.run(at(0).shape(all().at(Anchor.AIM).range(BOLT_RANGE).radius(BOLT_RADIUS).max(pulse.bolts()).order(Shape.All.Order.RANDOM))
					.hit(damage(MysteryFusion.BOLT_COEF * factor)).phase(0));
		}
		if (carve) {
			ctx.destroy(start, direction);
		}
	}
}
