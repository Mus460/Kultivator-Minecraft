package dev.kultivator.harta;

/**
 * Sub-ids ({@code a}) of {@code FxType.TREASURE}, drawn by {@code client/fx/HartaFx} (DESIGN 19.2.11). Every effect is
 * particles only and scaled by the client's Effect Quality.
 */
public final class HartaFxIds {

	/** A spirit stone pops out of an ore: pos = the ore's centre, b = grade, f = count. */
	public static final int STONE_FOUND = 0;
	/** A spirit herb found in a plant: pos = the plant, b = {@code HerbKind} ordinal, f = age in years. */
	public static final int HERB_FOUND = 1;
	/** A beast core absorbed: pos = the player's feet, b = element ordinal (−1 none), f = grade. */
	public static final int CORE_ABSORB = 2;
	/** A spirit stone's qi absorbed at once: pos = the player's feet, b = grade. */
	public static final int STONE_ABSORB = 3;
	/** A core fed to a spirit beast: pos = the beast, target = the player, b = element ordinal, f = grade. */
	public static final int CORE_FEED = 4;
	/** A stone dissolves into a protective formation: pos = the lodestone's centre, b = grade. */
	public static final int FORMATION_FUEL = 5;
	/** A core dropped by a slain beast: pos = the drop, b = element ordinal, f = grade. */
	public static final int CORE_DROP = 6;
	/** Grey smoke of a rejected pill (Sumsum menolak, Pil Membalik): pos = the player's feet. */
	public static final int SMOKE = 7;
	/** A refining used catalysts: pos = the cauldron top, b = stones, f = the quality floor ordinal + 1 (0 none). */
	public static final int CATALYST = 8;

	private HartaFxIds() {
	}
}
