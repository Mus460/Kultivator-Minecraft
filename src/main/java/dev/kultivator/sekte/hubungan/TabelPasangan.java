package dev.kultivator.sekte.hubungan;

import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.Ikatan;
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
 * The pair table of one hall (DESIGN 36.2.2), kept in R's hall section {@code sekte_hubungan} of {@code HallData}:
 * <ul>
 * <li>{@code pairs}: one compound per pair, key {@code soulA|soulB} (the two soul strings in order): {@code a} affinity
 * -100..100, {@code k} kind ({@link Ikatan} id), {@code h} the last four events, {@code m} tea meetings, {@code t} bouts
 * against each other in tournaments, {@code l} the day of the last meeting, {@code dd}/{@code ds}/{@code dm} today's day,
 * change and meetings (the per-day caps), {@code w} a heavy wrong happened (a grudge may grow), {@code ban} the master
 * forbade their love, {@code po} the day an oath was last proposed;</li>
 * <li>{@code names}: soul -> its last known name (a Component), for screens and log lines while the body is away;</li>
 * <li>{@code pergi}: the pairs of a soul that left the sect, parked (at most 10 souls) so they come back with it;</li>
 * <li>{@code minta}: the open requests (oaths, honour duels) with an id the chat chips name, and {@code next} the next id.</li>
 * </ul>
 * Without a hall there is no table (the section is a throwaway compound), so R does nothing for that master. With 12
 * disciples the table holds at most 66 pairs. Server thread only.
 */
final class TabelPasangan {

	static final int HISTORY = 4;
	static final int MAX_PARKED = 10;
	static final int MAX_REQUESTS = 8;

	private static final String PAIRS = "pairs";
	private static final String NAMES = "names";
	private static final String PARKED = "pergi";
	private static final String REQUESTS = "minta";
	private static final String NEXT = "next";

	/** One pair, a live view over its compound (writes go straight to the hall data; call {@code Halls.setDirty()}). */
	static final class Pair {
		final String key;
		final UUID a;
		final UUID b;
		final CompoundTag tag;

		Pair(String key, UUID a, UUID b, CompoundTag tag) {
			this.key = key;
			this.a = a;
			this.b = b;
			this.tag = tag;
		}

		int affinity() {
			return tag.getIntOr("a", 0);
		}

		void setAffinity(int value) {
			tag.putInt("a", Math.max(-100, Math.min(100, value)));
		}

		Ikatan kind() {
			return Ikatan.byId(tag.getStringOr("k", Ikatan.NONE.id()));
		}

		void setKind(Ikatan kind) {
			tag.putString("k", (kind == null ? Ikatan.NONE : kind).id());
		}

		int meetings() {
			return tag.getIntOr("m", 0);
		}

		void addMeeting(long day) {
			tag.putInt("m", meetings() + 1);
			tag.putLong("l", day);
			rollDay(day);
			tag.putInt("dm", tag.getIntOr("dm", 0) + 1);
		}

		int meetingsToday(long day) {
			return tag.getLongOr("dd", -1L) == day ? tag.getIntOr("dm", 0) : 0;
		}

		long lastMeeting() {
			return tag.getLongOr("l", -1L);
		}

		int tournamentBouts() {
			return tag.getIntOr("t", 0);
		}

		void addTournamentBout() {
			tag.putInt("t", tournamentBouts() + 1);
		}

		/** Today's summed change (the +-25 per day cap). */
		int changedToday(long day) {
			return tag.getLongOr("dd", -1L) == day ? tag.getIntOr("ds", 0) : 0;
		}

		void addChangedToday(long day, int delta) {
			rollDay(day);
			tag.putInt("ds", tag.getIntOr("ds", 0) + delta);
		}

		private void rollDay(long day) {
			if (tag.getLongOr("dd", -1L) != day) {
				tag.putLong("dd", day);
				tag.putInt("ds", 0);
				tag.putInt("dm", 0);
			}
		}

