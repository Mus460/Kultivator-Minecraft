package dev.kultivator.sect.build.blueprint;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The blocks one roof is made of (DESIGN 21.5 "Roof rank hierarchy"): the field (tiles), the trim (eave row, hips,
 * bargeboards, chiwen), the ridge block and the ridge cap ({@code cap} is placed on top of the ridge block when the
 * building is at least 11 wide). Built by {@link Rank#roof(Glaze)} and {@link Glaze#roof()}.
 */
public record RoofMat(Block fieldStairs, Block fieldSlab, Block fieldFull, Block trimStairs, Block trimSlab, Block trimFull, Block ridge,
		BlockState cap) {
}
