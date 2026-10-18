package dev.kultivator.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * "Is there a portal near this position?" without a block-volume scan (DESIGN 22.3.5 rule 3): nether portals through the
 * POI manager ({@code PoiTypes.NETHER_PORTAL}), end portals and gateways through the block entities of loaded chunks.
 * Answers are cached per chunk in a {@link Cache} that lives for one cast. A chunk that is not loaded counts as "near a
 * portal" (nothing is moved next to the unknown), and nothing is ever loaded here.
 */
public final class PortalIndex {

	/** Nothing is moved within this many blocks of a portal or gateway. */
	public static final int MOVE_RADIUS = 8;

	/** True when a nether portal, end portal or end gateway is within {@code radius} of {@code pos} (uncached). */
	public static boolean near(ServerLevel level, BlockPos pos, int radius) {
		return new Cache().near(level, pos, radius);
	}

	public static Cache cache() {
		return new Cache();
	}

	/** Per-cast cache of portal positions per chunk. */
	public static final class Cache {
		/** Marker for a chunk that is not loaded. */
		private static final List<BlockPos> UNLOADED = List.of(BlockPos.ZERO);

		private final Long2ObjectOpenHashMap<List<BlockPos>> chunks = new Long2ObjectOpenHashMap<>();
		private @Nullable ResourceKey<Level> dimension;

		public boolean near(ServerLevel level, BlockPos pos, int radius) {
			if (dimension != level.dimension()) {
				chunks.clear();
				dimension = level.dimension();
			}
			int r = Math.max(0, radius);
			long radiusSq = (long) r * r;
			int minX = SectionPos.blockToSectionCoord(pos.getX() - r);
			int maxX = SectionPos.blockToSectionCoord(pos.getX() + r);
			int minZ = SectionPos.blockToSectionCoord(pos.getZ() - r);
			int maxZ = SectionPos.blockToSectionCoord(pos.getZ() + r);
			for (int cx = minX; cx <= maxX; cx++) {
				for (int cz = minZ; cz <= maxZ; cz++) {
					List<BlockPos> portals = portals(level, cx, cz);
					if (portals == UNLOADED) {
						return true;
					}
					for (BlockPos portal : portals) {
						if (portal.distSqr(pos) <= radiusSq) {
							return true;
						}
					}
				}
			}
			return false;
		}

		private List<BlockPos> portals(ServerLevel level, int cx, int cz) {
			long key = ChunkPos.pack(cx, cz);
			List<BlockPos> cached = chunks.get(key);
			if (cached != null) {
				return cached;
			}
			LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
			if (chunk == null) {
				chunks.put(key, UNLOADED);
				return UNLOADED;
			}
			List<BlockPos> found = new ArrayList<>();
			level.getPoiManager().getInChunk(holder -> holder.is(PoiTypes.NETHER_PORTAL), new ChunkPos(cx, cz), PoiManager.Occupancy.ANY)
					.forEach(record -> found.add(record.getPos()));
			for (BlockEntity entity : chunk.getBlockEntities().values()) {
				// An end gateway is an end portal block entity too.
				if (entity instanceof TheEndPortalBlockEntity) {
					found.add(entity.getBlockPos());
				}
			}
			chunks.put(key, found);
			return found;
		}
	}

	private PortalIndex() {
	}
}
