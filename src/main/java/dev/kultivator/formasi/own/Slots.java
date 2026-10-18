package dev.kultivator.formasi.own;

/**
 * Active formation slots of a hall (DESIGN 24.2.4): registered cores beyond this number stay switched off
 * ({@code SUSPENDED}, "Slot aktif penuh"). The Formasi Pelindung, the Formasi Agung Sekte and personal Qi seats are free.
 */
public final class Slots {

	/** Slots by hall level: 1-2 → 2, 3-4 → 3, 5-6 → 4, 7-8 → 5, 9 → 6 (0 without a hall). */
	public static int active(int hallLevel) {
		if (hallLevel <= 0) {
			return 0;
		}
		return Math.min(6, 2 + (hallLevel - 1) / 2);
	}

	private Slots() {
	}
}
