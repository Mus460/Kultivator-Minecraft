package dev.kultivator.client.sekte.turnamen;

import dev.kultivator.Kultivator;
import dev.kultivator.client.dunia.dimensi.render.Headwear;
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
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;

/**
 * The cuboid models of the tournament's visiting spectators (DESIGN 36.2.3): the body (the player model plus the cheering
 * pose), the robe (the cultivator robe layers of {@code CultivatorModels}, skirt hanging straight) and two head pieces on
 * the player head (they turn with it): the villager's caping, a conical straw hat in four tiers with a chin cord, and the
 * envoy's guan, a black topknot under a small jade crown pierced by a gold hairpin. The head pieces reuse the material
 * bands of {@link Headwear#TEXTURE} (straw, black, jade, gold), so no new texture is needed.
 */
public final class PenontonModels {

	public static final ModelLayerLocation CAPING = new ModelLayerLocation(Kultivator.id("penonton_caping"), "main");
	public static final ModelLayerLocation GUAN = new ModelLayerLocation(Kultivator.id("penonton_guan"), "main");
	/** How long one cheer lasts (ticks). */
	public static final float CHEER_TICKS = 34.0F;

	private static boolean registered;

	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ModelLayerRegistry.registerModelLayer(CAPING, PenontonModels::caping);
		ModelLayerRegistry.registerModelLayer(GUAN, PenontonModels::guan);
	}

	private static CubeListBuilder band(int band) {
		return CubeListBuilder.create().texOffs(0, band * 32);
	}

	private static PartDefinition emptyHead(MeshDefinition mesh) {
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		return head;
	}

	/** The villager's caping: four straw tiers, a knob, a dark chin cord. */
	private static LayerDefinition caping() {
		MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
		mesh.getRoot().clearRecursively();
		PartDefinition head = emptyHead(mesh);
		head.addOrReplaceChild("caping", band(Headwear.BAND_STRAW).addBox(-7.5F, -8.7F, -7.5F, 15.0F, 0.7F, 15.0F)
				.addBox(-5.5F, -9.5F, -5.5F, 11.0F, 0.8F, 11.0F).addBox(-3.5F, -10.4F, -3.5F, 7.0F, 0.9F, 7.0F)
				.addBox(-1.5F, -11.3F, -1.5F, 3.0F, 0.9F, 3.0F), PartPose.ZERO);
		head.addOrReplaceChild("knob", band(Headwear.BAND_BLACK).addBox(-0.5F, -11.9F, -0.5F, 1.0F, 0.6F, 1.0F), PartPose.ZERO);
		head.addOrReplaceChild("cord", band(Headwear.BAND_BLACK).addBox(-4.15F, -8.0F, -0.4F, 0.3F, 8.4F, 0.8F)
				.addBox(3.85F, -8.0F, -0.4F, 0.3F, 8.4F, 0.8F).addBox(-4.15F, 0.1F, -0.4F, 8.3F, 0.3F, 0.8F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 512);
	}

	/** The envoy's guan: black hair cap and topknot, a small jade crown, a gold hairpin through it, two ribbons. */
	private static LayerDefinition guan() {
		MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
		mesh.getRoot().clearRecursively();
		PartDefinition head = emptyHead(mesh);
		head.addOrReplaceChild("hair", band(Headwear.BAND_BLACK).addBox(-4.3F, -8.5F, -4.3F, 8.6F, 1.4F, 8.6F)
				.addBox(-4.3F, -8.0F, 3.4F, 8.6F, 5.5F, 0.9F), PartPose.ZERO);
		head.addOrReplaceChild("topknot", band(Headwear.BAND_BLACK).addBox(-1.4F, -10.6F, -1.4F, 2.8F, 2.4F, 2.8F), PartPose.offset(0.0F, 0.0F, 0.5F));
		head.addOrReplaceChild("crown", band(Headwear.BAND_JADE).addBox(-1.8F, -11.2F, -1.8F, 3.6F, 2.2F, 3.6F)
				.addBox(-1.2F, -11.9F, -1.2F, 2.4F, 0.7F, 2.4F), PartPose.offset(0.0F, 0.0F, 0.5F));
		head.addOrReplaceChild("pin", band(Headwear.BAND_GOLD).addBox(-3.6F, -0.35F, -0.35F, 7.2F, 0.7F, 0.7F),
				PartPose.offsetAndRotation(0.0F, -10.0F, 0.5F, 0.0F, 0.0F, 6.0F * Mth.DEG_TO_RAD));
		head.addOrReplaceChild("ribbons", band(Headwear.BAND_GOLD).addBox(-1.2F, 0.0F, 0.0F, 0.6F, 6.0F, 0.3F)
				.addBox(0.6F, 0.0F, 0.0F, 0.6F, 6.0F, 0.3F), PartPose.offsetAndRotation(0.0F, -9.2F, 2.2F, 0.18F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 512);
	}

	/** The spectator's body: the player model plus the cheer (both arms thrown up, waving, the head lifted). */
	public static final class Body extends PlayerModel {

		public Body(ModelPart root, boolean slim) {
			super(root, slim);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			if (!(state instanceof PenontonRenderState s) || !s.animate) {
				return;
			}
			float t = s.ageInTicks;
			if (s.cheer >= 0.0F) {
				float k = Mth.clamp(Math.min(s.cheer / 4.0F, (CHEER_TICKS - s.cheer) / 6.0F), 0.0F, 1.0F);
				float wave = Mth.sin(t * 0.9F) * 0.22F;
				this.rightArm.xRot = Mth.lerp(k, this.rightArm.xRot, -2.75F + wave);
				this.rightArm.zRot = Mth.lerp(k, this.rightArm.zRot, 0.28F);
				this.leftArm.xRot = Mth.lerp(k, this.leftArm.xRot, -2.75F - wave);
				this.leftArm.zRot = Mth.lerp(k, this.leftArm.zRot, -0.28F);
				this.head.xRot = Mth.lerp(k, this.head.xRot, -0.32F);
			} else {
				// Arms folded in the sleeves while waiting (envoys), or loose at the sides (villagers); a slow breath.
				float breath = Mth.sin(t * 0.07F) * 0.03F;
				if (s.kind == 0) {
					this.rightArm.xRot = -0.85F + breath;
					this.rightArm.yRot = -0.45F;
					this.leftArm.xRot = -0.85F + breath;
					this.leftArm.yRot = 0.45F;
				} else {
					this.rightArm.xRot += breath;
					this.leftArm.xRot -= breath;
				}
			}
		}
	}

	/** The robe on the cultivator robe layers: hat off, the outer parts on, the skirt hanging straight. */
	public static final class Robe extends PlayerModel {

		private final ModelPart skirt;

		public Robe(ModelPart root, boolean slim) {
			super(root, slim);
			this.skirt = this.body.getChild("robe_skirt");
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			if (state instanceof PenontonRenderState s && s.animate) {
				// the arms follow the body's pose (cheer, folded sleeves)
				if (s.cheer >= 0.0F) {
					float k = Mth.clamp(Math.min(s.cheer / 4.0F, (CHEER_TICKS - s.cheer) / 6.0F), 0.0F, 1.0F);
					float wave = Mth.sin(s.ageInTicks * 0.9F) * 0.22F;
					this.rightArm.xRot = Mth.lerp(k, this.rightArm.xRot, -2.75F + wave);
					this.rightArm.zRot = Mth.lerp(k, this.rightArm.zRot, 0.28F);
					this.leftArm.xRot = Mth.lerp(k, this.leftArm.xRot, -2.75F - wave);
					this.leftArm.zRot = Mth.lerp(k, this.leftArm.zRot, -0.28F);
				} else if (s.kind == 0) {
					float breath = Mth.sin(s.ageInTicks * 0.07F) * 0.03F;
					this.rightArm.xRot = -0.85F + breath;
					this.rightArm.yRot = -0.45F;
					this.leftArm.xRot = -0.85F + breath;
					this.leftArm.yRot = 0.45F;
				}
			}
			this.hat.visible = false;
			this.jacket.visible = true;
			this.leftSleeve.visible = true;
			this.rightSleeve.visible = true;
			this.leftPants.visible = true;
			this.rightPants.visible = true;
			this.skirt.xRot = -this.body.xRot * 0.9F + Mth.sin(state.ageInTicks * 0.09F) * 0.02F;
		}
	}

	/** One head piece (caping or guan) on the player head. */
	public static final class Piece extends PlayerModel {

		public Piece(ModelPart root) {
			super(root, false);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// the head lifts with the body's cheer, the piece with it
			if (state instanceof PenontonRenderState s && s.animate && s.cheer >= 0.0F) {
				this.head.xRot = Mth.lerp(cheerBlend(s.cheer), this.head.xRot, CHEER_HEAD);
			}
		}
	}

	/** The head's lift during a cheer (radians, up). */
	static final float CHEER_HEAD = -0.32F;

	/** 0..1: in over 4 ticks, out over the last 6 of {@link #CHEER_TICKS}. */
	static float cheerBlend(float since) {
		return Mth.clamp(Math.min(since / 4.0F, (CHEER_TICKS - since) / 6.0F), 0.0F, 1.0F);
	}

	private PenontonModels() {
	}
}
