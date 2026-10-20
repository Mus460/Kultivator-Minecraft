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
	/** Phase 10 (DESIGN 28.8.1): a Kehancuran job of a spec with a Dampak palette turns its crater floor and rim (same job and undo). */
	public boolean kehancuranSurfacePass = true;
	/** The most blocks one surface pass converts (16-512). */
	public int kehancuranSurfaceMax = 256;

	// ---- phase 7: Dunia yang Hidup (DESIGN 19.2.10)
	/** Naturally spawned monsters near a Qi Refining+ player may become elite demonic beasts (DESIGN 19.3.1). */
	public boolean eliteMonsters = true;
	/** Multiplies the elite chance (0-5); the 7% cap still applies. */
	public double eliteChanceScale = 1.0D;
	/** Spirit stones, herbs, cores and jade slips in built-in structure chests (DESIGN 19.3.13). */
	public boolean structureLoot = true;
	/** Ores broken by a Qi Refining+ player sometimes drop spirit stones (DESIGN 19.3.2). */
	public boolean spiritStoneMining = true;
	/** Plants broken by a Qi Refining+ player may hold aged spirit herbs (DESIGN 19.3.4). */
	public boolean herbHarvest = true;
	/** The First Steps chapters of the Jalan tablet give their rewards (DESIGN 19.3.9). */
	public boolean guideRewards = true;
	/** Kewajiban Harian, the player's three daily chores (DESIGN 19.3.9). */
	public boolean dailyTasks = true;
	/** Lightning rings telegraph tribulation strikes from Deity Transformation up; off = every strike is a full hit (DESIGN 19.3.8). */
	public boolean tribulationTelegraph = true;

	// ---- phase 7b: Momen Epik (DESIGN 23.2)
	/** Epic-moment cues are sent at all; false keeps today's FX only. */
	public boolean epicMoments = true;
	/** Cues may carry the CAMERA_OK flag; false means no client ever directs its camera. */
	public boolean epicCameraShots = true;
	/** Send range in blocks of sky-scale scenes (96-512). */
	public int epicRange = 192;

	// ---- phase 7c: Formasi Sekte (DESIGN 24.13 item 11)
	/** Sect formations (Mata Formasi cores) run at all; false suspends every 7c core. */
	public boolean sectFormations = true;
	/** Killing arrays deal damage; false: they only show their FX. */
	public boolean formationDamage = true;
	/** Multiplies all sect formation damage (0-3). */
	public double sectFormationDamageScale = 1.0D;
	/** Transport arrays (M3, L5) may be used. */
	public boolean formationTransport = true;
	/** Concealment fields hide members' nametags from strangers (cosmetic). */
	public boolean formationConcealNames = true;
	/** Formation alarms may use chat; false sends every alarm to the action bar. */
	public boolean formationAlarmChat = true;
	/** Registered 7c cores of all owners in one dimension (8-256). */
	public int formationMaxCores = 64;
	/** Pattern verification block reads per tick for all cores together (8-64). */
	public int formationReadsPerTick = 16;
	/** Average added server milliseconds per tick before the auto-throttle (0.05-1.0). */
	public double formationBudgetMs = 0.15D;
	/** MSPT above which killing arrays pulse only every 40 ticks (30-50). */
	public int formationMsptSlow = 45;

	// ---- phase 8: Jalan Dunia Kultivasi (DESIGN 25.12)
	/** Secret realm gates can be opened at all. */
	public boolean secretRealms = true;
	/** Runs use the kultivator:alam_rahasia dimension; false runs every realm in Mode Ringan around the gate (zero blocks). */
	public boolean secretRealmDimension = true;
	/** Realm slots that may run at once in the dimension (1-8). */
	public int secretRealmSlots = 8;
	/** Rogue cultivators ambush travellers. */
	public boolean rogueAmbushes = true;
	/** Multiplies the ambush chance (0-3; the 15% cap stays). */
	public double rogueAmbushChanceScale = 1.0D;
	/** Rogue cultivators alive in the whole world (6-16). */
	public int rogueWorldCap = 12;
	/** Demonic sects raid Sect Halls. */
	public boolean sectRaids = true;
	/** Days between raids from hall level 3 (5-30; 0 = off). */
	public int sectRaidIntervalDays = 9;
	/** Raiders alive at once in one raid (4-12). */
	public int raidMaxAlive = 8;
	/** Seconds without input after which an owner counts as away and the raid resolves as data (30-300). */
	public int raidAfkSeconds = 60;
	/** The Earthfire Forge and artifact forging. */
	public boolean artifactForging = true;
	/** Storage pouches and rings. */
	public boolean storageRings = true;
	/** Storage rings stay with the player on death (default false: they drop like any item). */
	public boolean storageRingKeepOnDeath = false;
	/** Villages near a hall can pledge allegiance. */
	public boolean vassalVillages = true;
	/** Blocks from the hall stele a vassal village may lie (128-512). */
	public int vassalRange = 256;

	// ---- phase 9: Petualangan Dewa Abadi (DESIGN 26.12)
	/** The ascension to Alam Abadi (the kultivator:alam_abadi dimension) is possible at all. */
	public boolean immortalRealm = true;
	/** Radius of the soft circular border around 0,0 in Alam Abadi (512-4096). */
	public int immortalRealmRadius = 2048;
	/** Alam Abadi natives within 48 blocks of one player (0-6). */
	public int immortalNativesPerPlayer = 4;
	/** Alam Abadi natives alive in the whole dimension (4-16). */
	public int immortalNativeWorldCap = 12;
	/** Primordial beasts (Kun-Peng, Zhulong, Taotie) appear at all. */
	public boolean primordialBeasts = true;
	/** Multiplies the natural Gejolak Purba chance (0-3). */
	public double primordialChanceScale = 1.0D;
	/** A lethal hit from a primordial skill leaves the player at 1 HP with Luka Purba instead of killing. */
	public boolean primordialDeathGuard = true;
	/** The player's Seven Star Calamities gate the stars (false: stars are never gated). */
	public boolean starCalamities = true;
	/** The Heavens send wrath descents and the Panglima Langit. */
	public boolean heavenlyWrath = true;
	/** In-game days between two wrath rolls (3-15, ±1). */
	public int heavenlyWrathDays = 5;
	/** The inner world (Dunia Batin) is born at the ascension. */
	public boolean innerWorld = true;
	/** In-game days between two earthly calamities of the inner world (3-15; 0 = off). */
	public int innerWorldCalamityDays = 5;
	/** The Immortal auction holds sessions in Alam Abadi. */
	public boolean immortalAuction = true;
	/** Chance that an offended rival bidder sends a pursuit (0-1). */
	public double auctionPursuitChance = 0.35D;

	// ---- phase 10b: Pusaka Langit / Titah Langit (DESIGN 29.3.2)
	/** The Segel Titah Langit and its four decrees work at all. */
	public boolean titahEnabled = true;
	/** Largest visual radius of a decree in blocks (32-256); clients also stay inside their render distance. */
	public int titahVisualRadiusMax = 128;
	/** Largest damage radius of one evaluation (16-64; 40 = the Dewa WAVE/DOMAIN cap). */
	public int titahDamageRadiusMax = 40;
	/** Largest real carve radius under Mode Kehancuran (8-48). */
	public int titahCarveRadiusMax = 24;
	/** Blocks one decree may change under Mode Kehancuran (1024-32768), at kehancuranBlocksPerTick. */
	public int titahBlocksPerCast = 8192;
	/** Blocks of one block monument (Batu Pusaka, Urat Pusaka) under Mode Kehancuran (0-256; 0 = none). */
	public int titahMonumentBlocks = 96;
	/** In-game days a planted weapon stays (1-14). */
	public int titahMonumentDays = 3;
	/** Planted weapons one player may have (1-8); a new one beyond it sends the oldest back to heaven. */
	public int titahMonumentsPerPlayer = 3;
	/** Planted weapons in the whole world (1-32). */
	public int titahMonumentsPerWorld = 12;
	/** Pull-out rewards one player may get per game day (0-10). */
	public int titahRewardsPerDay = 3;
	/** How far the seal aims (64-192). */
	public int titahRange = 128;
	/** The other Dewa ultimates also leave a planted weapon entity (default false, never a reward). */
	public boolean titahUltimateMonuments = false;

	// ---- phase 10b: Tingkat Titah 1-9 and the fast bulk carve (DESIGN 29.16.12)
	/** Highest Tingkat any player can reach (1-9). */
	public int titahMaxTier = 9;
	/** Cap on a decree's scaled visual, damage and carve radius in blocks (128-4096). */
	public int titahMaxRadius = 2500;
	/** Targets one area hit of a decree takes at most (32-1024), bosses first, then nearest. */
	public int titahMaxTargets = 256;
	/** Milliseconds the bulk carve may use per server tick (1-20). */
	public int titahBulkMillisPerTick = 8;
	/** Above this average tick time (ms, 25-50) the bulk carve budget halves. */
	public int titahBulkMsptSlow = 35;
	/** Blocks one bulk job may change at most (100,000-50,000,000); the crater shrinks to fit. */
	public int titahBulkMaxBlocks = 6_000_000;
	/** Blocks an undoable bulk job (Tingkat 2-5) may change (0-4,000,000; 0 = Tingkat 2-5 carve nothing). */
	public int titahUndoMaxBlocks = 1_000_000;
	/** Chunks one bulk job may hold (64-4096). */
	public int titahBulkMaxChunks = 1024;
	/** Carved chunks that may wait for their light before the resend (2-32). */
	public int titahBulkLightChunks = 8;

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

	/** Keeps every phase-6, 6b, 6c, 7, 7b, 7c, 8 and 9 number inside its allowed range (DESIGN 20.2.11, 21.24, 22.18.1, 19.2.10, 23.2, 24.13, 25.12, 26.12). */
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
		// Phase 10 (DESIGN 28.8.1).
		kehancuranSurfaceMax = clamp(kehancuranSurfaceMax, 16, 512);
		// Phase 7 (DESIGN 19.2.10).
		eliteChanceScale = clamp(eliteChanceScale, 0.0D, 5.0D, 1.0D);
		// Phase 7b (DESIGN 23.2).
		epicRange = clamp(epicRange, 96, 512);
		// Phase 7c (DESIGN 24.13 item 11).
		sectFormationDamageScale = clamp(sectFormationDamageScale, 0.0D, 3.0D, 1.0D);
		formationMaxCores = clamp(formationMaxCores, 8, 256);
		formationReadsPerTick = clamp(formationReadsPerTick, 8, 64);
		formationBudgetMs = clamp(formationBudgetMs, 0.05D, 1.0D, 0.15D);
		formationMsptSlow = clamp(formationMsptSlow, 30, 50);
		// Phase 8 (DESIGN 25.12).
		secretRealmSlots = clamp(secretRealmSlots, 1, 8);
		rogueAmbushChanceScale = clamp(rogueAmbushChanceScale, 0.0D, 3.0D, 1.0D);
		rogueWorldCap = clamp(rogueWorldCap, 6, 16);
		sectRaidIntervalDays = sectRaidIntervalDays <= 0 ? 0 : clamp(sectRaidIntervalDays, 5, 30);
		raidMaxAlive = clamp(raidMaxAlive, 4, 12);
		raidAfkSeconds = clamp(raidAfkSeconds, 30, 300);
		vassalRange = clamp(vassalRange, 128, 512);
		// Phase 9 (DESIGN 26.12).
		immortalRealmRadius = clamp(immortalRealmRadius, 512, 4096);
		immortalNativesPerPlayer = clamp(immortalNativesPerPlayer, 0, 6);
		immortalNativeWorldCap = clamp(immortalNativeWorldCap, 4, 16);
		primordialChanceScale = clamp(primordialChanceScale, 0.0D, 3.0D, 1.0D);
		heavenlyWrathDays = clamp(heavenlyWrathDays, 3, 15);
		innerWorldCalamityDays = innerWorldCalamityDays <= 0 ? 0 : clamp(innerWorldCalamityDays, 3, 15);
		auctionPursuitChance = clamp(auctionPursuitChance, 0.0D, 1.0D, 0.35D);
		// Phase 10b (DESIGN 29.3.2).
		titahVisualRadiusMax = clamp(titahVisualRadiusMax, 32, 256);
		titahDamageRadiusMax = clamp(titahDamageRadiusMax, 16, 64);
		titahCarveRadiusMax = clamp(titahCarveRadiusMax, 8, 48);
		titahBlocksPerCast = clamp(titahBlocksPerCast, 1024, 32768);
		titahMonumentBlocks = clamp(titahMonumentBlocks, 0, 256);
		titahMonumentDays = clamp(titahMonumentDays, 1, 14);
		titahMonumentsPerPlayer = clamp(titahMonumentsPerPlayer, 1, 8);
		titahMonumentsPerWorld = clamp(titahMonumentsPerWorld, 1, 32);
		titahRewardsPerDay = clamp(titahRewardsPerDay, 0, 10);
		titahRange = clamp(titahRange, 64, 192);
		// Phase 10b Tingkat (DESIGN 29.16.12).
		titahMaxTier = clamp(titahMaxTier, 1, 9);
		titahMaxRadius = clamp(titahMaxRadius, 128, 4096);
		titahMaxTargets = clamp(titahMaxTargets, 32, 1024);
		titahBulkMillisPerTick = clamp(titahBulkMillisPerTick, 1, 20);
		titahBulkMsptSlow = clamp(titahBulkMsptSlow, 25, 50);
		titahBulkMaxBlocks = clamp(titahBulkMaxBlocks, 100_000, 50_000_000);
		titahUndoMaxBlocks = clamp(titahUndoMaxBlocks, 0, 4_000_000);
		titahBulkMaxChunks = clamp(titahBulkMaxChunks, 64, 4096);
		titahBulkLightChunks = clamp(titahBulkLightChunks, 2, 32);
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
