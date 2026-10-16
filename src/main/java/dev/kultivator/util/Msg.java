package dev.kultivator.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Player-facing text helpers. Every visible string is a translation key (lang-src/*.json). */
public final class Msg {

	/** "[Kultivasi] message" in chat. */
	public static MutableComponent prefixed(Component message) {
		return Component.translatable("kultivator.core.prefix").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(" "))
				.append(message.copy().withStyle(style -> style.getColor() == null ? style.withColor(ChatFormatting.WHITE) : style));
	}

	public static void chat(ServerPlayer player, Component message) {
		player.sendSystemMessage(prefixed(message));
	}

	/** Action bar line above the hotbar. */
	public static void actionBar(ServerPlayer player, Component message) {
		player.sendOverlayMessage(message);
	}

	/** Big title in the middle of the screen. */
	public static void title(ServerPlayer player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	/** A chat line for everyone on the server. */
	public static void broadcast(MinecraftServer server, Component message) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.sendSystemMessage(prefixed(message));
		}
	}

	/** Whole number with thousands separators kept simple (no locale surprises). */
	public static String number(double value) {
		long rounded = Math.round(Math.floor(value));
		return String.valueOf(rounded);
	}

	private Msg() {
	}
}
