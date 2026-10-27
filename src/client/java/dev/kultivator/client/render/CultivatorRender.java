package dev.kultivator.client.render;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;

/**
 * The cultivator look on the player model, all driven by the synced VisualState (and its SHOW_* cosmetic switches):
 * the robe ({@link RobeLayer}), the sword on the back ({@link BackSwordLayer}), the flying sword under the feet
 * ({@link FlyingSwordLayer}), the glowing eyes ({@link EyesLayer}), the Dao circle on the ground ({@link DaoCircleLayer})
 * and the Dharma form glow ({@link DharmaGlowLayer}). The layers are added to every avatar renderer (players, wide and
 * slim; other avatar entities have no look and draw nothing). Render layers never run for your own first-person view.
 */
public final class CultivatorRender {

	public static void init() {
		CultivatorModels.register();
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new RobeLayer(avatar, context));
				helper.register(new BackSwordLayer(avatar, context));
				helper.register(new FlyingSwordLayer(avatar, context));
				helper.register(new EyesLayer(avatar));
				helper.register(new DaoCircleLayer(avatar));
				helper.register(new DharmaGlowLayer(avatar, context));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> CultivatorLook.clear()));
	}

	private CultivatorRender() {
	}
}
