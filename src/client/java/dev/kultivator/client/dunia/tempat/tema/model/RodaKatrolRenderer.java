package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.gunung.RodaKatrolEntity;
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
 * Roda katrol ({@code kultivator:roda_katrol}, DESIGN 38.T.9.3): the bull wheel of the cable-car stations and the Tiang
 * Tengah, diameter 9 blocks ({@link RodaKatrolEntity#VARIAN_BESAR}) or 3 ({@link RodaKatrolEntity#VARIAN_KECIL}, the same
 * mesh at a third). Eight spokes through the hub (4 x 144 x 4, every 22.5 degrees), a rim of sixteen grooved segments
 * with iron straps, a hub (12 px) with wedges on a long axle, and the bearing blocks on a timber trestle. It turns while
 * {@link RodaKatrolEntity#KEADAAN_PUTAR} at {@code nilai} (-1..1) times a rim speed of 0.4 blocks a tick, with the rope
 * running in the groove. Texture {@code roda_katrol.png} (128 x 192, bands of 32).
 */
public class RodaKatrolRenderer extends TemaBendaRenderer<RodaKatrolEntity, RodaKatrolRenderer.State, RodaKatrolRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("roda_katrol"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/roda_katrol.png");

	static final int B = 32;
	static final int KAYU = 0;
	static final int BESI = 1;
	static final int PELEK = 2;
	static final int TALI = 3;
	static final int BALOK = 4;
	static final int BATU = 5;

	static final float JARI = 72.0F;
	static final int SEGMEN = 16;

	public static class State extends TemaBendaRenderer.State {
		public float putar;
		public float skala;
	}

	public RodaKatrolRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEX, null, 1.0F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, RodaKatrolRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(RodaKatrolEntity entity) {
		return 5.0D;
	}

	@Override
	protected double batas() {
		return 160.0D;
	}

	@Override
	public void extractRenderState(RodaKatrolEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		boolean kecil = state.varian == RodaKatrolEntity.VARIAN_KECIL;
		state.skala = kecil ? 1.0F / 3.0F : 1.0F;
		float radiusBlocks = kecil ? 1.5F : 4.5F;
		// rim speed 0.4 blocks a tick: angular speed = v / r (radians a tick)
		state.putar = state.keadaan == RodaKatrolEntity.KEADAAN_PUTAR && !state.jauh
				? state.waktu * 0.4F / radiusBlocks * Mth.clamp(state.nilai, -1.0F, 1.0F) : 0.0F;
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
		// everything scales about the trestle foot (y 24)
		PartDefinition semua = root.addOrReplaceChild("semua", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
		// trestle: footing, two raked posts each side, bearing blocks
		semua.addOrReplaceChild("alas", p(BATU).addBox(-20.0F, -4.0F, -24.0F, 40.0F, 4.0F, 12.0F).addBox(-20.0F, -4.0F, 12.0F, 40.0F, 4.0F, 12.0F),
				PartPose.ZERO);
		for (int side = -1; side <= 1; side += 2) {
			for (int k = -1; k <= 1; k += 2) {
				PartDefinition kaki = semua.addOrReplaceChild("kaki_" + (side < 0 ? "k" : "n") + (k < 0 ? "d" : "b"), CubeListBuilder.create(),
						PartPose.offsetAndRotation(k * 14.0F, -4.0F, side * 18.0F, 0.0F, 0.0F, k * -0.16F));
				kaki.addOrReplaceChild("b", p(BALOK, (k + 1) * 20 + (side + 1) * 7).addBox(0.0F, -2.5F, -2.5F, 82.0F, 5.0F, 5.0F), PartPose.rotation(0.0F, 0.0F,
						-Mth.HALF_PI));
			}
			semua.addOrReplaceChild("bantalan_" + (side < 0 ? "k" : "n"), p(BESI, 60).addBox(-6.0F, -4.0F, -4.0F, 12.0F, 8.0F, 8.0F), PartPose.offset(0.0F,
					-86.0F, side * 18.0F));
		}
		// the wheel turns about z on the axle at the top of the trestle (wheel plane x-y)
		PartDefinition roda = semua.addOrReplaceChild("roda", CubeListBuilder.create(), PartPose.offset(0.0F, -86.0F, 0.0F));
		roda.addOrReplaceChild("as", p(BESI).addBox(-24.0F, -1.5F, -1.5F, 48.0F, 3.0F, 3.0F), PartPose.rotation(0.0F, Mth.HALF_PI, 0.0F));
		roda.addOrReplaceChild("hub", p(KAYU, 70).addBox(-6.0F, -6.0F, -6.0F, 12.0F, 12.0F, 12.0F).addBox(-7.0F, -7.0F, -3.0F, 14.0F, 14.0F, 6.0F),
				PartPose.ZERO);
		for (int i = 0; i < 8; i++) {
			PartDefinition j = roda.addOrReplaceChild("jari_" + i, CubeListBuilder.create(), PartPose.rotation(0.0F, 0.0F, i * Mth.PI / 8.0F));
			j.addOrReplaceChild("b", p(KAYU, i * 13).addBox(-JARI, -2.0F, -2.0F, JARI * 2.0F, 4.0F, 4.0F), PartPose.ZERO);
		}
		float seg = 2.0F * JARI * Mth.sin(Mth.PI / SEGMEN) + 1.0F;
		for (int i = 0; i < SEGMEN; i++) {
			PartDefinition r = roda.addOrReplaceChild("pelek_" + i, CubeListBuilder.create(), PartPose.rotation(0.0F, 0.0F, i * Mth.TWO_PI / SEGMEN));
			r.addOrReplaceChild("b", p(PELEK, i * 7).addBox(-seg / 2.0F, JARI - 1.0F, -4.0F, seg, 6.0F, 8.0F), PartPose.ZERO);
			r.addOrReplaceChild("tali", p(TALI, i * 5).addBox(-seg / 2.0F, JARI + 4.6F, -1.2F, seg, 1.6F, 2.4F), PartPose.ZERO);
			r.addOrReplaceChild("plat", p(BESI, 30 + i * 3).addBox(-1.0F, JARI - 1.4F, -4.4F, 2.0F, 6.8F, 8.8F), PartPose.offset(seg / 2.0F - 1.0F, 0.0F, 0.0F));
		}
		return LayerDefinition.create(mesh, 128, B * 6);
	}

	/** The wheel's model: size variant and turning. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart semua;
		private final ModelPart roda;

		public Model(ModelPart root) {
			super(root);
			this.semua = root.getChild("semua");
			this.roda = semua.getChild("roda");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			semua.xScale = state.skala;
			semua.yScale = state.skala;
			semua.zScale = state.skala;
			roda.zRot = state.putar;
		}
	}
}
