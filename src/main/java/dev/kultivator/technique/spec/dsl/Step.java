package dev.kultivator.technique.spec.dsl;

import java.util.ArrayList;
import java.util.List;

/**
 * One step of a timeline (DESIGN 22.3): runs at tick {@code at} (or when the timeline ends, {@link Timing#END}),
 * optionally {@code repeat} times every {@code every} ticks ({@code x n/k}), with one shape, hostile payloads
 * ({@code hit}), caster payloads ({@code self}), ally payloads ({@code ally} within {@code allyRadius}, 0 = the shape's
 * own radius), conditions ({@code ? COND ×m} or extra payloads) and onHit / onKill / onEnd sub-steps.
 * <ul>
 * <li>{@code stage} > 0 runs only in that stage of a STAGES spec ({@code orWindowEnd}: also when the window runs
 * out);</li>
 * <li>{@code minTier} > 0 needs that tier index t ("t≥4: ...");</li>
 * <li>{@code only}: the step runs only while this condition holds (checked per target for TARGET_* and HIT_BEFORE
 * conditions, otherwise once; "if all 8 hit one target", "? Divine Sense active");</li>
 * <li>{@code growPerRepeat}: damage × (1 + g × k) on repetition k (0-based);</li>
 * <li>{@code extraPhase} ≥ 0 sends the step's FX as {@code Phase.extra(n)}.</li>
 * </ul>
 */
