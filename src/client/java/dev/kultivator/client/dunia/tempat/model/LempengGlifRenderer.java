package dev.kultivator.client.dunia.tempat.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.gua.LempengGlifEntity;
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
 * Lempeng Glif ({@code kultivator:lempeng_glif}, DESIGN 38.O.2): one of the five stone plates of a tomb's Understanding
 * trial. A stone frame 16 × 1 × 16 holds a turning plate 14 × 2 × 14 with an engraved ring and one raised glyph of the six
 * elements (fire, water, wood, metal, earth, thunder; {@link KaitModel#glifJenis}). Each click turns it a quarter turn
 * ({@link KaitModel#glifPutar}, eased by its owner); a solved plate's glyph burns through the emissive pass
 * ({@link KaitModel#glifNyala}), with a slow pulse. Texture {@code textures/entity/tempat/lempeng_glif.png} (64 × 96:
 * stone with roundels, frame stone, glyph cells), glow {@code lempeng_glif_pancar.png}. The glyph cells sit in band 2 at
 * u = 12 + 12 × column, v = 64 + 12 × row (six 12 px cells), so each glyph part's top face shows exactly its cell.
 */
public class LempengGlifRenderer extends BendaRenderer<LempengGlifEntity, LempengGlifRenderer.State, LempengGlifRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("lempeng_glif"), "main");
	static final Identifier TEXTURE = Kultivator.id("textures/entity/tempat/lempeng_glif.png");
	static final Identifier GLOW = Kultivator.id("textures/entity/tempat/lempeng_glif_pancar.png");

	public static class State extends BendaRenderer.State {
		public float turn;
		public int glyph;
	}

	public LempengGlifRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEXTURE, GLOW, 0.0F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, LempengGlifRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(LempengGlifEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		// the plate's turn (default: the entity's yaw) comes from the hook alone, so the frame is not turned twice
		state.yaw = 0.0F;
		state.turn = KaitModel.glifPutar.baca(entity, partialTicks);
		state.glyph = Math.floorMod(KaitModel.glifJenis.baca(entity), 6);
		float lit = Mth.clamp(KaitModel.glifNyala.baca(entity, partialTicks), 0.0F, 1.0F);
		state.pancar = lit * (0.8F + 0.2F * Mth.sin(state.ageInTicks * 0.15F));
	}

	static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("frame", band(1).addBox(-8.0F, 23.0F, -8.0F, 16.0F, 1.0F, 16.0F), PartPose.ZERO);
		PartDefinition plate = root.addOrReplaceChild("plate", band(0).addBox(-7.0F, -2.0F, -7.0F, 14.0F, 2.0F, 14.0F), PartPose.offset(0.0F, 23.0F, 0.0F));
		plate.addOrReplaceChild("notch", band(1).addBox(-1.0F, -2.4F, -7.2F, 2.0F, 0.6F, 1.2F), PartPose.ZERO);
		for (int k = 0; k < 6; k++) {
			int col = k % 4;
			int row = k / 4;
			plate.addOrReplaceChild("glyph_" + k, CubeListBuilder.create().texOffs(12 * col, 64 + 12 * row).addBox(-6.0F, -2.4F, -6.0F, 12.0F, 0.4F, 12.0F),
					PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 64, 96);
	}

	/** The plate's model: the quarter turns and the shown glyph. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart plate;
		private final ModelPart[] glyphs = new ModelPart[6];

		public Model(ModelPart root) {
			super(root);
			this.plate = root.getChild("plate");
			for (int k = 0; k < 6; k++) {
				glyphs[k] = plate.getChild("glyph_" + k);
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			plate.yRot = state.turn * Mth.HALF_PI;
			for (int k = 0; k < 6; k++) {
				glyphs[k].visible = k == state.glyph;
			}
		}
	}
}
