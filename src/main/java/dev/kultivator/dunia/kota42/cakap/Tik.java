package dev.kultivator.dunia.kota42.cakap;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.ToIntFunction;

/**
 * Speech tics (DESIGN 42.5A.8; data {@code percakapan/tik.json}, 36 tics, texts {@code kultivator.cakap.tik.<id>}):
 * every warga owns one or two, drawn once from its seed among the tics whose fixed conditions (traits, temperament, habits,
 * group, life stage, sex, dialect) hold. When it speaks, one owned tic whose full condition holds now (hour too) is added
 * with chance {@code peluang} (x {@code pengali_A} while arousal exceeds {@code ambang_A}), never the same tic twice in a
 * row. Positions: {@code awal} before the sentence, {@code akhir} after it, {@code sisip} as a trailing aside,
 * {@code gagap} a stammered opener ({@code lex.gagap.0-5}).
 * <p>
 * Pure: randomness only through {@link Realisasi#pecahan}, so {@code tools/cek_cakap.py} mirrors it exactly.
 */
public final class Tik {

	/** Condition keys that depend on the moment: ignored when a warga's own tics are drawn, checked when one is said. */
	public static final Set<String> SYARAT_SAAT = Set.of("jam", "cuaca", "musim", "acara", "wajah", "label_emosi");
	/** Number of stammer openers {@code lex.gagap.<n>}. */
	public static final int GAGAP = 6;

	/**
	 * One tic.
	 *
	 * @param id     id (text key {@code kultivator.cakap.tik.<id>}, none for {@code gagap})
	 * @param posisi {@code awal akhir sisip gagap}
	 * @param bobot  draw weight
	 * @param syarat condition (CakapData {@code syarat})
	 */
	public record Data(String id, String posisi, int bobot, JsonObject syarat) {
		/** The condition without the moment keys of {@link #SYARAT_SAAT}. */
		public JsonObject syaratTetap() {
			JsonObject o = new JsonObject();
			syarat.entrySet().forEach(e -> {
				if (!SYARAT_SAAT.contains(e.getKey())) {
					o.add(e.getKey(), e.getValue());
				}
			});
			return o;
		}
	}

	/**
	 * The file.
	 *
	 * @param peluang chance a tic is added to an utterance (config {@code kotaBicara.tikPeluang} overrides it)
	 * @param pengaliA chance multiplier while aroused
	 * @param ambangA  arousal above which {@link #pengaliA} applies
	 * @param maks     tics per warga (1 or 2 are drawn)
	 * @param tik      all tics, file order
	 */
	public record Tabel(double peluang, double pengaliA, double ambangA, int maks, List<Data> tik) {
		public static final Tabel KOSONG = new Tabel(0.25D, 1.5D, 0.4D, 2, List.of());

		public static Tabel parse(@Nullable JsonObject json) {
			if (json == null || json.isEmpty()) {
				return KOSONG;
			}
			List<Data> list = new ArrayList<>();
			for (JsonObject o : TataBahasa.objekList(json, "tik")) {
				String id = TataBahasa.teks(o, "id", "");
				if (!id.isEmpty()) {
					list.add(new Data(id, TataBahasa.teks(o, "posisi", "awal"), Math.max(1, TataBahasa.angka(o, "bobot", 1)),
							TataBahasa.objek(o, "syarat")));
				}
			}
			return new Tabel(TataBahasa.dbl(json, "peluang", 0.25D), TataBahasa.dbl(json, "pengali_A", 1.5D),
					TataBahasa.dbl(json, "ambang_A", 0.4D), Math.max(1, TataBahasa.angka(json, "maks_per_warga", 2)), List.copyOf(list));
		}

		public @Nullable Data cari(@Nullable String id) {
			for (Data d : tik) {
				if (d.id.equals(id)) {
					return d;
				}
			}
			return null;
		}
	}

	private static volatile Tabel tabel = Tabel.KOSONG;

	static void pasang(Tabel baru) {
		tabel = baru == null ? Tabel.KOSONG : baru;
	}

	public static Tabel tabel() {
		TataBahasa.pastikan();
		return tabel;
	}

	/**
	 * Draws the tics a warga owns (call once and store; deterministic for a seed): first a coin for one or two
	 * ({@code maks} 1 means one), then weighted draws without replacement among tics whose fixed condition holds.
	 *
	 * @param benih seed, e.g. {@code Cakap.benih(worldSeed, wargaId, 0, 0, 0, "tik")}
	 * @param cocok the warga's condition matcher (-1 = fails)
	 */
	public static List<String> milik(Tabel t, long benih, ToIntFunction<JsonObject> cocok) {
		List<Data> calon = new ArrayList<>();
		for (Data d : t.tik) {
			if (cocok.applyAsInt(d.syaratTetap()) >= 0) {
				calon.add(d);
			}
		}
		List<String> out = new ArrayList<>(2);
		if (calon.isEmpty()) {
			return out;
		}
		SplittableRandom r = new SplittableRandom(benih);
		int n = t.maks <= 1 ? 1 : Realisasi.pecahan(r) < 0.5D ? 1 : 2;
		for (int i = 0; i < n && !calon.isEmpty(); i++) {
			out.add(calon.remove(pilih(calon, r)).id);
		}
		return out;
	}

	/**
	 * The tic said with this utterance, or null: no draw at all when none is eligible; otherwise one draw for the chance and,
	 * with two eligible tics, one weighted draw.
	 *
	 * @param milik    the speaker's own tics
	 * @param peluang  base chance (negative = the file's {@code peluang})
	 * @param arousal  current arousal (PAD a)
	 * @param terakhir the speaker's previous tic (never repeated in a row)
	 * @param cocok    the speaker's condition matcher now
	 */
	public static @Nullable Data sisip(Tabel t, List<String> milik, double peluang, double arousal, @Nullable String terakhir,
			ToIntFunction<JsonObject> cocok, SplittableRandom r) {
		List<Data> calon = new ArrayList<>(2);
		for (String id : milik) {
			Data d = t.cari(id);
			if (d != null && !d.id.equals(terakhir) && cocok.applyAsInt(d.syarat) >= 0) {
				calon.add(d);
			}
		}
		if (calon.isEmpty()) {
			return null;
		}
		double p = (peluang < 0.0D ? t.peluang : peluang) * (arousal > t.ambangA ? t.pengaliA : 1.0D);
		if (Realisasi.pecahan(r) >= p) {
			return null;
		}
		return calon.size() == 1 ? calon.get(0) : calon.get(pilih(calon, r));
	}

	private static int pilih(List<Data> calon, SplittableRandom r) {
		double total = 0.0D;
		for (Data d : calon) {
			total += d.bobot;
		}
		double u = Realisasi.pecahan(r) * total;
		for (int i = 0; i < calon.size(); i++) {
			u -= calon.get(i).bobot;
			if (u < 0.0D) {
				return i;
			}
		}
		return calon.size() - 1;
	}

	private Tik() {
	}
}
