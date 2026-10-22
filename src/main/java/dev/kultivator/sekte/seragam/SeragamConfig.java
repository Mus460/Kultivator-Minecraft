package dev.kultivator.sekte.seragam;

import dev.kultivator.sekte.SekteConfig;
import dev.kultivator.sekte.SekteIds;

/**
 * Section {@code seragam} of {@code config/kultivator-sekte.json} (DESIGN 36.2.8). Every value is clamped on load and on
 * {@code /kultivasi uji perguruan muat}.
 */
public final class SeragamConfig {

	/** Design changes per world day that cost nothing (0-10). */
	public int ubahGratisPerHari = 1;
	/** Poin Kontribusi taken from the hall pool for each further change that day (0-10000). */
	public int ubahPK = 100;
	/** Banner cloths drawn per hall (2-16; the gate pair, the formation four, then the arena ones). */
	public int benderaMaks = 12;
	/** Blocks within which a cloth waves; up to twice this it hangs still, beyond that it is not drawn (16-96). */
	public int jarakAnimasi = 48;
	/** Blocks within which a hall's emblem and banners are sent to a player (64-256). */
	public int jarakSinkron = 160;
	/** The hall's uniform colours, cuts and emblem embroidery on its disciples' robes. */
	public boolean jubah = true;
	/** The emblem decal on the Titik Formasi plots and the Bagua disc. */
	public boolean lambangFormasi = true;
	/** Standards with the emblem of module D's NPC sects at their mountain gates (needs module D). */
	public boolean panjiSekteNpc = true;

	private static SeragamConfig config = new SeragamConfig();

	/** Reads the section (from {@code Seragam.init()}). */
	static void load() {
		config = SekteConfig.section(SekteIds.S, SeragamConfig.class, SeragamConfig::new, SeragamConfig::clamp);
	}

	public static SeragamConfig get() {
		return config;
	}

	private static SeragamConfig clamp(SeragamConfig c) {
		c.ubahGratisPerHari = Math.max(0, Math.min(10, c.ubahGratisPerHari));
		c.ubahPK = Math.max(0, Math.min(10000, c.ubahPK));
		c.benderaMaks = Math.max(2, Math.min(16, c.benderaMaks));
		c.jarakAnimasi = Math.max(16, Math.min(96, c.jarakAnimasi));
		c.jarakSinkron = Math.max(64, Math.min(256, c.jarakSinkron));
		return c;
	}
}
