package dev.kultivator.sect.build.blueprint.templates;

import dev.kultivator.sect.build.blueprint.BambooGrove;
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
import dev.kultivator.sect.build.blueprint.Paifang;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Paving;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Pond;
import dev.kultivator.sect.build.blueprint.Rank;
import dev.kultivator.sect.build.blueprint.Rock;
import dev.kultivator.sect.build.blueprint.RoofBuilder;
import dev.kultivator.sect.build.blueprint.Stamp;
import dev.kultivator.sect.build.blueprint.TemplateSpec;
import dev.kultivator.sect.build.blueprint.TerrainZone;
import dev.kultivator.sect.build.blueprint.TowerShaft;
import dev.kultivator.sect.build.blueprint.Tree;
import dev.kultivator.sect.build.blueprint.Wall;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

/**
 * Template 1, Sekte Tak Berperingkat / Unranked Sect (DESIGN 21.1, 21.3, 21.4): a 25×29 courtyard hermitage (siheyuan) at
 * plaza level, the Aula Pendiri (3-bay rank C xieshan in the plan's glaze) closing the north side, a dorm wing west, the
 * Scripture wing east, the Dapur Api and the alchemy room in the south corners, a bamboo fence that becomes a white
 * plaster wall at T4, the Bagua disc around the lodestone at (0, 2) at T5, the gilding and a 1-bay stone paifang at T6, and
 * a 3-room Blazing cellar under the hall at T9.
 * <p>
 * Sources (DESIGN 21.1): <i>Renegade Immortal</i>, Er Gen's author's note: the Xuan Dao Sect is a small, unranked sect and
 * the Heng Yue Sect fell to the bottom grade; for the founding feel, <i>Battle Through the Heavens</i>: Pan Men founded in
 * the Inner Academy. The hero plum is <i>Return of the Mount Hua Sect</i>'s plum blossom. Duty stations (DESIGN 21.2):
 * <i>Renegade Immortal</i> (the water vats), <i>A Record of a Mortal's Journey</i> ch. 150-151 (the Hundred Medicine
 * Garden), <i>A Will Eternal</i> ch. 2 and 20-26 (the Ovens, the Spirit Tail chickens). Layout: art direction.
 */
public final class T1Unranked {

