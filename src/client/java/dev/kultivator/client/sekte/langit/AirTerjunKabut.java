package dev.kultivator.client.sekte.langit;

import dev.kultivator.client.fx.qi.Qi;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Air Terjun Kabut, the mist falls at the island's rim (DESIGN 37.6.4): from every {@code airterjun_<i>_<side>} notch a
 * ribbon of {@code waterfall.png} 2-3 blocks wide pours outwards and down, arcing away from the rim like water leaving a
 * lip, 12 / 24 / 40 blocks long in 4 / 8 / 16 segments that always face the camera, its texture scrolling down 4
 * blocks/s; over its lowest 40% it thins into mist: 2 / 6 / 12 slowly turning {@code cloud_puff.png} billows that swell as
 * they sink, so the falls never reach the ground (rule 21.8: no water ever flows off an island; the small pond on the
 * island is real, closed water built by the blueprint). At most 4 / 8 / 8 emitters, the nearest first, within 128 blocks.
 * A few qi-mist motes drift off the nearest fall at Normal+ (particle budget).
 */
final class AirTerjunKabut {

	private static final double RANGE = 128.0D;
	private static final float SCROLL = 4.0F;
	private static final float TEXTURE_BLOCKS = 16.0F;
	private static final int MAX_EMITTERS = 8;

	private static final AwanSink FALL = new AwanSink(AwanSink.WATERFALL, false);
	private static final AwanSink MIST = new AwanSink(AwanSink.CLOUD_PUFF, false);
	private static final int[] PICK = new int[MAX_EMITTERS];
	private static final double[] PICK_D = new double[MAX_EMITTERS];

	static AwanSink fall() {
		return FALL;
	}

	static AwanSink mist() {
		return MIST;
	}

	static void draw(LangitFrame f) {
		if (!LangitClientConfig.on(LangitClientConfig.Toggle.AIR_TERJUN) || f.tier < 0) {
			return;
		}
		LangitSkyState.Hall hall = f.hall;
		int tier = f.tier;
		int emitters = pick(f, hall, LangitBudget.FALL_EMITTERS[tier]);
		int segments = LangitBudget.FALL_SEGMENTS[tier];
		float length = LangitBudget.FALL_LENGTH[tier];
		int puffs = LangitBudget.FALL_PUFFS[f.particleTier];
		int water = AwanSink.mix(0x8FA8C8, 0xEAF6FF, Math.max(0.25F, f.daylight));
		for (int k = 0; k < emitters; k++) {
			LangitSkyState.Fall fall = hall.falls.get(PICK[k]);
			if (!LangitBudget.take(segments)) {
				return;
			}
			float width = fall.index() % 3 == 0 ? 1.5F : 1.1F;
			float phase = fall.index() * 0.37F;
			float x0 = f.rx(fall.pos().getX() + 0.5D + fall.dx() * 0.6D);
			float y0 = f.ry(fall.pos().getY() + 0.9D);
			float z0 = f.rz(fall.pos().getZ() + 0.5D + fall.dz() * 0.6D);
			float vTop = (-f.seconds * SCROLL) / TEXTURE_BLOCKS + phase;
			float px = x0;
			float py = y0;
			float pz = z0;
			float pa = 0.92F;
			for (int i = 1; i <= segments; i++) {
				float t = (float) i / segments;
				float drop = t * length;
				// the arc of water leaving the lip: out fast, then nearly straight down
				float out = 0.55F * Mth.sqrt(drop) + 0.04F * drop;
				float sway = Mth.sin(f.seconds * 0.8F + phase + t * 3.0F) * 0.15F * t;
				float qx = x0 + fall.dx() * out + fall.dz() * sway;
				float qy = y0 - drop;
				float qz = z0 + fall.dz() * out + fall.dx() * sway;
				float qa = 0.92F * (1.0F - LautAwanRenderer.smooth((t - 0.6F) / 0.4F));
				float spread = width * (1.0F + 0.5F * t);
				FALL.beam(px, py, pz, qx, qy, qz, spread, vTop + (t - 1.0F / segments) * length / TEXTURE_BLOCKS, vTop + drop / TEXTURE_BLOCKS,
						AwanSink.argb(pa, water), AwanSink.argb(qa, water));
				px = qx;
				py = qy;
				pz = qz;
				pa = qa;
			}
			// the mist the fall dissolves into
			int granted = puffs > 0 ? LangitBudget.takeUpTo(puffs) : 0;
			for (int i = 0; i < granted; i++) {
				float life = frac(f.seconds * 0.12F + i * 0.618F + phase);
				float drop = length * (0.55F + 0.5F * life);
				float out = 0.55F * Mth.sqrt(drop) + 0.04F * drop + (frac(i * 0.37F) - 0.5F) * 2.0F;
				float side = (frac(i * 0.73F + 0.2F) - 0.5F) * 4.0F;
				float cx = x0 + fall.dx() * out + fall.dz() * side;
				float cy = y0 - drop;
				float cz = z0 + fall.dz() * out + fall.dx() * side;
				float size = width * (1.4F + 2.2F * life);
				float a = 0.55F * Mth.sin(life * Mth.PI);
				MIST.billboard(cx, cy, cz, size, f.seconds * 0.15F + i, 0.0F, 1.0F, 0.0F, 1.0F, AwanSink.argb(a, water));
			}
		}
	}

