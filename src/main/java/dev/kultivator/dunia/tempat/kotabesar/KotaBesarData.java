package dev.kultivator.dunia.tempat.kotabesar;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SavedData {@code kultivator:kota_besar} in the overworld (DESIGN 38.B.5.2, 38.B.6, 38.D.6, owner 38KS).
 * <p>
 * Per capital (keyed by its town id, the {@code kota/kota_besar} site): {@code kejahatan.<uuid>} crime points 0-200 (minus
 * 2 per world day, {@code hari_kejahatan} remembers the last day decayed), {@code hilang.<uuid>} Nama Baik lost through
 * crimes (half of it returns when the fine is paid), {@code sel.<uuid>} the cell sentence ({@code sampai} game time,
 * {@code nomor} cell, {@code poin} served), the calendar state ({@code acara} running event, {@code mulai}, {@code akhir},
 * {@code geser} events moved to the next day, {@code pasang_hari} next beast tide day), {@code tutup_sampai} (shops closed
 * after a lost defence), the open-gate grants ({@code buka.<uuid>} game time) and the event registrations
 * ({@code daftar.<acara>} uuid list, {@code skor.<acara>} uuid -> score).
 * <p>
 * Per player: {@code gelar} titles earned (ids), {@code perintah} the palace orders of the current 30-day term,
 * {@code teka.<festival day>} riddles answered, {@code kuliah} last lecture day, {@code gratis} the free-teleport day,
 * {@code menang.<acara>} wins.
 */
public final class KotaBesarData extends SavedData {

	public static final Codec<KotaBesarData> CODEC = CompoundTag.CODEC.xmap(KotaBesarData::fromTag, KotaBesarData::toTag);
	public static final SavedDataType<KotaBesarData> TYPE = new SavedDataType<>(Kultivator.id("kota_besar"), KotaBesarData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	public static final int KEJAHATAN_MAKS = 200;

	private final Map<Long, CompoundTag> kota = new HashMap<>();
	private final Map<UUID, CompoundTag> pemain = new HashMap<>();

	public KotaBesarData() {
	}

	public static KotaBesarData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** The capital's compound (created on first use); call {@link #setDirty()} after changing it. */
	public CompoundTag kota(long town) {
		return kota.computeIfAbsent(town, key -> new CompoundTag());
	}

	public Set<Long> kotaIds() {
		return Set.copyOf(kota.keySet());
	}

	/** The player's compound (created on first use). */
	public CompoundTag pemain(UUID id) {
		return pemain.computeIfAbsent(id, key -> new CompoundTag());
	}

	// ================================================================== crime record

	public int kejahatan(long town, UUID player) {
		return Mth.clamp(kota(town).getCompoundOrEmpty("kejahatan").getIntOr(player.toString(), 0), 0, KEJAHATAN_MAKS);
	}

	/** Sets the crime points (clamped) and returns them. */
	public int setKejahatan(long town, UUID player, int poin) {
		CompoundTag tag = kota(town).getCompoundOrEmpty("kejahatan");
		int v = Mth.clamp(poin, 0, KEJAHATAN_MAKS);
		if (v == 0) {
			tag.remove(player.toString());
		} else {
			tag.putInt(player.toString(), v);
		}
		kota(town).put("kejahatan", tag);
		setDirty();
		return v;
	}

	public int hilang(long town, UUID player) {
		return kota(town).getCompoundOrEmpty("hilang").getIntOr(player.toString(), 0);
	}

	public void setHilang(long town, UUID player, int nilai) {
		CompoundTag tag = kota(town).getCompoundOrEmpty("hilang");
		if (nilai <= 0) {
			tag.remove(player.toString());
		} else {
			tag.putInt(player.toString(), nilai);
		}
		kota(town).put("hilang", tag);
		setDirty();
	}

	/** Every player with crime points in {@code town}. */
	public Set<String> pelaku(long town) {
		return Set.copyOf(kota(town).getCompoundOrEmpty("kejahatan").keySet());
	}

	// ================================================================== cells

	public CompoundTag sel(long town, UUID player) {
		return kota(town).getCompoundOrEmpty("sel").getCompoundOrEmpty(player.toString());
	}

	public void setSel(long town, UUID player, CompoundTag sel) {
		CompoundTag all = kota(town).getCompoundOrEmpty("sel");
		if (sel == null || sel.isEmpty()) {
			all.remove(player.toString());
		} else {
			all.put(player.toString(), sel);
		}
		kota(town).put("sel", all);
		setDirty();
	}

	/** Every prisoner of {@code town}. */
	public Set<String> tahanan(long town) {
		return Set.copyOf(kota(town).getCompoundOrEmpty("sel").keySet());
	}

	// ================================================================== small values

	public long getLong(long town, String key, long fallback) {
		return kota(town).getLongOr(key, fallback);
	}

	public void putLong(long town, String key, long value) {
		kota(town).putLong(key, value);
		setDirty();
	}

	public String getString(long town, String key) {
		return kota(town).getStringOr(key, "");
	}

	public void putString(long town, String key, String value) {
		kota(town).putString(key, value);
		setDirty();
	}

	/** A per-capital compound under {@code key} (created on first use). */
	public CompoundTag bagian(long town, String key) {
		CompoundTag tag = kota(town);
		if (!tag.contains(key)) {
			tag.put(key, new CompoundTag());
		}
		return tag.getCompoundOrEmpty(key);
	}

	public void putBagian(long town, String key, CompoundTag value) {
		kota(town).put(key, value);
		setDirty();
	}

	// ================================================================== titles

	public boolean punyaGelar(UUID player, String gelar) {
		return pemain(player).getCompoundOrEmpty("gelar").getBooleanOr(gelar, false);
	}

	/** Adds a title; true when it is new. */
	public boolean beriGelar(UUID player, String gelar) {
		CompoundTag tag = pemain(player).getCompoundOrEmpty("gelar");
		if (tag.getBooleanOr(gelar, false)) {
			return false;
		}
		tag.putBoolean(gelar, true);
		pemain(player).put("gelar", tag);
		setDirty();
		return true;
	}

	public Set<String> gelar(UUID player) {
		return Set.copyOf(pemain(player).getCompoundOrEmpty("gelar").keySet());
	}

	// ================================================================== save

	private static KotaBesarData fromTag(CompoundTag tag) {
		KotaBesarData data = new KotaBesarData();
		CompoundTag towns = tag.getCompoundOrEmpty("kota");
		for (String key : towns.keySet()) {
			try {
				data.kota.put(Long.parseLong(key), towns.getCompoundOrEmpty(key).copy());
			} catch (NumberFormatException ignored) {
				// dropped
			}
		}
		CompoundTag players = tag.getCompoundOrEmpty("pemain");
		for (String key : players.keySet()) {
			try {
				data.pemain.put(UUID.fromString(key), players.getCompoundOrEmpty(key).copy());
			} catch (IllegalArgumentException ignored) {
				// dropped
			}
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		CompoundTag towns = new CompoundTag();
		for (Map.Entry<Long, CompoundTag> e : kota.entrySet()) {
			towns.put(Long.toString(e.getKey()), e.getValue().copy());
		}
		tag.put("kota", towns);
		CompoundTag players = new CompoundTag();
		for (Map.Entry<UUID, CompoundTag> e : pemain.entrySet()) {
			players.put(e.getKey().toString(), e.getValue().copy());
		}
		tag.put("pemain", players);
		return tag;
	}
}
