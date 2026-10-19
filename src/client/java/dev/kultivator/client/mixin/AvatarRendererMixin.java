package dev.kultivator.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.anim.PoseApplier;
import dev.kultivator.client.anim.PoseRenderData;
import dev.kultivator.client.render.MeditationPose;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The meditation seat and the cast pose: remembers in the player's render state how far into the seated pose they are
 * (from the synced look) and which cast pose and form they show (DESIGN 27.2, 27.4); lowers the model onto the ground
 * while seated and lifts it for floating or hopping poses (setupRotations HEAD, the world frame at the feet), and tilts the
 * whole body into a dash (setupRotations TAIL, the facing frame). The limbs are posed in {@link PlayerModelMixin}.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void kultivator$extractMeditation(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		MeditationPose.extract(entity, state, partialTicks);
		PoseRenderData.extract(entity, state, partialTicks);
	}

	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
			at = @At("HEAD"))
	private void kultivator$lowerMeditation(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		MeditationPose.lower(state, poseStack);
		PoseApplier.lift(state, poseStack);
	}

	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
			at = @At("TAIL"))
	private void kultivator$tiltCastPose(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		PoseApplier.tilt(state, poseStack);
	}
}
