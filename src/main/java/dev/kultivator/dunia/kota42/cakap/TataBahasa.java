package dev.kultivator.dunia.kota42.cakap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kultivator.Kultivator;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The typed grammar (DESIGN 42.5A.8; files {@code percakapan/tata/<kelompok>.json} written by
 * {@code tools/gen_kota_hidup_cakap.py} from the author sources {@code tools/kota_cakap_sumber/*.txt}; format in
 * {@link CakapData}). A rule names an intent (or none: a fragment reached through slots), a topic, a condition and weighted
 * frames; a frame is a lang key {@code kultivator.cakap.<kunci>} with positional slots and optional register/dialect
 * filters and variant suffixes ({@code .kasar}, {@code .pelabuhan}, {@code .p}, {@code .kuno.p} ...); a slot is typed
 * ({@link TipeSlot}), a fragment rule or a lexicon word. {@link Realisasi} expands it.
 * <p>
 * Loaded through {@link CakapReload#saatMuat} together with {@code dialek.json} ({@link Dialek}) and {@code tik.json}
 * ({@link Tik}); {@link #parse} is pure so the golden runner can build a table from files on disk.
 */
public final class TataBahasa {

	/** Maximum expansion depth (root frame = 1). */
	public static final int KEDALAMAN = 4;

	/**
	 * One slot of a rule.
	 *
	 * @param nama     slot name with {@code $}
	 * @param tipe     typed slot, null for fragment and lexicon slots
	 * @param aturan   fragment rule id, null otherwise
	 * @param lex      lexicon word, null otherwise
	 * @param dari     value source ({@code fakta.objek}, {@code pendengar}, {@code konteks.arah} ...), "" for none
	 * @param fitur    features the value carries ({@code jamak}, {@code kelamin})
	 * @param opsional chance the slot is filled (1 = always)
	 */
	public record Slot(String nama, @Nullable TipeSlot tipe, @Nullable String aturan, @Nullable String lex, String dari, List<String> fitur,
			double opsional) {
		static Slot parse(String nama, JsonObject o) {
			String tipe = teksAtauNull(o, "tipe");
			return new Slot(nama, tipe == null ? null : TipeSlot.dariId(tipe), teksAtauNull(o, "aturan"),
					teksAtauNull(o, "lex"), teks(o, "dari", ""), teksList(o, "fitur"), dbl(o, "opsional", 1.0D));
		}
	}

	/**
	 * One frame.
	 *
	 * @param kunci    full lang key
	 * @param slot     slot names in argument order ({@code %1$s} first)
	 * @param bobot    weight
	 * @param register registers it may be said in (empty = all)
	 * @param dialek   dialects it may be said in (empty = all)
	 * @param varian   variant suffixes that exist ({@code kasar}, {@code pelabuhan}, {@code kuno.p} ...), authored order
	 */
	public record Bingkai(String kunci, List<String> slot, int bobot, Set<String> register, Set<String> dialek, List<String> varian) {
		/** True when the frame may be said in this register and dialect. */
		public boolean boleh(String reg, String dia) {
			return (register.isEmpty() || register.contains(reg)) && (dialek.isEmpty() || dialek.contains(dia));
		}
	}

	/**
	 * One rule.
	 *
	 * @param id       rule id ({@code kabar.harga_naik})
	 * @param kelompok group = file name
	 * @param niat     intent id, null for a fragment
	 * @param topik    topic id, null for none
	 * @param syarat   condition (CakapData {@code syarat})
	 * @param bingkai  frames
	 * @param slot     slots by name
	 * @param lanjut   follow-up topics
	 * @param gestur   gesture name, null for the intent default
	 * @param seru     short voice base name, null for none
	 */
	public record Aturan(String id, String kelompok, @Nullable String niat, @Nullable String topik, JsonObject syarat, List<Bingkai> bingkai,
			Map<String, Slot> slot, List<String> lanjut, @Nullable String gestur, @Nullable String seru) {
	}

	/**
	 * The loaded grammar.
	 *
	 * @param aturan rules by id (path order; a later file with the same id replaces the content, keeping the first position)
	 * @param niat   rules with an intent, by intent id, in {@link #aturan} order
	 * @param dialek dialect table of the same load
	 * @param tik    tic table of the same load
	 */
	public record Tabel(Map<String, Aturan> aturan, Map<String, List<Aturan>> niat, Dialek.Tabel dialek, Tik.Tabel tik) {
		public static final Tabel KOSONG = new Tabel(Map.of(), Map.of(), Dialek.Tabel.KOSONG, Tik.Tabel.KOSONG);

		public List<Aturan> untuk(String niatId) {
			return niat.getOrDefault(niatId, List.of());
		}
	}

	private static volatile Tabel tabel = Tabel.KOSONG;
	private static volatile boolean terdaftar;

	/** Registers the reload listener (from {@code Cakap.init}); also done lazily on first use. */
	public static void init() {
		pastikan();
	}

	static void pastikan() {
		if (!terdaftar) {
			synchronized (TataBahasa.class) {
				if (!terdaftar) {
					terdaftar = true;
					CakapReload.saatMuat(TataBahasa::muat);
				}
			}
		}
	}

	public static Tabel tabel() {
		pastikan();
		return tabel;
	}

	private static void muat(Map<String, JsonObject> raw) {
		Tabel t = parse(raw);
		tabel = t;
		Dialek.pasang(t.dialek);
		Tik.pasang(t.tik);
		int frames = 0;
		for (Aturan a : t.aturan.values()) {
			frames += a.bingkai.size();
		}
		Kultivator.LOGGER.info("Conversation grammar: {} rules, {} frames, {} intents, {} dialects, {} tics", t.aturan.size(), frames,
				t.niat.size(), t.dialek.dialek().size(), t.tik.tik().size());
	}

	/** Builds a table from raw files by relative path ({@code tata/kabar}, {@code dialek}, {@code tik}), path order. */
	public static Tabel parse(Map<String, JsonObject> raw) {
		Map<String, Aturan> aturan = new LinkedHashMap<>();
		for (Map.Entry<String, JsonObject> file : raw.entrySet()) {
			if (!file.getKey().startsWith("tata/")) {
				continue;
			}
			String kelompok = teks(file.getValue(), "kelompok", file.getKey().substring(5));
			for (JsonObject o : objekList(file.getValue(), "isi")) {
				try {
					Aturan a = aturan(kelompok, o);
					if (a != null) {
						aturan.put(a.id, a);
					}
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.warn("Grammar rule in {} is malformed; skipped", file.getKey(), exception);
				}
			}
		}
		Map<String, List<Aturan>> niat = new LinkedHashMap<>();
		for (Aturan a : aturan.values()) {
			if (a.niat != null) {
				niat.computeIfAbsent(a.niat, k -> new ArrayList<>()).add(a);
			}
		}
		niat.replaceAll((k, v) -> List.copyOf(v));
		return new Tabel(Collections.unmodifiableMap(aturan), Collections.unmodifiableMap(niat), Dialek.Tabel.parse(raw.get(CakapData.DIALEK)),
				Tik.Tabel.parse(raw.get(CakapData.TIK)));
	}

	private static @Nullable Aturan aturan(String kelompok, JsonObject o) {
		String id = teks(o, "aturan", "");
		if (id.isEmpty()) {
			return null;
		}
		Map<String, Slot> slot = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : objek(o, "slot").entrySet()) {
			if (e.getValue().isJsonObject()) {
				slot.put(e.getKey(), Slot.parse(e.getKey(), e.getValue().getAsJsonObject()));
			}
		}
		List<Bingkai> bingkai = new ArrayList<>();
		for (JsonObject b : objekList(o, "bingkai")) {
			String kunci = teks(b, "kunci", "");
			if (!kunci.isEmpty()) {
				bingkai.add(new Bingkai(CakapData.KUNCI + kunci, teksList(b, "slot"), Math.max(1, angka(b, "bobot", 1)),
						Set.copyOf(new LinkedHashSet<>(teksList(b, "register"))), Set.copyOf(new LinkedHashSet<>(teksList(b, "dialek"))),
						teksList(b, "varian")));
			}
		}
		if (bingkai.isEmpty()) {
			return null;
		}
		return new Aturan(id, kelompok, teksAtauNull(o, "niat"), teksAtauNull(o, "topik"), objek(o, "syarat"),
				List.copyOf(bingkai), Collections.unmodifiableMap(slot), teksList(o, "lanjut"), teksAtauNull(o, "gestur"),
				teksAtauNull(o, "seru"));
	}

	/** A double field (exact JSON value, no float rounding: the python mirror reads the same number). */
	static double dbl(@Nullable JsonObject o, String key, double cadangan) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : cadangan;
	}

	// ------------------------------------------------------------------ JSON helpers of the 42K-T classes (no dependency on
	// the engine loader, so the golden runner works on files alone)

	static JsonObject objek(@Nullable JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
	}

	static List<JsonObject> objekList(@Nullable JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		List<JsonObject> out = new ArrayList<>();
		if (e != null && e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) {
				if (x.isJsonObject()) {
					out.add(x.getAsJsonObject());
				}
			}
		}
		return out;
	}

	static List<String> teksList(@Nullable JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		List<String> out = new ArrayList<>();
		if (e != null && e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) {
				if (x.isJsonPrimitive()) {
					out.add(x.getAsString());
				}
			}
		} else if (e != null && e.isJsonPrimitive()) {
			out.add(e.getAsString());
		}
		return List.copyOf(out);
	}

	static String teks(@Nullable JsonObject o, String key, String cadangan) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : cadangan;
	}

	static @Nullable String teksAtauNull(@Nullable JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	static int angka(@Nullable JsonObject o, String key, int cadangan) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : cadangan;
	}

	static boolean benar(@Nullable JsonObject o, String key, boolean cadangan) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : cadangan;
	}

	private TataBahasa() {
	}
}
