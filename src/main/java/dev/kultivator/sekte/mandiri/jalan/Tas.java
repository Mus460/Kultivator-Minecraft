package dev.kultivator.sekte.mandiri.jalan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.talisman.TalismanKind;
import dev.kultivator.talisman.Talismans;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.UUID;

/**
 * The disciple's bag of nine slots (DESIGN 39.6.7) as 39C uses it: free room, the stacks a journey brings (made lazily
 * from an item id at arrival, 39.6.11 rule 2), the mark {@code kultivator:beli_murid} on personal purchases (39D registers
 * the component, C39.D2; until it exists the mark is a custom-data tag of the same name) and the travel talismans.
 * Item ids are full ids ({@code kultivator:healing_pill}); spirit herbs carry their age ({@code kultivator:herb@100}),
 * beast cores their grade ({@code kultivator:beast_core@2}), technique slips their spec ({@code kultivator:jade_slip#<spec>}).
 */
public final class Tas {

	/** The fallback custom-data key of the purchase mark. */
	public static final String TANDA_BELI = "kultivator_beli_murid";
	private static final Identifier ID_BELI = Kultivator.id("beli_murid");
	private static final Identifier ID_BUATAN = Kultivator.id("buatan_murid");

	/** Free slots of the bag. */
	public static int ruang(DiscipleData data, HolderLookup.Provider registries) {
		return Math.max(0, MandiriIds.TAS_SLOT - MandiriApi.tas(data, registries).size());
	}

