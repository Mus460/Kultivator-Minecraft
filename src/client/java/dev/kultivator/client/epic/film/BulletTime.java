package dev.kultivator.client.epic.film;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicClock;
import dev.kultivator.client.epic.EpicSettings;

/**
 * Bullet time of the film (DESIGN 30.2.9): σ 0.03-0.1 for ≤ 30 real ticks ({@link EpicClock#MAX_WINDOW}), ≥ 6 s apart
 * ({@link EpicClock#MIN_GAP}), only with "Waktu beku" and "Gerak lambat" on and never under "Kurangi gerak". What freezes:
 * everything that reads presentation time (7b layers, Phase 10 and 10b world FX, model props, display layers, the
 * ultimate drivers, qi and film particles). What never freezes: vanilla entities and particles, the server, damage, and
 * the film camera itself, which keeps moving in real time.
 */
public final class BulletTime {

	/** Priority above every 7b slow-motion request (a film owns the moment). */
	public static final int PRIORITY = 90;

	public static boolean start(float sigma, int realTicks) {
		if (!EpicSettings.bullet() || !EpicSettings.slowmo() || EffectQuality.reduceMotion()) {
			return false;
		}
		return EpicClock.bullet(sigma, realTicks, PRIORITY);
	}

	public static boolean running() {
		return EpicClock.bulletTime();
	}

	private BulletTime() {
	}
}
