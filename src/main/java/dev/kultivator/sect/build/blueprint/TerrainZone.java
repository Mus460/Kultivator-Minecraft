package dev.kultivator.sect.build.blueprint;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The earthwork of one plot (DESIGN 21.9 {@code TerrainZone}, 21.10): a rectangle levelled to a standing level, owned by
 * the component whose plot it is (a terrace belongs to the component of its first building, DESIGN 21.2 rule 2).
 * <ul>
 * <li>CLEAR: plants, snow and trees above the plot up to their top (skipped on natural tree columns when
 * {@link #keepNature}: garden and lawn plots keep their trees).</li>
 * <li>CUT, layers top-down: natural ground above the level becomes air (the engine credits it and seals fluids first).</li>
 * <li>FILL, layers bottom-up: dirt up to the floor, stone deeper than 3; columns needing more than 16 are capped 6 below
 * the floor with a 3-block stone cap over the void (caves, ravines).</li>
 * <li>The floor cell gets the {@link Surface} (grass, moss, paving, dirt, or left alone).</li>
 * <li>Edges ({@link Edge}): RETAINING builds stone-brick retaining walls where the outside is 2+ lower (with a chiseled
 * tuff fret, a pilaster every 7 and an andesite coping; MARBLE style: diorite face, quartz fret and deck) and holds back
 * higher ground on the inside ring (at most 4 high, mossy); SLOPE finishes the edge with moss; NONE leaves it.</li>
 * </ul>
 * Positions are template-local (no frame). Natural blocks it moves are credited, never lost (the engine).
 */
public final class TerrainZone implements Part {

	public enum Edge {
		RETAINING,
		SLOPE,
		NONE
	}

	public enum Surface {
		GRASS,
		MOSS,
		PAVE,
		DIRT,
		NONE
	}

	/** Retaining walls taller than this step back (DESIGN 21.8: at most 4 exposed). */
	public static final int MAX_RETAINING = 4;
	/** Deeper fill is capped (DESIGN 21.10 cliffs and voids). */
	public static final int MAX_FILL = 16;

	private final int x0;
	private final int z0;
	private final int x1;
	private final int z1;
	private final Lv level;
	private Edge edge = Edge.RETAINING;
	private Surface surface = Surface.GRASS;
	private boolean keepNature;
	private boolean marble;
	private int clearHeight = 24;

	/** The rectangle (inclusive, template-local) levelled to {@code level}. */
	public TerrainZone(int x0, int z0, int x1, int z1, Lv level) {
		this.x0 = Math.min(x0, x1);
		this.z0 = Math.min(z0, z1);
		this.x1 = Math.max(x0, x1);
		this.z1 = Math.max(z0, z1);
		this.level = level;
	}

	public TerrainZone edge(Edge edge) {
		this.edge = edge;
		return this;
	}

	public TerrainZone surface(Surface surface) {
		this.surface = surface;
		return this;
	}

	/** Keeps natural trees standing (garden and lawn plots, DESIGN 21.2 rule 6). */
	public TerrainZone keepNature(boolean keep) {
		this.keepNature = keep;
		return this;
	}

	/** Marble retaining walls (polished diorite face, chiseled quartz fret, smooth quartz coping). */
	public TerrainZone marble(boolean marble) {
		this.marble = marble;
		return this;
	}

	/** How far above the level trees and plants are cleared (default 24). */
	public TerrainZone clearHeight(int height) {
		this.clearHeight = Math.max(0, height);
		return this;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		return new BoundingBox(x0, y - 1 - MAX_RETAINING, z0, x1, y, z1);
	}

	@Override
	public String kind() {
		return "terrain";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int level = ctx.level(this.level);
		TerrainView terrain = ctx.terrain();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int ground = terrain.ground(x, z);
				int top = terrain.top(x, z);
				boolean tree = (terrain.flags(x, z) & TerrainView.TREE) != 0;
				// CLEAR: everything above the new floor that is not ground (trees, plants, snow, water above).
				if (!(keepNature && tree)) {
					sink.phase(Phase.CLEAR);
					for (int y = Math.max(ground + 1, level); y <= Math.min(top, level + clearHeight); y++) {
						sink.air(x, y, z);
					}
				}
				// CUT: natural ground above the floor.
				sink.phase(Phase.CUT);
				for (int y = level; y <= ground; y++) {
					sink.air(x, y, z);
				}
				// FILL: up to the floor.
				sink.phase(Phase.FILL);
				int need = level - 1 - ground;
				if (need > MAX_FILL) {
					int capTop = level - 2;
					int capBottom = level - 7;
					for (int y = capBottom; y <= capTop; y++) {
						sink.set(x, y, z, Palette.st(y <= capBottom + 2 ? Palette.FILL_STONE : Palette.FILL_EARTH));
					}
				} else {
					for (int y = ground + 1; y <= level - 2; y++) {
						sink.set(x, y, z, Palette.st(y < level - 4 ? Palette.FILL_STONE : Palette.FILL_EARTH));
					}
				}
				BlockState floor = switch (surface) {
					case GRASS -> Palette.st(Blocks.GRASS_BLOCK);
					case MOSS -> Palette.st(Palette.MOSS);
					case PAVE -> Palette.st(Palette.PLINTH);
					case DIRT -> Palette.st(Blocks.DIRT);
					case NONE -> ground >= level - 1 ? null : Palette.st(Blocks.GRASS_BLOCK);
				};
				if (floor != null) {
					sink.set(x, level - 1, z, floor);
				}
			}
		}
		if (edge != Edge.NONE) {
			edges(ctx, sink, level, terrain);
		}
	}

	/**
	 * The column rectangles other parts of the template build on (their bounds; walls by their lines, ornaments ignored):
	 * a SLOPE ramp only grades free land between plots, never another part's ground.
	 */
	private java.util.List<Object> occupied(Ctx ctx) {
		java.util.List<Object> boxes = new java.util.ArrayList<>();
		for (Component component : ctx.spec().components()) {
			for (Part part : component.parts()) {
				if (part == this || part instanceof Ornament) {
					continue;
				}
				if (part instanceof Wall wall) {
					boxes.add(wall);
					continue;
				}
				BoundingBox box = part.bounds(ctx);
				if (box != null) {
					boxes.add(box);
				}
			}
		}
		return boxes;
	}

	private static boolean free(Ctx ctx, java.util.List<Object> boxes, int x, int z) {
		TemplateSpec spec = ctx.spec();
		if (x <= spec.x0() + 1 || x >= spec.x1() - 1 || z <= spec.z0() + 1 || z >= spec.z1() - 1) {
			return false;
		}
		for (Object each : boxes) {
			if (each instanceof BoundingBox box) {
				if (x >= box.minX() - 1 && x <= box.maxX() + 1 && z >= box.minZ() - 1 && z <= box.maxZ() + 1) {
					return false;
				}
			} else if (each instanceof Wall wall && wall.near(x, z, 1)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * SLOPE (DESIGN 21.10 "Landscape margin", 21.8 "never leave a bare dirt face"): where the outside ground differs by
	 * 1-4, a 1:1 ramp grades the free land beyond the edge (filled with earth under grass and moss, or cut back), with a
	 * mossy rock now and then; a taller drop falls back to the retaining wall. Natural tree columns are kept.
	 */
	private boolean slope(Ctx ctx, Sink sink, java.util.List<Object> boxes, TerrainView terrain, int level, int x, int z, int nx, int nz,
			int outside, int count) {
		int drop = outside - (level - 1);
		if (drop == 0) {
			return true;
		}
		if (Math.abs(drop) > MAX_RETAINING) {
			return false;
		}
		sink.phase(Phase.PLINTH);
		sink.set(x, level - 1, z, Palette.st(Palette.MOSS));
		if (nx != 0 && nz != 0) {
			return true;
		}
		for (int d = 1; d <= Math.abs(drop); d++) {
			int cx = x + nx * d;
			int cz = z + nz * d;
			if (!terrain.known(cx, cz) || (terrain.flags(cx, cz) & TerrainView.TREE) != 0 || !free(ctx, boxes, cx, cz)) {
				break;
			}
			int ground = terrain.ground(cx, cz);
			BlockState top = Palette.st((cx + cz + count) % 3 == 0 ? Palette.MOSS : Blocks.GRASS_BLOCK);
			if (drop < 0) {
				int target = level - 1 - d;
				if (ground >= target) {
					break;
				}
				sink.phase(Phase.FILL);
				for (int y = ground + 1; y < target; y++) {
					sink.set(cx, y, cz, Palette.st(Palette.FILL_EARTH));
				}
				sink.phase(Phase.PLINTH);
				sink.set(cx, target, cz, top);
				if (d == 1 && count % 6 == 0) {
					sink.phase(Phase.GARDEN);
					sink.set(cx, target + 1, cz, Palette.st(Blocks.MOSSY_COBBLESTONE));
				}
			} else {
				int target = level - 1 + d;
				if (ground <= target) {
					break;
				}
				sink.phase(Phase.CLEAR);
				for (int y = ground + 1; y <= Math.min(terrain.top(cx, cz), ground + 3); y++) {
					sink.air(cx, y, cz);
				}
				sink.phase(Phase.CUT);
				for (int y = target + 1; y <= ground; y++) {
					sink.air(cx, y, cz);
				}
				sink.phase(Phase.PLINTH);
				sink.set(cx, target, cz, top);
			}
		}
		return true;
	}

	private void edges(Ctx ctx, Sink sink, int level, TerrainView terrain) {
		sink.phase(Phase.PLINTH);
		int count = 0;
		java.util.List<Object> boxes = edge == Edge.SLOPE ? occupied(ctx) : java.util.List.of();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean border = x == x0 || x == x1 || z == z0 || z == z1;
				if (!border) {
					continue;
				}
				count++;
				int outX = x == x0 ? x - 1 : x == x1 ? x + 1 : x;
				int outZ = z == z0 ? z - 1 : z == z1 ? z + 1 : z;
				if (!terrain.known(outX, outZ)) {
					continue;
				}
				int outside = terrain.ground(outX, outZ);
				if (edge == Edge.SLOPE && slope(ctx, sink, boxes, terrain, level, x, z, outX - x, outZ - z, outside, count)) {
					continue;
				}
				sink.phase(Phase.PLINTH);
				if (outside <= level - 3) {
					// The outside is lower: a retaining wall face down to the outside ground (stepped at most 4).
					int foot = Math.max(outside + 1, level - 1 - MAX_RETAINING);
					for (int y = foot; y <= level - 2; y++) {
						boolean fret = y == foot + 1 && level - 2 - foot >= 2;
						BlockState face;
						if (count % 7 == 0) {
							face = Palette.st(marble ? Palette.MARBLE_FRET : Palette.STONE_PILASTER);
						} else if (fret) {
							face = Palette.st(marble ? Palette.MARBLE_FRET : Palette.STONE_FRET);
						} else {
							face = Palette.st(marble ? Palette.MARBLE_FACE : Palette.STONE);
						}
						sink.set(x, y, z, face);
					}
					for (int y = outside + 1; y < foot; y++) {
						sink.set(x, y, z, Palette.st(Palette.FILL_STONE));
					}
					sink.set(x, level - 1, z, Palette.st(marble ? Palette.MARBLE_DECK : Palette.PLINTH));
				} else if (outside >= level) {
					// The outside is higher: hold it back on the inside ring (mossy stone bricks, moss on top).
					int top = Math.min(outside, level - 1 + MAX_RETAINING);
					for (int y = level; y <= top; y++) {
						sink.set(x, y, z, Palette.st((x + z + y) % 5 == 0 ? Palette.STONE_MOSSY : Palette.STONE));
					}
					if (top >= level) {
						sink.set(x, top + 1 <= outside ? top : top, z, Palette.st(Palette.STONE_MOSSY));
					}
				}
			}
		}
	}

	public int x0() {
		return x0;
	}

	public int z0() {
		return z0;
	}

	public int x1() {
		return x1;
	}

	public int z1() {
		return z1;
	}

	public Lv level() {
		return level;
	}
}
