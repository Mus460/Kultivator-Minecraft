package dev.kultivator.harta;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.technique.source.JadeSlips;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.UniformContainerBase;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Relik di Peti Kuno (DESIGN 19.3.13): spirit stones, aged herbs, beast cores and a few jade slips in built-in structure
 * chests. One {@code LootTableEvents.MODIFY} listener, built-in chest tables only, config {@code structureLoot}, at most
 * two phase-7 pools per table (next to the 6c Giok Warisan and the hall tokens). A pool that mixes several rare items
 * rolls once with weights, so each keeps its exact chance. Loot tables only; no structure or block is changed.
 * <p>
 * The items carry their own sources; jade slips are RMJ jade slips (ch. 1269). The chest pools are game design.
 */
public final class HartaLoot {

	/** Discrete ages a herb pool spreads its range over. */
	public static final int AGE_STEPS = 6;
	/** Divine Sense treasure sense: unlooted containers within this many blocks are pinged gold. */
	public static final int SENSE_RADIUS = 24;
	public static final int SENSE_COLOR = 0xFFC83C;

	private static final int STONE = 0;
	private static final int HERB = 1;
	private static final int CORE = 2;
	private static final int SLIP = 3;

	/** One entry: a stone (value = grade, min..max count), herb (value = kind ordinal, min..max age), core or slip. */
	private record Entry(int type, int value, @Nullable Element element, int min, int max, int weight) {
	}

	/** One pool: rolled with {@code chance}; {@code empty} is the weight of "nothing" (0 = always one entry). */
	private record Pool(float chance, int empty, List<Entry> entries) {
	}

	private static final Map<ResourceKey<LootTable>, List<Pool>> TABLES = new LinkedHashMap<>();
	private static final Map<String, ResourceKey<LootTable>> NAMES = new LinkedHashMap<>();

	static {
		Pool fewLow = new Pool(0.30F, 0, List.of(stone(SpiritStones.LOW, 1, 4, 1)));
		table("simple_dungeon", BuiltInLootTables.SIMPLE_DUNGEON, fewLow);
		table("abandoned_mineshaft", BuiltInLootTables.ABANDONED_MINESHAFT, fewLow);
		Pool lows = new Pool(1.0F, 0, List.of(stone(SpiritStones.LOW, 2, 6, 1)));
		Pool mid15 = new Pool(0.15F, 0, List.of(stone(SpiritStones.MID, 1, 1, 1)));
		table("desert_pyramid", BuiltInLootTables.DESERT_PYRAMID, lows, mid15);
		table("jungle_temple", BuiltInLootTables.JUNGLE_TEMPLE, lows, mid15);
		table("stronghold_corridor", BuiltInLootTables.STRONGHOLD_CORRIDOR, lows, mid15);
		table("stronghold_crossing", BuiltInLootTables.STRONGHOLD_CROSSING, lows, mid15);
		table("stronghold_library", BuiltInLootTables.STRONGHOLD_LIBRARY, lows, mid15);
		table("woodland_mansion", BuiltInLootTables.WOODLAND_MANSION, new Pool(1.0F, 0, List.of(stone(SpiritStones.LOW, 1, 3, 1))),
				new Pool(0.10F, 0, List.of(stone(SpiritStones.MID, 1, 1, 1))));
		table("igloo_chest", BuiltInLootTables.IGLOO_CHEST, new Pool(0.30F, 0, List.of(herb(HerbKind.EIGHT_ANGLED_ICE_GRASS, 50, 300, 1))),
				new Pool(0.20F, 0, List.of(slip(Element.ICE))));
		table("underwater_ruin_big", BuiltInLootTables.UNDERWATER_RUIN_BIG, new Pool(0.20F, 0, List.of(stone(SpiritStones.LOW, 1, 3, 1))),
				new Pool(0.08F, 0, List.of(slip(Element.ICE))));
		table("nether_bridge", BuiltInLootTables.NETHER_BRIDGE, new Pool(0.20F, 0, List.of(herb(HerbKind.PURPLE_FLAME_FLOWER, 50, 300, 1))),
				new Pool(0.10F, 0, List.of(slip(Element.FIRE))));
		table("buried_treasure", BuiltInLootTables.BURIED_TREASURE, new Pool(0.10F, 0, List.of(herb(HerbKind.ILLUSORY_SPIRIT_HERB, 500, 1500, 1))));
		Pool mids = new Pool(1.0F, 0, List.of(stone(SpiritStones.MID, 1, 3, 1)));
		table("ancient_city", BuiltInLootTables.ANCIENT_CITY, mids, new Pool(1.0F, 750,
				List.of(stone(SpiritStones.HIGH, 1, 1, 100), core(BeastCores.HIGH, Element.SPACE, 150))));
		table("bastion_treasure", BuiltInLootTables.BASTION_TREASURE, mids, new Pool(1.0F, 850,
				List.of(stone(SpiritStones.HIGH, 1, 1, 100), core(BeastCores.KING, Element.FIRE, 50))));
		table("end_city_treasure", BuiltInLootTables.END_CITY_TREASURE, mids, new Pool(1.0F, 800,
				List.of(stone(SpiritStones.HIGH, 1, 1, 100), herb(HerbKind.GINSENG_FRUIT, 300, 900, 100))));
		table("reward_ominous", BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS, new Pool(0.08F, 0, List.of(stone(SpiritStones.HIGH, 1, 1, 1))));
	}

