package dev.kultivator.sect.build.blueprint.templates.t7t9;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import dev.kultivator.sect.build.blueprint.TerrainView;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * An irregular sealed lake (DESIGN 21.8 "Water", 21.10 "Fluids") for the big valley templates: the water is the union of
 * several {@link #lobe ellipses} minus round {@link #island}s, held in one basin of the plan's own (a clay floor, a sealed
 * rim of mud bricks and stone on every side), so containment is proven at emission and the shared {@code Pond}'s single
 * ellipse is not enough (overlapping ponds would wall each other off). Rock banks of taihu stones with flowering azaleas and
 * ferns; lotus on waterlogged stems, lily pads, seagrass and sea-pickle glow, sparse enough to leave open water. The surface
 * sits 1 below the path ({@code level}): water at L-2 and L-3, clay at L-4. Water goes in last (phase WATER) as static
 * sources. Template-local.
 */
public final class Lake implements Part {

	private record Lobe(int cx, int cz, int rx, int rz) {
	}

	private record Isle(int cx, int cz, int r) {
	}

	private final String salt;
	private final Lv level;
	private final List<Lobe> lobes = new ArrayList<>();
	private final List<Isle> isles = new ArrayList<>();
	private final List<int[]> dry = new ArrayList<>();
	private final List<int[]> channels = new ArrayList<>();
	private int lotus = 4;

	public Lake(String salt, Lv level) {
		this.salt = salt;
		this.level = level;
	}

	/** An elliptical lobe of water centred on (cx, cz) with radii rx × rz. */
	public Lake lobe(int cx, int cz, int rx, int rz) {
		lobes.add(new Lobe(cx, cz, Math.max(1, rx), Math.max(1, rz)));
		return this;
	}

	/** A round island of radius r (dry land at the path level, rock-banked) inside the water. */
	public Lake island(int cx, int cz, int r) {
		isles.add(new Isle(cx, cz, Math.max(1, r)));
		return this;
	}

