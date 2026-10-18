package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Random;

/**
 * Sealed water (DESIGN 21.8 "Water", 21.9 {@code Pond}, {@code Stream}, {@code Moat}, 21.10 "Fluids"): every water cell
 * sits in the plan's own basin (a clay floor and a stone rim on every side), so containment is proven at emission; water
 * goes in last (phase WATER) as static sources. The surface sits 1 below the path (water cells at L-2 and L-3, clay at
 * L-4, the rim's top at L-1). Template-local.
 * <ul>
 * <li>{@link #pond}: an ellipse (or {@link #rect}) of radii rx × rz: lotus (big dripleaf on waterlogged stems), lily pads,
 * seagrass and sea-pickle glow on the floor; a natural taihu-rock rim with flowering azalea on the banks, or a
 * {@link #formal} basin with a smooth-stone-slab curb (the lotus basins of template 1).</li>
 * <li>{@link #stream}: a polyline channel {@code width} wide (1-3) with rock banks; {@link #moat} the same in dressed
 * stone, 3 wide and 2 deep.</li>
 * </ul>
 * Vanilla water has no reflections, so compose for foreground water: the pond lies between the viewpoint and the pagoda.
 * No water ever flows off an island (hanging islands get mist instead).
 */
public final class Pond implements Part {

	private final int cx;
	private final int cz;
	private final int rx;
	private final int rz;
	private final Lv level;
	private boolean rectangle;
	private boolean formal;
	private boolean plants = true;
	private int[][] path;
	private int width;
	private boolean moat;
	private int[] box;

	private Pond(int cx, int cz, int rx, int rz, Lv level) {
		this.cx = cx;
		this.cz = cz;
		this.rx = Math.max(1, rx);
		this.rz = Math.max(1, rz);
		this.level = level;
	}

	/** An elliptical pond centred on (cx, cz) with radii rx × rz; the path around it stands on {@code level}. */
	public static Pond pond(int cx, int cz, int rx, int rz, Lv level) {
		return new Pond(cx, cz, rx, rz, level);
	}

