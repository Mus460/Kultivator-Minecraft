package dev.kultivator.dunia.tempat.ekonomi;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SavedData {@code kultivator:tempat_ekonomi} in the overworld (DESIGN 38.M, 38.0 rule 16, 38.E.5 test 30): everything of
 * the money loop that must survive a restart, saved from the moment it changes. Per player: bank deposit and history,
 * vault, pawns, notes cashed, Batu Asal exchange counters, guild points and badge, the active guild missions, gambling
 * counters and the inspected rack, forge orders, stall and caravans, dish and inn effects. Per site (keyed by site id):
 * town treasury, stall kiosks, the Grand Auction day and consignments. Global: note serials, the Young Dragon list, road
 * caravans, the weekly demand shift. Every number is clamped where it is read.
 */
public final class EkonomiData extends SavedData {

	public static final Codec<EkonomiData> CODEC = CompoundTag.CODEC.xmap(EkonomiData::fromTag, EkonomiData::toTag);
	public static final SavedDataType<EkonomiData> TYPE = new SavedDataType<>(Kultivator.id("tempat_ekonomi"), EkonomiData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<UUID, CompoundTag> pemain = new HashMap<>();
	private final Map<Long, CompoundTag> situs = new HashMap<>();
	private CompoundTag global = new CompoundTag();

	public EkonomiData() {
	}

	public static EkonomiData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** The player's compound (created on first use); call {@link #touch()} after changing it. */
	public CompoundTag pemain(UUID id) {
		return pemain.computeIfAbsent(id, key -> new CompoundTag());
	}

	/** A sub-compound {@code kunci} of the player's compound (created on first use). */
	public CompoundTag bagian(UUID id, String kunci) {
		CompoundTag tag = pemain(id);
		CompoundTag part = tag.getCompound(kunci).orElse(null);
		if (part == null) {
			part = new CompoundTag();
			tag.put(kunci, part);
		}
		return part;
	}

	/** A list {@code kunci} of the player's compound (created on first use). */
	public ListTag daftar(UUID id, String kunci) {
		CompoundTag tag = pemain(id);
		ListTag list = tag.getList(kunci).orElse(null);
		if (list == null) {
			list = new ListTag();
			tag.put(kunci, list);
		}
		return list;
	}

	/** Every player with a record (a copy). */
	public List<UUID> pemainIds() {
		return new ArrayList<>(pemain.keySet());
	}

	/** The site's compound (town treasury, kiosks; created on first use). */
	public CompoundTag situs(long id) {
		return situs.computeIfAbsent(id, key -> new CompoundTag());
	}

	/** Every site with a record (a copy). */
	public List<Long> situsIds() {
		return new ArrayList<>(situs.keySet());
	}

	/** World-wide data (note serials, Young Dragon list, road caravans). */
	public CompoundTag global() {
		return global;
	}

	/** A world-wide sub-compound (created on first use). */
	public CompoundTag global(String kunci) {
		CompoundTag part = global.getCompound(kunci).orElse(null);
		if (part == null) {
			part = new CompoundTag();
			global.put(kunci, part);
		}
		return part;
	}

	public void touch() {
		setDirty();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Daily and weekly counters

	/** The player's counter {@code kunci} of world day {@code hari} (0 on a new day). */
	public int harian(UUID id, long hari, String kunci) {
		CompoundTag day = pemain(id).getCompoundOrEmpty("harian");
		return day.getLongOr("hari", Long.MIN_VALUE) == hari ? day.getIntOr(kunci, 0) : 0;
	}

	/** Adds {@code n} to the player's counter {@code kunci} of day {@code hari}; returns the new value. */
	public int tambahHarian(UUID id, long hari, String kunci, int n) {
		CompoundTag tag = pemain(id);
		CompoundTag day = tag.getCompoundOrEmpty("harian");
		if (day.getLongOr("hari", Long.MIN_VALUE) != hari) {
			day = new CompoundTag();
			day.putLong("hari", hari);
		}
		int value = day.getIntOr(kunci, 0) + n;
		day.putInt(kunci, value);
		tag.put("harian", day);
		setDirty();
		return value;
	}

	/** The player's counter {@code kunci} of the world week ({@code hari / 7}). */
	public long mingguan(UUID id, long hari, String kunci) {
		CompoundTag week = pemain(id).getCompoundOrEmpty("mingguan");
		return week.getLongOr("minggu", Long.MIN_VALUE) == hari / 7L ? week.getLongOr(kunci, 0L) : 0L;
	}

	/** Adds {@code n} to the player's weekly counter {@code kunci}; returns the new value. */
	public long tambahMingguan(UUID id, long hari, String kunci, long n) {
		CompoundTag tag = pemain(id);
		CompoundTag week = tag.getCompoundOrEmpty("mingguan");
		if (week.getLongOr("minggu", Long.MIN_VALUE) != hari / 7L) {
			week = new CompoundTag();
			week.putLong("minggu", hari / 7L);
		}
		long value = week.getLongOr(kunci, 0L) + n;
		week.putLong(kunci, value);
		tag.put("mingguan", week);
		setDirty();
		return value;
	}

	/** The next serial of {@code kunci} (notes, orders, pawns, caravans): unique per world. */
	public long serial(String kunci) {
		CompoundTag serials = global("serial");
		long next = serials.getLongOr(kunci, 0L) + 1L;
		serials.putLong(kunci, next);
		setDirty();
		return next;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Codec

	private static EkonomiData fromTag(CompoundTag tag) {
		EkonomiData data = new EkonomiData();
		CompoundTag players = tag.getCompoundOrEmpty("pemain");
		for (String key : players.keySet()) {
			try {
				data.pemain.put(UUID.fromString(key), players.getCompoundOrEmpty(key).copy());
			} catch (IllegalArgumentException ignored) {
				// a broken key is dropped
			}
		}
		CompoundTag sites = tag.getCompoundOrEmpty("situs");
		for (String key : sites.keySet()) {
			try {
				data.situs.put(Long.parseLong(key), sites.getCompoundOrEmpty(key).copy());
			} catch (NumberFormatException ignored) {
				// a broken key is dropped
			}
		}
		data.global = tag.getCompoundOrEmpty("global").copy();
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		CompoundTag players = new CompoundTag();
		for (Map.Entry<UUID, CompoundTag> entry : pemain.entrySet()) {
			if (!entry.getValue().isEmpty()) {
				players.put(entry.getKey().toString(), entry.getValue().copy());
			}
		}
		tag.put("pemain", players);
		CompoundTag sites = new CompoundTag();
		for (Map.Entry<Long, CompoundTag> entry : situs.entrySet()) {
			if (!entry.getValue().isEmpty()) {
				sites.put(Long.toString(entry.getKey()), entry.getValue().copy());
			}
		}
		tag.put("situs", sites);
		tag.put("global", global.copy());
		return tag;
	}

	/** Removes list entries that are not compounds (defensive, after hand edits). */
	static void bersihkan(ListTag list) {
		for (int i = list.size() - 1; i >= 0; i--) {
			Tag each = list.get(i);
			if (!(each instanceof CompoundTag)) {
				list.remove(i);
			}
		}
	}
}
