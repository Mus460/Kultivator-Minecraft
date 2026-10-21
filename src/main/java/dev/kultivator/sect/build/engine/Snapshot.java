package dev.kultivator.sect.build.engine;

import dev.kultivator.sect.build.blueprint.TerrainView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/**
 * The terrain snapshot of a site (DESIGN 21.10 "Snapshot and preview scan"): per world column of the square around the
 * stele that covers all four rotations, the top natural ground, the top non-air block, the water depth and flags (player
 * made, block entity, lava, tree, protected). Captured on the server thread chunk by chunk as chunks are loaded (never a
 * sync load: {@code getChunkNow} only; an unknown column is "not captured"), at most {@link #CHUNKS_PER_TICK} chunks per
 * tick, then frozen and handed to plan emission as an immutable {@link TerrainView} (template-local through
 * {@link #local}). Packed into the site's {@code build_site.snap} so a resumed plan re-emits identically.
 */
public final class Snapshot {

	/** Chunks captured per server tick at most (about 3 s for template 9). */
	public static final int CHUNKS_PER_TICK = 2;
	/** Steps probed down from the heightmap to find the natural ground. */
	public static final int PROBE = 40;
	/** Obstacles remembered for the hologram. */
	public static final int MAX_OBSTACLES = 256;
	/** Block entities remembered for the validation (with their height and kind). */
	public static final int MAX_ENTITIES = 4096;
	/** Block entity kinds: anything else (sign, bed, bee nest, empty container: amber), a container with items, protected. */
	public static final byte BE_OTHER = 0;
	public static final byte BE_ITEMS = 1;
	public static final byte BE_PROTECTED = 2;

	final int minX;
	final int minZ;
	final int size;
	final int steleY;
	final short[] ground;
	final short[] top;
	final byte[] water;
	final byte[] flags;
	final BitSet known;
	final List<Long> obstacles = new ArrayList<>();
	/** Every block entity the builder did not place (world positions, y included) and its kind. */
	final List<Long> entities = new ArrayList<>();
	final List<Byte> entityKinds = new ArrayList<>();

	private Snapshot(int minX, int minZ, int size, int steleY) {
		this.minX = minX;
		this.minZ = minZ;
		this.size = size;
		this.steleY = steleY;
		int n = size * size;
		this.ground = new short[n];
		this.top = new short[n];
		this.water = new byte[n];
		this.flags = new byte[n];
		this.known = new BitSet(n);
	}

	/** An empty snapshot of the square of half size {@code half} around {@code stele}. */
	public static Snapshot of(BlockPos stele, int half) {
		return new Snapshot(stele.getX() - half, stele.getZ() - half, 2 * half + 1, stele.getY());
	}

	int index(int wx, int wz) {
		int ix = wx - minX;
		int iz = wz - minZ;
		if (ix < 0 || iz < 0 || ix >= size || iz >= size) {
			return -1;
		}
		return ix * size + iz;
	}

	/** True when the world column was captured. */
	public boolean knownAt(int wx, int wz) {
		int i = index(wx, wz);
		return i >= 0 && known.get(i);
	}

	/** The scan-time flags of a world column ({@link TerrainView#TREE}...), 0 when unknown. */
	public int flagsAt(int wx, int wz) {
		int i = index(wx, wz);
		return i >= 0 && known.get(i) ? flags[i] : 0;
	}

	/** True when every column is captured. */
	public boolean complete() {
		return known.cardinality() >= size * size;
	}

