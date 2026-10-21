package dev.kultivator.dunia.peta;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SavedData {@code kultivator:peta} in the overworld (DESIGN 34.5.16): the coarse biome grid of every dimension (one byte
 * per {@code cellSize} cell, in regions of 32 × 32 cells), and per player the fog of war (explored cells, one bit each,
 * per dimension and region), the waypoints, the halls they have seen and, while they walk as a soul, where their body
 * lies. Kept here and not in the synced cultivation data, so a big explored map never rides on every sync.
 */
public final class PetaData extends SavedData {

	public static final int REGION = 32;
	public static final int CELLS = REGION * REGION;

	public static final Codec<PetaData> CODEC = CompoundTag.CODEC.xmap(PetaData::fromTag, PetaData::toTag);
	public static final SavedDataType<PetaData> TYPE = new SavedDataType<>(Kultivator.id("peta"), PetaData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** One player's map. */
	static final class Player {
		final Map<String, Long2ObjectMap<long[]>> fog = new HashMap<>();
		ListTag waypoints = new ListTag();
		final java.util.Set<UUID> seenHalls = new java.util.LinkedHashSet<>();
		@Nullable CompoundTag body;
	}

	int cellSize = 64;
	final Map<String, Long2ObjectMap<byte[]>> cells = new HashMap<>();
	final Map<UUID, Player> players = new LinkedHashMap<>();

	public PetaData() {
	}

	static PetaData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	// ================================================================== keys

	static long regionKey(int rx, int rz) {
		return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
	}

	static int regionX(long key) {
		return (int) (key >> 32);
	}

	static int regionZ(long key) {
		return (int) key;
	}

	static int index(int cx, int cz) {
		return Math.floorMod(cz, REGION) * REGION + Math.floorMod(cx, REGION);
	}

	// ================================================================== cells

	/** The cell byte (0 = not sampled). */
	byte cell(String dim, int cx, int cz) {
		Long2ObjectMap<byte[]> map = cells.get(dim);
		if (map == null) {
			return 0;
		}
		byte[] region = map.get(regionKey(Math.floorDiv(cx, REGION), Math.floorDiv(cz, REGION)));
		return region == null ? 0 : region[index(cx, cz)];
	}

	void setCell(String dim, int cx, int cz, byte value) {
		byte[] region = cells.computeIfAbsent(dim, d -> new Long2ObjectOpenHashMap<>())
				.computeIfAbsent(regionKey(Math.floorDiv(cx, REGION), Math.floorDiv(cz, REGION)), k -> new byte[CELLS]);
		region[index(cx, cz)] = value;
		setDirty();
	}

	@Nullable byte[] region(String dim, long key) {
		Long2ObjectMap<byte[]> map = cells.get(dim);
		return map == null ? null : map.get(key);
	}

	/** Changes the cell size: every sampled cell is forgotten (and every fog, which is counted in cells). */
	void resetCells(int size) {
		cellSize = size;
		cells.clear();
		for (Player player : players.values()) {
			player.fog.clear();
		}
		setDirty();
	}

	// ================================================================== players

	Player player(UUID id) {
		return players.computeIfAbsent(id, key -> new Player());
	}

	@Nullable Player peek(UUID id) {
		return players.get(id);
	}

	/** True when the player explored the cell; marks it when {@code mark}. Returns whether it was new. */
	boolean explore(UUID id, String dim, int cx, int cz) {
		long[] bits = player(id).fog.computeIfAbsent(dim, d -> new Long2ObjectOpenHashMap<>())
				.computeIfAbsent(regionKey(Math.floorDiv(cx, REGION), Math.floorDiv(cz, REGION)), k -> new long[CELLS / 64]);
		int i = index(cx, cz);
		long mask = 1L << (i & 63);
		if ((bits[i >> 6] & mask) != 0L) {
			return false;
		}
		bits[i >> 6] |= mask;
		setDirty();
		return true;
	}

	boolean explored(UUID id, String dim, int cx, int cz) {
		Player player = players.get(id);
		if (player == null) {
			return false;
		}
		Long2ObjectMap<long[]> map = player.fog.get(dim);
		long[] bits = map == null ? null : map.get(regionKey(Math.floorDiv(cx, REGION), Math.floorDiv(cz, REGION)));
		if (bits == null) {
			return false;
		}
		int i = index(cx, cz);
		return (bits[i >> 6] & (1L << (i & 63))) != 0L;
	}

	/** The player's fog bits of one region (null: nothing explored there). */
	@Nullable long[] fog(UUID id, String dim, long key) {
		Player player = players.get(id);
		Long2ObjectMap<long[]> map = player == null ? null : player.fog.get(dim);
		return map == null ? null : map.get(key);
	}

	/** How many cells the player explored in {@code dim}. */
	int exploredCount(UUID id, String dim) {
		Player player = players.get(id);
		Long2ObjectMap<long[]> map = player == null ? null : player.fog.get(dim);
		if (map == null) {
			return 0;
		}
		int n = 0;
		for (long[] bits : map.values()) {
			for (long word : bits) {
				n += Long.bitCount(word);
			}
		}
		return n;
	}

	void forget(UUID id) {
		if (players.remove(id) != null) {
			setDirty();
		}
	}

	// ================================================================== save

	private static PetaData fromTag(CompoundTag tag) {
		PetaData data = new PetaData();
		data.cellSize = tag.getIntOr("cell_size", 64);
		CompoundTag dims = tag.getCompoundOrEmpty("cells");
		for (String dim : dims.keySet()) {
			Long2ObjectMap<byte[]> map = new Long2ObjectOpenHashMap<>();
			CompoundTag regions = dims.getCompoundOrEmpty(dim);
			for (String key : regions.keySet()) {
				byte[] bytes = regions.getByteArray(key).orElse(null);
				if (bytes != null && bytes.length == CELLS) {
					try {
						map.put(Long.parseLong(key), bytes.clone());
					} catch (NumberFormatException ignored) {
						// a broken key: that region is sampled again
					}
				}
			}
			data.cells.put(dim, map);
		}
		for (Tag each : tag.getListOrEmpty("players")) {
			if (!(each instanceof CompoundTag entry)) {
				continue;
			}
			UUID id = entry.read("id", UUIDUtil.CODEC).orElse(null);
			if (id == null) {
				continue;
			}
			Player player = new Player();
			CompoundTag fog = entry.getCompoundOrEmpty("fog");
			for (String dim : fog.keySet()) {
				Long2ObjectMap<long[]> map = new Long2ObjectOpenHashMap<>();
				CompoundTag regions = fog.getCompoundOrEmpty(dim);
				for (String key : regions.keySet()) {
					long[] bits = regions.getLongArray(key).orElse(null);
					if (bits != null && bits.length == CELLS / 64) {
						try {
							map.put(Long.parseLong(key), bits.clone());
						} catch (NumberFormatException ignored) {
							// a broken key
						}
					}
				}
				player.fog.put(dim, map);
			}
			player.waypoints = entry.getListOrEmpty("waypoints").copy();
			for (Tag hall : entry.getListOrEmpty("halls")) {
				if (hall instanceof CompoundTag h) {
					h.read("id", UUIDUtil.CODEC).ifPresent(player.seenHalls::add);
				}
			}
			CompoundTag body = entry.getCompoundOrEmpty("body");
			player.body = body.isEmpty() ? null : body.copy();
			data.players.put(id, player);
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("cell_size", cellSize);
		CompoundTag dims = new CompoundTag();
		cells.forEach((dim, map) -> {
			CompoundTag regions = new CompoundTag();
			for (Long2ObjectMap.Entry<byte[]> e : map.long2ObjectEntrySet()) {
				regions.putByteArray(Long.toString(e.getLongKey()), e.getValue());
			}
			dims.put(dim, regions);
		});
		tag.put("cells", dims);
		ListTag list = new ListTag();
		players.forEach((id, player) -> {
			CompoundTag entry = new CompoundTag();
			entry.store("id", UUIDUtil.CODEC, id);
			CompoundTag fog = new CompoundTag();
			player.fog.forEach((dim, map) -> {
				CompoundTag regions = new CompoundTag();
				for (Long2ObjectMap.Entry<long[]> e : map.long2ObjectEntrySet()) {
					regions.putLongArray(Long.toString(e.getLongKey()), e.getValue());
				}
				fog.put(dim, regions);
			});
			entry.put("fog", fog);
			entry.put("waypoints", player.waypoints.copy());
			ListTag halls = new ListTag();
			for (UUID hall : player.seenHalls) {
				CompoundTag h = new CompoundTag();
				h.store("id", UUIDUtil.CODEC, hall);
				halls.add(h);
			}
			entry.put("halls", halls);
			if (player.body != null) {
				entry.put("body", player.body.copy());
			}
			list.add(entry);
		});
		tag.put("players", list);
		return tag;
	}
}