		boolean wounded() {
			return tag.getBooleanOr("w", false);
		}

		void setWounded(boolean value) {
			if (value) {
				tag.putBoolean("w", true);
			} else {
				tag.remove("w");
			}
		}

		boolean banned() {
			return tag.getBooleanOr("ban", false);
		}

		void setBanned(boolean value) {
			if (value) {
				tag.putBoolean("ban", true);
			} else {
				tag.remove("ban");
			}
		}

		long proposedDay() {
			return tag.getLongOr("po", -1000L);
		}

		void setProposedDay(long day) {
			tag.putLong("po", day);
		}

		/** True when the two have met or shared any event. */
		boolean known() {
			return meetings() > 0 || !history().isEmpty() || affinity() != 0;
		}

		UUID other(UUID soul) {
			return soul.equals(a) ? b : a;
		}

		boolean has(UUID soul) {
			return soul.equals(a) || soul.equals(b);
		}

		/** The last events, newest first: {@code r} reason, {@code v} change, {@code d} day. */
		List<CompoundTag> history() {
			ListTag list = tag.getListOrEmpty("h");
			List<CompoundTag> out = new ArrayList<>(list.size());
			for (int i = list.size() - 1; i >= 0; i--) {
				list.getCompound(i).ifPresent(out::add);
			}
			return out;
		}

		void pushHistory(String reason, int delta, long day) {
			ListTag list = tag.getList("h").orElse(null);
			if (list == null) {
				list = new ListTag();
				tag.put("h", list);
			}
			CompoundTag event = new CompoundTag();
			event.putString("r", reason == null ? "" : reason);
			event.putInt("v", delta);
			event.putLong("d", day);
			list.add(event);
			while (list.size() > HISTORY) {
				list.remove(0);
			}
		}
	}

	/** True when the master has a hall (the table lives in it). */
	static boolean available(@Nullable UUID owner) {
		return owner != null && Halls.hallOf(owner) != null;
	}

	static CompoundTag root(UUID owner) {
		return Halls.section(owner, SekteIds.hallSection(SekteIds.R));
	}

	static String key(UUID a, UUID b) {
		String sa = a.toString();
		String sb = b.toString();
		return sa.compareTo(sb) <= 0 ? sa + "|" + sb : sb + "|" + sa;
	}

	private static CompoundTag child(CompoundTag parent, String name) {
		CompoundTag child = parent.getCompound(name).orElse(null);
		if (child == null) {
			child = new CompoundTag();
			parent.put(name, child);
		}
		return child;
	}

	static @Nullable Pair find(UUID owner, UUID a, UUID b) {
		if (a == null || b == null || a.equals(b) || !available(owner)) {
			return null;
		}
		String key = key(a, b);
		CompoundTag tag = root(owner).getCompoundOrEmpty(PAIRS).getCompound(key).orElse(null);
		return tag == null ? null : pair(key, tag);
	}

	static @Nullable Pair getOrCreate(UUID owner, UUID a, UUID b) {
		if (a == null || b == null || a.equals(b) || !available(owner)) {
			return null;
		}
		String key = key(a, b);
		CompoundTag pairs = child(root(owner), PAIRS);
		CompoundTag tag = pairs.getCompound(key).orElse(null);
		if (tag == null) {
			tag = new CompoundTag();
			tag.putInt("a", 0);
			tag.putString("k", Ikatan.NONE.id());
			pairs.put(key, tag);
			Halls.setDirty();
		}
		return pair(key, tag);
	}