	/** Captured share 0-100 of the columns inside {@code box} (world x/z, inclusive). */
	public int percent(int x0, int z0, int x1, int z1) {
		int total = 0;
		int have = 0;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int i = index(x, z);
				total++;
				if (i >= 0 && known.get(i)) {
					have++;
				}
			}
		}
		return total == 0 ? 100 : (int) Math.floor(100.0D * have / total);
	}

	/**
	 * Captures up to {@code budget} loaded, not yet captured chunks (server thread). Returns the chunks captured; reads
	 * only, never loads a chunk. The builder's own cells ({@code index}) are probed as air for the ground, so an upgrade
	 * fits to the land under the old sect, not to its roofs (DESIGN 21.18); they still count for the top.
	 */
	public int capture(ServerLevel level, int budget, @Nullable CellIndex index) {
		int captured = 0;
		int cx0 = minX >> 4;
		int cz0 = minZ >> 4;
		int cx1 = (minX + size - 1) >> 4;
		int cz1 = (minZ + size - 1) >> 4;
		for (int cx = cx0; cx <= cx1 && captured < budget; cx++) {
			for (int cz = cz0; cz <= cz1 && captured < budget; cz++) {
				if (chunkDone(cx, cz)) {
					continue;
				}
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				captureChunk(level, chunk, cx, cz, index);
				captured++;
			}
		}
		return captured;
	}

	private boolean chunkDone(int cx, int cz) {
		int x0 = Math.max(minX, cx << 4);
		int z0 = Math.max(minZ, cz << 4);
		int x1 = Math.min(minX + size - 1, (cx << 4) + 15);
		int z1 = Math.min(minZ + size - 1, (cz << 4) + 15);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int i = index(x, z);
				if (i >= 0 && !known.get(i)) {
					return false;
				}
			}
		}
		return true;
	}

	private void captureChunk(ServerLevel level, LevelChunk chunk, int cx, int cz, @Nullable CellIndex index) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int minY = level.getMinY();
		int x0 = Math.max(minX, cx << 4);
		int z0 = Math.max(minZ, cz << 4);
		int x1 = Math.min(minX + size - 1, (cx << 4) + 15);
		int z1 = Math.min(minZ + size - 1, (cz << 4) + 15);
		boolean own = index != null && index.hasAny(level);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int i = index(x, z);
				int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
				int y = Math.max(minY, surface);
				int topY = y;
				int groundY = y;
				int waterDepth = 0;
				int flag = 0;
				boolean leaves = false;
				boolean logs = false;
				for (int step = 0; step < PROBE && y >= minY; y--) {
					cursor.set(x, y, z);
					BlockState state = chunk.getBlockState(cursor);
					if (state.isAir()) {
						if (topY == y) {
							topY = y - 1;
						}
						step++;
						continue;
					}
					if (own && index.isPlaced(level, cursor)) {
						// The sect's own block: probed through (it counts for the top only, never as ground or a step).
						continue;
					}
					step++;
					if (state.getFluidState().is(Fluids.LAVA) || state.getFluidState().is(Fluids.FLOWING_LAVA)) {
						flag |= TerrainView.LAVA;
						groundY = y;
						break;
					}
					if (state.is(Blocks.WATER) || !state.getFluidState().isEmpty() && !state.isSolid()) {
						waterDepth++;
						continue;
					}
					if (state.is(BlockTags.LEAVES)) {
						// Only decaying leaves make a tree; a player's persistent leaves never do (DESIGN 21.10).
						leaves |= Classifier.naturalLeaves(state);
						if (!Classifier.naturalLeaves(state)) {
							flag |= TerrainView.PLAYER_MADE;
						}
						continue;
					}
					if (Classifier.growthPart(state)) {
						// A huge mushroom, a tree's bee nest, a natural creaking heart: looked through like a trunk.
						continue;
					}
					if (state.is(BlockTags.LOGS)) {
						if (Classifier.treeLog(state)) {
							logs = true;
						} else {
							flag |= TerrainView.PLAYER_MADE;
						}
						continue;
					}
					if (state.canBeReplaced() || !state.isSolid()) {
						if (!Classifier.naturalPlant(state)) {
							flag |= TerrainView.PLAYER_MADE;
						}
						continue;
					}
					groundY = y;
					if (!Classifier.naturalTagged(state) && !Classifier.rockySurface(level, cursor, state)) {
						flag |= TerrainView.PLAYER_MADE;
					}
					break;
				}
				if (leaves && logs) {
					flag |= TerrainView.TREE;
				} else if (logs) {
					flag |= TerrainView.PLAYER_MADE;
				}
				ground[i] = (short) (groundY - steleY);
				top[i] = (short) (Math.max(groundY, topY) - steleY);
				water[i] = (byte) Math.min(127, waterDepth);
				flags[i] = (byte) flag;
				known.set(i);
			}
		}
		for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
			BlockPos pos = entry.getKey();
			int i = index(pos.getX(), pos.getZ());
			if (i < 0 || own && index.isPlaced(level, pos) || Classifier.naturalTagged(entry.getValue().getBlockState())) {
				// The sect's own, or a natural one (a tree's bee nest): never an obstacle.
				continue;
			}
			flags[i] |= TerrainView.BLOCK_ENTITY;
			if (obstacles.size() < MAX_OBSTACLES) {
				obstacles.add(pos.asLong());
			}
			if (entities.size() < MAX_ENTITIES) {
				entities.add(pos.asLong());
				entityKinds.add(kindOf(level, pos, entry.getValue()));
			}
		}
	}

	/** The validation kind of a block entity (reads only: an unopened loot chest counts as holding items, never rolled). */
	private static byte kindOf(ServerLevel level, BlockPos pos, BlockEntity entity) {
		BlockState state = entity.getBlockState();
		if (Classifier.protectedBlock(level, pos, state)) {
			return BE_PROTECTED;
		}
		if (entity instanceof RandomizableContainer loot && loot.getLootTable() != null) {
			return BE_ITEMS;
		}
		if (entity instanceof Container container) {
			return container.isEmpty() ? BE_OTHER : BE_ITEMS;
		}
		// A modded block entity that is not a container is never touched at placement (Classifier).
		return "minecraft".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace()) ? BE_OTHER : BE_PROTECTED;
	}

	/** This snapshot seen in template-local coordinates for a site at {@code stele} with rotation {@code rot}. */
	public TerrainView local(BlockPos stele, int rot) {
		int r = Math.floorMod(rot, 4);
		int sx = stele.getX();
		int sz = stele.getZ();
		return new TerrainView() {
			private int idx(int x, int z) {
				int wx;
				int wz;
				switch (r) {
					case 1 -> {
						wx = sx - z;
						wz = sz + x;
					}
					case 2 -> {
						wx = sx - x;
						wz = sz - z;
					}
					case 3 -> {
						wx = sx + z;
						wz = sz - x;
					}
					default -> {
						wx = sx + x;
						wz = sz + z;
					}
				}
				return index(wx, wz);
			}

			@Override
			public int ground(int x, int z) {
				int i = idx(x, z);
				return i >= 0 && known.get(i) ? ground[i] : -1;
			}

			@Override
			public int top(int x, int z) {
				int i = idx(x, z);
				return i >= 0 && known.get(i) ? top[i] : -1;
			}

			@Override
			public int water(int x, int z) {
				int i = idx(x, z);
				return i >= 0 && known.get(i) ? water[i] : 0;
			}

			@Override
			public boolean known(int x, int z) {
				int i = idx(x, z);
				return i >= 0 && known.get(i);
			}

			@Override
			public int flags(int x, int z) {
				int i = idx(x, z);
				return i >= 0 && known.get(i) ? flags[i] : 0;
			}
		};
	}

	/** An immutable copy (the capture keeps writing into this one until frozen). */
	public Snapshot freeze() {
		Snapshot copy = new Snapshot(minX, minZ, size, steleY);
		System.arraycopy(ground, 0, copy.ground, 0, ground.length);
		System.arraycopy(top, 0, copy.top, 0, top.length);
		System.arraycopy(water, 0, copy.water, 0, water.length);
		System.arraycopy(flags, 0, copy.flags, 0, flags.length);
		copy.known.or(known);
		copy.obstacles.addAll(obstacles);
		copy.entities.addAll(entities);
		copy.entityKinds.addAll(entityKinds);
		return copy;
	}

	public int half() {
		return (size - 1) / 2;
	}

	// ================================================================== packing (build_site.snap)

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putIntArray("box", new int[]{minX, minZ, size, steleY});
		int n = size * size;
		int[] packed = new int[n];
		for (int i = 0; i < n; i++) {
			int g = known.get(i) ? (ground[i] + 2048) & 0xFFF : 0xFFF;
			int t = Math.max(0, Math.min(255, top[i] - ground[i]));
			int w = Math.max(0, Math.min(127, water[i]));
			packed[i] = g | t << 12 | w << 20 | (flags[i] & 0x1F) << 27;
		}
		tag.putIntArray("cols", packed);
		return tag;
	}

	public static Snapshot fromTag(CompoundTag tag) {
		int[] box = tag.getIntArray("box").orElse(new int[]{0, 0, 1, 0});
		if (box.length < 4) {
			box = new int[]{0, 0, 1, 0};
		}
		Snapshot snapshot = new Snapshot(box[0], box[1], Math.max(1, box[2]), box[3]);
		int[] packed = tag.getIntArray("cols").orElse(new int[0]);
		for (int i = 0; i < Math.min(packed.length, snapshot.ground.length); i++) {
			int g = packed[i] & 0xFFF;
			if (g == 0xFFF) {
				continue;
			}
			snapshot.ground[i] = (short) (g - 2048);
			snapshot.top[i] = (short) (snapshot.ground[i] + (packed[i] >>> 12 & 0xFF));
			snapshot.water[i] = (byte) (packed[i] >>> 20 & 0x7F);
			snapshot.flags[i] = (byte) (packed[i] >>> 27 & 0x1F);
			snapshot.known.set(i);
		}
		return snapshot;
	}
}
