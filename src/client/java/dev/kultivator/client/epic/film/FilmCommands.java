package dev.kultivator.client.epic.film;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.camera.Shots;
import dev.kultivator.client.epic.cosmic.CosmicSky;
import dev.kultivator.client.epic.kosmik.Kosmik3D;
import dev.kultivator.client.epic.kosmik.StageContext;
import dev.kultivator.client.epic.lens.HeatHaze;
import dev.kultivator.client.epic.lens.LensLayers;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * The film commands of {@code /sinematik} (DESIGN 30.6, client, no op): {@code spektakuler}, {@code film daftar|info},
 * {@code film <id> [act <n>]}, {@code kosmik [detik]}, {@code lensa <layer> [detik]} and {@code waktu_beku}. Every film
 * still passes every gate of 30.2.2; only the cooldowns of a test scene are skipped.
 * <p>
 * Film Kosmik 3D (DESIGN 32.7): {@code kosmik3d orbit|surga [detik] [tingkat]} previews a stage (its three preview beats
 * as stage shots joined by black dips under Kamera Spektakuler; one calm 4-s beat under Kamera Penuh; Singkat and Mati
 * refuse), {@code kosmik3d beat <orbit|surga> <beat> [detik] [tingkat]} plays one beat alone and {@code kosmik3d info}
 * prints the setting, the stage tier, the last pocket, the last stage frame's cost and each stage's state.
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
		// Film Kosmik 3D (32.7)
		LiteralArgumentBuilder<FabricClientCommandSource> kosmik3d = ClientCommands.literal("kosmik3d");
		for (Kosmik3D.StageId id : Kosmik3D.StageId.values()) {
			kosmik3d.then(ClientCommands.literal(id.id()).executes(context -> stagePreview(context, id, 8, 1))
					.then(ClientCommands.argument("detik", IntegerArgumentType.integer(2, 30))
							.executes(context -> stagePreview(context, id, IntegerArgumentType.getInteger(context, "detik"), 1))
							.then(ClientCommands.argument("tingkat", IntegerArgumentType.integer(1, 9))
									.executes(context -> stagePreview(context, id, IntegerArgumentType.getInteger(context, "detik"),
											IntegerArgumentType.getInteger(context, "tingkat"))))));
		}
		kosmik3d.then(ClientCommands.literal("beat").then(ClientCommands.argument("panggung", StringArgumentType.word())
				.suggests((context, builder) -> SharedSuggestionProvider.suggest(new String[]{"orbit", "surga"}, builder))
				.then(ClientCommands.argument("beat", StringArgumentType.word()).suggests(FilmCommands::suggestBeats)
						.executes(context -> beat(context, 5, 1))
						.then(ClientCommands.argument("detik", IntegerArgumentType.integer(1, 6))
								.executes(context -> beat(context, IntegerArgumentType.getInteger(context, "detik"), 1))
								.then(ClientCommands.argument("tingkat", IntegerArgumentType.integer(1, 9))
										.executes(context -> beat(context, IntegerArgumentType.getInteger(context, "detik"),
												IntegerArgumentType.getInteger(context, "tingkat"))))))));
		kosmik3d.then(ClientCommands.literal("info").executes(FilmCommands::kosmikInfo));
		root.then(kosmik3d);
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

	// ================================================================ Film Kosmik 3D (DESIGN 32.7)

	/** The longest preview: the scene's 400-tick camera cap (20 s), whatever the requested seconds. */
	private static final int PREVIEW_MAX = 400;
	private static final String[] PREVIEW_BEATS = {"preview_a", "preview_b", "preview_c"};
	private static final String[] SURGA_SOURCES = {"gerbang", "aula", "cincin", "laut_awan", "pengawal", "tiang_naga", "jembatan", "air_terjun",
			"kelopak", "tiang"};

	private static int stagePreview(CommandContext<FabricClientCommandSource> context, Kosmik3D.StageId id, int seconds, int tier) {
		sources(context, id);
		EpicSettings.Camera camera = CinematicDirector.mode();
		if (camera != EpicSettings.Camera.PENUH) {
			return fail(context, Component.translatable(K + "kosmik3d.camera"));
		}
		int total = Math.max(20, Math.min(PREVIEW_MAX, seconds * 20));
		if (!EpicSettings.spektakuler()) {
			// Kamera Penuh: one calm 4-s beat
			return calm(context, id, "preview_a", 80, tier);
		}
		int n = Math.max(3, (total + 119) / 120);
		int each = Math.max(12, total / n);
		Film film = Film.of("kosmik3d_" + id.id()).preview().tint(id == Kosmik3D.StageId.ORBIT ? 0xFFD54A : 0xFF9A3A);
		FilmAct act = FilmAct.cast(0);
		for (int i = 0; i < n; i++) {
			act.cut(FilmShot.Mask.BLACK_DIP).shot(beatShot(id, PREVIEW_BEATS[i % PREVIEW_BEATS.length], each));
		}
		film.act(act);
		FilmCatalog.validate(film);
		String refused = FilmDirector.preview(film, tier);
		if (refused != null) {
			return fail(context, Component.translatable(K + "kosmik3d.refused", id.id(), refused));
		}
		return ok(context, Component.translatable(K + "kosmik3d.started", id.id(), Math.round(n * each / 20.0F)));
	}

	private static int beat(CommandContext<FabricClientCommandSource> context, int seconds, int tier) {
		String stage = StringArgumentType.getString(context, "panggung").toLowerCase(Locale.ROOT);
		String beat = StringArgumentType.getString(context, "beat").toLowerCase(Locale.ROOT);
		Kosmik3D.StageId id = Kosmik3D.StageId.byId(stage);
		if (id == null) {
			return fail(context, Component.translatable(K + "kosmik3d.beat_unknown", beat, stage, "orbit, surga"));
		}
		List<String> beats = Kosmik3D.beats(id);
		if (!beats.contains(beat)) {
			return fail(context, Component.translatable(K + "kosmik3d.beat_unknown", beat, id.id(), String.join(", ", beats)));
		}
		if (CinematicDirector.mode() != EpicSettings.Camera.PENUH) {
			return fail(context, Component.translatable(K + "kosmik3d.camera"));
		}
		int ticks = Math.max(20, Math.min(120, seconds * 20));
		if (!EpicSettings.spektakuler()) {
			return calm(context, id, beat, Math.min(80, ticks), tier);
		}
		Film film = Film.of("kosmik3d_" + id.id() + "_" + beat).preview();
		film.act(FilmAct.cast(0).cut(FilmShot.Mask.BLACK_DIP).shot(beatShot(id, beat, ticks)));
		FilmCatalog.validate(film);
		String refused = FilmDirector.preview(film, tier);
		if (refused != null) {
			return fail(context, Component.translatable(K + "kosmik3d.refused", id.id() + "/" + beat, refused));
		}
		return ok(context, Component.translatable(K + "kosmik3d.started", id.id() + "/" + beat, ticks / 20));
	}

	/** Kamera Penuh: one calm stage beat as a single Film Pusaka-style segment (no translation, ≤ 16°/s). */
	private static int calm(CommandContext<FabricClientCommandSource> context, Kosmik3D.StageId id, String beat, int ticks, int tier) {
		Scene scene = EpicDirector.testScene();
		LocalPlayer player = Minecraft.getInstance().player;
		if (scene == null || player == null) {
			return fail(context, Component.translatable(K + "kosmik3d.refused", id.id(), "scene"));
		}
		StageContext ctx = Kosmik3D.context(-1, tier, id == Kosmik3D.StageId.ORBIT ? 0xFFD54A : 0xFF9A3A, player.getId() * 31L + tier, true);
		Kosmik3D.PenuhPlan plan = Kosmik3D.calm(id, beat, ctx, ticks);
		if (plan == null) {
			return fail(context, Component.translatable(K + "kosmik3d.refused", id.id(), Kosmik3D.lastRefusal()));
		}
		Shots.Shot shot = Shots.pusakaFilm(plan.c(), plan.c(), plan.look0(), plan.look1(), ticks).cut().stage(plan.binding());
		EpicDirector.refreshFlags(scene);
		if (!CinematicDirector.shoot(scene, shot, CinematicDirector.KIND_ULTIMATE, scene.priority, true)) {
			return fail(context, Component.translatable(K + "kosmik3d.refused", id.id(), "gate"));
		}
		return ok(context, Component.translatable(K + "kosmik3d.started", id.id() + "/" + beat, ticks / 20));
	}

	private static int kosmikInfo(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		source.sendFeedback(Component.translatable(K + "kosmik3d.info").withStyle(ChatFormatting.GOLD));
		for (String line : Kosmik3D.info()) {
			source.sendFeedback(Component.literal(line).withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}

	/** The stage's name and its Sumber lines (when the stage module's texts exist). */
	private static void sources(CommandContext<FabricClientCommandSource> context, Kosmik3D.StageId id) {
		FabricClientCommandSource source = context.getSource();
		Language language = Language.getInstance();
		String base = "kultivator.kosmik." + id.id();
		if (language.has(base + ".name")) {
			source.sendFeedback(Component.translatable(base + ".name").withStyle(ChatFormatting.GOLD));
		}
		if (id == Kosmik3D.StageId.ORBIT) {
			if (language.has(base + ".source")) {
				source.sendFeedback(Component.translatable(base + ".source").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		for (String part : SURGA_SOURCES) {
			if (language.has(base + "." + part + ".source")) {
				source.sendFeedback(Component.translatable(base + "." + part + ".source").withStyle(ChatFormatting.GRAY));
			}
		}
	}

	/**
	 * One stage shot of {@code beat} for the previews: the film framings of 32.5 for the film beats, calm drifts for the
	 * Penuh beats and the three preview framings (planet wide / the sword / the Dipper; up to the gate / the court / the
	 * nine rings). Every shot falls back to a sky drift.
	 */
	static FilmShot beatShot(Kosmik3D.StageId id, String beat, int ticks) {
		FilmShot shot = FilmShot.of(ticks).stage(id, beat).orElse(FilmDirector.fallbackOf(ticks));
		if (id == Kosmik3D.StageId.ORBIT) {
			shot.quiet(0.6F).tint(0xFFD54A);
			switch (beat) {
				case "pedang_hadir" -> shot.keyV(0.0F, "sword", 60.0D, 40.0D, -110.0D).keyV(1.0F, "sword", 45.0D, 30.0D, -85.0D).lookV("sword", 0.0D, 0.0D, 0.0D)
						.lookEndV("sword", 0.0D, 12.0D, 0.0D).flareV("sun").lens(FilmLens.ANAMORPHIC);
				case "pedang_menukik" -> shot.keyV(0.0F, "home", 45.0D, 350.0D, -85.0D).keyV(1.0F, "home", 45.0D, 352.0D, -88.0D)
						.lookV("sword", 0.0D, 0.0D, 0.0D);
				case "pedang_masuk" -> shot.keyV(0.0F, "home", -700.0D, 900.0D, -400.0D).keyV(1.0F, "home", -640.0D, 860.0D, -380.0D)
						.lookV("beam", 0.0D, 0.0D, 0.0D);
				case "bintang_biduk", "bintang_tenang", "preview_c" -> shot.keyV(0.0F, "dipper", -20.0D, -10.0D, -160.0D)
						.keyV(1.0F, "dipper", -8.0D, -4.0D, -120.0D).lookV("dipper", 0.0D, 0.0D, 0.0D).lookEndV("core7", 0.0D, 0.0D, 0.0D)
						.flareV("core1").tint(0xB57CFF);
				case "bintang_sinar" -> shot.keyV(0.0F, "home", 900.0D, 700.0D, 300.0D).keyV(1.0F, "home", 860.0D, 660.0D, 280.0D)
						.lookV("home", 0.0D, 40.0D, 0.0D).tint(0xB57CFF);
				case "gunung_sel" -> shot.keyV(0.0F, "home", -260.0D, 420.0D, -380.0D).keyV(1.0F, "home", -230.0D, 400.0D, -340.0D)
						.lookV("cells", 0.0D, 0.0D, 0.0D).tint(0x5FD6A0);
				case "pedang_tenang" -> shot.keyV(0.0F, "sword", 60.0D, 40.0D, -110.0D).keyV(1.0F, "sword", 54.0D, 36.0D, -100.0D)
						.lookV("sword", 0.0D, 0.0D, 0.0D).lookEndV("sword", 0.0D, 8.0D, 0.0D);
				case "preview_b" -> shot.keyV(0.0F, "sword", 60.0D, 30.0D, -100.0D).keyV(0.5F, "sword", 0.0D, 36.0D, -118.0D)
						.keyV(1.0F, "sword", -60.0D, 30.0D, -100.0D).lookV("sword", 0.0D, 0.0D, 0.0D);
				default -> shot.keyV(0.0F, "planet", -900.0D, 700.0D, 3000.0D).keyV(1.0F, "planet", -700.0D, 760.0D, 2900.0D)
						.lookV("planet", 0.0D, 0.0D, 0.0D).lookEndV("planet", 0.0D, 200.0D, 0.0D).flareV("sun").lens(FilmLens.ANAMORPHIC);
			}
			return shot;
		}
		shot.tint(0xFF9A3A);
		switch (beat) {
			case "tiang_gerbang" -> shot.keyV(0.0F, "gerbang", 0.0D, -52.0D, -150.0D).keyV(1.0F, "gerbang", 0.0D, -38.0D, -105.0D)
					.lookV("gerbang", 0.0D, 0.0D, 0.0D).lookEndV("gerbang", 0.0D, 10.0D, 0.0D).flareV("gerbang", 0.0D, 0.0D, 10.0D);
			case "tiang_lepas" -> shot.keyV(0.0F, "gerbang", 0.0D, -38.0D, -105.0D).keyV(1.0F, "gerbang", 40.0D, 0.0D, -30.0D)
					.lookV("tiang", 0.0D, 0.0D, 0.0D);
			case "tiang_jatuh" -> shot.keyV(0.0F, "tiang", 26.0D, 20.0D, 0.0D).keyV(1.0F, "tiang", 22.0D, 30.0D, 0.0D).lookV("tiang", 0.0D, -25.0D, 0.0D);
			case "gunung_segel", "gunung_tenang" -> shot.keyV(0.0F, "laut", 40.0D, 60.0D, -80.0D).keyV(1.0F, "laut", 40.0D, 90.0D, -80.0D)
					.lookV("segel", 0.0D, 0.0D, 0.0D).tint(0xFFD54A);
			case "tiang_tenang" -> shot.keyV(0.0F, "gerbang", 0.0D, -20.0D, -140.0D).keyV(1.0F, "gerbang", 0.0D, -16.0D, -120.0D)
					.lookV("gerbang", 0.0D, 0.0D, 0.0D).lookEndV("gerbang", 0.0D, 12.0D, 0.0D);
			case "preview_b" -> shot.keyV(0.0F, "gerbang", 25.0D, 18.0D, 20.0D).keyV(1.0F, "gerbang", 15.0D, 24.0D, 200.0D)
					.lookV("aula", 0.0D, 20.0D, 0.0D);
			case "preview_c" -> shot.keyV(0.0F, "gerbang", 70.0D, -30.0D, -70.0D).keyV(1.0F, "gerbang", 50.0D, -40.0D, -50.0D)
					.lookV("cincin1", 0.0D, 0.0D, 0.0D);
			default -> shot.keyV(0.0F, "gerbang", 0.0D, -80.0D, -190.0D).keyV(1.0F, "gerbang", 0.0D, -20.0D, -110.0D)
					.lookV("gerbang", 0.0D, 0.0D, 0.0D).lookEndV("gerbang", 0.0D, 15.0D, 0.0D).flareV("gerbang", 0.0D, 0.0D, 10.0D)
					.lens(FilmLens.ANAMORPHIC);
		}
		return shot;
	}

	private static CompletableFuture<Suggestions> suggestBeats(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
		String stage;
		try {
			stage = StringArgumentType.getString(context, "panggung").toLowerCase(Locale.ROOT);
		} catch (IllegalArgumentException exception) {
			return builder.buildFuture();
		}
		Kosmik3D.StageId id = Kosmik3D.StageId.byId(stage);
		return id == null ? builder.buildFuture() : SharedSuggestionProvider.suggest(Kosmik3D.beats(id), builder);
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
