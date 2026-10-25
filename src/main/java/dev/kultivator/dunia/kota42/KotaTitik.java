package dev.kultivator.dunia.kota42;

import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.Titik;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * The life-point kinds Kota yang Hidup adds to the 38 places (DESIGN 42.12.1): markers {@code kultivator_titik} in the NBT
 * written by 42W's {@code tools/kota_hidup_kit.py}, read by {@code TitikTemplat} like every other titik. 38NL needs no
 * registration (titik kinds are free strings); this class is the one list of the names so the kit, {@code cek_tempat.py},
 * the spread checks of 42S and every 42 module agree. Numbered kinds end in {@code _} ({@code ronda_3}, {@code nisan_17});
 * {@link #PAJANGAN} ends in {@code :} ({@code pajangan:guci}).
 */
public final class KotaTitik {

	/** Gossip centre, washing (6 / 1-2 / 1). */
	public static final String SUMUR = "sumur";
	/** Habit titik of 42.4.12. */
	public static final String CATUR = "catur";
	public static final String GANTUNG_SANGKAR = "gantung_sangkar";
	public static final String PANCING = "pancing";
	public static final String JONGKOK = "jongkok";
	/** Animals of 42.7.5. */
	public static final String KUCING_TIDUR = "kucing_tidur";
	public static final String TAMBAT = "tambat";
	public static final String KANDANG = "kandang";
	/** Kitchen smoke (one per kitchen). */
	public static final String CEROBONG = "cerobong";
	/** Visuals of 42.9.2-42.9.3. */
	public static final String LENTERA_GANTUNG = "lentera_gantung";
	public static final String PANJI = "panji";
	public static final String SPANDUK_A = "spanduk_a";
	public static final String SPANDUK_B = "spanduk_b";
	public static final String NAMA_JALAN = "nama_jalan";
	/** Services of 42. */
	public static final String LUMBUNG = "lumbung";
	public static final String PANGGUNG = "panggung";
	public static final String REKRUT = "rekrut";
	public static final String SIDANG = "sidang";
	public static final String MEJA_DADU = "meja_dadu";
	/** A growth plot (data {@code jenis}, {@code gaya}), 42.9.4. */
	public static final String KAVLING = "kavling";
	/** A house for sale. */
	public static final String DIJUAL = "dijual";

	/** Numbered kinds: night-watch route, morning market, rented stalls, Ghost Market spots, gravestones. */
	public static final String RONDA = "ronda_";
	public static final String PASAR_PAGI = "pasar_pagi_";
	public static final String LAPAK_SEWA = "lapak_sewa_";
	public static final String PASAR_HANTU = "pasar_hantu_";
	public static final String NISAN = "nisan_";
	/** Client display spots {@code pajangan:<jenis>} inside interiors. */
	public static final String PAJANGAN = "pajangan:";

	/** Every plain kind. */
	public static final List<String> TUNGGAL = List.of(SUMUR, CATUR, GANTUNG_SANGKAR, PANCING, JONGKOK, KUCING_TIDUR, TAMBAT, KANDANG,
			CEROBONG, LENTERA_GANTUNG, PANJI, SPANDUK_A, SPANDUK_B, NAMA_JALAN, LUMBUNG, PANGGUNG, REKRUT, SIDANG, MEJA_DADU, KAVLING,
			DIJUAL);
	/** Every numbered prefix. */
	public static final List<String> BERNOMOR = List.of(RONDA, PASAR_PAGI, LAPAK_SEWA, PASAR_HANTU, NISAN);

	/** True for a 42 titik kind (plain, numbered or a display). */
	public static boolean adalah(String jenis) {
		if (jenis == null) {
			return false;
		}
		if (TUNGGAL.contains(jenis) || jenis.startsWith(PAJANGAN)) {
			return true;
		}
		return nomor(jenis) >= 0;
	}

	/** The number of a numbered kind ({@code nisan_17} -> 17), or -1. */
	public static int nomor(String jenis) {
		for (String prefix : BERNOMOR) {
			if (jenis.startsWith(prefix) && jenis.length() > prefix.length()) {
				try {
					return Integer.parseInt(jenis.substring(prefix.length()));
				} catch (NumberFormatException exception) {
					return -1;
				}
			}
		}
		return -1;
	}

	/** The titik of kind {@code jenis} of place {@code situs} (loaded pieces, world coordinates). */
	public static List<Titik> titik(ServerLevel level, long situs, String jenis) {
		return TempatApi.titik(level, situs, jenis);
	}

	/** All numbered titik with {@code prefix} of a place, sorted by number ({@code ronda_1, ronda_2...}). */
	public static List<Titik> bernomor(ServerLevel level, long situs, String prefix) {
		List<Titik> out = new ArrayList<>();
		for (Titik t : TempatApi.titik(level, situs, "")) {
			if (t.jenis().startsWith(prefix) && nomor(t.jenis()) >= 0) {
				out.add(t);
			}
		}
		out.sort((a, b) -> Integer.compare(nomor(a.jenis()), nomor(b.jenis())));
		return out;
	}

	private KotaTitik() {
	}
}
