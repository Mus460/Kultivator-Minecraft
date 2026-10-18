package dev.kultivator.formasi.integrate;

import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.formasi.stack.Stacking;

/**
 * Breakthrough quality from formations (DESIGN 24.5): the sum of 7c contributions (A1 adds +0.04 through its field),
 * capped at {@code QUALITY_FORMATION_CAP = +0.06}, inside the −0.10 … +0.40 clamp of {@code Breakthrough}.
 */
public final class BreakthroughBridge {

	public static void init() {
		Breakthrough.addQualityBonusProvider((player, data, target) -> Math.min(Stacking.QUALITY_FORMATION_CAP, MeditationBridge.field(player).quality()));
	}

	private BreakthroughBridge() {
	}
}
