package dev.kultivator.alam;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Element;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.XpSource;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.harta.HartaComponents;
import dev.kultivator.harta.HartaItems;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.sect.event.SectEventItems;
import dev.kultivator.technique.source.JadeSlips;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where module A's keys come from and what a realm gives (DESIGN 25.3.8, 25.3.9):
 * <ul>
 * <li>chest loot ({@code LootTableEvents.MODIFY}, built-in tables only): {@code token_darah_api} 15% in simple dungeons and
 * abandoned mineshafts, {@code heavenvoid_fragment} 8% in ancient cities and 10% in end city treasure;</li>
 * <li>Raja Siluman (elite tier 3, the spine drop hook): 10% fragment, 2% inheritance crystal;</li>
 * <li>tribulations (the spine success hook): the first Deity Transformation tribulation gives an inheritance crystal, each
 * Immortal Ascension layer tribulation a tomb sigil;</li>
 * <li>the run rewards and the Makam layer rewards.</li>
 * </ul>
 * Rogue Tetua (20%) and Patriarch (60% fragment, 25% crystal, 100% sigil from IA) drops are module B's loot. Every amount is
 * game design; the items carry their own sources.
 */
public final class AlamLoot {

	public static final float TOKEN_CHANCE = 0.15F;
	public static final float FRAGMENT_ANCIENT_CITY = 0.08F;
	public static final float FRAGMENT_END_CITY = 0.10F;
	public static final double KING_FRAGMENT = 0.10D;
	public static final double KING_CRYSTAL = 0.02D;
	public static final String DT_CRYSTAL_KEY = "dt_crystal";

	/** What a clear gives one player: guaranteed items and, where the realm says so, 3 options to choose 1 from. */
	public record Treasure(List<ItemStack> items, List<ItemStack> choices) {
	}

	private record Row(Item item, float chance) {
	}

	private static final Map<ResourceKey<LootTable>, List<Row>> CHESTS = new LinkedHashMap<>();

