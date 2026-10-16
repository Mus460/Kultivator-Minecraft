package dev.kultivator.registry;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Item registration for every module: {@link #register} adds the item to the "Kultivator" creative tab. Modules keep
 * their items in their own classes (e.g. {@code public static final Item X = ModItems.register(...)}) and make sure
 * the class is loaded from their {@code init()}.
 */
public final class ModItems {

	private static final List<Item> TAB_ITEMS = new ArrayList<>();

	public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Kultivator.id("main"));

	public static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Kultivator.id(name));
		Item item = factory.apply(properties.setId(key));
		if (item instanceof BlockItem blockItem) {
			blockItem.registerBlocks(Item.BY_BLOCK, item);
		}
		Registry.register(BuiltInRegistries.ITEM, key, item);
		TAB_ITEMS.add(item);
		return item;
	}

	/** Called once by the spine after every module registered its items. */
	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.kultivator.main"))
				.icon(() -> new ItemStack(TAB_ITEMS.isEmpty() ? Items.AMETHYST_SHARD : TAB_ITEMS.get(0)))
				.displayItems((parameters, output) -> {
					for (Item item : TAB_ITEMS) {
						output.accept(item);
					}
				})
				.build());
	}

	private ModItems() {
	}
}
