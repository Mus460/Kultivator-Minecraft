package dev.kultivator.client.dunia.tempat.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.gua.TikusRohEntity;
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
 * Tikus Roh ({@code kultivator:tikus_roh}, DESIGN 38.O.2, quest {@code tikus_dapur}): a cosmetic spirit rat. A blue-grey
 * body (6 × 4 × 10), a pointed head with round pink ears and whiskers, four scurrying paws, a two-segment tail that lashes
 * in a sine wave, and eyes that glow cyan ({@link PancarLayer}). It never touches a block. Texture
 * {@code textures/entity/tempat/tikus_roh.png} (64 × 96: fur, skin, eye glow), glow {@code tikus_roh_pancar.png}.
 */
public class TikusRohRenderer extends MobRenderer<TikusRohEntity, LivingEntityRenderState, TikusRohRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("tikus_roh"), "main");
	static final Identifier TEXTURE = Kultivator.id("textures/entity/tempat/tikus_roh.png");
	static final Identifier GLOW = Kultivator.id("textures/entity/tempat/tikus_roh_pancar.png");

	public TikusRohRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), 0.25F);
		this.addLayer(new PancarLayer<>(this, GLOW, s -> 1.0F));
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, TikusRohRenderer::mesh);
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState state) {
		return TEXTURE;
	}

	private static CubeListBuilder band(int band) {
		return CubeListBuilder.create().texOffs(0, band * 32);
	}

	static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", band(0).addBox(-3.0F, -2.0F, -5.0F, 6.0F, 4.0F, 10.0F)
				.addBox(-2.5F, -2.6F, -3.0F, 5.0F, 1.0F, 7.0F), PartPose.offset(0.0F, 20.5F, 0.0F));
		PartDefinition head = body.addOrReplaceChild("head", band(0).addBox(-2.0F, -2.0F, -4.0F, 4.0F, 4.0F, 4.0F)
				.addBox(-1.2F, -0.8F, -6.0F, 2.4F, 2.4F, 2.0F), PartPose.offset(0.0F, -0.5F, -5.0F));
		head.addOrReplaceChild("nose", band(1).addBox(-0.5F, -0.2F, -6.4F, 1.0F, 1.0F, 0.6F), PartPose.ZERO);
		head.addOrReplaceChild("ear_l", band(1).addBox(-1.0F, -2.0F, -0.25F, 2.0F, 2.0F, 0.5F), PartPose.offsetAndRotation(1.6F, -1.8F, -1.5F, 0.0F, -0.4F, 0.3F));
		head.addOrReplaceChild("ear_r", band(1).addBox(-1.0F, -2.0F, -0.25F, 2.0F, 2.0F, 0.5F), PartPose.offsetAndRotation(-1.6F, -1.8F, -1.5F, 0.0F, 0.4F, -0.3F));
		head.addOrReplaceChild("eyes", band(2).addBox(-1.9F, -1.0F, -3.9F, 1.0F, 1.0F, 1.0F).addBox(0.9F, -1.0F, -3.9F, 1.0F, 1.0F, 1.0F), PartPose.ZERO);
		head.addOrReplaceChild("whiskers", band(1).addBox(-4.0F, 0.6F, -5.6F, 8.0F, 0.0F, 1.6F), PartPose.ZERO);
		PartDefinition tail = body.addOrReplaceChild("tail", band(1).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 4.0F), PartPose.offset(0.0F, -0.5F, 5.0F));
		tail.addOrReplaceChild("tip", band(1).addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 4.0F), PartPose.offset(0.0F, 0.0F, 4.0F));
		for (int i = 0; i < 4; i++) {
			float x = (i % 2 == 0 ? -1.0F : 1.0F) * 2.2F;
			float z = i < 2 ? -3.5F : 3.5F;
			root.addOrReplaceChild("paw_" + i, band(1).addBox(-0.75F, 0.0F, -1.0F, 1.5F, 2.0F, 2.0F), PartPose.offset(x, 22.0F, z));
		}
		return LayerDefinition.create(mesh, 64, 96);
	}

	/** The rat's model: scurrying paws, a bobbing head and a lashing tail. */
	public static final class Model extends EntityModel<LivingEntityRenderState> {

		private final ModelPart body;
		private final ModelPart head;
		private final ModelPart tail;
		private final ModelPart tip;
		private final ModelPart[] paws = new ModelPart[4];

		public Model(ModelPart root) {
			super(root);
			this.body = root.getChild("body");
			this.head = body.getChild("head");
			this.tail = body.getChild("tail");
			this.tip = tail.getChild("tip");
			for (int i = 0; i < 4; i++) {
				paws[i] = root.getChild("paw_" + i);
			}
		}

		@Override
		public void setupAnim(LivingEntityRenderState state) {
			super.setupAnim(state);
			float age = state.ageInTicks;
			float speed = Math.min(1.0F, state.walkAnimationSpeed * 2.0F);
			float run = state.walkAnimationPos * 1.6F;
			for (int i = 0; i < 4; i++) {
				float phase = run + (i == 0 || i == 3 ? 0.0F : Mth.PI);
				paws[i].xRot = Mth.sin(phase) * 1.0F * speed;
			}
			body.y += Math.abs(Mth.sin(run)) * -0.6F * speed;
			head.xRot = Mth.sin(age * 0.5F) * 0.08F + Mth.sin(run) * 0.1F * speed;
			head.yRot = Mth.sin(age * 0.17F) * 0.25F * (1.0F - speed);
			tail.yRot = Mth.sin(age * 0.35F + run) * (0.35F + 0.3F * speed);
			tip.yRot = Mth.sin(age * 0.35F + run - 1.2F) * (0.5F + 0.3F * speed);
			tail.xRot = -0.15F;
		}
	}
}
