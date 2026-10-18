package dev.kultivator.sect.build.flow;

import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.Bill;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.event.Storehouse;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The deposit table of DESIGN 21.11 and the deposit actions of 21.13: which item belongs to which of the nine categories
 * and what one is worth (centi-units; slab 1/2, stair 3/4 and wall 1 of their block), which items are exact (deposited 1:1,
 * never converted), and the ways materials go in (one stack, all of an item, everything accepted, "Setor yang dibutuhkan",
 * "Ambil dari Lumbung", the stele) and out (the Peti Galian, the Batu/Tanah/Kayu surplus). Depositing is the only thing
 * the player has to do (DESIGN 21 user decision 4): the engine reserves and starts the next building by itself as soon as
 * its bill is covered. Deposits give no PK by themselves.
 * <p>
 * The table is pure (item ids and vanilla tags), so the client's Bahan panel uses {@link #valueOf} too. Source: <i>A Record
 * of a Mortal's Journey</i> ch. 153 (Yue Lu Hall stores and supplies materials); <i>Return of the Mount Hua Sect</i> (the
 * Xi'an merchant guild backs Mount Hua after Chung Myung's group heals Elder Hwang Mun-yak, who declares himself Mount
 * Hua's sponsor: sect-mate deposits and the donor list). The values are game design.
 */
public final class Deposits {

	/** One deposit value: the category and centi-units per item, or {@code exact} (deposited 1:1 as itself). */
	public record Value(@Nullable Bahan cat, long centi, boolean exact) {
	}

	/** Exact items of DESIGN 21.11 (deposited 1:1, never converted from units). */
	public static final Set<String> EXACT = Set.of("cauldron", "smoker", "campfire", "soul_campfire", "bookshelf", "lectern", "brewing_stand",
			"lodestone", "crying_obsidian", "amethyst_block", "hay_block", "target", "bell", "barrel", "end_rod");
	/** The surplus of these categories (beyond the whole remaining bill) can be taken out 1:1 as these items. */
	public static final Bahan[] SURPLUS = {Bahan.BATU, Bahan.TANAH, Bahan.KAYU};
	/** Ticks between two thank-you lines of the same donor (DESIGN 21.13: at most one per player per minute). */
	public static final long THANKS_TICKS = 1200L;

	private static final Value NONE = new Value(null, 0L, false);
	private static final Value EXACT_VALUE = new Value(null, 0L, true);
	private static final Map<Item, Value> CACHE = new ConcurrentHashMap<>();
	private static final Map<String, Value> BASE = new HashMap<>();
	private static final Map<UUID, Long> THANKED = new HashMap<>();

