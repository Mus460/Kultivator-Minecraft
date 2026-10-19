package dev.kultivator.technique.spec.run;

/** Why a cast ended (DESIGN 22.2.6, 22.18.3). */
public enum EndReason {
	/** The timeline finished. */
	DONE,
	/** The caster died. */
	DEATH,
	/** The caster logged out (or the disciple unloaded). */
	LOGOUT,
	/** The caster changed dimension. */
	DIMENSION,
	/** Interrupted or cancelled (cast again, moved during a channel, a hit broke a charge). */
	CANCELLED,
	/** A newer cast took its one-of slot (domain, form, aura, toggle, the oldest field). */
	REPLACED,
	/** Ended early by the server's active-cast budget. */
	OVERLOAD
}
