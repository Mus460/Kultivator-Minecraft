package dev.kultivator.dunia.tempat;

import dev.kultivator.dunia.DuniaPacks;
import dev.kultivator.dunia.tempat.worldgen.TempatTypes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * The spine of DESIGN 38 places (owner 38W), initialised first by {@link Tempat#init()}: world-generation types
 * (always registered, 38.0 rule 17), the {@code tempat} and {@code jalan} config sections, the zone and safe-ground
 * providers, the life-point reader, the capital plan and the {@code uji tempat} node. Modules of 38 may use
 * {@link RencanaRegistry}, {@link TempatApi}, {@link TempatEvents}, {@link TempatZones} and {@link TitikTemplat} from
 * their own init after this.
 */
public final class TempatSpine {

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		TempatTypes.register();
		TempatConfig.get();
		TempatConfig.JalanConfig jalan = TempatConfig.JalanConfig.get();
		TempatZones.init();
		TitikTemplat.init();
		RencanaRegistry.register(RencanaKotaBesar.ID, RencanaKotaBesar.INSTANCE);
		DuniaPacks.worldgen("jalan", jalan.worldgen);
		TempatCommands.init();
		ServerLifecycleEvents.SERVER_STARTING.register(TempatApi::server);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TempatApi.server(null);
			TempatZones.reset();
			PutusanTanah.lupakan();
		});
	}

	private TempatSpine() {
	}
}
