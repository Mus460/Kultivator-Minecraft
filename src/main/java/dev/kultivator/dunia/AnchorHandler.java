package dev.kultivator.dunia;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * What a module does with its anchors (DESIGN 34.4.1, contract C34.S1), registered once with
 * {@link DuniaAnchors#register(String, AnchorHandler)} for the module's kind prefix. Every method runs on the server
 * thread; a method that throws is logged and the anchor stays dormant until its next call.
 */
public interface AnchorHandler {

	/** The anchor's chunk loaded (or the anchor was just generated). */
	default void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
	}

	/**
	 * Every 20 ticks (staggered by entity id; every 60 under the MSPT guard) while at least one survival or adventure
	 * player stands within {@link #radius()} blocks: {@code within} lists them, nearest first.
	 */
	default void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
	}

	/** The anchor's chunk unloads (or the anchor was removed). Clean up what {@link #nearby} spawned. */
	default void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
	}

	/** The range of {@link #nearby} in blocks (default 48). */
	default int radius() {
		return 48;
	}
}