	/** A few qi-mist motes drifting off the nearest fall within 32 blocks (Normal+, every 10 ticks, particle budget). */
	static void tick(ClientLevel level, double camX, double camY, double camZ, LangitSkyState.Hall hall, int particleTier) {
		if (particleTier < 1 || !LangitClientConfig.on(LangitClientConfig.Toggle.AIR_TERJUN) || level.getGameTime() % 10L != 0L
				|| !Qi.on()) {
			return;
		}
		LangitSkyState.Fall best = null;
		double bestD = 32.0D * 32.0D;
		for (LangitSkyState.Fall fall : hall.falls) {
			double d = fall.pos().distToCenterSqr(camX, camY, camZ);
			if (d < bestD) {
				best = fall;
				bestD = d;
			}
		}
		if (best == null) {
			return;
		}
		int n = LangitBudget.particles(particleTier >= 2 ? 3 : 1, particleTier);
		RandomSource random = level.getRandom();
		for (int i = 0; i < n; i++) {
			double drop = 2.0D + random.nextDouble() * 8.0D;
			double out = 0.55D * Math.sqrt(drop) + 0.5D;
			double x = best.pos().getX() + 0.5D + best.dx() * out + (random.nextDouble() - 0.5D) * 2.0D * (best.dz() != 0 ? 1 : 0.2D);
			double y = best.pos().getY() + 0.5D - drop;
			double z = best.pos().getZ() + 0.5D + best.dz() * out + (random.nextDouble() - 0.5D) * 2.0D * (best.dx() != 0 ? 1 : 0.2D);
			level.addParticle(Qi.mist(0xE8F4FF, 0.9F, 60), x, y, z, best.dx() * 0.02D, -0.01D, best.dz() * 0.02D);
		}
	}

	/** The nearest {@code max} falls within range into {@link #PICK}; returns how many. */
	private static int pick(LangitFrame f, LangitSkyState.Hall hall, int max) {
		int n = 0;
		max = Math.min(max, MAX_EMITTERS);
		for (int i = 0; i < hall.falls.size(); i++) {
			LangitSkyState.Fall fall = hall.falls.get(i);
			double d = fall.pos().distToCenterSqr(f.camX, f.camY, f.camZ);
			if (d > RANGE * RANGE) {
				continue;
			}
			if (n < max) {
				PICK[n] = i;
				PICK_D[n] = d;
				n++;
			} else {
				int worst = 0;
				for (int k = 1; k < n; k++) {
					if (PICK_D[k] > PICK_D[worst]) {
						worst = k;
					}
				}
				if (d < PICK_D[worst]) {
					PICK[worst] = i;
					PICK_D[worst] = d;
				}
			}
		}
		return n;
	}

	private static float frac(float v) {
		return v - Mth.floor(v);
	}

	private AirTerjunKabut() {
	}
}
