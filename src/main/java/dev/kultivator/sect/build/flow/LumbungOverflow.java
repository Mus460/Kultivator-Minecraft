package dev.kultivator.sect.build.flow;

import dev.kultivator.sect.event.Storehouse;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Where the goods of duties and expeditions go when the 27-slot Lumbung Sekte is full (DESIGN 20.3.9, 21.13): the rest
 * goes to the Peti Galian (no limit); a stack with its own data (an aged herb, a named item) that the Peti Galian could
 * not keep whole (its rich list is full) waits in a pending list, retried every ten seconds while the owner is online,
 * until the Lumbung or the Peti Galian has room again. Nothing is deleted. The pending list lives in the owner's
 * {@code build_flow} section ({@code lumbung_pending}); past {@link #MAX_PENDING} stacks the Peti Galian takes the item
 * by its kind (the item is kept, its own data is not).
 */
public final class LumbungOverflow {

	/** Everything fit the Lumbung. */
	public static final int LUMBUNG = 0;
	/** Some of it went to the Peti Galian. */
	public static final int PETI = 1;
	/** Some of it is waiting for room. */
	public static final int PENDING = 2;
	static final int MAX_PENDING = 256;
	private static final String KEY = "lumbung_pending";
	/** Ticks between two "went to the Peti Galian" notes, and between two "still waiting" reminders. */
	private static final long PETI_NOTICE = 12000L;
	private static final long PENDING_NOTICE = 6000L;
	private static final Map<UUID, Long> LAST_PETI = new HashMap<>();
	private static final Map<UUID, Long> LAST_PENDING = new HashMap<>();

	/** Puts {@code stack} into the owner's Lumbung, the rest into the Peti Galian or the pending list; returns where. */
	public static int store(UUID owner, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return LUMBUNG;
		}
		ItemStack rest = Storehouse.insert(owner, stack);
		if (rest.isEmpty()) {
			return LUMBUNG;
		}
		if (Ledger.INSTANCE.salvageWhole(owner, rest)) {
			return PETI;
		}
		return park(owner, rest) ? PENDING : PETI;
	}

	/**
	 * Tells the online owner where the overflow went ({@code worst} from {@link #store}; {@code example} names one item):
	 * the Peti Galian note at most every ten minutes, the pending note at most every five.
	 */
	public static void tell(@Nullable ServerPlayer player, UUID owner, int worst, @Nullable Component example) {
		if (player == null || worst == LUMBUNG) {
			return;
		}
		Component name = example == null ? Component.literal("?") : example;
		long now = FlowData.gameTime();
		if (worst == PENDING) {
			if (due(LAST_PENDING, owner, now, PENDING_NOTICE)) {
				Msg.chat(player, Component.translatable("kultivator.sectbuild.lumbung.pending", name, pending(owner)).withStyle(ChatFormatting.YELLOW));
			}
			return;
		}
		if (due(LAST_PETI, owner, now, PETI_NOTICE)) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.lumbung.to_peti", name).withStyle(ChatFormatting.GRAY));
		}
	}

	/** Stacks waiting for room. */
	public static int pending(UUID owner) {
		return FlowData.of(owner).getListOrEmpty(KEY).size();
	}

	/** Retries the pending list (every ten seconds per online owner, from the flow's heartbeat). */
	static void retry(ServerPlayer player, UUID owner) {
		CompoundTag section = FlowData.of(owner);
		ListTag list = section.getListOrEmpty(KEY);
		if (list.isEmpty()) {
			return;
		}
		RegistryOps<Tag> ops = Ledger.registryOps();
		if (ops == null) {
			return;
		}
		ListTag left = new ListTag();
		int delivered = 0;
		for (Tag tag : list) {
			if (!(tag instanceof CompoundTag entry)) {
				continue;
			}
			ItemStack stack = entry.read("item", ItemStack.CODEC, ops).orElse(ItemStack.EMPTY);
			if (stack.isEmpty()) {
				continue;
			}
			ItemStack rest = Storehouse.insert(owner, stack);
			if (rest.isEmpty() || Ledger.INSTANCE.salvageWhole(owner, rest)) {
				delivered++;
				continue;
			}
			CompoundTag keep = new CompoundTag();
			keep.store("item", ItemStack.CODEC, ops, rest);
			left.add(keep);
		}
		if (left.isEmpty()) {
			section.remove(KEY);
		} else {
			section.put(KEY, left);
		}
		FlowData.dirty();
		if (delivered > 0) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.lumbung.delivered", delivered, left.size()).withStyle(ChatFormatting.GREEN));
		} else if (!left.isEmpty() && due(LAST_PENDING, owner, FlowData.gameTime(), PENDING_NOTICE)) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.lumbung.still_pending", left.size()).withStyle(ChatFormatting.YELLOW));
		}
	}

	/** Keeps {@code stack} in the pending list (merged with an equal stack); false when it went to the Peti Galian by kind. */
	private static boolean park(UUID owner, ItemStack stack) {
		RegistryOps<Tag> ops = Ledger.registryOps();
		ListTag list = FlowData.list(FlowData.of(owner), KEY);
		if (ops == null) {
			Ledger.INSTANCE.salvage(owner, stack);
			return false;
		}
		ItemStack rest = stack.copy();
		for (int i = 0; i < list.size() && !rest.isEmpty(); i++) {
			if (!(list.get(i) instanceof CompoundTag entry)) {
				continue;
			}
			ItemStack have = entry.read("item", ItemStack.CODEC, ops).orElse(ItemStack.EMPTY);
			if (have.isEmpty() || !ItemStack.isSameItemSameComponents(have, rest) || have.getCount() >= have.getMaxStackSize()) {
				continue;
			}
			int move = Math.min(rest.getCount(), have.getMaxStackSize() - have.getCount());
			have.grow(move);
			rest.shrink(move);
			entry.store("item", ItemStack.CODEC, ops, have);
		}
		while (!rest.isEmpty() && list.size() < MAX_PENDING) {
			int move = Math.min(rest.getCount(), rest.getMaxStackSize());
			CompoundTag entry = new CompoundTag();
			entry.store("item", ItemStack.CODEC, ops, rest.copyWithCount(move));
			list.add(entry);
			rest.shrink(move);
		}
		FlowData.dirty();
		if (!rest.isEmpty()) {
			// The pending list is full too: the Peti Galian keeps the item by its kind rather than losing it.
			Ledger.INSTANCE.salvage(owner, rest);
		}
		return true;
	}

	private static boolean due(Map<UUID, Long> last, UUID owner, long now, long gap) {
		Long before = last.get(owner);
		if (before != null && now - before < gap && now >= before) {
			return false;
		}
		last.put(owner, now);
		return true;
	}

	static void clearSession() {
		LAST_PETI.clear();
		LAST_PENDING.clear();
	}

	private LumbungOverflow() {
	}
}
