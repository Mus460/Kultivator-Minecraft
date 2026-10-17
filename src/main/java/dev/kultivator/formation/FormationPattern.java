package dev.kultivator.formation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of a protective formation, read-only: a Lodestone with Crying Obsidian at (+-4,0,0) and (0,0,+-4), on the
 * same level. Nothing here ever places, breaks or changes a block.
 */
public final class FormationPattern {

	/** Horizontal distance of each pillar from the lodestone. */
	public static final int DISTANCE = 4;
	private static final int[][] OFFSETS = {{DISTANCE, 0}, {-DISTANCE, 0}, {0, DISTANCE}, {0, -DISTANCE}};

	public static boolean isLodestone(BlockGetter level, BlockPos pos) {
		return level.getBlockState(pos).is(Blocks.LODESTONE);
	}

	public static boolean isPillar(BlockGetter level, BlockPos pos) {
		return level.getBlockState(pos).is(Blocks.CRYING_OBSIDIAN);
	}

	/** The four pillar spots of a formation anchored at {@code lodestone}. */
	public static List<BlockPos> pillars(BlockPos lodestone) {
		List<BlockPos> list = new ArrayList<>(OFFSETS.length);
		for (int[] offset : OFFSETS) {
			list.add(lodestone.offset(offset[0], 0, offset[1]));
		}
		return list;
	}

	/** Pillar spots that do not hold Crying Obsidian (the caller makes sure the area is loaded). */
	public static List<BlockPos> missing(BlockGetter level, BlockPos lodestone) {
		List<BlockPos> missing = new ArrayList<>(OFFSETS.length);
		for (BlockPos pillar : pillars(lodestone)) {
			if (!isPillar(level, pillar)) {
				missing.add(pillar);
			}
		}
		return missing;
	}

	public static boolean complete(BlockGetter level, BlockPos lodestone) {
		return missing(level, lodestone).isEmpty();
	}

	/**
	 * True when a pillar spot whose chunk is loaded no longer holds Crying Obsidian. Spots in unloaded chunks are not
	 * judged (and never loaded by this check).
	 */
	public static boolean broken(ServerLevel level, BlockPos lodestone) {
		for (BlockPos pillar : pillars(lodestone)) {
			if (level.isLoaded(pillar) && !isPillar(level, pillar)) {
				return true;
			}
		}
		return false;
	}

	private FormationPattern() {
	}
}
