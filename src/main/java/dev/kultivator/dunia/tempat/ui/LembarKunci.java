package dev.kultivator.dunia.tempat.ui;

import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;

/**
 * The sheet ({@code lembar}) contract of every core 38 screen (DESIGN 38.U.3; jadwal-38-39-40 section 5, 38UI-0): screen
 * ids, tab ids, keys, value meanings and op verbs. Gameplay modules build sheets with these constants (pattern
 * {@code KotaView}) and open them with {@link TempatLayar#buka}; the 38UI kit draws them. Both sides compile against this
 * class only, so screens and builders are written in parallel. Key changes are requested in
 * {@code docs/progres/kota-UI.md}; keys are only ever added, never renamed. Theme screens ({@code tema_*}) document
 * their keys in 38TK's {@code TemaLayar} (C38.T3) and reuse the common keys of this class.
 *
 * <h2>Value types used in the schemas</h2>
 * <ul>
 * <li><b>T</b> text: a {@link Component} written with {@link #teks(CompoundTag, String, Component)} (codec
 * {@link ComponentSerialization#CODEC}); fixed texts are translatable components with keys of the builder's lang
 * fragment, names are the names the server already has. Never a raw string for anything a player reads.</li>
 * <li><b>S</b> id string, <b>I</b> int, <b>L</b> long, <b>F</b> float, <b>B</b> boolean, <b>IA</b> int array,
 * <b>LA</b> long array, <b>C</b> compound, <b>D</b> list of compounds, <b>SL</b> list of strings.</li>
 * <li><b>BR</b> item: a full {@link ItemStack} written with {@link #barang(CompoundTag, ItemStack, HolderLookup.Provider)}
 * under {@link #BARANG} (components kept, for the 3D preview, quality colour and tooltip); always next to a {@link #NAMA}.</li>
 * <li>Money is always {@code long} Nilai Batu (NB, {@code NilaiBatu.VALUE {1, 9, 81, 729}}); PK is always a separate
 * {@code int} column (38.M.0: never mixed, never converted).</li>
 * <li>Times: {@link #SISA_TICK} is ticks left <i>at send time</i> (the client counts down from the receive time);
 * {@link #HARI} is a world day number ({@code overworld clock / 24000}).</li>
 * <li>Lists hold at most {@link #DAFTAR_MAKS} entries (38.D.7 snapshot cap); the whole sheet stays under
 * {@link TempatLayar#LEMBAR_ANGGARAN} bytes.</li>
 * <li>A key that is absent means "not applicable" (no row drawn); a builder never sends placeholder values.</li>
 * </ul>
 *
 * <h2>Header keys (every screen, all optional unless the screen schema says required)</h2>
 * <pre>
 * judul        T   panel title                         subjudul   T   second line under the title
 * gaya         S   panel style  GAYA_*                 distrik    S   colour token  DISTRIK_*
 * situs        L   site id of the place                tempat     T   place name ("Kota Awan Seribu Gerbang")
 * uang         L   player's NB on hand                 batu       IA  player's stones by tier [rendah, menengah, tinggi, agung]
 * nama_baik    I   Nama Baik at this place             pangkat_nb I   rank 0 Musuh Kota .. 5 Pahlawan Kota (38.B.5.1)
 * pita         I   player's band 0-8 (Harga band)      diskon     F   price discount 0..0.15 shown as a seal
 * tab          S   tab to show when opening            tab_kunci  C   tab id -&gt; T reason (tab drawn locked, KeadaanTerkunci)
 * tab_sembunyi SL  tab ids not drawn at all            npc        C   speaker card, keys below
 * pesan        T   one-line result of the last action  pesan_jenis S  PESAN_OK | PESAN_PERINGATAN | PESAN_BAHAYA
 * kosong       C   empty state of the main list: teks T, op S (button op), tombol T (button label)
 * kunci_layar  C   whole screen locked: alasan T, syarat T (KeadaanTerkunci with seal)
 * </pre>
 * Speaker card {@code npc}: {@code nama} T, {@code gelar} T (role title, "Pemilik Penginapan"), {@code peran} S (NpcRole
 * name), {@code potret} I (entity id whose head is rendered live, blinking and talking; -1: none, draw {@code ikon}),
 * {@code ikon} I (slot of {@link Ikon}), {@code tokoh} S (recurring character id of 38.S.2), {@code sekte} T, {@code takut}
 * B (kneeling portrait, 38.R.5).
 *
 * <h2>Common entry keys (cards in lists)</h2>
 * {@code id} S (the op argument), {@code nama} T, {@code barang} BR, {@code ikon} I, {@code entitas} S (entity type id for
 * Pratinjau3D), {@code harga} L, {@code nilai} L, {@code stok} I (-1 unlimited), {@code jumlah} I, {@code mutu} I (-1 none,
 * else grade 0-4 / quality colour), {@code keadaan} S ({@code K_*}), {@code terkunci} B + {@code alasan} T, {@code langka}
 * B (gold star "langka hari ini"), {@code baru} B (gold flash), {@code anda} B (row of the player, highlighted), {@code x}
 * {@code z} I (world position for "tandai di peta"), {@code sisa_tick} L, {@code catatan} T.
 *
 * <h2>Ops</h2>
 * {@code "verb[:arg[:arg...]]"} built with {@link #op}, split with {@link #urai}; at most 256 chars. Reserved verbs handled
 * by {@link TempatLayar} itself: {@link #OP_TUTUP} (screen closed) and {@link #OP_LIHAT} (build and open, screens with a
 * {@code Pembangun}). Read-only verbs ({@link #opBaca}) are the only ones a hub screen ({@code npcId -1}, no anchor) may send.
 */
public final class LembarKunci {

	/** List cap per sheet list (38.D.7: snapshot at most 48 entries). */
	public static final int DAFTAR_MAKS = 48;

	// ---------------------------------------------------------------------------------------------------------------
	// Header keys

	public static final String JUDUL = "judul";
	public static final String SUBJUDUL = "subjudul";
	public static final String GAYA = "gaya";
	public static final String DISTRIK = "distrik";
	public static final String SITUS = "situs";
	public static final String TEMPAT = "tempat";
	public static final String UANG = "uang";
	public static final String BATU = "batu";
	public static final String NAMA_BAIK = "nama_baik";
	public static final String PANGKAT_NB = "pangkat_nb";
	public static final String PITA = "pita";
	public static final String DISKON = "diskon";
	public static final String TAB = "tab";
	public static final String TAB_KUNCI = "tab_kunci";
	public static final String TAB_SEMBUNYI = "tab_sembunyi";
	public static final String NPC = "npc";
	public static final String PESAN = "pesan";
	public static final String PESAN_JENIS = "pesan_jenis";
	public static final String KOSONG = "kosong";
	public static final String KUNCI_LAYAR = "kunci_layar";

	// Speaker card keys (inside NPC)
	public static final String GELAR = "gelar";
	public static final String PERAN = "peran";
	public static final String POTRET = "potret";
	public static final String TOKOH_ID = "tokoh";
	public static final String SEKTE = "sekte";
	public static final String TAKUT = "takut";

	// Empty state / lock keys (inside KOSONG, KUNCI_LAYAR)
	public static final String TOMBOL = "tombol";

	// ---------------------------------------------------------------------------------------------------------------
	// Common entry keys

	public static final String ID = "id";
	public static final String NAMA = "nama";
	public static final String TEKS = "teks";
	public static final String BARANG = "barang";
	public static final String IKON = "ikon";
	public static final String ENTITAS = "entitas";
	public static final String HARGA = "harga";
	public static final String NILAI = "nilai";
	public static final String NB = "nb";
	public static final String PK = "pk";
	public static final String STOK = "stok";
	public static final String JUMLAH = "jumlah";
	public static final String MUTU = "mutu";
	public static final String KEADAAN = "keadaan";
	public static final String TERKUNCI = "terkunci";
	public static final String ALASAN = "alasan";
	public static final String SYARAT = "syarat";
	public static final String LANGKA = "langka";
	public static final String BARU = "baru";
	public static final String ANDA = "anda";
	public static final String OK = "ok";
	public static final String BISA = "bisa";
	public static final String NO = "no";
	public static final String OP = "op";
	public static final String X = "x";
	public static final String Z = "z";
	public static final String JARAK = "jarak";
	public static final String SISA_TICK = "sisa_tick";
	public static final String SISA_HARI = "sisa_hari";
	public static final String HARI = "hari";
	public static final String CATATAN = "catatan";
	public static final String KEMAJUAN = "kemajuan";
	public static final String SKOR = "skor";
	public static final String MAKS = "maks";
	public static final String DAFTAR = "daftar";
	public static final String TANGAN = "tangan";
	public static final String BIAYA = "biaya";
	public static final String STATUS = "status";
	public static final String JENIS = "jenis";
	public static final String TAHAP = "tahap";
	public static final String TAHAP_TOTAL = "tahap_total";
	public static final String SLOT = "slot";
	public static final String PELUANG = "peluang";
	public static final String RISIKO = "risiko";
	public static final String TUJUAN = "tujuan";
	public static final String AKTIF = "aktif";
	public static final String RIWAYAT = "riwayat";
	public static final String UJIAN = "ujian";
	public static final String TARGET = "target";

