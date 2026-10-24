package dev.kultivator.sekte.mandiri;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.api.HatiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.api.Dorong;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * The seven drives (DESIGN 39.4.1), 0-100 in {@code extra.mandiri.dorong}. {@link #perJam} moves them once per world hour
 * while the body is loaded, from what the disciple did in that hour; {@link #setimbang} puts them at the path's resting
 * point on catch-up (closed form, no hour loop). Loyalty and contentment are never copied: the planner reads them live.
 * <p>
 * Rules of the table: hunger for cultivation +4/h while the bar is not full (x1.5 ambitious, x0.7 lazy), -8 meditating,
 * -12 in seclusion, -5 training; boredom +3/h doing the same as last hour and +1/h in the same place, -10 for something
 * new today, -25 on a journey, -8 with friends; fatigue +6/h training/sparring/work, +4/h walking far, +2/h crafting,
 * -10/h resting, -4/h meditating, -15 eating; company +2/h alone (x1.5 caring, x0.5 calm), -20 tea, -10 any shared
 * activity; ambition from the traits (ambitious 70, calm 30, else 50) +10 per priority-1 goal older than 10 days, -20
 * after a goal is reached (it drifts back 5/h); supplies = the open shopping list; wound = missing health (severe 100).
 */
public final class Dorongan {

	/** Drive values for the planner: a fixed array by {@link Dorong#ordinal()}. */
	public static int[] baca(DiscipleData data) {
		CompoundTag tag = MandiriApi.bagian(data).getCompoundOrEmpty(MandiriIds.K_DORONG);
		int[] out = new int[Dorong.JUMLAH];
		for (Dorong d : Dorong.values()) {
			out[d.ordinal()] = clamp(tag.getIntOr(d.id(), awal(d)));
		}
		return out;
	}

	public static int baca(DiscipleData data, Dorong d) {
		return clamp(MandiriApi.bagian(data).getCompoundOrEmpty(MandiriIds.K_DORONG).getIntOr(d.id(), awal(d)));
	}

	public static void tulis(DiscipleData data, Dorong d, int value) {
		CompoundTag section = MandiriApi.bagianTulis(data);
		CompoundTag tag = section.getCompound(MandiriIds.K_DORONG).orElse(null);
		if (tag == null) {
			tag = new CompoundTag();
			section.put(MandiriIds.K_DORONG, tag);
		}
		tag.putInt(d.id(), clamp(value));
	}

	public static void tambah(DiscipleData data, Dorong d, int delta) {
		tulis(data, d, baca(data, d) + delta);
	}

	/** A fresh disciple's drives (before the first hour). */
	private static int awal(Dorong d) {
		return switch (d) {
			case KULTIVASI -> 40;
			case BOSAN -> 20;
			case LELAH -> 10;
			case SOSIAL -> 30;
			case AMBISI -> 50;
			case BARANG, LUKA -> 0;
		};
	}

	/**
	 * One world hour passed while loaded. {@code lalu} is what the disciple did in that hour (KOSONG when nothing of the
	 * planner), {@code sebelumnya} the hour before, {@code tempatSama} whether it stayed at the same spot, {@code sendiri}
	 * whether no hall-mate stood within 6 blocks, {@code baruHariIni} whether {@code lalu} was new today.
	 */
	public static void perJam(DiscipleEntity entity, Kegiatan lalu, Kegiatan sebelumnya, boolean tempatSama, boolean sendiri,
			boolean baruHariIni) {
		DiscipleData data = entity.data();
		HatiService hati = SekteApi.hati();
		int[] d = baca(data);

		// Hunger for cultivation.
		double naik = data.barFull() || data.rank().isMax() ? 0.0D : 4.0D;
		if (hati.has(data, Sifat.AMBISIUS)) {
			naik *= 1.5D;
		}
		if (hati.has(data, Sifat.MALAS)) {
			naik *= 0.7D;
		}
		int turun = switch (lalu) {
			case MEDITASI, SERAP_BATU -> 8;
			case TUTUP_DIRI -> 12;
			case LATIH_JURUS, TANDING -> 5;
			case BELAJAR, MERENUNG -> 3;
			default -> 0;
		};
		d[Dorong.KULTIVASI.ordinal()] += (int) Math.round(naik) - turun;

		// Boredom.
		int bosan = 0;
		if (lalu != Kegiatan.KOSONG && lalu == sebelumnya) {
			bosan += 3;
		}
		if (tempatSama) {
			bosan += 1;
		}
		if (baruHariIni && lalu != Kegiatan.KOSONG) {
			bosan -= 10;
		}
		if (lalu == Kegiatan.PERJALANAN) {
			bosan -= 25;
		}
		if (lalu == Kegiatan.SOSIAL || lalu == Kegiatan.BERMAIN) {
			bosan -= 8;
		}
		d[Dorong.BOSAN.ordinal()] += bosan;

		// Fatigue.
		int lelah = switch (lalu) {
			case LATIH_JURUS, TANDING, KEBUN, TAMBANG, POS_TUGAS, JAGA_GERBANG -> 6;
			case PERJALANAN, BERANGKAT, TIBA -> 4;
			case MERAMU, JIMAT, JAGA_FORMASI, MENGAJAR -> 2;
			case ISTIRAHAT -> -10;
			case MEDITASI, TUTUP_DIRI, SERAP_BATU -> -4;
			case MAKAN -> -15;
			case KOSONG -> Irama.malam(Irama.jam(entity.level())) ? -3 : 1;
			default -> 1;
		};
		d[Dorong.LELAH.ordinal()] += lelah;

		// Company.
		int sosial = 0;
		if (sendiri && lalu != Kegiatan.TUTUP_DIRI) {
			double up = 2.0D;
			if (hati.has(data, Sifat.PENYAYANG)) {
				up *= 1.5D;
			}
			if (hati.has(data, Sifat.TENANG)) {
				up *= 0.5D;
			}
			sosial += (int) Math.round(up);
		}
		if (lalu == Kegiatan.SOSIAL) {
			sosial -= 20;
		} else if (lalu == Kegiatan.TANDING || lalu == Kegiatan.MAKAN && !sendiri || lalu == Kegiatan.BERMAIN
				|| lalu == Kegiatan.MENGAJAR || lalu == Kegiatan.JAGA_TEROBOSAN) {
			sosial -= 10;
		}
		d[Dorong.SOSIAL.ordinal()] += sosial;

		// Ambition drifts toward its target by 5 per hour.
		int target = ambisiSasaran(Irama.hari(entity.level()), data);
		int ambisi = d[Dorong.AMBISI.ordinal()];
		d[Dorong.AMBISI.ordinal()] = ambisi + Math.max(-5, Math.min(5, target - ambisi));

		d[Dorong.BARANG.ordinal()] = barang(data);
		d[Dorong.LUKA.ordinal()] = luka(entity);

		for (Dorong each : Dorong.values()) {
			tulis(data, each, d[each.ordinal()]);
		}
	}

	/** The resting point of a path, used on catch-up (39.4.7 step 3). */
	public static void setimbang(DiscipleEntity entity, long hari) {
		DiscipleData data = entity.data();
		JalanHidup jalan = MandiriApi.jalan(data);
		int kultivasi = data.barFull() ? 15 : jalan == JalanHidup.PERTAPA ? 55 : 40;
		int bosan = jalan == JalanHidup.PETUALANG || jalan == JalanHidup.PEDAGANG ? 45 : jalan == JalanHidup.PERTAPA ? 15 : 30;
		int sosial = jalan == JalanHidup.PERTAPA ? 20 : jalan == JalanHidup.PEDAGANG ? 50 : 35;
		tulis(data, Dorong.KULTIVASI, kultivasi);
		tulis(data, Dorong.BOSAN, bosan);
		tulis(data, Dorong.LELAH, 25);
		tulis(data, Dorong.SOSIAL, sosial);
		tulis(data, Dorong.AMBISI, ambisiSasaran(hari, data));
		tulis(data, Dorong.BARANG, barang(data));
		tulis(data, Dorong.LUKA, luka(entity));
	}

	/** Ambition's target: traits plus 10 per priority-1 goal unfinished for more than 10 days. */
	public static int ambisiSasaran(long hari, DiscipleData data) {
		HatiService hati = SekteApi.hati();
		int base = hati.has(data, Sifat.AMBISIUS) ? 70 : hati.has(data, Sifat.TENANG) ? 30 : 50;
		ListTag cita = MandiriApi.bagian(data).getListOrEmpty(MandiriIds.K_CITA);
		for (int i = 0; i < cita.size(); i++) {
			CompoundTag goal = cita.getCompoundOrEmpty(i);
			if (goal.getIntOr("prio", 3) == 1 && hari - goal.getLongOr("mulai", hari) > 10L) {
				base += 10;
			}
		}
		return clamp(base);
	}

	/** The open shopping list (39C writes it): each line weighs {@code (4 - prio) x 10}, at most 100. */
	public static int barang(DiscipleData data) {
		ListTag list = MandiriApi.bagian(data).getListOrEmpty(MandiriIds.K_BELANJA);
		int sum = 0;
		for (int i = 0; i < list.size(); i++) {
			int prio = Math.max(1, Math.min(3, list.getCompoundOrEmpty(i).getIntOr("prio", 3)));
			sum += (4 - prio) * 10;
		}
		return clamp(sum);
	}

	/** Missing health in percent; a severe wound or captivity pins it at 100. */
	public static int luka(DiscipleEntity entity) {
		JenisLuka jenis = MandiriApi.luka(entity.data(), entity.level().getGameTime());
		if (jenis == JenisLuka.BERAT || jenis == JenisLuka.TERTAWAN) {
			return 100;
		}
		float max = entity.getMaxHealth();
		return max <= 0.0F ? 0 : clamp((int) Math.round((1.0D - entity.getHealth() / max) * 100.0D));
	}

	static int clamp(int value) {
		return Math.max(0, Math.min(100, value));
	}

	private Dorongan() {
	}
}
