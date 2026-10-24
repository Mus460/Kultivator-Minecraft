package dev.kultivator.dunia.tempat.tema.air;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The water lines of the Kota Air (DESIGN 38.T.5.2, 38.T.5.4) in city-local continuous coordinates ({@link AirKota}):
 * the Perahu Pos loop (the same polyline as {@code RUTE_POS} of {@code tools/gen_tempat_tema_air.py}, whose
 * {@code jalur_perahu} points mark it in the templates; keep both lists identical), the six-place Tur Kanal, the
 * dragon-boat race course (Kanal Utama + two laps of the lantern lake, passing under the causeway hump) and the
 * lantern drift lanes of the Malam Lentera Air (to the client as {@code TemaNet.SPLINE}). Every canal centre line lies
 * on the water: border canals on x, z = +-48, the Kanal Utama on x = 0, the cross canals of Jembatan Kembar on x = -96 and
 * z = 0, the market canal on z = -16.
 * <p>
 * Movement is a pure function of the distance along a {@link Lintasan}; boats are moved by the server one step a tick
 * and interpolated by the client (no path finding, 38.T.2.7).
 */
public final class JalurKanal {

	/** One corner of a line; {@code halte} names a stop ("" none). */
	public record Titik(double x, double z, String halte) {
	}

	/** Perahu Pos loop (start and end at the central stop of Dermaga Perahu). */
	public static final List<Titik> POS = List.of(
			t(48, 118, "pusat"), t(48, 48), t(0, 48), t(0, 108, "gerbang_air"), t(0, 48),
			t(0, 32, "danau"), t(0, 48), t(-48, 48), t(-48, 0), t(-74, 0, "jembatan_kembar"),
			t(-48, 0), t(-48, -48), t(-8, -48, "rumah_sutra"), t(48, -48), t(48, 48), t(48, 118));

	/**
	 * Tur Kanal (6 minutes, 7200 ticks): starts and ends at the boat hire of Dermaga Perahu (north border canal by the
	 * office) and passes the Pintu Air, Jembatan Kembar, Taman Sang Sastrawan, Rumah Sutra, the Pasar Air Pagi and the
	 * Panggung Air; {@code halte} carries the place the guide tells about when the boat passes it.
	 */
	public static final List<Titik> TUR = List.of(
			t(128, 48, "dermaga"), t(48, 48), t(0, 48), t(0, 126, "pintu_air"), t(0, 48), t(-48, 48), t(-48, 0),
			t(-90, 0, "jembatan_kembar"), t(-48, 0), t(-48, -48), t(-48, -92, "taman_sastra"), t(-48, -48),
			t(-6, -48, "rumah_sutra"), t(48, -48), t(48, -16), t(96, -16, "pasar_air"), t(48, -16), t(48, 48), t(0, 48),
			t(0, 25), t(-20, 16), t(-20, -15), t(0, -15, "panggung_air"), t(10, -15), t(10, 16), t(0, 25), t(0, 48),
			t(48, 48), t(128, 48, "akhir"));

	/** The approach of the race: start line on the Kanal Utama, north into the lake channel. */
	public static final List<Titik> LOMBA_MASUK = List.of(t(0, 104, "start"), t(0, 48), t(0, 38));
	/**
	 * One lap of the lake around the Panggung Air (clockwise seen from above): west of the stage, under the causeway hump
	 * at z -16, between the stage and the pagoda island (x 14..26 is the island and its plank bridge), back south.
	 */
	public static final List<Titik> LOMBA_PUTARAN = List.of(t(0, 38), t(0, 22), t(-24, 20), t(-24, -16), t(0, -16), t(10, -16),
			t(10, 20), t(0, 22), t(0, 38));
	/** Back to the finish line by the Pintu Air. */
	public static final List<Titik> LOMBA_KELUAR = List.of(t(0, 38), t(0, 48), t(0, 120, "finish"));
	/** Laps of the race. */
	public static final int PUTARAN = 2;

