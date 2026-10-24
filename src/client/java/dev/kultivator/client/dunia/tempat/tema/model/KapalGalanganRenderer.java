package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.pelabuhan.KapalGalanganEntity;
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
 * Kapal di landasan galangan ({@code kultivator:kapal_galangan}, DESIGN 38.T.9.3 / 38.T.3.4): the player's ordered hull
 * on the slipway of the Galangan, built in five stages ({@code nilai} 0..4). Always there: the stone slip with its
 * greased ways, eleven keel blocks with wedges, two rows of raked shores and a bamboo scaffold of poles, ledger boards
 * and ladders down both sides. Stage 0 the keel (16 x 8 x 352) with stem and stern posts; 1 adds twelve curved frames
 * (three timbers each: floor and two futtocks) and a ribband; 2 the lower planking (bottom and bilge strakes, the half
 * hull); 3 the full hull: upper strakes, wale, deck, bulwarks, a square bow transom, the stern castle and the mast
 * steps; 4 the three masts with their sails furled on the boom, lashed with ropes, and the masthead streamers. The parts
 * of a new stage grow in over 10 ticks ({@link KapalGalanganEntity#KEADAAN_TAHAP_BARU}: scale 0 to 1, easeOutBack).
 * Two looks: the white-blue Perahu Awan and the red lacquered Kapal Sekte; textures {@code kapal_galangan_awan.png} and
 * {@code kapal_galangan_sekte.png} (256 x 768, bands of 64).
 */
public class KapalGalanganRenderer extends TemaBendaRenderer<KapalGalanganEntity, KapalGalanganRenderer.State, KapalGalanganRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("kapal_galangan"), "main");
	private static final Identifier TEX_AWAN = Kultivator.id("textures/entity/tempat/tema/kapal_galangan_awan.png");
	private static final Identifier TEX_SEKTE = Kultivator.id("textures/entity/tempat/tema/kapal_galangan_sekte.png");

	static final int B = 64;
	static final int KAYU = 0;
	static final int LAMBUNG = 1;
	static final int GADING = 2;
	static final int BAMBU = 3;
	static final int PAPAN = 4;
	static final int BATU = 5;
	static final int TALI = 6;
	static final int LAYAR = 7;
	static final int TIANG = 8;
	static final int CAT = 9;
	static final int BESI = 10;
	/** Decal band: bow eyes (north face of the bow transom, u 8..70) and the stern panel (south face, u 104..196). */
	static final int HIASAN = 11;

	static final float[] LEBAR = {0.56F, 0.80F, 0.94F, 1.0F, 1.0F, 0.97F, 0.90F, 0.82F};
	static final float[] SHEER = {-12.0F, -5.0F, -1.0F, 0.0F, 0.0F, -1.0F, -6.0F, -14.0F};
	static final float STATION = 44.0F;
	static final int GADING_N = 12;
	private static final int TAHAP = 5;

	public static class State extends TemaBendaRenderer.State {
		public int tahap;
		/** Scale of the newest stage's parts 0..1 (1 when not growing). */
		public float tumbuh = 1.0F;
		public float kibar;
	}

	public KapalGalanganRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), new Identifier[] {TEX_AWAN, TEX_SEKTE}, null, 0.0F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, KapalGalanganRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(KapalGalanganEntity entity) {
		return 12.5D;
	}

	@Override
	protected int tekstur(State state) {
		return state.varian == KapalGalanganEntity.VARIAN_KAPAL_SEKTE ? 1 : 0;
	}

	@Override
	public void extractRenderState(KapalGalanganEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.tahap = Mth.clamp(Math.round(state.nilai), 0, TAHAP - 1);
		if (state.keadaan == KapalGalanganEntity.KEADAAN_TAHAP_BARU && state.umur < 10.0F) {
			state.tumbuh = Math.max(0.02F, lewat(maju(state.umur, 0.0F, 10.0F)));
		} else {
			state.tumbuh = 1.0F;
		}
		state.kibar = state.jauh ? 0.0F : state.waktu * 0.3F + state.fase;
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
		// ---------------------------------------------------------------- the slip (always): stone ways, keel blocks, shores, scaffold
		PartDefinition landasan = root.addOrReplaceChild("landasan", CubeListBuilder.create(), PartPose.ZERO);
		for (int i = 0; i < 8; i++) {
			landasan.addOrReplaceChild("batu_" + i, p(BATU, i * 29).addBox(-40.0F, 20.0F, 0.0F, 80.0F, 4.0F, 48.0F), PartPose.offset(0.0F, 0.0F,
					-192.0F + i * 48.0F));
		}
		landasan.addOrReplaceChild("rel", p(KAYU, 40).addBox(-26.0F, 18.0F, -192.0F, 6.0F, 2.0F, 48.0F).addBox(20.0F, 18.0F, -192.0F, 6.0F, 2.0F, 48.0F),
				PartPose.ZERO);
		for (int i = 1; i < 8; i++) {
			landasan.addOrReplaceChild("rel_" + i, p(KAYU, 40 + i * 13).addBox(-26.0F, 18.0F, 0.0F, 6.0F, 2.0F, 48.0F)
					.addBox(20.0F, 18.0F, 0.0F, 6.0F, 2.0F, 48.0F), PartPose.offset(0.0F, 0.0F, -192.0F + i * 48.0F));
		}
		for (int i = 0; i < 11; i++) {
			float z = -160.0F + i * 32.0F;
			PartDefinition blok = landasan.addOrReplaceChild("blok_" + i, p(KAYU, i * 17).addBox(-10.0F, 6.0F, -5.0F, 20.0F, 6.0F, 10.0F)
					.addBox(-12.0F, 12.0F, -6.0F, 24.0F, 6.0F, 12.0F), PartPose.offset(0.0F, 0.0F, z));
			blok.addOrReplaceChild("baji", p(GADING, i * 7).addBox(-6.0F, 0.0F, -4.0F, 12.0F, 2.0F, 8.0F), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F,
					0.0F, 0.0F, i % 2 == 0 ? 0.06F : -0.06F));
		}
		// raked shores from the slip to the bilge, both sides
		for (int side = -1; side <= 1; side += 2) {
			for (int i = 0; i < 6; i++) {
				float z = -140.0F + i * 56.0F;
				PartDefinition topang = landasan.addOrReplaceChild("topang_" + (side < 0 ? "k" : "n") + i, CubeListBuilder.create(),
						PartPose.offsetAndRotation(side * 62.0F, 20.0F, z, 0.0F, 0.0F, side * 0.42F));
				topang.addOrReplaceChild("b", p(KAYU, 80 + i * 11).addBox(0.0F, -1.5F, -1.5F, 46.0F, 3.0F, 3.0F), PartPose.rotation(0.0F, 0.0F, -Mth.HALF_PI));
			}
		}
		// bamboo scaffold: standards, two ledger levels with boards, a ladder per side
		for (int side = -1; side <= 1; side += 2) {
			String s = side < 0 ? "k" : "n";
			PartDefinition perancah = landasan.addOrReplaceChild("perancah_" + s, CubeListBuilder.create(), PartPose.offset(side * 74.0F, 20.0F, 0.0F));
			for (int i = 0; i < 7; i++) {
				float z = -168.0F + i * 56.0F;
				perancah.addOrReplaceChild("tiang_" + i, p(BAMBU, i * 21).addBox(0.0F, -1.5F, -1.5F, 76.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 0.0F, z,
						0.0F, 0.0F, -Mth.HALF_PI));
			}
			for (int lv = 0; lv < 2; lv++) {
				float y = -30.0F - lv * 32.0F;
				perancah.addOrReplaceChild("palang_" + lv, p(BAMBU, 60 + lv * 40).addBox(-1.0F, -1.0F, -170.0F, 2.0F, 2.0F, 56.0F)
						.addBox(-1.0F, -1.0F, -114.0F, 2.0F, 2.0F, 56.0F).addBox(-1.0F, -1.0F, -58.0F, 2.0F, 2.0F, 56.0F)
						.addBox(-1.0F, -1.0F, -2.0F, 2.0F, 2.0F, 56.0F).addBox(-1.0F, -1.0F, 54.0F, 2.0F, 2.0F, 56.0F)
						.addBox(-1.0F, -1.0F, 110.0F, 2.0F, 2.0F, 58.0F), PartPose.offset(0.0F, y, 0.0F));
				for (int k = 0; k < 6; k++) {
					perancah.addOrReplaceChild("papan_" + lv + "_" + k, p(PAPAN, k * 31 + lv * 9).addBox(side < 0 ? 0.0F : -12.0F, 0.0F, 0.0F, 12.0F, 1.5F, 54.0F),
							PartPose.offset(0.0F, y + 1.0F, -168.0F + k * 56.0F + 1.0F));
				}
			}
			PartDefinition tangga = perancah.addOrReplaceChild("tangga", p(BAMBU, 140).addBox(-6.0F, -62.0F, -1.0F, 1.5F, 62.0F, 1.5F)
					.addBox(4.5F, -62.0F, -1.0F, 1.5F, 62.0F, 1.5F), PartPose.offsetAndRotation(side * 6.0F, 0.0F, 30.0F, 0.0F, Mth.HALF_PI, side * 0.18F));
			for (int k = 0; k < 8; k++) {
				tangga.addOrReplaceChild("anak_" + k, p(BAMBU, 150 + k * 3).addBox(-5.0F, -0.6F, -0.6F, 10.0F, 1.2F, 1.2F), PartPose.offset(0.0F, -6.0F - k * 7.5F,
						-0.25F));
			}
		}
		// ---------------------------------------------------------------- the hull group (keel bottom 1 block above the slip)
		PartDefinition kapal = root.addOrReplaceChild("kapal", CubeListBuilder.create(), PartPose.offset(0.0F, -16.0F, 0.0F));
		// stage 0: keel, stem and stern posts, the keel scarfs
		PartDefinition t0 = kapal.addOrReplaceChild("tahap_0", CubeListBuilder.create(), PartPose.ZERO);
		t0.addOrReplaceChild("lunas", p(KAYU).addBox(-8.0F, 16.0F, -176.0F, 16.0F, 8.0F, 44.0F).addBox(-8.0F, 16.0F, -132.0F, 16.0F, 8.0F, 44.0F)
				.addBox(-8.0F, 16.0F, -88.0F, 16.0F, 8.0F, 44.0F).addBox(-8.0F, 16.0F, -44.0F, 16.0F, 8.0F, 44.0F)
				.addBox(-8.0F, 16.0F, 0.0F, 16.0F, 8.0F, 44.0F).addBox(-8.0F, 16.0F, 44.0F, 16.0F, 8.0F, 44.0F)
				.addBox(-8.0F, 16.0F, 88.0F, 16.0F, 8.0F, 44.0F).addBox(-8.0F, 16.0F, 132.0F, 16.0F, 8.0F, 44.0F), PartPose.ZERO);
		t0.addOrReplaceChild("sambungan", p(BESI).addBox(-8.4F, 18.0F, -90.0F, 16.8F, 4.0F, 4.0F).addBox(-8.4F, 18.0F, -2.0F, 16.8F, 4.0F, 4.0F)
				.addBox(-8.4F, 18.0F, 86.0F, 16.8F, 4.0F, 4.0F), PartPose.ZERO);
		t0.addOrReplaceChild("linggi_depan", p(KAYU, 100).addBox(0.0F, -4.0F, -4.0F, 56.0F, 8.0F, 8.0F), PartPose.offsetAndRotation(0.0F, 20.0F, -180.0F, 0.0F,
				0.0F, -Mth.HALF_PI));
		t0.addOrReplaceChild("linggi_belakang", p(KAYU, 120).addBox(0.0F, -4.0F, -4.0F, 70.0F, 8.0F, 8.0F), PartPose.offsetAndRotation(0.0F, 20.0F, 180.0F,
				0.0F, 0.0F, -Mth.HALF_PI));
		// stage 1: twelve frames (floor + two futtocks + top timbers) and a ribband along each side
		PartDefinition t1 = kapal.addOrReplaceChild("tahap_1", CubeListBuilder.create(), PartPose.ZERO);
		for (int i = 0; i < GADING_N; i++) {
			float z = -165.0F + i * 30.0F;
			float w = lebarDi(z);
			float sh = sheerDi(z);
			PartDefinition g = t1.addOrReplaceChild("gading_" + i, CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, z));
			g.addOrReplaceChild("dasar", p(GADING, i * 19).addBox(-32.0F * w, 12.0F, -2.0F, 64.0F * w, 4.0F, 4.0F), PartPose.ZERO);
			for (int side = -1; side <= 1; side += 2) {
				// futtock from the floor's end up and out to the turn of the bilge, then the top timber to the sheer
				float x0 = side * 32.0F * w;
				float lean = side * 0.55F;
				PartDefinition futtock = g.addOrReplaceChild("futtock_" + (side < 0 ? "k" : "n"), p(GADING, 60 + i * 7).addBox(-2.0F, -22.0F, -2.0F, 4.0F, 22.0F,
						4.0F), PartPose.offsetAndRotation(x0, 16.0F, 0.0F, 0.0F, 0.0F, lean));
				float topLen = 20.0F - sh;
				futtock.addOrReplaceChild("atas", p(GADING, 120 + i * 5).addBox(-1.75F, -topLen, -1.75F, 3.5F, topLen, 3.5F), PartPose.offsetAndRotation(0.0F,
						-21.0F, 0.0F, 0.0F, 0.0F, -lean * 0.75F));
			}
		}
		// long timbers are built along x (texture band rule) and turned fore-aft
		t1.addOrReplaceChild("ribband_kiri", p(GADING, 30).addBox(-168.0F, -1.0F, -1.0F, 336.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(-52.0F, -8.0F, 0.0F,
				0.0F, Mth.HALF_PI, 0.0F));
		t1.addOrReplaceChild("ribband_kanan", p(GADING, 90).addBox(-168.0F, -1.0F, -1.0F, 336.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(52.0F, -8.0F, 0.0F,
				0.0F, Mth.HALF_PI, 0.0F));
		// stage 2: the lower planking (bottom and bilge strakes) per station
		PartDefinition t2 = kapal.addOrReplaceChild("tahap_2", CubeListBuilder.create(), PartPose.ZERO);
		for (int i = 0; i < LEBAR.length; i++) {
			float w = LEBAR[i];
			float s = SHEER[i];
			int u = (i * 37) % 128;
			PartDefinition st = t2.addOrReplaceChild("stasiun_" + i, CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, -176.0F + STATION * i));
			st.addOrReplaceChild("bawah", p(LAMBUNG, u + 20).addBox(-32.0F * w, 0.0F + s * 0.5F, 0.0F, 64.0F * w, 16.0F, STATION), PartPose.ZERO);
			st.addOrReplaceChild("lambung_kiri", p(LAMBUNG, u + 40).addBox(-48.0F * w, -4.0F + s, 0.0F, 16.0F * w, 5.5F, STATION), PartPose.ZERO);
			st.addOrReplaceChild("lambung_kanan", p(LAMBUNG, u + 50).addBox(32.0F * w, -4.0F + s, 0.0F, 16.0F * w, 5.5F, STATION), PartPose.ZERO);
		}
		// stage 3: full hull, wale, deck, bulwarks, bow transom, stern castle, mast steps
		PartDefinition t3 = kapal.addOrReplaceChild("tahap_3", CubeListBuilder.create(), PartPose.ZERO);
		for (int i = 0; i < LEBAR.length; i++) {
			float w = LEBAR[i];
			float s = SHEER[i];
			int u = (i * 37) % 128;
			PartDefinition st = t3.addOrReplaceChild("stasiun_" + i, CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, -176.0F + STATION * i));
			st.addOrReplaceChild("atas", p(LAMBUNG, u + 60).addBox(-48.0F * w, -9.5F + s, 0.0F, 96.0F * w, 5.5F, STATION), PartPose.ZERO);
			st.addOrReplaceChild("wale", p(CAT, u).addBox(-56.0F * w, -15.5F + s, 0.0F, 112.0F * w, 6.0F, STATION), PartPose.ZERO);
			st.addOrReplaceChild("geladak", p(KAYU, u + 10).addBox(-56.0F * w + 2.0F, -16.5F + s, 0.0F, 112.0F * w - 4.0F, 1.0F, STATION), PartPose.ZERO);
			st.addOrReplaceChild("pagar", p(LAMBUNG, u + 80).addBox(-56.0F * w, -24.0F + s, 0.0F, 2.0F, 8.5F, STATION)
					.addBox(56.0F * w - 2.0F, -24.0F + s, 0.0F, 2.0F, 8.5F, STATION), PartPose.ZERO);
			st.addOrReplaceChild("tutup", p(CAT, u + 30).addBox(-56.5F * w, -25.5F + s, 0.0F, 3.0F, 1.5F, STATION)
					.addBox(56.5F * w - 3.0F, -25.5F + s, 0.0F, 3.0F, 1.5F, STATION), PartPose.ZERO);
		}
		t3.addOrReplaceChild("haluan", p(HIASAN).addBox(-31.0F, -38.0F, -184.0F, 62.0F, 40.0F, 8.0F), PartPose.ZERO);
		t3.addOrReplaceChild("kabin", p(LAMBUNG, 160).addBox(-42.0F, -45.5F, 112.0F, 84.0F, 24.0F, 40.0F), PartPose.ZERO);
		t3.addOrReplaceChild("geladak_buritan", p(KAYU, 60).addBox(-46.0F, -47.5F, 104.0F, 92.0F, 2.0F, 56.0F), PartPose.ZERO);
		t3.addOrReplaceChild("dinding_buritan", p(LAMBUNG, 10).addBox(-46.0F, -45.5F, 152.0F, 92.0F, 24.0F, 24.0F), PartPose.ZERO);
		t3.addOrReplaceChild("transom", p(HIASAN).addBox(-46.0F, -50.0F, 176.0F, 92.0F, 48.0F, 6.0F), PartPose.ZERO);
		t3.addOrReplaceChild("pangkal", p(TIANG).addBox(-6.0F, -24.0F, -12.0F, 12.0F, 8.0F, 12.0F).addBox(-5.0F, -24.0F, -134.0F, 10.0F, 8.0F, 10.0F)
				.addBox(-3.0F, -56.0F, 123.0F, 10.0F, 8.0F, 10.0F), PartPose.ZERO);
		// stage 4: three masts, furled sails lashed on the boom, streamers
		PartDefinition t4 = kapal.addOrReplaceChild("tahap_4", CubeListBuilder.create(), PartPose.ZERO);
		tiang(t4, "utama", 0.0F, -8.0F, -16.5F, 320.0F, 8.0F, 160.0F, -40.0F, -60.0F);
		tiang(t4, "depan", -2.0F, -128.0F, -21.5F, 240.0F, 6.0F, 120.0F, -30.0F, -44.0F);
		tiang(t4, "belakang", 2.0F, 128.0F, -47.5F, 200.0F, 6.0F, 100.0F, -20.0F, -36.0F);
		return LayerDefinition.create(mesh, 256, B * 12);
	}

	private static float lebarDi(float z) {
		float f = Mth.clamp((z + 176.0F) / STATION, 0.0F, LEBAR.length - 1.001F);
		int i = (int) f;
		return Mth.lerp(f - i, LEBAR[i], LEBAR[Math.min(i + 1, LEBAR.length - 1)]);
	}

	private static float sheerDi(float z) {
		float f = Mth.clamp((z + 176.0F) / STATION, 0.0F, SHEER.length - 1.001F);
		int i = (int) f;
		return Mth.lerp(f - i, SHEER[i], SHEER[Math.min(i + 1, SHEER.length - 1)]);
	}

	/** A mast with its furled sail: the bundle of folded panels on the boom, four lashings and a streamer. */
	private static void tiang(PartDefinition t4, String id, float x, float z, float y, float tinggi, float tebal, float panjang, float depan, float boom) {
		PartDefinition tiang = t4.addOrReplaceChild("tiang_" + id, CubeListBuilder.create(), PartPose.offset(x, y, z));
		tiang.addOrReplaceChild("batang", p(TIANG, 7).addBox(0.0F, -tebal / 2.0F, -tebal / 2.0F, tinggi, tebal, tebal), PartPose.rotation(0.0F, 0.0F,
				-Mth.HALF_PI));
		tiang.addOrReplaceChild("puncak", p(CAT, 3).addBox(-tebal / 2.0F - 1.0F, -tinggi - 4.0F, -tebal / 2.0F - 1.0F, tebal + 2.0F, 4.0F, tebal + 2.0F),
				PartPose.ZERO);
		PartDefinition gulung = tiang.addOrReplaceChild("gulung", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, boom, 0.0F, 0.0F, -Mth.HALF_PI,
				0.0F));
		gulung.addOrReplaceChild("layar", p(LAYAR).addBox(depan, -9.0F, -3.5F, panjang, 9.0F, 7.0F), PartPose.ZERO);
		gulung.addOrReplaceChild("bilah", p(BAMBU, 20).addBox(depan - 2.0F, -1.0F, -1.0F, panjang + 4.0F, 2.0F, 2.0F)
				.addBox(depan - 2.0F, -10.0F, -1.0F, panjang + 4.0F, 2.0F, 2.0F), PartPose.ZERO);
		for (int i = 0; i < 4; i++) {
			gulung.addOrReplaceChild("ikat_" + i, p(TALI, i * 9).addBox(depan + panjang * (0.12F + 0.25F * i), -10.5F, -4.0F, 2.0F, 11.0F, 8.0F), PartPose.ZERO);
		}
		PartDefinition seg = tiang.addOrReplaceChild("panji", CubeListBuilder.create(), PartPose.offset(0.0F, -tinggi + 6.0F, 0.0F));
		for (int i = 0; i < 3; i++) {
			seg = seg.addOrReplaceChild("kain_" + i, p(CAT, 100 + i * 10).addBox(0.0F, 0.0F, -0.25F, 10.0F - i * 2.0F, 4.0F - i, 0.5F),
					PartPose.offset(i == 0 ? tebal / 2.0F : 12.0F - i * 2.0F, 0.0F, 0.0F));
		}
	}

	/** The slipway model: stage visibility, the growing newest stage, streamers. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart[] tahap = new ModelPart[TAHAP];
		private final ModelPart[][] kain = new ModelPart[3][3];

		public Model(ModelPart root) {
			super(root);
			ModelPart kapal = root.getChild("kapal");
			for (int i = 0; i < TAHAP; i++) {
				tahap[i] = kapal.getChild("tahap_" + i);
			}
			String[] ids = {"utama", "depan", "belakang"};
			ModelPart t4 = tahap[4];
			for (int s = 0; s < 3; s++) {
				ModelPart seg = t4.getChild("tiang_" + ids[s]).getChild("panji");
				for (int i = 0; i < 3; i++) {
					seg = seg.getChild("kain_" + i);
					kain[s][i] = seg;
				}
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			for (int i = 0; i < TAHAP; i++) {
				ModelPart part = tahap[i];
				part.visible = i <= state.tahap;
				float k = i == state.tahap ? state.tumbuh : 1.0F;
				part.xScale = k;
				part.yScale = k;
				part.zScale = k;
			}
			for (int s = 0; s < 3; s++) {
				for (int i = 0; i < 3; i++) {
					kain[s][i].yRot = state.jauh ? 0.15F : Mth.sin(state.kibar + i * 0.9F + s) * (0.25F + i * 0.12F);
				}
			}
		}
	}
}
