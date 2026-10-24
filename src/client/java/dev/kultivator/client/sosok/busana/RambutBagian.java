package dev.kultivator.client.sosok.busana;

import net.minecraft.client.model.geom.builders.PartDefinition;

import java.util.Map;

/**
 * The hair cubes and head ornaments of the Sosok busana model (DESIGN 40.3.6 region C, module 40K): every cube of
 * {@link BusanaKatalog#RAMBUT} on the skeleton's head or body, plus the alternative placements the resolver's looks
 * need in a static mesh (the model only switches parts on and off):
 * <ul>
 *   <li>{@code pita_rambut_ekor}: a single ribbon tied round the ponytail (no bun);</li>
 *   <li>{@code pita_rambut_cepol(_kiri)}: the twin-bun ribbons, one per bun;</li>
 *   <li>{@code tusuk_guan_rendah}: the hairpin through the old woman's low bun;</li>
 *   <li>{@code kepang_1_depan(_kiri)} with {@code kepang_2_depan(_kiri)}: two braids over the shoulders (desert, village
 *   girls; variant {@code V_KEPANG_DEPAN});</li>
 *   <li>{@code jambul_anak_satu}: the single front tuft of a little boy ({@code V_JAMBUL_SATU});</li>
 *   <li>{@code manik_buyao_0..2}: three dangling beads 0.8 px apart under the buyao pin.</li>
 * </ul>
 * Pairs: the table cube plus a mirrored {@code <id>_kiri} at -x.
 */
public final class RambutBagian {

	/** Adds every hair cube; {@code made} receives each created part definition by name. */
	static void pasang(PartDefinition kepala, PartDefinition badan, Map<String, PartDefinition> made) {
		for (BusanaKatalog.Kubus k : BusanaKatalog.RAMBUT) {
			PartDefinition host = switch (k.induk()) {
				case "kepala" -> kepala;
				case "badan" -> badan;
				default -> made.get(k.induk());
			};
			if (host == null) {
				continue;
			}
			switch (k.id()) {
				case "manik_buyao" -> {
					for (int i = 0; i < 3; i++) {
						BusanaBagian.tambah(host, k.id() + "_" + i, k, k.px() + 0.8F * (i - 1), k.py(), k.pz(), false, null, made);
					}
					continue;
				}
				case "kepang_1" -> {
					BusanaBagian.tambah(host, k.id(), k, k.px(), k.py(), k.pz(), false, null, made);
					BusanaBagian.tambah(badan, "kepang_1_depan", k, 2.6F, 0.5F, -2.2F, false, null, made);
					BusanaBagian.tambah(badan, "kepang_1_depan_kiri", k, -2.6F, 0.5F, -2.2F, true, null, made);
					continue;
				}
				case "kepang_2" -> {
					BusanaBagian.tambah(host, k.id(), k, k.px(), k.py(), k.pz(), false, null, made);
					BusanaBagian.tambah(made.get("kepang_1_depan"), "kepang_2_depan", k, k.px(), k.py(), k.pz(), false, null, made);
					BusanaBagian.tambah(made.get("kepang_1_depan_kiri"), "kepang_2_depan_kiri", k, k.px(), k.py(), k.pz(), true, null, made);
					continue;
				}
				case "jambul_anak" -> BusanaBagian.tambah(kepala, "jambul_anak_satu", k, 0.0F, -8.0F, -2.5F, false, null, made);
				case "pita_rambut" -> {
					BusanaBagian.tambah(made.get("ekor_kuda_1"), "pita_rambut_ekor", k, 0.6F, 0.6F, 0.9F, false, null, made);
					BusanaBagian.tambah(made.get("cepol"), "pita_rambut_cepol", k, 0.0F, -0.5F, 1.5F, false, null, made);
					BusanaBagian.tambah(made.get("cepol_kiri"), "pita_rambut_cepol_kiri", k, 0.0F, -0.5F, 1.5F, true, null, made);
				}
				default -> {
				}
			}
			BusanaBagian.tambah(host, k.id(), k, k.px(), k.py(), k.pz(), false, null, made);
			if (k.pasangan()) {
				BusanaBagian.tambah(host, k.id() + "_kiri", k, -k.px(), k.py(), k.pz(), true, null, made);
			}
		}
		// the hairpin through the low bun (the bun comes after the pin in the table)
		BusanaKatalog.Kubus tusuk = BusanaKatalog.RAMBUT.get(BusanaKatalog.rambut("tusuk_guan"));
		BusanaBagian.tambah(made.get("sanggul_rendah"), "tusuk_guan_rendah", tusuk, 0.0F, -1.4F, 1.5F, false, null, made);
	}

	private RambutBagian() {
	}
}
