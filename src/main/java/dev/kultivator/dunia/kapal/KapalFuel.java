package dev.kultivator.dunia.kapal;

import dev.kultivator.Kultivator;
import dev.kultivator.abadi.Abadi;
import dev.kultivator.abadi.AbadiItems;
import dev.kultivator.harta.SpiritStones;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The fuel of the hold (DESIGN 34.5.15): one item burns for a while, the next is taken when it runs out, the cheapest
 * first. Low stone 60 s, Mid 10 min, High 2 h, Supreme 24 h, Batu Meteor Roh 4 min (module L's item, found by its id so
 * nothing of L is imported), Batu Asal Abadi 6 h but only in Alam Abadi. {@code fuelScale} is applied by the ship.
 */
public final class KapalFuel {

	public static final long TICKS_LOW = 60L * 20L;
	public static final long TICKS_MID = 10L * 60L * 20L;
	public static final long TICKS_HIGH = 2L * 60L * 60L * 20L;
	public static final long TICKS_SUPREME = 24L * 60L * 60L * 20L;
	public static final long TICKS_METEOR = 4L * 60L * 20L;
	public static final long TICKS_ORIGIN = 6L * 60L * 60L * 20L;
	private static final Identifier METEOR = Kultivator.id("batu_meteor_roh");

	/** Ticks of flight one item of {@code stack} gives in {@code dimension} (0: not a fuel here). */
	public static long ticks(ItemStack stack, ResourceKey<Level> dimension) {
		if (stack == null || stack.isEmpty()) {
			return 0L;
		}
		int grade = SpiritStones.grade(stack);
		if (grade >= 0) {
			return switch (grade) {
				case SpiritStones.LOW -> TICKS_LOW;
				case SpiritStones.MID -> TICKS_MID;
				case SpiritStones.HIGH -> TICKS_HIGH;
				default -> TICKS_SUPREME;
			};
		}
		Item item = stack.getItem();
		if (METEOR.equals(BuiltInRegistries.ITEM.getKey(item))) {
			return TICKS_METEOR;
		}
		if (item == AbadiItems.IMMORTAL_ORIGIN_STONE) {
			return Abadi.IMMORTAL_LEVEL.equals(dimension) ? TICKS_ORIGIN : 0L;
		}
		return 0L;
	}

	/** True when {@code stack} is a fuel anywhere (the hold's tooltip line). */
	public static boolean isFuel(ItemStack stack) {
		return ticks(stack, Abadi.IMMORTAL_LEVEL) > 0L;
	}

	/**
	 * Takes the cheapest fuel item of {@code hold} that burns in {@code dimension}: returns its ticks, or 0 when the hold
	 * has none.
	 */
	public static long take(Container hold, ResourceKey<Level> dimension) {
		int best = -1;
		long bestTicks = Long.MAX_VALUE;
		for (int i = 0; i < hold.getContainerSize(); i++) {
			long t = ticks(hold.getItem(i), dimension);
			if (t > 0L && t < bestTicks) {
				best = i;
				bestTicks = t;
			}
		}
		if (best < 0) {
			return 0L;
		}
		hold.removeItem(best, 1);
		hold.setChanged();
		return bestTicks;
	}

	/** Every tick of fuel the hold carries for {@code dimension}, plus {@code burning} (for the helm's "≈ 3 j 20 m"). */
	public static long total(Container hold, ResourceKey<Level> dimension, long burning) {
		long sum = Math.max(0L, burning);
		for (int i = 0; i < hold.getContainerSize(); i++) {
			ItemStack stack = hold.getItem(i);
			long t = ticks(stack, dimension);
			if (t > 0L) {
				sum += t * stack.getCount();
			}
		}
		return sum;
	}

	private KapalFuel() {
	}
}
