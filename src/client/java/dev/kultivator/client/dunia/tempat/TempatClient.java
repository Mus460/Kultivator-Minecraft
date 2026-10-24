package dev.kultivator.client.dunia.tempat;

/**
 * Client entry of DESIGN 38 (Kota Besar dan Tempat, 38.T Kota Bertema), called once from
 * {@code DuniaContentClient.init()}. Every line is live from wave W0 against that package's client init class; the
 * package owner fills its own class and never edits this file (owners in {@code docs/progres/kota-W0.md}). Every
 * visual of 38 is budgeted by {@code EffectQuality} and the 7b {@code EpicBudget}, LOD by distance, no post chain
 * (Sodium and Iris safe, 38.0 rule 6).
 */
public final class TempatClient {

	private static boolean initialised;

	/** From {@code DuniaContentClient.init()}. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		// 38UI kit and core screens ("Pasar Giok"): screen registry, payload receiver.
		dev.kultivator.client.tempat.ui.UiTempatClient.init();
		// 38MD models, textures, entity renderers, festival decorations, props.
		dev.kultivator.client.dunia.tempat.model.TempatModelClient.init();
		// 38NL far crowds and stand crowds (impostors).
		dev.kultivator.client.dunia.tempat.hidup.HidupClient.init();
		// 38KS capital event visuals (lanterns, fireworks sprites, banners).
		dev.kultivator.client.dunia.tempat.kotabesar.KotaBesarClient.init();
		// 38ST story scenes.
		dev.kultivator.client.dunia.tempat.cerita.CeritaClient.init();
		// 38PR repair scaffolding.
		dev.kultivator.client.dunia.tempat.perbaikan.PerbaikanClient.init();
		// 38GU caves and sites: KaitModel readers (cage, glyph, cliff door, puppet) and the GuaPayload receiver.
		dev.kultivator.client.dunia.tempat.gua.GuaClient.init();
		// 38W milestone billboards and the road map overlay.
		dev.kultivator.client.dunia.tempat.jalan.JalanClient.init();
		// 38TV themed cities: receiver of TemaNet, models, fx, screens (TemaClient calls TemaModelClient and TemaUiClient).
		dev.kultivator.client.dunia.tempat.tema.TemaClient.init();
	}

	private TempatClient() {
	}
}
