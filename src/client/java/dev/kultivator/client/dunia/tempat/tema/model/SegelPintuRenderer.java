package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.iblis.SegelPintuEntity;
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
 * Segel pintu ({@code kultivator:segel_pintu}, DESIGN 38.T.9.3 / 38.T.7.7): the seal over the ninth-floor door of the
 * Menara Kitab Terlarang. A dark bronze plate (32 x 48 x 2) cast with a ring of glowing forbidden script around a
 * sealed eye, held by four corner nails and crossed by two paper talisman strips; a carved frame surrounds it. {@link
 * SegelPintuEntity#KEADAAN_UTUH}: the script throbs. {@link SegelPintuEntity#KEADAAN_RETAK}: the crack plates appear
 * along the fault lines, the strips tear, the plate shakes and the whole seal fades out over 20 ticks. {@link
 * SegelPintuEntity#KEADAAN_TERBUKA}: nothing is drawn but the frame. Texture {@code segel_pintu.png} (128 x 256, bands
 * of 64; the plate's north face u 2..34, v 2..50 is the script decal) + glow.
 */
public class SegelPintuRenderer extends TemaBendaRenderer<SegelPintuEntity, SegelPintuRenderer.State, SegelPintuRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("segel_pintu"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/segel_pintu.png");
	private static final Identifier GLOW = Kultivator.id("textures/entity/tempat/tema/segel_pintu_pancar.png");

	static final int B = 64;
	static final int PELAT = 0;
	static final int BINGKAI = 1;
	static final int JIMAT = 2;
	static final int RETAK = 3;

	public static class State extends TemaBendaRenderer.State {
		public float retak;
		public boolean terbuka;
		public float getar;
	}

	public SegelPintuRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEX, GLOW, 0.0F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, SegelPintuRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(SegelPintuEntity entity) {
		return 1.5D;
	}

	@Override
	public void extractRenderState(SegelPintuEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		float t = state.umur;
		state.terbuka = state.keadaan == SegelPintuEntity.KEADAAN_TERBUKA;
		state.retak = state.keadaan == SegelPintuEntity.KEADAAN_RETAK ? maju(t, 0.0F, 20.0F) : 0.0F;
		state.getar = state.retak > 0.0F && state.retak < 1.0F && !state.jauh ? Mth.sin(t * 3.1F) * 0.4F * (1.0F - state.retak) : 0.0F;
		float beat = 0.5F + 0.5F * Mth.sin(state.waktu * 0.09F + state.fase);
		state.pancar = state.terbuka ? 0.0F : (0.45F + 0.55F * beat) * (1.0F - state.retak) + state.retak * (1.0F - state.retak) * 2.0F;
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
		// carved frame around the door opening
		root.addOrReplaceChild("bingkai", p(BINGKAI).addBox(-20.0F, -28.0F, -3.0F, 4.0F, 52.0F, 6.0F).addBox(16.0F, -28.0F, -3.0F, 4.0F, 52.0F, 6.0F)
				.addBox(-20.0F, -32.0F, -3.0F, 40.0F, 4.0F, 6.0F), PartPose.ZERO);
		PartDefinition segel = root.addOrReplaceChild("segel", CubeListBuilder.create(), PartPose.ZERO);
		segel.addOrReplaceChild("pelat", p(PELAT).addBox(-16.0F, -24.0F, -1.0F, 32.0F, 48.0F, 2.0F), PartPose.ZERO);
		segel.addOrReplaceChild("paku", p(BINGKAI, 60).addBox(-15.0F, -23.0F, -2.0F, 3.0F, 3.0F, 1.0F).addBox(12.0F, -23.0F, -2.0F, 3.0F, 3.0F, 1.0F)
				.addBox(-15.0F, 20.0F, -2.0F, 3.0F, 3.0F, 1.0F).addBox(12.0F, 20.0F, -2.0F, 3.0F, 3.0F, 1.0F), PartPose.ZERO);
		// the talisman strips crossing the plate (two halves each, so they can tear)
		for (int i = 0; i < 2; i++) {
			float ang = i == 0 ? 0.55F : -0.55F;
			PartDefinition strip = segel.addOrReplaceChild("jimat_" + i, CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, -1.3F, 0.0F, 0.0F, ang));
			strip.addOrReplaceChild("a", p(JIMAT, i * 20).addBox(-3.0F, -27.0F, -0.1F, 6.0F, 27.0F, 0.2F), PartPose.ZERO);
			strip.addOrReplaceChild("b", p(JIMAT, 50 + i * 20).addBox(-3.0F, 0.0F, -0.1F, 6.0F, 27.0F, 0.2F), PartPose.ZERO);
		}
		// crack plates: thin shards along the fault lines, shown while it breaks
		PartDefinition retak = segel.addOrReplaceChild("retak", CubeListBuilder.create(), PartPose.ZERO);
		float[][] garis = {{-6.0F, -14.0F, 0.7F, 18.0F}, {4.0F, -2.0F, -0.4F, 22.0F}, {-2.0F, 10.0F, 1.1F, 14.0F}, {8.0F, -18.0F, 2.2F, 10.0F}};
		for (int i = 0; i < garis.length; i++) {
			float[] g = garis[i];
			retak.addOrReplaceChild("garis_" + i, p(RETAK, i * 10).addBox(-g[3] / 2.0F, -0.5F, -0.2F, g[3], 1.0F, 0.4F),
					PartPose.offsetAndRotation(g[0], g[1], -1.2F, 0.0F, 0.0F, g[2]));
		}
		return LayerDefinition.create(mesh, 128, B * 4);
	}

	/** The seal's model: shake, tearing strips, cracks. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart segel;
		private final ModelPart retak;
		private final ModelPart[][] jimat = new ModelPart[2][2];

		public Model(ModelPart root) {
			super(root);
			this.segel = root.getChild("segel");
			this.retak = segel.getChild("retak");
			for (int i = 0; i < 2; i++) {
				ModelPart s = segel.getChild("jimat_" + i);
				jimat[i][0] = s.getChild("a");
				jimat[i][1] = s.getChild("b");
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			segel.visible = !state.terbuka && state.retak < 1.0F;
			segel.x = state.getar;
			retak.visible = state.retak > 0.0F;
			float r = state.retak;
			for (int i = 0; i < 2; i++) {
				// the strip halves tear apart and curl away
				jimat[i][0].xRot = -r * 1.2F;
				jimat[i][0].y -= r * 4.0F;
				jimat[i][1].xRot = r * 1.4F;
				jimat[i][1].y += r * 6.0F;
			}
			float s = 1.0F - 0.15F * r;
			segel.xScale = s;
			segel.yScale = s;
		}
	}
}
