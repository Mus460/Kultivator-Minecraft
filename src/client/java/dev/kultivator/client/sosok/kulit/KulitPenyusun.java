package dev.kultivator.client.sosok.kulit;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * The Sosok skin composer (DESIGN 40.3.9, module 40K): paints a {@link SosokRupa} into one 128 x 128 atlas - the
 * 64 x 64 player skin (region A), 16 eye and 16 mouth strips of the animated face (B1/B2), the hair cube textures (C)
 * and the garment cube textures (D) - from the encoded part sheets ({@link BagianCache}) and the look's 16 slot
 * colours ({@link Palet}). A line-by-line port of {@code tools/sosok_lib.py susun}: the same layers, the same integer
 * ramp, noise and blend, so {@code /sosok dump} and {@code gen_sosok_pratinjau.py --benih} give identical pixels.
 * <p>
 * Works on a plain {@code int[]} (fast, no native calls in the loop), then copies the painted pixels into a
 * {@code NativeImage} once. Client thread only (sheets are read through the resource manager).
 */
public final class KulitPenyusun {

	public static final int ATLAS = 128;
	/** B1 origin: eye strip i at (64 + 8 (i mod 8), 4 (i div 8)), 8 x 4, face rows fy 2-5. */
	private static final int EYE_X = 64;
	private static final int EYE_Y = 0;
	/** B2 origin: mouth strip i at (64 + 8 (i mod 8), 8 + 3 (i div 8)), 8 x 3, face rows fy 5-7. */
	private static final int MOUTH_X = 64;
	private static final int MOUTH_Y = 8;
	/** The head front of the base layer. */
	private static final int FACE_X = 8;
	private static final int FACE_Y = 8;

	/** Composes {@code rupa} into a new 128 x 128 image (the caller owns and closes it). */
	public static NativeImage susun(SosokRupa rupa) {
		int[] px = susunPiksel(rupa);
		NativeImage img = new NativeImage(ATLAS, ATLAS, true);
		for (int y = 0; y < ATLAS; y++) {
			for (int x = 0; x < ATLAS; x++) {
				int v = px[y * ATLAS + x];
				if (v != 0) {
					img.setPixel(x, y, v);
				}
			}
		}
		return img;
	}

	/** The ARGB pixels of the atlas of {@code rupa} (row-major, 128 x 128). */
	public static int[] susunPiksel(SosokRupa rupa) {
		Resep r = Resep.get();
		int[] lin = r != null ? r.lin : TABEL_KOSONG_LIN;
		int[] srgb = r != null ? r.srgb : TABEL_KOSONG_SRGB;
		int[] ramp = Palet.ramp(rupa.palet(), lin, srgb);
		int seed = rupa.benih() & 0xFFFF;
		SosokRupa.Lapisan[] layers = rupa.lapisan();
		BagianCache.Lembar[] sheets = new BagianCache.Lembar[layers.length];
		for (int i = 0; i < layers.length; i++) {
			sheets[i] = BagianCache.get(layers[i].lembar());
		}
		int[] atlas = new int[ATLAS * ATLAS];
		for (int i = 0; i < layers.length; i++) {
			if (sheets[i] != null) {
				paint(atlas, ramp, sheets[i], layers[i], 0, 0, seed, false);
			}
		}
		// the face strips: recompose the head front with every eye state, then every mouth state
		int[] face = new int[ATLAS * ATLAS];
		int[] baseMouthRow = new int[8];
		for (int kind = 0; kind < 2; kind++) {
			for (int state = 0; state < 16; state++) {
				java.util.Arrays.fill(face, 0);
				int mata = kind == 0 ? state : 0;
				int mulut = kind == 1 ? state : 0;
				for (int i = 0; i < layers.length; i++) {
					if (sheets[i] != null) {
						paint(face, ramp, sheets[i], layers[i], mata, mulut, seed, true);
					}
				}
				if (kind == 0) {
					int ox = EYE_X + 8 * (state % 8);
					int oy = EYE_Y + 4 * (state / 8);
					for (int fy = 0; fy < 4; fy++) {
						for (int fx = 0; fx < 8; fx++) {
							atlas[(oy + fy) * ATLAS + ox + fx] = face[(FACE_Y + 2 + fy) * ATLAS + FACE_X + fx];
						}
					}
				} else {
					int ox = MOUTH_X + 8 * (state % 8);
					int oy = MOUTH_Y + 3 * (state / 8);
					if (state == 0) {
						for (int fx = 0; fx < 8; fx++) {
							baseMouthRow[fx] = face[(FACE_Y + 5) * ATLAS + FACE_X + fx];
						}
					}
					for (int fy = 0; fy < 3; fy++) {
						for (int fx = 0; fx < 8; fx++) {
							int v = face[(FACE_Y + 5 + fy) * ATLAS + FACE_X + fx];
							if (fy == 0 && (state == 0 || v == baseMouthRow[fx])) {
								v = 0;   // keep the eye strip's row 5 (blush, tears) where the mouth adds nothing
							}
							atlas[(oy + fy) * ATLAS + ox + fx] = v;
						}
					}
				}
			}
		}
		LambangSalin.Stempel lambang = rupa.lambang();
		if (lambang != null) {
			LambangSalin.terapkan(atlas, lambang);
		}
		return atlas;
	}

