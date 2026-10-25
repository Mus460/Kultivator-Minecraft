package dev.kultivator.sect.build.blueprint.awan;

import dev.kultivator.sect.build.blueprint.Component;
import dev.kultivator.sect.build.blueprint.Component.Group;
import dev.kultivator.sect.build.blueprint.Ctx;
import dev.kultivator.sect.build.blueprint.FormationDisc;
import dev.kultivator.sect.build.blueprint.Grounds;
import dev.kultivator.sect.build.blueprint.Hall;
import dev.kultivator.sect.build.blueprint.Interiors;
import dev.kultivator.sect.build.blueprint.LanternPost;
import dev.kultivator.sect.build.blueprint.Lv;
import dev.kultivator.sect.build.blueprint.Marks;
import dev.kultivator.sect.build.blueprint.Ornament;
import dev.kultivator.sect.build.blueprint.Pagoda;
import dev.kultivator.sect.build.blueprint.Palette;
import dev.kultivator.sect.build.blueprint.Part;
import dev.kultivator.sect.build.blueprint.Paving;
import dev.kultivator.sect.build.blueprint.Phase;
import dev.kultivator.sect.build.blueprint.Pond;
import dev.kultivator.sect.build.blueprint.QiSeat;
import dev.kultivator.sect.build.blueprint.Rank;
import dev.kultivator.sect.build.blueprint.RoofBuilder;
import dev.kultivator.sect.build.blueprint.Sink;
import dev.kultivator.sect.build.blueprint.Stamp;
import dev.kultivator.sect.build.blueprint.TemplateSpec;
import dev.kultivator.sect.build.blueprint.TowerShaft;
import dev.kultivator.sect.build.blueprint.Waterfall;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * The Awan family of sky templates 11-19 (DESIGN 37.3.2, 37.4.5): every tier of A1-A9 from one parametric plan over its
 * {@link AwanLayout}. Ids {@code aw_t<tier>_<what>} are stable forever; the DESIGN 36 components keep their exact
 * {@code sx_*} ids and anchors so the living-sect systems read a sky sect as they read a ground one.
 * <ul>
 * <li><b>T1 Inti Awan</b>: the core island, the Cloud Throne plaza with the water vats, the Aula Pendiri, the red wooden
 * gate and rim fence, the Cloud Transfer node (inactive), the gate banners.</li>
 * <li><b>T2 Api Dapur dan Taman Gantung</b>: the plateau grows (Dataran), the dorm, the kitchen, the ancestral altar, the
 * Hanging Garden satellite and its jade bridge, the first trees.</li>
 * <li><b>T3 Paviliun Kitab dan Tangga Awan</b>: the library, the Cloud Stair from the ground, the Heart Stones at its foot,
 * the spirit beds, the paths.</li>
 * <li><b>T4 Tepi dan Air</b>: the plateau widens with the pagoda hill, its white balustrade, the garden pond and its real
 * little waterfall (A4+), the mist-waterfall clefts, the alchemy satellite, the spirit mine (A1-A6), the hanging roots.</li>
 * <li><b>T5 Formasi Agung dan Jaring Awan</b>: the Bagua disc, the four Cloud Net poles and the net node, the formation
 * banners, the gate altars (A6+), the arena satellite (A3+).</li>
 * <li><b>T6 Aula Utama</b>: the outer terraces down to the rim and its marble balustrade (the turtle head under the front on
 * A7-A9), the main hall (A1: the Aula gilded), the marble paifang, the jade lattice on the old bridges, the dings, the
 * groves.</li>
 * <li><b>T7 Latihan, Dermaga, Panggung Pedang</b>: the drill ground with bell and drum, the sword terrace, the docks, the
 * ferry stop, the arena stands.</li>
 * <li><b>T8 Pagoda dan Leluhur</b>: the pagoda on its hill, the ancestors' satellite with its hall and graves, the
 * cranes' dorm satellite (A5+), the Qi seat, the rim meditation pavilions.</li>
 * <li><b>T9 Menara dan Sembilan Langit</b>: the Blazing Tower under the Aula Pendiri, the Gate of the Nine Heavens, the
 * hanging crystals, the mine satellite (A7+), the finale.</li>
 * </ul>
 * Sumber: <i>Liezi</i> "Tang Wen" (Guixu, the turtles, the names of the five mountains); <i>Huainanzi</i> (Kunlun's tiers);
 * <i>Journey to the West</i> ch. 4 (the Southern Heavenly Gate). Layout and architecture are art direction.
 */
public final class AwanTemplates {

	/** Op cap of one component (DESIGN 37.4.7): bigger rings are split into quarters. */
	public static final int COMPONENT_OPS = 24_000;
	private static final String[] QUARTER = {"u", "s", "t", "b"};

	public static TemplateSpec build(int sky) {
		AwanLayout L = AwanLayout.of(sky);
		AwanTabel row = L.row;
		int n = row.n();
		int p = L.plaza;
		int pf = L.plazaFront;
		Lv plaza = Lv.PLAZA;
		int qiTier = n <= 3 ? 1 : n <= 6 ? 2 : 3;
		TemplateSpec.Builder b = TemplateSpec.builder(sky).sky(true).skyFloor(L.skyFloor).footprint(L.x0, L.x1, L.z0, L.z1)
				.envelope(L.above, L.below).relief(0).earthwork(0).disc(row.discD(), row.discR()).territory(L.territory).towerRooms(row.towerRooms())
				.qiTier(qiTier).gateAltars(row.gateAltars()).planVersion(1)
				.zone("plaza", -p, -5, p, pf, 0)
				.kaki(L.kaki.x0(), L.kaki.z0(), L.kaki.x1(), L.kaki.z1(), Math.min(-160, -(L.below + 12)), -383, -(L.below + 12));

		// The Aula Pendiri of the size class (eave outline = plot "aula").
		Hall aula = switch (L.kelas) {
			case 0 -> new Hall(0, -11).bays(1, 4, 1).depth(5).pillar(4).platform(1);
			case 1 -> new Hall(0, -11).bays(3, 6, 3).depth(7).pillar(5).platform(2);
			default -> new Hall(0, -11).bays(3, 3, 6, 3, 3).depth(7).pillar(5).platform(2);
		};
		aula.rank(Rank.C).roof(RoofBuilder.Type.XIESHAN, 1).porch(1).stepsInset(1).interior(Interiors.Preset.MAIN_HALL).doors(Hall.Doors.CLOSED);
		int aulaFloor = L.kelas == 0 ? 1 : 2;

		// ============================================================ T1 Inti Awan
		b.add(Component.of("aw_t1_inti", 1).kind("island").name("awan_inti").group(Group.AXIS, 0)
				.add(new PulauAwan("main", 0).gaya(PulauAwan.Gaya.TAMAN)));
		Component.Builder tahta = Component.of("aw_t1_tahta", 1).kind("throne").name("awan_tahta").group(Group.AXIS, 1)
				.add(new TahtaPrasasti(p, -5, pf));
		for (int i = 0; i < row.vats(); i++) {
			int x = (i / 2 + 1) * (i % 2 == 0 ? -1 : 1);
			tahta.add(Stamp.vat(x, -3, plaza));
		}
		tahta.add(Marks.anchor("post_water", 0, -3, plaza));
		b.add(tahta);
		b.add(Component.of("aw_t1_aula", 1).kind("hall").name("aula_pendiri").group(Group.MAIN).add(aula));
		b.add(Component.of("aw_t1_gerbang", 1).kind("gate").name("awan_gerbang").group(Group.BOUNDARY)
				.add(GerbangAwan.kayu(L.gateZ))
				.add(new PagarTepi("main", 0, PagarTepi.Gaya.KAYU)));
		AwanLayout.Box node = L.plot("formasi_awan");
		b.add(Component.of("aw_t1_formasi_awan", 1).kind("formation_node").name("awan_formasi").group(Group.SIDE)
				.add(AwanHias.pola(AwanHias.Pola.TRANSFER, node.cx(), node.cz(), plaza, "formasi_awan")));
		b.add(Component.of("sx_seragam_panji", 1).kind("panji").name("sekte_seragam_panji").group(Group.BOUNDARY)
				.add(pole("panji_gerbang_0", 5, L.gateZ - 1, plaza, false))
				.add(pole("panji_gerbang_1", -5, L.gateZ - 1, plaza, false)));
		b.add(ornament(1).add(new Ornament(1, plaza).flowers(-2, pf - 1, 2, pf - 1))
				.add(Marks.camera(1, 0, L.frontZ - 2, plaza, 0, -11, Lv.at(5)))
				.add(Marks.camera(2, L.x1 - 4, L.cz, Lv.at(18), 0, L.cz, plaza))
				.add(Marks.camera(3, 0, L.frontZ + 6, Lv.at(-20), 0, L.cz, Lv.at(-12))));

		// ============================================================ T2 Api Dapur dan Taman Gantung
		addRing(b, L, 1, 2, "aw_t2_dataran", "awan_dataran", new PagarTepi("main", 0, PagarTepi.Gaya.KAYU).bongkar(),
				new PagarTepi("main", 1, PagarTepi.Gaya.KAYU));
		b.add(Component.of("aw_t2_asrama", 2).kind("hall").name("dorm").group(Group.SIDE).add(asrama(L)));
		b.add(Component.of("aw_t2_dapur", 2).kind("hall").name("kitchen").group(Group.SIDE).add(dapur(L)));
		AwanLayout.Box altar = L.plot("altar");
		b.add(Component.of("sx_paviliun_altar", 2).kind("altar").name("sx_paviliun_altar").group(Group.SIDE)
				.add(altar(altar.cx(), altar.cz(), plaza)));
		satellite(b, L, AwanLayout.Fungsi.KEBUN, "aw_t2_satelit_kebun", "aw_t2_jembatan_kebun", true);
		b.add(Component.of("aw_t2_pohon", 2).kind("trees").name("awan_pohon").group(Group.GARDEN)
				.add(AwanHias.taman("main", 0, 11)).add(AwanHias.taman("main", 1, 9)));
		b.add(ornament(2).add(new Ornament(2, plaza).lanterns(LanternPost.Style.JADE, -(p - 1), 2, p - 1, 2)));

		// ============================================================ T3 Paviliun Kitab dan Tangga Awan
		b.add(Component.of("aw_t3_kitab", 3).kind("hall").name("scripture").group(Group.SIDE).add(kitab(L)).add(lectern(L)));
		b.add(Component.of("aw_t3_tangga_awan", 3).kind("cloud_stair").name("awan_tangga").group(Group.AXIS, 0).add(new TanggaAwan()));
		b.add(Component.of("sx_tangga_batu", 3).kind("stele").name("sx_tangga_batu").group(Group.ORNAMENT, -1).add(batuHati(L)));
		if (L.satellite(AwanLayout.Fungsi.KEBUN) != null) {
			b.add(Component.of("sx_kebun_petak", 3).kind("garden").name("sx_kebun_petak").group(Group.GARDEN)
					.add(new PulauSatelit(AwanLayout.Fungsi.KEBUN, PulauSatelit.Isi.PETAK)));
		}
		b.add(Component.of("aw_t3_plaza", 3).kind("paving").name("plaza").group(Group.AXIS, 1).add(paths(L)));
		b.add(ornament(3).add(new Ornament(3, plaza).flowers(-(p - 1), pf - 2, p - 1, pf - 2)));

		// ============================================================ T4 Tepi dan Air
		addRing(b, L, 2, 4, "aw_t4_pelebaran", "awan_pelebaran", new PagarTepi("main", 1, PagarTepi.Gaya.KAYU).bongkar(), null);
		b.add(Component.of("aw_t4_pagar", 4).kind("fence").name("awan_pagar").group(Group.BOUNDARY)
				.add(new PagarTepi("main", 2, PagarTepi.Gaya.BATU)).add(AwanHias.darat(2)));
		if (L.plot("kolam") != null) {
			b.add(Component.of("aw_t4_kolam", 4).kind("pond").name("awan_kolam").group(Group.GARDEN).add(kolam(L)));
		}
		Component.Builder emitor = Component.of("aw_t4_emitor", 4).kind("mist_fall").name("awan_emitor").group(Group.GARDEN);
		for (AwanLayout.Titik lip : L.takik) {
			emitor.add(AwanHias.takik(lip));
		}
		b.add(emitor);
		satellite(b, L, AwanLayout.Fungsi.ALKIMIA, "aw_t4_satelit_alkimia", "aw_t4_jembatan_alkimia", false);
		if (L.plot("tambang") != null) {
			AwanLayout.Box mine = L.plot("tambang");
			b.add(Component.of("sx_kebun_tambang", 4).kind("mine").name("sx_kebun_tambang").group(Group.SIDE)
					.add(new RonggaAkar(mine.cx(), mine.z1() - 1, Direction.SOUTH, plaza)));
		}
		b.add(Component.of("aw_t4_akar", 4).kind("roots").name("awan_akar").group(Group.GARDEN).add(AwanHias.akar("main", true)));
		b.add(ornament(4).add(new Ornament(4, plaza).lanterns(LanternPost.Style.STONE, -(p + 1), -1, p + 1, -1)));

		// ============================================================ T5 Formasi Agung dan Jaring Awan
		b.add(Component.of("aw_t5_disc", 5).kind("formation").name("disc").group(Group.AXIS)
				.add(new FormationDisc(row.discD(), row.discR(), plaza).clip(-p + 1, -2, p - 1, pf - 1)));
		Component.Builder net = Component.of("aw_t5_jaring", 5).kind("net").name("awan_jaring").group(Group.BOUNDARY);
		for (AwanLayout.Titik pole : L.jaring) {
			net.add(AwanHias.tiangJaring(pole));
		}
		AwanLayout.Box eye = L.plot("jaring_mata");
		net.add(AwanHias.pola(AwanHias.Pola.JARING, eye.cx(), eye.cz(), plaza, "jaring_mata"));
		b.add(net);
		b.add(Component.of("sx_seragam_panji_formasi", 5).kind("panji").name("sekte_seragam_panji_formasi").group(Group.GARDEN)
				.add(pole("panji_formasi_0", p - 1, 1, plaza, true)).add(pole("panji_formasi_1", -(p - 1), 1, plaza, true))
				.add(pole("panji_formasi_2", p - 1, pf - 3, plaza, true)).add(pole("panji_formasi_3", -(p - 1), pf - 3, plaza, true)));
		if (row.gateAltars() > 0) {
			Component.Builder altars = Component.of("aw_t5_altars", 5).kind("gate_altar").name("gate_altars").group(Group.BOUNDARY);
			for (int i = 0; i < row.gateAltars(); i++) {
				AwanLayout.Box box = L.plot("altar_gerbang_" + i);
				if (box != null) {
					altars.add(new dev.kultivator.sect.build.blueprint.templates.t7t9.GateAltar(box.cx(), box.cz(), Lv.at(L.mainLevel(box.cx(), box.cz()))));
				}
			}
			b.add(altars);
		}
		satellite(b, L, AwanLayout.Fungsi.ARENA, "aw_t5_satelit_arena", "aw_t5_jembatan_arena", false);
		b.add(ornament(5).add(new Ornament(5, plaza).lanterns(LanternPost.Style.JADE, -(p - 2), pf, p - 2, pf)));

		// ============================================================ T6 Aula Utama
		Kura kura = new Kura(n >= 7);
		addRing(b, L, 3, 6, "aw_t6_penuh", "awan_penuh", null, null, kura);
		b.add(Component.of("aw_t6_pagar", 6).kind("fence").name("awan_pagar").group(Group.BOUNDARY)
				.add(new PagarTepi("main", 3, PagarTepi.Gaya.MARMER)));
		if (L.hasMainHall) {
			b.add(Component.of("aw_t6_aula_utama", 6).kind("hall").name("main_hall").group(Group.MAIN).add(mainHall(L)));
		} else {
			b.add(Component.of("aw_t6_aula_utama", 6).kind("gilding").name("gilding").group(Group.MAIN).add(aula.gilding()));
		}
		// MAIN after the ring (-10..-7): it stands on ring 3, so never before it.
		b.add(Component.of("aw_t6_paifang", 6).kind("paifang").name("paifang").group(Group.MAIN, 0).add(GerbangAwan.marmer(L.frontZ - 4)));
		Component.Builder lattice = Component.of("aw_t6_jembatan", 6).kind("bridge").name("awan_jembatan").group(Group.GARDEN);
		boolean anyOld = false;
		for (AwanLayout.Jembatan bridge : L.bridges) {
			if (bridge.tier() < 6) {
				lattice.add(new JembatanGiok(bridge.id().substring("jembatan_".length())).kisi());
				anyOld = true;
			}
		}
		if (anyOld) {
			b.add(lattice);
		}
		Component.Builder dings = Component.of("aw_t6_dings", 6).kind("ding").name("ding").group(Group.GARDEN);
		for (int i = 0; i < row.dings(); i++) {
			AwanLayout.Box box = L.plot("ding_" + i);
			dings.add(Stamp.ding(box.cx(), box.cz(), plaza, i == 0));
		}
		b.add(dings);
		b.add(Component.of("aw_t6_taman", 6).kind("trees").name("awan_taman").group(Group.GARDEN)
				.add(AwanHias.taman("main", 2, 10)).add(AwanHias.taman("main", 3, 7)));
		b.add(ornament(6).add(new Ornament(6, plaza).banners(-(p + 1), pf - 1, p + 1, pf - 1)));

		// ============================================================ T7 Latihan, Dermaga, Panggung Pedang
		AwanLayout.Box drill = L.plot("latihan");
		Component.Builder latihan = Component.of("aw_t7_latihan", 7).kind("drill").name("court_targets").group(Group.SIDE)
				.add(Grounds.drillGround(drill.x0(), drill.z0(), drill.x1(), drill.z1(), plaza, row.targets(), true));
		AwanLayout.Box bell = L.plot("lonceng");
		if (bell != null) {
			latihan.add(Stamp.bellFrame(bell.cx(), bell.cz(), Direction.SOUTH, plaza));
			if (row.drum()) {
				latihan.add(Stamp.drum(bell.cx() + 2, bell.cz() + 2, plaza));
			}
		}
		b.add(latihan);
		b.add(Component.of("aw_t7_panggung_pedang", 7).kind("sword_terrace").name("awan_panggung_pedang").group(Group.SIDE)
				.add(AwanHias.panggung(L.panggung, L.kelas == 0 ? 5 : 7)));
		Component.Builder docks = Component.of("aw_t7_dermaga", 7).kind("dock").name("awan_dermaga").group(Group.BOUNDARY);
		for (int i = 0; i < L.docks.size(); i++) {
			docks.add(new DermagaAwan(i));
		}
		b.add(docks);
		b.add(Component.of("aw_t7_halte_feri", 7).kind("ferry_stop").name("awan_halte_feri").group(Group.SIDE).add(AwanHias.halte(L.halte)));
		if (L.satellite(AwanLayout.Fungsi.ARENA) != null) {
			b.add(Component.of("sx_turnamen_tribun", 7).kind("tribune").name("sx_turnamen_tribun").group(Group.SIDE)
					.add(new PulauSatelit(AwanLayout.Fungsi.ARENA, PulauSatelit.Isi.TRIBUN)));
		}
		b.add(ornament(7).add(new Ornament(7, plaza).flowers(-(p + 2), 3, p + 2, 3)));

		// ============================================================ T8 Pagoda dan Leluhur
		if (L.hasPagoda) {
			int floors = row.pagodaFloors();
			b.add(Component.of("aw_t8_pagoda", 8).kind("pagoda").name("pagoda").group(Group.MAIN)
					.add(new Pagoda(L.hillX, L.hillZ, Lv.at(L.hillR > 0 ? row.bukit() : 0)).auto(floors, floors >= 7 ? 4 : floors >= 5 ? 3 : 2)
							.octagonal(floors >= 7).glazed(floors >= 5)));
		}
		satellite(b, L, AwanLayout.Fungsi.LELUHUR, "aw_t8_satelit_leluhur", "aw_t8_jembatan_leluhur", false);
		if (L.satellite(AwanLayout.Fungsi.LELUHUR) != null) {
			b.add(Component.of("sx_paviliun_makam", 8).kind("cemetery").name("sx_paviliun_makam").group(Group.GARDEN)
					.add(new PulauSatelit(AwanLayout.Fungsi.LELUHUR, PulauSatelit.Isi.MAKAM)));
		}
		satellite(b, L, AwanLayout.Fungsi.ASRAMA, "aw_t8_satelit_asrama", "aw_t8_jembatan_asrama", false);
		if (row.seat() > 0 && L.plot("kursi_qi") != null) {
			AwanLayout.Box seat = L.plot("kursi_qi");
			b.add(Component.of("aw_t8_kursi_qi", 8).kind("qi_seat").name("qi_seat").group(Group.GARDEN)
					.add(new QiSeat(seat.cx(), seat.cz(), plaza, row.seat())));
		}
		Component.Builder meditasi = Component.of("aw_t8_meditasi", 8).kind("meditation_ting").name("awan_meditasi").group(Group.GARDEN);
		for (AwanLayout.Titik spot : L.meditasi) {
			meditasi.add(AwanHias.meditasi(spot));
		}
		b.add(meditasi);
		b.add(ornament(8).add(new Ornament(8, plaza).lanterns(LanternPost.Style.JADE, -(p + 3), pf - 4, p + 3, pf - 4)));

		// ============================================================ T9 Menara dan Sembilan Langit
		int rooms = row.towerRooms();
		b.add(Component.of("aw_t9_menara", 9).kind("tower").name("blazing_cellar").group(Group.MAIN)
				.add(new TowerShaft(L.kelas == 0 ? -2 : -4, -11, Lv.at(aulaFloor), rooms, rooms >= 6 ? -4 : -5).roomSide(Direction.SOUTH)));
		GerbangSembilanLangit gate = new GerbangSembilanLangit(L.frontZ - 4, L.frontZ, L.kelas);
		int gateHalf = L.kelas == 0 ? 4 : L.kelas == 1 ? 6 : 7;
		b.add(Component.of("aw_t9_gerbang_langit", 9).kind("nine_heavens_gate").name("awan_gerbang_langit").group(Group.AXIS)
				.add(Marks.demolish(-14, 0, L.frontZ - 7, 14, 16, L.frontZ - 1))
				.add(Marks.demolish(5, 0, L.gateZ - 1, 5, 6, L.gateZ - 1)).add(Marks.demolish(-5, 0, L.gateZ - 1, -5, 6, L.gateZ - 1))
				.add(gate)
				.add(pole("panji_gerbang_0", gateHalf + 2, L.frontZ - 1, plaza, false))
				.add(pole("panji_gerbang_1", -(gateHalf + 2), L.frontZ - 1, plaza, false)));
		b.add(Component.of("aw_t9_kristal", 9).kind("crystals").name("awan_kristal").group(Group.GARDEN).add(new KristalGantung(row.kristal())));
		if (satellite(b, L, AwanLayout.Fungsi.TAMBANG, "aw_t9_satelit_tambang", "aw_t9_jembatan_tambang", false)) {
			int[] mouth = PulauSatelit.spot(L, AwanLayout.Fungsi.TAMBANG, 0, -1);
			AwanLayout.Satelit mine = L.satellite(AwanLayout.Fungsi.TAMBANG);
			// After its satellite island (GARDEN 0) and bridge (GARDEN 1).
			b.add(Component.of("sx_kebun_tambang", 9).kind("mine").name("sx_kebun_tambang").group(Group.GARDEN, 2)
					.add(new RonggaAkar(mouth[0], mouth[1], mine.toMain(), Lv.at(mouth[2]))));
		}
		b.add(Component.of("aw_t9_finale", 9).kind("finale").name("finale").group(Group.GARDEN)
				.add(Marks.petals(-p + 1, -2, p - 1, pf - 1, plaza, 10))
				.add(new LanternPost(LanternPost.Style.PALACE, plaza).at(-(p + 1), pf + 1, Direction.EAST).at(p + 1, pf + 1, Direction.WEST)));
		b.add(ornament(9).add(new Ornament(9, plaza).flowers(-3, pf + 1, 3, pf + 1)));
		return b.build();
	}

	// ================================================================== component helpers

	private static Component.Builder ornament(int tier) {
		return Component.of("aw_t" + tier + "_ornament", tier).kind("ornament").name("ornament_" + tier).group(Group.ORNAMENT);
	}

	/** The turtle head flag for the outermost ring (A7-A9). */
	private record Kura(boolean on) {
	}

	private static void addRing(TemplateSpec.Builder b, AwanLayout layout, int ring, int tier, String id, String name, Part before, Part after) {
		addRing(b, layout, ring, tier, id, name, before, after, new Kura(false));
	}

	/**
	 * A growth ring of the main island: one component, or four ({@code _u/_s/_t/_b}) when it would pass the per-component
	 * op cap. {@code before} (taking the old rim fence down) goes into the first, {@code after} (the new fence) into the last.
	 */
	private static void addRing(TemplateSpec.Builder b, AwanLayout layout, int ring, int tier, String id, String name, Part before, Part after,
			Kura kura) {
		int ops = estimate(layout, ring);
		Group group = ring == 1 ? Group.AXIS : Group.MAIN;
		if (ops <= COMPONENT_OPS) {
			Component.Builder c = Component.of(id, tier).kind("island").name(name).group(group, -10);
			c.add(before);
			c.add(new PulauAwan("main", ring).kura(kura.on()));
			c.add(after);
			b.add(c);
			return;
		}
		for (int q = 0; q < 4; q++) {
			Component.Builder c = Component.of(id + "_" + QUARTER[q], tier).kind("island").name(name).group(group, -10 + q);
			if (q == 0) {
				c.add(before);
			}
			c.add(new PulauAwan("main", ring).sektor(q).kura(kura.on() && q == 1));
			if (q == 3) {
				c.add(after);
			}
			b.add(c);
		}
	}

	/** A rough op count of a ring: its cells times the shell's cells per column. */
	private static int estimate(AwanLayout layout, int ring) {
		AwanLayout.Pulau p = layout.main;
		int reach = (int) Math.ceil(p.radius() * 1.16D) + 1;
		int cells = 0;
		for (int x = p.cx - reach; x <= p.cx + reach; x++) {
			for (int z = p.cz - reach; z <= p.cz + reach; z++) {
				if (p.ring(x, z) == ring) {
					cells++;
				}
			}
		}
		return cells * (ring == 3 ? 7 : 5);
	}

	/** A satellite island with its buildings (one component) and its bridge (another); false when the template has none. */
	private static boolean satellite(TemplateSpec.Builder b, AwanLayout layout, AwanLayout.Fungsi fungsi, String islandId, String bridgeId,
			boolean spore) {
		AwanLayout.Satelit s = layout.satellite(fungsi);
		if (s == null) {
			return false;
		}
		String island = s.pulau().id;
		PulauAwan.Gaya gaya = fungsi == AwanLayout.Fungsi.TAMBANG ? PulauAwan.Gaya.BATU : fungsi == AwanLayout.Fungsi.ARENA
				? PulauAwan.Gaya.PUNCAK : PulauAwan.Gaya.TAMAN;
		b.add(Component.of(islandId, fungsi.tier).kind("satellite").name("awan_satelit_" + fungsi.id).group(Group.GARDEN, 0)
				.add(new PulauAwan(island, 0).gaya(gaya))
				.add(new PagarTepi(island, 0, fungsi.tier >= 6 ? PagarTepi.Gaya.MARMER : PagarTepi.Gaya.KAYU))
				.add(AwanHias.akar(island, spore))
				.add(new PulauSatelit(fungsi, PulauSatelit.Isi.ISI)));
		b.add(Component.of(bridgeId, fungsi.tier).kind("bridge").name("awan_jembatan").group(Group.GARDEN, 1).add(new JembatanGiok(fungsi.id)));
		return true;
	}

	// ================================================================== buildings of the size classes

	private static Hall asrama(AwanLayout L) {
		return switch (L.kelas) {
			case 0 -> new Hall(-13, -9).front(Direction.SOUTH).bays(1, 4, 1).depth(5).pillar(4).platform(1).rank(Rank.A)
					.roof(RoofBuilder.Type.XIESHAN, 1).overhang(1).porch(0).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN);
			case 1 -> new Hall(-17, -3).front(Direction.EAST).bays(2, 4, 2).depth(5).pillar(4).platform(1).rank(Rank.A)
					.roof(RoofBuilder.Type.XIESHAN, 1).overhang(1).porch(0).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN);
			default -> new Hall(-20, -3).front(Direction.EAST).bays(2, 6, 2).depth(7).pillar(4).platform(1).rank(Rank.A)
					.roof(RoofBuilder.Type.XIESHAN, 1).overhang(1).porch(0).interior(Interiors.Preset.DORM).doors(Hall.Doors.OPEN);
		};
	}

	private static Hall dapur(AwanLayout L) {
		return switch (L.kelas) {
			case 0 -> new Hall(13, 4).front(Direction.WEST).bays(1, 2, 1).depth(3).pillar(4).platform(1).rank(Rank.A)
					.roof(RoofBuilder.Type.XIESHAN, 1).overhang(1).porch(0).interior(Interiors.Preset.KITCHEN).doors(Hall.Doors.NONE);
			case 1 -> new Hall(17, -18).front(Direction.SOUTH).bays(2, 4, 2).depth(5).pillar(4).platform(1).rank(Rank.A)
					.roof(RoofBuilder.Type.XIESHAN, 1).overhang(1).porch(0).interior(Interiors.Preset.KITCHEN).doors(Hall.Doors.NONE);
			default -> new Hall(20, -20).front(Direction.SOUTH).bays(2, 4, 2).depth(5).pillar(4).platform(1).rank(Rank.A)
					.roof(RoofBuilder.Type.XIESHAN, 1).overhang(1).porch(0).interior(Interiors.Preset.KITCHEN).doors(Hall.Doors.NONE);
		};
	}

	private static Hall kitab(AwanLayout L) {
		int shelves = L.row.shelves();
		return switch (L.kelas) {
			case 0 -> {
				Hall hall = new Hall(13, -9).front(Direction.SOUTH).bays(1, 4, 1).depth(7).pillar(4).platform(1).rank(Rank.B)
						.roof(RoofBuilder.Type.XIESHAN, 1).overhang(1).porch(0).interior(Interiors.Preset.LIBRARY, shelves).doors(Hall.Doors.NONE);
				yield L.row.n() >= 2 ? hall.storeys(2, 4) : hall;
			}
			case 1 -> new Hall(16, -3).front(Direction.WEST).bays(2, 4, 2).depth(7).pillar(4).platform(1).rank(Rank.B).storeys(2, 4)
					.roof(RoofBuilder.Type.XIESHAN, 1).overhang(1).porch(0).interior(Interiors.Preset.LIBRARY, shelves).doors(Hall.Doors.NONE);
			default -> new Hall(16, -3).front(Direction.WEST).bays(3, 6, 3).depth(7).pillar(5).platform(1).rank(Rank.B).storeys(2, 4)
					.roof(RoofBuilder.Type.XIESHAN, 1).overhang(1).porch(0).interior(Interiors.Preset.LIBRARY, shelves).doors(Hall.Doors.NONE);
		};
	}

	/** The second lectern of A5-A9 (an exact item) beside the library's door, on a stone base. */
	private static Part lectern(AwanLayout L) {
		boolean two = L.row.lecterns() >= 2;
		int x = L.kelas == 0 ? 10 : 8;
		int z = -1;
		Direction facing = L.kelas == 0 ? Direction.SOUTH : Direction.WEST;
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				return new BoundingBox(x, 0, z, x, 1, z);
			}

			@Override
			public String kind() {
				return "lectern";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				if (!two) {
					return;
				}
				Phase previous = sink.phase();
				sink.phase(Phase.INTERIOR);
				sink.set(x, 0, z, Palette.facing(Blocks.LECTERN, facing));
				sink.phase(previous);
			}
		};
	}

	private static Hall mainHall(AwanLayout L) {
		return switch (L.kelas) {
			case 0 -> new Hall(0, -27).bays(2, 4, 2).depth(7).pillar(5).platform(2).rank(Rank.D).roof(RoofBuilder.Type.XIESHAN, 1)
					.interior(Interiors.Preset.MAIN_HALL).doors(Hall.Doors.CLOSED);
			case 1 -> new Hall(0, -33).bays(3, 4, 6, 4, 3).depth(11).pillar(6).platform(2).rank(Rank.D).roof(RoofBuilder.Type.XIESHAN, 1)
					.interior(Interiors.Preset.MAIN_HALL).doors(Hall.Doors.CLOSED);
			default -> new Hall(0, -37).bays(3, 4, 6, 4, 3).depth(11).pillar(6).platform(2).rank(Rank.D).roof(RoofBuilder.Type.XIESHAN, 2)
					.interior(Interiors.Preset.MAIN_HALL).doors(Hall.Doors.CLOSED);
		};
	}

	/**
	 * DESIGN 36 A's ancestral altar (anchors {@code altar}, {@code altar_meja}, {@code altar_dupa}): a blackstone tablet
	 * shelf on a dais between two soul lanterns, an offering table, a bronze censer and two cushions, facing the gate.
	 */
	private static Part altar(int x, int z, Lv level) {
		Part body = Stamp.of("altar_leluhur").floorLayer(true)
				.layer("PPPPP", "PPPPP", "SSSSS")
				.layer("LBBBL", " T T ", "     ")
				.layer(" bbb ", " C c ", " k k ")
				.legend('P', Palette.st(Blocks.POLISHED_BLACKSTONE_BRICKS))
				.legend('S', Palette.slab(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, false))
				.legend('L', Palette.lantern(Blocks.SOUL_LANTERN, false))
				.legend('B', Palette.st(Blocks.POLISHED_BLACKSTONE))
				.legend('T', Palette.slab(Blocks.DARK_OAK_SLAB, true))
				.legend('b', Palette.slab(Blocks.POLISHED_BLACKSTONE_SLAB, false))
				.legend('C', Palette.st(Palette.bronze(Blocks.COPPER_GRATE)))
				.legend('c', Palette.candles(Palette.candle(DyeColor.RED), 3, true))
				.legend('k', Palette.st(Palette.carpet(DyeColor.RED)))
				.phase(Phase.INTERIOR)
				.at(x, z, Direction.SOUTH, level);
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				return body.bounds(ctx);
			}

			@Override
			public String kind() {
				return "ancestral_altar";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				body.emit(ctx, sink);
				int y = ctx.level(level);
				sink.anchor("altar", x, y, z + 2);
				sink.anchor("altar_meja", x, y + 1, z);
				sink.anchor("altar_dupa", x - 1, y + 1, z + 1);
			}
		};
	}

	/** A DESIGN 36 S banner pole: carved foot, cinnabar shaft, gilt cap; the anchor names the cap cell. */
	private static Part pole(String anchor, int x, int z, Lv level, boolean formation) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				int y = ctx.level(level);
				return new BoundingBox(x, y, z, x, y + 6, z);
			}

			@Override
			public String kind() {
				return "panji";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				int y = ctx.level(level);
				Phase previous = sink.phase();
				sink.phase(Phase.ORNAMENT);
				sink.set(x, y, z, Palette.st(formation ? Palette.STONE_FRET : Palette.STONE_PILASTER));
				for (int i = 1; i < 6; i++) {
					sink.set(x, y + i, z, Palette.st(Palette.PILLAR_THIN));
				}
				sink.set(x, y + 6, z, Palette.st(Palette.GILT_CAP));
				sink.anchor(anchor, x, y + 6, z);
				sink.phase(previous);
			}
		};
	}

	/**
	 * DESIGN 36 G's two Batu Hati stelae at the stair foot, facing the arrivals, with the projector and the six lineup
	 * cells in front (anchors {@code tangga_batu_kiri/kanan}, {@code tangga_proyektor}, {@code tangga_baris_1..6}).
	 */
	private static Part batuHati(AwanLayout L) {
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				int y = ctx.level(Lv.zone("kaki"));
				return new BoundingBox(L.kaki.x0(), y, L.kaki.z1() - 1, L.kaki.x1(), y + 4, L.kaki.z1() + 4);
			}

			@Override
			public String kind() {
				return "batu_hati";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				int y = ctx.level(Lv.zone("kaki"));
				int z = L.kaki.z1() - 1;
				Phase previous = sink.phase();
				for (int side : new int[]{-1, 1}) {
					int x = L.stairX + side * 3;
					sink.phase(Phase.FRAME);
					sink.set(x, y, z, Palette.st(Palette.PILLAR_BASE));
					sink.set(x, y + 1, z, Palette.st(Blocks.CHISELED_STONE_BRICKS));
					sink.set(x, y + 2, z, Palette.st(Palette.jade(Blocks.CHISELED_COPPER)));
					sink.phase(Phase.ORNAMENT);
					sink.set(x, y + 3, z, Palette.lantern(Palette.jade(Blocks.COPPER_LANTERN), false));
					sink.anchor(side < 0 ? "tangga_batu_kiri" : "tangga_batu_kanan", x, y, z);
				}
				sink.phase(previous);
				sink.anchor("tangga_proyektor", L.stairX, y, L.kaki.z1() + 1);
				int[] lineX = {-1, 1, -2, 2, -3, 3};
				for (int i = 0; i < lineX.length; i++) {
					sink.anchor("tangga_baris_" + (i + 1), L.stairX + lineX[i], y, L.kaki.z1() + 2);
				}
			}
		};
	}

	/** The walks of T3: the axis to the gate and an L-shaped path from the plaza to each hall's door. */
	private static Part paths(AwanLayout L) {
		List<Part> parts = new ArrayList<>();
		int p = L.plaza;
		parts.add(new Paving(-1, L.plazaFront + 1, 1, L.gateZ + 2, Lv.PLAZA, Paving.Pattern.AXIS).earth(false));
		for (String name : new String[]{"asrama", "kitab", "dapur", "altar"}) {
			AwanLayout.Box box = L.plot(name);
			if (box == null) {
				continue;
			}
			int cx = box.cx();
			if (Math.abs(cx) <= p) {
				continue;
			}
			// From the plaza's side edge along z = 0 to below the building, then along x to its middle.
			int side = Integer.signum(cx);
			int edgeX = side * (p + 1);
			int toX = side > 0 ? box.x0() - 1 : box.x1() + 1;
			if (Math.abs(toX) > Math.abs(edgeX)) {
				parts.add(new Paving(Math.min(edgeX, toX), 0, Math.max(edgeX, toX), 1, Lv.PLAZA, Paving.Pattern.PATH).earth(false));
			}
		}
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				BoundingBox box = null;
				for (Part part : parts) {
					BoundingBox each = part.bounds(ctx);
					box = box == null ? each : BoundingBox.encapsulating(box, each);
				}
				return box;
			}

			@Override
			public String kind() {
				return "paths";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				for (Part part : parts) {
					part.emit(ctx, sink);
				}
			}
		};
	}

	/**
	 * The garden pond at the hill's foot (A4+): its basin is cut into the plateau first (the island's own cells are taken
	 * out: earthwork never removes them), a mossy rock three high with a real little waterfall falling into it (the only
	 * fluid of the island, contained: no water ever leaves it).
	 */
	private static Part kolam(AwanLayout L) {
		AwanLayout.Box box = L.plot("kolam");
		int cx = box.cx();
		int cz = box.cz();
		int rx = Math.max(2, (box.x1() - box.x0()) / 2 - 1);
		int rz = Math.max(2, (box.z1() - box.z0()) / 2 - 1);
		Part cut = Marks.demolish(cx - rx - 1, -4, cz - rz - 1, cx + rx + 1, -1, cz + rz + 1);
		Part pond = Pond.pond(cx, cz, rx, rz, Lv.PLAZA);
		int fallX = cx - rx - 1;
		Part rock = Stamp.of("kolam_batu")
				.layer("MMM", "MMM", "MMM").layer("MMM", "MAM", "MMM").layer("M M", "   ", "M M")
				.legend('M', Palette.st(Blocks.MOSSY_COBBLESTONE)).legend('A', Palette.st(Blocks.ANDESITE))
				.phase(Phase.PLINTH).at(fallX - 1, cz, Direction.SOUTH, Lv.PLAZA);
		Part fall = new Waterfall("kolam", fallX - 1, cz, Lv.at(3), Lv.PLAZA, Direction.EAST);
		return new Part() {
			@Override
			public BoundingBox bounds(Ctx ctx) {
				return BoundingBox.encapsulating(pond.bounds(ctx), rock.bounds(ctx));
			}

			@Override
			public String kind() {
				return "pond";
			}

			@Override
			public void emit(Ctx ctx, Sink sink) {
				cut.emit(ctx, sink);
				pond.emit(ctx, sink);
				rock.emit(ctx, sink);
				fall.emit(ctx, sink);
			}
		};
	}

	private AwanTemplates() {
	}
}
