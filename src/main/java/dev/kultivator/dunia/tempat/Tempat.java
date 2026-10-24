package dev.kultivator.dunia.tempat;

/**
 * Kota Besar dan Tempat untuk Semua Fitur (DESIGN 38, 38.T): the one entry of every 38 package, called once from
 * {@code DuniaContent.init()} on both physical sides. The spine ({@link TempatSpine}, 38W) initialises first; then each
 * package's init class. Every line is live from wave W0 against that package's init class; the package owner fills
 * its class and never edits this file (owners in {@code docs/progres/kota-W0.md}). Runtime code of 38 never places,
 * breaks or changes a block except repairs through {@code WorldBreaker.submitRestore} (38.R): capitals, villages,
 * compounds, caves, roads and posts come only from world generation in new chunks (38.0 rule 1).
 */
public final class Tempat {

	private static boolean initialised;

	/** From {@code DuniaContent.init()}. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		// 38W spine: worldgen types, plans, zones, life points, config, uji tempat.
		TempatSpine.init();
		// 38UI screens: payloads, sessions, action receiver.
		dev.kultivator.dunia.tempat.ui.TempatLayar.init();
		// 38W roads, bridges, road-side posts.
		dev.kultivator.dunia.tempat.jalan.Jalan.init();
		// 38KS capital: civil services, law, events.
		dev.kultivator.dunia.tempat.kotabesar.KotaBesar.init();
		// 38EK money loop: shops, bank, guild missions, sect ledger.
		dev.kultivator.dunia.tempat.ekonomi.Ekonomi.init();
		// 38NL NPC life: residents, schedules, bodies.
		dev.kultivator.dunia.tempat.hidup.Hidup.init();
		// 38ST stories: dialogs, quests, rumours, recurring characters.
		dev.kultivator.dunia.tempat.cerita.Cerita.init();
		// 38PR repair and compensation.
		dev.kultivator.dunia.tempat.perbaikan.Perbaikan.init();
		// 38DS mortal villages and clan villages.
		dev.kultivator.dunia.tempat.kampung.Kampung.init();
		// 38DS NPC sect compounds.
		dev.kultivator.dunia.tempat.kompleks.Kompleks.init();
		// 38GU caves and natural sites.
		dev.kultivator.dunia.tempat.gua.Gua.init();
		// 38TK themed cities (38.T).
		dev.kultivator.dunia.tempat.tema.KotaTema.init();
		// 38W guide milestones of chapters 18-19 that no place module grants (38.D.11).
		PanduanTempat.init();
	}

	private Tempat() {
	}
}