	static {
		Bahan b = Bahan.BATU;
		for (String id : new String[]{"cobblestone", "mossy_cobblestone", "stone", "smooth_stone", "andesite", "polished_andesite", "granite",
				"polished_granite", "tuff", "polished_tuff", "tuff_bricks", "chiseled_tuff", "chiseled_tuff_bricks", "stone_bricks",
				"mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks", "basalt", "polished_basalt", "smooth_basalt", "sandstone",
				"cut_sandstone", "chiseled_sandstone", "smooth_sandstone", "red_sandstone", "cut_red_sandstone", "chiseled_red_sandstone",
				"smooth_red_sandstone", "end_stone", "end_stone_bricks"}) {
			put(id, b, 100L);
		}
		Bahan t = Bahan.TANAH;
		for (String id : new String[]{"dirt", "coarse_dirt", "rooted_dirt", "grass_block", "podzol", "mycelium", "mud", "packed_mud", "sand",
				"red_sand", "gravel", "soul_sand", "soul_soil"}) {
			put(id, t, 100L);
		}
		put("netherrack", t, 50L);
		put("stick", Bahan.KAYU, 25L);
		put("bamboo", Bahan.KAYU, 25L);
		Bahan g = Bahan.GENTENG;
		for (String id : new String[]{"deepslate", "cobbled_deepslate", "polished_deepslate", "deepslate_bricks", "cracked_deepslate_bricks",
				"deepslate_tiles", "cracked_deepslate_tiles", "chiseled_deepslate", "blackstone", "polished_blackstone", "polished_blackstone_bricks",
				"cracked_polished_blackstone_bricks", "chiseled_polished_blackstone", "gilded_blackstone", "clay", "bricks", "terracotta", "mud_bricks"}) {
			put(id, g, 100L);
		}
		put("clay_ball", g, 25L);
		put("brick", g, 25L);
		Bahan k = Bahan.KAPUR;
		for (String id : new String[]{"calcite", "diorite", "polished_diorite", "quartz_block", "chiseled_quartz_block", "quartz_pillar", "quartz_bricks",
				"smooth_quartz", "white_concrete", "white_concrete_powder", "bone_block", "snow_block"}) {
			put(id, k, 100L);
		}
		put("quartz", k, 25L);
		put("bone", k, 33L);
		put("bone_meal", k, 11L);
		put("snowball", k, 25L);
		Bahan p = Bahan.PERNIS;
		for (String id : new String[]{"cinnabar", "polished_cinnabar", "cinnabar_bricks", "chiseled_cinnabar", "beetroot", "red_concrete",
				"red_concrete_powder", "yellow_concrete", "yellow_concrete_powder", "blue_concrete", "blue_concrete_powder", "red_terracotta",
				"red_glazed_terracotta", "red_wool"}) {
			put(id, p, 100L);
		}
		Bahan f = Bahan.FLORA;
		for (String id : new String[]{"azalea", "flowering_azalea", "lily_pad", "moss_block", "pale_moss_block", "big_dripleaf", "small_dripleaf",
				"firefly_bush"}) {
			put(id, f, 100L);
		}
		for (String id : new String[]{"sunflower", "lilac", "rose_bush", "peony", "pitcher_plant", "spore_blossom"}) {
			put(id, f, 200L);
		}
		put("moss_carpet", f, 50L);
		put("pale_moss_carpet", f, 50L);
		for (String id : new String[]{"pink_petals", "wildflowers", "short_grass", "tall_grass", "fern", "large_fern", "bush", "short_dry_grass",
				"tall_dry_grass", "seagrass", "leaf_litter", "hanging_roots"}) {
			put(id, f, 25L);
		}
		Bahan l = Bahan.LOGAM;
		for (String id : new String[]{"iron_ingot", "raw_iron", "iron_chain", "chain", "lantern", "soul_lantern", "glowstone", "shroomlight",
				"ochre_froglight", "verdant_froglight", "pearlescent_froglight", "sea_lantern"}) {
			put(id, l, 100L);
		}
		put("iron_nugget", l, 11L);
		put("iron_block", l, 900L);
		put("torch", l, 25L);
		put("soul_torch", l, 25L);
		put("copper_torch", l, 25L);
		put("glowstone_dust", l, 25L);
		put("sea_pickle", l, 50L);
		put("iron_bars", l, 37L);
		Bahan j = Bahan.PERMATA;
		put("gold_ingot", j, 100L);
		put("raw_gold", j, 100L);
		put("gold_nugget", j, 11L);
		put("gold_block", j, 900L);
		put("raw_gold_block", j, 900L);
		put("copper_ingot", j, 50L);
		put("raw_copper", j, 50L);
		put("raw_copper_block", j, 450L);
		put("copper_block", j, 450L);
		put("exposed_copper", j, 450L);
		put("weathered_copper", j, 450L);
		put("oxidized_copper", j, 450L);
		put("prismarine_shard", j, 25L);
		put("prismarine_crystals", j, 25L);
		put("prismarine", j, 100L);
		put("prismarine_bricks", j, 200L);
		put("dark_prismarine", j, 200L);
		put("emerald", j, 200L);
		put("emerald_block", j, 1800L);
		put("lapis_lazuli", j, 50L);
		put("lapis_block", j, 450L);
		put("diamond", j, 400L);
		for (String id : new String[]{"sulfur", "polished_sulfur", "sulfur_bricks", "chiseled_sulfur", "potent_sulfur"}) {
			put(id, j, 50L);
		}
	}

	private static void put(String id, Bahan cat, long centi) {
		BASE.put(id, new Value(cat, centi, false));
	}

	// ================================================================== the table

