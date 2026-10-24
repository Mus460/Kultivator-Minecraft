package dev.kultivator.sekte.mandiri.cerita;

import java.util.UUID;

/**
 * Seeded variant choice of the Buku Harian (DESIGN 39.8.1): the sentence of an entry is picked with the seed
 * {@code (soul, hari, jenis)} plus a part salt, so the same entry always reads the same on every client, in every
 * language, after every reload. Pure arithmetic (SplitMix64), safe on both sides; no allocation.
 */
public final class PilihVarian {

	/** Part salts: opener, core sentence, reflection, closing, the 50 % closing roll, the weather of an unknown day. */
	public static final int BUKA = 1;
	public static final int INTI = 2;
	public static final int RENUNG = 3;
	public static final int TUTUP = 4;
	public static final int TUTUP_ADA = 5;
	public static final int CUACA = 6;

	/** The 64-bit seed of an entry. */
	public static long benih(UUID soul, long hari, String jenis, int jam) {
		long h = soul == null ? 0L : soul.getMostSignificantBits() ^ Long.rotateLeft(soul.getLeastSignificantBits(), 17);
		h ^= hari * 0x9E3779B97F4A7C15L;
		h ^= (jenis == null ? 0 : jenis.hashCode()) * 0xC2B2AE3D27D4EB4FL;
		h ^= jam * 0x165667B19E3779F9L;
		return aduk(h);
	}

	/** An index in {@code [0, n)} for part {@code salt} of the entry seeded {@code benih}. */
	public static int indeks(long benih, int salt, int n) {
		if (n <= 1) {
			return 0;
		}
		return (int) Math.floorMod(aduk(benih + salt * 0x632BE59BD9B4E019L), (long) n);
	}

	/** True with probability {@code persen}/100 for part {@code salt}. */
	public static boolean peluang(long benih, int salt, int persen) {
		return indeks(benih, salt, 100) < persen;
	}

	/** SplitMix64 finaliser. */
	public static long aduk(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	private PilihVarian() {
	}
}