	/**
	 * Paints one layer onto {@code out} ({@code sosok_lib.paint_layer}). Face layers take their source column from the
	 * eye ({@code mata}) or mouth ({@code mulut}) state; {@code klipWajah} clips to the head front (the strip pass).
	 */
	private static void paint(int[] out, int[] ramp, BagianCache.Lembar sheet, SosokRupa.Lapisan l, int mata, int mulut, int seed, boolean klipWajah) {
		int sx;
		int sy;
		int w;
		int h;
		if (l.jenis() == SosokRupa.BIASA) {
			sx = l.sx();
			sy = l.sy();
			w = l.w();
			h = l.h();
		} else {
			sx = 8 * (l.jenis() == SosokRupa.MULUT ? mulut : mata);
			sy = 0;
			w = 8;
			h = 8;
		}
		int dx = l.dx();
		int dy = l.dy();
		int x0 = 0;
		int y0 = 0;
		int x1 = w;
		int y1 = h;
		if (klipWajah) {
			x0 = Math.max(0, FACE_X - dx);
			y0 = Math.max(0, FACE_Y - dy);
			x1 = Math.min(w, FACE_X + 8 - dx);
			y1 = Math.min(h, FACE_Y + 8 - dy);
		}
		int sw = sheet.w();
		int sh = sheet.h();
		int[] px = sheet.px();
		int[] tet = sheet.tetap();
		for (int y = y0; y < y1; y++) {
			int ty = dy + y;
			int syy = sy + y;
			if (syy >= sh || ty < 0 || ty >= ATLAS) {
				continue;
			}
			for (int x = x0; x < x1; x++) {
				int tx = dx + x;
				int sxx = sx + x;
				if (sxx >= sw || tx < 0 || tx >= ATLAS) {
					continue;
				}
				int p = px[syy * sw + sxx];
				int a = p >>> 24;
				if (a == 0) {
					continue;
				}
				int b = p & 0xFF;
				int rgb;
				if ((b & Palet.F_TETAP) != 0 && tet != null) {
					rgb = tet[syy * sw + sxx] & 0xFFFFFF;
				} else {
					int slot = Math.min(15, ((p >> 8 & 0xFF) + 8) >> 4);
					int t = p >> 16 & 0xFF;
					if ((b & Palet.F_SEAM) != 0) {
						t -= Palet.STEP;
					}
					if ((b & Palet.F_SHINE) != 0) {
						t += Palet.STEP;
					}
					if ((b & Palet.F_NOISE) != 0) {
						t += Palet.derau(tx, ty, seed);
					}
					t = t < 0 ? 0 : (t > 255 ? 255 : t);
					rgb = ramp[slot * 256 + t];
				}
				int i = ty * ATLAS + tx;
				out[i] = Palet.campur(out[i], rgb, a);
			}
		}
	}

	private static final int[] TABEL_KOSONG_LIN = new int[256];
	private static final int[] TABEL_KOSONG_SRGB = new int[4096];

	private KulitPenyusun() {
	}
}
