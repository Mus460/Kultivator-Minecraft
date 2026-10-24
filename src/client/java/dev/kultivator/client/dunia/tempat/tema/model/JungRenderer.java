package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import dev.kultivator.dunia.tempat.tema.pelabuhan.KapalBajakLautEntity;
import dev.kultivator.dunia.tempat.tema.pelabuhan.KapalDagangEntity;
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
 * Jung dagang ({@code kultivator:kapal_dagang}) and the pirate junk ({@code kultivator:kapal_bajak_laut}), DESIGN
 * 38.T.9.1: a 22 x 7 block junk on one shared hull. Eight hull stations taper toward the square bow and rise in a sheer
 * to the high stern; each station has a keel, a narrow bilge strake, a 96 px hull strake, a flared wale, bulwarks with a
 * lacquered cap rail and a deck. The flat bow transom carries the painted <i>mata kapal</i>; the stern castle has a
 * lacquered cabin with lattice windows, a poop deck with twelve rail posts, a painted transom, a stern lantern and a
 * tiller. Three masts (main 320 px, fore 240, mizzen 200) carry battened lug sails of nine panels with ten bamboo
 * battens each, a streamer at each masthead, a hinged rudder, an anchor on a chain at the bow and a gangplank on the
 * starboard side, plus three to six cargo crates on deck. The pirate junk adds a bow ram, torn black-red sails and black
 * flags.
 * <p>
 * Animation (state from {@link KapalDagangEntity} / {@link KapalBajakLautEntity}): heave +-0.08 blocks over 80 ticks and
 * roll +-1.5 degrees when moored; 3 degrees of heel and a sheeted-out sail under way; furling lowers the panels in turn
 * (panel i from t = 4i, 8 ticks each, stacking on the boom; unfurling the reverse from the top); anchor down/up 30 ticks;
 * gangplank down 20 ticks (pirates 10); rudder = nilai x 12 degrees; a departing ship fades out between 120 and 160
 * blocks from the viewer. Textures {@code kapal_dagang.png}, {@code kapal_dagang_jung.png} (blue-white sails),
 * {@code kapal_bajak_laut.png} (256 x 896, bands of 64).
 */
