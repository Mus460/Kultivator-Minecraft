package dev.kultivator.alchemy;

import dev.kultivator.beast.BeastItems;
import dev.kultivator.item.RootItems;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The alchemy recipe table (DESIGN section 9): each recipe is an unordered multiset of vanilla items that have no
 * vanilla cauldron interaction, refined into pills. The pot must hold exactly the recipe's items, nothing more.
 * <pre>
 * qi_gathering        Glowstone Dust + Sugar + Glow Berries                                   -> 2 Qi Gathering Pills
 * qi_recovery         Amethyst Shard + Honeycomb + Sweet Berries                              -> 2 Qi Recovery Pills
 * healing             Glistering Melon Slice + Golden Carrot + Sweet Berries + Kelp           -> 2 Healing Pills
 * heart_calming       Honeycomb + Lily of the Valley + Cocoa Beans + Glow Berries             -> 2 Heart Calming Pills
 * spirit_beast        Bone + Glow Berries + Amethyst Shard + Golden Carrot                    -> 2 Spirit Beast Pills
 * foundation          Golden Carrot + Glowstone Dust + Amethyst Shard + Bone Meal             -> 2 Foundation Pills
 * marrow_cleansing    Glistering Melon Slice + Ghast Tear + Golden Apple + Amethyst Shard + Bone Meal -> 2 Marrow Pills
 * golden_core         Gold Ingot + Blaze Powder + Golden Apple + Amethyst Shard               -> 2 Golden Core Pills
 * tribulation_warding Copper Ingot + Phantom Membrane + Amethyst Shard + Glowstone Dust + Iron Ingot -> 2 Warding Pills
 * soul_nurturing      Ghast Tear + Echo Shard + Chorus Fruit + Nether Wart                    -> 2 Soul Nurturing Pills
 * immortal            Nether Star + Totem of Undying + Enchanted Golden Apple + Echo Shard + Heart of the Sea -> 1 Immortal Pill
 * </pre>
 */
public final class AlchemyRecipes {

	/** Colour of steam / sparkles for a mixture that matches no recipe. */
	public static final int MURKY_COLOR = 0x6B6B5A;
	private static final int DEFAULT_INGREDIENT_COLOR = 0xC8C8C8;

	/**
	 * One recipe. {@code xp} is the alchemy XP for a Low success (+20% per quality grade above Low); {@code count} the pills made (a Perfect refining of a
	 * pill without quality, the Marrow or Spirit Beast pill, gives one more).
	 */
	public record Recipe(String id, Supplier<Item> output, int count, Map<Item, Integer> ingredients, int color, double xp) {

		public Item outputItem() {
			return output.get();
		}

		/** The output is one of this module's pills (it carries a quality). */
		public boolean isPill() {
			return output.get() instanceof PillItem;
		}

		public boolean matches(Map<Item, Integer> contents) {
			return ingredients.equals(contents);
		}

