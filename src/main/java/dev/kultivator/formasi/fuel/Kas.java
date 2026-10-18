package dev.kultivator.formasi.fuel;

import dev.kultivator.formasi.core.CoreTicker;
import dev.kultivator.formasi.core.OwnerStore;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Kas Daya Formasi, the formation treasury of one owner (DESIGN 24.2.7): an amethyst pool (cap 2,000) and a stone pool
 * (cap 50,000), kept in centi-Daya in {@code OwnerStore}. Daya is spent qi: it is never withdrawable as items.
 */
public final class Kas {

	/** Pool caps in whole Daya (the pools themselves are kept in centi-Daya). */
	public static final long AMETHYST_CAP = 2_000L;
	public static final long STONE_CAP = 50_000L;
	/** Inventory slots searched by a deposit (the main inventory and hotbar). */
	private static final int MAIN_SLOTS = 36;

	/** The amethyst pool in centi-Daya. */
	public static long amethyst(UUID owner) {
		OwnerStore store = store();
		return store == null || !store.has(owner) ? 0L : store.entry(owner).kasA;
	}

	/** The stone pool in centi-Daya. */
	public static long stone(UUID owner) {
		OwnerStore store = store();
		return store == null || !store.has(owner) ? 0L : store.entry(owner).kasS;
	}

	/** The best stone grade in the stone pool since it was last empty (-1 none). */
	public static int bestGrade(UUID owner) {
		OwnerStore store = store();
		return store == null || !store.has(owner) ? -1 : store.entry(owner).kasBest;
	}

	/** Adds centi-Daya to a pool (grade -1 amethyst, 0-3 stone); returns what fitted. */
	public static long add(UUID owner, long centi, int grade) {
		OwnerStore store = store();
		if (store == null || centi <= 0L) {
			return 0L;
		}
		OwnerStore.Entry entry = store.entry(owner);
		long accepted;
		if (grade < 0) {
			accepted = Math.max(0L, Math.min(centi, AMETHYST_CAP * 100L - entry.kasA));
			entry.kasA += accepted;
		} else {
			accepted = Math.max(0L, Math.min(centi, STONE_CAP * 100L - entry.kasS));
			entry.kasS += accepted;
			if (accepted > 0L) {
				entry.kasBest = Math.max(entry.kasBest, grade);
			}
		}
		store.setDirty();
		return accepted;
	}

	/** Takes up to {@code centi} from a pool; returns what was taken. An emptied stone pool forgets its best grade. */
	public static long take(UUID owner, long centi, boolean stonePool) {
		OwnerStore store = store();
		if (store == null || centi <= 0L || !store.has(owner)) {
			return 0L;
		}
		OwnerStore.Entry entry = store.entry(owner);
		long taken;
		if (stonePool) {
			taken = Math.min(centi, entry.kasS);
			entry.kasS -= taken;
			if (entry.kasS <= 0L) {
				entry.kasBest = -1;
			}
		} else {
			taken = Math.min(centi, entry.kasA);
			entry.kasA -= taken;
		}
		if (taken > 0L) {
			store.setDirty();
		}
		return taken;
	}

	/** Sets both pools (test command); values in whole Daya. */
	public static void set(UUID owner, long amethyst, long stone, int best) {
		OwnerStore store = store();
		if (store == null) {
			return;
		}
		OwnerStore.Entry entry = store.entry(owner);
		entry.kasA = Math.max(0L, Math.min(AMETHYST_CAP, amethyst)) * 100L;
		entry.kasS = Math.max(0L, Math.min(STONE_CAP, stone)) * 100L;
		entry.kasBest = entry.kasS > 0L ? best : -1;
		store.setDirty();
	}

	/**
	 * Moves the held stack ({@code all = false}) or every accepted item of {@code player}'s inventory (stones first, up to
	 * the caps) into {@code owner}'s Kas; returns the Daya deposited. A sect mate's deposit is logged as a donation.
	 */
	public static int depositInventory(ServerPlayer player, UUID owner, boolean all) {
		int total = 0;
		if (!all) {
			total += depositStack(player, owner, player.getMainHandItem());
		} else {
			for (int pass = 0; pass < 2; pass++) {
				for (int slot = 0; slot < MAIN_SLOTS; slot++) {
					ItemStack stack = player.getInventory().getItem(slot);
					int grade = FuelTable.grade(stack);
					if (grade == FuelTable.NONE || (pass == 0) != (grade >= 0)) {
						continue;
					}
					total += depositStack(player, owner, stack);
				}
			}
		}
		if (total > 0 && !owner.equals(player.getUUID())) {
			OwnerStore store = store();
			if (store != null) {
				store.log(owner, day(), "kultivator.formasi.log.donation", player.getPlainTextName(), Integer.toString(total));
			}
			Halls.log(owner, Component.translatable("kultivator.formasi.log.donation", player.getPlainTextName(), total));
		}
		return total;
	}

	/** Deposits whole items of {@code stack} while each fits; returns the Daya added. */
	private static int depositStack(ServerPlayer player, UUID owner, ItemStack stack) {
		int grade = FuelTable.grade(stack);
		int each = FuelTable.daya(stack);
		if (grade == FuelTable.NONE || each <= 0) {
			return 0;
		}
		long room = grade < 0 ? AMETHYST_CAP * 100L - amethyst(owner) : STONE_CAP * 100L - stone(owner);
		int count = (int) Math.min(stack.getCount(), Math.max(0L, room / (each * 100L)));
		if (count <= 0) {
			return 0;
		}
		add(owner, (long) count * each * 100L, grade);
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(count);
		}
		return count * each;
	}

	private static long day() {
		MinecraftServer server = CoreTicker.server();
		return server == null ? 0L : server.overworld().getGameTime() / 24000L;
	}

	private static OwnerStore store() {
		MinecraftServer server = CoreTicker.server();
		return server == null ? null : OwnerStore.get(server);
	}

	private Kas() {
	}
}
