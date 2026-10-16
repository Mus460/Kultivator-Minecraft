package dev.kultivator.data;

import net.minecraft.network.chat.Component;

/** The Dao paths a cultivator can walk, chosen once at Golden Core. The ordinal is saved, so never reorder. */
public enum Dao {
	SWORD("sword", 0xD8E6FF, Element.METAL),
	FIRE("fire", 0xFF6A2A, Element.FIRE),
	ICE("ice", 0x9FF3FF, Element.ICE),
	LIGHTNING("lightning", 0xB57CFF, Element.LIGHTNING),
	BODY("body", 0xD9A05B, Element.EARTH),
	LIFE("life", 0x6CFF8A, Element.WOOD),
	SHADOW("shadow", 0x7A6AA0, Element.WIND),
	SPACE("space", 0x5A3FD0, Element.SPACE);

	public static final int COUNT = 8;

	private final String id;
	private final int color;
	private final Element affinity;

	Dao(String id, int color, Element affinity) {
		this.id = id;
		this.color = color;
		this.affinity = affinity;
	}

	public String id() {
		return id;
	}

	public int color() {
		return color;
	}

	/** The root element that speeds up comprehension of this Dao (Water also counts for Ice). */
	public Element affinity() {
		return affinity;
	}

	public boolean hasAffinity(int rootMask) {
		if ((rootMask & affinity.bit()) != 0) {
			return true;
		}
		return this == ICE && (rootMask & Element.WATER.bit()) != 0;
	}

	public Component displayName() {
		return Component.translatable("kultivator.dao." + id);
	}

	/** Null for -1 or an unknown index. */
	public static Dao byOrdinal(int ordinal) {
		return ordinal < 0 || ordinal >= COUNT ? null : values()[ordinal];
	}

	/** Null when unknown. */
	public static Dao byId(String id) {
		for (Dao dao : values()) {
			if (dao.id.equals(id)) {
				return dao;
			}
		}
		return null;
	}

	/** Comprehension levels: 0 Novice (0), 1 Minor (10), 2 Major (30), 3 Perfect (60), 4 Domain (90). */
	public static int comprehensionLevel(double comprehension) {
		if (comprehension >= 90.0D) {
			return 4;
		}
		if (comprehension >= 60.0D) {
			return 3;
		}
		if (comprehension >= 30.0D) {
			return 2;
		}
		return comprehension >= 10.0D ? 1 : 0;
	}

	public static Component comprehensionName(double comprehension) {
		return Component.translatable("kultivator.comprehension." + comprehensionLevel(comprehension));
	}
}
