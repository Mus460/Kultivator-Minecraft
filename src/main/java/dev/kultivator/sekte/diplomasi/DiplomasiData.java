package dev.kultivator.sekte.diplomasi;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
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
 * The saved state of module D (DESIGN 36.2.9), {@code kultivator:sekte_diplomasi} in the overworld's data storage: the
 * NPC sects of the world (founded once from the seeded {@link GerbangPlan} with the plan's count and distance kept, so a
 * later config change never moves the gates of an existing world), each hall's {@link Relasi} with each sect, each
 * hall's own small state (the day its events were last rolled, open envoy offers, the sect behind its next raid) and
 * the world news of the NPC sects (growth, feuds).
 */
public final class DiplomasiData extends SavedData {

	public static final int NEWS = 12;

	public static final Codec<DiplomasiData> CODEC = CompoundTag.CODEC.xmap(DiplomasiData::fromTag, DiplomasiData::toTag);
	public static final SavedDataType<DiplomasiData> TYPE = new SavedDataType<>(Kultivator.id("sekte_diplomasi"), DiplomasiData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	final List<SekteNpc> sects = new ArrayList<>();
	/** owner → sect id → relation. */
	final Map<UUID, Map<String, Relasi>> relations = new LinkedHashMap<>();
	/** owner → the hall's own state ({@code hari}, {@code tawaran}, {@code penyerbu}, ...). */
	final Map<UUID, CompoundTag> halls = new LinkedHashMap<>();
	final List<CompoundTag> news = new ArrayList<>();
	long planSeed;
	int planCount;
	int planMinDist;
	boolean founded;

	public DiplomasiData() {
	}

	public static DiplomasiData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	// ================================================================== access

	public List<SekteNpc> sects() {
		return sects;
	}

	public @Nullable SekteNpc sect(@Nullable String id) {
		if (id == null) {
			return null;
		}
		for (SekteNpc s : sects) {
			if (s.id.equals(id)) {
				return s;
			}
		}
		return null;
	}

	public @Nullable SekteNpc sect(int index) {
		return index >= 0 && index < sects.size() ? sects.get(index) : null;
	}

	/** The relation of {@code owner} with {@code sect}, created at the sect's alignment default. */
	public Relasi relasi(UUID owner, SekteNpc sect) {
		Map<String, Relasi> map = relations.computeIfAbsent(owner, key -> new LinkedHashMap<>());
		Relasi r = map.get(sect.id);
		if (r == null) {
			// A hall whose founder became the Leluhur Pendiri starts every new acquaintance 10 higher (DESIGN 36.2.9).
			r = new Relasi(Standing.awal(sect) + (hall(owner).getBooleanOr("leluhur", false) ? Standing.LELUHUR : 0));
			map.put(sect.id, r);
			setDirty();
		}
		return r;
	}

	/** The relation if it exists (read-only callers), else null. */
	public @Nullable Relasi peek(UUID owner, String sectId) {
		Map<String, Relasi> map = relations.get(owner);
		return map == null ? null : map.get(sectId);
	}

	public CompoundTag hall(UUID owner) {
		return halls.computeIfAbsent(owner, key -> new CompoundTag());
	}

	/**
	 * True the first time today that {@code module} changes the standing of {@code owner} with {@code sectId}: module T
	 * and X may both call {@code DiplomasiService.adjust} and fire their bus event for the same moment, and it counts
	 * once.
	 */
	boolean once(UUID owner, String sectId, String module, long day) {
		CompoundTag h = hall(owner);
		String key = "sekali_" + module + "_" + sectId;
		if (h.getLongOr(key, -1L) == day) {
			return false;
		}
		h.putLong(key, day);
		setDirty();
		return true;
	}

	public java.util.Set<UUID> owners() {
		return relations.keySet();
	}

	void addNews(long day, Component line) {
		CompoundTag t = new CompoundTag();
		t.putLong("day", day);
		t.store("text", ComponentSerialization.CODEC, line);
		news.add(t);
		while (news.size() > NEWS) {
			news.remove(0);
		}
		setDirty();
	}

	// ================================================================== save / load

	private static DiplomasiData fromTag(CompoundTag tag) {
		DiplomasiData data = new DiplomasiData();
		data.founded = tag.getBooleanOr("founded", false);
		data.planSeed = tag.getLongOr("plan_seed", 0L);
		data.planCount = tag.getIntOr("plan_count", 0);
		data.planMinDist = tag.getIntOr("plan_min", 0);
		ListTag sects = tag.getListOrEmpty("sects");
		for (int i = 0; i < sects.size(); i++) {
			data.sects.add(SekteNpc.load(sects.getCompoundOrEmpty(i)));
		}
		CompoundTag rel = tag.getCompoundOrEmpty("relations");
		for (String key : rel.keySet()) {
			UUID owner;
			try {
				owner = UUID.fromString(key);
			} catch (IllegalArgumentException exception) {
				continue;
			}
			CompoundTag per = rel.getCompoundOrEmpty(key);
			Map<String, Relasi> map = new LinkedHashMap<>();
			for (String sect : per.keySet()) {
				map.put(sect, Relasi.load(per.getCompoundOrEmpty(sect)));
			}
			data.relations.put(owner, map);
		}
		CompoundTag halls = tag.getCompoundOrEmpty("halls");
		for (String key : halls.keySet()) {
			try {
				data.halls.put(UUID.fromString(key), halls.getCompoundOrEmpty(key).copy());
			} catch (IllegalArgumentException ignored) {
				// a broken key is dropped
			}
		}
		ListTag news = tag.getListOrEmpty("news");
		for (int i = 0; i < news.size(); i++) {
			data.news.add(news.getCompoundOrEmpty(i));
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("founded", founded);
		tag.putLong("plan_seed", planSeed);
		tag.putInt("plan_count", planCount);
		tag.putInt("plan_min", planMinDist);
		ListTag sectList = new ListTag();
		for (SekteNpc s : sects) {
			sectList.add(s.save());
		}
		tag.put("sects", sectList);
		CompoundTag rel = new CompoundTag();
		for (Map.Entry<UUID, Map<String, Relasi>> entry : relations.entrySet()) {
			CompoundTag per = new CompoundTag();
			for (Map.Entry<String, Relasi> r : entry.getValue().entrySet()) {
				per.put(r.getKey(), r.getValue().save());
			}
			rel.put(entry.getKey().toString(), per);
		}
		tag.put("relations", rel);
		CompoundTag hallTag = new CompoundTag();
		for (Map.Entry<UUID, CompoundTag> entry : halls.entrySet()) {
			hallTag.put(entry.getKey().toString(), entry.getValue().copy());
		}
		tag.put("halls", hallTag);
		ListTag newsList = new ListTag();
		newsList.addAll(news);
		tag.put("news", newsList);
		return tag;
	}
}
