package dev.kultivator.dunia.tempat.cerita;

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
 * Loads the story data of DESIGN 38.S.1 on every data (re)load: dialogs {@code data/<ns>/tempat/dialog/*.json}, quests
 * {@code data/<ns>/tempat/quest/*.json} and rumour templates {@code data/<ns>/tempat/kabar/*.json} (one file holds a list
 * under {@code templat}). Registered once through Fabric's {@code ResourceLoader} (resource-loader v1, verified for
 * 0.162.0+26.3: {@code ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Identifier, PreparableReloadListener)});
 * parsing runs on the preparation executor, the swap on the server thread. A broken file is logged and skipped, never
 * fatal. Ids are the file path below the folder without {@code .json} ({@code dialog/tema_pelabuhan/syahbandar.json} ->
 * {@code tema_pelabuhan/syahbandar}); themed modules (38TK) add files with their own prefixes.
 */
public final class CeritaReload extends SimplePreparableReloadListener<CeritaReload.Muatan> {

	public static final Identifier ID = Kultivator.id("tempat_cerita");

	private static final FileToIdConverter DIALOG = FileToIdConverter.json("tempat/dialog");
	private static final FileToIdConverter QUEST = FileToIdConverter.json("tempat/quest");
	private static final FileToIdConverter KABAR = FileToIdConverter.json("tempat/kabar");

	/** What one load produced. */
	public record Muatan(Map<String, Dialog> dialog, Map<String, Quest> quest, Map<String, KabarTempat.Templat> kabar) {
	}

	private static volatile Muatan muatan = new Muatan(Map.of(), Map.of(), Map.of());
	private static boolean registered;

	/** Registers the listener once (from {@code Cerita.init}). */
	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(ID, new CeritaReload());
	}

	public static @Nullable Dialog dialog(String id) {
		return muatan.dialog.get(id);
	}

	/** Every dialog in load order (file path order). */
	public static List<Dialog> dialogs() {
		return List.copyOf(muatan.dialog.values());
	}

	public static @Nullable Quest quest(String id) {
		return muatan.quest.get(id);
	}

	public static List<Quest> quests() {
		return List.copyOf(muatan.quest.values());
	}

	public static Map<String, KabarTempat.Templat> kabar() {
		return muatan.kabar;
	}

	@Override
	protected Muatan prepare(ResourceManager manager, ProfilerFiller profiler) {
		Map<String, Dialog> dialogs = new LinkedHashMap<>();
		for (Map.Entry<Identifier, JsonObject> e : read(manager, DIALOG).entrySet()) {
			Dialog d = Dialog.parse(e.getKey().getPath(), e.getValue());
			if (d == null) {
				Kultivator.LOGGER.warn("Story dialog {} has no nodes; skipped", e.getKey());
			} else {
				dialogs.put(d.id(), d);
			}
		}
		Map<String, Quest> quests = new LinkedHashMap<>();
		for (Map.Entry<Identifier, JsonObject> e : read(manager, QUEST).entrySet()) {
			Quest q = Quest.parse(e.getKey().getPath(), e.getValue());
			if (q == null) {
				Kultivator.LOGGER.warn("Story quest {} has no stages; skipped", e.getKey());
			} else {
				quests.put(q.id(), q);
			}
		}
		Map<String, KabarTempat.Templat> rumours = new LinkedHashMap<>();
		for (Map.Entry<Identifier, JsonObject> e : read(manager, KABAR).entrySet()) {
			for (KabarTempat.Templat t : KabarTempat.Templat.parseFile(e.getValue())) {
				rumours.put(t.id(), t);
			}
		}
		return new Muatan(Collections.unmodifiableMap(dialogs), Collections.unmodifiableMap(quests), Collections.unmodifiableMap(rumours));
	}

	@Override
	protected void apply(Muatan loaded, ResourceManager manager, ProfilerFiller profiler) {
		muatan = loaded;
		Kultivator.LOGGER.info("Stories: {} dialogs, {} quests, {} rumour templates", loaded.dialog.size(), loaded.quest.size(), loaded.kabar.size());
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
				} else {
					Kultivator.LOGGER.warn("Story file {} is not a JSON object; skipped", entry.getKey());
				}
			} catch (Exception exception) {
				Kultivator.LOGGER.warn("Story file {} could not be read", entry.getKey(), exception);
			}
		}
		return out;
	}
}
