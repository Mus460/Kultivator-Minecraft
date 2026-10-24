package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.bersama.PintuAirEntity;
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
 * Pintu air ({@code kultivator:pintu_air}, DESIGN 38.T.9.3): the sluice gate of a canal or a dry dock. A plank leaf 7 x 4
 * blocks (112 x 64 x 6, two courses of planks with iron straps, a slimy water line and a lifting beam), sliding in two
 * stone grooves (6 x 80 x 6) under a timber gantry (7 blocks up) with two chain pulleys; the chains run from the beam over the pulleys.
 * Opening ({@link PintuAirEntity#KEADAAN_NAIK}) it rises 4 blocks in {@link PintuAirEntity#GERAK_TICK} ticks
 * (easeInOutSine, a little shudder, pulleys turning); closing ({@link PintuAirEntity#KEADAAN_TURUN}) the reverse; the
 * splash is the server's. Dock variant ({@link PintuAirEntity#VARIAN_DOK}): heavier iron-banded leaf. Textures
 * {@code pintu_air.png}, {@code pintu_air_dok.png} (128 x 256, bands of 64).
 */
public class PintuAirRenderer extends TemaBendaRenderer<PintuAirEntity, PintuAirRenderer.State, PintuAirRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("pintu_air"), "main");

	static final int B = 64;
	static final int PAPAN = 0;
	static final int BATU = 1;
	static final int KAYU = 2;
	static final int BESI = 3;

	static final float NAIK = 64.0F;

	public static class State extends TemaBendaRenderer.State {
		public float angkat;
		public float katrol;
	}

	public PintuAirRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), new Identifier[] {tex("pintu_air"), tex("pintu_air_dok")}, null, 0.0F);
	}

	private static Identifier tex(String name) {
		return Kultivator.id("textures/entity/tempat/tema/" + name + ".png");
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, PintuAirRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(PintuAirEntity entity) {
		return 3.0D;
	}

	@Override
	protected int tekstur(State state) {
		return state.varian == PintuAirEntity.VARIAN_DOK ? 1 : 0;
	}

	@Override
	public void extractRenderState(PintuAirEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		float t = state.umur;
		float len = PintuAirEntity.GERAK_TICK;
		float up = switch (state.keadaan) {
			case PintuAirEntity.KEADAAN_NAIK -> sinus(maju(t, 0.0F, len));
			case PintuAirEntity.KEADAAN_TERBUKA -> 1.0F;
			case PintuAirEntity.KEADAAN_TURUN -> 1.0F - sinus(maju(t, 0.0F, len));
			default -> 0.0F;
		};
		boolean moving = (state.keadaan == PintuAirEntity.KEADAAN_NAIK || state.keadaan == PintuAirEntity.KEADAAN_TURUN) && t < len;
		float shudder = moving && !state.jauh ? Mth.sin(t * 2.3F) * 0.25F : 0.0F;
		state.angkat = up * NAIK + shudder;
		// pulleys (radius 4 px) turn by the travelled chain
		state.katrol = up * NAIK / 4.0F;
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
		// grooves (two stacked stones each side) and the gantry above the open position
		for (int side = -1; side <= 1; side += 2) {
			float x = side * 59.0F;
			root.addOrReplaceChild("alur_" + (side < 0 ? "k" : "n"), p(BATU, side < 0 ? 0 : 40).addBox(-3.0F, -16.0F, -3.0F, 6.0F, 40.0F, 6.0F)
					.addBox(-3.0F, -56.0F, -3.0F, 6.0F, 40.0F, 6.0F), PartPose.offset(x, 0.0F, 0.0F));
			root.addOrReplaceChild("tiang_" + (side < 0 ? "k" : "n"), p(KAYU, side < 0 ? 0 : 30).addBox(-2.5F, -56.0F, -2.5F, 5.0F, 56.0F, 5.0F),
					PartPose.offset(x, -56.0F, 6.0F));
		}
		root.addOrReplaceChild("gawang", p(KAYU, 60).addBox(-64.0F, -3.0F, -3.0F, 128.0F, 6.0F, 6.0F), PartPose.offset(0.0F, -112.0F, 6.0F));
		// the leaf: two plank courses, straps, the lifting beam and two eyes for the chains
		PartDefinition daun = root.addOrReplaceChild("daun", CubeListBuilder.create(), PartPose.ZERO);
		daun.addOrReplaceChild("bawah", p(PAPAN).addBox(-56.0F, -8.0F, -3.0F, 112.0F, 32.0F, 6.0F), PartPose.ZERO);
		daun.addOrReplaceChild("atas", p(PAPAN, 40).addBox(-56.0F, -40.0F, -3.0F, 112.0F, 32.0F, 6.0F), PartPose.ZERO);
		daun.addOrReplaceChild("pita", p(BESI).addBox(-56.0F, -30.0F, -3.6F, 112.0F, 2.0F, 0.6F).addBox(-56.0F, 10.0F, -3.6F, 112.0F, 2.0F, 0.6F)
				.addBox(-40.0F, -40.0F, -3.6F, 3.0F, 32.0F, 0.6F).addBox(37.0F, -40.0F, -3.6F, 3.0F, 32.0F, 0.6F)
				.addBox(-40.0F, -8.0F, -3.6F, 3.0F, 32.0F, 0.6F).addBox(37.0F, -8.0F, -3.6F, 3.0F, 32.0F, 0.6F), PartPose.ZERO);
		daun.addOrReplaceChild("balok", p(KAYU, 90).addBox(-57.0F, -44.0F, -4.0F, 114.0F, 4.0F, 8.0F), PartPose.ZERO);
		daun.addOrReplaceChild("mata", p(BESI, 40).addBox(-42.0F, -48.0F, -1.0F, 4.0F, 4.0F, 2.0F).addBox(38.0F, -48.0F, -1.0F, 4.0F, 4.0F, 2.0F),
				PartPose.ZERO);
		// chains from the leaf eyes up to the pulleys (scaled as the leaf rises)
		for (int side = -1; side <= 1; side += 2) {
			float x = side * 40.0F;
			root.addOrReplaceChild("rantai_" + (side < 0 ? "k" : "n"), p(BESI, 60).addBox(0.0F, -0.8F, -0.8F, 64.0F, 1.6F, 1.6F),
					PartPose.offsetAndRotation(x, -48.0F, 0.0F, 0.0F, 0.0F, -Mth.HALF_PI));
			PartDefinition k = root.addOrReplaceChild("katrol_" + (side < 0 ? "k" : "n"), p(BESI, 80).addBox(-1.0F, -4.0F, -4.0F, 2.0F, 8.0F, 8.0F),
					PartPose.offset(x, -112.0F, 2.0F));
			k.addOrReplaceChild("b", p(BESI, 100).addBox(-0.9F, -4.0F, -4.0F, 1.8F, 8.0F, 8.0F), PartPose.rotation(Mth.PI / 4.0F, 0.0F, 0.0F));
		}
		return LayerDefinition.create(mesh, 128, B * 4);
	}

	/** The sluice's model: the rising leaf, shortening chains and turning pulleys. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart daun;
		private final ModelPart[] rantai = new ModelPart[2];
		private final ModelPart[] katrol = new ModelPart[2];

		public Model(ModelPart root) {
			super(root);
			this.daun = root.getChild("daun");
			for (int i = 0; i < 2; i++) {
				rantai[i] = root.getChild(i == 0 ? "rantai_k" : "rantai_n");
				katrol[i] = root.getChild(i == 0 ? "katrol_k" : "katrol_n");
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			daun.y -= state.angkat;
			for (int i = 0; i < 2; i++) {
				rantai[i].y -= state.angkat;
				rantai[i].xScale = Math.max(0.05F, (64.0F - state.angkat) / 64.0F);
				katrol[i].xRot = (i == 0 ? 1.0F : -1.0F) * state.katrol;
			}
		}
	}
}
