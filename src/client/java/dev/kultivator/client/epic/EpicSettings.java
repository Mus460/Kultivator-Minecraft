package dev.kultivator.client.epic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.kultivator.client.EffectQuality;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The 7b client settings (DESIGN 23.2), stored as the {@code epic_*} keys of {@code config/kultivator-client.json}
 * through {@link EffectQuality#get} / {@link EffectQuality#put}. Written by the spine with the final signatures
 * (23.17.3); P7b-sinema owns it and may extend it. The getters return the stored choice only: the director applies
 * "Kurangi gerak" (camera Mati, no slow motion) on top.
 *
 * <p>Text keys (in {@code lang-src/momen.*}, P7b-sinema): {@code kultivator.momen.setting.<id>} (cycle rows take the
 * value as {@code %s}), {@code .<id>.tip}, and the camera values {@code kultivator.momen.setting.camera.<penuh|singkat|mati>}.
 */
public final class EpicSettings {

	/** One row of the "Momen Epik" settings group: a jade coin ({@code cycle == false}) or a cycling talisman. */
	public record Row(String key, boolean cycle, Supplier<Component> value, Runnable next, Supplier<List<Component>> tooltip) {
	}

	/** Kamera sinematik: Penuh (danger micro-shots too), Singkat (safe beats only, default), Mati. */
	public enum Camera {
		PENUH, SINGKAT, MATI;

		/** The id used in the config file, the text keys and {@code /sinematik}. */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public static final String CAMERA = "epic_camera";
	public static final String LETTERBOX = "epic_letterbox";
	public static final String CARDS = "epic_cards";
	public static final String MUSIC = "epic_music";
	public static final String SLOWMO = "epic_slowmo";
	public static final String GEOMETRY = "epic_geometry";
	public static final String SKY = "epic_sky";
	public static final String VFX = "epic_vfx";
	public static final String RECORD = "epic_record";
	public static final String PACK_GEOMETRY = "epic_pack_geometry";
	/** Spec ids already cast once (the first cast of an Abadi ultimate gets a FULL card). */
	public static final String SEEN = "epic_seen";

	private static final Set<String> SESSION_SEEN = new HashSet<>();

	private static final float[] VFX_STEPS = {0.5F, 1.0F, 1.5F};
	private static final String T = "kultivator.momen.setting.";

	/**
	 * The rows of the Settings page, in order (Mode Rekam last; {@code epic_pack_geometry} has no row). Empty while the
	 * row texts are not in the language files yet, so the page never shows raw keys.
	 */
	public static List<Row> rows() {
		if (!Language.getInstance().has(T + "camera")) {
			return List.of();
		}
		return List.of(
				new Row(CAMERA, true, () -> Component.translatable(T + "camera", Component.translatable(T + "camera." + camera().id())),
						() -> setCamera(Camera.values()[(camera().ordinal() + 1) % Camera.values().length]), () -> tip("camera",
						Component.translatable(T + "camera", Component.translatable(T + "camera." + camera().id())))),
				toggle(LETTERBOX, "letterbox"),
				toggle(CARDS, "cards"),
				toggle(MUSIC, "music"),
				toggle(SLOWMO, "slowmo"),
				toggle(GEOMETRY, "geometry"),
				toggle(SKY, "sky"),
				new Row(VFX, true, () -> Component.translatable(T + "vfx", vfxText()), EpicSettings::nextVfx,
						() -> tip("vfx", Component.translatable(T + "vfx", vfxText()))),
				toggle(RECORD, "record"));
	}

	/** The on/off state of a boolean {@code epic_*} key with its default (Mode Rekam off, everything else on). */
	public static boolean flag(String key) {
		JsonElement value = EffectQuality.get(key);
		if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
			return value.getAsBoolean();
		}
		return !RECORD.equals(key);
	}

	/** Stores a boolean {@code epic_*} key. */
	public static void setFlag(String key, boolean on) {
		EffectQuality.put(key, new JsonPrimitive(on));
	}

	public static Camera camera() {
		JsonElement value = EffectQuality.get(CAMERA);
		if (value != null && value.isJsonPrimitive()) {
			String id = value.getAsString();
			for (Camera camera : Camera.values()) {
				if (camera.id().equals(id)) {
					return camera;
				}
			}
		}
		return Camera.SINGKAT;
	}

	public static void setCamera(Camera camera) {
		EffectQuality.put(CAMERA, new JsonPrimitive(camera.id()));
	}

	/** Skala VFX momen: 0.5, 1.0 (default) or 1.5. */
	public static float vfx() {
		JsonElement value = EffectQuality.get(VFX);
		if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
			float stored = value.getAsFloat();
			for (float step : VFX_STEPS) {
				if (Math.abs(step - stored) < 0.01F) {
					return step;
				}
			}
		}
		return 1.0F;
	}

	public static void setVfx(float scale) {
		float best = 1.0F;
		for (float step : VFX_STEPS) {
			if (Math.abs(step - scale) < Math.abs(best - scale)) {
				best = step;
			}
		}
		EffectQuality.put(VFX, new JsonPrimitive(best));
	}

	public static boolean letterbox() {
		return flag(LETTERBOX);
	}

	public static boolean cards() {
		return flag(CARDS);
	}

	public static boolean music() {
		return flag(MUSIC);
	}

	public static boolean slowmo() {
		return flag(SLOWMO);
	}

	public static boolean geometry() {
		return flag(GEOMETRY);
	}

	public static boolean sky() {
		return flag(SKY);
	}

	public static boolean record() {
		return flag(RECORD);
	}

	public static boolean packGeometry() {
		return flag(PACK_GEOMETRY);
	}

	/** True when the spec was cast before (ever, from {@code epic_seen}); marks it seen. */
	public static boolean seenEver(String specId) {
		JsonElement value = EffectQuality.get(SEEN);
		JsonArray list = value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
		for (JsonElement element : list) {
			if (element.isJsonPrimitive() && specId.equals(element.getAsString())) {
				return true;
			}
		}
		if (list.size() < 256) {
			list.add(specId);
			EffectQuality.put(SEEN, list);
		}
		return false;
	}

	/** True when the spec was cast before in this session (Dewa ultimates); marks it seen. */
	public static boolean seenThisSession(String specId) {
		return !SESSION_SEEN.add(specId);
	}

	/** Every 7b setting back to its default ({@code /sinematik reset}). */
	public static void resetAll() {
		for (String key : new String[]{CAMERA, LETTERBOX, CARDS, MUSIC, SLOWMO, GEOMETRY, SKY, VFX, RECORD, PACK_GEOMETRY}) {
			EffectQuality.put(key, null);
		}
	}

	private static Row toggle(String key, String id) {
		return new Row(key, false, () -> Component.translatable(T + id), () -> setFlag(key, !flag(key)), () -> tip(id, Component.translatable(T + id)));
	}

	private static List<Component> tip(String id, Component title) {
		return List.of(title, Component.translatable(T + id + ".tip").withStyle(ChatFormatting.GRAY));
	}

	private static Component vfxText() {
		return Component.literal("×" + vfx());
	}

	private static void nextVfx() {
		float now = vfx();
		int index = 0;
		for (int i = 0; i < VFX_STEPS.length; i++) {
			if (VFX_STEPS[i] == now) {
				index = i;
			}
		}
		setVfx(VFX_STEPS[(index + 1) % VFX_STEPS.length]);
	}

	private EpicSettings() {
	}
}
