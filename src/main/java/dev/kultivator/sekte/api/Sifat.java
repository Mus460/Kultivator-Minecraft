package dev.kultivator.sekte.api;

import org.jetbrains.annotations.Nullable;

/**
 * The twelve personality traits of a disciple (DESIGN 36.2.1; module P owns the roll and the effects, every module may
 * read them through {@code SekteApi.hati()}). Six axes of two opposite traits; a disciple never holds both ends of one
 * axis. The id is saved (in {@code extra.hati.sifat}) and never renamed.
 * <p>
 * Sumber: the casts of the cultivation novels: Han Li's caution and diligence (<i>A Record of a Mortal's Journey</i>),
 * Bai Xiaochun's fear and cunning (<i>A Will Eternal</i>), Xiao Yan's pride and his loyalty to Yao Lao (<i>Battle Through
 * the Heavens</i>), Wang Lin's grudges (<i>Renegade Immortal</i>).
 */
public enum Sifat {
	RAJIN("rajin", 0),
	MALAS("malas", 0),
	SOMBONG("sombong", 1),
	RENDAH_HATI("rendah_hati", 1),
	SETIA("setia", 2),
	LICIK("licik", 2),
	BERANI("berani", 3),
	PENAKUT("penakut", 3),
	AMBISIUS("ambisius", 4),
	TENANG("tenang", 4),
	PENYAYANG("penyayang", 5),
	PENDENDAM("pendendam", 5);

	/** Number of axes ({@link #axis()} 0-5). */
	public static final int AXES = 6;

	private final String id;
	private final int axis;

	Sifat(String id, int axis) {
		this.id = id;
		this.axis = axis;
	}

	/** Saved id (also the lang suffix: {@code kultivator.sekte.sifat.<id>} and {@code .desc}). */
	public String id() {
		return id;
	}

	/** The axis 0-5: diligence, pride, loyalty, courage, ambition, heart. */
	public int axis() {
		return axis;
	}

	/** The other end of the same axis. */
	public Sifat opposite() {
		return values()[ordinal() ^ 1];
	}

	/** The lang key of the trait's name. */
	public String key() {
		return "kultivator.sekte.sifat." + id;
	}

	public static @Nullable Sifat byId(@Nullable String id) {
		if (id != null) {
			for (Sifat sifat : values()) {
				if (sifat.id.equals(id)) {
					return sifat;
				}
			}
		}
		return null;
	}
}
