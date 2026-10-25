package dev.kultivator.sekte;

/**
 * Sekte yang Hidup (DESIGN 36): the one entry of the ten sect-and-disciple modules, called once from
 * {@code Kultivator.onInitialize} on both physical sides, after the Duel module and before {@code ModItems.init()} (the
 * modules register items) and {@code TechniqueRegistry.init()}.
 * <p>
 * Each module owns exactly ONE line below (DESIGN 36.4): it replaces its own commented line with the real call and never
 * touches another module's line. The lead's core (config, commands, memories, the API and bus in {@code sekte.api}, the
 * template hook {@code sect.build.blueprint.TemplateExtensions}) initialises first, so every module may use
 * {@link SekteConfig}, {@link SekteCommands}, {@link Ingatan}, {@link SekteLog}, {@code SekteApi} and {@code SekteBus}
 * from its own init.
 * <p>
 * Every module registers its own payloads ({@code PayloadTypeRegistry}, as {@code aura.AuraPreviews} does), entities,
 * items, commands (under {@code /kultivasi uji perguruan <module>} through {@link SekteCommands}), config section
 * ({@code config/kultivator-sekte.json} through {@link SekteConfig}) and template components (through
 * {@code TemplateExtensions.register}); nothing here edits {@code network/Payloads} or {@code Net}. Runtime code changes
 * blocks only through the sect build engine (deposited materials) or {@code kehancuran.WorldBreaker} (the player's
 * own Mode Kehancuran); NPC sect mountains come only from world generation in new chunks (DESIGN 36.0).
 */
public final class SekteContent {

	/** The lead's own config section {@code umum}. */
	public static final class Umum {
		/** Master switch of the whole phase: off makes every module skip its runtime init (registries and data stay). */
		public boolean aktif = true;
		/** The ten module switches, by id ({@link SekteIds#MODULES}); a module reads its own with {@link #on}. */
		public boolean kepribadian = true;
		public boolean hubungan = true;
		public boolean turnamen = true;
		public boolean tangga = true;
		public boolean paviliun = true;
		public boolean tetua = true;
		public boolean misi = true;
		public boolean seragam = true;
		public boolean diplomasi = true;
		public boolean kebun = true;
		/** Murid Mandiri (DESIGN 39 + 39.F, {@link SekteIds#MANDIRI}): disciples who live, travel and grow on their own. */
		public boolean mandiri = true;
		/** Memories kept per disciple (4-16). */
		public int ingatanMaks = Ingatan.MAX_MEMORIES;
	}

	private static Umum umum = new Umum();
	private static boolean initialised;

	/** From {@code Kultivator.onInitialize} (both physical sides). Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		umum = SekteConfig.section("umum", Umum.class, Umum::new, u -> {
			u.ingatanMaks = Math.max(4, Math.min(Ingatan.MAX_MEMORIES, u.ingatanMaks));
			return u;
		});
		SekteCommands.init();
		// K1: registry content (items, entity types, payload types, template components, the gate placement type) NEVER
		// follows a switch: a world keeps its items and a client with another config still connects. Only the runtime
		// logic below (listeners, ticks, commands, receivers) obeys umum.aktif and the module switches. Sekte Langit (37)
		// follows the same rule for langit.aktif.
		dev.kultivator.sekte.kepribadian.Kepribadian.registerContent();
		dev.kultivator.sekte.hubungan.Hubungan.registerContent();
		dev.kultivator.sekte.turnamen.Turnamen.registerContent();
		dev.kultivator.sekte.tangga.Tangga.registerContent();
		dev.kultivator.sekte.paviliun.Paviliun.registerContent();
		dev.kultivator.sekte.tetua.Tetua.registerContent();
		dev.kultivator.sekte.misi.Misi.registerContent();
		dev.kultivator.sekte.seragam.Seragam.registerContent();
		dev.kultivator.sekte.diplomasi.Diplomasi.registerContent();
		dev.kultivator.sekte.kebun.Kebun.registerContent();
		dev.kultivator.sekte.mandiri.Mandiri.registerContent();
		// Gabung Sekte, DESIGN 41 (41A): always called; it registers its content first and checks umum.aktif and
		// gabungSekte.aktif itself before any runtime wiring.
		dev.kultivator.sekte.gabung.Gabung.init();
		if (!umum.aktif) {
			return;
		}
		// P Kepribadian & Kesetiaan (36, 38):
		dev.kultivator.sekte.kepribadian.Kepribadian.init();
		// R Hubungan antar-murid (37):
		dev.kultivator.sekte.hubungan.Hubungan.init();
		// T Turnamen sekte (39):
		dev.kultivator.sekte.turnamen.Turnamen.init();
		// G Ujian Tangga Seribu (40):
		dev.kultivator.sekte.tangga.Tangga.init();
		// A Paviliun Kitab & Pemakaman / Altar Leluhur (41, 45):
		dev.kultivator.sekte.paviliun.Paviliun.init();
		// E Tetua & Pewaris (42, 50):
		dev.kultivator.sekte.tetua.Tetua.init();
		// X Misi luar & kepulangan (43, 44):
		dev.kultivator.sekte.misi.Misi.init();
		// S Seragam, Lambang & Bendera (46, 47):
		dev.kultivator.sekte.seragam.Seragam.init();
		// D Diplomasi & Sekte Rival (48, 49):
		dev.kultivator.sekte.diplomasi.Diplomasi.init();
		// W Kebun Obat & Tambang Roh (51, 52):
		dev.kultivator.sekte.kebun.Kebun.init();
		// Murid Mandiri, DESIGN 39 + 39.F (39A-39F; the module switch is checked inside):
		dev.kultivator.sekte.mandiri.Mandiri.init();
	}

	/** True when the phase and module {@code module} ({@link SekteIds#MODULES}) are switched on. */
	public static boolean on(String module) {
		if (!umum.aktif || module == null) {
			return false;
		}
		return switch (module) {
			case SekteIds.P -> umum.kepribadian;
			case SekteIds.R -> umum.hubungan;
			case SekteIds.T -> umum.turnamen;
			case SekteIds.G -> umum.tangga;
			case SekteIds.A -> umum.paviliun;
			case SekteIds.E -> umum.tetua;
			case SekteIds.X -> umum.misi;
			case SekteIds.S -> umum.seragam;
			case SekteIds.D -> umum.diplomasi;
			case SekteIds.W -> umum.kebun;
			case SekteIds.MANDIRI -> umum.mandiri;
			default -> false;
		};
	}

	/** The lead's section (read only for modules). */
	public static Umum umum() {
		return umum;
	}

	private SekteContent() {
	}
}
