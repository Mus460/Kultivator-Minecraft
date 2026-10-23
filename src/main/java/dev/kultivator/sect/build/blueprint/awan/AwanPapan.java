package dev.kultivator.sect.build.blueprint.awan;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * The texts of the sky plaques (DESIGN 37.4.5): the builder writes them when it places the sign (as it writes the sect's
 * name on the hall plaques). A pale-oak wall sign is the Gate of the Nine Heavens (九天门), a bamboo one the Cloud Stair
 * (雲梯), a birch one names the satellite it stands on: on A7-A9 after the drifting mountains of the <i>Liezi</i>
 * (Penglai, Fangzhang, Yingzhou, Daiyu, Yuanjiao, the last two "moored again" here), else by its function.
 */
public final class AwanPapan {

	/** The four lines of a sky plaque, or null when the block is no sky plaque. {@code local} is template-local. */
	public static @Nullable Component[] lines(int template, Block block, BlockPos local) {
		if (block == Blocks.PALE_OAK_WALL_SIGN) {
			return new Component[]{Component.empty(), Component.literal("九天门"), Component.translatable("kultivator.blueprint.awan.papan.gerbang"),
					Component.empty()};
		}
		if (block == Blocks.BAMBOO_WALL_SIGN) {
			return new Component[]{Component.empty(), Component.literal("雲梯"), Component.translatable("kultivator.blueprint.awan.papan.tangga"),
					Component.empty()};
		}
		if (block == Blocks.BIRCH_WALL_SIGN) {
			AwanLayout layout = AwanLayout.of(template);
			AwanLayout.Satelit best = null;
			double bestD = Double.MAX_VALUE;
			for (AwanLayout.Satelit s : layout.satellites) {
				double d = Math.hypot(local.getX() - s.pulau().cx, local.getZ() - s.pulau().cz);
				if (d < bestD) {
					bestD = d;
					best = s;
				}
			}
			if (best == null) {
				return null;
			}
			String key = (layout.row.n() >= 7 ? "kultivator.blueprint.awan.guixu." : "kultivator.blueprint.awan.satelit.") + best.fungsi().id;
			return new Component[]{Component.empty(), Component.translatable(key), Component.translatable("kultivator.blueprint.awan.satelit_sub."
					+ best.fungsi().id), Component.empty()};
		}
		return null;
	}

	private AwanPapan() {
	}
}
