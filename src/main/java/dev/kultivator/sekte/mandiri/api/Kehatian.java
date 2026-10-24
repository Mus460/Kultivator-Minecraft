package dev.kultivator.sekte.mandiri.api;

import org.jetbrains.annotations.Nullable;

/**
 * Caution (Kehati-hatian, DESIGN 39.5.9): how far above its power a disciple travels, when it retreats and whether a
 * night on the road is allowed. Traits shift it one step (BERANI +1, PENAKUT -1) but never past the master's policy.
 * Only {@link #NEKAT} together with the config switch {@code mandiri.bolehGugur} (default false, needs user approval to
 * change) could ever end in death; with the switch off no DESIGN 39 path calls {@code Disciples.markFallen}. Pure data,
 * safe on both sides; never reorder.
 */
public enum Kehatian {
	/** Destination tier at most power - 2, retreat at 60 % health, no night on the road. */
	AMAN("aman", -2, 0.60D, false),
	/** Default: power - 1, retreat at 40 %, nights only at an inn. */
	WAJAR("wajar", -1, 0.40D, true),
	/** Power, retreat at 25 %. */
	BERANI("berani", 0, 0.25D, true),
	/** Power + 1, retreat at 10 %. */
	NEKAT("nekat", 1, 0.10D, true);

	private final String id;
	private final int tierRelatif;
	private final double mundurNyawa;
	private final boolean malamDiJalan;

	Kehatian(String id, int tierRelatif, double mundurNyawa, boolean malamDiJalan) {
		this.id = id;
		this.tierRelatif = tierRelatif;
		this.mundurNyawa = mundurNyawa;
		this.malamDiJalan = malamDiJalan;
	}

	public String id() {
		return id;
	}

	/** Highest destination tier relative to the party's power. */
	public int tierRelatif() {
		return tierRelatif;
	}

	/** Health fraction at which a stage is abandoned. */
	public double mundurNyawa() {
		return mundurNyawa;
	}

	public boolean malamDiJalan() {
		return malamDiJalan;
	}

	/** One step bolder (+1) or more careful (-1), clamped to the four values. */
	public Kehatian geser(int langkah) {
		int i = Math.max(0, Math.min(values().length - 1, ordinal() + langkah));
		return values()[i];
	}

	/** {@code kultivator.sekte.mandiri.inti.kehatian.<id>}. */
	public String kunciNama() {
		return "kultivator.sekte.mandiri.inti.kehatian." + id;
	}

	public static Kehatian byId(@Nullable String id, Kehatian fallback) {
		if (id != null) {
			for (Kehatian kehatian : values()) {
				if (kehatian.id.equals(id) || kehatian.name().equalsIgnoreCase(id)) {
					return kehatian;
				}
			}
		}
		return fallback;
	}
}