	/** A rectangle of water x0..x1 × z0..z1 (a straight channel, the water under a bridge), joined to the lobes. */
	public Lake channel(int x0, int z0, int x1, int z1) {
		channels.add(new int[]{Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)});
		return this;
	}

	/** A rectangle kept dry (a landing, a pier foot). */
	public Lake dry(int x0, int z0, int x1, int z1) {
		dry.add(new int[]{Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)});
		return this;
	}

	/** Lotus density (percent of inner water cells, default 4). */
	public Lake lotus(int percent) {
		this.lotus = Math.max(0, Math.min(30, percent));
		return this;
	}

	/** True when (x, z) is open water of this lake. */
	public boolean isWater(int x, int z) {
		for (int[] box : dry) {
			if (x >= box[0] && x <= box[2] && z >= box[1] && z <= box[3]) {
				return false;
			}
		}
		for (Isle isle : isles) {
			int dx = x - isle.cx;
			int dz = z - isle.cz;
			if (dx * dx + dz * dz <= isle.r * isle.r + isle.r) {
				return false;
			}
		}
		for (int[] box : channels) {
			if (x >= box[0] && x <= box[2] && z >= box[1] && z <= box[3]) {
				return true;
			}
		}
		for (Lobe lobe : lobes) {
			double ex = (x - lobe.cx) / (lobe.rx + 0.5D);
			double ez = (z - lobe.cz) / (lobe.rz + 0.5D);
			if (ex * ex + ez * ez <= 1.0D) {
				return true;
			}
		}
		return false;
	}

	private boolean onIsle(int x, int z) {
		for (Isle isle : isles) {
			int dx = x - isle.cx;
			int dz = z - isle.cz;
			if (dx * dx + dz * dz <= isle.r * isle.r + isle.r) {
				return true;
			}
		}
		return false;
	}

	private int[] extent() {
		int x0 = Integer.MAX_VALUE;
		int z0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		int z1 = Integer.MIN_VALUE;
		for (Lobe lobe : lobes) {
			x0 = Math.min(x0, lobe.cx - lobe.rx - 1);
			x1 = Math.max(x1, lobe.cx + lobe.rx + 1);
			z0 = Math.min(z0, lobe.cz - lobe.rz - 1);
			z1 = Math.max(z1, lobe.cz + lobe.rz + 1);
		}
		for (int[] box : channels) {
			x0 = Math.min(x0, box[0] - 1);
			x1 = Math.max(x1, box[2] + 1);
			z0 = Math.min(z0, box[1] - 1);
			z1 = Math.max(z1, box[3] + 1);
		}
		return x0 == Integer.MAX_VALUE ? new int[]{0, 0, 0, 0} : new int[]{x0, z0, x1, z1};
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		int[] e = extent();
		return new BoundingBox(e[0], y - 5, e[1], e[2], y + 1, e[3]);
	}

	@Override
	public String kind() {
		return "lake";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int y = ctx.level(level);
		int surface = y - 2;
		int floor = surface - 2;
		Random random = ctx.random("lake" + salt);
		TerrainView terrain = ctx.terrain();
		int[] e = extent();
		Phase previous = sink.phase();
		for (int x = e[0]; x <= e[2]; x++) {
			for (int z = e[1]; z <= e[3]; z++) {
				boolean wet = isWater(x, z);
				boolean rim = !wet && (isWater(x + 1, z) || isWater(x - 1, z) || isWater(x, z + 1) || isWater(x, z - 1) || isWater(x + 1, z + 1)
						|| isWater(x - 1, z - 1) || isWater(x + 1, z - 1) || isWater(x - 1, z + 1));
				boolean isle = !wet && onIsle(x, z);
				if (!wet && !rim && !isle) {
					continue;
				}
				int ground = terrain.ground(x, z);
				sink.phase(Phase.CLEAR);
				for (int cy = Math.max(ground + 1, y); cy <= Math.min(terrain.top(x, z), y + 8); cy++) {
					sink.air(x, cy, z);
				}
				sink.phase(Phase.CUT);
				for (int cy = y; cy <= Math.min(ground, y + 6); cy++) {
					sink.air(x, cy, z);
				}
				if (wet) {
					for (int cy = Math.min(ground, y - 1); cy > floor; cy--) {
						sink.air(x, cy, z);
					}
					sink.phase(Phase.SHELL);
					sink.set(x, floor, z, Palette.st(Blocks.CLAY));
					sink.set(x, floor - 1, z, Palette.st(Palette.FILL_STONE));
					for (int cy = Math.max(ground + 1, floor - 6); cy < floor - 1; cy++) {
						sink.set(x, cy, z, Palette.st(Palette.FILL_STONE));
					}
					continue;
				}
				if (rim) {
					// The sealed rim: mud bricks from under the clay up to just below the bank top.
					sink.phase(Phase.SHELL);
					for (int cy = Math.max(ground + 1, floor - 5); cy < floor - 1; cy++) {
						sink.set(x, cy, z, Palette.st(Palette.FILL_STONE));
					}
					for (int cy = floor - 1; cy <= y - 2; cy++) {
						sink.set(x, cy, z, Palette.st(random.nextInt(5) == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.MUD_BRICKS));
					}
				} else {
					// Island core: fill up to the bank when the ground is lower.
					sink.phase(Phase.FILL);
					for (int cy = Math.max(ground + 1, floor - 5); cy <= y - 2; cy++) {
						sink.set(x, cy, z, Palette.st(cy >= y - 3 ? Palette.FILL_EARTH : Palette.FILL_STONE));
					}
				}
				sink.phase(Phase.PLINTH);
				sink.set(x, y - 1, z, Palette.st(rim && random.nextInt(3) == 0 ? Palette.MOSS : Blocks.GRASS_BLOCK));
				if (rim) {
					sink.phase(Phase.GARDEN);
					int roll = random.nextInt(12);
					if (roll < 2) {
						sink.set(x, y, z, Palette.st(roll == 0 ? Blocks.CALCITE : Blocks.TUFF));
						if (random.nextInt(3) == 0) {
							sink.set(x, y + 1, z, Palette.st(random.nextBoolean() ? Blocks.ANDESITE : Blocks.DIORITE));
						}
					} else if (roll == 2) {
						sink.set(x, y, z, Palette.st(Blocks.FLOWERING_AZALEA));
					} else if (roll == 3 || roll == 4) {
						sink.set(x, y, z, Palette.st(Blocks.FERN));
					} else if (roll == 5) {
						sink.set(x, y, z, Palette.st(Blocks.MOSSY_COBBLESTONE));
					}
				}
			}
		}
		// Plants, then the water itself, then lily pads.
		for (int x = e[0]; x <= e[2]; x++) {
			for (int z = e[1]; z <= e[3]; z++) {
				if (!isWater(x, z)) {
					continue;
				}
				boolean inner = isWater(x + 1, z) && isWater(x - 1, z) && isWater(x, z + 1) && isWater(x, z - 1);
				int roll = random.nextInt(100);
				sink.phase(Phase.GARDEN);
				if (roll < lotus && inner) {
					Direction facing = Direction.from2DDataValue(random.nextInt(4));
					sink.set(x, floor + 1, z, Palette.dripleafStem(facing, true));
					sink.set(x, floor + 2, z, Palette.dripleafStem(facing, true));
					sink.set(x, surface + 1, z, Palette.dripleaf(facing, false));
					continue;
				}
				if (roll < lotus + 6) {
					sink.set(x, floor + 1, z, Palette.st(Blocks.SEAGRASS));
				} else if (roll < lotus + 8) {
					sink.set(x, floor + 1, z, Palette.seaPickles(1 + random.nextInt(3)));
				}
				sink.phase(Phase.WATER);
				for (int wy = floor + 1; wy <= surface; wy++) {
					if (sink.get(x, wy, z) == null || sink.get(x, wy, z).isAir()) {
						sink.set(x, wy, z, Palette.st(Blocks.WATER));
					}
				}
				if (roll >= 90 && roll < 90 + Math.max(2, lotus)) {
					sink.set(x, surface + 1, z, Palette.st(Blocks.LILY_PAD));
				}
			}
		}
		sink.phase(previous);
	}
}
