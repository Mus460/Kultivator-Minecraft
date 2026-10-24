package dev.kultivator.sekte.mandiri.api;

import org.jetbrains.annotations.Nullable;

/**
 * The sixteen permission groups of the master's policy (DESIGN 39.4.5, 39.7.1): a bitmask {@code 1 << ordinal()} in
 * {@link Kebijakan#izin()}. A cleared bit gives every activity and journey of that group the score factor 0. Pure data,
 * safe on both sides; never reorder (the mask is saved).
 */
public enum IzinKegiatan {
	KULTIVASI("kultivasi"),
	LATIHAN("latihan"),
	TANDING("tanding"),
	BELAJAR("belajar"),
	KERAJINAN("kerajinan"),
	KERJA("kerja"),
	SOSIAL("sosial"),
	JAGA("jaga"),
	BELANJA("belanja"),
	MISI_GILDA("misi_gilda"),
	BERBURU("berburu"),
	RERUNTUHAN("reruntuhan"),
	GUA("gua"),
	/** Off in the default policy; it switches itself on from Nascent Soul (39.7.1). */
	ALAM_RAHASIA("alam_rahasia"),
	BERKELANA("berkelana"),
	DESA_SEKTE("desa_sekte");

	/** Every group allowed. */
	public static final int SEMUA = (1 << 16) - 1;
	/** The new-world default (39.7.1): everything except secret realms. */
	public static final int BAWAAN = SEMUA & ~(1 << 13);

	private final String id;

	IzinKegiatan(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public int bit() {
		return 1 << ordinal();
	}

	/** True when {@code mask} allows this group. */
	public boolean boleh(int mask) {
		return (mask & bit()) != 0;
	}

	/** {@code kultivator.sekte.mandiri.inti.izin.<id>}. */
	public String kunciNama() {
		return "kultivator.sekte.mandiri.inti.izin." + id;
	}

	public static @Nullable IzinKegiatan byId(@Nullable String id) {
		if (id == null) {
			return null;
		}
		for (IzinKegiatan izin : values()) {
			if (izin.id.equals(id)) {
				return izin;
			}
		}
		return null;
	}
}
