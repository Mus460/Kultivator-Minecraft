package dev.kultivator.sekte.langit.main;

import dev.kultivator.formasi.core.CoreView;
import dev.kultivator.formasi.core.Cores;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.Lambang;
import dev.kultivator.sekte.api.LambangService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitIds;
import dev.kultivator.sekte.langit.api.LangitPayloads;
import dev.kultivator.sekte.langit.api.SkyHallView;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sends {@link LangitPayloads.Sync} (DESIGN 37.6.1) to every player near a sky hall: when the set of halls within
 * {@link #RANGE} blocks (horizontal) of the player changes, when one of them is marked {@link #dirty}, and otherwise only
 * when its content changed (checked every {@link #CHECK_TICKS}, the hash of tier, flags and anchors). At most
 * {@link LangitPayloads#MAX_HALLS} halls per player (nearest first), only visual anchors, so the payload stays well under
 * 8 KiB. A player who walks away gets one empty sync, then nothing.
 */
public final class LangitSyncServer {

	/** Horizontal range from a hall's throne within which its sync is sent. */
	public static final double RANGE = 512.0D;
	/** Each player is looked at once per this many ticks (staggered by entity id). */
	public static final int CHECK_TICKS = 40;
	/** Default colour of the formation dome when the hall has no emblem (DESIGN 37.6.5 {@code HALL_COLOR}). */
	public static final int HALL_COLOR = 0x8FE3C8;
	/** Anchor id prefixes the client draws something at. */
	private static final String[] VISUAL = {"tahta", "gerbang_langit", "jaring_", "airterjun_", "feri_atas", "meditasi_", "dermaga_"};

	private static final Map<UUID, Integer> SENT = new HashMap<>();
	private static final Set<UUID> DIRTY = new HashSet<>();
	private static boolean dirtyAll;

	/** The visible state of {@code owner}'s sky hall changed: every viewer gets a fresh sync on its next check. */
	public static synchronized void dirty(UUID owner) {
		if (owner != null) {
			DIRTY.add(owner);
		}
	}

	/** Every viewer gets a fresh sync (festival flags, config reload). */
	public static synchronized void dirtyAll() {
		dirtyAll = true;
	}

	static void forget(ServerPlayer player) {
		SENT.remove(player.getUUID());
	}

	static void clear() {
		SENT.clear();
		synchronized (LangitSyncServer.class) {
			DIRTY.clear();
			dirtyAll = false;
		}
	}

	/** From the server tick. */
	static void tick(MinecraftServer server, long tick) {
		Set<UUID> dirtyNow;
		boolean all;
		synchronized (LangitSyncServer.class) {
			dirtyNow = DIRTY.isEmpty() ? Set.of() : new HashSet<>(DIRTY);
			all = dirtyAll;
			if (tick % CHECK_TICKS == CHECK_TICKS - 1) {
				DIRTY.clear();
				dirtyAll = false;
			}
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((tick + player.getId()) % CHECK_TICKS != 0) {
				continue;
			}
			check(player, dirtyNow, all);
		}
	}

	private static void check(ServerPlayer player, Set<UUID> dirtyNow, boolean all) {
		ServerLevel level = player.level();
		List<SkyHallView> near = near(level, player);
		List<LangitPayloads.SkyHall> halls = new ArrayList<>(near.size());
		int hash = 7;
		boolean touched = all;
		for (SkyHallView view : near) {
			LangitPayloads.SkyHall hall = build(level, view);
			halls.add(hall);
			hash = 31 * hash + fingerprint(hall);
			touched |= dirtyNow.contains(view.owner());
		}
		Integer last = SENT.get(player.getUUID());
		if (halls.isEmpty() && last == null) {
			return;
		}
		if (!touched && last != null && last == hash) {
			return;
		}
		if (halls.isEmpty()) {
			SENT.remove(player.getUUID());
		} else {
			SENT.put(player.getUUID(), hash);
		}
		LangitPayloads.send(player, new LangitPayloads.Sync(List.copyOf(halls)));
	}

	/** The sky halls of the player's level within {@link #RANGE} (horizontal), nearest first, at most four. */
	static List<SkyHallView> near(ServerLevel level, ServerPlayer player) {
		List<SkyHallView> all = LangitApi.get().all(level);
		if (all.isEmpty()) {
			return List.of();
		}
		List<SkyHallView> near = new ArrayList<>();
		for (SkyHallView view : all) {
			if (view.dim() == level.dimension() && view.horizontal(player.position()) <= RANGE) {
				near.add(view);
			}
		}
		near.sort(Comparator.comparingDouble(view -> view.horizontal(player.position())));
		return near.size() > LangitPayloads.MAX_HALLS ? List.copyOf(near.subList(0, LangitPayloads.MAX_HALLS)) : near;
	}

	/** The client's view of one hall. */
	static LangitPayloads.SkyHall build(ServerLevel level, SkyHallView view) {
		UUID owner = view.owner();
		int flags = 0;
		if (view.tethered()) {
			flags |= LangitIds.FLAG_TETHERED;
		}
		if (view.grandFormation()) {
			flags |= LangitIds.FLAG_GRAND_FORMATION;
		}
		if (netRunning(owner)) {
			flags |= LangitIds.FLAG_NET;
		}
		if (view.gateClosed()) {
			flags |= LangitIds.FLAG_GATE_CLOSED;
		}
		if (level.getServer() != null && LangitPeristiwa.festival(level.getServer(), owner)) {
			flags |= LangitIds.FLAG_FESTIVAL;
		}
		int dome = 0;
		if (view.grandFormation()) {
			int hallLevel = Math.max(1, Halls.level(owner));
			dome = Math.min(32, 16 + 2 * hallLevel);
		}
		List<LangitPayloads.Anchor> anchors = new ArrayList<>();
		for (Map.Entry<String, BlockPos> entry : LangitApi.get().anchors(owner).entrySet()) {
			if (visual(entry.getKey())) {
				anchors.add(new LangitPayloads.Anchor(entry.getKey(), entry.getValue()));
			}
		}
		// the cap keeps the same anchors every time: by the VISUAL order (throne, gate, net, falls first), then by id
		anchors.sort(Comparator.comparingInt((LangitPayloads.Anchor anchor) -> rank(anchor.id())).thenComparing(LangitPayloads.Anchor::id));
		if (anchors.size() > LangitPayloads.MAX_ANCHORS) {
			anchors.subList(LangitPayloads.MAX_ANCHORS, anchors.size()).clear();
		}
		return new LangitPayloads.SkyHall(owner, view.origin(), view.yP(), view.skyFloorY(), view.islandBox(), view.tier(), color(owner), dome, flags,
				List.copyOf(anchors));
	}

	private static int rank(String id) {
		for (int i = 0; i < VISUAL.length; i++) {
			if (id.startsWith(VISUAL[i])) {
				return i;
			}
		}
		return VISUAL.length;
	}

	private static boolean visual(String id) {
		for (String prefix : VISUAL) {
			if (id.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	/** True while one of the owner's {@code jaring_awan} cores (37M, netId 471) is powered or kept. */
	static boolean netRunning(UUID owner) {
		for (CoreView core : Cores.of(owner)) {
			if ("jaring_awan".equals(core.spec()) && core.state().running()) {
				return true;
			}
		}
		return false;
	}

	/** The hall emblem's field colour (module S), or {@link #HALL_COLOR}. */
	static int color(UUID owner) {
		LambangService lambang = SekteApi.lambang();
		if (lambang == LambangService.NONE) {
			return HALL_COLOR;
		}
		Lambang emblem = lambang.emblem(owner);
		return emblem == null ? HALL_COLOR : emblem.fieldRgb() & 0xFFFFFF;
	}

	private static int fingerprint(LangitPayloads.SkyHall hall) {
		int h = hall.owner().hashCode();
		h = 31 * h + hall.origin().hashCode();
		h = 31 * h + hall.yP();
		h = 31 * h + hall.skyFloorY();
		h = 31 * h + hall.tier();
		h = 31 * h + hall.flags();
		h = 31 * h + hall.color();
		h = 31 * h + hall.domeRadius();
		h = 31 * h + hall.box().hashCode();
		h = 31 * h + hall.anchors().hashCode();
		return h;
	}

	private LangitSyncServer() {
	}
}
