package dev.kultivator.technique.spec.run;

import dev.kultivator.data.Dao;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.DamageContext;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Anchor;
import dev.kultivator.technique.spec.dsl.Cond;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.Step;
import dev.kultivator.technique.spec.dsl.Val;
import dev.kultivator.util.LoadedPath;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Step execution and the shapes of DESIGN 22.3.1 for a {@link Caster}: one entity query per evaluation (WAVE and FRONT one
 * per 4 ticks covering the next 4 ticks, RAIN one per impact tick, CHAIN one per hop, areas one per pulse), the hard caps
 * of 22.15, the anchors, the step conditions and sub-steps, the FX events and the Kehancuran anchors. Never a block
 * change: rays are read-only clips along loaded paths.
 */
final class Steps {

	private static final int TRAVEL_CHUNK = 4;
	private static final int MAX_OCCURRENCES = 400;

	/** Who a shape reached, with a per-target factor (falloff, splash, side copies) and the evaluation's geometry. */
	static final class Hits {
		final List<LivingEntity> targets = new ArrayList<>();
		final Map<LivingEntity, Double> factors = new HashMap<>();
		Vec3 centre;
		double radius;
		boolean precise;
		Vec3 direction;
		double coefFactor = 1.0D;
		/** The anchor points this evaluation struck (multi-point anchors): Kehancuran points instead of the centre. */
		@Nullable List<Vec3> points;

		Hits(Vec3 centre, double radius, boolean precise, Vec3 direction) {
			this.centre = centre;
			this.radius = radius;
			this.precise = precise;
			this.direction = direction;
		}

		void add(LivingEntity target, double factor) {
			Double old = factors.get(target);
			if (old == null) {
				targets.add(target);
				factors.put(target, factor);
			} else if (factor > old) {
				factors.put(target, factor);
			}
		}

		double factor(LivingEntity target) {
			return factors.getOrDefault(target, 1.0D);
		}
	}

	// ================================================================== timeline

	/** Starts the timeline: every step of stage 0 or 1 at its tick (repeats, channel pulses). */
	static void startTimeline(Caster cast) {
		for (Step step : timeline(cast)) {
			if (step.timing() == Step.Timing.END) {
				continue;
			}
			if (cast.spec.stages() != null && step.stage() > 1) {
				continue;
			}
			scheduleOccurrences(cast, step);
		}
	}

	/** A STAGES recast advanced to {@code stage}: its steps run from now. */
	static void startStage(Caster cast, int stage) {
		cast.timelineStart = cast.level.getGameTime();
		cast.destruction.stageAdvanced();
		if (cast.spec.special() != null) {
			// A special's stages are its own: the handler runs again with ctx.stage() advanced (its timeline steps of that
			// stage are scheduled below by the runner, never by the handler).
			SpecialTechnique handler = dev.kultivator.data.TechniqueRegistry.special(cast.spec.special());
			if (handler != null) {
				try {
					handler.cast(cast);
				} catch (RuntimeException exception) {
					dev.kultivator.Kultivator.LOGGER.error("Kultivator: special {} stage failed", cast.spec.special(), exception);
				}
			}
		}
		for (Step step : timeline(cast)) {
			if (step.timing() != Step.Timing.END && step.stage() == stage) {
				scheduleOccurrences(cast, step);
			}
		}
	}

	/** The window of a STAGES spec ran out (or the last stage came): "st n or end" steps of later stages run now. */
	static void windowEnded(Caster cast) {
		for (Step step : timeline(cast)) {
			if (step.orWindowEnd() && step.stage() > cast.stage) {
				execute(cast, new Caster.Run(step, 0, null, null, 1.0D, DamageContext.Kind.HIT, false));
			}
		}
	}

	private static List<Step> timeline(Caster cast) {
		if (cast.bonusSteps.isEmpty()) {
			return cast.spec.timeline();
		}
		List<Step> list = new ArrayList<>(cast.spec.timeline());
		list.addAll(cast.bonusSteps);
		return list;
	}

	private static void scheduleOccurrences(Caster cast, Step step) {
		int repeat = Math.max(1, step.repeat());
		int every = Math.max(1, step.every());
		if (step.shape() instanceof Shape.Wave && cast.bonusWaves > 0) {
			repeat += cast.bonusWaves;
			if (step.every() <= 0) {
				every = 10;
			}
		}
		if (cast.spec.channel() != null) {
			int pulse = Math.max(1, cast.spec.channel().pulse());
			int ticks = cast.spec.channel().ticks();
			// A step with its own "x n/k" keeps that rhythm (samadhi_true_fire's cone), cut when the channel stops; a SELF or
			// lasting-area step (summons, guards, a domain that pulses by itself) runs once instead of being re-made each pulse.
			if (step.repeat() > 1) {
				for (int rep = 0; rep < Math.min(repeat, MAX_OCCURRENCES) && step.at() + rep * every < ticks; rep++) {
					int tick = step.at() + rep * every;
					int index = rep;
					cast.schedule(tick, () -> runIfChannel(cast, step, index));
				}
				return;
			}
			if (step.shape().kind() == Shape.Kind.SELF || step.shape().kind().lasting()) {
				cast.schedule(step.at(), () -> {
					if (step.at() == 0 || cast.channelUntil > 0L) {
						execute(cast, new Caster.Run(step, 0, null, null, growth(step, 0), DamageContext.Kind.HIT, false));
					}
				});
				return;
			}
			int occurrences = 0;
			for (int k = 0; k * pulse + step.at() < ticks && occurrences < MAX_OCCURRENCES; k++) {
				int tick = k * pulse + step.at();
				int rep = k;
				occurrences++;
				cast.schedule(tick, () -> runIfChannel(cast, step, rep));
			}
			return;
		}
		for (int rep = 0; rep < Math.min(repeat, MAX_OCCURRENCES); rep++) {
			int tick = step.at() + rep * every;
			int index = rep;
			cast.schedule(tick, () -> execute(cast, new Caster.Run(step, index, null, null, growth(step, index), DamageContext.Kind.HIT, false)));
		}
	}

	private static void runIfChannel(Caster cast, Step step, int rep) {
		if (cast.channelUntil > 0L || rep == 0) {
			execute(cast, new Caster.Run(step, rep, null, null, growth(step, rep), DamageContext.Kind.HIT, false));
		}
	}

	private static double growth(Step step, int rep) {
		return step.growPerRepeat() == 0.0D ? 1.0D : 1.0D + step.growPerRepeat() * rep;
	}

	/** Schedules a step (a sub-step, {@code CastContext.run}) from now with an anchor and a factor. */
	static void schedule(Caster cast, Step step, int ignored, @Nullable LivingEntity hit, double mult, DamageContext.Kind kind, boolean byProduct) {
		int repeat = Math.max(1, step.repeat());
		int every = Math.max(1, step.every());
		for (int rep = 0; rep < Math.min(repeat, MAX_OCCURRENCES); rep++) {
			int index = rep;
			int tick = step.timing() == Step.Timing.END ? 0 : step.at() + rep * every;
			cast.schedule(tick, () -> execute(cast, new Caster.Run(step, index, hit, null, mult * growth(step, index), kind, byProduct)));
		}
	}

	/** The END steps of the timeline (the cast is otherwise done). */
	static void runEndSteps(Caster cast) {
		for (Step step : timeline(cast)) {
			if (step.timing() == Step.Timing.END && (step.stage() == 0 || step.stage() == cast.stage)) {
				execute(cast, new Caster.Run(step, 0, null, null, 1.0D, DamageContext.Kind.HIT, false));
			}
		}
	}

	// ================================================================== one step

	/** Runs one step occurrence now: SELF payloads first (a dash before its PATH), then the shape and its hits, then allies. */
	static void execute(Caster cast, Caster.Run run) {
		if (cast.ended || !cast.casterValid()) {
			return;
		}
		Step step = run.step;
		if (step.minTier() > 0 && cast.tier() < step.minTier()) {
			return;
		}
		if (!Conditions.perTarget(step.only()) && !Conditions.test(step.only(), cast, cast.caster, run.hit)) {
			return;
		}
		// The cast's first step (S1): never a stage recast, never after a handler already drew its CAST.
		boolean first = cast.stage <= 1 && !cast.castFxSent && cast.timelineAge() == 0 && step.at() == 0 && step.timing() == Step.Timing.AT
				&& run.rep == 0 && run.hit == null;
		cast.currentPhase = step.extraPhase() >= 0 ? Phase.extra(step.extraPhase()) : first ? Phase.CAST : Phase.IMPACT;
		applySelf(cast, run, false);
		if (step.timing() == Step.Timing.AT && step.at() > 0) {
			int tick = step.at() + run.rep * Math.max(1, step.every());
			if (cast.destruction.wantsTick(tick)) {
				cast.destruction.tick(tick, anchorPoint(cast, defaultAnchor(step.shape()), run));
			}
		}
		Shape shape = step.shape();
		if (first && windsUp(cast, shape)) {
			// S10: a visible build-up. CAST now (the pose winds back, qi gathers at the hand), the release after the windup.
			int windup = cast.spec.windup();
			preview(cast, run, shape, windup);
			cast.activity(1);
			cast.schedule(windup, () -> {
				cast.activity(-1);
				if (!cast.ended && cast.casterValid()) {
					cast.currentPhase = step.extraPhase() >= 0 ? Phase.extra(step.extraPhase()) : Phase.IMPACT;
					runShape(cast, run, false);
				}
			});
			return;
		}
		runShape(cast, run, first);
	}

	/** Travelling and sky shapes get the build-up beat; a charged cast already had its build-up (the charge). */
	private static boolean windsUp(Caster cast, Shape shape) {
		if (cast.spec.charge() != null || cast.spec.windup() <= 0) {
			return false;
		}
		for (Step other : cast.spec.timeline()) {
			if (other.timing() == Step.Timing.AT && other.at() > 0 && other.at() <= cast.spec.windup()) {
				return false;
			}
		}
		return shape instanceof Shape.Wave || shape instanceof Shape.Front || shape instanceof Shape.Beam || shape instanceof Shape.Rain;
	}

	/** The CAST event of a winding-up step, at the hand (beams, fronts), the feet (waves) or the rain's centre. */
	private static void preview(Caster cast, Caster.Run run, Shape shape, int windup) {
		switch (shape) {
			case Shape.Wave wave -> Travel.wave(cast, run, wave, windup);
			case Shape.Front front -> Travel.front(cast, run, front, windup);
			case Shape.Rain rain -> Travel.rain(cast, run, rain, windup);
			case Shape.Beam beam -> {
				double length = beamLength(cast, beam);
				Vec3 eye = cast.caster.getEyePosition();
				Vec3 at = eye.add(cast.look.scale(Math.min(length, 2.0D))).add(0.0D, -0.3D, 0.0D);
				cast.castFx(at, eye.add(cast.look.scale(length)), (float) cast.area(beam.halfWidth(), TechniquePower.BEAM_WIDTH_CAP), (float) length, 1, windup);
			}
			default -> cast.castFx(cast.hand(), cast.aim, 0.0F, 0.0F, 1, windup);
		}
	}

