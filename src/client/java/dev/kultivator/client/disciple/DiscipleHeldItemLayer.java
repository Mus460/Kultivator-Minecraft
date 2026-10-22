package dev.kultivator.client.disciple;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.anim.form.FormModels;
import dev.kultivator.client.disciple.power.DutyPoses;
import dev.kultivator.disciple.DiscipleEntity;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Items in a disciple's hands, placed like the vanilla held-item layer; the natal or qi sword of a sword pose is our 3D
 * model and sits in the fist pointing forward. While a duty pose shows, its prop is drawn too (polish plan 2.6): the
 * carrying pole with two buckets over the right shoulder, the ladle in the kitchen, the feed basket on the left arm and
 * the sickle and herb basket in the garden. Hidden while it sits in meditation, when the hands rest on the knees.
 */
public class DiscipleHeldItemLayer extends RenderLayer<DiscipleRenderState, PlayerModel> {

	public DiscipleHeldItemLayer(RenderLayerParent<DiscipleRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, DiscipleRenderState state,
					   float yRot, float xRot) {
		if (state.seated > 0.5F || state.isInvisible) {
			return;
		}
		if (state.heldSword) {
			// the 3D sword: grip in the fist, blade forward along the closed hand
			poseStack.pushPose();
			this.getParentModel().translateToHand(state, HumanoidArm.RIGHT, poseStack);
			swordTransform(poseStack);
			FormModels.submitState(state.rightHandItemState, poseStack, submitNodeCollector, lightCoords, state.outlineColor);
			poseStack.popPose();
		} else {
			hand(state, state.rightHandItemState, HumanoidArm.RIGHT, poseStack, submitNodeCollector, lightCoords);
		}
		hand(state, state.leftHandItemState, HumanoidArm.LEFT, poseStack, submitNodeCollector, lightCoords);
		if (!state.castActive && DutyPoses.showsProp(state)) {
			dutyProps(state, poseStack, submitNodeCollector, lightCoords);
		}
	}

	/**
	 * The 3D sword's place in the fist, after {@code translateToHand} (DESIGN 35.5.4): the grip in the hand, the blade
	 * forward along the closed hand. Shared with the duel's {@code BladeProbeLayer}, so the blade it records is this one.
	 */
	public static void swordTransform(PoseStack poseStack) {
		poseStack.translate(-1.0F / 16.0F, 10.0F / 16.0F, -1.0F / 16.0F);
		poseStack.rotateDegrees(Axis.XP, 90.0F);
		poseStack.scale(0.8F, 0.8F, 0.8F);
	}

	private void dutyProps(DiscipleRenderState state, PoseStack poseStack, SubmitNodeCollector c, int light) {
		PlayerModel model = this.getParentModel();
		switch (state.activity) {
			case DiscipleEntity.ACTIVITY_WATER -> {
				poseStack.pushPose();
				model.root().translateAndRotate(poseStack);
				model.body.translateAndRotate(poseStack);
				// the pole runs front to back over the right shoulder, a bucket hanging at each end
				FormModels.submitPiece("carrying_pole_buckets", poseStack, c, light, 1.0F, -4.0F, -1.0F, 0.0F, 90.0F, 0.0F, 0.0F, 0.75F);
				poseStack.popPose();
			}
			case DiscipleEntity.ACTIVITY_KITCHEN -> inHand(model, state, HumanoidArm.RIGHT, "wok_ladle", poseStack, c, light, 0.8F, 0.0F);
			case DiscipleEntity.ACTIVITY_FARM -> {
				poseStack.pushPose();
				model.root().translateAndRotate(poseStack);
				model.leftArm.translateAndRotate(poseStack);
				FormModels.submitPiece("feed_basket", poseStack, c, light, 1.0F, 1.0F, 7.0F, -3.0F, 0.0F, 0.0F, 0.0F, 0.75F);
				poseStack.popPose();
			}
			case DiscipleEntity.ACTIVITY_GARDEN -> {
				inHand(model, state, HumanoidArm.RIGHT, "sickle", poseStack, c, light, 0.7F, 90.0F);
				inHand(model, state, HumanoidArm.LEFT, "herb_basket", poseStack, c, light, 0.6F, 0.0F);
			}
			default -> {
				// Sekte yang Hidup's activities 13-24 (DESIGN 36.3.3): the owner module's registered props.
				for (dev.kultivator.client.sekte.SektePoses.Prop prop : dev.kultivator.client.sekte.SektePoses.props(state.activity)) {
					inHand(model, state, prop.left() ? HumanoidArm.LEFT : HumanoidArm.RIGHT, prop.piece(), poseStack, c, light, prop.scale(),
							prop.pitch());
				}
			}
		}
	}

	private static void inHand(PlayerModel model, DiscipleRenderState state, HumanoidArm arm, String piece, PoseStack poseStack,
			SubmitNodeCollector c, int light, float scale, float pitch) {
		poseStack.pushPose();
		model.translateToHand(state, arm, poseStack);
		FormModels.submitPiece(piece, poseStack, c, light, 1.0F, arm == HumanoidArm.LEFT ? 1.0F : -1.0F, 10.5F, -0.5F, 0.0F, pitch, 0.0F, scale);
		poseStack.popPose();
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
