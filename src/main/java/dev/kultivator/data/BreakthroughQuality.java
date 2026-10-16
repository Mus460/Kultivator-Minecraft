package dev.kultivator.data;

import net.minecraft.network.chat.Component;

/** How well a breakthrough went; recorded per realm and turned into bonuses. The ordinal is saved. */
public enum BreakthroughQuality {
	CRACKED("cracked", 0.9D, 0.0D, 0xB06A4A),
	NORMAL("normal", 1.0D, 2.0D, 0xE0E0E0),
	PERFECT("perfect", 1.15D, 5.0D, 0x6CE0FF),
	HEAVENLY("heavenly", 1.3D, 10.0D, 0xFFD54A);

	private final String id;
	private final double statFactor;
	private final double comprehensionBonus;
	private final int color;

	BreakthroughQuality(String id, double statFactor, double comprehensionBonus, int color) {
		this.id = id;
		this.statFactor = statFactor;
		this.comprehensionBonus = comprehensionBonus;
		this.color = color;
	}

	public String id() {
		return id;
	}

	/** Multiplies the stat bonuses of cultivation. */
	public double statFactor() {
		return statFactor;
	}

	/** Dao comprehension gained with a breakthrough of this quality. */
	public double comprehensionBonus() {
		return comprehensionBonus;
	}

	public int color() {
		return color;
	}

	public Component displayName() {
		return Component.translatable("kultivator.quality." + id);
	}

	/** Null for -1 (not reached) or unknown. */
	public static BreakthroughQuality byOrdinal(int ordinal) {
		return ordinal < 0 || ordinal >= values().length ? null : values()[ordinal];
	}

	public static BreakthroughQuality fromScore(double score) {
		if (score < 0.35D) {
			return CRACKED;
		}
		if (score < 0.7D) {
			return NORMAL;
		}
		return score < 0.9D ? PERFECT : HEAVENLY;
	}
}
