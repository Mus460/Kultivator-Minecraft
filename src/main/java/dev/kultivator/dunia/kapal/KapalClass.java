package dev.kultivator.dunia.kapal;

import dev.kultivator.data.Realm;

/**
 * The three ship classes of DESIGN 34.5.15 and their layouts. The seat, deck and helm numbers are the ones
 * {@code tools/gen_dunia_kapal.py} prints for its hulls (local blocks relative to the root on the keel bottom: x across
 * the ship, y up, z toward the bow); the client draws the same hulls from {@code assets/kultivator/dunia/kapal/<id>.json}.
 * Pure data, shared by both sides.
 */
public enum KapalClass {

	/** Perahu Awan: personal, two seats, Golden Core. */
	PERAHU("perahu_awan", "perahu", 2, Realm.GOLDEN_CORE, 0, 12.0F, 200, 4.0F, 7, 13, 12, 3.0F,
			new float[][]{{0.0F, 3.5F, -3.0F}, {0.0F, 3.5F, 3.0F}},
			new float[][]{{0.0F, -3.5F, 3.0F, 2.0F}, {0.0F, -1.0F, 5.0F, 3.0F}, {0.0F, 1.5F, 5.0F, 2.0F}, {0.0F, 3.5F, 3.0F, 2.0F}}),
	/** Kapal Sekte: eight seats, Nascent Soul and a sect hall of level 3 or more. */
	SEKTE("kapal_sekte", "sekte", 8, Realm.NASCENT_SOUL, 3, 16.0F, 400, 2.6F, 11, 25, 20, 4.0F,
			new float[][]{{0.0F, 4.5F, -3.0F}, {-3.0F, 4.5F, 1.0F}, {3.0F, 4.5F, 1.0F}, {-3.0F, 4.5F, 3.0F}, {3.0F, 4.5F, 3.0F},
					{-3.0F, 4.5F, 5.0F}, {3.0F, 4.5F, 5.0F}, {0.0F, 4.5F, 9.0F}},
			new float[][]{{0.0F, -3.0F, 9.0F, 3.0F}, {0.0F, 0.0F, 9.0F, 3.0F}, {0.0F, 3.0F, 9.0F, 3.0F}, {0.0F, 5.0F, 9.0F, 1.0F},
					{0.0F, 7.0F, 7.0F, 3.0F}, {0.0F, 9.5F, 5.0F, 2.0F}, {0.0F, 11.0F, 3.0F, 1.0F}}),
	/** Bahtera Langit: twelve seats, armoured, Deity Transformation and a sect hall of level 7 or more. */
	BAHTERA("bahtera_langit", "bahtera", 12, Realm.DEITY_TRANSFORMATION, 7, 20.0F, 800, 1.7F, 17, 37, 28, 5.0F,
			new float[][]{{0.0F, 5.5F, -4.0F}, {-4.0F, 5.5F, 1.0F}, {4.0F, 5.5F, 1.0F}, {-4.0F, 5.5F, 3.0F}, {4.0F, 5.5F, 3.0F},
					{-4.0F, 5.5F, 5.0F}, {4.0F, 5.5F, 5.0F}, {-4.0F, 5.5F, 7.0F}, {4.0F, 5.5F, 7.0F}, {-4.0F, 5.5F, 9.0F},
					{4.0F, 5.5F, 9.0F}, {0.0F, 5.5F, 13.0F}},
			new float[][]{{0.0F, -16.5F, 9.0F, 2.0F}, {-5.5F, -9.5F, 2.0F, 8.0F}, {5.5F, -9.5F, 2.0F, 8.0F}, {0.0F, -1.5F, 13.0F, 8.0F},
					{0.0F, 6.0F, 13.0F, 7.0F}, {0.0F, 11.0F, 11.0F, 3.0F}, {0.0F, 13.0F, 9.0F, 1.0F}, {0.0F, 14.0F, 7.0F, 1.0F},
					{0.0F, 15.5F, 5.0F, 2.0F}, {0.0F, 17.0F, 3.0F, 1.0F}});

	private final String model;
	private final String command;
	private final int seats;
	private final Realm realm;
	private final int hallLevel;
	private final float topSpeed;
	private final int hull;
	private final float turnRate;
	private final int width;
	private final int length;
	private final int height;
	private final float deck;
	private final float[][] seatSpots;
	private final float[][] deckTiles;

	KapalClass(String model, String command, int seats, Realm realm, int hallLevel, float topSpeed, int hull, float turnRate, int width,
			int length, int height, float deck, float[][] seatSpots, float[][] deckTiles) {
		this.model = model;
		this.command = command;
		this.seats = seats;
		this.realm = realm;
		this.hallLevel = hallLevel;
		this.topSpeed = topSpeed;
		this.hull = hull;
		this.turnRate = turnRate;
		this.width = width;
		this.length = length;
		this.height = height;
		this.deck = deck;
		this.seatSpots = seatSpots;
		this.deckTiles = deckTiles;
	}

	/** The model id ({@code assets/kultivator/dunia/kapal/<id>.json}), also the lang suffix of the class name. */
	public String model() {
		return model;
	}

	/** The argument of {@code uji kapal beri}. */
	public String command() {
		return command;
	}

	public int seats() {
		return seats;
	}

	/** The realm the captain needs to launch it. */
	public Realm realm() {
		return realm;
	}

	/** The sect hall level the owner needs (0 = none). */
	public int hallLevel() {
		return hallLevel;
	}

	/** Top speed in blocks per second before the {@code maxSpeed} cap. */
	public float topSpeed() {
		return topSpeed;
	}

	/** Hull points. */
	public int hull() {
		return hull;
	}

	/** Degrees per tick at full rudder. */
	public float turnRate() {
		return turnRate;
	}

	/** Footprint across the ship (blocks). */
	public int width() {
		return width;
	}

	/** Footprint along the ship (blocks). */
	public int length() {
		return length;
	}

	/** Model height (blocks, masts included). */
	public int height() {
		return height;
	}

	/** Height of the deck surface above the keel bottom. */
	public float deck() {
		return deck;
	}

	/** Seat {@code i}: {x, y, z} local (seat 0 is the helm). */
	public float[] seat(int i) {
		return seatSpots[Math.max(0, Math.min(seatSpots.length - 1, i))];
	}

	/** The deck colliders: {centre x, centre z, width x, depth z} local, at most 10. */
	public float[][] deckTiles() {
		return deckTiles;
	}

	/** Half the diagonal of the footprint (a generous radius for culling and room checks). */
	public float radius() {
		return (float) Math.sqrt(width * width + length * length) * 0.5F;
	}

	public static KapalClass byOrdinal(int ordinal) {
		KapalClass[] all = values();
		return all[Math.max(0, Math.min(all.length - 1, ordinal))];
	}

	/** The class of a {@code uji kapal beri} argument or a model id, or null. */
	public static KapalClass byName(String name) {
		for (KapalClass each : values()) {
			if (each.command.equals(name) || each.model.equals(name)) {
				return each;
			}
		}
		return null;
	}

	/** Local (x, z) to world offsets for yaw {@code yawDegrees} (the renderer turns the model the same way). */
	public static double[] toWorld(double lx, double lz, float yawDegrees) {
		double r = Math.toRadians(yawDegrees);
		double c = Math.cos(r);
		double s = Math.sin(r);
		return new double[]{lx * c - lz * s, lx * s + lz * c};
	}
}
