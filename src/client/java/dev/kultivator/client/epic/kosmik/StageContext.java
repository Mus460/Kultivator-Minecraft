package dev.kultivator.client.epic.kosmik;

/**
 * What a stage knows about the film it plays in (DESIGN 32.0, contract C32.2).
 *
 * @param skill   the decree 0 Pedang, 1 Tiang, 2 Bintang, 3 Gunung; −1 for a preview
 * @param tier    Tingkat Titah 1-9 (29.16)
 * @param s       the tier's world scale {@code s(t) = 1.6^(t − 1)}
 * @param fs      the stage prop scale {@code s^0.35} (32.5.7): 1.0 at tier 1, 3.73 at tier 9
 * @param rgb     the film's tint colour
 * @param seed    the cast's seed (every seeded hash of the stage starts here)
 * @param quality the stage tier 0 Minimal, 1 Normal, 2 Maximal, after "Film kosmik 3D: Ringan" and the frame guard
 * @param ringan  "Film kosmik 3D: Ringan" (one map size smaller, no planet bake, petals and guards halved)
 * @param pack    a shader pack is in use (the pack profile of 32.1.6)
 * @param calm    Kamera Penuh's calm beat (32.5.6)
 * @param colors  the home colours sampled around the caster
 */
public record StageContext(int skill, int tier, float s, float fs, int rgb, long seed, int quality, boolean ringan, boolean pack, boolean calm,
						   KosmikColors colors) {

	/** The stage prop scale of a tier: {@code s(t)^0.35}. */
	public static float propScale(int tier) {
		return (float) Math.pow(tierScale(tier), 0.35D);
	}

	/** The tier's world scale {@code s(t) = 1.6^(t − 1)} (the same numbers as {@code TitahWorld.tierScale}). */
	public static float tierScale(int tier) {
		return (float) Math.pow(1.6D, Math.max(1, Math.min(9, tier)) - 1);
	}

	/** The same context with another quality (the frame guard at a shot start). */
	public StageContext withQuality(int q) {
		return new StageContext(skill, tier, s, fs, rgb, seed, Math.max(0, Math.min(2, q)), ringan, pack, calm, colors);
	}

	/** The same context for Kamera Penuh's calm beat. */
	public StageContext asCalm() {
		return new StageContext(skill, tier, s, fs, rgb, seed, quality, ringan, pack, true, colors);
	}
}
