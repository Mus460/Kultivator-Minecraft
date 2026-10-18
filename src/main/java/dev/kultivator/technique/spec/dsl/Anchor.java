package dev.kultivator.technique.spec.dsl;

/**
 * Where a shape, FX layer or destruction shape is placed (DESIGN 22.3.1). {@code n} is the point count of
 * {@code EACH_TARGET(n)} and {@code DENSEST(n)}, {@code search} the radius DENSEST picks its points in (from one query,
 * O(n²) over at most 32 hostiles).
 */
public record Anchor(Kind kind, int n, double search) {

	public enum Kind {
		/** The caster (eye for rays, feet for areas). */
		CASTER,
		/** The aim point: a read-only clip along a loaded path. */
		AIM,
		/** The locked target. */
		TARGET,
		/** Up to {@code n} hostile targets. */
		EACH_TARGET,
		/** The {@code n} points with the most hostile neighbours. */
		DENSEST,
		/** The target of the current onHit / onKill sub-step. */
		HIT,
		/** Where the cast started (BLINK BACK, rifts left at the departure point). */
		ORIGIN,
		/** The centre of the cast's own FIELD, DOMAIN or AURA ("ZONE(field)", "ALL(domain)"). */
		AREA,
		/** The end of the last travelling shape: a front's or beam's end, a dash's or path's last point. */
		END,
		/** The afterimages a dash or blink chain left behind. */
		AFTERIMAGE,
		/** The corners of a square FIELD. */
		CORNERS,
		/** The emitters of a FIELD. */
		EMITTERS,
		/** Up in the sky above the anchor point (sky tears, clouds). */
		SKY,
		/** The phantom whose attack runs this step (SUMMON attacks). */
		SUMMON
	}

	public static final Anchor CASTER = new Anchor(Kind.CASTER, 0, 0.0D);
	public static final Anchor AIM = new Anchor(Kind.AIM, 0, 0.0D);
	public static final Anchor TARGET = new Anchor(Kind.TARGET, 0, 0.0D);
	public static final Anchor HIT = new Anchor(Kind.HIT, 0, 0.0D);
	public static final Anchor ORIGIN = new Anchor(Kind.ORIGIN, 0, 0.0D);
	public static final Anchor AREA = new Anchor(Kind.AREA, 0, 0.0D);
	public static final Anchor END = new Anchor(Kind.END, 0, 0.0D);
	public static final Anchor AFTERIMAGE = new Anchor(Kind.AFTERIMAGE, 0, 0.0D);
	public static final Anchor CORNERS = new Anchor(Kind.CORNERS, 0, 0.0D);
	public static final Anchor EMITTERS = new Anchor(Kind.EMITTERS, 0, 0.0D);
	public static final Anchor SKY = new Anchor(Kind.SKY, 0, 0.0D);
	public static final Anchor SUMMON = new Anchor(Kind.SUMMON, 0, 0.0D);

	/** Up to {@code n} hostile targets (one copy of the shape at each). */
	public static Anchor eachTarget(int n) {
		return new Anchor(Kind.EACH_TARGET, Math.max(1, n), 0.0D);
	}

	/** The {@code n} densest points around the aim (search radius 16). */
	public static Anchor densest(int n) {
		return densest(n, 16.0D);
	}

	/** The {@code n} densest points within {@code search} of the aim. */
	public static Anchor densest(int n, double search) {
		return new Anchor(Kind.DENSEST, Math.max(1, n), Math.max(1.0D, search));
	}
}