	/** The release jetties of the lantern night (generator dermaga_lentera, ends 2 blocks out) and the lake centre. */
	public static final double[][] DERMAGA_LENTERA = {
			{-18, -25}, {16, -25}, {32, 6}, {32, 18}, {14, 24}, {-16, 24}, {-32, 14}, {-32, -12}};

	private static Titik t(double x, double z) {
		return new Titik(x, z, "");
	}

	private static Titik t(double x, double z, String halte) {
		return new Titik(x, z, halte);
	}

	/** The race course as one polyline. */
	public static List<Titik> lomba() {
		List<Titik> out = new ArrayList<>(LOMBA_MASUK);
		for (int i = 0; i < PUTARAN; i++) {
			out.addAll(LOMBA_PUTARAN.subList(1, LOMBA_PUTARAN.size()));
		}
		out.addAll(LOMBA_KELUAR.subList(1, LOMBA_KELUAR.size()));
		return out;
	}

	/** A polyline with arc length (city-local coordinates). */
	public static final class Lintasan {
		private final List<Titik> titik;
		private final double[] cum;

		public Lintasan(List<Titik> titik) {
			this.titik = List.copyOf(titik);
			this.cum = new double[this.titik.size()];
			for (int i = 1; i < this.titik.size(); i++) {
				Titik a = this.titik.get(i - 1);
				Titik b = this.titik.get(i);
				cum[i] = cum[i - 1] + Math.hypot(b.x - a.x, b.z - a.z);
			}
		}

		public double panjang() {
			return cum[cum.length - 1];
		}

		public List<Titik> titik() {
			return titik;
		}

		/** Arc length at corner i. */
		public double jarakTitik(int i) {
			return cum[Math.max(0, Math.min(cum.length - 1, i))];
		}

		private int segmen(double s) {
			int lo = 0;
			int hi = cum.length - 1;
			while (hi - lo > 1) {
				int mid = (lo + hi) >>> 1;
				if (cum[mid] <= s) {
					lo = mid;
				} else {
					hi = mid;
				}
			}
			return lo;
		}

		/** City-local point {x, z} at arc length s (clamped), offset sideways by {@code lajur} blocks (right = +). */
		public double[] di(double s, double lajur) {
			double ss = Math.max(0.0D, Math.min(panjang(), s));
			int i = segmen(ss);
			Titik a = titik.get(i);
			Titik b = titik.get(Math.min(i + 1, titik.size() - 1));
			double len = cum[Math.min(i + 1, cum.length - 1)] - cum[i];
			double f = len <= 1.0E-6D ? 0.0D : (ss - cum[i]) / len;
			double x = a.x + (b.x - a.x) * f;
			double z = a.z + (b.z - a.z) * f;
			if (lajur != 0.0D && len > 1.0E-6D) {
				double dx = (b.x - a.x) / len;
				double dz = (b.z - a.z) / len;
				x += -dz * lajur;
				z += dx * lajur;
			}
			return new double[] {x, z};
		}

		/** City-local heading {dx, dz} (unit) at arc length s, smoothed over 3 blocks around the corners. */
		public double[] arah(double s) {
			double[] p = di(s - 1.5D, 0.0D);
			double[] q = di(s + 1.5D, 0.0D);
			double dx = q[0] - p[0];
			double dz = q[1] - p[1];
			double len = Math.hypot(dx, dz);
			return len < 1.0E-6D ? new double[] {0.0D, 1.0D} : new double[] {dx / len, dz / len};
		}

		/** The stop the arc length s is within {@code r} of ("" none). */
		public String halteDekat(double s, double r) {
			for (int i = 0; i < titik.size(); i++) {
				if (!titik.get(i).halte.isEmpty() && Math.abs(cum[i] - s) <= r) {
					return titik.get(i).halte;
				}
			}
			return "";
		}

