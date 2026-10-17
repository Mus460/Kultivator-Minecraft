package dev.kultivator.alchemy;

import dev.kultivator.registry.ModItems;
import net.minecraft.world.item.Item;

import java.util.EnumMap;
import java.util.Map;

/** The pill items of the alchemy module. Loaded (and so registered) by {@code Alchemy.init()}. */
public final class AlchemyItems {

	private static final Map<PillKind, Item> PILLS = new EnumMap<>(PillKind.class);

	public static final Item QI_GATHERING_PILL = register(PillKind.QI_GATHERING);
	public static final Item QI_RECOVERY_PILL = register(PillKind.QI_RECOVERY);
	public static final Item FOUNDATION_PILL = register(PillKind.FOUNDATION);
	public static final Item GOLDEN_CORE_PILL = register(PillKind.GOLDEN_CORE);
	public static final Item HEART_CALMING_PILL = register(PillKind.HEART_CALMING);
	public static final Item HEALING_PILL = register(PillKind.HEALING);
	public static final Item TRIBULATION_WARDING_PILL = register(PillKind.TRIBULATION_WARDING);
	public static final Item SOUL_NURTURING_PILL = register(PillKind.SOUL_NURTURING);
	public static final Item IMMORTAL_PILL = register(PillKind.IMMORTAL);

	private static Item register(PillKind kind) {
		Item item = ModItems.register(kind.id(), properties -> new PillItem(properties, kind), new Item.Properties()
				.stacksTo(16)
				.rarity(kind.rarity())
				.component(AlchemyComponents.PILL_QUALITY, PillQuality.MID.ordinal()));
		PILLS.put(kind, item);
		return item;
	}

	/** The item of {@code kind}. */
	public static Item get(PillKind kind) {
		return PILLS.get(kind);
	}

	/** Loads the class so the items above are registered. */
	public static void init() {
	}

	private AlchemyItems() {
	}
}
