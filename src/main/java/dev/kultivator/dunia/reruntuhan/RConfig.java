package dev.kultivator.dunia.reruntuhan;

/**
 * Section {@code reruntuhan} of {@code config/kultivator-dunia.json} (DESIGN 34.5.5-34.5.7, read through
 * {@code DuniaConfig.section}): public fields with their defaults, clamped on load and after {@code uji jelajah muat}.
 * Every big system has its switch: {@link #worldgen} (the built-in pack of the three structure families),
 * {@link #ruins}, {@link #prisons} and {@link #tower} (the runtime; the structures stay when off).
 */
public final class RConfig {

	// ------------------------------------------------------------------ world generation
	/** Registers the built-in pack {@code kultivator:dunia_reruntuhan} (overworld structure sets). Read at start only. */
	public boolean worldgen = true;

	// ------------------------------------------------------------------ Gua Leluhur (55)
	public boolean ruins = true;
	/** A wrong move costs this share of max health (never below 1 HP). */
	public double puzzleBacklash = 0.10D;
	/** A hint appears after this many seconds of a puzzle. */
	public int hintSeconds = 90;
	public boolean guardians = true;
	/** A puzzle (and its guardian) seals itself again this many in-game days after it was solved, once nobody is near. */
	public int ruinResetDays = 3;

	// ------------------------------------------------------------------ Penjara Iblis Kuno (62)
	public boolean prisons = true;
	public double sealDecayPerDay = 0.2D;
	public double eclipseDecayPerHour = 1.0D;
	/** A lethal blow of a demon of the prison leaves 1 HP with Luka Iblis. */
	public boolean bossDeathGuard = true;
	/** A prison breaks at most once per this many in-game days. */
	public int rebreakDays = 7;

	// ------------------------------------------------------------------ Menara Seratus Lantai (65)
	public boolean tower = true;
	public int towerSlots = 6;
	/** A lethal blow inside the tower leaves 1 HP and sends the party to the lobby. */
	public boolean towerDeathGuard = true;
	public double towerRewardScale = 1.0D;
	/** A run starts at the first floor of the highest band reached (no re-climbing cleared bands). */
	public boolean startAtBand = true;

	public RConfig() {
	}

	/** Keeps every number in its range (DESIGN 34.5.5-34.5.7). */
	public static RConfig clamp(RConfig c) {
		c.puzzleBacklash = clamp(c.puzzleBacklash, 0.0D, 0.25D, 0.10D);
		c.hintSeconds = Math.max(30, Math.min(300, c.hintSeconds));
		c.ruinResetDays = Math.max(1, Math.min(30, c.ruinResetDays));
		c.sealDecayPerDay = clamp(c.sealDecayPerDay, 0.0D, 10.0D, 0.2D);
		c.eclipseDecayPerHour = clamp(c.eclipseDecayPerHour, 0.0D, 10.0D, 1.0D);
		c.rebreakDays = Math.max(1, Math.min(60, c.rebreakDays));
		c.towerSlots = Math.max(2, Math.min(8, c.towerSlots));
		c.towerRewardScale = clamp(c.towerRewardScale, 0.1D, 5.0D, 1.0D);
		return c;
	}

	private static double clamp(double value, double min, double max, double fallback) {
		if (!Double.isFinite(value)) {
			return fallback;
		}
		return Math.max(min, Math.min(max, value));
	}
}