	private static @Nullable Pair pair(String key, CompoundTag tag) {
		int bar = key.indexOf('|');
		if (bar <= 0) {
			return null;
		}
		try {
			return new Pair(key, UUID.fromString(key.substring(0, bar)), UUID.fromString(key.substring(bar + 1)), tag);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	/** Every pair of the hall. */
	static List<Pair> all(UUID owner) {
		if (!available(owner)) {
			return List.of();
		}
		CompoundTag pairs = root(owner).getCompoundOrEmpty(PAIRS);
		List<Pair> out = new ArrayList<>(pairs.size());
		for (String key : pairs.keySet()) {
			CompoundTag tag = pairs.getCompound(key).orElse(null);
			Pair pair = tag == null ? null : pair(key, tag);
			if (pair != null) {
				out.add(pair);
			}
		}
		return out;
	}

	/** Every pair of {@code soul}. */
	static List<Pair> of(UUID owner, UUID soul) {
		List<Pair> out = new ArrayList<>();
		for (Pair pair : all(owner)) {
			if (pair.has(soul)) {
				out.add(pair);
			}
		}
		return out;
	}

	// ================================================================== names

	static void rememberName(UUID owner, UUID soul, Component name) {
		if (!available(owner) || soul == null || name == null) {
			return;
		}
		CompoundTag names = child(root(owner), NAMES);
		String key = soul.toString();
		Component known = names.read(key, ComponentSerialization.CODEC).orElse(null);
		if (known == null || !known.equals(name)) {
			names.store(key, ComponentSerialization.CODEC, name);
			Halls.setDirty();
		}
	}

	static Component name(UUID owner, UUID soul) {
		if (soul == null || !available(owner)) {
			return Component.literal("?");
		}
		return root(owner).getCompoundOrEmpty(NAMES).read(soul.toString(), ComponentSerialization.CODEC)
				.orElse(Component.translatable("kultivator.sekte.hubungan.tanpa_nama"));
	}

	// ================================================================== leaving and coming back

	/** Moves every pair of {@code soul} into the parked list (it left the sect); the oldest parked soul is dropped past 10. */
	static void park(UUID owner, UUID soul) {
		if (!available(owner) || soul == null) {
			return;
		}
		CompoundTag root = root(owner);
		CompoundTag pairs = child(root, PAIRS);
		CompoundTag parked = new CompoundTag();
		for (Pair pair : of(owner, soul)) {
			parked.put(pair.key, pair.tag.copy());
			pairs.remove(pair.key);
		}
		ListTag list = root.getList(PARKED).orElse(null);
		if (list == null) {
			list = new ListTag();
			root.put(PARKED, list);
		}
		for (int i = list.size() - 1; i >= 0; i--) {
			if (soul.toString().equals(list.getCompoundOrEmpty(i).getStringOr("s", ""))) {
				list.remove(i);
			}
		}
		CompoundTag entry = new CompoundTag();
		entry.putString("s", soul.toString());
		entry.put("p", parked);
		list.add(entry);
		while (list.size() > MAX_PARKED) {
			list.remove(0);
		}
		Halls.setDirty();
	}

	/** True when {@code soul} has parked pairs (it left and may come back). */
	static boolean isParked(UUID owner, UUID soul) {
		if (!available(owner) || soul == null) {
			return false;
		}
		ListTag list = root(owner).getListOrEmpty(PARKED);
		for (int i = 0; i < list.size(); i++) {
			if (soul.toString().equals(list.getCompoundOrEmpty(i).getStringOr("s", ""))) {
				return true;
			}
		}
		return false;
	}

	/** Brings the parked pairs of {@code soul} back (pairs that exist again meanwhile are kept as they are). */
	static boolean restore(UUID owner, UUID soul) {
		if (!available(owner) || soul == null) {
			return false;
		}
		CompoundTag root = root(owner);
		ListTag list = root.getList(PARKED).orElse(null);
		if (list == null) {
			return false;
		}
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompoundOrEmpty(i);
			if (!soul.toString().equals(entry.getStringOr("s", ""))) {
				continue;
			}
			CompoundTag parked = entry.getCompoundOrEmpty("p");
			CompoundTag pairs = child(root, PAIRS);
			for (String key : parked.keySet()) {
				if (!pairs.contains(key)) {
					parked.getCompound(key).ifPresent(tag -> pairs.put(key, tag.copy()));
				}
			}
			list.remove(i);
			Halls.setDirty();
			return true;
		}
		return false;
	}

	/** Forgets every pair and name of {@code soul} (it fell and was buried). */
	static void drop(UUID owner, UUID soul) {
		if (!available(owner) || soul == null) {
			return;
		}
		CompoundTag pairs = child(root(owner), PAIRS);
		for (Pair pair : of(owner, soul)) {
			pairs.remove(pair.key);
		}
		Halls.setDirty();
	}

	// ================================================================== requests (oath, honour duel)

	/** An open request: {@code type} {@code sumpah} or {@code duel}, the two souls, the day it was made. */
	record Request(int id, String type, UUID a, UUID b, long day) {
	}

	static int addRequest(UUID owner, String type, UUID a, UUID b, long day) {
		if (!available(owner)) {
			return -1;
		}
		CompoundTag root = root(owner);
		for (Request open : requests(owner)) {
			if (open.type().equals(type) && key(open.a(), open.b()).equals(key(a, b))) {
				return open.id();
			}
		}
		int id = Math.max(1, root.getIntOr(NEXT, 1));
		root.putInt(NEXT, id >= 9999 ? 1 : id + 1);
		ListTag list = root.getList(REQUESTS).orElse(null);
		if (list == null) {
			list = new ListTag();
			root.put(REQUESTS, list);
		}
		CompoundTag entry = new CompoundTag();
		entry.putInt("id", id);
		entry.putString("t", type);
		entry.putString("a", a.toString());
		entry.putString("b", b.toString());
		entry.putLong("d", day);
		list.add(entry);
		while (list.size() > MAX_REQUESTS) {
			list.remove(0);
		}
		Halls.setDirty();
		return id;
	}

	static List<Request> requests(UUID owner) {
		if (!available(owner)) {
			return List.of();
		}
		ListTag list = root(owner).getListOrEmpty(REQUESTS);
		List<Request> out = new ArrayList<>(list.size());
		for (Tag tag : list) {
			if (tag instanceof CompoundTag entry) {
				try {
					out.add(new Request(entry.getIntOr("id", 0), entry.getStringOr("t", ""), UUID.fromString(entry.getStringOr("a", "")),
							UUID.fromString(entry.getStringOr("b", "")), entry.getLongOr("d", 0L)));
				} catch (IllegalArgumentException ignored) {
					// a broken entry is skipped (and dropped on the next removal)
				}
			}
		}
		return out;
	}

	static @Nullable Request request(UUID owner, int id) {
		for (Request request : requests(owner)) {
			if (request.id() == id) {
				return request;
			}
		}
		return null;
	}

	static void removeRequest(UUID owner, int id) {
		if (!available(owner)) {
			return;
		}
		ListTag list = root(owner).getList(REQUESTS).orElse(null);
		if (list == null) {
			return;
		}
		for (int i = list.size() - 1; i >= 0; i--) {
			CompoundTag entry = list.getCompoundOrEmpty(i);
			if (entry.isEmpty() || entry.getIntOr("id", 0) == id) {
				list.remove(i);
			}
		}
		Halls.setDirty();
	}

	/** Drops requests older than {@code maxDays}. */
	static void expireRequests(UUID owner, long today, int maxDays) {
		for (Request request : requests(owner)) {
			if (today - request.day() > maxDays) {
				removeRequest(owner, request.id());
			}
		}
	}

	/** {@code uji perguruan hubungan reset}: forgets the whole table. */
	static void reset(UUID owner) {
		if (!available(owner)) {
			return;
		}
		CompoundTag root = root(owner);
		for (String key : List.copyOf(root.keySet())) {
			root.remove(key);
		}
		Halls.setDirty();
	}

	private TabelPasangan() {
	}
}
