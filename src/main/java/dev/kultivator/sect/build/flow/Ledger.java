package dev.kultivator.sect.build.flow;

import dev.kultivator.Kultivator;
import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.Bill;
import dev.kultivator.sect.build.api.MaterialLedger;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gudang Bahan Sekte and Peti Galian (DESIGN 21.11, 21.13): the nine categories in centi-units (at most
 * {@link Bahan#MAX_UNITS} units each), the exact items, and the salvage counter {@code item -> count} that has no limit and
 * loses nothing (stacks carrying their own data are kept whole in a separate list). It is the {@link MaterialLedger} the
 * engine reserves from (all-or-nothing), releases to and credits digging, refunds and dismantling to. Everything lives in
 * the owner's {@code build_flow} section ({@link FlowData}).
 * <p>
 * Source: <i>A Record of a Mortal's Journey</i> ch. 153, Yue Lu Hall stores and supplies materials (the Gudang). The
 * categories, the numbers and the refunds are game design.
 */
public final class Ledger implements MaterialLedger {

	public static final Ledger INSTANCE = new Ledger();

	/** At most this many different salvage items are kept apart by id (more never happens with vanilla drops). */
	public static final int MAX_SALVAGE_KINDS = 512;
	/** Stacks with their own data (names, enchantments, contents) kept whole, at most this many (older ones merge by id). */
	public static final int MAX_RICH_STACKS = 256;
	/** The Catatan line about refunded sect blocks is written at most this often per owner (ticks). */
	public static final long BREAK_LOG_TICKS = 1200L;

	/** The item a category's overflow or a "keep the dug items" credit turns into (1 unit each). */
	private static final Item[] REPRESENTATIVE = {Items.COBBLESTONE, Items.DIRT, Items.OAK_PLANKS, Items.COBBLED_DEEPSLATE, Items.CALCITE,
			Items.DYE.red(), Items.MOSS_BLOCK, Items.IRON_INGOT, Items.GOLD_INGOT};

	private static final Map<UUID, Long> BREAK_CREDIT = new HashMap<>();
	private static final Map<UUID, Long> BREAK_LOGGED = new HashMap<>();

	// ================================================================== reading

	/** What the Gudang holds now (categories and exact items). */
	public Bill balance(UUID owner) {
		CompoundTag section = FlowData.of(owner);
		long[] centi = new long[Bahan.COUNT];
		CompoundTag ledger = section.getCompoundOrEmpty("ledger");
		for (Bahan cat : Bahan.values()) {
			centi[cat.ordinal()] = Math.max(0L, ledger.getLongOr(cat.id(), 0L));
		}
		Map<Item, Integer> exact = new LinkedHashMap<>();
		CompoundTag items = section.getCompoundOrEmpty("exact");
		for (String key : items.keySet()) {
			Item item = item(key);
			if (item != null && items.getIntOr(key, 0) > 0) {
				exact.put(item, items.getIntOr(key, 0));
			}
		}
		return new Bill(centi, exact);
	}

	public long centi(UUID owner, Bahan cat) {
		return Math.max(0L, FlowData.of(owner).getCompoundOrEmpty("ledger").getLongOr(cat.id(), 0L));
	}

	public int exact(UUID owner, Item item) {
		return FlowData.of(owner).getCompoundOrEmpty("exact").getIntOr(id(item), 0);
	}

	// ================================================================== writing

	/** Adds centi-units to a category; whatever would pass the cap goes to the Peti Galian as items (never lost). */
	public void add(UUID owner, Bahan cat, long centi) {
		if (centi <= 0L || cat == null) {
			return;
		}
		CompoundTag ledger = FlowData.sub(FlowData.of(owner), "ledger");
		long have = Math.max(0L, ledger.getLongOr(cat.id(), 0L));
		long room = Math.max(0L, Bahan.MAX_CENTI - have);
		long kept = Math.min(room, centi);
		ledger.putLong(cat.id(), have + kept);
		if (centi > kept) {
			toItems(owner, cat, centi - kept);
		}
		FlowData.dirty();
	}

	/** Takes up to {@code centi} from a category; the amount actually taken. */
	public long take(UUID owner, Bahan cat, long centi) {
		CompoundTag ledger = FlowData.sub(FlowData.of(owner), "ledger");
		long have = Math.max(0L, ledger.getLongOr(cat.id(), 0L));
		long taken = Math.max(0L, Math.min(have, centi));
		ledger.putLong(cat.id(), have - taken);
		FlowData.dirty();
		return taken;
	}

	public void addExact(UUID owner, Item item, int count) {
		if (item == null || count <= 0) {
			return;
		}
		CompoundTag exact = FlowData.sub(FlowData.of(owner), "exact");
		String key = id(item);
		exact.putInt(key, (int) Math.min(Integer.MAX_VALUE, (long) exact.getIntOr(key, 0) + count));
		FlowData.dirty();
	}

	/** Room left in a category, in centi-units. */
	public long room(UUID owner, Bahan cat) {
		return Math.max(0L, Bahan.MAX_CENTI - centi(owner, cat));
	}

	/** {@code uji sekte sumber}: sets every category to at least {@code centiEach} and adds the exact items of {@code extra}. */
	public void grant(UUID owner, long centiEach, Bill extra) {
		for (Bahan cat : Bahan.values()) {
			long want = Math.min(Bahan.MAX_CENTI, centiEach + (extra == null ? 0L : extra.centi(cat)));
			long have = centi(owner, cat);
			if (want > have) {
				add(owner, cat, want - have);
			}
		}
		if (extra != null) {
			extra.exact().forEach((item, count) -> {
				int missing = count - exact(owner, item);
				if (missing > 0) {
					addExact(owner, item, missing);
				}
			});
		}
	}

	// ================================================================== MaterialLedger (the engine calls these)

	@Override
	public boolean reserve(UUID owner, Bill bill) {
		if (bill == null || bill.isEmpty()) {
			return true;
		}
		if (!FlowData.hasHall(owner) || !balance(owner).covers(bill)) {
			return false;
		}
		CompoundTag section = FlowData.of(owner);
		CompoundTag ledger = FlowData.sub(section, "ledger");
		for (Bahan cat : Bahan.values()) {
			long need = bill.centi(cat);
			if (need > 0L) {
				ledger.putLong(cat.id(), Math.max(0L, ledger.getLongOr(cat.id(), 0L) - need));
			}
		}
		CompoundTag exact = FlowData.sub(section, "exact");
		for (Map.Entry<Item, Integer> entry : bill.exact().entrySet()) {
			String key = id(entry.getKey());
			exact.putInt(key, Math.max(0, exact.getIntOr(key, 0) - entry.getValue()));
		}
		FlowData.dirty();
		return true;
	}

	@Override
	public void release(UUID owner, Bill unused) {
		if (unused == null || unused.isEmpty()) {
			return;
		}
		for (Bahan cat : Bahan.values()) {
			add(owner, cat, unused.centi(cat));
		}
		unused.exact().forEach((item, count) -> addExact(owner, item, count));
	}

	@Override
	public void credit(UUID owner, Bahan cat, long centi, String reason) {
		if (cat == null || centi <= 0L) {
			return;
		}
		if ("dig".equals(reason) && !FlowData.flag(owner, FlowData.F_EXCAVATION)) {
			// "Pakai hasil galian" is off: the dug material is kept as items in the Peti Galian instead.
			toItems(owner, cat, centi);
			return;
		}
		add(owner, cat, centi);
		if ("break".equals(reason) || "explosion".equals(reason)) {
			noteBreak(owner, centi);
		}
	}

	@Override
	public void salvage(UUID owner, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		if (!stack.getComponentsPatch().isEmpty() && keepRich(owner, stack)) {
			return;
		}
		CompoundTag counter = FlowData.sub(FlowData.of(owner), "salvage");
		String key = id(stack.getItem());
		if (!counter.contains(key) && counter.size() >= MAX_SALVAGE_KINDS) {
			// Never lost: an unusual kind past the limit is credited by its category when it has one.
			Deposits.Value value = Deposits.valueOf(stack.getItem());
			if (value != null && value.cat() != null) {
				add(owner, value.cat(), value.centi() * stack.getCount());
				return;
			}
		}
		counter.putLong(key, counter.getLongOr(key, 0L) + stack.getCount());
		FlowData.dirty();
	}

	@Override
	public long depositValueCenti(Item item) {
		Deposits.Value value = Deposits.valueOf(item);
		return value == null || value.exact() ? 0L : value.centi();
	}

	@Override
	public @Nullable Bahan categoryOf(Item item) {
		Deposits.Value value = Deposits.valueOf(item);
		return value == null || value.exact() ? null : value.cat();
	}

	// ================================================================== Peti Galian

	/** The salvage counter in insertion order (item -> count). */
	public Map<Item, Long> salvageCounts(UUID owner) {
		Map<Item, Long> counts = new LinkedHashMap<>();
		CompoundTag counter = FlowData.of(owner).getCompoundOrEmpty("salvage");
		for (String key : counter.keySet()) {
			Item item = item(key);
			long count = counter.getLongOr(key, 0L);
			if (item != null && count > 0L) {
				counts.put(item, count);
			}
		}
		return counts;
	}

	/** Takes at most {@code max} of {@code item} out of the counter; how many were taken. */
	public long takeSalvage(UUID owner, Item item, long max) {
		CompoundTag counter = FlowData.sub(FlowData.of(owner), "salvage");
		String key = id(item);
		long have = counter.getLongOr(key, 0L);
		long taken = Math.max(0L, Math.min(have, max));
		if (have - taken <= 0L) {
			counter.remove(key);
		} else {
			counter.putLong(key, have - taken);
		}
		FlowData.dirty();
		return taken;
	}

	/** The whole stacks with their own data (names, enchantments, contents). */
	public List<ItemStack> richStacks(UUID owner) {
		List<ItemStack> stacks = new ArrayList<>();
		RegistryOps<Tag> ops = ops();
		if (ops == null) {
			return stacks;
		}
		for (Tag tag : FlowData.of(owner).getListOrEmpty("salvage_rich")) {
			if (tag instanceof CompoundTag entry) {
				entry.read("item", ItemStack.CODEC, ops).ifPresent(stacks::add);
			}
		}
		return stacks;
	}

	/** Takes the whole rich stack at {@code index}; empty when there is none. */
	public ItemStack takeRich(UUID owner, int index) {
		ListTag list = FlowData.list(FlowData.of(owner), "salvage_rich");
		RegistryOps<Tag> ops = ops();
		if (ops == null || index < 0 || index >= list.size() || !(list.get(index) instanceof CompoundTag entry)) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = entry.read("item", ItemStack.CODEC, ops).orElse(ItemStack.EMPTY);
		list.remove(index);
		FlowData.dirty();
		return stack;
	}

	private boolean keepRich(UUID owner, ItemStack stack) {
		RegistryOps<Tag> ops = ops();
		ListTag list = FlowData.list(FlowData.of(owner), "salvage_rich");
		if (ops == null || list.size() >= MAX_RICH_STACKS) {
			return false;
		}
		CompoundTag entry = new CompoundTag();
		entry.store("item", ItemStack.CODEC, ops, stack.copy());
		list.add(entry);
		FlowData.dirty();
		return true;
	}

	/** Turns centi-units of a category into its representative items in the Peti Galian (the remainder waits for more). */
	private void toItems(UUID owner, Bahan cat, long centi) {
		CompoundTag rest = FlowData.sub(FlowData.of(owner), "dig_rest");
		long total = rest.getLongOr(cat.id(), 0L) + centi;
		long items = total / Bahan.CENTI;
		rest.putLong(cat.id(), total - items * Bahan.CENTI);
		if (items > 0L) {
			CompoundTag counter = FlowData.sub(FlowData.of(owner), "salvage");
			String key = id(REPRESENTATIVE[cat.ordinal()]);
			counter.putLong(key, counter.getLongOr(key, 0L) + items);
		}
		FlowData.dirty();
	}

	/** Refunded sect blocks: one rate-limited Catatan line per minute per owner. */
	private static void noteBreak(UUID owner, long centi) {
		BREAK_CREDIT.merge(owner, centi, Long::sum);
		long now = FlowData.gameTime();
		Long last = BREAK_LOGGED.get(owner);
		if (last == null || now - last >= BREAK_LOG_TICKS) {
			BREAK_LOGGED.put(owner, now);
			long total = BREAK_CREDIT.getOrDefault(owner, 0L);
			BREAK_CREDIT.put(owner, 0L);
			Halls.log(owner, Component.translatable("kultivator.sectbuild.log.refund", Fmt.units(total)));
		}
	}

	static void clearSession() {
		BREAK_CREDIT.clear();
		BREAK_LOGGED.clear();
	}

	// ================================================================== helpers

	static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	static @Nullable Item item(String key) {
		Identifier id = Identifier.tryParse(key);
		Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
		return item == null || item == Items.AIR ? null : item;
	}

	private static @Nullable RegistryOps<Tag> ops() {
		MinecraftServer server = FlowData.server();
		return server == null ? null : server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
	}

	static void warnBroken(String what, Exception exception) {
		Kultivator.LOGGER.warn("Pembangunan Sekte: {} failed", what, exception);
	}

	private Ledger() {
	}
}
