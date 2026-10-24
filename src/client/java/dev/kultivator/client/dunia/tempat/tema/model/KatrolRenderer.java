package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.gunung.KatrolEntity;
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
 * Katrol ({@code kultivator:katrol}, DESIGN 38.T.9.3 / 38.T.6.4): the woven lift basket of the cliff (two riders or one
 * load). A bamboo-woven basket 24 x 20 x 24 with a rim hoop, corner staves and a plank floor; four ropes from its
 * corners meet at the sling ring and run up, over the 30-block shaft, to the head frame with the crank drum (16 x 16 x
 * 24) and its crank (2 x 2 x 10) that the PENJAGA_LIFT turns (NPC clip 96 {@code putar_katrol}, 30 ticks a turn). The
 * basket is the entity; the ropes and head frame are drawn above it at {@code (1 - nilai) x 30} blocks (nilai = height
 * 0..1). Moving ({@link KatrolEntity#KEADAAN_NAIK}, {@link KatrolEntity#KEADAAN_TURUN}) the drum and crank turn (reverse
 * going down) and the basket sways a little; at a station it settles with a damped bob. Texture {@code katrol.png}
 * (64 x 384, bands of 64).
 */
public class KatrolRenderer extends TemaBendaRenderer<KatrolEntity, KatrolRenderer.State, KatrolRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("katrol"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/katrol.png");

	static final int B = 64;
	static final int ANYAMAN = 0;
	static final int BAMBU = 1;
	static final int TALI = 2;
	static final int KAYU = 3;
	static final int BESI = 4;
	static final int PAPAN = 5;

	/** Shaft height in model units (30 blocks). */
	static final float POROS = 480.0F;

	public static class State extends TemaBendaRenderer.State {
		public float tinggiTali;
		public float engkol;
		public float ayun;
		public float lenggok;
	}

	public KatrolRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEX, null, 0.6F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, KatrolRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(KatrolEntity entity) {
		return 2.0D;
	}

	@Override
	public void extractRenderState(KatrolEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		float h = Mth.clamp(state.nilai, 0.0F, 1.0F);
		state.tinggiTali = (1.0F - h) * POROS + 24.0F;
		boolean naik = state.keadaan == KatrolEntity.KEADAAN_NAIK;
		boolean turun = state.keadaan == KatrolEntity.KEADAAN_TURUN;
		float turn = (state.umur % 30.0F) / 30.0F * Mth.TWO_PI;
		state.engkol = naik ? turn : turun ? -turn : 0.0F;
		if (state.jauh) {
			state.ayun = 0.0F;
			state.lenggok = 0.0F;
		} else if (naik || turun) {
			state.ayun = Mth.sin(state.waktu * 0.13F + state.fase) * rad(2.5F);
			state.lenggok = Mth.sin(state.waktu * 0.09F + state.fase) * rad(1.5F);
		} else {
			state.ayun = redam(state.umur, 50.0F, 0.3F) * rad(3.0F);
			state.lenggok = 0.0F;
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
		// the basket swings from its sling ring (2 blocks above the rim)
		PartDefinition gantung = root.addOrReplaceChild("gantung", CubeListBuilder.create(), PartPose.offset(0.0F, -28.0F, 0.0F));
		PartDefinition keranjang = gantung.addOrReplaceChild("keranjang", CubeListBuilder.create(), PartPose.offset(0.0F, 28.0F, 0.0F));
		keranjang.addOrReplaceChild("dinding", p(ANYAMAN).addBox(-12.0F, -20.0F, -12.0F, 24.0F, 20.0F, 1.5F).addBox(-12.0F, -20.0F, 10.5F, 24.0F, 20.0F, 1.5F)
				.addBox(-12.0F, -20.0F, -10.5F, 1.5F, 20.0F, 21.0F).addBox(10.5F, -20.0F, -10.5F, 1.5F, 20.0F, 21.0F), PartPose.ZERO);
		keranjang.addOrReplaceChild("lantai", p(PAPAN).addBox(-11.0F, -2.0F, -11.0F, 22.0F, 2.0F, 22.0F), PartPose.ZERO);
		keranjang.addOrReplaceChild("bibir", p(BAMBU).addBox(-12.6F, -21.5F, -12.6F, 25.2F, 2.0F, 2.0F).addBox(-12.6F, -21.5F, 10.6F, 25.2F, 2.0F, 2.0F)
				.addBox(-12.6F, -21.5F, -10.6F, 2.0F, 2.0F, 21.2F).addBox(10.6F, -21.5F, -10.6F, 2.0F, 2.0F, 21.2F), PartPose.ZERO);
		for (int i = 0; i < 4; i++) {
			float x = i % 2 == 0 ? -12.8F : 11.0F;
			float z = i < 2 ? -12.8F : 11.0F;
			keranjang.addOrReplaceChild("rusuk_" + i, p(BAMBU, 30 + i * 5).addBox(0.0F, -21.0F, 0.0F, 1.8F, 21.0F, 1.8F), PartPose.offset(x, 0.0F, z));
		}
		keranjang.addOrReplaceChild("pita", p(BAMBU, 50).addBox(-12.4F, -11.0F, -12.4F, 24.8F, 1.2F, 24.8F), PartPose.ZERO);
		// the four sling ropes from the corners to the ring
		for (int i = 0; i < 4; i++) {
			float x = i % 2 == 0 ? -11.5F : 11.5F;
			float z = i < 2 ? -11.5F : 11.5F;
			// from the ring (y 0) down to the rim corner 30.5 px below: Rz(b) * Rx(a) * (0, len, 0) = (x, 30.5, z)
			float dy = 30.5F;
			float len = (float) Math.sqrt(x * x + z * z + dy * dy);
			PartDefinition sl = gantung.addOrReplaceChild("selempang_" + i, CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F,
					(float) Math.asin(z / len), 0.0F, -(float) Math.atan2(x, dy)));
			sl.addOrReplaceChild("t", p(TALI, i * 7).addBox(-0.5F, 0.0F, -0.5F, 1.0F, len, 1.0F), PartPose.ZERO);
		}
		gantung.addOrReplaceChild("cincin", p(BESI).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 3.0F, 4.0F), PartPose.ZERO);
		// the hoist rope up the shaft (built along x, stood up, scaled to the height)
		gantung.addOrReplaceChild("tali", p(TALI, 40).addBox(0.0F, -0.8F, -0.8F, 64.0F, 1.6F, 1.6F), PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 0.0F, 0.0F,
				-Mth.HALF_PI));
		// head frame at the top of the rope: two posts, the drum on its axle, the crank
		PartDefinition kepala = root.addOrReplaceChild("kepala", CubeListBuilder.create(), PartPose.offset(0.0F, -30.0F, 0.0F));
		kepala.addOrReplaceChild("rangka", p(KAYU).addBox(-16.0F, -24.0F, -3.0F, 3.0F, 24.0F, 6.0F).addBox(13.0F, -24.0F, -3.0F, 3.0F, 24.0F, 6.0F)
				.addBox(-16.0F, -27.0F, -4.0F, 32.0F, 3.0F, 8.0F), PartPose.ZERO);
		PartDefinition drum = kepala.addOrReplaceChild("drum", p(KAYU, 40).addBox(-12.0F, -8.0F, -8.0F, 24.0F, 16.0F, 16.0F)
				.addBox(-13.0F, -9.0F, -9.0F, 1.0F, 18.0F, 18.0F).addBox(12.0F, -9.0F, -9.0F, 1.0F, 18.0F, 18.0F), PartPose.offset(0.0F, -12.0F, 0.0F));
		drum.addOrReplaceChild("lilitan", p(TALI, 20).addBox(-8.0F, -8.4F, -8.4F, 16.0F, 16.8F, 16.8F), PartPose.ZERO);
		PartDefinition engkol = drum.addOrReplaceChild("engkol", p(BESI, 20).addBox(13.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F)
				.addBox(16.0F, -1.0F, -10.0F, 2.0F, 2.0F, 10.0F), PartPose.ZERO);
		engkol.addOrReplaceChild("pegangan", p(KAYU, 100).addBox(18.0F, -1.2F, -11.0F, 5.0F, 2.4F, 2.4F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, B * 6);
	}

	/** The lift basket's model: rope length, the head frame's height, drum and crank, sway. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart gantung;
		private final ModelPart tali;
		private final ModelPart kepala;
		private final ModelPart drum;

		public Model(ModelPart root) {
			super(root);
			this.gantung = root.getChild("gantung");
			this.tali = gantung.getChild("tali");
			this.kepala = root.getChild("kepala");
			this.drum = kepala.getChild("drum");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			gantung.xRot = state.ayun;
			gantung.zRot = state.lenggok;
			tali.xScale = state.tinggiTali / 64.0F;
			kepala.y -= state.tinggiTali;
			drum.xRot = state.engkol;
		}
	}
}
