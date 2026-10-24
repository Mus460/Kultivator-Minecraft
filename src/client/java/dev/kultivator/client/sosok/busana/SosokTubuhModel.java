package dev.kultivator.client.sosok.busana;

import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;

/**
 * The Sosok body mesh (DESIGN 40.3.1, module 40K): {@code PlayerModel.createMesh(CubeDeformation.NONE, slim)} on a
 * 128 x 128 texture, so every vanilla UV (head and hat, body and jacket, arms and sleeves, legs and trousers) lands in
 * region A of the Sosok atlas. The outer layers keep the vanilla inflation (0.5 hat, 0.25 others). Baked by 40R
 * ({@code SosokJembatan.tubuh}) into a plain {@code PlayerModel}: casts, duels, meditation, duty poses and Gerak clips
 * move it through {@code PlayerModelMixin} like the player.
 */
public final class SosokTubuhModel {

	/** The layer definition of {@code sosok_tubuh} (wide arms) or {@code sosok_tubuh_ramping} (slim arms). */
	public static LayerDefinition mesh(boolean ramping) {
		return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, ramping), SosokLapisan.TEKSTUR, SosokLapisan.TEKSTUR);
	}

	private SosokTubuhModel() {
	}
}
