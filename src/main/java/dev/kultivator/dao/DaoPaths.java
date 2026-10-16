package dev.kultivator.dao;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.data.Dao;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** STUB (phase 0) — owned by P2-dao. Choosing a Dao and Dao comprehension (DESIGN section 7). */
public final class DaoPaths {

	public static void init() {
	}

	/** Chooses the player's Dao if they may (Golden Core+, none chosen yet). True on success. */
	public static boolean choose(ServerPlayer player, Dao dao) {
		return false;
	}

	/** Adds {@code dao choose <dao>} to /cultivate. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
	}

	private DaoPaths() {
	}
}
