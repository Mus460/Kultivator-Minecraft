package dev.kultivator.data;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Technique grades (DESIGN 22.7). Huang, Xuan, Di and Tian follow the Dou technique ranks of Battle Through the Heavens
 * (ch. 2); Abadi and Dewa above Tian are game design labels. Every number of the grade tables lives here, so the codex
 * shows exactly what the server uses. The ordinal is the grade tier 0-5; never reorder.
 */
public enum TechniqueGrade {
	HUANG("huang", 0xC8962E, 1.0D, 0.5D, new int[]{25, 80, 200, 450}, 200, 1, 0.10D, 0.0F),
	XUAN("xuan", 0x9B6CFF, 1.2D, 0.7D, new int[]{40, 120, 300, 700}, 400, 3, 0.15D, 0.10F),
	DI("di", 0xB0793A, 1.5D, 0.85D, new int[]{60, 180, 450, 1000}, 900, 5, 0.20D, 0.25F),
	TIAN("tian", 0x4FA8FF, 2.0D, 1.0D, new int[]{90, 270, 650, 1500}, 2000, 7, 0.30D, 0.45F),
	ABADI("abadi", 0xFFF1C0, 2.5D, 1.2D, new int[]{120, 360, 900, 2000}, 4000, 9, 0.40D, 0.70F),
	DEWA("dewa", 0xFF7AD9, 3.0D, 1.5D, new int[]{160, 480, 1200, 2700}, 0, 0, 0.50D, 1.0F);

	/** Sub-ranks Rendah / Menengah / Tinggi. */
	public static final int SUB_LOW = 0;
	public static final int SUB_MIDDLE = 1;
	public static final int SUB_HIGH = 2;

	private final String id;
	private final int ribbon;
	private final double comprehensionFactor;
	private final double bossFactor;
	private final int[] masteryThresholds;
	private final int pavilionPrice;
	private final int pavilionHallLevel;
	private final double teachQiShare;
	private final float defaultShake;

	TechniqueGrade(String id, int ribbon, double comprehensionFactor, double bossFactor, int[] masteryThresholds,
			int pavilionPrice, int pavilionHallLevel, double teachQiShare, float defaultShake) {
		this.id = id;
		this.ribbon = ribbon;
		this.comprehensionFactor = comprehensionFactor;
		this.bossFactor = bossFactor;
		this.masteryThresholds = masteryThresholds;
		this.pavilionPrice = pavilionPrice;
		this.pavilionHallLevel = pavilionHallLevel;
		this.teachQiShare = teachQiShare;
		this.defaultShake = defaultShake;
	}

	public String id() {
		return id;
	}

	/** Grade tier 0 (Kuning) to 5 (Dewa); compared with the caster grade tier for the realm gap (22.5.4). */
	public int tier() {
		return ordinal();
	}

	/** RGB of the grade ribbon on seal cards (ochre, violet, bronze, azure, white-gold, rainbow base). */
	public int ribbon() {
		return ribbon;
	}

	/** Comprehension per hitting cast is {@code 0.15 x} this (22.2.6). */
	public double comprehensionFactor() {
		return comprehensionFactor;
	}

	/** Boss cap grade factor (22.14.2). */
	public double bossFactor() {
		return bossFactor;
	}

	/** Cumulative mastery points for level {@code level} (1-4); 0 for level 0 or less (22.6). */
	public int masteryThreshold(int level) {
		if (level <= 0) {
			return 0;
		}
		return masteryThresholds[Math.min(4, level) - 1];
	}

	/** Pavilion slip price in contribution points (0 = never sold, Dewa). */
	public int pavilionPrice() {
		return pavilionPrice;
	}

	/** Hall level the pavilion needs to sell a slip of this grade (0 = never sold). */
	public int pavilionHallLevel() {
		return pavilionHallLevel;
	}

	/** Share of the master's max qi that teaching a disciple costs (22.13.2). */
	public double teachQiShare() {
		return teachQiShare;
	}

	/** Default camera shake of an ultimate of this grade (22.4.5). */
	public float defaultShake() {
		return defaultShake;
	}

	/** Translation key {@code kultivator.teknik.grade.<id>} ("Kuning", "Misterius"...). */
	public Component displayName() {
		return Component.translatable("kultivator.teknik.grade." + id);
	}

	/** "Rendah" / "Menengah" / "Tinggi" ({@code kultivator.teknik.sub.<0-2>}). */
	public static Component subName(int sub) {
		return Component.translatable("kultivator.teknik.sub." + Math.max(0, Math.min(2, sub)));
	}

	public static TechniqueGrade byTier(int tier) {
		TechniqueGrade[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, tier))];
	}

	public static @Nullable TechniqueGrade byId(String id) {
		for (TechniqueGrade grade : values()) {
			if (grade.id.equals(id)) {
				return grade;
			}
		}
		return null;
	}
}
