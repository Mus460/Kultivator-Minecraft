package dev.kultivator.duel.clash;

/**
 * The windows of the clash layer in server ticks (DESIGN 35.3.4). A window is measured as {@code impact − tEff} of the
 * defender's press, {@code tEff} being the press's arrival minus its latency compensation.
 */
public final class ClashTiming {

	/** Deflect: the press lands 0..5 ticks before the impact. */
	public static final int PARRY_WINDOW = 5;
	/** Perfect Deflect: 0..1 ticks before the impact (100 ms). */
	public static final int PERFECT_WINDOW = 1;
	/** A press that deflected nothing narrows the next presses for this long (spam lock). */
	public static final int SPAM_LOCK = 8;
	/** During the spam lock a press only has 0..2 and no Perfect. */
	public static final int SPAM_WINDOW = 2;
	/** Two impacts at most this far apart clash. */
	public static final int CLASH_WINDOW = 2;
	/** The facing arc for guard, deflect and clash (degrees between the look and the direction to the attacker). */
	public static final float GUARD_ARC = 100.0F;
	/** Two fighters clash only when both face each other within this angle. */
	public static final float CLASH_ARC = 60.0F;
	/** A thrust meets a thrust tip to tip only when both are this straight (degrees). */
	public static final float TIP_ARC = 15.0F;
	/** A feint must be pressed before {@code impact − FEINT_LAST}. */
	public static final int FEINT_LAST = 3;
	/** One feint per this many ticks. */
	public static final int FEINT_COOLDOWN = 20;
	/** A swing started this soon after a feint winds up one tick faster. */
	public static final int FEINT_BONUS_TICKS = 6;
	/** Posture a feint costs its user. */
	public static final float FEINT_POSTURE = 6.0F;
	/** Blade Step (Mikiri) against the Death Thrust: a press 0..4 ticks before the impact while moving forward. */
	public static final int MIKIRI_WINDOW = 4;
	/** Opening after a Perfect Deflect, a won blade lock or the enemy's posture break. */
	public static final int CELAH_TICKS = 10;
	/** Posture broken: no action for this long. */
	public static final int STAGGER_TICKS = 30;
	/** Bosses only stagger this long (35.3.13). */
	public static final int BOSS_STAGGER_TICKS = 10;
	/** The attacker thrown back by a Perfect Deflect of a light / heavy swing. */
	public static final int RECOIL_LIGHT = 6;
	public static final int RECOIL_HEAVY = 10;
	/** Both fighters after a CLASH. */
	public static final int RECOIL_CLASH = 4;
	/** The light side of a CLASH_MENANG. */
	public static final int RECOIL_CLASH_LOST = 8;
	/** The victim of a HIT. */
	public static final int RECOIL_HIT = 4;
	/** The loser of a blade lock, the victim of a release trick. */
	public static final int RECOIL_BIND = 12;
	/** The next exchange within this many ticks adds to the chain. */
	public static final int CHAIN_GAP = 30;
	/** Recovery after a light / heavy swing, +3 after a whiff. */
	public static final int RECOVER_LIGHT = 5;
	public static final int RECOVER_HEAVY = 8;
	public static final int RECOVER_WHIFF = 3;
	/** The next light cut of a combo may start this long after the previous impact (max 3 in a row). */
	public static final int COMBO_GAP = 3;
	public static final int COMBO_MAX = 3;
	/** Dodge invulnerability (Shadow Step, Combat Roll). */
	public static final int DODGE_TICKS = 6;
	/** Shadow Step cooldown. */
	public static final int DODGE_COOLDOWN = 16;
	/** The jump that clears a Low Sweep; an upward cut in the air within this long is a "Tebasan Langit". */
	public static final int SKY_TICKS = 10;
	/** Height above ground that clears a Low Sweep. */
	public static final double SWEEP_CLEAR = 0.4D;
	/** Guard forced down this long by a guard break. */
	public static final int GUARD_BROKEN_TICKS = 20;
	/** A guard holds after this many ticks of holding the button (the client sends GUARD_ON after it). */
	public static final int GUARD_DELAY = 5;
	/** A swing waits at most this long after its impact for a late defence before it is resolved anyway. */
	public static final int MAX_RESOLVE_WAIT = 6;
	/** A heavy charge is released by itself after being full this long. */
	public static final int CHARGE_HOLD_MAX = 40;
	/** The minimum lead of a perilous mark before its impact. */
	public static final int PERILOUS_LEAD = 8;
	/** A resolved Better Combat swing still matches BC's own hit this long. */
	public static final int BC_MATCH = 3;
	/** Reactive defence against mobs and vanilla hits: deflect / perfect when the press is this recent (35.3.13). */
	public static final int REACTIVE_DEFLECT = 6;
	public static final int REACTIVE_PERFECT = 2;
	/** Armed mob clash: the two blows at most this far apart. */
	public static final int MOB_CLASH_WINDOW = 2;

	/**
	 * The latest swing start a realm allows: light windups are 1 tick shorter from Nascent Soul and 2 from Immortal
	 * Ascension, never below 4 (35.3.1; HANDICAP does not remove it).
	 */
	public static int realmWindup(int windup, int realm) {
		int cut = realm >= 7 ? 2 : realm >= 4 ? 1 : 0;
		return Math.max(4, windup - cut);
	}

	private ClashTiming() {
	}
}
