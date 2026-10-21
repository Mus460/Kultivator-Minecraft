package dev.kultivator.client.dunia.dimensi.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.dunia.dimensi.laut.PenyuAoEntity;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pulau Penyu Ao ({@link PenyuAoEntity}): the giant turtle, a cuboid model scaled 4× (shell 10 × 12 b in four tiers of
 * plates, mossy top, a beaked head that looks about, four paddling flippers that slow while it rests) and the small
 * island on its back drawn from the game's own block models (grass and moss, a hill, a cherry tree, a stone lantern,
 * rocks and flowers; the layout is seeded by the turtle). Block count by Effect Quality 60 / 150 / 300 and by distance
 * (40 beyond 96 b): nothing is placed in the world, so the island goes wherever the turtle swims.
 */
public class PenyuRenderer extends EntityRenderer<PenyuAoEntity, PenyuRenderer.State> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("penyu_ao"), "main");
	private static final Identifier TEXTURE = Kultivator.id("textures/dunia/dimensi/entity/penyu_ao.png");
	private static final float MODEL_SCALE = 4.0F;
	private static final Map<Integer, List<Piece>> ISLANDS = new HashMap<>();

	/** One block of the island: local position (blocks, x right, z toward the tail) and its state. */
	private record Piece(int x, int y, int z, BlockState state) {
	}

	public static class State extends EntityRenderState {
		public float yaw;
		public boolean resting;
		public int seed;
	}

	private final Model model;
	private final BlockModelResolver blocks;
	private final Map<BlockState, BlockModelRenderState> blockStates = new IdentityHashMap<>();

	public PenyuRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new Model(context.bakeLayer(LAYER));
		this.blocks = context.getBlockModelResolver();
		this.shadowRadius = 0.0F;
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, PenyuRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PenyuAoEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yaw = entity.getYRot(partialTicks);
		state.resting = entity.resting();
		state.seed = entity.seed();
	}

	@Override
	protected AABB getBoundingBoxForCulling(PenyuAoEntity entity, float partialTicks) {
		return entity.getBoundingBox().inflate(4.0D, 8.0D, 4.0D);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, 180.0F - state.yaw);
		poseStack.pushPose();
		poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0F, -1.501F, 0.0F);
		collector.submitModel(model, state, poseStack, model.renderType(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY,
				state.outlineColor);
		poseStack.popPose();
		island(state, poseStack, collector);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/** The island's blocks on the shell top ({@code PenyuAoEntity.DECK} above the feet). */
	private void island(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		List<Piece> pieces = ISLANDS.computeIfAbsent(state.seed, PenyuRenderer::layout);
		int cap = switch (EffectQuality.level()) {
			case MINIMAL -> 60;
			case MAXIMAL -> 300;
			default -> 150;
		};
		if (state.distanceToCameraSq > 96.0D * 96.0D) {
			cap = Math.min(cap, 40);
		}
		poseStack.pushPose();
		poseStack.translate(0.0F, (float) PenyuAoEntity.DECK, 0.0F);
		int drawn = 0;
		java.util.Set<BlockState> fresh = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		for (Piece piece : pieces) {
			if (drawn++ >= cap) {
				break;
			}
			BlockModelRenderState render = blockStates.computeIfAbsent(piece.state(), key -> new BlockModelRenderState());
			if (fresh.add(piece.state())) {
				blocks.update(render, piece.state(), DisplayRenderer.BLOCK_DISPLAY_CONTEXT);
			}
			poseStack.pushPose();
			poseStack.translate(piece.x() - 0.5F, piece.y(), piece.z() - 0.5F);
			render.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	/** The seeded island layout, most important blocks first (the tree, the ground disc, the hill, the details). */
	private static List<Piece> layout(int seed) {
		RandomSource random = RandomSource.create(seed);
		List<Piece> out = new ArrayList<>();
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		BlockState moss = Blocks.MOSS_BLOCK.defaultBlockState();
		BlockState dirt = Blocks.ROOTED_DIRT.defaultBlockState();
		BlockState log = Blocks.CHERRY_LOG.defaultBlockState();
		BlockState leaves = Blocks.CHERRY_LEAVES.defaultBlockState();
		int tx = random.nextInt(3) - 1;
		int tz = random.nextInt(3) - 2;
		// the tree: trunk and the leaf core first
		for (int y = 2; y <= 5; y++) {
			out.add(new Piece(tx, y, tz, log));
		}
		List<Piece> outerLeaves = new ArrayList<>();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = 0; dy <= 2; dy++) {
					double d = Math.sqrt(dx * dx + dz * dz + (dy - 1) * (dy - 1) * 1.6D);
					if (d > 2.4D || (dx == 0 && dz == 0 && dy < 1)) {
						continue;
					}
					Piece leaf = new Piece(tx + dx, 5 + dy, tz + dz, leaves);
					if (d <= 1.5D) {
						out.add(leaf);
					} else {
						outerLeaves.add(leaf);
					}
				}
			}
		}
		// the ground disc (r 4.2) and the hill (r 2.5, 1.3)
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d <= 4.2D) {
					out.add(new Piece(x, 0, z, d > 3.4D && random.nextInt(3) == 0 ? moss : grass));
				}
			}
		}
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				double d = Math.sqrt((x - tx * 0.5D) * (x - tx * 0.5D) + (z - tz * 0.5D) * (z - tz * 0.5D));
				if (d <= 2.5D && !(x == tx && z == tz)) {
					out.add(new Piece(x, 1, z, d > 1.8D ? grass : dirt));
				}
				if (d <= 1.3D && !(x == tx && z == tz)) {
					out.add(new Piece(x, 2, z, grass));
				}
			}
		}
		out.add(new Piece(tx, 1, tz, dirt));
		out.addAll(outerLeaves);
		// a stone lantern on the shore, rocks, flowers and grass
		int lx = random.nextBoolean() ? 3 : -3;
		out.add(new Piece(lx, 1, 2, Blocks.ANDESITE_WALL.defaultBlockState()));
		out.add(new Piece(lx, 2, 2, Blocks.LANTERN.defaultBlockState()));
		for (int i = 0; i < 4; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			out.add(new Piece((int) Math.round(Math.cos(angle) * 3.6D), 1, (int) Math.round(Math.sin(angle) * 3.6D),
					random.nextBoolean() ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.TUFF.defaultBlockState()));
		}
		BlockState[] flowers = {Blocks.SHORT_GRASS.defaultBlockState(), Blocks.POPPY.defaultBlockState(), Blocks.ALLIUM.defaultBlockState(),
				Blocks.AZURE_BLUET.defaultBlockState(), Blocks.SHORT_GRASS.defaultBlockState(), Blocks.LILY_OF_THE_VALLEY.defaultBlockState()};
		for (int i = 0; i < 40; i++) {
			int x = random.nextInt(9) - 4;
			int z = random.nextInt(9) - 4;
			double d = Math.sqrt(x * x + z * z);
			if (d > 2.6D && d <= 4.0D) {
				out.add(new Piece(x, 1, z, flowers[random.nextInt(flowers.length)]));
			}
		}
		return out;
	}

	private static CubeListBuilder band(int band) {
		return CubeListBuilder.create().texOffs(0, band * 64);
	}

	static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition shell = root.addOrReplaceChild("shell", band(1).addBox(-18.0F, -2.0F, -22.0F, 36.0F, 4.0F, 44.0F),
				PartPose.offset(0.0F, 24.0F, 0.0F));
		shell.addOrReplaceChild("lower", band(0).addBox(-20.0F, -8.0F, -24.0F, 40.0F, 6.0F, 48.0F), PartPose.ZERO);
		shell.addOrReplaceChild("middle", band(2).addBox(-17.0F, -11.0F, -20.0F, 34.0F, 3.0F, 40.0F), PartPose.ZERO);
		shell.addOrReplaceChild("top", band(3).addBox(-12.0F, -13.6F, -15.0F, 24.0F, 2.6F, 30.0F), PartPose.ZERO);
		CubeListBuilder rim = band(4);
		for (int i = 0; i < 6; i++) {
			float z = -20.0F + i * 8.0F;
			rim.addBox(-21.0F, -6.5F, z, 1.0F, 3.0F, 6.0F).addBox(20.0F, -6.5F, z, 1.0F, 3.0F, 6.0F);
		}
		rim.addBox(-16.0F, -6.5F, -25.0F, 32.0F, 3.0F, 1.0F).addBox(-16.0F, -6.5F, 24.0F, 32.0F, 3.0F, 1.0F);
		shell.addOrReplaceChild("rim", rim, PartPose.ZERO);
		shell.addOrReplaceChild("scutes", band(4).addBox(-8.0F, -12.0F, -19.0F, 6.0F, 1.0F, 6.0F).addBox(2.0F, -12.0F, -19.0F, 6.0F, 1.0F, 6.0F)
				.addBox(-14.0F, -12.0F, -6.0F, 5.0F, 1.0F, 10.0F).addBox(9.0F, -12.0F, -6.0F, 5.0F, 1.0F, 10.0F)
				.addBox(-8.0F, -12.0F, 14.0F, 6.0F, 1.0F, 5.0F).addBox(2.0F, -12.0F, 14.0F, 6.0F, 1.0F, 5.0F), PartPose.ZERO);
		PartDefinition head = root.addOrReplaceChild("head", band(5).addBox(-5.0F, -4.0F, -6.0F, 10.0F, 8.0F, 8.0F)
				.addBox(-7.0F, -7.0F, -16.0F, 14.0F, 10.0F, 11.0F), PartPose.offset(0.0F, 20.0F, -24.0F));
		head.addOrReplaceChild("beak", band(6).addBox(-5.0F, -3.0F, -20.0F, 10.0F, 6.0F, 4.0F), PartPose.ZERO);
		head.addOrReplaceChild("eyes", band(7).addBox(-7.3F, -5.0F, -13.0F, 0.4F, 2.0F, 3.0F).addBox(6.9F, -5.0F, -13.0F, 0.4F, 2.0F, 3.0F),
				PartPose.ZERO);
		head.addOrReplaceChild("crest", band(4).addBox(-3.0F, -8.0F, -14.0F, 6.0F, 1.0F, 8.0F), PartPose.ZERO);
		for (int side = -1; side <= 1; side += 2) {
			String s = side < 0 ? "r" : "l";
			root.addOrReplaceChild("front_" + s, band(5).addBox(side < 0 ? -24.0F : 0.0F, -1.0F, -5.0F, 24.0F, 2.0F, 10.0F),
					PartPose.offsetAndRotation(side * 18.0F, 22.0F, -14.0F, 0.0F, side * 0.3F, 0.0F));
			root.addOrReplaceChild("back_" + s, band(5).addBox(side < 0 ? -14.0F : 0.0F, -1.0F, -4.0F, 14.0F, 2.0F, 8.0F),
					PartPose.offsetAndRotation(side * 17.0F, 22.0F, 16.0F, 0.0F, side * -0.4F, 0.0F));
		}
		root.addOrReplaceChild("tail", band(5).addBox(-3.0F, -2.0F, 0.0F, 6.0F, 4.0F, 8.0F), PartPose.offset(0.0F, 23.0F, 24.0F));
		return LayerDefinition.create(mesh, 256, 512);
	}

	/** The turtle's model: flippers paddle, the head looks about; slower while someone stands on its back. */
	static final class Model extends EntityModel<State> {

		private final ModelPart head;
		private final ModelPart frontL;
		private final ModelPart frontR;
		private final ModelPart backL;
		private final ModelPart backR;

		Model(ModelPart root) {
			super(root);
			this.head = root.getChild("head");
			this.frontL = root.getChild("front_l");
			this.frontR = root.getChild("front_r");
			this.backL = root.getChild("back_l");
			this.backR = root.getChild("back_r");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float age = state.ageInTicks;
			float amp = state.resting ? 0.08F : 0.38F;
			float speed = state.resting ? 0.03F : 0.07F;
			float stroke = Mth.sin(age * speed);
			frontL.zRot += stroke * amp;
			frontR.zRot -= stroke * amp;
			frontL.yRot += Mth.cos(age * speed) * amp * 0.5F;
			frontR.yRot -= Mth.cos(age * speed) * amp * 0.5F;
			backL.zRot -= stroke * amp * 0.6F;
			backR.zRot += stroke * amp * 0.6F;
			head.yRot = Mth.sin(age * 0.01F) * 0.35F;
			head.xRot = Mth.sin(age * 0.013F + 1.0F) * 0.08F;
		}
	}
}
