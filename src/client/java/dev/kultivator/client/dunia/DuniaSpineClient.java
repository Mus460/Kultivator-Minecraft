package dev.kultivator.client.dunia;

import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaGhostPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;

/**
 * The client side of the Dunia spine (DESIGN 34.4.9): a no-op renderer for the anchor (defensive: the anchor is never
 * sent), the ghost preview receiver of {@code uji struktur lihat}, and the cleanup on disconnect. {@link DuniaScenes} and
 * {@link SegmentChain} are the shared helpers of the modules.
 */
public final class DuniaSpineClient {

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		EntityRenderers.register(DuniaAnchors.TYPE, NoopRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(DuniaGhostPayload.TYPE, (payload, context) -> DuniaGhost.onPayload(payload));
		ClientTickEvents.END_CLIENT_TICK.register(client -> DuniaGhost.tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			DuniaGhost.clear();
			DuniaScenes.reset();
		}));
	}

	private DuniaSpineClient() {
	}
}
