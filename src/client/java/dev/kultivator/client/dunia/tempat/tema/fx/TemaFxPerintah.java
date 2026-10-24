package dev.kultivator.client.dunia.tempat.tema.fx;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.kultivator.client.epic.EpicBudget;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The client preview command {@code /temafx} of the themed-town effects (DESIGN 38.T.9.4; nothing is sent to the server
 * and nothing in the world changes), for looking at every effect without waiting for its festival or weather:
 * {@code badai} (omen wall for 10 s, then a 60 s storm), {@code lentera} (a 60-block test canal ahead with the festival
 * on), {@code cahaya} (the Buddha light with a cloud sea under the player), {@code awan}, {@code kabut}, {@code suar}
 * (a lamp scene 6 blocks ahead), {@code riak} (a blood pool at the feet), {@code ombak} (the storm surface), {@code tali}
 * (a rope pair), {@code mural} (the twelve murals in a row, mural 7 moving), {@code perahu} (a racing crew without hull),
 * {@code festival <acara>} (the banner), {@code semua}, {@code info} and {@code mati} (ends every preview and effect).
 */
final class TemaFxPerintah {

	private static final String K = "kultivator.tempat_tema_fx.perintah.";
	private static final long UJI = -5L;

	private TemaFxPerintah() {
	}

	static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> register(dispatcher));
	}

	private interface Uji {
		void jalan(LocalPlayer player, long now);
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> sub(String name, Uji uji) {
		return ClientCommands.literal(name).executes(context -> jalan(context, name, uji));
	}

	private static int jalan(CommandContext<FabricClientCommandSource> context, String name, Uji uji) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return 0;
		}
		uji.jalan(player, minecraft.level.getGameTime());
		context.getSource().sendFeedback(Component.translatable(K + name));
		return 1;
	}

	private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal("temafx")
				.then(sub("badai", TemaFxPerintah::badai))
				.then(sub("lentera", TemaFxPerintah::lentera))
				.then(sub("cahaya", TemaFxPerintah::cahaya))
				.then(sub("awan", (p, now) -> LautAwanFx.uji(p.getX(), p.getY() - 6.0D, p.getZ(), now, 1200)))
				.then(sub("kabut", (p, now) -> KabutGunungFx.uji(p.getX(), p.getY(), p.getZ(), now, 1200)))
				.then(sub("suar", TemaFxPerintah::suar))
				.then(sub("riak", TemaFxPerintah::riak))
				.then(sub("ombak", (p, now) -> OmbakFx.uji(now, 600)))
				.then(sub("tali", (p, now) -> TaliGantungRenderer.uji(p.getX(), p.getY() + 3.0D, p.getZ(), p.getYRot())))
				.then(sub("mural", (p, now) -> MuralBillboard.uji(p.getX(), p.getY(), p.getZ(), p.getYRot(), now)))
				.then(sub("perahu", TemaFxPerintah::perahu))
				.then(sub("semua", TemaFxPerintah::semua))
				.then(ClientCommands.literal("festival").then(ClientCommands.argument("acara", StringArgumentType.word()).executes(context -> {
					String acara = StringArgumentType.getString(context, "acara");
					TemaHud.spanduk(acara);
					context.getSource().sendFeedback(Component.translatable(K + "festival", acara));
					return 1;
				})))
				.then(ClientCommands.literal("info").executes(context -> {
					context.getSource().sendFeedback(Component.translatable(K + "info", TemaFx.quadsLastFrame(), LenteraAirFx.jumlah(),
							TemaFestival.jumlah(), EpicBudget.tier()));
					return 1;
				}))
				.then(ClientCommands.literal("mati").executes(context -> {
					TemaFx.bersihkan();
					context.getSource().sendFeedback(Component.translatable(K + "mati"));
					return 1;
				})));
	}

	private static Vec3 depan(LocalPlayer p, double jarak) {
		float a = p.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(p.getX() - Mth.sin(a) * jarak, p.getY(), p.getZ() + Mth.cos(a) * jarak);
	}

	private static void badai(LocalPlayer p, long now) {
		// centred on the player: the front rolls in from the storm's own (hashed) side for 10 s, then the storm
		BadaiPasirFx.uji(p.getX(), p.getZ(), now, 200);
	}

	private static void lentera(LocalPlayer p, long now) {
		List<Vec3> nodes = new ArrayList<>();
		float a = p.getYRot() * Mth.DEG_TO_RAD;
		for (int i = 0; i <= 6; i++) {
			double d = 2.0D + i * 10.0D;
			double wiggle = Math.sin(i * 1.3D) * 3.0D;
			nodes.add(new Vec3(p.getX() - Mth.sin(a) * d + Mth.cos(a) * wiggle, p.getY() - 1.0D, p.getZ() + Mth.cos(a) * d + Mth.sin(a) * wiggle));
		}
		TemaJalur.pasangUji(UJI, nodes, "lentera");
		LenteraAirFx.mulai(UJI, now + 1600L);
		Vec3 at = depan(p, 3.0D);
		LenteraAirFx.lepas(at.x, p.getY() - 1.0D, at.z, true);
		TemaHud.harapan(Component.translatable(K + "harapan_uji").getString());
	}

	private static void cahaya(LocalPlayer p, long now) {
		CahayaBudhaFx.uji(p.getX(), p.getY(), p.getZ(), now, 900);
		LautAwanFx.uji(p.getX(), p.getY() - 8.0D, p.getZ(), now, 900);
	}

	private static void suar(LocalPlayer p, long now) {
		Vec3 at = depan(p, 6.0D);
		SinarSuarFx.uji(at.x, p.getY() + 3.0D, at.z, now, 600);
	}

	private static void riak(LocalPlayer p, long now) {
		Vec3 at = depan(p, 3.0D);
		RiakDarahFx.uji(at.x, p.getY() + 0.02D, at.z);
	}

	private static void perahu(LocalPlayer p, long now) {
		Vec3 at = depan(p, 7.0D);
		PendayungImpostor.uji(at.x, p.getY(), at.z, p.getYRot() + 90.0F, now, 600);
	}

	private static void semua(LocalPlayer p, long now) {
		cahaya(p, now);
		suar(p, now);
		riak(p, now);
		lentera(p, now);
		perahu(p, now);
		MuralBillboard.uji(p.getX(), p.getY(), p.getZ(), p.getYRot(), now);
		TaliGantungRenderer.uji(p.getX(), p.getY() + 3.0D, p.getZ(), p.getYRot());
		TemaHud.spanduk("lentera_air");
	}
}
