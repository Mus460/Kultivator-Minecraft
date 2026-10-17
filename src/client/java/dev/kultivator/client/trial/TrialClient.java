package dev.kultivator.client.trial;

import dev.kultivator.client.fx.TrialFx;
import dev.kultivator.trial.TrialEntities;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.renderer.entity.EntityRenderers;

/** Client side of the heart demon: its renderer and the per-tick wisps (TrialFx ignores a second call in one tick). */
public final class TrialClient {

	public static void init() {
		EntityRenderers.register(TrialEntities.HEART_DEMON, HeartDemonRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level != null && minecraft.player != null && !minecraft.isPaused()) {
				TrialFx.tick(minecraft);
			}
		});
	}

	private TrialClient() {
	}
}
