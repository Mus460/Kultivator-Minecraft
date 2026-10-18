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
import dev.kultivator.sect.build.blueprint.Pond;
import dev.kultivator.sect.build.blueprint.Rank;
import dev.kultivator.sect.build.blueprint.Rock;
import dev.kultivator.sect.build.blueprint.RoofBuilder;
import dev.kultivator.sect.build.blueprint.StairFlight;
import dev.kultivator.sect.build.blueprint.Stamp;
import dev.kultivator.sect.build.blueprint.TemplateSpec;
import dev.kultivator.sect.build.blueprint.Terrace;
import dev.kultivator.sect.build.blueprint.TerrainZone;
import dev.kultivator.sect.build.blueprint.TowerShaft;
import dev.kultivator.sect.build.blueprint.Tree;
import dev.kultivator.sect.build.blueprint.Wall;
import dev.kultivator.sect.build.blueprint.Waterfall;
import dev.kultivator.sect.build.blueprint.templates.t4t6.Flight;
import dev.kultivator.sect.build.blueprint.templates.t4t6.Islet;
import dev.kultivator.sect.build.blueprint.templates.t4t6.Unplant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * Template 5, Sekte Agung / Great Sect (DESIGN 21.1, 21.3, 21.4): 73×81, the orthodox great sect of the 21.4 plan. A
 * paved plaza with the Bagua disc (lodestone at (0, 6), ring r 5) between two long wings, the 2-storey Scripture Pavilion
 * (west, 40 shelves, its lectern within 16 of the stele) and the Outer dorm (east), joined to the inner gate by short
 * covered galleries; the Aula Pendiri (15×7, rank B) closes the plaza on the axis, with the screen-wall rock behind it at
 * the foot of the three-tier white marble terrace (+2 / +4, hall platform +5) that carries the 5-bay double-eave main hall
 * (core 23×11 with an outer colonnade) at T6, flanked by the drum (west) and bell (east) towers at T7. West the Hundred
 * Medicine Garden; NW the lotus pond with its island (the octagonal meditation pavilion with the tier-2 Qi seat at T8), the
 * moon bridge, the stepping stones and the waterfall off the terrace (T9), and on its +3 court behind the water the glazed
 * octagonal Pagoda Kaca Tujuh Harta, the one dominant vertical. East the walled kitchen court, the beast garden, the
 * alchemy hall on its fitted +2 terrace and the ancestral hall on the NE court above it, joined to the main terrace by
 * climbing galleries. Red palace walls with a 3-bay shanmen and lions (T4), two corner towers (T5), archery west and drill
 * ground east (T7), a 3-bay stone paifang on the approach (T6), and the Blazing Sky Tower opening in the west bay of the
 * Aula Pendiri, which has become the Kantor Misi (T9).
 * <p>
 * Sources (DESIGN 21.1): <i>Return of the Mount Hua Sect</i>: the Nine Great Sects and One Gang, and the Five Great
 * Families (Mount Hua was once a member and was replaced by Hainan); <i>A Record of a Mortal's Journey</i>: Yue State's
 * Seven Great Sects. Hero plum: <i>Return of the Mount Hua Sect</i>'s plum blossom. Rank by height: <i>I Shall Seal the
 * Heavens</i> ch. 2-3. Pagoda name after <i>Soul Land</i>'s Seven Treasure Glazed Tile pagoda (a martial soul there; the
 * storey count is design). Beast garden: <i>A Record of a Mortal's Journey</i>, Spirit Beast Mountain (name only). Kantor
 * Misi: <i>A Will Eternal</i> ch. 9 and 27 (the Missions Office; the swap itself is game design). Duty stations, library,
 * drill ground and tower as in DESIGN 21.2. The white marble terrace, galleries and gardens are art direction (the
 * Forbidden City, Jiangnan gardens), not canon.
 */
public final class T5Great {

