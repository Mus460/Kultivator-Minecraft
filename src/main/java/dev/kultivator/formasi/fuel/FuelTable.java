package dev.kultivator.formasi.fuel;

import dev.kultivator.formation.ProtectiveFormations;
import dev.kultivator.harta.SpiritStones;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Formation fuel (DESIGN 24.2.7): Daya per item, derived from {@code SpiritStones.FORMATION_TICKS / TICKS_PER_SHARD} so a
 * stone is worth the same as in the Formasi Pelindung (Low 2, Mid 18, High 144, Supreme 1,296); amethyst shard 1, block
 * 4. Beast cores are not fuel.
 */
public final class FuelTable {

	/** Grade code of amethyst. */
	public static final int AMETHYST = -1;
	/** Grade code of a non-fuel item. */
	public static final int NONE = -2;
	public static final int SHARD_DAYA = 1;
	public static final int AMETHYST_BLOCK_DAYA = 4;
	/** Daya of one stone by grade (Low, Mid, High, Supreme). */
	public static final int[] STONE_DAYA = stoneDaya();

	private static int[] stoneDaya() {
		int[] out = new int[SpiritStones.FORMATION_TICKS.length];
		int last = 1;
		for (int grade = 0; grade < out.length; grade++) {
			int ticks = SpiritStones.FORMATION_TICKS[grade];
			out[grade] = ticks > 0 ? Math.max(1, ticks / ProtectiveFormations.TICKS_PER_SHARD) : last * 9;
			last = out[grade];
		}
		return out;
	}

	/** Daya of ONE item of {@code stack} (0 when it is not fuel). */
	public static int daya(ItemStack stack) {
		int grade = grade(stack);
		if (grade == AMETHYST) {
			return stack.is(Items.AMETHYST_BLOCK) ? AMETHYST_BLOCK_DAYA : SHARD_DAYA;
		}
		return grade >= 0 && grade < STONE_DAYA.length ? STONE_DAYA[grade] : 0;
	}

	/** {@link #AMETHYST} (-1), a stone grade 0-3, or {@link #NONE} (-2). */
	public static int grade(ItemStack stack) {
		if (stack.isEmpty()) {
			return NONE;
		}
		if (stack.is(Items.AMETHYST_SHARD) || stack.is(Items.AMETHYST_BLOCK)) {
			return AMETHYST;
		}
		if (SpiritStones.isStone(stack)) {
			return SpiritStones.grade(stack);
		}
		return NONE;
	}

	private FuelTable() {
	}
}