	static void init() {
		CHESTS.put(BuiltInLootTables.SIMPLE_DUNGEON, List.of(new Row(AlamItems.TOKEN_DARAH_API, TOKEN_CHANCE)));
		CHESTS.put(BuiltInLootTables.ABANDONED_MINESHAFT, List.of(new Row(AlamItems.TOKEN_DARAH_API, TOKEN_CHANCE)));
		CHESTS.put(BuiltInLootTables.ANCIENT_CITY, List.of(new Row(AlamItems.HEAVENVOID_FRAGMENT, FRAGMENT_ANCIENT_CITY)));
		CHESTS.put(BuiltInLootTables.END_CITY_TREASURE, List.of(new Row(AlamItems.HEAVENVOID_FRAGMENT, FRAGMENT_END_CITY)));
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin() || !KultivatorConfig.get().secretRealms) {
				return;
			}
			List<Row> rows = CHESTS.get(key);
			if (rows == null) {
				return;
			}
			for (Row row : rows) {
				builder.withPool(LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(row.chance()))
						.add(LootItem.lootTableItem(row.item())));
			}
		});
		EliteMonsters.addDropProvider((level, victim, tier, element, killer, out) -> {
			if (tier < 3 || !KultivatorConfig.get().secretRealms) {
				return;
			}
			RandomSource random = level.getRandom();
			if (random.nextDouble() < KING_FRAGMENT) {
				out.accept(new ItemStack(AlamItems.HEAVENVOID_FRAGMENT));
			}
			if (random.nextDouble() < KING_CRYSTAL) {
				out.accept(new ItemStack(AlamItems.INHERITANCE_CRYSTAL));
			}
		});
		Tribulation.addSuccessListener((player, info) -> {
			if (info.test() || !KultivatorConfig.get().secretRealms) {
				return;
			}
			Realm target = info.target().realm();
			if (target == Realm.DEITY_TRANSFORMATION && !ReturnPoint.dunia(player).getBooleanOr(DT_CRYSTAL_KEY, false)) {
				ReturnPoint.dunia(player).putBoolean(DT_CRYSTAL_KEY, true);
				Cultivators.dirty(player);
				give(player, new ItemStack(AlamItems.INHERITANCE_CRYSTAL));
				Msg.chat(player, Component.translatable("kultivator.alam.loot.dt_crystal").withStyle(ChatFormatting.AQUA));
			} else if (target == Realm.IMMORTAL_ASCENSION) {
				give(player, new ItemStack(AlamItems.TOMB_SIGIL));
				Msg.chat(player, Component.translatable("kultivator.alam.loot.ia_sigil").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		});
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().add(stack);
		if (!stack.isEmpty()) {
			RealmRun.drop(player, stack);
		}
	}

	// ================================================================== run rewards (25.3.8)

	/** The rewards of a cleared realm 1-3 for one player (realm 3's crystals and blood were given per hall already). */
	static Treasure runRewards(ServerPlayer player, RealmSpec spec, boolean first, boolean bonus, int halls) {
		List<ItemStack> items = new ArrayList<>();
		List<ItemStack> choices = new ArrayList<>();
		RandomSource random = player.getRandom();
		CultivationData data = Cultivators.get(player);
		double share;
		switch (spec.ordinal()) {
			case 1 -> {
				share = 0.10D;
				if (first) {
					for (int i = 0; i < 4; i++) {
						items.add(herb(60 + random.nextInt(61)));
					}
					items.add(new ItemStack(HartaItems.stoneItem(SpiritStones.LOW), 4));
					ItemStack page = slip(random, TechniqueGrade.HUANG, TechniqueGrade.XUAN);
					if (!page.isEmpty()) {
						items.add(page);
					}
				} else {
					items.add(herb(60 + random.nextInt(61)));
					items.add(new ItemStack(HartaItems.stoneItem(SpiritStones.LOW), 2));
				}
			}
			case 2 -> {
				share = 0.15D;
				if (first) {
					items.add(new ItemStack(AlamItems.HEAVENVOID_CAULDRON));
					items.add(new ItemStack(AlamItems.SOUL_NURTURING_WOOD, 2));
					items.add(new ItemStack(HartaItems.stoneItem(SpiritStones.MID), 2));
					items.add(BeastCores.create(randomElement(random), BeastCores.HIGH));
				} else {
					if (random.nextBoolean()) {
						items.add(new ItemStack(AlamItems.SOUL_NURTURING_WOOD));
					}
					items.add(new ItemStack(HartaItems.stoneItem(SpiritStones.MID), 1 + random.nextInt(3)));
					items.add(BeastCores.create(randomElement(random), random.nextBoolean() ? BeastCores.MID : BeastCores.HIGH));
				}
				ItemStack slip = slip(random, TechniqueGrade.DI, TechniqueGrade.DI);
				if (!slip.isEmpty()) {
					choices.add(slip);
				}
				choices.add(new ItemStack(HartaItems.stoneItem(SpiritStones.HIGH), 2));
				choices.add(BeastCores.create(ownElement(data, random), BeastCores.MID));
			}
			default -> share = 0.15D;
		}
		if (bonus) {
			items.add(new ItemStack(HartaItems.stoneItem(SpiritStones.HIGH)));
			items.add(BeastCores.create(randomElement(random), BeastCores.MID));
		}
		Progression.addXp(player, data.rank().xpRequired() * share, XpSource.TRIAL);
		return new Treasure(items, choices);
	}

	/**
	 * One Makam layer for one player: {@code 1 + floor(layer/9)} Esensi (half, at least 1, below the saved best); on a new best
	 * 5% of the bar, and on layers 9k a Supreme stone, a Raja core and a tomb sigil plus the choice of an Abadi slip, 3 Esensi
	 * or a Supreme stone.
	 */
	static Treasure layerRewards(ServerPlayer player, int layer, boolean newBest) {
		List<ItemStack> items = new ArrayList<>();
		List<ItemStack> choices = new ArrayList<>();
		int essence = 1 + layer / 9;
		if (!newBest) {
			essence = Math.max(1, essence / 2);
		}
		items.add(new ItemStack(AlamItems.TOMB_ESSENCE, essence));
		if (newBest) {
			CultivationData data = Cultivators.get(player);
			Progression.addXp(player, data.rank().xpRequired() * 0.05D, XpSource.TRIAL);
			if (layer % 9 == 0) {
				RandomSource random = player.getRandom();
				items.add(new ItemStack(HartaItems.stoneItem(SpiritStones.SUPREME)));
				items.add(BeastCores.create(randomElement(random), BeastCores.KING));
				items.add(new ItemStack(AlamItems.TOMB_SIGIL));
				ItemStack slip = slip(random, TechniqueGrade.ABADI, TechniqueGrade.ABADI);
				if (!slip.isEmpty()) {
					choices.add(slip);
				}
				choices.add(new ItemStack(AlamItems.TOMB_ESSENCE, 3));
				choices.add(new ItemStack(HartaItems.stoneItem(SpiritStones.SUPREME)));
			}
		}
		return new Treasure(items, choices);
	}

	/** Herba Fondasi of {@code age} years (it joins the herb family with its age, DESIGN 19.3.4). */
	static ItemStack herb(int age) {
		ItemStack stack = new ItemStack(SectEventItems.FOUNDATION_HERB);
		stack.set(HartaComponents.HERB_AGE, age);
		return stack;
	}

	/** A jade slip of a random slip-allowed spec of grade {@code from}..{@code to}; empty when none exists. */
	static ItemStack slip(RandomSource random, TechniqueGrade from, TechniqueGrade to) {
		if (!TechniqueRegistry.frozen()) {
			return ItemStack.EMPTY;
		}
		List<TechniqueSpec> pool = new ArrayList<>();
		for (TechniqueGrade grade : TechniqueGrade.values()) {
			if (grade.ordinal() < from.ordinal() || grade.ordinal() > to.ordinal()) {
				continue;
			}
			for (TechniqueSpec spec : TechniqueRegistry.ofGrade(grade)) {
				if (spec.slipAllowed()) {
					pool.add(spec);
				}
			}
		}
		if (pool.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return JadeSlips.stack(pool.get(random.nextInt(pool.size())).id());
	}

	private static Element randomElement(RandomSource random) {
		Element[] all = Element.values();
		return all[random.nextInt(all.length)];
	}

	/** The player's first root element ("the chosen element"), or a random one. */
	private static Element ownElement(CultivationData data, RandomSource random) {
		List<Element> roots = Element.fromMask(data.rootElements());
		return roots.isEmpty() ? randomElement(random) : roots.get(0);
	}

	/** The key items a gate of {@code spec} needs; null for an unknown realm. */
	static @Nullable Item key(RealmSpec spec) {
		return AlamItems.keyOf(spec.id());
	}

	private AlamLoot() {
	}
}
