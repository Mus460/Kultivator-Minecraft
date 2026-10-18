package dev.kultivator.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.kultivator.client.epic.camera.CinematicDirector;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 7b shot roll and hand (DESIGN 23.7.1): the Dutch roll at the HEAD of the private {@code bobHurt}, beside (not inside)
 * the 6c {@code GameRendererShakeMixin}, and the vanilla first-person hand hidden while a shot runs. Only calls
 * {@link CinematicDirector}.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererCinematicMixin {

	@Inject(method = "bobHurt(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"))
	private void kultivator$cinematicRoll(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		float roll = CinematicDirector.roll();
		if (roll != 0.0F) {
			poseStack.rotateDegrees(Axis.ZP, roll);
		}
	}

	@Inject(method = "renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V",
			at = @At("HEAD"), cancellable = true)
	private void kultivator$cinematicHand(CameraRenderState cameraState, PlayerRenderState playerState, GpuTextureView depthTextureView, CallbackInfo ci) {
		if (CinematicDirector.hideHand()) {
			ci.cancel();
		}
	}
}
