package dev.kultivator.sekte.kebun;

import dev.kultivator.sekte.SekteConfig;
import dev.kultivator.sekte.SekteIds;

/**
 * Module W's section {@code kebun} of {@code config/kultivator-sekte.json} (DESIGN 36.2.10). The five numbers of the
 * design keep their names; the rest are W's own switches. Reloaded in place by {@code /kultivasi uji perguruan muat}.
 */
public final class KebunConfig {

	/** At most this many spirit-herb beds count, whatever the template builds (DESIGN: 12). */
	public int petakMaks = 12;
	/** The base yield of one bed before the hall bonus ({@code hasilPetak + hall level / 3}). */
	public int hasilPetak = 2;
	/**
	 * PK from the hall pool for a mine level: {@code tambangPKDasar × level^1.3}, rounded (S28, approved balance change:
	 * 246, 417, 606, 810, 1027, 1255, 1493, 1740 PK for levels 2-9, 7,594 in all instead of 8,800). The old linear field
	 * {@code tambangPK} of older config files is ignored.
	 */
	public int tambangPKDasar = 100;
	/** The chance (percent) of a cave-in per shift from depth level 5 on; a miner is hurt, never killed. */
	public int runtuhPersen = 2;
	/** Consecutive work days before a rest is owed; each day beyond it counts as forced work. */
	public int hariLibur = 5;

	/** The garden (beds, gardeners, harvests). */
	public boolean kebunAktif = true;
	/** The mine (shifts, depth levels, cave-ins). */
	public boolean tambangAktif = true;
	/** Forced work ({@code DIPAKSA_KERJA}) is reported to the bus (module P turns it into lost contentment). */
	public boolean laporDipaksa = true;
	/** Seconds between two lazy computations of a hall (the {@code Duties} rhythm: 60). */
	public int hitungDetik = 60;
	/** Seconds between two world syncs of the beds and the mine to nearby players. */
	public int sinkronDetik = 10;
	/** The chance (percent) that a harvest of a spirit herb blooms rare (an extra herb three times as old). */
	public int mekarPersen = 3;
	/** Rare harvest and mountain-core scenes (beats 190-191; the camera rules of 7b still apply). */
	public boolean adegan = true;

	private static KebunConfig instance = new KebunConfig();

	static KebunConfig load() {
		instance = SekteConfig.section(SekteIds.W, KebunConfig.class, KebunConfig::new, KebunConfig::clamp);
		return instance;
	}

	public static KebunConfig get() {
		return instance;
	}

	private static KebunConfig clamp(KebunConfig c) {
		c.petakMaks = Math.max(0, Math.min(12, c.petakMaks));
		c.hasilPetak = Math.max(1, Math.min(16, c.hasilPetak));
		c.tambangPKDasar = Math.max(0, Math.min(10_000, c.tambangPKDasar));
		c.runtuhPersen = Math.max(0, Math.min(50, c.runtuhPersen));
		c.hariLibur = Math.max(1, Math.min(30, c.hariLibur));
		c.hitungDetik = Math.max(10, Math.min(600, c.hitungDetik));
		c.sinkronDetik = Math.max(2, Math.min(120, c.sinkronDetik));
		c.mekarPersen = Math.max(0, Math.min(50, c.mekarPersen));
		return c;
	}
}
