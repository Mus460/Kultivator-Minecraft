package dev.kultivator.client.dunia.tempat.model;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.spec.CameraShake;
import dev.kultivator.dunia.tempat.gua.PenjagaKristalEntity;
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
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Penjaga Kristal ({@code kultivator:penjaga_kristal}, DESIGN 38.O.2): a 2.4-block golem of violet crystal that guards the
 * Gua Urat Roh. A rock core under a clustered crystal torso, a prism head turned 45° with a crown of three spikes, long
 * arms ending in crystal fists, stumpy legs, and six shards that orbit its shoulders and back. It hovers a hand's width
 * when idle (LIFT ±0.05 over 60 ticks), steps heavily (one step per 30 ticks of walking), raises both fists over its head
 * and smashes them down (easeOutBack) when it strikes, and on death its shards burst outward while the body sinks into
 * the floor over 20 ticks instead of tipping over. An emissive pass ({@link PancarLayer}) makes the crystal glow.
 * Felt, not only seen: every heavy step shakes the camera a little (0.1, full within 8 blocks, the player's shake
 * setting applies) and kicks up the floor's dust, the hammer blow lands with a stronger shake and a spray of amethyst
 * splinters, and the dying golem bursts into crystal shards and violet sparks (client particles, counts by
 * {@code EffectQuality}).
 * Texture {@code textures/entity/tempat/penjaga_kristal.png} (128 × 160, five 32 px bands: deep crystal, light crystal,
 * rock, glow, dark crystal; {@code tools/gen_tempat_models.py}), glow {@code penjaga_kristal_pancar.png}.
 */
