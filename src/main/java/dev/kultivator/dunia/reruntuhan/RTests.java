package dev.kultivator.dunia.reruntuhan;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.reruntuhan.gua.FreeHost;
import dev.kultivator.dunia.reruntuhan.gua.GateRun;
import dev.kultivator.dunia.reruntuhan.gua.Gua;
import dev.kultivator.dunia.reruntuhan.gua.GuardianBrain;
import dev.kultivator.dunia.reruntuhan.gua.GuardianRun;
import dev.kultivator.dunia.reruntuhan.gua.HallRun;
import dev.kultivator.dunia.reruntuhan.gua.Puzzle;
import dev.kultivator.dunia.reruntuhan.gua.RoomRun;
import dev.kultivator.dunia.reruntuhan.gua.RuinLooks;
import dev.kultivator.dunia.reruntuhan.menara.MenaraTests;
import dev.kultivator.dunia.reruntuhan.penjara.CoreRun;
import dev.kultivator.dunia.reruntuhan.penjara.Prisons;
import dev.kultivator.dunia.reruntuhan.penjara.RajaIblisEntity;
import dev.kultivator.dunia.reruntuhan.penjara.ShrineRun;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The test commands of module R (DESIGN 34.5.5-34.5.7 "Uji"), op only, under {@code /kultivasi uji gua|penjara|menara}
 * through {@code DuniaCommands.test}. They find structures (vanilla locate and a teleport, never {@code /place}),
 * start puzzles and fights out of entities only, and set the module's saved numbers; none of them changes a block.
 */
final class RTests {

	private static final List<String> TIERS = List.of("kecil", "agung", "kuno");
	private static final SuggestionProvider<CommandSourceStack> TIER = (ctx, b) -> SharedSuggestionProvider.suggest(TIERS, b);
	private static final SuggestionProvider<CommandSourceStack> KINDS = (ctx, b) -> SharedSuggestionProvider.suggest(
			Arrays.stream(Puzzle.Kind.values()).map(Puzzle.Kind::id), b);
	private static final SuggestionProvider<CommandSourceStack> KINGS = (ctx, b) -> SharedSuggestionProvider.suggest(RajaIblisEntity.IDS, b);

	static void init() {
		DuniaCommands.test("gua", RTests::gua);
		DuniaCommands.test("penjara", RTests::penjara);
		DuniaCommands.test("menara", MenaraTests::node);
	}

