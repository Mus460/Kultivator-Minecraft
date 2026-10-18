package dev.kultivator.client.codex;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kultivator.Kultivator;
import dev.kultivator.data.TechniqueSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.resources.Resource;

import java.io.Reader;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The codex search (DESIGN 22.12.3): each spec's text is built once on first use from both merged language files of
 * the mod (so the Indonesian and the English name, the source work and named technique, the Dao, type and grade names
 * all match whatever language the game runs in) plus the id, folded to lower case without diacritics. A query is split
 * into tokens that must all match a word prefix. Rebuilt when the game language changes. Also gives the English name
 * for tooltips and the detail slip.
 */
public final class SearchIndex {

	private static final Pattern MARKS = Pattern.compile("\\p{M}+");
	private static final Pattern SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");

	private static final Map<String, String> TEXT = new HashMap<>();
	private static final Map<String, String> ENGLISH = new HashMap<>();
	private static String builtFor = "";
	private static JsonObject en = new JsonObject();
	private static JsonObject id = new JsonObject();

	/** True when every token of {@code query} starts a word of the spec's text. */
	public static boolean matches(TechniqueSpec spec, String[] tokens) {
		if (tokens.length == 0) {
			return true;
		}
		String text = textOf(spec);
		for (String token : tokens) {
			if (!text.contains(" " + token)) {
				return false;
			}
		}
		return true;
	}

	/** The folded tokens of a query (empty for a blank one). */
	public static String[] tokens(String query) {
		String folded = fold(query).trim();
		if (folded.isEmpty()) {
			return new String[0];
		}
		return folded.split("\\s+");
	}

	/** The English name of a spec (from the merged en_us file), or its id. */
	public static String english(TechniqueSpec spec) {
		ensure();
		return ENGLISH.computeIfAbsent(spec.id(), key -> string(en, spec.nameKey(), spec.id()));
	}

	/** The name in the other language than the one the game shows ("Indonesian / English" in the tooltip). */
	public static String otherName(TechniqueSpec spec) {
		ensure();
		String selected = Minecraft.getInstance().getLanguageManager().getSelected();
		JsonObject other = selected != null && selected.startsWith("id") ? en : id;
		return string(other, spec.nameKey(), spec.id());
	}

	private static String textOf(TechniqueSpec spec) {
		ensure();
		return TEXT.computeIfAbsent(spec.id(), key -> build(spec));
	}

	private static String build(TechniqueSpec spec) {
		StringBuilder builder = new StringBuilder(" ");
		builder.append(spec.id().replace('_', ' ')).append(' ').append(spec.id()).append(' ');
		for (JsonObject lang : List.of(en, id)) {
			builder.append(string(lang, spec.nameKey(), "")).append(' ');
			builder.append(string(lang, spec.sourceKey(), "")).append(' ');
			builder.append(string(lang, "kultivator.teknik.type." + spec.type().id(), "")).append(' ');
			builder.append(string(lang, "kultivator.teknik.grade." + spec.grade().id(), "")).append(' ');
			if (spec.dao() != null) {
				builder.append(string(lang, "kultivator.dao." + spec.dao().id(), "")).append(' ');
			} else {
				builder.append(string(lang, "kultivator.codex.universal", "universal")).append(' ');
			}
		}
		Language language = Language.getInstance();
		builder.append(language.getOrDefault(spec.nameKey())).append(' ');
		builder.append(language.getOrDefault(spec.sourceKey())).append(' ');
		if (spec.dao() != null) {
			builder.append(spec.dao().displayName().getString()).append(' ');
		}
		builder.append(spec.type().displayName().getString()).append(' ');
		String folded = fold(builder.toString());
		return " " + String.join(" ", SPLIT.split(folded)) + " ";
	}

	/** Lower case without diacritics ("é" → "e"). */
	static String fold(String text) {
		String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
		return MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
	}

	private static void ensure() {
		String selected = String.valueOf(Minecraft.getInstance().getLanguageManager().getSelected());
		if (selected.equals(builtFor)) {
			return;
		}
		builtFor = selected;
		TEXT.clear();
		ENGLISH.clear();
		en = read("en_us");
		id = read("id_id");
	}

	private static JsonObject read(String code) {
		try {
			Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(Kultivator.id("lang/" + code + ".json"));
			if (resource.isEmpty()) {
				return new JsonObject();
			}
			try (Reader reader = resource.get().openAsReader()) {
				JsonElement json = JsonParser.parseReader(reader);
				return json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
			}
		} catch (Exception exception) {
			Kultivator.LOGGER.warn("Kultivator: could not read lang/{}.json for the codex search", code, exception);
			return new JsonObject();
		}
	}

	private static String string(JsonObject json, String key, String fallback) {
		try {
			JsonElement element = json.get(key);
			return element == null ? fallback : element.getAsString();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	/** Drops the cached texts (resource reload). */
	public static void invalidate() {
		builtFor = "";
	}

	private SearchIndex() {
	}
}
