package dev.kultivator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.data.Realm;
import dev.kultivator.data.VisualState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;

/**
 * Glowing eyes from Nascent Soul: the emissive "eyes" render type (like spiders and endermen, so shader packs make them
 * shine) with a 64x64 overlay that only lights the two standard eye pixel pairs of the head's front face, tinted with
 * the Dao colour (the realm colour before a Dao is chosen). Softer while meditating, dark while asleep, and flickering
 * blood red during a Qi Deviation.
 */
public class EyesLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

	private static final RenderType EYES = RenderTypes.eyes(Kultivator.id("textures/entity/cultivator_eyes.png"));
	private static final int DEVIATION_RED = 0xFF2A1E;

	public EyesLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state,
					   float yRot, float xRot) {
		if (state.isInvisible || state.hasPose(Pose.SLEEPING)) {
			return;
		}
		VisualState visual = CultivatorLook.visual(state);
		if (!visual.has(VisualState.SHOW_EYES) || !CultivatorLook.atLeast(visual, Realm.NASCENT_SOUL)) {
			return;
		}
		float age = state.ageInTicks;
		int color;
		if (visual.has(VisualState.DEVIATION)) {
			// An unsteady, sick flicker between bright red, dim red and (rarely) the true colour fighting through.
			float noise = Mth.sin(age * 1.9F + state.id) + Mth.sin(age * 4.7F) * 0.6F + Mth.sin(age * 0.37F) * 0.5F;
			if (noise > 1.2F) {
				color = ARGB.color(200, CultivatorLook.color(visual));
			} else {
				int alpha = noise > -0.3F ? 255 : 70;
				color = ARGB.color(alpha, DEVIATION_RED);
			}
		} else {
			float breath = 0.5F + 0.5F * Mth.sin(age * 0.08F);
			int alpha = visual.has(VisualState.MEDITATING) ? (int) Mth.lerp(breath, 110.0F, 170.0F) : (int) Mth.lerp(breath, 205.0F, 255.0F);
			color = ARGB.color(alpha, CultivatorLook.lighten(CultivatorLook.color(visual), 0.25F));
		}
		submitNodeCollector.order(1).submitModel(this.getParentModel(), state, poseStack, EYES, LightCoordsUtil.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, color, null, state.outlineColor);
	}
}
