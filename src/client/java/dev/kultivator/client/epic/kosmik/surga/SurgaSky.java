package dev.kultivator.client.epic.kosmik.surga;

import dev.kultivator.client.epic.kosmik.StageFrame;
import dev.kultivator.client.epic.render.EpicRenderer;

/**
 * The soft layers of the Heaven stage (DESIGN 32.3.1): the mortal land far below, the great sea of clouds, the local cloud
 * bank under the islands, cloud puffs and the falling peach petals, all in the translucent phase (no depth write), written
 * far to near.
 * <p>
 * The cloud planes are infinite-looking horizontal layers drawn on an <b>eye-centred log-polar grid</b>: ring radii grow
 * geometrically from under the eye outwards, so no quad spans more than a small distance ratio (the diorama's depth stays
 * right, 32.1.3) while the texture stays fixed in the stage (UV from the stage x and z, scrolled by time). The bank fades
 * out at the edge of its ellipse; every layer fades out at its outer radius.
 */
final class SurgaSky {

	/** Cloud bank layers: height, UV metres per repeat, alpha, scroll speed. */
	private static final double[][] BANK = {{-10.0D, 170.0D, 0.82D, 1.0D}, {-17.0D, 130.0D, 0.62D, 1.4D}, {-25.0D, 230.0D, 0.5D, 0.7D}};
	/** Great sea layers. */
	private static final double[][] SEA = {{-460.0D, 520.0D, 0.95D, 0.6D}, {-472.0D, 380.0D, 0.75D, 0.9D}, {-486.0D, 760.0D, 0.6D, 0.45D}};
	private static final int[] BANK_LAYERS = {1, 2, 3};
	private static final int[] BANK_RINGS = {5, 8, 10};
	private static final int[] BANK_SEGMENTS = {10, 16, 20};
	private static final int[] SEA_RINGS = {5, 8, 8};
	private static final int[] SEA_SEGMENTS = {10, 16, 16};
	private static final int[] PETALS = {0, 40, 120};
	private static final int PUFFS = 24;
	private static final int MAX_GRID = (10 + 1) * (20 + 1);

	/** Petals live in a box round the eye (stage metres). */
	private static final double PETAL_BOX = 70.0D;
	private static final double PETAL_HEIGHT = 46.0D;

	// per-vertex scratch of one layer
	private final double[] vx = new double[MAX_GRID];
	private final double[] vz = new double[MAX_GRID];
	private final float[] va = new float[MAX_GRID];
	private final float[] drawn = new float[MAX_GRID * 3];
	private final boolean[] mapped = new boolean[MAX_GRID];
	private final double[] radii = new double[12];
	private final int[] order = new int[3];
	private final double[] layerY = new double[3];
	private final float[] tmp = new float[3];
	private final int[] puffOrder = new int[PUFFS];
	private final double[] puffDist = new double[PUFFS];
	private final double[] puffPos = new double[PUFFS * 3];

	private int landRgb = 0x9A8CC8;
	private long seed;

	/** At a shot start: the land's tint from the home colours, the seeded positions. */
	void beat(int grassRgb, long seed) {
		this.seed = seed;
		// the mortal land seen through heaven's haze: the home grass colour, darkened, half lost in lilac
		int r = (grassRgb >> 16 & 0xFF) * 6 / 10;
		int g = (grassRgb >> 8 & 0xFF) * 6 / 10;
		int b = (grassRgb & 0xFF) * 6 / 10;
		int hr = 0x9A;
		int hg = 0x8C;
		int hb = 0xC8;
		landRgb = (r + hr) / 2 << 16 | (g + hg) / 2 << 8 | (b + hb) / 2;
	}

	// ---------------------------------------------------------------- counting (the plan)

	/** Quads of the land, the sea and the bank (the clouds group) this frame. */
	int countClouds(StageFrame frame, SurgaDraw view, int quality, boolean pack, double time) {
		int n = 0;
		if (quality >= 1) {
			n += plane(frame, view, null, SurgaLayout.LAND_Y, 3, 12, 14000.0D, 1400.0D, 0.0D, 0.0D, 0xFFFFFF, 0.9F, false, false);
		}
		int seaLayers = pack ? 1 : BANK_LAYERS[quality];
		for (int i = 0; i < seaLayers; i++) {
			n += plane(frame, view, null, SEA[i][0], SEA_RINGS[quality], SEA_SEGMENTS[quality], 4200.0D, SEA[i][1], 0.0D, 0.0D, 0xFFFFFF,
					(float) SEA[i][2], false, false);
		}
		int bankLayers = pack ? 1 : BANK_LAYERS[quality];
		for (int i = 0; i < bankLayers; i++) {
			n += plane(frame, view, null, BANK[i][0], BANK_RINGS[quality], BANK_SEGMENTS[quality], 900.0D, BANK[i][1], 0.0D, 0.0D, 0xFFFFFF,
					(float) BANK[i][2], true, false);
		}
		if (quality >= 2 && !pack) {
			n += puffs(frame, view, null, time, false);
		}
		return n;
	}

