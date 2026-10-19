package dev.kultivator.disciple.power;

/**
 * Numbers shared by the server and the client side of module C (DESIGN 20.3.4, 20.3.5, 20.3.16, 20.3.17): the ids of
 * talents, physiques and special candidates as module A stores them in {@code extra.talent} (table order of DESIGN
 * 20.3.4 / 20.3.5, 1-based; talents are bits), the Heavenly Flame kinds, the natal treasure forms, the synced power bits
 * ({@code DiscipleEntity.syncedPower()}, bits 0-15) and the sub-ids of {@code FxType.DISCIPLE_POWER} (sent as {@code a}).
 */
public final class PowerIds {

	// ================================================================== soul colours (0 Merah .. 6 Nila)

	public static final int COLOUR_RED = 0;
	public static final int COLOUR_ORANGE = 1;
	public static final int COLOUR_YELLOW = 2;
	public static final int COLOUR_GREEN = 3;
	public static final int COLOUR_CYAN = 4;
	public static final int COLOUR_AZURE = 5;
	public static final int COLOUR_INDIGO = 6;
	public static final String[] COLOUR_IDS = {"red", "orange", "yellow", "green", "cyan", "azure", "indigo"};
	/** RGB of each soul colour (TDG's seven soul realms). */
	public static final int[] COLOUR_RGB = {0xE8473A, 0xF29B38, 0xF2D84A, 0x5FD066, 0x4FE0E6, 0x5AA8FF, 0x6A5AF0};
	/** The XP factor of each colour (DESIGN 20.3.5, replaces the root multiplier). */
	public static final double[] COLOUR_FACTOR = {0.6D, 0.85D, 1.0D, 1.1D, 1.3D, 1.5D, 1.7D};

	// ================================================================== talents (bits of extra.talent.talents)

	public static final int TALENT_PERSEVERING = 1;
	public static final int TALENT_ANCIENT_GOD = 1 << 1;
	public static final int TALENT_PILL_GIFT = 1 << 2;
	public static final int TALENT_SPIRIT_ENHANCER = 1 << 3;
	public static final int TALENT_FEAR_OF_DEATH = 1 << 4;
	public static final int TALENT_SWORD_SENSE = 1 << 5;
	public static final String[] TALENT_IDS = {"persevering", "ancient_god", "pill_gift", "spirit_enhancer", "fear_of_death", "sword_sense"};

	// ================================================================== physiques (extra.talent.physique, 0 none)

	public static final int PHYSIQUE_POISON = 1;
	public static final int PHYSIQUE_TAIXU = 2;
	public static final int PHYSIQUE_DOU_DI = 3;
	public static final int PHYSIQUE_GOLDEN_BODY = 4;
	public static final int PHYSIQUE_DEVOURING = 5;
	public static final int PHYSIQUE_SUPREME_BONE = 6;
	public static final int PHYSIQUE_DOUBLE_PUPILS = 7;
	public static final int PHYSIQUE_HERETIC = 8;
	public static final int PHYSIQUE_ATTRIBUTELESS = 9;
	/** Index = physique id; [0] is "none". */
	public static final String[] PHYSIQUE_IDS = {"none", "poison", "taixu", "dou_di", "golden_body", "devouring", "supreme_bone",
			"double_pupils", "heretic", "attributeless"};
	public static final int[] PHYSIQUE_RGB = {0xA8B5B0, 0x9D4FD8, 0xB05AFF, 0xFFB43A, 0xFFD54A, 0x7A2E9A, 0xF2F0E0, 0x8FE3FF,
			0xD8263A, 0xE8E8E8};

	// ================================================================== special candidates (extra.talent.special.type)

	public static final int SPECIAL_BEGGAR = 1;
	public static final int SPECIAL_REGRESSOR = 2;
	public static final int SPECIAL_BLOCKED_DANTIAN = 3;
	public static final String[] SPECIAL_IDS = {"none", "beggar", "regressor", "blocked_dantian"};

	// ================================================================== Heavenly Flames

