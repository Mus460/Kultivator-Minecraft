package dev.kultivator.dunia.kota42.cakap;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fact predicate of {@code percakapan/predikat.json} (DESIGN 42.5A.4; 44 kinds): what kind of thing a {@link Fakta} says,
 * its argument kinds, default emotional weight, extra memory strength, the topic it feeds, whether it is never forgotten
 * when it involves family, and whether it is a secret (told only when {@code relLawan >= 0.7}).
 *
 * @param id       UPPER_SNAKE id
 * @param golongan {@code pemain warga kota dunia opini}
 * @param subjek   kind of the subject ({@link Fakta.Jenis} id) or null
 * @param objek    kind of the object or null
 * @param nilai    meaning of the number: {@code tiada angka persen hari opini}
 * @param valensi  default valence -3..3
 * @param kuat     extra strength multiplier
 * @param topik    topic id it feeds
 * @param keluarga kept while it involves family
 * @param rahasia  a secret
 */
public record Predikat(String id, String golongan, @Nullable String subjek, @Nullable String objek, String nilai, int valensi, float kuat,
		String topik, boolean keluarga, boolean rahasia) {

	/** A predicate the data does not know: neutral, plain, feeds topic {@code kabar}. */
	public static Predikat asing(String id) {
		return new Predikat(id, "kota", null, null, "tiada", 0, 1.0F, "kabar", false, false);
	}

	/** The predicate of an id ({@link #asing} when unknown). */
	public static Predikat dari(String id) {
		Predikat p = CakapReload.predikat().get(id);
		return p != null ? p : asing(id);
	}

	static Map<String, Predikat> parse(JsonObject json) {
		Map<String, Predikat> out = new LinkedHashMap<>();
		for (JsonObject o : CakapReload.objekList(json, "predikat")) {
			String id = CakapReload.teks(o, "id", "");
			if (id.isEmpty()) {
				continue;
			}
			out.put(id, new Predikat(id, CakapReload.teks(o, "golongan", "kota"), CakapReload.teksAtauNull(o, "subjek"),
					CakapReload.teksAtauNull(o, "objek"), CakapReload.teks(o, "nilai", "tiada"),
					Math.max(-3, Math.min(3, CakapReload.angka(o, "valensi", 0))), CakapReload.pecahan(o, "kuat", 1.0F),
					CakapReload.teks(o, "topik", "kabar"), CakapReload.benar(o, "keluarga", false), CakapReload.benar(o, "rahasia", false)));
		}
		return out;
	}
}
