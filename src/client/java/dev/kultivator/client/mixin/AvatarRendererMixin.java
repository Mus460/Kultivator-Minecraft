package dev.kultivator.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.render.MeditationPose;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The meditation pose: remembers in the player's render state how far into the seated pose they are (from the synced
 * look), and lowers the whole model onto the ground while seated. The limbs are posed in {@link PlayerModelMixin}.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void kultivator$extractMeditation(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		MeditationPose.extract(entity, state, partialTicks);
	}

	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
			at = @At("HEAD"))
	private void kultivator$lowerMeditation(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		MeditationPose.lower(state, poseStack);
	}
}