	public static TemplateSpec build() {
		Lv p = Lv.PLAZA;
		Lv east = Lv.zone("east");
		Lv ne = Lv.zone("ne");
		Lv approach = Lv.zone("approach");
		TemplateSpec.Builder b = TemplateSpec.builder(5).footprint(-36, 36, -50, 30).envelope(48, 22).relief(28).earthwork(29565)
				.disc(6, 5).territory(58).towerRooms(5).qiTier(2).gateAltars(0).planVersion(1)
				.zone("plaza", -35, -18, 35, 23, 0)
				.zone("garden", -35, -34, -20, -19, 0)
				.zone("main", -19, -49, 19, -19, 2)
				.zone("hall", -17, -44, 17, -22, 4)
				.zone("pagoda", -35, -49, -21, -35, 3)
				.zone("east", 20, -33, 35, -19, 2, 1, 4)
				.zone("ne", 20, -49, 35, -34, 3, 2, 5, "east", 1)
				.zone("approach", -36, 24, 36, 30, -1, -3, 0);

		// The Aula Pendiri: core 15×7 (3 bays [4,6,4]), platform x -8..8 z -15..-7, eaves x -9..9 z -16..-6.
		Hall aula = new Hall(0, -11).bays(4, 6, 4).depth(7).pillar(5).platform(1).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1)
				.doors(Hall.Doors.OPEN).interior(Interiors.Preset.MAIN_HALL).plaque(true).gableField(Palette.st(Palette.PILLAR));

