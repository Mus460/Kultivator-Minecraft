package dev.kultivator.dunia;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The server budgets of DESIGN 34.4.4 (contract C34.S4). Modules {@link #track} the entities they spawn under a kind
 * ({@link #NPC}, {@link #DRAGON}, {@link #PART} or their own) and ask {@link #mayAdd} before spawning; removed entities
 * are forgotten by themselves. {@link #throttled} spreads custom AI over ticks, {@link #overloaded} is the MSPT guard
 * (average tick time above {@code umum.msptGuard} ms): then anchors tick every 60 ticks, no new NPC spawns, and births
 * and sky events postpone.
 */
public final class DuniaBudget {

	/** Humans of this phase (towns, wanderers, rivals, immortals, ghosts). */
	public static final String NPC = "npc";
	/** Wild dragons. */
	public static final String DRAGON = "naga";
	/** Collision parts (puzzle walls, doors, ship decks and seats). */
	public static final String PART = "part";
	public static final int PART_CAP = 96;
	public static final double PER_PLAYER_RANGE = 96.0D;

	private static final Map<String, List<Entity>> TRACKED = new HashMap<>();
	private static final Map<String, Integer> CAPS = new HashMap<>();
	private static boolean overloaded;

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(DuniaBudget::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TRACKED.clear();
			overloaded = false;
		});
	}

	/** Sets the world cap of {@code kind} (a module with its own kind; NPC and PART caps come from {@code umum}). */
	public static void setCap(String kind, int cap) {
		CAPS.put(kind, Math.max(0, cap));
	}

	/** The world cap of {@code kind}. */
	public static int cap(String kind) {
		if (NPC.equals(kind)) {
			return DuniaSpine.umum().npcCap;
		}
		if (PART.equals(kind)) {
			return CAPS.getOrDefault(PART, PART_CAP);
		}
		return CAPS.getOrDefault(kind, Integer.MAX_VALUE);
	}

	/** Remembers {@code entity} under {@code kind} (it is forgotten once removed). */
	public static void track(String kind, Entity entity) {
		if (entity == null) {
			return;
		}
		List<Entity> list = TRACKED.computeIfAbsent(kind, key -> new ArrayList<>());
		if (!list.contains(entity)) {
			list.add(entity);
		}
	}

	/** Live tracked entities of {@code kind}. */
	public static int count(String kind) {
		List<Entity> list = TRACKED.get(kind);
		if (list == null) {
			return 0;
		}
		list.removeIf(Entity::isRemoved);
		return list.size();
	}

	/** Live tracked entities of {@code kind} (a copy). */
	public static List<Entity> tracked(String kind) {
		List<Entity> list = TRACKED.get(kind);
		if (list == null) {
			return List.of();
		}
		list.removeIf(Entity::isRemoved);
		return List.copyOf(list);
	}

	/** True when one more entity of {@code kind} may spawn in {@code level} (world cap; no NPC under the MSPT guard). */
	public static boolean mayAdd(ServerLevel level, String kind) {
		if (!DuniaSpine.enabled()) {
			return false;
		}
		if (NPC.equals(kind) && overloaded(level.getServer())) {
			return false;
		}
		return count(kind) < cap(kind);
	}

	/**
	 * {@link #mayAdd(ServerLevel, String)} plus, for NPCs, the per-player cap: fewer than {@code umum.npcPerPlayer} NPCs
	 * within 96 blocks of every player within 96 blocks of {@code at}.
	 */
	public static boolean mayAdd(ServerLevel level, String kind, Vec3 at) {
		if (!mayAdd(level, kind)) {
			return false;
		}
		if (!NPC.equals(kind)) {
			return true;
		}
		int perPlayer = DuniaSpine.umum().npcPerPlayer;
		double r2 = PER_PLAYER_RANGE * PER_PLAYER_RANGE;
		List<Entity> npcs = TRACKED.getOrDefault(NPC, List.of());
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(at) > r2) {
				continue;
			}
			int near = 0;
			for (Entity npc : npcs) {
				if (!npc.isRemoved() && npc.level() == level && npc.position().distanceToSqr(player.position()) <= r2) {
					near++;
				}
			}
			if (near >= perPlayer) {
				return false;
			}
		}
		return true;
	}

	/**
	 * True on the entity's turn: every {@code every} ticks, staggered by id; every 10th tick ({@code max(every, 10)})
	 * when no player is within 24 blocks.
	 */
	public static boolean throttled(Entity entity, int every) {
		int period = Math.max(1, every);
		if (entity.level() instanceof ServerLevel level && level.getNearestPlayer(entity, 24.0D) == null) {
			period = Math.max(period, 10);
		}
		return Math.floorMod(entity.tickCount + entity.getId(), period) == 0;
	}

	/** The MSPT guard (DESIGN 34.4.4). */
	public static boolean overloaded(@Nullable MinecraftServer server) {
		return server != null && overloaded;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		double ms = server.getAverageTickTimeNanos() / 1_000_000.0D;
		overloaded = ms > DuniaSpine.umum().msptGuard;
	}

	private DuniaBudget() {
	}
}
