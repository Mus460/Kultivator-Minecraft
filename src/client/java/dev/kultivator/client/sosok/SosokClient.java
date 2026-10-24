package dev.kultivator.client.sosok;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Client entry of Sosok (DESIGN 40.7.3 C40.K3, module 40R): 40K's model layers (before the entity models are baked), 40G's
 * clip catalogue, the 200-tick cleanup of the per-body data, the release of every atlas when the world closes (so a new
 * world starts with 0 atlases, 40.7.6) and the client command {@code /sosok}. Called once from
 * {@code KultivatorClient.onInitializeClient} right after {@code DiscipleClient.init()}.
 */
public final class SosokClient {

	private static final int RAPIKAN_TICKS = 200;
	private static int tick;

	public static void init() {
		SosokJembatan.daftarLapisanModel();
		SosokJembatan.initGerak();
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (++tick >= RAPIKAN_TICKS) {
				tick = 0;
				SosokRender.rapikan();
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(SosokClient::lepas));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> SosokKlienPerintah.register(dispatcher));
	}

	/** Releases every atlas and forgets every body (world closed, {@code /sosok muat_ulang}). */
	static void lepas() {
		SosokJembatan.lepasSemua();
		SosokRender.bersihkan();
	}

	private SosokClient() {
	}
}
