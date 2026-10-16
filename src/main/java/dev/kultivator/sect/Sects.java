package dev.kultivator.sect;

import net.minecraft.server.level.ServerPlayer;

/** STUB — owned by P3-sects. Sects, disciples and sparring; /sect and /sekte (DESIGN sections 10 and 16). */
public final class Sects {

	public static void init() {
	}

	/** XP multiplier for meditation near a sect mate (1.15) or 1.0. */
	public static double meditationBonus(ServerPlayer player) {
		return 1.0D;
	}

	private Sects() {
	}
}