		// ============================================================ T1 Peletakan Prasasti
		b.add(Component.of("t1_axis", 1).kind("axis").name("axis").group(Group.AXIS)
				.add(new TerrainZone(-11, -6, 11, 22, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Paving(-2, -6, 2, 22, p, Paving.Pattern.AXIS).earth(false))
				.add(new Paving(-5, -6, 5, 2, p, Paving.Pattern.PLAZA).earth(false))
				.add(new Paving(-2, -2, 2, 2, p, Paving.Pattern.OCTAGON_INLAY).radius(2).earth(false))
				.add(new LanternPost(LanternPost.Style.STONE, p).at(-2, -2).at(2, -2).at(-2, 2).at(2, 2))
				.add(new LanternPost(LanternPost.Style.RED_POST, p).pair(4, 16).pair(7, -5))
				.add(new Paving(-11, -18, -3, -17, p, Paving.Pattern.STEPPING))
				.add(new Paving(3, -18, 11, -17, p, Paving.Pattern.STEPPING))
				.add(new Paving(-11, -16, -10, -7, p, Paving.Pattern.STEPPING))
				.add(new Paving(10, -16, 11, -7, p, Paving.Pattern.STEPPING))
				.add(Grounds.screenWall(0, -18, p, 2)));
		b.add(Component.of("t1_aula", 1).kind("hall").name("aula_pendiri").group(Group.MAIN)
				.add(new TerrainZone(-10, -17, 10, -6, p).edge(TerrainZone.Edge.SLOPE))
				.add(aula));
		// The Outer dorm on its contract plot (21.4: x 17..27, z -8..2), outside the T3 east gallery.
		b.add(Component.of("t1_dorm", 1).kind("hall").name("dorm").group(Group.SIDE)
				.add(new TerrainZone(16, -11, 28, 5, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(22, -3).front(Direction.WEST).bays(3, 4, 3).depth(7).pillar(4).platform(1).rank(Rank.A).roof(RoofBuilder.Type.XIESHAN, 1)
						.interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN)));
		b.add(Component.of("t1_well", 1).kind("well").name("well").group(Group.SIDE)
				.add(new Pavilion(7, -2, p).size(3).rank(Rank.B).entry(Direction.SOUTH))
				.add(Stamp.vat(7, -2, p)).add(Stamp.vat(5, -2, p)).add(Stamp.vat(9, -2, p)).add(Stamp.vat(7, -4, p))
				.add(Marks.anchor("post_water", 7, -1, p)));
		b.add(Component.of("t1_gate", 1).kind("gate").name("inner_gate").group(Group.BOUNDARY)
				.add(Gates.innerGate(0, 14, p, 2, true))
				.add(Marks.anchor("arrival", 0, 10, p)));
		// The hero plum just outside the west gallery (21.4 draws it at (-15, 7); the gallery is 7 wide with its eaves), in
		// the Scripture Pavilion's court, its plot clear of the gallery, the pavilion and the T7 archery ground.
		b.add(Component.of("t1_hero", 1).kind("tree").name("hero_tree").group(Group.GARDEN)
				.add(new Tree(Tree.Kind.PLUM, -19, 7, p).hero(true).force(true).within(-21, 5, -17, 8)));
		b.add(ornament(1).add(new Ornament(1, p).flowers(-10, 12, 10, 12))
				// Review views: the paifang, across the lotus pond to the glazed pagoda, the aerial.
				.add(Marks.camera(1, 0, 28, approach, 0, -33, Lv.at(12))).add(Marks.camera(2, -28, -14, p, -28, -42, Lv.at(25)))
				.add(Marks.camera(3, 35, 29, Lv.at(46), 0, -12, p)));

		// ============================================================ T2 Api Dapur dan Kebun Obat
		b.add(Component.of("t2_kitchen", 2).kind("kitchen").name("kitchen_court").group(Group.SIDE)
				.add(new TerrainZone(30, -13, 36, 7, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Wall(Wall.Style.WHITE_GARDEN, p, 4).line(30, -13, 30, 7).line(31, -13, 36, -13).line(31, 7, 36, 7).line(36, -12, 36, 6)
						.gap(30, 5, 1).drips(1))
				.add(new Hall(33, -9).front(Direction.SOUTH).bays(1, 2, 1).depth(3).pillar(4).platform(0).rank(Rank.A).roof(RoofBuilder.Type.XIESHAN, 1)
						.overhang(1).interior(Interiors.Preset.KITCHEN).doors(Hall.Doors.NONE))
				.add(Pond.basin(33, 1, 33, 1, p).plants(false))
				.add(Stamp.of("well_frame").layer("W.W").layer("W.W").layer("sSs")
						.legend('W', Palette.st(Blocks.ANDESITE_WALL))
						.legend('S', Palette.st(Blocks.DEEPSLATE_TILES))
						.legend('s', Palette.slab(Blocks.DEEPSLATE_TILE_SLAB, false))
						.phase(Phase.FRAME)
						.at(33, 1, Direction.SOUTH, p))
				.add(Stamp.coop(33, 4, Direction.WEST, p))
				.add(Marks.anchor("post_farm", 31, 4, p))
				.add(new Paving(31, -6, 35, -2, p, Paving.Pattern.PATH).earth(false)));
		// The stepped Hundred Medicine Garden keeps to its contract strip (21.4: x -35..-30); the trees stand where the
		// contract map draws them, each kept to its plot between the halls, galleries and the outer court.
		b.add(Component.of("t2_garden", 2).kind("garden").name("herb_garden").group(Group.GARDEN)
				.add(new TerrainZone(-35, -13, -30, 7, p).edge(TerrainZone.Edge.SLOPE).surface(TerrainZone.Surface.MOSS))
				.add(Grounds.herbBeds(-35, -13, -31, 7, p))
				.add(new Paving(-30, -13, -30, 7, p, Paving.Pattern.STEPPING).earth(false))
				.add(new Tree(Tree.Kind.GINKGO, -33, -15, p).force(true).within(-35, -17, -31, -14))
				.add(new Tree(Tree.Kind.PLUM, -25, 7, p).force(true).within(-28, 5, -23, 8))
				.add(new Tree(Tree.Kind.MAPLE, 20, 6, p).force(true).within(18, 5, 22, 8))
				.add(new Tree(Tree.Kind.GINKGO, 25, 6, p).force(true).within(23, 5, 28, 8)));
		b.add(ornament(2).add(new Ornament(2, p).lanterns(LanternPost.Style.JADE, -30, 8, -26, -14)));

		// ============================================================ T3 Paviliun Kitab
		// The Scripture Pavilion on its contract plot (21.4: x -29..-17, z -8..2) and the covered galleries framing the
		// plaza on both sides (x ±13, z -6..12, open at z 3 for the cross path).
		b.add(Component.of("t3_scripture", 3).kind("hall").name("scripture").group(Group.SIDE)
				.add(new TerrainZone(-29, -11, -17, 5, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(-23, -3).front(Direction.EAST).bays(3, 4, 3).depth(7).pillar(6).overhang(2).storeys(2, 4).platform(1).rank(Rank.B)
						.roof(RoofBuilder.Type.XIESHAN, 1).interior(Interiors.Preset.LIBRARY, 40).doors(Hall.Doors.NONE))
				.add(Stamp.of("lectern").layer("L").legend('L', Palette.facing(Blocks.LECTERN, Direction.EAST)).phase(Phase.INTERIOR)
						.at(-22, 0, Direction.SOUTH, Lv.at(1))));
		b.add(Component.of("t3_galleries", 3).kind("gallery").name("galleries").group(Group.SIDE)
				// Open toward the plaza, the plaster wall with leak windows on the outer side; they end before the outer
				// court, where the T7 bell and drum towers stand.
				.add(new Corridor(-13, -6, -13, 8, p).wall(1).openings(9))
				.add(new Corridor(13, -6, 13, 8, p).wall(-1).openings(9)));
		b.add(Component.of("t3_plaza", 3).kind("paving").name("plaza").group(Group.AXIS)
				.add(new Paving(-11, -6, -6, 13, p, Paving.Pattern.PLAZA))
				.add(new Paving(6, -6, 11, 13, p, Paving.Pattern.PLAZA))
				.add(new Paving(-5, 3, -3, 13, p, Paving.Pattern.PLAZA))
				.add(new Paving(3, 3, 5, 13, p, Paving.Pattern.PLAZA)));
		b.add(ornament(3).add(new Ornament(3, p).flowers(-10, 8, 10, 8)));

		// ============================================================ T4 Tembok dan Air
		b.add(Component.of("t4_walls", 4).kind("wall").name("wall").group(Group.BOUNDARY)
				.add(new TerrainZone(-36, 24, 36, 30, approach).edge(TerrainZone.Edge.SLOPE))
				// The front wall meets the shanmen's corner pillars (x ±6): no gap beside the gate.
				.add(new Wall(Wall.Style.RED_PALACE, p, 6).line(-36, 20, -7, 20).line(7, 20, 36, 20).line(-36, -34, -36, 19).line(36, -18, 36, 19)
						.drips(1))
				.add(new Wall(Wall.Style.RED_PALACE, Lv.at(3), 6).line(-36, -50, -36, -35).line(-35, -50, -20, -50).drips(1))
				.add(new Wall(Wall.Style.RED_PALACE, Lv.at(2), 6).line(-19, -50, 19, -50).drips(1))
				.add(new Wall(Wall.Style.RED_PALACE, ne, 6).line(20, -50, 36, -50).line(36, -49, 36, -34).drips(1))
				.add(new Wall(Wall.Style.RED_PALACE, east, 6).line(36, -33, 36, -19).drips(1))
				.add(new Hall(0, 20).bays(3, 6, 3).depth(3).pillar(5).platform(1).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1).overhang(2)
						.passage(true).porch(0).gableField(Palette.st(Palette.PILLAR)))
				.add(new StairFlight(0, 24, Direction.SOUTH, p, approach).width(5))
				.add(new Paving(-2, 24, 2, 30, approach, Paving.Pattern.AXIS).earth(false))
				.add(Stamp.lion(-5, 25, Direction.SOUTH, approach)).add(Stamp.lion(5, 25, Direction.SOUTH, approach))
				.add(new LanternPost(LanternPost.Style.STONE, approach).pair(4, 27)));
		b.add(Component.of("t4_alchemy", 4).kind("hall").name("alchemy").group(Group.SIDE)
				.add(new Terrace(20, -33, 35, -19, east, p).style(Terrace.Style.STONE).deck(false).opening(32, -19, 1))
				.add(new Paving(21, -32, 34, -20, east, Paving.Pattern.LAWN).earth(false))
				.add(new Flight(32, -18, Direction.NORTH, p, east).width(3))
				.add(new Hall(28, -25).site(east).front(Direction.SOUTH).bays(3, 4, 3).depth(5).pillar(5).platform(1).overhang(1).rank(Rank.B)
						.roof(RoofBuilder.Type.XIESHAN, 1).interior(Interiors.Preset.ALCHEMY, 1).doors(Hall.Doors.NONE))
				.add(Stamp.of("brewing").layer("B").legend('B', Palette.st(Blocks.BREWING_STAND)).phase(Phase.INTERIOR)
						.at(32, -26, Direction.SOUTH, east.plus(1)))
				.add(new Paving(26, -21, 30, -20, east, Paving.Pattern.PLAZA).earth(false))
				.add(new Tree(Tree.Kind.PINE, 33, -31, east).force(true))
				.add(new Tree(Tree.Kind.MAPLE, 22, -31, east).force(true)));
		b.add(Component.of("t4_lotus", 4).kind("garden").name("lotus_pond").group(Group.GARDEN)
				.add(Pond.pond(-27, -24, 7, 7, p))
				.add(new Islet(-31, -28, -23, -20, p).landing(-27, -20).landing(-27, -28))
				.add(new Bridge(Bridge.Type.MOON_ARCH, -27, -15, -27, -20, p).width(3))
				.add(new Tree(Tree.Kind.WILLOW, -24, -15, p).force(true).within(-25, -17, -21, -12))
				// The plum stands on the bank (the islet keeps a rock until the T8 island pavilion takes it).
				.add(new Tree(Tree.Kind.PLUM, -33, -19, p).force(true).within(-35, -21, -31, -17))
				.add(Rock.cluster(-30, -27, p, 1))
				.add(Rock.cluster(-21, -18, p, 1))
				.add(new LanternPost(LanternPost.Style.JADE, p).at(-29, -15).at(-25, -15)));
		b.add(ornament(4).add(new Ornament(4, p).flowers(-13, -17, 13, -17)));

		// ============================================================ T5 Formasi Agung
		b.add(Component.of("t5_disc", 5).kind("formation").name("disc").group(Group.AXIS)
				.add(new FormationDisc(6, 5, p).clip(-11, -2, 11, 13)));
		b.add(Component.of("t5_towers", 5).kind("tower").name("corner_towers").group(Group.BOUNDARY)
				.add(new Terrace(-35, 11, -27, 19, Lv.at(3), p).style(Terrace.Style.STONE).opening(-31, 11, 1))
				.add(new StairFlight(-31, 8, Direction.SOUTH, p, Lv.at(3)).width(3))
				.add(Gates.cornerTower(-31, 15, Lv.at(3), 2, 2, Direction.NORTH).overhang(2))
				.add(new Terrace(27, 11, 35, 19, Lv.at(3), p).style(Terrace.Style.STONE).opening(31, 11, 1))
				.add(new StairFlight(31, 8, Direction.SOUTH, p, Lv.at(3)).width(3))
				.add(Gates.cornerTower(31, 15, Lv.at(3), 2, 2, Direction.NORTH).overhang(2)));
		b.add(ornament(5).add(new Ornament(5, p).lanterns(LanternPost.Style.STONE, -8, -5, 10, 3, -10, 3)));

		// ============================================================ T6 Aula Utama
		b.add(Component.of("t6_paifang", 6).kind("paifang").name("paifang").group(Group.AXIS)
				.add(new Paifang(0, 29, approach).bays(3).roofs(3).material(Paifang.Material.WHITE_STONE)));
		b.add(Component.of("t6_main_hall", 6).kind("hall").name("main_hall").group(Group.MAIN)
				.add(new Terrace(-19, -49, 19, -19, Lv.at(2), p).style(Terrace.Style.MARBLE).opening(-11, -19, 1).opening(11, -19, 1))
				.add(new Unplant(-20, -26, -20, -22, p))
				.add(new StairFlight(-11, -17, Direction.NORTH, p, Lv.at(2)).width(3).style(Terrace.Style.MARBLE))
				.add(new StairFlight(11, -17, Direction.NORTH, p, Lv.at(2)).width(3).style(Terrace.Style.MARBLE))
				.add(new Terrace(-17, -44, 17, -23, Lv.at(4), Lv.at(2)).style(Terrace.Style.MARBLE).opening(0, -23, 2))
				.add(new StairFlight(0, -21, Direction.NORTH, Lv.at(2), Lv.at(4)).width(5).style(Terrace.Style.MARBLE).ramp(1))
				.add(new Hall(0, -33).site(Lv.at(4)).bays(4, 4, 6, 4, 4).depth(11).pillar(6).platform(1).rank(Rank.D).roof(RoofBuilder.Type.XIESHAN, 2)
						.interior(Interiors.Preset.MAIN_HALL).gableField(Palette.st(Palette.PILLAR)))
				.add(new LanternPost(LanternPost.Style.PAGODA, Lv.at(2)).pair(4, -20))
				.add(new LanternPost(LanternPost.Style.PALACE, Lv.at(4)).at(-16, -24, Direction.EAST).at(16, -24, Direction.WEST))
				// The bronze dings in the court before the hall stair (one with signal smoke), and lawn where the T8 bamboo grows.
				.add(Stamp.ding(-7, -21, Lv.at(2), true)).add(Stamp.ding(7, -21, Lv.at(2), false))
				.add(new Paving(-17, -48, 17, -46, Lv.at(2), Paving.Pattern.LAWN).earth(false)));
		b.add(ornament(6).add(new Ornament(6, p).banners(-4, -5, 4, -5, -4, 13, 4, 13))
				.add(Marks.anchor("treasury", 5, -10, Lv.at(1))));

		// ============================================================ T7 Lapangan Latihan
		b.add(Component.of("t7_drill", 7).kind("drill").name("drill_ground").group(Group.SIDE)
				.add(Grounds.archery(-25, 9, -19, 18, p, 4))
				.add(Grounds.drillGround(19, 9, 25, 18, p, 4, true))
				.add(Stamp.weaponRack(23, 19, Direction.NORTH, p)).add(Stamp.weaponRack(-23, 19, Direction.NORTH, p))
				.add(Stamp.flagPole(-17, 9, Direction.SOUTH, p, 5)).add(Stamp.flagPole(17, 9, Direction.SOUTH, p, 5)));
		// Bell (west) and drum (east) towers in the outer court (21.4: x ±12..16, z 14..18), clear of the wall line.
		b.add(Component.of("t7_bell_drum", 7).kind("tower").name("bell_drum").group(Group.SIDE)
				.add(new Pavilion(-14, 14, p).size(5).eaves(2).rank(Rank.B).entry(Direction.EAST))
				.add(Stamp.of("bell").layer("B").legend('B', Palette.bell(Direction.EAST, false)).phase(Phase.INTERIOR).at(-14, 14, Direction.SOUTH, p))
				.add(new Pavilion(14, 14, p).size(5).eaves(2).rank(Rank.B).entry(Direction.WEST))
				.add(Stamp.drum(14, 14, p))
				.add(new LanternPost(LanternPost.Style.STONE, p).pair(8, 4).pair(8, 8)));

		b.add(Component.of("t7_galleries", 7).kind("gallery").name("gallery").group(Group.SIDE)
				.add(new Corridor(18, -24, 21, -24, Lv.at(2)).to(east)));
		b.add(Component.of("t7_rocks", 7).kind("rocks").name("rocks").group(Group.GARDEN)
				.add(Rock.scholar(-9, -15, p, 4)).add(Rock.scholar(9, -15, p, 3))
				.add(Rock.cluster(28, 8, p, 1))
				.add(new LanternPost(LanternPost.Style.PALACE, p).at(-6, 16, Direction.WEST).at(6, 16, Direction.EAST))
				.add(new LanternPost(LanternPost.Style.STONE, approach).pair(9, 27)));
		b.add(ornament(7).add(new Ornament(7, p).lanterns(LanternPost.Style.HANGING, -9, -6, 9, -6)));

		// ============================================================ T8 Pagoda dan Leluhur
		b.add(Component.of("t8_pagoda", 8).kind("pagoda").name("pagoda").group(Group.MAIN)
				.add(new Terrace(-35, -49, -21, -35, Lv.at(3), p).style(Terrace.Style.STONE).opening(-27, -35, 1))
				.add(new StairFlight(-27, -32, Direction.NORTH, p, Lv.at(3)).width(3))
				.add(new Pagoda(-28, -42, Lv.at(3)).floors(7, new int[]{4, 4, 3, 3, 2, 2, 2}, new int[]{5, 4, 4, 4, 3, 3, 3})
						.overhangs(3, 2, 3, 2, 3, 2, 2).octagonal(true).glazed(true))
				.add(new Bridge(Bridge.Type.STEPPING, -27, -29, -27, -31, p))
				.add(new Tree(Tree.Kind.PINE, -33, -37, Lv.at(3)).force(true)));
		b.add(Component.of("t8_ancestral", 8).kind("hall").name("ancestral_hall").group(Group.SIDE)
				.add(new Terrace(20, -49, 35, -34, ne, east).style(Terrace.Style.STONE).opening(22, -34, 1).opening(20, -40, 1))
				.add(new Flight(22, -33, Direction.NORTH, east, ne).width(3))
				.add(new Hall(30, -41).site(ne).front(Direction.WEST).bays(3, 6, 3).depth(7).pillar(5).platform(1).rank(Rank.C)
						.roof(RoofBuilder.Type.XIESHAN, 1).interior(Interiors.Preset.ANCESTRAL).doors(Hall.Doors.OPEN))
				.add(new Corridor(18, -40, 21, -40, Lv.at(2)).to(ne)));
		b.add(Component.of("t8_meditation", 8).kind("pavilion").name("meditation_pavilion").group(Group.GARDEN)
				.add(new Pavilion(-27, -24, p).size(5).sides(8).seat(2).rank(Rank.C).entry(Direction.SOUTH)));
		b.add(Component.of("t8_beasts", 8).kind("garden").name("beast_garden").group(Group.GARDEN)
				.add(Grounds.beastGarden(22, -17, 29, -11, p)));
		b.add(Component.of("t8_bamboo", 8).kind("bamboo").name("bamboo").group(Group.GARDEN)
				.add(new BambooGrove(-17, -48, 17, -46, Lv.at(2)).maxHeight(10))
				.add(new BambooGrove(-35, -33, -33, -31, p).maxHeight(8)));
		b.add(ornament(8).add(new Ornament(8, p).lanterns(LanternPost.Style.JADE, -20, -16, -34, -18)));

		// ============================================================ T9 Menara Langit Membara
		// The Blazing Sky Tower opens visibly beside the forecourt (21.4 draws it at x -11..-7, z -3..1; it moves 2 east to
		// clear the 7-wide gallery): a ting over the well, the five rooms straight below.
		b.add(Component.of("t9_tower", 9).kind("tower").name("blazing_tower").group(Group.MAIN)
				.add(new Pavilion(-7, -1, p).size(3).rank(Rank.B).entry(Direction.EAST))
				.add(new TowerShaft(-7, -1, p, 5, -6).roomSide(Direction.WEST))
				.add(new LanternPost(LanternPost.Style.STONE, p).at(-4, -4).at(4, -4)));
		b.add(Component.of("t9_waterfall", 9).kind("waterfall").name("waterfall").group(Group.GARDEN)
				.add(new Waterfall("pond", -21, -24, Lv.at(2), p, Direction.WEST)));
		b.add(Component.of("t9_finale", 9).kind("finale").name("finale").group(Group.GARDEN)
				.add(aula.gilding())
				// Approach groves kept well inside the footprint (the edge is z 30): full crowns, never cut flat.
				.add(new Tree(Tree.Kind.PINE, -14, 25, approach).force(true)).add(new Tree(Tree.Kind.PINE, 14, 25, approach).force(true))
				.add(new Tree(Tree.Kind.PLUM, -25, 25, approach).force(true)).add(new Tree(Tree.Kind.PLUM, 25, 25, approach).force(true))
				.add(Marks.petals(-11, 3, 11, 11, p, 10))
				.add(new LanternPost(LanternPost.Style.PALACE, Lv.at(2)).at(-13, -21, Direction.EAST).at(13, -21, Direction.WEST)));
		b.add(ornament(9).add(new Ornament(9, p).flowers(-10, -4, 10, -4)));
		return b.build();
	}

	private static Component.Builder ornament(int tier) {
		return Component.of("t" + tier + "_ornament", tier).kind("ornament").name("ornament_" + tier).group(Group.ORNAMENT);
	}

	private T5Great() {
	}
}
