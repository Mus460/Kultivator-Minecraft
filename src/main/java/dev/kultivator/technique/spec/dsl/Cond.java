package dev.kultivator.technique.spec.dsl;

import java.util.List;

/**
 * A read-only condition (DESIGN 22.3.4 conditions and the feat conditions of 22.8.9). Used by step conditions
 * ({@code ? COND ×m}) and by feats. {@code a} and {@code b} are numeric parameters (a health share, a radius, strikes per
 * step and cap, an altitude), {@code key} names a biome id or {@code #tag}, a dimension id or a spiritual site kind,
 * and {@code parts} holds the children of ALL / ANY / NOT.
 */
public record Cond(Kind kind, double a, double b, String key, List<Cond> parts) {

	public enum Kind {
		/** Always true. */
		NONE,
		NIGHT, DAY, DAWN, SKY_OPEN, FULL_MOON, FULL_MOON_NIGHT,
		RAINING, THUNDERING,
		IN_WATER, IN_NETHER, IN_END,
		TARGET_BURNING, TARGET_FROZEN,
		/** Target health share below {@code a}. */
		TARGET_LOW_HP,
		TARGET_BOSS, TARGET_UNDEAD,
		/** Caster health share below {@code a}. */
		CASTER_LOW_HP,
		FULL_QI, HOLDING_SWORD,
		/** Within {@code a} blocks of the cast's anchor (field centre, flag...). */
		NEAR_ANCHOR,
		/** Lifetime survived tribulation strikes: +{@code a} per strike, at most {@code b} (as multiplier parts). */
		TRIB_STRIKES,
		/** A Divine Sense glow is active (Fiery Golden Eyes). */
		DIVINE_SENSE,
		/** Inside the caster's own field or domain. */
		IN_AREA,
		/** The target was hit at least {@code a} times earlier in this cast ("if all 8 hit one target"). */
		HIT_BEFORE,
		/** The target's max health is at least {@code a} × P (the gourd swallows only what is weaker). */
		TARGET_TOUGH,
		/** The caster is a player, not a disciple ("disciples learn it without the wings"). */
		PLAYER_CASTER,
		/** The caster reached realm ordinal {@code a}, and inside it layer index {@code b} (Kenaikan Abadi Tahap b + 1). */
		RANK_AT_LEAST,
		/** The receiver stands on natural ground (one read-only block check). */
		NATURAL_GROUND,
		/** The biome id or {@code #tag} in {@code key}. */
		BIOME,
		/** The dimension id in {@code key}. */
		DIMENSION,
		/** At or above y = {@code a}. */
		ALTITUDE,
		/** At a spiritual site of kind {@code key}. */
		SITE,
		ALL, ANY, NOT
	}

	public static final Cond NONE = of(Kind.NONE);
	public static final Cond NIGHT = of(Kind.NIGHT);
	public static final Cond DAY = of(Kind.DAY);
	public static final Cond DAWN = of(Kind.DAWN);
	public static final Cond SKY_OPEN = of(Kind.SKY_OPEN);
	public static final Cond FULL_MOON = of(Kind.FULL_MOON);
	public static final Cond FULL_MOON_NIGHT = of(Kind.FULL_MOON_NIGHT);
	public static final Cond RAINING = of(Kind.RAINING);
	public static final Cond THUNDERING = of(Kind.THUNDERING);
	public static final Cond IN_WATER = of(Kind.IN_WATER);
	public static final Cond IN_NETHER = of(Kind.IN_NETHER);
	public static final Cond IN_END = of(Kind.IN_END);
	public static final Cond TARGET_BURNING = of(Kind.TARGET_BURNING);
	public static final Cond TARGET_FROZEN = of(Kind.TARGET_FROZEN);
	public static final Cond TARGET_BOSS = of(Kind.TARGET_BOSS);
	public static final Cond TARGET_UNDEAD = of(Kind.TARGET_UNDEAD);
	public static final Cond FULL_QI = of(Kind.FULL_QI);
	public static final Cond HOLDING_SWORD = of(Kind.HOLDING_SWORD);
	public static final Cond DIVINE_SENSE = of(Kind.DIVINE_SENSE);
	public static final Cond IN_AREA = of(Kind.IN_AREA);
	public static final Cond NATURAL_GROUND = of(Kind.NATURAL_GROUND);
	public static final Cond PLAYER_CASTER = of(Kind.PLAYER_CASTER);

