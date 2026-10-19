package dev.kultivator.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.anim.CastPoses;
import dev.kultivator.client.anim.FirstPersonCast;
import dev.kultivator.client.anim.PoseApplier;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The cast pose in first person (DESIGN 27.2.5): each hand's pose stack is moved right after the vanilla
 * {@code pushPose} in {@code submitArmWithItem} (the arm and anything it holds), and a two-handed pose (seals, twin
 * palms, raises, spreads) also draws the empty off hand, which vanilla never shows.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsMixin {

	@Inject(method = "submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
	private void kultivator$castPoseArm(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
			InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
		AvatarRenderState avatar = playerState.avatarRenderState;
		if (avatar == null) {
			return;
		}
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? avatar.mainArm : avatar.mainArm.getOpposite();
		FirstPersonCast.apply(poseStack, arm, partialTicks);
	}

	@Inject(method = "submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V",
			at = @At("TAIL"))
	private void kultivator$castPoseOffHand(float partialTicks, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, PlayerRenderState playerState,
			FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		AvatarRenderState avatar = playerState.avatarRenderState;
		if (avatar == null || avatar.isInvisible || state.handRenderSelection == null || state.isScoping || !state.offHandItem.isEmpty()) {
			return;
		}
		CastPoses.Snapshot pose = FirstPersonCast.localPose(partialTicks);
		if (!PoseApplier.twoHanded(pose)) {
			return;
		}
		HumanoidArm arm = avatar.mainArm.getOpposite();
		poseStack.pushPose();
		FirstPersonCast.apply(poseStack, arm, partialTicks);
		((FirstPersonHandsInvoker) this).kultivator$renderPlayerArm(poseStack, submitNodeCollector, avatar.lightCoords, 0.0F, 0.0F, arm, playerState);
		poseStack.popPose();
	}
}
