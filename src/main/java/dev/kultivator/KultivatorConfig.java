package dev.kultivator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server settings in {@code config/kultivator.json}. Missing values get their defaults and the file is rewritten. */
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

	public static KultivatorConfig get() {
		return instance;
	}

	public static void load() {
		try {
			if (Files.exists(FILE)) {
				KultivatorConfig loaded = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), KultivatorConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			}
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(instance), StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException exception) {
			Kultivator.LOGGER.warn("Could not read config/kultivator.json, using defaults", exception);
		}
	}

	private KultivatorConfig() {
	}
}
