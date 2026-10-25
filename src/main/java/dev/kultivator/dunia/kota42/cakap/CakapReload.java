package dev.kultivator.dunia.kota42.cakap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Loads the data of the conversation engine (DESIGN 42.5A.0 butir 4; formats in {@link CakapData}) on every data
 * (re)load: every JSON object below {@code data/<ns>/tempat/percakapan/**} is read on the preparation executor (file path
 * order, a later namespace replaces the same path), the six engine files are parsed ({@code kepribadian emosi predikat topik
 * niat nada} plus the NPC-NPC chat scripts {@code obrolan/*}), and the result is swapped in on the server thread.
 * <p>
 * Grammar, dialect, tic and golden files (42K-T) are kept raw ({@link #mentah}); a module that parses them registers a
 * {@link #saatMuat} listener, called after every swap with all raw files by relative path ({@code tata/kabar},
 * {@code golden/contoh_bibi_wang}). A broken file is logged and skipped.
 */
public final class CakapReload extends SimplePreparableReloadListener<CakapReload.Muatan> {

	public static final Identifier ID = Kultivator.id("tempat_percakapan");

	private static final FileToIdConverter SEMUA = FileToIdConverter.json(CakapData.FOLDER);

	/** Everything one load produced. */
	public record Muatan(Map<String, JsonObject> mentah, Kepribadian.Tabel kepribadian, Emosi.Tabel emosi, Map<String, Predikat> predikat,
			Topik.Tabel topik, Map<String, Niat.Info> niat, PenilaiNada.Tabel nada, Map<String, ObrolanWarga.Naskah> obrolan) {
		static final Muatan KOSONG = new Muatan(Map.of(), Kepribadian.Tabel.KOSONG, Emosi.Tabel.KOSONG, Map.of(), Topik.Tabel.KOSONG, Map.of(),
				PenilaiNada.Tabel.KOSONG, Map.of());
	}

	private static volatile Muatan muatan = Muatan.KOSONG;
	private static final List<Consumer<Map<String, JsonObject>>> PENDENGAR = new CopyOnWriteArrayList<>();
	private static boolean registered;

	/** Registers the listener once (from {@code Cakap.init}). */
	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(ID, new CakapReload());
	}

	/** Called after every load with all raw files by relative path (42K-T grammar, dialects, tics, golden files). */
	public static void saatMuat(Consumer<Map<String, JsonObject>> pendengar) {
		if (pendengar != null) {
			PENDENGAR.add(pendengar);
			if (!muatan.mentah.isEmpty()) {
				pendengar.accept(muatan.mentah);
			}
		}
	}

	// ------------------------------------------------------------------ readers

	public static Muatan muatan() {
		return muatan;
	}

	/** A raw file by relative path without {@code .json} ({@code dialek}, {@code tata/kabar}); empty object when absent. */
	public static JsonObject mentah(String path) {
		JsonObject o = muatan.mentah.get(path);
		return o == null ? new JsonObject() : o;
	}

	/** Every raw file whose relative path starts with {@code awalan} ({@code "tata/"}), in path order. */
	public static Map<String, JsonObject> mentahAwalan(String awalan) {
		Map<String, JsonObject> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonObject> e : muatan.mentah.entrySet()) {
			if (e.getKey().startsWith(awalan)) {
				out.put(e.getKey(), e.getValue());
			}
		}
		return out;
	}

	public static Kepribadian.Tabel kepribadian() {
		return muatan.kepribadian;
	}

	public static Emosi.Tabel emosi() {
		return muatan.emosi;
	}

	public static Map<String, Predikat> predikat() {
		return muatan.predikat;
	}

	public static Topik.Tabel topik() {
		return muatan.topik;
	}

	public static Map<String, Niat.Info> niat() {
		return muatan.niat;
	}

	public static PenilaiNada.Tabel nada() {
		return muatan.nada;
	}

	public static Map<String, ObrolanWarga.Naskah> obrolan() {
		return muatan.obrolan;
	}

	// ------------------------------------------------------------------ reload

	@Override
	protected Muatan prepare(ResourceManager manager, ProfilerFiller profiler) {
		Map<String, JsonObject> raw = new LinkedHashMap<>();
		List<Map.Entry<Identifier, Resource>> entries = new ArrayList<>(SEMUA.listMatchingResources(manager).entrySet());
		entries.sort(Map.Entry.comparingByKey());
		for (Map.Entry<Identifier, Resource> entry : entries) {
			String path = SEMUA.fileToId(entry.getKey()).getPath();
			try (Reader reader = entry.getValue().openAsReader()) {
				JsonElement json = JsonParser.parseReader(reader);
				if (json.isJsonObject()) {
					raw.put(path, json.getAsJsonObject());
				} else {
					Kultivator.LOGGER.warn("Conversation file {} is not a JSON object; skipped", entry.getKey());
				}
			} catch (Exception exception) {
				Kultivator.LOGGER.warn("Conversation file {} could not be read", entry.getKey(), exception);
			}
		}
		Map<String, ObrolanWarga.Naskah> chats = new LinkedHashMap<>();
		for (Map.Entry<String, JsonObject> e : raw.entrySet()) {
			if (e.getKey().startsWith("obrolan/")) {
				ObrolanWarga.Naskah n = aman(e.getKey(), () -> ObrolanWarga.Naskah.parse(e.getValue()), null);
				if (n != null) {
					chats.put(n.jenis(), n);
				}
			}
		}
		return new Muatan(Collections.unmodifiableMap(raw),
				aman(CakapData.KEPRIBADIAN, () -> Kepribadian.Tabel.parse(raw.getOrDefault(CakapData.KEPRIBADIAN, new JsonObject())),
						Kepribadian.Tabel.KOSONG),
				aman(CakapData.EMOSI, () -> Emosi.Tabel.parse(raw.getOrDefault(CakapData.EMOSI, new JsonObject())), Emosi.Tabel.KOSONG),
				aman(CakapData.PREDIKAT, () -> Map.copyOf(Predikat.parse(raw.getOrDefault(CakapData.PREDIKAT, new JsonObject()))), Map.of()),
				aman(CakapData.TOPIK, () -> Topik.Tabel.parse(raw.getOrDefault(CakapData.TOPIK, new JsonObject())), Topik.Tabel.KOSONG),
				aman(CakapData.NIAT, () -> Collections.unmodifiableMap(Niat.Info.parse(raw.getOrDefault(CakapData.NIAT, new JsonObject()))),
						Map.of()),
				aman(CakapData.NADA, () -> PenilaiNada.Tabel.parse(raw.getOrDefault(CakapData.NADA, new JsonObject())), PenilaiNada.Tabel.KOSONG),
				Collections.unmodifiableMap(chats));
	}

	@Override
	protected void apply(Muatan loaded, ResourceManager manager, ProfilerFiller profiler) {
		muatan = loaded;
		Kultivator.LOGGER.info("Conversation engine: {} files, {} predicates, {} topics, {} intents, {} chat scripts", loaded.mentah.size(),
				loaded.predikat.size(), loaded.topik.topik().size(), loaded.niat.size(), loaded.obrolan.size());
		for (Consumer<Map<String, JsonObject>> each : PENDENGAR) {
			try {
				each.accept(loaded.mentah);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Conversation data listener failed", exception);
			}
		}
	}

	private interface Pengurai<T> {
		T urai();
	}

	private static <T> T aman(String file, Pengurai<T> parse, T cadangan) {
		try {
			return parse.urai();
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Conversation file {} is malformed; using defaults", file, exception);
			return cadangan;
		}
	}

	// ------------------------------------------------------------------ JSON helpers (shared by the 42K parsers)

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

	static float pecahan(@Nullable JsonObject o, String key, float cadangan) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsFloat() : cadangan;
	}

	static boolean benar(@Nullable JsonObject o, String key, boolean cadangan) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : cadangan;
	}
}
