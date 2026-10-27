package dev.kultivator.dunia.kota42.cakap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.api.WargaInfo;
import dev.kultivator.dunia.kota42.warga.Warga;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The tone check of a player choice (DESIGN 42.5A.7): chance {@code p = 1 / (1 + e^-(b0 + sum w_i x_i))} over factors of
 * the listener and the situation, numbers in {@code percakapan/nada.json}. The same trick on the same warga within a day
 * is multiplied by {@code ulang} (0.7) per repeat. The roll uses the session's seeded generator, so a replay with the same
 * world gives the same answer.
 */
public final class PenilaiNada {

	/**
	 * One tone row.
	 *
	 * @param nada     the tone
	 * @param syarat   requirement keys ({@code kesan_min dewasa lajang_atau_H_maks nb_min})
	 * @param b0       intercept
	 * @param w        factor weights by factor name
	 * @param berhasil effects on success
	 * @param gagal    effects on failure
	 */
	public record Baris(Nada nada, JsonObject syarat, double b0, Map<String, Double> w, JsonObject berhasil, JsonObject gagal) {
	}

	/** The table of {@code nada.json}. */
	public record Tabel(double ulang, Map<Nada, Baris> nada) {
		public static final Tabel KOSONG = new Tabel(0.7D, Map.of());

		static Tabel parse(JsonObject json) {
			Map<Nada, Baris> rows = new EnumMap<>(Nada.class);
			for (JsonObject o : CakapReload.objekList(json, "nada")) {
				Nada n = Nada.dari(CakapReload.teks(o, "id", ""));
				if (n == null) {
					continue;
				}
				Map<String, Double> w = new LinkedHashMap<>();
				for (Map.Entry<String, JsonElement> e : CakapReload.objek(o, "w").entrySet()) {
					if (e.getValue().isJsonPrimitive()) {
						w.put(e.getKey(), e.getValue().getAsDouble());
					}
				}
				rows.put(n, new Baris(n, CakapReload.objek(o, "syarat"), CakapReload.pecahan(o, "b0", 0.0F), java.util.Collections.unmodifiableMap(w),
						CakapReload.objek(o, "berhasil"), CakapReload.objek(o, "gagal")));
			}
			return new Tabel(json.has("ulang") ? json.get("ulang").getAsDouble() : 0.7D, rows);
		}
	}

	/**
	 * The numbers of one check.
	 *
	 * @param situasi  the listener's snapshot (traits, PAD, impression, realm gap seen from the listener)
	 * @param namaBaik town reputation of the player
	 * @param infami   the player's infamy
	 * @param saksi    other bodies in view
	 * @param nb       spirit coins the player carries (NB)
	 * @param ulang    times this tone was used on this warga today
	 * @param umurPemain rough age of the player's look (adult = 30) for {@code sebaya}
	 */
	public record Faktor(Topik.Situasi situasi, int namaBaik, int infami, int saksi, long nb, int ulang, int umurPemain) {
	}

	/** True when the tone may be chosen now (42.5A.7 "Syarat"). */
	public static boolean boleh(Nada nada, Faktor f) {
		Baris b = CakapReload.nada().nada().get(nada);
		if (b == null) {
			return nada == Nada.SOPAN;
		}
		JsonObject s = b.syarat();
		Topik.Situasi si = f.situasi();
		if (s.has("kesan_min") && si.nilaiKesan() < s.get("kesan_min").getAsInt()) {
			return false;
		}
		if (CakapReload.benar(s, "dewasa", false) && (si.tahap().equals("anak") || si.tahap().equals("remaja"))) {
			return false;
		}
		if (s.has("lajang_atau_H_maks") && si.pembicara().pasangan != Warga.TANPA && si.sifat().h() > s.get("lajang_atau_H_maks").getAsInt()) {
			return false;
		}
		return !s.has("nb_min") || f.nb() >= s.get("nb_min").getAsLong();
	}

	/** The success chance 0-1 (before the repeat factor). */
	public static double peluangDasar(Nada nada, Faktor f) {
		Baris b = CakapReload.nada().nada().get(nada);
		if (b == null) {
			return 0.9D;
		}
		double z = b.b0();
		for (Map.Entry<String, Double> e : b.w().entrySet()) {
			z += e.getValue() * nilai(e.getKey(), f);
		}
		return 1.0D / (1.0D + Math.exp(-z));
	}

	/** The success chance with the repeat factor {@code ulang^n}. */
	public static double peluang(Nada nada, Faktor f) {
		return peluangDasar(nada, f) * Math.pow(CakapReload.nada().ulang(), Math.max(0, f.ulang()));
	}

	/** One factor x_i by name (42.5A.7 / CakapData nada format). */
	static double nilai(String nama, Faktor f) {
		Topik.Situasi s = f.situasi();
		if (nama.length() == 1 && Kepribadian.HURUF.indexOf(nama.charAt(0)) >= 0) {
			return s.sifat().get(nama.charAt(0));
		}
		Warga w = s.pembicara();
		return switch (nama) {
			case "P" -> s.p() * 100.0D;
			case "A" -> s.a() * 100.0D;
			case "D" -> s.d() * 100.0D;
			case "kesan" -> s.nilaiKesan();
			case "nama_baik" -> f.namaBaik();
			case "sebaya" -> Math.abs(WargaApi.umur(w) - f.umurPemain()) <= 8 ? 1.0D : 0.0D;
			case "ranah" -> s.ranahSelisih();
			case "infami" -> f.infami();
			case "saksi" -> f.saksi();
			case "serakah" -> s.watak().equals("serakah") ? 1.0D : 0.0D;
			case "miskin" -> w.peranNpc().kelompok == dev.kultivator.dunia.kota.NpcRole.Kelompok.PEKERJA
					|| WargaInfo.Tahap.dari(WargaApi.umur(w)) == WargaInfo.Tahap.PENSIUN ? 1.0D : 0.0D;
			default -> 0.0D;
		};
	}

	private PenilaiNada() {
	}
}
