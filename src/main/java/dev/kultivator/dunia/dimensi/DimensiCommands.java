package dev.kultivator.dunia.dimensi;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.dimensi.bawah.ArwahEntity;
import dev.kultivator.dunia.dimensi.bawah.BawahPeople;
import dev.kultivator.dunia.dimensi.bawah.Courts;
import dev.kultivator.dunia.dimensi.bawah.SoulJourney;
import dev.kultivator.dunia.dimensi.laut.KepitingBatuEntity;
import dev.kultivator.dunia.dimensi.laut.SeaGate;
import dev.kultivator.dunia.dimensi.laut.SeaMonsters;
import dev.kultivator.dunia.dimensi.laut.UlarLautEntity;
import dev.kultivator.dunia.dimensi.laut.YakshaEntity;
import dev.kultivator.data.Cultivators;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * {@code /kultivasi uji bawah ...} and {@code uji laut ...} (DESIGN 34.5.13-14 "Uji"), op only, registered through the
 * spine's {@link DuniaCommands#test}. Nothing here places a structure (rule W6): {@code ... cari} finds the nearest one
 * (vanilla locate) and teleports there.
 */
public final class DimensiCommands {

	private static final SuggestionProvider<CommandSourceStack> SOUPS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(List.of("dao", "hati", "teknik", "tolak"), builder);
	private static final SuggestionProvider<CommandSourceStack> ISLANDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(List.of("herba", "pertapa", "pasar", "karang"), builder);
	private static final SuggestionProvider<CommandSourceStack> MONSTERS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(List.of("ular", "kepiting", "yaksha"), builder);

