package dev.kultivator.dunia.kota42.warga;

import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.api.PenimpaJadwalKota;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.PendudukData;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import org.jetbrains.annotations.Nullable;

/**
 * The 42A-2 schedule overrider (order {@value Jadwal#URUT_KEBIASAAN}, DESIGN 42.4.7-42.4.9, 42.4.12), registered through
 * {@link WargaApi#daftarPenimpa}: what a stored person does instead of its role's round, in this order:
 * <ol>
 * <li>a family moving away packs on its last evening and waves goodbye to the neighbours ({@link Migrasi#pamit});</li>
 * <li>an apprentice works beside its master during work hours, copying the master's motion ({@link Pekerjaan#magang});</li>
 * <li>a gifted child stares at the sky at dusk ({@link Berbakat#melamun});</li>
 * <li>the resident's habits, or a guest seat at another's habit (children around the storyteller, {@link Kebiasaan}).</li>
 * </ol>
 * A resident in a life state other than healthy (sick, mourning, just arrived) is left alone: the 38 state bending and
 * 42A's states win. Cheap and deterministic: every answer is a pure function of resident, slot, clock and day.
 */
public final class PenimpaWarga implements PenimpaJadwalKota {

	private static boolean ready;

	/** Registers the overrider. From {@code ArsipWarga.init}; safe twice. */
	public static void init() {
		if (ready) {
			return;
		}
		ready = true;
		WargaApi.daftarPenimpa(new PenimpaWarga());
	}

	@Override
	public @Nullable Kegiatan timpa(TempatHuni huni, Warga w, Penduduk p, long waktu, int jam, long now) {
		if (!KotaConfig.warga().aktif || w.status != Warga.Status.HIDUP || huni.keadaanDari(p.id()) != PendudukData.SEHAT) {
			return null;
		}
		Kegiatan k = Migrasi.pamit(huni, w, p, waktu, jam);
		if (k == null) {
			k = Pekerjaan.magang(huni, w, p, waktu, jam);
		}
		if (k == null) {
			k = Berbakat.melamun(huni, w, p, waktu, jam);
		}
		if (k == null) {
			k = Kebiasaan.kegiatan(huni, w, p, waktu, jam, now);
		}
		return k;
	}

	@Override
	public int urutan() {
		return Jadwal.URUT_KEBIASAAN;
	}
}
