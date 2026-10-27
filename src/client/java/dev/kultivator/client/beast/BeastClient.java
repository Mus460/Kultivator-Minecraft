package dev.kultivator.client.beast;

import dev.kultivator.client.fx.BeastFx;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Client setup for spirit beasts. The auras and the evolution cinematic are drawn by {@link BeastFx} from the synced
 * {@code kultivator:spirit_beast} attachment and {@code BEAST_EVOLVE} packets; here running cinematics are forgotten
 * when the player leaves a world.
 */
public final class BeastClient {

	public static void init() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> BeastFx.clear()));
	}

	private BeastClient() {
	}
}
