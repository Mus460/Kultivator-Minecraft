package dev.kultivator.client.sosok.kulit;

import com.mojang.blaze3d.platform.NativeImage;
import dev.kultivator.client.sekte.seragam.JubahLook;
import dev.kultivator.client.sekte.seragam.LambangCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The sect emblem on a Sosok atlas (DESIGN 40.3.8 step 1): the hall's robe embroidery sheet
 * ({@code JubahLook.embroidery}, a 64 x 64 {@code DynamicTexture} baked by {@code LambangAtlas}, read only, never
 * edited) carries the emblem already shrunk to 8 x 7 on the back of the body at (32, 20). It is copied onto the back
 * of the jacket (32, 36)-(40, 44) from Murid Inti or the Jiwa Baru Lahir realm on, and box-filtered to 3 x 3 on the
 * wearer's left breast of the jacket (25, 37) from Murid Dalam on. A sheet that is not a {@code DynamicTexture} (not
 * baked yet) paints nothing; the stamp in the cache key brings the emblem in as soon as the hall bakes it.
 */
public final class LambangSalin {

	/** What to stamp: the embroidery sheet, its bake stamp (cache key) and the two placements. */
	public record Stempel(Identifier tekstur, int stamp, boolean punggung, boolean dada) {
	}

	private static final int SUMBER_X = 32;
	private static final int SUMBER_Y = 20;
	private static final int SUMBER_W = 8;
	private static final int SUMBER_H = 7;
	private static final int PUNGGUNG_X = 32;
	private static final int PUNGGUNG_Y = 37;
	private static final int DADA_X = 25;
	private static final int DADA_Y = 37;

	/** The stamp of a uniform for a rank and realm, or null (no designed emblem, or below Murid Dalam and Jiwa Baru Lahir). */
	public static @Nullable Stempel untuk(@Nullable LambangCache.Entry seragam, int pangkat, int ranah) {
		if (seragam == null) {
			return null;
		}
		boolean punggung = pangkat >= 3 || ranah >= 4;
		boolean dada = pangkat >= 2;
		if (!punggung && !dada) {
			return null;
		}
		Identifier tekstur = JubahLook.embroidery(seragam);
		return tekstur == null ? null : new Stempel(tekstur, seragam.stamp, punggung, dada);
	}

	/** Paints the emblem of {@code s} onto a 128 x 128 ARGB atlas. */
	static void terapkan(int[] atlas, Stempel s) {
		AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(s.tekstur());
		if (!(tex instanceof DynamicTexture dyn)) {
			return;
		}
		NativeImage img = dyn.getPixels();
		if (img == null || img.getWidth() < SUMBER_X + SUMBER_W || img.getHeight() < SUMBER_Y + SUMBER_H) {
			return;
		}
		int[] patch = new int[SUMBER_W * SUMBER_H];
		for (int y = 0; y < SUMBER_H; y++) {
			for (int x = 0; x < SUMBER_W; x++) {
				patch[y * SUMBER_W + x] = img.getPixel(SUMBER_X + x, SUMBER_Y + y);
			}
		}
		if (s.punggung()) {
			for (int y = 0; y < SUMBER_H; y++) {
				for (int x = 0; x < SUMBER_W; x++) {
					int c = patch[y * SUMBER_W + x];
					int a = c >>> 24;
					if (a > 0) {
						int i = (PUNGGUNG_Y + y) * KulitPenyusun.ATLAS + PUNGGUNG_X + x;
						atlas[i] = Palet.campur(atlas[i], c & 0xFFFFFF, a);
					}
				}
			}
		}
		if (s.dada()) {
			for (int ty = 0; ty < 3; ty++) {
				for (int tx = 0; tx < 3; tx++) {
					int x0 = tx * SUMBER_W / 3;
					int x1 = (tx + 1) * SUMBER_W / 3;
					int y0 = ty * SUMBER_H / 3;
					int y1 = (ty + 1) * SUMBER_H / 3;
					long r = 0;
					long g = 0;
					long b = 0;
					long aSum = 0;
					int n = 0;
					for (int y = y0; y < y1; y++) {
						for (int x = x0; x < x1; x++) {
							int c = patch[y * SUMBER_W + x];
							int a = c >>> 24;
							r += (long) (c >> 16 & 0xFF) * a;
							g += (long) (c >> 8 & 0xFF) * a;
							b += (long) (c & 0xFF) * a;
							aSum += a;
							n++;
						}
					}
					if (aSum == 0 || n == 0 || aSum / n < 96) {
						continue;
					}
					int rgb = (int) (r / aSum) << 16 | (int) (g / aSum) << 8 | (int) (b / aSum);
					int i = (DADA_Y + ty) * KulitPenyusun.ATLAS + DADA_X + tx;
					atlas[i] = Palet.campur(atlas[i], rgb, 255);
				}
			}
		}
	}

	private LambangSalin() {
	}
}
