package dev.kultivator.sekte.mandiri;

import dev.kultivator.sekte.SekteConfig;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.mandiri.api.ModeMandiri;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Section {@code mandiri} of {@code config/kultivator-sekte.json} (DESIGN 39.10.4). The module switch is
 * {@code umum.mandiri} ({@code SekteContent}); {@link #aktif} only gates behaviour, never registry content (K1). Reloaded
 * in place by {@code /kultivasi uji perguruan muat}. The feud module 39F keeps its own section {@code mandiri_seteru}
 * ({@code SeteruConfig}).
 * <p>
 * Approved by the user (schedule 0.1): {@link #skalaXp} default <b>1.5</b>; {@link #bolehGugur} default <b>false</b> (no
 * disciple dies through DESIGN 39: NEKAT can end wounded or captured, never {@code markFallen}).
 */
public final class MandiriConfig {

	/** The module lives (registries always register). */
	public boolean aktif = true;
	/** The hall policy of a new hall: {@code penuh}, {@code seimbang}, {@code patuh}. */
	public String modeBawaan = ModeMandiri.SEIMBANG.id();
	/** XP of self-chosen activities and catch-up only (never ordered meditation or Master Guidance); approved 1.5. */
	public double skalaXp = 1.5D;
	/** Catch-up scale (no full daily rhythm while unloaded). */
	public double skalaLuarLayar = 0.75D;
	/** Most days caught up at once. */
	public int kejarMaksHari = 30;
	/** Planner evaluations per server tick, all halls together. */
	public int penilaianPerTick = 4;
	/** Disciples navigating at once per hall. */
	public int gerakMaks = 10;
	/** Pause after an explicit order of the master (ticks; 24000 = one world day). */
	public int jedaPerintah = 24_000;
	/** Days a ready disciple waits for the master before breaking through alone (Seimbang). */
	public int tungguGuruHari = 2;
	/** Days a Golden Core without Dao waits for the master's choice (Seimbang). */
	public int tungguDaoHari = 3;
	/** Pocket money NB per day by sect rank: registered, outer, inner, core, elder, grand elder. */
	public int[] uangSaku = {2, 4, 8, 16, 30, 40};
	/** Purse limit = this x (realm + 1). */
	public int dompetMaksPerRanah = 200;
	/** Earnings per disciple per day = this x (band + 1); the rest goes to the hall pool. */
	public int hasilMaksHariPerBand = 20;
	/** Units of one kind a town sells to disciples (all halls) per day. */
	public int stokMuridHari = 4;
	/** Loot stacks per hall per 7 days. */
	public int rampasanMinggu = 12;
	/** Share of the hall away at once: Seimbang, Penuh (percent). */
	public int[] pergiMaksPersen = {25, 40};
	/** Normal death for every cause, as before DESIGN 39 (the die guard checks only this flag; needs the user's approval to turn on). */
	public boolean bolehGugur = false;
	/** The temptation layer (39.3.3). */
	public boolean jalanIblis = true;
	/** Town bodies walking on roads near players. */
	public boolean tampilJalan = true;
	/** Town bodies ({@code Role.JELMAAN}) per town / per server. */
	public int jelmaanPerKota = 3;
	public int jelmaanServer = 6;
	/**
	 * Overrides of the planner weights (39.4.3), {@code "<kegiatan>.<dorong>"} (drive weight) or {@code "<jalan>.<kegiatan>"}
	 * (path weight) to a number 0-5; missing keys keep the design table.
	 */
	public Map<String, Double> bobot = new LinkedHashMap<>();
	/** Paid services without 38 (C39.K7, numbers of 38.M.2.2). */
	public Harga harga = new Harga();

	/** C39.K7 service prices in NB. */
	public static final class Harga {
		public int penginapan = 4;
		public int penginapanPos = 2;
		public int[] hidangan = {2, 6, 12, 25};
		public int dupa = 3;
		public int dupaJalan = 1;
		/** Teleport station: ceil(teleportDasar + d / teleportPer), at most teleportMaks. */
		public int teleportDasar = 2;
		public int teleportPer = 250;
		public int teleportMaks = 120;
		/** Cloud travel: ceil(awanDasar + d / awanPer). */
		public int awanDasar = 6;
		public int awanPer = 150;
		/** Healer: tabibPerBand x (band + 1). */
		public int tabibPerBand = 15;
		/** Ransom of a captive: tebusanPerBand x (band + 1). */
		public int tebusanPerBand = 30;
	}

	private static MandiriConfig instance = new MandiriConfig();

	/** Reads (or re-reads) the section; called from {@code Mandiri.init}. */
	static MandiriConfig load() {
		instance = SekteConfig.section(MandiriIds.MODUL, MandiriConfig.class, MandiriConfig::new, MandiriConfig::clamp);
		return instance;
	}

	public static MandiriConfig get() {
		return instance;
	}

	/** The new-hall mode parsed from {@link #modeBawaan}. */
	public ModeMandiri modeAwal() {
		return ModeMandiri.byId(modeBawaan, ModeMandiri.SEIMBANG);
	}

	/** Pocket money of sect rank index {@code rank} (0 registered ... 5 grand elder). */
	public int uangSakuJenjang(int rank) {
		return uangSaku[Math.max(0, Math.min(uangSaku.length - 1, rank))];
	}

	/** Most of the hall away at once for {@code mode} (percent). */
	public int pergiMaks(ModeMandiri mode) {
		return switch (mode) {
			case PENUH -> pergiMaksPersen[1];
			case SEIMBANG -> pergiMaksPersen[0];
			case PATUH -> 0;
		};
	}

	/** A weight override, or {@code bawaan}. */
	public double bobot(String key, double bawaan) {
		Double value = bobot == null ? null : bobot.get(key);
		return value == null ? bawaan : Math.max(0.0D, Math.min(5.0D, value));
	}

	private static MandiriConfig clamp(MandiriConfig c) {
		c.modeBawaan = c.modeAwal().id();
		c.skalaXp = clamp(c.skalaXp, 0.0D, 3.0D);
		c.skalaLuarLayar = clamp(c.skalaLuarLayar, 0.0D, 1.0D);
		c.kejarMaksHari = clamp(c.kejarMaksHari, 1, 120);
		c.penilaianPerTick = clamp(c.penilaianPerTick, 1, 16);
		c.gerakMaks = clamp(c.gerakMaks, 2, 40);
		c.jedaPerintah = Math.max(1200, c.jedaPerintah);
		c.tungguGuruHari = clamp(c.tungguGuruHari, 0, 30);
		c.tungguDaoHari = clamp(c.tungguDaoHari, 0, 30);
		c.uangSaku = sized(c.uangSaku, new int[]{2, 4, 8, 16, 30, 40}, 0, 500);
		c.dompetMaksPerRanah = clamp(c.dompetMaksPerRanah, 0, 5000);
		c.hasilMaksHariPerBand = clamp(c.hasilMaksHariPerBand, 0, 500);
		c.stokMuridHari = clamp(c.stokMuridHari, 0, 64);
		c.rampasanMinggu = clamp(c.rampasanMinggu, 0, 200);
		c.pergiMaksPersen = sized(c.pergiMaksPersen, new int[]{25, 40}, 0, 100);
		c.jelmaanPerKota = clamp(c.jelmaanPerKota, 0, 12);
		c.jelmaanServer = clamp(c.jelmaanServer, 0, 32);
		if (c.bobot == null) {
			c.bobot = new LinkedHashMap<>();
		}
		if (c.harga == null) {
			c.harga = new Harga();
		}
		Harga h = c.harga;
		h.penginapan = clamp(h.penginapan, 0, 500);
		h.penginapanPos = clamp(h.penginapanPos, 0, 500);
		h.hidangan = sized(h.hidangan, new int[]{2, 6, 12, 25}, 0, 500);
		h.dupa = clamp(h.dupa, 0, 100);
		h.dupaJalan = clamp(h.dupaJalan, 0, 100);
		h.teleportDasar = clamp(h.teleportDasar, 0, 100);
		h.teleportPer = clamp(h.teleportPer, 1, 10_000);
		h.teleportMaks = clamp(h.teleportMaks, 0, 10_000);
		h.awanDasar = clamp(h.awanDasar, 0, 100);
		h.awanPer = clamp(h.awanPer, 1, 10_000);
		h.tabibPerBand = clamp(h.tabibPerBand, 0, 1000);
		h.tebusanPerBand = clamp(h.tebusanPerBand, 0, 1000);
		return c;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
	}

	/** Keeps an array at the default length, each value clamped; a missing or short array falls back per index. */
	private static int[] sized(int[] value, int[] bawaan, int min, int max) {
		int[] out = bawaan.clone();
		if (value != null) {
			for (int i = 0; i < out.length && i < value.length; i++) {
				out[i] = clamp(value[i], min, max);
			}
		}
		return out;
	}
}
