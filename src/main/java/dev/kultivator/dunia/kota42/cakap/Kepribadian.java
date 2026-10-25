package dev.kultivator.dunia.kota42.cakap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.cerita.Tokoh;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Personality (DESIGN 42.5A.2): six HEXACO traits -100..100 per townsperson, born once from the temperament
 * ({@code Tokoh.WATAK}), the habits and a seeded normal spread, then fixed for life; a child inherits the parents' mean
 * +- {@code warisan}. Numbers come from {@code percakapan/kepribadian.json} ({@link Tabel}).
 * <p>
 * Generation is a pure function of (world seed, warga id, temperament, habits, parents), so the stored copy in
 * {@link Pengetahuan} is only a cache: losing it gives back the same person.
 */
public final class Kepribadian {

	/** Trait letters in storage order. */
	public static final String HURUF = "HEXACO";

	/**
	 * Habit ids by index 1-32 (DESIGN 42.4.12 table order; {@code Warga.kebiasaan1/2}). Index 0 = none. A read-only mirror
	 * of 42A-2's {@code Kebiasaan} numbering so 42K can read habits without depending on it.
	 */
	public static final List<String> KEBIASAAN = List.of("", "pemabuk", "kakek_catur", "gosip_sumur", "burung_sangkar", "tai_chi",
			"pemancing", "adu_jangkrik", "penyair", "pemain_erhu", "pendoa_kuil", "layang_layang", "penyulam", "tidur_siang", "pesolek",
			"pelit", "dermawan", "pemarah", "takhayul", "kutu_buku", "teman_kucing", "penjudi", "pencerita_anak", "tari_kipas",
			"penyapu_jalan", "pelukis_gula", "pecinta_teh", "penggemar_opera", "pengintip", "penabung", "pemuja_kultivator", "pemalas",
			"pekerja_keras");

	/**
	 * The six traits.
	 *
	 * @param h honesty-humility
	 * @param e emotionality
	 * @param x extraversion
	 * @param a agreeableness
	 * @param c conscientiousness
	 * @param o openness
	 */
	public record Sifat(int h, int e, int x, int a, int c, int o) {
		public static final Sifat NETRAL = new Sifat(0, 0, 0, 0, 0, 0);

		public Sifat {
			h = jepit(h);
			e = jepit(e);
			x = jepit(x);
			a = jepit(a);
			c = jepit(c);
			o = jepit(o);
		}

		/** A trait by letter (H E X A C O, plus S = -E stability); 0 for anything else. */
		public int get(char huruf) {
			return switch (Character.toUpperCase(huruf)) {
				case 'H' -> h;
				case 'E' -> e;
				case 'X' -> x;
				case 'A' -> a;
				case 'C' -> c;
				case 'O' -> o;
				case 'S' -> -e;
				default -> 0;
			};
		}

		public int[] larik() {
			return new int[] {h, e, x, a, c, o};
		}

		public static Sifat dari(int[] v) {
			return new Sifat(v[0], v[1], v[2], v[3], v[4], v[5]);
		}

		/** A copy with one trait shifted (temporary effects such as drink: C -30). */
		public Sifat geser(char huruf, int delta) {
			int[] v = larik();
			int i = HURUF.indexOf(Character.toUpperCase(huruf));
			if (i >= 0) {
				v[i] += delta;
			}
			return dari(v);
		}

		@Override
		public String toString() {
			return "H" + h + " E" + e + " X" + x + " A" + a + " C" + c + " O" + o;
		}
	}

	/**
	 * The table of {@code kepribadian.json}.
	 *
	 * @param simpangan seeded spread (standard deviation) around the temperament mean
	 * @param warisan   spread of a child around the parents' mean
	 * @param watak     temperament means
	 * @param kebiasaan habit shifts
	 */
	public record Tabel(int simpangan, int warisan, Map<String, int[]> watak, Map<String, int[]> kebiasaan) {
		public static final Tabel KOSONG = new Tabel(20, 25, Map.of(), Map.of());

		static Tabel parse(JsonObject json) {
			return new Tabel(CakapReload.angka(json, "simpangan", 20), CakapReload.angka(json, "warisan", 25), sifatPeta(json, "watak"),
					sifatPeta(json, "kebiasaan"));
		}

		private static Map<String, int[]> sifatPeta(JsonObject json, String key) {
			Map<String, int[]> out = new LinkedHashMap<>();
			JsonObject o = CakapReload.objek(json, key);
			for (Map.Entry<String, JsonElement> e : o.entrySet()) {
				if (e.getKey().startsWith("_") || !e.getValue().isJsonObject()) {
					continue;
				}
				out.put(e.getKey(), larik(e.getValue().getAsJsonObject()));
			}
			return out;
		}

		static int[] larik(JsonObject o) {
			int[] v = new int[6];
			for (int i = 0; i < 6; i++) {
				v[i] = CakapReload.angka(o, String.valueOf(HURUF.charAt(i)), 0);
			}
			return v;
		}
	}

	/** The temperament id of a warga ({@code Tokoh.WATAK} index). */
	public static String watak(Warga w) {
		return w.watak >= 0 && w.watak < Tokoh.WATAK.size() ? Tokoh.WATAK.get(w.watak) : "ramah";
	}

	/** Habit id of index 1-32, "" for none or unknown. */
	public static String kebiasaan(int indeks) {
		return indeks > 0 && indeks < KEBIASAAN.size() ? KEBIASAAN.get(indeks) : "";
	}

	/** True when the warga has habit {@code id}. */
	public static boolean punya(Warga w, String id) {
		return !id.isEmpty() && (id.equals(kebiasaan(w.kebiasaan1)) || id.equals(kebiasaan(w.kebiasaan2)));
	}

	/**
	 * The birth personality: temperament mean + habit shifts + seeded normal(0, simpangan). Children of two known parents
	 * ({@code induk} not null) use the parents' mean + normal(0, warisan) instead of the temperament mean, then the habits.
	 */
	public static Sifat bangkit(Warga w, Tabel t, long benihDunia, Sifat ayah, Sifat ibu) {
		SplittableRandom r = Cakap.acak(benihDunia, w.id, 0, 0L, 0, "kepribadian");
		int[] v = new int[6];
		boolean induk = ayah != null && ibu != null;
		int[] mean = induk ? rata(ayah.larik(), ibu.larik()) : t.watak.getOrDefault(watak(w), new int[6]);
		int spread = induk ? t.warisan : t.simpangan;
		for (int i = 0; i < 6; i++) {
			v[i] = mean[i] + (int) Math.round(normal(r) * spread);
		}
		for (String hab : new String[] {kebiasaan(w.kebiasaan1), kebiasaan(w.kebiasaan2)}) {
			int[] shift = t.kebiasaan.get(hab);
			if (shift != null) {
				for (int i = 0; i < 6; i++) {
					v[i] += shift[i];
				}
			}
		}
		return Sifat.dari(v);
	}

	private static int[] rata(int[] a, int[] b) {
		int[] v = new int[6];
		for (int i = 0; i < 6; i++) {
			v[i] = (a[i] + b[i]) / 2;
		}
		return v;
	}

	/** Standard normal from the seeded generator (Box-Muller, deterministic). */
	static double normal(SplittableRandom r) {
		double u = Math.max(1.0E-12D, r.nextDouble());
		double v = r.nextDouble();
		return Math.sqrt(-2.0D * Math.log(u)) * Math.cos(2.0D * Math.PI * v);
	}

	static int jepit(int v) {
		return Math.max(-100, Math.min(100, v));
	}

	private Kepribadian() {
	}
}
