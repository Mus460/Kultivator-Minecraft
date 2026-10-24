package dev.kultivator.client.dunia.tempat.tema.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The per-frame state every themed-town effect reads (render thread only, filled once per frame by {@link TemaFx}):
 * camera position and basis, partial tick, game time, the hour, how bright the sky is, rain and thunder as read from
 * the level (never changed), the sun direction from the {@code visual/sun_angle} environment attribute, and the budget
 * tier of {@code EpicBudget} (0 Minimal, 1 Normal, 2 Maximal). Small maths helpers live here too.
 */
final class Bingkai {

	static ClientLevel level;
	static double camX;
	static double camY;
	static double camZ;
	static final Vector3f KANAN = new Vector3f();
	static final Vector3f ATAS = new Vector3f();
	static final Vector3f MAJU = new Vector3f();
	static float pt;
	static long game;
	/** Game time as a float with the partial tick (wraps every 2.4 M ticks so floats keep their precision). */
	static float t;
	/** Overworld clock hour 0..24000. */
	static long jam;
	/** 0 (night) .. 1 (clear noon): how lit open-air effects look; rain and thunder darken it. */
	static float terang;
	static float hujan;
	static float petir;
	/** Unit vector toward the sun (from the camera). */
	static final Vector3f MATAHARI = new Vector3f(0.0F, 1.0F, 0.0F);
	static int tier;

	private Bingkai() {
	}

	static void isi(ClientLevel lv, Vec3 cam, org.joml.Quaternionf orientation, float partial) {
		level = lv;
		camX = cam.x;
		camY = cam.y;
		camZ = cam.z;
		orientation.transform(1.0F, 0.0F, 0.0F, KANAN);
		orientation.transform(0.0F, 1.0F, 0.0F, ATAS);
		orientation.transform(0.0F, 0.0F, -1.0F, MAJU);
		pt = partial;
		game = lv.getGameTime();
		t = (float) (game % 2_400_000L) + partial;
		jam = Math.floorMod(lv.getOverworldClockTime(), 24000L);
		hujan = lv.getRainLevel(partial);
		petir = lv.getThunderLevel(partial);
		float sky = 1.0F - Mth.clamp(lv.getSkyDarken() / 11.0F, 0.0F, 1.0F);
		terang = Mth.clamp(0.12F + 0.88F * sky, 0.12F, 1.0F) * (1.0F - 0.25F * hujan - 0.2F * petir);
		float sunDeg = lv.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, cam);
		float a = sunDeg * Mth.DEG_TO_RAD;
		MATAHARI.set(-Mth.sin(a), Mth.cos(a), 0.0F);
	}

	/** Game time with the partial tick, as a double (compare with absolute ticks of payloads). */
	static double sekarang() {
		return game + (double) pt;
	}

	static boolean malam() {
		return jam >= 12500L && jam <= 23500L;
	}

	/** Morning: dawn to mid-morning (22500 .. 2500), the hour of cloud seas, mist and the Buddha light. */
	static boolean pagi() {
		return jam >= 22500L || jam <= 2500L;
	}

	static double jarak2(double x, double y, double z) {
		double dx = x - camX;
		double dy = y - camY;
		double dz = z - camZ;
		return dx * dx + dy * dy + dz * dz;
	}

	static double jarakDatar2(double x, double z) {
		double dx = x - camX;
		double dz = z - camZ;
		return dx * dx + dz * dz;
	}

	/** World light at a position, packed. */
	static int cahaya(double x, double y, double z) {
		if (level == null) {
			return LightCoordsUtil.FULL_BRIGHT;
		}
		BlockPos p = BlockPos.containing(x, y, z);
		return LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, p), level.getBrightness(LightLayer.SKY, p));
	}

	static int argb(float alpha, int rgb) {
		return Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24 | rgb & 0xFFFFFF;
	}

	/** {@code rgb} scaled by {@code f} (0..1+), clamped per channel. */
	static int redup(int rgb, float f) {
		int r = Mth.clamp((int) ((rgb >> 16 & 0xFF) * f), 0, 255);
		int g = Mth.clamp((int) ((rgb >> 8 & 0xFF) * f), 0, 255);
		int b = Mth.clamp((int) ((rgb & 0xFF) * f), 0, 255);
		return r << 16 | g << 8 | b;
	}

	static int campur(int a, int b, float k) {
		float t0 = Mth.clamp(k, 0.0F, 1.0F);
		int r = (int) Mth.lerp(t0, a >> 16 & 0xFF, b >> 16 & 0xFF);
		int g = (int) Mth.lerp(t0, a >> 8 & 0xFF, b >> 8 & 0xFF);
		int bl = (int) Mth.lerp(t0, a & 0xFF, b & 0xFF);
		return r << 16 | g << 8 | bl;
	}

	static float hash(long seed, int a) {
		int h = Mth.murmurHash3Mixer((int) (seed ^ seed >>> 32) * 31 + a * 0x9E3779B1);
		return (h >>> 8) / 16777216.0F;
	}

	static float halus(float x) {
		float c = Mth.clamp(x, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}

	/** 0..1..0 envelope over [mulai, mulai + panjang] with fades of {@code masuk} and {@code keluar} ticks. */
	static float selubung(float now, float mulai, float panjang, float masuk, float keluar) {
		if (now < mulai || now > mulai + panjang) {
			return 0.0F;
		}
		float in = masuk <= 0.0F ? 1.0F : halus((now - mulai) / masuk);
		float out = keluar <= 0.0F ? 1.0F : halus((mulai + panjang - now) / keluar);
		return Math.min(in, out);
	}
}
