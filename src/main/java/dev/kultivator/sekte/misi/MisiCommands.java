package dev.kultivator.sekte.misi;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteCommands;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.MisiHasil;
import dev.kultivator.sekte.api.SekteBus;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /kultivasi uji perguruan misi} (op, DESIGN 36.2.7) and {@code /kultivasi perguruan misi} (every player):
 * <ul>
 * <li>{@code kirim <templat> <sikap> <nama, nama..>}: sends a team on that template at once (an offer is added to the
 * board; every rule of a normal departure applies);</li>
 * <li>{@code selesai}: every running mission is due now (the teams come home on the next hall tick);</li>
 * <li>{@code hasil <0-3>}: the running missions end with that many successful stages;</li>
 * <li>{@code laporan}: the last report again as a scroll (or a sample when there is none);</li>
 * <li>{@code pulang <nama>}: fires {@code MISSION_RETURNED} with {@code pulang:<soul>} for a departed disciple of the
 * search list (contract C36.X4: module P brings it back);</li>
 * <li>{@code papan}: a new board today; {@code info}: the running missions and their stage chances.</li>
 * </ul>
 */
final class MisiCommands {

	static void init() {
		SekteCommands.test(SekteIds.X, MisiCommands::testNode);
		SekteCommands.player(SekteIds.X, () -> Commands.literal(SekteIds.X).executes(ctx -> status(ctx.getSource())));
		SekteCommands.chips("perguruan misi kirim <templat> <sikap> <nama,..>", "perguruan misi selesai", "perguruan misi hasil <0-3>",
				"perguruan misi laporan", "perguruan misi pulang <nama>", "perguruan misi papan", "perguruan misi info");
	}

