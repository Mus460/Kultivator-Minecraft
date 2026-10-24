package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.gunung.HerbaTebingEntity;
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
 * Herba tebing ({@code kultivator:herba_tebing}, DESIGN 38.T.9.3 / 38.T.6.4): a glinting cliff herb in a niche or by
 * the plank walk. Two crossed leaf planes (12 x 16 x 12, cutout), a tuft of four short blades, a mossy root clump and
 * three glowing buds on stalks. It sways in the wind ({@code nilai} plus the town's gusts); picked ({@link
 * HerbaTebingEntity#KEADAAN_DIPETIK}) it shrinks away in 10 ticks. Variant {@link HerbaTebingEntity#VARIAN_RUMPUT_AWAN}:
 * the silvery thousand-year cloud grass with pale blue buds. Textures {@code herba_tebing.png},
 * {@code herba_tebing_awan.png} (64 x 64, bands of 16) + glow.
 */
public class HerbaTebingRenderer extends TemaBendaRenderer<HerbaTebingEntity, HerbaTebingRenderer.State, HerbaTebingRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("herba_tebing"), "main");

	static final int B = 16;
	static final int DAUN = 0;
	static final int TANAH = 1;
	static final int KUNCUP = 2;
	static final int BATANG = 3;

	public static class State extends TemaBendaRenderer.State {
		public float goyang;
		public float skala = 1.0F;
	}

	public HerbaTebingRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), new Identifier[] {tex("herba_tebing"), tex("herba_tebing_awan")},
				new Identifier[] {tex("herba_tebing_pancar"), tex("herba_tebing_awan_pancar")}, 0.0F);
	}

	private static Identifier tex(String name) {
		return Kultivator.id("textures/entity/tempat/tema/" + name + ".png");
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, HerbaTebingRenderer::mesh);
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
	protected int tekstur(State state) {
		return state.varian == HerbaTebingEntity.VARIAN_RUMPUT_AWAN ? 1 : 0;
	}

	@Override
	public void extractRenderState(HerbaTebingEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		float wind = 0.4F + 0.6F * Mth.clamp(state.nilai, 0.0F, 1.0F);
		float gust = Mth.sin(state.waktu * 0.013F) * 0.5F + 0.5F;
		state.goyang = state.jauh ? 0.0F : (Mth.sin(state.waktu * 0.12F + state.fase) * 0.12F + Mth.sin(state.waktu * 0.31F + state.fase) * 0.05F)
				* wind * (0.6F + 0.8F * gust);
		state.skala = state.keadaan == HerbaTebingEntity.KEADAAN_DIPETIK ? Math.max(0.0F, 1.0F - maju(state.umur, 0.0F, 10.0F)) : 1.0F;
		state.pancar = (0.55F + 0.45F * Mth.sin(state.waktu * 0.07F + state.fase)) * state.skala;
		if (state.skala <= 0.0F) {
			state.alpha = 0.0F;
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
		PartDefinition tanaman = root.addOrReplaceChild("tanaman", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
		tanaman.addOrReplaceChild("akar", p(TANAH).addBox(-4.0F, -2.0F, -4.0F, 8.0F, 2.0F, 8.0F).addBox(-2.5F, -3.0F, -2.0F, 5.0F, 1.0F, 5.0F), PartPose.ZERO);
		PartDefinition daun = tanaman.addOrReplaceChild("daun", CubeListBuilder.create(), PartPose.offset(0.0F, -2.0F, 0.0F));
		// two crossed planes (zero-thickness boxes show both faces in cutout)
		daun.addOrReplaceChild("silang_a", p(DAUN).addBox(-6.0F, -14.0F, 0.0F, 12.0F, 14.0F, 0.0F), PartPose.rotation(0.0F, Mth.PI / 4.0F, 0.0F));
		daun.addOrReplaceChild("silang_b", p(DAUN, 24).addBox(-6.0F, -14.0F, 0.0F, 12.0F, 14.0F, 0.0F), PartPose.rotation(0.0F, -Mth.PI / 4.0F, 0.0F));
		daun.addOrReplaceChild("rumpun", p(DAUN, 48).addBox(-3.0F, -6.0F, 0.0F, 6.0F, 6.0F, 0.0F), PartPose.rotation(0.0F, 0.2F, 0.0F));
		float[][] kuncup = {{-2.0F, -13.0F, 1.0F, 0.12F}, {2.5F, -15.0F, -1.0F, -0.1F}, {0.5F, -11.0F, 2.5F, 0.05F}};
		for (int i = 0; i < kuncup.length; i++) {
			float[] k = kuncup[i];
			PartDefinition tangkai = daun.addOrReplaceChild("tangkai_" + i, p(BATANG, i * 6).addBox(-0.3F, k[1], -0.3F, 0.6F, -k[1], 0.6F),
					PartPose.offsetAndRotation(k[0], 0.0F, k[2], k[3], 0.0F, -k[3]));
			tangkai.addOrReplaceChild("kuncup", p(KUNCUP, i * 8).addBox(-1.0F, k[1] - 2.0F, -1.0F, 2.0F, 2.4F, 2.0F)
					.addBox(-0.6F, k[1] - 2.6F, -0.6F, 1.2F, 0.6F, 1.2F), PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 64, B * 4);
	}

	/** The herb's model: the sway and the picking. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart tanaman;
		private final ModelPart daun;

		public Model(ModelPart root) {
			super(root);
			this.tanaman = root.getChild("tanaman");
			this.daun = tanaman.getChild("daun");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			daun.zRot = state.goyang;
			daun.xRot = state.goyang * 0.5F;
			float s = state.skala;
			tanaman.xScale = s;
			tanaman.yScale = s;
			tanaman.zScale = s;
		}
	}
}