	// ---------------------------------------------------------------------------------------------------------------
	// Values

	/** Panel styles (38.U.2, textures {@code gui/tempat/panel_<gaya>.png}). */
	public static final String GAYA_KERTAS = "kertas";
	public static final String GAYA_KAYU = "kayu";
	public static final String GAYA_BATU = "batu";
	public static final String GAYA_KAIN = "kain";
	public static final String GAYA_GIOK = "giok";
	public static final List<String> SEMUA_GAYA = List.of(GAYA_KERTAS, GAYA_KAYU, GAYA_BATU, GAYA_KAIN, GAYA_GIOK);

	/** District colour tokens (38.U.2 {@code TempatWarna}; ids of 38.B.3, {@code hunian} covers {@code hunian_barat}). */
	public static final String DISTRIK_ISTANA = "istana";
	public static final String DISTRIK_CENDEKIA = "cendekia";
	public static final String DISTRIK_AWAN = "awan";
	public static final String DISTRIK_GILDA = "gilda";
	public static final String DISTRIK_PUSAT = "pusat";
	public static final String DISTRIK_PASAR = "pasar";
	public static final String DISTRIK_HUNIAN = "hunian";
	public static final String DISTRIK_ARENA = "arena";
	public static final String DISTRIK_KUMUH = "kumuh";
	public static final String DISTRIK_GERBANG = "gerbang";
	public static final String DISTRIK_LUAR = "luar";
	public static final List<String> SEMUA_DISTRIK = List.of(DISTRIK_ISTANA, DISTRIK_CENDEKIA, DISTRIK_AWAN, DISTRIK_GILDA,
			DISTRIK_PUSAT, DISTRIK_PASAR, DISTRIK_HUNIAN, DISTRIK_ARENA, DISTRIK_KUMUH, DISTRIK_GERBANG, DISTRIK_LUAR);

	/** {@link #PESAN_JENIS} values (status tokens ok / peringatan / bahaya of 38.U.2). */
	public static final String PESAN_OK = "ok";
	public static final String PESAN_PERINGATAN = "peringatan";
	public static final String PESAN_BAHAYA = "bahaya";

	/** {@link #KEADAAN} values of a card. */
	public static final String K_OK = "ok";
	/** Sold out: grey card "Habis, kembali besok". */
	public static final String K_HABIS = "habis";
	/** Locked by band, rank or level: padlock + {@link #ALASAN}. */
	public static final String K_TERKUNCI = "terkunci";
	/** Not enough money: red price, disabled button with reason. */
	public static final String K_KURANG_UANG = "kurang_uang";
	/** Daily limit reached. */
	public static final String K_BATAS = "batas";
	/** Closed at this hour (night, curfew). */
	public static final String K_TUTUP = "tutup";
	/** Refused (wanted, hostile, sect at war). */
	public static final String K_DITOLAK = "ditolak";

	/** Nama Baik ranks of 38.B.5.1 in {@link #PANGKAT_NB} order. */
	public static final int PANGKAT_MUSUH = 0;
	public static final int PANGKAT_ASING = 1;
	public static final int PANGKAT_TAMU = 2;
	public static final int PANGKAT_WARGA = 3;
	public static final int PANGKAT_TERHORMAT = 4;
	public static final int PANGKAT_PAHLAWAN = 5;

	// ---------------------------------------------------------------------------------------------------------------
	// Ops

	/** Reserved: build and open (screens with a {@code Pembangun}); also "refresh me". */
	public static final String OP_LIHAT = "lihat";
	/** Reserved: the client closed the screen; ends the session. */
	public static final String OP_TUTUP = "tutup";
	/** Read: {@code tab:ID}, the player switched tab (for sheets that send a tab lazily). */
	public static final String OP_TAB = "tab";
	/** Read: {@code tandai:X:Z}, mark a place on the world map. */
	public static final String OP_TANDAI = "tandai";
	/** Read: {@code halaman:N}, page of a long list. */
	public static final String OP_HALAMAN = "halaman";
	/** Read: {@code lacak:ID}, track a quest with the compass needle (journal). */
	public static final String OP_LACAK = "lacak";
	private static final Set<String> BACA = Set.of(OP_LIHAT, OP_TAB, OP_TANDAI, OP_HALAMAN, OP_LACAK);

	/** True for verbs a read-only hub screen may send. */
	public static boolean opBaca(String verb) {
		return BACA.contains(verb);
	}

	/** {@code verb:arg:arg}; args with ':' are not allowed (replaced by '_'); capped at 256 chars. */
	public static String op(String verb, Object... args) {
		StringBuilder builder = new StringBuilder(verb);
		for (Object arg : args) {
			builder.append(':').append(String.valueOf(arg).replace(':', '_'));
		}
		return builder.length() > TempatAksiPayload.OP_MAKS ? builder.substring(0, TempatAksiPayload.OP_MAKS) : builder.toString();
	}

	/** The verb of an op (text before the first ':'). */
	public static String verb(String op) {
		int colon = op.indexOf(':');
		return colon < 0 ? op : op.substring(0, colon);
	}

	/** An op split at ':'; index 0 is the verb. */
	public static String[] urai(String op) {
		return op.split(":", -1);
	}

