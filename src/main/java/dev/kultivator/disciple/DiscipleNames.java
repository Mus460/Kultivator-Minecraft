package dev.kultivator.disciple;

import dev.kultivator.sosok.Sosok;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Xianxia-style names for new disciples. The names live in the language files ({@code kultivator.disciples.name.<n>}),
 * so a disciple's name is a translatable component: every player reads it in their own language. A name is picked once
 * at recruitment and kept as the custom name (a name tag replaces it).
 * <p>
 * Names follow the disciple's sex (DESIGN 40.2.5): the 36 old names 0-35 are given a sex by the fixed table
 * {@link #FEMALE_OLD}, and names 36-71 (fragment {@code sosok}) add 20 women's names (36-55) and 16 men's names
 * (56-71), so each pool holds 36 names.
 */
public final class DiscipleNames {

	/** How many names each language lists (keys {@code kultivator.disciples.name.0} .. {@code TOTAL - 1}). */
	public static final int TOTAL = 72;
	/** Names in the men's pool. */
	public static final int MALE_COUNT = 36;
	/** Names in the women's pool. */
	public static final int FEMALE_COUNT = 36;
	/** The old names 0-35 that are women's names ("Su Qingyi", "Han Muxue", ... "Yan Zhiqiu", "An Ruyi"). */
	public static final int[] FEMALE_OLD = {1, 3, 5, 6, 9, 12, 15, 17, 20, 22, 24, 27, 29, 31, 32, 35};
	/** First and last of the new women's names. */
	public static final int FEMALE_NEW_FIRST = 36;
	public static final int FEMALE_NEW_LAST = 55;
	/** First and last of the new men's names. */
	public static final int MALE_NEW_FIRST = 56;
	public static final int MALE_NEW_LAST = 71;
	private static final String PREFIX = "kultivator.disciples.name.";

	/** Name indexes of each pool, men first ({@link Sosok#LAKI} = 0, {@link Sosok#PEREMPUAN} = 1). */
	private static final int[][] POOL = new int[2][];

	static {
		boolean[] female = new boolean[TOTAL];
		for (int i : FEMALE_OLD) {
			female[i] = true;
		}
		for (int i = FEMALE_NEW_FIRST; i <= FEMALE_NEW_LAST; i++) {
			female[i] = true;
		}
		int[] men = new int[MALE_COUNT];
		int[] women = new int[FEMALE_COUNT];
		int m = 0;
		int w = 0;
		for (int i = 0; i < TOTAL; i++) {
			if (female[i]) {
				women[w++] = i;
			} else {
				men[m++] = i;
			}
		}
		if (m != MALE_COUNT || w != FEMALE_COUNT) {
			throw new IllegalStateException("Disciple name pools are " + m + " men and " + w + " women");
		}
		POOL[Sosok.LAKI] = men;
		POOL[Sosok.PEREMPUAN] = women;
	}

	/** The translation key of name {@code index}. */
	public static String key(int index) {
		return PREFIX + Math.floorMod(index, TOTAL);
	}

	/** The name {@code index} as a translatable component. */
	public static MutableComponent name(int index) {
		return Component.translatable(key(index));
	}

	/** The sex ({@link Sosok#LAKI} / {@link Sosok#PEREMPUAN}) of name {@code index}. */
	public static int kelamin(int index) {
		int i = Math.floorMod(index, TOTAL);
		for (int f : POOL[Sosok.PEREMPUAN]) {
			if (f == i) {
				return Sosok.PEREMPUAN;
			}
		}
		return Sosok.LAKI;
	}

	/** The sex of a disciple name key, or -1 when {@code key} is not one of these names. */
	public static int kelaminKunci(String key) {
		if (key == null || !key.startsWith(PREFIX)) {
			return -1;
		}
		try {
			int index = Integer.parseInt(key.substring(PREFIX.length()));
			return index >= 0 && index < TOTAL ? kelamin(index) : -1;
		} catch (NumberFormatException exception) {
			return -1;
		}
	}

	/**
	 * A random name of either sex that none of {@code taken} (names of the master's other disciples) already carries;
	 * any name when all are taken. Callers that know the sex use {@link #pick(RandomSource, Collection, int)}; a body
	 * made from a name picked here takes the name's sex ({@code SosokGen.dariTubuhLama}).
	 */
	public static MutableComponent pick(RandomSource random, Collection<Component> taken) {
		return pickFrom(random, taken, null);
	}

	/** A random free name of sex {@code kelamin} (40.2.5); any name of that sex when all are taken. */
	public static MutableComponent pick(RandomSource random, Collection<Component> taken, int kelamin) {
		return pickFrom(random, taken, POOL[kelamin == Sosok.PEREMPUAN ? Sosok.PEREMPUAN : Sosok.LAKI]);
	}

	private static MutableComponent pickFrom(RandomSource random, Collection<Component> taken, int[] pool) {
		List<Integer> free = new ArrayList<>();
		int size = pool == null ? TOTAL : pool.length;
		for (int n = 0; n < size; n++) {
			int i = pool == null ? n : pool[n];
			if (!isTaken(key(i), taken)) {
				free.add(i);
			}
		}
		int index;
		if (!free.isEmpty()) {
			index = free.get(random.nextInt(free.size()));
		} else {
			index = pool == null ? random.nextInt(TOTAL) : pool[random.nextInt(pool.length)];
		}
		return name(index);
	}

	private static boolean isTaken(String key, Collection<Component> taken) {
		for (Component name : taken) {
			if (name != null && name.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key)) {
				return true;
			}
		}
		return false;
	}

	private DiscipleNames() {
	}
}
