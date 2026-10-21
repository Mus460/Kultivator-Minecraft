package dev.kultivator.client.mixin;

import dev.kultivator.client.codex.Codex;
import dev.kultivator.client.hub.QuickWheel;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Roda Segel (DESIGN 22.12.7) and the Akses Cepat wheel (DESIGN 33.3.1) take the mouse while one of them is open:
 * the private {@code MouseHandler.turnPlayer(double)} of 26.3 is cancelled when {@link Codex#wheelInput} or
 * {@link QuickWheel#input} consumes the accumulated deltas (the handler resets them right after), and the public
 * {@code onScroll(long, double, double)} steps around the ring instead of changing the hotbar slot. Both only act while a
 * wheel is open and no screen is shown (the two are never open together); WASD keeps working.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

	@Shadow
	private double accumulatedDX;

	@Shadow
	private double accumulatedDY;

	@Inject(method = "turnPlayer(D)V", at = @At("HEAD"), cancellable = true)
	private void kultivator$sealWheel(double mousea, CallbackInfo ci) {
		if (Codex.wheelInput(this.accumulatedDX, this.accumulatedDY) || QuickWheel.input(this.accumulatedDX, this.accumulatedDY)) {
			ci.cancel();
		}
	}

	@Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
	private void kultivator$sealWheelScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
		double amount = yoffset != 0.0D ? yoffset : -xoffset;
		if (Codex.wheelScroll(amount) || QuickWheel.scroll(amount)) {
			ci.cancel();
		}
	}
}
