package dev.kultivator.sect.build.blueprint;

/**
 * The build phases inside one component, in placement order (DESIGN 21.9): a Chinese timber frame rises the way it is
 * really erected, so a half-built hall reads as an open pavilion, never as scaffolding. The ordinal is saved in the
 * resume cursor: never reorder, add new phases at the end only.
 * <ul>
 * <li>{@link #CLEAR}: plants, snow and natural trees above the plot (no drops, flowers credited as Flora).</li>
 * <li>{@link #SEAL}: fluids touching the cut volume become fill (the engine does this on demand before any cell becomes
 * air, DESIGN 21.10; components never emit it).</li>
 * <li>{@link #CUT}: earthwork, layers top-down. {@link #SHELL}: the 1-block shell of underground rooms.
 * {@link #FILL}: earthwork, layers bottom-up.</li>
 * <li>{@link #PLINTH}: platforms, terraces, steps, paving. {@link #FRAME}: pillars all together layer by layer, then
 * beams and brackets. {@link #ROOF}: ascending half-block height s, eave ring first and ridge last.</li>
 * <li>{@link #WALL}: walls, lattice, doors. {@link #INTERIOR}: furniture and lights inside. {@link #ORNAMENT}: plaques,
 * gold, lanterns, banners. {@link #GARDEN}: trees, beds, rocks. {@link #WATER}: static water sources, always last.
 * {@link #TIDY}: fences and walls reconnect (the engine runs it after the last op; components never emit it).</li>
 * </ul>
 */
public enum Phase {
	CLEAR,
	SEAL,
	CUT,
	SHELL,
	FILL,
	PLINTH,
	FRAME,
	ROOF,
	WALL,
	INTERIOR,
	ORNAMENT,
	GARDEN,
	WATER,
	TIDY;

	private static final Phase[] VALUES = values();

	public static Phase byOrdinal(int ordinal) {
		return ordinal < 0 || ordinal >= VALUES.length ? CLEAR : VALUES[ordinal];
	}

	/** Earthwork phases: their layer runs top-down (CUT, CLEAR) or bottom-up (FILL), and they spiral outward. */
	public boolean earthwork() {
		return this == CLEAR || this == CUT || this == FILL;
	}
}