	/** The shape part of a step occurrence ({@code first}: the cast's CAST event is still to send). */
	private static void runShape(Caster cast, Caster.Run run, boolean first) {
		Step step = run.step;
		Shape shape = step.shape();
		if (first && shape instanceof Shape.Volley) {
			// the hand the volley leaves; its projectiles are TRAVEL events of their own (FLAG_VOLLEY)
			cast.castFx(cast.hand(), cast.aim, 0.0F, 0.0F, 1, 0);
		}
		switch (shape) {
			case Shape.Self self -> {
				applyAllies(cast, run, cast.caster.position(), 8.0D, false);
				if (first) {
					cast.castFx(cast.caster.position(), cast.aim, 0.0F, 0.0F, 1, 0);
				}
				// A stage step's own layer (heaven_fire_transformation's shells) plays on every stage, the recast included; a
				// step anchored on a hit or kill (onKill restoreQi) plays at that body, toward the caster's hand.
				if (step.extraPhase() >= 0) {
					if (run.hit != null) {
						Vec3 body = run.hit.getBoundingBox().getCenter();
						cast.fx(cast.currentPhase, body, cast.hand(), 0.0F, (float) body.distanceTo(cast.hand()), 1, 0, run.hit.getId(), 0, 1, 0, 0);
					} else {
						cast.fx(cast.currentPhase, cast.caster.position(), cast.aim, 0.0F, 0.0F, 1, cast.castDuration());
					}
				}
				runEnded(cast, run);
			}
			case Shape.Zone zone when zoneDelay(zone) > 0 -> delayed(cast, run, zoneDelay(zone), skyOf(cast, zone), first);
			case Shape.Pillar pillar when pillar.delay() > 0 -> delayed(cast, run, pillar.delay(), 12.0D, first);
			case Shape.Wave wave -> Travel.wave(cast, run, wave, -1);
			case Shape.Front front -> Travel.front(cast, run, front, -1);
			case Shape.Beam beam when beam.travelling() && beamLength(cast, beam) > TechniquePower.BEAM_LENGTH_CAP -> Travel.beam(cast, run, beam);
			case Shape.Rain rain -> Travel.rain(cast, run, rain, -1);
			case Shape.Chain chain -> Travel.chain(cast, run, chain);
			case Shape.Volley volley -> volley(cast, run, volley);
			case Shape.Domain domain -> area(cast, run, shape);
			case Shape.Field field -> area(cast, run, shape);
			case Shape.Aura aura -> area(cast, run, shape);
			default -> {
				Hits hits = evaluate(cast, run, shape);
				Vec3 fxTo = fxTarget(cast, hits);
				LivingEntity main = shape instanceof Shape.Target && !hits.targets.isEmpty() ? hits.targets.get(0) : null;
				int targetId = main != null ? main.getId() : -1;
				Vec3 eye = cast.caster.getEyePosition();
				if (first) {
					switch (shape.kind()) {
						case STRIKE, CONE, BEAM -> {
							// S9: the flourish leaves the hand, toward the end of the reach
							double reach = shape.kind() == Shape.Kind.BEAM ? hits.centre.distanceTo(cast.end) : hits.radius;
							Vec3 at = eye.add(cast.look.scale(Math.min(Math.max(1.0D, reach), 2.0D))).add(0.0D, -0.3D, 0.0D);
							Vec3 to = shape.kind() == Shape.Kind.BEAM ? fxTo : eye.add(cast.look.scale(Math.max(1.0D, reach)));
							cast.castFx(at, to, (float) hits.radius, (float) at.distanceTo(to), hits.targets.size(), 0, targetId);
						}
						case TARGET -> {
							Vec3 hand = cast.hand();
							Vec3 to = main != null ? main.getBoundingBox().getCenter() : cast.aim;
							cast.castFx(hand, to, (float) hits.radius, (float) hand.distanceTo(to), hits.targets.size(), 0, targetId);
						}
						case PATH -> {
							Vec3 from = cast.pathFrom != null ? cast.pathFrom : cast.origin;
							Vec3 to = cast.pathTo != null ? cast.pathTo : cast.caster.position();
							cast.castFx(from, to, (float) hits.radius, (float) from.distanceTo(to), hits.targets.size(), 0);
						}
						default -> cast.castFx(hits.centre, fxTo, (float) hits.radius, (float) fxLength(cast, hits), hits.targets.size(), 0);
					}
				}
				if (shape.kind() == Shape.Kind.TARGET && main != null) {
					// the finger, seal or gourd light travels from the hand to the target (S9)
					Vec3 hand = cast.hand();
					Vec3 to = main.getBoundingBox().getCenter();
					cast.fx(step.extraPhase() >= 0 ? Phase.extra(step.extraPhase()) : Phase.TRAVEL, hand, to, (float) hits.radius,
							(float) hand.distanceTo(to), 1, (int) Math.max(4L, Math.round(hand.distanceTo(to) / 2.0D)), targetId, 0, 1, 0, 0);
				}
				if (shape.kind() == Shape.Kind.BEAM) {
					cast.fx(step.extraPhase() >= 0 ? Phase.extra(step.extraPhase()) : Phase.TRAVEL, hits.centre, fxTo, (float) hits.radius,
							(float) fxLength(cast, hits), hits.targets.size(), 0, targetId, 0, 1, 0, 0);
				} else if (!(shape.kind() == Shape.Kind.TARGET && step.extraPhase() >= 0)) {
					// S1: every step sends its own phase (the first one too, after the cast's one CAST)
					Phase own = first ? firstPhase(cast, step) : repeatPhase(cast, step);
					if (picksFew(shape) && hits.points == null && !hits.targets.isEmpty()) {
						// a few picked foes (a blood finger, a recall, star swords): per foe a TRAVEL from the hand to it and the
						// step's own event (pos = the hand, to = the foe, so onTarget layers sit on the foe)
						Vec3 hand = cast.hand();
						int n = Math.min(8, hits.targets.size());
						for (int i = 0; i < n; i++) {
							LivingEntity each = hits.targets.get(i);
							Vec3 body = each.getBoundingBox().getCenter();
							if (own != Phase.TRAVEL) {
								cast.fx(Phase.TRAVEL, hand, body, (float) hits.radius, (float) hand.distanceTo(body), n,
										(int) Math.max(4L, Math.round(hand.distanceTo(body) / 2.0D)), each.getId(), i, n, 0, 0);
							}
							cast.fx(own, hand, body, (float) hits.radius, (float) hand.distanceTo(body), n, 0, each.getId(), i, n, 0, 0);
						}
					} else {
						emitAt(cast, own, impactPos(cast, shape, hits), fxTo, hits, targetId, 0, 0);
					}
				}
				deliver(cast, run, hits);
				if (step.repeat() > 1 && step.extraPhase() >= 0 && Forms.form(cast.caster) != null) {
					// a form's stomps count on its look (the ancient god's brow stars light one by one)
					Forms.setStage(cast.caster, run.rep + 1);
				}
				applyAllies(cast, run, cast.caster.position(), hits.radius > 0.0D ? hits.radius : 8.0D, false);
				runEnded(cast, run);
			}
		}
	}

	/**
	 * One event per struck point of a multi-point evaluation (pillar rings, EACH_TARGET, CORNERS, EMITTERS: S9), or one
	 * event for a single point. More than 8 points: one event with {@code of} = n and {@code aux} = the ring radius × 100,
	 * the client places the copies.
	 */
	static void emitAt(Caster cast, Phase phase, Vec3 at, Vec3 to, Hits hits, int target, int duration, int flags) {
		List<Vec3> points = hits.points;
		if (points == null || points.size() <= 1) {
			cast.fx(phase, at, to, (float) hits.radius, (float) at.distanceTo(to), hits.targets.size(), duration, target, 0, 1, 0, flags);
			return;
		}
		int n = points.size();
		if (n > 8) {
			Vec3 centre = Vec3.ZERO;
			for (Vec3 point : points) {
				centre = centre.add(point);
			}
			centre = centre.scale(1.0D / n);
			double ring = 0.0D;
			for (Vec3 point : points) {
				ring += point.distanceTo(centre);
			}
			ring /= n;
			cast.fx(phase, centre, to, (float) hits.radius, 0.0F, hits.targets.size(), duration, target, -1, n,
					(int) Math.min(Integer.MAX_VALUE, Math.round(ring * 100.0D)), flags);
			return;
		}
		for (int i = 0; i < n; i++) {
			Vec3 point = points.get(i);
			cast.fx(phase, point, to, (float) hits.radius, 0.0F, hits.targets.size(), duration, target, i, n, 0, flags);
		}
	}

	/** ALL or RECALL shapes that pick at most 8 foes (their events go per foe, S9). */
	private static boolean picksFew(Shape shape) {
		return switch (shape) {
			case Shape.All all -> all.max().set() && Math.max(all.max().base(), Double.isNaN(all.max().cap()) ? 0.0D : all.max().cap()) <= 8.0D;
			case Shape.Recall recall -> recall.max() <= 8;
			default -> false;
		};
	}

	/** Where the step's own event lands: the struck foe of a STRIKE or TARGET, else the evaluation's centre. */
	private static Vec3 impactPos(Caster cast, Shape shape, Hits hits) {
		return switch (shape.kind()) {
			case STRIKE -> hits.targets.isEmpty()
					? cast.caster.getEyePosition().add(cast.look.scale(Math.max(1.0D, hits.radius) * 0.7D)).add(0.0D, -0.3D, 0.0D)
					: hits.targets.get(0).getBoundingBox().getCenter();
			case CONE -> cast.caster.getEyePosition().add(cast.look.scale(Math.max(1.0D, hits.radius) * 0.5D)).add(0.0D, -0.3D, 0.0D);
			case TARGET -> hits.targets.isEmpty() ? cast.aim : hits.targets.get(0).position();
			default -> hits.centre;
		};
	}

	/** The own phase of a cast's first step: its extra phase, else IMPACT, else PULSE for a repeating step drawn in PULSE. */
	private static Phase firstPhase(Caster cast, Step step) {
		if (step.extraPhase() >= 0) {
			return Phase.extra(step.extraPhase());
		}
		if (cast.spec.fx(Phase.IMPACT).isEmpty() && !cast.spec.fx(Phase.PULSE).isEmpty() && (step.repeat() > 1 || cast.spec.channel() != null)) {
			return Phase.PULSE;
		}
		return Phase.IMPACT;
	}