		/** Number of ingredient items. */
		public int size() {
			int total = 0;
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

	private static final List<Recipe> RECIPES = List.of(
			recipe("qi_gathering", () -> AlchemyItems.QI_GATHERING_PILL, 2, PillKind.QI_GATHERING.color(), 15.0D,
					Items.GLOWSTONE_DUST, Items.SUGAR, Items.GLOW_BERRIES),
			recipe("qi_recovery", () -> AlchemyItems.QI_RECOVERY_PILL, 2, PillKind.QI_RECOVERY.color(), 15.0D,
					Items.AMETHYST_SHARD, Items.HONEYCOMB, Items.SWEET_BERRIES),
			recipe("healing", () -> AlchemyItems.HEALING_PILL, 2, PillKind.HEALING.color(), 20.0D,
					Items.GLISTERING_MELON_SLICE, Items.GOLDEN_CARROT, Items.SWEET_BERRIES, Items.KELP),
			recipe("heart_calming", () -> AlchemyItems.HEART_CALMING_PILL, 2, PillKind.HEART_CALMING.color(), 22.0D,
					Items.HONEYCOMB, Items.LILY_OF_THE_VALLEY, Items.COCOA_BEANS, Items.GLOW_BERRIES),
			recipe("spirit_beast", () -> BeastItems.SPIRIT_BEAST_PILL, 2, 0xFFC870, 24.0D,
					Items.BONE, Items.GLOW_BERRIES, Items.AMETHYST_SHARD, Items.GOLDEN_CARROT),
			recipe("foundation", () -> AlchemyItems.FOUNDATION_PILL, 2, PillKind.FOUNDATION.color(), 28.0D,
					Items.GOLDEN_CARROT, Items.GLOWSTONE_DUST, Items.AMETHYST_SHARD, Items.BONE_MEAL),
			recipe("marrow_cleansing", () -> RootItems.MARROW_CLEANSING_PILL, 2, 0x8FE3C8, 30.0D,
					Items.GLISTERING_MELON_SLICE, Items.GHAST_TEAR, Items.GOLDEN_APPLE, Items.AMETHYST_SHARD, Items.BONE_MEAL),
			recipe("golden_core", () -> AlchemyItems.GOLDEN_CORE_PILL, 2, PillKind.GOLDEN_CORE.color(), 45.0D,
					Items.GOLD_INGOT, Items.BLAZE_POWDER, Items.GOLDEN_APPLE, Items.AMETHYST_SHARD),
			recipe("tribulation_warding", () -> AlchemyItems.TRIBULATION_WARDING_PILL, 2, PillKind.TRIBULATION_WARDING.color(), 55.0D,
					Items.COPPER_INGOT, Items.PHANTOM_MEMBRANE, Items.AMETHYST_SHARD, Items.GLOWSTONE_DUST, Items.IRON_INGOT),
			recipe("soul_nurturing", () -> AlchemyItems.SOUL_NURTURING_PILL, 2, PillKind.SOUL_NURTURING.color(), 75.0D,
					Items.GHAST_TEAR, Items.ECHO_SHARD, Items.CHORUS_FRUIT, Items.NETHER_WART),
			recipe("immortal", () -> AlchemyItems.IMMORTAL_PILL, 1, PillKind.IMMORTAL.color(), 200.0D,
					Items.NETHER_STAR, Items.TOTEM_OF_UNDYING, Items.ENCHANTED_GOLDEN_APPLE, Items.ECHO_SHARD, Items.HEART_OF_THE_SEA));

	private static final Set<Item> INGREDIENTS = new HashSet<>();
	private static final Map<Item, Integer> COLORS = new HashMap<>();

	static {
		for (Recipe recipe : RECIPES) {
			INGREDIENTS.addAll(recipe.ingredients().keySet());
		}
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
	}

	private static Recipe recipe(String id, Supplier<Item> output, int count, int color, double xp, Item... items) {
		Map<Item, Integer> ingredients = new LinkedHashMap<>();
		for (Item item : items) {
			ingredients.merge(item, 1, Integer::sum);
		}
		return new Recipe(id, output, count, Collections.unmodifiableMap(ingredients), color, xp);
	}

	/** Every recipe, from the simplest to the Immortal Pill. */
	public static List<Recipe> all() {
		return RECIPES;
	}

	/** The recipe whose ingredients are exactly {@code contents}, or null. */
	public static @Nullable Recipe match(Map<Item, Integer> contents) {
		if (contents.isEmpty()) {
			return null;
		}
		for (Recipe recipe : RECIPES) {
			if (recipe.matches(contents)) {
				return recipe;
			}
		}
		return null;
	}

	public static @Nullable Recipe byId(String id) {
		for (Recipe recipe : RECIPES) {
			if (recipe.id().equals(id)) {
				return recipe;
			}
		}
		return null;
	}

	/** True when {@code item} is used by at least one recipe (only these can be put into a pot). */
	public static boolean isIngredient(Item item) {
		return INGREDIENTS.contains(item);
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

	private AlchemyRecipes() {
	}
}
