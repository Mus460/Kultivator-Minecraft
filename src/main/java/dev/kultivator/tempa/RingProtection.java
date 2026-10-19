package dev.kultivator.tempa;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Protection of storage rings (DESIGN 25.7.3). A dropped ring with an imprint gets {@code setTarget(owner)} (only the owner
 * can pick it up, permanently) and an unlimited lifetime in {@code ENTITY_LOAD}; fire, lava, cactus and explosions never
 * destroy it ({@code #kultivator:ring_immune} on the item); one that falls out of the world goes back to its owner, or,
 * while they are offline, into the rescue store and back to them when they join. The ring menu closes on death before the
 * inventory drops. Known gaps: hoppers, foxes and allays ignore the target (a bound ring is useless to anyone else).
 */
public final class RingProtection {

	/** Rings that fell into the void while their owner was offline, per owner (Overworld saved data). */
	public static final class RescueStore extends SavedData {

		private static final Codec<Map<UUID, List<ItemStack>>> MAP_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, ItemStack.CODEC.listOf());

		public static final Codec<RescueStore> CODEC = MAP_CODEC.optionalFieldOf("rings", Map.of()).codec()
				.xmap(RescueStore::fromMap, store -> store.rings);

		public static final SavedDataType<RescueStore> TYPE = new SavedDataType<>(Kultivator.id("ring_rescue"), RescueStore::new,
				CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

		private final Map<UUID, List<ItemStack>> rings = new HashMap<>();

		public RescueStore() {
		}

		private static RescueStore fromMap(Map<UUID, List<ItemStack>> map) {
			RescueStore store = new RescueStore();
			map.forEach((owner, list) -> store.rings.put(owner, new ArrayList<>(list)));
			return store;
		}

		static RescueStore get(MinecraftServer server) {
			return server.overworld().getDataStorage().computeIfAbsent(TYPE);
		}
	}

	/** Rings kept through death when {@code storageRingKeepOnDeath} is on, until the respawn. */
	private static final Map<UUID, List<ItemStack>> KEPT = new HashMap<>();

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item) {
				protect(item);
			}
		});
		// After DeathGuard (default phase): this listener never refuses a death, it only acts on one that goes ahead.
		Identifier late = Kultivator.id("tempa_after_guards");
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(Event.DEFAULT_PHASE, late);
		ServerLivingEntityEvents.ALLOW_DEATH.register(late, (entity, source, amount) -> {
			if (entity instanceof ServerPlayer player) {
				if (player.containerMenu instanceof RingMenu) {
					player.closeContainer();
				}
				if (!totemWillSave(player, source)) {
					keepRings(player);
				}
			}
			return true;
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			List<ItemStack> kept = KEPT.remove(newPlayer.getUUID());
			if (kept != null) {
				kept.forEach(stack -> Inv.give(newPlayer, stack));
			}
		});
		ServerPlayerEvents.JOIN.register(RingProtection::returnRescued);
	}

	/** Owner-only pickup and no despawn for a dropped ring with an imprint. */
	static void protect(ItemEntity item) {
		UUID owner = StorageRingItem.bondOwner(item.getItem());
		if (owner != null) {
			item.setTarget(owner);
			item.setUnlimitedLifetime();
		}
	}

	/** Vanilla's totem check runs after ALLOW_DEATH: a death-protection item in a hand will still save the player. */
	private static boolean totemWillSave(ServerPlayer player, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return player.getMainHandItem().has(DataComponents.DEATH_PROTECTION) || player.getOffhandItem().has(DataComponents.DEATH_PROTECTION);
	}

	/** {@code storageRingKeepOnDeath}: takes the rings out before the inventory drops (unless keepInventory already keeps them). */
	private static void keepRings(ServerPlayer player) {
		if (!KultivatorConfig.get().storageRingKeepOnDeath || player.level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
			return;
		}
		Inventory inventory = player.getInventory();
		List<ItemStack> kept = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (StorageRingItem.is(stack)) {
				kept.add(stack.copy());
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
		if (!kept.isEmpty()) {
			KEPT.computeIfAbsent(player.getUUID(), key -> new ArrayList<>()).addAll(kept);
		}
	}

	/**
	 * Called by {@code ItemEntityBelowWorldMixin} when an item entity falls out of the world: a ring with an imprint goes
	 * back to its owner (or the rescue store). True when it was handled (the entity is then discarded by the caller).
	 */
	public static boolean rescue(ItemEntity item) {
		if (!(item.level() instanceof ServerLevel level)) {
			return false;
		}
		ItemStack stack = item.getItem();
		UUID owner = StorageRingItem.bondOwner(stack);
		if (owner == null) {
			return false;
		}
		MinecraftServer server = level.getServer();
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			Inv.give(player, stack.copy());
			Msg.chat(player, Component.translatable("kultivator.tempa.ring.rescued", stack.getHoverName()).withStyle(ChatFormatting.GOLD));
		} else {
			RescueStore store = RescueStore.get(server);
			store.rings.computeIfAbsent(owner, key -> new ArrayList<>()).add(stack.copy());
			store.setDirty();
		}
		item.setItem(ItemStack.EMPTY);
		return true;
	}

	private static void returnRescued(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		RescueStore store = RescueStore.get(server);
		List<ItemStack> list = store.rings.remove(player.getUUID());
		if (list == null || list.isEmpty()) {
			return;
		}
		store.setDirty();
		for (ItemStack stack : list) {
			Inv.give(player, stack);
			Msg.chat(player, Component.translatable("kultivator.tempa.ring.rescued", stack.getHoverName()).withStyle(ChatFormatting.GOLD));
		}
	}

	private RingProtection() {
	}
}
