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
import dev.kultivator.sect.build.blueprint.Pond;
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
import dev.kultivator.sect.build.blueprint.templates.t7t9.Bastion;
import dev.kultivator.sect.build.blueprint.templates.t7t9.CitadelWall;
import dev.kultivator.sect.build.blueprint.templates.t7t9.CityGate;
import dev.kultivator.sect.build.blueprint.templates.t7t9.GateAltar;
import dev.kultivator.sect.build.blueprint.templates.t7t9.GlazedScreen;
import dev.kultivator.sect.build.blueprint.templates.t7t9.Lake;
import dev.kultivator.sect.build.blueprint.templates.t7t9.Massif;
import dev.kultivator.sect.build.blueprint.templates.t7t9.Put;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * Template 8, Warisan Kuno Abadi / Ancient Immortal Lineage (DESIGN 21.1, 21.3, 21.4): 133×141, an ancient clan citadel
 * on one flat level. From the south: the red 7-roof paifang between two glazed screen walls, the moat with its three
 * bridges, the dark citadel wall (deepslate, 20% cracked, merlons) with the three-storey gate tower and two side gates,
 * the outer court with the spirit way of guardians and ancient pines, four gate altars and the bell and drum towers, the
 * chuihuamen, the plaza with the Bagua disc between the square Scripture Pavilion (W) and the octagonal Menara Pil (E),
 * the Aula Pendiri, and behind it the three halls on one three-tier white marble terrace (+3 / +6 / +9): the 9-bay
 * double-eave wudian in front, the square pyramidal Central Hall in the middle and, at T8, the 7-bay Grand Ancestral Hall
 * at the rear. The imperial garden lies NW (lake, moon bridge, the island meditation pavilion, the rockery hill with its
 * summit pavilion); the Pagoda Kaca Sembilan Harta rises NE between the two elders' courtyards; three chained relic islands
 * float over the rear court at T9, when a waterfall also drops off the rockery hill into the lake. Four three-storey corner
 * towers stand on solid bastions at the wall corners.
 * <p>
 * Sources (DESIGN 21.1): <i>Perfect World</i>: the Longevity Clans and ancient Dao lineages (古老道统) among the top forces
 * of the Nine Heavens Ten Earths; <i>Battle Through the Heavens</i>: the Eight Ancient Clans; <i>Shrouding the Heavens</i>:
 * the ancient Jiang family. Rank by height: <i>I Shall Seal the Heavens</i> ch. 2-3. Menara Pil: <i>Battle Through the
 * Heavens</i>, the Pill Tower (the shape is taken from the name). Beast garden: <i>A Record of a Mortal's Journey</i>,
 * Spirit Beast Mountain (name only). Pagoda name after <i>Soul Land</i> (a martial soul there; the storey count is design).
 * Relic islands: <i>Liezi</i>, "Tang Wen" (the chains are art direction, not canon). Hero tree, duty stations, library,
 * drill ground, Kantor Misi and the tower as in DESIGN 21.2. The citadel wall and gates (Ming city walls), the spirit way
 * (the Ming tombs), the three halls on the three-tier terrace (the Forbidden City), the glazed screen walls (the Nine-Dragon
 * Walls), the rockery hill (the imperial garden) and the Porcelain-Tower pagoda are art direction from real architecture.
 */
public final class T8AncientLineage {

