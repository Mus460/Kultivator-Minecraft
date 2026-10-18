package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A pagoda of n floors (DESIGN 21.7 "Pagodas", 21.9 {@code Pagoda}): floor heights fall as it rises and the silhouette
 * tapers convexly (never a straight stack). Each floor: core walls with lattice on 4 sides, a skirt eave (all +1, the last
 * +2) with corner tips and a lantern under every corner, and a balcony railing where the next core steps in. The top
 * floor carries a steep pyramid (or octagonal) roof and the spire: tile walls, the gold block, chains and an end rod
 * (1 end rod per pagoda, an exact item). Template-local, centred on (x, z), standing on {@code level}.
 * <ul>
 * <li>{@link #floors}(n, cores, heights): per floor the core half-size and wall height, e.g. Tujuh Harta
 * {@code floors(7, new int[]{4,4,3,3,2,2,2}, new int[]{6,5,5,4,4,4,4})}. {@link #auto} derives a convex taper from a base
 * core and a floor count.</li>
 * <li>{@link #octagonal}: the glazed 7- and 9-floor pagodas are octagonal (Porcelain Tower of Nanjing, Kaifeng Iron
 * Pagoda); templates 2-4 use square ones.</li>
 * <li>{@link #glazed}: always Giok field and trim with white walls and jade railings, whatever the plan's glaze (21.5).</li>
 * </ul>
 * The dominant pagoda's top should be 1.6-2.0 × the main hall's ridge height above the plaza (21.7). Names after
 * <i>Soul Land</i>'s Seven/Nine Treasure Glazed Tile pagoda (a martial soul there; the storey counts are design).
 */
public final class Pagoda implements Part {

	private final int x;
	private final int z;
	private final Lv level;
	private int[] cores = {3, 3, 2};
	private int[] heights = {5, 4, 4};
	private boolean octagonal;
	private boolean glazed;
	private int plinth = 2;
	private int plinthMargin = 2;
	private int[] overhangs;
	private Direction entry = Direction.SOUTH;
	private boolean spire = true;

	public Pagoda(int x, int z, Lv level) {
		this.x = x;
		this.z = z;
		this.level = level;
	}

	/** Explicit floors: core half-sizes and wall heights, bottom to top (arrays of {@code n}). */
	public Pagoda floors(int n, int[] coreHalves, int[] floorHeights) {
		int count = Math.max(1, n);
		this.cores = new int[count];
		this.heights = new int[count];
		for (int i = 0; i < count; i++) {
			cores[i] = Math.max(1, coreHalves[Math.min(i, coreHalves.length - 1)]);
			heights[i] = Math.max(3, floorHeights[Math.min(i, floorHeights.length - 1)]);
		}
		return this;
	}

	/** A convex taper of {@code n} floors from a base core half-size (heights 5 then 4). */
	public Pagoda auto(int n, int baseCore) {
		int count = Math.max(1, n);
		cores = new int[count];
		heights = new int[count];
		for (int i = 0; i < count; i++) {
			double t = count == 1 ? 0.0D : i / (double) (count - 1);
			cores[i] = Math.max(1, (int) Math.round(baseCore - (baseCore - Math.max(1, baseCore / 2)) * t * t));
			heights[i] = i == 0 ? 5 : 4;
		}
		return this;
	}

	public Pagoda octagonal(boolean octagonal) {
		this.octagonal = octagonal;
		return this;
	}

	public Pagoda glazed(boolean glazed) {
		this.glazed = glazed;
		return this;
	}

	/** The stone plinth height under floor 1 (default 2). */
	public Pagoda plinth(int height) {
		this.plinth = Math.max(1, height);
		return this;
	}

	/** Skirt overhangs per floor (default 3 and 2 alternating; odd ones leave no slit because skirts are all +1). */
	public Pagoda overhangs(int... overhangs) {
		this.overhangs = overhangs.clone();
		return this;
	}

	/** Cells of plinth beyond the ground-floor core (default 2). */
	public Pagoda plinthMargin(int margin) {
		this.plinthMargin = Math.max(1, margin);
		return this;
	}

	private int overhang(int floor) {
		if (overhangs != null && overhangs.length > 0) {
			return Math.max(1, overhangs[Math.min(floor, overhangs.length - 1)]);
		}
		return floor % 2 == 0 ? 3 : 2;
	}

	public Pagoda entry(Direction entry) {
		this.entry = entry;
		return this;
	}

	/**
	 * {@code false}: the top ends in the pavilion finial (tile wall and the gold block) without chains and the end rod, for
	 * towers that are not the sect's dominant pagoda (scripture towers, the pill tower). End rods (an End item) appear only
	 * on the T8/T9 pagodas (DESIGN 21.11), so "just deposit" never stalls at an early tier.
	 */
	public Pagoda spire(boolean spire) {
		this.spire = spire;
		return this;
	}

	/** Total height above the standing level (plinth, floors, roof and spire). */
	public int height() {
		int h = plinth;
		for (int i = 0; i < heights.length; i++) {
			h += heights[i] + 1;
		}
		return h + cores[cores.length - 1] + 4 + 6;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		int r = cores[0] + Math.max(plinthMargin, overhang(0)) + 1;
		return new BoundingBox(x - r, y - 1, z - r, x + r, y + height(), z + r);
	}

	@Override
	public String kind() {
		return "pagoda";
	}

	/** Pagodas are rank C/D (DESIGN 21.5): the player's glaze, or always Giok when glazed. */
	private RoofMat mat(Ctx ctx) {
		return glazed ? Glaze.GIOK.roof() : ctx.roof(Rank.D);
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int floor0 = ctx.level(level);
		sink.pushFacing(x, floor0, z, entry);
		RoofMat mat = mat(ctx);
		Block wall = Palette.PLASTER;
		Block lattice = glazed ? Palette.jade(Blocks.COPPER_TRAPDOOR) : Blocks.MANGROVE_TRAPDOOR;
		int base = cores[0] + plinthMargin;
		// Plinth with steps on the entry side.
		sink.phase(Phase.PLINTH);
		for (int dx = -base; dx <= base; dx++) {
			for (int dz = -base; dz <= base; dz++) {
				if (!inside(dx, dz, base, octagonal)) {
					continue;
				}
				for (int py = -1; py < plinth - 1; py++) {
					boolean edge = !inside(dx, dz, base - 1, octagonal);
					sink.set(dx, py, dz, Palette.st(py == plinth - 2 ? Palette.PLINTH : edge ? Palette.STONE : Palette.FILL_STONE));
				}
			}
		}
		for (int i = 0; i < plinth - 1; i++) {
			for (int sx = -1; sx <= 1; sx++) {
				sink.set(sx, plinth - 2 - i, base + 1 + i, Palette.stair(Palette.STONE_STAIRS, Direction.NORTH, false));
			}
		}
		int y = plinth - 1;
		RoofBuilder lastSkirt = null;
		for (int f = 0; f < cores.length; f++) {
			int c = cores[f];
			int h = heights[f];
			int chamfer = octagonal ? Math.max(1, (c + 1) / 2) : 0;
			// Core: cinnabar corner posts, plaster walls, lattice in the middle of each side, a door on floor 1.
			sink.phase(Phase.FRAME);
			for (int dx = -c; dx <= c; dx++) {
				for (int dz = -c; dz <= c; dz++) {
					if (!inside(dx, dz, c, octagonal, chamfer)) {
						continue;
					}
					boolean ring = !inside(dx, dz, c - 1, octagonal, Math.max(0, chamfer - 1));
					if (!ring) {
						sink.set(dx, y - 1, dz, Palette.st(f == 0 ? Palette.FLOOR : Palette.WOOD));
						continue;
					}
					boolean corner = isCorner(dx, dz, c, octagonal, chamfer);
					for (int py = 0; py < h; py++) {
						if (corner) {
							sink.phase(Phase.FRAME).set(dx, y + py, dz, Palette.st(py == 0 ? Palette.PILLAR_BASE : Palette.PILLAR));
							continue;
						}
						sink.phase(Phase.WALL);
						Direction side = Math.abs(dz) >= Math.abs(dx) ? (dz > 0 ? Direction.SOUTH : Direction.NORTH) : (dx > 0 ? Direction.EAST : Direction.WEST);
						int along = side.getAxis() == Direction.Axis.Z ? dx : dz;
						boolean middle = Math.abs(along) <= 1;
						if (f == 0 && side == Direction.SOUTH && middle && py <= 2) {
							sink.air(dx, y + py, dz);
						} else if (middle && py >= 1 && py <= h - 2) {
							sink.set(dx, y + py, dz, Palette.lattice(lattice, side));
						} else {
							sink.set(dx, y + py, dz, Palette.st(py == 0 ? Palette.PLINTH : wall));
						}
					}
					sink.phase(Phase.FRAME).set(dx, y + h, dz, Palette.st(Palette.BEAM));
				}
			}
			// Light inside every floor.
			sink.phase(Phase.INTERIOR);
			sink.set(0, y + h - 1, 0, Palette.lantern(Blocks.LANTERN, true));
			sink.set(0, y + h, 0, Palette.ironChain());
			// Skirt eave around this floor (all +1, last +2); overhang alternates 3 and 2.
			int o = overhang(f);
			if (f < cores.length - 1) {
				int skirtBase = y + h - 1;
				RoofBuilder skirt = new RoofBuilder(RoofBuilder.Type.SKIRT, -c - o, c + o, -c - o, c + o, skirtBase, mat).inner(-c, c, -c, c)
						.overhang(o).wall(-c, c, -c, c);
				if (octagonal) {
					skirt.octagon(chamfer + o, chamfer);
				}
				skirt.emit(sink);
				lastSkirt = skirt;
				// Balcony railing where the next core steps in.
				int next = cores[f + 1];
				sink.phase(Phase.ORNAMENT);
				if (next < c) {
					sink.phase(Phase.FRAME);
					for (int dx = -c + 1; dx <= c - 1; dx++) {
						for (int dz = -c + 1; dz <= c - 1; dz++) {
							if (inside(dx, dz, c - 1, octagonal, Math.max(0, chamfer - 1))) {
								sink.set(dx, y + h, dz, Palette.st(Palette.WOOD));
							}
						}
					}
					sink.phase(Phase.ORNAMENT);
					for (int dx = -c; dx <= c; dx++) {
						for (int dz = -c; dz <= c; dz++) {
							if (inside(dx, dz, c, octagonal, chamfer) && !inside(dx, dz, c - 1, octagonal, Math.max(0, chamfer - 1))) {
								sink.set(dx, y + h + 1, dz, glazed ? Palette.st(Palette.jade(Blocks.COPPER_BARS)) : Palette.st(Palette.FENCE));
							}
						}
					}
				}
				y += h + 1;
			} else {
				// The top roof: a steep pyramid (or octagon) with the spire.
				int outer = c + o;
				RoofBuilder.Type type = octagonal ? RoofBuilder.Type.OCTAGON : RoofBuilder.Type.PYRAMID;
				RoofBuilder roof = new RoofBuilder(type, -outer, outer, -outer, outer, y + h - 1, mat).overhang(o).wall(-c, c, -c, c)
						.profile(RoofBuilder.Profile.PAVILION).chamfer(chamfer + o).ridgeLight(true);
				roof.emit(sink);
				int apex = roof.ridgeTop();
				sink.phase(Phase.ORNAMENT);
				sink.set(0, apex + 1, 0, Palette.st(Blocks.DEEPSLATE_TILE_WALL));
				if (spire) {
					sink.set(0, apex + 2, 0, Palette.st(Blocks.DEEPSLATE_TILE_WALL));
					sink.set(0, apex + 3, 0, Palette.st(Palette.GOLD));
					sink.set(0, apex + 4, 0, Palette.ironChain());
					sink.set(0, apex + 5, 0, Palette.ironChain());
					sink.set(0, apex + 6, 0, Palette.facing(Blocks.END_ROD, Direction.UP));
				} else {
					// The pavilion finial: a short tile neck and the gold pearl.
					sink.set(0, apex + 2, 0, Palette.st(Palette.GOLD));
				}
				lastSkirt = roof;
			}
		}
		sink.pop();
	}

	@Override
	public void decorate(Ctx ctx, Sink sink, int tier) {
		if (tier != 4 && tier != 9) {
			return;
		}
		int floor0 = ctx.level(level);
		sink.pushFacing(x, floor0, z, entry);
		RoofMat mat = mat(ctx);
		int y = plinth - 1;
		for (int f = 0; f < cores.length - 1; f++) {
			int c = cores[f];
			int h = heights[f];
			int o = overhang(f);
			int chamfer = octagonal ? Math.max(1, (c + 1) / 2) : 0;
			RoofBuilder skirt = new RoofBuilder(RoofBuilder.Type.SKIRT, -c - o, c + o, -c - o, c + o, y + h - 1, mat).inner(-c, c, -c, c).overhang(o);
			if (octagonal) {
				skirt.octagon(chamfer + o, chamfer);
			}
			// Every floor has a lantern under every corner tip (21.7); the T4 ornament hangs them.
			if (tier == 4) {
				skirt.windBells(sink);
			}
			y += h + 1;
		}
		sink.pop();
	}

	private static boolean inside(int dx, int dz, int h, boolean octagonal) {
		return inside(dx, dz, h, octagonal, octagonal ? Math.max(1, (h + 1) / 2) : 0);
	}

	private static boolean inside(int dx, int dz, int h, boolean octagonal, int chamfer) {
		if (h < 0 || Math.abs(dx) > h || Math.abs(dz) > h) {
			return false;
		}
		if (!octagonal || chamfer <= 0) {
			return true;
		}
		return (h - Math.abs(dx)) + (h - Math.abs(dz)) >= chamfer;
	}

	private static boolean isCorner(int dx, int dz, int h, boolean octagonal, int chamfer) {
		if (!octagonal) {
			return Math.abs(dx) == h && Math.abs(dz) == h;
		}
		int a = h - Math.abs(dx);
		int b = h - Math.abs(dz);
		return a + b == chamfer && (a == 0 || b == 0);
	}
}
