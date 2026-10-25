package dev.kultivator.sekte.gabung.api;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * How a player left a sect (DESIGN 41.7, {@code Keanggotaan.riwayatSekte.<s>}), with the wait before applying to the same
 * sect again (41.7.5). Saved by id.
 */
public enum StatusKeluar {

	/** Pamit: the honourable leave (with or without the master's blessing); back after 7 days, rank - 1, frozen PK returned. */
	PAMIT("pamit", 7),
	/** Pamit Tanpa Izin (or R3 "found without leave"): standing -30, rank and PK lost; back after 15 days, full test. */
	PAMIT_PAKSA("pamit_paksa", 15),
	/** Diusir: expelled; back after 30 days and the "Tebus Dosa" chain, as Tercatat. */
	DIUSIR("diusir", 30),
	/** Membelot: a deserter, hunted; never back while the same master rules (-1). */
	PEMBELOT("pembelot", -1);

	private final String id;
	private final int jedaHari;

	StatusKeluar(String id, int jedaHari) {
		this.id = id;
		this.jedaHari = jedaHari;
	}

	public String id() {
		return id;
	}

	/** World days before the same sect takes an application again; -1 = not while the master who ruled then rules. */
	public int jedaHari() {
		return jedaHari;
	}

	public MutableComponent nama() {
		return Component.translatable("kultivator.gabung.keluar." + id);
	}

	public static StatusKeluar byId(String id, StatusKeluar fallback) {
		for (StatusKeluar s : values()) {
			if (s.id.equals(id)) {
				return s;
			}
		}
		return fallback;
	}
}
