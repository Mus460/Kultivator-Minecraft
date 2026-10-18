package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The Bagua formation disc inlaid in the plaza floor (DESIGN 21.2 T5, 21.5 "Formation disc", 21.9 {@code FormationDisc}):
 * a lodestone {@code d} blocks toward the gate from the stele, crying obsidian at ±4 and amethyst at ±3 around it (the
 * Protective Formation of DESIGN 10, placed <b>inactive</b>: the owner registers it with an amethyst shard), a smooth
 * quartz ring of radius r with the eight trigrams in polished blackstone (yang = 3 blocks, yin = 1 + gap + 1, bottom line
 * nearest the centre), gilded-blackstone compass points, prismarine jade inlay and a chiseled-tuff rim where it fits.
 * <p>
 * The trigrams follow the King Wen (Later Heaven) arrangement in <b>true compass orientation</b> whatever the rotation:
 * ☲ S, ☵ N, ☳ E, ☱ W, ☴ SE, ☷ SW, ☶ NE, ☰ NW (matches the GUI Bagua). The ring never passes through the stele (d ≠ r).
 * {@link #clip} keeps the inlay inside the plaza rectangle. Exact items: 1 lodestone, 4 crying obsidian, 4 amethyst blocks.
 * Also the Qi seat tier 1 of template 1 (the amethyst ring around the lodestone, DESIGN 4).
 */
public final class FormationDisc implements Part {

	/** Lines of each trigram from the bottom (inner) line up: 1 = yang, 0 = yin. */
	private static final int[] QIAN = {1, 1, 1};
	private static final int[] KUN = {0, 0, 0};
	private static final int[] ZHEN = {1, 0, 0};
	private static final int[] KAN = {0, 1, 0};
	private static final int[] GEN = {0, 0, 1};
	private static final int[] XUN = {0, 1, 1};
	private static final int[] LI = {1, 0, 1};
	private static final int[] DUI = {1, 1, 0};

	private final int d;
	private final int r;
	private final Lv level;
	private int cx0 = -64;
	private int cz0 = -64;
	private int cx1 = 64;
	private int cz1 = 64;
	private final java.util.List<int[]> avoid = new java.util.ArrayList<>();

	public FormationDisc(int d, int r, Lv level) {
		this.d = d;
		this.r = Math.max(3, r);
		this.level = level;
	}

	/** Keeps every inlay cell inside the template-local rectangle. */
	public FormationDisc clip(int x0, int z0, int x1, int z1) {
		this.cx0 = Math.min(x0, x1);
		this.cz0 = Math.min(z0, z1);
		this.cx1 = Math.max(x0, x1);
		this.cz1 = Math.max(z0, z1);
		return this;
	}

	/** Leaves the template-local rectangle alone (basins, posts, other parts already there). */
	public FormationDisc avoid(int x0, int z0, int x1, int z1) {
		avoid.add(new int[]{Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)});
		return this;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level) - 1;
		int reach = r + 3;
		return new BoundingBox(Math.max(cx0, -reach), y, Math.max(cz0, d - reach), Math.min(cx1, reach), y, Math.min(cz1, d + reach));
	}

	@Override
	public String kind() {
		return "formation";
	}

	private boolean allowed(int x, int z) {
		for (int[] box : avoid) {
			if (x >= box[0] && x <= box[2] && z >= box[1] && z <= box[3]) {
				return false;
			}
		}
		return x >= cx0 && x <= cx1 && z >= cz0 && z <= cz1 && !(x == 0 && z == 0);
	}

	private void put(Sink sink, int x, int y, int z, BlockState state) {
		if (allowed(x, z)) {
			sink.set(x, y, z, state);
		}
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int y = ctx.level(level) - 1;
		Phase previous = sink.phase();
		sink.phase(Phase.PLINTH);
		BlockState quartz = Palette.st(Palette.MARBLE_DECK);
		BlockState line = Palette.st(Blocks.POLISHED_BLACKSTONE);
		// The ring.
		for (int x = -r - 1; x <= r + 1; x++) {
			for (int z = d - r - 1; z <= d + r + 1; z++) {
				double dist = Math.sqrt(x * x + (z - d) * (z - d));
				if (Math.round(dist) == r) {
					put(sink, x, y, z, quartz);
				}
			}
		}
		// The eight trigrams in true compass orientation.
		trigram(ctx, sink, y, Direction.SOUTH, null, LI, quartz, line);
		trigram(ctx, sink, y, Direction.NORTH, null, KAN, quartz, line);
		trigram(ctx, sink, y, Direction.EAST, null, ZHEN, quartz, line);
		trigram(ctx, sink, y, Direction.WEST, null, DUI, quartz, line);
		trigram(ctx, sink, y, Direction.SOUTH, Direction.EAST, XUN, quartz, line);
		trigram(ctx, sink, y, Direction.SOUTH, Direction.WEST, KUN, quartz, line);
		trigram(ctx, sink, y, Direction.NORTH, Direction.EAST, GEN, quartz, line);
		trigram(ctx, sink, y, Direction.NORTH, Direction.WEST, QIAN, quartz, line);
		// Compass points, jade inlay and the rim where they fit.
		for (Direction compass : new Direction[]{Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST}) {
			Direction local = ctx.compass(compass);
			put(sink, local.getStepX() * (r + 3), y, d + local.getStepZ() * (r + 3), Palette.st(Palette.PLAQUE_FIELD));
		}
		for (int[] diagonal : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
			int k = Math.max(1, (int) Math.round((r - 1) / Math.sqrt(2.0D)));
			put(sink, diagonal[0] * k, y, d + diagonal[1] * k, Palette.st(Blocks.PRISMARINE_BRICKS));
		}
		// The Protective Formation (DESIGN 10): lodestone, crying obsidian at ±4, amethyst at ±3, all on one level.
		sink.set(0, y, d, Palette.st(Blocks.LODESTONE));
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			sink.set(4 * direction.getStepX(), y, d + 4 * direction.getStepZ(), Palette.st(Blocks.CRYING_OBSIDIAN));
			sink.set(3 * direction.getStepX(), y, d + 3 * direction.getStepZ(), Palette.st(Blocks.AMETHYST_BLOCK));
		}
		sink.anchor("formation", 0, y + 1, d);
		sink.phase(previous);
	}

	private void trigram(Ctx ctx, Sink sink, int y, Direction a, Direction b, int[] lines, BlockState gap, BlockState mark) {
		Direction la = ctx.compass(a);
		double ux = la.getStepX();
		double uz = la.getStepZ();
		if (b != null) {
			Direction lb = ctx.compass(b);
			ux = (ux + lb.getStepX()) / Math.sqrt(2.0D);
			uz = (uz + lb.getStepZ()) / Math.sqrt(2.0D);
		}
		double tx = -uz;
		double tz = ux;
		// The band lies outside the ring, unless that would crowd the stele: then it lies inside (bottom line still inner).
		boolean inward = false;
		for (int j = -1; j <= 1; j++) {
			int x = (int) Math.round(ux * (r + 2) + tx * j);
			int z = d + (int) Math.round(uz * (r + 2) + tz * j);
			if (Math.abs(x) <= 2 && Math.abs(z) <= 2) {
				inward = true;
			}
		}
		for (int k = 0; k < 3; k++) {
			double rho = inward ? r - 2 + k : r + k;
			for (int j = -1; j <= 1; j++) {
				int x = (int) Math.round(ux * rho + tx * j);
				int z = d + (int) Math.round(uz * rho + tz * j);
				boolean on = lines[k] == 1 || j != 0;
				if (isFormationCell(x, z)) {
					continue;
				}
				put(sink, x, y, z, on ? mark : gap);
			}
		}
	}

	/** Cells of the lodestone pattern (never overwritten by an inlay). */
	private boolean isFormationCell(int x, int z) {
		int dz = z - d;
		return x == 0 && Math.abs(dz) == 3 || x == 0 && Math.abs(dz) == 4 || dz == 0 && Math.abs(x) == 3 || dz == 0 && Math.abs(x) == 4
				|| x == 0 && dz == 0;
	}
}
