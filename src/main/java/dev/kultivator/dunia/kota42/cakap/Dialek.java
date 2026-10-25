package dev.kultivator.dunia.kota42.cakap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The seven town dialects (DESIGN 42.5A.8; data {@code percakapan/dialek.json}, words in lang fragment
 * {@code kota_hidup_cakap_lex}): {@code ibukota} (refined capital speech), {@code pelabuhan} (sailors: "Bung"),
 * {@code oasis} ("Saudara", oaths by the spring), {@code air} (soft canal speech: "Kakak", trailing "ya..."),
 * {@code gunung} (monastery: "Dermawan"), {@code iblis} (rough, politely threatening) and {@code kampung} (blunt village:
 * "Nak"). Each dialect replaces 60 frequent lexicon words; a word reaches the text as the key
 * {@code kultivator.cakap.lex.<word>[.<dialect>][.p]} chosen by {@link #kunci}. Pure data lookup, safe on both sides.
 */
public final class Dialek {

	/**
	 * One dialect.
	 *
	 * @param id              dialect id
	 * @param sebutan         default address word of the dialect (lexicon word)
	 * @param registerCondong register the dialect leans to ({@code sopan biasa kasar kuno})
	 * @param kata            words this dialect replaces ({@code lex.<w>.<id>} exists)
	 * @param kelamin         words with a female dialect form ({@code lex.<w>.<id>.p} exists)
	 * @param tanpaNama       address words whose dialect form cannot precede a name ("Nona yang mulia"): the base form is
	 *                        used before a name
	 */
	public record Data(String id, String sebutan, String registerCondong, Set<String> kata, Set<String> kelamin, Set<String> tanpaNama) {
	}

	/**
	 * The whole file.
	 *
	 * @param bawaan     dialect of places not listed
	 * @param tempat     place kind fragment -> dialect (first contained match wins, file order)
	 * @param berkelamin base words with a female form {@code lex.<w>.p}
	 * @param seruLabel  exclamation word of a {@code seru} slot by emotion label
	 * @param seruNiat   exclamation word by intent (checked first)
	 * @param dialek     dialects by id, file order
	 */
	public record Tabel(String bawaan, Map<String, String> tempat, Set<String> berkelamin, Map<String, String> seruLabel,
			Map<String, String> seruNiat, Map<String, Data> dialek) {
		public static final Tabel KOSONG = new Tabel("kampung", Map.of(), Set.of(), Map.of(), Map.of(), Map.of());

		public static Tabel parse(@Nullable JsonObject json) {
			if (json == null || json.isEmpty()) {
				return KOSONG;
			}
			Map<String, Data> dialek = new LinkedHashMap<>();
			for (JsonObject o : TataBahasa.objekList(json, "dialek")) {
				String id = TataBahasa.teks(o, "id", "");
				if (!id.isEmpty()) {
					dialek.put(id, new Data(id, TataBahasa.teks(o, "sebutan", "kawan"), TataBahasa.teks(o, "register_condong", "biasa"),
							new LinkedHashSet<>(TataBahasa.teksList(o, "kata")), new LinkedHashSet<>(TataBahasa.teksList(o, "kelamin")),
							new LinkedHashSet<>(TataBahasa.teksList(o, "tanpa_nama"))));
				}
			}
			return new Tabel(TataBahasa.teks(json, "bawaan", "kampung"), peta(TataBahasa.objek(json, "tempat")),
					new LinkedHashSet<>(TataBahasa.teksList(json, "berkelamin")), peta(TataBahasa.objek(json, "seru_label")),
					peta(TataBahasa.objek(json, "seru_niat")), dialek);
		}

		private static Map<String, String> peta(JsonObject o) {
			Map<String, String> out = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> e : o.entrySet()) {
				if (!e.getKey().startsWith("_") && e.getValue().isJsonPrimitive()) {
					out.put(e.getKey(), e.getValue().getAsString());
				}
			}
			return out;
		}
	}

	private static volatile Tabel tabel = Tabel.KOSONG;

	/** Swaps in a freshly parsed table ({@link TataBahasa} reload listener). */
	static void pasang(Tabel baru) {
		tabel = baru == null ? Tabel.KOSONG : baru;
	}

	public static Tabel tabel() {
		TataBahasa.pastikan();
		return tabel;
	}

	/**
	 * The lang key of a lexicon word in a dialect: {@code lex.<w>.<d>.p} (female form of the dialect) ->
	 * {@code lex.<w>.<d>} -> {@code lex.<w>.p} (female base form) -> {@code lex.<w>}.
	 *
	 * @param kelamin gender the word agrees with ({@code l}/{@code p}), "" when none
	 */
	public static String kunci(Tabel t, String kata, String dialek, String kelamin) {
		Data d = t.dialek.get(dialek);
		boolean p = "p".equals(kelamin);
		if (d != null && d.kata.contains(kata)) {
			return CakapData.KUNCI_LEX + kata + "." + dialek + (p && d.kelamin.contains(kata) ? ".p" : "");
		}
		return CakapData.KUNCI_LEX + kata + (p && t.berkelamin.contains(kata) ? ".p" : "");
	}

	/** {@link #kunci} for a word that precedes a name: the base form when the dialect form cannot take one. */
	public static String kunciBernama(Tabel t, String kata, String dialek, String kelamin) {
		Data d = t.dialek.get(dialek);
		if (d != null && d.tanpaNama.contains(kata)) {
			return CakapData.KUNCI_LEX + kata + ("p".equals(kelamin) && t.berkelamin.contains(kata) ? ".p" : "");
		}
		return kunci(t, kata, dialek, kelamin);
	}

	/** The dialect of a place kind ({@code kota_besar} -> {@code ibukota}), else {@link Tabel#bawaan}. */
	public static String dariTempat(Tabel t, @Nullable String tempat) {
		if (tempat != null && !tempat.isEmpty()) {
			for (Map.Entry<String, String> e : t.tempat.entrySet()) {
				if (tempat.contains(e.getKey())) {
					return e.getValue();
				}
			}
		}
		return t.bawaan;
	}

	/** The exclamation word for an intent, else for an emotion label, else {@code oh}. */
	public static String seru(Tabel t, @Nullable String niat, @Nullable String label) {
		String w = niat == null ? null : t.seruNiat.get(niat);
		if (w == null && label != null) {
			w = t.seruLabel.get(label);
		}
		return w == null ? "oh" : w;
	}

	private Dialek() {
	}
}
