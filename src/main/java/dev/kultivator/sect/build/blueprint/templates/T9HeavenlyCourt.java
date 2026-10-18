package dev.kultivator.sect.build.blueprint.templates;

import dev.kultivator.sect.build.blueprint.BambooGrove;
import dev.kultivator.sect.build.blueprint.Bridge;
import dev.kultivator.sect.build.blueprint.Component;
import dev.kultivator.sect.build.blueprint.Component.Group;
import dev.kultivator.sect.build.blueprint.Corridor;
import dev.kultivator.sect.build.blueprint.FormationDisc;
import dev.kultivator.sect.build.blueprint.Gates;
import dev.kultivator.sect.build.blueprint.Grounds;
import dev.kultivator.sect.build.blueprint.Hall;
import dev.kultivator.sect.build.blueprint.HangingIsland;
import dev.kultivator.sect.build.blueprint.Interiors;
import dev.kultivator.sect.build.blueprint.LanternPost;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Marks;
import dev.kultivator.sect.build.blueprint.Ornament;
import dev.kultivator.sect.build.blueprint.Pagoda;
import dev.kultivator.sect.build.blueprint.Paifang;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Pavilion;
import dev.kultivator.sect.build.blueprint.Paving;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Rank;
import dev.kultivator.sect.build.blueprint.Rock;
import dev.kultivator.sect.build.blueprint.RoofBuilder;
import dev.kultivator.sect.build.blueprint.SetPieces;
import dev.kultivator.sect.build.blueprint.StairFlight;
import dev.kultivator.sect.build.blueprint.Stamp;
import dev.kultivator.sect.build.blueprint.TemplateSpec;
import dev.kultivator.sect.build.blueprint.Terrace;
import dev.kultivator.sect.build.blueprint.TerrainZone;
import dev.kultivator.sect.build.blueprint.TowerShaft;
import dev.kultivator.sect.build.blueprint.Tree;
import dev.kultivator.sect.build.blueprint.Wall;
import dev.kultivator.sect.build.blueprint.Waterfall;
import dev.kultivator.sect.build.blueprint.templates.t7t9.GateAltar;
import dev.kultivator.sect.build.blueprint.templates.t7t9.GlazedScreen;
import dev.kultivator.sect.build.blueprint.templates.t7t9.Lake;
import dev.kultivator.sect.build.blueprint.templates.t7t9.Massif;
import dev.kultivator.sect.build.blueprint.templates.t7t9.Piers;
import dev.kultivator.sect.build.blueprint.templates.t7t9.Put;
import dev.kultivator.sect.build.blueprint.templates.t7t9.RoundHall;
import dev.kultivator.sect.build.blueprint.templates.t7t9.TestPath;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * Template 9, Istana Langit / Heavenly Court (DESIGN 21.1, 21.3, 21.4 plan): 161×161, five peaks above the clouds and no
 * perimeter wall. From the south: the grand 5-bay 7-roof paifang, four gate altars on the approach, the 5-bay shanmen
 * between two glazed screen walls with short red wall stubs ending in rock, the sunken outer court (arena W, drill ground and
 * archery E, bell and drum towers, the Inner dorms SE, the bamboo grove and the candidates' test path SW), the chuihuamen
 * with its galleries, the plaza and the Bagua disc (r 9), the Aula Pendiri at the foot of the sculpted main peak and the
 * nine-flight stair up its terraces (+3 / +6 / +9, marble +11 / +13) to the 9-bay double-eave wudian; behind it the
 * Grand Ancestral Hall on the +13 lawn and the summit (+18) with the Pagoda Kaca Sembilan Harta (top about +96). Around
 * the main peak: the west Blessed lake (island pavilion, zigzag bridge, stone boat) and its stream with three arched
 * bridges, Puncak Awan Harum (+8, W) with the Menara Pil, Puncak Dalam (+14, NW) and Puncak Tetua (+14, NE) behind their
 * barriers and joined to the ancestral lawn by covered gallery bridges, Puncak Binatang (+8, E) with the beast garden; at
 * T9 the hanging-mountain archipelago on jade chains, the round triple-eave Aula Dao Langit (Langit blue) on the largest
 * island with an arched sky bridge to the pagoda's fourth floor, and waterfalls off the peaks. Rank by height: the higher
 * the terrace, the higher the rank.
 * <p>
 * Sources (DESIGN 21.1): <i>Shrouding the Heavens</i>: Ye Fan establishes the Heavenly Court. Rank-by-height layout after
 * <i>A Record of a Mortal's Journey</i>'s Heavenly Star City (Sage Mountain, 81 layers, higher rank lives higher); Inner
 * Sect and elders on upper terraces behind a barrier: <i>I Shall Seal the Heavens</i> ch. 2-3 (the Reliance Sect). Multi-peak
 * sect and Puncak Awan Harum: <i>A Will Eternal</i>, the Spirit Stream Sect's peaks (Fragrant Cloud Peak makes the pills).
 * Menara Pil: <i>Battle Through the Heavens</i>, the Pill Tower (the shape is taken from the name). Beast garden: <i>A
 * Record of a Mortal's Journey</i>, Spirit Beast Mountain (name only). Test path: <i>A Record of a Mortal's Journey</i>
 * ch. 1-5, the Seven Mysteries climb. Pagoda name after <i>Soul Land</i> (a martial soul there; the storey count is design).
 * Hanging islands: <i>Liezi</i>, "Tang Wen" (the turtle head nods to it; the chains are art direction, not canon). Hero
 * tree, duty stations, library, drill ground, Kantor Misi and the tower as in DESIGN 21.2. The Aula Dao Langit (the Hall of
 * Prayer for Good Harvests), the stone boat (the Summer Palace), the paifang and screen wall, the terraces and the
 * Huangshan pines are art direction from real architecture and landscape, not novel canon.
 */
public final class T9HeavenlyCourt {

	public static TemplateSpec build() {
		Lv p = Lv.PLAZA;
		Lv court = Lv.zone("court");
		Lv approach = Lv.zone("approach");
		Lv l3 = Lv.at(3);
		Lv l6 = Lv.at(6);
		Lv l8 = Lv.at(8);
		Lv l9 = Lv.at(9);
		Lv l11 = Lv.at(11);
		Lv l13 = Lv.at(13);
		Lv l14 = Lv.at(14);
		Lv l18 = Lv.at(18);
		TemplateSpec.Builder b = TemplateSpec.builder(9).footprint(-80, 80, -100, 60).envelope(100, 30).relief(64).earthwork(129605)
				.disc(6, 9).territory(108).towerRooms(7).qiTier(3).gateAltars(4).planVersion(1)
				.zone("plaza", -12, -8, 12, 18, 0)
				.zone("court", -78, 19, 78, 46, -3, -3, 0)
				.zone("approach", -60, 47, 60, 60, -5, -6, 0, "court", -3)
				.zone("main", -37, -19, 37, -73, 13)
				.zone("summit", -16, -74, 16, -98, 18)
				.zone("dalam", -76, -64, -44, -96, 14)
				.zone("tetua", 44, -44, 76, -74, 14)
				.zone("awan", -76, -24, -52, -47, 8)
				.zone("binatang", 52, -16, 77, -38, 8);

		// The west Blessed lake and its stream (T4), shared with every peak that must keep off the water.
		Lake lake = new Lake("t9_lake", p).lotus(6)
				.lobe(-56, -3, 19, 12)
				.lobe(-62, -17, 7, 6)
				.channel(-46, -63, -42, -12)
				.island(-52, -4, 4);

		// ============================================================ T1 Peletakan Prasasti
		b.add(Component.of("t1_axis", 1).kind("axis").name("axis").group(Group.AXIS)
				.add(new TerrainZone(-12, -8, 12, 18, p).edge(TerrainZone.Edge.RETAINING))
				.add(new Paving(-2, -7, 2, 18, p, Paving.Pattern.AXIS).earth(false))
				.add(new Paving(-6, -7, 6, 4, p, Paving.Pattern.PLAZA).earth(false))
				.add(new Paving(-2, -2, 2, 2, p, Paving.Pattern.OCTAGON_INLAY).radius(2).earth(false))
				.add(new LanternPost(LanternPost.Style.STONE, p).at(-2, -2).at(2, -2).at(-2, 2).at(2, 2))
				.add(new Paving(-1, 19, 1, 46, court, Paving.Pattern.PATH))
				// Down from the plaza to the fitted outer court (the flight starts at the fixed upper edge, so any fitted depth fits).
				.add(new StairFlight(0, 19, Direction.SOUTH, p, court).width(5))
				// Stepping stones round the Aula Pendiri to the screen-wall rock at the foot of the main peak.
				.add(new Paving(-15, -18, -14, -6, p, Paving.Pattern.STEPPING))
				.add(new Paving(14, -18, 15, -6, p, Paving.Pattern.STEPPING))
				.add(new Paving(-13, -18, 13, -18, p, Paving.Pattern.STEPPING))
				.add(Grounds.screenWall(0, -17, p, 2)));
		b.add(Component.of("t1_aula", 1).kind("hall").name("aula_pendiri").group(Group.MAIN)
				.add(new TerrainZone(-14, -18, 14, -5, p).edge(TerrainZone.Edge.NONE))
				.add(new Hall(0, -11).bays(3, 4, 6, 4, 3).depth(7).pillar(5).platform(2).stepsInset(1).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1)
						.porch(1).doors(Hall.Doors.OPEN).gableField(Palette.st(Palette.PILLAR)).interior(Interiors.Preset.MAIN_HALL)));
		b.add(Component.of("t1_dorms", 1).kind("hall").name("dorm").group(Group.SIDE)
				.add(new TerrainZone(37, -11, 47, 9, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(42, -1).front(Direction.WEST).bays(3, 4, 4, 4, 3).depth(7).pillar(4).platform(1).rank(Rank.A)
						.roof(RoofBuilder.Type.GABLE, 1).overhang(1).porch(0).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN))
				.add(Stamp.horseHeadGable(42, -11, Direction.SOUTH, 5, 4, 3))
				.add(Stamp.horseHeadGable(42, 9, Direction.SOUTH, 5, 4, 3))
				.add(new TerrainZone(17, -20, 35, -12, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(26, -16).front(Direction.SOUTH).bays(2, 3, 4, 3, 2).depth(5).pillar(4).platform(1).rank(Rank.A)
						.roof(RoofBuilder.Type.GABLE, 1).overhang(1).porch(0).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN))
				.add(Stamp.horseHeadGable(18, -16, Direction.EAST, 5, 3, 2))
				.add(Stamp.horseHeadGable(34, -16, Direction.EAST, 5, 3, 2))
				.add(new Paving(13, -10, 37, -10, p, Paving.Pattern.STEPPING)));
		b.add(Component.of("t1_gate", 1).kind("gate").name("inner_gate").group(Group.BOUNDARY)
				.add(Gates.innerGate(0, 17, p, 2, true))
				.add(new LanternPost(LanternPost.Style.RED_POST, p).pair(4, 15))
				.add(Marks.anchor("arrival", 0, 15, p)));
		b.add(Component.of("t1_well", 1).kind("well").name("well").group(Group.SIDE)
				.add(new Paving(14, 7, 20, 13, p, Paving.Pattern.PLAZA))
				.add(new Pavilion(17, 10, p).size(3).sides(4).rank(Rank.B).entry(Direction.WEST))
				.add(Stamp.vat(15, 10, p)).add(Stamp.vat(19, 10, p)).add(Stamp.vat(17, 8, p)).add(Stamp.vat(17, 12, p))
				.add(Stamp.vat(-4, -7, p)).add(Stamp.vat(-6, -7, p)).add(Stamp.vat(4, -7, p)).add(Stamp.vat(6, -7, p))
				.add(Marks.anchor("post_water", 17, 10, p)));
		b.add(Component.of("t1_hero", 1).kind("tree").name("hero_tree").group(Group.GARDEN)
				.add(new Paving(-32, 10, -28, 14, p, Paving.Pattern.MOSS))
				.add(new Tree(Tree.Kind.PINE, -30, 12, p).hero(true).force(true).within(-35, 10, -26, 17))
				.add(Rock.scholar(-28, 10, p, 3)));
		b.add(ornament(1).add(new Ornament(1, p).flowers(-8, 13, 8, 13, -8, -5, 8, -5))
				// Review views: the paifang, across the west lake to the summit pagoda, the aerial.
				.add(Marks.camera(1, 0, 58, approach, 0, -46, Lv.at(25))).add(Marks.camera(2, -36, 6, p, 0, -89, Lv.at(60)))
				.add(Marks.camera(3, 79, 59, Lv.at(90), 0, -30, p)));

		// ============================================================ T2 Api Dapur dan Kebun Obat
		b.add(Component.of("t2_kitchen", 2).kind("hall").name("kitchen_court").group(Group.SIDE)
				.add(new TerrainZone(21, -11, 35, 4, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Wall(Wall.Style.WHITE_GARDEN, p, 3).ring(21, -11, 35, 4).gap(21, -3, 1).drips(2))
				.add(new Hall(30, -3).front(Direction.WEST).bays(3, 4, 3).depth(5).pillar(4).platform(1).rank(Rank.A).roof(RoofBuilder.Type.GABLE, 1)
						.overhang(1).porch(0).interior(Interiors.Preset.KITCHEN).doors(Hall.Doors.NONE))
				.add(Stamp.horseHeadGable(30, -9, Direction.SOUTH, 5, 3, 2))
				.add(Stamp.horseHeadGable(30, 3, Direction.SOUTH, 5, 3, 2))
				.add(Stamp.coop(23, -8, Direction.EAST, p))
				.add(Stamp.vat(23, 2, p))
				.add(new Tree(Tree.Kind.PLUM, 23, -4, p).force(true)));
		// Stepped herb fields east of the dorms: two low stone terraces and a field at their foot.
		b.add(Component.of("t2_herbs", 2).kind("garden").name("herb_garden").group(Group.GARDEN)
				.add(new Terrace(52, -10, 78, -4, Lv.at(2), Lv.at(1)).style(Terrace.Style.STONE).balustrade(false).opening(64, -4, 1))
				.add(new Terrace(52, -3, 78, 3, Lv.at(1), p).style(Terrace.Style.STONE).balustrade(false).opening(64, 3, 1))
				.add(Grounds.herbBeds(53, -9, 77, -5, Lv.at(2)))
				.add(Grounds.herbBeds(53, -2, 77, 2, Lv.at(1)))
				.add(Grounds.herbBeds(52, 5, 78, 11, p))
				.add(new StairFlight(64, 4, Direction.NORTH, p, Lv.at(1)).width(3))
				.add(new StairFlight(64, -3, Direction.NORTH, Lv.at(1), Lv.at(2)).width(3)));
		b.add(Component.of("t2_trees", 2).kind("trees").name("trees").group(Group.GARDEN)
				.add(new Tree(Tree.Kind.PINE, -36, 57, approach).force(true))
				.add(new Tree(Tree.Kind.PINE, 36, 57, approach).force(true))
				.add(new Tree(Tree.Kind.PINE, -56, 56, approach).force(true))
				.add(new Tree(Tree.Kind.PINE, 56, 56, approach).force(true))
				.add(new Tree(Tree.Kind.GINKGO, -16, 56, approach).force(true))
				.add(new Tree(Tree.Kind.GINKGO, 16, 56, approach).force(true))
				.add(new Tree(Tree.Kind.PINE, -24, 30, court).force(true))
				.add(new Tree(Tree.Kind.PINE, 24, 30, court).force(true))
				.add(new Tree(Tree.Kind.PLUM, -34, 15, p).force(true))
				.add(new Tree(Tree.Kind.PLUM, 30, 12, p).force(true))
				.add(new Tree(Tree.Kind.GINKGO, -22, -15, p).force(true))
				.add(new Tree(Tree.Kind.GINKGO, -36, -18, p).force(true))
				.add(new Tree(Tree.Kind.MAPLE, -22, -20, p).force(true))
				.add(new Tree(Tree.Kind.PLUM, -8, 23, court).force(true)).add(new Tree(Tree.Kind.PLUM, 8, 23, court).force(true))
				.add(new Tree(Tree.Kind.PLUM, -17, -11, p).force(true))
				.add(new Tree(Tree.Kind.PINE, 50, 13, p).force(true))
				.add(new Tree(Tree.Kind.WILLOW, -38, -14, p).force(true))
				// Trunks at least 5 inside the footprint edge (x ±80): full crowns, never cut flat.
				.add(new Tree(Tree.Kind.PINE, 74, 14, p).force(true))
				.add(new Tree(Tree.Kind.PINE, -74, 44, court).force(true))
				.add(new Tree(Tree.Kind.GINKGO, 48, 44, court).force(true))
				.add(new Tree(Tree.Kind.PLUM, -48, 44, court).force(true))
				.add(new Tree(Tree.Kind.PINE, -70, -54, p).force(true))
				.add(new Tree(Tree.Kind.PINE, 64, -88, p).force(true)));
		b.add(ornament(2).add(new Ornament(2, p).lanterns(LanternPost.Style.JADE, 50, -6, 50, 6, 21, 6, 36, 6)));

		// ============================================================ T3 Paviliun Kitab
		// A three-storey square scripture pavilion west of the plaza; the lecterns stand at its east door so the "library"
		// anchor stays within 16 of the stele. Covered galleries flank the chuihuamen.
		b.add(Component.of("t3_scripture", 3).kind("hall").name("scripture").group(Group.SIDE)
				.add(new TerrainZone(-28, -8, -12, 8, p).edge(TerrainZone.Edge.SLOPE))
				// An octagonal three-floor library tower (templates 7 and 8 use square ones), pavilion finial: the end rod belongs to
				// the summit pagoda alone.
				.add(new Pagoda(-20, 0, p).floors(3, new int[]{6, 5, 4}, new int[]{7, 6, 5}).overhangs(3, 3, 3).plinthMargin(1).entry(Direction.EAST)
						.octagonal(true).spire(false))
				.add(Put.library(-20, 0, Direction.EAST, p, 1, -5, 5, -5, 5, 72, 2, 3)));
		b.add(Component.of("t3_galleries", 3).kind("gallery").name("galleries").group(Group.SIDE)
				.add(new Corridor(-5, 17, -17, 17, p).wall(1))
				.add(new Corridor(5, 17, 17, 17, p).wall(-1)));
		b.add(Component.of("t3_plaza", 3).kind("paving").name("plaza").group(Group.AXIS)
				.add(new Paving(-12, -7, -3, 15, p, Paving.Pattern.PLAZA))
				.add(new Paving(3, -7, 12, 15, p, Paving.Pattern.PLAZA)));
		b.add(ornament(3).add(new Ornament(3, p).flowers(-12, 13, 12, 13, -18, 15, 18, 15)));

		// ============================================================ T4 Tembok dan Air
		b.add(Component.of("t4_lake", 4).kind("lake").name("lake").group(Group.GARDEN)
				.add(lake)
				.add(new Pavilion(-52, -4, p).size(5).sides(6).eaves(2).rank(Rank.C).entry(Direction.EAST))
				.add(new Bridge(Bridge.Type.ZIGZAG, -47, -1, -36, -1, p))
				.add(SetPieces.stoneBoat(-66, -2, 9, p))
				.add(new Bridge(Bridge.Type.MOON_ARCH, -48, -24, -40, -24, p).width(3))
				.add(new Bridge(Bridge.Type.MOON_ARCH, -48, -35, -40, -35, p).width(3))
				.add(new Bridge(Bridge.Type.MOON_ARCH, -48, -52, -40, -52, p).width(5))
				.add(new Tree(Tree.Kind.WILLOW, -36, 6, p).force(true))
				.add(new Tree(Tree.Kind.WILLOW, -38, -10, p).force(true))
				.add(new Tree(Tree.Kind.WILLOW, -48, -44, p).force(true))
				.add(new Tree(Tree.Kind.WILLOW, -76, 6, p).force(true))
				.add(Rock.cluster(-74, -12, p, 2)).add(Rock.cluster(-38, 10, p, 2))
				.add(new LanternPost(LanternPost.Style.JADE, p).at(-35, -6).at(-38, 2).at(-40, -20).at(-40, -44)));
		// Puncak Awan Harum, the pill peak (+8), with the Menara Pil and the pill-herb terraces.
		b.add(Component.of("t4_pill_peak", 4).kind("tower").name("menara_pil").group(Group.SIDE)
				.add(new Massif("t4_awan", p).slope(1).pines(4).avoid(lake)
						.pad(-76, -47, -52, -24, l8, Massif.Pad.STONE, true, 3)
						.stair(-49, -35, Direction.WEST, p, l8, 3, Terrace.Style.STONE))
				.add(new Pagoda(-64, -35, l8).floors(3, new int[]{6, 5, 4}, new int[]{6, 5, 4}).octagonal(true).overhangs(3, 2, 2).plinthMargin(1).spire(false)
						.entry(Direction.EAST))
				.add(Put.interior(-64, -35, Direction.EAST, l8, 1, Interiors.Preset.ALCHEMY, 2, -4, 4, -4, 4, 4, Rank.B))
				.add(Put.fittings(-64, -35, Direction.EAST, l8, 1, new int[][]{{4, -3}, {-4, 2}}, new int[0][]))
				.add(Grounds.herbBeds(-75, -46, -72, -26, l8))
				.add(new LanternPost(LanternPost.Style.STONE, l8).at(-55, -28).at(-55, -43))
				// The gardener keeps working the herb fields of T2 (the pill-peak beds would move the post up the peak).
				.add(Marks.anchor("post_garden", 65, 7, p)));
		b.add(Component.of("t4_shanmen", 4).kind("gate").name("shanmen").group(Group.BOUNDARY)
				.add(new Paving(-2, 22, 2, 36, court, Paving.Pattern.AXIS))
				.add(Gates.gateHall(0, 41, court, 5).stepsInset(0))
				// The stubs start at the gate's corner (core x ±9) and read as its wings.
				.add(new Wall(Wall.Style.RED_PALACE, court, 5).line(-22, 41, -10, 41).line(10, 41, 22, 41))
				.add(Rock.hill(-26, 41, court, 4)).add(Rock.hill(26, 41, court, 4))
				.add(new Paving(-2, 47, 2, 60, approach, Paving.Pattern.AXIS))
				.add(Stamp.lion(-6, 50, Direction.SOUTH, approach)).add(Stamp.lion(6, 50, Direction.SOUTH, approach))
				// Down from the shanmen to the fitted approach (the flight starts at the court edge whatever the fitted depth).
				.add(new StairFlight(0, 47, Direction.SOUTH, court, approach).width(5))
				.add(new LanternPost(LanternPost.Style.RED_POST, court).pair(5, 35)));
		b.add(Component.of("t4_boundary", 4).kind("boundary").name("boundary_stones").group(Group.BOUNDARY)
				.add(Stamp.boundaryStone(-64, -99, Direction.NORTH, p)).add(Stamp.boundaryStone(-32, -99, Direction.NORTH, p))
				.add(Stamp.boundaryStone(32, -99, Direction.NORTH, p)).add(Stamp.boundaryStone(64, -99, Direction.NORTH, p))
				.add(Stamp.boundaryStone(-79, -80, Direction.WEST, p)).add(Stamp.boundaryStone(79, -80, Direction.EAST, p))
				.add(Stamp.boundaryStone(-79, -52, Direction.WEST, p)).add(Stamp.boundaryStone(79, -52, Direction.EAST, p))
				.add(Stamp.boundaryStone(-79, -20, Direction.WEST, p)).add(Stamp.boundaryStone(79, -20, Direction.EAST, p))
				.add(Stamp.boundaryStone(-79, 12, Direction.WEST, p)).add(Stamp.boundaryStone(79, 16, Direction.EAST, p))
				.add(Stamp.boundaryStone(-40, 42, Direction.SOUTH, court)).add(Stamp.boundaryStone(40, 42, Direction.SOUTH, court))
				.add(Stamp.boundaryStone(-60, 44, Direction.SOUTH, court)).add(Stamp.boundaryStone(62, 44, Direction.SOUTH, court)));
		b.add(ornament(4).add(new Ornament(4, court).lanterns(LanternPost.Style.STONE, -5, 31, 5, 31, -5, 26, 5, 26)));

		// ============================================================ T5 Formasi Agung
		b.add(Component.of("t5_disc", 5).kind("formation").name("disc").group(Group.AXIS)
				.add(new FormationDisc(6, 9, p).clip(-12, -7, 12, 16)));
		b.add(Component.of("t5_altars", 5).kind("altar").name("gate_altars").group(Group.BOUNDARY)
				.add(new TerrainZone(-53, 47, -19, 60, approach).edge(TerrainZone.Edge.SLOPE))
				.add(new TerrainZone(19, 47, 53, 60, approach).edge(TerrainZone.Edge.SLOPE))
				.add(new GateAltar(-26, 53, approach)).add(new GateAltar(26, 53, approach))
				.add(new GateAltar(-46, 53, approach)).add(new GateAltar(46, 53, approach)));
		// Four watch pavilions on spurs at the rim: two flanking the approach, two behind the peaks.
		b.add(Component.of("t5_watch", 5).kind("tower").name("watch_towers").group(Group.BOUNDARY)
				.add(new Massif("t5_sw", p).slope(1).pines(2).blob(-68, 51, 7, Lv.at(6), 0.8).pad(-70, 49, -66, 53, Lv.at(6), Massif.Pad.NATURAL, true, 2))
				.add(new Pavilion(-68, 51, Lv.at(6)).size(5).sides(6).eaves(2).rank(Rank.B).entry(Direction.EAST))
				.add(new Massif("t5_se", p).slope(1).pines(2).blob(68, 51, 7, Lv.at(6), 0.8).pad(66, 49, 70, 53, Lv.at(6), Massif.Pad.NATURAL, true, 2))
				.add(new Pavilion(68, 51, Lv.at(6)).size(5).sides(4).eaves(2).rank(Rank.B).entry(Direction.WEST))
				.add(new Massif("t5_nw", p).slope(1).pines(2).blob(-32, -93, 7, Lv.at(8), 0.8).pad(-34, -95, -30, -91, Lv.at(8), Massif.Pad.NATURAL, true, 2))
				.add(new Pavilion(-32, -93, Lv.at(8)).size(5).sides(8).rank(Rank.B).entry(Direction.SOUTH))
				.add(new Massif("t5_ne", p).slope(1).pines(2).blob(56, -94, 7, Lv.at(8), 0.8).pad(54, -96, 58, -92, Lv.at(8), Massif.Pad.NATURAL, true, 2))
				.add(new Pavilion(56, -94, Lv.at(8)).size(5).sides(0).rank(Rank.B).entry(Direction.SOUTH)));
		b.add(ornament(5).add(new Ornament(5, p).lanterns(LanternPost.Style.STONE, -11, -6, 11, -7, -12, 12, 12, 12)));

		// ============================================================ T6 Aula Utama
		// The sculpted main peak (natural rock kept wherever it is higher): stone terraces +3 / +6 / +9, marble +11 / +13
		// under the 9-bay double-eave wudian, a natural slope behind it (the +13 lawn and the Grand Ancestral Hall come at T8)
		// and the rock ridge
		// that the summit crowns at T9. The nine-flight stair climbs the axis from the screen-wall rock: two flights per stone
		// terrace, one per marble terrace, the ninth the hall's own steps.
		b.add(Component.of("t6_main_hall", 6).kind("hall").name("main_hall").group(Group.MAIN)
				.add(new Massif("t6_peak", p).slope(1).pines(0).avoid(lake)
						.pad(-37, -62, 37, -22, l3, Massif.Pad.STONE, true, -1)
						.pad(-33, -62, 33, -26, l6, Massif.Pad.STONE, true, -1)
						.pad(-29, -62, 29, -30, l9, Massif.Pad.STONE, true, -1)
						.pad(-27, -60, 27, -32, l11, Massif.Pad.MARBLE)
						.pad(-25, -58, 25, -34, l13, Massif.Pad.MARBLE)
						.blob(0, -88, 16, Lv.at(15), 0.5)
						.opening(0, -22, 5).opening(0, -26, 5).opening(0, -30, 5).opening(0, -32, 5).opening(0, -34, 5))
				.add(new StairFlight(0, -18, Direction.NORTH, p, l3).width(9).style(Terrace.Style.MARBLE).ramp(3).landingEvery(2))
				.add(new StairFlight(0, -22, Direction.NORTH, l3, l6).width(9).style(Terrace.Style.MARBLE).ramp(3).landingEvery(2))
				.add(new StairFlight(0, -26, Direction.NORTH, l6, l9).width(9).style(Terrace.Style.MARBLE).ramp(3).landingEvery(2))
				.add(new StairFlight(0, -30, Direction.NORTH, l9, l11).width(9).style(Terrace.Style.MARBLE).ramp(3))
				.add(new StairFlight(0, -32, Direction.NORTH, l11, l13).width(9).style(Terrace.Style.MARBLE).ramp(3))
				.add(new Hall(0, -46).site(l13).bays(3, 4, 4, 4, 6, 4, 4, 4, 3).depth(15).pillar(6).platform(2).rank(Rank.D)
						.roof(RoofBuilder.Type.HIP, 2).interior(Interiors.Preset.MAIN_HALL).gableField(Palette.st(Palette.PILLAR)))
				.add(new LanternPost(LanternPost.Style.PALACE, l13).at(-7, -34, Direction.EAST).at(7, -34, Direction.WEST))
				.add(new LanternPost(LanternPost.Style.PAGODA, l3).at(-7, -23).at(7, -23))
				.add(new LanternPost(LanternPost.Style.PAGODA, l9).at(-7, -30).at(7, -30))
				.add(new Tree(Tree.Kind.PINE, -33, -23, l3).force(true)).add(new Tree(Tree.Kind.PINE, 33, -23, l3).force(true))
				.add(new Tree(Tree.Kind.PINE, -31, -55, l6).force(true)).add(new Tree(Tree.Kind.PINE, 31, -55, l6).force(true)));
		b.add(Component.of("t6_paifang", 6).kind("paifang").name("paifang").group(Group.BOUNDARY)
				.add(new Paifang(0, 56, approach).bays(5).roofs(7).material(Paifang.Material.WHITE_STONE))
				.add(new GlazedScreen(-17, 45, court, 4))
				.add(new GlazedScreen(17, 45, court, 4)));
		b.add(Component.of("t6_dings", 6).kind("ding").name("dings").group(Group.GARDEN)
				.add(Stamp.ding(-11, -27, l6, true)).add(Stamp.ding(11, -27, l6, false)));
		b.add(ornament(6).add(new Ornament(6, l13).banners(-24, -35, 24, -35, -24, -57, 24, -57)));

		// ============================================================ T7 Lapangan Latihan
		b.add(Component.of("t7_drill", 7).kind("drill").name("drill_ground").group(Group.SIDE)
				.add(Grounds.drillGround(26, 22, 46, 30, court, 8, true))
				.add(Grounds.archery(26, 33, 46, 40, court, 8))
				.add(Stamp.weaponRack(36, 23, Direction.NORTH, court)));
		b.add(Component.of("t7_arena", 7).kind("arena").name("arena").group(Group.SIDE)
				.add(Grounds.arena(-44, 24, -28, 38, court, 2))
				.add(Stamp.drum(-46, 24, court)).add(Stamp.drum(-46, 38, court))
				.add(new Pavilion(-36, 20, court).size(5).sides(-1).rank(Rank.B).entry(Direction.SOUTH)));
		b.add(Component.of("t7_bell_drum", 7).kind("tower").name("bell_drum").group(Group.SIDE)
				.add(Gates.cornerTower(-16, 30, court, 2, 2, Direction.SOUTH))
				.add(Gates.cornerTower(16, 30, court, 2, 2, Direction.SOUTH))
				.add(Put.stack(-16, 30, court, 4, Phase.INTERIOR, Palette.bell(Direction.SOUTH, true)))
				.add(Put.stack(16, 30, court, 1, Phase.INTERIOR, Palette.st(Blocks.CHISELED_CINNABAR), Palette.barrel(Direction.UP)))
				.add(Stamp.flagPole(-6, 34, Direction.SOUTH, court, 6)).add(Stamp.flagPole(6, 34, Direction.SOUTH, court, 6)));
		b.add(Component.of("t7_inner_dorms", 7).kind("hall").name("inner_dorm").group(Group.SIDE)
				.add(new Hall(58, 30).site(court).front(Direction.WEST).bays(3, 4, 4, 4, 3).depth(7).pillar(4).platform(1).rank(Rank.B)
						.roof(RoofBuilder.Type.XIESHAN, 1).porch(0).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN))
				.add(new Hall(71, 30).site(court).front(Direction.WEST).bays(3, 4, 6, 4, 3).depth(7).pillar(4).storeys(2, 4).platform(1).rank(Rank.B)
						.roof(RoofBuilder.Type.XIESHAN, 1).porch(0).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN)));
		// A climbing gallery up to the +3 terrace (W) and a walled gallery along the main peak's east foot.
		b.add(Component.of("t7_galleries", 7).kind("gallery").name("galleries").group(Group.SIDE)
				.add(new Corridor(-30, -9, -30, -21, p).to(l3))
				.add(new Corridor(41, -22, 41, -38, p).wall(1).openings(5)));
		b.add(ornament(7).add(new Ornament(7, court).banners(-48, 21, 48, 21, -48, 41, 48, 41)
				.lanterns(LanternPost.Style.HANGING, -24, 21, 24, 21)));

		// ============================================================ T8 Pagoda dan Leluhur
		// The +13 lawn behind the main hall (it fills the T6 slope; its edges stay natural) with the Grand Ancestral Hall.
		b.add(Component.of("t8_ancestral", 8).kind("hall").name("ancestral_hall").group(Group.MAIN)
				.add(new Massif("t8_lawn", p).slope(1).pines(0).avoid(lake).pad(-20, -73, 20, -59, l13, Massif.Pad.NATURAL, true, 2))
				.add(new Hall(0, -66).site(l13).bays(3, 4, 4, 6, 4, 4, 3).depth(9).pillar(5).platform(2).rank(Rank.C).roof(RoofBuilder.Type.XIESHAN, 1)
						.porch(1).gilded(true).interior(Interiors.Preset.ANCESTRAL).doors(Hall.Doors.OPEN).gableField(Palette.st(Palette.PILLAR))));
		// Puncak Dalam (NW, +14): the Inner Disciples' Hall and dorm behind a ring of jade formation lanterns.
		b.add(Component.of("t8_dalam", 8).kind("peak").name("inner_peak").group(Group.SIDE)
				.add(new Massif("t8_dalam", p).slope(1).pines(5).avoid(lake)
						.pad(-76, -96, -44, -64, l14, Massif.Pad.STONE, true, 6)
						.blob(-70, -98, 8, Lv.at(18), 0.8))
				.add(new Hall(-60, -86).site(l14).bays(3, 4, 6, 4, 3).depth(9).pillar(5).platform(2).rank(Rank.C).roof(RoofBuilder.Type.XIESHAN, 1)
						.porch(1).interior(Interiors.Preset.STORE).doors(Hall.Doors.OPEN).gableField(Palette.st(Palette.PLASTER)))
				.add(new Hall(-62, -72).site(l14).front(Direction.SOUTH).bays(3, 4, 4, 4, 3).depth(5).pillar(4).platform(1).rank(Rank.B)
						.roof(RoofBuilder.Type.XIESHAN, 1).porch(0).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN))
				.add(new LanternPost(LanternPost.Style.JADE, l14).at(-74, -66).at(-74, -80).at(-74, -94).at(-46, -94).at(-46, -80)
						.at(-60, -94).at(-60, -66))
				.add(new Paving(-62, -67, -45, -67, l14, Paving.Pattern.PATH).earth(false))
				.add(new Paving(-48, -78, -48, -68, l14, Paving.Pattern.PATH).earth(false))
				.add(new Paving(-60, -78, -49, -78, l14, Paving.Pattern.PATH).earth(false)));
		// Puncak Tetua (NE, +14): the elders' residence and the round meditation pavilion with the Qi seat (tier 3).
		b.add(Component.of("t8_tetua", 8).kind("hall").name("elders").group(Group.SIDE)
				.add(new Massif("t8_tetua", p).slope(1).pines(5)
						.pad(44, -74, 76, -44, l14, Massif.Pad.STONE, true, 6)
						.blob(72, -78, 7, Lv.at(17), 0.8))
				.add(new Hall(62, -64).site(l14).front(Direction.SOUTH).bays(3, 4, 6, 4, 3).depth(7).pillar(5).platform(1).rank(Rank.C)
						.roof(RoofBuilder.Type.XIESHAN, 1).porch(1).doors(Hall.Doors.OPEN).gableField(Palette.st(Palette.PLASTER)))
				.add(new LanternPost(LanternPost.Style.JADE, l14).at(46, -46).at(74, -46).at(74, -72).at(60, -46).at(46, -58))
				.add(new Paving(45, -68, 48, -68, l14, Paving.Pattern.PATH).earth(false))
				.add(new Paving(48, -67, 48, -50, l14, Paving.Pattern.PATH).earth(false))
				.add(new Paving(49, -58, 62, -58, l14, Paving.Pattern.PATH).earth(false))
				.add(new Paving(49, -50, 60, -50, l14, Paving.Pattern.PATH).earth(false)));
		b.add(Component.of("t8_meditation", 8).kind("pavilion").name("meditation_pavilion").group(Group.GARDEN)
				.add(new Pavilion(66, -50, l14).size(5).sides(0).eaves(2).seat(3).rank(Rank.C).entry(Direction.WEST)));
		// The two covered gallery bridges from the inner peaks to the ancestral lawn, on stone piers over the valleys.
		b.add(Component.of("t8_bridges", 8).kind("bridge").name("bridge").group(Group.SIDE)
				.add(new Corridor(-44, -68, -21, -68, l14).to(l13))
				.add(new Piers(-44, -68, -21, -68, l14, l13, 5))
				.add(new Corridor(44, -68, 21, -68, l14).to(l13))
				.add(new Piers(44, -68, 21, -68, l14, l13, 5)));
		// Puncak Binatang (E, +8): the beast garden.
		b.add(Component.of("t8_beasts", 8).kind("garden").name("beast_garden").group(Group.GARDEN)
				.add(new Massif("t8_binatang", p).slope(1).pines(4)
						.pad(52, -38, 77, -16, l8, Massif.Pad.STONE, true, 3)
						.stair(49, -27, Direction.EAST, p, l8, 3, Terrace.Style.STONE))
				.add(Grounds.beastGarden(58, -36, 76, -18, l8))
				.add(new Pavilion(55, -34, l8).size(3).sides(6).rank(Rank.B).entry(Direction.EAST)));
		// The bamboo grove, the candidates' test path to the knoll and the gathering square (SW of the outer court).
		b.add(Component.of("t8_test_path", 8).kind("garden").name("bamboo").group(Group.GARDEN)
				.add(new Massif("t8_knoll", court).slope(1).pines(2).pad(-78, 18, -70, 27, court.plus(6), Massif.Pad.NATURAL, true, 2))
				.add(new TestPath(-52, 23, Direction.WEST, court, court.plus(6), 10))
				.add(new BambooGrove(-78, 14, -50, 18, court).maxHeight(11))
				.add(new BambooGrove(-69, 28, -50, 31, court).maxHeight(9))
				.add(new Paving(-74, 33, -53, 41, court, Paving.Pattern.PLAZA))
				.add(Stamp.boundaryStone(-64, 37, Direction.SOUTH, court))
				.add(new LanternPost(LanternPost.Style.STONE, court).at(-74, 33).at(-53, 33).at(-74, 41).at(-53, 41)));
		b.add(ornament(8).add(new Ornament(8, l13).lanterns(LanternPost.Style.JADE, -18, -60, 18, -60, -18, -72, 18, -72)));

		// ============================================================ T9 Menara Langit Membara
		b.add(Component.of("t9_tower", 9).kind("tower").name("blazing_tower").group(Group.MAIN)
				.add(new Pavilion(15, -3, p).size(5).sides(4).pillar(3).rank(Rank.C).entry(Direction.WEST))
				.add(new TowerShaft(15, -3, p, 7, -4).roomSide(Direction.SOUTH))
				// Seven rooms (the deepest campfire 28 down, inside the 32 sphere) south of the well under a dark tower court east of
				// the disc; the anchor is the plaza cell at the ting's west entry.
				.add(Marks.anchor("tower", 11, -3, p)));
		// The summit (+18) and the Pagoda Kaca Sembilan Harta, top about +96.
		b.add(Component.of("t9_pagoda", 9).kind("pagoda").name("pagoda").group(Group.MAIN)
				.add(new Massif("t9_summit", p).slope(1).pines(4)
						.pad(-16, -98, 16, -80, l18, Massif.Pad.STONE, true, 6)
						.stair(0, -74, Direction.NORTH, l13, l18, 7, Terrace.Style.MARBLE))
				.add(new Pagoda(0, -89, l18).floors(9, new int[]{6, 6, 5, 4, 4, 3, 3, 3, 3}, new int[]{8, 7, 7, 6, 6, 5, 5, 5, 5}).octagonal(true)
						.glazed(true).plinth(2).plinthMargin(2).entry(Direction.SOUTH)));
		// The hanging-mountain archipelago on jade chains (mist instead of water).
		b.add(Component.of("t9_islands", 9).kind("island").name("island").group(Group.GARDEN)
				.add(new HangingIsland(32, -86, 12, Lv.at(44)).jadeChains(true).turtle(true).tether(26, -80, p).tether(38, -92, p))
				.add(new HangingIsland(-27, -93, 5, Lv.at(36)).jadeChains(true).sporeBlossom(true).tether(-27, -91, p))
				.add(new HangingIsland(-56, -56, 5, Lv.at(32)).jadeChains(true).pad(true).tether(-56, -54, p))
				.add(new Pavilion(-56, -56, Lv.at(32)).size(3).sides(8).rank(Rank.B))
				.add(new HangingIsland(66, -6, 4, Lv.at(30)).jadeChains(true).sporeBlossom(true).tether(66, -7, Lv.at(2)))
				.add(new HangingIsland(60, -89, 4, Lv.at(40)).jadeChains(true).tether(60, -87, p)));
		b.add(Component.of("t9_aula_dao_langit", 9).kind("hall").name("aula_dao_langit").group(Group.MAIN)
				.add(new RoundHall(32, -86, Lv.at(44)).radius(6).tiers(1)));
		// The arched sky bridge from the island to the pagoda's fourth floor (+44), through an opened bay.
		b.add(Component.of("t9_sky_bridge", 9).kind("bridge").name("bridge").group(Group.GARDEN)
				.add(new Bridge(Bridge.Type.SKY, 21, -89, 6, -89, Lv.at(44)).width(5))
				.add(Put.air(4, -90, 5, -88, Lv.at(44), 0, 2)));
		b.add(Component.of("t9_waterfalls", 9).kind("waterfall").name("waterfall").group(Group.GARDEN)
				.add(new Waterfall("awan", -62, -24, l8, p, Direction.SOUTH))
				.add(new Waterfall("dalam", -45, -64, l14, p, Direction.SOUTH)));
		b.add(Component.of("t9_finale", 9).kind("finale").name("finale").group(Group.GARDEN)
				.add(Marks.petals(-12, -7, 12, 16, p, 10))
				.add(Marks.petals(-20, -72, 20, -60, l13, 6))
				.add(new LanternPost(LanternPost.Style.PALACE, approach).at(-4, 56, Direction.EAST).at(4, 56, Direction.WEST))
				.add(new LanternPost(LanternPost.Style.PALACE, court).at(-4, 26, Direction.EAST).at(4, 26, Direction.WEST))
				.add(new LanternPost(LanternPost.Style.PALACE, l18).at(-6, -80, Direction.EAST).at(6, -80, Direction.WEST)));
		b.add(ornament(9).add(new Ornament(9, p).flowers(-12, -6, 12, -6, -24, 10, 24, 10)));
		return b.build();
	}

	private static Component.Builder ornament(int tier) {
		return Component.of("t" + tier + "_ornament", tier).kind("ornament").name("ornament_" + tier).group(Group.ORNAMENT);
	}

	private T9HeavenlyCourt() {
	}
}
