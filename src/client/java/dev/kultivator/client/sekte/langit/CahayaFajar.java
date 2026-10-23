package dev.kultivator.client.sekte.langit;

import net.minecraft.util.Mth;

/**
 * Cahaya Fajar, the golden light of dawn and dusk over the cloud sea (DESIGN 37.6.3): between 23 000-1 000 and
 * 11 500-13 000 the top layer of {@link LautAwanRenderer} is warmed toward {@code 0xFFD27A} (dusk a deeper amber), at most
 * 0.45, and while the camera is on the island 0 / 5 / 9 additive sunbeams ({@code shaft.png}) fall from the sun's side of
 * the sky onto the sea and sweep slowly across it, as if the light were combing the clouds. Rain thins the beams.
 */
final class CahayaFajar {

	private static final int DAWN = 0xFFD27A;
	private static final int DUSK = 0xFFA85C;
	private static final float MAX_TINT = 0.45F;

	private static final AwanSink SHAFTS = new AwanSink(AwanSink.SHAFT, true);

	static AwanSink sink() {
		return SHAFTS;
	}

	/** {@code rgb} warmed by the golden hour (the cloud sea's top layer). */
	static int tint(LangitFrame f, int rgb) {
		float k = f.golden * MAX_TINT * (1.0F - 0.7F * f.rain);
		if (k <= 0.005F) {
			return rgb;
		}
		return AwanSink.mix(rgb, f.dawn() ? DAWN : DUSK, k);
	}

	static void draw(LangitFrame f) {
		LangitSkyState.Hall hall = f.hall;
		int n = LangitBudget.DAWN_SHAFTS[Math.max(0, f.tier)];
		float w = f.golden * (1.0F - f.rain);
		if (n == 0 || w < 0.03F || !hall.onIsland(f.camX, f.camY, f.camZ)) {
			return;
		}
		// the sun's side of the sky, flattened to the horizon (at dawn and dusk it is low anyway)
		float hx = f.sunX;
		float hz = f.sunZ;
		float hl = Mth.sqrt(hx * hx + hz * hz);
		if (hl < 0.1F) {
			return;
		}
		hx /= hl;
		hz /= hl;
		// the perpendicular, along which the beams are spread
		float px = -hz;
		float pz = hx;
		float seaY = f.ry(hall.cloudY());
		int rgb = f.dawn() ? DAWN : DUSK;
		for (int i = 0; i < n; i++) {
			if (!LangitBudget.take(1)) {
				return;
			}
			float seed = i * 1.618F;
			// each beam lands on the sea 50-230 blocks toward the sun and sweeps sideways, slowly and out of step
			float along = 50.0F + 180.0F * frac(seed * 0.37F + 0.11F);
			float side = (frac(seed * 0.71F) - 0.5F) * 220.0F + Mth.sin(f.seconds * 0.05F + seed * 2.3F) * 30.0F;
			float lx = hx * along + px * side;
			float lz = hz * along + pz * side;
			// it comes from high up on the sun's side, leaning toward the camera
			float tx = lx + hx * 140.0F;
			float tz = lz + hz * 140.0F;
			float ty = seaY + 120.0F + 40.0F * frac(seed * 0.53F);
			float breathe = 0.6F + 0.4F * Mth.sin(f.seconds * 0.21F + seed * 4.1F);
			float a = 0.28F * w * breathe;
			float width = 4.0F + 8.0F * frac(seed * 0.29F + 0.5F);
			SHAFTS.beam(tx, ty, tz, lx, seaY, lz, width, 0.0F, 1.0F, AwanSink.argb(a * 0.35F, rgb), AwanSink.argb(a, rgb));
		}
	}

	private static float frac(float v) {
		return v - Mth.floor(v);
	}

	private CahayaFajar() {
	}
}
