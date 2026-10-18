package dev.kultivator.epic;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.data.Realm;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code debug momen info|bendera} and {@code uji momen ...} (DESIGN 23.15.1, 23.15.2). Both nodes are op-only (their
 * parents already require it); the sequences themselves live in {@link EpicTests}.
 */
public final class EpicCommands {

	private static final String PREFIX = EpicTests.PREFIX;

	private static final SuggestionProvider<CommandSourceStack> IDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(EpicTests.CATALOGUE.keySet(), builder);

	private static final SuggestionProvider<CommandSourceStack> ID_OPTIONS = (ctx, builder) -> {
		List<String> values = new ArrayList<>(EpicTests.argValues(StringArgumentType.getString(ctx, "id")));
		values.add("skala");
		values.add("beat");
		return SharedSuggestionProvider.suggest(values, builder);
	};

	private static final SuggestionProvider<CommandSourceStack> REALMS = (ctx, builder) -> {
		List<String> values = new ArrayList<>(EpicTests.REALM_ALIASES);
		for (Realm realm : Realm.values()) {
			values.add(realm.id());
		}
		return SharedSuggestionProvider.suggest(values, builder);
	};

	private static final SuggestionProvider<CommandSourceStack> FULL_OPTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(List.of("gagal", "1", "3", "5", "7", "9"), builder);

	private static final SuggestionProvider<CommandSourceStack> SHOW_OPTIONS = (ctx, builder) -> {
		List<String> values = new ArrayList<>(EpicTests.GROUPS);
		values.add("jeda");
		return SharedSuggestionProvider.suggest(values, builder);
	};

	/** Adds {@code debug momen info|bendera} (op). */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		debug.then(Commands.literal("momen")
				.then(Commands.literal("info").executes(EpicCommands::info))
				.then(Commands.literal("bendera")
						.executes(ctx -> flags(ctx, ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> flags(ctx, EntityArgument.getPlayer(ctx, "player"))))));
	}

	/** Adds {@code uji momen ...} to the op-only {@code uji} node. */
	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		uji.then(Commands.literal("momen")
				.executes(ctx -> EpicTests.list(ctx.getSource().getPlayerOrException()))
				.then(Commands.literal("daftar").executes(ctx -> EpicTests.list(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("berhenti").executes(ctx -> EpicTests.stop(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("semua")
						.executes(ctx -> EpicTests.showcase(ctx.getSource().getPlayerOrException(), ""))
						.then(Commands.argument("opsi", StringArgumentType.greedyString()).suggests(SHOW_OPTIONS)
								.executes(ctx -> EpicTests.showcase(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "opsi")))))
				.then(Commands.literal("tribulasi_penuh")
						.then(Commands.argument("ranah", StringArgumentType.word()).suggests(REALMS)
								.executes(ctx -> EpicTests.fullTribulation(ctx.getSource().getPlayerOrException(),
										StringArgumentType.getString(ctx, "ranah"), ""))
								.then(Commands.argument("opsi", StringArgumentType.greedyString()).suggests(FULL_OPTIONS)
										.executes(ctx -> EpicTests.fullTribulation(ctx.getSource().getPlayerOrException(),
												StringArgumentType.getString(ctx, "ranah"), StringArgumentType.getString(ctx, "opsi"))))))
				.then(Commands.argument("id", StringArgumentType.word()).suggests(IDS)
						.executes(ctx -> EpicTests.play(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id"), ""))
						.then(Commands.argument("opsi", StringArgumentType.greedyString()).suggests(ID_OPTIONS)
								.executes(ctx -> EpicTests.play(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id"),
										StringArgumentType.getString(ctx, "opsi"))))));
	}

	/** The last 16 cues: game time, moment, beat, S, flags by name, receivers and the viewer. */
	private static int info(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		List<EpicMoments.LogEntry> log = EpicMoments.log();
		if (log.isEmpty()) {
			source.sendSuccess(() -> Component.translatable(PREFIX + "debug.empty").withStyle(ChatFormatting.GRAY), false);
			return 0;
		}
		source.sendSuccess(() -> Component.translatable(PREFIX + "debug.header", log.size()).withStyle(ChatFormatting.GOLD), false);
		for (EpicMoments.LogEntry entry : log) {
			Component line = Component.translatable(PREFIX + "debug.line", entry.time(), EpicIds.momentName(entry.moment()), entry.beat(), entry.s(),
					EpicIds.flagNames(entry.flags()), entry.receivers(), entry.viewer()).withStyle(ChatFormatting.GRAY);
			source.sendSuccess(() -> line, false);
		}
		return log.size();
	}

	/** SAFE, RESULT_SAFE, IN_COMBAT, LETHAL_NEXT and PROTECTORS right now, with the reasons for a false SAFE. */
	private static int flags(CommandContext<CommandSourceStack> ctx, ServerPlayer player) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		List<String> reasons = new ArrayList<>();
		boolean safe = EpicMoments.safe(player, reasons);
		int flags = EpicMoments.flags(player);
		source.sendSuccess(() -> Component.translatable(PREFIX + "debug.flags.header", player.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
		line(source, "SAFE", safe);
		for (String reason : reasons) {
			Component why = Component.translatable(PREFIX + "debug.flags.reason", Component.translatable(PREFIX + "debug.reason." + reason))
					.withStyle(ChatFormatting.RED);
			source.sendSuccess(() -> why, false);
		}
		line(source, "RESULT_SAFE", (flags & EpicIds.FLAG_RESULT_SAFE) != 0);
		line(source, "IN_COMBAT", (flags & EpicIds.FLAG_IN_COMBAT) != 0);
		line(source, "LETHAL_NEXT", (flags & EpicIds.FLAG_LETHAL_NEXT) != 0);
		line(source, "PROTECTORS", (flags & EpicIds.FLAG_PROTECTORS) != 0);
		line(source, "LOW_LOAD", (flags & EpicIds.FLAG_LOW_LOAD) != 0);
		line(source, "CAMERA_OK", (flags & EpicIds.FLAG_CAMERA_OK) != 0);
		return safe ? 1 : 0;
	}

	private static void line(CommandSourceStack source, String name, boolean value) {
		MutableComponent answer = Component.translatable(PREFIX + (value ? "yes" : "no")).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
		Component line = Component.translatable(PREFIX + "debug.flags.line", name, answer).withStyle(ChatFormatting.GRAY);
		source.sendSuccess(() -> line, false);
	}

	private EpicCommands() {
	}
}
