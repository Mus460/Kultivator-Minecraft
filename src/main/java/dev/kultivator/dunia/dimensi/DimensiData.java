package dev.kultivator.dunia.dimensi;

import dev.kultivator.data.Cultivators;
import dev.kultivator.harta.HartaItems;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.util.NoGrief;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Module D's player data and money helpers. Player state lives in {@code CultivationData.extra()} under
 * {@code bawah} (the soul journey, courts, Meng Po, quests) and {@code laut} (the sea's return point, the Dragon King's
 * quests, Penglai), so it is saved, synced and copied on death like every module's extra (DESIGN 26.13 rule).
 * <p>
 * Prices are in Nilai Batu (NB, DESIGN 34.1: Low 1, Mid 9, High 80, Supreme 600). {@link #pay} takes real spirit stones
 * from the inventory, lowest grade first, and gives change in lower grades; {@link #giveNb} pays out real stones. No
 * shop of this module ever creates stones from nothing: rewards are fixed, once-per-player or capped per day.
 */
public final class DimensiData {

	public static final String BAWAH = "bawah";
	public static final String LAUT = "laut";
	/** Nilai Batu of each stone grade. */
	public static final int[] NB = {1, 9, 80, 600};

	/** {@code extra.<key>} of the player (created and attached when missing). */
	public static CompoundTag extra(ServerPlayer player, String key) {
		CompoundTag extra = Cultivators.get(player).extra();
		CompoundTag tag = extra.getCompound(key).orElse(null);
		if (tag == null) {
			tag = new CompoundTag();
			extra.put(key, tag);
		}
		return tag;
	}

	/** A sub-compound of {@code extra.<key>} (created and attached when missing). */
	public static CompoundTag section(ServerPlayer player, String key, String child) {
		CompoundTag parent = extra(player, key);
		CompoundTag tag = parent.getCompound(child).orElse(null);
		if (tag == null) {
			tag = new CompoundTag();
			parent.put(child, tag);
		}
		return tag;
	}

	/** Marks the player's data dirty (saved and synced on the next pass). */
	public static void dirty(ServerPlayer player) {
		Cultivators.dirty(player);
	}

	/** Overworld game time (the same clock for every dimension). */
	public static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	/** Overworld day index. */
	public static long day(MinecraftServer server) {
		return Math.max(0L, server.overworld().getOverworldClockTime() / 24000L);
	}

	/** The time of day 0-23999 of the overworld clock. */
	public static long timeOfDay(MinecraftServer server) {
		return Math.floorMod(server.overworld().getOverworldClockTime(), 24000L);
	}

	/** Night on the overworld clock (13 000-23 000). */
	public static boolean night(MinecraftServer server) {
		long t = timeOfDay(server);
		return t >= 13000L && t <= 23000L;
	}

	// ================================================================== spirit stones

	/** The Nilai Batu of every spirit stone in the player's inventory. */
	public static long nbIn(ServerPlayer player) {
		long total = 0L;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			int grade = SpiritStones.grade(stack);
			if (grade >= 0) {
				total += (long) NB[grade] * stack.getCount();
			}
		}
		return total;
	}

	/**
	 * Takes {@code price} NB in spirit stones (lowest grade first, change given back in the largest lower grades). False
	 * and nothing taken when the player has less. Creative players pay nothing.
	 */
	public static boolean pay(ServerPlayer player, int price) {
		if (price <= 0 || player.isCreative()) {
			return true;
		}
		if (nbIn(player) < price) {
			return false;
		}
		long paid = 0L;
		for (int grade = 0; grade < NB.length && paid < price; grade++) {
			for (int i = 0; i < player.getInventory().getContainerSize() && paid < price; i++) {
				ItemStack stack = player.getInventory().getItem(i);
				if (SpiritStones.grade(stack) != grade) {
					continue;
				}
				while (!stack.isEmpty() && paid < price) {
					stack.shrink(1);
					paid += NB[grade];
				}
			}
		}
		long change = paid - price;
		if (change > 0L) {
			giveNb(player, change);
		}
		return true;
	}

	/** Gives {@code amount} NB as real stones, largest grades first. */
	public static void giveNb(ServerPlayer player, long amount) {
		long left = Math.max(0L, amount);
		for (int grade = NB.length - 1; grade >= 0 && left > 0L; grade--) {
			long count = left / NB[grade];
			left -= count * NB[grade];
			while (count > 0L) {
				int take = (int) Math.min(64L, count);
				give(player, new ItemStack(HartaItems.stoneItem(grade), take));
				count -= take;
			}
		}
	}

	/** Gives a stack: the inventory, else dropped at the player's feet, locked to them. */
	public static void give(ServerPlayer player, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		ItemStack rest = stack.copy();
		player.getInventory().add(rest);
		if (rest.isEmpty()) {
			return;
		}
		ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), rest);
		item.setTarget(player.getUUID());
		item.setPickUpDelay(10);
		item.addTag(NoGrief.TAG);
		player.level().addFreshEntity(item);
	}

	/** Removes one item of {@code item} from the inventory; false when none (creative: always true, nothing taken). */
	public static boolean takeOne(ServerPlayer player, net.minecraft.world.item.Item item) {
		if (player.isCreative()) {
			return true;
		}
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	/** True when {@code item} is in either hand. */
	public static boolean holds(ServerPlayer player, net.minecraft.world.item.Item item) {
		return player.getMainHandItem().is(item) || player.getOffhandItem().is(item);
	}

	/** True when {@code item} is anywhere in the inventory. */
	public static boolean carries(ServerPlayer player, net.minecraft.world.item.Item item) {
		return player.getInventory().countItem(item) > 0;
	}

	private DimensiData() {
	}
}
