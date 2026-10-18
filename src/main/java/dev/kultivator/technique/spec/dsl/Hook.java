package dev.kultivator.technique.spec.dsl;

import org.jetbrains.annotations.Nullable;

/**
 * A timed hook granted by {@code GRANT_HOOK} (DESIGN 22.3.3): when {@code trigger} fires on the caster, {@code step}
 * runs (anchored at the hit or the attacker, see {@link Anchor#HIT}). Lasts {@code ticks} (NONE = as long as the step
 * that granted it, e.g. a toggle or form) and at most {@code charges} times (0 = unlimited); {@code last} runs instead
 * of {@code step} on the final charge ("the 3rd also FRZ 40t"); internal cooldown {@code icd} ≥ 4 ticks and at most
 * {@code perSecond} triggers per second (0 = no limit). Filters: {@code minShare} of max health taken (ON_HURT), the
 * attacker in front ({@code frontOnly}), only while holding a sword ({@code needsSword}).
 */
public record Hook(Trigger trigger, Val ticks, int charges, int icd, int perSecond, double minShare, boolean frontOnly,
		boolean needsSword, Step step, @Nullable Step last) {

	public enum Trigger {
		/** The caster lands a melee hit. */
		ON_MELEE_HIT,
		/** The caster is hurt (by a melee attacker unless stated). */
		ON_HURT,
		/** The caster kills something. */
		ON_KILL,
		/** One of the caster's techniques hits. */
		ON_TECHNIQUE_HIT,
		/** Melee or technique hits. */
		ON_ANY_HIT
	}

	public static final int MIN_ICD = 4;

	public static Hook onMelee(Step step) {
		return new Hook(Trigger.ON_MELEE_HIT, Val.NONE, 0, MIN_ICD, 0, 0.0D, false, false, step, null);
	}

	public static Hook onHurt(Step step) {
		return new Hook(Trigger.ON_HURT, Val.NONE, 0, MIN_ICD, 0, 0.0D, false, false, step, null);
	}

	public static Hook onKill(Step step) {
		return new Hook(Trigger.ON_KILL, Val.NONE, 0, MIN_ICD, 0, 0.0D, false, false, step, null);
	}

	public static Hook onTechniqueHit(Step step) {
		return new Hook(Trigger.ON_TECHNIQUE_HIT, Val.NONE, 0, MIN_ICD, 0, 0.0D, false, false, step, null);
	}

	public static Hook onAnyHit(Step step) {
		return new Hook(Trigger.ON_ANY_HIT, Val.NONE, 0, MIN_ICD, 0, 0.0D, false, false, step, null);
	}

	public Hook ticks(double value) { return new Hook(trigger, Val.of(value), charges, icd, perSecond, minShare, frontOnly, needsSword, step, last); }
	public Hook ticks(double base, double cap) { return new Hook(trigger, Val.of(base, cap), charges, icd, perSecond, minShare, frontOnly, needsSword, step, last); }
	public Hook charges(int value) { return new Hook(trigger, ticks, value, icd, perSecond, minShare, frontOnly, needsSword, step, last); }
	public Hook icd(int value) { return new Hook(trigger, ticks, charges, Math.max(MIN_ICD, value), perSecond, minShare, frontOnly, needsSword, step, last); }
	public Hook perSecond(int value) { return new Hook(trigger, ticks, charges, icd, value, minShare, frontOnly, needsSword, step, last); }
	public Hook minShare(double value) { return new Hook(trigger, ticks, charges, icd, perSecond, value, frontOnly, needsSword, step, last); }
	public Hook front() { return new Hook(trigger, ticks, charges, icd, perSecond, minShare, true, needsSword, step, last); }
	public Hook swordOnly() { return new Hook(trigger, ticks, charges, icd, perSecond, minShare, frontOnly, true, step, last); }
	/** Runs {@code value} instead of the step on the final charge. */
	public Hook lastCharge(Step value) { return new Hook(trigger, ticks, charges, icd, perSecond, minShare, frontOnly, needsSword, step, value); }
}
