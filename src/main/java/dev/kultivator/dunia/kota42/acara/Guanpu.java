package dev.kultivator.dunia.kota42.acara;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * Dice house / guanpu (DESIGN 42.7.6): big-small dice and guanpu prize draws at the {@code meja_dadu}.
 * <p>
 * stub F42-2-kompil: 42D must finish Guanpu (step 6 of m2-42D); until then the dice table opens nothing.
 */
public final class Guanpu {

	/** Opens the dice sheet of {@code meja} for {@code player}. stub F42-2-kompil: does nothing. */
	static void buka(ServerPlayer player, MejaDaduEntity meja) {
	}

	/** {@code uji kota_hidup acara guanpu}. stub F42-2-kompil: no subcommands yet. */
	static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("guanpu");
	}

	private Guanpu() {
	}
}
