package dev.kultivator.sekte.turnamen;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.event.Tournament;
import dev.kultivator.sekte.SekteCommands;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.AksiSekte;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Module T's commands (DESIGN 36.2.3, 36.3.4).
 * <ul>
 * <li>{@code /kultivasi uji perguruan turnamen} (op): {@code mulai [biasa|elit]}, {@code mulai antar <sekte>},
 * {@code babak lewati}, {@code penonton <n>}, {@code elo <nama> <n>}, {@code musim} and {@code musim reset}, {@code final}
 * and {@code juara} (the scenes only), {@code tribun} (venue, seats, flags), {@code tantang <sekte>} (an NPC sect's
 * challenge as module D would send it).</li>
 * <li>{@code /kultivasi perguruan turnamen} (everyone): the season board and the roll of champions; {@code terima} and
 * {@code tolak} answer a waiting inter-sect challenge.</li>
 * </ul>
 */
final class TurnamenCommands {

	static void init() {
		SekteCommands.test(SekteIds.T, TurnamenCommands::testNode);
		SekteCommands.player(SekteIds.T, TurnamenCommands::playerNode);
		SekteCommands.chips("perguruan turnamen mulai", "perguruan turnamen mulai elit", "perguruan turnamen mulai antar uji",
				"perguruan turnamen babak lewati", "perguruan turnamen penonton 6", "perguruan turnamen musim", "perguruan turnamen final",
				"perguruan turnamen juara", "perguruan turnamen tribun", "perguruan turnamen tantang uji");
	}

	// ================================================================== test

