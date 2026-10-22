package dev.kultivator.sekte.kepribadian;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.HatiService;
import dev.kultivator.sekte.api.Sifat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The saved heart of one disciple, {@code DiscipleData.extra().hati} (DESIGN 36.2.1, owned by module P only; everyone
 * else reads it through {@code SekteApi.hati()}). Pure data access, server thread only:
 * <ul>
 * <li>{@code sifat} the trait ids (2, sometimes 3), {@code k} contentment 0-100 (a double, shown rounded),
 * {@code day} the last day the daily update ran (absent until P met the disciple once), {@code low} days in a row below
 * the leaving threshold, {@code state} the last {@link HatiService.Keadaan} ordinal;</li>
 * <li>care of the day: {@code rd}/{@code rv} (day and the care sum), {@code last} the last day of any care, {@code ng}
 * the last neglect, {@code talk} the last talk, {@code gd}/{@code gn} gifts of the day, {@code maaf} the last apology,
 * {@code kb} the last "sacrificed" detection;</li>
 * <li>{@code bujuk}/{@code bujukDay} an NPC sect that tried to lure it (module D's MEREKRUT), {@code kembara} the last
 * time it asked to wander, {@code ujian} the last trait change by a heart trial.</li>
 * </ul>
 */
public final class Hati {

	public static final double START = 70.0D;

	static @Nullable CompoundTag peek(@Nullable DiscipleData data) {
		return data == null ? null : data.extra().getCompound(SekteIds.EXTRA_P).orElse(null);
	}

	static CompoundTag tag(DiscipleData data) {
		return data.extraSection(SekteIds.EXTRA_P);
	}

	/** True once P rolled the traits of this disciple. */
	public static boolean known(@Nullable DiscipleData data) {
		CompoundTag tag = peek(data);
		return tag != null && tag.contains("sifat");
	}

	// ================================================================== traits

	public static List<Sifat> traits(@Nullable DiscipleData data) {
		CompoundTag tag = peek(data);
		if (tag == null) {
			return List.of();
		}
		ListTag list = tag.getListOrEmpty("sifat");
		List<Sifat> traits = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			Sifat sifat = Sifat.byId(list.getStringOr(i, ""));
			if (sifat != null && !traits.contains(sifat)) {
				traits.add(sifat);
			}
		}
		return Collections.unmodifiableList(traits);
	}

	public static boolean has(@Nullable DiscipleData data, Sifat sifat) {
		return traits(data).contains(sifat);
	}

	/** Writes the traits (never two of one axis: a later one on a taken axis is dropped). */
	static void setTraits(DiscipleData data, List<Sifat> traits) {
		ListTag list = new ListTag();
		boolean[] axes = new boolean[Sifat.AXES];
		for (Sifat sifat : traits) {
			if (sifat != null && !axes[sifat.axis()]) {
				axes[sifat.axis()] = true;
				list.add(StringTag.valueOf(sifat.id()));
			}
		}
		tag(data).put("sifat", list);
	}

	/** Swaps {@code from} for {@code to} (same or another axis); false when it does not hold {@code from}. */
	static boolean swap(DiscipleData data, Sifat from, Sifat to) {
		List<Sifat> traits = new ArrayList<>(traits(data));
		int at = traits.indexOf(from);
		if (at < 0) {
			return false;
		}
		traits.set(at, to);
		for (int i = traits.size() - 1; i >= 0; i--) {
			if (i != at && traits.get(i).axis() == to.axis()) {
				traits.remove(i);
			}
		}
		setTraits(data, traits);
		return true;
	}

	// ================================================================== contentment

	public static double contentmentExact(@Nullable DiscipleData data) {
		CompoundTag tag = peek(data);
		return tag == null ? START : Math.max(0.0D, Math.min(100.0D, tag.getDoubleOr("k", START)));
	}

	public static int contentment(@Nullable DiscipleData data) {
		return (int) Math.round(contentmentExact(data));
	}

	static void setContentment(DiscipleData data, double value) {
		tag(data).putDouble("k", Math.max(0.0D, Math.min(100.0D, Double.isFinite(value) ? value : START)));
	}

	// ================================================================== plain numbers

	static long getLong(@Nullable DiscipleData data, String key, long fallback) {
		CompoundTag tag = peek(data);
		return tag == null ? fallback : tag.getLongOr(key, fallback);
	}

	static void putLong(DiscipleData data, String key, long value) {
		tag(data).putLong(key, value);
	}

	static int getInt(@Nullable DiscipleData data, String key, int fallback) {
		CompoundTag tag = peek(data);
		return tag == null ? fallback : tag.getIntOr(key, fallback);
	}

	static void putInt(DiscipleData data, String key, int value) {
		tag(data).putInt(key, value);
	}

	static String getString(@Nullable DiscipleData data, String key) {
		CompoundTag tag = peek(data);
		return tag == null ? "" : tag.getStringOr(key, "");
	}

	static void putString(DiscipleData data, String key, String value) {
		if (value == null || value.isEmpty()) {
			tag(data).remove(key);
		} else {
			tag(data).putString(key, value);
		}
	}

	/** The care gathered on {@code day} so far (0 when the stored care is of another day). */
	static double careOf(@Nullable DiscipleData data, long day) {
		CompoundTag tag = peek(data);
		if (tag == null || tag.getLongOr("rd", Long.MIN_VALUE) != day) {
			return 0.0D;
		}
		return tag.getDoubleOr("rv", 0.0D);
	}

	/** Adds care to today's sum (at most +10 a day from care, the daily cap of DESIGN 36.2.1 then clamps the whole step). */
	static void addCare(DiscipleData data, long today, double amount) {
		CompoundTag tag = tag(data);
		double now = tag.getLongOr("rd", Long.MIN_VALUE) == today ? tag.getDoubleOr("rv", 0.0D) : 0.0D;
		tag.putLong("rd", today);
		tag.putDouble("rv", Math.max(-10.0D, Math.min(10.0D, now + amount)));
		if (amount > 0.0D) {
			tag.putLong("last", today);
		}
	}

	/** Every key of P removed (the test command {@code reset}). */
	static void clear(DiscipleData data) {
		data.extra().remove(SekteIds.EXTRA_P);
	}

	static @Nullable Tag raw(@Nullable DiscipleData data) {
		return peek(data);
	}

	private Hati() {
	}
}
