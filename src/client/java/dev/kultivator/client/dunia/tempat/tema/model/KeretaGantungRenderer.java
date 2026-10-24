package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.gunung.KeretaGantungEntity;
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
 * Kereta gantung ({@code kultivator:kereta_gantung}, DESIGN 38.T.9.3 / 38.T.6.4): the six-seat cable car of the Kota
 * Gunung. A latticed teak cabin 40 x 36 x 40 (corner posts, sill and rail, lattice panels, a sliding door on the uphill
 * side, two benches inside), a two-tier tiled roof (48 x 6 x 48 and 32 x 6 x 32) with a gilt finial, the hanger arm
 * (4 x 40 x 4) with its yoke, and the trolley (16 x 8 x 24) running on two wheels (diameter 10) over a local slice of
 * the haul rope (3 px chain, 6 blocks; the full span catenary between the towers is not this entity's).
 * <p>
 * Under way ({@link KeretaGantungEntity#KEADAAN_JALAN}) the trolley wheels turn with the span progress {@code nilai};
 * the cabin swings like a damped pendulum (+-3 degrees) when it starts, when it stops in a station and as it passes the
 * middle tower ({@code nilai} 0.5); stuck ({@link KeretaGantungEntity#KEADAAN_MACET}) it sways in the wind; the door
 * slides open in 10 ticks ({@link KeretaGantungEntity#KEADAAN_PINTU}). Texture {@code kereta_gantung.png} (128 x 448,
 * bands of 64).
 */
public class KeretaGantungRenderer extends TemaBendaRenderer<KeretaGantungEntity, KeretaGantungRenderer.State, KeretaGantungRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("kereta_gantung"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/kereta_gantung.png");

	static final int B = 64;
	static final int KAYU = 0;
	static final int KISI = 1;
	static final int GENTENG = 2;
	static final int BESI = 3;
	static final int RANTAI = 4;
	static final int EMAS = 5;
	static final int PAPAN = 6;

	/** One span of the haul rope in blocks: the wheels turn {@code span / circumference} times per span. */
	static final float BENTANG = 96.0F;

	public static class State extends TemaBendaRenderer.State {
		public float ayun;
		public float roda;
		public float pintu;
	}

	public KeretaGantungRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEX, null, 0.0F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, KeretaGantungRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(KeretaGantungEntity entity) {
		return 6.0D;
	}

	@Override
	protected double batas() {
		return 160.0D;
	}

	@Override
	public void extractRenderState(KeretaGantungEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		float t = state.umur;
		float n = Mth.clamp(state.nilai, 0.0F, 1.0F);
		// wheel turns: distance along the span over the wheel's circumference (diameter 10 px)
		state.roda = n * BENTANG * 16.0F / (Mth.PI * 10.0F) * Mth.TWO_PI;
		float swing = 0.0F;
		if (!state.jauh) {
			switch (state.keadaan) {
				case KeretaGantungEntity.KEADAAN_JALAN -> {
					swing = redam(t, 70.0F, 0.22F) * 1.0F;
					float tower = Math.max(0.0F, 1.0F - Math.abs(n - 0.5F) * 18.0F);
					swing += Mth.sin(state.waktu * 0.22F) * tower;
				}
				case KeretaGantungEntity.KEADAAN_STASIUN, KeretaGantungEntity.KEADAAN_PINTU -> swing = -redam(t, 70.0F, 0.22F);
				case KeretaGantungEntity.KEADAAN_MACET -> swing = Mth.sin(state.waktu * 0.09F + state.fase) * 0.45F + Mth.sin(state.waktu * 0.23F) * 0.15F;
				default -> {
				}
			}
		}
		state.ayun = swing * rad(3.0F);
		state.pintu = state.keadaan == KeretaGantungEntity.KEADAAN_PINTU ? sinus(maju(t, 0.0F, 10.0F)) : 0.0F;
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
		// the cabin floor is the entity's feet (y 24, riders sit inside); arm, yoke and trolley rise 6 blocks above it
		PartDefinition troli = root.addOrReplaceChild("troli", p(BESI).addBox(-8.0F, -4.0F, -12.0F, 16.0F, 8.0F, 24.0F)
				.addBox(-6.0F, -6.0F, -10.0F, 12.0F, 2.0F, 20.0F), PartPose.offset(0.0F, -75.0F, 0.0F));
		troli.addOrReplaceChild("tali", p(RANTAI).addBox(-48.0F, -1.5F, -1.5F, 96.0F, 3.0F, 3.0F), PartPose.offsetAndRotation(0.0F, -11.5F, 0.0F, 0.0F,
				Mth.HALF_PI, 0.0F));
		for (int i = 0; i < 2; i++) {
			float z = i == 0 ? -7.0F : 7.0F;
			PartDefinition r = troli.addOrReplaceChild("roda_" + i, CubeListBuilder.create(), PartPose.offset(0.0F, -5.0F - 1.5F, z));
			r.addOrReplaceChild("a", p(BESI, 40).addBox(-1.5F, -5.0F, -2.0F, 3.0F, 10.0F, 4.0F).addBox(-1.5F, -2.0F, -5.0F, 3.0F, 4.0F, 10.0F), PartPose.ZERO);
			r.addOrReplaceChild("b", p(BESI, 60).addBox(-1.4F, -5.0F, -2.0F, 2.8F, 10.0F, 4.0F).addBox(-1.4F, -2.0F, -5.0F, 2.8F, 4.0F, 10.0F),
					PartPose.rotation(Mth.PI / 4.0F, 0.0F, 0.0F));
			r.addOrReplaceChild("poros", p(EMAS).addBox(-2.5F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F), PartPose.ZERO);
		}
		troli.addOrReplaceChild("pipi", p(BESI, 80).addBox(-9.0F, -9.0F, -9.0F, 1.0F, 6.0F, 18.0F).addBox(8.0F, -9.0F, -9.0F, 1.0F, 6.0F, 18.0F),
				PartPose.ZERO);
		// the pendulum: hanger arm, yoke and cabin swing about the trolley pin
		PartDefinition ayun = troli.addOrReplaceChild("ayun", CubeListBuilder.create(), PartPose.offset(0.0F, 4.0F, 0.0F));
		ayun.addOrReplaceChild("lengan", p(BESI, 100).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 40.0F, 4.0F), PartPose.ZERO);
		ayun.addOrReplaceChild("kuk", p(BESI, 20).addBox(-14.0F, 38.0F, -2.0F, 28.0F, 3.0F, 4.0F).addBox(-14.0F, 38.0F, -2.0F, 3.0F, 8.0F, 4.0F)
				.addBox(11.0F, 38.0F, -2.0F, 3.0F, 8.0F, 4.0F), PartPose.ZERO);
		// cabin: top of the roof at y 41 under the pin
		PartDefinition kabin = ayun.addOrReplaceChild("kabin", CubeListBuilder.create(), PartPose.offset(0.0F, 46.0F, 0.0F));
		kabin.addOrReplaceChild("atap_atas", p(GENTENG).addBox(-16.0F, 0.0F, -16.0F, 32.0F, 6.0F, 32.0F), PartPose.ZERO);
		kabin.addOrReplaceChild("atap_bawah", p(GENTENG, 30).addBox(-24.0F, 6.0F, -24.0F, 48.0F, 6.0F, 48.0F)
				.addBox(-25.0F, 10.5F, -25.0F, 50.0F, 1.5F, 50.0F), PartPose.ZERO);
		kabin.addOrReplaceChild("puncak", p(EMAS, 20).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 3.0F, 4.0F).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 3.0F, 2.0F),
				PartPose.ZERO);
		PartDefinition badan = kabin.addOrReplaceChild("badan", CubeListBuilder.create(), PartPose.offset(0.0F, 12.0F, 0.0F));
		// corner posts, lintel ring, waist rail, floor with a skirt
		for (int i = 0; i < 4; i++) {
			float x = i % 2 == 0 ? -20.0F : 17.0F;
			float z = i < 2 ? -20.0F : 17.0F;
			badan.addOrReplaceChild("tiang_" + i, p(KAYU, i * 13).addBox(0.0F, 0.0F, 0.0F, 3.0F, 36.0F, 3.0F), PartPose.offset(x, 0.0F, z));
		}
		badan.addOrReplaceChild("ambang", p(KAYU, 60).addBox(-20.0F, 0.0F, -20.0F, 40.0F, 3.0F, 3.0F).addBox(-20.0F, 0.0F, 17.0F, 40.0F, 3.0F, 3.0F)
				.addBox(-20.0F, 0.0F, -17.0F, 3.0F, 3.0F, 34.0F).addBox(17.0F, 0.0F, -17.0F, 3.0F, 3.0F, 34.0F), PartPose.ZERO);
		badan.addOrReplaceChild("pinggang", p(KAYU, 80).addBox(-20.0F, 20.0F, -20.0F, 40.0F, 2.0F, 2.0F).addBox(-20.0F, 20.0F, 18.0F, 40.0F, 2.0F, 2.0F)
				.addBox(-20.0F, 20.0F, -18.0F, 2.0F, 2.0F, 36.0F).addBox(18.0F, 20.0F, -18.0F, 2.0F, 2.0F, 36.0F), PartPose.ZERO);
		badan.addOrReplaceChild("lantai", p(PAPAN).addBox(-20.0F, 34.0F, -20.0F, 40.0F, 3.0F, 40.0F), PartPose.ZERO);
		// lattice panels: upper windows on all sides, lower solid panels (the uphill side north has the door instead)
		badan.addOrReplaceChild("kisi_atas", p(KISI).addBox(-17.0F, 3.0F, -19.0F, 34.0F, 17.0F, 1.0F).addBox(-17.0F, 3.0F, 18.0F, 34.0F, 17.0F, 1.0F)
				.addBox(-19.0F, 3.0F, -17.0F, 1.0F, 17.0F, 34.0F).addBox(18.0F, 3.0F, -17.0F, 1.0F, 17.0F, 34.0F), PartPose.ZERO);
		badan.addOrReplaceChild("panel_bawah", p(PAPAN, 50).addBox(-17.0F, 22.0F, 18.0F, 34.0F, 12.0F, 1.0F).addBox(-19.0F, 22.0F, -17.0F, 1.0F, 12.0F, 34.0F)
				.addBox(18.0F, 22.0F, -17.0F, 1.0F, 12.0F, 34.0F).addBox(-17.0F, 22.0F, -19.0F, 9.0F, 12.0F, 1.0F)
				.addBox(8.0F, 22.0F, -19.0F, 9.0F, 12.0F, 1.0F), PartPose.ZERO);
		badan.addOrReplaceChild("bangku", p(PAPAN, 90).addBox(-17.0F, 26.0F, 10.0F, 34.0F, 2.0F, 7.0F).addBox(-17.0F, 28.0F, 15.0F, 34.0F, 6.0F, 2.0F)
				.addBox(-17.0F, 26.0F, -6.0F, 7.0F, 2.0F, 14.0F).addBox(10.0F, 26.0F, -6.0F, 7.0F, 2.0F, 14.0F), PartPose.ZERO);
		// sliding door (front, -z): lower panel between the two fixed halves, slides right behind them
		PartDefinition pintu = badan.addOrReplaceChild("pintu", p(PAPAN, 70).addBox(-8.0F, 0.0F, -0.5F, 16.0F, 28.0F, 1.0F), PartPose.offset(0.0F, 6.0F, -20.0F));
		pintu.addOrReplaceChild("gagang", p(EMAS, 40).addBox(5.0F, 12.0F, -1.2F, 1.0F, 4.0F, 0.8F), PartPose.ZERO);
		badan.addOrReplaceChild("rel_pintu", p(BESI, 110).addBox(-17.0F, 4.5F, -21.0F, 34.0F, 1.5F, 1.0F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 128, B * 7);
	}

	/** The cable car's model: wheels, pendulum swing, the sliding door. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart ayun;
		private final ModelPart pintu;
		private final ModelPart[] roda = new ModelPart[2];

		public Model(ModelPart root) {
			super(root);
			ModelPart troli = root.getChild("troli");
			this.ayun = troli.getChild("ayun");
			this.pintu = ayun.getChild("kabin").getChild("badan").getChild("pintu");
			for (int i = 0; i < 2; i++) {
				roda[i] = troli.getChild("roda_" + i);
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			// swing fore-aft along the travel direction (z)
			ayun.xRot = state.ayun;
			for (ModelPart r : roda) {
				r.xRot = -state.roda;
			}
			pintu.x = 15.0F * state.pintu;
			pintu.z -= 1.2F * state.pintu;
		}
	}
}
