package dev.kultivator.dunia.langit;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.api.SpiritWeather;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;

/**
 * The test commands of module L (DESIGN 34.5.8-34.5.10 "Uji"), registered through {@code DuniaCommands.test} under
 * {@code /kultivasi uji} and {@code /cultivate uji} (op, the single cheat root):
 * <ul>
 * <li>{@code langit meteor|gerhana|bulan_kembar [menit]}, {@code langit berhenti}, {@code langit jadwal},
 * {@code langit jatuh} (one meteor 20 blocks ahead now), {@code langit segel [integritas]} (one minute of the omen sky of a
 * weak prison seal, centred on the player);</li>
 * <li>{@code harta_lahir [1-4] [herba|inti|giok|pusaka|api|batu]} (60 blocks ahead, a 5-second tremor first),
 * {@code harta_lahir dekat} (12 blocks, at once), {@code harta_lahir hapus}, {@code harta_lahir jadwal};</li>
 * <li>{@code cuaca <cerah|tipis|pekat|ungu> [menit]} (the player's cell), {@code cuaca info}.</li>
 * </ul>
 * Tests work while the systems are switched off (previews); none of them touches a block, the weather or the time.
 */
final class LangitCommands {

	private static final String[] WEATHER_WORDS = {"cerah", "tipis", "pekat", "ungu"};

