package dev.kultivator.client.disciple.power;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.anim.form.FormModels;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.disciple.power.PowerIds;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;

/**
 * The natal treasure on a disciple's back (DESIGN 20.3.17), like the cultivator's back sword, now as our 3D models
 * (polish plan [225]): the jian slung diagonally with the hilt over the right shoulder, the closed parasol upright, the
 * turtle-shell wok with its shell outward, the bell hanging between the shoulder blades; silver from fold 3, gold from
 * fold 9. From fold 14 ("Payung Abadi") the treasure leaves the back and slowly orbits the disciple, and a parasol opens
 * above its head and turns. The item states are filled by the renderer from the synced power bits.
 */
public class NatalTreasureLayer extends RenderLayer<DiscipleRenderState, PlayerModel> {

	private static final float BACK_Z = 3.7F / 16.0F;
	private static final float ORBIT_RADIUS = 0.95F;

	public NatalTreasureLayer(RenderLayerParent<DiscipleRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, DiscipleRenderState state,
					   float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		PlayerModel model = this.getParentModel();
		if (!state.parasolItem.isEmpty()) {
			// the Eternal Parasol opens over the head and turns slowly
			poseStack.pushPose();
			model.root().translateAndRotate(poseStack);
			poseStack.translate(-3.0F / 16.0F, -15.0F / 16.0F + Mth.sin(state.ageInTicks * 0.08F) * 0.02F, 0.0F);
			poseStack.rotateDegrees(Axis.YP, state.ageInTicks * 2.0F);
			poseStack.scale(0.9F, 0.9F, 0.9F);
			FormModels.submitState(state.parasolItem, poseStack, submitNodeCollector, lightCoords, state.outlineColor);
			poseStack.popPose();
		}
		if (state.natalItem.isEmpty()) {
			return;
		}
		int form = PowerIds.natalForm(state.power);
		poseStack.pushPose();
		model.root().translateAndRotate(poseStack);
		if ((state.power & PowerIds.BIT_NATAL_ORBIT) != 0) {
			// round the disciple at chest height, turning to face its path
			float angle = state.ageInTicks * 3.0F;
			poseStack.rotateDegrees(Axis.YP, angle);
			poseStack.translate(ORBIT_RADIUS, 0.25F + (float) Math.sin(state.ageInTicks * 0.08F) * 0.06F, 0.0F);
			poseStack.rotateDegrees(Axis.YP, 90.0F);
			if (form == PowerIds.NATAL_SWORD) {
				// the sword flies level, tip first
				poseStack.rotateDegrees(Axis.XP, 90.0F);
			}
			poseStack.scale(0.7F, 0.7F, 0.7F);
		} else {
			model.body.translateAndRotate(poseStack);
			switch (form) {
				case PowerIds.NATAL_WOK -> {
					// the shell outward, the bowl against the back
					poseStack.translate(0.0F, 6.0F / 16.0F, BACK_Z + 0.05F);
					poseStack.rotateDegrees(Axis.XP, 90.0F);
					poseStack.scale(0.7F, 0.7F, 0.7F);
				}
				case PowerIds.NATAL_BELL -> {
					poseStack.translate(0.0F, 3.0F / 16.0F, BACK_Z);
					poseStack.scale(0.5F, 0.5F, 0.5F);
				}
				case PowerIds.NATAL_PARASOL -> {
					poseStack.translate(0.0F, 6.0F / 16.0F, BACK_Z);
					poseStack.rotateDegrees(Axis.ZP, -15.0F);
					poseStack.scale(0.72F, 0.72F, 0.72F);
				}
				default -> {
					// the jian: grip at the right shoulder, blade down to the left hip
					poseStack.translate(-2.5F / 16.0F, 2.0F / 16.0F, BACK_Z);
					poseStack.rotateDegrees(Axis.ZP, 150.0F);
					poseStack.scale(0.8F, 0.8F, 0.8F);
				}
			}
		}
		FormModels.submitState(state.natalItem, poseStack, submitNodeCollector, lightCoords, state.outlineColor);
		poseStack.popPose();
	}
}