	/** A formal rectangular basin with water over x0..x1 × z0..z1 (the lotus basins of the court). */
	public static Pond basin(int x0, int z0, int x1, int z1, Lv level) {
		Pond pond = new Pond(0, 0, 1, 1, level);
		pond.box = new int[]{Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)};
		pond.formal = true;
		return pond;
	}

	/** A channel through the points (axis-aligned legs), {@code width} wide. */
	public static Pond stream(Lv level, int width, int... xzPairs) {
		Pond pond = new Pond(0, 0, 1, 1, level);
		pond.width = Math.max(1, Math.min(3, width));
		int n = xzPairs.length / 2;
		pond.path = new int[n][2];
		for (int i = 0; i < n; i++) {
			pond.path[i][0] = xzPairs[2 * i];
			pond.path[i][1] = xzPairs[2 * i + 1];
		}
		return pond;
	}

	/** A moat: a dressed-stone channel 3 wide through the points. */
	public static Pond moat(Lv level, int... xzPairs) {
		Pond pond = stream(level, 3, xzPairs);
		pond.moat = true;
		pond.formal = true;
		return pond;
	}

	/** A rectangle instead of an ellipse. */
	public Pond rect(boolean rectangle) {
		this.rectangle = rectangle;
		return this;
	}

	/** A formal basin: dressed rim with a smooth-stone-slab curb, no rocks. */
	public Pond formal(boolean formal) {
		this.formal = formal;
		return this;
	}

	public Pond plants(boolean plants) {
		this.plants = plants;
		return this;
	}

	private boolean water(int x, int z) {
		if (box != null) {
			return x >= box[0] && x <= box[2] && z >= box[1] && z <= box[3];
		}
		if (path != null) {
			for (int i = 0; i + 1 < path.length; i++) {
				int ax = Math.min(path[i][0], path[i + 1][0]);
				int bx = Math.max(path[i][0], path[i + 1][0]);
				int az = Math.min(path[i][1], path[i + 1][1]);
				int bz = Math.max(path[i][1], path[i + 1][1]);
				int half = (width - 1) / 2;
				int extra = width % 2 == 0 ? 1 : 0;
				if (x >= ax - half && x <= bx + half + extra && z >= az - half && z <= bz + half + extra) {
					return true;
				}
			}
			return false;
		}
		if (rectangle) {
			return Math.abs(x - cx) <= rx && Math.abs(z - cz) <= rz;
		}
		double ex = (x - cx) / (rx + 0.5D);
		double ez = (z - cz) / (rz + 0.5D);
		return ex * ex + ez * ez <= 1.0D;
	}

	private int[] extent() {
		if (box != null) {
			return new int[]{box[0] - 1, box[1] - 1, box[2] + 1, box[3] + 1};
		}
		if (path == null) {
			return new int[]{cx - rx - 1, cz - rz - 1, cx + rx + 1, cz + rz + 1};
		}
		int ax = Integer.MAX_VALUE;
		int az = Integer.MAX_VALUE;
		int bx = Integer.MIN_VALUE;
		int bz = Integer.MIN_VALUE;
		for (int[] point : path) {
			ax = Math.min(ax, point[0]);
			az = Math.min(az, point[1]);
			bx = Math.max(bx, point[0]);
			bz = Math.max(bz, point[1]);
		}
		return new int[]{ax - width - 1, az - width - 1, bx + width + 1, bz + width + 1};
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		int[] e = extent();
		return new BoundingBox(e[0], y - 4 - (moat ? 1 : 0), e[1], e[2], y + 1, e[3]);
	}

	@Override
	public String kind() {
		return path == null ? "pond" : moat ? "moat" : "stream";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int y = ctx.level(level);
		int deep = path != null && !moat ? 1 : 2;
		int surface = y - 2;
		int floor = surface - deep;
		Random random = ctx.random("pond" + cx + "," + cz + kind());
		int[] e = extent();
		for (int x = e[0]; x <= e[2]; x++) {
			for (int z = e[1]; z <= e[3]; z++) {
				boolean wet = water(x, z);
				boolean rim = !wet && (water(x + 1, z) || water(x - 1, z) || water(x, z + 1) || water(x, z - 1)
						|| water(x + 1, z + 1) || water(x - 1, z - 1) || water(x + 1, z - 1) || water(x - 1, z + 1));
				if (!wet && !rim) {
					continue;
				}
				// Dig the basin and clear above it.
				int ground = ctx.terrain().ground(x, z);
				sink.phase(Phase.CLEAR);
				for (int cy = Math.max(ground + 1, y); cy <= Math.min(ctx.terrain().top(x, z), y + 6); cy++) {
					sink.air(x, cy, z);
				}
				sink.phase(Phase.CUT);
				for (int cy = y; cy <= Math.min(ground, y + 4); cy++) {
					sink.air(x, cy, z);
				}
				if (wet) {
					for (int cy = Math.min(ground, y - 1); cy > floor; cy--) {
						sink.air(x, cy, z);
					}
					sink.air(x, y - 1, z);
					sink.phase(Phase.SHELL);
					sink.set(x, floor, z, Palette.st(Blocks.CLAY));
					sink.set(x, floor - 1, z, Palette.st(Palette.FILL_STONE));
					// Fill under the floor when the ground is lower (never a hanging basin).
					for (int cy = Math.max(ground + 1, floor - 6); cy < floor - 1; cy++) {
						sink.set(x, cy, z, Palette.st(Palette.FILL_STONE));
					}
				} else {
					sink.phase(Phase.SHELL);
					for (int cy = Math.max(ground + 1, floor - 5); cy <= y - 2; cy++) {
						sink.set(x, cy, z, Palette.st(formal ? Palette.STONE : (random.nextInt(4) == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS)));
					}
					for (int cy = floor - 1; cy <= y - 2; cy++) {
						sink.set(x, cy, z, Palette.st(moat ? Blocks.STONE_BRICKS : formal ? Palette.STONE : Blocks.MUD_BRICKS));
					}
					sink.phase(Phase.PLINTH);
					if (formal) {
						sink.set(x, y - 1, z, Palette.st(Palette.STONE));
						sink.set(x, y, z, Palette.slab(Palette.STONE_COPING, false));
					} else {
						sink.set(x, y - 1, z, Palette.st(random.nextInt(3) == 0 ? Palette.MOSS : Blocks.GRASS_BLOCK));
						sink.phase(Phase.GARDEN);
						int roll = random.nextInt(9);
						if (roll < 2) {
							sink.set(x, y, z, Palette.st(roll == 0 ? Blocks.CALCITE : Blocks.TUFF));
							if (random.nextInt(3) == 0) {
								sink.set(x, y + 1, z, Palette.st(Blocks.ANDESITE));
							}
						} else if (roll == 2) {
							sink.set(x, y, z, Palette.st(Blocks.FLOWERING_AZALEA));
						} else if (roll == 3) {
							sink.set(x, y, z, Palette.st(Blocks.FERN));
						}
					}
				}
			}
		}
		// Plants and glow, then the water itself (last), then lily pads on the surface.
		for (int x = e[0]; x <= e[2]; x++) {
			for (int z = e[1]; z <= e[3]; z++) {
				if (!water(x, z)) {
					continue;
				}
				boolean inner = water(x + 1, z) && water(x - 1, z) && water(x, z + 1) && water(x, z - 1);
				int roll = plants && !moat ? random.nextInt(14) : 99;
				if (box != null && plants && (box[2] - box[0] + 1) * (box[3] - box[1] + 1) <= 6) {
					// Small basins: a lotus leaf or a lily pad in every cell, glow on the floor.
					inner = true;
					roll = Math.floorMod(x + z, 2) == 0 ? 0 : 4;
				}
				sink.phase(Phase.GARDEN);
				if (roll == 0 && inner && deep >= 2) {
					Direction facing = Direction.from2DDataValue(random.nextInt(4));
					sink.set(x, floor + 1, z, Palette.dripleafStem(facing, true));
					sink.set(x, floor + 2, z, Palette.dripleafStem(facing, true));
					sink.set(x, surface + 1, z, Palette.dripleaf(facing, false));
					continue;
				} else if (roll == 1 || roll == 2) {
					sink.set(x, floor + 1, z, Palette.st(Blocks.SEAGRASS));
				} else if (roll == 3) {
					sink.set(x, floor + 1, z, Palette.seaPickles(1 + random.nextInt(3)));
				}
				sink.phase(Phase.WATER);
				for (int wy = floor + 1; wy <= surface; wy++) {
					if (sink.get(x, wy, z) == null || sink.get(x, wy, z).isAir()) {
						sink.set(x, wy, z, Palette.st(Blocks.WATER));
					}
				}
				if ((roll == 4 || roll == 5) && !moat) {
					sink.set(x, surface + 1, z, Palette.st(Blocks.LILY_PAD));
				}
			}
		}
	}

	/** True when (x, z) is a water cell of this pond (bridges and islands use it). */
	public boolean isWater(int x, int z) {
		return water(x, z);
	}

	public int cx() {
		return cx;
	}

	public int cz() {
		return cz;
	}

	/** The water state the engine places (a static source). */
	public static BlockState waterState() {
		return Palette.st(Blocks.WATER);
	}
}
