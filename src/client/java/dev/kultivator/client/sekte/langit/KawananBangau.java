package dev.kultivator.client.sekte.langit;

import net.minecraft.util.Mth;

/**
 * Kawanan Bangau, the red-crowned cranes of the sky sect (DESIGN 37.6.5): by day, within 96 blocks of the island,
 * 0 / 1 / 2 flocks (0 / 5 / 12 cranes) fly a slow Lissajous loop round the island between {@code yP + 10} and
 * {@code yP + 30}, in a V behind their leader. Each crane is a camera-facing sprite ({@code bangau.png}: side view, two
 * wing frames) mirrored to face the way it flies, beating its wings out of step with its neighbours and now and then
 * gliding with the wings held high. The flock's nearest distance feeds {@link LangitSuara} (wing beats ≤ 24 blocks).
 */
final class KawananBangau {

	private static final double RANGE = 96.0D;
	private static final float HALF = 1.3F;
	/** Seconds per loop of the flight path. */
	private static final float LOOP = 95.0F;

	private static final AwanSink CRANES = new AwanSink(AwanSink.BANGAU, false);
	/** Nearest crane this frame (blocks), for the wing-beat sound; large when none. */
	static float nearest = Float.MAX_VALUE;
	static float nearX;
	static float nearY;
	static float nearZ;

	static AwanSink sink() {
		return CRANES;
	}

	static void draw(LangitFrame f) {
		nearest = Float.MAX_VALUE;
		if (!LangitClientConfig.on(LangitClientConfig.Toggle.BANGAU) || f.tier < 1 || f.daylight < 0.25F || f.rain > 0.6F) {
			return;
		}
		LangitSkyState.Hall hall = f.hall;
		double cx = hall.centreX();
		double cz = hall.centreZ();
		double r = hall.radius();
		double dx = f.camX - cx;
		double dz = f.camZ - cz;
		if (dx * dx + dz * dz > (r + RANGE) * (r + RANGE)) {
			return;
		}
		int flocks = LangitBudget.CRANE_FLOCKS[f.tier];
		int total = LangitBudget.CRANES[f.tier];
		int granted = LangitBudget.takeUpTo(total);
		int drawn = 0;
		float light = 0.55F + 0.45F * f.daylight;
		int tint = AwanSink.mix(0x9AA2B8, 0xFFFFFF, light);
		for (int k = 0; k < flocks && drawn < granted; k++) {
			int birds = k == 0 ? (flocks == 1 ? total : total / 2 + total % 2) : total / 2;
			float phase = k * 0.47F + (hall.seed & 0xFF) / 255.0F;
			float t = (f.seconds / LOOP + phase) * Mth.TWO_PI * (k == 0 ? 1.0F : -1.0F);
			// the Lissajous loop round the island (1 : 2 in x and z gives the figure-of-eight over the island)
			float ax = (float) (r + 22.0D + k * 10.0D);
			float az = (float) (r + 14.0D + k * 8.0D);
			float baseY = (float) (hall.yP + 18.0D + k * 6.0D);
			float lx = (float) cx + ax * Mth.sin(t);
			float lz = (float) cz + az * Mth.sin(t * 2.0F + 0.6F) * 0.6F;
			float ly = baseY + 6.0F * Mth.sin(t * 3.0F + phase);
			// the heading (derivative of the loop)
			float vx = ax * Mth.cos(t);
			float vz = az * Mth.cos(t * 2.0F + 0.6F) * 1.2F;
			float vl = Mth.sqrt(vx * vx + vz * vz);
			if (vl < 1.0E-3F) {
				vl = 1.0F;
			}
			vx /= vl;
			vz /= vl;
			if (k == 1) {
				vx = -vx;
				vz = -vz;
			}
			// right-of-heading
			float sx = -vz;
			float sz = vx;
			for (int i = 0; i < birds && drawn < granted; i++) {
				// the V: the leader in front, the others back and to alternate sides
				int rank = (i + 1) / 2;
				float side = i == 0 ? 0.0F : (i % 2 == 0 ? 1.0F : -1.0F);
				float back = rank * 2.6F;
				float bx = lx - vx * back + sx * side * rank * 2.0F;
				float bz = lz - vz * back + sz * side * rank * 2.0F;
				float by = ly - rank * 0.5F + Mth.sin(f.seconds * 0.7F + i) * 0.3F;
				// wings: beating out of step, gliding (wings up) a third of the time
				float beat = f.seconds * 2.4F + i * 0.9F + k * 2.0F;
				boolean glide = Mth.sin(f.seconds * 0.21F + i * 1.7F) > 0.35F;
				int frame = glide ? 0 : (Mth.sin(beat * Mth.PI) > 0.0F ? 0 : 1);
				float rx = f.rx(bx);
				float ry = f.ry(by);
				float rz = f.rz(bz);
				// face the way it flies: compare its heading with the camera's right axis
				boolean flip = vx * f.rightX + vz * f.rightZ < 0.0F;
				float u0 = frame * 0.5F;
				float u1 = u0 + 0.5F;
				if (flip) {
					float tmp = u0;
					u0 = u1;
					u1 = tmp;
				}
				float dist = Mth.sqrt(rx * rx + ry * ry + rz * rz);
				float fade = 1.0F - LautAwanRenderer.smooth((dist - 120.0F) / 60.0F);
				CRANES.billboard(rx, ry, rz, HALF, 0.0F, u0, u1, 0.0F, 1.0F, AwanSink.argb(fade, tint));
				if (dist < nearest) {
					nearest = dist;
					nearX = (float) bx;
					nearY = (float) by;
					nearZ = (float) bz;
				}
				drawn++;
			}
		}
	}

	private KawananBangau() {
	}
}
