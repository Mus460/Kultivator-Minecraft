package dev.kultivator.dunia.kota42.ui;

import dev.kultivator.dunia.tempat.ui.LembarKunci;

import java.util.List;

/**
 * The sheet contract of every DESIGN 42 screen (42.11, contract C42.U1; frozen at the end of wave 42-G0): screen ids, tab
 * ids, keys, value meanings and op verbs. Server builders (42U-1/42U-2 {@code dunia/kota42/ui/Lembar*}, filled from the
 * APIs of 42A-42F: {@code WargaApi}, {@code Cakap}, {@code HargaKota}, {@code RumahKota}, {@code PaktaLindung},
 * {@code Kavling} ...) and client screens ({@code client/dunia/kota42/ui/layar/**}, drawn with the 38.U kit "Pasar Giok",
 * read-only, 320 x 240, GUI scale 2-4, full keyboard) compile against this class only. It follows {@link LembarKunci}
 * exactly: same value types (T text, S id, I int, L long, F float, B boolean, IA/LA arrays, C compound, D list of
 * compounds, SL string list, BR item), same header keys ({@code judul}, {@code subjudul}, {@code gaya}, {@code tab},
 * {@code tab_kunci}, {@code tab_sembunyi}, {@code npc}, {@code pesan}, {@code pesan_jenis}, {@code kosong},
 * {@code kunci_layar}, {@code uang}, {@code situs}, {@code tempat}, {@code nama_baik}, {@code pita}), the same entry keys
 * ({@code id}, {@code nama}, {@code teks}, {@code barang}, {@code harga}, {@code jumlah}, {@code stok}, {@code keadaan} +
 * {@code K_*}, {@code terkunci}/{@code alasan}, {@code syarat} [ {@code ok}, {@code teks}, {@code punya} ],
 * {@code bisa}, {@code x}/{@code z}, {@code anda}, {@code sisa_tick}, {@code hari}) and the same op form
 * {@code verb:arg:arg} ({@link LembarKunci#op}). Keys are only ever added, never renamed; additions are requested in
 * {@code docs/progres/m2-42U-1.md}.
 *
 * <h2>Binding rules of every 42 sheet</h2>
 * <ul>
 * <li><b>Money is NB only</b> ({@code long}, {@link LembarKunci#UANG}, {@link LembarKunci#HARGA}); 42 has no PK price.
 * A unit price that is a fraction (contract goods "3,2 NB/unit") is sent as {@code F} next to the {@code L} total.</li>
 * <li><b>Names of warga</b> are the names the server already has ({@code WargaApi} pinyin names, 42.14.6: never lang
 * keys); every other readable text is a translatable component of the builder's fragment ({@code kota_hidup_ui},
 * {@code kota_hidup_ui2}) or of the owning module's fragment. Conversation lines are assembled by 42K from
 * {@code kota_hidup_cakap*} keys (42.5A, no LLM, no free machine text): a builder copies the {@code Component} it gets from
 * {@code Cakap} unchanged.</li>
 * <li><b>Enum values of other modules travel as lowercase ids</b> ({@code name().toLowerCase(Locale.ROOT)}), never as
 * ordinals: wajah ({@code api/KodeWajah}), kenangan ({@code api/Kenangan}), jenis hubungan ({@code api/JenisHubungan}),
 * kategori ({@code api/Kategori}), upacara ({@code api/JenisUpacara}), niat and nada ({@code cakap/Niat},
 * {@code cakap/Nada}). The {@code *_} constants below list the values the designs name; if an enum gains a value, the id
 * simply appears (the client falls back to the {@code nama} text that every such row also carries).</li>
 * <li><b>Watak</b> is an id of {@code dunia/tempat/cerita/Tokoh.WATAK} ({@code ramah ... licik}); <b>akar</b> is I 0 fana,
 * 1-5 = {@code RootGrade.ordinal() + 1}; <b>peran</b> is the {@code NpcRole} name ({@link LembarKunci#PERAN}).</li>
 * <li><b>Remote sessions</b> (opened from a hub card or {@code /kultivasi kotaku ...} with {@code npcId -1} and no anchor)
 * may only send the read verbs of {@link LembarKunci#opBaca}. 42 screens therefore select rows with
 * {@code tab:TAB:ARG} ({@link #OP_TAB_PILIH}) and page/filter long lists with {@code halaman:N:SARING:CARI}
 * ({@link #OP_HALAMAN_SARING}); a jump to another screen from a remote screen is "select with {@code tab:...:ARG}, then
 * {@code UiTempatClient.lihatDariHub(other)}": the builders keep one selection per player and screen (cleared on logout,
 * 42U-1). Every other op listed below needs an NPC or anchor session and is drawn disabled ("datangi tempatnya") in a
 * remote session.</li>
 * <li>Lists hold at most {@link LembarKunci#DAFTAR_MAKS} rows; a sheet stays under {@code TempatLayar.LEMBAR_ANGGARAN}.
 * Pictures computed by the server ({@link #DENAH}, {@link Kavling#SILUET}) are capped at 32 x 32 cells.</li>
 * </ul>
 *
 * <h2>Panel styles (42.11)</h2>
 * {@code kertas}: warga, pohon, buku kota, kantor ({@code giok} when the office belongs to the player's sect);
 * {@code kayu}: toko pemain, gudang, investasi, lapak sewa, rombongan; {@code batu}: jabatan, perkara; {@code kain}:
 * rumah, upacara (ink by kind, {@link Upacara}), pasar hantu (with {@link #GELAP}: the client darkens the cloth and
 * draws accents in the new green token {@code TempatWarna} {@code #3FA86A}, added by 42U-1), guanpu; {@code giok}: pakta,
 * cabang, kavling. The extended {@code dialog} keeps the style its builder picks (38: {@code kertas}).
 *
 * <h2>Town header (every 42 screen that shows one town)</h2>
 * <pre>
 * situs        L   site id                              tempat      T   place name ("Kota Teratai Hijau")
 * jenis_tempat S   JT_*                                 kelas_kota  I   1 kampung/pos, 2 kota, 3 kota tema, 4 ibu kota
 * musim        I   0 semi, 1 panas, 2 gugur, 3 dingin   hari_musim  I   0-23 (day inside the season, 38.N.3)
 * hari_raya    T   today's festival ("Panen"); absent none
 * kemakmuran   I   0-100 (Kemakmuran.nilai)             tren        I   -1 falling, 0, 1 rising over 7 days
 * suasana_kota I   -100..100 (WargaApi.suasanaTempat)   pajak       I   tax rate % (5-20)
 * kas_kota     L   Kas Kota NB (KotaEkonomi.kasKota; absent where the player may not see it)
 * pelindung    C   protector: nama T, lambang I (packed Lambang), pemain B (a player's sect), anda B (the player's own);
 *                  absent = unprotected
 * gelap        B   Pasar Hantu night look (see panel styles)
 * </pre>
 *
 * <h2>Warga keys (any row or card that shows one townsperson)</h2>
 * <pre>
 * warga      I   warga id (WargaData; the op argument)   nama   T  name        nama_han T  hanzi name when known
 * kelamin    I   0 man, 1 woman                           umur   I  Tahun Fana  tahap    S  TAHAP_*
 * peran      S   NpcRole name                             kerja  T  "Koki · Rumah Makan Teratai"
 * ahli       I   job skill 0-100                          watak  S  Tokoh.WATAK id   watak_nama T
 * kebiasaan  D   id S (42.4.12 habit id), nama T (0-2)
 * wajah      S   WAJAH_* (mood face)                      suasana I  -100..100
 * kesan      I   impression of the player -100..100       kenal  I  meetings 0-99   tahu_nama B  knows the player's name
 * akrab      S   AKRAB_* (band of kesan, 42.5.2)          status S  ST_*            lokasi T  "di Kota Air", "Bukit Makam Barat #7"
 * akar       I   0 fana, 1-5 RootGrade + 1                akar_pasti B  tested (else drawn "MID?")
 * potret     I   entity id of a live body (-1 none)       rupa   I  Sosok look seed: a body-less head is drawn from
 *                                                                    SosokGen.untukNpc(peran, rupa) (fallback silhouette)
 * tokoh      S   named character id (42.10, 38.S.2); absent for ordinary warga
 * </pre>
 * Sizes: a list row needs {@code warga, nama, umur, tahap, peran, status, rupa}; the rest only on cards.
 */
public final class KotaLembar {

	// ---------------------------------------------------------------------------------------------------------------
	// Town header keys

	public static final String JENIS_TEMPAT = "jenis_tempat";
	public static final String KELAS_KOTA = "kelas_kota";
	public static final String MUSIM = "musim";
	public static final String HARI_MUSIM = "hari_musim";
	public static final String HARI_RAYA = "hari_raya";
	public static final String KEMAKMURAN = "kemakmuran";
	public static final String TREN = "tren";
	public static final String SUASANA_KOTA = "suasana_kota";
	public static final String PAJAK = "pajak";
	public static final String KAS_KOTA = "kas_kota";
	public static final String PELINDUNG = "pelindung";
	public static final String LAMBANG = "lambang";
	public static final String PEMAIN = "pemain";
	public static final String GELAP = "gelap";

	/** {@link #JENIS_TEMPAT} values. */
	public static final String JT_KAMPUNG = "kampung";
	public static final String JT_POS = "pos";
	public static final String JT_KOTA = "kota";
	public static final String JT_TEMA = "tema";
	public static final String JT_IBUKOTA = "ibukota";

	// ---------------------------------------------------------------------------------------------------------------
	// Warga keys

	public static final String WARGA = "warga";
	public static final String NAMA_HAN = "nama_han";
	public static final String KELAMIN = "kelamin";
	public static final String UMUR = "umur";
	public static final String TAHAP = "tahap";
	public static final String KERJA = "kerja";
	public static final String AHLI = "ahli";
	public static final String WATAK = "watak";
	public static final String WATAK_NAMA = "watak_nama";
	public static final String KEBIASAAN = "kebiasaan";
	public static final String WAJAH = "wajah";
	public static final String SUASANA = "suasana";
	public static final String KESAN = "kesan";
	public static final String KENAL = "kenal";
	public static final String TAHU_NAMA = "tahu_nama";
	public static final String AKRAB = "akrab";
	public static final String STATUS = LembarKunci.STATUS;
	public static final String LOKASI = "lokasi";
	public static final String AKAR = "akar";
	public static final String AKAR_PASTI = "akar_pasti";
	public static final String RUPA = "rupa";

	/** {@link #KELAMIN} values. */
	public static final int PRIA = 0;
	public static final int WANITA = 1;

	/** {@link #TAHAP} values (42.4.3). */
	public static final String TAHAP_BAYI = "bayi";
	public static final String TAHAP_ANAK = "anak";
	public static final String TAHAP_REMAJA = "remaja";
	public static final String TAHAP_MUDA = "dewasa_muda";
	public static final String TAHAP_DEWASA = "dewasa";
	public static final String TAHAP_TUA = "tua";
	public static final String TAHAP_PENSIUN = "pensiun";
	public static final List<String> SEMUA_TAHAP = List.of(TAHAP_BAYI, TAHAP_ANAK, TAHAP_REMAJA, TAHAP_MUDA, TAHAP_DEWASA,
			TAHAP_TUA, TAHAP_PENSIUN);

	/** {@link #WAJAH} values (42.4.11; client icons ☺ ☁ ! ✕ ≈ + beside the name, netral none). */
	public static final String WAJAH_SENANG = "senang";
	public static final String WAJAH_NETRAL = "netral";
	public static final String WAJAH_SEDIH = "sedih";
	public static final String WAJAH_TAKUT = "takut";
	public static final String WAJAH_MARAH = "marah";
	public static final String WAJAH_MABUK = "mabuk";
	public static final String WAJAH_SAKIT = "sakit";

	/** {@link #AKRAB} values (42.5.2 bands; the heart colour): musuh &le; -40, asing never met, kenal, akrab &ge; 30, sahabat &ge; 60. */
	public static final String AKRAB_MUSUH = "musuh";
	public static final String AKRAB_ASING = "asing";
	public static final String AKRAB_KENAL = "kenal";
	public static final String AKRAB_AKRAB = "akrab";
	public static final String AKRAB_SAHABAT = "sahabat";

	/** {@link #STATUS} values of a warga (42.11.3 ring colours: hidup white, arsip grey ✝, pindah blue, sekte gold). */
	public static final String ST_HIDUP = "hidup";
	public static final String ST_ARSIP = "arsip";
	public static final String ST_JALAN = "jalan";
	public static final String ST_PINDAH = "pindah";
	public static final String ST_SEKTE = "sekte";

	// ---------------------------------------------------------------------------------------------------------------
	// Shared keys of several 42 screens

