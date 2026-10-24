package dev.kultivator.dunia.tempat.tema;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The five themed-town kinds of DESIGN 38.T.1 / 38.T.2.1 ({@code kota_tema/<id>}). Fixed data only: the plan
 * ({@code RencanaKotaTema}, 38TK inti) reads the cell size, the first-ring numbers, the priority and the district grid
 * from here; the live switches and the per-world chance come from {@link TemaConfig} (a theme switched off answers
 * "not suitable" for every candidate, never half a town).
 * <p>
 * District grid before rotation (north = -z; 38.T.3.2, 38.T.4.2, 38.T.5.2, 38.T.6.2, 38.T.7.2). Each district is
 * 2 x 2 lots (96 x 96); structure {@code kultivator:kota_tema/<tema>/<distrik>}, structure set of the same name with
 * the salt below (unique in the repo, checked by {@code tools/cek_tempat.py}).
 */
public enum Tema {

	/** Kota Pelabuhan: coast, a third over water; the sea side becomes grid north. Salts 380401-380409. */
	PELABUHAN("pelabuhan", 3072, 0.7D, 1400, 2800, 16, 3, 0x5FA8C8, List.of(
			new Distrik("dermaga_nelayan", -1, -1, 380401, true),
			new Distrik("dermaga_agung", 0, -1, 380402, true),
			new Distrik("galangan", 1, -1, 380403, true),
			new Distrik("kota_awak", -1, 0, 380404, false),
			new Distrik("pasar_laut", 0, 0, 380405, false),
			new Distrik("gudang_dagang", 1, 0, 380406, false),
			new Distrik("kuil_laut", -1, 1, 380407, false),
			new Distrik("gerbang_darat", 0, 1, 380408, false),
			new Distrik("hunian_saudagar", 1, 1, 380409, false))),
	/** Kota Oasis Gurun: desert/badlands, gate south, plus the Lapangan Kafilah outside the gate. Salts 380411-380420. */
	OASIS("oasis", 3584, 0.7D, 1400, 2800, 16, 4, 0xE0B060, List.of(
			new Distrik("kuil_air", -1, -1, 380411, false),
			new Distrik("istana_pasir", 0, -1, 380412, false),
			new Distrik("kebun_palma", 1, -1, 380413, false),
			new Distrik("bazar_barat", -1, 0, 380414, false),
			new Distrik("mata_air", 0, 0, 380415, false),
			new Distrik("bazar_timur", 1, 0, 380416, false),
			new Distrik("hunian_tanah", -1, 1, 380417, false),
			new Distrik("gerbang_giok", 0, 1, 380418, false),
			new Distrik("karavanserai", 1, 1, 380419, false),
			new Distrik("lapangan_kafilah", 0, 2, 380420, false))),
	/** Kota Air: flat lowland canal town; side code {@code K} keeps every canal joined. Salts 380421-380429. */
	AIR("air", 3072, 0.5D, 1400, 2800, 16, 5, 0x7FC8B0, List.of(
			new Distrik("taman_sastra", -1, -1, 380421, false),
			new Distrik("rumah_sutra", 0, -1, 380422, false),
			new Distrik("bukit_teh", 1, -1, 380423, false),
			new Distrik("jembatan_kembar", -1, 0, 380424, false),
			new Distrik("danau_lentera", 0, 0, 380425, false),
			new Distrik("pasar_kanal", 1, 0, 380426, false),
			new Distrik("hunian_kanal", -1, 1, 380427, false),
			new Distrik("gerbang_air", 0, 1, 380428, false),
			new Distrik("dermaga_perahu", 1, 1, 380429, false))),
	/** Kota Gunung: three terrace rows climbing north (dy 0..72); the uphill side becomes grid north. Salts 380431-380439. */
	GUNUNG("gunung", 3072, 0.6D, 1400, 2800, 16, 2, 0xA0A8B8, List.of(
			new Distrik("tebing_papan", -1, -1, 380431, false),
			new Distrik("puncak_emas", 0, -1, 380432, false),
			new Distrik("biara_awan", 1, -1, 380433, false),
			new Distrik("teras_hunian", -1, 0, 380434, false),
			new Distrik("aula_herba", 0, 0, 380435, false),
			new Distrik("teras_kebun", 1, 0, 380436, false),
			new Distrik("penginapan_peziarah", -1, 1, 380437, false),
			new Distrik("kaki_gerbang", 0, 1, 380438, false),
			new Distrik("pasar_kaki", 1, 1, 380439, false))),
	/** Kota Iblis: demonic-path city, safe only inside the Zona Perjanjian (pasar_gelap, kedai_racun). Salts 380441-380449. */
	IBLIS("iblis", 4096, 0.45D, 3500, 2500, 12, 1, 0x8A1020, List.of(
			new Distrik("menara_terlarang", -1, -1, 380441, false),
			new Distrik("istana_darah", 0, -1, 380442, false),
			new Distrik("penjara_tawanan", 1, -1, 380443, false),
			new Distrik("pasar_gelap", -1, 0, 380444, false),
			new Distrik("arena_darah", 0, 0, 380445, false),
			new Distrik("sarang_kontrak", 1, 0, 380446, false),
			new Distrik("kumuh_iblis", -1, 1, 380447, false),
			new Distrik("gerbang_tengkorak", 0, 1, 380448, false),
			new Distrik("kedai_racun", 1, 1, 380449, false)));

