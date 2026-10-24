package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.oasis.BatuUkurAirEntity;
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
 * Batu Ukur Air ({@code kultivator:batu_ukur_air}, DESIGN 38.T.9.3 / 38.T.4.7): the measuring post of the Kuil Air's
 * sacred pool. A limestone post of two drums on a lotus foot, carved with seven level marks (the old high-water lines,
 * the lower part stained green with algae), crowned with a small water-jar finial; a bronze fish float rides in a slot
 * on its face. The float stands at the pool's level {@code nilai} (0 = two marks down, 1 = restored); during the restoring
 * ceremony ({@link BatuUkurAirEntity#KEADAAN_NAIK}, {@link BatuUkurAirEntity#NAIK_TICK} ticks) it climbs four marks from
 * the old level with an easeInOutSine and bobs as it settles. Texture {@code batu_ukur_air.png} (64 x 96, bands of 32).
 */
public class BatuUkurAirRenderer extends TemaBendaRenderer<BatuUkurAirEntity, BatuUkurAirRenderer.State, BatuUkurAirRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("batu_ukur_air"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/batu_ukur_air.png");

	static final int B = 32;
	static final int KAPUR = 0;
	static final int PERUNGGU = 1;
	static final int LUMUT = 2;

	/** Float height range on the post (model units): lowest mark to the restored line. */
	static final float BAWAH = 14.0F;
	static final float ATAS = -14.0F;

	public static class State extends TemaBendaRenderer.State {
		public float tinggi;
		public float apung;
	}

	public BatuUkurAirRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEX, null, 0.3F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, BatuUkurAirRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(BatuUkurAirEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		float target = Mth.clamp(state.nilai, 0.0F, 1.0F);
		float h = target;
		float settle = 0.0F;
		if (state.keadaan == BatuUkurAirEntity.KEADAAN_NAIK) {
			float k = maju(state.umur, 0.0F, BatuUkurAirEntity.NAIK_TICK);
			float from = Math.max(0.0F, target - 4.0F / 7.0F);
			h = Mth.lerp(sinus(k), from, target);
			settle = redam(state.umur - BatuUkurAirEntity.NAIK_TICK * 0.85F, 30.0F, 0.6F);
		}
		state.tinggi = h;
		state.apung = state.jauh ? 0.0F : Mth.sin(state.waktu * 0.09F + state.fase) * 0.25F + settle * 1.2F;
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
		// lotus foot: square plinth and four petal lips
		root.addOrReplaceChild("kaki", p(LUMUT).addBox(-6.0F, 21.0F, -6.0F, 12.0F, 3.0F, 12.0F), PartPose.ZERO);
		for (int i = 0; i < 4; i++) {
			PartDefinition sisi = root.addOrReplaceChild("kelopak_" + i, CubeListBuilder.create(), PartPose.rotation(0.0F, i * Mth.HALF_PI, 0.0F));
			sisi.addOrReplaceChild("k", p(LUMUT, 20 + i * 8).addBox(-3.0F, -1.5F, 0.0F, 6.0F, 3.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 20.0F, -6.0F,
					-0.5F, 0.0F, 0.0F));
		}
		// the post: algae-stained lower drum, marked upper drum, the slot for the float
		root.addOrReplaceChild("bawah", p(LUMUT, 40).addBox(-3.5F, 4.0F, -3.5F, 7.0F, 17.0F, 7.0F), PartPose.ZERO);
		root.addOrReplaceChild("atas", p(KAPUR).addBox(-3.5F, -17.0F, -3.5F, 7.0F, 21.0F, 7.0F), PartPose.ZERO);
		root.addOrReplaceChild("alur", p(PERUNGGU, 40).addBox(-1.2F, -16.0F, -3.8F, 2.4F, 18.0F, 0.4F).addBox(-1.2F, 2.0F, -3.8F, 2.4F, 16.0F, 0.4F),
				PartPose.ZERO);
		// seven carved level marks (ridges)
		for (int i = 0; i < 7; i++) {
			float y = BAWAH - i * (BAWAH - ATAS) / 6.0F;
			root.addOrReplaceChild("tanda_" + i, p(KAPUR, 30 + i * 3).addBox(-4.0F, -0.4F, -4.0F, 8.0F, 0.8F, 8.0F), PartPose.offset(0.0F, y, 0.0F));
		}
		// finial: neck, water jar, lid knob
		root.addOrReplaceChild("puncak", p(KAPUR, 20).addBox(-4.5F, -19.0F, -4.5F, 9.0F, 2.0F, 9.0F).addBox(-2.5F, -24.0F, -2.5F, 5.0F, 5.0F, 5.0F)
				.addBox(-1.5F, -25.5F, -1.5F, 3.0F, 1.5F, 3.0F).addBox(-0.6F, -26.5F, -0.6F, 1.2F, 1.0F, 1.2F), PartPose.ZERO);
		// the bronze fish float: body, tail fin, dorsal fin, two eye studs
		PartDefinition ikan = root.addOrReplaceChild("ikan", p(PERUNGGU).addBox(-1.5F, -1.5F, -5.0F, 3.0F, 3.0F, 1.6F)
				.addBox(-3.5F, -1.0F, -4.8F, 2.0F, 2.0F, 1.2F).addBox(1.5F, -1.0F, -4.8F, 1.6F, 1.6F, 1.2F), PartPose.offset(0.0F, BAWAH, 0.0F));
		ikan.addOrReplaceChild("ekor", p(PERUNGGU, 20).addBox(0.0F, -1.6F, -4.6F, 2.0F, 3.2F, 0.6F), PartPose.offset(3.0F, 0.0F, 0.0F));
		ikan.addOrReplaceChild("sirip", p(PERUNGGU, 30).addBox(-1.0F, -2.6F, -4.6F, 2.0F, 1.2F, 0.6F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, B * 3);
	}

	/** The gauge's model: the climbing float. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart ikan;
		private final ModelPart ekor;

		public Model(ModelPart root) {
			super(root);
			this.ikan = root.getChild("ikan");
			this.ekor = ikan.getChild("ekor");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			ikan.y = Mth.lerp(state.tinggi, BAWAH, ATAS) + state.apung;
			ekor.yRot = Mth.sin(state.waktu * 0.2F + state.fase) * 0.25F * (state.jauh ? 0.0F : 1.0F);
		}
	}
}
