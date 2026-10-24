package dev.kultivator.client.sosok.busana;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The cube tables of the Sosok hair and garments (DESIGN 40.3.6 region C, 40.3.7 region D; module 40K): parent part,
 * pivot, box, atlas UV, spring chain and placement flags of every cube. The order of {@link #RAMBUT} and
 * {@link #BUSANA} is the bit order of {@code SosokRupa.kubusRambut/kubusBusana} and the Python tables
 * {@code sosok_lib.UV_RAMBUT/UV_BUSANA}; {@code tools/cek_sosok.py} compares every number of this file with them
 * (regex on the {@code k(...)} lines, keep one entry per line).
 * <p>
 * Coordinates are model pixels relative to the parent's pivot (head: neck pivot, head -4..4, -8..0, -4..4; body: neck
 * pivot, -4..4, 0..12, -2..2; arms: the shoulder pivot). Fractional sizes keep integer UV footprints (the generator
 * paints the whole footprint). Pairs ({@link Kubus#pasangan}) share one UV; the second one is mirrored. For an
 * arm-parented pair the table pivot is the right arm's.
 */
public final class BusanaKatalog {

	/** One cube. {@code ramping} is the slim-arm box (arm cubes only), null otherwise. */
	public record Kubus(String id, String induk, float px, float py, float pz, float x0, float y0, float z0, float w, float h, float d, int u,
						int v, @Nullable String pegas, boolean pasangan, float rx, float ry, float rz, float[] ramping) {

		/** Width and height of the integer UV footprint on the atlas. */
		public int jejakW() {
			return (int) Math.ceil(2.0F * (d + w) - 1.0E-4F);
		}

		public int jejakH() {
			return (int) Math.ceil(d + h - 1.0E-4F);
		}
	}

	private static Kubus k(String id, String induk, float[] p, float[] b, int u, int v, @Nullable String pegas, boolean pasangan, float[] rot,
			float[] ramping) {
		return new Kubus(id, induk, p[0], p[1], p[2], b[0], b[1], b[2], b[3], b[4], b[5], u, v, pegas, pasangan, rot[0], rot[1], rot[2], ramping);
	}

	private static float[] p(float x, float y, float z) {
		return new float[] {x, y, z};
	}

	private static float[] b(float x0, float y0, float z0, float w, float h, float d) {
		return new float[] {x0, y0, z0, w, h, d};
	}

	private static float[] r(float x, float y, float z) {
		return new float[] {x, y, z};
	}

	private static final float[] NOL = {0.0F, 0.0F, 0.0F};

	/** Hair cubes and head ornaments, region C (64, 24)-(128, 64). */
	public static final List<Kubus> RAMBUT = List.of(
			k("sanggul", "kepala", p(0, -8, 0.5F), b(-1.5F, -3, -1.5F, 3, 3, 3), 98, 47, null, false, NOL, null),
			k("guan_rendah", "sanggul", p(0, -1, 0), b(-2, -1.2F, -2, 4, 2, 4), 110, 47, null, false, NOL, null),
			k("tusuk_guan", "guan_rendah", p(0, -0.4F, 0), b(-3.5F, -0.3F, -0.3F, 7, 0.6F, 0.6F), 70, 60, null, false, NOL, null),
			k("guan_tinggi", "sanggul", p(0, -1.5F, 0), b(-2, -2.5F, -2, 4, 4, 4), 64, 39, null, false, NOL, null),
			k("mahkota_teratai", "sanggul", p(0, -1.5F, 0), b(-2.5F, -1.2F, -2.5F, 5, 2, 5), 104, 39, null, false, NOL, null),
			k("rambut_atas", "kepala", p(0, -8, 4.6F), b(-4, 0, 0, 8, 8, 1), 96, 24, "RAMBUT", false, NOL, null),
			k("rambut_bawah", "badan", p(0, 0.3F, 2.6F), b(-3.5F, 0, 0, 7, 10, 1), 80, 24, "RAMBUT", false, NOL, null),
			k("rambut_bawah_panjang", "badan", p(0, 0.3F, 2.6F), b(-3.5F, 0, 0, 7, 14, 1), 64, 24, "RAMBUT", false, NOL, null),
			k("ekor_kuda_1", "kepala", p(0, -8.4F, 3.2F), b(-1, 0, -1, 2, 5, 2), 64, 47, "EKOR", false, r(25, 0, 0), null),
			k("ekor_kuda_2", "ekor_kuda_1", p(0, 5, 0), b(-0.9F, 0, -0.9F, 1.8F, 6, 1.8F), 80, 39, "EKOR", false, NOL, null),
			k("cepol", "kepala", p(2.6F, -8.2F, 0.4F), b(-1.5F, -3, -1.5F, 3, 3, 3), 64, 54, null, true, NOL, null),
			k("sanggul_tinggi", "kepala", p(0, -8, 1), b(-2.5F, -3, -2, 5, 3, 4), 72, 47, null, false, NOL, null),
			k("sanggul_rendah", "kepala", p(0, -3.5F, 4.2F), b(-2, -1.5F, 0, 4, 3, 3), 76, 54, null, false, NOL, null),
			k("kepang_1", "badan", p(0, 0.5F, 2.4F), b(-1, 0, -1, 2, 6, 2), 88, 39, "KEPANG", false, NOL, null),
			k("kepang_2", "kepang_1", p(0, 6, 0), b(-0.9F, 0, -0.9F, 1.8F, 6, 1.8F), 96, 39, "KEPANG", false, NOL, null),
			k("pita_rambut", "sanggul", p(1, -0.5F, 1.5F), b(-0.5F, 0, 0, 1, 6, 0.3F), 90, 47, "PITA", true, NOL, null),
			k("tusuk_buyao", "sanggul_tinggi", p(1.5F, -1.5F, 0), b(-0.5F, -0.5F, -0.5F, 5, 1, 1), 86, 60, null, false, r(0, -20, 0), null),
			k("manik_buyao", "tusuk_buyao", p(4.2F, 0.4F, 0), b(-0.4F, 0, -0.4F, 0.8F, 3, 0.8F), 108, 54, "BUYAO", false, NOL, null),
			k("bunga_rambut", "kepala", p(3.2F, -7.4F, 0), b(-1, -1, -0.5F, 2, 2, 1), 64, 60, null, false, NOL, null),
			k("jenggot_pendek", "kepala", p(0, -0.6F, -4.2F), b(-2, 0, -0.6F, 4, 4, 1), 94, 54, "JENGGOT", false, NOL, null),
			k("jenggot_panjang", "kepala", p(0, -0.6F, -4.2F), b(-2, 0, -0.6F, 4, 8, 1), 114, 24, "JENGGOT", false, NOL, null),
			k("kumis_naga", "kepala", p(1.8F, -2.2F, -4.3F), b(-0.5F, 0, -0.5F, 1, 4, 1), 104, 54, "KUMIS", true, NOL, null),
			k("alis_panjang", "kepala", p(3.4F, -5.6F, -4.2F), b(-0.4F, 0, -0.4F, 0.8F, 3, 0.8F), 112, 54, "ALIS", true, NOL, null),
			k("jambul_anak", "kepala", p(2, -8, 0), b(-1, -2, -1, 2, 2, 2), 116, 54, null, true, NOL, null),
			k("poni_samping", "kepala", p(3.6F, -6.5F, -2.6F), b(-0.5F, 0, -0.5F, 1, 6, 1), 94, 47, "PONI", true, NOL, null),
			k("ikat_dahi_ekor", "kepala", p(0.8F, -6.2F, 4.4F), b(-0.5F, 0, 0, 1, 5, 0.3F), 90, 54, "PITA", true, NOL, null));

	/** Garment cubes, the bamboo hat and small gear, region D (0, 64)-(128, 128). */
	public static final List<Kubus> BUSANA = List.of(
			k("rok_panjang_depan", "badan", p(0, 12, -2.1F), b(-4.5F, 0, -0.8F, 9, 11.5F, 0.8F), 80, 64, "ROK_DEPAN", false, NOL, null),
			k("rok_panjang_belakang", "badan", p(0, 12, 2.1F), b(-4.5F, 0, 0, 9, 11.5F, 0.8F), 100, 64, "ROK_BELAKANG", false, NOL, null),
			k("rok_panjang_sisi", "badan", p(4.2F, 12, 0), b(-0.4F, 0, -2.5F, 0.8F, 11.5F, 5), 20, 64, "ROK_SISI", true, NOL, null),
			k("rok_pendek_depan", "badan", p(0, 12, -2.1F), b(-4.5F, 0, -0.8F, 9, 6, 0.8F), 24, 102, "ROK_DEPAN", false, NOL, null),
			k("rok_pendek_belakang", "badan", p(0, 12, 2.1F), b(-4.5F, 0, 0, 9, 6, 0.8F), 44, 102, "ROK_BELAKANG", false, NOL, null),
			k("rok_pendek_sisi", "badan", p(4.2F, 12, 0), b(-0.4F, 0, -2.5F, 0.8F, 6, 5), 0, 82, "ROK_SISI", true, NOL, null),
			k("lengan_lebar_manset", "lengan", p(-1, 6, 0), b(-3, 0, -3, 6, 4, 6), 28, 82, null, true, NOL, b(-2.5F, 0, -3, 5, 4, 6)),
			k("lengan_lebar_kantong", "lengan_lebar_manset", p(0, 2, 2.2F), b(-2.5F, 0, 0, 5, 7, 2), 70, 82, "LENGAN", true, NOL, null),
			k("pelindung_lengan", "lengan", p(-0.5F, 6, 0), b(-2.5F, 0, -2.5F, 5, 4, 5), 84, 82, null, true, NOL, b(-2, 0, -2.5F, 4, 4, 5)),
			k("jubah_luar", "badan", p(0, 0.2F, 2.4F), b(-4.5F, 0, 0, 9, 17, 1), 0, 64, "JUBAH", false, NOL, null),
			k("jubah_luar_bahu", "badan", p(0, 0, 0), b(-5, -0.5F, -3, 10, 3, 6), 0, 93, null, false, NOL, null),
			k("kerah_tinggi", "badan", p(0, 0, 0), b(-4.5F, -2.5F, -2.5F, 9, 3, 5), 44, 93, null, false, NOL, null),
			k("sabuk", "badan", p(0, 6.5F, 0), b(-4.5F, 0, -2.5F, 9, 2, 5), 64, 102, null, false, NOL, null),
			k("pita_sabuk", "sabuk", p(1, 1.6F, -2.6F), b(-0.5F, 0, -0.3F, 1, 6, 0.3F), 92, 102, "PITA", true, NOL, null),
			k("liontin_giok", "sabuk", p(2.2F, 1.8F, -2.7F), b(-1, 0, -0.5F, 2, 4, 1), 6, 110, "RUMBAI", false, NOL, null),
			k("kantong", "sabuk", p(-2.8F, 1.5F, -2.6F), b(-1, 0, -1, 2, 3, 2), 12, 110, "RUMBAI", false, NOL, null),
			k("labu", "sabuk", p(-4.6F, 1.2F, 0.4F), b(-1.5F, 0, -1.5F, 3, 4, 3), 96, 102, "RUMBAI", false, NOL, null),
			k("bahu_zirah", "lengan", p(-0.5F, -2, 0), b(-2.5F, -0.5F, -2.5F, 5, 3, 5), 72, 93, null, true, NOL, null),
			k("rok_zirah", "badan", p(0, 11, -2.6F), b(-4.5F, 0, -0.5F, 9, 6, 1), 108, 102, "ROK_ZIRAH", false, NOL, null),
			k("selendang_1", "lengan", p(-1, 7, 2), b(-1, 0, -0.25F, 2, 8, 0.5F), 32, 93, "SELENDANG", true, NOL, null),
			k("selendang_2", "selendang_1", p(0, 8, 0), b(-1, 0, -0.25F, 2, 8, 0.5F), 38, 93, "SELENDANG", true, NOL, null),
			k("selendang_punggung", "badan", p(0, 4, 3), b(-6, 0, -0.25F, 12, 3, 0.5F), 20, 110, "SELENDANG", false, r(30, 0, 0), null),
			k("celemek", "badan", p(0, 4, -2.4F), b(-3.5F, 0, -0.5F, 7, 10, 1), 12, 82, "ROK_DEPAN", false, NOL, null),
			k("lencana_giok", "sabuk", p(-1.6F, 1.8F, -2.7F), b(-1, 0, -0.5F, 2, 3, 1), 46, 110, "RUMBAI", false, NOL, null),
			k("cincin_kasaya", "badan", p(2, 3, -2.6F), b(-1, -1, -0.5F, 2, 2, 1), 52, 110, null, false, NOL, null),
			k("buntalan", "badan", p(0, 1, 2.2F), b(-3, 0, 0, 6, 7, 3), 52, 82, null, false, r(0, 0, 20), null),
			k("rumbai_iblis", "badan", p(0, 12, 2.2F), b(-1, 0, -0.5F, 2, 6, 1), 0, 110, "RUMBAI", false, NOL, null),
			k("kerah_bulu", "badan", p(0, -0.5F, 0), b(-5, -1, -3, 10, 2, 6), 92, 93, null, false, NOL, null),
			k("caping_bambu", "kepala", p(0, -8.2F, 0), b(-6, -1, -6, 12, 1, 12), 32, 64, null, false, NOL, null),
			k("caping_puncak", "caping_bambu", p(0, -1, 0), b(-3, -2, -3, 6, 2, 6), 0, 102, null, false, NOL, null));

	private static final Map<String, Integer> INDEKS_RAMBUT = new HashMap<>();
	private static final Map<String, Integer> INDEKS_BUSANA = new HashMap<>();

	static {
		for (int i = 0; i < RAMBUT.size(); i++) {
			INDEKS_RAMBUT.put(RAMBUT.get(i).id(), i);
		}
		for (int i = 0; i < BUSANA.size(); i++) {
			INDEKS_BUSANA.put(BUSANA.get(i).id(), i);
		}
	}

	/** Bit index of a hair cube id, or -1. */
	public static int rambut(String id) {
		Integer i = INDEKS_RAMBUT.get(id);
		return i == null ? -1 : i;
	}

	/** Bit index of a garment cube id, or -1. */
	public static int busana(String id) {
		Integer i = INDEKS_BUSANA.get(id);
		return i == null ? -1 : i;
	}

	/**
	 * Cubes kept at LOD KURANG (40.6.1): skirts, wide sleeves, the outer robe, long hair, the ponytail (still), the bamboo
	 * hat. Everything else only at PENUH.
	 */
	public static boolean besar(String id) {
		return id.startsWith("rok_") || id.startsWith("lengan_lebar") || id.equals("jubah_luar") || id.startsWith("rambut_bawah")
				|| id.startsWith("ekor_kuda") || id.startsWith("caping");
	}

	/** Cubes drawn in the Iris shadow pass on Tinggi (skirts, long hair, hat); on Sedang only the skirts. */
	public static int bayangan(String id) {
		if (id.startsWith("rok_")) {
			return 1;
		}
		return id.startsWith("rambut_bawah") || id.startsWith("caping") ? 2 : 3;
	}

	private BusanaKatalog() {
	}
}
