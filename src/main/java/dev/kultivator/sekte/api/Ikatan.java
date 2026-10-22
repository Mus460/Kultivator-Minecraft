package dev.kultivator.sekte.api;

import org.jetbrains.annotations.Nullable;

/**
 * The kind of tie between two disciples of one sect (DESIGN 36.2.2, module R). R derives it from the pair's affinity
 * (-100..100) and history; {@link #positive()} ties help in fights and missions, negative ones cost contentment (read by
 * P). The id is saved and never renamed.
 * <p>
 * Sumber: sworn brotherhood (the Peach Garden Oath, <i>Romance of the Three Kingdoms</i> ch. 1), Dao companions (Wang
 * Lin and Li Muwan, <i>Renegade Immortal</i>), the senior and junior rivalries of <i>A Will Eternal</i>.
 */
public enum Ikatan {
	NONE("none", false, false),
	KENALAN("kenalan", false, false),
	SAHABAT("sahabat", true, false),
	SAUDARA_ANGKAT("saudara_angkat", true, false),
	KEKASIH("kekasih", true, false),
	PASANGAN_DAO("pasangan_dao", true, false),
	SAINGAN("saingan", false, true),
	DENDAM("dendam", false, true);

	private final String id;
	private final boolean positive;
	private final boolean negative;

	Ikatan(String id, boolean positive, boolean negative) {
		this.id = id;
		this.positive = positive;
		this.negative = negative;
	}

	public String id() {
		return id;
	}

	/** Friendship, sworn kin, love and Dao companions. */
	public boolean positive() {
		return positive;
	}

	/** Rivalry and grudge. */
	public boolean negative() {
		return negative;
	}

	public String key() {
		return "kultivator.sekte.ikatan." + id;
	}

	public static Ikatan byId(@Nullable String id) {
		if (id != null) {
			for (Ikatan ikatan : values()) {
				if (ikatan.id.equals(id)) {
					return ikatan;
				}
			}
		}
		return NONE;
	}
}
