package dev.kultivator.duel.clash;

/**
 * The direction of a cut (DESIGN 35.3.3), shared by server and client; the ordinal travels in {@code DuelInput.dir} and
 * {@code DuelSwing.dir}, so never reorder. "Kanan" is the <b>attacker's</b> right side: KANAN comes from the attacker's
 * right and sweeps to their left.
 */
public enum SwingDir {
	ATAS,
	ATAS_KANAN,
	KANAN,
	BAWAH_KANAN,
	BAWAH,
	BAWAH_KIRI,
	KIRI,
	ATAS_KIRI,
	TUSUK,
	PUTAR;

	/** The default combo order of light cuts: right, left, then the high right (35.3.1). */
	private static final SwingDir[] COMBO = {KANAN, KIRI, ATAS_KANAN};

	public static SwingDir byOrdinal(int ordinal) {
		SwingDir[] values = values();
		return ordinal < 0 || ordinal >= values.length ? KANAN : values[ordinal];
	}

	/** The {@code index}-th cut of the default combo (0, 1, 2, then again). */
	public static SwingDir combo(int index) {
		return COMBO[Math.floorMod(index, COMBO.length)];
	}

	/**
	 * True when this cut and {@code other} cross when the two fighters face each other, so the blades meet in the
	 * middle (35.3.3): the same side from both (each from their own right), the same high diagonals, high against low;
	 * a thrust only meets a thrust. Used for the blade lock.
	 */
	public boolean opposes(SwingDir other) {
		if (other == null) {
			return false;
		}
		if (this == TUSUK || other == TUSUK) {
			return this == TUSUK && other == TUSUK;
		}
		if (this == PUTAR || other == PUTAR) {
			return false;
		}
		if (this == other) {
			return this == KANAN || this == KIRI || this == ATAS || this == ATAS_KANAN || this == ATAS_KIRI;
		}
		return this == BAWAH && other == ATAS || this == ATAS && other == BAWAH;
	}

	/** The same cut from the other side (left-handed fighters and the mirrored poses). */
	public SwingDir mirror() {
		return switch (this) {
			case ATAS_KANAN -> ATAS_KIRI;
			case ATAS_KIRI -> ATAS_KANAN;
			case KANAN -> KIRI;
			case KIRI -> KANAN;
			case BAWAH_KANAN -> BAWAH_KIRI;
			case BAWAH_KIRI -> BAWAH_KANAN;
			default -> this;
		};
	}

	/** True for the cuts that come down from above (the Mountain Cleave family; "Tebasan Langit" from the air). */
	public boolean high() {
		return this == ATAS || this == ATAS_KANAN || this == ATAS_KIRI;
	}

	/** True for the cuts that rise from below (BAWAH is the Low Sweep when heavy). */
	public boolean low() {
		return this == BAWAH || this == BAWAH_KANAN || this == BAWAH_KIRI;
	}

	/** True for the horizontal and diagonal cuts that can throw a Sword Qi Wave (35.3.10). */
	public boolean wave() {
		return this == ATAS || this == KANAN || this == KIRI || this == ATAS_KANAN || this == ATAS_KIRI;
	}

	/**
	 * The KLASIK direction from the mouse movement of the last 150 ms (35.3.3), screen pixels after sensitivity with
	 * {@code dy} growing downwards: below 6 px the combo default, otherwise one of eight 45° sectors of
	 * {@code atan2(-dy, dx)}. Up on screen is ATAS; a movement to the left is a cut <b>from the right</b> (KANAN), since
	 * the blade travels left. The client (U) uses the same helper so both sides agree.
	 */
	public static SwingDir fromMouse(double dx, double dy, SwingDir comboDefault) {
		if (dx * dx + dy * dy < 36.0D) {
			return comboDefault;
		}
		double angle = Math.toDegrees(Math.atan2(-dy, dx));
		int sector = (int) Math.floorMod(Math.round(angle / 45.0D), 8L);
		// sector 0 = mouse right (blade travels right: from the left), 2 = up, 4 = left (from the right), 6 = down.
		return switch (sector) {
			case 0 -> KIRI;
			case 1 -> ATAS_KIRI;
			case 2 -> ATAS;
			case 3 -> ATAS_KANAN;
			case 4 -> KANAN;
			case 5 -> BAWAH_KANAN;
			case 6 -> BAWAH;
			default -> BAWAH_KIRI;
		};
	}
}