	/** The deposit value of {@code item}, or null when the Gudang does not accept it. */
	public static @Nullable Value valueOf(@Nullable Item item) {
		if (item == null || item == Items.AIR) {
			return null;
		}
		Value value = CACHE.computeIfAbsent(item, Deposits::resolve);
		return value == NONE ? null : value;
	}

	/** True for an exact item (deposited 1:1 as itself). */
	public static boolean isExact(Item item) {
		Value value = valueOf(item);
		return value != null && value.exact();
	}

	private static Value resolve(Item item) {
		Identifier key = BuiltInRegistries.ITEM.getKey(item);
		if (!"minecraft".equals(key.getNamespace())) {
			return NONE;
		}
		String id = key.getPath();
		if (EXACT.contains(id)) {
			return EXACT_VALUE;
		}
		Value direct = BASE.get(id);
		if (direct != null) {
			return direct;
		}
		if (id.endsWith("_copper_lantern") || id.equals("copper_lantern")) {
			return new Value(Bahan.LOGAM, 100L, false);
		}
		Value copper = copper(id);
		if (copper != null) {
			return copper;
		}
		if (has(item, ItemTags.LOGS)) {
			return new Value(Bahan.KAYU, 400L, false);
		}
		if (has(item, ItemTags.BAMBOO_BLOCKS)) {
			return new Value(Bahan.KAYU, 200L, false);
		}
		if (has(item, ItemTags.PLANKS)) {
			return new Value(Bahan.KAYU, 100L, false);
		}
		if (has(item, ItemTags.WOODEN_SLABS)) {
			return new Value(Bahan.KAYU, 50L, false);
		}
		if (has(item, ItemTags.WOODEN_STAIRS)) {
			return new Value(Bahan.KAYU, 75L, false);
		}
		if (has(item, ItemTags.SAPLINGS)) {
			return new Value(Bahan.FLORA, 100L, false);
		}
		if (has(item, ItemTags.LEAVES)) {
			return new Value(Bahan.FLORA, 25L, false);
		}
		if (has(item, BlockItemTags.SMALL_FLOWERS.item())) {
			return new Value(Bahan.FLORA, 100L, false);
		}
		if (has(item, ItemTags.CANDLES)) {
			return new Value(Bahan.LOGAM, 50L, false);
		}
		if (has(item, ItemTags.DYES) || id.endsWith("_dye")) {
			return new Value(Bahan.PERNIS, 100L, false);
		}
		Value derived = derived(id);
		if (derived != null) {
			return derived;
		}
		if (id.endsWith("_glazed_terracotta") || id.endsWith("_terracotta")) {
			return new Value(Bahan.GENTENG, 100L, false);
		}
		return NONE;
	}

	/** A slab (1/2), stair (3/4) or wall (1) of a block in the table. */
	private static @Nullable Value derived(String id) {
		String[][] forms = {{"_slab", "50"}, {"_stairs", "75"}, {"_wall", "100"}};
		for (String[] form : forms) {
			if (!id.endsWith(form[0])) {
				continue;
			}
			String base = id.substring(0, id.length() - form[0].length());
			for (String candidate : new String[]{base + "_block", base + "s", base}) {
				Value full = BASE.get(candidate);
				if (full != null && full.centi() >= 100L) {
					return new Value(full.cat(), full.centi() * Long.parseLong(form[1]) / 100L, false);
				}
			}
		}
		return null;
	}

	/** Copper building blocks: cut or waxed (any) 2, slab 1, stair 1 1/2, bars 3/8, chain 1 (DESIGN 21.11 and the cost invariant). */
	private static @Nullable Value copper(String id) {
		if (!id.contains("copper") || id.contains("ingot") || id.contains("nugget") || id.startsWith("raw_") || id.contains("golem")
				|| id.contains("chest") || id.contains("horn") || id.contains("torch") || id.contains("lantern")) {
			return null;
		}
		boolean building = id.contains("cut_copper") || id.contains("chiseled_copper") || id.contains("copper_grate") || id.contains("copper_door")
				|| id.contains("copper_trapdoor") || id.contains("copper_bulb") || id.contains("copper_bars") || id.contains("copper_chain")
				|| id.startsWith("waxed_");
		if (!building) {
			return null;
		}
		if (id.endsWith("_bars")) {
			return new Value(Bahan.PERMATA, 37L, false);
		}
		if (id.endsWith("_chain")) {
			return new Value(Bahan.PERMATA, 100L, false);
		}
		if (id.endsWith("_slab")) {
			return new Value(Bahan.PERMATA, 100L, false);
		}
		if (id.endsWith("_stairs")) {
			return new Value(Bahan.PERMATA, 150L, false);
		}
		return new Value(Bahan.PERMATA, 200L, false);
	}