public class PenjagaKristalRenderer extends MobRenderer<PenjagaKristalEntity, PenjagaKristalRenderer.State, PenjagaKristalRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("penjaga_kristal"), "main");
	static final Identifier TEXTURE = Kultivator.id("textures/entity/tempat/penjaga_kristal.png");
	static final Identifier GLOW = Kultivator.id("textures/entity/tempat/penjaga_kristal_pancar.png");

	public static class State extends LivingEntityRenderState {
		public float attack;
	}

	/** Particles per event (Minimal / Normal / Maximal): step dust, blow splinters, death shards. */
	private static final int[] DEBU = {1, 3, 5};
	private static final int[] SERPIH = {4, 10, 18};
	private static final int[] PECAH = {8, 22, 40};

	/** Per golem: last step index, last attack reading, death burst done (client only, forgotten with the entity). */
	private final Map<PenjagaKristalEntity, float[]> jejak = new WeakHashMap<>();

	public PenjagaKristalRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), 1.1F);
		this.addLayer(new PancarLayer<>(this, GLOW, s -> 0.65F + 0.35F * Mth.sin(s.ageInTicks * 0.08F)));
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, PenjagaKristalRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PenjagaKristalEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.attack = entity.getSwingAnimation(partialTicks);
		dampak(entity, state);
	}

	/** Step shakes, the blow's impact and the death burst, each once per event. */
	private void dampak(PenjagaKristalEntity entity, State state) {
		float[] j = jejak.computeIfAbsent(entity, e -> new float[] {Float.NaN, 0.0F, 0.0F});
		boolean near = state.distanceToCameraSq < 48.0D * 48.0D;
		Level level = entity.level();
		int tier = Math.min(2, EffectQuality.level().ordinal());
		// one step per half period of the gait curve sin(walkPos × 0.42)
		float stepIndex = Mth.floor(state.walkAnimationPos * 0.42F / Mth.PI);
		if (!Float.isNaN(j[0]) && stepIndex != j[0] && state.walkAnimationSpeed > 0.15F && state.deathTime <= 0.0F && near) {
			CameraShake.add(0.1F, 5, entity.position(), 4.0F);
			BlockState ground = level.getBlockState(entity.blockPosition().below());
			if (!ground.isAir()) {
				burst(level, new BlockParticleOption(ParticleTypes.BLOCK, ground), entity.position().add(0.0D, 0.05D, 0.0D), DEBU[tier], 0.6D, 0.06D);
			}
		}
		j[0] = stepIndex;
		// the hammer blow lands when the strike passes 60 %
		if (j[1] < 0.6F && state.attack >= 0.6F && near) {
			float yr = state.bodyRot * Mth.DEG_TO_RAD;
			Vec3 front = entity.position().add(-Mth.sin(yr) * 1.6D, 0.1D, Mth.cos(yr) * 1.6D);
			CameraShake.add(0.25F, 8, front, 5.0F);
			burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()), front, SERPIH[tier], 0.9D, 0.22D);
			burst(level, ParticleTypes.END_ROD, front.add(0.0D, 0.4D, 0.0D), SERPIH[tier] / 3, 0.5D, 0.08D);
		}
		j[1] = state.attack;
		// the death burst, once
		if (state.deathTime > 0.0F && j[2] == 0.0F) {
			j[2] = 1.0F;
			if (near) {
				Vec3 chest = entity.position().add(0.0D, 1.6D, 0.0D);
				CameraShake.add(0.3F, 10, chest, 6.0F);
				burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()), chest, PECAH[tier], 1.2D, 0.3D);
				burst(level, ParticleTypes.END_ROD, chest, PECAH[tier] / 2, 1.0D, 0.12D);
			}
		} else if (state.deathTime <= 0.0F) {
			j[2] = 0.0F;
		}
	}

	private static void burst(Level level, net.minecraft.core.particles.ParticleOptions option, Vec3 at, int count, double spread, double speed) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < count; i++) {
			double dx = (random.nextDouble() - 0.5D) * 2.0D;
			double dz = (random.nextDouble() - 0.5D) * 2.0D;
			level.addParticle(option, at.x + dx * spread * 0.5D, at.y + random.nextDouble() * 0.3D, at.z + dz * spread * 0.5D, dx * speed,
					speed * (0.6D + random.nextDouble()), dz * speed);
		}
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	@Override
	protected float getFlipDegrees() {
		return 0.0F; // it sinks, it does not tip over
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		poseStack.scale(1.1F, 1.1F, 1.1F);
	}

	private static CubeListBuilder band(int band) {
		return CubeListBuilder.create().texOffs(0, band * 32);
	}

	static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition waist = root.addOrReplaceChild("waist", CubeListBuilder.create(), PartPose.offset(0.0F, 12.0F, 0.0F));
		// rock core and the clustered crystal torso over it
		waist.addOrReplaceChild("core", band(2).addBox(-4.0F, -10.0F, -3.0F, 8.0F, 10.0F, 6.0F), PartPose.ZERO);
		PartDefinition torso = waist.addOrReplaceChild("torso", band(0).addBox(-5.0F, -14.0F, -4.0F, 10.0F, 9.0F, 8.0F)
				.addBox(-4.5F, -6.0F, -3.5F, 9.0F, 4.0F, 7.0F), PartPose.ZERO);
		torso.addOrReplaceChild("chest_facets", band(1).addBox(-3.5F, -13.0F, -4.6F, 3.0F, 5.0F, 1.0F).addBox(0.8F, -12.0F, -4.5F, 3.0F, 6.0F, 1.0F)
				.addBox(-1.5F, -8.0F, -4.4F, 3.0F, 3.0F, 1.0F), PartPose.ZERO);
		torso.addOrReplaceChild("back_ridge", band(4).addBox(-2.0F, -15.0F, 2.5F, 4.0F, 12.0F, 3.0F), PartPose.rotation(0.12F, 0.0F, 0.0F));
		// the prism head, turned 45°, with three crown spikes
		PartDefinition head = torso.addOrReplaceChild("head", band(1).addBox(-3.0F, -7.0F, -3.0F, 6.0F, 7.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, -14.0F, -0.5F, 0.0F, Mth.PI / 4.0F, 0.0F));
		head.addOrReplaceChild("eye_band", band(3).addBox(-3.2F, -4.5F, -3.2F, 6.4F, 1.2F, 6.4F), PartPose.ZERO);
		head.addOrReplaceChild("spike_c", band(0).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F), PartPose.offset(0.0F, -7.0F, 0.0F));
		head.addOrReplaceChild("spike_l", band(4).addBox(-0.75F, -3.5F, -0.75F, 1.5F, 3.5F, 1.5F),
				PartPose.offsetAndRotation(1.8F, -6.5F, 1.8F, 0.0F, 0.0F, -0.4F));
		head.addOrReplaceChild("spike_r", band(4).addBox(-0.75F, -3.5F, -0.75F, 1.5F, 3.5F, 1.5F),
				PartPose.offsetAndRotation(-1.8F, -6.5F, -1.8F, 0.0F, 0.0F, 0.4F));
		// arms with crystal fists
		for (int side = -1; side <= 1; side += 2) {
			String s = side < 0 ? "r" : "l";
			PartDefinition arm = torso.addOrReplaceChild("arm_" + s, band(0).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 14.0F, 4.0F),
					PartPose.offsetAndRotation(side * 7.0F, -12.0F, 0.0F, 0.0F, 0.0F, side * 0.08F));
			arm.addOrReplaceChild("shoulder", band(4).addBox(-3.0F, -3.5F, -3.0F, 6.0F, 4.0F, 6.0F), PartPose.ZERO);
			arm.addOrReplaceChild("fist", band(1).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 6.0F, 6.0F), PartPose.offset(0.0F, 11.0F, 0.0F));
			arm.addOrReplaceChild("knuckles", band(3).addBox(-2.5F, 4.0F, -3.4F, 5.0F, 1.5F, 1.0F), PartPose.offset(0.0F, 11.0F, 0.0F));
		}
		// legs
		for (int side = -1; side <= 1; side += 2) {
			String s = side < 0 ? "r" : "l";
			PartDefinition leg = root.addOrReplaceChild("leg_" + s, band(2).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 10.0F, 4.0F),
					PartPose.offset(side * 2.6F, 13.0F, 0.0F));
			leg.addOrReplaceChild("foot", band(4).addBox(-2.5F, 8.0F, -3.0F, 5.0F, 3.0F, 5.0F), PartPose.ZERO);
		}
		// six orbiting shards (the orbit part turns; each shard is tilted ±22.5°)
		PartDefinition orbit = waist.addOrReplaceChild("orbit", CubeListBuilder.create(), PartPose.offset(0.0F, -11.0F, 0.0F));
		for (int i = 0; i < 6; i++) {
			float a = i * Mth.TWO_PI / 6.0F;
			float r = 8.5F;
			float tilt = (i % 2 == 0 ? 1.0F : -1.0F) * 0.3927F;
			orbit.addOrReplaceChild("shard_" + i, band(i % 2 == 0 ? 1 : 4).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 6.0F, 2.0F),
					PartPose.offsetAndRotation(Mth.sin(a) * r, (i % 3) * 1.5F - 1.5F, Mth.cos(a) * r, tilt, 0.0F, -tilt));
		}
		return LayerDefinition.create(mesh, 128, 160);
	}

	/** The golem's model and its animation. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart waist;
		private final ModelPart torso;
		private final ModelPart head;
		private final ModelPart armR;
		private final ModelPart armL;
		private final ModelPart legR;
		private final ModelPart legL;
		private final ModelPart orbit;
		private final ModelPart[] shards = new ModelPart[6];

		public Model(ModelPart root) {
			super(root);
			this.waist = root.getChild("waist");
			this.torso = waist.getChild("torso");
			this.head = torso.getChild("head");
			this.armR = torso.getChild("arm_r");
			this.armL = torso.getChild("arm_l");
			this.legR = root.getChild("leg_r");
			this.legL = root.getChild("leg_l");
			this.orbit = waist.getChild("orbit");
			for (int i = 0; i < 6; i++) {
				shards[i] = orbit.getChild("shard_" + i);
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float age = state.ageInTicks;
			float speed = Math.min(1.0F, state.walkAnimationSpeed * 1.6F);
			// idle hover and the heavy gait (one step per 30 ticks of walking)
			float hover = Mth.sin(age * Mth.TWO_PI / 60.0F) * 0.8F * (1.0F - speed);
			float step = state.walkAnimationPos * 0.42F;
			float stomp = Math.abs(Mth.sin(step)) * speed;
			waist.y += hover - stomp * 0.8F;
			legR.xRot = Mth.sin(step) * 0.7F * speed;
			legL.xRot = -Mth.sin(step) * 0.7F * speed;
			torso.zRot = Mth.sin(step) * 0.06F * speed;
			armR.xRot = -Mth.sin(step) * 0.45F * speed;
			armL.xRot = Mth.sin(step) * 0.45F * speed;
			head.yRot += Mth.sin(age * 0.03F) * 0.12F;
			// the shards orbit 2° per tick
			orbit.yRot = age * 2.0F * Mth.DEG_TO_RAD;
			for (int i = 0; i < 6; i++) {
				shards[i].y += Mth.sin(age * 0.1F + i) * 0.6F;
			}
			// strike: both fists up to -150° (first 60 %), then a hammer blow down with an overshoot
			if (state.attack > 0.0F) {
				float t = state.attack;
				float raise;
				if (t < 0.6F) {
					raise = ease(t / 0.6F) * -2.62F;
				} else {
					float k = (t - 0.6F) / 0.4F;
					raise = -2.62F + backOut(k) * 2.27F;
				}
				armR.xRot = raise;
				armL.xRot = raise;
				armR.zRot = 0.15F;
				armL.zRot = -0.15F;
				torso.xRot = t < 0.6F ? -0.15F * ease(t / 0.6F) : 0.25F * (1.0F - (t - 0.6F) / 0.4F);
			}
			// death: the body sinks 24 px over 20 ticks and the shards burst outward
			if (state.deathTime > 0.0F) {
				float d = Math.min(1.0F, state.deathTime / 20.0F);
				waist.y += d * 18.0F;
				legR.y += d * 8.0F;
				legL.y += d * 8.0F;
				for (int i = 0; i < 6; i++) {
					ModelPart s = shards[i];
					s.x *= 1.0F + d * 1.6F;
					s.z *= 1.0F + d * 1.6F;
					s.y -= d * 6.0F;
					s.xRot += d * 3.0F;
				}
			}
		}

		private static float ease(float t) {
			return t * t * (3.0F - 2.0F * t);
		}

		private static float backOut(float t) {
			float c1 = 1.70158F;
			float c3 = c1 + 1.0F;
			float u = t - 1.0F;
			return 1.0F + c3 * u * u * u + c1 * u * u;
		}
	}
}
