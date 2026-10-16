package dev.kultivator.cultivation;

import dev.kultivator.data.CultivationData;
import net.minecraft.server.level.ServerPlayer;

/** STUB (phase 0) — owned by P1-breakthrough. Heavenly tribulation (omen, strikes, variants) (DESIGN section 3). */
public final class Tribulation {

	public static void init() {
	}

	public static void tick(ServerPlayer player, CultivationData data) {
	}

	/** True while a tribulation runs for the player. */
	public static boolean isActive(ServerPlayer player) {
		return false;
	}

	private Tribulation() {
	}
}
