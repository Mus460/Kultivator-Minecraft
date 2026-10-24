package dev.kultivator.client.dunia.tempat.tema.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Kabut Gunung (DESIGN 38.T.9.4): white mountain mist 24-48 blocks around a player in the upper row of the Kota Gunung
 * in the morning and while it rains, on every Effect Quality. From {@code TemaNet.CAHAYA apa=kabut} ({@code x, y, z} with
 * {@code y} the lowest height of the upper row, optional {@code r} (160), optional {@code tick} + {@code panjang} to force
 * it; {@code nyala=false} forgets it).
 * <p>
 * Faked without touching the fog settings: rings of soft mist bands ({@code kabut.png}, soft at top and bottom) at 36
 * blocks (Minimal), 28 and 44 (Normal) or 24, 36 and 48 (Maximal) around the camera, each drifting at its own pace so the
 * layers slide past each other, plus on Maximal a dozen slow mist puffs ({@code kabut_puff}) wandering between them; the
 * HUD adds a faint white veil ({@link TemaHud}). Thicker in rain, rose-tinted at dawn, dim at night.
 */
final class KabutGunungFx {

	private record Kabut(double x, double y, double z, int r, long paksaDari, long paksaSampai) {
	}

	private static final Map<Long, Kabut> KABUT = new HashMap<>();
	private static @Nullable Kabut kini;
	private static float kuat;
	private static float kuatLalu;
	private static final float[][] PUFF = new float[12][4];
	private static boolean puffSiap;

	private KabutGunungFx() {
	}

	static void terima(long situs, CompoundTag d, long now) {
		if (!d.getBooleanOr("nyala", true)) {
			KABUT.remove(situs);
			return;
		}
		long dari = d.contains("panjang") ? d.getLongOr("tick", now) : Long.MAX_VALUE;
		long sampai = d.contains("panjang") ? dari + d.getIntOr("panjang", 0) : Long.MIN_VALUE;
		KABUT.put(situs, new Kabut(d.getIntOr("x", 0) + 0.5D, d.getIntOr("y", 0), d.getIntOr("z", 0) + 0.5D, Math.max(32, d.getIntOr("r", 160)),
				dari, sampai));
		TemaJalur.ingatPusat(situs, d);
	}

	static void uji(double x, double y, double z, long now, int panjang) {
		KABUT.put(-2L, new Kabut(x, y - 4.0D, z, 160, now, now + panjang));
	}

	static boolean aktif() {
		return !KABUT.isEmpty();
	}

	static void bersihkan() {
		KABUT.clear();
		kini = null;
		kuat = 0.0F;
		kuatLalu = 0.0F;
	}

	/** 0..1 mist at the camera (interpolated) for the HUD veil. */
	static float kuat(float partial) {
		return Mth.lerp(partial, kuatLalu, kuat);
	}

	static void tick(Minecraft minecraft, long now) {
		kuatLalu = kuat;
		kini = null;
		if (minecraft.player == null || KABUT.isEmpty()) {
			kuat = 0.0F;
			return;
		}
		double best = Double.MAX_VALUE;
		for (Kabut k : KABUT.values()) {
			double d = Mth.square(k.x() - minecraft.player.getX()) + Mth.square(k.z() - minecraft.player.getZ());
			if (d < Mth.square(k.r() + 32.0D) && d < best) {
				best = d;
				kini = k;
			}
		}
		if (kini == null) {
			kuat = Math.max(0.0F, kuat - 0.02F);
			return;
		}
		Kabut k = kini;
		float mood = LautAwanFx.suasana(k.paksaDari(), k.paksaSampai());
		float tinggi = Bingkai.halus((float) ((minecraft.player.getEyeY() - (k.y() - 6.0D)) / 6.0D));
		float tepi = 1.0F - Bingkai.halus((float) ((Math.sqrt(best) - k.r()) / 32.0D));
		float target = mood * tinggi * tepi * (0.75F + 0.25F * Bingkai.hujan);
		// eases in and out over a few seconds so walking up the stair the mist gathers rather than snaps
		kuat += (target - kuat) * 0.03F;
	}

	static void gambar() {
		float k = kuat(Bingkai.pt);
		if (k <= 0.01F) {
			return;
		}
		int tier = Bingkai.tier;
		float dawn = Bingkai.jam >= 22500L ? Bingkai.halus((Bingkai.jam - 22500L) / 900.0F) * (1.0F - Bingkai.halus((Bingkai.jam - 23400L) / 600.0F))
				: 0.0F;
		int warna = Bingkai.campur(0xF4F7FC, 0xFFE2D6, dawn * 0.6F);
		warna = Bingkai.redup(warna, 0.25F + 0.75F * Bingkai.terang);
		int bawah = Bingkai.redup(warna, 0.9F);
		int light = LightCoordsUtil.FULL_BRIGHT;
		float t = Bingkai.t;
		if (tier == 0) {
			ring(36.0F, 12, k * 0.55F, warna, bawah, t * 0.0009F, light);
		} else if (tier == 1) {
			ring(44.0F, 16, k * 0.5F, warna, bawah, t * 0.0006F, light);
			ring(28.0F, 14, k * 0.38F, warna, bawah, -t * 0.0011F + 0.41F, light);
		} else {
			ring(48.0F, 18, k * 0.5F, warna, bawah, t * 0.0005F, light);
			ring(36.0F, 16, k * 0.4F, warna, bawah, -t * 0.0008F + 0.23F, light);
			ring(24.0F, 14, k * 0.3F, warna, bawah, t * 0.0013F + 0.61F, light);
			puffs(k, warna, light);
		}
	}

	private static void ring(float r, int seg, float alpha, int atas, int bawah, float geser, int light) {
		Bentuk.cangkang(TemaFx.kabut, r, -9.0F - r * 0.1F, 8.0F + r * 0.2F, seg, Math.max(2.0F, r / 10.0F), geser,
				Bingkai.argb(alpha, atas), Bingkai.argb(alpha, bawah), light);
	}

	private static void puffs(float k, int warna, int light) {
		if (!puffSiap) {
			Random rng = new Random(380925L);
			for (float[] p : PUFF) {
				p[0] = rng.nextFloat() * Mth.TWO_PI; // angle
				p[1] = 26.0F + rng.nextFloat() * 20.0F; // radius
				p[2] = -4.0F + rng.nextFloat() * 9.0F; // height
				p[3] = 0.4F + rng.nextFloat() * 0.6F; // speed
			}
			puffSiap = true;
		}
		if (!TemaFx.muat(PUFF.length)) {
			return;
		}
		float[] sel = FxTekstur.sel(FxTekstur.KABUT_PUFF);
		float t = Bingkai.t;
		for (int i = 0; i < PUFF.length; i++) {
			float[] p = PUFF[i];
			float a = p[0] + t * 0.0004F * p[3];
			float r = p[1] + Mth.sin(t * 0.003F + i) * 4.0F;
			float x = Mth.cos(a) * r;
			float z = Mth.sin(a) * r;
			float y = p[2] + Mth.sin(t * 0.002F + i * 1.7F) * 1.5F;
			float size = 6.0F + p[3] * 6.0F;
			TemaFx.fxGlow.billboard(x, y, z, size, size * 0.6F, i * 0.7F + t * 0.0003F, Bingkai.KANAN, Bingkai.ATAS, sel[0], sel[1], sel[2], sel[3],
					Bingkai.argb(0.35F * k, warna), light);
		}
	}
}
