package dev.kultivator.dunia.kapal;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.api.RivalService;
import dev.kultivator.dunia.api.RouteService;
import dev.kultivator.dunia.peta.PetaItems;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/**
 * Module T's {@link RouteService} (contract C34.T1) and the voyage helpers of the ships: the destinations other modules
 * register (D's {@code laut}), the sky pirates (their band and the spoils they leave when driven off) and a route's
 * arrival.
 * <p>
 * A route is a crossing: the ship flies {@link #ROUTE_CROSSING} blocks on by autopilot, then sets down; its people are
 * handed to the route's {@code arrive} (the captain and every other rider). Before that the ship is stowed back into its
 * keel in the captain's inventory (riders dismounted first), so a route that moves players to another dimension never
 * leaves an empty ship behind and the captain launches it again on the other side.
 */
public final class Voyages implements RouteService {

	public static final double ROUTE_CROSSING = 2000.0D;
	public static final double SPOILS_POINTER = 0.4D;
	public static final double SPOILS_METEOR = 0.25D;
	public static final double SPOILS_SUPREME = 0.1D;

	/** A registered destination. */
	public record Route(String id, Component name, BiPredicate<ServerPlayer, ServerLevel> allowed, BiConsumer<ServerPlayer, List<Entity>> arrive) {
	}

	private static final Map<String, Route> ROUTES = new LinkedHashMap<>();
	static final Voyages INSTANCE = new Voyages();

	private Voyages() {
	}

	@Override
	public void registerDestination(String id, Component name, BiPredicate<ServerPlayer, ServerLevel> allowed,
			BiConsumer<ServerPlayer, List<Entity>> arrive) {
		if (id == null || id.isBlank() || name == null || allowed == null || arrive == null) {
			return;
		}
		ROUTES.put(id, new Route(id, name, allowed, arrive));
	}

	@Override
	public boolean aboard(ServerPlayer player) {
		return player != null && player.getVehicle() instanceof KapalRohEntity ship && !ship.docked();
	}

	static List<Route> routes() {
		return new ArrayList<>(ROUTES.values());
	}

	static @Nullable Route route(String id) {
		return id == null ? null : ROUTES.get(id);
	}

	/** The route's arrival: riders off, the ship into the captain's keel, then the route moves the people. */
	static void arriveRoute(KapalRohEntity ship, ServerPlayer captain, Route route) {
		List<Entity> riders = new ArrayList<>();
		for (Entity passenger : ship.getPassengers()) {
			if (passenger != captain) {
				riders.add(passenger);
			}
		}
		ServerLevel level = (ServerLevel) ship.level();
		if (!ship.landDisciples(level, true)) {
			// a disciple could not land here: the route waits until the ship is somewhere with room
			Msg.actionBar(captain, Component.translatable("kultivator.kapal.stow.disciples").withColor(0xFFD54A));
			return;
		}
		ship.ejectPassengers();
		ship.stowForRoute(captain);
		try {
			route.arrive().accept(captain, riders);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kultivator: route {} failed to arrive", route.id(), exception);
		}
	}

	/** The Bajak Langit band: module K's rivals when K is on, else a band of rogue cultivators. */
	static void pirates(ServerLevel level, KapalRohEntity ship, ServerPlayer target, int tier, int realm) {
		RivalService rivals = DuniaApi.rivals();
		if (rivals != RivalService.NONE) {
			try {
				rivals.ambush(target, tier);
				return;
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Kultivator: the rival ambush of the sky pirates failed, rogues come instead", exception);
			}
		}
		int size = 2 + Math.min(2, tier / 2);
		int[] tiers = new int[size];
		tiers[0] = Math.min(4, tier + 1);
		for (int i = 1; i < size; i++) {
			tiers[i] = Math.max(1, tier - (i % 2));
		}
		// beside the hull on the raider's side (the ship sits low by now): they jump down from the raider and come aboard
		KapalBajakEntity raider = ship.raider();
		Vec3 centre = raider != null ? ship.position().add(raider.position()).scale(0.5D) : ship.position().add(0.0D, ship.kind().deck(), 0.0D);
		RogueCultivators.spawnBand(level, centre, tiers, realm, RogueCultivators.ROLE_AMBUSH, null, cleared -> {
		});
	}

	/**
	 * The spoils of a driven-off band for {@code receiver} (into the inventory, the rest at the feet): spirit stones by the
	 * band's tier (1: 8 Low + 1 Mid, 2: 4 Low + 3 Mid, 3: 6 Mid + 1 High, 4: 4 Mid + 2 High and a 10 % Supreme), a 40 %
	 * Gulungan Penunjuk and a 25 % Batu Meteor Roh (module L's item, when it exists).
	 */
	static void spoils(ServerLevel level, KapalRohEntity ship, ServerPlayer receiver, int tier) {
		RandomSource random = level.getRandom();
		List<ItemStack> loot = new ArrayList<>();
		switch (Math.max(1, Math.min(4, tier))) {
			case 1 -> {
				loot.add(SpiritStones.stack(SpiritStones.LOW, 8));
				loot.add(SpiritStones.stack(SpiritStones.MID, 1));
			}
			case 2 -> {
				loot.add(SpiritStones.stack(SpiritStones.LOW, 4));
				loot.add(SpiritStones.stack(SpiritStones.MID, 3));
			}
			case 3 -> {
				loot.add(SpiritStones.stack(SpiritStones.MID, 6));
				loot.add(SpiritStones.stack(SpiritStones.HIGH, 1));
			}
			default -> {
				loot.add(SpiritStones.stack(SpiritStones.MID, 4));
				loot.add(SpiritStones.stack(SpiritStones.HIGH, 2));
				if (random.nextDouble() < SPOILS_SUPREME) {
					loot.add(SpiritStones.stack(SpiritStones.SUPREME, 1));
				}
			}
		}
		if (random.nextDouble() < SPOILS_POINTER) {
			loot.add(new ItemStack(PetaItems.GULUNGAN_PENUNJUK));
		}
		Item meteor = BuiltInRegistries.ITEM.getOptional(Kultivator.id("batu_meteor_roh")).orElse(null);
		if (meteor != null && random.nextDouble() < SPOILS_METEOR) {
			loot.add(new ItemStack(meteor));
		}
		MutableComponent list = Component.empty();
		boolean first = true;
		for (ItemStack stack : loot) {
			if (stack.isEmpty()) {
				continue;
			}
			if (!first) {
				list.append(Component.literal(", "));
			}
			first = false;
			list.append(Component.literal(stack.getCount() + "× ")).append(stack.getHoverName());
			Kapal.give(receiver, stack);
		}
		level.playSound(null, ship.getX(), ship.getY() + ship.kind().deck(), ship.getZ(), SoundEvents.CHEST_OPEN, SoundSource.NEUTRAL, 1.0F, 0.9F);
		Msg.chat(receiver, Component.translatable("kultivator.kapal.pirates.spoils", list).withStyle(ChatFormatting.GOLD));
		KapalNet.send(receiver, new KapalNet.Event(ship.getId(), KapalNet.Event.EV_SPOILS, ship.getX(), ship.getY() + ship.kind().deck(), ship.getZ(),
				tier));
	}
}
