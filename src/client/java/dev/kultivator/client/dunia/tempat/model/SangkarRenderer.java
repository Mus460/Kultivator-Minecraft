package dev.kultivator.client.dunia.tempat.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.gua.SangkarEntity;
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
 * Pintu Sangkar ({@code kultivator:sangkar}, DESIGN 38.O.2): the barred door of a captive's cage in a demonic lair. A
 * rusted iron frame 16 × 24 with six bars, a middle rail, two hinges on the left and a bronze padlock on a hasp at the
 * right. When freed ({@link KaitModel#sangkarBuka}) the padlock's shackle springs, the lock drops and tumbles (first 30 %
 * of the reading, about 8 ticks), then the door swings out to 100° on its hinges. Texture
 * {@code textures/entity/tempat/sangkar.png} (64 × 64: rusted iron, bronze).
 */
public class SangkarRenderer extends BendaRenderer<SangkarEntity, SangkarRenderer.State, SangkarRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("sangkar"), "main");
	static final Identifier TEXTURE = Kultivator.id("textures/entity/tempat/sangkar.png");

	public static class State extends BendaRenderer.State {
		public float open;
	}

	public SangkarRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEXTURE, null, 0.0F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, SangkarRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(SangkarEntity entity) {
		return 1.0D;
	}

	@Override
	public void extractRenderState(SangkarEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.open = Mth.clamp(KaitModel.sangkarBuka.baca(entity, partialTicks), 0.0F, 1.0F);
	}

	static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// the fixed jamb (hinge side post and latch post) stays; the door swings about x = -8
		root.addOrReplaceChild("jamb", band(0).addBox(-9.0F, 0.0F, -1.0F, 1.0F, 24.0F, 2.0F).addBox(8.0F, 0.0F, -1.0F, 1.0F, 24.0F, 2.0F)
				.addBox(-9.0F, -1.0F, -1.0F, 18.0F, 1.0F, 2.0F), PartPose.ZERO);
		PartDefinition door = root.addOrReplaceChild("door", band(0).addBox(0.0F, 0.0F, -0.5F, 16.0F, 1.0F, 1.0F).addBox(0.0F, 23.0F, -0.5F, 16.0F, 1.0F, 1.0F)
				.addBox(0.0F, 11.5F, -0.5F, 16.0F, 1.0F, 1.0F).addBox(0.0F, 1.0F, -0.5F, 1.0F, 22.0F, 1.0F).addBox(15.0F, 1.0F, -0.5F, 1.0F, 22.0F, 1.0F),
				PartPose.offset(-8.0F, 0.0F, 0.0F));
		for (int i = 0; i < 6; i++) {
			door.addOrReplaceChild("bar_" + i, band(0).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 22.0F, 1.0F), PartPose.offset(2.6F + i * 2.15F, 1.0F, 0.0F));
		}
		door.addOrReplaceChild("hinges", band(1).addBox(-0.6F, 3.0F, -0.8F, 1.6F, 2.0F, 1.6F).addBox(-0.6F, 19.0F, -0.8F, 1.6F, 2.0F, 1.6F), PartPose.ZERO);
		door.addOrReplaceChild("hasp", band(1).addBox(14.0F, 10.5F, -1.2F, 3.0F, 3.0F, 0.6F), PartPose.ZERO);
		PartDefinition lock = door.addOrReplaceChild("lock", band(1).addBox(-1.0F, 0.0F, -0.5F, 2.0F, 3.0F, 1.0F), PartPose.offset(16.0F, 13.5F, -1.6F));
		lock.addOrReplaceChild("shackle", band(0).addBox(-0.8F, -2.0F, -0.25F, 0.4F, 2.0F, 0.5F).addBox(0.4F, -2.0F, -0.25F, 0.4F, 2.0F, 0.5F)
				.addBox(-0.8F, -2.4F, -0.25F, 1.6F, 0.4F, 0.5F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	/** The cage door's model: the falling lock and the swing. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart door;
		private final ModelPart lock;
		private final ModelPart shackle;

		public Model(ModelPart root) {
			super(root);
			this.door = root.getChild("door");
			this.lock = door.getChild("lock");
			this.shackle = lock.getChild("shackle");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float t = state.open;
			if (t <= 0.0F) {
				// a locked door rattles faintly when someone pulls at it from inside
				door.yRot = Mth.sin(state.ageInTicks * 0.9F) * 0.004F;
				return;
			}
			float drop = Math.min(1.0F, t / 0.3F);
			shackle.y -= Math.min(1.0F, drop * 3.0F) * 1.2F;
			lock.y += drop * drop * 11.0F;
			lock.xRot = drop * 2.4F;
			lock.zRot = drop * 0.9F;
			lock.visible = drop < 1.0F;
			float swing = t <= 0.3F ? 0.0F : (t - 0.3F) / 0.7F;
			float eased = 1.0F - (1.0F - swing) * (1.0F - swing);
			door.yRot = -eased * 100.0F * Mth.DEG_TO_RAD;
		}
	}
}
