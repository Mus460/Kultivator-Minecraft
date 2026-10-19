package dev.kultivator.alchemy;

import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.Nullable;

/**
 * The pills refined in an alchemy furnace (DESIGN section 9, phase 4). Each one is an item {@code kultivator:<id>}
 * ({@link PillItem}); the numbers here are for Mid quality and are multiplied by {@link PillQuality#multiplier()}.
 */
public enum PillKind {
	/** Pil Pengumpul Qi: cultivation XP worth 15% of the bar, at most {@link #QI_GATHERING_MAX_XP}. */
	QI_GATHERING("qi_gathering_pill", 0x7FE0FF, 6, 1, Rarity.COMMON),
	/** Pil Pemulih Qi: restores 50% of the qi pool. */
	QI_RECOVERY("qi_recovery_pill", 0x4A8CFF, 5, 1, Rarity.COMMON),
	/** Pil Pembangunan Fondasi: next breakthrough +15% (full strength at Qi Refining, half otherwise). */
	FOUNDATION("foundation_pill", 0xE0B060, 12, 2, Rarity.UNCOMMON),
	/** Pil Inti Emas: next tribulation strikes x0.85. */
	GOLDEN_CORE("golden_core_pill", 0xFFC23C, 15, 2, Rarity.RARE),
	/** Pil Penenang Hati: heart demon pressure -50, Qi Deviation 40% shorter. */
	HEART_CALMING("heart_calming_pill", 0xF7A8C8, 8, 2, Rarity.UNCOMMON),
	/** Pil Penyembuh: heals 60% of max health, halves a Qi Deviation (Perfect cures it). */
	HEALING("healing_pill", 0x6CFF8A, 7, 1, Rarity.COMMON),
	/** Pil Penangkal Petir: next tribulation strikes x0.75. */
	TRIBULATION_WARDING("tribulation_warding_pill", 0x9A7CFF, 15, 2, Rarity.RARE),
	/** Pil Pemelihara Jiwa: cultivation XP worth 30% of the bar (at most {@link #SOUL_NURTURING_MAX_XP}), Nascent Soul and above only. */
	SOUL_NURTURING("soul_nurturing_pill", 0xD9B8FF, 22, 3, Rarity.RARE),
	/** Pil Dewa: cultivation XP worth a whole bar (x quality only), full qi, purges all pill toxicity. */
	IMMORTAL("immortal_pill", 0xFFF0B0, 0, 3, Rarity.EPIC),
	/** Pil Wawasan Dao (phase 7): +3 x quality comprehension to the chosen Dao, never above 60, at most 5 per realm. */
	DAO_INSIGHT("dao_insight_pill", 0x3FD0C8, 18, 2, Rarity.RARE),
	/** Pil Penawar Racun (phase 7): toxicity −35 x quality and the harmful effects cleared. */
	TOXIN_PURGING("toxin_purging_pill", 0xB8F5A0, 0, 1, Rarity.UNCOMMON),
	/** Pil Pencerahan (phase 7): enlightenment chance x2 for 10 minutes. */
	ENLIGHTENMENT("enlightenment_pill", 0xFFE070, 10, 2, Rarity.RARE),
	/** Pil Penempa Tulang (phase 7): +1 max health for good (Perfect +2), at most +10 in total. */
	BONE_TEMPERING("bone_tempering_pill", 0xE8DCC0, 12, 2, Rarity.RARE),
	/** Pil Mata Terang (phase 8, DESIGN 25.3.9): for 2 minutes the secret realm's phantom exits show and phantoms are outlined. */
	MATA_TERANG("mata_terang_pill", 0xB8E8FF, 4, 1, Rarity.UNCOMMON);

