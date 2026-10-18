package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopperCollection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BambooLeaves;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.Tilt;

/**
 * The block palette of DESIGN 21.5 (every block verified in the 26.3 {@code Blocks}) and small helpers that build fully
 * specified states (DESIGN 21.10 "emit every property explicitly"). States are built in a part's canonical frame; the
 * {@link Sink} rotates them. All helpers are pure and thread-safe (emission runs off the server thread).
 * <p>
 * Copper is always the waxed form ({@link #jade}, {@link #bronze}): lightning only rewrites unwaxed copper. Dyed blocks
 * come from the 26.3 colour collections ({@link #concrete(DyeColor)} and friends). Art direction from real architecture,
 * not novel canon (DESIGN 21.5).
 */
public final class Palette {

	// ================================================================== roles (DESIGN 21.5 "Materials by role")

	/** Lacquer pillars of halls and gates. */
	public static final Block PILLAR = Blocks.POLISHED_CINNABAR;
	/** Round-reading pillars of pavilions, galleries and small gates. */
	public static final Block PILLAR_THIN = Blocks.POLISHED_CINNABAR_WALL;
	public static final Block PILLAR_BASE = Blocks.CHISELED_TUFF;
	/** Plinths, sills and plaza paving. */
	public static final Block PLINTH = Blocks.POLISHED_ANDESITE;
	public static final Block WOOD = Blocks.MANGROVE_PLANKS;
	public static final Block WOOD_STAIRS = Blocks.MANGROVE_STAIRS;
	public static final Block WOOD_SLAB = Blocks.MANGROVE_SLAB;
	/** Sills and lintels. */
	public static final Block LINTEL = Blocks.STRIPPED_MANGROVE_WOOD;
	public static final Block DOOR = Blocks.MANGROVE_DOOR;
	public static final Block FENCE = Blocks.MANGROVE_FENCE;
	/** The painted beam band (teal caihua). */
	public static final Block BEAM = Blocks.WARPED_PLANKS;
	/** Brackets (dougong): HALF=TOP, tall side against the wall. Warped wood does not burn. */
	public static final Block BRACKET = Blocks.WARPED_STAIRS;
	public static final Block BRACKET_TRAP = Blocks.DARK_OAK_TRAPDOOR;
	/** Aged gilt caps at pillar heads (T6+). */
	public static final Block GILT_CAP = Blocks.CHISELED_SULFUR;
	/** The dark rafter soffit under rank C/D eaves. */
	public static final Block SOFFIT = Blocks.POLISHED_DEEPSLATE_SLAB;
	/** All plaster. */
	public static final Block PLASTER = Blocks.SMOOTH_QUARTZ;
	public static final Block PALACE_WALL = Blocks.POLISHED_CINNABAR;
	public static final Block PALACE_WALL_TEXTURE = Blocks.CINNABAR_BRICKS;
	public static final Block FLOOR = Blocks.POLISHED_DEEPSLATE;
	public static final Block STONE = Blocks.STONE_BRICKS;
	public static final Block STONE_MOSSY = Blocks.MOSSY_STONE_BRICKS;
	public static final Block STONE_FRET = Blocks.CHISELED_TUFF_BRICKS;
	public static final Block STONE_COPING = Blocks.SMOOTH_STONE_SLAB;
	public static final Block STONE_STAIRS = Blocks.STONE_BRICK_STAIRS;
	public static final Block STONE_PILASTER = Blocks.CHISELED_STONE_BRICKS;
	public static final Block MARBLE_DECK = Blocks.SMOOTH_QUARTZ;
	public static final Block MARBLE_FACE = Blocks.POLISHED_DIORITE;
	public static final Block MARBLE_RAIL = Blocks.PALE_OAK_FENCE;
	public static final Block MARBLE_POST = Blocks.QUARTZ_PILLAR;
	public static final Block MARBLE_CAP = Blocks.SMOOTH_QUARTZ_SLAB;
	public static final Block MARBLE_FRET = Blocks.CHISELED_QUARTZ_BLOCK;
	public static final Block MARBLE_STAIRS = Blocks.SMOOTH_QUARTZ_STAIRS;
	public static final Block AXIS_PAVE = Blocks.SMOOTH_STONE;
	public static final Block AXIS_ACCENT = Blocks.CHISELED_STONE_BRICKS;
	public static final Block STEPPING = Blocks.TUFF_BRICK_SLAB;
	public static final Block MOSS = Blocks.MOSS_BLOCK;
	public static final Block GOLD = Blocks.GOLD_BLOCK;
	public static final Block RAW_GOLD = Blocks.RAW_GOLD_BLOCK;
	public static final Block PLAQUE_FIELD = Blocks.GILDED_BLACKSTONE;
	public static final Block LIGHT = Blocks.SHROOMLIGHT;
	public static final Block HIDDEN_LIGHT = Blocks.OCHRE_FROGLIGHT;
	public static final Block RIDGE_LIGHT = Blocks.PEARLESCENT_FROGLIGHT;
	public static final Block TOWER_BRICK = Blocks.POLISHED_BLACKSTONE_BRICKS;
	public static final Block FILL_EARTH = Blocks.DIRT;
	public static final Block FILL_STONE = Blocks.STONE;

