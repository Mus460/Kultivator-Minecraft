package dev.kultivator.client.sosok.gerak;

import dev.kultivator.data.Dao;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sosok.Gaya;
import net.minecraft.util.Mth;

/**
 * The face of a Sosok body each frame (DESIGN 40.5.9): which of the 16 eye strips (atlas B1) and 16 mouth strips (B2,
 * DESIGN 40.3.1) {@code WajahModel} (40K) shows. Priority: the clip's face events and face, closed eyes of
 * {@code MATA_TUTUP} clips, fighting (angry, the {@code kilat} iris from Nascent Soul), wounds, the mood and the traits;
 * on top run the blink (60-140 tick pauses seeded by the entity, half-shut 1 tick, shut 2, half 1, 15 % double blinks),
 * glances toward the look target and the talking mouth built from the spoken line (vowels a/i/u/e/o to A/E/O shapes,
 * consonants half-closed, spaces and punctuation as pauses, 2 ticks a letter; without text a seeded A/E/O pattern).
 */
public final class Wajah {

	// eye strips (B1 index)
	public static final int MATA_BUKA = 0, MATA_SETENGAH = 1, MATA_TUTUP = 2, MATA_LIRIK_KIRI = 3, MATA_LIRIK_KANAN = 4,
			MATA_LIRIK_ATAS = 5, MATA_LIRIK_BAWAH = 6, MATA_MARAH = 7, MATA_SEDIH = 8, MATA_KAGET = 9, MATA_SENANG = 10,
			MATA_MENGANTUK = 11, MATA_TUTUP_TENANG = 12, MATA_KILAT = 13, MATA_SAKIT = 14, MATA_MALU = 15;
	// mouth strips (B2 index)
	public static final int MULUT_DIAM = 0, MULUT_A = 1, MULUT_O = 2, MULUT_E = 3, MULUT_SENYUM = 4, MULUT_TERTAWA = 5,
			MULUT_CEMBERUT = 6, MULUT_MARAH = 7, MULUT_KAGET = 8, MULUT_SEDIH = 9, MULUT_TIDUR = 10, MULUT_MENIUP = 11,
			MULUT_MAKAN = 12, MULUT_BATUK_DARAH = 13, MULUT_MENYERINGAI = 14, MULUT_MENGGIGIT_BIBIR = 15;

	/** Names of the strips (test command {@code wajah}, {@code /sosok}), index = strip. */
	public static final String[] NAMA_MATA = {"buka", "setengah", "tutup", "lirik_kiri", "lirik_kanan", "lirik_atas", "lirik_bawah",
			"marah", "sedih", "kaget", "senang", "mengantuk", "tutup_tenang", "kilat", "sakit", "malu"};
	public static final String[] NAMA_MULUT = {"diam", "bicara_A", "bicara_O", "bicara_E", "senyum", "tertawa", "cemberut", "marah",
			"kaget", "sedih", "tidur", "meniup", "makan", "batuk_darah", "menyeringai", "menggigit_bibir"};

	/**
	 * Fills {@code d.mata}/{@code d.mulut}. {@code klipMata}/{@code klipMulut} come from the playing clip (and its face
	 * events, -1 = none); {@code bicara} is true while the body talks; {@code garis} is the spoken line (may be empty).
	 */
	static void hitung(GerakKeadaan k, GerakData d, float age, int klipMata, int klipMulut, boolean mataTutup, boolean bicara,
			String garis, boolean bertarung, int realm, boolean terluka, boolean wajahAktif) {
		if (!wajahAktif) {
			d.mata = MATA_BUKA;
			d.mulut = MULUT_DIAM;
			return;
		}
		Gaya gaya = k.gaya;
		int mata = MATA_BUKA;
		int mulut = MULUT_DIAM;
		// mood and traits (the base expression)
		switch (gaya.suasana()) {
			case Gaya.SUASANA_SENANG -> {
				mata = MATA_SENANG;
				mulut = MULUT_SENYUM;
			}
			case Gaya.SUASANA_SEDIH -> {
				mata = MATA_SEDIH;
				mulut = MULUT_SEDIH;
			}
			case Gaya.SUASANA_MARAH -> {
				mata = MATA_MARAH;
				mulut = MULUT_MARAH;
			}
			case Gaya.SUASANA_TAKUT -> {
				mata = MATA_KAGET;
				mulut = MULUT_MENGGIGIT_BIBIR;
			}
			case Gaya.SUASANA_MENGANTUK -> mata = MATA_MENGANTUK;
			case Gaya.SUASANA_MALU -> {
				mata = MATA_MALU;
				mulut = MULUT_MENGGIGIT_BIBIR;
			}
			case Gaya.SUASANA_BANGGA -> mulut = MULUT_MENYERINGAI;
			default -> {
				int slot = (int) (age / 200.0F) + k.benih;
				if (gaya.punya(Sifat.SOMBONG) && pilih(slot, 30)) {
					mulut = MULUT_MENYERINGAI;
				} else if (gaya.punya(Sifat.PENYAYANG)) {
					mulut = MULUT_SENYUM;
				} else if (gaya.punya(Sifat.PENDENDAM)) {
					mulut = MULUT_CEMBERUT;
				}
				if (gaya.punya(Sifat.MALAS) && pilih(slot + 7, 40)) {
					mata = MATA_MENGANTUK;
				} else if (gaya.punya(Sifat.TENANG) && k.diamSejak > 0.0F && age - k.diamSejak > 400.0F && pilih(slot + 3, 50)) {
					mata = MATA_TUTUP_TENANG;
				}
			}
		}
		if (gaya.luka() == Gaya.LUKA_HABIS_QI || gaya.luka() == Gaya.LUKA_PARAH || terluka) {
			mata = MATA_SAKIT;
			mulut = gaya.luka() == Gaya.LUKA_PARAH ? MULUT_MENGGIGIT_BIBIR : mulut;
		}
		if (bertarung) {
			mata = realm >= 4 ? MATA_KILAT : MATA_MARAH;
			mulut = MULUT_MARAH;
		}
		if (klipMata >= 0) {
			mata = klipMata;
		}
		if (klipMulut >= 0) {
			mulut = klipMulut;
		}
		if (mataTutup) {
			mata = mata == MATA_TUTUP ? MATA_TUTUP : MATA_TUTUP_TENANG;
		}
		// glances toward the look target (the head stops at its limit; the eyes go on)
		boolean bisaLirik = mata == MATA_BUKA || mata == MATA_SENANG;
		if (bisaLirik && d.lirikX > 0.55F) {
			mata = MATA_LIRIK_KIRI;
		} else if (bisaLirik && d.lirikX < -0.55F) {
			mata = MATA_LIRIK_KANAN;
		} else if (bisaLirik && d.lirikY > 0.6F) {
			mata = MATA_LIRIK_ATAS;
		} else if (bisaLirik && d.lirikY < -0.6F) {
			mata = MATA_LIRIK_BAWAH;
		}
		// the blink (not over closed or sleeping eyes)
		if (mata != MATA_TUTUP && mata != MATA_TUTUP_TENANG) {
			int kedip = kedip(k, age);
			if (kedip == 2) {
				mata = MATA_TUTUP;
			} else if (kedip == 1) {
				mata = MATA_SETENGAH;
			}
		}
		if (bicara && (klipMulut < 0 || klipMulut == MULUT_A)) {
			mulut = mulutBicara(garis, age, k.benih);
		}
		d.mata = mata;
		d.mulut = mulut;
	}

