package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * A timber-frame hall of any rank and bay count (DESIGN 21.7, 21.6), the workhorse of every template: dorms, kitchens,
 * scripture pavilions, the Aula Pendiri, alchemy and ancestral halls and the double-eave main halls.
 * <p>
 * <b>Position.</b> {@code new Hall(x, z)} puts the centre of the pillar ring (the core) at template-local (x, z);
 * {@link #front} turns the façade (default {@link Direction#SOUTH}, toward the gate); {@link #site} is the level the
 * platform stands on (default the plaza). The floor level is {@code site + platform}.
 * <p>
 * <b>Proportions (21.7).</b> {@link #bays} are the pillar spacings across the front (centre bay 6, others 4, end bays of
 * 7- and 9-bay halls 3; small rank A/B halls may use 3s: {@code bays(3, 6, 3)} is 13 wide). {@link #depth} is the core
 * depth in cells, pillar row to pillar row inclusive (odd, at least 7 for halls; width : depth 1.6-2.2). {@link #pillar}
 * height: dorms 4, halls 5-6. The overhang follows the parity rule (2 when the pillar is 5 or less, else 4) unless set.
 * {@link #platform}: 1 for minor buildings, 2 for halls; steps only as wide as the centre bay ({@link #stepsInset} cuts them
 * into the platform when space in front is short).
 * <p>
 * <b>Body.</b> Cinnabar pillars on chiseled tuff bases; a warped-plank beam band with cyan beam ends (rank C/D) and gilt
 * caps ({@link #gilded}); warped bracket rows (rank B+) closing the frieze up to the roof; white plaster side and back
 * walls on an andesite plinth with lattice windows; a façade of geshan doors in the door bays and lattice (open trapdoors
 * on the outer face) elsewhere; a porch ({@link #porch}, rank C/D default 1) sets the door line behind the front pillars.
 * Doors are {@link Doors#CLOSED}, {@link Doors#OPEN} or {@link Doors#NONE} (an open doorway: use it on disciple routes,
 * disciples cannot open doors). Halls of 5+ bays get interior pillar rows. Polished deepslate floor, a red carpet runner on
 * the axis (rank C/D), hanging palace lanterns every ~7 cells, and an {@link Interiors.Preset}.
 * <p>
 * <b>Roof.</b> {@link #roof} type (XIESHAN default, HIP needs width/depth ≥ 1.6, GABLE for humble wings) with
 * {@link #eaves} 1 or 2. Two eaves build a real outer colonnade 3 outside the core with a lower skirt, a 2-row clerestory
 * carrying the plaque and the upper roof 1-2 blocks inside the lower eave tip. {@link #storeys}(2) stacks an upper storey
 * over a skirt instead (two-storey scripture pavilions). Rank C/D get a soffit; rank D (or {@link #gilded}) gets the gold
 * pearl, chiwen and roof beasts; {@link #plaque} a gilded-blackstone frame with a wall sign (the engine writes the sect
 * name).
 * <p>
 * <b>Decorations by tier</b> ({@link #decorate}): T4 wind bells under the flying tips; T6 gilt caps (gilded halls); T9
 * eave lanterns. Art direction from real architecture (Yingzao Fashi, the Forbidden City), not novel canon.
 */
public final class Hall implements Part {

	/** Door leaves of the door bays. */
	public enum Doors {
		CLOSED,
		OPEN,
		NONE
	}

	private final int x;
	private final int z;
	private Direction front = Direction.SOUTH;
	private Lv site = Lv.PLAZA;
	private int[] bays = {3, 6, 3};
	private int depth = 7;
	private int pillarH = 5;
	private int platformH = 2;
	private int overhang = -1;
	private RoofBuilder.Type roof = RoofBuilder.Type.XIESHAN;
	private int eaves = 1;
	private int storeys = 1;
	private int upperH = 4;
	private Rank rank = Rank.B;
	private int porch = -1;
	private Interiors.Preset interior = Interiors.Preset.EMPTY;
	private Doors doors = Doors.CLOSED;
	private Boolean plaque;
	private Boolean gilded;
	private BlockState gableField = Palette.st(Palette.PLASTER);
	private int stepsInset;
	private boolean frontSteps = true;
	private boolean backSteps;
	private String anchor;
	private int interiorCount;
	private boolean passage;

	/** A hall whose pillar ring is centred on template-local (x, z). */
	public Hall(int x, int z) {
		this.x = x;
		this.z = z;
	}

	public Hall front(Direction front) {
		this.front = front.getAxis().isHorizontal() ? front : Direction.SOUTH;
		return this;
	}

	public Hall site(Lv site) {
		this.site = site;
		return this;
	}

	/** Pillar spacings across the front, e.g. {@code bays(4, 4, 6, 4, 4)} for a 5-bay hall (pillars at ±3, ±7, ±11). */
	public Hall bays(int... spacings) {
		if (spacings != null && spacings.length > 0) {
			this.bays = spacings.clone();
		}
		return this;
	}

	/** Core depth in cells, front pillar row to back pillar row inclusive (odd). */
	public Hall depth(int depth) {
		this.depth = Math.max(3, depth | 1);
		return this;
	}

	public Hall pillar(int height) {
		this.pillarH = Math.max(3, height);
		return this;
	}

	public Hall platform(int height) {
		this.platformH = Math.max(0, height);
		return this;
	}

	public Hall overhang(int overhang) {
		this.overhang = overhang;
		return this;
	}

	public Hall roof(RoofBuilder.Type type, int eaves) {
		this.roof = type;
		this.eaves = Math.max(1, Math.min(2, eaves));
		return this;
	}

	/** Two storeys: the upper storey of {@code upperHeight} rises over a skirt around the lower one. */
	public Hall storeys(int storeys, int upperHeight) {
		this.storeys = Math.max(1, Math.min(2, storeys));
		this.upperH = Math.max(3, upperHeight);
		return this;
	}

	public Hall rank(Rank rank) {
		this.rank = rank;
		return this;
	}

	/** Cells between the front pillar row and the door line (rank C/D default 1, others 0). */
	public Hall porch(int cells) {
		this.porch = Math.max(0, Math.min(3, cells));
		return this;
	}

	public Hall interior(Interiors.Preset preset) {
		this.interior = preset;
		return this;
	}

	/** Interior preset with a count (bookshelves of a library, beds of a dorm, tablets of an ancestral hall...). */
	public Hall interior(Interiors.Preset preset, int count) {
		this.interior = preset;
		this.interiorCount = count;
		return this;
	}

	public Hall doors(Doors doors) {
		this.doors = doors;
		return this;
	}

	public Hall plaque(boolean plaque) {
		this.plaque = plaque;
		return this;
	}

	public Hall gilded(boolean gilded) {
		this.gilded = gilded;
		return this;
	}

	/** Xieshan gable field: plaster (Jiangnan) or cinnabar (northern official style). */
	public Hall gableField(BlockState field) {
		this.gableField = field;
		return this;
	}

	/** Cuts the front steps {@code cells} deep into the platform (when the space in front is reserved). */
	public Hall stepsInset(int cells) {
		this.stepsInset = Math.max(0, cells);
		return this;
	}

	public Hall steps(boolean front, boolean back) {
		this.frontSteps = front;
		this.backSteps = back;
		return this;
	}

	/** A gate hall (shanmen): the door bays stay open front and back so the axis passes through (no doors). */
	public Hall passage(boolean passage) {
		this.passage = passage;
		if (passage) {
			this.doors = Doors.NONE;
			this.backSteps = true;
		}
		return this;
	}

	/** Names the standing cell in the middle of the hall floor ("main_hall", "library", "treasury"...). */
	public Hall anchor(String id) {
		this.anchor = id;
		return this;
	}

	// ================================================================== geometry (canonical frame)

	/**
	 * True when the façade is symmetric about the axis (DESIGN 21.7): an odd number of bays with an even total width, so
	 * a doorway, never a pillar, stands on the centre line ({@code uji sekte audit} reports the others).
	 */
	public boolean symmetricBays() {
		int sum = 0;
		for (int bay : bays) {
			sum += bay;
		}
		return bays.length % 2 == 1 && sum % 2 == 0;
	}

	int halfWidth() {
		int sum = 0;
		for (int bay : bays) {
			sum += bay;
		}
		return sum / 2;
	}

	int halfDepth() {
		return (depth - 1) / 2;
	}

	int[] pillarXs() {
		int[] xs = new int[bays.length + 1];
		xs[0] = -halfWidth();
		for (int i = 0; i < bays.length; i++) {
			xs[i + 1] = xs[i] + bays[i];
		}
		return xs;
	}

	int overhang() {
		return overhang >= 0 ? overhang : (pillarH <= 5 ? 2 : 4);
	}

	int porch() {
		return porch >= 0 ? porch : (rank == Rank.C || rank == Rank.D) && eaves == 1 ? 1 : 0;
	}

	boolean gilded() {
		return gilded != null ? gilded : rank == Rank.D;
	}

	boolean plaque() {
		return plaque != null ? plaque : rank == Rank.C || rank == Rank.D;
	}

	/** The outermost pillar ring half sizes (the colonnade when double-eaved). */
	int outerHalfWidth() {
		return halfWidth() + (eaves == 2 ? 3 : 0);
	}

	int outerHalfDepth() {
		return halfDepth() + (eaves == 2 ? 3 : 0);
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int floor = ctx.level(site) + platformH;
		int o = overhang();
		int hx = outerHalfWidth() + o + 1;
		int hz = outerHalfDepth() + o + 1 + platformH;
		int top = floor + pillarH + 6 + (eaves == 2 ? 10 : 0) + (storeys == 2 ? upperH + 3 : 0) + halfDepth();
		return Part.box(x, z, front, -hx, ctx.level(site) - 1 - platformH, -hz, hx, top, hz);
	}

	@Override
	public String kind() {
		return "hall";
	}

	// ================================================================== emission

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int floor = ctx.level(site) + platformH;
		sink.pushFacing(x, floor, z, front);
		RoofMat mat = ctx.roof(rank);
		int px = halfWidth();
		int pz = halfDepth();
		int[] xs = pillarXs();
		int o = overhang();
		int opx = outerHalfWidth();
		int opz = outerHalfDepth();
		platform(ctx, sink, opx, opz, xs);
		RoofBuilder upper;
		if (eaves == 2) {
			upper = doubleEave(ctx, sink, mat, px, pz, xs);
		} else if (storeys == 2) {
			upper = twoStoreys(ctx, sink, mat, px, pz, xs, o);
		} else {
			upper = singleEave(ctx, sink, mat, px, pz, xs, o);
		}
		int zf = eaves == 2 ? pz : pz - porch();
		// Interior: floor, runner, lights and the preset (inside the walls).
		sink.phase(Phase.INTERIOR);
		for (int ix = -px + 1; ix <= px - 1; ix++) {
			for (int iz = -pz + 1; iz <= zf - 1; iz++) {
				sink.set(ix, -1, iz, Palette.st(Palette.FLOOR));
			}
		}
		if (rank == Rank.C || rank == Rank.D) {
			for (int iz = -pz + 2; iz <= zf - 1; iz++) {
				sink.set(0, 0, iz, Palette.st(Palette.carpet(DyeColor.RED)));
			}
		}
		int ceiling = storeys == 2 ? pillarH - 1 : (eaves == 2 ? pillarH + 2 : pillarH);
		lights(sink, upper, px, pz, zf, ceiling);
		Interiors.emit(interior, interiorCount, ctx, sink, -px + 1, px - 1, -pz + 1, zf - 1, Math.min(ceiling, pillarH), rank);
		if (anchor != null) {
			sink.anchor(anchor, 0, 0, Math.min(zf - 2, 0));
		}
		// Gold, chiwen, beasts and the plaque (rank D, or a gilded hall).
		if (gilded()) {
			upper.gild(sink, true, true, ctx.glaze().beastCandle());
		}
		if (plaque()) {
			plaque(sink, pz, eaves == 2 ? pillarH + 5 : storeys == 2 ? pillarH + 1 + upperH : pillarH);
		}
		sink.pop();
	}

	private void platform(Ctx ctx, Sink sink, int opx, int opz, int[] xs) {
		sink.phase(Phase.PLINTH);
		int ex = opx + 1;
		int ez = opz + 1;
		int bottom = -Math.max(1, platformH);
		for (int ix = -ex; ix <= ex; ix++) {
			for (int iz = -ez; iz <= ez; iz++) {
				// Foundation down to the ground when the plot is lower (never a floating hall; at most 8).
				int ground = sink.ground(ctx, ix, iz);
				for (int fy = Math.max(bottom - 8, ground + 1); fy < bottom - 1; fy++) {
					sink.set(ix, fy, iz, Palette.st(Palette.FILL_STONE));
				}
				for (int fy = bottom - 1; fy <= -1; fy++) {
					if (fy < bottom) {
						if (ground < bottom - 1) {
							sink.set(ix, fy, iz, Palette.st(Palette.STONE));
						}
						continue;
					}
					boolean edge = Math.abs(ix) == ex || Math.abs(iz) == ez;
					BlockState state = fy == -1 ? Palette.st(Palette.PLINTH) : Palette.st(edge ? Palette.STONE : Palette.FILL_STONE);
					sink.set(ix, fy, iz, state);
				}
			}
		}
		// Steps in front (and back) of the centre bay only.
		int mid = bays.length / 2;
		int half = bays.length % 2 == 1 ? bays[mid] / 2 - 1 : 1;
		if (frontSteps) {
			steps(sink, ez, half, 1);
		}
		if (backSteps) {
			steps(sink, ez, half, -1);
		}
	}

	private void steps(Sink sink, int ez, int half, int dir) {
		for (int i = 0; i < platformH; i++) {
			int sz = dir * (ez + 1 + i - stepsInset);
			int sy = -1 - i;
			for (int sx = -half; sx <= half; sx++) {
				sink.set(sx, sy, sz, Palette.stair(Palette.STONE_STAIRS, dir > 0 ? Direction.NORTH : Direction.SOUTH, false));
				for (int fy = sy - 1; fy >= -platformH; fy--) {
					sink.set(sx, fy, sz, Palette.st(Palette.STONE));
				}
				for (int fy = sy + 1; fy <= -1; fy++) {
					sink.air(sx, fy, sz);
				}
			}
		}
	}

	private int baseFor(int pillarTop, int ringD, int radius, boolean brackets) {
		int f = radius <= 4 ? 1 : radius <= 9 ? 3 : 5;
		int s = ringD <= f ? 1 + ringD : 1 + f + 2 * (ringD - f);
		int offset = s % 2 == 1 ? (s - 1) / 2 : s / 2 - 1;
		return pillarTop + (brackets ? 2 : 1) - offset;
	}

	private RoofBuilder singleEave(Ctx ctx, Sink sink, RoofMat mat, int px, int pz, int[] xs, int o) {
		frame(sink, px, pz, xs, pillarH, true);
		walls(sink, px, pz, xs, pillarH, pz - porch(), mat);
		int radius = Math.min(px, pz) + o;
		int base = baseFor(pillarH, o, radius, rank.brackets());
		RoofBuilder builder = new RoofBuilder(roof, -px - o, px + o, -pz - o, pz + o, base, mat).overhang(o).wall(-px, px, -pz, pz)
				.gableField(gableField).soffit(rank.painted());
		frieze(sink, builder, px, pz, xs, pillarH);
		closeAbove(sink, builder, px, pz - porch(), pillarH);
		builder.emit(sink);
		return builder;
	}

	private RoofBuilder twoStoreys(Ctx ctx, Sink sink, RoofMat mat, int px, int pz, int[] xs, int o) {
		frame(sink, px, pz, xs, pillarH, true);
		walls(sink, px, pz, xs, pillarH, pz, mat);
		// The skirt between the storeys (all +1, last +2), leaning on the ring.
		int so = Math.max(1, Math.min(2, o));
		RoofBuilder skirt = new RoofBuilder(RoofBuilder.Type.SKIRT, -px - so, px + so, -pz - so, pz + so, pillarH + 1, mat)
				.inner(-px, px, -pz, pz).overhang(so).wall(-px, px, -pz, pz);
		skirt.emit(sink);
		// The upper floor and the upper storey.
		int f2 = pillarH + 1;
		sink.phase(Phase.FRAME);
		for (int ix = -px + 1; ix <= px - 1; ix++) {
			for (int iz = -pz + 1; iz <= pz - 1; iz++) {
				sink.set(ix, pillarH, iz, Palette.st(Palette.WOOD));
			}
		}
		sink.set(px - 1, pillarH, -pz + 1, Palette.air());
		for (int ly = 0; ly < pillarH; ly++) {
			sink.set(px - 1, ly, -pz + 1, Palette.facing(Blocks.LADDER, Direction.SOUTH));
		}
		for (int px2 : new int[]{-px, px}) {
			for (int pz2 : new int[]{-pz, pz}) {
				for (int ly = f2; ly < f2 + upperH; ly++) {
					sink.set(px2, ly, pz2, Palette.st(Palette.PILLAR));
				}
			}
		}
		for (int pillarX : xs) {
			for (int pz2 : new int[]{-pz, pz}) {
				for (int ly = f2; ly < f2 + upperH; ly++) {
					sink.set(pillarX, ly, pz2, Palette.st(Palette.PILLAR));
				}
			}
		}
		sink.phase(Phase.WALL);
		Direction plate = Direction.SOUTH;
		for (int ix = -px; ix <= px; ix++) {
			for (int iz = -pz; iz <= pz; iz++) {
				boolean ring = Math.abs(ix) == px || Math.abs(iz) == pz;
				if (!ring || sink.solid(ix, f2, iz) && sink.get(ix, f2, iz).getBlock() == Palette.PILLAR) {
					continue;
				}
				for (int ly = f2; ly < f2 + upperH; ly++) {
					boolean window = ly > f2 && ly < f2 + upperH - 1;
					if (window) {
						Direction side = iz == pz ? Direction.SOUTH : iz == -pz ? Direction.NORTH : ix == px ? Direction.EAST : Direction.WEST;
						sink.set(ix, ly, iz, Palette.lattice(rank.lattice(), side));
					} else {
						sink.set(ix, ly, iz, Palette.st(ly == f2 ? Palette.LINTEL : Palette.PLASTER));
					}
				}
			}
		}
		int top = f2 + upperH;
		frame(sink, px, pz, xs, top, false);
		int radius = Math.min(px, pz) + o;
		int base = baseFor(top, o, radius, rank.brackets());
		RoofBuilder builder = new RoofBuilder(roof, -px - o, px + o, -pz - o, pz + o, base, mat).overhang(o).wall(-px, px, -pz, pz)
				.gableField(gableField).soffit(rank.painted());
		frieze(sink, builder, px, pz, xs, top);
		builder.emit(sink);
		return builder;
	}

	private RoofBuilder doubleEave(Ctx ctx, Sink sink, RoofMat mat, int px, int pz, int[] xs) {
		int cpx = px + 3;
		int cpz = pz + 3;
		int h1 = pillarH;
		// Outer colonnade (副阶周匝): pillars every 4 along the ring.
		sink.phase(Phase.FRAME);
		for (int ix = -cpx; ix <= cpx; ix++) {
			for (int iz = -cpz; iz <= cpz; iz++) {
				boolean ring = Math.abs(ix) == cpx || Math.abs(iz) == cpz;
				if (!ring) {
					continue;
				}
				boolean pillarHere;
				if (Math.abs(iz) == cpz) {
					pillarHere = Math.abs(ix) == cpx || isPillarX(ix, xs);
				} else {
					pillarHere = (cpz - Math.abs(iz)) % 4 == 0;
				}
				if (pillarHere) {
					sink.set(ix, 0, iz, Palette.st(Palette.PILLAR_BASE));
					for (int ly = 1; ly < h1; ly++) {
						sink.set(ix, ly, iz, Palette.st(Palette.PILLAR));
					}
				}
				sink.set(ix, h1, iz, Palette.st(Palette.BEAM));
			}
		}
		int base1 = h1 + 1;
		int yb = base1 + 5;
		// Core pillars run up to the upper beam band.
		frameCore(sink, px, pz, xs, yb);
		walls(sink, px, pz, xs, h1, pz, mat);
		// Clerestory: plaster on the core ring from the colonnade beam level to the upper beam band.
		sink.phase(Phase.WALL);
		for (int ix = -px; ix <= px; ix++) {
			for (int iz = -pz; iz <= pz; iz++) {
				boolean ring = Math.abs(ix) == px || Math.abs(iz) == pz;
				if (!ring || isPillar(ix, iz, px, pz, xs)) {
					continue;
				}
				for (int ly = h1; ly < yb; ly++) {
					sink.set(ix, ly, iz, Palette.st(Palette.PLASTER));
				}
			}
		}
		// Lower skirt over the colonnade, leaning on the core.
		RoofBuilder skirt = new RoofBuilder(RoofBuilder.Type.SKIRT, -cpx - 2, cpx + 2, -cpz - 2, cpz + 2, base1, mat).inner(-px, px, -pz, pz)
				.overhang(2).wall(-cpx, cpx, -cpz, cpz).soffit(rank.painted());
		// Lower brackets on the colonnade.
		sink.phase(Phase.FRAME);
		for (int ix = -cpx; ix <= cpx; ix++) {
			for (int iz = -cpz; iz <= cpz; iz++) {
				boolean ring = Math.abs(ix) == cpx || Math.abs(iz) == cpz;
				if (!ring) {
					continue;
				}
				int piece = skirt.pieceY(ix, iz);
				for (int ly = h1 + 1; ly < piece; ly++) {
					sink.set(ix, ly, iz, bracket(ix, iz, cpx, cpz));
				}
			}
		}
		skirt.emit(sink);
		// Upper roof: eave tip 1 inside the lower tip, at least 3 above the skirt.
		int o2 = 4;
		int radius = Math.min(px, pz) + o2;
		int base2 = baseFor(yb, o2, radius, true);
		RoofBuilder upper = new RoofBuilder(roof, -px - o2, px + o2, -pz - o2, pz + o2, base2, mat).overhang(o2).wall(-px, px, -pz, pz)
				.gableField(gableField).soffit(rank.painted());
		frieze(sink, upper, px, pz, xs, yb);
		upper.emit(sink);
		return upper;
	}

	private boolean isPillarX(int ix, int[] xs) {
		for (int pillarX : xs) {
			if (Math.abs(ix) == Math.abs(pillarX)) {
				return true;
			}
		}
		return false;
	}

	/** Ring pillar positions: every bay pillar on the front and back rows, plus the side rows every 4 or so. */
	private boolean isPillar(int ix, int iz, int px, int pz, int[] xs) {
		if (Math.abs(iz) == pz) {
			for (int pillarX : xs) {
				if (pillarX == ix) {
					return true;
				}
			}
			return false;
		}
		if (Math.abs(ix) == px) {
			int span = 2 * pz;
			if (span >= 10) {
				return iz == 0 && span % 2 == 0 || Math.abs(iz) == pz;
			}
		}
		return false;
	}

	/** Pillars on bases and the beam band of the ring at {@code top}. */
	private void frame(Sink sink, int px, int pz, int[] xs, int top, boolean bases) {
		sink.phase(Phase.FRAME);
		for (int ix = -px; ix <= px; ix++) {
			for (int iz = -pz; iz <= pz; iz++) {
				boolean ring = Math.abs(ix) == px || Math.abs(iz) == pz;
				if (!ring) {
					continue;
				}
				if (bases && isPillar(ix, iz, px, pz, xs)) {
					sink.set(ix, 0, iz, Palette.st(Palette.PILLAR_BASE));
					for (int ly = 1; ly < top; ly++) {
						sink.set(ix, ly, iz, Palette.st(Palette.PILLAR));
					}
				}
				boolean corner = Math.abs(ix) == px && Math.abs(iz) == pz;
				BlockState band;
				if (corner && rank.painted()) {
					band = Palette.st(Palette.glazed(DyeColor.CYAN));
				} else if (isPillar(ix, iz, px, pz, xs) && gilded()) {
					band = Palette.st(Palette.GILT_CAP);
				} else {
					band = Palette.st(Palette.BEAM);
				}
				sink.set(ix, top, iz, band);
			}
		}
		// Interior pillar rows (金柱) for halls of 5 or more bays.
		if (bays.length >= 5 && bases) {
			for (int i = 1; i < xs.length - 1; i++) {
				for (int iz : new int[]{-pz + 2, pz - Math.max(1, porch()) - 2}) {
					if (iz <= -pz || iz >= pz) {
						continue;
					}
					sink.set(xs[i], 0, iz, Palette.st(Palette.PILLAR_BASE));
					for (int ly = 1; ly <= top; ly++) {
						sink.set(xs[i], ly, iz, Palette.st(Palette.PILLAR));
					}
				}
			}
		}
	}

	/** Core pillars of a double-eave hall (from the floor to the upper beam band). */
	private void frameCore(Sink sink, int px, int pz, int[] xs, int top) {
		frame(sink, px, pz, xs, top, true);
	}

	/** Bracket rows between the beam band and the roof underside on the ring (the frieze rule of 21.6.3). */
	private void frieze(Sink sink, RoofBuilder roofBuilder, int px, int pz, int[] xs, int top) {
		sink.phase(Phase.FRAME);
		for (int ix = -px; ix <= px; ix++) {
			for (int iz = -pz; iz <= pz; iz++) {
				boolean ring = Math.abs(ix) == px || Math.abs(iz) == pz;
				if (!ring) {
					continue;
				}
				int piece = roofBuilder.pieceY(ix, iz);
				if (piece == Integer.MIN_VALUE) {
					continue;
				}
				for (int ly = top + 1; ly < piece; ly++) {
					sink.set(ix, ly, iz, rank.brackets() ? bracket(ix, iz, px, pz) : Palette.st(Palette.BEAM));
				}
			}
		}
	}

	/** A bracket (warped stair, HALF=TOP) on the ring, its tall side against the hall (21.6.4); corners are planks. */
	private static BlockState bracket(int ix, int iz, int px, int pz) {
		boolean xEdge = Math.abs(ix) == px;
		boolean zEdge = Math.abs(iz) == pz;
		if (xEdge && zEdge) {
			return Palette.st(Palette.BEAM);
		}
		Direction inward = zEdge ? (iz > 0 ? Direction.NORTH : Direction.SOUTH) : (ix > 0 ? Direction.WEST : Direction.EAST);
		return Palette.stair(Palette.BRACKET, inward, true);
	}

	/** Fills the front wall line (behind a porch) from the beam level up to the roof, so no gap shows. */
	private void closeAbove(Sink sink, RoofBuilder roofBuilder, int px, int zf, int top) {
		if (zf == halfDepth()) {
			return;
		}
		sink.phase(Phase.WALL);
		for (int ix = -px; ix <= px; ix++) {
			int piece = roofBuilder.pieceY(ix, zf);
			for (int ly = top; ly < piece; ly++) {
				sink.set(ix, ly, zf, Palette.st(ly == top ? Palette.BEAM : Palette.PLASTER));
			}
		}
	}

	/** Walls of the core: façade on the door line {@code zf}, plaster sides and back on a plinth, lattice windows. */
	private void walls(Sink sink, int px, int pz, int[] xs, int height, int zf, RoofMat mat) {
		sink.phase(Phase.WALL);
		int mid = bays.length / 2;
		boolean odd = bays.length % 2 == 1;
		// Façade bays.
		for (int b = 0; b < bays.length; b++) {
			int xa = xs[b];
			int xb = xs[b + 1];
			boolean doorBay = odd ? b == mid : (b == mid - 1 || b == mid);
			int inner = xb - xa - 1;
			if (zf != pz) {
				// Posts on the recessed door line (golden pillars behind the porch).
				for (int ly = 0; ly < height; ly++) {
					sink.set(xa, ly, zf, Palette.st(Palette.PILLAR));
					sink.set(xb, ly, zf, Palette.st(Palette.PILLAR));
				}
			}
			for (int cx = xa + 1; cx < xb; cx++) {
				int k = cx - xa - 1;
				boolean doorCell = doorBay && (inner <= 3 || k >= (inner - 3) / 2 && k < (inner - 3) / 2 + 3);
				for (int ly = 0; ly < height; ly++) {
					BlockState state;
					if (passage && doorCell && ly == 2) {
						state = Palette.air();
					} else if (doorCell && ly <= 1) {
						if (doors == Doors.NONE) {
							state = Palette.air();
						} else {
							int leaf = k - (inner <= 3 ? 0 : (inner - 3) / 2);
							state = Palette.door(Palette.DOOR, Direction.NORTH, ly == 1, doors == Doors.OPEN, leaf == 2);
						}
					} else if (ly == 0) {
						state = Palette.st(Palette.PLINTH);
					} else if (ly == 1) {
						state = Palette.st(Palette.WOOD);
					} else {
						state = Palette.lattice(rank.lattice(), Direction.SOUTH);
					}
					sink.set(cx, ly, zf, state);
				}
			}
		}
		// Side walls (x = ±px) from the back to the door line, back wall (z = -pz).
		for (int side : new int[]{-px, px}) {
			for (int iz = -pz + 1; iz < zf; iz++) {
				if (isPillar(side, iz, px, pz, xs)) {
					continue;
				}
				boolean window = (zf + pz) >= 6 && Math.abs(iz - (zf - pz) / 2) <= 1;
				for (int ly = 0; ly < height; ly++) {
					BlockState state;
					if (ly == 0) {
						state = Palette.st(Palette.PLINTH);
					} else if (window && ly >= 2 && ly <= Math.min(3, height - 2)) {
						state = Palette.lattice(rank.lattice(), side > 0 ? Direction.EAST : Direction.WEST);
					} else {
						state = Palette.st(Palette.PLASTER);
					}
					sink.set(side, ly, iz, state);
				}
			}
		}
		for (int b = 0; b < bays.length; b++) {
			int xa = xs[b];
			int xb = xs[b + 1];
			int centre = (xa + xb) / 2;
			boolean open = passage && (odd ? b == mid : (b == mid - 1 || b == mid));
			for (int cx = xa + 1; cx < xb; cx++) {
				boolean window = b != mid && Math.abs(cx - centre) <= 0 && xb - xa >= 4;
				int k = cx - xa - 1;
				int inner = xb - xa - 1;
				boolean openCell = open && (inner <= 3 || k >= (inner - 3) / 2 && k < (inner - 3) / 2 + 3);
				for (int ly = 0; ly < height; ly++) {
					BlockState state;
					if (openCell) {
						state = ly <= 2 ? Palette.air() : Palette.lattice(rank.lattice(), Direction.NORTH);
					} else if (ly == 0) {
						state = Palette.st(Palette.PLINTH);
					} else if (window && ly >= 2 && ly <= Math.min(3, height - 2)) {
						state = Palette.lattice(rank.lattice(), Direction.NORTH);
					} else {
						state = Palette.st(Palette.PLASTER);
					}
					sink.set(cx, ly, -pz, state);
				}
			}
		}
	}

	/** Hanging palace lanterns about every 7 cells: chain from the roof, shroomlight, cinnabar rim (no 2×2×2 cubes). */
	private void lights(Sink sink, RoofBuilder roofBuilder, int px, int pz, int zf, int ceiling) {
		sink.phase(Phase.INTERIOR);
		int spanX = 2 * px - 1;
		int spanZ = zf + pz - 1;
		int nx = Math.max(1, (spanX + 3) / 7);
		int nz = Math.max(1, (spanZ + 3) / 7);
		for (int i = 0; i < nx; i++) {
			int lx = -px + 1 + (int) Math.round((i + 0.5D) * spanX / nx - 0.5D);
			if (lx == 0 && (rank == Rank.C || rank == Rank.D) && nx > 1) {
				lx = lx + 1;
			}
			for (int j = 0; j < nz; j++) {
				int lz = -pz + 1 + (int) Math.round((j + 0.5D) * spanZ / nz - 0.5D);
				int top = storeys == 2 ? ceiling : roofBuilder.underside(lx, lz);
				if (top == Integer.MIN_VALUE) {
					continue;
				}
				int lampY = Math.max(3, ceiling - 1);
				for (int ly = top; ly > lampY; ly--) {
					sink.set(lx, ly, lz, Palette.ironChain());
				}
				sink.set(lx, lampY, lz, Palette.st(Palette.LIGHT));
				sink.set(lx, lampY - 1, lz, Palette.slab(Blocks.POLISHED_CINNABAR_SLAB, true));
			}
		}
	}

	/** A gilded-blackstone plaque frame with a wall sign in the middle, on the façade at {@code row} (21.7). */
	private void plaque(Sink sink, int zf, int row) {
		sink.phase(Phase.ORNAMENT);
		for (int ix = -1; ix <= 1; ix++) {
			sink.set(ix, row, zf, Palette.st(Palette.PLAQUE_FIELD));
		}
		if (rank == Rank.D) {
			sink.set(-2, row, zf, Palette.st(Palette.RAW_GOLD));
			sink.set(2, row, zf, Palette.st(Palette.RAW_GOLD));
		}
		sink.set(0, row, zf + 1, Palette.facing(Blocks.DARK_OAK_WALL_SIGN, Direction.SOUTH));
	}

	@Override
	public void decorate(Ctx ctx, Sink sink, int tier) {
		int floor = ctx.level(site) + platformH;
		sink.pushFacing(x, floor, z, front);
		RoofMat mat = ctx.roof(rank);
		int px = halfWidth();
		int pz = halfDepth();
		int o = eaves == 2 ? 4 : overhang();
		int top = eaves == 2 ? pillarH + 6 : storeys == 2 ? pillarH + 1 + upperH : pillarH;
		int radius = Math.min(px, pz) + o;
		RoofBuilder builder = new RoofBuilder(roof, -px - o, px + o, -pz - o, pz + o, baseFor(top, o, radius, rank.brackets() || eaves == 2), mat)
				.overhang(o).wall(-px, px, -pz, pz);
		if (tier == 4 && rank != Rank.A) {
			builder.windBells(sink);
		}
		if (tier == 7 && rank != Rank.A && eaves == 1) {
			// Red wall banners on the outer corner pillars of the façade (gates included): the T7 finery reaches every hall.
			// Each hangs on the face of a front corner pillar (its support), never in the air.
			int[] xs = pillarXs();
			int fz = pz + 1;
			int by = Math.max(1, pillarH - 2);
			BlockState banner = Palette.with(Palette.wallBanner(DyeColor.RED).defaultBlockState(),
					net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
			Phase previous = sink.phase();
			sink.phase(Phase.ORNAMENT);
			for (int bx : new int[]{xs[0], xs[xs.length - 1]}) {
				if (sink.get(bx, by, fz) == null) {
					sink.set(bx, by, fz, banner);
				}
			}
			sink.phase(previous);
		}
		if (tier == 9 && rank != Rank.A) {
			builder.eaveLanterns(sink, true, lanternXs(o));
		}
		sink.pop();
	}

	/**
	 * The front eave lantern columns: one between each pair of pillars, leaving out the plaque span (|x| &lt;= 2) when the
	 * lantern row lies in the plaque and sign planes (overhang 2 or less), so no chain hangs into the sign.
	 */
	private int[] lanternXs(int o) {
		int[] xs = pillarXs();
		boolean clearPlaque = plaque() && o <= 2;
		int[] lanterns = new int[xs.length - 1];
		int count = 0;
		for (int i = 0; i < xs.length - 1; i++) {
			int mid = (xs[i] + xs[i + 1]) / 2;
			if (!clearPlaque || Math.abs(mid) > 2) {
				lanterns[count++] = mid;
			}
		}
		return java.util.Arrays.copyOf(lanterns, count);
	}

	/** The gilding of a rank C hall as a later component (template 1's T6): pearl, chiwen, beasts, gilt caps, eave lanterns. */
	public Part gilding() {
		Hall hall = this;
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				return hall.bounds(ctx);
			}

			@Override
			public String kind() {
				return "gilding";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				int floor = ctx.level(site) + platformH;
				sink.pushFacing(x, floor, z, front);
				RoofMat mat = ctx.roof(rank);
				int px = halfWidth();
				int pz = halfDepth();
				int o = overhang();
				int radius = Math.min(px, pz) + o;
				RoofBuilder builder = new RoofBuilder(roof, -px - o, px + o, -pz - o, pz + o, baseFor(pillarH, o, radius, rank.brackets()), mat)
						.overhang(o).wall(-px, px, -pz, pz);
				builder.gild(sink, true, true, ctx.glaze().beastCandle());
				// Gilt caps replace the hall's own beam ends: a structural phase, so the placer may swap them.
				sink.phase(Phase.FRAME);
				int[] xs = pillarXs();
				for (int pillarX : xs) {
					for (int iz : new int[]{-pz, pz}) {
						if (Math.abs(pillarX) != px) {
							sink.set(pillarX, pillarH, iz, Palette.st(Palette.GILT_CAP));
						}
					}
				}
				sink.phase(Phase.ORNAMENT);
				builder.eaveLanterns(sink, true, lanternXs(o));
				sink.pop();
			}
		};
	}

	/** The template-local floor level of this hall. */
	public int floor(Ctx ctx) {
		return ctx.level(site) + platformH;
	}

	public int x() {
		return x;
	}

	public int z() {
		return z;
	}

	public Direction front() {
		return front;
	}

	/** Core half width and depth (for interiors placed by other components, such as the T8 ancestral altar). */
	public int coreHalfWidth() {
		return halfWidth();
	}

	public int coreHalfDepth() {
		return halfDepth();
	}

	public @Nullable String anchorId() {
		return anchor;
	}
}
