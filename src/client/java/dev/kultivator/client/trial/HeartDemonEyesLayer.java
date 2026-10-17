package dev.kultivator.client.trial;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Glowing red eyes on the demon's face: the emissive "eyes" render type (like spiders and endermen) with a 64x64
 * overlay in the player-skin layout that only has pixels at the standard eye spots of the head's front face.
 */
public class HeartDemonEyesLayer extends RenderLayer<HeartDemonRenderState, PlayerModel> {

	private static final RenderType EYES = RenderTypes.eyes(Kultivator.id("textures/entity/heart_demon_eyes.png"));

	public HeartDemonEyesLayer(RenderLayerParent<HeartDemonRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, HeartDemonRenderState state,
					   float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		submitNodeCollector.order(1)
				.submitModel(this.getParentModel(), state, poseStack, EYES, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
	}
}
