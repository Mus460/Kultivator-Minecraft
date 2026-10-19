package dev.kultivator.alam;

/**
 * Sub-ids ({@code a}) of {@code FxType.ALAM} (DESIGN 25.2 item 4), owned by P8-alam. {@code b} and {@code f} are per id;
 * {@code tx, ty, tz} is a second point where an id says so.
 */
public final class AlamFxIds {

	/** The gate of glyph rings during the omen: b = realm ordinal 1-4, f = seconds of the omen. */
	public static final int GATE = 0;
	/** The 1 s fade of the Formasi Teleportasi, sent to one party member: b = realm ordinal. */
	public static final int FADE = 1;
	/** A telegraph ring on the ground: b = ticks until it strikes, f = radius; tx = rgb as a double. */
	public static final int TELEGRAPH = 2;
	/** A lava jet bursting from the lane: f = radius. */
	public static final int JET = 3;
	/** An ice crystal being absorbed: b = progress 0-100 (100 = absorbed). */
	public static final int CRYSTAL = 4;
	/** A phantom exit shattering under the player's feet. */
	public static final int PHANTOM_EXIT = 5;
	/** A chamber or run cleared: b = realm ordinal, f = radius. */
	public static final int CLEAR = 6;
	/** Divine Sense "ada qi" on a stone gate (sent to the sensing player only). */
	public static final int QI_MARK = 7;
	/** Clear Sight / Divine Sense marks the true maze exit (sent to the seeing player only). */
	public static final int TRUE_EXIT = 8;
	/** The glyph pad of the next chamber appears: b = realm ordinal. */
	public static final int PAD = 9;
	/** A stone gate opens: b = 0 empty, 1 trap, 2 treasure. */
	public static final int GATE_OPEN = 10;

	private AlamFxIds() {
	}
}
