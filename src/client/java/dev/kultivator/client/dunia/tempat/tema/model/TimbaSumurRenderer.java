package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.oasis.TimbaSumurEntity;
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
 * Timba sumur bertuas ({@code kultivator:timba_sumur}, DESIGN 38.T.9.3): the oasis shaduf. A mud-brick plinth, a forked
 * palm-wood post (6 x 48 x 6) with an iron pin, the sweep beam (96 px) pivoted a third from its end, a lashed stone
 * counterweight (12 px) on the short arm and a hooped wooden bucket (8 px) on a rope that always hangs plumb from the
 * long arm. Drawing water ({@link TimbaSumurEntity#KEADAAN_TIMBA}) the beam tips +-25 degrees over the
 * {@link TimbaSumurEntity#SIKLUS}-tick cycle in step with NPC clip 89 {@code timba_sumur} (hands high at t0, the bucket in
 * the well at t30); idle, the sweep rests long arm up and sways a little. Texture {@code timba_sumur.png} (64 x 160,
 * bands of 32).
 */
public class TimbaSumurRenderer extends TemaBendaRenderer<TimbaSumurEntity, TimbaSumurRenderer.State, TimbaSumurRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("timba_sumur"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/timba_sumur.png");

	static final int B = 32;
	static final int KAYU = 0;
	static final int BATU = 1;
	static final int TALI = 2;
	static final int EMBER = 3;
	static final int BATA = 4;

	/** Rope length from the long arm's tip to the bucket handle (model units). */
	static final float TALI_PANJANG = 26.0F;

	public static class State extends TemaBendaRenderer.State {
		public float balok;
		public float ayun;
	}

	public TimbaSumurRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEX, null, 0.5F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, TimbaSumurRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(TimbaSumurEntity entity) {
		return 4.0D;
	}

	@Override
	public void extractRenderState(TimbaSumurEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		if (state.keadaan == TimbaSumurEntity.KEADAAN_TIMBA) {
			float p = (state.umur % TimbaSumurEntity.SIKLUS) / TimbaSumurEntity.SIKLUS;
			float down = 0.5F - 0.5F * Mth.cos(p * Mth.TWO_PI);
			// positive xRot tips the long (front, -z) arm down
			state.balok = Mth.lerp(down, -rad(25.0F), rad(25.0F));
			state.ayun = state.jauh ? 0.0F : Mth.sin(p * Mth.TWO_PI * 2.0F) * 0.12F;
		} else {
			state.balok = -rad(18.0F) + (state.jauh ? 0.0F : Mth.sin(state.waktu * 0.03F + state.fase) * rad(1.5F));
			state.ayun = state.jauh ? 0.0F : Mth.sin(state.waktu * 0.05F + state.fase) * 0.06F;
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
		// mud-brick plinth in two courses
		root.addOrReplaceChild("alas", p(BATA).addBox(-8.0F, 18.0F, -8.0F, 16.0F, 6.0F, 16.0F).addBox(-6.0F, 14.0F, -6.0F, 12.0F, 4.0F, 12.0F), PartPose.ZERO);
		// the post with its fork, braces and iron pin (posts are built along x and stood up)
		root.addOrReplaceChild("tiang", p(KAYU).addBox(0.0F, -3.0F, -3.0F, 48.0F, 6.0F, 6.0F), PartPose.offsetAndRotation(0.0F, 14.0F, 0.0F, 0.0F, 0.0F,
				-Mth.HALF_PI));
		root.addOrReplaceChild("garpu", p(KAYU, 20).addBox(-4.0F, -40.0F, -1.0F, 2.0F, 8.0F, 2.0F).addBox(2.0F, -40.0F, -1.0F, 2.0F, 8.0F, 2.0F)
				.addBox(-4.0F, -34.0F, -3.5F, 8.0F, 2.0F, 7.0F), PartPose.ZERO);
		root.addOrReplaceChild("pasak", p(EMBER, 40).addBox(-5.0F, -38.5F, -0.7F, 10.0F, 1.4F, 1.4F), PartPose.ZERO);
		for (int side = -1; side <= 1; side += 2) {
			// raked braces from the plinth corners to the post
			root.addOrReplaceChild("penguat_" + (side < 0 ? "k" : "n"), p(KAYU, 30).addBox(-1.0F, -15.0F, -1.0F, 2.0F, 15.0F, 2.0F),
					PartPose.offsetAndRotation(0.0F, 14.0F, side * 7.0F, side * 0.5F, 0.0F, 0.0F));
		}
		// the sweep: pivot on the pin, short arm to the back (+z) with the stone, long arm forward (-z) with the rope
		PartDefinition balok = root.addOrReplaceChild("balok", CubeListBuilder.create(), PartPose.offset(0.0F, -38.0F, 0.0F));
		balok.addOrReplaceChild("batang", p(KAYU, 10).addBox(-64.0F, -2.0F, -2.0F, 96.0F, 4.0F, 4.0F)
				.addBox(-62.0F, -2.6F, -1.0F, 4.0F, 0.6F, 2.0F), PartPose.rotation(0.0F, -Mth.HALF_PI, 0.0F));
		PartDefinition beban = balok.addOrReplaceChild("pemberat", p(BATU).addBox(-6.0F, -4.0F, -6.0F, 12.0F, 12.0F, 12.0F)
				.addBox(-4.0F, 8.0F, -4.0F, 8.0F, 3.0F, 8.0F), PartPose.offset(0.0F, 2.0F, 26.0F));
		beban.addOrReplaceChild("ikat", p(TALI).addBox(-6.4F, -2.0F, -1.0F, 12.8F, 9.0F, 2.0F).addBox(-1.0F, -2.0F, -6.4F, 2.0F, 9.0F, 12.8F)
				.addBox(-1.0F, -5.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.ZERO);
		PartDefinition ujung = balok.addOrReplaceChild("ujung", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, -62.0F));
		ujung.addOrReplaceChild("tali", p(TALI, 20).addBox(-0.5F, 0.0F, -0.5F, 1.0F, TALI_PANJANG, 1.0F), PartPose.ZERO);
		PartDefinition ember = ujung.addOrReplaceChild("ember", p(EMBER).addBox(-4.0F, 2.0F, -4.0F, 8.0F, 8.0F, 8.0F)
				.addBox(-4.4F, 3.0F, -4.4F, 8.8F, 1.0F, 8.8F).addBox(-4.4F, 8.0F, -4.4F, 8.8F, 1.0F, 8.8F), PartPose.offset(0.0F, TALI_PANJANG, 0.0F));
		ember.addOrReplaceChild("pegangan", p(EMBER, 30).addBox(-4.2F, -1.0F, -0.4F, 0.8F, 3.0F, 0.8F).addBox(3.4F, -1.0F, -0.4F, 0.8F, 3.0F, 0.8F)
				.addBox(-4.2F, -1.4F, -0.4F, 8.4F, 0.8F, 0.8F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, B * 5);
	}

	/** The shaduf's model: the tipping sweep and the plumb rope. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart balok;
		private final ModelPart ujung;

		public Model(ModelPart root) {
			super(root);
			this.balok = root.getChild("balok");
			this.ujung = balok.getChild("ujung");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			balok.xRot = state.balok;
			// the rope stays plumb: undo the beam's tilt, plus the swing of the load
			ujung.xRot = -state.balok + state.ayun;
			ujung.zRot = state.ayun * 0.5F;
		}
	}
}
