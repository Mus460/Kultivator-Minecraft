package dev.kultivator.client.fx.spec;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;

/**
 * The AVATAR and CLONE renderers on players (DESIGN 22.4.4): the caster's own model drawn again at render time, scaled
 * up about the feet as a translucent full-bright colossus, and tinted copies turning on a ring around it. No entity is
 * created (never a {@code RemotePlayer} in the level). Render layers never run for one's own first-person view; the
 * particle outline of {@link RendererRun} covers that case and Minimal quality.
 */
final class AvatarLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

	/** Model space: the feet are 1.501 below the model origin (y points down after the renderer's flip). */
	private static final float FEET = 1.501F;

	AvatarLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot,
			float xRot) {
		if (state.isInvisible) {
			return;
		}
		AvatarFx.Avatar avatar = AvatarFx.avatarOf(state.id);
		AvatarFx.Clones clones = AvatarFx.clonesOf(state.id);
		if (avatar == null && clones == null) {
			return;
		}
		Identifier skin = state.skin.body().texturePath();
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		if (avatar != null) {
			float fade = AvatarFx.fade(avatar.start(), avatar.end());
			float grow = Math.min(1.0F, (Util.getMillis() - avatar.start()) / 400.0F);
			float scale = 1.0F + (avatar.scale() - 1.0F) * grow;
			int color = ARGB.color(Math.round(avatar.alpha() * fade), avatar.rgb());
			poseStack.pushPose();
			poseStack.translate(0.0F, FEET, 0.0F);
			poseStack.scale(scale, scale, scale);
			poseStack.translate(0.0F, -FEET, 0.0F);
			submitNodeCollector.order(4).submitModel(this.getParentModel(), state, poseStack, RenderTypes.entityTranslucentEmissive(skin),
					LightCoordsUtil.FULL_BRIGHT, overlay, color, null, 0);
			poseStack.popPose();
		}
		if (clones != null) {
			float fade = AvatarFx.fade(clones.start(), clones.end());
			int color = ARGB.color(Math.round(150 * fade), clones.rgb());
			double turn = (Util.getMillis() - clones.start()) / 1000.0D * 0.6D;
			for (int i = 0; i < clones.count(); i++) {
				double angle = Math.PI * 2.0D * i / clones.count() + turn;
				poseStack.pushPose();
				poseStack.translate((float) (Math.cos(angle) * clones.radius()), 0.0F, (float) (Math.sin(angle) * clones.radius()));
				submitNodeCollector.order(4).submitModel(this.getParentModel(), state, poseStack, RenderTypes.entityTranslucentEmissive(skin),
						LightCoordsUtil.FULL_BRIGHT, overlay, color, null, 0);
				poseStack.popPose();
			}
		}
	}
}
