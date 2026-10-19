package dev.kultivator.technique.special;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.technique.spec.dsl.Anchor;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.Step;
import dev.kultivator.technique.spec.run.CastContext;
import dev.kultivator.technique.spec.run.SpecialTechnique;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static dev.kultivator.technique.spec.dsl.Dsl.*;

/**
 * {@code mystery_fusion}, the special of {@code profound_mystery_fusion} (DESIGN 22.2.7, 22.9.9; Coiling Dragon: Linley
 * fuses profound mysteries of different elements). One orb (at most {@link #ORB_RANGE} blocks, flying
 * {@link #ORB_SPEED} b/t) bursts as a NOVA of {@link #NOVA_COEF} × P carrying the domain pulses of 22.10.1 of the main Dao
 * and the strongest second Dao (comprehension ≥ {@link #SECOND_DAO_MIN}) at ×{@link #PULSE_FACTOR}, plus one of the eight
 * pair rows (any other pair: radius ×1.5).
 * <p>
 * Contract with the runner: the spec's own timeline holds only an empty keep-alive step at {@link #KEEP_ALIVE_TICKS};
 * everything happens here through {@link CastContext} (shapes and payloads are evaluated by the runner, so every safety
 * rule of 22.3.5 and the damage choke point apply). The Dao colours ride in the TechFx {@code count} field:
 * {@code (main + 1) + 16 × (second + 1)} (Dao ordinals). Mastery level 4 re-runs the burst as an ECHO at
 * ×{@link #ECHO_FACTOR} after {@link #ECHO_DELAY} ticks, with no carving (22.3.4).
 */
public final class MysteryFusion implements SpecialTechnique {

	public static final String ID = "mystery_fusion";
	public static final double SECOND_DAO_MIN = 30.0D;
	public static final double ORB_RANGE = 32.0D;
	/** Blocks per tick the orb flies. */
	public static final double ORB_SPEED = 2.0D;
	public static final int ORB_MIN_TICKS = 4;
	public static final int ORB_MAX_TICKS = 16;
	public static final double NOVA_COEF = 2.2D;
	public static final double NOVA_RADIUS = 6.0D;
	public static final double NOVA_RADIUS_CAP = 12.0D;
	/** "both Daos' domainPulse ×2". */
	public static final double PULSE_FACTOR = 2.0D;
	/** Any pair without its own row: radius ×1.5. */
	public static final double OTHER_PAIR_RADIUS = 1.5D;
	public static final double FIRE_ICE_FACTOR = 1.5D;
	public static final double PAIR_DRAIN = 0.3D;
	public static final double PAIR_HEAL = 0.2D;
	public static final double PAIR_HEAL_RADIUS = 8.0D;
	public static final int KEEP_ALIVE_TICKS = 40;
	public static final int ECHO_DELAY = 10;
	public static final double ECHO_FACTOR = 0.4D;
	/** Effects of a domain pulse last 2 s (22.10.1). */
	public static final int PULSE_TICKS = 40;
	/** The bolt of a Lightning pulse. */
	public static final double BOLT_COEF = 0.8D;
	public static final int BOLTS_CAP = 4;

	private static final String KEY = "kultivator.technique.profound_mystery_fusion.why.";

	/** The eight pair rows of 22.9.9 (unordered), and every other pair. */
	public enum Pair {
		SWORD_LIGHTNING, FIRE_ICE, BODY_SPACE, LIFE_SHADOW, ICE_SPACE, SWORD_SPACE, FIRE_LIGHTNING, BODY_LIFE, OTHER;

		public static Pair of(Dao a, Dao b) {
			if (is(a, b, Dao.SWORD, Dao.LIGHTNING)) {
				return SWORD_LIGHTNING;
			}
			if (is(a, b, Dao.FIRE, Dao.ICE)) {
				return FIRE_ICE;
			}
			if (is(a, b, Dao.BODY, Dao.SPACE)) {
				return BODY_SPACE;
			}
			if (is(a, b, Dao.LIFE, Dao.SHADOW)) {
				return LIFE_SHADOW;
			}
			if (is(a, b, Dao.ICE, Dao.SPACE)) {
				return ICE_SPACE;
			}
			if (is(a, b, Dao.SWORD, Dao.SPACE)) {
				return SWORD_SPACE;
			}
			if (is(a, b, Dao.FIRE, Dao.LIGHTNING)) {
				return FIRE_LIGHTNING;
			}
			if (is(a, b, Dao.BODY, Dao.LIFE)) {
				return BODY_LIFE;
			}
			return OTHER;
		}

		private static boolean is(Dao a, Dao b, Dao x, Dao y) {
			return a == x && b == y || a == y && b == x;
		}
	}

