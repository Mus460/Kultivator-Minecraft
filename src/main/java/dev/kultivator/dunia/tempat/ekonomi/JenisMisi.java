package dev.kultivator.dunia.tempat.ekonomi;

import org.jetbrains.annotations.Nullable;

/**
 * A guild mission kind (DESIGN 38.M.3, 38.T.10; registered through {@link EkonomiApi#daftarJenisMisi}). The graded
 * board offers a kind only between its ranks, toward a real place of {@code jenisSitus} when given (nearest known or
 * located site). Story text keys: {@code kultivator.misi.<id>.judul|cerita|tikungan}, in the registering module lang
 * fragment.
 *
 * @param id          mission kind id ({@code binatang}, {@code begal}, {@code muatan}, {@code buronan_iblis}, ...)
 * @param pangkatMin  lowest rank {@link #E} .. {@link #S}
 * @param pangkatMaks highest rank
 * @param jenisSitus  site kind the mission points at ({@code gua/sarang_binatang}, {@code pos/kemah_begal}), or null
 * @param bobot       relative weight on the board (1-100)
 * @param pemilik     registering module label ({@code 38EK}, {@code 38TK}) for {@code uji ekonomi misi}
 */
public record JenisMisi(String id, int pangkatMin, int pangkatMaks, @Nullable String jenisSitus, int bobot, String pemilik) {

	public static final int E = 0;
	public static final int D = 1;
	public static final int C = 2;
	public static final int B = 3;
	public static final int A = 4;
	public static final int S = 5;
	/** Rank letters by index. */
	public static final String HURUF = "EDCBAS";

	public JenisMisi {
		pangkatMin = Math.clamp(pangkatMin, E, S);
		pangkatMaks = Math.clamp(pangkatMaks, pangkatMin, S);
		bobot = Math.clamp(bobot, 1, 100);
	}

	/** True when the kind may be offered at rank {@code pangkat}. */
	public boolean boleh(int pangkat) {
		return pangkat >= pangkatMin && pangkat <= pangkatMaks;
	}
}
