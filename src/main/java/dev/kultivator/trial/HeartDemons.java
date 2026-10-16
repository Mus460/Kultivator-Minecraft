package dev.kultivator.trial;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.data.CultivationData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** STUB — owned by P3-heart-demon. Heart demon trials (DESIGN sections 10 and 16). */
public final class HeartDemons {

	public static void init() {
	}

	public static void tick(ServerPlayer player, CultivationData data) {
	}

	/** Adds the heart demon debug commands. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
	}

	private HeartDemons() {
	}
}