	public static final int FLAME_GREEN_LOTUS = 0;
	public static final int FLAME_BONE_CHILLING = 1;
	public static final int FLAME_SEA_HEART = 2;
	public static final int FLAME_FALLEN_HEART = 3;
	public static final int FLAME_PURE_LOTUS = 4;
	public static final String[] FLAME_IDS = {"green_lotus", "bone_chilling", "sea_heart", "fallen_heart", "pure_lotus"};
	public static final int[] FLAME_RGB = {0x4FE07A, 0xBFEFFF, 0x3D8BFF, 0xFF7A2A, 0xC24BFF};

	// ================================================================== natal treasure forms

	public static final int NATAL_SWORD = 0;
	public static final int NATAL_PARASOL = 1;
	public static final int NATAL_WOK = 2;
	public static final int NATAL_BELL = 3;
	public static final String[] NATAL_IDS = {"sword", "parasol", "wok", "bell"};

	// ================================================================== synced power bits (POWER_MASK_C)

	/** Bits 0-4: one per Heavenly Flame kind absorbed. */
	public static final int BITS_FLAMES = 0x1F;
	public static final int BIT_DRAGON = 1 << 5;
	public static final int BIT_POISON = 1 << 6;
	public static final int BIT_GOLD = 1 << 7;
	/** Gate burst (Heretic God Veins) or poison rampage: the client picks the look by physique. */
	public static final int BIT_RAMPAGE = 1 << 8;
	/** Bits 9-10: natal tier 0 none, 1 plain, 2 silver (fold 3), 3 gold (fold 9). */
	public static final int NATAL_TIER_SHIFT = 9;
	/** Bits 11-12: natal form. */
	public static final int NATAL_FORM_SHIFT = 11;
	public static final int BIT_NATAL_ORBIT = 1 << 13;
	public static final int BIT_PURPLE_MIST = 1 << 14;
	public static final int BIT_LOTUS = 1 << 15;

	public static int natalTier(int bits) {
		return bits >> NATAL_TIER_SHIFT & 0x3;
	}

	public static int natalForm(int bits) {
		return bits >> NATAL_FORM_SHIFT & 0x3;
	}

	// ================================================================== FxType.DISCIPLE_POWER sub-ids (a)

