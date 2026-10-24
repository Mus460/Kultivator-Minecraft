package dev.kultivator.sekte.mandiri.api;

/**
 * Every reserved name of DESIGN 39 and 39.F in one place (step 1 of 39.12.2): the per-disciple schema keys of
 * {@code extra.mandiri} (contract C39.A4, only 39A changes the schema; other modules write through {@code MandiriApi}),
 * saved-data names, payload names (bodies by 39E / 39F), away reasons, advancement and hub-card ids, the
 * {@code DiscipleHooks.addInfo} key and lang prefixes. Pure constants, safe on both sides; never rename a released value.
 */
public final class MandiriIds {

	// ================================================================== module, lang

	/** The module id ({@code SekteIds.MANDIRI}, config switch {@code umum.mandiri}, test tree {@code uji perguruan mandiri}). */
	public static final String MODUL = "mandiri";
	/** Lang prefix of every 39 key; sub-modules add {@code <sub>.} ({@code inti}, {@code tumbuh}, {@code jalan}, {@code kerja},
	 * {@code cerita}, {@code seteru}). */
	public static final String LANG = "kultivator.sekte.mandiri.";

	// ================================================================== extra.mandiri (DESIGN 39.2.1)

	/** The sub-compound of {@code DiscipleData.extra()} (= {@code SekteIds.EXTRA_MANDIRI}). */
	public static final String EXTRA = "mandiri";
	/** Schema version, currently {@link #VERSI}. */
	public static final String K_VERSI = "v";
	public static final int VERSI = 1;
	/** Life path id ({@link JalanHidup#id()}). */
	public static final String K_JALAN = "jalan";
	/** The path an IBLIS overlay replaced. */
	public static final String K_JALAN_ASAL = "jalanAsal";
	/** Last score of every path (for the "why" line). */
	public static final String K_JALAN_SKOR = "jalanSkor";
	/** World day the path was chosen. */
	public static final String K_JALAN_HARI = "jalanHari";
	/** Paths the master pushed (+0.8), list of ids. */
	public static final String K_JALAN_DORONG = "jalanDorong";
	/** Event bonuses per path id (39.3.5, e.g. a lost final +0.5 pedang), halved at every path check. */
	public static final String K_JALAN_BONUS = "jalanBonus";
	/** Why the current path won: short reason codes ({@code sifat.berani}, {@code bakat.sword_sense}, {@code dao.sword},
	 * {@code akar}, {@code kenangan}, {@code kebiasaan}, {@code guru}) for the "kenapa" line of the tab. */
	public static final String K_JALAN_ALASAN = "jalanAlasan";
	/** World day of the last path check (every 10 days, 39.3.2). */
	public static final String K_JALAN_CEK = "jalanCek";
	/** Temptation 0-100 (39.3.3). */
	public static final String K_IBLIS = "iblis";
	/** Active goals, list of {@code {jenis, target, mulai, prog, prio, asal}} (max 3). */
	public static final String K_CITA = "cita";
	/** Finished goals, list of {@code {jenis, target, hari, hasil}} (max 8). */
	public static final String K_CITA_SELESAI = "citaSelesai";
	/** Drives 0-100 by {@link Dorong#id()}. */
	public static final String K_DORONG = "dorong";
	/** The running activity {@code {id, mulai, sampai, tempat (long), teman (UUID)?}}. */
	public static final String K_KEGIATAN = "kegiatan";
	/** Hour strip of today, 24 {@link Kegiatan} ordinals. */
	public static final String K_RIWAYAT = "riwayat";
	/** Hour strip of yesterday. */
	public static final String K_KEMARIN = "kemarin";
	/** Purse in NB (long, 0..dompetMaks). */
	public static final String K_DOMPET = "dompet";
	/** Bag, list of at most {@link #TAS_SLOT} stacks (ItemStack codec with registry ops). */
	public static final String K_TAS = "tas";
	/** Shopping list, at most 12 {@code {item, n, prio, alasan, maksHarga}}. */
	public static final String K_BELANJA = "belanja";
	/** Gear {@code {pedang 0-5, jubah 0-3, jubahWarna, jimatSaku, jimatN}}. */
	public static final String K_PERLENGKAPAN = "perlengkapan";
	/** Game time of the last simulation (catch-up). */
	public static final String K_TERAKHIR = "terakhir";
	/** Best seat {@code {pos, faktor}}. */
	public static final String K_TEMPAT_TERBAIK = "tempatTerbaik";
	/** Seclusion {@code {mulai, sampai, tumpuk}}. */
	public static final String K_TUTUP = "tutup";
	/** Wound {@code {jenis (JenisLuka id), sampai, sebab}}. */
	public static final String K_LUKA = "luka";
	/** Decision queue {@code {terobosan, dao, jenjang, dewa}} (world days). */
	public static final String K_TUNGGU = "tunggu";
	/** Game time the master's order pause ends (39.7.2); {@link Long#MAX_VALUE} = "forever". */
	public static final String K_BEBAS = "bebas";
	/** Per-disciple policy overlay (only present keys override, see {@link Kebijakan#fromTag}). */
	public static final String K_KEBIJAKAN = "kebijakan";
	/** Statistics {@code {perjalanan, belanjaNb, hasilNb, jamKultivasi, terobosanSendiri, tekSendiri, jamMeramu}}. */
	public static final String K_STATISTIK = "statistik";
	/** Spring blessing: game time until which self XP is +20 % (39.6.9). */
	public static final String K_BERKAH = "berkah";
	/** Guild badge rank 0-5 and successes {@code {pangkat, sukses}}. */
	public static final String K_LENCANA = "lencana";

