package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The ordered build ops of one component (DESIGN 21.9), as produced by {@link Sink#finish}: template-local packed
 * positions ({@code BlockPos.asLong}), the target states in template-local orientation (air = remove), the phase and
 * layer of each op, the named anchors the component defines, and the local box of its non-air ops. Immutable once built;
 * made off the server thread from immutable inputs only.
 */
public final class Emission {

	public final String componentId;
	public final long[] pos;
	public final BlockState[] states;
	public final byte[] phases;
	public final int[] layers;
	public final Map<String, BlockPos> anchors;
	public final @Nullable BoundingBox bounds;
	/** Ops dropped because they fell outside the footprint column (an audit violation). */
	public final int outside;

	Emission(String componentId, long[] pos, BlockState[] states, byte[] phases, int[] layers, Map<String, BlockPos> anchors,
			@Nullable BoundingBox bounds, int outside) {
		this.componentId = componentId;
		this.pos = pos;
		this.states = states;
		this.phases = phases;
		this.layers = layers;
		this.anchors = Map.copyOf(anchors);
		this.bounds = bounds;
		this.outside = outside;
	}

	public int size() {
		return pos.length;
	}

	public Phase phase(int index) {
		return Phase.byOrdinal(phases[index]);
	}

	/** The index of the first op at or after ({@code phase}, {@code layer}); {@link #size()} when none (resume cursor). */
	public int indexOf(int phase, int layer) {
		for (int i = 0; i < pos.length; i++) {
			if (phases[i] > phase || phases[i] == phase && layers[i] >= layer) {
				return i;
			}
		}
		return pos.length;
	}

	/** An emission built by the engine itself (a repair list, a dismantling replay): arrays are taken as given. */
	public static Emission of(String id, long[] pos, BlockState[] states, byte[] phases, int[] layers) {
		return new Emission(id, pos, states, phases, layers, Map.of(), null, 0);
	}

	/**
	 * The ops {@code idx} of {@code base} in that order (a retry of a parked component's cells): anchors and bounds are
	 * kept, so finishing the retry still registers the component's anchors.
	 */
	public static Emission subset(Emission base, int[] idx) {
		int n = 0;
		for (int i : idx) {
			if (i >= 0 && i < base.size()) {
				n++;
			}
		}
		long[] pos = new long[n];
		BlockState[] states = new BlockState[n];
		byte[] phases = new byte[n];
		int[] layers = new int[n];
		int k = 0;
		for (int i : idx) {
			if (i >= 0 && i < base.size()) {
				pos[k] = base.pos[i];
				states[k] = base.states[i];
				phases[k] = base.phases[i];
				layers[k] = base.layers[i];
				k++;
			}
		}
		return new Emission(base.componentId, pos, states, phases, layers, base.anchors, base.bounds, 0);
	}

	/** An empty emission (a component with nothing to place). */
	public static Emission empty(String componentId) {
		return new Emission(componentId, new long[0], new BlockState[0], new byte[0], new int[0], Map.of(), null, 0);
	}
}
