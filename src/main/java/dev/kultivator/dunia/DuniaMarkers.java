package dev.kultivator.dunia;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The shared marker table of DESIGN 34.4.8 (contract C34.S5): producers only publish site kinds, module T draws them from
 * {@code textures/dunia/peta/markers.png} (16 × 16 cells, {@code icon} = cell index, row-major, 8 per row). {@code minZoom}
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
	}

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
