package dev.kultivator.client.dunia.tempat.tema.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.oasis.PintuPasirEntity;
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
 * Pintu pasir ({@code kultivator:pintu_pasir}, DESIGN 38.T.9.3 / 38.T.4.7): the hall door of the desert ruin. A carved
 * sandstone frame (two jambs with sun-disc capitals and a lintel bearing the eye of the old water cult) holds a 2 x 3
 * block slab with the relief of the spring goddess; drifted sand lies against its foot and along the lintel. Opening
 * ({@code nilai} 0..1) the slab grinds sideways into the wall pocket with a small shudder, the drift on top pours off in
 * four trickling lumps and the foot drift flattens. Texture {@code pintu_pasir.png} (128 x 192, bands of 64): the slab's
 * north face (u 6..38, v 6..54) is the relief decal.
 */
public class PintuPasirRenderer extends TemaBendaRenderer<PintuPasirEntity, PintuPasirRenderer.State, PintuPasirRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("pintu_pasir"), "main");
	private static final Identifier TEX = Kultivator.id("textures/entity/tempat/tema/pintu_pasir.png");

	static final int B = 64;
	static final int PELAT = 0;
	static final int KUSEN = 1;
	static final int PASIR = 2;

	public static class State extends TemaBendaRenderer.State {
		public float geser;
		public float getar;
	}

	public PintuPasirRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEX, null, 0.0F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, PintuPasirRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(PintuPasirEntity entity) {
		return 2.0D;
	}

	@Override
	public void extractRenderState(PintuPasirEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		float g = switch (state.keadaan) {
			case PintuPasirEntity.KEADAAN_TERBUKA -> 1.0F;
			case PintuPasirEntity.KEADAAN_MEMBUKA -> Mth.clamp(state.nilai, 0.0F, 1.0F);
			default -> 0.0F;
		};
		state.geser = sinus(g);
		boolean moving = state.keadaan == PintuPasirEntity.KEADAAN_MEMBUKA && g < 0.999F;
		state.getar = moving && !state.jauh ? Mth.sin(state.ageInTicks * 2.7F) * 0.25F : 0.0F;
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
		// frame: threshold, two jambs, capitals, lintel with the cornice
		root.addOrReplaceChild("ambang", p(KUSEN).addBox(-22.0F, 22.0F, -5.0F, 44.0F, 2.0F, 10.0F), PartPose.ZERO);
		root.addOrReplaceChild("kusen_kiri", p(KUSEN, 20).addBox(-22.0F, -26.0F, -5.0F, 6.0F, 48.0F, 10.0F), PartPose.ZERO);
		root.addOrReplaceChild("kusen_kanan", p(KUSEN, 60).addBox(16.0F, -26.0F, -5.0F, 6.0F, 48.0F, 10.0F), PartPose.ZERO);
		root.addOrReplaceChild("kepala_tiang", p(KUSEN, 90).addBox(-23.0F, -30.0F, -6.0F, 8.0F, 4.0F, 12.0F).addBox(15.0F, -30.0F, -6.0F, 8.0F, 4.0F, 12.0F),
				PartPose.ZERO);
		root.addOrReplaceChild("ambang_atas", p(KUSEN, 4).addBox(-24.0F, -36.0F, -6.0F, 48.0F, 6.0F, 12.0F)
				.addBox(-25.0F, -38.0F, -7.0F, 50.0F, 2.0F, 14.0F), PartPose.ZERO);
		// the slab with its relief, and the drift lying on its top edge
		PartDefinition pelat = root.addOrReplaceChild("pelat", p(PELAT).addBox(-16.0F, -24.0F, -3.0F, 32.0F, 48.0F, 6.0F), PartPose.ZERO);
		for (int i = 0; i < 4; i++) {
			pelat.addOrReplaceChild("timbun_" + i, p(PASIR, i * 13).addBox(-3.5F, -2.0F, -2.5F, 7.0F, 2.0F, 5.0F).addBox(-2.0F, -3.0F, -1.5F, 4.0F, 1.0F, 3.0F),
					PartPose.offset(-12.0F + i * 8.0F, -24.0F, 0.0F));
		}
		// drift against the foot of the slab and in the corners
		root.addOrReplaceChild("gundukan", p(PASIR, 60).addBox(-16.0F, 20.0F, -9.0F, 32.0F, 2.0F, 6.0F).addBox(-12.0F, 18.5F, -7.0F, 22.0F, 1.5F, 4.0F)
				.addBox(-16.0F, 21.0F, -12.0F, 32.0F, 1.0F, 3.0F), PartPose.ZERO);
		root.addOrReplaceChild("sudut", p(PASIR, 90).addBox(-22.0F, 18.0F, -10.0F, 7.0F, 4.0F, 5.0F).addBox(15.0F, 19.0F, -9.0F, 7.0F, 3.0F, 4.0F)
				.addBox(-22.0F, -40.0F, -4.0F, 18.0F, 2.0F, 8.0F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 128, B * 3);
	}

	/** The door's model: the grinding slide, the pouring drift and the flattening foot drift. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart pelat;
		private final ModelPart gundukan;
		private final ModelPart[] timbun = new ModelPart[4];

		public Model(ModelPart root) {
			super(root);
			this.pelat = root.getChild("pelat");
			this.gundukan = root.getChild("gundukan");
			for (int i = 0; i < 4; i++) {
				timbun[i] = pelat.getChild("timbun_" + i);
			}
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float g = state.geser;
			// the slab slides 30 px into the left wall pocket
			pelat.x = -30.0F * g + state.getar;
			pelat.y += state.getar * 0.3F;
			// the drift on top pours off one lump after another as the edge passes the jamb
			for (int i = 0; i < 4; i++) {
				float k = Mth.clamp(g * 4.0F - (3 - i), 0.0F, 1.0F);
				timbun[i].y += k * k * 48.0F;
				timbun[i].x += 30.0F * g * k;
				timbun[i].visible = k < 0.98F;
			}
			gundukan.yScale = 1.0F - 0.6F * g;
			gundukan.y += 3.0F * g;
		}
	}
}
