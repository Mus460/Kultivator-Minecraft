package dev.kultivator.client.sekte.langit;

import dev.kultivator.sekte.langit.api.LangitIds;
import dev.kultivator.sekte.langit.api.LangitPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Mth;

/**
 * Halo Pelangi, the Buddha's Light of Mount Emei (DESIGN 37.6.3; the "glory" seen on Emei's Golden Summit: a rainbow
 * ring around the observer's own shadow on the sea of clouds). When the sun is low behind the camera
 * ({@code dot(view, −sun) > 0.9}), the camera stands above the cloud sea and within 48 blocks of the island's rim, one
 * camera-facing quad ({@code halo.png}: a faint human shadow inside a rainbow glory and a fainter outer bow) sits on the
 * cloud sea at the antisolar point, always 12° in radius, alpha 0.35. It shows on 30% of the dawns (rolled from the world
 * day, so every player sees the same morning) for 60 s; Minimal never draws it. The first sighting of such a morning
 * sends {@code Saw(SAW_HALO)}; the server checks time and place and grants {@code sekte_langit/cahaya_buddha}.
 */
final class HaloPelangi {

	private static final float ANGLE = 12.0F * Mth.DEG_TO_RAD;
	private static final float ALPHA = 0.35F;
	private static final int CHANCE = 30;
	private static final long SHOW_TICKS = 1200L;

	private static final AwanSink HALO = new AwanSink(AwanSink.HALO, false);
	/** A test sighting (37G {@code uji langit_sekte halo}): client ticks left, any time of day. */
	private static int forced;
	private static long sentDay = Long.MIN_VALUE;
	private static float shown;

	static AwanSink sink() {
		return HALO;
	}

	/** Shows the halo for {@code ticks} regardless of the day's roll and the sun (the test effect). */
	static void force(int ticks) {
		forced = Math.max(forced, ticks);
	}

	static void tick() {
		if (forced > 0) {
			forced--;
		}
	}

	static void clear() {
		forced = 0;
		shown = 0.0F;
	}

	/** True while this morning's halo window is open (30% of dawns, 60 s from a seeded start). */
	static boolean window(LangitFrame f) {
		if (forced > 0) {
			return true;
		}
		long h = f.day * 0x9E3779B97F4A7C15L + 0x37L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		if (Math.floorMod(h, 100L) >= CHANCE) {
			return false;
		}
		// the start: somewhere in 23 200 - 23 900, so the 60 s fall inside the dawn
		long start = 23200L + Math.floorMod(h >>> 8, 700L);
		long t = f.dayTime < 12000L ? f.dayTime + 24000L : f.dayTime;
		return t >= start && t < start + SHOW_TICKS;
	}

	static void draw(LangitFrame f) {
		if (!LangitClientConfig.on(LangitClientConfig.Toggle.HALO) || LangitBudget.HALO[Math.max(0, f.tier)] == 0) {
			return;
		}
		LangitSkyState.Hall hall = f.hall;
		boolean test = forced > 0;
		if (!window(f) || f.rain > 0.3F) {
			shown = 0.0F;
			return;
		}
		double seaY = hall.cloudY();
		if (f.camY < seaY + 4.0D) {
			return;
		}
		// within 48 blocks of the rim (from the island's centre outwards)
		double dx = f.camX - hall.centreX();
		double dz = f.camZ - hall.centreZ();
		double fromCentre = Math.sqrt(dx * dx + dz * dz);
		if (!test && fromCentre < hall.radius() - 48.0D) {
			return;
		}
		// the antisolar direction; the test puts it straight ahead and down
		float ax = -f.sunX;
		float ay = -f.sunY;
		float az = -f.sunZ;
		if (test) {
			float fl = Mth.sqrt(f.fwdX * f.fwdX + f.fwdZ * f.fwdZ);
			if (fl < 0.05F) {
				return;
			}
			ax = f.fwdX / fl * 0.94F;
			ay = -0.34F;
			az = f.fwdZ / fl * 0.94F;
		} else {
			float dot = f.fwdX * ax + f.fwdY * ay + f.fwdZ * az;
			if (dot < 0.9F || f.sunY > 0.45F) {
				return;
			}
		}
		// where the antisolar ray meets the cloud sea (or 200 blocks out along it when it barely dips)
		float h = (float) (seaY - f.camY);
		float dist = ay < -0.02F ? Math.min(320.0F, h / ay) : 200.0F;
		if (dist <= 2.0F) {
			return;
		}
		if (!LangitBudget.take(1)) {
			return;
		}
		shown = Math.min(1.0F, shown + 0.02F);
		float cx = ax * dist;
		float cy = ay < -0.02F ? h : ay * dist;
		float cz = az * dist;
		float half = dist * (float) Math.tan(ANGLE) * 1.05F;
		float shimmer = 0.9F + 0.1F * Mth.sin(f.seconds * 0.7F);
		HALO.billboard(cx, cy, cz, half, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, AwanSink.argb(ALPHA * shown * shimmer, 0xFFFFFF));
		if (shown > 0.5F && sentDay != f.day && !test) {
			sentDay = f.day;
			if (ClientPlayNetworking.canSend(LangitPayloads.Saw.TYPE)) {
				ClientPlayNetworking.send(new LangitPayloads.Saw(LangitIds.SAW_HALO));
			}
		}
	}

	private HaloPelangi() {
	}
}
