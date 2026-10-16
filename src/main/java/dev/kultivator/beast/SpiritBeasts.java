package dev.kultivator.beast;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.data.CultivationData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** STUB — owned by P3-beasts. Spirit beasts: awakened wolves and foxes that evolve (DESIGN sections 10 and 16). */
public final class SpiritBeasts {

	public static void init() {
	}

	public static void tick(ServerPlayer player, CultivationData data) {
	}

	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
	}

	private SpiritBeasts() {
	}
}
