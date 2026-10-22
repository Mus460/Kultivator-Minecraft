package dev.kultivator.sekte.seragam;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.sekte.SekteCommands;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.Lambang;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Commands of module S (DESIGN 36.2.8): {@code /kultivasi perguruan lambang} (everyone: opens the designer of his own
 * hall) and the op tests {@code /kultivasi uji perguruan seragam ...} on the executing player's own hall, free of charge:
 * {@code lambang <bentuk> <simbol> <w1> <w2> <w3>}, {@code acak}, {@code semboyan <teks>}, {@code seragam <jenjang>
 * <badan> <trim>} (-1 = the old look), {@code potongan <0-2> [jenjang]}, {@code bendera} (a test standard ahead for
 * 30 s), {@code atlas} (the client saves its emblem textures to {@code screenshots/}), {@code info}, {@code hapus}.
 */
final class SeragamCommands {

	static void init() {
		SekteCommands.player("lambang", () -> Commands.literal("lambang").executes(ctx -> {
			Seragam.openDesigner(ctx.getSource().getPlayerOrException());
			return 1;
		}));
		SekteCommands.test(SekteIds.S, SeragamCommands::testNode);
		SekteCommands.chips("perguruan seragam lambang <bentuk> <simbol> <w1> <w2> <w3>", "perguruan seragam acak", "perguruan seragam semboyan <teks>",
				"perguruan seragam seragam <jenjang> <badan> <trim>", "perguruan seragam potongan <0-2>", "perguruan seragam bendera",
				"perguruan seragam atlas", "perguruan seragam info", "perguruan seragam hapus");
	}

	private static LiteralArgumentBuilder<CommandSourceStack> testNode() {
		int last = Lambang.PALETTE.length - 1;
		return Commands.literal(SekteIds.S)
				.executes(ctx -> info(ctx))
				.then(Commands.literal("lambang")
						.then(Commands.argument("bentuk", IntegerArgumentType.integer(0, Lambang.SHAPES - 1))
								.then(Commands.argument("simbol", IntegerArgumentType.integer(0, Lambang.SYMBOLS - 1))
										.then(Commands.argument("w1", IntegerArgumentType.integer(0, last))
												.then(Commands.argument("w2", IntegerArgumentType.integer(0, last))
														.then(Commands.argument("w3", IntegerArgumentType.integer(0, last))
																.executes(ctx -> emblem(ctx, new Lambang(IntegerArgumentType.getInteger(ctx, "bentuk"),
																		IntegerArgumentType.getInteger(ctx, "simbol"), IntegerArgumentType.getInteger(ctx, "w1"),
																		IntegerArgumentType.getInteger(ctx, "w2"), IntegerArgumentType.getInteger(ctx, "w3"))))))))))
				.then(Commands.literal("acak").executes(ctx -> emblem(ctx, Lambang.seeded(ThreadLocalRandom.current().nextLong()))))
				.then(Commands.literal("semboyan").then(Commands.argument("teks", StringArgumentType.greedyString())
						.executes(ctx -> apply(ctx, LambangData.get(ctx.getSource().getPlayerOrException().getUUID())
								.withMotto(StringArgumentType.getString(ctx, "teks"))))))
				.then(Commands.literal("seragam")
						.then(Commands.argument("jenjang", IntegerArgumentType.integer(0, SeragamData.RANKS - 1))
								.then(Commands.argument("badan", IntegerArgumentType.integer(-1, last))
										.then(Commands.argument("trim", IntegerArgumentType.integer(-1, last))
												.executes(ctx -> apply(ctx, LambangData.get(ctx.getSource().getPlayerOrException().getUUID())
														.withRank(IntegerArgumentType.getInteger(ctx, "jenjang"), IntegerArgumentType.getInteger(ctx, "badan"),
																IntegerArgumentType.getInteger(ctx, "trim"))))))))
				.then(Commands.literal("potongan")
						.then(Commands.argument("potongan", IntegerArgumentType.integer(0, SeragamData.CUTS - 1))
								.executes(ctx -> apply(ctx, LambangData.get(ctx.getSource().getPlayerOrException().getUUID())
										.withCut(-1, IntegerArgumentType.getInteger(ctx, "potongan"))))
								.then(Commands.argument("jenjang", IntegerArgumentType.integer(0, SeragamData.RANKS - 1))
										.executes(ctx -> apply(ctx, LambangData.get(ctx.getSource().getPlayerOrException().getUUID())
												.withCut(IntegerArgumentType.getInteger(ctx, "jenjang"), IntegerArgumentType.getInteger(ctx, "potongan")))))))
				.then(Commands.literal("bendera").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Seragam.preview(player);
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.seragam.cmd.preview").withStyle(ChatFormatting.GOLD), false);
					return 1;
				}))
				.then(Commands.literal("atlas").executes(ctx -> {
					Seragam.debugAtlas(ctx.getSource().getPlayerOrException());
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.seragam.cmd.atlas").withStyle(ChatFormatting.GRAY), false);
					return 1;
				}))
				.then(Commands.literal("info").executes(SeragamCommands::info))
				.then(Commands.literal("hapus").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					SeragamData old = LambangData.get(player.getUUID());
					SeragamData reset = SeragamData.DEFAULT.withGeneration(old.generation()).withFlags(0);
					if (!LambangData.put(player.getUUID(), reset)) {
						return fail(ctx, Seragam.Result.NO_HALL);
					}
					Seragam.changed(ctx.getSource().getServer(), player.getUUID());
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.seragam.cmd.reset").withStyle(ChatFormatting.YELLOW), true);
					return 1;
				}));
	}

	private static int emblem(CommandContext<CommandSourceStack> ctx, Lambang emblem) throws CommandSyntaxException {
		return apply(ctx, LambangData.get(ctx.getSource().getPlayerOrException().getUUID()).withEmblem(emblem));
	}

	private static int apply(CommandContext<CommandSourceStack> ctx, SeragamData data) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Seragam.Result result = Seragam.save(ctx.getSource().getServer(), player, player.getUUID(), data, false);
		if (result != Seragam.Result.SAVED_FREE && result != Seragam.Result.SAVED_PAID && result != Seragam.Result.UNCHANGED) {
			return fail(ctx, result);
		}
		Lambang emblem = data.emblem();
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.seragam.cmd.set", emblem.shape(), emblem.symbol(), emblem.field(),
				emblem.charge(), emblem.border()).withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int fail(CommandContext<CommandSourceStack> ctx, Seragam.Result result) {
		ctx.getSource().sendFailure(Component.translatable(result == Seragam.Result.NO_HALL ? "kultivator.sekte.seragam.no_hall"
				: "kultivator.sekte.seragam.not_master"));
		return 0;
	}

	private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		for (Component line : Seragam.info(ctx.getSource().getServer(), player.getUUID())) {
			ctx.getSource().sendSuccess(() -> line, false);
		}
		return 1;
	}

	private SeragamCommands() {
	}
}
