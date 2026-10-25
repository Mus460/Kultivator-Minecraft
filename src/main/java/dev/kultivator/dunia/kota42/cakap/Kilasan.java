package dev.kultivator.dunia.kota42.cakap;

import dev.kultivator.dunia.kota42.api.Kenangan;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.Warga;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Callbacks at the opening of a conversation (DESIGN 42.5A.6 "Kilas balik"): what a townsperson brings up first when it
 * meets a player again, in priority order: a promise past its deadline, a promise due today, a debt between them, a strong
 * event since they last met (a memory of 42.4.10 or a weighty fact about the player), or a long absence. Each callback is
 * said once per state change (the promise is marked {@code ditagih}; a memory or fact only counts when newer than the last
 * meeting).
 */
public final class Kilasan {

	/** Days without a meeting that count as "long time no see". */
	public static final int LAMA_HARI = 10;

	/**
	 * One callback to say.
	 *
	 * @param jenis  {@code janji_lewat janji_tempo utang_warga utang_pemain kenangan kabar_pemain lama} (grammar topic
	 *               {@code kilas.<jenis>} of intent {@code KILAS_BALIK})
	 * @param fakta  the fact behind it (promise, deed), null for none
	 * @param isian  slot values ({@code waktu_lalu} = days ago as Long, {@code apa} = promised thing word, {@code kenangan},
	 *               {@code jumlah} = NB)
	 * @param janji  the promise it is about, null for none
	 */
	public record Kilas(String jenis, @Nullable Fakta fakta, Map<String, Object> isian, @Nullable Pengetahuan.Janji janji) {
		public String topik() {
			return "kilas." + jenis;
		}
	}

	/** The callback {@code w} has for the player of history {@code r} today, or null. */
	public static @Nullable Kilas cari(Pengetahuan pk, Warga w, Pengetahuan.Riwayat r, @Nullable Ingatan ing, long hari) {
		for (Pengetahuan.Janji j : r.janji) {
			if (j.terbuka() && hari > j.batas && !j.ditagih) {
				return new Kilas("janji_lewat", janjiFakta(j, r.pemain, w, hari), isian(hari - j.dibuat, j.apa, null, 0), j);
			}
		}
		for (Pengetahuan.Janji j : r.janji) {
			if (j.terbuka() && hari == j.batas && r.temuHari != hari) {
				return new Kilas("janji_tempo", janjiFakta(j, r.pemain, w, hari), isian(hari - j.dibuat, j.apa, null, 0), j);
			}
		}
		if (ing != null && ing.utang != 0 && r.temuHari != hari) {
			return new Kilas(ing.utang > 0 ? "utang_warga" : "utang_pemain", null, isian(0L, "", null, Math.abs(ing.utang)), null);
		}
		if (ing != null && r.temuHari >= 0L) {
			for (int i = 0; i < Ingatan.PERISTIWA; i++) {
				Kenangan k = ing.kenangan(i);
				if (k != null && ing.hariKenangan(i) > r.temuHari) {
					return new Kilas("kenangan", null, isian(hari - ing.hariKenangan(i), "", k.id(), 0), null);
				}
			}
		}
		if (r.temuHari >= 0L) {
			Fakta best = null;
			for (Fakta f : pk.ingat(w, hari)) {
				if (f.subjek().jenis() == Fakta.Jenis.PEMAIN && f.subjek().id() == r.pemain && Math.abs(f.valensi()) >= 2
						&& f.hariTahu() > r.temuHari && (best == null || Math.abs(f.valensi()) > Math.abs(best.valensi()))) {
					best = f;
				}
			}
			if (best != null) {
				return new Kilas("kabar_pemain", best, isian(hari - best.hariKejadian(), "", null, 0), null);
			}
			if (hari - r.temuHari >= LAMA_HARI) {
				return new Kilas("lama", null, isian(hari - r.temuHari, "", null, 0), null);
			}
		}
		return null;
	}

	/** Marks a callback said (a promise is brought up once after its deadline). */
	public static void tandai(Kilas k) {
		if (k.janji != null && k.jenis.equals("janji_lewat")) {
			k.janji.ditagih = true;
		}
	}

	private static Fakta janjiFakta(Pengetahuan.Janji j, int pemain, Warga w, long hari) {
		return new Fakta("BERJANJI", Fakta.Rujuk.pemain(pemain), j.apa.isEmpty() ? Fakta.Rujuk.warga(w.id) : Fakta.Rujuk.kata(j.apa),
				(int) j.batas, Fakta.Kebenaran.BENAR, 100, Fakta.Asal.SAKSI, j.dibuat, hari, Fakta.KEKUATAN_DENGAR * 4.0F, 0, 1, -1);
	}

	private static Map<String, Object> isian(long hariLalu, String apa, @Nullable String kenangan, int jumlah) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("waktu_lalu", Math.max(0L, hariLalu));
		if (!apa.isEmpty()) {
			m.put("apa", apa);
		}
		if (kenangan != null) {
			m.put("kenangan", kenangan);
		}
		if (jumlah != 0) {
			m.put("jumlah", jumlah);
		}
		return m;
	}

	private Kilasan() {
	}
}
