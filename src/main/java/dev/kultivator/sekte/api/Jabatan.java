package dev.kultivator.sekte.api;

import org.jetbrains.annotations.Nullable;

/**
 * The offices of the sect elders (DESIGN 36.2.6, module E). An office is held by a disciple of the existing
 * {@code SectRanks.TETUA} or {@code TETUA_AGUNG} rank; E keeps it in {@code extra.jabatan}. {@link #domain()} names the
 * module whose daily affairs the office runs, so that module may ask {@code SekteApi.tetua().holder(owner, office)} and
 * apply E's published bonus ({@code TetuaService.officeFactor}).
 * <p>
 * Sumber: the Hall of Discipline, Scripture Pavilion, Treasury and Outer Affairs elders of the sect novels (<i>A Will
 * Eternal</i>, <i>I Shall Seal the Heavens</i>, <i>Martial Peak</i>), the elders of the Seven Mysteries Sect (<i>A Record
 * of a Mortal's Journey</i>).
 */
public enum Jabatan {
	TETUA_AGUNG("agung", ""),
	TETUA_HUKUM("hukum", "kepribadian"),
	TETUA_AJAR("ajar", "paviliun"),
	TETUA_PUSAKA("pusaka", "kebun"),
	TETUA_LUAR("luar", "misi"),
	TETUA_OBAT("obat", "kebun");

	private final String id;
	private final String domain;

	Jabatan(String id, String domain) {
		this.id = id;
		this.domain = domain;
	}

	public String id() {
		return id;
	}

	/** The module id ({@code SekteIds}) whose affairs this office runs; empty for the Grand Elder (all of them). */
	public String domain() {
		return domain;
	}

	public String key() {
		return "kultivator.sekte.jabatan." + id;
	}

	public static @Nullable Jabatan byId(@Nullable String id) {
		if (id != null) {
			for (Jabatan jabatan : values()) {
				if (jabatan.id.equals(id)) {
					return jabatan;
				}
			}
		}
		return null;
	}
}
