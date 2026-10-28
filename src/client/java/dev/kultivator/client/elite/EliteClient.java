package dev.kultivator.client.elite;

import dev.kultivator.Kultivator;
import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.client.fx.FxDispatcher;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * Client side of P7-tempur (DESIGN 19.3.1, 19.3.8): the elite name plate under the crosshair ({@link ElitePlate}); the
 * auras, telegraphs and tribulation rings live in {@code client/fx/EliteFx}, ticked by {@code FxDispatcher}. Called once
 * from {@code KultivatorClient}.
 */
public final class EliteClient {

	public static void init() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("elite_plate"), new ElitePlate());
		// One disconnect hook for the whole client/fx layer (see FxDispatcher.forgetLevel).
		ClientDisconnect.register((handler, client) -> client.execute(FxDispatcher::forgetLevel));
	}

	private EliteClient() {
	}
}
