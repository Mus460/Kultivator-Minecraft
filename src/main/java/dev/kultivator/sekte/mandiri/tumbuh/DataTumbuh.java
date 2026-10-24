package dev.kultivator.sekte.mandiri.tumbuh;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.mandiri.api.Dorong;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriBus;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.paviliun.PaviliunMandiri;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Where module 39B keeps its state. The keys the 39A schema already reserves for 39B ({@code extra.mandiri.tutup},
 * {@code tunggu}, {@code statistik}; C39.A4) are written through {@link MandiriApi#bagianTulis} with the
 * {@link MandiriIds} names, never renamed; everything else of 39B lives in its own section {@code extra.mandiri_tumbuh}
 * (outside the 39A schema, shared with the bridge {@code PaviliunMandiri}). Both travel with the disciple (Soul Tablet,
 * away store) like the rest of {@code DiscipleData.extra()}.
 */
final class DataTumbuh {

	/** {@code extra.mandiri_tumbuh}. */
	static final String SECTION = PaviliunMandiri.SECTION;

	/** {@code tunggu} sub-keys (world days). */
	static final String T_TEROBOSAN = "terobosan";
	static final String T_DAO = "dao";
	static final String T_JENJANG = "jenjang";
	static final String T_DEWA = "dewa";

	/** {@code statistik} counters written by 39B. */
	static final String S_TEROBOSAN = "terobosanSendiri";
	static final String S_TEKNIK = "tekSendiri";
	static final String S_TUTUP = "hariTutup";
	static final String S_BATU = "batuDiserap";
	static final String S_AJAR = "muridDiajar";

	/** Own state for reading (detached empty tag when missing; never write into it). */
	static CompoundTag baca(DiscipleData data) {
		return data.extra().getCompoundOrEmpty(SECTION);
	}

	/** A live sub-compound of the own section (created when missing). */
	static CompoundTag sub(DiscipleData data, String key) {
		CompoundTag section = data.extraSection(SECTION);
		CompoundTag sub = section.getCompoundOrEmpty(key);
		if (!section.contains(key)) {
			section.put(key, sub);
		}
		return sub;
	}

	/** The live {@code extra.mandiri.tunggu} (decision queue, world days). */
	static CompoundTag tunggu(DiscipleData data) {
		CompoundTag mandiri = MandiriApi.bagianTulis(data);
		CompoundTag tunggu = mandiri.getCompoundOrEmpty(MandiriIds.K_TUNGGU);
		if (!mandiri.contains(MandiriIds.K_TUNGGU)) {
			mandiri.put(MandiriIds.K_TUNGGU, tunggu);
		}
		return tunggu;
	}

	/** {@code tunggu.<key>} or -1 when not waiting. */
	static long tungguHari(DiscipleData data, String key) {
		return MandiriApi.bagian(data).getCompoundOrEmpty(MandiriIds.K_TUNGGU).getLongOr(key, -1L);
	}

	static void setTunggu(DiscipleData data, String key, long hari) {
		if (hari < 0L) {
			if (MandiriApi.bagian(data).getCompoundOrEmpty(MandiriIds.K_TUNGGU).contains(key)) {
				tunggu(data).remove(key);
			}
		} else {
			tunggu(data).putLong(key, hari);
		}
	}

	/** Adds {@code delta} to {@code extra.mandiri.statistik.<key>}. */
	static void tambahStatistik(DiscipleData data, String key, int delta) {
		CompoundTag mandiri = MandiriApi.bagianTulis(data);
		CompoundTag stat = mandiri.getCompoundOrEmpty(MandiriIds.K_STATISTIK);
		if (!mandiri.contains(MandiriIds.K_STATISTIK)) {
			mandiri.put(MandiriIds.K_STATISTIK, stat);
		}
		stat.putInt(key, Math.max(0, stat.getIntOr(key, 0) + delta));
	}

	static int statistik(DiscipleData data, String key) {
		return MandiriApi.bagian(data).getCompoundOrEmpty(MandiriIds.K_STATISTIK).getIntOr(key, 0);
	}

	/** A drive 0-100 (39.4.1; 50 when the planner has not written it yet). */
	static int dorong(DiscipleData data, Dorong dorong) {
		return Math.max(0, Math.min(100, MandiriApi.bagian(data).getCompoundOrEmpty(MandiriIds.K_DORONG).getIntOr(dorong.id(), 50)));
	}

	/**
	 * Changes the temptation by {@code delta} (39.3.3: the lost Heart Trial +15), clamped 0-100 and kept at 59 or below
	 * while the Soul Seal or the heir rule holds (39A's {@code JalanIblis} owns the daily table; this is a one-off event),
	 * and fires {@code MandiriBus.IBLIS}.
	 */
	static void ubahIblis(@Nullable MinecraftServer server, DiscipleEntity entity, int delta) {
		if (delta == 0 || !dev.kultivator.sekte.mandiri.MandiriConfig.get().jalanIblis) {
			return;
		}
		DiscipleData data = entity.data();
		int before = MandiriApi.iblis(data);
		int after = Math.max(0, Math.min(100, before + delta));
		if (after != before) {
			MandiriApi.bagianTulis(data).putInt(MandiriIds.K_IBLIS, after);
			MandiriBus.iblis(server, entity.ownerId(), entity.soulId(), before, after);
		}
	}

	/** World day of the hall's clock. */
	static long hari(@Nullable MinecraftServer server) {
		return Ingatan.today(server);
	}

	/** World hour 0-23 ({@code (dayTime / 1000 + 6) mod 24}, the 38.N / 39.4.4 formula). */
	static int jam(@Nullable MinecraftServer server) {
		long clock = server == null ? 0L : server.overworld().getOverworldClockTime();
		return (int) Math.floorMod(clock / 1000L + 6L, 24L);
	}

	/** True during the world's night (19:00-05:00 by the hour above). */
	static boolean malam(Level level) {
		long day = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return day >= 13000L && day < 23000L;
	}

	private DataTumbuh() {
	}
}
