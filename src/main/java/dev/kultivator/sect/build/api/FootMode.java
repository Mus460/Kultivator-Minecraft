package dev.kultivator.sect.build.api;

/**
 * What happens to the ground site of path A at the Ascension (DESIGN 37.3.6, "Kaki Gunung"). The ordinal is saved:
 * never reorder, add new modes at the end only.
 * <ul>
 * <li>{@link #TETAP}: the ground site stays as the Mountain Foot (an orphan under {@code LangitKeys.footKey}): its cells
 * stay sect cells (refunds, spawn proofing, Kehancuran protection), no new components.</li>
 * <li>{@link #BONGKAR}: the ground site is taken down with refunds whenever the owner is near (as "Pindah &amp;
 * bongkar").</li>
 * <li>{@link #TINGGALKAN}: the ground blocks become ordinary blocks (as "Pindah &amp; tinggalkan").</li>
 * </ul>
 */
public enum FootMode {
	TETAP,
	BONGKAR,
	TINGGALKAN;

	private static final FootMode[] VALUES = values();

	public static FootMode byOrdinal(int ordinal) {
		return ordinal < 0 || ordinal >= VALUES.length ? TETAP : VALUES[ordinal];
	}

	/** The command word ({@code tetap}, {@code bongkar}, {@code tinggalkan}). */
	public String word() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	public static FootMode byWord(String word) {
		for (FootMode mode : VALUES) {
			if (mode.word().equals(word)) {
				return mode;
			}
		}
		return TETAP;
	}
}
