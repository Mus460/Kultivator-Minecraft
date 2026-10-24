package dev.kultivator.dunia.tempat.tema.pelabuhan;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Kota Pelabuhan (DESIGN 38.T.3): the init of this theme package, called by {@code KotaTema.init()}. Registers the
 * anchors of module {@code pelabuhan} ({@link PelabuhanAnchors}), the merchant junk schedule ({@link JadwalKapal}), the
 * Kapal Lintas Laut ({@link LintasLaut}), the shipyard ({@link Galangan}, screen {@link GalanganLayar}), the counters of
 * screen {@code tema_kapal} ({@link TokoPelabuhan}), boat hire ({@link PerahuSewa}), the flying-ship tower
 * ({@link MenaraTambat}), the festival ({@link HariDewiLaut}), pirate raids ({@link SerbuanBajakLaut}), the chain
 * {@code tema_pelabuhan_pelita} ({@link PelabuhanCerita}, puzzles {@link TekaPelabuhan}) and {@code uji tema kapal}
 * ({@link PelabuhanCommands}). Shared state through {@code KotaTemaData.kota("pelabuhan", situs)}, events through
 * {@code TemaKalender.daftar}, screens through {@code TemaLayar.daftar}, quest hooks through 38ST's
 * {@code Syarat/Akibat/QuestTracker.daftar*}; no 38TK inti file is edited.
 */
public final class Pelabuhan {

	private static boolean initialised;

	/** From {@code KotaTema.init()} (both physical sides; everything registered here is server behaviour). */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		PelabuhanLayar.init();
		PelabuhanAnchors.init();
		JadwalKapal.init();
		TokoPelabuhan.init();
		GalanganLayar.init();
		TekaPelabuhan.init();
		LintasLaut.init();
		PerahuSewa.init();
		MenaraTambat.init();
		HariDewiLaut.init();
		SerbuanBajakLaut.init();
		PelabuhanCerita.init();
		PelabuhanCommands.init();
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PelabuhanAnchors.reset();
			PelabuhanKota.reset();
		});
	}

	private Pelabuhan() {
	}
}
