package dev.kultivator.sect.build.blueprint.templates;

import dev.kultivator.sect.build.blueprint.BambooGrove;
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
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * Template 4, Sekte Kelas Satu / First-Rate Sect (DESIGN 21.1, 21.3, 21.4): 57×63, a martial mountain gate. The axis runs
 * from the 3-bay shanmen on the south wall through a narrow outer court (archery west, drill ground east, T7) and the
 * hanging-flower inner gate into the plaza with the Bagua disc (lodestone at (0, 6), ring r 5), then, framed by a red-wood
 * paifang (T6, where the screen-wall rock ends the axis before), up the <b>long central stair</b>: 7 wide, rising 8 in two
 * flights with three landings (the middle one the +4 terrace band, where disciples train and the signal ding smokes), to
 * the 5-bay double-eave main hall on its +8 terrace, the one dominant vertical at the stair head. The plaza court is closed
 * by the 2-storey Scripture Pavilion (west, 32 shelves) and the Aula Pendiri (east, later the Kantor Misi, with the Blazing
 * Sky cellar of 4 rooms under it at T9); the Outer dorm and a stepped herb garden flank the stair foot; west the kitchen
 * court, the alchemy hall on its +2 side terrace with a small waterfall falling off its retaining wall into the lotus pool
 * (T4) and the ancestral hall behind it (T8); east the hero pine, the beast garden and the +2 Inner court with the Inner
 * dorm (T7), the square 5-floor pagoda and the octagonal meditation pavilion (Qi seat tier 2, T8). Red palace walls with 4
 * xieshan watchtowers (T5).
 * <p>
 * Sources (DESIGN 21.1): wuxia/xianxia genre convention of first-rate sects (Wuxiaworld translations of <i>Yuan's
 * Ascension</i> and <i>Everlasting</i> ch. 73): a genre label, not one work's ladder. Rank by height: <i>I Shall Seal the
 * Heavens</i> ch. 2-3. Drill ground: <i>Return of the Mount Hua Sect</i>. Beast garden: <i>A Record of a Mortal's
 * Journey</i>, Spirit Beast Mountain (name only). Kantor Misi: <i>A Will Eternal</i> ch. 9 and 27. Duty stations, library
 * and tower as in DESIGN 21.2. The long stair, watchtowers and courts are art direction (mountain gates such as Wudang's
 * stairways), not canon.
 */
public final class T4FirstRate {

	public static TemplateSpec build() {
		Lv p = Lv.PLAZA;
		Lv side = Lv.at(2);
		Lv band = Lv.at(4);
		Lv top = Lv.at(8);
		TemplateSpec.Builder b = TemplateSpec.builder(4).footprint(-28, 28, -38, 24).envelope(40, 18).relief(22).earthwork(17955)
				.disc(6, 5).territory(46).towerRooms(4).qiTier(2).gateAltars(0).planVersion(1)
				.zone("plaza", -27, -11, 27, 24, 0)
				.zone("side", -27, -28, -19, -13, 2)
				.zone("inner", 19, -28, 27, -13, 2)
				.zone("band", -18, -38, 18, -12, 4)
				.zone("top", -17, -38, 17, -18, 8)
				.zone("nw", -27, -37, -19, -29, 0)
				.zone("ne", 19, -37, 27, -29, 0);

		// The Aula Pendiri faces west across the plaza: core 13×7 (bays [3,6,3]), platform x 9..17 z -5..9, eaves x 8..18 z -6..10.
		Hall aula = new Hall(13, 2).front(Direction.WEST).bays(3, 6, 3).depth(7).pillar(5).platform(1).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1)
				.doors(Hall.Doors.OPEN).interior(Interiors.Preset.STORE).plaque(true).gableField(Palette.st(Palette.PILLAR));

