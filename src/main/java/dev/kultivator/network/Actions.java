package dev.kultivator.network;

/** Action ids for {@link Payloads.Action}. Never renumber; add new ones at the end. */
public final class Actions {
	/** Start or stop meditating. */
	public static final int MEDITATE_TOGGLE = 1;
	/** Attempt a breakthrough (the bar must be full at a bottleneck). */
	public static final int BREAKTHROUGH = 2;
	/** Cast the selected technique, or the technique with id {@code text} when it is not empty. */
	public static final int CAST = 3;
	/** Select the technique with id {@code text}. */
	public static final int SELECT_TECHNIQUE = 4;
	public static final int SELECT_NEXT = 5;
	public static final int SELECT_PREVIOUS = 6;
	/** Mount or dismount the flying sword. */
	public static final int FLYING_SWORD_TOGGLE = 7;
	/** Jump pressed in mid-air: Cloud Step. */
	public static final int CLOUD_STEP = 8;
	/** Begin the spiritual root testing ceremony. */
	public static final int ROOT_TEST = 9;
	/** Choose Dao {@code arg} (a Dao ordinal). */
	public static final int CHOOSE_DAO = 10;
	/** Cosmetic flag {@code arg} (CultivationData.COSMETIC_*) on when {@code text} is "1", off otherwise. */
	public static final int SET_COSMETIC = 11;
	/** Hardcore breakthroughs on ({@code arg} 1) or off (0). */
	public static final int SET_HARDCORE = 12;
	/** Send me my data again. */
	public static final int REQUEST_SYNC = 13;

	// Disciples (phase 5): arg = the disciple's entity id, text = the parameter.
	/** Ask the server for the disciple's details and open its screen. */
	public static final int DISCIPLE_OPEN = 14;
	/** Set the disciple's order: text = follow | stay | meditate | assist | guard_hall. */
	public static final int DISCIPLE_MODE = 15;
	/** Teach the technique with id {@code text}. */
	public static final int DISCIPLE_TEACH = 16;
	/** Give the disciple the pill the master holds in the main hand. */
	public static final int DISCIPLE_FEED = 17;
	/** Start the disciple's breakthrough. */
	public static final int DISCIPLE_BREAKTHROUGH = 18;
	/** Choose the disciple's Dao: text = Dao id. */
	public static final int DISCIPLE_CHOOSE_DAO = 19;
	/** Rename is done with a name tag; this releases the disciple (text must be "confirm"). */
	public static final int DISCIPLE_RELEASE = 20;

	// Phase 7 "Dunia yang Hidup" (DESIGN 19.2.1): one id per module, text = "op[:param]". 23 and 24 stay reserved, 25-29 free.
	/**
	 * Boons, stars, Perkokoh Fondasi and the Cave Abode ({@code cultivation.ImmortalPerks.handleAction}); text = "star",
	 * "star:all", "reforge", "abode:set" or "abode:clear".
	 */
	public static final int KULTIVASI = 21;
	/** The Jalan tablet ({@code goals.Goals.handleAction}); text = "hide", "show", "claim:N" (N = chapter 1-5) or "seen:codex". */
	public static final int JALAN = 22;

	// Phase 6 (DESIGN 20.2.1). Hall actions: arg = the sub-op. Disciple actions: arg = the disciple's entity id.
	// text carries "op[:param]" in both cases.
	/** The Sect Hall screen ({@code sect.hall.Halls.handleAction}). */
	public static final int HALL = 30;
	/** Sect events: duties, missions, arena ({@code sect.event.SectEvents.handleAction}). */
	public static final int SECT_EVENT = 31;
	/** A disciple's talents, treasures and ultimates ({@code disciple.power.DisciplePowers.handleAction}). */
	public static final int DISCIPLE_POWER = 32;
	/** A disciple's path: stars, bond, heir, seal ({@code disciple.path.DisciplePaths.handleAction}). */
	public static final int DISCIPLE_PATH = 33;
	/** Mode Kehancuran locks and casts ({@code kehancuran.Kehancuran.handleAction}). */
	public static final int KEHANCURAN = 34;

	// Phase 6b (DESIGN 21.24): arg = the sub-op, text = "op[:param]" ("preview:<t>:<rot>:<glaze>", "deposit:<slot>", ...).
	/** Pembangunan Sekte: templates, preview, confirm, deposits, missions, builders ({@code sect.build.SectBuild.handleAction}). */
	public static final int SECT_BUILD = 35;

	// Phase 6c (DESIGN 22.12.10): text = "slot:<0-11>:<id|->", "autofill", "clear", "test:<id>" (op + creative).
	/** The Kitab Teknik and the Cincin Segel ({@code technique.spec.run.Codex.handleAction}). */
	public static final int TECHNIQUE = 36;

	private Actions() {
	}
}
