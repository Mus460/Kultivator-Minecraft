package dev.kultivator.abadi;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.Nullable;

/**
 * The arrival island of Alam Abadi (DESIGN 26.3.4): a spiral of up to {@link #SAMPLES} column samples around 0, 0 that asks
 * the world generator for the terrain height ({@code ChunkGenerator.getBaseHeight(x, z, MOTION_BLOCKING, level,
 * randomState)}): pure noise sampling, no chunk is loaded or generated, at most {@link #PER_TICK} samples per tick, once per
 * world. The first column at or above {@link #MIN_TOP} wins; without one the fallback (0, 120, 0) is used (the arrival then
 * gets Slow Falling and the void catch). Stored in {@link AlamAbadiData}. Read-only: no block is written.
 */
public final class ArrivalIsland {

	public static final int SAMPLES = 25;
	public static final int PER_TICK = 8;
	public static final int MIN_TOP = 40;
	/** Distance between two rings of the spiral (blocks). */
	public static final int STEP = 40;
	public static final BlockPos FALLBACK = new BlockPos(0, 120, 0);

	private static int next = -1;

	/** Starts (or restarts) the search; it then runs {@link #PER_TICK} samples per tick. */
	static void restart(MinecraftServer server) {
		AlamAbadiData.get(server).setArrival(null, false);
		next = 0;
	}

	/** Every tick: continues a running search, or starts one when the dimension exists and no arrival is known yet. */
	static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Abadi.IMMORTAL_LEVEL);
		if (level == null) {
			return;
		}
		AlamAbadiData data = AlamAbadiData.get(server);
		if (data.arrival() != null) {
			next = -1;
			return;
		}
		if (next < 0) {
			next = 0;
		}
		for (int i = 0; i < PER_TICK && next < SAMPLES; i++, next++) {
			BlockPos found = sample(level, next);
			if (found != null) {
				data.setArrival(found, false);
				next = -1;
				return;
			}
		}
		if (next >= SAMPLES) {
			data.setArrival(FALLBACK, true);
			next = -1;
		}
	}

	/** The arrival island (finishing the search at once when it has not ended yet), or the fallback without a dimension. */
	public static BlockPos arrival(MinecraftServer server) {
		AlamAbadiData data = AlamAbadiData.get(server);
		if (data.arrival() != null) {
			return data.arrival();
		}
		ServerLevel level = server.getLevel(Abadi.IMMORTAL_LEVEL);
		if (level == null) {
			return FALLBACK;
		}
		int start = Math.max(0, next);
		for (int i = start; i < SAMPLES; i++) {
			BlockPos found = sample(level, i);
			if (found != null) {
				data.setArrival(found, false);
				next = -1;
				return found;
			}
		}
		data.setArrival(FALLBACK, true);
		next = -1;
		return FALLBACK;
	}

	/** True when the arrival is the fallback point over the void. */
	public static boolean usesFallback(MinecraftServer server) {
		AlamAbadiData data = AlamAbadiData.get(server);
		return data.arrival() == null || data.fallback();
	}

	/** Spiral sample {@code index}: the centre first, then rings of 8 points every {@link #STEP} blocks. */
	private static @Nullable BlockPos sample(ServerLevel level, int index) {
		int x;
		int z;
		if (index == 0) {
			x = 8;
			z = 8;
		} else {
			int ring = (index - 1) / 8 + 1;
			int point = (index - 1) % 8;
			double angle = Math.PI * 2.0D * point / 8.0D + ring * 0.37D;
			x = (int) Math.round(Math.cos(angle) * STEP * ring) + 8;
			z = (int) Math.round(Math.sin(angle) * STEP * ring) + 8;
		}
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState random = level.getChunkSource().randomState();
		int top = generator.getBaseHeight(x, z, Heightmap.Types.MOTION_BLOCKING, level, random);
		if (top >= MIN_TOP && top < level.getMaxY()) {
			return new BlockPos(x, top, z);
		}
		return null;
	}

	private ArrivalIsland() {
	}
}
