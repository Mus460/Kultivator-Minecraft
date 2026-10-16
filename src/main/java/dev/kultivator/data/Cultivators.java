package dev.kultivator.data;

import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Access to a player's cultivation data, plus syncing it to their client and their look to everyone. */
public final class Cultivators {

	/** Swords from best to worst; any other item in the swords tag comes after them. */
	private static final List<Item> SWORD_ORDER = List.of(Items.NETHERITE_SWORD, Items.DIAMOND_SWORD, Items.IRON_SWORD,
			Items.GOLDEN_SWORD, Items.COPPER_SWORD, Items.STONE_SWORD, Items.WOODEN_SWORD);

	private static final Set<UUID> DIRTY = new HashSet<>();

	public static CultivationData get(Player player) {
		return player.getAttachedOrCreate(ModAttachments.CULTIVATION);
	}

	/** Marks the player's data as changed: their client gets a new snapshot within 5 ticks and their look is refreshed. */
	public static void dirty(ServerPlayer player) {
		DIRTY.add(player.getUUID());
	}

	/** Sends the snapshot right now (use after big changes the player should see at once). */
	public static void syncNow(ServerPlayer player) {
		DIRTY.remove(player.getUUID());
		Net.send(player, new Payloads.SyncSelf(get(player).saveForClient(), player.level().getGameTime()));
		refreshVisual(player);
	}

	/** Called by the spine every server tick after all modules ticked. */
	public static void flush(MinecraftServer server) {
		if (DIRTY.isEmpty() || server.getTickCount() % 5 != 0) {
			return;
		}
		for (UUID id : Set.copyOf(DIRTY)) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				syncNow(player);
			}
		}
		DIRTY.clear();
	}

	public static void forget(ServerPlayer player) {
		DIRTY.remove(player.getUUID());
	}

	/** Recomputes the synced look; only sends it when something changed. */
	public static void refreshVisual(ServerPlayer player) {
		CultivationData data = get(player);
		Rank rank = data.rank();
		int flags = 0;
		if (data.meditating()) {
			flags |= VisualState.MEDITATING;
		}
		if (data.flyingSword()) {
			flags |= VisualState.FLYING_SWORD;
		}
		if (data.domainActive()) {
			flags |= VisualState.DOMAIN;
		}
		if (data.dharmaActive()) {
			flags |= VisualState.DHARMA;
		}
		if (data.breakingThrough()) {
			flags |= VisualState.BREAKING_THROUGH;
		}
		if (data.deviating()) {
			flags |= VisualState.DEVIATION;
		}
		if (data.ascended()) {
			flags |= VisualState.ASCENDED;
		}
		if (data.enlightened()) {
			flags |= VisualState.ENLIGHTENED;
		}
		if (data.cosmetic(CultivationData.COSMETIC_AURA)) {
			flags |= VisualState.SHOW_AURA;
		}
		if (data.cosmetic(CultivationData.COSMETIC_ROBE)) {
			flags |= VisualState.SHOW_ROBE;
		}
		if (data.cosmetic(CultivationData.COSMETIC_BACK_SWORD)) {
			flags |= VisualState.SHOW_BACK_SWORD;
		}
		if (data.cosmetic(CultivationData.COSMETIC_EYES)) {
			flags |= VisualState.SHOW_EYES;
		}
		if (data.cosmetic(CultivationData.COSMETIC_DAO_CIRCLE)) {
			flags |= VisualState.SHOW_DAO_CIRCLE;
		}
		if (tribulationFlag.test(player)) {
			flags |= VisualState.TRIBULATION;
		}
		Dao dao = data.dao();
		ItemStack sword = bestSword(player);
		String swordId = sword.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(sword.getItem()).toString();
		VisualState state = new VisualState(rank.realm().ordinal(), rank.layer(), rank.stage(), dao == null ? -1 : dao.ordinal(),
				Element.mainColor(data.rootElements()), flags, swordId);
		if (!state.equals(player.getAttached(ModAttachments.VISUAL))) {
			player.setAttached(ModAttachments.VISUAL, state);
		}
	}

	/** Set by the tribulation module: true while a tribulation is running for the player. */
	public static java.util.function.Predicate<ServerPlayer> tribulationFlag = player -> false;

	/** The sword the player would fly on / carry on the back: the held one, else the best one in the inventory. */
	public static ItemStack bestSword(Player player) {
		ItemStack held = player.getMainHandItem();
		if (held.is(ItemTags.SWORDS)) {
			return held;
		}
		ItemStack best = ItemStack.EMPTY;
		int bestRank = Integer.MAX_VALUE;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty() || !stack.is(ItemTags.SWORDS)) {
				continue;
			}
			int index = SWORD_ORDER.indexOf(stack.getItem());
			int rankOf = index < 0 ? SWORD_ORDER.size() : index;
			if (rankOf < bestRank) {
				bestRank = rankOf;
				best = stack;
			}
		}
		return best;
	}

	private Cultivators() {
	}
}
