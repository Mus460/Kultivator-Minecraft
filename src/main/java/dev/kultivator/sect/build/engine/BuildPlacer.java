package dev.kultivator.sect.build.engine;

import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.Bill;
import dev.kultivator.sect.build.api.MaterialLedger;
import dev.kultivator.sect.build.blueprint.Costs;
import dev.kultivator.sect.build.blueprint.Emission;
import dev.kultivator.sect.build.blueprint.Phase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The ONLY code of Pembangunan Sekte that places or removes blocks (DESIGN 21.0, 21.10; {@code kehancuran/WorldBreaker}
 * stays the other caller in the mod). It acts only inside the confirmed footprint column, only in loaded chunks (it never
 * loads one), and the engine calls it only while the hall is active and the stele stands on its anchor cell.
 * <p>
 * Every placement re-checks the cell (DESIGN 21.10 "Per-position decision"): already the target → skip; air and cleared
 * plants → place, no drops; natural blocks → displaced, their bulk drops credited to the ledger and everything else
 * (ores, gems, raw metals) put in the Peti Galian, never lost; fluids → sealed first ({@link FluidGuard}); player-made
 * blocks and containers → blocked unless explicitly authorized (then a silk-touch drop / the contents go to the Peti
 * Galian); protected blocks → blocked, never authorizable; a living entity in the way → deferred ({@link EntityGuard}); a
 * cell a player emptied of its sect block → never refilled. The builder's own cells follow the light-swap rule: earthwork
 * never removes them, plants and ornaments never overwrite them, structural phases replace them (refunding the old
 * block's placed cost); an upgrade's legacy cells give way to any new op.
 * <p>
 * Flags: placement {@code UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SKIP_ON_PLACE} (no neighbour cascades, no
 * {@code onPlace}: gravity blocks, observers and redstone never wake up), removal {@code UPDATE_CLIENTS |
 * UPDATE_KNOWN_SHAPE}; an emptied container is replaced with {@code UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS} too (no item or
 * XP drops). TIDY (fences reconnect) and waterfalls are the only exceptions. No item entities, no fire, no explosions, no
 * lightning, no entities. Test sites and showcases record every original state in their {@link Undo}.
 */
public final class BuildPlacer {

	public static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;
	public static final int REMOVE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	/** TIDY: shape updates for the connecting whitelist only. */
	public static final int TIDY_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	public enum Result {
		/** The cell now holds the target. */
		PLACED,
		/** Nothing was placed and the op's reserved share is given back (the target was already there, or the cell was kept). */
		SKIPPED,
		/** The cell is already the builder's own target block: nothing placed, nothing given back. */
		SKIPPED_OWN,
		DEFERRED,
		BLOCKED,
		UNLOADED,
		OUTSIDE
	}

	/**
	 * Brings {@code pos} to {@code target} (world orientation) for {@code site} in phase {@code phase}. Test builds and
	 * showcases credit and refund nothing. Extra block changes (seals, the other half of a door) are added to
	 * {@code site.extraOps} so the engine counts them against its burst budget.
	 */
	static Result place(ServerLevel level, Site site, BlockPos pos, BlockState target, Phase phase, EntityGuard guard, CellIndex index) {
		SitePlan plan = site.plan();
		if (!plan.inFootprint(pos) || pos.equals(site.stele) || pos.equals(site.stele.below())) {
			return Result.OUTSIDE;
		}
		if (dev.kultivator.alam.Alam.isRealmLevel(level) || dev.kultivator.abadi.Abadi.isImmortalLevel(level)) {
			// Phase 8 (DESIGN 25.1.1, 25.2 item 12) and phase 9 (26.14 item 13): the realm dimensions are never built in.
			return Result.OUTSIDE;
		}
		if (!level.isLoaded(pos)) {
			return Result.UNLOADED;
		}
		BlockState current = level.getBlockState(pos);
		boolean free = !site.paid();
		if (current == target) {
			if (index.isPlaced(level, pos) && Classifier.ownStill(level, pos, current)) {
				if (index.isLegacy(level, pos)) {
					// The old sect's block is exactly what the new plan wants: it now belongs to the new plan.
					index.setPlaced(level, pos, site.owner, ownable(current));
				}
				return Result.SKIPPED_OWN;
			}
			return Result.SKIPPED;
		}
		if (!target.isAir() && index.isRemoved(level, pos) && !index.isPlaced(level, pos) && (current.isAir() || Classifier.clearedPlant(current))) {
			// A player took this sect block out: it is never refilled on its own (DESIGN 21.18).
			return Result.SKIPPED;
		}
		Classifier.Kind kind = Classifier.classify(level, pos, current, index, site);
		switch (kind) {
			case PROTECTED:
				return Result.BLOCKED;
			case OWN:
				if (target.isAir() && (phase == Phase.CLEAR || phase == Phase.CUT)) {
					return Result.SKIPPED_OWN;
				}
				if (!target.isAir() && (phase == Phase.GARDEN || phase == Phase.ORNAMENT || phase == Phase.WATER)
						&& !(phase == Phase.WATER && waterfallSource(site, pos))) {
					return Result.SKIPPED;
				}
				break;
			case PLAYER_MADE:
				if (!site.mayRemovePlayerBlocks(level.getServer())) {
					return Result.BLOCKED;
				}
				break;
			case CONTAINER:
				// A test build or showcase never empties a container (its undo log could not give the items back).
				if (free || !site.mayEmptyContainers(level.getServer())) {
					return Result.BLOCKED;
				}
				break;
			default:
				break;
		}
		BlockState actual = target;
		List<BlockPos> seal = new ArrayList<>();
		List<BlockPos> natural = new ArrayList<>();
		if (target.isAir()) {
			// Fluid-first: seal every fluid neighbour, or keep this cell solid (retaining wall).
			int verdict = FluidGuard.sealable(level, site, index, pos, seal, natural, guard);
			if (verdict == FluidGuard.WAIT) {
				return Result.DEFERRED;
			}
			if (verdict == FluidGuard.RETAIN || outsideDependent(level, plan, pos)) {
				actual = FluidGuard.retaining();
				seal.clear();
				natural.clear();
				if (current == actual) {
					return Result.SKIPPED;
				}
			}
		}
		// The entity check sees the block that really goes in.
		if (guard.inTheWay(level, pos, actual)) {
			return Result.DEFERRED;
		}
		Undo undo = free ? Undo.get(level.getServer(), site.owner) : null;
		for (BlockPos neighbour : seal) {
			BlockState was = level.getBlockState(neighbour);
			if (undo != null) {
				undo.record(neighbour, was);
			} else if (natural.contains(neighbour)) {
				displace(level, site, neighbour, was, false);
			}
			level.setBlock(neighbour, FluidGuard.fill(), PLACE_FLAGS);
			index.setPlaced(level, neighbour, site.owner, true);
			site.extraOps++;
		}
		// What the old block leaves behind.
		int flags = actual.isAir() ? REMOVE_FLAGS : PLACE_FLAGS;
		if (undo == null) {
			switch (kind) {
				case NATURAL -> displace(level, site, pos, current, false);
				case PLAYER_MADE -> {
					displace(level, site, pos, current, true);
					if (current.hasBlockEntity()) {
						flags |= Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
					}
				}
				case CONTAINER -> {
					BlockEntity entity = level.getBlockEntity(pos);
					if (entity instanceof Container container) {
						for (int slot = 0; slot < container.getContainerSize(); slot++) {
							ItemStack stack = container.getItem(slot);
							if (!stack.isEmpty()) {
								MaterialLedger.get().salvage(site.ledgerOwner(), stack.copy());
							}
						}
						container.clearContent();
					}
					MaterialLedger.get().salvage(site.ledgerOwner(), new ItemStack(current.getBlock().asItem()));
					// Emptied: no block-entity side effects (no XP orbs, nothing dropped).
					flags |= Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
				}
				case OWN, LEGACY -> refundPlaced(site, current);
				default -> {
				}
			}
		} else {
			undo.record(pos, current);
			if (current.hasBlockEntity()) {
				flags |= Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
			}
		}
		// The other half of a door or tall plant goes with it.
		if (current.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && current.getBlock() != actual.getBlock()) {
			BlockPos other = current.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
			if (level.isLoaded(other) && level.getBlockState(other).getBlock() == current.getBlock() && plan.inFootprint(other)) {
				BlockState half = level.getBlockState(other);
				if (undo != null) {
					undo.record(other, half);
				}
				level.setBlock(other, half.getFluidState().is(Fluids.WATER) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
				index.setPlaced(level, other, site.owner, false);
				site.extraOps++;
			}
		}
		level.setBlock(pos, actual, flags);
		index.setPlaced(level, pos, site.owner, ownable(actual));
		if (actual.getBlock() == Blocks.DARK_OAK_WALL_SIGN || actual.getBlock() == Blocks.MANGROVE_WALL_SIGN) {
			writePlaque(level, pos, BuildEngine.INSTANCE.plaqueName(site));
		}
		site.statPlaced++;
		return Result.PLACED;
	}

	/**
	 * True when a placed block counts as the builder's own cell (refunds, spawn-proofing, the light-swap rule): never air,
	 * a pure fluid, or a replaceable plant such as short grass or seagrass, so a player's block placed straight into such a
	 * cell is never taken for the sect's (DESIGN 21.13).
	 */
	static boolean ownable(BlockState state) {
		return !state.isAir() && !(state.getBlock() instanceof LiquidBlock) && !(state.getBlock() instanceof BubbleColumnBlock) && !state.canBeReplaced();
	}

	/**
	 * True when a block just outside the footprint hangs on a face of {@code pos} (a wall torch, sign, ladder, banner,
	 * button, vine...): the cell keeps a solid face (retaining wall) so nothing outside the footprint loses its support.
	 */
	private static boolean outsideDependent(ServerLevel level, SitePlan plan, BlockPos pos) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos next = pos.relative(direction);
			if (plan.inFootprint(next) || !level.isLoaded(next)) {
				continue;
			}
			BlockState state = level.getBlockState(next);
			if (state.isAir()) {
				continue;
			}
			Block block = state.getBlock();
			boolean faced = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING);
			if (faced && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == direction
					&& (block instanceof WallTorchBlock || block instanceof WallSignBlock || block instanceof WallHangingSignBlock || block instanceof LadderBlock
							|| block instanceof WallBannerBlock || block instanceof TripWireHookBlock || block instanceof WallSkullBlock
							|| block instanceof FaceAttachedHorizontalDirectionalBlock && state.hasProperty(BlockStateProperties.ATTACH_FACE)
									&& state.getValue(BlockStateProperties.ATTACH_FACE) == AttachFace.WALL)) {
				return true;
			}
			if (faced && block instanceof CocoaBlock && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == direction.getOpposite()) {
				return true;
			}
			if (block instanceof VineBlock || block instanceof MultifaceBlock) {
				BooleanProperty face = PipeBlock.PROPERTY_BY_DIRECTION.get(direction.getOpposite());
				if (face != null && state.hasProperty(face) && state.getValue(face)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Drops of a displaced block (DESIGN 21.10 "Credit routing"): bulk drops (stone, earth, timber, plants, tiles, lime)
	 * are credited to the ledger (the predicted digging credit absorbed first); ores, gems, raw metals and everything else
	 * go to the Peti Galian as items, never auto-converted. The upper half of a tall plant drops as its lower half.
	 */
	private static void displace(ServerLevel level, Site site, BlockPos pos, BlockState state, boolean silk) {
		if (Classifier.clearedPlant(state)) {
			return;
		}
		BlockState dropState = state;
		BlockPos dropPos = pos;
		if (!silk && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			BlockPos below = pos.below();
			BlockState lower = level.isLoaded(below) ? level.getBlockState(below) : null;
			dropState = lower != null && lower.getBlock() == state.getBlock() ? lower : state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
			dropPos = below;
		}
		ItemStack tool = toolFor(level, dropState, silk);
		BlockEntity entity = dropState.hasBlockEntity() ? level.getBlockEntity(dropPos) : null;
		List<ItemStack> drops = silk ? List.of(new ItemStack(state.getBlock().asItem())) : Block.getDrops(dropState, level, dropPos, entity, null, tool);
		MaterialLedger ledger = MaterialLedger.get();
		for (ItemStack drop : drops) {
			if (drop.isEmpty()) {
				continue;
			}
			Bahan cat = silk ? null : ledger.categoryOf(drop.getItem());
			boolean bulk = cat != null && cat != Bahan.LOGAM && cat != Bahan.PERMATA;
			long value = bulk ? ledger.depositValueCenti(drop.getItem()) * drop.getCount() : 0L;
			if (bulk && value > 0L) {
				long absorbed = Math.min(site.absorb[cat.ordinal()], value);
				site.absorb[cat.ordinal()] -= absorbed;
				if (value - absorbed > 0L) {
					ledger.credit(site.ledgerOwner(), cat, value - absorbed, "dig");
				}
				site.statCredited += value;
			} else {
				ledger.salvage(site.ledgerOwner(), drop.copy());
			}
		}
	}

	/** The unenchanted netherite tool of the block's mineable tag (or a silk-touch one), DESIGN 21.10. */
	private static ItemStack toolFor(ServerLevel level, BlockState state, boolean silk) {
		ItemStack tool;
		if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			tool = new ItemStack(Items.NETHERITE_PICKAXE);
		} else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
			tool = new ItemStack(Items.NETHERITE_SHOVEL);
		} else if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
			tool = new ItemStack(Items.NETHERITE_AXE);
		} else if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
			tool = new ItemStack(Items.NETHERITE_HOE);
		} else {
			tool = ItemStack.EMPTY;
		}
		if (silk && !tool.isEmpty()) {
			level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).get(Enchantments.SILK_TOUCH)
					.ifPresent(holder -> tool.enchant(holder, 1));
		}
		return tool;
	}

	/** Refunds the placed cost of one of the builder's own blocks replaced by a light swap. */
	static void refundPlaced(Site site, BlockState state) {
		refundPlaced(site, state, "refund");
	}

	/**
	 * Refunds the placed cost of one of the builder's own blocks; {@code reason} is the ledger credit reason
	 * ({@code refund} light swap, {@code break}, {@code explosion}, {@code dismantle}).
	 */
	static void refundPlaced(Site site, BlockState state, String reason) {
		if (!site.paid()) {
			return;
		}
		Costs.Cost cost = Costs.of(state);
		if (cost.cat() != null && cost.centi() > 0L) {
			MaterialLedger.get().credit(site.ledgerOwner(), cost.cat(), cost.centi(), reason);
		} else if (cost.exact() != null) {
			MaterialLedger.get().salvage(site.ledgerOwner(), new ItemStack(cost.exact()));
		}
	}

	/**
	 * A player broke a sect cell (DESIGN 21.13 "Break refund"): the vanilla break is cancelled by the caller; this removes
	 * the block and the sect blocks that depend on it (on any side), refunds their placed cost and marks "removed by
	 * player". Returns the refund (for the actionbar).
	 */
	static Bill refundRemove(ServerLevel level, Site site, BlockPos pos, CellIndex index) {
		Bill.Builder refund = Bill.builder();
		List<BlockPos> chain = new ArrayList<>();
		chain.add(pos);
		BlockState state = level.getBlockState(pos);
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			chain.add(state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below());
		}
		for (BlockPos each : chain) {
			removeOwn(level, site, each, index, refund, true);
		}
		removeDependants(level, site, pos, index, refund, true);
		return refund.build();
	}

	/** Own blocks on any side of {@code pos} that cannot stand without it (lanterns, signs, banners, ladders, bells, candles). */
	private static void removeDependants(ServerLevel level, Site site, BlockPos pos, CellIndex index, Bill.@Nullable Builder refund, boolean byPlayer) {
		for (Direction direction : Direction.values()) {
			BlockPos next = pos.relative(direction);
			if (level.isLoaded(next) && index.isPlaced(level, next)) {
				BlockState dependant = level.getBlockState(next);
				if (!dependant.isAir() && !dependant.canSurvive(level, next) && Classifier.ownStill(level, next, dependant)) {
					removeOwn(level, site, next, index, refund == null ? Bill.builder() : refund, byPlayer);
				}
			}
		}
	}

	private static void removeOwn(ServerLevel level, Site site, BlockPos pos, CellIndex index, Bill.Builder refund, boolean byPlayer) {
		if (!level.isLoaded(pos) || !index.isPlaced(level, pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || !Classifier.ownStill(level, pos, state)) {
			return;
		}
		if (site.paid()) {
			Costs.add(refund, state);
			refundPlaced(site, state, byPlayer ? "break" : "refund");
		}
		level.setBlock(pos, state.getFluidState().is(Fluids.WATER) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
		index.setPlaced(level, pos, site.owner, false);
		if (byPlayer) {
			index.setRemoved(level, pos, site.owner, true);
		}
		site.statRemoved++;
	}

	/**
	 * An explosion took a sect cell (DESIGN 21.13): removed without drops, its placed cost credited; repair may rebuild it.
	 * The caller has checked that the cell still holds the builder's own block and that its site exists.
	 */
	static void explosionRemove(ServerLevel level, Site site, BlockPos pos, CellIndex index) {
		BlockState state = level.getBlockState(pos);
		refundPlaced(site, state, "explosion");
		level.setBlock(pos, state.getFluidState().is(Fluids.WATER) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
		index.setPlaced(level, pos, site.owner, false);
		removeDependants(level, site, pos, index, null, false);
	}

	/**
	 * Dismantling (deconstruction, a lower test tier, an upgrade's legacy cells): removes an own cell still equal to its
	 * plan state ({@code expected}, null = any own block), refunded 100%. Never while an entity stands on or in it
	 * ({@link Result#DEFERRED}), and a cell next to a fluid that may not be sealed keeps its block (fluids stay contained).
	 */
	static Result dismantle(ServerLevel level, Site site, BlockPos pos, @Nullable BlockState expected, @Nullable BlockState replacement, CellIndex index,
			EntityGuard guard) {
		if (!level.isLoaded(pos)) {
			return Result.UNLOADED;
		}
		if (!index.isPlaced(level, pos)) {
			return Result.SKIPPED_OWN;
		}
		BlockState state = level.getBlockState(pos);
		if (expected != null && state != expected || !Classifier.ownStill(level, pos, state)) {
			return Result.SKIPPED_OWN;
		}
		BlockState after = replacement == null ? Blocks.AIR.defaultBlockState() : replacement;
		if (after.isAir() && hasFluidNeighbour(level, pos)) {
			// The sect's own pond or outside water beside it: the block stays as the basin wall.
			return Result.BLOCKED;
		}
		if (guard.inTheWay(level, pos, after)) {
			return Result.DEFERRED;
		}
		refundPlaced(site, state, "dismantle");
		level.setBlock(pos, after, after.isAir() ? REMOVE_FLAGS : PLACE_FLAGS);
		index.setPlaced(level, pos, site.owner, false);
		return Result.PLACED;
	}

	private static boolean hasFluidNeighbour(ServerLevel level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			BlockPos next = pos.relative(direction);
			if (!level.isLoaded(next) || !level.getBlockState(next).getFluidState().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Puts an undo-logged original back (test sites and showcases). {@link Result#DEFERRED} while the chunk is unloaded or
	 * someone stands there (air originals included: nobody loses the floor under them); {@link Result#SKIPPED} when a
	 * player changed the cell since (their block is never overwritten; the record is dropped).
	 */
	static Result restore(ServerLevel level, BlockPos pos, BlockState original, CellIndex index, EntityGuard guard) {
		if (!level.isLoaded(pos)) {
			return Result.DEFERRED;
		}
		BlockState current = level.getBlockState(pos);
		boolean own = index.isPlaced(level, pos);
		if (!own && !current.canBeReplaced() && current != original) {
			// Not the builder's block and not the air it left: a player built here since.
			return Result.SKIPPED;
		}
		if (guard.inTheWay(level, pos, original)) {
			return Result.DEFERRED;
		}
		level.setBlock(pos, original, original.isAir() ? REMOVE_FLAGS : PLACE_FLAGS);
		index.setPlaced(level, pos, new java.util.UUID(0L, 0L), false);
		index.setRemoved(level, pos, new java.util.UUID(0L, 0L), false);
		return Result.PLACED;
	}

	/**
	 * TIDY (DESIGN 21.9): fences, walls, panes, bars and fence gates reconnect to their neighbours; never stairs or plants.
	 * Only when all four horizontal neighbours are loaded (a shape update reads them; a chunk is never loaded for it).
	 * Returns true when a block changed.
	 */
	static boolean tidy(ServerLevel level, BlockPos pos, CellIndex index) {
		if (!level.isLoaded(pos) || !index.isPlaced(level, pos)) {
			return false;
		}
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (!level.isLoaded(pos.relative(direction))) {
				return false;
			}
		}
		BlockState state = level.getBlockState(pos);
		if (!tidyable(state)) {
			return false;
		}
		BlockState updated = Block.updateFromNeighbourShapes(state, level, pos);
		if (updated != state && !updated.isAir()) {
			level.setBlock(pos, updated, TIDY_FLAGS);
			return true;
		}
		return false;
	}

	static boolean tidyable(BlockState state) {
		return state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.BARS)
				|| state.is(Blocks.IRON_BARS) || state.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock;
	}

	/**
	 * True when {@code pos} is the open component's waterfall source (anchor "waterfall*"): the one WATER op allowed to
	 * replace the builder's own block, so a notch cut into an earlier component's deck or slope never stays dry.
	 */
	private static boolean waterfallSource(Site site, BlockPos pos) {
		Emission emission = site.emission;
		if (emission == null || emission.anchors.isEmpty()) {
			return false;
		}
		for (java.util.Map.Entry<String, BlockPos> anchor : emission.anchors.entrySet()) {
			BlockPos local = anchor.getValue();
			if (anchor.getKey().startsWith("waterfall") && site.plan().world(local.getX(), local.getY(), local.getZ()).equals(pos)) {
				return true;
			}
		}
		return false;
	}

	/** Starts a waterfall: the only water the builder gives a fluid tick (DESIGN 21.10). */
	static void startWaterfall(ServerLevel level, BlockPos pos) {
		if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.WATER)) {
			level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
	}

	/** The plaque sign: the sect's name in glowing yellow, waxed (DESIGN 21.7). */
	static void writePlaque(ServerLevel level, BlockPos pos, Component name) {
		if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			SignText text = SignText.EMPTY.asMutable().setLine(1, name).setColor(DyeColor.YELLOW).setTextGlowing(true).asImmutable();
			sign.setText(text, SignTextSlot.FRONT);
			sign.setWaxed(true);
		}
	}

	private BuildPlacer() {
	}
}
