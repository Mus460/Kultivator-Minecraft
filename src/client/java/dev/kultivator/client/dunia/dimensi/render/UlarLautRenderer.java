package dev.kultivator.client.dunia.dimensi.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.Kultivator;
import dev.kultivator.client.dunia.SegmentChain;
import dev.kultivator.dunia.dimensi.laut.UlarLautEntity;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Ular Laut Raksasa ({@link UlarLautEntity}): the head is a cuboid dragon-serpent head (skull, snout, swept-back horns,
 * gold whiskers, a crest fin, a jaw that gapes when it bites); the body is 12-24 scaled segments with belly plates and
 * a dorsal fin, posed by the spine's {@link SegmentChain} (pushed once per client tick from the head's path) and
 * tapering to a tail fin. LOD: the chain draws fewer segments with distance and on Minimal effects. The pearl serpent
 * of the Dragon King's quest carries a white pearl in its jaw.
 */
public class UlarLautRenderer extends MobRenderer<UlarLautEntity, UlarLautRenderer.State, UlarLautRenderer.HeadModel> {

	public static final ModelLayerLocation HEAD = new ModelLayerLocation(Kultivator.id("ular_laut"), "head");
	public static final ModelLayerLocation SEGMENT = new ModelLayerLocation(Kultivator.id("ular_laut"), "segment");
	private static final Identifier TEXTURE = Kultivator.id("textures/dunia/dimensi/entity/ular_laut.png");
	private static final RenderType TYPE = RenderTypes.entityCutout(TEXTURE);
	private static final Map<Integer, SegmentChain> CHAINS = new HashMap<>();

	public static class State extends LivingEntityRenderState {
		public int tier;
		public boolean pearl;
		public float attack;
		public int id;
		public float partial;
	}

	private final ModelPart segment;
	private final ModelPart tail;

	public UlarLautRenderer(EntityRendererProvider.Context context) {
		super(context, new HeadModel(context.bakeLayer(HEAD)), 1.0F);
		ModelPart body = context.bakeLayer(SEGMENT);
		this.segment = body.getChild("segment");
		this.tail = body.getChild("tail");
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(HEAD, UlarLautRenderer::headMesh);
		ModelLayerRegistry.registerModelLayer(SEGMENT, UlarLautRenderer::segmentMesh);
	}

	private static int segmentsFor(int tier) {
		return 12 + 3 * Mth.clamp(tier, 0, 4);
	}

