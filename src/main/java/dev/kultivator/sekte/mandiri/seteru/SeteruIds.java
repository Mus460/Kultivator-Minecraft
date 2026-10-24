package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.sekte.mandiri.api.MandiriIds;

/**
 * The reserved names of module 39F, Perseteruan antar-sekte (DESIGN 39.F): lang keys, trigger ids, statuses, memory
 * kinds, voice lines, the screen operations and the animation ids it owns on the client (clips 250-299, props 130-149,
 * schedule matrix row 17). Pure constants, safe on both physical sides (the client reads the clip, prop and operation
 * numbers from here); never rename a released value.
 */
public final class SeteruIds {

	/** Lang prefix of every 39F key ({@code kultivator.sekte.mandiri.seteru.}). */
	public static final String LANG = MandiriIds.LANG + "seteru.";

	/** {@code LANG + rest}. */
	public static String k(String rest) {
		return LANG + rest;
	}

	// ================================================================== triggers (39.F.2), saved as the feud's "sebab"

	public static final String SEBAB_HINAAN = "hinaan";
	public static final String SEBAB_HARTA = "harta";
	public static final String SEBAB_GILDA = "gilda";
	public static final String SEBAB_TURNAMEN = "turnamen";
	public static final String SEBAB_ASMARA = "asmara";
	public static final String SEBAB_GULUNGAN = "gulungan";
	public static final String SEBAB_PERUNDUNG = "perundung";
	public static final String SEBAB_DESA = "desa";
	public static final String SEBAB_PERISTIWA = "peristiwa";
	/** Every trigger in the order of the 39.F.2 table (screen icons, test command suggestions). */
	public static final String[] SEBAB = {SEBAB_HINAAN, SEBAB_HARTA, SEBAB_GILDA, SEBAB_TURNAMEN, SEBAB_ASMARA, SEBAB_GULUNGAN,
			SEBAB_PERUNDUNG, SEBAB_DESA, SEBAB_PERISTIWA};

	/** Index of a trigger in {@link #SEBAB} (the icon cell), 0 when unknown. */
	public static int sebabIndex(String sebab) {
		for (int i = 0; i < SEBAB.length; i++) {
			if (SEBAB[i].equals(sebab)) {
				return i;
			}
		}
		return 0;
	}

	/** Who started it: {@code kita} (our disciple), {@code mereka}, {@code undian} (the lot) or {@code -} (one-sided). */
	public static final String SALAH_KITA = "kita";
	public static final String SALAH_MEREKA = "mereka";
	public static final String SALAH_UNDIAN = "undian";
	public static final String SALAH_TIDAK = "-";

	// ================================================================== statuses (39.F.3)

	public static final String AKTIF = "AKTIF";
	public static final String DAMAI = "DAMAI";
	public static final String SAINGAN = "SAINGAN";
	public static final String DENDAM = "DENDAM";
	public static final String TERLARANG = "TERLARANG";
	/** Endings of forbidden lovers: they part, or they elope (the member joins the hall as a candidate). */
	public static final String BERPISAH = "BERPISAH";
	public static final String KAWIN_LARI = "KAWIN_LARI";
	public static final String[] STATUS = {AKTIF, DAMAI, SAINGAN, DENDAM, TERLARANG, BERPISAH, KAWIN_LARI};

	/** Roster member status (39.F.1). */
	public static final String TOKOH_AKTIF = "AKTIF";
	public static final String TOKOH_MEMBELOT = "MEMBELOT";
	public static final String TOKOH_PERGI = "PERGI";

	// ================================================================== ladder (39.F.3)

	/** Heat thresholds of T0-T7. */
	public static final int[] AMBANG = {1, 15, 30, 45, 60, 70, 80, 90};
	public static final int T_TEGANG = 0;
	public static final int T_CEKCOK = 1;
	public static final int T_DUEL = 2;
	public static final int T_TAWURAN = 3;
	public static final int T_SERGAPAN = 4;
	public static final int T_CAMPUR = 5;
	public static final int T_DIPLOMATIK = 6;
	public static final int T_BESAR = 7;

	/** The tier a heat value stands on now (-1 below T0). */
	public static int tingkatDari(int panas) {
		int t = -1;
		for (int i = 0; i < AMBANG.length; i++) {
			if (panas >= AMBANG[i]) {
				t = i;
			}
		}
		return t;
	}

