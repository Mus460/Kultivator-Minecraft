package dev.kultivator.sekte;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.sekte.api.Kenangan;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Ingatan, the shared memory ring of a disciple (DESIGN 36.3.2, contract C36.L3), kept by the lead in
 * {@code DiscipleData.extra().ingatan.list} so no module needs another to remember. Every module writes its moments with
 * {@link #remember} (kind {@code <module>.<what>}); P reads {@link #drift} for contentment, R reads the {@code other}
 * souls, A engraves the strongest ones on a grave, X quotes them in reports, the scroll shows {@link #of}.
 * <p>
 * At most {@link #MAX_MEMORIES} (config {@code umum.ingatanMaks}, 4-16); when full the weakest (smallest |weight|), then oldest, is forgotten. The same kind
 * with the same other soul on the same day replaces the earlier entry (no spam). Memories fade: {@link #drift} halves a
 * memory's pull every {@link #HALF_LIFE_DAYS} days, but entries of |weight| 9-10 never fade (a master's betrayal, a
 * Dao companion's death). Pure data on the disciple; server thread only.
 */
public final class Ingatan {

	public static final int MAX_MEMORIES = 16;
	public static final int HALF_LIFE_DAYS = 10;
	public static final int LASTING_WEIGHT = 9;

	private static final String LIST = "list";

	/** Today's in-game day number (the overworld clock / 24000). */
	public static long today(@Nullable MinecraftServer server) {
		return server == null ? 0L : Math.max(0L, server.overworld().getOverworldClockTime() / 24000L);
	}

	/** Remembers {@code memory} (see the class comment for replacing and forgetting). */
	public static void remember(@Nullable DiscipleData data, @Nullable Kenangan memory) {
		if (data == null || memory == null || memory.kind().isEmpty()) {
			return;
		}
		List<Kenangan> list = new ArrayList<>(of(data));
		list.removeIf(old -> old.kind().equals(memory.kind()) && old.day() == memory.day()
				&& java.util.Objects.equals(old.other(), memory.other()));
		list.add(memory);
		int max = Math.max(4, Math.min(MAX_MEMORIES, SekteContent.umum().ingatanMaks));
		while (list.size() > max) {
			Kenangan weakest = null;
			for (Kenangan k : list) {
				if (k == memory) {
					continue;
				}
				if (weakest == null || Math.abs(k.weight()) < Math.abs(weakest.weight())
						|| Math.abs(k.weight()) == Math.abs(weakest.weight()) && k.day() < weakest.day()) {
					weakest = k;
				}
			}
			list.remove(weakest == null ? list.get(0) : weakest);
		}
		write(data, list);
	}

	/** Shorthand: remembers a moment of today. */
	public static void remember(@Nullable MinecraftServer server, @Nullable DiscipleData data, String kind, @Nullable UUID other,
			int weight, String textKey, String arg) {
		remember(data, new Kenangan(kind, today(server), other, weight, textKey, arg));
	}

	/** Every memory, oldest first (empty list when none). */
	public static List<Kenangan> of(@Nullable DiscipleData data) {
		if (data == null) {
			return List.of();
		}
		CompoundTag section = data.extra().getCompound(SekteIds.EXTRA_MEMORY).orElse(null);
		if (section == null) {
			return List.of();
		}
		ListTag tags = section.getListOrEmpty(LIST);
		List<Kenangan> list = new ArrayList<>(tags.size());
		for (Tag tag : tags) {
			if (tag instanceof CompoundTag entry) {
				UUID other = null;
				String o = entry.getStringOr("o", "");
				if (!o.isEmpty()) {
					try {
						other = UUID.fromString(o);
					} catch (IllegalArgumentException ignored) {
						other = null;
					}
				}
				list.add(new Kenangan(entry.getStringOr("k", ""), entry.getLongOr("d", 0L), other, entry.getIntOr("w", 0),
						entry.getStringOr("t", ""), entry.getStringOr("a", "")));
			}
		}
		return Collections.unmodifiableList(list);
	}

	/** The memories whose kind starts with {@code prefix} ({@code "turnamen."} for all of T's), newest first. */
	public static List<Kenangan> ofKind(@Nullable DiscipleData data, String prefix) {
		List<Kenangan> found = new ArrayList<>();
		for (Kenangan k : of(data)) {
			if (k.kind().startsWith(prefix)) {
				found.add(0, k);
			}
		}
		return found;
	}

	/** The memories involving {@code other}, newest first. */
	public static List<Kenangan> about(@Nullable DiscipleData data, UUID other) {
		List<Kenangan> found = new ArrayList<>();
		for (Kenangan k : of(data)) {
			if (other.equals(k.other())) {
				found.add(0, k);
			}
		}
		return found;
	}

	/**
	 * The faded sum of all weights today, -40..40: a memory pulls with {@code weight * 0.5^(age / HALF_LIFE_DAYS)}, lasting
	 * ones (|weight| 9-10) with their full weight. P turns it into the daily contentment drift.
	 */
	public static double drift(@Nullable DiscipleData data, long today) {
		double sum = 0.0D;
		for (Kenangan k : of(data)) {
			if (Math.abs(k.weight()) >= LASTING_WEIGHT) {
				sum += k.weight();
			} else {
				long age = Math.max(0L, today - k.day());
				sum += k.weight() * Math.pow(0.5D, age / (double) HALF_LIFE_DAYS);
			}
		}
		return Math.max(-40.0D, Math.min(40.0D, sum));
	}

	/** Forgets every memory of {@code kindPrefix} (a module's reset command), or all with "". */
	public static void forget(@Nullable DiscipleData data, String kindPrefix) {
		if (data == null) {
			return;
		}
		List<Kenangan> list = new ArrayList<>(of(data));
		if (list.removeIf(k -> k.kind().startsWith(kindPrefix == null ? "" : kindPrefix))) {
			write(data, list);
		}
	}

	private static void write(DiscipleData data, List<Kenangan> list) {
		ListTag tags = new ListTag();
		for (Kenangan k : list) {
			CompoundTag entry = new CompoundTag();
			entry.putString("k", k.kind());
			entry.putLong("d", k.day());
			if (k.other() != null) {
				entry.putString("o", k.other().toString());
			}
			entry.putInt("w", k.weight());
			entry.putString("t", k.text());
			if (!k.arg().isEmpty()) {
				entry.putString("a", k.arg());
			}
			tags.add(entry);
		}
		data.extraSection(SekteIds.EXTRA_MEMORY).put(LIST, tags);
	}

	private Ingatan() {
	}
}
