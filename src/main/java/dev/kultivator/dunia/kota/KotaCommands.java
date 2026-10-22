package dev.kultivator.dunia.kota;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.Kultivator;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Module K's commands (DESIGN 34.5.1-34.5.4 "Uji", all op-only under {@code /kultivasi uji}): {@code kota},
 * {@code pengembara}, {@code saingan}, {@code kota_abadi}; and two player commands, {@code /kultivasi bicara} (talk to the
 * nearest wanderer, the "[Bicara]" chip) and {@code /kultivasi suap} (pay the raid bribe). Structures are found and
 * reached (vanilla locate + teleport), never placed (rule W6); test NPCs are ordinary session NPCs.
 */
final class KotaCommands {

	private static final SuggestionProvider<CommandSourceStack> ROLES = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(NpcRole.values()).map(NpcRole::id), builder);
	private static final SuggestionProvider<CommandSourceStack> DAOS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Dao.values()).map(Dao::id), builder);
	private static final SuggestionProvider<CommandSourceStack> REALMS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.stream(Realm.values()).map(Realm::id), builder);
	private static final SuggestionProvider<CommandSourceStack> TEMPERS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(List.of("ramah", "angkuh", "licik"), builder);
	private static final SuggestionProvider<CommandSourceStack> TEAS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(List.of("embun", "awan", "penenang"), builder);
	private static final SuggestionProvider<CommandSourceStack> GROUPS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Saingan.SERVICE.all(), builder);
	/** The four biome looks of a town (the structures kota/kota_kultivator[_salju|_sabana|_sakura]). */
	static final List<String> LOOK_IDS = List.of("sedang", "salju", "sabana", "sakura");
	private static final SuggestionProvider<CommandSourceStack> LOOKS = (ctx, builder) -> SharedSuggestionProvider.suggest(LOOK_IDS, builder);
	static final List<String> GESTURE_IDS = List.of("bicara", "hormat", "tatap", "kerja", "duduk", "hunus");
	private static final SuggestionProvider<CommandSourceStack> GESTURES = (ctx, builder) -> SharedSuggestionProvider.suggest(GESTURE_IDS, builder);
	private static final SuggestionProvider<CommandSourceStack> SKY_REACTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(List.of("blood_eclipse", "meteor_shower", "twin_moons", "harta", "blood_eclipse_end"), builder);

	static void init() {
		DuniaCommands.test("kota", KotaCommands::kota);
		DuniaCommands.test("pengembara", KotaCommands::pengembara);
		DuniaCommands.test("saingan", KotaCommands::saingan);
		DuniaCommands.test("kota_abadi", KotaCommands::kotaAbadi);
		DuniaCommands.player("bicara", () -> Commands.literal("bicara").executes(ctx -> Pengembara.talkNearest(ctx.getSource().getPlayerOrException())));
		DuniaCommands.player("suap", () -> Commands.literal("suap").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			return PasarGelap.bribe(player, 0L) ? 1 : 0;
		}));
	}

	// ================================================================== kota

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> kota() {
		return Commands.literal("kota")
				.then(Commands.literal("cari").executes(ctx -> findTown(ctx.getSource().getPlayerOrException(), null))
						.then(Commands.argument("tampilan", StringArgumentType.word()).suggests(LOOKS)
								.executes(ctx -> findTown(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "tampilan")))))
				.then(Commands.literal("barisan").executes(KotaCommands::lineUp))
				.then(Commands.literal("obrol").executes(ctx -> NpcLife.testChat(ctx.getSource().getPlayerOrException()) ? 1 : 0))
				.then(Commands.literal("gerak").then(Commands.argument("gerak", StringArgumentType.word()).suggests(GESTURES)
						.executes(ctx -> gesture(ctx, StringArgumentType.getString(ctx, "gerak")))))
				.then(Commands.literal("langit").then(Commands.argument("peristiwa", StringArgumentType.word()).suggests(SKY_REACTIONS)
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							int n = NpcLife.testReact(player, StringArgumentType.getString(ctx, "peristiwa"));
							ok(ctx, "kultivator.kota.uji.langit", n);
							return Math.max(1, n);
						})))
				.then(Commands.literal("npc").then(Commands.argument("peran", StringArgumentType.word()).suggests(ROLES)
						.executes(ctx -> npc(ctx, StringArgumentType.getString(ctx, "peran")))))
				.then(Commands.literal("lelang")
						.then(Commands.literal("mulai").executes(KotaCommands::auctionStart))
						.then(Commands.literal("tutup").executes(ctx -> {
							BalaiLelang.forceClose(ctx.getSource().getServer());
							ok(ctx, "kultivator.kota.uji.lelang_tutup");
							return 1;
						}))
						.then(Commands.literal("tawar").then(Commands.argument("persen", IntegerArgumentType.integer(5, 25))
								.executes(ctx -> auctionBid(ctx, IntegerArgumentType.getInteger(ctx, "persen"))))))
				.then(Commands.literal("teh").then(Commands.argument("jenis", StringArgumentType.word()).suggests(TEAS).executes(ctx -> {
					String kind = StringArgumentType.getString(ctx, "jenis");
					int index = Arrays.asList(KedaiTeh.IDS).indexOf(kind.toLowerCase(Locale.ROOT));
					if (index < 0) {
						ctx.getSource().sendFailure(Component.translatable("kultivator.kota.uji.jenis_tidak_dikenal", kind));
						return 0;
					}
					return KedaiTeh.drink(ctx.getSource().getPlayerOrException(), index, false) ? 1 : 0;
				})))
				.then(Commands.literal("gelap")
						.then(Commands.literal("buka").executes(KotaCommands::openDoor))
						.then(Commands.literal("razia").executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							PasarGelap.raid(player, nearestNpc(player, NpcRole.MAKELAR_GELAP));
							return 1;
						})))
				.then(Commands.literal("nama_baik").then(Commands.argument("nilai", IntegerArgumentType.integer(-100, 100)).executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					long town = townHere(player);
					int value = IntegerArgumentType.getInteger(ctx, "nilai");
					KotaData.get(ctx.getSource().getServer()).setReputation(town, player.getUUID(), value);
					ok(ctx, "kultivator.kota.uji.nama_baik", KotaNames.town(town), value);
					return 1;
				})))
				.then(Commands.literal("misi")
						.then(Commands.literal("baru").executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							KotaData data = KotaData.get(ctx.getSource().getServer());
							data.player(player.getUUID()).remove("misi");
							data.player(player.getUUID()).remove("daily");
							for (Long id : data.townIds()) {
								data.town(id).remove("misi_taken");
							}
							data.touch();
							ok(ctx, "kultivator.kota.uji.misi_baru");
							return 1;
						}))
						.then(Commands.literal("selesai").executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							CompoundTag misi = PapanMisi.active(player);
							if (misi.isEmpty()) {
								ctx.getSource().sendFailure(Component.translatable("kultivator.kota.uji.misi_tidak_ada"));
								return 0;
							}
							misi.putBoolean("done", true);
							KotaData.get(ctx.getSource().getServer()).touch();
							ok(ctx, "kultivator.kota.uji.misi_selesai");
							return 1;
						})))
				.then(Commands.literal("stok").then(Commands.literal("ulang").executes(ctx -> {
					KotaData data = KotaData.get(ctx.getSource().getServer());
					for (Long id : data.townIds()) {
						data.town(id).remove("sold");
						data.town(id).remove("auction_day");
					}
					data.touch();
					ok(ctx, "kultivator.kota.uji.stok_ulang");
					return 1;
				})));
	}

	/**
	 * {@code uji kota cari [tampilan]}: the nearest town of any look (the structure tag {@code #kultivator:kota}), or of
	 * one look, found with vanilla locate and reached by teleport like {@code /locate} + {@code /tp} (rule W6).
	 */
	static int findTown(ServerPlayer player, @Nullable String look) {
		if (look != null) {
			String id = look.toLowerCase(Locale.ROOT);
			if (!LOOK_IDS.contains(id)) {
				player.sendSystemMessage(Component.translatable("kultivator.kota.uji.jenis_tidak_dikenal", look).withStyle(ChatFormatting.RED));
				return 0;
			}
			return DuniaCommands.findAndTeleport(player, Kultivator.id("sedang".equals(id) ? "kota/kota_kultivator" : "kota/kota_kultivator_" + id));
		}
		ServerLevel level = (ServerLevel) player.level();
		net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure> tag = net.minecraft.tags.TagKey.create(
				net.minecraft.core.registries.Registries.STRUCTURE, Kultivator.id("kota"));
		BlockPos found = level.findNearestMapStructure(tag, player.blockPosition(), 100, false);
		if (found == null) {
			player.sendSystemMessage(Component.translatable("kultivator.dunia.cmd.find.none", "#kultivator:kota").withStyle(ChatFormatting.RED));
			return 0;
		}
		level.getChunk(found.getX() >> 4, found.getZ() >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, found.getX(), found.getZ());
		if (y <= level.getMinY() + 1) {
			y = net.minecraft.util.Mth.clamp(found.getY(), level.getMinY() + 2, level.getMaxY() - 2);
		}
		int distance = (int) Math.sqrt(found.distSqr(player.blockPosition()));
		player.teleportTo(level, found.getX() + 0.5D, y + 1.0D, found.getZ() + 0.5D, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
		player.sendSystemMessage(Component.translatable("kultivator.dunia.cmd.find.ok", "#kultivator:kota", found.getX(), y, found.getZ(), distance)
				.withStyle(ChatFormatting.GREEN));
		return 1;
	}

	/** {@code uji kota barisan}: one NPC of every role in a row before the player (looks, gear, plates, poses). */
	private static int lineUp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Vec3 look = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize();
		if (look.lengthSqr() < 1.0E-4D) {
			look = new Vec3(0.0D, 0.0D, 1.0D);
		}
		Vec3 side = new Vec3(-look.z, 0.0D, look.x);
		NpcRole[] roles = NpcRole.values();
		int n = 0;
		for (int i = 0; i < roles.length; i++) {
			Vec3 at = player.position().add(look.scale(5.0D)).add(side.scale((i - (roles.length - 1) / 2.0D) * 1.6D));
			KultivatorNpc npc = spawnTestAt(player, roles[i], at);
			if (npc == null) {
				break;
			}
			npc.memory().putBoolean("hold", true);
			npc.setHome(npc.blockPosition());
			NpcLife.face(npc, player.position());
			n++;
		}
		ok(ctx, "kultivator.kota.uji.barisan", n);
		return Math.max(1, n);
	}

	/** {@code uji kota gerak <bicara|hormat|tatap|kerja|duduk|hunus>} on the nearest NPC within 16 blocks (5 s). */
	private static int gesture(CommandContext<CommandSourceStack> ctx, String what) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		KultivatorNpc npc = null;
		double bestSq = 16.0D * 16.0D;
		for (KultivatorNpc each : player.level().getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(16.0D), KultivatorNpc::isAlive)) {
			double d = each.distanceToSqr(player);
			if (d < bestSq) {
				bestSq = d;
				npc = each;
			}
		}
		if (npc == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota.uji.tidak_ada", Component.translatable("kultivator.kota.uji.siapa")));
			return 0;
		}
		switch (what.toLowerCase(Locale.ROOT)) {
			case "bicara" -> NpcLife.speak(npc, "kultivator.kota.warga.kata." + npc.getRandom().nextInt(12), 100);
			case "hormat" -> npc.bow(100);
			case "tatap" -> npc.gaze(100);
			case "kerja" -> npc.work(100);
			case "duduk" -> npc.setFlag(KultivatorNpc.FLAG_SITTING, !npc.hasFlag(KultivatorNpc.FLAG_SITTING));
			case "hunus" -> {
				npc.fought();
				npc.arm(npc.getMainHandItem().isEmpty());
			}
			default -> {
				ctx.getSource().sendFailure(Component.translatable("kultivator.kota.uji.jenis_tidak_dikenal", what));
				return 0;
			}
		}
		ok(ctx, "kultivator.kota.uji.gerak", npc.plate(), what);
		return 1;
	}

	/** The town the player stands in, else a test town named by their position (so every service works anywhere). */
	static long townHere(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		long town = Towns.near(level, player.blockPosition(), 200);
		if (town != 0L) {
			return town;
		}
		BlockPos at = new BlockPos(player.getBlockX() >> 6 << 6, player.getBlockY(), player.getBlockZ() >> 6 << 6);
		town = DuniaSites.idOf(level.dimension(), at, "kota/uji");
		CompoundTag tag = KotaData.get(level.getServer()).town(town);
		if (!tag.contains("dim")) {
			tag.putString("dim", level.dimension().identifier().toString());
			tag.putLong("pos", at.asLong());
			tag.putBoolean("abadi", dev.kultivator.abadi.Abadi.isImmortalLevel(level));
			KotaData.get(level.getServer()).touch();
		}
		return town;
	}

	private static int npc(CommandContext<CommandSourceStack> ctx, String id) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		NpcRole role = NpcRole.byId(id.toLowerCase(Locale.ROOT));
		if (role == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota.uji.jenis_tidak_dikenal", id));
			return 0;
		}
		if (role == NpcRole.PENGEMBARA) {
			return Pengembara.spawnFor(player, null, -1, -1, -1) != null ? 1 : 0;
		}
		KultivatorNpc npc = spawnTest(player, role);
		if (npc == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota.uji.tidak_bisa"));
			return 0;
		}
		ok(ctx, "kultivator.kota.uji.npc", npc.plate());
		return 1;
	}

	/** A test NPC of {@code role} 3 blocks ahead, bound to the town here (or the test town). */
	static @Nullable KultivatorNpc spawnTest(ServerPlayer player, NpcRole role) {
		Vec3 look = player.getLookAngle();
		return spawnTestAt(player, role, player.position().add(look.x * 3.0D, 0.0D, look.z * 3.0D));
	}

	/** A test NPC of {@code role} at {@code at}, bound to the town here (or the test town). */
	static @Nullable KultivatorNpc spawnTestAt(ServerPlayer player, NpcRole role, Vec3 at) {
		ServerLevel level = (ServerLevel) player.level();
		if (!DuniaBudget.mayAdd(level, DuniaBudget.NPC)) {
			return null;
		}
		KultivatorNpc npc = KotaEntities.NPC.create(level, EntitySpawnReason.EVENT);
		if (npc == null) {
			return null;
		}
		Vec3 spot = Targeting.safeSpot(level, npc, at);
		if (spot == null) {
			spot = at;
		}
		int seed = level.getRandom().nextInt();
		long town = townHere(player);
		npc.snapTo(spot.x, spot.y, spot.z, player.getYRot() + 180.0F, 0.0F);
		int realm = role.immortal() ? Realm.IMMORTAL_ASCENSION.ordinal() : role == NpcRole.WARGA ? 0 : Realm.FOUNDATION.ordinal();
		npc.setup(role, seed, realm, 0, Dao.values()[Math.floorMod(seed, Dao.COUNT)], Math.floorMod(seed >>> 3, 6), 0, KotaNames.plate(seed, role));
		npc.setHome(BlockPos.containing(spot));
		npc.setTown(town);
		npc.setAnchorSite(DuniaSites.idOf(level.dimension(), BlockPos.containing(spot), "kota/" + role.id()));
		npc.setLonely(64.0D, 1200);
		if (role == NpcRole.PENJAGA) {
			npc.setFightsMonsters(true);
			npc.memory().putBoolean("hold", true);
		}
		if (!level.addFreshEntity(npc)) {
			return null;
		}
		DuniaBudget.track(DuniaBudget.NPC, npc);
		return npc;
	}

	static @Nullable KultivatorNpc nearestNpc(ServerPlayer player, NpcRole role) {
		KultivatorNpc best = null;
		double bestSq = 48.0D * 48.0D;
		for (KultivatorNpc npc : player.level().getEntitiesOfClass(KultivatorNpc.class, new AABB(player.blockPosition()).inflate(48.0D),
				each -> each.role() == role && each.isAlive())) {
			double d = npc.distanceToSqr(player);
			if (d < bestSq) {
				bestSq = d;
				best = npc;
			}
		}
		return best;
	}

	private static int auctionStart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		KultivatorNpc auctioneer = nearestNpc(player, NpcRole.JURU_LELANG);
		if (auctioneer == null) {
			auctioneer = spawnTest(player, NpcRole.JURU_LELANG);
		}
		if (auctioneer == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota.uji.tidak_bisa"));
			return 0;
		}
		BalaiLelang.forceStart(player, auctioneer);
		ok(ctx, "kultivator.kota.uji.lelang_mulai");
		return 1;
	}

	private static int auctionBid(CommandContext<CommandSourceStack> ctx, int percent) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		KultivatorNpc auctioneer = nearestNpc(player, NpcRole.JURU_LELANG);
		if (auctioneer == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota.uji.tidak_ada", Component.translatable(NpcRole.JURU_LELANG.titleKey())));
			return 0;
		}
		BalaiLelang.testBid(player, auctioneer, percent);
		return 1;
	}

	private static int openDoor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();
		KotaPintuEntity best = null;
		double bestSq = 64.0D * 64.0D;
		for (KotaPintuEntity door : level.getEntitiesOfClass(KotaPintuEntity.class, new AABB(player.blockPosition()).inflate(64.0D))) {
			double d = door.distanceToSqr(player);
			if (d < bestSq) {
				bestSq = d;
				best = door;
			}
		}
		if (best == null) {
			// no town door near: a test door in front of the player
			best = KotaEntities.PINTU.create(level, EntitySpawnReason.EVENT);
			if (best == null) {
				return 0;
			}
			Vec3 look = player.getLookAngle();
			Vec3 at = Vec3.atBottomCenterOf(BlockPos.containing(player.position().add(look.x * 3.0D, 0.0D, look.z * 3.0D)));
			best.snapTo(at.x, at.y, at.z, player.getYRot(), 0.0F);
			best.bind(0L, townHere(player));
			if (!level.addFreshEntity(best)) {
				return 0;
			}
		}
		best.setBarred(false);
		best.openFor(level);
		ok(ctx, "kultivator.kota.uji.pintu");
		return 1;
	}

	// ================================================================== pengembara

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> pengembara() {
		return Commands.literal("pengembara")
				.executes(ctx -> wanderer(ctx, -1, -1, -1))
				.then(Commands.literal("duel").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					KultivatorNpc npc = nearestNpc(player, NpcRole.PENGEMBARA);
					if (npc == null) {
						npc = Pengembara.spawnFor(player, null, Dao.SWORD.ordinal(), -1, Pengembara.ANGKUH);
					}
					if (npc == null) {
						ctx.getSource().sendFailure(Component.translatable("kultivator.kota.uji.tidak_bisa"));
						return 0;
					}
					KotaDuel.testOffer(player, npc);
					ok(ctx, "kultivator.kota.uji.duel", npc.plate());
					return 1;
				}))
				.then(Commands.literal("diskusi")
						.then(Commands.literal("menang").executes(ctx -> forceDiscussion(ctx, true)))
						.then(Commands.literal("kalah").executes(ctx -> forceDiscussion(ctx, false))))
				.then(Commands.literal("hapus").executes(ctx -> {
					int n = 0;
					for (net.minecraft.world.entity.Entity entity : DuniaBudget.tracked(DuniaBudget.NPC)) {
						if (entity instanceof KultivatorNpc npc && npc.role() == NpcRole.PENGEMBARA) {
							npc.vanish();
							n++;
						}
					}
					int count = n;
					ok(ctx, "kultivator.kota.uji.hapus", count);
					return Math.max(1, n);
				}))
				.then(Commands.argument("dao", StringArgumentType.word()).suggests(DAOS)
						.executes(ctx -> wanderer(ctx, daoIndex(ctx), -1, -1))
						.then(Commands.argument("ranah", StringArgumentType.word()).suggests(REALMS)
								.executes(ctx -> wanderer(ctx, daoIndex(ctx), realmIndex(ctx), -1))
								.then(Commands.argument("watak", StringArgumentType.word()).suggests(TEMPERS)
										.executes(ctx -> wanderer(ctx, daoIndex(ctx), realmIndex(ctx),
												List.of("ramah", "angkuh", "licik").indexOf(StringArgumentType.getString(ctx, "watak")))))));
	}

	private static int daoIndex(CommandContext<CommandSourceStack> ctx) {
		Dao dao = Dao.byId(StringArgumentType.getString(ctx, "dao"));
		return dao == null ? -1 : dao.ordinal();
	}

	private static int realmIndex(CommandContext<CommandSourceStack> ctx) {
		Realm realm = Realm.byId(StringArgumentType.getString(ctx, "ranah"));
		return realm == null ? -1 : realm.ordinal();
	}

	private static int wanderer(CommandContext<CommandSourceStack> ctx, int dao, int realm, int temper) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		KultivatorNpc npc = Pengembara.spawnFor(player, null, dao, realm, temper);
		if (npc == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota.uji.tidak_bisa"));
			return 0;
		}
		ok(ctx, "kultivator.kota.uji.npc", npc.plate());
		return 1;
	}

	private static int forceDiscussion(CommandContext<CommandSourceStack> ctx, boolean win) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		KultivatorNpc npc = nearestNpc(player, NpcRole.PENGEMBARA);
		if (npc == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota.uji.tidak_ada", Component.translatable(NpcRole.PENGEMBARA.titleKey())));
			return 0;
		}
		Pengembara.forceDiscussion(player, npc, win);
		return 1;
	}

	// ================================================================== saingan

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> saingan() {
		return Commands.literal("saingan")
				.then(Commands.literal("daftar").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					SainganData data = SainganData.get(ctx.getSource().getServer());
					ok(ctx, "kultivator.kota.uji.saingan_kepala", data.groups().size(), Saingan.band(ctx.getSource().getServer()),
							Saingan.races().size(), Saingan.pendingAmbushes());
					for (SainganData.Group group : data.groups()) {
						ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota.uji.saingan_baris", group.id, Saingan.groupName(group),
								Component.translatable("kultivator.kota.gaya." + SainganData.STYLES[group.style]), group.region.getX(), group.region.getZ(),
								group.grudge(player.getUUID()), group.wins, group.losses, group.beatenBy(player.getUUID()) ? "✔" : "-")
								.withStyle(ChatFormatting.GRAY), false);
					}
					return 1;
				}))
				.then(Commands.literal("datang").then(Commands.argument("grup", StringArgumentType.word()).suggests(GROUPS).executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					SainganData.Group group = SainganData.get(ctx.getSource().getServer()).byId(StringArgumentType.getString(ctx, "grup"));
					if (group == null) {
						ctx.getSource().sendFailure(Component.translatable("kultivator.kota.uji.jenis_tidak_dikenal", StringArgumentType.getString(ctx, "grup")));
						return 0;
					}
					return Saingan.strike(player, group, 3) ? 1 : 0;
				})))
				.then(Commands.literal("perlombaan").executes(ctx -> Saingan.testRace(ctx.getSource().getPlayerOrException()) ? 1 : 0))
				.then(Commands.literal("sergap").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Saingan.SERVICE.ambush(player, 3);
					return 1;
				}))
				.then(Commands.literal("dendam").then(Commands.argument("grup", StringArgumentType.word()).suggests(GROUPS)
						.then(Commands.argument("nilai", IntegerArgumentType.integer(0, 100)).executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							String group = StringArgumentType.getString(ctx, "grup");
							int value = IntegerArgumentType.getInteger(ctx, "nilai");
							SainganData.get(ctx.getSource().getServer()).setGrudge(group, player.getUUID(), value);
							ok(ctx, "kultivator.kota.uji.dendam", Saingan.name(ctx.getSource().getServer(), group), value);
							return 1;
						}))));
	}

	// ================================================================== kota_abadi

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> kotaAbadi() {
		return Commands.literal("kota_abadi")
				.then(Commands.literal("cari").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					if (!Kota.inAlamAbadi(player)) {
						ctx.getSource().sendFailure(Component.translatable("kultivator.kota.uji.abadi_saja"));
						return 0;
					}
					return DuniaCommands.findAndTeleport(player, Kultivator.id("kota/kota_abadi"));
				}))
				.then(Commands.literal("kuliah").executes(ctx -> {
					KotaAbadi.lectureReward(ctx.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("persik").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					net.minecraft.world.item.ItemStack peach = new net.minecraft.world.item.ItemStack(KotaItems.PERSIK_ABADI);
					if (!player.getInventory().add(peach)) {
						Kota.drop(player, peach);
					}
					return 1;
				}))
				.then(Commands.literal("titah").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					CompoundTag edict = KotaAbadi.edict(player);
					if (edict.isEmpty()) {
						edict = new CompoundTag();
						edict.putLong("week", Kota.day(ctx.getSource().getServer()) / KotaAbadi.WEEK);
						edict.putInt("kind", 2);
						KotaData.get(ctx.getSource().getServer()).player(player.getUUID()).put("titah", edict);
					}
					edict.putBoolean("done", true);
					KotaData.get(ctx.getSource().getServer()).touch();
					ok(ctx, "kultivator.kota.uji.titah");
					return 1;
				}));
	}

	private static void ok(CommandContext<CommandSourceStack> ctx, String key, Object... args) {
		ctx.getSource().sendSuccess(() -> Component.translatable(key, args).withStyle(ChatFormatting.AQUA), false);
	}

	private KotaCommands() {
	}
}