	// ================================================================== copper (always waxed)

	/** Waxed oxidized copper of a collection: the jade of the palette. */
	public static Block jade(WeatheringCopperCollection<Block> collection) {
		return collection.waxed().oxidized();
	}

	/** Waxed exposed copper of a collection: bronze (dings, vat stands). */
	public static Block bronze(WeatheringCopperCollection<Block> collection) {
		return collection.waxed().exposed();
	}

	public static Block jadeStairs() {
		return jade(Blocks.CUT_COPPER_STAIRS);
	}

	public static Block jadeSlab() {
		return jade(Blocks.CUT_COPPER_SLAB);
	}

	public static Block jadeBlock() {
		return jade(Blocks.CUT_COPPER);
	}

	// ================================================================== colours

	public static Block concrete(DyeColor color) {
		return Blocks.CONCRETE.pick(color);
	}

	public static Block concreteStairs(DyeColor color) {
		return Blocks.CONCRETE_STAIRS.pick(color);
	}

	public static Block concreteSlab(DyeColor color) {
		return Blocks.CONCRETE_SLAB.pick(color);
	}

	public static Block glazed(DyeColor color) {
		return Blocks.GLAZED_TERRACOTTA.pick(color);
	}

	public static Block carpet(DyeColor color) {
		return Blocks.CARPET.pick(color);
	}

	public static Block candle(DyeColor color) {
		return Blocks.DYED_CANDLE.pick(color);
	}

	public static Block banner(DyeColor color) {
		return Blocks.BANNER.pick(color);
	}

	public static Block wallBanner(DyeColor color) {
		return Blocks.WALL_BANNER.pick(color);
	}

	// ================================================================== state helpers

	public static BlockState st(Block block) {
		return block.defaultBlockState();
	}

