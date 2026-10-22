package dev.kultivator.duel.session;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.util.Msg;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Penitipan, the stake escrow of formal duels (DESIGN 35.2.4), global saved data {@code kultivator:duel_escrow}. Stakes
 * are taken out of the inventories when INTRO begins (after both proved they have them) and kept here as codec'd stacks
 * under the session's key; a cancelled duel gives each back to its owner, a decided one gives all to the winner. A
 * receiver who is offline or has a full inventory gets a pending entry, delivered on join and every 100 ticks when there
 * is room; after 7 game days without room the stack is dropped at the owner's feet. A server stopped in the middle of a
 * duel returns every active escrow to its owners on the next start: nothing is lost, nothing is doubled.
 */
public final class DuelEscrow extends SavedData {

	/** Pending stacks without room are dropped at the owner's feet after this long (7 game days). */
	public static final long DROP_AFTER = 7L * 24000L;

	/** One staked stack and who put it up. */
	public record Deposit(UUID owner, ItemStack stack) {
		static final Codec<Deposit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Deposit::owner),
				ItemStack.CODEC.fieldOf("stack").forGetter(Deposit::stack)
		).apply(instance, Deposit::new));
	}

	/** One stack waiting for its receiver. */
	public record Pending(ItemStack stack, long since) {
		static final Codec<Pending> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ItemStack.CODEC.fieldOf("stack").forGetter(Pending::stack),
				Codec.LONG.optionalFieldOf("since", 0L).forGetter(Pending::since)
		).apply(instance, Pending::new));
	}

	public static final Codec<DuelEscrow> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(Codec.STRING, Deposit.CODEC.listOf()).optionalFieldOf("active", Map.of()).forGetter(e -> e.active),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Pending.CODEC.listOf()).optionalFieldOf("pending", Map.of()).forGetter(e -> e.pending)
	).apply(instance, DuelEscrow::new));

	public static final SavedDataType<DuelEscrow> TYPE = new SavedDataType<>(Kultivator.id("duel_escrow"), DuelEscrow::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<String, List<Deposit>> active = new HashMap<>();
	private final Map<UUID, List<Pending>> pending = new HashMap<>();

	public DuelEscrow() {
	}

	private DuelEscrow(Map<String, List<Deposit>> active, Map<UUID, List<Pending>> pending) {
		active.forEach((key, list) -> this.active.put(key, new ArrayList<>(list)));
		pending.forEach((key, list) -> this.pending.put(key, new ArrayList<>(list)));
	}

	public static DuelEscrow get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Puts {@code stack} (taken from {@code owner}) into the escrow {@code key}. */
	public void deposit(String key, UUID owner, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		active.computeIfAbsent(key, k -> new ArrayList<>()).add(new Deposit(owner, stack.copy()));
		setDirty();
	}

	/** True when {@code key} holds anything. */
	public boolean holds(String key) {
		List<Deposit> list = active.get(key);
		return list != null && !list.isEmpty();
	}

	/** Takes everything out of {@code key} (the caller hands it on). */
	public List<Deposit> take(String key) {
		List<Deposit> list = active.remove(key);
		if (list == null) {
			return List.of();
		}
		setDirty();
		return list;
	}

	/** Gives every deposit of {@code key} back to its owner. */
	public void returnAll(MinecraftServer server, String key) {
		for (Deposit deposit : take(key)) {
			give(server, deposit.owner(), deposit.stack());
		}
	}

	/** On server start: every escrow still active belongs to a duel that died with the server; all go home. */
	public void returnAllActive(MinecraftServer server) {
		if (active.isEmpty()) {
			return;
		}
		for (String key : new ArrayList<>(active.keySet())) {
			returnAll(server, key);
		}
		Kultivator.LOGGER.info("Duel: returned the stakes of duels interrupted by the last shutdown");
	}

	/** Gives {@code stack} to the player {@code owner}: the inventory when online and there is room, pending otherwise. */
	public void give(MinecraftServer server, UUID owner, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		ItemStack rest = stack.copy();
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null && player.isAlive()) {
			player.getInventory().add(rest);
		}
		if (!rest.isEmpty()) {
			pending.computeIfAbsent(owner, k -> new ArrayList<>()).add(new Pending(rest, server.overworld().getGameTime()));
			setDirty();
			if (player != null) {
				Msg.chat(player, Component.translatable("kultivator.duel.msg.stake_pending").withColor(0xE0C080));
			}
		}
	}

	/** Delivers what waits for {@code player} (join, and every 100 ticks); drops what waited 7 days at their feet. */
	public void deliver(ServerPlayer player) {
		List<Pending> list = pending.get(player.getUUID());
		if (list == null || list.isEmpty() || !player.isAlive()) {
			return;
		}
		long now = player.level().getGameTime();
		boolean changed = false;
		boolean delivered = false;
		for (Iterator<Pending> it = list.iterator(); it.hasNext(); ) {
			Pending entry = it.next();
			ItemStack rest = entry.stack().copy();
			player.getInventory().add(rest);
			if (rest.isEmpty()) {
				it.remove();
				changed = true;
				delivered = true;
				continue;
			}
			if (rest.getCount() != entry.stack().getCount()) {
				changed = true;
				delivered = true;
			}
			if (now - entry.since() > DROP_AFTER) {
				ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.3D, player.getZ(), rest);
				item.setNoPickUpDelay();
				player.level().addFreshEntity(item);
				it.remove();
				changed = true;
			} else if (rest.getCount() != entry.stack().getCount()) {
				int index = list.indexOf(entry);
				if (index >= 0) {
					list.set(index, new Pending(rest, entry.since()));
				}
			}
		}
		if (list.isEmpty()) {
			pending.remove(player.getUUID());
		}
		if (changed) {
			setDirty();
		}
		if (delivered) {
			Msg.chat(player, Component.translatable("kultivator.duel.msg.stake_delivered").withColor(0x9FE0A0));
		}
	}

	/** True when something waits for {@code owner}. */
	public boolean hasPending(UUID owner) {
		List<Pending> list = pending.get(owner);
		return list != null && !list.isEmpty();
	}
}
