package dev.kultivator.sekte.seragam;

import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.SekteIds;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Module S's storage in the owner's {@code HallData} section {@code sekte_seragam} (DESIGN 36.2.8): the design
 * ({@link SeragamData}), the version that every change bumps (clients refresh their textures by it), the day of the last
 * change with the changes made that day (the free-change allowance), the heir's soul (module E's {@code HEIR_NAMED}) and
 * the achievement marks. Server thread only. A parsed copy per owner is cached; every write replaces it.
 */
public final class LambangData {

	private static final String SECTION = SekteIds.hallSection(SekteIds.S);
	private static final Map<UUID, SeragamData> CACHE = new HashMap<>();

	/** The owner's design ({@link SeragamData#DEFAULT} without a hall or before the first save). */
	public static SeragamData get(@Nullable UUID owner) {
		if (owner == null) {
			return SeragamData.DEFAULT;
		}
		SeragamData cached = CACHE.get(owner);
		if (cached != null) {
			return cached;
		}
		CompoundTag tag = Halls.section(owner, SECTION);
		SeragamData data = tag.contains("lambang") ? new SeragamData(tag.getIntOr("lambang", SeragamData.DEFAULT.lambang()), tag.getStringOr("motto", ""),
				tag.getByteArray("robe").orElse(null), tag.getByteArray("trim").orElse(null), tag.getByteArray("cut").orElse(null),
				tag.getIntOr("gen", 1), tag.getIntOr("flags", 0)) : SeragamData.DEFAULT.withGeneration(tag.getIntOr("gen", 1));
		CACHE.put(owner, data);
		return data;
	}

	/** Saves {@code data} (needs a hall: without one nothing is kept) and bumps the version. True when saved. */
	public static boolean put(UUID owner, SeragamData data) {
		if (owner == null || data == null || Halls.hallOf(owner) == null) {
			return false;
		}
		CompoundTag tag = Halls.section(owner, SECTION);
		tag.putInt("lambang", data.lambang());
		tag.putString("motto", data.motto());
		tag.putByteArray("robe", data.robe());
		tag.putByteArray("trim", data.trim());
		tag.putByteArray("cut", data.cut());
		tag.putInt("gen", data.generation());
		tag.putInt("flags", data.flags());
		tag.putInt("ver", tag.getIntOr("ver", 0) + 1);
		Halls.setDirty();
		CACHE.put(owner, data);
		return true;
	}

	/** The design version (0 before any save); the client keys its textures by it. */
	public static int version(UUID owner) {
		return owner == null ? 0 : Halls.section(owner, SECTION).getIntOr("ver", 0);
	}

	/** Changes made on {@code day} (the world day of {@code sekte.Ingatan.today}). */
	public static int changesOn(UUID owner, long day) {
		CompoundTag tag = Halls.section(owner, SECTION);
		return tag.getLongOr("day", -1L) == day ? tag.getIntOr("used", 0) : 0;
	}

	/** Counts one change on {@code day}. */
	public static void countChange(UUID owner, long day) {
		CompoundTag tag = Halls.section(owner, SECTION);
		int used = tag.getLongOr("day", -1L) == day ? tag.getIntOr("used", 0) : 0;
		tag.putLong("day", day);
		tag.putInt("used", used + 1);
		Halls.setDirty();
	}

	public static @Nullable UUID heir(UUID owner) {
		String text = Halls.section(owner, SECTION).getStringOr("heir", "");
		try {
			return text.isEmpty() ? null : UUID.fromString(text);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	public static void setHeir(UUID owner, @Nullable UUID soul) {
		CompoundTag tag = Halls.section(owner, SECTION);
		tag.putString("heir", soul == null ? "" : soul.toString());
		Halls.setDirty();
	}

	/** A one-time mark ({@code "adv_bendera"} ...): true the first time it is set. */
	public static boolean markOnce(UUID owner, String key) {
		CompoundTag tag = Halls.section(owner, SECTION);
		if (tag.getBooleanOr(key, false)) {
			return false;
		}
		tag.putBoolean(key, true);
		Halls.setDirty();
		return true;
	}

	/** Forgets the parsed copies (server stop, reset). */
	static void clearCache() {
		CACHE.clear();
	}

	static void forget(UUID owner) {
		CACHE.remove(owner);
	}

	private LambangData() {
	}
}
