package dev.kultivator.client.mixin;

import dev.kultivator.client.codex.Codex;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Roda Segel takes the mouse while it is open (DESIGN 22.12.7): the private {@code MouseHandler.turnPlayer(double)}
 * of 26.3 is cancelled when {@link Codex#wheelInput} consumes the accumulated deltas (the handler resets them right
 * after), and the public {@code onScroll(long, double, double)} steps around the ring instead of changing the hotbar
 * slot. Both only act while the wheel is open and no screen is shown; WASD keeps working.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

	@Shadow
	private double accumulatedDX;

	@Shadow
	private double accumulatedDY;

	@Inject(method = "turnPlayer(D)V", at = @At("HEAD"), cancellable = true)
	private void kultivator$sealWheel(double mousea, CallbackInfo ci) {
		if (Codex.wheelInput(this.accumulatedDX, this.accumulatedDY)) {
			ci.cancel();
		}
	}

	@Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
	private void kultivator$sealWheelScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
		if (Codex.wheelScroll(yoffset != 0.0D ? yoffset : -xoffset)) {
			ci.cancel();
		}
	}
}
