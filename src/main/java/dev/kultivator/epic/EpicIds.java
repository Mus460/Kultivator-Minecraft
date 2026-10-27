package dev.kultivator.epic;

/**
 * Every id of the 7b epic-moment cue (DESIGN 23.4): moments, beats, flags and the shared packings of {@code a}. Common
 * code (server and client read it); written in full by the spine. Never renumber: the values travel in
 * {@code Payloads.EpicMoment}, and clients ignore ids they do not know.
 */
public final class EpicIds {

	// ================================================================ moments (23.4.3)

	public static final int TRIBULATION = 1;
	public static final int BREAKTHROUGH = 2;
	public static final int ASCENSION = 3;
	public static final int HEART_DEMON = 4;
	public static final int DISCIPLE_BT = 5;
	public static final int CALAMITY = 6;
	public static final int SURPASS = 7;
	public static final int RECRUIT = 8;
	public static final int TOURNAMENT = 9;
	public static final int SECT = 10;
	public static final int KEHANCURAN = 11;
	public static final int BOSS = 12;
	public static final int CHEAT_DEATH = 13;
	public static final int EXTRA = 14;
	/** Client-derived from {@code SpecFx.play}; sent only by {@code uji momen} (with TEST). */
	public static final int TECH_ULTIMATE = 20;
	/** Client-derived from the {@code VisualState.DHARMA} rising edge. */
	public static final int DHARMA = 21;
	/** Client-derived from the {@code VisualState.DOMAIN} rising edge. */
	public static final int DOMAIN = 22;
	/** Client-derived from the local player's {@code VisualState.ENLIGHTENED} rising edge. */
	public static final int ENLIGHTEN = 23;
	/** Client-derived from the {@code VisualState.DEVIATION} rising edge. */
	public static final int DEVIATION = 24;
	/** Client-derived from a {@code TechFx} with {@code FLAG_KEHANCURAN}. */
	public static final int KEHANCURAN_SELF = 25;

	// ================================================================ beats per moment

	// TRIBULATION: OMEN a = variant | realm << 4 | layer << 8, b = total; PRE/STRIKE/HUSH a = index, b = total; END a = strikes done.
	public static final int TRIB_OMEN = 0;
	public static final int TRIB_PRE = 1;
	public static final int TRIB_STRIKE = 2;
	public static final int TRIB_HUSH = 3;
	public static final int TRIB_END_OK = 4;
	public static final int TRIB_END_FAIL = 5;
	public static final int TRIB_ABORT = 6;

	// BREAKTHROUGH: a = realm | quality << 4 | kind << 8 | layer << 12; FAIL f = 1 when Hardcore dropped a realm.
	public static final int BT_BEGIN = 0;
	public static final int BT_SUCCESS = 1;
	public static final int BT_FAIL = 2;

	// ASCENSION: a = event tick, f = real 1 / 0.
	public static final int ASC_DARKEN = 0;
	public static final int ASC_PILLAR = 1;
	public static final int ASC_HEAVENS = 2;
	public static final int ASC_VOICE = 3;
	public static final int ASC_FINALE = 4;
	public static final int ASC_STOP = 5;

	// HEART_DEMON: subject = the demon from APPEAR on; a = trial kind.
	public static final int HD_WARNING = 0;
	public static final int HD_APPEAR = 1;
	public static final int HD_WON = 2;
	public static final int HD_LOST = 3;

	// DISCIPLE_BT: a = realm | quality << 4 | kind << 8; b = Foundation type or reveal kind.
	public static final int DBT_START = 0;
	public static final int DBT_STRIKE = 1;
	public static final int DBT_END_OK = 2;
	public static final int DBT_END_FAIL = 3;
	public static final int DBT_CANCEL = 4;
	public static final int DBT_TAKE_LIGHTNING = 5;
	public static final int DBT_REVEAL = 6;

	// CALAMITY: a = k, b = index | total << 8.
	public static final int CAL_OMEN = 0;
	public static final int CAL_PRE = 1;
	public static final int CAL_STRIKE = 2;
	public static final int CAL_PASS = 3;
	public static final int CAL_FAIL = 4;
	public static final int CAL_ABORT = 5;
	public static final int CAL_TRAMPLING = 6;
	public static final int CAL_LAMP = 7;

	// SURPASS: a = by duel 1 / 0.
	public static final int SURPASS_TRIGGER = 0;

