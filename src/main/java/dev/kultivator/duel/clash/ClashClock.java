package dev.kultivator.duel.clash;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * The clash clock (DESIGN 35.3.4): {@code MinecraftServer.getTickCount()} at 20 Hz. Every window of the duel is counted
 * in these ticks; the server never slows down (hit-stop and slow motion are presentation on the client). The server
 * reference is set by {@code Duel} on SERVER_STARTING and cleared on SERVER_STOPPED.
 */
public final class ClashClock {

	private static @Nullable MinecraftServer server;

	public static void attach(@Nullable MinecraftServer running) {
		server = running;
	}

	public static @Nullable MinecraftServer server() {
		return server;
	}

	/** The current server tick, or 0 without a running server. */
	public static long now() {
		MinecraftServer current = server;
		return current == null ? 0L : current.getTickCount();
	}

	/**
	 * The latency compensation of a player's input (35.3.4): {@code round(latency_ms / 50)} clamped to 0..{@code max}.
	 * The latency is the keep-alive EMA ({@code ServerCommonPacketListenerImpl.latency()}), the round trip, so the
	 * defender is given the benefit of the doubt for at most {@code max} ticks (200 ms by default).
	 */
	public static int compensation(int latencyMs, int max) {
		if (latencyMs <= 0) {
			return 0;
		}
		return Math.max(0, Math.min(max, Math.round(latencyMs / 50.0F)));
	}

	private ClashClock() {
	}
}
