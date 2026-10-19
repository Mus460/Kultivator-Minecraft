package dev.kultivator.client.musuh;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.disciple.power.PhysiqueVisuals;
import dev.kultivator.musuh.RogueCultivatorEntity;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
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
 * The rogue's cloth mask (DESIGN 25.4.2: "a mask overlay and a tier trim colour"): a head shell a little outside the
 * skin's hat layer, so it never flickers against it, wearing a greyscale mask texture tinted with the tier's colour
 * (bronze wanderer, crimson robber, violet elder, gold Patriarch). See-through variants wear a see-through mask.
 */
public class RogueMaskLayer extends RenderLayer<DiscipleRenderState, PlayerModel> {

	public static final ModelLayerLocation MASK = new ModelLayerLocation(Kultivator.id("rogue_mask"), "main");
	private static final Identifier TEXTURE = Kultivator.id("textures/entity/rogue_mask.png");
	private static final RenderType TYPE = RenderTypes.entityTranslucent(TEXTURE);
	/** The mask stands this far off the head (the skin's hat layer is at 0.5). */
	private static final float INFLATE = 0.62F;

	private final MaskModel model;

	public RogueMaskLayer(RenderLayerParent<DiscipleRenderState, PlayerModel> renderer, EntityRendererProvider.Context context) {
		super(renderer);
		this.model = new MaskModel(context.bakeLayer(MASK));
	}

	/** Registers the mask's model layer (client init, before the entity models are baked). */
	static void register() {
		ModelLayerRegistry.registerModelLayer(MASK, RogueMaskLayer::mesh);
	}

	/** A head box only (the other parts stay empty), texture 64x64 laid out like a skin's head. */
	static LayerDefinition mesh() {
		MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head",
				CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(INFLATE)), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, DiscipleRenderState state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}
		int tier = Mth.clamp(((FabricRenderState) state).getDataOrDefault(RogueRenderer.TIER, 1), 1, 4);
		int alpha = PhysiqueVisuals.clothAlpha(state, 255);
		int color = ARGB.color(alpha, RogueCultivatorEntity.TIER_COLOR[tier] & 0xFFFFFF);
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		submitNodeCollector.order(3).submitModel(model, state, poseStack, TYPE, lightCoords, overlay, color, null, state.outlineColor);
	}

	/** Only the head is drawn; it turns with the body's head through the PlayerModel animation. */
	static final class MaskModel extends PlayerModel {

		MaskModel(ModelPart root) {
			super(root, false);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.head.visible = true;
			this.hat.visible = false;
			this.body.visible = false;
			this.leftArm.visible = false;
			this.rightArm.visible = false;
			this.leftLeg.visible = false;
			this.rightLeg.visible = false;
		}
	}
}
