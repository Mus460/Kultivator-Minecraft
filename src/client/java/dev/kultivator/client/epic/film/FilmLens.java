package dev.kultivator.client.epic.film;

/**
 * The lens and HUD layers a film shot asks for (DESIGN 30.2.7; the shot-list abbreviations of 30.3). Layers combine as
 * a bit mask; {@code client/epic/lens/LensLayers} draws them inside the budgets of 30.2.13 and the "Lapisan lensa"
 * setting. Letterbox and vignette are on for every film shot.
 */
public enum FilmLens {
	/** LB: the 2.39:1 bars. */
	LETTERBOX,
	/** GR: film grain (Normal+). */
	GRAIN,
	/** VG: the vignette in the shot's tint. */
	VIGNETTE,
	/** FR: chromatic fringe (Normal+). */
	FRINGE,
	/** FL: lens flare on the shot's light point (Normal+: 3 ghosts, Maximal 6). */
	FLARE,
	/** AN: the anamorphic streak through the light point (Maximal). */
	ANAMORPHIC,
	/** LL: a light leak sliding in from an edge (Normal+). */
	LIGHT_LEAK,
	/** SM: the whip smear (every quality; Minimal 4 strokes). */
	SMEAR,
	/** SL: speed lines. */
	SPEED_LINES,
	/** HZ: heat-haze quads over hot ground (Normal+: 2, Maximal 4). */
	HEAT_HAZE,
	/** IF: the shot cuts under the climax's impact frames (played by Dampak). */
	IMPACT,
	/** TC: the technique's title card. */
	TITLE;

	public int bit() {
		return 1 << ordinal();
	}

	public static int mask(FilmLens... lenses) {
		int mask = 0;
		for (FilmLens lens : lenses) {
			mask |= lens.bit();
		}
		return mask;
	}

	public static boolean has(int mask, FilmLens lens) {
		return (mask & lens.bit()) != 0;
	}
}
