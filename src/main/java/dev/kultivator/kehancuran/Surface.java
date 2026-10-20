package dev.kultivator.kehancuran;

import dev.kultivator.technique.spec.dsl.Dampak;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Kawah Lebur, the Phase 10 surface pass of a Mode Kehancuran job (DESIGN 28.8): after a job's carving, the floor under
 * the carved columns and the rim around their tops turn into the palette's blocks, inside the same job, the same undo
 * entry and the same per-tick budget ({@link WorldBreaker} does the changing). This class only plans: pure geometry, at
 * most 4 × cap block reads, deterministic per job (seeded by the job centre, targets by a position hash).
 * <p>
 * Never a valuable, farmable or unsafe target (28.8.2): no obsidian, crying obsidian, ice, blue ice, amethyst, ores,
 * lava, fire, powder snow, sand or gravel, nothing with a block entity, no gravity block. Magma never within
 * {@link #MAGMA_SAFE} blocks of the cast position nor under a standing player (blackstone instead).
 */
public final class Surface {

	/** Magma keeps at least this far from where the cast was made (28.8.1 rule 2). */
	public static final double MAGMA_SAFE = 6.0D;

	/** One planned pass: positions with the state read at planning time and the state each becomes. */
	record Plan(long[] positions, BlockState[] from, BlockState[] to) {
		boolean isEmpty() {
			return positions.length == 0;
		}
	}

	static final Plan EMPTY = new Plan(new long[0], new BlockState[0], new BlockState[0]);

	/** The cap of one pass: {@code min(kehancuranSurfaceMax, max(16, changed / 8))}. */
	static int cap(int changed, int surfaceMax) {
		return Math.min(Math.max(16, surfaceMax), Math.max(16, changed / 8));
	}

	/**
	 * Plans the pass of a finished carve: the block under each carved position that was not carved itself (the floor) and
	 * the four side neighbours of each carved column's top (the rim), duplicates dropped, a seeded sample of at most
	 * {@code cap} that the palette changes. Read-only; loaded chunks only.
	 *
	 * @param castPos where the caster stood (magma keeps 6 blocks away; null = no magma at all)
	 * @param axis    the horizontal direction of the cast (TAIJI bands run across it)
	 */
	static Plan plan(ServerLevel level, long[] carved, int carvedCount, Dampak.Palette palette, int cap, BlockPos centre,
			@Nullable Vec3 castPos, Vec3 axis) {
		if (palette == Dampak.Palette.NONE || carvedCount <= 0 || cap <= 0) {
			return EMPTY;
		}
		Set<Long> removed = new HashSet<>(carvedCount * 2);
		Map<Long, Integer> tops = new LinkedHashMap<>();
		for (int i = 0; i < carvedCount; i++) {
			removed.add(carved[i]);
			BlockPos pos = BlockPos.of(carved[i]);
			tops.merge(Shapes.columnKey(pos), pos.getY(), Math::max);
		}
		// floor first (false = floor, true = rim); a position named twice stays a floor
		Map<Long, Boolean> candidates = new LinkedHashMap<>();
		for (int i = 0; i < carvedCount; i++) {
			long below = BlockPos.of(carved[i]).below().asLong();
			if (!removed.contains(below)) {
				candidates.putIfAbsent(below, false);
			}
		}
		for (Map.Entry<Long, Integer> entry : tops.entrySet()) {
			BlockPos column = BlockPos.of(entry.getKey());
			BlockPos top = new BlockPos(column.getX(), entry.getValue(), column.getZ());
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				long side = top.relative(direction).asLong();
				if (!removed.contains(side)) {
					candidates.putIfAbsent(side, true);
				}
			}
		}
		List<Long> order = new ArrayList<>(candidates.keySet());
		long seed = centre.asLong() * 0x9E3779B97F4A7C15L;
		java.util.Collections.shuffle(order, new Random(seed));
		Vec3 flat = new Vec3(axis.x, 0.0D, axis.z);
		flat = flat.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
		int reads = 4 * cap;
		List<Long> positions = new ArrayList<>();
		List<BlockState> from = new ArrayList<>();
		List<BlockState> to = new ArrayList<>();
		for (Long packed : order) {
			if (positions.size() >= cap || reads <= 0) {
				break;
			}
			BlockPos pos = BlockPos.of(packed);
			if (!level.isLoaded(pos)) {
				continue;
			}
			reads--;
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty() || !Shapes.natural(state)) {
				continue;
			}
			boolean magmaOk = castPos != null && Vec3.atCenterOf(pos).distanceTo(castPos) >= MAGMA_SAFE;
			BlockState target = target(palette, state, candidates.get(packed), pos, seed, magmaOk, centre, flat);
			if (target == null || target.is(state.getBlock())) {
				continue;
			}
			positions.add(packed);
			from.add(state);
			to.add(target);
		}
		if (positions.isEmpty()) {
			return EMPTY;
		}
		long[] array = new long[positions.size()];
		for (int i = 0; i < array.length; i++) {
			array[i] = positions.get(i);
		}
		return new Plan(array, from.toArray(new BlockState[0]), to.toArray(new BlockState[0]));
	}

	/**
	 * What {@code source} becomes under {@code palette} ({@code rim}: a crater wall block, else the floor), or null when
	 * it stays. Deterministic: the weighted pick uses a hash of the position and the job seed.
	 */
	static @Nullable BlockState target(Dampak.Palette palette, BlockState source, boolean rim, BlockPos pos, long seed, boolean magmaOk,
			BlockPos centre, Vec3 axis) {
		boolean dirt = source.is(BlockTags.DIRT);
		boolean sand = source.is(Blocks.SAND) || source.is(Blocks.RED_SAND);
		int roll = roll(pos, seed);
		Block block = switch (palette) {
			case FIRE -> sand ? Blocks.GLASS
					: rim ? (roll < 50 ? Blocks.BLACKSTONE : Blocks.BASALT)
					: roll < 12 ? (magmaOk ? Blocks.MAGMA_BLOCK : Blocks.BLACKSTONE)
					: roll < 52 ? Blocks.BLACKSTONE : roll < 82 ? Blocks.BASALT : Blocks.SMOOTH_BASALT;
			case ICE -> rim ? Blocks.SNOW_BLOCK : roll < 55 ? Blocks.PACKED_ICE : Blocks.SNOW_BLOCK;
			case LIGHTNING -> sand ? Blocks.GLASS
					: rim ? (dirt ? Blocks.COARSE_DIRT : null)
					: roll < 60 ? Blocks.TUFF : Blocks.SMOOTH_BASALT;
			case SWORD -> rim ? Blocks.CALCITE : roll < 50 ? Blocks.CALCITE : roll < 85 ? Blocks.SMOOTH_STONE : Blocks.POLISHED_DIORITE;
			case SPACE -> rim ? Blocks.TUFF : roll < 40 ? Blocks.POLISHED_BLACKSTONE : roll < 70 ? Blocks.BLACKSTONE : Blocks.TUFF;
			case EARTH -> rim ? Blocks.COARSE_DIRT : roll < 40 ? Blocks.COBBLED_DEEPSLATE : roll < 70 ? Blocks.TUFF : Blocks.COARSE_DIRT;
			case LIFE -> !dirt ? null : rim ? Blocks.MOSS_BLOCK : roll < 60 ? Blocks.MOSS_BLOCK : Blocks.ROOTED_DIRT;
			case SHADOW -> dirt ? (rim || roll < 50 ? Blocks.SOUL_SOIL : null) : rim ? null : roll < 50 ? Blocks.BLACKSTONE : null;
			case TAIJI -> {
				if (rim) {
					yield null;
				}
				double along = (pos.getX() + 0.5D - centre.getX() - 0.5D) * axis.x + (pos.getZ() + 0.5D - centre.getZ() - 0.5D) * axis.z;
				yield Math.floorMod((int) Math.floor(along / 2.0D), 2) == 0 ? Blocks.CALCITE : Blocks.BLACKSTONE;
			}
			case NONE -> null;
		};
		return block == null ? null : block.defaultBlockState();
	}

	/** The palette of a Dao (own arts paint their crater in the creation's first Dao, 28.12). */
	public static Dampak.Palette forDao(dev.kultivator.data.Dao dao) {
		return switch (dao) {
			case SWORD -> Dampak.Palette.SWORD;
			case FIRE -> Dampak.Palette.FIRE;
			case ICE -> Dampak.Palette.ICE;
			case LIGHTNING -> Dampak.Palette.LIGHTNING;
			case BODY -> Dampak.Palette.EARTH;
			case LIFE -> Dampak.Palette.LIFE;
			case SHADOW -> Dampak.Palette.SHADOW;
			case SPACE -> Dampak.Palette.SPACE;
		};
	}

	/** 0-99 from the position and the seed (the same on every run of the same job). */
	private static int roll(BlockPos pos, long seed) {
		long h = pos.asLong() * 0xC2B2AE3D27D4EB4FL ^ seed;
		h ^= h >>> 29;
		h *= 0x165667B19E3779F9L;
		h ^= h >>> 32;
		return (int) Math.floorMod(h, 100L);
	}

	/** True for a planned magma block (the per-batch "no standing player on it" check). */
	static boolean magma(BlockState state) {
		return state.is(Blocks.MAGMA_BLOCK);
	}

	private Surface() {
	}
}
