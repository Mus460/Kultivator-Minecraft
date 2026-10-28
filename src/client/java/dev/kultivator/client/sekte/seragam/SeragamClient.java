package dev.kultivator.client.sekte.seragam;

import dev.kultivator.Kultivator;
import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.sekte.SekteContent;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.seragam.SeragamPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * Module S on the client (DESIGN 36.2.8, 36.4): the payload receivers, the banner and decal pass
 * ({@code LevelRenderEvents.COLLECT_SUBMITS}), the owner cache of the robe layer and the design for the designer in
 * Sekte › Ikhtisar › Identitas ({@link LambangPanel}, DESIGN 43.5). One line in {@code SekteContentClient}. Client code reads only module S's own
 * payloads and the shared {@code sekte.api} records.
 */
public final class SeragamClient {

	private static final long ASK_WINDOW_MS = 4000L;
	/** The sub id of Sekte › Ikhtisar where the designer lives. */
	public static final String SUB_IDENTITAS = "identitas";
	private static boolean initialised;
	private static long ticks;
	private static long askedAt = Long.MIN_VALUE / 2;
	private static @Nullable SeragamPayloads.LambangOpen terakhir;
	private static long versiBuka;
	private static long lastWarn;

	public static void init() {
		if (initialised || !SekteContent.on(SekteIds.S)) {
			return;
		}
		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(SeragamPayloads.LambangSync.TYPE, (payload, context) -> LambangCache.onSync(payload, ticks));
		ClientPlayNetworking.registerGlobalReceiver(SeragamPayloads.LambangForget.TYPE, (payload, context) -> LambangCache.onForget(payload.key()));
		ClientPlayNetworking.registerGlobalReceiver(SeragamPayloads.LambangOpen.TYPE, (payload, context) -> open(payload));
		ClientPlayNetworking.registerGlobalReceiver(SeragamPayloads.SeragamDebug.TYPE, (payload, context) -> {
			int written = LambangAtlas.dump();
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				mc.player.sendSystemMessage(Component.translatable("kultivator.sekte.seragam.cmd.atlas.done", written).withStyle(ChatFormatting.GRAY));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			ticks++;
			try {
				if (ticks % 10 == 0) {
					SeragamOwners.tick();
				}
				LambangCache.tick(ticks);
				LambangAtlas.tick();
			} catch (RuntimeException exception) {
				warn("tick", exception);
			}
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(BenderaRenderer::collect);
		ClientDisconnect.register((handler, client) -> client.execute(() -> clearAll()));
	}

	/**
	 * Asks the server for the design of our hall (it answers with {@code LambangOpen}); false when module S is off on the
	 * server (nothing would ever answer). Called by Sekte › Ikhtisar › Identitas while shown.
	 */
	public static boolean minta() {
		if (!initialised || !ClientPlayNetworking.canSend(SeragamPayloads.LambangRequest.TYPE)) {
			return false;
		}
		askedAt = Util.getMillis();
		ClientPlayNetworking.send(new SeragamPayloads.LambangRequest(0));
		return true;
	}

	/** A save was sent: its answering {@code LambangOpen} only refreshes the page. */
	static void tandaiDiminta() {
		askedAt = Util.getMillis();
	}

	/** The last {@code LambangOpen} (key, free changes, cost, pool, flags), or null before the first. */
	public static @Nullable SeragamPayloads.LambangOpen terakhir() {
		return terakhir;
	}

	/** Grows with every {@code LambangOpen}. */
	public static long versiBuka() {
		return versiBuka;
	}

	/**
	 * A design arrived: the Identitas page picks it up; one nobody asked for ({@code /kultivasi perguruan lambang}) opens
	 * the Gulungan on Sekte › Ikhtisar › Identitas (43.4.4: no screen of its own any more).
	 */
	private static void open(SeragamPayloads.LambangOpen payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		terakhir = payload;
		versiBuka++;
		boolean asked = Util.getMillis() - askedAt < ASK_WINDOW_MS;
		askedAt = Long.MIN_VALUE / 2;
		if (!asked) {
			dev.kultivator.client.hall.IkhtisarPage.buka(SUB_IDENTITAS);
		}
	}

	private static void clearAll() {
		terakhir = null;
		versiBuka++;
		LambangCache.clear();
		SeragamOwners.clear();
		BenderaRenderer.clear();
		LambangFormasiRenderer.clear();
		LambangAtlas.releaseAll();
		LambangAtlas.reloadSources();
	}

	/** Logs a failure of the module's client code at most every 10 s (a broken frame never spams the log). */
	static void warn(String where, RuntimeException exception) {
		long now = Util.getMillis();
		if (now - lastWarn > 10_000L) {
			lastWarn = now;
			Kultivator.LOGGER.warn("Kultivator sekte seragam: {} failed", where, exception);
		}
	}

	private SeragamClient() {
	}
}
