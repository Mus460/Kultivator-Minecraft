package dev.kultivator.client.dunia.tempat.tema.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Fajar Cahaya Budha of the Kota Gunung (DESIGN 38.T.6.5, 38.T.9.4): the "Buddha's light" of Emei Shan, a glory, a ring of
 * rainbow light on the cloud sea around the point opposite the sun, with the watcher's own shadow (the Brocken spectre)
 * at its centre. From {@code TemaNet.CAHAYA apa=budha} ({@code x, y, z} = the {@code pandang} titik of the Aula Emas,
 * {@code tick}, {@code panjang}, {@code nyala}).
 * <p>
 * The glory is drawn where it really appears: along the anti-solar direction from the camera (from the
 * {@code visual/sun_angle} attribute), held 6-40 degrees below the horizon so it lies on the clouds, 40 blocks out and
 * facing the camera, so it follows the eye like the real one. Layers (additive, breathing slowly, shimmering as mist
 * passes): the pre-coloured glory ring ({@code pelangi}); Normal adds a violet inner and a red outer ring; Maximal a faint
 * second-order pair, the white core and a wide fog-bow. The shadow (dark translucent {@code siluet}) shows only to a
 * player standing within 6 blocks of the {@code pandang} point; others in the upper row (64 blocks) see the ring alone.
 * Minimal: 1 ring, Normal 3, Maximal 5 layers. Weather and time are only read.
 */
final class CahayaBudhaFx {

	static final double JARAK = 40.0D;
	static final double DEKAT_PANDANG = 6.0D;
	static final double LIHAT = 64.0D;

	private record Cahaya(double x, double y, double z, long tick, int panjang, long berakhir) {
	}

	private static final Map<Long, Cahaya> CAHAYA = new HashMap<>();
	private static @Nullable Cahaya kini;
	private static double jarakKini = Double.MAX_VALUE;

	private CahayaBudhaFx() {
	}

	static void terima(long situs, CompoundTag d, long now) {
		if (!d.getBooleanOr("nyala", true)) {
			CAHAYA.remove(situs);
			return;
		}
		long tick = d.getLongOr("tick", now);
		int panjang = Math.max(200, d.getIntOr("panjang", 2000));
		CAHAYA.put(situs, new Cahaya(d.getIntOr("x", 0) + 0.5D, d.getIntOr("y", 0) + 1.0D, d.getIntOr("z", 0) + 0.5D, tick, panjang, tick + panjang));
	}

	/** Preview at the player's feet for {@code panjang} ticks. */
	static void uji(double x, double y, double z, long now, int panjang) {
		CAHAYA.put(-1L, new Cahaya(x, y, z, now, panjang, now + panjang));
	}

	static boolean aktif() {
		return !CAHAYA.isEmpty();
	}

	static void bersihkan() {
		CAHAYA.clear();
		kini = null;
	}

	/** True when the local player stands at a lit glory's {@code pandang} point (the HUD caption). */
	static boolean dilihat() {
		return kini != null && jarakKini <= DEKAT_PANDANG;
	}

	static void tick(long now) {
		CAHAYA.values().removeIf(c -> c.berakhir() + 20L < now);
		Minecraft minecraft = Minecraft.getInstance();
		kini = null;
		jarakKini = Double.MAX_VALUE;
		if (minecraft.player == null) {
			return;
		}
		for (Cahaya c : CAHAYA.values()) {
			double d = Math.sqrt(minecraft.player.distanceToSqr(c.x(), c.y(), c.z()));
			if (d < LIHAT && d < jarakKini && now >= c.tick()) {
				kini = c;
				jarakKini = d;
			}
		}
	}

