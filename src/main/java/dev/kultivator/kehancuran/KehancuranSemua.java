package dev.kultivator.kehancuran;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.pusakalangit.TitahSettings;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.run.TechniqueTests;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * ONE command for Mode Kehancuran: {@code /kultivasi kehancuran semua nyala|mati} (short: {@code /kultivasi hancur
 * nyala|mati}); {@code /kultivasi kehancuran} alone prints the same summary with [Nyalakan semua] / [Matikan semua].
 * <p>
 * {@code nyala} switches on, in one go: the server permission {@code modeKehancuran} when it is off and the player may
 * grant it (an operator or the singleplayer / LAN owner; remembered as {@link Kehancuran#ALL_PERMIT_KEY}), the player's
 * own switch, Hancur Total, Kawah Lebur, the test-cast carve switch ({@code uji teknik kehancuran}) and "Hancurkan tanah"
 * of all four Titah decrees (the Titah menu and the seal tooltip are refreshed). {@code mati} switches all of those off;
 * the server permission goes back off only when this player's own {@code semua nyala} turned it on, the player may still
 * grant it and no other online player has their own switch on (otherwise it stays as it is and the summary says why).
 * The Sumpah Kehancuran of the disciples' Destroyer Arts is never bypassed: the summary links to the oath screen.
 */
public final class KehancuranSemua {

	private static final String KEY = "kultivator.kehancuran_semua.";
	private static final int RED = 0xFF4A3A;
	private static final int MUTED = 0xA8B5B0;
	private static final int JADE = 0x9FF3E6;

	/** An operator, or the owner of the integrated server (singleplayer, the LAN host). */
	static boolean mayGrant(ServerPlayer player) {
		return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
				|| player.level().getServer().isSingleplayerOwner(player.nameAndId());
	}

	/** {@code kehancuran semua nyala|mati}. */
	public static int set(ServerPlayer player, boolean on) {
		MinecraftServer server = player.level().getServer();
		KultivatorConfig config = KultivatorConfig.get();
		List<Component> notes = new ArrayList<>();
		if (on) {
			if (!config.modeKehancuran && mayGrant(player)) {
				if (!Kehancuran.setPermissionQuiet(server, true)) {
					notes.add(Component.translatable("kultivator.kehancuran.permit_unsaved").withColor(RED));
				}
				Kehancuran.setAllPermit(player, true);
				notes.add(Component.translatable(KEY + "note.permit_on").withColor(RED));
			}
			Kehancuran.quietSwitches(player, true);
		} else {
			Kehancuran.quietSwitches(player, false);
			if (!config.modeKehancuran) {
				Kehancuran.setAllPermit(player, false);
			} else if (Kehancuran.allPermit(player) && mayGrant(player)) {
				List<String> others = new ArrayList<>();
				for (ServerPlayer other : server.getPlayerList().getPlayers()) {
					if (other != player && Kehancuran.selfStored(other) && !server.isSingleplayerOwner(other.nameAndId())) {
						others.add(other.getPlainTextName());
					}
				}
				if (others.isEmpty()) {
					if (!Kehancuran.setPermissionQuiet(server, false)) {
						notes.add(Component.translatable("kultivator.kehancuran.permit_unsaved").withColor(RED));
					}
					Kehancuran.setAllPermit(player, false);
					notes.add(Component.translatable(KEY + "note.permit_off").withColor(JADE));
				} else {
					notes.add(Component.translatable(KEY + "note.permit_kept", String.join(", ", others)).withColor(MUTED));
				}
			}
		}
		TechniqueTests.setKehancuranTests(player, on);
		TitahSettings.setAllHancur(player, on);
		Msg.chat(player, Component.translatable(on ? KEY + "done_on" : KEY + "done_off").withStyle(on ? ChatFormatting.RED : ChatFormatting.AQUA)
				.withStyle(ChatFormatting.BOLD));
		for (Component note : notes) {
			player.sendSystemMessage(note);
		}
		lines(player);
		return 1;
	}

	/** {@code /kultivasi kehancuran} and {@code kehancuran semua}: the summary only. */
	public static int summary(ServerPlayer player) {
		Msg.chat(player, Component.translatable(KEY + "title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		lines(player);
		return 1;
	}

	/** Every switch with ✔/✖, what can still stop a carve, and the buttons. */
	private static void lines(ServerPlayer player) {
		KultivatorConfig config = KultivatorConfig.get();
		CultivationData data = Cultivators.get(player);
		boolean grant = mayGrant(player);

		Component permission = null;
		if (config.modeKehancuran) {
			permission = Kehancuran.allPermit(player) ? Component.translatable(KEY + "perm.by_you") : null;
		} else {
			permission = Component.translatable(grant ? KEY + "perm.can" : KEY + "perm.op");
		}
		line(player, config.modeKehancuran, "perm", permission);
		if (!config.kehancuranPlayerTechniques) {
			line(player, false, "config", Component.translatable(KEY + "config.detail"));
		}
		Component why = Kehancuran.selfAllowed(player);
		line(player, Kehancuran.selfOn(player), "self", why);
		line(player, Kehancuran.totalOn(player), "total",
				config.kehancuranTotalAllowed ? null : Component.translatable(KEY + "server_off"));
		line(player, Kehancuran.kawahOn(player), "kawah",
				config.kehancuranSurfacePass ? null : Component.translatable(KEY + "server_off"));
		line(player, TechniqueTests.kehancuranTests(player), "tests", Component.translatable(KEY + "tests.detail"));
		int decrees = TitahSettings.hancurCount(player);
		line(player, decrees == 4, "titah", config.titahEnabled ? Component.translatable(KEY + "titah.detail", decrees)
				: Component.translatable(KEY + "titah.off"));
		boolean realm = data.realm().atLeast(Realm.GOLDEN_CORE) && TechniquePower.kScale(data) > 0.0D;
		line(player, realm, "realm", Component.translatable(KEY + "realm.detail"));

		MutableComponent oath = Component.translatable(KEY + "oath.button").withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)
				.withClickEvent(new ClickEvent.RunCommand("/kultivasi kehancuran sumpah"))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable(KEY + "oath.hover"))));
		line(player, Kehancuran.playerSworn(player), "oath", Component.translatable(KEY + "oath.detail").append(Component.literal(" ")).append(oath));

		player.sendSystemMessage(Component.translatable(KEY + "still", (int) ProtectedZones.VILLAGER_RADIUS, config.kehancuranPlayerSafeRadius,
				ProtectedZones.MIN_SPAWN_RADIUS, config.kehancuranHomeSafeRadius, config.kehancuranHallSafeRadius,
				(int) ProtectedZones.FORMATION_RADIUS, config.kehancuranCasterSafeRadius).withColor(MUTED));

		MutableComponent buttons = Component.empty();
		buttons.append(button(KEY + "button.on", KEY + "button.on_hover", "/kultivasi kehancuran semua nyala", RED));
		buttons.append(Component.literal(" "));
		buttons.append(button(KEY + "button.off", KEY + "button.off_hover", "/kultivasi kehancuran semua mati", 0x55FFFF));
		buttons.append(Component.literal(" "));
		buttons.append(button(KEY + "button.status", KEY + "button.status_hover", "/kultivasi kehancuran status", 0xFFB0A0));
		buttons.append(Component.literal(" "));
		buttons.append(button(KEY + "button.restore", KEY + "button.restore_hover", "/kultivasi kehancuran pulihkan", JADE));
		player.sendSystemMessage(buttons);
	}

	private static void line(ServerPlayer player, boolean ok, String id, @Nullable Component detail) {
		MutableComponent line = Component.literal(ok ? "✔ " : "✖ ").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)
				.append(Component.translatable(KEY + id).withStyle(ChatFormatting.WHITE));
		if (detail != null) {
			line.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY)).append(detail.copy().withColor(MUTED));
		}
		player.sendSystemMessage(line);
	}

	private static Component button(String key, String hover, String command, int color) {
		return Component.translatable(key).withStyle(style -> style.withColor(color).withBold(true)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable(hover).append(Component.literal("\n" + command)))));
	}

	private KehancuranSemua() {
	}
}
