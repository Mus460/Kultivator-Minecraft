package dev.kultivator.dunia.tempat.perbaikan;

import dev.kultivator.data.Realm;
import dev.kultivator.dunia.kota.NpcRole;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The repair profile of each kind of cared-for place (DESIGN 38.R.3-38.R.5 and 38.T.11): bill factor, strength (realm
 * ordinal the place can stand up to), collector and foreman roles, the craftsmen's role, crews and the billing style.
 * <ul>
 *   <li>style {@link #GAYA_BIASA}: refusing costs Nama Baik, prices +10 % for 7 days (capital: crime points; pledged
 *       village: loyalty);</li>
 *   <li>style {@link #GAYA_SEKTE}: an NPC sect compound, refusing also strains relations ({@code DiplomasiService.adjust});</li>
 *   <li>style {@link #GAYA_IBLIS}: Kota Iblis, the Blood Price (bill x2), refusing turns the enforcers hostile for a day
 *       and costs Gentar, never Nama Baik ({@code TemaApi.tolakTagihanIblis}).</li>
 * </ul>
 * The kind ({@code jenis}) is the site kind of {@code TempatApi} when the place index knows it, else derived from the
 * structure id ({@link #dariStruktur}).
 */
public final class JenisTempat {

	public static final String GAYA_BIASA = "biasa";
	public static final String GAYA_SEKTE = "sekte";
	public static final String GAYA_IBLIS = "iblis";

	/**
	 * One place kind.
	 *
	 * @param awalan    kind prefix this profile answers ({@code kota/kota_besar}, {@code kampung/klan}, ...)
	 * @param faktor    bill factor (38.R.4; the capital's comes from config {@code faktorIbuKota})
	 * @param kekuatan  default strength as a realm ordinal (38.R.5; compounds and clan villages ask their module)
	 * @param penagih   who brings the bill
	 * @param mandor    who points at the work
	 * @param tukang    the craftsmen
	 * @param regu      crews working at once
	 * @param perRegu   craftsmen per crew
	 * @param gaya      billing style
	 * @param namaKunci lang key of the place word used in titles ("Sahabat {tempat}") when the place has no name
	 */
	public record Profil(String awalan, double faktor, int kekuatan, NpcRole penagih, NpcRole mandor, NpcRole tukang, int regu, int perRegu,
			String gaya, String namaKunci) {

		/** Craftsmen at the base rate (all crews). */
		public int tukangDasar() {
			return Math.max(1, regu * perRegu);
		}

		public boolean ibuKota() {
			return awalan.equals("kota/kota_besar");
		}
	}

	private static final String L = "kultivator.tempat_perbaikan.jenis.";

	/** Most specific first: the first profile whose prefix the kind starts with wins. */
	private static final List<Profil> PROFIL = List.of(
			new Profil("kota/kota_besar", 2.5D, Realm.NASCENT_SOUL.ordinal(), NpcRole.KAPTEN_PENJAGA, NpcRole.KAPTEN_PENJAGA, NpcRole.TUKANG, 3, 3,
					GAYA_BIASA, L + "ibu_kota"),
			new Profil("kota/kota_abadi", 1.5D, Realm.IMMORTAL_ASCENSION.ordinal(), NpcRole.PENJAGA, NpcRole.PENJAGA, NpcRole.TUKANG, 3, 1,
					GAYA_BIASA, L + "kota_abadi"),
			new Profil("kota_tema/pelabuhan", 1.8D, Realm.GOLDEN_CORE.ordinal(), NpcRole.PETUGAS_BEA, NpcRole.SYAHBANDAR, NpcRole.TUKANG_KAPAL, 3, 1,
					GAYA_BIASA, L + "pelabuhan"),
			new Profil("kota_tema/oasis", 1.8D, Realm.NASCENT_SOUL.ordinal(), NpcRole.PENGAWAL_GURUN, NpcRole.TUAN_BENTENG, NpcRole.TUKANG, 3, 1,
					GAYA_BIASA, L + "oasis"),
			new Profil("kota_tema/air", 2.0D, Realm.FOUNDATION.ordinal(), NpcRole.KEPALA_DESA, NpcRole.KEPALA_DESA, NpcRole.TUKANG, 3, 1,
					GAYA_BIASA, L + "air"),
			new Profil("kota_tema/gunung", 2.0D, Realm.NASCENT_SOUL.ordinal(), NpcRole.BIKSU_GUNUNG, NpcRole.KEPALA_BIARA, NpcRole.KULI_GUNUNG, 3, 1,
					GAYA_BIASA, L + "gunung"),
			new Profil("kota_tema/iblis", 2.0D, Realm.DEITY_TRANSFORMATION.ordinal(), NpcRole.PENEGAK_IBLIS, NpcRole.PENEGAK_IBLIS, NpcRole.WARGA_IBLIS,
					3, 1, GAYA_IBLIS, L + "iblis"),
			new Profil("kota_tema", 1.8D, Realm.GOLDEN_CORE.ordinal(), NpcRole.KAPTEN_PENJAGA, NpcRole.KAPTEN_PENJAGA, NpcRole.TUKANG, 3, 1,
					GAYA_BIASA, L + "kota"),
			new Profil("kota", 1.5D, Realm.GOLDEN_CORE.ordinal(), NpcRole.KAPTEN_PENJAGA, NpcRole.KAPTEN_PENJAGA, NpcRole.TUKANG, 3, 1,
					GAYA_BIASA, L + "kota"),
			new Profil("kampung/klan", 1.5D, Realm.GOLDEN_CORE.ordinal(), NpcRole.TETUA_KLAN, NpcRole.PATRIARK_KLAN, NpcRole.MURID_KLAN, 2, 1,
					GAYA_BIASA, L + "klan"),
			new Profil("kampung/desa_besar", 1.0D, Realm.FOUNDATION.ordinal(), NpcRole.KEPALA_DESA, NpcRole.KEPALA_DESA, NpcRole.TUKANG, 2, 1,
					GAYA_BIASA, L + "desa_besar"),
			new Profil("kampung/desa", 1.0D, Realm.QI_REFINING.ordinal(), NpcRole.KEPALA_DESA, NpcRole.KEPALA_DESA, NpcRole.TUKANG, 2, 1,
					GAYA_BIASA, L + "desa"),
			new Profil("kampung", 1.0D, Realm.MORTAL.ordinal(), NpcRole.KEPALA_DESA, NpcRole.TETUA_DESA, NpcRole.TUKANG, 2, 1,
					GAYA_BIASA, L + "dusun"),
			new Profil("sekte/kompleks", 2.0D, Realm.GOLDEN_CORE.ordinal(), NpcRole.TETUA_SEKTE, NpcRole.TETUA_SEKTE, NpcRole.PELAYAN_SEKTE, 3, 1,
					GAYA_SEKTE, L + "kompleks"),
			new Profil("laut/pulau_pasar", 1.5D, Realm.GOLDEN_CORE.ordinal(), NpcRole.NAKHODA, NpcRole.NAKHODA, NpcRole.KULI, 2, 1,
					GAYA_BIASA, L + "pulau_pasar"),
			new Profil("pos", 1.0D, Realm.QI_REFINING.ordinal(), NpcRole.PEMILIK_PENGINAPAN, NpcRole.PEMILIK_PENGINAPAN, NpcRole.TUKANG, 1, 1,
					GAYA_BIASA, L + "pos"),
			new Profil("jalan", 1.0D, Realm.QI_REFINING.ordinal(), NpcRole.TUKANG, NpcRole.TUKANG, NpcRole.TUKANG, 1, 1,
					GAYA_BIASA, L + "jembatan"));

	/** The fallback for a kind nobody listed (a cared-for structure of a later design). */
	private static final Profil UMUM = new Profil("", 1.0D, Realm.QI_REFINING.ordinal(), NpcRole.KEPALA_DESA, NpcRole.KEPALA_DESA, NpcRole.TUKANG, 1, 1,
			GAYA_BIASA, L + "umum");

	/** The profile of kind {@code jenis}; the capital's factor follows config {@code faktorIbuKota}. */
	public static Profil of(String jenis) {
		String j = jenis == null ? "" : jenis;
		for (Profil p : PROFIL) {
			if (j.startsWith(p.awalan())) {
				if (p.ibuKota()) {
					double f = PerbaikanConfig.get().faktorIbuKota;
					return new Profil(p.awalan(), f, p.kekuatan(), p.penagih(), p.mandor(), p.tukang(), p.regu(), p.perRegu(), p.gaya(), p.namaKunci());
				}
				return p;
			}
		}
		return UMUM;
	}

	/**
	 * The site kind of a cared-for structure from its id (used until the place index of 38W answers): {@code kota/kota_*}
	 * variants are {@code kota/kota}, {@code kota_besar/*} {@code kota/kota_besar}, {@code kompleks_sekte/*}
	 * {@code sekte/kompleks}, {@code jalan/*} {@code jalan/jembatan}, the rest keep their first two path parts.
	 */
	public static String dariStruktur(Identifier struktur) {
		String path = struktur.getPath();
		if (path.equals("kota/kota_abadi")) {
			return "kota/kota_abadi";
		}
		if (path.startsWith("kota/kota_kultivator") || path.startsWith("kota/kota")) {
			return "kota/kota";
		}
		if (path.startsWith("kota_besar")) {
			return "kota/kota_besar";
		}
		if (path.startsWith("kompleks_sekte")) {
			return "sekte/kompleks";
		}
		if (path.startsWith("jalan")) {
			return "jalan/jembatan";
		}
		if (path.startsWith("laut/pulau_pasar")) {
			return "laut/pulau_pasar";
		}
		String[] parts = path.split("/");
		if (parts.length >= 2) {
			String second = parts[1];
			if (parts[0].equals("kampung")) {
				for (String size : new String[]{"desa_besar", "klan", "desa", "dusun"}) {
					if (second.startsWith(size)) {
						return "kampung/" + size;
					}
				}
			}
			return parts[0] + "/" + second;
		}
		return path;
	}

	private JenisTempat() {
	}
}
