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
import dev.kultivator.sect.build.blueprint.templates.t4t6.BoundaryStones;
import dev.kultivator.sect.build.blueprint.templates.t4t6.Channel;
import dev.kultivator.sect.build.blueprint.templates.t4t6.CliffDeck;
import dev.kultivator.sect.build.blueprint.templates.t4t6.GateAltar;
import dev.kultivator.sect.build.blueprint.templates.t4t6.Islet;
import dev.kultivator.sect.build.blueprint.templates.t4t6.Massif;
import dev.kultivator.sect.build.blueprint.templates.t4t6.Piers;
import dev.kultivator.sect.build.blueprint.templates.t4t6.Unplant;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

/**
 * Template 6, Tanah Suci / Holy Land (DESIGN 21.1, 21.3, 21.4): 89×97, twin peaks with a gallery bridge over a gorge, no
 * perimeter wall. The template-5 scheme grown wide: a 5-roof white stone paifang and two gate formation altars on the
 * approach, a 5-bay shanmen with short red wall stubs ending in rockeries and two watch pavilions, boundary stones along
 * the open rim; an outer court with the leitai arena (west) and the drill ground (east, 10 targets); the plaza with the
 * Bagua disc (lodestone at (0, 6), ring r 7) between the 2-storey Scripture Pavilion (west, 48 shelves) and the Outer dorm
 * (east); the Aula Pendiri on the axis with the screen-wall rock behind it at the foot of the main peak's white marble
 * terraces (+3 / +6 / +9) that carry the 7-bay double-eave wudian main hall at T6, flanked by the drum and bell towers. The
 * dominant vertical is the diagonal pair of the main hall and the 2-storey Inner Disciples' Hall (with the Inner dorm
 * before it) on the Inner peak, a +12 plateau in the NW with a sheer cliff on the gorge and a wooded slope down to the lake,
 * walled on its flanks and reached across the gorge stream by a climbing covered gallery bridge (T8), with a waterfall
 * spouting from its cliff into a plunge pool (T9); below it the lotus lake fed by the gorge stream, with the nine-turn
 * bridge and the island meditation pavilion (Qi seat tier 2). East the alchemy hall on its +3 terrace (two furnaces), the
 * glazed Pagoda Kaca Tujuh Harta and the ancestral hall in the low garden, and at T9 the cloud-sea terrace on its own
 * peak in the NE corner, a two-eave pavilion on a marble deck, whose east cliff carries the hexagonal cliff pavilion on a
 * cantilevered deck over the valley beyond the rim, joined to the main terrace by an arched sky bridge. The Blazing Sky
 * Tower (6 rooms) opens under a round two-eave kiosk east of the stele.
 * <p>
 * Sources (DESIGN 21.1): <i>Shrouding the Heavens</i>: Holy Lands such as the Yaoguang Holy Land; <i>Against the Gods</i>
 * ch. 696: the Four Great Sacred Grounds, guardians of the Profound Sky Continent. Inner Sect on an upper peak behind its
 * own defences: <i>I Shall Seal the Heavens</i> ch. 2-3 (the Reliance Sect). Pagoda name after <i>Soul Land</i>'s Seven
 * Treasure Glazed Tile pagoda (a martial soul there; the storey count is design). Beast garden: <i>A Record of a Mortal's
 * Journey</i>, Spirit Beast Mountain (name only). Kantor Misi: <i>A Will Eternal</i> ch. 9 and 27. Hero pine, duties,
 * library, drill ground and tower as in DESIGN 21.2. The cliff pavilion (after the Hanging Temple of Mount Heng), the
 * marble terraces and the gardens are art direction, not canon.
 */
public final class T6HolyLand {