	// RECRUIT: ARRIVAL a = count, b = war day; TEST a = Duan | colour << 4 | special << 8.
	public static final int RECRUIT_ARRIVAL = 0;
	public static final int RECRUIT_TEST = 1;
	public static final int RECRUIT_ACCEPT = 2;

	// TOURNAMENT: a = kind (TOUR_*); b = the other finalist's entity id.
	public static final int TOUR_INTRO = 0;
	public static final int TOUR_WINNER = 1;

	// SECT: a = tier or hall level, b = template.
	public static final int SECT_TIER = 0;
	public static final int SECT_TEMPLATE = 1;
	public static final int SECT_HALL_LEVEL = 2;

	// KEHANCURAN: a = art ordinal, b = seconds or blocks, f = radius.
	public static final int KEH_WARNING = 0;
	public static final int KEH_IMPACT = 1;
	public static final int KEH_ABORT = 2;
	public static final int KEH_RESTORE = 3;
	public static final int KEH_CRATER = 4;

	// BOSS: a = boss kind (BOSS_*), b = flame seed kind (FLAME_*).
	public static final int BOSS_KILL = 0;

	// CHEAT_DEATH: a = save kind (SAVE_*).
	public static final int CHEAT_SAVED = 0;

	// EXTRA: per beat.
	public static final int EXTRA_ROOT_TEST = 0;
	public static final int EXTRA_BEAST_TIER3 = 1;
	public static final int EXTRA_HEIR = 2;
	public static final int EXTRA_RING_BOND = 3;
	public static final int EXTRA_MASTER_DESCENDS = 4;
	public static final int EXTRA_FLAME_ABSORBED = 5;
	public static final int EXTRA_VOID_BORDER = 6;
	// Phase 8 (DESIGN 25.2 item 15); every one obeys 23.1 (no camera shot while in danger), the client only stamps them.
	/** A secret realm gate opens (the omen). a = realm ordinal 1-4. */
	public static final int EXTRA_REALM_GATE = 7;
	/** A secret realm cleared. a = realm ordinal 1-4, b = layer (realm 4). */
	public static final int EXTRA_REALM_CLEAR = 8;
	/** Black banners of a demonic sect on the horizon. a = sect index 0-7. */
	public static final int EXTRA_RAID_OMEN = 9;
	/** The natal weapon spirit awakens. a = spirit stage 2-3. */
	public static final int EXTRA_SPIRIT_AWAKEN = 10;
	/** The natal treasure vanishes into the body. a = form index. */
	public static final int EXTRA_NATAL_BOND = 11;
	/** A village swears allegiance. */
	public static final int EXTRA_VILLAGE_OATH = 12;
	// Phase 9 (DESIGN 26.14 item 4); every one obeys 23.1, the client only stamps them.
	/** The ascension tribulation's omen: the five-coloured sky and the cloud disc. */
	public static final int EXTRA_ASCEND_OMEN = 13;
	/** The ascension passed: the player rises into Alam Abadi (sent only after the danger is over). */
	public static final int EXTRA_ASCEND_PASS = 14;
	/** A primordial beast's silhouette crosses the far sky. a = kind 0 Kun-Peng, 1 Zhulong, 2 Taotie. */
	public static final int EXTRA_PRIMORDIAL_OMEN = 15;
	/** A primordial beast changes phase. a = kind, b = the new phase 1-2. */
	public static final int EXTRA_PRIMORDIAL_PHASE = 16;
	/** "The Heavens gaze at you": the white-gold sky of a wrath descent. */
	public static final int EXTRA_WRATH_OMEN = 17;
	/** The player passed the seventh calamity: Penginjak Langit. */
	public static final int EXTRA_PLAYER_TRAMPLING = 18;
	/** An earthly calamity strikes the inner world. a = type 0 meteor, 1 flood, 2 lightning shadow; b = calamity number. */
	public static final int EXTRA_INNER_CALAMITY = 19;
	/** The first cast of a self-created art. a = slot 0-2. */
	public static final int EXTRA_OWN_ART_BORN = 20;
	/** An Immortal auction lot won. */
	public static final int EXTRA_AUCTION_WIN = 21;
	/**
	 * A disciple's signature ultimate (DESIGN 27.6): subject = the disciple, viewer = its master, pos = the centre,
	 * a = the Dao ordinal, b = the radius in blocks (the client stamps the name and seal 劍 雷 禁 拳 炎 生 冰 影 and grades the sky).
	 */
	public static final int EXTRA_DISCIPLE_ULT = 22;
	/** A Jurus Pemusnah in its block-safe form (DESIGN 27.6): subject = the disciple, pos = the centre, a = the art ordinal, b = radius. */
	public static final int EXTRA_DESTROYER_ART = 23;
	/**
	 * Phase 10b (DESIGN 29.9 item 3): a Titah Langit decree. subject = viewer = the caster, pos = the aim (or the planted
	 * weapon), a = the decree 0 Pedang, 1 Tiang, 2 Bintang, 3 Gunung, b = the beat: {@link #TITAH_CARD} the jade title card
	 * at the cast, {@link #TITAH_PULL} the weapon pulled out and carried back to heaven.
	 */
	public static final int EXTRA_TITAH = 24;
	/** {@code b} of {@link #EXTRA_TITAH}: the decree's title card. */
	public static final int TITAH_CARD = 0;
	/** {@code b} of {@link #EXTRA_TITAH}: the planted weapon was pulled out. */
	public static final int TITAH_PULL = 1;
	/**
	 * Sekte yang Hidup (DESIGN 36.3.7): EXTRA beats 100-199 belong to its ten modules, ten each in {@code SekteIds.MODULES}
	 * order (P 100-109, R 110-119, T 120-129, G 130-139, A 140-149, E 150-159, X 160-169, S 170-179, D 180-189,
	 * W 190-199); the client hands them to {@code client.epic.SekteScenes}. Every one obeys 23.1.
	 */
	public static final int EXTRA_SEKTE_FIRST = 100;
	public static final int EXTRA_SEKTE_LAST = 199;

