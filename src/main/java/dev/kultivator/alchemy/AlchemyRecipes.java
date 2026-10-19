package dev.kultivator.alchemy;

import dev.kultivator.beast.BeastItems;
import dev.kultivator.data.Element;
import dev.kultivator.harta.BeastCoreItem;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.harta.HartaItems;
import dev.kultivator.harta.HerbKind;
import dev.kultivator.harta.SpiritHerbItem;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.harta.SpiritStoneItem;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.item.MarrowCleansingPillItem;
import dev.kultivator.item.RootItems;
import dev.kultivator.sect.event.SectEventItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The alchemy recipe table (DESIGN sections 9 and 19.3.7): each recipe is an unordered multiset of items refined into
 * pills; phase 7 adds spirit herbs as ingredients, a required beast core and four alternative recipes.
 * <pre>
 * qi_gathering        Glowstone Dust + Sugar + Glow Berries                                   -> 2 Qi Gathering Pills
 * qi_recovery         Amethyst Shard + Honeycomb + Sweet Berries                              -> 2 Qi Recovery Pills
 * healing             Glistering Melon Slice + Golden Carrot + Sweet Berries + Kelp           -> 2 Healing Pills
 * healing_ginseng     Ninecurl Spirit Ginseng + Golden Carrot + Glistering Melon Slice        -> 3 Healing Pills
 * heart_calming       Honeycomb + Lily of the Valley + Cocoa Beans + Glow Berries             -> 2 Heart Calming Pills
 * spirit_beast        Bone + Glow Berries + Amethyst Shard + Golden Carrot                    -> 2 Spirit Beast Pills
 * toxin_purging       Eight-Angled Ice Grass + Honey Bottle + Spirit Grass                    -> 2 Toxin Purging Pills
 * foundation          Golden Carrot + Glowstone Dust + Amethyst Shard + Bone Meal             -> 2 Foundation Pills
 * marrow_cleansing    Glistering Melon Slice + Ghast Tear + Golden Apple + Amethyst Shard + Bone Meal -> 2 Marrow Pills
 * enlightenment       Illusionary Spirit Herb + Glow Berries + Amethyst Shard                 -> 2 Enlightenment Pills
 * golden_core         Gold Ingot + Blaze Powder + Golden Apple + Amethyst Shard               -> 2 Golden Core Pills
 * golden_core_flame   Purple Flame Flower + Gold Ingot + Golden Apple                         -> 2 Golden Core Pills
 * bone_tempering      Bone Block + Iron Ingot + Golden Carrot + an Earth beast core Mid+      -> 2 Bone Tempering Pills
 * tribulation_warding Copper Ingot + Phantom Membrane + Amethyst Shard + Glowstone Dust + Iron Ingot -> 2 Warding Pills
 * tribulation_warding_bamboo Gold Lightning Bamboo + Copper Ingot + Phantom Membrane + Iron Ingot -> 2 Warding Pills
 * dao_insight         Illusionary Spirit Herb + Yellow Essence Mushroom + Echo Shard          -> 1 Dao Insight Pill
 * soul_nurturing      Ghast Tear + Echo Shard + Chorus Fruit + Nether Wart                    -> 2 Soul Nurturing Pills
 * soul_nurturing_fruit Ginseng Fruit + Ghast Tear + Echo Shard + Chorus Fruit                 -> 3 Soul Nurturing Pills
 * immortal            Nether Star + Totem of Undying + Enchanted Golden Apple + Echo Shard + Heart of the Sea -> 1 Immortal Pill
 * phase 8 (DESIGN 25.8.1, alternative recipes fed by the secret realms; module A items looked up by registry id):
 * foundation_herba    3 Herba Fondasi + Healing Pill                                          -> 1 Foundation Pill
 * soul_nurturing_wood Kayu Pemelihara Jiwa + Ghast Tear + Glow Berries                        -> 3 Soul Nurturing Pills
 * bone_tempering_blood Darah Dewa Kuno + Bone Block + Golden Carrot + an Earth beast core Mid+ -> 3 Bone Tempering Pills
 * tribulation_warding_essence Esensi Energi Makam + Echo Shard + Golden Apple                 -> 2 Warding Pills
 * mata_terang         Illusionary Spirit Herb + Spider Eye + Glow Berries                     -> 2 Pil Mata Terang
 * </pre>
 * The table is built on first use (after every module registered its items); a phase-8 recipe whose item is not
 * registered is left out.
 * Matching a pot ({@link #analyze}): (1) up to {@link #MAX_STONE_CATALYSTS} spirit stones are catalysts; (2) one beast
 * core is a catalyst unless the recipe requires a core; (3) the rest must equal a recipe's ingredients plus at most one
 * extra spirit herb, the auxiliary herb (the oldest one the recipe does not need). An exact match wins over one with an
 * auxiliary herb.
 */
public final class AlchemyRecipes {

	/** Colour of steam / sparkles for a mixture that matches no recipe. */
	public static final int MURKY_COLOR = 0x6B6B5A;
	private static final int DEFAULT_INGREDIENT_COLOR = 0xC8C8C8;
	/** At most this many spirit stones count as catalysts. */
	public static final int MAX_STONE_CATALYSTS = 2;

	/** A beast core a recipe requires: its element and lowest grade (1-4). */
	public record CoreNeed(Element element, int minGrade) {
		public boolean accepts(@Nullable Element core, int grade) {
			return core == element && grade >= minGrade;
		}
	}

	/**
	 * One recipe. {@code xp} is the alchemy XP for a Low success (+20% per quality grade above Low); {@code count} the pills made (a Perfect refining of a
	 * pill without quality, the Spirit Beast pill, gives one more). {@code core} is a required beast core, or null.
	 */
	public record Recipe(String id, Supplier<Item> output, int count, Map<Item, Integer> ingredients, int color, double xp,
						 @Nullable CoreNeed core) {

		public Item outputItem() {
			return output.get();
		}

		/** The output is one of this module's pills (it carries a quality). */
		public boolean isPill() {
			return output.get() instanceof PillItem;
		}

		/** The output carries a {@code pill_quality}: the module's pills and, since phase 7, the Marrow Cleansing Pill. */
		public boolean carriesQuality() {
			return isPill() || output.get() instanceof MarrowCleansingPillItem;
		}

		public boolean matches(Map<Item, Integer> contents) {
			return core == null && ingredients.equals(contents);
		}

		/** Number of ingredient items (a required core included). */
		public int size() {
			int total = core == null ? 0 : 1;
			for (int count : ingredients.values()) {
				total += count;
			}
			return total;
		}

		/** The output's name in the recipe colour. */
		public MutableComponent displayName() {
			return new ItemStack(output.get()).getHoverName().copy().withColor(color);
		}
	}

	/**
	 * A pot after the catalysts were taken out: the matched recipe (null for an unknown mixture), the Low and the Mid or
	 * better stones, the catalyst core's grade (0 none) and the auxiliary herb's age (−1 none).
	 */
	public record Mix(@Nullable Recipe recipe, int lowStones, int highStones, int coreGrade, int auxAge) {

		public static final Mix NONE = new Mix(null, 0, 0, 0, -1);

		public boolean hasCatalysts() {
			return lowStones > 0 || highStones > 0 || coreGrade > 0 || auxAge >= 0;
		}

		/** Success bonus of the catalysts (each Low stone +0.05, Mid or better +0.10, the core by grade). */
		public double chanceBonus() {
			return lowStones * SpiritStones.LOW_CATALYST_CHANCE + highStones * SpiritStones.MID_CATALYST_CHANCE + BeastCores.catalystChance(coreGrade);
		}

		/** Quality roll bonus (Mid or better stones +0.1 each, the auxiliary herb up to +0.30). */
		public double qualityBonus() {
			return highStones * SpiritStones.MID_CATALYST_QUALITY + (auxAge >= 0 ? SpiritHerbs.auxiliaryQuality(auxAge) : 0.0D);
		}

		/** The lowest quality the catalyst core allows, or null. */
		public @Nullable PillQuality floor() {
			return BeastCores.qualityFloor(coreGrade);
		}
	}

	private static final List<Recipe> BASE = List.of(
			recipe("qi_gathering", () -> AlchemyItems.QI_GATHERING_PILL, 2, PillKind.QI_GATHERING.color(), 15.0D,
					Items.GLOWSTONE_DUST, Items.SUGAR, Items.GLOW_BERRIES),
			recipe("qi_recovery", () -> AlchemyItems.QI_RECOVERY_PILL, 2, PillKind.QI_RECOVERY.color(), 15.0D,
					Items.AMETHYST_SHARD, Items.HONEYCOMB, Items.SWEET_BERRIES),
			recipe("healing", () -> AlchemyItems.HEALING_PILL, 2, PillKind.HEALING.color(), 20.0D,
					Items.GLISTERING_MELON_SLICE, Items.GOLDEN_CARROT, Items.SWEET_BERRIES, Items.KELP),
			recipe("healing_ginseng", () -> AlchemyItems.HEALING_PILL, 3, PillKind.HEALING.color(), 20.0D,
					HartaItems.NINECURL_GINSENG, Items.GOLDEN_CARROT, Items.GLISTERING_MELON_SLICE),
			recipe("heart_calming", () -> AlchemyItems.HEART_CALMING_PILL, 2, PillKind.HEART_CALMING.color(), 22.0D,
					Items.HONEYCOMB, Items.LILY_OF_THE_VALLEY, Items.COCOA_BEANS, Items.GLOW_BERRIES),
			recipe("spirit_beast", () -> BeastItems.SPIRIT_BEAST_PILL, 2, 0xFFC870, 24.0D,
					Items.BONE, Items.GLOW_BERRIES, Items.AMETHYST_SHARD, Items.GOLDEN_CARROT),
			recipe("toxin_purging", () -> AlchemyItems.TOXIN_PURGING_PILL, 2, PillKind.TOXIN_PURGING.color(), 26.0D,
					HartaItems.EIGHT_ANGLED_ICE_GRASS, Items.HONEY_BOTTLE, HartaItems.SPIRIT_GRASS),
			recipe("foundation", () -> AlchemyItems.FOUNDATION_PILL, 2, PillKind.FOUNDATION.color(), 28.0D,
					Items.GOLDEN_CARROT, Items.GLOWSTONE_DUST, Items.AMETHYST_SHARD, Items.BONE_MEAL),
			recipe("marrow_cleansing", () -> RootItems.MARROW_CLEANSING_PILL, 2, 0x8FE3C8, 30.0D,
					Items.GLISTERING_MELON_SLICE, Items.GHAST_TEAR, Items.GOLDEN_APPLE, Items.AMETHYST_SHARD, Items.BONE_MEAL),
			recipe("enlightenment", () -> AlchemyItems.ENLIGHTENMENT_PILL, 2, PillKind.ENLIGHTENMENT.color(), 32.0D,
					HartaItems.ILLUSORY_SPIRIT_HERB, Items.GLOW_BERRIES, Items.AMETHYST_SHARD),
			recipe("golden_core", () -> AlchemyItems.GOLDEN_CORE_PILL, 2, PillKind.GOLDEN_CORE.color(), 45.0D,
					Items.GOLD_INGOT, Items.BLAZE_POWDER, Items.GOLDEN_APPLE, Items.AMETHYST_SHARD),
			recipe("golden_core_flame", () -> AlchemyItems.GOLDEN_CORE_PILL, 2, PillKind.GOLDEN_CORE.color(), 45.0D,
					HartaItems.PURPLE_FLAME_FLOWER, Items.GOLD_INGOT, Items.GOLDEN_APPLE),
			coreRecipe("bone_tempering", () -> AlchemyItems.BONE_TEMPERING_PILL, 2, PillKind.BONE_TEMPERING.color(), 50.0D,
					new CoreNeed(Element.EARTH, BeastCores.MID), Items.BONE_BLOCK, Items.IRON_INGOT, Items.GOLDEN_CARROT),
			recipe("tribulation_warding", () -> AlchemyItems.TRIBULATION_WARDING_PILL, 2, PillKind.TRIBULATION_WARDING.color(), 55.0D,
					Items.COPPER_INGOT, Items.PHANTOM_MEMBRANE, Items.AMETHYST_SHARD, Items.GLOWSTONE_DUST, Items.IRON_INGOT),
			recipe("tribulation_warding_bamboo", () -> AlchemyItems.TRIBULATION_WARDING_PILL, 2, PillKind.TRIBULATION_WARDING.color(), 55.0D,
					HartaItems.GOLD_LIGHTNING_BAMBOO, Items.COPPER_INGOT, Items.PHANTOM_MEMBRANE, Items.IRON_INGOT),
			recipe("dao_insight", () -> AlchemyItems.DAO_INSIGHT_PILL, 1, PillKind.DAO_INSIGHT.color(), 70.0D,
					HartaItems.ILLUSORY_SPIRIT_HERB, HartaItems.YELLOW_ESSENCE_MUSHROOM, Items.ECHO_SHARD),
			recipe("soul_nurturing", () -> AlchemyItems.SOUL_NURTURING_PILL, 2, PillKind.SOUL_NURTURING.color(), 75.0D,
					Items.GHAST_TEAR, Items.ECHO_SHARD, Items.CHORUS_FRUIT, Items.NETHER_WART),
			recipe("soul_nurturing_fruit", () -> AlchemyItems.SOUL_NURTURING_PILL, 3, PillKind.SOUL_NURTURING.color(), 75.0D,
					HartaItems.GINSENG_FRUIT, Items.GHAST_TEAR, Items.ECHO_SHARD, Items.CHORUS_FRUIT),
			recipe("immortal", () -> AlchemyItems.IMMORTAL_PILL, 1, PillKind.IMMORTAL.color(), 200.0D,
					Items.NETHER_STAR, Items.TOTEM_OF_UNDYING, Items.ENCHANTED_GOLDEN_APPLE, Items.ECHO_SHARD, Items.HEART_OF_THE_SEA));

	private static final Map<Item, Integer> COLORS = new HashMap<>();
	private static volatile List<Recipe> recipes;
	private static volatile Set<Item> ingredients;

	static {
		COLORS.put(Items.SPIDER_EYE, 0x9C2B3A);
		COLORS.put(Items.GLOWSTONE_DUST, 0xFFD54A);
		COLORS.put(Items.SUGAR, 0xF4F4F4);
		COLORS.put(Items.GLOW_BERRIES, 0xFFB43C);
		COLORS.put(Items.AMETHYST_SHARD, 0xB57CFF);
		COLORS.put(Items.HONEYCOMB, 0xF2A93B);
		COLORS.put(Items.SWEET_BERRIES, 0xD4283C);
		COLORS.put(Items.GOLDEN_CARROT, 0xFFC93C);
		COLORS.put(Items.BONE_MEAL, 0xEDEBE0);
		COLORS.put(Items.BONE, 0xE8E4D4);
		COLORS.put(Items.GOLD_INGOT, 0xFFD700);
		COLORS.put(Items.BLAZE_POWDER, 0xFF9A1F);
		COLORS.put(Items.GOLDEN_APPLE, 0xFFD54A);
		COLORS.put(Items.LILY_OF_THE_VALLEY, 0xF6FFF2);
		COLORS.put(Items.COCOA_BEANS, 0x9A6A3A);
		COLORS.put(Items.GLISTERING_MELON_SLICE, 0xFF7484);
		COLORS.put(Items.KELP, 0x4F8F3A);
		COLORS.put(Items.COPPER_INGOT, 0xE07A4E);
		COLORS.put(Items.PHANTOM_MEMBRANE, 0xC8D4C0);
		COLORS.put(Items.IRON_INGOT, 0xD8D8D8);
		COLORS.put(Items.GHAST_TEAR, 0xE4F7FF);
		COLORS.put(Items.ECHO_SHARD, 0x2CC7C0);
		COLORS.put(Items.CHORUS_FRUIT, 0xB07CC0);
		COLORS.put(Items.NETHER_WART, 0xB0303A);
		COLORS.put(Items.NETHER_STAR, 0xF2F6FF);
		COLORS.put(Items.TOTEM_OF_UNDYING, 0xF2D76B);
		COLORS.put(Items.ENCHANTED_GOLDEN_APPLE, 0xE98BFF);
		COLORS.put(Items.HEART_OF_THE_SEA, 0x3E9BD8);
		COLORS.put(Items.HONEY_BOTTLE, 0xFFA928);
		COLORS.put(Items.BONE_BLOCK, 0xE8E4D4);
		for (HerbKind kind : HerbKind.values()) {
			COLORS.put(HartaItems.herb(kind), kind.color());
		}
		for (int grade = SpiritStones.LOW; grade <= SpiritStones.SUPREME; grade++) {
			COLORS.put(HartaItems.stoneItem(grade), SpiritStones.COLORS[grade]);
		}
		for (int grade = BeastCores.LOW; grade <= BeastCores.KING; grade++) {
			COLORS.put(HartaItems.coreItem(grade), BeastCores.NEUTRAL_COLOR);
		}
	}

	private static Recipe recipe(String id, Supplier<Item> output, int count, int color, double xp, Item... items) {
		return coreRecipe(id, output, count, color, xp, null, items);
	}

	private static Recipe coreRecipe(String id, Supplier<Item> output, int count, int color, double xp, @Nullable CoreNeed core, Item... items) {
		Map<Item, Integer> ingredients = new LinkedHashMap<>();
		for (Item item : items) {
			ingredients.merge(item, 1, Integer::sum);
		}
		return new Recipe(id, output, count, Collections.unmodifiableMap(ingredients), color, xp, core);
	}

	/** Every recipe, from the simplest to the Immortal Pill, then the phase-8 alternatives. */
	public static List<Recipe> all() {
		List<Recipe> list = recipes;
		if (list == null) {
			list = build();
		}
		return list;
	}

	private static synchronized List<Recipe> build() {
		if (recipes != null) {
			return recipes;
		}
		List<Recipe> list = new ArrayList<>(BASE);
		Item wood = registered("soul_nurturing_wood");
		Item blood = registered("ancient_god_blood");
		Item essence = registered("tomb_essence");
		list.add(new Recipe("foundation_herba", () -> AlchemyItems.FOUNDATION_PILL, 1, map(SectEventItems.FOUNDATION_HERB, SectEventItems.FOUNDATION_HERB,
				SectEventItems.FOUNDATION_HERB, AlchemyItems.HEALING_PILL), PillKind.FOUNDATION.color(), 28.0D, null));
		if (wood != null) {
			list.add(new Recipe("soul_nurturing_wood", () -> AlchemyItems.SOUL_NURTURING_PILL, 3, map(wood, Items.GHAST_TEAR, Items.GLOW_BERRIES),
					PillKind.SOUL_NURTURING.color(), 75.0D, null));
		}
		if (blood != null) {
			list.add(new Recipe("bone_tempering_blood", () -> AlchemyItems.BONE_TEMPERING_PILL, 3, map(blood, Items.BONE_BLOCK, Items.GOLDEN_CARROT),
					PillKind.BONE_TEMPERING.color(), 50.0D, new CoreNeed(Element.EARTH, BeastCores.MID)));
		}
		if (essence != null) {
			list.add(new Recipe("tribulation_warding_essence", () -> AlchemyItems.TRIBULATION_WARDING_PILL, 2, map(essence, Items.ECHO_SHARD,
					Items.GOLDEN_APPLE), PillKind.TRIBULATION_WARDING.color(), 55.0D, null));
		}
		list.add(new Recipe("mata_terang", () -> AlchemyItems.MATA_TERANG_PILL, 2, map(HartaItems.ILLUSORY_SPIRIT_HERB, Items.SPIDER_EYE,
				Items.GLOW_BERRIES), PillKind.MATA_TERANG.color(), 30.0D, null));
		Set<Item> used = new HashSet<>();
		for (Recipe recipe : list) {
			used.addAll(recipe.ingredients().keySet());
		}
		ingredients = Set.copyOf(used);
		List<Recipe> done = List.copyOf(list);
		recipes = done;
		return done;
	}

	private static @Nullable Item registered(String path) {
		Item item = BuiltInRegistries.ITEM.getValue(dev.kultivator.Kultivator.id(path));
		return item == Items.AIR ? null : item;
	}

	private static Map<Item, Integer> map(Item... items) {
		Map<Item, Integer> map = new LinkedHashMap<>();
		for (Item item : items) {
			map.merge(item, 1, Integer::sum);
		}
		return Collections.unmodifiableMap(map);
	}

	private static Set<Item> ingredientSet() {
		Set<Item> set = ingredients;
		if (set == null) {
			all();
			set = ingredients;
		}
		return set == null ? Set.of() : set;
	}

	/** The recipe without a required core whose ingredients are exactly {@code contents}, or null (no catalysts). */
	public static @Nullable Recipe match(Map<Item, Integer> contents) {
		if (contents.isEmpty()) {
			return null;
		}
		for (Recipe recipe : all()) {
			if (recipe.matches(contents)) {
				return recipe;
			}
		}
		return null;
	}

	public static @Nullable Recipe byId(String id) {
		for (Recipe recipe : all()) {
			if (recipe.id().equals(id)) {
				return recipe;
			}
		}
		return null;
	}

	/** True when {@code item} is used by at least one recipe. */
	public static boolean isIngredient(Item item) {
		return ingredientSet().contains(item);
	}

	/** True when {@code stack} may go into a pot: a recipe ingredient, a spirit stone, a beast core or any spirit herb. */
	public static boolean accepts(ItemStack stack) {
		return !stack.isEmpty() && (isIngredient(stack.getItem()) || SpiritStones.isStone(stack) || BeastCores.isCore(stack)
				|| SpiritHerbs.isHerb(stack));
	}

	/** The recipes that use {@code item} as an ingredient or, for a core, as their required core. */
	public static List<Recipe> usesOf(ItemStack stack) {
		List<Recipe> list = new ArrayList<>();
		for (Recipe recipe : all()) {
			if (recipe.ingredients().containsKey(stack.getItem())
					|| recipe.core() != null && BeastCores.isCore(stack) && recipe.core().accepts(BeastCores.element(stack), BeastCores.grade(stack))) {
				list.add(recipe);
			}
		}
		return list;
	}

	private static boolean herbItem(Item item) {
		return item instanceof SpiritHerbItem || item == SectEventItems.FOUNDATION_HERB;
	}

	/** Reads a pot (DESIGN 19.3.7): catalysts out, then the recipe with at most one auxiliary herb. */
	public static Mix analyze(AlchemyPots.Pot pot) {
		Map<Item, Integer> contents = pot.contents();
		if (contents.isEmpty()) {
			return Mix.NONE;
		}
		Map<Item, Integer> rest = new LinkedHashMap<>();
		int low = 0;
		int high = 0;
		int stones = 0;
		for (Map.Entry<Item, Integer> entry : contents.entrySet()) {
			if (entry.getKey() instanceof SpiritStoneItem stone) {
				stones += entry.getValue();
				if (stone.grade() == SpiritStones.LOW) {
					low += entry.getValue();
				} else {
					high += entry.getValue();
				}
			} else {
				rest.put(entry.getKey(), entry.getValue());
			}
		}
		if (stones > MAX_STONE_CATALYSTS) {
			// The stones beyond two stay ingredients, and no recipe uses stones.
			return new Mix(null, 0, 0, 0, -1);
		}
		List<AlchemyPots.Unit> cores = new ArrayList<>();
		for (AlchemyPots.Unit unit : pot.units()) {
			if (unit.item() instanceof BeastCoreItem) {
				cores.add(unit);
			}
		}
		int coreItems = 0;
		for (Map.Entry<Item, Integer> entry : rest.entrySet()) {
			if (entry.getKey() instanceof BeastCoreItem) {
				coreItems += entry.getValue();
			}
		}
		for (int pass = 0; pass < 2; pass++) {
			for (Recipe recipe : all()) {
				Mix mix = tryRecipe(recipe, rest, cores, coreItems, pot.units(), pass == 1, low, high);
				if (mix != null) {
					return mix;
				}
			}
		}
		return new Mix(null, low, high, 0, -1);
	}

	private static @Nullable Mix tryRecipe(Recipe recipe, Map<Item, Integer> rest, List<AlchemyPots.Unit> cores, int coreItems,
										   List<AlchemyPots.Unit> units, boolean allowAux, int low, int high) {
		Map<Item, Integer> work = new LinkedHashMap<>(rest);
		int catalystGrade = 0;
		if (coreItems > 1) {
			return null;
		}
		if (recipe.core() != null) {
			if (coreItems != 1 || cores.size() != 1) {
				return null;
			}
			AlchemyPots.Unit core = cores.get(0);
			int grade = core.item() instanceof BeastCoreItem item ? item.grade() : 0;
			if (!recipe.core().accepts(Element.byId(core.element()), grade)) {
				return null;
			}
			remove(work, core.item());
		} else if (coreItems == 1) {
			for (Map.Entry<Item, Integer> entry : rest.entrySet()) {
				if (entry.getKey() instanceof BeastCoreItem item) {
					catalystGrade = item.grade();
					remove(work, entry.getKey());
					break;
				}
			}
		}
		Item extra = null;
		int extras = 0;
		for (Map.Entry<Item, Integer> entry : work.entrySet()) {
			int need = recipe.ingredients().getOrDefault(entry.getKey(), 0);
			int over = entry.getValue() - need;
			if (over > 0) {
				extras += over;
				extra = entry.getKey();
			}
		}
		for (Map.Entry<Item, Integer> entry : recipe.ingredients().entrySet()) {
			if (work.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
				return null;
			}
		}
		if (extras == 0) {
			return allowAux ? null : new Mix(recipe, low, high, catalystGrade, -1);
		}
		if (!allowAux || extras != 1 || !herbItem(extra)) {
			return null;
		}
		int age = -1;
		for (AlchemyPots.Unit unit : units) {
			if (unit.item() == extra) {
				age = Math.max(age, unit.age());
			}
		}
		if (age < 0) {
			age = extra == SectEventItems.FOUNDATION_HERB ? SpiritHerbs.FOUNDATION_HERB_AGE : SpiritHerbs.DEFAULT_AGE;
		}
		return new Mix(recipe, low, high, catalystGrade, age);
	}

	private static void remove(Map<Item, Integer> work, Item item) {
		int left = work.getOrDefault(item, 0) - 1;
		if (left <= 0) {
			work.remove(item);
		} else {
			work.put(item, left);
		}
	}

	/** RGB of an ingredient's sparkle. */
	public static int color(Item item) {
		return COLORS.getOrDefault(item, DEFAULT_INGREDIENT_COLOR);
	}

	/** "Glowstone Dust + 2× Sugar + ..." for chat. */
	public static MutableComponent ingredientList(Map<Item, Integer> ingredients) {
		MutableComponent line = Component.empty();
		boolean first = true;
		for (Map.Entry<Item, Integer> entry : ingredients.entrySet()) {
			if (!first) {
				line.append(Component.literal(" + "));
			}
			first = false;
			if (entry.getValue() > 1) {
				line.append(Component.literal(entry.getValue() + "× "));
			}
			line.append(new ItemStack(entry.getKey()).getHoverName());
		}
		return line;
	}

	/** The ingredient list of {@code recipe} including its required core ("… + Inti Binatang Tanah Menengah+"). */
	public static MutableComponent ingredientList(Recipe recipe) {
		MutableComponent line = ingredientList(recipe.ingredients());
		if (recipe.core() != null) {
			line.append(Component.literal(" + "));
			line.append(Component.translatable("kultivator.harta.alchemy.core_need", recipe.core().element().displayName(),
					BeastCores.gradeName(recipe.core().minGrade())));
		}
		return line;
	}

	private AlchemyRecipes() {
	}
}
