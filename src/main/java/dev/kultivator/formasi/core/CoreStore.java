package dev.kultivator.formasi.core;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The sect formation cores of one dimension ({@code kultivator:formasi_cores}, DESIGN 24.12), with the target specs of
 * eyes that are not registered yet. Ids are never reused. Virtual test cores live here too (negative ids) but are never
 * written. The dimension key is set when the store is fetched through {@link #get}.
 */
public final class CoreStore extends SavedData {

	public static final Codec<CoreStore> CODEC = CompoundTag.CODEC.xmap(CoreStore::load, CoreStore::save);

	public static final SavedDataType<CoreStore> TYPE = new SavedDataType<>(Kultivator.id("formasi_cores"), CoreStore::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<Integer, CoreRecord> cores = new LinkedHashMap<>();
	private final Map<Long, String> targets = new HashMap<>();
	private final FormationIndex index = new FormationIndex();
	private CompoundTag pending = new CompoundTag();
	private @Nullable ResourceKey<Level> dim;
	private int nextId = 1;
	private int nextVirtual = -1;

	public CoreStore() {
	}

	/** The store of {@code level} (created empty when missing). */
	public static CoreStore get(ServerLevel level) {
		CoreStore store = level.getDataStorage().computeIfAbsent(TYPE);
		store.bind(level.dimension());
		return store;
	}

	/** Loads the saved records once the dimension is known. */
	private void bind(ResourceKey<Level> key) {
		if (dim != null) {
			return;
		}
		dim = key;
		for (Tag each : pending.getListOrEmpty("cores")) {
			if (each instanceof CompoundTag tag) {
				CoreRecord record = CoreRecord.load(key, tag);
				if (record != null && !cores.containsKey(record.id)) {
					cores.put(record.id, record);
				}
			}
		}
		pending = new CompoundTag();
		index.rebuild(cores.values());
	}

	/** The next id this store would hand out (see {@code CoreTicker.allocateId}). */
	int nextId() {
		return nextId;
	}

	public @Nullable ResourceKey<Level> dim() {
		return dim;
	}

	public Collection<CoreRecord> all() {
		return Collections.unmodifiableCollection(cores.values());
	}

	/** A copy of the records (safe while removing). */
	public List<CoreRecord> list() {
		return new ArrayList<>(cores.values());
	}

	public boolean isEmpty() {
		return cores.isEmpty();
	}

	public int realCount() {
		int count = 0;
		for (CoreRecord record : cores.values()) {
			if (!record.virtual) {
				count++;
			}
		}
		return count;
	}

	public @Nullable CoreRecord byId(int id) {
		return cores.get(id);
	}

	public @Nullable CoreRecord atEye(BlockPos eye) {
		int id = index.eyeAt(eye);
		return id <= 0 ? null : cores.get(id);
	}

	public FormationIndex index() {
		return index;
	}

	/** Registers a new real core and indexes it. */
	public CoreRecord create(String spec, BlockPos eye, int rot, java.util.UUID owner, String ownerName, int kind) {
		int id = CoreTicker.allocateId(this);
		while (cores.containsKey(id)) {
			id++;
		}
		nextId = id + 1;
		CoreRecord record = new CoreRecord(id, dim, spec, eye, rot, owner, ownerName, kind, false);
		cores.put(record.id, record);
		index.add(record);
		setDirty();
		return record;
	}

	/** Adds a virtual test core (never saved, never indexed). */
	public CoreRecord createVirtual(String spec, BlockPos eye, java.util.UUID owner, String ownerName) {
		CoreRecord record = new CoreRecord(nextVirtual--, dim, spec, eye, 0, owner, ownerName, 1, true);
		cores.put(record.id, record);
		return record;
	}

	/** Removes a core and re-indexes. */
	public @Nullable CoreRecord remove(int id) {
		CoreRecord removed = cores.remove(id);
		if (removed != null) {
			if (!removed.virtual) {
				index.rebuild(cores.values());
				setDirty();
			}
		}
		return removed;
	}

	/** Re-indexes after a record changed its spec or rotation. */
	public void reindex() {
		index.rebuild(cores.values());
		setDirty();
	}

	/** The target spec of an unregistered eye ({@code formasi tuju}), or null. */
	public @Nullable String target(BlockPos eye) {
		return targets.get(eye.asLong());
	}

	public void setTarget(BlockPos eye, @Nullable String spec) {
		if (spec == null) {
			targets.remove(eye.asLong());
		} else {
			targets.put(eye.asLong(), spec);
		}
		setDirty();
	}

	private static CoreStore load(CompoundTag tag) {
		CoreStore store = new CoreStore();
		store.nextId = Math.max(1, tag.getIntOr("next_id", 1));
		store.pending = tag.copy();
		CompoundTag saved = tag.getCompoundOrEmpty("targets");
		for (String key : saved.keySet()) {
			try {
				store.targets.put(Long.parseLong(key), saved.getStringOr(key, ""));
			} catch (NumberFormatException exception) {
				// skip a broken entry
			}
		}
		for (Tag each : tag.getListOrEmpty("cores")) {
			if (each instanceof CompoundTag core) {
				store.nextId = Math.max(store.nextId, core.getIntOr("id", 0) + 1);
			}
		}
		return store;
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("v", 1);
		tag.putInt("next_id", nextId);
		ListTag list = new ListTag();
		for (CoreRecord record : cores.values()) {
			if (!record.virtual) {
				list.add(record.save());
			}
		}
		if (dim == null) {
			// Never bound (not fetched this session): keep what was loaded.
			for (Tag each : pending.getListOrEmpty("cores")) {
				list.add(each.copy());
			}
		}
		tag.put("cores", list);
		CompoundTag saved = new CompoundTag();
		for (Map.Entry<Long, String> entry : targets.entrySet()) {
			saved.putString(Long.toString(entry.getKey()), entry.getValue());
		}
		tag.put("targets", saved);
		return tag;
	}
}
