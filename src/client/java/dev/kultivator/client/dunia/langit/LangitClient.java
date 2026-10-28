package dev.kultivator.client.dunia.langit;

import dev.kultivator.Kultivator;
import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.dunia.langit.LangitCuacaPayload;
import dev.kultivator.dunia.langit.LangitFxPayload;
import dev.kultivator.dunia.langit.LangitHartaPayload;
import dev.kultivator.dunia.langit.LangitItems;
import dev.kultivator.dunia.langit.LangitSkyPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.entity.EntityRenderers;

/**
 * Client entry of module L, Langit &amp; Cuaca (DESIGN 34.6 L), wired by its one line in {@code DuniaContentClient}: the
 * four payload receivers, the renderers of {@code meteor_roh} and {@code harta_lahir}, the world geometry
 * ({@link LangitRenderer}, after the 7b renderer in {@code COLLECT_SUBMITS}), the HUD layer ({@link LangitHud}, after the
 * vanilla overlays like the 7b screen effects), the client tick and the cleanup on a disconnect.
 */
public final class LangitClient {

	private static boolean ready;

	public static void init() {
		if (ready) {
			return;
		}
		ready = true;
		ClientPlayNetworking.registerGlobalReceiver(LangitSkyPayload.TYPE, (payload, context) -> LangitState.onSky(payload));
		ClientPlayNetworking.registerGlobalReceiver(LangitCuacaPayload.TYPE, (payload, context) -> LangitState.onWeather(payload));
		ClientPlayNetworking.registerGlobalReceiver(LangitHartaPayload.TYPE, (payload, context) -> LangitState.onBirth(payload));
		ClientPlayNetworking.registerGlobalReceiver(LangitFxPayload.TYPE, (payload, context) -> LangitState.onFx(payload));
		EntityRenderers.register(LangitItems.METEOR, MeteorRohRenderer::new);
		EntityRenderers.register(LangitItems.HARTA, HartaLahirRenderer::new);
		LevelRenderEvents.COLLECT_SUBMITS.register(LangitRenderer::collect);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Kultivator.id("langit_hud"), new LangitHud());
		ClientTickEvents.END_CLIENT_TICK.register(LangitState::tick);
		ClientDisconnect.register((handler, client) -> client.execute(() -> LangitState.clear()));
	}

	private LangitClient() {
	}
}
