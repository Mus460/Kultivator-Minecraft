package dev.kultivator.sect.build.engine;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The sect cell index (DESIGN 21.20), SavedData {@code kultivator:sect_cells} in the overworld storage: per dimension and
 * touched chunk section 4096-bit sets (placed by the builder and still its own; removed by a player; and, after a
 * template upgrade, placed by the previous template generation, "legacy") plus the owner (the site's owner, a showcase id
 * or a relocation orphan id). It answers break refunds, spawn-proofing, explosion credit, Kehancuran zones, repair and
 * the "own block" check without regenerating plans: a lookup is a map get and a bit test. Server thread only.
 */
public final class CellIndex extends SavedData {

	public static final Codec<CellIndex> CODEC = CompoundTag.CODEC.xmap(CellIndex::fromTag, CellIndex::toTag);
	public static final SavedDataType<CellIndex> TYPE = new SavedDataType<>(Kultivator.id("sect_cells"), CellIndex::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** One chunk section's bits. */
	static final class Entry {
		UUID owner;
		final long[] placed = new long[64];
		long[] removed;
		/** Cells of the previous template generation (DESIGN 21.18): still own, but any new op may replace them. */
		long[] legacy;

		Entry(UUID owner) {
			this.owner = owner;
		}

		boolean empty() {
			for (long word : placed) {
				if (word != 0L) {
					return false;
				}
			}
			return zero(removed) && zero(legacy);
		}

		private static boolean zero(long[] words) {
			if (words != null) {
				for (long word : words) {
					if (word != 0L) {
						return false;
					}
				}
			}
			return true;
		}
	}

	private final Map<String, Long2ObjectOpenHashMap<Entry>> dims = new HashMap<>();
	private static @Nullable CellIndex cached;
	private static @Nullable MinecraftServer cachedServer;

	public CellIndex() {
	}

	/** The index of the server (cached). */
	public static CellIndex get(MinecraftServer server) {
		CellIndex index = cached;
		if (index != null && cachedServer == server) {
			return index;
		}
		index = server.overworld().getDataStorage().computeIfAbsent(TYPE);
		cached = index;
		cachedServer = server;
		return index;
	}

	/** Drops the cached reference (server stopped). */
	static void forget() {
		cached = null;
		cachedServer = null;
	}

	private static int bit(BlockPos pos) {
		return (pos.getY() & 15) << 8 | (pos.getZ() & 15) << 4 | (pos.getX() & 15);
	}

	private @Nullable Entry entry(Level level, BlockPos pos) {
		Long2ObjectOpenHashMap<Entry> map = dims.get(level.dimension().identifier().toString());
		return map == null ? null : map.get(SectionPos.asLong(pos));
	}

	private Entry entryOrCreate(Level level, BlockPos pos, UUID owner) {
		Long2ObjectOpenHashMap<Entry> map = dims.computeIfAbsent(level.dimension().identifier().toString(), key -> new Long2ObjectOpenHashMap<>());
		long key = SectionPos.asLong(pos);
		Entry entry = map.get(key);
		if (entry == null) {
			entry = new Entry(owner);
			map.put(key, entry);
		}
		return entry;
	}

	/** True when the block at {@code pos} was placed by the builder and is still its own (this or the previous template). */
	public boolean isPlaced(Level level, BlockPos pos) {
		Entry entry = entry(level, pos);
		if (entry == null) {
			return false;
		}
		int bit = bit(pos);
		long mask = 1L << (bit & 63);
		return (entry.placed[bit >>> 6] & mask) != 0L || entry.legacy != null && (entry.legacy[bit >>> 6] & mask) != 0L;
	}

	/** True when the own block at {@code pos} belongs to the previous template generation (an upgrade may replace it). */
	public boolean isLegacy(Level level, BlockPos pos) {
		Entry entry = entry(level, pos);
		if (entry == null || entry.legacy == null) {
			return false;
		}
		int bit = bit(pos);
		return (entry.legacy[bit >>> 6] & 1L << (bit & 63)) != 0L;
	}

	/** True when a player removed the sect block at {@code pos} (repair leaves it alone). */
	public boolean isRemoved(Level level, BlockPos pos) {
		Entry entry = entry(level, pos);
		if (entry == null || entry.removed == null) {
			return false;
		}
		int bit = bit(pos);
		return (entry.removed[bit >>> 6] & 1L << (bit & 63)) != 0L;
	}

	/** The owner of the section holding {@code pos} (a site owner or a showcase id), or null. */
	public @Nullable UUID owner(Level level, BlockPos pos) {
		Entry entry = entry(level, pos);
		return entry == null ? null : entry.owner;
	}

	/** Sets or clears the own bit of {@code pos}; both set and clear drop the legacy bit (the cell is this generation's now). */
	public void setPlaced(Level level, BlockPos pos, UUID owner, boolean placed) {
		if (!placed) {
			Entry entry = entry(level, pos);
			if (entry == null) {
				return;
			}
			int bit = bit(pos);
			entry.placed[bit >>> 6] &= ~(1L << (bit & 63));
			if (entry.legacy != null) {
				entry.legacy[bit >>> 6] &= ~(1L << (bit & 63));
			}
			setDirty();
			return;
		}
		Entry entry = entryOrCreate(level, pos, owner);
		entry.owner = owner;
		int bit = bit(pos);
		entry.placed[bit >>> 6] |= 1L << (bit & 63);
		if (entry.removed != null) {
			entry.removed[bit >>> 6] &= ~(1L << (bit & 63));
		}
		if (entry.legacy != null) {
			entry.legacy[bit >>> 6] &= ~(1L << (bit & 63));
		}
		setDirty();
	}

	/**
	 * A template upgrade (DESIGN 21.18): every own cell of {@code owner} becomes a legacy cell. It keeps counting as the
	 * sect's own (refunds, spawn-proofing), and the new plan may replace or clear it with a refund.
	 */
	public void toLegacy(UUID owner) {
		for (Long2ObjectOpenHashMap<Entry> map : dims.values()) {
			for (Entry entry : map.values()) {
				if (!owner.equals(entry.owner)) {
					continue;
				}
				if (entry.legacy == null) {
					entry.legacy = new long[64];
				}
				for (int i = 0; i < 64; i++) {
					entry.legacy[i] |= entry.placed[i];
					entry.placed[i] = 0L;
				}
				// "Removed by player" guarded the old plan's repair; the new plan may build there.
				entry.removed = null;
			}
		}
		setDirty();
	}

	/** True when {@code owner} still has legacy cells (an upgraded site whose old buildings stand). */
	public boolean hasLegacy(UUID owner) {
		for (Long2ObjectOpenHashMap<Entry> map : dims.values()) {
			for (Entry entry : map.values()) {
				if (owner.equals(entry.owner) && !Entry.zero(entry.legacy)) {
					return true;
				}
			}
		}
		return false;
	}

	public void setRemoved(Level level, BlockPos pos, UUID owner, boolean removed) {
		Entry entry = removed ? entryOrCreate(level, pos, owner) : entry(level, pos);
		if (entry == null) {
			return;
		}
		int bit = bit(pos);
		if (removed) {
			if (entry.removed == null) {
				entry.removed = new long[64];
			}
			entry.removed[bit >>> 6] |= 1L << (bit & 63);
		} else if (entry.removed != null) {
			entry.removed[bit >>> 6] &= ~(1L << (bit & 63));
		}
		setDirty();
	}

	/** Forgets every cell of {@code owner} (a released site: its blocks become ordinary blocks). */
	public int clearOwner(UUID owner) {
		int sections = 0;
		for (Long2ObjectOpenHashMap<Entry> map : dims.values()) {
			var iterator = map.long2ObjectEntrySet().iterator();
			while (iterator.hasNext()) {
				Long2ObjectMap.Entry<Entry> each = iterator.next();
				if (owner.equals(each.getValue().owner)) {
					iterator.remove();
					sections++;
				}
			}
		}
		if (sections > 0) {
			setDirty();
		}
		return sections;
	}

	/** Moves every cell of {@code from} to {@code to} (relocation keeps the refund link). */
	public void reassign(UUID from, UUID to) {
		for (Long2ObjectOpenHashMap<Entry> map : dims.values()) {
			for (Entry entry : map.values()) {
				if (from.equals(entry.owner)) {
					entry.owner = to;
				}
			}
		}
		setDirty();
	}

	/**
	 * Sekte Langit (DESIGN 37.3.6, 37.4.9 item 9): moves the sections of {@code from} whose lowest y is below
	 * {@code belowY} to {@code to} ({@code Integer.MAX_VALUE} for all). The section rule of {@code Validator.sky} (S4) makes
	 * sure no section holds cells of both the ground site and the sky island, so the Ascension splits them cleanly.
	 */
	public int rekey(UUID from, UUID to, int belowY) {
		int moved = 0;
		for (Long2ObjectOpenHashMap<Entry> map : dims.values()) {
			for (Long2ObjectMap.Entry<Entry> each : map.long2ObjectEntrySet()) {
				Entry entry = each.getValue();
				if (!from.equals(entry.owner)) {
					continue;
				}
				int minY = SectionPos.sectionToBlockCoord(SectionPos.y(each.getLongKey()));
				if (minY < belowY) {
					entry.owner = to;
					moved++;
				}
			}
		}
		if (moved > 0) {
			setDirty();
		}
		return moved;
	}

	/** Placed cells of a section count (for stats). */
	public long placedCount(UUID owner) {
		long count = 0L;
		for (Long2ObjectOpenHashMap<Entry> map : dims.values()) {
			for (Entry entry : map.values()) {
				if (owner.equals(entry.owner)) {
					for (long word : entry.placed) {
						count += Long.bitCount(word);
					}
				}
			}
		}
		return count;
	}

	/** True when the level has any index at all (cheap early-out for the spawn mixin). */
	public boolean hasAny(ServerLevel level) {
		Long2ObjectOpenHashMap<Entry> map = dims.get(level.dimension().identifier().toString());
		return map != null && !map.isEmpty();
	}

	private static CellIndex fromTag(CompoundTag tag) {
		CellIndex index = new CellIndex();
		CompoundTag all = tag.getCompoundOrEmpty("dims");
		for (String dim : all.keySet()) {
			Long2ObjectOpenHashMap<Entry> map = new Long2ObjectOpenHashMap<>();
			for (Tag each : all.getListOrEmpty(dim)) {
				if (!(each instanceof CompoundTag section)) {
					continue;
				}
				UUID owner;
				try {
					owner = UUID.fromString(section.getStringOr("o", ""));
				} catch (IllegalArgumentException exception) {
					continue;
				}
				Entry entry = new Entry(owner);
				long[] placed = section.getLongArray("p").orElse(new long[0]);
				System.arraycopy(placed, 0, entry.placed, 0, Math.min(64, placed.length));
				long[] removed = section.getLongArray("r").orElse(null);
				if (removed != null && removed.length > 0) {
					entry.removed = new long[64];
					System.arraycopy(removed, 0, entry.removed, 0, Math.min(64, removed.length));
				}
				long[] legacy = section.getLongArray("l").orElse(null);
				if (legacy != null && legacy.length > 0) {
					entry.legacy = new long[64];
					System.arraycopy(legacy, 0, entry.legacy, 0, Math.min(64, legacy.length));
				}
				map.put(section.getLongOr("s", 0L), entry);
			}
			index.dims.put(dim, map);
		}
		return index;
	}

	private CompoundTag toTag() {
		CompoundTag all = new CompoundTag();
		for (Map.Entry<String, Long2ObjectOpenHashMap<Entry>> dim : dims.entrySet()) {
			ListTag list = new ListTag();
			for (Long2ObjectMap.Entry<Entry> each : dim.getValue().long2ObjectEntrySet()) {
				Entry entry = each.getValue();
				if (entry.empty()) {
					continue;
				}
				CompoundTag section = new CompoundTag();
				section.putLong("s", each.getLongKey());
				section.putString("o", entry.owner.toString());
				section.putLongArray("p", entry.placed.clone());
				if (entry.removed != null) {
					section.putLongArray("r", entry.removed.clone());
				}
				if (entry.legacy != null) {
					section.putLongArray("l", entry.legacy.clone());
				}
				list.add(section);
			}
			all.put(dim.getKey(), list);
		}
		CompoundTag tag = new CompoundTag();
		tag.put("dims", all);
		return tag;
	}
}
