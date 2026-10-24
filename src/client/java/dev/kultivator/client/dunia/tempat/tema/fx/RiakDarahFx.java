package dev.kultivator.client.dunia.tempat.tema.fx;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Riak Kolam Darah, the ripples of the blood pool before Xue Yao's throne in the Istana Darah of the Kota Iblis
 * (DESIGN 38.T.7, 38.T.9.4): the pool is only red concrete under red stained glass with hidden shroomlights (no fluid,
 * no redstone), and these additive rings make it look alive: red rings ({@code riak}, sharp with caustic breaks) grow
 * from the centre and fade, a soft rim ring ({@code cincin}) trails each on Maximal, and a faint red glow lies on the
 * surface. 0 / 1 / 3 rings (Minimal / Normal / Maximal), within 48 blocks.
 * <p>
 * From {@code TemaNet.CAHAYA apa=riak} ({@code x, y, z} the centre, {@code y} the block of the glass layer so the
 * surface is {@code y + 1}; optional {@code r} radius (5), {@code kuat} 0..1 (the boss's ring waves set 1 and the
 * rings come twice as fast); {@code nyala=false} stills the pool). During Pesta Darah ({@code FESTIVAL pesta_darah}) the
 * rings beat faster and brighter.
 */
final class RiakDarahFx {

	private record Kolam(double x, double y, double z, float r, float kuat) {
	}

	private static final Map<Long, Kolam> KOLAM = new HashMap<>();
	private static boolean pesta;

	private RiakDarahFx() {
	}

	static void terima(long situs, CompoundTag d) {
		if (!d.getBooleanOr("nyala", true)) {
			KOLAM.remove(situs);
			return;
		}
		KOLAM.put(situs, new Kolam(d.getIntOr("x", 0) + 0.5D, d.getIntOr("y", 0) + 1.0D, d.getIntOr("z", 0) + 0.5D,
				Mth.clamp(d.getFloatOr("r", 5.0F), 1.0F, 16.0F), Mth.clamp(d.getFloatOr("kuat", 0.0F), 0.0F, 1.0F)));
	}

	static void uji(double x, double y, double z) {
		KOLAM.put(-4L, new Kolam(x, y, z, 5.0F, 0.0F));
	}

	/** Pesta Darah on or off (from {@link TemaFestival}). */
	static void pesta(boolean on) {
		pesta = on;
	}

	static boolean aktif() {
		return !KOLAM.isEmpty();
	}

	static void bersihkan() {
		KOLAM.clear();
		pesta = false;
	}

	static void tick(long now) {
		// nothing ages: the pool keeps rippling while its town is near; the payload removes it
	}

	static void gambar() {
		int tier = Bingkai.tier;
		if (tier == 0) {
			return;
		}
		int rings = tier == 1 ? 1 : 3;
		float[] riak = FxTekstur.sel(FxTekstur.RIAK);
		float[] cincin = FxTekstur.sel(FxTekstur.CINCIN);
		float[] glow = FxTekstur.sel(FxTekstur.GLOW);
		int light = LightCoordsUtil.FULL_BRIGHT;
		for (Kolam k : KOLAM.values()) {
			if (Bingkai.jarak2(k.x(), k.y(), k.z()) > 48.0D * 48.0D) {
				continue;
			}
			int need = rings * (tier >= 2 ? 2 : 1) + 1;
			if (!TemaFx.muat(need)) {
				return;
			}
			float x = (float) (k.x() - Bingkai.camX);
			float y = (float) (k.y() - Bingkai.camY) + 0.015F;
			float z = (float) (k.z() - Bingkai.camZ);
			float speed = (pesta ? 1.6F : 1.0F) * (1.0F + k.kuat());
			float period = 80.0F / speed;
			float bright = pesta ? 1.25F : 1.0F;
			float pulse = 0.7F + 0.3F * Mth.sin(Bingkai.t * 0.05F * speed);
			TemaFx.fxAdd.datar(x, y, z, k.r() * 1.1F, k.r() * 1.1F, 0.0F, glow[0], glow[1], glow[2], glow[3],
					Bingkai.argb(0.22F * pulse * bright, 0xB01020), light);
			for (int i = 0; i < rings; i++) {
				float ph = Mth.positiveModulo(Bingkai.t / period + i / (float) rings, 1.0F);
				float rad = 0.3F + ph * k.r();
				float a = (float) Math.pow(1.0F - ph, 1.5D) * Math.min(1.0F, ph * 8.0F) * bright;
				float yaw = i * 1.9F + Bingkai.t * 0.002F;
				TemaFx.fxAdd.datar(x, y + i * 0.002F, z, rad, rad, yaw, riak[0], riak[1], riak[2], riak[3], Bingkai.argb(0.85F * a, 0xFF2030), light);
				if (tier >= 2) {
					float trail = rad * 0.86F;
					TemaFx.fxAdd.datar(x, y + 0.001F, z, trail, trail, -yaw, cincin[0], cincin[1], cincin[2], cincin[3],
							Bingkai.argb(0.35F * a, 0xC0102A), light);
				}
			}
		}
	}
}
