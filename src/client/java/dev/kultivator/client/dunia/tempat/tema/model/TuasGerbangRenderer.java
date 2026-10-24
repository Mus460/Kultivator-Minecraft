package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.iblis.TuasGerbangEntity;
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
 * Tuas gerbang ({@code kultivator:tuas_gerbang}, DESIGN 38.T.7.11 chapter 5): the gate lever in the toll house of the
 * Gerbang Tengkorak. A stone pedestal with a bronze quadrant plate and a slotted guide, the iron lever (with a bone
 * grip and a skull pommel) on its axle, a pawl that drops into the teeth, and the gate chain running down into the
 * floor. Pulled ({@link TuasGerbangEntity#KEADAAN_BAWAH}) it swings from +40 to -40 degrees in 8 ticks with an
 * overshoot and the chain jerks down; pushed back ({@link TuasGerbangEntity#KEADAAN_ATAS}) the reverse. Texture
 * {@code tuas_gerbang.png} (64 x 128, bands of 32).
 */
public class TuasGerbangRenderer extends TemaBendaRenderer<TuasGerbangEntity, TuasGerbangRenderer.State, TuasGerbangRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("tuas_gerbang"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/tuas_gerbang.png");

	static final int B = 32;
	static final int BATU = 0;
	static final int BESI = 1;
	static final int PERUNGGU = 2;
	static final int TULANG = 3;

	public static class State extends TemaBendaRenderer.State {
		public float tuas;
	}

	public TuasGerbangRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEX, null, 0.3F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, TuasGerbangRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(TuasGerbangEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		float k = lewat(maju(state.umur, 0.0F, 8.0F));
		float up = rad(40.0F);
		float down = -rad(40.0F);
		state.tuas = state.keadaan == TuasGerbangEntity.KEADAAN_BAWAH ? Mth.lerp(k, up, down) : Mth.lerp(k, down, up);
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
		root.addOrReplaceChild("alas", p(BATU).addBox(-5.0F, 16.0F, -5.0F, 10.0F, 8.0F, 10.0F).addBox(-6.0F, 22.0F, -6.0F, 12.0F, 2.0F, 12.0F), PartPose.ZERO);
		root.addOrReplaceChild("kuadran", p(PERUNGGU).addBox(-4.0F, 8.0F, -0.5F, 1.0F, 8.0F, 1.0F).addBox(3.0F, 8.0F, -0.5F, 1.0F, 8.0F, 1.0F)
				.addBox(-4.5F, 8.0F, -4.0F, 1.0F, 8.0F, 8.0F).addBox(3.5F, 8.0F, -4.0F, 1.0F, 8.0F, 8.0F), PartPose.ZERO);
		root.addOrReplaceChild("gigi", p(BESI, 20).addBox(-3.0F, 15.0F, -4.0F, 6.0F, 1.0F, 8.0F), PartPose.ZERO);
		PartDefinition tuas = root.addOrReplaceChild("tuas", p(BESI).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F)
				.addBox(-5.0F, -0.8F, -0.8F, 10.0F, 1.6F, 1.6F), PartPose.offset(0.0F, 12.0F, 0.0F));
		tuas.addOrReplaceChild("batang", p(BESI, 40).addBox(-0.9F, -16.0F, -0.9F, 1.8F, 16.0F, 1.8F), PartPose.ZERO);
		tuas.addOrReplaceChild("pegangan", p(TULANG).addBox(-1.2F, -22.0F, -1.2F, 2.4F, 6.0F, 2.4F), PartPose.ZERO);
		tuas.addOrReplaceChild("tengkorak", p(TULANG, 20).addBox(-1.8F, -25.5F, -1.8F, 3.6F, 3.5F, 3.6F).addBox(-1.2F, -22.6F, -2.2F, 2.4F, 1.0F, 1.0F),
				PartPose.ZERO);
		tuas.addOrReplaceChild("pengait", p(BESI, 50).addBox(-0.5F, 1.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.ZERO);
		root.addOrReplaceChild("rantai", p(BESI, 60).addBox(-0.6F, 0.0F, -0.6F, 1.2F, 10.0F, 1.2F), PartPose.offset(0.0F, 14.0F, 3.0F));
		return LayerDefinition.create(mesh, 64, B * 4);
	}

	/** The lever's model: the swing and the chain jerk. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart tuas;
		private final ModelPart rantai;

		public Model(ModelPart root) {
			super(root);
			this.tuas = root.getChild("tuas");
			this.rantai = root.getChild("rantai");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			tuas.xRot = state.tuas;
			// the chain is pulled down 3 px when the lever is down
			rantai.y += (rad(40.0F) - state.tuas) / rad(80.0F) * 3.0F;
		}
	}
}
