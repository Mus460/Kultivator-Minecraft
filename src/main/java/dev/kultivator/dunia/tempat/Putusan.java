package dev.kultivator.dunia.tempat;

/**
 * The shared terrain verdict of one planned place (DESIGN 38.B.2.4, 38.T.2.2): every district start and the server
 * simulation compute the same value from the seed, so a multi-start place is born whole or not at all.
 *
 * @param layak  true when the place may be born
 * @param dasarY the street floor height of the place (lot floor {@code y = 4} sits on it)
 * @param alasan short reason for {@code uji tempat rencana} ("air 5 > 3", "selisih 31 > 20", "bioma", "ok")
 */
public record Putusan(boolean layak, int dasarY, String alasan) {

	/** Not suitable (no reason known yet: plan switched off or no candidate). */
	public static final Putusan TIDAK = new Putusan(false, 0, "mati");

	public static Putusan tidak(String alasan) {
		return new Putusan(false, 0, alasan);
	}

	public static Putusan ya(int dasarY) {
		return new Putusan(true, dasarY, "ok");
	}
}
