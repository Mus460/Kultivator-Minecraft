package dev.kultivator.sekte.gabung.api;

/**
 * The animation ids of Gabung Sekte (DESIGN 41.11.6, contract C41.V1, frozen): clips 300-319 (reserved up to 349) and
 * props 150-169, inside the ranges the shared Gerak system already accepts ({@code GerakKatalog.daftar} 200-399,
 * {@code PropKatalog.daftar} 100-199), so no boundary of {@code sosok/GerakId} or {@code sosok/PropId} changes. Pure
 * constants, safe on both sides: the server names a clip in {@code SosokApi.gerak} and in the {@code gabung_klip}
 * payload; the client registers the tables in {@code client/sekte/gabung/gerak/GabungPoses} (41V-G).
 * <p>
 * Every clip lasts at most 3 s per cycle with the 40 springs on; on Minimal effect quality the client holds a static
 * pose. Player clips (the oath ceremony, leaving, expulsion, reflection, succession) reach the player model through the
 * {@code gabung_klip} payload and the additive {@code PlayerModelMixin} hook of 41V-G.
 */
public final class GabungGerakId {

	// ================================================================== clips 300-319 (reserved to 349)

	/** Kneeling on one knee, hands clasped (player, NPC candidates). */
	public static final int SUMPAH_BERLUTUT = 300;
	/** Raising three incense sticks to the brow and planting them (player; prop {@link #PROP_DUPA_SUMPAH}). */
	public static final int DUPA_TIGA = 301;
	/** Three full prostrations (player, NPC disciples). */
	public static final int KOWTOW = 302;
	/** Receiving with both hands and bowing (player; prop {@link #PROP_LENCANA_ANGGOTA}). */
	public static final int TERIMA_LENCANA = 303;
	/** Fist-in-palm salute at chest height (every NPC member). */
	public static final int SALAM_SEKTE = 304;
	/** Sitting cross-legged, nodding along to a lecture (player, NPC disciples). */
	public static final int DENGAR_CERAMAH = 305;
	/** A carrying pole on the shoulder, swaying walk (player, Pelayan NPC; prop {@link #PROP_EMBER_PIKUL}). */
	public static final int PIKUL_AIR = 306;
	/** Squatting, fanning a furnace (player, NPC; prop {@link #PROP_KIPAS_BAMBU}). */
	public static final int KIPAS_TUNGKU = 307;
	/** A deep bow, then three steps backwards (player leaving with honour). */
	public static final int PAMIT_BUNGKUK = 308;
	/** Head hung, shoulders dropped, slow walk (player expelled). */
	public static final int DIUSIR_TUNDUK = 309;
	/** Stroking the beard, eyes narrowed (elder NPC weighing a candidate). */
	public static final int TETUA_MENILAI = 310;
	/** Unrolling a scroll and pointing at a line (Tetua Hukum; prop {@link #PROP_GULUNGAN_ATURAN}). */
	public static final int TETUA_MENUNJUK_ATURAN = 311;
	/** Hand at the mouth, calling out (gate guard on the Test Day). */
	public static final int PENJAGA_MEMANGGIL = 312;
	/** A quick two-way cut (NPC instructor; prop {@link #PROP_PEDANG_KAYU}). */
	public static final int TEBASAN_UJIAN = 313;
	/** The bronze monk's horse stance with a visible breath. */
	public static final int BIKSU_KUDA_KUDA = 314;
	/** Sitting facing the wall (player in the Gua Renungan). */
	public static final int RENUNGAN = 315;
	/** The old master handing over robe and bowl (Ketua NPC; prop {@link #PROP_MANGKUK_KETUA}). */
	public static final int SERAH_JUBAH_MANGKUK = 316;
	/** Receiving robe and bowl (player; prop {@link #PROP_MANGKUK_KETUA}). */
	public static final int TERIMA_JUBAH_MANGKUK = 317;
	/** Falling to the knees on the stair, panting (NPC candidates; player at Mountain Pressure 100). */
	public static final int LELAH_BERLUTUT = 318;
	/** A raised fist cheer (tournament spectators). */
	public static final int SORAK_MURID = 319;

