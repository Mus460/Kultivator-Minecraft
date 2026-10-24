package dev.kultivator.client.dunia.tempat.tema.model;

/**
 * Render counters of the themed-town models (module 38TV-M) for the weak-laptop check {@code /temamodel info}: how many
 * themed entities were extracted for drawing, how many of them were past the small-animation LOD, and how many emissive
 * passes were submitted. Counted over one-second windows (no per-frame hook needed); the command divides the last full
 * window by the frame rate. Render thread only, plain fields, no allocation.
 */
final class TemaModelStat {

	private static long detik = -1L;
	private static int gambar;
	private static int jauh;
	private static int cahaya;
	private static int gambarLalu;
	private static int jauhLalu;
	private static int cahayaLalu;

	private TemaModelStat() {
	}

	private static void putar() {
		long now = System.nanoTime() / 1_000_000_000L;
		if (now != detik) {
			boolean berturut = now == detik + 1L;
			gambarLalu = berturut ? gambar : 0;
			jauhLalu = berturut ? jauh : 0;
			cahayaLalu = berturut ? cahaya : 0;
			gambar = 0;
			jauh = 0;
			cahaya = 0;
			detik = now;
		}
	}

	/** One themed entity extracted for this frame; {@code far} = past the small-animation LOD. */
	static void digambar(boolean far) {
		putar();
		gambar++;
		if (far) {
			jauh++;
		}
	}

	/** One emissive pass submitted. */
	static void bercahaya() {
		putar();
		cahaya++;
	}

	/** Last full second: {drawn, frozen, glow passes}. */
	static int[] detikLalu() {
		putar();
		return new int[] {gambarLalu, jauhLalu, cahayaLalu};
	}
}
