package dev.kultivator.sect.build.blueprint.templates.t7t9;

import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import dev.kultivator.sect.build.blueprint.StairFlight;
import dev.kultivator.sect.build.blueprint.Terrace;
import dev.kultivator.sect.build.blueprint.TerrainView;
import dev.kultivator.sect.build.blueprint.Tree;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A sculpted, terraced mountain for templates 7-9 (DESIGN 21.8 rule 5, 21.4 "keeps natural terrain"): the union of
 * radial {@link #blob}s (rounded summits with seeded noise) and flat building {@link #pad}s whose edges drop as dressed
 * retaining walls (at most 4 exposed, DESIGN 21.8) before a natural slope runs down to the {@code base} level. Cliff faces
 * are striped in stone, andesite, tuff and calcite (or rough taihu rock for {@link Style#ROCKERY}), gentle ledges carry
 * grass, ferns, azaleas, rocks and Huangshan pines. {@link #stair} flights are carved into the slope (the mountain never
 * buries them), {@link #cave}s are hollowed after the fill. Natural ground higher than the design is kept except under pads
 * and stairs, which are cut flat. Template-local; read-only on {@link Ctx} (thread-safe emission).
 * <p>
 * Art direction from real mountain sects (Huangshan, Wudang), not novel canon.
 */
public final class Massif implements Part {

	/** Surface and face style. */
	public enum Style {
		/** Grass and moss over striped cliff strata (the sect peaks). */
		MOUNTAIN,
		/** Rough taihu-rock rockery with holes, ferns and azaleas (the grotto hill, the imperial rockery). */
		ROCKERY
	}

	/** Pad edge and deck styles. */
	public enum Pad {
		/** Stone-brick retaining faces, andesite coping, stone balustrade, paved deck. */
		STONE,
		/** Polished diorite faces, quartz fret, smooth quartz deck, pale-oak and quartz balustrade. */
		MARBLE,
		/** No dressed edge: a lawn shelf whose slope starts at the edge. */
		NATURAL
	}

	private static final Block[] STRATA = {Blocks.STONE, Blocks.ANDESITE, Blocks.TUFF, Blocks.STONE, Blocks.CALCITE, Blocks.ANDESITE, Blocks.STONE};
	private static final Block[] TAIHU = {Blocks.CALCITE, Blocks.TUFF, Blocks.DIORITE, Blocks.ANDESITE, Blocks.MOSSY_COBBLESTONE, Blocks.CALCITE};

	private record Blob(int x, int z, int r, Lv top, double rough) {
	}

	private record PadRect(int x0, int z0, int x1, int z1, Lv level, Pad style, boolean paved, int skirt) {
		boolean contains(int x, int z) {
			return x >= x0 && x <= x1 && z >= z0 && z <= z1;
		}
	}

	private record Flight(int x, int z, Direction toward, Lv from, Lv to, int width, Terrace.Style style) {
	}

	private record Cave(int x0, int z0, int x1, int z1, Lv floor, int height) {
	}

	private final String salt;
	private final Lv base;
	private Style style = Style.MOUNTAIN;
	private final List<Blob> blobs = new ArrayList<>();
	private final List<PadRect> pads = new ArrayList<>();
	private final List<Flight> flights = new ArrayList<>();
	private final List<Cave> caves = new ArrayList<>();
	private final List<int[]> openings = new ArrayList<>();
	private final List<int[]> clears = new ArrayList<>();
	private final List<Lake> avoid = new ArrayList<>();
	private int pines = 8;
	private int slope = 2;

	/** A massif rising from standing level {@code base}; {@code salt} keeps its noise stable (use the component id). */
	public Massif(String salt, Lv base) {
		this.salt = salt;
		this.base = base;
	}

	public Massif style(Style style) {
		this.style = style;
		return this;
	}

	/** A rounded summit of radius {@code r} at (x, z) rising to standing level {@code top}; {@code rough} 0-1 noise. */
	public Massif blob(int x, int z, int r, Lv top, double rough) {
		blobs.add(new Blob(x, z, Math.max(3, r), top, Math.max(0.0D, rough)));
		return this;
	}

	/** A flat pad x0..x1 × z0..z1 (inclusive) at standing level {@code level}; its slope skirt is about 2 × its height. */
	public Massif pad(int x0, int z0, int x1, int z1, Lv level, Pad style) {
		pads.add(new PadRect(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), level, style, style != Pad.NATURAL, -1));
		return this;
	}

	/** A pad whose deck is left as lawn (true) or paved/marble (false) whatever its edge style. */
	public Massif pad(int x0, int z0, int x1, int z1, Lv level, Pad style, boolean lawn, int skirt) {
		pads.add(new PadRect(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), level, style, !lawn, skirt));
		return this;
	}

	/** A stair flight carved into the slope: foot at (x, z) on {@code from}, climbing {@code toward} up to {@code to} (higher). */
	public Massif stair(int x, int z, Direction toward, Lv from, Lv to, int width, Terrace.Style style) {
		flights.add(new Flight(x, z, toward, from, to, width | 1, style));
		return this;
	}

	/** Air hollowed inside the mountain after the fill (a grotto chamber or tunnel), {@code height} high from {@code floor}. */
	public Massif cave(int x0, int z0, int x1, int z1, Lv floor, int height) {
		caves.add(new Cave(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), floor, Math.max(2, height)));
		return this;
	}

	/** No balustrade on pad edges within {@code half} of (x, z) (a path or bridge arrives there). */
	public Massif opening(int x, int z, int half) {
		openings.add(new int[]{x, z, half});
		return this;
	}

	/** Keeps the rectangle at the base level (another part builds there). */
	public Massif clear(int x0, int z0, int x1, int z1) {
		clears.add(new int[]{Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)});
		return this;
	}

	/** Never rises over the water of {@code lake} (built by an earlier component): cliffs end at its bank. */
	public Massif avoid(Lake lake) {
		if (lake != null) {
			avoid.add(lake);
		}
		return this;
	}

	private boolean nearWater(int x, int z) {
		for (Lake lake : avoid) {
			if (lake.isWater(x, z)) {
				return true;
			}
		}
		return false;
	}

	public Massif pines(int count) {
		this.pines = Math.max(0, count);
		return this;
	}

	/** Horizontal run per block of drop on the natural slopes (1 = steep cliffs, 3 = gentle hills; default 2). */
	public Massif slope(int run) {
		this.slope = Math.max(1, run);
		return this;
	}

	// ================================================================== geometry

	private int[] extent(Ctx ctx) {
		int b = ctx.level(base);
		int x0 = Integer.MAX_VALUE;
		int z0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		int z1 = Integer.MIN_VALUE;
		int top = b;
		for (Blob blob : blobs) {
			x0 = Math.min(x0, blob.x - blob.r);
			x1 = Math.max(x1, blob.x + blob.r);
			z0 = Math.min(z0, blob.z - blob.r);
			z1 = Math.max(z1, blob.z + blob.r);
			top = Math.max(top, ctx.level(blob.top));
		}
		for (PadRect pad : pads) {
			int skirt = skirt(ctx, pad);
			x0 = Math.min(x0, pad.x0 - skirt);
			x1 = Math.max(x1, pad.x1 + skirt);
			z0 = Math.min(z0, pad.z0 - skirt);
			z1 = Math.max(z1, pad.z1 + skirt);
			top = Math.max(top, ctx.level(pad.level));
		}
		if (x0 == Integer.MAX_VALUE) {
			return new int[]{0, 0, 0, 0, b};
		}
		return new int[]{x0, z0, x1, z1, top};
	}

	private int skirt(Ctx ctx, PadRect pad) {
		if (pad.skirt >= 0) {
			return pad.skirt;
		}
		int rise = Math.max(0, ctx.level(pad.level) - ctx.level(base));
		int retained = pad.style == Pad.NATURAL ? 0 : Math.min(4, rise);
		return Math.max(2, (rise - retained) * slope);
	}

	private double noise(Ctx ctx, int x, int z) {
		long h = ctx.seed() * 0x9E3779B97F4A7C15L ^ salt.hashCode() * 0xC2B2AE3D27D4EB4FL ^ x * 341873128712L ^ z * 132897987541L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return ((h & 0xFFFF) / 65535.0D) - 0.5D;
	}

	/** The designed standing level of column (x, z), or Integer.MIN_VALUE when the massif does not rise there. */
	private int height(Ctx ctx, int x, int z) {
		for (int[] c : clears) {
			if (x >= c[0] && x <= c[2] && z >= c[1] && z <= c[3]) {
				return Integer.MIN_VALUE;
			}
		}
		if (!avoid.isEmpty() && nearWater(x, z)) {
			return Integer.MIN_VALUE;
		}
		int b = ctx.level(base);
		Integer flight = flightLevel(ctx, x, z);
		if (flight != null) {
			return flight;
		}
		double best = Double.NEGATIVE_INFINITY;
		for (PadRect pad : pads) {
			int level = ctx.level(pad.level);
			if (pad.contains(x, z)) {
				best = Math.max(best, level);
				continue;
			}
			int skirt = skirt(ctx, pad);
			double ex = Math.max(Math.max(pad.x0 - x, x - pad.x1), 0);
			double ez = Math.max(Math.max(pad.z0 - z, z - pad.z1), 0);
			double e = Math.sqrt(ex * ex + ez * ez);
			int retained = pad.style == Pad.NATURAL ? 0 : Math.min(4, level - b);
			double startTop = level - retained;
			if (e <= skirt) {
				double t = e / Math.max(1, skirt);
				double h = b + (startTop - b) * Math.pow(1.0D - t, 1.4D) + noise(ctx, x, z) * 1.2D * (1.0D - t);
				best = Math.max(best, h);
			}
		}
		for (Blob blob : blobs) {
			double d = Math.hypot(x - blob.x, z - blob.z) / blob.r;
			if (d >= 1.0D) {
				continue;
			}
			int top = ctx.level(blob.top);
			double falloff = 1.0D - d * d * (3.0D - 2.0D * d);
			double h = b + (top - b) * falloff + noise(ctx, x * 3 + 1, z * 3 - 2) * (1.0D + 3.0D * blob.rough) * (1.0D - d * 0.6D);
			best = Math.max(best, h);
		}
		if (best == Double.NEGATIVE_INFINITY) {
			return Integer.MIN_VALUE;
		}
		int level = (int) Math.round(best);
		return level <= b ? Integer.MIN_VALUE : level;
	}

	/** The standing level of a stair flight cell, or null outside every flight (cheeks included). */
	private Integer flightLevel(Ctx ctx, int x, int z) {
		for (Flight flight : flights) {
			int y0 = ctx.level(flight.from);
			int y1 = ctx.level(flight.to);
			int rise = Math.abs(y1 - y0);
			if (rise == 0) {
				continue;
			}
			int run = rise + Math.max(0, (rise - 1) / 4);
			int along;
			int across;
			switch (flight.toward) {
				case NORTH -> {
					along = flight.z - z;
					across = x - flight.x;
				}
				case SOUTH -> {
					along = z - flight.z;
					across = x - flight.x;
				}
				case EAST -> {
					along = x - flight.x;
					across = z - flight.z;
				}
				default -> {
					along = flight.x - x;
					across = z - flight.z;
				}
			}
			if (along < 0 || along >= run || Math.abs(across) > flight.width / 2 + 1) {
				continue;
			}
			// Steps climb one per cell with a landing cell after every 4 steps (flights always climb from the foot).
			int standing = Math.min(y0, y1) + along + 1 - (along + 1) / 5;
			return Math.min(Math.max(y0, y1), standing);
		}
		return null;
	}

	private boolean padCell(Ctx ctx, int x, int z) {
		for (PadRect pad : pads) {
			if (pad.contains(x, z)) {
				return true;
			}
		}
		return false;
	}

	private PadRect padAt(Ctx ctx, int x, int z) {
		PadRect best = null;
		for (PadRect pad : pads) {
			if (pad.contains(x, z) && (best == null || ctx.level(pad.level) > ctx.level(best.level))) {
				best = pad;
			}
		}
		return best;
	}

	private boolean inOpening(Ctx ctx, int x, int z) {
		for (int[] o : openings) {
			if (Math.abs(x - o[0]) <= o[2] && Math.abs(z - o[1]) <= o[2]) {
				return true;
			}
		}
		// The head and the foot of every stair flight stay open.
		for (Flight flight : flights) {
			int rise = Math.abs(ctx.level(flight.to) - ctx.level(flight.from));
			int run = rise + Math.max(0, (rise - 1) / 4);
			int along = switch (flight.toward) {
				case NORTH -> flight.z - z;
				case SOUTH -> z - flight.z;
				case EAST -> x - flight.x;
				default -> flight.x - x;
			};
			int across = flight.toward.getAxis() == Direction.Axis.Z ? x - flight.x : z - flight.z;
			if ((along >= run - 1 && along <= run + 1 || along >= -2 && along <= 0) && Math.abs(across) <= flight.width / 2 + 1) {
				return true;
			}
		}
		return false;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int[] e = extent(ctx);
		return new BoundingBox(e[0], ctx.level(base) - 2, e[1], e[2], e[4] + 10, e[3]);
	}

	@Override
	public String kind() {
		return "peak";
	}

	// ================================================================== emission

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int[] e = extent(ctx);
		int w = e[2] - e[0] + 1;
		int d = e[3] - e[1] + 1;
		int b = ctx.level(base);
		int[][] h = new int[w + 2][d + 2];
		for (int i = 0; i < w + 2; i++) {
			for (int j = 0; j < d + 2; j++) {
				int level = height(ctx, e[0] - 1 + i, e[1] - 1 + j);
				h[i][j] = level == Integer.MIN_VALUE ? b : level;
			}
		}
		TerrainView terrain = ctx.terrain();
		Random random = ctx.random("massif" + salt);
		Phase previous = sink.phase();
		boolean[][] built = new boolean[w + 2][d + 2];
		// Pass 1: earthwork and surfaces of every column.
		for (int i = 1; i <= w; i++) {
			for (int j = 1; j <= d; j++) {
				int x = e[0] - 1 + i;
				int z = e[1] - 1 + j;
				int level = h[i][j];
				boolean flight = flightLevel(ctx, x, z) != null;
				boolean pad = !flight && padCell(ctx, x, z) && !nearWater(x, z);
				if (level <= b && !pad || !inFootprint(ctx, x, z)) {
					continue;
				}
				int ground = terrain.ground(x, z);
				int low = Math.min(Math.min(h[i - 1][j], h[i + 1][j]), Math.min(h[i][j - 1], h[i][j + 1]));
				if (ground >= level - 1 && !pad && !flight) {
					// Higher natural terrain is kept (only pads and stairs are carved).
					continue;
				}
				built[i][j] = true;
				// CLEAR plants and trees above, CUT natural ground above the new surface.
				sink.phase(Phase.CLEAR);
				for (int y = Math.max(ground + 1, level); y <= Math.min(terrain.top(x, z), level + 16); y++) {
					sink.air(x, y, z);
				}
				sink.phase(Phase.CUT);
				for (int y = level; y <= ground; y++) {
					sink.air(x, y, z);
				}
				// FILL bottom-up: hidden core of stone and earth, visible faces of strata or dressed stone.
				sink.phase(Phase.FILL);
				PadRect owner = pad ? padAt(ctx, x, z) : null;
				boolean dressed = owner != null && owner.style != Pad.NATURAL;
				for (int y = Math.max(ground + 1, level - 26); y <= level - 2; y++) {
					boolean visible = y >= low;
					BlockState state;
					if (!visible) {
						state = Palette.st(y >= level - 3 ? Palette.FILL_EARTH : Palette.FILL_STONE);
					} else if (dressed) {
						state = face(owner.style, x, y, z, level, low);
					} else if (style == Style.ROCKERY) {
						state = Palette.st(TAIHU[Math.floorMod((int) (noise(ctx, x * 7 + y, z * 5 - y) * 1000), TAIHU.length)]);
					} else {
						state = Palette.st(STRATA[Math.floorMod(y / 2 + (int) Math.round(noise(ctx, x, z) * 2), STRATA.length)]);
					}
					sink.set(x, y, z, state);
				}
				// Taihu holes in rough rockery faces (never the top two layers).
				if (style == Style.ROCKERY && !pad && level - low >= 3) {
					for (int y = low; y <= level - 3; y++) {
						if (random.nextInt(6) == 0) {
							sink.air(x, y, z);
						}
					}
				}
				// The surface.
				sink.phase(Phase.PLINTH);
				boolean steep = level - low >= 2;
				BlockState top;
				if (flight) {
					top = Palette.st(Palette.FILL_STONE);
				} else if (pad) {
					top = deck(owner, x, z);
				} else if (style == Style.ROCKERY) {
					top = steep ? Palette.st(TAIHU[random.nextInt(TAIHU.length)]) : Palette.st(random.nextInt(3) == 0 ? Palette.MOSS : Blocks.GRASS_BLOCK);
				} else {
					top = steep ? Palette.st(random.nextInt(3) == 0 ? Blocks.ANDESITE : Palette.MOSS) : Palette.st(Blocks.GRASS_BLOCK);
				}
				sink.set(x, level - 1, z, top);
			}
		}
		// Pass 2: edges and planting (after every column's earthwork, so no clearing overwrites them).
		int planted = 0;
		for (int i = 1; i <= w; i++) {
			for (int j = 1; j <= d; j++) {
				if (!built[i][j]) {
					continue;
				}
				int x = e[0] - 1 + i;
				int z = e[1] - 1 + j;
				int level = h[i][j];
				boolean flight = flightLevel(ctx, x, z) != null;
				boolean pad = !flight && padCell(ctx, x, z) && !nearWater(x, z);
				int low = Math.min(Math.min(h[i - 1][j], h[i + 1][j]), Math.min(h[i][j - 1], h[i][j + 1]));
				boolean steep = level - low >= 2;
				PadRect owner = pad ? padAt(ctx, x, z) : null;
				// Pad edges: coping one out over a drop, balustrade on the edge, planting at the foot.
				if (owner != null && owner.style != Pad.NATURAL) {
					edge(ctx, sink, owner, x, z, level, h, i, j);
				}
				// Slope planting: pines on gentle ledges, ferns, azaleas and rocks.
				if (!pad && !flight && !steep) {
					sink.phase(Phase.GARDEN);
					int roll = random.nextInt(100);
					if (planted < pines && roll < 3 && level - b >= 2 && farFromPads(ctx, x, z, 3)) {
						new Tree(random.nextInt(5) == 0 ? Tree.Kind.MAPLE : Tree.Kind.PINE, x, z, Lv.at(level)).force(true).emit(ctx, sink);
						planted++;
					} else if (roll < 14) {
						sink.setIfAbsent(x, level, z, Palette.st(Blocks.FERN));
					} else if (roll < 18) {
						sink.setIfAbsent(x, level, z, Palette.st(Blocks.FLOWERING_AZALEA));
					} else if (roll < 21) {
						sink.setIfAbsent(x, level, z, Palette.st(random.nextBoolean() ? Blocks.MOSSY_COBBLESTONE : Blocks.TUFF));
					} else if (roll < 30) {
						sink.setIfAbsent(x, level, z, Palette.st(Blocks.SHORT_GRASS));
					}
				}
			}
		}
		// Caves after the fill.
		for (Cave cave : caves) {
			int floor = ctx.level(cave.floor);
			sink.phase(Phase.FILL);
			for (int y = floor + cave.height - 1; y >= floor; y--) {
				sink.layer(-y);
				for (int x = cave.x0; x <= cave.x1; x++) {
					for (int z = cave.z0; z <= cave.z1; z++) {
						sink.air(x, y, z);
					}
				}
			}
			sink.layerAuto();
			sink.phase(Phase.PLINTH);
			for (int x = cave.x0; x <= cave.x1; x++) {
				for (int z = cave.z0; z <= cave.z1; z++) {
					sink.set(x, floor - 1, z, Palette.st(Blocks.POLISHED_TUFF));
				}
			}
		}
		// Stair flights last (they fill under themselves and keep head room).
		for (Flight flight : flights) {
			new StairFlight(flight.x, flight.z, flight.toward, flight.from, flight.to).width(flight.width).style(flight.style).emit(ctx, sink);
		}
		sink.phase(previous);
	}

	/** The massif never reaches past the template footprint (its skirts are clipped at the edge). */
	private static boolean inFootprint(Ctx ctx, int x, int z) {
		return x >= ctx.spec().x0() && x <= ctx.spec().x1() && z >= ctx.spec().z0() && z <= ctx.spec().z1();
	}

	private boolean farFromPads(Ctx ctx, int x, int z, int margin) {
		for (PadRect pad : pads) {
			if (x >= pad.x0 - margin && x <= pad.x1 + margin && z >= pad.z0 - margin && z <= pad.z1 + margin) {
				return false;
			}
		}
		return true;
	}

	private static BlockState face(Pad style, int x, int y, int z, int level, int low) {
		boolean marble = style == Pad.MARBLE;
		if (y == level - 3 && level - low >= 4) {
			return Palette.st(marble ? Palette.MARBLE_FRET : Palette.STONE_FRET);
		}
		if (!marble && Math.floorMod(x * 3 + z * 5, 7) == 0) {
			return Palette.st(Palette.STONE_PILASTER);
		}
		if (!marble && Math.floorMod(x * 31 + z * 17 + y, 10) == 0) {
			return Palette.st(Palette.STONE_MOSSY);
		}
		return Palette.st(marble ? Palette.MARBLE_FACE : Palette.STONE);
	}

	private static BlockState deck(PadRect pad, int x, int z) {
		if (!pad.paved) {
			return Palette.st(Blocks.GRASS_BLOCK);
		}
		if (pad.style == Pad.MARBLE) {
			return Palette.st(Palette.MARBLE_DECK);
		}
		return Palette.st(Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0 ? Palette.STONE : Palette.PLINTH);
	}

	private void edge(Ctx ctx, Sink sink, PadRect pad, int x, int z, int level, int[][] h, int i, int j) {
		boolean marble = pad.style == Pad.MARBLE;
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		boolean onEdge = false;
		for (int[] dir : dirs) {
			int nx = x + dir[0];
			int nz = z + dir[1];
			int nh = h[i + dir[0]][j + dir[1]];
			if (padCell(ctx, nx, nz) && padAt(ctx, nx, nz) != null && ctx.level(padAt(ctx, nx, nz).level) >= level - 1) {
				continue;
			}
			if (nh > level - 2) {
				continue;
			}
			onEdge = true;
			if (!inOpening(ctx, x, z) && !nearWater(nx, nz)) {
				sink.phase(Phase.ORNAMENT);
				sink.set(nx, level - 1, nz, Palette.slab(marble ? Palette.MARBLE_CAP : Palette.STONE_COPING, true));
				sink.phase(Phase.GARDEN);
				int roll = Math.floorMod(nx * 13 + nz * 7, 6);
				if (roll == 0) {
					sink.setIfAbsent(nx, nh, nz, Palette.st(Blocks.FLOWERING_AZALEA));
				} else if (roll == 1) {
					sink.setIfAbsent(nx, nh, nz, Palette.st(Blocks.FERN));
				}
			}
		}
		if (onEdge && !inOpening(ctx, x, z)) {
			sink.phase(Phase.ORNAMENT);
			boolean post = Math.floorMod(x + z, 3) == 0;
			if (post) {
				sink.set(x, level, z, marble ? Palette.axis(Palette.MARBLE_POST, Direction.Axis.Y) : Palette.st(Palette.STONE_PILASTER));
				boolean lamp = Math.floorMod(x * 7 + z * 11, 4) == 0;
				sink.set(x, level + 1, z, lamp ? Palette.lantern(Blocks.LANTERN, false) : Palette.slab(marble ? Palette.MARBLE_CAP : Blocks.STONE_BRICK_SLAB, false));
			} else {
				sink.set(x, level, z, Palette.st(marble ? Palette.MARBLE_RAIL : Blocks.STONE_BRICK_WALL));
			}
		}
	}
}
