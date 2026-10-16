package dev.kultivator.cultivation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** STUB (phase 0) — owned by P1-meditation. Qi gathering formations built from vanilla blocks (read-only detection). */
public final class QiFormations {

	public static void init() {
	}

	/** Tier 0 (none) to 3 of the formation around the block the player sits on ({@code seat} = the block below them). */
	public static int tier(ServerLevel level, BlockPos seat) {
		return 0;
	}

	/** XP multiplier of a tier. */
	public static double multiplier(int tier) {
		return switch (tier) {
			case 1 -> 1.25D;
			case 2 -> 1.6D;
			case 3 -> 2.2D;
			default -> 1.0D;
		};
	}

	private QiFormations() {
	}
}