		// ============================================================ T1 Peletakan Prasasti
		b.add(Component.of("t1_axis", 1).kind("axis").name("axis").group(Group.AXIS)
				.add(new TerrainZone(-7, -6, 7, 22, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Paving(-2, -4, 2, 24, p, Paving.Pattern.AXIS))
				.add(new Paving(-5, -4, 5, 2, p, Paving.Pattern.PLAZA).earth(false))
				.add(new Paving(-2, -2, 2, 2, p, Paving.Pattern.OCTAGON_INLAY).radius(2).earth(false))
				.add(new LanternPost(LanternPost.Style.STONE, p).at(-2, -2).at(2, -2).at(-2, 2).at(2, 2))
				.add(new LanternPost(LanternPost.Style.RED_POST, p).pair(4, 17).at(7, -3))
				.add(Grounds.screenWall(0, -6, p, 2)));
		b.add(Component.of("t1_aula", 1).kind("hall").name("aula_pendiri").group(Group.MAIN)
				.add(new TerrainZone(8, -6, 18, 10, p).edge(TerrainZone.Edge.SLOPE))
				.add(aula));
		b.add(Component.of("t1_dorm", 1).kind("hall").name("dorm").group(Group.SIDE)
				.add(new TerrainZone(5, -11, 17, -7, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(11, -9).front(Direction.SOUTH).bays(3, 4, 3).depth(3).pillar(4).platform(1).rank(Rank.A).roof(RoofBuilder.Type.XIESHAN, 1)
						.overhang(1).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN)));
		b.add(Component.of("t1_well", 1).kind("well").name("well").group(Group.SIDE)
				.add(Pond.basin(-7, -3, -7, -3, p).plants(false))
				.add(Stamp.of("well_frame").layer("W.W").layer("W.W").layer("sSs")
						.legend('W', Palette.st(Blocks.ANDESITE_WALL))
						.legend('S', Palette.st(Blocks.DEEPSLATE_TILES))
						.legend('s', Palette.slab(Blocks.DEEPSLATE_TILE_SLAB, false))
						.phase(Phase.FRAME)
						.at(-7, -3, Direction.SOUTH, p))
				.add(Stamp.vat(-9, -4, p)).add(Stamp.vat(-9, -2, p)).add(Stamp.vat(-5, -4, p)).add(Stamp.vat(-7, -1, p))
				.add(Marks.anchor("post_water", -6, -1, p)));
		b.add(Component.of("t1_gate", 1).kind("gate").name("inner_gate").group(Group.BOUNDARY)
				.add(Gates.innerGate(0, 14, p, 2, true))
				.add(Marks.anchor("arrival", 0, 10, p)));
		b.add(Component.of("t1_hero", 1).kind("tree").name("hero_tree").group(Group.GARDEN)
				.add(new TerrainZone(20, -4, 27, 6, p).edge(TerrainZone.Edge.SLOPE).keepNature(true))
				.add(new Tree(Tree.Kind.PINE, 23, 1, p).hero(true).force(true))
				.add(Rock.cluster(26, 4, p, 1)));
		b.add(ornament(1).add(new Ornament(1, p).flowers(-6, 12, 6, 12))
				// Review views: the gate up the long stair, from the lotus garden to the main hall, the aerial.
				.add(Marks.camera(1, 0, 23, p, 0, -27, Lv.at(16))).add(Marks.camera(2, -22, -3, p, 0, -27, Lv.at(16)))
				.add(Marks.camera(3, 27, 23, Lv.at(40), 0, -10, p)));

		// ============================================================ T2 Api Dapur dan Kebun Obat
		b.add(Component.of("t2_kitchen", 2).kind("kitchen").name("kitchen_court").group(Group.SIDE)
				.add(new TerrainZone(-27, -3, -22, 9, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Wall(Wall.Style.WHITE_GARDEN, p, 4).line(-22, -3, -22, 9).line(-27, -3, -23, -3).line(-27, 9, -23, 9).line(-28, -3, -28, 9)
						.gap(-22, 6, 1).drips(1))
				.add(new Hall(-25, 2).front(Direction.EAST).bays(1, 2, 1).depth(3).pillar(4).platform(0).rank(Rank.A).roof(RoofBuilder.Type.XIESHAN, 1)
						.overhang(1).interior(Interiors.Preset.KITCHEN).doors(Hall.Doors.NONE))
				.add(Stamp.coop(-25, 6, Direction.EAST, p))
				.add(Marks.anchor("post_farm", -23, 6, p))
				.add(new Paving(-26, 4, -23, 4, p, Paving.Pattern.PATH).earth(false)));
		b.add(Component.of("t2_garden", 2).kind("garden").name("herb_garden").group(Group.GARDEN)
				.add(new TerrainZone(-11, -11, -6, -6, p).edge(TerrainZone.Edge.SLOPE).surface(TerrainZone.Surface.MOSS))
				.add(Grounds.herbBeds(-10, -11, -6, -9, p))
				.add(Grounds.herbBeds(-10, -7, -6, -6, p))
				.add(new Paving(-11, -11, -11, -6, p, Paving.Pattern.STEPPING).earth(false))
				.add(new Tree(Tree.Kind.GINKGO, -25, 12, p).force(true).within(-27, 10, -23, 14))
				.add(new Tree(Tree.Kind.MAPLE, -25, -7, p).red(true).force(true))
				.add(new Tree(Tree.Kind.GINKGO, -14, 23, p).force(true))
				.add(new Tree(Tree.Kind.GINKGO, 14, 23, p).force(true))
				.add(new Tree(Tree.Kind.GINKGO, -17, 23, p).force(true))
				.add(new Tree(Tree.Kind.GINKGO, 17, 23, p).force(true)));
		b.add(ornament(2).add(new Ornament(2, p).lanterns(LanternPost.Style.JADE, -11, -5, 19, -4)));

		// ============================================================ T3 Paviliun Kitab
		b.add(Component.of("t3_scripture", 3).kind("hall").name("scripture").group(Group.SIDE)
				.add(new TerrainZone(-21, -4, -11, 8, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(-16, 2).front(Direction.EAST).bays(3, 4, 3).depth(7).pillar(6).overhang(2).storeys(2, 4).platform(1).rank(Rank.B)
						.roof(RoofBuilder.Type.XIESHAN, 1).interior(Interiors.Preset.LIBRARY, 32).doors(Hall.Doors.NONE)));
		b.add(Component.of("t3_galleries", 3).kind("gallery").name("galleries").group(Group.SIDE)
				.add(new Corridor(-11, 12, -6, 12, p).wall(-1))
				.add(new Corridor(6, 12, 11, 12, p).wall(-1)));
		b.add(Component.of("t3_plaza", 3).kind("paving").name("plaza").group(Group.AXIS)
				.add(new Paving(-9, -4, -3, 13, p, Paving.Pattern.PLAZA))
				.add(new Paving(3, -4, 7, 13, p, Paving.Pattern.PLAZA))
				.add(new Paving(-12, 14, 12, 19, p, Paving.Pattern.PATH)));
		b.add(ornament(3).add(new Ornament(3, p).flowers(-10, 9, 10, 9)));

		// ============================================================ T4 Tembok dan Air
		b.add(Component.of("t4_walls", 4).kind("wall").name("wall").group(Group.BOUNDARY)
				// The front wall meets the shanmen's corner pillars (x ±6): no gap beside the gate.
				.add(new Wall(Wall.Style.RED_PALACE, p, 6).line(-28, 20, -7, 20).line(7, 20, 28, 20).line(-28, -38, -28, 19).line(28, -38, 28, 19)
						.line(-27, -38, -18, -38).line(18, -38, 27, -38).drips(1))
				.add(new Hall(0, 20).bays(3, 6, 3).depth(3).pillar(5).platform(0).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1).overhang(2)
						.passage(true).porch(0).gableField(Palette.st(Palette.PILLAR)))
				.add(Stamp.lion(-5, 23, Direction.SOUTH, p)).add(Stamp.lion(5, 23, Direction.SOUTH, p))
				.add(new LanternPost(LanternPost.Style.STONE, p).pair(9, 23)));
		b.add(Component.of("t4_alchemy", 4).kind("hall").name("alchemy").group(Group.SIDE)
				.add(new Terrace(-27, -24, -19, -13, side, p).style(Terrace.Style.STONE).deck(false).opening(-26, -13, 0).opening(-21, -13, 1))

				.add(new Paving(-26, -23, -20, -14, side, Paving.Pattern.LAWN).earth(false))
				.add(new StairFlight(-26, -11, Direction.NORTH, p, side).width(1))
				.add(new Hall(-23, -18).site(side).front(Direction.SOUTH).bays(2, 2, 2).depth(5).pillar(4).platform(1).overhang(1).rank(Rank.B)
						.roof(RoofBuilder.Type.XIESHAN, 1).interior(Interiors.Preset.ALCHEMY, 1).doors(Hall.Doors.NONE))
				.add(Stamp.of("brewing").layer("B").legend('B', Palette.st(Blocks.BREWING_STAND)).phase(Phase.INTERIOR)
						.at(-21, -19, Direction.SOUTH, side.plus(1)))
				.add(new Paving(-25, -14, -21, -14, side, Paving.Pattern.PLAZA).earth(false)));
		b.add(Component.of("t4_lotus", 4).kind("garden").name("lotus_garden").group(Group.GARDEN)
				.add(Pond.pond(-21, -9, 2, 3, p))
				.add(new Waterfall("lotus", -21, -12, side, p, Direction.SOUTH))
				.add(Rock.scholar(-25, -6, p, 3))
				.add(new Tree(Tree.Kind.WILLOW, -18, -9, p).force(true).within(-21, -11, -16, -6))
				.add(new LanternPost(LanternPost.Style.JADE, p).at(-24, -4).at(-18, -5)));
		b.add(ornament(4).add(new Ornament(4, p).flowers(-7, 23, 7, 23)));

		// ============================================================ T5 Formasi Agung
		b.add(Component.of("t5_disc", 5).kind("formation").name("disc").group(Group.AXIS)
				.add(new FormationDisc(6, 5, p).clip(-9, -1, 9, 13)));
		b.add(Component.of("t5_towers", 5).kind("tower").name("watch_towers").group(Group.BOUNDARY)
				.add(Gates.cornerTower(-24, 20, p, 2, 2, Direction.SOUTH).overhang(2))
				.add(Gates.cornerTower(24, 20, p, 2, 2, Direction.SOUTH).overhang(2))
				.add(Gates.cornerTower(-23, -33, p, 2, 2, Direction.SOUTH).overhang(2))
				.add(Gates.cornerTower(23, -33, p, 2, 2, Direction.SOUTH).overhang(2)));
		b.add(ornament(5).add(new Ornament(5, p).lanterns(LanternPost.Style.STONE, -8, -1, 8, -1)));

		// ============================================================ T6 Aula Utama (the long central stair)
		b.add(Component.of("t6_paifang", 6).kind("paifang").name("paifang").group(Group.AXIS)
				.add(Marks.demolish(-3, 0, -6, 3, 4, -5))
				.add(new Paifang(0, -5, p).bays(3).roofs(3).material(Paifang.Material.RED_WOOD)));
		b.add(Component.of("t6_main_hall", 6).kind("hall").name("main_hall").group(Group.MAIN)
				// The top terrace is 2 wider than the double-eave hall's platform on each side: a balustraded walk rings the hall.
				.add(new Terrace(-18, -38, 18, -12, band, p).style(Terrace.Style.STONE).opening(0, -12, 4))
				.add(new Terrace(-17, -38, 17, -18, top, band).style(Terrace.Style.STONE).opening(0, -18, 4))
				.add(new StairFlight(0, -7, Direction.NORTH, p, band).width(7).landingEvery(2))
				.add(new StairFlight(0, -13, Direction.NORTH, band, top).width(7).landingEvery(2))
				.add(new Hall(0, -27).site(top).bays(4, 4, 6, 4, 4).depth(11).pillar(6).platform(1).rank(Rank.D).roof(RoofBuilder.Type.XIESHAN, 2)
						.stepsInset(1).interior(Interiors.Preset.MAIN_HALL).gableField(Palette.st(Palette.PILLAR)))
				.add(new LanternPost(LanternPost.Style.PAGODA, band).pair(5, -13))
				// The signal ding on the band (in this component, so its hay replaces the band deck), and lawn where the T8 bamboo grows.
				.add(Stamp.ding(-10, -15, band, true))
				.add(new Paving(13, -17, 16, -13, band, Paving.Pattern.LAWN).earth(false))
				.add(new Paving(-16, -17, -15, -15, band, Paving.Pattern.LAWN).earth(false)));
		b.add(ornament(6).add(new Ornament(6, p).banners(-4, 13, 4, 13))
				.add(Stamp.flagPole(-9, -13, Direction.SOUTH, band, 5)).add(Stamp.flagPole(9, -13, Direction.SOUTH, band, 5))
				.add(Marks.anchor("treasury", 13, 2, Lv.at(1))));

		// ============================================================ T7 Lapangan Latihan
		b.add(Component.of("t7_drill", 7).kind("drill").name("drill_ground").group(Group.SIDE)
				.add(new Paving(-20, 14, -11, 16, p, Paving.Pattern.PATH).earth(false))
				.add(Stamp.targetPost(-20, 14, Direction.EAST, p)).add(Stamp.targetPost(-20, 15, Direction.EAST, p))
				.add(Stamp.targetPost(-20, 16, Direction.EAST, p))
				.add(Grounds.drillGround(11, 14, 20, 16, p, 5, true))
				.add(Stamp.weaponRack(-15, 18, Direction.NORTH, p)).add(Stamp.weaponRack(15, 18, Direction.NORTH, p))
				.add(Stamp.flagPole(-11, 17, Direction.SOUTH, p, 5)).add(Stamp.flagPole(11, 17, Direction.SOUTH, p, 5)));
		b.add(Component.of("t7_bell_drum", 7).kind("bell").name("bell_drum").group(Group.SIDE)
				.add(Stamp.bellFrame(14, 13, Direction.SOUTH, p))
				.add(Stamp.drum(-14, 13, p)));
		b.add(Component.of("t7_inner", 7).kind("hall").name("inner_dorm").group(Group.SIDE)
				.add(new Terrace(19, -28, 27, -13, side, p).style(Terrace.Style.STONE).deck(false).opening(23, -13, 1))
				.add(new Paving(20, -27, 26, -14, side, Paving.Pattern.LAWN).earth(false))
				.add(new StairFlight(23, -11, Direction.NORTH, p, side).width(3))
				.add(new Hall(23, -16).site(side).front(Direction.SOUTH).bays(2, 2, 2).depth(3).pillar(4).platform(1).overhang(1).rank(Rank.A)
						.roof(RoofBuilder.Type.XIESHAN, 1).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN)));
		b.add(Component.of("t7_galleries", 7).kind("gallery").name("gallery").group(Group.SIDE)
				.add(new Corridor(-14, -7, -14, -13, p).to(band).wall(-1)));
		b.add(Component.of("t7_rocks", 7).kind("rocks").name("rocks").group(Group.GARDEN)
				.add(Rock.scholar(-7, -15, band, 3)).add(Rock.scholar(7, -15, band, 3))
				.add(new LanternPost(LanternPost.Style.STONE, p).pair(7, 3).pair(8, 8)));
		b.add(ornament(7).add(new Ornament(7, p).lanterns(LanternPost.Style.HANGING, -7, 6, 7, 6)));

		// ============================================================ T8 Pagoda dan Leluhur
		b.add(Component.of("t8_pagoda", 8).kind("pagoda").name("pagoda").group(Group.MAIN)
				// Real eaves (skirts of 2), a taller first floor and a top below the main hall's ridge (Table B: the hall dominates).
				.add(new Pagoda(23, -24, side).floors(5, new int[]{2, 2, 2, 1, 1}, new int[]{4, 3, 3, 3, 3}).overhangs(2, 2, 2, 2, 1).plinth(1)
						.plinthMargin(1)));
		b.add(Component.of("t8_ancestral", 8).kind("hall").name("ancestral_hall").group(Group.SIDE)
				.add(new Terrace(-27, -28, -19, -25, side, p).style(Terrace.Style.STONE).balustrade(false))
				.add(new Hall(-23, -26).site(side).front(Direction.SOUTH).bays(2, 2, 2).depth(3).pillar(4).platform(1).overhang(1).rank(Rank.C)
						.roof(RoofBuilder.Type.XIESHAN, 1).interior(Interiors.Preset.ANCESTRAL).doors(Hall.Doors.OPEN)));
		b.add(Component.of("t8_meditation", 8).kind("pavilion").name("meditation_pavilion").group(Group.GARDEN)
				.add(new Paving(20, 7, 26, 13, p, Paving.Pattern.PLAZA))
				.add(new Pavilion(23, 10, p).size(3).sides(8).seat(2).rank(Rank.C).entry(Direction.WEST)));
		b.add(Component.of("t8_beasts", 8).kind("garden").name("beast_garden").group(Group.GARDEN)
				.add(Grounds.beastGarden(20, -10, 27, -5, p)));

		b.add(Component.of("t8_bamboo", 8).kind("bamboo").name("bamboo").group(Group.GARDEN)
				.add(new BambooGrove(13, -17, 16, -13, band).maxHeight(9))
				.add(new BambooGrove(-16, -17, -15, -15, band).maxHeight(8)));
		b.add(ornament(8).add(new Ornament(8, p).lanterns(LanternPost.Style.JADE, 19, -10, -19, -10)));

		// ============================================================ T9 Menara Langit Membara
		b.add(Component.of("t9_tower", 9).kind("tower").name("blazing_cellar").group(Group.MAIN)
				.add(new TowerShaft(13, -1, Lv.at(1), 4, -5).roomSide(Direction.NORTH)));
		b.add(Component.of("t9_finale", 9).kind("finale").name("finale").group(Group.GARDEN)
				.add(aula.gilding())
				.add(Marks.petals(-8, 3, 8, 11, p, 10))
				.add(new LanternPost(LanternPost.Style.PALACE, band).at(-12, -16, Direction.SOUTH).at(12, -16, Direction.SOUTH)));
		b.add(ornament(9).add(new Ornament(9, p).flowers(-12, 13, 12, 13)));
		return b.build();
	}

	private static Component.Builder ornament(int tier) {
		return Component.of("t" + tier + "_ornament", tier).kind("ornament").name("ornament_" + tier).group(Group.ORNAMENT);
	}

	private T4FirstRate() {
	}
}
