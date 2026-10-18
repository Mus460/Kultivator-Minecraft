package dev.kultivator.harta;

import dev.kultivator.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.EnumMap;
import java.util.Map;

/**
 * The items of P7-harta (DESIGN 19.3.2-19.3.4): four spirit stone grades, four beast core grades and the eight spirit
 * herbs. Loaded (and so registered) by {@code Harta.init()} after {@link HartaComponents}. The four new pills live in
 * {@code alchemy.AlchemyItems}.
 */
public final class HartaItems {

	private static final String[] STONE_IDS = {"spirit_stone_low", "spirit_stone_mid", "spirit_stone_high", "spirit_stone_supreme"};
	private static final Rarity[] STONE_RARITY = {Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC};
	private static final String[] CORE_IDS = {"beast_core_low", "beast_core_mid", "beast_core_high", "beast_core_king"};
	private static final Rarity[] CORE_RARITY = {Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC};

	private static final Item[] STONES = new Item[4];
	private static final Item[] CORES = new Item[4];
	private static final Map<HerbKind, Item> HERBS = new EnumMap<>(HerbKind.class);

	public static final Item SPIRIT_STONE_LOW = stone(SpiritStones.LOW);
	public static final Item SPIRIT_STONE_MID = stone(SpiritStones.MID);
	public static final Item SPIRIT_STONE_HIGH = stone(SpiritStones.HIGH);
	public static final Item SPIRIT_STONE_SUPREME = stone(SpiritStones.SUPREME);

	public static final Item BEAST_CORE_LOW = core(BeastCores.LOW);
	public static final Item BEAST_CORE_MID = core(BeastCores.MID);
	public static final Item BEAST_CORE_HIGH = core(BeastCores.HIGH);
	public static final Item BEAST_CORE_KING = core(BeastCores.KING);

	public static final Item SPIRIT_GRASS = herb(HerbKind.SPIRIT_GRASS, Rarity.COMMON);
	public static final Item NINECURL_GINSENG = herb(HerbKind.NINECURL_GINSENG, Rarity.UNCOMMON);
	public static final Item EIGHT_ANGLED_ICE_GRASS = herb(HerbKind.EIGHT_ANGLED_ICE_GRASS, Rarity.UNCOMMON);
	public static final Item PURPLE_FLAME_FLOWER = herb(HerbKind.PURPLE_FLAME_FLOWER, Rarity.UNCOMMON);
	public static final Item ILLUSORY_SPIRIT_HERB = herb(HerbKind.ILLUSORY_SPIRIT_HERB, Rarity.RARE);
	public static final Item GOLD_LIGHTNING_BAMBOO = herb(HerbKind.GOLD_LIGHTNING_BAMBOO, Rarity.RARE);
	public static final Item GINSENG_FRUIT = herb(HerbKind.GINSENG_FRUIT, Rarity.EPIC);
	public static final Item YELLOW_ESSENCE_MUSHROOM = herb(HerbKind.YELLOW_ESSENCE_MUSHROOM, Rarity.UNCOMMON);

	private static Item stone(int grade) {
		Item item = ModItems.register(STONE_IDS[grade], properties -> new SpiritStoneItem(properties, grade),
				new Item.Properties().stacksTo(64).rarity(STONE_RARITY[grade]));
		STONES[grade] = item;
		return item;
	}

	private static Item core(int grade) {
		Item item = ModItems.register(CORE_IDS[grade - 1], properties -> new BeastCoreItem(properties, grade),
				new Item.Properties().stacksTo(16).rarity(CORE_RARITY[grade - 1]));
		CORES[grade - 1] = item;
		return item;
	}

	private static Item herb(HerbKind kind, Rarity rarity) {
		Item item = ModItems.register(kind.id(), properties -> new SpiritHerbItem(properties, kind),
				new Item.Properties().stacksTo(64).rarity(rarity).component(HartaComponents.HERB_AGE, SpiritHerbs.DEFAULT_AGE));
		HERBS.put(kind, item);
		return item;
	}

	/** The stone item of grade 0-3 (clamped). */
	public static Item stoneItem(int grade) {
		return STONES[Math.max(0, Math.min(3, grade))];
	}

	/** The core item of grade 1-4 (clamped). */
	public static Item coreItem(int grade) {
		return CORES[Math.max(1, Math.min(4, grade)) - 1];
	}

	/** The herb item of {@code kind}. */
	public static Item herb(HerbKind kind) {
		return HERBS.get(kind);
	}

	/** Loads the class so the items above are registered. */
	public static void init() {
	}

	private HartaItems() {
	}
}
