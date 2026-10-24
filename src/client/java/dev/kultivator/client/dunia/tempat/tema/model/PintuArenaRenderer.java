package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.iblis.PintuArenaEntity;
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
 * Pintu arena ({@code kultivator:pintu_arena}, DESIGN 38.T.9.3 / 38.T.7.5): the portcullis of a beast pen of the Arena
 * Darah. Twelve forged bars (2 x 62 x 2) with spiked feet, four cross rails, two stone grooves with a lintel, and two
 * chains from its top rail up to the winch. It rises 4 blocks in 32 ticks ({@link PintuArenaEntity#KEADAAN_NAIK},
 * easeInOutSine with a little judder; the dust is the server's) and drops in 8 ticks ({@link
 * PintuArenaEntity#KEADAAN_JATUH}, easeIn) with a bounce and rattle at the bottom; the small camera shake is the
 * client fx's. Texture {@code pintu_arena.png} (64 x 256, bands of 64).
 */
public class PintuArenaRenderer extends TemaBendaRenderer<PintuArenaEntity, PintuArenaRenderer.State, PintuArenaRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("pintu_arena"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/pintu_arena.png");

	static final int B = 64;
	static final int BESI = 0;
	static final int BATU = 1;
	static final int RANTAI = 2;
	static final int KARAT = 3;

	static final float NAIK = 64.0F;

	public static class State extends TemaBendaRenderer.State {
		public float angkat;
	}

	public PintuArenaRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEX, null, 0.0F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, PintuArenaRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(PintuArenaEntity entity) {
		return 4.0D;
	}

	@Override
	public void extractRenderState(PintuArenaEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		float t = state.umur;
		float up = switch (state.keadaan) {
			case PintuArenaEntity.KEADAAN_NAIK -> sinus(maju(t, 0.0F, 32.0F)) + (state.jauh ? 0.0F : Mth.sin(t * 1.9F) * 0.006F * (t < 32.0F ? 1.0F : 0.0F));
			case PintuArenaEntity.KEADAAN_TERBUKA -> 1.0F;
			case PintuArenaEntity.KEADAAN_JATUH -> {
				float k = maju(t, 0.0F, 8.0F);
				yield k < 1.0F ? 1.0F - k * k * k : Math.abs(redam(t - 8.0F, 14.0F, 0.9F)) * 0.05F;
			}
			default -> 0.0F;
		};
		state.angkat = Mth.clamp(up, 0.0F, 1.0F) * NAIK;
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
		// stone grooves at both sides and the lintel with the winch slot
		root.addOrReplaceChild("alur", p(BATU).addBox(-30.0F, -40.0F, -4.0F, 6.0F, 32.0F, 8.0F).addBox(24.0F, -40.0F, -4.0F, 6.0F, 32.0F, 8.0F)
				.addBox(-30.0F, -8.0F, -4.0F, 6.0F, 32.0F, 8.0F).addBox(24.0F, -8.0F, -4.0F, 6.0F, 32.0F, 8.0F), PartPose.ZERO);
		root.addOrReplaceChild("ambang", p(BATU, 40).addBox(-30.0F, -48.0F, -5.0F, 60.0F, 8.0F, 10.0F), PartPose.ZERO);
		// the grille: rides up inside the grooves
		PartDefinition jeruji = root.addOrReplaceChild("jeruji", CubeListBuilder.create(), PartPose.ZERO);
		for (int i = 0; i < 12; i++) {
			float x = -22.0F + i * 4.0F;
			PartDefinition b = jeruji.addOrReplaceChild("batang_" + i, p(i % 3 == 0 ? KARAT : BESI, i * 5).addBox(-1.0F, -40.0F, -1.0F, 2.0F, 62.0F, 2.0F),
					PartPose.offset(x, 0.0F, 0.0F));
			b.addOrReplaceChild("duri", p(BESI, 60).addBox(-0.6F, 22.0F, -0.6F, 1.2F, 2.0F, 1.2F), PartPose.ZERO);
		}
		for (int i = 0; i < 4; i++) {
			jeruji.addOrReplaceChild("palang_" + i, p(BESI, 30 + i * 9).addBox(-24.0F, -1.5F, -1.5F, 48.0F, 3.0F, 3.0F), PartPose.offset(0.0F, -36.0F + i * 17.0F,
					0.0F));
		}
		jeruji.addOrReplaceChild("paku", p(KARAT, 40).addBox(-23.5F, -2.0F, -2.0F, 47.0F, 1.0F, 0.6F).addBox(-23.5F, 15.0F, -2.0F, 47.0F, 1.0F, 0.6F),
				PartPose.offset(0.0F, -36.0F, 0.0F));
		// the two lifting chains run from the top rail up through the lintel (scaled by the lift)
		for (int side = -1; side <= 1; side += 2) {
			jeruji.addOrReplaceChild("rantai_" + (side < 0 ? "k" : "n"), p(RANTAI).addBox(0.0F, -1.0F, -1.0F, 72.0F, 2.0F, 2.0F),
					PartPose.offsetAndRotation(side * 14.0F, -37.0F, 0.0F, 0.0F, 0.0F, -Mth.HALF_PI));
		}
		return LayerDefinition.create(mesh, 64, B * 4);
	}

	/** The portcullis model: lift and drop. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart jeruji;

		public Model(ModelPart root) {
			super(root);
			this.jeruji = root.getChild("jeruji");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			jeruji.y -= state.angkat;
		}
	}
}
