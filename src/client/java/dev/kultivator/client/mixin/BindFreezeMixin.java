package dev.kultivator.client.mixin;

import dev.kultivator.client.anim.BindVisuals;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bound bodies (DESIGN 27.4): frost, stopped time, seals and chains freeze the walk cycle and idle animation where the
 * bind caught them, frost shivers, and every bind tints the body (ice blue, grey, a red-violet pulse...). The chains and
 * glyphs around the body are drawn by BindFx and ArtModels. Renderers that override the model tint without calling the
 * vanilla one keep their own tint.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class BindFreezeMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void kultivator$bindFreeze(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		BindVisuals.extract(entity, state, partialTicks);
	}

	@Inject(method = "getModelTint(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)I", at = @At("RETURN"), cancellable = true)
	private void kultivator$bindTint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
		int tint = BindVisuals.tint(state);
		if (tint != -1) {
			cir.setReturnValue(ARGB.multiply(cir.getReturnValueI(), tint));
		}
	}
}
