package dev.kultivator.client.dunia.dimensi.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.dimensi.laut.KepitingBatuEntity;
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
 * Kepiting Batu Raksasa ({@link KepitingBatuEntity}): a cuboid rock crab, 18 × 14 px shell with a carapace ridge,
 * barnacle rocks and moss, eye stalks, two great pincers whose lower jaws snap, six jointed legs that scuttle while it
 * walks and fold in while it hides in its shell. Texture {@code textures/dunia/dimensi/entity/kepiting_batu.png} (128 × 512,
 * 16 bands of 32 px). Larger by tier.
 */
public class KepitingRenderer extends MobRenderer<KepitingBatuEntity, KepitingRenderer.State, KepitingRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("kepiting_batu"), "main");
	private static final Identifier TEXTURE = Kultivator.id("textures/dunia/dimensi/entity/kepiting_batu.png");

	public static class State extends LivingEntityRenderState {
		public boolean shell;
		public int tier;
		public float attack;
	}

	public KepitingRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), 1.2F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, KepitingRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(KepitingBatuEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.shell = entity.inShell();
		state.tier = entity.tier();
		state.attack = entity.getSwingAnimation(partialTicks);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		float s = 1.25F + 0.1F * state.tier;
		poseStack.scale(s, s, s);
	}

	private static CubeListBuilder band(int band) {
		return CubeListBuilder.create().texOffs(0, band * 32);
	}

	static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", band(0).addBox(-9.0F, -4.0F, -7.0F, 18.0F, 6.0F, 14.0F)
				.addBox(-7.0F, -6.0F, -5.0F, 14.0F, 2.0F, 10.0F), PartPose.offset(0.0F, 15.0F, 0.0F));
		body.addOrReplaceChild("rocks", band(1).addBox(-5.0F, -8.0F, -2.0F, 3.0F, 2.0F, 3.0F).addBox(2.0F, -7.5F, 0.0F, 4.0F, 1.5F, 3.0F)
				.addBox(-1.0F, -7.0F, -4.0F, 2.0F, 1.0F, 2.0F).addBox(5.0F, -5.0F, 3.0F, 2.0F, 1.0F, 2.0F), PartPose.ZERO);
		body.addOrReplaceChild("moss", band(2).addBox(-8.0F, -4.4F, -6.5F, 6.0F, 0.4F, 5.0F).addBox(3.0F, -6.3F, -3.0F, 4.0F, 0.4F, 4.0F),
				PartPose.ZERO);
		body.addOrReplaceChild("belly", band(3).addBox(-8.0F, 2.0F, -6.0F, 16.0F, 1.0F, 12.0F), PartPose.ZERO);
		body.addOrReplaceChild("stalks", band(0).addBox(-3.5F, -8.5F, -7.5F, 1.0F, 4.5F, 1.0F).addBox(2.5F, -8.5F, -7.5F, 1.0F, 4.5F, 1.0F),
				PartPose.ZERO);
		body.addOrReplaceChild("eyes", band(4).addBox(-4.0F, -10.0F, -8.0F, 2.0F, 2.0F, 2.0F).addBox(2.0F, -10.0F, -8.0F, 2.0F, 2.0F, 2.0F),
				PartPose.ZERO);
		body.addOrReplaceChild("mouth", band(5).addBox(-2.5F, -1.0F, -7.6F, 5.0F, 2.0F, 0.6F), PartPose.ZERO);
		for (int side = -1; side <= 1; side += 2) {
			String s = side < 0 ? "r" : "l";
			PartDefinition arm = root.addOrReplaceChild("arm_" + s, band(0).addBox(side < 0 ? -5.0F : 0.0F, -1.5F, -1.5F, 5.0F, 3.0F, 3.0F),
					PartPose.offsetAndRotation(side * 7.0F, 14.0F, -6.0F, 0.0F, side * 0.5F, 0.0F));
			PartDefinition pincer = arm.addOrReplaceChild("pincer", band(6).addBox(side < 0 ? -7.0F : 0.0F, -3.0F, -3.5F, 7.0F, 3.5F, 7.0F)
					.addBox(side < 0 ? -9.0F : 7.0F, -2.5F, -2.5F, 2.0F, 2.5F, 5.0F), PartPose.offset(side * 5.0F, 0.0F, 0.0F));
			pincer.addOrReplaceChild("jaw", band(6).addBox(side < 0 ? -7.5F : 0.0F, 0.0F, -2.5F, 7.5F, 2.0F, 5.0F),
					PartPose.offset(side * 0.5F, 0.5F, 0.0F));
			for (int i = 0; i < 3; i++) {
				PartDefinition leg = root.addOrReplaceChild("leg_" + s + i, band(0).addBox(side < 0 ? -8.0F : 0.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F),
						PartPose.offsetAndRotation(side * 8.0F, 15.0F, -2.5F + i * 4.0F, 0.0F, 0.0F, side * 0.45F));
				leg.addOrReplaceChild("foot", band(7).addBox(side < 0 ? -7.0F : 0.0F, -0.75F, -0.75F, 7.0F, 1.5F, 1.5F),
						PartPose.offsetAndRotation(side * 8.0F, 0.0F, 0.0F, 0.0F, 0.0F, side * 1.1F));
			}
		}
		return LayerDefinition.create(mesh, 128, 512);
	}

	/** The crab's model and its animation. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart body;
		private final ModelPart[] arms = new ModelPart[2];
		private final ModelPart[] jaws = new ModelPart[2];
		private final ModelPart[][] legs = new ModelPart[2][3];

		public Model(ModelPart root) {
			super(root);
			this.body = root.getChild("body");
			for (int side = 0; side < 2; side++) {
				String s = side == 0 ? "r" : "l";
				arms[side] = root.getChild("arm_" + s);
				jaws[side] = arms[side].getChild("pincer").getChild("jaw");
				for (int i = 0; i < 3; i++) {
					legs[side][i] = root.getChild("leg_" + s + i);
				}
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float walk = state.walkAnimationPos;
			float speed = Math.min(1.0F, state.walkAnimationSpeed * 2.0F);
			float age = state.ageInTicks;
			body.y += Mth.sin(age * 0.12F) * 0.25F;
			for (int side = 0; side < 2; side++) {
				float sign = side == 0 ? -1.0F : 1.0F;
				for (int i = 0; i < 3; i++) {
					ModelPart leg = legs[side][i];
					float phase = walk * 1.4F + i * 2.1F + side * Mth.PI;
					if (state.shell) {
						leg.zRot = sign * 1.25F;
						leg.yRot = sign * 0.3F;
					} else {
						leg.yRot += Mth.sin(phase) * 0.45F * speed;
						leg.zRot += Math.max(0.0F, Mth.cos(phase)) * -sign * 0.35F * speed;
					}
				}
				ModelPart arm = arms[side];
				float snap = Math.max(0.0F, Mth.sin(age * 0.22F + side * 1.3F)) * 0.25F;
				if (state.attack > 0.0F) {
					snap = Mth.sin(state.attack * Mth.PI) * 0.7F;
					arm.xRot -= Mth.sin(state.attack * Mth.PI) * 0.6F;
				}
				if (state.shell) {
					arm.yRot = -sign * 0.9F;
					snap = 0.0F;
				}
				jaws[side].xRot = snap;
			}
		}
	}
}
