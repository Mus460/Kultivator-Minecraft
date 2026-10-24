package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.bersama.TanduEntity;
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
 * Tandu ({@code kultivator:tandu}, DESIGN 38.T.9.3): the procession palanquin (Hari Dewi Laut, the Tuan Benteng, the
 * Demon Lord). A lacquered cabin 24 x 32 x 24 on a carved base, corner columns, an arched roof of three boards with
 * upturned eaves and a finial, side curtains of silk (thin plates that swing), a front curtain looped up, a seat with
 * a cushion, and two carrying poles (2 x 2 x 96) with brass end caps. Carried ({@link TanduEntity#KEADAAN_PIKUL}) it
 * sways +-2 degrees and bobs in step with the bearers' clip 106 {@code pikul_tandu} (30 ticks a step pair), the curtains
 * lagging behind. Three looks: Dewi Laut (blue, white and gold, a pearl finial), Pasir (ochre and red with tassels),
 * Hitam (black and blood red, horned finial). Textures {@code tandu_dewi_laut.png}, {@code tandu_pasir.png},
 * {@code tandu_hitam.png} (128 x 512, bands of 64).
 */
public class TanduRenderer extends TemaBendaRenderer<TanduEntity, TanduRenderer.State, TanduRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("tandu"), "main");

	static final int B = 64;
	static final int LAK = 0;
	static final int KAYU = 1;
	static final int ATAP = 2;
	static final int TIRAI = 3;
	static final int EMAS = 4;
	static final int BANTAL = 5;
	static final int GALAH = 6;
	static final int RUMBAI = 7;

	public static class State extends TemaBendaRenderer.State {
		public float goyang;
		public float naik;
		public float tirai;
		public boolean pikul;
	}

	public TanduRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), new Identifier[] {tex("dewi_laut"), tex("pasir"), tex("hitam")}, null, 0.8F);
	}

	private static Identifier tex(String look) {
		return Kultivator.id("textures/entity/tempat/tema/tandu_" + look + ".png");
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, TanduRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(TanduEntity entity) {
		return 3.5D;
	}

	@Override
	protected int tekstur(State state) {
		return Mth.clamp(state.varian, 0, 2);
	}

	@Override
	public void extractRenderState(TanduEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.pikul = state.keadaan == TanduEntity.KEADAAN_PIKUL;
		if (state.pikul && !state.jauh) {
			float ph = (state.umur % 30.0F) / 30.0F * Mth.TWO_PI;
			state.goyang = Mth.sin(ph) * rad(2.0F);
			state.naik = Math.abs(Mth.sin(ph)) * 0.8F;
			state.tirai = Mth.sin(ph - 0.9F) * 0.22F;
		} else {
			state.goyang = 0.0F;
			state.naik = 0.0F;
			state.tirai = state.jauh ? 0.0F : Mth.sin(state.waktu * 0.04F + state.fase) * 0.04F;
		}
	}

	private static CubeListBuilder p(int band) {
		return pita(band, B);
	}

	private static CubeListBuilder p(int band, int u) {
		return pita(band, B, u);
	}

	static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// carried at shoulder height (the poles 1.4 blocks up); set down, it stands on its base feet
		PartDefinition tandu = root.addOrReplaceChild("tandu", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
		tandu.addOrReplaceChild("kaki", p(KAYU).addBox(-12.0F, -3.0F, -12.0F, 3.0F, 3.0F, 3.0F).addBox(9.0F, -3.0F, -12.0F, 3.0F, 3.0F, 3.0F)
				.addBox(-12.0F, -3.0F, 9.0F, 3.0F, 3.0F, 3.0F).addBox(9.0F, -3.0F, 9.0F, 3.0F, 3.0F, 3.0F), PartPose.ZERO);
		tandu.addOrReplaceChild("alas", p(LAK).addBox(-13.0F, -7.0F, -13.0F, 26.0F, 4.0F, 26.0F).addBox(-12.0F, -8.0F, -12.0F, 24.0F, 1.0F, 24.0F),
				PartPose.ZERO);
		tandu.addOrReplaceChild("hias_alas", p(EMAS).addBox(-13.3F, -6.0F, -13.3F, 26.6F, 1.0F, 26.6F), PartPose.ZERO);
		// corner columns and the back and side panels (lower half), the seat
		for (int i = 0; i < 4; i++) {
			float x = i % 2 == 0 ? -12.0F : 10.0F;
			float z = i < 2 ? -12.0F : 10.0F;
			tandu.addOrReplaceChild("tiang_" + i, p(LAK, 30 + i * 6).addBox(0.0F, -32.0F, 0.0F, 2.0F, 24.0F, 2.0F), PartPose.offset(x, 0.0F, z));
		}
		tandu.addOrReplaceChild("panel", p(KAYU, 30).addBox(-10.0F, -18.0F, 10.5F, 20.0F, 10.0F, 1.0F).addBox(-11.5F, -14.0F, -10.0F, 1.0F, 6.0F, 20.0F)
				.addBox(10.5F, -14.0F, -10.0F, 1.0F, 6.0F, 20.0F), PartPose.ZERO);
		tandu.addOrReplaceChild("kursi", p(KAYU, 80).addBox(-9.0F, -13.0F, 2.0F, 18.0F, 2.0F, 8.0F), PartPose.ZERO);
		tandu.addOrReplaceChild("bantal", p(BANTAL).addBox(-8.0F, -14.5F, 2.5F, 16.0F, 1.5F, 7.0F).addBox(-7.0F, -21.0F, 9.0F, 14.0F, 7.0F, 1.5F),
				PartPose.ZERO);
		// arched roof: a ridge board and two sloping boards with upturned eave tips, lintel ring, finial
		PartDefinition atap = tandu.addOrReplaceChild("atap", CubeListBuilder.create(), PartPose.offset(0.0F, -34.0F, 0.0F));
		atap.addOrReplaceChild("ambang", p(LAK, 60).addBox(-13.0F, 0.0F, -13.0F, 26.0F, 2.0F, 26.0F), PartPose.ZERO);
		atap.addOrReplaceChild("bubungan", p(ATAP).addBox(-5.0F, -6.0F, -15.0F, 10.0F, 2.0F, 30.0F), PartPose.ZERO);
		atap.addOrReplaceChild("kiri", p(ATAP, 40).addBox(-12.0F, 0.0F, -15.0F, 12.0F, 2.0F, 30.0F), PartPose.offsetAndRotation(-4.5F, -5.5F, 0.0F, 0.0F,
				0.0F, -0.45F));
		atap.addOrReplaceChild("kanan", p(ATAP, 80).addBox(0.0F, 0.0F, -15.0F, 12.0F, 2.0F, 30.0F), PartPose.offsetAndRotation(4.5F, -5.5F, 0.0F, 0.0F,
				0.0F, 0.45F));
		for (int i = 0; i < 4; i++) {
			float x = i % 2 == 0 ? -15.0F : 13.0F;
			float z = i < 2 ? -15.5F : 13.5F;
			atap.addOrReplaceChild("ujung_" + i, p(EMAS, 20 + i * 5).addBox(0.0F, -3.0F, 0.0F, 2.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(x, 0.0F, z, 0.0F,
					0.0F, i % 2 == 0 ? 0.5F : -0.5F));
		}
		PartDefinition puncak = atap.addOrReplaceChild("puncak", p(EMAS, 50).addBox(-1.5F, -9.0F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.ZERO);
		puncak.addOrReplaceChild("mutiara", p(EMAS, 70).addBox(-1.5F, -12.5F, -1.5F, 3.0F, 3.5F, 3.0F), PartPose.ZERO);
		puncak.addOrReplaceChild("tanduk", p(EMAS, 90).addBox(-3.5F, -13.0F, -0.5F, 1.0F, 4.0F, 1.0F).addBox(2.5F, -13.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				PartPose.ZERO);
		// curtains: hung from the lintel, swinging; the front one is looped up
		for (int side = -1; side <= 1; side += 2) {
			tandu.addOrReplaceChild("tirai_" + (side < 0 ? "k" : "n"), p(TIRAI, side < 0 ? 0 : 50).addBox(-0.2F, 0.0F, -10.0F, 0.4F, 18.0F, 20.0F),
					PartPose.offset(side * 12.2F, -32.0F, 0.0F));
		}
		tandu.addOrReplaceChild("tirai_depan", p(TIRAI, 90).addBox(-10.0F, 0.0F, -0.2F, 20.0F, 6.0F, 0.4F).addBox(-10.0F, 6.0F, -0.2F, 3.0F, 8.0F, 0.4F)
				.addBox(7.0F, 6.0F, -0.2F, 3.0F, 8.0F, 0.4F), PartPose.offset(0.0F, -32.0F, -12.3F));
		// tassels at the roof corners
		for (int i = 0; i < 4; i++) {
			float x = i % 2 == 0 ? -13.0F : 13.0F;
			float z = i < 2 ? -13.0F : 13.0F;
			PartDefinition r = tandu.addOrReplaceChild("rumbai_" + i, p(RUMBAI, i * 7).addBox(-0.3F, 0.0F, -0.3F, 0.6F, 3.0F, 0.6F), PartPose.offset(x, -32.0F, z));
			r.addOrReplaceChild("ujung", p(RUMBAI, 40 + i * 4).addBox(-1.0F, 3.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.ZERO);
		}
		// two carrying poles along z (built along x, turned), brass caps
		for (int side = -1; side <= 1; side += 2) {
			PartDefinition g = tandu.addOrReplaceChild("galah_" + (side < 0 ? "k" : "n"), p(GALAH).addBox(-48.0F, -1.0F, -1.0F, 96.0F, 2.0F, 2.0F),
					PartPose.offsetAndRotation(side * 14.0F, -10.0F, 0.0F, 0.0F, Mth.HALF_PI, 0.0F));
			g.addOrReplaceChild("tutup", p(EMAS, 100).addBox(-49.5F, -1.4F, -1.4F, 2.0F, 2.8F, 2.8F).addBox(47.5F, -1.4F, -1.4F, 2.0F, 2.8F, 2.8F),
					PartPose.ZERO);
		}
		tandu.addOrReplaceChild("palang_galah", p(KAYU, 100).addBox(-15.0F, -11.0F, -11.0F, 30.0F, 2.0F, 2.0F).addBox(-15.0F, -11.0F, 9.0F, 30.0F, 2.0F, 2.0F),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, 128, B * 8);
	}

	/** The palanquin's model: sway, bob, curtains and tassels. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart tandu;
		private final ModelPart tiraiKiri;
		private final ModelPart tiraiKanan;
		private final ModelPart tiraiDepan;
		private final ModelPart[] rumbai = new ModelPart[4];

		public Model(ModelPart root) {
			super(root);
			this.tandu = root.getChild("tandu");
			this.tiraiKiri = tandu.getChild("tirai_k");
			this.tiraiKanan = tandu.getChild("tirai_n");
			this.tiraiDepan = tandu.getChild("tirai_depan");
			for (int i = 0; i < 4; i++) {
				rumbai[i] = tandu.getChild("rumbai_" + i);
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			// carried: the whole palanquin rides at the bearers' shoulders (lifted 14 px) and sways
			if (state.pikul) {
				tandu.y -= 14.0F + state.naik;
			}
			tandu.zRot = state.goyang;
			tiraiKiri.zRot = -state.goyang * 2.0F + state.tirai * 0.5F;
			tiraiKanan.zRot = -state.goyang * 2.0F - state.tirai * 0.5F;
			tiraiKiri.xRot = state.tirai * 0.3F;
			tiraiKanan.xRot = state.tirai * 0.3F;
			tiraiDepan.xRot = state.tirai * 0.4F;
			for (int i = 0; i < 4; i++) {
				rumbai[i].zRot = -state.goyang * 3.0F;
				rumbai[i].xRot = state.tirai * (i < 2 ? 1.0F : -1.0F);
			}
		}
	}
}
