package dev.kultivator.client.epic.kosmik.surga;

import dev.kultivator.Kultivator;
import net.minecraft.resources.Identifier;

/**
 * The fixed layout of the Heaven stage (DESIGN 32.3): stage metres, x east, y up, z north (the stage axes are the world
 * axes). The Southern Heaven Gate stands at the origin with its opening centre at (0, 30, 0), facing −z; the court runs
 * north on white-jade terraces at y = 0 to the Lingxiao Hall (front at z ≈ 420); the islands float on a local cloud bank
 * at y = −10; the Nine Heavens hang below as nine rings round one column at z = {@link #COLUMN_Z} (ring 9 at y = −60,
 * radius 140, down to ring 1 at y = −420, radius 520); the great sea of clouds lies at y = −460 and the dim mortal land at
 * y = −2000. Every instance of {@code surga_models.json} is listed here with the lowest quality that draws it.
 */
final class SurgaLayout {

	static final Identifier MODELS = Kultivator.id("kosmik/surga_models.json");

	static final Identifier ATLAS = tex("surga_atlas");
	static final Identifier[] SKY = {tex("sky_heaven_128"), tex("sky_heaven_256"), tex("sky_heaven_512")};
	static final Identifier[] CLOUD = {tex("cloud_sea_256"), tex("cloud_sea_512"), tex("cloud_sea_512")};
	static final Identifier PUFF = tex("cloud_puff");
	static final Identifier SHAFT = tex("shaft");
	static final Identifier EMBER = tex("ember_trail");
	static final Identifier PETAL = tex("petal");
	static final Identifier WATERFALL = tex("waterfall");
	static final Identifier RINGS = tex("nine_rings");
	static final Identifier GATE_GLOW = tex("gate_glow");
	static final Identifier LAND = tex("land_below_256");

	/** The gate opening's centre height. */
	static final double GATE_Y = 30.0D;
	/** The column of the Nine Heavens (the rings, the sea and the land anchors), in front of the gate's stairs. */
	static final double COLUMN_Z = -60.0D;
	static final double BANK_Y = -10.0D;
	static final double SEA_Y = -460.0D;
	static final double LAND_Y = -2000.0D;
	/** The local cloud bank under the islands: an ellipse round (0, 200), radii 460 × 560. */
	static final double BANK_CX = 0.0D;
	static final double BANK_CZ = 200.0D;
	static final double BANK_RX = 460.0D;
	static final double BANK_RZ = 560.0D;
	/** The great bronze bell of the bell tower (the javelin's target). */
	static final double BELL_X = 70.0D;
	static final double BELL_Y = 14.5D;
	static final double BELL_Z = 0.0D;
	/** The hall's front (the {@code aula} anchor) and the hall model's origin. */
	static final double HALL_Z = 400.0D;

	/** Ring k (1..9) height: ring 1 at −420, ring 9 at −60. */
	static double ringY(int k) {
		return -420.0D + (k - 1) * 45.0D;
	}

	/** Ring k (1..9) radius: ring 1 520, ring 9 140. */
	static double ringR(int k) {
		return 520.0D - (k - 1) * 47.5D;
	}

	// ---------------------------------------------------------------- budget groups, in takeQuads priority order (32.3.1)

	static final int G_GATE = 0;
	static final int G_HALL = 1;
	static final int G_PROPS = 2;
	static final int G_CLOUD = 3;
	static final int G_RINGS = 4;
	static final int G_ISLANDS = 5;
	static final int G_SHAFTS = 6;
	static final int G_GUARDS = 7;
	static final int G_PAVILION = 8;
	static final int G_FALLS = 9;
	static final int G_PETALS = 10;
	static final int GROUPS = 11;
	static final String[] GROUP_NAMES = {"gate", "hall", "props", "clouds", "rings", "islands", "shafts", "guards", "pavilions", "falls",
			"petals"};

	// ---------------------------------------------------------------- instances

	/** Skipped with "Film kosmik 3D: Ringan" (guards halved). */
	static final int HALVE = 1;
	/** Skipped under a shader pack (the pack profile keeps the kings only). */
	static final int NO_PACK = 2;
	/** The gate: its two door parts turn on their hinges. */
	static final int DOORS = 4;
	/** Never dropped as too small (the gate, the hall, the court). */
	static final int ALWAYS = 8;

	/**
	 * One placed model: its id, origin and yaw (degrees about +y), a uniform scale, the lowest quality that draws it, its
	 * budget group, its bounding sphere (stage centre and radius, for the view cone and the distance LOD) and flags.
	 */
	record Inst(String model, double x, double y, double z, float yaw, float scale, int minQuality, int group, double cx, double cy, double cz,
				double radius, int flags) {
	}