	/**
	 * One Dao's domain pulse of DESIGN 22.10.1 at {@code factor}: the extra damage coefficient ({@code damage} × P, to be
	 * added to the carrying hit), a drain share of that damage, hostile payloads, caster and ally boons, and the bolts of a
	 * Lightning pulse ({@code bolts} × {@link #BOLT_COEF} × factor on different random targets).
	 */
	public record Pulse(double damage, double drainDamage, List<Payload.Hit> hits, List<Payload.Boon> self, List<Payload.Boon> ally,
			int bolts) {
	}

	/** The domain pulse of {@code dao} (null = the Dao-less pulse) at {@code factor}, for a caster at tier index {@code tier}. */
	public static Pulse pulse(@Nullable Dao dao, double factor, int tier) {
		if (dao == null) {
			return new Pulse(0.4D * factor, 0.0D, List.of(eff(Payload.Eff.SLOWNESS, 1, PULSE_TICKS)), List.of(), List.of(), 0);
		}
		return switch (dao) {
			case SWORD -> new Pulse(0.5D * factor, 0.0D, List.of(), List.of(), List.of(), 0);
			case FIRE -> new Pulse(0.3D * factor, 0.0D, List.of(ignite(3.0D), dot(0.4D * factor, 3, Payload.DotKind.FIRE)), List.of(),
					List.of(), 0);
			// "from DU every 4th pulse FRZ 80t": one burst, so the long freeze from tier index 3 (Dao Union).
			case ICE -> new Pulse(0.0D, 0.0D, List.of(eff(Payload.Eff.SLOWNESS, 3, PULSE_TICKS), freeze(tier >= 3 ? 80 : PULSE_TICKS)),
					List.of(), List.of(), 0);
			case LIGHTNING -> new Pulse(0.0D, 0.0D, List.of(), List.of(), List.of(), Math.min(BOLTS_CAP, 1 + Math.max(0, tier) / 2));
			case BODY -> new Pulse(0.4D * factor, 0.0D, List.of(), List.of(buff(Payload.BuffKind.RESISTANCE, 2, PULSE_TICKS)), List.of(), 0);
			case LIFE -> new Pulse(0.3D * factor, 0.3D * factor, List.of(eff(Payload.Eff.WITHER, 1, PULSE_TICKS)), List.of(),
					List.of(buff(Payload.BuffKind.REGENERATION, 2, PULSE_TICKS)), 0);
			case SHADOW -> new Pulse(0.4D * factor, 0.0D, List.of(eff(Payload.Eff.BLINDNESS, 1, PULSE_TICKS)),
					List.of(buff(Payload.BuffKind.INVISIBILITY, 1, PULSE_TICKS)), List.of(), 0);
			case SPACE -> new Pulse(0.3D * factor, 0.0D, List.of(eff(Payload.Eff.SLOWNESS, 5, PULSE_TICKS),
					pull(0.3D).toward(Payload.Center.ANCHOR)), List.of(), List.of(), 0);
		};
	}

	/** The Dao other than the main one with the highest comprehension of at least {@link #SECOND_DAO_MIN}, or null. */
	public static @Nullable Dao secondDao(CultivationData data) {
		Dao main = data.dao();
		Dao best = null;
		double bestValue = SECOND_DAO_MIN;
		for (Dao dao : Dao.values()) {
			if (dao == main) {
				continue;
			}
			double value = data.comprehension(dao);
			if (value >= bestValue) {
				best = dao;
				bestValue = value;
			}
		}
		return best;
	}

