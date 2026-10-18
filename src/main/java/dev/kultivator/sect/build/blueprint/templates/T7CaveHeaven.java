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
import dev.kultivator.sect.build.blueprint.templates.t7t9.Lake;
import dev.kultivator.sect.build.blueprint.templates.t7t9.Massif;
import dev.kultivator.sect.build.blueprint.templates.t7t9.Put;
import dev.kultivator.sect.build.blueprint.templates.t7t9.TestPath;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

/**
 * Template 7, Gua Surga Tanah Berkah / Cave Heaven, Blessed Land (DESIGN 21.1, 21.3, 21.4): 109×117, a lake valley with
 * no perimeter wall. The axis runs gate → plaza → a straight marble bridge across the Blessed Land lake (with its mid-lake
 * pavilion on an islet) → the main peak, whose marble stair climbs +4 / +8 to the +12 pad of the 7-bay double-eave
 * wudian. The lake wraps the peak in two lobes and runs an arm down the west side of the plaza, where a nine-turn bridge
 * leads from a waterside pavilion to the islet of the Pagoda Kaca Tujuh Harta, beside the stone boat and the willows. The
 * Cave-Heaven grotto hill (T9) rises at the plaza's north-west corner with the six tower rooms inside and below it and a
 * waterfall into the arm; three chained hanging rocks float over the lake, the largest with a carved turtle head. The
 * Inner peak (NW) and the Elders' peak (NE) join the main pad by sky bridges at T8; four watch pavilions stand on the
 * valley rim, four gate altars (empty sockets) by the gate, boundary stones mark the rest.
 * <p>
 * Sources (DESIGN 21.1): <i>Martial Peak</i> ch. 3831: the 36 Cave Heavens and 72 Paradises, the forces that dominate the
 * 3,000 Worlds; the classical scheme of Cave Heavens and Blessed Lands: Sima Chengzhen's <i>Tiandi gongfu tu</i> (Tang) and
 * Du Guangting's <i>Dongtian fudi yuedu mingshan ji</i> (901). Rank by height and peaks behind barriers: <i>I Shall Seal
 * the Heavens</i> ch. 2-3 (the Reliance Sect). Test path: <i>A Record of a Mortal's Journey</i> ch. 1-5. Hero tree, duty
 * stations and the tower as in DESIGN 21.2. Pagoda name after <i>Soul Land</i> (a martial soul there, the storey count is
 * design). Floating rocks: <i>Liezi</i>, "Tang Wen" (the turtle head nods to it; the chains are art direction, not canon).
 * The stone boat, the nine-turn bridge and the grotto are art direction from the Summer Palace and Jiangnan gardens.
 */
public final class T7CaveHeaven {

	public static TemplateSpec build() {
		Lv p = Lv.PLAZA;
		Lv court = Lv.zone("court");
		Lv main = Lv.zone("main");
		Lv inner = Lv.zone("inner");
		Lv elders = Lv.zone("elders");
		Lv knoll = Lv.at(5);
		TemplateSpec.Builder b = TemplateSpec.builder(7).footprint(-54, 54, -72, 44).envelope(62, 26).relief(44).earthwork(63765)
				.disc(6, 8).territory(80).towerRooms(6).qiTier(3).gateAltars(4).planVersion(1)
				.zone("plaza", -14, -19, 14, 18, 0)
				.zone("court", -44, 19, 44, 44, 0, -2, 0)
				.zone("main", -33, -72, 33, -48, 12)
				.zone("inner", -52, -71, -40, -57, 10, 10, 13, "main", -3)
				.zone("elders", 40, -71, 52, -57, 10, 10, 13, "main", -3);

		// The Blessed Land lake (T4): a belt behind the Aula Pendiri, two lobes along the main peak, the west arm by the plaza.
		Lake lake = new Lake("t7_lake", p).lotus(5)
				.lobe(0, -26, 40, 6)
				.lobe(-44, -36, 8, 12)
				.lobe(44, -36, 8, 12)
				.lobe(-37, -1, 12, 16)
				.island(0, -26, 3)
				.island(-47, -36, 2)
				.island(47, -36, 2)
				.island(-40, 1, 5);

		Hall aula = new Hall(0, -12).bays(3, 6, 3).depth(7).pillar(5).platform(2).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1).porch(1)
				.doors(Hall.Doors.OPEN).gableField(Palette.st(Palette.PILLAR)).interior(Interiors.Preset.MAIN_HALL);

