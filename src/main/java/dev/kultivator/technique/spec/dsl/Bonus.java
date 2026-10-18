package dev.kultivator.technique.spec.dsl;

import java.util.List;

/**
 * A mastery bonus (DESIGN 22.6): level 3 {@code perfection} (EXTRA_COUNT, EXTRA_WAVE, AREA, DURATION, PAYLOAD, CHAIN,
 * COOLDOWN, EXTRA_CAST) and level 4 {@code peak} (ECHO 0.4 where 22.3.4 allows it, otherwise DURATION 0.25).
 * {@code value}: a share (AREA +0.25, COOLDOWN −0.2, DURATION +0.25) or a factor (ECHO, EXTRA_CAST, side copies of
 * EXTRA_COUNT); {@code count}: extra things; {@code ticks}: flat extra duration (DURATION_TICKS) or EXTRA_CAST spacing;
 * {@code hit}/{@code self}: extra payloads; {@code steps}: extra steps (a final giant sword, a converging bolt);
 * {@code key}: a stable id for bonuses only hand-written code understands (SPECIAL).
 */
public record Bonus(Kind kind, double value, int count, int ticks, List<Payload.Hit> hit, List<Payload.Boon> self,
		List<Step> steps, String key) {

	public enum Kind { EXTRA_COUNT, EXTRA_WAVE, AREA, DURATION, DURATION_TICKS, PAYLOAD, CHAIN, COOLDOWN, EXTRA_CAST, ECHO, STEPS, SPECIAL }

	private static Bonus of(Kind kind, double value, int count, int ticks) {
		return new Bonus(kind, value, count, ticks, List.of(), List.of(), List.of(), "");
	}

	/** +n projectiles, swords, crescents, bolts... */
	public static Bonus extraCount(int n) { return of(Kind.EXTRA_COUNT, 1.0D, n, 0); }
	/** +n copies, the extra ones at {@code factor} ("3 palms fanned, side palms ×.6"). */
	public static Bonus extraCount(int n, double factor) { return of(Kind.EXTRA_COUNT, factor, n, 0); }
	public static Bonus extraWave(int n) { return of(Kind.EXTRA_WAVE, 1.0D, n, 0); }
	/** Area +{@code share} (0.25 = +25%), still inside the hard caps. */
	public static Bonus area(double share) { return of(Kind.AREA, share, 0, 0); }
	/** Duration +{@code share}. */
	public static Bonus duration(double share) { return of(Kind.DURATION, share, 0, 0); }
	/** Duration +{@code ticks} flat ("+100t"). */
	public static Bonus durationTicks(int ticks) { return of(Kind.DURATION_TICKS, 0.0D, 0, ticks); }
	public static Bonus payload(Payload.Hit... payloads) { return new Bonus(Kind.PAYLOAD, 0.0D, 0, 0, List.of(payloads), List.of(), List.of(), ""); }
	public static Bonus selfPayload(Payload.Boon... payloads) { return new Bonus(Kind.PAYLOAD, 0.0D, 0, 0, List.of(), List.of(payloads), List.of(), ""); }
	/** +n chain hops. */
	public static Bonus chain(int hops) { return of(Kind.CHAIN, 0.0D, hops, 0); }
	/** Cooldown −{@code share} (within the ×0.4 floor; never on PAMUNGKAS). */
	public static Bonus cooldown(double share) { return of(Kind.COOLDOWN, share, 0, 0); }
	/** The whole cast again {@code times} more times at {@code factor}, {@code spacing} ticks apart. */
	public static Bonus extraCast(int times, double factor, int spacing) { return of(Kind.EXTRA_CAST, factor, times, spacing); }
	/** ECHO (22.3.4): the steps re-run at {@code factor} with no destruction, an afterimage tint and no mastery. */
	public static Bonus echo(double factor) { return of(Kind.ECHO, factor, 0, 10); }
	public static Bonus steps(Step... steps) { return new Bonus(Kind.STEPS, 0.0D, 0, 0, List.of(), List.of(), List.of(steps), ""); }
	/** A bonus only the spec's hand-written code knows, by key (legacy techniques, specials). */
	public static Bonus special(String key, double value) { return new Bonus(Kind.SPECIAL, value, 0, 0, List.of(), List.of(), List.of(), key); }
}
