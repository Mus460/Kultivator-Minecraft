package dev.kultivator.client.sosok.kulit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kultivator.Kultivator;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The look recipe {@code assets/kultivator/sosok/resep.json} (DESIGN 40.3.10, 40.4; written by
 * {@code tools/gen_sosok_resep.py}), parsed once into typed tables for {@link RupaResolver}: part weights, the colour
 * tables (skin, hair, iris, Dao, realm, rank trims, clan and bright colours), the role rows 40.4.1 A-D
 * ({@code peran.<key>}), the disciple rank rows 40.4.2 ({@code peran.murid.<n>}), the life-path rows 40.4.3
 * ({@code jalan.<id>}), the hair and garment styles and the extra layers. The insertion order of every JSON object is
 * kept (Gson keeps it), because the resolver iterates some maps in file order exactly like the Python resolver.
 * <p>
 * Read through the resource manager (a resource pack may restyle it); {@link #lepas} forgets it ({@code /sosok
 * muat_ulang}, world closed), the next look reads it again. A missing or broken file logs one line and leaves
 * {@link #get} null: bodies then keep the class fallback atlases.
 */
public final class Resep {

	public static final Identifier ID = Kultivator.id("sosok/resep.json");

	private static @Nullable Resep instance;
	private static boolean gagal;

	public final int[] lin = new int[256];
	public final int[] srgb = new int[4096];
	public final Map<String, Integer> warna = new HashMap<>();
	public final Map<String, Integer> logam = new HashMap<>();
	public final List<Warna> kulit = new ArrayList<>();
	public final LinkedHashMap<String, Warna> rambutWarna = new LinkedHashMap<>();
	public final List<Warna> iris = new ArrayList<>();
	public final List<Dao> dao = new ArrayList<>();
	public final List<Integer> ranah = new ArrayList<>();
	public final List<Integer> tepiPangkat = new ArrayList<>();
	public final List<String> lencanaPangkat = new ArrayList<>();
	public final List<Integer> klan = new ArrayList<>();
	public final List<Integer> cerah = new ArrayList<>();
	public final Map<String, Integer> sikap = new HashMap<>();
	public final List<String> profilJalan = new ArrayList<>();
	public final Map<String, GayaRambut> gayaRambut = new HashMap<>();
	public final Map<String, GayaBusana> gayaBusana = new HashMap<>();
	public final Map<String, List<String>> kelompokKubus = new HashMap<>();
	public final Map<String, Tambahan> tambahan = new HashMap<>();
	public final List<String> kubusRambut = new ArrayList<>();
	public final List<String> kubusBusana = new ArrayList<>();
	public final Map<String, Baris> peran = new HashMap<>();
	public final Map<String, Jalan> jalan = new HashMap<>();

	/** A colour with a weight (skin tones, hair colours, iris). */
	public record Warna(String nama, int rgb, int bobot) {
	}

	/** One Dao row (40.3.8): cloth, embroidery, metal name, iris colour and the realm it shows from (-1 none). */
	public record Dao(String id, int kain, int sulam, String logam, int iris, int irisRanah, String motif) {
	}

	public record GayaRambut(String k, String u, List<String> kubus, boolean kecil, boolean satu) {
	}

	/** A garment style: cube groups, footwear, default cloth tokens, wide skirt, head cloth, rank badge. */
	public record GayaBusana(List<String> kubus, String kaki, List<String> kain, boolean lebar, String kepala, String buzi) {
	}

	/** An extra layer (40.3.7 "Tambahan"): cubes, painted sheet, head cloth, hidden cubes, extra trim level, variant. */
	public record Tambahan(List<String> kubus, String lembar, String kepala, List<String> sembunyi, int tepiNaik, String varian) {
	}

	/** One look row ({@code peran.<key>}, 40.4.1/40.4.2). Field names follow the JSON keys. */
	public static final class Baris {
		public List<SplitMix64.Berbobot<String>> gayaL, gayaP, gayaAnak, mata, alisL, alisP, mulut, bentuk;
		public List<String> kain, rambutL, rambutP, tambahan, kubus, kubusP;
		public String hiasanL, hiasanP, kulit, rambutWarna, iris, buzi, kaki, kepala, tema, topi, mataTetap, jalan, kelas;
		public LinkedHashMap<String, Integer> tanda, tandaP, rw, tambahanAcak, propAcak;
		public int kulitGelap, rambutMerah, luka, pangkat, prop, sikapL, sikapP;
		public boolean melayang, pudar, buddha, tambalan, lenganTelanjang, tandaRanah, selendangP, taoDao, murid, topiMenutup;
	}

	/** One life-path row ({@code jalan.<id>}, 40.4.3), added on top of the rank row. */
	public static final class Jalan {
		public List<String> rambutL, rambutP, kubus, kubusP, tambahan;
		public List<SplitMix64.Berbobot<String>> mata, alisP;
		public LinkedHashMap<String, Integer> tanda, tandaP;
		public int kulitGelap, tepiNaik, prop, sikap;
		public String rambutWarna, iris, kainTepi, kain2, gayaTanpaSeragam, gayaIntiEmas;
		public boolean tambalan;
	}

	/** The recipe, read on first use; null when the file is missing or broken (logged once). */
	public static @Nullable Resep get() {
		if (instance == null && !gagal) {
			try {
				Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(ID);
				if (res.isEmpty()) {
					throw new IllegalStateException("missing " + ID);
				}
				try (Reader reader = res.get().openAsReader()) {
					instance = parse(JsonParser.parseReader(reader).getAsJsonObject());
				}
			} catch (Exception e) {
				gagal = true;
				Kultivator.LOGGER.error("[Sosok] cannot read {}; bodies keep the fallback atlases", ID, e);
			}
		}
		return instance;
	}

	/** Installs a recipe parsed outside the game (the scratch parity harness of 40K; never called by the mod). */
	static void pasangUntukUji(Resep r) {
		instance = r;
		gagal = false;
	}

	/** Forgets the recipe (read again on next use). */
	public static void lepas() {
		instance = null;
		gagal = false;
	}

	/** A named colour: {@code #RRGGBB}, a palette name or a metal name; null when unknown. */
	public @Nullable Integer bernama(String tok) {
		if (tok.startsWith("#")) {
			return Palet.hex(tok);
		}
		Integer c = warna.get(tok);
		return c != null ? c : logam.get(tok);
	}

	// ================================================================ parsing

	static Resep parse(JsonObject o) {
		Resep r = new Resep();
		JsonObject tabel = o.getAsJsonObject("tabel");
		ints(tabel.getAsJsonArray("lin"), r.lin);
		ints(tabel.getAsJsonArray("srgb"), r.srgb);
		for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("warna").entrySet()) {
			r.warna.put(e.getKey(), Palet.hex(e.getValue().getAsString()));
		}
		for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("logam").entrySet()) {
			r.logam.put(e.getKey(), Palet.hex(e.getValue().getAsString()));
		}
		for (JsonElement e : o.getAsJsonArray("kulit")) {
			JsonObject k = e.getAsJsonObject();
			r.kulit.add(new Warna(k.get("nama").getAsString(), Palet.hex(k.get("rgb").getAsString()), k.get("bobot").getAsInt()));
		}
		for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("rambut_warna").entrySet()) {
			JsonObject k = e.getValue().getAsJsonObject();
			r.rambutWarna.put(e.getKey(), new Warna(e.getKey(), Palet.hex(k.get("rgb").getAsString()), k.get("bobot").getAsInt()));
		}
		for (JsonElement e : o.getAsJsonArray("iris")) {
			JsonObject k = e.getAsJsonObject();
			r.iris.add(new Warna("", Palet.hex(k.get("rgb").getAsString()), k.get("bobot").getAsInt()));
		}
		for (JsonElement e : o.getAsJsonArray("dao")) {
			JsonObject k = e.getAsJsonObject();
			int iris = -1;
			int irisRanah = 99;
			JsonElement ie = k.get("iris");
			if (ie != null && ie.isJsonArray()) {
				iris = Palet.hex(ie.getAsJsonArray().get(0).getAsString());
				irisRanah = ie.getAsJsonArray().get(1).getAsInt();
			}
			r.dao.add(new Dao(k.get("id").getAsString(), Palet.hex(k.get("kain").getAsString()), Palet.hex(k.get("sulam").getAsString()),
					k.get("logam").getAsString(), iris, irisRanah, k.get("motif").getAsString()));
		}
		hexes(o.getAsJsonArray("ranah"), r.ranah);
		hexes(o.getAsJsonArray("tepi_pangkat"), r.tepiPangkat);
		r.lencanaPangkat.addAll(strings(o.getAsJsonArray("lencana_pangkat")));
		hexes(o.getAsJsonArray("klan"), r.klan);
		hexes(o.getAsJsonArray("cerah"), r.cerah);
		for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("sikap").entrySet()) {
			r.sikap.put(e.getKey(), e.getValue().getAsInt());
		}
		r.profilJalan.addAll(strings(o.getAsJsonArray("profil_jalan")));
		for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("gaya_rambut").entrySet()) {
			JsonObject k = e.getValue().getAsJsonObject();
			r.gayaRambut.put(e.getKey(), new GayaRambut(str(k, "k"), str(k, "u"), strings(k.getAsJsonArray("kubus")), bool(k, "kecil"),
					bool(k, "satu")));
		}
		for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("gaya_busana").entrySet()) {
			JsonObject k = e.getValue().getAsJsonObject();
			List<String> kain = k.has("kain") ? strings(k.getAsJsonArray("kain")) : List.of("#8A7A60", "#EDE6D6", "#6A4A30", "perunggu");
			r.gayaBusana.put(e.getKey(), new GayaBusana(strings(k.getAsJsonArray("kubus")), k.has("kaki") ? str(k, "kaki") : "kain", kain,
					bool(k, "lebar"), str(k, "kepala"), str(k, "buzi")));
		}
		for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("kelompok_kubus").entrySet()) {
			r.kelompokKubus.put(e.getKey(), strings(e.getValue().getAsJsonArray()));
		}
		for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("tambahan").entrySet()) {
			JsonObject k = e.getValue().getAsJsonObject();
			r.tambahan.put(e.getKey(), new Tambahan(k.has("kubus") ? strings(k.getAsJsonArray("kubus")) : List.of(), str(k, "lembar"),
					str(k, "kepala"), k.has("sembunyi") ? strings(k.getAsJsonArray("sembunyi")) : List.of(), integer(k, "tepi_naik"),
					str(k, "varian")));
		}
		r.kubusRambut.addAll(strings(o.getAsJsonArray("kubus_rambut")));
		r.kubusBusana.addAll(strings(o.getAsJsonArray("kubus_busana")));
		for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("peran").entrySet()) {
			r.peran.put(e.getKey(), baris(e.getValue().getAsJsonObject()));
		}
		for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("jalan").entrySet()) {
			r.jalan.put(e.getKey(), jalan(e.getValue().getAsJsonObject()));
		}
		return r;
	}

	private static Baris baris(JsonObject k) {
		Baris b = new Baris();
		b.gayaL = weighted(k, "gaya_l");
		b.gayaP = weighted(k, "gaya_p");
		b.gayaAnak = weighted(k, "gaya_anak");
		b.mata = weighted(k, "mata");
		b.alisL = weighted(k, "alis_l");
		b.alisP = weighted(k, "alis_p");
		b.mulut = weighted(k, "mulut");
		b.bentuk = weighted(k, "bentuk");
		b.kain = list(k, "kain");
		b.rambutL = list(k, "rambut_l");
		b.rambutP = list(k, "rambut_p");
		b.tambahan = list(k, "tambahan");
		b.kubus = list(k, "kubus");
		b.kubusP = list(k, "kubus_p");
		b.hiasanL = str(k, "hiasan_l");
		b.hiasanP = str(k, "hiasan_p");
		b.kulit = str(k, "kulit");
		b.rambutWarna = str(k, "rambut_warna");
		b.iris = str(k, "iris");
		b.buzi = str(k, "buzi");
		b.kaki = str(k, "kaki");
		b.kepala = str(k, "kepala");
		b.tema = str(k, "tema");
		b.topi = str(k, "topi");
		b.mataTetap = str(k, "mata_tetap");
		b.jalan = str(k, "jalan");
		b.kelas = str(k, "kelas");
		b.tanda = map(k, "tanda");
		b.tandaP = map(k, "tanda_p");
		b.rw = map(k, "rw");
		b.tambahanAcak = map(k, "tambahan_acak");
		b.propAcak = map(k, "prop_acak");
		b.kulitGelap = integer(k, "kulit_gelap");
		b.rambutMerah = integer(k, "rambut_merah");
		b.luka = integer(k, "luka");
		b.pangkat = k.has("pangkat") ? k.get("pangkat").getAsInt() : -1;
		b.prop = integer(k, "prop");
		b.sikapL = integer(k, "sikap_l");
		b.sikapP = integer(k, "sikap_p");
		b.melayang = bool(k, "melayang");
		b.pudar = bool(k, "pudar");
		b.buddha = bool(k, "buddha");
		b.tambalan = bool(k, "tambalan");
		b.lenganTelanjang = bool(k, "lengan_telanjang");
		b.tandaRanah = bool(k, "tanda_ranah");
		b.selendangP = bool(k, "selendang_p");
		b.taoDao = bool(k, "tao_dao");
		b.murid = bool(k, "murid");
		b.topiMenutup = bool(k, "topi_menutup");
		return b;
	}

	private static Jalan jalan(JsonObject k) {
		Jalan j = new Jalan();
		j.rambutL = list(k, "rambut_l");
		j.rambutP = list(k, "rambut_p");
		j.kubus = list(k, "kubus");
		j.kubusP = list(k, "kubus_p");
		j.tambahan = list(k, "tambahan");
		j.mata = weighted(k, "mata");
		j.alisP = weighted(k, "alis_p");
		j.tanda = map(k, "tanda");
		j.tandaP = map(k, "tanda_p");
		j.kulitGelap = integer(k, "kulit_gelap");
		j.tepiNaik = integer(k, "tepi_naik");
		j.prop = integer(k, "prop");
		j.sikap = integer(k, "sikap");
		j.rambutWarna = str(k, "rambut_warna");
		j.iris = str(k, "iris");
		j.kainTepi = str(k, "kain_tepi");
		j.kain2 = str(k, "kain2");
		j.gayaTanpaSeragam = str(k, "gaya_tanpa_seragam");
		j.gayaIntiEmas = str(k, "gaya_inti_emas");
		j.tambalan = bool(k, "tambalan");
		return j;
	}

	private static List<SplitMix64.Berbobot<String>> weighted(JsonObject k, String key) {
		if (!k.has(key)) {
			return List.of();
		}
		List<SplitMix64.Berbobot<String>> out = new ArrayList<>();
		for (JsonElement e : k.getAsJsonArray(key)) {
			JsonArray a = e.getAsJsonArray();
			out.add(new SplitMix64.Berbobot<>(a.get(0).getAsString(), a.get(1).getAsInt()));
		}
		return Collections.unmodifiableList(out);
	}

	private static LinkedHashMap<String, Integer> map(JsonObject k, String key) {
		LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
		if (k.has(key)) {
			for (Map.Entry<String, JsonElement> e : k.getAsJsonObject(key).entrySet()) {
				out.put(e.getKey(), e.getValue().getAsInt());
			}
		}
		return out;
	}

	private static List<String> list(JsonObject k, String key) {
		return k.has(key) ? strings(k.getAsJsonArray(key)) : List.of();
	}

	private static List<String> strings(JsonArray a) {
		List<String> out = new ArrayList<>(a.size());
		for (JsonElement e : a) {
			out.add(e.getAsString());
		}
		return Collections.unmodifiableList(out);
	}

	private static void ints(JsonArray a, int[] out) {
		for (int i = 0; i < out.length && i < a.size(); i++) {
			out[i] = a.get(i).getAsInt();
		}
	}

	private static void hexes(JsonArray a, List<Integer> out) {
		for (JsonElement e : a) {
			out.add(Palet.hex(e.getAsString()));
		}
	}

	private static String str(JsonObject k, String key) {
		JsonElement e = k.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	private static int integer(JsonObject k, String key) {
		JsonElement e = k.get(key);
		return e == null || e.isJsonNull() ? 0 : e.getAsInt();
	}

	private static boolean bool(JsonObject k, String key) {
		JsonElement e = k.get(key);
		return e != null && !e.isJsonNull() && e.getAsBoolean();
	}

	private Resep() {
	}
}
