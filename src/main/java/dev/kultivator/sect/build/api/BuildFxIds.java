package dev.kultivator.sect.build.api;

/**
 * Sub effects ({@code a}) of the {@code FxType.SECT_BUILD} packets (DESIGN 21.16), shared by both 6b modules and drawn by
 * {@code client.sectbuild.BuildFx}, scaled by Effect Quality. pos and target are world points; never renumber.
 */
public final class BuildFxIds {

	/**
	 * Blocks placed (batched, at most 8 per payload per tick per site): pos = first placement, target = last placement of
	 * the batch, b = {@code Block.getId(state)} of a representative block (for the crumbs), f = blocks in the batch.
	 */
	public static final int PLACE = 0;
	/** A component started building: pos = box min corner, target = box max corner + 1, b = tier, f = 0. */
	public static final int COMPONENT_OPEN = 1;
	/** A component finished (petal burst along its roofline): pos = box min, target = box max + 1, b = tier, f = roofline y. */
	public static final int COMPONENT_DONE = 2;
	/** A tier finished (light pillar, ribbon along the axis): pos = the main hall anchor, target = the gate end of the axis, b = tier, f = template. */
	public static final int TIER_DONE = 3;
	/** Batu Pertama, the footprint was confirmed (the outline flashes once): pos = footprint min, target = footprint max + 1, b = template, f = rotation. */
	public static final int FIRST_STONE = 4;
	/** The red X shimmer on a blocked cell: pos = target = the cell, b = cells blocked in this ping, f = seconds it lasts. */
	public static final int BLOCKED = 5;
	/** Blocks dismantled or refunded: pos = first cell, target = last cell of the batch, b = {@code Block.getId(old state)}, f = count. */
	public static final int DECONSTRUCT = 6;

	private BuildFxIds() {
	}
}
