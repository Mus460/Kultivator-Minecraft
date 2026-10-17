package dev.kultivator.sect.event;

/**
 * Sub effects of {@code FxType.SECT_EVENT} (DESIGN 20.3.22, module B), shared by the server and the client
 * ({@code client.sectevent.SectEventFx}). Always: a = one of the ids below. b and f as noted; pos and target are the
 * packet's two points. Continuous effects carry a duration in ticks in b; the server refreshes them every few seconds.
 */
public final class SectEventFxIds {

	/** Cloud-step departure: a swirl of cloud motes and a pillar at pos. b = RGB of the disciple's root. */
	public static final int DEPART = 0;
	/** Cloud-step return at pos. b = RGB. */
	public static final int RETURN = 1;
	/** The duel ring at pos (the stele), radius f, kept for b ticks. */
	public static final int DUEL_RING = 2;
	/** Sparks of one landed duel hit at pos. b = RGB. */
	public static final int HIT_SPARK = 3;
	/** The Void Realm's portal swirl at pos for b ticks (radius f). */
	public static final int VOID_PORTAL = 4;
	/** A phantom dissolves at pos. */
	public static final int PHANTOM_DISSOLVE = 5;
	/** Crimson wind around the stele at pos for b ticks (the forbidden ground is open), radius f. */
	public static final int CRIMSON_WIND = 6;
	/** The ghostly Primordial Ark floating at pos for b ticks. */
	public static final int ARK = 7;
	/** A heart-fire surge at pos: b = 1 resisted (blue flash), 0 burnt (thrown out). */
	public static final int HEART_FIRE = 8;
	/** A small puff of work at a duty post at pos. b = the duty (1 water, 2 garden, 3 kitchen, 4 farm). */
	public static final int DUTY_PUFF = 9;
	/** A promotion-trial wave rises around pos (radius f). b = wave number. */
	public static final int TRIAL_WAVE = 10;
	/** The trial ends at pos: b = 1 passed, 0 failed. */
	public static final int TRIAL_END = 11;
	/** A rival's 12-block qi line from pos to target. b = RGB. */
	public static final int RIVAL_LINE = 12;
	/** A rival's element burst at pos, radius f. b = RGB. */
	public static final int RIVAL_BURST = 13;
	/** Champion crowned at pos: golden fanfare. b = 1 for a Turnamen Elit. */
	public static final int CHAMPION = 14;
	/** A team boards the ghostly ark from pos up to target. */
	public static final int ARK_BOARD = 15;
	/** Herbs refined into a Foundation pill at pos. b = pill quality RGB. */
	public static final int REFINE = 16;

	private SectEventFxIds() {
	}
}
