package dev.kultivator.sekte.hubungan;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.SekteCommands;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.Ikatan;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * The commands of module R (DESIGN 36.2.2, 36.3.4):
 * <ul>
 * <li>{@code /kultivasi perguruan hubungan} (everyone): opens the Jaring Hubungan (or lists the ties in chat), and
 * {@code jawab <id> ya|tidak} (the request chips), {@code daftar}, {@code sumpah}, {@code restui}, {@code larang},
 * {@code duel <a> <b>};</li>
 * <li>{@code /kultivasi uji perguruan hubungan} (op): {@code info <a> <b>}, {@code set <a> <b> <-100..100>},
 * {@code jenis <a> <b> <ikatan>}, {@code temu <a> <b>}, {@code jaring}, {@code reset}, plus {@code hari} (the daily
 * round now), {@code duel <a> <b>} (a grudge pair asks at once) and {@code minta} (the open requests).</li>
 * </ul>
 * Names are the disciples' names as shown (quote names with spaces); only the master's own loaded disciples count.
 */
final class HubunganCommands {

	private static final SuggestionProvider<CommandSourceStack> NAMES = (ctx, builder) -> {
		List<String> names = new ArrayList<>();
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player != null) {
			for (DiscipleEntity disciple : Hubungan.loaded(player.getUUID()).values()) {
				String name = disciple.getName().getString();
				names.add(name.indexOf(' ') >= 0 || name.indexOf('"') >= 0 ? StringArgumentType.escapeIfRequired(name) : name);
			}
		}
		return SharedSuggestionProvider.suggest(names, builder);
	};

	private static final SuggestionProvider<CommandSourceStack> KINDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Ikatan.values()).map(Ikatan::id).toList(), builder);

	@FunctionalInterface
	private interface PairCommand {
		int run(ServerPlayer player, DiscipleEntity a, DiscipleEntity b, CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
	}

	static void init() {
		SekteCommands.test(SekteIds.R, HubunganCommands::testTree);
		SekteCommands.player(SekteIds.R, HubunganCommands::playerTree);
		SekteCommands.chips("perguruan hubungan info <a> <b>", "perguruan hubungan set <a> <b> <-100..100>", "perguruan hubungan jenis <a> <b> <ikatan>",
				"perguruan hubungan temu <a> <b>", "perguruan hubungan jaring", "perguruan hubungan hari", "perguruan hubungan duel <a> <b>",
				"perguruan hubungan minta", "perguruan hubungan reset");
	}

	// ================================================================== trees

	private static LiteralArgumentBuilder<CommandSourceStack> playerTree() {
		return Commands.literal(SekteIds.R)
				.executes(ctx -> open(ctx.getSource().getPlayerOrException()))
				.then(Commands.literal("daftar").executes(ctx -> list(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("jawab").then(Commands.argument("id", IntegerArgumentType.integer(1))
						.then(Commands.literal("ya").executes(ctx -> answer(ctx, true)))
						.then(Commands.literal("tidak").executes(ctx -> answer(ctx, false)))))
				.then(Commands.literal("sumpah").then(pair((player, a, b, ctx) -> {
					Upacara.oathFromScroll(player, a.soulId(), b.soulId());
					return 1;
				})))
				.then(Commands.literal("restui").then(pair((player, a, b, ctx) -> {
					Upacara.bless(player, a.soulId(), b.soulId());
					return 1;
				})))
				.then(Commands.literal("larang").then(pair((player, a, b, ctx) -> {
					Upacara.forbid(player, a.soulId(), b.soulId());
					return 1;
				})))
				.then(Commands.literal("duel").then(pair((player, a, b, ctx) -> {
					DuelKehormatan.fromScroll(player, a.soulId(), b.soulId());
					return 1;
				})));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> testTree() {
		return Commands.literal(SekteIds.R)
				.executes(ctx -> help(ctx.getSource()))
				.then(Commands.literal("info").then(pair(HubunganCommands::info)))
				.then(Commands.literal("set").then(pairThen(b -> b.then(Commands.argument("nilai", IntegerArgumentType.integer(-100, 100))
						.executes(ctx -> withPair(ctx, HubunganCommands::set))))))
				.then(Commands.literal("jenis").then(pairThen(b -> b.then(Commands.argument("ikatan", StringArgumentType.word()).suggests(KINDS)
						.executes(ctx -> withPair(ctx, HubunganCommands::kind))))))
				.then(Commands.literal("temu").then(pair((player, a, b, ctx) -> {
					Component why = GiliranSosial.forceTea(player.getUUID(), a, b);
					ctx.getSource().sendSuccess(() -> why == null ? Component.translatable("kultivator.sekte.hubungan.uji.temu", a.getName(), b.getName())
							.withStyle(ChatFormatting.GREEN) : why.copy().withStyle(ChatFormatting.RED), false);
					return why == null ? 1 : 0;
				})))
				.then(Commands.literal("jaring").executes(ctx -> jaring(ctx.getSource())))
				.then(Commands.literal("hari").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Hubungan.daily(player);
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.hubungan.uji.hari").withStyle(ChatFormatting.GREEN), false);
					return 1;
				}))
				.then(Commands.literal("duel").then(pair((player, a, b, ctx) -> {
					TabelPasangan.Pair pair = TabelPasangan.getOrCreate(player.getUUID(), a.soulId(), b.soulId());
					if (pair == null) {
						return noHall(ctx.getSource());
					}
					if (pair.kind() != Ikatan.DENDAM) {
						pair.setAffinity(Math.min(pair.affinity(), -70));
						pair.setWounded(true);
						Hubungan.setKind(player.getUUID(), pair, pair.kind(), Ikatan.DENDAM, a, b, false);
					}
					DuelKehormatan.ask(player.getUUID(), a.soulId(), b.soulId(), Hubungan.today());
					return 1;
				})))
				.then(Commands.literal("minta").executes(ctx -> requests(ctx.getSource())))
				.then(Commands.literal("reset").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					TabelPasangan.reset(player.getUUID());
					for (DiscipleEntity disciple : Hubungan.loaded(player.getUUID()).values()) {
						Hubungan.markDao(disciple, null);
						dev.kultivator.sekte.Ingatan.forget(disciple.data(), SekteIds.R + ".");
						EfekIkatan.invalidate(disciple.soulId());
					}
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.hubungan.uji.reset").withStyle(ChatFormatting.GREEN), true);
					return 1;
				}));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> pair(PairCommand command) {
		return pairThen(b -> b.executes(ctx -> withPair(ctx, command)));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> pairThen(
			Function<RequiredArgumentBuilder<CommandSourceStack, String>, RequiredArgumentBuilder<CommandSourceStack, String>> second) {
		return Commands.argument("a", StringArgumentType.string()).suggests(NAMES)
				.then(second.apply(Commands.argument("b", StringArgumentType.string()).suggests(NAMES)));
	}

	private static int withPair(CommandContext<CommandSourceStack> ctx, PairCommand command) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (!Hubungan.active()) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.hubungan.mati"));
			return 0;
		}
		if (!TabelPasangan.available(player.getUUID())) {
			return noHall(ctx.getSource());
		}
		DiscipleEntity a = byName(player, StringArgumentType.getString(ctx, "a"));
		DiscipleEntity b = byName(player, StringArgumentType.getString(ctx, "b"));
		if (a == null || b == null || a == b) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.hubungan.uji.nama"));
			return 0;
		}
		return command.run(player, a, b, ctx);
	}

	private static @Nullable DiscipleEntity byName(ServerPlayer player, String name) {
		for (DiscipleEntity disciple : Hubungan.loaded(player.getUUID()).values()) {
			if (disciple.getName().getString().equalsIgnoreCase(name.trim())) {
				return disciple;
			}
		}
		return null;
	}

	private static int noHall(CommandSourceStack source) {
		source.sendFailure(Component.translatable("kultivator.sekte.hubungan.tanpa_aula"));
		return 0;
	}

	// ================================================================== player

	private static int open(ServerPlayer player) {
		if (!Hubungan.active()) {
			player.sendSystemMessage(Component.translatable("kultivator.sekte.hubungan.mati").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, HubunganPayloads.Jaring.TYPE)) {
			Hubungan.sendJaring(player);
			return 1;
		}
		return list(player);
	}

	private static int list(ServerPlayer player) {
		UUID owner = player.getUUID();
		if (!TabelPasangan.available(owner)) {
			player.sendSystemMessage(Component.translatable("kultivator.sekte.hubungan.tanpa_aula").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		List<TabelPasangan.Pair> pairs = new ArrayList<>();
		for (TabelPasangan.Pair pair : TabelPasangan.all(owner)) {
			if (pair.known() && pair.kind() != Ikatan.NONE) {
				pairs.add(pair);
			}
		}
		pairs.sort(Comparator.comparingInt((TabelPasangan.Pair p) -> -Kecocokan.effectRank(p.kind()) * 1000 - Math.abs(p.affinity())));
		player.sendSystemMessage(Component.translatable("kultivator.sekte.hubungan.daftar.judul", pairs.size()).withStyle(ChatFormatting.GOLD));
		if (pairs.isEmpty()) {
			player.sendSystemMessage(Component.translatable("kultivator.sekte.hubungan.daftar.kosong").withStyle(ChatFormatting.GRAY));
		}
		for (int i = 0; i < Math.min(20, pairs.size()); i++) {
			TabelPasangan.Pair pair = pairs.get(i);
			player.sendSystemMessage(line(owner, pair));
		}
		return pairs.size();
	}

	private static MutableComponent line(UUID owner, TabelPasangan.Pair pair) {
		return Component.translatable("kultivator.sekte.hubungan.daftar.baris", TabelPasangan.name(owner, pair.a), TabelPasangan.name(owner, pair.b),
				Component.translatable(pair.kind().key()).withColor(kindColor(pair.kind())), pair.affinity());
	}

	static int kindColor(Ikatan kind) {
		return switch (kind) {
			case SAHABAT -> 0x8FE3B0;
			case SAUDARA_ANGKAT -> 0xF2C66A;
			case KEKASIH -> 0xFF9CC4;
			case PASANGAN_DAO -> 0xFF6FA8;
			case SAINGAN -> 0xE59A6A;
			case DENDAM -> 0xE0584A;
			case KENALAN -> 0xB8C8C0;
			case NONE -> 0x909090;
		};
	}

	private static int answer(CommandContext<CommandSourceStack> ctx, boolean yes) throws CommandSyntaxException {
		Upacara.answer(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "id"), yes);
		return 1;
	}

	// ================================================================== tests

	private static int help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.hubungan.uji.bantuan").withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	private static int info(ServerPlayer player, DiscipleEntity a, DiscipleEntity b, CommandContext<CommandSourceStack> ctx) {
		UUID owner = player.getUUID();
		TabelPasangan.Pair pair = TabelPasangan.find(owner, a.soulId(), b.soulId());
		CommandSourceStack source = ctx.getSource();
		int compat = Kecocokan.compatibility(a.data(), b.data());
		if (pair == null) {
			source.sendSuccess(() -> Component.translatable("kultivator.sekte.hubungan.uji.info.baru", a.getName(), b.getName(), compat)
					.withStyle(ChatFormatting.GRAY), false);
			return 1;
		}
		long today = Hubungan.today();
		source.sendSuccess(() -> line(owner, pair), false);
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.hubungan.uji.info", compat, pair.meetings(), pair.tournamentBouts(),
				pair.changedToday(today), pair.wounded() ? "ya" : "-", pair.banned() ? "ya" : "-").withStyle(ChatFormatting.GRAY), false);
		for (CompoundTag event : pair.history()) {
			String reason = event.getStringOr("r", "");
			int value = event.getIntOr("v", 0);
			long day = event.getLongOr("d", 0L);
			source.sendSuccess(() -> Component.literal("  " + (value > 0 ? "+" : "") + value + "  " + reason + "  (" + day + ")")
					.withStyle(value >= 0 ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED), false);
		}
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.hubungan.uji.info.saldo", Hubungan.balance(owner, a.soulId()),
				Hubungan.balance(owner, b.soulId())).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int set(ServerPlayer player, DiscipleEntity a, DiscipleEntity b, CommandContext<CommandSourceStack> ctx) {
		int value = IntegerArgumentType.getInteger(ctx, "nilai");
		TabelPasangan.Pair pair = TabelPasangan.getOrCreate(player.getUUID(), a.soulId(), b.soulId());
		if (pair == null) {
			return noHall(ctx.getSource());
		}
		int delta = value - pair.affinity();
		if (value <= Kecocokan.GRUDGE) {
			pair.setWounded(true);
		}
		Hubungan.change(player.getUUID(), a.soulId(), b.soulId(), delta, SekteIds.R + ".uji", 0);
		ctx.getSource().sendSuccess(() -> line(player.getUUID(), pair), false);
		return 1;
	}

	private static int kind(ServerPlayer player, DiscipleEntity a, DiscipleEntity b, CommandContext<CommandSourceStack> ctx) {
		Ikatan kind = Ikatan.byId(StringArgumentType.getString(ctx, "ikatan"));
		TabelPasangan.Pair pair = TabelPasangan.getOrCreate(player.getUUID(), a.soulId(), b.soulId());
		if (pair == null) {
			return noHall(ctx.getSource());
		}
		int floor = switch (kind) {
			case SAHABAT -> Kecocokan.FRIEND;
			case SAUDARA_ANGKAT -> Kecocokan.OATH;
			case KEKASIH -> Kecocokan.LOVE;
			case PASANGAN_DAO -> Kecocokan.DAO;
			case SAINGAN -> Kecocokan.RIVAL;
			case DENDAM -> Kecocokan.GRUDGE;
			default -> 0;
		};
		pair.setAffinity(kind.negative() ? Math.min(pair.affinity(), floor) : kind.positive() ? Math.max(pair.affinity(), floor) : floor);
		pair.setWounded(kind == Ikatan.DENDAM);
		Hubungan.setKind(player.getUUID(), pair, pair.kind(), kind, a, b, false);
		if (kind == Ikatan.PASANGAN_DAO) {
			Hubungan.markDao(a, b.soulId());
			Hubungan.markDao(b, a.soulId());
		}
		ctx.getSource().sendSuccess(() -> line(player.getUUID(), pair), false);
		return 1;
	}

	private static int jaring(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Hubungan.sendJaring(player);
		return list(player);
	}

	private static int requests(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		UUID owner = player.getUUID();
		List<TabelPasangan.Request> open = TabelPasangan.requests(owner);
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.hubungan.uji.minta", open.size()).withStyle(ChatFormatting.GOLD), false);
		for (TabelPasangan.Request request : open) {
			Upacara.announce(owner, request.id(), request.type(), request.a(), request.b());
		}
		return open.size();
	}

	private HubunganCommands() {
	}
}
