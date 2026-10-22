package dev.kultivator.duel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kultivator.Kultivator;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The server settings of the sword duel, {@code config/kultivator-duel.json} (DESIGN 35.8.3). Public fields with their
 * defaults; missing keys get the default, numbers are clamped, unknown keys in the file are kept, and the file is
 * rewritten with the result. A file that cannot be parsed is left alone (the admin may want to fix it) and the
 * defaults run meanwhile. {@link #reload()} ({@code /kultivasi uji duel muat}) reads it again.
 */
public final class DuelConfig {

	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("kultivator-duel.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static volatile DuelConfig current;

	/** The whole duel system (off: the clash layer and formal duels are gone, vanilla combat as before). */
	public boolean enabled = true;
	/** Formal duels (challenges, sessions, stakes). */
	public boolean formalDuels = true;
	/** Deflect and guard against mobs' melee blows outside a duel (35.3.13). */
	public boolean mobParry = true;
	/** Posture bars for elites, rogues, primordials, rival bodies, NPCs and bosses. */
	public boolean mobPosture = true;
	/** A player's blow and an armed mob's blow within 2 ticks of each other clash (both cancelled, sparks). */
	public boolean armedMobClash = true;
	/** A held guard against a mob's melee blow takes this share of the damage. */
	public double guardDamageFactor = 0.4D;
	/** Latency compensation cap in ticks (4 = 200 ms). */
	public int maxRewindTicks = 4;
	/** A challenge lapses after this many seconds. */
	public int answerSeconds = 60;
	/** A challenge reaches this far (blocks). */
	public double challengeRange = 24.0D;
	/** The ring radius when a challenge does not say (6, 9 or 12). */
	public int ringRadiusDefault = 9;
	/** Ticks outside the ring line that lose the duel. */
	public int ringOutTicks = 60;
	/** The default time limit of a fight. */
	public int timeLimitSeconds = 180;
	/** Running sessions at most. */
	public int maxSessions = 16;
	/** Spirit stone and jade slip stakes. */
	public boolean allowStakes = true;
	public int maxStoneCount = 64;
	public boolean allowSlipStakes = true;
	/** Honour (Elo) and its titles. */
	public boolean honour = true;
	/** Wandering NPCs may offer duels by themselves. */
	public boolean npcOffers = true;
	/** At most one NPC offer per player per this many minutes. */
	public int npcOfferMinutes = 20;
	/** XP and Sword Dao comprehension for real wins. */
	public boolean rewards = true;
	/** The cinematic intro lasts at most this long (ticks). */
	public int introFilmMaxTicks = 100;
	/** Direct vanilla blows between duel partners are refused when the hitter's client uses KLASIK (no macros). */
	public boolean blockVanillaHitsInClassic = true;
	/** Combat Roll's roll gives the 6 dodge ticks. */
	public boolean combatRollCompat = true;

	/** The settings in use (loaded on first use). */
	public static DuelConfig get() {
		DuelConfig config = current;
		if (config == null) {
			synchronized (DuelConfig.class) {
				config = current;
				if (config == null) {
					config = load();
					current = config;
				}
			}
		}
		return config;
	}

	/** Reads the file again; false when it could not be parsed (the old values stay). */
	public static synchronized boolean reload() {
		JsonObject root = readRoot();
		if (root == null && Files.exists(FILE)) {
			return false;
		}
		current = load();
		return true;
	}

	public static Path file() {
		return FILE;
	}

	private static DuelConfig load() {
		JsonObject root = readRoot();
		boolean broken = root == null && Files.exists(FILE);
		DuelConfig config = null;
		if (root != null) {
			try {
				config = GSON.fromJson(root, DuelConfig.class);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Duel config {} is broken, using the defaults", FILE, exception);
				broken = true;
			}
		}
		if (config == null) {
			config = new DuelConfig();
		}
		config.clamp();
		if (!broken) {
			// Unknown keys are kept: the file's own object first, then every field of ours over it.
			JsonObject out = root != null ? root.deepCopy() : new JsonObject();
			for (Map.Entry<String, JsonElement> entry : GSON.toJsonTree(config).getAsJsonObject().entrySet()) {
				out.add(entry.getKey(), entry.getValue());
			}
			try {
				Files.createDirectories(FILE.getParent());
				Files.writeString(FILE, GSON.toJson(out), StandardCharsets.UTF_8);
			} catch (IOException exception) {
				Kultivator.LOGGER.warn("Could not write {}", FILE, exception);
			}
		}
		return config;
	}

	private static JsonObject readRoot() {
		if (!Files.exists(FILE)) {
			return null;
		}
		try {
			JsonElement element = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8));
			return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
		} catch (IOException | RuntimeException exception) {
			Kultivator.LOGGER.warn("Duel config {} could not be read", FILE, exception);
			return null;
		}
	}

	private void clamp() {
		guardDamageFactor = Math.max(0.0D, Math.min(1.0D, guardDamageFactor));
		maxRewindTicks = Math.max(0, Math.min(8, maxRewindTicks));
		answerSeconds = Math.max(10, Math.min(600, answerSeconds));
		challengeRange = Math.max(4.0D, Math.min(64.0D, challengeRange));
		ringRadiusDefault = ringRadiusDefault <= 7 ? 6 : ringRadiusDefault >= 11 ? 12 : 9;
		ringOutTicks = Math.max(20, Math.min(400, ringOutTicks));
		timeLimitSeconds = Math.max(30, Math.min(600, timeLimitSeconds));
		maxSessions = Math.max(1, Math.min(64, maxSessions));
		maxStoneCount = Math.max(1, Math.min(64, maxStoneCount));
		npcOfferMinutes = Math.max(1, Math.min(600, npcOfferMinutes));
		introFilmMaxTicks = Math.max(40, Math.min(100, introFilmMaxTicks));
	}

	/** The answer window in ticks. */
	public int answerTicks() {
		return answerSeconds * 20;
	}

	/** The default time limit in ticks. */
	public int timeLimitTicks() {
		return timeLimitSeconds * 20;
	}
}