	private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	private static int say(ServerPlayer player, String key, Object... args) {
		Msg.chat(player, Component.translatable(key, args).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	// ================================================================== gua

	private static LiteralArgumentBuilder<CommandSourceStack> gua() {
		return Commands.literal("gua").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("cari").then(Commands.argument("tingkat", StringArgumentType.word()).suggests(TIER)
						.executes(ctx -> {
							String tier = StringArgumentType.getString(ctx, "tingkat").toLowerCase(Locale.ROOT);
							if (!TIERS.contains(tier)) {
								return say(player(ctx), "kultivator.reruntuhan.uji.unknown", tier);
							}
							return DuniaCommands.findAndTeleport(player(ctx), Kultivator.id("reruntuhan/gua_" + tier));
						})))
				.then(Commands.literal("teka").then(Commands.argument("jenis", StringArgumentType.word()).suggests(KINDS)
						.executes(ctx -> puzzle(player(ctx), StringArgumentType.getString(ctx, "jenis")))))
				.then(Commands.literal("selesai").executes(ctx -> solve(player(ctx))))
				.then(Commands.literal("penjaga").executes(ctx -> guardian(player(ctx))))
				.then(Commands.literal("reset").executes(ctx -> reset(player(ctx))));
	}

	/** {@code uji gua teka <jenis>}: a puzzle of entities only, seven blocks ahead (needs a free area of about ten blocks). */
	private static int puzzle(ServerPlayer player, String id) {
		Puzzle.Kind kind = Puzzle.Kind.byId(id.toLowerCase(Locale.ROOT));
		if (kind == null) {
			return say(player, "kultivator.reruntuhan.uji.unknown", id);
		}
		int quarter = Math.floorMod(Math.round(player.getYRot() / 90.0F) + 2, 4);
		Frame look = new Frame(player.blockPosition(), quarter);
		Frame frame = new Frame(look.block(0, 0, -7), quarter);
		FreeHost.start(player.level(), frame, kind, 1, player.getRandom().nextLong(), false, solved -> {
		});
		return say(player, "kultivator.reruntuhan.uji.teka", Component.translatable("kultivator.reruntuhan.teka." + kind.id()));
	}

	private static int solve(ServerPlayer player) {
		RoomRun room = Gua.nearest(player, RoomRun.class, 14.0D);
		if (room != null) {
			room.testSolve();
			return say(player, "kultivator.reruntuhan.uji.done");
		}
		FreeHost free = FreeHost.nearest(player, 16.0D);
		if (free != null) {
			free.puzzle().forceSolve();
			return say(player, "kultivator.reruntuhan.uji.done");
		}
		GateRun gate = Gua.nearest(player, GateRun.class, 12.0D);
		if (gate != null) {
			gate.openNow(player);
			return say(player, "kultivator.reruntuhan.uji.done");
		}
		GuardianRun guardian = Gua.nearest(player, GuardianRun.class, 24.0D);
		if (guardian != null) {
			guardian.testDefeat(player);
			return say(player, "kultivator.reruntuhan.uji.done");
		}
		HallRun hall = Gua.nearest(player, HallRun.class, 16.0D);
		if (hall != null) {
			hall.grant(player);
			return 1;
		}
		return say(player, "kultivator.reruntuhan.uji.none");
	}

	/** {@code uji gua penjaga}: wakes the nearest ruin's guardian, or a test guardian in front of the player. */
	private static int guardian(ServerPlayer player) {
		GuardianRun run = Gua.nearest(player, GuardianRun.class, 32.0D);
		if (run != null) {
			run.testWake(player);
			return say(player, "kultivator.reruntuhan.uji.done");
		}
		ServerLevel level = player.level();
		Vec3 at = player.position().add(player.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize().scale(6.0D));
		dev.kultivator.dunia.reruntuhan.gua.BonekaBatuEntity golem = REntities.BONEKA_BATU.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
		if (golem == null) {
			return 0;
		}
		golem.snapTo(at.x, at.y, at.z, player.getYRot() + 180.0F, 0.0F);
		golem.setKind(0);
		golem.setTier(1);
		golem.setBase(Attributes.MAX_HEALTH, RuinLooks.guardianHealth(1));
		golem.setBase(Attributes.ATTACK_DAMAGE, RuinLooks.guardianAttack(1));
		golem.setHealth(golem.getMaxHealth());
		golem.setIntakeCap(0.06D);
		golem.setFlag(RCreature.FLAG_RISING, true);
		golem.setSkill(dev.kultivator.dunia.reruntuhan.gua.BonekaBatuEntity.RISE);
		golem.setBrain(new GuardianBrain(at, 12.0D, 1, (c, s) -> RFx.at(level, c.position(), RFx.GUARDIAN_DOWN, 0xC8A060, 1, 2.0F), (c, v) -> {
		}));
		level.addFreshEntity(golem);
		RFx.at(level, at, RFx.GUARDIAN_RISE, 1, 0, 3.0F);
		return say(player, "kultivator.reruntuhan.uji.done");
	}

	private static int reset(ServerPlayer player) {
		int n = 0;
		for (RoomRun run : Runs.all(RoomRun.class)) {
			if (run.level == player.level() && run.anchor.position().distanceToSqr(player.position()) < 128.0D * 128.0D) {
				run.testReset();
				n++;
			}
		}
		for (GateRun run : Runs.all(GateRun.class)) {
			if (run.level == player.level() && run.anchor.position().distanceToSqr(player.position()) < 128.0D * 128.0D) {
				run.testReset();
				n++;
			}
		}
		for (GuardianRun run : Runs.all(GuardianRun.class)) {
			if (run.level == player.level() && run.anchor.position().distanceToSqr(player.position()) < 128.0D * 128.0D) {
				run.testReset();
				n++;
			}
		}
		for (HallRun run : Runs.all(HallRun.class)) {
			if (run.level == player.level() && run.anchor.position().distanceToSqr(player.position()) < 128.0D * 128.0D) {
				run.testReset();
				n++;
			}
		}
		return say(player, "kultivator.reruntuhan.uji.reset", n);
	}

	// ================================================================== penjara

	private static LiteralArgumentBuilder<CommandSourceStack> penjara() {
		return Commands.literal("penjara").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> info(player(ctx)))
				.then(Commands.literal("cari").executes(ctx -> DuniaCommands.findAndTeleport(player(ctx), Kultivator.id("penjara/penjara_iblis")))
						// the badlands prison (red sandstone, acacia, mud brick)
						.then(Commands.literal("gurun").executes(ctx -> DuniaCommands.findAndTeleport(player(ctx), Kultivator.id("penjara/penjara_iblis_gurun")))))
				// a local stand-in for a Gerhana Darah over this dimension: only the prisons' own reaction (restless demons,
				// faster seal decay, the eclipse achievement); the sky itself stays module L's
				.then(Commands.literal("gerhana").then(Commands.argument("nyala", com.mojang.brigadier.arguments.BoolArgumentType.bool())
						.executes(ctx -> {
							boolean on = com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "nyala");
							Prisons.testEclipse(player(ctx).level(), on);
							return say(player(ctx), on ? "kultivator.reruntuhan.uji.eclipse.on" : "kultivator.reruntuhan.uji.eclipse.off");
						})))
				.then(Commands.literal("segel").then(Commands.argument("nilai", IntegerArgumentType.integer(0, 100))
						.executes(ctx -> setSeal(player(ctx), IntegerArgumentType.getInteger(ctx, "nilai")))))
				.then(Commands.literal("cabut").then(Commands.argument("jimat", IntegerArgumentType.integer(1, 5))
						.executes(ctx -> pull(player(ctx), IntegerArgumentType.getInteger(ctx, "jimat")))))
				.then(Commands.literal("lepas").then(Commands.argument("raja", StringArgumentType.word()).suggests(KINGS)
						.executes(ctx -> release(player(ctx), StringArgumentType.getString(ctx, "raja")))))
				.then(Commands.literal("segel_ulang").executes(ctx -> reseal(player(ctx))))
				.then(Commands.literal("hapus").executes(ctx -> forget(player(ctx))));
	}

	private static int info(ServerPlayer player) {
		Prisons.Seal seal = Prisons.nearestSeal(player);
		if (seal == null) {
			return say(player, "kultivator.reruntuhan.uji.no_prison");
		}
		Msg.chat(player, Component.translatable("kultivator.reruntuhan.uji.seal", seal.integrity(), Component.translatable(seal.broken() ? "kultivator.reruntuhan.uji.seal.broken" : "kultivator.reruntuhan.uji.seal.whole"),
				Integer.bitCount(seal.pulled()), Component.translatable("entity.kultivator.raja_iblis." + RajaIblisEntity.IDS[seal.king()]))
				.withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int setSeal(ServerPlayer player, int value) {
		Prisons.Seal seal = Prisons.nearestSeal(player);
		if (seal == null) {
			return say(player, "kultivator.reruntuhan.uji.no_prison");
		}
		Prisons.testSet(player.level(), seal, value);
		return info(player);
	}

	private static int pull(ServerPlayer player, int n) {
		ShrineRun shrine = Prisons.nearestShrine(player, 64.0D);
		if (shrine == null) {
			return say(player, "kultivator.reruntuhan.uji.no_prison");
		}
		shrine.testPull(player, n - 1);
		return info(player);
	}

	private static int release(ServerPlayer player, String king) {
		Prisons.Seal seal = Prisons.nearestSeal(player);
		if (seal == null) {
			return say(player, "kultivator.reruntuhan.uji.no_prison");
		}
		int index = Arrays.asList(RajaIblisEntity.IDS).indexOf(king.toLowerCase(Locale.ROOT));
		if (index < 0) {
			return say(player, "kultivator.reruntuhan.uji.unknown", king);
		}
		Prisons.testRelease(player.level(), seal, index);
		return info(player);
	}

	private static int reseal(ServerPlayer player) {
		CoreRun core = Prisons.nearestCore(player, 96.0D);
		if (core == null) {
			Prisons.Seal seal = Prisons.nearestSeal(player);
			if (seal == null) {
				return say(player, "kultivator.reruntuhan.uji.no_prison");
			}
			Prisons.testSet(player.level(), seal, 100);
			return info(player);
		}
		core.testReseal();
		return info(player);
	}

	private static int forget(ServerPlayer player) {
		Prisons.Seal seal = Prisons.nearestSeal(player);
		if (seal == null) {
			return say(player, "kultivator.reruntuhan.uji.no_prison");
		}
		Runs.clear(CoreRun.class);
		Runs.clear(ShrineRun.class);
		Prisons.forget(player.level(), seal);
		return say(player, "kultivator.reruntuhan.uji.forgot");
	}

	private RTests() {
	}
}