	/** True when a stack of the bag matches {@code itemId} (herbs by kind only). */
	public static int jumlah(DiscipleData data, HolderLookup.Provider registries, String itemId) {
		int n = 0;
		for (ItemStack stack : MandiriApi.tas(data, registries)) {
			if (cocok(stack, itemId)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** Does {@code stack} show item id {@code itemId}? */
	public static boolean cocok(ItemStack stack, String itemId) {
		if (stack == null || stack.isEmpty() || itemId == null) {
			return false;
		}
		String id = itemId;
		int at = id.indexOf('@');
		if (at >= 0) {
			id = id.substring(0, at);
		}
		if (id.endsWith(":herb")) {
			return SpiritHerbs.isHerb(stack);
		}
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id);
	}

	/** True while the bag holds a talisman of {@code kind}. */
	public static boolean adaJimat(DiscipleData data, HolderLookup.Provider registries, TalismanKind kind) {
		Item item = Talismans.item(kind);
		if (item == null) {
			return false;
		}
		for (ItemStack stack : MandiriApi.tas(data, registries)) {
			if (stack.is(item)) {
				return true;
			}
		}
		return false;
	}

	/** Uses up one talisman of {@code kind} from the bag; false when there is none. */
	public static boolean pakaiJimat(DiscipleData data, HolderLookup.Provider registries, TalismanKind kind) {
		Item item = Talismans.item(kind);
		return item != null && !MandiriApi.ambilTas(data, registries, stack -> stack.is(item), 1).isEmpty();
	}

	// ================================================================== making stacks (only at arrival)

	/** The band (0-8) a ware is made for: the disciple's realm. */
	public static int band(DiscipleData data) {
		return Math.max(0, Math.min(Harga.BANDS - 1, data.realm().ordinal()));
	}

	/**
	 * Makes {@code n} items of {@code itemId} for a disciple of {@code band}: town wares through their own maker (a
	 * talisman of the band's power, an herb of its age), anything else plain from the registry. Empty when the item does
	 * not exist (its module off).
	 */
	public static ItemStack buat(String itemId, int n, int band, RandomSource random) {
		if (itemId == null || itemId.isEmpty() || n <= 0) {
			return ItemStack.EMPTY;
		}
		String id = itemId;
		int hash = id.indexOf('#');
		if (hash >= 0) {
			// a technique slip: kultivator:jade_slip#<spec id>
			ItemStack slip = dev.kultivator.technique.source.JadeSlips.stack(id.substring(hash + 1));
			return slip == null ? ItemStack.EMPTY : slip;
		}
		int age = -1;
		int at = id.indexOf('@');
		if (at >= 0) {
			try {
				age = Integer.parseInt(id.substring(at + 1));
			} catch (NumberFormatException ignored) {
				age = 100;
			}
			id = id.substring(0, at);
		}
		Identifier key = Identifier.tryParse(id);
		if (key == null) {
			return ItemStack.EMPTY;
		}
		if (Kultivator.MOD_ID.equals(key.getNamespace())) {
			String path = key.getPath();
			if ("beast_core".equals(path)) {
				dev.kultivator.data.Element[] all = dev.kultivator.data.Element.values();
				ItemStack core = dev.kultivator.harta.BeastCores.create(all[random.nextInt(all.length)], Math.max(1, Math.min(4, age < 0 ? 1 : age)));
				if (!core.isEmpty()) {
					core.setCount(Math.min(core.getMaxStackSize(), n));
				}
				return core;
			}
			if ("herb".equals(path)) {
				int a = Math.max(10, age < 0 ? 100 : age);
				ItemStack herb = SpiritHerbs.randomFor(null, random, a, a);
				if (!herb.isEmpty()) {
					herb.setCount(Math.min(herb.getMaxStackSize(), n));
				}
				return herb;
			}
			Harga.Ware ware = Harga.byId(Harga.PILLS, path);
			if (ware == null) {
				ware = Harga.byId(Harga.TREASURES, path);
			}
			if (ware != null && ware.make() != null) {
				ItemStack made = ware.make().make(random, band);
				if (!made.isEmpty()) {
					made.setCount(Math.min(made.getMaxStackSize(), n));
				}
				return made;
			}
		}
		Item item = BuiltInRegistries.ITEM.getValue(key);
		if (item == null || item == Items.AIR) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(item);
		stack.setCount(Math.min(stack.getMaxStackSize(), n));
		return stack;
	}

	// ================================================================== marks (C39.D2)

	/** Marks a personal purchase {@code kultivator:beli_murid = soul}: never sold again by a disciple (39.6.11 rule 6). */
	public static void tandaiBeli(ItemStack stack, UUID soul) {
		if (stack == null || stack.isEmpty() || soul == null) {
			return;
		}
		if (!setKomponen(stack, ID_BELI, soul)) {
			CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(TANDA_BELI, soul.toString()));
		}
	}

	/** True for a disciple's own work ({@code kultivator:buatan_murid}, set by 39D). */
	public static boolean buatanMurid(ItemStack stack) {
		DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(ID_BUATAN);
		return type != null && stack != null && stack.has(type);
	}

	/** True for a stack a disciple bought (either form of the mark). */
	public static boolean dibeliMurid(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(ID_BELI);
		if (type != null && stack.has(type)) {
			return true;
		}
		CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
		return custom != null && custom.copyTag().contains(TANDA_BELI);
	}

	/** Sets component {@code id} (value type unknown here: UUID or String) from the soul; false when it does not exist. */
	@SuppressWarnings("unchecked")
	private static boolean setKomponen(ItemStack stack, Identifier id, UUID soul) {
		DataComponentType<?> raw = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
		if (raw == null || raw.codec() == null) {
			return false;
		}
		DataComponentType<Object> type = (DataComponentType<Object>) raw;
		Codec<Object> codec = type.codec();
		for (Tag form : List.of(UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, soul).result().orElse(new IntArrayTag(new int[4])),
				StringTag.valueOf(soul.toString()))) {
			DataResult<Object> parsed = codec.parse(NbtOps.INSTANCE, form);
			Object value = parsed.result().orElse(null);
			if (value != null) {
				stack.set(type, value);
				return true;
			}
		}
		return false;
	}

	private Tas() {
	}
}
