package dev.kultivator.sekte.kepribadian;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteCommands;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.HatiService.Keadaan;
import dev.kultivator.sekte.api.Kenangan;
import dev.kultivator.sekte.api.Kepergian;
import dev.kultivator.sekte.api.Perlakuan;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Module P's commands (DESIGN 36.2.1, 36.3.4):
 * <ul>
 * <li>{@code /kultivasi perguruan murid} (everyone): the hearts of the own disciples in chat; {@code murid tantangan
 * terima|tolak} answers a rebel's challenge, {@code murid izin ya|tidak} a wanderer's request (the clickable chat
 * buttons run these).</li>
 * <li>{@code /kultivasi uji perguruan kepribadian} (op): {@code info <nama>}, {@code sifat <nama> <id> [id] [id]},
 * {@code puas <nama> <0-100>}, {@code siksa <nama> <perlakuan>}, {@code hari <n>}, {@code pergi <nama> <cara>},
 * {@code pulih <nama>|semua}, {@code yangpergi}, {@code bujuk <sekte>}, {@code reset}.</li>
 * </ul>
 * Names are the disciple's name with spaces written as underscores, among the player's loaded disciples.
 */
public final class KepribadianCommands {

	private static final SuggestionProvider<CommandSourceStack> NAMES = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		List<String> names = new ArrayList<>();
		if (player != null) {
			for (DiscipleEntity e : Disciples.loaded(player.getUUID())) {
				names.add(e.getName().getString().replace(' ', '_'));
			}
		}
		return SharedSuggestionProvider.suggest(names, builder);
	};

	private static final SuggestionProvider<CommandSourceStack> TRAITS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Sifat.values()).map(Sifat::id), builder);

	private static final SuggestionProvider<CommandSourceStack> TREATMENTS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Perlakuan.values()).map(Perlakuan::id), builder);

	private static final SuggestionProvider<CommandSourceStack> WAYS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Kepergian.values()).map(Kepergian::id), builder);

	private static final SuggestionProvider<CommandSourceStack> GONE = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		List<String> names = new ArrayList<>(List.of("semua"));
		if (player != null) {
			for (YangPergi.Entry entry : YangPergi.list(ctx.getSource().getServer(), player.getUUID())) {
				names.add(entry.name().getString().replace(' ', '_'));
			}
		}
		return SharedSuggestionProvider.suggest(names, builder);
	};

	static void init() {
		SekteCommands.test(SekteIds.P, KepribadianCommands::testNode);
		SekteCommands.player("murid", KepribadianCommands::playerNode);
		SekteCommands.chips("perguruan kepribadian info <nama>", "perguruan kepribadian sifat <nama> <id>", "perguruan kepribadian puas <nama> <0-100>",
				"perguruan kepribadian siksa <nama> <perlakuan>", "perguruan kepribadian hari <n>", "perguruan kepribadian pergi <nama> <cara>",
				"perguruan kepribadian pulih <nama>", "perguruan kepribadian yangpergi", "perguruan kepribadian bujuk <sekte>",
				"perguruan kepribadian reset");
	}

	// ================================================================== player

	private static LiteralArgumentBuilder<CommandSourceStack> playerNode() {
		return Commands.literal("murid")
				.executes(ctx -> hearts(ctx.getSource().getPlayerOrException()))
				.then(Commands.literal("tantangan")
						.then(Commands.literal("terima").executes(ctx -> KepergianMurid.answerChallenge(ctx.getSource().getPlayerOrException(), true)))
						.then(Commands.literal("tolak").executes(ctx -> KepergianMurid.answerChallenge(ctx.getSource().getPlayerOrException(), false))))
				.then(Commands.literal("izin")
						.then(Commands.literal("ya").executes(ctx -> KepergianMurid.answerWander(ctx.getSource().getPlayerOrException(), true)))
						.then(Commands.literal("tidak").executes(ctx -> KepergianMurid.answerWander(ctx.getSource().getPlayerOrException(), false))));
	}

	/** {@code /kultivasi perguruan murid}: one line per loaded disciple (traits, state, loyalty, days left). */
	private static int hearts(ServerPlayer player) {
		if (!Kepribadian.on()) {
			player.sendSystemMessage(Component.translatable("kultivator.sekte.kepribadian.blok.mati").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		Advancements.grant(player, "sekte/kepribadian_sifat");
		player.sendSystemMessage(Component.translatable("kultivator.sekte.kepribadian.cmd.judul").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		int count = 0;
		for (DiscipleEntity e : Disciples.loaded(player.getUUID())) {
			if (e.role() != DiscipleEntity.Role.DISCIPLE || !Hati.known(e.data())) {
				continue;
			}
			player.sendSystemMessage(line(e));
			count++;
		}
		if (count == 0) {
			player.sendSystemMessage(Component.translatable("kultivator.sekte.kepribadian.cmd.kosong").withStyle(ChatFormatting.GRAY));
		}
		List<YangPergi.Entry> gone = YangPergi.list(player.level().getServer(), player.getUUID());
		if (!gone.isEmpty()) {
			player.sendSystemMessage(Component.translatable("kultivator.sekte.kepribadian.cmd.yangpergi", gone.size()).withStyle(ChatFormatting.DARK_GRAY));
		}
		return count;
	}

	static Component line(DiscipleEntity e) {
		DiscipleData data = e.data();
		Keadaan state = Kesetiaan.state(data);
		ChatFormatting color = switch (state) {
			case SETIA -> ChatFormatting.GREEN;
			case GELISAH -> ChatFormatting.YELLOW;
			case MEMBANGKANG -> ChatFormatting.GOLD;
			case AKAN_PERGI -> ChatFormatting.RED;
		};
		MutableComponent traits = Component.empty();
		List<Sifat> list = Hati.traits(data);
		for (int i = 0; i < list.size(); i++) {
			if (i > 0) {
				traits.append(", ");
			}
			traits.append(Component.translatable(list.get(i).key()));
		}
		MutableComponent line = Component.literal(" ").append(e.getDisplayName().copy().withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
				.append(traits.withStyle(ChatFormatting.AQUA))
				.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.translatable("kultivator.sekte.keadaan." + state.name().toLowerCase(Locale.ROOT)).withStyle(color))
				.append(Component.translatable("kultivator.sekte.kepribadian.cmd.angka", Kesetiaan.loyalty(data), Hati.contentment(data),
						DisciplePaths.bond(data)).withStyle(ChatFormatting.GRAY));
		int left = Kesetiaan.daysLeft(data);
		if (left >= 0) {
			line.append(Component.translatable("kultivator.sekte.kepribadian.cmd.sisa", left).withStyle(ChatFormatting.RED));
		}
		if (Kesetiaan.bound(data)) {
			line.append(Component.translatable("kultivator.sekte.kepribadian.cmd.terikat").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		return line;
	}

	// ================================================================== tests

	private static LiteralArgumentBuilder<CommandSourceStack> testNode() {
		return Commands.literal(SekteIds.P)
				.then(Commands.literal("info").then(Commands.argument("nama", StringArgumentType.word()).suggests(NAMES)
						.executes(ctx -> info(ctx))))
				.then(Commands.literal("sifat").then(Commands.argument("nama", StringArgumentType.word()).suggests(NAMES)
						.then(Commands.argument("a", StringArgumentType.word()).suggests(TRAITS)
								.executes(ctx -> traits(ctx, 1))
								.then(Commands.argument("b", StringArgumentType.word()).suggests(TRAITS)
										.executes(ctx -> traits(ctx, 2))
										.then(Commands.argument("c", StringArgumentType.word()).suggests(TRAITS)
												.executes(ctx -> traits(ctx, 3)))))))
				.then(Commands.literal("puas").then(Commands.argument("nama", StringArgumentType.word()).suggests(NAMES)
						.then(Commands.argument("nilai", IntegerArgumentType.integer(0, 100)).executes(KepribadianCommands::setContentment))))
				.then(Commands.literal("siksa").then(Commands.argument("nama", StringArgumentType.word()).suggests(NAMES)
						.then(Commands.argument("perlakuan", StringArgumentType.word()).suggests(TREATMENTS).executes(KepribadianCommands::mistreat))))
				.then(Commands.literal("hari").then(Commands.argument("n", IntegerArgumentType.integer(1, 30)).executes(KepribadianCommands::days)))
				.then(Commands.literal("pergi").then(Commands.argument("nama", StringArgumentType.word()).suggests(NAMES)
						.then(Commands.argument("cara", StringArgumentType.word()).suggests(WAYS).executes(KepribadianCommands::leave))))
				.then(Commands.literal("pulih").then(Commands.argument("nama", StringArgumentType.word()).suggests(GONE)
						.executes(KepribadianCommands::bringBack)))
				.then(Commands.literal("yangpergi").executes(ctx -> gone(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("bujuk").then(Commands.argument("sekte", StringArgumentType.word()).executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					int n = Kepribadian.lure(ctx.getSource().getServer(), player.getUUID(), StringArgumentType.getString(ctx, "sekte"));
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.kepribadian.uji.bujuk", n), false);
					return n;
				})))
				.then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource().getPlayerOrException())));
	}

	private static @Nullable DiscipleEntity find(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(ctx, "nama");
		for (DiscipleEntity e : Disciples.loaded(player.getUUID())) {
			if (e.role() == DiscipleEntity.Role.DISCIPLE && e.getName().getString().replace(' ', '_').equalsIgnoreCase(name)) {
				return e;
			}
		}
		ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.kepribadian.uji.tidak_ada", name));
		return null;
	}

	private static void ensureKnown(DiscipleEntity e, MinecraftServer server) {
		if (!Hati.known(e.data())) {
			DiscipleData data = e.data();
			Hati.setTraits(data, Watak.roll(data, e.getRandom()));
			Hati.setContentment(data, Hati.START);
			Hati.putLong(data, "day", Ingatan.today(server));
			Hati.putLong(data, "last", Ingatan.today(server));
		}
	}

	private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity e = find(ctx);
		if (e == null) {
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		ensureKnown(e, server);
		DiscipleData data = e.data();
		long today = Ingatan.today(server);
		ctx.getSource().sendSuccess(() -> line(e), false);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.kepribadian.uji.info",
				String.format(Locale.ROOT, "%.1f", Hati.contentmentExact(data)), Hati.getInt(data, "low", 0), Kesetiaan.threshold(data),
				String.format(Locale.ROOT, "%.2f", Watak.workFactor(data)), String.format(Locale.ROOT, "%.1f", Ingatan.drift(data, today)),
				String.format(Locale.ROOT, "%.1f", Hati.careOf(data, today)), today - Hati.getLong(data, "last", today)).withStyle(ChatFormatting.GRAY), false);
		for (Kenangan k : Ingatan.of(data)) {
			ctx.getSource().sendSuccess(() -> Component.literal("  " + k.kind() + " d" + k.day() + " w" + k.weight() + " ")
					.append(Component.translatable(k.text(), k.arg())).withStyle(k.weight() >= 0 ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED), false);
		}
		KepergianMurid.Pending pending = KepergianMurid.pending(e.soulId());
		if (pending != null) {
			ctx.getSource().sendSuccess(() -> Component.literal("  pending " + pending.fase + " " + pending.how.id()).withStyle(ChatFormatting.RED), false);
		}
		return 1;
	}

	private static int traits(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
		DiscipleEntity e = find(ctx);
		if (e == null) {
			return 0;
		}
		ensureKnown(e, ctx.getSource().getServer());
		List<Sifat> list = new ArrayList<>();
		for (String arg : new String[]{"a", "b", "c"}) {
			if (list.size() >= count) {
				break;
			}
			Sifat sifat = Sifat.byId(StringArgumentType.getString(ctx, arg));
			if (sifat == null) {
				ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.kepribadian.uji.sifat_salah", StringArgumentType.getString(ctx, arg)));
				return 0;
			}
			list.add(sifat);
		}
		Hati.setTraits(e.data(), list);
		Kepribadian.afterChange(e, false);
		ctx.getSource().sendSuccess(() -> line(e), true);
		return 1;
	}

	private static int setContentment(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity e = find(ctx);
		if (e == null) {
			return 0;
		}
		ensureKnown(e, ctx.getSource().getServer());
		Hati.setContentment(e.data(), IntegerArgumentType.getInteger(ctx, "nilai"));
		Kepribadian.afterChange(e, false);
		ctx.getSource().sendSuccess(() -> line(e), true);
		return 1;
	}

	private static int mistreat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity e = find(ctx);
		if (e == null) {
			return 0;
		}
		ensureKnown(e, ctx.getSource().getServer());
		String id = StringArgumentType.getString(ctx, "perlakuan");
		Perlakuan kind = null;
		for (Perlakuan p : Perlakuan.values()) {
			if (p.id().equals(id)) {
				kind = p;
			}
		}
		if (kind == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.kepribadian.uji.perlakuan_salah", id));
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		SekteBus.mistreated(ctx.getSource().getServer(), player.getUUID(), e.soulId(), kind, SekteIds.P + ".uji");
		ctx.getSource().sendSuccess(() -> line(e), true);
		return 1;
	}

	/** Runs {@code n} daily steps for every loaded disciple of the player now. */
	private static int days(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		int n = IntegerArgumentType.getInteger(ctx, "n");
		long today = Ingatan.today(server);
		int count = 0;
		for (DiscipleEntity e : Disciples.loaded(player.getUUID())) {
			if (e.role() != DiscipleEntity.Role.DISCIPLE) {
				continue;
			}
			ensureKnown(e, server);
			for (int i = 0; i < n; i++) {
				Kepribadian.runDays(server, e, player.getUUID(), today - 1, today);
			}
			count++;
		}
		int done = count;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.kepribadian.uji.hari", n, done), true);
		for (DiscipleEntity e : Disciples.loaded(player.getUUID())) {
			if (e.role() == DiscipleEntity.Role.DISCIPLE && Hati.known(e.data())) {
				ctx.getSource().sendSuccess(() -> line(e), false);
			}
		}
		return count;
	}

	private static int leave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity e = find(ctx);
		if (e == null) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ensureKnown(e, ctx.getSource().getServer());
		String id = StringArgumentType.getString(ctx, "cara");
		Kepergian how = null;
		for (Kepergian k : Kepergian.values()) {
			if (k.id().equals(id)) {
				how = k;
			}
		}
		if (how == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.kepribadian.uji.cara_salah", id));
			return 0;
		}
		String why = KepergianMurid.forceStart(ctx.getSource().getServer(), e, player, how);
		if (why != null) {
			ctx.getSource().sendFailure(Component.translatable(why, e.getDisplayName()));
			return 0;
		}
		Kepergian chosen = how;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.kepribadian.uji.pergi", Component.translatable(chosen.key())), true);
		return 1;
	}

	private static int bringBack(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		String name = StringArgumentType.getString(ctx, "nama");
		int count = 0;
		for (YangPergi.Entry entry : YangPergi.list(server, player.getUUID())) {
			if ("semua".equalsIgnoreCase(name) || entry.name().getString().replace(' ', '_').equalsIgnoreCase(name)) {
				if (YangPergi.bringBack(server, player.getUUID(), entry.soul(), "uji")) {
					count++;
				}
			}
		}
		if (count == 0) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.kepribadian.uji.tidak_ada", name));
		}
		return count;
	}

	private static int gone(ServerPlayer player) {
		List<YangPergi.Entry> list = YangPergi.list(player.level().getServer(), player.getUUID());
		player.sendSystemMessage(Component.translatable("kultivator.sekte.kepribadian.cmd.yangpergi", list.size()).withStyle(ChatFormatting.GOLD));
		long today = Ingatan.today(player.level().getServer());
		for (YangPergi.Entry entry : list) {
			player.sendSystemMessage(Component.literal(" ").append(entry.name()).append(" · ")
					.append(Component.translatable(entry.how().key()))
					.append(Component.translatable("kultivator.sekte.kepribadian.cmd.hari_lalu", Math.max(0L, today - entry.day())))
					.withStyle(ChatFormatting.GRAY));
		}
		return list.size();
	}

	private static int reset(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		int count = 0;
		for (DiscipleEntity e : Disciples.loaded(player.getUUID())) {
			if (e.role() != DiscipleEntity.Role.DISCIPLE) {
				continue;
			}
			if (e.activity() == SekteIds.ACT_SULK || e.activity() == SekteIds.ACT_LEAVING) {
				e.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			}
			Hati.clear(e.data());
			Ingatan.forget(e.data(), SekteIds.P + ".");
			count++;
		}
		YangPergi.clear(server, player.getUUID());
		KepergianMurid.clear();
		int done = count;
		player.sendSystemMessage(Component.translatable("kultivator.sekte.kepribadian.uji.reset", done).withStyle(ChatFormatting.GREEN));
		return count;
	}

	private KepribadianCommands() {
	}
}