	public static TemplateSpec build() {
		Lv p = Lv.PLAZA;
		TemplateSpec.Builder b = TemplateSpec.builder(1).footprint(-12, 12, -16, 12).envelope(14, 14).relief(10).earthwork(3625)
				.disc(2, 4).territory(24).towerRooms(3).qiTier(1).gateAltars(0).planVersion(1)
				.zone("plaza", -11, -15, 11, 11, 0);

		// The Aula Pendiri: core 13×7 (pillars x ±3, ±6; z -14..-8), platform x -7..7 z -15..-7, eaves x -8..8 z -16..-6.
		Hall aula = new Hall(0, -11).bays(3, 6, 3).depth(7).pillar(5).platform(2).rank(Rank.C).roof(RoofBuilder.Type.XIESHAN, 1).porch(1)
				.stepsInset(1).interior(Interiors.Preset.MAIN_HALL).doors(Hall.Doors.CLOSED);

		// ============================================================ T1 Peletakan Prasasti
		b.add(Component.of("t1_axis", 1).kind("axis").name("axis").group(Group.AXIS)
				.add(new TerrainZone(-6, -5, 6, 11, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Paving(-2, -5, 2, 11, p, Paving.Pattern.AXIS).earth(false))
				.add(new Paving(-5, -5, 5, 3, p, Paving.Pattern.PLAZA).earth(false))
				.add(new Paving(-2, -2, 2, 2, p, Paving.Pattern.OCTAGON_INLAY).radius(2).earth(false))
				.add(new LanternPost(LanternPost.Style.STONE, p).at(-2, -2).at(2, -2).at(-2, 2).at(2, 2)));
		b.add(Component.of("t1_aula", 1).kind("hall").name("aula_pendiri").group(Group.MAIN)
				.add(new TerrainZone(-9, -15, 9, -6, p).edge(TerrainZone.Edge.SLOPE))
				.add(aula));
		b.add(Component.of("t1_well", 1).kind("well").name("water_vats").group(Group.SIDE)
				.add(Stamp.vat(-3, -5, p)).add(Stamp.vat(3, -5, p))
				.add(Marks.anchor("post_water", 2, -5, p)));
		b.add(Component.of("t1_gate", 1).kind("gate").name("gate_fence").group(Group.BOUNDARY)
				.add(new Wall(Wall.Style.BAMBOO_FENCE, p, 2).ring(-12, -16, 12, 12).gap(0, 12, 3))
				.add(Gates.innerGate(0, 11, p, 2, false))
				.add(new LanternPost(LanternPost.Style.RED_POST, p).pair(3, 9))
				.add(Marks.anchor("arrival", 0, 8, p)));
		b.add(Component.of("t1_hero", 1).kind("tree").name("hero_tree").group(Group.GARDEN)
				// Its plot is the strip between the Scripture wing (eave at z 6, from y 5) and the kitchen (z 8): branches grow
				// west along it and stay under the eave, never into a later building (DESIGN 21.10: 3 cells from wooden walls).
				.add(new Tree(Tree.Kind.PLUM, 10, 7, p).hero(true).force(true).within(7, 6, 11, 7).maxHeight(5)));
		b.add(ornament(1).add(new Ornament(1, p).flowers(-5, 3, 5, 3))
				// Review views (uji sekte kamera): the gateway, the court toward the hero plum, the aerial.
				.add(Marks.camera(1, 0, 10, p, 0, -11, Lv.at(5))).add(Marks.camera(2, -5, 8, p, 10, 7, Lv.at(3)))
				.add(Marks.camera(3, 11, 11, Lv.at(22), 0, -6, p)));

		// ============================================================ T2 Api Dapur dan Kebun Obat
		b.add(Component.of("t2_dorm", 2).kind("hall").name("dorm").group(Group.SIDE)
				.add(new TerrainZone(-11, -4, -6, 6, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(-9, 1).front(Direction.EAST).bays(2, 4, 2).depth(5).pillar(4).platform(1).rank(Rank.A).roof(RoofBuilder.Type.XIESHAN, 1)
						.overhang(1).porch(0).stepsInset(1).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN)));
		b.add(Component.of("t2_kitchen", 2).kind("hall").name("kitchen").group(Group.SIDE)
				.add(new TerrainZone(5, 9, 11, 11, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(8, 10).front(Direction.NORTH).bays(2, 2, 2).depth(3).pillar(4).platform(1).rank(Rank.A).roof(RoofBuilder.Type.XIESHAN, 1)
						.overhang(1).porch(0).interior(Interiors.Preset.KITCHEN).doors(Hall.Doors.NONE)));
		b.add(Component.of("t2_garden", 2).kind("garden").name("herb_garden").group(Group.GARDEN)
				.add(Grounds.herbBeds(-11, 7, -7, 7, p))
				// Beside the hall's east flank: the strip by the plum is only one cell deep between the two wings' platforms.
				.add(Stamp.coop(9, -7, Direction.WEST, p))
				// Both trees keep their crowns between the wall and the wings' eaves.
				.add(new Tree(Tree.Kind.GINKGO, -10, -5, p).force(true).within(-11, -9, -8, -5))
				.add(new Tree(Tree.Kind.PINE, 10, -5, p).force(true).within(8, -9, 11, -5)));
		b.add(ornament(2).add(new Ornament(2, p).lanterns(LanternPost.Style.JADE, -5, 6, 5, 6)));

		// ============================================================ T3 Paviliun Kitab
		b.add(Component.of("t3_scripture", 3).kind("hall").name("scripture").group(Group.SIDE)
				.add(new TerrainZone(6, -4, 11, 6, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(9, 1).front(Direction.WEST).bays(2, 4, 2).depth(5).pillar(4).platform(1).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1)
						.overhang(1).porch(0).stepsInset(1).interior(Interiors.Preset.LIBRARY, 14).doors(Hall.Doors.NONE)));
		b.add(Component.of("t3_plaza", 3).kind("paving").name("plaza").group(Group.AXIS)
				.add(new Paving(-6, -5, -3, 7, p, Paving.Pattern.PLAZA))
				.add(new Paving(3, -5, 6, 7, p, Paving.Pattern.PLAZA))
				.add(new Paving(-2, 4, 2, 7, p, Paving.Pattern.AXIS)));
		b.add(ornament(3).add(new Ornament(3, p).flowers(-6, 7, 6, 7)));

		// ============================================================ T4 Tembok dan Air
		b.add(Component.of("t4_alchemy", 4).kind("hall").name("alchemy").group(Group.SIDE)
				.add(new TerrainZone(-11, 9, -5, 11, p).edge(TerrainZone.Edge.SLOPE))
				.add(new Hall(-8, 10).front(Direction.NORTH).bays(2, 2, 2).depth(3).pillar(4).platform(1).rank(Rank.B).roof(RoofBuilder.Type.XIESHAN, 1)
						.overhang(1).porch(0).interior(Interiors.Preset.ALCHEMY, 1).doors(Hall.Doors.NONE)));
		b.add(Component.of("t4_wall", 4).kind("wall").name("white_wall").group(Group.BOUNDARY)
				.add(new Wall(Wall.Style.WHITE_GARDEN, p, 4).ring(-12, -16, 12, 12).gap(0, 12, 3).drips(0)));
		// The basins are sunk into the T3 plaza: demolish its paving (and any fill) over the water first, so the lotus
		// leaves and lily pads replace the paving in game instead of leaving the water hidden under it.
		b.add(Component.of("t4_basins", 4).kind("pond").name("lotus_basins").group(Group.GARDEN)
				.add(Marks.demolish(-5, -3, -3, -4, -1, -2)).add(Marks.demolish(4, -3, -3, 5, -1, -2))
				.add(Pond.basin(-5, -3, -4, -2, p)).add(Pond.basin(4, -3, 5, -2, p)));
		b.add(ornament(4).add(new Ornament(4, p).flowers(-7, -5, 7, -5)));

		// ============================================================ T5 Formasi Agung
		b.add(Component.of("t5_disc", 5).kind("formation").name("disc").group(Group.AXIS)
				.add(new FormationDisc(2, 4, p).clip(-6, -5, 6, 7).avoid(-6, -4, -3, -1).avoid(3, -4, 6, -1)));
		b.add(ornament(5).add(new Ornament(5, p).lanterns(LanternPost.Style.JADE, -6, 2, 6, 2)));

		// ============================================================ T6 Aula Utama (the gilding tier of template 1)
		b.add(Component.of("t6_gilding", 6).kind("gilding").name("gilding").group(Group.MAIN).add(aula.gilding()));
		b.add(Component.of("t6_paifang", 6).kind("paifang").name("paifang").group(Group.BOUNDARY)
				.add(Marks.demolish(-5, 6, 10, 5, 7, 12))
				.add(Marks.demolish(-3, 0, 10, 3, 5, 11))
				.add(Marks.demolish(-3, 0, 12, 3, 0, 12))
				.add(new Paifang(0, 11, p).bays(1).roofs(1).material(Paifang.Material.WHITE_STONE)));
		// Two small censer-dings beside the hall steps: the disc's KAN band and the axis walk stay clear.
		b.add(Component.of("t6_ding", 6).kind("ding").name("ding").group(Group.GARDEN).add(Stamp.smallDing(-4, -6, p)).add(Stamp.smallDing(4, -6, p)));
		b.add(ornament(6).add(new Ornament(6, p).banners(-4, 10, 4, 10)));

		// ============================================================ T7 Lapangan Latihan
		b.add(Component.of("t7_court", 7).kind("drill").name("court_targets").group(Group.SIDE)
				.add(Stamp.targetPost(-11, 8, Direction.SOUTH, p)).add(Stamp.targetPost(-5, 8, Direction.SOUTH, p))
				.add(Stamp.targetPost(5, 8, Direction.SOUTH, p)).add(Stamp.targetPost(11, 8, Direction.SOUTH, p))
				.add(new LanternPost(LanternPost.Style.STONE, p).pair(6, 6))
				.add(new LanternPost(LanternPost.Style.PAGODA, p).pair(3, -6))
				.add(Rock.scholar(-10, -9, p, 3)).add(Rock.scholar(10, -9, p, 4))
				.add(Marks.anchor("arena", 0, 7, p)));
		b.add(ornament(7).add(new Ornament(7, p).flowers(-4, 7, 4, 7)));

		// ============================================================ T8 Pagoda dan Leluhur
		b.add(Component.of("t8_ancestors", 8).kind("altar").name("ancestral_altar").group(Group.MAIN)
				.add(Stamp.of("altar").layer("BBB").layer("LCL")
						.legend('B', Palette.st(Blocks.POLISHED_BLACKSTONE))
						.legend('L', Palette.lantern(Blocks.SOUL_LANTERN, false))
						.legend('C', Palette.candles(Palette.candle(DyeColor.RED), 3, true))
						.phase(Phase.INTERIOR)
						.at(4, -13, Direction.SOUTH, Lv.at(2)))
				.add(new BambooGrove(-11, -15, -10, -12, p).maxHeight(7))
				.add(new BambooGrove(10, -15, 11, -12, p).maxHeight(7))
				.add(Stamp.bonsai(-5, -6, p, 1)).add(Stamp.bonsai(5, -6, p, 2)));
		b.add(ornament(8).add(new Ornament(8, p).lanterns(LanternPost.Style.JADE, -9, -4, 9, -4)));

		// ============================================================ T9 Menara Langit Membara
		b.add(Component.of("t9_tower", 9).kind("tower").name("blazing_cellar").group(Group.MAIN)
				.add(new TowerShaft(-4, -11, Lv.at(2), 3, -5).roomSide(Direction.SOUTH)));
		b.add(Component.of("t9_finale", 9).kind("finale").name("finale").group(Group.GARDEN)
				.add(Marks.petals(-6, 4, 6, 7, p, 14))
				.add(new LanternPost(LanternPost.Style.PALACE, p).at(-6, -5, Direction.EAST).at(6, -5, Direction.WEST)));
		b.add(ornament(9).add(new Ornament(9, p).flowers(-8, -5, 8, -5)));
		return b.build();
	}

	private static Component.Builder ornament(int tier) {
		return Component.of("t" + tier + "_ornament", tier).kind("ornament").name("ornament_" + tier).group(Group.ORNAMENT);
	}

	private T1Unranked() {
	}
}
