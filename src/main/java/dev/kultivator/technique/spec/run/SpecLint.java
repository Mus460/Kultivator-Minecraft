package dev.kultivator.technique.spec.run;

import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.technique.spec.dsl.Bonus;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.Step;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The polish-pass spec checks (DESIGN 27.1 S2): pure reads of a spec, shared by the dev-time registry warning and the
 * {@code uji teknik audit} command. A phase with layers that no step of the spec can send is drawn by nobody (the
 * "only the camera moves" bug); template parameters the client ignores, a missing model resource and an ultimate
 * without a climax phase are named too. Specials and legacy specs send phases from their own code and are only checked
 * for template parameters.
 */
public final class SpecLint {

	/** Templates whose size reads as a geometry multiplier from 3 up (use {@code radius} instead, S14). */
	private static final Set<FxLayer.Template> SIZE_AS_RADIUS = EnumSet.of(FxLayer.Template.SHOCK_RING, FxLayer.Template.SHELL,
			FxLayer.Template.RUNE_CIRCLE, FxLayer.Template.MIST, FxLayer.Template.DOME, FxLayer.Template.CLOUD_DISC, FxLayer.Template.VORTEX,
			FxLayer.Template.SHIELD_SHELL);

	/** Every warning for {@code spec} (empty when it is clean). */
	public static List<String> check(TechniqueSpec spec) {
		List<String> out = new ArrayList<>();
		boolean ownCode = spec.isLegacy() || spec.special() != null;
		Set<Phase> sent = emittable(spec);
		for (Map.Entry<Phase, List<FxLayer>> entry : spec.fx().entrySet()) {
			Phase phase = entry.getKey();
			if (!ownCode && !entry.getValue().isEmpty() && !sent.contains(phase)) {
				out.add("phase " + phase + " has " + entry.getValue().size() + " layer(s) but no step sends it");
			}
			for (FxLayer layer : entry.getValue()) {
				template(spec, phase, layer, out);
			}
		}
		if ((spec.type() == TechniqueType.PAMUNGKAS || spec.type() == TechniqueType.WILAYAH) && spec.climax() == null
				&& spec.climaxPhase() == Phase.CAST && !ownCode) {
			out.add("an ultimate or domain without a climax phase (.climax(phase) or layers on IMPACT/TRAVEL/EXTRA)");
		}
		if (spec.climax() != null && !ownCode && !sent.contains(spec.climax())) {
			out.add("climax " + spec.climax() + " is never sent");
		}
		dampak(spec, ownCode, out);
		return out;
	}

	private static void template(TechniqueSpec spec, Phase phase, FxLayer layer, List<String> out) {
		FxLayer.Template t = layer.template();
		String where = phase + " " + t;
		switch (layer.variant()) {
			case VERTICAL -> {
				if (t != FxLayer.Template.RUNE_CIRCLE && t != FxLayer.Template.CRESCENT_FLY && t != FxLayer.Template.SHOCK_RING) {
					out.add(where + ": VERTICAL is only drawn on RUNE_CIRCLE, CRESCENT_FLY and SHOCK_RING");
				}
			}
			case SPLIT -> {
				if (t != FxLayer.Template.DOME && t != FxLayer.Template.AVATAR) {
					out.add(where + ": SPLIT is only drawn on DOME and AVATAR");
				}
			}
			case FANNED -> {
				if (t != FxLayer.Template.BOLT && t != FxLayer.Template.SWORD_SHAPE && t != FxLayer.Template.CRESCENT_FLY) {
					out.add(where + ": FANNED is only drawn on BOLT, SWORD_SHAPE and CRESCENT_FLY");
				}
			}
			default -> {
			}
		}
		if (SIZE_AS_RADIUS.contains(t) && layer.size() >= 3.0F) {
			out.add(where + ": size " + layer.size() + " reads as a geometry multiplier; use radius(...)");
		}
		if (t == FxLayer.Template.MODEL && !layer.hasModel()) {
			out.add(where + ": a MODEL layer without .model(id)");
		}
		if (layer.hasModel() && SpecLint.class.getResource("/assets/kultivator/items/fx/" + layer.model() + ".json") == null) {
			out.add(where + ": model '" + layer.model() + "' has no assets/kultivator/items/fx/" + layer.model() + ".json");
		}
		if (layer.followUntil() == Phase.ABORT) {
			out.add(where + ": until(ABORT) is implied; name the phase that ends it");
		}
	}

