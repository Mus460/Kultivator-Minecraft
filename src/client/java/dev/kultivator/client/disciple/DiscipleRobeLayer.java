package dev.kultivator.client.disciple;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.disciple.power.DutyPoses;
import dev.kultivator.client.disciple.power.PhysiqueVisuals;
import dev.kultivator.client.render.CultivatorModels;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * A disciple's robe from Qi Refining: the cultivator robe model layers ({@link CultivatorModels#ROBE}) wearing the
 * greyscale robe texture tinted with its Dao colour (realm colour before a Dao), trims in its sect rank's colour (phase 6;
 * rivals, phantoms and clones keep the gold trims from Golden Core) and the embroidered emblem from Nascent Soul. The robe
 * is a PlayerModel, so the meditation pose and the duty poses fold it with the body; see-through bodies wear a
 * see-through robe.
 */
public class DiscipleRobeLayer extends RenderLayer<DiscipleRenderState, PlayerModel> {

	private static final Identifier BASE = Kultivator.id("textures/entity/robe_base.png");
	private static final Identifier TRIM = Kultivator.id("textures/entity/robe_trim.png");
	private static final Identifier EMBLEM = Kultivator.id("textures/entity/robe_emblem.png");
	/** The trims in greyscale, tinted with the sect rank's colour (module C, DESIGN 20.3.6). */
	private static final Identifier RANK_TRIM = Kultivator.id("textures/entity/robe_rank_trim.png");
	private static final RenderType BASE_TYPE = RenderTypes.entityTranslucent(BASE);
	private static final RenderType TRIM_TYPE = RenderTypes.entityTranslucent(TRIM);
	private static final RenderType RANK_TRIM_TYPE = RenderTypes.entityTranslucent(RANK_TRIM);
	private static final RenderType EMBLEM_TYPE = RenderTypes.entityTranslucent(EMBLEM);
	private static final int CLOTH_ALPHA = 242;
	/** Name of the skirt part inside the robe model layers. */
	private static final String SKIRT = "robe_skirt";

	private final RobeModel wide;
	private final RobeModel slim;

	public DiscipleRobeLayer(RenderLayerParent<DiscipleRenderState, PlayerModel> renderer, EntityRendererProvider.Context context) {
		super(renderer);
		this.wide = new RobeModel(context.bakeLayer(CultivatorModels.ROBE), false);
		this.slim = new RobeModel(context.bakeLayer(CultivatorModels.ROBE_SLIM), true);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, DiscipleRenderState state,
					   float yRot, float xRot) {
		if (state.isInvisible || state.realm < Realm.QI_REFINING.ordinal()) {
			return;
		}
		RobeModel model = state.slim ? slim : wide;
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		int alpha = PhysiqueVisuals.clothAlpha(state, CLOTH_ALPHA);
		int cloth = ARGB.color(alpha, state.robeColor & 0xFFFFFF);
		int plain = PhysiqueVisuals.translucent(state) ? ARGB.color(alpha, 0xFFFFFF) : -1;
		submitNodeCollector.submitModel(model, state, poseStack, BASE_TYPE, lightCoords, overlay, cloth, null, state.outlineColor);
		if (state.role == DiscipleEntity.Role.DISCIPLE.ordinal()) {
			// The trims in the colour of its sect rank (grey Tercatat ... white-gold Tetua Agung).
			int trim = ARGB.color(PhysiqueVisuals.translucent(state) ? alpha : 255, PhysiqueVisuals.trimColor(state));
			submitNodeCollector.order(1).submitModel(model, state, poseStack, RANK_TRIM_TYPE, lightCoords, overlay, trim, null, state.outlineColor);
		} else if (state.realm >= Realm.GOLDEN_CORE.ordinal()) {
			submitNodeCollector.order(1).submitModel(model, state, poseStack, TRIM_TYPE, lightCoords, overlay, plain, null, state.outlineColor);
		}
		if (state.realm >= Realm.NASCENT_SOUL.ordinal()) {
			submitNodeCollector.order(2).submitModel(model, state, poseStack, EMBLEM_TYPE, lightCoords, overlay, plain, null, state.outlineColor);
		}
	}

	/**
	 * The robe as a PlayerModel: outer layers (sash, cuffs, hems) always shown, no hat, and the skirt hanging straight
	 * while the body leans, flaring a little while walking and folding into a short apron while seated.
	 */
	static final class RobeModel extends PlayerModel {

		private final ModelPart skirt;

		RobeModel(ModelPart root, boolean slim) {
			super(root, slim);
			this.skirt = this.body.getChild(SKIRT);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// Phase 6: the robe follows the duty poses of the body (module C).
			DutyPoses.apply(this, state);
			this.hat.visible = false;
			this.jacket.visible = true;
			this.leftSleeve.visible = true;
			this.rightSleeve.visible = true;
			this.leftPants.visible = true;
			this.rightPants.visible = true;

			float walk = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			float sway = Mth.sin(state.ageInTicks * 0.09F) * 0.025F;
			this.skirt.xRot = -this.body.xRot * 0.9F + sway;
			this.skirt.yScale = 1.0F;
			this.skirt.zScale = 1.0F + 0.45F * walk;
			this.skirt.xScale = 1.0F + 0.06F * walk;
			float seated = MeditationPose.strength(state);
			if (seated > 0.0F) {
				this.skirt.yScale = Mth.lerp(seated, 1.0F, 0.32F);
				this.skirt.zScale = Mth.lerp(seated, this.skirt.zScale, 1.25F);
				this.skirt.xScale = Mth.lerp(seated, this.skirt.xScale, 1.12F);
			}
		}
	}
}
