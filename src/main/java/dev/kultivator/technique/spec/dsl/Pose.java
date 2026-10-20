package dev.kultivator.technique.spec.dsl;

/**
 * The cast pose of a phase (DESIGN 27.2): which body animation the client plays on the caster when a TechFx event of
 * that phase arrives. Pure data shared by both sides; the server never animates, it only names the pose in the spec
 * ({@code .pose(...)}) and swings the main hand for the vanilla first-person feedback ({@link #swing()}).
 * <p>
 * {@link #AUTO} lets the client derive the pose from the spec's type, first shape, Dao and phase; {@link #NONE} plays
 * nothing. Never reorder: the client keeps keyframe tables per constant.
 */
public enum Pose {
	AUTO(Swing.NONE, 0),
	NONE(Swing.NONE, 0),
	/** Hands meet at the chest: buffs, seals, formations. */
	SEAL(Swing.NONE, 0),
	/** One-hand qi palm thrust. */
	PALM_THRUST(Swing.STAB, 6),
	/** Two palms pushed forward (beams, fronts). */
	TWIN_PALM(Swing.STAB, 8),
	/** Body fist, repeated per accent. */
	PUNCH(Swing.STAB, 5),
	SWORD_THRUST(Swing.STAB, 6),
	SWORD_SLASH(Swing.WHACK, 7),
	/** The sword raised to the sky (sword rain, formations). */
	SWORD_SKY(Swing.NONE, 0),
	/** An open palm raised to the sky (lightning and sky calls). */
	SKY_RAISE(Swing.NONE, 0),
	OVERHEAD_SLAM(Swing.WHACK, 8),
	/** Both arms spread (domains, fields, auras), held as a channel. */
	ARMS_SPREAD(Swing.NONE, 0),
	MEDITATE_FLOAT(Swing.NONE, 0),
	STOMP(Swing.NONE, 0),
	FINGER_POINT(Swing.STAB, 4),
	SUMMON_RAISE(Swing.NONE, 0),
	KNEEL_STRIKE(Swing.WHACK, 6),
	CHARGE_BACK(Swing.NONE, 0),
	CHARGE_OVERHEAD(Swing.NONE, 0),
	DASH_LEAN(Swing.NONE, 0),
	ROAR(Swing.NONE, 0),
	CHEST_BEAT(Swing.NONE, 0),
	FAN_SWING(Swing.WHACK, 8),
	FLUTE(Swing.NONE, 0),
	PRAY(Swing.NONE, 0),
	HOLD_ITEM_UP(Swing.NONE, 0),
	GATE_FLEX(Swing.NONE, 0),
	FLAME_CUP(Swing.NONE, 0),
	/** Phase 10b (DESIGN 29.5.2): the overhead javelin throw of Tiang Penenang Samudra (the pillar flies into the sky). */
	JAVELIN_THROW(Swing.WHACK, 8);

	/** The vanilla hand swing a pose sends with its cast ({@code LivingEntity.swing(hand, SwingAnimation, true)}). */
	public enum Swing { NONE, WHACK, STAB }

	private static final Pose[] VALUES = values();

	private final Swing swing;
	private final int swingTicks;

	Pose(Swing swing, int swingTicks) {
		this.swing = swing;
		this.swingTicks = swingTicks;
	}

	public Swing swing() {
		return swing;
	}

	/** Duration of the vanilla swing in ticks (0 = no swing). */
	public int swingTicks() {
		return swingTicks;
	}

	/** True for poses the client must pick itself (AUTO) or that play nothing (NONE). */
	public boolean explicit() {
		return this != AUTO && this != NONE;
	}

	public static Pose byOrdinal(int ordinal) {
		return ordinal < 0 || ordinal >= VALUES.length ? AUTO : VALUES[ordinal];
	}
}
