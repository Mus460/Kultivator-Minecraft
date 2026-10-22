package dev.kultivator.sekte.paviliun;

import dev.kultivator.sect.build.blueprint.Component;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The two sect buildings of module A (DESIGN 36.2.5, contract C36.A4), added to templates 2-9 through
 * {@link TemplateExtensions} so the engine plans, bills, schedules and builds them from deposited materials like any other
 * component (a site past their tier builds them first as "Tambahan"):
 * <ul>
 * <li>{@code sx_paviliun_altar} (Altar Leluhur, tier 2, templates 2-7): a 5 x 5 dais with a lacquered tablet screen under
 * a tile canopy, an offering table, a bronze-cauldron censer, kneeling cushions and stone lanterns; a compact 3 x 2 shrine
 * (a blackstone tablet shelf between two lanterns, the censer between two cushions) in the crowded templates 2-4. Anchors {@code altar} (where one
 * kneels), {@code altar_meja} (the table top, where the client stands the spirit tablets) and {@code altar_dupa} (the censer).</li>
 * <li>{@code sx_paviliun_makam} (Pemakaman, tier 3, templates 3-9): rows of graves in the Chinese manner (a grassy mound
 * behind an upright tombstone that faces the path), stone lanterns, cypress columns and, where there is room, a memorial
 * stele. Anchors {@code makam_<n>} (each tombstone, the client's floating name) and {@code makam_pusat} (the path in front
 * of the stele: funerals and Qingming gather there).</li>
 * </ul>
 * DESIGN 36.3.5 gave A the west-back third of every template, but the dry runs ({@code scratchpad/aA} harness) show that
 * plot fully built over in templates 2-6 (the tier-8 pagoda, the ancestral hall, the lotus pond, the main hall). The
 * buildings therefore stand in the free pockets nearest to it, checked cell by cell against every component of all
 * tiers <i>and against the other modules' extensions</i> (W's herb beds and spirit mine, G's trial stones, S's banners,
 * T's arena stands; harness {@code Clash}: no shared column in any template) and kept out of the plots of G and S
 * (|x| &lt;= 8 in front) and the formation ring: outside the front wall west of the approach (T2 shrine, T3, T5 graves:
 * the old custom of burying the dead outside the walls), along the front lane (T4), west of the forecourt (T6), on a
 * terrace by the lake (T7), between the west halls (T8) and on a terrace beside the Dalam peak (T9); the altar stands
 * in the corner pocket (T4), between the lotus pond and the main hall (T5), facing the graves (T6) or at the end of the
 * terrace (T7). Untouched natural ground gets its own earthwork ({@link TerrainZone}); the two hillside pockets (T7, T9)
 * get their own fitted zone so the terrace follows the real slope. Graves per template: T3 3, T4 3, T5 5, T6 9, T7 16,
 * T8 12, T9 32. Every part stays inside the footprint column; {@code /kultivasi uji sekte audit} checks it in game.
 * <p>
 * Sumber: papan arwah leluhur di aula leluhur dan ziarah makam Qingming (adat Tiongkok); kuburan di luar tembok kota dan
 * gundukan makam di belakang batu nisan (adat pemakaman Tiongkok).
 */
public final class PaviliunKomponen {

	public static final String ALTAR = SekteIds.componentId(SekteIds.A, "altar");
	public static final String MAKAM = SekteIds.componentId(SekteIds.A, "makam");
	/** Where one kneels before the altar (also the rite spot). */
	public static final String ANCHOR_ALTAR = "altar";
	/** The offering table top: the client stands the spirit tablets here. */
	public static final String ANCHOR_TABLE = "altar_meja";
	/** The censer cell (the client's incense smoke rises from it). */
	public static final String ANCHOR_CENSER = "altar_dupa";
	/** The gathering spot of the cemetery (funerals, Qingming). */
	public static final String ANCHOR_CENTER = "makam_pusat";
	/** Prefix of the tombstone anchors {@code makam_0..n}. */
	public static final String ANCHOR_GRAVE = "makam_";
	/** The cell in front of the memorial stele (its inscribed face). */
	public static final String ANCHOR_STELE = "makam_tugu";
	/** The zone A adds on the two hillside pockets (T7, T9). */
	public static final String ZONE = "sx_paviliun_tanah";

