package dev.kultivator.client.mixin;

import dev.kultivator.client.epic.kosmik.Kosmik3D;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Film Kosmik 3D (DESIGN 32.1.2): rain and snow are drawn as columns within a few blocks of the camera, inside the
 * stage's dome, so while a 3D stage covers the frame they would fall through space. This empties this frame's weather
 * columns on this client only; the weather itself, its sounds (ducked by the film) and every other player are untouched.
 * Idle cost one boolean.
 */
@Mixin(WeatherEffectRenderer.class)
public abstract class KosmikWeatherMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;)V",
			at = @At("TAIL"))
	private void kultivator$noWeatherInStage(ClientLevel level, float partialTicks, Vec3 cameraPos, WeatherRenderState renderState, CallbackInfo ci) {
		if (Kosmik3D.covering()) {
			renderState.rainColumns.clear();
			renderState.snowColumns.clear();
			renderState.intensity = 0.0F;
		}
	}
}