	// TECH_ULTIMATE: the TechFx phases (CAST the pre-roll, TRAVEL / IMPACT the climax, END the release).
	public static final int TECH_CAST = 0;
	public static final int TECH_CLIMAX = 1;
	public static final int TECH_END = 2;

	/**
	 * A reserved beat valid with any known moment and FLAG_TEST: the viewer's client ends every scene at once and restores
	 * sky, sound, music and camera ({@code uji momen berhenti}). Above every {@link #maxBeat} (the highest is EXTRA_SEKTE_LAST), so older clients drop it.
	 */
	public static final int BEAT_STOP_ALL = 250;

	/**
	 * True for the beats that end a scene (results, ABORT / STOP / CANCEL, TECH_END): the per-subject rate limit never drops
	 * them, or the client would keep the vortex, the sky grade and the scene until its watchdog.
	 */
	public static boolean closing(int moment, int beat) {
		return switch (moment) {
			case TRIBULATION -> beat == TRIB_END_OK || beat == TRIB_END_FAIL || beat == TRIB_ABORT;
			case BREAKTHROUGH -> beat == BT_SUCCESS || beat == BT_FAIL;
			case ASCENSION -> beat == ASC_FINALE || beat == ASC_STOP;
			case HEART_DEMON -> beat == HD_WON || beat == HD_LOST;
			case DISCIPLE_BT -> beat == DBT_END_OK || beat == DBT_END_FAIL || beat == DBT_CANCEL;
			case CALAMITY -> beat == CAL_PASS || beat == CAL_FAIL || beat == CAL_ABORT;
			case KEHANCURAN -> beat == KEH_ABORT || beat == KEH_RESTORE;
			case TECH_ULTIMATE -> beat == TECH_END;
			default -> false;
		};
	}

	/** The one beat of DHARMA, DOMAIN, ENLIGHTEN, DEVIATION and KEHANCURAN_SELF (RISE, UNFOLD, START, BURST, IMPACT). */
	public static final int SINGLE = 0;

	// ================================================================ flags (23.4.2)

	/** {@code epicCameraShots} is on and the cue is the viewer's own. */
	public static final int FLAG_CAMERA_OK = 1;
	/** Sent by {@code uji momen} or a test tribulation: plays fully, announces nothing. */
	public static final int FLAG_TEST = 2;
	/** Average tick above {@code kehancuranMsptPause}: clients halve geometry; no PRE beats are sent. */
	public static final int FLAG_LOW_LOAD = 4;
	/** The viewer was hurt by a mob within the last 100 ticks. */
	public static final int FLAG_IN_COMBAT = 8;
	/** Dao Protectors are alive for the viewer. */
	public static final int FLAG_PROTECTORS = 16;
	/** The next strike would fail the tribulation (read-only from {@code sessionExtra.trib.pct}). */
	public static final int FLAG_LETHAL_NEXT = 32;
	/** The last strike or the last beat of a scene. */
	public static final int FLAG_FINAL = 64;
	/** The failure drops a realm. */
	public static final int FLAG_HARDCORE = 128;
	/** The viewer is safe for a camera shot (the full list is in DESIGN 23.4.2). */
	public static final int FLAG_SAFE = 256;
	/** No enemy within 24 blocks, not burning, falling or in fluid: a failure result shot may run at low health. */
	public static final int FLAG_RESULT_SAFE = 512;
	/** The first time this player (or this master's disciples) reaches this milestone. */
	public static final int FLAG_FIRST = 1024;
	/** The scene has sky-scale layers (sent with {@code epicRange}). */
	public static final int FLAG_SKY_SCALE = 2048;
	/** The server also shows a vanilla title for this beat; a client that draws the card hides it. */
	public static final int FLAG_SUPPRESS_TITLE = 4096;

