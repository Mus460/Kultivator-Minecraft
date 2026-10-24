package dev.kultivator.client.dunia.tempat.cerita;

import dev.kultivator.dunia.tempat.cerita.AdeganPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client init of 38ST stories (DESIGN 38.S): the receiver of the short story scenes ({@link AdeganCerita}, 7b camera
 * rules: at most 4 s, skippable, Penuh/Singkat/Mati, never in real danger). Called once from {@code TempatClient.init()}.
 * Every visual obeys {@code EffectQuality} (Minimal / Normal / Maximal) and {@code EpicBudget} (scenes go through the
 * scene system), with no post chain (Sodium and Iris safe); client code only, never referenced by server code. The
 * dialog and journal screens are drawn by the 38UI kit from the sheets {@code TempatDialog} / {@code JurnalCerita} send.
 */
public final class CeritaClient {

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ClientPlayNetworking.registerGlobalReceiver(AdeganPayload.TYPE, (payload, context) -> context.client().execute(() -> AdeganCerita.terima(payload)));
	}

	private CeritaClient() {
	}
}
