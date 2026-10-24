package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.pelabuhan.DerekDermagaEntity;
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
 * Derek injak ({@code kultivator:derek_dermaga}, DESIGN 38.T.9.3): the treadwheel crane of the Dermaga Agung. A stone
 * footing, two A-frames of teak (each two raked legs 8 blocks tall with a cross brace) joined by a head beam, a turning
 * jib raked 30 degrees up from the head with a pulley at its tip, a rope down to an iron hook with slings and a cargo
 * crate, and behind the tower a 4-block treadwheel (16 tread boards, 24 spokes over two faces, an axle on two posts)
 * whose drum winds the rope. During {@link DerekDermagaEntity#KEADAAN_BONGKAR} it runs the 200-tick cycle of 38.T.9.3
 * from the state's age: lift the crate 3 blocks (40 ticks, wheel turning), swing 90 degrees (60), lower (40), unhook,
 * swing back empty (50). Texture {@code derek_dermaga.png} (128 x 384, bands of 64).
 */
public class DerekDermagaRenderer extends TemaBendaRenderer<DerekDermagaEntity, DerekDermagaRenderer.State, DerekDermagaRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("derek_dermaga"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/derek_dermaga.png");

	static final int B = 64;
	static final int KAYU = 0;
	static final int PAPAN = 1;
	static final int TALI = 2;
	static final int BESI = 3;
	static final int PETI = 4;
	static final int BATU = 5;

	/** Jib tip relative to the head pivot (128 px raked 30 degrees). */
	private static final float TIP_X = 128.0F * Mth.cos(Mth.PI / 6.0F);
	private static final float TIP_Y = -128.0F * Mth.sin(Mth.PI / 6.0F);
	private static final float TALI_BAWAH = 166.0F;
	private static final float TALI_ATAS = 118.0F;

	public static class State extends TemaBendaRenderer.State {
		public float tali = 120.0F;
		public float ayun;
		public float roda;
		public boolean peti;
		public float goyang;
	}

	public DerekDermagaRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEX, null, 1.2F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, DerekDermagaRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(DerekDermagaEntity entity) {
		return 8.0D;
	}

	@Override
	public void extractRenderState(DerekDermagaEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		if (state.keadaan != DerekDermagaEntity.KEADAAN_BONGKAR) {
			state.tali = 120.0F;
			state.ayun = 0.0F;
			state.roda = 0.0F;
			state.peti = false;
			state.goyang = 0.0F;
			return;
		}
		float t = state.umur % 200.0F;
		float rope;
		float swing;
		boolean crate = true;
		if (t < 40.0F) {
			rope = TALI_BAWAH - 48.0F * sinus(t / 40.0F);
			swing = 0.0F;
		} else if (t < 100.0F) {
			rope = TALI_ATAS;
			swing = sinus((t - 40.0F) / 60.0F);
		} else if (t < 140.0F) {
			rope = TALI_ATAS + 48.0F * sinus((t - 100.0F) / 40.0F);
			swing = 1.0F;
		} else if (t < 150.0F) {
			rope = TALI_BAWAH;
			swing = 1.0F;
			crate = false;
		} else {
			rope = TALI_BAWAH;
			swing = 1.0F - sinus((t - 150.0F) / 50.0F);
			crate = false;
		}
		state.tali = rope;
		state.ayun = swing * Mth.HALF_PI;
		// the drum (radius 4 px) winds what the rope gains; whole cycles keep turning forward
		int cycles = (int) (state.umur / 200.0F);
		state.roda = (TALI_BAWAH - rope) / 4.0F + cycles * 0.0F;
		state.peti = crate;
		// the load sways a little after each start and stop of the swing
		state.goyang = state.jauh ? 0.0F : redam(t - 40.0F, 40.0F, 0.35F) * 0.12F + redam(t - 100.0F, 40.0F, 0.35F) * -0.12F;
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
		// stone footing under the tower and the wheel
		for (int i = 0; i < 4; i++) {
			root.addOrReplaceChild("alas_" + i, p(BATU, i * 17).addBox(-20.0F, 20.0F, 0.0F, 40.0F, 4.0F, 20.0F), PartPose.offset(0.0F, 0.0F, -60.0F + i * 20.0F));
		}
		// two A-frames: raked legs (built along x, stood up), cross braces, head beam
		float rake = (float) Math.atan2(24.0D, 124.0D);
		float leg = (float) Math.sqrt(24.0D * 24.0D + 124.0D * 124.0D);
		for (int side = -1; side <= 1; side += 2) {
			for (int end = -1; end <= 1; end += 2) {
				String id = "kaki_" + (side < 0 ? "k" : "n") + (end < 0 ? "d" : "b");
				PartDefinition k = root.addOrReplaceChild(id, CubeListBuilder.create(), PartPose.offsetAndRotation(side * 16.0F, 20.0F, end * 24.0F,
						end * rake, 0.0F, 0.0F));
				k.addOrReplaceChild("batang", p(KAYU, (side + 1) * 20 + (end + 1) * 7).addBox(0.0F, -2.0F, -2.0F, leg, 4.0F, 4.0F),
						PartPose.rotation(0.0F, 0.0F, -Mth.HALF_PI));
				k.addOrReplaceChild("sepatu", p(BESI).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 3.0F, 6.0F), PartPose.ZERO);
			}
			root.addOrReplaceChild("palang_" + (side < 0 ? "k" : "n"), p(KAYU, 60).addBox(-2.0F, -2.0F, -13.0F, 4.0F, 4.0F, 26.0F)
					.addBox(-1.5F, 40.0F, -18.5F, 3.0F, 3.0F, 37.0F), PartPose.offset(side * 16.0F, -42.0F, 0.0F));
		}
		root.addOrReplaceChild("balok_kepala", p(KAYU, 80).addBox(-20.0F, -4.0F, -3.0F, 40.0F, 5.0F, 6.0F), PartPose.offset(0.0F, -104.0F, 0.0F));
		// turning head: pivot post, jib raked 30 degrees, tip pulley, rope, hook and crate
		PartDefinition putar = root.addOrReplaceChild("putar", p(BESI, 20).addBox(-3.0F, -6.0F, -3.0F, 6.0F, 4.0F, 6.0F), PartPose.offset(0.0F, -104.0F, 0.0F));
		PartDefinition lengan = putar.addOrReplaceChild("lengan", p(KAYU, 100).addBox(-12.0F, -2.0F, -2.0F, 140.0F, 4.0F, 4.0F),
				PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, -Mth.PI / 6.0F));
		lengan.addOrReplaceChild("katrol", p(BESI, 40).addBox(-3.0F, -3.0F, -1.5F, 6.0F, 6.0F, 3.0F).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F),
				PartPose.offset(128.0F, 0.0F, 0.0F));
		lengan.addOrReplaceChild("ikat", p(TALI, 5).addBox(10.0F, -2.5F, -2.5F, 3.0F, 5.0F, 5.0F).addBox(60.0F, -2.5F, -2.5F, 3.0F, 5.0F, 5.0F),
				PartPose.ZERO);
		putar.addOrReplaceChild("tali", p(TALI).addBox(0.0F, -0.6F, -0.6F, 64.0F, 1.2F, 1.2F), PartPose.offsetAndRotation(TIP_X, TIP_Y - 6.0F, 0.0F, 0.0F, 0.0F,
				Mth.HALF_PI));
		PartDefinition kait = putar.addOrReplaceChild("kait", p(BESI, 60).addBox(-2.0F, 0.0F, -1.0F, 4.0F, 6.0F, 2.0F)
				.addBox(-0.5F, 5.0F, -2.5F, 1.0F, 2.0F, 5.0F), PartPose.offset(TIP_X, TIP_Y - 6.0F + 120.0F, 0.0F));
		PartDefinition muatan = kait.addOrReplaceChild("muatan", CubeListBuilder.create(), PartPose.offset(0.0F, 7.0F, 0.0F));
		muatan.addOrReplaceChild("selempang", p(TALI, 20).addBox(-7.5F, 0.0F, -0.4F, 0.8F, 4.6F, 0.8F).addBox(6.7F, 0.0F, -0.4F, 0.8F, 4.6F, 0.8F)
				.addBox(-0.4F, 0.0F, -7.5F, 0.8F, 4.6F, 0.8F).addBox(-0.4F, 0.0F, 6.7F, 0.8F, 4.6F, 0.8F), PartPose.ZERO);
		muatan.addOrReplaceChild("peti", p(PETI).addBox(-8.0F, 4.0F, -8.0F, 16.0F, 16.0F, 16.0F), PartPose.ZERO);
		muatan.addOrReplaceChild("pita", p(BESI, 80).addBox(-8.3F, 3.7F, -1.0F, 16.6F, 16.6F, 2.0F), PartPose.ZERO);
		// the treadwheel behind the tower: axle along x, 16 tread boards, 2 x 12 spokes, posts and drum
		root.addOrReplaceChild("tiang_roda", p(KAYU, 30).addBox(-17.0F, -14.0F, -42.0F, 3.0F, 34.0F, 4.0F)
				.addBox(14.0F, -14.0F, -42.0F, 3.0F, 34.0F, 4.0F), PartPose.ZERO);
		PartDefinition roda = root.addOrReplaceChild("roda", p(BESI, 100).addBox(-16.0F, -2.0F, -2.0F, 32.0F, 4.0F, 4.0F)
				.addBox(-5.0F, -4.0F, -4.0F, 10.0F, 8.0F, 8.0F), PartPose.offset(0.0F, -14.0F, -40.0F));
		for (int i = 0; i < 16; i++) {
			roda.addOrReplaceChild("papan_" + i, p(PAPAN, i * 7).addBox(-12.0F, -33.0F, -6.6F, 24.0F, 2.0F, 13.2F), PartPose.rotation(i * Mth.PI / 8.0F, 0.0F, 0.0F));
		}
		for (int side = -1; side <= 1; side += 2) {
			for (int i = 0; i < 12; i++) {
				PartDefinition j = roda.addOrReplaceChild("jari_" + (side < 0 ? "k" : "n") + i, CubeListBuilder.create(),
						PartPose.offsetAndRotation(side * 11.0F, 0.0F, 0.0F, i * Mth.PI / 12.0F, 0.0F, 0.0F));
				j.addOrReplaceChild("b", p(KAYU, i * 9).addBox(-31.0F, -1.0F, -1.0F, 62.0F, 2.0F, 2.0F), PartPose.rotation(0.0F, 0.0F, Mth.HALF_PI));
			}
			roda.addOrReplaceChild("pelek_" + (side < 0 ? "k" : "n"), p(BESI, 110).addBox(-1.0F, -33.5F, -3.0F, 2.0F, 2.0F, 6.0F)
					.addBox(-1.0F, 31.5F, -3.0F, 2.0F, 2.0F, 6.0F).addBox(-1.0F, -3.0F, -33.5F, 2.0F, 6.0F, 2.0F)
					.addBox(-1.0F, -3.0F, 31.5F, 2.0F, 6.0F, 2.0F), PartPose.offset(side * 12.5F, 0.0F, 0.0F));
		}
		// the rope from the drum up to the head
		float dz = 40.0F;
		float dy = 90.0F;
		PartDefinition naik = root.addOrReplaceChild("tali_naik", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -14.0F, -40.0F,
				-(float) Math.atan2(dz, dy), 0.0F, 0.0F));
		naik.addOrReplaceChild("b", p(TALI, 40).addBox(0.0F, -0.6F, -0.6F, (float) Math.sqrt(dz * dz + dy * dy), 1.2F, 1.2F),
				PartPose.rotation(0.0F, 0.0F, -Mth.HALF_PI));
		return LayerDefinition.create(mesh, 128, B * 6);
	}

	/** The crane's model: swing, rope length, wheel and the swaying load. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart putar;
		private final ModelPart tali;
		private final ModelPart kait;
		private final ModelPart muatan;
		private final ModelPart roda;

		public Model(ModelPart root) {
			super(root);
			this.putar = root.getChild("putar");
			this.tali = putar.getChild("tali");
			this.kait = putar.getChild("kait");
			this.muatan = kait.getChild("muatan");
			this.roda = root.getChild("roda");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			putar.yRot = state.ayun;
			tali.xScale = state.tali / 64.0F;
			kait.y = TIP_Y - 6.0F + state.tali;
			kait.zRot = state.goyang;
			muatan.visible = state.peti;
			roda.xRot = state.roda;
		}
	}
}
