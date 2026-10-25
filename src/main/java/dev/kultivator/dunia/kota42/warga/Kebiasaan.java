package dev.kultivator.dunia.kota42.warga;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kultivator.Kultivator;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.cakap.Fakta;
import dev.kultivator.dunia.kota42.cakap.GosipWarga;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.KerjaPeran;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The 32 unique habits of residents (DESIGN 42.4.12). Each habit is a data schedule overrider
 * ({@code data/kultivator/tempat/kebiasaan/<id>.json}) read by {@link PenimpaWarga} before the role's round: hours, the titik
 * it goes to (with fallbacks: a titik the place lacks makes the habit use the next one or not show at all), the clip and
 * props there, alternating clips, group roles (two chess players and their watchers), invited guests (children around the
 * storyteller), weather and season conditions. Its lasting effects live here too ({@link #harian}): health of drinkers and
 * tai chi sleepers, friendships of the chess elders, quarrels of the hot-tempered, gossip, and the factors other modules
 * read ({@link #faktorGosip}, {@link #faktorKelahi}, {@link #faktorAhli}, {@link #faktorTawar}, {@link #suasana},
 * {@link #kemakmuran}).
 * <p>
 * A resident keeps 0-2 habits ({@code Warga.kebiasaan1/2}, numbers 1-32 = the order of {@link #ID}); they are rolled when
 * raised ({@link #undiAwal}) and at the ages 8 (children's habits), 16 and 50 ({@link #ulangTahun}).
 *
 * <pre>{@code
 * {
 *   "nomor": 2,                          // must equal the position in ID (1-32)
 *   "siapa": { "umur": [55, 200], "kelamin": "pria", "kelompok": ["tetua"], "bukan": ["rahib"], "peran": ["sastrawan"],
 *              "bekerja": false, "tahap": ["dewasa", "tua"] },    // tahap: when it may be picked (anak 8, dewasa 16, tua 50)
 *   "bobot": 1.0, "sial": false,        // sial: stays home on the almanac's unlucky days (takhayul)
 *   "sesi": [ {
 *     "jam": [9, 11],                    // decimal hours, end exclusive, may wrap ([23, 5])
 *     "hari": [0, 6], "peluang": 0.8, "per_jam": false, "butuh": -1,   // butuh: index of a session that must have happened today
 *     "syarat": ["cerah", "angin", "semi", "panas", "gugur", "dingin", "salju", "tidak_hujan", "bekerja", "pasar"],
 *     "tujuan": ["catur", "kursi", "@jalan:2"],          // titik types or @kasur/@rumah/@kedai/@kerja/@jalan:<n>/@depan_lapak:<n>/@pemain
 *     "kegiatan": "kb_kakek_catur", "klip": "catur", "varian": 0, "kanan": "papan_catur", "kiri": "", "punggung": "",
 *     "jalan": "kota42:jalan_mabuk", "dalam": false,
 *     "ragam": [POSE...],                 // per resident (id % n)
 *     "ganti": [POSE...], "ganti_menit": 6,   // alternating poses
 *     "pemain": 2, "penonton": POSE, "penonton_ganti": [POSE...],   // first n participants play, the rest watch
 *     "undang": { "umur": [4, 11], "maks": 4, "klip": "duduk_lantai" }   // residents without a habit gather around
 *   } ]
 * }
 * POSE = { "klip": "...", "varian": 0, "kanan": "...", "kiri": "...", "punggung": "..." }
 * }</pre>
 * Clip names: {@code kota42:<name>} = {@link KotaGerakId} (350-459), else {@link GerakId}; {@code "@kerja"} = the role's own
 * work clip. Prop names: {@code kota42:<name>} = the 42 props (170-239), else {@link PropId}.
 */
public final class Kebiasaan {

	/** Habit ids by number (index 0 = none). */
	public static final String[] ID = {"", "pemabuk", "kakek_catur", "gosip_sumur", "burung_sangkar", "tai_chi", "pemancing",
			"adu_jangkrik", "penyair", "pemain_erhu", "pendoa_kuil", "layang_layang", "penyulam", "tidur_siang", "pesolek", "pelit",
			"dermawan", "pemarah", "takhayul", "kutu_buku", "teman_kucing", "penjudi", "pencerita_anak", "tari_kipas",
			"penyapu_jalan", "pelukis_gula", "pecinta_teh", "penggemar_opera", "pengintip", "penabung", "pemuja_kultivator",
			"pemalas", "pekerja_keras"};
	public static final int JUMLAH = ID.length - 1;

	/** {@link Siapa#tahap} bits: picked at 8, 16, 50. */
	public static final int TAHAP_ANAK = 1;
	public static final int TAHAP_DEWASA = 2;
	public static final int TAHAP_TUA = 4;

	/** Pose value "keep the session's". */
	static final int TETAP = -1;
	/** Clip value "the role's own work clip". */
	static final int KLIP_KERJA = -2;

	/** {@link Sesi#syarat} bits. */
	static final int S_CERAH = 1;
	static final int S_TIDAK_HUJAN = 1 << 1;
	static final int S_ANGIN = 1 << 2;
	static final int S_SEMI = 1 << 3;
	static final int S_PANAS = 1 << 4;
	static final int S_GUGUR = 1 << 5;
	static final int S_DINGIN = 1 << 6;
	static final int S_SALJU = 1 << 7;
	static final int S_BEKERJA = 1 << 8;
	static final int S_PASAR = 1 << 9;
	private static final String[] SYARAT = {"cerah", "tidak_hujan", "angin", "semi", "panas", "gugur", "dingin", "salju", "bekerja", "pasar"};

	/** Special goal: near a passing cultivator of Foundation or higher (the young admirer). */
	static final String PEMAIN = "@pemain";

	/** A pose: clip, variant and props ({@link #TETAP} keeps the session's). */
	public record Pose(int klip, int varian, int kanan, int kiri, int punggung) {
		static final Pose KOSONG = new Pose(TETAP, TETAP, TETAP, TETAP, TETAP);
	}

	/** Who may have a habit. */
	public record Siapa(int umurMin, int umurMaks, int kelamin, Set<NpcRole.Kelompok> kelompok, Set<NpcRole.Kelompok> bukan,
			Set<String> peran, boolean bekerja, int tahap) {
	}

	/** Children (or anyone of an age) gathering around a session's holder. */
	public record Undang(int umurMin, int umurMaks, int maks, Pose pose) {
	}

	/** One session of a habit. */
	public record Sesi(double dari, double sampai, int hari, double peluang, boolean perJam, int butuh, int syarat, List<String> tujuan,
			String kunci, Pose pose, int jalan, boolean dalam, List<Pose> ragam, List<Pose> ganti, int gantiMenit, int pemain,
			@Nullable Pose penonton, List<Pose> penontonGanti, @Nullable Undang undang) {

		/** True when decimal hour {@code j} is inside (end exclusive, wrapping past midnight). */
		boolean di(double j) {
			return dari <= sampai ? j >= dari && j < sampai : j >= dari || j < sampai;
		}
	}

	/** One habit file. */
	public record Data(String id, int nomor, Siapa siapa, double bobot, boolean sial, List<Sesi> sesi) {
	}

	/** The session a resident is in now: habit, session index, the decimal hour. */
	private record Pilihan(Data data, int sesi) {
	}

	private static volatile Data[] data = new Data[ID.length];
	private static boolean registered;

	/** Group participants and guests of one place for one base hour. */
	private static final class Jam {
		long kunci = Long.MIN_VALUE;
		final Map<Integer, List<Integer>> peserta = new HashMap<>();
		final Map<Integer, Kegiatan> tamu = new HashMap<>();
	}

	private static final Map<Long, Jam> JAM = new HashMap<>();
	/** Daily mood bonus per place: situs -> (resident id -> bonus); rebuilt by {@link #harian}. */
	private static final Map<Long, Map<Integer, Integer>> BONUS = new HashMap<>();

	/** Registers the data loader. From {@code ArsipWarga.init}; safe twice. */
	public static void init() {
		if (registered) {
			return;
		}
		registered = true;
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Kultivator.id("kota_kebiasaan"), new Pemuat());
	}

	// ================================================================== lookups

	/** The number of habit {@code id} (1-32), or 0. */
	public static int nomor(@Nullable String id) {
		if (id == null) {
			return 0;
		}
		for (int i = 1; i < ID.length; i++) {
			if (ID[i].equals(id)) {
				return i;
			}
		}
		return 0;
	}

	/** The id of habit {@code nomor}, or "" for none. */
	public static String id(int nomor) {
		return nomor > 0 && nomor < ID.length ? ID[nomor] : "";
	}

	/** {@code kultivator.kota42.kebiasaan.<id>}. */
	public static Component nama(int nomor) {
		return nomor > 0 && nomor < ID.length ? Component.translatable("kultivator.kota42.kebiasaan." + ID[nomor])
				: Component.translatable("kultivator.kota42.kebiasaan.tidak_ada");
	}

	/** The data of habit {@code nomor}, or null (file missing or broken). */
	public static @Nullable Data data(int nomor) {
		Data[] all = data;
		return nomor > 0 && nomor < all.length ? all[nomor] : null;
	}

	/** Number of habits with data (commands). */
	public static int jumlahData() {
		int n = 0;
		for (Data d : data) {
			if (d != null) {
				n++;
			}
		}
		return n;
	}

	public static boolean punya(Warga w, int nomor) {
		return w != null && nomor > 0 && (w.kebiasaan1 == nomor || w.kebiasaan2 == nomor);
	}

	public static boolean punya(Warga w, String id) {
		return punya(w, nomor(id));
	}

	// ================================================================== factors read by other modules

	/** Gossip spread factor (42.5.4): the well gossipers x3, the peeping elders hear everything x3. */
	public static int faktorGosip(Warga w) {
		return punya(w, "gosip_sumur") || punya(w, "pengintip") ? 3 : 1;
	}

	/** Fight factor (42D street events): the hot-tempered x2, the drunk x1.5 (rounded up to 2 at night). */
	public static double faktorKelahi(Warga w) {
		return punya(w, "pemarah") ? 2.0D : punya(w, "pemabuk") ? 1.5D : 1.0D;
	}

	/** Work skill growth factor: lazy x0.5, hard-working x1.5, bookworm scholar x1.3. */
	public static double faktorAhli(Warga w) {
		double f = 1.0D;
		if (punya(w, "pemalas")) {
			f *= 0.5D;
		}
		if (punya(w, "pekerja_keras")) {
			f *= 1.5D;
		}
		if (punya(w, "kutu_buku") && w.peranNpc().kelompok == NpcRole.Kelompok.CENDEKIA) {
			f *= 1.3D;
		}
		return f;
	}

	/** Bargaining rounds a buyer insists on at a player's shop (42C): the stingy 3, the vain 0, others 1. */
	public static int faktorTawar(Warga w) {
		return punya(w, "pelit") ? 3 : punya(w, "pesolek") ? 0 : 1;
	}

	/** Extra chance to join a festival crowd: the fan dancers +0.3, the opera lovers +0.15. */
	public static double tambahFestival(Warga w) {
		return punya(w, "tari_kipas") ? 0.3D : punya(w, "penggemar_opera") ? 0.15D : 0.0D;
	}

	/** Today's mood bonus of {@code w} from habits of others in its place (erhu neighbours, storyteller, generous). */
	public static int suasana(long situs, Warga w) {
		Map<Integer, Integer> m = BONUS.get(situs);
		return m == null ? 0 : m.getOrDefault(w.id, 0);
	}

	/** Household prosperity change from habits of its members: a gambler -10, a saver +8, a hard worker +4. */
	public static int kemakmuran(List<Warga> rumahTangga) {
		int v = 0;
		for (Warga k : rumahTangga) {
			if (!k.hidup()) {
				continue;
			}
			if (punya(k, "penjudi")) {
				v -= 10;
			}
			if (punya(k, "penabung")) {
				v += 8;
			}
			if (punya(k, "pekerja_keras")) {
				v += 4;
			}
			if (punya(k, "pemalas")) {
				v -= 4;
			}
		}
		return Mth.clamp(v, -25, 20);
	}

	/** The almanac's unlucky days (huangli): about one day in seven, the same for the whole world. */
	public static boolean sial(long hari) {
		long h = (hari + 11L) * 0x9E3779B97F4A7C15L;
		return Math.floorMod(h ^ (h >>> 31), 7L) == 3L;
	}

	/** True when {@code w} is in one of its habits' sessions now ({@code id} of that habit; for 42B/42C/42D lines). */
	public static boolean sedang(Warga w, String id, long situs) {
		int no = nomor(id);
		TempatHuni huni = Pemanggil.huni(situs);
		MinecraftServer server = KotaHidup.server();
		if (!punya(w, no) || huni == null || server == null || w.slot == Warga.TANPA_SLOT) {
			return false;
		}
		Penduduk p = huni.orang(w.slot);
		if (p == null) {
			return false;
		}
		Jadwal.Saat s = Jadwal.saat(p, huni.jenis, server.overworld().getOverworldClockTime());
		Pilihan pil = pilih(huni, w, p, s);
		return pil != null && pil.data.nomor == no;
	}

	// ================================================================== rolling habits

	/** Rolls the habits of a resident raised at age {@code umur} (WargaGen): as if it had passed the ages 8, 16 and 50. */
	public static void undiAwal(Warga w, int umur, RandomSource random) {
		w.kebiasaan1 = 0;
		w.kebiasaan2 = 0;
		if (umur >= 8) {
			ulangTahun(w, 8, random);
		}
		if (umur >= 16) {
			ulangTahun(w, 16, random);
		}
		if (umur >= 50) {
			ulangTahun(w, 50, random);
		}
	}

	/**
	 * A birthday at {@code umur}: 8 gives a child's habit (35 %), 16 replaces it with an adult's (70 %, a second 30 %),
	 * 50 may add or swap in an elder's habit (60 %). Only habits the resident fits are picked, weighted by {@code bobot}.
	 */
	public static void ulangTahun(Warga w, int umur, RandomSource random) {
		if (umur == 8) {
			if (random.nextDouble() < 0.35D) {
				w.kebiasaan1 = undi(w, umur, TAHAP_ANAK, 0, random);
			}
		} else if (umur == 16) {
			int lama = w.kebiasaan1;
			w.kebiasaan1 = 0;
			w.kebiasaan2 = 0;
			// a child's habit that also fits adults stays half the time (the bookworm becomes a scholar)
			if (lama != 0 && boleh(w, data(lama), 16, TAHAP_DEWASA) && random.nextBoolean()) {
				w.kebiasaan1 = lama;
			} else if (random.nextDouble() < 0.7D) {
				w.kebiasaan1 = undi(w, umur, TAHAP_DEWASA, 0, random);
			}
			if (w.kebiasaan1 != 0 && random.nextDouble() < 0.3D) {
				w.kebiasaan2 = undi(w, umur, TAHAP_DEWASA, w.kebiasaan1, random);
			}
		} else if (umur == 50 && random.nextDouble() < 0.6D) {
			int baru = undi(w, umur, TAHAP_TUA, w.kebiasaan1, random);
			if (baru != 0 && baru != w.kebiasaan2) {
				if (w.kebiasaan1 == 0) {
					w.kebiasaan1 = baru;
				} else if (w.kebiasaan2 == 0 || random.nextBoolean()) {
					w.kebiasaan2 = baru;
				}
			}
		}
		// habits that no longer fit (a child's admiration past 17) drop away
		if (w.kebiasaan1 != 0 && !cocokUmur(data(w.kebiasaan1), umur)) {
			w.kebiasaan1 = w.kebiasaan2;
			w.kebiasaan2 = 0;
		}
		if (w.kebiasaan2 != 0 && !cocokUmur(data(w.kebiasaan2), umur)) {
			w.kebiasaan2 = 0;
		}
	}

	private static boolean cocokUmur(@Nullable Data d, int umur) {
		return d == null || umur >= d.siapa.umurMin && umur <= d.siapa.umurMaks;
	}

	private static int undi(Warga w, int umur, int tahap, int kecuali, RandomSource random) {
		double total = 0.0D;
		double[] bobot = new double[ID.length];
		for (int i = 1; i < ID.length; i++) {
			Data d = data(i);
			if (i != kecuali && boleh(w, d, umur, tahap)) {
				bobot[i] = d.bobot;
				total += d.bobot;
			}
		}
		if (total <= 0.0D) {
			return 0;
		}
		double r = random.nextDouble() * total;
		for (int i = 1; i < ID.length; i++) {
			r -= bobot[i];
			if (bobot[i] > 0.0D && r <= 0.0D) {
				return i;
			}
		}
		return 0;
	}

	/** True when {@code w} at {@code umur} fits habit {@code d} for the picking stage {@code tahap}. */
	static boolean boleh(Warga w, @Nullable Data d, int umur, int tahap) {
		if (d == null || (d.siapa.tahap & tahap) == 0 || umur < d.siapa.umurMin || umur > d.siapa.umurMaks) {
			return false;
		}
		Siapa s = d.siapa;
		if (s.kelamin >= 0 && s.kelamin != w.kelamin) {
			return false;
		}
		NpcRole.Kelompok k = w.peranNpc().kelompok;
		if (s.bukan.contains(k)) {
			return false;
		}
		boolean grup = s.kelompok.isEmpty() && s.peran.isEmpty();
		if (!grup && !s.kelompok.contains(k) && !s.peran.contains(w.peran)) {
			// children are judged by age, not by the ANAK role, when the habit names a child group
			if (!(umur < 16 && s.kelompok.contains(NpcRole.Kelompok.ANAK))) {
				return false;
			}
		}
		return !s.bekerja || umur >= 16 && Pekerjaan.bekerja(w);
	}

	// ================================================================== the overrider

	/**
	 * The habit activity of {@code w} in slot {@code p} now, or null. Asked by {@link PenimpaWarga}; deterministic for the
	 * same inputs (daily rolls are hashes of resident, day, habit and session).
	 */
	public static @Nullable Kegiatan kegiatan(TempatHuni huni, Warga w, Penduduk p, long waktu, int jam, long now) {
		if (w.kebiasaan1 == 0 && w.kebiasaan2 == 0) {
			return tamu(huni, w, waktu);
		}
		Jadwal.Saat s = Jadwal.saat(p, huni.jenis, waktu);
		// the superstitious stay home on an unlucky day, from morning till dusk
		for (int no : new int[] {w.kebiasaan1, w.kebiasaan2}) {
			Data d = data(no);
			if (d != null && d.sial && sial(s.hari()) && s.jam() >= 6 && s.jam() < 20) {
				return Kegiatan.dalam("kb_" + d.id + "_sial");
			}
		}
		Pilihan pil = pilih(huni, w, p, s);
		if (pil == null) {
			return tamu(huni, w, waktu);
		}
		Sesi sesi = pil.data.sesi.get(pil.sesi);
		String tujuan = tujuan(huni, sesi.tujuan);
		if (tujuan == null) {
			return null;
		}
		Pose pose = sesi.pose;
		if (!sesi.ragam.isEmpty()) {
			pose = timpa(pose, sesi.ragam.get(Math.floorMod(w.id, sesi.ragam.size())));
		}
		int menit = s.jam() * 60 + s.menit();
		if (sesi.pemain > 0) {
			List<Integer> peserta = peserta(huni, waktu).getOrDefault(kunci(pil), List.of());
			int idx = peserta.indexOf(w.id);
			if (PEMAIN.equals(tujuan) && idx >= sesi.pemain) {
				return null;
			}
			if (idx >= sesi.pemain && sesi.penonton != null) {
				pose = timpa(pose, sesi.penonton);
				if (!sesi.penontonGanti.isEmpty()) {
					pose = timpa(pose, ganti(sesi.penontonGanti, sesi.gantiMenit, menit, w.id + 1));
				}
			} else if (!sesi.ganti.isEmpty()) {
				pose = timpa(pose, ganti(sesi.ganti, sesi.gantiMenit, menit, w.id));
			}
		} else if (!sesi.ganti.isEmpty()) {
			pose = timpa(pose, ganti(sesi.ganti, sesi.gantiMenit, menit, w.id));
		}
		if (PEMAIN.equals(tujuan)) {
			tujuan = dekatPemain(huni, w);
			if (tujuan == null) {
				return null;
			}
		}
		if (sesi.dalam) {
			return new Kegiatan(sesi.kunci, Kegiatan.KASUR, GerakId.TIDUR, 0, 0, 0, 0, 0, true);
		}
		if (pose.klip == KLIP_KERJA) {
			KerjaPeran.Kerja kerja = KerjaPeran.untuk(p.peran(), p.benih());
			Kegiatan k = kerja.kegiatan();
			return new Kegiatan(sesi.kunci, k.tujuan(), k.klip(), k.varian(), k.propKanan(), k.propKiri(), k.propPunggung(), k.jalan(), false);
		}
		return new Kegiatan(sesi.kunci, tujuan, Math.max(0, pose.klip), Math.max(0, pose.varian), Math.max(0, pose.kanan),
				Math.max(0, pose.kiri), Math.max(0, pose.punggung), sesi.jalan, false);
	}

	/** The session {@code w} is in at its clock {@code s}: habit 1 first, sessions in file order, the first active wins. */
	private static @Nullable Pilihan pilih(TempatHuni huni, Warga w, Penduduk p, Jadwal.Saat s) {
		double jam = s.jam() + s.menit() / 60.0D;
		for (int no : new int[] {w.kebiasaan1, w.kebiasaan2}) {
			Data d = data(no);
			if (d == null) {
				continue;
			}
			for (int i = 0; i < d.sesi.size(); i++) {
				Sesi sesi = d.sesi.get(i);
				if (sesi.di(jam) && aktif(huni, w, d, i, s) && (sesi.butuh < 0 || sesi.butuh >= d.sesi.size()
						|| lulus(w, d, sesi.butuh, s.hari(), 0))) {
					return new Pilihan(d, i);
				}
			}
		}
		return null;
	}

	private static boolean aktif(TempatHuni huni, Warga w, Data d, int i, Jadwal.Saat s) {
		Sesi sesi = d.sesi.get(i);
		if (sesi.hari != 0 && (sesi.hari & (1 << s.hariPekan())) == 0) {
			return false;
		}
		if (sesi.syarat != 0 && !syarat(sesi.syarat, huni.cuaca(), s, w)) {
			return false;
		}
		return lulus(w, d, i, s.hari(), sesi.perJam ? s.jam() + 1 : 0);
	}

	/** The daily (or hourly) roll of session {@code i}. */
	private static boolean lulus(Warga w, Data d, int i, long hari, int jam) {
		Sesi sesi = d.sesi.get(i);
		if (sesi.peluang >= 1.0D) {
			return true;
		}
		long h = (w.id * 0x9E3779B97F4A7C15L) ^ (hari * 0xC2B2AE3D27D4EB4FL) ^ ((long) d.nomor << 40) ^ ((long) i << 32) ^ jam * 0x165667B1L;
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53 < sesi.peluang;
	}

	private static boolean syarat(int bits, Jadwal.Cuaca c, Jadwal.Saat s, Warga w) {
		boolean basah = c.hujan() || c.badai() || c.salju();
		if ((bits & S_CERAH) != 0 && (basah || c.kabut())) {
			return false;
		}
		if ((bits & S_TIDAK_HUJAN) != 0 && (c.hujan() || c.badai())) {
			return false;
		}
		if ((bits & S_ANGIN) != 0 && (basah || s.musim() == Jadwal.PANAS || s.musim() == Jadwal.DINGIN)) {
			return false;
		}
		int musim = bits & (S_SEMI | S_PANAS | S_GUGUR | S_DINGIN);
		if (musim != 0 && (musim & (S_SEMI << s.musim())) == 0) {
			return false;
		}
		if ((bits & S_SALJU) != 0 && !c.salju()) {
			return false;
		}
		if ((bits & S_BEKERJA) != 0 && !Pekerjaan.bekerja(w)) {
			return false;
		}
		return (bits & S_PASAR) == 0 || s.hariPekan() == 0;
	}

	/** The first goal of the list the place can serve (a special goal always can), or null: the habit does not show. */
	private static @Nullable String tujuan(TempatHuni huni, List<String> daftar) {
		for (String t : daftar) {
			if (t.startsWith("@") || !huni.titik(t).isEmpty()) {
				return t;
			}
		}
		return null;
	}

	/** A spot 3-5 blocks from the nearest Foundation-or-higher player within 24 blocks of the place, or null. */
	private static @Nullable String dekatPemain(TempatHuni huni, Warga w) {
		MinecraftServer server = KotaHidup.server();
		ServerLevel level = server == null ? null : server.getLevel(huni.dimensi);
		if (level == null) {
			return null;
		}
		ServerPlayer best = null;
		double bestD = Double.MAX_VALUE;
		BlockPos c = huni.pusat();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !huni.kotak.inflatedBy(24).isInside(player.blockPosition())
					|| Cultivators.get(player).realm().ordinal() < Realm.FOUNDATION.ordinal()) {
				continue;
			}
			double d = player.blockPosition().distSqr(c);
			if (d < bestD) {
				bestD = d;
				best = player;
			}
		}
		if (best == null) {
			return null;
		}
		double sudut = Math.floorMod(w.id * 2654435761L, 360L) * Mth.DEG_TO_RAD;
		double jari = 3.0D + Math.floorMod(w.id, 3);
		int x = Mth.floor(best.getX() + Math.cos(sudut) * jari);
		int z = Mth.floor(best.getZ() + Math.sin(sudut) * jari);
		return Kegiatan.XZ + x + "," + z;
	}

	private static Pose ganti(List<Pose> daftar, int menitPer, int menit, int geser) {
		int per = Math.max(1, menitPer);
		return daftar.get(Math.floorMod(menit / per + geser, daftar.size()));
	}

	private static Pose timpa(Pose dasar, Pose atas) {
		return new Pose(atas.klip != TETAP ? atas.klip : dasar.klip, atas.varian != TETAP ? atas.varian : dasar.varian,
				atas.kanan != TETAP ? atas.kanan : dasar.kanan, atas.kiri != TETAP ? atas.kiri : dasar.kiri,
				atas.punggung != TETAP ? atas.punggung : dasar.punggung);
	}

	private static int kunci(Pilihan p) {
		return p.data.nomor << 8 | p.sesi;
	}

	// ================================================================== groups and guests (cached per place and hour)

	private static Map<Integer, List<Integer>> peserta(TempatHuni huni, long waktu) {
		return jam(huni, waktu).peserta;
	}

	private static @Nullable Kegiatan tamu(TempatHuni huni, Warga w, long waktu) {
		return jam(huni, waktu).tamu.get(w.id);
	}

	/** The group table of a place for the base hour of {@code waktu}, rebuilt when the hour turns. */
	private static Jam jam(TempatHuni huni, long waktu) {
		long kunci = Math.floorDiv(waktu, 1000L);
		Jam j = JAM.computeIfAbsent(huni.situsId, k -> new Jam());
		if (j.kunci == kunci) {
			return j;
		}
		j.kunci = kunci;
		j.peserta.clear();
		j.tamu.clear();
		List<Penduduk> tuan = new ArrayList<>();
		List<Pilihan> tuanPil = new ArrayList<>();
		List<Warga> bebas = new ArrayList<>();
		Map<Integer, Penduduk> slot = new HashMap<>();
		for (Penduduk p : huni.penduduk()) {
			Warga x = WargaApi.warga(huni.situsId, p.id());
			if (x == null || !x.bertubuhMungkin()) {
				continue;
			}
			slot.put(x.id, p);
			Jadwal.Saat s = Jadwal.saat(p, huni.jenis, waktu);
			Pilihan pil = x.kebiasaan1 == 0 && x.kebiasaan2 == 0 ? null : pilih(huni, x, p, s);
			if (pil == null) {
				bebas.add(x);
				continue;
			}
			j.peserta.computeIfAbsent(kunci(pil), k -> new ArrayList<>()).add(x.id);
			if (pil.data.sesi.get(pil.sesi).undang != null) {
				tuan.add(p);
				tuanPil.add(pil);
			}
		}
		j.peserta.values().forEach(list -> list.sort(Integer::compare));
		if (tuan.isEmpty() || bebas.isEmpty()) {
			return j;
		}
		MinecraftServer server = KotaHidup.server();
		ServerLevel level = server == null ? null : server.getLevel(huni.dimensi);
		if (level == null) {
			return j;
		}
		long hari = Math.floorDiv(waktu, 24000L);
		Set<Integer> dipakai = new HashSet<>();
		for (int t = 0; t < tuan.size(); t++) {
			Penduduk hostP = tuan.get(t);
			Pilihan pil = tuanPil.get(t);
			Sesi sesi = pil.data.sesi.get(pil.sesi);
			Undang u = sesi.undang;
			String goal = tujuan(huni, sesi.tujuan);
			if (u == null || goal == null || goal.startsWith(PEMAIN)) {
				continue;
			}
			Kegiatan hostK = Kegiatan.di(sesi.kunci, goal, Math.max(0, sesi.pose.klip), 0);
			TempatHuni.Tujuan pos = huni.tujuan(level, hostP, hostK, (int) ((waktu / 1000L + 6L) % 24L), false);
			if (pos == null) {
				continue;
			}
			int hostId = hostP.id();
			List<Warga> calon = new ArrayList<>();
			for (Warga x : bebas) {
				int umur = WargaApi.umur(x);
				if (!dipakai.contains(x.id) && umur >= u.umurMin && umur <= u.umurMaks) {
					calon.add(x);
				}
			}
			calon.sort((a, b) -> Long.compare(acak(a.id, hostId, hari), acak(b.id, hostId, hari)));
			for (int n = 0; n < Math.min(u.maks, calon.size()); n++) {
				Warga g = calon.get(n);
				dipakai.add(g.id);
				double sudut = (n + 0.5D) * (Math.PI * 2.0D / Math.max(1, u.maks)) + pos.hadap().toYRot() * Mth.DEG_TO_RAD;
				int x = Mth.floor(pos.pos().getX() + 0.5D + Math.cos(sudut) * 2.0D);
				int z = Mth.floor(pos.pos().getZ() + 0.5D + Math.sin(sudut) * 2.0D);
				Pose gp = u.pose;
				j.tamu.put(g.id, new Kegiatan(sesi.kunci + "_tamu", Kegiatan.XZ + x + "," + z, Math.max(0, gp.klip), Math.max(0, gp.varian),
						Math.max(0, gp.kanan), Math.max(0, gp.kiri), Math.max(0, gp.punggung), 0, false));
			}
		}
		return j;
	}

	private static long acak(int a, int b, long hari) {
		long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ hari;
		return h ^ (h >>> 29);
	}

	// ================================================================== Hari Warga step

	/**
	 * Hari Warga step (before {@link Suasana#harian}): habits picked on birthdays (8, 16, 50), yearly health of drinkers
	 * (-2) and tai chi practitioners (+3), friendships within habit groups (chess elders x2), quarrels of the hot-tempered,
	 * gossip about drunks and gamblers, and today's mood bonuses (erhu neighbours +2, storyteller +3 and generous +2 for
	 * children).
	 */
	public static void harian(MinecraftServer server, WargaData.Situs s, long hari) {
		if (!KotaConfig.warga().aktif) {
			return;
		}
		WargaData data = WargaData.get(server);
		RandomSource random = RandomSource.create(s.id * 0x5DEECE66DL ^ hari * 0x9E3779B97F4A7C15L ^ 0x4B45L);
		long now = server.overworld().getGameTime();
		int tahun = KotaConfig.warga().hariPerTahun;
		Map<Integer, Integer> umur = new HashMap<>();
		Map<Integer, List<Warga>> grup = new TreeMap<>();
		List<Warga> hidup = new ArrayList<>();
		for (Warga w : s.warga) {
			if (w.status != Warga.Status.HIDUP) {
				continue;
			}
			hidup.add(w);
			int u = WargaApi.umur(w);
			umur.put(w.id, u);
			int kemarin = umurPada(w, hari - 1L, tahun);
			if (u != kemarin && (u == 8 || u == 16 || u == 50)) {
				ulangTahun(w, u, random);
			}
			if (u != kemarin) {
				if (punya(w, "pemabuk")) {
					w.kesehatan = Mth.clamp(w.kesehatan - 2, 0, 100);
				}
				if (punya(w, "tai_chi")) {
					w.kesehatan = Mth.clamp(w.kesehatan + 3, 0, 100);
				}
			}
			for (int no : new int[] {w.kebiasaan1, w.kebiasaan2}) {
				if (no != 0) {
					grup.computeIfAbsent(no, k -> new ArrayList<>()).add(w);
				}
			}
		}
		ServerLevel level = server.overworld();
		for (Warga w : hidup) {
			if (punya(w, "pemabuk") && random.nextDouble() < 0.04D) {
				GosipWarga.lahir(level, s.id, "MABUK", Fakta.Rujuk.warga(w.id));
			}
			if (punya(w, "penjudi") && random.nextDouble() < 0.06D) {
				GosipWarga.lahir(level, s.id, "KALAH_JUDI", Fakta.Rujuk.warga(w.id));
			}
			if (punya(w, "pemarah") && hidup.size() > 1 && random.nextDouble() < 0.05D) {
				Warga lawan = hidup.get(random.nextInt(hidup.size()));
				if (lawan != w) {
					Hubungan.ubah(data, w, lawan, -6, hari, umur.get(w.id), umur.get(lawan.id));
					Suasana.marah(server, w, now);
					Suasana.marah(server, lawan, now);
					if (random.nextDouble() < 0.3D) {
						GosipWarga.lahir(level, s.id, "BERKELAHI", Fakta.Rujuk.warga(w.id), Fakta.Rujuk.warga(lawan.id));
					}
				}
			}
		}
		// friendships inside habit groups: up to two pairs a day, the chess elders twice as fast
		for (Map.Entry<Integer, List<Warga>> e : grup.entrySet()) {
			List<Warga> g = e.getValue();
			if (g.size() < 2 || !kelompok(e.getKey())) {
				continue;
			}
			int delta = ID[e.getKey()].equals("kakek_catur") ? 6 : 3;
			for (int n = 0; n < 2; n++) {
				Warga a = g.get(random.nextInt(g.size()));
				Warga b = g.get(random.nextInt(g.size()));
				if (a != b) {
					Hubungan.ubah(data, a, b, delta, hari, umur.get(a.id), umur.get(b.id));
				}
			}
		}
		BONUS.put(s.id, bonus(s, hidup, umur, grup));
		data.ubah();
	}

	/** Habits whose members meet each other (a group of the same titik and hour). */
	private static boolean kelompok(int no) {
		return switch (ID[no]) {
			case "kakek_catur", "gosip_sumur", "tai_chi", "adu_jangkrik", "tari_kipas", "pecinta_teh", "penjudi", "pemabuk",
					"penggemar_opera", "burung_sangkar" -> true;
			default -> false;
		};
	}

	private static Map<Integer, Integer> bonus(WargaData.Situs s, List<Warga> hidup, Map<Integer, Integer> umur, Map<Integer, List<Warga>> grup) {
		Map<Integer, Integer> out = new HashMap<>();
		boolean pencerita = grup.containsKey(nomor("pencerita_anak"));
		boolean dermawan = grup.containsKey(nomor("dermawan"));
		if (pencerita || dermawan) {
			for (Warga w : hidup) {
				if (umur.get(w.id) < 13) {
					out.merge(w.id, (pencerita ? 3 : 0) + (dermawan ? 2 : 0), Integer::sum);
				}
			}
		}
		List<Warga> erhu = grup.getOrDefault(nomor("pemain_erhu"), List.of());
		if (!erhu.isEmpty()) {
			TempatHuni huni = Pemanggil.huni(s.id);
			for (Warga pemain : erhu) {
				BlockPos rumah = rumahPos(huni, pemain);
				for (Warga w : hidup) {
					if (w == pemain) {
						continue;
					}
					boolean dekat = w.rumahTangga != 0 && w.rumahTangga == pemain.rumahTangga;
					if (!dekat && rumah != null) {
						BlockPos lain = rumahPos(huni, w);
						dekat = lain != null && lain.distSqr(rumah) <= 24 * 24;
					}
					if (dekat) {
						out.merge(w.id, 2, (a, b) -> Math.min(4, a + b));
					}
				}
			}
		}
		return out;
	}

	private static @Nullable BlockPos rumahPos(@Nullable TempatHuni huni, Warga w) {
		if (huni == null || w.slot == Warga.TANPA_SLOT) {
			return null;
		}
		Penduduk p = huni.orang(w.slot);
		return p == null ? null : huni.rumahPos(p);
	}

	/** The age of {@code w} on world day {@code hari} (Fana Years of {@code tahun} days). */
	static int umurPada(Warga w, long hari, int tahun) {
		long tenths = hari * 10L - w.lahir;
		return (int) Math.max(0L, tenths / (10L * Math.max(1, tahun)));
	}

	/** Forgets the caches (server stop). */
	public static void lupakan() {
		JAM.clear();
		BONUS.clear();
	}

	// ================================================================== loader

	private static final FileToIdConverter FOLDER = FileToIdConverter.json("tempat/kebiasaan");

	private static final class Pemuat extends SimplePreparableReloadListener<Data[]> {
		@Override
		protected Data[] prepare(ResourceManager manager, ProfilerFiller profiler) {
			Data[] out = new Data[ID.length];
			Map<Identifier, Resource> files = new TreeMap<>(FOLDER.listMatchingResources(manager));
			for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
				Identifier id = FOLDER.fileToId(file.getKey());
				if (!Kultivator.MOD_ID.equals(id.getNamespace())) {
					continue;
				}
				String nama = id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
				int no = nomor(nama);
				if (no == 0) {
					Kultivator.LOGGER.warn("Habit file {}: unknown habit id", file.getKey());
					continue;
				}
				try (Reader reader = file.getValue().openAsReader()) {
					JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
					if (o.has("nomor") && o.get("nomor").getAsInt() != no) {
						throw new IllegalArgumentException("nomor " + o.get("nomor").getAsInt() + " != " + no);
					}
					out[no] = baca(nama, no, o);
				} catch (Exception e) {
					Kultivator.LOGGER.warn("Habit file {} could not be read", file.getKey(), e);
				}
			}
			return out;
		}

		@Override
		protected void apply(Data[] prepared, ResourceManager manager, ProfilerFiller profiler) {
			data = prepared;
			JAM.clear();
		}
	}

	static Data baca(String id, int no, JsonObject o) {
		JsonObject s = o.has("siapa") ? o.getAsJsonObject("siapa") : new JsonObject();
		int[] umur = rentang(s, "umur", 0, 200);
		int kelamin = switch (str(s, "kelamin", "semua")) {
			case "pria" -> Warga.PRIA;
			case "wanita" -> Warga.WANITA;
			default -> -1;
		};
		int tahap = 0;
		for (String t : daftar(s, "tahap", List.of("dewasa"))) {
			tahap |= switch (t) {
				case "anak" -> TAHAP_ANAK;
				case "tua" -> TAHAP_TUA;
				case "dewasa" -> TAHAP_DEWASA;
				default -> throw new IllegalArgumentException("tahap " + t);
			};
		}
		Siapa siapa = new Siapa(umur[0], umur[1], kelamin, kelompok(daftar(s, "kelompok", List.of())), kelompok(daftar(s, "bukan", List.of())),
				Set.copyOf(daftar(s, "peran", List.of())), s.has("bekerja") && s.get("bekerja").getAsBoolean(), tahap);
		List<Sesi> sesi = new ArrayList<>();
		for (JsonElement e : o.getAsJsonArray("sesi")) {
			sesi.add(sesi(id, e.getAsJsonObject()));
		}
		if (sesi.isEmpty()) {
			throw new IllegalArgumentException("no sesi");
		}
		double bobot = o.has("bobot") ? o.get("bobot").getAsDouble() : 1.0D;
		return new Data(id, no, siapa, Math.max(0.0D, bobot), o.has("sial") && o.get("sial").getAsBoolean(), List.copyOf(sesi));
	}

	private static Sesi sesi(String id, JsonObject o) {
		JsonArray jam = o.getAsJsonArray("jam");
		double dari = jam.get(0).getAsDouble();
		double sampai = jam.get(1).getAsDouble();
		if (dari < 0 || dari >= 24.01 || sampai < 0 || sampai > 24.01) {
			throw new IllegalArgumentException("jam " + jam);
		}
		int hari = 0;
		for (JsonElement e : o.has("hari") ? o.getAsJsonArray("hari") : new JsonArray()) {
			hari |= 1 << Mth.clamp(e.getAsInt(), 0, 6);
		}
		int syarat = 0;
		for (String t : daftar(o, "syarat", List.of())) {
			int i = List.of(SYARAT).indexOf(t);
			if (i < 0) {
				throw new IllegalArgumentException("syarat " + t);
			}
			syarat |= 1 << i;
		}
		List<String> tujuan = daftar(o, "tujuan", List.of());
		if (tujuan.isEmpty()) {
			throw new IllegalArgumentException("no tujuan");
		}
		Pose pose = pose(o, true);
		Undang undang = null;
		if (o.has("undang")) {
			JsonObject u = o.getAsJsonObject("undang");
			int[] r = rentang(u, "umur", 4, 11);
			undang = new Undang(r[0], r[1], u.has("maks") ? Mth.clamp(u.get("maks").getAsInt(), 1, 8) : 4, pose(u, true));
		}
		return new Sesi(dari % 24.0D, sampai >= 24.0D ? sampai - 24.0D : sampai, hari,
				o.has("peluang") ? Mth.clamp(o.get("peluang").getAsDouble(), 0.0D, 1.0D) : 1.0D,
				o.has("per_jam") && o.get("per_jam").getAsBoolean(), o.has("butuh") ? o.get("butuh").getAsInt() : -1, syarat,
				List.copyOf(tujuan), str(o, "kegiatan", "kb_" + id), pose, klip(str(o, "jalan", "")),
				o.has("dalam") && o.get("dalam").getAsBoolean(), poses(o, "ragam"), poses(o, "ganti"),
				o.has("ganti_menit") ? Math.max(1, o.get("ganti_menit").getAsInt()) : 6, o.has("pemain") ? Math.max(0, o.get("pemain").getAsInt()) : 0,
				o.has("penonton") ? pose(o.getAsJsonObject("penonton"), false) : null, poses(o, "penonton_ganti"), undang);
	}

	private static List<Pose> poses(JsonObject o, String key) {
		List<Pose> out = new ArrayList<>();
		if (o.has(key)) {
			for (JsonElement e : o.getAsJsonArray(key)) {
				out.add(pose(e.getAsJsonObject(), false));
			}
		}
		return List.copyOf(out);
	}

	/** A pose; with {@code dasar} missing fields are 0, otherwise {@link #TETAP} (kept from the session). */
	private static Pose pose(JsonObject o, boolean dasar) {
		int keep = dasar ? 0 : TETAP;
		return new Pose(o.has("klip") ? klip(o.get("klip").getAsString()) : keep, o.has("varian") ? o.get("varian").getAsInt() : keep,
				o.has("kanan") ? prop(o.get("kanan").getAsString()) : keep, o.has("kiri") ? prop(o.get("kiri").getAsString()) : keep,
				o.has("punggung") ? prop(o.get("punggung").getAsString()) : keep);
	}

	/** A clip name ({@code kota42:<name>}, a {@link GerakId} name, {@code @kerja}; "" = 0). Unknown names are errors. */
	static int klip(String nama) {
		if (nama == null || nama.isEmpty()) {
			return 0;
		}
		if (nama.equals("@kerja")) {
			return KLIP_KERJA;
		}
		int id = nama.startsWith("kota42:") ? KotaGerakId.klipId(nama.substring(7)) : GerakId.id(nama);
		if (id == 0) {
			throw new IllegalArgumentException("clip " + nama);
		}
		return id;
	}

	/** A prop name ({@code kota42:<name>} or a {@link PropId} name; "" = 0). Unknown names are errors. */
	static int prop(String nama) {
		if (nama == null || nama.isEmpty()) {
			return 0;
		}
		if (nama.startsWith("kota42:")) {
			String key = nama.substring(7);
			for (int i = KotaGerakId.PROP_FIRST; i <= KotaGerakId.PROP_LAST; i++) {
				if (key.equals(KotaGerakId.propNama(i))) {
					return i;
				}
			}
			throw new IllegalArgumentException("prop " + nama);
		}
		int id = PropId.id(nama);
		if (id == 0) {
			throw new IllegalArgumentException("prop " + nama);
		}
		return id;
	}

	private static Set<NpcRole.Kelompok> kelompok(List<String> ids) {
		Set<NpcRole.Kelompok> out = EnumSet.noneOf(NpcRole.Kelompok.class);
		for (String id : ids) {
			out.add(NpcRole.Kelompok.valueOf(id.toUpperCase(Locale.ROOT)));
		}
		return out;
	}

	private static int[] rentang(JsonObject o, String key, int a, int b) {
		if (!o.has(key)) {
			return new int[] {a, b};
		}
		JsonArray r = o.getAsJsonArray(key);
		return new int[] {r.get(0).getAsInt(), r.get(1).getAsInt()};
	}

	private static String str(JsonObject o, String key, String def) {
		return o.has(key) ? o.get(key).getAsString() : def;
	}

	private static List<String> daftar(JsonObject o, String key, List<String> def) {
		if (!o.has(key)) {
			return def;
		}
		JsonElement e = o.get(key);
		if (e.isJsonPrimitive()) {
			return List.of(e.getAsString());
		}
		List<String> out = new ArrayList<>();
		for (JsonElement x : e.getAsJsonArray()) {
			out.add(x.getAsString());
		}
		return out;
	}

	private Kebiasaan() {
	}
}
