package dev.kultivator.dunia.reruntuhan.menara;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.Sects;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The tower's ranking (DESIGN 34.5.7), saved in the overworld ({@code kultivator:menara_rekor}): every climber's best
 * floor of all time and of this week (an in-game week of seven days). When a week ends the three best of it give their
 * sect renown (30 / 20 / 10) and the weekly list starts again. The steles show the top five.
 */
public final class TowerRecords extends SavedData {

	public static final long WEEK = 7L * 24000L;
	public static final int[] WEEK_RENOWN = {30, 20, 10};

	public record Entry(UUID id, String name, int best, int week, long at) {
	}

	public static final Codec<TowerRecords> CODEC = CompoundTag.CODEC.xmap(TowerRecords::fromTag, TowerRecords::toTag);
	public static final SavedDataType<TowerRecords> TYPE = new SavedDataType<>(Kultivator.id("menara_rekor"), TowerRecords::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<UUID, Entry> entries = new LinkedHashMap<>();
	private long weekIndex = -1L;

	public TowerRecords() {
	}

	public static TowerRecords get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	private static TowerRecords fromTag(CompoundTag tag) {
		TowerRecords data = new TowerRecords();
		data.weekIndex = tag.getLongOr("week", -1L);
		for (Tag t : tag.getListOrEmpty("entries")) {
			if (t instanceof CompoundTag e) {
				try {
					UUID id = UUID.fromString(e.getStringOr("id", ""));
					data.entries.put(id, new Entry(id, e.getStringOr("name", "?"), e.getIntOr("best", 0), e.getIntOr("weekly", 0), e.getLongOr("at", 0L)));
				} catch (IllegalArgumentException ignored) {
					// a broken entry is dropped
				}
			}
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("week", weekIndex);
		ListTag list = new ListTag();
		for (Entry entry : entries.values()) {
			CompoundTag e = new CompoundTag();
			e.putString("id", entry.id().toString());
			e.putString("name", entry.name());
			e.putInt("best", entry.best());
			e.putInt("weekly", entry.week());
			e.putLong("at", entry.at());
			list.add(e);
		}
		tag.put("entries", list);
		return tag;
	}

	/** A floor was cleared by {@code player}. */
	public void record(ServerPlayer player, int floor, long now) {
		rollWeek(player.level().getServer(), now);
		Entry old = entries.get(player.getUUID());
		int best = old == null ? floor : Math.max(old.best(), floor);
		int week = old == null ? floor : Math.max(old.week(), floor);
		long at = old != null && old.week() >= floor ? old.at() : now;
		entries.put(player.getUUID(), new Entry(player.getUUID(), player.getPlainTextName(), best, week, at));
		setDirty();
	}

	/** Sets a player's best directly ({@code uji menara rekor <n>}). */
	public void set(ServerPlayer player, int floor, long now) {
		entries.put(player.getUUID(), new Entry(player.getUUID(), player.getPlainTextName(), floor, floor, now));
		setDirty();
	}

	/** A new week: the three best of the old one give their sect renown, then the weekly list empties. */
	public void rollWeek(MinecraftServer server, long now) {
		long index = now / WEEK;
		if (weekIndex == index) {
			return;
		}
		if (weekIndex >= 0L) {
			List<Entry> top = weekly(3);
			for (int i = 0; i < top.size(); i++) {
				Entry entry = top.get(i);
				if (entry.week() <= 0) {
					continue;
				}
				ServerPlayer online = server.getPlayerList().getPlayer(entry.id());
				UUID owner = entry.id();
				if (online != null) {
					Sect sect = Sects.sectOf(online);
					if (sect != null) {
						owner = sect.master();
					}
				}
				Halls.addRenown(owner, WEEK_RENOWN[i], "kultivator.reruntuhan.menara.renown.week");
			}
			for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
				Entry x = e.getValue();
				e.setValue(new Entry(x.id(), x.name(), x.best(), 0, x.at()));
			}
		}
		weekIndex = index;
		setDirty();
	}

	/** The best of this week (by floor, then the earlier). */
	public List<Entry> weekly(int n) {
		List<Entry> list = new ArrayList<>(entries.values());
		list.removeIf(e -> e.week() <= 0);
		list.sort(Comparator.comparingInt(Entry::week).reversed().thenComparingLong(Entry::at));
		return list.size() > n ? list.subList(0, n) : list;
	}

	/** The best of all time. */
	public List<Entry> allTime(int n) {
		List<Entry> list = new ArrayList<>(entries.values());
		list.sort(Comparator.comparingInt(Entry::best).reversed().thenComparingLong(Entry::at));
		return list.size() > n ? list.subList(0, n) : list;
	}
}