		// ============================================================ T1 Peletakan Prasasti
		b.add(Component.of("t1_axis", 1).kind("axis").name("axis").group(Group.AXIS)
				.add(new TerrainZone(-7, -6, 7, 18, p).edge(TerrainZone.Edge.NONE))
				.add(new Paving(-2, -6, 2, 18, p, Paving.Pattern.AXIS).earth(false))
				.add(new Paving(-5, -5, 5, 3, p, Paving.Pattern.PLAZA).earth(false))
				.add(new Paving(-2, -2, 2, 2, p, Paving.Pattern.OCTAGON_INLAY).radius(2).earth(false))
				.add(new LanternPost(LanternPost.Style.STONE, p).at(-2, -2).at(2, -2).at(-2, 2).at(2, 2))
				.add(new Paving(-1, 20, 1, 44, court, Paving.Pattern.PATH))
				.add(new StairFlight(0, 19, Direction.SOUTH, p, court).width(5)));
		b.add(Component.of("t1_aula", 1).kind("hall").name("aula_pendiri").group(Group.MAIN)
				.add(new TerrainZone(-9, -17, 9, -6, p).edge(TerrainZone.Edge.NONE))
				.add(aula));
		b.add(Component.of("t1_dorm", 1).kind("hall").name("dorm").group(Group.SIDE)
				.add(new TerrainZone(32, 0, 52, 10, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(42, 5).front(Direction.SOUTH).bays(3, 4, 4, 4, 3).depth(7).pillar(4).platform(1).rank(Rank.A)
						.roof(RoofBuilder.Type.GABLE, 1).overhang(1).porch(0).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN))
				.add(Stamp.horseHeadGable(32, 5, Direction.EAST, 5, 4, 3))
				.add(Stamp.horseHeadGable(52, 5, Direction.EAST, 5, 4, 3)));
		b.add(Component.of("t1_gate", 1).kind("gate").name("inner_gate").group(Group.BOUNDARY)
				.add(Gates.innerGate(0, 18, p, 2, true))
				.add(new LanternPost(LanternPost.Style.RED_POST, p).pair(5, 16))
				.add(Marks.anchor("arrival", 0, 13, p)));
		b.add(Component.of("t1_well", 1).kind("well").name("water_vats").group(Group.SIDE)
				.add(Stamp.vat(-4, -7, p)).add(Stamp.vat(-5, -7, p)).add(Stamp.vat(-6, -7, p))
				.add(Stamp.vat(4, -7, p)).add(Stamp.vat(5, -7, p)).add(Stamp.vat(6, -7, p))
				.add(Marks.anchor("post_water", 3, -6, p)));
		b.add(Component.of("t1_hero", 1).kind("tree").name("hero_tree").group(Group.GARDEN)
				.add(new Paving(-12, 13, -8, 17, p, Paving.Pattern.MOSS))
				.add(new Tree(Tree.Kind.PINE, -10, 15, p).hero(true).force(true)));
		b.add(ornament(1).add(new Ornament(1, p).flowers(-9, -5, 9, -5))
				// Review views: the paifang, across the lake to the glazed pagoda by the grotto, the aerial.
				.add(Marks.camera(1, 0, 42, court, 0, -60, Lv.at(20))).add(Marks.camera(2, -20, -20, p, -40, 1, Lv.at(30)))
				.add(Marks.camera(3, 53, 43, Lv.at(60), 0, -15, p)));

