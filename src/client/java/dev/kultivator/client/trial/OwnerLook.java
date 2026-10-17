package dev.kultivator.client.trial;

import dev.kultivator.trial.HeartDemonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** How a heart demon's owner looks on this client: skin from the player list (default skin when unknown) and name. */
final class OwnerLook {

	static PlayerSkin skin(@Nullable UUID owner) {
		if (owner == null) {
			return DefaultPlayerSkin.getDefaultSkin();
		}
		PlayerInfo info = info(owner);
		return info != null ? info.getSkin() : DefaultPlayerSkin.get(owner);
	}

	static String name(HeartDemonEntity demon) {
		UUID owner = demon.getOwnerUUID();
		if (owner == null) {
			return "???";
		}
		PlayerInfo info = info(owner);
		if (info != null) {
			return info.getProfile().name();
		}
		Player player = demon.level().getPlayerByUUID(owner);
		return player != null ? player.getName().getString() : "???";
	}

	private static @Nullable PlayerInfo info(UUID owner) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		return connection == null ? null : connection.getPlayerInfo(owner);
	}

	private OwnerLook() {
	}
}
