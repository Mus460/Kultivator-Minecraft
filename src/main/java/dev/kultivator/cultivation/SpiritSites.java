package dev.kultivator.cultivation;

import net.minecraft.server.level.ServerPlayer;

/** STUB (phase 0) — owned by P1-meditation. Spiritual sites that speed up meditation (DESIGN section 4). */
public final class SpiritSites {

	public static void init() {
	}

	/** Combined multiplier of every spiritual site the player is at, 1.0 for none, at most 4.0. */
	public static double multiplier(ServerPlayer player) {
		return 1.0D;
	}

	private SpiritSites() {
	}
}
