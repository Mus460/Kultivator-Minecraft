package dev.kultivator.sect.build.blueprint.templates;

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
import dev.kultivator.sect.build.blueprint.Pond;
import dev.kultivator.sect.build.blueprint.QiSeat;
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
import net.minecraft.core.Direction;

/**
 * Template 3, Sekte Kelas Dua / Second-Rate Sect (DESIGN 21.1, 21.3, 21.4): 45×51, a Jiangnan water garden. The plaza
 * with the Bagua disc (lodestone at (0, 6), ring r 5) is ringed by covered galleries (T3); the Aula Pendiri closes it on
 * the axis; the 2-storey Scripture Pavilion and the herb garden lie west, the Outer dorm and the kitchen court east. At T4
 * a lotus garden opens NW behind a white garden wall whose moon gate frames the arched bridge and, from T8, the 5-floor
 * square pagoda on its terrace beyond the water; a waterside pavilion (水榭) leans over the pond (it becomes the meditation
 * pavilion with its Qi seat at T8). The north terraces (+3) carry the alchemy hall (T4), the 5-bay main hall (T6) and the
 * ancestral hall (T8). Stepped white walls with a 3-bay shanmen at T4, two corner towers at T5, horse-head gables on the
 * dorm and kitchen at T8, and the Blazing Sky Tower opening in the east gallery at T9.
 * <p>
 * Sources (DESIGN 21.1): wuxia/xianxia genre convention of second-rate sects (a genre label, not one work's ladder). Rank
 * by height: <i>I Shall Seal the Heavens</i> ch. 2-3. Hero plum: <i>Return of the Mount Hua Sect</i>. Pagoda name after
 * <i>Soul Land</i>. Horse-head walls, moon gates and the water garden are art direction (Hui-style walls, Ji Cheng's
 * <i>Yuanye</i>), not canon.
 */
public final class T3SecondRate {

