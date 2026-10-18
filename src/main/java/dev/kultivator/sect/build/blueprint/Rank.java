package dev.kultivator.sect.build.blueprint;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The building ranks of DESIGN 21.5 / 21.7, from humble to grand. The rank picks the roof materials and the ornament
 * budget:
 * <ul>
 * <li>{@link #A}: dorm, kitchen, coop, gallery. Deepslate tiles, polished deepslate trim; tiles, pillars, plain lattice.</li>
 * <li>{@link #B}: gate, Aula Pendiri (templates 2-9), scripture, alchemy, towers. Deepslate tiles with a jade (waxed
 * oxidized cut copper) border; + brackets, jade trim.</li>
 * <li>{@link #C}: template 1's Aula Pendiri, ancestral halls. The player's glaze; + painted beams, plaque, soffit.</li>
 * <li>{@link #D}: main halls, pagodas, Aula Dao Langit. The glaze; + double eave, gold, chiwen, roof beasts,
 * colonnade.</li>
 * </ul>
 * Real practice (grey tile with a green-glazed border, 灰瓦绿剪边); art direction, not novel canon.
 */
public enum Rank {
	A,
	B,
	C,
	D;

	/** The roof materials of this rank under {@code glaze} (C and D take the glaze). */
	public RoofMat roof(Glaze glaze) {
		return switch (this) {
			case A -> new RoofMat(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE_STAIRS,
					Blocks.POLISHED_DEEPSLATE_SLAB, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Palette.st(Blocks.POLISHED_DEEPSLATE_WALL));
			case B -> new RoofMat(Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES, Palette.jadeStairs(),
					Palette.jadeSlab(), Palette.jadeBlock(), Blocks.DEEPSLATE_BRICKS, Palette.slab(Palette.jadeSlab(), false));
			case C, D -> glaze.roof();
		};
	}

	/** Brackets under the eaves (rank B and up). */
	public boolean brackets() {
		return this != A;
	}

	/** Painted beam ends, plaque and soffit (rank C and up). */
	public boolean painted() {
		return this == C || this == D;
	}

	/** Gold pearl, chiwen and roof beasts (rank D; rank C gets them through a gilding component). */
	public boolean gold() {
		return this == D;
	}

	/** The lattice trapdoor of this rank (DESIGN 21.5 "Lattice"). */
	public Block lattice() {
		return switch (this) {
			case D -> Palette.jade(Blocks.COPPER_TRAPDOOR);
			case B, C -> Blocks.MANGROVE_TRAPDOOR;
			case A -> Blocks.POPLAR_TRAPDOOR;
		};
	}
}
