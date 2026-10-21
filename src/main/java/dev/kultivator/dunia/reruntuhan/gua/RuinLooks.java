package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.data.Realm;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The look of each ruin tier (the same palettes as {@code tools/gen_dunia_reruntuhan.py}: Kecil mossy stone, Agung tuff
 * and deepslate with gold, Kuno blackstone with gilded stone) and the realms each tier is meant for (DESIGN 34.5.5:
 * Kecil Qi Refining - Foundation, Agung Golden Core - Nascent Soul, Kuno Deity Transformation - Dao Union).
 */
public final class RuinLooks {

	public static BlockState wall(int tier) {
		return switch (tier) {
			case 2 -> Blocks.TUFF_BRICKS.defaultBlockState();
			case 3 -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
			default -> Blocks.STONE_BRICKS.defaultBlockState();
		};
	}

	/** The carved panels of a Gerbang Segel. */
	public static BlockState door(int tier) {
		return switch (tier) {
			case 2 -> Blocks.CHISELED_TUFF_BRICKS.defaultBlockState();
			case 3 -> Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
			default -> Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
		};
	}

	/** The lowest realm the outer seal answers to. */
	public static Realm minRealm(int tier) {
		return switch (tier) {
			case 2 -> Realm.GOLDEN_CORE;
			case 3 -> Realm.DEITY_TRANSFORMATION;
			default -> Realm.QI_REFINING;
		};
	}

	/** The guardian's health before the party factor. */
	public static double guardianHealth(int tier) {
		return switch (tier) {
			case 2 -> 520.0D;
			case 3 -> 1400.0D;
			default -> 170.0D;
		};
	}

	/** The guardian's melee damage. */
	public static double guardianAttack(int tier) {
		return switch (tier) {
			case 2 -> 14.0D;
			case 3 -> 26.0D;
			default -> 7.0D;
		};
	}

	/** The Dao colour of a ruin's remnant soul robe. */
	public static int robe(int tier) {
		return switch (tier) {
			case 2 -> 0xFFD54A;
			case 3 -> 0xC89BFF;
			default -> 0x9FE0C0;
		};
	}

	private RuinLooks() {
	}
}