	/** Petal quads this frame (0 when the beat has none). */
	int countPetals(StageFrame frame, SurgaDraw view, int quality, boolean ringan, boolean pack, double strength, double time) {
		return petals(frame, view, null, petalCount(quality, ringan, pack), strength, time, false);
	}

	private static int petalCount(int quality, boolean ringan, boolean pack) {
		int n = PETALS[Math.max(0, Math.min(2, quality))];
		if (ringan) {
			n /= 2;
		}
		if (pack) {
			n = Math.min(n, 20);
		}
		return n;
	}

	// ---------------------------------------------------------------- drawing

	/** Land, sea layers and bank layers, far to near, then the puffs. */
	void drawClouds(StageFrame frame, SurgaDraw view, int quality, boolean pack, double time) {
		if (quality >= 1) {
			EpicRenderer.Sink land = frame.glow(SurgaLayout.LAND);
			plane(frame, view, land, SurgaLayout.LAND_Y, 3, 12, 14000.0D, 1400.0D, 0.0D, 0.0D, landRgb, 0.9F, false, true);
		}
		EpicRenderer.Sink cloud = frame.glow(SurgaLayout.CLOUD[Math.max(0, Math.min(2, quality))]);
		int seaLayers = pack ? 1 : BANK_LAYERS[quality];
		sortLayers(SEA, seaLayers, view.eyeY());
		for (int k = 0; k < seaLayers; k++) {
			int i = order[k];
			double scroll = time * 0.0012D * SEA[i][3];
			plane(frame, view, cloud, SEA[i][0], SEA_RINGS[quality], SEA_SEGMENTS[quality], 4200.0D, SEA[i][1], scroll + i * 0.37D,
					scroll * 0.6D + i * 0.21D, cloudColor(view.eyeY(), SEA[i][0]), (float) SEA[i][2], false, true);
		}
		int bankLayers = pack ? 1 : BANK_LAYERS[quality];
		sortLayers(BANK, bankLayers, view.eyeY());
		for (int k = 0; k < bankLayers; k++) {
			int i = order[k];
			double scroll = time * 0.002D * BANK[i][3];
			plane(frame, view, cloud, BANK[i][0], BANK_RINGS[quality], BANK_SEGMENTS[quality], 900.0D, BANK[i][1], scroll + i * 0.53D,
					scroll * 0.4D + i * 0.17D, cloudColor(view.eyeY(), BANK[i][0]), (float) BANK[i][2], true, true);
		}
		if (quality >= 2 && !pack) {
			puffs(frame, view, frame.glow(SurgaLayout.PUFF), time, true);
		}
	}

	void drawPetals(StageFrame frame, SurgaDraw view, int quality, boolean ringan, boolean pack, double strength, double time) {
		petals(frame, view, frame.glow(SurgaLayout.PETAL), petalCount(quality, ringan, pack), strength, time, true);
	}

	/** Lit tops seen from above, lilac undersides seen from below. */
	private static int cloudColor(double eyeY, double layerY) {
		return eyeY >= layerY ? 0xFFFFFF : 0xD4CCF0;
	}

	/** Layer indices into {@link #order}, the farthest from the eye's height first. */
	private void sortLayers(double[][] layers, int n, double eyeY) {
		for (int i = 0; i < n; i++) {
			order[i] = i;
			layerY[i] = Math.abs(eyeY - layers[i][0]);
		}
		for (int i = 1; i < n; i++) {
			int o = order[i];
			int j = i - 1;
			while (j >= 0 && layerY[order[j]] < layerY[o]) {
				order[j + 1] = order[j];
				j--;
			}
			order[j + 1] = o;
		}
	}

