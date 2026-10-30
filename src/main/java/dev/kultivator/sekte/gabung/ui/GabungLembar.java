package dev.kultivator.sekte.gabung.ui;

import dev.kultivator.dunia.tempat.ui.LembarKunci;

import java.util.List;

/**
 * The sheet contract of every DESIGN 41 screen (41.10, contract C41.U1; frozen at the end of wave 41-G0): screen ids, tab
 * ids, keys, value meanings and op verbs. Server builders (41U-1/41U-2 {@code ui/Lembar*}, filled from the APIs of
 * 41A-41D) and client screens ({@code client/sekte/gabung/ui/**}, drawn with the 38.U kit, read-only) compile against this
 * class only. It follows {@link LembarKunci} exactly: same value types (T text, S id, I int, L long, F float, B boolean,
 * IA/LA arrays, C compound, D list of compounds, SL string list, BR item), same header keys ({@code judul},
 * {@code gaya}, {@code tab}, {@code tab_kunci}, {@code tab_sembunyi}, {@code npc}, {@code pesan}, {@code kosong},
 * {@code kunci_layar}), same entry keys ({@code id}, {@code nama}, {@code barang}, {@code harga}, {@code stok},
 * {@code keadaan} + {@code K_*}, {@code terkunci}/{@code alasan}, {@code x}/{@code z}, {@code anda}) and the same op form
 * {@code verb:arg:arg} ({@link LembarKunci#op}). Keys are only ever added, never renamed; additions are requested in
 * {@code docs/progres/m2-41U-1.md}.
 * <p>
 * <b>PK is never NB</b> (41.0 butir 4): every price of a 41 screen is {@code int} PK under {@link LembarKunci#PK} /
 * {@link LembarKunci#HARGA} with the PK seal; NB appears only in {@link LembarKunci#UANG}.
 *
 * <h2>Sect header (every 41 screen that shows one sect)</h2>
 * <pre>
 * sekte_id     S   "s3"                              nama_sekte  T   "Sekte Pedang Awan Ungu"
 * warna        I   title ink (DiplomasiService.color) lambang     I   packed Lambang (LambangService)
 * aliran       I   0 Lurus, 1 Netral, 2 Iblis        kelas       I   KelasSekte nomor 1-5
 * tingkat      I   tier 1-9                          corak       S   Corak id ("pedang")
 * langit       B   owns a cloud palace (icon ☁)      merosot     B   status MEROSOT (41.4.7)
 * pangkat_anggota I  the player's Pangkat ordinal there, -1 not a member
 * pk           I   the player's spendable PK         pk_total    I   PK ever earned (ranking)
 * </pre>
 * Panel style by alignment ({@link GabungLayar#gaya(int)}): Lurus {@code giok}, Netral {@code kayu}, Iblis {@code batu}
 * (the client tints it {@code #6A1420}).
 */
public final class GabungLembar {

	// ---------------------------------------------------------------------------------------------------------------
	// Sect header keys

	public static final String SEKTE_ID = "sekte_id";
	public static final String NAMA_SEKTE = "nama_sekte";
	public static final String WARNA = "warna";
	public static final String LAMBANG = "lambang";
	public static final String ALIRAN = "aliran";
	public static final String KELAS = "kelas";
	public static final String TINGKAT = "tingkat";
	public static final String CORAK = "corak";
	public static final String LANGIT = "langit";
	public static final String MEROSOT = "merosot";
	public static final String PANGKAT_ANGGOTA = "pangkat_anggota";
	public static final String PK_TOTAL = "pk_total";

	// Shared keys of several 41 screens
	/** I a Pangkat ordinal. */
	public static final String PANGKAT = "pangkat";
	/** I a Realm ordinal. */
	public static final String RANAH = "ranah";
	/** S a Dao id. */
	public static final String DAO = "dao";
	/** T an office title ("Tetua Hukum"). */
	public static final String JABATAN = "jabatan";
	/** T a character's temper ("keras, aturan adalah aturan"). */
	public static final String WATAK = "watak";
	/** I an attitude or standing -100..100. */
	public static final String SIKAP = "sikap";
	/** T the attitude band name ("Ramah"). */
	public static final String SIKAP_NAMA = "sikap_nama";
	/** S tokoh id ({@code s3#t2}, {@code s3#k}, roster {@code s3#4}). */
	public static final String TOKOH = "tokoh";
	/** B the row is a player (else a named character). */
	public static final String PEMAIN = "pemain";
	/** I the Teacher bond 0-100. */
	public static final String IKATAN = "ikatan";
	/** I entity id whose head the kit renders live (-1 none). */
	public static final String POTRET = "potret";
	/** S door id ({@code api/Pintu}). */
	public static final String PINTU = "pintu";
	/** L world day of the sect's next test day (-1 unknown). */
	public static final String HARI_UJIAN = "hari_ujian";
	/** T what the player has against a condition ("punya: Fondasi Tengah"). */
	public static final String PUNYA = "punya";
	/** T an advice line next to a failed condition ("pakai Token"). */
	public static final String SARAN = "saran";

	/** Hub cards (41.10 "Hub"): section SEKTE "Perguruanku", section DUNIA "Direktori Sekte". */
	public static final String HUB_SEKTEKU = "gabung_sekteku";
	public static final String HUB_DIREKTORI = "gabung_direktori";

