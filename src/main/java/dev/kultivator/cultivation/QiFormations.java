package dev.kultivator.cultivation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Qi gathering formations built by the player out of vanilla blocks (DESIGN section 4). Detection only reads blocks
 * around the block the player sits on ({@code seat}); nothing is ever placed, broken or changed.
 * <ul>
 * <li>Tier 1, Small: Amethyst Blocks at (+-3,0,0) and (0,0,+-3) on the floor level, x1.25.</li>
 * <li>Tier 2, Great: tier 1 + Amethyst Blocks at (+-3,0,+-3) and the seat is a Gold Block, x1.6.</li>
 * <li>Tier 3, Heavenly Star: tier 2 + End Rods on the four cardinal amethyst blocks + a Beacon or Lodestone right
 * under the gold block, x2.2.</li>
 * </ul>
 */
public final class QiFormations {

	public static final int MAX_TIER = 3;
	/** Horizontal offsets of the four cardinal pillars and of the four corner pillars. */
	private static final int[][] CARDINAL = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}};
	private static final int[][] CORNERS = {{3, 3}, {3, -3}, {-3, 3}, {-3, -3}};

	public static void init() {
	}

	/** Tier 0 (none) to 3 of the formation around the block the player sits on ({@code seat} = the block below them). */
	public static int tier(ServerLevel level, BlockPos seat) {
		if (!all(level, seat, CARDINAL, 0, Blocks.AMETHYST_BLOCK)) {
			return 0;
		}
		if (!level.getBlockState(seat).is(Blocks.GOLD_BLOCK) || !all(level, seat, CORNERS, 0, Blocks.AMETHYST_BLOCK)) {
			return 1;
		}
		BlockState base = level.getBlockState(seat.below());
		if (!all(level, seat, CARDINAL, 1, Blocks.END_ROD) || !(base.is(Blocks.BEACON) || base.is(Blocks.LODESTONE))) {
			return 2;
		}
		return 3;
	}

	/** XP multiplier of a tier. */
	public static double multiplier(int tier) {
		return switch (tier) {
			case 1 -> 1.25D;
			case 2 -> 1.6D;
			case 3 -> 2.2D;
			default -> 1.0D;
		};
	}

	/** "Small Qi Gathering Formation" and so on; tier 0 gives an empty text. */
	public static Component name(int tier) {
		if (tier < 1 || tier > MAX_TIER) {
			return Component.empty();
		}
		return Component.translatable("kultivator.meditation.formation." + tier);
	}

	/** RGB colour of a tier's qi lines (violet, gold, starlight). */
	public static int color(int tier) {
		return switch (tier) {
			case 2 -> 0xFFD54A;
			case 3 -> 0xBFF6FF;
			default -> 0xC69CFF;
		};
	}

	private static boolean all(ServerLevel level, BlockPos seat, int[][] offsets, int dy, Block block) {
		for (int[] offset : offsets) {
			if (!level.getBlockState(seat.offset(offset[0], dy, offset[1])).is(block)) {
				return false;
			}
		}
		return true;
	}

	private QiFormations() {
	}
}