	private static boolean pilih(int slot, int persen) {
		return Math.floorMod(slot * 2654435761L >>> 7, 100) < persen;
	}

	/** 0 open, 1 half, 2 shut, from a per-entity blink schedule. */
	static int kedip(GerakKeadaan k, float age) {
		if (age >= k.kedipBerikut || age + 400.0F < k.kedipMulai) {
			k.kedipMulai = Math.max(age, k.kedipBerikut);
			long h = (long) (k.benih * 0x9E3779B9L) ^ (long) (age * 31.0F);
			h = h * 0xBF58476D1CE4E5B9L;
			int jeda = 60 + (int) Math.floorMod(h >>> 20, 81L);
			k.kedipGanda = Math.floorMod(h >>> 40, 100L) < 15;
			k.kedipBerikut = k.kedipMulai + jeda;
		}
		float dalam = age - (k.kedipBerikut - 4.0F);
		if (dalam >= 0.0F && dalam < 4.0F) {
			return dalam < 1.0F || dalam >= 3.0F ? 1 : 2;
		}
		if (k.kedipGanda) {
			float kedua = age - (k.kedipBerikut - 10.0F);
			if (kedua >= 0.0F && kedua < 4.0F) {
				return kedua < 1.0F || kedua >= 3.0F ? 1 : 2;
			}
		}
		return 0;
	}

	/**
	 * The talking mouth (40.5.9): the line is walked letter by letter, at most 60 letters, then repeated while it shows.
	 * Vowels hold their shape 2 ticks (a -> A, i/e -> E, o/u -> O), a consonant is a half-open E for 1 tick then closed
	 * for 1, a space closes the mouth 2 ticks, punctuation pauses 6. Walked fresh each frame (60 letters at most, no
	 * allocation). Without a line: a seeded A/E/O babble.
	 */
	static int mulutBicara(String garis, float age, int benih) {
		int t = (int) age;
		if (garis == null || garis.isEmpty()) {
			int slot = (t / 3) + benih;
			return switch (Math.floorMod(slot * 1103515245 + 12345 >>> 8, 5)) {
				case 0, 1 -> MULUT_A;
				case 2 -> MULUT_E;
				case 3 -> MULUT_O;
				default -> MULUT_DIAM;
			};
		}
		int len = Math.min(60, garis.length());
		int siklus = 6;
		for (int i = 0; i < len; i++) {
			siklus += lamaHuruf(garis.charAt(i));
		}
		int pos = Math.floorMod(t, siklus);
		for (int i = 0; i < len; i++) {
			char c = Character.toLowerCase(garis.charAt(i));
			int lama = lamaHuruf(c);
			if (pos < lama) {
				return switch (c) {
					case 'a' -> MULUT_A;
					case 'i', 'e' -> MULUT_E;
					case 'o', 'u' -> MULUT_O;
					case ' ', ',', '.', '!', '?', ';', ':', '-' -> MULUT_DIAM;
					default -> pos == 0 && Character.isLetter(c) ? MULUT_E : MULUT_DIAM;
				};
			}
			pos -= lama;
		}
		return MULUT_DIAM;
	}

	/** Ticks one character of a spoken line keeps the mouth (punctuation is a breath). */
	private static int lamaHuruf(char c) {
		return switch (c) {
			case ',', '.', '!', '?', ';', ':', '-' -> 6;
			default -> 2;
		};
	}

	/** The Dao colour for the {@code kilat} strip and particles (-1 = none). */
	public static int warnaDao(int dao) {
		Dao[] all = Dao.values();
		return dao >= 0 && dao < all.length ? all[dao].color() : 0x9FE8FF;
	}

	/** Clamp helper for glance amounts. */
	static float lirik(float selisihDerajat) {
		return Mth.clamp(selisihDerajat / 25.0F, -1.0F, 1.0F);
	}

	private Wajah() {
	}
}