		// ============================================================ T2 Api Dapur dan Kebun Obat
		b.add(Component.of("t2_kitchen", 2).kind("hall").name("kitchen_court").group(Group.SIDE)
				.add(new TerrainZone(36, -11, 52, -2, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(42, -7).front(Direction.SOUTH).bays(2, 2, 2).depth(5).pillar(4).platform(1).rank(Rank.A).roof(RoofBuilder.Type.GABLE, 1)
						.overhang(1).porch(0).interior(Interiors.Preset.KITCHEN).doors(Hall.Doors.NONE))
				.add(Stamp.horseHeadGable(38, -7, Direction.EAST, 5, 3, 2))
				.add(Stamp.horseHeadGable(46, -7, Direction.EAST, 5, 3, 2))
				.add(Stamp.coop(50, -6, Direction.WEST, p))
				.add(Stamp.vat(48, -3, p)));
		b.add(Component.of("t2_herbs", 2).kind("garden").name("herb_garden").group(Group.GARDEN)
				.add(new Terrace(18, -14, 32, -10, Lv.at(1), p).style(Terrace.Style.STONE).balustrade(false))
				.add(Grounds.herbBeds(19, -13, 31, -11, Lv.at(1)))
				.add(Grounds.herbBeds(18, -18, 32, -16, p))
				.add(new Tree(Tree.Kind.PLUM, 34, -15, p).force(true)));
		b.add(Component.of("t2_trees", 2).kind("trees").name("trees").group(Group.GARDEN)
				// The gate court keeps its plums (the paifang and the gate altars take the cells by the axis); every tree keeps
				// to its plot, clear of the galleries, the waterside pavilion and the scripture tower, and 5 inside the edge.
				.add(new Tree(Tree.Kind.PLUM, -22, 41, court).force(true).within(-25, 38, -19, 43))
				.add(new Tree(Tree.Kind.PLUM, 22, 41, court).force(true).within(19, 38, 25, 43))
				.add(new Tree(Tree.Kind.PLUM, 9, 14, p).force(true).within(6, 12, 11, 17))
				.add(new Tree(Tree.Kind.PINE, -21, 14, p).force(true))
				.add(new Tree(Tree.Kind.MAPLE, -21, 7, p).force(true).within(-24, 6, -20, 9))
				.add(new Tree(Tree.Kind.GINKGO, 17, -15, p).force(true).within(16, -18, 19, -13))
				.add(new Tree(Tree.Kind.PINE, 50, -1, p).force(true))
				.add(new Tree(Tree.Kind.PINE, 28, 16, p).force(true))
				.add(new Tree(Tree.Kind.PLUM, -28, 17, p).force(true))
				.add(new Tree(Tree.Kind.GINKGO, -30, 39, court).force(true))
				.add(new Tree(Tree.Kind.PINE, 49, 22, court).force(true))
				.add(new Tree(Tree.Kind.PLUM, -46, 17, p).force(true)));
		b.add(ornament(2).add(new Ornament(2, p).lanterns(LanternPost.Style.JADE, -7, -4, 7, -4, -13, 10, 11, 10)));

		// ============================================================ T3 Paviliun Kitab
		// A three-storey square scripture pavilion east of the plaza; its reading room keeps the lecterns near the west door,
		// so the "library" anchor stays within 16 of the stele.
		b.add(Component.of("t3_scripture", 3).kind("hall").name("scripture").group(Group.SIDE)
				.add(new TerrainZone(12, -7, 27, 9, p).edge(TerrainZone.Edge.SLOPE))
				// The pavilion finial (no end rod: that is for the glazed pagoda only, T8).
				.add(new Pagoda(19, 1, p).floors(3, new int[]{6, 5, 4}, new int[]{6, 5, 5}).overhangs(3, 2, 2).plinthMargin(1).entry(Direction.WEST)
						.spire(false))
				.add(Put.library(19, 1, Direction.WEST, p, 1, -5, 5, -5, 5, 56, 2, 2)));
		b.add(Component.of("t3_galleries", 3).kind("gallery").name("galleries").group(Group.SIDE)
				.add(new Corridor(-16, 3, -16, 17, p).openings(1, 2))
				.add(new Corridor(15, 11, 15, 17, p).wall(1)));
		b.add(Component.of("t3_plaza", 3).kind("paving").name("plaza").group(Group.AXIS)
				.add(new Paving(-13, -5, -3, 17, p, Paving.Pattern.PLAZA))
				.add(new Paving(3, -5, 12, 17, p, Paving.Pattern.PLAZA))
				.add(new Paving(-8, -6, -3, -6, p, Paving.Pattern.PLAZA))
				.add(new Paving(3, -6, 8, -6, p, Paving.Pattern.PLAZA)));
		b.add(ornament(3).add(new Ornament(3, p).flowers(-9, 17, 9, 17)));

		// ============================================================ T4 Tembok dan Air
		b.add(Component.of("t4_lake", 4).kind("lake").name("lake").group(Group.GARDEN)
				.add(lake)
				.add(new Paving(-14, -19, 14, -18, p, Paving.Pattern.PATH))
				.add(new Paving(-13, -17, -11, -7, p, Paving.Pattern.STEPPING))
				.add(new Paving(11, -17, 13, -7, p, Paving.Pattern.STEPPING))
				.add(new LanternPost(LanternPost.Style.STONE, p).pair(4, -19))
				.add(new Tree(Tree.Kind.WILLOW, -14, -19, p).force(true).within(-17, -22, -13, -18))
				.add(new Tree(Tree.Kind.WILLOW, 15, -19, p).force(true).within(14, -22, 18, -18))
				.add(new Tree(Tree.Kind.WILLOW, -23, 12, p).force(true).within(-26, 10, -20, 15))
				.add(new Tree(Tree.Kind.WILLOW, -36, -21, p).force(true))
				.add(new Tree(Tree.Kind.WILLOW, 36, -21, p).force(true))
				.add(Rock.cluster(-24, 0, p, 2)).add(Rock.cluster(24, -21, p, 2)).add(Rock.cluster(0, -26, p, 2)));
		b.add(Component.of("t4_waterside", 4).kind("bridge").name("bridge").group(Group.GARDEN)
				.add(new Pavilion(-23, 1, p).size(5).sides(4).waterside(true).rank(Rank.B).entry(Direction.EAST))
				.add(new Bridge(Bridge.Type.ZIGZAG, -26, 1, -35, 1, p))
				.add(SetPieces.stoneBoat(-35, -10, 7, p))
				.add(new Paving(-20, 1, -17, 1, p, Paving.Pattern.STEPPING))
				.add(new Paving(-17, 1, -17, 5, p, Paving.Pattern.STEPPING)));
		b.add(Component.of("t4_alchemy", 4).kind("hall").name("alchemy").group(Group.SIDE)
				.add(new TerrainZone(35, -22, 49, -14, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(42, -18).front(Direction.SOUTH).bays(3, 4, 3).depth(5).pillar(4).platform(1).rank(Rank.B)
						.roof(RoofBuilder.Type.XIESHAN, 1).overhang(1).porch(0).interior(Interiors.Preset.ALCHEMY, 2).doors(Hall.Doors.NONE))
				.add(Put.fittings(42, -18, Direction.SOUTH, p, 1, new int[][]{{-4, 0}, {4, 0}}, new int[0][])));
		b.add(Component.of("t4_shanmen", 4).kind("gate").name("shanmen").group(Group.BOUNDARY)
				.add(new Paving(-2, 36, 2, 44, court, Paving.Pattern.AXIS))
				.add(new Paving(-2, 19, 2, 27, court, Paving.Pattern.AXIS))
				.add(Gates.gateHall(0, 31, court, 5).stepsInset(0))
				// The stubs start at the gate's corner (core x ±9) and read as its wings.
				.add(new Wall(Wall.Style.RED_PALACE, court, 5).line(-21, 31, -10, 31).line(10, 31, 21, 31))
				.add(Rock.hill(-25, 31, court, 4)).add(Rock.hill(25, 31, court, 4))
				.add(Stamp.lion(-5, 37, Direction.SOUTH, court)).add(Stamp.lion(5, 37, Direction.SOUTH, court))
				.add(new LanternPost(LanternPost.Style.RED_POST, court).pair(4, 35)));
		b.add(Component.of("t4_boundary", 4).kind("boundary").name("boundary_stones").group(Group.BOUNDARY)
				.add(Stamp.boundaryStone(-32, 43, Direction.SOUTH, p))
				.add(Stamp.boundaryStone(32, 43, Direction.SOUTH, p))
				.add(Stamp.boundaryStone(-53, 26, Direction.WEST, p)).add(Stamp.boundaryStone(53, 26, Direction.EAST, p))
				.add(Stamp.boundaryStone(-53, -20, Direction.WEST, p)).add(Stamp.boundaryStone(53, 10, Direction.EAST, p))
				.add(Stamp.boundaryStone(53, -12, Direction.EAST, p)).add(Stamp.boundaryStone(-53, -52, Direction.WEST, p))
				.add(Stamp.boundaryStone(53, -52, Direction.EAST, p)));
		b.add(ornament(4).add(new Ornament(4, p).flowers(-12, 9, 11, 9)));

		// ============================================================ T5 Formasi Agung
		b.add(Component.of("t5_disc", 5).kind("formation").name("disc").group(Group.AXIS)
				.add(new FormationDisc(6, 8, p).clip(-13, -5, 13, 17)));
		b.add(Component.of("t5_altars", 5).kind("altar").name("gate_altars").group(Group.BOUNDARY)
				.add(new GateAltar(-15, 38, court)).add(new GateAltar(15, 38, court))
				.add(new GateAltar(-20, 23, court)).add(new GateAltar(20, 23, court)));
		b.add(Component.of("t5_watch", 5).kind("tower").name("watch_towers").group(Group.BOUNDARY)
				.add(new Massif("t5_sw", court).pad(-51, 35, -42, 41, knoll, Massif.Pad.NATURAL, true, 3).blob(-49, 42, 5, knoll.plus(3), 0.8).pines(2))
				.add(new Pavilion(-47, 38, knoll).size(5).sides(6).eaves(2).rank(Rank.B).entry(Direction.EAST))
				.add(new Massif("t5_se", court).pad(42, 35, 51, 41, knoll, Massif.Pad.NATURAL, true, 3).blob(49, 42, 5, knoll.plus(3), 0.8).pines(2))
				.add(new Pavilion(47, 38, knoll).size(5).sides(4).eaves(2).rank(Rank.B).entry(Direction.WEST))
				.add(new Pavilion(-47, -36, p).size(3).sides(0).rank(Rank.B).entry(Direction.EAST))
				.add(new Pavilion(47, -36, p).size(3).sides(8).rank(Rank.B).entry(Direction.WEST)));
		// Lanterns on free plaza cells: clear of the grotto hill (T9), the scripture tower's plinth and the galleries.
		b.add(ornament(5).add(new Ornament(5, p).lanterns(LanternPost.Style.STONE, -12, 4, 11, 4, -12, 12, 11, 12)));

		// ============================================================ T6 Aula Utama
		// 13 deep: width 29, ratio 2.2 (DESIGN 21.7).
		Hall mainHall = new Hall(0, -60).site(main).bays(3, 4, 4, 6, 4, 4, 3).depth(13).pillar(6).platform(2).rank(Rank.D)
				.roof(RoofBuilder.Type.HIP, 2).interior(Interiors.Preset.MAIN_HALL).gableField(Palette.st(Palette.PILLAR));
		b.add(Component.of("t6_bridge", 6).kind("bridge").name("bridge").group(Group.AXIS)
				.add(new Bridge(Bridge.Type.STRAIGHT_MARBLE, 0, -19, 0, -23, p).width(5))
				.add(new Bridge(Bridge.Type.STRAIGHT_MARBLE, 0, -29, 0, -33, p).width(5))
				.add(new Pavilion(0, -26, p).size(5).sides(8).eaves(2).rank(Rank.D).entry(Direction.SOUTH))
				.add(Put.air(-1, -28, 1, -28, p, 0, 1))
				.add(new LanternPost(LanternPost.Style.PALACE, p).at(-4, -18, Direction.EAST).at(4, -18, Direction.WEST)));
		b.add(Component.of("t6_main_hall", 6).kind("hall").name("main_hall").group(Group.MAIN)
				.add(new Massif("t6_peak", p).slope(1).pines(10).avoid(lake)
						.pad(-20, -72, 20, -48, main, Massif.Pad.STONE)
						.pad(-33, -72, -21, -48, main, Massif.Pad.STONE, true, -1)
						.pad(21, -72, 33, -48, main, Massif.Pad.STONE, true, -1)
						.pad(-14, -47, 14, -43, main.plus(-4), Massif.Pad.STONE)
						.pad(-10, -42, 10, -38, main.plus(-8), Massif.Pad.STONE)
						.stair(0, -34, Direction.NORTH, p, main.plus(-8), 7, Terrace.Style.MARBLE)
						.stair(0, -39, Direction.NORTH, main.plus(-8), main.plus(-4), 7, Terrace.Style.MARBLE)
						.stair(0, -44, Direction.NORTH, main.plus(-4), main, 7, Terrace.Style.MARBLE))
				.add(mainHall)
				.add(new LanternPost(LanternPost.Style.PALACE, main).at(-6, -49, Direction.EAST).at(6, -49, Direction.WEST)));
		b.add(Component.of("t6_paifang", 6).kind("paifang").name("paifang").group(Group.BOUNDARY)
				.add(new Paifang(0, 41, court).bays(3).roofs(7).material(Paifang.Material.WHITE_STONE)));
		b.add(Component.of("t6_dings", 6).kind("ding").name("dings").group(Group.GARDEN)
				.add(Stamp.ding(-9, -45, main.plus(-4), true)).add(Stamp.ding(9, -45, main.plus(-4), false)));
		b.add(ornament(6).add(new Ornament(6, main).banners(-22, -50, 22, -50, -30, -50, 30, -50)));

		// ============================================================ T7 Lapangan Latihan
		b.add(Component.of("t7_drill", 7).kind("drill").name("drill_ground").group(Group.SIDE)
				.add(Grounds.drillGround(26, 19, 44, 27, court, 12, true))
				.add(Stamp.weaponRack(31, 26, Direction.NORTH, court)).add(Stamp.weaponRack(39, 26, Direction.NORTH, court)));
		b.add(Component.of("t7_arena", 7).kind("arena").name("arena").group(Group.SIDE)
				.add(Grounds.arena(-42, 20, -27, 27, court, 2)));
		b.add(Component.of("t7_bell_drum", 7).kind("tower").name("bell_drum").group(Group.SIDE)
				.add(Gates.cornerTower(-10, 23, court, 2, 2, Direction.SOUTH))
				.add(Gates.cornerTower(10, 23, court, 2, 2, Direction.SOUTH))
				.add(Put.stack(-10, 23, court, 4, Phase.INTERIOR, Palette.bell(Direction.SOUTH, true)))
				.add(Put.stack(10, 23, court, 1, Phase.INTERIOR, Palette.st(Blocks.CHISELED_CINNABAR), Palette.barrel(Direction.UP)))
				.add(Stamp.flagPole(-4, 25, Direction.SOUTH, court, 6)).add(Stamp.flagPole(4, 25, Direction.SOUTH, court, 6)));
		b.add(Component.of("t7_inner_dorms", 7).kind("hall").name("inner_dorm").group(Group.SIDE)
				.add(new TerrainZone(32, 12, 52, 18, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(36, 15).front(Direction.SOUTH).bays(2, 2, 2).depth(5).pillar(4).platform(1).rank(Rank.A).roof(RoofBuilder.Type.XIESHAN, 1)
						.overhang(1).porch(0).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN))
				.add(new Hall(48, 15).front(Direction.SOUTH).bays(2, 2, 2).depth(5).pillar(4).platform(1).rank(Rank.A).roof(RoofBuilder.Type.XIESHAN, 1)
						.overhang(1).porch(0).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN)));
		// Covered walks round the Aula Pendiri down to the lakeside promenade.
		b.add(Component.of("t7_galleries", 7).kind("gallery").name("galleries").group(Group.SIDE)
				.add(new Corridor(-12, -6, -12, -17, p).wall(-1))
				.add(new Corridor(12, -6, 12, -17, p).wall(1)));
		b.add(ornament(7).add(new Ornament(7, court).banners(-45, 21, 45, 21).lanterns(LanternPost.Style.HANGING, -13, 29, 13, 29)));

		// ============================================================ T8 Pagoda dan Leluhur
		b.add(Component.of("t8_pagoda", 8).kind("pagoda").name("pagoda").group(Group.MAIN)
				.add(new Pagoda(-40, 1, p).floors(7, new int[]{4, 4, 3, 3, 2, 2, 2}, new int[]{5, 4, 4, 4, 3, 3, 3}).octagonal(true).glazed(true)
						.plinth(1).plinthMargin(1).entry(Direction.EAST)));
		b.add(Component.of("t8_inner_peak", 8).kind("peak").name("inner_peak").group(Group.SIDE)
				.add(new Massif("t8_inner", p).slope(1).pines(5).avoid(lake)
						.pad(-52, -71, -40, -57, inner, Massif.Pad.STONE)
						.blob(-48, -54, 6, inner.plus(-2), 0.8))
				.add(new Hall(-46, -65).site(inner).bays(3, 4, 3).depth(7).pillar(5).platform(1).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1)
						.porch(1).interior(Interiors.Preset.STORE).doors(Hall.Doors.OPEN).gableField(Palette.st(Palette.PLASTER)))
				.add(new Bridge(Bridge.Type.SKY, -34, -64, -39, -64, main).endLevel(inner).width(3)));
		b.add(Component.of("t8_elders_peak", 8).kind("hall").name("elders").group(Group.SIDE)
				.add(new Massif("t8_elders", p).slope(1).pines(5).avoid(lake)
						.pad(40, -71, 52, -57, elders, Massif.Pad.STONE)
						.blob(48, -54, 6, elders.plus(-2), 0.8))
				.add(new Hall(46, -65).site(elders).bays(3, 4, 3).depth(7).pillar(4).platform(1).rank(Rank.C).roof(RoofBuilder.Type.XIESHAN, 1)
						.porch(1).doors(Hall.Doors.OPEN).gableField(Palette.st(Palette.PLASTER)))
				.add(new Bridge(Bridge.Type.SKY, 34, -64, 39, -64, main).endLevel(elders).width(3)));
		b.add(Component.of("t8_ancestral", 8).kind("hall").name("ancestral_hall").group(Group.MAIN)
				.add(new Hall(-27, -61).site(main).front(Direction.SOUTH).bays(2, 2, 2).depth(7).pillar(5).platform(1).rank(Rank.C)
						.roof(RoofBuilder.Type.XIESHAN, 1).porch(1).interior(Interiors.Preset.ANCESTRAL).doors(Hall.Doors.OPEN)));
		b.add(Component.of("t8_meditation", 8).kind("pavilion").name("meditation_pavilion").group(Group.GARDEN)
				.add(new Pavilion(27, -60, main).size(5).sides(8).eaves(2).seat(3).rank(Rank.C).entry(Direction.SOUTH)));
		b.add(Component.of("t8_beasts", 8).kind("garden").name("beast_garden").group(Group.GARDEN)
				.add(Grounds.beastGarden(30, 31, 40, 41, court))
				.add(new BambooGrove(-53, 18, -49, 30, court).maxHeight(11)));
		b.add(Component.of("t8_test_path", 8).kind("garden").name("bamboo").group(Group.GARDEN)
				.add(new TestPath(-30, 38, Direction.WEST, court, knoll, 6)));
		b.add(ornament(8).add(new Ornament(8, main).lanterns(LanternPost.Style.JADE, -16, -50, 16, -50, -26, -50, 26, -50)));

