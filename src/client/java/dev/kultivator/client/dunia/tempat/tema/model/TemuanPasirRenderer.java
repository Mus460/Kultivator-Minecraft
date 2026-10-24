package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.oasis.TemuanPasirEntity;
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
 * Temuan pasir ({@code kultivator:temuan_pasir}, DESIGN 38.T.9.3 / 38.T.4.6): a find half buried in a dune after the
 * sandstorm, in three looks: an ancient square-holed coin (6 x 1 x 6) tipped on its edge beside two smaller ones, a
 * painted mural shard (10 x 8 x 1) leaning out of the sand, and a bronze map plate (8 x 1 x 10) with an engraved
 * route. Each sits in a small wind-ribbed sand drift. It glints every {@link TemuanPasirEntity#KILAU_PERIODE} ticks
 * ({@code nilai}, emissive pass) and, once taken ({@link TemuanPasirEntity#KEADAAN_DIAMBIL}), rises a little and fades
 * over 20 ticks. Texture {@code temuan_pasir.png} (64 x 64, bands of 16) + glow.
 */
public class TemuanPasirRenderer extends TemaBendaRenderer<TemuanPasirEntity, TemuanPasirRenderer.State, TemuanPasirRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("temuan_pasir"), "main");
	private static final Identifier TEX = tex("temuan_pasir");
	private static final Identifier GLOW = tex("temuan_pasir_pancar");

	static final int B = 16;
	static final int EMAS = 0;
	static final int MURAL = 1;
	static final int PERUNGGU = 2;
	static final int PASIR = 3;

	public static class State extends TemaBendaRenderer.State {
		public float angkat;
	}

	public TemuanPasirRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEX, GLOW, 0.0F);
	}

	private static Identifier tex(String name) {
		return Kultivator.id("textures/entity/tempat/tema/" + name + ".png");
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, TemuanPasirRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double batas() {
		return 64.0D;
	}

	@Override
	public void extractRenderState(TemuanPasirEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		// a sharp glint once per period, plus the synced strength
		float p = (state.waktu + state.fase * 10.0F) % TemuanPasirEntity.KILAU_PERIODE;
		float spark = p < 8.0F ? Mth.sin(p / 8.0F * Mth.PI) : 0.0F;
		state.pancar = Mth.clamp(0.15F + spark * 0.85F, 0.0F, 1.0F) * Mth.clamp(0.4F + state.nilai, 0.0F, 1.0F);
		if (state.keadaan == TemuanPasirEntity.KEADAAN_DIAMBIL) {
			float k = maju(state.umur, 0.0F, 20.0F);
			state.angkat = keluar(k) * 6.0F;
			state.alpha = 1.0F - k;
		} else {
			state.angkat = 0.0F;
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
		// the drift: three overlapping low mounds with wind ribs
		PartDefinition pasir = root.addOrReplaceChild("pasir", p(PASIR).addBox(-7.0F, 22.0F, -6.0F, 14.0F, 2.0F, 12.0F)
				.addBox(-5.0F, 21.0F, -4.0F, 9.0F, 1.0F, 8.0F).addBox(-2.0F, 20.4F, -2.5F, 5.0F, 0.6F, 5.0F), PartPose.ZERO);
		pasir.addOrReplaceChild("rusuk", p(PASIR, 30).addBox(-6.5F, 21.6F, 3.0F, 12.0F, 0.4F, 1.0F).addBox(-6.0F, 21.6F, -5.0F, 10.0F, 0.4F, 1.0F),
				PartPose.ZERO);
		// coin look: the big coin on edge, two small ones flat
		PartDefinition koin = root.addOrReplaceChild("koin", CubeListBuilder.create(), PartPose.ZERO);
		koin.addOrReplaceChild("besar", p(EMAS).addBox(-3.0F, -3.0F, -0.5F, 6.0F, 6.0F, 1.0F).addBox(-3.4F, -2.0F, -0.4F, 0.4F, 4.0F, 0.8F)
				.addBox(3.0F, -2.0F, -0.4F, 0.4F, 4.0F, 0.8F).addBox(-2.0F, -3.4F, -0.4F, 4.0F, 0.4F, 0.8F), PartPose.offsetAndRotation(0.5F, 20.2F, 0.0F, 0.35F,
				0.4F, 0.15F));
		koin.addOrReplaceChild("kecil", p(EMAS, 20).addBox(2.0F, 20.0F, 2.0F, 3.0F, 0.6F, 3.0F).addBox(-4.5F, 20.8F, -1.0F, 3.0F, 0.6F, 3.0F), PartPose.ZERO);
		// mural shard leaning out of the sand
		root.addOrReplaceChild("mural", p(MURAL).addBox(-5.0F, -8.0F, -0.5F, 10.0F, 8.0F, 1.0F).addBox(-4.0F, -9.0F, -0.5F, 5.0F, 1.0F, 1.0F),
				PartPose.offsetAndRotation(0.0F, 23.0F, 0.5F, -0.45F, 0.25F, 0.08F));
		// bronze map plate, half buried at a tilt
		root.addOrReplaceChild("peta", p(PERUNGGU).addBox(-4.0F, -0.5F, -5.0F, 8.0F, 1.0F, 10.0F).addBox(-4.4F, -0.7F, -5.4F, 8.8F, 0.4F, 1.0F),
				PartPose.offsetAndRotation(0.0F, 21.2F, 0.0F, 0.3F, -0.3F, 0.12F));
		return LayerDefinition.create(mesh, 64, B * 4);
	}

	/** The find's model: which look, the lift when taken. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart koin;
		private final ModelPart mural;
		private final ModelPart peta;

		public Model(ModelPart root) {
			super(root);
			this.koin = root.getChild("koin");
			this.mural = root.getChild("mural");
			this.peta = root.getChild("peta");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			koin.visible = state.varian == TemuanPasirEntity.VARIAN_KOIN;
			mural.visible = state.varian == TemuanPasirEntity.VARIAN_MURAL;
			peta.visible = state.varian == TemuanPasirEntity.VARIAN_PETA;
			koin.y -= state.angkat;
			mural.y -= state.angkat;
			peta.y -= state.angkat;
		}
	}
}