	public static TemplateSpec build() {
		Lv p = Lv.PLAZA;
		Lv east = Lv.at(3);
		Lv inner = Lv.at(12);
		Lv cloud = Lv.at(14);
		TemplateSpec.Builder b = TemplateSpec.builder(6).footprint(-44, 44, -60, 36).envelope(56, 26).relief(36).earthwork(43165)
				.disc(6, 7).territory(68).towerRooms(6).qiTier(2).gateAltars(2).planVersion(1)
				.zone("plaza", -43, -22, 43, 36, 0)
				.zone("lake", -44, -34, -24, -15, 0)
				.zone("main", -23, -60, 23, -23, 6)
				.zone("east", 24, -14, 43, 6, 3)
				.zone("garden", 24, -41, 43, -15, 0)
				.zone("inner", -43, -58, -29, -42, 12)
				.zone("cloud", 30, -57, 38, -49, 14);

		// The Aula Pendiri: core 15×7 (3 bays [4,6,4]), eaves x -9..9 z -17..-7.
		Hall aula = new Hall(0, -12).bays(4, 6, 4).depth(7).pillar(5).platform(1).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1)
				.doors(Hall.Doors.OPEN).interior(Interiors.Preset.MAIN_HALL).plaque(true).gableField(Palette.st(Palette.PILLAR));
		// The Inner peak (T8): a plateau at +12 with a sheer cliff on the gorge (east), craggy faces at the rim (west, north) and
		// a wooded slope down to the lake (south). The cloud peak (T9): a plateau at +14 in the NE corner, its east face a cliff
		// over the valley beyond the rim. Both keep natural ground where it is higher.
		Massif innerPeak = new Massif(-43, -58, -29, -42, inner, p).spread(1, 0, 2, 7).pines(5);
		Massif cloudPeak = new Massif(30, -57, 38, -49, cloud, p).spread(5, 0, 3, 6).pines(3);
		Pond lake = Pond.pond(-34, -24, 9, 8, p);
		// The lecture floor of the Inner Disciples' Hall: the master's seat between two lanterns, three cushions before it.
		Stamp lecture = Stamp.of("lecture_floor").layer("L.S.L", "C.C.C")
				.legend('L', Palette.lantern(Blocks.LANTERN, false))
				.legend('S', Palette.stair(Blocks.SMOOTH_QUARTZ_STAIRS, Direction.NORTH, false))
				.legend('C', Palette.st(Palette.carpet(DyeColor.RED)))
				.phase(Phase.INTERIOR);