	/** Int argument {@code index} of a split op, or {@code fallback}. */
	public static int argInt(String[] parts, int index, int fallback) {
		if (index >= parts.length) {
			return fallback;
		}
		try {
			return Integer.parseInt(parts[index]);
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	/** Long argument {@code index} of a split op, or {@code fallback}. */
	public static long argLong(String[] parts, int index, long fallback) {
		if (index >= parts.length) {
			return fallback;
		}
		try {
			return Long.parseLong(parts[index]);
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	/** String argument {@code index} of a split op, or {@code ""}. */
	public static String arg(String[] parts, int index) {
		return index < parts.length ? parts[index] : "";
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Value helpers (both sides)

	/** Writes text value {@code kunci} (type T). */
	public static void teks(CompoundTag tag, String kunci, Component teks) {
		if (teks != null) {
			tag.store(kunci, ComponentSerialization.CODEC, teks);
		}
	}

	/** Reads text value {@code kunci}; empty when absent. */
	public static Component teks(CompoundTag tag, String kunci) {
		return tag.read(kunci, ComponentSerialization.CODEC).orElse(Component.empty());
	}

	/** True when text value {@code kunci} is present. */
	public static boolean adaTeks(CompoundTag tag, String kunci) {
		return tag.contains(kunci);
	}

	/** Writes item {@code stack} under {@link #BARANG} (type BR) plus its hover name under {@link #NAMA} if none yet. */
	public static void barang(CompoundTag tag, ItemStack stack, HolderLookup.Provider registries) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		tag.store(BARANG, ItemStack.CODEC, registries.createSerializationContext(NbtOps.INSTANCE), stack);
		if (!tag.contains(NAMA)) {
			teks(tag, NAMA, stack.getHoverName());
		}
	}

	/** Reads the item under {@link #BARANG}; {@link ItemStack#EMPTY} when absent or unreadable. */
	public static ItemStack barang(CompoundTag tag, HolderLookup.Provider registries) {
		if (!tag.contains(BARANG)) {
			return ItemStack.EMPTY;
		}
		return tag.read(BARANG, ItemStack.CODEC, registries.createSerializationContext(NbtOps.INSTANCE)).orElse(ItemStack.EMPTY);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Screens of 38.U.3

	/**
	 * {@code dialog} (38.U.3.1) — every 38 NPC. Built by 38ST ({@code TempatDialog}, data dialogs 38.S.1); services listed
	 * by the module that owns them. Style {@link #GAYA_KERTAS} (the place's style may override).
	 * <pre>
	 * required  npc C (speaker card), teks T (current line)
	 * dialog    S   dialog id                      simpul   S  node id
	 * gestur    I   clip id the body plays (info)  akhir    B  dialog ended: only "(Esc) tutup"
	 * nama_baik I   shown top right
	 * pilihan   D   no I (1-9, digit key), teks T, terkunci B, alasan T (unmet condition shown locked with its reason)
	 * jasa      D   id S (screen id to open), nama T, ikon I, catatan T (hours "08.00-20.00"), terkunci B, alasan T
	 *               (tab "Jasa" hidden when absent or empty)
	 * quest     D   id S, nama T, teks T (stage line), baru B (tab "Quest" hidden when absent or empty)
	 * </pre>
	 * Ops: {@code pilih:NO}, {@code jasa:LAYAR} (the handler opens that screen for the same NPC), {@code quest:ID}.
	 */
	public static final class Dialog {
		public static final String LAYAR = "dialog";
		public static final String TAB_BICARA = "bicara";
		public static final String TAB_JASA = "jasa";
		public static final String TAB_QUEST = "quest";
		public static final List<String> TABS = List.of(TAB_BICARA, TAB_JASA, TAB_QUEST);

		public static final String DIALOG = "dialog";
		public static final String SIMPUL = "simpul";
		public static final String GESTUR = "gestur";
		public static final String AKHIR = "akhir";
		public static final String PILIHAN = "pilihan";
		public static final String JASA = "jasa";
		public static final String QUEST = "quest";

		public static final String OP_PILIH = "pilih";
		public static final String OP_JASA = "jasa";
		public static final String OP_QUEST = "quest";

		private Dialog() {
		}
	}

	/**
	 * {@code toko_senjata} (38.U.3.2) — Paviliun Seribu Bilah, Gilda Pandai Pusaka, sect markets, town stalls. Built by
	 * 38EK ({@code TokoSenjata}, {@code PesananTempa}). Style {@link #GAYA_KAYU}. Tabs = weapon shapes of 38.M.2.1 plus
	 * armour and the forge order tab; tabs without goods go in {@code tab_sembunyi}.
	 * <pre>
	 * header    uang, diskon, pita
	 * daftar    D   id S, tab S (TAB_*), nama T, barang BR, harga L, stok I, mutu I, keadaan S, alasan T, langka B,
	 *               kelas_duel S (PEDANG|TOMBAK|KAPAK|GADA, 35), stat D [ kunci S (STAT_*), nilai F,
	 *               banding F (difference to the weapon in hand; absent = nothing to compare) ]
	 * tangan    C   nama T, barang BR (weapon in hand, "vs di tangan")
	 * layanan   C   perbaiki L (repair price of the item in hand, absent = cannot), nilai_sisa I (free appraisals left)
	 * pesan     C   forge order tab:
	 *               bentuk D [ id S, nama T, ikon I ], mutu_maks I, pesanan_maks I (2),
	 *               kutipan C (answer to op kutip) [ id S (shape), mutu I, bahan D [ nama T, barang BR, perlu I, punya I ],
	 *                 ongkos L, hari_jadi I, bisa B, alasan T ],
	 *               pesanan D [ id S, nama T, barang BR, mutu I, sisa_tick L, siap B ]  (CincinWaktu per order)
	 * </pre>
	 * Ops: {@code beli:ID:N}, {@code kutip:BENTUK:MUTU}, {@code pesan:BENTUK:MUTU}, {@code ambil:ID}, {@code perbaiki},
	 * {@code nilai}. Search ({@code /}) and sort are client side.
	 */
	public static final class TokoSenjata {
		public static final String LAYAR = "toko_senjata";
		public static final String TAB_JIAN = "jian";
		public static final String TAB_DAO = "dao";
		public static final String TAB_QIANG = "qiang";
		public static final String TAB_KAPAK = "kapak";
		public static final String TAB_GADA = "gada";
		public static final String TAB_KIPAS_BESI = "kipas_besi";
		public static final String TAB_KEBUT = "kebut";
		public static final String TAB_ZIRAH = "zirah";
		public static final String TAB_PESAN = "pesan";
		public static final List<String> TABS = List.of(TAB_JIAN, TAB_DAO, TAB_QIANG, TAB_KAPAK, TAB_GADA, TAB_KIPAS_BESI,
				TAB_KEBUT, TAB_ZIRAH, TAB_PESAN);

		public static final String TAB_ENTRI = "tab";
		public static final String KELAS_DUEL = "kelas_duel";
		public static final String STAT = "stat";
		public static final String STAT_KUNCI = "kunci";
		public static final String BANDING = "banding";
		public static final String LAYANAN = "layanan";
		public static final String PERBAIKI = "perbaiki";
		public static final String NILAI_SISA = "nilai_sisa";
		public static final String PESAN_TEMPA = "pesan";
		public static final String BENTUK = "bentuk";
		public static final String MUTU_MAKS = "mutu_maks";
		public static final String PESANAN_MAKS = "pesanan_maks";
		public static final String KUTIPAN = "kutipan";
		public static final String BAHAN = "bahan";
		public static final String PERLU = "perlu";
		public static final String PUNYA = "punya";
		public static final String ONGKOS = "ongkos";
		public static final String HARI_JADI = "hari_jadi";
		public static final String PESANAN = "pesanan";
		public static final String SIAP = "siap";

		public static final String STAT_SERANGAN = "serangan";
		public static final String STAT_KECEPATAN = "kecepatan";
		public static final String STAT_DAYA_TAHAN = "daya_tahan";
		public static final String STAT_JANGKAUAN = "jangkauan";
		public static final String STAT_ZIRAH = "zirah";

		public static final String OP_BELI = "beli";
		public static final String OP_KUTIP = "kutip";
		public static final String OP_PESAN = "pesan";
		public static final String OP_AMBIL = "ambil";
		public static final String OP_PERBAIKI = "perbaiki";
		public static final String OP_NILAI = "nilai";

		private TokoSenjata() {
		}
	}

	/**
	 * {@code lelang} (38.U.3.3) — town Balai Lelang and the capital's Lelang Agung (one engine, {@code BalaiLelang} +
	 * {@code ProfilLelang}). Built by 38EK. Style {@link #GAYA_KAIN}. Live: refreshed with {@link TempatLayar#segarkan} on
	 * every bid and lot change (viewers only).
	 * <pre>
	 * header    uang
	 * profil    S   PROFIL_KOTA | PROFIL_AGUNG       status  S  S_BELUM | S_BERJALAN | S_JEDA | S_SELESAI
	 * sisa_tick L   until start (belum), lot end (berjalan) or next lot (jeda)
	 * jadwal    T   "Lelang Agung hari 5, 12.00" (belum)
	 * lot       C   no I, total I, nama T, barang BR, penjual T, dasar L, kini L, pemimpin T, anda B (player leads),
	 *               misteri B (closed box), terjual B (red seal "TERJUAL" spins in), sisa_tick L
	 * tawaran   D   nama T, nb L, anda B, baru B (newest first, at most 8; baru flashes gold 400 ms)
	 * langkah   IA  bid steps in percent [5, 10, 25]  (Space = first step)
	 * tawar_min L   lowest valid next bid               komisi  F  seller commission (0 town, 0.10 Agung)
	 * titip     C   maks I, bisa B, alasan T, tangan C [ nama T, barang BR, perkiraan L ],
	 *               daftar D [ id S, nama T, barang BR, dasar L, status S (T_ANTRE|T_DILELANG|T_TERJUAL|T_KEMBALI), hasil L ]
	 * hasil     D   nama T, nb L, jenis S (H_MENANG|H_TITIP|H_UTANG), hari I     utang L  owed to the player
	 * </pre>
	 * Ops: {@code tawar:PERSEN}, {@code titip:DASAR}, {@code tarik:ID}, {@code ambil_hasil}.
	 */
	public static final class Lelang {
		public static final String LAYAR = "lelang";
		public static final String TAB_LOT = "lot";
		public static final String TAB_TITIP = "titip";
		public static final String TAB_HASIL = "hasil";
		public static final List<String> TABS = List.of(TAB_LOT, TAB_TITIP, TAB_HASIL);

		public static final String PROFIL = "profil";
		public static final String PROFIL_KOTA = "kota";
		public static final String PROFIL_AGUNG = "agung";
		public static final String S_BELUM = "belum";
		public static final String S_BERJALAN = "berjalan";
		public static final String S_JEDA = "jeda";
		public static final String S_SELESAI = "selesai";
		public static final String JADWAL = "jadwal";
		public static final String LOT = "lot";
		public static final String TOTAL = "total";
		public static final String PENJUAL = "penjual";
		public static final String DASAR = "dasar";
		public static final String KINI = "kini";
		public static final String PEMIMPIN = "pemimpin";
		public static final String MISTERI = "misteri";
		public static final String TERJUAL = "terjual";
		public static final String TAWARAN = "tawaran";
		public static final String LANGKAH = "langkah";
		public static final String TAWAR_MIN = "tawar_min";
		public static final String KOMISI = "komisi";
		public static final String TITIP = "titip";
		public static final String PERKIRAAN = "perkiraan";
		public static final String HASIL = "hasil";
		public static final String UTANG = "utang";
		public static final String T_ANTRE = "antre";
		public static final String T_DILELANG = "dilelang";
		public static final String T_TERJUAL = "terjual";
		public static final String T_KEMBALI = "kembali";
		public static final String H_MENANG = "menang";
		public static final String H_TITIP = "titip";
		public static final String H_UTANG = "utang";

		public static final String OP_TAWAR = "tawar";
		public static final String OP_TITIP = "titip";
		public static final String OP_TARIK = "tarik";
		public static final String OP_AMBIL_HASIL = "ambil_hasil";

		private Lelang() {
		}
	}

	/**
	 * {@code gilda_misi} (38.U.3.4) — Gilda Kultivator board (E-S), town boards (E-C) and post copies (E-D): one board
	 * system ({@code PapanMisi} + {@code MisiGilda}). Built by 38EK. Style {@link #GAYA_BATU}.
	 * <pre>
	 * lencana       I  badge stars 0-9          poin I  guild points    poin_berikut I  points for the next star
	 * misi_hari_ini I  taken today              misi_maks I (3-5)
	 * papan   D  id S, pangkat S (PANGKAT_E..S), jenis S (buru, kemah, herba, kawal, binatang, sarang, begal, kafilah,
	 *            gua, kurir, tawanan, naga_muda), judul T, cerita T (2-3 sentences), syarat T ("Pangkat C · Fondasi+"),
	 *            imbalan_nb L, imbalan_nama_baik I, imbalan_poin I, peluang_barang F, waktu_hari I,
	 *            arah T ("±640 b barat"), x I, z I, terkunci B, alasan T
	 * aktif   D  id S, pangkat S, judul T, teks T (stage), tahap I, tahap_total I, sisa_tick L, x I, z I, bisa_serah B
	 * naga    D  peringkat I, nama T, sekte T, skor I, anda B (Daftar Naga Muda, 50 rows)    naga_anda I (0 = unranked)
	 * ujian   C  target I (next exam star 3/6/9), syarat D [ teks T, ok B ], biaya L, penguji T, bisa B, alasan T
	 * </pre>
	 * Ops: {@code terima:ID}, {@code serah:ID}, {@code batal:ID}, {@code ujian}, {@code tandai:X:Z}.
	 */
	public static final class GildaMisi {
		public static final String LAYAR = "gilda_misi";
		public static final String TAB_PAPAN = "papan";
		public static final String TAB_AKTIF = "aktif";
		public static final String TAB_NAGA = "naga";
		public static final String TAB_UJIAN = "ujian";
		public static final List<String> TABS = List.of(TAB_PAPAN, TAB_AKTIF, TAB_NAGA, TAB_UJIAN);

		public static final String LENCANA = "lencana";
		public static final String POIN = "poin";
		public static final String POIN_BERIKUT = "poin_berikut";
		public static final String MISI_HARI_INI = "misi_hari_ini";
		public static final String MISI_MAKS = "misi_maks";
		public static final String PAPAN = "papan";
		public static final String PANGKAT = "pangkat";
		public static final String CERITA = "cerita";
		public static final String IMBALAN_NB = "imbalan_nb";
		public static final String IMBALAN_NAMA_BAIK = "imbalan_nama_baik";
		public static final String IMBALAN_POIN = "imbalan_poin";
		public static final String PELUANG_BARANG = "peluang_barang";
		public static final String WAKTU_HARI = "waktu_hari";
		public static final String ARAH = "arah";
		public static final String BISA_SERAH = "bisa_serah";
		public static final String NAGA = "naga";
		public static final String PERINGKAT = "peringkat";
		public static final String NAGA_ANDA = "naga_anda";
		public static final String PENGUJI = "penguji";

		public static final String PANGKAT_E = "E";
		public static final String PANGKAT_D = "D";
		public static final String PANGKAT_C = "C";
		public static final String PANGKAT_B = "B";
		public static final String PANGKAT_A = "A";
		public static final String PANGKAT_S = "S";
		public static final List<String> SEMUA_PANGKAT = List.of(PANGKAT_E, PANGKAT_D, PANGKAT_C, PANGKAT_B, PANGKAT_A, PANGKAT_S);

		public static final String OP_TERIMA = "terima";
		public static final String OP_SERAH = "serah";
		public static final String OP_BATAL = "batal";
		public static final String OP_UJIAN = "ujian";

		private GildaMisi() {
		}
	}

	/**
	 * {@code bank} (38.U.3.5) — Rumah Uang Seribu Tael. Built by 38EK ({@code RumahUang}, {@code SuratWesel}, {@code Gadai},
	 * {@code Brankas}). Style {@link #GAYA_KAIN}.
	 * <pre>
	 * header       uang, batu
	 * saldo        L   deposit                 biaya_tarik F  withdrawal fee (0.01, min 1 NB)
	 * riwayat      D   hari I, nb L (signed), catatan T (newest first)
	 * wesel        C   biaya F (0.02), tangan C [ nama T, nb L ] (a note in hand that can be cashed)
	 * gadai        C   pinjam F (0.40), tebus F (0.46), hari I (7),
	 *                  tangan C [ nama T, barang BR, nilai L, pinjaman L ],
	 *                  daftar D [ id S, nama T, barang BR, pinjaman L, tebus_nb L, sisa_hari I ]
	 * tukar        C   beli L (180 NB per Batu Asal), jual L (90), beli_sisa I (this week), jual_sisa I (today),
	 *                  boleh_beli B, alasan T, batu_asal I (in bag)
	 * brankas      C   slot I (9 or 18), daftar D [ slot I, nama T, barang BR ]
	 * </pre>
	 * Ops: {@code setor}, {@code tarik:NB}, {@code wesel:NB}, {@code cair}, {@code gadai}, {@code tebus:ID},
	 * {@code tukar_beli:N}, {@code tukar_jual:N}, {@code simpan} (item in hand), {@code ambil:SLOT}.
	 */
	public static final class Bank {
		public static final String LAYAR = "bank";
		public static final String TAB_SIMPAN = "simpan";
		public static final String TAB_WESEL = "wesel";
		public static final String TAB_GADAI = "gadai";
		public static final String TAB_TUKAR = "tukar";
		public static final String TAB_BRANKAS = "brankas";
		public static final List<String> TABS = List.of(TAB_SIMPAN, TAB_WESEL, TAB_GADAI, TAB_TUKAR, TAB_BRANKAS);

		public static final String SALDO = "saldo";
		public static final String BIAYA_TARIK = "biaya_tarik";
		public static final String WESEL = "wesel";
		public static final String GADAI = "gadai";
		public static final String PINJAM = "pinjam";
		public static final String TEBUS = "tebus";
		public static final String PINJAMAN = "pinjaman";
		public static final String TEBUS_NB = "tebus_nb";
		public static final String TUKAR = "tukar";
		public static final String BELI = "beli";
		public static final String JUAL = "jual";
		public static final String BELI_SISA = "beli_sisa";
		public static final String JUAL_SISA = "jual_sisa";
		public static final String BOLEH_BELI = "boleh_beli";
		public static final String BATU_ASAL = "batu_asal";
		public static final String BRANKAS = "brankas";

		public static final String OP_SETOR = "setor";
		public static final String OP_TARIK = "tarik";
		public static final String OP_WESEL = "wesel";
		public static final String OP_CAIR = "cair";
		public static final String OP_GADAI = "gadai";
		public static final String OP_TEBUS = "tebus";
		public static final String OP_TUKAR_BELI = "tukar_beli";
		public static final String OP_TUKAR_JUAL = "tukar_jual";
		public static final String OP_SIMPAN = "simpan";
		public static final String OP_AMBIL = "ambil";

		private Bank() {
		}
	}

	/**
	 * {@code buku_kas} (38.U.3.6) — sect ledger; hub SEKTE card "Buku Kas" / "Lapak &amp; Kafilah" ({@code npcId -1}, read
	 * only, {@link TempatLayar#daftarHub}), the capital's Record Office NPC, and the stall counter anchor
	 * ({@link TempatLayar#bukaDi}). Built by 38EK ({@code BukuKas}, {@code LapakSekte}, {@code KafilahSekte}). Style
	 * {@link #GAYA_KERTAS}. PK and Batu are <b>always</b> two separate columns with their own icons.
	 * <pre>
	 * hari          I   today
	 * baris         D   hari I, sumber S (SUMBER_*), nama T, pk I (signed, 0 = none), nb L (signed), catatan T
	 *                   ("pajak −11", "berjalan, 1 hari")
	 * bersih_pk     I   today's net PK          bersih_nb L  today's net NB
	 * grafik_pk     IA  7 days, oldest first    grafik_nb LA 7 days, oldest first (sparkline)
	 * minggu        D   hari I, pk I, nb L (7 rows)
	 * sumber_daftar D   sumber S, nama T, pk I, nb L (7-day totals), catatan T (cap, "maks 240 NB/hari")
	 * lapak         C   ada B, alasan T (hall level below 2), kios T, sewa L, sewa_sisa_hari I, penjaga T (disciple),
	 *                   kas L (uncollected), volume_maks L,
	 *                   daftar D (18) [ slot I, nama T, barang BR, harga L, nilai L (Harga.value), peluang I (80/50/20/0 %) ]
	 * kafilah       C   maks I, alasan T, laba_minggu L, laba_batas L,
	 *                   daftar D [ id S, tujuan T, muatan D [ nama T, barang BR, jumlah I ], pengawal D [ nama T ],
	 *                     risiko I (% per day), laba_kira L, sisa_hari I, kemajuan F, ax az tx tz I (mini map) ],
	 *                   rencana C [ tujuan D [ id S, nama T, jarak I, hari I, permintaan F ],
	 *                     murid D [ id S (uuid), nama T, ranah T ], muatan D [ id S, nama T, barang BR, nilai L ] ]
	 * </pre>
	 * Ops (Record Office NPC or stall anchor only; the hub only reads): {@code lapak_sewa}, {@code lapak_isi} (item in
	 * hand), {@code lapak_harga:SLOT:NB}, {@code lapak_ambil:SLOT}, {@code lapak_kas},
	 * {@code kafilah_kirim:TUJUAN:M1.M2:S1.S2.S3} (indexes into {@code rencana} lists, '.'-separated).
	 */
	public static final class BukuKas {
		public static final String LAYAR = "buku_kas";
		public static final String TAB_HARI_INI = "hari_ini";
		public static final String TAB_MINGGU = "minggu";
		public static final String TAB_SUMBER = "sumber";
		public static final String TAB_LAPAK = "lapak";
		public static final String TAB_KAFILAH = "kafilah";
		public static final List<String> TABS = List.of(TAB_HARI_INI, TAB_MINGGU, TAB_SUMBER, TAB_LAPAK, TAB_KAFILAH);

		public static final String BARIS = "baris";
		public static final String SUMBER = "sumber";
		public static final String BERSIH_PK = "bersih_pk";
		public static final String BERSIH_NB = "bersih_nb";
		public static final String GRAFIK_PK = "grafik_pk";
		public static final String GRAFIK_NB = "grafik_nb";
		public static final String MINGGU = "minggu";
		public static final String SUMBER_DAFTAR = "sumber_daftar";
		public static final String LAPAK = "lapak";
		public static final String ADA = "ada";
		public static final String KIOS = "kios";
		public static final String SEWA = "sewa";
		public static final String SEWA_SISA_HARI = "sewa_sisa_hari";
		public static final String PENJAGA = "penjaga";
		public static final String KAS = "kas";
		public static final String VOLUME_MAKS = "volume_maks";
		public static final String KAFILAH = "kafilah";
		public static final String LABA_MINGGU = "laba_minggu";
		public static final String LABA_BATAS = "laba_batas";
		public static final String MUATAN = "muatan";
		public static final String PENGAWAL = "pengawal";
		public static final String LABA_KIRA = "laba_kira";
		public static final String ASAL_X = "ax";
		public static final String ASAL_Z = "az";
		public static final String TUJUAN_X = "tx";
		public static final String TUJUAN_Z = "tz";
		public static final String RENCANA = "rencana";
		public static final String MURID = "murid";
		public static final String RANAH = "ranah";
		public static final String PERMINTAAN = "permintaan";

		/** {@code sumber} ids of 38.M.4 (lang key {@code kultivator.tempat_ekonomi.kas.<id>}, 38EK). */
		public static final String SUMBER_TAMBANG = "tambang";
		public static final String SUMBER_KEBUN = "kebun";
		public static final String SUMBER_UPETI = "upeti";
		public static final String SUMBER_LAPAK = "lapak";
		public static final String SUMBER_KAFILAH = "kafilah";
		public static final String SUMBER_TURNAMEN = "turnamen";
		public static final String SUMBER_DIPLOMASI = "diplomasi";
		public static final String SUMBER_LUMBUNG = "lumbung";
		public static final String SUMBER_MISI = "misi";
		public static final String SUMBER_SEWA = "sewa";
		public static final String SUMBER_ONGKOS = "ongkos";
		public static final String SUMBER_GAJI = "gaji";
		public static final String SUMBER_UJIAN = "ujian";
		public static final String SUMBER_TAGIHAN = "tagihan";
		public static final String SUMBER_KRU = "kru";

		public static final String OP_LAPAK_SEWA = "lapak_sewa";
		public static final String OP_LAPAK_ISI = "lapak_isi";
		public static final String OP_LAPAK_HARGA = "lapak_harga";
		public static final String OP_LAPAK_AMBIL = "lapak_ambil";
		public static final String OP_LAPAK_KAS = "lapak_kas";
		public static final String OP_KAFILAH_KIRIM = "kafilah_kirim";

		private BukuKas() {
		}
	}

	/**
	 * {@code tagihan} (38.U.3.7) — damage bill. Built by 38PR ({@code Tagihan}, {@code Takut}). Style {@link #GAYA_KERTAS},
	 * narrow panel. Three variants:
	 * <pre>
	 * required  npc C (the collector), varian S (V_BIASA | V_TAKUT | V_DONASI), tagihan_id S
	 * dari      T   "Tagihan dari Kepala Desa Kali Putih"     kalimat T  the collector's line
	 * biasa:    rincian D [ nama T, barang BR (block icon, optional), jumlah I, subtotal F (NB, one decimal) ],
	 *           upah F, upah_persen I (30), total L, akibat_bayar T, akibat_tolak T, sudah_tawar B
	 * takut:    no numbers; npc.takut true (kneeling portrait), takut I (place's fear meter 0-100)
	 * donasi:   donasi C [ kemajuan F, perlu D [ nama T, barang BR, jumlah I, ada I ], min L, bantu B ]
	 * </pre>
	 * Ops: biasa {@code bayar}, {@code tawar}, {@code tolak}; takut {@code bayar_rela}, {@code pergi}; donasi
	 * {@code donasi:NB}, {@code antar} (materials in hand), {@code bantu}.
	 */
	public static final class Tagihan {
		public static final String LAYAR = "tagihan";

		public static final String VARIAN = "varian";
		public static final String V_BIASA = "biasa";
		public static final String V_TAKUT = "takut";
		public static final String V_DONASI = "donasi";
		public static final String TAGIHAN_ID = "tagihan_id";
		public static final String DARI = "dari";
		public static final String KALIMAT = "kalimat";
		public static final String RINCIAN = "rincian";
		public static final String SUBTOTAL = "subtotal";
		public static final String UPAH = "upah";
		public static final String UPAH_PERSEN = "upah_persen";
		public static final String TOTAL = "total";
		public static final String AKIBAT_BAYAR = "akibat_bayar";
		public static final String AKIBAT_TOLAK = "akibat_tolak";
		public static final String SUDAH_TAWAR = "sudah_tawar";
		public static final String TAKUT_METER = "takut";
		public static final String DONASI = "donasi";
		public static final String PERLU = "perlu";
		public static final String ADA = "ada";
		public static final String MIN = "min";
		public static final String BANTU = "bantu";

		public static final String OP_BAYAR = "bayar";
		public static final String OP_TAWAR = "tawar";
		public static final String OP_TOLAK = "tolak";
		public static final String OP_BAYAR_RELA = "bayar_rela";
		public static final String OP_PERGI = "pergi";
		public static final String OP_DONASI = "donasi";
		public static final String OP_ANTAR = "antar";
		public static final String OP_BANTU = "bantu";

		private Tagihan() {
		}
	}

	/**
	 * {@code peta_kota} (38.U.3.8) — capital map and directory; hub DUNIA card "Kota Besar" (read only,
	 * {@link TempatLayar#daftarHub}) and the city guide NPC. Built by 38KS. Style {@link #GAYA_GIOK}.
	 * <pre>
	 * tempat          T   city name
	 * distrik_daftar  D   id S (DISTRIK_*), nama T, gx I, gz I (0-2 on the 3 x 3 grid; -1 for outer), luar B,
	 *                     layanan D [ nama T, ikon I, catatan T ("lelang hari 5") ]
	 * pos_x pos_z     F   player in grid units 0..3 (absent: outside the walls)    distrik_pemain S
	 * layanan         D   id S (screen id), nama T, ikon I, distrik S, catatan T (hours), buka B, x I, z I
	 * tokoh           D   id S, nama T, gelar T, catatan T (where now, by schedule), distrik S, kenal B
	 * acara           D   id S, nama T, hari I, sisa_tick L, aktif B, catatan T (hours)
	 * </pre>
	 * Ops: {@code tandai:X:Z}.
	 */
	public static final class PetaKota {
		public static final String LAYAR = "peta_kota";
		public static final String TAB_PETA = "peta";
		public static final String TAB_LAYANAN = "layanan";
		public static final String TAB_TOKOH = "tokoh";
		public static final String TAB_ACARA = "acara";
		public static final List<String> TABS = List.of(TAB_PETA, TAB_LAYANAN, TAB_TOKOH, TAB_ACARA);

		public static final String DISTRIK_DAFTAR = "distrik_daftar";
		public static final String GX = "gx";
		public static final String GZ = "gz";
		public static final String LUAR = "luar";
		public static final String LAYANAN = "layanan";
		public static final String POS_X = "pos_x";
		public static final String POS_Z = "pos_z";
		public static final String DISTRIK_PEMAIN = "distrik_pemain";
		public static final String BUKA = "buka";
		public static final String TOKOH = "tokoh";
		public static final String KENAL = "kenal";
		public static final String ACARA = "acara";

		private PetaKota() {
		}
	}

	/**
	 * {@code teleport} (38.U.3.9, 38.B.4.3) — formation station and post discs. Built by 38KS ({@code Teleport}). Style
	 * {@link #GAYA_GIOK}.
	 * <pre>
	 * header          uang
	 * tujuan          D   id S (site id), nama T, jenis S (site kind), ikon I, jarak I, harga L, x I, z I, keadaan S,
	 *                     terkunci B, alasan T ("Temukan dulu", "Ditolak")
	 * pengikut        I   disciples coming along (max 4)     harga_pengikut L  cost per disciple
	 * gratis_sisa     I   free teleports left today          buronan B         wanted: every row refused
	 * kanal_detik     I   channel time (3 s, HUD)
	 * </pre>
	 * Ops: {@code berangkat:ID}.
	 */
	public static final class Teleport {
		public static final String LAYAR = "teleport";

		public static final String PENGIKUT = "pengikut";
		public static final String HARGA_PENGIKUT = "harga_pengikut";
		public static final String GRATIS_SISA = "gratis_sisa";
		public static final String BURONAN = "buronan";
		public static final String KANAL_DETIK = "kanal_detik";

		public static final String OP_BERANGKAT = "berangkat";

		private Teleport() {
		}
	}

	/**
	 * {@code dermaga} (38.U.3.9, 38.B.4.3) — Sky Dock, Perjalanan Awan. Built by 38KS. Style {@link #GAYA_GIOK}.
	 * <pre>
	 * header       uang
	 * tujuan       D   as Teleport, plus durasi I (scene seconds, skippable), langit B (allied sky palace dock, 37)
	 * buka         B   day 1000-11000 only        catatan T  hours / "Tutup malam"
	 * tamu_langit  D   nama T, hari I (sky guests of 37)
	 * entitas      S   ship model shown in Pratinjau3D
	 * </pre>
	 * Ops: {@code berangkat:ID}.
	 */
	public static final class Dermaga {
		public static final String LAYAR = "dermaga";

		public static final String DURASI = "durasi";
		public static final String LANGIT = "langit";
		public static final String BUKA = "buka";
		public static final String TAMU_LANGIT = "tamu_langit";

		public static final String OP_BERANGKAT = "berangkat";

		private Dermaga() {
		}
	}

	/**
	 * {@code pasar_binatang} (38.U.3.9) — spirit beast market. Built by 38EK ({@code PasarBinatang}). Style
	 * {@link #GAYA_KAYU}.
	 * <pre>
	 * header    uang
	 * kandang   D   id S, nama T, entitas S (rotating 3D preview), harga L, stok I, keadaan S, alasan T, sisa_hari I
	 * sesaji    D   cards: id S, nama T, barang BR, harga L, stok I, keadaan S
	 * binatang  D   id S (beast uuid), nama T, tier I, harga L (trainer fee), sudah B (trained today)
	 * inti      D   cards: id S, nama T, barang BR, harga L, jual B (the market buys it), keadaan S
	 * </pre>
	 * Ops: {@code beli:ID}, {@code latih:UUID}, {@code jual:ID}.
	 */
	public static final class PasarBinatang {
		public static final String LAYAR = "pasar_binatang";
		public static final String TAB_KANDANG = "kandang";
		public static final String TAB_SESAJI = "sesaji";
		public static final String TAB_PELATIH = "pelatih";
		public static final String TAB_INTI = "inti";
		public static final List<String> TABS = List.of(TAB_KANDANG, TAB_SESAJI, TAB_PELATIH, TAB_INTI);

		public static final String KANDANG = "kandang";
		public static final String SESAJI = "sesaji";
		public static final String BINATANG = "binatang";
		public static final String TIER = "tier";
		public static final String SUDAH = "sudah";
		public static final String INTI = "inti";
		public static final String JUAL = "jual";

		public static final String OP_BELI = "beli";
		public static final String OP_LATIH = "latih";
		public static final String OP_JUAL = "jual";

		private PasarBinatang() {
		}
	}

	/**
	 * {@code judi_batu} (38.U.3.9, 38.M.2.4) — stone gambling (EV 0.88). Built by 38EK ({@code JudiBatu}). Style
	 * {@link #GAYA_BATU}.
	 * <pre>
	 * header            uang
	 * rak               D   id S, nama T, tingkat S (TINGKAT_*), barang BR, harga L, diperiksa B,
	 *                       warna S (WARNA_*, only when diperiksa), keadaan S, alasan T
	 * sisa              I   stones left today (of 10)       purba_sisa I  Batu Purba left today (of 1)
	 * mata_batin_sisa   I   Mata Batin uses left (of 3)     mata_batin_boleh B (band 4+)
	 * riwayat           D   nama T, hasil S (HASIL_*), nilai L (today, newest first)
	 * potong            C   last cut: id S, hasil S, nama T, barang BR, nilai L (a new id plays the 16-tick split)
	 * </pre>
	 * Ops: {@code beli:ID}, {@code periksa:ID}, {@code potong:ID}.
	 */
	public static final class JudiBatu {
		public static final String LAYAR = "judi_batu";

		public static final String RAK = "rak";
		public static final String TINGKAT = "tingkat";
		public static final String DIPERIKSA = "diperiksa";
		public static final String WARNA = "warna";
		public static final String SISA = "sisa";
		public static final String PURBA_SISA = "purba_sisa";
		public static final String MATA_BATIN_SISA = "mata_batin_sisa";
		public static final String MATA_BATIN_BOLEH = "mata_batin_boleh";
		public static final String HASIL = "hasil";
		public static final String POTONG = "potong";

		public static final String TINGKAT_KERIKIL = "kerikil";
		public static final String TINGKAT_BONGKAH = "bongkah";
		public static final String TINGKAT_GUNUNG = "gunung";
		public static final String TINGKAT_PURBA = "purba";
		public static final String WARNA_ABU = "abu";
		public static final String WARNA_BIRU = "biru";
		public static final String WARNA_EMAS = "emas";
		public static final String HASIL_KOSONG = "kosong";
		public static final String HASIL_KECIL = "kecil";
		public static final String HASIL_SEDANG = "sedang";
		public static final String HASIL_BESAR = "besar";
		public static final String HASIL_JACKPOT = "jackpot";

		public static final String OP_BELI = "beli";
		public static final String OP_PERIKSA = "periksa";
		public static final String OP_POTONG = "potong";

		private JudiBatu() {
		}
	}

	/**
	 * {@code penginapan} / {@code rumah_makan} / {@code jasa} (38.U.3.9, 38.M.2.2) — inn rooms, dishes, and the services
	 * healer / temple incense / library / news archive. Built by 38EK ({@code Penginapan}, {@code Hidangan}, {@code Jasa})
	 * and 38KS (temple, library, healer of the capital). Style {@link #GAYA_KAYU} (inn, food) or {@link #GAYA_GIOK} (jasa).
	 * <pre>
	 * header          uang
	 * jenis           S   JENIS_* (what the jasa screen offers; inn and food use their own)
	 * menu            D   id S, nama T, ikon I, barang BR (optional), harga L, efek T, keadaan S, alasan T
	 *                     (healer rows for disciples use id "murid.UUID")
	 * aktif           D   nama T, sisa_tick L (effects running: "Istirahat Nyenyak", "Restu Kuil")
	 * sudah_menginap  B   already slept here tonight
	 * </pre>
	 * Ops: {@code pesan:ID}.
	 */
	public static final class Layanan {
		public static final String LAYAR_PENGINAPAN = "penginapan";
		public static final String LAYAR_RUMAH_MAKAN = "rumah_makan";
		public static final String LAYAR_JASA = "jasa";

		public static final String MENU = "menu";
		public static final String EFEK = "efek";
		public static final String SUDAH_MENGINAP = "sudah_menginap";

		public static final String JENIS_PENGINAPAN = "penginapan";
		public static final String JENIS_RUMAH_MAKAN = "rumah_makan";
		public static final String JENIS_TABIB = "tabib";
		public static final String JENIS_KUIL = "kuil";
		public static final String JENIS_PERPUSTAKAAN = "perpustakaan";
		public static final String JENIS_ARSIP = "arsip";

		public static final String OP_PESAN = "pesan";

		private Layanan() {
		}
	}

	/**
	 * {@code yamen} (38.U.3.9, 38.B.5.2) — magistrate: crime record, fines, surrender, confiscated goods. Built by 38KS
	 * ({@code Hukum}). Style {@link #GAYA_BATU}.
	 * <pre>
	 * header   uang, tempat
	 * poin     I   crime points 0-200            status S  ST_BERSIH | ST_DICURIGAI | ST_BURONAN | ST_BURONAN_BERAT
	 * denda    L   fine to clear the record      catatan T  what happens when paying / surrendering
	 * sitaan   D   cards: id S, nama T, barang BR, harga L (redeem price)
	 * sel      C   sisa_tick L (present while in a cell: big countdown, nothing else)
	 * </pre>
	 * Ops: {@code denda}, {@code serah}, {@code tebus:ID}.
	 */
	public static final class Yamen {
		public static final String LAYAR = "yamen";

		public static final String POIN = "poin";
		public static final String DENDA = "denda";
		public static final String SITAAN = "sitaan";
		public static final String SEL = "sel";
		public static final String ST_BERSIH = "bersih";
		public static final String ST_DICURIGAI = "dicurigai";
		public static final String ST_BURONAN = "buronan";
		public static final String ST_BURONAN_BERAT = "buronan_berat";

		public static final String OP_DENDA = "denda";
		public static final String OP_SERAH = "serah";
		public static final String OP_TEBUS = "tebus";

		private Yamen() {
		}
	}

	/**
	 * {@code acara} (38.U.3.9, 38.B.6) — festival / contest boards. Built by 38KS (calendar) with 38EK scores. Style
	 * {@link #GAYA_KAIN}. Live: refreshed with {@link TempatLayar#segarkan} while running.
	 * <pre>
	 * acara       S   event id         jenis S (ACARA_*)        status S (Lelang.S_BELUM | S_BERJALAN | S_SELESAI)
	 * sisa_tick   L   until start or end              biaya L (entry fee)     terdaftar B
	 * jadwal      D   nama T, hari I, catatan T (hours)
	 * skor        D   nama T, skor I, anda B, geser I (rank change since the last sheet; + = up, PapanSkor slide)
	 * bracket     D   babak I, a T, b T, menang I (-1 open, 0 a, 1 b), anda B
	 * teka        C   id S, soal T, jawaban D [ teks T ] (3), sisa I (riddles left, max 3 per festival)
	 * ringkasan   T   result summary of an event that ran as data (no player near)
	 * catatan     T   note: the bare calendar's "no event" line, or the palace hall's season note
	 * npc         C   speaker card (header NPC keys); drawn as the audience portrait in the palace
	 * </pre>
	 * Without {@code status} the head row (status, countdown, Tonton, Daftar) is left out. {@code acara} =
	 * {@link #ACARA_ISTANA}: palace audience / court orders (38KS {@code Istana}): the left column is the audience hall
	 * (speaker portrait under a canopy, {@code catatan}), {@code teka} id {@code audiensi} / {@code perintah} holds the
	 * spoken line and the three replies, {@code jadwal} rows are the season's orders (nama, catatan = state).
	 * Ops: {@code daftar}, {@code jawab:ID:N}, {@code tonton}.
	 */
	public static final class Acara {
		public static final String LAYAR = "acara";

		public static final String ACARA_ID = "acara";
		public static final String TERDAFTAR = "terdaftar";
		public static final String JADWAL = "jadwal";
		public static final String GESER = "geser";
		public static final String BRACKET = "bracket";
		public static final String BABAK = "babak";
		public static final String A = "a";
		public static final String B = "b";
		public static final String MENANG = "menang";
		public static final String TEKA = "teka";
		public static final String SOAL = "soal";
		public static final String JAWABAN = "jawaban";
		public static final String SISA = "sisa";
		public static final String RINGKASAN = "ringkasan";

		public static final String ACARA_PASAR_MALAM = "pasar_malam";
		public static final String ACARA_PERTEMUAN_PIL = "pertemuan_pil";
		public static final String ACARA_LELANG_AGUNG = "lelang_agung";
		public static final String ACARA_HARI_GERBANG = "hari_gerbang";
		public static final String ACARA_TURNAMEN = "turnamen_agung";
		public static final String ACARA_FESTIVAL = "festival_lentera";
		public static final String ACARA_PERTEMUAN_SEKTE = "pertemuan_sekte";
		public static final String ACARA_PERTAHANAN = "gelombang_binatang";
		/** The palace audience and court orders (value of 38KS {@code Istana.ID}). */
		public static final String ACARA_ISTANA = "istana";

		public static final String OP_DAFTAR = "daftar";
		public static final String OP_JAWAB = "jawab";
		public static final String OP_TONTON = "tonton";

		private Acara() {
		}
	}

	/**
	 * {@code lapak_rekrut} (38.U.3.9, 38.M.4.3) — recruitment stall on Gate Opening Day. Built by 38KS ({@code HariGerbang}).
	 * Style {@link #GAYA_KAYU}. {@code kunci_layar} when the hall level is below 2.
	 * <pre>
	 * header       uang
	 * calon        D   id S, nama T, potret I (entity id or -1), akar T (spirit root), bakat T (short talent),
	 *                  peluang I (% to accept), keadaan S, alasan T
	 * undang_sisa  I   invitations left today (of 3)    sewa L    sudah_sewa B
	 * </pre>
	 * Ops: {@code sewa}, {@code undang:ID}.
	 */
	public static final class LapakRekrut {
		public static final String LAYAR = "lapak_rekrut";

		public static final String CALON = "calon";
		public static final String AKAR = "akar";
		public static final String BAKAT = "bakat";
		public static final String UNDANG_SISA = "undang_sisa";
		public static final String SEWA = "sewa";
		public static final String SUDAH_SEWA = "sudah_sewa";

		public static final String OP_SEWA = "sewa";
		public static final String OP_UNDANG = "undang";

		private LapakRekrut() {
		}
	}

	/**
	 * {@code jurnal} (38.U.3.9, 38.S.1) — Catatan Perjalanan; hub DUNIA card (read only, {@link TempatLayar#daftarHub}).
	 * Built by 38ST ({@code QuestTracker}, {@code CeritaData}). Style {@link #GAYA_KERTAS}.
	 * <pre>
	 * aktif    D   id S, nama T, tempat T, teks T (stage), tahap I, tahap_total I, x I, z I, ada_arah B,
	 *              sisa_tick L (deadline; absent = none)
	 * selesai  D   id S, nama T, tempat T, hari I, hasil T (branch taken)
	 * alur     D   bab I, judul T, ringkasan T, terbuka B (capital storyline chapters)
	 * lacak    S   id of the tracked quest (compass needle)
	 * </pre>
	 * Ops: {@code lacak:ID}, {@code tandai:X:Z} (both read-only).
	 */
	public static final class Jurnal {
		public static final String LAYAR = "jurnal";
		public static final String TAB_AKTIF = "aktif";
		public static final String TAB_SELESAI = "selesai";
		public static final String TAB_ALUR = "alur";
		public static final List<String> TABS = List.of(TAB_AKTIF, TAB_SELESAI, TAB_ALUR);

		public static final String ADA_ARAH = "ada_arah";
		public static final String SELESAI = "selesai";
		public static final String HASIL = "hasil";
		public static final String ALUR = "alur";
		public static final String BAB = "bab";
		public static final String RINGKASAN = "ringkasan";
		public static final String TERBUKA = "terbuka";
		public static final String LACAK = "lacak";

		private Jurnal() {
		}
	}

	/**
	 * {@code gilda_alkemis} / {@code gilda_tempa} (38.U.3.9) — Alchemist and Forge guilds: tier exams, guild shop, orders,
	 * appraisal. Built by 38EK ({@code GildaAlkemis}, {@code GildaTempa}). Style {@link #GAYA_BATU}.
	 * <pre>
	 * header        uang
	 * tingkat       I   guild tier       tingkat_nama T
	 * ujian         C   target I, syarat D [ teks T, ok B ], biaya L (5 x n²), bisa B, alasan T
	 * toko          D   cards: id S, nama T, barang BR, harga L, stok I, mutu I, keadaan S, terkunci B, alasan T (tier gate)
	 * pesanan       D   as TokoSenjata.PESANAN entries (id, nama, barang, mutu, sisa_tick, siap)
	 * nilai_sisa    I   free appraisals left today
	 * </pre>
	 * Ops: {@code ujian}, {@code beli:ID:N}, {@code ambil:ID}, {@code nilai}.
	 */
	public static final class Gilda {
		public static final String LAYAR_ALKEMIS = "gilda_alkemis";
		public static final String LAYAR_TEMPA = "gilda_tempa";
		public static final String TAB_UJIAN = "ujian";
		public static final String TAB_TOKO = "toko";
		public static final String TAB_PESANAN = "pesanan";
		public static final String TAB_NILAI = "nilai";
		public static final List<String> TABS = List.of(TAB_UJIAN, TAB_TOKO, TAB_PESANAN, TAB_NILAI);

		public static final String TINGKAT = "tingkat";
		public static final String TINGKAT_NAMA = "tingkat_nama";
		public static final String TOKO = "toko";
		public static final String PESANAN = "pesanan";
		public static final String NILAI_SISA = "nilai_sisa";

		public static final String OP_UJIAN = "ujian";
		public static final String OP_BELI = "beli";
		public static final String OP_AMBIL = "ambil";
		public static final String OP_NILAI = "nilai";

		private Gilda() {
		}
	}

	/**
	 * {@code kafilah} (38.U.3.9) — town side of caravans on the roads: escort offers. Built by 38EK ({@code KafilahJalan}).
	 * Style {@link #GAYA_KAYU}.
	 * <pre>
	 * daftar   D   id S, nama T (caravan), asal T, tujuan T, x I, z I (now), kemajuan F, risiko I (%), imbalan L,
	 *              bisa B, alasan T
	 * </pre>
	 * Ops: {@code kawal:ID}.
	 */
	public static final class Kafilah {
		public static final String LAYAR = "kafilah";

		public static final String ASAL = "asal";
		public static final String IMBALAN = "imbalan";

		public static final String OP_KAWAL = "kawal";

		private Kafilah() {
		}
	}

	/** Every core screen id of 38.U.3 (the {@code uji ui layar} command of 38UI walks this list). */
	public static final List<String> SEMUA_LAYAR = List.of(Dialog.LAYAR, TokoSenjata.LAYAR, Lelang.LAYAR, GildaMisi.LAYAR,
			Bank.LAYAR, BukuKas.LAYAR, Tagihan.LAYAR, PetaKota.LAYAR, Teleport.LAYAR, Dermaga.LAYAR, PasarBinatang.LAYAR,
			JudiBatu.LAYAR, Layanan.LAYAR_PENGINAPAN, Layanan.LAYAR_RUMAH_MAKAN, Layanan.LAYAR_JASA, Yamen.LAYAR, Acara.LAYAR,
			LapakRekrut.LAYAR, Jurnal.LAYAR, Gilda.LAYAR_ALKEMIS, Gilda.LAYAR_TEMPA, Kafilah.LAYAR);

	/** Screens that open from a hub card without NPC (read only). */
	public static final List<String> LAYAR_HUB = List.of(BukuKas.LAYAR, PetaKota.LAYAR, Jurnal.LAYAR);

	// ---------------------------------------------------------------------------------------------------------------
	// Icons

	/**
	 * Slots of {@code textures/gui/tempat/ikon.png} (256 x 256, 16 px cells, 16 x 16 grid; slot = row * 16 + column),
	 * drawn by 38UI's {@code tools/gen_tempat_ui.py} in exactly this order (38.U.2 icon list). Builders send these in
	 * {@code ikon}. Slots 112-255 are free for 38UI's own additions (append only); theme screens use their own atlas.
	 */
	public static final class Ikon {
		/** No icon. */
		public static final int TIDAK_ADA = -1;

		// Row 0: money and status glyphs
		public static final int BATU_RENDAH = 0;
		public static final int BATU_MENENGAH = 1;
		public static final int BATU_TINGGI = 2;
		public static final int BATU_AGUNG = 3;
		public static final int NB = 4;
		public static final int PK = 5;
		public static final int BATU_ASAL = 6;
		public static final int TREN_NAIK = 8;
		public static final int TREN_TURUN = 9;
		public static final int TREN_DATAR = 10;
		public static final int GEMBOK = 11;
		public static final int JAM_PASIR = 12;
		public static final int LONCENG = 13;
		public static final int CAP_MERAH = 14;
		public static final int MATA_BATIN = 15;

		// Row 1: guild ranks E-S, scales, caravan
		public static final int PANGKAT_E = 16;
		public static final int TIMBANGAN = 22;
		public static final int KAFILAH = 23;

		// Row 2: badge stars 1-9
		public static final int BINTANG_1 = 32;

		// Row 3: goods
		public static final int JIAN = 48;
		public static final int DAO = 49;
		public static final int QIANG = 50;
		public static final int KAPAK = 51;
		public static final int GADA = 52;
		public static final int KIPAS_BESI = 53;
		public static final int KEBUT = 54;
		public static final int ZIRAH = 55;
		public static final int PIL = 56;
		public static final int HERBA = 57;
		public static final int JIMAT = 58;

		// Row 4: districts, in SEMUA_DISTRIK order
		public static final int DISTRIK_PERTAMA = 64;

		// Rows 5-6: services (20)
		public static final int PENGINAPAN = 80;
		public static final int RUMAH_MAKAN = 81;
		public static final int TOKO_SENJATA = 82;
		public static final int LELANG = 83;
		public static final int GILDA_MISI = 84;
		public static final int BANK = 85;
		public static final int TELEPORT = 86;
		public static final int DERMAGA = 87;
		public static final int PASAR_BINATANG = 88;
		public static final int JUDI_BATU = 89;
		public static final int YAMEN = 90;
		public static final int KUIL = 91;
		public static final int PERPUSTAKAAN = 92;
		public static final int TABIB = 93;
		public static final int MENARA = 94;
		public static final int GILDA_ALKEMIS = 95;
		public static final int GILDA_TEMPA = 96;
		public static final int LAPAK_SEKTE = 97;
		public static final int ISTANA = 98;
		public static final int ARENA = 99;

		/** First slot free for 38UI additions. */
		public static final int BEBAS_PERTAMA = 112;

		/** Rank icon for {@code GildaMisi.PANGKAT_*} ("E".."S"); -1 for an unknown rank. */
		public static int pangkat(String pangkat) {
			int index = GildaMisi.SEMUA_PANGKAT.indexOf(pangkat);
			return index < 0 ? TIDAK_ADA : PANGKAT_E + index;
		}

		/** Star icon 1-9 (clamped). */
		public static int bintang(int n) {
			return BINTANG_1 + Math.max(1, Math.min(9, n)) - 1;
		}

		/** District icon for a {@code DISTRIK_*} id; -1 when unknown. */
		public static int distrik(String distrik) {
			int index = SEMUA_DISTRIK.indexOf(distrik);
			return index < 0 ? TIDAK_ADA : DISTRIK_PERTAMA + index;
		}

		/** Service icon for a screen id of {@link #SEMUA_LAYAR} (dialog and journal have none: -1). */
		public static int layanan(String layar) {
			return switch (layar) {
				case Layanan.LAYAR_PENGINAPAN -> PENGINAPAN;
				case Layanan.LAYAR_RUMAH_MAKAN -> RUMAH_MAKAN;
				case TokoSenjata.LAYAR -> TOKO_SENJATA;
				case Lelang.LAYAR -> LELANG;
				case GildaMisi.LAYAR -> GILDA_MISI;
				case Bank.LAYAR -> BANK;
				case Teleport.LAYAR -> TELEPORT;
				case Dermaga.LAYAR -> DERMAGA;
				case PasarBinatang.LAYAR -> PASAR_BINATANG;
				case JudiBatu.LAYAR -> JUDI_BATU;
				case Yamen.LAYAR -> YAMEN;
				case Layanan.LAYAR_JASA -> KUIL;
				case Gilda.LAYAR_ALKEMIS -> GILDA_ALKEMIS;
				case Gilda.LAYAR_TEMPA -> GILDA_TEMPA;
				case BukuKas.LAYAR, LapakRekrut.LAYAR -> LAPAK_SEKTE;
				case Acara.LAYAR -> LONCENG;
				case Kafilah.LAYAR -> KAFILAH;
				case Tagihan.LAYAR -> CAP_MERAH;
				case PetaKota.LAYAR -> DISTRIK_PERTAMA + 4;
				default -> TIDAK_ADA;
			};
		}

		private Ikon() {
		}
	}

	private LembarKunci() {
	}
}
