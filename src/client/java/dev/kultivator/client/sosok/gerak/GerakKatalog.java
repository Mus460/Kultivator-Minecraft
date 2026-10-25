package dev.kultivator.client.sosok.gerak;

import dev.kultivator.sosok.GerakId;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The clip catalog of the shared Gerak system (DESIGN 40.5.10-40.5.13, contract C40.G1). Ids 1-176 are DESIGN 38 and 40
 * ({@link KatalogTempat}, {@link KatalogSosok}); 200-469 are registered by other modules from their client init with
 * {@link #daftar} (39E 200-249, 39F 250-299, later DESIGN 38 and DESIGN 41 300-349, DESIGN 42 350-469). The disciple-activity map (autonomous activities
 * 40-56 of DESIGN 39, defaults for 57-60) tells {@code DutyPoses} which clip shows an activity; owners may remap with
 * {@link #petakanAktivitas}. Client effect hooks for {@code KAIT} events are registered with {@link #daftarKait}.
 * <p>
 * Built lazily on first use (so registration order between modules does not matter: a mapping or clip registered by
 * another module before the catalog is built is kept; the built-in activity defaults never overwrite one). Client thread
 * only.
 */
public final class GerakKatalog {

	private static final int UKURAN = GerakId.KOTA_HIDUP_LAST + 1;
	private static final int AKTIVITAS_MAKS = 128;

	private static final Klip[] KLIP = new Klip[UKURAN];
	private static final int[] AKT_KLIP = new int[AKTIVITAS_MAKS];
	private static final int[] AKT_VARIAN = new int[AKTIVITAS_MAKS];
	private static final boolean[] AKT_PEMILIK = new boolean[AKTIVITAS_MAKS];
	private static final Map<String, KaitGerak> KAIT = new HashMap<>();
	private static boolean siap;
	private static int jumlah;

	/** Builds the built-in catalog once (GerakKlien.init calls it; every accessor does too). */
	public static void pastikan() {
		if (siap) {
			return;
		}
		siap = true;
		KatalogTempat.daftarkan(GerakKatalog::pasang);
		KatalogSosok.daftarkan(GerakKatalog::pasang);
		KatalogSosok.petakan();
	}

	/** The clip with this id, or null (unknown, reserved 19, or not registered). */
	public static @Nullable Klip klip(int id) {
		pastikan();
		return id > 0 && id < UKURAN ? KLIP[id] : null;
	}

	/** Number of clips in the catalog (built-in and registered). */
	public static int jumlah() {
		pastikan();
		return jumlah;
	}

	/**
	 * Registers a clip of another module (ids 200-469 only: 39E 200-249, 39F 250-299, DESIGN 38/41 300-349, DESIGN 42
	 * 350-469). Registering an id twice replaces it.
	 */
	public static void daftar(Klip klip) {
		int id = klip.id();
		if (id < GerakId.MANDIRI_FIRST || id > GerakId.KOTA_HIDUP_LAST) {
			throw new IllegalArgumentException("Gerak clip id outside 200-469: " + id);
		}
		pastikan();
		simpan(klip);
	}

	/**
	 * Maps a disciple activity to a clip (and variant) that {@code DutyPoses} plays for it. Activities 13-27 are the
	 * existing function poses and cannot be mapped; 0-12 are the base duties.
	 */
	public static void petakanAktivitas(int aktivitas, int klip, int varian) {
		periksaAktivitas(aktivitas);
		AKT_KLIP[aktivitas] = klip;
		AKT_VARIAN[aktivitas] = varian;
		AKT_PEMILIK[aktivitas] = true;
	}

	/** Installs a built-in activity default; never overwrites an owner's mapping. */
	static void petakanBawaan(int aktivitas, int klip, int varian) {
		periksaAktivitas(aktivitas);
		if (!AKT_PEMILIK[aktivitas]) {
			AKT_KLIP[aktivitas] = klip;
			AKT_VARIAN[aktivitas] = varian;
		}
	}

	private static void periksaAktivitas(int aktivitas) {
		if (aktivitas < 28 || aktivitas >= AKTIVITAS_MAKS) {
			throw new IllegalArgumentException("Activity cannot carry a Gerak clip: " + aktivitas);
		}
	}

	/** The clip an activity shows (0 = none). */
	public static int klipAktivitas(int aktivitas) {
		pastikan();
		return aktivitas >= 0 && aktivitas < AKTIVITAS_MAKS ? AKT_KLIP[aktivitas] : 0;
	}

	/** The variant of {@link #klipAktivitas}. */
	public static int varianAktivitas(int aktivitas) {
		return aktivitas >= 0 && aktivitas < AKTIVITAS_MAKS ? AKT_VARIAN[aktivitas] : 0;
	}

	/** Registers the client effect of a {@code KAIT} event id (replaces an earlier one). */
	public static void daftarKait(String id, KaitGerak kait) {
		KAIT.put(id, kait);
	}

	/** The hook of an id, or null. */
	public static @Nullable KaitGerak kait(String id) {
		return KAIT.get(id);
	}

	private static void pasang(Klip klip) {
		int id = klip.id();
		if (id <= 0 || id > GerakId.SOSOK_LAST || !GerakId.ada(id)) {
			throw new IllegalStateException("Built-in Gerak clip with an id GerakId does not define: " + id);
		}
		simpan(klip);
	}

	private static void simpan(Klip klip) {
		if (KLIP[klip.id()] == null) {
			jumlah++;
		}
		KLIP[klip.id()] = klip;
	}

	private GerakKatalog() {
	}
}