	/** Branch ids of {@code uji ... naik <id> [cabang]} and the history lines. */
	public static final String[] CABANG = {"lewat", "naik", "maaf", "tantang", "pergi", "lapang", "sakit", "curang", "menang", "kalah",
			"lolos", "terluka", "tertawan", "ganti_rugi", "sumpah", "tantang_resmi", "perang", "utusan"};

	// ================================================================== the master's actions (39.F.4), screen op ids

	public static final int AKSI_DAMAIKAN = 0;
	public static final int AKSI_UTUS_TETUA = 1;
	public static final int AKSI_HUKUM = 2;
	public static final int AKSI_TUNTUT = 3;
	public static final int AKSI_DUKUNG = 4;
	public static final int AKSI_TANTANG_TETUA = 5;
	public static final int AKSI_LARANG = 6;
	public static final int AKSI_BIARKAN = 7;
	/** Ids of the actions (lang {@code aksi.<id>}, {@code aksi.<id>.desc}). */
	public static final String[] AKSI = {"damaikan", "utus_tetua", "hukum", "tuntut", "dukung", "tantang_tetua", "larang", "biarkan"};
	/** Forbidden lovers: the master's blessing to reconcile both sects (39.F.3 c). */
	public static final int AKSI_RESTUI = 8;
	/** The lovers part (39.F.3 b), asked by the master. */
	public static final int AKSI_PISAHKAN = 9;

	// ================================================================== payload operations (screen)

	public static final int OP_BUKA = 0;
	public static final int OP_SEGAR = 1;
	public static final int OP_AKSI = 2;
	public static final int OP_ROSTER = 3;

	// ================================================================== memories ("mandiri.seteru.*", 39.F.3 endings)

	public static final String ING_DUEL_MENANG = "mandiri.seteru.duel_menang";
	public static final String ING_DUEL_KALAH = "mandiri.seteru.duel_kalah";
	public static final String ING_DIHINA = "mandiri.seteru.dihina";
	public static final String ING_TAWURAN_MENANG = "mandiri.seteru.tawuran_menang";
	public static final String ING_TAWURAN_KALAH = "mandiri.seteru.tawuran_kalah";
	public static final String ING_DISERGAP = "mandiri.seteru.disergap";
	public static final String ING_DIBELA = "mandiri.seteru.dibela";
	public static final String ING_DIHUKUM = "mandiri.seteru.dihukum";
	public static final String ING_SUMPAH = "mandiri.seteru.sumpah";
	public static final String ING_DAMAI = "mandiri.seteru.damai";
	public static final String ING_SAINGAN = "mandiri.seteru.saingan";
	public static final String ING_DENDAM = "mandiri.seteru.dendam";
	public static final String ING_SAHABAT = "mandiri.seteru.sahabat";
	public static final String ING_KEKASIH = "mandiri.seteru.kekasih";
	public static final String ING_PISAH = "mandiri.seteru.pisah";
	public static final String ING_KAWIN_LARI = "mandiri.seteru.kawin_lari";
	public static final String ING_DITOLONG = "mandiri.seteru.ditolong";

	// ================================================================== voice lines (39.F.7, generated by 39E)

	public static final String V_EJEK = "seteru_ejek";
	public static final String V_EJEK_GURU = "seteru_ejek_guru";
	public static final String V_BALAS = "seteru_balas";
	public static final String V_TANTANG = "seteru_tantang";
	public static final String V_TERIMA = "seteru_terima";
	public static final String V_MENANG = "seteru_menang";
	public static final String V_KALAH = "seteru_kalah";
	public static final String V_KEROYOK = "seteru_keroyok";
	public static final String V_SERGAP = "seteru_sergap";
	public static final String V_LINDUNGI = "seteru_lindungi";
	public static final String V_CURI = "seteru_curi";
	public static final String V_MAAF = "seteru_maaf";
	public static final String V_SUMPAH = "seteru_sumpah";
	public static final String V_DAMAI = "seteru_damai";
	public static final String V_SAINGAN = "seteru_saingan";
	public static final String V_KEKASIH = "seteru_kekasih";
	public static final String V_TETUA_LERAI = "elder.seteru_lerai";
	public static final String V_TETUA_GANTI_RUGI = "elder.seteru_ganti_rugi";

	// ================================================================== client animation (schedule matrix row 17)