	private static boolean has(Item item, TagKey<Item> tag) {
		try {
			return item.builtInRegistryHolder().is(tag);
		} catch (RuntimeException exception) {
			return false;
		}
	}

	// ================================================================== what is still needed

	/** What the current and the next tier still need beyond the Gudang (the "Setor yang dibutuhkan" target). */
	public static Bill neededNow(UUID owner) {
		EngineApi engine = EngineApi.get();
		if (engine.template(owner) <= 0) {
			return Bill.EMPTY;
		}
		int tier = engine.tier(owner);
		Bill need = Bill.EMPTY;
		if (tier + 1 <= 9) {
			need = need.plus(engine.remainingBill(owner, tier + 1));
		}
		if (tier + 2 <= 9) {
			need = need.plus(engine.remainingBill(owner, tier + 2));
		}
		return need.minus(Ledger.INSTANCE.balance(owner));
	}

	/** How many more of an exact item the whole remaining template can use (0 without a template). */
	public static int exactRoom(UUID owner, Item item) {
		EngineApi engine = EngineApi.get();
		if (engine.template(owner) <= 0) {
			return 0;
		}
		return Math.max(0, engine.remainingBill(owner, 0).exact(item) - Ledger.INSTANCE.exact(owner, item));
	}

	// ================================================================== putting materials in

	/** The result of a deposit: centi-units of a category, or exact items, and the category or item. */
	public record Result(@Nullable Bahan cat, @Nullable Item item, long centi, int count) {
		static final Result EMPTY = new Result(null, null, 0L, 0);

		boolean empty() {
			return count <= 0;
		}
	}

	/**
	 * Deposits at most {@code max} items from inventory slot {@code slot} of {@code depositor} into {@code owner}'s Gudang.
	 * {@code why} collects the reason when nothing could go in.
	 */
	public static Result depositSlot(ServerPlayer depositor, UUID owner, int slot, int max, @Nullable List<Component> why) {
		Inventory inventory = depositor.getInventory();
		if (slot < 0 || slot >= Inventory.INVENTORY_SIZE) {
			return Result.EMPTY;
		}
		ItemStack stack = inventory.getItem(slot);
		if (stack.isEmpty()) {
			return Result.EMPTY;
		}
		Value value = valueOf(stack.getItem());
		if (value == null) {
			add(why, Component.translatable("kultivator.sectbuild.deposit.refused", stack.getHoverName()));
			return Result.EMPTY;
		}
		int count = Math.min(stack.getCount(), Math.max(0, max));
		if (value.exact()) {
			int room = exactRoom(owner, stack.getItem());
			if (room <= 0) {
				add(why, Component.translatable(EngineApi.get().template(owner) <= 0 ? "kultivator.sectbuild.deposit.exact_no_template"
						: "kultivator.sectbuild.deposit.exact_not_needed", stack.getHoverName()));
				return Result.EMPTY;
			}
			count = Math.min(count, room);
			if (count <= 0) {
				return Result.EMPTY;
			}
			Item item = stack.getItem();
			inventory.removeItem(slot, count);
			Ledger.INSTANCE.addExact(owner, item, count);
			return new Result(null, item, 0L, count);
		}
		long room = Ledger.INSTANCE.room(owner, value.cat());
		count = (int) Math.min(count, room / Math.max(1L, value.centi()));
		if (count <= 0) {
			add(why, Component.translatable("kultivator.sectbuild.deposit.full", value.cat().displayName()));
			return Result.EMPTY;
		}
		inventory.removeItem(slot, count);
		long centi = value.centi() * count;
		Ledger.INSTANCE.add(owner, value.cat(), centi);
		return new Result(value.cat(), null, centi, count);
	}

