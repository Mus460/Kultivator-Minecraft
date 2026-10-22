package dev.kultivator.sekte.tetua;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sekte.SekteCommands;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.Jabatan;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Module E's commands (DESIGN 36.2.6 "Uji"): {@code /kultivasi uji perguruan tetua} (op) with {@code angkat <nama>
 * <jabatan>} (no rank check), {@code copot <nama>}, {@code laporan} (the morning report now), {@code urusan} (the next
 * affair now), {@code takhta <nama>} (the ceremony without its requirements), {@code lewati} (to the climax) and
 * {@code generasi} (the lineage); and for everyone {@code /kultivasi perguruan tetua} (the Balai Tetua screen).
 */
final class TetuaCommands {

	static void init() {
		SekteCommands.test(SekteIds.E, TetuaCommands::tests);
		SekteCommands.player(SekteIds.E, () -> Commands.literal(SekteIds.E).executes(ctx -> {
			BalaiTetua.open(ctx.getSource().getPlayerOrException());
			return 1;
		}));
		SekteCommands.chips("perguruan tetua angkat <nama> <jabatan>", "perguruan tetua copot <nama>", "perguruan tetua laporan",
				"perguruan tetua urusan", "perguruan tetua takhta <nama>", "perguruan tetua lewati", "perguruan tetua generasi");
	}

