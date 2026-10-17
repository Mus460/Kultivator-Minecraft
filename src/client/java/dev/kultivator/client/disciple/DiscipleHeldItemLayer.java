package dev.kultivator.client.disciple;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Items in a disciple's hands (normally none), placed like the vanilla held-item layer. Hidden while it sits in
 * meditation, when the hands rest on the knees.
 */
public class DiscipleHeldItemLayer extends RenderLayer<DiscipleRenderState, PlayerModel> {

	public DiscipleHeldItemLayer(RenderLayerParent<DiscipleRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, DiscipleRenderState state,
					   float yRot, float xRot) {
		if (state.seated > 0.5F) {
			return;
		}
		hand(state, state.rightHandItemState, HumanoidArm.RIGHT, poseStack, submitNodeCollector, lightCoords);
		hand(state, state.leftHandItemState, HumanoidArm.LEFT, poseStack, submitNodeCollector, lightCoords);
	}

	private void hand(DiscipleRenderState state, ItemStackRenderState item, HumanoidArm arm, PoseStack poseStack,
					  SubmitNodeCollector submitNodeCollector, int lightCoords) {
		if (item.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		this.getParentModel().translateToHand(state, arm, poseStack);
		poseStack.rotateDegrees(Axis.XP, -90.0F);
		poseStack.rotateDegrees(Axis.YP, 180.0F);
		poseStack.translate((arm == HumanoidArm.LEFT ? -1.0F : 1.0F) / 16.0F, 2.0F / 16.0F, -10.0F / 16.0F);
		item.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}
}
