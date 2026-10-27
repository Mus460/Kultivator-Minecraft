package dev.kultivator.dunia.kota42.ragam;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Capacities, hour multipliers and targets of the even spread (DESIGN 42.4A.1): the rules of
 * {@code data/kultivator/tempat/ragam/zona.json} (defaults below are the same table) and the pure arithmetic
 * <pre>
 *   T_z = tetap_z + K'_z x m_z(jam) / sum(K' x m) x N_bebas      K'_z = max(0, K_z - tetap_z)
 *   C_z = ceil(kapZona x T_z) + 1                                 L_z = floor(lantaiZona x T_z) when K_z >= 4, else 0
 * </pre>
 * {@code tetap_z} are the residents with a fixed duty in the zone that hour (staff at their counter, guards at their post,
 * sleepers at home): they already fill it, so only the free residents are shared out by capacity x multiplier. A zone never
 * gets more than its room {@code K'_z}; what a full zone cannot take is shared over the others the same way, so the targets
 * add up to {@code N_bebas} while the place has room. An event lifts the multipliers of its zone kinds.
 */
public final class Kapasitas {

	public static final Identifier ID = Kultivator.id("kota_ragam_zona");
	private static final Identifier BERKAS = Kultivator.id("tempat/ragam/zona.json");

	/** The twelve zone kinds of the 42.4A.1 table. */
	public static final List<String> JENIS = List.of("pasar", "makan", "teh", "arak", "rumah", "kuil", "sekolah", "kerja", "ladang", "alun",
			"ruas", "gerbang");

	/** The rules read from {@code zona.json}. */
	public record Aturan(int[] jamMulai, Map<String, double[]> pengali, Map<String, Map<String, Double>> acara, Map<String, Integer> kapasitas,
			double ruasPerPejalan, double luasPerOrang, Map<String, String> titikJenis, List<String[]> nama, Set<String> umum) {
	}

	private static volatile Aturan aturan = bawaan();
	private static boolean registered;

	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(ID, new Pemuat());
	}

	public static Aturan aturan() {
		return aturan;
	}

	// ================================================================== lookups

	/** The band 0-7 of the table's columns for hour {@code jam} (04-06, 06-08, 08-11, 11-13, 13-17, 17-19, 19-23, 23-04). */
	public static int pita(int jam) {
		int[] mulai = aturan.jamMulai();
		int h = Math.floorMod(jam, 24);
		int band = mulai.length - 1;
		for (int i = 0; i < mulai.length; i++) {
			if (h >= mulai[i]) {
				band = i;
			}
		}
		// hours before the first column's start (00-03) belong to the last band (23-04)
		return h < mulai[0] ? mulai.length - 1 : band;
	}

	/** The plain multiplier of zone kind {@code jenis} at hour {@code jam} (unknown kinds count as {@code alun}). */
	public static double pengali(String jenis, int jam) {
		double[] row = aturan.pengali().get(jenis);
		if (row == null) {
			row = aturan.pengali().get("alun");
		}
		int band = pita(jam);
		return row == null || band >= row.length ? 0.5D : row[band];
	}

	/** The multiplier with event {@code acara} applied (its kinds lifted to at least the event's value). */
	public static double pengali(String jenis, int jam, @Nullable String acara) {
		double m = pengali(jenis, jam);
		if (acara == null || acara.isEmpty()) {
			return m;
		}
		for (Map.Entry<String, Map<String, Double>> e : aturan.acara().entrySet()) {
			if (acara.startsWith(e.getKey())) {
				Double lift = e.getValue().get(jenis);
				if (lift != null) {
					m = Math.max(m, lift);
				}
			}
		}
		return m;
	}

	/** Capacity of one titik of kind {@code jenis} (0 for doors, building boxes, street nodes and other non-places). */
	public static int kapasitasTitik(String jenis) {
		Integer k = aturan.kapasitas().get(jenis);
		if (k != null) {
			return k;
		}
		if (jenis.startsWith("kerja:")) {
			return aturan.kapasitas().getOrDefault("kerja:*", 1);
		}
		return 0;
	}

	/** The zone kind a titik of kind {@code jenis} lends to an open field (null: it lends none, e.g. doors). */
	public static @Nullable String jenisTitik(String jenis) {
		String z = aturan.titikJenis().get(jenis);
		if (z == null && jenis.startsWith("kerja:")) {
			z = aturan.titikJenis().get("kerja:*");
		}
		return z;
	}

	/** The zone kind of a building by its name ({@code rumah_makan_teratai} -> makan), or null when no rule matches. */
	public static @Nullable String jenisNama(String nama) {
		if (nama == null || nama.isEmpty()) {
			return null;
		}
		String n = nama.toLowerCase(Locale.ROOT);
		for (String[] rule : aturan.nama()) {
			if (n.contains(rule[0])) {
				return rule[1];
			}
		}
		return null;
	}

	/** True when a goal ({@code Kegiatan.tujuan}) is a general one the balancer places: titik kinds or {@code @kedai/@depan_lapak/@jalan}. */
	public static boolean umum(String tujuan) {
		if (tujuan.startsWith("@")) {
			int colon = tujuan.indexOf(':');
			return aturan.umum().contains(colon > 0 ? tujuan.substring(0, colon) : tujuan);
		}
		return aturan.umum().contains(tujuan);
	}

	// ================================================================== targets

	/**
	 * The targets of one hour.
	 *
	 * @param target {@code T_z}
	 * @param topi   {@code C_z}
	 * @param lantai {@code L_z}
	 * @param pengali the multiplier used per zone
	 * @param bebas  {@code N_bebas}
	 */
	public record Sasaran(double[] target, int[] topi, int[] lantai, double[] pengali, int bebas) {
		public static final Sasaran KOSONG = new Sasaran(new double[0], new int[0], new int[0], new double[0], 0);

		public int jumlah() {
			return target.length;
		}
	}

	/**
	 * The targets of the zones.
	 *
	 * @param kapasitas {@code K_z}
	 * @param jenis     zone kinds
	 * @param tetap     residents with a fixed duty in each zone this hour
	 * @param bebas     free residents to share out
	 * @param jam       the place's hour
	 * @param acara     the running event or null
	 * @param acaraZona the zone of a scene at x,z ({@code tonton:}) or -1
	 * @param kapZona   {@code kotaWarga.kapZona}
	 * @param lantaiZona {@code kotaWarga.lantaiZona}
	 */
	public static Sasaran hitung(int[] kapasitas, String[] jenis, int[] tetap, int bebas, int jam, @Nullable String acara, int acaraZona,
			double kapZona, double lantaiZona) {
		return hitung(kapasitas, jenis, tetap, bebas, jam, acara, acaraZona, kapZona, lantaiZona, TANPA_HARI);
	}

	/** "No day": {@link #hitung} without the week and season rhythm. */
	public static final long TANPA_HARI = Long.MIN_VALUE;

	/**
	 * {@link #hitung} with the week and season rhythm of world day {@code hari} on every multiplier (42S-R
	 * {@code IramaMingguan.pengaliZona}: big market day, livestock day, rest day, winter at home).
	 */
	public static Sasaran hitung(int[] kapasitas, String[] jenis, int[] tetap, int bebas, int jam, @Nullable String acara, int acaraZona,
			double kapZona, double lantaiZona, long hari) {
		return hitung(kapasitas, jenis, tetap, bebas, jam, acara, acaraZona, kapZona, lantaiZona, hari, null);
	}

	/** Share of the demand seen in the data (what the free residents want right now) in the split of a target; the rest is the table. */
	static final double BOBOT_MINAT = 0.75D;
	/** Fewest free residents with a known wish before the observed demand is used at all. */
	static final int MINAT_MIN = 8;

	/**
	 * {@link #hitung} with the observed demand: {@code minat[z]} = free residents who want a spot that lies in zone {@code z}
	 * right now (each wish shared over the zones holding a matching titik, {@code Penyeimbang.hitung}). The split of the free
	 * residents between zones is {@link #BOBOT_MINAT} what they actually want and the rest the hour table, so a lunch hour
	 * that sends forty people to the tables, or monks who meditate all night in one court, sets those zones' targets high
	 * instead of leaving them "over target".
	 */
	public static Sasaran hitung(int[] kapasitas, String[] jenis, int[] tetap, int bebas, int jam, @Nullable String acara, int acaraZona,
			double kapZona, double lantaiZona, long hari, double @Nullable [] minat) {
		int n = kapasitas.length;
		double[] m = new double[n];
		double[] bobot = new double[n];
		double sum = 0.0D;
		for (int z = 0; z < n; z++) {
			m[z] = pengali(jenis[z], jam, acara);
			if (hari != TANPA_HARI) {
				m[z] *= Math.max(0.0D, IramaMingguan.pengaliZona(jenis[z], hari, jam));
			}
			if (z == acaraZona) {
				m[z] = Math.max(m[z], 1.0D) * 2.0D;
			}
			double sisa = Math.max(0, kapasitas[z] - tetap[z]);
			bobot[z] = sisa * m[z];
			sum += bobot[z];
		}
		if (minat != null) {
			sum = minatkan(bobot, minat, sum);
		}
		// share the free residents by K' x m, never past a zone's room (K - tetap): what a full zone cannot take goes to the
		// others in the same proportions (water filling), so the targets add up to N_bebas while the place has room
		double[] free = new double[n];
		boolean[] full = new boolean[n];
		double left = bebas;
		for (int round = 0; round < n && left > 1.0E-9D && sum > 0.0D; round++) {
			double share = left / sum;
			double given = 0.0D;
			double nextSum = 0.0D;
			for (int z = 0; z < n; z++) {
				if (full[z] || bobot[z] <= 0.0D) {
					continue;
				}
				double room = Math.max(0, kapasitas[z] - tetap[z]) - free[z];
				double add = bobot[z] * share;
				if (add >= room) {
					add = Math.max(0.0D, room);
					full[z] = true;
				} else {
					nextSum += bobot[z];
				}
				free[z] += add;
				given += add;
			}
			left -= given;
			sum = nextSum;
		}
		double[] target = new double[n];
		int[] topi = new int[n];
		int[] lantai = new int[n];
		for (int z = 0; z < n; z++) {
			double t = tetap[z] + free[z];
			target[z] = t;
			topi[z] = (int) Math.ceil(kapZona * t) + 1;
			lantai[z] = kapasitas[z] >= 4 ? (int) Math.floor(lantaiZona * t) : 0;
		}
		return new Sasaran(target, topi, lantai, m, bebas);
	}

	/** Blends the observed demand into the zone weights (zone weight -> mix of its table share and its demand share); the new sum. */
	private static double minatkan(double[] bobot, double[] minat, double sum) {
		double total = 0.0D;
		for (double v : minat) {
			total += v;
		}
		if (total < MINAT_MIN || sum <= 0.0D) {
			return sum;
		}
		double baru = 0.0D;
		for (int z = 0; z < bobot.length; z++) {
			bobot[z] = (1.0D - BOBOT_MINAT) * bobot[z] / sum + BOBOT_MINAT * (z < minat.length ? minat[z] : 0.0D) / total;
			baru += bobot[z];
		}
		return baru;
	}

	/** Largest-remainder split of {@code total} by {@code bobot}, at least {@code minimal} for every positive weight while possible. */
	public static int[] sisaTerbesar(double[] bobot, int total, int minimal) {
		int n = bobot.length;
		int[] out = new int[n];
		if (total <= 0 || n == 0) {
			return out;
		}
		double sum = 0.0D;
		int positif = 0;
		for (double b : bobot) {
			if (b > 0.0D) {
				sum += b;
				positif++;
			}
		}
		if (sum <= 0.0D) {
			return out;
		}
		int left = total;
		if (minimal > 0 && positif * minimal <= total) {
			for (int i = 0; i < n; i++) {
				if (bobot[i] > 0.0D) {
					out[i] = minimal;
					left -= minimal;
				}
			}
		}
		double[] rest = new double[n];
		int given = 0;
		for (int i = 0; i < n; i++) {
			if (bobot[i] <= 0.0D) {
				continue;
			}
			double exact = bobot[i] / sum * left;
			int whole = (int) Math.floor(exact);
			out[i] += whole;
			given += whole;
			rest[i] = exact - whole;
		}
		for (int k = given; k < left; k++) {
			int best = -1;
			for (int i = 0; i < n; i++) {
				if (bobot[i] > 0.0D && (best < 0 || rest[i] > rest[best])) {
					best = i;
				}
			}
			if (best < 0) {
				break;
			}
			out[best]++;
			rest[best] = -1.0D;
		}
		return out;
	}

	/** Gini coefficient of non-negative values (0 = all equal; 0 for fewer than two values). */
	public static double gini(double[] x) {
		int n = x.length;
		if (n < 2) {
			return 0.0D;
		}
		double sum = 0.0D;
		double diff = 0.0D;
		for (int i = 0; i < n; i++) {
			sum += x[i];
			for (int j = 0; j < n; j++) {
				diff += Math.abs(x[i] - x[j]);
			}
		}
		return sum <= 0.0D ? 0.0D : diff / (2.0D * n * sum);
	}

	// ================================================================== data

	private static Aturan bawaan() {
		Map<String, double[]> m = new LinkedHashMap<>();
		m.put("pasar", new double[]{0.1, 0.8, 1.0, 0.6, 0.6, 0.8, 0.2, 0.0});
		m.put("makan", new double[]{0.1, 0.6, 0.3, 1.0, 0.4, 0.9, 0.5, 0.0});
		m.put("teh", new double[]{0.0, 0.3, 0.5, 0.5, 0.9, 0.6, 0.5, 0.0});
		m.put("arak", new double[]{0.0, 0.0, 0.0, 0.2, 0.2, 0.5, 1.0, 0.4});
		m.put("rumah", new double[]{1.0, 0.6, 0.25, 0.4, 0.25, 0.5, 0.7, 1.0});
		m.put("kuil", new double[]{1.0, 0.6, 0.3, 0.2, 0.3, 0.6, 0.2, 0.0});
		m.put("sekolah", new double[]{0.0, 0.2, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0});
		m.put("kerja", new double[]{0.0, 0.5, 1.0, 0.4, 1.0, 0.3, 0.0, 0.0});
		m.put("ladang", new double[]{0.2, 0.9, 1.0, 0.4, 0.9, 0.4, 0.0, 0.0});
		m.put("alun", new double[]{0.6, 0.7, 0.5, 0.4, 0.7, 0.9, 0.7, 0.05});
		m.put("ruas", new double[]{0.1, 0.9, 0.6, 0.7, 0.6, 0.9, 0.5, 0.05});
		m.put("gerbang", new double[]{0.2, 0.6, 0.5, 0.4, 0.5, 0.6, 0.3, 0.1});
		Map<String, Map<String, Double>> acara = new LinkedHashMap<>();
		acara.put("festival_lentera", Map.of("alun", 1.0, "ruas", 1.0, "kuil", 0.8));
		acara.put("turnamen", Map.of("alun", 1.0));
		acara.put("pertemuan_sekte", Map.of("alun", 1.0));
		acara.put("pasar_hantu", Map.of("pasar", 1.0, "ruas", 0.8));
		Map<String, Integer> k = new HashMap<>();
		for (String s : List.of("kursi", "kursi_lantai", "meja_makan", "kasur", "penonton", "meditasi", "meja_tulis", "jaga", "alat_tenun", "jalan_lentera",
				"pancing", "jongkok", "kerja:*")) {
			k.put(s, 1);
		}
		for (String s : List.of("lapak", "obrol", "pandang", "dupa", "latihan", "tambatan", "kandang_unta", "tiba")) {
			k.put(s, 2);
		}
		k.put("main", 3);
		k.put("catur", 5);
		k.put("sumur", 4);
		k.put("sumur_timba", 4);
		k.put("panggung", 12);
		Map<String, String> tj = new HashMap<>();
		for (String s : List.of("lapak", "kerja:kios", "kerja:timbangan")) {
			tj.put(s, "pasar");
		}
		tj.put("kerja:teko", "teh");
		for (String s : List.of("kerja:kuali", "kerja:tungku", "meja_makan")) {
			tj.put(s, "makan");
		}
		for (String s : List.of("dupa", "kerja:dupa")) {
			tj.put(s, "kuil");
		}
		for (String s : List.of("meja_tulis", "kerja:meja_tulis")) {
			tj.put(s, "sekolah");
		}
		for (String s : List.of("kerja:cangkul", "kerja:sabit", "kerja:pancing", "kerja:jala", "kerja:dayung", "tambatan", "jalur_perahu", "jalur_kapal",
				"pancing", "kandang_unta")) {
			tj.put(s, "ladang");
		}
		for (String s : List.of("main", "obrol", "panggung", "penonton", "pandang", "catur", "sumur", "sumur_timba", "latihan", "meditasi", "kursi",
				"kursi_lantai")) {
			tj.put(s, "alun");
		}
		tj.put("jalan_lentera", "ruas");
		tj.put("lentera_air", "ruas");
		for (String s : List.of("jaga", "tiba", "jaga_suar", "arena_pintu")) {
			tj.put(s, "gerbang");
		}
		tj.put("kasur", "rumah");
		tj.put("peti_milik", "rumah");
		tj.put("kerja:*", "kerja");
		List<String[]> nama = new ArrayList<>();
		String[][] rules = {{"rumah_makan", "makan"}, {"warung", "makan"}, {"penginapan", "makan"}, {"karavanserai", "makan"}, {"kedai_teh", "teh"},
				{"paviliun_teh", "teh"}, {"teh", "teh"}, {"kedai", "arak"}, {"arak", "arak"}, {"judi", "arak"}, {"paviliun_minum", "arak"},
				{"perjamuan", "arak"}, {"pasar", "pasar"}, {"kios", "pasar"}, {"toko", "pasar"}, {"lelang", "pasar"}, {"rumah_tukar", "pasar"},
				{"kuil", "kuil"}, {"biara", "kuil"}, {"pagoda", "kuil"}, {"ibadah", "kuil"}, {"sekolah", "sekolah"}, {"perpustakaan", "sekolah"},
				{"arsip", "sekolah"}, {"pandai", "kerja"}, {"bengkel", "kerja"}, {"gudang", "kerja"}, {"tenun", "kerja"}, {"lumbung", "kerja"},
				{"kandang", "kerja"}, {"dermaga", "ladang"}, {"kantor", "kerja"}, {"rumah_uang", "kerja"}, {"pos", "gerbang"}, {"penjaga", "gerbang"},
				{"mercusuar", "gerbang"}, {"lapangan", "alun"}, {"taman", "alun"}, {"rumah", "rumah"}, {"siheyuan", "rumah"}, {"pondok", "rumah"},
				{"wisma", "rumah"}, {"paviliun", "rumah"}};
		for (String[] r : rules) {
			nama.add(r);
		}
		Set<String> umum = new LinkedHashSet<>(List.of("obrol", "main", "kursi", "kursi_lantai", "meja_makan", "dupa", "pandang", "sumur", "sumur_timba",
				"catur", "penonton", "lapak", "latihan", "meditasi", "@kedai", "@depan_lapak", "@jalan"));
		return new Aturan(new int[]{4, 6, 8, 11, 13, 17, 19, 23}, m, acara, k, 6.0D, 25.0D, tj, nama, umum);
	}

	/** Reads {@code zona.json}; every missing part keeps the default. */
	static Aturan baca(JsonObject o, Aturan dasar) {
		int[] jam = dasar.jamMulai();
		if (o.has("jam") && o.get("jam").isJsonArray() && o.getAsJsonArray("jam").size() == 8) {
			JsonArray a = o.getAsJsonArray("jam");
			jam = new int[8];
			for (int i = 0; i < 8; i++) {
				jam[i] = Math.floorMod(a.get(i).getAsInt(), 24);
			}
		}
		Map<String, double[]> pengali = new LinkedHashMap<>(dasar.pengali());
		if (o.has("pengali") && o.get("pengali").isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("pengali").entrySet()) {
				if (e.getValue().isJsonArray() && e.getValue().getAsJsonArray().size() == jam.length) {
					JsonArray a = e.getValue().getAsJsonArray();
					double[] row = new double[a.size()];
					for (int i = 0; i < row.length; i++) {
						row[i] = Math.max(0.0D, Math.min(4.0D, a.get(i).getAsDouble()));
					}
					pengali.put(e.getKey(), row);
				}
			}
		}
		Map<String, Map<String, Double>> acara = new LinkedHashMap<>(dasar.acara());
		if (o.has("acara") && o.get("acara").isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("acara").entrySet()) {
				if (e.getKey().startsWith("_") || !e.getValue().isJsonObject()) {
					continue;
				}
				Map<String, Double> lift = new HashMap<>();
				for (Map.Entry<String, JsonElement> f : e.getValue().getAsJsonObject().entrySet()) {
					lift.put(f.getKey(), f.getValue().getAsDouble());
				}
				acara.put(e.getKey(), Map.copyOf(lift));
			}
		}
		Map<String, Integer> kap = new HashMap<>(dasar.kapasitas());
		if (o.has("kapasitas") && o.get("kapasitas").isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("kapasitas").entrySet()) {
				kap.put(e.getKey(), Math.max(0, Math.min(64, e.getValue().getAsInt())));
			}
		}
		double ruas = o.has("ruasPerPejalan") ? Math.max(1.0D, o.get("ruasPerPejalan").getAsDouble()) : dasar.ruasPerPejalan();
		double luas = o.has("luasPerOrang") ? Math.max(1.0D, o.get("luasPerOrang").getAsDouble()) : dasar.luasPerOrang();
		Map<String, String> tj = new HashMap<>(dasar.titikJenis());
		if (o.has("titikJenis") && o.get("titikJenis").isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("titikJenis").entrySet()) {
				tj.put(e.getKey(), e.getValue().getAsString());
			}
		}
		List<String[]> nama = dasar.nama();
		if (o.has("nama") && o.get("nama").isJsonArray()) {
			nama = new ArrayList<>();
			for (JsonElement e : o.getAsJsonArray("nama")) {
				if (e.isJsonArray() && e.getAsJsonArray().size() == 2) {
					nama.add(new String[]{e.getAsJsonArray().get(0).getAsString(), e.getAsJsonArray().get(1).getAsString()});
				}
			}
		}
		Set<String> umum = dasar.umum();
		if (o.has("umum") && o.get("umum").isJsonArray()) {
			umum = new LinkedHashSet<>();
			for (JsonElement e : o.getAsJsonArray("umum")) {
				umum.add(e.getAsString());
			}
		}
		return new Aturan(jam, Map.copyOf(pengali), Map.copyOf(acara), Map.copyOf(kap), ruas, luas, Map.copyOf(tj), List.copyOf(nama), Set.copyOf(umum));
	}

	private static final class Pemuat extends SimplePreparableReloadListener<Aturan> {
		@Override
		protected Aturan prepare(ResourceManager manager, ProfilerFiller profiler) {
			Optional<Resource> file = manager.getResource(BERKAS);
			if (file.isEmpty()) {
				return bawaan();
			}
			try (Reader reader = file.get().openAsReader()) {
				JsonElement root = JsonParser.parseReader(reader);
				return root.isJsonObject() ? baca(root.getAsJsonObject(), bawaan()) : bawaan();
			} catch (Exception e) {
				Kultivator.LOGGER.warn("Zone rules {} could not be read; defaults kept", BERKAS, e);
				return bawaan();
			}
		}

		@Override
		protected void apply(Aturan prepared, ResourceManager manager, ProfilerFiller profiler) {
			aturan = prepared;
			PetaZona.lupakanSemua();
		}
	}

	private Kapasitas() {
	}
}