	/**
	 * One horizontal layer at height y on the eye-centred log-polar grid ({@code rings} × {@code segments}, radius
	 * {@code rMax}); counts (sink null or {@code draw} false) or draws its visible quads, outer rings first. Returns the
	 * quads.
	 */
	private int plane(StageFrame frame, SurgaDraw view, EpicRenderer.Sink sink, double y, int rings, int segments, double rMax, double uvMetres,
					  double du, double dv, int rgb, float alpha, boolean bank, boolean draw) {
		double h = Math.abs(frame.ey - y);
		double rMin = Math.max(3.0D, 0.6D * h);
		int r = rings;
		if (rMin * 1.6D >= rMax) {
			r = Math.min(rings, 2);
			rMin = rMax * 0.5D;
		}
		radii[0] = 0.0D;
		double q = Math.pow(rMax / rMin, 1.0D / Math.max(1, r - 1));
		for (int i = 1; i <= r; i++) {
			radii[i] = rMin * Math.pow(q, i - 1);
		}
		int s = segments;
		int stride = s + 1;
		for (int i = 0; i <= r; i++) {
			double rad = radii[i];
			for (int j = 0; j <= s; j++) {
				double ang = Math.PI * 2.0D * j / s;
				int k = i * stride + j;
				double x = frame.ex + Math.cos(ang) * rad;
				double z = frame.ez + Math.sin(ang) * rad;
				vx[k] = x;
				vz[k] = z;
				float fade = (float) (1.0D - SurgaChoreo.smooth(0.7D * rMax, rMax, rad));
				if (bank) {
					double ex = (x - SurgaLayout.BANK_CX) / SurgaLayout.BANK_RX;
					double ez = (z - SurgaLayout.BANK_CZ) / SurgaLayout.BANK_RZ;
					fade *= (float) (1.0D - SurgaChoreo.smooth(0.5D, 1.0D, ex * ex + ez * ez));
				}
				va[k] = alpha * fade;
				mapped[k] = false;
			}
		}
		int quads = 0;
		for (int i = r - 1; i >= 0; i--) {
			double rIn = radii[i];
			double rOut = radii[i + 1];
			double rMid = (rIn + rOut) * 0.5D;
			double reach = Math.max(rOut - rIn, rOut * Math.PI * 2.0D / s) * 0.6D;
			for (int j = 0; j < s; j++) {
				int k00 = i * stride + j;
				int k01 = k00 + 1;
				int k10 = k00 + stride;
				int k11 = k10 + 1;
				if (va[k00] <= 0.02F && va[k01] <= 0.02F && va[k10] <= 0.02F && va[k11] <= 0.02F) {
					continue;
				}
				double ang = Math.PI * 2.0D * (j + 0.5D) / s;
				if (!view.sees(frame.ex + Math.cos(ang) * rMid, y, frame.ez + Math.sin(ang) * rMid, reach)) {
					continue;
				}
				quads++;
				if (draw && sink != null) {
					corner(frame, sink, k10, y, uvMetres, du, dv, rgb);
					corner(frame, sink, k11, y, uvMetres, du, dv, rgb);
					corner(frame, sink, k01, y, uvMetres, du, dv, rgb);
					corner(frame, sink, k00, y, uvMetres, du, dv, rgb);
				}
			}
		}
		return quads;
	}

	private void corner(StageFrame frame, EpicRenderer.Sink sink, int k, double y, double uvMetres, double du, double dv, int rgb) {
		if (!mapped[k]) {
			frame.map(vx[k], y, vz[k], tmp);
			drawn[k * 3] = tmp[0];
			drawn[k * 3 + 1] = tmp[1];
			drawn[k * 3 + 2] = tmp[2];
			mapped[k] = true;
		}
		float u = (float) (vx[k] / uvMetres + du);
		float v = (float) (vz[k] / uvMetres + dv);
		// keep the UVs small (the texture repeats): whole repeats removed relative to the eye
		u -= (float) Math.floor(frame.ex / uvMetres);
		v -= (float) Math.floor(frame.ez / uvMetres);
		frame.raw(sink, drawn[k * 3], drawn[k * 3 + 1], drawn[k * 3 + 2], u, v, StageFrame.argb(va[k], rgb));
	}

