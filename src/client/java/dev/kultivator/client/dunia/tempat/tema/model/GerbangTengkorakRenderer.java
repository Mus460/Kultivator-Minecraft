package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.iblis.GerbangTengkorakEntity;
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
 * Gerbang Tengkorak ({@code kultivator:gerbang_tengkorak}, DESIGN 38.T.9.3 / 38.T.7.4): the gate of the Kota Iblis, the
 * mouth of the great skull the town wall is built around. The lower jaw (160 x 48 x 64, four bone slabs, a gum ridge
 * and ten fangs of 6 x 10 x 6 with two great canines) forms the threshold; on it stand the two iron leaves (48 x 96 x 4
 * each, built as an upper and a lower plate) cast with the relief of the Demon Lord's face and the chained souls, with
 * studs, straps, ring pulls and spiked crests. Opening ({@link GerbangTengkorakEntity#KEADAAN_MEMBUKA}): the jaw drops
 * 25 degrees on its back hinge (24 ticks, easeInOutSine; the dust and the 0.6-pitch iron door sound are the server's)
 * and then the leaves swing 90 degrees inward (30 ticks); closing runs the reverse. The red eyes of the relief glow.
 * Textures {@code gerbang_tengkorak.png} (black iron), {@code gerbang_tengkorak_merah.png} (red lacquer), 256 x 512, bands
 * of 64, + glow.
 */
public class GerbangTengkorakRenderer extends TemaBendaRenderer<GerbangTengkorakEntity, GerbangTengkorakRenderer.State, GerbangTengkorakRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("gerbang_tengkorak"), "main");

	static final int B = 64;
	static final int TULANG = 0;
	static final int GIGI = 1;
	static final int BESI = 2;
	static final int RELIEF_ATAS = 3;
	static final int RELIEF_BAWAH = 4;
	static final int PAKU = 5;
	static final int GUSI = 6;
	static final int RANTAI = 7;

	/** Jaw drop and leaf swing durations (ticks). */
	static final float T_RAHANG = 24.0F;
	static final float T_DAUN = 30.0F;

	public static class State extends TemaBendaRenderer.State {
		public float rahang;
		public float daun;
	}

	public GerbangTengkorakRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), new Identifier[] {tex("gerbang_tengkorak"), tex("gerbang_tengkorak_merah")},
				new Identifier[] {tex("gerbang_tengkorak_pancar")}, 0.0F);
	}

	private static Identifier tex(String name) {
		return Kultivator.id("textures/entity/tempat/tema/" + name + ".png");
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, GerbangTengkorakRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(GerbangTengkorakEntity entity) {
		return 3.0D;
	}

	@Override
	protected double batas() {
		return 160.0D;
	}

	@Override
	protected int tekstur(State state) {
		return state.varian == GerbangTengkorakEntity.VARIAN_MERAH ? 1 : 0;
	}

	@Override
	public void extractRenderState(GerbangTengkorakEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		float t = state.umur;
		float j;
		float d;
		switch (state.keadaan) {
			case GerbangTengkorakEntity.KEADAAN_MEMBUKA -> {
				j = sinus(maju(t, 0.0F, T_RAHANG));
				d = sinus(maju(t, T_RAHANG, T_DAUN));
			}
			case GerbangTengkorakEntity.KEADAAN_TERBUKA -> {
				j = 1.0F;
				d = 1.0F;
			}
			case GerbangTengkorakEntity.KEADAAN_MENUTUP -> {
				d = 1.0F - sinus(maju(t, 0.0F, T_DAUN));
				j = 1.0F - sinus(maju(t, T_DAUN, T_RAHANG));
			}
			default -> {
				j = 0.0F;
				d = 0.0F;
			}
		}
		state.rahang = j;
		state.daun = d;
		state.pancar = 0.6F + 0.4F * Mth.sin(state.waktu * 0.05F + state.fase);
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
		// ---------------------------------------------------------------- the lower jaw, hinged at its back top edge (z +32)
		PartDefinition rahang = root.addOrReplaceChild("rahang", CubeListBuilder.create(), PartPose.offset(0.0F, -24.0F, 32.0F));
		for (int i = 0; i < 4; i++) {
			float w = 160.0F - (i == 0 ? 8.0F : 0.0F) - (i == 3 ? 16.0F : 0.0F);
			rahang.addOrReplaceChild("tulang_" + i, p(TULANG, i * 37).addBox(-w / 2.0F, 0.0F, -64.0F + i * 16.0F, w, 48.0F, 16.0F), PartPose.ZERO);
		}
		rahang.addOrReplaceChild("gusi", p(GUSI).addBox(-74.0F, -4.0F, -62.0F, 148.0F, 4.0F, 14.0F), PartPose.ZERO);
		// ten fangs along the front, the two outer ones great canines
		for (int i = 0; i < 10; i++) {
			float x = -67.5F + i * 15.0F;
			boolean taring = i == 1 || i == 8;
			float h = taring ? 18.0F : 10.0F;
			float s = taring ? 8.0F : 6.0F;
			rahang.addOrReplaceChild("gigi_" + i, p(GIGI, i * 19).addBox(-s / 2.0F, -h, -s / 2.0F, s, h, s)
					.addBox(-s / 4.0F, -h - 3.0F, -s / 4.0F, s / 2.0F, 3.0F, s / 2.0F), PartPose.offsetAndRotation(x, -3.0F, -56.0F, -0.08F, 0.0F,
					(i - 4.5F) * 0.025F));
		}
		// ---------------------------------------------------------------- the two leaves standing on the jaw's hinge line
		for (int side = -1; side <= 1; side += 2) {
			String id = side < 0 ? "kiri" : "kanan";
			PartDefinition daun = root.addOrReplaceChild("daun_" + id, CubeListBuilder.create(), PartPose.offset(side * 48.0F, -24.0F, 26.0F));
			float x0 = side < 0 ? 0.0F : -48.0F;
			daun.addOrReplaceChild("atas", p(RELIEF_ATAS).addBox(x0, -96.0F, -2.0F, 48.0F, 48.0F, 4.0F), PartPose.ZERO);
			daun.addOrReplaceChild("bawah", p(RELIEF_BAWAH).addBox(x0, -48.0F, -2.0F, 48.0F, 48.0F, 4.0F), PartPose.ZERO);
			// straps, studs, the crest of spikes, the ring pull
			daun.addOrReplaceChild("pita", p(BESI).addBox(x0, -72.0F, -3.0F, 48.0F, 3.0F, 1.0F).addBox(x0, -26.0F, -3.0F, 48.0F, 3.0F, 1.0F)
					.addBox(side < 0 ? 0.0F : -4.0F, -96.0F, -3.0F, 4.0F, 48.0F, 1.0F).addBox(side < 0 ? 0.0F : -4.0F, -48.0F, -3.0F, 4.0F, 48.0F, 1.0F)
					.addBox(side < 0 ? 44.0F : -48.0F, -96.0F, -3.0F, 4.0F, 48.0F, 1.0F).addBox(side < 0 ? 44.0F : -48.0F, -48.0F, -3.0F, 4.0F, 48.0F, 1.0F),
					PartPose.ZERO);
			for (int i = 0; i < 5; i++) {
				float sx = x0 + 6.0F + i * 9.0F;
				daun.addOrReplaceChild("duri_" + i, p(PAKU, i * 9).addBox(-1.5F, -8.0F, -1.5F, 3.0F, 8.0F, 3.0F)
						.addBox(-0.75F, -11.0F, -0.75F, 1.5F, 3.0F, 1.5F), PartPose.offset(sx, -96.0F, 0.0F));
			}
			daun.addOrReplaceChild("paku", p(PAKU, 60).addBox(x0 + 8.0F, -70.5F, -4.0F, 2.0F, 2.0F, 1.0F).addBox(x0 + 20.0F, -70.5F, -4.0F, 2.0F, 2.0F, 1.0F)
					.addBox(x0 + 32.0F, -70.5F, -4.0F, 2.0F, 2.0F, 1.0F).addBox(x0 + 8.0F, -24.5F, -4.0F, 2.0F, 2.0F, 1.0F)
					.addBox(x0 + 20.0F, -24.5F, -4.0F, 2.0F, 2.0F, 1.0F).addBox(x0 + 32.0F, -24.5F, -4.0F, 2.0F, 2.0F, 1.0F), PartPose.ZERO);
			float rx = side < 0 ? 40.0F : -40.0F;
			PartDefinition cincin = daun.addOrReplaceChild("cincin", p(RANTAI).addBox(-1.5F, -1.5F, -4.0F, 3.0F, 3.0F, 2.0F), PartPose.offset(rx, -46.0F, 0.0F));
			cincin.addOrReplaceChild("gelang", p(RANTAI, 20).addBox(-4.0F, 0.0F, -4.5F, 8.0F, 1.2F, 1.2F).addBox(-4.0F, 7.0F, -4.5F, 8.0F, 1.2F, 1.2F)
					.addBox(-4.0F, 0.0F, -4.5F, 1.2F, 8.0F, 1.2F).addBox(2.8F, 0.0F, -4.5F, 1.2F, 8.0F, 1.2F), PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 256, B * 8);
	}

	/** The skull gate's model: the dropping jaw and the swinging leaves. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart rahang;
		private final ModelPart kiri;
		private final ModelPart kanan;

		public Model(ModelPart root) {
			super(root);
			this.rahang = root.getChild("rahang");
			this.kiri = root.getChild("daun_kiri");
			this.kanan = root.getChild("daun_kanan");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			// the jaw's front (-z) tips down: y' = -z sin(a) > 0 for a > 0 (model y points down)
			rahang.xRot = rad(25.0F) * state.rahang;
			rahang.y += 4.0F * state.rahang;
			float a = Mth.HALF_PI * state.daun;
			kiri.yRot = -a;
			kanan.yRot = a;
			// the leaves ride down a little with the jaw (they stand on it)
			kiri.y += 3.0F * state.rahang;
			kanan.y += 3.0F * state.rahang;
		}
	}
}
