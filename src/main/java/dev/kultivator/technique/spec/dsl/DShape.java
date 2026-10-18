package dev.kultivator.technique.spec.dsl;

/**
 * A Mode Kehancuran carving shape at K = 1 (DESIGN 22.11.4, the KH column). Only {@code Kehancuran.submitSelf} ever
 * turns it into block changes, and only when the player's own switch is on (22.11.2); every dimension is scaled by
 * K × mastery area and clamped (22.11.3), counts ({@code copies}) never scale. Dimensions per kind:
 * <ul>
 * <li>BOWL r, SCATTER r, SPHERE r, STRIP r, WITHER r: {@code a} = r;</li>
 * <li>RING ro/ri/d: a = ro, b = ri, c = d; TUNNEL r L: a = r, b = L; FISSURE / TRENCH L×w×d: a = L, b = w, c = d;</li>
 * <li>ARC r° w d: a = r, angle = °, b = w, c = d; FAN L° d: a = L, angle = °, c = d; STRIP fan L: a = L, angle = °;</li>
 * <li>DISC r d [nat]: a = r, c = d; PIT hw/d: a = hw, c = d; COLUMN r d: a = r, c = d; NINE r s: a = r, b = s.</li>
 * </ul>
 * Suffixes: {@code copies} (×n anchors), {@code atTick} (@t), {@code stage} (st n), {@code when} (at the first impact,
 * at release / the end), {@code along} (a dash path, the beam, a square field's edges, the shock line),
 * {@code ahead} (from n blocks ahead), {@code perEach} (one copy per crescent, phoenix or chain hop), {@code naturalOnly},
 * {@code eachStage} (again at every stage of a STAGES spec, "each stomp", sharing the shape's cap part) and
 * {@code nearGround} (only when a solid block lies within n blocks below the anchor; one read-only scan).
 */
