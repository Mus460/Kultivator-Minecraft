package dev.kultivator.dunia.kota42.cakap;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Event appraisal (DESIGN 42.5A.3, OCC): an event kind maps to a PAD push that the engine scales by intensity and
 * {@code (1 + E/100)}. Kinds come from {@code percakapan/emosi.json} (section {@code peristiwa}, loaded by 42K) and from
 * code: other modules register their own kinds once at init with {@link #daftar} (e.g. 42D {@code upacara_nikah}, 42C
 * {@code dicurangi}) and then report events through {@code Cakap.peristiwa(warga, jenis, sasaran, intensitas)}.
 * Data entries with the same id override code registrations (data wins, so balance is tuned without code).
 * <p>
 * Frozen API (C42.K1, 42-G0); 42K adds the appraisal logic around it in 42-G1.
 */
public final class Penilaian {

	/**
	 * A PAD push, each axis -1..1.
	 *
	 * @param p pleasure
	 * @param a arousal
	 * @param d dominance
	 */
	public record Dorongan(float p, float a, float d) {
		public Dorongan {
			p = Math.max(-1.0F, Math.min(1.0F, p));
			a = Math.max(-1.0F, Math.min(1.0F, a));
			d = Math.max(-1.0F, Math.min(1.0F, d));
		}
	}

	private static final Map<String, Dorongan> KODE = new LinkedHashMap<>();

	/** Registers (or replaces) the code default of an event kind. Ids are lower_snake ASCII. */
	public static synchronized void daftar(String jenis, Dorongan dorongan) {
		if (jenis == null || jenis.isEmpty() || dorongan == null) {
			throw new IllegalArgumentException("Penilaian.daftar: empty kind or push");
		}
		KODE.put(jenis, dorongan);
	}

	/** The code default of a kind, null when unregistered (42K checks data first). */
	public static synchronized @Nullable Dorongan kode(String jenis) {
		return KODE.get(jenis);
	}

	/** Every code-registered kind in registration order (for {@code cek} and the test command). */
	public static synchronized Map<String, Dorongan> semuaKode() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(KODE));
	}

	private Penilaian() {
	}
}
