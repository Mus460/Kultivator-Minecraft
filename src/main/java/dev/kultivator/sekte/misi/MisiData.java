package dev.kultivator.sekte.misi;

import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.SekteIds;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * X's saved state in the owner's hall section {@code sekte_misi} (DESIGN 36.3.1, {@code SekteIds.hallSection("misi")}):
 * <ul>
 * <li>{@code papan} the day's story board (offers) and {@code papan_hari} its day;</li>
 * <li>{@code aktif} the running missions (team, stages so far, the result once finished);</li>
 * <li>{@code efek} per soul: what the return handler writes into the member's stored data (applied once);</li>
 * <li>{@code riwayat} the last {@link #HISTORY} reports (the "Kabar Misi" tab reads them again);</li>
 * <li>{@code dicari} departed disciples a "Cari Murid" mission may look for (heard from {@code DISCIPLE_LEFT});</li>
 * <li>{@code tertunda} report scrolls waiting for the master to be online;</li>
 * <li>{@code seq} mission ids, {@code selesai} finished missions.</li>
 * </ul>
 * Server thread only; every writer calls {@link #dirty()}.
 */
final class MisiData {

	static final int HISTORY = 8;
	static final int SEARCH_MAX = 10;
	static final int PENDING_MAX = 8;

	static CompoundTag section(UUID owner) {
		return Halls.section(owner, SekteIds.hallSection(SekteIds.X));
	}

	static void dirty() {
		Halls.setDirty();
	}

	static ListTag list(CompoundTag section, String key) {
		ListTag list = section.getList(key).orElse(null);
		if (list == null) {
			list = new ListTag();
			section.put(key, list);
		}
		return list;
	}

	static List<CompoundTag> compounds(ListTag list) {
		List<CompoundTag> out = new ArrayList<>(list.size());
		for (Tag tag : list) {
			if (tag instanceof CompoundTag compound) {
				out.add(compound);
			}
		}
		return out;
	}

	static List<CompoundTag> active(UUID owner) {
		return compounds(list(section(owner), "aktif"));
	}

	static @Nullable CompoundTag mission(UUID owner, int id) {
		for (CompoundTag mission : active(owner)) {
			if (mission.getIntOr("id", -1) == id) {
				return mission;
			}
		}
		return null;
	}

	static void removeMission(UUID owner, int id) {
		ListTag list = list(section(owner), "aktif");
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.get(i) instanceof CompoundTag mission && mission.getIntOr("id", -1) == id) {
				list.remove(i);
			}
		}
		dirty();
	}

	static int nextId(UUID owner) {
		CompoundTag section = section(owner);
		int id = section.getIntOr("seq", 0) + 1;
		section.putInt("seq", id);
		return id;
	}

	/** The team entries of a mission ({@code soul}, {@code name}, {@code realm}, {@code hurt}, {@code left}, {@code back}). */
	static List<CompoundTag> team(CompoundTag mission) {
		return compounds(mission.getListOrEmpty("team"));
	}

	static @Nullable UUID soul(CompoundTag entry) {
		return uuid(entry.getStringOr("soul", ""));
	}

	static @Nullable UUID uuid(String text) {
		if (text == null || text.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(text);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	static Component name(CompoundTag entry, String key) {
		return entry.read(key, ComponentSerialization.CODEC).orElse(Component.translatable("entity.kultivator.disciple"));
	}

	static void putName(CompoundTag entry, String key, Component name) {
		entry.store(key, ComponentSerialization.CODEC, name == null ? Component.empty() : name);
	}

	/** The mission whose team holds {@code soul} and has not come back yet, or null. */
	static @Nullable CompoundTag missionOf(UUID owner, UUID soul) {
		String text = soul.toString();
		for (CompoundTag mission : active(owner)) {
			for (CompoundTag entry : team(mission)) {
				if (text.equals(entry.getStringOr("soul", "")) && !entry.getBooleanOr("back", false)) {
					return mission;
				}
			}
		}
		return null;
	}

	// ================================================================== history and pending reports

	static void addHistory(UUID owner, CompoundTag report) {
		ListTag history = list(section(owner), "riwayat");
		history.add(report.copy());
		while (history.size() > HISTORY) {
			history.remove(0);
		}
		CompoundTag section = section(owner);
		section.putInt("selesai", section.getIntOr("selesai", 0) + 1);
		dirty();
	}

	static List<CompoundTag> history(UUID owner) {
		return compounds(list(section(owner), "riwayat"));
	}

	static void addPending(UUID owner, CompoundTag report) {
		ListTag pending = list(section(owner), "tertunda");
		pending.add(report.copy());
		while (pending.size() > PENDING_MAX) {
			pending.remove(0);
		}
		dirty();
	}

	// ================================================================== departed disciples (DISCIPLE_LEFT)

	static void addSearchable(UUID owner, UUID soul, Component name, String how, long day) {
		ListTag list = list(section(owner), "dicari");
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.get(i) instanceof CompoundTag entry && soul.toString().equals(entry.getStringOr("soul", ""))) {
				list.remove(i);
			}
		}
		CompoundTag entry = new CompoundTag();
		entry.putString("soul", soul.toString());
		putName(entry, "name", name);
		entry.putString("how", how);
		entry.putLong("day", day);
		list.add(entry);
		while (list.size() > SEARCH_MAX) {
			list.remove(0);
		}
		dirty();
	}

	static List<CompoundTag> searchable(UUID owner) {
		return compounds(list(section(owner), "dicari"));
	}

	static void removeSearchable(UUID owner, UUID soul) {
		ListTag list = list(section(owner), "dicari");
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.get(i) instanceof CompoundTag entry && soul.toString().equals(entry.getStringOr("soul", ""))) {
				list.remove(i);
			}
		}
		dirty();
	}

	// ================================================================== the away records of Halls (recall)

	/**
	 * Moves the return time of {@code soul}'s {@code sekte_misi} away record to {@code returnAt}. The records live in the
	 * hall's {@code core.away} list ({@code Halls.storeAway}); {@code Halls.section(owner, "core")} is that same compound
	 * (as {@code Envoys} uses it for {@code envoy_day}). False when there is no such record.
	 */
	static boolean moveReturn(UUID owner, UUID soul, long returnAt) {
		ListTag away = Halls.section(owner, "core").getListOrEmpty("away");
		boolean moved = false;
		for (Tag tag : away) {
			if (tag instanceof CompoundTag rec && Misi.REASON.equals(rec.getStringOr("reason", ""))
					&& soul.equals(rec.read("soul", UUIDUtil.CODEC).orElse(null)) && !rec.getBooleanOr("handled", false)) {
				rec.putLong("return_at", returnAt);
				moved = true;
			}
		}
		if (moved) {
			dirty();
		}
		return moved;
	}

	private MisiData() {
	}
}
