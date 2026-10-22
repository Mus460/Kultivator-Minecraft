package dev.kultivator.duel;

/**
 * Every byte code of the sword duel (DESIGN 35.8.1, contract C35.S3), in the order of the design's tables: the value of
 * each name is its position there. The payload records ({@code duel.net.DuelPayloads}) carry these numbers; the client
 * modules (U, V, A) read the same values. Never renumber a released code; new ones go at the end of their group.
 */
public final class DuelIds {

	// ================================================================ phases (35.2.2, DuelSession.Phase order)
	public static final int PHASE_INVITE = 0;
	public static final int PHASE_GATHER = 1;
	public static final int PHASE_INTRO = 2;
	public static final int PHASE_COUNTDOWN = 3;
	public static final int PHASE_FIGHT = 4;
	public static final int PHASE_FINISHER = 5;
	public static final int PHASE_ENDED = 6;

	// ================================================================ pairs (35.2.1)
	public static final int PAIR_PVP = 0;
	public static final int PAIR_MURID = 1;
	public static final int PAIR_NPC = 2;
	public static final int PAIR_TONTON = 3;
	public static final int PAIR_LATIH = 4;
	public static final int PAIR_UJI = 5;

	// ================================================================ clash contexts (35.2.7, 35.3.13): where the layer runs
	/** No clash layer between these two. */
	public static final int CONTEXT_NONE = 0;
	/** An official {@code DuelSession} in FIGHT. */
	public static final int CONTEXT_RESMI = 1;
	/** {@code Sparring} (Latih Tanding, {@code PAIR_LATIH}): clashes, posture, sparks; no intro, no stakes. */
	public static final int CONTEXT_LATIH = 2;
	/** Any other {@code DuelRules} pair: tournament bouts, the Hundred Dynasties War, the Void Realm. */
	public static final int CONTEXT_TURNAMEN = 3;
	/** Tanding dengan Guru ({@code disciple/path/Surpass}): a {@code DuelRules} pair of a master and own disciple. */
	public static final int CONTEXT_GURU = 4;
	/** The general layer against mobs (35.3.13). */
	public static final int CONTEXT_UMUM = 5;

	// ================================================================ DuelInput.kind (35.8.1)
	public static final int IN_SWING = 0;
	public static final int IN_CHARGE_START = 1;
	public static final int IN_CHARGE_RELEASE = 2;
	public static final int IN_PARRY = 3;
	public static final int IN_GUARD_ON = 4;
	public static final int IN_GUARD_OFF = 5;
	public static final int IN_FEINT = 6;
	public static final int IN_BIND_PUSH = 7;
	public static final int IN_BIND_RELEASE = 8;
	public static final int IN_DODGE = 9;
	public static final int IN_BC_SWING = 10;
	public static final int IN_SURRENDER_HOLD = 11;

	// ================================================================ DuelInput.flags
	public static final int INF_RUN = 1;
	public static final int INF_FORWARD = 1 << 1;
	public static final int INF_AIR = 1 << 2;
	public static final int INF_CROUCH = 1 << 3;
	public static final int INF_BC_OFFHAND = 1 << 4;
	public static final int INF_BC_HEAVY = 1 << 5;
	/** The client predicted a Perfect Deflect: diagnostics only, never trusted. */
	public static final int INF_PREDICT_PERFECT = 1 << 6;

	// ================================================================ DuelAnswer.answer
	public static final int ANSWER_TERIMA = 0;
	public static final int ANSWER_TOLAK = 1;
	public static final int ANSWER_MENYERAH = 2;
	public static final int ANSWER_TARIK = 3;
	public static final int ANSWER_TUNDA = 4;

	// ================================================================ DuelPrefs
	/** {@code DuelPrefs.kiai}: off (default), male, female. */
	public static final int KIAI_OFF = 0;
	public static final int KIAI_MALE = 1;
	public static final int KIAI_FEMALE = 2;
	/** {@code DuelPrefs.inputMode} (InputMode, 35.3.2). OTOMATIS is resolved by the client; it reports the mode in use. */
	public static final int INPUT_OTOMATIS = 0;
	public static final int INPUT_KLASIK = 1;
	public static final int INPUT_IKUTI_BC = 2;
	public static final int INPUT_VANILLA = 3;
	/** {@code DuelPrefs.flags}. */
	public static final int PREF_INTRO_FILM = 1;
	public static final int PREF_PARRY_OUTSIDE = 1 << 1;
	public static final int PREF_VISUAL_MODULE = 1 << 2;

