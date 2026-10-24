package dev.kultivator.client.dunia.tempat.hidup;

import dev.kultivator.dunia.tempat.hidup.PendudukSnapshot;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/**
 * Client init of 38NL NPC life (DESIGN 38.N.6): the far crowd and the stand crowds ({@link ImpostorRenderer}, named apart
 * from {@code client/sekte/turnamen/PenontonRenderer}). Near bodies need nothing here: their clips, props, gaze, faces
 * and sleep are DESIGN 40's (40G clip player, 40R renderers) driven by the synced {@code DATA_GERAK}/{@code DATA_PROP}/
 * {@code DATA_LOOK_AT} the server sets through {@code SosokApi}. Called once from {@code TempatClient.init()}. Every
 * visual obeys {@code EffectQuality} and {@code EpicBudget}, with no post chain (Sodium and Iris safe); client code only,
 * never referenced by server code.
 */
public final class HidupClient {

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(PendudukSnapshot.TYPE, (payload, context) -> ImpostorRenderer.terima(payload));
		LevelRenderEvents.COLLECT_SUBMITS.register(ImpostorRenderer::collect);
		ClientTickEvents.END_CLIENT_TICK.register(ImpostorRenderer::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ImpostorRenderer.bersihkan());
	}

	private HidupClient() {
	}
}