public class JungRenderer<T extends TemaMekanismeEntity> extends TemaBendaRenderer<T, JungRenderer.State, JungRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("kapal_dagang"), "main");
	public static final ModelLayerLocation LAYER_BAJAK = new ModelLayerLocation(Kultivator.id("kapal_bajak_laut"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/kapal_dagang.png");
	private static final Identifier TEX_JUNG = Kultivator.id("textures/entity/tempat/tema/kapal_dagang_jung.png");
	private static final Identifier TEX_BAJAK = Kultivator.id("textures/entity/tempat/tema/kapal_bajak_laut.png");
	private static final Identifier GLOW = Kultivator.id("textures/entity/tempat/tema/kapal_dagang_pancar.png");
	private static final Identifier GLOW_BAJAK = Kultivator.id("textures/entity/tempat/tema/kapal_bajak_laut_pancar.png");

	/** Band height of the ship textures. */
	static final int B = 64;
	static final int LAMBUNG = 0;
	static final int GELADAK = 1;
	static final int HALUAN = 2;
	static final int LAYAR = 3;
	static final int TIANG = 4;
	static final int BAMBU = 5;
	static final int BESI = 6;
	static final int RANTAI = 7;
	static final int PETI = 8;
	static final int LENTERA = 9;
	static final int TALI = 10;
	static final int CAT = 11;
	static final int PANJI = 12;
	static final int BURITAN = 13;

	/** Hull stations from the bow: width factor and sheer (px, negative = up). */
	static final float[] LEBAR = {0.56F, 0.80F, 0.94F, 1.0F, 1.0F, 0.97F, 0.90F, 0.82F};
	static final float[] SHEER = {-12.0F, -5.0F, -1.0F, 0.0F, 0.0F, -1.0F, -6.0F, -14.0F};
	static final float STATION = 44.0F;
	private static final int PANEL = 9;
	/** Crate places on deck (x, z, yaw). */
	private static final float[][] PETI_POS = {{-20, -60, 0.1F}, {18, -44, -0.15F}, {-22, 30, 0.05F}, {20, 46, 0.2F}, {-4, -86, -0.08F}, {2, 70, 0.12F}};

	private final boolean bajak;

	public static class State extends TemaBendaRenderer.State {
		public float layar;
		public float[] panel = new float[PANEL];
		public float jangkar;
		public float titian;
		public float kemudi;
		public float oleng;
		public float angguk;
		public float naik;
		public float miring;
		public float set;
		public float kibar;
		public int peti;
		public boolean bajak;
	}

	private JungRenderer(EntityRendererProvider.Context context, boolean bajak) {
		super(context, new Model(context.bakeLayer(bajak ? LAYER_BAJAK : LAYER)), bajak ? new Identifier[] {TEX_BAJAK} : new Identifier[] {TEX, TEX_JUNG},
				bajak ? new Identifier[] {GLOW_BAJAK} : new Identifier[] {GLOW, GLOW}, 0.0F);
		this.bajak = bajak;
	}

	public static JungRenderer<KapalDagangEntity> dagang(EntityRendererProvider.Context context) {
		return new JungRenderer<>(context, false);
	}

	public static JungRenderer<KapalBajakLautEntity> bajak(EntityRendererProvider.Context context) {
		return new JungRenderer<>(context, true);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, () -> mesh(false));
		ModelLayerRegistry.registerModelLayer(LAYER_BAJAK, () -> mesh(true));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double batas() {
		return 192.0D;
	}

	@Override
	protected double jauhKecil() {
		return 80.0D;
	}

	@Override
	protected double reach(T entity) {
		return 12.5D;
	}

	@Override
	protected int tekstur(State state) {
		return !state.bajak && state.varian == KapalDagangEntity.VARIAN_JUNG_LAUT ? 1 : 0;
	}

	@Override
	public void extractRenderState(T entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.bajak = bajak;
		float t = state.umur;
		int k = state.keadaan;
		boolean berlayar;
		float furl;
		float anchor;
		float plank;
		boolean fading;
		if (bajak) {
			berlayar = k == KapalBajakLautEntity.KEADAAN_BERLAYAR || k == KapalBajakLautEntity.KEADAAN_MEMUDAR;
			furl = berlayar ? 0.0F : 1.0F;
			anchor = berlayar ? 0.0F : 1.0F;
			plank = k == KapalBajakLautEntity.KEADAAN_TITIAN_TURUN ? keluar(maju(t, 0.0F, 10.0F)) : 0.0F;
			fading = k == KapalBajakLautEntity.KEADAAN_MEMUDAR;
			for (int i = 0; i < PANEL; i++) {
				state.panel[i] = furl;
			}
		} else {
			berlayar = k == KapalDagangEntity.KEADAAN_BERLAYAR || k == KapalDagangEntity.KEADAAN_MEMUDAR;
			fading = k == KapalDagangEntity.KEADAAN_MEMUDAR;
			for (int i = 0; i < PANEL; i++) {
				float p;
				if (k == KapalDagangEntity.KEADAAN_GULUNG_LAYAR) {
					p = sinus(maju(t, 4.0F * i, 8.0F));
				} else if (k == KapalDagangEntity.KEADAAN_KEMBANG_LAYAR) {
					p = 1.0F - sinus(maju(t, 4.0F * (PANEL - 1 - i), 8.0F));
				} else {
					p = berlayar ? 0.0F : 1.0F;
				}
				state.panel[i] = p;
			}
			furl = state.panel[PANEL - 1];
			anchor = switch (k) {
				case KapalDagangEntity.KEADAAN_JANGKAR_TURUN -> sinus(maju(t, 0.0F, 30.0F));
				case KapalDagangEntity.KEADAAN_JANGKAR_NAIK -> 1.0F - sinus(maju(t, 0.0F, 30.0F));
				case KapalDagangEntity.KEADAAN_DIAM, KapalDagangEntity.KEADAAN_TITIAN_TURUN -> 1.0F;
				default -> 0.0F;
			};
			plank = k == KapalDagangEntity.KEADAAN_TITIAN_TURUN ? keluar(maju(t, 0.0F, 20.0F)) : 0.0F;
		}
		state.layar = furl;
		state.jangkar = anchor;
		state.titian = plank;
		state.kemudi = Mth.clamp(state.nilai, -1.0F, 1.0F) * rad(12.0F);
		float w = state.waktu;
		if (state.jauh) {
			state.oleng = 0.0F;
			state.angguk = 0.0F;
			state.naik = 0.0F;
		} else {
			state.naik = Mth.sin(w * Mth.TWO_PI / 80.0F + state.fase) * 1.28F;
			state.oleng = Mth.sin(w * Mth.TWO_PI / 80.0F * 0.73F + state.fase * 1.3F) * rad(1.5F);
			state.angguk = Mth.sin(w * Mth.TWO_PI / 80.0F * 1.21F + state.fase * 0.7F) * rad(berlayar ? 0.8F : 0.4F);
		}
		state.miring = berlayar ? rad(3.0F) : 0.0F;
		state.set = berlayar ? rad(14.0F) : 0.0F;
		state.kibar = state.jauh ? 0.0F : w * 0.35F + state.fase;
		state.peti = 3 + state.acak % 4;
		state.pancar = 0.85F;
		if (fading) {
			float d = (float) Math.sqrt(state.distanceToCameraSq);
			state.alpha = Mth.clamp((160.0F - d) / 40.0F, 0.0F, 1.0F);
		}
	}

	private static CubeListBuilder p(int band) {
		return pita(band, B);
	}

	private static CubeListBuilder p(int band, int u) {
		return pita(band, B, u);
	}

	static LayerDefinition mesh(boolean bajak) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// everything rolls about the waterline (y 24)
		PartDefinition kapal = root.addOrReplaceChild("kapal", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
		// ---------------------------------------------------------------- hull stations (bow = -z)
		for (int i = 0; i < LEBAR.length; i++) {
			float w = LEBAR[i];
			float s = SHEER[i];
			float z0 = -176.0F + STATION * i;
			int u = (i * 37) % 128;
			PartDefinition st = kapal.addOrReplaceChild("stasiun_" + i, CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, z0));
			st.addOrReplaceChild("lunas", p(LAMBUNG, u).addBox(-8.0F, 16.0F + s * 0.2F, 0.0F, 16.0F, 8.0F, STATION), PartPose.ZERO);
			st.addOrReplaceChild("lambung_bawah", p(LAMBUNG, u + 20).addBox(-32.0F * w, 0.0F + s * 0.5F, 0.0F, 64.0F * w, 16.0F, STATION), PartPose.ZERO);
			st.addOrReplaceChild("lambung_atas", p(LAMBUNG, u + 40).addBox(-48.0F * w, -9.5F + s, 0.0F, 96.0F * w, 9.5F, STATION), PartPose.ZERO);
			st.addOrReplaceChild("wale", p(LAMBUNG, u + 60).addBox(-56.0F * w, -15.5F + s, 0.0F, 112.0F * w, 6.0F, STATION), PartPose.ZERO);
			st.addOrReplaceChild("geladak", p(GELADAK, u).addBox(-56.0F * w + 2.0F, -16.5F + s, 0.0F, 112.0F * w - 4.0F, 1.0F, STATION), PartPose.ZERO);
			st.addOrReplaceChild("pagar_kiri", p(LAMBUNG, u + 80).addBox(-56.0F * w, -24.0F + s, 0.0F, 2.0F, 8.5F, STATION), PartPose.ZERO);
			st.addOrReplaceChild("pagar_kanan", p(LAMBUNG, u + 90).addBox(56.0F * w - 2.0F, -24.0F + s, 0.0F, 2.0F, 8.5F, STATION), PartPose.ZERO);
			st.addOrReplaceChild("tutup_kiri", p(CAT, u).addBox(-56.5F * w, -25.5F + s, 0.0F, 3.0F, 1.5F, STATION), PartPose.ZERO);
			st.addOrReplaceChild("tutup_kanan", p(CAT, u + 9).addBox(56.5F * w - 3.0F, -25.5F + s, 0.0F, 3.0F, 1.5F, STATION), PartPose.ZERO);
		}
		// ---------------------------------------------------------------- square bow transom with the painted eyes (decal at u 8..70, v 8..48)
		kapal.addOrReplaceChild("haluan", p(HALUAN).addBox(-31.0F, -38.0F, -184.0F, 62.0F, 40.0F, 8.0F), PartPose.ZERO);
		kapal.addOrReplaceChild("haluan_bibir", p(CAT, 20).addBox(-32.0F, -40.0F, -185.0F, 64.0F, 2.0F, 9.0F), PartPose.ZERO);
		if (bajak) {
			// the bow ram, iron-shod, at the waterline
			kapal.addOrReplaceChild("taji", p(BESI).addBox(-4.0F, -6.0F, -216.0F, 8.0F, 8.0F, 32.0F)
					.addBox(-2.5F, -4.5F, -224.0F, 5.0F, 5.0F, 8.0F), PartPose.ZERO);
		}
		// ---------------------------------------------------------------- stern castle (cabin front decal on HALUAN u 140..224, v 40..64)
		kapal.addOrReplaceChild("kabin", p(HALUAN, 100).addBox(-42.0F, -45.5F, 112.0F, 84.0F, 24.0F, 40.0F), PartPose.ZERO);
		kapal.addOrReplaceChild("geladak_buritan", p(GELADAK, 30).addBox(-46.0F, -47.5F, 104.0F, 92.0F, 2.0F, 56.0F), PartPose.ZERO);
		kapal.addOrReplaceChild("dinding_buritan", p(LAMBUNG, 10).addBox(-46.0F, -45.5F, 152.0F, 92.0F, 24.0F, 24.0F), PartPose.ZERO);
		kapal.addOrReplaceChild("atap_buritan", p(GELADAK, 70).addBox(-46.0F, -47.5F, 160.0F, 92.0F, 2.0F, 16.0F), PartPose.ZERO);
		// painted transom (decal on BURITAN, south face u 104..196, v 6..54)
		kapal.addOrReplaceChild("transom", p(BURITAN).addBox(-46.0F, -50.0F, 176.0F, 92.0F, 48.0F, 6.0F), PartPose.ZERO);
		PartDefinition pagar = kapal.addOrReplaceChild("pagar_buritan", CubeListBuilder.create(), PartPose.offset(0.0F, -47.5F, 0.0F));
		for (int i = 0; i < 12; i++) {
			float x = i < 6 ? -45.0F : 43.0F;
			float z = 106.0F + (i % 6) * 13.6F;
			pagar.addOrReplaceChild("tiang_" + i, p(CAT, i * 7).addBox(0.0F, -10.0F, 0.0F, 2.0F, 10.0F, 2.0F), PartPose.offset(x, 0.0F, z));
		}
		pagar.addOrReplaceChild("rel_kiri", p(CAT, 40).addBox(-45.5F, -11.0F, 104.0F, 3.0F, 1.5F, 38.0F)
				.addBox(-45.5F, -11.0F, 142.0F, 3.0F, 1.5F, 38.0F), PartPose.ZERO);
		pagar.addOrReplaceChild("rel_kanan", p(CAT, 60).addBox(42.5F, -11.0F, 104.0F, 3.0F, 1.5F, 38.0F)
				.addBox(42.5F, -11.0F, 142.0F, 3.0F, 1.5F, 38.0F), PartPose.ZERO);
		pagar.addOrReplaceChild("rel_depan", p(CAT, 80).addBox(-45.5F, -11.0F, 103.0F, 91.0F, 1.5F, 2.0F), PartPose.ZERO);
		// tiller bar and stern lantern on a curved bracket
		kapal.addOrReplaceChild("kemudi_tangan", p(TIANG, 12).addBox(-1.0F, -50.5F, 150.0F, 2.0F, 2.0F, 26.0F), PartPose.ZERO);
		PartDefinition lentera = kapal.addOrReplaceChild("lentera", p(BESI, 30).addBox(-0.5F, -14.0F, -0.5F, 1.0F, 14.0F, 1.0F)
				.addBox(-0.5F, -14.0F, 0.5F, 1.0F, 1.0F, 6.0F), PartPose.offset(0.0F, -50.0F, 182.0F));
		lentera.addOrReplaceChild("kap", p(LENTERA).addBox(-3.0F, -1.0F, 3.5F, 6.0F, 6.0F, 6.0F)
				.addBox(-3.5F, -1.8F, 3.0F, 7.0F, 0.8F, 7.0F).addBox(-3.5F, 5.0F, 3.0F, 7.0F, 0.8F, 7.0F), PartPose.offset(0.0F, -13.0F, 0.0F));
		// ---------------------------------------------------------------- rudder on its hinge behind the transom (blade built along x, turned down)
		PartDefinition kemudi = kapal.addOrReplaceChild("kemudi", p(TIANG, 50).addBox(-3.0F, -30.0F, -3.0F, 6.0F, 30.0F, 6.0F),
				PartPose.offset(0.0F, -16.0F, 184.0F));
		kemudi.addOrReplaceChild("bilah", p(LAMBUNG, 120).addBox(0.0F, -4.0F, 0.0F, 64.0F, 8.0F, 40.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
				Mth.HALF_PI));
		// ---------------------------------------------------------------- masts with battened sails
		tiang(kapal, "utama", 0.0F, -8.0F, -16.5F, 320.0F, 8.0F, 160.0F, 24.0F, -40.0F, -40.0F, bajak);
		tiang(kapal, "depan", -2.0F, -128.0F, -21.5F, 240.0F, 6.0F, 120.0F, 18.0F, -30.0F, -34.0F, bajak);
		tiang(kapal, "belakang", 2.0F, 128.0F, -47.5F, 200.0F, 6.0F, 100.0F, 15.0F, -20.0F, -26.0F, bajak);
		// ---------------------------------------------------------------- anchor at the bow (hangs from the hawse; drops 3 blocks)
		PartDefinition jangkar = kapal.addOrReplaceChild("jangkar", CubeListBuilder.create(), PartPose.offset(26.0F, -20.0F, -170.0F));
		jangkar.addOrReplaceChild("rantai", p(RANTAI).addBox(0.0F, -1.0F, -1.0F, 64.0F, 2.0F, 2.0F), PartPose.rotation(0.0F, 0.0F, Mth.HALF_PI));
		PartDefinition mata = jangkar.addOrReplaceChild("mata", p(BESI).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 24.0F, 3.0F)
				.addBox(-8.0F, 2.0F, -1.0F, 16.0F, 2.0F, 2.0F), PartPose.offset(0.0F, 8.0F, 0.0F));
		mata.addOrReplaceChild("lengan_kiri", p(BESI, 20).addBox(-9.0F, -1.5F, -1.5F, 9.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 23.0F, 0.0F, 0.0F, 0.0F,
				-0.55F));
		mata.addOrReplaceChild("lengan_kanan", p(BESI, 40).addBox(0.0F, -1.5F, -1.5F, 9.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, 23.0F, 0.0F, 0.0F, 0.0F,
				0.55F));
		mata.addOrReplaceChild("kuku", p(BESI, 60).addBox(-9.5F, -4.0F, -2.0F, 3.0F, 3.0F, 4.0F).addBox(6.5F, -4.0F, -2.0F, 3.0F, 3.0F, 4.0F),
				PartPose.offset(0.0F, 21.0F, 0.0F));
		// ---------------------------------------------------------------- gangplank on the starboard (+x) side, hinged at the cap rail
		PartDefinition titian = kapal.addOrReplaceChild("titian", CubeListBuilder.create(), PartPose.offset(56.0F, -25.0F, 4.0F));
		titian.addOrReplaceChild("papan", p(GELADAK, 90).addBox(0.0F, -1.0F, -12.0F, 96.0F, 2.0F, 24.0F)
				.addBox(4.0F, -2.0F, -10.0F, 1.0F, 1.0F, 20.0F).addBox(28.0F, -2.0F, -10.0F, 1.0F, 1.0F, 20.0F)
				.addBox(52.0F, -2.0F, -10.0F, 1.0F, 1.0F, 20.0F).addBox(76.0F, -2.0F, -10.0F, 1.0F, 1.0F, 20.0F), PartPose.ZERO);
		titian.addOrReplaceChild("tali", p(TALI).addBox(0.0F, -12.0F, -12.5F, 96.0F, 0.8F, 0.8F).addBox(0.0F, -12.0F, 11.7F, 96.0F, 0.8F, 0.8F),
				PartPose.ZERO);
		// ---------------------------------------------------------------- cargo crates with rope straps
		PartDefinition muatan = kapal.addOrReplaceChild("muatan", CubeListBuilder.create(), PartPose.offset(0.0F, -16.5F, 0.0F));
		for (int i = 0; i < PETI_POS.length; i++) {
			float[] c = PETI_POS[i];
			PartDefinition peti = muatan.addOrReplaceChild("peti_" + i, p(PETI, i * 23).addBox(-8.0F, -16.0F, -8.0F, 16.0F, 16.0F, 16.0F),
					PartPose.offsetAndRotation(c[0], 0.0F, c[1], 0.0F, c[2], 0.0F));
			peti.addOrReplaceChild("ikat", p(TALI, i * 11).addBox(-8.3F, -16.3F, -1.0F, 16.6F, 16.6F, 2.0F)
					.addBox(-1.0F, -16.4F, -8.3F, 2.0F, 0.4F, 16.6F), PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 256, B * 14);
	}

	/**
	 * One mast at (x, z) with its base at y (deck), a pole {@code tinggi} long and {@code tebal} thick (built along x and
	 * turned up), nine sail panels {@code panjang} x {@code tinggiPanel} (built along x, turned fore-aft; {@code depan}
	 * of each panel reaches forward of the mast), ten bamboo battens, a masthead streamer of three segments and, on the
	 * pirate junk, a black flag.
	 */
	private static void tiang(PartDefinition kapal, String id, float x, float z, float y, float tinggi, float tebal, float panjang, float tinggiPanel,
			float depan, float boom, boolean bajak) {
		PartDefinition tiang = kapal.addOrReplaceChild("tiang_" + id, CubeListBuilder.create(), PartPose.offset(x, y, z));
		tiang.addOrReplaceChild("batang", p(TIANG, 7).addBox(0.0F, -tebal / 2.0F, -tebal / 2.0F, tinggi, tebal, tebal), PartPose.rotation(0.0F, 0.0F,
				-Mth.HALF_PI));
		tiang.addOrReplaceChild("cincin", p(BESI, 9).addBox(-tebal / 2.0F - 0.6F, -2.0F, -tebal / 2.0F - 0.6F, tebal + 1.2F, 2.0F, tebal + 1.2F)
				.addBox(-tebal / 2.0F - 0.6F, boom - 4.0F, -tebal / 2.0F - 0.6F, tebal + 1.2F, 2.0F, tebal + 1.2F), PartPose.ZERO);
		// the sail group turns the panels fore-aft (+x of a panel = aft) and sheets out under way
		PartDefinition layar = tiang.addOrReplaceChild("layar", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, boom, 0.0F, 0.0F, -Mth.HALF_PI,
				0.0F));
		for (int i = 0; i < PANEL; i++) {
			// panel i hangs from its own pivot at its bottom edge; the renderer stacks them
			layar.addOrReplaceChild("panel_" + i, p(LAYAR, (i * 41) % 200).addBox(depan, -tinggiPanel, -0.5F, panjang, tinggiPanel, 1.0F),
					PartPose.offset(0.0F, -tinggiPanel * i, 0.0F));
		}
		for (int i = 0; i <= PANEL; i++) {
			layar.addOrReplaceChild("bilah_" + i, p(BAMBU, (i * 29) % 64).addBox(depan - 2.0F, -1.0F, -1.0F, panjang + 4.0F, 2.0F, 2.0F),
					PartPose.offset(0.0F, -tinggiPanel * i, 0.0F));
		}
		// the sheet ropes from the batten ends to the deck (fan of four)
		for (int i = 0; i < 4; i++) {
			float k = i * 2.0F + 1.0F;
			layar.addOrReplaceChild("tali_" + i, p(TALI, i * 13).addBox(0.0F, -0.3F, -0.3F, tinggiPanel * k * 0.6F, 0.6F, 0.6F),
					PartPose.offsetAndRotation(depan + panjang, -tinggiPanel * k * 0.5F, 0.0F, 0.0F, 0.0F, 1.1F));
		}
		// masthead streamer (three segments) and the pirate flag
		PartDefinition panji = tiang.addOrReplaceChild("panji", CubeListBuilder.create(), PartPose.offset(0.0F, -tinggi + 6.0F, 0.0F));
		PartDefinition seg = panji;
		for (int i = 0; i < 3; i++) {
			float len = bajak ? 12.0F : 10.0F - i * 2.0F;
			float h = bajak ? 20.0F - i * 2.0F : 4.0F - i;
			seg = seg.addOrReplaceChild("kain_" + i, p(PANJI, i * (bajak ? 12 : 10)).addBox(0.0F, 0.0F, -0.25F, len, h, 0.5F),
					PartPose.offset(i == 0 ? tebal / 2.0F : (bajak ? 12.0F : 12.0F - i * 2.0F), 0.0F, 0.0F));
		}
		tiang.addOrReplaceChild("puncak", p(CAT, 3).addBox(-tebal / 2.0F - 1.0F, -tinggi - 4.0F, -tebal / 2.0F - 1.0F, tebal + 2.0F, 4.0F, tebal + 2.0F),
				PartPose.ZERO);
	}

	/** The junk's model: heave, roll, heel, furling panels, anchor, gangplank, rudder, streamers. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart kapal;
		private final ModelPart kemudi;
		private final ModelPart jangkar;
		private final ModelPart rantai;
		private final ModelPart mata;
		private final ModelPart titian;
		private final ModelPart[] muatan = new ModelPart[PETI_POS.length];
		private final ModelPart[] layar = new ModelPart[3];
		private final ModelPart[][] panel = new ModelPart[3][PANEL];
		private final ModelPart[][] bilah = new ModelPart[3][PANEL + 1];
		private final ModelPart[][] kain = new ModelPart[3][3];
		private final ModelPart[][] tali = new ModelPart[3][4];
		private final float[] tinggiPanel = {24.0F, 18.0F, 15.0F};

		public Model(ModelPart root) {
			super(root);
			this.kapal = root.getChild("kapal");
			this.kemudi = kapal.getChild("kemudi");
			this.jangkar = kapal.getChild("jangkar");
			this.rantai = jangkar.getChild("rantai");
			this.mata = jangkar.getChild("mata");
			this.titian = kapal.getChild("titian");
			ModelPart m = kapal.getChild("muatan");
			for (int i = 0; i < PETI_POS.length; i++) {
				muatan[i] = m.getChild("peti_" + i);
			}
			String[] ids = {"utama", "depan", "belakang"};
			for (int s = 0; s < 3; s++) {
				ModelPart tiang = kapal.getChild("tiang_" + ids[s]);
				layar[s] = tiang.getChild("layar");
				for (int i = 0; i < PANEL; i++) {
					panel[s][i] = layar[s].getChild("panel_" + i);
				}
				for (int i = 0; i <= PANEL; i++) {
					bilah[s][i] = layar[s].getChild("bilah_" + i);
				}
				for (int i = 0; i < 4; i++) {
					tali[s][i] = layar[s].getChild("tali_" + i);
				}
				ModelPart seg = tiang.getChild("panji");
				for (int i = 0; i < 3; i++) {
					seg = seg.getChild("kain_" + i);
					kain[s][i] = seg;
				}
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			kapal.y += -state.naik;
			kapal.zRot = state.oleng + state.miring;
			kapal.xRot = state.angguk;
			kemudi.yRot = state.kemudi;
			// anchor: the chain grows from the hawse while the anchor drops 48 px (3 blocks) below the waterline
			float drop = 8.0F + state.jangkar * 64.0F;
			mata.y = drop;
			rantai.xScale = Math.max(0.05F, drop / 64.0F);
			mata.zRot = state.jauh ? 0.0F : Mth.sin(state.waktu * 0.05F + state.fase) * 0.05F * (1.0F - state.jangkar * 0.6F);
			// gangplank: stowed across the bulwarks (180 degrees), swung over to lie out on the quay (+12 degrees)
			titian.zRot = Mth.lerp(sinus(state.titian), Mth.PI, rad(12.0F));
			for (int i = 0; i < muatan.length; i++) {
				muatan[i].visible = i < state.peti;
			}
			for (int s = 0; s < 3; s++) {
				float h = tinggiPanel[s];
				layar[s].yRot = -Mth.HALF_PI + state.set * (s == 1 ? 0.8F : 1.0F);
				// stack the panels from the boom: panel i shrinks to 2.5 px as it folds
				float y = 0.0F;
				for (int i = 0; i < PANEL; i++) {
					float f = state.panel[i];
					float hh = Mth.lerp(f, h, 2.5F);
					panel[s][i].y = y;
					panel[s][i].yScale = hh / h;
					bilah[s][i].y = y;
					y -= hh;
				}
				bilah[s][PANEL].y = y;
				for (int i = 0; i < 4; i++) {
					tali[s][i].visible = state.layar < 0.5F;
				}
				// streamers flutter (frozen far away)
				for (int i = 0; i < 3; i++) {
					kain[s][i].yRot = state.jauh ? 0.15F : Mth.sin(state.kibar + i * 0.9F + s) * (0.25F + i * 0.12F);
				}
			}
		}
	}
}
