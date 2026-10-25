package dev.kultivator.dunia.kota42.api;

import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import org.jetbrains.annotations.Nullable;

/**
 * A 42 module's say over one resident's activity, with the stored person already resolved (DESIGN 42.4.12, 42.4.14,
 * 42.4A.2). Registered through {@code WargaApi.daftarPenimpa}; 42A bridges all of them into one 38 overrider
 * ({@code Jadwal.addPenimpa}), so each 42 module never touches {@code dunia/tempat/hidup}. Order: lower {@link #urutan()}
 * first (ceremony 100, habit 200, daily plan 300, other 400 as in {@code Jadwal.URUT_*}); the first non-null answer wins.
 * Called for every resident with a Warga every place cycle: keep it cheap and deterministic for the same inputs.
 */
@FunctionalInterface
public interface PenimpaJadwalKota {

	/**
	 * @param warga the stored person occupying slot {@code p}
	 * @param waktu overworld clock time (absolute ticks) the resident lives
	 * @param jam   the resident's own hour
	 * @param now   game time
	 * @return the activity instead of the round, or null to leave it
	 */
	@Nullable Kegiatan timpa(TempatHuni huni, Warga warga, Penduduk p, long waktu, int jam, long now);

	/** The order among overriders (ceremony 100, habit 200, daily plan 300, other 400). */
	default int urutan() {
		return 400;
	}
}
