package dev.kultivator.sect.build.blueprint.templates;

import dev.kultivator.sect.build.blueprint.BambooGrove;
import dev.kultivator.sect.build.blueprint.Bridge;
import dev.kultivator.sect.build.blueprint.Component;
import dev.kultivator.sect.build.blueprint.Component.Group;
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
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

/**
 * Template 2, Sekte Kelas Tiga / Third-Rate Sect (DESIGN 21.1, 21.3, 21.4): 33×39, two courts climbing a slope. The front
 * court holds the stele and the Bagua disc (lodestone at (0, 6), ring r 4) between the dorm (east) and the Scripture
 * Pavilion (west), with the kitchen and the herb garden in the front corners; the Aula Pendiri (rank B) closes the court on
 * the axis; behind it three terraces rise to +3 (the east one with the alchemy hall and the drill yard at T4, the main hall
 * in the middle at T6, the 3-floor square pagoda NW at T8, with the lotus pond and its flat bridge in front of it). A
 * wooden gate and fence become a white wall with a gate house at T4; a red-wood paifang stands before the Aula at T6.
 * <p>
 * Sources (DESIGN 21.1): wuxia/xianxia genre convention of third-rate sects (Wuxiaworld translations of <i>Yuan's
 * Ascension</i> and <i>Everlasting</i> ch. 73): a genre label, not one work's ladder. Rank by height: <i>I Shall Seal the
 * Heavens</i> ch. 2-3 (the Reliance Sect's upper courts); pagoda name after <i>Soul Land</i>; hero tree, duties and
 * tower as in DESIGN 21.2/21.3. Layout: art direction.
 */
public final class T2ThirdRate {

	public static TemplateSpec build() {
		Lv p = Lv.PLAZA;
		Lv rear = Lv.zone("rear");
		TemplateSpec.Builder b = TemplateSpec.builder(2).footprint(-16, 16, -24, 14).envelope(24, 14).relief(14).earthwork(6435)
				.disc(6, 4).territory(32).towerRooms(3).qiTier(1).gateAltars(0).planVersion(1)
				.zone("plaza", -15, -12, 15, 12, 0)
				.zone("rear", -15, -24, 15, -13, 3, 2, 4, "plaza", 2);

		Hall aula = new Hall(0, -9).bays(3, 4, 3).depth(5).pillar(5).platform(1).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1).overhang(1)
				.porch(0).stepsInset(1).doors(Hall.Doors.OPEN).gableField(Palette.st(Palette.PILLAR));

