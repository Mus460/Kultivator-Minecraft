package dev.kultivator.sosok;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Life path looks of a disciple (DESIGN 40.4.3; contract C40.I3): the string ids DESIGN 39 uses ({@code JalanHidup},
 * 39.3.1) mapped to the 5-bit {@code jalan} field of {@link Gaya}. Bits 1-10 are exactly the ten paths of DESIGN 39 in
 * its table order; bits 11-15 are extra path looks other modules may use (DESIGN 38 for NPCs, DESIGN 39 later). Bit 0
 * means "no path yet". Ids and bits are never renumbered.
 * <p>
 * 39 calls {@code SosokApi.setJalan(disciple, id)} when a path is chosen, changed, overridden by {@code iblis} or
 * redeemed; the look row of 40.4.3 (marks, cubes, props, idle stance, signature clips) is added on top of the rank row.
 * The look key of a path is {@code jalan.<id>} ({@link #kunciRupa(int)}).
 */
public final class SosokJalan {

	/** No path. */
	public static final int TIDAK_ADA = 0;

	/** Sword cultivator: hair tied with a tailed headband, sharp eyes, sword at the left hip. */
	public static final int PEDANG = 1;
	/** Alchemist: soot smudge, tied sleeves, pill bottle at the right hip. */
	public static final int ALKEMIS = 2;
	/** Formation and talisman master: ink on the fingers, trigram hems, brush in the bun. */
	public static final int FORMASI = 3;
	/** Hermit: loose hair, patched robe, prayer beads in hand. */
	public static final int PERTAPA = 4;
	/** Adventurer: travelling kit, sun-darkened skin, small scars. */
	public static final int PETUALANG = 5;
	/** Beast hunter: fur collar, claw scar, fang trophy on the left shoulder. */
	public static final int PEMBURU = 6;
	/** Merchant: merchant robe in town, abacus at the right hip, crate on the back while carrying. */
	public static final int PEDAGANG = 7;
	/** Scholar of scriptures: scholar robe in the pavilion, scroll at the left hip. */
	public static final int CENDEKIA = 8;
	/** Sect guardian: duty armour, shoulder plates, spear on watch. */
	public static final int PENJAGA = 9;
	/** Demonic path (a layer over another path): hair darkening to deep red, red irises, blood hems. */
	public static final int IBLIS = 10;
	/** Extra: smith (bare right arm, leather apron). */
	public static final int TEMPA = 11;
	/** Extra: physician (gentle eyes, medicine box). */
	public static final int TABIB = 12;
	/** Extra: spirit farmer (rolled sleeves, bamboo hat in the garden). */
	public static final int PETANI_ROH = 13;
	/** Extra: musician (flute or guqin). */
	public static final int MUSISI = 14;
	/** Extra: noble (noble robe, gold jade pendant). */
	public static final int BANGSAWAN = 15;

	/** Highest path bit in use. */
	public static final int MAKS = 15;
	/** The paths that belong to DESIGN 39 (bits 1-10). */
	public static final int JALAN_39_LAST = IBLIS;

	private static final String[] ID = {null, "pedang", "alkemis", "formasi", "pertapa", "petualang", "pemburu",
			"pedagang", "cendekia", "penjaga", "iblis", "tempa", "tabib", "petani_roh", "musisi", "bangsawan"};

	/**
	 * Other spellings accepted by {@link #bit(String)}: 39's display names and enum names (upper case works because the
	 * lookup is case-insensitive) and the short words the look tables use.
	 */
	public static final Map<String, String> ALIAS;

	private static final Map<String, Integer> BIT = new HashMap<>();

	static {
		Map<String, String> alias = new HashMap<>();
		alias.put("pendekar_pedang", "pedang");
		alias.put("pendekar", "pedang");
		alias.put("alkimia", "alkemis");
		alias.put("peramu", "alkemis");
		alias.put("ahli_formasi", "formasi");
		alias.put("formasi_jimat", "formasi");
		alias.put("jimat", "formasi");
		alias.put("petapa", "pertapa");
		alias.put("pengembara", "petualang");
		alias.put("pemburu_binatang", "pemburu");
		alias.put("saudagar", "pedagang");
		alias.put("cendekiawan", "cendekia");
		alias.put("cendekiawan_kitab", "cendekia");
		alias.put("sarjana", "cendekia");
		alias.put("penjaga_sekte", "penjaga");
		alias.put("jalan_iblis", "iblis");
		alias.put("empu", "tempa");
		alias.put("pandai", "tempa");
		alias.put("pandai_besi", "tempa");
		alias.put("penyembuh", "tabib");
		alias.put("petani", "petani_roh");
		alias.put("pemusik", "musisi");
		alias.put("bangsawan_muda", "bangsawan");
		ALIAS = Map.copyOf(alias);
		for (int i = 1; i < ID.length; i++) {
			BIT.put(ID[i], i);
		}
	}

	/** The bit (1-15) of a path id or alias (case-insensitive), or 0 for null, empty or unknown. */
	public static int bit(@Nullable String id) {
		if (id == null || id.isEmpty()) {
			return TIDAK_ADA;
		}
		String key = id.trim().toLowerCase(Locale.ROOT);
		Integer bit = BIT.get(key);
		if (bit == null) {
			String target = ALIAS.get(key);
			bit = target != null ? BIT.get(target) : null;
		}
		return bit != null ? bit : TIDAK_ADA;
	}

	/** The canonical id of a bit, or null for 0 and unknown bits. */
	public static @Nullable String id(int bit) {
		return bit >= 1 && bit <= MAKS ? ID[bit] : null;
	}

	/** True for the ten DESIGN 39 paths (bits 1-10). */
	public static boolean jalan39(int bit) {
		return bit >= 1 && bit <= JALAN_39_LAST;
	}

	/** The look key of a path ({@code jalan.<id>}), or null for no path. */
	public static @Nullable String kunciRupa(int bit) {
		String id = id(bit);
		return id != null ? "jalan." + id : null;
	}

	/** Lang key of a path look's name ({@code kultivator.sosok.jalan.<id>}, fragment {@code sosok}, 40I). */
	public static String kunciLang(int bit) {
		String id = id(bit);
		return "kultivator.sosok.jalan." + (id != null ? id : "tidak_ada");
	}

	private SosokJalan() {
	}
}