	/** The target was hit at least {@code times} times earlier in this cast. */
	public static Cond hitBefore(int times) {
		return new Cond(Kind.HIT_BEFORE, times, 0.0D, "", List.of());
	}

	/** The target's max health is at least {@code powerFactor} × P. */
	public static Cond targetTough(double powerFactor) {
		return new Cond(Kind.TARGET_TOUGH, powerFactor, 0.0D, "", List.of());
	}

	private static Cond of(Kind kind) {
		return new Cond(kind, 0.0D, 0.0D, "", List.of());
	}

	public static Cond targetLowHp(double share) {
		return new Cond(Kind.TARGET_LOW_HP, share, 0.0D, "", List.of());
	}

	public static Cond casterLowHp(double share) {
		return new Cond(Kind.CASTER_LOW_HP, share, 0.0D, "", List.of());
	}

	public static Cond nearAnchor(double radius) {
		return new Cond(Kind.NEAR_ANCHOR, radius, 0.0D, "", List.of());
	}

	/** {@code TRIB_STRIKES(per, cap)}: +per for each lifetime tribulation strike survived, at most cap. */
	public static Cond tribStrikes(double perStrike, double cap) {
		return new Cond(Kind.TRIB_STRIKES, perStrike, cap, "", List.of());
	}

	/** A biome id ({@code minecraft:bamboo_jungle}) or biome tag ({@code #minecraft:is_mountain}). */
	public static Cond biome(String idOrTag) {
		return new Cond(Kind.BIOME, 0.0D, 0.0D, idOrTag, List.of());
	}

	/** A dimension id ({@code minecraft:the_nether}). */
	public static Cond dimension(String id) {
		return new Cond(Kind.DIMENSION, 0.0D, 0.0D, id, List.of());
	}

	/** The caster reached {@code realm} (and inside it layer index {@code layer}: Kenaikan Abadi Tahap layer + 1). */
	public static Cond rankAtLeast(dev.kultivator.data.Realm realm, int layer) {
		return new Cond(Kind.RANK_AT_LEAST, realm.ordinal(), Math.max(0, layer), "", List.of());
	}

	public static Cond altitude(int minY) {
		return new Cond(Kind.ALTITUDE, minY, 0.0D, "", List.of());
	}

	/** A spiritual site kind (DESIGN 4, {@code SpiritSites}), e.g. {@code mountain_peak}. */
	public static Cond site(String kind) {
		return new Cond(Kind.SITE, 0.0D, 0.0D, kind, List.of());
	}

	public static Cond all(Cond... parts) {
		return new Cond(Kind.ALL, 0.0D, 0.0D, "", List.of(parts));
	}

	public static Cond any(Cond... parts) {
		return new Cond(Kind.ANY, 0.0D, 0.0D, "", List.of(parts));
	}

	public static Cond not(Cond part) {
		return new Cond(Kind.NOT, 0.0D, 0.0D, "", List.of(part));
	}

	/** A short stable text for feat keys ("full_moon_night", "all(sky_open,night)", "biome:minecraft:bamboo_jungle"). */
	public String key() {
		String name = kind.name().toLowerCase(java.util.Locale.ROOT);
		return switch (kind) {
			case TARGET_LOW_HP, CASTER_LOW_HP, NEAR_ANCHOR, ALTITUDE, HIT_BEFORE, TARGET_TOUGH -> name + ":" + trim(a);
			case TRIB_STRIKES -> name + ":" + trim(a) + ":" + trim(b);
			case BIOME, DIMENSION, SITE -> name + ":" + key;
			case ALL, ANY, NOT -> {
				StringBuilder builder = new StringBuilder(name).append('(');
				for (int i = 0; i < parts.size(); i++) {
					builder.append(i == 0 ? "" : ",").append(parts.get(i).key());
				}
				yield builder.append(')').toString();
			}
			default -> name;
		};
	}

	private static String trim(double value) {
		return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
	}
}
