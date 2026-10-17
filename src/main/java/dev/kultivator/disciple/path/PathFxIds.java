package dev.kultivator.disciple.path;

/**
 * Sub effects ({@code a}) of the {@code FxType.STAR} packets of module D, shared by the server and
 * {@code client.disciple.path.PathFx}. Never renumber.
 */
public final class PathFxIds {

	/** A star condenses: pos = chest, b = the new star count. */
	public static final int CONDENSE = 0;
	/** The calamity omen glyph ring: pos = feet, b = calamity k, f = seconds left. */
	public static final int CALAMITY_OMEN = 1;
	/** A calamity or lamp trial passed: pos = feet, b = k. */
	public static final int CALAMITY_PASS = 2;
	/** Soul Seal ritual: pos = disciple, target = master, f = progress 0..1. */
	public static final int SEAL_GLYPH = 3;
	/** The Soul Seal is set: pos = disciple. */
	public static final int SEALED = 4;
	/** Heir ritual: jade light between the dantians, pos = master, target = disciple, f = progress. */
	public static final int HEIR_LINK = 5;
	/** The heir is named: pos = disciple. */
	public static final int HEIR_NAMED = 6;
	/** The old sage in the ring appears: pos = disciple. */
	public static final int RING_GHOST = 7;
	/** The disciple surpasses its master: a golden pillar at pos. */
	public static final int SURPASS = 8;
	/** Tahan Petir: an arc from the disciple (target) to the master (pos). */
	public static final int TAHAN_ARC = 9;
	/** Hu Fa seal under the guarding master: pos = master, target = disciple. */
	public static final int HUFA_SEAL = 10;
	/** Dao foundation pillars rise: pos = disciple, b = type (1 Fana .. 4 Sempurna). */
	public static final int FOUNDATION = 11;
	/** A Divine Clone appears (b = 0) or fades (b = 1) at pos. */
	public static final int CLONE = 12;
	/** Jiwa Guru Turun: the master's aura on the heir, pos = disciple, b = master's realm colour, f = seconds. */
	public static final int DESCEND = 13;
	/** A hex is created: pos = disciple, b = hexes now. */
	public static final int HEX = 14;
	/** A Jurus Pemusnah (block-safe form): pos = origin or centre, target = end or target, b = art ordinal, f = radius or length. */
	public static final int ART = 15;
	/** A build-up blow of the Great Sumeru Hammer: pos = disciple, b = blow 1..9, f = radius. */
	public static final int ART_CHARGE = 16;
	/** A soul lamp is put out: pos = disciple, b = lamps out. */
	public static final int LAMP = 17;
	/** Tersegel pins monsters: pos = victim, f = radius. */
	public static final int PIN = 18;
	/** Balas Budi: the disciple appears beside its master, pos = master, target = disciple. */
	public static final int REPAY = 19;
	/** Tanding dengan Guru: the duel ring, pos = centre, f = radius. */
	public static final int DUEL = 20;

	private PathFxIds() {
	}
}
