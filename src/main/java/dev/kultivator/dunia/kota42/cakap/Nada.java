package dev.kultivator.dunia.kota42.cakap;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The six tones a player can put on a dialog choice (DESIGN 42.5A.7). Fixed set (C42.K1, frozen in 42-G0); the numbers
 * (requirements, logistic weights, effects) live in {@code data/kultivator/tempat/percakapan/nada.json} (format in
 * {@link CakapData}). Order = the number keys 1-6 of the dialog screen; {@link #SOPAN} is the default.
 */
public enum Nada {
	SOPAN,
	AKRAB,
	MERAYU,
	MENGANCAM,
	MENYUAP,
	BERCANDA;

	/** Lower-case id used in data and lang ({@code kultivator.cakap.nada.<id>}). */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The tone of a data id, or null when unknown. */
	public static @Nullable Nada dari(String id) {
		for (Nada n : values()) {
			if (n.id().equals(id)) {
				return n;
			}
		}
		return null;
	}
}