public record Step(Timing timing, int at, int repeat, int every, int stage, boolean orWindowEnd, int minTier, Cond only,
		Shape shape, List<Payload.Hit> hit, List<Payload.Boon> self, List<Payload.Boon> ally, double allyRadius, List<When> when,
		List<Step> onHit, List<Step> onKill, List<Step> onEnd, double growPerRepeat, int extraPhase) {

	public enum Timing { AT, END }

	/**
	 * {@code ? cond ×mult} and/or extra payloads when the condition holds (checked per target for TARGET_* conditions,
	 * per cast otherwise). {@code self} boons go to the receivers of the step's SELF and ALLY lists.
	 */
	public record When(Cond cond, double mult, List<Payload.Hit> hit, List<Payload.Boon> self) {
	}

	/** A step at tick {@code tick} with the SELF shape and nothing else (fill it with the fluent copies). */
	public static Step at(int tick) {
		return new Step(Timing.AT, Math.max(0, tick), 1, 0, 0, false, 0, Cond.NONE, Shape.Self.INSTANCE, List.of(), List.of(),
				List.of(), 0.0D, List.of(), List.of(), List.of(), List.of(), 0.0D, -1);
	}

	/** A step that runs when the timeline (or the effect it belongs to: a bind, a field, a form) ends. */
	public static Step atEnd() {
		return new Step(Timing.END, 0, 1, 0, 0, false, 0, Cond.NONE, Shape.Self.INSTANCE, List.of(), List.of(), List.of(), 0.0D,
				List.of(), List.of(), List.of(), List.of(), 0.0D, -1);
	}

	public Step shape(Shape value) {
		return new Step(timing, at, repeat, every, stage, orWindowEnd, minTier, only, value, hit, self, ally, allyRadius, when, onHit, onKill, onEnd, growPerRepeat, extraPhase);
	}

	/** Adds hostile payloads. */
	public Step hit(Payload.Hit... payloads) {
		return new Step(timing, at, repeat, every, stage, orWindowEnd, minTier, only, shape, add(hit, payloads), self, ally, allyRadius, when, onHit, onKill, onEnd, growPerRepeat, extraPhase);
	}

	/** Adds caster payloads. */
	public Step self(Payload.Boon... payloads) {
		return new Step(timing, at, repeat, every, stage, orWindowEnd, minTier, only, shape, hit, add(self, payloads), ally, allyRadius, when, onHit, onKill, onEnd, growPerRepeat, extraPhase);
	}

	/** Adds ally payloads (allies within the shape's radius). */
	public Step ally(Payload.Boon... payloads) {
		return new Step(timing, at, repeat, every, stage, orWindowEnd, minTier, only, shape, hit, self, add(ally, payloads), allyRadius, when, onHit, onKill, onEnd, growPerRepeat, extraPhase);
	}

	/** Adds ally payloads for allies within {@code radius} of the caster. */
	public Step ally(double radius, Payload.Boon... payloads) {
		return new Step(timing, at, repeat, every, stage, orWindowEnd, minTier, only, shape, hit, self, add(ally, payloads), radius, when, onHit, onKill, onEnd, growPerRepeat, extraPhase);
	}

	/** {@code ? cond ×mult}. */
	public Step when(Cond cond, double mult) {
		return withWhen(new When(cond, mult, List.of(), List.of()));
	}

	/** {@code ? cond ×mult} plus extra hostile payloads. */
	public Step when(Cond cond, double mult, Payload.Hit... extra) {
		return withWhen(new When(cond, mult, List.of(extra), List.of()));
	}

	/** Extra caster / ally payloads while {@code cond} holds ("SELF ? CASTER_LOW_HP(.5): HEAL .3"). */
	public Step whenSelf(Cond cond, Payload.Boon... extra) {
		return withWhen(new When(cond, 1.0D, List.of(), List.of(extra)));
	}

	private Step withWhen(When value) {
		List<When> list = new ArrayList<>(when);
		list.add(value);
		return new Step(timing, at, repeat, every, stage, orWindowEnd, minTier, only, shape, hit, self, ally, allyRadius, List.copyOf(list), onHit, onKill, onEnd, growPerRepeat, extraPhase);
	}

	public Step onHit(Step... steps) {
		return new Step(timing, at, repeat, every, stage, orWindowEnd, minTier, only, shape, hit, self, ally, allyRadius, when, add(onHit, steps), onKill, onEnd, growPerRepeat, extraPhase);
	}

	public Step onKill(Step... steps) {
		return new Step(timing, at, repeat, every, stage, orWindowEnd, minTier, only, shape, hit, self, ally, allyRadius, when, onHit, add(onKill, steps), onEnd, growPerRepeat, extraPhase);
	}

	public Step onEnd(Step... steps) {
		return new Step(timing, at, repeat, every, stage, orWindowEnd, minTier, only, shape, hit, self, ally, allyRadius, when, onHit, onKill, add(onEnd, steps), growPerRepeat, extraPhase);
	}

	/** {@code x n/k}: runs {@code times} times, {@code every} ticks apart. */
	public Step repeat(int times, int every) {
		return new Step(timing, at, Math.max(1, times), Math.max(1, every), stage, orWindowEnd, minTier, only, shape, hit, self, ally, allyRadius, when, onHit, onKill, onEnd, growPerRepeat, extraPhase);
	}

	/** Only in stage {@code n} (1-based) of a STAGES spec. */
	public Step stage(int n) {
		return new Step(timing, at, repeat, every, Math.max(0, n), orWindowEnd, minTier, only, shape, hit, self, ally, allyRadius, when, onHit, onKill, onEnd, growPerRepeat, extraPhase);
	}

	/** In stage {@code n}, or when the window runs out before it ("st2 or end: release"). */
	public Step stageOrEnd(int n) {
		return new Step(timing, at, repeat, every, Math.max(0, n), true, minTier, only, shape, hit, self, ally, allyRadius, when, onHit, onKill, onEnd, growPerRepeat, extraPhase);
	}

	/** Only from tier index {@code t} ("t≥4: ..."). */
	public Step minTier(int t) {
		return new Step(timing, at, repeat, every, stage, orWindowEnd, Math.max(0, t), only, shape, hit, self, ally, allyRadius, when, onHit, onKill, onEnd, growPerRepeat, extraPhase);
	}

	/** The step runs only while {@code cond} holds. */
	public Step only(Cond cond) {
		return new Step(timing, at, repeat, every, stage, orWindowEnd, minTier, cond, shape, hit, self, ally, allyRadius, when, onHit, onKill, onEnd, growPerRepeat, extraPhase);
	}

	/** Damage × (1 + {@code perRepeat} × k) on repetition k ("DMG .2 × (1 + .15k)"). */
	public Step grow(double perRepeat) {
		return new Step(timing, at, repeat, every, stage, orWindowEnd, minTier, only, shape, hit, self, ally, allyRadius, when, onHit, onKill, onEnd, perRepeat, extraPhase);
	}

	/** Sends this step's FX as {@code Phase.extra(index)} instead of IMPACT/PULSE. */
	public Step phase(int extraIndex) {
		return new Step(timing, at, repeat, every, stage, orWindowEnd, minTier, only, shape, hit, self, ally, allyRadius, when, onHit, onKill, onEnd, growPerRepeat, Math.max(0, Math.min(7, extraIndex)));
	}

	/** The tick of the last repetition. */
	public int lastTick() {
		return at + Math.max(0, repeat - 1) * every;
	}

	@SafeVarargs
	private static <T> List<T> add(List<T> base, T... more) {
		List<T> list = new ArrayList<>(base);
		list.addAll(List.of(more));
		return List.copyOf(list);
	}
}
