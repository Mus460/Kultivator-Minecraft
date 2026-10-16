package dev.kultivator.cultivation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** STUB (phase 0) — owned by P1-meditation. Spirit veins that thin out under meditation and recover (DESIGN section 4). */
public final class SpiritVeins {

	public static void init() {
	}

	/** Qi density 0..1 of the region around {@code pos}. */
	public static double density(ServerLevel level, BlockPos pos) {
		return 1.0D;
	}

	/** Draws {@code amount} density from the region around {@code pos}. */
	public static void drain(ServerLevel level, BlockPos pos, double amount) {
	}

	private SpiritVeins() {
	}
}
