package dev.kultivator.client.dunia.tempat.kotabesar;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * Client init of 38KS, the capital (DESIGN 38.B.6, 38.O.3): the receiver of {@link KotaBesarPayload} (festival lanterns
 * and sky lanterns, fireworks, the night market, tournament banners, temple incense through 38MD's {@code HiasanDunia};
 * the teleport pillar, arrival flash and cloud-boat wipe; the beast tide's defence bar), the HUD element and the client
 * tick. Called once from {@code TempatClient.init()}. Every visual obeys {@code EffectQuality} and {@code EpicBudget},
 * with no post chain (Sodium and Iris safe); client code only, never referenced by server code.
 */
public final class KotaBesarClient {

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(KotaBesarPayload.TYPE, (payload, context) -> KotaBesarFx.terima(payload));
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Kultivator.id("kota_besar_hud"), new KotaBesarHud());
		ClientTickEvents.END_CLIENT_TICK.register(KotaBesarFx::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> KotaBesarFx.bersihkan()));
	}

	private KotaBesarClient() {
	}
}
