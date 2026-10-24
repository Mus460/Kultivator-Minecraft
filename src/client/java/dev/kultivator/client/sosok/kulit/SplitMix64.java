package dev.kultivator.client.sosok.kulit;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The shared random generator of the Sosok composer (DESIGN 40.3.10, module 40K): SplitMix64 exactly as
 * {@code tools/sosok_lib.py}, so a look resolved here is the same look the Python previews draw
 * ({@code gen_sosok_pratinjau.py --benih} and {@code /sosok dump} give identical atlases).
 * <p>
 * One generator per draw group ({@link #untuk}): adding a draw to one group never changes another group's result.
 * Weighted picks use {@code (nextLong() >>> 11) / 2^53}, exactly the Python double arithmetic.
 */
public final class SplitMix64 {

	private long state;

	public SplitMix64(long seed) {
		this.state = seed;
	}

	/** The generator of one draw group: {@code SplitMix64(benih * 0x100000001B3 ^ fnv32(group))}. */
	public static SplitMix64 untuk(int benih, String kelompok) {
		return new SplitMix64(((long) benih * 0x100000001B3L) ^ (fnv32(kelompok) & 0xFFFFFFFFL));
	}

	public long nextLong() {
		state += 0x9E3779B97F4A7C15L;
		long z = state;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** Uniform double in [0, 1) from the top 53 bits. */
	public double next01() {
		return (nextLong() >>> 11) / 9007199254740992.0D;
	}

	/** True with {@code persen} % chance (one draw). */
	public boolean chance(int persen) {
		return next01() * 100.0D < persen;
	}

	/** Uniform int in [0, n) (one draw); 0 without a draw for n <= 0 (as the Python generator). */
	public int below(int n) {
		return n > 0 ? (int) (next01() * n) : 0;
	}

	/**
	 * Weighted pick from {@code items} (weights <= 0 never come out); null for an empty or all-zero list. Always one
	 * draw, also when the list is empty (the Python generator draws before it checks the total).
	 */
	public <T> T pick(List<Berbobot<T>> items) {
		int total = 0;
		for (Berbobot<T> b : items) {
			if (b.bobot() > 0) {
				total += b.bobot();
			}
		}
		double r = next01() * total;
		if (total <= 0) {
			return null;
		}
		int acc = 0;
		T last = null;
		for (Berbobot<T> b : items) {
			if (b.bobot() <= 0) {
				continue;
			}
			acc += b.bobot();
			last = b.nilai();
			if (r < acc) {
				return b.nilai();
			}
		}
		return last;
	}

	/** A value with an integer weight. */
	public record Berbobot<T>(T nilai, int bobot) {
	}

	/** FNV-1a 32 over the UTF-8 bytes of {@code text} (as a signed int; callers mask with 0xFFFFFFFFL). */
	public static int fnv32(String text) {
		int h = 0x811C9DC5;
		for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
			h ^= b & 0xFF;
			h *= 0x01000193;
		}
		return h;
	}

	/** One step of FNV-1a 64 over an int fed as 4 little-endian bytes (the atlas cache key, {@link SosokRupa#kunci}). */
	public static long fnv64(long h, int v) {
		for (int i = 0; i < 4; i++) {
			h ^= (v >>> (8 * i)) & 0xFF;
			h *= 0x100000001B3L;
		}
		return h;
	}

	public static final long FNV64_AWAL = 0xCBF29CE484222325L;
}