	/** pos centre, target (swords, ticks, 0), b swords, f radius. */
	public static final int FX_ULT_SWORD = 1;
	/**
	 * pos victim, target sky start, b 1 for a chain bolt; or b {@link #BOLT_STORM}: the whole cast in one packet (pos the
	 * caster, f the radius), the client draws the bolts on the nearest {@link #ULT_BOLTS} monsters and one chain each.
	 */
	public static final int FX_ULT_BOLT = 2;
	public static final int BOLT_STORM = 2;
	/** Petir Pengusir Kejahatan: bolts per cast and the chain's reach (shared by the server and the client drawing). */
	public static final int ULT_BOLTS = 6;
	public static final double ULT_CHAIN_RANGE = 6.0D;
	/** pos centre, b ticks, f radius. */
	public static final int FX_ULT_PRISON = 3;
	/** pos impact, f shockwave radius. */
	public static final int FX_ULT_FIST = 4;
	/** pos caster, b entity id, f charge ticks. */
	public static final int FX_LOTUS_CHARGE = 5;
	/** pos impact, target thrower, b flames, f radius. */
	public static final int FX_LOTUS_BLAST = 6;
	/** pos centre, f radius. */
	public static final int FX_ULT_LIFE = 7;
	/** pos caster, b Dao ordinal: the flash of any signature ultimate. */
	public static final int FX_ULT_CAST = 8;
	/** pos from, target to, b 1 at full power. */
	public static final int FX_KUNPENG = 9;
	/** pos from, target to. */
	public static final int FX_DASH = 10;
	/** pos centre, b ticks, f radius. */
	public static final int FX_POISON_CLOUD = 11;
	/** pos caster, b entity id. */
	public static final int FX_DRAGON = 12;
	/** pos mouth, target end of the cone. */
	public static final int FX_DRAGON_BREATH = 13;
	/** pos disciple, b gate number 1-7. */
	public static final int FX_GATE_OPEN = 14;
	/** pos disciple, b gates. */
	public static final int FX_GATE_BURST = 15;
	/** pos disciple, b reveal kind (1 beggar, 2 heretic, 3 Dou Di). */
	public static final int FX_REVEAL = 16;
	/** pos above the monster, b its entity id, f seconds (sent to the master only). */
	public static final int FX_EYE_MARK = 17;
	/** pos disciple. */
	public static final int FX_DODGE = 18;
	/** pos centre, b petals, f radius. */
	public static final int FX_PETALS = 19;
	/** pos disciple, b flame kind, f seconds. */
	public static final int FX_FLAME_RITUAL = 20;
	/** pos disciple, b flame kind, f 1 success / 0 failure. */
	public static final int FX_FLAME_RESULT = 21;
	/** pos disciple, b form. */
	public static final int FX_NATAL_REFINE = 22;
	/** pos disciple, b folds now, f 1 success / 0 failure. */
	public static final int FX_NATAL_FOLD = 23;
	/** pos disciple, f radius. */
	public static final int FX_PAYUNG = 24;
	/** pos disciple, f radius. */
	public static final int FX_LONCENG = 25;
	/** pos body, target stele. */
	public static final int FX_SOUL_FLIGHT = 26;
	/** pos where it re-forms. */
	public static final int FX_SOUL_RETURN = 27;
	/** pos disciple: Undying Blood refuses death. */
	public static final int FX_UNDYING = 28;
	/** pos disciple, b codex stage. */
	public static final int FX_CODEX_STAGE = 29;
	/** pos victim, target disciple. */
	public static final int FX_DEVOUR = 30;
	/** pos disciple, b new colour. */
	public static final int FX_COLOUR_UP = 31;
	/** pos master, target disciple. */
	public static final int FX_FUSION = 32;
	/** pos disciple, b physique id: a rare physique stirs (first sight, awakening). */
	public static final int FX_PHYSIQUE = 33;

	// ------------------------------------------------------------------ polish pass (DESIGN 27.6)

	/**
	 * The Seven Fairies link up (edge-triggered once when the seventh joins): one packet per fairy, pos = the fairy, target
	 * = the centre of the seven, b = its entity id, f = its index 0-6 (the client links the seven ids it got within 2 s).
	 */
	public static final int FX_FAIRIES = 34;
	/** The fire ultimate lotus leaves the hands: pos = the hands, target = the impact, b = flight ticks, f = radius. */
	public static final int FX_LOTUS_RELEASE = 35;
	/** A Kunpeng Wings dash: pos = from, target = to, b = ticks the move takes, f = 1 at full power. */
	public static final int FX_KUNPENG_DASH = 36;
	/**
	 * A Divine Clone starts fading: the same event as {@code PathFxIds.CLONE} with b = 1 (f = fade ticks), named here for
	 * the power code; the clone body is removed {@code DivineClones.FADE_TICKS} later.
	 */
	public static final int FX_CLONE_FADE = 37;
	/** A damage pulse of the sword formation: pos = the struck foe's middle, target = the dome centre, b = its entity id. */
	public static final int FX_ULT_SWORD_DIVE = 38;

	/**
	 * Kunpeng Wings are spread (the dash window is open). A bit of {@code DiscipleEntity.syncedFlags()} (the power bits are
	 * full): read it as {@code (disciple.syncedFlags() & PowerIds.BIT_KUNPENG) != 0}. Same value as
	 * {@code DiscipleEntity.FLAG_KUNPENG}.
	 */
	public static final int BIT_KUNPENG = 1 << 4;
	/** The Seven Fairies are linked (all seven near each other). A bit of {@code syncedFlags()}, = {@code DiscipleEntity.FLAG_FAIRIES}. */
	public static final int BIT_FAIRIES = 1 << 5;

	private PowerIds() {
	}
}