	/** Cloud puffs resting on the bank round the gate and the islands (Maximal), drifting slowly, far to near. */
	private int puffs(StageFrame frame, SurgaDraw view, EpicRenderer.Sink sink, double time, boolean draw) {
		int n = 0;
		for (int i = 0; i < PUFFS; i++) {
			double x = -320.0D + 640.0D * hash(i, 1) + time * 0.02D * (0.5D + hash(i, 2));
			double y = -16.0D + 12.0D * hash(i, 3);
			double z = -220.0D + 760.0D * hash(i, 4);
			// keep the court and the gate clear
			if (Math.abs(x) < 70.0D && z > -40.0D && z < 420.0D) {
				x += x < 0.0D ? -80.0D : 80.0D;
			}
			double half = 18.0D + 24.0D * hash(i, 5);
			puffPos[i * 3] = x;
			puffPos[i * 3 + 1] = y;
			puffPos[i * 3 + 2] = z;
			if (!view.sees(x, y, z, half * 1.5D)) {
				puffDist[i] = -1.0D;
				continue;
			}
			puffDist[i] = view.distance(x, y, z);
			n++;
		}
		if (!draw || sink == null) {
			return n;
		}
		int m = 0;
		for (int i = 0; i < PUFFS; i++) {
			if (puffDist[i] < 0.0D) {
				continue;
			}
			int j = m - 1;
			while (j >= 0 && puffDist[puffOrder[j]] < puffDist[i]) {
				puffOrder[j + 1] = puffOrder[j];
				j--;
			}
			puffOrder[j + 1] = i;
			m++;
		}
		for (int k = 0; k < m; k++) {
			int i = puffOrder[k];
			double half = 18.0D + 24.0D * hash(i, 5);
			float a = (float) (0.85D * (1.0D - SurgaChoreo.smooth(900.0D, 1400.0D, puffDist[i])) * SurgaChoreo.smooth(half * 0.6D, half * 2.0D,
					puffDist[i]));
			frame.billboard(sink, puffPos[i * 3], puffPos[i * 3 + 1], puffPos[i * 3 + 2], half, (float) (hash(i, 6) * 6.283D),
					StageFrame.argb(a, 0xFFFFFF));
		}
		return n;
	}

	/**
	 * Peach-blossom petals on seeded parametric paths in a box that travels with the eye (each petal wraps round it), so
	 * they always drift past the camera; four tumbling frames; faded at the box edges and right at the eye.
	 */
	private int petals(StageFrame frame, SurgaDraw view, EpicRenderer.Sink sink, int count, double strength, double time, boolean draw) {
		if (count <= 0 || strength <= 0.02D) {
			return 0;
		}
		int n = 0;
		double halfBox = PETAL_BOX * 0.5D;
		double halfH = PETAL_HEIGHT * 0.5D;
		for (int i = 0; i < count; i++) {
			double speed = 0.07D + 0.08D * hash(i, 11);
			double sway = 1.5D + 2.0D * hash(i, 12);
			double phase = hash(i, 13) * 6.283D;
			double bx = hash(i, 14) * PETAL_BOX + Math.sin(time * 0.05D + phase) * sway + time * 0.03D;
			double by = hash(i, 15) * PETAL_HEIGHT - time * speed;
			double bz = hash(i, 16) * PETAL_BOX + Math.cos(time * 0.04D + phase) * sway * 0.6D;
			double ox = wrap(bx - frame.ex, PETAL_BOX) - halfBox;
			double oy = wrap(by - frame.ey, PETAL_HEIGHT) - halfH;
			double oz = wrap(bz - frame.ez, PETAL_BOX) - halfBox;
			double x = frame.ex + ox;
			double y = frame.ey + oy;
			double z = frame.ez + oz;
			double edge = Math.max(Math.max(Math.abs(ox) / halfBox, Math.abs(oz) / halfBox), Math.abs(oy) / halfH);
			double d = Math.sqrt(ox * ox + oy * oy + oz * oz);
			double a = strength * (1.0D - SurgaChoreo.smooth(0.7D, 1.0D, edge)) * SurgaChoreo.smooth(1.2D, 3.0D, d);
			if (a <= 0.02D || !view.sees(x, y, z, 0.6D)) {
				continue;
			}
			n++;
			if (draw && sink != null) {
				int fr = (int) Math.floor(time * (0.12D + 0.1D * hash(i, 17)) + hash(i, 18) * 4.0D) & 3;
				float u0 = fr * 0.25F;
				double roll = phase + time * 0.03D * (hash(i, 19) - 0.5D);
				view.sprite(frame, sink, x, y, z, 0.32D + 0.12D * hash(i, 20), roll, u0, 0.0F, u0 + 0.25F, 1.0F, StageFrame.argb((float) a, 0xFFFFFF));
			}
		}
		return n;
	}

	private static double wrap(double v, double size) {
		double m = v % size;
		return m < 0.0D ? m + size : m;
	}

	/** A seeded hash in [0, 1). */
	private double hash(int i, int salt) {
		long h = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}
}
