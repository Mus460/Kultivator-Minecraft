package dev.kultivator.disciple;

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
 */
public final class DiscipleNames {

	/** How many names each language lists (keys {@code kultivator.disciples.name.0} .. {@code COUNT - 1}). */
	public static final int COUNT = 36;
	private static final String PREFIX = "kultivator.disciples.name.";

	/** The translation key of name {@code index}. */
	public static String key(int index) {
		return PREFIX + Math.floorMod(index, COUNT);
	}

	/** The name {@code index} as a translatable component. */
	public static MutableComponent name(int index) {
		return Component.translatable(key(index));
	}

	/**
	 * A random name that none of {@code taken} (names of the master's other disciples) already carries; any name when
	 * all are taken.
	 */
	public static MutableComponent pick(RandomSource random, Collection<Component> taken) {
		List<Integer> free = new ArrayList<>();
		for (int i = 0; i < COUNT; i++) {
			if (!isTaken(key(i), taken)) {
				free.add(i);
			}
		}
		int index = free.isEmpty() ? random.nextInt(COUNT) : free.get(random.nextInt(free.size()));
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