	static void init() {
		DuniaCommands.test("bawah", DimensiCommands::bawah);
		DuniaCommands.test("laut", DimensiCommands::laut);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> bawah() {
		return Commands.literal("bawah")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("masuk").executes(ctx -> ok(ctx, SoulJourney.enter(player(ctx), true), "masuk")))
				.then(Commands.literal("pulang").executes(DimensiCommands::bawahPulang))
				.then(Commands.literal("meng_po").then(Commands.argument("pilihan", StringArgumentType.word()).suggests(SOUPS)
						.executes(ctx -> {
							BawahPeople.soup(player(ctx), null, StringArgumentType.getString(ctx, "pilihan"), true);
							return ok(ctx, true, "meng_po");
						})))
				.then(Commands.literal("pengadilan").then(Commands.argument("n", IntegerArgumentType.integer(1, 10))
						.executes(ctx -> court(ctx, false))
						.then(Commands.literal("lulus").executes(ctx -> court(ctx, true)))))
				.then(Commands.literal("cermin").executes(ctx -> {
					ServerPlayer player = player(ctx);
					DimensiNet.open(player, DimensiNet.SCREEN_COURT, -1, Courts.courtTag(player, 1));
					return 1;
				}))
				.then(Commands.literal("buku").executes(ctx -> {
					ServerPlayer player = player(ctx);
					Courts.setPassed(player, 10);
					BawahPeople.openBook(player, player.getId());
					return 1;
				}))
				.then(Commands.literal("coret").executes(ctx -> {
					BawahPeople.book(player(ctx), "coret", true);
					return ok(ctx, true, "coret");
				}))
				.then(Commands.literal("arwah").then(Commands.argument("n", IntegerArgumentType.integer(1, 12))
						.executes(DimensiCommands::ghosts)))
				.then(Commands.literal("kuil").then(Commands.literal("cari").executes(ctx ->
						find(ctx, "bawah/kuil_gerbang_hantu", Level.OVERWORLD))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> laut() {
		return Commands.literal("laut")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("masuk").executes(ctx -> ok(ctx, SeaGate.enter(player(ctx), true), "laut_masuk")))
				.then(Commands.literal("pulang").executes(ctx -> {
					SeaGate.leave(player(ctx));
					return 1;
				}))
				.then(Commands.literal("pulau").then(Commands.literal("cari")
						.executes(ctx -> findAtSea(ctx, "laut/pulau_roh"))
						.then(Commands.argument("jenis", StringArgumentType.word()).suggests(ISLANDS)
								.executes(ctx -> findAtSea(ctx, "laut/pulau_roh")))))
				.then(Commands.literal("istana").then(Commands.literal("cari").executes(ctx -> findAtSea(ctx, "laut/istana_naga"))))
				.then(Commands.literal("penglai").executes(ctx -> findAtSea(ctx, "laut/pulau_penglai")))
				.then(Commands.literal("pusaran").then(Commands.literal("cari").executes(ctx ->
						find(ctx, "laut/pusaran_laut", Level.OVERWORLD))))
				.then(Commands.literal("monster").then(Commands.argument("jenis", StringArgumentType.word()).suggests(MONSTERS)
						.executes(ctx -> monster(ctx, -1))
						.then(Commands.argument("tingkat", IntegerArgumentType.integer(0, 4))
								.executes(ctx -> monster(ctx, IntegerArgumentType.getInteger(ctx, "tingkat"))))))
				.then(Commands.literal("penyu").executes(DimensiCommands::turtle));
	}

	// ================================================================== bodies

	private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	private static int ok(CommandContext<CommandSourceStack> ctx, boolean done, String what) {
		if (done) {
			ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.dimensi.uji." + what), false);
			return 1;
		}
		return 0;
	}

	private static int bawahPulang(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		if (SoulJourney.isSoul(player)) {
			SoulJourney.returnToBody(player, SoulJourney.BY_COMMAND);
		} else if (DimensiLevels.isBawah(player.level())) {
			DimensiLevels.sendHome(player, null);
		}
		return 1;
	}

	private static int court(CommandContext<CommandSourceStack> ctx, boolean pass) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		int n = IntegerArgumentType.getInteger(ctx, "n");
		DimensiData.extra(player, DimensiData.BAWAH).putBoolean("mengpo", true);
		Courts.setPassed(player, n - 1);
		if (pass) {
			Courts.pass(player, n, null);
		} else {
			DimensiNet.open(player, DimensiNet.SCREEN_COURT, -1, Courts.courtTag(player, n));
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.dimensi.uji.pengadilan", n, Courts.passed(player)), false);
		return 1;
	}

	private static int ghosts(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		int n = IntegerArgumentType.getInteger(ctx, "n");
		ServerLevel level = player.level();
		int realm = Cultivators.get(player).realm().ordinal();
		int made = 0;
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / n;
			Vec3 at = player.position().add(Math.cos(angle) * 6.0D, 0.0D, Math.sin(angle) * 6.0D);
			ArwahEntity ghost = DimensiEntities.ARWAH.create(level, EntitySpawnReason.COMMAND);
			if (ghost == null) {
				continue;
			}
			ghost.setup(ArwahEntity.HANTU_LAPAR, level.getRandom().nextInt(9), 0, realm);
			ghost.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
			ghost.setHome(ghost.blockPosition(), 14);
			if (level.addFreshEntity(ghost)) {
				DuniaBudget.track(DuniaBudget.NPC, ghost);
				made++;
			}
		}
		int count = made;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.dimensi.uji.arwah", count), false);
		return count;
	}

	private static int find(CommandContext<CommandSourceStack> ctx, String id, net.minecraft.resources.ResourceKey<Level> dim)
			throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		if (player.level().dimension() != dim) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.dimensi.uji.dunia_salah"));
			return 0;
		}
		return DuniaCommands.findAndTeleport(player, Kultivator.id(id));
	}

	/** Finds a structure of the sea: crosses into the sea first when needed, then searches a moment later. */
	private static int findAtSea(CommandContext<CommandSourceStack> ctx, String id) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		Identifier structure = Kultivator.id(id);
		if (!DimensiLevels.isLaut(player.level())) {
			if (!SeaGate.enter(player, true)) {
				return 0;
			}
			java.util.UUID who = player.getUUID();
			Dimensi.schedule(40, () -> {
				ServerPlayer now = player.level().getServer().getPlayerList().getPlayer(who);
				if (now != null && DimensiLevels.isLaut(now.level())) {
					DuniaCommands.findAndTeleport(now, structure);
				}
			});
			return 1;
		}
		return DuniaCommands.findAndTeleport(player, structure);
	}

	private static int monster(CommandContext<CommandSourceStack> ctx, int tier) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		ServerLevel level = player.level();
		String kind = StringArgumentType.getString(ctx, "jenis");
		int t = tier < 0 ? SeaMonsters.tierAt(player.position()) : tier;
		Vec3 at = player.position().add(player.getLookAngle().multiply(6.0D, 0.0D, 6.0D));
		Mob mob;
		switch (kind) {
			case "ular" -> {
				UlarLautEntity serpent = DimensiEntities.ULAR_LAUT.create(level, EntitySpawnReason.COMMAND);
				if (serpent == null) {
					return 0;
				}
				serpent.setup(t, false);
				mob = serpent;
			}
			case "kepiting" -> {
				KepitingBatuEntity crab = DimensiEntities.KEPITING_BATU.create(level, EntitySpawnReason.COMMAND);
				if (crab == null) {
					return 0;
				}
				crab.setup(t);
				mob = crab;
			}
			case "yaksha" -> {
				YakshaEntity yaksha = DimensiEntities.YAKSHA.create(level, EntitySpawnReason.COMMAND);
				if (yaksha == null) {
					return 0;
				}
				yaksha.setup(YakshaEntity.LAUT, t, false);
				mob = yaksha;
			}
			default -> {
				ctx.getSource().sendFailure(Component.translatable("kultivator.dimensi.uji.monster_tak_dikenal", kind));
				return 0;
			}
		}
		mob.snapTo(at.x, at.y, at.z, player.getYRot() + 180.0F, 0.0F);
		if (!level.addFreshEntity(mob)) {
			return 0;
		}
		DuniaBudget.track(SeaMonsters.MONSTER, mob);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.dimensi.uji.monster", kind, t), false);
		return 1;
	}

	private static int turtle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		if (!DimensiLevels.isLaut(player.level())) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.dimensi.uji.dunia_salah"));
			return 0;
		}
		var turtle = SeaMonsters.spawnTurtle(player.level(), player);
		if (turtle == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.dimensi.uji.penyu_gagal"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.dimensi.uji.penyu", (int) turtle.getX(), (int) turtle.getZ()), false);
		return 1;
	}

	private DimensiCommands() {
	}
}
