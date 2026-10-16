package dev.kultivator.data;

import net.minecraft.network.chat.Component;

/** Quality of a spiritual root; multiplies cultivation speed and max qi. The ordinal is saved. */
public enum RootGrade {
	TRASH("trash", 0.6D, 0x8A8A8A),
	LOW("low", 0.85D, 0xC8C8C8),
	MID("mid", 1.0D, 0x7CE07C),
	HIGH("high", 1.3D, 0x56B4FF),
	HEAVENLY("heavenly", 1.7D, 0xFFC93C);

	private final String id;
	private final double multiplier;
	private final int color;

	RootGrade(String id, double multiplier, int color) {
		this.id = id;
		this.multiplier = multiplier;
		this.color = color;
	}

	public String id() {
		return id;
	}

	/** XP and max-qi multiplier. */
	public double multiplier() {
		return multiplier;
	}

	public int color() {
		return color;
	}

	public RootGrade up() {
		return this == HEAVENLY ? this : values()[ordinal() + 1];
	}

	public Component displayName() {
		return Component.translatable("kultivator.root_grade." + id);
	}

	public static RootGrade byOrdinal(int ordinal) {
		RootGrade[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, ordinal))];
	}

	/** Null when unknown. */
	public static RootGrade byId(String id) {
		for (RootGrade grade : values()) {
			if (grade.id.equals(id)) {
				return grade;
			}
		}
		return null;
	}
}
