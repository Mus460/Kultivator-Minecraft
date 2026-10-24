package dev.kultivator.desa;

import dev.kultivator.harta.HerbKind;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.sect.event.Storehouse;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import dev.kultivator.Kultivator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Upeti / tribute (DESIGN 25.6.4; PW ch. 3, the offerings Stone Village makes to its guardian): every 3 in-game days,
 * computed lazily with at most 3 cycles of catch-up, into the Lumbung ({@link Storehouse#insert}); whatever does not fit
 * waits in the village's {@code owed} buffer (at most 9 stacks) and is retried first on the next cycle. Halved after a
 * sack, skipped while the hall is dormant. The amounts are game design.
 * <p>
 * Hooks for other modules (DESIGN 38.C.2.3, owner 38DS): {@link #addBonusProvider} adds wares to a cycle (a Desa Klan pays
 * aged herbs and middle spirit stones besides the crops), {@link #addPledgeVeto} lets a module refuse a pledge (a clan
 * is pledged only after its patriarch was beaten or its regard reached 50; {@code Pledge} asks {@link #vetoIkrar}), and
 * {@link #ikrarDi} answers which master protects the village at a bell (the vassal records are package-private).
 */
public final class Tribute {

	public static final int CYCLE_DAYS = 3;
	public static final int MAX_CATCHUP = 3;
	public static final int OWED_MAX = 9;

	/** Extra wares of one tribute cycle (added after the crops, before the Lumbung; amounts already scaled by the provider). */
	@FunctionalInterface
	public interface BonusProvider {
		/**
		 * Adds stacks for {@code village} of {@code owner} to {@code out}. {@code factor} is the cycle's factor (0.5 after a
		 * sack, 1.5 for a Panen Gagal reward); {@code bell} is the village bell.
		 */
		void bonus(MinecraftServer server, UUID owner, BlockPos bell, int loyalty, double factor, RandomSource random, List<ItemStack> out);
	}

	/** May refuse a pledge at {@code bell}: the reason, or null to allow. */
	@FunctionalInterface
	public interface PledgeVeto {
		@Nullable Component refuse(ServerPlayer player, ServerLevel level, BlockPos bell);
	}

	/** A pledged village as other modules see it. */
	public record Ikrar(UUID owner, String name, BlockPos bell, BlockPos plaque, int loyalty, int guardRealm, boolean guardPresent) {
	}

	private static final List<BonusProvider> BONUS = new CopyOnWriteArrayList<>();
	private static final List<PledgeVeto> VETO = new CopyOnWriteArrayList<>();

	/** Adds a tribute bonus provider (once, from a module's init). */
	public static void addBonusProvider(BonusProvider provider) {
		if (provider != null) {
			BONUS.add(provider);
		}
	}

	/** Adds a pledge veto (once, from a module's init). */
	public static void addPledgeVeto(PledgeVeto veto) {
		if (veto != null) {
			VETO.add(veto);
		}
	}

	/** The first refusal of the registered vetoes for a pledge at {@code bell}, or null (called by {@code Pledge}). */
	public static @Nullable Component vetoIkrar(ServerPlayer player, ServerLevel level, BlockPos bell) {
		for (PledgeVeto veto : VETO) {
			try {
				Component why = veto.refuse(player, level, bell);
				if (why != null) {
					return why;
				}
			} catch (RuntimeException e) {
				Kultivator.LOGGER.warn("Pledge veto failed at {}", bell, e);
			}
		}
		return null;
	}

	/**
	 * The pledged, not lost village whose bell lies within {@code range} blocks of {@code pos} (overworld records only),
	 * or null.
	 */
	public static @Nullable Ikrar ikrarDi(BlockPos pos, double range) {
		double r2 = range * range;
		for (UUID owner : Vassals.owners()) {
			Vassals.Owner entry = Vassals.peek(owner);
			if (entry == null) {
				continue;
			}
			for (Village village : entry.villages) {
				if (village.pledged && !village.lost && village.bell.distSqr(pos) <= r2) {
					return new Ikrar(owner, village.name, village.bell, village.plaque, village.loyalty, village.guardRealm, village.guardPresent());
				}
			}
		}
		return null;
	}

	/** Pays every due cycle of {@code village} (at most {@link #MAX_CATCHUP}); true when something changed. */
	static boolean process(MinecraftServer server, UUID owner, Village village, long day) {
		if (!village.pledged || village.lost || day < village.nextTribute) {
			return false;
		}
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || !hall.active()) {
			// Skipped while the hall is dormant (DESIGN 25.6.4): the clock moves on, nothing piles up.
			village.nextTribute = day + CYCLE_DAYS;
			return true;
		}
		long due = (day - village.nextTribute) / CYCLE_DAYS + 1;
		int cycles = (int) Math.min(MAX_CATCHUP, Math.max(1L, due));
		long next = village.nextTribute + (long) cycles * CYCLE_DAYS;
		// A long absence (or a big /time add) pays at most three cycles and starts the clock again from today.
		village.nextTribute = next <= day ? day + CYCLE_DAYS : next;
		for (int i = 0; i < cycles; i++) {
			double factor = village.halfTribute ? 0.5D : 1.0D;
			village.halfTribute = false;
			pay(server, owner, village, factor);
		}
		return true;
	}

	/** One tribute cycle at {@code factor} (0.5 after a sack, 1.5 for a Panen Gagal reward). */
	static void pay(MinecraftServer server, UUID owner, Village village, double factor) {
		RandomSource random = server.overworld().getRandom();
		List<ItemStack> stacks = roll(village, factor, random);
		bonus(server, owner, village, factor, random, stacks);
		int stored = retryOwed(server, owner, village);
		int pending = 0;
		for (ItemStack stack : stacks) {
			int count = stack.getCount();
			ItemStack rest = Storehouse.insert(owner, stack);
			stored += count - rest.getCount();
			if (!rest.isEmpty()) {
				pending += rest.getCount();
				owe(server, village, rest);
			}
		}
		Vassals.changed();
		Desa.dirty(owner);
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			Msg.chat(player, Component.translatable(pending > 0 ? "kultivator.desa.tribute.owed" : "kultivator.desa.tribute.arrived", village.name, stored,
					pending).withStyle(ChatFormatting.GREEN));
		}
	}

	/** The stacks of one cycle: {@code V = villagers x (0.5 + loyalty / 100)}, scaled by {@code factor}. */
	static List<ItemStack> roll(Village village, double factor, RandomSource random) {
		double v = village.villagers * (0.5D + village.loyalty / 100.0D) * factor;
		List<ItemStack> list = new ArrayList<>();
		Item crop = switch (random.nextInt(8)) {
			case 0, 1, 2 -> Items.WHEAT;
			case 3, 4 -> Items.CARROT;
			case 5, 6 -> Items.POTATO;
			default -> Items.BREAD;
		};
		add(list, crop, (int) Math.round(v * 2.0D));
		add(list, random.nextBoolean() ? Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE) : Items.LEATHER, (int) Math.round(v / 2.0D));
		if (village.loyalty >= 50) {
			add(list, Items.EMERALD, (int) Math.floor(v / 4.0D));
		}
		add(list, Items.IRON_INGOT, (int) Math.floor(v / 6.0D));
		if (village.loyalty >= 70 && random.nextDouble() < 0.15D) {
			list.add(SpiritHerbs.create(random.nextBoolean() ? HerbKind.SPIRIT_GRASS : HerbKind.NINECURL_GINSENG, 50 + random.nextInt(151)));
		}
		if (village.loyalty >= 100) {
			list.add(SpiritStones.stack(SpiritStones.LOW, 2));
		}
		list.removeIf(ItemStack::isEmpty);
		return list;
	}

	/** Lets every {@link BonusProvider} add its wares to one cycle. */
	private static void bonus(MinecraftServer server, UUID owner, Village village, double factor, RandomSource random, List<ItemStack> stacks) {
		for (BonusProvider provider : BONUS) {
			try {
				List<ItemStack> extra = new ArrayList<>();
				provider.bonus(server, owner, village.bell, village.loyalty, factor, random, extra);
				for (ItemStack stack : extra) {
					if (stack != null && !stack.isEmpty()) {
						stacks.add(stack);
					}
				}
			} catch (RuntimeException e) {
				Kultivator.LOGGER.warn("Tribute bonus provider failed for village {}", village.name, e);
			}
		}
	}

	private static void add(List<ItemStack> list, Item item, int count) {
		int left = count;
		while (left > 0) {
			int part = Math.min(left, item.getDefaultMaxStackSize());
			list.add(new ItemStack(item, part));
			left -= part;
		}
	}

	/** Moves as much of the owed buffer as fits into the Lumbung; returns how many items moved. */
	static int retryOwed(MinecraftServer server, UUID owner, Village village) {
		if (village.owed.isEmpty()) {
			return 0;
		}
		RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		ListTag keep = new ListTag();
		int moved = 0;
		for (Tag tag : village.owed) {
			if (!(tag instanceof CompoundTag entry)) {
				continue;
			}
			ItemStack stack = entry.read("item", ItemStack.CODEC, ops).orElse(ItemStack.EMPTY);
			if (stack.isEmpty()) {
				continue;
			}
			int count = stack.getCount();
			ItemStack rest = Storehouse.insert(owner, stack);
			moved += count - rest.getCount();
			if (!rest.isEmpty()) {
				CompoundTag back = new CompoundTag();
				back.store("item", ItemStack.CODEC, ops, rest);
				keep.add(back);
			}
		}
		village.owed = keep;
		return moved;
	}

	/** Adds {@code stack} to the owed buffer, merging with equal stacks; beyond 9 stacks the rest is held back and told. */
	private static void owe(MinecraftServer server, Village village, ItemStack stack) {
		RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		ItemStack rest = stack.copy();
		ListTag merged = new ListTag();
		for (Tag tag : village.owed) {
			if (!(tag instanceof CompoundTag entry)) {
				continue;
			}
			ItemStack held = entry.read("item", ItemStack.CODEC, ops).orElse(ItemStack.EMPTY);
			if (held.isEmpty()) {
				continue;
			}
			if (!rest.isEmpty() && ItemStack.isSameItemSameComponents(held, rest) && held.getCount() < held.getMaxStackSize()) {
				int move = Math.min(rest.getCount(), held.getMaxStackSize() - held.getCount());
				held.grow(move);
				rest.shrink(move);
			}
			CompoundTag back = new CompoundTag();
			back.store("item", ItemStack.CODEC, ops, held);
			merged.add(back);
		}
		while (!rest.isEmpty() && merged.size() < OWED_MAX) {
			int part = Math.min(rest.getCount(), rest.getMaxStackSize());
			CompoundTag back = new CompoundTag();
			back.store("item", ItemStack.CODEC, ops, rest.copyWithCount(part));
			merged.add(back);
			rest.shrink(part);
		}
		village.owed = merged;
	}

	/** Items waiting in the owed buffer (for the cards). */
	static int owedCount(MinecraftServer server, Village village) {
		if (village.owed.isEmpty()) {
			return 0;
		}
		RegistryOps<Tag> ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		int total = 0;
		for (Tag tag : village.owed) {
			if (tag instanceof CompoundTag entry) {
				total += entry.read("item", ItemStack.CODEC, ops).map(ItemStack::getCount).orElse(0);
			}
		}
		return total;
	}

	private Tribute() {
	}
}
