package dev.kultivator.sekte.gabung.api;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Where a player stands with the NPC sects (DESIGN 41.12.1 {@code Keanggotaan.status}). One sect at a time (rule R1);
 * {@link #MENYAMAR} is the temporary second, false membership of the "Dua Topeng" chain. Saved by id.
 */
public enum StatusAnggota {

	/** No sect (never joined, or left). */
	BEBAS("bebas"),
	/** An application is open: sect, door, next Test Day, {@code calonSampai} (3 world days). */
	CALON("calon"),
	/** A sworn member with a rank. */
	ANGGOTA("anggota"),
	/** A member infiltrating a second (demonic) sect under a false name for 10 days. */
	MENYAMAR("menyamar");

	private final String id;

	StatusAnggota(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	/** Belongs to a sect right now (Anggota or Menyamar, whose real sect is still {@code sekte}). */
	public boolean anggota() {
		return this == ANGGOTA || this == MENYAMAR;
	}

	public MutableComponent nama() {
		return Component.translatable("kultivator.gabung.status." + id);
	}

	public static StatusAnggota byId(String id) {
		for (StatusAnggota s : values()) {
			if (s.id.equals(id)) {
				return s;
			}
		}
		return BEBAS;
	}
}
