package dev.kultivator.dunia.tempat.ekonomi;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/**
 * Kas Kota (DESIGN 38.M.5): the treasury of one town or place, filled only by sinks (fines, the Grand Auction commission,
 * stall tax, bills paid, entry fees) and spent only on defence and festival prizes. It never mints: {@link #ambil} pays at
 * most what is in it. Kept per site in {@link EkonomiData}.
 */
public final class KasKota {

	private static final String KAS = "kas_kota";
	private static final String MASUK = "kas_masuk";
	private static final String KELUAR = "kas_keluar";

	/** Adds {@code nb} (a sink) to the treasury of {@code situsId}. */
	public static void tambah(MinecraftServer server, long situsId, long nb) {
		if (server == null || nb <= 0L) {
			return;
		}
		EkonomiData data = EkonomiData.get(server);
		CompoundTag site = data.situs(situsId);
		site.putLong(KAS, Math.max(0L, site.getLongOr(KAS, 0L)) + nb);
		site.putLong(MASUK, site.getLongOr(MASUK, 0L) + nb);
		data.touch();
	}

	/** The treasury of {@code situsId}. */
	public static long saldo(MinecraftServer server, long situsId) {
		return Math.max(0L, EkonomiData.get(server).situs(situsId).getLongOr(KAS, 0L));
	}

	/**
	 * Takes up to {@code nb} for a defence or festival prize; returns what was really taken (never more than the treasury
	 * holds, so a prize is smaller when the town is poor).
	 */
	public static long ambil(MinecraftServer server, long situsId, long nb) {
		if (server == null || nb <= 0L) {
			return 0L;
		}
		EkonomiData data = EkonomiData.get(server);
		CompoundTag site = data.situs(situsId);
		long have = Math.max(0L, site.getLongOr(KAS, 0L));
		long take = Math.min(have, nb);
		site.putLong(KAS, have - take);
		site.putLong(KELUAR, site.getLongOr(KELUAR, 0L) + take);
		data.touch();
		return take;
	}

	/** Lifetime in and out of the treasury ({@code uji ekonomi kas}). */
	public static long[] riwayat(MinecraftServer server, long situsId) {
		CompoundTag site = EkonomiData.get(server).situs(situsId);
		return new long[] {site.getLongOr(MASUK, 0L), site.getLongOr(KELUAR, 0L)};
	}

	private KasKota() {
	}
}
