package dev.kultivator.dunia.tempat.tema;

import net.minecraft.network.chat.Component;

/**
 * Names of the themed towns (DESIGN 38.T.1): {@code KotaTemaNames.of(seed, tema, index)} combines a first part
 * {@code kultivator.kota_tema.<tema>.nama.a.<i>} and a kind word {@code ...nama.b.<j>} through the theme's format key
 * {@code kultivator.kota_tema.<tema>.nama.format} ({@code %1$s} = part a, {@code %2$s} = part b; Indonesian puts the
 * kind first: "Pelabuhan Cahaya Zaitun", English after: "Olive Light Harbour"). The first-ring town of each theme
 * ({@code index} 0 of the ring) always gets pair (0, 0), the design's example name; later towns walk the pairs from a
 * seed offset so two towns of one theme rarely share a name. Text lives in {@code lang-src/tempat_tema.*}.
 */
public final class KotaTemaNames {

	/** Number of first parts per theme (pelabuhan, oasis, air, gunung, iblis). */
	private static final int[] A = {5, 4, 4, 4, 4};
	/** Number of kind words per theme. */
	private static final int[] B = {3, 2, 2, 2, 2};

	/** The display name of base candidate {@code index} of {@code tema} ({@code pertama}: a first-ring town). */
	public static Component of(long seed, Tema tema, int index, boolean pertama) {
		int[] pair = pasangan(seed, tema, index, pertama);
		String base = "kultivator.kota_tema." + tema.id() + ".nama.";
		return Component.translatable(base + "format", Component.translatable(base + "a." + pair[0]), Component.translatable(base + "b." + pair[1]));
	}

	/** The display name of a base candidate. */
	public static Component of(long seed, RencanaKotaTema.Calon calon) {
		return of(seed, calon.tema(), calon.index(), calon.pertama());
	}

	/** The (a, b) indices of a name. */
	public static int[] pasangan(long seed, Tema tema, int index, boolean pertama) {
		int na = A[tema.ordinal()];
		int nb = B[tema.ordinal()];
		if (pertama) {
			return new int[]{0, 0};
		}
		int pairs = na * nb;
		int offset = (int) Math.floorMod(RencanaKotaTema.hash(seed, 0x4A4DL, tema.ordinal(), 0), (long) pairs);
		int p = Math.floorMod(offset + index * 7, pairs);
		return new int[]{p % na, p / na};
	}

	private KotaTemaNames() {
	}
}
