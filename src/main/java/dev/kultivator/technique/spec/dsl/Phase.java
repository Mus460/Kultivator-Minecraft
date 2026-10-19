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
	EXTRA7,
	/**
	 * The cast stopped early (cancelled, replaced, interrupted, the caster died or a toggle was cast off): every client
	 * ends this caster's runs of the spec, its pose and its cinematic. Position = caster, duration 0. Never has layers.
	 */
	ABORT,
	/**
	 * A summoned phantom attacks: pos = the phantom, to = the victim, {@code target} = the victim's id, {@code aux} = the
	 * {@code PhantomKind} ordinal.
	 */
	PHANTOM,
	/** A simulated projectile hit the ground without a target (pos = the landing point, to = where it came from). */
	LAND;

	private static final Phase[] VALUES = values();

	/** Spec-specific moment {@code index} (0-7). */
	public static Phase extra(int index) {
		return VALUES[EXTRA0.ordinal() + Math.max(0, Math.min(7, index))];
	}

	/** The index 0-7 of an EXTRA phase, or −1. */
	public int extraIndex() {
		int i = ordinal() - EXTRA0.ordinal();
		return i >= 0 && i <= 7 ? i : -1;
	}

	public static Phase byOrdinal(int ordinal) {
		return ordinal < 0 || ordinal >= VALUES.length ? CAST : VALUES[ordinal];
	}
}
