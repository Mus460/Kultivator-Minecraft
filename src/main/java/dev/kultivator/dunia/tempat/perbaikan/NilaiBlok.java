package dev.kultivator.dunia.tempat.perbaikan;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The Nilai Batu worth of one block of a place (DESIGN 38.R.4), from tags, block classes and the block's id: earth and
 * grass 0.05, stone and cobble 0.1, wood and planks 0.2, wool and carpet 0.15, glass and panes 0.3, bricks, stone bricks
 * and terracotta 0.4, copper, quartz and fine decoration 0.6, doors and trapdoors 0.5, lanterns, chains and bars 1.0,
 * a bed 2, a bell 20, plants 0.05, anything else 0.25. Also the bill's line groups ({@link #kelompok}) so a bill reads
 * "Batu bata x24" instead of forty block names. Cached per block (server thread only).
 */
public final class NilaiBlok {

	/** Bill line groups, also the lang suffix {@code kultivator.tempat_perbaikan.kelompok.<id>}. */
	public static final String TANAH = "tanah";
	public static final String BATU = "batu";
	public static final String KAYU = "kayu";
	public static final String KAIN = "kain";
	public static final String KACA = "kaca";
	public static final String BATA = "bata";
	public static final String HIAS = "hias";
	public static final String PINTU = "pintu";
	public static final String LOGAM = "logam";
	public static final String RANJANG = "ranjang";
	public static final String LONCENG = "lonceng";
	public static final String TANAMAN = "tanaman";
	public static final String LAIN = "lain";

	private record Nilai(double nb, String kelompok) {
	}

	private static final Map<Block, Nilai> CACHE = new IdentityHashMap<>();

	/** Worth in NB (one decimal matters on the bill). Air and fluids are worth nothing. */
	public static double nilai(BlockState state) {
		return of(state).nb;
	}

	/** The bill line group of {@code state} (see the constants). */
	public static String kelompok(BlockState state) {
		return of(state).kelompok;
	}

	private static Nilai of(BlockState state) {
		if (state == null || state.isAir()) {
			return new Nilai(0.0D, LAIN);
		}
		Block block = state.getBlock();
		Nilai known = CACHE.get(block);
		if (known == null) {
			known = hitung(state, block);
			CACHE.put(block, known);
		}
		return known;
	}

	private static Nilai hitung(BlockState state, Block block) {
		if (!state.getFluidState().isEmpty() && state.getFluidState().isSource() && state.getBlock().defaultBlockState().getFluidState().isSource()) {
			return new Nilai(0.0D, LAIN);
		}
		String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
		if (block instanceof BellBlock) {
			return new Nilai(20.0D, LONCENG);
		}
		if (block instanceof BedBlock || state.is(BlockTags.BEDS)) {
			return new Nilai(2.0D, RANJANG);
		}
		if (block instanceof LanternBlock || block instanceof ChainBlock || block instanceof IronBarsBlock && !(block instanceof StainedGlassPaneBlock)
				&& !path.contains("glass")) {
			return new Nilai(1.0D, LOGAM);
		}
		if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock || state.is(BlockTags.DOORS)
				|| state.is(BlockTags.TRAPDOORS)) {
			return new Nilai(0.5D, PINTU);
		}
		if (block instanceof TransparentBlock || block instanceof StainedGlassBlock || block instanceof StainedGlassPaneBlock || path.contains("glass")) {
			return new Nilai(0.3D, KACA);
		}
		if (path.contains("copper") || path.contains("quartz") || path.contains("gold") || path.contains("amethyst") || path.contains("prismarine")
				|| path.contains("purpur") || path.startsWith("chiseled_") || path.contains("glazed")) {
			return new Nilai(0.6D, HIAS);
		}
		if (path.contains("brick") || state.is(BlockTags.STONE_BRICKS) || state.is(BlockTags.TERRACOTTA) || path.contains("terracotta")
				|| path.contains("tile")) {
			return new Nilai(0.4D, BATA);
		}
		if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || path.endsWith("_carpet") || path.contains("wool")) {
			return new Nilai(0.15D, KAIN);
		}
		if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) || path.contains("planks") || path.contains("_log") || path.contains("_wood")
				|| path.contains("bamboo") || path.contains("oak") || path.contains("spruce") || path.contains("birch") || path.contains("jungle")
				|| path.contains("acacia") || path.contains("cherry") || path.contains("mangrove") || path.contains("crimson") || path.contains("warped")
				|| path.contains("pale_oak") || path.equals("ladder") || path.contains("bookshelf") || path.contains("barrel")) {
			return new Nilai(0.2D, KAYU);
		}
		if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || path.equals("gravel") || path.equals("dirt_path") || path.equals("farmland")
				|| path.equals("clay") || path.contains("mud")) {
			return new Nilai(0.05D, TANAH);
		}
		if (block instanceof VegetationBlock || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.CROPS)
				|| state.is(BlockTags.LEAVES) || path.contains("vine") || path.contains("moss")) {
			return new Nilai(0.05D, TANAMAN);
		}
		if (state.is(BlockTags.BASE_STONE_OVERWORLD) || path.contains("stone") || path.contains("cobble") || path.contains("andesite")
				|| path.contains("diorite") || path.contains("granite") || path.contains("deepslate") || path.contains("tuff") || path.contains("basalt")
				|| path.contains("blackstone") || path.contains("calcite")) {
			return new Nilai(0.1D, BATU);
		}
		return new Nilai(0.25D, LAIN);
	}

	private NilaiBlok() {
	}
}
