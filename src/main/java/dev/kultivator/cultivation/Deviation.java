package dev.kultivator.cultivation;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import net.minecraft.server.level.ServerPlayer;

/** STUB (phase 0) — owned by P1-breakthrough. Qi Deviation: the injury of a failed breakthrough (DESIGN section 3). */
public final class Deviation {

	public static void init() {
	}

	public static void tick(ServerPlayer player, CultivationData data) {
	}

	/**
	 * Injures the player with a Qi Deviation (never lethal). {@code severity} 1.0 is a normal failed breakthrough; the
	 * duration and the injury scale with it.
	 */
	public static void apply(ServerPlayer player, double severity) {
	}

	/** Ends a deviation at once (healing pills, commands). */
	public static void clear(ServerPlayer player) {
		Cultivators.get(player).setDeviationTicks(0);
		Cultivators.dirty(player);
	}

	private Deviation() {
	}
}
