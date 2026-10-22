package dev.kultivator.dunia.kota;

import dev.kultivator.Kultivator;
import dev.kultivator.harta.SpiritStones;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Nilai Batu (NB, DESIGN 34.1): the price unit of the towns. A Low spirit stone is 1 NB, Mid 9, High 81, Supreme 729:
 * the same 9:1 as the exchange recipes, so no grade is worth more broken than whole (balance change of audit S23, approved
 * "Ubah angkanya"; it was 80/600, and every payment of 600+ NB came as a Supreme stone worth 720 when broken). This is the
 * one NB table of the mod: {@code DimensiData} and {@code RRewards} use it too (audit R17). The player always pays and is
 * paid in real spirit stones; change comes back in lower grades (largest first), so no trade creates stones from nothing.
 * Batu Asal Abadi (the Immortal City's currency) is counted separately.
 */
public final class NilaiBatu {

	public static final long[] VALUE = {1L, 9L, 81L, 729L};

	/** Every NB the player carries (main inventory and off hand). */
	public static long total(ServerPlayer player) {
		long sum = 0L;
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			int grade = SpiritStones.grade(stack);
			if (grade >= 0) {
				sum += VALUE[grade] * stack.getCount();
			}
		}
		return sum;
	}

	/** Stones of each grade the player carries. */
	static long[] counts(ServerPlayer player) {
		long[] counts = new long[4];
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			int grade = SpiritStones.grade(stack);
			if (grade >= 0) {
				counts[grade] += stack.getCount();
			}
		}
		return counts;
	}

	/**
	 * Takes {@code price} NB: low grades first, then the smallest higher stone that covers the rest, the change given back
	 * in lower grades. False (nothing taken) when the player cannot pay.
	 */
	public static boolean pay(ServerPlayer player, long price) {
		if (price <= 0L) {
			return true;
		}
		long[] have = counts(player);
		long wealth = have[0] * VALUE[0] + have[1] * VALUE[1] + have[2] * VALUE[2] + have[3] * VALUE[3];
		if (wealth < price) {
			return false;
		}
		long[] take = new long[4];
		long left = price;
		for (int grade = 0; grade < 4 && left > 0L; grade++) {
			long use = Math.min(have[grade], left / VALUE[grade]);
			take[grade] += use;
			left -= use * VALUE[grade];
		}
		if (left > 0L) {
			// one more stone of the smallest grade that still has one covers the rest
			for (int grade = 0; grade < 4; grade++) {
				if (have[grade] - take[grade] > 0L && VALUE[grade] >= left) {
					take[grade]++;
					left -= VALUE[grade];
					break;
				}
			}
		}
		if (left > 0L) {
			// still short in small coins: break bigger stones from the top
			for (int grade = 3; grade >= 0 && left > 0L; grade--) {
				while (have[grade] - take[grade] > 0L && left > 0L) {
					take[grade]++;
					left -= VALUE[grade];
				}
			}
		}
		if (left > 0L) {
			return false;
		}
		for (int grade = 0; grade < 4; grade++) {
			remove(player, grade, take[grade]);
		}
		if (left < 0L) {
			give(player, -left);
		}
		return true;
	}

	/** Gives {@code amount} NB as stones, largest grade first (to the inventory, the rest dropped at the player). */
	public static void give(ServerPlayer player, long amount) {
		long left = Math.max(0L, amount);
		for (int grade = 3; grade >= 0 && left > 0L; grade--) {
			long n = left / VALUE[grade];
			left -= n * VALUE[grade];
			while (n > 0L) {
				int count = (int) Math.min(64L, n);
				n -= count;
				ItemStack stack = SpiritStones.stack(grade, count);
				if (!player.getInventory().add(stack) && !stack.isEmpty()) {
					Kota.drop(player, stack);
				}
			}
		}
	}

	private static void remove(ServerPlayer player, int grade, long count) {
		long left = count;
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize() && left > 0L; i++) {
			ItemStack stack = inventory.getItem(i);
			if (SpiritStones.grade(stack) == grade) {
				int use = (int) Math.min(left, stack.getCount());
				stack.shrink(use);
				left -= use;
			}
		}
		inventory.setChanged();
	}

	/** "12 NB" in gold. */
	public static MutableComponent text(long nb) {
		return Component.translatable("kultivator.kota.nb", nb).withColor(0xE8C060);
	}

	// ================================================================== Batu Asal Abadi

	private static Item originStone() {
		return BuiltInRegistries.ITEM.getValue(Kultivator.id("immortal_origin_stone"));
	}

	/** Batu Asal Abadi the player carries. */
	public static int origin(ServerPlayer player) {
		Item item = originStone();
		if (item == Items.AIR) {
			return 0;
		}
		int sum = 0;
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				sum += stack.getCount();
			}
		}
		return sum;
	}

	/** Takes {@code count} Batu Asal Abadi; false (nothing taken) when the player has fewer. */
	public static boolean payOrigin(ServerPlayer player, int count) {
		if (count <= 0) {
			return true;
		}
		Item item = originStone();
		if (item == Items.AIR || origin(player) < count) {
			return false;
		}
		int left = count;
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				int use = Math.min(left, stack.getCount());
				stack.shrink(use);
				left -= use;
			}
		}
		inventory.setChanged();
		return true;
	}

	/** Gives {@code count} Batu Asal Abadi. */
	public static void giveOrigin(ServerPlayer player, int count) {
		Item item = originStone();
		if (item == Items.AIR || count <= 0) {
			return;
		}
		int left = count;
		while (left > 0) {
			int n = Math.min(64, left);
			left -= n;
			ItemStack stack = new ItemStack(item, n);
			if (!player.getInventory().add(stack) && !stack.isEmpty()) {
				Kota.drop(player, stack);
			}
		}
	}

	private NilaiBatu() {
	}
}
