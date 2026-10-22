package dev.kultivator.sekte.turnamen;

import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.SekteIds;
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
 * The seasons of a hall's tournaments (DESIGN 36.2.3), in the hall section {@code sekte_turnamen}: the season number
 * (the old competition count), the board of Elo ratings of every disciple who ever fought ({@code papan}, soul to name,
 * rating, wins and losses, kept even while the disciple is away or unloaded, at most {@link #BOARD_MAX}), the roll of
 * champions ({@code juara}, newest last, at most {@link #ROLL_MAX}), the inter-sect record and the NPC sect challenge that
 * waits for the master ({@code tantangan}). Server thread only.
 */
public final class Musim {

	public static final int BOARD_MAX = 30;
	public static final int ROLL_MAX = 12;

	/** One line of the season board. */
	public record Baris(UUID soul, Component name, int elo, int wins, int losses) {
	}

	/** One champion of the roll. */
	public record Juara(int season, String kind, Component name, @Nullable UUID soul, @Nullable String guestSect) {
	}

	static CompoundTag section(UUID owner) {
		return Halls.section(owner, SekteIds.hallSection(SekteIds.T));
	}

	static void dirty() {
		Halls.setDirty();
	}

	public static int season(UUID owner) {
		return section(owner).getIntOr("musim", 0);
	}

	static void setSeason(UUID owner, int season) {
		section(owner).putInt("musim", season);
		dirty();
	}

	// ================================================================== board

	/** Writes the current rating and record of {@code soul} on the board. */
	static void board(UUID owner, UUID soul, Component name, int elo, int wins, int losses) {
		CompoundTag section = section(owner);
		CompoundTag board = section.getCompoundOrEmpty("papan");
		CompoundTag entry = new CompoundTag();
		entry.store("n", ComponentSerialization.CODEC, name);
		entry.putInt("e", elo);
		entry.putInt("w", wins);
		entry.putInt("l", losses);
		board.put(soul.toString(), entry);
		if (board.size() > BOARD_MAX) {
			String lowest = null;
			int value = Integer.MAX_VALUE;
			for (String key : board.keySet()) {
				if (key.equals(soul.toString())) {
					continue;
				}
				int each = board.getCompoundOrEmpty(key).getIntOr("e", 0);
				if (each < value) {
					value = each;
					lowest = key;
				}
			}
			if (lowest != null) {
				board.remove(lowest);
			}
		}
		section.put("papan", board);
		dirty();
	}

	/** Removes a soul from the board (it left the sect, or a test reset). */
	static void forget(UUID owner, UUID soul) {
		CompoundTag section = section(owner);
		CompoundTag board = section.getCompoundOrEmpty("papan");
		if (board.contains(soul.toString())) {
			board.remove(soul.toString());
			section.put("papan", board);
			dirty();
		}
	}

	/** The board, highest rating first. */
	public static List<Baris> board(UUID owner) {
		CompoundTag board = section(owner).getCompoundOrEmpty("papan");
		List<Baris> list = new ArrayList<>();
		for (String key : board.keySet()) {
			UUID soul;
			try {
				soul = UUID.fromString(key);
			} catch (IllegalArgumentException exception) {
				continue;
			}
			CompoundTag entry = board.getCompoundOrEmpty(key);
			Component name = entry.read("n", ComponentSerialization.CODEC).orElse(Component.literal("?"));
			list.add(new Baris(soul, name, entry.getIntOr("e", Elo.START), entry.getIntOr("w", 0), entry.getIntOr("l", 0)));
		}
		list.sort((a, b) -> Integer.compare(b.elo(), a.elo()));
		return list;
	}

	// ================================================================== champions

	static void champion(UUID owner, int season, String kind, Component name, @Nullable UUID soul, @Nullable String guestSect) {
		CompoundTag section = section(owner);
		ListTag roll = section.getListOrEmpty("juara").copy();
		CompoundTag entry = new CompoundTag();
		entry.putInt("s", season);
		entry.putString("k", kind);
		entry.store("n", ComponentSerialization.CODEC, name);
		if (soul != null) {
			entry.putString("j", soul.toString());
		}
		if (guestSect != null) {
			entry.putString("g", guestSect);
		}
		roll.add(entry);
		while (roll.size() > ROLL_MAX) {
			roll.remove(0);
		}
		section.put("juara", roll);
		dirty();
	}

	/** The roll of champions, newest first. */
	public static List<Juara> champions(UUID owner) {
		List<Juara> list = new ArrayList<>();
		for (Tag tag : section(owner).getListOrEmpty("juara")) {
			if (tag instanceof CompoundTag entry) {
				UUID soul = null;
				String id = entry.getStringOr("j", "");
				if (!id.isEmpty()) {
					try {
						soul = UUID.fromString(id);
					} catch (IllegalArgumentException ignored) {
						soul = null;
					}
				}
				String guest = entry.getStringOr("g", "");
				list.add(0, new Juara(entry.getIntOr("s", 0), entry.getStringOr("k", "biasa"),
						entry.read("n", ComponentSerialization.CODEC).orElse(Component.literal("?")), soul, guest.isEmpty() ? null : guest));
			}
		}
		return list;
	}

	/** The reigning champion's soul (the newest roll entry that was the hall's own disciple), or null. */
	public static @Nullable UUID reigning(UUID owner) {
		List<Juara> roll = champions(owner);
		return roll.isEmpty() ? null : roll.get(0).soul();
	}

	// ================================================================== inter-sect

	static void interSect(UUID owner, boolean won) {
		CompoundTag section = section(owner);
		String key = won ? "antar_menang" : "antar_kalah";
		section.putInt(key, section.getIntOr(key, 0) + 1);
		dirty();
	}

	public static int interSectWins(UUID owner) {
		return section(owner).getIntOr("antar_menang", 0);
	}

	public static int interSectLosses(UUID owner) {
		return section(owner).getIntOr("antar_kalah", 0);
	}

	/** An NPC sect's challenge that waits until the master can host it ({@code sectId}, the day it came). */
	static void setPending(UUID owner, @Nullable String sectId, long day) {
		CompoundTag section = section(owner);
		if (sectId == null) {
			section.remove("tantangan");
			section.remove("tantangan_hari");
		} else {
			section.putString("tantangan", sectId);
			section.putLong("tantangan_hari", day);
		}
		dirty();
	}

	static @Nullable String pending(UUID owner) {
		String id = section(owner).getStringOr("tantangan", "");
		return id.isEmpty() ? null : id;
	}

	static long pendingDay(UUID owner) {
		return section(owner).getLongOr("tantangan_hari", 0L);
	}

	/** Clears the whole section (test {@code musim reset}). */
	static void reset(UUID owner) {
		CompoundTag section = section(owner);
		for (String key : List.copyOf(section.keySet())) {
			section.remove(key);
		}
		dirty();
	}

	private Musim() {
	}
}
