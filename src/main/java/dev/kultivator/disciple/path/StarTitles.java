package dev.kultivator.disciple.path;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The Stellar Transformations title ladder of the Bintang Abadi stars (DESIGN 20.3.19; Sumber: <i>Stellar
 * Transformations</i>' stages Nebula, Meteor, Star Core, Planet, Tribulation, Stellar, Dark Star, Black Hole). One title
 * per 9 stars, then "Tak Terbatas I, II, ..." with a new hue for each tier. Pure functions, readable by module C (the
 * nametag "Lin Feng · Planet ★31") and the client.
 */
public final class StarTitles {

	/** Stars per title. */
	public static final int TIER_SIZE = 9;
	/** Named tiers before "Tak Terbatas". */
	public static final int NAMED_TIERS = 8;
	/** Meteor shows 1, 3, then 9 gold granules at these stars. */
	public static final int METEOR_ONE = 10;
	public static final int METEOR_THREE = 13;
	public static final int METEOR_NINE = 18;

	private static final String[] IDS = {"nebula", "meteor", "star_core", "planet", "tribulation", "stellar", "dark_star", "black_hole"};
	private static final int[] COLORS = {0xDCE6F5, 0xFFD54A, 0xFFB347, 0x7FC8FF, 0xC59BFF, 0xFFF1A8, 0x7A5CC9, 0x2A1B48};

	/** The title tier of {@code stars}: -1 below one star, 0 Nebula (★1-9) .. 7 Black Hole (★64-72), 8+ Tak Terbatas. */
	public static int tier(int stars) {
		return stars <= 0 ? -1 : (stars - 1) / TIER_SIZE;
	}

	/** The tier's name alone ("Planet", "Tak Terbatas II"); empty below one star. */
	public static Component tierName(int stars) {
		int tier = tier(stars);
		if (tier < 0) {
			return Component.empty();
		}
		if (tier < NAMED_TIERS) {
			return Component.translatable("kultivator.dpath.star.tier." + IDS[tier]);
		}
		return Component.translatable("kultivator.dpath.star.tier.infinite", roman(tier - NAMED_TIERS + 1));
	}

	/** The title for {@code stars} stars: "Planet ★31" (empty below one star). */
	public static Component title(int stars) {
		if (stars <= 0) {
			return Component.empty();
		}
		MutableComponent title = Component.translatable("kultivator.dpath.star.title", tierName(stars), stars);
		return title.withColor(color(stars));
	}

	/** RGB of the tier (aura hue and text colour); each Tak Terbatas tier gets a new hue. */
	public static int color(int stars) {
		int tier = tier(stars);
		if (tier < 0) {
			return 0xFFFFFF;
		}
		if (tier < NAMED_TIERS) {
			return COLORS[tier];
		}
		float hue = ((tier - NAMED_TIERS) * 0.137F) % 1.0F;
		return hsv(hue, 0.55F, 1.0F);
	}

	/** Gold granules of the Meteor stage (0, 1, 3 or 9); later tiers keep nine. */
	public static int granules(int stars) {
		if (stars >= METEOR_NINE) {
			return 9;
		}
		if (stars >= METEOR_THREE) {
			return 3;
		}
		return stars >= METEOR_ONE ? 1 : 0;
	}

	/** Roman numerals up to 3999, plain digits above. */
	public static String roman(int n) {
		if (n <= 0 || n >= 4000) {
			return String.valueOf(n);
		}
		int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
		String[] marks = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
		StringBuilder out = new StringBuilder();
		int left = n;
		for (int i = 0; i < values.length; i++) {
			while (left >= values[i]) {
				out.append(marks[i]);
				left -= values[i];
			}
		}
		return out.toString();
	}

	private static int hsv(float h, float s, float v) {
		float r;
		float g;
		float b;
		int i = (int) Math.floor(h * 6.0F);
		float f = h * 6.0F - i;
		float p = v * (1.0F - s);
		float q = v * (1.0F - f * s);
		float t = v * (1.0F - (1.0F - f) * s);
		switch (Math.floorMod(i, 6)) {
			case 0 -> {
				r = v;
				g = t;
				b = p;
			}
			case 1 -> {
				r = q;
				g = v;
				b = p;
			}
			case 2 -> {
				r = p;
				g = v;
				b = t;
			}
			case 3 -> {
				r = p;
				g = q;
				b = v;
			}
			case 4 -> {
				r = t;
				g = p;
				b = v;
			}
			default -> {
				r = v;
				g = p;
				b = q;
			}
		}
		return Math.round(r * 255.0F) << 16 | Math.round(g * 255.0F) << 8 | Math.round(b * 255.0F);
	}

	private StarTitles() {
	}
}
