package dev.kultivator.client.dunia;

/**
 * Client entry of Dunia Kultivasi yang Luas (DESIGN 34), called once from {@code KultivatorClient.onInitializeClient}
 * after the Kitab Panduan client. Named {@code DuniaContentClient} because {@code DuniaClient} in this package is the
 * phase-8 Dunia screen router (DESIGN 25, 33.7 P), which this class does not touch.
 * <p>
 * Each module owns exactly ONE line below (DESIGN 34.6) and replaces only its own commented line. A module's client
 * init registers its payload receivers ({@code ClientPlayNetworking}), entity renderers, HUD layers, screens and client
 * settings rows; every visual is budgeted by {@code EffectQuality} (Minimal / Normal / Maximal, {@code othersScale}) and
 * the 7b {@code EpicBudget}, with no post chain (Sodium and Iris safe, DESIGN 34.0 rule V).
 */
public final class DuniaContentClient {

	private static boolean initialised;

	/** From {@code KultivatorClient.onInitializeClient}. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		// Spine client (DESIGN 34.4, module K): the anchor's no-op renderer and the shared client caches.
		DuniaSpineClient.init();
		// K Kota & NPC:
		dev.kultivator.client.dunia.kota.KotaClient.init();
		// R Reruntuhan, Penjara & Menara:
		dev.kultivator.client.dunia.reruntuhan.ReruntuhanClient.init();
		// L Langit & Cuaca:
		dev.kultivator.client.dunia.langit.LangitClient.init();
		// B Naga & Binatang:
		dev.kultivator.client.dunia.naga.NagaClient.init();
		// D Dimensi baru:
		dev.kultivator.client.dunia.dimensi.DimensiClient.init();
		// T Kapal & Peta:
		dev.kultivator.client.dunia.kapal.KapalClient.init();
	}

	private DuniaContentClient() {
	}
}
