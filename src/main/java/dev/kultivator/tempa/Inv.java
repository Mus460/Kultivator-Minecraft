package dev.kultivator.tempa;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Counting and taking items in a container or a player's inventory (forge slots, ritual stones). */
final class Inv {

	/** How many items in {@code container} match. */
	static int count(Container container, Predicate<ItemStack> match) {
		int total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && match.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** Removes {@code amount} matching items (as many as there are); true when all were there. */
	static boolean take(Container container, Predicate<ItemStack> match, int amount) {
		int left = amount;
		for (int i = 0; i < container.getContainerSize() && left > 0; i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && match.test(stack)) {
				int used = Math.min(left, stack.getCount());
				stack.shrink(used);
				left -= used;
				if (stack.isEmpty()) {
					container.setItem(i, ItemStack.EMPTY);
				}
			}
		}
		container.setChanged();
		return left <= 0;
	}

	/** The first matching stack (not a copy), or EMPTY. */
	static ItemStack first(Container container, Predicate<ItemStack> match) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && match.test(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** The index of the first matching stack, or -1. */
	static int firstIndex(Container container, Predicate<ItemStack> match) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && match.test(stack)) {
				return i;
			}
		}
		return -1;
	}

	static int count(ServerPlayer player, Predicate<ItemStack> match) {
		return count(player.getInventory(), match);
	}

	static boolean take(ServerPlayer player, Predicate<ItemStack> match, int amount) {
		Inventory inventory = player.getInventory();
		return take(inventory, match, amount);
	}

	/** Gives the stack to the player, dropping what does not fit. */
	static void give(ServerPlayer player, ItemStack stack) {
		if (!stack.isEmpty()) {
			player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		}
	}

	private Inv() {
	}
}
