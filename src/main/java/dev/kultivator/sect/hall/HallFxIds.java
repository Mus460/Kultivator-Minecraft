package dev.kultivator.sect.hall;

/**
 * The effect ids of {@code FxType.HALL} (shared by the server and {@code client.hall.HallFx}): {@code a} is one of these,
 * {@code b} and {@code f} are listed per id; pos is where it plays, target a second point.
 */
public final class HallFxIds {

	/** A hall is founded or wakes up: pillar, rings and rising motes at the stele. b = RGB. */
	public static final int FOUNDED = 0;
	/** The hall rises a level: a tall jade pillar and a gold ring. b = new level. */
	public static final int LEVEL_UP = 1;
	/** Travellers' mist puff where a candidate arrives or leaves. b = mist RGB, f = 1 arriving, 0 leaving. */
	public static final int MIST = 2;
	/** The three tiers of the entrance test climb beside the candidate for 3 s. b = tiers climbed (0-3). */
	public static final int TEST_TIERS = 3;
	/** The stele's Duan pillar: pos = stele top, target = the candidate, b = soul colour RGB, f = Duan (1-9). */
	public static final int DUAN = 4;
	/** A candidate accepted: jade ring and a bow. b = root RGB. */
	public static final int RECRUIT = 5;
	/** A promotion: 3 s robe flash in the new trim colour. b = trim RGB, f = new rank. */
	public static final int PROMOTION = 6;
	/** A lost art restored: glyphs rising around the stele. b = set (1-3). */
	public static final int RESTORE = 7;
	/** The hall falls dormant: grey motes fall at the stele. */
	public static final int DORMANT = 8;
	/** An Immortal Ascension Token is used: gold motes spiral into the stele. */
	public static final int TOKEN = 9;
	/** Candidates arrived (sent to the master only): a toast, no particles. b = count, f = 1 on a war day. */
	public static final int ARRIVAL_TOAST = 10;

	private HallFxIds() {
	}
}
