package dev.kultivator.client.epic.film;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.cosmic.CosmicSky;
import dev.kultivator.client.epic.lens.HeatHaze;
import dev.kultivator.client.epic.lens.LensLayers;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * The film commands of {@code /sinematik} (DESIGN 30.6, client, no op): {@code spektakuler}, {@code film daftar|info},
 * {@code film <id> [act <n>]}, {@code kosmik [detik]}, {@code lensa <layer> [detik]} and {@code waktu_beku}. Every film
 * still passes every gate of 30.2.2; only the cooldowns of a test scene are skipped.
 */
public final class FilmCommands {

	private static final String K = "kultivator.film.cmd.";
	private static final String[] LAYERS = {"grain", "flare", "leak", "smear", "fringe", "haze"};
	private static int warningIn;

	/** Adds the film literals to the {@code /sinematik} root. */
	public static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
		root.then(ClientCommands.literal("spektakuler").executes(FilmCommands::spektakuler));
		root.then(ClientCommands.literal("film")
				.then(ClientCommands.literal("daftar").executes(FilmCommands::list))
				.then(ClientCommands.literal("info").executes(FilmCommands::info))
				.then(ClientCommands.argument("id", StringArgumentType.word()).suggests(FilmCommands::suggestFilms)
						.executes(context -> film(context, -1))
						.then(ClientCommands.literal("act").then(ClientCommands.argument("n", IntegerArgumentType.integer(0, 9))
								.executes(context -> film(context, IntegerArgumentType.getInteger(context, "n")))))));
		root.then(ClientCommands.literal("kosmik").executes(context -> cosmic(context, 6))
				.then(ClientCommands.argument("detik", IntegerArgumentType.integer(1, 30))
						.executes(context -> cosmic(context, IntegerArgumentType.getInteger(context, "detik")))));
		LiteralArgumentBuilder<FabricClientCommandSource> lens = ClientCommands.literal("lensa");
		for (String layer : LAYERS) {
			lens.then(ClientCommands.literal(layer).executes(context -> lens(context, layer, 4))
					.then(ClientCommands.argument("detik", IntegerArgumentType.integer(1, 20))
							.executes(context -> lens(context, layer, IntegerArgumentType.getInteger(context, "detik")))));
		}
		root.then(lens);
		root.then(ClientCommands.literal("waktu_beku").executes(FilmCommands::bullet));
	}

	/** Opens a pending motion warning once the chat screen has closed (from {@code FilmDirector.tick}). */
	static void tick() {
		if (warningIn > 0 && --warningIn == 0) {
			EpicSettings.requestSpektakuler();
		}
	}

	private static int spektakuler(CommandContext<FabricClientCommandSource> context) {
		if (EffectQuality.reduceMotion()) {
			return fail(context, Component.translatable(K + "reduce_motion"));
		}
		if (EpicSettings.spektakulerConfirmed()) {
			EpicSettings.requestSpektakuler();
			return ok(context, Component.translatable(K + "spektakuler"));
		}
		warningIn = 3;
		return ok(context, Component.translatable(K + "warning"));
	}

	private static int list(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		source.sendFeedback(Component.translatable(K + "list").withStyle(ChatFormatting.GOLD));
		for (String line : FilmCatalog.list()) {
			source.sendFeedback(Component.literal(line).withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}

	private static int info(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		source.sendFeedback(Component.translatable(K + "info").withStyle(ChatFormatting.GOLD));
		for (String line : FilmDirector.info()) {
			source.sendFeedback(Component.literal(line).withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}

	private static int film(CommandContext<FabricClientCommandSource> context, int act) {
		String id = StringArgumentType.getString(context, "id").toLowerCase(Locale.ROOT);
		String refused = FilmDirector.test(id, act);
		if (refused != null) {
			return fail(context, Component.translatable(K + "refused", id, refused));
		}
		return ok(context, Component.translatable(K + "playing", id));
	}

	private static int cosmic(CommandContext<FabricClientCommandSource> context, int seconds) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return 0;
		}
		if (!CosmicSky.show(seconds * 20, CosmicSky.Palette.VIOLET, player.getId() * 31L + seconds, player.getYRot(), true, true)) {
			return fail(context, Component.translatable(K + "cosmic_off"));
		}
		return ok(context, Component.translatable(K + "cosmic", seconds));
	}

	private static int lens(CommandContext<FabricClientCommandSource> context, String layer, int seconds) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return 0;
		}
		Vec3 look = player.getViewVector(1.0F);
		Vec3 ahead = player.getEyePosition().add(look.scale(30.0D));
		int ticks = seconds * 20;
		switch (layer) {
			case "grain" -> LensLayers.test(FilmLens.GRAIN, ticks, null);
			case "flare" -> LensLayers.test(FilmLens.FLARE, ticks, ahead);
			case "leak" -> LensLayers.test(FilmLens.LIGHT_LEAK, ticks, null);
			case "smear" -> LensLayers.test(FilmLens.SMEAR, ticks, null);
			case "fringe" -> LensLayers.test(FilmLens.FRINGE, ticks, null);
			default -> HeatHaze.show(player.position().add(look.x * 6.0D, 0.0D, look.z * 6.0D), 4.0F, ticks, 0xFFD8A0, player.getId());
		}
		return ok(context, Component.translatable(K + "lens", layer, seconds));
	}

	private static int bullet(CommandContext<FabricClientCommandSource> context) {
		if (!BulletTime.start(0.05F, 20)) {
			return fail(context, Component.translatable(K + "bullet_off"));
		}
		return ok(context, Component.translatable(K + "bullet"));
	}

	private static CompletableFuture<Suggestions> suggestFilms(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
		return SharedSuggestionProvider.suggest(FilmCatalog.all().keySet(), builder);
	}

	private static int ok(CommandContext<FabricClientCommandSource> context, Component message) {
		context.getSource().sendFeedback(message.copy().withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int fail(CommandContext<FabricClientCommandSource> context, Component message) {
		context.getSource().sendError(message);
		return 0;
	}

	private FilmCommands() {
	}
}
