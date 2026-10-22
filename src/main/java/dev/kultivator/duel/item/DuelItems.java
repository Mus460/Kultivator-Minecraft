package dev.kultivator.duel.item;

import dev.kultivator.Kultivator;
import dev.kultivator.registry.ModItems;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * The duel's two items (DESIGN 35.5.7, module A).
 * <ul>
 * <li>{@code kultivator:pedang_latih}, the wooden training sword: in {@code #minecraft:swords} (data tag, not replacing),
 * 4 attack damage and 1.6 attacks per second like a wooden sword, wood durability; two planks and a stick on the
 * diagonal. The Duel Dummy holds it.</li>
 * <li>{@code kultivator:duel_post}, the red lacquered ring post with a paper lantern: only the model of the client-side
 * ring posts ({@code ItemDisplay}), never in the creative tab and without a recipe.</li>
 * </ul>
 * No ItemStack is made here (lazy stacks, DESIGN rules).
 */
public final class DuelItems {

	public static final Item PEDANG_LATIH = ModItems.register("pedang_latih", Item::new,
			new Item.Properties().sword(ToolMaterial.WOOD, 3.0F, -2.4F));

	/** Registered by hand so it stays out of the creative tab. */
	public static final Item DUEL_POST = registerHidden("duel_post", new Item.Properties().stacksTo(1));

	private static boolean initialised;

	/** Called once from {@code Duel.init} (contract C35.S1); loading the class registers the items. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		Kultivator.LOGGER.debug("Duel items: {}, {}", BuiltInRegistries.ITEM.getKey(PEDANG_LATIH), BuiltInRegistries.ITEM.getKey(DUEL_POST));
	}

	private static Item registerHidden(String name, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Kultivator.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
	}

	private DuelItems() {
	}
}
