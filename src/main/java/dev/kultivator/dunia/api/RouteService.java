package dev.kultivator.dunia.api;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/** Module T's flying ship routes (DESIGN 34.5.15, contract C34.T1). The default: no ships, no routes. */
public interface RouteService {

	RouteService NONE = new RouteService() {
	};

	/**
	 * A voyage destination another module offers (D's {@code laut}): {@code allowed} tells whether the captain may sail
	 * there from that level, {@code arrive} moves the captain and the listed passengers when the ship gets there.
	 */
	default void registerDestination(String id, Component name, BiPredicate<ServerPlayer, ServerLevel> allowed,
			BiConsumer<ServerPlayer, List<Entity>> arrive) {
	}

	/** True while {@code player} sits on a flying ship. */
	default boolean aboard(ServerPlayer player) {
		return false;
	}
}