	public static final int KLIP_FIRST = SUMPAH_BERLUTUT;
	/** Last clip in use. */
	public static final int KLIP_LAST = SORAK_MURID;
	/** Last id reserved for 41 (D-41V may add 320-349 without touching anyone else). */
	public static final int KLIP_CADANGAN_LAST = 349;

	/** Clip names of 300-319 in order ({@code GerakKatalog} name {@code "gabung_" + name}). */
	public static final String[] KLIP_NAMA = {"sumpah_berlutut", "dupa_tiga", "kowtow", "terima_lencana", "salam_sekte", "dengar_ceramah",
			"pikul_air", "kipas_tungku", "pamit_bungkuk", "diusir_tunduk", "tetua_menilai", "tetua_menunjuk_aturan", "penjaga_memanggil",
			"tebasan_ujian", "biksu_kuda_kuda", "renungan", "serah_jubah_mangkuk", "terima_jubah_mangkuk", "lelah_berlutut", "sorak_murid"};

	/** Clips a player may be posed with through {@code gabung_klip} (the server sends nothing else for players). */
	public static final int[] KLIP_PEMAIN = {SUMPAH_BERLUTUT, DUPA_TIGA, KOWTOW, TERIMA_LENCANA, DENGAR_CERAMAH, PIKUL_AIR, KIPAS_TUNGKU,
			PAMIT_BUNGKUK, DIUSIR_TUNDUK, RENUNGAN, TERIMA_JUBAH_MANGKUK, LELAH_BERLUTUT, SALAM_SEKTE};

	// ================================================================== props 150-169 (model kultivator:fx/sekte_gabung_<name>)

	/** Three incense sticks of the oath. */
	public static final int PROP_DUPA_SUMPAH = 150;
	/** The member token (jade disc on a red cord). */
	public static final int PROP_LENCANA_ANGGOTA = 151;
	/** Two buckets on a carrying pole. */
	public static final int PROP_EMBER_PIKUL = 152;
	/** The master's alms bowl of the succession. */
	public static final int PROP_MANGKUK_KETUA = 153;
	/** A bamboo fan for the furnace. */
	public static final int PROP_KIPAS_BAMBU = 154;
	/** The sect rules scroll of the Tetua Hukum. */
	public static final int PROP_GULUNGAN_ATURAN = 155;
	/** A wooden practice sword. */
	public static final int PROP_PEDANG_KAYU = 156;

	public static final int PROP_FIRST = PROP_DUPA_SUMPAH;
	public static final int PROP_LAST = PROP_PEDANG_KAYU;
	public static final int PROP_CADANGAN_LAST = 169;

	/** Model names of 150-156 in order ({@code kultivator:fx/sekte_gabung_<name>}). */
	public static final String[] PROP_NAMA = {"dupa_sumpah", "lencana_anggota", "ember_pikul", "mangkuk_ketua", "kipas_bambu",
			"gulungan_aturan", "pedang_kayu"};

	/** The prop model path of prop {@code id}, or null outside 150-156. */
	public static String propModel(int id) {
		int i = id - PROP_FIRST;
		return i >= 0 && i < PROP_NAMA.length ? "fx/sekte_gabung_" + PROP_NAMA[i] : null;
	}

	/** The clip name of clip {@code id}, or null outside 300-319. */
	public static String klipNama(int id) {
		int i = id - KLIP_FIRST;
		return i >= 0 && i < KLIP_NAMA.length ? KLIP_NAMA[i] : null;
	}

	/** True for an id a player may be posed with. */
	public static boolean klipPemain(int id) {
		for (int k : KLIP_PEMAIN) {
			if (k == id) {
				return true;
			}
		}
		return false;
	}

	private GabungGerakId() {
	}
}
