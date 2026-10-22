package dev.kultivator.client.sekte.seragam;

import dev.kultivator.Kultivator;
import dev.kultivator.client.hub.HubCards;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.sekte.SekteContent;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.seragam.SeragamPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Module S on the client (DESIGN 36.2.8, 36.4): the payload receivers, the banner and decal pass
 * ({@code LevelRenderEvents.COLLECT_SUBMITS}), the owner cache of the robe layer, the designer and the hub card
 * Sekte › "Lambang &amp; Seragam". One line in {@code SekteContentClient}. Client code reads only module S's own
 * payloads and the shared {@code sekte.api} records.
 */
public final class SeragamClient {

	private static final long HUB_WINDOW_MS = 4000L;
	private static boolean initialised;
	private static long ticks;
	private static long hubAskedAt = Long.MIN_VALUE / 2;
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
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearAll());
		// Hub › Sekte › "Lambang & Seragam": the server answers with the design and opens the designer
		HubCards.register(HubNav.SEKTE, "sekte_seragam_lambang", Glyphs.SEAL, null, () -> {
			hubAskedAt = Util.getMillis();
			ClientPlayNetworking.send(new SeragamPayloads.LambangRequest(0));
		});
	}

	private static void open(SeragamPayloads.LambangOpen payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		if (mc.gui.screen() instanceof LambangDesignerScreen designer && designer.key().equals(payload.key())) {
			designer.refresh(payload.freeLeft(), payload.cost(), payload.pool());
			return;
		}
		boolean fromHub = Util.getMillis() - hubAskedAt < HUB_WINDOW_MS;
		hubAskedAt = Long.MIN_VALUE / 2;
		LambangDesignerScreen screen = new LambangDesignerScreen(fromHub ? null : mc.gui.screen(), payload.key(), payload.freeLeft(), payload.cost(),
				payload.pool(), payload.flags());
		if (fromHub) {
			HubNav.openChild(screen, HubNav.SEKTE);
		} else {
			mc.gui.setScreen(screen);
		}
	}

	private static void clearAll() {
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
