package dev.kultivator.formasi.match;

import dev.kultivator.formasi.spec.Rule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only block access for the matcher (DESIGN 24.2.3): {@code getChunkNow} with a one-chunk cache, never a chunk load.
 * Each slot reads as {@link #OK}, {@link #MISSING} (air or replaceable), {@link #WRONG} or {@link #UNKNOWN} (chunk not
 * loaded). Counts every read for the budgets.
 */
public final class ChunkReader {

	public static final byte UNKNOWN = 0;
	public static final byte OK = 1;
	public static final byte MISSING = 2;
	public static final byte WRONG = 3;

	private final ServerLevel level;
	private @Nullable LevelChunk chunk;
	private int chunkX = Integer.MIN_VALUE;
	private int chunkZ = Integer.MIN_VALUE;
	private int reads;

	public ChunkReader(ServerLevel level) {
		this.level = level;
	}

	/** The state at {@code pos}, or null when its chunk is not loaded. */
	public @Nullable BlockState state(BlockPos pos) {
		LevelChunk at = chunk(pos);
		return at == null ? null : at.getBlockState(pos);
	}

	/** Reads one slot against {@code rule} in rotation {@code rot}. */
	public byte read(BlockPos pos, Rule rule, int rot) {
		reads++;
		LevelChunk at = chunk(pos);
		if (at == null) {
			return UNKNOWN;
		}
		BlockState state = at.getBlockState(pos);
		FluidState fluid = at.getFluidState(pos);
		if (rule.test(state, fluid, rot)) {
			return OK;
		}
		return state.isAir() || state.canBeReplaced() ? MISSING : WRONG;
	}

	public int reads() {
		return reads;
	}

	public ServerLevel level() {
		return level;
	}

	private @Nullable LevelChunk chunk(BlockPos pos) {
		int cx = pos.getX() >> 4;
		int cz = pos.getZ() >> 4;
		if (cx != chunkX || cz != chunkZ) {
			chunkX = cx;
			chunkZ = cz;
			chunk = level.getChunkSource().getChunkNow(cx, cz);
		}
		return chunk;
	}
}
