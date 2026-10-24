package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.gunung.TaliTurunEntity;
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
 * Tali turun ({@code kultivator:tali_turun}, DESIGN 38.T.9.3 / 38.T.6.4 Lift Macet): the climbing rope the lift keeper
 * drops from a stuck cabin. A hemp rope (1 x 1 x n) hanging {@code nilai} blocks below its knot, with a climbing knot at
 * every block and a weighted end bag. Dropped ({@link TaliTurunEntity#KEADAAN_TURUN}) it unrolls downward in 16 ticks
 * with a whip at the end and then sways; pulled up ({@link TaliTurunEntity#KEADAAN_DITARIK}) it shortens to nothing in
 * 20 ticks. Texture {@code tali_turun.png} (32 x 64, bands of 16).
 */
public class TaliTurunRenderer extends TemaBendaRenderer<TaliTurunEntity, TaliTurunRenderer.State, TaliTurunRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("tali_turun"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/tali_turun.png");

	static final int B = 16;
	static final int TALI = 0;
	static final int SIMPUL = 1;
	static final int KARUNG = 2;

	/** Knots drawn (one per block); longer ropes show the first 40. */
	static final int SIMPUL_N = 40;

	public static class State extends TemaBendaRenderer.State {
		public float panjang;
		public float ayun;
	}

	public TaliTurunRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEX, null, 0.0F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, TaliTurunRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(TaliTurunEntity entity) {
		return Math.max(1.0D, entity.nilai());
	}

	@Override
	public void extractRenderState(TaliTurunEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		float full = Math.max(0.5F, state.nilai) * 16.0F;
		float k = state.keadaan == TaliTurunEntity.KEADAAN_DITARIK ? 1.0F - sinus(maju(state.umur, 0.0F, 20.0F)) : keluar(maju(state.umur, 0.0F, 16.0F));
		state.panjang = Math.max(0.5F, full * k);
		float whip = state.keadaan == TaliTurunEntity.KEADAAN_TURUN ? redam(state.umur - 16.0F, 40.0F, 0.35F) * 0.15F : 0.0F;
		state.ayun = state.jauh ? 0.0F : Mth.sin(state.waktu * 0.06F + state.fase) * 0.03F + whip;
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
		// the rope hangs from the entity's position (its top knot at y 24 - 1 block) downward
		PartDefinition ikat = root.addOrReplaceChild("ikat", p(SIMPUL).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offset(0.0F, 8.0F, 0.0F));
		ikat.addOrReplaceChild("tali", p(TALI).addBox(0.0F, -0.6F, -0.6F, 16.0F, 1.2F, 1.2F), PartPose.rotation(0.0F, 0.0F, Mth.HALF_PI));
		for (int i = 0; i < SIMPUL_N; i++) {
			ikat.addOrReplaceChild("simpul_" + i, p(SIMPUL, i % 4 * 6).addBox(-1.1F, -0.8F, -1.1F, 2.2F, 1.6F, 2.2F), PartPose.offset(0.0F, 16.0F * (i + 1), 0.0F));
		}
		ikat.addOrReplaceChild("karung", p(KARUNG).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 4.0F, 4.0F).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 2.0F),
				PartPose.offset(0.0F, 16.0F, 0.0F));
		return LayerDefinition.create(mesh, 32, B * 4);
	}

	/** The rope's model: length, knots, the end bag and the sway. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart ikat;
		private final ModelPart tali;
		private final ModelPart karung;
		private final ModelPart[] simpul = new ModelPart[SIMPUL_N];

		public Model(ModelPart root) {
			super(root);
			this.ikat = root.getChild("ikat");
			this.tali = ikat.getChild("tali");
			this.karung = ikat.getChild("karung");
			for (int i = 0; i < SIMPUL_N; i++) {
				simpul[i] = ikat.getChild("simpul_" + i);
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float len = state.panjang;
			tali.xScale = len / 16.0F;
			karung.y = len;
			for (int i = 0; i < SIMPUL_N; i++) {
				simpul[i].visible = 16.0F * (i + 1) < len - 4.0F;
			}
			ikat.xRot = state.ayun;
			ikat.zRot = state.ayun * 0.6F;
		}
	}
}
