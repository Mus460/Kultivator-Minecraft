package dev.kultivator.client.dunia.tempat.tema.fx;

import dev.kultivator.client.epic.EpicBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Malam Lentera Air of the Kota Air (DESIGN 38.T.6.4, 38.T.9.4): lotus lanterns drift down the canal lanes
 * ({@code TemaNet.SPLINE} lanes of kind {@code lentera}, else {@code perahu}) to the Danau Lentera and turn slowly on
 * the lake, 0 / 40 / 120 at a time (Minimal / Normal / Maximal), each living 600 ticks with a fade.
 * <p>
 * A lantern is stacked quads: two crossed petal sides ({@code teratai}), the petal ring seen from above ({@code atas}), a
 * candle flame and its warm glow (additive, so the canal glitters like the real festival's river) and a stretched
 * reflection on the water ({@code pantul}). Past 48 blocks only the glow and the reflection remain, past 128 nothing.
 * Each lantern turns 0.5 degrees per tick, bobs on the actual water surface (read from the fluid every 10 ticks) and
 * drifts 0.05 blocks per tick; at the end of its lane it circles on the lake.
 * <p>
 * Driven by {@code TemaNet.FESTIVAL acara=lentera_air} ({@code mulai}, {@code akhir}); a lantern a player releases is
 * {@code acara=lentera_lepas} ({@code x, y, z}, optional {@code harapan} sent only to its owner, who sees a gold halo
 * around it and his wish as a HUD caption). The shared 7b budget pays for the stream ({@code EpicBudget.takeQuads}).
 */
final class LenteraAirFx {

	static final int[] MAKS = {0, 40, 120};
	static final int UMUR = 600;
	static final float LAJU = 0.05F;
	static final float PUTAR = 0.5F * Mth.DEG_TO_RAD;

	private static final class Lentera {
		@Nullable TemaJalur.Jalur jalur;
		double s;
		float sisi;
		double x;
		double z;
		double y;
		double xo;
		double zo;
		float permukaan = Float.NaN;
		int umur;
		final int hidup;
		final float yaw0;
		final float fase;
		final boolean milik;
		// lake circling once the lane has ended
		double danauX;
		double danauZ;
		float danauR;
		boolean diDanau;

		Lentera(int hidup, float yaw0, float fase, boolean milik) {
			this.hidup = hidup;
			this.yaw0 = yaw0;
			this.fase = fase;
			this.milik = milik;
		}
	}

	private static final Map<Long, Long> FESTIVAL = new HashMap<>();
	private static final List<Lentera> LENTERA = new ArrayList<>();
	private static final Random RNG = new Random(380640L);
	private static final double[] TITIK = new double[4];
	private static final BlockPos.MutableBlockPos CEK = new BlockPos.MutableBlockPos();
	private static int jeda;

	private LenteraAirFx() {
	}

	/** The festival of site {@code situs} runs until game tick {@code akhir}. */
	static void mulai(long situs, long akhir) {
		FESTIVAL.put(situs, akhir);
	}

	static void akhir(long situs) {
		FESTIVAL.remove(situs);
	}

	/** A lantern released at (x, y, z); {@code milik}: the local player's own (gold halo). */
	static void lepas(double x, double y, double z, boolean milik) {
		Lentera l = new Lentera(UMUR, RNG.nextFloat() * Mth.TWO_PI, RNG.nextFloat() * Mth.TWO_PI, milik);
		TemaJalur.Jalur j = TemaJalur.terdekatJenis(x, z, 24.0D, "lentera");
		if (j == null) {
			j = TemaJalur.terdekatJenis(x, z, 24.0D, "perahu");
		}
		l.x = l.xo = x;
		l.z = l.zo = z;
		l.y = y;
		if (j != null) {
			l.jalur = j;
			l.s = j.terdekat(x, z);
			l.sisi = (RNG.nextFloat() - 0.5F) * 1.2F;
		} else {
			keDanau(l, x, z);
		}
		if (LENTERA.size() >= MAKS[2] + 8) {
			LENTERA.remove(0);
		}
		LENTERA.add(l);
	}

	static boolean aktif() {
		return !LENTERA.isEmpty() || !FESTIVAL.isEmpty();
	}

	static int jumlah() {
		return LENTERA.size();
	}

	static void bersihkan() {
		FESTIVAL.clear();
		LENTERA.clear();
	}

	private static void keDanau(Lentera l, double x, double z) {
		l.diDanau = true;
		l.danauR = 2.0F + RNG.nextFloat() * 9.0F;
		float a = RNG.nextFloat() * Mth.TWO_PI;
		l.danauX = x - Mth.cos(a) * l.danauR;
		l.danauZ = z - Mth.sin(a) * l.danauR;
	}

	// ================================================================ tick

	static void tick(long now) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		FESTIVAL.values().removeIf(akhir -> akhir < now);
		for (int i = LENTERA.size() - 1; i >= 0; i--) {
			Lentera l = LENTERA.get(i);
			l.umur++;
			if (l.umur >= l.hidup) {
				LENTERA.remove(i);
				continue;
			}
			gerak(level, l, now);
		}
		int tier = Math.max(0, Math.min(2, EpicBudget.tier()));
		int maks = MAKS[tier];
		if (FESTIVAL.isEmpty() || maks == 0 || --jeda > 0) {
			return;
		}
		jeda = Math.max(2, UMUR / Math.max(1, maks));
		if (LENTERA.size() >= maks) {
			return;
		}
		double px = minecraft.player.getX();
		double pz = minecraft.player.getZ();
		for (long situs : FESTIVAL.keySet()) {
			List<TemaJalur.Jalur> lanes = TemaJalur.jalur(situs, "lentera", "perahu");
			if (lanes.isEmpty()) {
				continue;
			}
			TemaJalur.Jalur j = lanes.get(RNG.nextInt(lanes.size()));
			if (j.jarakKe2(px, pz) > 160.0D * 160.0D) {
				continue;
			}
			Lentera l = new Lentera(UMUR - RNG.nextInt(120), RNG.nextFloat() * Mth.TWO_PI, RNG.nextFloat() * Mth.TWO_PI, false);
			l.jalur = j;
			// released from anywhere along the first two thirds of the lane, as townsfolk do from every bank
			l.s = RNG.nextDouble() * j.panjang() * 0.67D;
			l.sisi = (RNG.nextFloat() - 0.5F) * 1.6F;
			j.titik(l.s, TITIK);
			l.x = l.xo = TITIK[0];
			l.z = l.zo = TITIK[2];
			l.y = TITIK[1];
			LENTERA.add(l);
			return;
		}
	}

	private static void gerak(ClientLevel level, Lentera l, long now) {
		l.xo = l.x;
		l.zo = l.z;
		if (!l.diDanau && l.jalur != null) {
			l.s += LAJU;
			if (l.s >= l.jalur.panjang()) {
				keDanau(l, l.x, l.z);
			} else {
				l.jalur.titik(l.s, TITIK);
				float h = (float) TITIK[3];
				// sideways from the lane centre, with a slow meander
				float side = l.sisi + Mth.sin(l.umur * 0.02F + l.fase) * 0.35F;
				l.x = TITIK[0] - Mth.sin(h) * side;
				l.z = TITIK[2] + Mth.cos(h) * side;
				l.y = TITIK[1];
			}
		}
		if (l.diDanau) {
			float a = l.umur * (LAJU / Math.max(1.0F, l.danauR)) + l.fase;
			l.x = l.danauX + Mth.cos(a) * l.danauR;
			l.z = l.danauZ + Mth.sin(a) * l.danauR;
		}
		if (Float.isNaN(l.permukaan) || (l.umur + (int) (l.fase * 10.0F)) % 10 == 0) {
			l.permukaan = permukaan(level, l.x, l.y, l.z);
		}
	}

	/** The water surface height near (x, y, z): the lane's y block, one above or one below; else y + 0.9. */
	private static float permukaan(ClientLevel level, double x, double y, double z) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		int by = Mth.floor(y);
		for (int dy = 1; dy >= -2; dy--) {
			CEK.set(bx, by + dy, bz);
			FluidState fluid = level.getFluidState(CEK);
			if (!fluid.isEmpty()) {
				CEK.set(bx, by + dy + 1, bz);
				if (level.getFluidState(CEK).isEmpty()) {
					CEK.set(bx, by + dy, bz);
					return by + dy + fluid.getHeight(level, CEK);
				}
			}
		}
		return (float) (by + 0.9D);
	}

	// ================================================================ drawing

	static void gambar() {
		if (LENTERA.isEmpty()) {
			return;
		}
		float[] sisi = FxTekstur.sel(FxTekstur.TERATAI);
		float[] atas = FxTekstur.sel(FxTekstur.ATAS);
		float[] api = FxTekstur.sel(FxTekstur.API);
		float[] glow = FxTekstur.sel(FxTekstur.GLOW);
		float[] pantul = FxTekstur.sel(FxTekstur.PANTUL);
		float[] cincin = FxTekstur.sel(FxTekstur.CINCIN);
		float pt = Bingkai.pt;
		int light = LightCoordsUtil.FULL_BRIGHT;
		float malam = Bingkai.malam() ? 1.0F : 0.55F;
		for (Lentera l : LENTERA) {
			double wx = Mth.lerp(pt, l.xo, l.x);
			double wz = Mth.lerp(pt, l.zo, l.z);
			float base = Float.isNaN(l.permukaan) ? (float) l.y + 0.9F : l.permukaan;
			double d2 = Bingkai.jarak2(wx, base, wz);
			if (d2 > 128.0D * 128.0D) {
				continue;
			}
			boolean dekat = d2 < 48.0D * 48.0D;
			int need = dekat ? (l.milik ? 7 : 6) : 2;
			if (!TemaFx.muat(need) || !EpicBudget.takeQuads(need)) {
				return;
			}
			float age = l.umur + pt;
			float fade = Math.min(1.0F, age / 40.0F) * Math.min(1.0F, (l.hidup - age) / 120.0F);
			if (fade <= 0.01F) {
				continue;
			}
			float bob = Mth.sin(age * 0.11F + l.fase) * 0.035F;
			float x = (float) (wx - Bingkai.camX);
			float y = (float) (base + bob - Bingkai.camY);
			float z = (float) (wz - Bingkai.camZ);
			float yaw = l.yaw0 + age * PUTAR;
			float flick = 0.85F + 0.15F * Mth.sin(age * 0.9F + l.fase * 3.0F) * Mth.sin(age * 0.37F + l.fase);
			// reflection: stretched toward the camera's side, below everything else
			TemaFx.fxAdd.datar(x, y + 0.01F, z, 0.32F, 0.9F, (float) Math.atan2(z, x) + Mth.HALF_PI, pantul[0], pantul[1], pantul[2], pantul[3],
					Bingkai.argb(0.5F * fade * flick * malam, 0xFFB060), light);
			if (dekat) {
				int petal = Bingkai.argb(fade, Bingkai.redup(0xFFFFFF, 0.8F + 0.2F * malam));
				TemaFx.fxGlow.dinding(x, y + 0.24F, z, 0.32F, 0.24F, yaw, sisi[0], sisi[1], sisi[2], sisi[3], petal, light);
				TemaFx.fxGlow.dinding(x, y + 0.24F, z, 0.32F, 0.24F, yaw + Mth.HALF_PI, sisi[0], sisi[1], sisi[2], sisi[3], petal, light);
				TemaFx.fxGlow.datar(x, y + 0.2F, z, 0.27F, 0.27F, yaw, atas[0], atas[1], atas[2], atas[3], petal, light);
				TemaFx.fxAdd.billboard(x, y + 0.42F, z, 0.05F, 0.11F, 0.0F, Bingkai.KANAN, Bingkai.ATAS, api[0], api[1], api[2], api[3],
						Bingkai.argb(fade * flick, 0xFFE0A0), light);
			}
			TemaFx.fxAdd.billboard(x, y + 0.36F, z, 0.55F, 0.55F, 0.0F, Bingkai.KANAN, Bingkai.ATAS, glow[0], glow[1], glow[2], glow[3],
					Bingkai.argb(0.42F * fade * flick * malam, 0xFF9A50), light);
			if (dekat && l.milik) {
				float pulse = 0.6F + 0.4F * Mth.sin(age * 0.08F);
				TemaFx.fxAdd.datar(x, y + 0.02F, z, 0.75F, 0.75F, yaw * 0.3F, cincin[0], cincin[1], cincin[2], cincin[3],
						Bingkai.argb(0.55F * fade * pulse, 0xFFD870), light);
			}
		}
	}
}