	/**
	 * The phase of a later occurrence: its extra phase, else PULSE for repeats when the spec draws PULSE and not IMPACT
	 * (samadhi_true_fire's spray, S17), else IMPACT.
	 */
	private static Phase repeatPhase(Caster cast, Step step) {
		if (step.extraPhase() >= 0) {
			return Phase.extra(step.extraPhase());
		}
		Phase phase = cast.currentPhase == Phase.CAST ? Phase.IMPACT : cast.currentPhase;
		if (phase == Phase.IMPACT && !cast.spec.fx(Phase.PULSE).isEmpty() && cast.spec.fx(Phase.IMPACT).isEmpty()) {
			return Phase.PULSE;
		}
		return phase;
	}

	/** A ZONE's landing delay: its own, else a sky zone falls for clamp(h / 2, 6, 20) ticks (S10). */
	static int zoneDelay(Shape.Zone zone) {
		if (zone.delay() > 0) {
			return zone.delay();
		}
		if (zone.skyHeight().set() && zone.skyHeight().base() > 0.0D) {
			return (int) Math.max(6L, Math.min(20L, Math.round(zone.skyHeight().base() / 2.0D)));
		}
		return 0;
	}

	private static double skyOf(Caster cast, Shape.Zone zone) {
		if (zone.skyHeight().set() && zone.skyHeight().base() > 0.0D) {
			return Math.max(4.0D, Math.min(64.0D, cast.area(zone.skyHeight(), 64.0D)));
		}
		return 12.0D;
	}

	/**
	 * A ZONE or PILLAR that lands after its delay. At once: the CAST (at the clamped zone centre, toward the caster: S9)
	 * for the cast's first step, and the falling visual's TRAVEL with FLAG_PRE_IMPACT from {@code sky} blocks up, timed to
	 * land exactly on the IMPACT (S10). On landing: the IMPACT per struck point.
	 */
	private static void delayed(Caster cast, Caster.Run run, int delay, double sky, boolean first) {
		Shape shape = run.step.shape();
		Anchor anchor = defaultAnchor(shape);
		Vec3 at = anchorPoint(cast, anchor, run);
		if (multiPoint(anchor)) {
			// repetition k of a DENSEST / EACH_TARGET zone falls on point k (nine suns on nine dense points)
			at = perRepetition(anchorPoints(cast, anchor, run, at), run, anchor).get(0);
		}
		if (shape instanceof Shape.Zone zone && aimedAnchor(zone.anchor())) {
			at = clampToRange(cast, at, cast.area(zone.range(), TechniquePower.ZONE_RANGE_CAP));
		}
		float radius = (float) previewRadius(cast, shape);
		if (first) {
			cast.castFx(at, cast.caster.position(), radius, (float) at.distanceTo(cast.caster.position()), 1, delay);
		}
		cast.fx(Phase.TRAVEL, at.add(0.0D, sky, 0.0D), at, radius, (float) sky, 1, delay, -1, 0, 1, 0, Payloads.TechFx.FLAG_PRE_IMPACT);
		cast.activity(1);
		cast.schedule(delay, () -> {
			cast.activity(-1);
			Hits hits = evaluate(cast, run, run.step.shape());
			Phase impact = run.step.extraPhase() >= 0 ? Phase.extra(run.step.extraPhase()) : Phase.IMPACT;
			emitAt(cast, impact, hits.centre, hits.centre.add(0.0D, sky, 0.0D), hits, -1, 0, 0);
			deliver(cast, run, hits);
			applyAllies(cast, run, cast.caster.position(), hits.radius, false);
			runEnded(cast, run);
		});
	}
	/** onEnd sub-steps of a finished step occurrence. */
	static void runEnded(Caster cast, Caster.Run run) {
		for (Step sub : run.step.onEnd()) {
			schedule(cast, sub, 0, run.hit, run.mult, run.kind, run.byProduct);
		}
	}

	// ================================================================== payload delivery

	/** SELF payloads of a step (with the level-3 PAYLOAD self bonus on the first one) and its whenSelf extras. */
	static void applySelf(Caster cast, Caster.Run run, boolean pulse) {
		Step step = run.step;
		for (Payload.Boon boon : step.self()) {
			cast.applyBoon(cast.caster, boon, true, pulse);
		}
		if (!run.byProduct && !pulse && run.rep == 0 && step == firstStep(cast) && !cast.bonusSelf.isEmpty()) {
			for (Payload.Boon boon : cast.bonusSelf) {
				cast.applyBoon(cast.caster, boon, true, false);
			}
		}
		for (Step.When when : step.when()) {
			if (!when.self().isEmpty() && !Conditions.perTarget(when.cond()) && Conditions.test(when.cond(), cast, cast.caster, null)) {
				for (Payload.Boon boon : when.self()) {
					cast.applyBoon(cast.caster, boon, true, pulse);
				}
			}
		}
	}

	private static @Nullable Step firstStep(Caster cast) {
		for (Step step : cast.spec.timeline()) {
			if (step.timing() == Step.Timing.AT) {
				return step;
			}
		}
		return null;
	}

	/** ALLY payloads to allies within the step's ally radius (or {@code fallbackRadius}). */
	static void applyAllies(Caster cast, Caster.Run run, Vec3 centre, double fallbackRadius, boolean pulse) {
		if (run.step.ally().isEmpty()) {
			return;
		}
		double radius = run.step.allyRadius() > 0.0D ? cast.area(Val.of(run.step.allyRadius(), run.step.allyRadius() * 2.0D), 32.0D)
				: Math.max(4.0D, fallbackRadius);
		List<LivingEntity> allies = cast.alliesExceptSelf(centre, radius);
		cast.allyPass = true;
		try {
			for (LivingEntity ally : allies) {
				for (Payload.Boon boon : run.step.ally()) {
					cast.applyBoon(ally, boon, false, pulse);
				}
				// "whenSelf" extras go to every receiver whose own condition holds (NATURAL_GROUND reads the ally's ground)
				for (Step.When when : run.step.when()) {
					if (!when.self().isEmpty() && !Conditions.perTarget(when.cond()) && Conditions.test(when.cond(), cast, cast.caster, ally)) {
						for (Payload.Boon boon : when.self()) {
							cast.applyBoon(ally, boon, false, pulse);
						}
					}
				}
			}
			if (!allies.isEmpty() && !pulse) {
				Phase phase = cast.currentPhase == Phase.CAST && cast.castFxSent ? Phase.IMPACT : cast.currentPhase;
				cast.fx(phase, centre, centre, (float) radius, 0.0F, allies.size(), 0);
			}
		} finally {
			cast.allyPass = false;
		}
	}

	/** Applies the step's hostile payloads to every target reached, then FX, sub-steps and Kehancuran anchors. */
	static void deliver(Caster cast, Caster.Run run, Hits hits) {
		Step step = run.step;
		if (hits.targets.isEmpty()) {
			return;
		}
		cast.pendingLink.clear();
		cast.hitBossThisPass = false;
		double castMult = 1.0D;
		List<Payload.Hit> castExtra = new ArrayList<>();
		for (Step.When when : step.when()) {
			if (!Conditions.perTarget(when.cond())) {
				double mult = Conditions.multiplier(when, cast, cast.caster, null);
				castMult *= mult;
				if (!when.hit().isEmpty() && (when.cond().kind() == Cond.Kind.TRIB_STRIKES || Conditions.test(when.cond(), cast, cast.caster, null))) {
					castExtra.addAll(when.hit());
				}
			}
		}
		boolean damaging = false;
		for (Payload.Hit payload : step.hit()) {
			if (payload instanceof Payload.Dmg || payload instanceof Payload.DmgPct) {
				damaging = true;
				break;
			}
		}
		List<LivingEntity> landed = new ArrayList<>();
		List<LivingEntity> killed = new ArrayList<>();
		int limit = cast.maxTargets();
		int done = 0;
		Payload.Link link = null;
		boolean phantom = run.kind == DamageContext.Kind.PHANTOM;
		boolean wasOpen = cast.passOpen;
		cast.passOpen = true;
		try {
			for (LivingEntity target : hits.targets) {
				if (done >= limit) {
					break;
				}
				if (!target.isAlive() || target.isRemoved()) {
					continue;
				}
				if (phantom && target instanceof net.minecraft.world.entity.player.Player) {
					// summons never attack players, not even a sparring partner (22.14.5)
					continue;
				}
				if (Conditions.perTarget(step.only()) && !Conditions.test(step.only(), cast, cast.caster, target)) {
					continue;
				}
				done++;
				double mult = castMult;
				List<Payload.Hit> extra = new ArrayList<>(castExtra);
				for (Step.When when : step.when()) {
					if (Conditions.perTarget(when.cond())) {
						mult *= Conditions.multiplier(when, cast, cast.caster, target);
						if (!when.hit().isEmpty() && Conditions.test(when.cond(), cast, cast.caster, target)) {
							extra.addAll(when.hit());
						}
					}
				}
				Caster.HitInfo info = new Caster.HitInfo();
				info.precise = hits.precise || cast.spec.has(dev.kultivator.data.TechniqueSpec.Flag.PRECISE) && step.shape() instanceof Shape.All;
				info.kind = run.kind == DamageContext.Kind.HIT && cast.echo ? DamageContext.Kind.ECHO : run.kind;
				info.centre = hits.centre;
				info.radius = hits.radius;
				info.storedDamage = run.storedDamage;
				info.coefFactor = hits.coefFactor;
				info.f = hits.factor(target) * run.mult * mult * cast.stackFactor();
				int before = cast.hits.getOrDefault(target.getUUID(), 0);
				for (Payload.Hit payload : step.hit()) {
					cast.applyHit(target, payload, info);
					if (payload instanceof Payload.Link each) {
						link = each;
					}
				}
				for (Payload.Hit payload : extra) {
					cast.applyHit(target, payload, info);
				}
				if (damaging && !run.byProduct) {
					for (Payload.Hit payload : cast.bonusHits) {
						cast.applyHit(target, payload, info);
					}
				}
				if (cast.hits.getOrDefault(target.getUUID(), 0) > before || !damaging) {
					landed.add(target);
					if (TechniquePower.isBoss(target)) {
						cast.hitBossThisPass = true;
					}
					if (target.isDeadOrDying()) {
						killed.add(target);
					}
				}
			}
		} finally {
			cast.passOpen = wasOpen;
		}
		if (!wasOpen) {
			cast.flushAllyHeal();
		}
		if (link != null && cast.pendingLink.size() >= 2) {
			StatusTracker.link(cast, List.copyOf(cast.pendingLink), link.share(), link.ticks(), link.bossShare());
		}
		cast.pendingLink.clear();
		if (!landed.isEmpty()) {
			boolean marks = hasTemplate(cast, Phase.HIT, dev.kultivator.technique.spec.dsl.FxLayer.Template.VIEWER_MARK);
			int dot = run.kind == DamageContext.Kind.DOT ? Payloads.TechFx.FLAG_DOT : 0;
			if (landed.size() > TechniquePower.FX_MASS_HIT && !marks) {
				cast.fx(Phase.MASS_HIT, hits.centre, cast.hand(), (float) hits.radius, 0.0F, landed.size(), 0, -1, -1, landed.size(), 0, dot);
			} else {
				Vec3 hand = cast.hand();
				int index = 0;
				for (LivingEntity target : landed) {
					Vec3 middle = target.getBoundingBox().getCenter();
					cast.fx(Phase.HIT, middle, hand, target.getBbWidth(), (float) middle.distanceTo(hand), 1, 0, target.getId(), index++, landed.size(), 0, dot);
				}
			}
			markLooks(cast, step, landed);
			if (damaging && !run.byProduct && !cast.echo && cast.parent == null) {
				// the evaluation's own points (each struck anchor of EACH_TARGET / DENSEST), else its centre
				List<Vec3> points = hits.points != null && !hits.points.isEmpty() ? hits.points : List.of(hits.centre);
				cast.destruction.impactPoints(points, hits.direction, !cast.firstImpactDone);
				cast.firstImpactDone = true;
			} else if (phantom && damaging && !cast.echo && cast.parent == null) {
				// a phantom's attack never carves (22.11.2 rule 6); what it struck is a point for SCATTER (22.11.4)
				cast.destruction.impact(landed.get(0).position(), hits.direction, true);
			}
		}
		for (LivingEntity target : landed) {
			for (Step sub : step.onHit()) {
				schedule(cast, sub, 0, target, run.mult, run.kind, run.byProduct);
			}
		}
		for (LivingEntity target : killed) {
			for (Step sub : step.onKill()) {
				schedule(cast, sub, 0, target, run.mult, run.kind, run.byProduct);
			}
		}
		cast.hitBossThisPass = false;
	}

