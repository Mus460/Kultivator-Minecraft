package dev.kultivator.client.sekte.langit;

import net.minecraft.util.Mth;

/**
 * Sungai Bintang, the River of Stars seen from above the clouds (DESIGN 37.6.3): at night (13 000-23 000), in clear
 * weather and with the camera on the island, the Milky Way ({@code sungai_bintang.png}, its warm core at u = 0) is drawn
 * as a ribbon of 8 / 16 / 24 quads along a great circle of the sky at radius {@code min(0.8 × fog start, 480)}, tilted
 * like the real band and turning with the vanilla star sphere ({@code STAR_ANGLE}); it fades near the horizon (where the
 * cloud sea and far peaks are), with the star brightness and when the moon outshines it. 0 / 20 / 60 brighter stars
 * ({@code gate_glow.png}) twinkle on and around it, seeded by the hall so they keep their places night after night.
 */
final class SungaiBintang {

	private static final float HALF_WIDTH = 0.21F;
	private static final float TILT = 62.0F * Mth.DEG_TO_RAD;
	private static final int MAX_STARS = 60;

	private static final AwanSink RIVER = new AwanSink(AwanSink.SUNGAI, false);
	private static final AwanSink STARS = new AwanSink(AwanSink.GATE_GLOW, false);
	private static final float[] STAR_DIR = new float[MAX_STARS * 3];
	private static final float[] STAR_SIZE = new float[MAX_STARS];
	private static final float[] STAR_PHASE = new float[MAX_STARS];
	private static long starSeed = Long.MIN_VALUE;

	static AwanSink river() {
		return RIVER;
	}

	static AwanSink stars() {
		return STARS;
	}

