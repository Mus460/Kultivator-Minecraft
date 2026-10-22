package dev.kultivator.client.duel.fx;

import dev.kultivator.duel.DuelIds;

/**
 * The byte codes of the duel payloads that the visuals read (DESIGN 35.8.1): short names for the {@link DuelIds} codes of
 * module S (the value of each name is its position in the design's tables), plus the colour helpers of the visuals.
 */
public final class DuelCodes {

	// ---------------------------------------------------------------- DuelSessionSync.phase (35.2.2)
	public static final int PHASE_INVITE = DuelIds.PHASE_INVITE;
	public static final int PHASE_GATHER = DuelIds.PHASE_GATHER;
	public static final int PHASE_INTRO = DuelIds.PHASE_INTRO;
	public static final int PHASE_COUNTDOWN = DuelIds.PHASE_COUNTDOWN;
	public static final int PHASE_FIGHT = DuelIds.PHASE_FIGHT;
	public static final int PHASE_FINISHER = DuelIds.PHASE_FINISHER;
	public static final int PHASE_ENDED = DuelIds.PHASE_ENDED;

	// ---------------------------------------------------------------- pairs (35.2.1)
	public static final int PAIR_PVP = DuelIds.PAIR_PVP;
	public static final int PAIR_MURID = DuelIds.PAIR_MURID;
	public static final int PAIR_NPC = DuelIds.PAIR_NPC;
	public static final int PAIR_TONTON = DuelIds.PAIR_TONTON;
	public static final int PAIR_LATIH = DuelIds.PAIR_LATIH;
	public static final int PAIR_UJI = DuelIds.PAIR_UJI;

	// ---------------------------------------------------------------- DuelSessionSync.flags
	public static final int SESSION_INTRO_FILM = DuelIds.SF_INTRO_FILM;
	public static final int SESSION_CROWD = DuelIds.SF_CROWD;
	public static final int SESSION_OUT_A = DuelIds.SF_OUT_A;
	public static final int SESSION_OUT_B = DuelIds.SF_OUT_B;
	public static final int SESSION_SECT_ARENA = DuelIds.SF_SECT_ARENA;
	public static final int SESSION_DECISIVE = DuelIds.SF_DECISIVE;
	public static final int SESSION_SURRENDER = DuelIds.SF_SURRENDER;

	// ---------------------------------------------------------------- DuelBindSync.flags
	public static final int BIND_A_PUSHING = DuelIds.BF_A_PUSHING;
	public static final int BIND_B_PUSHING = DuelIds.BF_B_PUSHING;
	public static final int BIND_START = DuelIds.BF_START;
	public static final int BIND_END = DuelIds.BF_END;

	/** {@code DuelSwing.windup == 0}: a heavy charge being held (the real windup follows on release). */
	public static final int WINDUP_CHARGING = DuelIds.WINDUP_CHARGING;
	/** "Tebasan Langit": the air follow-up after jumping a Low Sweep. */
	public static final int SWING_SKY = DuelIds.SW_SKY;

	// ---------------------------------------------------------------- DuelClash.outcome
	public static final int HIT = DuelIds.OUT_HIT;
	public static final int WHIFF = DuelIds.OUT_WHIFF;
	public static final int BLOCK = DuelIds.OUT_BLOCK;
	public static final int DEFLECT = DuelIds.OUT_DEFLECT;
	public static final int PERFECT = DuelIds.OUT_PERFECT;
	public static final int CLASH = DuelIds.OUT_CLASH;
	public static final int CLASH_MENANG = DuelIds.OUT_CLASH_MENANG;
	public static final int BIND = DuelIds.OUT_BIND;
	public static final int BIND_MENANG_A = DuelIds.OUT_BIND_MENANG_A;
	public static final int BIND_MENANG_B = DuelIds.OUT_BIND_MENANG_B;
	public static final int BIND_LEPAS = DuelIds.OUT_BIND_LEPAS;
	public static final int BIND_RELEASE_TRICK = DuelIds.OUT_BIND_RELEASE_TRICK;
	public static final int GUARD_BREAK = DuelIds.OUT_GUARD_BREAK;
	public static final int POSTURE_BREAK = DuelIds.OUT_POSTURE_BREAK;
	public static final int DEATHBLOW = DuelIds.OUT_DEATHBLOW;
	public static final int EVADE = DuelIds.OUT_EVADE;
	public static final int MIKIRI = DuelIds.OUT_MIKIRI;
	public static final int QI_DEFLECT = DuelIds.OUT_QI_DEFLECT;
	public static final int QI_REFLECT = DuelIds.OUT_QI_REFLECT;
	public static final int COMPANION_DEFLECT = DuelIds.OUT_COMPANION_DEFLECT;

	// ---------------------------------------------------------------- DuelClash.flags
	public static final int CLASH_DECISIVE = DuelIds.CF_DECISIVE;
	public static final int CLASH_PREDICTED_MATCH = DuelIds.CF_PREDICTED_MATCH;

	// ---------------------------------------------------------------- DuelSwing.kind (SwingKind order)
	public static final int KIND_RINGAN = 0;
	public static final int KIND_BERAT = 1;
	public static final int KIND_TUSUK = 2;
	public static final int KIND_QI = 3;
	public static final int KIND_BALIK = 4;
	public static final int KIND_PENENTU = 5;
	public static final int KIND_TERBANG = 6;
	public static final int KIND_BC = 7;

	// ---------------------------------------------------------------- DuelSwing.dir (SwingDir order)
	public static final int DIR_ATAS = 0;
	public static final int DIR_ATAS_KANAN = 1;
	public static final int DIR_KANAN = 2;
	public static final int DIR_BAWAH_KANAN = 3;
	public static final int DIR_BAWAH = 4;
	public static final int DIR_BAWAH_KIRI = 5;
	public static final int DIR_KIRI = 6;
	public static final int DIR_ATAS_KIRI = 7;
	public static final int DIR_TUSUK = 8;
	public static final int DIR_PUTAR = 9;

