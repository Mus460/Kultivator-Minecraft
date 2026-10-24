package dev.kultivator.client.dunia.tempat.tema.fx;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The canal and sea lanes of the themed towns as the client knows them ({@code TemaNet.SPLINE}, sent once per town on
 * entering): per site a list of polylines {@code {id, kind, p}} with {@code kind} {@code perahu}, {@code kapal} or
 * {@code lentera}. A lane is sampled by arc length with {@link Jalur#titik}, so drifting lanterns move at an even speed
 * whatever the spacing of the NBT nodes. Also remembers one centre point per site (from any themed payload carrying
 * {@code x}/{@code z}), used by the HUD effects to know whether the player stands in that town.
 */
public final class TemaJalur {

	/** One lane: the nodes (block centres, water surface height) and the running arc lengths. */
	public static final class Jalur {
		final String id;
		final String kind;
		final double[] xs;
		final double[] ys;
		final double[] zs;
		final double[] s;
		final double panjang;

		Jalur(String id, String kind, int[] p) {
			this.id = id;
			this.kind = kind;
			int n = p.length / 3;
			xs = new double[n];
			ys = new double[n];
			zs = new double[n];
			s = new double[n];
			double acc = 0.0D;
			for (int i = 0; i < n; i++) {
				xs[i] = p[i * 3] + 0.5D;
				ys[i] = p[i * 3 + 1];
				zs[i] = p[i * 3 + 2] + 0.5D;
				if (i > 0) {
					acc += Math.sqrt(Mth.square(xs[i] - xs[i - 1]) + Mth.square(ys[i] - ys[i - 1]) + Mth.square(zs[i] - zs[i - 1]));
				}
				s[i] = acc;
			}
			panjang = acc;
		}

		public double panjang() {
			return panjang;
		}

		public int simpul() {
			return xs.length;
		}

		/** The point at arc length {@code jarak} (clamped to the lane) into {@code out} {x, y, z, heading radians}. */
		public double[] titik(double jarak, double[] out) {
			int n = xs.length;
			if (n == 0) {
				out[0] = out[1] = out[2] = out[3] = 0.0D;
				return out;
			}
			if (n == 1 || jarak <= 0.0D) {
				out[0] = xs[0];
				out[1] = ys[0];
				out[2] = zs[0];
				out[3] = n > 1 ? Math.atan2(zs[1] - zs[0], xs[1] - xs[0]) : 0.0D;
				return out;
			}
			if (jarak >= panjang) {
				out[0] = xs[n - 1];
				out[1] = ys[n - 1];
				out[2] = zs[n - 1];
				out[3] = Math.atan2(zs[n - 1] - zs[n - 2], xs[n - 1] - xs[n - 2]);
				return out;
			}
			int lo = 0;
			int hi = n - 1;
			while (hi - lo > 1) {
				int mid = (lo + hi) >>> 1;
				if (s[mid] <= jarak) {
					lo = mid;
				} else {
					hi = mid;
				}
			}
			double seg = s[hi] - s[lo];
			double k = seg <= 1.0E-6D ? 0.0D : (jarak - s[lo]) / seg;
			out[0] = Mth.lerp(k, xs[lo], xs[hi]);
			out[1] = Mth.lerp(k, ys[lo], ys[hi]);
			out[2] = Mth.lerp(k, zs[lo], zs[hi]);
			out[3] = Math.atan2(zs[hi] - zs[lo], xs[hi] - xs[lo]);
			return out;
		}

		/** Node {@code i} into {@code out} {x, y, z}. */
		void node(int i, double[] out) {
			out[0] = xs[i];
			out[1] = ys[i];
			out[2] = zs[i];
		}

		public String kind() {
			return kind;
		}

		/** Arc length of the node nearest to (x, z), for lanterns released at a point. */
		double terdekat(double x, double z) {
			double best = Double.MAX_VALUE;
			double at = 0.0D;
			for (int i = 0; i < xs.length; i++) {
				double d = Mth.square(xs[i] - x) + Mth.square(zs[i] - z);
				if (d < best) {
					best = d;
					at = s[i];
				}
			}
			return at;
		}

		double jarakKe2(double x, double z) {
			double best = Double.MAX_VALUE;
			for (int i = 0; i < xs.length; i++) {
				best = Math.min(best, Mth.square(xs[i] - x) + Mth.square(zs[i] - z));
			}
			return best;
		}
	}

	private static final Map<Long, List<Jalur>> JALUR = new HashMap<>();
	private static final Map<Long, double[]> PUSAT = new HashMap<>();

	private TemaJalur() {
	}

	/** {@code TemaNet.SPLINE} for site {@code situsId}: replaces that site's lanes. */
	static void terima(long situsId, CompoundTag data) {
		ListTag list = data.getListOrEmpty("jalur");
		List<Jalur> lanes = new ArrayList<>();
		for (int i = 0; i < list.size() && lanes.size() < 64; i++) {
			CompoundTag c = list.getCompoundOrEmpty(i);
			int[] p = c.getIntArray("p").orElse(new int[0]);
			if (p.length >= 6) {
				lanes.add(new Jalur(c.getStringOr("id", "j" + i), c.getStringOr("kind", "perahu"), p));
				if (!PUSAT.containsKey(situsId)) {
					PUSAT.put(situsId, new double[] {p[0] + 0.5D, p[2] + 0.5D});
				}
			}
		}
		JALUR.put(situsId, lanes);
	}

	/** Remembers the centre of site {@code situsId} when {@code data} carries {@code x} and {@code z}. */
	static void ingatPusat(long situsId, CompoundTag data) {
		if (data.contains("x") && data.contains("z")) {
			PUSAT.put(situsId, new double[] {data.getDoubleOr("x", 0.0D), data.getDoubleOr("z", 0.0D)});
		}
	}

	/** The remembered centre {x, z} of a site, or null. */
	static double @Nullable [] pusat(long situsId) {
		return PUSAT.get(situsId);
	}

	/** The lanes of {@code situsId} of kind {@code kind}, falling back to {@code cadangan} when there are none. */
	static List<Jalur> jalur(long situsId, String kind, @Nullable String cadangan) {
		List<Jalur> all = JALUR.get(situsId);
		if (all == null || all.isEmpty()) {
			return List.of();
		}
		List<Jalur> out = new ArrayList<>();
		for (Jalur j : all) {
			if (j.kind.equals(kind)) {
				out.add(j);
			}
		}
		if (out.isEmpty() && cadangan != null) {
			for (Jalur j : all) {
				if (j.kind.equals(cadangan)) {
					out.add(j);
				}
			}
		}
		return out;
	}

	/** The lane of any site nearest to (x, z) within {@code r} blocks, or null (lanterns released by a person). */
	static @Nullable Jalur terdekat(double x, double z, double r) {
		Jalur best = null;
		double bestD = r * r;
		for (List<Jalur> lanes : JALUR.values()) {
			for (Jalur j : lanes) {
				double d = j.jarakKe2(x, z);
				if (d < bestD) {
					bestD = d;
					best = j;
				}
			}
		}
		return best;
	}

	/** The lane of kind {@code kind} of any site nearest to (x, z) within {@code r} blocks, or null. */
	static @Nullable Jalur terdekatJenis(double x, double z, double r, String kind) {
		Jalur best = null;
		double bestD = r * r;
		for (List<Jalur> lanes : JALUR.values()) {
			for (Jalur j : lanes) {
				if (!j.kind.equals(kind)) {
					continue;
				}
				double d = j.jarakKe2(x, z);
				if (d < bestD) {
					bestD = d;
					best = j;
				}
			}
		}
		return best;
	}

	/** A test lane made on the client (the {@code /temafx} preview). */
	static void pasangUji(long situsId, List<Vec3> nodes, String kind) {
		int[] p = new int[nodes.size() * 3];
		for (int i = 0; i < nodes.size(); i++) {
			Vec3 v = nodes.get(i);
			p[i * 3] = Mth.floor(v.x);
			p[i * 3 + 1] = Mth.floor(v.y);
			p[i * 3 + 2] = Mth.floor(v.z);
		}
		JALUR.put(situsId, new ArrayList<>(List.of(new Jalur("uji", kind, p))));
	}

	static void lupa(long situsId) {
		JALUR.remove(situsId);
	}

	static void bersihkan() {
		JALUR.clear();
		PUSAT.clear();
	}
}
