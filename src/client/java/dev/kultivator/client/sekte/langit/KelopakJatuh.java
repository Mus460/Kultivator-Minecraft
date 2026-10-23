package dev.kultivator.client.sekte.langit;

import net.minecraft.util.Mth;

/**
 * Kelopak Jatuh, plum petals drifting off the island (DESIGN 37.6.5): from the second tier (the plum trees of
 * {@code aw_t2_pohon}) and thicker after {@code aw_t9_finale}, 0 / 40 / 120 petals ({@code petal.png}, four shapes) within
 * 48 blocks of the camera ride the day's breeze across the island, tumble over the rim and fade out as they sink below the
 * island's belly. Every petal is a pure function of the hall seed, its index and the time (no state, nothing to tick):
 * it is born at a seeded spot over the island, lives 14 s, and is born again elsewhere. Day and festival nights only;
 * the vanilla "Partikel" Minimal shows none.
 */
final class KelopakJatuh {

	private static final double RANGE = 48.0D;
	private static final float LIFE = 14.0F;
	private static final int[] TINTS = {0xFFE4EC, 0xFFD0DE, 0xFFF4F6, 0xF8C8D8};

	private static final AwanSink PETALS = new AwanSink(AwanSink.PETAL, false);

	static AwanSink sink() {
		return PETALS;
	}

	static void draw(LangitFrame f) {
		LangitSkyState.Hall hall = f.hall;
		if (!LangitClientConfig.on(LangitClientConfig.Toggle.KELOPAK) || f.particleTier < 1 || hall.tier < 2 || f.rain > 0.5F) {
			return;
		}
		if (f.daylight < 0.2F && !hall.festival()) {
			return;
		}
		if (!hall.onIsland(f.camX, f.camY, f.camZ)) {
			return;
		}
		int want = LangitBudget.PETALS[f.particleTier];
		if (hall.tier < 9) {
			want = want * 2 / 3;
		}
		// the breeze of the day (the cloud sea's wind, gentler)
		long h = hall.seed * 0x9E3779B97F4A7C15L + f.day * 0xBF58476D1CE4E5B9L;
		h ^= h >>> 31;
		float wind = (h & 0xFFFF) / 65536.0F * Mth.TWO_PI;
		float wx = Mth.cos(wind);
		float wz = Mth.sin(wind);
		double minX = hall.box.minX();
		double minZ = hall.box.minZ();
		double spanX = hall.box.maxX() + 1 - minX;
		double spanZ = hall.box.maxZ() + 1 - minZ;
		// over a big island only part of the petals are near the camera: try four times as many births, keep the near ones
		int granted = 0;
		int budget = LangitBudget.takeUpTo(want);
		int candidates = want * 4;
		float light = 0.6F + 0.4F * Math.max(f.daylight, 0.3F);
		for (int i = 0; i < candidates && granted < budget; i++) {
			float offset = hash(hall.seed, i, 0) * LIFE;
			float age = f.seconds + offset;
			int cycle = Mth.floor(age / LIFE);
			float life = age / LIFE - cycle;
			double bx = minX + hash(hall.seed, i, cycle * 2 + 1) * spanX;
			double bz = minZ + hash(hall.seed, i, cycle * 2 + 2) * spanZ;
			double by = hall.yP + 3.0D + hash(hall.seed, i, cycle + 7) * 9.0D;
			// drift: down the breeze, tumbling sideways, sinking slowly (faster once past the rim)
			float travel = life * 22.0F;
			float wobble = Mth.sin(life * 9.0F + i) * 1.2F;
			double x = bx + wx * travel + wz * wobble;
			double z = bz + wz * travel - wx * wobble;
			double y = by - life * 7.0F - Math.max(0.0F, life - 0.6F) * 25.0F;
			double dx = x - f.camX;
			double dz = z - f.camZ;
			if (dx * dx + dz * dz > RANGE * RANGE) {
				continue;
			}
			float fade = Mth.clamp(life * 8.0F, 0.0F, 1.0F) * (1.0F - LautAwanRenderer.smooth((life - 0.8F) / 0.2F));
			if (y < hall.skyFloorY) {
				fade *= Mth.clamp((float) (y - hall.skyFloorY + 12.0D) / 12.0F, 0.0F, 1.0F);
			}
			if (fade < 0.05F) {
				continue;
			}
			int shape = i & 3;
			float u0 = shape * 0.25F;
			int tint = AwanSink.scale(TINTS[(i >> 2) & 3], light);
			PETALS.billboard(f.rx(x), f.ry(y), f.rz(z), 0.16F, life * 11.0F + i, u0, u0 + 0.25F, 0.0F, 1.0F, AwanSink.argb(0.95F * fade, tint));
			granted++;
		}
	}

	/** A hash of (seed, i, k) in [0, 1). */
	static float hash(long seed, int i, int k) {
		long h = seed ^ (i * 0x9E3779B97F4A7C15L) ^ (k * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (h >>> 40) / (float) (1L << 24);
	}

	private KelopakJatuh() {
	}
}
