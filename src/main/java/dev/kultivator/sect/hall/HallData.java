package dev.kultivator.sect.hall;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every Sect Hall of the world, in the overworld's data storage ({@code kultivator:sect_halls}, DESIGN 20.4.6): per owner
 * one compound with the sections {@code core} (module A), {@code activity} (module B) and {@code kehancuran} (module D).
 * Sections are live tags: change them, then call {@link #setDirty()}.
 */
public final class HallData extends SavedData {

	public static final String CORE = "core";

	public static final Codec<HallData> CODEC = CompoundTag.CODEC.xmap(HallData::fromTag, HallData::toTag);

	public static final SavedDataType<HallData> TYPE = new SavedDataType<>(Kultivator.id("sect_halls"), HallData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Owner -> {core, activity, kehancuran}. */
	private final Map<UUID, CompoundTag> halls = new LinkedHashMap<>();

	public HallData() {
	}

	public static HallData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	private static HallData fromTag(CompoundTag tag) {
		HallData data = new HallData();
		CompoundTag all = tag.getCompoundOrEmpty("halls");
		for (String key : all.keySet()) {
			try {
				UUID owner = UUID.fromString(key);
				data.halls.put(owner, all.getCompoundOrEmpty(key).copy());
			} catch (IllegalArgumentException exception) {
				Kultivator.LOGGER.warn("Skipping a saved Sect Hall with a broken owner id: {}", key);
			}
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag all = new CompoundTag();
		for (Map.Entry<UUID, CompoundTag> entry : halls.entrySet()) {
			all.put(entry.getKey().toString(), entry.getValue().copy());
		}
		CompoundTag tag = new CompoundTag();
		tag.put("halls", all);
		return tag;
	}

	/** The owner's whole hall entry, or null when the owner never founded one. */
	public @Nullable CompoundTag entry(UUID owner) {
		return halls.get(owner);
	}

	/** The owner's {@code core} section, or null without a hall. */
	public @Nullable CompoundTag core(UUID owner) {
		CompoundTag entry = halls.get(owner);
		return entry == null ? null : section(entry, CORE);
	}

	/** Creates the owner's entry (with an empty core) when missing. */
	public CompoundTag create(UUID owner) {
		CompoundTag entry = halls.computeIfAbsent(owner, id -> new CompoundTag());
		section(entry, CORE);
		setDirty();
		return entry;
	}

	/** A live sub-compound of an entry (created when missing). */
	public static CompoundTag section(CompoundTag entry, String key) {
		CompoundTag section = entry.getCompound(key).orElse(null);
		if (section == null) {
			section = new CompoundTag();
			entry.put(key, section);
		}
		return section;
	}

	/** Owners with a hall, in founding order (a copy). */
	public List<UUID> owners() {
		return new ArrayList<>(halls.keySet());
	}

	public boolean isEmpty() {
		return halls.isEmpty();
	}
}