	/** World day of the last daily step of the planner (temptation, goals, path check, pocket money). */
	public static final String K_HARI = "hari";
	/** World day the pocket money was last paid (39.6.4; paid by 39A, never again by 39C). */
	public static final String K_SAKU_HARI = "sakuHari";
	/** World day the hour strip {@link #K_RIWAYAT} belongs to (rolled into {@link #K_KEMARIN} at day change). */
	public static final String K_RIWAYAT_HARI = "riwayatHari";
	/** Habit hours per path id, decaying x0.9 a day (the "kebiasaan" term of 39.3.2, about ten days of memory). */
	public static final String K_KEBIASAAN = "kebiasaan";
	/** The world day each activity was last done, by {@link Kegiatan#id()} (the "not done for 3 days" bonus, 39.4.3). */
	public static final String K_KEG_HARI = "kegHari";
	/** The realm ordinal seen at the last hourly step (a rise wakes the hall-mates' hunger, 39.4.1). */
	public static final String K_RANAH_LALU = "ranahLalu";
	/** The Dao ordinal seen at the last hourly step (-1 none): choosing a Dao re-checks the path (39.3.2). */
	public static final String K_DAO_LALU = "daoLalu";
	/** World day of the hall's last morning salute this disciple joined. */
	public static final String K_SALAM_HARI = "salamHari";
	/** Liked activities honoured today {@code {hari, n}} (at most 3 a day, 39.10.2). */
	public static final String K_SUKA = "suka";
	/** Redemption state {@code {mulai, guru, malam, malamHari}} (39.3.3: the master's ritual, a friend's nights). */
	public static final String K_TEBUS = "tebus";
	/** World day the master last scolded or praised this disciple (one each a day, 39.7.3). */
	public static final String K_TEGUR_HARI = "tegurHari";
	/** World day the last Lumbung liberty of an IBLIS disciple was rolled (2 %/day, 39.3.3). */
	public static final String K_IBLIS_HARI = "iblisHari";
	/** Fields of {@link #K_KEGIATAN}. */
	public static final String KG_ID = "id";
	public static final String KG_MULAI = "mulai";
	public static final String KG_SAMPAI = "sampai";
	public static final String KG_TEMPAT = "tempat";
	public static final String KG_TEMAN = "teman";
	/** A planner event that borrows an activity's look ({@code salam}: the morning salute). */
	public static final String KG_ACARA = "acara";
	/** Mode before a temporary {@code GUARD_HALL} gate watch (restored at the end). */
	public static final String KG_MODE_ASAL = "modeAsal";

	/** Keys of {@link #K_STATISTIK} (written through {@code MandiriApi.tambahStatistik}/{@code maksStatistik}). */
	public static final String S_PERJALANAN = "perjalanan";
	public static final String S_BELANJA_NB = "belanjaNb";
	public static final String S_HASIL_NB = "hasilNb";
	public static final String S_JAM_KULTIVASI = "jamKultivasi";
	public static final String S_TEROBOSAN = "terobosanSendiri";
	public static final String S_TEKNIK = "tekSendiri";
	public static final String S_JAM_MERAMU = "jamMeramu";
	/** Highest beast tier slain (39C journeys, 39D hunts). */
	public static final String S_BINATANG = "binatangTier";
	/** Highest secret-realm tier entered (39C). */
	public static final String S_RAHASIA = "rahasiaTier";
	/** Raiders driven off and villages helped (39A counts kills during a sect raid; 39C adds village journeys). */
	public static final String S_MENOLONG = "menolong";
	/** Highest pill or talisman grade crafted (39D). */
	public static final String S_KARYA = "karyaGrade";
	/** Failed breakthroughs in a row (39B). */
	public static final String S_GAGAL_BERUNTUN = "gagalBeruntun";
	/** Hours the disciple spent in its own planned activities (all kinds). */
	public static final String S_JAM_MANDIRI = "jamMandiri";

	/** Slots of the bag. */
	public static final int TAS_SLOT = 9;

