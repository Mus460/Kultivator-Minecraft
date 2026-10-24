package dev.kultivator.client.dunia.tempat.tema.fx;

import dev.kultivator.dunia.tempat.tema.gunung.GunungTata;
import dev.kultivator.dunia.tempat.tema.gunung.KatrolEntity;
import dev.kultivator.dunia.tempat.tema.gunung.KeretaGantungEntity;
import dev.kultivator.dunia.tempat.tema.gunung.RodaKatrolEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The ropes of the Kota Gunung lifts (DESIGN 38.T.6, 38.T.9.1, 38.T.9.4 "tali kereta gantung 1 batch"), all in one
 * lit cutout batch of three-strand hemp ribbons ({@code tali}, turned about their own axis toward the camera):
 * <ul>
 * <li><b>Kereta gantung</b>: a jig-back tramway, two track ropes between the big bull wheels ({@link RodaKatrolEntity}
 * {@code VARIAN_BESAR}, 9 blocks across, lying flat) at the two stations, one strand on each side of the wheel
 * ({@link #SISI} blocks off the span line). Each strand sags as a catenary (parabola, 2.5 % of the span), and where a
 * cabin ({@link KeretaGantungEntity}) hangs from it the rope is pulled down into two curves meeting at its trolley, so the
 * rope visibly carries the cabin. Lanes of kind {@code kabel} from {@code TemaNet.SPLINE} (station, middle tower, station)
 * replace the wheel pairing when the server sends them; a cabin with neither shows a short rope stub along its heading.
 * The server's own lane {@code tali_gantung} of kind {@code tali} ({@code KeretaGantung.kirimTali}: 25 samples of the whole
 * station - tower - station path at rope height, already sagging) wins over both: the full span is drawn from it, a
 * track rope through the trolley groove and a thinner haul rope beside and below it, each running onto the rim of the
 * bull wheel at its station, so the rope is visible from end to end even where no cabin hangs.</li>
 * <li><b>Katrol</b>: the four ropes from the corners of each basket ({@link KatrolEntity}) up to the small pulley
 * ({@code VARIAN_KECIL}) above it, else up to where its height value {@code nilai} says the top is (30 blocks).</li>
 * </ul>
 * Segments get longer with distance (1.5 / 4 / 8 blocks), nothing past 192 blocks; lists are rescanned every 20 ticks.
 */
public final class TaliGantungRenderer {

	/** Height of the cabin trolley's rope groove above the cabin entity (KeretaGantungRenderer, 38TV-M). */
	public static final float TROLI = 3.25F;
	/** Height of the bull wheel's rope groove above the wheel entity. */
	public static final float ALUR_RODA = 0.5F;
	/** Half the bull wheel: each strand runs this far beside the span line. */
	public static final float SISI = 4.5F;
	/** Height of a basket's rope eyes above the basket entity (KatrolRenderer, 38TV-M). */
	public static final float MATA_KATROL = 1.2F;
	static final double JANGKAU = 160.0D;
	static final float LEBAR = 0.12F;

	private static final List<KeretaGantungEntity> KABIN = new ArrayList<>();
	private static final List<KatrolEntity> KATROL = new ArrayList<>();
	private static final List<RodaKatrolEntity> RODA = new ArrayList<>();
	private static final double[] TITIK = new double[4];
	private static final double[] TITIK2 = new double[4];
	/** The nearest full-span lift rope (kind {@code tali}) within reach, rescanned with the entities. */
	private static TemaJalur.Jalur taliLajur;
	/** The server samples the rope this far above the cabin path; the trolley groove sits at {@link #TROLI}. */
	private static final double TURUN_KE_ALUR = GunungTata.TALI_DI_ATAS - TROLI;
	/** Haul rope: this far beside and below the track rope. */
	private static final double TARIK_SISI = 0.55D;
	private static final double TARIK_TURUN = 0.45D;

	private TaliGantungRenderer() {
	}

	static boolean aktif() {
		return !KABIN.isEmpty() || !KATROL.isEmpty() || uji != null || taliLajur != null;
	}

	static void bersihkan() {
		KABIN.clear();
		KATROL.clear();
		RODA.clear();
		uji = null;
		taliLajur = null;
	}

	private static double[] uji;

	/** Preview: a 48-block rope pair from (x, y, z) along yaw (degrees), with no entity. */
	static void uji(double x, double y, double z, float yawDeg) {
		float a = yawDeg * Mth.DEG_TO_RAD;
		uji = new double[] {x, y, z, x - Mth.sin(a) * 48.0D, y + 10.0D, z + Mth.cos(a) * 48.0D};
	}

	static void pindai(ClientLevel level, Vec3 at) {
		KABIN.clear();
		KATROL.clear();
		RODA.clear();
		AABB box = new AABB(at, at).inflate(JANGKAU, 96.0D, JANGKAU);
		taliLajur = TemaJalur.terdekatJenis(at.x, at.z, JANGKAU, "tali");
		for (KeretaGantungEntity e : level.getEntitiesOfClass(KeretaGantungEntity.class, box)) {
			if (KABIN.size() < 4) {
				KABIN.add(e);
			}
		}
		for (KatrolEntity e : level.getEntitiesOfClass(KatrolEntity.class, box)) {
			if (KATROL.size() < 4) {
				KATROL.add(e);
			}
		}
		if (!KABIN.isEmpty() || !KATROL.isEmpty() || taliLajur != null) {
			for (RodaKatrolEntity e : level.getEntitiesOfClass(RodaKatrolEntity.class, box)) {
				if (RODA.size() < 8) {
					RODA.add(e);
				}
			}
		}
	}

	// ================================================================ drawing

	static void gambar() {
		float pt = Bingkai.pt;
		if (uji != null) {
			tali(uji[0], uji[1], uji[2], uji[3], uji[4], uji[5], 0.025F, Double.NaN, 0.0D, 0.0D, 0.0D);
			double ox = 0.6D;
			tali(uji[0] + ox, uji[1], uji[2] + ox, uji[3] + ox, uji[4], uji[5] + ox, 0.025F, Double.NaN, 0.0D, 0.0D, 0.0D);
		}
		kabel(pt);
		katrol(pt);
	}

	private static void kabel(float pt) {
		List<RodaKatrolEntity> besar = new ArrayList<>(4);
		for (RodaKatrolEntity r : RODA) {
			if (!r.isRemoved() && r.varian() == RodaKatrolEntity.VARIAN_BESAR) {
				besar.add(r);
			}
		}
		// 0) the server's full-span rope: drawn whole, and every cabin on it needs nothing else
		if (taliLajur != null && taliLajur.simpul() >= 2) {
			lajurTali(taliLajur, besar, pt);
		}
		if (KABIN.isEmpty()) {
			return;
		}
		boolean[] dipakai = new boolean[KABIN.size()];
		if (taliLajur != null) {
			for (int ci = 0; ci < KABIN.size(); ci++) {
				Vec3 p = KABIN.get(ci).getPosition(pt);
				dipakai[ci] = KABIN.get(ci).isRemoved() || taliLajur.jarakKe2(p.x, p.z) < 36.0D;
			}
			besar.clear();
		}
		// 1) lanes of kind "kabel" from the server
		for (int ci = 0; ci < KABIN.size(); ci++) {
			KeretaGantungEntity cab = KABIN.get(ci);
			if (cab.isRemoved()) {
				dipakai[ci] = true;
				continue;
			}
			Vec3 p = cab.getPosition(pt);
			TemaJalur.Jalur lane = TemaJalur.terdekatJenis(p.x, p.z, 24.0D, "kabel");
			if (lane != null) {
				dipakai[ci] = true;
				lajurKabel(lane, p);
			}
		}
		// 2) the two bull wheels the cabin hangs between
		if (besar.size() >= 2) {
			for (int i = 0; i < besar.size(); i++) {
				for (int j = i + 1; j < besar.size(); j++) {
					Vec3 a = besar.get(i).getPosition(pt);
					Vec3 b = besar.get(j).getPosition(pt);
					bentang(a, b, pt, dipakai);
				}
			}
		}
		// 3) a stub along the cabin's heading
		for (int ci = 0; ci < KABIN.size(); ci++) {
			if (dipakai[ci]) {
				continue;
			}
			KeretaGantungEntity cab = KABIN.get(ci);
			Vec3 p = cab.getPosition(pt);
			float yaw = cab.getYRot(pt) * Mth.DEG_TO_RAD;
			double fx = -Mth.sin(yaw);
			double fz = Mth.cos(yaw);
			double y = p.y + TROLI;
			tali(p.x - fx * 20.0D, y + 1.2D, p.z - fz * 20.0D, p.x, y, p.z, 0.01F, Double.NaN, 0, 0, 0);
			tali(p.x, y, p.z, p.x + fx * 20.0D, y + 1.2D, p.z + fz * 20.0D, 0.01F, Double.NaN, 0, 0, 0);
		}
	}

	/** Both strands of the span between wheels a and b, each pulled down by any cabin hanging on it. */
	private static void bentang(Vec3 a, Vec3 b, float pt, boolean[] dipakai) {
		double sx = b.x - a.x;
		double sz = b.z - a.z;
		double span = Math.sqrt(sx * sx + sz * sz);
		if (span < 12.0D || span > 400.0D) {
			return;
		}
		double ux = sx / span;
		double uz = sz / span;
		// side vector (right of a -> b)
		double rx = -uz;
		double rz = ux;
		for (int side = -1; side <= 1; side += 2) {
			double ax = a.x + rx * SISI * side;
			double az = a.z + rz * SISI * side;
			double bx = b.x + rx * SISI * side;
			double bz = b.z + rz * SISI * side;
			double ay = a.y + ALUR_RODA;
			double by = b.y + ALUR_RODA;
			// a cabin within 3 blocks of this strand, between the wheels
			KeretaGantungEntity load = null;
			double loadK = 0.0D;
			for (int ci = 0; ci < KABIN.size(); ci++) {
				KeretaGantungEntity cab = KABIN.get(ci);
				if (cab.isRemoved()) {
					continue;
				}
				Vec3 p = cab.getPosition(pt);
				double k = ((p.x - ax) * ux + (p.z - az) * uz) / span;
				double off = Math.abs((p.x - ax) * rx + (p.z - az) * rz);
				if (k > -0.02D && k < 1.02D && off < 3.0D) {
					load = cab;
					loadK = k;
					dipakai[ci] = true;
					break;
				}
			}
			if (load == null) {
				tali(ax, ay, az, bx, by, bz, 0.025F, Double.NaN, 0, 0, 0);
			} else {
				Vec3 p = load.getPosition(pt);
				tali(ax, ay, az, bx, by, bz, 0.012F, Mth.clamp(loadK, 0.0D, 1.0D), p.x, p.y + TROLI, p.z);
			}
		}
	}

	/**
	 * The full lift rope from a {@code tali} lane: nodes smoothed [1 2 1] (they arrive floored to whole blocks), lowered
	 * into the trolley groove, drawn as straight runs (the samples already carry the sag). The track rope runs over the
	 * nodes; the haul rope runs beside and below it (only within 96 blocks). At each end the ropes run onto the rim of a
	 * bull wheel within 12 blocks: the track rope to the near rim point, the haul rope to the side point.
	 */
	private static void lajurTali(TemaJalur.Jalur lane, List<RodaKatrolEntity> besar, float pt) {
		int n = lane.simpul();
		double[] xs = new double[n];
		double[] ys = new double[n];
		double[] zs = new double[n];
		for (int i = 0; i < n; i++) {
			lane.node(i, TITIK);
			xs[i] = TITIK[0];
			ys[i] = TITIK[1] + 0.5D - TURUN_KE_ALUR;
			zs[i] = TITIK[2];
		}
		double[] hx = xs.clone();
		double[] hy = ys.clone();
		double[] hz = zs.clone();
		for (int i = 1; i + 1 < n; i++) {
			hx[i] = (xs[i - 1] + 2.0D * xs[i] + xs[i + 1]) * 0.25D;
			hy[i] = (ys[i - 1] + 2.0D * ys[i] + ys[i + 1]) * 0.25D;
			hz[i] = (zs[i - 1] + 2.0D * zs[i] + zs[i + 1]) * 0.25D;
		}
		boolean dekat = Bingkai.jarak2(hx[n / 2], hy[n / 2], hz[n / 2]) < Mth.square(96.0D + lane.panjang() * 0.5D);
		for (int i = 0; i + 1 < n; i++) {
			tali(hx[i], hy[i], hz[i], hx[i + 1], hy[i + 1], hz[i + 1], 0.0F, Double.NaN, 0, 0, 0);
			if (dekat) {
				double dx = hx[i + 1] - hx[i];
				double dz = hz[i + 1] - hz[i];
				double len = Math.sqrt(dx * dx + dz * dz);
				if (len > 1.0E-4D) {
					double rx = -dz / len * TARIK_SISI;
					double rz = dx / len * TARIK_SISI;
					tali(hx[i] + rx, hy[i] - TARIK_TURUN, hz[i] + rz, hx[i + 1] + rx, hy[i + 1] - TARIK_TURUN, hz[i + 1] + rz, 0.0F, Double.NaN, 0, 0, 0);
				}
			}
		}
		ujungRoda(hx[0], hy[0], hz[0], hx[1], hz[1], besar, pt, dekat);
		ujungRoda(hx[n - 1], hy[n - 1], hz[n - 1], hx[n - 2], hz[n - 2], besar, pt, dekat);
	}

	/** Runs the rope end (ex, ey, ez), coming from (fx, fz), onto the rim of the nearest bull wheel within 12 blocks. */
	private static void ujungRoda(double ex, double ey, double ez, double fx, double fz, List<RodaKatrolEntity> besar, float pt, boolean dekat) {
		RodaKatrolEntity roda = null;
		double best = 144.0D;
		for (RodaKatrolEntity r : besar) {
			Vec3 w = r.getPosition(pt);
			double d = Mth.square(w.x - ex) + Mth.square(w.z - ez);
			if (d < best) {
				best = d;
				roda = r;
			}
		}
		if (roda == null) {
			return;
		}
		Vec3 w = roda.getPosition(pt);
		double ux = fx - w.x;
		double uz = fz - w.z;
		double len = Math.sqrt(ux * ux + uz * uz);
		if (len < 1.0E-4D) {
			return;
		}
		ux /= len;
		uz /= len;
		double wy = w.y + ALUR_RODA;
		// track rope onto the near rim; haul rope around to the side of the wheel
		tali(ex, ey, ez, w.x + ux * SISI, wy, w.z + uz * SISI, 0.0F, Double.NaN, 0, 0, 0);
		if (dekat) {
			double rx = -uz;
			double rz = ux;
			tali(ex + rx * TARIK_SISI, ey - TARIK_TURUN, ez + rz * TARIK_SISI, w.x + rx * SISI, wy, w.z + rz * SISI, 0.0F, Double.NaN, 0, 0, 0);
		}
	}

	/** A server lane of kind kabel: catenaries between its nodes, the one under the cabin pulled to the trolley. */
	private static void lajurKabel(TemaJalur.Jalur lane, Vec3 cabin) {
		int n = lane.simpul();
		for (int i = 0; i + 1 < n; i++) {
			lane.node(i, TITIK);
			lane.node(i + 1, TITIK2);
			double sx = TITIK2[0] - TITIK[0];
			double sz = TITIK2[2] - TITIK[2];
			double len2 = sx * sx + sz * sz;
			double k = len2 < 1.0E-6D ? -1.0D : ((cabin.x - TITIK[0]) * sx + (cabin.z - TITIK[2]) * sz) / len2;
			if (k >= 0.0D && k <= 1.0D) {
				tali(TITIK[0], TITIK[1], TITIK[2], TITIK2[0], TITIK2[1], TITIK2[2], 0.012F, k, cabin.x, cabin.y + TROLI, cabin.z);
			} else {
				tali(TITIK[0], TITIK[1], TITIK[2], TITIK2[0], TITIK2[1], TITIK2[2], 0.025F, Double.NaN, 0, 0, 0);
			}
		}
	}

	private static void katrol(float pt) {
		for (KatrolEntity k : KATROL) {
			if (k.isRemoved()) {
				continue;
			}
			Vec3 p = k.getPosition(pt);
			double topX = p.x;
			double topY = p.y + MATA_KATROL + (1.0F - Mth.clamp(k.nilai(), 0.0F, 1.0F)) * 30.0F + 2.0D;
			double topZ = p.z;
			for (RodaKatrolEntity r : RODA) {
				if (r.isRemoved() || r.varian() != RodaKatrolEntity.VARIAN_KECIL) {
					continue;
				}
				Vec3 w = r.getPosition(pt);
				if (w.y > p.y + 2.0D && Mth.square(w.x - p.x) + Mth.square(w.z - p.z) < 9.0D) {
					topX = w.x;
					topY = w.y;
					topZ = w.z;
					break;
				}
			}
			float yaw = k.getYRot(pt) * Mth.DEG_TO_RAD;
			float cs = Mth.cos(yaw);
			float sn = Mth.sin(yaw);
			for (int c = 0; c < 4; c++) {
				float lx = (c & 1) == 0 ? -0.6F : 0.6F;
				float lz = (c & 2) == 0 ? -0.6F : 0.6F;
				double ex = p.x + lx * cs - lz * sn;
				double ez = p.z + lx * sn + lz * cs;
				// the four ropes gather into one just under the pulley
				tali(ex, p.y + MATA_KATROL, ez, topX + lx * 0.08D, topY - 0.4D, topZ + lz * 0.08D, 0.0F, Double.NaN, 0, 0, 0);
			}
		}
	}

	/**
	 * One rope from a to b sagging by {@code sag} × length; when {@code beban} (0..1 along the rope) is a number the rope
	 * is drawn as two curves meeting at the load point (lx, ly, lz).
	 */
	private static void tali(double ax, double ay, double az, double bx, double by, double bz, float sag, double beban, double lx, double ly,
			double lz) {
		if (!Double.isNaN(beban)) {
			kurva(ax, ay, az, lx, ly, lz, sag);
			kurva(lx, ly, lz, bx, by, bz, sag);
			return;
		}
		kurva(ax, ay, az, bx, by, bz, sag);
	}

	private static void kurva(double ax, double ay, double az, double bx, double by, double bz, float sag) {
		double mx = (ax + bx) * 0.5D;
		double my = (ay + by) * 0.5D;
		double mz = (az + bz) * 0.5D;
		double d2 = Bingkai.jarak2(mx, my, mz);
		double len = Math.sqrt(Mth.square(bx - ax) + Mth.square(by - ay) + Mth.square(bz - az));
		if (len < 0.05D || d2 > Mth.square(192.0D + len * 0.5D)) {
			return;
		}
		double near = Math.sqrt(d2) - len * 0.5D;
		float segLen = near < 48.0D ? 1.5F : near < 128.0D ? 4.0F : 8.0F;
		int segs = Mth.clamp((int) Math.ceil(len / segLen), 1, 96);
		if (!TemaFx.muat(segs)) {
			return;
		}
		float[] uv = FxTekstur.sel(FxTekstur.TALI);
		int light = Bingkai.cahaya(mx, my, mz);
		int col = 0xFFFFFFFF;
		double drop = len * sag;
		float w = near < 16.0D ? LEBAR : LEBAR * 1.4F;
		double px = ax - Bingkai.camX;
		double py = ay - Bingkai.camY;
		double pz = az - Bingkai.camZ;
		for (int i = 1; i <= segs; i++) {
			double t = i / (double) segs;
			double qx = Mth.lerp(t, ax, bx) - Bingkai.camX;
			double qy = Mth.lerp(t, ay, by) - 4.0D * drop * t * (1.0D - t) - Bingkai.camY;
			double qz = Mth.lerp(t, az, bz) - Bingkai.camZ;
			// each segment shows the whole cell (four twists of the three strands; the cell tiles along v)
			TemaFx.fxCut.pita((float) px, (float) py, (float) pz, (float) qx, (float) qy, (float) qz, w, w, uv[0], uv[1], uv[2], uv[3], col, col, light);
			px = qx;
			py = qy;
			pz = qz;
		}
	}
}