	/** Clips 250-299 (registered by {@code SeteruPoses}). */
	public static final int KLIP_CEKCOK = 250;
	public static final int KLIP_CEKCOK_BALAS = 251;
	public static final int KLIP_TANTANG = 252;
	public static final int KLIP_TERLUKA = 253;
	public static final int KLIP_SUMPAH = 254;
	public static final int KLIP_PATROLI = 255;
	public static final int KLIP_EJEK_GURU = 256;
	public static final int KLIP_MAAF = 257;
	public static final int KLIP_SORAK_KEROYOK = 258;
	public static final int KLIP_LINDUNGI = 259;
	public static final int KLIP_TANCAP_PANJI = 260;
	public static final int KLIP_RAHASIA = 261;
	public static final int KLIP_TETUA_LERAI = 262;
	public static final int KLIP_MENANG = 263;
	public static final int KLIP_KALAH = 264;
	public static final int KLIP_GANTI_RUGI = 265;
	public static final int KLIP_DAMAI = 266;
	public static final int KLIP_CURI = 267;
	/** T0: arms folded, a sideways glare and a toss of the head ("Hmph."). */
	public static final int KLIP_MELIRIK = 268;
	/** The bully looming over a villager or merchant, palm out for money, a finger poking the chest. */
	public static final int KLIP_PALAK = 269;
	/** Forbidden lovers found out: a start, a jump apart, hands hidden behind the back. */
	public static final int KLIP_KETAHUAN = 270;
	/** Lifelong rivals: the blade pointed at the other, then a fist to the heart and a nod of respect. */
	public static final int KLIP_SAINGAN = 271;
	/** A grudge: turning away with a trembling fist, one last look over the shoulder. */
	public static final int KLIP_DENDAM = 272;
	/** A mocking laugh: a hand on the belly, the head thrown back, the shoulders shaking. */
	public static final int KLIP_TERTAWA = 273;
	public static final int KLIP_LAST = 273;

	/** Props 130-149 ({@code kultivator:fx/sekte_mandiri_seteru_<name>}). */
	public static final int PROP_PERBAN = 130;
	public static final int PROP_PANJI_LURUS = 131;
	public static final int PROP_PANJI_NETRAL = 132;
	public static final int PROP_PANJI_IBLIS = 133;
	public static final int PROP_GULUNGAN_CURIAN = 134;
	public static final int PROP_SURAT_TANTANGAN = 135;
	public static final int PROP_KANTONG_GANTI_RUGI = 136;
	public static final int PROP_CAWAN_PECAH = 137;
	public static final int PROP_TALI_MERAH = 138;
	public static final int PROP_KRUK = 139;
	public static final int PROP_TIANG_PANJI = 140;
	/** Model names of 130-140 in order. */
	public static final String[] PROP_NAMA = {"perban", "panji_lurus", "panji_netral", "panji_iblis", "gulungan_curian", "surat_tantangan",
			"kantong_ganti_rugi", "cawan_pecah", "tali_merah", "kruk", "tiang_panji"};

	/** The back banner prop of an alignment (0 lurus, 1 netral, 2 iblis). */
	public static int panji(int aliran) {
		return switch (aliran) {
			case 2 -> PROP_PANJI_IBLIS;
			case 1 -> PROP_PANJI_NETRAL;
			default -> PROP_PANJI_LURUS;
		};
	}

	// ================================================================== the honour-duel title card (payload "judul")

	/** Seconds the title card shows. */
	public static final int JUDUL_DETIK = 2;

	/** Rumour templates ({@code data/kultivator/tempat/kabar/mandiri_seteru.json}). */
	public static final String KABAR_DUEL = "mandiri_seteru_duel";
	public static final String KABAR_TAWURAN = "mandiri_seteru_tawuran";
	public static final String KABAR_DAMAI = "mandiri_seteru_damai";
	public static final String KABAR_PATROLI = "mandiri_seteru_patroli";
	public static final String KABAR_KAWIN_LARI = "mandiri_seteru_kawin_lari";
	/** Our disciple stood up to their bullies (perundung/desa started by them). */
	public static final String KABAR_PEMBELA = "mandiri_seteru_pembela";
	public static final String KABAR_GULUNGAN = "mandiri_seteru_gulungan";
	public static final String KABAR_SUMPAH = "mandiri_seteru_sumpah";
	public static final String KABAR_SAINGAN = "mandiri_seteru_saingan";

	/** The {@code SibukMurid} provider id while a disciple is in a feud scene. */
	public static final String SIBUK = "seteru";

	private SeteruIds() {
	}
}
