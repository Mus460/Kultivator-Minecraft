package dev.kultivator.client.dunia.dimensi.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.disciple.power.PhysiqueVisuals;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;

/**
 * Draws the {@link Headwear} piece (and, for the hungry ghost, its burning eyes) on module D's people. The piece is a
 * player-head model, so it nods and turns with the head; see-through bodies (ghosts) get see-through pieces. Dropped
 * beyond 48 b (24 b on Minimal effects): the silhouette is enough there.
 */
public class HeadwearLayer extends RenderLayer<DiscipleRenderState, PlayerModel> {

	/** The piece of the state (a {@code Headwear} kind). */
	public static final RenderStateDataKey<Integer> KIND = RenderStateDataKey.create(() -> "kultivator:dimensi_headwear");
	/** A second, glowing piece (the ghost's eyes), or {@code Headwear.NONE}. */
	public static final RenderStateDataKey<Integer> GLOW = RenderStateDataKey.create(() -> "kultivator:dimensi_headwear_glow");
	private static final double FAR_SQ = 48.0D * 48.0D;

	private static final RenderType SOLID = RenderTypes.entityCutout(Headwear.TEXTURE);
	private static final RenderType SEE_THROUGH = RenderTypes.entityTranslucent(Headwear.TEXTURE);
	private static final RenderType EYES = RenderTypes.eyes(Headwear.TEXTURE);

	private final PieceModel[] models = new PieceModel[Headwear.KINDS];

	public HeadwearLayer(RenderLayerParent<DiscipleRenderState, PlayerModel> renderer, EntityRendererProvider.Context context) {
		super(renderer);
		for (int i = 1; i < Headwear.KINDS; i++) {
			models[i] = new PieceModel(context.bakeLayer(Headwear.layer(i)));
		}
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, DiscipleRenderState state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		double limit = dev.kultivator.client.EffectQuality.minimal() ? FAR_SQ / 4.0D : FAR_SQ;
		if (state.distanceToCameraSq > limit) {
			return;
		}
		FabricRenderState data = (FabricRenderState) state;
		int kind = data.getDataOrDefault(KIND, Headwear.NONE);
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		boolean ghost = PhysiqueVisuals.translucent(state);
		if (kind > Headwear.NONE && kind < Headwear.KINDS && models[kind] != null) {
			int alpha = ghost ? 150 : 255;
			collector.order(3).submitModel(models[kind], state, poseStack, ghost ? SEE_THROUGH : SOLID, lightCoords, overlay,
					ARGB.color(alpha, 0xFFFFFF), null, state.outlineColor);
		}
		int glow = data.getDataOrDefault(GLOW, Headwear.NONE);
		if (glow > Headwear.NONE && glow < Headwear.KINDS && models[glow] != null) {
			collector.order(4).submitModel(models[glow], state, poseStack, EYES, LightCoordsUtil.FULL_BRIGHT, overlay, -1, null,
					state.outlineColor);
		}
	}

	/** Only the head (with its piece) is drawn; it turns with the body's head through the PlayerModel animation. */
	static final class PieceModel extends PlayerModel {

		PieceModel(ModelPart root) {
			super(root, false);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.head.visible = true;
			this.hat.visible = true;
			this.body.visible = false;
			this.leftArm.visible = false;
			this.rightArm.visible = false;
			this.leftLeg.visible = false;
			this.rightLeg.visible = false;
		}
	}
}
