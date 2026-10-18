package dev.kultivator.elite;

/**
 * Sub-ids ({@code a}) of {@code FxType.ELITE}, drawn by {@code client/fx/EliteFx} (DESIGN 19.2.11, 19.3.1, 19.3.8).
 * Common code: the client reads the durations from here so both sides agree.
 * <ul>
 * <li>Elite telegraphs and impacts: {@code pos} = the centre (or the elite for a breath), {@code target} = the cone tip
 * (breath) or the landing spot (blink), {@code b} = the elite's entity id (the client reads its element colour from the
 * synced attachment), {@code f} = the radius.</li>
 * <li>Tribulation rings: {@code a} = {@link #TRIB_RING} + variant (0 Overworld, 1 Nether, 2 End), {@code pos} = the ring
 * centre, {@code b} = the strike number (+{@link #TWIN_OFFSET} for a twin ring), {@code f} = the radius.</li>
 * <li>Graze arcs and Hu Fa threads: {@code pos} → {@code target}, {@code b} = the tribulation variant.</li>
 * </ul>
 */
public final class CombatFxIds {

	/** Tanda Langit: a ring under the target (lasts {@link #MARK_TICKS}). */
	public static final int MARK = 1;
	/** Napas Es: a cone from the elite towards {@code target} (lasts {@link #WINDUP_TICKS}). */
	public static final int BREATH = 2;
	/** Hentakan Bumi: a ring around the elite (lasts {@link #WINDUP_TICKS}). */
	public static final int STOMP = 3;
	/** Kedipan: the elite gathers itself (lasts {@link #BLINK_TICKS}). */
	public static final int BLINK = 4;
	/** Duri Racun: a ring of thorns around the elite (lasts {@link #WINDUP_TICKS}). */
	public static final int THORNS = 5;

	/** Impacts of the skills above. */
	public static final int MARK_HIT = 11;
	public static final int BREATH_HIT = 12;
	public static final int STOMP_HIT = 13;
	/** The elite lands behind its target: {@code pos} where it left, {@code target} where it arrived. */
	public static final int BLINK_HIT = 14;
	public static final int THORNS_HIT = 15;
	/** The Lightning passive: a small particle bolt from the elite ({@code pos}) to its victim ({@code target}). */
	public static final int BOLT = 16;
	/** An elite was made (a gathering spiral on the mob). */
	public static final int BIRTH = 17;

	/** Tribulation lightning ring; add the variant (20, 21, 22). Lasts {@link #RING_TICKS}. */
	public static final int TRIB_RING = 20;
	/** {@code b} offset of the second ring of a twin strike. */
	public static final int TWIN_OFFSET = 1000;
	/** A grazing strike: the bolt hits the ring centre ({@code pos}) and arcs to the player ({@code target}). */
	public static final int GRAZE = 25;
	/** A Hu Fa guardian's gold qi thread from the guardian ({@code pos}) to the cultivator ({@code target}). */
	public static final int THREAD = 26;
	/** A guardian joined (a gold burst at {@code pos}). */
	public static final int GUARD_JOIN = 27;

	/** Tanda Langit warning time (DESIGN 19.3.1: 1.5 s). */
	public static final int MARK_TICKS = 30;
	/** Wind-up of Napas Es, Hentakan Bumi and Duri Racun. */
	public static final int WINDUP_TICKS = 20;
	/** Wind-up of Kedipan. */
	public static final int BLINK_TICKS = 10;
	/** A tribulation ring is shown this long before its strike (DESIGN 19.3.8). */
	public static final int RING_TICKS = 30;

	private CombatFxIds() {
	}
}
