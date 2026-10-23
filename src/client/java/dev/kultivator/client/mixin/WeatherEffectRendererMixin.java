package dev.kultivator.client.mixin;

import dev.kultivator.client.sekte.langit.LangitClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sekte Langit (DESIGN 37.6.5, 37.7.2): above the sea of clouds, inside a sky island's air, "the sky is always clear;
 * the storm rages below your feet". After vanilla extracts this frame's rain and snow columns, they are emptied when the
 * camera is above the cloud sea of a synced, standing sky hall (client switch "Kebal cuaca"). Drawing only, on this
 * client only: the weather itself, the server, other players and every block are untouched. Idle cost one empty-list
 * check.
 */
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;)V",
			at = @At("TAIL"))
	private void kultivator$clearAboveClouds(ClientLevel level, float partialTicks, Vec3 cameraPos, WeatherRenderState renderState, CallbackInfo ci) {
		if (LangitClient.weatherSheltered(cameraPos.x, cameraPos.y, cameraPos.z)) {
			renderState.rainColumns.clear();
			renderState.snowColumns.clear();
			renderState.intensity = 0.0F;
		}
	}
}
