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
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.dimensi.bawah.ArwahEntity;
import dev.kultivator.dunia.dimensi.bawah.BawahPeople;
import dev.kultivator.dunia.dimensi.bawah.Courts;
import dev.kultivator.dunia.dimensi.bawah.SoulJourney;
import dev.kultivator.dunia.dimensi.laut.KepitingBatuEntity;
import dev.kultivator.dunia.dimensi.laut.LautNpcEntity;
import dev.kultivator.dunia.dimensi.laut.SeaGate;
import dev.kultivator.dunia.dimensi.laut.SeaMonsters;
import dev.kultivator.dunia.dimensi.laut.SeaPeople;
import dev.kultivator.dunia.dimensi.laut.UlarLautEntity;
import dev.kultivator.dunia.dimensi.laut.YakshaEntity;
import dev.kultivator.data.Cultivators;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * {@code /kultivasi uji bawah ...} and {@code uji laut ...} (DESIGN 34.5.13-14 "Uji"), op only, registered through the
 * spine's {@link DuniaCommands#test}. Nothing here places a structure (rule W6): {@code ... cari} finds the nearest one
 * (vanilla locate, by structure or by tag) and teleports there.
 * <p>
 * Besides the design's nodes: {@code bawah wuchang [putih|hitam]}, {@code bawah celoteh} (the nearest NPC speaks now),
 * {@code bawah langit <gerhana|meteor|bulan_kembar|berhenti> [menit]} (module D's own reaction to a sky event, L's sky
 * untouched), {@code bawah bentrok} (the duel clash effect), {@code laut raja} (the Dragon King before you: armoury and
 * quests), {@code laut nelayan} (the fisherman), {@code laut pulau cari <herba|pertapa|pasar|karang>} (each kind is its own
 * structure now).
 */
public final class DimensiCommands {

