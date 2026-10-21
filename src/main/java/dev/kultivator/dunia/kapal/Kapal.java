package dev.kultivator.dunia.kapal;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.DuniaConfig;
import dev.kultivator.dunia.DuniaTickets;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Module T of DESIGN 34 (Kapal & Peta, ideas 56 and 67), server entry: wired by its one line in {@code DuniaContent}.
 * Registers the {@code kapal} config section, the keel items, the ship entities, the ship payloads, the
 * {@code uji kapal} test node, the {@link Voyages} route service, then initialises the world map ({@code Peta}).
 * Every ship is an entity; nothing here changes a block (DESIGN 34.0 W1/W2).
 */
public final class Kapal {

	private static KapalConfig config = new KapalConfig();
	private static boolean initialised;

	/** A far ship called to its owner: its chunk got a short ticket, the summon waits for it (at most 40 ticks). */
	private record Summon(UUID player, UUID ship, ServerLevel level, int[] left) {
	}

	private static final List<Summon> SUMMONS = new ArrayList<>();

	/** From {@code DuniaContent.init()} (both physical sides). */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		config = DuniaConfig.section("kapal", KapalConfig.class, KapalConfig::new, KapalConfig::clamp);
		KapalItems.init();
		KapalEntities.init();
		KapalNet.register();
		DuniaApi.setRoutes(Voyages.INSTANCE);
		KapalTests.register();
		ServerTickEvents.END_SERVER_TICK.register(Kapal::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SUMMONS.clear());
		dev.kultivator.dunia.peta.Peta.init();
	}

	/** The {@code kapal} section (never null). */
	public static KapalConfig config() {
		return config;
	}

	/** Into the inventory, the rest at the feet (a block-safe item entity). */
	static void give(ServerPlayer player, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), stack);
			item.setDefaultPickUpDelay();
			item.addTag(NoGrief.TAG);
			player.level().addFreshEntity(item);
		}
	}

	/** The ship {@code player} rides, else their deployed ship when it is loaded, else null. */
	static @Nullable KapalRohEntity shipOf(ServerPlayer player) {
		if (player.getVehicle() instanceof KapalRohEntity ship) {
			return ship;
		}
		MinecraftServer server = player.level().getServer();
		KapalFleet.Entry entry = KapalFleet.ship(server, player.getUUID());
		if (entry == null) {
			return null;
		}
		ServerLevel level = server.getLevel(entry.dim());
		return level != null && level.getEntity(entry.ship()) instanceof KapalRohEntity ship ? ship : null;
	}

	/** Brings the player's ship to them: at once when loaded, else after a short ticket loaded its chunk. */
	static boolean summon(ServerPlayer player) {
		KapalRohEntity ship = shipOf(player);
		if (ship != null) {
			ship.summonTo(player);
			return true;
		}
		MinecraftServer server = player.level().getServer();
		KapalFleet.Entry entry = KapalFleet.ship(server, player.getUUID());
		ServerLevel level = entry == null ? null : server.getLevel(entry.dim());
		if (entry == null || level == null) {
			return false;
		}
		DuniaTickets.touch(level, ChunkPos.containing(entry.pos()), 1);
		SUMMONS.add(new Summon(player.getUUID(), entry.ship(), level, new int[]{40}));
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (!SUMMONS.isEmpty()) {
			Iterator<Summon> it = SUMMONS.iterator();
			while (it.hasNext()) {
				Summon summon = it.next();
				Entity found = summon.level().getEntity(summon.ship());
				ServerPlayer player = server.getPlayerList().getPlayer(summon.player());
				if (found instanceof KapalRohEntity ship && player != null) {
					ship.summonTo(player);
					it.remove();
				} else if (--summon.left()[0] <= 0 || player == null) {
					if (player != null) {
						Msg.actionBar(player, Component.translatable("kultivator.kapal.summon_failed").withColor(0xFF8A7A));
					}
					it.remove();
				}
			}
		}
		if (server.getTickCount() % 100 == 37) {
			landPending(server);
		}
	}

	/** Disciples of a destroyed ship that could not land then: they arrive next to their master when they can. */
	private static void landPending(MinecraftServer server) {
		for (UUID owner : KapalFleet.pendingOwners(server)) {
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			ListTag tags = KapalFleet.pending(server, owner);
			if (player == null || tags == null || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			ListTag left = new ListTag();
			for (Tag tag : tags) {
				if (tag instanceof CompoundTag entry) {
					DiscipleEntity back = Disciples.arrive(level, player.position(), entry, owner);
					if (back == null) {
						left.add(entry);
					}
				}
			}
			KapalFleet.setPending(server, owner, left);
		}
	}

	private Kapal() {
	}
}
