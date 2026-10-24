package dev.kultivator.sosok;

import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.kepribadian.Hati;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The look of a disciple that changes by itself (DESIGN 40.2.2, 40.4.2), checked once a second per disciple from
 * {@code DiscipleEntity.tick} (staggered by entity id, a few comparisons each):
 * <ul>
 * <li><b>Visible age</b> (once a minute): a child looks young after {@code hariAnakKeMuda} world days (48); a youth looks
 * adult after {@code hariMudaKeDewasa} days (160) only while it is at most Qi Refining: from Foundation up the face
 * freezes, the mark of a cultivator. Growing out of childhood is not ageing, so a child grows up at any realm. Old age
 * only comes through {@link SosokApi#setUsia} (DESIGN 39 may model real age). Days are world days
 * ({@code getOverworldClockTime() / 24000}, the clock {@code /time add} moves), counted from {@code extra.sosok.lahir} (the
 * day it joined) and {@code extra.sosok.muda} (the day it became young); a failed breakthrough never ages.</li>
 * <li><b>Wound</b>: health under 35 % gives {@link Gaya#LUKA_PINCANG} (limp, bandaged leg), a qi deviation
 * {@link Gaya#LUKA_HABIS_QI} (pained face, dropped shoulders); healed, it clears.</li>
 * <li><b>Mourning</b>: while in Sekte yang Hidup's mourning activity it wears white ({@link Gaya#BUSANA_BERKABUNG}).</li>
 * <li><b>Shown traits</b>: the first two personality traits ({@code Hati.traits}) for the face and stance tables.</li>
 * </ul>
 * Wound and outfit are only taken back when this class set them, so a value DESIGN 39 set through {@link SosokApi}
 * (a captive's wound, a travelling outfit) is never overwritten.
 */
public final class SosokUsia {

	/** {@link #periksa} runs this often per disciple (ticks). */
	public static final int INTERVAL = 20;
	/** The age check runs every this many {@link #periksa} calls (once a minute). */
	private static final int USIA_SETIAP = 60;
	/** Health share under which a disciple limps. */
	public static final float LUKA_BATAS = 0.35F;
	/** {@code DiscipleData.extraSection} of this module. */
	public static final String BAGIAN = "sosok";

	/** What this class set last on a body: [wound, outfit] (-1 = nothing of ours). */
	private static final Map<DiscipleEntity, int[]> OTOMATIS = new WeakHashMap<>();

	/** Notes the world day a disciple joined (recruit, accepted candidate), once. */
	public static void catatLahir(DiscipleEntity murid) {
		if (!(murid.level() instanceof ServerLevel level)) {
			return;
		}
		CompoundTag bagian = murid.data().extraSection(BAGIAN);
		long hari = hariDunia(level);
		if (!bagian.contains("lahir")) {
			bagian.putLong("lahir", hari);
		}
		if (!bagian.contains("muda") && SosokApi.usia(murid) >= Sosok.MUDA) {
			bagian.putLong("muda", hari);
		}
	}

	/** The once-a-second check (server, a master's disciple only). */
	public static void periksa(DiscipleEntity murid) {
		if (!(murid.level() instanceof ServerLevel level) || murid.role() != DiscipleEntity.Role.DISCIPLE || murid.isReleasing()) {
			return;
		}
		Gaya gaya = SosokApi.gaya(murid);
		Gaya baru = otomatis(murid, gaya);
		baru = sifat(murid, baru);
		if (!baru.equals(gaya)) {
			SosokApi.setGaya(murid, baru);
		}
		if (Math.floorMod(murid.tickCount / INTERVAL + murid.getId(), USIA_SETIAP) == 0) {
			harian(murid, level);
		}
	}

	/**
	 * The visible-age step (40.2.2): child to youth, youth to adult (at most Qi Refining). Returns true when the age group
	 * changed.
	 */
	public static boolean harian(DiscipleEntity murid, ServerLevel level) {
		Sosok sosok = SosokApi.sosok(murid);
		if (sosok.usia() >= Sosok.DEWASA) {
			return false;
		}
		SosokConfig.Settings s = SosokConfig.get();
		CompoundTag bagian = murid.data().extraSection(BAGIAN);
		long hari = hariDunia(level);
		if (!bagian.contains("lahir")) {
			bagian.putLong("lahir", hari);
			return false;
		}
		if (sosok.usia() == Sosok.ANAK) {
			if (hari - bagian.getLongOr("lahir", hari) >= s.hariAnakKeMuda) {
				bagian.putLong("muda", hari);
				SosokApi.setSosok(murid, sosok.withUsia(Sosok.MUDA));
				return true;
			}
			return false;
		}
		if (!bagian.contains("muda")) {
			bagian.putLong("muda", bagian.getLongOr("lahir", hari));
		}
		boolean beku = murid.syncedRealm() > Realm.QI_REFINING.ordinal();
		if (!beku && hari - bagian.getLongOr("muda", hari) >= s.hariMudaKeDewasa) {
			SosokApi.setSosok(murid, sosok.withUsia(Sosok.DEWASA));
			return true;
		}
		return false;
	}

	/** The world day of the overworld clock ({@code /time add} moves it). */
	public static long hariDunia(ServerLevel level) {
		return Math.floorDiv(level.getServer().overworld().getOverworldClockTime(), 24000L);
	}

	/** Days since the disciple joined (0 when unknown); for the test command and 39. */
	public static long umurHari(DiscipleEntity murid) {
		if (!(murid.level() instanceof ServerLevel level)) {
			return 0L;
		}
		CompoundTag bagian = murid.data().extraSection(BAGIAN);
		long hari = hariDunia(level);
		return Math.max(0L, hari - bagian.getLongOr("lahir", hari));
	}

	/** Moves the noted join day back by {@code hari} days (the test command's quick ageing). */
	public static void majukan(DiscipleEntity murid, int hari) {
		CompoundTag bagian = murid.data().extraSection(BAGIAN);
		if (bagian.contains("lahir")) {
			bagian.putLong("lahir", bagian.getLongOr("lahir", 0L) - hari);
		}
		if (bagian.contains("muda")) {
			bagian.putLong("muda", bagian.getLongOr("muda", 0L) - hari);
		}
	}

	// ================================================================== automatic wound, mourning

	private static Gaya otomatis(DiscipleEntity murid, Gaya gaya) {
		int[] kita = OTOMATIS.computeIfAbsent(murid, k -> new int[]{-1, -1});
		int luka = Gaya.LUKA_SEHAT;
		if ((murid.syncedFlags() & DiscipleEntity.FLAG_DEVIATING) != 0) {
			luka = Gaya.LUKA_HABIS_QI;
		} else if (murid.getHealth() < murid.getMaxHealth() * LUKA_BATAS) {
			luka = Gaya.LUKA_PINCANG;
		}
		Gaya out = gaya;
		if (gaya.luka() == Gaya.LUKA_SEHAT || gaya.luka() == kita[0]) {
			if (gaya.luka() != luka) {
				out = out.withLuka(luka);
			}
			kita[0] = luka == Gaya.LUKA_SEHAT ? -1 : luka;
		}
		int busana = murid.activity() == SekteIds.ACT_MOURN ? Gaya.BUSANA_BERKABUNG : Gaya.BUSANA_OTOMATIS;
		if (gaya.busanaKhusus() == Gaya.BUSANA_OTOMATIS || gaya.busanaKhusus() == kita[1]) {
			if (gaya.busanaKhusus() != busana) {
				out = out.withBusanaKhusus(busana);
			}
			kita[1] = busana == Gaya.BUSANA_OTOMATIS ? -1 : busana;
		}
		return out;
	}

	private static Gaya sifat(DiscipleEntity murid, Gaya gaya) {
		List<Sifat> traits = Hati.traits(murid.data());
		Sifat pertama = traits.isEmpty() ? null : traits.get(0);
		Sifat kedua = traits.size() < 2 ? null : traits.get(1);
		if (gaya.sifatPertama() == pertama && gaya.sifatKedua() == kedua) {
			return gaya;
		}
		return gaya.withSifat(pertama, kedua);
	}

	/** Forgets the bookkeeping (server stop). */
	static void clear() {
		OTOMATIS.clear();
	}

	private SosokUsia() {
	}
}
