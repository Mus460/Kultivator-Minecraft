package dev.kultivator.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * The one DISCONNECT hook of the client. Every module registers its clear here instead of on Fabric's event.
 * DISCONNECT can fire on the netty thread, where {@code client.execute} only queues, and
 * {@code Minecraft.disconnect} then calls {@code dropAllTasks()}, which throws the queued clears away. So after the
 * listeners a marker task is queued behind them; when that marker has not run two client ticks later the clears were
 * dropped, and every listener runs again on the client thread (where {@code client.execute} runs at once).
 * Listeners must be idempotent and must ignore the connection argument (it is whatever is left when replayed).
 */
public final class ClientDisconnect {

	private static final List<ClientPlayConnectionEvents.Disconnect> LISTENERS = new CopyOnWriteArrayList<>();
	private static volatile boolean pending;
	private static boolean seen;

	private ClientDisconnect() {
	}

	/** Same shape as {@code ClientPlayConnectionEvents.DISCONNECT.register}. */
	public static void register(ClientPlayConnectionEvents.Disconnect listener) {
		LISTENERS.add(listener);
	}

	/** Called once, first thing in {@code KultivatorClient.onInitializeClient}. */
	static void init() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			pending = true;
			runAll(handler, client);
			client.execute(() -> pending = false);
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!pending) {
				seen = false;
			} else if (!seen) {
				seen = true;
			} else {
				seen = false;
				pending = false;
				runAll(client.getConnection(), client);
			}
		});
	}

	private static void runAll(ClientPacketListener handler, Minecraft client) {
		for (ClientPlayConnectionEvents.Disconnect listener : LISTENERS) {
			listener.onPlayDisconnect(handler, client);
		}
	}
}
