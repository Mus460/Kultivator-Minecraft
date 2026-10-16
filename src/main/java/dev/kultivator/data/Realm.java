package dev.kultivator.data;

import net.minecraft.network.chat.Component;

/** The great realms of cultivation, in order. The ordinal is saved, so never reorder. */
public enum Realm {
	MORTAL("mortal", 1, 1, 0xB0B0B0, 60),
	QI_REFINING("qi_refining", 1, 4, 0xE6F4FF, 120),
	FOUNDATION("foundation", 1, 4, 0x7FE0FF, 450),
	GOLDEN_CORE("golden_core", 1, 4, 0xFFD54A, 1500),
	NASCENT_SOUL("nascent_soul", 1, 4, 0xB98CFF, 4500),
	DEITY_TRANSFORMATION("deity_transformation", 1, 4, 0xFF7AD9, 12000),
	DAO_UNION("dao_union", 1, 4, 0x7CFFB2, 30000),
	IMMORTAL_ASCENSION("immortal_ascension", 9, 4, 0xFFFFFF, 60000),
	TRUE_IMMORTAL("true_immortal", 1, 4, 0xFFE9A8, 250000);

	private final String id;
	private final int layers;
	private final int stages;
	private final int color;
	private final double baseXp;

	Realm(String id, int layers, int stages, int color, double baseXp) {
		this.id = id;
		this.layers = layers;
		this.stages = stages;
		this.color = color;
		this.baseXp = baseXp;
	}

	public String id() {
		return id;
	}

	/** 9 for Immortal Ascension (layers 1-9), 1 otherwise. */
	public int layers() {
		return layers;
	}

	/** 1 for Mortal, 4 (Early, Middle, Late, Peak) otherwise. */
	public int stages() {
		return stages;
	}

	/** RGB colour of the realm (aura, HUD text). */
	public int color() {
		return color;
	}

	public double baseXp() {
		return baseXp;
	}

	public boolean isMortal() {
		return this == MORTAL;
	}

	public boolean isFinal() {
		return this == TRUE_IMMORTAL;
	}

	public Realm next() {
		return isFinal() ? this : values()[ordinal() + 1];
	}

	public Realm previous() {
		return this == MORTAL ? this : values()[ordinal() - 1];
	}

	public boolean atLeast(Realm other) {
		return ordinal() >= other.ordinal();
	}

	public Component displayName() {
		return Component.translatable("kultivator.realm." + id);
	}

	public static Realm byOrdinal(int ordinal) {
		Realm[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, ordinal))];
	}

	/** Null when unknown. */
	public static Realm byId(String id) {
		for (Realm realm : values()) {
			if (realm.id.equals(id)) {
				return realm;
			}
		}
		return null;
	}
}
