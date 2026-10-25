package dev.kultivator.dunia.kota42.acara;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Travelling troupes (DESIGN 42.7.2): six troupes, the stage entity, shows, the Terhibur buff.
 * <p>
 * stub F42-2-kompil: 42D must finish Rombongan (step 5 of m2-42D); until then no troupe is ever in town.
 */
public final class Rombongan {

	static void init() {
	}

	/** The troupe staying in place {@code situs} now, or null. stub F42-2-kompil: always null. */
	static @Nullable String diKota(MinecraftServer server, long situs) {
		return null;
	}

	/** True while a troupe performs in place {@code situs}. stub F42-2-kompil: always false. */
	static boolean sedangPentas(long situs) {
		return false;
	}

	/** {@code uji kota_hidup acara rombongan}. stub F42-2-kompil: no subcommands yet. */
	static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("rombongan");
	}

	private Rombongan() {
	}
}
