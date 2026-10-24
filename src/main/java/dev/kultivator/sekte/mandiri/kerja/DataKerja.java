package dev.kultivator.sekte.mandiri.kerja;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.talisman.Inscribe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * The own state of module 39D in {@code DiscipleData.extra().mandiri_kerja} (not inside 39A's schema {@code extra.mandiri};
 * the statistics of 39.2.1 go through {@code MandiriApi.tambahStatistik}). It travels with the soul tablet like every
 * other extra section.
 * <ul>
 *   <li>{@code jimatXp}, {@code jimatBuat}: the talisman skill of the disciple (the player's rule of {@code Inscribe}: +4 a
 *   success, +1 a failure, level {@code floor(sqrt(xp / 8))}, 0-10).</li>
 *   <li>{@code latih {hari, poin}}: kata practice points given today (a day cap like the no-hit cap of {@code Mastery}).</li>
 *   <li>{@code elo}, {@code menang}, {@code kalah}, {@code seri}, {@code lawan}: the unofficial sparring record of the
 *   training ground (39.4.2 TANDING "Elo kecil, tidak resmi"; never the tournament's {@code Elo}).</li>
 *   <li>{@code racik}: the ingredients a brewing block holds while it runs (given back when it is interrupted early).</li>
 *   <li>{@code jimatBahan}: the blank and the catalyst a talisman block holds.</li>
 *   <li>{@code natalHari}: the world day of the last natal attempt (one a day).</li>
 * </ul>
 */
final class DataKerja {

	static final String SECTION = "mandiri_kerja";
	/** The lang prefix of 39D. */
	static final String P = MandiriIds.LANG + "kerja.";

	static final String JIMAT_XP = "jimatXp";
	static final String JIMAT_BUAT = "jimatBuat";
	static final String LATIH = "latih";
	static final String ELO = "elo";
	static final String MENANG = "menang";
	static final String KALAH = "kalah";
	static final String SERI = "seri";
	static final String LAWAN = "lawan";
	static final String RACIK = "racik";
	static final String JIMAT_BAHAN = "jimatBahan";
	static final String NATAL_HARI = "natalHari";

	/** Start of the training-ground rating and its K factor (small: friendly bouts). */
	static final int ELO_AWAL = 1000;
	static final int ELO_K = 12;
	/** A world hour in ticks. */
	static final int JAM_TICK = 1000;

	/** Read only (a detached empty tag when missing; never write into it). */
	static CompoundTag baca(DiscipleData data) {
		return data == null ? new CompoundTag() : data.extra().getCompoundOrEmpty(SECTION);
	}

	/** The live section, created when missing. */
	static CompoundTag tulis(DiscipleData data) {
		return data.extraSection(SECTION);
	}

	/** The world day ({@code Ingatan.today}, the diary's day). */
	static long hari(@Nullable MinecraftServer server) {
		return Ingatan.today(server);
	}

	/** World hour 0-23 ({@code (dayTime / 1000 + 6) mod 24}, the 39.4.4 formula). */
	static int jam(@Nullable MinecraftServer server) {
		long clock = server == null ? 0L : server.overworld().getOverworldClockTime();
		return (int) Math.floorMod(clock / 1000L + 6L, 24L);
	}

	// ================================================================== skills

	/** The disciple's alchemy level 0-10: {@code min(10, brewing hours / 40)} (DESIGN 39.6.8). */
	static int levelAlkimia(DiscipleData data) {
		return (int) Math.min(10L, MandiriApi.statistik(data, MandiriIds.S_JAM_MERAMU) / 40L);
	}

	static int jimatXp(DiscipleData data) {
		return Math.max(0, baca(data).getIntOr(JIMAT_XP, 0));
	}

	/** The talisman skill level 0-10 with the player's own curve ({@code Inscribe.level}). */
	static int levelJimat(DiscipleData data) {
		return Inscribe.level(jimatXp(data));
	}

	static void tambahJimatXp(DiscipleData data, int xp, boolean jadi) {
		CompoundTag own = tulis(data);
		own.putInt(JIMAT_XP, Math.max(0, own.getIntOr(JIMAT_XP, 0) + xp));
		if (jadi) {
			own.putInt(JIMAT_BUAT, own.getIntOr(JIMAT_BUAT, 0) + 1);
		}
	}

	static int elo(DiscipleData data) {
		return baca(data).getIntOr(ELO, ELO_AWAL);
	}

	/** A child compound of {@code parent}, created when missing. */
	static CompoundTag anak(CompoundTag parent, String key) {
		CompoundTag child = parent.getCompound(key).orElse(null);
		if (child == null) {
			child = new CompoundTag();
			parent.put(key, child);
		}
		return child;
	}

	private DataKerja() {
	}
}
