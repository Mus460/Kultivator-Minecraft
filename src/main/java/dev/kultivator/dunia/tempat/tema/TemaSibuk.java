package dev.kultivator.dunia.tempat.tema;

import dev.kultivator.disciple.Disciples;
import dev.kultivator.util.Sibuk;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The themed towns' busy states (DESIGN 38.T.11 row Sibuk): {@code tema.pelayaran} (the Kapal Lintas Laut crossing),
 * {@code tema.perahu} (a boat or canal tour passenger), {@code tema.lift} (cable car or pulley basket), {@code tema.arena}
 * (an arena bout) and {@code tema.tari} (a dance or rhythm mini-game). While a player is held, other systems'
 * scheduled events wait ({@code Sibuk.why}, retried after {@code Sibuk.RETRY_TICKS}), and disciples do not follow a
 * teleport (a follow veto; the Kapal Lintas Laut itself takes two disciples by its own code).
 * <p>
 * Theme systems call {@link #tahan} when the state begins (with a safety length in ticks; the hold ends by itself when a
 * system forgets), {@link #lepas} when it ends, and {@link #sedang} to ask. Memory only (a reload ends every hold).
 * Reason keys {@code kultivator.sibuk.tema.<jenis>} live in {@code lang-src/tempat_tema.*}.
 */
public final class TemaSibuk {

	public static final String PELAYARAN = "pelayaran";
	public static final String PERAHU = "perahu";
	public static final String LIFT = "lift";
	public static final String ARENA = "arena";
	public static final String TARI = "tari";
	public static final List<String> SEMUA = List.of(PELAYARAN, PERAHU, LIFT, ARENA, TARI);

	/** player -> (jenis -> game time the hold ends). */
	private static final Map<UUID, Map<String, Long>> TAHAN = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		for (String jenis : SEMUA) {
			Sibuk.register("tema." + jenis, player -> sedang(player, jenis));
		}
		Disciples.addFollowVeto((player, origin, destination) -> {
			for (String jenis : SEMUA) {
				if (!PELAYARAN.equals(jenis) && sedang(player, jenis)) {
					return "kultivator.sibuk.tema." + jenis;
				}
			}
			return null;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> lepasSemua(handler.getPlayer()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			synchronized (TAHAN) {
				TAHAN.clear();
			}
		});
	}

	/** Holds {@code player} in state {@code jenis} for at most {@code ticks} game ticks (a later call extends it). */
	public static void tahan(ServerPlayer player, String jenis, int ticks) {
		if (player == null || jenis == null) {
			return;
		}
		long until = player.level().getGameTime() + Math.max(20, ticks);
		synchronized (TAHAN) {
			TAHAN.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(jenis, until);
		}
	}

	/** Ends the hold {@code jenis}. */
	public static void lepas(ServerPlayer player, String jenis) {
		if (player == null) {
			return;
		}
		synchronized (TAHAN) {
			Map<String, Long> map = TAHAN.get(player.getUUID());
			if (map != null) {
				map.remove(jenis);
				if (map.isEmpty()) {
					TAHAN.remove(player.getUUID());
				}
			}
		}
	}

	/** Ends every hold of the player. */
	public static void lepasSemua(ServerPlayer player) {
		if (player != null) {
			synchronized (TAHAN) {
				TAHAN.remove(player.getUUID());
			}
		}
	}

	/** True while {@code player} is held in {@code jenis}. */
	public static boolean sedang(ServerPlayer player, String jenis) {
		if (player == null) {
			return false;
		}
		synchronized (TAHAN) {
			Map<String, Long> map = TAHAN.get(player.getUUID());
			if (map == null) {
				return false;
			}
			Long until = map.get(jenis);
			if (until == null) {
				return false;
			}
			if (until < player.level().getGameTime()) {
				map.remove(jenis);
				return false;
			}
			return true;
		}
	}

	/** The first state holding the player, or null. */
	public static String apa(ServerPlayer player) {
		for (String jenis : SEMUA) {
			if (sedang(player, jenis)) {
				return jenis;
			}
		}
		return null;
	}

	private TemaSibuk() {
	}
}
