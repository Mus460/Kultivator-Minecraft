package dev.kultivator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server settings in {@code config/kultivator.json}. Missing values get their defaults, out-of-range values are clamped
 * (DESIGN 20.2.11, 21.24) and the file is rewritten.
 */
public final class KultivatorConfig {

	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("kultivator.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static KultivatorConfig instance = new KultivatorConfig();

	/** Multiplies all cultivation XP. */
	public double xpMultiplier = 1.0D;
	/** Every player's failed breakthroughs drop one realm (otherwise players opt in with /cultivate hardcore on). */
	public boolean hardcoreForced = false;
	/** Monsters are drawn to tribulations (the Dao Protector event). */
	public boolean daoProtectorEvent = true;
	/** Multiplies tribulation strike damage (still never lethal). */
	public double tribulationDamageScale = 1.0D;
	/** Heart demon trials before some breakthroughs. */
	public boolean heartDemonTrials = true;
	/** Protective formations can be powered at all (each one still starts switched off). */
	public boolean protectiveFormations = true;
	/** Tell the whole server about breakthroughs from Golden Core up. */
	public boolean announceBreakthroughs = true;

	// ---- phase 6: the sect (DESIGN 20)
	/** Sect Hall steles found halls (DESIGN 20.3.1). */
	public boolean sectHall = true;
	/** Candidates arrive every this many in-game days (1-15). */
	public int recruitmentIntervalDays = 3;
	/** Sect events: tournaments, the forbidden ground, the Hundred Dynasties War. */
	public boolean sectEvents = true;
	/** Kompetisi Daftar Kuat every this many days (3-30). */
	public int tournamentIntervalDays = 7;
	/** The Blood-Colored Forbidden Ground opens every this many days (5-60). */
	public int forbiddenGroundIntervalDays = 15;

	// ---- phase 6: Mode Kehancuran (DESIGN 20.3.21), the ONLY block-changing feature; OFF by default
	/** Lock 1 of 3: the server allows destructive ultimates at all. Default false. */
	public boolean modeKehancuran = false;
	/** Largest radius of a destructive shape (4-24). */
	public int kehancuranMaxRadius = 12;
	/** Blocks removed per server tick over all jobs (8-256). */
	public int kehancuranBlocksPerTick = 48;
	/** Blocks one cast may remove at most (256-16384). */
	public int kehancuranMaxBlocksPerCast = 4096;
	/** Chance in percent that a removed block drops its resources (0-10). */
	public int kehancuranDropsPct = 0;
	/** Nothing is touched within this many horizontal blocks of any Sect Hall stele (48-256). */
	public int kehancuranHallSafeRadius = 64;
	/** Nothing is touched within this many blocks of the master or any player (16-128). */
	public int kehancuranPlayerSafeRadius = 24;
	/** A running job pauses while the average tick time is above this many milliseconds (30-50). */
	public int kehancuranMsptPause = 45;
	/** The dimension-wide warning lasts this many seconds before a destructive cast (10-60). */
	public int kehancuranWarningSeconds = 30;
	/** Removed blocks can be restored for this many minutes (0-30). */
	public int kehancuranUndoMinutes = 10;

	// ---- phase 6b: Pembangunan Sekte (DESIGN 21), the second exception: blocks only inside a confirmed footprint
	/** Sect templates can be chosen and built at all. */
	public boolean sectConstruction = true;
	/** Multiplies the placement rate (0.25-4). */
	public double sectBuildSpeed = 1.0D;
	/** Hard average cap of placements per tick over all sites (1-32); bursts every 4 ticks place up to 4x this. */
	public int sectBuildMaxPerTick = 8;
	/** Construction pauses while the average tick time is at or above this many milliseconds (30-50). */
	public int sectBuildMsptPause = 40;
	/** The owner (or a sect mate) must be within this many horizontal blocks of the stele for building to run (96-512). */
	public int sectBuildRange = 160;
	/** Build even with nobody near (still never force-loads chunks). Default false: away time is banked instead. */
	public boolean sectBuildWhileAway = false;
	/** At most this many away ticks are banked (0-72000); banked ticks double the rate until used up. */
	public int sectBuildBankTicks = 24000;
	/** The largest template that may be chosen (1-9). */
	public int sectBuildMaxTemplate = 9;
	/** Only show the realm hint on the plaque (false) or require that realm to choose a template (true). */
	public boolean sectTemplateRealmGate = false;
	/** Breaking a sect block refunds its placed cost to the Gudang instead of dropping it. */
	public boolean sectBuildBreakRefund = true;
	/** Exploded sect blocks drop nothing and are credited to the Gudang. */
	public boolean sectBuildExplosionCredit = true;
	/** Monsters never spawn naturally on sect cells. */
	public boolean sectBuildSpawnProof = true;
	/** Clients report a long low frame rate so their sect builds at half speed. */
	public boolean sectBuildFpsHint = true;

	// ---- phase 6c: Teknik Dewa (DESIGN 22.14.7, 22.18.1)
	/** A factor in the player's technique power P (0.1-10). */
	public double techniqueDamageMultiplier = 1.0D;
	/** The radius hard cap of technique shapes (8-48); Dewa specs × 1.25. */
	public int techniqueAreaCap = 32;
	/** The per-cast boss damage caps of DESIGN 22.14.2. */
	public boolean techniqueBossCaps = true;
	/** Lasting technique casts on the whole server (16-128); beyond that the oldest ends early. */
	public int techniqueMaxActiveCasts = 64;
	/** Clients may draw client-only phantom displays for techniques. */
	public boolean techniqueDisplays = true;
	/** The player's own techniques may use Mode Kehancuran at all (the one-click switch, DESIGN 22.11.1). */
	public boolean kehancuranPlayerTechniques = true;
	/** Nothing is carved within this many blocks of the casting player, at full height (3-8). */
	public int kehancuranCasterSafeRadius = 4;
	/** Nothing is carved within this many blocks of any player's respawn point (0-64). */
	public int kehancuranHomeSafeRadius = 24;

	public static KultivatorConfig get() {
		return instance;
	}

	public static void load() {
		boolean readable = true;
		try {
			if (Files.exists(FILE)) {
				KultivatorConfig loaded = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), KultivatorConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			}
		} catch (IOException | RuntimeException exception) {
			// A broken file is left as it is (the admin may want to fix it); the defaults run meanwhile.
			readable = false;
			Kultivator.LOGGER.warn("Could not read config/kultivator.json, using defaults", exception);
		}
		instance.clamp();
		if (readable) {
			save();
		}
	}

