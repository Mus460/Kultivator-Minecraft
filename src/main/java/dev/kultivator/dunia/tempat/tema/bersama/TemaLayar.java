package dev.kultivator.dunia.tempat.tema.bersama;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Contract C38.T3 (DESIGN 38.T.13.3, 38.T.14.4): the sheet ({@code lembar}) keys of every themed-town screen
 * {@code tema_*}, and the one registration point for their builders. 38TK theme agents build the sheets (pattern
 * {@code KotaView}) in a sheet-builder class of their own package and register it with {@link #daftar}; 38TV-U draws
 * them on the 38UI kit "Pasar Giok" and reads only this class. Header keys, entry keys, value types (T, S, I, L, F, B,
 * IA, D, BR, ...), state tokens ({@code K_*}) and the reserved ops ({@code lihat}, {@code tutup}, {@code tab},
 * {@code halaman}) are those of {@link LembarKunci}; this class only adds the theme-specific keys. Keys are only ever
 * added, never renamed; requests go to {@code docs/progres/kota-TK.md}. Money is always {@code long} NB.
 * <p>
 * Builder per screen (W2): {@code tema_dagang}, {@code tema_irama} 38TK inti; {@code tema_kapal}, {@code tema_galangan},
 * {@code tema_dokumen}, {@code tema_racik} 38TK-P; {@code tema_tawar}, {@code tema_kafilah}, {@code tema_mural} 38TK-O;
 * {@code tema_sutra}, {@code tema_teh}, {@code tema_bait} 38TK-A; {@code tema_herba}, {@code tema_ziarah},
 * {@code tema_lift} 38TK-G; {@code tema_pasar_gelap}, {@code tema_kitab_terlarang}, {@code tema_kontrak},
 * {@code tema_taruhan}, {@code tema_tawanan} 38TK-I.
 */
public final class TemaLayar {

	/** Every themed screen id (38.T.13.3). */
	public static final List<String> SEMUA = List.of(
			Tawar.LAYAR, Dagang.LAYAR, Kapal.LAYAR, Galangan.LAYAR, Kafilah.LAYAR, Mural.LAYAR, Sutra.LAYAR, Teh.LAYAR,
			Bait.LAYAR, Herba.LAYAR, Ziarah.LAYAR, Lift.LAYAR, PasarGelap.LAYAR, KitabTerlarang.LAYAR, Kontrak.LAYAR,
			Taruhan.LAYAR, Tawanan.LAYAR, Dokumen.LAYAR, Racik.LAYAR, Irama.LAYAR);

	/**
	 * Registers the builder and the action handler of one themed screen ({@code layar} must be in {@link #SEMUA}).
	 * {@code TempatLayar} then opens it on op {@code lihat}, refreshes it after each action and enforces reach 8 and
	 * the 10 actions per second limit.
	 */
	public static void daftar(String layar, TempatLayar.Pembangun pembangun, TempatLayar.Penangan penangan) {
		if (layar == null || !SEMUA.contains(layar) || pembangun == null || penangan == null) {
			Kultivator.LOGGER.warn("Unknown themed screen {} or missing builder/handler; not registered", layar);
			return;
		}
		TempatLayar.daftar(layar, penangan, pembangun);
	}

	/** Registers a read-only hub screen ({@code npcId -1}; {@link Dagang#LAYAR}, the hub card "Kota Bertema"). */
	public static void daftarHub(String layar, TempatLayar.Pembangun pembangun, TempatLayar.Penangan penangan) {
		if (layar == null || !SEMUA.contains(layar) || pembangun == null || penangan == null) {
			Kultivator.LOGGER.warn("Unknown themed hub screen {} or missing builder/handler; not registered", layar);
			return;
		}
		TempatLayar.daftarHub(layar, penangan, pembangun);
	}

	/** Builds and opens a registered themed screen for {@code player} at NPC {@code npcId} (-1: anchor/hub). */
	public static boolean buka(ServerPlayer player, String layar, int npcId) {
		return SEMUA.contains(layar) && TempatLayar.bukaDari(player, layar, npcId);
	}

	// ================================================================================================================

	/**
	 * {@code tema_tawar} — bazaar haggling (38.T.4.5), every bazaar stall of the Kota Oasis. Style {@code kain}.
	 * <pre>
	 * header    uang, npc (the trader; potret live)
	 * barang    C   nama T, barang BR, jumlah I
	 * buka      L   opening price (1.30 x base)        dasar_kira  T  "≈ ? NB" hint (never the hidden floor)
	 * tawaran   L   the player's current offer          langkah     IA offer steps in NB [1, 5, 10]
	 * putaran   I   round 1..3                          putaran_maks I  3
	 * suasana   I   trader mood 0-100                   ucapan      T  the trader's last line ("harga teman: 52 NB!")
	 * terima    L   price the trader accepts now (button "Terima N NB")
	 * menolak   B   "tidak jual hari ini" for this good (mood &lt; 20): whole screen locked with ucapan
	 * </pre>
	 * Ops: {@code tawar:NB}, {@code terima}, {@code pergi}.
	 */
	public static final class Tawar {
		public static final String LAYAR = "tema_tawar";
		public static final String BARANG = "barang";
		public static final String BUKA = "buka";
		public static final String DASAR_KIRA = "dasar_kira";
		public static final String TAWARAN = "tawaran";
		public static final String LANGKAH = "langkah";
		public static final String PUTARAN = "putaran";
		public static final String PUTARAN_MAKS = "putaran_maks";
		public static final String SUASANA = "suasana";
		public static final String UCAPAN = "ucapan";
		public static final String TERIMA = "terima";
		public static final String MENOLAK = "menolak";
		public static final String OP_TAWAR = "tawar";
		public static final String OP_TERIMA = "terima";
		public static final String OP_PERGI = "pergi";

		private Tawar() {
		}
	}

	/**
	 * {@code tema_dagang} — Buku Dagang (38.T.10.1; also the hub card "Kota Bertema" and {@code /kultivasi dagang}).
	 * Hub screen ({@code npcId -1}, read-only). Style {@code kertas}. Tabs {@code harga}, {@code laba}, {@code kontrak}.
	 * <pre>
	 * kota      D   id S (site), nama T, tema S (pelabuhan|oasis|air|gunung|iblis|ibu_kota), jarak I
	 * harga     D   id S (dagang/...), nama T, barang BR, asal S (tema), sel D [ kota S, nb F, umur_hari I,
	 *               jenuh F 0.6..1, terbaik B, asal B ]       (grey cell when umur_hari &gt; 3)
	 * laba      C   minggu L (profit this week), batas L (150 x (band+1)), hari IA (last 7 days profit), lewat B
	 * kontrak   D   id S, nama T, tujuan T, peti I, sisa_hari I, deposit L, bayar L, kapal B (sky x1.3)
	 * infami    I   0-100 (hub card meter)          gentar  D  kota T, nilai I
	 * laba.total L  lifetime trade profit (advancement tema_saudagar at 1000)
	 * pasar     C   MARKET MODE only (a theme market NPC or counter opened it with DagangLayar.bukaPasar): id S (site),
	 *               nama T, lewat B (weekly cap passed: sales at 0.5 x base),
	 *               beli  D [ id S (good id without dagang/), nama T, barang BR, nb L (unit price), stok I (left of 16) ]
	 *               jual  D [ id S, nama T, barang BR, jumlah I (carried), nb F (next unit), jenuh F, terkunci B + alasan T ]
	 *               segel D [ id S, nama T, nb L (seal 1 NB: 8 units become one Peti Dagang) ]
	 * </pre>
	 * Empty state: {@code kosong} "Belum ada kota bertema yang kau ketahui." Ops: read only ({@code tab}, {@code tandai});
	 * market mode adds {@code beli:ID:N}, {@code jual:ID:N}, {@code segel:ID}. Builder: {@code bersama.DagangLayar}.
	 */
	public static final class Dagang {
		public static final String LAYAR = "tema_dagang";
		public static final String TAB_HARGA = "harga";
		public static final String TAB_LABA = "laba";
		public static final String TAB_KONTRAK = "kontrak";
		public static final List<String> TABS = List.of(TAB_HARGA, TAB_LABA, TAB_KONTRAK);
		public static final String KOTA = "kota";
		public static final String TEMA = "tema";
		public static final String HARGA = "harga";
		public static final String ASAL = "asal";
		public static final String SEL = "sel";
		public static final String UMUR_HARI = "umur_hari";
		public static final String JENUH = "jenuh";
		public static final String TERBAIK = "terbaik";
		public static final String LABA = "laba";
		public static final String MINGGU = "minggu";
		public static final String BATAS = "batas";
		public static final String HARI_LABA = "hari";
		public static final String LEWAT = "lewat";
		public static final String KONTRAK = "kontrak";
		public static final String PETI = "peti";
		public static final String DEPOSIT = "deposit";
		public static final String BAYAR = "bayar";
		public static final String KAPAL = "kapal";
		public static final String INFAMI = "infami";
		public static final String GENTAR = "gentar";
		public static final String TOTAL = "total";
		public static final String PASAR = "pasar";
		public static final String BELI = "beli";
		public static final String JUAL = "jual";
		public static final String SEGEL = "segel";
		public static final String STOK = "stok";
		public static final String OP_BELI = "beli";
		public static final String OP_JUAL = "jual";
		public static final String OP_SEGEL = "segel";

		private Dagang() {
		}
	}

	/**
	 * {@code tema_kapal} — moored merchant ship (38.T.3.4), NAKHODA_LAUT on deck; tab {@code lintas} = Kapal Lintas
	 * Laut (Kapten Gao Fan). Style {@code kayu}.
	 * <pre>
	 * header    uang, npc, pita
	 * kapal     C   nama T, asal T, tambatan I, entitas S (kultivator:kapal_dagang), berangkat_tick L
	 * muatan    D   id S, nama T, barang BR, harga L, stok I, keadaan S (K_*)       (4-6 goods; 3D preview)
	 * jadwal    D   tambatan I, asal T, datang_hari I, keadaan S (datang|tambat|berangkat|kosong)
	 * lintas    C   ongkos L, bisa B, alasan T, berangkat T ("12.00"), murid I (0-2), badai B
	 * badai     B   storm: no departures (status bahaya)
	 * </pre>
	 * Ops: {@code beli:ID:N}, {@code lintas:MURID}.
	 */
	public static final class Kapal {
		public static final String LAYAR = "tema_kapal";
		public static final String TAB_MUATAN = "muatan";
		public static final String TAB_LINTAS = "lintas";
		public static final List<String> TABS = List.of(TAB_MUATAN, TAB_LINTAS);
		public static final String KAPAL = "kapal";
		public static final String ASAL = "asal";
		public static final String TAMBATAN = "tambatan";
		public static final String BERANGKAT_TICK = "berangkat_tick";
		public static final String MUATAN = "muatan";
		public static final String JADWAL = "jadwal";
		public static final String DATANG_HARI = "datang_hari";
		public static final String LINTAS = "lintas";
		public static final String ONGKOS = "ongkos";
		public static final String BERANGKAT = "berangkat";
		public static final String MURID = "murid";
		public static final String BADAI = "badai";
		public static final String OP_BELI = "beli";
		public static final String OP_LINTAS = "lintas";

		private Kapal() {
		}
	}

	/**
	 * {@code tema_galangan} — shipyard (38.T.3.4), Mbah Lunas. Style {@code kayu}.
	 * <pre>
	 * header    uang, npc, pita
	 * kelas     D   id S (perahu|sekte), nama T, harga L, hari I (2|4), pita_min I, keadaan S, alasan T
	 * pesanan   C   kelas S, tahap I 0-4, tahap_total I 5, sisa_hari I, entitas S (kultivator:kapal_galangan), siap B
	 * layar     D   id S (KapalStyle name), nama T, harga L (60)    (absent when no public KapalStyle API)
	 * </pre>
	 * Ops: {@code pesan:KELAS}, {@code ambil}, {@code layar:ID}.
	 */
	public static final class Galangan {
		public static final String LAYAR = "tema_galangan";
		public static final String KELAS = "kelas";
		public static final String PITA_MIN = "pita_min";
		public static final String PESANAN = "pesanan";
		public static final String SIAP = "siap";
		public static final String GAYA_LAYAR = "layar";
		public static final String OP_PESAN = "pesan";
		public static final String OP_AMBIL = "ambil";
		public static final String OP_LAYAR = "layar";

		private Galangan() {
		}
	}

	/**
	 * {@code tema_kafilah} — caravan board of the karavanserai / Kandang Unta (38.T.4.4). Style {@code kain}.
	 * <pre>
	 * header    uang, npc
	 * jadwal    D   id S, nama T, tujuan T, berangkat_hari I, unta I, keadaan S (tiba|bongkar|berangkat|di_jalan)
	 * ikut      C   penumpang L (8 NB), pengawal B (gilda mission kawal_unta), bisa B, alasan T
	 * sewa      C   harga L (12 per day), hari_maks I, aktif B, sisa_tick L, unta S (entity id while hired)
	 * badai     B   sandstorm: no departures
	 * </pre>
	 * Ops: {@code ikut:ID}, {@code kawal:ID}, {@code sewa:HARI}, {@code kembalikan}.
	 */
	public static final class Kafilah {
		public static final String LAYAR = "tema_kafilah";
		public static final String JADWAL = "jadwal";
		public static final String BERANGKAT_HARI = "berangkat_hari";
		public static final String UNTA = "unta";
		public static final String IKUT = "ikut";
		public static final String PENUMPANG = "penumpang";
		public static final String PENGAWAL = "pengawal";
		public static final String SEWA = "sewa";
		public static final String HARI_MAKS = "hari_maks";
		public static final String BADAI = "badai";
		public static final String OP_IKUT = "ikut";
		public static final String OP_KAWAL = "kawal";
		public static final String OP_SEWA = "sewa";
		public static final String OP_KEMBALIKAN = "kembalikan";

		private Kafilah() {
		}
	}

	/**
	 * {@code tema_mural} — Tebing Seribu Lukisan (38.T.4.5), Mo Hua. Style {@code batu}.
	 * <pre>
	 * kisah     D   no I 1-12, nama T, teks T (2-4 paragraphs), gambar I (mural atlas slot), dibaca B, bergerak B
	 * pesan     C   harga L (50), bisa B, alasan T, pilihan D [ id S, nama T, ikon I ] (player's feats),
	 *               milik C [ nama T, ikon I, sisa_hari I ] (own mural pending or done)
	 * </pre>
	 * Ops: {@code baca:NO}, {@code pesan:ID}.
	 */
	public static final class Mural {
		public static final String LAYAR = "tema_mural";
		public static final String KISAH = "kisah";
		public static final String GAMBAR = "gambar";
		public static final String DIBACA = "dibaca";
		public static final String BERGERAK = "bergerak";
		public static final String PESAN_MURAL = "pesan";
		public static final String PILIHAN = "pilihan";
		public static final String MILIK = "milik";
		public static final String OP_BACA = "baca";
		public static final String OP_PESAN = "pesan";

		private Mural() {
		}
	}

	/**
	 * {@code tema_sutra} — Rumah Sutra Su (38.T.5.4). Style {@code kain}. Tabs {@code toko}, {@code jubah}, {@code tenun}.
	 * <pre>
	 * header    uang, npc, diskon
	 * toko      D   id S, nama T, barang BR, harga L, stok I, keadaan S
	 * jubah     C   harga L (80), hari I (2), warna IA (dye colours offered), motif SL (awan|bangau|teratai|naga),
	 *               pesanan C [ warna I, motif S, sisa_hari I, siap B ]  (3D preview on a mannequin)
	 * tenun     C   bisa B, alasan T (once a day), hadiah_maks I (3)
	 * </pre>
	 * Ops: {@code beli:ID:N}, {@code pesan_jubah:WARNA:MOTIF}, {@code ambil_jubah}, {@code tenun} (opens tema_irama).
	 */
	public static final class Sutra {
		public static final String LAYAR = "tema_sutra";
		public static final String TAB_TOKO = "toko";
		public static final String TAB_JUBAH = "jubah";
		public static final String TAB_TENUN = "tenun";
		public static final List<String> TABS = List.of(TAB_TOKO, TAB_JUBAH, TAB_TENUN);
		public static final String TOKO = "toko";
		public static final String JUBAH = "jubah";
		public static final String WARNA = "warna";
		public static final String MOTIF = "motif";
		public static final String PESANAN = "pesanan";
		public static final String SIAP = "siap";
		public static final String TENUN = "tenun";
		public static final String HADIAH_MAKS = "hadiah_maks";
		public static final String OP_BELI = "beli";
		public static final String OP_PESAN_JUBAH = "pesan_jubah";
		public static final String OP_AMBIL_JUBAH = "ambil_jubah";
		public static final String OP_TENUN = "tenun";

		private Sutra() {
		}
	}

	/**
	 * {@code tema_teh} — Paviliun Teh Awan (38.T.5.4), Lu Yuqing. Style {@code kayu}.
	 * <pre>
	 * header    uang, npc
	 * menu      D   id S, nama T, barang BR, harga L, efek T, stok I, pita_min I, keadaan S
	 * aktif     C   nama T, sisa_tick L (current tea effect; only the strongest applies)
	 * upacara   C   harga L, bisa B (4 s scene of klip seduh_teh + tuang_teh)
	 * </pre>
	 * Ops: {@code beli:ID}, {@code upacara}.
	 */
	public static final class Teh {
		public static final String LAYAR = "tema_teh";
		public static final String MENU = "menu";
		public static final String EFEK = "efek";
		public static final String PITA_MIN = "pita_min";
		public static final String AKTIF_TEH = "aktif";
		public static final String UPACARA = "upacara";
		public static final String OP_BELI = "beli";
		public static final String OP_UPACARA = "upacara";

		private Teh() {
		}
	}

	/**
	 * {@code tema_bait} — Pasangan Bait in the Taman Sang Sastrawan (38.T.5.4), Wen Shi. Style {@code kertas}.
	 * <pre>
	 * bait      T   first line of the couplet          pilihan  D  id S, teks T (three second lines)
	 * benar     I   correct in a row 0-3                maks     I  3
	 * hari_ini  B   already rewarded today (screen still playable, no reward)
	 * </pre>
	 * Ops: {@code pilih:ID}.
	 */
	public static final class Bait {
		public static final String LAYAR = "tema_bait";
		public static final String BAIT = "bait";
		public static final String PILIHAN = "pilihan";
		public static final String BENAR = "benar";
		public static final String HARI_INI = "hari_ini";
		public static final String OP_PILIH = "pilih";

		private Bait() {
		}
	}

	/**
	 * {@code tema_herba} — Aula Herba Gunung (38.T.6.4). Style {@code kayu}. Tabs {@code beli}, {@code jual},
	 * {@code nilai}, {@code peringkat}.
	 * <pre>
	 * header    uang, npc, diskon
	 * beli      D   id S, nama T, barang BR, umur I (years), harga L, stok I, keadaan S
	 * jual      D   slot I, nama T, barang BR, umur I, harga L (0.5 x value)
	 * nilai     C   tangan C [ nama T, barang BR, umur I, mutu I ] (free age appraisal of the held herb)
	 * peringkat C   pangkat I 1-5, poin I, poin_naik I, titik_tambahan I, diskon F
	 * </pre>
	 * Ops: {@code beli:ID}, {@code jual:SLOT}, {@code nilai}.
	 */
	public static final class Herba {
		public static final String LAYAR = "tema_herba";
		public static final String TAB_BELI = "beli";
		public static final String TAB_JUAL = "jual";
		public static final String TAB_NILAI = "nilai";
		public static final String TAB_PERINGKAT = "peringkat";
		public static final List<String> TABS = List.of(TAB_BELI, TAB_JUAL, TAB_NILAI, TAB_PERINGKAT);
		public static final String BELI = "beli";
		public static final String JUAL = "jual";
		public static final String UMUR = "umur";
		public static final String NILAI_HERBA = "nilai";
		public static final String PERINGKAT = "peringkat";
		public static final String PANGKAT = "pangkat";
		public static final String POIN = "poin";
		public static final String POIN_NAIK = "poin_naik";
		public static final String TITIK_TAMBAHAN = "titik_tambahan";
		public static final String OP_BELI = "beli";
		public static final String OP_JUAL = "jual";
		public static final String OP_NILAI = "nilai";

		private Herba() {
		}
	}

	/**
	 * {@code tema_ziarah} — Seribu Anak Tangga pilgrimage (38.T.6.4), Kuil Kaki. Style {@code batu}.
	 * <pre>
	 * status    S   belum | berjalan | selesai | batal
	 * bordes    D   no I 1-9, nama T, dinyalakan B, x I, z I
	 * syarat    SL  rule keys shown as a list (tanpa_terbang, tanpa_kereta, tanpa_katrol, tanpa_teleport, satu_hari)
	 * batal     T   why it was cancelled ("Kau terbang di bordes 4.")
	 * restu     C   sisa_hari I (Restu Puncak 7 days), jeda_hari I (next allowed)
	 * </pre>
	 * Ops: {@code daftar}, {@code berhenti}, {@code tandai:X:Z} (read).
	 */
	public static final class Ziarah {
		public static final String LAYAR = "tema_ziarah";
		public static final String BORDES = "bordes";
		public static final String DINYALAKAN = "dinyalakan";
		public static final String SYARAT_ZIARAH = "syarat";
		public static final String BATAL = "batal";
		public static final String RESTU = "restu";
		public static final String JEDA_HARI = "jeda_hari";
		public static final String S_BELUM = "belum";
		public static final String S_BERJALAN = "berjalan";
		public static final String S_SELESAI = "selesai";
		public static final String S_BATAL = "batal";
		public static final String OP_DAFTAR = "daftar";
		public static final String OP_BERHENTI = "berhenti";

		private Ziarah() {
		}
	}

	/**
	 * {@code tema_lift} — cable car / pulley station (38.T.6.4), Paman Tie Suo. Style {@code kayu}.
	 * <pre>
	 * header    uang, npc
	 * ongkos    L   1 NB up, 0 down                     arah     S  naik | turun
	 * jadwal    C   berangkat_tick L, jam_buka T ("05.00-18.00"), tutup B (night)
	 * skema     C   kabin F 0..1 (position on the rope schema), tiang F (middle pylon at), macet B, penumpang I, kursi I
	 * </pre>
	 * Ops: {@code naik}, {@code turun}.
	 */
	public static final class Lift {
		public static final String LAYAR = "tema_lift";
		public static final String ONGKOS = "ongkos";
		public static final String ARAH = "arah";
		public static final String JADWAL = "jadwal";
		public static final String BERANGKAT_TICK = "berangkat_tick";
		public static final String JAM_BUKA = "jam_buka";
		public static final String TUTUP = "tutup";
		public static final String SKEMA = "skema";
		public static final String KABIN = "kabin";
		public static final String TIANG = "tiang";
		public static final String MACET = "macet";
		public static final String PENUMPANG = "penumpang";
		public static final String KURSI = "kursi";
		public static final String OP_NAIK = "naik";
		public static final String OP_TURUN = "turun";

		private Lift() {
		}
	}

	/**
	 * {@code tema_pasar_gelap} — Pasar Seribu Bayangan (38.T.7.6). Style {@code kain} (black cloth). Tabs {@code racun},
	 * {@code pil}, {@code binatang}, {@code peta}, {@code topeng}, {@code curian}.
	 * <pre>
	 * header    uang, npc
	 * markup    F   1.3 / 1.2 (Gentar &gt;= 30) / 1.15 (Infami &gt;= 30) / 1.1 (Tamu Darah)
	 * gentar    I   0-100                               infami  I  0-100
	 * barang_d  D   id S, nama T, barang BR, harga L, stok I, pita_min I, keadaan S, gelap B (asal_gelap component)
	 * penyusup  B   refused: whole screen locked ("Penyusup tidak dilayani.")
	 * </pre>
	 * Ops: {@code beli:ID:N}.
	 */
	public static final class PasarGelap {
		public static final String LAYAR = "tema_pasar_gelap";
		public static final String TAB_RACUN = "racun";
		public static final String TAB_PIL = "pil";
		public static final String TAB_BINATANG = "binatang";
		public static final String TAB_PETA = "peta";
		public static final String TAB_TOPENG = "topeng";
		public static final String TAB_CURIAN = "curian";
		public static final List<String> TABS = List.of(TAB_RACUN, TAB_PIL, TAB_BINATANG, TAB_PETA, TAB_TOPENG, TAB_CURIAN);
		public static final String MARKUP = "markup";
		public static final String GENTAR = "gentar";
		public static final String INFAMI = "infami";
		public static final String BARANG_DAFTAR = "barang_d";
		public static final String GELAP = "gelap";
		public static final String PENYUSUP = "penyusup";
		public static final String OP_BELI = "beli";

		private PasarGelap() {
		}
	}

	/**
	 * {@code tema_kitab_terlarang} — Menara Kitab Terlarang (38.T.7.7), Si Buta Mo Wen. Style {@code kertas} (dark).
	 * <pre>
	 * header    uang, npc
	 * lantai    D   no I 1-9, nama T, terbuka B, gentar_min I (10 x (n-1)), segel B (floor 9)
	 * gulungan  D   id S (tema/gulungan_*), nama T, barang BR, harga L, pita_min I, infami I (+2), efek T, keadaan S
	 * giok      D   id S, nama T, barang BR, harga L (3 x jade), infami I (+8), keadaan S  (absent without a public API)
	 * </pre>
	 * Ops: {@code beli:ID}, {@code lantai:NO} (read).
	 */
	public static final class KitabTerlarang {
		public static final String LAYAR = "tema_kitab_terlarang";
		public static final String LANTAI = "lantai";
		public static final String TERBUKA = "terbuka";
		public static final String GENTAR_MIN = "gentar_min";
		public static final String SEGEL = "segel";
		public static final String GULUNGAN = "gulungan";
		public static final String INFAMI = "infami";
		public static final String EFEK = "efek";
		public static final String GIOK = "giok";
		public static final String OP_BELI = "beli";
		public static final String OP_LANTAI = "lantai";

		private KitabTerlarang() {
		}
	}

	/**
	 * {@code tema_kontrak} — Sarang Kontrak board (38.T.7.7). Contracts ONLY against demonic beasts and elites, rival
	 * demonic cultivator groups, bandit bands and hunting righteous squads; never townsfolk, villagers, disciples or
	 * players. Style {@code kertas}.
	 * <pre>
	 * kontrak   D   id S, nama T, sasaran T, jenis S (binatang|iblis_saingan|begal|pemburu), pangkat S (B..S),
	 *               imbalan L (MisiGilda x 1.3), infami I (+5, beasts +1), gentar I (+3), jarak I, x I, z I, keadaan S
	 * aktif     D   id S, nama T, sisa_hari I, kemajuan F
	 * </pre>
	 * Ops: {@code ambil:ID}, {@code serah:ID}, {@code batal:ID}.
	 */
	public static final class Kontrak {
		public static final String LAYAR = "tema_kontrak";
		public static final String KONTRAK = "kontrak";
		public static final String SASARAN = "sasaran";
		public static final String PANGKAT = "pangkat";
		public static final String IMBALAN = "imbalan";
		public static final String INFAMI = "infami";
		public static final String GENTAR = "gentar";
		public static final String AKTIF_KONTRAK = "aktif";
		public static final String OP_AMBIL = "ambil";
		public static final String OP_SERAH = "serah";
		public static final String OP_BATAL = "batal";

		private Kontrak() {
		}
	}

	/**
	 * {@code tema_taruhan} — Arena Darah betting and the Tangga Darah ladder (38.T.7.5), Tu Gang. Style {@code batu}.
	 * Tabs {@code taruhan}, {@code tangga}.
	 * <pre>
	 * header    uang, npc
	 * laga      C   sisa_tick L, kiri C, kanan C (each: nama T, julukan T, tier I, watak T, peluang F, odds F, potret I)
	 * taruhan   C   maks L (20 x (band+1)), sisa I (of 6 a night), pasang C [ sisi S kiri|kanan, nb L ]
	 * tangga    C   peringkat I 1-9, biaya L (8 x (band+1)), hadiah L, lawan C [nama T, julukan T, tier I],
	 *               sisa I (of 5 a day), bisa B, alasan T
	 * </pre>
	 * Ops: {@code pasang:SISI:NB}, {@code tantang}, {@code tebusan_darah:ID} (from tema_tawanan).
	 */
	public static final class Taruhan {
		public static final String LAYAR = "tema_taruhan";
		public static final String TAB_TARUHAN = "taruhan";
		public static final String TAB_TANGGA = "tangga";
		public static final List<String> TABS = List.of(TAB_TARUHAN, TAB_TANGGA);
		public static final String LAGA = "laga";
		public static final String KIRI = "kiri";
		public static final String KANAN = "kanan";
		public static final String JULUKAN = "julukan";
		public static final String TIER = "tier";
		public static final String WATAK = "watak";
		public static final String ODDS = "odds";
		public static final String TARUHAN = "taruhan";
		public static final String SISA = "sisa";
		public static final String PASANG = "pasang";
		public static final String SISI = "sisi";
		public static final String TANGGA = "tangga";
		public static final String PERINGKAT = "peringkat";
		public static final String HADIAH = "hadiah";
		public static final String LAWAN = "lawan";
		public static final String OP_PASANG = "pasang";
		public static final String OP_TANTANG = "tantang";
		public static final String OP_TEBUSAN_DARAH = "tebusan_darah";

		private Taruhan() {
		}
	}

	/**
	 * {@code tema_tawanan} — Kandang Tawanan (38.T.7.6). The only actions are ransom and the blood ransom duel; a captive
	 * is never owned, employed or sold (38.T.0 butir 5). Style {@code batu}.
	 * <pre>
	 * header    uang, npc
	 * tawanan   D   id S, nama T, asal T (home village), umur I, potret I, cerita T, harga L (25 x (1 + kekuatan/2))
	 * sisa      I   ransoms left today (of 3)           kosong (header) when the cells are empty
	 * </pre>
	 * Ops: {@code tebus:ID}, {@code tebusan_darah:ID}.
	 */
	public static final class Tawanan {
		public static final String LAYAR = "tema_tawanan";
		public static final String TAWANAN = "tawanan";
		public static final String ASAL = "asal";
		public static final String UMUR = "umur";
		public static final String CERITA = "cerita";
		public static final String SISA = "sisa";
		public static final String OP_TEBUS = "tebus";
		public static final String OP_TEBUSAN_DARAH = "tebusan_darah";

		private Tawanan() {
		}
	}

	/**
	 * {@code tema_dokumen} — quest mini-game: match three cargo manifests, one is forged (quest
	 * {@code tema_pelabuhan_pelita} bab 2). Style {@code kertas}.
	 * <pre>
	 * surat     D   id S, judul T, baris D [ label T, isi T ] (ship, cargo, weight, seal, date)
	 * catatan   D   label T, isi T (the customs ledger to compare with)
	 * percobaan I   tries left (3)
	 * </pre>
	 * Ops: {@code palsu:ID}.
	 */
	public static final class Dokumen {
		public static final String LAYAR = "tema_dokumen";
		public static final String SURAT = "surat";
		public static final String BARIS = "baris";
		public static final String LABEL = "label";
		public static final String ISI = "isi";
		public static final String PERCOBAAN = "percobaan";
		public static final String OP_PALSU = "palsu";

		private Dokumen() {
		}
	}

	/**
	 * {@code tema_racik} — quest mini-game: three-step lens making from sea-salt glass (quest
	 * {@code tema_pelabuhan_pelita} bab 3). Style {@code kayu}.
	 * <pre>
	 * langkah   I   current step 1..3                   tahap_total (common) 3
	 * bahan     D   id S, nama T, barang BR, punya I, perlu I
	 * pilihan   D   id S, teks T (heat / grind / polish choices)
	 * panas     F   furnace heat 0..1 (gauge)           mutu  I  result grade so far (-1 none)
	 * </pre>
	 * Ops: {@code pilih:ID}, {@code ulang}.
	 */
	public static final class Racik {
		public static final String LAYAR = "tema_racik";
		public static final String LANGKAH = "langkah";
		public static final String BAHAN = "bahan";
		public static final String PILIHAN = "pilihan";
		public static final String PANAS = "panas";
		public static final String OP_PILIH = "pilih";
		public static final String OP_ULANG = "ulang";

		private Racik() {
		}
	}

	/**
	 * {@code tema_irama} — rhythm mini-game shared by the whirling dance (oasis, 30 s), weaving (air, 20 s; Kain Xiu Niang
	 * 3 rounds) and the dragon-boat drum (air, 60 s). The client plays the beat track locally and sends the result once.
	 * Style {@code kain}.
	 * <pre>
	 * pola      S   tari_putar | tenun | genderang
	 * ketukan   IA  beat times in ticks from start; arah IA  direction key per beat (0 up, 1 right, 2 down, 3 left)
	 * durasi    I   ticks                               lulus  I  pass score (70)
	 * babak     I   round 1..n                          babak_total I
	 * </pre>
	 * Ops: {@code hasil:SKOR} (0-100, checked server side against the beat count and time).
	 */
	public static final class Irama {
		public static final String LAYAR = "tema_irama";
		public static final String POLA = "pola";
		public static final String POLA_TARI_PUTAR = "tari_putar";
		public static final String POLA_TENUN = "tenun";
		public static final String POLA_GENDERANG = "genderang";
		public static final String KETUKAN = "ketukan";
		public static final String ARAH = "arah";
		public static final String DURASI = "durasi";
		public static final String LULUS = "lulus";
		public static final String BABAK = "babak";
		public static final String BABAK_TOTAL = "babak_total";
		public static final String OP_HASIL = "hasil";

		private Irama() {
		}
	}

	private TemaLayar() {
	}
}