	/** One row of graves: {@code count} tombstones from ({@code x}, {@code z}) every 2 cells across, facing {@code front}. */
	private record Row(int x, int z, int count, Direction front) {
	}

	/** A layout of one template (template-local; null parts are absent). */
	private record Plan(int template, Lv level, @Nullable int[] zone, @Nullable Altar altar, List<int[]> altarEarth, List<Row> rows,
			List<int[]> lanterns, List<int[]> cypresses, @Nullable int[] stele, List<int[]> paths, List<int[]> earth, int[] center,
			boolean retaining) {
	}

	/** The altar: centre (or compact back-middle) cell, the direction it opens to, compact or full. */
	private record Altar(int x, int z, Direction front, boolean compact) {
	}

	private static boolean registered;

	/** From {@code Paviliun.init}: adds the components to templates 2-9 (before the first {@code Templates.get}). */
	public static synchronized void register() {
		if (registered) {
			return;
		}
		registered = true;
		for (int template = 2; template <= 9; template++) {
			int t = template;
			TemplateExtensions.register("sekte_paviliun", t, builder -> add(builder, t));
		}
	}

	/** The tombstones of template {@code template} (0 without a cemetery). */
	public static int graves(int template) {
		Plan plan = plan(template);
		if (plan == null) {
			return 0;
		}
		int n = 0;
		for (Row row : plan.rows()) {
			n += row.count();
		}
		return n;
	}

	/** True when template {@code template} has the altar component. */
	public static boolean hasAltar(int template) {
		Plan plan = plan(template);
		return plan != null && plan.altar() != null;
	}

	private static void add(TemplateSpec.Builder builder, int template) {
		Plan plan = plan(template);
		if (plan == null || builder.hasComponent(ALTAR) || builder.hasComponent(MAKAM)) {
			return;
		}
		if (plan.zone() != null) {
			int[] z = plan.zone();
			builder.zone(ZONE, z[0], z[1], z[2], z[3], z[4], z[5], z[6]);
		}
		if (plan.altar() != null) {
			Component.Builder altar = Component.of(ALTAR, 2).kind("altar").name("sx_paviliun_altar").group(Component.Group.SIDE);
			for (int[] r : plan.altarEarth()) {
				altar.add(earth(r, plan));
			}
			altar.add(new AltarPart(plan.altar(), plan.level()));
			builder.add(altar);
		}
		if (!plan.rows().isEmpty()) {
			Component.Builder makam = Component.of(MAKAM, 3).kind("cemetery").name("sx_paviliun_makam").group(Component.Group.GARDEN);
			for (int[] r : plan.earth()) {
				makam.add(earth(r, plan));
			}
			makam.add(new CemeteryPart(plan));
			builder.add(makam);
		}
	}

	private static TerrainZone earth(int[] r, Plan plan) {
		return new TerrainZone(r[0], r[1], r[2], r[3], plan.level()).edge(plan.retaining() ? TerrainZone.Edge.RETAINING : TerrainZone.Edge.NONE)
				.surface(TerrainZone.Surface.GRASS).clearHeight(8);
	}

	// ================================================================== the layouts (dry-run checked, see the class comment)

