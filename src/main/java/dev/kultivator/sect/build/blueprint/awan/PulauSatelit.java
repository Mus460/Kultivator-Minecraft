package dev.kultivator.sect.build.blueprint.awan;

import dev.kultivator.sect.build.blueprint.BambooGrove;
import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.Grounds;
import dev.kultivator.sect.build.blueprint.Hall;
import dev.kultivator.sect.build.blueprint.Interiors;
import dev.kultivator.sect.build.blueprint.LanternPost;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Pavilion;
import dev.kultivator.sect.build.blueprint.Paving;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Rank;
import dev.kultivator.sect.build.blueprint.RoofBuilder;
import dev.kultivator.sect.build.blueprint.Sink;
import dev.kultivator.sect.build.blueprint.Stamp;
import dev.kultivator.sect.build.blueprint.Tree;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * What stands on a satellite island (DESIGN 37.4.3), in the satellite's own frame: its front faces the main island (the
 * bridge lands at the front, a stone walk leads in from it and stays free), x across.
 * <ul>
 * <li>KEBUN "Taman Gantung" (T2): the Hundred Medicine beds, a tea pavilion, a bamboo grove, jade lanterns; at T3 the spirit
 * beds of DESIGN 36 W ({@code sx_kebun_petak}: anchors {@code kebun_petak_<n>}, {@code kebun_rawat_<k>}, the same numbering
 * as the ground plots).</li>
 * <li>ALKIMIA "Paviliun Tungku" (T4): a two-storey alchemy hall (furnaces and a brewing stand inside, the other brewing
 * stands on stone tables in its court).</li>
 * <li>ARENA "Gelanggang Awan" (T5): the leitai stage; at T7 the stands of DESIGN 36 T ({@code sx_turnamen_tribun}: anchors
 * {@code turnamen_pusat}, {@code kursi_<n>}, {@code panji_arena_<n>}).</li>
 * <li>LELUHUR (T8): the ancestral hall (anchor {@code leluhur}) and DESIGN 36 A's graves ({@code sx_paviliun_makam}:
 * {@code makam_<n>}, {@code makam_pusat}, {@code makam_tugu}).</li>
 * <li>ASRAMA "Pondok Bangau" (T8): two to four dorm halls round a court with a dry well (anchors {@code asrama_<n>}).</li>
 * <li>TAMBANG "Daiyu" (T9): a rocky island round the Root Hollow (built by {@code sx_kebun_tambang}).</li>
 * </ul>
 * A birch plaque at the landing names the island (the engine writes the name: Penglai, Fangzhang, Yingzhou, Daiyu and
 * Yuanjiao on A7-A9, after the <i>Liezi</i>).
 */
public final class PulauSatelit implements Part {

	/** Which layer of a satellite this part draws. */
	public enum Isi {
		/** The function's buildings (with the satellite's own component). */
		ISI,
		/** {@code sx_kebun_petak} on KEBUN. */
		PETAK,
		/** {@code sx_turnamen_tribun} on ARENA. */
		TRIBUN,
		/** {@code sx_paviliun_makam} on LELUHUR. */
		MAKAM
	}

	private final AwanLayout.Fungsi fungsi;
	private final Isi isi;

	public PulauSatelit(AwanLayout.Fungsi fungsi, Isi isi) {
		this.fungsi = fungsi;
		this.isi = isi;
	}

	@Override
	public String kind() {
		return "satellite_" + fungsi.id;
	}

	@Override
	public BoundingBox bounds(Ctx ctx) {
		AwanLayout layout = AwanLayout.of(ctx.template());
		AwanLayout.Satelit s = layout.satellite(fungsi);
		if (s == null) {
			return new BoundingBox(0, 0, 0, 0, 0, 0);
		}
		AwanLayout.Pulau p = s.pulau();
		int r = (int) Math.ceil(p.radius() * 1.1D) + 1;
		return new BoundingBox(p.cx - r, p.level - 4, p.cz - r, p.cx + r, p.level + 20, p.cz + r);
	}

	/** The satellite frame: template-local position of (fx across, fz toward the main island). */
	private record Frame(int cx, int cz, Direction front, int level, AwanLayout.Pulau island) {
		int x(int fx, int fz) {
			Direction right = front.getClockWise();
			return cx + right.getStepX() * fx + front.getStepX() * fz;
		}