	private static Entry stone(int grade, int min, int max, int weight) {
		return new Entry(STONE, grade, null, min, max, weight);
	}

	private static Entry herb(HerbKind kind, int minAge, int maxAge, int weight) {
		return new Entry(HERB, kind.ordinal(), null, minAge, maxAge, weight);
	}

	private static Entry core(int grade, Element element, int weight) {
		return new Entry(CORE, grade, element, 1, 1, weight);
	}

	private static Entry slip(Element element) {
		return new Entry(SLIP, 0, element, 1, 1, 1);
	}

	private static void table(String name, ResourceKey<LootTable> key, Pool... pools) {
		TABLES.put(key, List.of(pools));
		NAMES.put(name, key);
	}

	static void init() {
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin() || !KultivatorConfig.get().structureLoot) {
				return;
			}
			List<Pool> pools = TABLES.get(key);
			if (pools == null) {
				return;
			}
			for (Pool pool : pools) {
				LootPool.Builder built = build(pool);
				if (built != null) {
					builder.withPool(built);
				}
			}
		});
	}

	// ================================================================ building the pools

	private static @Nullable LootPool.Builder build(Pool pool) {
		LootPool.Builder builder = LootPool.lootPool();
		if (pool.chance() < 0.9999F) {
			builder.when(LootItemRandomChanceCondition.randomChance(pool.chance()));
		}
		boolean mixed = pool.empty() > 0;
		int scale = mixed ? AGE_STEPS : 1;
		int added = 0;
		for (Entry entry : pool.entries()) {
			added += addEntry(builder, entry, scale, mixed);
		}
		if (added == 0) {
			return null;
		}
		if (mixed) {
			builder.add(EmptyLootItem.emptyItem().setWeight(pool.empty() * scale));
		}
		return builder;
	}

	/** Adds the loot entries of one row; returns how many were added. */
	private static int addEntry(LootPool.Builder pool, Entry entry, int scale, boolean weighted) {
		switch (entry.type()) {
			case STONE -> {
				UniformContainerBase.Builder<?> item = LootItem.lootTableItem(HartaItems.stoneItem(entry.value()));
				if (entry.max() > 1) {
					item.apply(SetItemCountFunction.setCount(ContextIntProviders.between(entry.min(), entry.max())));
				}
				if (weighted) {
					item.setWeight(entry.weight() * scale);
				}
				pool.add(item);
				return 1;
			}
			case HERB -> {
				HerbKind kind = HerbKind.values()[entry.value()];
				for (int age : ages(entry.min(), entry.max())) {
					UniformContainerBase.Builder<?> item = LootItem.lootTableItem(HartaItems.herb(kind))
							.apply(SetComponentsFunction.setComponent(HartaComponents.HERB_AGE, age));
					if (weighted) {
						item.setWeight(entry.weight());
					}
					pool.add(item);
				}
				return AGE_STEPS;
			}
			case CORE -> {
				Element element = entry.element() == null ? Element.EARTH : entry.element();
				UniformContainerBase.Builder<?> item = LootItem.lootTableItem(HartaItems.coreItem(entry.value()))
						.apply(SetComponentsFunction.setComponent(HartaComponents.CORE_ELEMENT, element.id()))
						.apply(SetComponentsFunction.setComponent(DataComponents.CUSTOM_MODEL_DATA,
								new CustomModelData(List.of(), List.of(), List.of(), List.of(element.color()))));
				if (weighted) {
					item.setWeight(entry.weight() * scale);
				}
				pool.add(item);
				return 1;
			}
			case SLIP -> {
				int count = 0;
				for (TechniqueSpec spec : slipSpecs(entry.element())) {
					pool.add(LootItem.lootTableItem(JadeSlips.JADE_SLIP)
							.apply(SetComponentsFunction.setComponent(JadeSlips.TECHNIQUE, spec.id()))
							.apply(SetComponentsFunction.setComponent(DataComponents.RARITY,
									spec.grade() == TechniqueGrade.HUANG ? Rarity.COMMON : Rarity.UNCOMMON)));
					count++;
				}
				return count;
			}
			default -> {
				return 0;
			}
		}
	}

	/** {@link #AGE_STEPS} ages spread over {@code min}..{@code max}, slightly uneven so finds look natural. */
	static int[] ages(int min, int max) {
		int[] ages = new int[AGE_STEPS];
		for (int i = 0; i < AGE_STEPS; i++) {
			int base = min + (int) Math.round((max - min) * (double) i / (AGE_STEPS - 1));
			int jitter = ((i * 37) % 11) - 5;
			ages[i] = Math.max(min, Math.min(max, base + (i == 0 || i == AGE_STEPS - 1 ? 0 : jitter)));
		}
		return ages;
	}

	/**
	 * Specs a slip pool may hold: Kuning (Huang) or Misterius (Xuan) grade, slip allowed, of the Dao of {@code element}
	 * (Ice or Fire). Reads the frozen registry inside the MODIFY callback, never at init.
	 */
	private static List<TechniqueSpec> slipSpecs(@Nullable Element element) {
		Dao dao = element == Element.FIRE ? Dao.FIRE : Dao.ICE;
		List<TechniqueSpec> list = new ArrayList<>();
		if (!TechniqueRegistry.frozen()) {
			return list;
		}
		for (TechniqueSpec spec : TechniqueRegistry.ofDao(dao)) {
			if (spec.slipAllowed() && (spec.grade() == TechniqueGrade.HUANG || spec.grade() == TechniqueGrade.XUAN)) {
				list.add(spec);
			}
		}
		return list;
	}

	// ================================================================ uji relik

	/** The short table names {@code uji relik} accepts. */
	public static List<String> tableNames() {
		return List.copyOf(NAMES.keySet());
	}

	/**
	 * Rolls the (modified) built-in table {@code name} once into the player's inventory; returns the lines describing
	 * the phase-7 pools of that table (empty when the name is unknown).
	 */
	static List<Component> roll(ServerPlayer player, String name, List<ItemStack> out) {
		ResourceKey<LootTable> key = NAMES.get(name);
		List<Component> lines = new ArrayList<>();
		if (key == null) {
			return lines;
		}
		ServerLevel level = player.level();
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, player.position())
				.withOptionalParameter(LootContextParams.THIS_ENTITY, player)
				.create(LootContextParamSets.CHEST);
		out.addAll(table.getRandomItems(params));
		for (Pool pool : TABLES.get(key)) {
			int total = pool.empty();
			for (Entry entry : pool.entries()) {
				total += pool.empty() > 0 ? entry.weight() : 0;
			}
			for (Entry entry : pool.entries()) {
				double chance = pool.empty() > 0 ? pool.chance() * entry.weight() / (double) Math.max(1, total) : pool.chance();
				lines.add(Component.translatable("kultivator.harta.relic.line", HartaText.percent(chance), describe(entry)));
			}
		}
		return lines;
	}

	private static MutableComponent describe(Entry entry) {
		return switch (entry.type()) {
			case STONE -> Component.translatable("kultivator.harta.relic.stone", range(entry.min(), entry.max()), SpiritStones.name(entry.value()));
			case HERB -> Component.translatable("kultivator.harta.relic.herb", new ItemStack(HartaItems.herb(HerbKind.values()[entry.value()])).getHoverName(),
					range(entry.min(), entry.max()));
			case CORE -> Component.translatable("kultivator.harta.relic.core",
					BeastCores.create(entry.element() == null ? Element.EARTH : entry.element(), entry.value()).getHoverName());
			default -> Component.translatable("kultivator.harta.relic.slip", entry.element() == null ? Element.ICE.displayName()
					: entry.element().displayName(), slipSpecs(entry.element()).size());
		};
	}

	private static String range(int min, int max) {
		return min == max ? String.valueOf(min) : min + "-" + max;
	}

	// ================================================================ treasure sense

	/**
	 * Unlooted containers (block entities with a pending loot table) within {@code radius} blocks of {@code center}, read
	 * from the block-entity maps of loaded chunks only (DESIGN 19.3.13). Read-only.
	 */
	public static List<BlockPos> unlootedContainers(ServerLevel level, BlockPos center, int radius) {
		List<BlockPos> found = new ArrayList<>();
		int radiusSq = radius * radius;
		int minX = SectionPos.blockToSectionCoord(center.getX() - radius);
		int maxX = SectionPos.blockToSectionCoord(center.getX() + radius);
		int minZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
		int maxZ = SectionPos.blockToSectionCoord(center.getZ() + radius);
		for (int cx = minX; cx <= maxX; cx++) {
			for (int cz = minZ; cz <= maxZ; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (Map.Entry<BlockPos, BlockEntity> each : chunk.getBlockEntities().entrySet()) {
					if (each.getValue() instanceof RandomizableContainer container && container.getLootTable() != null
							&& each.getKey().distSqr(center) <= radiusSq) {
						found.add(each.getKey().immutable());
					}
				}
			}
		}
		return found;
	}

	private HartaLoot() {
	}
}
