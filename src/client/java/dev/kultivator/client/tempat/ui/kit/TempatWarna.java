package dev.kultivator.client.tempat.ui.kit;

import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.dunia.tempat.ui.LembarKunci;

/**
 * Colour tokens of the "Pasar Giok" kit (DESIGN 38.U.2), derived from {@link Palette}: district tokens, the three paths
 * (aliran), status tokens and one {@link Gaya} per panel style with its ink, card and accent colours. Everything a 38
 * screen colours comes from here, so the five styles stay legible (ink on light panels, cream on dark ones).
 */
public final class TempatWarna {

	// District tokens (main, accent) in LembarKunci.SEMUA_DISTRIK order
	public static final int ISTANA = 0xFFC9A24A;
	public static final int ISTANA_MERAH = 0xFF8E1F1F;
	public static final int CENDEKIA = 0xFF2A3A5C;
	public static final int AWAN = 0xFF6FA8C8;
	public static final int GILDA = 0xFF8C6A3A;
	public static final int PUSAT = 0xFF5FA88C;
	public static final int PASAR = 0xFFD08A2A;
	public static final int HUNIAN = 0xFF7A5A3A;
	public static final int ARENA = 0xFF7A1A1A;
	public static final int KUMUH = 0xFF6A6A60;
	public static final int GERBANG = 0xFF8E1F1F;
	public static final int LUAR = 0xFF4F8A3A;

	// Paths
	public static final int LURUS = 0xFFD8E4F0;
	public static final int NETRAL = 0xFFC9A24A;
	public static final int IBLIS = 0xFF6A1420;

	// Status
	public static final int OK = 0xFF5FA88C;
	public static final int PERINGATAN = 0xFFD0A040;
	public static final int BAHAYA = 0xFFB03030;
	/** "Locked" = 45 % ink. */
	public static final float TERKUNCI_ALFA = 0.45F;

	// Gold accents shared by every style
	public static final int EMAS = Palette.GOLD;
	public static final int EMAS_TERANG = Palette.GOLD_LIGHT;
	public static final int EMAS_GELAP = Palette.GOLD_DARK;
	public static final int SEGEL = Palette.SEAL;
	/** The Ghost Market green (DESIGN 42.11): accents of the 42 night market and the shadow world. */
	public static final int HIJAU_HANTU = 0xFF3FA86A;

	/** Quality colours by grade 0-4 (mutu) used for item names. */
	private static final int[] MUTU = {0xFF8A8F8C, 0xFF4F9A4A, 0xFF3E78C0, 0xFF8B4FC8, 0xFFD08A2A};
	private static final int[] MUTU_TERANG = {0xFFD8DCD8, 0xFFA6E08A, 0xFFA8D2FF, 0xFFD7B4FF, 0xFFFFD27A};

	/** One panel style: texture name plus the colours drawn on it. */
	public enum Gaya {
		KERTAS(LembarKunci.GAYA_KERTAS, Palette.INK, Palette.INK_SOFT, Palette.INK_FAINT, 0x30A08850, 0x60C9A24A, 0xFFF4ECD4,
				Palette.JADE_DARK, 0xFFEFE4C6),
		KAYU(LembarKunci.GAYA_KAYU, 0xFFF6E8C8, 0xFFE0C89A, 0xFFB89670, 0x40000000, 0x70F7DC7E, 0xFF4A140C, Palette.GOLD_LIGHT,
				0xFF5E1A10),
		BATU(LembarKunci.GAYA_BATU, Palette.INK, 0xFF39403C, 0xFF5E6662, 0x28FFFFFF, 0x60E8E8DE, 0xFFD4D4CA, 0xFF7A1A1A, 0xFFC0C0B6),
		KAIN(LembarKunci.GAYA_KAIN, 0xFFF4E6D0, 0xFFD8C0A8, 0xFFA88AA0, 0x40000000, 0x70D4A637, 0xFF2E0E22, Palette.GOLD_LIGHT,
				0xFF3C1430),
		GIOK(LembarKunci.GAYA_GIOK, 0xFF10261C, 0xFF2A4A3C, 0xFF5E7E70, 0x30184B36, 0x7067C99B, 0xFFE4F2EA, 0xFF1E6A4A, 0xFFD2E8DC);

		public final String id;
		/** Body text. */
		public final int teks;
		/** Secondary text. */
		public final int teksLemah;
		/** Captions, hints. */
		public final int teksSamar;
		/** Card fill (translucent) and its hover tint. */
		public final int kartu;
		public final int kartuSorot;
		/** Inset well (detail pane, input). */
		public final int sumur;
		/** Accent for titles and selected rows. */
		public final int aksen;
		/** Solid back of the content area behind translucent cards. */
		public final int dasar;

		Gaya(String id, int teks, int teksLemah, int teksSamar, int kartu, int kartuSorot, int sumur, int aksen, int dasar) {
			this.id = id;
			this.teks = teks;
			this.teksLemah = teksLemah;
			this.teksSamar = teksSamar;
			this.kartu = kartu;
			this.kartuSorot = kartuSorot;
			this.sumur = sumur;
			this.aksen = aksen;
			this.dasar = dasar;
		}

		/** True for the dark styles (cream text). */
		public boolean gelap() {
			return this == KAYU || this == KAIN;
		}

		/** Text colour inside a {@link #sumur} well (wells are always light). */
		public int teksSumur() {
			return this == KAYU || this == KAIN ? 0xFF2A1A12 : teks;
		}

		public static Gaya dari(String id, Gaya fallback) {
			for (Gaya gaya : values()) {
				if (gaya.id.equals(id)) {
					return gaya;
				}
			}
			return fallback;
		}
	}

	/** District colour of a {@code DISTRIK_*} id ({@link #PUSAT} when unknown). */
	public static int distrik(String id) {
		return switch (id) {
			case LembarKunci.DISTRIK_ISTANA -> ISTANA;
			case LembarKunci.DISTRIK_CENDEKIA -> CENDEKIA;
			case LembarKunci.DISTRIK_AWAN -> AWAN;
			case LembarKunci.DISTRIK_GILDA -> GILDA;
			case LembarKunci.DISTRIK_PASAR -> PASAR;
			case LembarKunci.DISTRIK_HUNIAN -> HUNIAN;
			case LembarKunci.DISTRIK_ARENA -> ARENA;
			case LembarKunci.DISTRIK_KUMUH -> KUMUH;
			case LembarKunci.DISTRIK_GERBANG -> GERBANG;
			case LembarKunci.DISTRIK_LUAR -> LUAR;
			default -> PUSAT;
		};
	}

	/** Status colour of a {@code PESAN_*} value. */
	public static int status(String jenis) {
		return switch (jenis) {
			case LembarKunci.PESAN_BAHAYA -> BAHAYA;
			case LembarKunci.PESAN_PERINGATAN -> PERINGATAN;
			default -> OK;
		};
	}

	/** Name colour of an item grade 0-4 on a light ({@code gelap} false) or dark panel; -1 = plain text colour. */
	public static int mutu(int mutu, boolean gelap, int polos) {
		if (mutu < 0) {
			return polos;
		}
		int k = Math.min(MUTU.length - 1, mutu);
		return gelap ? MUTU_TERANG[k] : Palette.darken(MUTU[k], 0.1F);
	}

	/** Colour at "locked" strength (45 % of the ink over the panel). */
	public static int terkunci(int warna) {
		return Palette.argb(Math.round(255 * TERKUNCI_ALFA), warna & 0xFFFFFF);
	}

	private TempatWarna() {
	}
}
