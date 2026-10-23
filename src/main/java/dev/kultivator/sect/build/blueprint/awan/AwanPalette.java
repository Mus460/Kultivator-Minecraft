package dev.kultivator.sect.build.blueprint.awan;

import dev.kultivator.sect.build.blueprint.Palette;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The blocks of the Awan family of sky islands (DESIGN 37.4.1). Everything here is either inert or kept from growing
 * (cave vines at their last age), so an island never changes by itself:
 * <ul>
 * <li>Banned (checked by the sky audit): budding amethyst (grows), pointed dripstone and every gravity block (sand,
 * gravel, anvils, scaffolding), free water or lava, fire, glow lichen and vines (DESIGN 21.5 bans them in builds: they
 * spread; the underside is lit by froglights and glow berries instead, a deviation from 37.4.1 noted in the progress
 * file).</li>
 * <li>Cliffs: strata of stone, andesite, tuff and calcite, a calcite band every fifth layer; the belly shell darkens with
 * depth (dirt and rooted dirt, stone and andesite, tuff with calcite streaks, deepslate).</li>
 * <li>Crystals (T9): an amethyst spine (exact items, one per layer), tinted-glass flanks, a calcite collar, amethyst
 * clusters and large buds on the tips.</li>
 * </ul>
 */
public final class AwanPalette {

	public static final Block AWAN_LANTAI = Blocks.SMOOTH_QUARTZ;
	public static final Block AWAN_ANAK_TANGGA = Blocks.SMOOTH_QUARTZ_SLAB;
	public static final Block AWAN_TEPI = Blocks.CHISELED_QUARTZ_BLOCK;
	public static final Block[] TEBING = {Blocks.STONE, Blocks.ANDESITE, Blocks.TUFF, Blocks.STONE, Blocks.CALCITE};
	public static final Block[] PERUT = {Blocks.STONE, Blocks.ANDESITE, Blocks.TUFF, Blocks.DEEPSLATE};
	public static final Block AKAR = Blocks.MANGROVE_ROOTS;
	public static final Block AKAR_TANAH = Blocks.ROOTED_DIRT;
	public static final Block KRISTAL = Blocks.AMETHYST_BLOCK;
	public static final Block KRISTAL_KACA = Blocks.TINTED_GLASS;
	public static final Block KRISTAL_UJUNG = Blocks.AMETHYST_CLUSTER;
	public static final Block KRISTAL_TUNAS = Blocks.LARGE_AMETHYST_BUD;
	public static final Block PAGAR_KAYU = Blocks.PALE_OAK_FENCE;
	/** A white balustrade (26.3 has no polished andesite wall; diorite reads as marble). */
	public static final Block PAGAR_BATU = Blocks.DIORITE_WALL;
	public static final Block PAGAR_TUTUP = Blocks.SMOOTH_QUARTZ_SLAB;
	public static final Block LUMUT_GANTUNG = Blocks.HANGING_ROOTS;

	public static BlockState giok() {
		return Palette.st(Palette.jadeBlock());
	}

	public static BlockState giokLantai(boolean top) {
		return Palette.slab(Palette.jadeSlab(), top);
	}

	public static BlockState giokRantai() {
		return Palette.jadeChain();
	}

	/** A cave vine head at its last age (never grows) with or without glow berries. */
	public static BlockState sulur(boolean berries) {
		BlockState state = Palette.with(Blocks.CAVE_VINES.defaultBlockState(), BlockStateProperties.AGE_25, 25);
		return Palette.with(state, BlockStateProperties.BERRIES, berries);
	}

	/** A cave vine body segment (glow berries optional). */
	public static BlockState sulurBatang(boolean berries) {
		return Palette.with(Blocks.CAVE_VINES_PLANT.defaultBlockState(), BlockStateProperties.BERRIES, berries);
	}

	/** An amethyst cluster or bud pointing {@code facing} (down under the crystals). */
	public static BlockState kristalUjung(Block block, Direction facing) {
		return Palette.dry(Palette.with(block.defaultBlockState(), BlockStateProperties.FACING, facing));
	}

	/** True when {@code state} must never appear in an Awan plan (the sky audit). */
	public static boolean banned(BlockState state) {
		return state.is(Blocks.BUDDING_AMETHYST) || state.is(Blocks.POINTED_DRIPSTONE) || state.is(Blocks.LAVA) || state.is(Blocks.GLOW_LICHEN)
				|| state.is(Blocks.VINE) || state.is(Blocks.SCAFFOLDING);
	}

	private AwanPalette() {
	}
}
