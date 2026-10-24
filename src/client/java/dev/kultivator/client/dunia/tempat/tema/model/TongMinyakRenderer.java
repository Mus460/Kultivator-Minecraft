package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.iblis.TongMinyakEntity;
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
 * Tong Minyak Bayangan ({@code kultivator:tong_minyak}, DESIGN 38.T.7.11 chapter 3): a sealed barrel of shadow oil in
 * the Sarang Kontrak warehouse. Twelve bellied staves (two rings of six, the upper ring tilted in), three iron hoops,
 * a lid with a wax seal and a red paper charm, black oil weeping from the seams and a slick under the foot. Smashed
 * ({@link TongMinyakEntity#KEADAAN_PECAH}): the hoops spring, the staves burst outward and fall, the lid flips and the
 * slick spreads, all in 14 ticks, then the pieces shrink away. Texture {@code tong_minyak.png} (64 x 128, bands of 32)
 * + glow (the oil's faint violet sheen).
 */
public class TongMinyakRenderer extends TemaBendaRenderer<TongMinyakEntity, TongMinyakRenderer.State, TongMinyakRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("tong_minyak"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/tong_minyak.png");
	private static final Identifier GLOW = Kultivator.id("textures/entity/tempat/tema/tong_minyak_pancar.png");

	static final int B = 32;
	static final int PAPAN = 0;
	static final int BESI = 1;
	static final int SEGEL = 2;
	static final int MINYAK = 3;

	static final int BILAH = 6;

	public static class State extends TemaBendaRenderer.State {
		public float pecah;
	}

	public TongMinyakRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEX, GLOW, 0.4F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, TongMinyakRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(TongMinyakEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.pecah = state.keadaan == TongMinyakEntity.KEADAAN_PECAH ? maju(state.umur, 0.0F, 14.0F) : 0.0F;
		float fade = state.keadaan == TongMinyakEntity.KEADAAN_PECAH ? 1.0F - maju(state.umur, 30.0F, 30.0F) : 1.0F;
		state.alpha = fade;
		state.pancar = 0.35F + 0.15F * Mth.sin(state.waktu * 0.05F + state.fase);
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
		root.addOrReplaceChild("genangan", p(MINYAK).addBox(-9.0F, 23.8F, -9.0F, 18.0F, 0.2F, 18.0F), PartPose.ZERO);
		root.addOrReplaceChild("dasar", p(PAPAN, 50).addBox(-6.0F, 22.0F, -6.0F, 12.0F, 2.0F, 12.0F), PartPose.ZERO);
		// staves: lower ring leans out, upper ring leans in (the belly at the middle hoop, y 15)
		for (int ring = 0; ring < 2; ring++) {
			for (int i = 0; i < BILAH; i++) {
				float yaw = i * Mth.TWO_PI / BILAH + (ring == 0 ? 0.0F : Mth.PI / BILAH);
				PartDefinition arm = root.addOrReplaceChild("bilah_" + ring + "_" + i, CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 15.0F, 0.0F,
						0.0F, yaw, 0.0F));
				if (ring == 0) {
					arm.addOrReplaceChild("p", p(PAPAN, i * 9).addBox(-3.6F, 0.0F, -0.5F, 7.2F, 9.0F, 1.6F), PartPose.offsetAndRotation(0.0F, 0.0F, -7.0F,
							0.12F, 0.0F, 0.0F));
				} else {
					arm.addOrReplaceChild("p", p(PAPAN, 30 + i * 9).addBox(-3.6F, -8.0F, -0.5F, 7.2F, 8.0F, 1.6F), PartPose.offsetAndRotation(0.0F, 0.0F, -7.0F,
							-0.14F, 0.0F, 0.0F));
				}
			}
		}
		// three hoops (square rings) at the foot, the belly and the chime
		float[][] simpai = {{22.0F, 6.4F}, {14.6F, 7.6F}, {8.0F, 6.6F}};
		for (int i = 0; i < 3; i++) {
			float y = simpai[i][0];
			float r = simpai[i][1];
			root.addOrReplaceChild("simpai_" + i, p(BESI, i * 15).addBox(-r, -0.8F, -r, 2.0F * r, 1.6F, 0.8F).addBox(-r, -0.8F, r - 0.8F, 2.0F * r, 1.6F, 0.8F)
					.addBox(-r, -0.8F, -r, 0.8F, 1.6F, 2.0F * r).addBox(r - 0.8F, -0.8F, -r, 0.8F, 1.6F, 2.0F * r), PartPose.offset(0.0F, y, 0.0F));
		}
		// lid, wax seal and the charm hanging over the edge
		PartDefinition tutup = root.addOrReplaceChild("tutup", p(PAPAN, 80).addBox(-6.0F, -1.0F, -6.0F, 12.0F, 1.5F, 12.0F), PartPose.offset(0.0F, 7.5F, 0.0F));
		tutup.addOrReplaceChild("lilin", p(SEGEL).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 1.0F, 4.0F), PartPose.ZERO);
		tutup.addOrReplaceChild("jimat", p(SEGEL, 20).addBox(-1.5F, -1.1F, -6.4F, 3.0F, 0.2F, 5.0F).addBox(-1.5F, -1.0F, -6.6F, 3.0F, 6.0F, 0.2F),
				PartPose.ZERO);
		// oil runs down two seams
		root.addOrReplaceChild("tetes", p(MINYAK, 30).addBox(-1.0F, 9.0F, -7.9F, 1.2F, 13.0F, 0.4F).addBox(7.5F, 12.0F, 1.0F, 0.4F, 10.0F, 1.2F),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, B * 4);
	}

	/** The barrel's model: the burst. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart[][] bilah = new ModelPart[2][BILAH];
		private final ModelPart[] simpai = new ModelPart[3];
		private final ModelPart tutup;
		private final ModelPart genangan;
		private final ModelPart tetes;

		public Model(ModelPart root) {
			super(root);
			for (int r = 0; r < 2; r++) {
				for (int i = 0; i < BILAH; i++) {
					bilah[r][i] = root.getChild("bilah_" + r + "_" + i).getChild("p");
				}
			}
			for (int i = 0; i < 3; i++) {
				simpai[i] = root.getChild("simpai_" + i);
			}
			this.tutup = root.getChild("tutup");
			this.genangan = root.getChild("genangan");
			this.tetes = root.getChild("tetes");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float k = state.pecah;
			genangan.xScale = 1.0F + 1.6F * k;
			genangan.zScale = 1.0F + 1.6F * k;
			if (k <= 0.0F) {
				return;
			}
			tetes.visible = false;
			for (int r = 0; r < 2; r++) {
				for (int i = 0; i < BILAH; i++) {
					// burst outward (local -z) and topple, landing flat
					ModelPart part = bilah[r][i];
					part.z -= 10.0F * k;
					part.xRot += (r == 0 ? 1.0F : -1.0F) * 1.4F * k;
					part.y += (r == 0 ? 6.0F : 14.0F) * k * k;
				}
			}
			for (int i = 0; i < 3; i++) {
				float s = 1.0F + 0.5F * k;
				simpai[i].xScale = s;
				simpai[i].zScale = s;
				simpai[i].y += (22.0F - simpai[i].y) * k * 0.95F;
			}
			tutup.y -= 10.0F * Mth.sin(k * Mth.PI) - 15.0F * k * k;
			tutup.xRot = 2.6F * k;
			tutup.x += 6.0F * k;
		}
	}
}
