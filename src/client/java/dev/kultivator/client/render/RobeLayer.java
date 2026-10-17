package dev.kultivator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.data.Realm;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * The cultivator's robe (Jubah) from Qi Refining: a slightly inflated copy of the player model (with a skirt, see
 * {@link CultivatorModels.RobeModel}) wearing a greyscale robe texture tinted with the Dao colour (the realm colour
 * before a Dao is chosen). From Golden Core gold trims are drawn over it, from Nascent Soul an embroidered emblem on
 * the back. It follows every pose of the model, the meditation seat included. Hidden for Mortals, invisible players
 * and when the player switched the robe off.
 */
public class RobeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

	private static final Identifier BASE = Kultivator.id("textures/entity/robe_base.png");
	private static final Identifier TRIM = Kultivator.id("textures/entity/robe_trim.png");
	private static final Identifier EMBLEM = Kultivator.id("textures/entity/robe_emblem.png");
	private static final RenderType BASE_TYPE = RenderTypes.entityTranslucent(BASE);
	private static final RenderType TRIM_TYPE = RenderTypes.entityTranslucent(TRIM);
	private static final RenderType EMBLEM_TYPE = RenderTypes.entityTranslucent(EMBLEM);
	/** The cloth is nearly opaque; the tiny bit of translucency keeps it soft. */
	private static final int CLOTH_ALPHA = 242;

	private final CultivatorModels.RobeModel wide;
	private final CultivatorModels.RobeModel slim;

	public RobeLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityRendererProvider.Context context) {
		super(renderer);
		this.wide = new CultivatorModels.RobeModel(context.bakeLayer(CultivatorModels.ROBE), false);
		this.slim = new CultivatorModels.RobeModel(context.bakeLayer(CultivatorModels.ROBE_SLIM), true);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state,
					   float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		VisualState visual = CultivatorLook.visual(state);
		if (!visual.has(VisualState.SHOW_ROBE) || !CultivatorLook.atLeast(visual, Realm.QI_REFINING)) {
			return;
		}
		CultivatorModels.RobeModel model = state.skin.model() == PlayerModelType.SLIM ? slim : wide;
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		int cloth = ARGB.color(CLOTH_ALPHA, CultivatorLook.color(visual));
		submitNodeCollector.submitModel(model, state, poseStack, BASE_TYPE, lightCoords, overlay, cloth, null, state.outlineColor);
		if (CultivatorLook.atLeast(visual, Realm.GOLDEN_CORE)) {
			submitNodeCollector.order(1).submitModel(model, state, poseStack, TRIM_TYPE, lightCoords, overlay, -1, null, state.outlineColor);
		}
		if (CultivatorLook.atLeast(visual, Realm.NASCENT_SOUL)) {
			submitNodeCollector.order(2).submitModel(model, state, poseStack, EMBLEM_TYPE, lightCoords, overlay, -1, null, state.outlineColor);
		}
	}
}
