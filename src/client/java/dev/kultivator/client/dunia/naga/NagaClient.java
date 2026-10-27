package dev.kultivator.client.dunia.naga;

import dev.kultivator.Kultivator;
import dev.kultivator.client.dunia.DuniaScenes;
import dev.kultivator.dunia.naga.NagaEntities;
import dev.kultivator.dunia.naga.NagaPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.entity.EntityRenderers;

/**
 * Client entry of module B, Naga dan Binatang (DESIGN 34.6 B), wired by one line in {@code DuniaContentClient.init()}:
 * the Jiao and tribulation renderers, the tier 4-6 wolf and fox layers, the payload receivers (contest, moments), the
 * HUD (the contest ring, the Napas Naga gauge) and the client tick of the body chains and particles. Every visual is
 * budgeted by EffectQuality and the 7b EpicBudget, with no post chain or shader of its own (Sodium and Iris safe).
 */
public final class NagaClient {

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		EntityRenderers.register(NagaEntities.NAGA_LIAR, NagaRenderer::new);
		EntityRenderers.register(NagaEntities.KESENGSARAAN, KesengsaraanRenderer::new);
		BeastTierLayers.init();
		ClientPlayNetworking.registerGlobalReceiver(NagaPayloads.Contest.TYPE, (payload, context) -> NagaHud.onContest(payload));
		ClientPlayNetworking.registerGlobalReceiver(NagaPayloads.Moment.TYPE, (payload, context) -> NagaMoments.play(payload));
		ClientPlayNetworking.registerGlobalReceiver(NagaPayloads.Far.TYPE, (payload, context) -> NagaFar.onFar(payload));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("naga_hud"), new NagaHud());
		LevelRenderEvents.START_MAIN.register(context -> NagaVisuals.beginFrame());
		// the sky-projected silhouettes of Jiao too far to track (DESIGN 34.5.11 level of detail)
		LevelRenderEvents.COLLECT_SUBMITS.register(NagaFar::collect);
		// the world-space telegraphs of the kind skills (whirl, thunder marks)
		LevelRenderEvents.COLLECT_SUBMITS.register(NagaStrikes::collect);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			NagaVisuals.tick(minecraft);
			NagaFar.tick(minecraft);
			NagaStrikes.tick(minecraft);
			NagaHud.tick(minecraft);
			BeastTierLayers.tick(minecraft);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			NagaVisuals.clear();
			NagaFar.clear();
			NagaStrikes.clear();
			NagaHud.clear();
			BeastTierLayers.clear();
			NagaModels.clear();
		}));
		// a subdued dragon and an evolution are rare: one shot each per minute at most (the spine default)
		DuniaScenes.cooldown("naga/tunduk", 1200);
	}

	private NagaClient() {
	}
}
