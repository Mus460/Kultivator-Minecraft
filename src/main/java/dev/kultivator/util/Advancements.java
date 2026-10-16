package dev.kultivator.util;

import dev.kultivator.Kultivator;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Grants the mod's advancements (the "Kultivasi" tab). Every advancement file under
 * {@code data/kultivator/advancement/} has one {@code minecraft:impossible} criterion named {@code "done"}.
 */
public final class Advancements {

	/** Grants {@code kultivator:<name>}; does nothing if it is unknown or already done. */
	public static void grant(ServerPlayer player, String name) {
		AdvancementHolder holder = player.level().getServer().getAdvancements().get(Kultivator.id(name));
		if (holder != null) {
			player.getAdvancements().award(holder, "done");
		}
	}

	private Advancements() {
	}
}
