package dev.kultivator.client.sekte.langit;

import net.minecraft.util.Mth;

/**
 * Laut Awan, the sea of clouds below a sky island (DESIGN 37.6.2): up to three horizontal layers at
 * {@code yAwan = clamp(skyFloorY - 10, 150, 205)}, {@code yAwan - 7} and {@code yAwan - 15}, each on a camera-centred
 * <b>log-polar grid</b> (the {@code SurgaSky} bank pattern: ring radii grow geometrically outwards, so near quads are small
 * and far quads large, and the whole plane costs only rings × segments quads). The texture stays fixed to the world (UV
 * from world x and z, never from the camera), drifting 0.6 blocks/s down the day's wind, which turns with every world day
 * (seeded by the hall). Alpha 0.85 / 0.62 / 0.50 per layer, faded over the outer rings (96-384 blocks); from below only at
 * Normal+ and at half alpha. The colour follows the vanilla sky colour (white noon, ink-blue night), warmed gold at dawn
 * and dusk ({@link CahayaFajar#tint}), darkened 35% in a storm.
 * <p>
 * Minimal 1 × 4 × 8 = 32 quads, Normal 2 × 6 × 12 = 144, Maximal 3 × 8 × 16 = 384 (37.6.7). The vanilla clouds at
 * 192.33 are untouched; they drift through the sea and through the island's belly.
 */
final class LautAwanRenderer {

	private static final double[] OFFSET = {0.0D, -7.0D, -15.0D};
	private static final double[] UV_METRES = {170.0D, 130.0D, 230.0D};
	private static final float[] ALPHA = {0.85F, 0.62F, 0.50F};
	private static final double[] SPEED = {1.0D, 1.4D, 0.7D};
	private static final double R_MAX = 384.0D;
	private static final double FADE_FROM = 96.0D;

	private static final int MAX_RINGS = 8;
	private static final int MAX_SEGMENTS = 16;
	private static final int MAX_GRID = (MAX_RINGS + 1) * (MAX_SEGMENTS + 1);
	private static final float[] VX = new float[MAX_GRID];
	private static final float[] VZ = new float[MAX_GRID];
	private static final float[] VU = new float[MAX_GRID];
	private static final float[] VV = new float[MAX_GRID];
	private static final int[] VC = new int[MAX_GRID];
	private static final double[] RADII = new double[MAX_RINGS + 1];
	private static final int[] ORDER = new int[3];

	private static final AwanSink SEA_256 = new AwanSink(AwanSink.CLOUD_SEA_256, false);
	private static final AwanSink SEA_512 = new AwanSink(AwanSink.CLOUD_SEA_512, false);

	static AwanSink sink(int tier) {
		return tier >= 2 ? SEA_512 : SEA_256;
	}

	static void reset() {
		SEA_256.reset();
		SEA_512.reset();
	}

	/** Draws the sea of {@code f.hall}. */
	static void draw(LangitFrame f) {
		if (!LangitClientConfig.on(LangitClientConfig.Toggle.LAUT_AWAN) || f.tier < 0) {
			return;
		}
		LangitSkyState.Hall hall = f.hall;
		double yAwan = hall.cloudY();
		boolean below = f.camY < yAwan;
		if (below && f.tier < 1) {
			return;
		}
		int tier = f.tier;
		int layers = LangitBudget.SEA_LAYERS[tier];
		if (f.pack) {
			layers = Math.min(layers, 2);
		}
		int rings = LangitBudget.SEA_RINGS[tier];
		int segments = LangitBudget.SEA_SEGMENTS[tier];
		AwanSink sink = sink(tier);
		// the day's wind: one direction per world day, seeded by the hall
		long h = hall.seed * 0x9E3779B97F4A7C15L + f.day * 0xBF58476D1CE4E5B9L;
		h ^= h >>> 31;
		double windAngle = (h & 0xFFFF) / 65536.0D * Math.PI * 2.0D;
		double windX = Math.cos(windAngle);
		double windZ = Math.sin(windAngle);
		int colour = colour(f, hall);
		// far layers first (from the camera's height)
		sort(yAwan, layers, f.camY);
		for (int k = 0; k < layers; k++) {
			int i = ORDER[k];
			double y = yAwan + OFFSET[i];
			int quads = rings * segments;
			if (!LangitBudget.take(quads)) {
				return;
			}
			float alpha = ALPHA[i] * (below ? 0.5F : 1.0F);
			// the top layer catches the dawn gold, lower layers a little less
			int rgb = i == 0 ? CahayaFajar.tint(f, colour) : AwanSink.mix(colour, CahayaFajar.tint(f, colour), 0.4F);
			if (below) {
				rgb = AwanSink.scale(rgb, 0.8F);
			}
			double drift = f.seconds * 0.6D * SPEED[i];
			plane(f, sink, y, rings, segments, UV_METRES[i], drift * windX, drift * windZ, i * 0.37D, rgb, alpha);
		}
	}