		// ============================================================ T1 Peletakan Prasasti
		b.add(Component.of("t1_axis", 1).kind("axis").name("axis").group(Group.AXIS)
				.add(new TerrainZone(-11, -6, 11, 23, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Paving(-2, -6, 2, 23, p, Paving.Pattern.AXIS).earth(false))
				.add(new Paving(-5, -6, 5, 2, p, Paving.Pattern.PLAZA).earth(false))
				.add(new Paving(-2, -2, 2, 2, p, Paving.Pattern.OCTAGON_INLAY).radius(2).earth(false))
				.add(new LanternPost(LanternPost.Style.STONE, p).at(-2, -2).at(2, -2).at(-2, 2).at(2, 2))
				.add(new LanternPost(LanternPost.Style.RED_POST, p).pair(4, 21).pair(7, -6))
				.add(new Paving(-12, -21, -3, -20, p, Paving.Pattern.STEPPING))
				.add(new Paving(3, -21, 12, -20, p, Paving.Pattern.STEPPING))
				.add(new Paving(-12, -19, -11, -8, p, Paving.Pattern.STEPPING))
				.add(new Paving(11, -19, 12, -8, p, Paving.Pattern.STEPPING))
				.add(Grounds.screenWall(0, -20, p, 2)));
		b.add(Component.of("t1_aula", 1).kind("hall").name("aula_pendiri").group(Group.MAIN)
				.add(new TerrainZone(-10, -18, 10, -7, p).edge(TerrainZone.Edge.SLOPE))
				.add(aula));
		b.add(Component.of("t1_dorm", 1).kind("hall").name("dorm").group(Group.SIDE)
				.add(new TerrainZone(12, -9, 22, 9, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(17, 0).front(Direction.WEST).bays(4, 6, 4).depth(7).pillar(4).platform(1).rank(Rank.A).roof(RoofBuilder.Type.XIESHAN, 1)
						.interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN)));
		// The well pavilion stands clear of the disc's r 7 ring and its trigram band (the QIAN trigram stays readable).
		b.add(Component.of("t1_well", 1).kind("well").name("well").group(Group.SIDE)
				.add(new Pavilion(-9, -3, p).size(3).rank(Rank.B).entry(Direction.EAST))
				.add(Stamp.vat(-9, -3, p)).add(Stamp.vat(-11, -5, p)).add(Stamp.vat(-11, -1, p)).add(Stamp.vat(-7, -5, p)).add(Stamp.vat(-7, -1, p))
				.add(Stamp.vat(-9, -5, p))
				.add(Marks.anchor("post_water", -8, -3, p)));
		b.add(Component.of("t1_gate", 1).kind("gate").name("inner_gate").group(Group.BOUNDARY)
				.add(Gates.innerGate(0, 19, p, 2, true))
				.add(Marks.anchor("arrival", 0, 12, p)));
		b.add(Component.of("t1_hero", 1).kind("tree").name("hero_tree").group(Group.GARDEN)
				.add(new TerrainZone(-23, 12, -15, 20, p).edge(TerrainZone.Edge.SLOPE).keepNature(true))
				.add(new Tree(Tree.Kind.PINE, -19, 16, p).hero(true).force(true).within(-23, 12, -16, 20))
				.add(Rock.scholar(-16, 13, p, 3)));
		b.add(ornament(1).add(new Ornament(1, p).flowers(-11, 16, 11, 16))
				// Review views: the paifang, across the lake to the inner peak's hall, the aerial.
				.add(Marks.camera(1, 0, 34, p, 0, -44, Lv.at(18))).add(Marks.camera(2, -34, -14, p, -36, -54, Lv.at(20)))
				.add(Marks.camera(3, 43, 35, Lv.at(56), 0, -12, p)));

		// ============================================================ T2 Api Dapur dan Kebun Obat
		b.add(Component.of("t2_kitchen", 2).kind("kitchen").name("kitchen_court").group(Group.SIDE)
				.add(new TerrainZone(-36, -13, -24, -4, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Wall(Wall.Style.WHITE_GARDEN, p, 4).ring(-36, -13, -24, -4).gap(-24, -8, 1).gap(-30, -4, 1).drips(2))
				.add(new Hall(-31, -10).front(Direction.SOUTH).bays(2, 2, 2).depth(3).pillar(4).platform(0).rank(Rank.A).roof(RoofBuilder.Type.XIESHAN, 1)
						.overhang(1).interior(Interiors.Preset.KITCHEN).doors(Hall.Doors.NONE))
				.add(Stamp.coop(-26, -7, Direction.WEST, p))
				.add(Marks.anchor("post_farm", -28, -7, p))
				.add(Pond.basin(-34, -6, -34, -6, p).plants(false))
				.add(Stamp.of("well_frame").layer("W.W").layer("W.W").layer("sSs")
						.legend('W', Palette.st(Blocks.ANDESITE_WALL))
						.legend('S', Palette.st(Blocks.DEEPSLATE_TILES))
						.legend('s', Palette.slab(Blocks.DEEPSLATE_TILE_SLAB, false))
						.phase(Phase.FRAME)
						.at(-34, -6, Direction.SOUTH, p))
				.add(new Paving(-33, -7, -27, -5, p, Paving.Pattern.PATH).earth(false)));
		b.add(Component.of("t2_garden", 2).kind("garden").name("herb_garden").group(Group.GARDEN)
				.add(new TerrainZone(-43, -14, -38, 6, p).edge(TerrainZone.Edge.SLOPE).surface(TerrainZone.Surface.MOSS))
				.add(Grounds.herbBeds(-43, -14, -38, -5, p))
				.add(Grounds.herbBeds(-43, -3, -38, 6, p))
				.add(new Paving(-43, -4, -38, -4, p, Paving.Pattern.STEPPING).earth(false))
				// Every tree keeps to its plot, clear of the later inner dorm (T7), gate (T4), galleries (T3/T7), bell and drum
				// towers (T7) and the pagoda (T8).
				.add(new Tree(Tree.Kind.GINKGO, -33, 7, p).force(true).within(-35, 6, -31, 8))
				.add(new Tree(Tree.Kind.MAPLE, -27, 7, p).force(true).within(-29, 6, -25, 8))
				.add(new Tree(Tree.Kind.PINE, -41, 12, p).force(true))
				.add(new Tree(Tree.Kind.PLUM, 20, 14, p).force(true))
				.add(new Tree(Tree.Kind.PINE, 40, 13, p).force(true))
				.add(new Tree(Tree.Kind.MAPLE, 26, 11, p).force(true)));
		b.add(ornament(2).add(new Ornament(2, p).lanterns(LanternPost.Style.JADE, -37, -8, -23, 0, 23, -10)));

		// ============================================================ T3 Paviliun Kitab
		b.add(Component.of("t3_scripture", 3).kind("hall").name("scripture").group(Group.SIDE)
				.add(new TerrainZone(-21, -9, -11, 9, p).edge(TerrainZone.Edge.SLOPE))
				// A hipped (庑殿) two-storey library: its own silhouette, unlike the xieshan scripture of template 5.
				.add(new Hall(-16, 0).front(Direction.EAST).bays(4, 6, 4).depth(7).pillar(6).overhang(2).storeys(2, 4).platform(1).rank(Rank.B)
						.roof(RoofBuilder.Type.HIP, 1).interior(Interiors.Preset.LIBRARY, 48).doors(Hall.Doors.NONE))
				.add(Stamp.of("lectern").layer("L").legend('L', Palette.facing(Blocks.LECTERN, Direction.EAST)).phase(Phase.INTERIOR)
						.at(-15, 4, Direction.SOUTH, Lv.at(1))));
		b.add(Component.of("t3_galleries", 3).kind("gallery").name("galleries").group(Group.SIDE)
				.add(new Corridor(-13, 13, -8, 13, p).wall(-1))
				.add(new Corridor(8, 13, 13, 13, p).wall(-1)));
		b.add(Component.of("t3_plaza", 3).kind("paving").name("plaza").group(Group.AXIS)
				.add(new Paving(-11, -6, -6, 17, p, Paving.Pattern.PLAZA))
				.add(new Paving(6, -6, 11, 17, p, Paving.Pattern.PLAZA))
				.add(new Paving(-5, 3, -3, 17, p, Paving.Pattern.PLAZA))
				.add(new Paving(3, 3, 5, 17, p, Paving.Pattern.PLAZA)));
		b.add(ornament(3).add(new Ornament(3, p).flowers(-10, 7, 10, 7)));

		// ============================================================ T4 Tembok dan Air
		b.add(Component.of("t4_gate", 4).kind("gate").name("shanmen").group(Group.BOUNDARY)
				.add(new TerrainZone(-23, 20, 23, 36, p).edge(TerrainZone.Edge.SLOPE))
				// The stubs start at the gate's corner (core x ±9) and read as its wings.
				.add(new Wall(Wall.Style.RED_PALACE, p, 6).line(-22, 25, -10, 25).line(10, 25, 22, 25).drips(2))
				.add(Gates.gateHall(0, 25, p, 5))
				.add(Rock.hill(-26, 24, p, 3)).add(Rock.hill(26, 24, p, 3))
				.add(new Paving(-2, 24, 2, 36, p, Paving.Pattern.AXIS).earth(false))
				.add(Stamp.lion(-5, 30, Direction.SOUTH, p)).add(Stamp.lion(5, 30, Direction.SOUTH, p))
				.add(new LanternPost(LanternPost.Style.STONE, p).pair(4, 33)));
		b.add(Component.of("t4_boundary", 4).kind("boundary").name("boundary_stones").group(Group.BOUNDARY)
				.add(new BoundaryStones()
						.at(-44, -4, Direction.EAST).at(-44, 22, Direction.EAST).at(-44, 30, Direction.EAST)
						.at(44, -17, Direction.WEST).at(44, 12, Direction.WEST).at(44, 28, Direction.WEST)
						.at(-36, 36, Direction.NORTH).at(-20, 36, Direction.NORTH).at(20, 36, Direction.NORTH).at(36, 36, Direction.NORTH)));
		b.add(Component.of("t4_alchemy", 4).kind("hall").name("alchemy").group(Group.SIDE)
				.add(new Terrace(24, -14, 43, 6, east, p).style(Terrace.Style.STONE).deck(false).opening(33, 6, 1).opening(24, -4, 1))
				.add(new Paving(25, -13, 42, 5, east, Paving.Pattern.LAWN).earth(false))
				.add(new StairFlight(33, 9, Direction.NORTH, p, east).width(3))
				.add(new StairFlight(21, -4, Direction.EAST, p, east).width(3))
				.add(new Hall(35, -4).site(east).front(Direction.WEST).bays(4, 6, 4).depth(7).pillar(5).platform(1).rank(Rank.B)
						.roof(RoofBuilder.Type.XIESHAN, 1).interior(Interiors.Preset.ALCHEMY, 2).doors(Hall.Doors.NONE))
				.add(Stamp.of("brewing").layer("B").legend('B', Palette.st(Blocks.BREWING_STAND)).phase(Phase.INTERIOR)
						.at(33, 2, Direction.SOUTH, east.plus(1)))
				.add(new Paving(25, -6, 29, -2, east, Paving.Pattern.PLAZA).earth(false))
				.add(new Tree(Tree.Kind.PINE, 27, 3, east).force(true))
				.add(new Tree(Tree.Kind.MAPLE, 27, -11, east).force(true)));
		b.add(Component.of("t4_lake", 4).kind("garden").name("lake").group(Group.GARDEN)
				.add(lake)
				.add(new Islet(-38, -28, -30, -20, p).landing(-34, -20).landing(-32, -20))
				.add(new Channel(p, 1, -24, -59, -24, -29, -28, -29).join(lake))
				.add(new Bridge(Bridge.Type.ZIGZAG, -34, -15, -34, -20, p))
				.add(new Tree(Tree.Kind.WILLOW, -26, -18, p).force(true).within(-29, -21, -24, -15))
				.add(new Tree(Tree.Kind.WILLOW, -42, -15, p).force(true))
				.add(Rock.cluster(-26, -32, p, 2))
				.add(new LanternPost(LanternPost.Style.JADE, p).at(-36, -14).at(-32, -14)));
		b.add(ornament(4).add(new Ornament(4, p).flowers(-13, -21, 13, -21)));

		// ============================================================ T5 Formasi Agung
		b.add(Component.of("t5_disc", 5).kind("formation").name("disc").group(Group.AXIS)
				.add(new FormationDisc(6, 7, p).clip(-11, -4, 11, 17)));
		b.add(Component.of("t5_altars", 5).kind("altar").name("gate_altars").group(Group.BOUNDARY)
				.add(new GateAltar(-18, 31, p).emptySockets()).add(new GateAltar(18, 31, p).emptySockets()));
		b.add(Component.of("t5_watch", 5).kind("tower").name("watch_towers").group(Group.BOUNDARY)
				.add(new Pavilion(-34, 24, p).size(5).eaves(2).rank(Rank.B).entry(Direction.NORTH))
				.add(new Pavilion(34, 24, p).size(5).eaves(2).rank(Rank.B).entry(Direction.NORTH)));
		b.add(ornament(5).add(new Ornament(5, p).lanterns(LanternPost.Style.STONE, -10, -2, -10, 9, 10, 9)));

		// ============================================================ T6 Aula Utama
		b.add(Component.of("t6_paifang", 6).kind("paifang").name("paifang").group(Group.AXIS)
				.add(new Paifang(0, 35, p).bays(3).roofs(5).material(Paifang.Material.WHITE_STONE)));
		b.add(Component.of("t6_main_hall", 6).kind("hall").name("main_hall").group(Group.MAIN)
				.add(new Terrace(-23, -60, 23, -23, Lv.at(3), p).style(Terrace.Style.MARBLE).opening(-12, -23, 1).opening(12, -23, 1))
				.add(new Unplant(-24, -59, -24, -29, p))
				.add(new StairFlight(-12, -20, Direction.NORTH, p, Lv.at(3)).width(3).style(Terrace.Style.MARBLE))
				.add(new StairFlight(12, -20, Direction.NORTH, p, Lv.at(3)).width(3).style(Terrace.Style.MARBLE))
				.add(new Terrace(-21, -58, 21, -27, Lv.at(6), Lv.at(3)).style(Terrace.Style.MARBLE).opening(0, -27, 2))
				.add(new StairFlight(0, -24, Direction.NORTH, Lv.at(3), Lv.at(6)).width(5).style(Terrace.Style.MARBLE).ramp(1))
				.add(new Terrace(-19, -56, 19, -31, Lv.at(9), Lv.at(6)).style(Terrace.Style.MARBLE).opening(0, -31, 2))
				.add(new StairFlight(0, -28, Direction.NORTH, Lv.at(6), Lv.at(9)).width(5).style(Terrace.Style.MARBLE).ramp(1))
				// 13 deep: width 29, ratio 2.2 (DESIGN 21.7), a hall of real depth under its double hipped roof.
				.add(new Hall(0, -44).site(Lv.at(9)).bays(3, 4, 4, 6, 4, 4, 3).depth(13).pillar(6).platform(2).rank(Rank.D)
						.roof(RoofBuilder.Type.HIP, 2).interior(Interiors.Preset.MAIN_HALL))
				.add(new LanternPost(LanternPost.Style.PAGODA, Lv.at(3)).pair(4, -24))
				.add(new LanternPost(LanternPost.Style.PAGODA, Lv.at(6)).pair(4, -28))
				.add(new LanternPost(LanternPost.Style.PALACE, Lv.at(9)).at(-12, -32, Direction.EAST).at(12, -32, Direction.WEST))
				// The bronze dings on the middle terrace (one with signal smoke), in this component so they replace its deck.
				.add(Stamp.ding(-10, -29, Lv.at(6), true)).add(Stamp.ding(10, -29, Lv.at(6), false)));
		b.add(ornament(6).add(new Ornament(6, p).banners(-4, -7, 4, -7, -4, 18, 4, 18))
				.add(Marks.anchor("treasury", 5, -11, Lv.at(1))));

		// ============================================================ T7 Lapangan Latihan
		b.add(Component.of("t7_drill", 7).kind("drill").name("drill_ground").group(Group.SIDE)
				.add(Grounds.arena(-36, 9, -24, 19, p, 2))
				.add(Grounds.drillGround(24, 10, 36, 19, p, 10, true))
				.add(Stamp.weaponRack(30, 21, Direction.NORTH, p))
				.add(Stamp.flagPole(-22, 9, Direction.SOUTH, p, 6)).add(Stamp.flagPole(22, 9, Direction.SOUTH, p, 6)));
		// Two-storey bell and drum towers (not the double-eave pavilions of template 5): the bell hangs from the upper floor.
		b.add(Component.of("t7_bell_drum", 7).kind("tower").name("bell_drum").group(Group.SIDE)
				.add(Gates.cornerTower(17, -16, p, 2, 2, Direction.WEST).overhang(2))
				.add(Stamp.of("bell").layer(" ").layer(" ").layer(" ").layer("B").legend('B', Palette.bell(Direction.WEST, true)).phase(Phase.INTERIOR)
						.at(17, -16, Direction.SOUTH, Lv.at(1)))
				.add(Gates.cornerTower(-17, -16, p, 2, 2, Direction.EAST).overhang(2))
				.add(Stamp.drum(-17, -16, Lv.at(1))));
		b.add(Component.of("t7_galleries", 7).kind("gallery").name("gallery").group(Group.SIDE)
				.add(new Corridor(-22, 21, -13, 21, p))
				.add(new Corridor(13, 21, 22, 21, p)));
		b.add(Component.of("t7_inner", 7).kind("hall").name("inner_dorm").group(Group.SIDE)
				.add(new TerrainZone(-36, -2, -24, 6, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(-30, 2).front(Direction.SOUTH).bays(3, 4, 3).depth(3).pillar(4).platform(1).rank(Rank.A).roof(RoofBuilder.Type.XIESHAN, 1)
						.overhang(1).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN)));
		b.add(ornament(7).add(new Ornament(7, p).lanterns(LanternPost.Style.HANGING, -11, -7, 11, -7)));

		// ============================================================ T8 Pagoda dan Leluhur
		// The pad is levelled first; the massif then turns its earth fill into rock and keeps its paved floor. The plunge pool at
		// the cliff foot waits for the T9 waterfall.
		b.add(Component.of("t8_inner_peak", 8).kind("peak").name("inner_peak").group(Group.MAIN)
				.add(Pond.pond(-27, -58, 1, 1, p))
				.add(new TerrainZone(-43, -58, -29, -42, inner).edge(TerrainZone.Edge.NONE).surface(TerrainZone.Surface.PAVE))
				.add(innerPeak)
				.add(new Hall(-36, -54).site(inner).front(Direction.SOUTH).bays(3, 4, 3).depth(5).pillar(5).platform(1).overhang(2).storeys(2, 4)
						.rank(Rank.C).roof(RoofBuilder.Type.XIESHAN, 1).doors(Hall.Doors.OPEN).plaque(true).gableField(Palette.st(Palette.PILLAR)))
				.add(lecture.at(-36, -54, Direction.SOUTH, Lv.at(13)))
				.add(new Hall(-36, -43).site(inner).front(Direction.NORTH).bays(2, 4, 2).depth(3).pillar(4).platform(1).overhang(1).rank(Rank.A)
						.roof(RoofBuilder.Type.XIESHAN, 1).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN))
				.add(new Wall(Wall.Style.WHITE_GARDEN, inner, 3).line(-43, -58, -43, -44).line(-29, -58, -29, -44).gap(-29, -48, 1).leakWindows(4).drips(0))
				.add(new LanternPost(LanternPost.Style.JADE, inner).at(-41, -48).at(-31, -46)));
		b.add(Component.of("t8_gallery_bridge", 8).kind("bridge").name("bridge").group(Group.MAIN)
				.add(Marks.demolish(-21, 7, -50, -21, 8, -46))
				.add(new Corridor(-21, -48, -29, -48, Lv.at(6)).to(inner))
				.add(new Piers(-21, -48, -29, -48, Lv.at(6), inner).range(5, 7)));
		b.add(Component.of("t8_pagoda", 8).kind("pagoda").name("pagoda").group(Group.SIDE)
				.add(new TerrainZone(26, -33, 42, -17, p).edge(TerrainZone.Edge.SLOPE).keepNature(true))
				.add(new Pagoda(34, -25, p).floors(7, new int[]{4, 4, 3, 3, 2, 2, 1}, new int[]{4, 3, 3, 3, 3, 3, 3})
						.overhangs(3, 2, 3, 2, 3, 2, 2).plinth(1).octagonal(true).glazed(true))
				.add(new Paving(33, -17, 35, -16, p, Paving.Pattern.STEPPING))
				.add(new Tree(Tree.Kind.PINE, 41, -30, p).force(true)));
		b.add(Component.of("t8_ancestral", 8).kind("hall").name("ancestral_hall").group(Group.SIDE)
				.add(new TerrainZone(26, -41, 42, -34, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(34, -37).front(Direction.SOUTH).bays(3, 4, 3).depth(5).pillar(5).platform(1).rank(Rank.C).roof(RoofBuilder.Type.XIESHAN, 1)
						.interior(Interiors.Preset.ANCESTRAL).doors(Hall.Doors.OPEN)));
		b.add(Component.of("t8_meditation", 8).kind("pavilion").name("meditation_pavilion").group(Group.GARDEN)
				.add(new Pavilion(-34, -24, p).size(5).sides(8).seat(2).rank(Rank.C).entry(Direction.SOUTH)));
		b.add(Component.of("t8_beasts", 8).kind("garden").name("beast_garden").group(Group.GARDEN)
				.add(Grounds.beastGarden(-43, 9, -38, 19, p)));
		b.add(Component.of("t8_bamboo", 8).kind("bamboo").name("bamboo").group(Group.GARDEN)
				.add(new BambooGrove(-28, -44, -26, -38, p).maxHeight(9))
				.add(new BambooGrove(-44, -34, -42, -32, p).maxHeight(10)));
		b.add(ornament(8).add(new Ornament(8, p).lanterns(LanternPost.Style.JADE, -24, -14, 24, -16, 30, -33)));

		// ============================================================ T9 Menara Langit Membara
		b.add(Component.of("t9_tower", 9).kind("tower").name("blazing_tower").group(Group.MAIN)
				// The tower ting stands clear of the disc's ring and trigram band (the GEN trigram stays whole).
				.add(new Pavilion(9, -3, p).size(3).sides(0).eaves(2).rank(Rank.B).entry(Direction.WEST))
				.add(new TowerShaft(9, -3, p, 6, -5).roomSide(Direction.EAST)));
		// The marble terrace is laid first; the massif then turns its fill into rock and keeps its deck and balustrade.
		b.add(Component.of("t9_cloud", 9).kind("terrace").name("cloud_terrace").group(Group.MAIN)
				.add(new Terrace(30, -57, 38, -49, cloud, Lv.at(13)).style(Terrace.Style.MARBLE).opening(30, -53, 1).opening(38, -53, 1))
				.add(cloudPeak)
				.add(new Pavilion(34, -53, cloud).size(3).eaves(2).rank(Rank.C).entry(Direction.WEST))
				.add(new CliffDeck(39, -55, 43, -51, cloud, Direction.WEST, cloudPeak::height))
				.add(new Pavilion(41, -53, cloud).size(3).sides(6).rank(Rank.C).entry(Direction.WEST))
				.add(Marks.demolish(19, 9, -54, 19, 10, -52))
				.add(new Bridge(Bridge.Type.SKY, 20, -53, 29, -53, Lv.at(9)).endLevel(cloud).width(3))
				.add(new LanternPost(LanternPost.Style.PALACE, cloud).at(31, -56, Direction.EAST).at(31, -50, Direction.EAST)));
		b.add(Component.of("t9_waterfall", 9).kind("waterfall").name("waterfall").group(Group.GARDEN)
				.add(new Waterfall("inner", -28, -58, inner, p, Direction.EAST)));
		b.add(Component.of("t9_finale", 9).kind("finale").name("finale").group(Group.GARDEN)
				.add(aula.gilding())
				.add(new Tree(Tree.Kind.GINKGO, -12, 31, p).force(true)).add(new Tree(Tree.Kind.GINKGO, 12, 31, p).force(true))
				.add(new Tree(Tree.Kind.PLUM, -28, 33, p).force(true)).add(new Tree(Tree.Kind.PLUM, 28, 33, p).force(true))
				.add(Marks.petals(-11, 3, 11, 15, p, 10))
				.add(new LanternPost(LanternPost.Style.PALACE, p).at(-6, 17, Direction.WEST).at(6, 17, Direction.EAST)
						.at(-8, 32, Direction.EAST).at(8, 32, Direction.WEST)));
		b.add(ornament(9).add(new Ornament(9, p).flowers(-10, 4, 11, 1)));
		return b.build();
	}

	private static Component.Builder ornament(int tier) {
		return Component.of("t" + tier + "_ornament", tier).kind("ornament").name("ornament_" + tier).group(Group.ORNAMENT);
	}

	private T6HolyLand() {
	}
}