	/** The phases some step, flow or payload of the spec can send (S2). */
	public static Set<Phase> emittable(TechniqueSpec spec) {
		EnumSet<Phase> sent = EnumSet.of(Phase.CAST, Phase.HIT, Phase.MASS_HIT, Phase.END, Phase.ABORT);
		if (spec.charge() != null) {
			sent.add(Phase.CHARGE);
		}
		if (spec.channel() != null) {
			sent.add(Phase.PULSE);
		}
		List<Step> steps = new ArrayList<>(spec.timeline());
		for (Bonus bonus : spec.perfection()) {
			steps.addAll(bonus.steps());
		}
		for (Bonus bonus : spec.peak()) {
			steps.addAll(bonus.steps());
		}
		for (Step step : steps) {
			walk(step, sent, 0);
		}
		return sent;
	}

	private static void walk(Step step, Set<Phase> sent, int depth) {
		if (step == null || depth > 6) {
			return;
		}
		Shape shape = step.shape();
		Phase own = step.extraPhase() >= 0 ? Phase.extra(step.extraPhase()) : null;
		switch (shape.kind()) {
			case SELF -> {
				if (own != null) {
					sent.add(own);
				}
			}
			case DOMAIN, FIELD, AURA -> {
				sent.add(Phase.PULSE);
				if (own != null) {
					sent.add(own);
				}
			}
			case WAVE, FRONT, CHAIN, VOLLEY -> {
				sent.add(own != null ? own : Phase.TRAVEL);
				if (shape.kind() == Shape.Kind.VOLLEY) {
					sent.add(Phase.LAND);
				}
			}
			case BEAM -> sent.add(own != null ? own : Phase.TRAVEL);
			case RAIN -> {
				sent.add(own != null ? own : Phase.IMPACT);
				sent.add(Phase.PULSE);
				sent.add(Phase.TRAVEL);
			}
			case TARGET -> {
				sent.add(own != null ? own : Phase.TRAVEL);
				sent.add(Phase.IMPACT);
			}
			case ZONE, PILLAR -> {
				sent.add(own != null ? own : Phase.IMPACT);
				sent.add(Phase.TRAVEL);
				if (step.repeat() > 1) {
					sent.add(Phase.PULSE);
				}
			}
			default -> {
				sent.add(own != null ? own : Phase.IMPACT);
				if (step.repeat() > 1) {
					sent.add(Phase.PULSE);
				}
				if (shape.kind() == Shape.Kind.ALL || shape.kind() == Shape.Kind.RECALL) {
					sent.add(Phase.TRAVEL);
				}
			}
		}
		if (!step.ally().isEmpty()) {
			sent.add(Phase.IMPACT);
		}
		for (Payload.Boon boon : step.self()) {
			boon(boon, sent, depth);
		}
		for (Step.When when : step.when()) {
			for (Payload.Boon boon : when.self()) {
				boon(boon, sent, depth);
			}
		}
		for (List<Step> children : List.of(step.onHit(), step.onKill(), step.onEnd())) {
			for (Step child : children) {
				walk(child, sent, depth + 1);
			}
		}
	}

	private static void boon(Payload.Boon boon, Set<Phase> sent, int depth) {
		switch (boon) {
			case Payload.Dash dash -> sent.add(Phase.TRAVEL);
			case Payload.Blink blink -> {
				sent.add(Phase.TRAVEL);
				walk(blink.hop(), sent, depth + 1);
			}
			case Payload.Summon summon -> {
				sent.add(Phase.PHANTOM);
				sent.add(Phase.PULSE);
				walk(summon.attack(), sent, depth + 1);
			}
			case Payload.GrantHook grant -> {
				walk(grant.hook().step(), sent, depth + 1);
				walk(grant.hook().last(), sent, depth + 1);
			}
			case Payload.Counter counter -> {
				// EXTRA6: a blow stored by the counter (QiShield)
				sent.add(Phase.EXTRA6);
				walk(counter.release(), sent, depth + 1);
			}
			case Payload.ProjectileGuard guard -> sent.add(Phase.EXTRA7);
			case Payload.CheatDeath cheat -> walk(cheat.onTrigger(), sent, depth + 1);
			case Payload.Transform transform -> {
				for (Payload.Boon effect : transform.effects()) {
					boon(effect, sent, depth + 1);
				}
			}
			default -> {
			}
		}
	}

