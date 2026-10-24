package dev.kultivator.sekte;

/**
 * The shared numbering of Sekte yang Hidup (DESIGN 36.3): module letters, the per-disciple and per-hall data keys each
 * module owns, the disciple activity ids 13-24 (synced to the client like 1-12), the template-extension component prefix
 * and the advancement folder. Pure constants, safe on both sides. Never renumber a value once released (activities are
 * synced, keys are saved).
 */
public final class SekteIds {

	// ================================================================== modules (DESIGN 36.4)

	/** P Kepribadian &amp; Kesetiaan (36, 38). */
	public static final String P = "kepribadian";
	/** R Hubungan antar-murid (37). */
	public static final String R = "hubungan";
	/** T Turnamen sekte (39). */
	public static final String T = "turnamen";
	/** G Ujian Tangga Seribu (40). */
	public static final String G = "tangga";
	/** A Paviliun Kitab &amp; Pemakaman / Altar Leluhur (41, 45). */
	public static final String A = "paviliun";
	/** E Tetua &amp; Pewaris (42, 50). */
	public static final String E = "tetua";
	/** X Misi luar &amp; kepulangan (43, 44). */
	public static final String X = "misi";
	/** S Seragam, Lambang &amp; Bendera (46, 47). */
	public static final String S = "seragam";
	/** D Diplomasi &amp; Sekte Rival (48, 49). */
	public static final String D = "diplomasi";
	/** W Kebun Obat &amp; Tambang Roh (51, 52). */
	public static final String W = "kebun";

	/** The ten module ids in DESIGN order (config sections, test sub-trees, tiles). */
	public static final String[] MODULES = {P, R, T, G, A, E, X, S, D, W};

	/**
	 * Murid Mandiri (DESIGN 39 + 39.F, package {@code sekte.mandiri}): switch {@code umum.mandiri}, config section and
	 * test tree {@code mandiri}. Deliberately not in {@link #MODULES}: that array is the ten DESIGN 36 modules whose index
	 * fixes the epic beat ranges 100-199 ({@code EpicIds.EXTRA_SEKTE_FIRST}) and the service list of
	 * {@code SekteApi.liveModules}; 39 reports itself with {@code uji perguruan mandiri modul}.
	 */
	public static final String MANDIRI = "mandiri";

	// ================================================================== saved data keys

	/**
	 * Sub-compounds of {@code DiscipleData.extra()} (DESIGN 20.4.6 rule: a module writes only its own key, everyone may
	 * read): {@code hati} P (traits, contentment, leave state), {@code tangga} G, {@code turnamen} T, {@code ingatan} the
	 * lead's memory ring, {@code ikatan_murid} R (only the soul's
	 * Dao-companion mark; the pair table lives in the hall section), {@code kitab} A (study queue, pavilion progress),
	 * {@code jabatan} E (office), {@code misi} X (mission history), {@code kerja} W (garden/mine shift).
	 */
	public static final String EXTRA_P = "hati";
	/** G: the candidate's climb record (steps reached, stage of the fall, heart verdict), written once at recruitment. */
	public static final String EXTRA_G = "tangga";
	/** T: the disciple's tournament record (bouts, wins, titles, best placing). */
	public static final String EXTRA_T = "turnamen";
	/** Lead ({@code sekte.Ingatan}): the shared memory ring every module writes through {@code Ingatan.remember}. */
	public static final String EXTRA_MEMORY = "ingatan";
	public static final String EXTRA_R = "ikatan_murid";
	public static final String EXTRA_A = "kitab";
	public static final String EXTRA_E = "jabatan";
	public static final String EXTRA_X = "misi";
	public static final String EXTRA_W = "kerja";
	/** 39: path, goals, drives, activity, purse, bag... (schema C39.A4 in {@code sekte.mandiri.api.MandiriIds}). */
	public static final String EXTRA_MANDIRI = "mandiri";

	/** Sections of the owner's {@code HallData} ({@code Halls.section(owner, key)}): {@code sekte_<module>}. */
	public static String hallSection(String module) {
		return "sekte_" + module;
	}

	// ================================================================== disciple activities 13-24 (DiscipleEntity)

