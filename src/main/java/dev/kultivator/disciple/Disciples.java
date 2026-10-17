package dev.kultivator.disciple;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.network.Payloads;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** STUB — owned by P5-disciple-core. Disciples (Murid): recruitment, orders, screen actions (DESIGN 18). */
public final class Disciples {

	public static void init() {
	}

	/** Every DISCIPLE_* action (arg = the disciple's entity id). */
	public static void handleAction(ServerPlayer player, Payloads.Action action) {
	}

	/** Adds "murid ..." to /cultivate and the disciple debug commands. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
	}

	private Disciples() {
	}
}
