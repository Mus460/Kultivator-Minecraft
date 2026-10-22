package dev.kultivator.sekte.diplomasi;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.sekte.SekteCommands;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.Lambang;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Commands of module D (DESIGN 36.2.9). Op tests under {@code /kultivasi uji perguruan diplomasi}: {@code daftar},
 * {@code info <sekte>}, {@code standing <sekte> <n>}, {@code aksi <sekte> <peristiwa>}, {@code tumbuh <sekte>},
 * {@code perang <sekte>}, {@code damai <sekte>}, {@code menang <sekte>}, {@code ketua <sekte>}, {@code gerbang <sekte>}
 * (teleport to the planned gate), {@code simulasi <hari>}, {@code layar}. Everyone: {@code /kultivasi perguruan
 * diplomasi [sekte]} opens the Diplomasi screen. A sect is named by its id ({@code s0}..) or its number.
 */
final class DiplomasiCommands {

	private static final SuggestionProvider<CommandSourceStack> SECTS = (ctx, builder) -> {
		List<String> ids = new ArrayList<>();
		MinecraftServer server = ctx.getSource().getServer();
		for (SekteNpc sect : DiplomasiData.get(server).sects()) {
			ids.add(sect.id);
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	};

	private static final SuggestionProvider<CommandSourceStack> EVENTS = (ctx, builder) -> {
		List<String> ids = new ArrayList<>();
		for (Peristiwa p : Peristiwa.values()) {
			ids.add(p.id());
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	};

	static void register() {
		SekteCommands.chips("perguruan diplomasi daftar", "perguruan diplomasi info <sekte>", "perguruan diplomasi standing <sekte> <n>",
				"perguruan diplomasi aksi <sekte> <peristiwa>", "perguruan diplomasi tumbuh <sekte>", "perguruan diplomasi perang <sekte>",
				"perguruan diplomasi damai <sekte>", "perguruan diplomasi menang <sekte>", "perguruan diplomasi ketua <sekte>",
				"perguruan diplomasi gerbang <sekte>", "perguruan diplomasi simulasi <hari>", "perguruan diplomasi layar");
		SekteCommands.test(SekteIds.D, DiplomasiCommands::tests);
		SekteCommands.player(SekteIds.D, () -> Commands.literal("diplomasi")
				.executes(ctx -> open(ctx, null))
				.then(Commands.argument("sekte", StringArgumentType.word()).suggests(SECTS)
						.executes(ctx -> open(ctx, StringArgumentType.getString(ctx, "sekte")))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> tests() {
		return Commands.literal("diplomasi")
				.executes(DiplomasiCommands::list)
				.then(Commands.literal("daftar").executes(DiplomasiCommands::list))
				.then(Commands.literal("layar").executes(ctx -> open(ctx, null)))
				.then(Commands.literal("info").then(sect().executes(DiplomasiCommands::info)))
				.then(Commands.literal("standing").then(sect().then(Commands.argument("n", IntegerArgumentType.integer(-100, 100))
						.executes(DiplomasiCommands::standing))))
				.then(Commands.literal("aksi").then(sect().then(Commands.argument("peristiwa", StringArgumentType.word()).suggests(EVENTS)
						.executes(DiplomasiCommands::event))))
				.then(Commands.literal("tumbuh").then(sect().executes(ctx -> eventNamed(ctx, Peristiwa.TUMBUH))))
				.then(Commands.literal("perang").then(sect().executes(ctx -> eventNamed(ctx, Peristiwa.PERANG))))
				.then(Commands.literal("ketua").then(sect().executes(DiplomasiCommands::ketua)))
				.then(Commands.literal("damai").then(sect().executes(DiplomasiCommands::damai)))
				.then(Commands.literal("menang").then(sect().executes(DiplomasiCommands::menang)))
				.then(Commands.literal("gerbang").then(sect().executes(DiplomasiCommands::gate)))
				.then(Commands.literal("simulasi").then(Commands.argument("hari", IntegerArgumentType.integer(1, 30))
						.executes(DiplomasiCommands::simulate)));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> sect() {
		return Commands.argument("sekte", StringArgumentType.word()).suggests(SECTS);
	}

	private static @Nullable SekteNpc sectOf(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "sekte");
		DiplomasiData data = DiplomasiData.get(ctx.getSource().getServer());
		SekteNpc sect = data.sect(id);
		if (sect == null) {
			try {
				sect = data.sect(Integer.parseInt(id));
			} catch (NumberFormatException ignored) {
				sect = null;
			}
		}
		if (sect == null) {
			ctx.getSource().sendFailure(Component.translatable(GeneratorNama.KEY + "uji.tidak_ada", id));
		}
		return sect;
	}

	private static int open(CommandContext<CommandSourceStack> ctx, @Nullable String sect) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		DiplomasiPayloads.open(player, sect, 0);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		MinecraftServer server = source.getServer();
		DiplomasiData data = DiplomasiData.get(server);
		ServerPlayer player = source.getPlayer();
		source.sendSuccess(() -> Component.translatable(GeneratorNama.KEY + "uji.daftar", data.sects().size(), data.planCount, data.planMinDist)
				.withStyle(ChatFormatting.GOLD), false);
		for (SekteNpc sect : data.sects()) {
			Relasi r = player == null ? null : data.relasi(player.getUUID(), sect);
			Component line = Component.translatable(GeneratorNama.KEY + "uji.baris", sect.id, sect.name(),
					Component.translatable(GeneratorNama.KEY + "aliran." + SekteNpc.ALIRAN[sect.aliran]), sect.tier, sect.strength(),
					r == null ? "-" : Integer.toString(r.standing), r == null ? Component.literal("-") : Component.translatable(r.sikap().key()),
					sect.blockX, sect.blockZ, GerbangPlan.BAND_NAMES[sect.band],
					Component.translatable(GeneratorNama.KEY + (sect.gateBorn() ? "uji.lahir" : "uji.belum")));
			source.sendSuccess(() -> line, false);
		}
		return data.sects().size();
	}

	private static int info(CommandContext<CommandSourceStack> ctx) {
		SekteNpc sect = sectOf(ctx);
		if (sect == null) {
			return 0;
		}
		CommandSourceStack source = ctx.getSource();
		DiplomasiData data = DiplomasiData.get(source.getServer());
		ServerPlayer player = source.getPlayer();
		Lambang emblem = sect.emblem();
		source.sendSuccess(() -> Component.translatable(GeneratorNama.KEY + "uji.info", sect.name(), sect.master(), sect.tier, sect.sumber,
				sect.nextTierCost(), sect.agresi, sect.kehormatan, emblem.shape(), emblem.symbol(), sect.raiderSect, sect.pembelot.size(),
				sect.musuhNpc).withStyle(ChatFormatting.AQUA), false);
		if (player != null) {
			Relasi r = data.relasi(player.getUUID(), sect);
			source.sendSuccess(() -> Component.translatable(GeneratorNama.KEY + "uji.relasi", r.standing, Component.translatable(r.sikap().key()),
					r.pakta, r.perang, r.perangKalah, r.perangMenang, r.bawahan, r.upetiPaksa, r.ditemui), false);
		}
		source.sendSuccess(() -> Component.translatable(GeneratorNama.KEY + "uji.tubuh", AnggotaSekte.alive().size()), false);
		return 1;
	}

	private static int standing(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		SekteNpc sect = sectOf(ctx);
		if (sect == null) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int n = IntegerArgumentType.getInteger(ctx, "n");
		DiplomasiData data = DiplomasiData.get(ctx.getSource().getServer());
		Relasi r = data.relasi(player.getUUID(), sect);
		Standing.adjust(ctx.getSource().getServer(), player.getUUID(), sect, n - r.standing, "uji");
		ctx.getSource().sendSuccess(() -> Component.translatable(GeneratorNama.KEY + "uji.standing", sect.name(), r.standing,
				Component.translatable(r.sikap().key())), true);
		return 1;
	}

	private static int event(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Peristiwa event = Peristiwa.byId(StringArgumentType.getString(ctx, "peristiwa"));
		if (event == null) {
			ctx.getSource().sendFailure(Component.translatable(GeneratorNama.KEY + "uji.peristiwa_tidak_ada"));
			return 0;
		}
		return eventNamed(ctx, event);
	}

	private static int eventNamed(CommandContext<CommandSourceStack> ctx, Peristiwa event) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		SekteNpc sect = sectOf(ctx);
		if (sect == null) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		DiplomasiData data = DiplomasiData.get(server);
		boolean wasOn = Diplomasi.config().perangAktif;
		Simulasi.apply(server, data, player.getUUID(), sect, data.relasi(player.getUUID(), sect), event, server.overworld().getRandom(),
				Diplomasi.today(server));
		ctx.getSource().sendSuccess(() -> Component.translatable(GeneratorNama.KEY + "uji.aksi", event.id(), sect.name(),
				wasOn ? "" : "(perangAktif=false)"), true);
		return 1;
	}

	private static int ketua(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		SekteNpc sect = sectOf(ctx);
		if (sect == null) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		KetuaSekte.summonToHall(ctx.getSource().getServer(), player.getUUID(), sect);
		return 1;
	}

	private static int damai(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		SekteNpc sect = sectOf(ctx);
		if (sect == null) {
			return 0;
		}
		Perang.damai(ctx.getSource().getServer(), ctx.getSource().getPlayerOrException().getUUID(), sect);
		return 1;
	}

	private static int menang(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		SekteNpc sect = sectOf(ctx);
		if (sect == null) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Perang.menang(ctx.getSource().getServer(), player.getUUID(), sect, player);
		return 1;
	}

	private static int gate(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		SekteNpc sect = sectOf(ctx);
		if (sect == null) {
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = ctx.getSource().getServer().overworld();
		BlockPos at = sect.gateBorn() ? sect.gate().above() : Tubuh.ground(level, new BlockPos(sect.blockX, 0, sect.blockZ)).above(2);
		player.teleportTo(level, at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D + 12.0D, Set.<Relative>of(), 180.0F, 0.0F, true);
		ctx.getSource().sendSuccess(() -> Component.translatable(GeneratorNama.KEY + (sect.gateBorn() ? "uji.gerbang" : "uji.gerbang_belum"),
				sect.name(), at.getX(), at.getY(), at.getZ()), true);
		return 1;
	}

	private static int simulate(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		DiplomasiData data = DiplomasiData.get(server);
		int days = IntegerArgumentType.getInteger(ctx, "hari");
		long today = Diplomasi.today(server);
		for (int i = 1; i <= days; i++) {
			long day = today + i;
			for (SekteNpc sect : data.sects()) {
				Simulasi.grow(server, data, sect, day);
			}
			Simulasi.daily(server, data, player.getUUID(), player, day);
		}
		ctx.getSource().sendSuccess(() -> Component.translatable(GeneratorNama.KEY + "uji.simulasi", days), true);
		return days;
	}

	private DiplomasiCommands() {
	}
}
