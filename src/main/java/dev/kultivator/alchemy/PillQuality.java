package dev.kultivator.alchemy;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * Grade of a refined pill (DESIGN section 9), stored in the {@code kultivator:pill_quality} component as the ordinal
 * (0..3, so never reorder). It multiplies the pill's effect; better pills are also purer (less toxin).
 */
public enum PillQuality {
	LOW("low", 0.8D, 1.2D, 0xB4B4B4),
	MID("mid", 1.0D, 1.0D, 0x7CFF8A),
	HIGH("high", 1.25D, 0.8D, 0x6FB8FF),
	PERFECT("perfect", 1.6D, 0.5D, 0xFFC23C);

	private static final PillQuality[] ALL = values();

	private final String id;
	private final double multiplier;
	private final double toxinFactor;
	private final int color;

	PillQuality(String id, double multiplier, double toxinFactor, int color) {
		this.id = id;
		this.multiplier = multiplier;
		this.toxinFactor = toxinFactor;
		this.color = color;
	}

	public String id() {
		return id;
	}

	/** Multiplies every number of the pill's effect: 0.8 / 1.0 / 1.25 / 1.6. */
	public double multiplier() {
		return multiplier;
	}

	/** Multiplies the pill's toxin: low pills are cloudier, perfect ones nearly pure. */
	public double toxinFactor() {
		return toxinFactor;
	}

	/** RGB of the quality (item name, effects). */
	public int color() {
		return color;
	}

	/** One grade higher (Perfect stays Perfect). */
	public PillQuality up() {
		return this == PERFECT ? PERFECT : ALL[ordinal() + 1];
	}

	/** "Rendah" / "Low" in the quality colour. */
	public MutableComponent displayName() {
		return Component.translatable("kultivator.alchemy.quality." + id).withColor(color);
	}

	/** Clamped to 0..3. */
	public static PillQuality byOrdinal(int ordinal) {
		return ALL[Math.max(0, Math.min(ALL.length - 1, ordinal))];
	}

	/** Null when unknown. */
	public static @Nullable PillQuality byId(String id) {
		for (PillQuality quality : ALL) {
			if (quality.id.equals(id)) {
				return quality;
			}
		}
		return null;
	}
}
