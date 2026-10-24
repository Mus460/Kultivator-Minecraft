package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.client.dunia.tempat.model.PancarLayer;
import dev.kultivator.dunia.tempat.tema.oasis.KalajengkingRajaEntity;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Raja Kalajengking ({@code kultivator:kalajengking_raja}, DESIGN 38.T.9.2): the boss of the desert ruin, 4 blocks long.
 * A cephalothorax (32 x 14 x 28) with a raised carapace ridge, an eye mound with two median and four lateral eyes and
 * chelicerae; four abdomen segments tapering 28 to 22 wide; a six-segment tail curled up over the back (+30 degrees per
 * segment) ending in the bulb and hooked sting; two great claws (arm, hand with the fixed finger, hinged lower finger);
 * eight legs of three joints each. Black-red chitin with orange glowing cracks and eyes (the tamed Pasir variant: pale
 * sand chitin, only the eyes glow).
 * <p>
 * Idle: the tail sways, the claws click; walking: an alternating leg wave from the walk animation. {@link
 * KalajengkingRajaEntity#KEADAAN_CAPIT} snaps both claws in 6 ticks with a lunge; {@link
 * KalajengkingRajaEntity#KEADAAN_SENGAT} whips the tail forward over the head in 10 ticks (easeOutBack); {@link
 * KalajengkingRajaEntity#KEADAAN_GALI} sinks the body 2.5 blocks into the sand in 30 ticks with digging legs (drawing
 * only, no block changes), {@link KalajengkingRajaEntity#KEADAAN_TERSEMBUNYI} draws nothing and {@link
 * KalajengkingRajaEntity#KEADAAN_MUNCUL} bursts back up in 20 ticks. Textures {@code kalajengking_raja.png},
 * {@code kalajengking_pasir.png} (128 x 512, bands of 64) and their glow textures.
 */
public class KalajengkingRajaRenderer extends TemaMakhlukRenderer<KalajengkingRajaEntity, KalajengkingRajaRenderer.State, KalajengkingRajaRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("kalajengking_raja"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/kalajengking_raja.png");
	private static final Identifier TEX_PASIR = Kultivator.id("textures/entity/tempat/tema/kalajengking_pasir.png");
	private static final Identifier GLOW = Kultivator.id("textures/entity/tempat/tema/kalajengking_raja_pancar.png");
	private static final Identifier GLOW_PASIR = Kultivator.id("textures/entity/tempat/tema/kalajengking_pasir_pancar.png");

	static final int B = 64;
	static final int KITIN = 0;
	static final int PERUT = 1;
	static final int EKOR = 2;
	static final int CAPIT = 3;
	static final int KAKI = 4;
	static final int SENGAT = 5;
	static final int MATA = 6;
	static final int MULUT = 7;

	static final int RUAS_EKOR = 6;
	/** Sinking depth for the dig (model units, 2.5 blocks). */
	static final float DALAM = 40.0F;

	public static class State extends TemaMakhlukRenderer.State {
		public float capit;
		public float sengat;
		public float turun;
		public boolean sembunyi;
		public float gali;
		public boolean pasir;
	}

	public KalajengkingRajaRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), 1.8F, 1.0F, new Identifier[] {TEX, TEX_PASIR}, null);
		this.addLayer(new PancarLayer<>(this, GLOW, s -> s.pasir ? 0.0F : s.pancar));
		this.addLayer(new PancarLayer<>(this, GLOW_PASIR, s -> s.pasir ? s.pancar : 0.0F));
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, KalajengkingRajaRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected float getShadowRadius(State state) {
		return state.sembunyi ? 0.0F : super.getShadowRadius(state) * (1.0F - state.turun / DALAM);
	}

	@Override
	public void extractRenderState(KalajengkingRajaEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		float t = state.umur;
		state.pasir = state.varian == KalajengkingRajaEntity.VARIAN_PASIR;
		state.capit = 0.0F;
		state.sengat = 0.0F;
		state.turun = 0.0F;
		state.gali = 0.0F;
		state.sembunyi = false;
		switch (state.keadaan) {
			case KalajengkingRajaEntity.KEADAAN_CAPIT -> state.capit = maju(t, 0.0F, 6.0F);
			case KalajengkingRajaEntity.KEADAAN_SENGAT -> state.sengat = t < 10.0F ? lewat(maju(t, 0.0F, 10.0F)) : Math.max(0.0F, 1.0F - (t - 10.0F) / 8.0F);
			case KalajengkingRajaEntity.KEADAAN_GALI -> {
				state.turun = DALAM * sinus(maju(t, 0.0F, 30.0F));
				state.gali = 1.0F;
			}
			case KalajengkingRajaEntity.KEADAAN_TERSEMBUNYI -> {
				state.sembunyi = true;
				state.turun = DALAM;
			}
			case KalajengkingRajaEntity.KEADAAN_MUNCUL -> {
				state.turun = DALAM * (1.0F - lewat(maju(t, 0.0F, 20.0F)));
				state.gali = 1.0F - maju(t, 10.0F, 10.0F);
			}
			default -> {
			}
		}
		// the cracks and eyes breathe slowly
		state.pancar = 0.75F + 0.25F * Mth.sin(state.ageInTicks * 0.08F + state.fase);
	}

	private static CubeListBuilder p(int band) {
		return CubeListBuilder.create().texOffs(0, band * B);
	}

	private static CubeListBuilder p(int band, int u) {
		return CubeListBuilder.create().texOffs(u, band * B);
	}

	static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition tubuh = root.addOrReplaceChild("tubuh", p(KITIN).addBox(-16.0F, -7.0F, -14.0F, 32.0F, 14.0F, 28.0F), PartPose.offset(0.0F, 8.0F, 0.0F));
		tubuh.addOrReplaceChild("perisai", p(KITIN, 30).addBox(-12.0F, -10.0F, -12.0F, 24.0F, 3.0F, 22.0F)
				.addBox(-6.0F, -11.5F, -13.0F, 12.0F, 1.5F, 10.0F), PartPose.ZERO);
		// eye mound: two median eyes on top, two lateral triplets at the front corners (6 glowing eyes)
		tubuh.addOrReplaceChild("mata", p(MATA).addBox(-2.6F, -12.6F, -11.0F, 2.0F, 1.2F, 2.0F).addBox(0.6F, -12.6F, -11.0F, 2.0F, 1.2F, 2.0F)
				.addBox(-11.0F, -8.0F, -14.6F, 1.0F, 1.0F, 1.0F).addBox(-9.0F, -8.6F, -14.6F, 1.0F, 1.0F, 1.0F)
				.addBox(10.0F, -8.0F, -14.6F, 1.0F, 1.0F, 1.0F).addBox(8.0F, -8.6F, -14.6F, 1.0F, 1.0F, 1.0F), PartPose.ZERO);
		tubuh.addOrReplaceChild("mulut", p(MULUT).addBox(-4.5F, 0.0F, -18.0F, 3.0F, 3.0F, 4.0F).addBox(1.5F, 0.0F, -18.0F, 3.0F, 3.0F, 4.0F)
				.addBox(-3.0F, 3.0F, -16.0F, 6.0F, 2.0F, 3.0F), PartPose.ZERO);
		// ---------------------------------------------------------------- abdomen: four tapering segments, each hinged on the one before
		PartDefinition ruas = tubuh;
		float[][] perut = {{28.0F, 12.0F, 10.0F}, {26.0F, 11.3F, 10.0F}, {24.0F, 10.6F, 10.0F}, {22.0F, 10.0F, 8.0F}};
		for (int i = 0; i < perut.length; i++) {
			float w = perut[i][0];
			float h = perut[i][1];
			float d = perut[i][2];
			float z = i == 0 ? 14.0F : perut[i - 1][2];
			ruas = ruas.addOrReplaceChild("perut_" + i, p(PERUT, i * 23).addBox(-w / 2.0F, -h / 2.0F - 1.0F, 0.0F, w, h, d)
					.addBox(-w / 2.0F + 2.0F, -h / 2.0F - 2.2F, 0.5F, w - 4.0F, 1.2F, d - 1.0F), PartPose.offsetAndRotation(0.0F, i == 0 ? 0.0F : -0.3F, z, -0.04F,
					0.0F, 0.0F));
		}
		// ---------------------------------------------------------------- tail: six segments curling up (+30 degrees each), bulb and hook
		PartDefinition ekor = ruas.addOrReplaceChild("ekor_0", p(EKOR).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 12.0F), PartPose.offsetAndRotation(0.0F,
				-3.0F, 8.0F, rad(30.0F), 0.0F, 0.0F));
		ekor.addOrReplaceChild("duri", p(EKOR, 70).addBox(-1.0F, -6.5F, 2.0F, 2.0F, 1.5F, 8.0F), PartPose.ZERO);
		for (int i = 1; i < RUAS_EKOR; i++) {
			float k = i / (RUAS_EKOR - 1.0F);
			float s = Mth.lerp(k, 9.0F, 6.0F);
			float len = Mth.lerp(k, 11.5F, 8.0F);
			float prev = i == 1 ? 12.0F : Mth.lerp((i - 1) / (RUAS_EKOR - 1.0F), 11.5F, 8.0F);
			ekor = ekor.addOrReplaceChild("ekor_" + i, p(EKOR, i * 19).addBox(-s / 2.0F, -s / 2.0F, 0.0F, s, s, len)
					.addBox(-0.8F, -s / 2.0F - 1.2F, 1.0F, 1.6F, 1.2F, len - 2.0F), PartPose.offsetAndRotation(0.0F, 0.0F, prev - 0.5F, rad(30.0F), 0.0F, 0.0F));
		}
		PartDefinition sengat = ekor.addOrReplaceChild("sengat", p(SENGAT).addBox(-4.0F, -5.0F, 0.0F, 8.0F, 10.0F, 14.0F), PartPose.offsetAndRotation(0.0F,
				0.0F, 7.5F, rad(20.0F), 0.0F, 0.0F));
		PartDefinition kait = sengat.addOrReplaceChild("kait", p(SENGAT, 50).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 6.0F), PartPose.offsetAndRotation(0.0F,
				2.5F, 13.0F, rad(45.0F), 0.0F, 0.0F));
		kait.addOrReplaceChild("ujung", p(SENGAT, 70).addBox(-0.8F, -0.8F, 0.0F, 1.6F, 1.6F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 5.5F, rad(40.0F),
				0.0F, 0.0F));
		// ---------------------------------------------------------------- claws: arm, hand with the fixed finger, hinged lower finger
		for (int side = -1; side <= 1; side += 2) {
			String id = side < 0 ? "kiri" : "kanan";
			PartDefinition bahu = tubuh.addOrReplaceChild("capit_" + id, p(CAPIT).addBox(-3.0F, -3.0F, -20.0F, 6.0F, 6.0F, 20.0F),
					PartPose.offsetAndRotation(side * 12.0F, -1.0F, -13.0F, -0.1F, side * -0.45F, 0.0F));
			PartDefinition siku = bahu.addOrReplaceChild("siku", p(CAPIT, 50).addBox(-2.5F, -2.5F, -14.0F, 5.0F, 5.0F, 14.0F),
					PartPose.offsetAndRotation(0.0F, 0.0F, -19.0F, 0.1F, side * 0.9F, 0.0F));
			PartDefinition tangan = siku.addOrReplaceChild("tangan", p(CAPIT, 80).addBox(-5.0F, -4.0F, -14.0F, 10.0F, 8.0F, 14.0F)
					.addBox(-4.0F, -4.8F, -12.0F, 8.0F, 1.0F, 10.0F), PartPose.offsetAndRotation(0.0F, 0.0F, -13.0F, 0.0F, side * -0.35F, 0.0F));
			tangan.addOrReplaceChild("jari_atas", p(CAPIT, 110).addBox(-3.0F, -3.0F, -12.0F, 6.0F, 4.0F, 12.0F)
					.addBox(-2.0F, -2.2F, -15.0F, 4.0F, 2.6F, 3.0F), PartPose.offset(0.0F, -1.0F, -14.0F));
			tangan.addOrReplaceChild("jari_bawah", p(CAPIT, 20).addBox(-5.0F, 0.0F, -14.0F, 10.0F, 4.0F, 14.0F)
					.addBox(-1.6F, -0.6F, -16.0F, 3.2F, 2.6F, 2.0F), PartPose.offset(0.0F, 2.5F, -12.0F));
		}
		// ---------------------------------------------------------------- eight legs, three joints (built along x, mirrored per side)
		for (int side = -1; side <= 1; side += 2) {
			for (int i = 0; i < 4; i++) {
				String id = (side < 0 ? "k" : "n") + i;
				float fwd = (1.5F - i) * 0.32F;
				float x0 = side < 0 ? -14.0F : 0.0F;
				PartDefinition paha = tubuh.addOrReplaceChild("kaki_" + id, p(KAKI, i * 31).addBox(x0, -1.5F, -1.5F, 14.0F, 3.0F, 3.0F),
						PartPose.offsetAndRotation(side * 15.0F, 0.0F, -9.0F + i * 6.0F, 0.0F, side * fwd, side * -rad(35.0F)));
				PartDefinition betis = paha.addOrReplaceChild("betis", p(KAKI, 40 + i * 13).addBox(x0, -1.25F, -1.25F, 14.0F, 2.5F, 2.5F),
						PartPose.offsetAndRotation(side * 13.5F, 0.0F, 0.0F, 0.0F, 0.0F, side * rad(85.0F)));
				betis.addOrReplaceChild("tarsus", p(KAKI, 90 + i * 7).addBox(x0, -1.0F, -1.0F, 14.0F, 2.0F, 2.0F)
						.addBox(side < 0 ? -15.0F : 13.0F, -0.6F, -0.6F, 2.0F, 1.2F, 1.2F), PartPose.offsetAndRotation(side * 13.5F, 0.0F, 0.0F, 0.0F, 0.0F,
						side * rad(25.0F)));
			}
		}
		return LayerDefinition.create(mesh, 128, B * 8);
	}

	/** The scorpion king's model: leg wave, swaying tail, claw snaps, the sting and the dig. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart wadah;
		private final ModelPart tubuh;
		private final ModelPart[] ekor = new ModelPart[RUAS_EKOR];
		private final ModelPart[] bahu = new ModelPart[2];
		private final ModelPart[] jari = new ModelPart[2];
		private final ModelPart[] paha = new ModelPart[8];
		private final ModelPart[] betis = new ModelPart[8];

		public Model(ModelPart root) {
			super(root);
			this.wadah = root;
			this.tubuh = root.getChild("tubuh");
			ModelPart r = tubuh;
			for (int i = 0; i < 4; i++) {
				r = r.getChild("perut_" + i);
			}
			ModelPart e = r.getChild("ekor_0");
			ekor[0] = e;
			for (int i = 1; i < RUAS_EKOR; i++) {
				e = e.getChild("ekor_" + i);
				ekor[i] = e;
			}
			for (int s = 0; s < 2; s++) {
				bahu[s] = tubuh.getChild(s == 0 ? "capit_kiri" : "capit_kanan");
				jari[s] = bahu[s].getChild("siku").getChild("tangan").getChild("jari_bawah");
			}
			for (int s = 0; s < 2; s++) {
				for (int i = 0; i < 4; i++) {
					paha[s * 4 + i] = tubuh.getChild("kaki_" + (s == 0 ? "k" : "n") + i);
					betis[s * 4 + i] = paha[s * 4 + i].getChild("betis");
				}
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			wadah.visible = !state.sembunyi;
			float age = state.ageInTicks;
			boolean still = state.jauh;
			tubuh.y += state.turun;
			float speed = Math.min(1.0F, state.walkAnimationSpeed * 2.0F);
			float pos = state.walkAnimationPos * 1.1F;
			// the leg wave: alternate tetrapods, digging legs paddle fast while sinking or bursting out
			for (int s = 0; s < 2; s++) {
				float side = s == 0 ? -1.0F : 1.0F;
				for (int i = 0; i < 4; i++) {
					int k = s * 4 + i;
					float ph = pos + i * Mth.HALF_PI + (s == 0 ? 0.0F : Mth.PI);
					float dig = state.gali * Mth.sin(age * 1.3F + i + s * 2.0F);
					paha[k].yRot += side * (Mth.sin(ph) * 0.3F * speed + dig * 0.35F);
					paha[k].zRot += side * (Math.min(0.0F, Mth.cos(ph)) * 0.35F * speed - Math.abs(dig) * 0.3F);
					betis[k].zRot += side * (Math.max(0.0F, Mth.cos(ph)) * 0.15F * speed);
				}
			}
			// tail: idle sway and a gentle curl breath; the sting whips it forward over the head
			float sway = still ? 0.0F : Mth.sin(age * 0.06F + state.fase) * 0.16F;
			for (int i = 0; i < RUAS_EKOR; i++) {
				ModelPart seg = ekor[i];
				seg.yRot = sway * (i == 0 ? 1.0F : 0.25F);
				seg.xRot = rad(30.0F) + (still ? 0.0F : Mth.sin(age * 0.09F - i * 0.5F) * 0.03F) + state.sengat * rad(i == 0 ? -25.0F : 9.0F);
			}
			// claws: idle clicking, the snap with a lunge
			for (int s = 0; s < 2; s++) {
				float side = s == 0 ? -1.0F : 1.0F;
				float click = still ? 0.0F : Math.max(0.0F, Mth.sin(age * 0.21F + s * 2.1F + state.fase)) * 0.25F;
				float snap = state.capit > 0.0F ? (state.capit < 0.5F ? state.capit * 2.0F : (1.0F - state.capit) * 2.0F) : 0.0F;
				jari[s].xRot = click + (state.capit > 0.0F ? (state.capit < 0.5F ? 0.7F * snap : 0.7F * snap * 0.3F) : 0.0F);
				bahu[s].xRot = -0.1F - snap * 0.25F;
				bahu[s].yRot = side * -0.45F + side * snap * 0.2F;
			}
			tubuh.xRot = state.sengat * rad(6.0F) - state.capit * (1.0F - state.capit) * 0.2F;
		}
	}
}
