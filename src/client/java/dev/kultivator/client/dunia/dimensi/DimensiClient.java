package dev.kultivator.client.dunia.dimensi;

import dev.kultivator.Kultivator;
import dev.kultivator.client.dunia.dimensi.render.ArwahRenderer;
import dev.kultivator.client.dunia.dimensi.render.Headwear;
import dev.kultivator.client.dunia.dimensi.render.KepitingRenderer;
import dev.kultivator.client.dunia.dimensi.render.LautNpcRenderer;
import dev.kultivator.client.dunia.dimensi.render.PenyuRenderer;
import dev.kultivator.client.dunia.dimensi.render.TandaRenderer;
import dev.kultivator.client.dunia.dimensi.render.UlarLautRenderer;
import dev.kultivator.client.dunia.dimensi.render.YakshaRenderer;
import dev.kultivator.dunia.dimensi.DimensiEntities;
import dev.kultivator.dunia.dimensi.DimensiNet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;

/**
 * Module D's client (DESIGN 34.6 D), wired by ONE line in {@code DuniaContentClient.init()}: the receivers of the
 * module's own payloads (scrolls, soul state, world moments), the renderers and model layers of its people, monsters,
 * the turtle island and the marks, the soul's HUD (an edge tint and the timer, no post chain) and the ambience of the
 * two dimensions. Every effect is budgeted by {@code EffectQuality}; nothing here touches a block.
 */
public final class DimensiClient {

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		// model layers (registered before the entity models are baked)
		Headwear.register();
		KepitingRenderer.register();
		PenyuRenderer.register();
		UlarLautRenderer.register();
		// renderers
		EntityRenderers.register(DimensiEntities.ARWAH, ArwahRenderer::new);
		EntityRenderers.register(DimensiEntities.RAJA_NAGA, LautNpcRenderer::new);
		EntityRenderers.register(DimensiEntities.YAKSHA, YakshaRenderer::new);
		EntityRenderers.register(DimensiEntities.ULAR_LAUT, UlarLautRenderer::new);
		EntityRenderers.register(DimensiEntities.KEPITING_BATU, KepitingRenderer::new);
		EntityRenderers.register(DimensiEntities.PENYU_AO, PenyuRenderer::new);
		EntityRenderers.register(DimensiEntities.PENYU_DEK, NoopRenderer::new);
		EntityRenderers.register(DimensiEntities.TANDA, TandaRenderer::new);
		// payloads (handlers run on the client thread)
		ClientPlayNetworking.registerGlobalReceiver(DimensiNet.Open.TYPE, (payload, context) -> DimensiScreen.open(payload));
		ClientPlayNetworking.registerGlobalReceiver(DimensiNet.State.TYPE, (payload, context) -> DimensiState.onState(payload));
		ClientPlayNetworking.registerGlobalReceiver(DimensiNet.Fx.TYPE, (payload, context) -> DimensiFx.onFx(payload));
		// the soul's view
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("dimensi_jiwa"), new DimensiHud());
		ClientTickEvents.END_CLIENT_TICK.register(DimensiClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			DimensiState.clear();
			DimensiFx.clear();
			UlarLautRenderer.clearChains();
		});
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		DimensiFx.tick(minecraft);
		// serpents live at sea, but the test command may call one anywhere: the chains follow every loaded serpent
		if (!minecraft.isPaused()) {
			UlarLautRenderer.tickChains(minecraft.level, minecraft.player.getEyePosition());
		}
	}

	private DimensiClient() {
	}
}