	static void gambar() {
		Cahaya c = kini;
		if (c == null) {
			return;
		}
		double now = Bingkai.sekarang();
		float env = Bingkai.selubung((float) (now - c.tick()), 0.0F, c.panjang(), 100.0F, 160.0F);
		// the glory needs sun on the clouds: none in rain, thinner as the morning grows bright overhead
		env *= 1.0F - Bingkai.hujan;
		if (env <= 0.01F) {
			return;
		}
		// anti-solar direction, held below the horizon onto the cloud sea
		float dx = -Bingkai.MATAHARI.x;
		float dy = -Bingkai.MATAHARI.y;
		float dz = -Bingkai.MATAHARI.z;
		float flat = Mth.sqrt(dx * dx + dz * dz);
		if (flat < 1.0E-3F) {
			dx = 1.0F;
			dz = 0.0F;
			flat = 1.0F;
		}
		float elev = (float) Math.atan2(dy, flat);
		elev = Mth.clamp(elev, -40.0F * Mth.DEG_TO_RAD, -6.0F * Mth.DEG_TO_RAD);
		float ce = Mth.cos(elev);
		float x = (float) (dx / flat * ce * JARAK);
		float y = (float) (Mth.sin(elev) * JARAK);
		float z = (float) (dz / flat * ce * JARAK);
		float t = Bingkai.t;
		float breathe = 0.85F + 0.15F * Mth.sin(t * 0.03F);
		// mist drifting across: a slow shimmer
		float mist = 0.75F + 0.25F * Mth.sin(t * 0.011F + 1.3F) * Mth.sin(t * 0.023F);
		float a = env * breathe * mist;
		int light = LightCoordsUtil.FULL_BRIGHT;
		float[] pelangi = FxTekstur.sel(FxTekstur.PELANGI);
		float[] cincin = FxTekstur.sel(FxTekstur.CINCIN);
		float[] glow = FxTekstur.sel(FxTekstur.GLOW);
		float[] siluet = FxTekstur.sel(FxTekstur.SILUET);
		int tier = Bingkai.tier;
		int need = tier == 0 ? 1 : tier == 1 ? 3 : 6;
		boolean bayangan = jarakKini <= DEKAT_PANDANG;
		if (!TemaFx.muat(need + (bayangan ? 1 : 0))) {
			return;
		}
		float spin = t * 0.0008F;
		if (tier >= 2) {
			// the wide pale fog-bow far outside, and the white core where the shadow stands
			TemaFx.fxAdd.billboard(x, y, z, 19.0F, 19.0F, -spin, Bingkai.KANAN, Bingkai.ATAS, cincin[0], cincin[1], cincin[2], cincin[3],
					Bingkai.argb(0.16F * a, 0xF4F6FF), light);
			TemaFx.fxAdd.billboard(x, y, z, 4.2F, 4.2F, 0.0F, Bingkai.KANAN, Bingkai.ATAS, glow[0], glow[1], glow[2], glow[3],
					Bingkai.argb(0.45F * a, 0xFFF8E8), light);
		}
		TemaFx.fxAdd.billboard(x, y, z, 8.0F, 8.0F, spin, Bingkai.KANAN, Bingkai.ATAS, pelangi[0], pelangi[1], pelangi[2], pelangi[3],
				Bingkai.argb(0.85F * a, 0xFFFFFF), light);
		if (tier >= 1) {
			TemaFx.fxAdd.billboard(x, y, z, 5.4F, 5.4F, -spin * 1.3F, Bingkai.KANAN, Bingkai.ATAS, cincin[0], cincin[1], cincin[2], cincin[3],
					Bingkai.argb(0.5F * a, 0xA488FF), light);
			TemaFx.fxAdd.billboard(x, y, z, 10.4F, 10.4F, spin * 0.7F, Bingkai.KANAN, Bingkai.ATAS, cincin[0], cincin[1], cincin[2], cincin[3],
					Bingkai.argb(0.42F * a, 0xFF6A58), light);
		}
		if (tier >= 2) {
			// second-order glory: fainter, wider, the colours repeating
			TemaFx.fxAdd.billboard(x, y, z, 12.6F, 12.6F, -spin * 0.5F, Bingkai.KANAN, Bingkai.ATAS, pelangi[0], pelangi[1], pelangi[2], pelangi[3],
					Bingkai.argb(0.3F * a, 0xFFFFFF), light);
		}
		if (bayangan) {
			Minecraft minecraft = Minecraft.getInstance();
			float sway = 0.0F;
			float lift = 0.0F;
			if (minecraft.player != null) {
				// the spectre copies the watcher: leaning with a sidestep, bobbing with a crouch
				sway = Mth.clamp((float) (minecraft.player.getDeltaMovement().x * Bingkai.KANAN.x + minecraft.player.getDeltaMovement().z * Bingkai.KANAN.z)
						* 4.0F, -0.35F, 0.35F);
				lift = minecraft.player.isCrouching() ? -0.5F : 0.0F;
			}
			float near = 1.0F - Bingkai.halus((float) ((jarakKini - 2.0D) / (DEKAT_PANDANG - 2.0D)));
			TemaFx.fxGlow.billboard(x, y + lift, z, 2.6F, 2.6F, sway, Bingkai.KANAN, Bingkai.ATAS, siluet[0], siluet[1], siluet[2], siluet[3],
					Bingkai.argb(0.62F * env * mist * near, 0x0A0C14), light);
		}
	}
}