	private static final String[] FLAG_NAMES = {"CAMERA_OK", "TEST", "LOW_LOAD", "IN_COMBAT", "PROTECTORS", "LETHAL_NEXT", "FINAL",
			"HARDCORE", "SAFE", "RESULT_SAFE", "FIRST", "SKY_SCALE", "SUPPRESS_TITLE"};

	// ================================================================ timing (23.5.1)

	/** A PRE beat is sent this many ticks before its strike lands (matches the 19.3.8 telegraph). */
	public static final int PRE_LEAD = 30;
	/** The HUSH before the final strike is sent this many ticks before it lands. */
	public static final int HUSH_LEAD = 60;
	/** Clients clamp {@code at − gameTime} to this range (ticks). */
	public static final int SCHEDULE_MIN = -20;
	public static final int SCHEDULE_MAX = 80;
	/** Scene scale S range (23.10.1). */
	public static final int SCALE_MAX = 10;

	// ================================================================ shared kinds

	/** BREAKTHROUGH kinds; the same values as {@code Breakthrough.KIND_*}. */
	public static final int KIND_AWAKENING = 0;
	public static final int KIND_CHANCE = 1;
	public static final int KIND_TRIBULATION = 2;
	public static final int KIND_FORCED = 3;

	/** TOURNAMENT kinds. */
	public static final int TOUR_INTERNAL = 0;
	public static final int TOUR_ELITE = 1;
	public static final int TOUR_HUNDRED_DYNASTIES = 2;

	/** BOSS kinds, in the order of {@code uji momen bos <warden|wither|naga|penjaga|warisan|raja_siluman|penjaga_alam|tetua_iblis|binatang_purba|panglima_langit>}. */
	public static final int BOSS_WARDEN = 0;
	public static final int BOSS_WITHER = 1;
	public static final int BOSS_DRAGON = 2;
	public static final int BOSS_ELDER_GUARDIAN = 3;
	public static final int BOSS_INHERITANCE = 4;
	public static final int BOSS_DEMON_KING = 5;
	/** Phase 8 (DESIGN 25.2 item 15): a secret realm guardian (subject {@code penjaga_alam}; tags {@code kultivator.boss} + {@code kultivator.realm_guard}). */
	public static final int BOSS_REALM_GUARDIAN = 6;
	/** Phase 8: the Demonic Patriarch of a sect raid (subject {@code tetua_iblis}; tags {@code kultivator.boss} + {@code kultivator.rogue}). */
	public static final int BOSS_DEMONIC_ELDER = 7;
	/** Phase 9 (DESIGN 26.14 item 4): a primordial beast (subject {@code binatang_purba}; tags {@code kultivator.boss} + {@code kultivator.purba}). */
	public static final int BOSS_PRIMORDIAL = 8;
	/** Phase 9: the Panglima Langit of a wrath descent (subject {@code panglima_langit}; tags {@code kultivator.boss} + {@code kultivator.abadi_foe}). */
	public static final int BOSS_HEAVEN_GENERAL = 9;

	/** Heavenly Flame seed kinds of a first boss kill (F1); 0 = none. */
	public static final int FLAME_NONE = 0;
	public static final int FLAME_SEA_HEART = 1;
	public static final int FLAME_FALLING_HEART = 2;
	public static final int FLAME_DEMON_LOTUS = 3;

