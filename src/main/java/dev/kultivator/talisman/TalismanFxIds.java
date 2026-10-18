package dev.kultivator.talisman;

/**
 * Sub-ids ({@code a}) of {@code FxType.TALISMAN}, drawn by {@code client/fx/TalismanFx} (DESIGN 19.2.11, 19.3.5,
 * 19.3.6, 19.3.11). {@code pos} is where it happens; the other arguments are listed per id.
 */
public final class TalismanFxIds {

	/** A talisman burns into glyphs as it is used; b = {@link TalismanKind} ordinal. */
	public static final int ACTIVATE = 0;
	/** Thunder Talisman bolt from pos to target; b = 1 for the chained hop. */
	public static final int THUNDER = 1;
	/** Fire Cloud cone from pos towards target (the cone's far centre); f = range. */
	public static final int FIRE_CLOUD = 2;
	/** Shield Talisman: a golden shell around pos; f = the player's height. */
	public static final int SHIELD = 3;
	/** Binding Talisman: glowing chains around the target at pos; f = its height. */
	public static final int BINDING = 4;
	/** Return Talisman channel: golden flames rising around pos; b = ticks done of the 100-tick channel. */
	public static final int RETURN_CHANNEL = 5;
	/** Return Talisman: the golden flame burst where the user left (b = 0) or arrived (b = 1). */
	public static final int RETURN_FLASH = 6;
	/** Tribulation Ward: a blue glowing slip over pos. */
	public static final int WARD = 7;
	/** Thousand Mile Talisman: wind streaks around pos. */
	public static final int THOUSAND_MILE = 8;
	/** Limpidity Talisman: a shimmer that fades the user out at pos. */
	public static final int LIMPIDITY = 9;
	/** Inscribing brush strokes at pos (the hand); b = {@link TalismanKind} ordinal. */
	public static final int INSCRIBE = 10;
	/** A finished inscription flashes; b = {@link TalismanKind} ordinal. */
	public static final int INSCRIBED = 11;
	/** A failed inscription burns blank and catalyst into ash at pos (items only). */
	public static final int ASH = 12;
	/** Jiwa Tak Hancur: the golden soul shatter and reform around pos. */
	public static final int SOUL = 13;
	/** A player star condenses over pos; b = the new star count. */
	public static final int STAR = 14;
	/** Immortal meridians open (Anugerah Kenaikan); b = the boon's layer. */
	public static final int BOON = 15;
	/** Perkokoh Fondasi result; b = new quality ordinal, or −1 for a failure. */
	public static final int REFORGE = 16;
	/** Vein compass search: a jade ping from pos towards target. */
	public static final int COMPASS = 17;
	/** A spirit stone in the offhand dissolves into meditation fuel; b = stone grade 0-3. */
	public static final int FUEL = 18;
	/** A cave abode is set at the seat at pos. */
	public static final int ABODE = 19;

	private TalismanFxIds() {
	}
}
