package dev.kultivator.cultivation;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.data.CultivationData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * STUB (phase 0) — owned by P1-breakthrough. Breakthroughs: awakening, chance roll, heavenly tribulation, quality,
 * Qi Deviation, Hardcore (DESIGN section 3).
 */
public final class Breakthrough {

	public static void init() {
	}

	public static void tick(ServerPlayer player, CultivationData data) {
	}

	/** Tries to start a breakthrough; tells the player why not when it cannot. True when one started. */
	public static boolean attempt(ServerPlayer player) {
		return false;
	}

	/** True while a breakthrough of the player is running. */
	public static boolean isBusy(ServerPlayer player) {
		return false;
	}

	public static void onLeave(ServerPlayer player) {
	}

	/** After death: applies a failure left over from a tribulation the player died in. */
	public static void onRespawn(ServerPlayer player, CultivationData data) {
	}

	/** Adds {@code breakthrough} to /cultivate and {@code breakthrough force}, {@code tribulation test} to debug. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
	}

	private Breakthrough() {
	}
}
