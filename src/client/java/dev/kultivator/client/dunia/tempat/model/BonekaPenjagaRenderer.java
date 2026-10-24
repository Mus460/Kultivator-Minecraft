package dev.kultivator.client.dunia.tempat.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.gua.BonekaPenjagaEntity;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Boneka Penjaga ({@code kultivator:boneka_penjaga}, DESIGN 38.O.2): the jointed wooden guardian of an inheritance tomb.
 * A red-and-white lacquered mask with a crest, a torso bound by two bronze rings, ball joints at shoulders, elbows, hips
 * and knees, two-piece limbs, a short red apron, and a wooden sword in the right hand. It moves like a puppet: the head
 * jerks 15° every 40 ticks, the walk is quantised into four stiff positions, the strike has three keys (raise to -120°,
 * cut to -20° with an overshoot, return), and when a trial begins it rises from a cross-legged seat over 30 ticks
 * ({@link KaitModel#bonekaBangkit}). Texture {@code textures/entity/tempat/boneka_penjaga.png} (128 × 160: old wood,
 * bronze, mask, red cloth, sword wood).
 */
public class BonekaPenjagaRenderer extends MobRenderer<BonekaPenjagaEntity, BonekaPenjagaRenderer.State, BonekaPenjagaRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("boneka_penjaga"), "main");
	static final Identifier TEXTURE = Kultivator.id("textures/entity/tempat/boneka_penjaga.png");

	public static class State extends LivingEntityRenderState {
		public float attack;
		public float rise = 1.0F;
	}

	public BonekaPenjagaRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), 0.5F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, BonekaPenjagaRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(BonekaPenjagaEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.attack = entity.getSwingAnimation(partialTicks);
		state.rise = Mth.clamp(KaitModel.bonekaBangkit.baca(entity, partialTicks), 0.0F, 1.0F);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	private static CubeListBuilder band(int band) {
		return CubeListBuilder.create().texOffs(0, band * 32);
	}

	static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", band(0).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
		body.addOrReplaceChild("rings", band(1).addBox(-4.4F, 2.0F, -2.4F, 8.8F, 1.2F, 4.8F).addBox(-4.4F, 8.0F, -2.4F, 8.8F, 1.2F, 4.8F),
				PartPose.ZERO);
		body.addOrReplaceChild("apron", band(3).addBox(-3.0F, 9.2F, -2.6F, 6.0F, 5.0F, 0.6F).addBox(-3.0F, 9.2F, 2.0F, 6.0F, 4.0F, 0.6F),
				PartPose.ZERO);
		body.addOrReplaceChild("neck", band(1).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 1.5F, 3.0F), PartPose.ZERO);
		PartDefinition head = root.addOrReplaceChild("head", band(0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.offset(0.0F, -1.0F, 0.0F));
		head.addOrReplaceChild("mask", band(2).addBox(-3.6F, -7.4F, -4.4F, 7.2F, 7.0F, 0.6F), PartPose.ZERO);
		head.addOrReplaceChild("crest", band(3).addBox(-1.0F, -11.0F, -1.0F, 2.0F, 3.0F, 2.0F).addBox(-0.6F, -12.0F, 0.0F, 1.2F, 1.0F, 3.0F),
				PartPose.ZERO);
		head.addOrReplaceChild("band", band(1).addBox(-4.3F, -8.4F, -4.3F, 8.6F, 1.0F, 8.6F), PartPose.ZERO);
		for (int side = -1; side <= 1; side += 2) {
			String s = side < 0 ? "r" : "l";
			PartDefinition shoulder = root.addOrReplaceChild("arm_" + s, band(1).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F),
					PartPose.offset(side * 5.5F, 1.5F, 0.0F));
			shoulder.addOrReplaceChild("upper", band(0).addBox(-1.5F, 1.0F, -1.5F, 3.0F, 6.0F, 3.0F), PartPose.ZERO);
			PartDefinition elbow = shoulder.addOrReplaceChild("elbow", band(1).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F),
					PartPose.offset(0.0F, 8.0F, 0.0F));
			PartDefinition lower = elbow.addOrReplaceChild("lower", band(0).addBox(-1.5F, 1.0F, -1.5F, 3.0F, 6.0F, 3.0F), PartPose.ZERO);
			if (side < 0) {
				PartDefinition sword = lower.addOrReplaceChild("sword", band(4).addBox(-0.5F, -1.0F, -14.0F, 1.0F, 2.0F, 14.0F)
						.addBox(-1.5F, -1.5F, -0.5F, 3.0F, 3.0F, 1.0F), PartPose.offset(0.0F, 6.5F, 0.0F));
				sword.addOrReplaceChild("grip", band(3).addBox(-0.6F, -0.6F, -0.5F, 1.2F, 1.2F, 3.0F), PartPose.ZERO);
			}
			PartDefinition hip = root.addOrReplaceChild("leg_" + s, band(1).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F),
					PartPose.offset(side * 2.0F, 12.5F, 0.0F));
			hip.addOrReplaceChild("thigh", band(0).addBox(-1.5F, 1.0F, -1.5F, 3.0F, 5.0F, 3.0F), PartPose.ZERO);
			PartDefinition knee = hip.addOrReplaceChild("knee", band(1).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offset(0.0F, 6.5F, 0.0F));
			knee.addOrReplaceChild("shin", band(0).addBox(-1.5F, 1.0F, -1.5F, 3.0F, 4.0F, 3.0F).addBox(-1.8F, 4.0F, -2.6F, 3.6F, 1.0F, 4.2F),
					PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 128, 160);
	}

	/** The puppet's model: stiff, quantised motion. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart body;
		private final ModelPart head;
		private final ModelPart[] arms = new ModelPart[2];
		private final ModelPart[] elbows = new ModelPart[2];
		private final ModelPart[] legs = new ModelPart[2];
		private final ModelPart[] knees = new ModelPart[2];

		public Model(ModelPart root) {
			super(root);
			this.body = root.getChild("body");
			this.head = root.getChild("head");
			for (int i = 0; i < 2; i++) {
				String s = i == 0 ? "r" : "l";
				arms[i] = root.getChild("arm_" + s);
				elbows[i] = arms[i].getChild("elbow");
				legs[i] = root.getChild("leg_" + s);
				knees[i] = legs[i].getChild("knee");
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float age = state.ageInTicks;
			// the head jerks to a new angle every 40 ticks (no easing: a puppet)
			int beat = Mth.floor(age / 40.0F);
			head.yRot = ((beat * 7919) % 3 - 1) * 0.26F;
			head.xRot = ((beat * 104729) % 2) * 0.1F;
			// stiff gait: the swing is quantised into four positions
			float speed = Math.min(1.0F, state.walkAnimationSpeed * 1.5F);
			float swing = Mth.sin(state.walkAnimationPos * 0.6662F);
			float q = Math.round(swing * 2.0F) / 2.0F * speed;
			legs[0].xRot = q * 0.7F;
			legs[1].xRot = -q * 0.7F;
			knees[0].xRot = Math.max(0.0F, -q) * 0.6F;
			knees[1].xRot = Math.max(0.0F, q) * 0.6F;
			arms[0].xRot = -q * 0.5F;
			arms[1].xRot = q * 0.5F;
			elbows[0].xRot = -0.35F;
			elbows[1].xRot = -0.2F;
			// three-key strike: raise (0-0.4) to -120°, cut (0.4-0.7) to -20° with an overshoot, return (0.7-1)
			if (state.attack > 0.0F) {
				float t = state.attack;
				float a;
				if (t < 0.4F) {
					a = -2.09F * (t / 0.4F);
				} else if (t < 0.7F) {
					float k = (t - 0.4F) / 0.3F;
					a = -2.09F + 1.74F * backOut(k);
				} else {
					a = -0.35F * (1.0F - (t - 0.7F) / 0.3F);
				}
				arms[0].xRot = Math.round(a / 0.26F) * 0.26F;
				elbows[0].xRot = -0.1F;
				body.yRot = -0.2F * Mth.sin(t * Mth.PI);
			}
			// rising from the cross-legged seat
			float sit = 1.0F - state.rise;
			if (sit > 0.0F) {
				float drop = sit * 11.0F;
				body.y += drop;
				head.y += drop;
				arms[0].y += drop;
				arms[1].y += drop;
				legs[0].y += drop;
				legs[1].y += drop;
				legs[0].xRot = -1.5F * sit;
				legs[1].xRot = -1.5F * sit;
				legs[0].yRot = 0.55F * sit;
				legs[1].yRot = -0.55F * sit;
				knees[0].xRot = 2.2F * sit;
				knees[1].xRot = 2.2F * sit;
				knees[0].yRot = -1.0F * sit;
				knees[1].yRot = 1.0F * sit;
				arms[0].xRot = -0.6F * sit;
				arms[1].xRot = -0.6F * sit;
				elbows[0].xRot = -0.8F * sit;
				elbows[1].xRot = -0.8F * sit;
				head.xRot = 0.3F * sit;
			}
		}

		private static float backOut(float t) {
			float c1 = 1.70158F;
			float c3 = c1 + 1.0F;
			float u = t - 1.0F;
			return 1.0F + c3 * u * u * u + c1 * u * u;
		}
	}
}