	// ================================================================ DuelFighterSync.state
	public static final int ST_IDLE = 0;
	public static final int ST_WINDUP = 1;
	public static final int ST_STRIKE = 2;
	public static final int ST_RECOVER = 3;
	public static final int ST_GUARD = 4;
	public static final int ST_STAGGER = 5;
	public static final int ST_BIND = 6;
	public static final int ST_DODGE = 7;
	public static final int ST_RECOIL = 8;
	public static final int ST_HELD = 9;

	// ================================================================ DuelFighterSync.flags
	public static final int FF_CELAH = 1;
	public static final int FF_QI_READY = 1 << 1;
	public static final int FF_COMPANION = 1 << 2;
	/** Staggered: the 決 deathblow mark is shown to attackers within 6 blocks. */
	public static final int FF_DEATHBLOW_MARK = 1 << 3;
	/** Spam-parry lock: the next presses only have the narrow window and no Perfect. */
	public static final int FF_SPAM = 1 << 4;

	// ================================================================ DuelSwing.flags (short)
	public static final int SW_HEAVY = 1;
	public static final int SW_PERILOUS_GUNUNG = 1 << 1;
	public static final int SW_PERILOUS_MAUT = 1 << 2;
	public static final int SW_PERILOUS_SAPU = 1 << 3;
	public static final int SW_QI = 1 << 4;
	public static final int SW_FEINT_CANCEL = 1 << 5;
	public static final int SW_REJECTED = 1 << 6;
	public static final int SW_BC = 1 << 7;
	public static final int SW_CHAIN_NEXT = 1 << 8;
	public static final int SW_RIPOSTE = 1 << 9;
	/** A QI swing that launches a Sword Qi Wave at its impact (the client module V reads the same bit). */
	public static final int SW_QI_WAVE = 1 << 10;
	/** "Tebasan Langit": the upward-from-the-air follow-up after jumping a Low Sweep (posture ×1.3). */
	public static final int SW_SKY = 1 << 11;
	public static final int SW_PERILOUS = SW_PERILOUS_GUNUNG | SW_PERILOUS_MAUT | SW_PERILOUS_SAPU;

	/**
	 * {@code DuelSwing.windup == 0}: the swing is a heavy charge being held (open-ended, pose ANGKAT_BERAT); the swing with
	 * the same {@code swingId} and the real windup follows when it is released.
	 */
	public static final int WINDUP_CHARGING = 0;

	// ================================================================ DuelClash.outcome
	public static final int OUT_HIT = 0;
	public static final int OUT_WHIFF = 1;
	public static final int OUT_BLOCK = 2;
	public static final int OUT_DEFLECT = 3;
	public static final int OUT_PERFECT = 4;
	public static final int OUT_CLASH = 5;
	public static final int OUT_CLASH_MENANG = 6;
	public static final int OUT_BIND = 7;
	public static final int OUT_BIND_MENANG_A = 8;
	public static final int OUT_BIND_MENANG_B = 9;
	public static final int OUT_BIND_LEPAS = 10;
	public static final int OUT_BIND_RELEASE_TRICK = 11;
	public static final int OUT_GUARD_BREAK = 12;
	public static final int OUT_POSTURE_BREAK = 13;
	public static final int OUT_DEATHBLOW = 14;
	public static final int OUT_EVADE = 15;
	public static final int OUT_MIKIRI = 16;
	public static final int OUT_QI_DEFLECT = 17;
	public static final int OUT_QI_REFLECT = 18;
	public static final int OUT_COMPANION_DEFLECT = 19;
	public static final int OUTCOME_COUNT = 20;

	// ================================================================ DuelClash.flags
	public static final int CF_DECISIVE = 1;
	public static final int CF_PREDICTED_MATCH = 1 << 1;

	// ================================================================ DuelBindSync.flags
	/** A pushed during the last 3 ticks. */
	public static final int BF_A_PUSHING = 1;
	public static final int BF_B_PUSHING = 1 << 1;
	/** The lock just started (first sync of this bind). */
	public static final int BF_START = 1 << 2;
	/** The last sync of the lock (it ended this tick). */
	public static final int BF_END = 1 << 3;

	// ================================================================ DuelResult.reason
	public static final int RES_SEPULUH_PERSEN = 0;
	public static final int RES_PENENTU = 1;
	public static final int RES_KELUAR = 2;
	public static final int RES_MENYERAH = 3;
	public static final int RES_WAKTU = 4;
	public static final int RES_SERI = 5;
	public static final int RES_BATAL = 6;

