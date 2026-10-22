package dev.kultivator.sekte.kebun;

import dev.kultivator.sect.build.blueprint.Component;
import dev.kultivator.sect.build.blueprint.Component.Group;
import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.LanternPost;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Sink;
import dev.kultivator.sect.build.blueprint.TemplateExtensions;
import dev.kultivator.sect.build.blueprint.TemplateSpec;
import dev.kultivator.sect.build.blueprint.TerrainZone;
import dev.kultivator.sekte.SekteIds;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * The two sect buildings of module W (DESIGN 36.2.10, contract C36.W3), added to the nine templates through
 * {@code TemplateExtensions} so the engine plans, bills, schedules and builds them from deposited materials like every
 * other component (a site already past the tier builds them first as "Tambahan"):
 * <ul>
 * <li>{@code sx_kebun_petak} (templates 2-9, tier 3, GARDEN): the Petak Herba Roh, 4 / 4 / 6 / 6 / 8 / 8 / 12 / 12 beds of
 * dark spirit soil (mud) in a mud-brick frame with jade qi lanterns; flush with the ground in the cramped templates 2 and 3,
 * raised half a block (slab curbs and a slab path between the rows) from template 4 on. Anchors {@code kebun_petak_<n>}
 * (the cell above bed n, where the client draws the herb; beds 2k-1 and 2k share gardener k) and {@code kebun_rawat_<k>}
 * (gardener k's spot on the path). No crop block ever stands in a bed: the herbs are drawn by the client.</li>
 * <li>{@code sx_kebun_tambang} (templates 4-9, tier 4, SIDE): the Mulut Tambang Roh, a mossy rock outcrop 5 wide and 4 deep
 * with a timber-framed adit (stripped spruce posts and lintel under a little slab roof, two hanging lanterns), plank floor
 * boards, a hidden cavity behind the adit where the miners stand while "inside" (its back wall is black so the adit
 * reads deep), raw-copper veins in the rock and an ore cart (a barrel heaped with raw copper) by the door ({@code C}, {@code O}). Anchors
 * {@code tambang_mulut} (the cell in front of the adit), {@code tambang_dalam_1} (in the adit, visible) and
 * {@code tambang_dalam_2..4} (the cavity, hidden).</li>
 * </ul>
 * Plots (DESIGN 36.3.5): W's quarter is the east-back one, or beside the old herb beds. The templates were drawn to fill
 * their footprint, so the cells below were chosen with an offline dry-run of every component (no op of another component
 * except plants and earthwork in any of them, a passable approach, outside A's west-back quarter, G's and S's front band
 * and T's arena ring): templates 6-9 use the east-back quarter itself, 2-5 the nearest free court. Cells outside every
 * template zone get their own fitted zone ({@code sx_kebun_*}) so the plot follows the real ground within a few blocks.
 * <p>
 * Sumber: the Hundred Medicine Garden of the Huang Feng Valley (<i>A Record of a Mortal's Journey</i> ch. 150-151); the jade
 * mountains of the <i>Shanhaijing</i> as the source of "spirit stone" in a mountain's belly.
 */
public final class KebunKomponen {

	public static final String PLOTS = SekteIds.componentId(SekteIds.W, "petak");
	public static final String MINE = SekteIds.componentId(SekteIds.W, "tambang");
	/** Anchor ids (DESIGN 36.4, contract C36.W3). */
	public static final String ANCHOR_PLOT = "kebun_petak_";
	public static final String ANCHOR_TENDER = "kebun_rawat_";
	public static final String ANCHOR_MOUTH = "tambang_mulut";
	public static final String ANCHOR_INSIDE = "tambang_dalam_";
	/** Spots inside the mine (1 the adit, 2-4 the hidden cavity). */
	public static final int INSIDE_SPOTS = 4;
	/** The tiers of the two components. */
	public static final int PLOT_TIER = 3;
	public static final int MINE_TIER = 4;

	private static final BlockState SOIL = Palette.st(Blocks.MUD);
	private static final BlockState FRAME_FLOOR = Palette.st(Blocks.MUD_BRICKS);
	private static final BlockState FLUSH_FRAME = Palette.st(Blocks.PACKED_MUD);
	private static final BlockState CURB = Palette.slab(Blocks.MUD_BRICK_SLAB, false);

	/** The plot layout of one template: grid (or strip) origin, size, level and style. */
	private record PlotLayout(int x0, int z0, int cols, int rows, Lv level, boolean raised, int[][] lanterns) {
	}

	/** The mine layout of one template: the adit cell (front centre), the facing and level. */
	private record MineLayout(int x, int z, Direction front, Lv level) {
	}

	private static boolean registered;

	/** From {@code Kebun.init} (before the first {@code Templates.get}). */
	public static synchronized void register() {
		if (registered) {
			return;
		}
		registered = true;
		TemplateExtensions.register("sekte:kebun", 0, KebunKomponen::extend);
	}

	private static void extend(TemplateSpec.Builder b) {
		int t = b.template();
		if (t < 2 || b.hasComponent(PLOTS)) {
			return;
		}
		if (t == 2) {
			b.add(Component.of(PLOTS, PLOT_TIER).kind("garden").name(PLOTS).group(Group.GARDEN).add(new PondsideStrip()));
			return;
		}
		PlotLayout plots = plots(b, t);
		if (plots != null) {
			Component.Builder c = Component.of(PLOTS, PLOT_TIER).kind("garden").name(PLOTS).group(Group.GARDEN);
			c.add(new TerrainZone(plots.x0(), plots.z0(), plots.x0() + 2 * plots.cols(), plots.z0() + 2 * plots.rows(), plots.level())
					.edge(TerrainZone.Edge.SLOPE).surface(TerrainZone.Surface.NONE));
			c.add(new PlotGrid(plots));
			b.add(c);
		}
		MineLayout mine = mine(b, t);
		if (mine != null) {
			Component.Builder c = Component.of(MINE, MINE_TIER).kind("mine").name(MINE).group(Group.SIDE);
			BoundingBox box = Part.box(mine.x(), mine.z(), mine.front(), -2, 0, -3, 2, 0, 0);
			c.add(new TerrainZone(box.minX(), box.minZ(), box.maxX(), box.maxZ(), mine.level()).edge(TerrainZone.Edge.SLOPE)
					.surface(TerrainZone.Surface.NONE));
			c.add(new MineMouth(mine));
			b.add(c);
		}
	}

	/** Beds per template (DESIGN 36.2.10: 4 / 6 / 8 / 12). */
	public static int plotsOf(int template) {
		return switch (template) {
			case 2, 3 -> 4;
			case 4, 5 -> 6;
			case 6, 7 -> 8;
			case 8, 9 -> 12;
			default -> 0;
		};
	}

	private static @Nullable PlotLayout plots(TemplateSpec.Builder b, int t) {
		return switch (t) {
			// The front court between the two stair feet of the rear terrace.
			case 3 -> new PlotLayout(13, -16, 2, 2, Lv.PLAZA, false, new int[][]{{13, -16}, {17, -16}});
			// The hero pine's lawn, in front of the beast garden.
			case 4 -> new PlotLayout(20, -4, 3, 2, Lv.PLAZA, true, new int[][]{{20, -4}, {26, -4}});
			// The east court under the main terrace.
			case 5 -> new PlotLayout(14, -17, 3, 2, Lv.PLAZA, true, new int[][]{{14, -17}, {20, -17}});
			// The east lane between the drill ground and the east wall: two columns of four (the finish pass moved it here
			// from the east-back quarter, where the tier-9 cloud pavilion's mound would have buried the beds).
			case 6 -> new PlotLayout(39, 15, 2, 4, fitted(b, "sx_kebun_petak", 39, 15, 43, 23, 0), true,
					new int[][]{{39, 15}, {43, 15}, {39, 23}, {43, 23}});
			// East-back quarter, the lawn west of the lake.
			case 7 -> new PlotLayout(19, -38, 4, 2, fitted(b, "sx_kebun_petak", 19, -38, 27, -34, 0), true,
					new int[][]{{19, -38}, {27, -38}});
			// The rear court's east lane along the wall: two columns of six.
			case 8 -> new PlotLayout(57, -76, 2, 6, Lv.zone("rear"), true, new int[][]{{57, -76}, {61, -76}, {57, -64}, {61, -64}});
			// Behind the court's highest terrace, in the east-back corner.
			case 9 -> new PlotLayout(63, -97, 6, 2, fitted(b, "sx_kebun_petak", 63, -97, 75, -93, 0), true,
					new int[][]{{63, -97}, {75, -97}, {63, -93}, {75, -93}});
			default -> null;
		};
	}

	private static @Nullable MineLayout mine(TemplateSpec.Builder b, int t) {
		return switch (t) {
			case 4 -> new MineLayout(-18, 13, Direction.SOUTH, Lv.PLAZA);
			case 5 -> new MineLayout(-16, -14, Direction.SOUTH, Lv.PLAZA);
			case 6 -> new MineLayout(41, -46, Direction.SOUTH, fitted(b, "sx_kebun_tambang", 39, -49, 43, -46, 1));
			case 7 -> new MineLayout(31, -35, Direction.SOUTH, fitted(b, "sx_kebun_tambang", 29, -38, 33, -35, 1));
			case 8 -> new MineLayout(60, -59, Direction.SOUTH, Lv.zone("rear"));
			case 9 -> new MineLayout(78, -97, Direction.SOUTH, fitted(b, "sx_kebun_tambang", 76, -100, 80, -97, 1));
			default -> null;
		};
	}

	/**
	 * A zone of W's own, fitted to the real ground within {@code design - 2 .. design + 4} (the plot outside every template
	 * zone follows the terrain instead of being cut or filled to the plaza level). Reused when already added.
	 */
	private static Lv fitted(TemplateSpec.Builder b, String id, int x0, int z0, int x1, int z1, int design) {
		if (b.zoneSoFar(id) == null) {
			b.zone(id, x0, z0, x1, z1, design, design - 2, design + 4);
		}
		return Lv.zone(id);
	}

	// ================================================================== parts

	/**
	 * The bed grid: {@code cols × rows} beds, each a mud cell inside a one-block frame ({@code 2 cols + 1} wide, {@code
	 * 2 rows + 1} deep). Raised: a mud-brick floor under the frame and bottom slabs on it (the beds sit half a block lower
	 * than the curbs, the middle curb row or column is the gardeners' path); flush: packed mud in the frame cells only.
	 * Jade qi lanterns stand on the listed frame corners.
	 */
	private record PlotGrid(PlotLayout layout) implements Part {

		@Override
		public BoundingBox bounds(Ctx ctx) {
			int y = ctx.level(layout.level());
			return new BoundingBox(layout.x0(), y - 1, layout.z0(), layout.x0() + 2 * layout.cols(), y + 2, layout.z0() + 2 * layout.rows());
		}

		@Override
		public String kind() {
			return "spirit_beds";
		}

		@Override
		public void emit(Ctx ctx, Sink sink) {
			int y = ctx.level(layout.level());
			int x1 = layout.x0() + 2 * layout.cols();
			int z1 = layout.z0() + 2 * layout.rows();
			Phase previous = sink.phase();
			sink.phase(Phase.PLINTH);
			for (int x = layout.x0(); x <= x1; x++) {
				for (int z = layout.z0(); z <= z1; z++) {
					boolean bed = (x - layout.x0()) % 2 == 1 && (z - layout.z0()) % 2 == 1;
					if (bed) {
						sink.set(x, y - 1, z, SOIL);
						sink.air(x, y, z);
					} else {
						sink.set(x, y - 1, z, layout.raised() ? FRAME_FLOOR : FLUSH_FRAME);
					}
				}
			}
			if (layout.raised()) {
				sink.phase(Phase.WALL);
				for (int x = layout.x0(); x <= x1; x++) {
					for (int z = layout.z0(); z <= z1; z++) {
						boolean bed = (x - layout.x0()) % 2 == 1 && (z - layout.z0()) % 2 == 1;
						if (!bed && !lantern(x, z)) {
							sink.set(x, y, z, CURB);
						}
					}
				}
			}
			sink.phase(Phase.ORNAMENT);
			for (int[] at : layout.lanterns()) {
				LanternPost.emitOne(sink, at[0], y, at[1], LanternPost.Style.JADE, Direction.SOUTH);
			}
			sink.phase(previous);
			// Beds numbered so that 2k-1 and 2k share gardener k (a column of two rows, or a row of two columns).
			int n = 0;
			int k = 0;
			if (layout.cols() == 2 && layout.rows() > 2) {
				for (int r = 0; r < layout.rows(); r++) {
					for (int c = 0; c < 2; c++) {
						sink.anchor(ANCHOR_PLOT + (++n), bedX(c), y, bedZ(r));
					}
					sink.anchor(ANCHOR_TENDER + (++k), layout.x0() + 2, y, bedZ(r));
				}
			} else {
				for (int c = 0; c < layout.cols(); c++) {
					for (int r = 0; r < layout.rows(); r++) {
						sink.anchor(ANCHOR_PLOT + (++n), bedX(c), y, bedZ(r));
					}
					// The path between the two rows (or the front frame row of a single row).
					sink.anchor(ANCHOR_TENDER + (++k), bedX(c), y, layout.rows() >= 2 ? layout.z0() + 2 : layout.z0() + 2 * layout.rows());
				}
			}
		}

		private boolean lantern(int x, int z) {
			for (int[] at : layout.lanterns()) {
				if (at[0] == x && at[1] == z) {
					return true;
				}
			}
			return false;
		}

		private int bedX(int col) {
			return layout.x0() + 1 + 2 * col;
		}

		private int bedZ(int row) {
			return layout.z0() + 1 + 2 * row;
		}
	}

	/**
	 * Template 2 has no free rectangle at all: its four beds lie flush in one row on the lotus pond's south shore
	 * ({@code x -14..-11, z -7}), edged with packed mud on the court side, with one jade lantern at the east end; the T8
	 * lantern post of the court ({@code -13, -6}) keeps its cell.
	 */
	private static final class PondsideStrip implements Part {
		private static final int Z = -7;
		private static final int[] BEDS = {-14, -13, -12, -11};
		private static final int[][] EDGE = {{-14, -6}, {-12, -6}, {-11, -6}, {-10, -6}, {-10, -7}};

		@Override
		public BoundingBox bounds(Ctx ctx) {
			return new BoundingBox(-14, -1, -7, -10, 2, -6);
		}

		@Override
		public String kind() {
			return "spirit_beds";
		}

		@Override
		public void emit(Ctx ctx, Sink sink) {
			int y = ctx.level(Lv.PLAZA);
			Phase previous = sink.phase();
			sink.phase(Phase.PLINTH);
			for (int x : BEDS) {
				sink.set(x, y - 1, Z, SOIL);
			}
			for (int[] at : EDGE) {
				sink.set(at[0], y - 1, at[1], FLUSH_FRAME);
			}
			sink.phase(Phase.ORNAMENT);
			LanternPost.emitOne(sink, -10, y, Z, LanternPost.Style.JADE, Direction.SOUTH);
			sink.phase(previous);
			for (int i = 0; i < BEDS.length; i++) {
				sink.anchor(ANCHOR_PLOT + (i + 1), BEDS[i], y, Z);
			}
			sink.anchor(ANCHOR_TENDER + 1, -14, y, -6);
			sink.anchor(ANCHOR_TENDER + 2, -11, y, -6);
		}
	}

	/**
	 * The mine mouth in its own frame (front = +z, the adit at frame (0, 0, 0), rows -3 back .. 0 front, columns -2..2),
	 * drawn layer by layer from the char grid below; {@code R} is the rock mix (stone, andesite, tuff, cobble, mossy
	 * cobble, a calcite streak, chosen per cell from the plan seed), {@code V} a raw-copper vein.
	 */
	private record MineMouth(MineLayout layout) implements Part {

		/** Layers from y-1 (the floor) up; rows from the back (z -3) to the front (z 0); columns x -2..2. */
		private static final String[][] LAYERS = {
				{"SSSSS", "SpppS", "SSpSS", "SSpSS"},
				{"RKKKR", "R___R", "RRbRR", "MIbIC"},
				{"RKKKR", "R___R", "VR_RR", "RI_IO"},
				{"RRRRR", "RRRRR", "RRRRV", "L===L"},
				{"rRRRr", "RRgRR", "rRRRr", "sssss"},
				{" rRr ", "rRRRr", " rgr ", "     "},
				{"  g  ", " rRr ", "     ", "     "},
				{"     ", "  f  ", "     ", "     "}};

		@Override
		public BoundingBox bounds(Ctx ctx) {
			int y = ctx.level(layout.level());
			return Part.box(layout.x(), layout.z(), layout.front(), -2, y - 1, -3, 2, y + LAYERS.length - 2, 0);
		}

		@Override
		public String kind() {
			return "mine_mouth";
		}

		@Override
		public void emit(Ctx ctx, Sink sink) {
			int y = ctx.level(layout.level());
			Random random = ctx.random(MINE);
			sink.pushFacing(layout.x(), y, layout.z(), layout.front());
			Phase previous = sink.phase();
			for (int l = 0; l < LAYERS.length; l++) {
				String[] rows = LAYERS[l];
				int fy = l - 1;
				for (int r = 0; r < rows.length; r++) {
					String row = rows[r];
					int fz = r - 3;
					for (int c = 0; c < row.length(); c++) {
						int fx = c - 2;
						char key = row.charAt(c);
						if (key == ' ') {
							continue;
						}
						if (key == '_') {
							sink.phase(Phase.FRAME);
							sink.air(fx, fy, fz);
							continue;
						}
						BlockState state = state(key, random);
						if (state == null) {
							continue;
						}
						sink.phase(phase(key));
						sink.set(fx, fy, fz, state);
					}
				}
			}
			sink.phase(previous);
			sink.anchor(ANCHOR_MOUTH, 0, 0, 1);
			sink.anchor(ANCHOR_INSIDE + 1, 0, 0, -1);
			sink.anchor(ANCHOR_INSIDE + 2, 0, 0, -2);
			sink.anchor(ANCHOR_INSIDE + 3, -1, 0, -2);
			sink.anchor(ANCHOR_INSIDE + 4, 1, 0, -2);
			sink.pop();
		}

		private static Phase phase(char key) {
			return switch (key) {
				case 'S', 'p' -> Phase.PLINTH;
				case 'I', '=', 'b' -> Phase.FRAME;
				case 's' -> Phase.ROOF;
				case 'L', 'O' -> Phase.ORNAMENT;
				case 'g', 'f' -> Phase.GARDEN;
				default -> Phase.WALL;
			};
		}

		private static @Nullable BlockState state(char key, Random random) {
			return switch (key) {
				case 'S' -> Palette.st(Blocks.COBBLED_DEEPSLATE);
				case 'p' -> Palette.st(Blocks.SPRUCE_PLANKS);
				case 'I' -> Palette.axis(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y);
				case '=' -> Palette.axis(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X);
				case 'K' -> Palette.st(Palette.concrete(DyeColor.BLACK));
				case 'b' -> Palette.trapdoorFlat(Blocks.SPRUCE_TRAPDOOR, Direction.NORTH, false);
				case 'M' -> Palette.st(Blocks.MOSSY_COBBLESTONE);
				case 'C' -> Palette.barrel(Direction.UP);
				case 'O' -> Palette.st(Blocks.RAW_COPPER_BLOCK);
				case 'L' -> Palette.lantern(Blocks.LANTERN, true);
				case 's' -> Palette.slab(Blocks.SPRUCE_SLAB, false);
				case 'g' -> Palette.st(Blocks.MOSS_BLOCK);
				case 'f' -> Palette.st(Blocks.FERN);
				case 'V' -> Palette.st(Blocks.RAW_COPPER_BLOCK);
				case 'r' -> Palette.slab(random.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE_SLAB : Blocks.COBBLESTONE_SLAB, false);
				case 'R' -> Palette.st(rock(random));
				default -> null;
			};
		}

		private static Block rock(Random random) {
			int roll = random.nextInt(20);
			if (roll < 6) {
				return Blocks.STONE;
			}
			if (roll < 10) {
				return Blocks.ANDESITE;
			}
			if (roll < 13) {
				return Blocks.TUFF;
			}
			if (roll < 16) {
				return Blocks.COBBLESTONE;
			}
			if (roll < 19) {
				return Blocks.MOSSY_COBBLESTONE;
			}
			return Blocks.CALCITE;
		}
	}

	private KebunKomponen() {
	}
}
