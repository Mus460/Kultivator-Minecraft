package dev.kultivator.client.duel.fx;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.ShaderCompat;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/**
 * The client settings the duel visuals read (DESIGN 35.8.3; the rows are written by module U through
 * {@link EffectQuality#put}) and the duel's own quality tier (35.4.12). Every reader is tolerant: a stored boolean, an
 * id string (Indonesian or English) or an ordinal number all work, and anything unknown falls back to the default.
 */
public final class DuelFxSettings {

	public static final String SPARKS = "duel_sparks";
	public static final String TRAILS = "duel_trails";
	public static final String SLOWMO = "duel_slowmo";
	public static final String HITSTOP = "duel_hitstop";
	public static final String INTRO = "duel_intro";
	public static final String CAMERA = "duel_camera";
	public static final String LOCKON = "duel_lockon";

	/** "Jejak bilah": Otomatis (in formal duels and for qi swings), Selalu, Mati. */
	public enum Trails { OTOMATIS, SELALU, MATI }

	/** "Kamera duel": Mati, Bahu (over the shoulder), Penonton (an orbit for onlookers). */
	public enum Camera { MATI, BAHU, PENONTON }

	private static float fpsAverage = 60.0F;
	private static int lowFrames;
	private static boolean stepDown;

	/** "Percikan duel": 1 Penuh (default), 0.5 Ringan, 0 Mati. */
	public static float sparks() {
		String id = id(SPARKS);
		if (id == null) {
			return 1.0F;
		}
		return switch (id) {
			case "ringan", "light", "1" -> 0.5F;
			case "mati", "off", "false", "2" -> 0.0F;
			default -> 1.0F;
		};
	}

	public static Trails trails() {
		String id = id(TRAILS);
		if (id == null) {
			return Trails.OTOMATIS;
		}
		return switch (id) {
			case "selalu", "always", "1" -> Trails.SELALU;
			case "mati", "off", "false", "2" -> Trails.MATI;
			default -> Trails.OTOMATIS;
		};
	}

	public static Camera camera() {
		String id = id(CAMERA);
		if (id == null) {
			return Camera.MATI;
		}
		return switch (id) {
			case "bahu", "shoulder", "1" -> Camera.BAHU;
			case "penonton", "spectator", "2" -> Camera.PENONTON;
			default -> Camera.MATI;
		};
	}

	/** Stores the camera mode (module U's {@code duel_camera} key cycles it through {@code DuelSettings.nextCamera}). */
	public static void setCamera(Camera mode) {
		EffectQuality.put(CAMERA, new JsonPrimitive(mode.name().toLowerCase(Locale.ROOT)));
	}

	public static boolean slowmo() {
		return flag(SLOWMO, true);
	}

	public static boolean hitStop() {
		return flag(HITSTOP, true);
	}

	public static boolean intro() {
		return flag(INTRO, true);
	}

	public static boolean lockOn() {
		return flag(LOCKON, false);
	}

	/**
	 * The duel's quality tier: 0 Minimal, 1 Normal, 2 Maximal (Effect Quality and the 7b session cap via
	 * {@link EpicBudget#tierOrMinimal}), one step lower while the frame rate stays under {@link EpicBudget#MIN_FPS} (35.4.12).
	 */
	public static int tier() {
		int tier = Math.min(EffectQuality.level().ordinal(), EpicBudget.tierOrMinimal());
		return Math.max(0, stepDown ? tier - 1 : tier);
	}

	/** Once per client tick: watches the frame rate for the one-step drop (hysteresis 30 → 40 fps over 2 s). */
	static void tick(Minecraft minecraft) {
		if (minecraft.isPaused()) {
			return;
		}
		fpsAverage = fpsAverage * 0.9F + minecraft.getFps() * 0.1F;
		if (!stepDown && fpsAverage < EpicBudget.MIN_FPS) {
			if (++lowFrames >= 40) {
				stepDown = true;
				lowFrames = 0;
			}
		} else if (stepDown && fpsAverage > EpicBudget.MIN_FPS + 10.0F) {
			if (++lowFrames >= 40) {
				stepDown = false;
				lowFrames = 0;
			}
		} else {
			lowFrames = 0;
		}
	}

	/** A value of the 35.4.12 budget table by tier: {minimal, normal, maximal}. */
	public static int pick(int minimal, int normal, int maximal) {
		return switch (tier()) {
			case 0 -> minimal;
			case 1 -> normal;
			default -> maximal;
		};
	}

	/** The geometry quad cap of one frame (× 0.6 under a shader pack, like 7b). */
	public static int quadCap() {
		int cap = pick(600, 2400, 6000);
		return ShaderCompat.packOn() ? Math.round(cap * 0.6F) : cap;
	}

	private static boolean flag(String key, boolean fallback) {
		String id = id(key);
		if (id == null) {
			return fallback;
		}
		return switch (id) {
			case "true", "nyala", "on", "1" -> true;
			case "false", "mati", "off", "0" -> false;
			default -> fallback;
		};
	}

	/** The stored value as a lower-case id ("true"/"false" for booleans, the number as text), or null. */
	private static String id(String key) {
		JsonElement value = EffectQuality.get(key);
		if (value == null || !value.isJsonPrimitive()) {
			return null;
		}
		JsonPrimitive primitive = value.getAsJsonPrimitive();
		if (primitive.isBoolean()) {
			return primitive.getAsBoolean() ? "true" : "false";
		}
		if (primitive.isNumber()) {
			return Integer.toString(primitive.getAsInt());
		}
		return primitive.getAsString().toLowerCase(Locale.ROOT).trim();
	}

	private DuelFxSettings() {
	}
}
