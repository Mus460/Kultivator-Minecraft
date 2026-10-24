package dev.kultivator.dunia;

import dev.kultivator.Kultivator;
import dev.kultivator.kehancuran.ProtectedZones;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The anchors of DESIGN 34.4.1 (contract C34.S1): the entity type, the module handlers, the loaded set and its per-level
 * spatial hash, the round-robin pass (at most {@link #PER_TICK} anchors per server tick, each visited every 20 ticks,
 * every 60 under the MSPT guard) and the one Mode Kehancuran zone provider of the phase (34.2.4), which protects
 * {@link DuniaAnchorEntity#protectRadius()} blocks around every loaded anchor in O(1) per call.
 * <p>
 * An anchor whose module has no handler (switched off, missing) or while {@code umum.enabled} is false stays dormant: it
 * is kept, saved and protected, but nothing is told about it.
 */
public final class DuniaAnchors {

	public static final int PER_TICK = 64;
	public static final int VISIT_TICKS = 20;
	public static final int GUARD_VISIT_TICKS = 60;
	/** Spatial hash cell (blocks); protection radii are clamped to it, so a lookup reads the 3 × 3 cells around a block. */
	private static final int CELL_SHIFT = 7;

	private static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("dunia_anchor"));

	/** {@code kultivator:dunia_anchor}: saved with its chunk, never sent to clients, never summoned, no loot. */
	public static final EntityType<DuniaAnchorEntity> TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
			EntityType.Builder.<DuniaAnchorEntity>of(DuniaAnchorEntity::new, MobCategory.MISC)
					.sized(0.25F, 0.25F)
					.clientTrackingRange(0)
					.fireImmune()
					.noSummon()
					.noLootTable()
					.build(KEY));

	private static final class Entry {
		final DuniaAnchorEntity anchor;
		long nextAt;

		Entry(DuniaAnchorEntity anchor, long nextAt) {
			this.anchor = anchor;
			this.nextAt = nextAt;
		}
	}

	private static final Map<String, AnchorHandler> HANDLERS = new HashMap<>();
	private static final Map<DuniaAnchorEntity, Entry> LOADED = new IdentityHashMap<>();
	private static final List<Entry> ORDER = new ArrayList<>();
	private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<List<DuniaAnchorEntity>>> CELLS = new HashMap<>();
	private static int cursor;

	/** From {@code DuniaSpine.init()}. */
	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof DuniaAnchorEntity anchor) {
				onLoad(level, anchor);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof DuniaAnchorEntity anchor) {
				onUnload(level, anchor);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(DuniaAnchors::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			ORDER.clear();
			CELLS.clear();
			cursor = 0;
		});
		ProtectedZones.addZoneProvider((level, pos) -> protectedNear(level, pos, 0));
	}

	// ================================================================== API

	/**
	 * The handler of every anchor whose kind starts with {@code module + "/"} ("kota" handles {@code kota/lelang}, ...).
	 * One handler per module; a second registration replaces the first. Anchors already loaded are told at once.
	 */
	public static void register(String module, AnchorHandler handler) {
		if (module == null || module.isEmpty() || handler == null) {
			return;
		}
		HANDLERS.put(module, handler);
		if (!DuniaSpine.enabled()) {
			return;
		}
		for (Entry entry : List.copyOf(ORDER)) {
			DuniaAnchorEntity anchor = entry.anchor;
			if (module.equals(anchor.module()) && anchor.level() instanceof ServerLevel level && !anchor.isRemoved()) {
				anchor.announced = false;
				announce(level, anchor);
			}
		}
	}

	/** The loaded anchors of {@code level} whose kind starts with {@code kindPrefix} ("kota/" or "kota/lelang"). */
	public static List<DuniaAnchorEntity> loaded(ServerLevel level, String kindPrefix) {
		List<DuniaAnchorEntity> list = new ArrayList<>();
		for (Entry entry : ORDER) {
			DuniaAnchorEntity anchor = entry.anchor;
			if (anchor.level() == level && !anchor.isRemoved() && anchor.kind().startsWith(kindPrefix)) {
				list.add(anchor);
			}
		}
		return list;
	}

	/** The loaded anchors of every level whose kind starts with {@code kindPrefix}. */
	public static List<DuniaAnchorEntity> loaded(String kindPrefix) {
		List<DuniaAnchorEntity> list = new ArrayList<>();
		for (Entry entry : ORDER) {
			if (!entry.anchor.isRemoved() && entry.anchor.kind().startsWith(kindPrefix)) {
				list.add(entry.anchor);
			}
		}
		return list;
	}

	/** The nearest loaded anchor of {@code level} with that kind prefix within {@code range} of {@code at}, or null. */
	public static @Nullable DuniaAnchorEntity nearest(ServerLevel level, Vec3 at, String kindPrefix, double range) {
		DuniaAnchorEntity best = null;
		double bestSq = range * range;
		for (Entry entry : ORDER) {
			DuniaAnchorEntity anchor = entry.anchor;
			if (anchor.level() != level || anchor.isRemoved() || !anchor.kind().startsWith(kindPrefix)) {
				continue;
			}
			double d = anchor.position().distanceToSqr(at);
			if (d <= bestSq) {
				bestSq = d;
				best = anchor;
			}
		}
		return best;
	}

	/** The {@link AnchorHandler#radius()} of {@code module}'s handler, or -1 when no handler serves it ({@code uji hidup audit}). */
	public static int radiusOf(String module) {
		AnchorHandler handler = HANDLERS.get(module);
		return handler == null ? -1 : handler.radius();
	}

	/** How many anchors are loaded now (every level). */
	public static int count() {
		return ORDER.size();
	}

	/**
	 * True when {@code pos} lies within {@code radius} + an anchor's protection radius of a loaded anchor of
	 * {@code level} (34.2.4). O(1): the anchors of the 3 × 3 hash cells around {@code pos}.
	 */
	public static boolean protectedNear(ServerLevel level, BlockPos pos, int radius) {
		Long2ObjectOpenHashMap<List<DuniaAnchorEntity>> cells = CELLS.get(level.dimension());
		if (cells == null || cells.isEmpty()) {
			return false;
		}
		int cx = pos.getX() >> CELL_SHIFT;
		int cz = pos.getZ() >> CELL_SHIFT;
		int extra = Math.max(0, radius);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				List<DuniaAnchorEntity> list = cells.get(cell(cx + dx, cz + dz));
				if (list == null) {
					continue;
				}
				for (DuniaAnchorEntity anchor : list) {
					int r = anchor.protectRadius();
					if (r <= 0 && extra <= 0) {
						continue;
					}
					double reach = r + extra + 0.5D;
					double ddx = anchor.getX() - (pos.getX() + 0.5D);
					double ddz = anchor.getZ() - (pos.getZ() + 0.5D);
					double ddy = anchor.getY() - (pos.getY() + 0.5D);
					// a town's footprint protects its whole height band; others a sphere-like box
					double vertical = Math.max(24.0D, reach);
					if (ddx * ddx + ddz * ddz <= reach * reach && Math.abs(ddy) <= vertical) {
						return true;
					}
				}
			}
		}
		return false;
	}

	// ================================================================== load / unload

	private static void onLoad(ServerLevel level, DuniaAnchorEntity anchor) {
		if (LOADED.containsKey(anchor)) {
			return;
		}
		try {
			anchor.resolve(level);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("A Dunia anchor {} could not resolve its structure", anchor.kind(), exception);
		}
		long now = level.getGameTime();
		Entry entry = new Entry(anchor, now + Math.floorMod(anchor.getId(), VISIT_TICKS));
		LOADED.put(anchor, entry);
		ORDER.add(entry);
		cellsOf(level.dimension()).computeIfAbsent(cell(anchor.getBlockX() >> CELL_SHIFT, anchor.getBlockZ() >> CELL_SHIFT),
				key -> new ArrayList<>()).add(anchor);
		if (DuniaSpine.umum().debugAnchors) {
			Kultivator.LOGGER.info("Dunia anchor loaded: {} at {} ({})", anchor.kind(), anchor.blockPosition().toShortString(), anchor.structure());
		}
		if ("situs".equals(anchor.what())) {
			publishSite(level, anchor);
		}
		if (DuniaSpine.enabled()) {
			announce(level, anchor);
		}
	}

	private static void onUnload(ServerLevel level, DuniaAnchorEntity anchor) {
		Entry entry = LOADED.remove(anchor);
		if (entry == null) {
			return;
		}
		ORDER.remove(entry);
		if (DuniaSpine.umum().debugAnchors) {
			Kultivator.LOGGER.info("Dunia anchor unloaded: {} at {}", anchor.kind(), anchor.blockPosition().toShortString());
		}
		Long2ObjectOpenHashMap<List<DuniaAnchorEntity>> cells = CELLS.get(level.dimension());
		if (cells != null) {
			long key = cell(anchor.getBlockX() >> CELL_SHIFT, anchor.getBlockZ() >> CELL_SHIFT);
			List<DuniaAnchorEntity> list = cells.get(key);
			if (list != null) {
				list.remove(anchor);
				if (list.isEmpty()) {
					cells.remove(key);
				}
			}
		}
		if (anchor.announced) {
			anchor.announced = false;
			AnchorHandler handler = HANDLERS.get(anchor.module());
			if (handler != null) {
				try {
					handler.unloaded(level, anchor);
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("Dunia anchor handler {} failed on unload", anchor.module(), exception);
				}
			}
		}
	}

	private static void announce(ServerLevel level, DuniaAnchorEntity anchor) {
		if (anchor.announced) {
			return;
		}
		AnchorHandler handler = HANDLERS.get(anchor.module());
		if (handler == null) {
			return;
		}
		anchor.announced = true;
		try {
			handler.loaded(level, anchor);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Dunia anchor handler {} failed on load", anchor.module(), exception);
		}
	}

	/** {@code <module>/situs}: publishes the structure as a site (data {@code situs}, {@code tier}); idempotent. */
	private static void publishSite(ServerLevel level, DuniaAnchorEntity anchor) {
		String kind = anchor.data().getStringOr("situs", anchor.module() + "/" + anchor.module());
		int tier = anchor.data().getIntOr("tier", 1);
		BlockPos at = anchor.origin() != null ? anchor.origin() : anchor.blockPosition();
		try {
			DuniaSites.publish(level, kind, at, tier);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("A Dunia site {} could not be published", kind, exception);
		}
	}

	// ================================================================== tick

	private static void tick(MinecraftServer server) {
		if (ORDER.isEmpty() || !DuniaSpine.enabled()) {
			return;
		}
		int visit = DuniaBudget.overloaded(server) ? GUARD_VISIT_TICKS : VISIT_TICKS;
		int n = Math.min(PER_TICK, ORDER.size());
		for (int i = 0; i < n && !ORDER.isEmpty(); i++) {
			if (cursor >= ORDER.size()) {
				cursor = 0;
			}
			Entry entry = ORDER.get(cursor++);
			DuniaAnchorEntity anchor = entry.anchor;
			if (anchor.isRemoved() || !(anchor.level() instanceof ServerLevel level)) {
				continue;
			}
			long now = level.getGameTime();
			if (now < entry.nextAt) {
				continue;
			}
			entry.nextAt = now + visit;
			if (!anchor.announced) {
				announce(level, anchor);
				if (!anchor.announced) {
					continue;
				}
			}
			AnchorHandler handler = HANDLERS.get(anchor.module());
			if (handler == null) {
				continue;
			}
			List<ServerPlayer> within = playersNear(level, anchor.position(), handler.radius());
			if (within.isEmpty()) {
				continue;
			}
			try {
				handler.nearby(level, anchor, within);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Dunia anchor handler {} failed near {}", anchor.module(), anchor.kind(), exception);
			}
		}
	}

	/** Survival and adventure players of {@code level} within {@code radius} of {@code at}, nearest first. */
	public static List<ServerPlayer> playersNear(ServerLevel level, Vec3 at, double radius) {
		List<ServerPlayer> list = new ArrayList<>();
		double r2 = radius * radius;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative() || !player.isAlive()) {
				continue;
			}
			if (player.position().distanceToSqr(at) <= r2) {
				list.add(player);
			}
		}
		if (list.size() > 1) {
			list.sort(Comparator.comparingDouble(player -> player.position().distanceToSqr(at)));
		}
		return list;
	}

	/** The players of {@code within} no farther than {@code range} from {@code anchor} (a handler whose radius grew for its bodies). */
	public static List<ServerPlayer> dalam(List<ServerPlayer> within, DuniaAnchorEntity anchor, double range) {
		double r2 = range * range;
		List<ServerPlayer> list = new ArrayList<>(within.size());
		for (ServerPlayer player : within) {
			if (player.position().distanceToSqr(anchor.position()) <= r2) {
				list.add(player);
			}
		}
		return list;
	}

	private static Long2ObjectOpenHashMap<List<DuniaAnchorEntity>> cellsOf(ResourceKey<Level> dimension) {
		return CELLS.computeIfAbsent(dimension, key -> new Long2ObjectOpenHashMap<>());
	}

	private static long cell(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	private DuniaAnchors() {
	}
}