	/** Every screen id of 41, in 41.10 order. */
	public static final List<String> SEMUA_LAYAR = List.of(
			"gabung_direktori", "gabung_lamaran", "gabung_kartu", "gabung_paviliun", "gabung_papan", "gabung_pangkat",
			"gabung_kediaman", "gabung_politik", "gabung_shifu", "gabung_putusan", "gabung_hukum");

	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * {@code gabung_direktori} (41.10.1) — the sect directory; hub card {@link #HUB_DIREKTORI}, recruiters ("Tanya tentang
	 * sekte"), the {@code gabung} command. Builder {@code LembarDirektori} (41U-1). Style by the player's own sect, else
	 * {@code kertas}. Only sects in {@code Keanggotaan.dikenal} are shown in full; the others are "?" cards.
	 * <pre>
	 * daftar    D   one per sect: id S (sect id), dikenal B, petunjuk T (unknown: "Dengar kabar di Kedai Teh"),
	 *               nama T, peringkat I (1-based), kelas I, tingkat I, aliran I, corak S, warna I, lambang I,
	 *               jarak I (blocks, -1 unknown), arah I (8 sectors 0 = N clockwise, -1 unknown), langit B, merosot B,
	 *               ketua T, ranah_ketua I, sikap I, sikap_nama T, syarat D [ ok B, teks T ] (best door),
	 *               pintu S (best door), hari_ujian L, x I, z I (absent: place unknown), anggota B (the player's sect)
	 * peringkat D   tab Peringkat: id S, nama T, kekuatan I, tren I (-1 down, 0, 1 up over 7 days), kelas I, anda B
	 * sekteku   C   tab Sekteku (members only): pangkat I, pk I, pk_total I,
	 *               papan_jasa D [ nama T, pk_total I, pemain B, anda B ] (this month, players and roster)
	 * jumlah    I   sects in the world           dikenal_jumlah I  sects the player knows
	 * </pre>
	 * Ops: {@code lamar:SEKTE} (opens {@code gabung_lamaran}), {@code kartu} (opens {@code gabung_kartu}), {@code tandai:X:Z}.
	 * Search ({@code /}) and sort (peringkat, jarak, kelas) are client side.
	 */
	public static final class Direktori {
		public static final String LAYAR = "gabung_direktori";
		public static final String TAB_SEMUA = "semua";
		public static final String TAB_LURUS = "lurus";
		public static final String TAB_NETRAL = "netral";
		public static final String TAB_IBLIS = "iblis";
		public static final String TAB_PERINGKAT = "peringkat";
		public static final String TAB_SEKTEKU = "sekteku";
		public static final List<String> TABS = List.of(TAB_SEMUA, TAB_LURUS, TAB_NETRAL, TAB_IBLIS, TAB_PERINGKAT, TAB_SEKTEKU);

		public static final String DIKENAL = "dikenal";
		public static final String PETUNJUK = "petunjuk";
		public static final String PERINGKAT = "peringkat";
		public static final String ARAH = "arah";
		public static final String KETUA = "ketua";
		public static final String RANAH_KETUA = "ranah_ketua";
		public static final String ANGGOTA = "anggota";
		public static final String KEKUATAN = "kekuatan";
		public static final String TREN = "tren";
		public static final String SEKTEKU = "sekteku";
		public static final String PAPAN_JASA = "papan_jasa";
		public static final String DIKENAL_JUMLAH = "dikenal_jumlah";

		public static final String OP_LAMAR = "lamar";
		public static final String OP_KARTU = "kartu";

		/** Added by 41U-1: S the sect row selected on open ({@code /kultivasi gabung info <sekte>}, a recruiter's question). */
		public static final String PILIH = "pilih";
		/** Added by 41U-1: B in a {@code daftar} row, the player's open application is for this sect. */
		public static final String CALON = "calon";
		/** Added by 41U-1: T in a {@code daftar} row, the class title of the world ("Tanah Suci"). */
		public static final String GELAR_KELAS = "gelar_kelas";

		private Direktori() {
		}
	}

	/**
	 * {@code gabung_lamaran} (41.10.2) — the application of one sect. Builder {@code LembarLamaran} (41U-1) with
	 * {@code SyaratMasuk.periksa}; opened by {@code masuk/Lamaran.buka}. Sect header required. Tabs = the five doors; a door
	 * that cannot be taken is listed in {@code tab_kunci} with its reason (KeadaanTerkunci).
	 * <pre>
	 * pintu     D   id S (door id = tab id), nama T, bisa B, alasan T, tahap SL (stage ids TAHAP_*), pangkat I (best rank
	 *               the door can give), syarat D [ ok B, teks T, punya T, saran T ]
	 * token     D   tab Token: nama T, barang BR, sah B (belongs to this sect or is general and the class &le; 3), alasan T
	 * hari_ujian L  next test day            sisa_hari I  days until it
	 * jeda      L   world day the 3-day pause after a failed try ends (absent: none)
	 * calon     C   the running candidacy (absent: none): nama_sekte T, pintu S, sampai L (world day), sini B (this sect)
	 * </pre>
	 * Ops: {@code ajukan:PINTU}, {@code batal} (withdraw the running candidacy).
	 */
	public static final class Lamaran {
		public static final String LAYAR = "gabung_lamaran";
		public static final String TAB_GERBANG = "gerbang";
		public static final String TAB_TOKEN = "token";
		public static final String TAB_UNDANGAN = "undangan";
		public static final String TAB_PELAYAN = "pelayan";
		public static final String TAB_TAMU = "tamu";
		public static final List<String> TABS = List.of(TAB_GERBANG, TAB_TOKEN, TAB_UNDANGAN, TAB_PELAYAN, TAB_TAMU);

		/** Stage ids of {@code tahap}: Tangga Ujian, corak test, Ujian Hati, the servant's one-question talk, the guest trial. */
		public static final String TAHAP_TANGGA = "tangga";
		public static final String TAHAP_CORAK = "corak";
		public static final String TAHAP_HATI = "hati";
		public static final String TAHAP_WAWANCARA = "wawancara";
		public static final String TAHAP_TAMU = "tamu";

		public static final String PINTU_DAFTAR = "pintu";
		public static final String TOKEN = "token";
		public static final String SAH = "sah";
		public static final String JEDA = "jeda";
		public static final String CALON = "calon";
		public static final String SAMPAI = "sampai";
		public static final String SINI = "sini";

		public static final String OP_AJUKAN = "ajukan";
		public static final String OP_BATAL = "batal";

		private Lamaran() {
		}
	}

	/**
	 * {@code gabung_kartu} (41.10.3) — the membership card; hub card {@link #HUB_SEKTEKU}. Builder {@code LembarKartu}
	 * (41U-1). Sect header required. Tab Hukum shows the {@code gabung_hukum} parts when a hearing is due.
	 * <pre>
	 * kartu     C   potret I (the player), nama T, pangkat I, warna I (rank edge, SectRanks.TRIM family), gelar T (title
	 *               prefix, "Juara Turnamen Murid"), hari_masuk L, hari_anggota I, pk I, pk_total I, peringkat I (Papan
	 *               Jasa place), shifu C [ nama T, tokoh S, jabatan T, ikatan I ], kakak C [ nama T, tokoh S ],
	 *               saingan C [ nama T, tokoh S, sikap I ], pelanggaran I (0-100), tugas I, tugas_maks I, jatah B (ready),
	 *               sifat D [ teks T ] (candidate traits "Teguh", "Licik"...), samaran T (MENYAMAR name, absent otherwise),
	 *               berikut C [ pangkat I, nama T, syarat D [ ok B, teks T, punya T ] ]
	 * shifu     C   tab Shifu: tokoh S, potret I, nama T, jabatan T, ranah I, dao S, watak T, ikatan I,
	 *               pelajaran L (world day of the next lesson), misi D [ id S, nama T, teks T ], hadiah B (a gift allowed
	 *               today), ganti C [ biaya I, bisa B, alasan T ]
	 * hukum     C   tab Hukum: pelanggaran I, catatan D [ hari L, teks T, poin I ], kurungan L (sisa_tick of a running
	 *               confinement, absent none), sidang B (a hearing is due: op hukum opens gabung_hukum)
	 * murid     C   tab Murid: maks I (1 + rank/2), biaya I (PK each), daftar D [ id S (soul), nama T, ranah I,
	 *               terdaftar B, bisa B, alasan T ]
	 * keluar    C   tab Keluar: syarat D [ ok B, teks T ] (Pamit), bisa B, restu B (a blessing will be given),
	 *               peringatan T (Membelot warning, drawn in PESAN_BAHAYA colours)
	 * </pre>
	 * Ops: {@code jatah}, {@code pangkat} / {@code papan} / {@code paviliun} / {@code kediaman} / {@code politik} (open those
	 * screens), {@code hadiah}, {@code ganti_shifu}, {@code hukum}, {@code daftar_murid:SOUL}, {@code lepas_murid:SOUL},
	 * {@code pamit}, {@code pamit_paksa}, {@code belot}.
	 */
	public static final class Kartu {
		public static final String LAYAR = "gabung_kartu";
		public static final String TAB_KARTU = "kartu";
		public static final String TAB_SHIFU = "shifu";
		public static final String TAB_HUKUM = "hukum";
		public static final String TAB_MURID = "murid";
		public static final String TAB_KELUAR = "keluar";
		public static final List<String> TABS = List.of(TAB_KARTU, TAB_SHIFU, TAB_HUKUM, TAB_MURID, TAB_KELUAR);

		public static final String KARTU = "kartu";
		public static final String GELAR = "gelar";
		public static final String HARI_MASUK = "hari_masuk";
		public static final String HARI_ANGGOTA = "hari_anggota";
		public static final String PERINGKAT = "peringkat";
		public static final String SHIFU = "shifu";
		public static final String KAKAK = "kakak";
		public static final String SAINGAN = "saingan";
		public static final String PELANGGARAN = "pelanggaran";
		/** World days left of the week after joining in which the rules read at the gate halve the offence points (0 = none). */
		public static final String ATURAN = "aturan";
		public static final String TUGAS = "tugas";
		public static final String TUGAS_MAKS = "tugas_maks";
		public static final String JATAH = "jatah";
		public static final String SIFAT = "sifat";
		public static final String SAMARAN = "samaran";
		public static final String BERIKUT = "berikut";
		public static final String PELAJARAN = "pelajaran";
		public static final String MISI = "misi";
		public static final String HADIAH = "hadiah";
		public static final String GANTI = "ganti";
		public static final String HUKUM = "hukum";
		public static final String KURUNGAN = "kurungan";
		public static final String SIDANG = "sidang";
		public static final String MURID = "murid";
		public static final String TERDAFTAR = "terdaftar";
		public static final String KELUAR = "keluar";
		public static final String RESTU = "restu";
		public static final String PERINGATAN = "peringatan";
		/** I points of one {@code hukum.catatan} row (named in the schema above). */
		public static final String POIN = "poin";

		public static final String OP_JATAH = "jatah";
		public static final String OP_PANGKAT = "pangkat";
		public static final String OP_PAPAN = "papan";
		public static final String OP_PAVILIUN = "paviliun";
		public static final String OP_KEDIAMAN = "kediaman";
		public static final String OP_POLITIK = "politik";
		public static final String OP_HADIAH = "hadiah";
		public static final String OP_GANTI_SHIFU = "ganti_shifu";
		public static final String OP_HUKUM = "hukum";
		public static final String OP_DAFTAR_MURID = "daftar_murid";
		public static final String OP_LEPAS_MURID = "lepas_murid";
		public static final String OP_PAMIT = "pamit";
		public static final String OP_PAMIT_PAKSA = "pamit_paksa";
		public static final String OP_BELOT = "belot";

		private Kartu() {
		}
	}

	/**
	 * {@code gabung_paviliun} (41.10.4) — the Paviliun Jasa; built by {@code hidup/PaviliunJasa.lembar} (41C) and drawn by
	 * 41U-2. Sect header required, {@code npc} = the Pavilion Elder. Prices are PK ({@code harga} I), the rank factor shown
	 * as a seal. Tabs without goods go in {@code tab_sembunyi}.
	 * <pre>
	 * pengali   F   the player's rank factor (0.7-1.0)
	 * daftar    D   id S, tab S (TAB_*), nama T, barang BR, harga I (PK after factors), harga_dasar I, stok I (-1 unlimited,
	 *               weekly stock), mutu I, keadaan S, alasan T (rank, class, stock), corak B (corak Dao -20 %), dao S,
	 *               warisan B (Teknik Warisan)
	 * pesanan   D   tab Pesanan: id S, nama T, barang BR, jumlah I, punya I (in the bag), pk I (reward)
	 * ceramah   D   tab Ceramah: id S (soul of a registered disciple), nama T, bisa B, alasan T; harga I
	 * </pre>
	 * Ops: {@code tukar:ID:N}, {@code serahkan:ID} (hand in an order from the bag), {@code ceramah:SOUL}. Tabs Kediaman and
	 * Kuota open {@code gabung_kediaman} / list the realm keys of {@code hidup/KuotaAlam} with tab {@code kuota}.
	 */
	public static final class Paviliun {
		public static final String LAYAR = "gabung_paviliun";
		public static final String TAB_TEKNIK = "teknik";
		public static final String TAB_WARISAN = "warisan";
		public static final String TAB_PIL = "pil";
		public static final String TAB_PUSAKA = "pusaka";
		public static final String TAB_BAHAN = "bahan";
		public static final String TAB_KUOTA = "kuota";
		public static final String TAB_KEDIAMAN = "kediaman";
		public static final String TAB_PESANAN = "pesanan";
		public static final String TAB_CERAMAH = "ceramah";
		public static final List<String> TABS = List.of(TAB_TEKNIK, TAB_WARISAN, TAB_PIL, TAB_PUSAKA, TAB_BAHAN, TAB_KUOTA,
				TAB_KEDIAMAN, TAB_PESANAN, TAB_CERAMAH);

		public static final String PENGALI = "pengali";
		public static final String TAB_ENTRI = "tab";
		public static final String HARGA_DASAR = "harga_dasar";
		public static final String CORAK_DISKON = "corak";
		public static final String WARISAN = "warisan";
		public static final String PESANAN = "pesanan";
		public static final String CERAMAH = "ceramah";

		public static final String OP_TUKAR = "tukar";
		public static final String OP_SERAHKAN = "serahkan";
		public static final String OP_CERAMAH = "ceramah";

		private Paviliun() {
		}
	}

	/**
	 * {@code gabung_papan} (41.10.5) — the sect mission board at {@code kompleks/papan_misi}. Builder {@code LembarPapan}
	 * (41U-2) from 41C's {@code PapanMisiSekte}/{@code TugasHarian} and 41D's war. Sect header required.
	 * <pre>
	 * misi_hari I   missions taken today      misi_maks I  daily limit (3, +1 Dalam, +1 Inti)
	 * perang    C   absent without war: lawan T, lawan_id S, skor I (our side), sisa_hari I, wajib B (Dalam+ must join)
	 * daftar    D   id S, tab S (TAB_PAPAN | TAB_AKTIF | TAB_PERANG | TAB_KELOMPOK), mutu S (mission rank "E".."S", or
	 *               "perang"), jenis S (mission kind id), nama T, teks T (2-3 lines of flavour), pk I (reward), skor I (war
	 *               score), keadaan S, alasan T, x I, z I, sisa_tick L (running), anggota D [ nama T, anda B ]
	 *               (group missions), anggota_maks I
	 * tugas     D   tab Tugas: id S, nama T, teks T, pk I, kemajuan I, maks I, selesai B, corak B (corak bonus), x I, z I
	 * turnamen  C   tab Turnamen (read-only, Sekte › Kegiatan › Arena of a member): hari L (next tournament day), berjalan B,
	 *               juara T, juara_hari L, terbaik I ({@code TurnamenMurid.lembar})
	 * </pre>
	 * Ops: {@code terima:ID}, {@code gabung:ID} (join a group mission within 32 blocks), {@code batal:ID}, {@code tugas:ID}
	 * (start a duty), {@code tandai:X:Z}.
	 */
	public static final class Papan {
		public static final String LAYAR = "gabung_papan";
		public static final String TAB_PAPAN = "papan";
		public static final String TAB_AKTIF = "aktif";
		public static final String TAB_PERANG = "perang";
		public static final String TAB_KELOMPOK = "kelompok";
		public static final String TAB_TUGAS = "tugas";
		public static final String TAB_TURNAMEN = "turnamen";
		public static final List<String> TABS = List.of(TAB_PAPAN, TAB_AKTIF, TAB_PERANG, TAB_KELOMPOK, TAB_TUGAS, TAB_TURNAMEN);

		public static final String MISI_HARI = "misi_hari";
		public static final String MISI_MAKS = "misi_maks";
		public static final String PERANG = "perang";
		public static final String LAWAN = "lawan";
		public static final String LAWAN_ID = "lawan_id";
		public static final String WAJIB = "wajib";
		public static final String TAB_ENTRI = "tab";
		public static final String MUTU_MISI = "mutu";
		public static final String ANGGOTA = "anggota";
		public static final String ANGGOTA_MAKS = "anggota_maks";
		public static final String TUGAS = "tugas";
		public static final String SELESAI = "selesai";
		public static final String CORAK_BONUS = "corak";
		public static final String TURNAMEN = "turnamen";
		public static final String HARI_TURNAMEN = "hari";
		public static final String BERJALAN = "berjalan";
		public static final String JUARA = "juara";
		public static final String JUARA_HARI = "juara_hari";
		public static final String TERBAIK = "terbaik";

		public static final String OP_TERIMA = "terima";
		public static final String OP_GABUNG = "gabung";
		public static final String OP_BATAL = "batal";
		public static final String OP_TUGAS = "tugas";

		private Papan() {
		}
	}

	/**
	 * {@code gabung_pangkat} (41.10.6) — the rank tree. Builder {@code LembarPangkat} (41U-1) from {@code api/Pangkat} and
	 * {@code politik/Kursi}. Sect header required. No tabs. Nodes are drawn as jade seals in the {@code SectRanks.TRIM}
	 * colours; locked nodes at 45 % ink; seat holders on hover.
	 * <pre>
	 * simpul    D   one per Pangkat (ordinal order): pangkat I, id S, nama T, warna I, jalur S (JALUR_*), keadaan S
	 *               (K_LEWAT | K_KINI | K_TERBUKA | K_TERKUNCI), kursi I (taken), kursi_maks I (0 = unlimited),
	 *               pemegang D [ nama T, tokoh S, pemain B, anda B ]
	 * kini      I   the player's rank          pilih   I  the rank detailed below (next one by default)
	 * syarat    D   ok B, teks T, punya T       hak  D  teks T (rights of the detailed rank)
	 * biaya     I   PK cost (times kf)         ringkas T  "Kursi Inti 5/6 · Pewaris: Lin Shuangjian · Tetua 5/6 · Tamu 1/2"
	 * bisa      B   may apply now + alasan T
	 * tantang   D   challengeable seat holders of the detailed rank: id S (holder id), nama T, sikap I, bisa B, alasan T
	 * </pre>
	 * Ops: {@code ajukan:PANGKAT} (id), {@code tantang:PEMEGANG}, {@code pilih:PANGKAT} (read-only: detail another node).
	 */
	public static final class PohonPangkat {
		public static final String LAYAR = "gabung_pangkat";

		public static final String SIMPUL = "simpul";
		public static final String JALUR = "jalur";
		public static final String KURSI = "kursi";
		public static final String KURSI_MAKS = "kursi_maks";
		public static final String PEMEGANG = "pemegang";
		public static final String KINI = "kini";
		public static final String PILIH = "pilih";
		public static final String HAK = "hak";
		public static final String RINGKAS = "ringkas";
		public static final String TANTANG = "tantang";

		/** {@code jalur} values: the disciple line, the elder line, the side line of the guest elder. */
		public static final String JALUR_MURID = "murid";
		public static final String JALUR_TETUA = "tetua";
		public static final String JALUR_SAMPING = "samping";

		/** {@code keadaan} values of a node. */
		public static final String K_LEWAT = "lewat";
		public static final String K_KINI = "kini";
		public static final String K_TERBUKA = "terbuka";
		public static final String K_TERKUNCI = "terkunci";

		public static final String OP_AJUKAN = "ajukan";
		public static final String OP_TANTANG = "tantang";
		public static final String OP_PILIH = "pilih";

		private PohonPangkat() {
		}
	}

	/**
	 * {@code gabung_kediaman} (41.10.7) — cave dwellings and spirit pools. Builder {@code LembarKediaman} (41U-2) from 41C-K's
	 * {@code hidup/Kediaman}. Sect header required.
	 * <pre>
	 * hunian    C   the rented dwelling (absent: dormitory only): jenis S (Kediaman.Jenis id), nama T ("Gua Kecil #3"),
	 *               nomor I, sampai L (world day the rent is paid to), faktor F, jimat B (Jimat Pulang target),
	 *               peti D [ slot I, nama T, barang BR ] (9 slots), sewa I (PK to extend)
	 * daftar    D   tab Pilih: id S (titik), jenis S, nama T, nomor I, pangkat I (lowest rank), harga I (PK / 7 days),
	 *               faktor F, keadaan S, pemakai T (who lives there), alasan T
	 * kolam     D   tab Kolam: id S (titik), nama T, terisi B, pemakai T, harga I, gratis B (Inti+ free session left)
	 * </pre>
	 * Ops: {@code perpanjang}, {@code pindah:TITIK}, {@code lepas}, {@code sewa:TITIK}, {@code sesi:TITIK},
	 * {@code peti_ambil:SLOT}, {@code peti_taruh} (the item in hand).
	 */
	public static final class Kediaman {
		public static final String LAYAR = "gabung_kediaman";
		public static final String TAB_GUA = "gua";
		public static final String TAB_PILIH = "pilih";
		public static final String TAB_KOLAM = "kolam";
		public static final List<String> TABS = List.of(TAB_GUA, TAB_PILIH, TAB_KOLAM);

		public static final String HUNIAN = "hunian";
		public static final String NOMOR = "nomor";
		public static final String SAMPAI = "sampai";
		public static final String FAKTOR = "faktor";
		public static final String JIMAT = "jimat";
		public static final String PETI = "peti";
		public static final String SEWA = "sewa";
		public static final String PEMAKAI = "pemakai";
		public static final String KOLAM = "kolam";
		public static final String TERISI = "terisi";
		public static final String GRATIS = "gratis";

		public static final String OP_PERPANJANG = "perpanjang";
		public static final String OP_PINDAH = "pindah";
		public static final String OP_LEPAS = "lepas";
		public static final String OP_SEWA = "sewa";
		public static final String OP_SESI = "sesi";
		public static final String OP_PETI_AMBIL = "peti_ambil";
		public static final String OP_PETI_TARUH = "peti_taruh";

		private Kediaman() {
		}
	}

	/**
	 * {@code gabung_politik} (41.10.8) — the council, succession, war, treasury and branches. Builder {@code LembarPolitik}
	 * (41U-2) from 41D. Sect header required. Votes stay secret until the result ({@code rahasia}).
	 * <pre>
	 * majelis   C   ketua C, wakil C, pewaris C (each: nama T, tokoh S, ranah I, pemain B, anda B; absent = vacant),
	 *               tetua D [ nama T, tokoh S, jabatan T, sikap I (toward the player), pemain B, anda B ],
	 *               sumber I, naik I (sumber needed for the next tier), peringkat I
	 * suksesi   C   bab I (Suksesi.BAB_*), tahap D [ teks T, selesai B ], rahasia B,
	 *               suara D [ nama T, pilihan T ] (shown when rahasia is false)
	 * perang    C   absent without war: lawan T, lawan_id S, skor I, sisa_hari I, misi D [ id S, nama T, selesai B ],
	 *               garis IA [x1, z1, x2, z2] (gate to gate), ikut B (opted in to Duel Juara against players)
	 * kas       C   sumbangan D [ nama T, jumlah I, anda B ] (this month), grafik IA (7 days of sumber),
	 *               kas_ketua I (PK of the master's weekly share, masters only)
	 * cabang    C   daftar D [ nama T, pemilik T, upeti I ], serap B (may absorb), lebur B (may merge), terserap B
	 * arah      S   the sect's course set by a player master (ARAH_*), absent for others
	 * </pre>
	 * Ops: {@code suara:ID}, {@code usul:ID} (propose an elder), {@code sumbang} (the stack in hand to the treasury),
	 * {@code tunjuk:JABATAN:ID}, {@code arah:ARAH}, {@code ikut_duel:0|1}, {@code serap} then {@code serap_ya},
	 * {@code lebur} then {@code lebur_ya} (two-step confirmation).
	 */
	public static final class Politik {
		public static final String LAYAR = "gabung_politik";
		public static final String TAB_MAJELIS = "majelis";
		public static final String TAB_SUKSESI = "suksesi";
		public static final String TAB_PERANG = "perang";
		public static final String TAB_KAS = "kas";
		public static final String TAB_CABANG = "cabang";
		public static final List<String> TABS = List.of(TAB_MAJELIS, TAB_SUKSESI, TAB_PERANG, TAB_KAS, TAB_CABANG);

		public static final String MAJELIS = "majelis";
		public static final String KETUA = "ketua";
		public static final String WAKIL = "wakil";
		public static final String PEWARIS = "pewaris";
		public static final String TETUA = "tetua";
		public static final String SUMBER = "sumber";
		public static final String NAIK = "naik";
		public static final String PERINGKAT = "peringkat";
		public static final String SUKSESI = "suksesi";
		public static final String BAB = "bab";
		public static final String SELESAI = "selesai";
		public static final String RAHASIA = "rahasia";
		public static final String SUARA = "suara";
		public static final String PILIHAN = "pilihan";
		public static final String PERANG = "perang";
		public static final String LAWAN = "lawan";
		public static final String LAWAN_ID = "lawan_id";
		public static final String MISI = "misi";
		public static final String GARIS = "garis";
		public static final String IKUT = "ikut";
		public static final String KAS = "kas";
		public static final String SUMBANGAN = "sumbangan";
		public static final String GRAFIK = "grafik";
		public static final String KAS_KETUA = "kas_ketua";
		public static final String CABANG = "cabang";
		public static final String PEMILIK = "pemilik";
		public static final String UPETI = "upeti";
		public static final String SERAP = "serap";
		public static final String LEBUR = "lebur";
		public static final String TERSERAP = "terserap";
		public static final String ARAH = "arah";

		/** {@code arah} values (41.8.5 "Arah sekte"). */
		public static final String ARAH_NETRAL = "netral";
		public static final String ARAH_DAMAI = "damai";
		public static final String ARAH_AGRESIF = "agresif";

		public static final String OP_SUARA = "suara";
		public static final String OP_USUL = "usul";
		public static final String OP_SUMBANG = "sumbang";
		public static final String OP_TUNJUK = "tunjuk";
		public static final String OP_ARAH = "arah";
		public static final String OP_IKUT_DUEL = "ikut_duel";
		public static final String OP_SERAP = "serap";
		public static final String OP_SERAP_YA = "serap_ya";
		public static final String OP_LEBUR = "lebur";
		public static final String OP_LEBUR_YA = "lebur_ya";

		private Politik() {
		}
	}

	/**
	 * {@code gabung_shifu} (41.10.9) — the elders' offers at "Tetua Memilih Murid" (41.5.7); built by 41B's
	 * {@code masuk/PilihShifu}, drawn by 41U-2. Sect header required.
	 * <pre>
	 * tawaran   D   1-3 offers: id S (tokoh id), potret I, nama T, jabatan T, ranah I, dao S, watak T, janji T ("Aku akan
	 *               mengajarimu pedang"), minat I (interest, drawn as a bar)
	 * kasihan   B   no elder wanted the player: the admissions elder takes them (single card, no choice)
	 * </pre>
	 * Ops: {@code pilih:ID}.
	 */
	public static final class Shifu {
		public static final String LAYAR = "gabung_shifu";

		public static final String TAWARAN = "tawaran";
		public static final String JANJI = "janji";
		public static final String MINAT = "minat";
		public static final String KASIHAN = "kasihan";

		public static final String OP_PILIH = "pilih";

		private Shifu() {
		}
	}

	/**
	 * {@code gabung_putusan} (41.10.9) — the verdict card after the test (41.5.5): red stamp "DITERIMA" or grey "DITOLAK";
	 * built by 41B's {@code Putusan}, drawn by 41U-2. Sect header required.
	 * <pre>
	 * diterima  B   accepted                   pangkat I  the rank given (absent when refused)
	 * skor      I   total score 0-100          istimewa B  Calon Istimewa (41.5.5)
	 * tahap     D   nama T, skor F (0-1), dilewati B (skipped by the door: counted 0.8)
	 * rincian   D   teks T, nilai I (each term of the score formula, signed)
	 * bonus     I   PK bonus given (40 or 100 x kf)
	 * saran     T   refused: the next step ("Pintu Pelayan terbuka bagimu", "Coba lagi setelah 3 hari")
	 * pelayan   B   refused but the servant door is offered (class &le; 3)
	 * </pre>
	 * Ops: {@code sumpah} (walk to the oath), {@code pelayan} (take the servant door now).
	 */
	public static final class Putusan {
		public static final String LAYAR = "gabung_putusan";

		public static final String DITERIMA = "diterima";
		public static final String ISTIMEWA = "istimewa";
		public static final String TAHAP = "tahap";
		public static final String DILEWATI = "dilewati";
		public static final String RINCIAN = "rincian";
		public static final String BONUS = "bonus";
		public static final String PELAYAN = "pelayan";

		public static final String OP_SUMPAH = "sumpah";
		public static final String OP_PELAYAN = "pelayan";

		private Putusan() {
		}
	}

	/**
	 * {@code gabung_hukum} (41.10.9, 41.6.11) — a hearing of the Balai Hukum; built by 41A's {@code BalaiHukum}, drawn by
	 * 41U-2 (opened from the card's Hukum tab or by the Discipline Elder). Sect header required, {@code npc} = the
	 * Discipline Elder.
	 * <pre>
	 * pelanggaran I  points now (0-100)         hukuman T  the sentence asked (fine, confinement, demotion, expulsion)
	 * catatan   D   hari L, teks T, poin I (the offences heard)
	 * saksi     D   nama T, pemain B (witnesses)
	 * ikatan    I   Teacher bond                shifu B  "Minta Shifu membela" possible (bond &ge; 50)
	 * soal      C   after op bela: teks T, pilihan D [ no I, teks T ] (one Ujian Hati question)
	 * tebus     C   running confinement: sisa_tick L, harga I (PK per 10 s), bisa B
	 * </pre>
	 * Ops: {@code terima}, {@code bela}, {@code jawab:NO}, {@code minta_shifu}, {@code tebus}.
	 */
	public static final class Hukum {
		public static final String LAYAR = "gabung_hukum";

		public static final String PELANGGARAN = "pelanggaran";
		public static final String HUKUMAN = "hukuman";
		public static final String CATATAN_HUKUM = "catatan";
		public static final String SAKSI = "saksi";
		public static final String SHIFU = "shifu";
		public static final String SOAL = "soal";
		public static final String PILIHAN = "pilihan";
		public static final String TEBUS = "tebus";

		public static final String OP_TERIMA = "terima";
		public static final String OP_BELA = "bela";
		public static final String OP_JAWAB = "jawab";
		public static final String OP_MINTA_SHIFU = "minta_shifu";
		public static final String OP_TEBUS = "tebus";

		private Hukum() {
		}
	}

	/**
	 * {@code gabung_ujian} (41.10.9, HUD, not a screen): the byte layout of {@link dev.kultivator.sekte.gabung.GabungPayloads.Ujian}
	 * shared by the senders (41B stages 1/3, 41B-C style tests) and the client HUD of 41U-1 ({@code HudUjian}). The record
	 * fields stay as they are: {@code tahap} 1-3 (0 hides), {@code tekanan} Mountain Pressure 0-100 (stage 1 only, else 0),
	 * {@code waktu} ticks left of the stage, {@code skor} 0-1 of the stage so far. {@code data[0]} is the kind
	 * ({@code J_*}); the following bytes are unsigned 0-255 ({@code b & 0xFF}); a byte past the end reads 0. Build the
	 * array with {@link #data}.
	 * <pre>
	 * J_TANGGA     1  stage 1 Tangga Ujian: [1] height % 0-100, [2] peak pressure 0-100, [3] slowness 0-2, [4] meditating 0/1,
	 *                 [5] illusions passed 0-4
	 * J_CORAK + o     stage 2, o = Corak ordinal:
	 *   PEDANG     2  Seratus Tebasan: [1] hits 0-100, [2] lit so far 0-100, [3] lit dummy 0-5 (255 none), [4] duel 0/1
	 *   BUDDHA     3  Lorong Perunggu: [1] wave 1-3, [2] waves passed 0-3, [3] monks standing 0-2, [4] own HP % 0-100,
	 *                 [5] oath broken 0/1
	 *   BINATANG   4  Taklukkan Binatang: [1] beast HP % 0-100, [2] target % (20), [3] phase 0 weaken, 1 feed, 2 done
	 *   FORMASI    5  Labirin Batu: [1] eyes stepped 0-3, [2] trigram hint 0-7 (255 none), [3] direction to the next eye
	 *                 0-7 (0 = north, clockwise; 255 unknown)
	 *   UNSUR      6  Lima Pilar Qi: [1] pillar 0-4, [2] needle 0-255, [3] band centre 0-255, [4] band half width 0-255,
	 *                 [5] holding 0/1, [6..10] accuracy per pillar 0-100, [11] seconds left on this pillar
	 *   ALKIMIA    7  Satu Tungku: [1] phase 0 waiting, 1 brewing, 2 done, [2] pill grade 0 Low .. 3 Perfect (255 none)
	 *   TUBUH      8  Pikul Gunung: [1] height % 0-100, [2] stone held 0/1
	 *   DARAH      9  Kolam Darah: [1] wave 1-3, [2] waves passed 0-3, [3] own HP % 0-100, [4] blood offering active 0/1
	 *   RACUN     10  Tiga Cawan: [1] cup 1-3, [2] right answers 0-3, [3] seconds left to choose 0-20, [4] poisoned 0/1
	 *   BAYANGAN  11  Ujian Senyap: [1] times seen 0-3, [2] jade taken 0/1, [3] nearest guard blocks 0-255, [4] alert 0-100
	 * J_HATI      12  stage 3 Ujian Hati: [1] question 1-3, [2] impression + 128 (0-255 = -128..127)
	 * J_WAWANCARA 13  the servant door's one question: [1] answered 0/1
	 * J_TAMU      14  the guest trial: [1] round 1-3, [2] rounds won 0-3
	 * </pre>
	 */
	public static final class HudUjian {
		public static final String ID = "gabung_ujian";

		public static final int J_KOSONG = 0;
		public static final int J_TANGGA = 1;
		/** First style-test kind: {@code J_CORAK + Corak.ordinal()} (2-11). */
		public static final int J_CORAK = 2;
		public static final int J_HATI = 12;
		public static final int J_WAWANCARA = 13;
		public static final int J_TAMU = 14;

		/** {@code data[0] = jenis}, then each value clamped to 0-255 (at most {@code GabungPayloads.UJIAN_DATA_MAKS} bytes). */
		public static byte[] data(int jenis, int... nilai) {
			int n = Math.min(nilai == null ? 0 : nilai.length, 39);
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

		private HudUjian() {
		}
	}

	private GabungLembar() {
	}
}
