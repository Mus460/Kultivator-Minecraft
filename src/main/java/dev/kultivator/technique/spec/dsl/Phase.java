package dev.kultivator.technique.spec.dsl;

/**
 * FX and sound phases of a cast (DESIGN 22.4.1). The ordinal is the {@code phase} byte of {@code Payloads.TechFx}, so
 * never reorder; new phases go at the end.
 */
public enum Phase {
	/** Step 0. */
	CAST,
	/** A charge starts. */
	CHARGE,
	/** A beam, front, projectile, wave or dash starts. */
	TRAVEL,
	/** A delayed area lands. */
	IMPACT,
	/** One target was hit. */
	HIT,
	/** A domain, field, aura, rain or channel tick. */
	PULSE,
	/** The timeline ended. */
	END,
	/** More than 8 hits in one evaluation, sent as one event. */
	MASS_HIT,
	EXTRA0,
	EXTRA1,
	EXTRA2,
	EXTRA3,
	EXTRA4,
	EXTRA5,
	EXTRA6,
	EXTRA7;

	private static final Phase[] VALUES = values();

	/** Spec-specific moment {@code index} (0-7). */
	public static Phase extra(int index) {
		return VALUES[EXTRA0.ordinal() + Math.max(0, Math.min(7, index))];
	}

	public static Phase byOrdinal(int ordinal) {
		return ordinal < 0 || ordinal >= VALUES.length ? CAST : VALUES[ordinal];
	}
}