	private static LiteralArgumentBuilder<CommandSourceStack> testNode() {
		return Commands.literal(SekteIds.T)
				.executes(ctx -> season(ctx.getSource()))
				.then(Commands.literal("mulai")
						.executes(ctx -> start(ctx, null, null))
						.then(Commands.literal("biasa").executes(ctx -> start(ctx, Tournament.KIND_NORMAL, null)))
						.then(Commands.literal("elit").executes(ctx -> start(ctx, Tournament.KIND_ELITE, null)))
						.then(Commands.literal("antar")
								.then(Commands.argument("sekte", StringArgumentType.word())
										.executes(ctx -> start(ctx, Tournament.KIND_GUESTS, StringArgumentType.getString(ctx, "sekte"))))))
				.then(Commands.literal("babak").then(Commands.literal("lewati").executes(ctx -> skip(ctx.getSource()))))
				.then(Commands.literal("penonton")
						.then(Commands.argument("n", IntegerArgumentType.integer(1, 12))
								.executes(ctx -> spectators(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n")))))
				.then(Commands.literal("elo")
						.then(Commands.argument("nama", StringArgumentType.string())
								.then(Commands.argument("n", IntegerArgumentType.integer(Elo.FLOOR, 3000))
										.executes(ctx -> setElo(ctx.getSource(), StringArgumentType.getString(ctx, "nama"),
												IntegerArgumentType.getInteger(ctx, "n"))))))
				.then(Commands.literal("musim")
						.executes(ctx -> season(ctx.getSource()))
						.then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource()))))
				.then(Commands.literal("final").executes(ctx -> scene(ctx.getSource(), false)))
				.then(Commands.literal("juara").executes(ctx -> scene(ctx.getSource(), true)))
				.then(Commands.literal("tribun").executes(ctx -> tribune(ctx.getSource())))
				.then(Commands.literal("tantang")
						.then(Commands.argument("sekte", StringArgumentType.word())
								.executes(ctx -> challenge(ctx.getSource(), StringArgumentType.getString(ctx, "sekte")))));
	}

	private static int start(CommandContext<CommandSourceStack> ctx, String kind, String sect) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		boolean ok = Tournament.KIND_GUESTS.equals(kind) ? AntarSekte.host(player, sect, true)
				: Tournament.startNow(player, kind, null, null, 0, true);
		if (ok) {
			ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.turnamen.uji.mulai",
					Tournament.runningKind(player.getUUID()) == null ? "-" : Tournament.runningKind(player.getUUID())), false);
		}
		return ok ? 1 : 0;
	}

	private static int skip(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		boolean ok = Tournament.skipBout(player.getUUID());
		source.sendSuccess(() -> Component.translatable(ok ? "kultivator.sekte.turnamen.uji.lewati" : "kultivator.sekte.turnamen.uji.tidak_jalan"),
				false);
		return ok ? 1 : 0;
	}

	private static int spectators(CommandSourceStack source, int n) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		int made = Kerumunan.spawnTest(player.getUUID(), n);
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.turnamen.uji.penonton", made, Turnamen.crowd(player.getUUID())), false);
		return made;
	}

	private static int setElo(CommandSourceStack source, String name, int value) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		String wanted = name.toLowerCase(Locale.ROOT);
		for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
			if (disciple.getName().getString().toLowerCase(Locale.ROOT).contains(wanted)) {
				Elo.setRating(disciple.data(), value);
				Turnamen.board(player.getUUID(), disciple);
				source.sendSuccess(() -> Component.translatable("kultivator.sekte.turnamen.uji.elo", disciple.getName(), value), false);
				return 1;
			}
		}
		source.sendFailure(Component.translatable("kultivator.sekte.turnamen.uji.tak_ketemu", name));
		return 0;
	}

	private static int season(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		summary(player);
		return 1;
	}

	private static int reset(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Musim.reset(player.getUUID());
		for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
			Elo.reset(disciple.data());
		}
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.turnamen.uji.reset"), false);
		return 1;
	}

	private static int scene(CommandSourceStack source, boolean champion) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Turnamen.testScenes(player, champion);
		return 1;
	}

	private static int tribune(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		UUID owner = player.getUUID();
		ServerLevel level = (ServerLevel) player.level();
		BlockPos centre = EngineApi.get().anchor(owner, ArenaTempat.ANCHOR_CENTRE);
		var venue = ArenaTempat.venue(owner, level, player.position());
		int seats = venue == null ? 0 : ArenaTempat.seats(owner, level, venue).size();
		int flags = ArenaTempat.flags(owner).size();
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.turnamen.uji.tribun", EngineApi.get().template(owner),
				centre == null ? "-" : centre.toShortString(), seats, flags, TurnamenConfig.get().tribun ? "on" : "off"), false);
		return 1;
	}

	private static int challenge(CommandSourceStack source, String sect) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		// As module D would: the bus event (so every listener sees it), or directly while D is missing.
		SekteBus.rivalAction(source.getServer(), player.getUUID(), sect, AksiSekte.TANTANGAN, 3);
		if (Musim.pending(player.getUUID()) == null) {
			AntarSekte.challenge(source.getServer(), player.getUUID(), sect);
		}
		return 1;
	}

	// ================================================================== player

	private static LiteralArgumentBuilder<CommandSourceStack> playerNode() {
		return Commands.literal(SekteIds.T)
				.executes(ctx -> {
					summary(ctx.getSource().getPlayerOrException());
					return 1;
				})
				.then(Commands.literal("papan").executes(ctx -> {
					summary(ctx.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("terima").executes(ctx -> AntarSekte.accept(ctx.getSource().getPlayerOrException()) ? 1 : 0))
				.then(Commands.literal("tolak").executes(ctx -> AntarSekte.refuse(ctx.getSource().getPlayerOrException()) ? 1 : 0));
	}

	/** The season board and the roll of champions in chat. */
	static void summary(ServerPlayer player) {
		UUID owner = player.getUUID();
		Msg.chat(player, Component.translatable("kultivator.sekte.turnamen.papan.judul", Musim.season(owner)).withStyle(ChatFormatting.GOLD,
				ChatFormatting.BOLD));
		List<Musim.Baris> board = Musim.board(owner);
		if (board.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.sekte.turnamen.papan.kosong").withStyle(ChatFormatting.GRAY));
		}
		for (int i = 0; i < Math.min(10, board.size()); i++) {
			Musim.Baris row = board.get(i);
			MutableComponent line = Component.literal(String.format(Locale.ROOT, "%2d. ", i + 1)).withStyle(i == 0 ? ChatFormatting.GOLD
					: ChatFormatting.YELLOW);
			line.append(row.name().copy().withStyle(ChatFormatting.WHITE));
			line.append(Component.translatable("kultivator.sekte.turnamen.papan.baris", row.elo(), row.wins(), row.losses())
					.withStyle(ChatFormatting.GRAY));
			Msg.chat(player, line);
		}
		List<Musim.Juara> roll = Musim.champions(owner);
		if (!roll.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.sekte.turnamen.papan.juara").withStyle(ChatFormatting.GOLD));
			for (int i = 0; i < Math.min(5, roll.size()); i++) {
				Musim.Juara each = roll.get(i);
				Msg.chat(player, Component.translatable("kultivator.sekte.turnamen.papan.juara_baris", each.season(),
						Component.translatable("kultivator.sekte.turnamen.jenis." + each.kind()), each.name()).withStyle(ChatFormatting.GRAY));
			}
		}
		int won = Musim.interSectWins(owner);
		int lost = Musim.interSectLosses(owner);
		if (won + lost > 0) {
			Msg.chat(player, Component.translatable("kultivator.sekte.turnamen.papan.antar", won, lost).withStyle(ChatFormatting.AQUA));
		}
		String pending = Musim.pending(owner);
		if (pending != null) {
			Msg.chat(player, Component.translatable("kultivator.sekte.turnamen.papan.menunggu", AntarSekte.sectName(pending))
					.withStyle(ChatFormatting.GOLD));
		}
	}

	/** The hub card's data ({@link TurnamenPayloads.Papan}). */
	static CompoundTag boardData(ServerPlayer player) {
		UUID owner = player.getUUID();
		CompoundTag tag = new CompoundTag();
		tag.putInt("season", Musim.season(owner));
		ListTag board = new ListTag();
		for (Musim.Baris row : Musim.board(owner)) {
			if (board.size() >= 10) {
				break;
			}
			CompoundTag entry = new CompoundTag();
			entry.store("n", ComponentSerialization.CODEC, row.name());
			entry.putInt("e", row.elo());
			entry.putInt("w", row.wins());
			entry.putInt("l", row.losses());
			board.add(entry);
		}
		tag.put("board", board);
		ListTag roll = new ListTag();
		for (Musim.Juara each : Musim.champions(owner)) {
			if (roll.size() >= 8) {
				break;
			}
			CompoundTag entry = new CompoundTag();
			entry.putInt("s", each.season());
			entry.putString("k", each.kind());
			entry.store("n", ComponentSerialization.CODEC, each.name());
			roll.add(entry);
		}
		tag.put("roll", roll);
		tag.putInt("antar_w", Musim.interSectWins(owner));
		tag.putInt("antar_l", Musim.interSectLosses(owner));
		String pending = Musim.pending(owner);
		if (pending != null) {
			tag.store("pending", ComponentSerialization.CODEC, AntarSekte.sectName(pending));
		}
		tag.putBoolean("tribun", ArenaTempat.hasTribune(owner));
		tag.putBoolean("crowd_live", Turnamen.live(owner));
		tag.putInt("crowd", Turnamen.crowd(owner));
		tag.put("live", Tournament.view(owner));
		return tag;
	}

	private TurnamenCommands() {
	}
}
