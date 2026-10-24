package dev.kultivator.dunia.tempat.ekonomi;

import com.google.gson.JsonArray;
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

/**
 * Data of the money loop (DESIGN 38.M.2.3, 38.M.4.2, 38.T.10.1, 38.T.14.3 row EK), reloaded with the data packs:
 * <ul>
 * <li>{@code data/kultivator/tempat/permintaan/<jenis>.json}: the sale demand of a kind of place, {@code {"<item id>":
 * factor, "*": default}} where the file name is the site kind with '/' as '_' ({@code kota_tema_pelabuhan} is
 * {@code kota_tema/pelabuhan}; 38TK writes the themed ones, 38EK {@code kota}, {@code kota_besar}, {@code pos_dagang}).
 * Read generically by {@link KafilahSekte}.</li>
 * <li>{@code data/kultivator/tempat/lelang/<tabel>.json}: auction lot tables ({@code ProfilLelang.tabelLot}): {@code {"lot":
 * [{"jenis": ..., "kelas": "rendah|menengah|tinggi", "bobot": n, ...}]}} (see {@link LelangAgung}).</li>
 * </ul>
 */
public final class EkonomiReload extends SimplePreparableReloadListener<EkonomiReload.Muatan> {

	public static final Identifier ID = Kultivator.id("tempat_ekonomi");
	private static final FileToIdConverter PERMINTAAN = FileToIdConverter.json("tempat/permintaan");
	private static final FileToIdConverter LELANG = FileToIdConverter.json("tempat/lelang");

	/** One load: demand per site kind (item id → factor, "*" default), lot tables by id. */
	public record Muatan(Map<String, Map<String, Double>> permintaan, Map<String, List<JsonObject>> lelang) {
	}

	private static volatile Muatan muatan = new Muatan(Map.of(), Map.of());
	private static boolean registered;

	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(ID, new EkonomiReload());
	}

	/** The demand table of site kind {@code jenisSitus} ({@code kota/kota_besar}), or null when none was loaded. */
	public static @Nullable Map<String, Double> permintaan(String jenisSitus) {
		return muatan.permintaan.get(jenisSitus.replace('/', '_'));
	}

	/** Every loaded demand table, keyed by file name. */
	public static Map<String, Map<String, Double>> semuaPermintaan() {
		return muatan.permintaan;
	}

	/** The entries of lot table {@code tabel} (empty when missing). */
	public static List<JsonObject> lelang(String tabel) {
		return muatan.lelang.getOrDefault(tabel, List.of());
	}

	@Override
	protected Muatan prepare(ResourceManager manager, ProfilerFiller profiler) {
		Map<String, Map<String, Double>> demand = new LinkedHashMap<>();
		for (Map.Entry<Identifier, JsonObject> e : read(manager, PERMINTAAN).entrySet()) {
			Map<String, Double> table = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> each : e.getValue().entrySet()) {
				if (each.getValue().isJsonPrimitive() && each.getValue().getAsJsonPrimitive().isNumber()) {
					table.put(each.getKey(), Math.max(0.0D, Math.min(1.35D, each.getValue().getAsDouble())));
				}
			}
			demand.put(e.getKey().getPath(), Collections.unmodifiableMap(table));
		}
		Map<String, List<JsonObject>> lots = new LinkedHashMap<>();
		for (Map.Entry<Identifier, JsonObject> e : read(manager, LELANG).entrySet()) {
			JsonArray array = e.getValue().has("lot") && e.getValue().get("lot").isJsonArray() ? e.getValue().getAsJsonArray("lot") : new JsonArray();
			List<JsonObject> list = new ArrayList<>();
			for (JsonElement each : array) {
				if (each.isJsonObject()) {
					list.add(each.getAsJsonObject());
				}
			}
			lots.put(e.getKey().getPath(), List.copyOf(list));
		}
		return new Muatan(Collections.unmodifiableMap(demand), Collections.unmodifiableMap(lots));
	}

	@Override
	protected void apply(Muatan loaded, ResourceManager manager, ProfilerFiller profiler) {
		muatan = loaded;
		Kultivator.LOGGER.info("Economy: {} demand tables, {} auction lot tables", loaded.permintaan.size(), loaded.lelang.size());
	}

	private static Map<Identifier, JsonObject> read(ResourceManager manager, FileToIdConverter lister) {
		Map<Identifier, JsonObject> out = new LinkedHashMap<>();
		List<Map.Entry<Identifier, Resource>> entries = new ArrayList<>(lister.listMatchingResources(manager).entrySet());
		entries.sort(Map.Entry.comparingByKey());
		for (Map.Entry<Identifier, Resource> entry : entries) {
			Identifier id = lister.fileToId(entry.getKey());
			try (Reader reader = entry.getValue().openAsReader()) {
				JsonElement json = JsonParser.parseReader(reader);
				if (json.isJsonObject()) {
					out.put(id, json.getAsJsonObject());
				}
			} catch (Exception exception) {
				Kultivator.LOGGER.warn("Economy data file {} could not be read", entry.getKey(), exception);
			}
		}
		return out;
	}
}
