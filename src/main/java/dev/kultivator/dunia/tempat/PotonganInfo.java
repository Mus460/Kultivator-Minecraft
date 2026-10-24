package dev.kultivator.dunia.tempat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * One generated piece of a place (contract C38.W1, {@code TempatApi.potongan}): the template it came from, its origin,
 * rotation and box. Used by NPC life (points), repair blueprints (38.R.1) and {@code uji tempat cek}.
 */
public record PotonganInfo(Identifier templat, BlockPos pos, Rotation rot, BoundingBox kotak) {

	public PotonganInfo {
		pos = pos.immutable();
	}
}