	public static final double QI_GATHERING_XP = 0.15D;
	/** Cap of the Qi Gathering XP before quality (about 15% of a Golden Core Peak bar), so late realms cannot be pill-farmed. */
	public static final double QI_GATHERING_MAX_XP = 450.0D;
	public static final double QI_RECOVERY_QI = 0.5D;
	public static final double FOUNDATION_BONUS = 0.15D;
	/** Outside Qi Refining the foundation pill works at this share. */
	public static final double FOUNDATION_OFF_REALM = 0.5D;
	/** Outside Qi Refining, Foundation pills never raise the next breakthrough bonus above this. */
	public static final double FOUNDATION_OFF_REALM_MAX = 0.15D;
	public static final double GOLDEN_CORE_REDUCTION = 0.15D;
	public static final double HEART_PRESSURE = 50.0D;
	public static final double HEART_DEVIATION = 0.4D;
	public static final double HEALING_HEALTH = 0.6D;
	public static final double HEALING_DEVIATION = 0.5D;
	public static final double WARDING_REDUCTION = 0.25D;
	public static final double SOUL_NURTURING_XP = 0.30D;
	/** Cap of the Soul Nurturing XP before quality (30% of a Deity Transformation Early bar). */
	public static final double SOUL_NURTURING_MAX_XP = 3600.0D;
	public static final double IMMORTAL_XP = 1.0D;
	/** Dao Insight Pill (DESIGN 19.3.7): comprehension x quality, the ceiling it never passes, pills per realm. */
	public static final double DAO_INSIGHT_COMPREHENSION = 3.0D;
	public static final double DAO_INSIGHT_MAX = 60.0D;
	public static final int DAO_INSIGHT_PER_REALM = 5;
	/** Toxin Purging Pill: toxicity removed x quality. */
	public static final double TOXIN_PURGE = 35.0D;
	/** Enlightenment Pill: the chance factor and how long it lasts. */
	public static final double ENLIGHTENMENT_FACTOR = 2.0D;
	public static final int ENLIGHTENMENT_TICKS = 12000;
	/** Bone Tempering Pill: max health per pill (Perfect twice) and the total cap. */
	public static final int BONE_HEALTH = 1;
	public static final int BONE_HEALTH_PERFECT = 2;
	public static final int BONE_HEALTH_CAP = 10;
	/** Pil Mata Terang: how long the clear sight lasts (ticks, quality does not change it). */
	public static final int MATA_TERANG_TICKS = 2400;

	private static final PillKind[] ALL = values();

	private final String id;
	private final int color;
	private final int toxin;
	private final int strength;
	private final Rarity rarity;

	PillKind(String id, int color, int toxin, int strength, Rarity rarity) {
		this.id = id;
		this.color = color;
		this.toxin = toxin;
		this.strength = strength;
		this.rarity = rarity;
	}

	/** Item id path, e.g. {@code qi_gathering_pill}. */
	public String id() {
		return id;
	}

	/** Id without the {@code _pill} suffix, used by commands. */
	public String shortId() {
		return id.endsWith("_pill") ? id.substring(0, id.length() - "_pill".length()) : id;
	}

	/** RGB of the pill (effects, action bar). */
	public int color() {
		return color;
	}

	/** Base toxin (Mid quality); see {@link #toxin(PillQuality)}. */
	public int toxin() {
		return toxin;
	}

	/** Toxin of a pill of this quality. */
	public double toxin(PillQuality quality) {
		return toxin * quality.toxinFactor();
	}

	/** 1..3, how grand the swallowing effect looks. */
	public int strength() {
		return strength;
	}

	public Rarity rarity() {
		return rarity;
	}

	/** The pill gives cultivation XP (so pill toxicity weakens it). */
	public boolean givesXp() {
		return this == QI_GATHERING || this == SOUL_NURTURING || this == IMMORTAL;
	}

	/** One of the four phase-7 pills (DESIGN 19.3.7): disciples refuse them for now, their tooltips carry a source line. */
	public boolean phase7() {
		return this == DAO_INSIGHT || this == TOXIN_PURGING || this == ENLIGHTENMENT || this == BONE_TEMPERING;
	}

	/** Null when unknown; accepts the item id or the short id. */
	public static @Nullable PillKind byId(String id) {
		for (PillKind kind : ALL) {
			if (kind.id.equals(id) || kind.shortId().equals(id)) {
				return kind;
			}
		}
		return null;
	}
}