		// ============================================================ T1 Peletakan Prasasti
		b.add(Component.of("t1_axis", 1).kind("axis").name("axis").group(Group.AXIS)
				.add(new TerrainZone(-7, -5, 7, 11, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Paving(-2, -5, 2, 11, p, Paving.Pattern.AXIS).earth(false))
				.add(new Paving(-5, -4, 5, 3, p, Paving.Pattern.PLAZA).earth(false))
				.add(new Paving(-2, -2, 2, 2, p, Paving.Pattern.OCTAGON_INLAY).radius(2).earth(false))
				.add(new LanternPost(LanternPost.Style.STONE, p).at(-2, -2).at(2, -2).at(-2, 2).at(2, 2)));
		b.add(Component.of("t1_aula", 1).kind("hall").name("aula_pendiri").group(Group.MAIN)
				.add(new TerrainZone(-7, -12, 7, -6, p).edge(TerrainZone.Edge.SLOPE))
				.add(aula));
		b.add(Component.of("t1_well", 1).kind("well").name("water_vats").group(Group.SIDE)
				.add(Pond.basin(-5, -1, -5, -1, p).plants(false))
				.add(Stamp.of("well_frame").layer("W.W").layer("W.W").layer("sSs")
						.legend('W', Palette.st(Blocks.ANDESITE_WALL))
						.legend('S', Palette.st(Blocks.DEEPSLATE_TILES))
						.legend('s', Palette.slab(Blocks.DEEPSLATE_TILE_SLAB, false))
						.phase(Phase.FRAME)
						.at(-5, -1, Direction.SOUTH, p))
				.add(Stamp.vat(-4, -3, p)).add(Stamp.vat(4, -3, p))
				.add(Marks.anchor("post_water", -5, -3, p)));
		b.add(Component.of("t1_gate", 1).kind("gate").name("gate_fence").group(Group.BOUNDARY)
				.add(new Wall(Wall.Style.BAMBOO_FENCE, p, 2).line(-15, 12, 15, 12).line(-15, -12, -15, 12).line(15, -12, 15, 12).gap(0, 12, 3))
				.add(Gates.innerGate(0, 12, p, 2, false))
				.add(new LanternPost(LanternPost.Style.RED_POST, p).pair(4, 10))
				.add(Marks.anchor("arrival", 0, 8, p)));
		b.add(Component.of("t1_hero", 1).kind("tree").name("hero_tree").group(Group.GARDEN)
				// Its plot is the south-west court: clear of the Scripture Pavilion (T3), the wall (T4) and the gate hall.
				.add(new Tree(Tree.Kind.PINE, -8, 10, p).hero(true).force(true).within(-10, 8, -7, 11)));
		b.add(ornament(1).add(new Ornament(1, p).flowers(-6, 1, 6, 1))
				// Review views: the gate, across the lotus pond to the pagoda, the aerial.
				.add(Marks.camera(1, 0, 11, p, 0, -18, Lv.at(7))).add(Marks.camera(2, -4, -6, p, -12, -19, Lv.at(12)))
				.add(Marks.camera(3, 15, 13, Lv.at(26), 0, -8, p)));

		// ============================================================ T2 Api Dapur dan Kebun Obat
		b.add(Component.of("t2_dorm", 2).kind("hall").name("dorm").group(Group.SIDE)
				.add(new TerrainZone(8, -5, 14, 7, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(11, 1).front(Direction.WEST).bays(3, 4, 3).depth(5).pillar(4).platform(1).rank(Rank.A).roof(RoofBuilder.Type.XIESHAN, 1)
						.overhang(1).porch(0).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN)));
		b.add(Component.of("t2_kitchen", 2).kind("hall").name("kitchen").group(Group.SIDE)
				.add(new TerrainZone(6, 9, 14, 12, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(10, 11).front(Direction.NORTH).bays(2, 2, 2).depth(3).pillar(4).platform(1).rank(Rank.A).roof(RoofBuilder.Type.XIESHAN, 1)
						.overhang(1).porch(0).interior(Interiors.Preset.KITCHEN).doors(Hall.Doors.NONE)));
		b.add(Component.of("t2_garden", 2).kind("garden").name("herb_garden").group(Group.GARDEN)
				.add(Grounds.herbBeds(-13, 8, -8, 10, p))
				.add(Stamp.coop(12, -8, Direction.WEST, p))
				.add(new Tree(Tree.Kind.PLUM, -14, 10, p).force(true).within(-14, 8, -12, 11))
				// The pine keeps its crown east of the T7 bell pavilion. (The ginkgo moved out of the pond: the T4 bridge lands there.)
				.add(new Tree(Tree.Kind.PINE, 14, -9, p).force(true).within(13, -12, 14, -6)));
		b.add(ornament(2).add(new Ornament(2, p).lanterns(LanternPost.Style.JADE, -7, -2, 7, -2)));

		// ============================================================ T3 Paviliun Kitab
		b.add(Component.of("t3_scripture", 3).kind("hall").name("scripture").group(Group.SIDE)
				.add(new TerrainZone(-15, -5, -7, 7, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(-11, 1).front(Direction.EAST).bays(3, 4, 3).depth(7).pillar(5).platform(1).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1)
						.overhang(1).porch(0).interior(Interiors.Preset.LIBRARY, 16).doors(Hall.Doors.NONE)));
		b.add(Component.of("t3_plaza", 3).kind("paving").name("plaza").group(Group.AXIS)
				.add(new Paving(-6, -5, -3, 11, p, Paving.Pattern.PLAZA))
				.add(new Paving(3, -5, 6, 11, p, Paving.Pattern.PLAZA)));
		b.add(ornament(3).add(new Ornament(3, p).flowers(-7, 8, 7, 8)));

		// ============================================================ T4 Tembok dan Air
		b.add(Component.of("t4_alchemy", 4).kind("hall").name("alchemy").group(Group.SIDE)
				.add(new Terrace(10, -23, 14, -13, rear, p).style(Terrace.Style.STONE).opening(12, -13, 1))
				.add(new StairFlight(12, -10, Direction.NORTH, p, rear).width(3))
				.add(new Hall(12, -20).front(Direction.SOUTH).bays(1, 2, 1).depth(3).pillar(4).platform(1).site(rear).rank(Rank.B)
						.roof(RoofBuilder.Type.XIESHAN, 1).overhang(1).porch(0).interior(Interiors.Preset.ALCHEMY, 1).doors(Hall.Doors.NONE))
				.add(new Paving(10, -16, 14, -14, rear, Paving.Pattern.PLAZA).earth(false)));
		b.add(Component.of("t4_wall", 4).kind("wall").name("white_wall").group(Group.BOUNDARY)
				.add(Marks.demolish(-5, 5, 11, 5, 7, 13))
				.add(Marks.demolish(-3, 0, 11, 3, 4, 13))
				.add(new Wall(Wall.Style.WHITE_GARDEN, p, 4).line(-15, 12, 15, 12).line(-15, -12, -15, 12).line(15, -12, 15, 12).gap(0, 12, 3).drips(0))
				.add(new Wall(Wall.Style.WHITE_GARDEN, rear, 4).line(-15, -24, 15, -24).line(-15, -24, -15, -13).line(15, -24, 15, -13).drips(0))
				.add(new Hall(0, 12).bays(2, 4, 2).depth(3).pillar(4).platform(1).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1).overhang(1).porch(0)
						.stepsInset(1).passage(true)));
		b.add(Component.of("t4_pond", 4).kind("pond").name("lotus_pond").group(Group.GARDEN)
				.add(Pond.pond(-11, -10, 2, 1, p))
				.add(new Bridge(Bridge.Type.FLAT, -14, -10, -8, -10, p).width(1))
				.add(Rock.cluster(-8, -7, p, 1)));
		b.add(ornament(4).add(new Ornament(4, p).flowers(-7, -11, 10, -9)));

		// ============================================================ T5 Formasi Agung
		b.add(Component.of("t5_disc", 5).kind("formation").name("disc").group(Group.AXIS)
				.add(new FormationDisc(6, 4, p).clip(-7, -1, 7, 9)));
		b.add(ornament(5).add(new Ornament(5, p).lanterns(LanternPost.Style.STONE, -7, 6, 7, 6)));

		// ============================================================ T6 Aula Utama
		Hall mainHall = new Hall(0, -18).site(rear).bays(3, 4, 3).depth(7).pillar(5).platform(2).rank(Rank.D).roof(RoofBuilder.Type.XIESHAN, 1)
				.porch(1).stepsInset(1).interior(Interiors.Preset.MAIN_HALL).gableField(Palette.st(Palette.PILLAR));
		b.add(Component.of("t6_main_hall", 6).kind("hall").name("main_hall").group(Group.MAIN)
				.add(new Terrace(-9, -23, 9, -13, rear, p).style(Terrace.Style.STONE).opening(0, -13, 1).opening(9, -15, 1).opening(-9, -15, 1)
						.opening(8, -13, 1))
				.add(new StairFlight(8, -10, Direction.NORTH, p, rear).width(3))
				.add(mainHall));
		b.add(Component.of("t6_paifang", 6).kind("paifang").name("paifang").group(Group.AXIS)
				.add(new Paifang(0, -3, p).bays(1).roofs(3).material(Paifang.Material.RED_WOOD)));
		// Two small censer-dings on the forecourt flanks: the stele's four faces and the paifang opening stay free.
		b.add(Component.of("t6_ding", 6).kind("ding").name("ding").group(Group.GARDEN).add(Stamp.smallDing(-5, -5, p)).add(Stamp.smallDing(5, -5, p)));
		b.add(ornament(6).add(new Ornament(6, p).banners(-6, -5, 6, -5)));

		// ============================================================ T7 Lapangan Latihan
		// Four targets on the plaza court facing the axis (the alchemy forecourt stays free), a small bell pavilion in the
		// east court with the bell hanging from a centre beam.
		b.add(Component.of("t7_drill", 7).kind("drill").name("drill_ground").group(Group.SIDE)
				.add(Stamp.targetPost(-6, 3, Direction.EAST, p)).add(Stamp.targetPost(-6, 5, Direction.EAST, p))
				.add(Stamp.targetPost(6, 3, Direction.WEST, p)).add(Stamp.targetPost(6, 5, Direction.WEST, p))
				.add(new Pavilion(9, -8, p).size(3).rank(Rank.B).entry(Direction.SOUTH))
				.add(Stamp.of("pavilion_bell").layer(" ").layer(" ").layer(" ").layer("B").layer("W")
						.legend('B', Palette.bell(Direction.SOUTH, true))
						.legend('W', Palette.st(Palette.BEAM))
						.phase(Phase.INTERIOR)
						.at(9, -8, Direction.SOUTH, p))
				.add(Stamp.flagPole(-6, 0, Direction.SOUTH, p, 5)).add(Stamp.flagPole(6, 0, Direction.SOUTH, p, 5))
				.add(Marks.anchor("arena", 0, 4, p)));
		b.add(ornament(7).add(new Ornament(7, p).lanterns(LanternPost.Style.HANGING, -7, -6, 7, -6)));

		// ============================================================ T8 Pagoda dan Leluhur
		b.add(Component.of("t8_pagoda", 8).kind("pagoda").name("pagoda").group(Group.MAIN)
				.add(new Terrace(-14, -23, -10, -13, rear, p).style(Terrace.Style.STONE))
				// A convex taper (DESIGN 21.7: never a straight stack): a broad first floor, then two narrower ones.
				.add(new Pagoda(-12, -19, rear).floors(3, new int[]{2, 1, 1}, new int[]{5, 4, 4}).overhangs(2, 2, 1).plinthMargin(1)));
		// The shrine stands beside the pagoda facing east (not as a wall before its door); the soul lantern hangs from the
		// beam and the candles stand on the altar block.
		b.add(Component.of("t8_shrine", 8).kind("shrine").name("ancestral_shrine").group(Group.SIDE)
				.add(Stamp.of("shrine").layer("PBP").layer("PCP").layer("PLP").layer("WWW").layer("sSs")
						.legend('P', Palette.st(Palette.PILLAR_THIN))
						.legend('B', Palette.st(Blocks.POLISHED_BLACKSTONE))
						.legend('L', Palette.lantern(Blocks.SOUL_LANTERN, true))
						.legend('C', Palette.candles(Palette.candle(DyeColor.RED), 2, true))
						.legend('W', Palette.st(Palette.BEAM))
						.legend('S', Palette.st(Blocks.DEEPSLATE_TILES))
						.legend('s', Palette.slab(Blocks.DEEPSLATE_TILE_SLAB, false))
						.phase(Phase.WALL)
						.at(-14, -14, Direction.EAST, rear))
				.add(new BambooGrove(14, -7, 14, -6, p).maxHeight(8)));
		b.add(ornament(8).add(new Ornament(8, p).lanterns(LanternPost.Style.JADE, -13, -6, 13, -5)));

		// ============================================================ T9 Menara Langit Membara
		b.add(Component.of("t9_tower", 9).kind("tower").name("blazing_cellar").group(Group.MAIN)
				.add(new TowerShaft(-3, -9, Lv.at(1), 3, -5).roomSide(Direction.SOUTH)));
		b.add(Component.of("t9_finale", 9).kind("finale").name("finale").group(Group.GARDEN)
				.add(Marks.petals(-6, 5, 6, 9, p, 12))
				// Two late maples framing the south court, clear of the gate hall, the kitchen and the targets.
				.add(new Tree(Tree.Kind.MAPLE, -4, 8, p).force(true).within(-5, 6, -3, 8))
				.add(new Tree(Tree.Kind.MAPLE, 4, 8, p).force(true).within(3, 6, 6, 8))
				.add(new LanternPost(LanternPost.Style.PALACE, p).at(-4, 9, Direction.EAST).at(4, 9, Direction.WEST)));
		b.add(ornament(9).add(new Ornament(9, p).flowers(-6, -6, 6, -6)));
		return b.build();
	}

	private static Component.Builder ornament(int tier) {
		return Component.of("t" + tier + "_ornament", tier).kind("ornament").name("ornament_" + tier).group(Group.ORNAMENT);
	}

	private T2ThirdRate() {
	}
}
