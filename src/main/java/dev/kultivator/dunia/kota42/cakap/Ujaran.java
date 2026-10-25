package dev.kultivator.dunia.kota42.cakap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One utterance (DESIGN 42.5A.0 butir 2, 42.5A.8): the meaning chosen by the server as a tree of lang keys with typed
 * arguments, realised as nested {@link Component#translatable} so the client shows it in its own language. No free text:
 * every node is a lang key of a {@code kota_hidup_cakap*} fragment, except literal leaves for names and numbers > 20.
 * <p>
 * Frozen shape (C42.K1, 42-G0; body by 42K-T: golden form, plain-text rendering, language tables; built by
 * {@link Realisasi}). Golden form (42.5A.10/12) of a node:
 * {@code {"k": "<full lang key>", "a": [<node>...]}} or a literal leaf {@code {"t": "<text>"}}.
 *
 * @param akar   root node (a frame key of a grammar rule)
 * @param niat   speech act it realises
 * @param gestur clip id of {@code GerakId}/{@code KotaGerakId} played with it, 0 for none
 * @param seru   short voice key ({@code kota42.seru.*}, 42.5A.8 table) played at the start of the bubble, null for none
 * @param wajah  face code (42.4.11 {@code KodeWajah} index) shown while speaking, -1 to keep the current face
 */
public record Ujaran(Simpul akar, Niat niat, int gestur, @Nullable String seru, int wajah) {

	/** Nothing to say (engine off, stub, or an effect-only turn). */
	public static final Ujaran KOSONG = new Ujaran(Simpul.KOSONG, Niat.SAMBUNG_RASA, 0, null, -1);

	public boolean kosong() {
		return akar.kunci().isEmpty() && akar.teks() == null;
	}

	/** The realised component (recursive, depth <= 4 by construction of the grammar). */
	public Component komponen() {
		return akar.komponen();
	}

	/** Plain text in one language ({@link #bahasa}), for logs, tests and server-side previews. */
	public String teks(Map<String, String> lang) {
		return Realisasi.rangkai(akar, lang);
	}

	/**
	 * A language table read from the mod jar ({@code assets/kultivator/lang/<locale>.json}), empty when absent. Works on
	 * both sides (the jar holds the assets on a dedicated server too); used by the golden test, never for shown text.
	 */
	public static Map<String, String> bahasa(String locale) {
		Map<String, String> out = new LinkedHashMap<>();
		try (InputStream in = Ujaran.class.getResourceAsStream("/assets/kultivator/lang/" + locale + ".json")) {
			if (in != null) {
				try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
					for (Map.Entry<String, JsonElement> e : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
						if (e.getValue().isJsonPrimitive()) {
							out.put(e.getKey(), e.getValue().getAsString());
						}
					}
				}
			}
		} catch (Exception exception) {
			dev.kultivator.Kultivator.LOGGER.warn("Language table {} could not be read", locale, exception);
		}
		return out;
	}

	/**
	 * A node: a lang key with argument nodes, or a literal leaf.
	 *
	 * @param kunci full lang key ({@code kultivator.cakap.harga_naik.0}); "" for a literal leaf
	 * @param arg   positional arguments ({@code %1$s}, {@code %2$s} ...), each realised recursively
	 * @param teks  literal leaf (player/warga name, number > 20), null for key nodes
	 */
	public record Simpul(String kunci, List<Simpul> arg, @Nullable Component teks) {
		public static final Simpul KOSONG = new Simpul("", List.of(), null);

		public Simpul {
			kunci = kunci == null ? "" : kunci;
			arg = List.copyOf(arg);
		}

		public static Simpul kunci(String kunci, Simpul... arg) {
			return new Simpul(kunci, List.of(arg), null);
		}

		public static Simpul teks(Component teks) {
			return new Simpul("", List.of(), teks);
		}

		/** Golden form: {@code {"k": key, "a": [...]}} or a literal leaf {@code {"t": text}} (empty node: {@code {"t": ""}}). */
		public JsonObject golden() {
			JsonObject o = new JsonObject();
			if (teks != null || kunci.isEmpty()) {
				o.addProperty("t", teks == null ? "" : teks.getString());
				return o;
			}
			o.addProperty("k", kunci);
			JsonArray a = new JsonArray();
			for (Simpul s : arg) {
				a.add(s.golden());
			}
			o.add("a", a);
			return o;
		}

		/** Reads the golden form back (literal leaves become {@link Component#literal}). */
		public static Simpul dariGolden(@Nullable JsonElement e) {
			if (e == null || !e.isJsonObject()) {
				return KOSONG;
			}
			JsonObject o = e.getAsJsonObject();
			if (o.has("t")) {
				String t = o.get("t").getAsString();
				return t.isEmpty() ? KOSONG : teks(Component.literal(t));
			}
			List<Simpul> args = new ArrayList<>();
			if (o.has("a")) {
				for (JsonElement x : o.getAsJsonArray("a")) {
					args.add(dariGolden(x));
				}
			}
			return new Simpul(o.has("k") ? o.get("k").getAsString() : "", args, null);
		}

		public Component komponen() {
			if (teks != null) {
				return teks;
			}
			if (kunci.isEmpty()) {
				return Component.empty();
			}
			Object[] args = new Object[arg.size()];
			for (int i = 0; i < args.length; i++) {
				args[i] = arg.get(i).komponen();
			}
			return Component.translatable(kunci, args);
		}
	}
}
