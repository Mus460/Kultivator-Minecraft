package dev.kultivator.client.sosok.busana;

import dev.kultivator.client.sosok.SosokDetail;
import dev.kultivator.client.sosok.SosokRender;
import dev.kultivator.client.sosok.gerak.GerakData;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.Direction;

import java.util.EnumSet;

/**
 * The animated face of a Sosok body (DESIGN 40.3.1, 40.5.9; module 40K): children of the head, 16 eye planes
 * {@code addBox(-4, -6, -4.12, 8, 4, 0, NORTH)} and 16 mouth planes {@code addBox(-4, -3, -4.13, 8, 3, 0, NORTH)} whose
 * {@code texOffs} are the strips B1/B2 of the atlas (composed by {@code KulitPenyusun} with every eye and mouth state).
 * Exactly one eye plane and one mouth plane are visible per frame - the strips 40G's {@code Wajah} chose
 * ({@code GerakData.mata/mulut}). The open eye and the closed mouth are already painted on the skin, so a face at rest
 * draws nothing. The planes sit 0.12 px in front of the head (no z-fighting from afar) and behind the hat layer
 * (-4.5: fringes stay in front). Depth 0 with only the north face: Sodium's own cube path draws it like any cube.
 */
public class WajahModel extends PlayerModel {

	public static final int STRIP = 16;
	private final ModelPart[] mata = new ModelPart[STRIP];
	private final ModelPart[] mulut = new ModelPart[STRIP];

	public WajahModel(ModelPart root) {
		super(root, false);
		ModelPart head = root.getChild("head");
		for (int i = 0; i < STRIP; i++) {
			mata[i] = head.getChild("mata_" + i);
			mulut[i] = head.getChild("mulut_" + i);
		}
	}

	/** The layer definition: the bare skeleton with the 32 planes on the head. */
	static LayerDefinition mesh() {
		MeshDefinition mesh = SosokLapisan.kerangka();
		PartDefinition head = mesh.getRoot().getChild("head");
		EnumSet<Direction> depan = EnumSet.of(Direction.NORTH);
		for (int i = 0; i < STRIP; i++) {
			head.addOrReplaceChild("mata_" + i,
					CubeListBuilder.create().texOffs(64 + 8 * (i % 8), 4 * (i / 8)).addBox(-4.0F, -6.0F, -4.12F, 8.0F, 4.0F, 0.0F, depan),
					PartPose.ZERO);
			head.addOrReplaceChild("mulut_" + i,
					CubeListBuilder.create().texOffs(64 + 8 * (i % 8), 8 + 3 * (i / 8)).addBox(-4.0F, -3.0F, -4.13F, 8.0F, 3.0F, 0.0F, depan),
					PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, SosokLapisan.TEKSTUR, SosokLapisan.TEKSTUR);
	}

	@Override
	public void setupAnim(AvatarRenderState state) {
		super.setupAnim(state);
		int m = 0;
		int u = 0;
		SosokRender.Keadaan k = SosokRender.keadaan(state);
		GerakData gd = GerakData.dari(state);
		if (k != null && gd != null && SosokDetail.wajah() && k.lod == SosokDetail.Lod.PENUH) {
			m = Math.floorMod(gd.mata, STRIP);
			u = Math.floorMod(gd.mulut, STRIP);
		}
		for (int i = 0; i < STRIP; i++) {
			mata[i].visible = i == m && m != 0;
			mulut[i].visible = i == u && u != 0;
		}
	}

	/** True when this frame shows any plane (the layer skips the draw otherwise). */
	static boolean adaWajah(AvatarRenderState state) {
		SosokRender.Keadaan k = SosokRender.keadaan(state);
		GerakData gd = GerakData.dari(state);
		return k != null && gd != null && SosokDetail.wajah() && k.lod == SosokDetail.Lod.PENUH
				&& (Math.floorMod(gd.mata, STRIP) != 0 || Math.floorMod(gd.mulut, STRIP) != 0);
	}
}