	/** One stack (a click on the deposit grid, the stele). */
	public static void depositOne(ServerPlayer depositor, UUID owner, int slot) {
		List<Component> why = new ArrayList<>();
		Result result = depositSlot(depositor, owner, slot, Integer.MAX_VALUE, why);
		finish(depositor, owner, List.of(result), why);
	}

	/** Every stack of one item (shift-click on the grid). */
	public static void depositItem(ServerPlayer depositor, UUID owner, Item item) {
		List<Component> why = new ArrayList<>();
		List<Result> results = new ArrayList<>();
		Inventory inventory = depositor.getInventory();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			if (inventory.getItem(slot).is(item)) {
				results.add(depositSlot(depositor, owner, slot, Integer.MAX_VALUE, why));
			}
		}
		finish(depositor, owner, results, why);
	}

	/** Every accepted item in the bag (exact items only as far as the template needs them). */
	public static void depositAll(ServerPlayer depositor, UUID owner) {
		List<Component> why = new ArrayList<>();
		List<Result> results = new ArrayList<>();
		Inventory inventory = depositor.getInventory();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			if (valueOf(inventory.getItem(slot).getItem()) != null) {
				results.add(depositSlot(depositor, owner, slot, Integer.MAX_VALUE, null));
			}
		}
		if (results.stream().allMatch(Result::empty)) {
			why.add(Component.translatable("kultivator.sectbuild.deposit.nothing"));
		}
		finish(depositor, owner, results, why);
	}

	/** "Setor yang dibutuhkan": only what the current and the next tier still need, taken from the bag in one go. */
	public static void depositNeeded(ServerPlayer depositor, UUID owner) {
		List<Component> why = new ArrayList<>();
		List<Result> results = new ArrayList<>();
		if (EngineApi.get().template(owner) <= 0) {
			why.add(Component.translatable("kultivator.sectbuild.deposit.no_template"));
			finish(depositor, owner, results, why);
			return;
		}
		Bill need = neededNow(owner);
		long[] left = need.centi();
		Map<Item, Integer> exactLeft = new HashMap<>(need.exact());
		Inventory inventory = depositor.getInventory();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			Value value = valueOf(stack.getItem());
			if (value == null) {
				continue;
			}
			if (value.exact()) {
				int want = exactLeft.getOrDefault(stack.getItem(), 0);
				if (want > 0) {
					Result result = depositSlot(depositor, owner, slot, want, null);
					exactLeft.put(stack.getItem(), want - result.count());
					results.add(result);
				}
				continue;
			}
			int index = value.cat().ordinal();
			if (left[index] <= 0L) {
				continue;
			}
			int want = (int) Math.min(Integer.MAX_VALUE, (left[index] + value.centi() - 1L) / Math.max(1L, value.centi()));
			Result result = depositSlot(depositor, owner, slot, want, null);
			left[index] -= result.centi();
			results.add(result);
		}
		if (results.stream().allMatch(Result::empty)) {
			why.add(need.isEmpty() ? Component.translatable("kultivator.sectbuild.deposit.covered")
					: Component.translatable("kultivator.sectbuild.deposit.none_needed", Fmt.joined(need, 4)));
		}
		finish(depositor, owner, results, why);
	}

	/** "Ambil dari Lumbung": accepted items from the 27-slot storehouse; what cannot go in goes back (or to the Peti Galian). */
	public static void fromStorehouse(ServerPlayer player, UUID owner) {
		List<ItemStack> taken = Storehouse.take(owner, stack -> {
			Value value = valueOf(stack.getItem());
			return value != null && (!value.exact() || exactRoom(owner, stack.getItem()) > 0);
		}, 27 * 64);
		List<Result> results = new ArrayList<>();
		for (ItemStack stack : taken) {
			Value value = valueOf(stack.getItem());
			ItemStack rest = stack.copy();
			if (value != null && value.exact()) {
				int count = Math.min(rest.getCount(), exactRoom(owner, rest.getItem()));
				if (count > 0) {
					Ledger.INSTANCE.addExact(owner, rest.getItem(), count);
					results.add(new Result(null, rest.getItem(), 0L, count));
					rest.shrink(count);
				}
			} else if (value != null) {
				int count = (int) Math.min(rest.getCount(), Ledger.INSTANCE.room(owner, value.cat()) / Math.max(1L, value.centi()));
				if (count > 0) {
					Ledger.INSTANCE.add(owner, value.cat(), value.centi() * count);
					results.add(new Result(value.cat(), null, value.centi() * count, count));
					rest.shrink(count);
				}
			}
			if (!rest.isEmpty()) {
				ItemStack back = Storehouse.insert(owner, rest);
				if (!back.isEmpty()) {
					Ledger.INSTANCE.salvage(owner, back);
				}
			}
		}
		List<Component> why = new ArrayList<>();
		if (results.isEmpty()) {
			why.add(Component.translatable("kultivator.sectbuild.deposit.storehouse_empty"));
		}
		finish(player, owner, results, why);
	}

	/** After deposits: missions, donors, the actionbar line, a chime and the deposit swirl at the stele. */
	private static void finish(ServerPlayer depositor, UUID owner, List<Result> results, List<Component> why) {
		long[] centi = new long[Bahan.COUNT];
		Map<Item, Integer> exact = new HashMap<>();
		int count = 0;
		for (Result result : results) {
			if (result == null || result.empty()) {
				continue;
			}
			count += result.count();
			if (result.cat() != null) {
				centi[result.cat().ordinal()] += result.centi();
			} else if (result.item() != null) {
				exact.merge(result.item(), result.count(), Integer::sum);
			}
		}
		if (count <= 0) {
			if (!why.isEmpty()) {
				Msg.actionBar(depositor, why.get(0).copy().withStyle(ChatFormatting.YELLOW));
				depositor.level().playSound(null, depositor.getX(), depositor.getY(), depositor.getZ(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(),
						SoundSource.PLAYERS, 0.4F, 0.7F);
			}
			return;
		}
		Bill added = new Bill(centi, exact);
		for (Bahan cat : Bahan.values()) {
			if (centi[cat.ordinal()] > 0L) {
				BuildMissions.onDeposit(owner, cat, centi[cat.ordinal()]);
			}
		}
		long units = added.totalCenti() / Bahan.CENTI + exact.values().stream().mapToInt(Integer::intValue).sum();
		if (!depositor.getUUID().equals(owner)) {
			donor(depositor, owner, units);
		}
		Msg.actionBar(depositor, summary(owner, added));
		depositor.level().playSound(null, depositor.getX(), depositor.getY(), depositor.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
				0.6F, 1.2F + depositor.getRandom().nextFloat() * 0.3F);
		Ceremonies.depositSwirl(owner, depositor, added);
		FlowData.dirty();
	}

	/** "+64 Batu · Batu 412 / 1570 (tingkat ini)". */
	static MutableComponent summary(UUID owner, Bill added) {
		EngineApi engine = EngineApi.get();
		int tier = engine.tier(owner);
		Bill tierNeed = engine.template(owner) > 0 && tier < 9 ? engine.remainingBill(owner, tier + 1) : Bill.EMPTY;
		MutableComponent line = Component.empty();
		boolean first = true;
		for (Bahan cat : Bahan.values()) {
			long centi = added.centi(cat);
			if (centi <= 0L) {
				continue;
			}
			if (!first) {
				line.append(Component.literal("  "));
			}
			first = false;
			long have = Ledger.INSTANCE.centi(owner, cat);
			long need = tierNeed.centi(cat);
			line.append(need > 0L ? Component.translatable("kultivator.sectbuild.deposit.line_need", Fmt.units(centi), cat.displayName(),
					Fmt.units(have), Fmt.units(need)) : Component.translatable("kultivator.sectbuild.deposit.line", Fmt.units(centi), cat.displayName(),
					Fmt.units(have)));
		}
		for (Map.Entry<Item, Integer> entry : added.exact().entrySet()) {
			if (!first) {
				line.append(Component.literal("  "));
			}
			first = false;
			int have = Ledger.INSTANCE.exact(owner, entry.getKey());
			int need = tierNeed.exact(entry.getKey());
			line.append(Component.translatable("kultivator.sectbuild.deposit.line_exact", entry.getValue(), entry.getKey().getDefaultInstance().getHoverName(),
					have, Math.max(need, have)));
		}
		return line.withStyle(ChatFormatting.GREEN);
	}

	/** A sect mate deposited: the donor list, a thank-you line in sect chat (once a minute per donor), the advancement. */
	private static void donor(ServerPlayer depositor, UUID owner, long units) {
		var section = FlowData.of(owner);
		var donors = FlowData.sub(section, "donors");
		String key = depositor.getUUID().toString();
		donors.putLong(key, donors.getLongOr(key, 0L) + units);
		FlowData.sub(section, "donor_names").putString(key, depositor.getName().getString());
		FlowData.dirty();
		dev.kultivator.util.Advancements.grant(depositor, "sect_donor");
		long now = FlowData.gameTime();
		Long last = THANKED.get(depositor.getUUID());
		if (last != null && now - last < THANKS_TICKS) {
			return;
		}
		THANKED.put(depositor.getUUID(), now);
		Halls.HallView hall = Halls.hallOf(owner);
		Component name = hall == null || hall.name().isBlank() ? Component.translatable("kultivator.sectbuild.sect_default")
				: Component.literal(hall.name());
		Ceremonies.sectChat(owner, Component.translatable("kultivator.sectbuild.donor.thanks", depositor.getDisplayName(), units, name)
				.withStyle(ChatFormatting.GOLD));
	}

	// ================================================================== taking materials out

	/** One stack of a Peti Galian item into the player's bag. */
	public static void takeSalvage(ServerPlayer player, UUID owner, Item item) {
		int max = new ItemStack(item).getMaxStackSize();
		long taken = Ledger.INSTANCE.takeSalvage(owner, item, max);
		if (taken > 0L) {
			give(player, new ItemStack(item, (int) taken));
		}
	}

	/** A whole stack with its own data from the Peti Galian. */
	public static void takeRich(ServerPlayer player, UUID owner, int index) {
		ItemStack stack = Ledger.INSTANCE.takeRich(owner, index);
		if (!stack.isEmpty()) {
			give(player, stack);
		}
	}

	/** Units of a Batu/Tanah/Kayu surplus beyond the whole remaining bill (0 for the other categories). */
	public static long surplusUnits(UUID owner, Bahan cat) {
		boolean allowed = false;
		for (Bahan each : SURPLUS) {
			allowed |= each == cat;
		}
		if (!allowed) {
			return 0L;
		}
		long have = Ledger.INSTANCE.centi(owner, cat);
		long need = EngineApi.get().template(owner) > 0 ? EngineApi.get().remainingBill(owner, 0).centi(cat) : 0L;
		return Math.max(0L, (have - need) / Bahan.CENTI);
	}

	/** One stack of surplus Batu, Tanah or Kayu as cobblestone, dirt or oak planks (1:1). */
	public static void takeSurplus(ServerPlayer player, UUID owner, Bahan cat) {
		long units = Math.min(64L, surplusUnits(owner, cat));
		if (units <= 0L) {
			Msg.actionBar(player, Component.translatable("kultivator.sectbuild.surplus.none", cat.displayName()).withStyle(ChatFormatting.YELLOW));
			return;
		}
		Item item = cat == Bahan.BATU ? Items.COBBLESTONE : cat == Bahan.TANAH ? Items.DIRT : Items.OAK_PLANKS;
		long taken = Ledger.INSTANCE.take(owner, cat, units * Bahan.CENTI) / Bahan.CENTI;
		if (taken > 0L) {
			give(player, new ItemStack(item, (int) taken));
			// Taken-out surplus deposited straight back never counts toward an accepted Setoran offer.
			BuildMissions.onWithdraw(owner, cat, taken * Bahan.CENTI);
		}
	}

	/** Puts a stack into the bag; what does not fit lands at the player's feet (no block is touched). */
	static void give(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), stack);
			drop.addTag(NoGrief.TAG);
			drop.setPickUpDelay(0);
			player.level().addFreshEntity(drop);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.1F);
	}

	private static void add(@Nullable List<Component> why, Component line) {
		if (why != null) {
			why.add(line);
		}
	}

	static void clearSession() {
		THANKED.clear();
	}

	private Deposits() {
	}
}
