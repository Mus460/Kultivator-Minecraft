package dev.kultivator.dunia.tempat.hidup;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kultivator.Kultivator;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Optional schedule data per kind of place (DESIGN 38.T.14.3 row NL): {@code data/kultivator/tempat/jadwal/*.json}.
 * The kind a file is for is its {@code "jenis"} field ({@code "kota_tema/oasis"}); without one, the file name with '_'
 * for '/' ({@code kota_tema_pelabuhan.json} is {@code kota_tema/pelabuhan}), and a themed town's file may carry a suffix
 * ({@code kota_tema_oasis_kafilah.json} is still {@code kota_tema/oasis}). Several files of one kind are merged in file
 * name order: a later file fills or overrides single hours of a group or role, single people of an event, and the
 * largest {@code geser_jam} wins. Reloaded with the data packs; a broken entry is skipped with a warning, the built-in
 * tables of {@link Jadwal} and {@link JadwalTema} answer everything the files leave out.
 *
 * <pre>{@code
 * {
 *   "jenis": "kota_tema/iblis",                        // optional: the site kind (else from the file name)
 *   "geser_jam": 12,                                   // whole place lives shifted (night city); 0-12
 *   "kelompok": { "pekerja": { "5-8": ENTRY, "12": ENTRY } },   // 38.N.3 group ids (Kelompok.id())
 *   "peran":    { "penjual_ikan": { "5-9": ENTRY } },           // role ids (NpcRole.id()), wins over kelompok
 *   "acara":    { "festival_air": { "*": ENTRY, "anak": ENTRY, "pendayung": ENTRY } }  // HidupApi.timpaJadwal names
 * }
 * ENTRY = { "kegiatan": "kerja", "tujuan": "kerja:jala" | "@kerja" | "@kasur" | "@rumah" | "@tetap" | <titik type>,
 *           "klip": "tarik_jala", "varian": 0, "kanan": "jala", "kiri": "", "punggung": "", "jalan": "jalan_bawa",
 *           "dalam": false }
 * }</pre>
 * Hours are 0-23 (06:00 = tick 0), a range {@code "a-b"} includes both ends and may wrap ({@code "22-4"}). Clip and
 * prop names are those of {@link GerakId} / {@link PropId} ({@code GerakId.id(nama)}); an unknown name is an error.
 */
public final class JadwalData extends SimplePreparableReloadListener<Map<String, JadwalData.Berkas>> {

	public static final Identifier ID = Kultivator.id("tempat_jadwal");
	private static final FileToIdConverter FOLDER = FileToIdConverter.json("tempat/jadwal");

	/** One file: hour tables by group and role, event tables by event, and the whole-place hour shift. */
	public record Berkas(int geserJam, Map<String, Kegiatan[]> kelompok, Map<String, Kegiatan[]> peran,
			Map<String, Map<String, Kegiatan>> acara) {
	}

	private static volatile Map<String, Berkas> data = Map.of();
	private static boolean registered;

	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(ID, new JadwalData());
	}

	/** The (merged) files of site kind {@code jenisSitus} ({@code kota_tema/iblis}), or null. */
	public static @Nullable Berkas untuk(String jenisSitus) {
		return data.get(jenisSitus.replace('/', '_'));
	}

	/** Number of kinds with schedule data (commands). */
	public static int jumlah() {
		return data.size();
	}

	@Override
	protected Map<String, Berkas> prepare(ResourceManager manager, ProfilerFiller profiler) {
		Map<String, Berkas> out = new LinkedHashMap<>();
		// file name order, so the merge of several files of one kind is the same on every machine
		Map<Identifier, Resource> files = new TreeMap<>(FOLDER.listMatchingResources(manager));
		for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
			Identifier id = FOLDER.fileToId(file.getKey());
			if (!Kultivator.MOD_ID.equals(id.getNamespace())) {
				continue;
			}
			try (Reader reader = file.getValue().openAsReader()) {
				JsonElement root = JsonParser.parseReader(reader);
				if (root.isJsonObject()) {
					JsonObject o = root.getAsJsonObject();
					String kunci = kunci(id.getPath(), o);
					Berkas baru = baca(id.getPath(), o);
					Berkas lama = out.get(kunci);
					out.put(kunci, lama == null ? baru : gabung(lama, baru));
				}
			} catch (Exception e) {
				Kultivator.LOGGER.warn("Schedule data {} could not be read", file.getKey(), e);
			}
		}
		return out;
	}

	/** The five themed towns: a file {@code kota_tema_<tema>_<anything>} belongs to {@code kota_tema/<tema>}. */
	private static final String[] TEMA = {"pelabuhan", "oasis", "air", "gunung", "iblis"};

	/** The lookup key ('/' as '_') of the kind a file is for. */
	static String kunci(String nama, JsonObject root) {
		if (root.has("jenis") && root.get("jenis").isJsonPrimitive()) {
			String jenis = root.get("jenis").getAsString().trim();
			if (!jenis.isEmpty()) {
				return jenis.replace('/', '_');
			}
		}
		String base = nama.substring(nama.lastIndexOf('/') + 1);
		for (String tema : TEMA) {
			String awal = "kota_tema_" + tema;
			if (base.equals(awal) || base.startsWith(awal + "_")) {
				return awal;
			}
		}
		return base;
	}

	/** {@code b} over {@code a}: single hours, single people of an event, the larger hour shift. */
	static Berkas gabung(Berkas a, Berkas b) {
		Map<String, Map<String, Kegiatan>> acara = new HashMap<>(a.acara());
		for (Map.Entry<String, Map<String, Kegiatan>> e : b.acara().entrySet()) {
			Map<String, Kegiatan> who = new HashMap<>(acara.getOrDefault(e.getKey(), Map.of()));
			who.putAll(e.getValue());
			acara.put(e.getKey(), Map.copyOf(who));
		}
		return new Berkas(Math.max(a.geserJam(), b.geserJam()), jamGabung(a.kelompok(), b.kelompok()), jamGabung(a.peran(), b.peran()),
				Map.copyOf(acara));
	}

	private static Map<String, Kegiatan[]> jamGabung(Map<String, Kegiatan[]> a, Map<String, Kegiatan[]> b) {
		Map<String, Kegiatan[]> out = new HashMap<>(a);
		for (Map.Entry<String, Kegiatan[]> e : b.entrySet()) {
			Kegiatan[] lama = out.get(e.getKey());
			Kegiatan[] hours = lama != null ? lama.clone() : new Kegiatan[24];
			for (int h = 0; h < 24; h++) {
				if (e.getValue()[h] != null) {
					hours[h] = e.getValue()[h];
				}
			}
			out.put(e.getKey(), hours);
		}
		return Map.copyOf(out);
	}

	@Override
	protected void apply(Map<String, Berkas> prepared, ResourceManager manager, ProfilerFiller profiler) {
		data = Map.copyOf(prepared);
	}

	private static Berkas baca(String nama, JsonObject root) {
		int geser = root.has("geser_jam") ? Math.max(0, Math.min(12, root.get("geser_jam").getAsInt())) : 0;
		Map<String, Kegiatan[]> kelompok = jamTabel(nama, root.getAsJsonObject("kelompok"));
		Map<String, Kegiatan[]> peran = jamTabel(nama, root.getAsJsonObject("peran"));
		Map<String, Map<String, Kegiatan>> acara = new HashMap<>();
		JsonObject events = root.getAsJsonObject("acara");
		if (events != null) {
			for (Map.Entry<String, JsonElement> e : events.entrySet()) {
				if (!e.getValue().isJsonObject()) {
					continue;
				}
				Map<String, Kegiatan> who = new HashMap<>();
				for (Map.Entry<String, JsonElement> w : e.getValue().getAsJsonObject().entrySet()) {
					Kegiatan k = entri(nama, w.getValue());
					if (k != null) {
						who.put(w.getKey(), k);
					}
				}
				acara.put(e.getKey(), Map.copyOf(who));
			}
		}
		return new Berkas(geser, Map.copyOf(kelompok), Map.copyOf(peran), Map.copyOf(acara));
	}

	/** {@code {"<who>": {"<hour or range>": ENTRY}}} into 24-slot arrays (null = not given). */
	private static Map<String, Kegiatan[]> jamTabel(String nama, @Nullable JsonObject table) {
		Map<String, Kegiatan[]> out = new HashMap<>();
		if (table == null) {
			return out;
		}
		for (Map.Entry<String, JsonElement> who : table.entrySet()) {
			if (!who.getValue().isJsonObject()) {
				continue;
			}
			Kegiatan[] hours = new Kegiatan[24];
			for (Map.Entry<String, JsonElement> slot : who.getValue().getAsJsonObject().entrySet()) {
				Kegiatan k = entri(nama, slot.getValue());
				int[] range = jam(slot.getKey());
				if (k == null || range == null) {
					Kultivator.LOGGER.warn("Schedule data {}: bad entry {}/{}", nama, who.getKey(), slot.getKey());
					continue;
				}
				for (int h = range[0]; ; h = (h + 1) % 24) {
					hours[h] = k;
					if (h == range[1]) {
						break;
					}
				}
			}
			out.put(who.getKey(), hours);
		}
		return out;
	}

	private static int @Nullable [] jam(String key) {
		try {
			String[] parts = key.trim().split("-");
			int a = Integer.parseInt(parts[0].trim());
			int b = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : a;
			if (a < 0 || a > 23 || b < 0 || b > 23) {
				return null;
			}
			return new int[]{a, b};
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static @Nullable Kegiatan entri(String nama, JsonElement element) {
		if (!element.isJsonObject()) {
			return null;
		}
		JsonObject o = element.getAsJsonObject();
		String kunci = teks(o, "kegiatan", "kerja");
		String tujuan = teks(o, "tujuan", Kegiatan.TETAP);
		int klip = klip(nama, teks(o, "klip", ""));
		int jalan = klip(nama, teks(o, "jalan", ""));
		int varian = o.has("varian") ? Math.max(0, Math.min(GerakId.VARIAN_MAKS, o.get("varian").getAsInt())) : 0;
		if (klip < 0 || jalan < 0) {
			return null;
		}
		int kanan = prop(nama, teks(o, "kanan", ""));
		int kiri = prop(nama, teks(o, "kiri", ""));
		int punggung = prop(nama, teks(o, "punggung", ""));
		if (kanan < 0 || kiri < 0 || punggung < 0) {
			return null;
		}
		boolean dalam = o.has("dalam") && o.get("dalam").getAsBoolean();
		return new Kegiatan(kunci, tujuan, klip, varian, kanan, kiri, punggung, jalan, dalam);
	}

	private static String teks(JsonObject o, String key, String fallback) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
	}

	/** Clip id by name ("" = 0); -1 when the name is unknown. */
	private static int klip(String nama, String name) {
		if (name.isEmpty()) {
			return 0;
		}
		int id = GerakId.id(name);
		if (id <= 0) {
			Kultivator.LOGGER.warn("Schedule data {}: unknown clip {}", nama, name);
			return -1;
		}
		return id;
	}

	/** Prop id by name ("" = 0); -1 when the name is unknown. */
	private static int prop(String nama, String name) {
		if (name.isEmpty()) {
			return 0;
		}
		int id = PropId.id(name);
		if (id <= 0) {
			Kultivator.LOGGER.warn("Schedule data {}: unknown prop {}", nama, name);
			return -1;
		}
		return id;
	}
}
