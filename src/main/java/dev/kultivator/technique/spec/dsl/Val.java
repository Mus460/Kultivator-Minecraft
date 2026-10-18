package dev.kultivator.technique.spec.dsl;

/**
 * A size, duration or count of the catalogue notation (DESIGN 22.3.6): {@code b} alone is fixed, {@code b→c} grows from
 * {@code base} at mastery 0 with A/D/N and mastery up to the spec cap {@code cap}, and {@code lin(b, k, c)} is
 * {@code b + k} per tier index above the gate, capped at {@code c}, with no A/D/N on top (22.2.5). {@link #NONE} means
 * "use the shape's default or the surrounding area". The runner applies the hard caps of 22.15 on top.
 */
public record Val(double base, double cap, double perTier, boolean linear) {

	/** Not set: the default of the shape, or the radius of the cast's own field/domain. */
	public static final Val NONE = new Val(Double.NaN, Double.NaN, 0.0D, false);

	/** A fixed value (never grows). */
	public static Val of(double value) {
		return new Val(value, value, 0.0D, false);
	}

	/** {@code base → cap}: grows with A/D/N and mastery up to {@code cap}. */
	public static Val of(double base, double cap) {
		return new Val(base, Math.max(base, cap), 0.0D, false);
	}

	/** {@code lin(base, perTier, cap)}: base plus perTier per tier index above the gate, at most cap (22.2.5). */
	public static Val lin(double base, double perTier, double cap) {
		return new Val(base, cap, perTier, true);
	}

	public boolean set() {
		return !Double.isNaN(base);
	}

	/** True when the value can grow (b→c with c > b, or a lin() with a step). */
	public boolean grows() {
		return linear ? perTier != 0.0D : cap > base;
	}

	/** The base, or {@code fallback} when not set. */
	public double baseOr(double fallback) {
		return set() ? base : fallback;
	}

	/** The spec cap, or {@code fallback} when not set. */
	public double capOr(double fallback) {
		return set() ? cap : fallback;
	}
}
