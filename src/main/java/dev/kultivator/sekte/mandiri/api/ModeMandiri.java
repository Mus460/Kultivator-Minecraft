package dev.kultivator.sekte.mandiri.api;

import org.jetbrains.annotations.Nullable;

/**
 * The hall policy mode (DESIGN 39.4.5, 39.7.1). {@link #PATUH} switches the planner off entirely: behaviour exactly as
 * before DESIGN 39. Pure data, safe on both sides; never reorder.
 */
public enum ModeMandiri {
	/** Mandiri Penuh: journeys up to 5 days, 40 % away at once, big decisions without waiting for the master. */
	PENUH("penuh", 5, 40),
	/** Seimbang (new-world default): journeys up to 2 days, 25 % away, sect duties weighted x1.2. */
	SEIMBANG("seimbang", 2, 25),
	/** Patuh: no planner; disciples only obey. */
	PATUH("patuh", 0, 0);

	private final String id;
	private final int perjalananMaksHari;
	private final int pergiMaksPersenBawaan;

	ModeMandiri(String id, int perjalananMaksHari, int pergiMaksPersenBawaan) {
		this.id = id;
		this.perjalananMaksHari = perjalananMaksHari;
		this.pergiMaksPersenBawaan = pergiMaksPersenBawaan;
	}

	public String id() {
		return id;
	}

	/** Longest journey a disciple plans under this mode (world days). */
	public int perjalananMaksHari() {
		return perjalananMaksHari;
	}

	/** Default share of the hall away at once (config {@code pergiMaksPersen} overrides it). */
	public int pergiMaksPersenBawaan() {
		return pergiMaksPersenBawaan;
	}

	/** True when the planner runs at all. */
	public boolean hidup() {
		return this != PATUH;
	}

	/** {@code kultivator.sekte.mandiri.inti.mode.<id>}. */
	public String kunciNama() {
		return "kultivator.sekte.mandiri.inti.mode." + id;
	}

	public static ModeMandiri byId(@Nullable String id, ModeMandiri fallback) {
		if (id != null) {
			for (ModeMandiri mode : values()) {
				if (mode.id.equals(id) || mode.name().equalsIgnoreCase(id)) {
					return mode;
				}
			}
		}
		return fallback;
	}
}