	static final Inst[] INSTANCES = {
			// the gate group: the gate, its cloud stairs and the bell tower
			new Inst("gerbang", 0, 0, 0, 0, 1, 0, G_GATE, 0, 50, -5, 82, DOORS | ALWAYS),
			new Inst("tangga_awan", 0, 0, 0, 0, 1, 0, G_GATE, 0, -22, -75, 56, ALWAYS),
			new Inst("menara_lonceng", BELL_X, 0, 0, 0, 1, 0, G_GATE, BELL_X, 8, 0, 30, ALWAYS),
			// the hall and its court
			new Inst("aula", 0, 0, HALL_Z, 0, 1, 0, G_HALL, 0, 30, HALL_Z + 48, 96, ALWAYS),
			new Inst("halaman", 0, 0, 0, 0, 1, 0, G_HALL, 0, -10, 208, 200, ALWAYS),
			// islands, bridges and the dragon pillars
			new Inst("pulau_l", -160, 0, 140, 0, 1, 0, G_ISLANDS, -160, -25, 140, 72, 0),
			new Inst("pulau_m", 150, 0, 250, 0, 1, 0, G_ISLANDS, 150, -20, 250, 54, 0),
			new Inst("pulau_s", -112, 0, 330, 0, 1, 1, G_ISLANDS, -112, -10, 330, 32, 0),
			new Inst("pulau_s", 120, 30, 40, 50, 1, 2, G_ISLANDS, 120, 20, 40, 32, 0),
			new Inst("pulau_m", -230, 14, 420, 70, 1, 2, G_ISLANDS, -230, -6, 420, 54, 0),
			new Inst("pulau_l", 240, -30, 470, 20, 1, 2, G_ISLANDS, 240, -55, 470, 72, 0),
			new Inst("jembatan", -40, 0, 140, 180, 1, 1, G_ISLANDS, -80, 4, 140, 42, 0),
			new Inst("jembatan", 40, 0, 250, 0, 1, 1, G_ISLANDS, 80, 4, 250, 42, 0),
			new Inst("jembatan", -40, 0, 330, 180, 0.675F, 2, G_ISLANDS, -67, 3, 330, 30, 0),
			new Inst("tiang_naga", -58, 0, 80, 0, 1, 1, G_ISLANDS, -58, 28, 80, 72, 0),
			new Inst("tiang_naga", 58, 0, 80, 180, 1, 1, G_ISLANDS, 58, 28, 80, 72, 0),
			new Inst("tiang_naga", -58, 0, 260, 90, 1, 1, G_ISLANDS, -58, 28, 260, 72, 0),
			new Inst("tiang_naga", 58, 0, 260, 270, 1, 1, G_ISLANDS, 58, 28, 260, 72, 0),
			new Inst("tiang_naga", -58, 0, 170, 45, 1, 2, G_ISLANDS, -58, 28, 170, 72, 0),
			new Inst("tiang_naga", 58, 0, 170, 225, 1, 2, G_ISLANDS, 58, 28, 170, 72, 0),
			new Inst("tiang_naga", -58, 0, 350, 135, 1, 2, G_ISLANDS, -58, 28, 350, 72, 0),
			new Inst("tiang_naga", 58, 0, 350, 315, 1, 2, G_ISLANDS, 58, 28, 350, 72, 0),
			// the Four Heavenly Kings flank the stair; celestial soldiers line the court
			new Inst("pengawal_raja", -24, 0, -11, 0, 1, 1, G_GUARDS, -24, 8, -11, 10, 0),
			new Inst("pengawal_raja", 24, 0, -11, 0, 1, 1, G_GUARDS, 24, 8, -11, 10, 0),
			new Inst("pengawal_raja", -46, 0, -11, 0, 1, 1, G_GUARDS, -46, 8, -11, 10, HALVE),
			new Inst("pengawal_raja", 46, 0, -11, 0, 1, 1, G_GUARDS, 46, 8, -11, 10, HALVE),
			new Inst("pengawal", -22, 0, 70, 270, 1, 2, G_GUARDS, -22, 3, 70, 5, NO_PACK),
			new Inst("pengawal", 22, 0, 70, 90, 1, 2, G_GUARDS, 22, 3, 70, 5, NO_PACK | HALVE),
			new Inst("pengawal", -22, 0, 130, 270, 1, 2, G_GUARDS, -22, 3, 130, 5, NO_PACK | HALVE),
			new Inst("pengawal", 22, 0, 130, 90, 1, 2, G_GUARDS, 22, 3, 130, 5, NO_PACK),
			new Inst("pengawal", -22, 0, 190, 270, 1, 2, G_GUARDS, -22, 3, 190, 5, NO_PACK),
			new Inst("pengawal", 22, 0, 190, 90, 1, 2, G_GUARDS, 22, 3, 190, 5, NO_PACK | HALVE),
			new Inst("pengawal", -22, 0, 250, 270, 1, 2, G_GUARDS, -22, 3, 250, 5, NO_PACK | HALVE),
			new Inst("pengawal", 22, 0, 250, 90, 1, 2, G_GUARDS, 22, 3, 250, 5, NO_PACK),
			new Inst("pengawal", -22, 0, 310, 270, 1, 2, G_GUARDS, -22, 3, 310, 5, NO_PACK),
			new Inst("pengawal", 22, 0, 310, 90, 1, 2, G_GUARDS, 22, 3, 310, 5, NO_PACK | HALVE),
			new Inst("pengawal", -22, 0, 370, 270, 1, 2, G_GUARDS, -22, 3, 370, 5, NO_PACK | HALVE),
			new Inst("pengawal", 22, 0, 370, 90, 1, 2, G_GUARDS, 22, 3, 370, 5, NO_PACK),
			// pavilions and pagodas on the islands, lanterns along the court
			new Inst("paviliun", -160, 0, 140, 22, 1, 1, G_PAVILION, -160, 8, 140, 14, 0),
			new Inst("paviliun", 150, 0, 250, 0, 1, 1, G_PAVILION, 150, 8, 250, 14, 0),
			new Inst("paviliun", -112, 0, 330, 10, 1, 2, G_PAVILION, -112, 8, 330, 14, 0),
			new Inst("paviliun", 120, 30, 40, 40, 1, 2, G_PAVILION, 120, 38, 40, 14, 0),
			new Inst("pagoda", -230, 14, 420, 0, 1, 2, G_PAVILION, -230, 50, 420, 40, 0),
			new Inst("pagoda", 240, -30, 470, 45, 1, 2, G_PAVILION, 240, 6, 470, 40, 0),
			new Inst("lentera", -32, 0, 60, 0, 1, 1, G_PAVILION, -32, 5, 60, 6, 0),
			new Inst("lentera", 32, 0, 60, 0, 1, 1, G_PAVILION, 32, 5, 60, 6, 0),
			new Inst("lentera", -32, 0, 140, 0, 1, 1, G_PAVILION, -32, 5, 140, 6, 0),
			new Inst("lentera", 32, 0, 140, 0, 1, 1, G_PAVILION, 32, 5, 140, 6, 0),
			new Inst("lentera", -32, 0, 220, 0, 1, 1, G_PAVILION, -32, 5, 220, 6, 0),
			new Inst("lentera", 32, 0, 220, 0, 1, 1, G_PAVILION, 32, 5, 220, 6, 0),
			new Inst("lentera", -32, 0, 300, 0, 1, 1, G_PAVILION, -32, 5, 300, 6, 0),
			new Inst("lentera", 32, 0, 300, 0, 1, 1, G_PAVILION, 32, 5, 300, 6, 0),
			new Inst("lentera", -32, 0, 100, 0, 1, 2, G_PAVILION, -32, 5, 100, 6, 0),
			new Inst("lentera", 32, 0, 100, 0, 1, 2, G_PAVILION, 32, 5, 100, 6, 0),
			new Inst("lentera", -32, 0, 180, 0, 1, 2, G_PAVILION, -32, 5, 180, 6, 0),
			new Inst("lentera", 32, 0, 180, 0, 1, 2, G_PAVILION, 32, 5, 180, 6, 0),
			new Inst("lentera", -32, 0, 260, 0, 1, 2, G_PAVILION, -32, 5, 260, 6, 0),
			new Inst("lentera", 32, 0, 260, 0, 1, 2, G_PAVILION, 32, 5, 260, 6, 0),
			new Inst("lentera", -32, 0, 340, 0, 1, 2, G_PAVILION, -32, 5, 340, 6, 0),
			new Inst("lentera", 32, 0, 340, 0, 1, 2, G_PAVILION, 32, 5, 340, 6, 0),
	};

	/** Waterfalls of light (the Heavenly River) pouring off island edges: x, top y, z; the first two from Normal. */
	static final double[][] FALLS = {
			{-200.0D, -3.0D, 140.0D}, {180.0D, -3.0D, 250.0D}, {-262.0D, 11.0D, 420.0D}, {282.0D, -33.0D, 470.0D},
	};

	private static Identifier tex(String name) {
		return Kultivator.id("textures/misc/kosmik/surga/" + name + ".png");
	}

	private SurgaLayout() {
	}
}
