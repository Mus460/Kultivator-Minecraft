package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.gunung.JeratEntity;
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
 * Jerat roh ({@code kultivator:jerat}, DESIGN 38.T.9.3 / 38.T.6.5 chapter 3): Gou Tie's spirit snare in a niche of the
 * plank walk. A flat iron ring (12 x 1 x 12) of eight segments with twelve saw teeth, a talisman pinned at its pivot, a
 * stake and chain; faint red runes glow on the plate. {@link JeratEntity#KEADAAN_MENUTUP}: the two jaw halves snap up and
 * meet in 4 ticks (easeOutBack); {@link JeratEntity#KEADAAN_DILEPAS}: they fall open again and the runes die out over
 * 20 ticks. Texture {@code jerat.png} (64 x 64, bands of 16) + glow.
 */
public class JeratRenderer extends TemaBendaRenderer<JeratEntity, JeratRenderer.State, JeratRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("jerat"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/jerat.png");
	private static final Identifier GLOW = Kultivator.id("textures/entity/tempat/tema/jerat_pancar.png");

	static final int B = 16;
	static final int BESI = 0;
	static final int GIGI = 1;
	static final int JIMAT = 2;
	static final int RANTAI = 3;

	public static class State extends TemaBendaRenderer.State {
		public float tutup;
	}

	public JeratRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEX, GLOW, 0.0F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, JeratRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double batas() {
		return 48.0D;
	}

	@Override
	public void extractRenderState(JeratEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		float t = state.umur;
		switch (state.keadaan) {
			case JeratEntity.KEADAAN_MENUTUP -> {
				state.tutup = Math.min(1.08F, lewat(maju(t, 0.0F, 4.0F)));
				state.pancar = 0.9F;
			}
			case JeratEntity.KEADAAN_DILEPAS -> {
				state.tutup = 1.0F - sinus(maju(t, 0.0F, 8.0F));
				state.pancar = 0.9F * (1.0F - maju(t, 0.0F, 20.0F));
			}
			default -> {
				state.tutup = 0.0F;
				state.pancar = 0.35F + 0.25F * Mth.sin(state.waktu * 0.1F + state.fase) + 0.4F * Mth.clamp(state.nilai, 0.0F, 1.0F);
			}
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
		root.addOrReplaceChild("pelat", p(BESI).addBox(-3.0F, 23.0F, -3.0F, 6.0F, 1.0F, 6.0F).addBox(-1.0F, 22.4F, -6.0F, 2.0F, 0.6F, 12.0F), PartPose.ZERO);
		root.addOrReplaceChild("jimat", p(JIMAT).addBox(-1.5F, 22.2F, -2.0F, 3.0F, 0.2F, 4.0F), PartPose.rotation(0.0F, 0.3F, 0.0F));
		root.addOrReplaceChild("pasak", p(RANTAI).addBox(5.0F, 22.0F, 6.0F, 1.0F, 3.0F, 1.0F).addBox(0.0F, 23.2F, 5.5F, 5.0F, 0.6F, 0.6F), PartPose.ZERO);
		// the two jaws hinge on the x axis at the centre line; each is half the ring with six teeth
		for (int side = -1; side <= 1; side += 2) {
			PartDefinition rahang = root.addOrReplaceChild("rahang_" + (side < 0 ? "k" : "n"), CubeListBuilder.create(), PartPose.offset(0.0F, 23.0F, 0.0F));
			float z0 = side < 0 ? -6.0F : 0.0F;
			rahang.addOrReplaceChild("cincin", p(BESI, side < 0 ? 20 : 40).addBox(-6.0F, -1.0F, side < 0 ? -6.0F : 5.0F, 12.0F, 1.0F, 1.0F)
					.addBox(-6.0F, -1.0F, z0 + (side < 0 ? 1.0F : 0.0F), 1.0F, 1.0F, 5.0F).addBox(5.0F, -1.0F, z0 + (side < 0 ? 1.0F : 0.0F), 1.0F, 1.0F, 5.0F),
					PartPose.ZERO);
			for (int i = 0; i < 6; i++) {
				float x = -5.0F + i * 2.0F;
				rahang.addOrReplaceChild("gigi_" + i, p(GIGI, i * 4).addBox(-0.5F, -2.6F, -0.5F, 1.0F, 2.0F, 1.0F),
						PartPose.offsetAndRotation(x + 0.5F, -0.4F, side * 5.0F, side * 0.35F, 0.0F, 0.0F));
			}
		}
		return LayerDefinition.create(mesh, 64, B * 4);
	}

	/** The snare's model: the jaws snapping shut. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart kiri;
		private final ModelPart kanan;

		public Model(ModelPart root) {
			super(root);
			this.kiri = root.getChild("rahang_k");
			this.kanan = root.getChild("rahang_n");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			// closed: each jaw stands up 90 degrees so the teeth meet over the centre
			float a = Mth.HALF_PI * state.tutup;
			kiri.xRot = -a;
			kanan.xRot = a;
		}
	}
}