		/** Arc length of the first corner of stop {@code halte} (-1 when unknown). */
		public double jarakHalte(String halte) {
			for (int i = 0; i < titik.size(); i++) {
				if (titik.get(i).halte.equals(halte)) {
					return cum[i];
				}
			}
			return -1.0D;
		}

		/** The arc length on this line nearest the city-local point (x, z). */
		public double terdekat(double x, double z) {
			double best = 0.0D;
			double bestD = Double.MAX_VALUE;
			for (int i = 0; i + 1 < titik.size(); i++) {
				Titik a = titik.get(i);
				Titik b = titik.get(i + 1);
				double vx = b.x - a.x;
				double vz = b.z - a.z;
				double len2 = vx * vx + vz * vz;
				double f = len2 < 1.0E-9D ? 0.0D : Math.max(0.0D, Math.min(1.0D, ((x - a.x) * vx + (z - a.z) * vz) / len2));
				double px = a.x + vx * f - x;
				double pz = a.z + vz * f - z;
				double d = px * px + pz * pz;
				if (d < bestD) {
					bestD = d;
					best = cum[i] + Math.sqrt(len2) * f;
				}
			}
			return best;
		}
	}

	public static final Lintasan LINTAS_POS = new Lintasan(POS);
	public static final Lintasan LINTAS_TUR = new Lintasan(TUR);
	public static final Lintasan LINTAS_LOMBA = new Lintasan(lomba());

	/** World position at arc length s of a line in a town (boat height). */
	public static Vec3 dunia(AirKota.Hidup kota, Lintasan l, double s, double lajur) {
		double[] p = l.di(s, lajur);
		return kota.dunia(p[0], p[1], kota.permukaan());
	}

	/** World yaw of a line at arc length s in a town. */
	public static float yaw(AirKota.Hidup kota, Lintasan l, double s) {
		double[] d = l.arah(s);
		return kota.yaw(d[0], d[1]);
	}

	/**
	 * The {@code TemaNet.SPLINE} body of a town: the Perahu Pos and tour lines ({@code kind perahu}) and the lantern
	 * lanes ({@code kind lentera}: each jetty to the lake centre, the Kanal Utama and the north-west canal into the lake),
	 * every corner as world block coordinates x, y, z.
	 */
	public static CompoundTag spline(AirKota.Hidup kota) {
		CompoundTag data = new CompoundTag();
		ListTag list = new ListTag();
		list.add(jalur(kota, "pos", "perahu", POS));
		list.add(jalur(kota, "tur", "perahu", TUR));
		for (int i = 0; i < DERMAGA_LENTERA.length; i++) {
			double[] d = DERMAGA_LENTERA[i];
			double mx = d[0] * 0.35D;
			double mz = d[1] * 0.35D;
			list.add(jalur(kota, "lentera_" + i, "lentera", List.of(t(d[0], d[1]), t(mx, mz), t(mx * 0.2D + 3.0D, mz * 0.2D + 2.0D))));
		}
		list.add(jalur(kota, "lentera_utama", "lentera", List.of(t(0, 126), t(0, 48), t(0, 25), t(0, 8))));
		list.add(jalur(kota, "lentera_barat", "lentera", List.of(t(-48, -48), t(-48, 48), t(0, 48), t(0, 25), t(-6, 10))));
		data.put("jalur", list);
		return data;
	}

	private static CompoundTag jalur(AirKota.Hidup kota, String id, String kind, List<Titik> pts) {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		tag.putString("kind", kind);
		int[] p = new int[pts.size() * 3];
		int y = (int) Math.floor(kota.permukaan() + 0.5D);
		for (int i = 0; i < pts.size(); i++) {
			Vec3 w = kota.dunia(pts.get(i).x, pts.get(i).z, y);
			p[i * 3] = (int) Math.floor(w.x);
			p[i * 3 + 1] = y;
			p[i * 3 + 2] = (int) Math.floor(w.z);
		}
		tag.putIntArray("p", p);
		return tag;
	}

	private JalurKanal() {
	}
}
