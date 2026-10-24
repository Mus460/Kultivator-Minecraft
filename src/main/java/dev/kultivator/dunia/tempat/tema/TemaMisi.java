package dev.kultivator.dunia.tempat.tema;

import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ekonomi.JenisMisi;

import java.util.List;

/**
 * The nine guild mission kinds of the themed towns (DESIGN 38.T.11 row Misi gilda), registered once with
 * {@code EkonomiApi.daftarJenisMisi}. The graded boards of 38EK offer them between their ranks, pointed at the nearest
 * known or located place of the given site kind; the theme system that runs the objective reports it with
 * {@code EkonomiApi.misiSelesai(player, id)} (and spawns its foes only while {@code EkonomiApi.misiAktif}):
 * <pre>
 * muatan          D-B  any              bersama.KontrakMuatan (a delivered cargo contract)
 * kawal_unta      D-B  kota_tema/oasis  38TK-O  (escort a camel caravan through the dunes)
 * reruntuhan      C-A  kota_tema/reruntuhan_gurun  38TK-O (bring back a relic of the buried city)
 * antar_sutra     E    kota_tema/air    38TK-A  (carry a silk parcel of House Su)
 * herba_tebing    D    kota_tema/gunung 38TK-G  (pick a cliff herb on a rope)
 * pencuri_kanal   D    kota_tema/air    38TK-A  (catch the canal thief, Tikus Air)
 * bajak_laut      B    kota_tema/pelabuhan 38TK-P (break a pirate raid on the piers)
 * buronan_iblis   B-S  kota_tema/pos_pemburu 38TK-I (a named demonic fugitive on the hunters' board)
 * tebus_tawanan   C    kota_tema/iblis  38TK-I  (ransom a captive and bring them home)
 * </pre>
 * Story text keys {@code kultivator.misi.<id>.judul|cerita} are in {@code lang-src/tempat_tema.*}.
 */
public final class TemaMisi {

	public static final List<JenisMisi> SEMUA = List.of(
			new JenisMisi("muatan", JenisMisi.D, JenisMisi.B, null, 8, "38TK"),
			new JenisMisi("kawal_unta", JenisMisi.D, JenisMisi.B, "kota_tema/oasis", 6, "38TK"),
			new JenisMisi("reruntuhan", JenisMisi.C, JenisMisi.A, "kota_tema/reruntuhan_gurun", 6, "38TK"),
			new JenisMisi("antar_sutra", JenisMisi.E, JenisMisi.E, "kota_tema/air", 8, "38TK"),
			new JenisMisi("herba_tebing", JenisMisi.D, JenisMisi.D, "kota_tema/gunung", 6, "38TK"),
			new JenisMisi("pencuri_kanal", JenisMisi.D, JenisMisi.D, "kota_tema/air", 5, "38TK"),
			new JenisMisi("bajak_laut", JenisMisi.B, JenisMisi.B, "kota_tema/pelabuhan", 5, "38TK"),
			new JenisMisi("buronan_iblis", JenisMisi.B, JenisMisi.S, "kota_tema/pos_pemburu", 5, "38TK"),
			new JenisMisi("tebus_tawanan", JenisMisi.C, JenisMisi.C, "kota_tema/iblis", 4, "38TK"));

	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		for (JenisMisi jenis : SEMUA) {
			EkonomiApi.daftarJenisMisi(jenis);
		}
	}

	private TemaMisi() {
	}
}
