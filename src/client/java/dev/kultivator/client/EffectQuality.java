package dev.kultivator.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kultivator.Kultivator;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client settings in {@code config/kultivator-client.json}: Effect Quality (Minimal / Normal / Maximal) and whether the
 * HUD is shown. Every particle count of the mod goes through {@link #scale(int)}.
 */
public final class EffectQuality {

	public enum Level {
		MINIMAL("minimal", 0.25F),
		NORMAL("normal", 1.0F),
		MAXIMAL("maximal", 2.0F);

		private final String id;
		private final float factor;

		Level(String id, float factor) {
			this.id = id;
			this.factor = factor;
		}

		public String id() {
			return id;
		}

		public float factor() {
			return factor;
		}

		public Component displayName() {
			return Component.translatable("kultivator.quality_setting." + id);
		}
	}

	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("kultivator-client.json");
	private static Level level = Level.NORMAL;
	private static boolean hudVisible = true;

	public static Level level() {
		return level;
	}

	public static void setLevel(Level newLevel) {
		level = newLevel;
		save();
	}

	public static boolean hudVisible() {
		return hudVisible;
	}

	public static void setHudVisible(boolean visible) {
		hudVisible = visible;
		save();
	}

	public static boolean minimal() {
		return level == Level.MINIMAL;
	}

	public static boolean maximal() {
		return level == Level.MAXIMAL;
	}

	/** {@code count} particles at Normal quality, scaled for the current setting (at least 1 when count > 0). */
	public static int scale(int count) {
		if (count <= 0) {
			return 0;
		}
		return Math.max(1, Math.round(count * level.factor()));
	}

	/** For continuous effects: true on the share of ticks the current quality allows (always at Normal and above). */
	public static boolean chance(net.minecraft.util.RandomSource random, float normalChance) {
		return random.nextFloat() < normalChance * level.factor();
	}

	public static void load() {
		try {
			if (!Files.exists(FILE)) {
				return;
			}
			JsonObject json = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
			String saved = json.has("effect_quality") ? json.get("effect_quality").getAsString() : "normal";
			for (Level each : Level.values()) {
				if (each.id.equals(saved)) {
					level = each;
				}
			}
			hudVisible = !json.has("hud") || json.get("hud").getAsBoolean();
		} catch (IOException | RuntimeException exception) {
			Kultivator.LOGGER.warn("Could not read config/kultivator-client.json", exception);
		}
	}

	private static void save() {
		try {
			JsonObject json = new JsonObject();
			json.addProperty("effect_quality", level.id);
			json.addProperty("hud", hudVisible);
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, json.toString(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			Kultivator.LOGGER.warn("Could not save config/kultivator-client.json", exception);
		}
	}

	private EffectQuality() {
	}
}