	private static LiteralArgumentBuilder<CommandSourceStack> tests() {
		return Commands.literal(SekteIds.E)
				.executes(ctx -> {
					BalaiTetua.open(ctx.getSource().getPlayerOrException());
					return 1;
				})
				.then(Commands.literal("angkat")
						.then(Commands.argument("nama", StringArgumentType.string()).suggests((ctx, b) -> SharedSuggestionProvider.suggest(names(ctx), b))
								.then(Commands.argument("jabatan", StringArgumentType.word())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(officeIds(), b))
										.executes(TetuaCommands::appoint))))
				.then(Commands.literal("copot")
						.then(Commands.argument("nama", StringArgumentType.string()).suggests((ctx, b) -> SharedSuggestionProvider.suggest(names(ctx), b))
								.executes(TetuaCommands::dismiss)))
				.then(Commands.literal("laporan").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					LaporanPagi.deliver(ctx.getSource().getServer(), player, false);
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.tetua.cmd.report"), false);
					return 1;
				}))
				.then(Commands.literal("urusan").executes(TetuaCommands::affair))
				.then(Commands.literal("takhta")
						.then(Commands.argument("nama", StringArgumentType.string()).suggests((ctx, b) -> SharedSuggestionProvider.suggest(names(ctx), b))
								.executes(TetuaCommands::throne)))
				.then(Commands.literal("lewati").executes(ctx -> {
					boolean ok = PenyerahanTakhta.skip(ctx.getSource().getPlayerOrException().getUUID());
					ctx.getSource().sendSuccess(() -> Component.translatable(ok ? "kultivator.sekte.tetua.cmd.skip" : "kultivator.sekte.tetua.cmd.no_ceremony"), false);
					return ok ? 1 : 0;
				}))
				.then(Commands.literal("generasi").executes(TetuaCommands::lineage));
	}

	private static List<String> officeIds() {
		List<String> ids = new ArrayList<>();
		for (Jabatan j : Jabatan.values()) {
			ids.add(j.id());
		}
		return ids;
	}

	private static List<String> names(CommandContext<CommandSourceStack> ctx) {
		List<String> list = new ArrayList<>();
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player != null) {
			for (DiscipleEntity d : Disciples.loaded(player.getUUID())) {
				String name = d.getName().getString();
				list.add(name.indexOf(' ') >= 0 ? "\"" + name + "\"" : name);
			}
		}
		return list;
	}

	private static @Nullable DiscipleEntity byName(ServerPlayer player, String name) {
		for (DiscipleEntity d : Disciples.loaded(player.getUUID())) {
			if (d.role() == DiscipleEntity.Role.DISCIPLE && d.getName().getString().equalsIgnoreCase(name.trim())) {
				return d;
			}
		}
		return null;
	}

	private static int appoint(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		DiscipleEntity entity = byName(player, StringArgumentType.getString(ctx, "nama"));
		Jabatan office = Jabatan.byId(StringArgumentType.getString(ctx, "jabatan"));
		if (entity == null || office == null) {
			ctx.getSource().sendFailure(Component.translatable(entity == null ? "kultivator.sekte.tetua.cmd.no_disciple" : "kultivator.sekte.tetua.cmd.no_office"));
			return 0;
		}
		Component why = JabatanTetua.appoint(player, entity, office, true);
		if (why != null) {
			ctx.getSource().sendFailure(why);
			return 0;
		}
		return 1;
	}

	private static int dismiss(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		DiscipleEntity entity = byName(player, StringArgumentType.getString(ctx, "nama"));
		Jabatan office = entity == null ? null : TetuaData.officeOf(player.getUUID(), entity.soulId());
		if (office == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.tetua.cmd.no_holder"));
			return 0;
		}
		JabatanTetua.dismiss(ctx.getSource().getServer(), player.getUUID(), office, player);
		return 1;
	}

	private static int affair(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		UUID owner = player.getUUID();
		MinecraftServer server = ctx.getSource().getServer();
		UrusanHarian.hurry(owner);
		int ran = 0;
		for (Map.Entry<Jabatan, UUID> e : TetuaData.offices(owner).entrySet()) {
			DiscipleEntity elder = JabatanTetua.loaded(owner, e.getValue());
			if (elder != null) {
				UrusanHarian.run(server, owner, elder, e.getKey());
				ran++;
			}
		}
		int count = ran;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.tetua.cmd.affairs", count).withStyle(ChatFormatting.AQUA), false);
		return ran;
	}

	private static int throne(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		DiscipleEntity entity = byName(player, StringArgumentType.getString(ctx, "nama"));
		if (entity == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.tetua.cmd.no_disciple"));
			return 0;
		}
		Component why = PenyerahanTakhta.start(ctx.getSource().getServer(), player, true, entity);
		if (why != null) {
			ctx.getSource().sendFailure(why);
			return 0;
		}
		return 1;
	}

	private static int lineage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		UUID owner = player.getUUID();
		CommandSourceStack source = ctx.getSource();
		int gen = Generasi.generation(owner);
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.tetua.cmd.lineage", gen, Generasi.founder(owner)
				? Component.translatable("kultivator.sekte.tetua.yes") : Component.translatable("kultivator.sekte.tetua.no")).withStyle(ChatFormatting.GOLD), false);
		CompoundTag mine = TetuaData.hall(owner);
		for (Tag tag : mine.getListOrEmpty(TetuaData.LINEAGE)) {
			if (tag instanceof CompoundTag line) {
				Component name = TetuaData.name(line, "name");
				int g = line.getIntOr("gen", 0);
				long day = line.getLongOr("day", 0L);
				source.sendSuccess(() -> Component.translatable("kultivator.sekte.tetua.cmd.lineage_line", g, name, day), false);
			}
		}
		UUID heir = Generasi.heir(owner);
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.tetua.cmd.heir",
				heir == null ? Component.translatable("kultivator.sekte.tetua.none") : TetuaData.name(mine, TetuaData.HEIR_NAME)), false);
		for (Generasi.Requirement r : Generasi.requirements(source.getServer(), owner)) {
			source.sendSuccess(() -> Component.literal(r.ok() ? "✔ " : "✘ ").withStyle(r.ok() ? ChatFormatting.GREEN : ChatFormatting.RED)
					.append(r.text().copy().withStyle(ChatFormatting.GRAY)), false);
		}
		return 1;
	}

	private TetuaCommands() {
	}
}
