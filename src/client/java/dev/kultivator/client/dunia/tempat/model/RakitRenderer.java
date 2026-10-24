package dev.kultivator.client.dunia.tempat.model;

import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.dunia.tempat.jalan.RakitEntity;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Rakit Penyeberangan ({@code kultivator:rakit}, DESIGN 38.C.6.2 / 38.O.2): the bamboo ferry raft of a wide river crossing.
 * Seven 4 × 4 × 48 culms side by side, two cross bars lashed with rope, a lantern post at the bow and a long pole lying on
 * the deck. It rides the water (LIFT ±0.04 over 50 ticks, a ±1.2° roll), and during the poling scene
 * ({@link KaitModel#rakitDayung}, by default one stroke every 20 ticks while the ferryman poles) the pole rises, plunges
 * and pulls back over 20 ticks; water splashes where it bites and a wake curls off the bow while the raft moves. The
 * entity is drawn at its own height (the owner keeps it on the water surface). Texture {@code textures/entity/tempat/rakit.png} (64 × 96: bamboo,
 * rope, dark pole).
 */
public class RakitRenderer extends BendaRenderer<RakitEntity, RakitRenderer.State, RakitRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("rakit"), "main");
	static final Identifier TEXTURE = Kultivator.id("textures/entity/tempat/rakit.png");

	public static class State extends BendaRenderer.State {
		public float pole = -1.0F;
	}

	/** Splash particles per stroke and wake particles per tick of travel (Minimal / Normal / Maximal). */
	private static final int[] PERCIK = {2, 5, 9};
	private static final int[] RIAK = {0, 1, 2};

	/** Per raft: the last stroke reading and the last wake tick (client only, forgotten with the entity). */
	private final Map<RakitEntity, float[]> kayuh = new WeakHashMap<>();

	public RakitRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEXTURE, null, 0.0F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, RakitRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(RakitEntity entity) {
		return 1.4D;
	}

	@Override
	public void extractRenderState(RakitEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.pole = KaitModel.rakitDayung.baca(entity, partialTicks);
		percik(entity, state);
	}

	/** The pole's bite (once per stroke, at 45 %) and the bow wake (once per tick while poling). */
	private void percik(RakitEntity entity, State state) {
		float[] k = kayuh.computeIfAbsent(entity, e -> new float[] {-1.0F, -1.0F});
		float last = k[0];
		k[0] = state.pole;
		if (state.pole < 0.0F || state.distanceToCameraSq > 48.0D * 48.0D) {
			return;
		}
		Level level = entity.level();
		RandomSource random = level.getRandom();
		int tier = Math.min(2, EffectQuality.level().ordinal());
		float yr = state.yaw * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yr);
		double fz = Mth.cos(yr);
		if (last >= 0.0F && last < 0.45F && state.pole >= 0.45F) {
			// the pole tip: 8 px to the side and 18 px aft of the centre (model x -8, z +18)
			double px = entity.getX() + (-0.5D) * Mth.cos(yr) - fx * 1.125D;
			double pz = entity.getZ() + (-0.5D) * Mth.sin(yr) - fz * 1.125D;
			for (int i = 0; i < PERCIK[tier]; i++) {
				level.addParticle(ParticleTypes.SPLASH, px + (random.nextDouble() - 0.5D) * 0.4D, entity.getY() + 0.1D,
						pz + (random.nextDouble() - 0.5D) * 0.4D, 0.0D, 0.1D, 0.0D);
			}
		}
		if (entity.tickCount != (int) k[1]) {
			k[1] = entity.tickCount;
			for (int i = 0; i < RIAK[tier]; i++) {
				double side = random.nextBoolean() ? 1.0D : -1.0D;
				level.addParticle(ParticleTypes.SPLASH, entity.getX() + fx * 1.5D + side * Mth.cos(yr) * 0.9D, entity.getY() + 0.05D,
						entity.getZ() + fz * 1.5D + side * Mth.sin(yr) * 0.9D, 0.0D, 0.02D, 0.0D);
			}
		}
	}

	static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition deck = root.addOrReplaceChild("deck", CubeListBuilder.create(), PartPose.offset(0.0F, 22.0F, 0.0F));
		for (int i = 0; i < 7; i++) {
			float x = -14.0F + i * 4.0F;
			float lift = (i % 2) * 0.3F;
			deck.addOrReplaceChild("culm_" + i, band(0).addBox(x, -2.0F + lift, -24.0F, 4.0F, 4.0F, 24.0F).addBox(x, -2.0F + lift, 0.0F, 4.0F, 4.0F, 24.0F),
					PartPose.ZERO);
		}
		deck.addOrReplaceChild("bars", band(2).addBox(-15.0F, -3.5F, -17.0F, 30.0F, 2.0F, 2.0F).addBox(-15.0F, -3.5F, 15.0F, 30.0F, 2.0F, 2.0F),
				PartPose.ZERO);
		deck.addOrReplaceChild("lashings", band(1).addBox(-15.2F, -3.8F, -17.4F, 2.0F, 6.2F, 2.8F).addBox(13.2F, -3.8F, -17.4F, 2.0F, 6.2F, 2.8F)
				.addBox(-15.2F, -3.8F, 14.6F, 2.0F, 6.2F, 2.8F).addBox(13.2F, -3.8F, 14.6F, 2.0F, 6.2F, 2.8F)
				.addBox(-1.0F, -3.8F, -17.4F, 2.0F, 6.2F, 2.8F).addBox(-1.0F, -3.8F, 14.6F, 2.0F, 6.2F, 2.8F), PartPose.ZERO);
		PartDefinition post = deck.addOrReplaceChild("post", band(2).addBox(-0.75F, -16.0F, -0.75F, 1.5F, 16.0F, 1.5F), PartPose.offset(10.0F, -2.0F, -21.0F));
		post.addOrReplaceChild("lantern_arm", band(2).addBox(-0.5F, -0.5F, -4.0F, 1.0F, 1.0F, 4.0F), PartPose.offset(0.0F, -15.0F, 0.0F));
		post.addOrReplaceChild("lantern", band(1).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F), PartPose.offset(0.0F, -14.5F, -4.0F));
		post.addOrReplaceChild("post_tie", band(1).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offset(0.0F, -1.0F, 0.0F));
		// the pole: lying on the deck, or held for the poling scene
		PartDefinition pole = root.addOrReplaceChild("pole", band(2).addBox(-0.5F, -24.0F, -0.5F, 1.0F, 24.0F, 1.0F).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 24.0F, 1.0F),
				PartPose.offsetAndRotation(-9.0F, 18.0F, 0.0F, Mth.HALF_PI, 0.0F, 0.0F));
		pole.addOrReplaceChild("pole_tip", band(1).addBox(-0.7F, 23.0F, -0.7F, 1.4F, 2.0F, 1.4F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 96);
	}

	/** The raft's model: riding the water and the poling scene. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart deck;
		private final ModelPart pole;

		public Model(ModelPart root) {
			super(root);
			this.deck = root.getChild("deck");
			this.pole = root.getChild("pole");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float age = state.ageInTicks;
			float bob = Mth.sin(age * Mth.TWO_PI / 50.0F) * 0.64F;
			float roll = Mth.sin(age * Mth.TWO_PI / 70.0F + 1.0F) * 1.2F * Mth.DEG_TO_RAD;
			deck.y += bob;
			deck.zRot = roll;
			deck.xRot = Mth.sin(age * Mth.TWO_PI / 90.0F) * 0.6F * Mth.DEG_TO_RAD;
			if (state.pole >= 0.0F) {
				// held upright at the stern, plunged (0-0.5), pushed back (0.5-0.8), lifted again (0.8-1)
				float t = state.pole % 1.0F;
				float plunge = t < 0.5F ? sine(t / 0.5F) : (t < 0.8F ? 1.0F : 1.0F - sine((t - 0.8F) / 0.2F));
				float push = t < 0.5F ? 0.0F : (t < 0.8F ? sine((t - 0.5F) / 0.3F) : 1.0F - sine((t - 0.8F) / 0.2F));
				pole.x = -8.0F;
				pole.z = 18.0F;
				pole.y = 2.0F + bob + plunge * 10.0F;
				pole.xRot = 0.25F - push * 0.45F;
				pole.zRot = 0.08F;
			} else {
				pole.y += bob - 0.5F;
				pole.zRot = roll;
			}
		}

		private static float sine(float t) {
			return (float) (-(Math.cos(Math.PI * t) - 1.0D) / 2.0D);
		}
	}
}