	private static LiteralArgumentBuilder<CommandSourceStack> testNode() {
		return Commands.literal(SekteIds.X)
				.executes(ctx -> info(ctx.getSource()))
				.then(Commands.literal("kirim")
						.then(Commands.argument("templat", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(templateIds(), builder))
								.then(Commands.argument("sikap", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("damai", "tegas", "licik"), builder))
										.then(Commands.argument("nama", StringArgumentType.greedyString()).executes(MisiCommands::send)))))
				.then(Commands.literal("selesai").executes(ctx -> due(ctx.getSource())))
				.then(Commands.literal("hasil").then(Commands.argument("n", IntegerArgumentType.integer(0, 3))
						.executes(ctx -> force(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n")))))
				.then(Commands.literal("laporan").executes(ctx -> report(ctx.getSource())))
				.then(Commands.literal("pulang").then(Commands.argument("nama", StringArgumentType.greedyString())
						.executes(ctx -> home(ctx.getSource(), StringArgumentType.getString(ctx, "nama")))))
				.then(Commands.literal("papan").executes(ctx -> reroll(ctx.getSource())))
				.then(Commands.literal("info").executes(ctx -> info(ctx.getSource())));
	}

	private static List<String> templateIds() {
		List<String> ids = new ArrayList<>();
		for (TemplatMisi tpl : TemplatMisi.values()) {
			ids.add(tpl.id());
		}
		return ids;
	}

	private static int send(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TemplatMisi tpl = TemplatMisi.byId(StringArgumentType.getString(ctx, "templat").toLowerCase(Locale.ROOT));
		if (tpl == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.misi.uji.templat", String.join(", ", templateIds())));
			return 0;
		}
		TemplatMisi.Pendekatan stance = TemplatMisi.Pendekatan.byId(StringArgumentType.getString(ctx, "sikap").toLowerCase(Locale.ROOT));
		List<UUID> souls = new ArrayList<>();
		for (String part : StringArgumentType.getString(ctx, "nama").split(",")) {
			String wanted = part.trim();
			for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
				if (!wanted.isEmpty() && disciple.getName().getString().equalsIgnoreCase(wanted) && !souls.contains(disciple.soulId())) {
					souls.add(disciple.soulId());
				}
			}
		}
		if (souls.isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.sekte.misi.uji.nama"));
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		ListTag board = TimMisi.board(server, player.getUUID());
		CompoundTag offer = new CompoundTag();
		offer.putString("tpl", tpl.id());
		offer.putInt("tier", Math.max(tpl.minTier(), Math.min(tpl.maxTier(), TimMisi.strongestTier(player.getUUID()))));
		offer.putInt("place", server.overworld().getRandom().nextInt(TimMisi.PLACES));
		offer.putInt("foe", server.overworld().getRandom().nextInt(TimMisi.FOES));
		offer.putBoolean("taken", false);
		if (tpl.has(TemplatMisi.Flags.SEARCH)) {
			List<CompoundTag> wanted = MisiData.searchable(player.getUUID());
			if (!wanted.isEmpty()) {
				offer.putString("target", wanted.get(0).getStringOr("soul", ""));
				MisiData.putName(offer, "target_name", MisiData.name(wanted.get(0), "name"));
				offer.putString("target_how", wanted.get(0).getStringOr("how", ""));
			}
		}
		board.add(offer);
		MisiData.dirty();
		return TimMisi.send(player, board.size() - 1, stance, souls) ? 1 : 0;
	}

	private static int due(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		UUID owner = player.getUUID();
		long now = source.getServer().overworld().getGameTime();
		int count = 0;
		for (CompoundTag mission : MisiData.active(owner)) {
			if (mission.getBooleanOr("finished", false)) {
				continue;
			}
			mission.putLong("start", Math.min(mission.getLongOr("start", now), now - mission.getLongOr("len", 24000L)));
			mission.putLong("end", now);
			for (CompoundTag member : MisiData.team(mission)) {
				UUID soul = MisiData.soul(member);
				if (soul != null && !member.getBooleanOr("left", false)) {
					MisiData.moveReturn(owner, soul, now);
				}
			}
			count++;
		}
		MisiData.dirty();
		int n = count;
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.misi.uji.selesai", n).withStyle(ChatFormatting.GREEN), true);
		return count;
	}

	private static int force(CommandSourceStack source, int successes) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		int count = 0;
		for (CompoundTag mission : MisiData.active(player.getUUID())) {
			if (!mission.getBooleanOr("finished", false)) {
				mission.putInt("paksa", successes);
				count++;
			}
		}
		MisiData.dirty();
		int n = count;
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.misi.uji.hasil", n, successes).withStyle(ChatFormatting.GREEN), true);
		return count;
	}

	private static int report(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		List<CompoundTag> history = MisiData.history(player.getUUID());
		CompoundTag report = history.isEmpty() ? sample(source.getServer(), player) : history.get(history.size() - 1);
		Kepulangan.deliver(source.getServer(), player, report);
		return 1;
	}

	/** A sample report (a legendary escort with two members) for the screen test without a real mission. */
	private static CompoundTag sample(MinecraftServer server, ServerPlayer player) {
		CompoundTag report = new CompoundTag();
		report.putString("tpl", TemplatMisi.KAWAL.id());
		report.putInt("tier", 3);
		report.putString("sikap", TemplatMisi.Pendekatan.TEGAS.id());
		report.putInt("grade", 3);
		report.putLong("day", Ingatan.today(server));
		report.putInt("place", 3);
		report.putInt("foe", 1);
		ListTag team = new ListTag();
		List<DiscipleEntity> loaded = Disciples.loaded(player.getUUID());
		for (int i = 0; i < Math.max(1, Math.min(2, loaded.size())); i++) {
			CompoundTag entry = new CompoundTag();
			MisiData.putName(entry, "name", i < loaded.size() ? loaded.get(i).getDisplayName() : Component.literal("Han Li"));
			entry.putInt("color", 0x9FE8C8);
			entry.putBoolean("hurt", i == 1);
			team.add(entry);
		}
		report.put("team", team);
		ListTag stages = new ListTag();
		String[] kinds = {"jelajah", "tempur", "damai"};
		for (int i = 0; i < 3; i++) {
			CompoundTag stage = new CompoundTag();
			stage.putString("kind", kinds[i]);
			stage.putBoolean("ok", true);
			stage.putDouble("chance", 0.7D);
			stages.add(stage);
		}
		report.put("stages", stages);
		report.putInt("recruits", 2);
		report.putInt("treasures", 1);
		ListTag items = new ListTag();
		CompoundTag line = new CompoundTag();
		MisiData.putName(line, "text", Component.translatable("kultivator.sekte.misi.lapor.item", 3, Component.translatable("item.minecraft.emerald")));
		items.add(line);
		report.put("items", items);
		return report;
	}

	private static int home(CommandSourceStack source, String name) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		UUID owner = player.getUUID();
		for (CompoundTag entry : MisiData.searchable(owner)) {
			if (MisiData.name(entry, "name").getString().equalsIgnoreCase(name.trim())) {
				UUID soul = MisiData.soul(entry);
				if (soul == null) {
					continue;
				}
				MisiData.removeSearchable(owner, soul);
				MisiHasil result = new MisiHasil(SekteIds.X + "." + TemplatMisi.CARI.id(), List.of(), 2, 0, 0, TemplatMisi.CARI.key() + ".s3.ok",
						List.of("pulang:" + soul, TemplatMisi.CARI.id(), "1"), null, List.of());
				SekteBus.MISSION_RETURNED.invoker().onMissionReturned(source.getServer(), owner, result);
				source.sendSuccess(() -> Component.translatable("kultivator.sekte.misi.uji.pulang", MisiData.name(entry, "name"))
						.withStyle(ChatFormatting.GREEN), true);
				return 1;
			}
		}
		source.sendFailure(Component.translatable("kultivator.sekte.misi.uji.pulang_tidak", MisiData.searchable(owner).size()));
		return 0;
	}

	private static int reroll(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		TimMisi.roll(server, player.getUUID(), Ingatan.today(server), server.overworld().getRandom());
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.misi.uji.papan", TimMisi.board(server, player.getUUID()).size())
				.withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int info(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		UUID owner = player.getUUID();
		List<CompoundTag> active = MisiData.active(owner);
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.misi.uji.info", active.size(), MisiConfig.get().misiMaksAktif,
				MisiData.searchable(owner).size(), Misi.SERVICE.finished(owner)).withStyle(ChatFormatting.GOLD), false);
		for (CompoundTag mission : active) {
			TemplatMisi tpl = TemplatMisi.byId(mission.getStringOr("tpl", ""));
			StringBuilder chances = new StringBuilder();
			for (int stage = 0; stage < 3; stage++) {
				chances.append(stage == 0 ? "" : " / ").append(Math.round(JalanCerita.chance(owner, mission, stage).total() * 100.0D)).append('%');
			}
			String text = chances.toString();
			source.sendSuccess(() -> Component.translatable("kultivator.sekte.misi.uji.baris", mission.getIntOr("id", 0),
					tpl == null ? Component.literal("?") : Component.translatable(tpl.key()), mission.getIntOr("tier", 1),
					mission.getIntOr("done", 0), text, Component.translatable(mission.getBooleanOr("finished", false)
							? "kultivator.sekte.misi.uji.pulang_jalan" : "kultivator.sekte.misi.uji.berjalan")).withStyle(ChatFormatting.GRAY), false);
		}
		return active.size();
	}

	/** {@code /kultivasi perguruan misi}: the running missions with their time left. */
	private static int status(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		UUID owner = player.getUUID();
		long now = source.getServer().overworld().getGameTime();
		List<CompoundTag> active = MisiData.active(owner);
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.misi.status.judul", active.size(), MisiConfig.get().misiMaksAktif)
				.withStyle(ChatFormatting.GOLD), false);
		for (CompoundTag mission : active) {
			TemplatMisi tpl = TemplatMisi.byId(mission.getStringOr("tpl", ""));
			double days = Math.max(0L, mission.getLongOr("end", now) - now) / 24000.0D;
			source.sendSuccess(() -> Component.translatable("kultivator.sekte.misi.status.baris",
					tpl == null ? Component.literal("?") : Component.translatable(tpl.key()), mission.getIntOr("tier", 1),
					mission.getIntOr("done", 0), String.format(Locale.ROOT, "%.1f", days)).withStyle(ChatFormatting.AQUA), false);
		}
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.misi.status.petunjuk").withStyle(ChatFormatting.GRAY), false);
		return active.size();
	}

	private MisiCommands() {
	}
}
