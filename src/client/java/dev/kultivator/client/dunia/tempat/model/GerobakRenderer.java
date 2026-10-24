package dev.kultivator.client.dunia.tempat.model;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.ekonomi.GerobakEntity;
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

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Gerobak ({@code kultivator:gerobak}, DESIGN 38.O.2): the caravan cart of 38EK. A plank bed with side boards, stake
 * pockets and iron corner straps, two spoked wheels (a twelve-segment felloe with an iron tyre, six spokes and an iron
 * hub with a linchpin) that turn with the distance travelled (angle = distance / wheel radius), two shafts reaching
 * forward to the horse with a yoke, a paper lantern swinging on a hook at the front corner, and one of three loads
 * ({@link KaitModel#gerobakMuatan}): three iron-banded chests roped down, four tied sacks, or two lidded jars bedded in
 * straw. The bed rocks ±1.5° while it rolls and the lantern swings with it. Texture
 * {@code textures/entity/tempat/gerobak.png} (128 × 224: light wood, iron, sacking, crate wood, jar glaze, rope, red
 * lantern paper).
 */
public class GerobakRenderer extends BendaRenderer<GerobakEntity, GerobakRenderer.State, GerobakRenderer.Model> {

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kultivator.id("gerobak"), "main");
	static final Identifier TEXTURE = Kultivator.id("textures/entity/tempat/gerobak.png");
	/** Wheel radius in blocks (8 px). */
	private static final float RADIUS = 0.5F;

	/** Per cart: last x, last z, rolled distance (client only, forgotten with the entity). */
	private final Map<GerobakEntity, double[]> rolled = new WeakHashMap<>();

	public static class State extends BendaRenderer.State {
		public float wheel;
		public float rolling;
		public int load;
	}

	public GerobakRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), TEXTURE, null, 1.0F);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, GerobakRenderer::mesh);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected double reach(GerobakEntity entity) {
		return 1.2D;
	}

	@Override
	public void extractRenderState(GerobakEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		double x = Mth.lerp(partialTicks, entity.xo, entity.getX());
		double z = Mth.lerp(partialTicks, entity.zo, entity.getZ());
		double[] r = rolled.computeIfAbsent(entity, e -> new double[] {x, z, 0.0D, 0.0D});
		double dx = x - r[0];
		double dz = z - r[1];
		double step = Math.sqrt(dx * dx + dz * dz);
		if (step < 4.0D) {
			// forward or backward along the cart's facing
			float yawRad = state.yaw * Mth.DEG_TO_RAD;
			double forward = -dx * Mth.sin(yawRad) + dz * Mth.cos(yawRad);
			r[2] += Math.signum(forward) * step;
		}
		r[3] = r[3] * 0.85D + step * 0.15D;
		r[0] = x;
		r[1] = z;
		state.wheel = (float) (r[2] / RADIUS);
		state.rolling = (float) Math.min(1.0D, r[3] * 20.0D);
		state.load = Math.floorMod(KaitModel.gerobakMuatan.baca(entity), 3);
	}

	static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition bed = root.addOrReplaceChild("bed", CubeListBuilder.create(), PartPose.offset(0.0F, 14.0F, 0.0F));
		// the plank floor in two halves, the side and end boards, iron corner straps, a front step
		bed.addOrReplaceChild("floor", band(0).addBox(-12.0F, 0.0F, -20.0F, 24.0F, 1.0F, 20.0F).addBox(-12.0F, 0.0F, 0.0F, 24.0F, 1.0F, 20.0F),
				PartPose.ZERO);
		bed.addOrReplaceChild("sides", band(0).addBox(-12.0F, -6.0F, -20.0F, 1.0F, 6.0F, 20.0F).addBox(-12.0F, -6.0F, 0.0F, 1.0F, 6.0F, 20.0F)
				.addBox(11.0F, -6.0F, -20.0F, 1.0F, 6.0F, 20.0F).addBox(11.0F, -6.0F, 0.0F, 1.0F, 6.0F, 20.0F)
				.addBox(-11.0F, -5.0F, 19.0F, 22.0F, 5.0F, 1.0F).addBox(-11.0F, -4.0F, -20.0F, 22.0F, 4.0F, 1.0F), PartPose.ZERO);
		bed.addOrReplaceChild("straps", band(1).addBox(-12.4F, -6.2F, -20.4F, 1.6F, 7.4F, 1.6F).addBox(10.8F, -6.2F, -20.4F, 1.6F, 7.4F, 1.6F)
				.addBox(-12.4F, -6.2F, 18.8F, 1.6F, 7.4F, 1.6F).addBox(10.8F, -6.2F, 18.8F, 1.6F, 7.4F, 1.6F)
				.addBox(-12.3F, -3.2F, -1.0F, 0.6F, 1.0F, 2.0F).addBox(11.7F, -3.2F, -1.0F, 0.6F, 1.0F, 2.0F), PartPose.ZERO);
		bed.addOrReplaceChild("axle", band(1).addBox(-15.0F, -1.0F, -1.0F, 30.0F, 2.0F, 2.0F), PartPose.offset(0.0F, 3.0F, 4.0F));
		// shafts to the horse (forward is -z)
		bed.addOrReplaceChild("shaft_l", band(3).addBox(-0.5F, -0.5F, -30.0F, 1.0F, 1.0F, 30.0F), PartPose.offsetAndRotation(8.0F, 1.0F, -20.0F, -0.12F, 0.0F, 0.0F));
		bed.addOrReplaceChild("shaft_r", band(3).addBox(-0.5F, -0.5F, -30.0F, 1.0F, 1.0F, 30.0F), PartPose.offsetAndRotation(-8.0F, 1.0F, -20.0F, -0.12F, 0.0F, 0.0F));
		bed.addOrReplaceChild("yoke", band(3).addBox(-9.0F, -0.5F, -0.5F, 18.0F, 1.0F, 1.0F), PartPose.offset(0.0F, -2.5F, -49.5F));
		// loads
		PartDefinition chests = bed.addOrReplaceChild("load_chests", CubeListBuilder.create(), PartPose.ZERO);
		for (int i = 0; i < 3; i++) {
			float z = -14.0F + i * 11.0F;
			float x = i == 1 ? 1.0F : -1.5F;
			chests.addOrReplaceChild("chest_" + i, band(3).addBox(-5.0F, -10.0F, -5.0F, 10.0F, 10.0F, 10.0F), PartPose.offsetAndRotation(x, 0.0F, z, 0.0F, i * 0.12F - 0.1F, 0.0F));
			chests.addOrReplaceChild("band_" + i, band(1).addBox(-5.3F, -10.3F, -1.0F, 10.6F, 10.6F, 2.0F), PartPose.offsetAndRotation(x, 0.0F, z, 0.0F, i * 0.12F - 0.1F, 0.0F));
			chests.addOrReplaceChild("hasp_" + i, band(1).addBox(-1.0F, -8.0F, -5.6F, 2.0F, 2.5F, 0.6F), PartPose.offsetAndRotation(x, 0.0F, z, 0.0F, i * 0.12F - 0.1F, 0.0F));
		}
		// two ropes over the chests, tied down to the side boards
		chests.addOrReplaceChild("rope_a", band(5).addBox(-11.5F, -10.6F, -0.4F, 23.0F, 0.8F, 0.8F).addBox(-11.6F, -10.6F, -0.4F, 0.8F, 6.0F, 0.8F)
				.addBox(10.8F, -10.6F, -0.4F, 0.8F, 6.0F, 0.8F), PartPose.offset(0.0F, 0.0F, -9.0F));
		chests.addOrReplaceChild("rope_b", band(5).addBox(-11.5F, -10.6F, -0.4F, 23.0F, 0.8F, 0.8F).addBox(-11.6F, -10.6F, -0.4F, 0.8F, 6.0F, 0.8F)
				.addBox(10.8F, -10.6F, -0.4F, 0.8F, 6.0F, 0.8F), PartPose.offset(0.0F, 0.0F, 8.0F));
		PartDefinition sacks = bed.addOrReplaceChild("load_sacks", CubeListBuilder.create(), PartPose.ZERO);
		for (int i = 0; i < 4; i++) {
			float z = -15.0F + i * 9.0F;
			sacks.addOrReplaceChild("sack_" + i, band(2).addBox(-4.0F, -6.0F, -3.0F, 8.0F, 6.0F, 6.0F).addBox(-3.0F, -7.5F, -2.0F, 6.0F, 1.5F, 4.0F),
					PartPose.offsetAndRotation(i % 2 == 0 ? -4.5F : 4.0F, 0.0F, z, 0.0F, (i % 3 - 1) * 0.3F, (i % 2 == 0 ? 1 : -1) * 0.1F));
			sacks.addOrReplaceChild("tie_" + i, band(5).addBox(-1.0F, -8.2F, -1.0F, 2.0F, 0.8F, 2.0F).addBox(-0.4F, -8.6F, -0.4F, 0.8F, 1.6F, 0.8F),
					PartPose.offsetAndRotation(i % 2 == 0 ? -4.5F : 4.0F, 0.0F, z, 0.0F, 0.0F, 0.0F));
		}
		PartDefinition jars = bed.addOrReplaceChild("load_jars", CubeListBuilder.create(), PartPose.ZERO);
		for (int i = 0; i < 2; i++) {
			float z = -9.0F + i * 18.0F;
			jars.addOrReplaceChild("straw_" + i, band(0).addBox(-5.0F, -3.0F, -5.0F, 10.0F, 3.0F, 10.0F), PartPose.offset(0.0F, 0.0F, z));
			jars.addOrReplaceChild("jar_" + i, band(4).addBox(-3.0F, -12.0F, -3.0F, 6.0F, 10.0F, 6.0F).addBox(-2.0F, -14.0F, -2.0F, 4.0F, 2.0F, 4.0F),
					PartPose.offset(0.0F, 0.0F, z));
			jars.addOrReplaceChild("lid_" + i, band(3).addBox(-2.4F, -15.0F, -2.4F, 4.8F, 1.0F, 4.8F), PartPose.offset(0.0F, 0.0F, z));
		}
		// stake pockets along the side boards and a driver's footboard at the front
		bed.addOrReplaceChild("stakes", band(3).addBox(-13.0F, -8.0F, -14.0F, 1.0F, 9.0F, 1.5F).addBox(-13.0F, -8.0F, 12.0F, 1.0F, 9.0F, 1.5F)
				.addBox(12.0F, -8.0F, -14.0F, 1.0F, 9.0F, 1.5F).addBox(12.0F, -8.0F, 12.0F, 1.0F, 9.0F, 1.5F), PartPose.ZERO);
		bed.addOrReplaceChild("footboard", band(0).addBox(-8.0F, 2.0F, -24.0F, 16.0F, 1.0F, 4.0F), PartPose.ZERO);
		// the front-corner lantern: a hook post, and the lantern as a child so it can swing
		PartDefinition post = bed.addOrReplaceChild("lantern_post", band(3).addBox(-0.5F, -14.0F, -0.5F, 1.0F, 14.0F, 1.0F)
				.addBox(-0.5F, -14.0F, -3.5F, 1.0F, 1.0F, 3.5F), PartPose.offset(-11.5F, -5.0F, -19.5F));
		PartDefinition lantern = post.addOrReplaceChild("lantern", band(1).addBox(-0.3F, 0.0F, -0.3F, 0.6F, 1.5F, 0.6F), PartPose.offset(0.0F, -13.0F, -3.0F));
		lantern.addOrReplaceChild("paper", band(6).addBox(-2.0F, 1.5F, -2.0F, 4.0F, 4.5F, 4.0F), PartPose.ZERO);
		lantern.addOrReplaceChild("caps", band(1).addBox(-1.5F, 1.2F, -1.5F, 3.0F, 0.6F, 3.0F).addBox(-1.5F, 5.8F, -1.5F, 3.0F, 0.6F, 3.0F)
				.addBox(-0.4F, 6.4F, -0.4F, 0.8F, 1.6F, 0.8F), PartPose.ZERO);
		// wheels: a twelve-segment felloe (each a 4 px arc, 1.9 px deep) with iron tyre plates, six spokes, an iron hub and pin
		for (int side = -1; side <= 1; side += 2) {
			String s = side < 0 ? "r" : "l";
			PartDefinition wheel = root.addOrReplaceChild("wheel_" + s, CubeListBuilder.create(), PartPose.offset(side * 14.0F, 17.0F, 4.0F));
			for (int k = 0; k < 12; k++) {
				PartDefinition seg = wheel.addOrReplaceChild("felloe_" + k, band(0).addBox(-1.0F, -7.6F, -2.05F, 2.0F, 1.9F, 4.1F),
						PartPose.rotation(k * Mth.PI / 6.0F, 0.0F, 0.0F));
				seg.addOrReplaceChild("tyre", band(1).addBox(-1.2F, -8.2F, -2.1F, 2.4F, 0.7F, 4.2F), PartPose.ZERO);
			}
			for (int k = 0; k < 3; k++) {
				wheel.addOrReplaceChild("spoke_" + k, band(3).addBox(-0.5F, -6.2F, -0.6F, 1.0F, 12.4F, 1.2F), PartPose.rotation(k * Mth.PI / 3.0F, 0.0F, 0.0F));
			}
			wheel.addOrReplaceChild("hub", band(1).addBox(-1.6F, -2.0F, -2.0F, 3.2F, 4.0F, 4.0F).addBox(-2.0F, -1.4F, -1.4F, 4.0F, 2.8F, 2.8F),
					PartPose.ZERO);
			wheel.addOrReplaceChild("pin", band(1).addBox(side * 2.0F - 0.3F, -0.3F, -1.8F, 0.6F, 0.6F, 3.6F), PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 128, 224);
	}

	/** The cart's model: rolling wheels, a rocking bed, the chosen load. */
	public static final class Model extends EntityModel<State> {

		private final ModelPart bed;
		private final ModelPart chests;
		private final ModelPart sacks;
		private final ModelPart jars;
		private final ModelPart wheelL;
		private final ModelPart wheelR;
		private final ModelPart lantern;

		public Model(ModelPart root) {
			super(root);
			this.bed = root.getChild("bed");
			this.chests = bed.getChild("load_chests");
			this.sacks = bed.getChild("load_sacks");
			this.jars = bed.getChild("load_jars");
			this.wheelL = root.getChild("wheel_l");
			this.wheelR = root.getChild("wheel_r");
			this.lantern = bed.getChild("lantern_post").getChild("lantern");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			wheelL.xRot = state.wheel;
			wheelR.xRot = state.wheel;
			bed.zRot = Mth.sin(state.ageInTicks * 0.55F) * 1.5F * Mth.DEG_TO_RAD * state.rolling;
			bed.xRot = Mth.sin(state.ageInTicks * 0.31F) * 0.6F * Mth.DEG_TO_RAD * state.rolling;
			// the lantern swings against the bed's rocking, and a little in the wind when the cart stands
			lantern.zRot = -bed.zRot * 3.0F + Mth.sin(state.ageInTicks * 0.05F) * 0.05F;
			lantern.xRot = Mth.sin(state.ageInTicks * 0.42F + 1.3F) * 0.25F * state.rolling + Mth.sin(state.ageInTicks * 0.037F) * 0.04F;
			chests.visible = state.load == 0;
			sacks.visible = state.load == 1;
			jars.visible = state.load == 2;
		}
	}
}
