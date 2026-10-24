package dev.kultivator.client.dunia.tempat.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.dunia.tempat.kotabesar.GerbangKotaEntity;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Daun Gerbang Kota ({@code kultivator:gerbang_kota}, DESIGN 38.B.4.1 / 38.O.2): the two leaves of a capital gate, sized
 * from the anchor data ({@link KaitModel#gerbangLebar} × {@link KaitModel#gerbangTinggi} blocks). Each leaf is built of
 * one-block panels of red-lacquered planks 4 px thick with two gold studs per panel face (the 4 × 6 stud grid of a 2 × 6
 * leaf), iron straps across the second and second-to-last rows, a raised border beam along the meeting edge, iron hinge
 * knuckles on the outer edge and, at chest height on both faces, a bronze lion-head knocker with its hanging ring
 * (the {@code pushou} of a palace gate; the ring swings while the leaf moves). The leaves turn on their outer hinges from 0
 * to 92° ({@link KaitModel#gerbangBuka}, already eased by the entity); dust puffs up at their feet when they start to move.
 * When shut, a dark crossbar lies across both on the city side. Damage from a beast tide shows as cracks
 * ({@link KaitModel#gerbangRetak}: light from 0.3, heavy from 0.65), and when the tide breaks in
 * ({@link KaitModel#gerbangRoboh}) the left leaf leans 12° and trembles.
 *
 * <p>A Yamen cell door ({@link KaitModel#gerbangSel}) is drawn as one barred iron door on a single hinge instead: an iron
 * frame with top, middle and bottom rails, five round bars per block and a heavy lock plate.
 *
 * <p>No block ever changes: the gatehouse is the structure, the leaves are this entity. The entity faces the city: the
 * leaves swing toward its facing (local +z). Texture {@code textures/entity/tempat/gerbang_kota.png} (128 × 256: lacquered
 * planks, iron, gold studs, dark beam, bronze knocker, light cracks, heavy cracks, dark cell iron;
 * {@code tools/gen_tempat_models.py}).
 */
public class GerbangKotaRenderer extends EntityRenderer<GerbangKotaEntity, GerbangKotaRenderer.State> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("gerbang_kota"), "main");
	private static final Identifier TEXTURE = Kultivator.id("textures/entity/tempat/gerbang_kota.png");
	private static final float MAX_ANGLE = 92.0F;
	private static final int[] DEBU = {3, 8, 14};

	public static class State extends EntityRenderState {
		public float yaw;
		public float open;
		public float swing;
		public float width = 4.0F;
		public float height = 6.0F;
		public float broken;
		public float cracks;
		public boolean cell;
	}

	private final RenderType type = RenderTypes.entityCutout(TEXTURE);
	private final ModelPart panel;
	private final ModelPart studs;
	private final ModelPart strap;
	private final ModelPart hinge;
	private final ModelPart bar;
	private final ModelPart edge;
	private final ModelPart knocker;
	private final ModelPart ring;
	private final ModelPart crackLight;
	private final ModelPart crackHeavy;
	private final ModelPart cellFrame;
	private final ModelPart cellBars;
	private final ModelPart cellLock;
	/** Per gate: the last open reading and the previous frame's motion (client only, forgotten with the entity). */
	private final Map<GerbangKotaEntity, float[]> motion = new WeakHashMap<>();

	public GerbangKotaRenderer(EntityRendererProvider.Context context) {
		super(context);
		ModelPart root = context.bakeLayer(LAYER);
		this.panel = root.getChild("panel");
		this.studs = root.getChild("studs");
		this.strap = root.getChild("strap");
		this.hinge = root.getChild("hinge");
		this.bar = root.getChild("bar");
		this.edge = root.getChild("edge");
		this.knocker = root.getChild("knocker");
		this.ring = knocker.getChild("ring");
		this.crackLight = root.getChild("crack_light");
		this.crackHeavy = root.getChild("crack_heavy");
		this.cellFrame = root.getChild("cell_frame");
		this.cellBars = root.getChild("cell_bars");
		this.cellLock = root.getChild("cell_lock");
		this.shadowRadius = 0.0F;
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, GerbangKotaRenderer::mesh);
	}

	private static CubeListBuilder band(int band) {
		return CubeListBuilder.create().texOffs(0, band * 32);
	}

	/** Parts in block-sized panel units (16 px = 1 block), each at the origin; posed only through the PoseStack. */
	static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("panel", band(0).addBox(0.0F, 0.0F, -2.0F, 16.0F, 16.0F, 4.0F), PartPose.ZERO);
		root.addOrReplaceChild("studs", band(2).addBox(3.25F, 7.25F, -2.75F, 1.5F, 1.5F, 0.75F).addBox(11.25F, 7.25F, -2.75F, 1.5F, 1.5F, 0.75F)
				.addBox(3.25F, 7.25F, 2.0F, 1.5F, 1.5F, 0.75F).addBox(11.25F, 7.25F, 2.0F, 1.5F, 1.5F, 0.75F), PartPose.ZERO);
		root.addOrReplaceChild("strap", band(1).addBox(0.0F, 6.0F, -2.4F, 16.0F, 4.0F, 4.8F), PartPose.ZERO);
		root.addOrReplaceChild("hinge", band(1).addBox(-1.2F, 2.0F, -1.2F, 2.4F, 4.0F, 2.4F).addBox(-1.2F, 10.0F, -1.2F, 2.4F, 4.0F, 2.4F), PartPose.ZERO);
		root.addOrReplaceChild("bar", band(3).addBox(0.0F, -1.5F, 0.0F, 16.0F, 3.0F, 3.0F), PartPose.ZERO);
		// the raised beam along a leaf's meeting edge (one block tall, scaled by the leaf height)
		root.addOrReplaceChild("edge", band(3).addBox(-1.0F, 0.0F, -2.6F, 2.0F, 16.0F, 5.2F), PartPose.ZERO);
		// lion-head roundel on both faces, a ring hanging from its jaw (the ring is a child so it can swing)
		PartDefinition knocker = root.addOrReplaceChild("knocker", band(4).addBox(-2.5F, -2.5F, -3.0F, 5.0F, 5.0F, 1.0F)
				.addBox(-2.5F, -2.5F, 2.0F, 5.0F, 5.0F, 1.0F), PartPose.ZERO);
		// (y is up in this renderer: the ring hangs below the jaw, its pivot at the jaw)
		knocker.addOrReplaceChild("ring", band(4).addBox(-2.0F, -0.6F, -3.6F, 4.0F, 0.6F, 0.6F).addBox(-2.0F, -4.0F, -3.6F, 4.0F, 0.6F, 0.6F)
				.addBox(-2.0F, -4.0F, -3.6F, 0.6F, 4.0F, 0.6F).addBox(1.4F, -4.0F, -3.6F, 0.6F, 4.0F, 0.6F)
				.addBox(-2.0F, -0.6F, 3.0F, 4.0F, 0.6F, 0.6F).addBox(-2.0F, -4.0F, 3.0F, 4.0F, 0.6F, 0.6F)
				.addBox(-2.0F, -4.0F, 3.0F, 0.6F, 4.0F, 0.6F).addBox(1.4F, -4.0F, 3.0F, 0.6F, 4.0F, 0.6F), PartPose.offset(0.0F, -1.2F, 0.0F));
		// crack overlays: the panel box a hair larger, alpha-cut crack bands
		root.addOrReplaceChild("crack_light", band(5).addBox(-0.02F, -0.02F, -2.06F, 16.04F, 16.04F, 4.12F), PartPose.ZERO);
		root.addOrReplaceChild("crack_heavy", band(6).addBox(-0.03F, -0.03F, -2.08F, 16.06F, 16.06F, 4.16F), PartPose.ZERO);
		// the Yamen cell door, one block per panel: frame posts and rails, five round bars, the lock plate
		root.addOrReplaceChild("cell_frame", band(7).addBox(0.0F, 0.0F, -1.0F, 1.5F, 16.0F, 2.0F).addBox(14.5F, 0.0F, -1.0F, 1.5F, 16.0F, 2.0F)
				.addBox(1.5F, 0.0F, -1.0F, 13.0F, 1.5F, 2.0F).addBox(1.5F, 14.5F, -1.0F, 13.0F, 1.5F, 2.0F), PartPose.ZERO);
		root.addOrReplaceChild("cell_bars", band(7).addBox(3.4F, 1.5F, -0.5F, 1.0F, 13.0F, 1.0F).addBox(5.8F, 1.5F, -0.5F, 1.0F, 13.0F, 1.0F)
				.addBox(8.2F, 1.5F, -0.5F, 1.0F, 13.0F, 1.0F).addBox(10.6F, 1.5F, -0.5F, 1.0F, 13.0F, 1.0F).addBox(13.0F, 1.5F, -0.5F, 1.0F, 13.0F, 1.0F)
				.addBox(1.5F, 7.4F, -0.7F, 13.0F, 1.2F, 1.4F), PartPose.ZERO);
		root.addOrReplaceChild("cell_lock", band(1).addBox(11.5F, 6.0F, -1.8F, 3.5F, 4.5F, 3.6F).addBox(12.6F, 7.2F, -2.1F, 1.2F, 1.8F, 0.4F),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, 128, 256);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(GerbangKotaEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yaw = entity.getYRot(partialTicks);
		state.open = Mth.clamp(KaitModel.gerbangBuka.baca(entity, partialTicks), 0.0F, 1.0F);
		state.width = Mth.clamp(KaitModel.gerbangLebar.baca(entity, partialTicks), 1.0F, 16.0F);
		state.height = Mth.clamp(KaitModel.gerbangTinggi.baca(entity, partialTicks), 1.0F, 16.0F);
		state.broken = Mth.clamp(KaitModel.gerbangRoboh.baca(entity, partialTicks), 0.0F, 1.0F);
		state.cracks = Mth.clamp(KaitModel.gerbangRetak.baca(entity, partialTicks), 0.0F, 1.0F);
		state.cell = KaitModel.gerbangSel.baca(entity) != 0;
		float[] m = motion.computeIfAbsent(entity, e -> new float[] {state.open, 0.0F});
		float delta = state.open - m[0];
		boolean moving = Math.abs(delta) > 1.0E-4F;
		if (moving && m[1] == 0.0F && !state.cell) {
			dust(entity, state);
		}
		// the knocker rings swing with the leaves' motion and settle when they stop
		state.swing = Mth.clamp(delta * 40.0F, -1.0F, 1.0F);
		m[0] = state.open;
		m[1] = moving ? 1.0F : 0.0F;
	}

	/** A puff of the ground's own dust along the leaves' feet as they start to swing (client particles, budgeted). */
	private static void dust(GerbangKotaEntity entity, State state) {
		Level level = entity.level();
		if (state.distanceToCameraSq > 48.0D * 48.0D) {
			return;
		}
		BlockPos below = entity.blockPosition().below();
		BlockState ground = level.getBlockState(below);
		if (ground.isAir()) {
			return;
		}
		RandomSource random = level.getRandom();
		int n = DEBU[Math.min(2, EffectQuality.level().ordinal())];
		float yr = state.yaw * Mth.DEG_TO_RAD;
		double ax = Mth.cos(yr);
		double az = Mth.sin(yr);
		BlockParticleOption option = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		for (int i = 0; i < n; i++) {
			double along = (random.nextDouble() - 0.5D) * state.width;
			double x = entity.getX() + ax * along + (random.nextDouble() - 0.5D) * 0.6D;
			double z = entity.getZ() + az * along + (random.nextDouble() - 0.5D) * 0.6D;
			level.addParticle(option, x, entity.getY() + 0.05D, z, (random.nextDouble() - 0.5D) * 0.12D, 0.08D + random.nextDouble() * 0.06D,
					(random.nextDouble() - 0.5D) * 0.12D);
		}
	}

	@Override
	protected AABB getBoundingBoxForCulling(GerbangKotaEntity entity, float partialTicks) {
		float w = Mth.clamp(KaitModel.gerbangLebar.baca(entity, partialTicks), 1.0F, 16.0F);
		float h = Mth.clamp(KaitModel.gerbangTinggi.baca(entity, partialTicks), 1.0F, 16.0F);
		return new AABB(entity.getX() - w, entity.getY(), entity.getZ() - w, entity.getX() + w, entity.getY() + h + 1.0D, entity.getZ() + w);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.isInvisible) {
			boolean detail = state.distanceToCameraSq < (EffectQuality.minimal() ? 24.0D * 24.0D : 64.0D * 64.0D);
			float angle = state.open * MAX_ANGLE;
			poseStack.pushPose();
			poseStack.rotateDegrees(Axis.YP, -state.yaw);
			if (state.cell) {
				cellDoor(state, poseStack, collector, angle, detail);
			} else {
				float half = state.width / 2.0F;
				for (int side = -1; side <= 1; side += 2) {
					poseStack.pushPose();
					poseStack.translate(side * half, 0.0F, 0.0F);
					// the left leaf (side -1) turns clockwise seen from above, toward +z; the right one mirrors it
					poseStack.rotateDegrees(Axis.YP, side * angle);
					if (side < 0 && state.broken > 0.0F) {
						float tremble = Mth.sin(state.ageInTicks * 2.7F) * 0.8F * state.broken;
						poseStack.rotateDegrees(Axis.XP, 12.0F * state.broken + tremble);
					}
					leaf(state, poseStack, collector, -side, half, detail);
					poseStack.popPose();
				}
				if (state.open < 0.05F && detail) {
					// the crossbar across both shut leaves, on the city side, resting in two iron brackets
					poseStack.pushPose();
					poseStack.translate(-half - 0.25F, state.height * 0.45F, 0.16F);
					poseStack.scale(state.width + 0.5F, 1.0F, 1.0F);
					collector.submitModelPart(bar, poseStack, type, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
					poseStack.popPose();
				}
			}
			poseStack.popPose();
		}
		super.submit(state, poseStack, collector, camera);
	}

	/** One leaf from its hinge (the current origin) toward the middle along {@code dir} (+1 = +x). */
	private void leaf(State state, PoseStack poseStack, SubmitNodeCollector collector, int dir, float width, boolean detail) {
		int rows = Mth.ceil(state.height);
		int cols = Mth.ceil(width);
		ModelPart crack = state.cracks >= 0.65F ? crackHeavy : (state.cracks >= 0.3F ? crackLight : null);
		for (int c = 0; c < cols; c++) {
			float w = Math.min(1.0F, width - c);
			for (int r = 0; r < rows; r++) {
				float h = Math.min(1.0F, state.height - r);
				poseStack.pushPose();
				poseStack.translate(dir > 0 ? c : -c - w, r, 0.0F);
				poseStack.scale(w, h, 1.0F);
				collector.submitModelPart(panel, poseStack, type, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
				if (crack != null && ((c * 7 + r * 3) % 4 != 0 || crack == crackHeavy)) {
					collector.submitModelPart(crack, poseStack, type, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
				}
				if (detail) {
					collector.submitModelPart(studs, poseStack, type, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
					if (r == 1 || r == rows - 2) {
						collector.submitModelPart(strap, poseStack, type, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
					}
				}
				poseStack.popPose();
			}
		}
		if (!detail) {
			return;
		}
		// the meeting-edge beam, full leaf height
		poseStack.pushPose();
		poseStack.translate(dir * width, 0.0F, 0.0F);
		poseStack.scale(1.0F, state.height, 1.0F);
		collector.submitModelPart(edge, poseStack, type, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();
		for (int r = 0; r < rows; r += Math.max(1, rows / 3)) {
			poseStack.pushPose();
			poseStack.translate(0.0F, r, 0.0F);
			collector.submitModelPart(hinge, poseStack, type, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
			poseStack.popPose();
		}
		// the knocker at chest height, a hand's width from the meeting edge; the ring hangs and swings
		ring.xRot = state.swing * 0.5F + Mth.sin(state.ageInTicks * 0.6F) * 0.06F * Math.abs(state.swing);
		poseStack.pushPose();
		poseStack.translate(dir * (width - 0.45F), Math.min(state.height - 0.5F, 2.3F), 0.0F);
		collector.submitModelPart(knocker, poseStack, type, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();
	}

	/** The Yamen cell door: one barred leaf on the hinge at local -x, swinging toward +z. */
	private void cellDoor(State state, PoseStack poseStack, SubmitNodeCollector collector, float angle, boolean detail) {
		float half = state.width / 2.0F;
		poseStack.pushPose();
		poseStack.translate(-half, 0.0F, 0.0F);
		poseStack.rotateDegrees(Axis.YP, -angle);
		int rows = Mth.ceil(state.height);
		int cols = Mth.ceil(state.width);
		for (int c = 0; c < cols; c++) {
			float w = Math.min(1.0F, state.width - c);
			for (int r = 0; r < rows; r++) {
				float h = Math.min(1.0F, state.height - r);
				poseStack.pushPose();
				poseStack.translate(c, r, 0.0F);
				poseStack.scale(w, h, 1.0F);
				collector.submitModelPart(cellFrame, poseStack, type, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
				collector.submitModelPart(cellBars, poseStack, type, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
				if (detail && r == 0 && c == cols - 1) {
					collector.submitModelPart(cellLock, poseStack, type, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
				}
				poseStack.popPose();
			}
		}
		if (detail) {
			for (int r = 0; r < rows; r++) {
				poseStack.pushPose();
				poseStack.translate(0.0F, r, 0.0F);
				collector.submitModelPart(hinge, poseStack, type, state.lightCoords, OverlayTexture.NO_OVERLAY, null);
				poseStack.popPose();
			}
		}
		poseStack.popPose();
	}
}
