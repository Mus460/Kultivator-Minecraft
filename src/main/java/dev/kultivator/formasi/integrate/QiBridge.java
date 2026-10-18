package dev.kultivator.formasi.integrate;

import dev.kultivator.cultivation.Deviation;
import dev.kultivator.cultivation.Qi;

/**
 * Healing for players (DESIGN 24.4.3, 24.5): qi regeneration ×(1 + 0.3 × P) (≤ ×1.39) and the Qi Deviation timer ×1.33
 * inside an M4, read from the cached field (paused during the player's own tribulation by the handler).
 */
public final class QiBridge {

	public static void init() {
		Qi.addRegenFactor(player -> MeditationBridge.field(player).qiRegen());
		Deviation.addTimerFactor(player -> MeditationBridge.field(player).deviation());
	}

	private QiBridge() {
	}
}