public record DShape(Kind kind, double a, double b, double c, double angle, boolean naturalOnly, int copies, int atTick,
		int stage, When when, Along along, double ahead, boolean perEach, boolean perStage, double nearGround) {

	public enum Kind { BOWL, RING, TUNNEL, FISSURE, SCATTER, ARC, FAN, STRIP, STRIP_FAN, TRENCH, SPHERE, DISC, PIT, COLUMN, NINE, WITHER }

	/** When the shape is submitted (default: the first IMPACT, or the first hit of the first damaging step). */
	public enum When { IMPACT, TICK, RELEASE }

	public enum Along { DEFAULT, PATH, BEAM, EDGES, LINE }

	private static DShape of(Kind kind, double a, double b, double c, double angle) {
		return new DShape(kind, a, b, c, angle, false, 1, -1, 0, When.IMPACT, Along.DEFAULT, 0.0D, false, false, 0.0D);
	}

	public static DShape bowl(double r) { return of(Kind.BOWL, r, 0.0D, 0.0D, 0.0D); }
	public static DShape ring(double ro, double ri, double d) { return of(Kind.RING, ro, ri, d, 0.0D); }
	public static DShape tunnel(double r, double length) { return of(Kind.TUNNEL, r, length, 0.0D, 0.0D); }
	public static DShape fissure(double length, double width, double depth) { return of(Kind.FISSURE, length, width, depth, 0.0D); }
	public static DShape scatter(double r) { return of(Kind.SCATTER, r, 0.0D, 0.0D, 0.0D); }
	public static DShape arc(double r, double degrees, double width, double depth) { return of(Kind.ARC, r, width, depth, degrees); }
	public static DShape fan(double length, double degrees, double depth) { return of(Kind.FAN, length, 0.0D, depth, degrees); }
	/** Foliage only ({@code #kultivator:strippable}) in a disc of radius r. */
	public static DShape strip(double r) { return of(Kind.STRIP, r, 0.0D, 0.0D, 0.0D); }
	/** Foliage only in a fan of length L (default half-angle 35°). */
	public static DShape stripFan(double length) { return of(Kind.STRIP_FAN, length, 0.0D, 0.0D, 35.0D); }
	public static DShape stripFan(double length, double degrees) { return of(Kind.STRIP_FAN, length, 0.0D, 0.0D, degrees); }
	public static DShape trench(double length, double width, double depth) { return of(Kind.TRENCH, length, width, depth, 0.0D); }
	/** {@code TRENCH path×w×d}: along the segment a dash or blink travelled. */
	public static DShape trenchPath(double width, double depth) { return of(Kind.TRENCH, 0.0D, width, depth, 0.0D).alongPath(); }
	public static DShape sphere(double r) { return of(Kind.SPHERE, r, 0.0D, 0.0D, 0.0D); }
	public static DShape disc(double r, double depth) { return of(Kind.DISC, r, 0.0D, depth, 0.0D); }
	public static DShape pit(double halfWidth, double depth) { return of(Kind.PIT, halfWidth, 0.0D, depth, 0.0D); }
	public static DShape column(double r, double depth) { return of(Kind.COLUMN, r, 0.0D, depth, 0.0D); }
	public static DShape nine(double r, double spacing) { return of(Kind.NINE, r, spacing, 0.0D, 0.0D); }
	public static DShape wither(double r) { return of(Kind.WITHER, r, 0.0D, 0.0D, 0.0D); }

	/** Natural blocks only (the 20.3.21 list). */
	public DShape nat() { return new DShape(kind, a, b, c, angle, true, copies, atTick, stage, when, along, ahead, perEach, perStage, nearGround); }
	/** {@code ×n}: one copy at each of up to n anchors (corners, emitters, targets, DENSEST points, impacts). */
	public DShape times(int n) { return new DShape(kind, a, b, c, angle, naturalOnly, Math.max(1, n), atTick, stage, when, along, ahead, perEach, perStage, nearGround); }
	/** {@code (@t)}: at step tick t. */
	public DShape at(int tick) { return new DShape(kind, a, b, c, angle, naturalOnly, copies, tick, stage, When.TICK, along, ahead, perEach, perStage, nearGround); }
	/** {@code (st n)}: at stage n. */
	public DShape stage(int n) { return new DShape(kind, a, b, c, angle, naturalOnly, copies, atTick, n, when, along, ahead, perEach, perStage, nearGround); }
	/** At release / on collapse / at the end. */
	public DShape atRelease() { return new DShape(kind, a, b, c, angle, naturalOnly, copies, atTick, stage, When.RELEASE, along, ahead, perEach, perStage, nearGround); }
	public DShape alongPath() { return new DShape(kind, a, b, c, angle, naturalOnly, copies, atTick, stage, when, Along.PATH, ahead, perEach, perStage, nearGround); }
	public DShape alongBeam() { return new DShape(kind, a, b, c, angle, naturalOnly, copies, atTick, stage, when, Along.BEAM, ahead, perEach, perStage, nearGround); }
	public DShape edges() { return new DShape(kind, a, b, c, angle, naturalOnly, copies, atTick, stage, when, Along.EDGES, ahead, perEach, perStage, nearGround); }
	public DShape alongLine() { return new DShape(kind, a, b, c, angle, naturalOnly, copies, atTick, stage, when, Along.LINE, ahead, perEach, perStage, nearGround); }
	/** From {@code blocks} ahead of the caster. */
	public DShape ahead(double blocks) { return new DShape(kind, a, b, c, angle, naturalOnly, copies, atTick, stage, when, along, blocks, perEach, perStage, nearGround); }
	/** One copy per crescent / phoenix / chain hop. */
	public DShape each() { return new DShape(kind, a, b, c, angle, naturalOnly, copies, atTick, stage, when, along, ahead, true, perStage, nearGround); }
	/** Again at every stage of a STAGES spec ("each stomp"); the stages share this shape's part of the cap. */
	public DShape eachStage() { return new DShape(kind, a, b, c, angle, naturalOnly, copies, atTick, stage, when, along, ahead, perEach, true, nearGround); }
	/** Only when a solid block lies within {@code blocks} below the anchor ("only within 3 of the ground"). */
	public DShape nearGround(double blocks) { return new DShape(kind, a, b, c, angle, naturalOnly, copies, atTick, stage, when, along, ahead, perEach, perStage, Math.max(0.0D, blocks)); }
}
