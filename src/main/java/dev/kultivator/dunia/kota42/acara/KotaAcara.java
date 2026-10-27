package dev.kultivator.dunia.kota42.acara;

/**
 * Runtime init of 42D Kehidupan & Acara (DESIGN 42.7, 42.9.6), called once from {@code KotaHidup.init} on both sides (registries are not here: 42R0's
 * registries are called from {@code KotaHidup.registerContent}). Skeleton of 42A0; the owner module fills it with
 * troupes, morning routine, night watch, street events (pickpocket, brawl, cart, small fire), seasons and festivals, ceremonies, the night district; the ceremony overrider ({@code WargaApi.daftarPenimpa}, order 100); the rhythm handler ({@code KotaPayloads.setPenerimaIrama}); Sibuk {@code KotaHidup.tandaiSibuk}; subs {@code uji kota_hidup acara}. Behaviour checks {@code kotaAcara} at run time ({@code KotaConfig}), so a reload can switch it off and on.
 */
public final class KotaAcara {

	private static boolean initialised;

	/** From {@code KotaHidup.init}. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		// the calendar hub starts the morning routine, the night watch, the seasons, the troupes and the night district
		KalenderKota.init();
		// street events (also starts Upacara) and the shadow theatre
		PeristiwaJalan.init();
		TeaterBayang.init();
	}

	private KotaAcara() {
	}
}
