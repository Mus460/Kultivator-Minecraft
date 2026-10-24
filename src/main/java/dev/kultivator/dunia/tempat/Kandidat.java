package dev.kultivator.dunia.tempat;

/**
 * One candidate place of a deterministic plan (DESIGN 38.B.2.2, contract C38.W2): the capital, a sect compound or a
 * themed city that MAY be born at {@code (pusatX, pusatZ)} if its terrain verdict ({@link Putusan}) says so. Pure data
 * from the world seed: every structure start of the place and the server simulation compute the same candidates.
 *
 * @param rencana the plan id of {@link RencanaRegistry} ({@code kota_besar}, {@code kompleks_sekte}, {@code kota_tema_<tema>})
 * @param index   stable index inside that plan (also the key of the terrain-verdict cache and of the place names)
 * @param pusatX  block x of the place centre
 * @param pusatZ  block z of the place centre
 * @param putar   whole-place rotation in clockwise quarter turns 0..3 ({@code Rotation.values()} order:
 *                NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90)
 * @param pertama true for the guaranteed first-ring candidate
 */
public record Kandidat(String rencana, int index, int pusatX, int pusatZ, int putar, boolean pertama) {

	public Kandidat {
		putar = Math.floorMod(putar, 4);
	}

	/** Chunk x of the centre. */
	public int chunkX() {
		return pusatX >> 4;
	}

	/** Chunk z of the centre. */
	public int chunkZ() {
		return pusatZ >> 4;
	}
}
