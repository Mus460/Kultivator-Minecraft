package dev.kultivator.dunia.kota42.ragam;

import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.hidup.Penduduk;

/**
 * The rhythm of the week and the seasons (DESIGN 42.4A.4): day 0 of the week is the <b>big market day</b> (visiting
 * traders, markets x1.3), day 3 the <b>livestock and fish market</b> (pens and quays busy), day 6 the <b>rest day</b> (half
 * the workers go fishing, picnic in the garden, visit the temple or relatives); the seasons swap the pools' weights (winter:
 * indoors x1.4 and soup, summer: the waterside, autumn: harvest, spring: planting and kites). Festivals and ceremonies need
 * nothing here: an event runs before every overrider (42.4A.2 order). Pure functions; 42S may scale its hour multipliers
 * with {@link #pengaliZona}.
 */
public final class IramaMingguan {

	public static final int PASAR_BESAR = 0;
	public static final int TERNAK = 3;
	public static final int ISTIRAHAT = 6;

	/** Day of the week 0-6 of world day {@code hari}. */
	public static int pekan(long hari) {
		return (int) Math.floorMod(hari, 7L);
	}

	/** Season 0-3 ({@code Jadwal.SEMI..DINGIN}) of world day {@code hari}. */
	public static int musim(long hari) {
		return (int) Math.floorMod(hari / Jadwal.HARI_MUSIM, 4L);
	}

	/** {@code kultivator.kota.ragam2.hari.<...>}: the name of the kind of day. */
	public static String kunciHari(long hari) {
		return "kultivator.kota.ragam2.hari." + switch (pekan(hari)) {
			case PASAR_BESAR -> "pasar_besar";
			case TERNAK -> "ternak";
			case ISTIRAHAT -> "istirahat";
			default -> "biasa";
		};
	}

	/** {@code kultivator.kota.ragam2.musim.<0-3>}. */
	public static String kunciMusim(long hari) {
		return "kultivator.kota.ragam2.musim." + musim(hari);
	}

	/**
	 * True when {@code p} has the rest day off: half the workers (by seed) on day 6; stall keepers, guards and fixed lives
	 * keep their day (38.N.3 rest day, same half as the built-in round).
	 */
	public static boolean libur(Penduduk p, long hari) {
		return pekan(hari) == ISTIRAHAT && p.peran().kelompok == NpcRole.Kelompok.PEKERJA && !p.peran().stationary && (p.benih() & 1L) == 0L;
	}

	/** The weight factor of activity {@code a} on day {@code hari} (week and season; 1 = unchanged). */
	public static double bobot(KumpulanKegiatan.Aktivitas a, long hari, boolean libur) {
		double w = 1.0D;
		switch (pekan(hari)) {
			case PASAR_BESAR -> {
				if (a.ada("pasar")) {
					w *= 1.3D;
				}
				if (a.ada("jajan")) {
					w *= 1.2D;
				}
			}
			case TERNAK -> {
				if (a.ada("ternak") || a.ada("ikan")) {
					w *= 1.5D;
				}
			}
			case ISTIRAHAT -> {
				if (a.ada("piknik") || a.ada("kerabat") || a.ada("kuil") || a.ada("santai")) {
					w *= 1.6D;
				}
				if (libur && a.inti()) {
					w *= 0.3D;
				}
			}
			default -> {
			}
		}
		switch (musim(hari)) {
			case Jadwal.DINGIN -> {
				if (a.ada("teduh") || a.ada("rumah")) {
					w *= 1.4D;
				}
				if (a.ada("sup")) {
					w *= 1.5D;
				}
				if (a.ada("luar") && !a.inti()) {
					w *= 0.7D;
				}
				if (a.ada("air")) {
					w *= 0.4D;
				}
			}
			case Jadwal.PANAS -> {
				if (a.ada("air")) {
					w *= 1.6D;
				}
				if (a.ada("berat") && !a.inti()) {
					w *= 0.8D;
				}
			}
			case Jadwal.GUGUR -> {
				if (a.ada("panen")) {
					w *= 1.8D;
				}
			}
			default -> {
				if (a.ada("tanam")) {
					w *= 1.8D;
				}
				if (a.ada("layangan")) {
					w *= 1.6D;
				}
			}
		}
		return w;
	}

	/**
	 * The week's and season's factor on a 42.4A.1 zone kind ({@code Kapasitas.JENIS}) at hour {@code jam} of day
	 * {@code hari} (1 = unchanged): market days fill the market, the rest day empties workshops into gardens and temples,
	 * winter keeps people home.
	 */
	public static double pengaliZona(String jenisZona, long hari, int jam) {
		double m = 1.0D;
		switch (pekan(hari)) {
			case PASAR_BESAR -> {
				if (jenisZona.equals("pasar")) {
					m *= 1.3D;
				} else if (jenisZona.equals("ruas") || jenisZona.equals("gerbang")) {
					m *= 1.15D;
				}
			}
			case TERNAK -> {
				if (jenisZona.equals("ladang")) {
					m *= 1.3D;
				} else if (jenisZona.equals("pasar")) {
					m *= 1.1D;
				}
			}
			case ISTIRAHAT -> {
				switch (jenisZona) {
					case "kerja" -> m *= 0.5D;
					case "alun", "kuil" -> m *= 1.3D;
					case "teh" -> m *= 1.2D;
					case "rumah" -> m *= 1.1D;
					case "sekolah" -> m *= 0.0D;
					default -> {
					}
				}
			}
			default -> {
			}
		}
		switch (musim(hari)) {
			case Jadwal.DINGIN -> {
				if (jenisZona.equals("rumah") || jenisZona.equals("teh") || jenisZona.equals("arak")) {
					m *= 1.3D;
				} else if (jenisZona.equals("alun") || jenisZona.equals("ladang")) {
					m *= 0.7D;
				}
			}
			case Jadwal.PANAS -> {
				if (jenisZona.equals("ladang") && jam >= 13 && jam < 19) {
					m *= 1.2D;
				}
			}
			default -> {
			}
		}
		return m;
	}

	private IramaMingguan() {
	}
}
