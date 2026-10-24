package dev.kultivator.client.sosok.busana;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import java.util.Map;

/**
 * The garment cubes of the Sosok busana model (DESIGN 40.3.7 region D, module 40K): every cube of
 * {@link BusanaKatalog#BUSANA} added to the player skeleton, with the placement rules of 40.3.7 that the static mesh
 * needs (one model serves every look; a look only switches parts on and off):
 * <ul>
 *   <li>pairs: the table cube plus a mirrored {@code <id>_kiri}; arm cubes go on the right arm and mirrored on the left;
 *   slim arms move them 0.5 px toward the body (the slim arm is 3 px, centred 0.5 px further in);</li>
 *   <li>belt hangings (ribbons, jade pendant, purse, gourd, rank badge) hang from the body at the belt's height, so they
 *   show also with robes that have no belt cube; the high-waisted ruqun's ribbons get a chest copy
 *   {@code pita_sabuk_dada(_kiri)} (body y 3);</li>
 *   <li>the wide sleeve's pocket hangs from the arm at the cuff's pivot;</li>
 *   <li>{@code rok_zirah} front and back ({@code rok_zirah_b}), {@code rumbai_iblis_0..2} across the hem.</li>
 * </ul>
 * Names are unique in the whole tree (the model finds parts by name).
 */
public final class BusanaBagian {

	/** Belt pivot on the body (table {@code sabuk}). */
	static final float SABUK_Y = 6.5F;
	/** The chest height of the ruqun ribbons. */
	static final float DADA_Y = 3.0F;

	/** Adds every garment cube; {@code made} receives each created part definition by name. */
	static void pasang(PartDefinition kepala, PartDefinition badan, PartDefinition lenganKanan, PartDefinition lenganKiri, boolean ramping,
			Map<String, PartDefinition> made) {
		for (BusanaKatalog.Kubus k : BusanaKatalog.BUSANA) {
			switch (k.id()) {
				case "rok_zirah" -> {
					tambah(badan, k.id(), k, k.px(), k.py(), -2.6F, false, null, made);
					tambah(badan, k.id() + "_b", k, k.px(), k.py(), 2.6F, false, null, made);
				}
				case "rumbai_iblis" -> {
					float[] xs = {-3.0F, 0.0F, 3.0F};
					for (int i = 0; i < 3; i++) {
						tambah(badan, k.id() + "_" + i, k, xs[i], k.py(), i == 1 ? 2.2F : -2.2F, false, null, made);
					}
				}
				case "pita_sabuk", "liontin_giok", "kantong", "labu", "lencana_giok" -> {
					tambah(badan, k.id(), k, k.px(), SABUK_Y + k.py(), k.pz(), false, null, made);
					if (k.pasangan()) {
						tambah(badan, k.id() + "_kiri", k, -k.px(), SABUK_Y + k.py(), k.pz(), true, null, made);
					}
					if (k.id().equals("pita_sabuk")) {
						tambah(badan, "pita_sabuk_dada", k, k.px(), DADA_Y, k.pz(), false, null, made);
						tambah(badan, "pita_sabuk_dada_kiri", k, -k.px(), DADA_Y, k.pz(), true, null, made);
					}
				}
				case "lengan_lebar_kantong" -> {
					BusanaKatalog.Kubus manset = BusanaKatalog.BUSANA.get(BusanaKatalog.busana("lengan_lebar_manset"));
					float geser = ramping ? 0.5F : 0.0F;
					tambah(lenganKanan, k.id(), k, manset.px() + geser + k.px(), manset.py() + k.py(), manset.pz() + k.pz(), false, null, made);
					tambah(lenganKiri, k.id() + "_kiri", k, -(manset.px() + geser) - k.px(), manset.py() + k.py(), manset.pz() + k.pz(), true, null,
							made);
				}
				default -> {
					if (k.induk().equals("lengan")) {
						float geser = ramping ? 0.5F : 0.0F;
						float[] box = ramping && k.ramping() != null ? k.ramping() : null;
						tambah(lenganKanan, k.id(), k, k.px() + geser, k.py(), k.pz(), false, box, made);
						if (k.pasangan()) {
							tambah(lenganKiri, k.id() + "_kiri", k, -(k.px() + geser), k.py(), k.pz(), true, box, made);
						}
						continue;
					}
					PartDefinition host = switch (k.induk()) {
						case "kepala" -> kepala;
						case "badan" -> badan;
						default -> made.get(k.induk());
					};
					if (host == null) {
						continue;
					}
					tambah(host, k.id(), k, k.px(), k.py(), k.pz(), false, null, made);
					if (k.pasangan()) {
						// a pair under a mirrored parent (selendang_2 under selendang_1_kiri) keeps the parent's pivot sign
						PartDefinition hostKiri = made.getOrDefault(k.induk() + "_kiri", host);
						float x = hostKiri == host ? -k.px() : k.px();
						tambah(hostKiri, k.id() + "_kiri", k, x, k.py(), k.pz(), true, null, made);
					}
				}
			}
		}
	}

	/**
	 * Adds one cube part: box from the table (or {@code box} for slim arms), mirrored about x when {@code cermin}
	 * (box x and the y/z rotations flip), rotation in degrees from the table.
	 */
	static void tambah(PartDefinition host, String name, BusanaKatalog.Kubus k, float px, float py, float pz, boolean cermin, float[] box,
			Map<String, PartDefinition> made) {
		float x0 = box != null ? box[0] : k.x0();
		float y0 = box != null ? box[1] : k.y0();
		float z0 = box != null ? box[2] : k.z0();
		float w = box != null ? box[3] : k.w();
		float h = box != null ? box[4] : k.h();
		float d = box != null ? box[5] : k.d();
		CubeListBuilder cubes = CubeListBuilder.create().texOffs(k.u(), k.v());
		if (cermin) {
			cubes.mirror().addBox(-x0 - w, y0, z0, w, h, d);
		} else {
			cubes.addBox(x0, y0, z0, w, h, d);
		}
		float ry = cermin ? -k.ry() : k.ry();
		float rz = cermin ? -k.rz() : k.rz();
		PartPose pose = PartPose.offsetAndRotation(px, py, pz, k.rx() * Mth.DEG_TO_RAD, ry * Mth.DEG_TO_RAD, rz * Mth.DEG_TO_RAD);
		made.put(name, host.addOrReplaceChild(name, cubes, pose));
	}

	private BusanaBagian() {
	}
}
