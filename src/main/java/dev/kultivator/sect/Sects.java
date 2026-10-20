package dev.kultivator.sect;

import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sects, disciples and sparring (DESIGN sections 10 and 16): {@code /sect} and {@code /sekte} ({@link SectCommands}),
 * the saved sects ({@link SectData}), friendly duels ({@link Sparring}) and the meditation bonus near a sect mate.
 */
public final class Sects {

	/** Parchment gold used for sect messages. */
	public static final int ACCENT = 0xE8D7A8;
	/** Meditation XP multiplier beside a sect mate. */
	public static final double MEDITATION_BONUS = 1.15D;
	public static final double MEDITATION_RANGE = 16.0D;
	/** A sect this big earns its members the "sect_five" advancement. */
	public static final int FLOURISHING_SIZE = 5;

	/** Server tick of the last meditation bonus check per player (a gap means a new meditation session). */
	private static final Map<UUID, Long> BONUS_CHECKED = new HashMap<>();
	/** Players already told about the bonus in their current meditation session. */
	private static final Set<UUID> BONUS_TOLD = new HashSet<>();

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> SectCommands.register(dispatcher));
		Sparring.init();
		ServerPlayerEvents.JOIN.register(Sects::onJoin);
		ServerPlayerEvents.LEAVE.register(player -> {
			BONUS_CHECKED.remove(player.getUUID());
			BONUS_TOLD.remove(player.getUUID());
			SectCommands.forget(player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BONUS_CHECKED.clear();
			BONUS_TOLD.clear();
			SectCommands.clear();
		});
	}

	/** {@code /kultivasi uji sekte addmember <name>} (op): the same stand-in member as {@code /sekte debug addmember}. */
	public static int testAddMember(net.minecraft.commands.CommandSourceStack source, String name)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return SectCommands.debugAddMember(source, name);
	}

	/** XP multiplier for meditation near a sect mate (1.15) or 1.0. */
	public static double meditationBonus(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Sect sect = SectData.get(server).sectOf(player.getUUID());
		ServerPlayer mate = sect == null ? null : nearbyMate(server, sect, player);
		long now = server.getTickCount();
		Long last = BONUS_CHECKED.put(player.getUUID(), now);
		if (last == null || now - last > 60L) {
			BONUS_TOLD.remove(player.getUUID());
		}
		if (mate == null) {
			return 1.0D;
		}
		if (BONUS_TOLD.add(player.getUUID())) {
			Msg.chat(player, Component.translatable("kultivator.sects.meditation_bonus", mate.getDisplayName()).withColor(ACCENT));
		}
		return MEDITATION_BONUS;
	}

	/** An online member of {@code sect} other than {@code player} within 16 blocks in the same dimension, or null. */
	private static @Nullable ServerPlayer nearbyMate(MinecraftServer server, Sect sect, ServerPlayer player) {
		double rangeSq = MEDITATION_RANGE * MEDITATION_RANGE;
		for (Sect.Member member : sect.members()) {
			if (member.id().equals(player.getUUID())) {
				continue;
			}
			ServerPlayer other = online(server, member.id());
			if (other != null && other.level() == player.level() && !other.isSpectator() && other.distanceToSqr(player) <= rangeSq) {
				return other;
			}
		}
		return null;
	}

	// ================================================================== public helpers

	/** The player's sect, or null. */
	public static @Nullable Sect sectOf(ServerPlayer player) {
		return SectData.get(player.level().getServer()).sectOf(player.getUUID());
	}

	/** True when both players belong to the same sect. */
	public static boolean areSectMates(ServerPlayer a, ServerPlayer b) {
		if (a == b) {
			return false;
		}
		Sect sect = sectOf(a);
		return sect != null && sect.isMember(b.getUUID());
	}

	/** Members of {@code sect} who are online right now. */
	public static List<ServerPlayer> onlineMembers(MinecraftServer server, Sect sect) {
		List<ServerPlayer> list = new ArrayList<>();
		for (Sect.Member member : sect.members()) {
			ServerPlayer player = online(server, member.id());
			if (player != null) {
				list.add(player);
			}
		}
		return list;
	}

	/** "[Sect] message" to the sect's online members except {@code except}. */
	public static void tell(MinecraftServer server, Sect sect, Component message, Set<UUID> except) {
		MutableComponent line = Component.empty().append(sect.tag()).append(Component.literal(" "))
				.append(message.copy().withStyle(style -> style.getColor() == null ? style.withColor(ACCENT) : style));
		for (ServerPlayer player : onlineMembers(server, sect)) {
			if (!except.contains(player.getUUID())) {
				player.sendSystemMessage(line);
			}
		}
	}

	/** Grants "sect_five" to the online members once the sect has 5 members or more. */
	static void checkFlourishing(MinecraftServer server, Sect sect) {
		if (sect.size() < FLOURISHING_SIZE) {
			return;
		}
		for (ServerPlayer player : onlineMembers(server, sect)) {
			Advancements.grant(player, "sect_five");
		}
	}

	/** A bold clickable chat button "[Text]" that runs {@code command}. */
	static MutableComponent button(String key, String command, Component hover, int color) {
		return Component.translatable(key).withStyle(style -> style.withColor(color).withBold(true)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	static @Nullable ServerPlayer online(MinecraftServer server, UUID id) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		return player == null || player.hasDisconnected() ? null : player;
	}

	private static void onJoin(ServerPlayer player) {
		SectData data = SectData.get(player.level().getServer());
		data.rename(player.getUUID(), player.getPlainTextName());
		Sect sect = data.sectOf(player.getUUID());
		if (sect != null && sect.size() >= FLOURISHING_SIZE) {
			Advancements.grant(player, "sect_five");
		}
	}

	private Sects() {
	}
}
