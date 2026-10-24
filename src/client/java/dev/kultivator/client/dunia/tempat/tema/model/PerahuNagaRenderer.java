package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.air.PerahuNagaEntity;
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
 * Perahu naga ({@code kultivator:perahu_naga}, DESIGN 38.T.9.1): the 18 x 2 block racing dragon boat of the Kota Air. A
 * narrow scaled hull of twelve sections with painted gunwales; at the bow the dragon's head on a raised neck (skull,
 * snout with whiskers, a hinged lower jaw with teeth, two three-tined horns raked 22.5 degrees, a beard and glowing
 * eyes); at the stern a three-segment curled tail with a fin; the drum (12 px) on its stand amidships; and ten pairs of
 * paddlers drawn as light client impostors (seated torso, head with the team headband, two arms and a paddle each), no
 * entities. A steersman stands at the stern with the long sweep.
 * <p>
 * Racing ({@link PerahuNagaEntity#KEADAAN_LOMBA}): all twenty paddles stroke together in a sine whose rate follows the
 * speed {@code nilai}; the paddlers lean into each catch; the jaw opens on each drum beat (two per stroke cycle); the boat
 * heaves a little with each catch. Idle: paddles resting across the gunwales, the head bobs on the water. Four team
 * colours, textures {@code perahu_naga_merah.png}, {@code _hijau}, {@code _biru}, {@code _hitam} (128 x 640, bands of
 * 64) + one glow texture for the eyes ({@code perahu_naga_merah_pancar.png}).
 */
public class PerahuNagaRenderer extends TemaBendaRenderer<PerahuNagaEntity, PerahuNagaRenderer.State, PerahuNagaRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("perahu_naga"), "main");
	private static final Identifier GLOW = Kultivator.id("textures/entity/tempat/tema/perahu_naga_merah_pancar.png");

	static final int B = 64;
	static final int SISIK = 0;
	static final int LAMBUNG = 1;
	static final int KEPALA = 2;
	static final int TANDUK = 3;
	static final int KAYU = 4;
	static final int GENDERANG = 5;
	static final int MATA = 6;
	static final int BAJU = 7;
	static final int KULIT = 8;
	static final int RAMBUT = 9;

	static final int BARIS = 10;
	static final float HALUAN_Z = -144.0F;

	public static class State extends TemaBendaRenderer.State {
		public float kayuh;
		public float kuat;
		public float rahang;
		public float naik;
		public float oleng;
	}

	public PerahuNagaRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), new Identifier[] {tex("merah"), tex("hijau"), tex("biru"), tex("hitam")},
				new Identifier[] {GLOW}, 0.0F);
	}

	private static Identifier tex(String warna) {
		return Kultivator.id("textures/entity/tempat/tema/perahu_naga_" + warna + ".png");
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, PerahuNagaRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(PerahuNagaEntity entity) {
		return 9.5D;
	}

	@Override
	protected double batas() {
		return 160.0D;
	}

	@Override
	protected int tekstur(State state) {
		return Mth.clamp(state.varian, 0, 3);
	}

	@Override
	public void extractRenderState(PerahuNagaEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		boolean lomba = state.keadaan == PerahuNagaEntity.KEADAAN_LOMBA;
		float laju = Mth.clamp(state.nilai, 0.0F, 1.0F);
		float rate = 0.16F + 0.3F * laju;
		state.kayuh = lomba ? state.umur * rate : 0.0F;
		state.kuat = lomba ? 1.0F : 0.0F;
		// the jaw opens on each beat: two beats per stroke (catch and recovery)
		float beat = lomba ? Math.max(0.0F, Mth.sin(state.kayuh * 2.0F)) : 0.0F;
		state.rahang = beat * beat * rad(28.0F);
		float w = state.waktu;
		if (state.jauh) {
			state.naik = 0.0F;
			state.oleng = 0.0F;
		} else {
			state.naik = Mth.sin(w * 0.07F + state.fase) * 0.4F + (lomba ? Math.max(0.0F, Mth.sin(state.kayuh)) * 0.6F : 0.0F);
			state.oleng = Mth.sin(w * 0.05F + state.fase) * rad(1.0F);
		}
		state.pancar = 0.9F;
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
		// hull: twelve sections, narrowing toward both ends, scaled sides, painted gunwale caps
		for (int i = 0; i < 12; i++) {
			float z = -144.0F + i * 24.0F;
			float m = Math.abs(i - 5.5F) / 5.5F;
			float w = 24.0F - 8.0F * m * m;
			PartDefinition s = boat.addOrReplaceChild("seksi_" + i, CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, z));
			s.addOrReplaceChild("dasar", p(LAMBUNG, i * 11).addBox(-w / 2.0F + 1.0F, 1.0F, 0.0F, w - 2.0F, 3.0F, 24.0F), PartPose.ZERO);
			s.addOrReplaceChild("sisi", p(SISIK, i * 13).addBox(-w / 2.0F, -6.0F, 0.0F, 2.0F, 9.0F, 24.0F).addBox(w / 2.0F - 2.0F, -6.0F, 0.0F, 2.0F, 9.0F, 24.0F),
					PartPose.ZERO);
			s.addOrReplaceChild("bibir", p(TANDUK, i * 7).addBox(-w / 2.0F - 0.5F, -7.0F, 0.0F, 3.0F, 1.0F, 24.0F)
					.addBox(w / 2.0F - 2.5F, -7.0F, 0.0F, 3.0F, 1.0F, 24.0F), PartPose.ZERO);
		}
		// ---------------------------------------------------------------- the dragon's head on its raised neck
		PartDefinition leher = boat.addOrReplaceChild("leher", p(SISIK, 20).addBox(-8.0F, -24.0F, -8.0F, 16.0F, 24.0F, 16.0F),
				PartPose.offsetAndRotation(0.0F, -2.0F, HALUAN_Z, rad(-22.5F), 0.0F, 0.0F));
		leher.addOrReplaceChild("sirip_leher", p(TANDUK, 40).addBox(-1.0F, -26.0F, 6.0F, 2.0F, 22.0F, 6.0F), PartPose.ZERO);
		PartDefinition kepala = leher.addOrReplaceChild("kepala", p(KEPALA).addBox(-12.0F, -16.0F, -26.0F, 24.0F, 16.0F, 28.0F),
				PartPose.offsetAndRotation(0.0F, -22.0F, 0.0F, rad(22.5F), 0.0F, 0.0F));
		kepala.addOrReplaceChild("moncong", p(KEPALA, 60).addBox(-8.0F, -12.0F, -40.0F, 16.0F, 10.0F, 14.0F)
				.addBox(-6.0F, -14.0F, -36.0F, 12.0F, 2.0F, 10.0F), PartPose.ZERO);
		kepala.addOrReplaceChild("gigi_atas", p(TANDUK, 60).addBox(-7.5F, -2.0F, -40.0F, 1.5F, 2.0F, 12.0F).addBox(6.0F, -2.0F, -40.0F, 1.5F, 2.0F, 12.0F)
				.addBox(-4.0F, -2.0F, -40.0F, 1.0F, 3.0F, 1.0F).addBox(3.0F, -2.0F, -40.0F, 1.0F, 3.0F, 1.0F), PartPose.ZERO);
		kepala.addOrReplaceChild("mata", p(MATA).addBox(-12.6F, -13.0F, -18.0F, 1.0F, 4.0F, 6.0F).addBox(11.6F, -13.0F, -18.0F, 1.0F, 4.0F, 6.0F),
				PartPose.ZERO);
		kepala.addOrReplaceChild("alis", p(TANDUK, 80).addBox(-13.0F, -17.0F, -20.0F, 4.0F, 3.0F, 9.0F).addBox(9.0F, -17.0F, -20.0F, 4.0F, 3.0F, 9.0F),
				PartPose.ZERO);
		PartDefinition rahang = kepala.addOrReplaceChild("rahang", p(KEPALA, 100).addBox(-10.0F, 0.0F, -24.0F, 20.0F, 6.0F, 24.0F),
				PartPose.offset(0.0F, 0.0F, -12.0F));
		rahang.addOrReplaceChild("gigi_bawah", p(TANDUK, 100).addBox(-8.0F, -2.0F, -23.0F, 1.5F, 2.0F, 12.0F).addBox(6.5F, -2.0F, -23.0F, 1.5F, 2.0F, 12.0F),
				PartPose.ZERO);
		rahang.addOrReplaceChild("janggut", p(RAMBUT, 40).addBox(-2.0F, 6.0F, -22.0F, 4.0F, 8.0F, 1.0F).addBox(-5.0F, 6.0F, -16.0F, 3.0F, 6.0F, 1.0F)
				.addBox(2.0F, 6.0F, -16.0F, 3.0F, 6.0F, 1.0F), PartPose.ZERO);
		// whiskers: long thin barbels swept back from the snout
		for (int side = -1; side <= 1; side += 2) {
			kepala.addOrReplaceChild("kumis_" + (side < 0 ? "k" : "n"), p(RAMBUT, 60).addBox(0.0F, -0.5F, -0.5F, 22.0F, 1.0F, 1.0F),
					PartPose.offsetAndRotation(side * 8.0F, -6.0F, -38.0F, 0.0F, side < 0 ? rad(-135.0F) : rad(-45.0F), rad(10.0F)));
		}
		// horns: three segments each, raked back 22.5 degrees
		for (int side = -1; side <= 1; side += 2) {
			PartDefinition t = kepala.addOrReplaceChild("tanduk_" + (side < 0 ? "k" : "n"), p(TANDUK).addBox(-1.0F, -10.0F, -1.0F, 2.0F, 10.0F, 2.0F),
					PartPose.offsetAndRotation(side * 6.0F, -16.0F, -6.0F, rad(-22.5F), 0.0F, side * rad(12.0F)));
			PartDefinition t2 = t.addOrReplaceChild("dua", p(TANDUK, 10).addBox(-0.8F, -8.0F, -0.8F, 1.6F, 8.0F, 1.6F), PartPose.offsetAndRotation(0.0F, -10.0F,
					0.0F, rad(-22.5F), 0.0F, 0.0F));
			t2.addOrReplaceChild("cabang", p(TANDUK, 20).addBox(-0.6F, -5.0F, -0.6F, 1.2F, 5.0F, 1.2F), PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F,
					rad(45.0F), 0.0F, 0.0F));
			t2.addOrReplaceChild("tiga", p(TANDUK, 30).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 6.0F, 1.0F), PartPose.offsetAndRotation(0.0F, -8.0F, 0.0F,
					rad(-22.5F), 0.0F, 0.0F));
		}
		kepala.addOrReplaceChild("surai", p(RAMBUT).addBox(-6.0F, -18.0F, -4.0F, 12.0F, 4.0F, 10.0F).addBox(-4.0F, -15.0F, 2.0F, 8.0F, 10.0F, 3.0F),
				PartPose.ZERO);
		// ---------------------------------------------------------------- the curled tail at the stern
		PartDefinition e1 = boat.addOrReplaceChild("ekor", p(SISIK, 40).addBox(-8.0F, -24.0F, 0.0F, 16.0F, 24.0F, 32.0F), PartPose.offsetAndRotation(0.0F, 2.0F,
				144.0F, rad(-15.0F), 0.0F, 0.0F));
		PartDefinition e2 = e1.addOrReplaceChild("dua", p(SISIK, 60).addBox(-6.0F, -20.0F, 0.0F, 12.0F, 20.0F, 24.0F), PartPose.offsetAndRotation(0.0F,
				-6.0F, 28.0F, rad(-40.0F), 0.0F, 0.0F));
		PartDefinition e3 = e2.addOrReplaceChild("tiga", p(SISIK, 80).addBox(-4.0F, -16.0F, 0.0F, 8.0F, 16.0F, 16.0F), PartPose.offsetAndRotation(0.0F, -6.0F,
				20.0F, rad(-60.0F), 0.0F, 0.0F));
		e3.addOrReplaceChild("sirip", p(TANDUK, 50).addBox(-1.0F, -24.0F, 2.0F, 2.0F, 12.0F, 14.0F), PartPose.ZERO);
		// ---------------------------------------------------------------- drum on its stand amidships, the steersman's sweep
		PartDefinition drum = boat.addOrReplaceChild("genderang", p(KAYU, 80).addBox(-5.0F, -6.0F, -5.0F, 10.0F, 6.0F, 10.0F), PartPose.offset(0.0F, 0.0F,
				-6.0F));
		drum.addOrReplaceChild("badan", p(GENDERANG).addBox(-6.0F, -18.0F, -6.0F, 12.0F, 12.0F, 12.0F).addBox(-6.5F, -17.0F, -6.5F, 13.0F, 1.0F, 13.0F)
				.addBox(-6.5F, -8.0F, -6.5F, 13.0F, 1.0F, 13.0F), PartPose.ZERO);
		PartDefinition kemudi = boat.addOrReplaceChild("kemudi", CubeListBuilder.create(), PartPose.offset(0.0F, -7.0F, 130.0F));
		kemudi.addOrReplaceChild("galah", p(KAYU).addBox(-4.0F, -1.0F, -1.0F, 48.0F, 2.0F, 2.0F), PartPose.rotation(0.0F, -Mth.HALF_PI, 0.0F));
		pendayung(kemudi.addOrReplaceChild("juru", CubeListBuilder.create(), PartPose.offset(0.0F, -1.0F, -4.0F)), true);
		// ---------------------------------------------------------------- ten pairs of paddlers with paddles
		for (int r = 0; r < BARIS; r++) {
			float z = -108.0F + r * 22.0F;
			for (int side = -1; side <= 1; side += 2) {
				String id = r + (side < 0 ? "k" : "n");
				PartDefinition duduk = boat.addOrReplaceChild("dayung_" + id, CubeListBuilder.create(), PartPose.offset(side * 6.0F, -3.0F, z));
				PartDefinition badan = pendayung(duduk, false);
				// the paddle: held across the body, blade outboard; built along y, swung by the model
				PartDefinition dayung = badan.addOrReplaceChild("dayung", CubeListBuilder.create(), PartPose.offsetAndRotation(side * 5.0F, -8.0F, -2.0F, 0.0F,
						0.0F, side * rad(35.0F)));
				dayung.addOrReplaceChild("galah", p(KAYU, 20 + r * 3).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 24.0F, 1.0F), PartPose.ZERO);
				dayung.addOrReplaceChild("daun", p(KAYU, 60).addBox(-0.5F, 14.0F, -2.0F, 1.0F, 8.0F, 4.0F)
						.addBox(-0.6F, -7.0F, -1.0F, 1.2F, 1.0F, 2.0F), PartPose.ZERO);
			}
		}
		return LayerDefinition.create(mesh, 128, B * 10);
	}

	/** One light impostor paddler (seated): hips on the thwart, torso, head with headband, two arms. */
	private static PartDefinition pendayung(PartDefinition duduk, boolean berdiri) {
		duduk.addOrReplaceChild("pinggul", p(BAJU, 80).addBox(-3.0F, -2.0F, -2.0F, 6.0F, 3.0F, 4.0F).addBox(-3.0F, -1.0F, -6.0F, 6.0F, 2.0F, 4.0F),
				PartPose.ZERO);
		PartDefinition badan = duduk.addOrReplaceChild("badan", p(BAJU).addBox(-3.5F, -9.0F, -2.0F, 7.0F, 9.0F, 4.0F), PartPose.offset(0.0F, berdiri ? -10.0F
				: -2.0F, 0.0F));
		PartDefinition kepala = badan.addOrReplaceChild("kepala", p(KULIT).addBox(-2.5F, -5.0F, -2.5F, 5.0F, 5.0F, 5.0F), PartPose.offset(0.0F, -9.0F, 0.0F));
		kepala.addOrReplaceChild("ikat", p(RAMBUT, 20).addBox(-2.7F, -5.4F, -2.7F, 5.4F, 2.0F, 5.4F).addBox(-0.5F, -4.4F, 2.6F, 1.0F, 3.0F, 0.6F),
				PartPose.ZERO);
		for (int side = -1; side <= 1; side += 2) {
			badan.addOrReplaceChild("lengan_" + (side < 0 ? "k" : "n"), p(KULIT, 30).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F),
					PartPose.offsetAndRotation(side * 4.2F, -8.5F, 0.0F, rad(-60.0F), 0.0F, side * rad(-10.0F)));
		}
		if (berdiri) {
			duduk.addOrReplaceChild("kaki", p(BAJU, 100).addBox(-3.0F, -10.0F, -1.5F, 2.5F, 10.0F, 3.0F).addBox(0.5F, -10.0F, -1.5F, 2.5F, 10.0F, 3.0F),
					PartPose.ZERO);
		}
		return badan;
	}

	/** The dragon boat's model: the stroke, the paddlers' lean, the jaw and the swell. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart perahu;
		private final ModelPart rahang;
		private final ModelPart kepala;
		private final ModelPart[] badan = new ModelPart[BARIS * 2];
		private final ModelPart[] dayung = new ModelPart[BARIS * 2];

		public Model(ModelPart root) {
			super(root);
			this.perahu = root.getChild("perahu");
			this.kepala = perahu.getChild("leher").getChild("kepala");
			this.rahang = kepala.getChild("rahang");
			for (int r = 0; r < BARIS; r++) {
				for (int s = 0; s < 2; s++) {
					ModelPart d = perahu.getChild("dayung_" + r + (s == 0 ? "k" : "n"));
					badan[r * 2 + s] = d.getChild("badan");
					dayung[r * 2 + s] = badan[r * 2 + s].getChild("dayung");
				}
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			perahu.y -= state.naik;
			perahu.zRot = state.oleng;
			rahang.xRot = state.rahang;
			kepala.xRot += state.jauh ? 0.0F : Mth.sin(state.waktu * 0.06F + state.fase) * rad(2.0F);
			float k = state.kuat;
			float c = Mth.sin(state.kayuh);
			for (int i = 0; i < badan.length; i++) {
				float side = i % 2 == 0 ? -1.0F : 1.0F;
				// lean forward into the catch, back through the drive
				badan[i].xRot = k * (rad(18.0F) * c + rad(8.0F));
				// paddle: reach forward and dip at the catch, pull back and lift at the exit; resting across the gunwale
				dayung[i].xRot = k * (rad(40.0F) * c);
				dayung[i].zRot = side * (k > 0.0F ? rad(35.0F) - Math.max(0.0F, Mth.cos(state.kayuh)) * rad(14.0F) * k : rad(70.0F));
			}
		}
	}
}
