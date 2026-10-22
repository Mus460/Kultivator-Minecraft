package dev.kultivator.client.duel.fx;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.duel.ui.DuelSettings;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.ShaderCompat;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/**
 * The client settings the duel visuals read (DESIGN 35.8.3; the rows are written by module U through
 * {@link EffectQuality#put}) and the duel's own quality tier (35.4.12). Every reader is tolerant: a stored boolean, an
 * id string (Indonesian or English) or an ordinal number all work, and anything unknown falls back to the default.
 * <p>
 * Audit M4 (R5, S10): sparks, trails and the camera are read from {@link DuelSettings} (one source, one value per
 * option; the enums here only mirror its names), and the tier and the quad cap come from {@link EpicBudget}, the one
 * frame guard and quad budget shared with 7b.
 */
public final class DuelFxSettings {

	public static final String SPARKS = "duel_sparks";
	public static final String TRAILS = "duel_trails";
	public static final String SLOWMO = "duel_slowmo";
	public static final String HITSTOP = "duel_hitstop";
	public static final String INTRO = "duel_intro";
	public static final String CAMERA = "duel_camera";
	public static final String LOCKON = "duel_lockon";

	/** "Jejak bilah": Otomatis (in formal duels and for qi swings), Selalu, Mati (mirrors {@link DuelSettings.Trails}). */
	public enum Trails { OTOMATIS, SELALU, MATI }

	/** "Kamera duel": Mati, Bahu (over the shoulder), Penonton (an orbit for onlookers); mirrors {@link DuelSettings.Camera}. */
	public enum Camera { MATI, BAHU, PENONTON }

	/** "Percikan duel": {@link DuelSettings.Sparks#factor()}, 1 Penuh (default), 0.5 Ringan, 0 Mati (audit M4 R5). */
	public static float sparks() {
		return DuelSettings.sparks().factor();
	}

	public static Trails trails() {
		return Trails.valueOf(DuelSettings.trails().name());
	}

	public static Camera camera() {
		return Camera.valueOf(DuelSettings.camera().name());
	}

	/** Stores the camera mode (module U's {@code duel_camera} key cycles it through {@code DuelSettings.nextCamera}). */
	public static void setCamera(Camera mode) {
		DuelSettings.setCamera(DuelSettings.Camera.valueOf(mode.name()));
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
	 * The duel's quality tier: 0 Minimal, 1 Normal, 2 Maximal, exactly {@link EpicBudget#tierOrMinimal} (Effect Quality,
	 * the 7b session cap and the frame guard's drops). Audit M4 S10: the duel's own fps watcher is gone, so one guard
	 * decides the tier for 7b and the duel alike.
	 */
	public static int tier() {
		return EpicBudget.tierOrMinimal();
	}

	/** Once per client tick (kept for {@code DuelFx}): nothing to watch any more, {@link EpicBudget} guards the frame (S10). */
	static void tick(Minecraft minecraft) {
	}

	/** A value of the 35.4.12 budget table by tier: {minimal, normal, maximal}. */
	public static int pick(int minimal, int normal, int maximal) {
		return switch (tier()) {
			case 0 -> minimal;
			case 1 -> normal;
			default -> maximal;
		};
	}

	/**
	 * The duel's own geometry quad cap of one frame (× 0.6 under a shader pack, like 7b), never above
	 * {@link EpicBudget#quadCap}; {@code DuelGeo} also takes every quad from {@link EpicBudget#takeQuads}, so the duel and a
	 * 7b scene together stay inside {@link EpicBudget#QUADS} (audit M4 S10).
	 */
	public static int quadCap() {
		int cap = pick(600, 2400, 6000);
		return Math.min(EpicBudget.quadCap(), ShaderCompat.packOn() ? Math.round(cap * 0.6F) : cap);
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