	public static TemplateSpec build() {
		Lv p = Lv.PLAZA;
		Lv l3 = Lv.at(3);
		Lv l6 = Lv.at(6);
		Lv l9 = Lv.at(9);
		TemplateSpec.Builder b = TemplateSpec.builder(8).footprint(-66, 66, -88, 52).envelope(70, 30).relief(52).earthwork(93765)
				.disc(6, 8).territory(96).towerRooms(7).qiTier(3).gateAltars(4).planVersion(1)
				.zone("plaza", -12, -6, 12, 17, 0)
				.zone("court", -62, 18, 62, 40, 0)
				.zone("approach", -62, 41, 62, 52, 0)
				.zone("west", -62, -19, -13, 17, 0)
				.zone("east", 13, -19, 62, 17, 0)
				.zone("aula", -12, -19, 12, -7, 0)
				.zone("terrace", -34, -20, 34, -86, 9)
				.zone("garden", -62, -20, -35, -86, 0)
				.zone("rear", 35, -20, 62, -86, 0);

		// The imperial-garden lake (T4), shared with the rockery hill that must keep off its water (T8).
		Lake lake = new Lake("t8_lake", p).lotus(6)
				.lobe(-52, -36, 9, 8)
				.lobe(-52, -52, 9, 9)
				.lobe(-55, -63, 6, 5)
				.channel(-47, -48, -43, -46)
				.channel(-52, -68, -50, -66)
				.island(-52, -47, 3)
				.island(-57, -62, 1);

		// ============================================================ T1 Peletakan Prasasti
		b.add(Component.of("t1_axis", 1).kind("axis").name("axis").group(Group.AXIS)
				.add(new TerrainZone(-9, -6, 9, 17, p).edge(TerrainZone.Edge.NONE))
				.add(new Paving(-2, -6, 2, 30, p, Paving.Pattern.AXIS).earth(false))
				.add(new Paving(-6, -6, 6, 4, p, Paving.Pattern.PLAZA).earth(false))
				.add(new Paving(-2, -2, 2, 2, p, Paving.Pattern.OCTAGON_INLAY).radius(2).earth(false))
				.add(new LanternPost(LanternPost.Style.STONE, p).at(-2, -2).at(2, -2).at(-2, 2).at(2, 2))
				// Stepping stones round the Aula Pendiri to the screen-wall rock where the axis ends before T6.
				.add(new Paving(-12, -18, -11, -6, p, Paving.Pattern.STEPPING))
				.add(new Paving(11, -18, 12, -6, p, Paving.Pattern.STEPPING))
				.add(new Paving(-10, -19, 10, -19, p, Paving.Pattern.STEPPING))
				.add(Grounds.screenWall(0, -19, p, 2)));
		b.add(Component.of("t1_aula", 1).kind("hall").name("aula_pendiri").group(Group.MAIN)
				.add(new TerrainZone(-10, -18, 10, -6, p).edge(TerrainZone.Edge.NONE))
				.add(new Hall(0, -12).bays(4, 6, 4).depth(7).pillar(5).platform(2).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1).porch(1)
						.doors(Hall.Doors.OPEN).gableField(Palette.st(Palette.PILLAR)).interior(Interiors.Preset.MAIN_HALL)));
		b.add(Component.of("t1_dorms", 1).kind("hall").name("dorm").group(Group.SIDE)
				.add(new TerrainZone(-47, -7, -33, 15, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(-40, 4).front(Direction.EAST).bays(3, 4, 4, 4, 3).depth(7).pillar(4).platform(1).rank(Rank.A)
						.roof(RoofBuilder.Type.GABLE, 1).overhang(1).porch(0).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN))
				.add(new TerrainZone(33, -7, 47, 15, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(40, 4).front(Direction.WEST).bays(3, 4, 4, 4, 3).depth(7).pillar(4).platform(1).rank(Rank.A)
						.roof(RoofBuilder.Type.GABLE, 1).overhang(1).porch(0).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN))
				.add(new Paving(-34, 3, -10, 5, p, Paving.Pattern.STEPPING))
				.add(new Paving(10, 3, 34, 5, p, Paving.Pattern.STEPPING)));
		b.add(Component.of("t1_gate", 1).kind("gate").name("inner_gate").group(Group.BOUNDARY)
				.add(Gates.innerGate(0, 17, p, 2, true))
				.add(new LanternPost(LanternPost.Style.RED_POST, p).pair(6, 16))
				.add(Marks.anchor("arrival", 0, 15, p)));
		b.add(Component.of("t1_well", 1).kind("well").name("water_vats").group(Group.SIDE)
				.add(Stamp.vat(-4, -7, p)).add(Stamp.vat(-5, -7, p)).add(Stamp.vat(-6, -7, p)).add(Stamp.vat(-7, -7, p))
				.add(Stamp.vat(4, -7, p)).add(Stamp.vat(5, -7, p)).add(Stamp.vat(6, -7, p)).add(Stamp.vat(7, -7, p))
				.add(Marks.anchor("post_water", 3, -6, p)));
		b.add(Component.of("t1_hero", 1).kind("tree").name("hero_tree").group(Group.GARDEN)
				.add(new Paving(-17, 11, -13, 15, p, Paving.Pattern.MOSS))
				.add(new Tree(Tree.Kind.PINE, -15, 13, p).hero(true).force(true).within(-18, 11, -12, 16))
				.add(Rock.scholar(-13, 11, p, 3)));
		b.add(ornament(1).add(new Ornament(1, p).flowers(-12, 16, 12, 16, -8, -5, 8, -5))
				// Review views: the paifang before the gate tower, the court toward the glazed pagoda, the aerial.
				.add(Marks.camera(1, 0, 50, p, 0, -42, Lv.at(22))).add(Marks.camera(2, 30, -49, p, 50, -49, Lv.at(40)))
				.add(Marks.camera(3, 65, 51, Lv.at(70), 0, -20, p)));

