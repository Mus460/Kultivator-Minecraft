package dev.kultivator.musuh;

/** Sub-ids ({@code a}) of {@code FxType.MUSUH} (DESIGN 25.2 item 4), owned by P8-musuh. */
public final class MusuhFxIds {

	/** A rogue vanishes in a cloud-step puff (b: rgb). */
	public static final int VANISH = 0;
	/** A rogue steps out of a dark swirl (b: rgb, f: tier). */
	public static final int ARRIVE = 1;
	/** A channelling raider feeds the blood-red seal: pos the rogue, target the stele (b: tier). */
	public static final int SEAL = 2;
	/** The tier-3 telegraph: a closing ring at pos (b: rgb, f: radius). */
	public static final int MARK = 3;
	/** The mark strikes (b: rgb, f: radius). */
	public static final int MARK_HIT = 4;
	/** Segel Darah, the Patriarch's 6-block ring (f: radius). */
	public static final int BLOOD_SEAL = 5;
	/** Segel Darah strikes (f: radius). */
	public static final int BLOOD_HIT = 6;
	/** A bandit camp's smoke column (f: seconds to keep smoking). */
	public static final int CAMP_SMOKE = 7;
	/** Killing intent before an ambush: dark motes gather around the player (b: count). */
	public static final int AMBUSH_ALERT = 8;

	/** Telegraph lengths in ticks (shared by the server timing and the client rings). */
	public static final int MARK_TICKS = 30;
	public static final int BLOOD_TICKS = 40;

	private MusuhFxIds() {
	}
}