	/** Salt of the jigsaw satellite {@code kota_tema/reruntuhan_gurun} (38.T.2.6). */
	public static final int SALT_RERUNTUHAN_GURUN = 380451;
	/** Salt of the planned satellite {@code kota_tema/pos_pemburu} (38.T.2.6). */
	public static final int SALT_POS_PEMBURU = 380452;
	/** Side of one district cell in blocks (2 x 2 lots of 48). */
	public static final int SEL_DISTRIK = 96;
	/** Side of one lot in blocks (38.B.2.5). */
	public static final int LOT = 48;

	/**
	 * One district of a themed town.
	 *
	 * @param id   the district id ({@code kota_tema/<tema>/<id>})
	 * @param x    grid column -1..1 before rotation
	 * @param z    grid row -1..1 (oasis: 2 for the caravan field outside the gate) before rotation; north = -1
	 * @param salt the structure-set salt
	 * @param laut true for the harbour's sea row (terrain_adaptation none, piers on piles)
	 */
	public record Distrik(String id, int x, int z, int salt, boolean laut) {

		/** The structure / structure-set path {@code kota_tema/<tema>/<id>}. */
		public String struktur(Tema tema) {
			return "kota_tema/" + tema.id() + "/" + id;
		}
	}

	private final String id;
	private final int ukuranSel;
	private final double peluangSel;
	private final int cincinMin;
	private final int cincinLebar;
	private final int kandidatCincin;
	private final int prioritas;
	private final int warna;
	private final List<Distrik> distrik;

	Tema(String id, int ukuranSel, double peluangSel, int cincinMin, int cincinLebar, int kandidatCincin, int prioritas,
			int warna, List<Distrik> distrik) {
		this.id = id;
		this.ukuranSel = ukuranSel;
		this.peluangSel = peluangSel;
		this.cincinMin = cincinMin;
		this.cincinLebar = cincinLebar;
		this.kandidatCincin = kandidatCincin;
		this.prioritas = prioritas;
		this.warna = warna;
		this.distrik = distrik;
	}

	/** {@code pelabuhan}, {@code oasis}, {@code air}, {@code gunung}, {@code iblis}. */
	public String id() {
		return id;
	}

	/** The site kind {@code kota_tema/<id>} (DuniaSites, markers 42-46). */
	public String jenisSitus() {
		return "kota_tema/" + id;
	}

	/** The plan id in {@code RencanaRegistry}: {@code kota_tema_<id>}. */
	public String idRencana() {
		return "kota_tema_" + id;
	}

	/** Default far-cell size in blocks (config {@code tema<Tema>.ukuranSel} may change it). */
	public int ukuranSel() {
		return ukuranSel;
	}

	/** Default chance a far cell holds a town (config {@code tema<Tema>.peluangSel}). */
	public double peluangSel() {
		return peluangSel;
	}

	/** First ring: the smallest radius of a candidate (1400; iblis 3500). */
	public int cincinMin() {
		return cincinMin;
	}

	/** First ring: radius spread added by the hash (2800; iblis 2500). */
	public int cincinLebar() {
		return cincinLebar;
	}

	/** First ring: number of candidates on the ring (16 at 22.5 degrees; iblis 12 at 30 degrees). */
	public int kandidatCincin() {
		return kandidatCincin;
	}

	/** Evaluation order after the capitals: 1 first (iblis, gunung, pelabuhan, oasis, air). */
	public int prioritas() {
		return prioritas;
	}

	/** Map marker colour (38.T.11, markers 42-46). */
	public int warna() {
		return warna;
	}

	/** The districts in grid order (9; oasis 10). */
	public List<Distrik> distrik() {
		return distrik;
	}

	/** The district with this id, or null. */
	public @Nullable Distrik distrik(String distrikId) {
		for (Distrik d : distrik) {
			if (d.id().equals(distrikId)) {
				return d;
			}
		}
		return null;
	}

	/** True for the four peaceful themes (whole town is a safe zone; iblis only its Zona Perjanjian). */
	public boolean damai() {
		return this != IBLIS;
	}

	/** The theme with this id or site kind ({@code oasis} or {@code kota_tema/oasis}, any case), or null. */
	public static @Nullable Tema dari(@Nullable String id) {
		if (id == null) {
			return null;
		}
		String key = id.toLowerCase(Locale.ROOT);
		if (key.startsWith("kota_tema/")) {
			key = key.substring("kota_tema/".length());
		}
		for (Tema tema : values()) {
			if (tema.id.equals(key)) {
				return tema;
			}
		}
		return null;
	}
}
