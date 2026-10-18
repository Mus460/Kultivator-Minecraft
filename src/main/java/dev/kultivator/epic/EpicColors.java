package dev.kultivator.epic;

/**
 * Common colours of the epic moments (DESIGN 23.13 A2, A3, 23.9.2), RGB without alpha. Written in full by the spine so
 * P7-tempur and both 7b modules share them. The hues are Presentasi; the seven-colour Immortal Tribulation Lightning
 * itself is [D] I Shall Seal the Heavens ch. 969-970. The nine-colour finale is Presentasi and never named in text.
 */
public final class EpicColors {

	/** The seven Kenaikan Abadi strike colours, cycled by strike: red, orange, yellow, green, cyan, blue, violet. */
	public static final int[] IMMORTAL_STRIKE = {0xFF3B3B, 0xFF8A1A, 0xFFD54A, 0x3BDB7A, 0x3BE0E0, 0x4A7BFF, 0xA65BFF};

	/** The nine converging bolts of the Dewa Abadi finale: the seven, then white and abyss violet. */
	public static final int[] NINE = {0xFF3B3B, 0xFF8A1A, 0xFFD54A, 0x3BDB7A, 0x3BE0E0, 0x4A7BFF, 0xA65BFF, 0xFFFFFF, 0x2A0F45};

	/** Bolt colour per tribulation variant (Overworld, Nether, End; the {@code Tribulation.VARIANT_*} values). */
	public static final int BOLT_OVERWORLD = 0xB57CFF;
	public static final int BOLT_NETHER = 0xFF7A2A;
	public static final int BOLT_END = 0xC08BFF;
	/** The ember core of Nether bolts and the white core of the others. */
	public static final int CORE_NETHER = 0xFFD04A;
	public static final int CORE_WHITE = 0xF8F4FF;
	/** Calamity lightning: silver-white with a gold core (E2). */
	public static final int BOLT_CALAMITY = 0xE8EEFF;
	public static final int CORE_GOLD = 0xFFD54A;

	// ---- sky grades (23.9.2): sky, fog, cloud
	public static final int TRIB_SKY = 0x1B1A24;
	public static final int TRIB_FOG = 0x2A2833;
	public static final int TRIB_CLOUD = 0x2B2933;
	public static final int NETHER_FOG = 0x3A0E05;
	public static final int VOID_FOG = 0x1A0B26;
	public static final int GOLD_SKY = 0xF6D27A;
	public static final int GOLD_FOG = 0xFFE3A0;
	public static final int GOLD_CLOUD = 0xFFF2C0;
	public static final int WHITE_SKY = 0xFFFFFF;
	public static final int WHITE_FOG = 0xFFF8E8;
	public static final int RED_SKY = 0x6A0D0D;
	public static final int RED_FOG = 0x3A0505;
	public static final int RED_CLOUD = 0x4A0A0A;
	public static final int ASH_SKY = 0x4A4A4A;
	public static final int ASH_FOG = 0x2A2A2A;
	public static final int ASH_RED_SKY = 0x4A2A2A;
	public static final int STARS_SKY = 0x0E1430;
	public static final int STARS_FOG = 0x141A33;
	public static final int JADE_TINT = 0x8FE3C8;
	public static final int JADE_CLOUD = 0xE8FFF6;
	public static final int ICE_TINT = 0xBFE8FF;

	/** The colour of Kenaikan Abadi strike {@code index} (1-based, as {@code Tribulation.Listener.onStrike} counts). */
	public static int immortalStrike(int index) {
		return IMMORTAL_STRIKE[Math.floorMod(index - 1, IMMORTAL_STRIKE.length)];
	}

	/** The bolt colour of a tribulation variant (0 Overworld, 1 Nether, 2 End; anything else is Overworld). */
	public static int variantBolt(int variant) {
		return switch (variant) {
			case 1 -> BOLT_NETHER;
			case 2 -> BOLT_END;
			default -> BOLT_OVERWORLD;
		};
	}

	private EpicColors() {
	}
}
