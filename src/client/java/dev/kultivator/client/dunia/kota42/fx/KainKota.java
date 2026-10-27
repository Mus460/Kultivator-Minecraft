package dev.kultivator.client.dunia.kota42.fx;

import net.minecraft.util.Mth;

/**
 * Cloth in the wind for the banners of the towns (DESIGN 42.9.3): a strip of {@code segmen} quads hanging from a top edge, every
 * row pushed along the cloth's normal by two travelling sine waves whose swing grows toward the free end (nothing at the top),
 * with a small sideways shear and a slow billow; drawn on both faces. Pure geometry into a {@link Geo.Sink} in camera-relative
 * coordinates; the caller owns the texture, the light and the budget. Directions: the cloth faces {@code (nx, nz)} (unit, the
 * titik's facing), its right edge as a viewer in front sees it is {@code (nz, -nx)}. Client thread only.
 */
final class KainKota {

	/**
	 * A hanging cloth.
	 *
	 * @param cx camera-relative x of the middle of the top edge ({@code cy}, {@code cz} likewise)
	 * @param nx unit normal x (the side the front faces), {@code nz} unit normal z
	 * @param lebar width in blocks, {@code tinggi} length down
	 * @param amp swing in blocks at the free end (0 = hangs still)
	 * @param angin a steady push of the free end along the normal in blocks (the wind's lean)
	 * @param fase per-cloth phase so neighbours never flap in step
	 * @param uv uv rect {u0, v0, u1, v1} of the whole cloth in the texture
	 */
	static void kain(Geo.Sink s, float cx, float cy, float cz, float nx, float nz, float lebar, float tinggi, int segmen, float waktu, float fase, float amp,
			float angin, float[] uv, int argb, int light) {
		float rx = nz * lebar * 0.5F;
		float rz = -nx * lebar * 0.5F;
		float[] ax = new float[segmen + 1];
		float[] ay = new float[segmen + 1];
		float[] az = new float[segmen + 1];
		float[] sh = new float[segmen + 1];
		for (int j = 0; j <= segmen; j++) {
			float f = j / (float) segmen;
			float swing = amp * f * Mth.sqrt(f) * (Mth.sin(2.1F * waktu * 0.1F + f * 3.3F + fase) + 0.45F * Mth.sin(3.7F * waktu * 0.1F + f * 5.9F + fase * 1.7F)
					+ 0.2F * Mth.sin(6.1F * waktu * 0.1F + f * 9.0F + fase * 0.4F));
			float push = angin * f * (0.6F + 0.4F * f);
			float off = swing + push;
			// the cloth is a little shorter the more it swings away from the vertical
			float drop = f * tinggi * (1.0F - 0.5F * Mth.clamp(Math.abs(off) / Math.max(tinggi, 0.01F), 0.0F, 0.4F) * Math.abs(off));
			ax[j] = cx + nx * off;
			ay[j] = cy - drop;
			az[j] = cz + nz * off;
			sh[j] = 0.06F * f * Mth.sin(1.3F * waktu * 0.1F + fase * 2.0F);
		}
		for (int j = 0; j < segmen; j++) {
			float v0 = uv[1] + (uv[3] - uv[1]) * j / segmen;
			float v1 = uv[1] + (uv[3] - uv[1]) * (j + 1) / segmen;
			float l0x = ax[j] - rx + nz * sh[j];
			float l0z = az[j] - rz - nx * sh[j];
			float r0x = ax[j] + rx + nz * sh[j];
			float r0z = az[j] + rz - nx * sh[j];
			float l1x = ax[j + 1] - rx + nz * sh[j + 1];
			float l1z = az[j + 1] - rz - nx * sh[j + 1];
			float r1x = ax[j + 1] + rx + nz * sh[j + 1];
			float r1z = az[j + 1] + rz - nx * sh[j + 1];
			s.quad(l0x, ay[j], l0z, l1x, ay[j + 1], l1z, r1x, ay[j + 1], r1z, r0x, ay[j], r0z, uv[0], v0, uv[2], v1, argb, light, true);
		}
	}

	/** A flat rectangle facing {@code (nx, nz)} centred at the point, {@code lebar} wide and {@code tinggi} high, optionally turned half a turn. */
	static void kotak(Geo.Sink s, float cx, float cy, float cz, float nx, float nz, float lebar, float tinggi, boolean putar, float[] uv, int argb, int light,
			boolean dua) {
		float rx = nz * lebar * 0.5F;
		float rz = -nx * lebar * 0.5F;
		float h = tinggi * 0.5F;
		float u0 = uv[0];
		float v0 = uv[1];
		float u1 = uv[2];
		float v1 = uv[3];
		if (putar) {
			float t = u0;
			u0 = u1;
			u1 = t;
			t = v0;
			v0 = v1;
			v1 = t;
		}
		s.quad(cx - rx, cy + h, cz - rz, cx - rx, cy - h, cz - rz, cx + rx, cy - h, cz + rz, cx + rx, cy + h, cz + rz, u0, v0, u1, v1, argb, light, dua);
	}

	/** A taut or sagging rope from a to b (camera-relative), {@code lendut} blocks of sag in the middle, drawn as two crossed ribbons. */
	static void tali(Geo.Sink s, float ax, float ay, float az, float bx, float by, float bz, float lendut, float tebal, int argb, int light) {
		int n = 10;
		float px = ax;
		float py = ay;
		float pz = az;
		for (int i = 1; i <= n; i++) {
			float f = i / (float) n;
			float x = ax + (bx - ax) * f;
			float z = az + (bz - az) * f;
			float y = ay + (by - ay) * f - lendut * 4.0F * f * (1.0F - f);
			// a vertical ribbon and a horizontal one, so the rope reads from every side
			s.quad(px, py + tebal, pz, px, py - tebal, pz, x, y - tebal, z, x, y + tebal, z, 0.0F, 0.0F, 1.0F, 1.0F, argb, light, true);
			float dx = x - px;
			float dz = z - pz;
			float len = Mth.sqrt(dx * dx + dz * dz);
			float sx = len > 1.0E-5F ? -dz / len * tebal : tebal;
			float sz = len > 1.0E-5F ? dx / len * tebal : 0.0F;
			s.quad(px - sx, py, pz - sz, px + sx, py, pz + sz, x + sx, y, z + sz, x - sx, y, z - sz, 0.0F, 0.0F, 1.0F, 1.0F, argb, light, true);
			px = x;
			py = y;
			pz = z;
		}
	}

	/** The point of the rope at fraction {@code f} (same curve as {@link #tali}): returns y; x and z are linear. */
	static float taliY(float ay, float by, float lendut, float f) {
		return ay + (by - ay) * f - lendut * 4.0F * f * (1.0F - f);
	}

	private KainKota() {
	}
}
