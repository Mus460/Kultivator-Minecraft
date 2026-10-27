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

	// ---- status tokens of the single kit (DESIGN 43.7; were TempatWarna OK / PERINGATAN / BAHAYA)
	public static final int OK = 0xFF5FA88C;
	public static final int PERINGATAN = 0xFFD0A040;
	public static final int BAHAYA = 0xFFB03030;
	/** "Locked" = 45 % ink. */
	public static final float TERKUNCI_ALFA = 0.45F;
	/** The demonic path's colour (sect sheets, DESIGN 41). */
	public static final int IBLIS = 0xFF6A1420;
	/** The town gate marker of the city map. */
	public static final int GERBANG = 0xFF8E1F1F;

	/** Quality colours by grade 0-4 (mutu) used for item names. */
	private static final int[] MUTU = {0xFF8A8F8C, 0xFF4F9A4A, 0xFF3E78C0, 0xFF8B4FC8, 0xFFD08A2A};
	private static final int[] MUTU_TERANG = {0xFFD8DCD8, 0xFFA6E08A, 0xFFA8D2FF, 0xFFD7B4FF, 0xFFFFD27A};

	/**
	 * One panel style of the single kit (DESIGN 43.7; the five styles of 38.U.2, ids of {@code LembarKunci.GAYA_*}): the
	 * ink, card and accent colours drawn on that paper. The Gulungan keeps one paper; a page or a context only changes these
	 * colours (43.3.1: no second backdrop), so a sheet's style still reads as wood, stone, cloth or jade.
	 */
	public enum Gaya {
		KERTAS(dev.kultivator.dunia.tempat.ui.LembarKunci.GAYA_KERTAS, INK, INK_SOFT, INK_FAINT, 0x30A08850, 0x60C9A24A, 0xFFF4ECD4, JADE_DARK,
				0xFFEFE4C6),
		KAYU(dev.kultivator.dunia.tempat.ui.LembarKunci.GAYA_KAYU, 0xFFF6E8C8, 0xFFE0C89A, 0xFFB89670, 0x40000000, 0x70F7DC7E, 0xFF4A140C,
				GOLD_LIGHT, 0xFF5E1A10),
		BATU(dev.kultivator.dunia.tempat.ui.LembarKunci.GAYA_BATU, INK, 0xFF39403C, 0xFF5E6662, 0x28FFFFFF, 0x60E8E8DE, 0xFFD4D4CA, 0xFF7A1A1A,
				0xFFC0C0B6),
		KAIN(dev.kultivator.dunia.tempat.ui.LembarKunci.GAYA_KAIN, 0xFFF4E6D0, 0xFFD8C0A8, 0xFFA88AA0, 0x40000000, 0x70D4A637, 0xFF2E0E22,
				GOLD_LIGHT, 0xFF3C1430),
		GIOK(dev.kultivator.dunia.tempat.ui.LembarKunci.GAYA_GIOK, 0xFF10261C, 0xFF2A4A3C, 0xFF5E7E70, 0x30184B36, 0x7067C99B, 0xFFE4F2EA,
				0xFF1E6A4A, 0xFFD2E8DC);

		public final String id;
		/** Body text. */
		public final int teks;
		/** Secondary text. */
		public final int teksLemah;
		/** Captions, hints. */
		public final int teksSamar;
		/** Card fill (translucent) and its hover tint. */
		public final int kartu;
		public final int kartuSorot;
		/** Inset well (detail pane, input). */
		public final int sumur;
		/** Accent for titles and selected rows. */
		public final int aksen;
		/** Solid back of the content area behind translucent cards. */
		public final int dasar;

		Gaya(String id, int teks, int teksLemah, int teksSamar, int kartu, int kartuSorot, int sumur, int aksen, int dasar) {
			this.id = id;
			this.teks = teks;
			this.teksLemah = teksLemah;
			this.teksSamar = teksSamar;
			this.kartu = kartu;
			this.kartuSorot = kartuSorot;
			this.sumur = sumur;
			this.aksen = aksen;
			this.dasar = dasar;
		}

		/** True for the dark styles (cream text). */
		public boolean gelap() {
			return this == KAYU || this == KAIN;
		}

		/** Text colour inside a {@link #sumur} well (wells are always light). */
		public int teksSumur() {
			return gelap() ? 0xFF2A1A12 : teks;
		}

		/** Green for gains on this paper. */
		public int naik() {
			return gelap() ? 0xFF9AE0B0 : 0xFF2E7A4E;
		}

		/** Red for losses and refusals on this paper. */
		public int turun() {
			return gelap() ? 0xFFFF8A7A : BAHAYA;
		}

		/** The style with id {@code id} ({@code fallback} when unknown). */
		public static Gaya dari(String id, Gaya fallback) {
			for (Gaya gaya : values()) {
				if (gaya.id.equals(id)) {
					return gaya;
				}
			}
			return fallback;
		}
	}

	/** Status colour of a {@code LembarKunci.PESAN_*} value. */
	public static int status(String jenis) {
		return switch (jenis) {
			case dev.kultivator.dunia.tempat.ui.LembarKunci.PESAN_BAHAYA -> BAHAYA;
			case dev.kultivator.dunia.tempat.ui.LembarKunci.PESAN_PERINGATAN -> PERINGATAN;
			default -> OK;
		};
	}

	/** District colour of a {@code LembarKunci.DISTRIK_*} id (jade green when unknown). */
	public static int distrik(String id) {
		return switch (id) {
			case dev.kultivator.dunia.tempat.ui.LembarKunci.DISTRIK_ISTANA -> 0xFFC9A24A;
			case dev.kultivator.dunia.tempat.ui.LembarKunci.DISTRIK_CENDEKIA -> 0xFF2A3A5C;
			case dev.kultivator.dunia.tempat.ui.LembarKunci.DISTRIK_AWAN -> 0xFF6FA8C8;
			case dev.kultivator.dunia.tempat.ui.LembarKunci.DISTRIK_GILDA -> 0xFF8C6A3A;
			case dev.kultivator.dunia.tempat.ui.LembarKunci.DISTRIK_PASAR -> 0xFFD08A2A;
			case dev.kultivator.dunia.tempat.ui.LembarKunci.DISTRIK_HUNIAN -> 0xFF7A5A3A;
			case dev.kultivator.dunia.tempat.ui.LembarKunci.DISTRIK_ARENA -> 0xFF7A1A1A;
			case dev.kultivator.dunia.tempat.ui.LembarKunci.DISTRIK_KUMUH -> 0xFF6A6A60;
			case dev.kultivator.dunia.tempat.ui.LembarKunci.DISTRIK_GERBANG -> GERBANG;
			case dev.kultivator.dunia.tempat.ui.LembarKunci.DISTRIK_LUAR -> 0xFF4F8A3A;
			default -> 0xFF5FA88C;
		};
	}

	/** Name colour of an item grade 0-4 on a light ({@code gelap} false) or dark panel; -1 = {@code polos}. */
	public static int mutu(int mutu, boolean gelap, int polos) {
		if (mutu < 0) {
			return polos;
		}
		int k = Math.min(MUTU.length - 1, mutu);
		return gelap ? MUTU_TERANG[k] : darken(MUTU[k], 0.1F);
	}

	/** Colour at "locked" strength (45 % of the ink over the panel). */
	public static int terkunci(int warna) {
		return argb(Math.round(255 * TERKUNCI_ALFA), warna & 0xFFFFFF);
	}

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
