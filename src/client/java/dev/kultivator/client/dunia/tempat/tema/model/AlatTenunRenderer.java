package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.air.AlatTenunEntity;
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
 * Alat tenun ({@code kultivator:alat_tenun}, DESIGN 38.T.9.3): a floor loom of the Rumah Sutra Su. A teak frame 40 x 36 x
 * 24 (four posts, side rails, the castle beam on top), the warp beam at the back and the cloth beam at the front, the
 * sheet of warp threads between them (cutout), two heddle frames hanging from the castle on cords, the beater (reed in
 * its swinging frame, pivoting at the floor), the boat shuttle, two treadles and the weaver's bench. Weaving ({@link
 * AlatTenunEntity#KEADAAN_TENUN}) runs in step with NPC clip 90 {@code tenun} (loop 20): the treadles and heddles
 * alternate every 10 ticks, the shuttle flies across at each change and the beater swings 15 degrees to beat the pick
 * in; the cloth roll grows with {@code nilai}. Textures {@code alat_tenun.png}, {@code alat_tenun_merah.png} (64 x 192,
 * bands of 32).
 */
public class AlatTenunRenderer extends TemaBendaRenderer<AlatTenunEntity, AlatTenunRenderer.State, AlatTenunRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("alat_tenun"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/alat_tenun.png");
	private static final Identifier TEX_MERAH = Kultivator.id("textures/entity/tempat/tema/alat_tenun_merah.png");

	static final int B = 32;
	static final int KAYU = 0;
	static final int BENANG = 1;
	static final int KAIN = 2;
	static final int BAMBU = 3;
	static final int TALI = 4;
	static final int SISIR = 5;

	public static class State extends TemaBendaRenderer.State {
		public float sisir;
		public float teropong;
		public float injak;
		public float gulung;
	}

	public AlatTenunRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), new Identifier[] {TEX, TEX_MERAH}, null, 0.6F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, AlatTenunRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(AlatTenunEntity entity) {
		return 1.5D;
	}

	@Override
	protected int tekstur(State state) {
		return state.varian == AlatTenunEntity.VARIAN_MERAH ? 1 : 0;
	}

	@Override
	public void extractRenderState(AlatTenunEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.gulung = 0.35F + 0.65F * Mth.clamp(state.nilai, 0.0F, 1.0F);
		if (state.keadaan != AlatTenunEntity.KEADAAN_TENUN || state.jauh) {
			state.sisir = 0.0F;
			state.teropong = -1.0F;
			state.injak = 0.0F;
			return;
		}
		float t = state.umur % 20.0F;
		float half = t % 10.0F;
		// shuttle flies across in the first 4 ticks of each half cycle, side alternating
		float fly = sinus(maju(half, 0.0F, 4.0F));
		state.teropong = t < 10.0F ? -1.0F + 2.0F * fly : 1.0F - 2.0F * fly;
		// beater: back (toward the weaver) at ticks 5-8 of each half
		float beat = half < 5.0F ? 0.0F : Mth.sin(maju(half, 5.0F, 4.0F) * Mth.PI);
		state.sisir = beat * rad(15.0F);
		state.injak = t < 10.0F ? 1.0F : -1.0F;
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
		// frame: four posts (front two shorter), side rails, castle beam
		root.addOrReplaceChild("tiang", p(KAYU).addBox(-20.0F, 0.0F, -12.0F, 2.0F, 24.0F, 2.0F).addBox(18.0F, 0.0F, -12.0F, 2.0F, 24.0F, 2.0F)
				.addBox(-20.0F, 0.0F, 10.0F, 2.0F, 24.0F, 2.0F).addBox(18.0F, 0.0F, 10.0F, 2.0F, 24.0F, 2.0F), PartPose.ZERO);
		root.addOrReplaceChild("tiang_atas", p(KAYU, 10).addBox(-20.0F, -12.0F, -1.0F, 2.0F, 24.0F, 2.0F).addBox(18.0F, -12.0F, -1.0F, 2.0F, 24.0F, 2.0F),
				PartPose.ZERO);
		root.addOrReplaceChild("rel", p(KAYU, 20).addBox(-20.0F, 20.0F, -12.0F, 2.0F, 2.0F, 24.0F).addBox(18.0F, 20.0F, -12.0F, 2.0F, 2.0F, 24.0F)
				.addBox(-20.0F, 0.0F, -12.0F, 2.0F, 2.0F, 24.0F).addBox(18.0F, 0.0F, -12.0F, 2.0F, 2.0F, 24.0F), PartPose.ZERO);
		root.addOrReplaceChild("puri", p(KAYU, 30).addBox(-21.0F, -14.0F, -2.0F, 42.0F, 2.0F, 4.0F), PartPose.ZERO);
		// warp beam (back), breast beam and cloth beam (front)
		root.addOrReplaceChild("balok_lungsin", p(KAYU, 40).addBox(-18.0F, -1.5F, -1.5F, 36.0F, 3.0F, 3.0F), PartPose.offset(0.0F, 2.0F, 11.0F));
		root.addOrReplaceChild("balok_dada", p(KAYU, 50).addBox(-18.0F, -1.5F, -1.5F, 36.0F, 3.0F, 3.0F), PartPose.offset(0.0F, 2.0F, -11.0F));
		// the warp sheet from back beam to breast beam (thin cutout plate of threads)
		root.addOrReplaceChild("lungsin", p(BENANG).addBox(-16.0F, 0.0F, -11.0F, 32.0F, 0.2F, 22.0F), PartPose.offset(0.0F, 0.4F, 0.0F));
		// the cloth roll on the front lower beam: six staves around an axle; scaled with the progress
		PartDefinition gulungan = root.addOrReplaceChild("gulungan", p(KAYU, 60).addBox(-18.0F, -1.0F, -1.0F, 36.0F, 2.0F, 2.0F),
				PartPose.offset(0.0F, 14.0F, -10.0F));
		PartDefinition kain = gulungan.addOrReplaceChild("kain", CubeListBuilder.create(), PartPose.ZERO);
		for (int i = 0; i < 6; i++) {
			kain.addOrReplaceChild("bilah_" + i, p(KAIN, i * 9).addBox(-16.0F, -3.0F, -1.25F, 32.0F, 1.2F, 2.5F), PartPose.rotation(i * Mth.PI / 3.0F, 0.0F,
					0.0F));
		}
		root.addOrReplaceChild("kain_naik", p(KAIN, 50).addBox(-16.0F, 0.0F, -0.1F, 32.0F, 10.0F, 0.2F), PartPose.offsetAndRotation(0.0F, 3.0F, -12.0F, -0.15F,
				0.0F, 0.0F));
		// two heddle frames hanging on cords from the castle
		for (int i = 0; i < 2; i++) {
			PartDefinition g = root.addOrReplaceChild("gun_" + i, p(BAMBU, i * 20).addBox(-17.0F, -1.0F, -0.5F, 34.0F, 1.0F, 1.0F)
					.addBox(-17.0F, 7.0F, -0.5F, 34.0F, 1.0F, 1.0F).addBox(-17.0F, -1.0F, -0.5F, 1.0F, 9.0F, 1.0F).addBox(16.0F, -1.0F, -0.5F, 1.0F, 9.0F, 1.0F),
					PartPose.offset(0.0F, -5.0F, 2.0F + i * 3.0F));
			g.addOrReplaceChild("tali", p(TALI, i * 10).addBox(-12.0F, -4.0F, -0.3F, 0.6F, 4.0F, 0.6F).addBox(11.4F, -4.0F, -0.3F, 0.6F, 4.0F, 0.6F),
					PartPose.ZERO);
			g.addOrReplaceChild("benang", p(BENANG, 10 + i * 6).addBox(-15.0F, 0.0F, -0.1F, 30.0F, 7.0F, 0.2F), PartPose.ZERO);
		}
		// beater: two swords pivoting at the floor rail, the reed and the top bar (handle)
		PartDefinition sisir = root.addOrReplaceChild("sisir", p(KAYU, 70).addBox(-17.5F, -22.0F, -1.0F, 1.5F, 22.0F, 2.0F)
				.addBox(16.0F, -22.0F, -1.0F, 1.5F, 22.0F, 2.0F), PartPose.offset(0.0F, 22.0F, -4.0F));
		sisir.addOrReplaceChild("buluh", p(SISIR).addBox(-16.0F, -21.0F, -0.4F, 32.0F, 4.0F, 0.8F), PartPose.ZERO);
		sisir.addOrReplaceChild("pegangan", p(KAYU, 80).addBox(-18.0F, -24.0F, -1.5F, 36.0F, 3.0F, 3.0F), PartPose.ZERO);
		// boat shuttle with its pirn
		PartDefinition teropong = root.addOrReplaceChild("teropong", p(BAMBU, 50).addBox(-4.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F)
				.addBox(-5.0F, -0.6F, -0.6F, 1.0F, 1.2F, 1.2F).addBox(4.0F, -0.6F, -0.6F, 1.0F, 1.2F, 1.2F), PartPose.offset(0.0F, -0.6F, -3.0F));
		teropong.addOrReplaceChild("palet", p(KAIN, 70).addBox(-2.0F, -1.3F, -0.6F, 4.0F, 0.4F, 1.2F), PartPose.ZERO);
		// two treadles hinged at the back
		for (int i = 0; i < 2; i++) {
			root.addOrReplaceChild("pedal_" + i, p(KAYU, 90 + i * 6).addBox(-2.0F, -0.5F, -20.0F, 4.0F, 1.0F, 20.0F), PartPose.offset(-5.0F + i * 10.0F,
					22.5F, 10.0F));
		}
		// the weaver's bench in front
		root.addOrReplaceChild("bangku", p(KAYU, 100).addBox(-12.0F, 12.0F, -24.0F, 24.0F, 2.0F, 7.0F).addBox(-11.0F, 14.0F, -23.0F, 2.0F, 10.0F, 2.0F)
				.addBox(9.0F, 14.0F, -23.0F, 2.0F, 10.0F, 2.0F).addBox(-11.0F, 14.0F, -20.0F, 2.0F, 10.0F, 2.0F)
				.addBox(9.0F, 14.0F, -20.0F, 2.0F, 10.0F, 2.0F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, B * 6);
	}

	/** The loom's model: beater, shuttle, heddles, treadles, the growing roll. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart sisir;
		private final ModelPart teropong;
		private final ModelPart kain;
		private final ModelPart[] gun = new ModelPart[2];
		private final ModelPart[] pedal = new ModelPart[2];

		public Model(ModelPart root) {
			super(root);
			this.sisir = root.getChild("sisir");
			this.teropong = root.getChild("teropong");
			this.kain = root.getChild("gulungan").getChild("kain");
			for (int i = 0; i < 2; i++) {
				gun[i] = root.getChild("gun_" + i);
				pedal[i] = root.getChild("pedal_" + i);
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			// the beater swings toward the weaver (-z): negative xRot about its floor pivot
			sisir.xRot = -state.sisir;
			teropong.x = state.teropong * 15.0F;
			for (int i = 0; i < 2; i++) {
				float down = (i == 0 ? state.injak : -state.injak) > 0.0F ? 1.0F : 0.0F;
				pedal[i].xRot = -down * 0.12F;
				gun[i].y += down * 2.0F - 1.0F * Math.abs(state.injak);
			}
			float g = state.gulung;
			kain.yScale = g;
			kain.zScale = g;
		}
	}
}
