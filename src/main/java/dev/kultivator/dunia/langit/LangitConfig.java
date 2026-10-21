package dev.kultivator.dunia.langit;

import dev.kultivator.dunia.DuniaConfig;

/**
 * Section {@code langit} of {@code config/kultivator-dunia.json} (DESIGN 34.5.8-34.5.10): every switch and number of
 * module L, read once through {@link DuniaConfig#section} and clamped on load (out-of-range values are fixed and the file
 * is rewritten). {@code /kultivasi uji jelajah muat} copies new values into this very object.
 * <p>
 * Switches: {@link #events} (all random sky events), {@link #treasureBirth}, {@link #weather}; each sky event kind on its
 * own. Off means the system neither schedules nor applies anything; the test commands still preview it (op).
 */
public final class LangitConfig {

	// ---- 34.5.8 Peristiwa Langit Acak
	public boolean events = true;
	public double chancePerNight = 0.25D;
	public int minNightsBetween = 3;
	public boolean meteorShower = true;
	public boolean bloodEclipse = true;
	public boolean twinMoons = true;
	public int meteorsPerPlayer = 6;
	public double eclipseMobBuff = 0.25D;

	// ---- 34.5.9 Pertanda Harta Lahir
	public boolean treasureBirth = true;
	public int treasureDays = 3;
	public int distanceMin = 300;
	public int distanceMax = 900;
	public int durationTicks = 12000;
	public double guardianChance = 0.4D;

	// ---- 34.5.10 Cuaca Roh
	public boolean weather = true;
	public int cellSize = 512;
	public double thinFactor = 1.2D;
	public double denseFactor = 1.5D;
	public double dawnFactor = 1.6D;
	public int dewPerDay = 4;

	private static LangitConfig instance;

	/** The live section (loaded on first use). */
	public static LangitConfig get() {
		LangitConfig current = instance;
		if (current == null) {
			current = DuniaConfig.section("langit", LangitConfig.class, LangitConfig::new, LangitConfig::clamp);
			instance = current;
		}
		return current;
	}

	/** Keeps every number in its documented range (DESIGN 34.5.8-34.5.10). */
	static LangitConfig clamp(LangitConfig c) {
		c.chancePerNight = clampD(c.chancePerNight, 0.0D, 1.0D, 0.25D);
		c.minNightsBetween = Math.max(1, Math.min(14, c.minNightsBetween));
		c.meteorsPerPlayer = Math.max(0, Math.min(12, c.meteorsPerPlayer));
		c.eclipseMobBuff = clampD(c.eclipseMobBuff, 0.0D, 0.5D, 0.25D);
		c.treasureDays = Math.max(1, Math.min(14, c.treasureDays));
		c.distanceMin = Math.max(64, Math.min(2000, c.distanceMin));
		c.distanceMax = Math.max(c.distanceMin + 50, Math.min(3000, c.distanceMax));
		c.durationTicks = Math.max(1200, Math.min(72000, c.durationTicks));
		c.guardianChance = clampD(c.guardianChance, 0.0D, 1.0D, 0.4D);
		c.cellSize = Math.max(256, Math.min(2048, c.cellSize));
		c.thinFactor = clampD(c.thinFactor, 1.0D, 2.0D, 1.2D);
		c.denseFactor = clampD(c.denseFactor, 1.0D, 2.5D, 1.5D);
		c.dawnFactor = clampD(c.dawnFactor, 1.0D, 2.5D, 1.6D);
		c.dewPerDay = Math.max(0, Math.min(16, c.dewPerDay));
		return c;
	}

	private static double clampD(double value, double min, double max, double fallback) {
		if (!Double.isFinite(value)) {
			return fallback;
		}
		return Math.max(min, Math.min(max, value));
	}
}
