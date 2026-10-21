package dev.kultivator.dunia.reruntuhan.menara;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * {@code /kultivasi uji menara ...} (DESIGN 34.5.7): {@code cari} (locate the pagoda and teleport), {@code masuk} (to
 * the lobby), {@code lantai <1-100>} (a free run from that floor: no Luka, no first-clear pay), {@code selesai},
 * {@code gagal}, {@code keluar}, {@code rekor <n>} (sets the best floor) and {@code info}.
 */
public final class MenaraTests {

	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("menara").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("cari").executes(ctx -> DuniaCommands.findAndTeleport(player(ctx), Kultivator.id("menara/menara_seratus"))))
				.then(Commands.literal("masuk").executes(ctx -> enter(player(ctx))))
				.then(Commands.literal("lantai").then(Commands.argument("n", IntegerArgumentType.integer(1, 100))
						.executes(ctx -> floor(player(ctx), IntegerArgumentType.getInteger(ctx, "n")))))
				.then(Commands.literal("selesai").executes(ctx -> clear(player(ctx))))
				.then(Commands.literal("gagal").executes(ctx -> fail(player(ctx))))
				.then(Commands.literal("keluar").executes(ctx -> leave(player(ctx))))
				.then(Commands.literal("rekor").then(Commands.argument("n", IntegerArgumentType.integer(0, 100))
						.executes(ctx -> record(player(ctx), IntegerArgumentType.getInteger(ctx, "n")))))
				.then(Commands.literal("info").executes(ctx -> info(player(ctx))));
	}

	private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	private static int enter(ServerPlayer player) {
		if (!Menara.isTowerLevel(player.level())) {
			Menara.saveReturn(player);
		}
		Menara.toLobby(player);
		return 1;
	}

	private static int floor(ServerPlayer player, int n) {
		TowerRun run = Menara.runOf(player);
		if (run != null) {
			run.testFloor(n);
			return 1;
		}
		if (!Menara.isTowerLevel(player.level())) {
			Menara.saveReturn(player);
		}
		TowerRun made = Menara.start(List.of(player), n, true);
		return made == null ? 0 : 1;
	}

	private static int clear(ServerPlayer player) {
		TowerRun run = Menara.runOf(player);
		if (run == null) {
			return none(player);
		}
		run.testClear();
		return 1;
	}

	private static int fail(ServerPlayer player) {
		TowerRun run = Menara.runOf(player);
		if (run == null) {
			return none(player);
		}
		run.testFail();
		return 1;
	}

	private static int leave(ServerPlayer player) {
		TowerRun run = Menara.runOf(player);
		if (run != null) {
			run.end(false, true);
		}
		Menara.sendHome(player);
		return 1;
	}

	private static int record(ServerPlayer player, int n) {
		Menara.setBest(player, n);
		TowerRecords.get(player.level().getServer()).set(player, n, player.level().getServer().overworld().getGameTime());
		Msg.chat(player, Component.translatable("kultivator.reruntuhan.uji.rekor", n).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int info(ServerPlayer player) {
		TowerRun run = Menara.runOf(player);
		Msg.chat(player, Component.literal("Menara: slots " + Menara.leased() + "/" + dev.kultivator.dunia.reruntuhan.Reruntuhan.config().towerSlots
				+ ", runs " + Menara.RUNS.size() + ", best " + Menara.best(player) + ", luka " + Menara.lukaLeft(player)
				+ (run == null ? "" : ", run " + run.describe())).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private static int none(ServerPlayer player) {
		Msg.chat(player, Component.translatable("kultivator.reruntuhan.uji.no_run").withStyle(ChatFormatting.GRAY));
		return 0;
	}

	private MenaraTests() {
	}
}
