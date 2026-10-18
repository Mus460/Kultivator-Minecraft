package dev.kultivator.sect.build.api;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An amount of building materials (DESIGN 21.11): centi-units per {@link Bahan} (index = ordinal, always
 * {@link Bahan#COUNT} long, never negative) plus exact items that are deposited 1:1 and never converted from units
 * (cauldrons, bookshelves, lodestones...). Immutable: the constructor and {@link #centi()} copy the array, the exact map is
 * unmodifiable. Used for bills, reservations, shortfalls and refunds.
 * <p>
 * Tag form (the {@code bill} / {@code exact} keys of the {@code BuildPreview} payload, DESIGN 21.24):
 * {@code bill {<bahan id>: centi}}, {@code exact {<item id>: n}}; zero entries are left out.
 */
public record Bill(long[] centi, Map<Item, Integer> exact) {

	public static final Bill EMPTY = new Bill(new long[Bahan.COUNT], Map.of());

	public Bill {
		long[] copy = new long[Bahan.COUNT];
		if (centi != null) {
			for (int i = 0; i < Math.min(centi.length, Bahan.COUNT); i++) {
				copy[i] = Math.max(0L, centi[i]);
			}
		}
		centi = copy;
		Map<Item, Integer> items = new LinkedHashMap<>();
		if (exact != null) {
			for (Map.Entry<Item, Integer> entry : exact.entrySet()) {
				if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0) {
					items.merge(entry.getKey(), entry.getValue(), Integer::sum);
				}
			}
		}
		exact = Collections.unmodifiableMap(items);
	}

	/** A copy of the centi-units per category. */
	@Override
	public long[] centi() {
		return centi.clone();
	}

	public long centi(Bahan cat) {
		return centi[cat.ordinal()];
	}

	public int exact(Item item) {
		Integer count = exact.get(item);
		return count == null ? 0 : count;
	}

	public boolean isEmpty() {
		if (!exact.isEmpty()) {
			return false;
		}
		for (long each : centi) {
			if (each > 0L) {
				return false;
			}
		}
		return true;
	}

	/** Sum of all categories in centi-units (exact items not counted). */
	public long totalCenti() {
		long sum = 0L;
		for (long each : centi) {
			sum += each;
		}
		return sum;
	}

	public Bill plus(Bill other) {
		long[] sum = centi.clone();
		for (int i = 0; i < Bahan.COUNT; i++) {
			sum[i] += other.centi[i];
		}
		Map<Item, Integer> items = new LinkedHashMap<>(exact);
		other.exact.forEach((item, count) -> items.merge(item, count, Integer::sum));
		return new Bill(sum, items);
	}

	/** This minus {@code other}, every entry clamped at zero (a shortfall: {@code need.minus(have)}). */
	public Bill minus(Bill other) {
		long[] rest = centi.clone();
		for (int i = 0; i < Bahan.COUNT; i++) {
			rest[i] = Math.max(0L, rest[i] - other.centi[i]);
		}
		Map<Item, Integer> items = new LinkedHashMap<>();
		exact.forEach((item, count) -> items.put(item, Math.max(0, count - other.exact(item))));
		return new Bill(rest, items);
	}

	/** True when this covers {@code need} in every category and every exact item. */
	public boolean covers(Bill need) {
		for (int i = 0; i < Bahan.COUNT; i++) {
			if (centi[i] < need.centi[i]) {
				return false;
			}
		}
		for (Map.Entry<Item, Integer> entry : need.exact.entrySet()) {
			if (exact(entry.getKey()) < entry.getValue()) {
				return false;
			}
		}
		return true;
	}

	/** Writes {@code bill} and {@code exact} into {@code tag}. */
	public void writeTo(CompoundTag tag) {
		CompoundTag bill = new CompoundTag();
		for (Bahan cat : Bahan.values()) {
			if (centi[cat.ordinal()] > 0L) {
				bill.putLong(cat.id(), centi[cat.ordinal()]);
			}
		}
		CompoundTag items = new CompoundTag();
		for (Map.Entry<Item, Integer> entry : exact.entrySet()) {
			items.putInt(BuiltInRegistries.ITEM.getKey(entry.getKey()).toString(), entry.getValue());
		}
		tag.put("bill", bill);
		tag.put("exact", items);
	}

	/** Reads the {@code bill} and {@code exact} keys of {@code tag} (unknown categories and items are skipped). */
	public static Bill readFrom(CompoundTag tag) {
		long[] centi = new long[Bahan.COUNT];
		CompoundTag bill = tag.getCompoundOrEmpty("bill");
		for (String key : bill.keySet()) {
			Bahan cat = Bahan.byId(key);
			if (cat != null) {
				centi[cat.ordinal()] = bill.getLongOr(key, 0L);
			}
		}
		Map<Item, Integer> items = new LinkedHashMap<>();
		CompoundTag exact = tag.getCompoundOrEmpty("exact");
		for (String key : exact.keySet()) {
			Identifier id = Identifier.tryParse(key);
			Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
			if (item != null) {
				items.put(item, exact.getIntOr(key, 0));
			}
		}
		return new Bill(centi, items);
	}

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		writeTo(tag);
		return tag;
	}

	public static Bill fromTag(CompoundTag tag) {
		return readFrom(tag);
	}

	public static Builder builder() {
		return new Builder();
	}

	/** Collects amounts (used by dry runs and refunds). */
	public static final class Builder {
		private final long[] centi = new long[Bahan.COUNT];
		private final Map<Item, Integer> exact = new LinkedHashMap<>();

		public Builder add(Bahan cat, long centiUnits) {
			centi[cat.ordinal()] += centiUnits;
			return this;
		}

		public Builder add(Item item, int count) {
			exact.merge(item, count, Integer::sum);
			return this;
		}

		public Builder add(Bill bill) {
			for (int i = 0; i < Bahan.COUNT; i++) {
				centi[i] += bill.centi[i];
			}
			bill.exact.forEach((item, count) -> exact.merge(item, count, Integer::sum));
			return this;
		}

		public Bill build() {
			return new Bill(centi, exact);
		}
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Bill bill && Arrays.equals(centi, bill.centi) && exact.equals(bill.exact);
	}

	@Override
	public int hashCode() {
		return 31 * Arrays.hashCode(centi) + exact.hashCode();
	}

	@Override
	public String toString() {
		return "Bill" + Arrays.toString(centi) + exact;
	}
}