	// ---------------------------------------------------------------- DuelSwing.flags
	public static final int SWING_HEAVY = DuelIds.SW_HEAVY;
	public static final int SWING_PERILOUS_GUNUNG = DuelIds.SW_PERILOUS_GUNUNG;
	public static final int SWING_PERILOUS_MAUT = DuelIds.SW_PERILOUS_MAUT;
	public static final int SWING_PERILOUS_SAPU = DuelIds.SW_PERILOUS_SAPU;
	public static final int SWING_QI = DuelIds.SW_QI;
	public static final int SWING_FEINT_CANCEL = DuelIds.SW_FEINT_CANCEL;
	public static final int SWING_REJECTED = DuelIds.SW_REJECTED;
	public static final int SWING_BC = DuelIds.SW_BC;
	public static final int SWING_CHAIN_NEXT = DuelIds.SW_CHAIN_NEXT;
	public static final int SWING_RIPOSTE = DuelIds.SW_RIPOSTE;
	/** Not in the design's table: S may set it on a QI swing that launched a Sword Qi Wave; without it V infers the wave. */
	public static final int SWING_QI_WAVE = DuelIds.SW_QI_WAVE;
	public static final int SWING_PERILOUS = DuelIds.SW_PERILOUS;

	// ---------------------------------------------------------------- WeaponClass order
	public static final int W_PEDANG = 0;
	public static final int W_PEDANG_BERAT = 1;
	public static final int W_KAPAK = 2;
	public static final int W_TOMBAK = 3;
	public static final int W_GADA = 4;
	public static final int W_TANGAN_KOSONG = 5;

	// ---------------------------------------------------------------- DuelFighterSync.state / flags
	public static final int STATE_IDLE = DuelIds.ST_IDLE;
	public static final int STATE_WINDUP = DuelIds.ST_WINDUP;
	public static final int STATE_STRIKE = DuelIds.ST_STRIKE;
	public static final int STATE_RECOVER = DuelIds.ST_RECOVER;
	public static final int STATE_GUARD = DuelIds.ST_GUARD;
	public static final int STATE_STAGGER = DuelIds.ST_STAGGER;
	public static final int STATE_BIND = DuelIds.ST_BIND;
	public static final int STATE_DODGE = DuelIds.ST_DODGE;
	public static final int STATE_RECOIL = DuelIds.ST_RECOIL;
	public static final int STATE_HELD = DuelIds.ST_HELD;
	public static final int FIGHTER_OPENING = DuelIds.FF_CELAH;
	public static final int FIGHTER_QI_READY = DuelIds.FF_QI_READY;
	public static final int FIGHTER_COMPANION = DuelIds.FF_COMPANION;
	public static final int FIGHTER_DEATHBLOW_MARK = DuelIds.FF_DEATHBLOW_MARK;
	public static final int FIGHTER_SPAM = DuelIds.FF_SPAM;

	// ---------------------------------------------------------------- DuelResult.reason
	public static final int REASON_SEPULUH_PERSEN = DuelIds.RES_SEPULUH_PERSEN;
	public static final int REASON_PENENTU = DuelIds.RES_PENENTU;
	public static final int REASON_KELUAR = DuelIds.RES_KELUAR;
	public static final int REASON_MENYERAH = DuelIds.RES_MENYERAH;
	public static final int REASON_WAKTU = DuelIds.RES_WAKTU;
	public static final int REASON_SERI = DuelIds.RES_SERI;
	public static final int REASON_BATAL = DuelIds.RES_BATAL;

	// ---------------------------------------------------------------- DuelPreview.kind
	public static final int PREVIEW_SPARK = DuelIds.PV_SPARK;
	public static final int PREVIEW_SLOWMO = DuelIds.PV_SLOWMO;
	public static final int PREVIEW_INTRO = DuelIds.PV_INTRO;
	public static final int PREVIEW_FINISHER = DuelIds.PV_FINISHER;
	public static final int PREVIEW_TRAIL = DuelIds.PV_TRAIL;
	public static final int PREVIEW_BIND = DuelIds.PV_BIND;
	public static final int PREVIEW_SHOCK = DuelIds.PV_SHOCK;
	public static final int PREVIEW_DECAL = DuelIds.PV_DECAL;
	public static final int PREVIEW_ALL_POSES = DuelIds.PV_ALL_POSES;
	/** {@code uji duel gelanggang [radius]}: the ring at the player without a session ({@code arg} = radius). */
	public static final int PREVIEW_RING = DuelIds.PV_RING;

	/** The base spark colour of a weapon class (35.3.1). */
	public static int sparkRgb(int weaponClass) {
		return switch (weaponClass) {
			case W_PEDANG_BERAT -> 0xFFD08A;
			case W_KAPAK -> 0xFFC070;
			case W_TOMBAK -> 0xFFF0C8;
			case W_GADA -> 0xFFB060;
			case W_TANGAN_KOSONG -> 0xFFFFFF;
			default -> 0xFFE7B0;
		};
	}

	/** True for the outcomes where two blades actually meet (metal sparks). */
	public static boolean metal(int outcome) {
		return switch (outcome) {
			case CLASH, CLASH_MENANG, BIND, BIND_MENANG_A, BIND_MENANG_B, BIND_LEPAS, BIND_RELEASE_TRICK, DEFLECT, PERFECT, MIKIRI, BLOCK,
					GUARD_BREAK, COMPANION_DEFLECT, QI_DEFLECT, QI_REFLECT -> true;
			default -> false;
		};
	}

	private DuelCodes() {
	}
}
