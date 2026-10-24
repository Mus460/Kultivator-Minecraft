package dev.kultivator.sekte.mandiri.api;

import org.jetbrains.annotations.Nullable;

/**
 * The ten life paths of Murid Mandiri (DESIGN 39.3.1). Every owned disciple has exactly one (saved as {@link #id()} in
 * {@code extra.mandiri.jalan}); the path weights activities, goals, the shopping list, lines and poses, but locks
 * nothing. {@link #IBLIS} is never chosen at recruitment: it overlays another path at temptation 85 (39.3.3) and the
 * original is kept in {@code jalanAsal}. Pure data, safe on both sides; never reorder (the ordinal is synced in info).
 */
public enum JalanHidup {
	/** Pendekar Pedang: training forms, sparring, hunting, the tournament. */
	PEDANG("pedang", 0xFFB8C7D9),
	/** Alkemis: the herb garden, brewing pills, buying herbs. */
	ALKEMIS("alkemis", 0xFFE0893A),
	/** Ahli Formasi &amp; Jimat: formation keeping, writing talismans, study. */
	FORMASI("formasi", 0xFF7FB8E6),
	/** Pertapa: meditation, seclusion, contemplation, hermit caves. */
	PERTAPA("pertapa", 0xFF9FD7A8),
	/** Petualang: journeys, ruins, secret realms. */
	PETUALANG("petualang", 0xFFE8C65A),
	/** Pemburu Binatang: hunting spirit beasts, selling cores. */
	PEMBURU("pemburu", 0xFFB5764A),
	/** Pedagang: buying and selling, the sect stall, caravans. */
	PEDAGANG("pedagang", 0xFFD9B44A),
	/** Cendekiawan Kitab: study, teaching juniors, pilgrimage. */
	CENDEKIA("cendekia", 0xFFC9B6E8),
	/** Penjaga Sekte: guarding the gate, patrols, teaching. */
	PENJAGA("penjaga", 0xFF8C9BAD),
	/** Jalan Iblis: an overlay at temptation 85, redeemable (39.3.3). */
	IBLIS("iblis", 0xFF9B4FD1);

	private final String id;
	private final int warna;

	JalanHidup(String id, int warna) {
		this.id = id;
		this.warna = warna;
	}

	/** The saved id ({@code extra.mandiri.jalan}). */
	public String id() {
		return id;
	}

	/** The path's colour on the Jalan Hidup tab and the board (ARGB). */
	public int warna() {
		return warna;
	}

	/** The name key {@code kultivator.sekte.mandiri.inti.jalan_hidup.<id>} (lang fragment {@code sekte_mandiri_inti}). */
	public String kunciNama() {
		return "kultivator.sekte.mandiri.inti.jalan_hidup." + id;
	}

	/** The one-line description key {@code kultivator.sekte.mandiri.inti.jalan_hidup.<id>.desc}. */
	public String kunciDesc() {
		return kunciNama() + ".desc";
	}

	/** False only for {@link #IBLIS}: the paths a fresh recruit can draw (39.3.2). */
	public boolean pilihanAwal() {
		return this != IBLIS;
	}

	/** Null when unknown or empty. */
	public static @Nullable JalanHidup byId(@Nullable String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		for (JalanHidup jalan : values()) {
			if (jalan.id.equals(id)) {
				return jalan;
			}
		}
		return null;
	}
}
