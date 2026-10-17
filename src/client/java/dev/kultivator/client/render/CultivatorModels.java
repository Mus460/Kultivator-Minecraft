package dev.kultivator.client.render;

import dev.kultivator.Kultivator;
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
 * The extra player-shaped models of the cultivator look, baked from our own model layers:
 * <ul>
 * <li>the robe (Jubah): the player model inflated a little, without a head, plus a skirt hanging from the waist (its
 * texture sits in the head area of the 64x64 skin layout, which the robe does not use);</li>
 * <li>two glow shells for the Dharma form, inflated further.</li>
 * </ul>
 * Each exists for wide and slim arms. They are PlayerModels, so the vanilla animation and the meditation pose (the
 * PlayerModel mixin) move them exactly like the player.
 */
public final class CultivatorModels {

	public static final ModelLayerLocation ROBE = new ModelLayerLocation(Kultivator.id("robe"), "main");
	public static final ModelLayerLocation ROBE_SLIM = new ModelLayerLocation(Kultivator.id("robe_slim"), "main");
	public static final ModelLayerLocation GLOW = new ModelLayerLocation(Kultivator.id("dharma_glow"), "main");
	public static final ModelLayerLocation GLOW_SLIM = new ModelLayerLocation(Kultivator.id("dharma_glow_slim"), "main");
	public static final ModelLayerLocation GLOW_OUTER = new ModelLayerLocation(Kultivator.id("dharma_glow_outer"), "main");
	public static final ModelLayerLocation GLOW_OUTER_SLIM = new ModelLayerLocation(Kultivator.id("dharma_glow_outer_slim"), "main");

	/** How far the robe stands off the skin (model pixels): over the skin's jacket layer, under armour. */
	static final float ROBE_INFLATE = 0.32F;
	static final float GLOW_INFLATE = 0.75F;
	static final float GLOW_OUTER_INFLATE = 1.5F;
	static final String SKIRT = "robe_skirt";

	/** Registers the layer definitions (client init, before the entity models are baked). */
	static void register() {
		ModelLayerRegistry.registerModelLayer(ROBE, () -> robe(false));
		ModelLayerRegistry.registerModelLayer(ROBE_SLIM, () -> robe(true));
		ModelLayerRegistry.registerModelLayer(GLOW, () -> glow(false, GLOW_INFLATE));
		ModelLayerRegistry.registerModelLayer(GLOW_SLIM, () -> glow(true, GLOW_INFLATE));
		ModelLayerRegistry.registerModelLayer(GLOW_OUTER, () -> glow(false, GLOW_OUTER_INFLATE));
		ModelLayerRegistry.registerModelLayer(GLOW_OUTER_SLIM, () -> glow(true, GLOW_OUTER_INFLATE));
	}

	/** The robe: inflated body, arms and legs with their outer layers (sash, cuffs, hem), no head, a skirt on the body. */
	static LayerDefinition robe(boolean slim) {
		CubeDeformation inflate = new CubeDeformation(ROBE_INFLATE);
		MeshDefinition mesh = PlayerModel.createMesh(inflate, slim);
		PartDefinition root = mesh.getRoot();
		// No head: the hat and head of the robe model stay empty (their texture area holds the skirt).
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		// The skirt hangs from the waist down to the shins; it is a child of the body so it bends with it.
		root.getChild("body").addOrReplaceChild(SKIRT,
				CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, 0.0F, -2.5F, 9.0F, 9.0F, 5.0F, inflate),
				PartPose.offset(0.0F, 11.5F, 0.0F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	/** A plain inflated player shell for the Dharma glow (its texture only covers the base layer). */
	static LayerDefinition glow(boolean slim, float inflate) {
		return LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(inflate), slim), 64, 64);
	}

	/**
	 * The robe model: a PlayerModel whose outer layers (sash, collar, cuffs, hems) are always shown, whatever the skin's
	 * own layer settings, and whose skirt hangs straight down while crouching, flares a little while walking and folds
	 * up into a short apron while seated in meditation or riding.
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
			boolean showBody = !state.isSpectator;
			this.hat.visible = false;
			this.jacket.visible = showBody;
			this.leftSleeve.visible = showBody;
			this.rightSleeve.visible = showBody;
			this.leftPants.visible = showBody;
			this.rightPants.visible = showBody;

			// Keep the skirt hanging down when the body leans (crouching), with a soft sway.
			float walk = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			float sway = Mth.sin(state.ageInTicks * 0.09F) * 0.025F;
			this.skirt.xRot = -this.body.xRot * 0.9F + sway;
			// Long strides push the front and back of the skirt out instead of the legs poking through.
			this.skirt.zScale = 1.0F + 0.45F * walk;
			this.skirt.xScale = 1.0F + 0.06F * walk;

			// Seated (meditation, riding): the skirt folds up over the crossed legs.
			float seated = Math.max(MeditationPose.strength(state), state.isPassenger ? 1.0F : 0.0F);
			if (seated > 0.0F) {
				this.skirt.yScale = Mth.lerp(seated, 1.0F, 0.32F);
				this.skirt.zScale = Mth.lerp(seated, this.skirt.zScale, 1.25F);
				this.skirt.xScale = Mth.lerp(seated, this.skirt.xScale, 1.12F);
			}
			// Swimming and gliding: the skirt trails along the body.
			if (state.swimAmount > 0.0F || state.isFallFlying) {
				this.skirt.xRot = Mth.lerp(Math.max(state.swimAmount, state.isFallFlying ? 1.0F : 0.0F), this.skirt.xRot, 0.0F);
			}
		}
	}

	private CultivatorModels() {
	}
}
