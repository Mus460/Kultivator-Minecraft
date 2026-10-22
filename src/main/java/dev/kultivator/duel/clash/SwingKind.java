package dev.kultivator.duel.clash;

/**
 * What kind of swing (DESIGN 35.3.3), shared by server and client; the ordinal travels in {@code DuelSwing.kind}, never
 * reorder. The multipliers are 35.3.5: {@code damage} on the attacker's weapon damage for BEBAS/HANDICAP, {@code share}
 * of the victim's max health for TEKAN_RANAH, {@code posture} on the posture the swing deals.
 */
public enum SwingKind {
	/** Light cut. */
	RINGAN(1.0F, 0.07F, 1.0F),
	/** Heavy cut (charged). */
	BERAT(1.6F, 0.12F, 1.0F),
	/** Thrust (crouch + click, or a spear's light cut). */
	TUSUK(1.2F, 0.09F, 1.0F),
	/** Full heavy cut with qi: the blade glows in the Dao colour. */
	QI(1.6F * 1.25F, 0.10F, 1.25F),
	/** Riposte during an Opening: only a Perfect Deflect stops it. */
	BALIK(1.3F, 0.10F, 1.3F),
	/** Deathblow on a staggered enemy. */
	PENENTU(2.5F, 0.30F, 1.0F),
	/** The Companion Sword's thrust (35.3.11). */
	TERBANG(0.5F, 0.05F, 0.6F),
	/** A Better Combat swing: its strength is read from the combo (heavy on the last attack). */
	BC(1.0F, 0.07F, 1.0F);

	/** Weapon damage multiplier (BEBAS / HANDICAP). */
	public final float damage;
	/** Share of the victim's max health (TEKAN_RANAH). */
	public final float share;
	/** Posture multiplier. */
	public final float posture;

	SwingKind(float damage, float share, float posture) {
		this.damage = damage;
		this.share = share;
		this.posture = posture;
	}

	public static SwingKind byOrdinal(int ordinal) {
		SwingKind[] values = values();
		return ordinal < 0 || ordinal >= values.length ? RINGAN : values[ordinal];
	}

	/** True for the heavy family (charged, posture H): BERAT and QI. */
	public boolean heavy() {
		return this == BERAT || this == QI;
	}

	/** True when no Deflect or guard can stop it (only an evade, 35.3.9). */
	public boolean unblockable() {
		return this == PENENTU;
	}
}
