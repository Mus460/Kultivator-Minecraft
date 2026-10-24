package dev.kultivator.client.sosok.kulit;

/**
 * Colour maths of the Sosok composer (DESIGN 40.3.2-40.3.3, module 40K), all integer so Java and
 * {@code tools/sosok_lib.py} paint identical pixels: the 16 palette slots, the colour ramp ({@link #tangga}: shadows
 * shift cool, highlights warm, skin shadows stay warm), the cloth noise and the alpha blend.
 * <p>
 * The sRGB/linear lookup tables come from {@code resep.json} ({@code "tabel"}), written by the generator, so no float
 * {@code pow} difference between the two languages can change a pixel.
 */
public final class Palet {

	public static final int KULIT = 0;
	public static final int KULIT_RONA = 1;
	public static final int RAMBUT = 2;
	public static final int RAMBUT_KILAP = 3;
	public static final int MATA_PUTIH = 4;
	public static final int IRIS = 5;
	public static final int PUPIL = 6;
	public static final int ALIS = 7;
	public static final int BIBIR = 8;
	public static final int KAIN1 = 9;
	public static final int KAIN2 = 10;
	public static final int TEPI = 11;
	public static final int LOGAM = 12;
	public static final int GIOK = 13;
	public static final int SULAM = 14;
	public static final int SAMAK = 15;
	public static final int SLOT = 16;

	/** Sheet flags (blue channel). */
	public static final int F_SEAM = 1;
	public static final int F_NOISE = 2;
	public static final int F_SHINE = 4;
	public static final int F_TETAP = 8;
	/** One "tingkat" of tone. */
	public static final int STEP = 24;

	private static final int[] OFF_DINGIN = {2621, 1966, 5898};
	private static final int[] OFF_KULIT = {3932, 655, 0};
	private static final int[] OFF_HANGAT = {3277, 2621, 0};

	/**
	 * RGB of slot colour {@code rgb} at {@code tone} 0-255 (128 = the colour itself up to table rounding).
	 * {@code kulit}: the warm skin shadow of slots 0-1.
	 */
	public static int tangga(int rgb, int tone, boolean kulit, int[] lin, int[] srgb) {
		tone = Math.max(0, Math.min(255, tone));
		int out = 0;
		for (int i = 0; i < 3; i++) {
			int sh = 16 - 8 * i;
			int c = (rgb >> sh) & 255;
			int l = lin[c];
			int gelap = kulit ? l * 62 / 100 + OFF_KULIT[i] : l * 42 / 100 + OFF_DINGIN[i];
			if (gelap > 65535) {
				gelap = 65535;
			}
			int terang = l * 118 / 100 + OFF_HANGAT[i];
			if (terang > 65535) {
				terang = 65535;
			}
			int v = tone < 128 ? (gelap * (128 - tone) + l * tone) / 128 : (l * (256 - tone) + terang * (tone - 128)) / 128;
			out |= srgb[v >> 4] << sh;
		}
		return out;
	}

	/** The 16 x 256 ramp of a palette (index {@code slot * 256 + tone}). */
	public static int[] ramp(int[] palet, int[] lin, int[] srgb) {
		int[] table = new int[SLOT * 256];
		for (int s = 0; s < SLOT; s++) {
			boolean kulit = s <= KULIT_RONA;
			for (int t = 0; t < 256; t++) {
				table[s * 256 + t] = tangga(palet[s], t, kulit, lin, srgb);
			}
		}
		return table;
	}

	/** Cloth noise -4..4 (flag 2), the same integer hash as {@code sosok_lib.noise}. */
	public static int derau(int x, int y, int seed) {
		int h = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791);
		return ((h >>> 16) & 0xFFFF) % 9 - 4;
	}

	/** Integer "over" of colour {@code rgb} with coverage {@code a} onto ARGB {@code dst} ({@code sosok_lib.blend}). */
	public static int campur(int dst, int rgb, int a) {
		if (a >= 255) {
			return 0xFF000000 | rgb;
		}
		int da = (dst >>> 24) & 255;
		if (da == 0) {
			return (a << 24) | rgb;
		}
		int inv = 255 - a;
		int oa = a + (da * inv + 127) / 255;
		int den = oa * 255;
		int out = oa << 24;
		for (int sh = 16; sh >= 0; sh -= 8) {
			int s = (rgb >> sh) & 255;
			int d = (dst >> sh) & 255;
			int c = (s * a * 255 + d * da * inv + den / 2) / den;
			out |= Math.min(255, c) << sh;
		}
		return out;
	}

	/** rgb x (r, g, b) / 100 per channel, integer, clamped ({@code sosok_rupa.scale_rgb}). */
	public static int skala(int rgb, int r, int g, int b) {
		return Math.min(255, ((rgb >> 16) & 255) * r / 100) << 16 | Math.min(255, ((rgb >> 8) & 255) * g / 100) << 8
				| Math.min(255, (rgb & 255) * b / 100);
	}

	/** rgb + (r, g, b) per channel, clamped ({@code sosok_rupa.add_rgb}). */
	public static int tambah(int rgb, int r, int g, int b) {
		return Math.min(255, ((rgb >> 16) & 255) + r) << 16 | Math.min(255, ((rgb >> 8) & 255) + g) << 8 | Math.min(255, (rgb & 255) + b);
	}

	/** a + (b - a) * t / 100 per channel, integer with Python's rounding toward the source ({@code sosok_rupa.mix_rgb}). */
	public static int campurWarna(int a, int b, int t100) {
		int out = 0;
		for (int sh = 16; sh >= 0; sh -= 8) {
			int ca = (a >> sh) & 255;
			int cb = (b >> sh) & 255;
			int c = cb >= ca ? ca + (cb - ca) * t100 / 100 : ca - (ca - cb) * t100 / 100;
			out |= c << sh;
		}
		return out;
	}

	/** {@code "#RRGGBB"} or {@code "RRGGBB"} to an RGB int. */
	public static int hex(String s) {
		return Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16);
	}

	private Palet() {
	}
}
