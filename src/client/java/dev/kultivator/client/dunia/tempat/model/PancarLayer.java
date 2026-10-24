package dev.kultivator.client.dunia.tempat.model;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.EffectQuality;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.function.ToDoubleFunction;

/**
 * The emissive pass of a 38 creature (DESIGN 38.O.2): the parent model drawn again with a glow texture through the vanilla
 * {@code eyes} render type (full bright, alpha blended, no post chain: Sodium and Iris safe). The glow texture has the
 * same layout as the body texture and is transparent wherever nothing glows. Its strength (0..1) comes from the state;
 * on Minimal effects it is drawn only within 16 blocks, otherwise within 48.
 */
public class PancarLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {

	private final RenderType type;
	private final ToDoubleFunction<S> strength;

	public PancarLayer(RenderLayerParent<S, M> renderer, Identifier glowTexture, ToDoubleFunction<S> strength) {
		super(renderer);
		this.type = RenderTypes.eyes(glowTexture);
		this.strength = strength;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		double far = EffectQuality.minimal() ? 16.0D : 48.0D;
		if (state.distanceToCameraSq > far * far) {
			return;
		}
		float k = Mth.clamp((float) strength.applyAsDouble(state), 0.0F, 1.0F);
		if (k <= 0.02F) {
			return;
		}
		int alpha = Math.round(255.0F * k);
		collector.order(1).submitModel(getParentModel(), state, poseStack, type, lightCoords, LivingEntityRenderer.getOverlayCoords(state, 0.0F),
				ARGB.color(alpha, 0xFFFFFF), null, state.outlineColor);
	}
}
