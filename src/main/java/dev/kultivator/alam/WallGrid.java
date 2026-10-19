package dev.kultivator.alam;

import net.minecraft.nbt.IntArrayTag;
import net.minecraft.util.RandomSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * The invisible server wall grid of one chamber (DESIGN 25.3.3): a {@link BitSet} of 48 × 48 cells around the stage
 * origin (local x and z from −24 to 23). A player or mob moving into a wall cell is pushed back, an O(1) lookup per tick
 * per party member. The client draws the same walls as merged Display runs from {@link #runs()}. Pure data: no block is
 * ever read or written.
 */
public final class WallGrid {

	public static final int SIZE = 48;
	public static final int HALF = 24;
	/** Height of the drawn walls (the grid itself applies at every height). */
	public static final int WALL_HEIGHT = 3;

	private final BitSet bits = new BitSet(SIZE * SIZE);

	private static int index(int lx, int lz) {
		return (lx + HALF) + (lz + HALF) * SIZE;
	}

	private static boolean inside(int lx, int lz) {
		return lx >= -HALF && lx < HALF && lz >= -HALF && lz < HALF;
	}

	public void set(int lx, int lz) {
		if (inside(lx, lz)) {
			bits.set(index(lx, lz));
		}
	}

	/** True for a wall cell (local coordinates); outside the grid is never a wall. */
	public boolean wall(int lx, int lz) {
		return inside(lx, lz) && bits.get(index(lx, lz));
	}

	public boolean isEmpty() {
		return bits.isEmpty();
	}

	/**
	 * The walls as merged runs {@code x0, z0, x1, z1} (inclusive local cells): maximal horizontal runs of two or more cells,
	 * maximal vertical runs of two or more, then single cells covered by neither. Runs may overlap at corners.
	 */
	public int[] runs() {
		List<int[]> list = new ArrayList<>();
		BitSet covered = new BitSet(SIZE * SIZE);
		for (int lz = -HALF; lz < HALF; lz++) {
			int lx = -HALF;
			while (lx < HALF) {
				if (!wall(lx, lz)) {
					lx++;
					continue;
				}
				int start = lx;
				while (lx < HALF && wall(lx, lz)) {
					lx++;
				}
				if (lx - start >= 2) {
					list.add(new int[]{start, lz, lx - 1, lz});
					for (int x = start; x < lx; x++) {
						covered.set(index(x, lz));
					}
				}
			}
		}
		for (int lx = -HALF; lx < HALF; lx++) {
			int lz = -HALF;
			while (lz < HALF) {
				if (!wall(lx, lz)) {
					lz++;
					continue;
				}
				int start = lz;
				while (lz < HALF && wall(lx, lz)) {
					lz++;
				}
				if (lz - start >= 2) {
					list.add(new int[]{lx, start, lx, lz - 1});
					for (int z = start; z < lz; z++) {
						covered.set(index(lx, z));
					}
				}
			}
		}
		for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
			if (!covered.get(i)) {
				int lx = i % SIZE - HALF;
				int lz = i / SIZE - HALF;
				list.add(new int[]{lx, lz, lx, lz});
			}
		}
		int[] out = new int[list.size() * 4];
		for (int i = 0; i < list.size(); i++) {
			System.arraycopy(list.get(i), 0, out, i * 4, 4);
		}
		return out;
	}

	/** {@link #runs()} as an NBT int array (scenery sync). */
	public IntArrayTag runsTag() {
		return new IntArrayTag(runs());
	}

	// ================================================================== layouts

	/** Jalan Lava: a 5-wide lane from x −18 to 18 between walls at z = ±3, closed at both ends (x = ±19). */
	public static WallGrid lavaLane() {
		WallGrid grid = new WallGrid();
		for (int x = -19; x <= 19; x++) {
			grid.set(x, -3);
			grid.set(x, 3);
		}
		for (int z = -2; z <= 2; z++) {
			grid.set(-19, z);
			grid.set(19, z);
		}
		return grid;
	}

	/** A generated maze: its grid, the entry room centre and the true and phantom exits (local cells). */
	public record Maze(WallGrid grid, int[] entry, int[] exit, List<int[]> phantoms) {
	}

	/** Rooms per side of the Labirin Ilusi: 6 rooms of 3 × 3 cells with 1-cell walls make the 25 × 25 maze (RMJ ch. 1126). */
	public static final int MAZE_ROOMS = 6;

	/**
	 * Labirin Ilusi: a perfect maze (depth-first, seeded) of {@link #MAZE_ROOMS}² rooms, 25 × 25 cells centred on the origin.
	 * The entry is a room on the west side, the true exit the farthest dead end, and up to {@code phantomCount} phantom
	 * exits the next farthest dead ends (other far rooms when there are not enough).
	 */
	public static Maze maze(long seed, int phantomCount) {
		RandomSource random = RandomSource.create(seed);
		int n = MAZE_ROOMS;
		boolean[][] passE = new boolean[n][n];
		boolean[][] passS = new boolean[n][n];
		boolean[][] seen = new boolean[n][n];
		int startJ = random.nextInt(n);
		ArrayDeque<int[]> stack = new ArrayDeque<>();
		stack.push(new int[]{0, startJ});
		seen[0][startJ] = true;
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!stack.isEmpty()) {
			int[] cur = stack.peek();
			List<int[]> next = new ArrayList<>(4);
			for (int[] d : dirs) {
				int ni = cur[0] + d[0];
				int nj = cur[1] + d[1];
				if (ni >= 0 && nj >= 0 && ni < n && nj < n && !seen[ni][nj]) {
					next.add(new int[]{ni, nj});
				}
			}
			if (next.isEmpty()) {
				stack.pop();
				continue;
			}
			int[] pick = next.get(random.nextInt(next.size()));
			if (pick[0] != cur[0]) {
				passE[Math.min(pick[0], cur[0])][cur[1]] = true;
			} else {
				passS[cur[0]][Math.min(pick[1], cur[1])] = true;
			}
			seen[pick[0]][pick[1]] = true;
			stack.push(pick);
		}
		WallGrid grid = new WallGrid();
		for (int k = 0; k <= n; k++) {
			int line = 4 * k - 12;
			for (int r = 0; r < n; r++) {
				if (hSeg(passS, r, k)) {
					for (int c = 4 * r - 11; c <= 4 * r - 9; c++) {
						grid.set(c, line);
					}
				}
				if (vSeg(passE, k, r)) {
					for (int c = 4 * r - 11; c <= 4 * r - 9; c++) {
						grid.set(line, c);
					}
				}
			}
		}
		for (int k = 0; k <= n; k++) {
			for (int m = 0; m <= n; m++) {
				boolean post = (k >= 1 && hSeg(passS, k - 1, m)) || (k < n && hSeg(passS, k, m))
						|| (m >= 1 && vSeg(passE, k, m - 1)) || (m < n && vSeg(passE, k, m));
				if (post) {
					grid.set(4 * k - 12, 4 * m - 12);
				}
			}
		}
		// Distances from the entry room, and the dead ends sorted far to near.
		int[][] dist = new int[n][n];
		for (int[] row : dist) {
			java.util.Arrays.fill(row, -1);
		}
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[]{0, startJ});
		dist[0][startJ] = 0;
		while (!queue.isEmpty()) {
			int[] cur = queue.poll();
			int i = cur[0];
			int j = cur[1];
			int[][] moves = {
					i + 1 < n && passE[i][j] ? new int[]{i + 1, j} : null,
					i - 1 >= 0 && passE[i - 1][j] ? new int[]{i - 1, j} : null,
					j + 1 < n && passS[i][j] ? new int[]{i, j + 1} : null,
					j - 1 >= 0 && passS[i][j - 1] ? new int[]{i, j - 1} : null};
			for (int[] move : moves) {
				if (move != null && dist[move[0]][move[1]] < 0) {
					dist[move[0]][move[1]] = dist[i][j] + 1;
					queue.add(move);
				}
			}
		}
		List<int[]> deadEnds = new ArrayList<>();
		List<int[]> others = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (i == 0 && j == startJ) {
					continue;
				}
				int degree = (i + 1 < n && passE[i][j] ? 1 : 0) + (i > 0 && passE[i - 1][j] ? 1 : 0)
						+ (j + 1 < n && passS[i][j] ? 1 : 0) + (j > 0 && passS[i][j - 1] ? 1 : 0);
				(degree == 1 ? deadEnds : others).add(new int[]{i, j, dist[i][j]});
			}
		}
		deadEnds.sort((a, b) -> Integer.compare(b[2], a[2]));
		others.sort((a, b) -> Integer.compare(b[2], a[2]));
		List<int[]> ranked = new ArrayList<>(deadEnds);
		ranked.addAll(others);
		int[] exitRoom = ranked.get(0);
		List<int[]> phantoms = new ArrayList<>();
		for (int i = 1; i < ranked.size() && phantoms.size() < phantomCount; i++) {
			phantoms.add(roomCentre(ranked.get(i)[0], ranked.get(i)[1]));
		}
		return new Maze(grid, roomCentre(0, startJ), roomCentre(exitRoom[0], exitRoom[1]), phantoms);
	}

	/** Horizontal wall on line {@code k} (0..n) over room column {@code i}: the border, or no passage south. */
	private static boolean hSeg(boolean[][] passS, int i, int k) {
		int n = passS.length;
		return k == 0 || k == n || !passS[i][k - 1];
	}

	/** Vertical wall on line {@code k} (0..n) over room row {@code j}: the border, or no passage east. */
	private static boolean vSeg(boolean[][] passE, int k, int j) {
		int n = passE.length;
		return k == 0 || k == n || !passE[k - 1][j];
	}

	/** The local centre cell of room (i, j). */
	public static int[] roomCentre(int i, int j) {
		return new int[]{4 * i - 10, 4 * j - 10};
	}
}
