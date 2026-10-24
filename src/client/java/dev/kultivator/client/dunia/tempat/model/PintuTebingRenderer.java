package dev.kultivator.client.dunia.tempat.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.gua.PintuTebingEntity;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Pintu Tebing ({@code kultivator:pintu_tebing}, DESIGN 38.O.2): the false-stone door of a hermit's cave. Two blocks of
 * stone 16 × 32 × 4 that match the cliff (texture after {@code stone} with hairline cracks), a darker lintel crack and a
 * worn hand-hold. It slides aside 17 px over 24 ticks ({@link KaitModel#pintuGeser}), grinding: it shudders a little
 * while it moves and sinks half a pixel into the rock first, like {@code KotaPintuEntity}. Texture
 * {@code textures/entity/tempat/pintu_tebing.png} (64 × 64: cracked stone, darker stone).
 */
public class PintuTebingRenderer extends BendaRenderer<PintuTebingEntity, PintuTebingRenderer.State, PintuTebingRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("pintu_tebing"), "main");
	static final Identifier TEXTURE = Kultivator.id("textures/entity/tempat/pintu_tebing.png");

	public static class State extends BendaRenderer.State {
		public float slide;
	}

	public PintuTebingRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEXTURE, null, 0.0F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, PintuTebingRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(PintuTebingEntity entity) {
		return 1.2D;
	}

	@Override
	public void extractRenderState(PintuTebingEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.slide = Mth.clamp(KaitModel.pintuGeser.baca(entity, partialTicks), 0.0F, 1.0F);
	}

	static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition slab = root.addOrReplaceChild("slab", band(0).addBox(-8.0F, -8.0F, -2.0F, 16.0F, 16.0F, 4.0F).addBox(-8.0F, 8.0F, -2.0F, 16.0F, 16.0F, 4.0F),
				PartPose.ZERO);
		slab.addOrReplaceChild("seam", band(1).addBox(-8.2F, 7.6F, -2.2F, 16.4F, 0.8F, 0.4F).addBox(-8.2F, -8.4F, -2.2F, 16.4F, 0.6F, 0.6F), PartPose.ZERO);
		slab.addOrReplaceChild("hold", band(1).addBox(4.5F, 9.0F, -2.6F, 2.0F, 4.0F, 0.8F), PartPose.ZERO);
		slab.addOrReplaceChild("rubble", band(1).addBox(-7.0F, 22.6F, -2.8F, 3.0F, 1.4F, 1.0F).addBox(2.0F, 23.0F, -2.6F, 4.0F, 1.0F, 0.8F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	/** The door's model: the grinding slide. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart slab;

		public Model(ModelPart root) {
			super(root);
			this.slab = root.getChild("slab");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float t = state.slide;
			if (t <= 0.0F) {
				return;
			}
			float sink = Math.min(1.0F, t * 4.0F) * 0.6F;
			float moving = t < 1.0F ? 1.0F : 0.0F;
			slab.z += sink;
			slab.x += sine(t) * 17.0F;
			slab.y += Mth.sin(state.ageInTicks * 3.1F) * 0.15F * moving;
			slab.zRot = Mth.sin(state.ageInTicks * 2.3F) * 0.004F * moving;
		}

		private static float sine(float t) {
			return (float) (-(Math.cos(Math.PI * t) - 1.0D) / 2.0D);
		}
	}
}