	/**
	 * CHEAT_DEATH save kinds, in the order of {@code uji momen lolos_maut <...>}; {@link #SAVE_TECHNIQUE} is a technique
	 * guard whose look the client takes from the cheat-death spec cast within 2 ticks.
	 */
	public static final int SAVE_UNBROKEN_SOUL = 0;
	public static final int SAVE_HAIR = 1;
	public static final int SAVE_LOTUS = 2;
	public static final int SAVE_TREE = 3;
	public static final int SAVE_UNDYING_BLOOD = 4;
	public static final int SAVE_SOUL_SHIELD = 5;
	public static final int SAVE_REPAY = 6;
	public static final int SAVE_SOUL_FLIGHT = 7;
	public static final int SAVE_TECHNIQUE = 8;

	// ================================================================ packings of a

	/** TRIBULATION OMEN {@code a}. */
	public static int packOmen(int variant, int realm, int layer) {
		return (variant & 15) | (realm & 15) << 4 | (layer & 15) << 8;
	}

	/** BREAKTHROUGH {@code a} (DISCIPLE_BT uses the same with layer 0). */
	public static int packBreakthrough(int realm, int quality, int kind, int layer) {
		return (realm & 15) | (quality & 15) << 4 | (kind & 15) << 8 | (layer & 15) << 12;
	}

	/** RECRUIT TEST {@code a}. */
	public static int packRecruitTest(int duan, int colour, int special) {
		return (duan & 15) | (colour & 15) << 4 | (special & 255) << 8;
	}

	/** CALAMITY {@code b}. */
	public static int packCalamity(int index, int total) {
		return (index & 255) | (total & 255) << 8;
	}

	/** Field 0-3 (4 bits each) of a packed {@code a}: realm or variant 0, quality or realm 1, kind or layer 2, layer 3. */
	public static int nibble(int packed, int field) {
		return packed >> (field * 4) & 15;
	}

	// ================================================================ names (debug and showcase)

	/** A short name of the moment id, or {@code "?<id>"}. */
	public static String momentName(int moment) {
		return switch (moment) {
			case TRIBULATION -> "TRIBULATION";
			case BREAKTHROUGH -> "BREAKTHROUGH";
			case ASCENSION -> "ASCENSION";
			case HEART_DEMON -> "HEART_DEMON";
			case DISCIPLE_BT -> "DISCIPLE_BT";
			case CALAMITY -> "CALAMITY";
			case SURPASS -> "SURPASS";
			case RECRUIT -> "RECRUIT";
			case TOURNAMENT -> "TOURNAMENT";
			case SECT -> "SECT";
			case KEHANCURAN -> "KEHANCURAN";
			case BOSS -> "BOSS";
			case CHEAT_DEATH -> "CHEAT_DEATH";
			case EXTRA -> "EXTRA";
			case TECH_ULTIMATE -> "TECH_ULTIMATE";
			case DHARMA -> "DHARMA";
			case DOMAIN -> "DOMAIN";
			case ENLIGHTEN -> "ENLIGHTEN";
			case DEVIATION -> "DEVIATION";
			case KEHANCURAN_SELF -> "KEHANCURAN_SELF";
			default -> "?" + moment;
		};
	}

	/** True for the moment ids of 23.4.3 (1-14 and 20-25). */
	public static boolean known(int moment) {
		return moment >= TRIBULATION && moment <= EXTRA || moment >= TECH_ULTIMATE && moment <= KEHANCURAN_SELF;
	}

	/** The highest beat id of a moment, or −1 for an unknown moment. */
	public static int maxBeat(int moment) {
		return switch (moment) {
			case TRIBULATION, DISCIPLE_BT -> 6;
			// Sekte yang Hidup's beats 100-199 (DESIGN 36.3.7); 25-99 stay unused (the client's default branch ignores them).
			case EXTRA -> EXTRA_SEKTE_LAST;
			case BREAKTHROUGH, RECRUIT, SECT, TECH_ULTIMATE -> 2;
			case ASCENSION -> 5;
			case HEART_DEMON -> 3;
			case CALAMITY -> 7;
			case TOURNAMENT -> 1;
			case KEHANCURAN -> 4;
			case SURPASS, BOSS, CHEAT_DEATH, DHARMA, DOMAIN, ENLIGHTEN, DEVIATION, KEHANCURAN_SELF -> 0;
			default -> -1;
		};
	}

	/** The set flags by name, joined with {@code |} ({@code "-"} for none). */
	public static String flagNames(int flags) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < FLAG_NAMES.length; i++) {
			if ((flags & 1 << i) != 0) {
				if (!out.isEmpty()) {
					out.append('|');
				}
				out.append(FLAG_NAMES[i]);
			}
		}
		return out.isEmpty() ? "-" : out.toString();
	}

	private EpicIds() {
	}
}
