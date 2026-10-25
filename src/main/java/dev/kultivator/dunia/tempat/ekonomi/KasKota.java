package dev.kultivator.dunia.tempat.ekonomi;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kas Kota (DESIGN 38.M.5, 42.6.0): the treasury of one town or place, filled by sinks (fines, the Grand Auction commission,
 * stall tax, bills paid, entry fees) and since 42 by the simulated town tax ({@link #pajak}: business tax, property tax,
 * player shop tax, rents); it pays defence and festival prizes, office wages, granary purchases and plot material. It
 * never mints and is never negative: {@link #ambil} pays at most what is in it, {@link #bayar} pays all or nothing.
 * Kept per site in {@link EkonomiData}; 42 money movements are booked per world week by source ({@link #buku}).
 */
public final class KasKota {

	private static final String KAS = "kas_kota";
	private static final String MASUK = "kas_masuk";
	private static final String KELUAR = "kas_keluar";
	/** Weekly ledger: {@code kas_buku -> "w<week>" -> {masuk: {sumber: nb}, keluar: {tujuan: nb}}}, two weeks kept. */
	private static final String BUKU = "kas_buku";

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

	/**
	 * A town tax or rent of 42 ({@code sumber}: "usaha", "properti", "toko", "bea", "lapak", "gudang", "denda"...): added
	 * like {@link #tambah} and booked in this week's ledger.
	 */
	public static void pajak(MinecraftServer server, long situsId, long nb, String sumber) {
		if (server == null || nb <= 0L) {
			return;
		}
		tambah(server, situsId, nb);
		catat(server, situsId, "masuk", sumber, nb);
	}

	/**
	 * Pays {@code nb} for {@code tujuan} ("gaji", "festival", "lumbung", "kavling", "pesanan"...) only when the treasury
	 * holds all of it (42.6.0: a payment above the balance is refused, never partly made); booked in this week's ledger.
	 */
	public static boolean bayar(MinecraftServer server, long situsId, long nb, String tujuan) {
		if (server == null || nb <= 0L) {
			return nb == 0L;
		}
		if (saldo(server, situsId) < nb) {
			return false;
		}
		ambil(server, situsId, nb);
		catat(server, situsId, "keluar", tujuan, nb);
		return true;
	}

	/** The ledger of world week {@code minggu} ({@code keluar} false = income by source), insertion order. */
	public static Map<String, Long> buku(MinecraftServer server, long situsId, long minggu, boolean keluar) {
		Map<String, Long> out = new LinkedHashMap<>();
		CompoundTag week = EkonomiData.get(server).situs(situsId).getCompoundOrEmpty(BUKU).getCompoundOrEmpty("w" + minggu)
				.getCompoundOrEmpty(keluar ? "keluar" : "masuk");
		for (String key : week.keySet()) {
			out.put(key, week.getLongOr(key, 0L));
		}
		return out;
	}

	private static void catat(MinecraftServer server, long situsId, String arah, String sumber, long nb) {
		EkonomiData data = EkonomiData.get(server);
		CompoundTag site = data.situs(situsId);
		CompoundTag buku = site.getCompoundOrEmpty(BUKU);
		long minggu = Math.floorDiv(Barang.hari(server), 7L);
		String key = "w" + minggu;
		for (String old : java.util.List.copyOf(buku.keySet())) {
			if (!old.equals(key) && !old.equals("w" + (minggu - 1L))) {
				buku.remove(old);
			}
		}
		CompoundTag week = buku.getCompoundOrEmpty(key);
		CompoundTag side = week.getCompoundOrEmpty(arah);
		String name = sumber == null || sumber.isBlank() ? "lain" : sumber;
		side.putLong(name, side.getLongOr(name, 0L) + nb);
		week.put(arah, side);
		buku.put(key, week);
		site.put(BUKU, buku);
		data.touch();
	}

	/** Lifetime in and out of the treasury ({@code uji ekonomi kas}). */
	public static long[] riwayat(MinecraftServer server, long situsId) {
		CompoundTag site = EkonomiData.get(server).situs(situsId);
		return new long[] {site.getLongOr(MASUK, 0L), site.getLongOr(KELUAR, 0L)};
	}

	private KasKota() {
	}
}
