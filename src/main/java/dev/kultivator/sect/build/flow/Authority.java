package dev.kultivator.sect.build.flow;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * Who may authorize the site permissions "Bongkar &amp; simpan" (player-made blocks) and "Kosongkan" (containers) of DESIGN
 * 21.10 / 21.19: only in singleplayer without LAN, or an op. Otherwise a footprint could be used to grief a neighbour's
 * house. The mod is not a claims mod and adds no protection beyond this.
 */
public final class Authority {

	public static boolean mayAuthorize(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server.isSingleplayer() && !server.isPublished()) {
			return true;
		}
		return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	private Authority() {
	}
}