	/** C a server-drawn floor plan: lebar I, panjang I (&le; 32), warna IA (lebar x panjang ARGB, row-major x fastest; 0 = outside). */
	public static final String DENAH = "denah";
	public static final String LEBAR = "lebar";
	public static final String PANJANG = "panjang";
	public static final String WARNA = "warna";
	/** C the selected row's detail on screens with a list + detail column. */
	public static final String RINCI = "rinci";
	/** S the selected row id (echo of the last {@code pilih}/{@code tab:...:ARG}). */
	public static final String PILIH = "pilih";
	/** D button rows of a card: id S (the op verb), nama T, bisa B, alasan T (why disabled). */
	public static final String AKSI = "aksi";
	/** D a 7-point series (IA) drawn as a sparkline. */
	public static final String GRAFIK = "grafik";
	/** D sebab rows: teks T, nilai I (signed %). */
	public static final String SEBAB = "sebab";
	/** L a world day number. */
	public static final String HARI = LembarKunci.HARI;
	/** I hour 0-23 and minute 0-59 of a scheduled thing. */
	public static final String JAM = "jam";
	public static final String MENIT = "menit";
	/** T the warga who told the player something (Kabar rows). */
	public static final String DARI = "dari";
	/** L day until which something runs (rent paid to, seal ends). */
	public static final String SAMPAI = "sampai";
	/** I a direction in 8 sectors, 0 = north, clockwise. */
	public static final String ARAH = "arah";
	/** T a landmark line ("lewat Jembatan Batu Merah"). */
	public static final String PATOKAN = "patokan";
	/** S a murid soul uuid (disciple rows of 42.8). */
	public static final String SOUL = "soul";
	/** I a Realm ordinal. */
	public static final String RANAH = "ranah";
	/** I the player's money cap for a faucet this period (42.6.0), and what is used of it. */
	public static final String BATAS = "batas";
	public static final String TERPAKAI = "terpakai";
	/**
	 * B (added by 42U-1) the session is remote (hub card, command, a jump from a remote screen: {@code npcId -1} without an
	 * anchor): the client draws every write button disabled with "datangi tempatnya"; the server would drop the op anyway.
	 */
	public static final String JAUH = "jauh";
	/** T (added by 42U-1) where the writable version of a remote screen opens ("Juru Tulis di Yamen", "meja kasir tokomu"). */
	public static final String DI_MANA = "di_mana";

	// ---------------------------------------------------------------------------------------------------------------
	// Ops shared by 42 screens

	/** Read: {@code tab:TAB:ARG} switches to tab TAB and selects ARG (warga id, rumah id, kavling id ...). */
	public static final String OP_TAB_PILIH = LembarKunci.OP_TAB;
	/** Read: {@code halaman:N:SARING:CARI} page N (0-based) of a long list filtered by SARING ({@code SR_*}) and text CARI. */
	public static final String OP_HALAMAN_SARING = LembarKunci.OP_HALAMAN;
	/** {@code pilih:ID} selects a row (non-remote sessions; remote ones use {@link #OP_TAB_PILIH}). */
	public static final String OP_PILIH = "pilih";
	/** {@code kartu:WARGA} opens {@code warga_kartu} for that warga with the same NPC/anchor. */
	public static final String OP_KARTU = "kartu";

	/** {@code SARING} values of {@link #OP_HALAMAN_SARING}. */
	public static final String SR_SEMUA = "semua";
	public static final String SR_ANAK = "anak";
	public static final String SR_DEWASA = "dewasa";
	public static final String SR_TUA = "tua";
	public static final String SR_BERAKAR = "berakar";
	public static final String SR_KENAL = "kenal";
	public static final String SR_ARSIP = "arsip";
	public static final String SR_KERJA = "kerja";
	public static final String SR_MENGANGGUR = "menganggur";
	public static final List<String> SEMUA_SARING = List.of(SR_SEMUA, SR_ANAK, SR_DEWASA, SR_TUA, SR_BERAKAR, SR_KENAL,
			SR_ARSIP, SR_KERJA, SR_MENGANGGUR);

	// ---------------------------------------------------------------------------------------------------------------
	// Hub (42.11 "Hub")

	/** Card ids: DUNIA "Buku Kota" (opens kota_buku), DIRI "Rumah &amp; Usahaku" (opens kota_milikku), SEKTE "Sekte di Kota"
	 * (opens pakta; id deliberately without the {@code sekte_} prefix, so it sits in the "Aula" sub-coin of 34.6). */
	public static final String HUB_BUKU = "kota_buku";
	public static final String HUB_MILIKKU = "kota_milikku";
	public static final String HUB_SEKTE = "kota_sekte";

	/** Encyclopedia topics (42.11). */
	public static final String TOPIK_WARGA = "warga";
	public static final String TOPIK_EKONOMI = "ekonomi_kota";
	public static final String TOPIK_KEHIDUPAN = "kehidupan_kota";
	public static final String TOPIK_SEKTE = "sekte_kota";
	public static final String TOPIK_TUMBUH = "kota_tumbuh";
	public static final List<String> SEMUA_TOPIK = List.of(TOPIK_WARGA, TOPIK_EKONOMI, TOPIK_KEHIDUPAN, TOPIK_SEKTE,
			TOPIK_TUMBUH);

	/** Every screen id of 42 in 42.11 order (the extended 38 {@code dialog} is not repeated here). */
	public static final List<String> SEMUA_LAYAR = List.of(
			"warga_kartu", "warga_pohon", "kota_buku", "kota_milikku", "rumah", "toko_pemain", "investasi", "gudang",
			"jabatan", "perkara", "pasar_hantu", "guanpu", "kantor_rekrut", "pakta", "cabang", "kavling", "upacara",
			"rombongan", "lapak_sewa");

