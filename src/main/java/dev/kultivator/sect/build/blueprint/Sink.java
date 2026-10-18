package dev.kultivator.sect.build.blueprint;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects the build ops of one component while its parts emit them (DESIGN 21.9), then orders them into an
 * {@link Emission}. Parts draw in their own <b>canonical frame</b> (x across, front toward +z, y up) after the component
 * {@link #push pushes} a frame (origin and quarter turns); the sink maps every position to template-local coordinates and
 * rotates every state with vanilla {@code BlockState.rotate}, so a part never handles orientation itself.
 * <p>
 * Rules the sink enforces: the last write to a cell wins (state, phase and layer); the stele cell (0,0,0) and the block
 * under it are never written; cells marked {@link #keep} (duty posts) stay air; ops outside the footprint column are
 * dropped and counted. {@link #finish} sets every stair's {@code SHAPE} exactly as vanilla {@code getStairsShape} would
 * compute it from the planned neighbours (DESIGN 21.6.2 robustness) and sorts the ops: phase, layer (y; top-down for
 * CUT and CLEAR; the roof's half-block height s), then mirror pairs outward from the part's axis, so a building is
 * symmetric at every moment of its growth.
 * <p>
 * Not thread-safe; one sink per emission (emission runs off the server thread on immutable inputs).
 */
public final class Sink {

	/** Safety cap of ops per component. */
	public static final int MAX_OPS = 400_000;

	private final String componentId;
	private final @Nullable BoundingBox clip;
	private final LongSet keep;
	private int size;
	private long[] pos = new long[1024];
	private BlockState[] states = new BlockState[1024];
	private byte[] phases = new byte[1024];
	private int[] layers = new int[1024];
	private int[] mirror = new int[1024];
	private int[] frameZ = new int[1024];
	private final Long2IntOpenHashMap index = new Long2IntOpenHashMap();
	/** Cells cleared by {@link #demolish} (template-local packed). */
	private final LongOpenHashSet demolished = new LongOpenHashSet();
	private boolean demolishing;
	private final Map<String, BlockPos> anchors = new LinkedHashMap<>();
	private int outside;

	private int ox;
	private int oy;
	private int oz;
	private int q;
	private final Deque<int[]> frames = new ArrayDeque<>();
	private Phase phase = Phase.PLINTH;
	private int layerOverride = Integer.MIN_VALUE;
	private boolean optional;

	/**
	 * @param componentId the component being emitted
	 * @param clip        the footprint column in template-local coordinates (null = no clip, for previews of a single part)
	 * @param keep        template-local packed cells that must stay air (duty posts and their head room)
	 */
	public Sink(String componentId, @Nullable BoundingBox clip, @Nullable LongSet keep) {
		this.componentId = componentId;
		this.clip = clip;
		this.keep = keep == null ? new LongOpenHashSet() : keep;
		this.index.defaultReturnValue(-1);
	}

	// ================================================================== frame

	/**
	 * Starts a new canonical frame at ({@code x, y, z}) of the current frame, turned {@code quarterTurns} clockwise (seen
	 * from above) relative to it: 0 keeps "front = +z", 1 makes the part's front face -x (west), 2 north, 3 east.
	 */
	public Sink push(int x, int y, int z, int quarterTurns) {
		frames.push(new int[]{ox, oy, oz, q});
		int lx = localX(x, z);
		int lz = localZ(x, z);
		ox = lx;
		oy = oy + y;
		oz = lz;
		q = Math.floorMod(q + quarterTurns, 4);
		return this;
	}

	/** A frame whose front faces {@code front} (template-local direction), at ({@code x, y, z}) of the current frame. */
	public Sink pushFacing(int x, int y, int z, Direction front) {
		int turns = switch (front) {
			case WEST -> 1;
			case NORTH -> 2;
			case EAST -> 3;
			default -> 0;
		};
		return push(x, y, z, turns - q);
	}

	public Sink pop() {
		int[] frame = frames.pop();
		ox = frame[0];
		oy = frame[1];
		oz = frame[2];
		q = frame[3];
		return this;
	}

	/** The vanilla rotation of the current frame (template-local). */
	public Rotation rotation() {
		return rot(q);
	}

	/** A canonical direction of the current frame as a template-local direction. */
	public Direction dir(Direction canonical) {
		return canonical.getAxis().isVertical() ? canonical : rot(q).rotate(canonical);
	}

	/** Template-local x of a frame position. */
	public int localX(int x, int z) {
		return switch (q) {
			case 1 -> ox - z;
			case 2 -> ox - x;
			case 3 -> ox + z;
			default -> ox + x;
		};
	}

	/** Template-local z of a frame position. */
	public int localZ(int x, int z) {
		return switch (q) {
			case 1 -> oz + x;
			case 2 -> oz - z;
			case 3 -> oz - x;
			default -> oz + z;
		};
	}

	/** Template-local y of a frame y. */
	public int localY(int y) {
		return oy + y;
	}

	/** The frame y of a template-local y (to place parts on terrain read from {@link Ctx}). */
	public int frameY(int localY) {
		return localY - oy;
	}

	/** The natural ground (template-local y converted to frame y) under frame column (x, z). */
	public int ground(Ctx ctx, int x, int z) {
		return frameY(ctx.terrain().ground(localX(x, z), localZ(x, z)));
	}

	/** The top non-air block (frame y) of frame column (x, z). */
	public int top(Ctx ctx, int x, int z) {
		return frameY(ctx.terrain().top(localX(x, z), localZ(x, z)));
	}

	/** Water depth of frame column (x, z). */
	public int water(Ctx ctx, int x, int z) {
		return ctx.terrain().water(localX(x, z), localZ(x, z));
	}

	/** Terrain flags of frame column (x, z). */
	public int flags(Ctx ctx, int x, int z) {
		return ctx.terrain().flags(localX(x, z), localZ(x, z));
	}

	/** The fitted standing level of {@code lv} as a frame y. */
	public int level(Ctx ctx, Lv lv) {
		return frameY(ctx.level(lv));
	}

	// ================================================================== phase and layer

	public Sink phase(Phase phase) {
		this.phase = phase;
		this.layerOverride = Integer.MIN_VALUE;
		return this;
	}

	public Phase phase() {
		return phase;
	}

	/** Uses {@code layer} for the next ops instead of the automatic one (the roof's s, a custom sweep). */
	public Sink layer(int layer) {
		this.layerOverride = layer;
		return this;
	}

	/**
	 * Marks the next ops as optional decoration (flying tips, wind bells): outside the footprint column they are dropped
	 * silently instead of being counted as violations. Plants and ornaments (GARDEN, ORNAMENT) are always optional.
	 */
	public Sink optional(boolean optional) {
		this.optional = optional;
		return this;
	}

	/** Back to the automatic layer (y; -y for CUT and CLEAR). */
	public Sink layerAuto() {
		this.layerOverride = Integer.MIN_VALUE;
		return this;
	}

	// ================================================================== writing

	/** Plans {@code state} at frame (x, y, z); the last write to a cell wins. */
	public Sink set(int x, int y, int z, BlockState state) {
		if (state == null) {
			return this;
		}
		int lx = localX(x, z);
		int ly = oy + y;
		int lz = localZ(x, z);
		if (lx == 0 && lz == 0 && (ly == 0 || ly == -1)) {
			// The stele and the block under it are never edited (DESIGN 21.9).
			return this;
		}
		long key = BlockPos.asLong(lx, ly, lz);
		if (!state.isAir() && keep.contains(key)) {
			return this;
		}
		if (clip != null && !clip.isInside(lx, ly, lz)) {
			if (!optional && phase != Phase.GARDEN && phase != Phase.ORNAMENT) {
				outside++;
			}
			return this;
		}
		Phase effective = phase;
		if (demolishing) {
			demolished.add(key);
		} else if (demolished.contains(key)) {
			if (state.isAir() && (phase == Phase.CLEAR || phase == Phase.CUT)) {
				// The demolition's own air (FRAME) already clears the cell, also over an earlier component's block.
				return this;
			}
			if (!state.isAir() && state.getFluidState().isEmpty() && (phase == Phase.GARDEN || phase == Phase.ORNAMENT || phase == Phase.WATER)) {
				// GARDEN/ORNAMENT/WATER never replace an earlier component's block in game: inside a demolished box the
				// replacement is planned in FRAME so the light swap really happens.
				effective = Phase.FRAME;
			}
		}
		BlockState rotated = q == 0 ? state : state.rotate(rot(q));
		int layer = layerOverride != Integer.MIN_VALUE && effective == phase ? layerOverride
				: (effective == Phase.CUT || effective == Phase.CLEAR ? -ly : ly);
		int existing = index.get(key);
		int slot;
		if (existing >= 0) {
			slot = existing;
		} else {
			if (size >= MAX_OPS) {
				outside++;
				return this;
			}
			grow();
			slot = size++;
			index.put(key, slot);
			pos[slot] = key;
		}
		states[slot] = rotated;
		phases[slot] = (byte) effective.ordinal();
		layers[slot] = layer;
		mirror[slot] = Math.abs(x) * 2 + (x < 0 ? 1 : 0);
		frameZ[slot] = z;
		return this;
	}

	/** Plans {@code state} only where nothing is planned yet. */
	public Sink setIfAbsent(int x, int y, int z, BlockState state) {
		if (get(x, y, z) == null) {
			set(x, y, z, state);
		}
		return this;
	}

	/** Plans air (a cut or a cleared cell). */
	public Sink air(int x, int y, int z) {
		return set(x, y, z, Blocks.AIR.defaultBlockState());
	}

	/**
	 * Plans air that dismantles an earlier component's blocks (a light swap, {@code Marks.demolish}), in the current
	 * phase and layer. Later CLEAR/CUT air on the cell keeps this op, and a later non-fluid GARDEN, ORNAMENT or WATER
	 * block there is planned in FRAME, because in game those phases never replace an earlier component's block.
	 */
	public Sink demolish(int x, int y, int z) {
		demolishing = true;
		try {
			return set(x, y, z, Blocks.AIR.defaultBlockState());
		} finally {
			demolishing = false;
		}
	}

	/** Fills the frame box between two corners (inclusive, any order). */
	public Sink fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		int ax = Math.min(x0, x1);
		int bx = Math.max(x0, x1);
		int ay = Math.min(y0, y1);
		int by = Math.max(y0, y1);
		int az = Math.min(z0, z1);
		int bz = Math.max(z0, z1);
		for (int y = ay; y <= by; y++) {
			for (int x = ax; x <= bx; x++) {
				for (int z = az; z <= bz; z++) {
					set(x, y, z, state);
				}
			}
		}
		return this;
	}

	/** Plans the same state at (x, z) and (-x, z): a mirror pair about the frame axis (only once when x is 0). */
	public Sink mirror(int x, int y, int z, BlockState state, BlockState mirrored) {
		set(x, y, z, state);
		if (x != 0) {
			set(-x, y, z, mirrored);
		}
		return this;
	}

	/** The planned state at frame (x, y, z) in template-local orientation, or null. */
	public @Nullable BlockState get(int x, int y, int z) {
		int slot = index.get(BlockPos.asLong(localX(x, z), oy + y, localZ(x, z)));
		return slot < 0 ? null : states[slot];
	}

	/** True when a non-air state is planned at frame (x, y, z). */
	public boolean solid(int x, int y, int z) {
		BlockState state = get(x, y, z);
		return state != null && !state.isAir();
	}

	/** Removes any plan at frame (x, y, z) (a part that leaves a cell to the world). */
	public Sink forget(int x, int y, int z) {
		long key = BlockPos.asLong(localX(x, z), oy + y, localZ(x, z));
		int slot = index.get(key);
		if (slot >= 0) {
			states[slot] = null;
		}
		return this;
	}

	/** Names a frame position (an anchor: "post_water", "library", "tower", "work_*", ...). */
	public Sink anchor(String id, int x, int y, int z) {
		anchors.put(id, new BlockPos(localX(x, z), oy + y, localZ(x, z)));
		return this;
	}

	/** True when frame (x, y, z) is a kept-open cell (a duty post). */
	public boolean kept(int x, int y, int z) {
		return keep.contains(BlockPos.asLong(localX(x, z), oy + y, localZ(x, z)));
	}

	public String componentId() {
		return componentId;
	}

	public int size() {
		return size;
	}

	// ================================================================== finish

	/** Fixes stair shapes, sorts the ops and freezes them into an {@link Emission}. */
	public Emission finish() {
		int live = 0;
		for (int i = 0; i < size; i++) {
			if (states[i] != null) {
				live++;
			}
		}
		for (int i = 0; i < size; i++) {
			BlockState state = states[i];
			if (state != null && state.getBlock() instanceof StairBlock) {
				states[i] = state.setValue(BlockStateProperties.STAIRS_SHAPE, stairShape(state, pos[i]));
			}
		}
		Integer[] order = new Integer[live];
		int n = 0;
		for (int i = 0; i < size; i++) {
			if (states[i] != null) {
				order[n++] = i;
			}
		}
		Arrays.sort(order, (a, b) -> {
			int c = Byte.compare(phases[a], phases[b]);
			if (c != 0) {
				return c;
			}
			c = Integer.compare(layers[a], layers[b]);
			if (c != 0) {
				return c;
			}
			c = Integer.compare(mirror[a] >> 1, mirror[b] >> 1);
			if (c != 0) {
				return c;
			}
			c = Integer.compare(frameZ[a], frameZ[b]);
			if (c != 0) {
				return c;
			}
			c = Integer.compare(mirror[a] & 1, mirror[b] & 1);
			if (c != 0) {
				return c;
			}
			c = Integer.compare(BlockPos.getY(pos[a]), BlockPos.getY(pos[b]));
			return c != 0 ? c : Integer.compare(a, b);
		});
		long[] outPos = new long[live];
		BlockState[] outStates = new BlockState[live];
		byte[] outPhases = new byte[live];
		int[] outLayers = new int[live];
		BoundingBox box = null;
		for (int k = 0; k < live; k++) {
			int i = order[k];
			outPos[k] = pos[i];
			outStates[k] = states[i];
			outPhases[k] = phases[i];
			outLayers[k] = layers[i];
			if (!states[i].isAir()) {
				int x = BlockPos.getX(pos[i]);
				int y = BlockPos.getY(pos[i]);
				int z = BlockPos.getZ(pos[i]);
				if (box == null) {
					box = new BoundingBox(x, y, z, x, y, z);
				} else {
					box = BoundingBox.encapsulating(box, new BoundingBox(x, y, z, x, y, z));
				}
			}
		}
		return new Emission(componentId, outPos, outStates, outPhases, outLayers, anchors, box, outside);
	}

	/** Vanilla {@code StairBlock.getStairsShape} on the planned neighbours (unplanned neighbours count as no stair). */
	private StairsShape stairShape(BlockState state, long at) {
		Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
		Half half = state.getValue(BlockStateProperties.HALF);
		BlockState behind = planned(at, facing);
		if (isStair(behind) && behind.getValue(BlockStateProperties.HALF) == half) {
			Direction behindFacing = behind.getValue(BlockStateProperties.HORIZONTAL_FACING);
			if (behindFacing.getAxis() != facing.getAxis() && canTakeShape(state, at, behindFacing.getOpposite())) {
				return behindFacing == facing.getCounterClockWise() ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
			}
		}
		BlockState front = planned(at, facing.getOpposite());
		if (isStair(front) && front.getValue(BlockStateProperties.HALF) == half) {
			Direction frontFacing = front.getValue(BlockStateProperties.HORIZONTAL_FACING);
			if (frontFacing.getAxis() != facing.getAxis() && canTakeShape(state, at, frontFacing)) {
				return frontFacing == facing.getCounterClockWise() ? StairsShape.INNER_LEFT : StairsShape.INNER_RIGHT;
			}
		}
		return StairsShape.STRAIGHT;
	}

	private boolean canTakeShape(BlockState state, long at, Direction neighbour) {
		BlockState other = planned(at, neighbour);
		return !isStair(other) || other.getValue(BlockStateProperties.HORIZONTAL_FACING) != state.getValue(BlockStateProperties.HORIZONTAL_FACING)
				|| other.getValue(BlockStateProperties.HALF) != state.getValue(BlockStateProperties.HALF);
	}

	private @Nullable BlockState planned(long at, Direction direction) {
		int slot = index.get(BlockPos.offset(at, direction));
		return slot < 0 ? null : states[slot];
	}

	private static boolean isStair(@Nullable BlockState state) {
		return state != null && state.getBlock() instanceof StairBlock;
	}

	/** The vanilla shape a stair at {@code at} gets from its neighbours, given a lookup (also used by the audit). */
	public static StairsShape vanillaShape(BlockState state, java.util.function.Function<Direction, BlockState> neighbour) {
		Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
		Half half = state.getValue(BlockStateProperties.HALF);
		BlockState behind = neighbour.apply(facing);
		if (isStair(behind) && behind.getValue(BlockStateProperties.HALF) == half) {
			Direction behindFacing = behind.getValue(BlockStateProperties.HORIZONTAL_FACING);
			if (behindFacing.getAxis() != facing.getAxis() && canTake(state, neighbour.apply(behindFacing.getOpposite()))) {
				return behindFacing == facing.getCounterClockWise() ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
			}
		}
		BlockState front = neighbour.apply(facing.getOpposite());
		if (isStair(front) && front.getValue(BlockStateProperties.HALF) == half) {
			Direction frontFacing = front.getValue(BlockStateProperties.HORIZONTAL_FACING);
			if (frontFacing.getAxis() != facing.getAxis() && canTake(state, neighbour.apply(frontFacing))) {
				return frontFacing == facing.getCounterClockWise() ? StairsShape.INNER_LEFT : StairsShape.INNER_RIGHT;
			}
		}
		return StairsShape.STRAIGHT;
	}

	private static boolean canTake(BlockState state, @Nullable BlockState other) {
		return !isStair(other) || other.getValue(BlockStateProperties.HORIZONTAL_FACING) != state.getValue(BlockStateProperties.HORIZONTAL_FACING)
				|| other.getValue(BlockStateProperties.HALF) != state.getValue(BlockStateProperties.HALF);
	}

	private void grow() {
		if (size < pos.length) {
			return;
		}
		int capacity = pos.length * 2;
		pos = Arrays.copyOf(pos, capacity);
		states = Arrays.copyOf(states, capacity);
		phases = Arrays.copyOf(phases, capacity);
		layers = Arrays.copyOf(layers, capacity);
		mirror = Arrays.copyOf(mirror, capacity);
		frameZ = Arrays.copyOf(frameZ, capacity);
	}

	/** The vanilla rotation of {@code quarterTurns} clockwise turns. */
	public static Rotation rot(int quarterTurns) {
		return switch (Math.floorMod(quarterTurns, 4)) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}
}
