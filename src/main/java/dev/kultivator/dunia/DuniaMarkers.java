package dev.kultivator.dunia;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The shared marker table of DESIGN 34.4.8 (contract C34.S5): producers only publish site kinds, module T draws them from
 * {@code textures/dunia/peta/markers.png} (16 × 16 cells, {@code icon} = cell index, row-major, 8 per row; icons from
 * {@link #ATLAS2_FIRST} on are cells of the second atlas {@code markers_tempat.png}, same layout). {@code minZoom}
 * is the coarsest zoom (0 = 1 px per 256 b, 1 = 64 b, 2 = 16 b) that still shows the marker. Unknown kinds get
 * {@link #UNKNOWN}. Lang key {@code kultivator.dunia.marker.<kind with '/' as '.'>}.
 */
public final class DuniaMarkers {

	public record Marker(int icon, int color, String langKey, int minZoom) {
	}

	private static final Map<String, Marker> TABLE = new LinkedHashMap<>();
	public static final Marker UNKNOWN = new Marker(31, 0xC8C8C8, "kultivator.dunia.marker.unknown", 1);

	static {
		put("kota/kota", 0, 0xE0B050, 0);
		put("kota/kota_abadi", 1, 0xFFF0C0, 0);
		put("reruntuhan/gua_kecil", 2, 0x9AA890, 1);
		put("reruntuhan/gua_agung", 3, 0x7FA0B8, 1);
		put("reruntuhan/gua_kuno", 4, 0xC890FF, 0);
		put("penjara/penjara", 5, 0xC03030, 0);
		put("menara/menara", 6, 0xE8E8FF, 0);
		put("bawah/kuil", 7, 0x6FA0A0, 1);
		put("bawah/istana_yama", 8, 0x8A1E1E, 0);
		put("bawah/desa_arwah", 9, 0x7A8C9A, 1);
		put("laut/pusaran", 10, 0x40A8E0, 0);
		put("laut/pulau", 11, 0x60C080, 1);
		put("laut/istana_naga", 12, 0x30D0C0, 0);
		put("laut/penglai", 13, 0xFFE0A0, 0);
		put("langit/harta", 14, 0xFF80FF, 0);
		put("langit/meteor", 15, 0x90C0FF, 1);
		put("naga/naga", 16, 0x60E0A0, 0);
		put("kapal/kapal", 17, 0xD0A060, 1);
		put("kapal/dermaga", 18, 0xA08060, 1);
		put("sekte/aula", 19, 0xFFD27A, 0);
		put("sekte/wilayah", 20, 0xFFD27A, 0);
		put("desa/desa", 21, 0xB0C890, 1);
		put("kota/wilayah", 22, 0xC0A070, 0);
		// the gates of the NPC sects (Sekte 36 diplomasi, AnggotaSekte.SITE_KIND): the sect hall icon in a rival's red
		put("sekte/gerbang", 19, 0xE07060, 0);
		// the floating palaces of the NPC sky sects (Sekte Langit 37G, LangitNpcAnchors.SITE_KIND): the Penglai island icon in sky blue
		put("sekte/istana_langit", 13, 0xCFE8FF, 0);
		// DESIGN 38.D.12 + 38.T (38W): icons 32-60 live in the second atlas markers_tempat.png (cell = icon - 32;
		// cells 23-31 of markers.png already belong to module T, so the design's numbers 23-49 are shifted by 9)
		put("kota/kota_besar", 32, 0xFFD27A, 0);
		put("kampung/dusun", 33, 0xB8A070, 1);
		put("kampung/desa", 33, 0xB8A070, 1);
		put("kampung/desa_besar", 33, 0xC8B080, 0);
		put("kampung/klan", 34, 0xC08060, 1);
		put("sekte/kompleks", 35, 0xE8C890, 0);
		put("gua/pertapa", 36, 0xB0A8C8, 1);
		put("gua/makam", 37, 0xD8C890, 1);
		put("gua/sarang_binatang", 38, 0xA08850, 1);
		put("gua/sarang_iblis", 39, 0x8A2030, 1);
		put("gua/urat_roh", 40, 0xB8A6F0, 1);
		put("gua/cermin_hati", 41, 0xA8D8F0, 1);
		put("situs/puncak_petir", 42, 0xC8E8FF, 1);
		put("situs/panggung_bintang", 43, 0xFFF0A0, 1);
		put("situs/mata_air_roh", 44, 0x80E0E0, 1);
		put("pos/singgah", 45, 0xD8C8A0, 1);
		put("pos/singgah_besar", 45, 0xE8D8B0, 1);
		put("pos/dagang", 46, 0xE0C080, 1);
		put("pos/tonggak", 47, 0xB8B0A0, 2);
		put("pos/kemah_begal", 48, 0xC06040, 1);
		put("tempat/kafilah", 49, 0xC8A070, 1);
		put("tempat/acara", 50, 0xFF9060, 0);
		put("kota_tema/pelabuhan", 51, 0x5FA8C8, 0);
		put("kota_tema/oasis", 52, 0xE0B060, 0);
		put("kota_tema/air", 53, 0x7FC8B0, 0);
		put("kota_tema/gunung", 54, 0xA0A8B8, 0);
		put("kota_tema/iblis", 55, 0x8A1020, 0);
		put("kota_tema/reruntuhan_gurun", 56, 0xC09050, 1);
		put("kota_tema/pos_pemburu", 57, 0xD8E4F0, 1);
		put("tema/kapal_dagang", 58, 0xE8C890, 1);
		put("pos/kuil_jalan", 59, 0xD06050, 2);
		put("pos/penyeberangan", 60, 0x80B8E0, 1);
		// DESIGN 39C: a captured disciple waiting for its master (live site of Tawanan, 3 world days): the bandit camp icon in alarm red
		put("mandiri/tawanan", 48, 0xFF4040, 0);
		// DESIGN 41.11.2: the NPC sect the player has joined (Gabung Sekte): the sect gate icon in a member's jade green
		put("gabung/sekteku", 19, 0x8FE6B0, 0);
		// DESIGN 42.5.5: a way a resident showed ([Tandai] of Tanya Jalan, live 5 minutes): the milestone icon in bright gold
		put("kota/tujuan", 47, 0xFFE15A, 0);
	}

	/** Icons below this index are cells of {@code markers.png}; from it on, cells {@code icon - 32} of {@code markers_tempat.png}. */
	public static final int ATLAS2_FIRST = 32;

	private static void put(String kind, int icon, int color, int minZoom) {
		TABLE.put(kind, new Marker(icon, color, "kultivator.dunia.marker." + kind.replace('/', '.'), minZoom));
	}

	/** The marker of a site kind (exact kind first, then {@code <module>/<module>}, else {@link #UNKNOWN}). */
	public static Marker kind(String kind) {
		if (kind == null) {
			return UNKNOWN;
		}
		Marker marker = TABLE.get(kind);
		if (marker != null) {
			return marker;
		}
		int slash = kind.indexOf('/');
		String module = slash < 0 ? kind : kind.substring(0, slash);
		return TABLE.getOrDefault(module + "/" + module, UNKNOWN);
	}

	/** Every known kind (read-only), in icon order. */
	public static Map<String, Marker> all() {
		return java.util.Collections.unmodifiableMap(TABLE);
	}

	private DuniaMarkers() {
	}
}
