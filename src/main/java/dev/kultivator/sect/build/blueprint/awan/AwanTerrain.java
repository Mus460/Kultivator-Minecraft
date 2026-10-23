package dev.kultivator.sect.build.blueprint.awan;

import dev.kultivator.sect.build.blueprint.TerrainView;

/**
 * The ground every part of a sky plan stands on (DESIGN 37.4.9): wherever an island of the full plan lies, the "natural
 * ground" is the island's own top block (so a hall's foundation, a stair's fill or a paving bed never reach down through
 * the sky to the real ground); everywhere else, the stair foot included, the real snapshot. Pure and immutable (emission
 * runs off the server thread).
 */
public final class AwanTerrain implements TerrainView {

	private final AwanLayout layout;
	private final TerrainView real;

	public AwanTerrain(int template, TerrainView real) {
		this.layout = AwanLayout.of(template);
		this.real = real;
	}

	/** The island top block y at (x, z) of the full plan, or {@code Integer.MIN_VALUE} when no island lies there. */
	public static int islandTop(AwanLayout layout, int x, int z) {
		for (AwanLayout.Pulau p : layout.islands()) {
			int reach = (int) Math.ceil(p.radius() * 1.16D) + 1;
			if (Math.abs(x - p.cx) > reach || Math.abs(z - p.cz) > reach) {
				continue;
			}
			if (PulauAwan.exists(layout, p, x, z, 3)) {
				return PulauAwan.levelOf(layout, p, x, z) - 1;
			}
		}
		return Integer.MIN_VALUE;
	}

	@Override
	public int ground(int x, int z) {
		int top = islandTop(layout, x, z);
		return top != Integer.MIN_VALUE ? top : real.ground(x, z);
	}

	@Override
	public int top(int x, int z) {
		int top = islandTop(layout, x, z);
		return top != Integer.MIN_VALUE ? top : real.top(x, z);
	}

	@Override
	public int water(int x, int z) {
		return islandTop(layout, x, z) != Integer.MIN_VALUE ? 0 : real.water(x, z);
	}

	@Override
	public boolean known(int x, int z) {
		return islandTop(layout, x, z) != Integer.MIN_VALUE || real.known(x, z);
	}

	@Override
	public int flags(int x, int z) {
		return islandTop(layout, x, z) != Integer.MIN_VALUE ? 0 : real.flags(x, z);
	}

	/** The real snapshot under the plan (the stair foot reads it). */
	public TerrainView real() {
		return real;
	}
}
