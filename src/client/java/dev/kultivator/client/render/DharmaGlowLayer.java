package dev.kultivator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.data.VisualState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * The Dharma form (Wujud Dharma) glow: the player model drawn again over itself with the skin tinted bright gold,
 * translucent and full bright (a golden sheen), wrapped in an inflated shell of soft golden flame streaks (Normal
 * quality and up) and a second, wider and fainter shell at Maximal: a golden outline around the giant avatar. Both
 * breathe slowly. The golden motes and the halo are particles in TechniqueFx.
 */
public class DharmaGlowLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

	private static final RenderType SHELL = RenderTypes.entityTranslucentEmissive(Kultivator.id("textures/entity/dharma_glow.png"));
	private static final int GOLD = 0xFFD54A;
	private static final int PALE_GOLD = 0xFFF0B0;

	private final PlayerModel shellWide;
	private final PlayerModel shellSlim;
	private final PlayerModel outerWide;
	private final PlayerModel outerSlim;

	public DharmaGlowLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityRendererProvider.Context context) {
		super(renderer);
		this.shellWide = new PlayerModel(context.bakeLayer(CultivatorModels.GLOW), false);
		this.shellSlim = new PlayerModel(context.bakeLayer(CultivatorModels.GLOW_SLIM), true);
		this.outerWide = new PlayerModel(context.bakeLayer(CultivatorModels.GLOW_OUTER), false);
		this.outerSlim = new PlayerModel(context.bakeLayer(CultivatorModels.GLOW_OUTER_SLIM), true);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state,
					   float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		VisualState visual = CultivatorLook.visual(state);
		if (!visual.has(VisualState.DHARMA)) {
			return;
		}
		float pulse = 0.5F + 0.5F * Mth.sin(state.ageInTicks * 0.16F);
		boolean slim = state.skin.model() == PlayerModelType.SLIM;

		// The golden sheen over the skin itself (same geometry, drawn after it).
		Identifier skin = state.skin.body().texturePath();
		int sheen = ARGB.color(Math.round(Mth.lerp(pulse, 70.0F, 115.0F)), GOLD);
		submitNodeCollector.order(2).submitModel(this.getParentModel(), state, poseStack, RenderTypes.entityTranslucentEmissive(skin),
				LightCoordsUtil.FULL_BRIGHT, LivingEntityRenderer.getOverlayCoords(state, 0.0F), sheen, null, 0);
		if (EffectQuality.minimal()) {
			return;
		}
		int shell = ARGB.color(Math.round(Mth.lerp(pulse, 120.0F, 175.0F)), GOLD);
		submitNodeCollector.order(3).submitModel(slim ? shellSlim : shellWide, state, poseStack, SHELL, LightCoordsUtil.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, shell, null, 0);
		if (EffectQuality.maximal()) {
			int outer = ARGB.color(Math.round(Mth.lerp(1.0F - pulse, 60.0F, 105.0F)), PALE_GOLD);
			submitNodeCollector.order(3).submitModel(slim ? outerSlim : outerWide, state, poseStack, SHELL, LightCoordsUtil.FULL_BRIGHT,
					OverlayTexture.NO_OVERLAY, outer, null, 0);
		}
	}
}
