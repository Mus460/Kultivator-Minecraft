package dev.kultivator.sect.build.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * An open pavilion (ting, DESIGN 21.9 {@code Pavilion}): a stone platform, thin cinnabar pillars on the outline's corners,
 * a low balustrade with benches between them (one side left open as the entry), and a steep-topped pavilion roof
 * ({@link RoofBuilder.Profile#PAVILION}, the last column +3) with a gold apex finial. Template-local, centred on (x, z).
 * <ul>
 * <li>{@link #size}: the pillar square side (odd, 3-9); the eave outline is 2 wider each side.</li>
 * <li>{@link #sides}: 4 (pyramid roof), 6 or 8 (octagonal roof with chamfered corners), 0 = ROUND (round roof, pillars on a
 * circle), -1 = FAN (a round roof over a half disc, 扇面亭).</li>
 * <li>{@link #eaves}: 1-3; extra eaves are skirts stacked under the top roof.</li>
 * <li>{@link #seat}: a {@link QiSeat} of tier 1-3 in the middle (the meditation pavilion's Qi seat tier, DESIGN 21.2 T8).</li>
 * <li>{@link #waterside}: a 水榭 standing over water on stone piers (no platform fill; the pond part supplies the water).</li>
 * <li>{@link #entry}: which side stays open (default the front, +z).</li>
 * </ul>
 * Roof : body ≥ 1.2 for pavilions (DESIGN 21.6.1). Art direction from real gardens, not canon.
 */
public final class Pavilion implements Part {

	private final int x;
	private final int z;
	private final Lv level;
	private int size = 5;
	private int sides = 4;
	private int eaves = 1;
	private int seat;
	private boolean waterside;
	private int pillarH = 4;
	private Rank rank = Rank.B;
	private Direction entry = Direction.SOUTH;
	private String anchor;

	public Pavilion(int x, int z, Lv level) {
		this.x = x;
		this.z = z;
		this.level = level;
	}

	public Pavilion size(int size) {
		this.size = Math.max(3, Math.min(11, size | 1));
		return this;
	}

	public Pavilion sides(int sides) {
		this.sides = sides;
		return this;
	}

	public Pavilion eaves(int eaves) {
		this.eaves = Math.max(1, Math.min(3, eaves));
		return this;
	}

	public Pavilion seat(int tier) {
		this.seat = Math.max(0, Math.min(3, tier));
		return this;
	}

	public Pavilion waterside(boolean waterside) {
		this.waterside = waterside;
		return this;
	}

	public Pavilion pillar(int height) {
		this.pillarH = Math.max(3, height);
		return this;
	}

	public Pavilion rank(Rank rank) {
		this.rank = rank;
		return this;
	}

	public Pavilion entry(Direction entry) {
		this.entry = entry;
		return this;
	}

	public Pavilion anchor(String id) {
		this.anchor = id;
		return this;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		int y = ctx.level(level);
		int h = size / 2 + 3;
		return new BoundingBox(x - h, y - 2, z - h, x + h, y + pillarH + size / 2 + 4 * eaves + 4, z + h);
	}

	@Override
	public String kind() {
		return "pavilion";
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		int floor = ctx.level(level);
		sink.push(x, floor, z, 0);
		int h = size / 2;
		boolean round = sides <= 0;
		boolean hexagon = sides == 6 && h >= 2;
		boolean octagon = sides == 8 || sides == 6 && !hexagon;
		int chamfer = Math.max(1, h / 2 + (h >= 3 ? 1 : 0));
		if (hexagon) {
			// A real hexagon (DESIGN 21.8 rule 6): pointed toward ±x, flat ±z edges of half length (h + 1) / 2.
			emitHexagon(ctx, sink, h);
			sink.pop();
			return;
		}
		RoofMat mat = ctx.roof(rank);
		// Platform (or piers over water).
		sink.phase(Phase.PLINTH);
		for (int dx = -h - 1; dx <= h + 1; dx++) {
			for (int dz = -h - 1; dz <= h + 1; dz++) {
				if (!inShape(dx, dz, h + 1, round, octagon, chamfer + 1)) {
					continue;
				}
				boolean edge = !inShape(dx, dz, h, round, octagon, chamfer);
				if (waterside) {
					sink.set(dx, -1, dz, Palette.st(edge ? Blocks.STONE_BRICK_SLAB : Palette.WOOD));
					if (edge && (Math.abs(dx) + Math.abs(dz)) % 3 == 0) {
						for (int py = -2; py >= -3; py--) {
							sink.set(dx, py, dz, Palette.st(Palette.STONE));
						}
					}
				} else {
					sink.set(dx, -1, dz, Palette.st(edge ? Palette.STONE : Palette.PLINTH));
					sink.set(dx, -2, dz, Palette.st(Palette.STONE));
				}
			}
		}
		if (waterside) {
			for (int dx = -h - 1; dx <= h + 1; dx++) {
				for (int dz = -h - 1; dz <= h + 1; dz++) {
					if (inShape(dx, dz, h + 1, round, octagon, chamfer + 1)) {
						sink.set(dx, -1, dz, Palette.slab(Palette.WOOD_SLAB, true));
					}
				}
			}
		}
		// Pillars on the outline corners (or around the circle).
		sink.phase(Phase.FRAME);
		int[][] posts = posts(h, round, octagon, chamfer);
		for (int[] post : posts) {
			for (int py = 0; py < pillarH; py++) {
				sink.set(post[0], py, post[1], Palette.st(py == 0 ? Palette.PILLAR_BASE : Palette.PILLAR_THIN));
			}
			sink.set(post[0], pillarH, post[1], Palette.st(Palette.BEAM));
		}
		// A beam ring at the pillar tops, a balustrade and benches (the entry side open).
		for (int dx = -h; dx <= h; dx++) {
			for (int dz = -h; dz <= h; dz++) {
				if (!inShape(dx, dz, h, round, octagon, chamfer) || inShape(dx, dz, h - 1, round, octagon, Math.max(1, chamfer - 1))) {
					continue;
				}
				sink.set(dx, pillarH, dz, Palette.st(Palette.BEAM));
				boolean isPost = false;
				for (int[] post : posts) {
					if (post[0] == dx && post[1] == dz) {
						isPost = true;
					}
				}
				boolean entrySide = entry == Direction.SOUTH && dz == h || entry == Direction.NORTH && dz == -h || entry == Direction.EAST && dx == h
						|| entry == Direction.WEST && dx == -h;
				if (!isPost && !entrySide && (seat == 0 || !round)) {
					sink.phase(Phase.WALL);
					sink.set(dx, 0, dz, Palette.slab(Palette.WOOD_SLAB, false));
					sink.phase(Phase.FRAME);
				}
				if (!isPost && entrySide && Math.abs(entry.getAxis() == Direction.Axis.Z ? dx : dz) <= 1) {
					sink.air(dx, 0, dz);
				}
			}
		}
		// Roofs: skirts for the lower eaves, then the steep top.
		int o = 2;
		int outer = h + o;
		int base = pillarH + 1;
		Block lattice = Blocks.POPLAR_TRAPDOOR;
		for (int e = 1; e < eaves; e++) {
			RoofBuilder skirt = new RoofBuilder(RoofBuilder.Type.SKIRT, -outer, outer, -outer, outer, base, mat).inner(-h, h, -h, h).overhang(o)
					.wall(-h, h, -h, h);
			if (octagon || round) {
				skirt.octagon(chamfer + o, chamfer);
			}
			skirt.emit(sink);
			// The core rises one storey of lattice above each skirt.
			sink.phase(Phase.WALL);
			for (int dx = -h; dx <= h; dx++) {
				for (int dz = -h; dz <= h; dz++) {
					if (!inShape(dx, dz, h, round, octagon, chamfer) || inShape(dx, dz, h - 1, round, octagon, Math.max(1, chamfer - 1))) {
						continue;
					}
					for (int py = base + 2; py < base + 4; py++) {
						Direction side = Math.abs(dz) >= Math.abs(dx) ? (dz > 0 ? Direction.SOUTH : Direction.NORTH) : (dx > 0 ? Direction.EAST : Direction.WEST);
						sink.set(dx, py, dz, Palette.lattice(lattice, side));
					}
					sink.set(dx, base + 4, dz, Palette.st(Palette.BEAM));
				}
			}
			for (int[] post : posts) {
				for (int py = pillarH; py < base + 4; py++) {
					sink.set(post[0], py, post[1], Palette.st(Palette.PILLAR_THIN));
				}
			}
			base += 4;
		}
		RoofBuilder.Type type = round ? RoofBuilder.Type.ROUND : octagon ? RoofBuilder.Type.OCTAGON : RoofBuilder.Type.PYRAMID;
		RoofBuilder roof = new RoofBuilder(type, -outer, outer, -outer, outer, base, mat).overhang(o).wall(-h, h, -h, h)
				.profile(RoofBuilder.Profile.PAVILION).chamfer(chamfer + o).ridgeLight(false);
		// Close the frieze between the last beam ring and the roof on the pillar ring.
		sink.phase(Phase.FRAME);
		int beamY = eaves > 1 ? base : pillarH;
		for (int dx = -h; dx <= h; dx++) {
			for (int dz = -h; dz <= h; dz++) {
				if (!inShape(dx, dz, h, round, octagon, chamfer) || inShape(dx, dz, h - 1, round, octagon, Math.max(1, chamfer - 1))) {
					continue;
				}
				int piece = roof.pieceY(dx, dz);
				for (int py = beamY + 1; py < piece && py < beamY + 4; py++) {
					sink.set(dx, py, dz, Palette.st(Palette.BEAM));
				}
			}
		}
		roof.emit(sink);
		roof.gild(sink, true, false, null);
		// Under-roof light: a lantern hanging from the apex.
		sink.phase(Phase.INTERIOR);
		int under = roof.underside(0, 0);
		if (under != Integer.MIN_VALUE && under > pillarH + 1) {
			for (int py = under; py > pillarH; py--) {
				sink.set(0, py, 0, Palette.ironChain());
			}
			sink.set(0, pillarH, 0, Palette.lantern(Blocks.LANTERN, true));
		}
		if (seat > 0) {
			QiSeat.emitAt(sink, 0, 0, 0, seat);
			sink.anchor(anchor != null ? anchor : "meditation", 0, 0, 1);
		} else if (anchor != null) {
			sink.anchor(anchor, 0, 0, 0);
		}
		sink.pop();
	}

	@Override
	public void decorate(Ctx ctx, Sink sink, int tier) {
		if (tier != 4) {
			return;
		}
		int floor = ctx.level(level);
		sink.push(x, floor, z, 0);
		int h = size / 2;
		int outer = h + 2;
		boolean round = sides <= 0;
		boolean hexagon = sides == 6 && h >= 2;
		boolean octagon = sides == 8 || sides == 6 && !hexagon;
		int chamfer = Math.max(1, h / 2 + (h >= 3 ? 1 : 0));
		RoofBuilder.Type type = round ? RoofBuilder.Type.ROUND : hexagon ? RoofBuilder.Type.HEXAGON : octagon ? RoofBuilder.Type.OCTAGON : RoofBuilder.Type.PYRAMID;
		int base = pillarH + 1 + 4 * (eaves - 1);
		new RoofBuilder(type, -outer, outer, -outer, outer, base, ctx.roof(rank)).overhang(2).profile(RoofBuilder.Profile.PAVILION)
				.chamfer(chamfer + 2).hexagon((outer + 1) / 2).windBells(sink);
		sink.pop();
	}

	/** Inside a hexagon of half size {@code h} pointed toward ±x (flat ±z edges of half length {@code (h + 1) / 2}). */
	private static boolean inHex(int dx, int dz, int h) {
		if (h < 0 || Math.abs(dx) > h || Math.abs(dz) > h) {
			return false;
		}
		int c = (h + 1) / 2;
		int p = h - Math.abs(dx);
		int q = h - Math.abs(dz);
		return p * h + q * (h - c) >= h * (h - c);
	}

	/** The hexagonal pavilion: platform, six posts on the vertices, rails, the lower skirts and the hexagonal top roof. */
	private void emitHexagon(Ctx ctx, Sink sink, int h) {
		RoofMat mat = ctx.roof(rank);
		int c = (h + 1) / 2;
		sink.phase(Phase.PLINTH);
		for (int dx = -h - 1; dx <= h + 1; dx++) {
			for (int dz = -h - 1; dz <= h + 1; dz++) {
				if (!inHex(dx, dz, h + 1)) {
					continue;
				}
				boolean edge = !inHex(dx, dz, h);
				if (waterside) {
					sink.set(dx, -1, dz, Palette.slab(Palette.WOOD_SLAB, true));
					if (edge && (Math.abs(dx) + Math.abs(dz)) % 3 == 0) {
						for (int py = -2; py >= -3; py--) {
							sink.set(dx, py, dz, Palette.st(Palette.STONE));
						}
					}
				} else {
					sink.set(dx, -1, dz, Palette.st(edge ? Palette.STONE : Palette.PLINTH));
					sink.set(dx, -2, dz, Palette.st(Palette.STONE));
				}
			}
		}
		sink.phase(Phase.FRAME);
		int[][] posts = {{-h, 0}, {h, 0}, {-c, -h}, {c, -h}, {-c, h}, {c, h}};
		for (int[] post : posts) {
			for (int py = 0; py < pillarH; py++) {
				sink.set(post[0], py, post[1], Palette.st(py == 0 ? Palette.PILLAR_BASE : Palette.PILLAR_THIN));
			}
			sink.set(post[0], pillarH, post[1], Palette.st(Palette.BEAM));
		}
		for (int dx = -h; dx <= h; dx++) {
			for (int dz = -h; dz <= h; dz++) {
				if (!inHex(dx, dz, h) || inHex(dx, dz, h - 1)) {
					continue;
				}
				sink.set(dx, pillarH, dz, Palette.st(Palette.BEAM));
				boolean isPost = false;
				for (int[] post : posts) {
					isPost |= post[0] == dx && post[1] == dz;
				}
				boolean entrySide = entry == Direction.SOUTH && dz > 0 && Math.abs(dx) <= c || entry == Direction.NORTH && dz < 0 && Math.abs(dx) <= c
						|| entry == Direction.EAST && dx > 0 && dz == 0 || entry == Direction.WEST && dx < 0 && dz == 0;
				if (!isPost && !entrySide && seat == 0) {
					sink.phase(Phase.WALL);
					sink.set(dx, 0, dz, Palette.slab(Palette.WOOD_SLAB, false));
					sink.phase(Phase.FRAME);
				} else if (!isPost && entrySide) {
					sink.air(dx, 0, dz);
				}
			}
		}
		int o = 2;
		int outer = h + o;
		int base = pillarH + 1;
		for (int e = 1; e < eaves; e++) {
			// Lower eaves: an octagonal skirt (the closest skirt outline) with a lattice storey above.
			RoofBuilder skirt = new RoofBuilder(RoofBuilder.Type.SKIRT, -outer, outer, -outer, outer, base, mat).inner(-h, h, -h, h).overhang(o)
					.wall(-h, h, -h, h);
			skirt.octagon(c + o, c);
			skirt.emit(sink);
			sink.phase(Phase.WALL);
			for (int dx = -h; dx <= h; dx++) {
				for (int dz = -h; dz <= h; dz++) {
					if (!inHex(dx, dz, h) || inHex(dx, dz, h - 1)) {
						continue;
					}
					for (int py = base + 2; py < base + 4; py++) {
						Direction side = Math.abs(dz) >= Math.abs(dx) ? (dz > 0 ? Direction.SOUTH : Direction.NORTH) : (dx > 0 ? Direction.EAST : Direction.WEST);
						sink.set(dx, py, dz, Palette.lattice(Blocks.POPLAR_TRAPDOOR, side));
					}
					sink.set(dx, base + 4, dz, Palette.st(Palette.BEAM));
				}
			}
			for (int[] post : posts) {
				for (int py = pillarH; py < base + 4; py++) {
					sink.set(post[0], py, post[1], Palette.st(Palette.PILLAR_THIN));
				}
			}
			base += 4;
		}
		RoofBuilder roof = new RoofBuilder(RoofBuilder.Type.HEXAGON, -outer, outer, -outer, outer, base, mat).overhang(o).wall(-h, h, -h, h)
				.profile(RoofBuilder.Profile.PAVILION).hexagon((outer + 1) / 2).ridgeLight(false);
		sink.phase(Phase.FRAME);
		int beamY = eaves > 1 ? base : pillarH;
		for (int dx = -h; dx <= h; dx++) {
			for (int dz = -h; dz <= h; dz++) {
				if (!inHex(dx, dz, h) || inHex(dx, dz, h - 1)) {
					continue;
				}
				int piece = roof.pieceY(dx, dz);
				for (int py = beamY + 1; py < piece && py < beamY + 4; py++) {
					sink.set(dx, py, dz, Palette.st(Palette.BEAM));
				}
			}
		}
		roof.emit(sink);
		roof.gild(sink, true, false, null);
		sink.phase(Phase.INTERIOR);
		int under = roof.underside(0, 0);
		if (under != Integer.MIN_VALUE && under > pillarH + 1) {
			for (int py = under; py > pillarH; py--) {
				sink.set(0, py, 0, Palette.ironChain());
			}
			sink.set(0, pillarH, 0, Palette.lantern(Blocks.LANTERN, true));
		}
		if (seat > 0) {
			QiSeat.emitAt(sink, 0, 0, 0, seat);
			sink.anchor(anchor != null ? anchor : "meditation", 0, 0, 1);
		} else if (anchor != null) {
			sink.anchor(anchor, 0, 0, 0);
		}
	}

	private static boolean inShape(int dx, int dz, int h, boolean round, boolean octagon, int chamfer) {
		if (Math.abs(dx) > h || Math.abs(dz) > h) {
			return false;
		}
		if (round) {
			return dx * dx + dz * dz <= h * h + h;
		}
		if (octagon) {
			int a = h - Math.abs(dx);
			int b = h - Math.abs(dz);
			return a + b >= chamfer;
		}
		return true;
	}

	private static int[][] posts(int h, boolean round, boolean octagon, int chamfer) {
		if (round || octagon) {
			int c = round ? Math.max(1, (int) Math.round(h * 0.3D)) : chamfer;
			return new int[][]{{-h + c, -h}, {h - c, -h}, {-h + c, h}, {h - c, h}, {-h, -h + c}, {-h, h - c}, {h, -h + c}, {h, h - c}};
		}
		return new int[][]{{-h, -h}, {h, -h}, {-h, h}, {h, h}};
	}
}
