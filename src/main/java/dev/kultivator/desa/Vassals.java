package dev.kultivator.desa;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
 * The vassal villages of each owner, SavedData {@code kultivator:vassals} in the overworld (DESIGN 25.6, 25.14, 25.15).
 * Per owner: the village records ({@link Village}), the Penjarahan log (last 10), the hall level last seen (for the +3 on a
 * level up), the last candidate day and the next record id. The public readers are cheap and safe without a server.
 */
public final class Vassals extends SavedData {

	public static final Codec<Vassals> CODEC = CompoundTag.CODEC.xmap(Vassals::fromTag, Vassals::toTag);
	public static final SavedDataType<Vassals> TYPE = new SavedDataType<>(Kultivator.id("vassals"), Vassals::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
	/** Entries of the Penjarahan log kept per owner. */
	public static final int LOG_SIZE = 10;

	/** One owner's villages and bookkeeping. */
	public static final class Owner {
		public final List<Village> villages = new ArrayList<>();
		public ListTag log = new ListTag();
		public int hallLevel;
		public long candDay = -1L;
		public int nextId = 1;
		/** Departed guard tags of villages that are gone, waiting to come back beside the owner. */
		public ListTag returns = new ListTag();
	}

	private static @Nullable MinecraftServer server;
	/** Bells of every pledged village (Kehancuran zones), rebuilt on every change. */
	private static volatile List<BlockPos> bells = List.of();

	private final Map<UUID, Owner> owners = new LinkedHashMap<>();

	public Vassals() {
	}

	// ================================================================== public contract (DESIGN 25.15)

	/** The owner's pledged villages (lost ones not counted). */
	public static int count(UUID owner) {
		Owner entry = peek(owner);
		if (entry == null) {
			return 0;
		}
		int n = 0;
		for (Village village : entry.villages) {
			if (village.pledged && !village.lost) {
				n++;
			}
		}
		return n;
	}

	/** The highest loyalty 0-100 among the owner's villages (0 without any). */
	public static int maxLoyalty(UUID owner) {
		Owner entry = peek(owner);
		int best = 0;
		if (entry != null) {
			for (Village village : entry.villages) {
				if (village.pledged && !village.lost) {
					best = Math.max(best, village.loyalty);
				}
			}
		}
		return best;
	}

	/** A view of every pledged village of the owner. */
	public static List<VillageView> views(UUID owner) {
		Owner entry = peek(owner);
		if (entry == null) {
			return List.of();
		}
		List<VillageView> list = new ArrayList<>();
		for (Village village : entry.villages) {
			if (village.pledged) {
				list.add(new VillageView(village.bell, village.name, village.loyalty, village.villagers, threatened(owner, village)));
			}
		}
		return list;
	}

	/** A request that brings danger, or a live wave event at the plaque. */
	static boolean threatened(UUID owner, Village village) {
		return VillageRaids.siegeAt(owner, village.id) != null || Village.REQ_BEAST.equals(village.reqType) || Village.REQ_CAMP.equals(village.reqType)
				|| Village.REQ_WATCH.equals(village.reqType);
	}

	// ================================================================== module access

	static void setServer(@Nullable MinecraftServer value) {
		server = value;
		if (value == null) {
			bells = List.of();
		} else {
			rebuildBells();
		}
	}

	static @Nullable MinecraftServer server() {
		return server;
	}

	static @Nullable Vassals data() {
		MinecraftServer current = server;
		return current == null ? null : current.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** The owner's entry, created when missing (null without a server). */
	static @Nullable Owner owner(UUID owner) {
		Vassals data = data();
		return data == null || owner == null ? null : data.owners.computeIfAbsent(owner, id -> new Owner());
	}

	/** The owner's entry or null (never creates). */
	static @Nullable Owner peek(UUID owner) {
		Vassals data = data();
		return data == null || owner == null ? null : data.owners.get(owner);
	}

	/** Every owner with an entry (a copy). */
	static List<UUID> owners() {
		Vassals data = data();
		return data == null ? List.of() : new ArrayList<>(data.owners.keySet());
	}

	static @Nullable Village find(UUID owner, int id) {
		Owner entry = peek(owner);
		if (entry != null) {
			for (Village village : entry.villages) {
				if (village.id == id) {
					return village;
				}
			}
		}
		return null;
	}

	/** The record whose plaque is at {@code pos}, with its owner; null when none. */
	static @Nullable Map.Entry<UUID, Village> byPlaque(BlockPos pos) {
		Vassals data = data();
		if (data == null) {
			return null;
		}
		for (Map.Entry<UUID, Owner> entry : data.owners.entrySet()) {
			for (Village village : entry.getValue().villages) {
				if (village.plaque.equals(pos)) {
					return Map.entry(entry.getKey(), village);
				}
			}
		}
		return null;
	}

	/** True when any record (of any owner) has its bell within {@code range} of {@code pos}. */
	static boolean anyBellWithin(BlockPos pos, double range) {
		Vassals data = data();
		if (data == null) {
			return false;
		}
		for (Owner entry : data.owners.values()) {
			for (Village village : entry.villages) {
				if (village.bell.distSqr(pos) < range * range) {
					return true;
				}
			}
		}
		return false;
	}

	/** Marks the data to be saved and refreshes the Kehancuran zone cache. */
	static void changed() {
		Vassals data = data();
		if (data != null) {
			data.setDirty();
		}
		rebuildBells();
	}

	private static void rebuildBells() {
		Vassals data = data();
		if (data == null) {
			bells = List.of();
			return;
		}
		List<BlockPos> list = new ArrayList<>();
		for (Owner entry : data.owners.values()) {
			for (Village village : entry.villages) {
				if (village.pledged && !village.lost) {
					list.add(village.bell);
				}
			}
		}
		bells = List.copyOf(list);
	}

	/** The bells of every pledged village (read by the Kehancuran zone provider; never null). */
	static List<BlockPos> bells() {
		return bells;
	}

	/** Adds a Penjarahan log line {day, village, result 1 repelled / 2 sacked, live}. */
	static void log(UUID owner, Village village, int result, boolean live) {
		Owner entry = owner(owner);
		if (entry == null) {
			return;
		}
		CompoundTag line = new CompoundTag();
		line.putLong("day", day());
		line.putString("village", village.name);
		line.putInt("result", result);
		line.putBoolean("live", live);
		entry.log.add(line);
		while (entry.log.size() > LOG_SIZE) {
			entry.log.remove(0);
		}
		changed();
	}

	// ================================================================== time

	/** Overworld day index {@code floor(clock / 24000)}. */
	static long day() {
		MinecraftServer current = server;
		return current == null ? 0L : Math.floorDiv(current.overworld().getOverworldClockTime(), 24000L);
	}

	/** Overworld time of day 0-23999. */
	static int timeOfDay() {
		MinecraftServer current = server;
		return current == null ? 0 : (int) Math.floorMod(current.overworld().getOverworldClockTime(), 24000L);
	}

	/** Night as vanilla monsters see it here: 13,000 to 23,000. */
	static boolean night() {
		int time = timeOfDay();
		return time >= 13000 && time < 23000;
	}

	// ================================================================== codec

	private static Vassals fromTag(CompoundTag tag) {
		Vassals data = new Vassals();
		CompoundTag all = tag.getCompoundOrEmpty("owners");
		for (String key : all.keySet()) {
			UUID id;
			try {
				id = UUID.fromString(key);
			} catch (IllegalArgumentException exception) {
				Kultivator.LOGGER.warn("Skipping vassal villages with a broken owner id: {}", key);
				continue;
			}
			CompoundTag ownerTag = all.getCompoundOrEmpty(key);
			Owner entry = new Owner();
			for (Tag each : ownerTag.getListOrEmpty("villages")) {
				if (each instanceof CompoundTag villageTag) {
					entry.villages.add(Village.load(villageTag));
				}
			}
			entry.log = ownerTag.getListOrEmpty("log").copy();
			entry.hallLevel = ownerTag.getIntOr("hall_level", 0);
			entry.candDay = ownerTag.getLongOr("cand_day", -1L);
			entry.nextId = Math.max(1, ownerTag.getIntOr("next_id", 1));
			entry.returns = ownerTag.getListOrEmpty("returns").copy();
			data.owners.put(id, entry);
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag all = new CompoundTag();
		for (Map.Entry<UUID, Owner> entry : owners.entrySet()) {
			Owner value = entry.getValue();
			if (value.villages.isEmpty() && value.log.isEmpty() && value.returns.isEmpty()) {
				continue;
			}
			CompoundTag ownerTag = new CompoundTag();
			ListTag villages = new ListTag();
			for (Village village : value.villages) {
				villages.add(village.save());
			}
			ownerTag.put("villages", villages);
			ownerTag.put("log", value.log.copy());
			ownerTag.putInt("hall_level", value.hallLevel);
			ownerTag.putLong("cand_day", value.candDay);
			ownerTag.putInt("next_id", value.nextId);
			ownerTag.put("returns", value.returns.copy());
			all.put(entry.getKey().toString(), ownerTag);
		}
		CompoundTag tag = new CompoundTag();
		tag.put("owners", all);
		return tag;
	}
}