	private static final SuggestionProvider<CommandSourceStack> KINDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.asList(TreasureRewards.KINDS), builder);
	private static final SuggestionProvider<CommandSourceStack> WEATHERS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.asList(WEATHER_WORDS), builder);

	// ================================================================== uji langit

	static LiteralArgumentBuilder<CommandSourceStack> langit() {
		return Commands.literal("langit").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> usage(ctx.getSource(), "kultivator.langit.cmd.usage_langit"))
				.then(sky("meteor", SkyKind.METEOR_SHOWER))
				.then(sky("gerhana", SkyKind.BLOOD_ECLIPSE))
				.then(sky("bulan_kembar", SkyKind.TWIN_MOONS))
				.then(Commands.literal("berhenti").executes(LangitCommands::stop))
				.then(Commands.literal("jadwal").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					ctx.getSource().sendSuccess(() -> Msg.prefixed(SkyEvents.schedule(player.level())), false);
					return 1;
				}))
				.then(Commands.literal("jatuh").executes(LangitCommands::fall))
				.then(Commands.literal("segel")
						.executes(ctx -> sealOmen(ctx, 10))
						.then(Commands.argument("integritas", IntegerArgumentType.integer(0, 100))
								.executes(ctx -> sealOmen(ctx, IntegerArgumentType.getInteger(ctx, "integritas")))));
	}

	/** {@code uji langit segel [0-100]}: one minute of the sky omen of a seal at that integrity, centred here. */
	private static int sealOmen(CommandContext<CommandSourceStack> ctx, int integrity) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		boolean shown = SealOmens.preview(player.level(), player.blockPosition(), integrity);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(shown
				? Component.translatable("kultivator.langit.cmd.seal_omen", integrity, SealOmens.strength(integrity, integrity <= 0))
				: Component.translatable("kultivator.langit.cmd.seal_omen_none", integrity, SealOmens.THRESHOLD)), false);
		return shown ? 1 : 0;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> sky(String word, SkyKind kind) {
		return Commands.literal(word)
				.executes(ctx -> force(ctx, kind, 5))
				.then(Commands.argument("menit", IntegerArgumentType.integer(1, 600))
						.executes(ctx -> force(ctx, kind, IntegerArgumentType.getInteger(ctx, "menit"))));
	}

	private static int force(CommandContext<CommandSourceStack> ctx, SkyKind kind, int minutes) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		if (!SkyEvents.eligible(level)) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.langit.cmd.wrong_dimension"));
			return 0;
		}
		SkyEvents.force(level, kind, minutes);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.langit.cmd.forced", SkyEvents.name(kind), minutes)), true);
		return 1;
	}

	private static int stop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		boolean stopped = SkyEvents.stop(player.level());
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable(stopped ? "kultivator.langit.cmd.stopped"
				: "kultivator.langit.cmd.none")), true);
		return stopped ? 1 : 0;
	}

	private static int fall(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MeteorRohEntity meteor = SkyEvents.fall(player.level(), player, 20.0D);
		if (meteor == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.langit.cmd.no_spot"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.langit.cmd.falling",
				meteor.landing().toShortString(), meteor.fallTicks() / 20.0F)), false);
		return 1;
	}

	// ================================================================== uji harta_lahir

	static LiteralArgumentBuilder<CommandSourceStack> hartaLahir() {
		return Commands.literal("harta_lahir").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> birth(ctx, 0, null))
				.then(Commands.literal("dekat").executes(LangitCommands::near))
				.then(Commands.literal("hapus").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					boolean any = TreasureBirths.clear(player.level());
					ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable(any ? "kultivator.langit.cmd.harta_cleared"
							: "kultivator.langit.cmd.harta_none")), true);
					return any ? 1 : 0;
				}))
				.then(Commands.literal("jadwal").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Component text = TreasureBirths.scheduleNow(player);
					ctx.getSource().sendSuccess(() -> Msg.prefixed(text), false);
					return 1;
				}))
				.then(Commands.argument("tier", IntegerArgumentType.integer(1, 4))
						.executes(ctx -> birth(ctx, IntegerArgumentType.getInteger(ctx, "tier"), null))
						.then(Commands.argument("jenis", StringArgumentType.word()).suggests(KINDS)
								.executes(ctx -> birth(ctx, IntegerArgumentType.getInteger(ctx, "tier"), StringArgumentType.getString(ctx, "jenis")))));
	}

	private static int birth(CommandContext<CommandSourceStack> ctx, int tier, @Nullable String kind) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (kind != null && !TreasureRewards.validKind(kind.toLowerCase(Locale.ROOT))) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.langit.cmd.bad_kind", kind, String.join(", ", TreasureRewards.KINDS)));
			return 0;
		}
		BlockPos pos = ahead(player, 60.0D);
		TreasureBirths.testBirth(player.level(), pos, tier, kind == null ? null : kind.toLowerCase(Locale.ROOT));
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.langit.cmd.harta_tremor", pos.toShortString(),
				TreasureBirths.TEST_TREMOR_TICKS / 20)), true);
		return 1;
	}

	private static int near(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		BlockPos pos = ahead(player, 12.0D);
		var birth = TreasureBirths.spawnNow(player.level(), pos, 0, null, true);
		if (birth == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.langit.cmd.harta_refused"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.langit.cmd.harta_born", TreasureBirths.tierName(birth.tier()),
				TreasureBirths.kindName(birth.kind()), pos.toShortString())), true);
		return 1;
	}

	/** The ground {@code distance} blocks ahead of the player (loaded chunk: the heightmap; else the noise height). */
	private static BlockPos ahead(ServerPlayer player, double distance) {
		Vec3 look = player.getLookAngle();
		double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
		double dx = horizontal < 1.0E-3D ? 0.0D : look.x / horizontal;
		double dz = horizontal < 1.0E-3D ? 1.0D : look.z / horizontal;
		int x = (int) Math.floor(player.getX() + dx * distance);
		int z = (int) Math.floor(player.getZ() + dz * distance);
		ServerLevel level = player.level();
		int y;
		if (level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
			y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		} else {
			y = level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level,
					level.getChunkSource().randomState());
		}
		if (y <= level.getMinY()) {
			y = player.getBlockY();
		}
		return new BlockPos(x, y, z);
	}

	// ================================================================== uji cuaca

	static LiteralArgumentBuilder<CommandSourceStack> cuaca() {
		return Commands.literal("cuaca").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> usage(ctx.getSource(), "kultivator.langit.cmd.usage_cuaca"))
				.then(Commands.literal("info").executes(LangitCommands::weatherInfo))
				.then(Commands.argument("jenis", StringArgumentType.word()).suggests(WEATHERS)
						.executes(ctx -> setWeather(ctx, 10))
						.then(Commands.argument("menit", IntegerArgumentType.integer(1, 600))
								.executes(ctx -> setWeather(ctx, IntegerArgumentType.getInteger(ctx, "menit")))));
	}

	private static int setWeather(CommandContext<CommandSourceStack> ctx, int minutes) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String word = StringArgumentType.getString(ctx, "jenis").toLowerCase(Locale.ROOT);
		int index = Arrays.asList(WEATHER_WORDS).indexOf(word);
		if (index < 0) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.langit.cmd.bad_weather", word, String.join(", ", WEATHER_WORDS)));
			return 0;
		}
		ServerLevel level = player.level();
		if (!SpiritWeathers.eligible(level)) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.langit.cmd.wrong_dimension"));
			return 0;
		}
		SpiritWeather weather = SpiritWeather.values()[index];
		SpiritWeathers.override(level, player.blockPosition(), weather, minutes);
		SpiritWeathers.resend(player);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.langit.cmd.weather_set", weatherName(weather), minutes,
				LangitConfig.get().weather ? Component.translatable("options.on") : Component.translatable("kultivator.langit.cmd.weather_off"))), true);
		return 1;
	}

	private static int weatherInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		int cx = SpiritWeathers.cellOf(player.getBlockX());
		int cz = SpiritWeathers.cellOf(player.getBlockZ());
		SpiritWeather weather = SpiritWeathers.at(level, player.blockPosition());
		double bias = SpiritWeathers.eligible(level) ? SpiritWeathers.bias(level, cx, cz) : 0.0D;
		CompoundTag own = Langit.extra(Cultivators.get(player));
		long day = Math.floorDiv(level.getOverworldClockTime(), (long) SkyEvents.DAY);
		int dew = own.getLongOr("dew_day", -1L) == day ? own.getIntOr("dew_n", 0) : 0;
		Component text = Component.translatable("kultivator.langit.cmd.weather_info", cx, cz, SpiritWeathers.cellSize(), weatherName(weather),
				Msg.number(SpiritWeathers.factor(weather)), Msg.number(bias), SpiritWeathers.ticksToNextPeriod(level) / 20L,
				dew, LangitConfig.get().dewPerDay, Msg.number(LangitSites.factor(player)),
				Component.translatable(LangitSites.outdoors(player) ? "kultivator.langit.cmd.outdoors" : "kultivator.langit.cmd.indoors"));
		ctx.getSource().sendSuccess(() -> Msg.prefixed(text), false);
		return 1;
	}

	static Component weatherName(SpiritWeather weather) {
		int color = switch (weather) {
			case CLEAR -> 0xCFE8FF;
			case THIN_MIST -> 0xC8F0E0;
			case DENSE_MIST -> 0x9FE8C8;
			case PURPLE_DAWN -> 0xD0A0FF;
		};
		return Component.translatable("kultivator.langit.weather." + weather.id()).withColor(color);
	}

	private static int usage(CommandSourceStack source, String key) {
		source.sendSuccess(() -> Component.translatable(key).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private LangitCommands() {
	}
}