		// ============================================================ T9 Menara Langit Membara
		b.add(Component.of("t9_grotto", 9).kind("tower").name("grotto_hill").group(Group.MAIN)
				// The covered walk (T7) keeps its strip at plaza level: a gorge through the rockery, never buried.
				.add(new Massif("t9_grotto", p).style(Massif.Style.ROCKERY).slope(1).pines(4).avoid(lake)
						.blob(-22, -11, 10, Lv.at(15), 0.9)
						.blob(-12, -5, 6, Lv.at(8), 0.5)
						.clear(-15, -18, -9, -4)
						.pad(-27, -10, -25, -7, Lv.at(9), Massif.Pad.NATURAL, true, 1)
						.cave(-14, -8, -8, -2, p, 4)
						.cave(-8, -6, -6, -4, p, 3))
				.add(new TowerShaft(-11, -5, p, 6, -6).roomSide(Direction.WEST))
				.add(new Waterfall("grotto", -25, -8, Lv.at(9), p, Direction.WEST))
				// The carved cave gate: chiseled tuff jambs and lintel, a soul lantern in the mouth.
				.add(Put.stack(-6, -7, p, 0, Phase.ORNAMENT, Palette.st(Blocks.CHISELED_TUFF_BRICKS), Palette.st(Blocks.CHISELED_TUFF_BRICKS),
						Palette.st(Blocks.CHISELED_TUFF_BRICKS), Palette.st(Blocks.CHISELED_TUFF_BRICKS)))
				.add(Put.stack(-6, -3, p, 0, Phase.ORNAMENT, Palette.st(Blocks.CHISELED_TUFF_BRICKS), Palette.st(Blocks.CHISELED_TUFF_BRICKS),
						Palette.st(Blocks.CHISELED_TUFF_BRICKS), Palette.st(Blocks.CHISELED_TUFF_BRICKS)))
				.add(Put.stack(-6, -6, p, 3, Phase.ORNAMENT, Palette.st(Blocks.CHISELED_TUFF_BRICKS)))
				.add(Put.stack(-6, -5, p, 3, Phase.ORNAMENT, Palette.st(Blocks.CHISELED_TUFF_BRICKS)))
				.add(Put.stack(-6, -4, p, 3, Phase.ORNAMENT, Palette.st(Blocks.CHISELED_TUFF_BRICKS)))
				.add(Put.stack(-6, -5, p, 2, Phase.ORNAMENT, Palette.lantern(Blocks.SOUL_LANTERN, true))));
		b.add(Component.of("t9_islands", 9).kind("island").name("island").group(Group.GARDEN)
				.add(new HangingIsland(-44, -38, 6, Lv.at(36)).turtle(true).pad(true).tether(-47, -36, Lv.at(-1)).tether(-41, -40, Lv.at(-1)))
				.add(new HangingIsland(44, -38, 5, Lv.at(42)).jadeChains(true).sporeBlossom(true).tether(47, -36, Lv.at(-1)))
				.add(new HangingIsland(-20, -26, 4, Lv.at(30)).tether(-20, -26, Lv.at(-1)))
				.add(new Pavilion(-44, -38, Lv.at(36)).size(3).sides(6).rank(Rank.B)));
		b.add(Component.of("t9_finale", 9).kind("finale").name("finale").group(Group.GARDEN)
				.add(Marks.petals(-12, 4, 12, 17, p, 10))
				.add(new LanternPost(LanternPost.Style.PALACE, p).at(-9, -7, Direction.EAST).at(9, -7, Direction.WEST))
				.add(new LanternPost(LanternPost.Style.PALACE, court).at(-4, 33, Direction.EAST).at(4, 33, Direction.WEST)));
		b.add(ornament(9).add(new Ornament(9, p).flowers(-13, -3, 13, -3, -13, 8, 13, 8)));
		return b.build();
	}

	private static Component.Builder ornament(int tier) {
		return Component.of("t" + tier + "_ornament", tier).kind("ornament").name("ornament_" + tier).group(Group.ORNAMENT);
	}

	private T7CaveHeaven() {
	}
}