		// ============================================================ T2 Api Dapur dan Kebun Obat
		// The large kitchen court in the east: the Dapur Api, the Lumbung granary, the coop, vats and a low court wall.
		b.add(Component.of("t2_kitchen", 2).kind("hall").name("kitchen_court").group(Group.SIDE)
				.add(new TerrainZone(50, -7, 62, 17, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(57, -1).front(Direction.WEST).bays(3, 4, 3).depth(7).pillar(4).platform(1).rank(Rank.A).roof(RoofBuilder.Type.GABLE, 1)
						.overhang(1).porch(0).interior(Interiors.Preset.KITCHEN).doors(Hall.Doors.NONE))
				.add(new Hall(57, 12).front(Direction.WEST).bays(2, 4, 2).depth(5).pillar(4).platform(1).rank(Rank.A).roof(RoofBuilder.Type.GABLE, 1)
						.overhang(1).porch(0).interior(Interiors.Preset.STORE).doors(Hall.Doors.OPEN))
				.add(new Wall(Wall.Style.WHITE_GARDEN, p, 3).line(50, -7, 50, 16).gap(50, 6, 1).drips(2))
				.add(Stamp.coop(53, 15, Direction.SOUTH, p))
				.add(Stamp.vat(53, -5, p))
				.add(new Tree(Tree.Kind.PLUM, 52, 6, p).force(true)));
		b.add(Component.of("t2_herbs", 2).kind("garden").name("herb_garden").group(Group.GARDEN)
				.add(Grounds.herbBeds(-61, 6, -50, 16, p))
				.add(new LanternPost(LanternPost.Style.JADE, p).at(-49, 6).at(-49, 16))
				.add(new Tree(Tree.Kind.PLUM, -48, 11, p).force(true)));
		b.add(Component.of("t2_trees", 2).kind("trees").name("trees").group(Group.GARDEN)
				.add(new Tree(Tree.Kind.PINE, -34, 48, p).force(true))
				.add(new Tree(Tree.Kind.PINE, 34, 48, p).force(true))
				.add(new Tree(Tree.Kind.PINE, -52, 48, p).force(true))
				.add(new Tree(Tree.Kind.PINE, 52, 48, p).force(true))
				.add(new Tree(Tree.Kind.PLUM, -30, 14, p).force(true))
				.add(new Tree(Tree.Kind.PLUM, 30, 14, p).force(true))
				.add(new Tree(Tree.Kind.GINKGO, -30, -6, p).force(true).within(-32, -9, -30, -3))
				.add(new Tree(Tree.Kind.GINKGO, 30, -6, p).force(true).within(30, -9, 32, -3))
				.add(new Tree(Tree.Kind.GINKGO, -12, -14, p).force(true))
				.add(new Tree(Tree.Kind.GINKGO, 12, -14, p).force(true))
				.add(new Tree(Tree.Kind.PINE, -58, 47, p).force(true))
				.add(new Tree(Tree.Kind.PINE, 58, 47, p).force(true))
				.add(new Tree(Tree.Kind.PINE, -58, 20, p).force(true))
				.add(new Tree(Tree.Kind.PINE, 58, 20, p).force(true))
				// Plots clear of the arena and drill ground (T7), the bell tower (T7), the main terrace (T6) and the citadel wall.
				.add(new Tree(Tree.Kind.PLUM, -47, -8, p).force(true).within(-50, -10, -44, -5))
				.add(new Tree(Tree.Kind.PLUM, 47, -8, p).force(true).within(44, -10, 50, -5))
				.add(new Tree(Tree.Kind.MAPLE, -60, -24, p).force(true))
				.add(new Tree(Tree.Kind.PINE, 17, 13, p).force(true).within(14, 10, 20, 15))
				.add(new Tree(Tree.Kind.PINE, -36, 30, p).force(true).within(-38, 28, -33, 33)));
		b.add(ornament(2).add(new Ornament(2, p).lanterns(LanternPost.Style.JADE, -13, 9, 13, 9, -32, 9, 32, 9)));

		// ============================================================ T3 Paviliun Kitab
		// A three-storey square scripture pavilion west of the plaza; its reading room keeps the lecterns at the east door,
		// so the "library" anchor stays within 16 of the stele.
		b.add(Component.of("t3_scripture", 3).kind("hall").name("scripture").group(Group.SIDE)
				.add(new TerrainZone(-28, -8, -12, 8, p).edge(TerrainZone.Edge.SLOPE))
				// A squat two-storey library tower (not the three-floor scripture of templates 7 and 9), pavilion finial.
				.add(new Pagoda(-20, 0, p).floors(2, new int[]{6, 5}, new int[]{8, 6}).overhangs(3, 3).plinthMargin(1).entry(Direction.EAST).spire(false))
				.add(Put.library(-20, 0, Direction.EAST, p, 1, -5, 5, -5, 5, 64, 2, 2)));
		b.add(Component.of("t3_galleries", 3).kind("gallery").name("galleries").group(Group.SIDE)
				.add(new Corridor(-10, 3, -10, 13, p))
				.add(new Corridor(10, 3, 10, 13, p)));
		b.add(Component.of("t3_plaza", 3).kind("paving").name("plaza").group(Group.AXIS)
				.add(new Paving(-8, -5, -3, 16, p, Paving.Pattern.PLAZA))
				.add(new Paving(3, -5, 8, 16, p, Paving.Pattern.PLAZA))
				.add(new Paving(-12, -1, -9, 1, p, Paving.Pattern.PLAZA)));
		b.add(ornament(3).add(new Ornament(3, p).flowers(-12, 2, 12, 2, -10, 15, 10, 15)));

		// ============================================================ T4 Tembok dan Air
		b.add(Component.of("t4_gate_tower", 4).kind("gate").name("gate_tower").group(Group.BOUNDARY)
				.add(new Paving(-2, 18, 2, 40, p, Paving.Pattern.AXIS))
				.add(CityGate.tower(0, 36, p, 11, 8)));
		b.add(Component.of("t4_walls", 4).kind("wall").name("wall").group(Group.BOUNDARY)
				.add(new CitadelWall(p, 7).ring(-64, -87, 64, 36).gap(0, 36, 11).gap(-40, 36, 6).gap(40, 36, 6))
				.add(CityGate.side(-40, 36, p, 8))
				.add(CityGate.side(40, 36, p, 8))
				// Horse ramps (马道) up to the wall walk on both sides of the gate tower.
				.add(new StairFlight(-15, 33, Direction.WEST, p, Lv.at(7)).width(3))
				.add(new StairFlight(15, 33, Direction.EAST, p, Lv.at(7)).width(3))
				.add(Put.air(-23, 35, -21, 35, p, 7, 8))
				.add(Put.air(21, 35, 23, 35, p, 7, 8))
				.add(new Paving(-41, 18, -39, 33, p, Paving.Pattern.PATH))
				.add(new Paving(39, 18, 41, 33, p, Paving.Pattern.PATH)));
		b.add(Component.of("t4_moat", 4).kind("moat").name("moat").group(Group.BOUNDARY)
				.add(Pond.moat(p, -62, 43, 62, 43))
				.add(new Bridge(Bridge.Type.MOON_ARCH, 0, 41, 0, 45, p).width(5))
				.add(new Bridge(Bridge.Type.FLAT, -40, 41, -40, 45, p).width(3))
				.add(new Bridge(Bridge.Type.FLAT, 40, 41, 40, 45, p).width(3))
				.add(new Paving(-2, 46, 2, 52, p, Paving.Pattern.AXIS))
				.add(new Paving(-41, 46, -39, 52, p, Paving.Pattern.PATH))
				.add(new Paving(39, 46, 41, 52, p, Paving.Pattern.PATH))
				.add(Stamp.lion(-5, 46, Direction.SOUTH, p)).add(Stamp.lion(5, 46, Direction.SOUTH, p)));
		b.add(Component.of("t4_menara_pil", 4).kind("tower").name("menara_pil").group(Group.SIDE)
				.add(new TerrainZone(15, -1, 29, 13, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Pagoda(22, 6, p).floors(3, new int[]{5, 4, 3}, new int[]{6, 5, 4}).octagonal(true).overhangs(3, 2, 2).plinthMargin(1)
						.entry(Direction.WEST).spire(false))
				.add(Put.interior(22, 6, Direction.WEST, p, 1, Interiors.Preset.ALCHEMY, 2, -3, 3, -3, 3, 4, Rank.B))
				.add(Put.fittings(22, 6, Direction.WEST, p, 1, new int[][]{{3, -3}, {-3, 1}}, new int[0][])));
		b.add(Component.of("t4_garden", 4).kind("lake").name("lake").group(Group.GARDEN)
				.add(lake)
				.add(new Bridge(Bridge.Type.MOON_ARCH, -42, -47, -49, -47, p).width(3))
				.add(new Bridge(Bridge.Type.STEPPING, -56, -47, -62, -47, p))
				.add(new Tree(Tree.Kind.WILLOW, -44, -30, p).force(true))
				.add(new Tree(Tree.Kind.WILLOW, -44, -62, p).force(true))
				.add(Rock.cluster(-61, -30, p, 2)).add(Rock.cluster(-43, -56, p, 2))
				.add(new LanternPost(LanternPost.Style.JADE, p).at(-42, -40).at(-42, -52).at(-62, -36)));
		b.add(ornament(4).add(new Ornament(4, p).lanterns(LanternPost.Style.STONE, -8, 30, 8, 30, -10, 47, 10, 47)));

		// ============================================================ T5 Formasi Agung
		b.add(Component.of("t5_disc", 5).kind("formation").name("disc").group(Group.AXIS)
				.add(new FormationDisc(6, 8, p).clip(-9, -5, 9, 16)));
		b.add(Component.of("t5_altars", 5).kind("altar").name("gate_altars").group(Group.BOUNDARY)
				.add(new GateAltar(-29, 25, p)).add(new GateAltar(29, 25, p))
				.add(new GateAltar(-48, 25, p)).add(new GateAltar(48, 25, p)));
		b.add(Component.of("t5_towers", 5).kind("tower").name("corner_towers").group(Group.BOUNDARY)
				.add(new Bastion(-60, 32, 5, p, 9)).add(Gates.cornerTower(-60, 32, Lv.at(9), 3, 2, Direction.SOUTH))
				.add(new Bastion(60, 32, 5, p, 9)).add(Gates.cornerTower(60, 32, Lv.at(9), 3, 2, Direction.SOUTH))
				.add(new Bastion(-60, -82, 5, p, 9)).add(Gates.cornerTower(-60, -82, Lv.at(9), 3, 2, Direction.SOUTH))
				.add(new Bastion(60, -82, 5, p, 9)).add(Gates.cornerTower(60, -82, Lv.at(9), 3, 2, Direction.SOUTH)));
		b.add(ornament(5).add(new Ornament(5, p).lanterns(LanternPost.Style.STONE, -13, 15, 13, 15, -31, 0, 31, 0)));

		// ============================================================ T6 Aula Utama
		b.add(Component.of("t6_spirit_way", 6).kind("spirit_way").name("spirit_way").group(Group.AXIS)
				.add(SetPieces.spiritWay(6, 19, 30, 3, p))
				.add(new LanternPost(LanternPost.Style.PALACE, p).at(-4, 31, Direction.EAST).at(4, 31, Direction.WEST)));
		// The three-tier white marble terrace (side ascents from the plaza, the dragon-ramp stair +3 -> +9 on the axis) with
		// the 9-bay double-eave wudian in front; the rear court (planted pines, lanterns) waits as a finished marble court.
		b.add(Component.of("t6_main_hall", 6).kind("hall").name("main_hall").group(Group.MAIN)
				// The terraces reach to the citadel wall's walk so the three halls stand with room between them; the main hall
				// is 13 deep (it no longer reads as a long low shed: width 37, ratio 2.8).
				.add(new Terrace(-34, -86, 34, -20, l3, p).style(Terrace.Style.MARBLE).opening(-13, -20, 1).opening(13, -20, 1).opening(0, -20, 3))
				.add(new StairFlight(-13, -17, Direction.NORTH, p, l3).width(3).style(Terrace.Style.MARBLE))
				.add(new StairFlight(13, -17, Direction.NORTH, p, l3).width(3).style(Terrace.Style.MARBLE))
				.add(new Terrace(-31, -85, 31, -25, l6, l3).style(Terrace.Style.MARBLE).opening(0, -25, 5))
				.add(new Terrace(-28, -84, 28, -30, l9, l6).style(Terrace.Style.MARBLE).opening(0, -30, 5))
				.add(new StairFlight(0, -23, Direction.NORTH, l3, l9).width(9).style(Terrace.Style.MARBLE).ramp(3))
				.add(new Hall(0, -42).site(l9).bays(3, 4, 4, 4, 6, 4, 4, 4, 3).depth(13).pillar(6).platform(2).rank(Rank.D)
						.roof(RoofBuilder.Type.HIP, 2).steps(true, true).interior(Interiors.Preset.MAIN_HALL).gableField(Palette.st(Palette.PILLAR)))
				.add(new LanternPost(LanternPost.Style.PALACE, l9).at(-6, -31, Direction.EAST).at(6, -31, Direction.WEST))
				.add(new Paving(-25, -72, -21, -68, l9, Paving.Pattern.MOSS)).add(new Tree(Tree.Kind.PINE, -23, -70, l9).force(true))
				.add(new Paving(21, -72, 25, -68, l9, Paving.Pattern.MOSS)).add(new Tree(Tree.Kind.PINE, 23, -70, l9).force(true))
				.add(new LanternPost(LanternPost.Style.STONE, l9).at(-20, -62).at(20, -62).at(-20, -78).at(20, -78)
						.at(-25, -36).at(25, -36).at(-25, -46).at(25, -46).at(-25, -56).at(25, -56))
				.add(Stamp.bonsai(-32, -30, l3, 1)).add(Stamp.bonsai(32, -30, l3, 1))
				.add(Stamp.bonsai(-32, -44, l3, 2)).add(Stamp.bonsai(32, -44, l3, 2))
				.add(Stamp.bonsai(-32, -58, l3, 3)).add(Stamp.bonsai(32, -58, l3, 3))
				.add(Stamp.bonsai(-32, -72, l3, 0)).add(Stamp.bonsai(32, -72, l3, 0)));
		b.add(Component.of("t6_central_hall", 6).kind("hall").name("hall").group(Group.MAIN)
				.add(new Hall(0, -62).site(l9).bays(2, 4, 2).depth(9).pillar(5).platform(1).rank(Rank.D).roof(RoofBuilder.Type.PYRAMID, 1).porch(1)
						.steps(true, true).doors(Hall.Doors.OPEN).gableField(Palette.st(Palette.PILLAR))));
		b.add(Component.of("t6_paifang", 6).kind("paifang").name("paifang").group(Group.BOUNDARY)
				.add(new Paifang(0, 49, p).bays(3).roofs(7).material(Paifang.Material.RED_WOOD))
				.add(new GlazedScreen(-19, 50, p, 5))
				.add(new GlazedScreen(19, 50, p, 5)));
		b.add(Component.of("t6_dings", 6).kind("ding").name("dings").group(Group.GARDEN)
				.add(Stamp.ding(-10, -27, l6, true)).add(Stamp.ding(10, -27, l6, false)));
		b.add(ornament(6).add(new Ornament(6, l9).banners(-26, -31, 26, -31, -26, -79, 26, -79)));

		// ============================================================ T7 Lapangan Latihan
		b.add(Component.of("t7_drill", 7).kind("drill").name("drill_ground").group(Group.SIDE)
				.add(Grounds.drillGround(38, -24, 60, -11, p, 12, true))
				.add(Stamp.weaponRack(44, -12, Direction.NORTH, p)).add(Stamp.weaponRack(54, -12, Direction.NORTH, p)));
		b.add(Component.of("t7_arena", 7).kind("arena").name("arena").group(Group.SIDE)
				.add(Grounds.arena(-56, -22, -42, -11, p, 2))
				.add(Stamp.drum(-58, -12, p)).add(Stamp.drum(-58, -21, p))
				.add(Stamp.flagPole(-40, -11, Direction.SOUTH, p, 6)).add(Stamp.flagPole(-40, -22, Direction.SOUTH, p, 6)));
		b.add(Component.of("t7_bell_drum", 7).kind("tower").name("bell_drum").group(Group.SIDE)
				.add(Gates.cornerTower(-19, 22, p, 2, 2, Direction.SOUTH))
				.add(Gates.cornerTower(19, 22, p, 2, 2, Direction.SOUTH))
				.add(Put.stack(-19, 22, p, 4, Phase.INTERIOR, Palette.bell(Direction.SOUTH, true)))
				.add(Put.stack(19, 22, p, 1, Phase.INTERIOR, Palette.st(Blocks.CHISELED_CINNABAR), Palette.barrel(Direction.UP))));
		b.add(Component.of("t7_inner_dorms", 7).kind("hall").name("inner_dorm").group(Group.SIDE)
				.add(new Hall(-24, -14).front(Direction.SOUTH).bays(3, 4, 3).depth(5).pillar(4).platform(1).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1)
						.porch(0).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN))
				.add(new Hall(24, -14).front(Direction.SOUTH).bays(3, 4, 3).depth(5).pillar(4).platform(1).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1)
						.porch(0).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN)));
		// A waterside gallery along the garden lake (W) and a walled gallery past the elders' courts and the pagoda (E).
		b.add(Component.of("t7_galleries", 7).kind("gallery").name("galleries").group(Group.SIDE)
				.add(new Corridor(-39, -26, -39, -66, p))
				.add(new Corridor(39, -26, 39, -62, p).wall(1).openings(6, 23)));
		// The tier ornament also flies red banners along the citadel wall walk.
		b.add(ornament(7).add(new Ornament(7, p).banners(-36, -24, 36, -24, -62, 17, 62, 17)
				.lanterns(LanternPost.Style.HANGING, -36, -10, 36, -10))
				.add(Stamp.flagPole(-26, 36, Direction.SOUTH, Lv.at(7), 5)).add(Stamp.flagPole(26, 36, Direction.SOUTH, Lv.at(7), 5))
				.add(Stamp.flagPole(-52, 36, Direction.SOUTH, Lv.at(7), 5)).add(Stamp.flagPole(52, 36, Direction.SOUTH, Lv.at(7), 5))
				.add(Stamp.flagPole(-64, 8, Direction.WEST, Lv.at(7), 5)).add(Stamp.flagPole(64, 8, Direction.EAST, Lv.at(7), 5))
				.add(Stamp.flagPole(-64, -24, Direction.WEST, Lv.at(7), 5)).add(Stamp.flagPole(64, -24, Direction.EAST, Lv.at(7), 5))
				.add(Stamp.flagPole(-64, -56, Direction.WEST, Lv.at(7), 5)).add(Stamp.flagPole(64, -56, Direction.EAST, Lv.at(7), 5))
				.add(Stamp.flagPole(-30, -87, Direction.NORTH, Lv.at(7), 5)).add(Stamp.flagPole(30, -87, Direction.NORTH, Lv.at(7), 5)));

		// ============================================================ T8 Pagoda dan Leluhur
		b.add(Component.of("t8_ancestral", 8).kind("hall").name("ancestral_hall").group(Group.MAIN)
				.add(new Hall(0, -77).site(l9).bays(3, 4, 4, 6, 4, 4, 3).depth(9).pillar(5).platform(2).rank(Rank.C).roof(RoofBuilder.Type.XIESHAN, 1)
						.porch(1).gilded(true).interior(Interiors.Preset.ANCESTRAL).doors(Hall.Doors.OPEN).gableField(Palette.st(Palette.PILLAR))));
		b.add(Component.of("t8_pagoda", 8).kind("pagoda").name("pagoda").group(Group.MAIN)
				.add(new TerrainZone(42, -58, 58, -40, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Paving(42, -58, 58, -40, p, Paving.Pattern.PLAZA).earth(false))
				.add(new Pagoda(50, -49, p).floors(9, new int[]{5, 5, 4, 4, 3, 3, 3, 2, 2}, new int[]{6, 5, 5, 4, 4, 4, 4, 4, 4}).octagonal(true)
						.glazed(true).plinth(2).plinthMargin(2).entry(Direction.WEST))
				.add(new LanternPost(LanternPost.Style.STONE, p).at(43, -41).at(57, -41).at(43, -57).at(57, -57)));
		b.add(Component.of("t8_rockery", 8).kind("garden").name("rocks").group(Group.GARDEN)
				.add(new Massif("t8_rockery", p).style(Massif.Style.ROCKERY).slope(1).pines(3).avoid(lake)
						.blob(-48, -72, 8, Lv.at(9), 0.8)
						.pad(-51, -76, -45, -69, Lv.at(10), Massif.Pad.NATURAL, true, 1)
						.stair(-36, -72, Direction.WEST, p, Lv.at(10), 3, Terrace.Style.STONE))
				.add(new Pavilion(-48, -72, Lv.at(10)).size(3).sides(8).rank(Rank.C).entry(Direction.EAST)));
		b.add(Component.of("t8_meditation", 8).kind("pavilion").name("meditation_pavilion").group(Group.GARDEN)
				.add(new Pavilion(-52, -47, p).size(5).sides(8).eaves(2).seat(3).rank(Rank.C).entry(Direction.EAST)));
		b.add(Component.of("t8_beasts", 8).kind("garden").name("beast_garden").group(Group.GARDEN)
				.add(Grounds.beastGarden(-61, -7, -50, 3, p))
				.add(new BambooGrove(-61, -26, -46, -24, p).maxHeight(10)));
		b.add(Component.of("t8_elders", 8).kind("hall").name("elders").group(Group.SIDE)
				.add(new TerrainZone(42, -38, 61, -26, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Wall(Wall.Style.WHITE_GARDEN, p, 4).ring(42, -38, 61, -26).gap(42, -32, 1).drips(2))
				.add(new Hall(55, -32).front(Direction.WEST).bays(3, 4, 3).depth(7).pillar(4).platform(1).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1)
						.porch(0).doors(Hall.Doors.OPEN))
				.add(new Tree(Tree.Kind.PLUM, 46, -29, p).force(true)).add(Rock.scholar(46, -35, p, 3))
				.add(new TerrainZone(42, -80, 54, -62, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Wall(Wall.Style.WHITE_GARDEN, p, 4).ring(42, -80, 54, -62).gap(42, -66, 1).drips(2))
				.add(new Hall(48, -75).front(Direction.SOUTH).bays(3, 4, 3).depth(7).pillar(4).platform(1).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1)
						.porch(0).doors(Hall.Doors.OPEN))
				.add(new Tree(Tree.Kind.GINKGO, 51, -65, p).force(true)).add(Stamp.bonsai(45, -65, p, 2)));
		b.add(ornament(8).add(new Ornament(8, p).lanterns(LanternPost.Style.JADE, -36, -30, -36, -60, 36, -30, 36, -60)));

		// ============================================================ T9 Menara Langit Membara
		// The tower entrance: a square ting on the plaza's east side, the seven rooms below it.
		b.add(Component.of("t9_tower", 9).kind("tower").name("blazing_tower").group(Group.MAIN)
				.add(new Pavilion(12, -3, p).size(5).sides(4).pillar(3).rank(Rank.C).entry(Direction.WEST))
				.add(new TowerShaft(12, -3, p, 7, -4).roomSide(Direction.NORTH))
				// Seven rooms (the deepest campfire 28 down, inside the 32 sphere) north of the well under a dark tower court east of
				// the Aula Pendiri; the anchor is the plaza cell at the ting's west entry.
				.add(Marks.anchor("tower", 9, -3, p)));
		// Three relic islands over the rear court, on jade chains to bollards on the terrace, the rockery and an elders' court.
		b.add(Component.of("t9_islands", 9).kind("island").name("island").group(Group.GARDEN)
				.add(new HangingIsland(0, -83, 5, Lv.at(46)).pad(true).jadeChains(true).turtle(true).tether(-3, -82, l6).tether(3, -82, l6))
				.add(new Pavilion(0, -83, Lv.at(46)).size(3).sides(4).rank(Rank.C))
				.add(new HangingIsland(-45, -79, 4, Lv.at(38)).jadeChains(true).sporeBlossom(true).tether(-45, -76, Lv.at(10)))
				.add(new HangingIsland(47, -69, 4, Lv.at(42)).jadeChains(true).tether(47, -68, p))
				.add(Stamp.boundaryStone(47, -69, Direction.SOUTH, Lv.at(42))));
		b.add(Component.of("t9_finale", 9).kind("finale").name("finale").group(Group.GARDEN)
				.add(Marks.petals(-9, -5, 9, 16, p, 10))
				.add(Marks.petals(-24, -74, 24, -64, l9, 6))
				.add(new LanternPost(LanternPost.Style.PALACE, p).at(-4, 47, Direction.EAST).at(4, 47, Direction.WEST).at(-4, 26, Direction.EAST)
						.at(4, 26, Direction.WEST))
				// A waterfall off the rockery hill's summit shelf into the garden lake.
				.add(new Waterfall("rockery", -51, -69, Lv.at(10), p, Direction.SOUTH)));
		b.add(ornament(9).add(new Ornament(9, p).flowers(-13, -2, 13, 12, -32, -2, 32, -2)));
		return b.build();
	}

	private static Component.Builder ornament(int tier) {
		return Component.of("t" + tier + "_ornament", tier).kind("ornament").name("ornament_" + tier).group(Group.ORNAMENT);
	}

	private T8AncientLineage() {
	}
}