	/** Once per client tick: every serpent within 160 b pushes its head into its chain; gone ones are forgotten. */
	public static void tickChains(ClientLevel level, Vec3 camera) {
		for (Iterator<Map.Entry<Integer, SegmentChain>> it = CHAINS.entrySet().iterator(); it.hasNext(); ) {
			Entity entity = level.getEntity(it.next().getKey());
			if (!(entity instanceof UlarLautEntity) || entity.isRemoved()) {
				it.remove();
			}
		}
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof UlarLautEntity serpent && serpent.position().distanceToSqr(camera) < 160.0D * 160.0D) {
				float spacing = 1.3F * (1.0F + 0.15F * serpent.tier());
				SegmentChain chain = CHAINS.computeIfAbsent(serpent.getId(), id -> new SegmentChain(segmentsFor(serpent.tier()), spacing));
				chain.push(serpent.position().add(0.0D, 0.8D, 0.0D), serpent.getYRot(), serpent.getXRot());
			}
		}
	}

	public static void clearChains() {
		CHAINS.clear();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(UlarLautEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.tier = entity.tier();
		state.pearl = entity.hasPearl();
		state.attack = entity.getSwingAnimation(partialTicks);
		state.id = entity.getId();
		state.partial = partialTicks;
	}

	@Override
	protected AABB getBoundingBoxForCulling(UlarLautEntity entity, float partialTicks) {
		return entity.getBoundingBox().inflate(24.0D, 8.0D, 24.0D);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		float s = 1.0F + 0.15F * state.tier;
		poseStack.scale(s, s, s);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		super.submit(state, poseStack, collector, camera);
		SegmentChain chain = CHAINS.get(state.id);
		if (chain == null || state.isInvisible) {
			return;
		}
		int drawn = chain.segments();
		int capacity = chain.capacity();
		if (drawn <= 1) {
			return;
		}
		float size = 1.0F + 0.15F * state.tier;
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		Vec3 origin = new Vec3(state.x, state.y, state.z);
		for (int i = 1; i < drawn; i++) {
			int k = Math.min(capacity - 1, i * capacity / drawn);
			SegmentChain.Pose pose = chain.pose(k, state.partial);
			float t = k / (float) (capacity - 1);
			float taper = size * (1.0F - 0.62F * (float) Math.pow(t, 1.4D));
			Vec3 rel = pose.pos().subtract(origin);
			poseStack.pushPose();
			poseStack.translate(rel.x, rel.y, rel.z);
			poseStack.rotateDegrees(Axis.YP, 180.0F - pose.yaw());
			poseStack.rotateDegrees(Axis.XP, pose.pitch());
			float sway = Mth.sin(state.ageInTicks * 0.15F - k * 0.6F) * 6.0F;
			poseStack.rotateDegrees(Axis.ZP, sway);
			poseStack.scale(-taper, -taper, taper);
			collector.submitModelPart(i == drawn - 1 ? tail : segment, poseStack, TYPE, state.lightCoords, overlay, null);
			poseStack.popPose();
		}
	}

	private static CubeListBuilder band(int band) {
		return CubeListBuilder.create().texOffs(0, band * 32);
	}

	static LayerDefinition headMesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", band(0).addBox(-5.0F, -5.0F, -10.0F, 10.0F, 8.0F, 13.0F)
				.addBox(-4.0F, -3.0F, -16.0F, 8.0F, 5.0F, 6.0F), PartPose.offset(0.0F, 15.0F, 0.0F));
		head.addOrReplaceChild("crest", band(2).addBox(-0.5F, -9.0F, -6.0F, 1.0F, 4.0F, 10.0F), PartPose.ZERO);
		head.addOrReplaceChild("brows", band(3).addBox(-5.5F, -5.5F, -9.0F, 3.0F, 1.5F, 4.0F).addBox(2.5F, -5.5F, -9.0F, 3.0F, 1.5F, 4.0F),
				PartPose.ZERO);
		head.addOrReplaceChild("eyes", band(4).addBox(-5.3F, -4.0F, -8.0F, 0.4F, 2.0F, 3.0F).addBox(4.9F, -4.0F, -8.0F, 0.4F, 2.0F, 3.0F),
				PartPose.ZERO);
		head.addOrReplaceChild("teeth_top", band(6).addBox(-3.5F, 2.0F, -15.5F, 7.0F, 1.0F, 1.0F).addBox(-3.8F, 2.0F, -14.0F, 1.0F, 1.0F, 5.0F)
				.addBox(2.8F, 2.0F, -14.0F, 1.0F, 1.0F, 5.0F), PartPose.ZERO);
		PartDefinition jaw = head.addOrReplaceChild("jaw", band(1).addBox(-4.0F, 0.0F, -13.0F, 8.0F, 3.0F, 12.0F),
				PartPose.offset(0.0F, 2.5F, -2.0F));
		jaw.addOrReplaceChild("teeth", band(6).addBox(-3.5F, -1.0F, -12.5F, 7.0F, 1.0F, 1.0F), PartPose.ZERO);
		jaw.addOrReplaceChild("pearl", band(8).addBox(-1.5F, -2.5F, -11.5F, 3.0F, 3.0F, 3.0F), PartPose.ZERO);
		for (int side = -1; side <= 1; side += 2) {
			String s = side < 0 ? "r" : "l";
			head.addOrReplaceChild("horn_" + s, band(3).addBox(-0.75F, -0.75F, 0.0F, 1.5F, 1.5F, 9.0F),
					PartPose.offsetAndRotation(side * 3.0F, -5.0F, -4.0F, 0.45F, side * 0.25F, 0.0F));
			head.addOrReplaceChild("whisker_" + s, band(7).addBox(side < 0 ? -7.0F : 0.0F, -0.3F, -0.3F, 7.0F, 0.6F, 0.6F),
					PartPose.offsetAndRotation(side * 4.0F, 0.0F, -14.0F, 0.0F, side * -0.6F, side * 0.4F));
			head.addOrReplaceChild("gill_" + s, band(2).addBox(side < 0 ? -0.5F : 0.0F, -3.0F, 0.0F, 0.5F, 6.0F, 4.0F),
					PartPose.offsetAndRotation(side * 5.0F, -1.0F, -1.0F, 0.0F, side * 0.5F, 0.0F));
		}
		return LayerDefinition.create(mesh, 128, 512);
	}

	static LayerDefinition segmentMesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition segment = root.addOrReplaceChild("segment", band(0).addBox(-4.5F, -4.5F, -6.5F, 9.0F, 9.0F, 13.0F), PartPose.ZERO);
		segment.addOrReplaceChild("belly", band(1).addBox(-4.0F, 3.6F, -6.0F, 8.0F, 1.5F, 12.0F), PartPose.ZERO);
		segment.addOrReplaceChild("fin", band(2).addBox(-0.5F, -8.5F, -5.5F, 1.0F, 4.0F, 11.0F), PartPose.ZERO);
		segment.addOrReplaceChild("spines", band(3).addBox(-0.6F, -9.5F, -4.0F, 1.2F, 1.0F, 1.0F).addBox(-0.6F, -9.5F, 2.0F, 1.2F, 1.0F, 1.0F),
				PartPose.ZERO);
		PartDefinition tail = root.addOrReplaceChild("tail", band(0).addBox(-3.0F, -3.0F, -6.0F, 6.0F, 6.0F, 10.0F), PartPose.ZERO);
		tail.addOrReplaceChild("fluke", band(2).addBox(-0.5F, -8.0F, 2.0F, 1.0F, 16.0F, 9.0F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 128, 512);
	}

	/** The serpent's head and its animation (pitch, the bite, the whiskers' sway). */
	public static final class HeadModel extends EntityModel<State> {

		private final ModelPart head;
		private final ModelPart jaw;
		private final ModelPart pearl;
		private final ModelPart whiskerL;
		private final ModelPart whiskerR;

		public HeadModel(ModelPart root) {
			super(root);
			this.head = root.getChild("head");
			this.jaw = head.getChild("jaw");
			this.pearl = jaw.getChild("pearl");
			this.whiskerL = head.getChild("whisker_l");
			this.whiskerR = head.getChild("whisker_r");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			head.xRot = state.xRot * Mth.DEG_TO_RAD;
			head.yRot = (state.yRot) * Mth.DEG_TO_RAD * 0.3F;
			float idle = (0.5F + 0.5F * Mth.sin(state.ageInTicks * 0.08F)) * 0.12F;
			jaw.xRot = idle + Mth.sin(Math.min(1.0F, state.attack) * Mth.PI) * 0.9F;
			pearl.visible = state.pearl;
			float sway = Mth.sin(state.ageInTicks * 0.1F) * 0.2F;
			whiskerL.zRot += sway;
			whiskerR.zRot -= sway;
		}
	}
}
