package dev.kultivator.sekte.mandiri.api;

import org.jetbrains.annotations.Nullable;

/**
 * How a risk ends for a disciple (DESIGN 39.5.9): by default every failure ends wounded, badly wounded or captured, never
 * dead ({@code mandiri.bolehGugur} false). Saved in {@code extra.mandiri.luka.jenis}. Pure data, safe on both sides.
 */
public enum JenisLuka {
	/** Unhurt. */
	TIDAK("tidak"),
	/** Home at 30 % health with Qi Deviation 3600 ticks (the X numbers). */
	TERLUKA("terluka"),
	/** 2-4 days without sparring or journeys, max health -30 % meanwhile; the healer, three healing pills or the master. */
	BERAT("berat"),
	/** Held at a real site for 3 world days: a rescue for the master, or it escapes badly wounded. */
	TERTAWAN("tertawan");

	private final String id;

	JenisLuka(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	/** {@code kultivator.sekte.mandiri.inti.luka.<id>}. */
	public String kunciNama() {
		return "kultivator.sekte.mandiri.inti.luka." + id;
	}

	/** {@link #TIDAK} when unknown. */
	public static JenisLuka byId(@Nullable String id) {
		if (id != null) {
			for (JenisLuka luka : values()) {
				if (luka.id.equals(id) || luka.name().equalsIgnoreCase(id)) {
					return luka;
				}
			}
		}
		return TIDAK;
	}
}