	public static TemplateSpec build() {
		Lv p = Lv.PLAZA;
		Lv rear = Lv.zone("rear");
		TemplateSpec.Builder b = TemplateSpec.builder(3).footprint(-22, 22, -32, 18).envelope(36, 18).relief(18).earthwork(11475)
				.disc(6, 5).territory(40).towerRooms(4).qiTier(1).gateAltars(0).planVersion(1)
				.zone("plaza", -21, -19, 21, 17, 0)
				.zone("rear", -21, -32, 21, -20, 3, 2, 4, "plaza", 2);

		Hall aula = new Hall(0, -14).bays(3, 6, 3).depth(7).pillar(5).platform(2).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1).porch(1)
				.doors(Hall.Doors.OPEN).gableField(Palette.st(Palette.PLASTER));
		// Hard-gable (硬山) roofs: the T8 horse-head walls stand on their end pillar lines and rise above the ridge.
		Hall dorm = new Hall(16, 5).front(Direction.WEST).bays(3, 4, 3).depth(5).pillar(4).platform(1).rank(Rank.A).roof(RoofBuilder.Type.GABLE, 1)
				.overhang(1).porch(0).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN);
		Hall kitchen = new Hall(16, -7).front(Direction.WEST).bays(2, 2, 2).depth(5).pillar(4).platform(1).rank(Rank.A).roof(RoofBuilder.Type.GABLE, 1)
				.overhang(1).porch(0).interior(Interiors.Preset.KITCHEN).doors(Hall.Doors.NONE);

		// ============================================================ T1 Peletakan Prasasti
		b.add(Component.of("t1_axis", 1).kind("axis").name("axis").group(Group.AXIS)
				.add(new TerrainZone(-7, -8, 7, 12, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Paving(-2, -8, 2, 12, p, Paving.Pattern.AXIS).earth(false))
				.add(new Paving(-5, -4, 5, 3, p, Paving.Pattern.PLAZA).earth(false))
				.add(new Paving(-2, -2, 2, 2, p, Paving.Pattern.OCTAGON_INLAY).radius(2).earth(false))
				.add(new LanternPost(LanternPost.Style.STONE, p).at(-2, -2).at(2, -2).at(-2, 2).at(2, 2)));
		b.add(Component.of("t1_aula", 1).kind("hall").name("aula_pendiri").group(Group.MAIN)
				.add(new TerrainZone(-9, -19, 9, -8, p).edge(TerrainZone.Edge.SLOPE))
				.add(aula));
		b.add(Component.of("t1_dorm", 1).kind("hall").name("dorm").group(Group.SIDE)
				.add(new TerrainZone(13, -1, 19, 11, p).edge(TerrainZone.Edge.SLOPE))
				.add(dorm));
		b.add(Component.of("t1_gate", 1).kind("gate").name("inner_gate").group(Group.BOUNDARY)
				.add(Gates.innerGate(0, 12, p, 2, true))
				.add(Marks.anchor("arrival", 0, 9, p)));
		b.add(Component.of("t1_well", 1).kind("well").name("water_vats").group(Group.SIDE)
				.add(Stamp.vat(-4, -3, p)).add(Stamp.vat(-4, -1, p)).add(Stamp.vat(4, -3, p)).add(Stamp.vat(4, -1, p))
				.add(Marks.anchor("post_water", -3, -2, p)));
		b.add(Component.of("t1_hero", 1).kind("tree").name("hero_tree").group(Group.GARDEN)
				.add(new Tree(Tree.Kind.PLUM, -5, 9, p).hero(true).force(true)));
		b.add(ornament(1).add(new Ornament(1, p).flowers(-6, 1, 6, 1))
				// Review views: the gate, across the lotus pond to the pagoda, the aerial.
				.add(Marks.camera(1, 0, 17, p, 0, -14, Lv.at(5))).add(Marks.camera(2, -14, -4, p, -17, -26, Lv.at(16)))
				.add(Marks.camera(3, 21, 17, Lv.at(34), 0, -8, p)));

		// ============================================================ T2 Api Dapur dan Kebun Obat
		b.add(Component.of("t2_kitchen", 2).kind("hall").name("kitchen_court").group(Group.SIDE)
				.add(new TerrainZone(13, -11, 19, -3, p).edge(TerrainZone.Edge.SLOPE))
				.add(kitchen)
				.add(Stamp.coop(19, -13, Direction.WEST, p)));
		b.add(Component.of("t2_garden", 2).kind("garden").name("herb_garden").group(Group.GARDEN)
				.add(Grounds.herbBeds(-20, -6, -13, -2, p))
				// Each tree keeps to its plot: the band before the T4 front wall, clear of the galleries' ends (T3), and the
				// rear corners clear of the side walls, the pagoda (T8) and the coop.
				.add(new Tree(Tree.Kind.GINKGO, -11, 13, p).force(true).within(-13, 13, -9, 14))
				.add(new Tree(Tree.Kind.MAPLE, 11, 13, p).force(true).within(9, 13, 13, 14))
				.add(new Tree(Tree.Kind.PINE, -20, -18, p).force(true).within(-20, -19, -18, -15))
				.add(new Tree(Tree.Kind.PINE, 20, -18, p).force(true).within(18, -19, 20, -15))
				.add(new Tree(Tree.Kind.PLUM, -14, 13, p).force(true).within(-16, 13, -12, 14))
				.add(new Tree(Tree.Kind.PLUM, 14, 13, p).force(true).within(12, 13, 16, 14)));
		b.add(ornament(2).add(new Ornament(2, p).lanterns(LanternPost.Style.JADE, -6, -6, 6, -6)));

		// ============================================================ T3 Paviliun Kitab
		b.add(Component.of("t3_scripture", 3).kind("hall").name("scripture").group(Group.SIDE)
				.add(new TerrainZone(-19, -1, -13, 11, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(-16, 5).front(Direction.EAST).bays(3, 4, 3).depth(5).pillar(6).platform(1).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1)
						.overhang(1).porch(0).storeys(2, 4).interior(Interiors.Preset.LIBRARY, 24).doors(Hall.Doors.NONE)));
		b.add(Component.of("t3_galleries", 3).kind("gallery").name("galleries").group(Group.SIDE)
				.add(new Corridor(-10, -7, -10, 11, p).wall(-1))
				.add(new Corridor(10, -7, 10, 11, p).wall(1)));
		b.add(Component.of("t3_plaza", 3).kind("paving").name("plaza").group(Group.AXIS)
				.add(new Paving(-7, -7, -3, 11, p, Paving.Pattern.PLAZA))
				.add(new Paving(3, -7, 7, 11, p, Paving.Pattern.PLAZA)));
		b.add(ornament(3).add(new Ornament(3, p).flowers(-7, 12, 7, 12)));

		// ============================================================ T4 Tembok dan Air
		b.add(Component.of("t4_alchemy", 4).kind("hall").name("alchemy").group(Group.SIDE)
				.add(new Terrace(13, -31, 20, -20, rear, p).style(Terrace.Style.STONE).opening(17, -20, 1).opening(13, -22, 1))
				.add(new StairFlight(17, -17, Direction.NORTH, p, rear).width(3))
				.add(new Hall(17, -29).site(rear).front(Direction.SOUTH).bays(2, 2, 2).depth(3).pillar(4).platform(1).rank(Rank.B)
						.roof(RoofBuilder.Type.XIESHAN, 1).overhang(1).porch(0).interior(Interiors.Preset.ALCHEMY, 1).doors(Hall.Doors.NONE))
				// The lawn is the forecourt until the T8 ancestral hall takes the plot (no tree there: it would grow into it).
				.add(new Paving(14, -25, 20, -21, rear, Paving.Pattern.LAWN).earth(false)));
		b.add(Component.of("t4_lotus", 4).kind("garden").name("lotus_garden").group(Group.GARDEN)
				.add(Pond.pond(-16, -12, 3, 3, p))
				.add(new Bridge(Bridge.Type.MOON_ARCH, -18, -7, -18, -19, p).width(3))
				.add(new Pavilion(-11, -12, p).size(5).waterside(true).rank(Rank.B).entry(Direction.EAST)));
		b.add(Component.of("t4_walls", 4).kind("wall").name("white_wall").group(Group.BOUNDARY)
				.add(Marks.demolish(-5, 5, 11, 5, 7, 13))
				.add(Marks.demolish(-3, 0, 11, 3, 4, 13))
				.add(new Wall(Wall.Style.WHITE_GARDEN, p, 6).line(-20, -7, -13, -7).moonGate(-18, -7).leakWindows(4).drips(2))
				// Drawn to the corners, so the walled tier looks finished before the T5 towers replace the corner cells.
				.add(new Wall(Wall.Style.WHITE_GARDEN, p, 5).line(-21, 15, -7, 15).line(7, 15, 21, 15).line(-21, -20, -21, 15).line(21, -20, 21, 15)
						.leakWindows(6).drips(0))
				.add(new Wall(Wall.Style.WHITE_GARDEN, rear, 5).line(-21, -32, 21, -32).line(-21, -31, -21, -21).line(21, -31, 21, -21).drips(0))
				.add(Gates.gateHall(0, 15, p, 3).overhang(1).stepsInset(1))
				.add(Stamp.lion(-5, 18, Direction.SOUTH, p)).add(Stamp.lion(5, 18, Direction.SOUTH, p)));
		b.add(ornament(4).add(new Ornament(4, p).flowers(-12, -6, 12, -2)));

		// ============================================================ T5 Formasi Agung
		b.add(Component.of("t5_disc", 5).kind("formation").name("disc").group(Group.AXIS)
				.add(new FormationDisc(6, 5, p).clip(-7, -1, 7, 10)));
		b.add(Component.of("t5_towers", 5).kind("tower").name("corner_towers").group(Group.BOUNDARY)
				.add(Gates.cornerTower(-19, 15, p, 2, 2, Direction.SOUTH).overhang(1))
				.add(Gates.cornerTower(19, 15, p, 2, 2, Direction.SOUTH).overhang(1)));
		b.add(ornament(5).add(new Ornament(5, p).lanterns(LanternPost.Style.STONE, -6, -7, 6, -7)));

		// ============================================================ T6 Aula Utama
		b.add(Component.of("t6_main_hall", 6).kind("hall").name("main_hall").group(Group.MAIN)
				.add(new Terrace(-12, -31, 12, -20, rear, p).style(Terrace.Style.STONE).opening(0, -20, 2).opening(-11, -20, 1).opening(11, -20, 1)
						.opening(-12, -22, 1).opening(12, -22, 1))
				.add(new StairFlight(-11, -17, Direction.NORTH, p, rear).width(3))
				.add(new StairFlight(11, -17, Direction.NORTH, p, rear).width(3))
				.add(new Hall(0, -26).site(rear).bays(3, 3, 6, 3, 3).depth(9).pillar(5).platform(2).rank(Rank.D).roof(RoofBuilder.Type.XIESHAN, 1)
						.porch(1).stepsInset(1).interior(Interiors.Preset.MAIN_HALL)));
		// The paifang stands 4 clear of the Aula's eave (the north duty post at (0, -5) stays open under it); the two
		// censer-dings flank it on the plaza, clear of the vats, lanterns and rocks.
		b.add(Component.of("t6_paifang", 6).kind("paifang").name("paifang").group(Group.AXIS)
				.add(new Paifang(0, -4, p).bays(1).roofs(1).material(Paifang.Material.WHITE_STONE)));
		b.add(Component.of("t6_dings", 6).kind("ding").name("dings").group(Group.GARDEN)
				.add(Stamp.smallDing(-5, -5, p)).add(Stamp.smallDing(5, -5, p)));
		b.add(ornament(6).add(new Ornament(6, p).banners(-7, 11, 7, 11)));

		// ============================================================ T7 Lapangan Latihan
		b.add(Component.of("t7_drill", 7).kind("drill").name("drill_ground").group(Group.SIDE)
				.add(Stamp.targetPost(-7, 1, Direction.EAST, p)).add(Stamp.targetPost(-7, 4, Direction.EAST, p)).add(Stamp.targetPost(-7, 7, Direction.EAST, p))
				.add(Stamp.targetPost(7, 1, Direction.WEST, p)).add(Stamp.targetPost(7, 4, Direction.WEST, p)).add(Stamp.targetPost(7, 7, Direction.WEST, p))
				.add(Stamp.bellFrame(-6, 12, Direction.NORTH, Lv.at(1))).add(Stamp.drum(6, 12, Lv.at(1)))
				.add(Rock.scholar(-7, -6, p, 3)).add(Rock.scholar(7, -6, p, 3))
				.add(new LanternPost(LanternPost.Style.STONE, p).pair(3, 9).pair(3, 11))
				.add(Marks.anchor("arena", 0, 4, p)));
		b.add(ornament(7).add(new Ornament(7, p).lanterns(LanternPost.Style.HANGING, -12, 13, 12, 13)));

		// ============================================================ T8 Pagoda dan Leluhur
		b.add(Component.of("t8_pagoda", 8).kind("pagoda").name("pagoda").group(Group.MAIN)
				.add(new Terrace(-20, -31, -13, -20, rear, p).style(Terrace.Style.STONE).opening(-13, -22, 1))
				.add(new Pagoda(-17, -26, rear).floors(5, new int[]{2, 2, 2, 1, 1}, new int[]{5, 4, 3, 3, 3}).overhangs(3, 2, 2, 2, 2).plinthMargin(1)));
		b.add(Component.of("t8_ancestral", 8).kind("hall").name("ancestral_hall").group(Group.SIDE)
				.add(new Hall(17, -23).site(rear).front(Direction.SOUTH).bays(2, 2, 2).depth(3).pillar(4).platform(1).rank(Rank.C)
						.roof(RoofBuilder.Type.XIESHAN, 1).overhang(1).porch(0).stepsInset(1).interior(Interiors.Preset.ANCESTRAL).doors(Hall.Doors.OPEN)));
		b.add(Component.of("t8_meditation", 8).kind("pavilion").name("meditation_pavilion").group(Group.GARDEN)
				.add(new QiSeat(-11, -12, p, 1)));
		b.add(Component.of("t8_gables", 8).kind("gables").name("horse_head_gables").group(Group.SIDE)
				.add(Stamp.horseHeadGable(16, 0, Direction.SOUTH, 5, 4, 3)).add(Stamp.horseHeadGable(16, 10, Direction.SOUTH, 5, 4, 3))
				.add(Stamp.horseHeadGable(16, -10, Direction.SOUTH, 5, 4, 3)).add(Stamp.horseHeadGable(16, -4, Direction.SOUTH, 5, 4, 3)));
		b.add(ornament(8).add(new Ornament(8, p).lanterns(LanternPost.Style.JADE, -12, -18, 13, -18)));

		// ============================================================ T9 Menara Langit Membara
		b.add(Component.of("t9_tower", 9).kind("tower").name("blazing_tower").group(Group.MAIN)
				.add(new TowerShaft(10, -4, p, 4, -5).roomSide(Direction.SOUTH)));
		b.add(Component.of("t9_finale", 9).kind("finale").name("finale").group(Group.GARDEN)
				.add(Marks.petals(-6, 5, 6, 11, p, 12))
				.add(new LanternPost(LanternPost.Style.PALACE, p).at(-9, -9, Direction.EAST).at(9, -9, Direction.WEST)));
		b.add(ornament(9).add(new Ornament(9, p).flowers(-12, 14, 12, 14)));
		return b.build();
	}

	private static Component.Builder ornament(int tier) {
		return Component.of("t" + tier + "_ornament", tier).kind("ornament").name("ornament_" + tier).group(Group.ORNAMENT);
	}

	private T3SecondRate() {
	}
}
