package dev.kultivator.dunia.kota;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SavedData {@code kultivator:kota} in the overworld (DESIGN 34.5.1-34.5.4): per town (keyed by the town's site id) the
 * Nama Baik of every player and the town's own bookkeeping, and per player the module's counters: daily caps (bounties,
 * shop sales, discussions, consignments; reset when the in-game day changes), the tea in effect, rumours bought, the
 * black-market reputation, the wanderer cooldown, the Immortal City's peach, lecture and edict days, badges and the rival
 * leaders beaten. Every number is clamped where it is read.
 */
public final class KotaData extends SavedData {

	public static final Codec<KotaData> CODEC = CompoundTag.CODEC.xmap(KotaData::fromTag, KotaData::toTag);
	public static final SavedDataType<KotaData> TYPE = new SavedDataType<>(Kultivator.id("kota"), KotaData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<Long, CompoundTag> towns = new HashMap<>();
	private final Map<UUID, CompoundTag> players = new HashMap<>();

	public KotaData() {
	}

	public static KotaData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** The town's compound (created on first use). Mark dirty after changing it ({@link #touch()}). */
	public CompoundTag town(long id) {
		return towns.computeIfAbsent(id, key -> new CompoundTag());
	}

	/** Every town with a record (read-only). */
	public java.util.Set<Long> townIds() {
		java.util.Set<Long> ids = new java.util.HashSet<>();
		for (Map.Entry<Long, CompoundTag> entry : towns.entrySet()) {
			if (entry.getValue().contains("dim")) {
				ids.add(entry.getKey());
			}
		}
		return ids;
	}

	/** The player's compound (created on first use). */
	public CompoundTag player(UUID id) {
		return players.computeIfAbsent(id, key -> new CompoundTag());
	}

	public void touch() {
		setDirty();
	}

	// ================================================================== Nama Baik (−100..100 per town)

	public int reputation(long town, UUID player) {
		return Mth.clamp(town(town).getCompoundOrEmpty("rep").getIntOr(player.toString(), 0), -100, 100);
	}

	public int addReputation(long town, UUID player, int delta) {
		CompoundTag rep = town(town).getCompoundOrEmpty("rep");
		int value = Mth.clamp(rep.getIntOr(player.toString(), 0) + delta, -100, 100);
		rep.putInt(player.toString(), value);
		town(town).put("rep", rep);
		setDirty();
		return value;
	}

	public void setReputation(long town, UUID player, int value) {
		CompoundTag rep = town(town).getCompoundOrEmpty("rep");
		rep.putInt(player.toString(), Mth.clamp(value, -100, 100));
		town(town).put("rep", rep);
		setDirty();
	}

	// ================================================================== daily counters

	/** The player's counters of {@code day} (a fresh compound when the day changed). */
	public CompoundTag daily(UUID player, long day) {
		CompoundTag tag = player(player);
		CompoundTag daily = tag.getCompoundOrEmpty("daily");
		if (daily.getLongOr("day", -1L) != day) {
			daily = new CompoundTag();
			daily.putLong("day", day);
			tag.put("daily", daily);
			setDirty();
		}
		return daily;
	}

	public int dailyCount(UUID player, long day, String key) {
		return daily(player, day).getIntOr(key, 0);
	}

	public void addDaily(UUID player, long day, String key, int amount) {
		CompoundTag daily = daily(player, day);
		daily.putInt(key, daily.getIntOr(key, 0) + amount);
		setDirty();
	}

	// ================================================================== small helpers

	public int getInt(ServerPlayer player, String key, int fallback) {
		return player(player.getUUID()).getIntOr(key, fallback);
	}

	public void putInt(ServerPlayer player, String key, int value) {
		player(player.getUUID()).putInt(key, value);
		setDirty();
	}

	public long getLong(ServerPlayer player, String key, long fallback) {
		return player(player.getUUID()).getLongOr(key, fallback);
	}

	public void putLong(ServerPlayer player, String key, long value) {
		player(player.getUUID()).putLong(key, value);
		setDirty();
	}

	public void forget(UUID player) {
		players.remove(player);
		for (CompoundTag town : towns.values()) {
			town.getCompoundOrEmpty("rep").remove(player.toString());
		}
		setDirty();
	}

	// ================================================================== save

	private static KotaData fromTag(CompoundTag tag) {
		KotaData data = new KotaData();
		CompoundTag towns = tag.getCompoundOrEmpty("towns");
		for (String key : towns.keySet()) {
			try {
				data.towns.put(Long.parseLong(key), towns.getCompoundOrEmpty(key).copy());
			} catch (NumberFormatException ignored) {
				// dropped
			}
		}
		CompoundTag players = tag.getCompoundOrEmpty("players");
		for (String key : players.keySet()) {
			try {
				data.players.put(UUID.fromString(key), players.getCompoundOrEmpty(key).copy());
			} catch (IllegalArgumentException ignored) {
				// dropped
			}
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		CompoundTag townTag = new CompoundTag();
		for (Map.Entry<Long, CompoundTag> entry : towns.entrySet()) {
			townTag.put(Long.toString(entry.getKey()), entry.getValue().copy());
		}
		tag.put("towns", townTag);
		CompoundTag playerTag = new CompoundTag();
		for (Map.Entry<UUID, CompoundTag> entry : players.entrySet()) {
			playerTag.put(entry.getKey().toString(), entry.getValue().copy());
		}
		tag.put("players", playerTag);
		return tag;
	}
}
