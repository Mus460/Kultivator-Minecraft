package dev.kultivator.client.sekte;

/**
 * Sekte yang Hidup on the client (DESIGN 36): the one client entry of the ten modules, called once from
 * {@code KultivatorClient.onInitializeClient}. Each module owns exactly ONE line below (DESIGN 36.4) and registers there
 * its payload receivers, renderers, screens, hub cards ({@code client.hub.HubCards.register}), poses
 * ({@link SektePoses#register}) and HUD layers. Client code never references a module's server internals; it reads only
 * what its own payloads bring and the shared constants of {@code sekte.SekteIds} and {@code sekte.api} records.
 */
public final class SekteContentClient {

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		// P Kepribadian & Kesetiaan (36, 38):
		dev.kultivator.client.sekte.kepribadian.KepribadianClient.init();
		// R Hubungan antar-murid (37):
		dev.kultivator.client.sekte.hubungan.HubunganClient.init();
		// T Turnamen sekte (39):
		dev.kultivator.client.sekte.turnamen.TurnamenClient.init();
		// G Ujian Tangga Seribu (40):
		dev.kultivator.client.sekte.tangga.TanggaClient.init();
		// A Paviliun Kitab & Pemakaman / Altar Leluhur (41, 45):
		dev.kultivator.client.sekte.paviliun.PaviliunClient.init();
		// E Tetua & Pewaris (42, 50):
		dev.kultivator.client.sekte.tetua.TetuaClient.init();
		// X Misi luar & kepulangan (43, 44):
		dev.kultivator.client.sekte.misi.MisiClient.init();
		// S Seragam, Lambang & Bendera (46, 47):
		dev.kultivator.client.sekte.seragam.SeragamClient.init();
		// D Diplomasi & Sekte Rival (48, 49):
		dev.kultivator.client.sekte.diplomasi.DiplomasiClient.init();
		// W Kebun Obat & Tambang Roh (51, 52):
		dev.kultivator.client.sekte.kebun.KebunClient.init();
		// Murid Mandiri, DESIGN 39 + 39.F (39E owns MandiriClient, which calls 39F's SeteruClient):
		dev.kultivator.client.sekte.mandiri.MandiriClient.init();
	}

	private SekteContentClient() {
	}
}
