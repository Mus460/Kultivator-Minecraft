package dev.kultivator.alchemy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.Kultivator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The alchemy pots of one dimension, keyed by their cauldron position: the ingredients put in (item ids + counts), when
 * the refining ends and who refines. Only data: the cauldron and the campfire under it are never changed.
 */
public final class AlchemyPots extends SavedData {

	/** A pot holds at most this many items. */
	public static final int CAPACITY = 8;

	public static final Codec<AlchemyPots> CODEC = Pot.CODEC.listOf().optionalFieldOf("pots", List.of()).codec()
			.xmap(AlchemyPots::fromList, AlchemyPots::toList);

	public static final SavedDataType<AlchemyPots> TYPE = new SavedDataType<>(Kultivator.id("alchemy_pots"), AlchemyPots::new,
			CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<BlockPos, Pot> pots = new LinkedHashMap<>();

	public AlchemyPots() {
	}

	public static AlchemyPots get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	private static AlchemyPots fromList(List<Pot> list) {
		AlchemyPots data = new AlchemyPots();
		for (Pot pot : list) {
			if (!pot.isEmpty()) {
				data.pots.put(pot.pos(), pot);
			}
		}
		return data;
	}

	private List<Pot> toList() {
		return new ArrayList<>(pots.values());
	}

	public @Nullable Pot get(BlockPos pos) {
		return pots.get(pos);
	}

	/** The pot at {@code pos}, created empty when there is none. */
	public Pot getOrCreate(BlockPos pos) {
		Pot pot = pots.get(pos);
		if (pot == null) {
			pot = new Pot(pos, new LinkedHashMap<>(), 0L, null, 0, false);
			pots.put(pot.pos(), pot);
			setDirty();
		}
		return pot;
	}

	public @Nullable Pot remove(BlockPos pos) {
		Pot removed = pots.remove(pos);
		if (removed != null) {
			setDirty();
		}
		return removed;
	}

	/** Read-only view; copy it before removing entries while iterating. */
	public Collection<Pot> all() {
		return Collections.unmodifiableCollection(pots.values());
	}

	public boolean isEmpty() {
		return pots.isEmpty();
	}

	/** One saved ingredient line. */
	private record Entry(Identifier id, int count) {
		private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Identifier.CODEC.fieldOf("id").forGetter(Entry::id),
				Codec.INT.optionalFieldOf("count", 1).forGetter(Entry::count)
		).apply(instance, Entry::new));
	}

	/** The pot of one furnace. Call {@link AlchemyPots#setDirty()} after changing it. */
	public static final class Pot {

		private static final Codec<Pot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(Pot::pos),
				Entry.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Pot::entries),
				Codec.LONG.optionalFieldOf("refine_end", 0L).forGetter(Pot::refineEnd),
				UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Pot::ownerOptional),
				Codec.INT.optionalFieldOf("owner_level", 0).forGetter(Pot::ownerLevel),
				Codec.BOOL.optionalFieldOf("owner_fire", false).forGetter(Pot::ownerFire)
		).apply(instance, Pot::decode));

		private final BlockPos pos;
		private final LinkedHashMap<Item, Integer> items;
		private long refineEnd;
		private @Nullable UUID owner;
		private int ownerLevel;
		private boolean ownerFire;

		private Pot(BlockPos pos, LinkedHashMap<Item, Integer> items, long refineEnd, @Nullable UUID owner, int ownerLevel, boolean ownerFire) {
			this.pos = pos.immutable();
			this.items = items;
			this.refineEnd = Math.max(0L, refineEnd);
			this.owner = owner;
			this.ownerLevel = ownerLevel;
			this.ownerFire = ownerFire;
		}

		private static Pot decode(BlockPos pos, List<Entry> entries, long refineEnd, Optional<UUID> owner, int ownerLevel, boolean ownerFire) {
			LinkedHashMap<Item, Integer> items = new LinkedHashMap<>();
			int total = 0;
			for (Entry entry : entries) {
				if (entry.count() <= 0 || !BuiltInRegistries.ITEM.containsKey(entry.id())) {
					continue;
				}
				Item item = BuiltInRegistries.ITEM.getValue(entry.id());
				if (item == null || item == Items.AIR) {
					continue;
				}
				int count = Math.min(entry.count(), CAPACITY - total);
				if (count <= 0) {
					break;
				}
				items.merge(item, count, Integer::sum);
				total += count;
			}
			return new Pot(pos, items, refineEnd, owner.orElse(null), ownerLevel, ownerFire);
		}

		private Optional<UUID> ownerOptional() {
			return Optional.ofNullable(owner);
		}

		private List<Entry> entries() {
			List<Entry> list = new ArrayList<>();
			for (Map.Entry<Item, Integer> entry : items.entrySet()) {
				Identifier id = BuiltInRegistries.ITEM.getKey(entry.getKey());
				if (id != null) {
					list.add(new Entry(id, entry.getValue()));
				}
			}
			return list;
		}

		public BlockPos pos() {
			return pos;
		}

		/** Item -> count, in the order they were put in (read-only). */
		public Map<Item, Integer> contents() {
			return Collections.unmodifiableMap(items);
		}

		/** Number of items in the pot. */
		public int size() {
			int total = 0;
			for (int count : items.values()) {
				total += count;
			}
			return total;
		}

		public boolean isEmpty() {
			return items.isEmpty();
		}

		public boolean isFull() {
			return size() >= CAPACITY;
		}

		/** Adds one item; false when the pot is full. */
		public boolean add(Item item) {
			if (isFull()) {
				return false;
			}
			items.merge(item, 1, Integer::sum);
			return true;
		}

		/** One stack per ingredient (for spilling the pot). */
		public List<ItemStack> stacks() {
			List<ItemStack> list = new ArrayList<>();
			for (Map.Entry<Item, Integer> entry : items.entrySet()) {
				list.add(new ItemStack(entry.getKey(), entry.getValue()));
			}
			return list;
		}

		public boolean refining() {
			return refineEnd > 0L;
		}

		/** Game time when the refining ends (0 when not refining). */
		public long refineEnd() {
			return refineEnd;
		}

		/** The player who started the refining (gets the alchemy XP), or null. */
		public @Nullable UUID owner() {
			return owner;
		}

		/** The refiner's alchemy level when the refining started (used if they are offline at the end). */
		public int ownerLevel() {
			return ownerLevel;
		}

		/** The refiner walked the Fire Dao when the refining started. */
		public boolean ownerFire() {
			return ownerFire;
		}

		public void startRefining(long endTime, UUID refiner, int alchemyLevel, boolean fireDao) {
			this.refineEnd = Math.max(1L, endTime);
			this.owner = refiner;
			this.ownerLevel = alchemyLevel;
			this.ownerFire = fireDao;
		}
	}
}