	// ================================================================== MandiriData, per hall (DESIGN 39.2.2)

	/** The hall policy (full {@code Kebijakan} compound). */
	public static final String A_KEBIJAKAN = "kebijakan";
	/** Diary rings by soul: {@code harian.<soul>} list of {@code EntriHarian} (at most 40). */
	public static final String A_HARIAN = "harian";
	/** The last 30 news lines of the hall {@code {hari, jam, soul, kunci, arg[], penting}}. */
	public static final String A_KABAR = "kabar";
	/** Active journey records by soul (39C). */
	public static final String A_JALAN = "jalan";
	/** Named characters, at most 32 (39E). */
	public static final String A_TOKOH = "tokoh";
	/** Town stock bought by disciples {@code stokKota.<town>.<day>} (39C). */
	public static final String A_STOK = "stokKota";
	/** Money book of 7 days {@code {hari, soul, jumlah, sebab}} (39C writes purse changes, 39A pocket money). */
	public static final String A_KAS = "kas";
	/** Journey results waiting for the body by soul (39C). */
	public static final String A_PENDING = "pending";
	/** Known town destinations cache (39C). */
	public static final String A_KOTA = "kotaDikenal";
	/** Events for unloaded souls waiting for their next load (39A: goals and paths after 36 events). */
	public static final String A_ANTRE = "antre";
	/** A copy of each disciple's policy overlay so the board and unloaded checks can read it (39A). */
	public static final String A_TUMPANG = "tumpang";

	// ================================================================== saved data, away reasons

	/** {@code MandiriData}: per hall, keyed by owner UUID (39.2.2); path of the {@code SavedDataType} id {@code kultivator:mandiri}. */
	public static final String DATA_MANDIRI = "mandiri";
	/** {@code SeteruData} (39F): world-wide roster and feuds (39.F.1); id {@code kultivator:mandiri_seteru}. */
	public static final String DATA_SETERU = "mandiri_seteru";
	/** {@code Halls.storeAway} reason of a journey (39C). */
	public static final String AWAY_JALAN = "mandiri_jalan";
	/** {@code Halls.storeAway} reason of an elder sent to make peace (39F). */
	public static final String AWAY_SETERU = "mandiri_seteru";

	// ================================================================== payloads (registered by their module's init)

	/** 39E: diary page and 2-day schedule on request. */
	public static final String P_HARIAN = "mandiri_harian";
	/** 39E: the Jalan Hidup Murid board (all disciples, news, policy, purse book). */
	public static final String P_PAPAN = "mandiri_papan";
	/** 39E: actions from the tab and the board (C2S). */
	public static final String P_AKSI = "mandiri_aksi";
	/** 39E: a thought bubble {@code (entityId, ikon, kunci)}. */
	public static final String P_PIKIR = "mandiri_pikir";
	/** 39E: the small top-right toast. */
	public static final String P_TOAST = "mandiri_toast";
	/** 39F: the feud screen data (hub card "Perseteruan"). */
	public static final String P_SETERU_LAYAR = "mandiri_seteru_layar";
	/** 39F: feud actions from the screen (C2S). */
	public static final String P_SETERU_AKSI = "mandiri_seteru_aksi";
	/** 39F: the 2 s title card of an honour duel. */
	public static final String P_SETERU_JUDUL = "mandiri_seteru_judul";

	// ================================================================== hooks, cards, advancements

	/** {@code DiscipleHooks.addInfo} key (≤ 600 bytes, read by the Jalan Hidup tab). */
	public static final String INFO = "sekte_mandiri";
	/** Hub card of the MURID section (39E). */
	public static final String KARTU_JALAN = "sekte_mandiri_jalan";
	/** Hub card of the MURID section (39F). */
	public static final String KARTU_SETERU = "sekte_mandiri_seteru";
	/** Advancement ids under {@code data/kultivator/advancement/sekte/} (criteria {@code minecraft:impossible}). */
	public static final String ADV_JALAN = "mandiri_jalan";
	public static final String ADV_KEBIJAKAN = "mandiri_kebijakan";
	public static final String ADV_BELANJA = "mandiri_belanja";
	public static final String ADV_HARIAN = "mandiri_harian";
	public static final String ADV_TEMBUS = "mandiri_tembus";
	public static final String ADV_CITA = "mandiri_cita";
	public static final String ADV_ABADI = "mandiri_abadi";
	public static final String ADV_SETERU_DUEL = "mandiri_seteru_duel";
	public static final String ADV_SETERU_DAMAI = "mandiri_seteru_damai";
	public static final String ADV_SETERU_KEKASIH = "mandiri_seteru_kekasih";

	/** The Kitab Panduan chapter of DESIGN 39 (bab 21, save index 20; schedule fact 0.3), added by 39E. */
	public static final int BAB_PANDUAN = 21;

	private MandiriIds() {
	}
}
