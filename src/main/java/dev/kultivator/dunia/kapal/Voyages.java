package dev.kultivator.dunia.kapal;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.api.RivalService;
import dev.kultivator.dunia.api.RouteService;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
 * register (D's {@code laut}), the sky pirates and a route's arrival.
 * <p>
 * A route is a crossing: the ship flies {@link #ROUTE_CROSSING} blocks on by autopilot, then sets down; its people are
 * handed to the route's {@code arrive} (the captain and every other rider). Before that the ship is stowed back into its
 * keel in the captain's inventory (riders dismounted first), so a route that moves players to another dimension never
 * leaves an empty ship behind and the captain launches it again on the other side.
 */
public final class Voyages implements RouteService {

	public static final double ROUTE_CROSSING = 2000.0D;

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
		Vec3 centre = ship.position().add(0.0D, ship.kind().deck(), 0.0D);
		RogueCultivators.spawnBand(level, centre, tiers, realm, RogueCultivators.ROLE_AMBUSH, null, cleared -> {
		});
	}
}
