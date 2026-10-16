package dev.kultivator.technique;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.data.CultivationData;
import dev.kultivator.network.Payloads;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** STUB (phase 0) — owned by P2-techniques. Learning, selecting and casting techniques (DESIGN section 8). */
public final class Techniques {

	public static void init() {
	}

	public static void tick(ServerPlayer player, CultivationData data) {
	}

	/** CAST, SELECT_*, FLYING_SWORD_TOGGLE and CLOUD_STEP actions. */
	public static void handleAction(ServerPlayer player, Payloads.Action action) {
	}

	/** Learns every REALM/DAO technique the player now qualifies for (called after rank or Dao changes). */
	public static void refreshUnlocks(ServerPlayer player) {
	}

	/** Before the session reset on join: e.g. take away flight we granted in a previous session. */
	public static void onJoin(ServerPlayer player, CultivationData data) {
	}

	public static void onLeave(ServerPlayer player) {
	}

	/** Adds {@code technique select <id>} to /cultivate. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
	}

	private Techniques() {
	}
}
