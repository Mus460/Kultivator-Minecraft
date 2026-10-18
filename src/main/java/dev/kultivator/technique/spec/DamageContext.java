package dev.kultivator.technique.spec;

import dev.kultivator.data.TechniqueSpec;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * One technique hit for {@link TechniquePower#finalDamage} (DESIGN 22.5.5), built by the choke points
 * ({@code Techniques.hurt}, {@code DiscipleTechniques.hurt}, {@code PowerCore.hurt}) or a {@code CastContext}.
 * <ul>
 * <li>{@code LEGACY}: {@code raw} is a finished pre-6c amount and passes unchanged (the 23 legacy techniques keep their
 * numbers until P6c-mesin moves them to coefficients);</li>
 * <li>{@code PERCENT}: {@code coef} is a share of max health, {@code bossCoef} the share on bosses;</li>
 * <li>{@code EXEC}: lethal (bosses only below 5% health, never players);</li>
 * <li>every other kind: {@code coef × power × mastery damage × f}, then the arena, trial-mob, heart-demon and boss rules.
 * DOT and PULSE ticks are also at most 2% of a boss's max health each.</li>
 * </ul>
 * {@code power} is the P snapshot of the cast; {@code f} the product of falloff, conditions, stacks, marks, versus and
 * empower (clamped to ×3, ×1.5 in an arena); {@code precise} lets calm neutrals be hit and sends dragon hits to the head;
 * {@code pierce} uses {@code kultivator:technique_pierce}; {@code ledger} is the cast's per-target record (null = a single
 * hit with a fresh cap).
 */
public record DamageContext(Kind kind, LivingEntity attacker, LivingEntity target, @Nullable TechniqueSpec spec, double coef,
		double bossCoef, double raw, double power, int mastery, double f, boolean precise, boolean pierce,
		@Nullable CastLedger ledger) {

	public enum Kind {
		LEGACY, HIT, DOT, PULSE, LINK, REFLECT, ECHO, PHANTOM, COUNTER, PERCENT, EXEC;

		/** Follow-ups that are also capped at 2% of a boss's max health per tick (22.14.2). */
		public boolean tick() {
			return this == DOT || this == PULSE;
		}
	}

	/** A finished pre-6c amount (passes {@code finalDamage} unchanged). */
	public static DamageContext legacy(LivingEntity attacker, LivingEntity target, double amount) {
		return new DamageContext(Kind.LEGACY, attacker, target, null, 0.0D, 0.0D, amount, 0.0D, 0, 1.0D, true, false, null);
	}

	/** {@code DMG coef} × P of a spec. */
	public static DamageContext hit(LivingEntity attacker, LivingEntity target, TechniqueSpec spec, double coef, double power,
			int mastery, double f, @Nullable CastLedger ledger) {
		return new DamageContext(Kind.HIT, attacker, target, spec, coef, coef, 0.0D, power, mastery, f, false, false, ledger);
	}

	/** {@code DMG% share (boss share)}. */
	public static DamageContext percent(LivingEntity attacker, LivingEntity target, TechniqueSpec spec, double share,
			double bossShare, @Nullable CastLedger ledger) {
		return new DamageContext(Kind.PERCENT, attacker, target, spec, share, bossShare, 0.0D, 0.0D, 0, 1.0D, false, false, ledger);
	}

	/** {@code EXEC}: lethal damage (the runner checked the threshold). */
	public static DamageContext exec(LivingEntity attacker, LivingEntity target, TechniqueSpec spec, @Nullable CastLedger ledger) {
		return new DamageContext(Kind.EXEC, attacker, target, spec, 0.0D, 0.0D, 0.0D, 0.0D, 0, 1.0D, true, false, ledger);
	}

	public DamageContext kind(Kind value) {
		return new DamageContext(value, attacker, target, spec, coef, bossCoef, raw, power, mastery, f, precise, pierce, ledger);
	}

	public DamageContext coef(double value) {
		return new DamageContext(kind, attacker, target, spec, value, value, raw, power, mastery, f, precise, pierce, ledger);
	}

	public DamageContext power(double value) {
		return new DamageContext(kind, attacker, target, spec, coef, bossCoef, raw, value, mastery, f, precise, pierce, ledger);
	}

	public DamageContext f(double value) {
		return new DamageContext(kind, attacker, target, spec, coef, bossCoef, raw, power, mastery, value, precise, pierce, ledger);
	}

	public DamageContext precise(boolean value) {
		return new DamageContext(kind, attacker, target, spec, coef, bossCoef, raw, power, mastery, f, value, pierce, ledger);
	}

	public DamageContext pierce(boolean value) {
		return new DamageContext(kind, attacker, target, spec, coef, bossCoef, raw, power, mastery, f, precise, value, ledger);
	}

	public DamageContext target(LivingEntity value) {
		return new DamageContext(kind, attacker, value, spec, coef, bossCoef, raw, power, mastery, f, precise, pierce, ledger);
	}

	public DamageContext ledger(@Nullable CastLedger value) {
		return new DamageContext(kind, attacker, target, spec, coef, bossCoef, raw, power, mastery, f, precise, pierce, value);
	}
}