	static void draw(LangitFrame f) {
		if (!LangitClientConfig.on(LangitClientConfig.Toggle.SUNGAI_BINTANG) || f.tier < 0) {
			return;
		}
		long t = f.dayTime;
		if (t < 13000L || t > 23000L) {
			return;
		}
		LangitSkyState.Hall hall = f.hall;
		float clear = 1.0F - Mth.clamp(f.rain * 3.0F, 0.0F, 1.0F);
		float w = Math.max(f.night, f.starBrightness) * clear;
		if (w < 0.04F || !hall.onIsland(f.camX, f.camY, f.camZ)) {
			return;
		}
		float radius = Math.min(0.8F * f.fogStart, 480.0F);
		radius = Math.max(48.0F, radius);
		int n = LangitBudget.RIVER_QUADS[f.tier];
		// the band's plane: a great circle tilted TILT from the zenith, turned by the star sphere about the X axis
		float sa = Mth.sin(f.starAngle);
		float ca = Mth.cos(f.starAngle);
		// A runs along the band's "equator" (east-west), B across the sky through the tilted pole
		float ax = 1.0F;
		float ay = 0.0F;
		float az = 0.0F;
		float bx = 0.0F;
		float by = Mth.cos(TILT);
		float bz = Mth.sin(TILT);
		// turn B with the stars about X
		float by2 = by * ca - bz * sa;
		float bz2 = by * sa + bz * ca;
		by = by2;
		bz = bz2;
		// the band's normal N = A × B
		float nx = ay * bz - az * by;
		float ny = az * bx - ax * bz;
		float nz = ax * by - ay * bx;
		if (LangitBudget.take(n)) {
			for (int i = 0; i < n; i++) {
				float t0 = (float) i / n;
				float t1 = (float) (i + 1) / n;
				float th0 = t0 * Mth.TWO_PI;
				float th1 = t1 * Mth.TWO_PI;
				float p0x = Mth.cos(th0) * ax + Mth.sin(th0) * bx;
				float p0y = Mth.cos(th0) * ay + Mth.sin(th0) * by;
				float p0z = Mth.cos(th0) * az + Mth.sin(th0) * bz;
				float p1x = Mth.cos(th1) * ax + Mth.sin(th1) * bx;
				float p1y = Mth.cos(th1) * ay + Mth.sin(th1) * by;
				float p1z = Mth.cos(th1) * az + Mth.sin(th1) * bz;
				int c0 = AwanSink.argb(0.8F * w * horizon(p0y), 0xFFFFFF);
				int c1 = AwanSink.argb(0.8F * w * horizon(p1y), 0xFFFFFF);
				if ((c0 >>> 24) == 0 && (c1 >>> 24) == 0) {
					// both ends under the horizon: keep the quad count honest but draw nothing
					continue;
				}
				float r = radius;
				float hw = HALF_WIDTH * r;
				RIVER.quad(p0x * r - nx * hw, p0y * r - ny * hw, p0z * r - nz * hw, t0, 1.0F, c0,
						p1x * r - nx * hw, p1y * r - ny * hw, p1z * r - nz * hw, t1, 1.0F, c1,
						p1x * r + nx * hw, p1y * r + ny * hw, p1z * r + nz * hw, t1, 0.0F, c1,
						p0x * r + nx * hw, p0y * r + ny * hw, p0z * r + nz * hw, t0, 0.0F, c0);
			}
		}
		// the twinkling bright stars
		int stars = LangitBudget.STARS[f.tier];
		if (stars == 0) {
			return;
		}
		seed(hall.seed);
		int granted = LangitBudget.takeUpTo(stars);
		float r = radius * 0.99F;
		for (int i = 0; i < granted; i++) {
			float x = STAR_DIR[i * 3];
			float y = STAR_DIR[i * 3 + 1];
			float z = STAR_DIR[i * 3 + 2];
			// the stars turn with the sky like the band
			float y2 = y * ca - z * sa;
			float z2 = y * sa + z * ca;
			float a = horizon(y2) * w * (0.45F + 0.55F * (0.5F + 0.5F * Mth.sin(f.ticks * 0.09F + STAR_PHASE[i])));
			if (a < 0.03F) {
				continue;
			}
			int rgb = i % 5 == 0 ? 0xFFE2C0 : (i % 3 == 0 ? 0xC8D8FF : 0xFFFFFF);
			STARS.billboard(x * r, y2 * r, z2 * r, STAR_SIZE[i] * r, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, AwanSink.argb(a, rgb));
		}
	}

	/** Fades the sky out toward the horizon (the cloud sea and far peaks are there). */
	private static float horizon(float y) {
		return Mth.clamp((y - 0.04F) / 0.18F, 0.0F, 1.0F);
	}

	private static void seed(long seed) {
		if (seed == starSeed) {
			return;
		}
		starSeed = seed;
		java.util.Random random = new java.util.Random(seed ^ 0x5EB7L);
		for (int i = 0; i < MAX_STARS; i++) {
			// two thirds hug the band, the rest anywhere on the upper sky
			float x;
			float y;
			float z;
			if (i % 3 != 0) {
				float th = random.nextFloat() * Mth.TWO_PI;
				float off = (random.nextFloat() - 0.5F) * 0.4F;
				x = Mth.cos(th);
				y = Mth.sin(th) * Mth.cos(TILT) + off * Mth.sin(TILT);
				z = Mth.sin(th) * Mth.sin(TILT) - off * Mth.cos(TILT);
			} else {
				x = random.nextFloat() * 2.0F - 1.0F;
				y = random.nextFloat() * 0.9F + 0.1F;
				z = random.nextFloat() * 2.0F - 1.0F;
			}
			float l = Mth.sqrt(x * x + y * y + z * z);
			STAR_DIR[i * 3] = x / l;
			STAR_DIR[i * 3 + 1] = y / l;
			STAR_DIR[i * 3 + 2] = z / l;
			STAR_SIZE[i] = 0.004F + random.nextFloat() * 0.006F;
			STAR_PHASE[i] = random.nextFloat() * Mth.TWO_PI;
		}
	}

	private SungaiBintang() {
	}
}