	private static final SuggestionProvider<CommandSourceStack> SOUPS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(List.of("dao", "hati", "teknik", "tolak"), builder);
	private static final SuggestionProvider<CommandSourceStack> ISLANDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(SeaPeople.ISLAND_KINDS, builder);
	private static final SuggestionProvider<CommandSourceStack> MONSTERS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(List.of("ular", "kepiting", "yaksha"), builder);
	private static final SuggestionProvider<CommandSourceStack> WUCHANG = (ctx, builder) ->
			SharedSuggestionProvider.suggest(List.of("putih", "hitam"), builder);
	private static final SuggestionProvider<CommandSourceStack> SKY = (ctx, builder) ->
			SharedSuggestionProvider.suggest(List.of("gerhana", "meteor", "bulan_kembar", "berhenti"), builder);

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
				.then(Commands.literal("wuchang")
						.executes(ctx -> wuchang(ctx, "putih"))
						.then(Commands.argument("warna", StringArgumentType.word()).suggests(WUCHANG)
								.executes(ctx -> wuchang(ctx, StringArgumentType.getString(ctx, "warna")))))
				.then(Commands.literal("celoteh").executes(DimensiCommands::chatter))
				.then(Commands.literal("langit").then(Commands.argument("jenis", StringArgumentType.word()).suggests(SKY)
						.executes(ctx -> sky(ctx, 5))
						.then(Commands.argument("menit", IntegerArgumentType.integer(1, 60))
								.executes(ctx -> sky(ctx, IntegerArgumentType.getInteger(ctx, "menit"))))))
				.then(Commands.literal("bentrok").executes(ctx -> {
					ServerPlayer player = player(ctx);
					Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(1.5D));
					DimensiNet.fx(player.level(), DimensiNet.FX_CLASH, at, DimensiLevels.isLaut(player.level()) ? 1 : 0, 48.0D);
					return 1;
				}))
				.then(Commands.literal("kuil").then(Commands.literal("cari").executes(ctx ->
						findTag(ctx, "bawah/kuil", Level.OVERWORLD))));
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
						.executes(ctx -> findAtSea(ctx, null))
						.then(Commands.argument("jenis", StringArgumentType.word()).suggests(ISLANDS)
								.executes(ctx -> {
									String jenis = StringArgumentType.getString(ctx, "jenis");
									if (!SeaPeople.ISLAND_KINDS.contains(jenis)) {
										ctx.getSource().sendFailure(Component.translatable("kultivator.dimensi.uji.pulau_tak_dikenal", jenis));
										return 0;
									}
									return findAtSea(ctx, "laut/pulau_" + jenis);
								}))))
				.then(Commands.literal("istana").then(Commands.literal("cari").executes(ctx -> findAtSea(ctx, "laut/istana_naga"))))
				.then(Commands.literal("penglai").executes(ctx -> findAtSea(ctx, "laut/pulau_penglai")))
				.then(Commands.literal("pusaran").then(Commands.literal("cari").executes(ctx ->
						findTag(ctx, "laut/pusaran", Level.OVERWORLD))))
				.then(Commands.literal("monster").then(Commands.argument("jenis", StringArgumentType.word()).suggests(MONSTERS)
						.executes(ctx -> monster(ctx, -1))
						.then(Commands.argument("tingkat", IntegerArgumentType.integer(0, 4))
								.executes(ctx -> monster(ctx, IntegerArgumentType.getInteger(ctx, "tingkat"))))))
				.then(Commands.literal("penyu").executes(DimensiCommands::turtle))
				.then(Commands.literal("raja").executes(ctx -> seaPerson(ctx, LautNpcEntity.RAJA_NAGA)))
				.then(Commands.literal("nelayan").executes(ctx -> seaPerson(ctx, LautNpcEntity.NELAYAN)));
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

	/** {@code uji bawah wuchang [putih|hitam]}: Bai or Hei Wuchang 3 b in front of the player. */
	private static int wuchang(CommandContext<CommandSourceStack> ctx, String colour) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		ServerLevel level = player.level();
		ArwahEntity npc = DimensiEntities.ARWAH.create(level, EntitySpawnReason.COMMAND);
		if (npc == null) {
			return 0;
		}
		int role = "hitam".equals(colour) ? ArwahEntity.WUCHANG_HITAM : ArwahEntity.WUCHANG_PUTIH;
		npc.setup(role, role == ArwahEntity.WUCHANG_HITAM ? 2 : 5, 0, Cultivators.get(player).realm().ordinal());
		Vec3 at = player.position().add(player.getLookAngle().multiply(3.0D, 0.0D, 3.0D));
		npc.snapTo(at.x, at.y, at.z, player.getYRot() + 180.0F, 0.0F);
		npc.setYHeadRot(player.getYRot() + 180.0F);
		npc.setHome(npc.blockPosition(), 8);
		if (!level.addFreshEntity(npc)) {
			return 0;
		}
		DuniaBudget.track(DuniaBudget.NPC, npc);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.dimensi.uji.wuchang", npc.getName()), false);
		return 1;
	}

	/** {@code uji bawah celoteh}: the nearest of module D's people within 12 b speaks to the player now. */
	private static int chatter(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		DimensiNpc nearest = null;
		double best = 12.0D * 12.0D;
		for (DimensiNpc npc : player.level().getEntitiesOfClass(DimensiNpc.class, player.getBoundingBox().inflate(12.0D))) {
			double d = npc.distanceToSqr(player);
			if (npc.chatterKey() != null && d < best) {
				best = d;
				nearest = npc;
			}
		}
		if (nearest == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.dimensi.uji.celoteh_tiada"));
			return 0;
		}
		DimensiChatter.say(nearest, player, DimensiChatter.line(nearest, player, player.getRandom()));
		return 1;
	}

	/** {@code uji bawah langit <jenis> [menit]}: module D reacts as if the sky event held (L's sky is not touched). */
	private static int sky(CommandContext<CommandSourceStack> ctx, int minutes) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		String what = StringArgumentType.getString(ctx, "jenis");
		SkyKind kind = switch (what) {
			case "gerhana" -> SkyKind.BLOOD_ECLIPSE;
			case "meteor" -> SkyKind.METEOR_SHOWER;
			case "bulan_kembar" -> SkyKind.TWIN_MOONS;
			default -> null;
		};
		DimensiSky.testOverride(player.level().getServer(), kind, kind == null ? 0 : minutes);
		for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
			SoulJourney.sendState(other);
		}
		ctx.getSource().sendSuccess(() -> kind == null ? Component.translatable("kultivator.dimensi.uji.langit_henti")
				: Component.translatable("kultivator.dimensi.uji.langit", Component.translatable("kultivator.dimensi.langit." + kind.id()), minutes),
				false);
		return 1;
	}

	/** {@code uji laut raja|nelayan}: that person of the sea 3 b in front of the player (anywhere; its screens work). */
	private static int seaPerson(CommandContext<CommandSourceStack> ctx, int role) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		ServerLevel level = player.level();
		LautNpcEntity npc = DimensiEntities.RAJA_NAGA.create(level, EntitySpawnReason.COMMAND);
		if (npc == null) {
			return 0;
		}
		npc.setup(role, role == LautNpcEntity.RAJA_NAGA ? 7 : level.getRandom().nextInt(9), Cultivators.get(player).realm().ordinal());
		Vec3 at = player.position().add(player.getLookAngle().multiply(3.0D, 0.0D, 3.0D));
		npc.snapTo(at.x, at.y, at.z, player.getYRot() + 180.0F, 0.0F);
		npc.setYHeadRot(player.getYRot() + 180.0F);
		npc.setHome(npc.blockPosition(), 3);
		if (!level.addFreshEntity(npc)) {
			return 0;
		}
		DuniaBudget.track(DuniaBudget.NPC, npc);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.dimensi.uji.orang_laut", npc.getName()), false);
		return 1;
	}

	/** Finds the nearest structure of the tag {@code kultivator:<tagPath>} (vanilla locate, 100 chunks) and teleports there. */
	private static int findTag(CommandContext<CommandSourceStack> ctx, String tagPath, ResourceKey<Level> dim) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		if (player.level().dimension() != dim) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.dimensi.uji.dunia_salah"));
			return 0;
		}
		return teleportToTag(player, TagKey.create(Registries.STRUCTURE, Kultivator.id(tagPath)));
	}

	/** The W6 rule by tag: locate, load the column like any teleport, stand on top (the spine's find, for a tag). */
	private static int teleportToTag(ServerPlayer player, TagKey<Structure> tag) {
		ServerLevel level = player.level();
		BlockPos found = level.findNearestMapStructure(tag, player.blockPosition(), 100, false);
		if (found == null) {
			player.sendSystemMessage(Component.translatable("kultivator.dunia.cmd.find.none", "#" + tag.location()).withStyle(ChatFormatting.RED));
			return 0;
		}
		level.getChunk(found.getX() >> 4, found.getZ() >> 4, ChunkStatus.FULL, true);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, found.getX(), found.getZ());
		if (y <= level.getMinY() + 1) {
			y = Mth.clamp(found.getY(), level.getMinY() + 2, level.getMaxY() - 2);
		}
		int distance = (int) Math.sqrt(found.distSqr(player.blockPosition()));
		player.teleportTo(level, found.getX() + 0.5D, y + 1.0D, found.getZ() + 0.5D, Set.of(), player.getYRot(), player.getXRot(), true);
		player.sendSystemMessage(Component.translatable("kultivator.dunia.cmd.find.ok", "#" + tag.location(), found.getX(), y, found.getZ(), distance)
				.withStyle(ChatFormatting.GREEN));
		return 1;
	}

	/**
	 * Finds a structure of the sea ({@code id} null: any spirit island, by the tag {@code laut/pulau}): crosses into the
	 * sea first when needed, then searches a moment later.
	 */
	private static int findAtSea(CommandContext<CommandSourceStack> ctx, @org.jetbrains.annotations.Nullable String id) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		Identifier structure = id == null ? null : Kultivator.id(id);
		if (!DimensiLevels.isLaut(player.level())) {
			if (!SeaGate.enter(player, true)) {
				return 0;
			}
			java.util.UUID who = player.getUUID();
			Dimensi.schedule(40, () -> {
				ServerPlayer now = player.level().getServer().getPlayerList().getPlayer(who);
				if (now != null && DimensiLevels.isLaut(now.level())) {
					if (structure == null) {
						teleportToTag(now, SeaGate.ISLANDS);
					} else {
						DuniaCommands.findAndTeleport(now, structure);
					}
				}
			});
			return 1;
		}
		return structure == null ? teleportToTag(player, SeaGate.ISLANDS) : DuniaCommands.findAndTeleport(player, structure);
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
