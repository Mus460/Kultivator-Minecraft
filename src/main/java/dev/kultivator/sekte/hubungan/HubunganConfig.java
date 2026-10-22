package dev.kultivator.sekte.hubungan;

import dev.kultivator.sekte.SekteConfig;
import dev.kultivator.sekte.SekteIds;

/**
 * Section {@code hubungan} of {@code config/kultivator-sekte.json} (DESIGN 36.2.2): the switches and numbers of module R.
 * The design's six values come first; the rest only tune the moments (tea length, honour-duel chance, the bond effects).
 */
public final class HubunganConfig {

	/** Off: no tea turns, no events, no effects (the pair table stays saved). */
	public boolean aktif = true;
	/** Seconds between two social turns of one hall (one pair per turn). */
	public int giliranDetik = 20;
	/** Two idle disciples at most this many blocks apart may meet. */
	public int jarakTemu = 12;
	/** Love may bloom between close friends (Kekasih, then Pasangan Dao with the master's blessing). */
	public boolean cintaAktif = true;
	/** Chance per tea meeting that two close friends (affinity 70+) become lovers. */
	public double peluangCinta = 0.05D;
	/** Grudges (Dendam) may form after a heavy wrong. */
	public boolean dendamAktif = true;
	/** Seconds two disciples drink tea together once both reached their seats. */
	public int lamaTehDetik = 30;
	/** Chance per day that a grudge pair asks the master for a Duel Kehormatan. */
	public double peluangDuel = 0.10D;
	/** The fight and cultivation bonuses of ties (Sahabat, Saudara Angkat, Kekasih, Pasangan Dao, Saingan). */
	public boolean efekAktif = true;
	/** Sworn kin rush to defend a sibling that is struck. */
	public boolean saudaraMembela = true;

	private static HubunganConfig config = new HubunganConfig();

	static void load() {
		config = SekteConfig.section(SekteIds.R, HubunganConfig.class, HubunganConfig::new, HubunganConfig::clamp);
	}

	public static HubunganConfig get() {
		return config;
	}

	private static HubunganConfig clamp(HubunganConfig c) {
		c.giliranDetik = Math.max(5, Math.min(600, c.giliranDetik));
		c.jarakTemu = Math.max(4, Math.min(32, c.jarakTemu));
		c.peluangCinta = Math.max(0.0D, Math.min(1.0D, c.peluangCinta));
		c.lamaTehDetik = Math.max(10, Math.min(120, c.lamaTehDetik));
		c.peluangDuel = Math.max(0.0D, Math.min(1.0D, c.peluangDuel));
		return c;
	}
}
