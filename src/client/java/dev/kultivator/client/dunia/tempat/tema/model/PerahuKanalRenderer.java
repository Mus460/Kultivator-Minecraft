package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.air.PerahuKanalEntity;
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
 * Perahu kanal ({@code kultivator:perahu_kanal}, DESIGN 38.T.9.1): the 7 x 2 block black-awning boat of the Kota Air
 * canals. A flat-bottomed hull of four middle sections with up-swept ends (two raked sections each, a carved bow
 * board and a transom), gunwales and two benches; a black woven barrel roof of three arched sections (the outer two
 * pitched 11.25 degrees) on six posts; the yuloh (stern scull, 64 px) on its pin; a bow lantern that glows. Variants:
 * {@code wisata} (red paper lanterns along the roof and cushions), {@code lapak} (four crates and baskets under a little
 * striped awning instead of the roof), {@code hitam} (Tikus Air's fast black sampan, no roof).
 * <p>
 * Moored it rocks; under way ({@link PerahuKanalEntity#KEADAAN_JALAN}, chase {@link PerahuKanalEntity#KEADAAN_KEJAR})
 * the yuloh sweeps +-25 degrees over 40 ticks (clip 79 {@code dayung_yuloh}; faster in a chase) and the bow lifts a
 * little with each stroke. The yaw comes from the spline (server). Textures {@code perahu_kanal.png},
 * {@code perahu_kanal_hitam.png} (128 x 512, bands of 64) + glow.
 */
public class PerahuKanalRenderer extends TemaBendaRenderer<PerahuKanalEntity, PerahuKanalRenderer.State, PerahuKanalRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("perahu_kanal"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/perahu_kanal.png");
	private static final Identifier TEX_HITAM = Kultivator.id("textures/entity/tempat/tema/perahu_kanal_hitam.png");
	private static final Identifier GLOW = Kultivator.id("textures/entity/tempat/tema/perahu_kanal_pancar.png");

	static final int B = 64;
	static final int LAMBUNG = 0;
	static final int ATAP = 1;
	static final int KAYU = 2;
	static final int CAT = 3;
	static final int LENTERA = 4;
	static final int KAIN = 5;
	static final int PETI = 6;
	static final int TALI = 7;

	public static class State extends TemaBendaRenderer.State {
		public float yuloh;
		public float naik;
		public float oleng;
		public float angguk;
		public boolean atap;
		public boolean wisata;
		public boolean lapak;
	}

	public PerahuKanalRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), new Identifier[] {TEX, TEX_HITAM}, new Identifier[] {GLOW, GLOW}, 0.0F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, PerahuKanalRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(PerahuKanalEntity entity) {
		return 3.5D;
	}

	@Override
	protected int tekstur(State state) {
		return state.varian == PerahuKanalEntity.VARIAN_HITAM ? 1 : 0;
	}

	@Override
	public void extractRenderState(PerahuKanalEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		boolean jalan = state.keadaan == PerahuKanalEntity.KEADAAN_JALAN || state.keadaan == PerahuKanalEntity.KEADAAN_KEJAR;
		boolean kejar = state.keadaan == PerahuKanalEntity.KEADAAN_KEJAR;
		float period = kejar ? 22.0F : 40.0F;
		float ph = jalan ? (state.umur % period) / period * Mth.TWO_PI : 0.0F;
		state.yuloh = jalan ? Mth.sin(ph) * rad(25.0F) : rad(-8.0F);
		float w = state.waktu;
		if (state.jauh) {
			state.naik = 0.0F;
			state.oleng = 0.0F;
			state.angguk = 0.0F;
		} else {
			state.naik = Mth.sin(w * 0.08F + state.fase) * 0.5F;
			state.oleng = Mth.sin(w * 0.061F + state.fase * 1.3F) * rad(jalan ? 1.0F : 2.0F) + (jalan ? Mth.sin(ph) * rad(1.2F) : 0.0F);
			state.angguk = jalan ? -rad(1.5F) - Math.abs(Mth.sin(ph)) * rad(1.5F) * (kejar ? 1.6F : 1.0F) : Mth.sin(w * 0.09F) * rad(0.8F);
		}
		state.atap = state.varian == PerahuKanalEntity.VARIAN_BIASA || state.varian == PerahuKanalEntity.VARIAN_WISATA;
		state.wisata = state.varian == PerahuKanalEntity.VARIAN_WISATA;
		state.lapak = state.varian == PerahuKanalEntity.VARIAN_LAPAK;
		state.pancar = 0.8F;
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
		PartDefinition boat = root.addOrReplaceChild("perahu", CubeListBuilder.create(), PartPose.offset(0.0F, 21.0F, 0.0F));
		// four middle sections: floor, two sides, gunwale caps, a painted strake
		for (int i = 0; i < 4; i++) {
			float z = -32.0F + i * 16.0F;
			int u = i * 23;
			PartDefinition s = boat.addOrReplaceChild("tengah_" + i, CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, z));
			s.addOrReplaceChild("lantai", p(LAMBUNG, u).addBox(-13.0F, 1.0F, 0.0F, 26.0F, 2.0F, 16.0F), PartPose.ZERO);
			s.addOrReplaceChild("sisi", p(LAMBUNG, u + 40).addBox(-15.0F, -7.0F, 0.0F, 2.0F, 10.0F, 16.0F).addBox(13.0F, -7.0F, 0.0F, 2.0F, 10.0F, 16.0F),
					PartPose.ZERO);
			s.addOrReplaceChild("bibir", p(KAYU, u).addBox(-15.5F, -8.0F, 0.0F, 3.0F, 1.0F, 16.0F).addBox(12.5F, -8.0F, 0.0F, 3.0F, 1.0F, 16.0F),
					PartPose.ZERO);
			s.addOrReplaceChild("pita", p(CAT, u).addBox(-15.3F, -5.0F, 0.0F, 0.6F, 2.0F, 16.0F).addBox(14.7F, -5.0F, 0.0F, 0.6F, 2.0F, 16.0F), PartPose.ZERO);
		}
		ujung(boat, "haluan", -1.0F);
		ujung(boat, "buritan", 1.0F);
		boat.addOrReplaceChild("bangku", p(KAYU, 60).addBox(-13.0F, -2.0F, -28.0F, 26.0F, 2.0F, 4.0F).addBox(-13.0F, -2.0F, 24.0F, 26.0F, 2.0F, 4.0F)
				.addBox(-13.0F, -1.0F, -2.0F, 26.0F, 1.0F, 4.0F), PartPose.ZERO);
		// barrel roof of three arched sections on six posts
		PartDefinition atap = boat.addOrReplaceChild("atap", CubeListBuilder.create(), PartPose.offset(0.0F, -22.0F, 0.0F));
		for (int i = 0; i < 3; i++) {
			float z = -12.0F + (i - 1) * 24.0F;
			float pitch = (i - 1) * rad(11.25F);
			PartDefinition seg = atap.addOrReplaceChild("seg_" + i, CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, Math.abs(i - 1) * 1.6F, z + 12.0F,
					pitch, 0.0F, 0.0F));
			seg.addOrReplaceChild("puncak", p(ATAP, i * 30).addBox(-8.0F, -1.0F, -12.0F, 16.0F, 2.0F, 24.0F), PartPose.ZERO);
			seg.addOrReplaceChild("kiri", p(ATAP, 40 + i * 20).addBox(-11.0F, 0.0F, -12.0F, 11.0F, 2.0F, 24.0F), PartPose.offsetAndRotation(-7.5F, 0.0F, 0.0F,
					0.0F, 0.0F, -0.62F));
			seg.addOrReplaceChild("kanan", p(ATAP, 80 + i * 20).addBox(0.0F, 0.0F, -12.0F, 11.0F, 2.0F, 24.0F), PartPose.offsetAndRotation(7.5F, 0.0F, 0.0F,
					0.0F, 0.0F, 0.62F));
			seg.addOrReplaceChild("rusuk", p(KAYU, 70 + i * 9).addBox(-8.5F, -1.6F, 11.0F, 17.0F, 0.6F, 1.0F), PartPose.ZERO);
		}
		for (int i = 0; i < 6; i++) {
			float x = i % 2 == 0 ? -14.0F : 13.0F;
			float z = -34.0F + (i / 2) * 34.0F;
			atap.addOrReplaceChild("tiang_" + i, p(KAYU, 100 + i * 4).addBox(0.0F, 0.0F, 0.0F, 1.0F, 15.0F, 1.0F), PartPose.offset(x, 0.0F, z));
		}
		// tour lanterns: four red paper lanterns hanging from the roof edge
		PartDefinition wisata = boat.addOrReplaceChild("wisata", CubeListBuilder.create(), PartPose.offset(0.0F, -16.0F, 0.0F));
		for (int i = 0; i < 4; i++) {
			float x = i % 2 == 0 ? -16.5F : 16.5F;
			float z = i < 2 ? -24.0F : 24.0F;
			PartDefinition l = wisata.addOrReplaceChild("lampion_" + i, p(TALI, i * 7).addBox(-0.3F, 0.0F, -0.3F, 0.6F, 3.0F, 0.6F), PartPose.offset(x, 0.0F, z));
			l.addOrReplaceChild("badan", p(LENTERA, 40 + i * 9).addBox(-2.0F, 3.0F, -2.0F, 4.0F, 5.0F, 4.0F).addBox(-1.5F, 2.4F, -1.5F, 3.0F, 0.6F, 3.0F)
					.addBox(-1.5F, 8.0F, -1.5F, 3.0F, 0.6F, 3.0F).addBox(-0.3F, 8.6F, -0.3F, 0.6F, 2.0F, 0.6F), PartPose.ZERO);
		}
		wisata.addOrReplaceChild("bantal", p(KAIN).addBox(-12.0F, 13.0F, -28.5F, 24.0F, 1.5F, 5.0F).addBox(-12.0F, 13.0F, 23.5F, 24.0F, 1.5F, 5.0F),
				PartPose.ZERO);
		// market-boat load: striped awning on four poles, two crates, two baskets of greens and a sack
		PartDefinition lapak = boat.addOrReplaceChild("lapak", CubeListBuilder.create(), PartPose.ZERO);
		lapak.addOrReplaceChild("tenda", p(KAIN, 30).addBox(-12.0F, -1.0F, -14.0F, 24.0F, 1.0F, 28.0F), PartPose.offsetAndRotation(0.0F, -21.0F, 4.0F, 0.0F,
				0.0F, 0.06F));
		lapak.addOrReplaceChild("galah", p(KAYU, 90).addBox(-11.5F, -20.0F, -13.5F, 1.0F, 20.0F, 1.0F).addBox(10.5F, -20.0F, -13.5F, 1.0F, 20.0F, 1.0F)
				.addBox(-11.5F, -20.0F, 20.5F, 1.0F, 20.0F, 1.0F).addBox(10.5F, -20.0F, 20.5F, 1.0F, 20.0F, 1.0F), PartPose.ZERO);
		lapak.addOrReplaceChild("peti", p(PETI).addBox(-11.0F, -8.0F, -10.0F, 9.0F, 8.0F, 9.0F).addBox(-1.0F, -7.0F, -9.0F, 8.0F, 7.0F, 8.0F),
				PartPose.ZERO);
		lapak.addOrReplaceChild("keranjang", p(ATAP, 100).addBox(2.0F, -6.0F, 4.0F, 8.0F, 6.0F, 8.0F).addBox(-10.0F, -6.0F, 8.0F, 8.0F, 6.0F, 8.0F),
				PartPose.ZERO);
		lapak.addOrReplaceChild("sayur", p(PETI, 60).addBox(2.5F, -7.5F, 4.5F, 7.0F, 1.5F, 7.0F).addBox(-9.5F, -7.5F, 8.5F, 7.0F, 1.5F, 7.0F)
				.addBox(5.0F, -5.0F, 16.0F, 6.0F, 5.0F, 7.0F), PartPose.ZERO);
		// bow lantern on a short post
		PartDefinition lentera = boat.addOrReplaceChild("lentera", p(KAYU, 110).addBox(-0.5F, -10.0F, -0.5F, 1.0F, 10.0F, 1.0F),
				PartPose.offset(0.0F, -9.0F, -52.0F));
		lentera.addOrReplaceChild("kap", p(LENTERA).addBox(-2.0F, -16.0F, -2.0F, 4.0F, 6.0F, 4.0F).addBox(-2.5F, -16.8F, -2.5F, 5.0F, 0.8F, 5.0F)
				.addBox(-2.5F, -10.4F, -2.5F, 5.0F, 0.8F, 5.0F), PartPose.ZERO);
		// the yuloh on its stern pin: built along x (handle inboard), turned aft; the blade trails in the water
		PartDefinition pin = boat.addOrReplaceChild("yuloh", CubeListBuilder.create(), PartPose.offset(0.0F, -9.0F, 50.0F));
		pin.addOrReplaceChild("pasak", p(KAYU, 120).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.ZERO);
		PartDefinition arah = pin.addOrReplaceChild("arah", CubeListBuilder.create(), PartPose.rotation(0.0F, -Mth.HALF_PI, 0.0F));
		PartDefinition galah = arah.addOrReplaceChild("galah", p(KAYU, 30).addBox(-20.0F, -1.0F, -1.0F, 64.0F, 2.0F, 2.0F),
				PartPose.rotation(0.0F, 0.0F, 0.32F));
		galah.addOrReplaceChild("daun", p(KAYU, 50).addBox(34.0F, -0.6F, -3.0F, 12.0F, 1.2F, 6.0F), PartPose.ZERO);
		galah.addOrReplaceChild("tali", p(TALI).addBox(-18.0F, -0.4F, -0.4F, 0.8F, 10.0F, 0.8F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 128, B * 8);
	}

	/** One end: two raked sections, a carved board and, at the stern, a transom. {@code dir} -1 = bow (-z). */
	private static void ujung(PartDefinition boat, String id, float dir) {
		float z0 = dir < 0 ? -32.0F : 32.0F;
		float tilt = dir < 0 ? rad(10.0F) : -rad(10.0F);
		PartDefinition s1 = boat.addOrReplaceChild(id, CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, z0, -tilt, 0.0F, 0.0F));
		float a = dir < 0 ? -12.0F : 0.0F;
		s1.addOrReplaceChild("lantai", p(LAMBUNG, 10).addBox(-12.0F, 1.0F, a, 24.0F, 2.0F, 12.0F), PartPose.ZERO);
		s1.addOrReplaceChild("sisi", p(LAMBUNG, 50).addBox(-14.0F, -7.0F, a, 2.0F, 10.0F, 12.0F).addBox(12.0F, -7.0F, a, 2.0F, 10.0F, 12.0F), PartPose.ZERO);
		s1.addOrReplaceChild("bibir", p(KAYU, 40).addBox(-14.5F, -8.0F, a, 3.0F, 1.0F, 12.0F).addBox(11.5F, -8.0F, a, 3.0F, 1.0F, 12.0F), PartPose.ZERO);
		PartDefinition s2 = s1.addOrReplaceChild("dua", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, dir * 12.0F, -tilt * 1.6F, 0.0F, 0.0F));
		float b = dir < 0 ? -10.0F : 0.0F;
		s2.addOrReplaceChild("lantai", p(LAMBUNG, 70).addBox(-9.0F, 1.0F, b, 18.0F, 2.0F, 10.0F), PartPose.ZERO);
		s2.addOrReplaceChild("sisi", p(LAMBUNG, 90).addBox(-11.0F, -7.0F, b, 2.0F, 10.0F, 10.0F).addBox(9.0F, -7.0F, b, 2.0F, 10.0F, 10.0F), PartPose.ZERO);
		s2.addOrReplaceChild("bibir", p(KAYU, 80).addBox(-11.5F, -8.0F, b, 3.0F, 1.0F, 10.0F).addBox(8.5F, -8.0F, b, 3.0F, 1.0F, 10.0F), PartPose.ZERO);
		float c = dir < 0 ? -11.0F : 9.0F;
		s2.addOrReplaceChild("papan", p(CAT, 60).addBox(-9.0F, -9.0F, c, 18.0F, 12.0F, 2.0F).addBox(-6.0F, -11.0F, c, 12.0F, 2.0F, 2.0F), PartPose.ZERO);
	}

	/** The canal boat's model: swell, the bow lift, the yuloh's sweep, the variant parts. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart perahu;
		private final ModelPart atap;
		private final ModelPart wisata;
		private final ModelPart lapak;
		private final ModelPart yuloh;
		private final ModelPart[] lampion = new ModelPart[4];

		public Model(ModelPart root) {
			super(root);
			this.perahu = root.getChild("perahu");
			this.atap = perahu.getChild("atap");
			this.wisata = perahu.getChild("wisata");
			this.lapak = perahu.getChild("lapak");
			this.yuloh = perahu.getChild("yuloh");
			for (int i = 0; i < 4; i++) {
				lampion[i] = wisata.getChild("lampion_" + i);
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			perahu.y -= state.naik;
			perahu.zRot = state.oleng;
			perahu.xRot = state.angguk;
			atap.visible = state.atap;
			wisata.visible = state.wisata;
			lapak.visible = state.lapak;
			yuloh.yRot = state.yuloh;
			for (int i = 0; i < 4; i++) {
				lampion[i].xRot = state.jauh ? 0.0F : Mth.sin(state.waktu * 0.11F + i) * 0.12F - state.angguk;
				lampion[i].zRot = -state.oleng;
			}
		}
	}
}
