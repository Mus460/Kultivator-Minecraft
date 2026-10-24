package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.pelabuhan.PerahuNelayanEntity;
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
 * Perahu nelayan ({@code kultivator:perahu_nelayan}, DESIGN 38.T.9.1): a 6 x 2 block fishing sampan. An open hull (floor,
 * two side strakes with a painted band, two benches) whose ends sweep up in three angled segments each to a carved
 * post, an arched woven roof on four posts over the middle, a heap of net and a fish basket, a stern lantern on a
 * bamboo pole, and a pair of oars in oarlocks. It bobs on the swell; rowing ({@link PerahuNelayanEntity#KEADAAN_DAYUNG})
 * sweeps the oars at a rate set by {@code nilai}; racing ({@link PerahuNelayanEntity#KEADAAN_LOMBA}) rows fast with the
 * bow lifted. Textures {@code perahu_nelayan.png} / {@code perahu_nelayan_lomba.png} (red race ribbons), 128 x 448,
 * bands of 64.
 */
public class PerahuNelayanRenderer extends TemaBendaRenderer<PerahuNelayanEntity, PerahuNelayanRenderer.State, PerahuNelayanRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("perahu_nelayan"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/perahu_nelayan.png");
	private static final Identifier TEX_LOMBA = Kultivator.id("textures/entity/tempat/tema/perahu_nelayan_lomba.png");
	private static final Identifier GLOW = Kultivator.id("textures/entity/tempat/tema/perahu_nelayan_pancar.png");

	static final int B = 64;
	static final int LAMBUNG = 0;
	static final int ANYAMAN = 1;
	static final int KAYU = 2;
	static final int TALI = 3;
	static final int JALA = 4;
	static final int CAT = 5;
	static final int LENTERA = 6;

	public static class State extends TemaBendaRenderer.State {
		public float kayuh;
		public float kuat;
		public float naik;
		public float oleng;
		public float angguk;
	}

	public PerahuNelayanRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), new Identifier[] {TEX, TEX_LOMBA}, new Identifier[] {GLOW, GLOW}, 0.0F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, PerahuNelayanRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(PerahuNelayanEntity entity) {
		return 3.0D;
	}

	@Override
	protected int tekstur(State state) {
		return state.varian == PerahuNelayanEntity.VARIAN_LOMBA ? 1 : 0;
	}

	@Override
	public void extractRenderState(PerahuNelayanEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		float w = state.waktu;
		boolean lomba = state.keadaan == PerahuNelayanEntity.KEADAAN_LOMBA;
		boolean dayung = lomba || state.keadaan == PerahuNelayanEntity.KEADAAN_DAYUNG;
		float rate = lomba ? 0.48F : 0.16F + 0.26F * Mth.clamp(state.nilai, 0.0F, 1.0F);
		state.kayuh = dayung ? w * rate + state.fase : 0.0F;
		state.kuat = dayung ? 1.0F : 0.0F;
		if (state.jauh) {
			state.naik = 0.0F;
			state.oleng = 0.0F;
			state.angguk = lomba ? rad(-3.0F) : 0.0F;
		} else {
			state.naik = Mth.sin(w * 0.09F + state.fase) * 0.9F + (dayung ? Mth.sin(state.kayuh * 2.0F) * 0.3F : 0.0F);
			state.oleng = Mth.sin(w * 0.067F + state.fase * 1.7F) * rad(2.5F);
			state.angguk = Mth.sin(w * 0.11F + state.fase) * rad(1.5F) + (lomba ? rad(-3.0F) : 0.0F);
		}
		state.pancar = 0.75F;
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
		PartDefinition boat = root.addOrReplaceChild("perahu", CubeListBuilder.create(), PartPose.offset(0.0F, 20.0F, 0.0F));
		// open middle: floor, side strakes, painted band, gunwale caps, two benches, ribs
		boat.addOrReplaceChild("lantai", p(LAMBUNG).addBox(-14.0F, 1.0F, -24.0F, 28.0F, 2.0F, 48.0F), PartPose.ZERO);
		boat.addOrReplaceChild("lunas", p(LAMBUNG, 40).addBox(-3.0F, 3.0F, -26.0F, 6.0F, 2.0F, 52.0F), PartPose.ZERO);
		boat.addOrReplaceChild("sisi_kiri", p(LAMBUNG, 10).addBox(-16.0F, -7.0F, -24.0F, 2.0F, 10.0F, 48.0F), PartPose.ZERO);
		boat.addOrReplaceChild("sisi_kanan", p(LAMBUNG, 20).addBox(14.0F, -7.0F, -24.0F, 2.0F, 10.0F, 48.0F), PartPose.ZERO);
		boat.addOrReplaceChild("pita", p(CAT).addBox(-16.3F, -5.0F, -24.0F, 0.6F, 2.0F, 48.0F).addBox(15.7F, -5.0F, -24.0F, 0.6F, 2.0F, 48.0F),
				PartPose.ZERO);
		boat.addOrReplaceChild("bibir", p(KAYU).addBox(-16.5F, -8.0F, -24.0F, 3.0F, 1.0F, 48.0F).addBox(13.5F, -8.0F, -24.0F, 3.0F, 1.0F, 48.0F),
				PartPose.ZERO);
		for (int i = 0; i < 4; i++) {
			boat.addOrReplaceChild("gading_" + i, p(KAYU, i * 9).addBox(-14.0F, -6.0F, -1.0F, 28.0F, 1.0F, 2.0F), PartPose.offset(0.0F, 7.0F, -18.0F + i * 12.0F));
		}
		boat.addOrReplaceChild("bangku", p(KAYU, 30).addBox(-14.0F, -2.0F, -16.0F, 28.0F, 2.0F, 4.0F).addBox(-14.0F, -2.0F, 14.0F, 28.0F, 2.0F, 4.0F),
				PartPose.ZERO);
		// the up-swept ends: three angled segments each, then a carved post
		ujung(boat, "buritan", 1.0F);
		ujung(boat, "haluan", -1.0F);
		// arched woven roof on four posts
		PartDefinition atap = boat.addOrReplaceChild("atap", CubeListBuilder.create(), PartPose.offset(0.0F, -21.0F, -2.0F));
		atap.addOrReplaceChild("puncak", p(ANYAMAN).addBox(-7.0F, -1.0F, -16.0F, 14.0F, 2.0F, 32.0F), PartPose.ZERO);
		atap.addOrReplaceChild("kiri", p(ANYAMAN, 30).addBox(-10.0F, 0.0F, -16.0F, 10.0F, 2.0F, 32.0F), PartPose.offsetAndRotation(-6.5F, 0.0F, 0.0F, 0.0F, 0.0F,
				-0.6F));
		atap.addOrReplaceChild("kanan", p(ANYAMAN, 60).addBox(0.0F, 0.0F, -16.0F, 10.0F, 2.0F, 32.0F), PartPose.offsetAndRotation(6.5F, 0.0F, 0.0F, 0.0F, 0.0F,
				0.6F));
		atap.addOrReplaceChild("rusuk", p(KAYU, 50).addBox(-7.5F, -1.6F, -16.5F, 15.0F, 0.6F, 1.0F).addBox(-7.5F, -1.6F, 15.5F, 15.0F, 0.6F, 1.0F),
				PartPose.ZERO);
		for (int i = 0; i < 4; i++) {
			float x = i % 2 == 0 ? -14.0F : 13.0F;
			float z = i < 2 ? -15.0F : 14.0F;
			atap.addOrReplaceChild("tiang_" + i, p(KAYU, 70 + i * 5).addBox(0.0F, 0.0F, 0.0F, 1.0F, 13.0F, 1.0F), PartPose.offset(x, 0.0F, z));
		}
		// net heap, fish basket, coiled rope
		boat.addOrReplaceChild("jala", p(JALA).addBox(-12.0F, -3.0F, 4.0F, 10.0F, 4.0F, 9.0F).addBox(-10.0F, -5.0F, 6.0F, 6.0F, 2.0F, 5.0F),
				PartPose.ZERO);
		boat.addOrReplaceChild("keranjang", p(ANYAMAN, 90).addBox(4.0F, -5.0F, 5.0F, 7.0F, 6.0F, 7.0F), PartPose.ZERO);
		boat.addOrReplaceChild("tali", p(TALI).addBox(5.0F, -1.5F, -12.0F, 6.0F, 2.0F, 6.0F), PartPose.ZERO);
		// stern lantern on a bamboo pole
		PartDefinition lentera = boat.addOrReplaceChild("lentera", p(KAYU, 100).addBox(0.0F, -18.0F, 0.0F, 1.0F, 18.0F, 1.0F)
				.addBox(0.0F, -18.0F, 1.0F, 1.0F, 1.0F, 4.0F), PartPose.offset(-1.0F, -7.0F, 36.0F));
		lentera.addOrReplaceChild("kap", p(LENTERA).addBox(-1.5F, 0.0F, 3.5F, 4.0F, 5.0F, 4.0F).addBox(-1.0F, -0.8F, 4.0F, 3.0F, 0.8F, 3.0F),
				PartPose.offset(0.0F, -17.0F, 0.0F));
		// oars in their locks: built along x (handle inside, blade outside), swept about the lock
		for (int side = -1; side <= 1; side += 2) {
			PartDefinition kunci = boat.addOrReplaceChild(side < 0 ? "dayung_kiri" : "dayung_kanan", CubeListBuilder.create(),
					PartPose.offset(side * 16.0F, -8.0F, 2.0F));
			PartDefinition dayung = kunci.addOrReplaceChild("batang", side < 0
					? p(KAYU, 110).addBox(-40.0F, -1.0F, -1.0F, 48.0F, 2.0F, 2.0F)
					: p(KAYU, 110).addBox(-8.0F, -1.0F, -1.0F, 48.0F, 2.0F, 2.0F), PartPose.ZERO);
			dayung.addOrReplaceChild("daun", p(KAYU, 120).addBox(side < 0 ? -42.0F : 32.0F, -0.5F, -3.0F, 10.0F, 1.0F, 6.0F), PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 128, B * 7);
	}

	/** One up-swept end; {@code dir} = +1 for the stern (+z), -1 for the bow. */
	private static void ujung(PartDefinition boat, String id, float dir) {
		float tilt = dir > 0 ? 1.0F : -1.0F;
		PartDefinition s1 = boat.addOrReplaceChild(id, p(LAMBUNG, 60).addBox(-14.0F, -7.0F, dir > 0 ? 0.0F : -10.0F, 28.0F, 10.0F, 10.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, dir * 24.0F, tilt * rad(12.0F), 0.0F, 0.0F));
		PartDefinition s2 = s1.addOrReplaceChild("dua", p(LAMBUNG, 80).addBox(-11.0F, -7.0F, dir > 0 ? 0.0F : -9.0F, 22.0F, 9.0F, 9.0F),
				PartPose.offsetAndRotation(0.0F, -0.5F, dir * 9.5F, tilt * rad(14.0F), 0.0F, 0.0F));
		PartDefinition s3 = s2.addOrReplaceChild("tiga", p(LAMBUNG, 100).addBox(-7.0F, -7.0F, dir > 0 ? 0.0F : -8.0F, 14.0F, 7.0F, 8.0F),
				PartPose.offsetAndRotation(0.0F, -0.5F, dir * 8.5F, tilt * rad(18.0F), 0.0F, 0.0F));
		s3.addOrReplaceChild("tiang", p(CAT, 40).addBox(-1.5F, -11.0F, dir > 0 ? 5.0F : -7.0F, 3.0F, 6.0F, 2.0F)
				.addBox(-2.0F, -12.0F, dir > 0 ? 4.5F : -7.5F, 4.0F, 1.0F, 3.0F), PartPose.ZERO);
		s1.addOrReplaceChild("bibir", p(KAYU, 20).addBox(-14.5F, -8.0F, dir > 0 ? 0.0F : -10.0F, 29.0F, 1.0F, 10.0F), PartPose.ZERO);
	}

	/** The sampan's model: swell, rowing oars. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart perahu;
		private final ModelPart kiri;
		private final ModelPart kanan;

		public Model(ModelPart root) {
			super(root);
			this.perahu = root.getChild("perahu");
			this.kiri = perahu.getChild("dayung_kiri");
			this.kanan = perahu.getChild("dayung_kanan");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			perahu.y -= state.naik;
			perahu.zRot = state.oleng;
			perahu.xRot = state.angguk;
			float c = state.kayuh;
			float k = state.kuat;
			// rest: oars shipped along the gunwale; rowing: sweep +-32 degrees, blade dips on the drive stroke
			float sweep = k * Mth.sin(c) * rad(32.0F);
			float dip = k * (Mth.cos(c) > 0.0F ? rad(16.0F) : rad(-6.0F)) + (1.0F - k) * rad(-4.0F);
			float rest = (1.0F - k) * rad(70.0F);
			kiri.yRot = sweep + rest;
			kiri.zRot = -dip;
			kanan.yRot = -sweep - rest;
			kanan.zRot = dip;
		}
	}
}
