package dev.kultivator.client.dunia.tempat.tema.model;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.dunia.tempat.model.SangkarRenderer;
import dev.kultivator.dunia.tempat.tema.iblis.SelTawananEntity;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Sel tawanan ({@code kultivator:sel_tawanan}, DESIGN 38.T.9.3 / 38.T.7.6): a cell door of the Kandang Tawanan. The door
 * itself is the 38MD {@code sangkar} model, imported unchanged (its baked layer {@link SangkarRenderer#LAYER} and
 * texture, animated here with the same padlock-and-swing motion); around it this renderer adds the cell front of the
 * captives' pen: a blackened timber frame, a barred transom above the door, a hanging tally board with a red ransom
 * tag, and straw on the floor. {@link SelTawananEntity#KEADAAN_TERBUKA} (ransomed or freed): the shackle springs, the
 * lock drops and the door swings out 100 degrees over 30 ticks; the tag flutters off. Texture
 * {@code sel_tawanan.png} (64 x 256, bands of 64) for the frame; {@code textures/entity/tempat/sangkar.png} (38MD) for the
 * door.
 */
public class SelTawananRenderer extends TemaBendaRenderer<SelTawananEntity, SelTawananRenderer.State, SelTawananRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("sel_tawanan"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/sel_tawanan.png");
	private static final Identifier TEX_SANGKAR = Kultivator.id("textures/entity/tempat/sangkar.png");

	static final int B = 64;
	static final int KAYU = 0;
	static final int BESI = 1;
	static final int PAPAN = 2;
	static final int JERAMI = 3;

	public static class State extends TemaBendaRenderer.State {
		public float buka;
	}

	private final PintuSel pintu;
	private final RenderType pintuType;

	public SelTawananRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEX, null, 0.0F);
		this.pintu = new PintuSel(context.bakeLayer(SangkarRenderer.LAYER));
		this.pintuType = RenderTypes.entityCutout(TEX_SANGKAR);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, SelTawananRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(SelTawananEntity entity) {
		return 1.5D;
	}

	@Override
	public void extractRenderState(SelTawananEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.buka = state.keadaan == SelTawananEntity.KEADAAN_TERBUKA ? maju(state.umur, 0.0F, 30.0F) : 0.0F;
	}

	@Override
	protected void submitExtra(State state, PoseStack poseStack, SubmitNodeCollector collector) {
		collector.submitModel(pintu, state, poseStack, pintuType, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
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
		// frame: two posts outside the cage jamb, a sill, the head beam 2 blocks up
		root.addOrReplaceChild("tiang", p(KAYU).addBox(-11.5F, -8.0F, -1.5F, 2.0F, 32.0F, 3.0F).addBox(9.5F, -8.0F, -1.5F, 2.0F, 32.0F, 3.0F), PartPose.ZERO);
		root.addOrReplaceChild("ambang", p(KAYU, 20).addBox(-12.0F, -10.0F, -2.0F, 24.0F, 2.0F, 4.0F).addBox(-11.0F, 23.0F, -2.0F, 22.0F, 1.0F, 4.0F),
				PartPose.ZERO);
		// barred transom between the door top (y -1) and the head beam
		root.addOrReplaceChild("jendela", p(BESI).addBox(-9.5F, -2.0F, -0.5F, 19.0F, 1.0F, 1.0F), PartPose.ZERO);
		for (int i = 0; i < 5; i++) {
			root.addOrReplaceChild("jeruji_" + i, p(BESI, 20 + i * 4).addBox(-0.5F, -8.0F, -0.5F, 1.0F, 6.0F, 1.0F), PartPose.offset(-7.0F + i * 3.5F, 0.0F, 0.0F));
		}
		// tally board with the ransom tag hanging from the head beam
		PartDefinition papan = root.addOrReplaceChild("papan", p(PAPAN).addBox(-4.0F, 0.0F, -0.5F, 8.0F, 5.0F, 1.0F), PartPose.offset(0.0F, -8.0F, -2.6F));
		papan.addOrReplaceChild("label", p(PAPAN, 30).addBox(-1.5F, 5.0F, -0.3F, 3.0F, 4.0F, 0.4F), PartPose.ZERO);
		// straw spread inside and spilling out
		root.addOrReplaceChild("jerami", p(JERAMI).addBox(-9.0F, 23.4F, 0.5F, 18.0F, 0.6F, 10.0F).addBox(-6.0F, 23.6F, -4.0F, 8.0F, 0.4F, 4.0F),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, B * 4);
	}

	/** The frame's model: the ransom tag flutters off when the door opens. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart label;

		public Model(ModelPart root) {
			super(root);
			this.label = root.getChild("papan").getChild("label");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float k = Math.min(1.0F, state.buka * 2.0F);
			label.y += k * k * 16.0F;
			label.xRot = k * 2.0F;
			label.visible = k < 1.0F;
		}
	}

	/**
	 * The imported 38MD cage door ({@link SangkarRenderer#LAYER}, parts {@code door / lock / shackle}) with the same
	 * opening motion as 38MD's {@code SangkarRenderer.Model}: lock drops in the first 30 %, then the swing to 100 degrees.
	 */
	public static final class PintuSel extends EntityModel<State> {

		private final ModelPart door;
		private final ModelPart lock;
		private final ModelPart shackle;

		public PintuSel(ModelPart root) {
			super(root);
			this.door = root.getChild("door");
			this.lock = door.getChild("lock");
			this.shackle = lock.getChild("shackle");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float t = state.buka;
			if (t <= 0.0F) {
				door.yRot = state.jauh ? 0.0F : Mth.sin(state.ageInTicks * 0.9F) * 0.004F;
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
