package dev.kultivator.dunia.kota42.acara;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shadow play (DESIGN 42.7.6): shows on the {@code layar_bayang} screen, the gong rhythm game.
 * <p>
 * stub F42-2-kompil: 42D must finish TeaterBayang (step 6 of m2-42D); until then no show runs.
 */
public final class TeaterBayang {

	/** True while a shadow play runs in place {@code situs}. stub F42-2-kompil: always false. */
	static boolean sedangPentas(long situs) {
		return false;
	}

	/** A gong beat of the J_WAYANG rhythm HUD (routed by {@link Ronda}). stub F42-2-kompil: ignored. */
	static void irama(ServerPlayer player, byte[] data) {
	}

	/** {@code uji kota_hidup acara wayang}. stub F42-2-kompil: no subcommands yet. */
	static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("wayang");
	}

	private TeaterBayang() {
	}
}