		int z(int fx, int fz) {
			Direction right = front.getClockWise();
			return cz + right.getStepZ() * fx + front.getStepZ() * fz;
		}

		Lv lv() {
			return Lv.at(level);
		}

		/** True when the frame cell and its neighbours within {@code margin} stand on the island's inner rings. */
		boolean on(int fx, int fz, int margin) {
			for (int a = -margin; a <= margin; a++) {
				for (int b = -margin; b <= margin; b++) {
					int ring = island.ring(x(fx + a, fz + b), z(fx + a, fz + b));
					if (ring < 0 || ring > 2) {
						return false;
					}
				}
			}
			return true;
		}

		/** The template-local box of a frame rectangle. */
		int[] box(int fx0, int fz0, int fx1, int fz1) {
			int ax = x(fx0, fz0);
			int az = z(fx0, fz0);
			int bx = x(fx1, fz1);
			int bz = z(fx1, fz1);
			return new int[]{Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz)};
		}
	}

	@Override
	public void emit(Ctx ctx, Sink sink) {
		AwanLayout layout = AwanLayout.of(ctx.template());
		AwanLayout.Satelit s = layout.satellite(fungsi);
		if (s == null) {
			return;
		}
		AwanLayout.Pulau p = s.pulau();
		Frame f = new Frame(p.cx, p.cz, s.toMain(), p.level, p);
		int r = s.r();
		Phase previous = sink.phase();
		switch (isi) {
			case ISI -> {
				walk(ctx, sink, f, r);
				switch (fungsi) {
					case KEBUN -> kebun(ctx, sink, f, r);
					case ALKIMIA -> alkimia(ctx, sink, f, r, layout);
					case ARENA -> arena(ctx, sink, f, r, layout);
					case LELUHUR -> leluhur(ctx, sink, f, r);
					case ASRAMA -> asrama(ctx, sink, f, r);
					case TAMBANG -> tambang(ctx, sink, f, r);
				}
			}
			case PETAK -> petak(sink, f, r, layout);
			case TRIBUN -> tribun(sink, f, layout);
			case MAKAM -> makam(sink, f, r, layout);
		}
		sink.phase(previous);
	}

	// ================================================================== common

	/** The stone walk from the landing to the centre, the name plaque, two lanterns and leaning pines on the far rim. */
	private void walk(Ctx ctx, Sink sink, Frame f, int r) {
		int[] b = f.box(-1, 0, 1, r - 1);
		new Paving(b[0], b[1], b[2], b[3], f.lv(), Paving.Pattern.PATH).earth(false).emit(ctx, sink);
		sink.phase(Phase.FRAME);
		int px = f.x(2, r - 3);
		int pz = f.z(2, r - 3);
		sink.set(px, f.level(), pz, Palette.st(Blocks.POLISHED_ANDESITE));
		sink.set(px, f.level() + 1, pz, Palette.st(Blocks.CHISELED_STONE_BRICKS));
		sink.phase(Phase.ORNAMENT);
		sink.set(f.x(2, r - 2), f.level() + 1, f.z(2, r - 2), Palette.facing(Blocks.BIRCH_WALL_SIGN, f.front()));
		LanternPost.emitOne(sink, f.x(-2, r - 4), f.level(), f.z(-2, r - 4), LanternPost.Style.STONE, f.front());
		for (int i = 0; i < 2; i++) {
			// The leaning pines on the far rim: as far out as the trunk still stands on the island (never on the rim band).
			int side = i == 0 ? -1 : 1;
			for (int in = 3; in <= r - 2; in++) {
				int fx = side * (r - in);
				int fz = -(r - in - 1);
				if (f.on(fx, fz, 1)) {
					new Tree(Tree.Kind.PINE, f.x(fx, fz), f.z(fx, fz), f.lv()).force(true).emit(ctx, sink);
					break;
				}
			}
		}
	}

	// ================================================================== functions

	private void kebun(Ctx ctx, Sink sink, Frame f, int r) {
		// Herb beds across the middle, the tea pavilion at the back, bamboo on one side.
		int[] beds = f.box(-(r - 4), -2, -2, 2);
		Grounds.herbBeds(beds[0], beds[1], beds[2], beds[3], f.lv()).emit(ctx, sink);
		int[] beds2 = f.box(2, -2, r - 4, 2);
		Grounds.herbBeds(beds2[0], beds2[1], beds2[2], beds2[3], f.lv()).emit(ctx, sink);
		new Pavilion(f.x(0, -(r - 4)), f.z(0, -(r - 4)), f.lv()).size(3).sides(4).pillar(3).rank(Rank.B).entry(f.front()).emit(ctx, sink);
		int[] bamboo = f.box(r - 5, -(r - 5), r - 3, -(r - 6));
		new BambooGrove(bamboo[0], bamboo[1], bamboo[2], bamboo[3], f.lv()).maxHeight(7).emit(ctx, sink);
		// Jade lanterns on the right of the walk (the left becomes the spirit fields at T3).
		LanternPost lanterns = new LanternPost(LanternPost.Style.JADE, f.lv());
		lanterns.at(f.x(3, 4), f.z(3, 4)).at(f.x(r - 3, 0), f.z(r - 3, 0));
		lanterns.emit(ctx, sink);
	}

	/** Beds per sky template (as DESIGN 36 W's ground plots: 4 / 6 / 8 / 12). */
	static int beds(int n) {
		return n <= 3 ? 4 : n <= 5 ? 6 : n <= 7 ? 8 : 12;
	}

	/**
	 * DESIGN 36 W's spirit beds (contract C36.W3): {@code cols × rows} beds of mud in a mud-brick frame, raised half a
	 * block with mud-brick slab curbs; beds 2k-1 and 2k share gardener k ({@code kebun_rawat_<k>}). Left half, centred.
	 */
	private void petak(Sink sink, Frame f, int r, AwanLayout layout) {
		int n = beds(layout.row.n());
		// The grid on the left of the walk, centred front to back so it stays on the island (4 = 1x4, 6 = 2x3, 8 = 2x4,
		// 12 = 3x4 beds); the T2 herb beds on that side give way to it.
		int cols = n <= 4 ? 1 : n <= 8 ? 2 : 3;
		int rows = (n + cols - 1) / cols;
		int fx0 = -2 - 2 * cols;
		int fz0 = -rows;
		sink.phase(Phase.PLINTH);
		for (int i = 0; i <= 2 * cols; i++) {
			for (int j = 0; j <= 2 * rows; j++) {
				int x = f.x(fx0 + i, fz0 + j);
				int z = f.z(fx0 + i, fz0 + j);
				boolean bed = i % 2 == 1 && j % 2 == 1;
				if (bed) {
					sink.set(x, f.level() - 1, z, Palette.st(Blocks.MUD));
					sink.air(x, f.level(), z);
				} else {
					sink.set(x, f.level() - 1, z, Palette.st(Blocks.MUD_BRICKS));
				}
			}
		}
		sink.phase(Phase.WALL);
		for (int i = 0; i <= 2 * cols; i++) {
			for (int j = 0; j <= 2 * rows; j++) {
				boolean bed = i % 2 == 1 && j % 2 == 1;
				boolean corner = (i == 0 || i == 2 * cols) && (j == 0 || j == 2 * rows);
				if (!bed && !corner) {
					sink.set(f.x(fx0 + i, fz0 + j), f.level(), f.z(fx0 + i, fz0 + j), Palette.slab(Blocks.MUD_BRICK_SLAB, false));
				}
			}
		}
		sink.phase(Phase.ORNAMENT);
		for (int[] c : new int[][]{{0, 0}, {2 * cols, 0}, {0, 2 * rows}, {2 * cols, 2 * rows}}) {
			LanternPost.emitOne(sink, f.x(fx0 + c[0], fz0 + c[1]), f.level(), f.z(fx0 + c[0], fz0 + c[1]), LanternPost.Style.JADE, Direction.SOUTH);
		}
		int bed = 0;
		int keeper = 0;
		for (int c = 0; c < cols; c++) {
			for (int row = 0; row < rows; row++) {
				int fx = fx0 + 1 + 2 * c;
				int fz = fz0 + 1 + 2 * row;
				sink.anchor("kebun_petak_" + (++bed), f.x(fx, fz), f.level(), f.z(fx, fz));
				if (bed % 2 == 1) {
					// Gardener k stands on the curb after bed 2k-1 (between it and bed 2k).
					sink.anchor("kebun_rawat_" + (++keeper), f.x(fx, fz + 1), f.level(), f.z(fx, fz + 1));
				}
			}
		}
	}

	private void alkimia(Ctx ctx, Sink sink, Frame f, int r, AwanLayout layout) {
		int hz = -(r - 6);
		new Hall(f.x(0, hz), f.z(0, hz)).front(f.front()).site(f.lv()).bays(2, 4, 2).depth(5).pillar(4).platform(1).rank(Rank.B)
				.roof(RoofBuilder.Type.XIESHAN, 1).storeys(2, 4).overhang(1).porch(0).interior(Interiors.Preset.ALCHEMY, layout.row.alchemy())
				.doors(Hall.Doors.OPEN).emit(ctx, sink);
		// The court: dressed stone with the extra brewing stands on stone tables.
		int[] court = f.box(-3, 1, 3, 4);
		new Paving(court[0], court[1], court[2], court[3], f.lv(), Paving.Pattern.PLAZA).earth(false).emit(ctx, sink);
		sink.phase(Phase.INTERIOR);
		for (int i = 1; i < layout.row.brewing(); i++) {
			int fx = i % 2 == 1 ? -3 : 3;
			int x = f.x(fx, 2);
			int z = f.z(fx, 2);
			sink.set(x, f.level(), z, Palette.st(Blocks.POLISHED_ANDESITE));
			sink.set(x, f.level() + 1, z, Palette.st(Blocks.BREWING_STAND));
		}
		sink.anchor("alkimia", f.x(0, 1), f.level(), f.z(0, 1));
	}

	/** The arena half size (stage 2a+1 square). */
	static int arenaHalf(AwanLayout layout) {
		return layout.kelas == 0 ? 3 : layout.kelas == 1 ? 4 : 5;
	}

	private void arena(Ctx ctx, Sink sink, Frame f, int r, AwanLayout layout) {
		int a = arenaHalf(layout);
		// The stage stands centred, its stair toward +z of the template (Grounds.arena): keep it square on the centre.
		Grounds.arena(f.cx() - a, f.cz() - a, f.cx() + a, f.cz() + a, f.lv(), 2).emit(ctx, sink);
		LanternPost posts = new LanternPost(LanternPost.Style.RED_POST, f.lv());
		posts.at(f.x(-(a + 2), a + 2), f.z(-(a + 2), a + 2)).at(f.x(a + 2, a + 2), f.z(a + 2, a + 2));
		posts.emit(ctx, sink);
		Stamp.drum(f.x(-(a + 2), -(a + 2)), f.z(-(a + 2), -(a + 2)), f.lv()).emit(ctx, sink);
	}

	/** DESIGN 36 T's stands: one or two stepped rows on the three sides away from the stage stair. */
	private void tribun(Sink sink, Frame f, AwanLayout layout) {
		int a = arenaHalf(layout);
		int y = f.level();
		int cx = f.cx();
		int cz = f.cz();
		int seat = 0;
		int flag = 0;
		sink.phase(Phase.PLINTH);
		// Rows around the square x/z ±(a+2) .. ±(a+3) (template-local), skipping the stair side (+z).
		int rows = layout.kelas == 0 ? 1 : 2;
		for (int row = 0; row < rows; row++) {
			int d = a + 2 + row;
			int height = row + 1;
			BlockState step = Palette.st(row == 0 ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS);
			for (int t = -d; t <= d; t++) {
				int[][] cells = {{cx + t, cz - d}, {cx - d, cz + t}, {cx + d, cz + t}};
				for (int[] c : cells) {
					if (c[1] > cz + a) {
						continue;
					}
					for (int h = 0; h < height; h++) {
						sink.set(c[0], y + h, c[1], h == height - 1 ? step : Palette.st(Blocks.STONE_BRICKS));
					}
					if (seat < 48 && Math.floorMod(t, 2) == 0) {
						sink.anchor("kursi_" + seat++, c[0], y + height, c[1]);
					}
				}
			}
		}
		sink.phase(Phase.ORNAMENT);
		int d = a + 2 + rows;
		for (int[] c : new int[][]{{cx - d, cz - d}, {cx + d, cz - d}, {cx - d, cz + a}, {cx + d, cz + a}}) {
			for (int h = 0; h < 4; h++) {
				sink.set(c[0], y + h, c[1], Palette.st(Palette.FENCE));
			}
			sink.set(c[0], y + 4, c[1], Palette.lantern(Blocks.LANTERN, false));
			sink.anchor("panji_arena_" + flag++, c[0], y + 4, c[1]);
		}
		sink.anchor("turnamen_pusat", cx, y + 2, cz);
	}

	private void leluhur(Ctx ctx, Sink sink, Frame f, int r) {
		int hz = -(r - 5);
		new Hall(f.x(0, hz), f.z(0, hz)).front(f.front()).site(f.lv()).bays(1, 4, 1).depth(5).pillar(4).platform(1).rank(Rank.C)
				.roof(RoofBuilder.Type.XIESHAN, 1).overhang(1).porch(0).interior(Interiors.Preset.ANCESTRAL).doors(Hall.Doors.OPEN).emit(ctx, sink);
		sink.anchor("leluhur", f.x(0, hz + 4), f.level(), f.z(0, hz + 4));
		new Tree(Tree.Kind.PINE, f.x(-(r - 4), hz + 1), f.z(-(r - 4), hz + 1), f.lv()).hero(true).force(true).emit(ctx, sink);
	}

	/** Graves per sky template (as DESIGN 36 A's cemeteries). */
	static int graves(int n) {
		return switch (n) {
			case 1, 2, 3, 4 -> 3;
			case 5 -> 5;
			case 6 -> 9;
			case 7 -> 16;
			case 8 -> 12;
			default -> 32;
		};
	}

	/** DESIGN 36 A's graves: rows of tombstones facing the walk, a mound behind each, stone lanterns, a memorial stele. */
	private void makam(Sink sink, Frame f, int r, AwanLayout layout) {
		int n = graves(layout.row.n());
		int perRow = Math.min(8, Math.max(3, r - 4));
		int y = f.level();
		int grave = 0;
		sink.phase(Phase.FRAME);
		for (int row = 0; grave < n; row++) {
			int fz = 1 + row * 3;
			int half = (perRow + 1) / 2;
			for (int i = 0; i < perRow && grave < n; i++) {
				// Two apart on both sides of the walk, one grave per cell.
				int fx = i < half ? -2 * (half - i) : 2 * (i - half + 1);
				int x = f.x(fx, fz);
				int z = f.z(fx, fz);
				// The tombstone faces the front, the mound lies behind it.
				sink.set(x, y, z, Palette.st(Blocks.POLISHED_ANDESITE));
				sink.set(x, y + 1, z, Palette.st(Blocks.CHISELED_STONE_BRICKS));
				int mx = f.x(fx, fz - 1);
				int mz = f.z(fx, fz - 1);
				sink.set(mx, y, mz, Palette.st(Blocks.MOSS_BLOCK));
				sink.anchor("makam_" + grave++, f.x(fx, fz + 1), y, f.z(fx, fz + 1));
			}
		}
		sink.phase(Phase.ORNAMENT);
		LanternPost.emitOne(sink, f.x(-2, 1), y, f.z(-2, 1), LanternPost.Style.STONE, f.front());
		LanternPost.emitOne(sink, f.x(2, 1), y, f.z(2, 1), LanternPost.Style.STONE, f.front());
		// The memorial stele on the walk's far end.
		sink.phase(Phase.FRAME);
		int tx = f.x(0, -1);
		int tz = f.z(0, -1);
		sink.set(tx, y, tz, Palette.st(Palette.PILLAR_BASE));
		sink.set(tx, y + 1, tz, Palette.st(Blocks.CHISELED_STONE_BRICKS));
		sink.set(tx, y + 2, tz, Palette.st(Blocks.CHISELED_STONE_BRICKS));
		sink.set(tx, y + 3, tz, Palette.slab(Blocks.STONE_BRICK_SLAB, false));
		sink.anchor("makam_tugu", f.x(0, 0), y, f.z(0, 0));
		sink.anchor("makam_pusat", f.x(0, 1), y, f.z(0, 1));
	}

	private void asrama(Ctx ctx, Sink sink, Frame f, int r) {
		int count = r >= 11 ? 4 : r >= 10 ? 3 : 2;
		List<int[]> spots = new ArrayList<>();
		spots.add(new int[]{-(r - 5), -2});
		spots.add(new int[]{r - 5, -2});
		spots.add(new int[]{0, -(r - 5)});
		spots.add(new int[]{-(r - 5), -(r - 4)});
		for (int i = 0; i < count; i++) {
			int[] spot = spots.get(i);
			// The halls face the court: a west hall faces +fx (the frame's right), an east hall -fx, the back one the front.
			Direction facing = spot[0] < 0 ? f.front().getClockWise() : spot[0] > 0 ? f.front().getCounterClockWise() : f.front();
			new Hall(f.x(spot[0], spot[1]), f.z(spot[0], spot[1])).front(facing).site(f.lv()).bays(1, 2, 1).depth(5).pillar(4).platform(1)
					.rank(Rank.A).roof(RoofBuilder.Type.XIESHAN, 1).overhang(1).porch(0).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN)
					.emit(ctx, sink);
			sink.anchor("asrama_" + i, f.x(spot[0] / 2, spot[1]), f.level(), f.z(spot[0] / 2, spot[1]));
		}
		// The dry well in the court: a stone ring, a cauldron-free basin and a lantern on a post.
		sink.phase(Phase.FRAME);
		int wx = f.x(0, 1);
		int wz = f.z(0, 1);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					sink.set(wx + dx, f.level(), wz + dz, Palette.st(Blocks.MOSSY_STONE_BRICKS));
				}
			}
		}
		sink.set(wx, f.level() - 1, wz, Palette.st(Blocks.MOSS_BLOCK));
		sink.phase(Phase.ORNAMENT);
		sink.set(wx + 1, f.level() + 1, wz + 1, Palette.lantern(Blocks.LANTERN, false));
	}

	private void tambang(Ctx ctx, Sink sink, Frame f, int r) {
		// Scattered rock spurs and raw-copper outcrops (the mine itself is sx_kebun_tambang).
		for (int i = 0; i < 5; i++) {
			double t = (i + 0.5D) / 5.0D * Math.PI * 2.0D;
			int fx = (int) Math.round(Math.sin(t) * (r - 3));
			int fz = (int) Math.round(Math.cos(t) * (r - 3)) - 1;
			if (fz > r - 4 && Math.abs(fx) <= 2) {
				continue;
			}
			dev.kultivator.sect.build.blueprint.Rock.scholar(f.x(fx, fz), f.z(fx, fz), f.lv(), 2 + i % 3).emit(ctx, sink);
		}
		sink.phase(Phase.GARDEN);
		sink.set(f.x(3, 2), f.level(), f.z(3, 2), Palette.st(Blocks.RAW_COPPER_BLOCK));
		sink.set(f.x(-4, 1), f.level(), f.z(-4, 1), Palette.st(Blocks.MOSSY_COBBLESTONE));
		new LanternPost(LanternPost.Style.STONE, f.lv()).at(f.x(-2, 3), f.z(-2, 3)).at(f.x(2, 3), f.z(2, 3)).emit(ctx, sink);
		sink.phase(Phase.ORNAMENT);
		sink.set(f.x(0, 4), f.level(), f.z(0, 4), Palette.banner(DyeColor.BROWN, f.front()));
	}

	/** The frame of a satellite (for the components that build on it, e.g. the mine). */
	static int[] spot(AwanLayout layout, AwanLayout.Fungsi fungsi, int fx, int fz) {
		AwanLayout.Satelit s = layout.satellite(fungsi);
		if (s == null) {
			return new int[]{0, 0, 0};
		}
		Frame f = new Frame(s.pulau().cx, s.pulau().cz, s.toMain(), s.pulau().level, s.pulau());
		return new int[]{f.x(fx, fz), f.z(fx, fz), f.level()};
	}
}