	private static int[] r(int x0, int z0, int x1, int z1) {
		return new int[]{Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)};
	}

	private static int[] p(int x, int z) {
		return new int[]{x, z};
	}

	private static @Nullable Plan plan(int template) {
		Direction s = Direction.SOUTH;
		Direction n = Direction.NORTH;
		Direction w = Direction.WEST;
		Direction e = Direction.EAST;
		return switch (template) {
			// T2: a compact shrine outside the front wall, west of the gate approach (the pondside belongs to W's herb beds).
			case 2 -> new Plan(2, Lv.PLAZA, null, new Altar(-12, 13, s, true), List.of(r(-13, 13, -11, 14)), List.of(), List.of(), List.of(), null,
					List.of(), List.of(), p(-12, 14), false);
			// T3: outside the front wall flanking the approach: three graves west, the shrine east.
			case 3 -> new Plan(3, Lv.PLAZA, null, new Altar(12, 16, s, true), List.of(r(11, 16, 13, 17)),
					List.of(new Row(-14, 17, 3, s)), List.of(p(-15, 16), p(-15, 18)), List.of(), null,
					List.of(r(-14, 18, -10, 18)), List.of(r(-15, 16, -10, 18)), p(-12, 18), false);
			// T4: the shrine in the west corner pocket; three graves along the front lane, their mounds against the wall
			// (W's spirit mine took the court pocket beside the garden).
			case 4 -> new Plan(4, Lv.PLAZA, null, new Altar(-22, 14, s, true), List.of(r(-23, 14, -21, 15)),
					List.of(new Row(-19, 17, 2, n), new Row(-13, 17, 1, n)), List.of(p(-15, 17)), List.of(), null,
					List.of(), List.of(r(-19, 17, -13, 17), r(-19, 18, -17, 18), r(-13, 18, -13, 18)), p(-16, 17), false);
			// T5: the altar between the lotus pond and the main hall; the cemetery outside the front wall, west of the
			// approach: five graves in a row facing the lane, a stone lantern at each end of it.
			case 5 -> new Plan(5, Lv.PLAZA, null, new Altar(-14, -10, n, false), List.of(r(-16, -12, -12, -8)),
					List.of(new Row(-21, 22, 5, s)), List.of(p(-21, 23), p(-13, 23)), List.of(), null, List.of(r(-20, 23, -14, 23)),
					List.of(r(-21, 21, -13, 23)), p(-17, 23), false);
			// T6: the west of the forecourt: the altar facing two rows of graves across a lantern path.
			case 6 -> new Plan(6, Lv.PLAZA, null, new Altar(-41, 26, s, false), List.of(r(-43, 24, -39, 28)),
					List.of(new Row(-41, 31, 2, s), new Row(-35, 31, 2, s), new Row(-41, 33, 5, n)), List.of(p(-43, 32), p(-31, 32)), List.of(),
					new int[]{-37, 31, s.get2DDataValue()}, List.of(r(-42, 32, -32, 32), r(-43, 29, -40, 29)),
					List.of(r(-42, 30, -32, 34), r(-43, 32, -43, 32), r(-31, 32, -31, 32), r(-43, 29, -40, 29)), p(-37, 32), false);
			// T7: a terrace on the lake shore (own fitted zone, retaining walls): three rows, the altar at the east end.
			case 7 -> new Plan(7, Lv.zone(ZONE), new int[]{-34, -41, -17, -33, 2, 1, 6}, new Altar(-19, -38, w, false), List.of(r(-21, -40, -17, -36)),
					List.of(new Row(-33, -40, 6, s), new Row(-33, -38, 6, n), new Row(-33, -35, 2, s), new Row(-25, -35, 2, s)),
					List.of(p(-34, -39), p(-22, -39), p(-34, -34), p(-22, -34)), List.of(p(-19, -34)), new int[]{-28, -35, s.get2DDataValue()},
					List.of(r(-34, -39, -22, -39), r(-34, -34, -22, -34)), List.of(r(-33, -41, -22, -41), r(-34, -40, -22, -33), r(-21, -35, -17, -33)), p(-28, -34), true);
			// T8: between the west halls (east of T's arena stands): three rows of four, the stele closing the middle path.
			case 8 -> new Plan(8, Lv.zone("west"), null, null, List.of(),
					List.of(new Row(-38, -17, 4, s), new Row(-38, -15, 4, n), new Row(-38, -12, 4, s)),
					List.of(p(-38, -11), p(-32, -11)), List.of(), new int[]{-32, -16, w.get2DDataValue()},
					List.of(r(-38, -16, -33, -16), r(-37, -11, -33, -11)), List.of(r(-38, -18, -32, -11)),
					p(-35, -16), false);
			// T9: a terrace beside the Dalam peak (own fitted zone, retaining walls): four rows of eight.
			case 9 -> new Plan(9, Lv.zone(ZONE), new int[]{-38, -83, -22, -74, 13, 6, 16}, null, List.of(),
					List.of(new Row(-37, -82, 8, s), new Row(-37, -80, 8, n), new Row(-37, -77, 8, s), new Row(-37, -75, 8, n)),
					List.of(p(-38, -81), p(-22, -81), p(-22, -76)), List.of(), new int[]{-38, -76, e.get2DDataValue()},
					List.of(r(-38, -81, -22, -81), r(-38, -76, -22, -76)), List.of(r(-38, -83, -22, -74)), p(-30, -76), true);
			default -> null;
		};
	}

	// ================================================================== altar

	/** The Altar Leluhur in its own frame: front = +z (where one kneels), the tablet screen at the back. */
	private static final class AltarPart implements Part {
		private final Altar altar;
		private final Lv level;

		AltarPart(Altar altar, Lv level) {
			this.altar = altar;
			this.level = level;
		}

		@Override
		public BoundingBox bounds(Ctx ctx) {
			int y = ctx.level(level);
			return altar.compact() ? Part.box(altar.x(), altar.z(), altar.front(), -1, y - 1, 0, 1, y + 3, 1)
					: Part.box(altar.x(), altar.z(), altar.front(), -2, y - 1, -2, 2, y + 4, 2);
		}

		@Override
		public String kind() {
			return "altar";
		}

		@Override
		public void emit(Ctx ctx, Sink sink) {
			int y = ctx.level(level);
			Phase previous = sink.phase();
			sink.pushFacing(altar.x(), y, altar.z(), altar.front());
			if (altar.compact()) {
				compact(sink);
			} else {
				full(sink);
			}
			sink.pop();
			sink.phase(previous);
		}

		/** 3 x 2: (z 0) a blackstone shelf with lanterns on its ends; (z 1) a cushion, the censer, a cushion. */
		private static void compact(Sink sink) {
			sink.phase(Phase.PLINTH);
			for (int x = -1; x <= 1; x++) {
				sink.set(x, -1, 0, Palette.st(Blocks.POLISHED_ANDESITE));
				sink.set(x, -1, 1, Palette.st(x == 0 ? Blocks.CHISELED_STONE_BRICKS : Blocks.POLISHED_ANDESITE));
			}
			sink.phase(Phase.FRAME);
			for (int x = -1; x <= 1; x++) {
				sink.set(x, 0, 0, Palette.st(Blocks.POLISHED_BLACKSTONE_BRICKS));
			}
			sink.phase(Phase.INTERIOR);
			sink.set(0, 0, 1, Palette.st(Blocks.CAULDRON));
			sink.set(-1, 0, 1, Palette.st(Palette.carpet(DyeColor.RED)));
			sink.set(1, 0, 1, Palette.st(Palette.carpet(DyeColor.RED)));
			sink.phase(Phase.ORNAMENT);
			sink.set(-1, 1, 0, Palette.lantern(Blocks.LANTERN, false));
			sink.set(1, 1, 0, Palette.lantern(Blocks.LANTERN, false));
			sink.anchor(ANCHOR_TABLE, 0, 1, 0);
			sink.anchor(ANCHOR_CENSER, 0, 0, 1);
			sink.anchor(ANCHOR_ALTAR, -1, 0, 1);
		}

		/** 5 x 5: screen and canopy (z -2..-1), table (z -1), censer (z 0), cushions (z 1), lanterns (z 2). */
		private static void full(Sink sink) {
			sink.phase(Phase.PLINTH);
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
					boolean axis = x == 0 && z >= 0;
					sink.set(x, -1, z, Palette.st(corner ? Blocks.CHISELED_STONE_BRICKS : axis ? Blocks.SMOOTH_STONE : Blocks.POLISHED_ANDESITE));
				}
			}
			// the back screen: a blackstone sill, red pillars, a lacquered lintel and the gilt plaque field
			sink.phase(Phase.FRAME);
			for (int x = -2; x <= 2; x++) {
				sink.set(x, 0, -2, Palette.st(Blocks.POLISHED_BLACKSTONE_BRICKS));
			}
			for (int x : new int[]{-2, 2}) {
				sink.set(x, 1, -2, Palette.st(Palette.PILLAR_THIN));
				sink.set(x, 2, -2, Palette.st(Palette.PILLAR_THIN));
			}
			sink.phase(Phase.WALL);
			for (int x = -1; x <= 1; x++) {
				sink.set(x, 1, -2, Palette.axis(Palette.LINTEL, Direction.Axis.X));
				sink.set(x, 2, -2, x == 0 ? Palette.st(Palette.PLAQUE_FIELD) : Palette.st(Blocks.POLISHED_CINNABAR));
			}
			// the tile canopy over the screen and table
			sink.phase(Phase.ROOF);
			for (int x = -2; x <= 2; x++) {
				sink.set(x, 3, -2, Palette.st(Blocks.DEEPSLATE_TILES));
				sink.set(x, 3, -1, Palette.stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.NORTH, false));
			}
			sink.set(-2, 4, -2, Palette.slab(Blocks.POLISHED_CINNABAR_SLAB, false));
			sink.set(2, 4, -2, Palette.slab(Blocks.POLISHED_CINNABAR_SLAB, false));
			sink.set(0, 4, -2, Palette.slab(Blocks.DEEPSLATE_TILE_SLAB, false));
			// the offering table: legs and a mangrove slab top (the client stands the tablets on it)
			sink.phase(Phase.INTERIOR);
			sink.set(-1, 0, -1, Palette.stair(Blocks.MANGROVE_STAIRS, Direction.EAST, true));
			sink.set(0, 0, -1, Palette.st(Blocks.STRIPPED_MANGROVE_WOOD));
			sink.set(1, 0, -1, Palette.stair(Blocks.MANGROVE_STAIRS, Direction.WEST, true));
			for (int x = -1; x <= 1; x++) {
				sink.set(x, 1, -1, Palette.slab(Blocks.MANGROVE_SLAB, false));
			}
			// the censer: a bronze-coloured cauldron on the axis
			sink.set(0, 0, 0, Palette.st(Blocks.CAULDRON));
			for (int x = -1; x <= 1; x++) {
				sink.set(x, 0, 1, Palette.st(Palette.carpet(x == 0 ? DyeColor.RED : DyeColor.YELLOW)));
			}
			// lanterns: short ones beside the table, stone lanterns at the front corners
			sink.phase(Phase.ORNAMENT);
			for (int x : new int[]{-2, 2}) {
				sink.set(x, 0, -1, Palette.st(Blocks.ANDESITE_WALL));
				sink.set(x, 1, -1, Palette.lantern(Blocks.LANTERN, false));
				LanternPost.emitOne(sink, x, 0, 2, LanternPost.Style.STONE, Direction.SOUTH);
			}
			sink.anchor(ANCHOR_TABLE, 0, 1, -1);
			sink.anchor(ANCHOR_CENSER, 0, 0, 0);
			sink.anchor(ANCHOR_ALTAR, 0, 0, 1);
		}
	}

	// ================================================================== cemetery

	/** Every grave, lantern, cypress, the stele, the path paving and the anchors of a plan (template-local). */
	private static final class CemeteryPart implements Part {
		private final Plan plan;

		CemeteryPart(Plan plan) {
			this.plan = plan;
		}

		@Override
		public BoundingBox bounds(Ctx ctx) {
			int y = ctx.level(plan.level());
			BoundingBox box = null;
			for (int[] r : plan.earth()) {
				BoundingBox each = new BoundingBox(r[0], y - 1, r[1], r[2], y + 5, r[3]);
				box = box == null ? each : BoundingBox.encapsulating(box, each);
			}
			return box == null ? new BoundingBox(0, y, 0, 0, y, 0) : box;
		}

		@Override
		public String kind() {
			return "cemetery";
		}

		@Override
		public void emit(Ctx ctx, Sink sink) {
			int y = ctx.level(plan.level());
			Random random = ctx.random(MAKAM + plan.template());
			Phase previous = sink.phase();
			// paths: stone with moss here and there
			sink.phase(Phase.PLINTH);
			for (int[] r : plan.paths()) {
				for (int x = r[0]; x <= r[2]; x++) {
					for (int z = r[1]; z <= r[3]; z++) {
						int roll = random.nextInt(9);
						BlockState floor = Palette.st(roll == 0 ? Blocks.MOSSY_STONE_BRICKS : roll == 1 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS);
						sink.set(x, y - 1, z, floor);
					}
				}
			}
			int index = 0;
			for (Row row : plan.rows()) {
				Direction across = row.front().getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
				for (int i = 0; i < row.count(); i++) {
					int x = row.x() + across.getStepX() * 2 * i;
					int z = row.z() + across.getStepZ() * 2 * i;
					grave(sink, random, x, y, z, row.front());
					sink.anchor(ANCHOR_GRAVE + index, x, y, z);
					index++;
				}
			}
			int[] stele = plan.stele();
			if (stele != null) {
				stele(sink, stele[0], y, stele[1], Direction.from2DDataValue(stele[2]));
			}
			sink.phase(Phase.ORNAMENT);
			for (int[] l : plan.lanterns()) {
				LanternPost.emitOne(sink, l[0], y, l[1], LanternPost.Style.STONE, Direction.SOUTH);
			}
			sink.phase(Phase.GARDEN);
			for (int[] c : plan.cypresses()) {
				cypress(sink, c[0], y, c[1]);
			}
			sink.anchor(ANCHOR_CENTER, plan.center()[0], y, plan.center()[1]);
			sink.phase(previous);
		}

		/** A tombstone at (x, z) facing {@code front}, its grassy mound one cell behind it. */
		private static void grave(Sink sink, Random random, int x, int y, int z, Direction front) {
			int bx = x - front.getStepX();
			int bz = z - front.getStepZ();
			sink.phase(Phase.PLINTH);
			sink.set(x, y - 1, z, Palette.st(Blocks.POLISHED_ANDESITE));
			sink.set(bx, y - 1, bz, Palette.st(Blocks.COARSE_DIRT));
			sink.phase(Phase.FRAME);
			sink.set(x, y, z, Palette.st(random.nextInt(4) == 0 ? Blocks.CHISELED_STONE_BRICKS : Blocks.POLISHED_DIORITE));
			sink.set(x, y + 1, z, Palette.st(Blocks.DIORITE_WALL));
			sink.phase(Phase.GARDEN);
			sink.set(bx, y, bz, Palette.st(Blocks.MOSS_BLOCK));
			if (random.nextInt(3) == 0) {
				sink.set(bx, y + 1, bz, Palette.st(Blocks.MOSS_CARPET));
			}
		}

		/** The memorial stele: a stone tortoise base, a tall tablet and a tiled cap, facing {@code front}. */
		private static void stele(Sink sink, int x, int y, int z, Direction front) {
			sink.phase(Phase.PLINTH);
			sink.set(x, y - 1, z, Palette.st(Blocks.CHISELED_STONE_BRICKS));
			sink.phase(Phase.FRAME);
			sink.set(x, y, z, Palette.st(Blocks.CHISELED_TUFF_BRICKS));
			sink.set(x, y + 1, z, Palette.st(Blocks.POLISHED_DIORITE));
			sink.set(x, y + 2, z, Palette.st(Blocks.POLISHED_DIORITE));
			sink.phase(Phase.ROOF);
			sink.set(x, y + 3, z, Palette.slab(Blocks.DEEPSLATE_TILE_SLAB, false));
			// the inscribed face looks toward {@code front} (the client draws the carved names there)
			sink.anchor(ANCHOR_STELE, x + front.getStepX(), y, z + front.getStepZ());
		}

		/** A slim cypress column (spruce log and persistent leaves, at most one cell to the side). */
		private static void cypress(Sink sink, int x, int y, int z) {
			sink.set(x, y, z, Palette.axis(Blocks.SPRUCE_LOG, Direction.Axis.Y));
			sink.set(x, y + 1, z, Palette.axis(Blocks.SPRUCE_LOG, Direction.Axis.Y));
			BlockState leaves = Palette.leaves(Blocks.SPRUCE_LEAVES);
			for (int dy = 2; dy <= 5; dy++) {
				sink.set(x, y + dy, z, leaves);
			}
			for (Direction d : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
				sink.set(x + d.getStepX(), y + 2, z + d.getStepZ(), leaves);
				sink.set(x + d.getStepX(), y + 3, z + d.getStepZ(), leaves);
			}
		}
	}

	/** Template-local anchor list of a template's graves, for the uji listing (empty without a cemetery). */
	static List<String> graveAnchors(int template) {
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < graves(template); i++) {
			ids.add(ANCHOR_GRAVE + i);
		}
		return ids;
	}

	private PaviliunKomponen() {
	}
}
