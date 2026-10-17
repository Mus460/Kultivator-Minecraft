package dev.kultivator.trial;

/**
 * Sub effects of the heart demon packets, shared by the server ({@link HeartDemons}, {@link HeartDemonEntity}) and the
 * client ({@code client.fx.TrialFx}).
 * <ul>
 *   <li>{@code FxType.HEART_DEMON_APPEAR}: a = one of the APPEAR_* / MOVE_* ids below, b = the demon's entity id;
 *   pos = where it happens, target = the second point (dash end, palm target, blink destination).</li>
 *   <li>{@code FxType.HEART_DEMON_DEFEAT}: a = one of the DEFEAT_* ids, b = the owner's entity id (the motes follow
 *   them); pos = the demon, target = the owner.</li>
 * </ul>
 */
public final class TrialFxIds {

	/** A pillar of black smoke and violet motes collapsing into the demon's shape. */
	public static final int APPEAR = 0;
	/** The dark qi dash: a streak of shadow from pos to target. */
	public static final int DASH = 1;
	/** The dark palm gathering in the demon's hand (about 16 ticks). */
	public static final int PALM_CHARGE = 2;
	/** The dark palm released from pos (the demon) into target (the owner). */
	public static final int PALM = 3;
	/** The demon blinks back near its owner: a puff at pos and one at target. */
	public static final int BLINK = 4;
	/** Personal warning shown only to the cultivator ten seconds before a pressure demon appears. */
	public static final int WARNING = 5;

	/** Won: the shadow shatters and its motes flow into the owner, golden flash. */
	public static final int DEFEAT_SHATTERED = 0;
	/** Lost: the shadow pours back into the owner's heart as black smoke. */
	public static final int DEFEAT_RETURNED = 1;
	/** Dispelled without a result: it simply dissolves. */
	public static final int DEFEAT_DISSOLVED = 2;

	private TrialFxIds() {
	}
}
