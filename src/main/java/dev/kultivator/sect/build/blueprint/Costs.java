package dev.kultivator.sect.build.blueprint;

import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.Bill;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Placed costs (DESIGN 21.11 "Placed cost, by material and never by role"), in centi-units of a {@link Bahan}, plus the
 * exact items that are paid 1:1 (cauldrons, bookshelves, lodestones...). Bulk masonry is stretched 2 : 1 by the sect's
 * artisans (a full block costs half a unit); hidden fill a quarter; water and air are free. A block no rule knows falls
 * back to Batu ½ and is reported by the audit ({@link #known}). Thread-safe (emission and dry runs run off the server
 * thread). Numbers are game design.
 */
public final class Costs {

	/** One placed block's price: a category and centi-units, or an exact item (count per block). */
	public record Cost(@Nullable Bahan cat, long centi, @Nullable Item exact, boolean known) {
		public static final Cost FREE = new Cost(null, 0L, null, true);

		static Cost of(Bahan cat, long centi) {
			return new Cost(cat, centi, null, true);
		}

		static Cost item(Item item) {
			return new Cost(null, 0L, item, true);
		}

		public boolean free() {
			return cat == null && exact == null;
		}
	}

	/** A full masonry block (½ unit), a stair (⅜), a slab (¼). */
	public static final long FULL = 50L;
	public static final long STAIR = 38L;
	public static final long SLAB = 25L;
	/** Hidden fill (terrace cores, foundations): ¼. */
	public static final long FILL = 25L;

	private static final Map<Block, Cost> CACHE = new ConcurrentHashMap<>();

	/** The placed cost of {@code state} (the upper half of a door or tall plant is free: the lower half pays). */
	public static Cost of(BlockState state) {
		if (state == null || state.isAir()) {
			return Cost.FREE;
		}
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			return Cost.FREE;
		}
		return CACHE.computeIfAbsent(state.getBlock(), Costs::compute);
	}

	/** True when a rule prices {@code block} (the audit refuses unknown blocks, DESIGN 21.11). */
	public static boolean known(Block block) {
		return CACHE.computeIfAbsent(block, Costs::compute).known();
	}

	/** Adds the cost of {@code state} to a bill builder. */
	public static void add(Bill.Builder bill, BlockState state) {
		Cost cost = of(state);
		if (cost.cat() != null && cost.centi() > 0L) {
			bill.add(cost.cat(), cost.centi());
		} else if (cost.exact() != null) {
			bill.add(cost.exact(), 1);
		}
	}

	/** The bill of every non-air op of an emission (before digging credit). */
	public static Bill bill(Emission emission) {
		Bill.Builder bill = Bill.builder();
		for (BlockState state : emission.states) {
			add(bill, state);
		}
		return bill.build();
	}

	/** The bill of the ops of an emission from {@code from} (inclusive) to {@code to} (exclusive). */
	public static Bill bill(Emission emission, int from, int to) {
		Bill.Builder bill = Bill.builder();
		for (int i = Math.max(0, from); i < Math.min(to, emission.size()); i++) {
			add(bill, emission.states[i]);
		}
		return bill.build();
	}

	private static Cost compute(Block block) {
		if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR || block == Blocks.WATER) {
			return Cost.FREE;
		}
		// Exact items, deposited 1:1 (DESIGN 21.11 "Exact items").
		if (block == Blocks.CAULDRON || block == Blocks.WATER_CAULDRON) {
			return Cost.item(Items.CAULDRON);
		}
		Item exact = exactItem(block);
		if (exact != null) {
			return Cost.item(exact);
		}
		String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
		// The few blocks only the 6b formation patterns place (DESIGN 24.6.2 B): an iron block is its deposit value, nine units.
		if (block == Blocks.IRON_BLOCK) {
			return Cost.of(Bahan.LOGAM, 900L);
		}
		if (block == Blocks.MAGMA_BLOCK) {
			return Cost.of(Bahan.BATU, FULL);
		}
		if (path.startsWith("purpur")) {
			return shaped(Bahan.KAPUR, path);
		}
		if (path.contains("nether_brick")) {
			return shaped(Bahan.GENTENG, path);
		}
		// Gold and jade (Emas & Giok).
		if (block == Blocks.GOLD_BLOCK || block == Blocks.RAW_GOLD_BLOCK) {
			return Cost.of(Bahan.PERMATA, 900L);
		}
		if (block == Blocks.LAPIS_BLOCK) {
			return Cost.of(Bahan.PERMATA, 450L);
		}
		if (block == Blocks.PRISMARINE_BRICKS || block == Blocks.DARK_PRISMARINE) {
			return Cost.of(Bahan.PERMATA, 200L);
		}
		if (block == Blocks.PRISMARINE) {
			// Never more than its deposit value (1 unit), so a refund can never pay out more than the block is worth.
			return Cost.of(Bahan.PERMATA, 100L);
		}
		if (path.contains("sulfur")) {
			return Cost.of(Bahan.PERMATA, 50L);
		}
		// Sekte Langit (DESIGN 37.4.1): the hanging crystals' flanks and tips, the glow-berry vines under the islands.
		if (block == Blocks.TINTED_GLASS) {
			return Cost.of(Bahan.PERMATA, 50L);
		}
		if (block == Blocks.AMETHYST_CLUSTER || block == Blocks.LARGE_AMETHYST_BUD) {
			return Cost.of(Bahan.PERMATA, 25L);
		}
		if (block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT) {
			return Cost.of(Bahan.FLORA, 25L);
		}
		// Lights and fittings (Logam & Pelita).
		if (path.endsWith("lantern") || path.endsWith("froglight") || block == Blocks.SHROOMLIGHT || block == Blocks.GLOWSTONE
				|| block == Blocks.SEA_LANTERN) {
			return Cost.of(Bahan.LOGAM, 100L);
		}
		if (block == Blocks.IRON_CHAIN || block == Blocks.IRON_BARS || path.endsWith("candle") || block == Blocks.SEA_PICKLE) {
			return Cost.of(Bahan.LOGAM, 50L);
		}
		if (path.contains("copper")) {
			if (path.endsWith("_stairs")) {
				return Cost.of(Bahan.PERMATA, 150L);
			}
			if (path.endsWith("_slab") || path.endsWith("_bars") || path.endsWith("_chain")) {
				return Cost.of(Bahan.PERMATA, 100L);
			}
			return Cost.of(Bahan.PERMATA, 200L);
		}
		// Plants (Flora).
		if (path.endsWith("leaves")) {
			return Cost.of(Bahan.FLORA, 25L);
		}
		if (path.startsWith("potted_") || path.endsWith("_sapling") || block == Blocks.AZALEA || block == Blocks.FLOWERING_AZALEA
				|| block == Blocks.LILY_PAD || block == Blocks.BIG_DRIPLEAF || block == Blocks.FIREFLY_BUSH || isFlower(block)) {
			return Cost.of(Bahan.FLORA, 100L);
		}
		if (block == Blocks.SPORE_BLOSSOM) {
			return Cost.of(Bahan.FLORA, 200L);
		}
		if (block == Blocks.BIG_DRIPLEAF_STEM || block == Blocks.SEAGRASS || block == Blocks.PINK_PETALS || block == Blocks.LEAF_LITTER
				|| block == Blocks.HANGING_ROOTS || block == Blocks.MOSS_CARPET || block == Blocks.SHORT_GRASS || block == Blocks.FERN
				|| block == Blocks.TALL_GRASS || block == Blocks.LARGE_FERN) {
			return Cost.of(Bahan.FLORA, 25L);
		}
		if (block == Blocks.MOSS_BLOCK) {
			return Cost.of(Bahan.FLORA, 50L);
		}
		if (block == Blocks.BAMBOO || block == Blocks.LADDER) {
			return Cost.of(Bahan.KAYU, 25L);
		}
		// Lacquer and dye (Pernis): cinnabar, glazed terracotta, coloured concrete, wool goods.
		if (path.endsWith("_banner")) {
			return Cost.of(Bahan.PERNIS, 100L);
		}
		if (path.endsWith("_carpet")) {
			return Cost.of(Bahan.PERNIS, 25L);
		}
		if (path.endsWith("_wool") || path.endsWith("glazed_terracotta")) {
			return Cost.of(Bahan.PERNIS, FULL);
		}
		if (path.contains("concrete")) {
			return shaped(path.startsWith("white_") ? Bahan.KAPUR : Bahan.PERNIS, path);
		}
		if (path.contains("cinnabar")) {
			return shaped(Bahan.PERNIS, path);
		}
		// Timber (Kayu).
		if (isWood(path)) {
			if (path.endsWith("_door")) {
				// The lower half pays for the whole door (DESIGN 21.11: 1 unit per door).
				return Cost.of(Bahan.KAYU, 100L);
			}
			if (path.endsWith("_trapdoor")) {
				return Cost.of(Bahan.KAYU, STAIR);
			}
			if (path.endsWith("_fence")) {
				return Cost.of(Bahan.KAYU, SLAB);
			}
			if (path.endsWith("_sign") || path.endsWith("_fence_gate")) {
				return Cost.of(Bahan.KAYU, FULL);
			}
			return shaped(Bahan.KAYU, path);
		}
		// Tile (Genteng): deepslate, blackstone, bricks, terracotta, mud bricks, clay.
		if (path.contains("deepslate") || path.contains("blackstone") || path.equals("bricks") || path.startsWith("brick_")
				|| path.contains("mud_brick") || path.contains("terracotta") || block == Blocks.CLAY || block == Blocks.PACKED_MUD) {
			return shaped(Bahan.GENTENG, path);
		}
		// Lime and plaster (Kapur): quartz, calcite, diorite.
		if (path.contains("quartz") || block == Blocks.CALCITE || path.contains("diorite")) {
			return shaped(Bahan.KAPUR, path);
		}
		// Earth (Tanah): soil and hidden fill.
		if (block == Blocks.DIRT || block == Blocks.GRASS_BLOCK || block == Blocks.COARSE_DIRT || block == Blocks.PODZOL || block == Blocks.ROOTED_DIRT
				|| block == Blocks.MUD || block == Blocks.DIRT_PATH) {
			return Cost.of(Bahan.TANAH, FILL);
		}
		// Stone (Batu): plain stone is hidden fill, every worked stone a masonry block.
		if (block == Blocks.STONE) {
			return Cost.of(Bahan.BATU, FILL);
		}
		if (path.contains("stone") || path.contains("andesite") || path.contains("granite") || path.contains("tuff") || path.contains("cobble")
				|| path.contains("basalt") || block == Blocks.DRIPSTONE_BLOCK) {
			return shaped(Bahan.BATU, path);
		}
		return new Cost(Bahan.BATU, FULL, null, false);
	}

	/** Full ½, stair ⅜, slab ¼, wall ½ of {@code cat}. */
	private static Cost shaped(Bahan cat, String path) {
		if (path.endsWith("_stairs")) {
			return Cost.of(cat, STAIR);
		}
		if (path.endsWith("_slab")) {
			return Cost.of(cat, SLAB);
		}
		return Cost.of(cat, FULL);
	}

	private static boolean isWood(String path) {
		for (String wood : new String[]{"mangrove", "warped", "dark_oak", "spruce", "cherry", "poplar", "pale_oak", "oak", "birch", "jungle",
				"acacia", "crimson", "bamboo"}) {
			if (path.startsWith(wood + "_") || path.startsWith("stripped_" + wood)) {
				return true;
			}
		}
		return path.endsWith("_planks") || path.endsWith("_log") || path.endsWith("_wood");
	}

	private static boolean isFlower(Block block) {
		return block == Blocks.PEONY || block == Blocks.LILAC || block == Blocks.LILY_OF_THE_VALLEY || block == Blocks.TORCHFLOWER
				|| block == Blocks.PITCHER_PLANT || block == Blocks.ALLIUM || block == Blocks.OXEYE_DAISY || block == Blocks.CORNFLOWER
				|| block == Blocks.ROSE_BUSH || block == Blocks.SUNFLOWER || block == Blocks.BLUE_ORCHID;
	}

	private static @Nullable Item exactItem(Block block) {
		if (block == Blocks.SMOKER) {
			return Items.SMOKER;
		}
		if (block == Blocks.CAMPFIRE) {
			return Items.CAMPFIRE;
		}
		if (block == Blocks.SOUL_CAMPFIRE) {
			return Items.SOUL_CAMPFIRE;
		}
		if (block == Blocks.BOOKSHELF) {
			return Items.BOOKSHELF;
		}
		if (block == Blocks.LECTERN) {
			return Items.LECTERN;
		}
		if (block == Blocks.BREWING_STAND) {
			return Items.BREWING_STAND;
		}
		if (block == Blocks.LODESTONE) {
			return Items.LODESTONE;
		}
		if (block == Blocks.CRYING_OBSIDIAN) {
			return Items.CRYING_OBSIDIAN;
		}
		if (block == Blocks.AMETHYST_BLOCK) {
			return Items.AMETHYST_BLOCK;
		}
		if (block == Blocks.HAY_BLOCK) {
			return Items.HAY_BLOCK;
		}
		if (block == Blocks.TARGET) {
			return Items.TARGET;
		}
		if (block == Blocks.BELL) {
			return Items.BELL;
		}
		if (block == Blocks.BARREL) {
			return Items.BARREL;
		}
		if (block == Blocks.END_ROD) {
			return Items.END_ROD;
		}
		return null;
	}

	private Costs() {
	}
}
