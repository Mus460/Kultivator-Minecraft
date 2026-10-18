package dev.kultivator.sect.build.engine;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The undo log of a test site or a showcase (DESIGN 21.20): one SavedData {@code kultivator:build_undo_<id>} per site
 * with the original state of every position the builder changed (recorded once, before the first change: packed
 * positions, a state palette and indices). {@code uji sekte reset} and {@code pamer hapus} put the world back exactly from
 * it (through {@link BuildPlacer#restore}). Server thread only.
 */
public final class Undo extends SavedData {

	private static final Map<String, SavedDataType<Undo>> TYPES = new HashMap<>();

	/** Original states by packed position, in first-change order. */
	final Long2ObjectLinkedOpenHashMap<BlockState> originals = new Long2ObjectLinkedOpenHashMap<>();
	private CompoundTag pending;

	public Undo() {
	}

	private static SavedDataType<Undo> type(String id) {
		return TYPES.computeIfAbsent(id, key -> {
			Codec<Undo> codec = CompoundTag.CODEC.xmap(Undo::fromTag, Undo::toTag);
			return new SavedDataType<>(Kultivator.id("build_undo_" + key), Undo::new, codec, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
		});
	}

	/** The undo log of a site key (a test owner or a showcase id). */
	public static Undo get(MinecraftServer server, UUID key) {
		Undo undo = server.overworld().getDataStorage().computeIfAbsent(type(key.toString().replace("-", "")));
		undo.resolve(server);
		return undo;
	}

	/** Records the original state of {@code pos} unless already recorded. */
	public void record(BlockPos pos, BlockState original) {
		long key = pos.asLong();
		if (!originals.containsKey(key)) {
			originals.put(key, original);
			setDirty();
		}
	}

	public int size() {
		return originals.size();
	}

	/** Every recorded position (copy, first-change order). */
	public List<Long> positions() {
		List<Long> list = new ArrayList<>(originals.size());
		for (Long2ObjectMap.Entry<BlockState> entry : originals.long2ObjectEntrySet()) {
			list.add(entry.getLongKey());
		}
		return list;
	}

	public BlockState original(long pos) {
		return originals.get(pos);
	}

	public void forget(long pos) {
		if (originals.remove(pos) != null) {
			setDirty();
		}
	}

	public void clear() {
		originals.clear();
		setDirty();
	}

	/** Palette states need the block registry lookup: decoded lazily on first use. */
	private void resolve(MinecraftServer server) {
		if (pending == null) {
			return;
		}
		CompoundTag tag = pending;
		pending = null;
		long[] positions = tag.getLongArray("pos").orElse(new long[0]);
		int[] indices = tag.getIntArray("idx").orElse(new int[0]);
		List<BlockState> palette = new ArrayList<>();
		for (Tag each : tag.getListOrEmpty("palette")) {
			if (each instanceof CompoundTag state) {
				palette.add(NbtUtils.readBlockState(server.registryAccess().lookupOrThrow(Registries.BLOCK), state));
			}
		}
		for (int i = 0; i < Math.min(positions.length, indices.length); i++) {
			int index = indices[i];
			if (index >= 0 && index < palette.size()) {
				originals.put(positions[i], palette.get(index));
			}
		}
	}

	private static Undo fromTag(CompoundTag tag) {
		Undo undo = new Undo();
		undo.pending = tag.copy();
		return undo;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		if (pending != null) {
			return pending.copy();
		}
		long[] positions = new long[originals.size()];
		int[] indices = new int[originals.size()];
		Map<BlockState, Integer> palette = new HashMap<>();
		ListTag paletteTag = new ListTag();
		int i = 0;
		for (Long2ObjectMap.Entry<BlockState> entry : originals.long2ObjectEntrySet()) {
			positions[i] = entry.getLongKey();
			Integer index = palette.get(entry.getValue());
			if (index == null) {
				index = palette.size();
				palette.put(entry.getValue(), index);
				paletteTag.add(NbtUtils.writeBlockState(entry.getValue()));
			}
			indices[i] = index;
			i++;
		}
		tag.putLongArray("pos", positions);
		tag.putIntArray("idx", indices);
		tag.put("palette", paletteTag);
		return tag;
	}
}
