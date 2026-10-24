package dev.kultivator.client.sosok.busana;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import java.util.HashMap;
import java.util.Map;

/**
 * The model layers of Sosok bodies (DESIGN 40.3.1, contract C40.K3; module 40K), registered by 40R's
 * {@code SosokClient.init()} before the entity models are baked (pattern {@code CultivatorModels.register}):
 * <ul>
 *   <li>{@link #TUBUH} / {@link #TUBUH_RAMPING}: the player mesh on a 128 x 128 texture, so vanilla UVs fall in region
 *   A of the atlas (EMF/ETF player packs do not replace it: it is not {@code minecraft:player});</li>
 *   <li>{@link #BUSANA} / {@link #BUSANA_RAMPING}: the skeleton with every hair and garment cube ({@link SosokBusanaModel});</li>
 *   <li>{@link #WAJAH}: the skeleton with the 16 eye and 16 mouth planes ({@link WajahModel}).</li>
 * </ul>
 */
public final class SosokLapisan {

	public static final ModelLayerLocation TUBUH = new ModelLayerLocation(Kultivator.id("sosok_tubuh"), "main");
	public static final ModelLayerLocation TUBUH_RAMPING = new ModelLayerLocation(Kultivator.id("sosok_tubuh_ramping"), "main");
	public static final ModelLayerLocation BUSANA = new ModelLayerLocation(Kultivator.id("sosok_busana"), "main");
	public static final ModelLayerLocation BUSANA_RAMPING = new ModelLayerLocation(Kultivator.id("sosok_busana_ramping"), "main");
	public static final ModelLayerLocation WAJAH = new ModelLayerLocation(Kultivator.id("sosok_wajah"), "main");

	public static final int TEKSTUR = 128;

	private static boolean terdaftar;

	/** Registers the five layer definitions (once). */
	public static void register() {
		if (terdaftar) {
			return;
		}
		terdaftar = true;
		ModelLayerRegistry.registerModelLayer(TUBUH, () -> SosokTubuhModel.mesh(false));
		ModelLayerRegistry.registerModelLayer(TUBUH_RAMPING, () -> SosokTubuhModel.mesh(true));
		ModelLayerRegistry.registerModelLayer(BUSANA, () -> busana(false));
		ModelLayerRegistry.registerModelLayer(BUSANA_RAMPING, () -> busana(true));
		ModelLayerRegistry.registerModelLayer(WAJAH, WajahModel::mesh);
	}

	/** The busana mesh: skeleton + hair cubes + garment cubes. */
	static LayerDefinition busana(boolean ramping) {
		MeshDefinition mesh = kerangka();
		PartDefinition root = mesh.getRoot();
		Map<String, PartDefinition> made = new HashMap<>();
		PartDefinition head = root.getChild("head");
		PartDefinition body = root.getChild("body");
		RambutBagian.pasang(head, body, made);
		BusanaBagian.pasang(head, body, root.getChild("right_arm"), root.getChild("left_arm"), ramping, made);
		return LayerDefinition.create(mesh, TEKSTUR, TEKSTUR);
	}

	/**
	 * The player skeleton without skin cubes (pattern {@code KotaGear.skeleton()}): head, body, arms and legs with their
	 * outer-layer children, at the pivots of {@code PlayerModel.createMesh} (26.3: both arm types pivot at y 2).
	 */
	static MeshDefinition kerangka() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		body.addOrReplaceChild("jacket", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
		rightArm.addOrReplaceChild("right_sleeve", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
		leftArm.addOrReplaceChild("left_sleeve", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
		rightLeg.addOrReplaceChild("right_pants", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
		leftLeg.addOrReplaceChild("left_pants", CubeListBuilder.create(), PartPose.ZERO);
		return mesh;
	}

	private SosokLapisan() {
	}
}
