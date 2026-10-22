package dev.kultivator.sekte.tangga;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sect.build.api.ComponentView;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.hall.Candidates;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteCommands;
import dev.kultivator.sekte.SekteIds;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Module G's commands (DESIGN 36.2.4, 36.3.4):
 * <ul>
 * <li>{@code /kultivasi uji perguruan tangga} (op): {@code mulai [n]} (a climb now, free, without the stair: n test
 * candidates are added when fewer are waiting), {@code proyeksi} (the projection alone, 20 s), {@code langkah <0-1000>
 * <nama>} (sets a disciple's steps and heart verdict), {@code ulang <nama>} (an Ujian Hati re-climb, free),
 * {@code henti} (settles the running climb), {@code info}.</li>
 * <li>{@code /kultivasi perguruan tangga} (everyone): the steps of the master's disciples; {@code ujian <nama>} asks one
 * for the Ujian Hati (PK, once per 30 days).</li>
 * </ul>
 */
final class TanggaCommands {

	static void register() {
		SekteCommands.test(SekteIds.G, TanggaCommands::testTree);
		SekteCommands.player(SekteIds.G, TanggaCommands::playerTree);
		SekteCommands.chips("perguruan tangga info", "perguruan tangga mulai [n]", "perguruan tangga proyeksi",
				"perguruan tangga langkah <0-1000> <nama>", "perguruan tangga ulang <nama>", "perguruan tangga henti");
	}

	private static LiteralArgumentBuilder<CommandSourceStack> testTree() {
		return Commands.literal(SekteIds.G)
				.executes(ctx -> info(ctx.getSource()))
				.then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
				.then(Commands.literal("mulai").executes(ctx -> start(ctx.getSource(), 0))
						.then(Commands.argument("n", IntegerArgumentType.integer(1, JalurTangga.LANES))
								.executes(ctx -> start(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n")))))
				.then(Commands.literal("proyeksi").executes(ctx -> preview(ctx.getSource())))
				.then(Commands.literal("langkah").then(Commands.argument("langkah", IntegerArgumentType.integer(0, JalurTangga.STEPS))
						.then(Commands.argument("nama", StringArgumentType.greedyString())
								.executes(ctx -> setSteps(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "langkah"),
										StringArgumentType.getString(ctx, "nama"))))))
				.then(Commands.literal("ulang").then(Commands.argument("nama", StringArgumentType.greedyString())
						.executes(ctx -> UjianHati.request(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "nama"), true))))
				.then(Commands.literal("henti").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					boolean stopped = Pendakian.stop(ctx.getSource().getServer(), player.getUUID());
					ctx.getSource().sendSuccess(() -> Component.translatable(stopped ? "kultivator.sekte.tangga.cmd.stopped"
							: "kultivator.sekte.tangga.cmd.none").withStyle(ChatFormatting.GRAY), false);
					return stopped ? 1 : 0;
				}));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> playerTree() {
		return Commands.literal(SekteIds.G)
				.executes(ctx -> list(ctx.getSource().getPlayerOrException()))
				.then(Commands.literal("ujian").then(Commands.argument("nama", StringArgumentType.greedyString())
						.executes(ctx -> UjianHati.request(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "nama"), false))));
	}

	/** {@code uji perguruan tangga info}: stair, component state, anchors, the running climb, the config. */
	private static int info(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		UUID owner = player.getUUID();
		Tangga.forgetStair(owner);
		EngineApi engine = EngineApi.get();
		int template = engine.template(owner);
		String state = "-";
		for (ComponentView view : engine.components(owner)) {
			if (BatuHatiKomponen.ID.equals(view.id())) {
				state = Integer.toString(view.state());
			}
		}
		boolean stair = Tangga.INSTANCE.hasStair(owner);
		BlockPos projector = SectBuild.anchor(owner, BatuHatiKomponen.ANCHOR_PROJECTOR);
		String stateText = state;
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.tangga.cmd.info", template, engine.tier(owner),
				BatuHatiKomponen.tierOf(template), stateText, Component.translatable(stair ? "kultivator.sekte.cmd.module.on"
						: "kultivator.sekte.cmd.module.off"), projector == null ? "-" : projector.toShortString()).withStyle(ChatFormatting.AQUA), false);
		ServerLevel level = Candidates.hallLevel(owner);
		BlockPos stele = Candidates.hallStele(owner);
		JalurTangga.Frame frame = level == null || stele == null ? null : Tangga.frame(owner, level, stele, player.position());
		if (frame != null) {
			source.sendSuccess(() -> Component.translatable("kultivator.sekte.tangga.cmd.frame", Math.round(frame.ox()), Math.round(frame.oy()),
					Math.round(frame.oz()), Math.round(frame.yaw())).withStyle(ChatFormatting.GRAY), false);
		}
		Pendakian climb = Pendakian.of(owner);
		TanggaConfig.Values c = TanggaConfig.get();
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.tangga.cmd.config", c.aktif ? "on" : "off", c.lod, c.ujianHatiPK,
				c.ujianHatiHari, c.detikPendakian, climb == null ? "-" : climb.ticks + "/" + climb.phase).withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	/** {@code uji perguruan tangga mulai [n]}: tops the waiting untested candidates up to n, then climbs them all, free. */
	private static int start(CommandSourceStack source, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		UUID owner = player.getUUID();
		ServerLevel level = Candidates.hallLevel(owner);
		if (level == null) {
			source.sendFailure(Component.translatable("kultivator.sekte.tangga.cmd.no_hall"));
			return 0;
		}
		List<Candidates.Climber> waiting = Candidates.climbers(owner, -1, JalurTangga.LANES);
		int want = n <= 0 ? Math.max(1, waiting.size()) : n;
		if (waiting.size() < want) {
			List<Halls.CandidateSpec> specs = new ArrayList<>();
			for (int i = waiting.size(); i < want; i++) {
				specs.add(new Halls.CandidateSpec(DiscipleData.fresh(level.getRandom()), -1, -1.0D, -1.0D, false, "invite"));
			}
			Halls.addCandidates(owner, specs);
			waiting = Candidates.climbers(owner, -1, JalurTangga.LANES);
		}
		if (waiting.isEmpty()) {
			source.sendFailure(Component.translatable("kultivator.sekte.tangga.cmd.no_candidates"));
			return 0;
		}
		boolean started = Pendakian.beginTrial(player, owner, waiting.get(0).id(), false, true);
		if (!started) {
			source.sendFailure(Component.translatable("kultivator.sekte.tangga.cannot"));
		}
		return started ? 1 : 0;
	}

	private static int preview(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		boolean ok = Pendakian.beginPreview(player, player.getUUID());
		if (!ok) {
			source.sendFailure(Component.translatable("kultivator.sekte.tangga.cannot"));
		}
		return ok ? 1 : 0;
	}

	private static int setSteps(CommandSourceStack source, int steps, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		DiscipleEntity disciple = UjianHati.find(player.getUUID(), name);
		if (disciple == null) {
			source.sendFailure(Component.translatable("kultivator.sekte.tangga.no_disciple", name));
			return 0;
		}
		int tekad = dev.kultivator.sect.hall.Talents.tekad(disciple.data());
		int heart = Pendakian.heartVerdict(steps, tekad);
		Pendakian.write(disciple.data(), steps, heart, Ingatan.today(source.getServer()), false);
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.tangga.cmd.set", disciple.getDisplayName(), steps,
				Component.translatable("kultivator.sekte.tangga.hati." + heart)).withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	/** {@code /kultivasi perguruan tangga}: every loaded disciple's climb. */
	private static int list(ServerPlayer player) {
		UUID owner = player.getUUID();
		player.sendSystemMessage(Component.translatable("kultivator.sekte.tangga.list.title",
				Component.translatable(Tangga.INSTANCE.hasStair(owner) ? "kultivator.sekte.tangga.list.stair" : "kultivator.sekte.tangga.list.no_stair"))
				.withStyle(ChatFormatting.GOLD));
		int shown = 0;
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (disciple.role() != DiscipleEntity.Role.DISCIPLE) {
				continue;
			}
			CompoundTag mine = disciple.data().extra().getCompound(SekteIds.EXTRA_G).orElse(null);
			if (mine == null || !mine.contains(Tangga.K_STEPS)) {
				player.sendSystemMessage(Component.translatable("kultivator.sekte.tangga.list.none", disciple.getDisplayName()).withStyle(ChatFormatting.GRAY));
			} else {
				int steps = mine.getIntOr(Tangga.K_STEPS, 0);
				int heart = mine.getIntOr(Tangga.K_VERDICT, 0);
				player.sendSystemMessage(Component.translatable("kultivator.sekte.tangga.list.line", disciple.getDisplayName(), steps,
						mine.getIntOr(Tangga.K_BEST, steps), Component.translatable("kultivator.sekte.tangga.hati." + heart))
						.withStyle(mine.getBooleanOr(Tangga.K_PEAK, false) ? ChatFormatting.GOLD : ChatFormatting.AQUA));
			}
			shown++;
		}
		if (shown == 0) {
			player.sendSystemMessage(Component.translatable("kultivator.sekte.tangga.list.empty").withStyle(ChatFormatting.GRAY));
		}
		player.sendSystemMessage(Component.translatable("kultivator.sekte.tangga.list.hint", TanggaConfig.get().ujianHatiPK,
				TanggaConfig.get().ujianHatiHari).withStyle(ChatFormatting.DARK_GRAY));
		return 1;
	}

	/** R23: the same records as {@link #list}, as data for the hub scroll. */
	static CompoundTag records(ServerPlayer player) {
		UUID owner = player.getUUID();
		CompoundTag out = new CompoundTag();
		out.putBoolean("stair", Tangga.INSTANCE.hasStair(owner));
		out.putInt("pk", TanggaConfig.get().ujianHatiPK);
		out.putInt("hari", TanggaConfig.get().ujianHatiHari);
		net.minecraft.nbt.ListTag rows = new net.minecraft.nbt.ListTag();
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (disciple.role() != DiscipleEntity.Role.DISCIPLE || rows.size() >= 64) {
				continue;
			}
			CompoundTag row = new CompoundTag();
			row.putString("name", disciple.getDisplayName().getString());
			CompoundTag mine = disciple.data().extra().getCompound(SekteIds.EXTRA_G).orElse(null);
			if (mine == null || !mine.contains(Tangga.K_STEPS)) {
				row.putInt("steps", -1);
			} else {
				int steps = mine.getIntOr(Tangga.K_STEPS, 0);
				row.putInt("steps", steps);
				row.putInt("best", mine.getIntOr(Tangga.K_BEST, steps));
				row.putInt("heart", Math.max(0, Math.min(3, mine.getIntOr(Tangga.K_VERDICT, 0))));
				row.putBoolean("peak", mine.getBooleanOr(Tangga.K_PEAK, false));
			}
			rows.add(row);
		}
		out.put("rows", rows);
		return out;
	}

	private TanggaCommands() {
	}
}
