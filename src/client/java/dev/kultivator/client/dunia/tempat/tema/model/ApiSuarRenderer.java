package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.pelabuhan.ApiSuarEntity;
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
 * Api Suar Pelita Angin ({@code kultivator:api_suar}, DESIGN 38.T.9.3): the lamp house of the harbour lighthouse. A brass
 * cage of four posts on a gallery plate with a railing, leaded glass panes on four sides, a three-tier verdigris roof
 * with a finial, and inside an octagonal ringed lens drum (two square drums turned 45 degrees) around a flame. Lit
 * ({@link ApiSuarEntity#KEADAAN_MENYALA}) the lens turns 3 degrees per tick and the flame and the lens rings glow with
 * {@code nilai}; the beams themselves are 38TV-F's {@code SinarSuarFx}. Three looks: cracked lens (dim, the start of the
 * Pelita chain), new lens, and the blue-gold Mutiara Fajar lens. Textures {@code api_suar_retak.png},
 * {@code api_suar.png}, {@code api_suar_fajar.png} (128 x 192, bands of 32) + glow.
 */
public class ApiSuarRenderer extends TemaBendaRenderer<ApiSuarEntity, ApiSuarRenderer.State, ApiSuarRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("api_suar"), "main");

	static final int B = 32;
	static final int KUNINGAN = 0;
	static final int KACA = 1;
	static final int LENSA = 2;
	static final int API = 3;
	static final int ATAP = 4;
	static final int BATU = 5;

	public static class State extends TemaBendaRenderer.State {
		public float lensa;
		public float lidah;
	}

	public ApiSuarRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)),
				new Identifier[] {tex("api_suar_retak"), tex("api_suar"), tex("api_suar_fajar")},
				new Identifier[] {tex("api_suar_retak_pancar"), tex("api_suar_pancar"), tex("api_suar_fajar_pancar")}, 0.0F);
	}

	private static Identifier tex(String name) {
		return Kultivator.id("textures/entity/tempat/tema/" + name + ".png");
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, ApiSuarRenderer::mesh);
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
	protected double reach(ApiSuarEntity entity) {
		return 1.0D;
	}

	@Override
	protected int tekstur(State state) {
		return Mth.clamp(state.varian, 0, 2);
	}

	@Override
	public void extractRenderState(ApiSuarEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		boolean lit = state.keadaan == ApiSuarEntity.KEADAAN_MENYALA;
		// all lamps of a town turn in step (game time), 3 degrees per tick
		state.lensa = lit ? state.waktu * rad(3.0F) : 0.0F;
		state.lidah = lit && !state.jauh ? Mth.sin(state.ageInTicks * 0.9F) * 0.08F + Mth.sin(state.ageInTicks * 2.3F) * 0.05F : 0.0F;
		float strength = Mth.clamp(state.nilai, 0.0F, 1.0F);
		if (state.varian == ApiSuarEntity.VARIAN_RETAK) {
			strength *= 0.45F;
		}
		state.pancar = lit ? 0.35F + 0.65F * strength : 0.0F;
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
		// gallery plate with a drip edge and a railing of 4 bars + 12 balusters
		root.addOrReplaceChild("lantai", p(BATU).addBox(-15.0F, 22.0F, -15.0F, 30.0F, 2.0F, 30.0F), PartPose.ZERO);
		root.addOrReplaceChild("bibir", p(KUNINGAN).addBox(-15.5F, 21.0F, -15.5F, 31.0F, 1.0F, 31.0F), PartPose.ZERO);
		PartDefinition pagar = root.addOrReplaceChild("pagar", CubeListBuilder.create(), PartPose.offset(0.0F, 21.0F, 0.0F));
		pagar.addOrReplaceChild("rel", p(KUNINGAN, 20).addBox(-15.5F, -7.0F, -15.5F, 31.0F, 1.0F, 1.0F).addBox(-15.5F, -7.0F, 14.5F, 31.0F, 1.0F, 1.0F)
				.addBox(-15.5F, -7.0F, -14.5F, 1.0F, 1.0F, 29.0F).addBox(14.5F, -7.0F, -14.5F, 1.0F, 1.0F, 29.0F), PartPose.ZERO);
		for (int i = 0; i < 12; i++) {
			int side = i / 3;
			float k = -10.0F + (i % 3) * 10.0F;
			float x = side == 0 ? k : side == 1 ? 15.0F : side == 2 ? -k : -15.0F;
			float z = side == 0 ? -15.0F : side == 1 ? k : side == 2 ? 15.0F : -k;
			pagar.addOrReplaceChild("tiang_" + i, p(KUNINGAN, i * 3).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 6.0F, 1.0F), PartPose.offset(x, 0.0F, z));
		}
		// the lamp house: four brass posts, a sill and a lintel ring, leaded glass panes
		PartDefinition rumah = root.addOrReplaceChild("rumah", CubeListBuilder.create(), PartPose.ZERO);
		for (int i = 0; i < 4; i++) {
			float x = i % 2 == 0 ? -12.0F : 10.0F;
			float z = i < 2 ? -12.0F : 10.0F;
			rumah.addOrReplaceChild("tiang_" + i, p(KUNINGAN, 40 + i * 4).addBox(0.0F, 0.0F, 0.0F, 2.0F, 22.0F, 2.0F), PartPose.offset(x, 0.0F, z));
		}
		rumah.addOrReplaceChild("ambang", p(KUNINGAN, 60).addBox(-12.0F, 19.0F, -12.0F, 24.0F, 2.0F, 24.0F), PartPose.ZERO);
		rumah.addOrReplaceChild("kusen", p(KUNINGAN, 80).addBox(-12.5F, -0.5F, -12.5F, 25.0F, 2.0F, 25.0F), PartPose.ZERO);
		rumah.addOrReplaceChild("kaca", p(KACA).addBox(-10.0F, 1.5F, -11.5F, 20.0F, 17.5F, 0.5F).addBox(-10.0F, 1.5F, 11.0F, 20.0F, 17.5F, 0.5F),
				PartPose.ZERO);
		rumah.addOrReplaceChild("kaca_sisi", p(KACA, 44).addBox(-10.0F, 1.5F, -11.5F, 20.0F, 17.5F, 0.5F).addBox(-10.0F, 1.5F, 11.0F, 20.0F, 17.5F, 0.5F),
				PartPose.rotation(0.0F, Mth.HALF_PI, 0.0F));
		// roof: three tiers, a drum and a finial with a spike
		PartDefinition atap = root.addOrReplaceChild("atap", CubeListBuilder.create(), PartPose.ZERO);
		atap.addOrReplaceChild("tingkat_0", p(ATAP).addBox(-14.0F, -3.0F, -14.0F, 28.0F, 3.0F, 28.0F), PartPose.ZERO);
		atap.addOrReplaceChild("tingkat_1", p(ATAP, 30).addBox(-10.0F, -6.0F, -10.0F, 20.0F, 3.0F, 20.0F), PartPose.ZERO);
		atap.addOrReplaceChild("tingkat_2", p(ATAP, 60).addBox(-6.0F, -9.0F, -6.0F, 12.0F, 3.0F, 12.0F), PartPose.ZERO);
		atap.addOrReplaceChild("tudung", p(KUNINGAN, 100).addBox(-3.0F, -11.0F, -3.0F, 6.0F, 2.0F, 6.0F)
				.addBox(-1.5F, -14.0F, -1.5F, 3.0F, 3.0F, 3.0F).addBox(-0.5F, -18.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.ZERO);
		atap.addOrReplaceChild("talang", p(KUNINGAN, 110).addBox(-14.5F, -0.5F, -14.5F, 29.0F, 0.5F, 29.0F), PartPose.ZERO);
		// burner stand and flame, and the turning lens drum (octagon of two squares)
		root.addOrReplaceChild("kaki_api", p(KUNINGAN, 70).addBox(-2.0F, 14.0F, -2.0F, 4.0F, 5.0F, 4.0F).addBox(-3.0F, 13.0F, -3.0F, 6.0F, 1.0F, 6.0F),
				PartPose.ZERO);
		root.addOrReplaceChild("api", p(API).addBox(-1.5F, -5.0F, -1.5F, 3.0F, 5.0F, 3.0F).addBox(-0.8F, -7.5F, -0.8F, 1.6F, 2.5F, 1.6F),
				PartPose.offset(0.0F, 13.0F, 0.0F));
		PartDefinition lensa = root.addOrReplaceChild("lensa", CubeListBuilder.create(), PartPose.offset(0.0F, 10.0F, 0.0F));
		lensa.addOrReplaceChild("a", p(LENSA).addBox(-6.0F, -8.0F, -6.0F, 12.0F, 16.0F, 12.0F), PartPose.ZERO);
		lensa.addOrReplaceChild("b", p(LENSA, 48).addBox(-6.0F, -7.5F, -6.0F, 12.0F, 15.0F, 12.0F), PartPose.rotation(0.0F, Mth.PI / 4.0F, 0.0F));
		lensa.addOrReplaceChild("cincin", p(KUNINGAN, 90).addBox(-6.6F, -8.6F, -6.6F, 13.2F, 1.0F, 13.2F).addBox(-6.6F, 7.6F, -6.6F, 13.2F, 1.0F, 13.2F),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, 128, B * 6);
	}

	/** The lamp's model: the turning lens and a flickering flame. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart lensa;
		private final ModelPart api;

		public Model(ModelPart root) {
			super(root);
			this.lensa = root.getChild("lensa");
			this.api = root.getChild("api");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			lensa.yRot = state.lensa;
			api.yScale = 1.0F + state.lidah;
			api.xScale = 1.0F - state.lidah * 0.5F;
			api.zScale = 1.0F - state.lidah * 0.5F;
			api.visible = state.keadaan == ApiSuarEntity.KEADAAN_MENYALA;
		}
	}
}
