package dev.kultivator.client.mixin;

import dev.kultivator.client.epic.camera.CinematicDirector;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 7b shots pause mouse look (DESIGN 23.7.1): the private {@code MouseHandler.turnPlayer(double)} is cancelled while
 * {@link CinematicDirector#consumeMouse} takes the accumulated deltas; a big motion ends the shot instead. The 6c
 * {@code MouseHandlerMixin} stays untouched beside it.
 */
@Mixin(MouseHandler.class)
public abstract class MouseCinematicMixin {

	@Shadow
	private double accumulatedDX;

	@Shadow
	private double accumulatedDY;

	@Inject(method = "turnPlayer(D)V", at = @At("HEAD"), cancellable = true)
	private void kultivator$cinematicLook(double mousea, CallbackInfo ci) {
		if (CinematicDirector.consumeMouse(this.accumulatedDX, this.accumulatedDY)) {
			ci.cancel();
		}
	}
}
