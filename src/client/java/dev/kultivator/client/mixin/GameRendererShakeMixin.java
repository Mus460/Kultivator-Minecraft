package dev.kultivator.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.fx.spec.CameraShake;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Technique camera shake (DESIGN 22.4.5): {@code GameRenderer.bobHurt(CameraRenderState, PoseStack)} is private in 26.3
 * and returns early, so the shake is applied at HEAD (not TAIL). The body of {@link CameraShake#apply} belongs to
 * P6c-kitab; this hook only calls it.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererShakeMixin {

	@Inject(method = "bobHurt(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"))
	private void kultivator$techniqueShake(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		CameraShake.apply(cameraState, poseStack);
	}
}
