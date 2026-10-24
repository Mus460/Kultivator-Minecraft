package dev.kultivator.client.dunia.tempat.tema.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Lautan Awan of the Kota Gunung (DESIGN 38.T.6, 38.T.9.4): a sea of clouds below the upper row of the mountain town in
 * the morning and while it rains, the view of Emei's Golden Summit. From {@code TemaNet.CAHAYA apa=awan} ({@code x, y, z}
 * with {@code y} the height of the cloud top, optional {@code r} the town radius (192), optional {@code tick} +
 * {@code panjang} to force it on whatever the hour, for the rope-blessing scene; {@code nyala=false} forgets it).
 * <p>
 * Two textured sheets ({@code awan.png}, the upper one bright and the lower one shadowed 3.5 blocks under it) around the
 * camera, world-anchored texture coordinates drifting slowly with the wind, a soft rim far out and a fade past the town
 * radius, tinted rose-gold at dawn and grey in rain. Drawn only while the camera is above the cloud top (from inside the
 * town's lower rows the mountain mist covers it); Minimal 1 sheet, Normal and Maximal 2 (Maximal finer).
 */
final class LautAwanFx {

	record Awan(double x, double y, double z, int r, long paksaDari, long paksaSampai) {
	}

	private static final Map<Long, Awan> AWAN = new HashMap<>();
	private static @Nullable Awan kini;

	private LautAwanFx() {
	}

	static void terima(long situs, CompoundTag d, long now) {
		if (!d.getBooleanOr("nyala", true)) {
			AWAN.remove(situs);
			return;
		}
		long dari = d.contains("panjang") ? d.getLongOr("tick", now) : Long.MAX_VALUE;
		long sampai = d.contains("panjang") ? dari + d.getIntOr("panjang", 0) : Long.MIN_VALUE;
		AWAN.put(situs, new Awan(d.getIntOr("x", 0) + 0.5D, d.getIntOr("y", 0) + 0.0D, d.getIntOr("z", 0) + 0.5D,
				Math.max(48, d.getIntOr("r", 192)), dari, sampai));
		TemaJalur.ingatPusat(situs, d);
	}

	static void uji(double x, double y, double z, long now, int panjang) {
		AWAN.put(-2L, new Awan(x, y, z, 192, now, now + panjang));
	}

	static boolean aktif() {
		return !AWAN.isEmpty();
	}

	static void bersihkan() {
		AWAN.clear();
		kini = null;
	}

	/** The nearest known cloud sea within its town radius + 96 blocks, for {@link KabutGunungFx} and the HUD. */
	static @Nullable Awan kini() {
		return kini;
	}

	static void tick(long now) {
		Minecraft minecraft = Minecraft.getInstance();
		kini = null;
		if (minecraft.player == null) {
			return;
		}
		double best = Double.MAX_VALUE;
		for (Awan a : AWAN.values()) {
			double d = Mth.square(a.x() - minecraft.player.getX()) + Mth.square(a.z() - minecraft.player.getZ());
			if (d < Mth.square(a.r() + 96.0D) && d < best) {
				best = d;
				kini = a;
			}
		}
	}

	/** 0..1: how much the morning-or-rain condition holds now (soft at the edges of the morning window). */
	static float suasana(long paksaDari, long paksaSampai) {
		double now = Bingkai.sekarang();
		if (now >= paksaDari && now <= paksaSampai) {
			return Bingkai.selubung((float) (now - paksaDari), 0.0F, (float) (paksaSampai - paksaDari), 60.0F, 80.0F);
		}
		// morning 22500 .. 2500, fading 600 ticks at each end
		long j = Bingkai.jam;
		float pagi;
		if (j >= 22500L) {
			pagi = Bingkai.halus((j - 22500L) / 600.0F);
		} else if (j <= 2500L) {
			pagi = Bingkai.halus((2500L - j) / 600.0F);
		} else {
			pagi = 0.0F;
		}
		return Math.max(pagi, Bingkai.halus((Bingkai.hujan - 0.1F) / 0.4F));
	}

	static void gambar() {
		Awan a = kini;
		if (a == null) {
			return;
		}
		float mood = suasana(a.paksaDari(), a.paksaSampai());
		if (mood <= 0.01F) {
			return;
		}
		float above = (float) (Bingkai.camY - a.y());
		float diatas = Bingkai.halus((above - 1.0F) / 4.0F);
		if (diatas <= 0.01F) {
			return;
		}
		double dTown = Math.sqrt(Bingkai.jarakDatar2(a.x(), a.z()));
		float town = 1.0F - Bingkai.halus((float) ((dTown - a.r()) / 96.0D));
		float alpha = mood * diatas * town;
		if (alpha <= 0.01F) {
			return;
		}
		// dawn rose-gold, daylight white, rain grey
		long j = Bingkai.jam;
		float dawn = j >= 22500L ? Bingkai.halus((j - 22500L) / 900.0F) * (1.0F - Bingkai.halus((j - 23400L) / 600.0F))
				: j < 1200L ? 1.0F - Bingkai.halus(j / 1200.0F) : 0.0F;
		int warna = Bingkai.campur(0xFFFFFF, 0xFFD2B4, dawn * 0.7F);
		warna = Bingkai.campur(warna, 0x9AA2B0, Bingkai.hujan * 0.8F);
		warna = Bingkai.redup(warna, 0.35F + 0.65F * Bingkai.terang);
		int bawah = Bingkai.redup(Bingkai.campur(warna, 0x8090B0, 0.35F), 0.85F);
		int light = LightCoordsUtil.FULL_BRIGHT;
		float t = Bingkai.t;
		int tier = Bingkai.tier;
		int n = tier == 0 ? 6 : tier == 1 ? 8 : 10;
		float tile = tier == 2 ? 26.0F : 32.0F;
		float y = (float) (a.y() - Bingkai.camY);
		if (tier >= 1) {
			Bentuk.lembar(TemaFx.awan, 0.0F, y - 3.5F, 0.0F, Bingkai.camX, Bingkai.camZ, n, tile, 96.0F, t * 0.00006F + 0.37F, t * 0.00003F + 0.11F,
					bawah, 0.7F * alpha, 0.45F, light);
		}
		Bentuk.lembar(TemaFx.awan, 0.0F, y, 0.0F, Bingkai.camX, Bingkai.camZ, n, tile, 72.0F, t * 0.00012F, t * 0.00005F, warna,
				0.9F * alpha, 0.5F, light);
	}
}
