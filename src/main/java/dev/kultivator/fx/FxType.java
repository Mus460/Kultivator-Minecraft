package dev.kultivator.fx;

/**
 * Every effect the server can ask clients to show (see {@code Fx.send}). The client draws it in the class named in the
 * comment, scaled by the viewer's Effect Quality. The ordinal is sent over the network: add new ones at the end only.
 * Arguments: pos = x,y,z; target = tx,ty,tz; a, b (ints) and f (float).
 */
public enum FxType {
	// ---- common primitives (client/fx/CommonFx) usable by any module
	/** Burst of coloured motes. a = RGB, b = count (before quality), f = radius. */
	BURST,
	/** Flat ring on the ground expanding to radius f. a = RGB. */
	RING,
	/** Straight beam of motes from pos to target. a = RGB, f = width. */
	BEAM,
	/** Column of motes rising at pos. a = RGB, f = height. */
	PILLAR,

	// ---- progression / breakthrough (client/fx/BreakthroughFx)
	/** Minor breakthrough (stage up). a = realm ordinal, b = new stage, f = stages climbed in this gain. */
	STAGE_UP,
	/** Mortal -> Qi Refining awakening, played over its 6 s. a = root main colour, b = duration ticks (0 = stop). */
	AWAKENING,
	/** A breakthrough begins (qi gathering). a = realm ordinal of the target, b = duration ticks (0 = stop). */
	BREAKTHROUGH_START,
	/** Success. a = new realm ordinal, b = BreakthroughQuality ordinal, f = new rank step. */
	BREAKTHROUGH_SUCCESS,
	/** Failure / Qi Deviation burst. a = 1 for a Hardcore realm drop, f = severity (1 = normal). */
	DEVIATION,

	// ---- tribulation (client/fx/TribulationFx); a = variant (0 overworld, 1 nether, 2 end)
	/** Gathering clouds over pos for b ticks. */
	TRIBULATION_OMEN,
	/** One strike from target (sky) down to pos. b = strike number, f = 1 normal / 2 final. */
	TRIBULATION_STRIKE,
	/** The clouds part. b = 1 success, 0 failure. */
	TRIBULATION_END,
	/** A Dao Protector monster arrives at pos (b = 0) or vanishes with a poof when the tribulation ends (b = 1). */
	DAO_PROTECTOR,

	// ---- meditation (client/fx/MeditationFx)
	/** Enlightenment moment for b ticks. a = colour. */
	ENLIGHTENMENT,
	/** Qi gathering formation lines; pos = centre, a = tier. */
	QI_FORMATION,

	// ---- pills (client/fx/PillFx)
	/** A pill taken. a = RGB, b = strength 1..3. */
	PILL,

	// ---- phase 2 (client/fx/RootFx, DaoFx, TechniqueFx)
	/**
	 * Root testing ceremony step. a = -2 the stones rise, an element ordinal for the stone being read, -1 the final reveal;
	 * b = elements mask, f = grade ordinal, pos = where the ceremony started.
	 */
	ROOT_CEREMONY,
	/** Marrow cleansing. b > 0 starts b ticks of impurities around pos; b = 0 is the purified flash (a = new grade ordinal). */
	MARROW_CLEANSE,
	/**
	 * Dao effects. a = Dao ordinal (-1 universal); b = 0 Dao chosen, 1..4 comprehension level reached, 5 technique learned
	 * from a book, 6 book insight.
	 */
	DAO_CHOSEN,
	/** Any technique. a = Technique ordinal, b = technique specific sub effect, f = scale; target = aim point. */
	TECHNIQUE,

	// ---- phase 3 (client/fx/TrialFx, BeastFx, FormationFx, SectFx)
	HEART_DEMON_APPEAR,
	HEART_DEMON_DEFEAT,
	/** a = tier, b = entity id. */
	BEAST_EVOLVE,
	/** Protective formation dome. pos = centre, f = radius, a = 1 active / 0 switched off. */
	PROTECTIVE_FORMATION,
	/** b = 1 start, 0 end. */
	SPARRING,

	// ---- phase 4 (client/fx/AlchemyFx, AscensionFx)
	/** a = 0 add ingredient, 1 refining (b = ticks), 2 success (b = pill quality), 3 failure. */
	ALCHEMY,
	/** True Immortal ascension. a = step of the event. */
	ASCENSION;

	public static FxType byOrdinal(int ordinal) {
		FxType[] values = values();
		return ordinal < 0 || ordinal >= values.length ? null : values[ordinal];
	}
}
