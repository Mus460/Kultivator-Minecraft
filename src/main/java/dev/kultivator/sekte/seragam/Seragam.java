package dev.kultivator.sekte.seragam;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.api.FormasiAnchor;
import dev.kultivator.sect.build.api.SiteView;
import dev.kultivator.sect.hall.HallData;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteContent;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.Lambang;
import dev.kultivator.sekte.api.LambangService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.util.Advancements;
import dev.kultivator.sekte.SekteSuara;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Module S, Seragam, Lambang &amp; Bendera (DESIGN 36.2.8, items 46 and 47). The hall master designs the sect's emblem
 * (shape, symbol, three colours, motto) and its uniform (body and trim colour and cut per sect rank) in the client's
 * designer; the design is saved in the hall section {@code sekte_seragam} ({@link LambangData}), the first change of a
 * world day is free, each further one costs {@link SeragamConfig#ubahPK} PK from the hall pool. The emblem shows on the
 * banner cloths of the flagpoles that module S adds to every template ({@link PanjiKomponen}, built from deposited
 * materials like any component), on module T's arena poles when they exist, as a decal on the Titik Formasi plots and the
 * Bagua disc, and as the embroidery of the disciples' robes; NPC sects of module D fly standards with their seeded emblem
 * at their mountain gates. Everything but the poles is drawn by the client: no block changes outside the build engine.
 * <p>
 * Contracts: C36.S1 {@link SekteApi#setLambang}; C36.S2 fires {@link SekteBus#EMBLEM_CHANGED}; C36.S3 anchors
 * {@code panji_gerbang_n}, {@code panji_formasi_n} and cloth on {@code panji_arena_n}; C36.S4 NPC standards from
 * {@code DiplomasiService.emblem/gate} without importing module D. Listens to {@code HEIR_NAMED} and {@code SUCCESSION}
 * (the generation count stitched along the banners' top hem).
 * <p>
 * Sync (weak laptops): every 40 ticks each player gets the designs that matter to him, at most
 * {@link #MAX_KEYS_PER_PLAYER}: his own hall, the halls within {@link SeragamConfig#jarakSinkron} blocks (with banners and
 * decals), the masters of disciples within 48 blocks (robes only) and the nearest two NPC sect gates; a payload is sent
 * only when a key's signature changed, and {@link SeragamPayloads.LambangForget} when it left the range.
 */
public final class Seragam {

	public static final int MAX_KEYS_PER_PLAYER = 4;
	private static final int SYNC_TICKS = 40;
	private static final double DISCIPLE_RANGE = 48.0D;
	private static final int NPC_KEYS = 2;
	private static final long REQUEST_COOLDOWN_MS = 750L;

	/** What each player was last sent: key → signature. */
	private static final Map<UUID, Map<String, Integer>> SENT = new HashMap<>();
	/** Test banners ({@code uji perguruan seragam bendera}): player → expiry tick. */
	private static final Map<UUID, Long> PREVIEWS = new HashMap<>();
	/** NPC standards found near a gate (read-only heightmap scan), by sect id. */
	private static final Map<String, List<SeragamPayloads.Banner>> NPC_STANDARDS = new HashMap<>();
	private static final Map<UUID, Long> LAST_SET = new HashMap<>();
	private static long ticks;
	private static boolean initialised;

	/** C36.S1: the emblem and uniform of each hall. */
	static final LambangService SERVICE = new LambangService() {
		@Override
		public Lambang emblem(UUID owner) {
			return LambangData.get(owner).emblem();
		}

		@Override
		public int robeColor(UUID owner, int sectRank) {
			return SeragamConfig.get().jubah ? LambangData.get(owner).robeRgb(sectRank) : -1;
		}

		@Override
		public int trimColor(UUID owner, int sectRank) {
			return SeragamConfig.get().jubah ? LambangData.get(owner).trimRgb(sectRank) : -1;
		}

		@Override
		public String motto(UUID owner) {
			return LambangData.get(owner).motto();
		}
	};

	/** From {@code SekteContent.init()} (its module line). */
	private static boolean content;

	/** Registry content (K1): the banner component and the payload types, always, whatever the switches say. */
	public static void registerContent() {
		if (content) {
			return;
		}
		content = true;
		SeragamConfig.load();
		PanjiKomponen.register();
		PayloadTypeRegistry.clientboundPlay().register(SeragamPayloads.LambangSync.TYPE, SeragamPayloads.LambangSync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SeragamPayloads.LambangForget.TYPE, SeragamPayloads.LambangForget.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SeragamPayloads.LambangOpen.TYPE, SeragamPayloads.LambangOpen.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SeragamPayloads.SeragamDebug.TYPE, SeragamPayloads.SeragamDebug.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SeragamPayloads.LambangSet.TYPE, SeragamPayloads.LambangSet.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SeragamPayloads.LambangRequest.TYPE, SeragamPayloads.LambangRequest.CODEC);
	}

	public static void init() {
		registerContent();
		if (initialised || !SekteContent.on(SekteIds.S)) {
			return;
		}
		initialised = true;
		SekteApi.setLambang(SERVICE);

		ServerPlayNetworking.registerGlobalReceiver(SeragamPayloads.LambangSet.TYPE, (payload, context) -> onSet(context.player(), payload.data()));
		ServerPlayNetworking.registerGlobalReceiver(SeragamPayloads.LambangRequest.TYPE, (payload, context) -> openDesigner(context.player()));

		ServerTickEvents.END_SERVER_TICK.register(Seragam::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SENT.remove(handler.getPlayer().getUUID()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUUID();
			SENT.remove(id);
			PREVIEWS.remove(id);
			LAST_SET.remove(id);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAll());
		ServerLifecycleEvents.SERVER_STARTED.register(server -> clearAll());

		SekteBus.HEIR_NAMED.register((server, owner, soul, previous) -> {
			LambangData.setHeir(owner, soul);
			SekteLog.log(server, owner, SekteIds.S, Component.translatable("kultivator.sekte.seragam.log.heir"));
		});
		SekteBus.SUCCESSION.register((server, owner, newMaster) -> {
			SeragamData data = LambangData.get(owner);
			if (LambangData.put(owner, data.withGeneration(data.generation() + 1))) {
				SekteLog.log(server, owner, SekteIds.S, Component.translatable("kultivator.sekte.seragam.log.generation", data.generation() + 1));
				changed(server, owner);
			}
		});
		SeragamCommands.init();
	}

	private static void clearAll() {
		SENT.clear();
		PREVIEWS.clear();
		NPC_STANDARDS.clear();
		LAST_SET.clear();
		LambangData.clearCache();
		ticks = 0L;
	}

	// ================================================================== changing a design

	/** The result of a save. */
	public enum Result {
		SAVED_FREE,
		SAVED_PAID,
		UNCHANGED,
		NOT_MASTER,
		NO_HALL,
		NO_PK
	}

	/** Free changes left today for {@code owner}'s hall. */
	public static int freeLeft(MinecraftServer server, UUID owner) {
		return Math.max(0, SeragamConfig.get().ubahGratisPerHari - LambangData.changesOn(owner, Ingatan.today(server)));
	}

	private static void onSet(ServerPlayer player, SeragamData requested) {
		long now = System.currentTimeMillis();
		Long last = LAST_SET.get(player.getUUID());
		if (last != null && now - last < REQUEST_COOLDOWN_MS) {
			return;
		}
		LAST_SET.put(player.getUUID(), now);
		Result result = save(player.level().getServer(), player, player.getUUID(), requested, true);
		Component message = switch (result) {
			case SAVED_FREE -> Component.translatable("kultivator.sekte.seragam.saved.free").withStyle(ChatFormatting.GREEN);
			case SAVED_PAID -> Component.translatable("kultivator.sekte.seragam.saved.paid", SeragamConfig.get().ubahPK).withStyle(ChatFormatting.GREEN);
			case UNCHANGED -> Component.translatable("kultivator.sekte.seragam.unchanged").withStyle(ChatFormatting.GRAY);
			case NOT_MASTER -> Component.translatable("kultivator.sekte.seragam.not_master").withStyle(ChatFormatting.RED);
			case NO_HALL -> Component.translatable("kultivator.sekte.seragam.no_hall").withStyle(ChatFormatting.RED);
			case NO_PK -> Component.translatable("kultivator.sekte.seragam.no_pk", SeragamConfig.get().ubahPK).withStyle(ChatFormatting.RED);
		};
		player.sendSystemMessage(message);
	}

	/**
	 * Saves {@code requested} as {@code owner}'s design for {@code player} (the master); {@code charge} applies the daily
	 * allowance and the PK cost (test commands pass false).
	 */
	public static Result save(MinecraftServer server, @Nullable ServerPlayer player, UUID owner, SeragamData requested, boolean charge) {
		if (player != null && charge && !player.getUUID().equals(owner)) {
			return Result.NOT_MASTER;
		}
		if (Halls.hallOf(owner) == null) {
			return Result.NO_HALL;
		}
		SeragamData old = LambangData.get(owner);
		if (old.sameDesign(requested) && old.designed()) {
			return Result.UNCHANGED;
		}
		boolean paid = false;
		if (charge && server != null) {
			long today = Ingatan.today(server);
			if (freeLeft(server, owner) <= 0) {
				int cost = SeragamConfig.get().ubahPK;
				if (cost > 0 && !Halls.spendPool(owner, cost)) {
					return Result.NO_PK;
				}
				paid = cost > 0;
			}
			LambangData.countChange(owner, today);
		}
		SeragamData saved = new SeragamData(requested.lambang(), requested.motto(), requested.robe(), requested.trim(), requested.cut(), old.generation(),
				old.flags() | SeragamData.DESIGNED);
		LambangData.put(owner, saved);
		if (server != null) {
			afterSave(server, owner, old, saved, player);
		}
		return paid ? Result.SAVED_PAID : Result.SAVED_FREE;
	}

	/** Bus, log, memories, advancement and an immediate re-sync after a saved change. */
	static void afterSave(MinecraftServer server, UUID owner, SeragamData old, SeragamData saved, @Nullable ServerPlayer master) {
		if (old.lambang() != saved.lambang() || !old.designed()) {
			SekteBus.EMBLEM_CHANGED.invoker().onEmblemChanged(server, owner, saved.emblem());
		}
		SekteLog.log(server, owner, SekteIds.S, Component.translatable("kultivator.sekte.seragam.log.changed",
				saved.motto().isEmpty() ? Component.translatable("kultivator.sekte.seragam.no_motto") : Component.literal(saved.motto())));
		// the disciples whose robes changed remember the new uniform (a small, lasting joy for module P to read)
		boolean thanked = false;
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (disciple.role() != DiscipleEntity.Role.DISCIPLE) {
				continue;
			}
			int rank = SeragamData.clampRank(disciple.syncedSectRank());
			if (old.robeIndex(rank) != saved.robeIndex(rank) || old.trimIndex(rank) != saved.trimIndex(rank) || old.cutOf(rank) != saved.cutOf(rank)
					|| old.lambang() != saved.lambang()) {
				Ingatan.remember(server, disciple.data(), "seragam.jubah_baru", null, 2, "kultivator.sekte.seragam.ingat.jubah",
						saved.motto());
				if (!thanked && master != null && disciple.distanceToSqr(master) <= 16.0D * 16.0D) {
					thanked = SekteSuara.line(disciple, "robe", "thanks", 1200);
				}
			}
		}
		if (master != null) {
			Advancements.grant(master, SekteIds.ADVANCEMENT_FOLDER + "seragam_lambang");
		}
		changed(server, owner);
	}

	/** Forces every player's next sync to resend {@code owner}'s design. */
	static void changed(MinecraftServer server, UUID owner) {
		String key = key(owner);
		for (Map<String, Integer> sent : SENT.values()) {
			sent.remove(key);
		}
		syncAll(server);
	}

	// ================================================================== designer

	/** Sends the player's own design and opens the designer (command, hub card). */
	public static void openDesigner(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		boolean hall = Halls.hallOf(owner) != null;
		send(player, owner, true);
		int flags = (hall ? SeragamPayloads.LambangOpen.OPEN_EDIT | SeragamPayloads.LambangOpen.OPEN_HALL : 0);
		ServerPlayNetworking.send(player, new SeragamPayloads.LambangOpen(key(owner), hall ? freeLeft(server, owner) : 0, SeragamConfig.get().ubahPK,
				hall ? poolOf(server, owner) : 0, flags));
	}

	/** The hall pool (PK) of {@code owner}, read from the hall data's core compound (shown in the designer). */
	private static int poolOf(MinecraftServer server, UUID owner) {
		net.minecraft.nbt.CompoundTag core = HallData.get(server).core(owner);
		return core == null ? 0 : Math.max(0, core.getIntOr("pool", 0));
	}

	// ================================================================== sync

	public static String key(UUID owner) {
		return "u:" + owner;
	}

	private static void tick(MinecraftServer server) {
		ticks++;
		if (ticks % SYNC_TICKS != 0) {
			return;
		}
		try {
			syncAll(server);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Sekte seragam sync failed", exception);
		}
	}

	private static void syncAll(MinecraftServer server) {
		List<UUID> owners = HallData.get(server).owners();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			syncPlayer(server, player, owners);
		}
	}

	private record Want(String key, @Nullable UUID owner, @Nullable String npc, boolean banners, double dist2) {
	}

	private static void syncPlayer(MinecraftServer server, ServerPlayer player, List<UUID> owners) {
		SeragamConfig config = SeragamConfig.get();
		double range = config.jarakSinkron;
		Map<String, Want> want = new LinkedHashMap<>();
		UUID self = player.getUUID();
		if (Halls.hallOf(self) != null) {
			want.put(key(self), new Want(key(self), self, null, near(player, self, range), -1.0D));
		}
		// halls in range, nearest first
		List<Want> halls = new ArrayList<>();
		for (UUID owner : owners) {
			if (owner.equals(self)) {
				continue;
			}
			Halls.HallView hall = Halls.hallOf(owner);
			if (hall == null || !hall.dim().equals(player.level().dimension())) {
				continue;
			}
			double d2 = player.distanceToSqr(hall.pos().getX() + 0.5D, hall.pos().getY(), hall.pos().getZ() + 0.5D);
			if (d2 <= range * range) {
				halls.add(new Want(key(owner), owner, null, true, d2));
			}
		}
		halls.sort((a, b) -> Double.compare(a.dist2(), b.dist2()));
		for (Want w : halls) {
			if (want.size() >= MAX_KEYS_PER_PLAYER) {
				break;
			}
			want.putIfAbsent(w.key(), w);
		}
		// masters of the disciples around (robes only)
		if (want.size() < MAX_KEYS_PER_PLAYER && config.jubah) {
			Set<UUID> masters = new HashSet<>();
			for (DiscipleEntity disciple : player.level().getEntitiesOfClass(DiscipleEntity.class, player.getBoundingBox().inflate(DISCIPLE_RANGE))) {
				UUID owner = disciple.ownerId();
				if (owner != null && disciple.role() == DiscipleEntity.Role.DISCIPLE && Halls.hallOf(owner) != null) {
					masters.add(owner);
				}
			}
			for (UUID owner : masters) {
				if (want.size() >= MAX_KEYS_PER_PLAYER) {
					break;
				}
				want.putIfAbsent(key(owner), new Want(key(owner), owner, null, false, Double.MAX_VALUE));
			}
		}
		// NPC sects of module D (their seeded emblems at their gates)
		DiplomasiService diplomacy = SekteApi.diplomasi();
		if (config.panjiSekteNpc && diplomacy != DiplomasiService.NONE && player.level().dimension().equals(Level.OVERWORLD)) {
			List<Want> npcs = new ArrayList<>();
			for (String sect : diplomacy.sects()) {
				BlockPos gate = diplomacy.gate(sect);
				if (gate == null) {
					continue;
				}
				double d2 = player.distanceToSqr(gate.getX() + 0.5D, gate.getY(), gate.getZ() + 0.5D);
				if (d2 <= range * range) {
					npcs.add(new Want("npc:" + sect, null, sect, true, d2));
				}
			}
			npcs.sort((a, b) -> Double.compare(a.dist2(), b.dist2()));
			for (int i = 0; i < Math.min(NPC_KEYS, npcs.size()); i++) {
				want.putIfAbsent(npcs.get(i).key(), npcs.get(i));
			}
		}
		Map<String, Integer> sent = SENT.computeIfAbsent(self, id -> new HashMap<>());
		for (Want w : want.values()) {
			SeragamPayloads.LambangSync sync = w.owner() != null ? hallSync(server, w.owner(), w.banners()) : npcSync(server, w.npc());
			if (sync == null) {
				continue;
			}
			int signature = signature(sync);
			Integer before = sent.get(w.key());
			if (before == null || before != signature) {
				ServerPlayNetworking.send(player, sync);
				sent.put(w.key(), signature);
			}
			if (w.owner() != null && w.owner().equals(self) && !sync.banners().isEmpty()) {
				checkBannerAdvancement(player, sync);
			}
		}
		for (String key : List.copyOf(sent.keySet())) {
			if (!want.containsKey(key)) {
				sent.remove(key);
				ServerPlayNetworking.send(player, new SeragamPayloads.LambangForget(key));
			}
		}
		// the test banner
		Long until = PREVIEWS.get(self);
		if (until != null && ticks > until) {
			PREVIEWS.remove(self);
		}
	}

	private static boolean near(ServerPlayer player, UUID owner, double range) {
		Halls.HallView hall = Halls.hallOf(owner);
		return hall != null && hall.dim().equals(player.level().dimension())
				&& player.distanceToSqr(hall.pos().getX() + 0.5D, hall.pos().getY(), hall.pos().getZ() + 0.5D) <= range * range;
	}

	private static int signature(SeragamPayloads.LambangSync sync) {
		return Objects.hash(sync.version(), sync.dim(), sync.data(), sync.banners(), sync.decals(), sync.maxBanners(), sync.animRange());
	}

	private static void send(ServerPlayer player, UUID owner, boolean banners) {
		SeragamPayloads.LambangSync sync = hallSync(player.level().getServer(), owner, banners && near(player, owner, SeragamConfig.get().jarakSinkron));
		if (sync != null) {
			ServerPlayNetworking.send(player, sync);
			SENT.computeIfAbsent(player.getUUID(), id -> new HashMap<>()).put(sync.key(), signature(sync));
		}
	}

	/** The sync of a player's hall: the design, and (when {@code withBanners}) the banners and decals of its site. */
	static @Nullable SeragamPayloads.LambangSync hallSync(MinecraftServer server, UUID owner, boolean withBanners) {
		SeragamConfig config = SeragamConfig.get();
		SeragamData data = LambangData.get(owner);
		if (!config.jubah) {
			data = data.withFlags(data.flags() | SeragamData.NO_ROBE);
		}
		List<SeragamPayloads.Banner> banners = new ArrayList<>();
		List<SeragamPayloads.Decal> decals = new ArrayList<>();
		String dim = "";
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall != null) {
			dim = hall.dim().identifier().toString();
		}
		if (withBanners) {
			EngineApi engine = EngineApi.get();
			SiteView site = engine.site(owner);
			if (site != null) {
				dim = site.dim().identifier().toString();
				BlockPos stele = site.stele();
				BlockPos formation = engine.anchor(owner, "formation");
				BlockPos arena = engine.anchor(owner, "arena");
				BlockPos g0 = engine.anchor(owner, PanjiKomponen.GATE_ANCHOR + 0);
				BlockPos g1 = engine.anchor(owner, PanjiKomponen.GATE_ANCHOR + 1);
				for (BlockPos gate : new BlockPos[]{g0, g1}) {
					if (gate != null && banners.size() < config.benderaMaks) {
						BlockPos other = gate == g0 ? g1 : g0;
						BlockPos toward = other != null ? new BlockPos((gate.getX() + other.getX()) / 2, gate.getY(), (gate.getZ() + other.getZ()) / 2) : stele;
						banners.add(new SeragamPayloads.Banner(gate, yaw(gate, toward), SeragamPayloads.BANNER_GATE));
					}
				}
				for (int i = 0; i < PanjiKomponen.DISC_POLES && banners.size() < config.benderaMaks; i++) {
					BlockPos pole = engine.anchor(owner, PanjiKomponen.DISC_ANCHOR + i);
					if (pole != null) {
						banners.add(new SeragamPayloads.Banner(pole, yaw(pole, formation != null ? formation : stele), SeragamPayloads.BANNER_FORMATION));
					}
				}
				for (int i = 0; i < PanjiKomponen.ARENA_POLES && banners.size() < config.benderaMaks; i++) {
					BlockPos pole = engine.anchor(owner, PanjiKomponen.ARENA_ANCHOR + i);
					if (pole != null) {
						banners.add(new SeragamPayloads.Banner(pole, yaw(pole, arena != null ? arena : stele), SeragamPayloads.BANNER_ARENA));
					}
				}
				if (config.lambangFormasi) {
					if (formation != null) {
						decals.add(new SeragamPayloads.Decal(formation, 3));
					}
					for (FormasiAnchor anchor : engine.formasiAnchors(owner)) {
						if (decals.size() >= SeragamPayloads.MAX_DECALS) {
							break;
						}
						decals.add(new SeragamPayloads.Decal(anchor.centre().above(), Math.max(1, Math.min(5, anchor.radius() * 2 - 1))));
					}
				}
			}
			// the test banner of the master in front of him
			for (Map.Entry<UUID, Long> preview : PREVIEWS.entrySet()) {
				if (!preview.getKey().equals(owner) || ticks > preview.getValue()) {
					continue;
				}
				ServerPlayer master = server.getPlayerList().getPlayer(owner);
				if (master != null) {
					dim = master.level().dimension().identifier().toString();
					BlockPos at = BlockPos.containing(master.getX() + master.getLookAngle().x * 3.0D, master.getY(), master.getZ() + master.getLookAngle().z * 3.0D);
					banners.add(new SeragamPayloads.Banner(at, yaw(at, master.blockPosition()), SeragamPayloads.BANNER_STANDARD));
				}
			}
		}
		return new SeragamPayloads.LambangSync(key(owner), LambangData.version(owner), dim, data, banners, decals, config.benderaMaks, config.jarakAnimasi, 0);
	}

	/** An NPC sect's emblem and the standards at its gate (module D's data; positions read once, never changed). */
	private static @Nullable SeragamPayloads.LambangSync npcSync(MinecraftServer server, String sect) {
		DiplomasiService diplomacy = SekteApi.diplomasi();
		BlockPos gate = diplomacy.gate(sect);
		if (gate == null) {
			return null;
		}
		List<SeragamPayloads.Banner> standards = NPC_STANDARDS.get(sect);
		if (standards == null) {
			standards = findStandards(server.overworld(), gate);
			if (standards == null) {
				return null;
			}
			NPC_STANDARDS.put(sect, standards);
		}
		Lambang emblem = diplomacy.emblem(sect);
		SeragamData data = SeragamData.DEFAULT.withEmblem(emblem).withFlags(SeragamData.DESIGNED)
				.withGeneration(Math.max(1, diplomacy.tier(sect)));
		return new SeragamPayloads.LambangSync("npc:" + sect, emblem.pack(), Level.OVERWORLD.identifier().toString(), data, standards, List.of(),
				SeragamConfig.get().benderaMaks, SeragamConfig.get().jarakAnimasi, 0);
	}

	/**
	 * Two free-standing standards flanking an NPC gate: the first two of eight spots 6-8 blocks around it whose ground is
	 * dry, solid and within 6 blocks of the gate's height with 5 free blocks above. Null while the chunks are not loaded
	 * (tried again on the next sync). Reads the world only.
	 */
	private static @Nullable List<SeragamPayloads.Banner> findStandards(ServerLevel level, BlockPos gate) {
		int[][] spots = {{7, 0}, {-7, 0}, {0, 7}, {0, -7}, {6, 6}, {-6, -6}, {6, -6}, {-6, 6}, {8, 3}, {-8, -3}};
		List<SeragamPayloads.Banner> found = new ArrayList<>(2);
		boolean anyLoaded = false;
		for (int[] spot : spots) {
			int x = gate.getX() + spot[0];
			int z = gate.getZ() + spot[1];
			if (!level.hasChunkAt(x, z)) {
				continue;
			}
			anyLoaded = true;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos at = new BlockPos(x, y, z);
			BlockState ground = level.getBlockState(at.below());
			if (Math.abs(y - gate.getY()) > 6 || ground.isAir() || !ground.getFluidState().isEmpty()) {
				continue;
			}
			boolean clear = true;
			for (int i = 0; i < 5 && clear; i++) {
				clear = level.isEmptyBlock(at.above(i));
			}
			if (!clear) {
				continue;
			}
			// the second standard must stand on the other side of the gate
			if (!found.isEmpty()) {
				BlockPos first = found.get(0).pos();
				if ((long) (first.getX() - gate.getX()) * spot[0] + (long) (first.getZ() - gate.getZ()) * spot[1] > 0) {
					continue;
				}
			}
			found.add(new SeragamPayloads.Banner(at, yaw(at, gate), SeragamPayloads.BANNER_STANDARD));
			if (found.size() == 2) {
				break;
			}
		}
		return anyLoaded ? found : null;
	}

	/** Yaw from {@code from} toward {@code to} in 1/256 turns, 0 = +z (south), 64 = -x (west), as {@code Mth} yaw. */
	static int yaw(BlockPos from, @Nullable BlockPos to) {
		if (to == null) {
			return 0;
		}
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		if (dx == 0.0D && dz == 0.0D) {
			return 0;
		}
		double degrees = Math.toDegrees(Math.atan2(-dx, dz));
		return (int) Math.round(degrees / 360.0D * 256.0D) & 0xFF;
	}

	private static void checkBannerAdvancement(ServerPlayer player, SeragamPayloads.LambangSync sync) {
		for (SeragamPayloads.Banner banner : sync.banners()) {
			if (banner.kind() == SeragamPayloads.BANNER_STANDARD) {
				continue;
			}
			if (player.blockPosition().distSqr(banner.pos()) <= 24 * 24) {
				Advancements.grant(player, SekteIds.ADVANCEMENT_FOLDER + "seragam_bendera");
				if (LambangData.markOnce(player.getUUID(), "suara_bendera")) {
					SekteSuara.inner(player, "elder.banner", "elder.welcome");
				}
				return;
			}
		}
	}

	// ================================================================== tests

	/** {@code uji perguruan seragam bendera}: a standard with the master's emblem 3 blocks ahead for 30 s. */
	static void preview(ServerPlayer player) {
		PREVIEWS.put(player.getUUID(), ticks + 600L);
		SENT.computeIfAbsent(player.getUUID(), id -> new HashMap<>()).remove(key(player.getUUID()));
		SeragamPayloads.LambangSync sync = hallSync(player.level().getServer(), player.getUUID(), true);
		if (sync != null) {
			ServerPlayNetworking.send(player, sync);
		}
	}

	static void debugAtlas(ServerPlayer player) {
		ServerPlayNetworking.send(player, new SeragamPayloads.SeragamDebug(SeragamPayloads.SeragamDebug.DEBUG_ATLAS));
	}

	/** Info lines for {@code uji perguruan seragam info}. */
	static List<Component> info(MinecraftServer server, UUID owner) {
		SeragamData data = LambangData.get(owner);
		Lambang emblem = data.emblem();
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("kultivator.sekte.seragam.info.emblem", emblem.shape(), emblem.symbol(), emblem.field(), emblem.charge(),
				emblem.border(), Integer.toHexString(data.lambang())));
		lines.add(Component.translatable("kultivator.sekte.seragam.info.motto", data.motto(), data.generation(), LambangData.version(owner),
				freeLeft(server, owner)));
		for (int rank = 0; rank < SeragamData.RANKS; rank++) {
			lines.add(Component.translatable("kultivator.sekte.seragam.info.rank", rank, data.robeIndex(rank), data.trimIndex(rank), data.cutOf(rank)));
		}
		SeragamPayloads.LambangSync sync = hallSync(server, owner, true);
		lines.add(Component.translatable("kultivator.sekte.seragam.info.banners", sync == null ? 0 : sync.banners().size(),
				sync == null ? 0 : sync.decals().size()));
		return lines;
	}

	private Seragam() {
	}
}
