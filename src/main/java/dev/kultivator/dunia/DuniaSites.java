package dev.kultivator.dunia;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.Sects;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The sites of DESIGN 34.4.2 (contract C34.S2): everything the map, the rumours and the quests know. SavedData
 * {@code kultivator:dunia_sites} in the overworld holds every published site of every dimension and, per player, the ids
 * they discovered; live sites (treasure births, dragon sightings, sky-event zones) live in memory only.
 * <p>
 * Discovery: automatic within {@link #AUTO_RANGE} blocks (checked every 40 ticks per player), by a rumour, a locate
 * scroll or a map exchange ({@link #discover}). Sect mates share what they know ({@link #known}). {@link #locate} wraps
 * {@code ServerLevel.findNearestMapStructure} with at most one search per server tick.
 */
public final class DuniaSites extends SavedData {

	/** A site. {@code foundDay} is the in-game day it was first published. */
	public record Site(long id, String kind, ResourceKey<Level> dim, BlockPos pos, int tier, long foundDay) {

		public Site {
			pos = pos.immutable();
		}

		/** The module part of the kind ("kota" of "kota/kota"). */
		public String module() {
			int slash = kind.indexOf('/');
			return slash < 0 ? kind : kind.substring(0, slash);
		}

		/** Horizontal distance to {@code at} in blocks (infinite in another dimension). */
		public double distance(ResourceKey<Level> dimension, BlockPos at) {
			if (!dim.equals(dimension)) {
				return Double.POSITIVE_INFINITY;
			}
			double dx = pos.getX() - at.getX();
			double dz = pos.getZ() - at.getZ();
			return Math.sqrt(dx * dx + dz * dz);
		}
	}

	public static final double AUTO_RANGE = 48.0D;
	public static final int MAX_LOCATE_CHUNKS = 64;
	public static final int PLAYER_LOCATE_COOLDOWN = 600;
	/** At most this many old cave sites are checked by one {@link #cleanCaves} run (each may load one chunk to structure starts). */
	public static final int CLEAN_PER_RUN = 64;

	/** The site kind and tier a structure's own anchors publish (a broad tag such as all three caves finds one of them). */
	private record RealKind(String kind, int tier) {
	}

	private static final Map<String, RealKind> REAL_KINDS = Map.of(
			"kultivator:reruntuhan/gua_kecil", new RealKind("reruntuhan/gua_kecil", 1),
			"kultivator:reruntuhan/gua_agung", new RealKind("reruntuhan/gua_agung", 2),
			"kultivator:reruntuhan/gua_kuno", new RealKind("reruntuhan/gua_kuno", 3),
			"kultivator:kota/kota_abadi", new RealKind("kota/kota_abadi", 1));

	public static final Codec<DuniaSites> CODEC = CompoundTag.CODEC.xmap(DuniaSites::fromTag, DuniaSites::toTag);
	public static final SavedDataType<DuniaSites> TYPE = new SavedDataType<>(Kultivator.id("dunia_sites"), DuniaSites::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<Long, Site> sites = new LinkedHashMap<>();
	private final Map<UUID, LongOpenHashSet> known = new HashMap<>();

	private static final Map<Long, Site> LIVE = new LinkedHashMap<>();
	private static final Map<Long, Long> LIVE_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> PLAYER_LOCATE = new HashMap<>();
	private static long lastSearchTick = Long.MIN_VALUE;
	private static @Nullable MinecraftServer server;

	public DuniaSites() {
	}

	/** From {@code DuniaSpine.init()}. */
	static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
			server = null;
			LIVE.clear();
			LIVE_UNTIL.clear();
			PLAYER_LOCATE.clear();
			lastSearchTick = Long.MIN_VALUE;
		});
		ServerTickEvents.END_SERVER_TICK.register(DuniaSites::tick);
	}

	/** The data of the running server (overworld storage). */
	public static DuniaSites get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	// ================================================================== ids

	/**
	 * {@code hash(dimension, pos x/z, kind)}, never 0. The height is left out, so a structure located from afar (the start
	 * chunk's middle at some y) and its own anchors (the same middle at the structure's height) name the same site.
	 */
	public static long idOf(ResourceKey<Level> dimension, BlockPos pos, String kind) {
		long h = 0x9E3779B97F4A7C15L;
		h = mix(h ^ dimension.identifier().toString().hashCode());
		h = mix(h ^ BlockPos.asLong(pos.getX(), 0, pos.getZ()));
		h = mix(h ^ (kind == null ? 0 : kind.hashCode()));
		return h == 0L ? 1L : h;
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	// ================================================================== publish

	/** Publishes a site (idempotent: the same id changes nothing) and returns it. */
	public static Site publish(ServerLevel level, String kind, BlockPos pos, int tier) {
		DuniaSites data = get(level.getServer());
		long id = idOf(level.dimension(), pos, kind);
		Site site = data.sites.get(id);
		if (site != null) {
			if (!site.pos().equals(pos)) {
				// the structure's own anchor knows the real height better than a far locate
				site = new Site(id, site.kind(), site.dim(), pos, site.tier(), site.foundDay());
				data.sites.put(id, site);
				data.setDirty();
			}
			return site;
		}
		site = new Site(id, kind, level.dimension(), pos, Math.max(0, tier), day(level.getServer()));
		data.sites.put(id, site);
		data.setDirty();
		return site;
	}

	/** A live site until game time {@code untilGameTime} (memory only, never saved). Re-publishing extends it. */
	public static Site publishLive(ServerLevel level, String kind, BlockPos pos, int tier, long untilGameTime) {
		long id = idOf(level.dimension(), pos, kind);
		Site site = LIVE.get(id);
		if (site == null) {
			site = new Site(id, kind, level.dimension(), pos, Math.max(0, tier), day(level.getServer()));
			LIVE.put(id, site);
		}
		LIVE_UNTIL.put(id, untilGameTime);
		return site;
	}

	/** Ends a live site at once. */
	public static void endLive(long id) {
		LIVE.remove(id);
		LIVE_UNTIL.remove(id);
	}

	/** The site (saved or live) with this id, or null. */
	public static @Nullable Site byId(MinecraftServer server, long id) {
		Site site = get(server).sites.get(id);
		return site != null ? site : LIVE.get(id);
	}

	/** Every saved site (read-only). */
	public static Collection<Site> all(MinecraftServer server) {
		return java.util.Collections.unmodifiableCollection(get(server).sites.values());
	}

	/** Every live site (read-only). */
	public static Collection<Site> live() {
		return java.util.Collections.unmodifiableCollection(LIVE.values());
	}

	// ================================================================== discovery

	/** The player discovers site {@code id}; true when it was new to them (fires {@code SITE_DISCOVERED}). */
	public static boolean discover(ServerPlayer player, long id) {
		MinecraftServer current = player.level().getServer();
		DuniaSites data = get(current);
		Site site = byId(current, id);
		if (site == null) {
			return false;
		}
		LongOpenHashSet set = data.known.computeIfAbsent(player.getUUID(), key -> new LongOpenHashSet());
		if (!set.add(id)) {
			return false;
		}
		if (data.sites.containsKey(id)) {
			data.setDirty();
		}
		DuniaEvents.fireSiteDiscovered(player, site);
		return true;
	}

	/** True when the player (or a sect mate) knows site {@code id}. */
	public static boolean knows(ServerPlayer player, long id) {
		DuniaSites data = get(player.level().getServer());
		LongOpenHashSet own = data.known.get(player.getUUID());
		if (own != null && own.contains(id)) {
			return true;
		}
		Sect sect = Sects.sectOf(player);
		if (sect != null) {
			for (Sect.Member member : sect.members()) {
				LongOpenHashSet mate = data.known.get(member.id());
				if (mate != null && mate.contains(id)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Every site the player or a sect mate discovered (saved and still-live ones). */
	public static List<Site> known(ServerPlayer player) {
		MinecraftServer current = player.level().getServer();
		DuniaSites data = get(current);
		LongOpenHashSet ids = new LongOpenHashSet();
		LongOpenHashSet own = data.known.get(player.getUUID());
		if (own != null) {
			ids.addAll(own);
		}
		Sect sect = Sects.sectOf(player);
		if (sect != null) {
			for (Sect.Member member : sect.members()) {
				LongOpenHashSet mate = data.known.get(member.id());
				if (mate != null) {
					ids.addAll(mate);
				}
			}
		}
		List<Site> list = new ArrayList<>();
		for (long id : ids) {
			Site site = byId(current, id);
			if (site != null) {
				list.add(site);
			}
		}
		return list;
	}

	/** Forgets everything the player discovered (test command). */
	public static void forget(ServerPlayer player) {
		DuniaSites data = get(player.level().getServer());
		if (data.known.remove(player.getUUID()) != null) {
			data.setDirty();
		}
	}

	/** Saved and live sites of {@code level} within {@code radius} blocks of {@code pos} whose kind starts with {@code kindPrefix}. */
	public static List<Site> near(ServerLevel level, BlockPos pos, int radius, String kindPrefix) {
		List<Site> list = new ArrayList<>();
		String prefix = kindPrefix == null ? "" : kindPrefix;
		for (Site site : get(level.getServer()).sites.values()) {
			if (site.kind().startsWith(prefix) && site.distance(level.dimension(), pos) <= radius) {
				list.add(site);
			}
		}
		for (Site site : LIVE.values()) {
			if (site.kind().startsWith(prefix) && site.distance(level.dimension(), pos) <= radius) {
				list.add(site);
			}
		}
		list.sort(java.util.Comparator.comparingDouble(site -> site.distance(level.dimension(), pos)));
		return list;
	}

	// ================================================================== locate

	/**
	 * The nearest structure of {@code tag} within {@code chunkRadius} (≤ 64) chunks of {@code from}, published as a site
	 * whose kind is the tag's path ({@code kultivator:reruntuhan/gua_kecil} → {@code reruntuhan/gua_kecil}). Null when
	 * none was found, structures are off, or a search already ran this server tick.
	 */
	public static @Nullable Site locate(ServerLevel level, TagKey<Structure> tag, BlockPos from, int chunkRadius) {
		return locate(level, tag, from, chunkRadius, tag.location().getPath(), 1);
	}

	/** {@link #locate(ServerLevel, TagKey, BlockPos, int)} publishing the result as {@code kind} with {@code tier}. */
	public static @Nullable Site locate(ServerLevel level, TagKey<Structure> tag, BlockPos from, int chunkRadius, String kind, int tier) {
		long tick = level.getServer().getTickCount();
		if (tick == lastSearchTick) {
			return null;
		}
		lastSearchTick = tick;
		BlockPos found;
		Holder<Structure> structure;
		try {
			Optional<HolderSet.Named<Structure>> set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(tag);
			if (set.isEmpty()) {
				return null;
			}
			Pair<BlockPos, Holder<Structure>> result = level.getChunkSource().getGenerator().findNearestMapStructure(level, set.get(), from,
					Math.max(1, Math.min(MAX_LOCATE_CHUNKS, chunkRadius)), false);
			if (result == null) {
				return null;
			}
			found = result.getFirst();
			structure = result.getSecond();
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Dunia locate of {} failed", tag.location(), exception);
			return null;
		}
		// the structure actually found names the kind (a cave rumour may find a Gua Agung or Gua Kuno, integration audit S18)
		RealKind real = REAL_KINDS.get(structure.unwrapKey().map(key -> key.identifier().toString()).orElse(""));
		if (real != null && moduleOf(real.kind()).equals(moduleOf(kind))) {
			kind = real.kind();
			tier = real.tier();
		}
		// never a height lookup here: that could load the far chunk (the structure's own anchors publish the real height)
		BlockPos at = found.immutable();
		// a structure's sites are keyed by its start chunk middle, like the anchors' origin (DuniaAnchorEntity.resolve)
		Site anchorSite = siteAt(level, kind, found);
		return anchorSite != null ? anchorSite : publish(level, kind, at, tier);
	}

	/**
	 * {@link #locate} for a player's rumour or scroll: at most one per player per 30 s. Null on the cooldown (check
	 * {@link #locateCooldown}) or when nothing was found.
	 */
	public static @Nullable Site locateFor(ServerPlayer player, TagKey<Structure> tag, int chunkRadius, String kind, int tier) {
		long now = player.level().getServer().getTickCount();
		Long last = PLAYER_LOCATE.get(player.getUUID());
		if (last != null && now - last < PLAYER_LOCATE_COOLDOWN) {
			return null;
		}
		PLAYER_LOCATE.put(player.getUUID(), now);
		return locate((ServerLevel) player.level(), tag, player.blockPosition(), chunkRadius, kind, tier);
	}

	/** Ticks left of the player's locate cooldown. */
	public static long locateCooldown(ServerPlayer player) {
		Long last = PLAYER_LOCATE.get(player.getUUID());
		if (last == null) {
			return 0L;
		}
		return Math.max(0L, PLAYER_LOCATE_COOLDOWN - (player.level().getServer().getTickCount() - last));
	}

	private static String moduleOf(String kind) {
		int slash = kind.indexOf('/');
		return slash < 0 ? kind : kind.substring(0, slash);
	}

	/**
	 * Save repair for old worlds (test command {@code uji peta situs bersihkan}, integration audit S18): rumours and pointer
	 * scrolls used to publish every cave as {@code reruntuhan/gua_kecil}. A Gua Kecil site with a Gua Agung or Gua Kuno site
	 * within 96 blocks is that cave's duplicate; otherwise the structure start in the site's chunk names the real kind (the
	 * chunk is brought to structure starts at most, never generated further). Players who knew the wrong site know the
	 * right one. A site whose chunk holds no cave start is left alone. Returns the number of sites repaired and checked.
	 */
	public static int[] cleanCaves(MinecraftServer current) {
		DuniaSites data = get(current);
		List<Site> small = new ArrayList<>();
		for (Site site : data.sites.values()) {
			if (site.kind().equals("reruntuhan/gua_kecil") && small.size() < CLEAN_PER_RUN) {
				small.add(site);
			}
		}
		int fixed = 0;
		for (Site site : small) {
			ServerLevel level = current.getLevel(site.dim());
			if (level == null) {
				continue;
			}
			Site real = null;
			for (Site other : data.sites.values()) {
				if ((other.kind().equals("reruntuhan/gua_agung") || other.kind().equals("reruntuhan/gua_kuno"))
						&& other.distance(site.dim(), site.pos()) <= 96.0D) {
					real = other;
					break;
				}
			}
			if (real == null) {
				RealKind kind = startIn(level, site.pos());
				if (kind == null || kind.kind().equals(site.kind()) || !moduleOf(kind.kind()).equals("reruntuhan")) {
					continue;
				}
				real = publish(level, kind.kind(), site.pos(), kind.tier());
			}
			if (real.id() == site.id()) {
				continue;
			}
			data.sites.remove(site.id());
			for (LongOpenHashSet set : data.known.values()) {
				if (set.remove(site.id())) {
					set.add(real.id());
				}
			}
			data.setDirty();
			fixed++;
		}
		return new int[]{fixed, small.size()};
	}

	/** The known structure (see {@link #REAL_KINDS}) starting in the chunk of {@code pos}, or null. */
	private static @Nullable RealKind startIn(ServerLevel level, BlockPos pos) {
		ChunkAccess chunk;
		try {
			chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS, true);
		} catch (RuntimeException exception) {
			return null;
		}
		if (chunk == null) {
			return null;
		}
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
			Identifier id = registry.getKey(entry.getKey());
			RealKind kind = id == null ? null : REAL_KINDS.get(id.toString());
			if (kind != null && entry.getValue().isValid()) {
				return kind;
			}
		}
		return null;
	}

	/** A saved site of {@code kind} already published within 96 blocks of {@code pos} (the structure's own), or null. */
	private static @Nullable Site siteAt(ServerLevel level, String kind, BlockPos pos) {
		for (Site site : get(level.getServer()).sites.values()) {
			if (site.kind().equals(kind) && site.distance(level.dimension(), pos) <= 96.0D) {
				return site;
			}
		}
		return null;
	}

	// ================================================================== tick

	private static void tick(MinecraftServer current) {
		long tick = current.getTickCount();
		if (tick % 20L == 0L && !LIVE_UNTIL.isEmpty()) {
			Iterator<Map.Entry<Long, Long>> iterator = LIVE_UNTIL.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<Long, Long> entry = iterator.next();
				Site site = LIVE.get(entry.getKey());
				ServerLevel level = site == null ? null : current.getLevel(site.dim());
				if (site == null || level == null || level.getGameTime() >= entry.getValue()) {
					iterator.remove();
					LIVE.remove(entry.getKey());
				}
			}
		}
		if (tick % 40L != 0L || !DuniaSpine.enabled()) {
			return;
		}
		DuniaSites data = get(current);
		if (data.sites.isEmpty() && LIVE.isEmpty()) {
			return;
		}
		for (ServerPlayer player : current.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			// staggered: each player every 40 ticks, one pass over the sites of their dimension
			BlockPos at = player.blockPosition();
			for (Site site : near(level, at, (int) AUTO_RANGE, "")) {
				discover(player, site.id());
			}
		}
	}

	static long day(MinecraftServer current) {
		return Math.floorDiv(current.overworld().getOverworldClockTime(), 24000L);
	}

	// ================================================================== save

	private static DuniaSites fromTag(CompoundTag tag) {
		DuniaSites data = new DuniaSites();
		for (Tag each : tag.getListOrEmpty("sites")) {
			if (!(each instanceof CompoundTag s)) {
				continue;
			}
			Identifier dim = Identifier.tryParse(s.getStringOr("dim", "minecraft:overworld"));
			if (dim == null) {
				continue;
			}
			ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dim);
			Site site = new Site(s.getLongOr("id", 0L), s.getStringOr("kind", ""), key, BlockPos.of(s.getLongOr("pos", 0L)),
					s.getIntOr("tier", 1), s.getLongOr("day", 0L));
			if (site.id() != 0L && !site.kind().isEmpty()) {
				data.sites.put(site.id(), site);
			}
		}
		CompoundTag knownTag = tag.getCompoundOrEmpty("known");
		for (String key : knownTag.keySet()) {
			try {
				UUID uuid = UUID.fromString(key);
				long[] ids = knownTag.getLongArray(key).orElse(new long[0]);
				data.known.put(uuid, new LongOpenHashSet(ids));
			} catch (IllegalArgumentException ignored) {
				// a broken key is dropped
			}
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (Site site : sites.values()) {
			CompoundTag s = new CompoundTag();
			s.putLong("id", site.id());
			s.putString("kind", site.kind());
			s.putString("dim", site.dim().identifier().toString());
			s.putLong("pos", site.pos().asLong());
			s.putInt("tier", site.tier());
			s.putLong("day", site.foundDay());
			list.add(s);
		}
		tag.put("sites", list);
		CompoundTag knownTag = new CompoundTag();
		for (Map.Entry<UUID, LongOpenHashSet> entry : known.entrySet()) {
			// only saved sites are kept (live ones end with the session)
			LongOpenHashSet saved = new LongOpenHashSet();
			for (long id : entry.getValue()) {
				if (sites.containsKey(id)) {
					saved.add(id);
				}
			}
			if (!saved.isEmpty()) {
				knownTag.put(entry.getKey().toString(), new LongArrayTag(saved.toLongArray()));
			}
		}
		tag.put("known", knownTag);
		return tag;
	}
}
