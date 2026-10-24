package dev.kultivator.dunia.tempat.cerita;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.nbt.CompoundTag;
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
 * SavedData {@code kultivator:tempat_cerita} in the overworld (DESIGN 38.S.1): per player the story flags, every quest
 * ever started (stage, stage start day, goal counters, branch taken), the tracked quest and the heard rumours; per
 * capital site the shared state of the arc "Bayangan di Balik Takhta" (38.S.3); per place the live rumours (38.S.5) and
 * the world-wide list. New worlds only: no migration of older layouts.
 *
 * <h2>Player compound</h2>
 * <pre>
 * bendera  C  flag id -> 1b
 * quest    C  quest id -> { tahap I (1..n, 999 done, -1 failed), simpul S (stage id), mulai L (day started),
 *                           hari L (day this stage started), hitung IA (goal counters of this stage), hasil S (ending
 *                           stage), situs L (place the quest was given at, 0 none), selesai L (day finished) }
 * lacak    S  quest id tracked by the journal needle
 * dengar   C  rumour key -> day last heard (no repeats within a day)
 * </pre>
 * <h2>Arc compound (per capital site id)</h2>
 * <pre>
 * bab I (0 not started, 1..7, 8 done), mulai L (day of chapter 1), babHari L (day the chapter started),
 * dukung S (ruyi|gu|netral), bab5 S (a|b|c), dou B (Dou helped in chapter 2), penguasa S (ruyi|gu), peserta C (uuid -> 1b),
 * bukti C (uuid -> count of the 3 proofs), hasil C (chapter -> outcome id)
 * </pre>
 */
public final class CeritaData extends SavedData {

