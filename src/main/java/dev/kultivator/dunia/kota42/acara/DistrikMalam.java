package dev.kultivator.dunia.kota42.acara;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * The night district (DESIGN 42.7.6): lanterns, night stalls, gambling tables.
 * <p>
 * stub F42-2-kompil: 42D must finish DistrikMalam (step 6 of m2-42D).
 */
public final class DistrikMalam {

	static void init() {
	}

	/** {@code uji kota_hidup acara malam}. stub F42-2-kompil: no subcommands yet. */
	static LiteralArgumentBuilder<CommandSourceStack> ujiMalam() {
		return Commands.literal("malam");
	}

	private DistrikMalam() {
	}
}
