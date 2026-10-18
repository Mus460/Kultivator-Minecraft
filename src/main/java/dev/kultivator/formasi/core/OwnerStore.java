package dev.kultivator.formasi.core;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-owner formation data in the overworld ({@code kultivator:formasi_owners}, DESIGN 24.12): the Kas pools in
 * centi-Daya, the stone pool's best grade, the last 20 log lines and the settings.
 */
public final class OwnerStore extends SavedData {

	public static final int LOG_SIZE = 20;

	public static final Codec<OwnerStore> CODEC = CompoundTag.CODEC.xmap(OwnerStore::load, OwnerStore::save);

	public static final SavedDataType<OwnerStore> TYPE = new SavedDataType<>(Kultivator.id("formasi_owners"), OwnerStore::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** One owner's entry; change it, then call {@link #setDirty()}. */
	public static final class Entry {
		public long kasA;
		public long kasS;
		public int kasBest = -1;
		public boolean drawDefault = true;
		public final ListTag log = new ListTag();
	}

	private final Map<UUID, Entry> owners = new LinkedHashMap<>();

	public OwnerStore() {
	}

	public static OwnerStore get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public Entry entry(UUID owner) {
		return owners.computeIfAbsent(owner, id -> new Entry());
	}

	public boolean has(UUID owner) {
		return owners.containsKey(owner);
	}

	/** Adds a log line {@code {d, k, args}}; the last {@link #LOG_SIZE} are kept. */
	public void log(UUID owner, long day, String key, String... args) {
		Entry entry = entry(owner);
		CompoundTag line = new CompoundTag();
		line.putLong("d", day);
		line.putString("k", key);
		ListTag list = new ListTag();
		for (String arg : args) {
			list.add(StringTag.valueOf(arg == null ? "" : arg));
		}
		line.put("args", list);
		entry.log.add(line);
		while (entry.log.size() > LOG_SIZE) {
			entry.log.remove(0);
		}
		setDirty();
	}

	private static OwnerStore load(CompoundTag tag) {
		OwnerStore store = new OwnerStore();
		CompoundTag all = tag.getCompoundOrEmpty("owners");
		for (String key : all.keySet()) {
			UUID owner;
			try {
				owner = UUID.fromString(key);
			} catch (IllegalArgumentException exception) {
				continue;
			}
			CompoundTag in = all.getCompoundOrEmpty(key);
			Entry entry = new Entry();
			entry.kasA = Math.max(0L, in.getLongOr("kas_a", 0L));
			entry.kasS = Math.max(0L, in.getLongOr("kas_s", 0L));
			entry.kasBest = in.getIntOr("kas_best", -1);
			entry.drawDefault = in.getCompoundOrEmpty("settings").getBooleanOr("draw_default", true);
			for (Tag line : in.getListOrEmpty("log")) {
				if (line instanceof CompoundTag compound) {
					entry.log.add(compound.copy());
				}
			}
			store.owners.put(owner, entry);
		}
		return store;
	}

	private CompoundTag save() {
		CompoundTag all = new CompoundTag();
		for (Map.Entry<UUID, Entry> each : owners.entrySet()) {
			Entry entry = each.getValue();
			CompoundTag out = new CompoundTag();
			out.putLong("kas_a", entry.kasA);
			out.putLong("kas_s", entry.kasS);
			out.putInt("kas_best", entry.kasBest);
			CompoundTag settings = new CompoundTag();
			settings.putBoolean("draw_default", entry.drawDefault);
			out.put("settings", settings);
			out.put("log", entry.log.copy());
			all.put(each.getKey().toString(), out);
		}
		CompoundTag tag = new CompoundTag();
		tag.putInt("v", 1);
		tag.put("owners", all);
		return tag;
	}
}
