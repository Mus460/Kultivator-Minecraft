package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * The six seeded tree archetypes of DESIGN 21.8 (never a free L-system), grown as blocks on day one: logs with their axis
 * and leaves with PERSISTENT=true (no decay, DESIGN 21.9). A tree is skipped when a natural tree already stands within 2
 * (the plan keeps it instead). Template-local; {@code level} null = on the natural ground.
 * <ul>
 * <li>{@link Kind#PINE} Huangshan pine: spruce trunk 6-10 leaning 1 per 3, 2-4 horizontal log arms each ending in a flat
 * spruce pad (5×3 or 7×4, 1 thick) and a crown pad; the most common tree, good on ledges.</li>
 * <li>{@link Kind#PLUM} Mount Hua plum: cherry trunk 2-3 splitting into 3 crooked branches with sparse cherry clusters
 * (30% holes; the dark branches stay visible) and pink petals around (the hero tree of most templates).</li>
 * <li>{@link Kind#WILLOW}: dark trunk 4-5, azalea-leaf dome r 3 with hanging strands; water edges only.</li>
 * <li>{@link Kind#GINKGO}: straight poplar trunk 7-9, conical yellow poplar leaves, a leaf-litter ring.</li>
 * <li>{@link Kind#MAPLE}: poplar trunk 4-5, orange poplar leaves ({@link #red} for red leaves against white walls or rocks).</li>
 * <li>{@link Kind#BAMBOO}: a small bamboo clump (use {@link BambooGrove} for groves).</li>
 * </ul>
 * Planting rules: at least 60% green foliage, about 25% plum and ginkgo, at most 15% maple; trees stay 3 from wooden
 * walls. Every tree stays inside its plot ({@link #within}, always intersected with the footprint column): branches and
 * arms grow shorter instead of being cut flat by a later building or the footprint edge. The hero plum is <i>Return of
 * the Mount Hua Sect</i>'s plum blossom (DESIGN 21.3); the rest is art direction. Thread-safe (no state changes in emit).
 */
public final class Tree implements Part {

	public enum Kind {
		PINE,
		PLUM,
		WILLOW,
		GINKGO,
		MAPLE,
		BAMBOO
	}

	private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private final int x;
	private final int z;
	private final @Nullable Lv level;
	private final Kind kind;
	private boolean red;
	private boolean hero;
	private boolean force;
	/** The cells the tree may use (its plot between later buildings), template-local x/z inclusive; null = anywhere. */
	private int @Nullable [] within;
	/** Cells above {@code base + maxHeight - 1} are never used (an eave or roof overhead); 0 = no limit. */
	private int maxHeight;

	/** The plot of one emission (never stored on the shared part). */
	private record Clip(int x0, int z0, int x1, int z1, int yMax) {
		boolean inside(int px, int py, int pz) {
			return px >= x0 && px <= x1 && pz >= z0 && pz <= z1 && py <= yMax;
		}

		/** Room for a leaf cluster of radius {@code r} around a branch end at (px, pz). */
		boolean roomFor(int px, int pz, int r) {
			return px - r >= x0 && px + r <= x1 && pz - r >= z0 && pz + r <= z1;
		}

		void put(Sink sink, int px, int py, int pz, BlockState state) {
			if (inside(px, py, pz)) {
				sink.set(px, py, pz, state);
			}
		}

		void putIfAbsent(Sink sink, int px, int py, int pz, BlockState state) {
			if (inside(px, py, pz)) {
				sink.setIfAbsent(px, py, pz, state);
			}
		}
	}

	public Tree(Kind kind, int x, int z, @Nullable Lv level) {
		this.kind = kind;
		this.x = x;
		this.z = z;
		this.level = level;
	}

	/** Red maple leaves (only against white walls or rocks). */
	public Tree red(boolean red) {
		this.red = red;
		return this;
	}

	/** A larger hero specimen (the T1 hero tree). */
	public Tree hero(boolean hero) {
		this.hero = hero;
		return this;
	}

	/** Grow even when a natural tree stands near (the plan cleared the plot). */
	public Tree force(boolean force) {
		this.force = force;
		return this;
	}

	/**
	 * Keeps every cell of the tree (trunk lean, branches, crowns, petals) inside the rectangle, shortening branches and
	 * arms instead of cutting them flat: a tree planted between buildings of later tiers never grows into them (DESIGN
	 * 21.8, 21.10: 3 cells from wooden walls).
	 */
	public Tree within(int x0, int z0, int x1, int z1) {
		this.within = new int[]{Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)};
		return this;
	}

	/** Grows no higher than {@code height} cells above its base (it stays under an eave). */
	public Tree maxHeight(int height) {
		this.maxHeight = Math.max(2, height);
		return this;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = baseY(ctx);
		int r = switch (kind) {
			case PINE -> hero ? 5 : 4;
			case PLUM, WILLOW -> 4;
			case GINKGO, MAPLE -> 3;
			case BAMBOO -> 2;
		};
		int h = switch (kind) {
			case PINE -> hero ? 13 : 11;
			case GINKGO -> 11;
			case PLUM -> hero ? 8 : 7;
			default -> 8;
		};
		if (maxHeight > 0) {
			h = Math.min(h, maxHeight - 1);
		}
		int x0 = x - r;
		int z0 = z - r;
		int x1 = x + r;
		int z1 = z + r;
		if (within != null) {
			x0 = Math.max(x0, within[0]);
			z0 = Math.max(z0, within[1]);
			x1 = Math.min(x1, within[2]);
			z1 = Math.min(z1, within[3]);
		}
		return new BoundingBox(Math.min(x0, x), y - 1, Math.min(z0, z), Math.max(x1, x), y + h, Math.max(z1, z));
	}

	@Override
	public String kind() {
		return "tree";
	}

	private int baseY(Ctx ctx) {
		return level != null ? ctx.level(level) : ctx.terrain().ground(x, z) + 1;
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		if (!force && level == null && naturalTreeNear(ctx)) {
			return;
		}
		Random random = ctx.random("tree" + kind + x + "," + z);
		int y = baseY(ctx);
		// The plot, kept inside the footprint column so a tree near the edge grows shorter branches, never a flat-cut crown.
		TemplateSpec spec = ctx.spec();
		Clip clip = new Clip(Math.max(spec.x0(), within == null ? Integer.MIN_VALUE : within[0]),
				Math.max(spec.z0(), within == null ? Integer.MIN_VALUE : within[1]), Math.min(spec.x1(), within == null ? Integer.MAX_VALUE : within[2]),
				Math.min(spec.z1(), within == null ? Integer.MAX_VALUE : within[3]), maxHeight > 0 ? y + maxHeight - 1 : Integer.MAX_VALUE);
		Phase previous = sink.phase();
		sink.phase(Phase.GARDEN);
		sink.set(x, y - 1, z, Palette.st(kind == Kind.BAMBOO ? Blocks.PODZOL : Blocks.GRASS_BLOCK));
		switch (kind) {
			case PINE -> pine(sink, clip, random, y);
			case PLUM -> plum(sink, clip, random, y);
			case WILLOW -> willow(sink, clip, random, y);
			case GINKGO -> ginkgo(sink, clip, random, y);
			case MAPLE -> maple(sink, clip, random, y);
			case BAMBOO -> BambooGrove.clump(sink, random, x, y, z, 2);
		}
		sink.phase(previous);
	}

	private boolean naturalTreeNear(Ctx ctx) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if ((ctx.terrain().flags(x + dx, z + dz) & TerrainView.TREE) != 0) {
					return true;
				}
			}
		}
		return false;
	}

	private void pine(Sink sink, Clip clip, Random random, int y) {
		int height = (hero ? 8 : 6) + random.nextInt(hero ? 3 : 4);
		if (clip.yMax() != Integer.MAX_VALUE) {
			height = Math.max(3, Math.min(height, clip.yMax() - y - 1));
		}
		Direction lean = HORIZONTAL[random.nextInt(4)];
		// The lean only where the crown pad (r 2) still fits the plot.
		int shift = (height - 1) / 3;
		if (!clip.roomFor(x + lean.getStepX() * shift, z + lean.getStepZ() * shift, 2)) {
			lean = null;
		}
		int tx = x;
		int tz = z;
		Block log = Blocks.SPRUCE_LOG;
		for (int i = 0; i < height; i++) {
			if (lean != null && i > 0 && i % 3 == 0) {
				tx += lean.getStepX();
				tz += lean.getStepZ();
			}
			clip.put(sink, tx, y + i, tz, Palette.axis(log, Direction.Axis.Y));
		}
		Direction base = lean == null ? HORIZONTAL[random.nextInt(4)] : lean;
		// 2-4 horizontal arms, alternating sides, each with a flat pad; an arm grows only as far as its pad fits the plot.
		int arms = 2 + random.nextInt(hero ? 3 : 2);
		for (int a = 0; a < arms; a++) {
			int ay = y + height - 2 - a * 2;
			if (ay < y + 3) {
				break;
			}
			Direction dir = a % 2 == 0 ? base.getClockWise() : base.getCounterClockWise();
			if (a == 2) {
				dir = base;
			}
			int padX = a == 0 && hero ? 3 : 2;
			int len = 2 + random.nextInt(2);
			while (len > 0 && !clip.roomFor(tx + dir.getStepX() * len, tz + dir.getStepZ() * len, Math.min(padX, 2) - 1)) {
				len--;
			}
			if (len <= 0) {
				continue;
			}
			int ax = tx;
			int az = tz;
			for (int i = 1; i <= len; i++) {
				ax = tx + dir.getStepX() * i;
				az = tz + dir.getStepZ() * i;
				clip.put(sink, ax, ay, az, Palette.axis(log, dir.getAxis()));
			}
			pad(sink, clip, random, ax, ay + 1, az, padX, a == 0 ? 2 : 1, Blocks.SPRUCE_LEAVES);
		}
		pad(sink, clip, random, tx, y + height, tz, 2, 2, Blocks.SPRUCE_LEAVES);
		clip.put(sink, tx, y + height + 1, tz, Palette.leaves(Blocks.SPRUCE_LEAVES));
	}

	/** A flat, slightly ragged leaf pad of half sizes rx × rz, one block thick. */
	private void pad(Sink sink, Clip clip, Random random, int cx, int cy, int cz, int rx, int rz, Block leaves) {
		for (int dx = -rx; dx <= rx; dx++) {
			for (int dz = -rz; dz <= rz; dz++) {
				boolean corner = Math.abs(dx) == rx && Math.abs(dz) == rz;
				if (corner || (Math.abs(dx) == rx || Math.abs(dz) == rz) && random.nextInt(4) == 0) {
					continue;
				}
				clip.putIfAbsent(sink, cx + dx, cy, cz + dz, Palette.leaves(leaves));
			}
		}
	}

	private void plum(Sink sink, Clip clip, Random random, int y) {
		int trunk = 2 + random.nextInt(2);
		for (int i = 0; i < trunk; i++) {
			clip.put(sink, x, y + i, z, Palette.axis(Blocks.CHERRY_LOG, Direction.Axis.Y));
		}
		int branches = 3;
		for (int b = 0; b < branches; b++) {
			double angle = (b + random.nextDouble() * 0.5D) * Math.PI * 2 / branches;
			int by = y + trunk - 1;
			int len = (hero ? 3 : 2) + random.nextInt(2);
			// A branch reaches only as far as its cluster (r 1) still fits the plot; with no room it stays a short stub.
			while (len > 1 && !clip.roomFor(x + (int) Math.round(Math.cos(angle) * len * 0.9D), z + (int) Math.round(Math.sin(angle) * len * 0.9D), 1)) {
				len--;
			}
			int bx = x;
			int bz = z;
			for (int i = 1; i <= len; i++) {
				int nx = x + (int) Math.round(Math.cos(angle) * i * 0.9D);
				int nz = z + (int) Math.round(Math.sin(angle) * i * 0.9D);
				int ny = by + (i % 2 == 0 ? 1 : 0) + (i > 1 ? 1 : 0);
				if (!clip.inside(nx, ny, nz)) {
					break;
				}
				Direction.Axis axis = nx != bx ? Direction.Axis.X : nz != bz ? Direction.Axis.Z : Direction.Axis.Y;
				sink.set(nx, ny, nz, Palette.axis(Blocks.CHERRY_LOG, axis));
				bx = nx;
				bz = nz;
				by = ny;
			}
			// Sparse clusters with 30% holes: the branches stay visible.
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = 0; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (random.nextInt(10) < 3 || dy == 1 && (dx != 0 && dz != 0)) {
							continue;
						}
						clip.putIfAbsent(sink, bx + dx, by + dy, bz + dz, Palette.leaves(Blocks.CHERRY_LEAVES));
					}
				}
			}
			clip.putIfAbsent(sink, bx, by + 2, bz, Palette.leaves(Blocks.CHERRY_LEAVES));
		}
		// Pink petals r 3 on the ground.
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if ((dx != 0 || dz != 0) && dx * dx + dz * dz <= 9 && random.nextInt(3) == 0) {
					clip.putIfAbsent(sink, x + dx, y, z + dz, Palette.petals(Blocks.PINK_PETALS, 1 + random.nextInt(4), HORIZONTAL[random.nextInt(4)]));
				}
			}
		}
	}

	private void willow(Sink sink, Clip clip, Random random, int y) {
		int trunk = 4 + random.nextInt(2);
		for (int i = 0; i < trunk; i++) {
			clip.put(sink, x, y + i, z, Palette.axis(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
		}
		int top = y + trunk;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 > 10) {
					continue;
				}
				clip.putIfAbsent(sink, x + dx, top, z + dz, Palette.leaves(Blocks.AZALEA_LEAVES));
				if (d2 <= 4) {
					clip.putIfAbsent(sink, x + dx, top + 1, z + dz, Palette.leaves(Blocks.AZALEA_LEAVES));
				}
				boolean rim = d2 >= 7;
				if (rim && Math.floorMod(dx + dz, 2) == 0) {
					int strand = 2 + random.nextInt(3);
					for (int s = 1; s <= strand; s++) {
						clip.putIfAbsent(sink, x + dx, top - s, z + dz, Palette.leaves(Blocks.AZALEA_LEAVES));
					}
				}
			}
		}
	}

	private void ginkgo(Sink sink, Clip clip, Random random, int y) {
		int trunk = 7 + random.nextInt(3);
		for (int i = 0; i < trunk; i++) {
			clip.put(sink, x, y + i, z, Palette.axis(Blocks.POPLAR_LOG, Direction.Axis.Y));
		}
		for (int i = 3; i <= trunk + 1; i++) {
			int r = Math.max(0, (trunk + 2 - i) / 2);
			r = Math.min(r, 2);
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) == r && Math.abs(dz) == r && r > 0) {
						continue;
					}
					clip.putIfAbsent(sink, x + dx, y + i, z + dz, Palette.leaves(Blocks.YELLOW_POPLAR_LEAVES));
				}
			}
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if ((dx != 0 || dz != 0) && random.nextInt(2) == 0) {
					clip.putIfAbsent(sink, x + dx, y, z + dz, Palette.petals(Blocks.LEAF_LITTER, 1 + random.nextInt(3), Direction.NORTH));
				}
			}
		}
	}

	private void maple(Sink sink, Clip clip, Random random, int y) {
		int trunk = 4 + random.nextInt(2);
		for (int i = 0; i < trunk; i++) {
			clip.put(sink, x, y + i, z, Palette.axis(Blocks.POPLAR_LOG, Direction.Axis.Y));
		}
		Block leaves = red ? Blocks.RED_POPLAR_LEAVES : Blocks.ORANGE_POPLAR_LEAVES;
		for (int dy = -1; dy <= 2; dy++) {
			int r = dy == 2 ? 1 : 2;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) == r && Math.abs(dz) == r && random.nextBoolean()) {
						continue;
					}
					clip.putIfAbsent(sink, x + dx, y + trunk + dy, z + dz, Palette.leaves(leaves));
				}
			}
		}
	}

	/** Leaves of a tree kind (for the audit colour budget). */
	public static BlockState leafOf(Kind kind) {
		return switch (kind) {
			case PLUM -> Palette.leaves(Blocks.CHERRY_LEAVES);
			case WILLOW -> Palette.leaves(Blocks.AZALEA_LEAVES);
			case GINKGO -> Palette.leaves(Blocks.YELLOW_POPLAR_LEAVES);
			case MAPLE -> Palette.leaves(Blocks.ORANGE_POPLAR_LEAVES);
			default -> Palette.leaves(Blocks.SPRUCE_LEAVES);
		};
	}
}
