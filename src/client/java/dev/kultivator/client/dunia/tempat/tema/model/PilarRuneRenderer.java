package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.oasis.PilarRuneEntity;
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
 * Pilar rune ({@code kultivator:pilar_rune}, DESIGN 38.T.9.3 / 38.T.4.7 chapter 4): one of the four water-draining
 * formation pillars in the ruin's third hall. A stepped base, a shaft of two drums (8 x 16 x 8 each) banded with
 * water-green runes, a flared capital holding a hovering, slowly turning rune ring of four plates. {@link
 * PilarRuneEntity#KEADAAN_AKTIF}: the runes throb (emissive, strength {@code nilai}) and the ring spins; {@link
 * PilarRuneEntity#KEADAAN_PECAH}: the upper drum topples off askew, the ring falls dark on the base; {@link
 * PilarRuneEntity#KEADAAN_TERSEGEL}: a band of blessed water wraps the shaft, the runes fade out over 20 ticks and the
 * ring stops. Texture {@code pilar_rune.png} (64 x 160, bands of 32) + glow.
 */
public class PilarRuneRenderer extends TemaBendaRenderer<PilarRuneEntity, PilarRuneRenderer.State, PilarRuneRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("pilar_rune"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/pilar_rune.png");
	private static final Identifier GLOW = Kultivator.id("textures/entity/tempat/tema/pilar_rune_pancar.png");

	static final int B = 32;
	static final int BATU = 0;
	static final int RUNE = 1;
	static final int KEPALA = 2;
	static final int SEGEL = 3;
	static final int CINCIN = 4;

	public static class State extends TemaBendaRenderer.State {
		public float putar;
		public boolean pecah;
		public boolean segel;
		public float jatuh;
	}

	public PilarRuneRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEX, GLOW, 0.4F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, PilarRuneRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(PilarRuneEntity entity) {
		return 1.0D;
	}

	@Override
	public void extractRenderState(PilarRuneEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		float t = state.umur;
		state.pecah = state.keadaan == PilarRuneEntity.KEADAAN_PECAH;
		state.segel = state.keadaan == PilarRuneEntity.KEADAAN_TERSEGEL;
		state.jatuh = state.pecah ? Math.min(1.0F, lewat(maju(t, 0.0F, 14.0F))) : 0.0F;
		float k = Mth.clamp(state.nilai, 0.0F, 1.0F);
		if (state.keadaan == PilarRuneEntity.KEADAAN_AKTIF) {
			float beat = 0.5F + 0.5F * Mth.sin(state.waktu * 0.16F + state.fase);
			state.pancar = (0.35F + 0.65F * k) * (0.55F + 0.45F * beat * beat);
			state.putar = state.jauh ? 0.0F : state.waktu * 0.04F + state.fase;
		} else if (state.segel) {
			state.pancar = 0.6F * (1.0F - maju(t, 0.0F, 20.0F));
			state.putar = state.fase;
		} else {
			state.pancar = 0.5F * (1.0F - maju(t, 0.0F, 6.0F));
			state.putar = state.fase;
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
		root.addOrReplaceChild("alas", p(BATU).addBox(-7.0F, 20.0F, -7.0F, 14.0F, 4.0F, 14.0F).addBox(-5.5F, 17.0F, -5.5F, 11.0F, 3.0F, 11.0F), PartPose.ZERO);
		root.addOrReplaceChild("bawah", p(RUNE).addBox(-4.0F, 1.0F, -4.0F, 8.0F, 16.0F, 8.0F), PartPose.ZERO);
		PartDefinition atas = root.addOrReplaceChild("atas", CubeListBuilder.create(), PartPose.offset(0.0F, 1.0F, 0.0F));
		atas.addOrReplaceChild("drum", p(RUNE, 32).addBox(-4.0F, -16.0F, -4.0F, 8.0F, 16.0F, 8.0F), PartPose.ZERO);
		atas.addOrReplaceChild("kepala", p(KEPALA).addBox(-5.0F, -18.0F, -5.0F, 10.0F, 2.0F, 10.0F).addBox(-6.0F, -21.0F, -6.0F, 12.0F, 3.0F, 12.0F)
				.addBox(-3.0F, -23.0F, -3.0F, 6.0F, 2.0F, 6.0F), PartPose.ZERO);
		root.addOrReplaceChild("segel", p(SEGEL).addBox(-4.6F, 6.0F, -4.6F, 9.2F, 5.0F, 9.2F).addBox(-4.6F, -10.0F, -4.6F, 9.2F, 3.0F, 9.2F), PartPose.ZERO);
		// the hovering ring: four rune plates on a thin hoop, turning above the capital
		PartDefinition cincin = root.addOrReplaceChild("cincin", CubeListBuilder.create(), PartPose.offset(0.0F, -28.0F, 0.0F));
		for (int i = 0; i < 4; i++) {
			PartDefinition arm = cincin.addOrReplaceChild("lempeng_" + i, CubeListBuilder.create(), PartPose.rotation(0.0F, i * Mth.HALF_PI, 0.0F));
			arm.addOrReplaceChild("pelat", p(CINCIN, i * 12).addBox(-2.5F, -3.0F, -9.5F, 5.0F, 6.0F, 1.0F), PartPose.ZERO);
			arm.addOrReplaceChild("busur", p(CINCIN, 50).addBox(2.5F, -0.4F, -9.2F, 4.5F, 0.8F, 0.6F).addBox(-7.0F, -0.4F, -9.2F, 4.5F, 0.8F, 0.6F),
					PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 64, B * 5);
	}

	/** The pillar's model: spin, the toppled drum, the water seal. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart atas;
		private final ModelPart segel;
		private final ModelPart cincin;

		public Model(ModelPart root) {
			super(root);
			this.atas = root.getChild("atas");
			this.segel = root.getChild("segel");
			this.cincin = root.getChild("cincin");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			segel.visible = state.segel;
			cincin.yRot = state.putar;
			if (state.pecah) {
				float j = state.jatuh;
				// the upper drum slides off and lies tipped against the lower one; the ring drops onto the base
				atas.x = 6.0F * j;
				atas.y += 10.0F * j;
				atas.zRot = 1.25F * j;
				cincin.y += 44.0F * Math.min(1.0F, j * 1.2F);
				cincin.xRot = 1.4F * j;
				cincin.z = -9.0F * j;
			} else {
				cincin.y += state.jauh ? 0.0F : Mth.sin(state.waktu * 0.07F + state.fase) * 0.8F;
			}
		}
	}
}
