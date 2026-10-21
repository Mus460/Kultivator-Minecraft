package dev.kultivator.sect.build.engine;

import dev.kultivator.Kultivator;
import dev.kultivator.formation.FormationData;
import dev.kultivator.kehancuran.ProtectedZones;
import dev.kultivator.sect.build.blueprint.Costs;
import dev.kultivator.sect.build.blueprint.TerrainView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SmokerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Classification of what stands in a cell the builder wants (DESIGN 21.10 "Classification" and the per-position
 * decision): natural blocks ({@code #kultivator:sect_natural}, leaves only when not persistent, logs only as part of a
 * natural tree cluster found at scan time), cleared plants, the builder's own cells (the {@link CellIndex}, re-checked
 * against the block that stands there now), pure fluids, player-made blocks, containers and protected blocks
 * ({@code #kultivator:sect_protected}, {@code #kultivator:indestructible}, hardness below 0, the stele, a registered
 * formation lodestone, the obsidian frame of a lit nether portal, a modded block entity that is not a container). A
 * natural block entity (a tree's bee nest, a natural creaking heart) is natural; any other vanilla block entity the
 * builder did not place (a sign, a bed, a banner, a lectern) is player-made. A datapack may extend both tags. Server
 * thread only. The only write is clearing a stale own bit (a block the builder could never have placed sits on it).
 */
public final class Classifier {

	public static final TagKey<Block> NATURAL = TagKey.create(Registries.BLOCK, Kultivator.id("sect_natural"));
	public static final TagKey<Block> PROTECTED = TagKey.create(Registries.BLOCK, Kultivator.id("sect_protected"));
	/** Blocks that are natural only in a rocky-surface biome (cobble mountains of Terralith and similar worldgen). */
	public static final TagKey<Block> NATURAL_ROCKY = TagKey.create(Registries.BLOCK, Kultivator.id("sect_natural_rocky"));
	/** Biomes whose generator lays cobblestone or cobbled deepslate as surface rock. */
	public static final TagKey<Biome> ROCKY_SURFACE_BIOMES = TagKey.create(Registries.BIOME, Kultivator.id("rocky_surface_biomes"));
	private static final Identifier STELE = Kultivator.id("sect_hall_stele");

	public enum Kind {
		/** Air: place. */
		AIR,
		/** Short or tall grass, fern, dead bush, snow layer: cleared, no drops. */
		PLANT,
		/** Natural ground, rock, flowers, a natural tree: displaced, its drops credited. */
		NATURAL,
		/** Pure water or lava (a liquid block or a bubble column): sealed first. */
		FLUID,
		/** A cell the builder placed and still owns. */
		OWN,
		/** An own cell of the previous template generation (DESIGN 21.18): any new op replaces it, with a refund. */
		LEGACY,
		/** Anything else a player built: blocked unless "Bongkar &amp; simpan" is authorized. */
		PLAYER_MADE,
		/** A container holding items: blocked unless "Kosongkan" is authorized. */
		CONTAINER,
		/** Never touched. */
		PROTECTED
	}

	/** {@link #classify(ServerLevel, BlockPos, BlockState, CellIndex, Site)} without a site (no scan-time tree verdict). */
	public static Kind classify(ServerLevel level, BlockPos pos, BlockState state, CellIndex index) {
		return classify(level, pos, state, index, null);
	}

	/**
	 * Classifies {@code state} at {@code pos}. {@code site} gives the frozen snapshot whose tree verdict decides logs, so
	 * clearing a canopy first never turns its trunk into "player-made" (DESIGN 21.10).
	 */
	public static Kind classify(ServerLevel level, BlockPos pos, BlockState state, CellIndex index, @Nullable Site site) {
		if (state.isAir()) {
			return Kind.AIR;
		}
		if (index.isPlaced(level, pos)) {
			if (ownStill(level, pos, state)) {
				return index.isLegacy(level, pos) ? Kind.LEGACY : Kind.OWN;
			}
			// A block the builder never places sits on a stale bit: it is not the sect's; classify it normally.
			UUID owner = index.owner(level, pos);
			index.setPlaced(level, pos, owner == null ? new UUID(0L, 0L) : owner, false);
		}
		if (protectedBlock(level, pos, state)) {
			return Kind.PROTECTED;
		}
		if (state.hasBlockEntity()) {
			BlockEntity entity = level.getBlockEntity(pos);
			if (entity instanceof RandomizableContainer loot && loot.getLootTable() != null) {
				// An unopened loot chest: never rolled by a read (isEmpty would unpack it).
				return Kind.CONTAINER;
			}
			if (entity instanceof Container container) {
				return container.isEmpty() ? Kind.PLAYER_MADE : Kind.CONTAINER;
			}
			if (naturalTagged(state)) {
				// A tree's bee nest or a pale garden's creaking heart: displaced like the ground around it.
				return Kind.NATURAL;
			}
			// A vanilla sign, bed, banner, lectern...: a player's block, taken up under "Bongkar & simpan". A modded block
			// entity may keep contents the engine cannot see, so it is never touched.
			Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
			return "minecraft".equals(id.getNamespace()) ? Kind.PLAYER_MADE : Kind.PROTECTED;
		}
		if (state.getBlock() instanceof LiquidBlock || state.is(Blocks.BUBBLE_COLUMN)) {
			return Kind.FLUID;
		}
		// A waterlogged block or a water plant is classified as the block itself (the fluid is sealed afterwards).
		if (clearedPlant(state)) {
			return Kind.PLANT;
		}
		if (state.is(BlockTags.LOGS)) {
			return naturalLog(level, pos, state, site) ? Kind.NATURAL : Kind.PLAYER_MADE;
		}
		if (naturalTagged(state) || rockySurface(level, pos, state)) {
			return Kind.NATURAL;
		}
		return Kind.PLAYER_MADE;
	}

	/**
	 * Cobblestone or cobbled deepslate in a rocky-surface biome (e.g. Terralith's Rocky Mountains, whose surface rule lays
	 * a 12-20 block cobble band): natural there, while a player's cobble house anywhere else stays player-made.
	 */
	public static boolean rockySurface(ServerLevel level, BlockPos pos, BlockState state) {
		return state.is(NATURAL_ROCKY) && level.getBiome(pos).is(ROCKY_SURFACE_BIOMES);
	}

	/**
	 * True while an own cell still holds a block the builder could have put there (DESIGN 21.10, 21.13): a priced block
	 * (never a player's diamond block, beacon or shulker box), not protected, and a block entity only of the builder's
	 * own kinds (signs, banners, lecterns, bells, campfires, brewing stands, barrels, smokers), a container only while
	 * empty (a player's items are never deleted with it).
	 */
	public static boolean ownStill(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || protectedBlock(level, pos, state) || !Costs.known(state.getBlock())) {
			return false;
		}
		if (!state.hasBlockEntity()) {
			return true;
		}
		Block block = state.getBlock();
		boolean builderKind = block instanceof SignBlock || block instanceof AbstractBannerBlock || block instanceof LecternBlock
				|| block instanceof BellBlock || block instanceof CampfireBlock || block instanceof BrewingStandBlock || block instanceof BarrelBlock
				|| block instanceof SmokerBlock;
		if (!builderKind) {
			return false;
		}
		BlockEntity entity = level.getBlockEntity(pos);
		return !(entity instanceof Container container) || container.isEmpty();
	}

	/** Never touched, whatever is authorized (DESIGN 21.10 "Protected, never touched"). */
	public static boolean protectedBlock(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.is(PROTECTED) || state.is(ProtectedZones.INDESTRUCTIBLE) || state.is(Blocks.BEDROCK)) {
			return true;
		}
		if (state.getDestroySpeed(level, pos) < 0.0F) {
			return true;
		}
		if (state.is(BlockTags.PORTALS) || state.is(Blocks.END_PORTAL_FRAME) || state.is(Blocks.END_GATEWAY)) {
			return true;
		}
		if (STELE.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
			return true;
		}
		if (portalFrame(level, pos, state)) {
			return true;
		}
		return state.is(Blocks.LODESTONE) && FormationData.get(level).get(pos) != null;
	}

	/**
	 * Obsidian (or crying obsidian) touching a lit nether portal: the frame of a working portal is never displaced, while
	 * the loose obsidian of a lava pool or a ruined portal is natural.
	 */
	public static boolean portalFrame(ServerLevel level, BlockPos pos, BlockState state) {
		if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.CRYING_OBSIDIAN)) {
			return false;
		}
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (Direction direction : Direction.values()) {
			cursor.setWithOffset(pos, direction);
			if (level.isLoaded(cursor) && level.getBlockState(cursor).is(Blocks.NETHER_PORTAL)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Parts of a natural growth that a ground scan looks through like a trunk (DESIGN 21.10): huge mushroom caps and
	 * stems, a bee nest, a natural creaking heart.
	 */
	public static boolean growthPart(BlockState state) {
		return state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.BEE_NEST)
				|| state.is(Blocks.CREAKING_HEART) && naturalTagged(state);
	}

	/** Plants that are simply cleared (no drops, DESIGN 21.10). */
	public static boolean clearedPlant(BlockState state) {
		return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)
				|| state.is(Blocks.DEAD_BUSH) || state.is(Blocks.SNOW) || state.is(Blocks.BUSH) || state.is(Blocks.SHORT_DRY_GRASS)
				|| state.is(Blocks.TALL_DRY_GRASS) || state.is(Blocks.LEAF_LITTER);
	}

	/** Plants a snapshot may skip over (natural ground cover). */
	public static boolean naturalPlant(BlockState state) {
		return clearedPlant(state) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(Blocks.BAMBOO) || state.is(Blocks.SUGAR_CANE)
				|| state.is(Blocks.VINE) || state.is(Blocks.GLOW_LICHEN) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.CACTUS)
				|| state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.MOSS_CARPET) || naturalTagged(state);
	}

	/**
	 * In the natural tag, or leaves that would decay (not persistent). A block with the {@code natural} state property
	 * (the creaking heart) counts only while worldgen set it.
	 */
	public static boolean naturalTagged(BlockState state) {
		if (state.is(BlockTags.LEAVES)) {
			return naturalLeaves(state);
		}
		if (!state.is(NATURAL)) {
			return false;
		}
		return !state.hasProperty(BlockStateProperties.NATURAL) || state.getValue(BlockStateProperties.NATURAL);
	}

	/** Leaves that decay (a tree's, never a player's persistent leaves). */
	public static boolean naturalLeaves(BlockState state) {
		return state.is(BlockTags.LEAVES) && state.hasProperty(BlockStateProperties.PERSISTENT) && !state.getValue(BlockStateProperties.PERSISTENT);
	}

	/** An unstripped log ({@code *_log}): the only log a natural tree grows (never wood, stripped logs or planks). */
	public static boolean treeLog(BlockState state) {
		if (!state.is(BlockTags.LOGS)) {
			return false;
		}
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return path.endsWith("_log") && !path.startsWith("stripped_");
	}

	/**
	 * A log is natural only as an unstripped log in a column the frozen snapshot marked as a natural tree (decaying
	 * leaves above a trunk, DESIGN 21.10 "logs as whole clusters"). Without a snapshot column (never for a confirmed
	 * footprint) a small live check looks for decaying leaves straight above.
	 */
	public static boolean naturalLog(ServerLevel level, BlockPos pos, BlockState state, @Nullable Site site) {
		if (!treeLog(state)) {
			return false;
		}
		if (site != null && site.snapshot != null && site.snapshot.knownAt(pos.getX(), pos.getZ())) {
			return (site.snapshot.flagsAt(pos.getX(), pos.getZ()) & TerrainView.TREE) != 0;
		}
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dy = 1; dy <= 12; dy++) {
			cursor.set(pos.getX(), pos.getY() + dy, pos.getZ());
			if (!level.isLoaded(cursor)) {
				return false;
			}
			BlockState above = level.getBlockState(cursor);
			if (naturalLeaves(above)) {
				return true;
			}
			if (!treeLog(above)) {
				return false;
			}
		}
		return false;
	}

	private Classifier() {
	}
}
