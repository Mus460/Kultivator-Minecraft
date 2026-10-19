package dev.kultivator.tempa;

/** Sub-ids ({@code a}) of {@code FxType.TEMPA} (DESIGN 25.2 item 4), owned by P8-tempa. */
public final class TempaFxIds {

	/** A forging job finished: b = 1 success, 0 failure ("Leburan gagal"); f = the forge heat. */
	public static final int FORGE_DONE = 0;
	/** A weapon-spirit strike from pos to target. */
	public static final int SPIRIT_STRIKE = 1;
	/** Payung absorption: b = grade, f = radius. */
	public static final int PAYUNG_SHELL = 2;
	/** Wajan spirit block at the player. */
	public static final int WAJAN_BLOCK = 3;
	/** Lonceng chime ring: b = grade, f = radius. */
	public static final int BELL_CHIME = 4;
	/** The natal treasure enters the body: b = form index, f = tint rgb. */
	public static final int NATAL_BOND = 5;
	/** A refinement ended: b = 1 success, 0 failure. */
	public static final int REFINE_DONE = 6;
	/** A ring takes a spiritual imprint: b = tier. */
	public static final int RING_BOND = 7;
	/** Fire over a working forge (every second of a channel): b = heat. */
	public static final int FORGE_WORK = 8;
	/** The Heavenvoid Cauldron's lid opens. */
	public static final int KUALI_OPEN = 9;
	/** The weapon spirit awakens: b = stage, f = tint rgb. */
	public static final int SPIRIT_AWAKEN = 10;

	private TempaFxIds() {
	}
}
