package dev.kultivator.sect.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Lumbung Sekte, the hall's 27-slot virtual storehouse (DESIGN 20.3.9): duties, expeditions and the forbidden ground put
 * goods in; the master takes them out by clicking on the Tugas tablet (no container menu, no block). What does not fit
 * is lost (the caller warns). Kept in the owner's {@code HallData} section {@code activity.storehouse}.
 */
public final class Storehouse {

	public static final int SLOTS = 27;
	private static final String KEY = "storehouse";

	/** Puts {@code stack} into the owner's storehouse and returns what did not fit (empty when everything fit). */
	public static ItemStack insert(UUID owner, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack[] slots = load(owner);
		if (slots == null) {
			return stack;
		}
		ItemStack rest = stack.copy();
		for (int i = 0; i < SLOTS && !rest.isEmpty(); i++) {
			ItemStack slot = slots[i];
			if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, rest) && slot.getCount() < slot.getMaxStackSize()) {
				int move = Math.min(rest.getCount(), slot.getMaxStackSize() - slot.getCount());
				slot.grow(move);
				rest.shrink(move);
			}
		}
		for (int i = 0; i < SLOTS && !rest.isEmpty(); i++) {
			if (slots[i].isEmpty()) {
				int move = Math.min(rest.getCount(), rest.getMaxStackSize());
				slots[i] = rest.copyWithCount(move);
				rest.shrink(move);
			}
		}
		save(owner, slots);
		return rest.isEmpty() ? ItemStack.EMPTY : rest;
	}

	/** Takes the whole stack of {@code slot} out (empty when there is none). */
	static ItemStack take(UUID owner, int slot) {
		ItemStack[] slots = load(owner);
		if (slots == null || slot < 0 || slot >= SLOTS || slots[slot].isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack taken = slots[slot];
		slots[slot] = ItemStack.EMPTY;
		save(owner, slots);
		return taken;
	}

	/** How many items match {@code filter}. */
	static int count(UUID owner, Predicate<ItemStack> filter) {
		ItemStack[] slots = load(owner);
		int count = 0;
		if (slots != null) {
			for (ItemStack slot : slots) {
				if (!slot.isEmpty() && filter.test(slot)) {
					count += slot.getCount();
				}
			}
		}
		return count;
	}

	static int count(UUID owner, Item item) {
		return count(owner, stack -> stack.is(item));
	}

	/** Removes up to {@code amount} items matching {@code filter}; returns how many were removed. */
	static int remove(UUID owner, Predicate<ItemStack> filter, int amount) {
		ItemStack[] slots = load(owner);
		if (slots == null || amount <= 0) {
			return 0;
		}
		int left = amount;
		for (int i = 0; i < SLOTS && left > 0; i++) {
			ItemStack slot = slots[i];
			if (!slot.isEmpty() && filter.test(slot)) {
				int take = Math.min(left, slot.getCount());
				slot.shrink(take);
				left -= take;
				if (slot.isEmpty()) {
					slots[i] = ItemStack.EMPTY;
				}
			}
		}
		if (left != amount) {
			save(owner, slots);
		}
		return amount - left;
	}

	/** A copy of every slot (27 entries, empty stacks for free slots), or an empty list without a server. */
	static List<ItemStack> contents(UUID owner) {
		ItemStack[] slots = load(owner);
		List<ItemStack> list = new ArrayList<>(SLOTS);
		if (slots != null) {
			for (ItemStack slot : slots) {
				list.add(slot.copy());
			}
		}
		return list;
	}

	/** The slots as sent to the client: {@code [{slot, item}]} with registry-aware item tags. */
	static ListTag encodeForClient(UUID owner) {
		ListTag list = new ListTag();
		ItemStack[] slots = load(owner);
		RegistryOps<Tag> ops = EventUtil.ops();
		if (slots == null || ops == null) {
			return list;
		}
		for (int i = 0; i < SLOTS; i++) {
			if (!slots[i].isEmpty()) {
				CompoundTag entry = new CompoundTag();
				entry.putInt("slot", i);
				entry.store("item", ItemStack.CODEC, ops, slots[i]);
				list.add(entry);
			}
		}
		return list;
	}

	private static ItemStack[] load(UUID owner) {
		RegistryOps<Tag> ops = EventUtil.ops();
		if (ops == null) {
			return null;
		}
		ItemStack[] slots = new ItemStack[SLOTS];
		java.util.Arrays.fill(slots, ItemStack.EMPTY);
		for (Tag tag : EventUtil.section(owner).getListOrEmpty(KEY)) {
			if (tag instanceof CompoundTag entry) {
				int slot = entry.getIntOr("slot", -1);
				if (slot >= 0 && slot < SLOTS) {
					slots[slot] = entry.read("item", ItemStack.CODEC, ops).orElse(ItemStack.EMPTY);
				}
			}
		}
		return slots;
	}

	private static void save(UUID owner, ItemStack[] slots) {
		RegistryOps<Tag> ops = EventUtil.ops();
		if (ops == null) {
			return;
		}
		ListTag list = new ListTag();
		for (int i = 0; i < SLOTS; i++) {
			if (!slots[i].isEmpty()) {
				CompoundTag entry = new CompoundTag();
				entry.putInt("slot", i);
				entry.store("item", ItemStack.CODEC, ops, slots[i]);
				list.add(entry);
			}
		}
		EventUtil.section(owner).put(KEY, list);
		EventUtil.dirty();
	}

	private Storehouse() {
	}
}
