package dev.kultivator.client.dunia.tempat.model;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The client test command {@code /hiasan} of the 38MD visuals (DESIGN 38.O.5, client side; nothing is sent to the
 * server and nothing in the world changes):
 * <ul>
 * <li>{@code /hiasan festival}: every 38.O.3 decoration in front of the player for 20 s, festival on, fireworks and
 * sky lanterns at any hour;</li>
 * <li>{@code /hiasan senjata}: four weapon racks with all 28 city weapons for 30 s;</li>
 * <li>{@code /hiasan pamer}: every 38.O.2 creature and mechanism model on an arc of stands, animated, for 30 s
 * ({@link PamerModel});</li>
 * <li>{@code /hiasan prop [1|2]}: a wall of the hand props (page 1 = 1-40, page 2 = 41-88) turning over their names for
 * 30 s;</li>
 * <li>{@code /hiasan hapus}: ends every test;</li>
 * <li>{@code /hiasan info}: decorations, racks and showcase stands on this client, and last frame's quads.</li>
 * </ul>
 */
final class HiasPerintah {

	private static final String K = "kultivator.tempat_model.perintah.";

	static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal("hiasan")
				.then(ClientCommands.literal("festival").executes(HiasPerintah::festival))
				.then(ClientCommands.literal("senjata").executes(HiasPerintah::senjata))
				.then(ClientCommands.literal("pamer").executes(HiasPerintah::pamer))
				.then(ClientCommands.literal("prop").executes(context -> prop(context, 1))
						.then(ClientCommands.argument("halaman", IntegerArgumentType.integer(1, 2))
								.executes(context -> prop(context, IntegerArgumentType.getInteger(context, "halaman")))))
				.then(ClientCommands.literal("hapus").executes(context -> {
					HiasanDunia.hapusUji();
					RakSenjata.hapusUji();
					PamerModel.hapus();
					context.getSource().sendFeedback(Component.translatable(K + "hapus"));
					return 1;
				}))
				.then(ClientCommands.literal("info").executes(context -> {
					context.getSource().sendFeedback(Component.translatable(K + "info", HiasanDunia.jumlah(), RakSenjata.jumlah(),
							PamerModel.aktif(), HiasanDunia.quadsLastFrame()));
					return 1;
				})));
	}

	private static int festival(CommandContext<FabricClientCommandSource> context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null) {
			return 0;
		}
		HiasanDunia.uji(level, player.position(), player.getYRot());
		context.getSource().sendFeedback(Component.translatable(K + "festival"));
		return 1;
	}

	private static int senjata(CommandContext<FabricClientCommandSource> context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null) {
			return 0;
		}
		RakSenjata.uji(level, player.position(), player.getYRot());
		context.getSource().sendFeedback(Component.translatable(K + "senjata"));
		return 1;
	}

	private static int pamer(CommandContext<FabricClientCommandSource> context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null) {
			return 0;
		}
		PamerModel.pamer(level, player.position(), player.getYRot());
		context.getSource().sendFeedback(Component.translatable(K + "pamer"));
		return 1;
	}

	private static int prop(CommandContext<FabricClientCommandSource> context, int halaman) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null) {
			return 0;
		}
		int n = PamerModel.prop(level, player.position(), player.getYRot(), halaman);
		context.getSource().sendFeedback(Component.translatable(K + "prop", halaman, n));
		return 1;
	}

	private HiasPerintah() {
	}
}
