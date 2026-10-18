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
	/** Heart demon moments. a = step, b = demon entity id (player id for a warning), f = ticks; see trial.TrialFxIds. */
	HEART_DEMON_APPEAR,
	/** A heart demon trial ends. a = outcome, b = owner entity id, pos = demon, target = owner chest; see trial.TrialFxIds. */
	HEART_DEMON_DEFEAT,
	/**
	 * a >= 0: evolution cinematic to tier a (0 = awakening), b = entity id, f = kind. a = -1 Moon Thunder bolt (pos victim,
	 * target sky), -2 Foxfire (pos victim), -3 tier 3 blessing thread (pos beast, target owner).
	 */
	BEAST_EVOLVE,
	/**
	 * Protective formation. pos = lodestone centre, f = radius. a = 1 dome refresh (b = 1 just switched on, 2 charged),
	 * 0 switched off, 2 / 3 missing / present pillar marker (pos = pillar), 4 monster pushed back (target = centre).
	 */
	PROTECTIVE_FORMATION,
	/** b = 1 start (pos = A, target = B, a = A colour, f = B colour), 0 end (a = winner colour at pos, -1 no winner). */
	SPARRING,

	// ---- phase 4 (client/fx/AlchemyFx, AscensionFx)
	/** a = 0 add ingredient, 1 refining (b = ticks), 2 success (b = pill quality), 3 failure. */
	ALCHEMY,
	/** True Immortal ascension. a = step of the event. */
	ASCENSION,

	// ---- phase 6 (DESIGN 20.2.3); arguments are defined by the owning module
	/** Sect Hall effects (client/hall/HallFx, module A). */
	HALL,
	/** Duties, expeditions, duels, the Void Realm (client/sectevent/SectEventFx, module B). */
	SECT_EVENT,
	/** Talents, physiques, treasures, ultimates (client/disciple/power/PowerFx, module C). */
	DISCIPLE_POWER,
	/** Bintang Abadi stars, calamities, seals, the heir (client/disciple/path/PathFx, module D). */
	STAR,
	/** Mode Kehancuran: red sky, shape preview, impact dust, restore shimmer (client/kehancuran/KehancuranFx, module D). */
	KEHANCURAN,

	// ---- phase 6b (DESIGN 21.24)
	/** Pembangunan Sekte: placement crumbs, component and tier ceremonies, blocked pings (client/sectbuild/BuildFx); a = {@code sect.build.api.BuildFxIds}. */
	SECT_BUILD,

	// ---- phase 7 (DESIGN 19.2.11); one per module, sub-ids in each module's FxIds class
	/** Elite telegraphs, element sparks, tribulation lightning rings, twin rings, Hu Fa threads (client/fx/EliteFx); a = {@code elite.CombatFxIds}. */
	ELITE,
	/** Spirit stones, beast cores, herbs, treasure finds (client/fx/HartaFx); a = {@code harta.HartaFxIds}. */
	TREASURE,
	/** Talismans inscribed, burning into glyphs, the Unbroken Soul shatter (client/fx/TalismanFx); a = {@code talisman.TalismanFxIds}. */
	TALISMAN,

	// ---- phase 7c (DESIGN 24.13 item 3)
	/** Sect formations: slot marks, pattern show, awaken, pulses, hits, alarms, trips (client/formasi/FormasiFx); a = {@code formasi.FormasiFxIds}. */
	FORMASI;

	public static FxType byOrdinal(int ordinal) {
		FxType[] values = values();
		return ordinal < 0 || ordinal >= values.length ? null : values[ordinal];
	}
}
