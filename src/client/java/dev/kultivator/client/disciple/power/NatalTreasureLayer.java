package dev.kultivator.client.disciple.power;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.disciple.power.PowerIds;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * The natal treasure on a disciple's back (DESIGN 20.3.17), like the cultivator's back sword: the form's item model
 * (sword, parasol, wok, bell), silver from fold 3 and gold from fold 9; from fold 14 ("Payung Abadi") it leaves the back
 * and slowly orbits the disciple. The item state is filled by the renderer from the synced power bits.
 */
public class NatalTreasureLayer extends RenderLayer<DiscipleRenderState, PlayerModel> {

	private static final float CENTER_Y = 6.5F / 16.0F;
	private static final float CENTER_Z = 3.9F / 16.0F;
	private static final float TILT = 15.0F;
	private static final float ORBIT_RADIUS = 0.95F;

	public NatalTreasureLayer(RenderLayerParent<DiscipleRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, DiscipleRenderState state,
					   float yRot, float xRot) {
		if (state.isInvisible || state.natalItem.isEmpty()) {
			return;
		}
		PlayerModel model = this.getParentModel();
		int form = PowerIds.natalForm(state.power);
		poseStack.pushPose();
		model.root().translateAndRotate(poseStack);
		if ((state.power & PowerIds.BIT_NATAL_ORBIT) != 0) {
			// The Eternal Parasol: around the disciple at chest height, turning to face its path.
			float angle = state.ageInTicks * 3.0F;
			poseStack.rotateDegrees(Axis.YP, angle);
			poseStack.translate(ORBIT_RADIUS, 0.25F + (float) Math.sin(state.ageInTicks * 0.08F) * 0.06F, 0.0F);
			poseStack.rotateDegrees(Axis.YP, 90.0F);
			poseStack.scale(0.7F, 0.7F, 0.7F);
		} else {
			model.body.translateAndRotate(poseStack);
			poseStack.translate(0.0F, CENTER_Y, CENTER_Z);
			switch (form) {
				case PowerIds.NATAL_WOK -> {
					// The turtle-shell wok sits flat on the back.
					poseStack.rotateDegrees(Axis.YP, 180.0F);
					poseStack.scale(0.85F, 0.85F, 0.85F);
				}
				case PowerIds.NATAL_BELL -> {
					poseStack.translate(0.0F, 0.18F, 0.0F);
					poseStack.rotateDegrees(Axis.YP, 180.0F);
					poseStack.scale(0.55F, 0.55F, 0.55F);
				}
				default -> {
					poseStack.rotateDegrees(Axis.ZP, TILT);
					poseStack.rotateDegrees(Axis.YP, 180.0F);
					poseStack.scale(0.8F, 0.8F, 0.8F);
				}
			}
		}
		state.natalItem.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}
}
