package dev.kultivator.client.hub;

/**
 * The frozen page tree of the Gulungan Utama (DESIGN 43.5): page ids and their order inside each section. Modules register
 * their page with {@code HubPages.daftar(HubNav.SEKTE, Urutan.ID_KAS, Urutan.KAS, KasPage::new)}; the order constants are the
 * page's position in {@link #POHON} times ten (gaps leave room). Ids are unique over the whole tree.
 */
public final class Urutan {

	/** Page ids per section in tree order (index = {@code HubNav} section). Frozen in 43-S; do not reorder. */
	public static final String[][] POHON = {
			{"status", "akar_dao", "jalan", "alkimia", "pusaka", "harta"},
			{"kodeks", "cincin_segel", "kehancuran"},
			{"daftar", "murid", "jaring"},
			{"ikhtisar", "anggota", "bangun", "formasi", "kas", "kegiatan", "wilayah", "luar"},
			{"peta", "alam", "musuh", "duel", "perjalanan", "kota", "dagang"},
			{"alam_abadi", "purba", "batin", "ciptaan", "lelang", "titah"},
			{"kitab", "ensiklopedia"},
			{"tampilan", "film", "suara", "hud", "kontrol"}};

	/** Order of the transition page "Lainnya" (old launcher cards, 43.16.1); always last. */
	public static final int LAINNYA = 900;

	// [1] Diri
	public static final String ID_STATUS = "status";
	public static final String ID_AKAR_DAO = "akar_dao";
	public static final String ID_JALAN = "jalan";
	public static final String ID_ALKIMIA = "alkimia";
	public static final String ID_PUSAKA = "pusaka";
	public static final String ID_HARTA = "harta";
	public static final int STATUS = 10;
	public static final int AKAR_DAO = 20;
	public static final int JALAN = 30;
	public static final int ALKIMIA = 40;
	public static final int PUSAKA = 50;
	public static final int HARTA = 60;
	// [2] Teknik
	public static final String ID_KODEKS = "kodeks";
	public static final String ID_CINCIN_SEGEL = "cincin_segel";
	public static final String ID_KEHANCURAN = "kehancuran";
	public static final int KODEKS = 10;
	public static final int CINCIN_SEGEL = 20;
	public static final int KEHANCURAN = 30;
	// [3] Murid
	public static final String ID_DAFTAR = "daftar";
	public static final String ID_MURID = "murid";
	public static final String ID_JARING = "jaring";
	public static final int DAFTAR = 10;
	public static final int MURID = 20;
	public static final int JARING = 30;
	// [4] Sekte
	public static final String ID_IKHTISAR = "ikhtisar";
	public static final String ID_ANGGOTA = "anggota";
	public static final String ID_BANGUN = "bangun";
	public static final String ID_FORMASI = "formasi";
	public static final String ID_KAS = "kas";
	public static final String ID_KEGIATAN = "kegiatan";
	public static final String ID_WILAYAH = "wilayah";
	public static final String ID_LUAR = "luar";
	public static final int IKHTISAR = 10;
	public static final int ANGGOTA = 20;
	public static final int BANGUN = 30;
	public static final int FORMASI = 40;
	public static final int KAS = 50;
	public static final int KEGIATAN = 60;
	public static final int WILAYAH = 70;
	public static final int LUAR = 80;
	// [5] Dunia
	public static final String ID_PETA = "peta";
	public static final String ID_ALAM = "alam";
	public static final String ID_MUSUH = "musuh";
	public static final String ID_DUEL = "duel";
	public static final String ID_PERJALANAN = "perjalanan";
	public static final String ID_KOTA = "kota";
	public static final String ID_DAGANG = "dagang";
	public static final int PETA = 10;
	public static final int ALAM = 20;
	public static final int MUSUH = 30;
	public static final int DUEL = 40;
	public static final int PERJALANAN = 50;
	public static final int KOTA = 60;
	public static final int DAGANG = 70;
	// [6] Keabadian
	public static final String ID_ALAM_ABADI = "alam_abadi";
	public static final String ID_PURBA = "purba";
	public static final String ID_BATIN = "batin";
	public static final String ID_CIPTAAN = "ciptaan";
	public static final String ID_LELANG = "lelang";
	public static final String ID_TITAH = "titah";
	public static final int ALAM_ABADI = 10;
	public static final int PURBA = 20;
	public static final int BATIN = 30;
	public static final int CIPTAAN = 40;
	public static final int LELANG = 50;
	public static final int TITAH = 60;
	// [7] Panduan
	public static final String ID_KITAB = "kitab";
	public static final String ID_ENSIKLOPEDIA = "ensiklopedia";
	public static final int KITAB = 10;
	public static final int ENSIKLOPEDIA = 20;
	// [8] Pengaturan
	public static final String ID_TAMPILAN = "tampilan";
	public static final String ID_FILM = "film";
	public static final String ID_SUARA = "suara";
	public static final String ID_HUD = "hud";
	public static final String ID_KONTROL = "kontrol";
	public static final int TAMPILAN = 10;
	public static final int FILM = 20;
	public static final int SUARA = 30;
	public static final int HUD = 40;
	public static final int KONTROL = 50;

	/** The section of page {@code id} in the tree, or {@code HubNav.DIRI} when it is not in the tree. */
	public static int bagianDari(String id) {
		for (int s = 0; s < POHON.length; s++) {
			for (String page : POHON[s]) {
				if (page.equals(id)) {
					return s;
				}
			}
		}
		return HubNav.DIRI;
	}

	/** The tree order of page {@code id} in section {@code bagian} ({@code 10 * (index + 1)}), or 500 when not in the tree. */
	public static int urutan(int bagian, String id) {
		if (bagian >= 0 && bagian < POHON.length) {
			String[] pages = POHON[bagian];
			for (int i = 0; i < pages.length; i++) {
				if (pages[i].equals(id)) {
					return 10 * (i + 1);
				}
			}
		}
		return 500;
	}

	private Urutan() {
	}
}
