package dev.kultivator.formation;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The registered protective formations of one dimension, keyed by their lodestone position. */
public final class FormationData extends SavedData {

	public static final Codec<FormationData> CODEC = Formation.CODEC.listOf().optionalFieldOf("formations", List.of()).codec()
			.xmap(FormationData::fromList, FormationData::toList);

	public static final SavedDataType<FormationData> TYPE = new SavedDataType<>(Kultivator.id("formations"), FormationData::new,
			CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<BlockPos, Formation> formations = new LinkedHashMap<>();

	public FormationData() {
	}

	public static FormationData get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	private static FormationData fromList(List<Formation> list) {
		FormationData data = new FormationData();
		for (Formation formation : list) {
			data.formations.put(formation.pos(), formation);
		}
		return data;
	}

	private List<Formation> toList() {
		return new ArrayList<>(formations.values());
	}

	public @Nullable Formation get(BlockPos lodestone) {
		return formations.get(lodestone);
	}

	public void put(Formation formation) {
		formations.put(formation.pos(), formation);
		setDirty();
	}

	public @Nullable Formation remove(BlockPos lodestone) {
		Formation removed = formations.remove(lodestone);
		if (removed != null) {
			setDirty();
		}
		return removed;
	}

	/** Read-only view; copy it before removing entries while iterating. */
	public Collection<Formation> all() {
		return Collections.unmodifiableCollection(formations.values());
	}

	public int size() {
		return formations.size();
	}

	public boolean isEmpty() {
		return formations.isEmpty();
	}

	public void clear() {
		if (!formations.isEmpty()) {
			formations.clear();
			setDirty();
		}
	}
}
