package dev.kultivator.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kultivator.Kultivator;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client settings in {@code config/kultivator-client.json}: Effect Quality (Minimal / Normal / Maximal, or "Otomatis"),
 * whether the HUD is shown, and the technique settings of DESIGN 22.12.9 (shake, flash, phantom displays, others'
 * effects, the seal wheel, reduce motion) plus the codex's remembered filters. Every particle count of the mod goes
 * through {@link #scale(int)}; unknown keys of the file are kept when it is saved.
 * <p>
 * "Otomatis" (19.2.16, 22.15.2): the FPS averaged over 5 s below 40 switches to Minimal, 10 s above 70 back to Normal,
 * never Maximal; {@link #level()} always returns the level in effect.
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

	/** Camera shake strength (22.4.5): Penuh 1, Ringan 0.4 (default), Mati 0. */
	public enum Shake {
		FULL("full", 1.0F), LIGHT("light", 0.4F), OFF("off", 0.0F);

		private final String id;
		private final float factor;

		Shake(String id, float factor) {
			this.id = id;
			this.factor = factor;
		}

		public String id() {
			return id;
		}

		public float factor() {
			return factor;
		}
	}

	/** Other players' technique effects (22.15.2): Penuh ×1, Ringan ×0.25 without displays, Mati. */
	public enum Others {
		FULL("full", 1.0F), LIGHT("light", 0.25F), OFF("off", 0.0F);

		private final String id;
		private final float factor;

		Others(String id, float factor) {
			this.id = id;
			this.factor = factor;
		}

		public String id() {
			return id;
		}

		public float factor() {
			return factor;
		}
	}

	/** Auto quality thresholds (19.2.16). */
	public static final float AUTO_LOW_FPS = 40.0F;
	public static final float AUTO_HIGH_FPS = 70.0F;
	public static final int AUTO_RECOVER_TICKS = 200;
	private static final int AUTO_WINDOW = 100;

	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("kultivator-client.json");
	private static Level level = Level.NORMAL;
	private static boolean hudVisible = true;
	private static boolean auto;
	private static Level autoLevel = Level.NORMAL;
	private static Shake shake = Shake.LIGHT;
	private static boolean flash = true;
	private static boolean phantoms = true;
	private static Others others = Others.FULL;
	private static boolean wheelHold = true;
	private static boolean releaseCast;
	private static boolean reduceMotion;
	/** Everything else of the file (codex filters and seen set, other modules' keys), kept as read. */
	private static JsonObject extra = new JsonObject();

	private static final float[] FPS = new float[AUTO_WINDOW];
	private static int fpsIndex;
	private static int fpsCount;
	private static int highTicks;

	/** The level in effect (with "Otomatis" on: Minimal or Normal by the frame rate). */
	public static Level level() {
		return auto ? autoLevel : level;
	}

	/** The level chosen by hand (shown stamped when "Otomatis" is off). */
	public static Level chosenLevel() {
		return level;
	}

	public static void setLevel(Level newLevel) {
		level = newLevel;
		auto = false;
		save();
	}

	public static boolean auto() {
		return auto;
	}

	public static void setAuto(boolean on) {
		auto = on;
		autoLevel = Level.NORMAL;
		highTicks = 0;
		fpsCount = 0;
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
		return level() == Level.MINIMAL;
	}

	public static boolean maximal() {
		return level() == Level.MAXIMAL;
	}

	/** {@code count} particles at Normal quality, scaled for the current setting (at least 1 when count > 0). */
	public static int scale(int count) {
		if (count <= 0) {
			return 0;
		}
		return Math.max(1, Math.round(count * level().factor()));
	}

	/** For continuous effects: true on the share of ticks the current quality allows (always at Normal and above). */
	public static boolean chance(net.minecraft.util.RandomSource random, float normalChance) {
		return random.nextFloat() < normalChance * level().factor();
	}

	// ================================================================== technique settings (22.12.9)

	public static Shake shake() {
		return shake;
	}

	public static void setShake(Shake value) {
		shake = value;
		save();
	}

	public static boolean flash() {
		return flash;
	}

	public static void setFlash(boolean on) {
		flash = on;
		save();
	}

	/** "Tampilan raksasa": client-only phantom displays allowed. */
	public static boolean phantoms() {
		return phantoms;
	}

	public static void setPhantoms(boolean on) {
		phantoms = on;
		save();
	}

	public static Others others() {
		return others;
	}

	public static void setOthers(Others value) {
		others = value;
		save();
	}

	/** "Tahan G untuk roda": holding the cast key opens the seal wheel. */
	public static boolean wheelHold() {
		return wheelHold;
	}

	public static void setWheelHold(boolean on) {
		wheelHold = on;
		save();
	}

	/** "Lepas = rapal": releasing the wheel casts the hovered seal instead of selecting it. */
	public static boolean releaseCast() {
		return releaseCast;
	}

	public static void setReleaseCast(boolean on) {
		releaseCast = on;
		save();
	}

	/** "Kurangi gerak": no Dewa shimmer and no screen flashes. */
	public static boolean reduceMotion() {
		return reduceMotion;
	}

	public static void setReduceMotion(boolean on) {
		reduceMotion = on;
		save();
	}

	/** Particle factor for other players' casts (22.15.2). */
	public static float othersScale() {
		return others.factor();
	}

	/** Phantom displays one cast may create at the level in effect: 2 (Minimal, grade ≥ Langit only) / 8 / 24. */
	public static int displayAllowance(int gradeTier) {
		return switch (level()) {
			case MINIMAL -> gradeTier >= 3 ? 2 : 0;
			case NORMAL -> 8;
			case MAXIMAL -> 24;
		};
	}

	/** Phantom displays alive at once: 8 / 32 / 96. */
	public static int displaysAlive() {
		return switch (level()) {
			case MINIMAL -> 8;
			case NORMAL -> 32;
			case MAXIMAL -> 96;
		};
	}

	/** A stored value of another client feature (codex filters, seen set), or null. */
	public static @Nullable JsonElement get(String key) {
		return extra.get(key);
	}

	/** Stores a value of another client feature and saves the file. */
	public static void put(String key, @Nullable JsonElement value) {
		if (value == null) {
			extra.remove(key);
		} else {
			extra.add(key, value);
		}
		save();
	}

	// ================================================================== "Otomatis"

	/** Every client tick: averages the frame rate over 5 s and moves the automatic level (with hysteresis). */
	public static void tickAuto(Minecraft minecraft) {
		if (!auto || minecraft.level == null || minecraft.isPaused()) {
			return;
		}
		FPS[fpsIndex] = minecraft.getFps();
		fpsIndex = (fpsIndex + 1) % AUTO_WINDOW;
		fpsCount = Math.min(AUTO_WINDOW, fpsCount + 1);
		if (fpsCount < AUTO_WINDOW) {
			return;
		}
		float sum = 0.0F;
		for (float fps : FPS) {
			sum += fps;
		}
		float average = sum / AUTO_WINDOW;
		if (autoLevel != Level.MINIMAL && average < AUTO_LOW_FPS) {
			autoLevel = Level.MINIMAL;
			highTicks = 0;
		} else if (autoLevel == Level.MINIMAL) {
			highTicks = average > AUTO_HIGH_FPS ? highTicks + 1 : 0;
			if (highTicks >= AUTO_RECOVER_TICKS) {
				autoLevel = Level.NORMAL;
				highTicks = 0;
			}
		}
	}

	// ================================================================== file

	public static void load() {
		try {
			if (!Files.exists(FILE)) {
				return;
			}
			JsonObject json = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
			String saved = string(json, "effect_quality", "normal");
			for (Level each : Level.values()) {
				if (each.id.equals(saved)) {
					level = each;
				}
			}
			hudVisible = bool(json, "hud", true);
			auto = bool(json, "quality_auto", false);
			String shakeId = string(json, "shake", Shake.LIGHT.id);
			for (Shake each : Shake.values()) {
				if (each.id.equals(shakeId)) {
					shake = each;
				}
			}
			String othersId = string(json, "others_fx", Others.FULL.id);
			for (Others each : Others.values()) {
				if (each.id.equals(othersId)) {
					others = each;
				}
			}
			flash = bool(json, "flash", true);
			phantoms = bool(json, "phantoms", true);
			wheelHold = bool(json, "wheel_hold", true);
			releaseCast = bool(json, "release_cast", false);
			reduceMotion = bool(json, "reduce_motion", false);
			extra = json.deepCopy();
			for (String key : OWN_KEYS) {
				extra.remove(key);
			}
		} catch (IOException | RuntimeException exception) {
			Kultivator.LOGGER.warn("Could not read config/kultivator-client.json", exception);
		}
	}

	private static final String[] OWN_KEYS = {"effect_quality", "hud", "quality_auto", "shake", "others_fx", "flash", "phantoms", "wheel_hold",
			"release_cast", "reduce_motion"};

	private static String string(JsonObject json, String key, String fallback) {
		try {
			return json.has(key) ? json.get(key).getAsString() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static boolean bool(JsonObject json, String key, boolean fallback) {
		try {
			return json.has(key) ? json.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static void save() {
		try {
			JsonObject json = extra.deepCopy();
			json.addProperty("effect_quality", level.id);
			json.addProperty("hud", hudVisible);
			json.addProperty("quality_auto", auto);
			json.addProperty("shake", shake.id);
			json.addProperty("others_fx", others.id);
			json.addProperty("flash", flash);
			json.addProperty("phantoms", phantoms);
			json.addProperty("wheel_hold", wheelHold);
			json.addProperty("release_cast", releaseCast);
			json.addProperty("reduce_motion", reduceMotion);
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, json.toString(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			Kultivator.LOGGER.warn("Could not save config/kultivator-client.json", exception);
		}
	}

	private EffectQuality() {
	}
}
