package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.oasis.UntaKafilahEntity;
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
 * Unta kafilah ({@code kultivator:unta_kafilah}, DESIGN 38.T.9.2): the Bactrian caravan camel, built at the design's
 * sizes and drawn at 0.56 so it stands 2.3 blocks. A barrel body on the waist pivot, two humps with shaggy crowns, a long
 * neck raked 35 degrees with the hanging throat mane, a head with a split upper lip, a chewing lower jaw, flicking ears
 * and a rope halter; four legs of thigh, shin with the calloused knee pad and wide pad foot; a swishing tail. Pack
 * camels ({@link UntaKafilahEntity#VARIAN_BEBAN}) carry a frame, two sacks, a rolled carpet and a neck bell; the others a
 * saddle blanket. Winter coat ({@link UntaKafilahEntity#VARIAN_MUSIM_DINGIN}) is the shaggier texture.
 * <p>
 * Gait: a <b>pace</b> (both legs of one side together) from the walk animation, with the side-to-side body roll and a
 * nodding head; humps wobble +-2 degrees. Kneeling: front legs fold first (20 ticks), then the hind legs (20), the body
 * sinking 0.9 blocks; getting up runs the reverse (hind first); in a sandstorm it kneels with the head low. Textures
 * {@code unta_kafilah.png}, {@code unta_kafilah_dingin.png} (128 x 704, bands of 64).
 */
public class UntaKafilahRenderer extends TemaMakhlukRenderer<UntaKafilahEntity, UntaKafilahRenderer.State, UntaKafilahRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("unta_kafilah"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/unta_kafilah.png");
	private static final Identifier TEX_DINGIN = Kultivator.id("textures/entity/tempat/tema/unta_kafilah_dingin.png");

	static final int B = 64;
	static final int BULU = 0;
	static final int LEBAT = 1;
	static final int KAKI = 2;
	static final int KEPALA = 3;
	static final int KULIT = 4;
	static final int KAIN = 5;
	static final int KARUNG = 6;
	static final int KAYU = 7;
	static final int LOGAM = 8;
	static final int TALI = 9;
	static final int MATA = 10;

	/** Hip and shoulder height (model units) and the kneel drop: 0.9 blocks at the 0.56 scale. */
	static final float PINGGUL = -16.0F;
	static final float TURUN = 25.7F;
	/** Kneeling thigh rake and shin fold (radians). */
	static final float PAHA_LUTUT = 1.056F;
	static final float BETIS_LUTUT = 2.63F;

	public static class State extends TemaMakhlukRenderer.State {
		public float depan;
		public float belakang;
		public boolean beban;
		public boolean badai;
		public boolean lari;
	}

	public UntaKafilahRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), 1.0F, 0.56F, new Identifier[] {TEX, TEX, TEX_DINGIN}, null);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, UntaKafilahRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(UntaKafilahEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		float t = state.umur;
		float f;
		float b;
		switch (state.keadaan) {
			case UntaKafilahEntity.KEADAAN_BERLUTUT_TURUN -> {
				f = sinus(maju(t, 0.0F, 20.0F));
				b = sinus(maju(t, 20.0F, 20.0F));
			}
			case UntaKafilahEntity.KEADAAN_BERLUTUT, UntaKafilahEntity.KEADAAN_BADAI -> {
				f = 1.0F;
				b = 1.0F;
			}
			case UntaKafilahEntity.KEADAAN_BANGUN -> {
				b = 1.0F - sinus(maju(t, 0.0F, 20.0F));
				f = 1.0F - sinus(maju(t, 20.0F, 20.0F));
			}
			default -> {
				f = 0.0F;
				b = 0.0F;
			}
		}
		state.depan = f;
		state.belakang = b;
		state.beban = state.varian == UntaKafilahEntity.VARIAN_BEBAN;
		state.badai = state.keadaan == UntaKafilahEntity.KEADAAN_BADAI;
		state.lari = state.keadaan == UntaKafilahEntity.KEADAAN_LARI;
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
		// ---------------------------------------------------------------- body on the waist pivot
		PartDefinition tubuh = root.addOrReplaceChild("tubuh", p(BULU).addBox(-10.0F, -9.0F, -18.0F, 20.0F, 18.0F, 36.0F)
				.addBox(-9.0F, 8.0F, -14.0F, 18.0F, 2.0F, 26.0F), PartPose.offset(0.0F, PINGGUL - 9.0F, 0.0F));
		// chest and rump rounding
		tubuh.addOrReplaceChild("dada", p(BULU, 40).addBox(-9.0F, -7.0F, -21.0F, 18.0F, 15.0F, 3.0F), PartPose.ZERO);
		tubuh.addOrReplaceChild("pantat", p(BULU, 80).addBox(-9.0F, -8.0F, 18.0F, 18.0F, 14.0F, 3.0F), PartPose.ZERO);
		// two humps with a rounded cap and a shaggy crown each
		for (int i = 0; i < 2; i++) {
			float z = i == 0 ? -8.0F : 8.0F;
			PartDefinition punuk = tubuh.addOrReplaceChild("punuk_" + i, p(BULU, 20 + i * 30).addBox(-6.0F, -8.0F, -6.0F, 12.0F, 8.0F, 12.0F),
					PartPose.offset(0.0F, -9.0F, z));
			punuk.addOrReplaceChild("tudung", p(BULU, 60 + i * 20).addBox(-4.5F, -11.0F, -4.5F, 9.0F, 3.0F, 9.0F), PartPose.ZERO);
			punuk.addOrReplaceChild("jumbai", p(LEBAT, i * 40).addBox(-5.0F, -12.0F, -5.0F, 10.0F, 3.0F, 10.0F)
					.addBox(-6.3F, -9.0F, -6.3F, 12.6F, 3.0F, 12.6F), PartPose.ZERO);
		}
		// neck raked 35 degrees forward from the chest, hanging throat mane, head on top
		PartDefinition leher = tubuh.addOrReplaceChild("leher", p(BULU, 100).addBox(-4.0F, -22.0F, -4.0F, 8.0F, 22.0F, 8.0F),
				PartPose.offsetAndRotation(0.0F, -2.0F, -18.0F, rad(35.0F), 0.0F, 0.0F));
		leher.addOrReplaceChild("surai", p(LEBAT, 80).addBox(-4.5F, -18.0F, -6.0F, 9.0F, 10.0F, 2.0F)
				.addBox(-4.0F, -10.0F, -5.5F, 8.0F, 8.0F, 2.0F), PartPose.ZERO);
		leher.addOrReplaceChild("jumbai_atas", p(LEBAT, 100).addBox(-4.3F, -22.5F, -1.0F, 8.6F, 12.0F, 5.3F), PartPose.ZERO);
		PartDefinition kalung = leher.addOrReplaceChild("kalung", p(TALI).addBox(-4.4F, -8.0F, -4.4F, 8.8F, 1.6F, 8.8F), PartPose.ZERO);
		kalung.addOrReplaceChild("lonceng", p(LOGAM).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 3.0F, 3.0F).addBox(-2.0F, 2.6F, -2.0F, 4.0F, 0.8F, 4.0F)
				.addBox(-0.5F, -0.8F, -0.5F, 1.0F, 0.8F, 1.0F), PartPose.offset(0.0F, -6.0F, -6.0F));
		PartDefinition kepala = leher.addOrReplaceChild("kepala", p(KEPALA).addBox(-4.0F, -5.0F, -13.0F, 8.0F, 9.0F, 16.0F),
				PartPose.offsetAndRotation(0.0F, -21.0F, 0.0F, -rad(35.0F), 0.0F, 0.0F));
		kepala.addOrReplaceChild("dahi", p(LEBAT, 120).addBox(-4.4F, -6.0F, -6.0F, 8.8F, 3.0F, 8.0F), PartPose.ZERO);
		kepala.addOrReplaceChild("bibir", p(KULIT).addBox(-3.0F, -1.0F, -17.0F, 6.0F, 3.0F, 4.0F)
				.addBox(-0.4F, -1.2F, -17.2F, 0.8F, 1.4F, 0.6F), PartPose.ZERO);
		kepala.addOrReplaceChild("rahang", p(KULIT, 20).addBox(-2.6F, 0.0F, -12.0F, 5.2F, 2.0F, 12.0F), PartPose.offset(0.0F, 4.0F, -4.0F));
		kepala.addOrReplaceChild("hidung", p(KULIT, 40).addBox(-2.6F, -1.4F, -16.8F, 1.4F, 0.8F, 1.0F).addBox(1.2F, -1.4F, -16.8F, 1.4F, 0.8F, 1.0F),
				PartPose.ZERO);
		kepala.addOrReplaceChild("mata", p(MATA).addBox(-4.3F, -2.5F, -9.0F, 0.5F, 1.6F, 2.0F).addBox(3.8F, -2.5F, -9.0F, 0.5F, 1.6F, 2.0F), PartPose.ZERO);
		kepala.addOrReplaceChild("telinga_kiri", p(KEPALA, 60).addBox(-1.0F, -4.0F, -0.5F, 2.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(-3.0F, -5.0F, 1.0F,
				-0.2F, 0.0F, -0.45F));
		kepala.addOrReplaceChild("telinga_kanan", p(KEPALA, 70).addBox(-1.0F, -4.0F, -0.5F, 2.0F, 4.0F, 1.0F), PartPose.offsetAndRotation(3.0F, -5.0F, 1.0F,
				-0.2F, 0.0F, 0.45F));
		// rope halter: noseband, cheek straps, the lead ring
		kepala.addOrReplaceChild("tali_kepala", p(TALI, 20).addBox(-4.3F, -2.0F, -13.0F, 8.6F, 1.2F, 1.2F).addBox(-4.3F, -5.3F, -2.0F, 8.6F, 1.2F, 1.2F)
				.addBox(-4.4F, -5.0F, -12.6F, 0.6F, 4.0F, 0.6F).addBox(3.8F, -5.0F, -12.6F, 0.6F, 4.0F, 0.6F)
				.addBox(-4.4F, -4.6F, -12.6F, 0.6F, 0.6F, 11.0F).addBox(3.8F, -4.6F, -12.6F, 0.6F, 0.6F, 11.0F), PartPose.ZERO);
		kepala.addOrReplaceChild("cincin", p(LOGAM, 20).addBox(-0.8F, 0.0F, -0.3F, 1.6F, 2.4F, 0.6F), PartPose.offset(0.0F, 0.0F, -14.2F));
		// tail
		PartDefinition ekor = tubuh.addOrReplaceChild("ekor", p(BULU, 110).addBox(-1.5F, 0.0F, -1.0F, 3.0F, 12.0F, 2.0F),
				PartPose.offsetAndRotation(0.0F, -6.0F, 20.5F, 0.15F, 0.0F, 0.0F));
		ekor.addOrReplaceChild("ujung", p(LEBAT, 60).addBox(-1.8F, 9.0F, -1.3F, 3.6F, 5.0F, 2.6F), PartPose.ZERO);
		// riding saddle blanket (not on pack camels): between the humps, two side flaps, fringe
		PartDefinition pelana = tubuh.addOrReplaceChild("pelana", p(KAIN).addBox(-10.5F, -9.6F, -3.0F, 21.0F, 1.0F, 6.0F)
				.addBox(-11.0F, -9.0F, -7.0F, 1.0F, 12.0F, 14.0F).addBox(10.0F, -9.0F, -7.0F, 1.0F, 12.0F, 14.0F), PartPose.ZERO);
		pelana.addOrReplaceChild("rumbai", p(TALI, 40).addBox(-11.2F, 3.0F, -7.0F, 1.2F, 1.5F, 14.0F).addBox(10.0F, 3.0F, -7.0F, 1.2F, 1.5F, 14.0F),
				PartPose.ZERO);
		// pack load: frame, two sacks, rolled carpet
		PartDefinition beban = tubuh.addOrReplaceChild("beban", p(KAYU).addBox(-11.0F, -11.0F, -12.0F, 22.0F, 2.0F, 24.0F)
				.addBox(-12.0F, -11.0F, -11.0F, 1.0F, 10.0F, 2.0F).addBox(11.0F, -11.0F, -11.0F, 1.0F, 10.0F, 2.0F)
				.addBox(-12.0F, -11.0F, 9.0F, 1.0F, 10.0F, 2.0F).addBox(11.0F, -11.0F, 9.0F, 1.0F, 10.0F, 2.0F), PartPose.ZERO);
		beban.addOrReplaceChild("karung_kiri", p(KARUNG).addBox(-8.0F, 0.0F, -8.0F, 8.0F, 14.0F, 16.0F).addBox(-7.0F, -1.5F, -6.0F, 6.0F, 1.5F, 12.0F),
				PartPose.offsetAndRotation(-11.0F, -10.0F, 0.0F, 0.0F, 0.0F, 0.08F));
		beban.addOrReplaceChild("karung_kanan", p(KARUNG, 50).addBox(0.0F, 0.0F, -8.0F, 8.0F, 14.0F, 16.0F).addBox(1.0F, -1.5F, -6.0F, 6.0F, 1.5F, 12.0F),
				PartPose.offsetAndRotation(11.0F, -10.0F, 0.0F, 0.0F, 0.0F, -0.08F));
		beban.addOrReplaceChild("permadani", p(KAIN, 60).addBox(-11.0F, -6.0F, -3.0F, 22.0F, 6.0F, 6.0F), PartPose.offset(0.0F, -14.5F, 0.0F));
		beban.addOrReplaceChild("ikatan", p(TALI, 60).addBox(-11.5F, -21.0F, -1.0F, 1.0F, 12.0F, 2.0F).addBox(10.5F, -21.0F, -1.0F, 1.0F, 12.0F, 2.0F)
				.addBox(-11.5F, -21.4F, -1.0F, 23.0F, 1.0F, 2.0F), PartPose.ZERO);
		// ---------------------------------------------------------------- legs: thigh, shin with knee pad, pad foot
		for (int i = 0; i < 4; i++) {
			boolean front = i < 2;
			float x = (i % 2 == 0 ? -1.0F : 1.0F) * 6.0F;
			float z = front ? -12.0F : 13.0F;
			PartDefinition paha = root.addOrReplaceChild("paha_" + i, p(KAKI, i * 24).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 24.0F, 6.0F),
					PartPose.offset(x, PINGGUL, z));
			paha.addOrReplaceChild("bulu", p(LEBAT, 20 + i * 24).addBox(-3.5F, -1.0F, -3.5F, 7.0F, front ? 10.0F : 7.0F, 7.0F), PartPose.ZERO);
			PartDefinition betis = paha.addOrReplaceChild("betis", p(KAKI, 100 + i * 7).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 14.0F, 5.0F),
					PartPose.offset(0.0F, 24.0F, 0.0F));
			betis.addOrReplaceChild("lutut", p(KULIT, 60 + i * 9).addBox(-3.0F, -1.5F, -3.2F, 6.0F, 3.0F, 3.0F), PartPose.ZERO);
			betis.addOrReplaceChild("telapak", p(KULIT, 90 + i * 5).addBox(-3.5F, 0.0F, -4.0F, 7.0F, 2.0F, 7.0F)
					.addBox(-3.6F, 1.2F, -4.4F, 1.6F, 0.8F, 1.6F).addBox(2.0F, 1.2F, -4.4F, 1.6F, 0.8F, 1.6F), PartPose.offset(0.0F, 14.0F, 0.0F));
		}
		return LayerDefinition.create(mesh, 128, B * 11);
	}

	/** The camel's model: pace gait, kneeling, chewing, humps, tail, load. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart tubuh;
		private final ModelPart leher;
		private final ModelPart kepala;
		private final ModelPart rahang;
		private final ModelPart telingaKiri;
		private final ModelPart telingaKanan;
		private final ModelPart ekor;
		private final ModelPart pelana;
		private final ModelPart beban;
		private final ModelPart lonceng;
		private final ModelPart[] punuk = new ModelPart[2];
		private final ModelPart[] paha = new ModelPart[4];
		private final ModelPart[] betis = new ModelPart[4];

		public Model(ModelPart root) {
			super(root);
			this.tubuh = root.getChild("tubuh");
			this.leher = tubuh.getChild("leher");
			this.kepala = leher.getChild("kepala");
			this.rahang = kepala.getChild("rahang");
			this.telingaKiri = kepala.getChild("telinga_kiri");
			this.telingaKanan = kepala.getChild("telinga_kanan");
			this.ekor = tubuh.getChild("ekor");
			this.pelana = tubuh.getChild("pelana");
			this.beban = tubuh.getChild("beban");
			this.lonceng = leher.getChild("kalung").getChild("lonceng");
			for (int i = 0; i < 2; i++) {
				punuk[i] = tubuh.getChild("punuk_" + i);
			}
			for (int i = 0; i < 4; i++) {
				paha[i] = root.getChild("paha_" + i);
				betis[i] = paha[i].getChild("betis");
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float age = state.ageInTicks;
			boolean still = state.jauh;
			float kneel = Math.max(state.depan, state.belakang);
			float speed = Math.min(1.0F, state.walkAnimationSpeed * 1.6F) * (1.0F - kneel);
			float pos = state.walkAnimationPos * (state.lari ? 0.75F : 0.55F);
			pelana.visible = !state.beban;
			beban.visible = state.beban;
			lonceng.visible = state.beban;
			// pace: legs 0 and 2 are the left side, 1 and 3 the right; one side swings together
			for (int i = 0; i < 4; i++) {
				boolean front = i < 2;
				float side = i % 2 == 0 ? 0.0F : Mth.PI;
				float ph = pos + side;
				float swing = Mth.sin(ph) * 0.55F * speed;
				float lift = Math.max(0.0F, -Mth.cos(ph)) * 0.7F * speed;
				float k = front ? state.depan : state.belakang;
				paha[i].y += TURUN * k;
				paha[i].xRot = swing - PAHA_LUTUT * k;
				betis[i].xRot = lift * (front ? 1.0F : 0.6F) + BETIS_LUTUT * k;
			}
			// body: sinks front first, rolls with the pace, breathes
			float df = TURUN * state.depan;
			float db = TURUN * state.belakang;
			tubuh.y += (df + db) * 0.5F;
			tubuh.xRot = (float) Math.atan2(df - db, 25.0F);
			tubuh.zRot = Mth.sin(pos) * 0.06F * speed;
			if (!still) {
				tubuh.y += Mth.sin(age * 0.08F + state.fase) * 0.3F;
			}
			for (int i = 0; i < 2; i++) {
				punuk[i].zRot = still ? 0.0F : Mth.sin(pos + 0.6F + i * 0.4F) * rad(2.0F) * (0.3F + speed) + Mth.sin(age * 0.05F + i) * rad(0.6F);
				punuk[i].xRot = still ? 0.0F : Mth.cos(pos * 2.0F + i) * rad(1.5F) * speed;
			}
			// neck and head: nod with the gait, look around, chew; low in a sandstorm
			float nod = Mth.sin(pos * 2.0F) * 0.07F * speed;
			float low = state.badai ? 1.0F : 0.0F;
			leher.xRot = rad(35.0F) + nod + low * 0.75F + kneel * 0.15F + (state.lari ? 0.2F : 0.0F);
			kepala.xRot = -rad(35.0F) - nod * 0.6F + low * 0.25F - kneel * 0.1F;
			float yaw = Mth.clamp((state.yRot) * Mth.DEG_TO_RAD, -0.6F, 0.6F);
			kepala.yRot = yaw * 0.6F;
			leher.yRot = yaw * 0.4F;
			if (!still) {
				boolean chew = speed < 0.2F && !state.badai;
				rahang.xRot = chew ? (Mth.sin(age * 0.55F) * 0.5F + 0.5F) * 0.16F : 0.02F;
				rahang.yRot = chew ? Mth.sin(age * 0.275F) * 0.08F : 0.0F;
				float flick = Mth.sin(age * 0.09F + state.fase) > 0.97F ? Mth.sin(age * 1.4F) * 0.3F : 0.0F;
				telingaKiri.zRot = -0.45F + flick - low * 0.5F;
				telingaKanan.zRot = 0.45F - flick * 0.7F + low * 0.5F;
				ekor.zRot = Mth.sin(age * 0.13F + state.fase) * (0.2F + 0.25F * speed);
				ekor.xRot = 0.15F + Mth.sin(age * 0.07F) * 0.05F + speed * 0.2F;
				lonceng.zRot = Mth.sin(pos * 2.0F) * 0.35F * speed + Mth.sin(age * 0.2F) * 0.05F;
			}
		}
	}
}
