package dev.kultivator.duel.session;

import dev.kultivator.Kultivator;
import dev.kultivator.duel.DuelConfig;
import dev.kultivator.duel.clash.ClashClock;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every running formal duel (DESIGN 35.2.2): one session per entity ({@link #byEntity}), at most
 * {@code DuelConfig.maxSessions} per server. Server thread only, memory only.
 */
public final class DuelSessions {

	private static final Map<Integer, DuelSession> SESSIONS = new LinkedHashMap<>();
	private static final Map<UUID, DuelSession> BY_ENTITY = new HashMap<>();
	private static int nextId = 1;

	/** The session {@code entity} duels in (any phase but ENDED), or null (C35.S2). */
	public static @Nullable DuelSession byEntity(@Nullable Entity entity) {
		if (entity == null || BY_ENTITY.isEmpty()) {
			return null;
		}
		DuelSession session = BY_ENTITY.get(entity.getUUID());
		return session != null && !session.ended() && (session.a == entity || session.b == entity) ? session : null;
	}

	/** The session a player watches as the challenger of a watched duel (TONTON), or null. */
	public static @Nullable DuelSession watchedBy(ServerPlayer player) {
		for (DuelSession session : SESSIONS.values()) {
			if (!session.ended() && session.challenger == player && !session.involves(player)) {
				return session;
			}
		}
		return null;
	}

	public static @Nullable DuelSession byId(int id) {
		return SESSIONS.get(id);
	}

	public static List<DuelSession> all() {
		return new ArrayList<>(SESSIONS.values());
	}

	public static int count() {
		return SESSIONS.size();
	}

	/** True when another session may start. */
	public static boolean room() {
		return SESSIONS.size() < DuelConfig.get().maxSessions;
	}

	/**
	 * Starts a session between {@code a} and {@code b} (the challenge was accepted). Null with a reason in {@code why}
	 * when one of them is busy or the server is full.
	 */
	public static @Nullable DuelSession start(int pair, DuelRuleSet rules, LivingEntity a, LivingEntity b, @Nullable ServerPlayer challenger,
			@Nullable String slipA, @Nullable String slipB, Component[] why) {
		if (!room()) {
			why[0] = Component.translatable("kultivator.duel.reason.full");
			return null;
		}
		if (byEntity(a) != null || byEntity(b) != null || dev.kultivator.disciple.DuelRules.inDuel(a) || dev.kultivator.disciple.DuelRules.inDuel(b)) {
			why[0] = Component.translatable("kultivator.duel.reason.busy");
			return null;
		}
		if (a.level() != b.level() || a.level().isClientSide()) {
			why[0] = Component.translatable("kultivator.duel.reason.far");
			return null;
		}
		int id = nextId++;
		if (nextId > 1_000_000_000) {
			nextId = 1;
		}
		DuelSession session = new DuelSession(id, pair, rules, a, b, challenger);
		session.slipA = slipA;
		session.slipB = slipB;
		SESSIONS.put(id, session);
		BY_ENTITY.put(a.getUUID(), session);
		BY_ENTITY.put(b.getUUID(), session);
		try {
			session.start(ClashClock.now());
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Duel: starting session {} failed", id, exception);
			session.cancel(Component.translatable("kultivator.duel.reason.error"));
			why[0] = Component.translatable("kultivator.duel.reason.error");
			return null;
		}
		return session;
	}

	/** Called by the session itself when it ended. */
	static void remove(DuelSession session) {
		SESSIONS.remove(session.id, session);
		BY_ENTITY.remove(session.a.getUUID(), session);
		BY_ENTITY.remove(session.b.getUUID(), session);
	}

	/** Every server tick. */
	public static void tick(MinecraftServer server) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		long now = ClashClock.now();
		for (DuelSession session : new ArrayList<>(SESSIONS.values())) {
			try {
				session.tick(now);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Duel: session {} failed, cancelling it", session.id, exception);
				try {
					session.cancel(Component.translatable("kultivator.duel.reason.error"));
				} catch (RuntimeException again) {
					remove(session);
				}
			}
		}
	}

	/** Cancels every session {@code player} is part of or watches ({@code uji duel stop}); the count. */
	public static int cancelAll(ServerPlayer player, Component reason) {
		int count = 0;
		for (DuelSession session : new ArrayList<>(SESSIONS.values())) {
			if (session.involves(player) || session.challenger == player) {
				session.cancel(reason);
				count++;
			}
		}
		return count;
	}

	/** A player left or changed dimension: their sessions are cancelled (stakes back). */
	public static void onPlayerGone(ServerPlayer player, Component reason) {
		DuelSession session = byEntity(player);
		if (session != null) {
			session.cancel(reason);
		}
	}

	/** Server stop: cancel everything (the escrow returns the stakes; on the next start it also returns leftovers). */
	public static void stopAll() {
		for (DuelSession session : new ArrayList<>(SESSIONS.values())) {
			try {
				session.cancel(Component.translatable("kultivator.duel.reason.stopping"));
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Duel: stopping session {} failed", session.id, exception);
			}
		}
		SESSIONS.clear();
		BY_ENTITY.clear();
	}

	private DuelSessions() {
	}
}
