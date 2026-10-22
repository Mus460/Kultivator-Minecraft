package dev.kultivator.dunia.peta;

import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.DuniaConfig;
import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.DuniaSpine;
import dev.kultivator.dunia.kapal.KapalVoice;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.Sects;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Peta Dunia Kultivasi (DESIGN 34.5.16, idea 67, module T), server side: the coarse biome grid (sampled by noise, never
 * loading a chunk: {@code ServerLevel.getNoiseBiome} reads a loaded chunk and otherwise the level's uncached biome
 * resolver), the fog of war (cells within 96 blocks of a player, every 40 ticks), waypoints, the marker sheet and the map
 * exchange of two players holding maps. The client paints everything; the server only sends regions of 32 × 32 cell
 * bytes (unexplored cells blanked) and one marker sheet. Initialised by {@code Kapal.init()} (module T's one line).
 * <p>
 * Sumber: Shan Hai Jing; terinspirasi dari Alam Gunung dan Laut di I Shall Seal the Heavens (lang
 * {@code kultivator.peta.peta.source}).
 */
public final class Peta {

	public static final double EXPLORE_RADIUS = 96.0D;
	/** Known sites for the {@code dunia/sites_15} advancement (Sembilan Gunung, Sembilan Laut). */
	public static final int SITES_MANY = 15;
	public static final int LIVE_RANGE = 1536;
	private static final long SAMPLE_NANOS = 1_500_000L;
	private static final int REGION_PUSH_TICKS = 10;
	private static final int INFO_PUSH_TICKS = 100;
	private static final String BAWAH = "kultivator:dunia_bawah";

	private static PetaConfig config = new PetaConfig();
	private static boolean initialised;

	/** A player with the map screen open. */
	private static final class Watch {
		String dim;
		final LongOpenHashSet regions = new LongOpenHashSet();
		final LongOpenHashSet dirty = new LongOpenHashSet();
		boolean infoDirty;
		long lastInfo;
		long lastPush;
		long opened;

		Watch(String dim) {
			this.dim = dim;
		}
	}

	private static final Map<UUID, Watch> WATCH = new HashMap<>();
	private static final Map<String, LongLinkedOpenHashSet> QUEUE = new HashMap<>();
	private static final Map<UUID, CompoundTag> LAST_SURFACE = new HashMap<>();
	private static final Map<Long, Long> EXCHANGES = new HashMap<>();
	private static int sampled;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		config = DuniaConfig.section("peta", PetaConfig.class, PetaConfig::new, PetaConfig::clamp);
		PetaItems.init();
		PetaNet.register();
		PetaTests.register();
		DuniaCommands.player("peta", () -> Commands.literal("peta").executes(ctx -> {
			open(ctx.getSource().getPlayerOrException());
			return 1;
		}));
		ServerTickEvents.END_SERVER_TICK.register(Peta::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(Peta::checkCellSize);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WATCH.clear();
			QUEUE.clear();
			LAST_SURFACE.clear();
			EXCHANGES.clear();
		});
		DuniaConfig.onReload(() -> {
			MinecraftServer server = currentServer;
			if (server != null) {
				checkCellSize(server);
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> currentServer = server);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> currentServer = null);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer self)
					|| !(entity instanceof ServerPlayer other)) {
				return InteractionResult.PASS;
			}
			return exchange(self, other) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
		});
		// every world event the map shows: watchers get a fresh sheet within a second
		DuniaEvents.TREASURE_BORN.register((level, birth) -> touchAll());
		DuniaEvents.TREASURE_CLAIMED.register((level, birth, winner, rival) -> touchAll());
		DuniaEvents.SKY_EVENT.register((level, kind, started) -> touchAll());
		DuniaEvents.WEATHER_CHANGED.register((level, cell, weather) -> touchAll());
		DuniaEvents.SEAL_CHANGED.register((level, site, integrity, broken) -> touchAll());
		DuniaEvents.VOYAGE.register((captain, destination, arrived) -> touch(captain));
		DuniaEvents.SITE_DISCOVERED.register((player, site) -> {
			touch(player);
			int known = DuniaSites.known(player).size();
			if (known >= 5) {
				Advancements.grant(player, "dunia/sites_5");
			}
			if (known >= SITES_MANY) {
				Advancements.grant(player, "dunia/sites_15");
			}
			// the sky notes the new place (one line per 10 s at most: a map exchange writes many sites at once)
			KapalVoice.sky(player, KapalVoice.SITE_FOUND, null);
		});
		DuniaEvents.SOUL_JOURNEY.register(Peta::onSoul);
	}

	private static @Nullable MinecraftServer currentServer;

	public static PetaConfig config() {
		return config;
	}

	static boolean on() {
		return config.map && DuniaSpine.enabled();
	}

	private static void checkCellSize(MinecraftServer server) {
		PetaData data = PetaData.get(server);
		if (data.cellSize != config.cellSize) {
			data.resetCells(config.cellSize);
			QUEUE.clear();
		}
	}

	static String dimId(Level level) {
		return level.dimension().identifier().toString();
	}

	// ================================================================== opening

	/** Opens the map screen of {@code player} (the hub card, the key, {@code /kultivasi peta} and the scroll). */
	public static void open(ServerPlayer player) {
		if (!on()) {
			Msg.actionBar(player, Component.translatable("kultivator.peta.off").withColor(0xFF8A7A));
			return;
		}
		PetaNet.send(player, new PetaNet.Info(true, PetaSheet.build(player)));
	}

	static void onRequest(ServerPlayer player, PetaNet.Request request) {
		if (!on()) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		PetaData data = PetaData.get(server);
		String dim = dimId(player.level());
		switch (request.op()) {
			case PetaNet.OP_OPEN -> {
				Watch watch = WATCH.computeIfAbsent(player.getUUID(), id -> new Watch(dim));
				watch.dim = dim;
				watch.regions.clear();
				watch.opened = server.getTickCount();
				watch.lastInfo = server.getTickCount();
				Advancements.grant(player, "dunia/map_opened");
				explore(server, data, player);
				PetaNet.send(player, new PetaNet.Info(false, PetaSheet.build(player)));
			}
			case PetaNet.OP_CLOSE -> WATCH.remove(player.getUUID());
			case PetaNet.OP_REGIONS -> {
				Watch watch = WATCH.computeIfAbsent(player.getUUID(), id -> new Watch(dim));
				watch.dim = dim;
				for (long key : request.keys()) {
					watch.regions.add(key);
					sendRegion(player, data, dim, key);
				}
			}
			case PetaNet.OP_INFO -> PetaNet.send(player, new PetaNet.Info(false, PetaSheet.build(player)));
			case PetaNet.OP_WAYPOINT_ADD -> {
				addWaypoint(player, data, dim, request.x(), request.z(), request.text());
				PetaNet.send(player, new PetaNet.Info(false, PetaSheet.build(player)));
			}
			case PetaNet.OP_WAYPOINT_DELETE -> {
				removeWaypoint(data, player.getUUID(), request.x());
				PetaNet.send(player, new PetaNet.Info(false, PetaSheet.build(player)));
			}
			case PetaNet.OP_WAYPOINT_RENAME -> {
				renameWaypoint(data, player.getUUID(), request.x(), request.text());
				PetaNet.send(player, new PetaNet.Info(false, PetaSheet.build(player)));
			}
			case PetaNet.OP_SAIL -> {
				if (!dev.kultivator.dunia.kapal.KapalRohEntity.sailFromMap(player, request.x(), request.z(), request.text())) {
					Msg.actionBar(player, Component.translatable("kultivator.peta.sail.not_captain").withColor(0xFFD54A));
				}
			}
			default -> {
			}
		}
	}

	// ================================================================== regions

	/** Sends region {@code key} of {@code dim} blanked by the player's (and, when shared, the sect's) fog. */
	private static void sendRegion(ServerPlayer player, PetaData data, String dim, long key) {
		byte[] cells = data.region(dim, key);
		byte[] out = new byte[PetaData.CELLS];
		List<long[]> fogs = fogs(player, data, dim, key);
		if (!fogs.isEmpty()) {
			int rx = PetaData.regionX(key);
			int rz = PetaData.regionZ(key);
			int queued = 0;
			for (int i = 0; i < PetaData.CELLS; i++) {
				boolean explored = false;
				for (long[] bits : fogs) {
					if ((bits[i >> 6] & (1L << (i & 63))) != 0L) {
						explored = true;
						break;
					}
				}
				if (!explored) {
					continue;
				}
				byte value = cells == null ? 0 : cells[i];
				out[i] = value;
				if (value == 0 && queued < 256) {
					int cx = rx * PetaData.REGION + (i % PetaData.REGION);
					int cz = rz * PetaData.REGION + (i / PetaData.REGION);
					queue(dim, cx, cz);
					queued++;
				}
			}
		}
		PetaNet.send(player, new PetaNet.Region(dim, PetaData.regionX(key), PetaData.regionZ(key), data.cellSize, out));
	}

	private static List<long[]> fogs(ServerPlayer player, PetaData data, String dim, long key) {
		List<long[]> list = new ArrayList<>(2);
		long[] own = data.fog(player.getUUID(), dim, key);
		if (own != null) {
			list.add(own);
		}
		if (config.shareWithSect) {
			Sect sect = Sects.sectOf(player);
			if (sect != null) {
				for (Sect.Member member : sect.members()) {
					if (!member.id().equals(player.getUUID())) {
						long[] mate = data.fog(member.id(), dim, key);
						if (mate != null) {
							list.add(mate);
						}
					}
				}
			}
		}
		return list;
	}

	// ================================================================== fog of war and sampling

	private static void tick(MinecraftServer server) {
		if (!on()) {
			return;
		}
		PetaData data = PetaData.get(server);
		int now = server.getTickCount();
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		for (int i = 0; i < players.size(); i++) {
			ServerPlayer player = players.get(i);
			if ((now + i * 7) % 40 == 0 && !player.isSpectator()) {
				explore(server, data, player);
				if ((now / 40) % 5 == 0) {
					seeHalls(server, data, player);
				}
			}
		}
		sample(server, data);
		if (!WATCH.isEmpty()) {
			pushWatchers(server, data, now);
		}
		if (now % 1200 == 0) {
			EXCHANGES.entrySet().removeIf(entry -> now - entry.getValue() > 200L);
		}
	}

	/** Marks the cells within 96 blocks of the player as explored and queues the ones never sampled. */
	static void explore(MinecraftServer server, PetaData data, ServerPlayer player) {
		String dim = dimId(player.level());
		if (!BAWAH.equals(dim)) {
			CompoundTag at = new CompoundTag();
			at.putString("dim", dim);
			at.putInt("x", player.getBlockX());
			at.putInt("y", player.getBlockY());
			at.putInt("z", player.getBlockZ());
			LAST_SURFACE.put(player.getUUID(), at);
		}
		revealAround(server, data, player.getUUID(), dim, player.getX(), player.getZ(), EXPLORE_RADIUS);
	}

	/** Explores every cell whose centre lies within {@code radius} of x/z; returns how many were new. */
	static int revealAround(MinecraftServer server, PetaData data, UUID id, String dim, double x, double z, double radius) {
		int cs = data.cellSize;
		int c0x = Mth.floor((x - radius) / cs);
		int c1x = Mth.floor((x + radius) / cs);
		int c0z = Mth.floor((z - radius) / cs);
		int c1z = Mth.floor((z + radius) / cs);
		double reach = (radius + cs * 0.5D) * (radius + cs * 0.5D);
		int fresh = 0;
		Watch watch = WATCH.get(id);
		for (int cx = c0x; cx <= c1x; cx++) {
			for (int cz = c0z; cz <= c1z; cz++) {
				double dx = (cx + 0.5D) * cs - x;
				double dz = (cz + 0.5D) * cs - z;
				if (dx * dx + dz * dz > reach) {
					continue;
				}
				if (data.explore(id, dim, cx, cz)) {
					fresh++;
					if (data.cell(dim, cx, cz) == 0) {
						queue(dim, cx, cz);
					}
					if (watch != null && dim.equals(watch.dim)) {
						watch.dirty.add(PetaData.regionKey(Math.floorDiv(cx, PetaData.REGION), Math.floorDiv(cz, PetaData.REGION)));
					}
				}
			}
		}
		return fresh;
	}

	static void queue(String dim, int cx, int cz) {
		QUEUE.computeIfAbsent(dim, d -> new LongLinkedOpenHashSet()).add(PetaData.regionKey(cx, cz));
	}

	static int queued() {
		int n = 0;
		for (LongLinkedOpenHashSet set : QUEUE.values()) {
			n += set.size();
		}
		return n;
	}

	static int sampledSoFar() {
		return sampled;
	}

	/** Samples queued cells: at most {@code samplesPerTick} and 1.5 ms per tick, never loading a chunk. */
	private static void sample(MinecraftServer server, PetaData data) {
		if (QUEUE.isEmpty()) {
			return;
		}
		long end = System.nanoTime() + SAMPLE_NANOS;
		int budget = config.samplesPerTick;
		Iterator<Map.Entry<String, LongLinkedOpenHashSet>> dims = QUEUE.entrySet().iterator();
		while (dims.hasNext() && budget > 0) {
			Map.Entry<String, LongLinkedOpenHashSet> entry = dims.next();
			String dim = entry.getKey();
			ServerLevel level = levelOf(server, dim);
			LongLinkedOpenHashSet set = entry.getValue();
			if (level == null) {
				dims.remove();
				continue;
			}
			while (!set.isEmpty() && budget > 0) {
				long packed = set.removeFirstLong();
				int cx = PetaData.regionX(packed);
				int cz = PetaData.regionZ(packed);
				if (data.cell(dim, cx, cz) == 0) {
					data.setCell(dim, cx, cz, sampleCell(level, data.cellSize, cx, cz));
					sampled++;
					markDirty(dim, cx, cz);
				}
				budget--;
				if ((budget & 7) == 0 && System.nanoTime() > end) {
					budget = 0;
				}
			}
			if (set.isEmpty()) {
				dims.remove();
			}
		}
	}

	/** One cell: the biome at its centre (a loaded chunk's own, else noise) and, when loaded, its height band. */
	static byte sampleCell(ServerLevel level, int cs, int cx, int cz) {
		int x = cx * cs + cs / 2;
		int z = cz * cs + cs / 2;
		int band = 0;
		int y = Math.min(level.getMaxY() - 8, Math.max(level.getSeaLevel() + 96, level.getMinY() + 8));
		if (level.hasChunkAt(x, z)) {
			int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
			if (top > level.getMinY()) {
				band = PetaInk.bandOf(top, level.getSeaLevel());
				y = Math.min(level.getMaxY() - 1, top + 2);
			}
		}
		Holder<Biome> biome = level.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
		int kind = PetaInk.classify(biome);
		return PetaInk.pack(kind == 0 ? PetaInk.PLAINS : kind, band);
	}

	private static @Nullable ServerLevel levelOf(MinecraftServer server, String dim) {
		Identifier id = Identifier.tryParse(dim);
		return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	private static void markDirty(String dim, int cx, int cz) {
		if (WATCH.isEmpty()) {
			return;
		}
		long key = PetaData.regionKey(Math.floorDiv(cx, PetaData.REGION), Math.floorDiv(cz, PetaData.REGION));
		for (Watch watch : WATCH.values()) {
			if (dim.equals(watch.dim) && watch.regions.contains(key)) {
				watch.dirty.add(key);
			}
		}
	}

	private static void pushWatchers(MinecraftServer server, PetaData data, int now) {
		Iterator<Map.Entry<UUID, Watch>> it = WATCH.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Watch> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Watch watch = entry.getValue();
			if (player == null || now - watch.opened > 20 * 60 * 30) {
				it.remove();
				continue;
			}
			String dim = dimId(player.level());
			if (!dim.equals(watch.dim)) {
				watch.dim = dim;
				watch.regions.clear();
				watch.dirty.clear();
				watch.infoDirty = true;
			}
			if (!watch.dirty.isEmpty() && now - watch.lastPush >= REGION_PUSH_TICKS) {
				watch.lastPush = now;
				int sent = 0;
				LongOpenHashSet done = new LongOpenHashSet();
				for (long key : watch.dirty) {
					if (sent >= 8) {
						break;
					}
					if (watch.regions.contains(key)) {
						sendRegion(player, data, dim, key);
						sent++;
					}
					done.add(key);
				}
				watch.dirty.removeAll(done);
			}
			if ((watch.infoDirty && now - watch.lastInfo >= 20) || now - watch.lastInfo >= INFO_PUSH_TICKS) {
				watch.infoDirty = false;
				watch.lastInfo = now;
				PetaNet.send(player, new PetaNet.Info(false, PetaSheet.build(player)));
			}
		}
	}

	private static void touch(ServerPlayer player) {
		Watch watch = player == null ? null : WATCH.get(player.getUUID());
		if (watch != null) {
			watch.infoDirty = true;
		}
	}

	private static void touchAll() {
		for (Watch watch : WATCH.values()) {
			watch.infoDirty = true;
		}
	}

	/** Halls of online owners within 160 blocks become "seen" (the map then draws their territory). */
	private static void seeHalls(MinecraftServer server, PetaData data, ServerPlayer player) {
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other == player) {
				continue;
			}
			Halls.HallView hall = Halls.hallOf(other.getUUID());
			if (hall == null || !hall.dim().equals(player.level().dimension())) {
				continue;
			}
			double dx = hall.pos().getX() - player.getX();
			double dz = hall.pos().getZ() - player.getZ();
			if (dx * dx + dz * dz <= 160.0D * 160.0D + (double) hall.radius() * hall.radius()) {
				if (data.player(player.getUUID()).seenHalls.add(other.getUUID())) {
					data.setDirty();
					touch(player);
				}
			}
		}
	}

	private static void onSoul(ServerPlayer player, boolean entered) {
		PetaData data = PetaData.get(player.level().getServer());
		if (entered) {
			// the body lies where the soul left the living world: here, unless the soul already stands in the Dunia Bawah
			CompoundTag here = new CompoundTag();
			here.putString("dim", dimId(player.level()));
			here.putInt("x", player.getBlockX());
			here.putInt("y", player.getBlockY());
			here.putInt("z", player.getBlockZ());
			CompoundTag at = BAWAH.equals(dimId(player.level())) ? LAST_SURFACE.getOrDefault(player.getUUID(), here) : here;
			data.player(player.getUUID()).body = at.copy();
		} else {
			data.player(player.getUUID()).body = null;
		}
		data.setDirty();
		touch(player);
	}

	// ================================================================== waypoints

	/** The player's waypoints in {@code dimension} (each {@code name}, {@code x}, {@code z}, {@code color}, {@code i}). */
	public static List<CompoundTag> waypointsFor(ServerPlayer player, ResourceKey<Level> dimension) {
		List<CompoundTag> list = new ArrayList<>();
		PetaData.Player entry = PetaData.get(player.level().getServer()).peek(player.getUUID());
		if (entry == null) {
			return list;
		}
		String dim = dimension.identifier().toString();
		for (int i = 0; i < entry.waypoints.size(); i++) {
			if (entry.waypoints.get(i) instanceof CompoundTag wp && dim.equals(wp.getStringOr("dim", ""))) {
				CompoundTag copy = wp.copy();
				copy.putInt("i", i);
				list.add(copy);
			}
		}
		return list;
	}

	private static final int[] WAYPOINT_COLORS = {0xB3281D, 0xD4A637, 0x2E8963, 0x3C7FD6, 0x8E5BD8, 0xE88E7A, 0x2A2F35, 0x67C99B};

	private static void addWaypoint(ServerPlayer player, PetaData data, String dim, int x, int z, String name) {
		PetaData.Player entry = data.player(player.getUUID());
		if (entry.waypoints.size() >= config.waypoints) {
			Msg.actionBar(player, Component.translatable("kultivator.peta.waypoint.full", config.waypoints).withColor(0xFFD54A));
			return;
		}
		CompoundTag wp = new CompoundTag();
		wp.putString("dim", dim);
		wp.putInt("x", Mth.clamp(x, -29_000_000, 29_000_000));
		wp.putInt("z", Mth.clamp(z, -29_000_000, 29_000_000));
		String clean = name == null ? "" : name.strip();
		if (clean.length() > 24) {
			clean = clean.substring(0, 24);
		}
		wp.putString("name", clean);
		wp.putInt("color", WAYPOINT_COLORS[entry.waypoints.size() % WAYPOINT_COLORS.length]);
		entry.waypoints.add(wp);
		data.setDirty();
	}

	private static void removeWaypoint(PetaData data, UUID id, int index) {
		PetaData.Player entry = data.peek(id);
		if (entry != null && index >= 0 && index < entry.waypoints.size()) {
			entry.waypoints.remove(index);
			data.setDirty();
		}
	}

	private static void renameWaypoint(PetaData data, UUID id, int index, String name) {
		PetaData.Player entry = data.peek(id);
		if (entry != null && index >= 0 && index < entry.waypoints.size() && entry.waypoints.get(index) instanceof CompoundTag wp) {
			String clean = name == null ? "" : name.strip();
			wp.putString("name", clean.length() > 24 ? clean.substring(0, 24) : clean);
			data.setDirty();
		}
	}

	// ================================================================== the map exchange

	/** Two players holding maps who right-click each other merge their known sites (not their fog). */
	private static boolean exchange(ServerPlayer self, ServerPlayer other) {
		if (!on() || !holdsMap(self) || !holdsMap(other)) {
			return false;
		}
		long pair = pairKey(self.getUUID(), other.getUUID());
		long now = self.level().getServer().getTickCount();
		Long last = EXCHANGES.get(pair);
		if (last != null && now - last < 100L) {
			return true;
		}
		EXCHANGES.put(pair, now);
		int toOther = give(self, other);
		int toSelf = give(other, self);
		if (toOther + toSelf > 0) {
			Advancements.grant(self, "dunia/map_exchange");
			Advancements.grant(other, "dunia/map_exchange");
		}
		Msg.actionBar(self, Component.translatable("kultivator.peta.exchange", other.getName(), toSelf, toOther).withColor(0x9FE0C0));
		Msg.actionBar(other, Component.translatable("kultivator.peta.exchange", self.getName(), toOther, toSelf).withColor(0x9FE0C0));
		self.level().playSound(null, self.getX(), self.getY(), self.getZ(), net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN,
				net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	private static int give(ServerPlayer from, ServerPlayer to) {
		int n = 0;
		for (DuniaSites.Site site : DuniaSites.known(from)) {
			if (!DuniaSites.knows(to, site.id()) && DuniaSites.discover(to, site.id())) {
				n++;
			}
		}
		return n;
	}

	private static boolean holdsMap(Player player) {
		return player.getMainHandItem().is(PetaItems.PETA_DUNIA) || player.getOffhandItem().is(PetaItems.PETA_DUNIA);
	}

	private static long pairKey(UUID a, UUID b) {
		long x = a.getMostSignificantBits() ^ a.getLeastSignificantBits();
		long y = b.getMostSignificantBits() ^ b.getLeastSignificantBits();
		return x < y ? x * 31L + y : y * 31L + x;
	}

	// ================================================================== test helpers

	/** {@code uji peta reset}: forgets the player's fog, waypoints and discovered sites. */
	static void reset(ServerPlayer player) {
		PetaData data = PetaData.get(player.level().getServer());
		data.forget(player.getUUID());
		DuniaSites.forget(player);
		touch(player);
	}

	static Set<String> queuedDims() {
		return new HashSet<>(QUEUE.keySet());
	}

	private Peta() {
	}
}
