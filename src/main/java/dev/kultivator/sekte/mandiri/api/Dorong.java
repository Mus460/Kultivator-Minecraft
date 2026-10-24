package dev.kultivator.sekte.mandiri.api;

import org.jetbrains.annotations.Nullable;

/**
 * The seven drives (Dorongan, DESIGN 39.4.1), each 0-100 in {@code extra.mandiri.dorong.<id>}: updated once per world
 * hour while the body is loaded and set to the path's equilibrium on catch-up. Loyalty and contentment are never copied
 * here (read live from {@code SekteApi.hati()}). Pure data, safe on both sides; never reorder (the tab shows bars in this
 * order).
 */
public enum Dorong {
	/** Hunger for cultivation: rises while the bar is not full. */
	KULTIVASI("kultivasi", 0xFF6FD3FF),
	/** Boredom: rises doing the same thing in the same place. */
	BOSAN("bosan", 0xFFB0A27A),
	/** Fatigue: training, work, walking. */
	LELAH("lelah", 0xFFE08A5A),
	/** Need for company: rises alone. */
	SOSIAL("sosial", 0xFFF29BC1),
	/** Ambition: from traits and unfinished priority-1 goals. */
	AMBISI("ambisi", 0xFFE8C65A),
	/** Need for supplies: open shopping-list priorities. */
	BARANG("barang", 0xFF9FD7A8),
	/** Need to heal: follows missing health; severe wounds pin it at 100. */
	LUKA("luka", 0xFFE05555);

	/** Number of drives (the fixed score arrays of the planner are this long). */
	public static final int JUMLAH = 7;

	private final String id;
	private final int warna;

	Dorong(String id, int warna) {
		this.id = id;
		this.warna = warna;
	}

	/** The key inside {@code extra.mandiri.dorong}. */
	public String id() {
		return id;
	}

	/** Bar colour on the Jalan Hidup tab (ARGB). */
	public int warna() {
		return warna;
	}

	/** {@code kultivator.sekte.mandiri.inti.dorong.<id>}. */
	public String kunciNama() {
		return "kultivator.sekte.mandiri.inti.dorong." + id;
	}

	public static @Nullable Dorong byId(@Nullable String id) {
		if (id == null) {
			return null;
		}
		for (Dorong dorong : values()) {
			if (dorong.id.equals(id)) {
				return dorong;
			}
		}
		return null;
	}
}