	public static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}

	/** Sets a property when the state has it (guards every helper against block-class differences). */
	public static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, T value) {
		return state.hasProperty(property) ? state.setValue(property, value) : state;
	}

	/** A dry state: WATERLOGGED false when present. */
	public static BlockState dry(BlockState state) {
		return with(state, BlockStateProperties.WATERLOGGED, false);
	}

	/** A waterlogged state (for blocks placed in planned water). */
	public static BlockState wet(BlockState state) {
		return with(state, BlockStateProperties.WATERLOGGED, true);
	}

	/** A straight stair (the {@link Sink} sets the vanilla shape at the end): tall side toward {@code facing}. */
	public static BlockState stair(Block block, Direction facing, boolean top) {
		BlockState state = with(block.defaultBlockState(), BlockStateProperties.HORIZONTAL_FACING, facing);
		state = with(state, BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
		return dry(with(state, BlockStateProperties.STAIRS_SHAPE, StairsShape.STRAIGHT));
	}

	public static BlockState slab(Block block, boolean top) {
		return dry(with(block.defaultBlockState(), BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM));
	}

	public static BlockState doubleSlab(Block block) {
		return dry(with(block.defaultBlockState(), BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE));
	}

	/** A log, chain or pillar along {@code axis}. */
	public static BlockState axis(Block block, Direction.Axis axis) {
		return dry(with(block.defaultBlockState(), BlockStateProperties.AXIS, axis));
	}

	/** A vertical chain (AXIS=Y). */
	public static BlockState chain(Block block) {
		return axis(block, Direction.Axis.Y);
	}

	public static BlockState ironChain() {
		return chain(Blocks.IRON_CHAIN);
	}

	public static BlockState jadeChain() {
		return chain(jade(Blocks.COPPER_CHAIN));
	}

	/** Leaves that never decay (PERSISTENT=true, DESIGN 21.9). */
	public static BlockState leaves(Block block) {
		return dry(with(block.defaultBlockState(), BlockStateProperties.PERSISTENT, true));
	}

	/** An open trapdoor whose plate lies on the {@code plateSide} face of its cell (lattice, DESIGN 21.7). */
	public static BlockState lattice(Block block, Direction plateSide) {
		BlockState state = with(block.defaultBlockState(), BlockStateProperties.HORIZONTAL_FACING, plateSide.getOpposite());
		state = with(state, BlockStateProperties.OPEN, true);
		state = with(state, BlockStateProperties.HALF, Half.BOTTOM);
		return dry(with(state, BlockStateProperties.POWERED, false));
	}

	/** A closed trapdoor lying flat at the top or the bottom of its cell. */
	public static BlockState trapdoorFlat(Block block, Direction facing, boolean top) {
		BlockState state = with(block.defaultBlockState(), BlockStateProperties.HORIZONTAL_FACING, facing);
		state = with(state, BlockStateProperties.OPEN, false);
		state = with(state, BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
		return dry(with(state, BlockStateProperties.POWERED, false));
	}

	/** One half of a door; both halves share facing, hinge and open (DESIGN 21.10). */
	public static BlockState door(Block block, Direction facing, boolean upper, boolean open, boolean rightHinge) {
		BlockState state = with(block.defaultBlockState(), BlockStateProperties.HORIZONTAL_FACING, facing);
		state = with(state, BlockStateProperties.DOUBLE_BLOCK_HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
		state = with(state, BlockStateProperties.OPEN, open);
		state = with(state, BlockStateProperties.DOOR_HINGE, rightHinge ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
		return with(state, BlockStateProperties.POWERED, false);
	}

	/** One half of a tall plant (peony, lilac, tall grass...). */
	public static BlockState tallPlant(Block block, boolean upper) {
		return with(block.defaultBlockState(), BlockStateProperties.DOUBLE_BLOCK_HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
	}

	/** A lantern; HANGING must match its support, which is always placed first (DESIGN 21.10). */
	public static BlockState lantern(Block block, boolean hanging) {
		return dry(with(block.defaultBlockState(), BlockStateProperties.HANGING, hanging));
	}

	/** A lit campfire; only the one signal ding per sect has {@code signal} true (DESIGN 21.7). */
	public static BlockState campfire(Block block, boolean signal) {
		BlockState state = with(block.defaultBlockState(), BlockStateProperties.LIT, true);
		state = with(state, BlockStateProperties.SIGNAL_FIRE, signal);
		return dry(with(state, BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
	}

	/** An unlit candle group (roof beasts) or a lit one (altars). */
	public static BlockState candles(Block block, int count, boolean lit) {
		BlockState state = with(block.defaultBlockState(), BlockStateProperties.CANDLES, Math.max(1, Math.min(4, count)));
		return dry(with(state, BlockStateProperties.LIT, lit));
	}

	/** Bamboo with STAGE=1, AGE and LEAVES set (DESIGN 21.10). */
	public static BlockState bamboo(boolean bigLeaves, boolean smallLeaves) {
		BlockState state = with(Blocks.BAMBOO.defaultBlockState(), BlockStateProperties.STAGE, 1);
		state = with(state, BlockStateProperties.AGE_1, 1);
		return with(state, BlockStateProperties.BAMBOO_LEAVES, bigLeaves ? BambooLeaves.LARGE : smallLeaves ? BambooLeaves.SMALL : BambooLeaves.NONE);
	}

	/** A big dripleaf head (lotus leaf): TILT=NONE, facing, waterlogged as asked. */
	public static BlockState dripleaf(Direction facing, boolean waterlogged) {
		BlockState state = with(Blocks.BIG_DRIPLEAF.defaultBlockState(), BlockStateProperties.TILT, Tilt.NONE);
		state = with(state, BlockStateProperties.HORIZONTAL_FACING, facing);
		return with(state, BlockStateProperties.WATERLOGGED, waterlogged);
	}

	public static BlockState dripleafStem(Direction facing, boolean waterlogged) {
		BlockState state = with(Blocks.BIG_DRIPLEAF_STEM.defaultBlockState(), BlockStateProperties.HORIZONTAL_FACING, facing);
		return with(state, BlockStateProperties.WATERLOGGED, waterlogged);
	}

	/** Pink petals or leaf litter: amount 1-4 and facing. */
	public static BlockState petals(Block block, int amount, Direction facing) {
		BlockState state = with(block.defaultBlockState(), BlockStateProperties.FLOWER_AMOUNT, Math.max(1, Math.min(4, amount)));
		state = with(state, BlockStateProperties.SEGMENT_AMOUNT, Math.max(1, Math.min(4, amount)));
		return with(state, BlockStateProperties.HORIZONTAL_FACING, facing);
	}

	/** Sea pickles (always waterlogged on pond floors). */
	public static BlockState seaPickles(int count) {
		BlockState state = with(Blocks.SEA_PICKLE.defaultBlockState(), BlockStateProperties.PICKLES, Math.max(1, Math.min(4, count)));
		return with(state, BlockStateProperties.WATERLOGGED, true);
	}

	/** A full water cauldron (vats; water is free). */
	public static BlockState waterVat() {
		return with(Blocks.WATER_CAULDRON.defaultBlockState(), BlockStateProperties.LEVEL_CAULDRON, 3);
	}

	/** A bell hanging from a ceiling or standing on the floor ({@code ceiling}), facing as given. */
	public static BlockState bell(Direction facing, boolean ceiling) {
		BlockState state = with(Blocks.BELL.defaultBlockState(), BlockStateProperties.HORIZONTAL_FACING, facing);
		state = with(state, BlockStateProperties.BELL_ATTACHMENT, ceiling ? BellAttachType.CEILING : BellAttachType.FLOOR);
		return with(state, BlockStateProperties.POWERED, false);
	}

	/** A horizontal-facing block (wall sign, lectern, smoker, barrel lying, wall banner...). */
	public static BlockState facing(Block block, Direction facing) {
		BlockState state = with(block.defaultBlockState(), BlockStateProperties.HORIZONTAL_FACING, facing);
		state = with(state, BlockStateProperties.FACING, facing);
		return dry(state);
	}

	/** A standing banner rotated to face {@code facing} (ROTATION_16). */
	public static BlockState banner(DyeColor color, Direction facing) {
		int rotation = switch (facing) {
			case SOUTH -> 0;
			case WEST -> 4;
			case NORTH -> 8;
			default -> 12;
		};
		return with(banner(color).defaultBlockState(), BlockStateProperties.ROTATION_16, rotation);
	}

	/** A barrel standing upright (a drum or a decorative store; always empty). */
	public static BlockState barrel(Direction facing) {
		BlockState state = with(Blocks.BARREL.defaultBlockState(), BlockStateProperties.FACING, facing);
		return with(state, BlockStateProperties.OPEN, false);
	}

	private Palette() {
	}
}