	public static final Codec<CeritaData> CODEC = CompoundTag.CODEC.xmap(CeritaData::fromTag, CeritaData::toTag);
	public static final SavedDataType<CeritaData> TYPE = new SavedDataType<>(Kultivator.id("tempat_cerita"), CeritaData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** Key of the world-wide rumour list in {@link #rumours}. */
	public static final long GLOBAL = 0L;

	private final Map<UUID, CompoundTag> players = new HashMap<>();
	private final Map<Long, CompoundTag> arcs = new HashMap<>();
	private final Map<Long, List<CompoundTag>> rumours = new HashMap<>();
	/** The site id of the first capital that started its arc (fixed character names live there, 38.S.2). */
	private long firstCapital;

	public CeritaData() {
	}

	public static CeritaData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public void touch() {
		setDirty();
	}

	// ================================================================== players

	public CompoundTag player(UUID id) {
		return players.computeIfAbsent(id, key -> new CompoundTag());
	}

	public List<UUID> playerIds() {
		return new ArrayList<>(players.keySet());
	}

	public boolean flag(UUID player, String id) {
		return player(player).getCompoundOrEmpty("bendera").getBooleanOr(id, false);
	}

	public void setFlag(UUID player, String id, boolean on) {
		CompoundTag tag = player(player);
		CompoundTag flags = tag.getCompoundOrEmpty("bendera");
		if (on) {
			flags.putBoolean(id, true);
		} else {
			flags.remove(id);
		}
		tag.put("bendera", flags);
		setDirty();
	}

	/** The quest record of {@code quest} (empty compound when never started; not stored until written back). */
	public CompoundTag quest(UUID player, String quest) {
		return player(player).getCompoundOrEmpty("quest").getCompoundOrEmpty(quest);
	}

	public void putQuest(UUID player, String quest, CompoundTag record) {
		CompoundTag tag = player(player);
		CompoundTag quests = tag.getCompoundOrEmpty("quest");
		quests.put(quest, record);
		tag.put("quest", quests);
		setDirty();
	}

	public void removeQuest(UUID player, String quest) {
		CompoundTag tag = player(player);
		CompoundTag quests = tag.getCompoundOrEmpty("quest");
		quests.remove(quest);
		tag.put("quest", quests);
		setDirty();
	}

	/** Every quest id the player has a record of. */
	public List<String> questIds(UUID player) {
		return new ArrayList<>(player(player).getCompoundOrEmpty("quest").keySet());
	}

	public String tracked(UUID player) {
		return player(player).getStringOr("lacak", "");
	}

	public void setTracked(UUID player, String quest) {
		player(player).putString("lacak", quest == null ? "" : quest);
		setDirty();
	}

	/** True when rumour {@code key} was already told to the player on {@code day}. */
	public boolean heard(UUID player, String key, long day) {
		return player(player).getCompoundOrEmpty("dengar").getLongOr(key, -1L) == day;
	}

	public void markHeard(UUID player, String key, long day) {
		CompoundTag tag = player(player);
		CompoundTag heard = tag.getCompoundOrEmpty("dengar");
		if (heard.size() > 64) {
			heard = new CompoundTag();
		}
		heard.putLong(key, day);
		tag.put("dengar", heard);
		setDirty();
	}

	public void forget(UUID player) {
		players.remove(player);
		setDirty();
	}

	// ================================================================== capital arcs

	public CompoundTag arc(long site) {
		return arcs.computeIfAbsent(site, key -> new CompoundTag());
	}

	public List<Long> arcSites() {
		return new ArrayList<>(arcs.keySet());
	}

	public long firstCapital() {
		return firstCapital;
	}

	public void setFirstCapital(long site) {
		if (firstCapital == 0L) {
			firstCapital = site;
			setDirty();
		}
	}

	// ================================================================== rumours

	/** Live rumours of {@code site} ({@link #GLOBAL} for the world-wide list), oldest first (mutable). */
	public List<CompoundTag> rumours(long site) {
		return rumours.computeIfAbsent(site, key -> new ArrayList<>());
	}

	public List<Long> rumourSites() {
		return new ArrayList<>(rumours.keySet());
	}

	// ================================================================== save

	private static CeritaData fromTag(CompoundTag tag) {
		CeritaData data = new CeritaData();
		CompoundTag players = tag.getCompoundOrEmpty("players");
		for (String key : players.keySet()) {
			try {
				data.players.put(UUID.fromString(key), players.getCompoundOrEmpty(key).copy());
			} catch (IllegalArgumentException ignored) {
				// dropped
			}
		}
		CompoundTag arcs = tag.getCompoundOrEmpty("alur");
		for (String key : arcs.keySet()) {
			try {
				data.arcs.put(Long.parseLong(key), arcs.getCompoundOrEmpty(key).copy());
			} catch (NumberFormatException ignored) {
				// dropped
			}
		}
		CompoundTag rumours = tag.getCompoundOrEmpty("kabar");
		for (String key : rumours.keySet()) {
			try {
				long site = Long.parseLong(key);
				List<CompoundTag> list = new ArrayList<>();
				var entries = rumours.getListOrEmpty(key);
				for (int i = 0; i < entries.size(); i++) {
					list.add(entries.getCompoundOrEmpty(i).copy());
				}
				data.rumours.put(site, list);
			} catch (NumberFormatException ignored) {
				// dropped
			}
		}
		data.firstCapital = tag.getLongOr("pertama", 0L);
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		CompoundTag playerTag = new CompoundTag();
		for (Map.Entry<UUID, CompoundTag> entry : players.entrySet()) {
			playerTag.put(entry.getKey().toString(), entry.getValue().copy());
		}
		tag.put("players", playerTag);
		CompoundTag arcTag = new CompoundTag();
		for (Map.Entry<Long, CompoundTag> entry : arcs.entrySet()) {
			arcTag.put(Long.toString(entry.getKey()), entry.getValue().copy());
		}
		tag.put("alur", arcTag);
		CompoundTag rumourTag = new CompoundTag();
		for (Map.Entry<Long, List<CompoundTag>> entry : rumours.entrySet()) {
			net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
			for (CompoundTag rumour : entry.getValue()) {
				list.add(rumour.copy());
			}
			rumourTag.put(Long.toString(entry.getKey()), list);
		}
		tag.put("kabar", rumourTag);
		tag.putLong("pertama", firstCapital);
		return tag;
	}
}