	/** The cloud colour: the sky's (lifted toward white by day, ink-blue by night), darkened 35% in a storm. */
	private static int colour(LangitFrame f, LangitSkyState.Hall hall) {
		int day = AwanSink.mix(f.skyRgb, 0xF6F8FF, 0.78F);
		int night = AwanSink.mix(f.skyRgb, 0x3A4468, 0.6F);
		int c = AwanSink.mix(night, day, f.daylight);
		float storm = Math.max(f.rain, f.thunder) * 0.35F;
		return AwanSink.scale(c, 1.0F - storm);
	}

	private static void sort(double yAwan, int n, double camY) {
		for (int i = 0; i < n; i++) {
			ORDER[i] = i;
		}
		for (int i = 1; i < n; i++) {
			int o = ORDER[i];
			double d = Math.abs(camY - (yAwan + OFFSET[o]));
			int j = i - 1;
			while (j >= 0 && Math.abs(camY - (yAwan + OFFSET[ORDER[j]])) < d) {
				ORDER[j + 1] = ORDER[j];
				j--;
			}
			ORDER[j + 1] = o;
		}
	}

	/** One layer: the log-polar grid around the camera at world height y, outer rings first. */
	private static void plane(LangitFrame f, AwanSink sink, double y, int rings, int segments, double uvMetres, double shiftX, double shiftZ,
			double phase, int rgb, float alpha) {
		double height = Math.abs(f.camY - y);
		double rMin = Math.max(3.0D, 0.6D * height);
		if (rMin * 1.6D >= R_MAX) {
			rMin = R_MAX * 0.5D;
		}
		RADII[0] = 0.0D;
		double q = Math.pow(R_MAX / rMin, 1.0D / Math.max(1, rings - 1));
		for (int i = 1; i <= rings; i++) {
			RADII[i] = rMin * Math.pow(q, i - 1);
		}
		// world UV, re-based on the camera's tile so the floats stay small (the texture repeats, so whole tiles vanish)
		double baseU = Math.floor(f.camX / uvMetres);
		double baseV = Math.floor(f.camZ / uvMetres);
		float ry = (float) (y - f.camY);
		int stride = segments + 1;
		for (int i = 0; i <= rings; i++) {
			double rad = RADII[i];
			float fade = 1.0F - smooth((float) ((rad - FADE_FROM) / (R_MAX - FADE_FROM)));
			int c = AwanSink.argb(alpha * fade, rgb);
			for (int j = 0; j <= segments; j++) {
				double ang = Math.PI * 2.0D * j / segments;
				double dx = Math.cos(ang) * rad;
				double dz = Math.sin(ang) * rad;
				int k = i * stride + j;
				VX[k] = (float) dx;
				VZ[k] = (float) dz;
				VU[k] = (float) ((f.camX + dx - shiftX) / uvMetres - baseU + phase);
				VV[k] = (float) ((f.camZ + dz - shiftZ) / uvMetres - baseV + phase * 0.6D);
				VC[k] = c;
			}
		}
		for (int i = rings - 1; i >= 0; i--) {
			for (int j = 0; j < segments; j++) {
				int a = i * stride + j;
				int b = a + 1;
				int c = (i + 1) * stride + j + 1;
				int d = (i + 1) * stride + j;
				sink.quad(VX[a], ry, VZ[a], VU[a], VV[a], VC[a],
						VX[b], ry, VZ[b], VU[b], VV[b], VC[b],
						VX[c], ry, VZ[c], VU[c], VV[c], VC[c],
						VX[d], ry, VZ[d], VU[d], VV[d], VC[d]);
			}
		}
	}

	static float smooth(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private LautAwanRenderer() {
	}
}
