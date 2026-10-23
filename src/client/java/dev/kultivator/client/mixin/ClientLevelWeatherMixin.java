package dev.kultivator.client.mixin;

import dev.kultivator.client.sekte.langit.LangitClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sekte Langit (DESIGN 37.6.5): the client rain ticker ({@code ClientLevel.tickWeatherEffects}: the splash particles on
 * the blocks round the camera and the {@code WEATHER_RAIN} / {@code WEATHER_RAIN_ABOVE} patter) is skipped while the
 * camera is above the cloud sea of a synced, standing sky hall (client switch "Kebal cuaca"). The rain still falls on
 * the ground below for everyone there; nothing is changed but this client's particles and sounds.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelWeatherMixin {

	@Inject(method = "tickWeatherEffects()V", at = @At("HEAD"), cancellable = true)
	private void kultivator$noRainAboveClouds(CallbackInfo ci) {
		Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
		if (LangitClient.weatherSheltered(camera.x, camera.y, camera.z)) {
			ci.cancel();
		}
	}
}
