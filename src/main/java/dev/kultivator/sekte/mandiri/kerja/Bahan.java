package dev.kultivator.sekte.mandiri.kerja;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.build.flow.LumbungOverflow;
import dev.kultivator.sect.event.Duties;
import dev.kultivator.sect.event.Storehouse;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Materials of the disciple's crafts (DESIGN 39.6.7, 39.6.8, 39.6.11): first its own bag ({@code MandiriApi.tas}), then
 * the hall's Lumbung, but only items the effective policy allows ({@code Kebijakan.jatahLumbung}, item ids). Nothing comes
 * from nowhere: what a block takes is written into the block's record ({@link #simpanRekam}) and goes back to where it came
 * from when the block is interrupted early ({@link #kembalikan}); results go into the bag, and what does not fit into the
 * Lumbung ({@code LumbungOverflow}, never lost).
 */
final class Bahan {

	/** What the disciple may use now: bag counts and allowed Lumbung counts per item. */
	record Stok(Map<Item, Integer> tas, Map<Item, Integer> lumbung) {
		int ada(Item item) {
			return tas.getOrDefault(item, 0) + lumbung.getOrDefault(item, 0);
		}

		boolean cukup(Map<Item, Integer> need) {
			for (Map.Entry<Item, Integer> entry : need.entrySet()) {
				if (ada(entry.getKey()) < entry.getValue()) {
					return false;
				}
			}
			return true;
		}
	}

	/** One stack taken for a block, and whether it came from the Lumbung. */
	record Ambil(ItemStack stack, boolean lumbung) {
	}

	/** The disciple's stock; Lumbung items only when {@code penting} accepts them (keeps the Lumbung reads few). */
	static Stok stok(DiscipleEntity e, Predicate<Item> penting) {
		Map<Item, Integer> tas = new HashMap<>();
		for (ItemStack stack : MandiriApi.tas(e.data(), registries(e))) {
			tas.merge(stack.getItem(), stack.getCount(), Integer::sum);
		}
		Map<Item, Integer> lumbung = new HashMap<>();
		UUID owner = e.ownerId();
		if (owner != null) {
			for (Item item : jatah(MandiriApi.kebijakan(e))) {
				if (penting.test(item)) {
					int n = Duties.lumbungCount(owner, stack -> stack.is(item));
					if (n > 0) {
						lumbung.put(item, n);
					}
				}
			}
		}
		return new Stok(tas, lumbung);
	}

	/** The items of the policy's Lumbung allowance (unknown ids are skipped). */
	static List<Item> jatah(Kebijakan kebijakan) {
		List<Item> out = new ArrayList<>();
		for (String id : kebijakan.jatahLumbung()) {
			Identifier key = Identifier.tryParse(id);
			Item item = key == null ? null : BuiltInRegistries.ITEM.getValue(key);
			if (item != null && item != Items.AIR && !out.contains(item)) {
				out.add(item);
			}
		}
		return out;
	}

	/**
	 * Takes {@code n} of {@code item}: the bag first, then the allowed Lumbung. All or nothing: when the stock does not hold
	 * {@code n} everything taken is put back and the list is empty.
	 */
	static List<Ambil> ambil(DiscipleEntity e, Item item, int n) {
		List<Ambil> out = new ArrayList<>();
		HolderLookup.Provider registries = registries(e);
		int left = n;
		while (left > 0) {
			ItemStack part = MandiriApi.ambilTas(e.data(), registries, stack -> stack.is(item), left);
			if (part.isEmpty()) {
				break;
			}
			left -= part.getCount();
			out.add(new Ambil(part, false));
		}
		UUID owner = e.ownerId();
		if (left > 0 && owner != null && jatah(MandiriApi.kebijakan(e)).contains(item)) {
			for (ItemStack part : Storehouse.take(owner, stack -> stack.is(item), left)) {
				left -= part.getCount();
				out.add(new Ambil(part, true));
			}
		}
		if (left > 0) {
			kembalikan(e, out);
			return List.of();
		}
		return out;
	}

	/** Takes every item of {@code need} (all or nothing). */
	static List<Ambil> ambilSemua(DiscipleEntity e, Map<Item, Integer> need) {
		List<Ambil> all = new ArrayList<>();
		for (Map.Entry<Item, Integer> entry : need.entrySet()) {
			List<Ambil> part = ambil(e, entry.getKey(), entry.getValue());
			if (part.isEmpty() && entry.getValue() > 0) {
				kembalikan(e, all);
				return List.of();
			}
			all.addAll(part);
		}
		return all;
	}

	/** Puts taken stacks back where they came from (the Lumbung's overflow when it is full; the bag, else the Lumbung). */
	static void kembalikan(DiscipleEntity e, List<Ambil> taken) {
		UUID owner = e.ownerId();
		for (Ambil a : taken) {
			if (a.stack().isEmpty()) {
				continue;
			}
			if (a.lumbung() && owner != null) {
				ItemStack rest = Storehouse.insert(owner, a.stack());
				if (!rest.isEmpty()) {
					LumbungOverflow.store(owner, rest);
				}
			} else {
				simpan(e, a.stack());
			}
		}
	}

	/** A result of the disciple's work: into its bag, else into the Lumbung; true when it went into the bag. */
	static boolean simpan(DiscipleEntity e, ItemStack stack) {
		if (stack.isEmpty()) {
			return true;
		}
		if (MandiriApi.masukTas(e.data(), registries(e), stack)) {
			return true;
		}
		UUID owner = e.ownerId();
		if (owner != null) {
			LumbungOverflow.store(owner, stack.copy());
		}
		return false;
	}

	/** Writes the taken stacks into a block record (list {@code bahan}) so an interrupted block can give them back. */
	static void simpanRekam(DiscipleEntity e, CompoundTag rec, List<Ambil> taken) {
		RegistryOps<Tag> ops = registries(e).createSerializationContext(NbtOps.INSTANCE);
		ListTag list = new ListTag();
		for (Ambil a : taken) {
			CompoundTag one = new CompoundTag();
			one.store("item", ItemStack.CODEC, ops, a.stack());
			if (a.lumbung()) {
				one.putBoolean("lumbung", true);
			}
			list.add(one);
		}
		rec.put("bahan", list);
	}

	/** The stacks of a block record (created on demand). */
	static List<Ambil> bacaRekam(DiscipleEntity e, CompoundTag rec) {
		RegistryOps<Tag> ops = registries(e).createSerializationContext(NbtOps.INSTANCE);
		ListTag list = rec.getListOrEmpty("bahan");
		List<Ambil> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			CompoundTag one = list.getCompoundOrEmpty(i);
			ItemStack stack = one.read("item", ItemStack.CODEC, ops).orElse(ItemStack.EMPTY);
			if (!stack.isEmpty()) {
				out.add(new Ambil(stack, one.getBooleanOr("lumbung", false)));
			}
		}
		return out;
	}

	/** Free slots of the bag (9 minus the stacks). */
	static int ruangTas(DiscipleEntity e) {
		return Math.max(0, dev.kultivator.sekte.mandiri.api.MandiriIds.TAS_SLOT - MandiriApi.tas(e.data(), registries(e)).size());
	}

	/** Copies of the bag's stacks matching {@code filter}. */
	static List<ItemStack> isiTas(DiscipleEntity e, @Nullable Predicate<ItemStack> filter) {
		List<ItemStack> out = new ArrayList<>();
		for (ItemStack stack : MandiriApi.tas(e.data(), registries(e))) {
			if (filter == null || filter.test(stack)) {
				out.add(stack);
			}
		}
		return out;
	}

	static HolderLookup.Provider registries(DiscipleEntity e) {
		return e.level().registryAccess();
	}

	private Bahan() {
	}
}
