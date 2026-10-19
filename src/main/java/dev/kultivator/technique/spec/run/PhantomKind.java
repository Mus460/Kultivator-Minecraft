package dev.kultivator.technique.spec.run;

import org.jetbrains.annotations.Nullable;

/**
 * What a synced phantom looks like on the client (DESIGN 27.4, {@code Payloads.PhantomSync}): the server keeps phantoms
 * virtual (no entity); every client draws each kind with its own model or a tinted player copy. The ordinal is sent, so
 * never reorder; new kinds go at the end.
 */
public enum PhantomKind {
	FLAME_CART,
	FIERCE_CORPSE,
	ICE_PHOENIX,
	THUNDER_DEITY,
	COSMIC_AVATAR,
	ICE_EMPEROR,
	SNOW_EMPRESS,
	ICE_SCORPION,
	BLOOD_CLONE,
	HAIR_CLONE,
	GENERIC_CLONE;

	/** Movement modes of a synced phantom ({@code Entry.mode}). */
	public static final byte MODE_FOLLOW = 0;
	public static final byte MODE_ANCHOR = 1;
	public static final byte MODE_CLING = 2;

	private static final PhantomKind[] VALUES = values();

	public static PhantomKind byOrdinal(int ordinal) {
		return ordinal < 0 || ordinal >= VALUES.length ? GENERIC_CLONE : VALUES[ordinal];
	}

	/** The kind of a DSL summon kind id ({@code Payload.Summon.kind}, e.g. "flame_cart", "fierce_corpse", "clone"). */
	public static PhantomKind ofSummon(@Nullable String kind) {
		if (kind == null) {
			return GENERIC_CLONE;
		}
		String k = kind.toLowerCase(java.util.Locale.ROOT);
		if (k.contains("cart") || k.contains("samadhi")) {
			return FLAME_CART;
		}
		if (k.contains("corpse") || k.contains("zombie") || k.contains("jiangshi")) {
			return FIERCE_CORPSE;
		}
		if (k.contains("scorpion")) {
			return ICE_SCORPION;
		}
		if (k.contains("empress")) {
			return SNOW_EMPRESS;
		}
		if (k.contains("emperor")) {
			return ICE_EMPEROR;
		}
		if (k.contains("phoenix")) {
			return ICE_PHOENIX;
		}
		if (k.contains("thunder") || k.contains("deity") || k.contains("venerable")) {
			return THUNDER_DEITY;
		}
		if (k.contains("cosmic") || k.contains("avatar") || k.contains("giant")) {
			return COSMIC_AVATAR;
		}
		if (k.contains("blood")) {
			return BLOOD_CLONE;
		}
		if (k.contains("hair")) {
			return HAIR_CLONE;
		}
		return GENERIC_CLONE;
	}
}
