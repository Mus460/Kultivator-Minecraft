package dev.kultivator.beast;

import dev.kultivator.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Items of the spirit beast module. Loaded (and so registered) by {@code SpiritBeasts.init()}. */
public final class BeastItems {

	/** Pil Binatang Roh: awakens a tamed wolf or a fox, later feeds spirit XP to it. */
	public static final Item SPIRIT_BEAST_PILL = ModItems.register("spirit_beast_pill", SpiritBeastPillItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

	/** Loads the class so the items above are registered. */
	public static void init() {
	}

	private BeastItems() {
	}
}
