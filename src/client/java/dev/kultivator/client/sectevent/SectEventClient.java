package dev.kultivator.client.sectevent;

import dev.kultivator.client.hall.HallClient;
import dev.kultivator.sect.event.SectEventPayload;
import dev.kultivator.sect.event.Storehouse;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Client side of module B (DESIGN 20.4.2): keeps the newest {@code "sectevent"} part of the hall details (from a full
 * {@code HallInfo} or from the module's own {@link SectEventPayload} after an action; the higher {@code rev} wins), the
 * decoded storehouse, and drives the continuous event effects ({@link SectEventFx#tick}). Called once from
 * {@code KultivatorClient}.
 */
public final class SectEventClient {

	private static CompoundTag data = new CompoundTag();
	private static long version;
	private static long seenHallVersion = -1L;
	private static ItemStack[] store = emptyStore();
	private static long storeVersion = -1L;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(SectEventPayload.TYPE, (payload, context) -> accept(payload.data()));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level != null && minecraft.player != null && !minecraft.isPaused()) {
				SectEventFx.tick(minecraft);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			data = new CompoundTag();
			version++;
			seenHallVersion = -1L;
			store = emptyStore();
			storeVersion = -1L;
			SectEventFx.clear();
		});
	}

	/** The newest sect-activity details (never null; empty before the hall was opened). */
	public static CompoundTag data() {
		long hallVersion = HallClient.version();
		if (hallVersion != seenHallVersion) {
			seenHallVersion = hallVersion;
			CompoundTag fromHall = HallClient.info().getCompoundOrEmpty("sectevent");
			if (!fromHall.isEmpty()) {
				accept(fromHall);
			}
		}
		return data;
	}

	/** Grows whenever the details change. */
	public static long version() {
		data();
		return version;
	}

	private static void accept(CompoundTag tag) {
		if (tag == null || tag.isEmpty()) {
			return;
		}
		if (tag.getIntOr("rev", 0) < data.getIntOr("rev", 0) && data.getBooleanOr("hall", false)) {
			return;
		}
		data = tag;
		version++;
	}

	/** The storehouse slots (27, empty stacks for free slots), decoded once per version. */
	public static ItemStack[] store() {
		long now = version();
		if (now == storeVersion) {
			return store;
		}
		storeVersion = now;
		store = emptyStore();
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return store;
		}
		RegistryOps<Tag> ops = minecraft.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		for (Tag tag : data.getListOrEmpty("store")) {
			if (tag instanceof CompoundTag entry) {
				int slot = entry.getIntOr("slot", -1);
				if (slot >= 0 && slot < Storehouse.SLOTS) {
					store[slot] = entry.read("item", ItemStack.CODEC, ops).orElse(ItemStack.EMPTY);
				}
			}
		}
		return store;
	}

	private static ItemStack[] emptyStore() {
		ItemStack[] slots = new ItemStack[Storehouse.SLOTS];
		Arrays.fill(slots, ItemStack.EMPTY);
		return slots;
	}

	/** The roster entries (the master's loaded disciples). */
	public static List<CompoundTag> roster() {
		List<CompoundTag> list = new ArrayList<>();
		for (Tag tag : data().getListOrEmpty("roster")) {
			if (tag instanceof CompoundTag entry) {
				list.add(entry);
			}
		}
		return list;
	}

	/** A stored name component (or a dash). */
	public static Component name(CompoundTag tag, String key) {
		return tag.read(key, ComponentSerialization.CODEC).orElse(Component.literal("-"));
	}

	/** Game time the server had when the details were made. */
	public static long serverTime() {
		return data().getLongOr("time", 0L);
	}

	private SectEventClient() {
	}
}