	@Override
	public @Nullable Component blocker(CastContext ctx) {
		ServerPlayer player = ctx.player();
		if (player == null) {
			return Component.translatable(KEY + "player");
		}
		CultivationData data = Cultivators.get(player);
		if (data.dao() == null) {
			return Component.translatable(KEY + "dao");
		}
		if (secondDao(data) == null) {
			return Component.translatable(KEY + "second");
		}
		if (!ctx.options().forceVisual() && ctx.hostiles(ctx.caster().position(), ORB_RANGE, false).isEmpty()) {
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
		CultivationData data = Cultivators.get(player);
		Dao main = data.dao();
		Dao second = secondDao(data);
		if (main == null || second == null) {
			return false;
		}
		// both Daos' colours on every event of the fusion (FLAG_TWO_DAO)
		ctx.twoDao(second.color());
		LivingEntity target = ctx.target();
		Vec3 eye = ctx.eye();
		Vec3 point = impactPoint(ctx, target, eye);
		int travel = (int) Math.max(ORB_MIN_TICKS, Math.min(ORB_MAX_TICKS, Math.ceil(eye.distanceTo(point) / ORB_SPEED)));
		int colours = main.ordinal() + 1 + 16 * (second.ordinal() + 1);
		ctx.fx(Phase.TRAVEL, eye, point, 0.6F, (float) eye.distanceTo(point), colours, travel);
		ctx.sound(Phase.TRAVEL, eye);
		ctx.schedule(travel, () -> burst(ctx, main, second, point, target, 1.0D, true));
		if (ctx.mastery() >= 4 && !ctx.arena() && !ctx.echo()) {
			ctx.schedule(travel + ECHO_DELAY, () -> burst(ctx, main, second, point, target, ECHO_FACTOR, false));
		}
		return true;
	}

	/** The locked target when it is within the orb's range, otherwise the aim point at most {@link #ORB_RANGE} from the eye. */
	private static Vec3 impactPoint(CastContext ctx, @Nullable LivingEntity target, Vec3 eye) {
		if (target != null && target.isAlive() && target.level() == ctx.level() && target.position().distanceTo(eye) <= ORB_RANGE) {
			return target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
		}
		Vec3 offset = ctx.aim().subtract(eye);
		double length = offset.length();
		return length > ORB_RANGE ? eye.add(offset.scale(ORB_RANGE / length)) : ctx.aim();
	}

	/** The burst at the orb's landing: the NOVA with both pulses and the pair row; carving only for the first burst. */
	private static void burst(CastContext ctx, Dao main, Dao second, Vec3 planned, @Nullable LivingEntity target, double factor,
			boolean carve) {
		if (ctx.ended()) {
			return;
		}
		Vec3 point = planned;
		if (target != null && target.isAlive() && target.level() == ctx.level() && target.position().distanceTo(planned) <= 8.0D) {
			point = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
		}
		ctx.markEnd(point);
		Pair pair = Pair.of(main, second);
		Pulse first = pulse(main, PULSE_FACTOR * factor, ctx.tier());
		Pulse other = pulse(second, PULSE_FACTOR * factor, ctx.tier());
		double radiusFactor = pair == Pair.OTHER ? OTHER_PAIR_RADIUS : 1.0D;
		double coef = NOVA_COEF * (pair == Pair.FIRE_ICE ? FIRE_ICE_FACTOR : 1.0D) * factor + first.damage() + other.damage();
		double drainShare = coef > 0.0D ? (first.drainDamage() + other.drainDamage()) / coef : 0.0D;
		List<Payload.Hit> hits = new ArrayList<>();
		hits.add(damage(coef));
		hits.addAll(first.hits());
		hits.addAll(other.hits());
		List<Payload.Boon> self = new ArrayList<>(first.self());
		self.addAll(other.self());
		List<Payload.Boon> ally = new ArrayList<>(first.ally());
		ally.addAll(other.ally());
		switch (pair) {
			case FIRE_ICE -> hits.add(eff(Payload.Eff.SLOWNESS, 3, 60));
			case BODY_SPACE -> {
				hits.add(pull(0.6D).toward(Payload.Center.ANCHOR));
				hits.add(dot(0.3D * factor, 3, Payload.DotKind.QI));
			}
			case LIFE_SHADOW -> drainShare += PAIR_DRAIN;
			case ICE_SPACE -> hits.add(bind(Payload.BindKind.ROOT, 80));
			case FIRE_LIGHTNING -> {
				hits.add(ignite(4.0D));
				hits.add(bind(Payload.BindKind.STUN, 20));
			}
			default -> {
			}
		}
		if (drainShare > 0.0D) {
			hits.add(drain(Math.min(1.0D, drainShare)));
		}
		Step blast = at(0).shape(nova().radius(NOVA_RADIUS * radiusFactor, NOVA_RADIUS_CAP * radiusFactor).at(Anchor.END))
				.hit(hits.toArray(new Payload.Hit[0]))
				.phase(0);
		if (!self.isEmpty()) {
			blast = blast.self(self.toArray(new Payload.Boon[0]));
		}
		if (!ally.isEmpty()) {
			blast = blast.ally(ally.toArray(new Payload.Boon[0]));
		}
		// The runner sends the step's FX and sound as EXTRA0 (Step.phase); the colours ride on the TRAVEL event.
		ctx.run(blast);
		int bolts = first.bolts() + other.bolts();
		if (bolts > 0) {
			ctx.run(at(0).shape(all().at(Anchor.END).radius(NOVA_RADIUS * radiusFactor, NOVA_RADIUS_CAP * radiusFactor).max(bolts)
					.order(Shape.All.Order.RANDOM)).hit(damage(BOLT_COEF * PULSE_FACTOR * factor)).phase(1));
		}
		switch (pair) {
			case SWORD_LIGHTNING -> ctx.run(at(0).shape(chain().from(Anchor.END).jumps(4)).hit(damage(1.0D * factor)).phase(1));
			case SWORD_SPACE -> ctx.run(at(0).shape(front().length(16).width(1)).hit(damage(1.0D * factor)).phase(1));
			case BODY_LIFE -> ctx.run(at(0).ally(PAIR_HEAL_RADIUS, heal(PAIR_HEAL)).phase(1));
			default -> {
			}
		}
		if (carve) {
			ctx.destroy(point, new Vec3(0.0D, -1.0D, 0.0D));
		}
	}
}
