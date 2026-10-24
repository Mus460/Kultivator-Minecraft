package dev.kultivator.dunia;

/**
 * Dunia Kultivasi yang Luas (DESIGN 34): the one entry of the six world-content modules, called once from
 * {@code Kultivator.onInitialize} on both physical sides, after the phase-9 and voice modules and before
 * {@code ModItems.init()} (the modules register items) and {@code TechniqueRegistry.init()}.
 * <p>
 * Each module owns exactly ONE line below (DESIGN 34.6): it replaces its own commented line with the real call and
 * never touches another module's line. The spine (34.4, owned by module K and landed first) initialises before the
 * modules, so every module may use {@code DuniaAnchors}, {@code DuniaSites}, {@code DuniaEvents}, {@code DuniaConfig},
 * {@code DuniaCommands}, {@code DuniaPacks} and {@code DuniaTickets} from its own init.
 * <p>
 * Every module registers its own payloads ({@code PayloadTypeRegistry}, as {@code aura.AuraPreviews} does), entities,
 * items, commands (under {@code /kultivasi uji <module>} through {@code DuniaCommands}) and config section
 * ({@code config/kultivator-dunia.json} through {@code DuniaConfig}); nothing here edits {@code network/Payloads} or
 * {@code Net}. Runtime code never places, breaks or changes a block: new towns, ruins, prisons, towers, islands and
 * palaces come only from world generation in new chunks (DESIGN 34.0).
 */
public final class DuniaContent {

	private static boolean initialised;

	/** From {@code Kultivator.onInitialize} (both physical sides). Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		// Spine (DESIGN 34.4, module K lands it first):
		DuniaSpine.init();
		// K Kota & NPC (53, 54, 61, 66):
		dev.kultivator.dunia.kota.Kota.init();
		// R Reruntuhan, Penjara & Menara (55, 62, 65):
		dev.kultivator.dunia.reruntuhan.Reruntuhan.init();
		// L Langit & Cuaca (59, 60, 68):
		dev.kultivator.dunia.langit.Langit.init();
		// B Naga & Binatang (57, 58):
		dev.kultivator.dunia.naga.Naga.init();
		// D Dimensi baru (63 Dunia Bawah, 64 Laut Tanpa Batas):
		dev.kultivator.dunia.dimensi.Dimensi.init();
		// T Kapal & Peta (56, 67):
		dev.kultivator.dunia.kapal.Kapal.init();
		// DESIGN 38 Kota Besar dan Tempat (38W spine + every 38 package, 38.T themed cities):
		dev.kultivator.dunia.tempat.Tempat.init();
	}

	private DuniaContent() {
	}
}
