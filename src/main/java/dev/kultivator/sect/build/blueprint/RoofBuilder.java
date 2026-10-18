package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Curved tiered eaves from stairs and slabs, exactly per DESIGN 21.6, shared by every roofed part. Works in the caller's
 * canonical frame (the caller pushes the building frame on the {@link Sink}).
 * <p>
 * <b>Profile (21.6.1, the juzhe rule, at most 45°).</b> Heights {@code s} are half-blocks per column counted from the eave
 * tip ({@code d = 0}) inward: {@code f} columns of +1 (slabs, about 27°; f = 1 when R ≤ 4, 3 when R is 5-9, 5 when R ≥ 10),
 * then +2 per column (stairs, 45°) up to the ridge. Pavilion tops ({@link Profile#PAVILION}) add +3 on the last column;
 * skirts and pagoda eaves ({@link Profile#SKIRT}) are all +1 except +2 on the last column. Corner upturn (qiqiao):
 * {@code up(i) = n - i} for {@code i < n}, {@code n = clamp(R/2, 2, 5)}, surface = {@code s(min(d, R)) + max(0, up(i) - d)};
 * the hip line ({@code i == 0}) sits +1 half-block above the field, in trim, running into the flying tip.
 * <p>
 * <b>Blocks (21.6.2).</b> s odd: bottom slab at {@code baseY + (s-1)/2}; s even and {@code s - sOut ≥ 2}: a stair
 * (HALF=BOTTOM, facing the ridge) at {@code baseY + s/2 - 1}; otherwise a top slab there; the upturn zone uses slabs only.
 * Under the shell the roof fills down to {@code floor(sOut/2)} so no hole shows, except in the first {@code overhang}
 * columns (an open soffit). Top slabs on the wall ring become full blocks so the frieze closes (21.6.3); the hall fills the
 * frieze below. The eave ring is placed first and the ridge last: every op's layer is its s.
 * <p>
 * <b>Shapes.</b> d = min(a, b) for hip and pyramid roofs; a xieshan uses d = b where a ≥ {@code gableInset} (the vertical
 * gable triangle 1 inside the end pillar line, field {@link #gableField}); a gable roof always d = b (a = distance along
 * the ridge to its end, b = distance across). Octagonal: {@code d = min(a, b, (a + b - c)/2)}. Round:
 * {@code d = R - sqrt(dx² + dz²)}. Hip roofs need W/D ≥ 1.6 (else use xieshan): the caller decides.
 * <p>
 * <b>Ridge and ornaments (21.6.4).</b> The ridge column is a shared field block, then the ridge block, then the cap when
 * the roof is at least 11 long (a rolled ridge of slabs when 6 or less). {@link #gild} adds the gold pearl (flanked by two
 * stairs), chiwen hooks at both ridge ends and the roof beasts (unlit candles on the last hip pieces); {@link #windBells}
 * the T4 chain-and-lantern under every flying tip; {@link #eaveLanterns} palace lanterns under the front eave. Art
 * direction from the Yingzao Fashi and the Qing Gongbu Gongcheng Zuofa, not novel canon.
 */
public final class RoofBuilder {

	/** Roof forms. */
	public enum Type {
		GABLE,
		XIESHAN,
		HIP,
		PYRAMID,
		OCTAGON,
		ROUND,
		/** A lean-to skirt from the outline up to an inner rectangle (lower eave of a double roof, pagoda floor eaves). */
		SKIRT,
		/** A hexagonal pavilion roof: pointed at the ±x outline middles, flat along the ±z edges ({@link #hexagon}). */
		HEXAGON
	}

	/** Height profiles of 21.6.1. */
	public enum Profile {
		HALL,
		PAVILION,
		SKIRT
	}

	private final Type type;
	private final int x0;
	private final int x1;
	private final int z0;
	private final int z1;
	private final int baseY;
	private final RoofMat mat;
	private int overhang = 2;
	private int[] wall;
	private int[] inner;
	private int gableInset = -1;
	private int chamfer = 2;
	private BlockState gableField = Palette.st(Palette.PLASTER);
	private boolean soffit;
	private boolean ridgeLight = true;
	private Profile profile = Profile.HALL;
	private boolean rolled;
	private int skirtChamfer;
	private int innerChamfer;
	private int hexFlat = 1;

	private boolean computed;
	private int width;
	private int depth;
	private boolean alongX;
	private int radius;
	private int upturn;
	private int[][] surface;
	private int[][] dist;
	private int[][] cross;
	private boolean[][] slabOnly;
	private boolean[][] hip;

	/**
	 * A roof over the eave outline {@code x0..x1 × z0..z1} (inclusive, canonical frame) whose eave tip (s = 1) is a bottom
	 * slab at {@code baseY}.
	 */
	public RoofBuilder(Type type, int x0, int x1, int z0, int z1, int baseY, RoofMat mat) {
		this.type = type;
		this.x0 = Math.min(x0, x1);
		this.x1 = Math.max(x0, x1);
		this.z0 = Math.min(z0, z1);
		this.z1 = Math.max(z0, z1);
		this.baseY = baseY;
		this.mat = mat;
		if (type == Type.SKIRT) {
			profile = Profile.SKIRT;
		}
	}

	/** Columns outside the wall ring (open soffit, no fill under them). */
	public RoofBuilder overhang(int overhang) {
		this.overhang = Math.max(0, overhang);
		return this;
	}

	/** The wall ring (pillar line): top slabs on it become full blocks so the frieze closes. */
	public RoofBuilder wall(int wx0, int wx1, int wz0, int wz1) {
		this.wall = new int[]{Math.min(wx0, wx1), Math.max(wx0, wx1), Math.min(wz0, wz1), Math.max(wz0, wz1)};
		return this;
	}

	/** {@link Type#SKIRT}: the inner rectangle the skirt leans against (the core wall). */
	public RoofBuilder inner(int ix0, int ix1, int iz0, int iz1) {
		this.inner = new int[]{Math.min(ix0, ix1), Math.max(ix0, ix1), Math.min(iz0, iz1), Math.max(iz0, iz1)};
		return this;
	}

	/** Xieshan: the gable face's distance from the roof end (default overhang + 1). */
	public RoofBuilder gableInset(int inset) {
		this.gableInset = inset;
		return this;
	}

	/** Octagon: the corner cut of the outline. */
	public RoofBuilder chamfer(int chamfer) {
		this.chamfer = Math.max(1, chamfer);
		return this;
	}

	/** {@link Type#HEXAGON}: the half length of the flat ±z edges (the ±x edges end in a point). */
	public RoofBuilder hexagon(int flatHalf) {
		this.hexFlat = Math.max(0, flatHalf);
		return this;
	}

	/** {@link Type#SKIRT} of an octagonal pagoda: outline corner cut {@code outer}, core corner cut {@code inner}. */
	public RoofBuilder octagon(int outer, int inner) {
		this.skirtChamfer = Math.max(1, outer);
		this.innerChamfer = Math.max(0, inner);
		this.chamfer = this.skirtChamfer;
		return this;
	}

	/** Xieshan gable field: {@code SMOOTH_QUARTZ} (Jiangnan) or {@code POLISHED_CINNABAR} (northern official style). */
	public RoofBuilder gableField(BlockState field) {
		this.gableField = field;
		return this;
	}

	/** The dark rafter soffit of rank C/D under the overhang. */
	public RoofBuilder soffit(boolean soffit) {
		this.soffit = soffit;
		return this;
	}

	/** A pearlescent froglight in the ridge centre (the light rule of 21.10; default on). */
	public RoofBuilder ridgeLight(boolean light) {
		this.ridgeLight = light;
		return this;
	}

	public RoofBuilder profile(Profile profile) {
		this.profile = profile;
		return this;
	}

	/** Forces a rolled ridge (卷棚) of slabs, the default when the roof is 6 or less long. */
	public RoofBuilder rolled(boolean rolled) {
		this.rolled = rolled;
		return this;
	}

	// ================================================================== geometry

	private void compute() {
		if (computed) {
			return;
		}
		computed = true;
		width = x1 - x0 + 1;
		depth = z1 - z0 + 1;
		alongX = width >= depth;
		int shortSide = Math.min(width, depth);
		radius = Math.max(1, (shortSide - 1) / 2);
		if (type == Type.SKIRT && inner != null) {
			int gap = Math.min(Math.min(inner[0] - x0, x1 - inner[1]), Math.min(inner[2] - z0, z1 - inner[3]));
			radius = Math.max(1, gap - 1);
		}
		upturn = Math.max(2, Math.min(5, radius / 2));
		surface = new int[width][depth];
		dist = new int[width][depth];
		cross = new int[width][depth];
		slabOnly = new boolean[width][depth];
		hip = new boolean[width][depth];
		double cxf = (x0 + x1) / 2.0D;
		double czf = (z0 + z1) / 2.0D;
		double roundR = shortSide / 2.0D;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int ix = x - x0;
				int iz = z - z0;
				int dx = Math.min(x - x0, x1 - x);
				int dz = Math.min(z - z0, z1 - z);
				int a = alongX ? dx : dz;
				int b = alongX ? dz : dx;
				int d;
				int i = Math.abs(a - b);
				boolean corners = true;
				switch (type) {
					case GABLE -> {
						d = b;
						corners = false;
					}
					case XIESHAN -> {
						int inset = gableInset >= 0 ? gableInset : overhang + 1;
						d = a >= inset ? b : Math.min(a, b);
					}
					case OCTAGON -> {
						if (a + b < chamfer) {
							continue;
						}
						// The chamfer face rises from its eave (a + b = c) to the apex (a + b = 2R) as evenly as the straight faces.
						int diag = (a + b - chamfer) * radius / Math.max(1, 2 * radius - chamfer);
						d = Math.min(Math.min(a, b), diag);
						i = Math.min(Math.abs(a - diag), Math.abs(b - diag));
					}
					case HEXAGON -> {
						// Pointed toward ±x: inside when p·R + q·(R − c) ≥ R·(R − c) (p, q: distances to the x and z edges).
						int p = dx;
						int q = dz;
						int r = Math.max(1, Math.min((width - 1) / 2, (depth - 1) / 2));
						int c = Math.min(hexFlat, r);
						long f = (long) p * r + (long) q * (r - c) - (long) r * (r - c);
						if (f < 0) {
							continue;
						}
						int diag = (int) (f / r);
						d = Math.min(q, diag);
						i = Math.min(Math.abs(q - diag), r - q);
					}
					case ROUND -> {
						double distance = Math.hypot(x - cxf, z - czf);
						if (distance > roundR) {
							continue;
						}
						d = (int) Math.floor(roundR - distance);
						corners = false;
					}
					case SKIRT -> {
						if (inner != null && x >= inner[0] && x <= inner[1] && z >= inner[2] && z <= inner[3]) {
							int ia = Math.min(x - inner[0], inner[1] - x);
							int ib = Math.min(z - inner[2], inner[3] - z);
							if (innerChamfer <= 0 || ia + ib >= innerChamfer) {
								continue;
							}
						}
						if (skirtChamfer > 0) {
							if (a + b < skirtChamfer) {
								continue;
							}
							int diag = (a + b - skirtChamfer) / 2;
							d = Math.min(Math.min(a, b), diag);
							i = Math.min(Math.abs(a - diag), Math.abs(b - diag));
						} else {
							d = Math.min(a, b);
						}
					}
					default -> d = Math.min(a, b);
				}
				d = Math.max(0, Math.min(d, radius));
				int s = profile(d);
				boolean upZone = false;
				if (corners && i < upturn) {
					int lift = upturn - i - d;
					if (lift > 0) {
						s += lift;
						upZone = true;
					}
				}
				boolean onHip = corners && i == 0 && d > 0 && d < radius;
				if (onHip) {
					s += 1;
				}
				surface[ix][iz] = s;
				dist[ix][iz] = d;
				cross[ix][iz] = (a >= b || type == Type.GABLE) ? 1 : 0;
				slabOnly[ix][iz] = upZone;
				hip[ix][iz] = onHip || corners && i == 0 && d == 0;
			}
		}
	}

	/** The profile height s of column distance {@code d} (21.6.1). */
	private int profile(int d) {
		if (profile == Profile.SKIRT) {
			int last = radius;
			return d < last ? 1 + d : d + 2;
		}
		int f = radius <= 4 ? 1 : radius <= 9 ? 3 : 5;
		int s = d <= f ? 1 + d : 1 + f + 2 * (d - f);
		if (profile == Profile.PAVILION && d == radius && radius >= 2) {
			s += 1;
		}
		return s;
	}

	private boolean inside(int x, int z) {
		return x >= x0 && x <= x1 && z >= z0 && z <= z1 && surface[x - x0][z - z0] > 0;
	}

	/** The surface height s of column (x, z), 0 outside the roof. */
	public int s(int x, int z) {
		compute();
		return x >= x0 && x <= x1 && z >= z0 && z <= z1 ? surface[x - x0][z - z0] : 0;
	}

	/** The y of the roof piece of column (x, z) (its slab or stair), or {@link Integer#MIN_VALUE} outside. */
	public int pieceY(int x, int z) {
		int s = s(x, z);
		if (s <= 0) {
			return Integer.MIN_VALUE;
		}
		return s % 2 == 1 ? baseY + (s - 1) / 2 : baseY + s / 2 - 1;
	}

	/** The y just under the roof shell at column (x, z) (where a hanging chain may start), MIN_VALUE outside. */
	public int underside(int x, int z) {
		int y = pieceY(x, z);
		return y == Integer.MIN_VALUE ? y : y - 1;
	}

	/** The top y of the ridge stack (for spires and sightlines). */
	public int ridgeTop() {
		compute();
		int ridgeLength = alongX ? width : depth;
		int stack = rolled || ridgeLength <= 6 ? 1 : ridgeLength >= 11 && mat.cap() != null ? 2 : 1;
		int top = baseY;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int y = pieceY(x, z);
				if (y != Integer.MIN_VALUE) {
					top = Math.max(top, y + (type != Type.SKIRT && dist[x - x0][z - z0] == radius ? stack : 0));
				}
			}
		}
		return top;
	}

	public int radius() {
		compute();
		return radius;
	}

	public int baseY() {
		return baseY;
	}

	// ================================================================== emission

	/** Plans the roof shell, the fill under it and the ridge (phase ROOF, layer = s). */
	public void emit(Sink sink) {
		compute();
		Phase previous = sink.phase();
		sink.phase(Phase.ROOF);
		int ridgeLength = alongX ? width : depth;
		boolean rolledRidge = rolled || ridgeLength <= 6;
		int ridgeCx = (x0 + x1) / 2;
		int ridgeCz = (z0 + z1) / 2;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				int ix = x - x0;
				int iz = z - z0;
				int s = surface[ix][iz];
				if (s <= 0) {
					continue;
				}
				int d = dist[ix][iz];
				int sOut = Integer.MAX_VALUE;
				for (Direction direction : Direction.Plane.HORIZONTAL) {
					int nx = x + direction.getStepX();
					int nz = z + direction.getStepZ();
					sOut = Math.min(sOut, inside(nx, nz) ? surface[nx - x0][nz - z0] : 0);
				}
				boolean ridge = type != Type.SKIRT && d == radius && type != Type.ROUND || type == Type.ROUND && d == radius;
				boolean gableCol = type == Type.XIESHAN && isGableColumn(x, z);
				// Trim: the eave row, the raised hips, the skirt's flashing row and the xieshan bargeboard.
				boolean trim = d == 0 || hip[ix][iz] || type == Type.SKIRT && d == radius || gableCol;
				boolean onWall = wall != null && onRing(x, z, wall);
				sink.layer(s);
				int y;
				if (ridge) {
					y = s % 2 == 1 ? baseY + (s - 1) / 2 : baseY + s / 2 - 1;
					boolean centre = x == ridgeCx && z == ridgeCz;
					if (rolledRidge) {
						sink.set(x, y, z, Palette.st(mat.fieldFull()));
						sink.layer(s + 1).set(x, y + 1, z, Palette.slab(mat.trimSlab(), false));
					} else {
						sink.set(x, y, z, centre && ridgeLight ? Palette.st(Palette.RIDGE_LIGHT) : Palette.st(mat.fieldFull()));
						sink.layer(s + 1).set(x, y + 1, z, Palette.st(mat.ridge()));
						if (ridgeLength >= 11 && mat.cap() != null) {
							sink.layer(s + 2).set(x, y + 2, z, mat.cap());
						}
					}
					sink.layer(s);
				} else {
					y = s % 2 == 1 ? baseY + (s - 1) / 2 : baseY + s / 2 - 1;
					BlockState piece;
					if (s % 2 == 1) {
						piece = Palette.slab(trim ? mat.trimSlab() : mat.fieldSlab(), false);
					} else if (!slabOnly[ix][iz] && s - sOut >= 2) {
						piece = Palette.stair(trim ? mat.trimStairs() : mat.fieldStairs(), uphill(x, z), false);
					} else if (onWall) {
						piece = Palette.st(trim ? mat.trimFull() : mat.fieldFull());
					} else {
						piece = Palette.slab(trim ? mat.trimSlab() : mat.fieldSlab(), true);
					}
					if (onWall && s % 2 == 0 && piece.getBlock() != mat.trimStairs() && piece.getBlock() != mat.fieldStairs()) {
						piece = Palette.st(trim ? mat.trimFull() : mat.fieldFull());
					}
					sink.set(x, y, z, piece);
				}
				// No slab-on-fill stacks and no holes: fill down to floor(sOut/2) inside the overhang (21.6.2).
				if (d >= overhang && sOut != Integer.MAX_VALUE) {
					int from = baseY + Math.max(0, sOut) / 2;
					BlockState fill = gableCol ? gableField : Palette.st(mat.fieldFull());
					for (int fy = from; fy < y; fy++) {
						sink.layer(Math.max(1, 2 * (fy - baseY) + 1)).set(x, fy, z, fill);
					}
					sink.layer(s);
				}
				// Rank C/D soffit: a dark top slab under the overhang pieces (not the eave tip itself).
				if (soffit && d > 0 && d < overhang && s % 2 == 1) {
					sink.layer(0).set(x, y - 1, z, Palette.slab(Palette.SOFFIT, true));
					sink.layer(s);
				}
			}
		}
		// Flying tips: a slab diagonally outside each corner over an upside-down trim bracket.
		sink.optional(true);
		if (hasCorners()) {
			for (int[] corner : corners()) {
				int cs = s(corner[0], corner[1]);
				int tipS = cs + 1;
				int tipY = tipS % 2 == 1 ? baseY + (tipS - 1) / 2 : baseY + tipS / 2 - 1;
				int tx = corner[0] + corner[2];
				int tz = corner[1] + corner[3];
				sink.layer(tipS).set(tx, tipY, tz, Palette.slab(mat.trimSlab(), tipS % 2 == 0));
				Direction inward = corner[2] > 0 ? Direction.WEST : Direction.EAST;
				sink.set(tx, tipY - 1, tz, Palette.stair(mat.trimStairs(), inward, true));
			}
		}
		sink.optional(false);
		sink.phase(previous);
	}

	/**
	 * Gold pearl at the ridge centre, chiwen at both ridge ends and roof beasts on the hips (rank D, gilding). The pearl,
	 * finial and chiwen replace ridge pieces, so they are emitted in ROOF (the placer lets a structural phase swap the
	 * builder's own blocks, while GARDEN/ORNAMENT/WATER never overwrite them); the beast candles stay ORNAMENT.
	 */
	public void gild(Sink sink, boolean pearl, boolean chiwen, @Nullable Block beastCandle) {
		compute();
		if (type == Type.SKIRT) {
			return;
		}
		Phase previous = sink.phase();
		sink.phase(Phase.ROOF);
		int ridgeLength = alongX ? width : depth;
		int cx = (x0 + x1) / 2;
		int cz = (z0 + z1) / 2;
		int topS = profile(radius);
		int ridgeY = (topS % 2 == 1 ? baseY + (topS - 1) / 2 : baseY + topS / 2 - 1) + 2;
		if (type == Type.PYRAMID || type == Type.OCTAGON || type == Type.HEXAGON || type == Type.ROUND || ridgeLength <= 6) {
			if (pearl) {
				// Apex finial: tile walls, the gold block, a chain and an end-rod spire (21.6.5).
				int apex = ridgeY - 1;
				sink.set(cx, apex, cz, Palette.st(Blocks.DEEPSLATE_TILE_WALL));
				sink.set(cx, apex + 1, cz, Palette.st(Palette.GOLD));
			}
		} else {
			if (pearl) {
				sink.set(cx, ridgeY, cz, Palette.st(Palette.GOLD));
				if (alongX) {
					sink.set(cx - 1, ridgeY, cz, Palette.stair(mat.trimStairs(), Direction.EAST, false));
					sink.set(cx + 1, ridgeY, cz, Palette.stair(mat.trimStairs(), Direction.WEST, false));
				} else {
					sink.set(cx, ridgeY, cz - 1, Palette.stair(mat.trimStairs(), Direction.SOUTH, false));
					sink.set(cx, ridgeY, cz + 1, Palette.stair(mat.trimStairs(), Direction.NORTH, false));
				}
			}
			if (chiwen) {
				int[] ends = ridgeEnds();
				if (ends != null) {
					if (alongX) {
						sink.set(ends[0], ridgeY, cz, Palette.stair(mat.trimStairs(), Direction.WEST, false));
						sink.set(ends[0] - 1, ridgeY, cz, Palette.slab(mat.trimSlab(), true));
						sink.set(ends[1], ridgeY, cz, Palette.stair(mat.trimStairs(), Direction.EAST, false));
						sink.set(ends[1] + 1, ridgeY, cz, Palette.slab(mat.trimSlab(), true));
					} else {
						sink.set(cx, ridgeY, ends[0], Palette.stair(mat.trimStairs(), Direction.NORTH, false));
						sink.set(cx, ridgeY, ends[0] - 1, Palette.slab(mat.trimSlab(), true));
						sink.set(cx, ridgeY, ends[1], Palette.stair(mat.trimStairs(), Direction.SOUTH, false));
						sink.set(cx, ridgeY, ends[1] + 1, Palette.slab(mat.trimSlab(), true));
					}
				}
			}
		}
		sink.phase(Phase.ORNAMENT);
		if (beastCandle != null && hasCorners()) {
			// Unlit candles on the last hip pieces before each tip, only where the piece has a full top face.
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					int ix = x - x0;
					int iz = z - z0;
					if (!hip[ix][iz] || dist[ix][iz] < 1 || dist[ix][iz] > 4) {
						continue;
					}
					int s = surface[ix][iz];
					if (s % 2 == 0 && !(s - minNeighbour(x, z) >= 2 && !slabOnly[ix][iz])) {
						sink.set(x, baseY + s / 2, z, Palette.candles(beastCandle, 1, false));
					}
				}
			}
		}
		sink.phase(previous);
	}

	/** The T4 wind bells: an iron chain and a lantern under every flying tip bracket (ORNAMENT). */
	public void windBells(Sink sink) {
		compute();
		if (type == Type.OCTAGON || type == Type.HEXAGON || type == Type.SKIRT && skirtChamfer > 0) {
			Phase previous = sink.phase();
			sink.phase(Phase.ORNAMENT);
			for (int[] vertex : type == Type.HEXAGON ? hexagonVertices() : octagonVertices()) {
				int y = pieceY(vertex[0], vertex[1]);
				if (y != Integer.MIN_VALUE) {
					sink.set(vertex[0], y - 1, vertex[1], Palette.ironChain());
					sink.set(vertex[0], y - 2, vertex[1], Palette.lantern(Blocks.LANTERN, true));
				}
			}
			sink.phase(previous);
			return;
		}
		if (!hasCorners()) {
			return;
		}
		Phase previous = sink.phase();
		sink.phase(Phase.ORNAMENT);
		for (int[] corner : corners()) {
			int cs = s(corner[0], corner[1]);
			int tipS = cs + 1;
			int tipY = tipS % 2 == 1 ? baseY + (tipS - 1) / 2 : baseY + tipS / 2 - 1;
			int tx = corner[0] + corner[2];
			int tz = corner[1] + corner[3];
			sink.set(tx, tipY - 2, tz, Palette.ironChain());
			sink.set(tx, tipY - 3, tz, Palette.lantern(Blocks.LANTERN, true));
		}
		sink.phase(previous);
	}

	/**
	 * Palace lanterns hanging under the eave at the given canonical x positions of the front ({@code front} true: the +z
	 * eave) or back: 1-2 iron chains, a shroomlight and a cinnabar top-slab rim below (21.5 "Lights"; no 2×2×2 cubes).
	 */
	public void eaveLanterns(Sink sink, boolean front, int... xs) {
		compute();
		Phase previous = sink.phase();
		sink.phase(Phase.ORNAMENT);
		int z = front ? z1 - 1 : z0 + 1;
		for (int x : xs) {
			int top = underside(x, z);
			if (top == Integer.MIN_VALUE) {
				continue;
			}
			sink.set(x, top, z, Palette.ironChain());
			sink.set(x, top - 1, z, Palette.st(Palette.LIGHT));
			sink.set(x, top - 2, z, Palette.slab(Blocks.POLISHED_CINNABAR_SLAB, true));
		}
		sink.phase(previous);
	}

	// ================================================================== helpers

	private boolean hasCorners() {
		return type == Type.XIESHAN || type == Type.HIP || type == Type.PYRAMID || type == Type.SKIRT && skirtChamfer <= 0;
	}

	/** The eight outline vertices of an octagonal roof or skirt. */
	private int[][] octagonVertices() {
		int c = chamfer;
		return new int[][]{{x0 + c, z0}, {x1 - c, z0}, {x0 + c, z1}, {x1 - c, z1}, {x0, z0 + c}, {x0, z1 - c}, {x1, z0 + c}, {x1, z1 - c}};
	}

	/** The six outline vertices of a hexagonal roof: the ±x points and the ends of the flat ±z edges. */
	private int[][] hexagonVertices() {
		int cx = (x0 + x1) / 2;
		int cz = (z0 + z1) / 2;
		int c = Math.min(hexFlat, (x1 - x0) / 2);
		return new int[][]{{x0, cz}, {x1, cz}, {cx - c, z0}, {cx + c, z0}, {cx - c, z1}, {cx + c, z1}};
	}

	/** The four outline corners with their outward diagonal steps: {x, z, sx, sz}. */
	private int[][] corners() {
		return new int[][]{{x0, z0, -1, -1}, {x1, z0, 1, -1}, {x0, z1, -1, 1}, {x1, z1, 1, 1}};
	}

	private boolean isGableColumn(int x, int z) {
		int inset = gableInset >= 0 ? gableInset : overhang + 1;
		int dx = Math.min(x - x0, x1 - x);
		int dz = Math.min(z - z0, z1 - z);
		int a = alongX ? dx : dz;
		return a == inset;
	}

	/** The along-ridge coordinates of both main-ridge ends, or null. */
	private int @Nullable [] ridgeEnds() {
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (surface[x - x0][z - z0] > 0 && dist[x - x0][z - z0] == radius) {
					int along = alongX ? x : z;
					low = Math.min(low, along);
					high = Math.max(high, along);
				}
			}
		}
		return low == Integer.MAX_VALUE || high - low < 2 ? null : new int[]{low, high};
	}

	private int minNeighbour(int x, int z) {
		int min = Integer.MAX_VALUE;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			int nx = x + direction.getStepX();
			int nz = z + direction.getStepZ();
			min = Math.min(min, inside(nx, nz) ? surface[nx - x0][nz - z0] : 0);
		}
		return min;
	}

	/** The direction toward the ridge from column (x, z): toward the highest neighbour (across the roof on ties). */
	private Direction uphill(int x, int z) {
		Direction best = null;
		int bestS = Integer.MIN_VALUE;
		boolean crossDriven = cross[x - x0][z - z0] == 1;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			int nx = x + direction.getStepX();
			int nz = z + direction.getStepZ();
			int s = inside(nx, nz) ? surface[nx - x0][nz - z0] : 0;
			boolean acrossAxis = alongX ? direction.getAxis() == Direction.Axis.Z : direction.getAxis() == Direction.Axis.X;
			if (s > bestS || s == bestS && acrossAxis == crossDriven) {
				bestS = s;
				best = direction;
			}
		}
		return best == null ? Direction.NORTH : best;
	}

	private static boolean onRing(int x, int z, int[] ring) {
		boolean inX = x >= ring[0] && x <= ring[1];
		boolean inZ = z >= ring[2] && z <= ring[3];
		return inX && inZ && (x == ring[0] || x == ring[1] || z == ring[2] || z == ring[3]);
	}
}