	/** EpicDirector's ultimate rule (DESIGN 23, 28.12): a PAMUNGKAS of grade Abadi or higher, or any Dewa spec. */
	public static boolean ultimate(TechniqueSpec spec) {
		return spec.pamungkas() && spec.grade().tier() >= TechniqueGrade.ABADI.tier() || spec.dewa();
	}

	/** The Phase 10 checks (DESIGN 28.14 M1): rows of 28.12 that cannot play as authored. */
	private static void dampak(TechniqueSpec spec, boolean ownCode, List<String> out) {
		Dampak dampak = spec.dampak();
		if (dampak == null) {
			if (ultimate(spec) && !spec.isLegacy()) {
				out.add("an Abadi or Dewa ultimate without a Dampak row (.dampak(...), DESIGN 28.12)");
			}
			return;
		}
		if (dampak.rideTicks() > Dampak.MAX_RIDE_TICKS) {
			out.add("Dampak rideTicks " + dampak.rideTicks() + " > " + Dampak.MAX_RIDE_TICKS);
		}
		// Phase 10b (DESIGN 29.7.3): the Film Pusaka belongs to the Titah decrees only, at most 240 ticks in all
		if (dampak.ride() == Dampak.Ride.FILM && !spec.titah()) {
			out.add("Dampak ride FILM without the TITAH flag");
		}
		if (dampak.ride() == Dampak.Ride.FILM && (dampak.filmTicks() <= 0 || dampak.filmTicks() > Dampak.MAX_FILM_TICKS)) {
			out.add("Dampak filmTicks " + dampak.filmTicks() + " outside 1-" + Dampak.MAX_FILM_TICKS);
		}
		if (dampak.ride() == Dampak.Ride.FULL && dampak.climaxAt() < dampak.rideTicks() && spec.charge() == null) {
			out.add("Dampak FULL ride of " + dampak.rideTicks() + " ticks cannot end on a climax at " + dampak.climaxAt() + " (not charged)");
		}
		if (!ownCode) {
			int first = firstClimaxTick(spec);
			if (first >= 0 && first != dampak.climaxAt()) {
				out.add("Dampak climaxAt " + dampak.climaxAt() + " but the first step sending " + spec.climaxPhase() + " runs at " + first);
			}
		}
		if (dampak.sigilTrench() && spec.destruction().isEmpty() && !spec.titah()) {
			// (a Titah decree burns its trenches through Kehancuran.submitTitah, not through spec shapes)
			out.add("Dampak sigilTrench without Mode Kehancuran shapes");
		}
		String monument = dampak.monument();
		if (monument != null && !Dampak.MONUMENT_BY_DAO.equals(monument)
				&& SpecLint.class.getResource("/assets/kultivator/items/fx/" + monument + ".json") == null) {
			out.add("Dampak monument '" + monument + "' has no assets/kultivator/items/fx/" + monument + ".json");
		}
		if (dampak.monumentSeconds() < 30 || dampak.monumentSeconds() > 180) {
			out.add("Dampak monumentSeconds " + dampak.monumentSeconds() + " outside 30-180");
		}
	}

	/** The step tick of the first timed step that sends the climax phase, or -1 (delayed zones and pillars are skipped). */
	private static int firstClimaxTick(TechniqueSpec spec) {
		Phase climax = spec.climaxPhase();
		int best = -1;
		for (Step step : spec.timeline()) {
			if (step.timing() != Step.Timing.AT || step.stage() > 1) {
				continue;
			}
			Shape.Kind kind = step.shape().kind();
			if (kind == Shape.Kind.ZONE || kind == Shape.Kind.PILLAR || kind == Shape.Kind.RAIN) {
				continue;
			}
			EnumSet<Phase> phases = EnumSet.noneOf(Phase.class);
			walkOwn(step, phases);
			if (phases.contains(climax) && (best < 0 || step.at() < best)) {
				best = step.at();
			}
		}
		return best;
	}

	/** The phases one step sends by itself (no children, no boons). */
	private static void walkOwn(Step step, Set<Phase> sent) {
		Phase own = step.extraPhase() >= 0 ? Phase.extra(step.extraPhase()) : null;
		switch (step.shape().kind()) {
			case SELF -> {
				if (own != null) {
					sent.add(own);
				}
			}
			case DOMAIN, FIELD, AURA -> {
				if (own != null) {
					sent.add(own);
				}
			}
			case WAVE, FRONT, CHAIN, VOLLEY, BEAM, TARGET -> sent.add(own != null ? own : Phase.TRAVEL);
			default -> sent.add(own != null ? own : Phase.IMPACT);
		}
	}

	private SpecLint() {
	}
}