	/** True when {@code phase} of the spec has a layer of {@code template}. */
	static boolean hasTemplate(Caster cast, Phase phase, dev.kultivator.technique.spec.dsl.FxLayer.Template template) {
		for (dev.kultivator.technique.spec.dsl.FxLayer layer : cast.spec.fx(phase)) {
			if (layer.template() == template) {
				return true;
			}
		}
		return false;
	}

	/** True when the spec's timeline starts a caster-following lasting area (DOMAIN, AURA): its events carry FLAG_FOLLOW. */
	static boolean followsCaster(dev.kultivator.data.TechniqueSpec spec) {
		for (Step step : spec.timeline()) {
			if (step.shape() instanceof Shape.Domain || step.shape() instanceof Shape.Aura) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The authored bind look ({@code bindLook}) of a step that marks or curses without a BIND payload (the eighth hex's seal)
	 * goes to every landed foe for the step's longest mark or attribute duration.
	 */
	private static void markLooks(Caster cast, Step step, List<LivingEntity> landed) {
		BindKind look = cast.spec.bindLook();
		if (look == null || landed.isEmpty()) {
			return;
		}
		int ticks = 0;
		for (Payload.Hit payload : step.hit()) {
			if (payload instanceof Payload.Bind) {
				return;
			}
			if (payload instanceof Payload.Marked marked) {
				ticks = Math.max(ticks, marked.ticks());
			} else if (payload instanceof Payload.Attr attr) {
				ticks = Math.max(ticks, attr.ticks());
			}
		}
		if (ticks <= 0) {
			return;
		}
		for (LivingEntity target : landed) {
			if (!(target instanceof net.minecraft.world.entity.player.Player)) {
				VisualSync.bind(target, look, ticks, cast.caster, cast.spec.netId());
			}
		}
	}

	/** A projectile of a volley hit {@code victim}. */
	static void projectileHit(Caster cast, Caster.Run run, LivingEntity victim, Vec3 at) {
		Hits hits = new Hits(at, 1.0D, false, cast.look);
		hits.add(victim, 1.0D);
		// deliver records the hit as the impact point (once; a landing without a hit records nothing)
		deliver(cast, run, hits);
	}

	// ================================================================== lasting areas

	private static void area(Caster cast, Caster.Run run, Shape shape) {
		boolean dewa = cast.spec.dewa();
		Fields.Area area;
		long now = cast.level.getGameTime();
		switch (shape) {
			case Shape.Domain domain -> {
				double radius = cast.area(domain.radius(), dewa ? TechniquePower.DOMAIN_RADIUS_CAP_DEWA : TechniquePower.DOMAIN_RADIUS_CAP);
				int duration = cast.duration(domain.duration(), dewa ? TechniquePower.DOMAIN_DURATION_CAP_DEWA : TechniquePower.DOMAIN_DURATION_CAP);
				double height = height(radius, domain.height());
				area = new Fields.Area(cast, run.step, Shape.Kind.DOMAIN, null, radius, height, now, duration, domain.pulse(), domain.ramp(),
						domain.rampMax(), Shape.Field.Form.CIRCLE, Shape.Field.Walls.NONE, List.of(), run.rep);
			}
			case Shape.Field field -> {
				Vec3 centre = field.anchor().kind() == Anchor.Kind.CASTER ? cast.caster.position()
						: clampToRange(cast, anchorPoint(cast, field.anchor(), run), cast.area(field.range(), TechniquePower.ZONE_RANGE_CAP));
				double radius = cast.area(field.radius(), dewa ? TechniquePower.DOMAIN_RADIUS_CAP_DEWA : TechniquePower.DOMAIN_RADIUS_CAP);
				int duration = cast.duration(field.duration(), dewa ? TechniquePower.DOMAIN_DURATION_CAP_DEWA : TechniquePower.DOMAIN_DURATION_CAP);
				int emitters = cast.count(field.emitters(), 16.0D);
				List<Vec3> points = new ArrayList<>();
				for (int i = 0; i < emitters; i++) {
					double angle = Math.PI * 2.0D * i / Math.max(1, emitters);
					points.add(centre.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius));
				}
				area = new Fields.Area(cast, run.step, Shape.Kind.FIELD, centre, radius, height(radius, field.height()), now, duration,
						field.pulse(), field.ramp(), field.rampMax(), field.form(), field.wallMode(), points, run.rep);
			}
			case Shape.Aura aura -> {
				double radius = cast.area(aura.radius(), TechniquePower.AURA_RADIUS_CAP);
				int duration = cast.duration(aura.duration(), TechniquePower.AURA_DURATION_CAP);
				area = new Fields.Area(cast, run.step, Shape.Kind.AURA, null, radius, Math.max(3.0D, radius), now, duration, aura.pulse(),
						0.0D, 0, Shape.Field.Form.CIRCLE, Shape.Field.Walls.NONE, List.of(), run.rep);
			}
			default -> {
				return;
			}
		}
		cast.lastAreaRadius = area.radius;
		Fields.start(area);
		cast.activity(1);
		if (area.kind == Shape.Kind.DOMAIN || area.kind == Shape.Kind.AURA) {
			cast.followArea = true;
		}
		int lasting = (int) (area.until - area.start);
		if (cast.currentPhase == Phase.CAST && !cast.castFxSent) {
			cast.castFx(area.centre(), area.centre(), (float) area.radius, (float) area.height, area.emitters.size(), lasting);
		} else {
			// the cast already drew its CAST (a self step before it, a stage recast): the area starts as a PULSE (or its phase)
			Phase phase = cast.currentPhase == Phase.CAST || cast.currentPhase == Phase.IMPACT ? Phase.PULSE : cast.currentPhase;
			cast.fx(phase, area.centre(), area.centre(), (float) area.radius, (float) area.height, area.emitters.size(), lasting);
		}
		applyAllies(cast, run, area.centre(), area.radius, false);
		if (shape instanceof Shape.Field) {
			cast.destruction.field(area);
			cast.lastFieldCentre = area.centre();
			cast.lastFieldRadius = area.radius;
		}
	}

	private static double height(double radius, double requested) {
		double height = requested > 0.0D ? requested : Math.max(4.0D, radius);
		return radius > 16.0D ? Math.min(TechniquePower.AREA_HEIGHT_CAP, height) : height;
	}

	/** One pulse of a lasting area: one query, the step's payloads (×RAMP, ×2 when thinned), per-pulse boons. */
	static void pulse(Caster cast, Fields.Area area, double thin) {
		Vec3 centre = area.centre();
		double reach = area.form == Shape.Field.Form.SQUARE || area.form == Shape.Field.Form.OCTAGON ? area.radius * 1.42D : area.radius;
		Hits hits = new Hits(centre, area.radius, false, cast.look);
		hits.coefFactor = thin;
		// one flattened query per pulse: the area's own height (≤ 16 when r > 16), never a full cube (22.15.1)
		for (LivingEntity target : cast.hostilesFlat(centre, reach, Math.max(2.0D, area.height), false)) {
			if (area.contains(target.position())) {
				hits.add(target, 1.0D);
			}
		}
		Caster.Run run = new Caster.Run(area.step, area.repetition, null, null, area.rampFactor(), DamageContext.Kind.PULSE, false);
		cast.currentPhase = Phase.PULSE;
		if (area.pulses > 1) {
			applySelf(cast, run, true);
		}
		deliver(cast, run, hits);
		applyAllies(cast, run, centre, area.radius, true);
		cast.fx(Phase.PULSE, centre, centre, (float) area.radius, 0.0F, hits.targets.size(), area.pulse);
	}

	/** FIELD walls: non-boss hostiles bounce back inside (IN) or are kept out (OUT). */
	static void walls(Caster cast, Fields.Area area) {
		Vec3 centre = area.centre();
		for (LivingEntity target : cast.hostilesFlat(centre, area.radius + 4.0D, Math.max(4.0D, area.height), false)) {
			if (TechniquePower.isBoss(target) || !cast.moveAllowed(target)) {
				continue;
			}
			boolean inside = area.contains(target.position());
			Vec3 to = centre.subtract(target.position()).horizontal();
			if (to.lengthSqr() < 1.0E-4D) {
				continue;
			}
			Vec3 dir = to.normalize();
			if (area.walls == Shape.Field.Walls.IN && !inside && target.position().distanceTo(centre) <= area.radius + 3.5D) {
				target.setDeltaMovement(dir.x * 0.6D, 0.2D, dir.z * 0.6D);
			} else if (area.walls == Shape.Field.Walls.OUT && inside) {
				target.setDeltaMovement(-dir.x * 0.6D, 0.2D, -dir.z * 0.6D);
			} else {
				continue;
			}
			target.addTag(dev.kultivator.util.NoGrief.TAG);
			target.setPortalCooldown();
			target.needsSync = true;
			target.syncVelocity = true;
		}
	}

	/** An area ended: its onEnd sub-steps and END steps of the same shape run. */
	static void areaEnded(Caster cast, Fields.Area area, EndReason reason) {
		cast.activity(-1);
		if (reason == EndReason.DONE && !cast.ended) {
			Caster.Run run = new Caster.Run(area.step, area.repetition, null, null, 1.0D, DamageContext.Kind.HIT, false);
			runEnded(cast, run);
		}
	}

	/**
	 * A phantom's attack: its attack step anchored at the phantom (a by-product: it never carves; a struck target is a
	 * SCATTER point), or a light default strike. Summons never target players (22.14.5).
	 */
	static void phantomAttack(Caster cast, Phantoms.Phantom phantom) {
		Step attack = phantom.summon.attack();
		Caster.Run run;
		LivingEntity nearest = null;
		for (LivingEntity candidate : cast.hostiles(phantom.pos, phantom.range, false)) {
			if (!(candidate instanceof net.minecraft.world.entity.player.Player)) {
				nearest = candidate;
				break;
			}
		}
		if (nearest != null) {
			// the phantom lunges and strikes on every client (DESIGN 27.4: pos = phantom, to = victim, aux = PhantomKind)
			cast.fx(Phase.PHANTOM, phantom.pos, nearest.getBoundingBox().getCenter(), 1.0F, (float) phantom.pos.distanceTo(nearest.position()), 1, 6,
					nearest.getId(), phantom.index, phantom.count, Phantoms.kind(phantom).ordinal(), 0);
		}
		if (attack == null) {
			if (nearest == null) {
				return;
			}
			run = new Caster.Run(Step.at(0).shape(Shape.Target.DEFAULT.pick(Shape.Target.Pick.HIT)).hit(new Payload.Dmg(0.4D, false, Map.of(),
					Double.NaN, 0.0D, Payload.Per.NONE, false, false)), 0, nearest, phantom.pos, 1.0D, DamageContext.Kind.PHANTOM, true);
		} else {
			run = new Caster.Run(attack, 0, null, phantom.pos, 1.0D, DamageContext.Kind.PHANTOM, true);
		}
		execute(cast, run);
	}

	// ================================================================== volley

	private static void volley(Caster cast, Caster.Run run, Shape.Volley volley) {
		int count = cast.countWithBonus(volley.count(), TechniquePower.VOLLEY_COUNT_CAP);
		double range = cast.area(volley.range(), TechniquePower.VOLLEY_RANGE_CAP);
		double hitRadius = cast.area(volley.hitRadius(), TechniquePower.VOLLEY_HIT_RADIUS_CAP);
		Vec3 from = anchorPoint(cast, volley.from(), run);
		if (volley.from().kind() == Anchor.Kind.CASTER) {
			from = cast.caster.getEyePosition();
		}
		if (!VirtualProjectiles.launch(cast, run, volley, count, range, hitRadius, from, cast.aim)) {
			runEnded(cast, run);
		}
	}

	// ================================================================== shape evaluation

	/** Evaluates an instant shape now (one entity query; multi-point anchors are merged). */
	static Hits evaluate(Caster cast, Caster.Run run, Shape shape) {
		boolean dewa = cast.spec.dewa();
		Vec3 look = cast.look;
		switch (shape) {
			case Shape.Strike strike -> {
				double range = cast.area(strike.range(), TechniquePower.STRIKE_RANGE_CAP);
				double arc = cast.area(strike.arc(), TechniquePower.STRIKE_ARC_CAP);
				Vec3 from = strike.anchor().kind() == Anchor.Kind.CASTER ? cast.caster.getEyePosition() : anchorPoint(cast, strike.anchor(), run);
				Hits hits = new Hits(from, range, true, look);
				Vec3 flat = horizontalLook(cast);
				int max = strike.max() > 0 ? strike.max() : cast.maxTargets();
				for (LivingEntity target : cast.hostiles(from, range + 2.0D, true)) {
					Vec3 to = target.getBoundingBox().getCenter().subtract(from);
					if (Math.sqrt(to.x * to.x + to.z * to.z) > range + target.getBbWidth() * 0.5D || Math.abs(to.y) > strike.height() + target.getBbHeight()) {
						continue;
					}
					Vec3 toFlat = to.horizontal();
					if (toFlat.lengthSqr() > 1.0D && angle(flat, toFlat.normalize()) > arc / 2.0D) {
						continue;
					}
					if (!los(cast, from, target)) {
						continue;
					}
					hits.add(target, 1.0D);
					if (hits.targets.size() >= max) {
						break;
					}
				}
				return hits;
			}
			case Shape.Cone cone -> {
				double length = cast.area(cone.length(), TechniquePower.CONE_LENGTH_CAP);
				double half = cast.area(cone.halfAngle(), TechniquePower.CONE_ANGLE_CAP);
				int base = Math.max(1, cast.count(cone.count(), 12.0D));
				int count = Math.min(12, base + cast.bonusCount);
				Vec3 from = cast.caster.getEyePosition();
				Hits hits = new Hits(from, length, false, look);
				List<LivingEntity> candidates = cast.hostiles(from, length + 1.5D, false);
				int max = cone.max() > 0 ? cone.max() : cast.maxTargets();
				for (int i = 0; i < count; i++) {
					double offset = cone.yaw() + (count <= 1 ? 0.0D : (i - (count - 1) / 2.0D) * (cone.fan() > 0.0D ? cone.fan() : 2.0D * half));
					Vec3 dir = Caster.rotateYaw(look, offset);
					double factor = i >= base ? cast.bonusCountFactor : 1.0D;
					for (LivingEntity target : candidates) {
						Vec3 to = target.getBoundingBox().getCenter().subtract(from);
						double distance = to.length();
						if (distance > length + target.getBbWidth() * 0.5D) {
							continue;
						}
						boolean inside = distance < 1.5D ? to.dot(dir) > 0.0D : angle(dir, to.scale(1.0D / distance)) <= half;
						if (inside && los(cast, from, target)) {
							hits.add(target, factor);
						}
					}
				}
				trim(hits, max);
				return hits;
			}
			case Shape.Beam beam -> {
				double length = beamLength(cast, beam);
				double halfWidth = cast.area(beam.halfWidth(), TechniquePower.BEAM_WIDTH_CAP);
				int base = Math.max(1, cast.count(beam.count(), 12.0D));
				int count = Math.min(12, base + cast.bonusCount);
				Vec3 from = beam.from().kind() == Anchor.Kind.CASTER ? cast.caster.getEyePosition() : anchorPoint(cast, beam.from(), run);
				Vec3 aimDir = cast.aim.subtract(from);
				Vec3 baseDir = beam.from().kind() == Anchor.Kind.CASTER || aimDir.lengthSqr() < 1.0E-4D ? look : aimDir.normalize();
				boolean pass = beam.passBlocks() && (cast.spec.dao() == Dao.SPACE || dewa);
				Hits hits = new Hits(from, halfWidth, true, baseDir);
				List<LivingEntity> candidates = cast.hostiles(from.add(baseDir.scale(length * 0.5D)), length * 0.5D + halfWidth + 2.0D, true);
				Vec3 farthest = from;
				for (int i = 0; i < count; i++) {
					double offset = beam.yaw() + (count <= 1 ? 0.0D : (i - (count - 1) / 2.0D) * (beam.fan() > 0.0D ? beam.fan() : 12.0D));
					Vec3 dir = Caster.rotateYaw(baseDir, offset);
					Vec3 to = from.add(dir.scale(length));
					Vec3 end;
					if (pass) {
						end = LoadedPath.clip(cast.level, from, to);
					} else {
						BlockHitResult hit = LoadedPath.ray(cast.level, from, to, cast.caster);
						end = hit.getLocation();
						if (hit.getType() != HitResult.Type.MISS && i == 0) {
							cast.destruction.beam(from, dir, hit.getLocation());
						}
					}
					if (i == 0) {
						farthest = end;
					}
					double factor = i >= base ? cast.bonusCountFactor : 1.0D;
					List<LivingEntity> line = new ArrayList<>();
					for (LivingEntity target : candidates) {
						AABB body = target.getBoundingBox().inflate(halfWidth);
						if (body.contains(from) || body.clip(from, end).isPresent()) {
							line.add(target);
						}
					}
					line.sort(Comparator.comparingDouble(target -> target.distanceToSqr(from)));
					int pierce = beam.pierceCount();
					int limit = pierce == Shape.Beam.PIERCE_ALL ? Integer.MAX_VALUE : Math.max(1, pierce == 0 ? 1 : pierce);
					for (int n = 0; n < Math.min(limit, line.size()); n++) {
						hits.add(line.get(n), factor);
					}
				}
				cast.markEnd(farthest);
				hits.radius = halfWidth;
				hits.centre = from;
				hits.direction = farthest.subtract(from);
				return hits;
			}
			case Shape.Nova nova -> {
				Vec3 centre = anchorPoint(cast, nova.anchor(), run);
				double radius = nova.radius().set() ? cast.area(nova.radius(), dewa ? TechniquePower.DOMAIN_RADIUS_CAP_DEWA : TechniquePower.NOVA_RADIUS_CAP)
						: Math.max(1.0D, cast.areaRadius() > 0.0D ? cast.areaRadius() : cast.lastAreaRadius);
				if (Double.isNaN(radius)) {
					radius = 6.0D;
				}
				radius = Math.min(radius, cast.radiusCap());
				cast.lastAreaRadius = radius;
				Hits hits = new Hits(centre, radius, false, look);
				List<Vec3> points = perRepetition(anchorPoints(cast, nova.anchor(), run, centre), run, nova.anchor());
				double r2 = radius * radius;
				List<LivingEntity> pool = points.size() == 1 ? cast.hostiles(points.get(0), radius, false) : around(cast, points, radius, radius, radius);
				for (Vec3 point : points) {
					for (LivingEntity target : pool) {
						double d2 = target.distanceToSqr(point);
						if (d2 > r2 || nova.inner() > 0.0D && d2 < nova.inner() * nova.inner()) {
							continue;
						}
						hits.add(target, 1.0D);
					}
				}
				if (points.size() > 1 || multiPoint(nova.anchor())) {
					hits.points = points;
				}
				trim(hits, cast.maxTargets());
				return hits;
			}
			case Shape.Zone zone -> {
				double range = cast.area(zone.range(), TechniquePower.ZONE_RANGE_CAP);
				// Only an aimed anchor is pulled into range: a zone on the field, the end of a dive or a hit stays where it is.
				Vec3 raw = anchorPoint(cast, zone.anchor(), run);
				Vec3 centre = aimedAnchor(zone.anchor()) ? clampToRange(cast, raw, range) : raw;
				if (zone.advance() != 0.0D) {
					centre = centre.add(horizontalLook(cast).scale(zone.advance() * run.rep));
				}
				double radius = zone.radius().set() ? cast.area(zone.radius(), dewa ? TechniquePower.ZONE_RADIUS_CAP_DEWA : TechniquePower.ZONE_RADIUS_CAP)
						: Math.max(1.0D, cast.areaRadius() > 0.0D ? cast.areaRadius() : 5.0D);
				cast.lastAreaRadius = radius;
				Hits hits = new Hits(centre, radius, false, look);
				List<Vec3> points = perRepetition(anchorPoints(cast, zone.anchor(), run, centre), run, zone.anchor());
				if (points.size() == 1 && multiPoint(zone.anchor())) {
					// repetition k strikes dense point k: draw it there too
					hits.centre = points.get(0);
				}
				double half = Math.max(2.0D, zone.height());
				// one flattened query (or one box over every point): never a cube per point (22.15.1)
				List<LivingEntity> pool = points.size() == 1 ? cast.hostilesFlat(points.get(0), radius, half + 1.0D, false)
						: around(cast, points, radius, half + 1.0D, half + 1.0D);
				for (Vec3 point : points) {
					for (LivingEntity target : pool) {
						Vec3 rel = target.position().subtract(point);
						if (rel.x * rel.x + rel.z * rel.z <= radius * radius && Math.abs(rel.y) <= half) {
							hits.add(target, 1.0D);
						}
					}
				}
				if (points.size() > 1 || multiPoint(zone.anchor())) {
					hits.points = points;
				}
				trim(hits, cast.maxTargets());
				return hits;
			}
			case Shape.Pillar pillar -> {
				double radius = cast.area(pillar.radius(), TechniquePower.PILLAR_RADIUS_CAP);
				double height = cast.area(pillar.height(), TechniquePower.PILLAR_HEIGHT_CAP);
				Vec3 centre = anchorPoint(cast, pillar.anchor(), run);
				List<Vec3> points = new ArrayList<>();
				int on = Math.min(cast.maxTargets(), cast.countWithBonus(pillar.onTargets(), 32.0D));
				List<LivingEntity> pool;
				if (on > 0) {
					// one query: the search sphere grown by the pillar radius picks the targets and answers the hits
					double search = cast.area(pillar.search(), TechniquePower.PILLAR_SEARCH_CAP);
					pool = cast.hostiles(centre, search + radius + 1.0D, false);
					double s2 = search * search;
					for (LivingEntity target : pool) {
						if (points.size() >= on) {
							break;
						}
						if (target.distanceToSqr(centre) <= s2) {
							points.add(target.position());
						}
					}
				} else {
					if (pillar.ring() > 0) {
						for (int i = 0; i < pillar.ring(); i++) {
							double angle = Math.PI * 2.0D * i / pillar.ring();
							points.add(centre.add(Math.cos(angle) * pillar.ringRadius(), 0.0D, Math.sin(angle) * pillar.ringRadius()));
						}
					} else {
						points.addAll(anchorPoints(cast, pillar.anchor(), run, centre));
					}
					pool = around(cast, points, radius + 1.0D, 2.5D, height + 1.0D);
				}
				Hits hits = new Hits(centre, radius, false, look);
				for (Vec3 point : points) {
					for (LivingEntity target : pool) {
						Vec3 rel = target.position().subtract(point);
						if (rel.x * rel.x + rel.z * rel.z <= (radius + target.getBbWidth() * 0.5D) * (radius + target.getBbWidth() * 0.5D)
								&& rel.y >= -2.0D && rel.y <= height) {
							hits.add(target, 1.0D);
						}
					}
				}
				if (points.size() > 1 || multiPoint(pillar.anchor())) {
					hits.points = points;
				}
				trim(hits, cast.maxTargets());
				return hits;
			}
			case Shape.Target targetShape -> {
				double range = cast.area(targetShape.range(), TechniquePower.TARGET_RANGE_CAP);
				Vec3 eye = cast.caster.getEyePosition();
				LivingEntity main = switch (targetShape.pick()) {
					case HIT -> run.hit != null && run.hit.isAlive() ? run.hit : cast.target();
					case STRONGEST, WEAKEST -> {
						LivingEntity best = null;
						for (LivingEntity candidate : cast.hostiles(run.at != null ? run.at : cast.caster.position(), range, false)) {
							if (targetShape.los() && !los(cast, eye, candidate)) {
								continue;
							}
							if (best == null || (targetShape.pick() == Shape.Target.Pick.STRONGEST ? candidate.getMaxHealth() > best.getMaxHealth()
									: candidate.getHealth() < best.getHealth())) {
								best = candidate;
							}
						}
						yield best;
					}
					default -> lockedOrFront(cast, range, targetShape.los(), run);
				};
				Vec3 at = main != null ? main.position() : cast.aim;
				Hits hits = new Hits(at, 0.0D, true, look);
				if (main == null || main.position().distanceTo(run.at != null ? run.at : cast.caster.position()) > range + 4.0D
						|| !cast.mayHit(main, true)) {
					return hits;
				}
				hits.add(main, 1.0D);
				double splash = cast.area(targetShape.splash(), TechniquePower.TARGET_SPLASH_CAP);
				if (splash > 0.0D) {
					hits.radius = splash;
					for (LivingEntity other : cast.hostiles(main.position(), splash, false)) {
						if (other != main) {
							hits.add(other, targetShape.splashFactor());
						}
					}
				}
				cast.markEnd(main.position());
				return hits;
			}
			case Shape.All all -> {
				Vec3 centre;
				double radius;
				if (all.anchor().kind() == Anchor.Kind.AREA && !all.radius().set()) {
					centre = cast.areaCentre();
					radius = Math.max(1.0D, cast.areaRadius() > 0.0D ? cast.areaRadius() : 16.0D);
				} else {
					double range = all.range().set() && all.range().base() > 0.0D ? cast.area(all.range(), TechniquePower.ZONE_RANGE_CAP) : 0.0D;
					centre = all.anchor().kind() == Anchor.Kind.CASTER ? cast.caster.position()
							: range > 0.0D ? clampToRange(cast, anchorPoint(cast, all.anchor(), run), range) : anchorPoint(cast, all.anchor(), run);
					radius = cast.area(all.radius(), dewa ? TechniquePower.DOMAIN_RADIUS_CAP_DEWA : TechniquePower.ALL_RADIUS_CAP);
				}
				boolean precise = all.isPrecise() || cast.spec.has(dev.kultivator.data.TechniqueSpec.Flag.PRECISE);
				int max = Math.min(cast.maxTargets(), Math.max(1, cast.count(all.max(), cast.maxTargets())));
				Vec3 eye = cast.caster.getEyePosition();
				if (multiPoint(all.anchor())) {
					List<Vec3> points = anchorPoints(cast, all.anchor(), run, centre);
					if (points.size() > 1) {
						// "max" per point (tongtian_divine_fire_pillars: each emitter strikes its own foe), one box query in all
						Hits hits = new Hits(centre, radius, precise, look);
						List<LivingEntity> pool = new ArrayList<>(around(cast, points, radius, radius, radius));
						if (all.needsLos()) {
							pool.removeIf(target -> !los(cast, eye, target));
						}
						double r2 = radius * radius;
						for (Vec3 point : points) {
							pool.sort(Comparator.comparingDouble((LivingEntity target) -> target.distanceToSqr(point)));
							int taken = 0;
							for (LivingEntity target : pool) {
								if (taken >= max || target.distanceToSqr(point) > r2) {
									break;
								}
								if (!hits.factors.containsKey(target)) {
									hits.add(target, 1.0D);
									taken++;
								}
							}
						}
						hits.points = points;
						trim(hits, cast.maxTargets());
						return hits;
					}
				}
				List<LivingEntity> list = new ArrayList<>(cast.hostiles(centre, radius, precise));
				if (all.needsLos()) {
					list.removeIf(target -> !los(cast, eye, target));
				}
				switch (all.order()) {
					case STRONGEST -> list.sort(Comparator.comparingDouble((LivingEntity target) -> -target.getMaxHealth()));
					case WEAKEST -> list.sort(Comparator.comparingDouble(LivingEntity::getHealth));
					case RANDOM -> shuffle(list, cast.level.getRandom());
					default -> {
					}
				}
				Hits hits = new Hits(centre, radius, precise, look);
				for (int i = 0; i < Math.min(max, list.size()); i++) {
					hits.add(list.get(i), 1.0D);
				}
				return hits;
			}
			case Shape.Recall recall -> {
				Hits hits = new Hits(cast.caster.position(), 0.0D, false, look);
				double range = Math.min(TechniquePower.RECALL_RANGE, recall.range());
				int max = Math.min(TechniquePower.RECALL_MAX, recall.max());
				for (LivingEntity target : cast.hitOrder) {
					if (target.isAlive() && !target.isRemoved() && target.level() == cast.level
							&& target.position().distanceTo(cast.caster.position()) <= range) {
						hits.add(target, 1.0D);
						if (hits.targets.size() >= max) {
							break;
						}
					}
				}
				return hits;
			}
			case Shape.Path path -> {
				double halfWidth = cast.area(path.halfWidth(), TechniquePower.PATH_WIDTH_CAP);
				Vec3 from = cast.pathFrom != null ? cast.pathFrom : cast.origin;
				Vec3 to = cast.pathTo != null ? cast.pathTo : cast.caster.position();
				Vec3 mid = from.add(to).scale(0.5D);
				Hits hits = new Hits(mid, halfWidth, false, to.subtract(from));
				Vec3 lift = new Vec3(0.0D, 1.0D, 0.0D);
				for (LivingEntity target : cast.hostiles(mid, from.distanceTo(to) * 0.5D + halfWidth + 2.0D, false)) {
					AABB body = target.getBoundingBox().inflate(halfWidth);
					if (body.contains(from.add(lift)) || body.clip(from.add(lift), to.add(lift)).isPresent()) {
						hits.add(target, 1.0D);
					}
				}
				trim(hits, cast.maxTargets());
				return hits;
			}
			default -> {
				return new Hits(cast.caster.position(), 0.0D, false, look);
			}
		}
	}

	// ================================================================== anchors

	static Anchor defaultAnchor(Shape shape) {
		return switch (shape) {
			case Shape.Zone zone -> zone.anchor();
			case Shape.Pillar pillar -> pillar.anchor();
			case Shape.Nova nova -> nova.anchor();
			case Shape.Rain rain -> rain.anchor();
			case Shape.All all -> all.anchor();
			case Shape.Wave wave -> wave.origin();
			case Shape.Field field -> field.anchor();
			case Shape.Strike strike -> strike.anchor();
			case Shape.Beam beam -> beam.from();
			case Shape.Front front -> front.from();
			case Shape.Volley volley -> volley.from();
			case Shape.Chain chain -> chain.from();
			default -> Anchor.CASTER;
		};
	}

	/** One point for an anchor (the first point of multi-point anchors). */
	static Vec3 anchorPoint(Caster cast, Anchor anchor, Caster.Run run) {
		return switch (anchor.kind()) {
			case CASTER -> run.at != null ? run.at : cast.caster.position();
			case AIM -> cast.aim;
			case TARGET -> {
				LivingEntity target = cast.target();
				yield target != null ? target.position() : cast.aim;
			}
			case HIT -> run.hit != null ? run.hit.position() : cast.aim;
			case ORIGIN -> cast.origin;
			case AREA -> cast.areaCentre();
			case END -> cast.end;
			case AFTERIMAGE -> cast.afterimages.isEmpty() ? cast.origin : cast.afterimages.get(cast.afterimages.size() - 1);
			case SKY -> cast.aim.add(0.0D, 20.0D, 0.0D);
			case SUMMON -> run.at != null ? run.at : cast.caster.position();
			case CORNERS, EMITTERS -> {
				Fields.Area area = Fields.of(cast);
				yield area != null ? area.centre() : cast.aim;
			}
			case EACH_TARGET, DENSEST -> {
				List<Vec3> points = anchorPoints(cast, anchor, run, cast.aim);
				yield points.isEmpty() ? cast.aim : points.get(0);
			}
		};
	}

	/** Every point of an anchor: EACH_TARGET(n), DENSEST(n), AFTERIMAGE, CORNERS and EMITTERS give several. */
	static List<Vec3> anchorPoints(Caster cast, Anchor anchor, Caster.Run run, Vec3 single) {
		switch (anchor.kind()) {
			case EACH_TARGET, DENSEST -> {
				// found once per occurrence (one query) and shared by every reader of this run; Kehancuran points are
				// recorded by deliver for the points the evaluation really struck
				List<Vec3> points = run.anchors;
				if (points == null || run.anchorsKind != anchor.kind()) {
					points = anchor.kind() == Anchor.Kind.DENSEST ? densest(cast, anchor) : eachTarget(cast, anchor);
					run.anchors = points;
					run.anchorsKind = anchor.kind();
				}
				return points.isEmpty() ? List.of(single) : points;
			}
			case AFTERIMAGE -> {
				return cast.afterimages.isEmpty() ? List.of(cast.origin) : List.copyOf(cast.afterimages);
			}
			case CORNERS -> {
				Fields.Area area = Fields.of(cast);
				return area == null ? List.of(single) : area.corners();
			}
			case EMITTERS -> {
				Fields.Area area = Fields.of(cast);
				return area == null || area.emitters.isEmpty() ? List.of(single) : area.emitters;
			}
			default -> {
				return List.of(single);
			}
		}
	}

	/**
	 * A repeating step ({@code x n/k}) on a multi-point anchor (DENSEST(n), EACH_TARGET(n)) lands repetition k on point k
	 * (nine_suns_heavens_fury: nine suns on nine dense points), instead of every repetition striking every point.
	 */
	private static List<Vec3> perRepetition(List<Vec3> points, Caster.Run run, Anchor anchor) {
		if (points.size() <= 1 || run.step.repeat() <= 1 || (anchor.kind() != Anchor.Kind.DENSEST && anchor.kind() != Anchor.Kind.EACH_TARGET)) {
			return points;
		}
		return List.of(points.get(Math.floorMod(run.rep, points.size())));
	}

	/** Anchors the caster aims (clamped to the shape's range); the rest are places the cast already made. */
	static boolean aimedAnchor(Anchor anchor) {
		return switch (anchor.kind()) {
			case AIM, TARGET, DENSEST, EACH_TARGET, SKY -> true;
			default -> false;
		};
	}

	/** Anchors that give several points (their struck points become the Kehancuran points of the evaluation). */
	private static boolean multiPoint(Anchor anchor) {
		return switch (anchor.kind()) {
			case EACH_TARGET, DENSEST, CORNERS, EMITTERS, AFTERIMAGE -> true;
			default -> false;
		};
	}

	/** EACH_TARGET(n): the positions of the n hostiles nearest the caster (one query). */
	private static List<Vec3> eachTarget(Caster cast, Anchor anchor) {
		List<Vec3> points = new ArrayList<>();
		for (LivingEntity target : cast.hostiles(cast.caster.position(), Math.min(32.0D, cast.radiusCap()), false)) {
			if (points.size() >= anchor.n()) {
				break;
			}
			points.add(target.position());
		}
		return points;
	}

	/**
	 * One query for every point of a shape (22.15.1): the box around all points inflated by {@code reach} horizontally
	 * and by {@code below} / {@code above} vertically, answered once; the caller tests each candidate per point.
	 */
	private static List<LivingEntity> around(Caster cast, List<Vec3> points, double reach, double below, double above) {
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;
		for (Vec3 point : points) {
			minX = Math.min(minX, point.x);
			minY = Math.min(minY, point.y);
			minZ = Math.min(minZ, point.z);
			maxX = Math.max(maxX, point.x);
			maxY = Math.max(maxY, point.y);
			maxZ = Math.max(maxZ, point.z);
		}
		return cast.hostilesBox(new AABB(minX - reach, minY - below, minZ - reach, maxX + reach, maxY + above, maxZ + reach), false);
	}

	/** DENSEST(n): the n hostile positions with the most hostile neighbours within 4 blocks (one query, O(n²) over ≤ 32). */
	private static List<Vec3> densest(Caster cast, Anchor anchor) {
		List<LivingEntity> around = cast.hostiles(cast.aim, Math.min(anchor.search(), cast.radiusCap()), false);
		if (around.size() > TechniquePower.MAX_TARGETS) {
			around = around.subList(0, TechniquePower.MAX_TARGETS);
		}
		Map<LivingEntity, Integer> score = new LinkedHashMap<>();
		for (LivingEntity a : around) {
			int n = 0;
			for (LivingEntity b : around) {
				if (a != b && a.distanceToSqr(b) <= 16.0D) {
					n++;
				}
			}
			score.put(a, n);
		}
		List<LivingEntity> sorted = new ArrayList<>(score.keySet());
		sorted.sort(Comparator.comparingInt((LivingEntity entity) -> -score.get(entity)));
		List<Vec3> points = new ArrayList<>();
		for (LivingEntity entity : sorted) {
			boolean far = true;
			for (Vec3 point : points) {
				if (point.distanceToSqr(entity.position()) < 9.0D) {
					far = false;
					break;
				}
			}
			if (far) {
				points.add(entity.position());
			}
			if (points.size() >= anchor.n()) {
				break;
			}
		}
		return points;
	}

	/** The locked target, else the hostile nearest the look within range (TARGET LOCKED). */
	static @Nullable LivingEntity lockedOrFront(Caster cast, double range, boolean needLos, Caster.Run run) {
		LivingEntity locked = cast.target();
		Vec3 eye = cast.caster.getEyePosition();
		if (locked != null && locked.position().distanceTo(cast.caster.position()) <= range + 2.0D && (!needLos || los(cast, eye, locked))) {
			return locked;
		}
		LivingEntity best = null;
		double bestDot = 0.85D;
		// one query centred half-way along the look, so a 64-block reach fits the 32-radius query box (22.15.1)
		double reach = Math.min(range, cast.radiusCap() * 2.0D);
		Vec3 mid = eye.add(cast.look.scale(reach * 0.5D));
		double range2 = range * range;
		for (LivingEntity candidate : cast.hostiles(mid, reach * 0.5D + 2.0D, true)) {
			Vec3 to = candidate.getBoundingBox().getCenter().subtract(eye);
			if (to.lengthSqr() < 1.0E-4D || to.lengthSqr() > range2) {
				continue;
			}
			double dot = to.normalize().dot(cast.look);
			if (dot > bestDot && (!needLos || los(cast, eye, candidate))) {
				bestDot = dot;
				best = candidate;
			}
		}
		return best;
	}

	// ================================================================== helpers

	static double beamLength(Caster cast, Shape.Beam beam) {
		double cap = cast.spec.dewa() ? TechniquePower.BEAM_LENGTH_CAP_DEWA : TechniquePower.BEAM_LENGTH_CAP;
		if (beam.travelling() && cast.spec.dewa() && cast.spec.pamungkas()) {
			cap = TechniquePower.TRAVEL_CAP;
		}
		return cast.area(beam.length(), cap);
	}

	static Vec3 clampToRange(Caster cast, Vec3 point, double range) {
		Vec3 from = cast.caster.position();
		if (range <= 0.0D || point.distanceTo(from) <= range) {
			return point;
		}
		Vec3 dir = point.subtract(from).normalize();
		Vec3 far = from.add(dir.scale(range));
		BlockHitResult down = LoadedPath.ray(cast.level, far.add(0.0D, 2.0D, 0.0D), far.add(0.0D, -24.0D, 0.0D), null);
		return down.getType() == HitResult.Type.MISS ? far : down.getLocation();
	}

	static Vec3 horizontalLook(Caster cast) {
		Vec3 flat = cast.look.horizontal();
		return flat.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
	}

	static double angle(Vec3 a, Vec3 b) {
		return Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, a.dot(b)))));
	}

	/** Read-only line of sight from {@code from} to the target's middle (along a loaded path). */
	static boolean los(Caster cast, Vec3 from, LivingEntity target) {
		Vec3 middle = target.getBoundingBox().getCenter();
		BlockHitResult hit = LoadedPath.ray(cast.level, from, middle, cast.caster);
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(from) >= middle.distanceToSqr(from) - 0.25D;
	}

	private static void trim(Hits hits, int max) {
		while (hits.targets.size() > max) {
			LivingEntity removed = hits.targets.remove(hits.targets.size() - 1);
			hits.factors.remove(removed);
		}
	}

	private static void shuffle(List<LivingEntity> list, RandomSource random) {
		for (int i = list.size() - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			LivingEntity swap = list.get(i);
			list.set(i, list.get(j));
			list.set(j, swap);
		}
	}

	private static Vec3 fxTarget(Caster cast, Hits hits) {
		if (hits.direction != null && hits.direction.lengthSqr() > 1.0E-4D && hits.radius <= TechniquePower.BEAM_WIDTH_CAP) {
			return cast.end;
		}
		return cast.aim;
	}

	private static double fxLength(Caster cast, Hits hits) {
		return hits.centre.distanceTo(cast.end);
	}

	private static double previewRadius(Caster cast, Shape shape) {
		return switch (shape) {
			case Shape.Zone zone -> zone.radius().set() ? cast.area(zone.radius(), TechniquePower.ZONE_RADIUS_CAP_DEWA) : 5.0D;
			case Shape.Pillar pillar -> cast.area(pillar.radius(), TechniquePower.PILLAR_RADIUS_CAP);
			default -> 0.0D;
		};
	}

	// ================================================================== BLINK

	/** BLINK (22.3.3): a safe teleport (loaded chunks, never into a portal); chains wait ≥ 3 ticks between hops. */
	static void blink(Caster cast, Payload.Blink blink) {
		double range = Math.min(Caster.MOVE_CAP, cast.area(blink.range(), TechniquePower.BLINK_CAP));
		int chain = blink.chain().set() ? cast.count(blink.chain(), 12.0D) : 0;
		Set<LivingEntity> visited = new HashSet<>();
		LivingEntity first = blink.mode() == Payload.BlinkMode.BEHIND_TARGET || blink.mode() == Payload.BlinkMode.TO_TARGET
				? lockedOrFront(cast, range, true, new Caster.Run(Step.at(0), 0, null, null, 1.0D, DamageContext.Kind.HIT, false)) : null;
		if (!hop(cast, blink, range, first, visited)) {
			return;
		}
		for (int i = 1; i <= chain; i++) {
			int delay = Math.max(TechniquePower.TELEPORT_GAP, blink.gap()) * i;
			cast.activity(1);
			cast.schedule(delay, () -> {
				cast.activity(-1);
				LivingEntity next = null;
				double within = blink.chainRange() > 0.0D ? blink.chainRange() : range;
				for (LivingEntity candidate : cast.hostiles(cast.caster.position(), within, true)) {
					if (!visited.contains(candidate) && !(candidate instanceof Creeper)) {
						next = candidate;
						break;
					}
				}
				if (next != null) {
					hop(cast, blink, within, next, visited);
				}
			});
		}
	}

	private static boolean hop(Caster cast, Payload.Blink blink, double range, @Nullable LivingEntity target, Set<LivingEntity> visited) {
		Vec3 from = cast.caster.position();
		Vec3 spot = null;
		boolean skipPath = blink.noPathCheck() && cast.spec.dao() == Dao.SPACE;
		switch (blink.mode()) {
			case AIM -> {
				Vec3 eye = cast.caster.getEyePosition();
				BlockHitResult hit = LoadedPath.ray(cast.level, eye, eye.add(cast.look.scale(range)), cast.caster);
				Vec3 aim = hit.getLocation();
				if (hit.getType() != HitResult.Type.MISS) {
					Direction face = hit.getDirection();
					double dy = face == Direction.DOWN ? -cast.caster.getBbHeight() - 0.1D : face.getStepY() * 0.1D;
					aim = aim.add(face.getStepX() * 0.6D, dy, face.getStepZ() * 0.6D);
				}
				spot = cast.safeSpot(aim);
				if (spot != null && !skipPath && !cast.clear(eye, spot.add(0.0D, cast.caster.getBbHeight() * 0.5D, 0.0D))
						&& !cast.clear(aim, spot.add(0.0D, cast.caster.getBbHeight() * 0.5D, 0.0D))) {
					spot = null;
				}
			}
			case BEHIND_TARGET, TO_TARGET -> {
				if (target == null || target instanceof Creeper) {
					return false;
				}
				visited.add(target);
				Vec3 approach = target.position().subtract(from).horizontal();
				approach = approach.lengthSqr() < 1.0E-4D ? horizontalLook(cast) : approach.normalize();
				double base = target.getBbWidth() * 0.5D + cast.caster.getBbWidth() * 0.5D;
				double sign = blink.mode() == Payload.BlinkMode.BEHIND_TARGET ? 1.0D : -1.0D;
				for (double gap : new double[]{0.9D, 0.5D, 1.5D}) {
					Vec3 candidate = cast.safeSpot(target.position().add(approach.scale(sign * (base + gap))));
					if (candidate != null && Math.abs(candidate.y - target.getY()) <= 2.5D
							&& (skipPath || cast.clear(target.getBoundingBox().getCenter(), candidate.add(0.0D, cast.caster.getBbHeight() * 0.5D, 0.0D)))) {
						spot = candidate;
						break;
					}
				}
			}
			case BACK -> {
				Vec3 back = horizontalLook(cast).scale(-1.0D);
				for (double distance = range; distance >= 3.0D && spot == null; distance -= 3.0D) {
					Vec3 candidate = cast.safeSpot(from.add(back.scale(distance)));
					if (candidate == null || !skipPath && !cast.clear(from.add(0.0D, 1.0D, 0.0D), candidate.add(0.0D, 1.0D, 0.0D))) {
						continue;
					}
					if (blink.safeFrom() > 0.0D && !cast.hostiles(candidate, blink.safeFrom(), false).isEmpty()) {
						continue;
					}
					spot = candidate;
				}
			}
			case ORIGIN -> spot = cast.safeSpot(cast.origin);
		}
		if (spot == null || spot.distanceTo(from) > Caster.MOVE_CAP + 2.0D) {
			return false;
		}
		Float yaw = null;
		if (target != null && target.isAlive()) {
			// a hop that must face its target turns the caster for real (absolute yaw: the player's camera turns too)
			yaw = Moves.yawTo(spot.add(0.0D, cast.caster.getEyeHeight(), 0.0D), target.getBoundingBox().getCenter());
		}
		if (blink.iframes() > 0) {
			// i-frames during the chain: every hop renews them, so a chain with iframes ≥ its gap is never hit in between
			Boons.iframes(cast.caster, blink.iframes() + (blink.leaping() ? Moves.LEAP_TICKS : 0));
		}
		cast.markPath(from, spot);
		cast.addAfterimage(from);
		int ticks = blink.leaping() ? Moves.LEAP_TICKS : 1;
		cast.fx(Phase.TRAVEL, from, spot, cast.caster.getBbWidth(), (float) from.distanceTo(spot), 1, ticks, target != null ? target.getId() : -1, 0, 1, 0,
				Payloads.TechFx.FLAG_BLINK);
		Step hopStep = blink.hop();
		LivingEntity hopTarget = target;
		Runnable landed = hopStep == null ? null : () -> execute(cast, new Caster.Run(hopStep, 0, hopTarget, null, 1.0D, DamageContext.Kind.HIT, false));
		if (blink.leaping()) {
			// S16: a leap is a parabola (no fall damage), the landing step runs on landing
			Boons.noFall(cast.caster, Moves.LEAP_TICKS + 40);
			double lift = Math.max(2.0D, Math.min(6.0D, from.distanceTo(spot) * 0.25D));
			Moves.glide(cast, from, spot, Moves.LEAP_TICKS, lift, yaw, landed);
		} else {
			if (yaw != null) {
				cast.teleport(spot, yaw, cast.caster.getXRot());
			} else {
				cast.teleport(spot);
			}
			if (landed != null) {
				landed.run();
			}
		}
		return true;
	}

	// ================================================================== target precheck (22.2.6 step 4)

	/**
	 * True when the cast can reach a hostile: the first damaging step's instant shape finds one, or any hostile is within
	 * 32 for delayed or travelling shapes. REQUIRES_TARGET needs a locked target.
	 */
	static boolean anyTarget(Caster cast) {
		if (cast.spec.has(dev.kultivator.data.TechniqueSpec.Flag.REQUIRES_TARGET)) {
			LivingEntity locked = lockedOrFront(cast, 64.0D, false, new Caster.Run(Step.at(0), 0, null, null, 1.0D, DamageContext.Kind.HIT, false));
			if (locked == null) {
				return false;
			}
			cast.target = locked;
		}
		for (Step step : cast.spec.timeline()) {
			if (step.hit().isEmpty() || step.shape() instanceof Shape.Self) {
				continue;
			}
			Shape shape = step.shape();
			boolean instant = step.at() == 0 && step.timing() == Step.Timing.AT && switch (shape) {
				case Shape.Strike strike -> true;
				case Shape.Cone cone -> true;
				case Shape.Nova nova -> nova.anchor().kind() == Anchor.Kind.CASTER;
				case Shape.Target target -> true;
				case Shape.All all -> all.anchor().kind() != Anchor.Kind.AREA;
				case Shape.Beam beam -> !beam.travelling();
				default -> false;
			};
			if (instant) {
				Hits hits = evaluate(cast, new Caster.Run(step, 0, null, null, 1.0D, DamageContext.Kind.HIT, false), shape);
				if (!hits.targets.isEmpty()) {
					return true;
				}
				if (shape instanceof Shape.Strike || shape instanceof Shape.Cone || shape instanceof Shape.Target) {
					return false;
				}
			}
			if (!cast.hostiles(cast.caster.position(), 32.0D, false).isEmpty()) {
				return true;
			}
			// Long-range arts (fields, zones, fronts and volleys up to 64 blocks away): look where the first one lands too.
			Vec3 probe = reachProbe(cast);
			return probe != null && !cast.hostiles(probe, REACH_PROBE_RADIUS, false).isEmpty();
		}
		return true;
	}

	/** Radius of the second target check around where a long-range art lands. */
	private static final double REACH_PROBE_RADIUS = 24.0D;

	/**
	 * Where the first far-reaching shape of the timeline lands: the aim clamped to a FIELD's, ZONE's or RAIN's range (aimed
	 * anchors only), a VOLLEY's range, or a FRONT's midpoint. Null when the timeline has none.
	 */
	private static @Nullable Vec3 reachProbe(Caster cast) {
		Caster.Run run = new Caster.Run(Step.at(0), 0, null, null, 1.0D, DamageContext.Kind.HIT, false);
		for (Step step : cast.spec.timeline()) {
			switch (step.shape()) {
				case Shape.Field field when aimedAnchor(field.anchor()) -> {
					return clampToRange(cast, anchorPoint(cast, field.anchor(), run), cast.area(field.range(), TechniquePower.ZONE_RANGE_CAP));
				}
				case Shape.Zone zone when aimedAnchor(zone.anchor()) -> {
					return clampToRange(cast, anchorPoint(cast, zone.anchor(), run), cast.area(zone.range(), TechniquePower.ZONE_RANGE_CAP));
				}
				case Shape.Rain rain when aimedAnchor(rain.anchor()) -> {
					return clampToRange(cast, anchorPoint(cast, rain.anchor(), run), cast.area(rain.range(), TechniquePower.ZONE_RANGE_CAP));
				}
				case Shape.Volley volley -> {
					return clampToRange(cast, cast.aim, cast.area(volley.range(), TechniquePower.ZONE_RANGE_CAP));
				}
				case Shape.Front front -> {
					double length = cast.area(front.length(), TechniquePower.FRONT_LENGTH_CAP);
					return cast.caster.getEyePosition().add(cast.look.scale(length * 0.5D));
				}
				default -> {
				}
			}
		}
		return null;
	}

	/** True when the spec has hostile payloads at all (attack specs are prechecked). */
	static boolean attacks(dev.kultivator.data.TechniqueSpec spec) {
		for (Step step : spec.timeline()) {
			if (!step.hit().isEmpty() && !(step.shape() instanceof Shape.Self)) {
				return true;
			}
		}
		return false;
	}

	private Steps() {
	}
}
