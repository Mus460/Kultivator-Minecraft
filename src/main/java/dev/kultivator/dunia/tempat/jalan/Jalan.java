package dev.kultivator.dunia.tempat.jalan;

import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Init of 38W roads (DESIGN 38.C.6): road plan, road structure, milestones, road shrines, rest posts, ferries, bandit
 * camps. Called once from {@code Tempat.init()}. Registry entries (the raft entity, the road map payload) are
 * registered always; {@code jalan.worldgen} only turns the built-in pack {@code kultivator:dunia_jalan} on or off
 * ({@code TempatSpine}), {@code jalan.begal} the bandits, {@code tempat.aktif} everything else (38.0 rule 17).
 */
public final class Jalan {

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		JalanEntities.init();
		JalanNet.register();
		DuniaAnchors.register("pos", new PosAnchors());
		KemahBegal.init();
		UseBlockCallback.EVENT.register(KuilJalan::pakai);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> Tonggak.kirim(handler.player)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PapanTonggak.lupa(handler.player.getUUID()));
		// a newly found capital adds its district plan to the map
		DuniaEvents.SITE_DISCOVERED.register((player, site) -> {
			if (site.kind().equals("kota/kota_besar")) {
				Tonggak.kirim(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Penyeberangan.reset();
			PapanTonggak.reset();
			KemahBegal.reset();
			RencanaJalan.lupakan();
		});
		JalanCommands.init();
	}

	private Jalan() {
	}
}