	/** Writes the current settings to {@code config/kultivator.json} (used by {@code /kultivasi kehancuran izin}). */
	public static boolean save() {
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(instance), StandardCharsets.UTF_8);
			return true;
		} catch (IOException | RuntimeException exception) {
			Kultivator.LOGGER.warn("Could not write config/kultivator.json", exception);
			return false;
		}
	}

	/** Keeps every phase-6, 6b and 6c number inside its allowed range (DESIGN 20.2.11, 21.24, 22.18.1). */
	private void clamp() {
		recruitmentIntervalDays = clamp(recruitmentIntervalDays, 1, 15);
		tournamentIntervalDays = clamp(tournamentIntervalDays, 3, 30);
		forbiddenGroundIntervalDays = clamp(forbiddenGroundIntervalDays, 5, 60);
		kehancuranMaxRadius = clamp(kehancuranMaxRadius, 4, 24);
		kehancuranBlocksPerTick = clamp(kehancuranBlocksPerTick, 8, 256);
		kehancuranMaxBlocksPerCast = clamp(kehancuranMaxBlocksPerCast, 256, 16384);
		kehancuranDropsPct = clamp(kehancuranDropsPct, 0, 10);
		kehancuranHallSafeRadius = clamp(kehancuranHallSafeRadius, 48, 256);
		kehancuranPlayerSafeRadius = clamp(kehancuranPlayerSafeRadius, 16, 128);
		kehancuranMsptPause = clamp(kehancuranMsptPause, 30, 50);
		kehancuranWarningSeconds = clamp(kehancuranWarningSeconds, 10, 60);
		kehancuranUndoMinutes = clamp(kehancuranUndoMinutes, 0, 30);
		// Phase 6b (DESIGN 21.24).
		sectBuildSpeed = clamp(sectBuildSpeed, 0.25D, 4.0D, 1.0D);
		sectBuildMaxPerTick = clamp(sectBuildMaxPerTick, 1, 32);
		sectBuildMsptPause = clamp(sectBuildMsptPause, 30, 50);
		sectBuildRange = clamp(sectBuildRange, 96, 512);
		sectBuildBankTicks = clamp(sectBuildBankTicks, 0, 72000);
		sectBuildMaxTemplate = clamp(sectBuildMaxTemplate, 1, 9);
		// Phase 6c (DESIGN 22.18.1 item 12).
		techniqueDamageMultiplier = clamp(techniqueDamageMultiplier, 0.1D, 10.0D, 1.0D);
		techniqueAreaCap = clamp(techniqueAreaCap, 8, 48);
		techniqueMaxActiveCasts = clamp(techniqueMaxActiveCasts, 16, 128);
		kehancuranCasterSafeRadius = clamp(kehancuranCasterSafeRadius, 3, 8);
		kehancuranHomeSafeRadius = clamp(kehancuranHomeSafeRadius, 0, 64);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	/** {@code fallback} when the file held no number (NaN). */
	private static double clamp(double value, double min, double max, double fallback) {
		return Double.isNaN(value) ? fallback : Math.max(min, Math.min(max, value));
	}

	private KultivatorConfig() {
	}
}
