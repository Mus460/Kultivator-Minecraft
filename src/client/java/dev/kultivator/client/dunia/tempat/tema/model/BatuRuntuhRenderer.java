package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.gunung.BatuRuntuhEntity;
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
 * Batu runtuh ({@code kultivator:batu_runtuh}, DESIGN 38.T.9.3 / 38.T.6.4): a boulder of a rain-time rockfall. A rough
 * core (20 x 18 x 20) with two crossed, turned lumps and five chips, so no face reads as a cube. Rolling ({@link
 * BatuRuntuhEntity#KEADAAN_GELINDING}) it turns in proportion to the distance travelled ({@code nilai} blocks, radius
 * 10 px) with a small wobble; breaking ({@link BatuRuntuhEntity#KEADAAN_PECAH}) its seven pieces fly apart, tumble and
 * shrink to nothing in 12 ticks (the dust is particles). No block ever changes. Three rocks: andesite, granite, mossy;
 * textures {@code batu_runtuh_andesit.png}, {@code _granit}, {@code _berlumut} (64 x 128, bands of 64).
 */
public class BatuRuntuhRenderer extends TemaBendaRenderer<BatuRuntuhEntity, BatuRuntuhRenderer.State, BatuRuntuhRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("batu_runtuh"), "main");

	static final int B = 64;
	static final int BATU = 0;
	static final int PECAHAN = 1;

	/** Fragment flight directions (x, y, z) and spins for the break. */
	static final float[][] ARAH = {{-1.0F, -0.6F, 0.2F}, {1.0F, -0.5F, -0.3F}, {0.1F, -1.2F, 0.9F}, {-0.4F, -0.8F, -1.0F}, {0.8F, -0.9F, 0.8F},
			{-0.9F, -0.4F, -0.7F}, {0.3F, -1.0F, -0.2F}};

	public static class State extends TemaBendaRenderer.State {
		public float guling;
		public float pecah;
	}

	public BatuRuntuhRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), new Identifier[] {tex("andesit"), tex("granit"), tex("berlumut")}, null, 0.7F);
	}

	private static Identifier tex(String jenis) {
		return Kultivator.id("textures/entity/tempat/tema/batu_runtuh_" + jenis + ".png");
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, BatuRuntuhRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected int tekstur(State state) {
		return Mth.clamp(state.varian, 0, 2);
	}

	@Override
	public void extractRenderState(BatuRuntuhEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.guling = state.nilai * 16.0F / 10.0F;
		state.pecah = state.keadaan == BatuRuntuhEntity.KEADAAN_PECAH ? maju(state.umur, 0.0F, 12.0F) : 0.0F;
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
		// the boulder turns about its centre, 10 px above the ground
		PartDefinition bongkah = root.addOrReplaceChild("bongkah", CubeListBuilder.create(), PartPose.offset(0.0F, 14.0F, 0.0F));
		bongkah.addOrReplaceChild("p0", p(BATU).addBox(-10.0F, -9.0F, -10.0F, 20.0F, 18.0F, 20.0F), PartPose.ZERO);
		bongkah.addOrReplaceChild("p1", p(BATU, 40).addBox(-8.0F, -10.5F, -8.0F, 16.0F, 21.0F, 16.0F), PartPose.rotation(0.4F, 0.78F, 0.2F));
		bongkah.addOrReplaceChild("p2", p(BATU, 80).addBox(-9.0F, -8.0F, -7.5F, 18.0F, 16.0F, 15.0F), PartPose.rotation(-0.3F, -0.5F, 0.6F));
		float[][] serpih = {{9.0F, -4.0F, 3.0F}, {-8.0F, 5.0F, -6.0F}, {2.0F, 8.5F, 7.0F}, {-4.0F, -8.0F, 6.0F}};
		for (int i = 0; i < serpih.length; i++) {
			float[] c = serpih[i];
			bongkah.addOrReplaceChild("p" + (3 + i), p(PECAHAN, i * 14).addBox(-3.0F, -2.5F, -3.0F, 6.0F, 5.0F, 6.0F), PartPose.offsetAndRotation(c[0], c[1], c[2],
					i * 0.7F, i * 1.1F, i * 0.4F));
		}
		return LayerDefinition.create(mesh, 64, B * 2);
	}

	/** The boulder's model: rolling and breaking. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart bongkah;
		private final ModelPart[] bagian = new ModelPart[7];

		public Model(ModelPart root) {
			super(root);
			this.bongkah = root.getChild("bongkah");
			for (int i = 0; i < bagian.length; i++) {
				bagian[i] = bongkah.getChild("p" + i);
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float k = state.pecah;
			if (k <= 0.0F) {
				bongkah.xRot = state.guling;
				bongkah.zRot = state.jauh ? 0.0F : Mth.sin(state.guling * 0.5F) * 0.12F;
				return;
			}
			// break: pieces fly out on a short arc, spin and shrink
			bongkah.xRot = state.guling;
			for (int i = 0; i < bagian.length; i++) {
				float[] d = ARAH[i];
				ModelPart part = bagian[i];
				float out = 14.0F * k;
				part.x += d[0] * out;
				part.z += d[2] * out;
				part.y += d[1] * out + 30.0F * k * k;
				part.xRot += k * (3.0F + i);
				part.yRot += k * (2.0F - i * 0.5F);
				float s = Math.max(0.0F, 1.0F - k * (0.8F + 0.04F * i));
				part.xScale = s;
				part.yScale = s;
				part.zScale = s;
			}
		}
	}
}
