package dev.kultivator.client.mixin.duel;

import dev.kultivator.client.duel.DuelInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The duel's hold on the two mouse buttons (DESIGN 35.3.2, 35.3.14; module U, priority 900 so other mods' HEAD hooks see
 * the click only when the duel leaves it to vanilla):
 * <ul>
 * <li>{@code startAttack} / {@code continueAttack}: cancelled only in KLASIK mode while the player fights a formal duel
 * with a weapon, and during a Blade Lock (pushes); never while Better Combat is installed (its attacks stay its own).</li>
 * <li>{@code startUseItem}: cancelled while the use button is a deflect or guard (a weapon without its own use, the off
 * hand empty or without a use and no shield; outside a duel only with the setting on and the crosshair on nothing).</li>
 * </ul>
 * Everything else, including every click outside these cases, is vanilla.
 */
@Mixin(value = Minecraft.class, priority = 900)
public abstract class DuelInputMixin {

	@Inject(method = "startAttack()Z", at = @At("HEAD"), cancellable = true)
	private void kultivator$duelAttack(CallbackInfoReturnable<Boolean> cir) {
		if (DuelInput.onAttackPress()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack(Z)V", at = @At("HEAD"), cancellable = true)
	private void kultivator$duelContinueAttack(boolean down, CallbackInfo ci) {
		if (down && DuelInput.onAttackContinue()) {
			ci.cancel();
		}
	}

	@Inject(method = "startUseItem()V", at = @At("HEAD"), cancellable = true)
	private void kultivator$duelUse(CallbackInfo ci) {
		if (DuelInput.onUsePress()) {
			ci.cancel();
		}
	}
}
