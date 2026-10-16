package dev.kultivator.client.screen.ui;

/**
 * Colours of the "Gulungan Giok" (jade scroll) theme, plus small colour helpers. Everything is ARGB. The theme is a
 * pale jade parchment on a dark ink-blue night, with gold borders, red seals, paper-yellow talismans and jade stones.
 */
public final class Palette {

	// ---- night backdrop
	public static final int NIGHT = 0xFF0A1322;
	public static final int NIGHT_MIST = 0xFF1A2B44;
	public static final int MOUNTAIN = 0xFF14233A;

	// ---- jade parchment
	public static final int PAPER_TOP = 0xFFE6EDDB;
	public static final int PAPER_BOTTOM = 0xFFCADBC4;
	public static final int PAPER_SHADE = 0xFFB4C9AE;
	public static final int PAPER_DEEP = 0xFFA4BC9E;
	public static final int CLOUD = 0x30406F5E;

	// ---- gold
	public static final int GOLD = 0xFFD4A637;
	public static final int GOLD_DARK = 0xFF8A671B;
	public static final int GOLD_LIGHT = 0xFFF7DC7E;

	// ---- ink
	public static final int INK = 0xFF1B2521;
	public static final int INK_SOFT = 0xFF465950;
	public static final int INK_FAINT = 0xFF7A8C83;

	// ---- jade stone
	public static final int JADE = 0xFF2E8963;
	public static final int JADE_LIGHT = 0xFF67C99B;
	public static final int JADE_DARK = 0xFF184B36;
	public static final int JADE_PALE = 0xFFB6E3CB;
	public static final int JADE_DULL = 0xFF7E9187;

	// ---- talisman paper and seal
	public static final int TALISMAN = 0xFFF1D57E;
	public static final int TALISMAN_LOW = 0xFFE2BE5E;
	public static final int TALISMAN_EDGE = 0xFFB98F34;
	public static final int SEAL = 0xFFB3281D;
	public static final int SEAL_GLOW = 0xFFFF5B3A;
	public static final int SEAL_DARK = 0xFF6E160F;

	// ---- lacquered scroll rods
	public static final int ROD = 0xFF45230F;
	public static final int ROD_LIGHT = 0xFF7E4A27;
	public static final int ROD_DARK = 0xFF26120A;

	// ---- bamboo slips
	public static final int BAMBOO = 0xFFDCC783;
	public static final int BAMBOO_ALT = 0xFFD0B86F;
	public static final int BAMBOO_KNOT = 0xFF94783A;
	public static final int CORD = 0xFF5B3B22;

	// ---- meaning
	public static final int QI = 0xFF47B2FF;
	public static final int QI_LIGHT = 0xFFB4E2FF;
	public static final int STORED = 0xFFAE6CFF;
	public static final int STORED_LIGHT = 0xFFDCC2FF;
	public static final int DANGER = 0xFFD5322A;
	public static final int WARN = 0xFFE5821C;
	public static final int LOCKED = 0xFF8D928E;
	public static final int LOCKED_PAPER = 0xFFC9CDC6;

	/** Opaque colour from RGB. */
	public static int opaque(int rgb) {
		return 0xFF000000 | (rgb & 0xFFFFFF);
	}

	/** RGB with an alpha of 0..255. */
	public static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	/** Multiplies the colour's alpha by {@code factor} (0..1). */
	public static int fade(int color, float factor) {
		int alpha = Math.round((color >>> 24) * Math.max(0.0F, Math.min(1.0F, factor)));
		return (alpha << 24) | (color & 0xFFFFFF);
	}

	/** Blends two ARGB colours channel by channel (alpha too); {@code t} 0 gives {@code from}, 1 gives {@code to}. */
	public static int mix(int from, int to, float t) {
		float k = Math.max(0.0F, Math.min(1.0F, t));
		int result = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int a = (from >>> shift) & 0xFF;
			int b = (to >>> shift) & 0xFF;
			result |= (Math.round(a + (b - a) * k) & 0xFF) << shift;
		}
		return result;
	}

	/** Brighter (towards white) by {@code t}, alpha kept. */
	public static int lighten(int color, float t) {
		return (color & 0xFF000000) | (mix(color, 0xFFFFFFFF, t) & 0xFFFFFF);
	}

	/** Darker (towards black) by {@code t}, alpha kept. */
	public static int darken(int color, float t) {
		return (color & 0xFF000000) | (mix(color, 0xFF000000, t) & 0xFFFFFF);
	}

	/**
	 * A version of a (possibly very light) RGB colour that reads well as ink on the pale parchment: light colours are
	 * pulled towards the ink colour until they are dark enough.
	 */
	public static int onPaper(int rgb) {
		int color = opaque(rgb);
		float luminance = luminance(color);
		if (luminance <= 0.42F) {
			return color;
		}
		float t = Math.min(0.85F, (luminance - 0.42F) / Math.max(0.01F, luminance) + 0.15F);
		return mix(color, INK, t);
	}

	/** 0..1 perceived brightness. */
	public static float luminance(int color) {
		float r = ((color >> 16) & 0xFF) / 255.0F;
		float g = ((color >> 8) & 0xFF) / 255.0F;
		float b = (color & 0xFF) / 255.0F;
		return 0.299F * r + 0.587F * g + 0.114F * b;
	}

	/** Grey version of a colour (locked, disabled), alpha kept. */
	public static int grey(int color, float amount) {
		int l = Math.round(luminance(color) * 255.0F);
		int grey = (color & 0xFF000000) | (l << 16) | (l << 8) | l;
		return mix(color, grey, amount);
	}

	private Palette() {
	}
}
