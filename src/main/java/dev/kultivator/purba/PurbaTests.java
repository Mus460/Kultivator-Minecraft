package dev.kultivator.purba;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.kultivator.data.Cultivators;
import dev.kultivator.sect.event.SectEventItems;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The module's test commands (DESIGN 26.11): {@code uji purba <kunpeng|zhulong|taotie> [fase 1-3]}, {@code uji purba
 * celah|hapus|gejolak|enam|reset}, {@code uji bencana <k> [lulus|gagal]}, {@code uji bencana t <1.0-1.331>},
 * {@code uji bencana lewati <k>}, {@code uji murka [gelombang <1-3>|panglima]}, {@code uji murka menang|kalah|jadwal} and
 * {@code uji utusan <n> [k]}. Every feature without grinding.
 */
final class PurbaTests {

	static void add(LiteralArgumentBuilder<CommandSourceStack> uji) {
		LiteralArgumentBuilder<CommandSourceStack> purba = Commands.literal("purba")
				.then(Commands.literal("celah").executes(ctx -> celah(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("hapus").executes(ctx -> remove(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("gejolak").executes(ctx -> natural(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("enam").executes(ctx -> six(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource().getPlayerOrException())));
		for (int kind = 0; kind < Purba.KINDS.size(); kind++) {
			int chosen = kind;
			purba.then(Commands.literal(Purba.KINDS.get(kind))
					.executes(ctx -> spawn(ctx.getSource().getPlayerOrException(), chosen, 0))
					.then(Commands.literal("fase")
							.then(Commands.argument("fase", IntegerArgumentType.integer(1, 3))
									.executes(ctx -> spawn(ctx.getSource().getPlayerOrException(), chosen, IntegerArgumentType.getInteger(ctx, "fase") - 1)))));
		}
		uji.then(purba);
		uji.then(Commands.literal("bencana")
				.then(Commands.literal("t")
						.then(Commands.argument("nilai", DoubleArgumentType.doubleArg(1.0D, 1.331D))
								.executes(ctx -> setT(ctx, DoubleArgumentType.getDouble(ctx, "nilai")))))
				.then(Commands.literal("lewati")
						.then(Commands.argument("k", IntegerArgumentType.integer(1, StarCalamity.COUNT))
								.executes(ctx -> skip(ctx, IntegerArgumentType.getInteger(ctx, "k")))))
				.then(Commands.argument("k", IntegerArgumentType.integer(1, StarCalamity.COUNT))
						.executes(ctx -> calamity(ctx, IntegerArgumentType.getInteger(ctx, "k")))
						.then(Commands.literal("lulus").executes(ctx -> force(ctx, IntegerArgumentType.getInteger(ctx, "k"), true)))
						.then(Commands.literal("gagal").executes(ctx -> force(ctx, IntegerArgumentType.getInteger(ctx, "k"), false)))));
		uji.then(Commands.literal("murka")
				.executes(ctx -> wrath(ctx.getSource().getPlayerOrException()))
				.then(Commands.literal("gelombang")
						.then(Commands.argument("n", IntegerArgumentType.integer(1, HeavenlyWrath.WAVES.length))
								.executes(ctx -> wave(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "n")))))
				.then(Commands.literal("panglima").executes(ctx -> duel(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("menang").executes(ctx -> wrathForce(ctx.getSource().getPlayerOrException(), true)))
				.then(Commands.literal("kalah").executes(ctx -> wrathForce(ctx.getSource().getPlayerOrException(), false)))
				.then(Commands.literal("jadwal").executes(ctx -> schedule(ctx.getSource().getPlayerOrException()))));
		uji.then(Commands.literal("utusan")
				.then(Commands.argument("n", IntegerArgumentType.integer(1, EnvoyAi.MAX_ALIVE))
						.executes(ctx -> envoys(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "n"), 0))
						.then(Commands.argument("k", IntegerArgumentType.integer(0, StarCalamity.COUNT))
								.executes(ctx -> envoys(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "n"),
										IntegerArgumentType.getInteger(ctx, "k"))))));
	}

	// ================================================================== primordials

	private static int spawn(ServerPlayer player, int kind, int phase) {
		PrimordialEvents.Fight existing = PrimordialEvents.current;
		if (existing != null) {
			existing.depart(false);
		}
		PrimordialEvents.Fight fight = PrimordialEvents.startNow(player, kind, phase);
		if (fight == null || fight.beast == null) {
			return fail(player, "kultivator.purba.test.no_spawn");
		}
		Msg.chat(player, Component.translatable("kultivator.purba.test.spawned", PurbaItems.kindName(kind), phase + 1,
				Msg.number(fight.beast.getMaxHealth()), fight.realm).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int celah(ServerPlayer player) {
		PrimordialEvents.Fight fight = PrimordialEvents.current;
		if (fight == null || !fight.active) {
			return fail(player, "kultivator.purba.debug.none");
		}
		fight.openCelah(120);
		Msg.chat(player, Component.translatable("kultivator.purba.test.celah").withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int remove(ServerPlayer player) {
		PrimordialEvents.Fight fight = PrimordialEvents.current;
		if (fight == null) {
			return fail(player, "kultivator.purba.debug.none");
		}
		fight.depart(false);
		Msg.chat(player, Component.translatable("kultivator.purba.test.removed").withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private static int natural(ServerPlayer player) {
		if (PrimordialEvents.current != null) {
			return fail(player, "kultivator.purba.scroll.busy_world");
		}
		boolean ring = PrimordialEvents.solidRing(player.level(), player.position());
		if (PrimordialEvents.start(player, PrimordialEvents.leastKilled(player), true, null) == null) {
			return fail(player, "kultivator.purba.test.no_spawn");
		}
		Msg.chat(player, Component.translatable("kultivator.purba.test.natural", ring ? "+" : "-").withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int six(ServerPlayer player) {
		for (int piece = 1; piece <= 6; piece++) {
			Purba.give(player, SectEventItems.fragment(piece));
		}
		Msg.chat(player, Component.translatable("kultivator.purba.test.six").withStyle(ChatFormatting.GOLD));
		Purba.sync(player);
		return 1;
	}

	private static int reset(ServerPlayer player) {
		CompoundTag purba = Purba.section(Cultivators.get(player));
		purba.remove("cd");
		purba.remove("rep_day");
		purba.remove("rep_n");
		purba.remove("luka_until");
		purba.remove("langit_until");
		Cultivators.dirty(player);
		Msg.chat(player, Component.translatable("kultivator.purba.test.reset").withStyle(ChatFormatting.GOLD));
		Purba.sync(player);
		return 1;
	}

	// ================================================================== calamities

	private static int calamity(CommandContext<CommandSourceStack> ctx, int k) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		return StarCalamity.start(player, k, true) ? 1 : 0;
	}

	private static int force(CommandContext<CommandSourceStack> ctx, int k, boolean pass) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		StarCalamity.force(player, k, pass);
		return 1;
	}

	private static int skip(CommandContext<CommandSourceStack> ctx, int k) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		StarCalamity.skip(player, k);
		Msg.chat(player, Component.translatable("kultivator.purba.test.skipped", k).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int setT(CommandContext<CommandSourceStack> ctx, double t) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		StarCalamity.setT(player, t);
		Msg.chat(player, Component.translatable("kultivator.purba.test.t", String.format(java.util.Locale.ROOT, "%.3f",
				StarCalamity.trialFactor(Cultivators.get(player))), String.format(java.util.Locale.ROOT, "%.3f",
				TechniquePower.playerStarFactor(Cultivators.get(player)))).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	// ================================================================== wrath and envoys

	private static int wrath(ServerPlayer player) {
		HeavenlyWrath.abandon(player);
		return HeavenlyWrath.start(player, true) ? ok(player, "kultivator.purba.test.wrath") : fail(player, "kultivator.purba.test.no_spawn");
	}

	private static int wave(ServerPlayer player, int n) {
		return HeavenlyWrath.testWave(player, n) ? ok(player, "kultivator.purba.test.wrath") : fail(player, "kultivator.purba.test.no_spawn");
	}

	private static int duel(ServerPlayer player) {
		return HeavenlyWrath.testDuel(player) ? ok(player, "kultivator.purba.test.duel") : fail(player, "kultivator.purba.test.no_spawn");
	}

	private static int wrathForce(ServerPlayer player, boolean won) {
		HeavenlyWrath.testForce(player, won);
		return 1;
	}

	private static int schedule(ServerPlayer player) {
		CompoundTag info = HeavenlyWrath.info(player);
		Msg.chat(player, Component.translatable("kultivator.purba.test.schedule", info.getLongOr("today", 0L), info.getLongOr("next", -1L),
				info.getIntOr("skips", 0), info.getBooleanOr("eligible", false) ? "+" : "-", info.getBooleanOr("enabled", false) ? "+" : "-",
				info.getBooleanOr("live", false) ? "+" : "-").withStyle(ChatFormatting.AQUA));
		return 1;
	}

	private static int envoys(ServerPlayer player, int n, int k) {
		int spawned = EnvoyAi.spawnWave(player, n, k, cleared -> {
			if (player.isAlive()) {
				Msg.chat(player, Component.translatable(Boolean.TRUE.equals(cleared) ? "kultivator.purba.test.wave_cleared"
						: "kultivator.purba.test.wave_failed").withStyle(ChatFormatting.GRAY));
			}
		});
		Msg.chat(player, Component.translatable("kultivator.purba.test.envoys", spawned, n, k, Purba.realmEff(player)).withStyle(ChatFormatting.GOLD));
		return spawned;
	}

	private static int ok(ServerPlayer player, String key) {
		Msg.chat(player, Component.translatable(key).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int fail(ServerPlayer player, String key) {
		Msg.chat(player, Component.translatable(key).withStyle(ChatFormatting.RED));
		return 0;
	}

	private PurbaTests() {
	}
}
