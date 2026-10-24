package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.gunung.PalangEntity;
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
 * Palang rantai ({@code kultivator:palang}, DESIGN 38.T.9.3): the chain barrier that closes a path (rockfall, a stuck
 * lift, the cliff edge). Two stone-footed posts 3 blocks apart with iron caps and ring bolts, two chains across (upper
 * and lower), each of eight links sagging by {@code nilai}, and a red warning tablet hung from the upper chain. {@link
 * PalangEntity#KEADAAN_TERBUKA}: the chains are unhooked from the right post and hang down the left one (10 ticks).
 * Texture {@code palang.png} (64 x 128, bands of 32).
 */
public class PalangRenderer extends TemaBendaRenderer<PalangEntity, PalangRenderer.State, PalangRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("palang"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/palang.png");

	static final int B = 32;
	static final int KAYU = 0;
	static final int RANTAI = 1;
	static final int BATU = 2;
	static final int PAPAN = 3;

	static final int MATA = 8;
	static final float LEBAR = 40.0F;

	public static class State extends TemaBendaRenderer.State {
		public float kendur;
		public float buka;
		public float goyang;
	}

	public PalangRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEX, null, 0.0F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, PalangRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(PalangEntity entity) {
		return 1.0D;
	}

	@Override
	public void extractRenderState(PalangEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.kendur = Mth.clamp(state.nilai, 0.0F, 1.0F);
		state.buka = state.keadaan == PalangEntity.KEADAAN_TERBUKA ? sinus(maju(state.umur, 0.0F, 10.0F)) : 0.0F;
		state.goyang = state.jauh ? 0.0F : Mth.sin(state.waktu * 0.08F + state.fase) * 0.06F;
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
		for (int side = -1; side <= 1; side += 2) {
			String s = side < 0 ? "kiri" : "kanan";
			float x = side * 22.0F;
			root.addOrReplaceChild("kaki_" + s, p(BATU, side < 0 ? 0 : 30).addBox(-4.0F, 20.0F, -4.0F, 8.0F, 4.0F, 8.0F), PartPose.offset(x, 0.0F, 0.0F));
			root.addOrReplaceChild("tiang_" + s, p(KAYU, side < 0 ? 0 : 20).addBox(-2.0F, -6.0F, -2.0F, 4.0F, 26.0F, 4.0F)
					.addBox(-2.5F, -8.0F, -2.5F, 5.0F, 2.0F, 5.0F).addBox(-1.5F, -9.0F, -1.5F, 3.0F, 1.0F, 3.0F), PartPose.offset(x, 0.0F, 0.0F));
			root.addOrReplaceChild("baut_" + s, p(RANTAI, 50).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F).addBox(-1.0F, 9.0F, -1.0F, 2.0F, 2.0F, 2.0F),
					PartPose.offset(x - side * 2.5F, 0.0F, 0.0F));
		}
		// two chains hung from the left post's ring bolts; each link pivots on the one before (a chain of parts)
		for (int c = 0; c < 2; c++) {
			PartDefinition rantai = root.addOrReplaceChild("rantai_" + c, CubeListBuilder.create(), PartPose.offset(-19.5F, c * 10.0F, 0.0F));
			PartDefinition mata = rantai;
			float len = (LEBAR - 1.0F) / MATA;
			for (int i = 0; i < MATA; i++) {
				boolean face = i % 2 == 0;
				mata = mata.addOrReplaceChild("mata_" + i, p(RANTAI, i * 6).addBox(0.0F, face ? -1.0F : -0.4F, face ? -0.4F : -1.0F, len + 0.6F, face ? 2.0F : 0.8F,
						face ? 0.8F : 2.0F), PartPose.offset(i == 0 ? 0.0F : len, 0.0F, 0.0F));
			}
		}
		// warning tablet on the upper chain's middle
		PartDefinition papan = root.addOrReplaceChild("papan", p(PAPAN).addBox(-6.0F, 1.0F, -0.5F, 12.0F, 8.0F, 1.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
		papan.addOrReplaceChild("tali", p(RANTAI, 60).addBox(-4.5F, -1.0F, -0.2F, 0.4F, 2.4F, 0.4F).addBox(4.1F, -1.0F, -0.2F, 0.4F, 2.4F, 0.4F),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, B * 4);
	}

	/** The barrier's model: the sag of each link, the unhooking, the tablet. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart[][] mata = new ModelPart[2][MATA];
		private final ModelPart papan;

		public Model(ModelPart root) {
			super(root);
			for (int c = 0; c < 2; c++) {
				ModelPart m = root.getChild("rantai_" + c);
				for (int i = 0; i < MATA; i++) {
					m = m.getChild("mata_" + i);
					mata[c][i] = m;
				}
			}
			this.papan = root.getChild("papan");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			// a catenary-like sag: the first link points down by s, each next link bends up by 2s / (n - 1) (a polygon arc)
			float s = rad(6.0F + 22.0F * state.kendur);
			float sagY = 0.0F;
			float len = (LEBAR - 1.0F) / MATA;
			for (int c = 0; c < 2; c++) {
				float a0 = Mth.lerp(state.buka, s, Mth.HALF_PI);
				for (int i = 0; i < MATA; i++) {
					// unhooked, the links fold back and forth into a short zig-zag hanging from the left bolt
					float bend = i == 0 ? a0 : Mth.lerp(state.buka, -(2.0F * s / (MATA - 1)), i % 2 == 1 ? 2.6F : -2.6F);
					mata[c][i].zRot = bend + (i == 0 ? state.goyang * (1.0F - state.buka) : 0.0F);
				}
			}
			// the tablet follows the middle of the upper chain
			for (int i = 0; i < MATA / 2; i++) {
				float ang = s - i * 2.0F * s / (MATA - 1);
				sagY += len * Mth.sin(ang);
			}
			papan.visible = state.buka < 0.5F;
			papan.x = -19.5F + LEBAR / 2.0F - 0.5F;
			papan.y = sagY * (1.0F - state.buka);
			papan.zRot = state.goyang * 0.5F;
		}
	}
}
