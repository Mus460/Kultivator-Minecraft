package dev.kultivator.cultivation;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.data.CultivationData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** STUB (phase 0) — owned by P1-meditation. Meditation, spiritual sites, veins, enlightenment (DESIGN section 4). */
public final class Meditation {

	public static void init() {
	}

	public static void tick(ServerPlayer player, CultivationData data) {
	}

	public static void toggle(ServerPlayer player) {
	}

	/** True when meditation started; tells the player why not otherwise. */
	public static boolean start(ServerPlayer player) {
		return false;
	}

	public static void stop(ServerPlayer player) {
	}

	public static boolean isMeditating(ServerPlayer player) {
		return false;
	}

	public static void onLeave(ServerPlayer player) {
	}

	/** Adds {@code meditate} to /cultivate. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
	}

	private Meditation() {
	}
}