	/** Screens with a builder that also open remotely ({@code TempatLayar.daftarHub}; hub cards and {@code kotaku}). */
	public static final List<String> LAYAR_HUB = List.of("warga_kartu", "warga_pohon", "kota_buku", "kota_milikku", "rumah",
			"toko_pemain", "investasi", "gudang", "jabatan", "kantor_rekrut", "pakta", "cabang", "kavling");

	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * {@code dialog} (42.11.1, 42.5.1) — the 38 dialog screen extended for every civilian. Built by 42B's
	 * {@code BicaraWarga.lembar} (C42.B1) when no 38 data dialog applies; the 38 keys of {@link LembarKunci.Dialog} stay
	 * valid (required {@code npc} + {@code teks}, {@code gestur}, {@code akhir}, {@code jasa}, {@code quest}). A sheet is a
	 * 42 sheet when {@link #CAKAP} is true: then the client shows the eight tablets below, the speaker line gains age, job,
	 * mood icon and heart, and digit keys send {@link #OP_UCAP} instead of 38's {@code pilih}.
	 * <pre>
	 * cakap     B   this is a 42 conversation sheet
	 * npc       C   38 speaker card + warga keys: warga, umur, kelamin, kerja, wajah, kesan, akrab, tahu_nama, kenal
	 * teks      T   the current NPC utterance (from Cakap; may be 1-3 lines)
	 * raut      T   a face cue instead of numbers ("Dia tampak tidak sabar"); absent none
	 * pilihan   D   options of tablet Bicara: no I (1-9), teks T, niat S (NIAT_*), topik S (topic id of percakapan/topik),
	 *               janji B (choosing it makes a promise "[Aku akan ...]"), terkunci B, alasan T
	 * nada      C   tone picker (right edge, keys Shift+1..6): daftar D [ id S (NADA_*), nama T, bisa B, alasan T
	 *               ("kesan &ge; 20"), peluang I (0-100, only when kotaBicara.tampilPeluang) ], bawaan S (NADA_SOPAN)
	 * kabar     D   tablet Kabar (1-3): id S (gossip id), teks T (told in the speaker's manner), hari L (when heard),
	 *               tanya B (op tanya_sumber possible), jejak D [ nama T, warga I ] (chain traced so far, oldest last)
	 * sebar     C   [Sebarkan kabar baik sekteku]: bisa B, biaya L (5), alasan T (no hall, already today)
	 * jalan     C   tablet Jalan: tahu B (false: teks T is the refusal "Aku baru di sini..."), biaya L (a sly warga asks 2),
	 *               layanan D, orang D, tempat_lain D, each row: id S, nama T, ikon I (LembarKunci.Ikon, -1 none),
	 *               arah I, jarak I (blocks), patokan T, teks T (the full answer), x I, z I; orang rows add warga I and
	 *               sampai T ("sampai sore")
	 * hubungan  C   tablet Hubungan (summary; op kartu opens warga_kartu): kenal I, kesan I, utang L (signed, + the warga
	 *               owes the player), kenangan D [ id S (KN_*), nama T, hari L, kuat B ] (&le; 4), aksi D (see Kartu)
	 * tolong    D   tablet Tolong: id S ("tolong:SITUS:WARGA:HARI"), jenis S (TL_*), nama T, teks T, tujuan D [ teks T,
	 *               kemajuan I, maks I, selesai B ], imbalan T, nb L, sisa_tick L, keadaan S (K_OK | TL_DITERIMA |
	 *               TL_SELESAI | K_BATAS), x I, z I
	 * beri      C   tablet Beri: tangan C [ nama T, barang BR ] (item in hand; absent empty hand), petunjuk T ("Sepertinya
	 *               dia suka teh"), bisa B, alasan T ("satu hadiah tiap 3 hari"), berikut L (day a gift counts again)
	 * </pre>
	 * Tabs without content go in {@code tab_sembunyi} (Jasa and Quest as in 38). Ops: {@code ucap:NO:NADA} (NADA absent =
	 * sopan), {@code tanya_sumber:GOSIP}, {@code sebar}, {@code tandai_jalan:ID} (5-minute marker {@code kota/tujuan} +
	 * compass, 42B), {@code kartu}, {@code terima_tolong:ID}, {@code serahkan_tolong:ID}, {@code beri}, and the Kartu ops
	 * {@code pekerjakan}, {@code tanam}, {@code pinjam:NB}, {@code pinjamkan:NB}, {@code ajak_sekte}; 38's {@code jasa:LAYAR}
	 * and {@code quest:ID} unchanged.
	 */
	public static final class Dialog {
		public static final String LAYAR = LembarKunci.Dialog.LAYAR;
		public static final String TAB_BICARA = LembarKunci.Dialog.TAB_BICARA;
		public static final String TAB_KABAR = "kabar";
		public static final String TAB_JALAN = "jalan";
		public static final String TAB_HUBUNGAN = "hubungan";
		public static final String TAB_TOLONG = "tolong";
		public static final String TAB_BERI = "beri";
		public static final String TAB_JASA = LembarKunci.Dialog.TAB_JASA;
		public static final String TAB_QUEST = LembarKunci.Dialog.TAB_QUEST;
		public static final List<String> TABS = List.of(TAB_BICARA, TAB_KABAR, TAB_JALAN, TAB_HUBUNGAN, TAB_TOLONG, TAB_BERI,
				TAB_JASA, TAB_QUEST);

		public static final String CAKAP = "cakap";
		public static final String RAUT = "raut";
		public static final String NIAT = "niat";
		public static final String TOPIK = "topik";
		public static final String JANJI = "janji";
		public static final String NADA = "nada";
		public static final String PELUANG = LembarKunci.PELUANG;
		public static final String BAWAAN = "bawaan";
		public static final String KABAR = "kabar";
		public static final String TANYA = "tanya";
		public static final String JEJAK = "jejak";
		public static final String SEBAR = "sebar";
		public static final String JALAN = "jalan";
		public static final String TAHU = "tahu";
		public static final String LAYANAN = "layanan";
		public static final String ORANG = "orang";
		public static final String TEMPAT_LAIN = "tempat_lain";
		public static final String HUBUNGAN = "hubungan";
		public static final String UTANG = "utang";
		public static final String KENANGAN = "kenangan";
		public static final String KUAT = "kuat";
		public static final String TOLONG = "tolong";
		public static final String TUJUAN = LembarKunci.TUJUAN;
		public static final String IMBALAN = "imbalan";
		public static final String BERI = "beri";
		public static final String PETUNJUK = "petunjuk";
		public static final String BERIKUT = "berikut";

		/** {@code niat} values (42.5A.7: ask, tell, request, bargain, promise, refuse, praise, insult, leave). */
		public static final String NIAT_TANYA = "tanya";
		public static final String NIAT_KABAR = "kabar";
		public static final String NIAT_MINTA = "minta";
		public static final String NIAT_TAWAR = "tawar";
		public static final String NIAT_JANJI = "janji";
		public static final String NIAT_TOLAK = "tolak";
		public static final String NIAT_PUJI = "puji";
		public static final String NIAT_HINA = "hina";
		public static final String NIAT_PAMIT = "pamit";

		/** Tone ids in picker order 1-6 (42.5A.7). */
		public static final String NADA_SOPAN = "sopan";
		public static final String NADA_AKRAB = "akrab";
		public static final String NADA_MERAYU = "merayu";
		public static final String NADA_MENGANCAM = "mengancam";
		public static final String NADA_MENYUAP = "menyuap";
		public static final String NADA_BERCANDA = "bercanda";
		public static final List<String> SEMUA_NADA = List.of(NADA_SOPAN, NADA_AKRAB, NADA_MERAYU, NADA_MENGANCAM,
				NADA_MENYUAP, NADA_BERCANDA);

		/** {@code tolong.jenis} values (42.5.6). */
		public static final String TL_OBAT_ORTU = "obat_ortu";
		public static final String TL_KAYU_BAKAR = "kayu_bakar";
		public static final String TL_KUCING_HILANG = "kucing_hilang";
		public static final String TL_ANAK_HILANG = "anak_hilang";
		public static final String TL_SURAT_KEKASIH = "surat_kekasih";
		public static final String TL_DAMAIKAN = "damaikan";
		public static final String TL_RENTENIR = "rentenir";
		public static final String TL_PREMAN = "preman";
		public static final String TL_BARANG_DICOPET = "barang_dicopet";
		public static final String TL_BANTU_PANEN = "bantu_panen";
		public static final String TL_BAHAN_RUMAH = "bahan_rumah";
		public static final String TL_AJARI_ANAK = "ajari_anak";
		public static final String TL_CARI_JODOH = "cari_jodoh";
		public static final String TL_KABAR_ANAK_SEKTE = "kabar_anak_sekte";
		public static final String TL_PINJAM_UANG = "pinjam_uang";
		public static final String TL_UNDANGAN = "undangan";
		/** {@code tolong.keadaan} values beside the 38 ones. */
		public static final String TL_DITERIMA = "diterima";
		public static final String TL_SELESAI = "selesai";

		public static final String OP_UCAP = "ucap";
		public static final String OP_TANYA_SUMBER = "tanya_sumber";
		public static final String OP_SEBAR = "sebar";
		public static final String OP_TANDAI_JALAN = "tandai_jalan";
		public static final String OP_KARTU = KotaLembar.OP_KARTU;
		public static final String OP_TERIMA_TOLONG = "terima_tolong";
		public static final String OP_SERAHKAN_TOLONG = "serahkan_tolong";
		public static final String OP_BERI = "beri";

		private Dialog() {
		}
	}

	/**
	 * {@code warga_kartu} (42.11.2) — the townsperson card; tablet Hubungan of the dialog ({@code kartu}), Buku Kota rows,
	 * {@code /kultivasi kotaku warga <nama>}. Builder {@code LembarWarga} (42U-1) from {@code WargaApi}. Style
	 * {@code kertas}. Town header required.
	 * <pre>
	 * diri      C   warga keys (all) + tempat_kerja T, lahir C [ hari L, tempat T ], asal T ("negeri jauh"), kesehatan I
	 *               0-100, sakit B, zhuazhou C [ id S (ZH_*), nama T ] (absent: not yet), mimpi T (the warga's wish, once
	 *               told); pratinjau: the body (potret) or the Sosok of rupa
	 * pemain    C   "Tentang kamu": kenal I, kesan I, tahu_nama B, bantu I, utang L (signed), berikut L (next meaningful
	 *               gift day), kenangan D [ id S (KN_*), nama T, hari L, kuat B ] (&le; 4, strongest first)
	 * aksi      D   buttons by state: id S (A_* = op verb), nama T, bisa B, alasan T ("bekerja" on pekerjakan)
	 * keluarga  D   tab Keluarga: warga keys of a row + ikatan S (KEL_*), wafat L (archive), arsip B
	 * teman     D   tab Teman: warga I (absent for a disciple), soul S (disciple friend "murid:SOUL"), nama T, jenis S
	 *               (JH_*), kekuatan I -100..100, sejak L, lokasi T ("di mana sekarang"), rupa I, potret I
	 * ingatan   D   tab Ingatan (every memory of the player): id S (KN_*), nama T, teks T (one line), hari L, kuat B,
	 *               kesan I (the change it made)
	 * </pre>
	 * Ops: {@code pohon} (opens {@code warga_pohon} centred on this warga), {@code kartu:WARGA}, {@code beri},
	 * {@code pekerjakan:TUGAS} (TUGAS {@code pembantu:RUMAH} or {@code pegawai:TOKO}), {@code tanam} (opens
	 * {@code investasi} with the warga's business selected), {@code pinjam:NB}, {@code pinjamkan:NB}, {@code ajak_sekte},
	 * {@code tandai:X:Z}. Remote: {@code tab:pohon:WARGA} then {@code lihat} of {@code warga_pohon}.
	 */
	public static final class Kartu {
		public static final String LAYAR = "warga_kartu";
		public static final String TAB_DIRI = "diri";
		public static final String TAB_KELUARGA = "keluarga";
		public static final String TAB_TEMAN = "teman";
		public static final String TAB_INGATAN = "ingatan";
		public static final String TAB_POHON = "pohon";
		public static final List<String> TABS = List.of(TAB_DIRI, TAB_KELUARGA, TAB_TEMAN, TAB_INGATAN, TAB_POHON);

		public static final String DIRI = "diri";
		public static final String TEMPAT_KERJA = "tempat_kerja";
		public static final String LAHIR = "lahir";
		public static final String ASAL = "asal";
		public static final String KESEHATAN = "kesehatan";
		public static final String SAKIT = "sakit";
		public static final String ZHUAZHOU = "zhuazhou";
		public static final String MIMPI = "mimpi";
		public static final String PEMAIN = KotaLembar.PEMAIN;
		public static final String BANTU = "bantu";
		public static final String UTANG = "utang";
		public static final String BERIKUT = "berikut";
		public static final String KENANGAN = "kenangan";
		public static final String KUAT = "kuat";
		public static final String KELUARGA = "keluarga";
		public static final String IKATAN = "ikatan";
		public static final String WAFAT = "wafat";
		public static final String ARSIP = "arsip";
		public static final String TEMAN = "teman";
		public static final String JENIS = LembarKunci.JENIS;
		public static final String KEKUATAN = "kekuatan";
		public static final String SEJAK = "sejak";
		public static final String INGATAN = "ingatan";

		/** {@code aksi.id} values = op verbs. */
		public static final String A_BERI = "beri";
		public static final String A_PEKERJAKAN = "pekerjakan";
		public static final String A_TANAM = "tanam";
		public static final String A_PINJAM = "pinjam";
		public static final String A_PINJAMKAN = "pinjamkan";
		public static final String A_AJAK_SEKTE = "ajak_sekte";
		public static final String A_TANDAI = LembarKunci.OP_TANDAI;

		/** {@code keluarga.ikatan} values. */
		public static final String KEL_AYAH = "ayah";
		public static final String KEL_IBU = "ibu";
		public static final String KEL_PASANGAN = "pasangan";
		public static final String KEL_ANAK = "anak";
		public static final String KEL_SAUDARA = "saudara";
		public static final String KEL_KAKEK = "kakek";
		public static final String KEL_NENEK = "nenek";
		public static final String KEL_CUCU = "cucu";
		public static final String KEL_MERTUA = "mertua";
		public static final String KEL_MENANTU = "menantu";

		/** {@code teman.jenis} values (api/JenisHubungan, 42.4.5). */
		public static final String JH_SAHABAT = "sahabat";
		public static final String JH_KEKASIH = "kekasih";
		public static final String JH_PASANGAN = "pasangan";
		public static final String JH_MUSUH = "musuh";
		public static final String JH_SAINGAN = "saingan";
		public static final String JH_GURU_MURID = "guru_murid";

		/** Memory ids (api/Kenangan, 42.4.10). */
		public static final String KN_DIBERI_HADIAH = "diberi_hadiah";
		public static final String KN_DIBANTU = "dibantu";
		public static final String KN_DISELAMATKAN = "diselamatkan";
		public static final String KN_HADIR_NIKAH = "hadir_nikah";
		public static final String KN_HADIR_MAKAM = "hadir_makam";
		public static final String KN_ANAK_DIREKRUT = "anak_direkrut";
		public static final String KN_DIPUKUL = "dipukul";
		public static final String KN_DICURI = "dicuri";
		public static final String KN_RUMAH_RUSAK = "rumah_rusak";
		public static final String KN_UTANG_LUNAS = "utang_lunas";
		public static final String KN_UTANG_MANGKIR = "utang_mangkir";
		public static final String KN_CERITA_ORANG_TUA = "cerita_orang_tua";

		/** {@code zhuazhou.id} values (42.4.7 / 42.7.7: the eight objects). */
		public static final String ZH_KUAS = "kuas";
		public static final String ZH_SEMPOA = "sempoa";
		public static final String ZH_PEDANG_KAYU = "pedang_kayu";
		public static final String ZH_CAP = "cap";
		public static final String ZH_KITAB = "kitab";
		public static final String ZH_PALU = "palu";
		public static final String ZH_TASBIH = "tasbih";
		public static final String ZH_KOIN = "koin";

		public static final String OP_POHON = "pohon";
		public static final String OP_KARTU = KotaLembar.OP_KARTU;
		public static final String OP_BERI = A_BERI;
		public static final String OP_PEKERJAKAN = A_PEKERJAKAN;
		public static final String OP_TANAM = A_TANAM;
		public static final String OP_PINJAM = A_PINJAM;
		public static final String OP_PINJAMKAN = A_PINJAMKAN;
		public static final String OP_AJAK_SEKTE = A_AJAK_SEKTE;
		/** {@code pekerjakan} TUGAS prefixes. */
		public static final String TUGAS_PEMBANTU = "pembantu";
		public static final String TUGAS_PEGAWAI = "pegawai";

		private Kartu() {
		}
	}

	/**
	 * {@code warga_pohon} (42.11.3) — the family tree, three generations up and down of the centre. Builder
	 * {@code LembarPohon} (42U-1) from {@code WargaData} + {@code ArsipWarga}. Style {@code kertas}. No tabs. The server
	 * sends the graph, the client lays it out (drag to pan, 3 generations visible).
	 * <pre>
	 * pusat     I   warga id of the centre (gen 0)
	 * simpul    D   one per person (&le; 48): warga keys of a row (warga is the archive id for archived people) + gen I
	 *               (-3..3 relative to the centre), ayah I, ibu I, pasangan I (ids, 0 none), lahir L, wafat L (archive),
	 *               arsip B, makam C [ nama T ("Bukit Barat"), nomor I, x I, z I ] (archive)
	 * naik      B   more generations above the shown ones   turun  B  more below
	 * </pre>
	 * Ops: {@code pusat:WARGA} (re-centre), {@code kartu:WARGA} (living warga; an archive node shows its summary in a
	 * tooltip), {@code tandai:X:Z} (grave). Remote: {@code tab:pusat:WARGA} (re-centre), {@code tab:kartu:WARGA} then
	 * {@code lihat} of {@code warga_kartu}.
	 */
	public static final class Pohon {
		public static final String LAYAR = "warga_pohon";

		public static final String PUSAT = "pusat";
		public static final String SIMPUL = "simpul";
		public static final String GEN = "gen";
		public static final String AYAH = "ayah";
		public static final String IBU = "ibu";
		public static final String PASANGAN = "pasangan";
		public static final String LAHIR = "lahir";
		public static final String WAFAT = "wafat";
		public static final String ARSIP = "arsip";
		public static final String MAKAM = "makam";
		public static final String NOMOR = "nomor";
		public static final String NAIK = "naik";
		public static final String TURUN = "turun";

		public static final String OP_PUSAT = "pusat";
		public static final String OP_KARTU = KotaLembar.OP_KARTU;

		private Pohon() {
		}
	}

	/**
	 * {@code kota_buku} (42.11.4) — Buku Kota; hub card {@link #HUB_BUKU} (nearest or chosen known town), the town hall
	 * board, {@code /kultivasi kotaku buku [kota]}. Builder {@code LembarBuku} (42U-1). Style {@code kertas}. Town header
	 * required.
	 * <pre>
	 * ringkas   C   tab Ringkas: penduduk I, kasur I, lahir I, wafat I, masuk I, keluar I (last 7 days), wali T (mayor),
	 *               jabatan T (the player's office here), upeti_siap B, kenal_warga I (warga who know the player),
	 *               penuh B ("Kota Penuh": every lot built), acara D [ teks T, hari L, jam I, menit I, tempat T, x I, z I ]
	 *               (next &le; 3), malam T (Pasar Hantu hint "beli sandi di kedai teh"; absent none)
	 * daftar    D   tab Warga (one page of &le; 48): warga keys of a row + kerja T, kenal B, kesan I, wajah S;
	 *               halaman I, halaman_maks I, jumlah I (matches), saring S (SR_*), cari T echo
	 * acara     D   tab Acara (14 days): id S, jenis S (AC_*), nama T, hari L, jam I, menit I, tempat T, x I, z I,
	 *               undangan B (the player is invited), sisa_tick L (today only)
	 * harga     D   tab Harga (12 categories): id S (api/Kategori), nama T, faktor F (0.6-1.6), grafik IA (7 days, x100),
	 *               stok I (0-200), sebab D [ teks T, nilai I ]
	 * kavling   D   tab Kavling: id I (lot number), nama T, rencana T, tahap I, tahap_total I (5), keadaan S (KV_*), x I, z I
	 * kabar     D   tab Kabar (gossip the player heard in this town): id S, teks T, dari T, dari_warga I, hari L, panas I 0-100
	 * </pre>
	 * Ops (all read): {@code halaman:N:SARING:CARI}, {@code tab:...}, {@code tab:warga:WARGA} then {@code lihat} of
	 * {@code warga_kartu}, {@code tab:kavling:ID} then {@code lihat} of {@code kavling}, {@code tandai:X:Z}.
	 */
	public static final class Buku {
		public static final String LAYAR = "kota_buku";
		public static final String TAB_RINGKAS = "ringkas";
		public static final String TAB_WARGA = "warga";
		public static final String TAB_ACARA = "acara";
		public static final String TAB_HARGA = "harga";
		public static final String TAB_KAVLING = "kavling";
		public static final String TAB_KABAR = "kabar";
		/**
		 * Jump tab (added by 42U-1, "No GUI twice"): {@code tab:milikku} opens {@code kota_milikku}. Tabs {@code acara} and
		 * {@code harga} are jumps too: the server answers {@code tab:acara} with the 38 {@code acara} screen (rows
		 * {@code jadwal}: the 14-day calendar) and {@code tab:harga} with {@code gudang} on its tab {@link Gudang#TAB_HARGA}
		 * (the one trade ledger); {@code tab:kavling:ID} opens 42U-2's {@code kavling}.
		 */
		public static final String TAB_MILIKKU = "milikku";
		public static final List<String> TABS = List.of(TAB_RINGKAS, TAB_WARGA, TAB_ACARA, TAB_HARGA, TAB_KAVLING, TAB_KABAR, TAB_MILIKKU);

		public static final String RINGKAS = "ringkas";
		public static final String PENDUDUK = "penduduk";
		public static final String KASUR = "kasur";
		public static final String LAHIR = "lahir";
		public static final String WAFAT = "wafat";
		public static final String MASUK = "masuk";
		public static final String KELUAR = "keluar";
		public static final String WALI = "wali";
		public static final String JABATAN = "jabatan";
		public static final String UPETI_SIAP = "upeti_siap";
		public static final String KENAL_WARGA = "kenal_warga";
		public static final String PENUH = "penuh";
		public static final String ACARA = "acara";
		public static final String MALAM = "malam";
		public static final String HALAMAN = "halaman";
		public static final String HALAMAN_MAKS = "halaman_maks";
		public static final String SARING = "saring";
		public static final String CARI = "cari";
		public static final String UNDANGAN = "undangan";
		public static final String HARGA = "harga";
		public static final String FAKTOR = "faktor";
		public static final String STOK = LembarKunci.STOK;
		public static final String KAVLING = "kavling";
		public static final String RENCANA = "rencana";
		public static final String TAHAP_TOTAL = LembarKunci.TAHAP_TOTAL;
		public static final String KABAR = "kabar";
		public static final String DARI_WARGA = "dari_warga";
		public static final String PANAS = "panas";

		/** {@code acara.jenis} values. */
		public static final String AC_NIKAH = "nikah";
		public static final String AC_MAKAM = "makam";
		public static final String AC_LAHIR = "lahir";
		public static final String AC_SEBULAN = "sebulan";
		public static final String AC_ZHUAZHOU = "zhuazhou";
		public static final String AC_ROMBONGAN = "rombongan";
		public static final String AC_HARI_RAYA = "hari_raya";
		public static final String AC_RONDA = "ronda";
		public static final String AC_UJIAN_TERBUKA = "ujian_terbuka";
		public static final String AC_PASAR_PAGI = "pasar_pagi";
		public static final String AC_WAYANG = "wayang";
		public static final String AC_SERBUAN = "serbuan";
		public static final String AC_PERESMIAN = "peresmian";
		public static final String AC_SIDANG = "sidang";

		private Buku() {
		}
	}

	/**
	 * {@code kota_milikku} — "Rumah &amp; Usahaku", hub card {@link #HUB_MILIKKU} (DIRI) and {@code /kultivasi kotaku}
	 * without a sub: one row per asset the player holds in any town, each a jump to its screen. Builder {@code LembarBuku}
	 * (42U-1). Style {@code kayu}. No town header (spans towns).
	 * <pre>
	 * daftar    D   id S (the asset id: rumah id, toko id, usaha id, gudang id, kontrak id, "jabatan:SITUS"), jenis S
	 *               (MJ_*), layar S (the screen it opens), nama T, tempat T, situs L, ringkas T ("sewa s.d. hari 61",
	 *               "laci 412 NB", "+28 minggu ini"), peringatan T (absent none: "pajak belum dibayar 2x"), x I, z I
	 * jumlah    C   rumah I, rumah_maks I, toko I, usaha I, usaha_maks I, gudang I, kontrak I, jabatan I
	 * minggu    C   this week's faucets: toko L, investasi L, kontrak L, gaji L, upeti L, bunga L (NB in, for the 42.6.0
	 *               caps), batas C [ same keys, NB caps ]
	 * </pre>
	 * Ops (read): {@code tab:daftar:ID} then {@code lihat} of the row's {@code layar}; {@code tandai:X:Z}.
	 */
	public static final class Milikku {
		public static final String LAYAR = "kota_milikku";

		public static final String LAYAR_TUJUAN = "layar";
		public static final String RINGKAS = "ringkas";
		public static final String PERINGATAN = "peringatan";
		public static final String JUMLAH = "jumlah";
		public static final String MINGGU = "minggu";
		public static final String BATAS = KotaLembar.BATAS;

		/** {@code daftar.jenis} values (and the {@code jumlah}/{@code minggu} keys). */
		public static final String MJ_RUMAH = "rumah";
		public static final String MJ_TOKO = "toko";
		public static final String MJ_USAHA = "usaha";
		public static final String MJ_GUDANG = "gudang";
		public static final String MJ_KONTRAK = "kontrak";
		public static final String MJ_JABATAN = "jabatan";
		public static final String MJ_LAPAK = "lapak";
		public static final String MAKS_RUMAH = "rumah_maks";
		public static final String MAKS_USAHA = "usaha_maks";
		public static final String M_INVESTASI = "investasi";
		public static final String M_GAJI = "gaji";
		public static final String M_UPETI = "upeti";
		public static final String M_BUNGA = "bunga";

		private Milikku() {
		}
	}

	/**
	 * {@code rumah} (42.11.5) — houses for sale or rent, the player's houses and household help. Builder
	 * {@code LembarRumah} (42U-1) from 42C's {@code RumahKota}. Opened at the land office (Jasa tab "Rumah" of the Yamen,
	 * village hall or sect office NPC), at a {@code dijual} sign (anchor), and remotely from {@code kota_milikku} (tab
	 * Milikku, read only). Style {@code kain}. Town header required.
	 * <pre>
	 * daftar    D   tabs Dijual / Sewa: id S ("SITUS:N"), nama T ("Rumah Sedang · Jalan Lentera No. 4"), jenis S (RJ_*),
	 *               kasur I, lebar I, panjang I (blocks), harga L (buy), sewa L (per 7 days), pajak L (per 7 days),
	 *               sumber S (RS_DIJUAL | RS_KOSONG | RS_KAVLING), keadaan S, alasan T, x I, z I
	 * rinci     C   the selected house: id S, faktor_kota F, faktor_makmur F, faktor_harga F (HargaKota BANGUNAN),
	 *               denah C (DENAH: floor/wall colours from the Cetak record, never client chunks),
	 *               tetangga D [ warga I, nama T, akrab S, kesan I ], syarat D [ ok B, teks T, punya T ]
	 * milik     D   tab Milikku: id S, nama T, status S (RS_MILIK | RS_SEWA | RS_SEGEL), sampai L (rent paid to / seal ends),
	 *               tunggak I (unpaid tax weeks 0-3), kunci B (key item exists), nilai_jual L (50 %), cabang T (branch
	 *               name, absent none), toko B (shop house with a registered shop), x I, z I
	 * pembantu  D   tab Pembantu: warga keys of a row + watak S, ahli I, gaji L (per 7 days), rumah S (house id), dipekerjakan B
	 *               (false = an unemployed candidate)
	 * rumah_maks I  kotaEkonomi.rumahMaks            rumah_jumlah I  houses held in all towns
	 * </pre>
	 * Ops: {@code pilih:ID}, {@code beli:ID}, {@code sewa:ID:MINGGU} (1-4), {@code perpanjang:ID:MINGGU},
	 * {@code bayar_pajak:ID}, {@code kunci:ID} (reprint the key, 5 NB), {@code jual:ID} then {@code jual_ya:ID},
	 * {@code pekerjakan:WARGA:RUMAH}, {@code berhentikan:WARGA}, {@code tandai:X:Z}.
	 */
	public static final class Rumah {
		public static final String LAYAR = "rumah";
		public static final String TAB_DIJUAL = "dijual";
		public static final String TAB_SEWA = "sewa";
		public static final String TAB_MILIKKU = "milikku";
		public static final String TAB_PEMBANTU = "pembantu";
		public static final List<String> TABS = List.of(TAB_DIJUAL, TAB_SEWA, TAB_MILIKKU, TAB_PEMBANTU);

		public static final String KASUR = "kasur";
		public static final String SEWA = "sewa";
		public static final String PAJAK = KotaLembar.PAJAK;
		public static final String SUMBER = "sumber";
		public static final String FAKTOR_KOTA = "faktor_kota";
		public static final String FAKTOR_MAKMUR = "faktor_makmur";
		public static final String FAKTOR_HARGA = "faktor_harga";
		public static final String TETANGGA = "tetangga";
		public static final String MILIK = "milik";
		public static final String TUNGGAK = "tunggak";
		public static final String KUNCI = "kunci";
		public static final String NILAI_JUAL = "nilai_jual";
		public static final String CABANG = "cabang";
		public static final String TOKO = "toko";
		public static final String PEMBANTU = "pembantu";
		public static final String GAJI = "gaji";
		public static final String RUMAH = "rumah";
		public static final String DIPEKERJAKAN = "dipekerjakan";
		public static final String RUMAH_MAKS = "rumah_maks";
		public static final String RUMAH_JUMLAH = "rumah_jumlah";

		/** {@code jenis} values (42.6.1 price table). */
		public static final String RJ_KECIL = "kecil";
		public static final String RJ_SEDANG = "sedang";
		public static final String RJ_BESAR = "besar";
		public static final String RJ_RUMAH_TOKO = "rumah_toko";
		public static final String RJ_PAVILIUN = "paviliun";
		/** {@code sumber} and {@code milik.status} values. */
		public static final String RS_DIJUAL = "dijual";
		public static final String RS_KOSONG = "kosong";
		public static final String RS_KAVLING = "kavling";
		public static final String RS_MILIK = "milik";
		public static final String RS_SEWA = "sewa";
		public static final String RS_SEGEL = "segel";

		public static final String OP_BELI = "beli";
		public static final String OP_SEWA = "sewa";
		public static final String OP_PERPANJANG = "perpanjang";
		public static final String OP_BAYAR_PAJAK = "bayar_pajak";
		public static final String OP_KUNCI = "kunci";
		public static final String OP_JUAL = "jual";
		public static final String OP_JUAL_YA = "jual_ya";
		public static final String OP_PEKERJAKAN = "pekerjakan";
		public static final String OP_BERHENTIKAN = "berhentikan";

		private Rumah() {
		}
	}

	/**
	 * {@code toko_pemain} (42.11.6) — the player's own shop. Builder {@code LembarToko} (42U-1) from 42C's
	 * {@code TokoPemain}; opened at the shop's {@code meja_kasir} or {@code papan_nama_toko} (anchor), remotely from
	 * {@code kota_milikku}. Style {@code kayu}. Town header required.
	 * <pre>
	 * toko      C   id S, nama T ("Pil Seribu Embun"), jenis S (TJ_*), reputasi I 0-100, buka B, tutup T (why closed),
	 *               merek B (sect brand of a branch), pajak I (%), laci L, ke_bank B (sales go to the Rumah Uang),
	 *               batas L (daily NB cap), terpakai L (sold today), rak_maks I (12)
	 * rak       D   tab Rak: no I, x I, y I, z I, isi D [ slot I (0-2), nama T, barang BR, jumlah I, harga L (each), nilai L
	 *               (base value), faktor F (HargaKota), h F (harga / nilai), laku I (% sale chance per busy hour) ]
	 * rinci     C   selected stack: rak I, slot I, nama T, barang BR, harga L, nilai L, faktor F, tren I (-1/0/1), laku I,
	 *               harga_pasar L, harga_maks L (1.5 x nilai)
	 * pegawai   D   tab Pegawai: warga keys of a row or soul S + murid B (disciple, no NB wage), watak S, ahli I, gaji L,
	 *               dipekerjakan B (false = candidate)
	 * laporan   C   tab Laporan: hari D [ hari L, pembeli I, terjual I, nb L ] (7), barang D [ nama T, barang BR,
	 *               terjual I, nb L ] (top 8), keluhan I, pencurian I (stacks lost this week)
	 * </pre>
	 * Ops: {@code pilih:RAK:SLOT}, {@code harga:RAK:SLOT:NB}, {@code harga_pasar:RAK:SLOT}, {@code ambil_laci},
	 * {@code ke_bank:0|1}, {@code buka:0|1}, {@code nama:TEKS} (&le; 24 chars), {@code pekerjakan:WARGA},
	 * {@code pekerjakan_murid:SOUL}, {@code berhentikan:ID}. Tab Harga is the price list of every stack (same rows).
	 */
	public static final class Toko {
		public static final String LAYAR = "toko_pemain";
		public static final String TAB_RAK = "rak";
		public static final String TAB_HARGA = "harga";
		public static final String TAB_PEGAWAI = "pegawai";
		public static final String TAB_LACI = "laci";
		public static final String TAB_LAPORAN = "laporan";
		public static final List<String> TABS = List.of(TAB_RAK, TAB_HARGA, TAB_PEGAWAI, TAB_LACI, TAB_LAPORAN);

		public static final String TOKO = "toko";
		public static final String REPUTASI = "reputasi";
		public static final String BUKA = "buka";
		public static final String TUTUP = "tutup";
		public static final String MEREK = "merek";
		public static final String LACI = "laci";
		public static final String KE_BANK = "ke_bank";
		public static final String RAK_MAKS = "rak_maks";
		public static final String RAK = "rak";
		public static final String ISI = "isi";
		public static final String Y = "y";
		public static final String FAKTOR = "faktor";
		public static final String H = "h";
		public static final String LAKU = "laku";
		public static final String HARGA_PASAR = "harga_pasar";
		public static final String HARGA_MAKS = "harga_maks";
		public static final String PEGAWAI = "pegawai";
		public static final String MURID = "murid";
		public static final String GAJI = "gaji";
		public static final String DIPEKERJAKAN = "dipekerjakan";
		public static final String LAPORAN = "laporan";
		public static final String PEMBELI = "pembeli";
		public static final String TERJUAL = "terjual";
		public static final String KELUHAN = "keluhan";
		public static final String PENCURIAN = "pencurian";

		/** {@code toko.jenis} values. */
		public static final String TJ_RUMAH_TOKO = "rumah_toko";
		public static final String TJ_LAPAK = "lapak";
		public static final String TJ_CABANG = "cabang";

		public static final String OP_HARGA = "harga";
		public static final String OP_HARGA_PASAR = "harga_pasar";
		public static final String OP_AMBIL_LACI = "ambil_laci";
		public static final String OP_KE_BANK = "ke_bank";
		public static final String OP_BUKA = "buka";
		public static final String OP_NAMA = "nama";
		public static final String OP_PEKERJAKAN = "pekerjakan";
		public static final String OP_PEKERJAKAN_MURID = "pekerjakan_murid";
		public static final String OP_BERHENTIKAN = "berhentikan";
		/** Longest shop name accepted by {@code nama:TEKS}. */
		public static final int NAMA_MAKS = 24;

		private Toko() {
		}
	}

	/**
	 * {@code investasi} (42.11.7) — stakes in warga businesses. Builder {@code LembarInvestasi} (42U-1) from 42C-2's
	 * {@code UsahaWarga}/{@code Investasi}; opened at the broker (Juragan Shen Wan, Rumah Uang), from a business owner's
	 * card ({@code tanam}), remotely from {@code kota_milikku}. Style {@code kayu}.
	 * <pre>
	 * ringkas   C   ditanam L, nilai L, minggu_ini L (signed), batas L / terpakai L (weekly faucet, 42.6.0), usaha I,
	 *               usaha_maks I (10), per_tempat_maks I (3)
	 * daftar    D   tab Portofolio: id S (usaha id), nama T ("Bakmi Wu"), tempat T, saham F (0-1), setor L, nilai L,
	 *               laba_minggu L (signed), grafik IA (7 weeks), pemilik T, pemilik_warga I, peringatan T ("pemilik sakit
	 *               (ahli waris: Lin Hu, magang)"), tarik_hari L (notice running until; absent none), pengelola T
	 * peluang   D   tab Peluang: id S, nama T, tempat T, jenis S (business kind), modal L, omzet L (daily estimate),
	 *               margin I (%), pemilik T, watak S, min L (50), maks L, bisa B, alasan T, x I, z I
	 * </pre>
	 * Ops: {@code tanam:ID:NB}, {@code tarik:ID} (7-day notice, 2 % fee), {@code batal_tarik:ID},
	 * {@code pengelola:ID:SOUL}, {@code lepas_pengelola:ID}, {@code pilih:ID}, {@code tandai:X:Z}. Tab Tarik lists the
	 * running withdrawals (rows of {@code daftar} with {@code tarik_hari}).
	 */
	public static final class Investasi {
		public static final String LAYAR = "investasi";
		public static final String TAB_PORTOFOLIO = "portofolio";
		public static final String TAB_PELUANG = "peluang";
		public static final String TAB_TARIK = "tarik";
		public static final List<String> TABS = List.of(TAB_PORTOFOLIO, TAB_PELUANG, TAB_TARIK);

		public static final String RINGKAS = "ringkas";
		public static final String DITANAM = "ditanam";
		public static final String MINGGU_INI = "minggu_ini";
		public static final String USAHA = "usaha";
		public static final String USAHA_MAKS = "usaha_maks";
		public static final String PER_TEMPAT_MAKS = "per_tempat_maks";
		public static final String SAHAM = "saham";
		public static final String SETOR = "setor";
		public static final String LABA_MINGGU = "laba_minggu";
		public static final String PEMILIK = "pemilik";
		public static final String PEMILIK_WARGA = "pemilik_warga";
		public static final String PERINGATAN = "peringatan";
		public static final String TARIK_HARI = "tarik_hari";
		public static final String PENGELOLA = "pengelola";
		public static final String PELUANG = LembarKunci.PELUANG;
		public static final String MODAL = "modal";
		public static final String OMZET = "omzet";
		public static final String MARGIN = "margin";
		public static final String MIN = "min";
		public static final String MAKS = LembarKunci.MAKS;

		public static final String OP_TANAM = "tanam";
		public static final String OP_TARIK = "tarik";
		public static final String OP_BATAL_TARIK = "batal_tarik";
		public static final String OP_PENGELOLA = "pengelola";
		public static final String OP_LEPAS_PENGELOLA = "lepas_pengelola";

		private Investasi() {
		}
	}

	/**
	 * {@code gudang} (42.11.8) — warehouses, transfers, trade contracts and city orders. Builder {@code LembarGudang}
	 * (42U-1) from 42C-2's {@code GudangKota}/{@code KontrakDagang}/{@code RumahDagang} and 42F's city orders; opened at
	 * the warehouse keeper (Nyonya Fang, head porter) or the Rumah Dagang clerk, remotely from {@code kota_milikku}. Style
	 * {@code kayu}. Town header required.
	 * <pre>
	 * gudang    D   tab Gudang: id S, nama T ("Gudang Pelabuhan Timur"), tempat T, slot I (used), slot_maks I (27/54/108),
	 *               sampai L, sewa L (per 7 days), x I, z I
	 * isi       D   the selected warehouse: slot I, nama T, barang BR, jumlah I
	 * tawaran_sewa D  offers here: id S, nama T, slot_maks I, harga L, bisa B, alasan T
	 * kirim     D   tab Kirim (running caravans): id S, dari T, ke T, isi D [ nama T, barang BR, jumlah I ], sisa_hari I,
	 *               risiko I (% per day), kawal B, ongkos L
	 * kontrak   D   tab Kontrak: id S, nama T ("Kontrak II: 64 Teh Awan → Ibu Kota"), barang BR, jumlah I, asal T, tujuan T,
	 *               tiap I (days, 7), kali I, kali_maks I, stok I (in the source warehouse), cukup B, berangkat L,
	 *               harga_satuan F (NB per unit), denda I (%), tingkat I (I-V), keadaan S
	 * tawaran   D   contracts on offer (Rumah Dagang, Papan Kontrak Muatan): kontrak keys + bisa B, alasan T
	 * nama_dagang I 0-1000    tingkat I 1-5    kontrak_maks I (2 + tingkat)
	 * pesanan   D   tab Pesanan (Pesanan Kota): id S, nama T, barang BR, perlu I, terkumpul I, punya I (in the bag), harga L
	 *               (per unit, HargaKota x 1.1), nama_baik I (+1 per 32)
	 * </pre>
	 * Ops: {@code pilih:ID}, {@code sewa:ID}, {@code perpanjang:ID}, {@code taruh:ID:SLOTTAS} (inventory slot 0-35 into the
	 * warehouse; filled containers and rings are refused), {@code ambil:ID:SLOT}, {@code kirim:DARI:KE:SLOT:N},
	 * {@code kawal:ID}, {@code ambil_kontrak:ID}, {@code batal_kontrak:ID}, {@code serahkan:ID:N}, {@code tandai:X:Z}.
	 */
	public static final class Gudang {
		public static final String LAYAR = "gudang";
		public static final String TAB_GUDANG = "gudang";
		public static final String TAB_KIRIM = "kirim";
		public static final String TAB_KONTRAK = "kontrak";
		public static final String TAB_PESANAN = "pesanan";
		/**
		 * Tab Harga (added by 42U-1, "No GUI twice": the Buku Kota price tab lives in the one trade ledger): rows
		 * {@link #HARGA} exactly as {@link Buku#HARGA} for the town of the header.
		 */
		public static final String TAB_HARGA = "harga";
		public static final List<String> TABS = List.of(TAB_HARGA, TAB_GUDANG, TAB_KIRIM, TAB_KONTRAK, TAB_PESANAN);
		public static final String HARGA = Buku.HARGA;

		public static final String GUDANG = "gudang";
		public static final String SLOT_MAKS = "slot_maks";
		public static final String SEWA = "sewa";
		public static final String ISI = "isi";
		public static final String TAWARAN_SEWA = "tawaran_sewa";
		public static final String KIRIM = "kirim";
		public static final String KE = "ke";
		public static final String RISIKO = LembarKunci.RISIKO;
		public static final String KAWAL = "kawal";
		public static final String ONGKOS = "ongkos";
		public static final String KONTRAK = "kontrak";
		public static final String ASAL = "asal";
		public static final String TIAP = "tiap";
		public static final String KALI = "kali";
		public static final String KALI_MAKS = "kali_maks";
		public static final String CUKUP = "cukup";
		public static final String BERANGKAT = "berangkat";
		public static final String HARGA_SATUAN = "harga_satuan";
		public static final String DENDA = "denda";
		public static final String TINGKAT = "tingkat";
		public static final String TAWARAN = "tawaran";
		public static final String NAMA_DAGANG = "nama_dagang";
		public static final String KONTRAK_MAKS = "kontrak_maks";
		public static final String PESANAN = "pesanan";
		public static final String PERLU = "perlu";
		public static final String TERKUMPUL = "terkumpul";
		public static final String PUNYA = "punya";

		public static final String OP_SEWA = "sewa";
		public static final String OP_PERPANJANG = "perpanjang";
		public static final String OP_TARUH = "taruh";
		public static final String OP_AMBIL = "ambil";
		public static final String OP_KIRIM = "kirim";
		public static final String OP_KAWAL = "kawal";
		public static final String OP_AMBIL_KONTRAK = "ambil_kontrak";
		public static final String OP_BATAL_KONTRAK = "batal_kontrak";
		public static final String OP_SERAHKAN = "serahkan";

		private Gudang() {
		}
	}

	/**
	 * {@code jabatan} (42.11.9) — the player's town office. Builder {@code LembarJabatan} (42U-2) from 42C-2's
	 * {@code Jabatan}/{@code PajakKota}/{@code Perkara}; opened at Panitera Qiu (Yamen) or the town hall clerk, remotely
	 * from {@code kota_milikku}. Style {@code batu}. Town header required.
	 * <pre>
	 * jabatan   C   pangkat I (0 none, 1-5), gelar T, gaji L (last 7 days), gaji_maks L, tugas D [ teks T, kemajuan I,
	 *               maks I, selesai B ], hadir L (last attendance day), peringatan T (14/28-day absence), hak D [ teks T ],
	 *               berikut C [ pangkat I, gelar T, syarat D [ ok B, teks T, punya T ], bisa B, alasan T ],
	 *               tempat_tinggi I / tempat_tinggi_maks I (towns with rank &ge; 3, max 2)
	 * pajak     C   tab Pajak: tarif I, min I, maks I (rank 4: tarif &plusmn; 2; rank 5: 5-20), bisa B, alasan T,
	 *               masuk D [ teks T, nb L ], keluar D [ teks T, nb L ] (last week), efek T ("tarif &gt; 10 %: suasana -1 per poin"),
	 *               festival L (rank 5 festival budget; absent below)
	 * perkara   D   tab Perkara: id S, nama T ("Ayam Bibi Wang hilang"), saksi I, bukti I, sisa_hari I, selesai B, putusan T
	 * catatan   C   diputus I, suap I (accepted), audit I
	 * </pre>
	 * Tab Kavling (rank &ge; 4) shows {@link Buku}'s {@code kavling} rows. Ops: {@code ajukan} (apply for the next rank:
	 * starts its quest or dialog), {@code pajak:TARIF}, {@code festival:NB}, {@code perkara:ID} (opens {@code perkara}),
	 * {@code kavling:ID} (opens {@code kavling}), {@code tandai:X:Z}.
	 */
	public static final class Jabatan {
		public static final String LAYAR = "jabatan";
		public static final String TAB_JABATAN = "jabatan";
		public static final String TAB_PAJAK = "pajak";
		public static final String TAB_PERKARA = "perkara";
		public static final String TAB_KAVLING = "kavling";
		public static final List<String> TABS = List.of(TAB_JABATAN, TAB_PAJAK, TAB_PERKARA, TAB_KAVLING);

		public static final String JABATAN = "jabatan";
		public static final String PANGKAT = "pangkat";
		public static final String GELAR = LembarKunci.GELAR;
		public static final String GAJI = "gaji";
		public static final String GAJI_MAKS = "gaji_maks";
		public static final String TUGAS = "tugas";
		public static final String HADIR = "hadir";
		public static final String PERINGATAN = "peringatan";
		public static final String HAK = "hak";
		public static final String BERIKUT = "berikut";
		public static final String TEMPAT_TINGGI = "tempat_tinggi";
		public static final String TEMPAT_TINGGI_MAKS = "tempat_tinggi_maks";
		public static final String PAJAK = KotaLembar.PAJAK;
		public static final String TARIF = "tarif";
		public static final String MIN = "min";
		public static final String MASUK = "masuk";
		public static final String KELUAR = "keluar";
		public static final String EFEK = "efek";
		public static final String FESTIVAL = "festival";
		public static final String PERKARA = "perkara";
		public static final String SAKSI = "saksi";
		public static final String BUKTI = "bukti";
		public static final String SELESAI = "selesai";
		public static final String PUTUSAN = "putusan";
		public static final String CATATAN = LembarKunci.CATATAN;
		public static final String DIPUTUS = "diputus";
		public static final String SUAP = "suap";
		public static final String AUDIT = "audit";

		/** Office ranks (42.6.6). */
		public static final int JURU_TULIS = 1;
		public static final int PENGAWAS_PASAR = 2;
		public static final int HAKIM_PEMBANTU = 3;
		public static final int WAKIL_WALI_KOTA = 4;
		public static final int WALI_KOTA = 5;

		public static final String OP_AJUKAN = "ajukan";
		public static final String OP_PAJAK = "pajak";
		public static final String OP_FESTIVAL = "festival";
		public static final String OP_PERKARA = "perkara";
		public static final String OP_KAVLING = "kavling";

		private Jabatan() {
		}
	}

	/**
	 * {@code perkara} (42.11.9) — one court case heard by a Hakim Pembantu (rank &ge; 3). Builder {@code LembarPerkara}
	 * (42U-2) from 42C-2's {@code Perkara} (templates 42B-C {@code tempat/perkara/*.json}). Opened from {@code jabatan} or
	 * the court clerk; witnesses are bodies called to the hall. Style {@code batu}.
	 * <pre>
	 * perkara   C   id S, nama T, teks T (summary), jenis S (template id), hari L
	 * pihak     D   the two sides: warga keys of a row + peran_pihak S (PP_PENGGUGAT | PP_TERGUGAT), klaim T, watak S
	 *               (only when known)
	 * saksi     D   warga keys of a row + dipanggil B, hadir B, kesaksian T (after being called; drawn as a bubble)
	 * bukti     D   id S, jenis S (BK_*), nama T, barang BR (BK_BARANG), teks T, rantai D [ nama T, warga I ] (BK_GOSIP:
	 *               the spreader chain traced so far), ditelusuri B
	 * putusan   D   no I (1-3), teks T, akibat T (estimated consequence), bisa B, alasan T
	 * amplop    C   a bribe on the table (sly party): dari T, warga I, nb L (drawn in PESAN_BAHAYA colours); absent none
	 * hasil     C   after the verdict: teks T, kesan D [ nama T, nilai I ]
	 * </pre>
	 * Ops: {@code panggil:WARGA}, {@code telusuri:BUKTI}, {@code putuskan:NO}, {@code amplop_terima},
	 * {@code amplop_tolak}.
	 */
	public static final class Perkara {
		public static final String LAYAR = "perkara";

		public static final String PERKARA = "perkara";
		public static final String PIHAK = "pihak";
		public static final String PERAN_PIHAK = "peran_pihak";
		public static final String KLAIM = "klaim";
		public static final String SAKSI = "saksi";
		public static final String DIPANGGIL = "dipanggil";
		public static final String HADIR = "hadir";
		public static final String KESAKSIAN = "kesaksian";
		public static final String BUKTI = "bukti";
		public static final String RANTAI = "rantai";
		public static final String DITELUSURI = "ditelusuri";
		public static final String PUTUSAN = "putusan";
		public static final String AKIBAT = "akibat";
		public static final String AMPLOP = "amplop";
		public static final String HASIL = "hasil";

		public static final String PP_PENGGUGAT = "penggugat";
		public static final String PP_TERGUGAT = "tergugat";
		/** {@code bukti.jenis} values. */
		public static final String BK_BARANG = "barang";
		public static final String BK_GOSIP = "gosip";
		public static final String BK_CATATAN = "catatan";

		public static final String OP_PANGGIL = "panggil";
		public static final String OP_TELUSURI = "telusuri";
		public static final String OP_PUTUSKAN = "putuskan";
		public static final String OP_AMPLOP_TERIMA = "amplop_terima";
		public static final String OP_AMPLOP_TOLAK = "amplop_tolak";

		private Perkara() {
		}
	}

	/**
	 * {@code pasar_hantu} (42.11.10) — the Ghost Market of tonight, opened at a masked seller ({@code lapak_hantu}
	 * entity). Builder {@code LembarPasarHantu} (42U-2) from 42C-2's {@code PasarHantu} and {@code PasarGelap.dagangan}.
	 * Style {@code kain} + {@link #GELAP}. Town header required.
	 * <pre>
	 * pasar     C   lokasi T ("Bawah Jembatan Batu"), bubar_tick L (ticks to dawn 05:00), topeng B (mask worn), diskon F (0.1),
	 *               razia I 0-100 (tension), sandi B (the player came by the night password)
	 * penjual   C   nama T, bisik T (the seller's whisper line), potret I
	 * daftar    D   tab Lapak: id S, nama T, barang BR, harga L, stok I, keadaan S, langka B, lapak I (stall index)
	 * curian    D   tab Curian: id S, nama T, barang BR, tebus L (1.2 x nilai), hari L (stolen), milikmu B
	 * penadah   D   tab Penadah (what the fence buys from the bag): slot I (inventory 0-35), nama T, barang BR, harga L
	 *               (0.6 x nilai), infami I (+1)
	 * </pre>
	 * Ops: {@code beli:ID:N}, {@code tebus:ID}, {@code jual:SLOT:N}.
	 */
	public static final class PasarHantu {
		public static final String LAYAR = "pasar_hantu";
		public static final String TAB_LAPAK = "lapak";
		public static final String TAB_CURIAN = "curian";
		public static final String TAB_PENADAH = "penadah";
		public static final List<String> TABS = List.of(TAB_LAPAK, TAB_CURIAN, TAB_PENADAH);

		public static final String PASAR = "pasar";
		public static final String LOKASI = KotaLembar.LOKASI;
		public static final String BUBAR_TICK = "bubar_tick";
		public static final String TOPENG = "topeng";
		public static final String DISKON = LembarKunci.DISKON;
		public static final String RAZIA = "razia";
		public static final String SANDI = "sandi";
		public static final String PENJUAL = "penjual";
		public static final String BISIK = "bisik";
		public static final String LAPAK = "lapak";
		public static final String CURIAN = "curian";
		public static final String TEBUS = "tebus";
		public static final String MILIKMU = "milikmu";
		public static final String PENADAH = "penadah";
		public static final String INFAMI = "infami";

		/** The new green accent token of the Ghost Market (42U-1 adds it to {@code TempatWarna}). */
		public static final int WARNA_HIJAU = 0xFF3FA86A;

		public static final String OP_BELI = "beli";
		public static final String OP_TEBUS = "tebus";
		public static final String OP_JUAL = "jual";

		private PasarHantu() {
		}
	}

	/**
	 * {@code guanpu} (42.11.10, 42.9.6) — the dice table of a gambling house ({@code meja_dadu} entity). Builder
	 * {@code LembarGuanpu} (42U-2) from 42D's {@code Guanpu}. Style {@code kain} (green table cloth drawn by the client).
	 * The bamboo cup shakes 20 ticks from {@code lempar} receive, then three dice fall and roll (client physics); the
	 * odds are written honestly.
	 * <pre>
	 * meja      C   tempat T, sisa I (throws left today), batas_hari I (20), min L, maks L (stake limits)
	 * dadu      IA  [3] the last throw (1-6 each); absent before the first throw   jumlah I  their sum
	 * lempar    B   this sheet answers a throw (play the cup animation)
	 * taruhan   D   tab Besar-Kecil: id S (BT_*), nama T, bayar T ("0,85 : 1"), peluang T ("105/216"), pilih B, nb L (staked)
	 * riwayat   D   last 10: dadu IA, jumlah I, hasil S (BT_BESAR | BT_KECIL | BT_KEMBAR), menang B, nb L
	 * guanpu    D   tab Guanpu (prizes on the merchant's table): id S, nama T, barang BR, harga_lempar L (1/8 of the value),
	 *               menang_jika I (15), peluang T ("9,3 %"), sisa I
	 * hasil     C   the last result: menang B, nb L, barang BR, teks T
	 * </pre>
	 * Ops: {@code taruh:BT:NB}, {@code lempar}, {@code lempar_guanpu:ID}.
	 */
	public static final class Guanpu {
		public static final String LAYAR = "guanpu";
		public static final String TAB_BESAR_KECIL = "besar_kecil";
		public static final String TAB_GUANPU = "guanpu";
		public static final List<String> TABS = List.of(TAB_BESAR_KECIL, TAB_GUANPU);

		public static final String MEJA = "meja";
		public static final String SISA = "sisa";
		public static final String BATAS_HARI = "batas_hari";
		public static final String MIN = "min";
		public static final String MAKS = LembarKunci.MAKS;
		public static final String DADU = "dadu";
		public static final String LEMPAR = "lempar";
		public static final String TARUHAN = "taruhan";
		public static final String BAYAR = "bayar";
		public static final String PELUANG = LembarKunci.PELUANG;
		public static final String RIWAYAT = LembarKunci.RIWAYAT;
		public static final String HASIL = "hasil";
		public static final String MENANG = "menang";
		public static final String GUANPU = "guanpu";
		public static final String HARGA_LEMPAR = "harga_lempar";
		public static final String MENANG_JIKA = "menang_jika";

		/** Bet ids; a specific triple is {@code kembar_N} (N 1-6). */
		public static final String BT_BESAR = "besar";
		public static final String BT_KECIL = "kecil";
		public static final String BT_KEMBAR = "kembar";
		/** Cup shake length the client plays before the dice fall. */
		public static final int KOCOK_TICK = 20;

		public static final String OP_TARUH = "taruh";
		public static final String OP_LEMPAR = "lempar";
		public static final String OP_LEMPAR_GUANPU = "lempar_guanpu";

		private Guanpu() {
		}
	}

	/**
	 * {@code kantor_rekrut} (42.11.11) — the player's sect recruitment office in a town. Builder {@code LembarKantor}
	 * (42U-2) from 42E's {@code KantorRekrut} and {@code WargaApi.berbakat}; opened at the office keeper, remotely from the
	 * SEKTE card. Style {@code giok} (own sect). Town header required. Without a hall {@code kunci_layar}; without an
	 * office here the {@code sewa} block replaces the lists.
	 * <pre>
	 * kantor    C   id S, nama T ("Kantor Sekte Awan Ungu · Kota Teratai"), sampai L, sewa L, diterima I, diterima_maks I
	 *               (3 per 7 days), penjaga T, panji B
	 * sewa      C   no office: harga L (per 7 days), bisa B, alasan T, syarat D [ ok B, teks T, punya T ]
	 * pelamar   D   tab Pelamar: warga keys of a row + akar I, akar_pasti B, watak S, kebiasaan D, zhuazhou T, ayah T, ibu T,
	 *               ayah_warga I, akrab_keluarga S, sikap S (SK_*), restu L (blessing money; -1 = needs "Restu Ayah"),
	 *               istimewa B (✦ rooted and chose the sword), saingan T (an NPC sect recruiter wants them), saingan_hari I
	 * ujian     C   tab Ujian (Hari Ujian Terbuka): berikut L (earliest day), biaya L (20), bisa B, alasan T,
	 *               jadwal D [ hari L, teks T ], berlangsung B
	 * statistik C   tab Kantor: pelamar_minggu I, diterima_total I, ditolak I, renown I, gosip D [ teks T ]
	 * </pre>
	 * Ops: {@code sewa}, {@code perpanjang}, {@code uji:WARGA} (Batu Uji, 2 NB), {@code terima:WARGA},
	 * {@code tolak:WARGA}, {@code adakan_ujian}, {@code pilih:WARGA}, {@code tandai:X:Z}.
	 */
	public static final class Kantor {
		public static final String LAYAR = "kantor_rekrut";
		public static final String TAB_PELAMAR = "pelamar";
		public static final String TAB_UJIAN = "ujian";
		public static final String TAB_KANTOR = "kantor";
		public static final List<String> TABS = List.of(TAB_PELAMAR, TAB_UJIAN, TAB_KANTOR);

		public static final String KANTOR = "kantor";
		public static final String SEWA = "sewa";
		public static final String DITERIMA = "diterima";
		public static final String DITERIMA_MAKS = "diterima_maks";
		public static final String PENJAGA = "penjaga";
		public static final String PANJI = "panji";
		public static final String PELAMAR = "pelamar";
		public static final String ZHUAZHOU = "zhuazhou";
		public static final String AYAH = "ayah";
		public static final String IBU = "ibu";
		public static final String AYAH_WARGA = "ayah_warga";
		public static final String AKRAB_KELUARGA = "akrab_keluarga";
		public static final String SIKAP = "sikap";
		public static final String RESTU = "restu";
		public static final String ISTIMEWA = "istimewa";
		public static final String SAINGAN = "saingan";
		public static final String SAINGAN_HARI = "saingan_hari";
		public static final String UJIAN = LembarKunci.UJIAN;
		public static final String BERIKUT = "berikut";
		public static final String JADWAL = "jadwal";
		public static final String BERLANGSUNG = "berlangsung";
		public static final String STATISTIK = "statistik";
		public static final String PELAMAR_MINGGU = "pelamar_minggu";
		public static final String DITERIMA_TOTAL = "diterima_total";
		public static final String DITOLAK = "ditolak";
		public static final String RENOWN = "renown";
		public static final String GOSIP = "gosip";

		/** Family stance {@code sikap} (42.4.9). */
		public static final String SK_BANGGA = "bangga";
		public static final String SK_RAGU = "ragu";
		public static final String SK_MENOLAK = "menolak";

		public static final String OP_SEWA = "sewa";
		public static final String OP_PERPANJANG = "perpanjang";
		public static final String OP_UJI = "uji";
		public static final String OP_TERIMA = "terima";
		public static final String OP_TOLAK = "tolak";
		public static final String OP_ADAKAN_UJIAN = "adakan_ujian";

		private Kantor() {
		}
	}

	/**
	 * {@code pakta} (42.11.12) — protection pact, defence calls, disciples on town duty; hub card {@link #HUB_SEKTE}
	 * ("Sekte di Kota", locked without a hall). Builder {@code LembarPakta} (42U-2) from 42E's {@code PaktaLindung},
	 * {@code SerbuanKota}, {@code TugasKotaMurid}; opened at the town elder, remotely from the hub. Style {@code giok}. Town
	 * header required. Tab Cabang is a jump to {@code cabang} ({@code cabang} op).
	 * <pre>
	 * pakta     C   ada B, sejak L, upeti L (NB per week), barang D [ nama T, barang BR, jumlah I ] (surplus goods),
	 *               upeti_siap B, renown I (per week), penjaga D [ nama T, soul S, ranah I, sisa_hari I ], rotasi I (days),
	 *               gagal I, gagal_maks I (3)
	 * calon     C   no pact yet: pelindung T (current NPC protector), cara D [ id S (CR_*), nama T, bisa B, alasan T ],
	 *               syarat D [ ok B, teks T, punya T ]
	 * bela      C   tab Bela: keadaan S (BL_*), jenis S (BJ_*), sisa_tick L (to answer), kekuatan I (enemy), kekuatan_kita I
	 *               (estimated, sent disciples), terakhir C [ hari L, menang B ], x I, z I
	 * murid     D   tab Murid: soul S, nama T, ranah I, tugas S (TK_*; absent idle), tempat T, bisa B, alasan T
	 * daftar_pakta D  every pact of the player (&le; kotaSekte.paktaMaks): situs L, tempat T, upeti_siap B, bela S (BL_*)
	 * </pre>
	 * Ops: {@code ambil_upeti}, {@code rebut:CR}, {@code bela:BD}, {@code tugaskan:SOUL:TK}, {@code tarik_murid:SOUL},
	 * {@code putus} then {@code putus_ya}, {@code cabang} (opens {@code cabang}), {@code tandai:X:Z}.
	 */
	public static final class Pakta {
		public static final String LAYAR = "pakta";
		public static final String TAB_PAKTA = "pakta";
		public static final String TAB_BELA = "bela";
		public static final String TAB_MURID = "murid";
		public static final String TAB_CABANG = "cabang";
		public static final List<String> TABS = List.of(TAB_PAKTA, TAB_BELA, TAB_MURID, TAB_CABANG);

		public static final String PAKTA = "pakta";
		public static final String ADA = "ada";
		public static final String SEJAK = "sejak";
		public static final String UPETI = "upeti";
		public static final String BARANG_UPETI = "barang";
		public static final String UPETI_SIAP = "upeti_siap";
		public static final String RENOWN = "renown";
		public static final String PENJAGA = "penjaga";
		public static final String ROTASI = "rotasi";
		public static final String GAGAL = "gagal";
		public static final String GAGAL_MAKS = "gagal_maks";
		public static final String CALON = "calon";
		public static final String CARA = "cara";
		public static final String BELA = "bela";
		public static final String KEKUATAN = "kekuatan";
		public static final String KEKUATAN_KITA = "kekuatan_kita";
		public static final String TERAKHIR = "terakhir";
		public static final String MENANG = "menang";
		public static final String MURID = "murid";
		public static final String TUGAS = "tugas";
		public static final String DAFTAR_PAKTA = "daftar_pakta";

		/** Ways to take over a town ({@code cara.id}, 42.8.2). */
		public static final String CR_DIALOG = "dialog";
		public static final String CR_TANDING = "tanding";
		public static final String CR_DIPILIH = "dipilih";
		/** {@code bela.keadaan}. */
		public static final String BL_TENANG = "tenang";
		public static final String BL_PANGGILAN = "panggilan";
		public static final String BL_BERLANGSUNG = "berlangsung";
		/** {@code bela.jenis}: beasts, bandits, demonic cultivators. */
		public static final String BJ_BINATANG = "binatang";
		public static final String BJ_BEGAL = "begal";
		public static final String BJ_IBLIS = "iblis";
		/** {@code bela:BD}: come in person or send disciples. */
		public static final String BD_DATANG = "datang";
		public static final String BD_KIRIM = "kirim";
		/** Disciple town duties (42.8.3). */
		public static final String TK_JAGA_KOTA = "jaga_kota";
		public static final String TK_KERJA_KOTA = "kerja_kota";
		public static final String TK_BELANJA = "belanja";

		public static final String OP_AMBIL_UPETI = "ambil_upeti";
		public static final String OP_REBUT = "rebut";
		public static final String OP_BELA = "bela";
		public static final String OP_TUGASKAN = "tugaskan";
		public static final String OP_TARIK_MURID = "tarik_murid";
		public static final String OP_PUTUS = "putus";
		public static final String OP_PUTUS_YA = "putus_ya";
		public static final String OP_CABANG = "cabang";

		private Pakta() {
		}
	}

	/**
	 * {@code cabang} (42.11.12) — a sect branch in a player-owned building ({@code papan_cabang} block, anchor). Builder
	 * {@code LembarCabang} (42U-2) from 42E's {@code CabangKota}. Style {@code giok}. Town header required.
	 * <pre>
	 * cabang    C   ada B, nama T ("Sekte Awan Ungu · Cabang Kota Teratai"), jenis S (CJ_*), ganti L (day the 7-day change
	 *               lock ends), isi D [ teks T, punya I, perlu I, ok B ] (required player blocks inside), penjaga D [ nama T,
	 *               soul S ], efek D [ teks T ], laporan D [ hari L, teks T, nb L ], renown I (per week), rumah S
	 * jenis     D   the three kinds: id S (CJ_*), nama T, teks T, syarat D [ ok B, teks T ], pilih B
	 * syarat    D   no branch: ok B, teks T, punya T (owned building, hall level &ge; 4, pact or Nama Baik &ge; 60)
	 * </pre>
	 * Ops: {@code jenis:CJ}, {@code tugaskan:SOUL}, {@code tutup} then {@code tutup_ya}, {@code rumah} (opens
	 * {@code rumah} on this building).
	 */
	public static final class Cabang {
		public static final String LAYAR = "cabang";

		public static final String CABANG = "cabang";
		public static final String ADA = "ada";
		public static final String JENIS = LembarKunci.JENIS;
		public static final String GANTI = "ganti";
		public static final String ISI = "isi";
		public static final String PERLU = "perlu";
		public static final String PUNYA = "punya";
		public static final String PENJAGA = "penjaga";
		public static final String EFEK = "efek";
		public static final String LAPORAN = "laporan";
		public static final String RENOWN = "renown";
		public static final String RUMAH = "rumah";

		public static final String CJ_TOKO_PIL = "toko_pil";
		public static final String CJ_BALAI_LATIHAN = "balai_latihan";
		public static final String CJ_KLINIK = "klinik";
		public static final List<String> SEMUA_JENIS = List.of(CJ_TOKO_PIL, CJ_BALAI_LATIHAN, CJ_KLINIK);

		public static final String OP_JENIS = "jenis";
		public static final String OP_TUGASKAN = "tugaskan";
		public static final String OP_TUTUP = "tutup";
		public static final String OP_TUTUP_YA = "tutup_ya";
		public static final String OP_RUMAH = "rumah";

		private Cabang() {
		}
	}

	/**
	 * {@code kavling} (42.11.13) — one growth lot. Builder {@code LembarKavling} (42U-2) from 42F's {@code Kavling},
	 * {@code BahanKota}, {@code Kemakmuran}; opened at the lot sign (anchor) or the foreman (Mandor Gong Shu, 38.R
	 * foremen), remotely from {@code kota_buku}/{@code jabatan}. Style {@code giok}. Town header required.
	 * <pre>
	 * kavling   C   id I, nama T ("Kavling 3 · Pasar Timur"), rencana T ("Rumah Toko (gaya ibu kota pasar)"), rencana_id S,
	 *               gaya S, jenis S, tahap I (0-5), tahap_total I (5), tahap_nama T ("Dinding"), kemajuan I (0-100 in the
	 *               stage), regu I, sisa_hari I (estimate, -1 unknown), keadaan S (KV_*), terhalang T (what blocks it)
	 * siluet    C   turning preview of stage 5 (Pratinjau3D cannot draw structures): lebar I, panjang I, tinggi IA
	 *               (lebar x panjang column heights), warna IA (lebar x panjang top colours ARGB) — &le; 32 x 32
	 * bahan     D   tab Bahan: id S (BH_*), nama T, terkumpul I, perlu I, tas I (matching blocks in the bag), harga L (per unit,
	 *               HargaKota x 1.1), ok B
	 * syarat    D   ok B, teks T, punya T ("Kemakmuran 68 &ge; 55", "Kas Kota cukup", "terhalang: —")
	 * pilihan   D   tab Pilih (office rank &ge; 4): id S (rencana id), nama T, teks T (the need: crowded, jobless, children,
	 *               raids), biaya L, pilih B, bisa B, alasan T
	 * dana      D   tab Dana: nama T, pemain B, anda B, nb L, persen I    dana_total L   biaya_total L
	 * papan_nama B  the player gave &ge; 25 % (name on the finished sign)
	 * </pre>
	 * Ops: {@code jual:BH:N}, {@code sumbang:BH:N}, {@code sumbang_nb:NB}, {@code rencana:ID}, {@code tandai:X:Z}.
	 */
	public static final class Kavling {
		public static final String LAYAR = "kavling";
		public static final String TAB_KAVLING = "kavling";
		public static final String TAB_BAHAN = "bahan";
		public static final String TAB_PILIH = "pilih";
		public static final String TAB_DANA = "dana";
		public static final List<String> TABS = List.of(TAB_KAVLING, TAB_BAHAN, TAB_PILIH, TAB_DANA);

		public static final String KAVLING = "kavling";
		public static final String RENCANA = "rencana";
		public static final String RENCANA_ID = "rencana_id";
		public static final String GAYA_KAVLING = "gaya";
		public static final String JENIS = LembarKunci.JENIS;
		public static final String TAHAP_TOTAL = LembarKunci.TAHAP_TOTAL;
		public static final String TAHAP_NAMA = "tahap_nama";
		public static final String KEMAJUAN = LembarKunci.KEMAJUAN;
		public static final String REGU = "regu";
		public static final String TERHALANG = "terhalang";
		public static final String SILUET = "siluet";
		public static final String TINGGI = "tinggi";
		public static final String BAHAN = "bahan";
		public static final String TERKUMPUL = "terkumpul";
		public static final String PERLU = "perlu";
		public static final String TAS = "tas";
		public static final String PILIHAN = "pilihan";
		public static final String DANA = "dana";
		public static final String PERSEN = "persen";
		public static final String DANA_TOTAL = "dana_total";
		public static final String BIAYA_TOTAL = "biaya_total";
		public static final String PAPAN_NAMA = "papan_nama";

		/** {@code keadaan} of a lot. */
		public static final String KV_KOSONG = "kosong";
		public static final String KV_MENUNGGU = "menunggu";
		public static final String KV_DIBANGUN = "dibangun";
		public static final String KV_SELESAI = "selesai";
		public static final String KV_TERHALANG = "terhalang";
		public static final String KV_TIDAK_LAYAK = "tidak_layak";
		/** Material ids (42.9.4). */
		public static final String BH_KAYU = "kayu";
		public static final String BH_BATU = "batu";
		public static final String BH_GENTENG = "genteng";
		public static final String BH_KACA_KERTAS = "kaca_kertas";
		public static final String BH_KAIN = "kain";
		public static final List<String> SEMUA_BAHAN = List.of(BH_KAYU, BH_BATU, BH_GENTENG, BH_KACA_KERTAS, BH_KAIN);

		public static final String OP_JUAL = "jual";
		public static final String OP_SUMBANG = "sumbang";
		public static final String OP_SUMBANG_NB = "sumbang_nb";
		public static final String OP_RENCANA = "rencana";

		private Kavling() {
		}
	}

	/**
	 * {@code upacara} (42.11.14, 42.7.7) — an invitation card: red 囍 (wedding), white 奠 (funeral), egg red (birth, month
	 * feast, zhuazhou). Builder {@code LembarUpacara} (42U-2) from 42D-P's {@code Upacara}; opened from the dialog of a
	 * family member, the invitation item or the Buku Kota Acara tab. Style {@code kain}, ink by {@code jenis}.
	 * <pre>
	 * undangan  C   id S, jenis S (api/JenisUpacara: UP_*), nama T ("Pernikahan Zhang Shan &amp; Li Ying"), hari L, jam I,
	 *               menit I, tempat T, x I, z I, fase S (UF_*), sisa_tick L (to the start), keluarga D [ nama T, warga I,
	 *               peran T ("ayah pengantin") ], hadir B (the player is there), sudah_beri B
	 * beri      C   gift envelope: jenis S (BE_ANGPAO | BE_DUKA), min L (2 x (b+1)), maks L, saran L, bisa B, alasan T
	 * </pre>
	 * Ops: {@code beri:NB} (PemilihJumlah), {@code tandai:X:Z}.
	 */
	public static final class Upacara {
		public static final String LAYAR = "upacara";

		public static final String UNDANGAN = "undangan";
		public static final String FASE = "fase";
		public static final String KELUARGA = "keluarga";
		public static final String HADIR = "hadir";
		public static final String SUDAH_BERI = "sudah_beri";
		public static final String BERI = "beri";
		public static final String MIN = "min";
		public static final String MAKS = LembarKunci.MAKS;
		public static final String SARAN = "saran";

		public static final String UP_NIKAH = "nikah";
		public static final String UP_MAKAM = "makam";
		public static final String UP_LAHIR = "lahir";
		public static final String UP_SEBULAN = "sebulan";
		public static final String UP_ZHUAZHOU = "zhuazhou";
		/** {@code fase} values in order (a wedding uses all, others a subset). */
		public static final String UF_MENUNGGU = "menunggu";
		public static final String UF_JEMPUT = "jemput";
		public static final String UF_ARAK = "arak";
		public static final String UF_SEMBAH = "sembah";
		public static final String UF_JAMUAN = "jamuan";
		public static final String UF_USUNG = "usung";
		public static final String UF_MAKAM = "makam";
		public static final String UF_PILIH_BENDA = "pilih_benda";
		public static final String UF_SELESAI = "selesai";
		public static final String BE_ANGPAO = "angpao";
		public static final String BE_DUKA = "duka";

		/** Card inks: wedding red, funeral white (text dark), birth egg red. */
		public static final int WARNA_NIKAH = 0xFFB3202A;
		public static final int WARNA_MAKAM = 0xFFEDE8DC;
		public static final int WARNA_LAHIR = 0xFFD9464B;

		public static final String OP_BERI = "beri";

		private Upacara() {
		}
	}

	/**
	 * {@code rombongan} (42.11.14, 42.7.1) — a travelling troupe in town. Builder {@code LembarUpacara} (42U-2, small
	 * screens) from 42D's {@code Rombongan}; opened at the troupe leader. Style {@code kayu}. Town header required.
	 * <pre>
	 * rombongan C   id S (RB_*), nama T, jenis T ("akrobat dan barongsai"), ketua T, tiba L, pergi L, berikut T (next town),
	 *               sesi D [ jam I, menit I, teks T, tempat T, x I, z I, berlangsung B ], tip_min L, tip_maks L
	 * kisah     D   Lao Shuo: id S (story id), nama T, didengar B, hari L
	 * lagu      D   Ah Qin: id S, nama T, didengar B
	 * </pre>
	 * Ops: {@code tip:NB}, {@code minta:LAGU}, {@code tandai:X:Z}.
	 */
	public static final class Rombongan {
		public static final String LAYAR = "rombongan";

		public static final String ROMBONGAN = "rombongan";
		public static final String KETUA = "ketua";
		public static final String TIBA = "tiba";
		public static final String PERGI = "pergi";
		public static final String BERIKUT = "berikut";
		public static final String SESI = "sesi";
		public static final String BERLANGSUNG = "berlangsung";
		public static final String TIP_MIN = "tip_min";
		public static final String TIP_MAKS = "tip_maks";
		public static final String KISAH = "kisah";
		public static final String LAGU = "lagu";
		public static final String DIDENGAR = "didengar";

		/** The six troupes of 42.7.1. */
		public static final String RB_BULAN_SABIT = "bulan_sabit";
		public static final String RB_LAO_SHUO = "lao_shuo";
		public static final String RB_AH_QIN = "ah_qin";
		public static final String RB_HU_LU = "hu_lu";
		public static final String RB_MA_FU = "ma_fu";
		public static final String RB_SUONA = "suona";

		public static final String OP_TIP = "tip";
		public static final String OP_MINTA = "minta";

		private Rombongan() {
		}
	}

	/**
	 * {@code lapak_sewa} (42.11.14, 42.6.2) — renting a town market stall ({@code lapak_sewa_N} titik, tent entity
	 * {@code tenda_lapak}); pattern of the 38.U.3.6 Lapak tab. Builder {@code LembarToko} (42U-1); opened at the market
	 * overseer or the tent. Style {@code kayu}. Town header required.
	 * <pre>
	 * daftar    D   id S (titik id), nama T ("Lapak 3 · Pasar Timur"), distrik S (LembarKunci DISTRIK_*), harga L (per week,
	 *               6 x (b+1)), keadaan S, penyewa T, anda B, sampai L, x I, z I
	 * </pre>
	 * Ops: {@code sewa:ID:MINGGU}, {@code perpanjang:ID:MINGGU}, {@code lepas:ID}, {@code toko:ID} (opens
	 * {@code toko_pemain} of that stall), {@code tandai:X:Z}.
	 */
	public static final class LapakSewa {
		public static final String LAYAR = "lapak_sewa";

		public static final String DISTRIK = LembarKunci.DISTRIK;
		public static final String PENYEWA = "penyewa";

		public static final String OP_SEWA = "sewa";
		public static final String OP_PERPANJANG = "perpanjang";
		public static final String OP_LEPAS = "lepas";
		public static final String OP_TOKO = "toko";

		private LapakSewa() {
		}
	}

	/**
	 * HUD {@code kejar} (42.11.14, 42.7.4; not a screen): payload {@code kultivator:kota_kejar} (KotaPayloads, 42A0)
	 * carries the pickpocket's entity id and the ticks left; the client HUD (42U-1) draws an arrow to that entity at the
	 * screen edge, a 30 s countdown ring and the distance in blocks; {@code sisa} 0 or an unknown entity hides it.
	 */
	public static final class HudKejar {
		public static final String ID = "kota_kejar";
		/** A chase lasts 30 s. */
		public static final int DURASI_TICK = 600;
		/** Below this distance the arrow turns into a "pukul!" prompt. */
		public static final int DEKAT_BLOK = 3;

		private HudKejar() {
		}
	}

	/**
	 * HUD {@code irama} (42.11.9, 42.7.3, 42.10; not a screen): a 5-beat ring with strike markers for the night watch,
	 * teaching Xiao Bang and the shadow theatre gong. Payload {@code kultivator:kota_irama} (KotaPayloads, 42A0) carries a
	 * {@code byte[]} of at most {@link #DATA_MAKS} bytes laid out below; unsigned ({@code b & 0xFF}), a byte past the end
	 * reads 0. Build with {@link #data}, read with {@link #baca}.
	 * <pre>
	 * S2C  [0] jenis (J_*; 0 hides)  [1] current beat 0-4  [2] pattern bits (bit i: beat i must be struck)
	 *      [3] ticks per beat (10-40)  [4] round 1-n  [5] rounds total  [6] beats struck on time this round (bits)
	 *      [7] misses so far  [8] start tick of the round, low byte (client sync)
	 * C2S  [0] jenis  [1] beat index struck 0-4  [2] offset in ticks + 128 (early &lt; 128 &lt; late)
	 * </pre>
	 */
	public static final class HudIrama {
		public static final String ID = "kota_irama";
		public static final int KETUKAN = 5;
		public static final int DATA_MAKS = 16;

		public static final int J_KOSONG = 0;
		/** Ronda: the n-th geng pattern (n gong strikes + clapper roll). */
		public static final int J_RONDA = 1;
		/** "Lentera Terakhir Lao Geng" stage 2: teach Xiao Bang the patterns. */
		public static final int J_AJAR = 2;
		/** Shadow theatre premiere ("Malam Wayang" stage 4): the player beats the gong. */
		public static final int J_WAYANG = 3;

		/** {@code data[0] = jenis}, then each value clamped to 0-255 (at most {@link #DATA_MAKS} bytes in all). */
		public static byte[] data(int jenis, int... nilai) {
			int n = Math.min(nilai == null ? 0 : nilai.length, DATA_MAKS - 1);
			byte[] out = new byte[1 + n];
			out[0] = (byte) Math.max(0, Math.min(255, jenis));
			for (int i = 0; i < n; i++) {
				out[1 + i] = (byte) Math.max(0, Math.min(255, nilai[i]));
			}
			return out;
		}

		/** Unsigned byte {@code i} of {@code data} (0 past the end). */
		public static int baca(byte[] data, int i) {
			return data == null || i < 0 || i >= data.length ? 0 : data[i] & 0xFF;
		}

		private HudIrama() {
		}
	}

	private KotaLembar() {
	}
}