	/** A: reading in the Paviliun Kitab (seated, a scroll in the hands). Holds its spot. */
	public static final int ACT_STUDY = 13;
	/** A: kneeling at a grave or the Altar Leluhur, incense in the hands. Holds its spot. */
	public static final int ACT_MOURN = 14;
	/** W: a mine shift at the Tambang Roh entrance (pick on the shoulder; hidden while "inside"). Holds its spot. */
	public static final int ACT_MINE = 15;
	/** W: tending a spirit-herb plot of the Kebun Obat (kneeling, watering gourd). Holds its spot. */
	public static final int ACT_TEND = 16;
	/** E: an elder at the office desk / receiving reports. Holds its spot. */
	public static final int ACT_OFFICE = 17;
	/** R: two disciples talking / drinking tea face to face. Holds its spot. */
	public static final int ACT_SOCIAL = 18;
	/** P: sulking, arms crossed, refusing work (Membangkang). Holds its spot. */
	public static final int ACT_SULK = 19;
	/** X / T: lined up at the gate or the arena entrance, a travel bundle or a sword on the back. Holds its spot. */
	public static final int ACT_MUSTER = 20;
	/** G: a candidate climbing the stair under pressure (pose only, the module drives the walk). */
	public static final int ACT_CLIMB = 21;
	/** P: a deserter walking out of the gate with a bundle (pose only). */
	public static final int ACT_LEAVING = 22;
	/** E / A / T: a formal bow (succession, funeral, tournament salute). Pose only. */
	public static final int ACT_BOW = 23;
	/** Reserved for a later module. */
	public static final int ACT_RESERVED = 24;

	public static final int ACT_FIRST = ACT_STUDY;
	public static final int ACT_LAST_HOLDING = ACT_MUSTER;
	public static final int ACT_LAST = ACT_RESERVED;

	/**
	 * True when the module that releases activity {@code act} (13-24) runs (S15: a disciple saved in a held activity of a
	 * module now switched off loads free instead of holding its spot forever). Ids outside 13-24 are always true.
	 */
	public static boolean activityRuns(int act) {
		return switch (act) {
			case ACT_STUDY, ACT_MOURN -> SekteContent.on(A);
			case ACT_MINE, ACT_TEND -> SekteContent.on(W);
			case ACT_OFFICE -> SekteContent.on(E);
			case ACT_SOCIAL -> SekteContent.on(R);
			case ACT_SULK, ACT_LEAVING -> SekteContent.on(P);
			case ACT_MUSTER -> SekteContent.on(X) || SekteContent.on(T);
			case ACT_CLIMB -> SekteContent.on(G);
			case ACT_BOW -> SekteContent.on(E) || SekteContent.on(A) || SekteContent.on(T);
			default -> true;
		};
	}

	// ================================================================== disciple activities 40-60 (DESIGN 39.4.2, 39.F.5)

	/** First activity id of Murid Mandiri (names and owners per id in {@code sekte.mandiri.MandiriKegiatan}). */
	public static final int ACT_MANDIRI_FIRST = 40;
	/** Last id: 40-56 DESIGN 39, 57-60 the feud module 39F. */
	public static final int ACT_MANDIRI_LAST = 60;

	/** True for 40-60. None of them is saved with the body (a reload restores 0, rule S15; the planner picks again). */
	public static boolean mandiriActivity(int act) {
		return act >= ACT_MANDIRI_FIRST && act <= ACT_MANDIRI_LAST;
	}

	/**
	 * The ids of 40-60 that bind the disciple to its spot like the duty posts (39.4.2, 39.F.9): 40-45, 48, 51, 53-60.
	 * 46 packing, 47 carrying, 49 bargaining, 50 browsing and 52 the flying departure are poses only.
	 */
	public static boolean mandiriHolding(int act) {
		return mandiriActivity(act) && act != 46 && act != 47 && act != 49 && act != 50 && act != 52;
	}

	/**
	 * The ids of 40-60 that count as seated meditation (39.4.2): 45 seclusion, 54 absorbing stones and 56 self-chosen
	 * meditation, so the old meditation XP, regeneration, Master Guidance and the formation factor work without
	 * changing the master's order.
	 */
	public static boolean mandiriSits(int act) {
		return act == 45 || act == 54 || act == 56;
	}

	// ================================================================== sect build extensions (TemplateExtensions)

	/** Every component an extension adds has an id {@code sx_<module>_<what>} (stable, never renamed). */
	public static String componentId(String module, String what) {
		return "sx_" + module + "_" + what;
	}

	/** Advancements live under {@code data/kultivator/advancement/sekte/<module>_<name>.json}. */
	public static final String ADVANCEMENT_FOLDER = "sekte/";

	/** The Kitab Panduan chapters of this phase (0-based chapter indices 14 and 15). */
	public static final int GUIDE_CHAPTER_MURID = 14;
	public static final int GUIDE_CHAPTER_SEKTE = 15;

	private SekteIds() {
	}
}
