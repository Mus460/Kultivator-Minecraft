package dev.kultivator.sekte.turnamen;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.sekte.SekteIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The tournament record of one disciple, {@code DiscipleData.extra().turnamen} (DESIGN 36.2.3, written only by module T):
 * the Elo rating ({@code elo}, start 1000, K from the config), won and lost bouts ({@code menang}, {@code kalah}), the
 * titles won ({@code gelar}: "juara:&lt;season&gt;", "juara_elit:&lt;season&gt;", "juara_antar:&lt;season&gt;"), the best placing
 * ever ({@code terbaik}, 1-based, 0 never placed), the seasons entered ({@code musim}) and the last season ({@code akhir}).
 * <p>
 * The rating is the classic Elo of chess, used here only as a public standing of the sect's disciples on the season
 * board; it changes no gameplay number.
 */
public final class Elo {

	public static final int START = 1000;
	public static final int FLOOR = 100;
	public static final int MAX_TITLES = 12;

	/** The expected score of a rating {@code a} against {@code b} (0..1). */
	public static double expected(int a, int b) {
		return 1.0D / (1.0D + Math.pow(10.0D, (b - a) / 400.0D));
	}

	/** The change for the side rated {@code self} against {@code other} with {@code score} 1 win, 0 loss, 0.5 draw. */
	public static int delta(int self, int other, double score, int k) {
		return (int) Math.round(k * (score - expected(self, other)));
	}

	/** A visitor's rating: the own fighter's rating shifted by 20 per rank step the visitor is stronger. */
	public static int visitor(int ownRating, int ownStep, int visitorStep) {
		return Math.max(FLOOR, ownRating + 20 * (visitorStep - ownStep));
	}

	// ================================================================== the record

	private static @Nullable CompoundTag read(DiscipleData data) {
		return data.extra().getCompound(SekteIds.EXTRA_T).orElse(null);
	}

	private static CompoundTag write(DiscipleData data) {
		return data.extraSection(SekteIds.EXTRA_T);
	}

	public static int rating(DiscipleData data) {
		CompoundTag tag = read(data);
		return tag == null ? START : tag.getIntOr("elo", START);
	}

	public static void setRating(DiscipleData data, int value) {
		write(data).putInt("elo", Math.max(FLOOR, value));
	}

	public static int wins(DiscipleData data) {
		CompoundTag tag = read(data);
		return tag == null ? 0 : tag.getIntOr("menang", 0);
	}

	public static int losses(DiscipleData data) {
		CompoundTag tag = read(data);
		return tag == null ? 0 : tag.getIntOr("kalah", 0);
	}

	/** Best placing ever, 1-based (0 never placed). */
	public static int best(DiscipleData data) {
		CompoundTag tag = read(data);
		return tag == null ? 0 : tag.getIntOr("terbaik", 0);
	}

	public static int seasons(DiscipleData data) {
		CompoundTag tag = read(data);
		return tag == null ? 0 : tag.getIntOr("musim", 0);
	}

	/** The titles won ("juara:3", "juara_elit:6", "juara_antar:7"), oldest first. */
	public static List<String> titles(DiscipleData data) {
		CompoundTag tag = read(data);
		List<String> list = new ArrayList<>();
		if (tag != null) {
			for (Tag each : tag.getListOrEmpty("gelar")) {
				each.asString().ifPresent(list::add);
			}
		}
		return list;
	}

	/** One bout: the rating moves by {@code change}, a win or a loss is counted. */
	public static void bout(DiscipleData data, int change, boolean won) {
		CompoundTag tag = write(data);
		tag.putInt("elo", Math.max(FLOOR, tag.getIntOr("elo", START) + change));
		if (won) {
			tag.putInt("menang", tag.getIntOr("menang", 0) + 1);
		} else {
			tag.putInt("kalah", tag.getIntOr("kalah", 0) + 1);
		}
	}

	/** The end of a season for an entrant: its place (1-based, 0 not placed) and the season number. */
	public static void season(DiscipleData data, int place, int season) {
		CompoundTag tag = write(data);
		if (tag.getIntOr("akhir", 0) != season) {
			tag.putInt("musim", tag.getIntOr("musim", 0) + 1);
			tag.putInt("akhir", season);
		}
		if (place > 0) {
			int best = tag.getIntOr("terbaik", 0);
			if (best == 0 || place < best) {
				tag.putInt("terbaik", place);
			}
		}
	}

	/** Adds a title ("juara:&lt;season&gt;"); the oldest one goes when there are more than {@link #MAX_TITLES}. */
	public static void title(DiscipleData data, String title) {
		CompoundTag tag = write(data);
		ListTag list = tag.getListOrEmpty("gelar").copy();
		list.add(StringTag.valueOf(title));
		while (list.size() > MAX_TITLES) {
			list.remove(0);
		}
		tag.put("gelar", list);
	}

	/** Clears the record (test {@code elo reset}). */
	public static void reset(DiscipleData data) {
		data.extra().remove(SekteIds.EXTRA_T);
	}

	private Elo() {
	}
}
