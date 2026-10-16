package dev.kultivator.item;

import dev.kultivator.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Items of the spiritual-roots module. Loaded (and so registered) by {@code SpiritRoots.init()}. */
public final class RootItems {

	/** Pil Pembersih Sumsum: raises the root grade one step, may wash out one element. */
	public static final Item MARROW_CLEANSING_PILL = ModItems.register("marrow_cleansing_pill", MarrowCleansingPillItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

	/** Loads the class so the items above are registered. */
	public static void init() {
	}

	private RootItems() {
	}
}
