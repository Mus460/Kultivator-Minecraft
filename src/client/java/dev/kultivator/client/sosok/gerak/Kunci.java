package dev.kultivator.client.sosok.gerak;

/**
 * One key of a clip (DESIGN 40.5.3): at tick {@code t} the channels in {@code nilai} (24, NaN = not written by this
 * key) are reached with the easing {@code masuk} from the previous key that wrote the same channel. Values are in the
 * stored units (radians for angles; the builder converts the degrees of the tables).
 */
public record Kunci(int t, float[] nilai, Easing masuk) {

	/** True when this key writes {@code kanal}. */
	public boolean menulis(int kanal) {
		return !Float.isNaN(nilai[kanal]);
	}
}