	// ================================================================ StakeKind (35.2.4)
	public static final int STAKE_TANPA = 0;
	public static final int STAKE_BATU_ROH = 1;
	public static final int STAKE_GULUNGAN = 2;
	public static final int STAKE_KEHORMATAN = 3;

	// ================================================================ RealmRule, JurusRule, WinRule (35.2.5)
	public static final int REALM_BEBAS = 0;
	public static final int REALM_HANDICAP = 1;
	public static final int REALM_TEKAN_RANAH = 2;
	public static final int JURUS_PEDANG_SAJA = 0;
	public static final int JURUS_BEBAS = 1;
	/** Only the 10% line (and ring out, surrender, time) decides. */
	public static final int WIN_SEPULUH_PERSEN = 0;
	/** The default: the 10% line AND a Deathblow wins at once. */
	public static final int WIN_TEBASAN_PENENTU = 1;

	// ================================================================ rule flags (the 5th byte of the rules, DuelChallenge.flags)
	public static final int RF_INTRO_FILM = 1;
	public static final int RF_CROWD = 1 << 1;

	// ================================================================ DuelSessionSync.flags
	public static final int SF_INTRO_FILM = 1;
	public static final int SF_CROWD = 1 << 1;
	public static final int SF_OUT_A = 1 << 2;
	public static final int SF_OUT_B = 1 << 3;
	/** The arena is the challenger's sect hall stele (disciple spectators seated). */
	public static final int SF_SECT_ARENA = 1 << 4;
	/** A Deathblow decided the fight (FINISHER with the full closing film). */
	public static final int SF_DECISIVE = 1 << 5;
	/** The loser surrendered (FINISHER without the impact frame, card "Mengaku kalah"). */
	public static final int SF_SURRENDER = 1 << 6;

	// ================================================================ DuelPreview.kind (35.8.1, + 9 from V, + 10-12 from S)
	public static final int PV_SPARK = 0;
	public static final int PV_SLOWMO = 1;
	public static final int PV_INTRO = 2;
	public static final int PV_FINISHER = 3;
	public static final int PV_TRAIL = 4;
	public static final int PV_BIND = 5;
	public static final int PV_SHOCK = 6;
	public static final int PV_DECAL = 7;
	public static final int PV_ALL_POSES = 8;
	/** {@code uji duel gelanggang [radius]} (V): {@code arg} = radius. */
	public static final int PV_RING = 9;
	/** {@code uji duel bilah} (A): toggles the grip-to-tip debug lines ({@code arg} 1 on, 0 off). */
	public static final int PV_BLADE_DEBUG = 10;
	/** {@code uji duel pose <id>} (A): {@code arg} = DuelPose ordinal. */
	public static final int PV_POSE = 11;
	/** {@code uji duel bc} (U): print the last Better Combat animation and its direction. */
	public static final int PV_BC_INFO = 12;

	// ================================================================ perilous kinds (DuelHooks.telegraph, 35.3.8)
	public static final int PERIL_NONE = 0;
	public static final int PERIL_GUNUNG = 1;
	public static final int PERIL_MAUT = 2;
	public static final int PERIL_SAPU = 3;

	// ================================================================ voice moments (DuelVoices.kiai / cheer, C35.S4)
	/** {@code DuelVoices.kiai(entity, what)}. */
	public static final int VOICE_HEAVY = 0;
	public static final int VOICE_DEATHBLOW = 1;
	public static final int VOICE_BIND_WIN = 2;
	public static final int VOICE_MIKIRI = 3;
	/** Posture broken ({@code grunt.*.hurt}). */
	public static final int VOICE_HURT = 4;
	/** {@code DuelVoices.cheer(session, moment)}. */
	public static final int CHEER_CHAIN = 0;
	public static final int CHEER_PERFECT = 1;
	public static final int CHEER_GUARD_BREAK = 2;
	public static final int CHEER_END = 3;
	/** {@code DuelVoices.npcLine(npc, line)}: the line names a disciple/NPC uses. */
	public static final String LINE_OPEN = "attack";
	public static final String LINE_OPEN_ALT = "attack_alt";
	public static final String LINE_VICTORY = "victory";
	public static final String LINE_DEFEAT = "injured";

	/** The four AI styles (35.7.2), DuelStyle order. */
	public static final int STYLE_AGRESIF = 0;
	public static final int STYLE_BERTAHAN = 1;
	public static final int STYLE_PENIPU = 2;
	public static final int STYLE_SANTO_PEDANG = 3;

	private DuelIds() {
	}
}
