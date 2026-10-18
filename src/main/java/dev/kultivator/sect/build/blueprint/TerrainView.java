package dev.kultivator.sect.build.blueprint;

/**
 * The frozen terrain a plan is fitted to, in template-local coordinates (x east of the stele with the template's
 * rotation undone, z toward the gate, y relative to the stele). It is immutable, so components may read it off the
 * server thread (DESIGN 21.9): the engine's {@code Snapshot} implements it from chunk heightmaps taken on the server
 * thread; dry runs use {@link #flat(int)}.
 */
public interface TerrainView {

	/** Column flag: the column holds player-made blocks between its ground and its top. */
	int PLAYER_MADE = 1;
	/** Column flag: a block entity (container, sign, spawner...) stands in the column. */
	int BLOCK_ENTITY = 2;
	/** Column flag: lava at or near the surface. */
	int LAVA = 4;
	/** Column flag: a natural tree (logs with natural leaves) stands on the column. */
	int TREE = 8;
	/** Column flag: a protected block (bedrock, portal, spawner...) was seen in the column. */
	int PROTECTED = 16;

	/** The local y of the top natural ground block of a column (stone, dirt, sand...; never logs, leaves or plants). */
	int ground(int x, int z);

	/** The local y of the top non-air block (trees, plants, snow and buildings included); at least {@link #ground}. */
	int top(int x, int z);

	/** Water depth above the ground (0 = dry). */
	int water(int x, int z);

	/** True when the column was captured (its chunk was loaded at least once). */
	boolean known(int x, int z);

	/** {@link #PLAYER_MADE}, {@link #BLOCK_ENTITY}, {@link #LAVA}, {@link #TREE}, {@link #PROTECTED} bits. */
	int flags(int x, int z);

	/** Flat dry land whose ground block is at local y {@code groundY} everywhere (dry runs, the audit, showcase hints). */
	static TerrainView flat(int groundY) {
		return new TerrainView() {
			@Override
			public int ground(int x, int z) {
				return groundY;
			}

			@Override
			public int top(int x, int z) {
				return groundY;
			}

			@Override
			public int water(int x, int z) {
				return 0;
			}

			@Override
			public boolean known(int x, int z) {
				return true;
			}

			@Override
			public int flags(int x, int z) {
				return 0;
			}
		};
	}
}
