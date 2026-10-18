package dev.kultivator.sect.build.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Fluids are sealed first (DESIGN 21.10 "Fluids"): before a cell inside the footprint becomes air, every fluid cell
 * touching it becomes fill (stone), placed through {@link BuildPlacer}. Only pure fluids and natural water plants (kelp,
 * seagrass: their drops are credited first) are sealed. A fluid outside the footprint or in an unloaded chunk, one of
 * the sect's own cells, a player-made or container waterlogged block, a protected block or a seal cell with an entity in
 * it means the cell never becomes air: it becomes retaining wall instead. Reads on the server thread only; it never
 * changes a block itself.
 */
public final class FluidGuard {

	/** What a cell that may not become air becomes. */
	public static BlockState retaining() {
		return Blocks.STONE_BRICKS.defaultBlockState();
	}

	/** What a sealed fluid cell becomes. */
	public static BlockState fill() {
		return Blocks.STONE.defaultBlockState();
	}

	/** {@link #sealable} results: every fluid neighbour may be sealed; the cell must stay solid; wait for a swimmer. */
	static final int SEAL = 0;
	static final int RETAIN = 1;
	static final int WAIT = 2;

	/**
	 * Checks the six neighbours of {@code pos}: {@link #SEAL} when every fluid neighbour may be sealed ({@code seal}
	 * receives each of them and {@code natural} those whose block is a natural water plant, to be credited first),
	 * {@link #RETAIN} when one may never be sealed, {@link #WAIT} when an entity is in a cell that would be sealed.
	 */
	static int sealable(ServerLevel level, Site site, CellIndex index, BlockPos pos, List<BlockPos> seal, List<BlockPos> natural, EntityGuard guard) {
		SitePlan plan = site.plan();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		boolean wait = false;
		for (Direction direction : Direction.values()) {
			cursor.setWithOffset(pos, direction);
			if (!level.isLoaded(cursor)) {
				return RETAIN;
			}
			BlockState state = level.getBlockState(cursor);
			if (state.getFluidState().isEmpty()) {
				continue;
			}
			if (!plan.inFootprint(cursor) || cursor.equals(site.stele) || cursor.equals(site.stele.below())) {
				return RETAIN;
			}
			BlockPos at = cursor.immutable();
			Classifier.Kind kind = Classifier.classify(level, at, state, index, site);
			if (kind == Classifier.Kind.NATURAL || kind == Classifier.Kind.PLANT) {
				natural.add(at);
			} else if (kind != Classifier.Kind.FLUID) {
				// Own water, a player's waterlogged stairs or chest, a protected block: never sealed over.
				return RETAIN;
			}
			if (guard.inTheWay(level, at, fill())) {
				// Someone swims there: wait rather than encase them.
				wait = true;
			}
			seal.add(at);
		}
		return wait ? WAIT : SEAL;
	}

	private FluidGuard() {
	}
}
