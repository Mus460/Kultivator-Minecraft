package dev.kultivator.sect.build.blueprint;

/**
 * A standing level in template-local blocks (DESIGN 21.1): level {@code L} means "people stand at y = L", so its floor
 * block lies at {@code y = L - 1}. The plaza is level 0 (the stele's own level; the plaza floor is stele y - 1). A level is
 * either fixed or relative to a terrain-fitted zone of the {@link TemplateSpec} ({@code zone("east", 1)} = one above the
 * fitted level of zone "east"); {@link Ctx#level(Lv)} resolves it.
 */
public record Lv(String zone, int offset) {

	/** The plaza, level 0. */
	public static final Lv PLAZA = new Lv(null, 0);

	/** A fixed level (relative to the plaza). */
	public static Lv at(int level) {
		return new Lv(null, level);
	}

	/** The fitted level of a zone. */
	public static Lv zone(String zone) {
		return new Lv(zone, 0);
	}

	/** The fitted level of a zone plus {@code offset}. */
	public static Lv zone(String zone, int offset) {
		return new Lv(zone, offset);
	}

	/** This level raised by {@code by}. */
	public Lv plus(int by) {
		return new Lv(zone, offset + by);
	}

	public boolean fixed() {
		return zone == null;
	}
}
