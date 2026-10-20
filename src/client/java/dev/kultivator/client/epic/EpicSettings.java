package dev.kultivator.client.epic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.lens.LensLayers;
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

	// Phase 10 Dampak Langit (DESIGN 28.11): the rows after "Kamera sinematik".
	public static final String SKYRIDE = "epic_skyride";
	public static final String IMPACT_FRAMES = "epic_impact_frames";
	/** True once the player confirmed the photosensitivity warning of "Bingkai dampak: Penuh". */
	public static final String IMPACT_FRAMES_OK = "epic_impact_frames_ok";
	public static final String SILENT_IMPACT = "epic_silent_impact";
	public static final String WORLD_FX = "epic_world_fx";
	public static final String IMPACT_POWER = "epic_impact_power";

	// Phase 30 Film Spektakuler (DESIGN 30.2.1): the camera value above Penuh and the film rows.
	/** The stored camera value of Kamera Spektakuler ({@link #camera()} reads it as Penuh). */
	public static final String SPEKTAKULER = "spektakuler";
	/** True once the player confirmed the motion warning of Kamera Spektakuler. */
	public static final String SPEKTAKULER_OK = "epic_spektakuler_ok";
	public static final String LENS = "epic_lens";
	public static final String COSMIC = "epic_cosmic";
	public static final String BULLET = "epic_bullet";
	private static final String F = "kultivator.film.setting.";
	/** Opens the motion warning (set by the film module). */
	private static Runnable motionWarning;

	// Phase 10b Titah Langit (DESIGN 29.2.2, 29.4): the per-decree Film choice of the Titah Langit menu.
	/** Prefix of the per-decree film keys: {@code titah_film_pedang}, {@code _tiang}, {@code _bintang}, {@code _gunung}. */
	public static final String TITAH_FILM = "titah_film_";
	/** The four decrees in the order of the server's {@code Titah.Skill} (ordinal = net id − 1200). */
	public static final String[] TITAH_SKILLS = {"pedang", "tiang", "bintang", "gunung"};

	/**
	 * Film of one decree: Penuh (the film, default), Singkat (the 28.3 crane), Mati (no camera). The global camera Mati,
	 * "Kurangi gerak" and "Perjalanan langit: Mati" always win over it (29.4).
	 */
	public enum TitahFilm {
		PENUH, SINGKAT, MATI;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Perjalanan langit: Pertama (the first cast of each spec per session, default), Selalu, Mati. */
	public enum SkyRide {
		PERTAMA, SELALU, MATI;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Bingkai dampak: Lembut (default, one tint at most 0.35, no inversion), Penuh (after the warning), Mati. */
	public enum ImpactFrames {
		LEMBUT, PENUH, MATI;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** The steps of "Daya dampak" in cycling order (default first). */
	private static final float[] POWER_STEPS = {1.0F, 1.5F, 0.5F};
	private static final String D = "kultivator.dampak.setting.";
	/** Opens the photosensitivity warning (set by the client cinema module). */
	private static Runnable impactWarning;

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
		List<Row> rows = new java.util.ArrayList<>(List.of(
				new Row(CAMERA, true, EpicSettings::cameraText, EpicSettings::nextCamera, () -> tip("camera", cameraText())),
				// Phase 10 (28.11): Perjalanan langit, Bingkai dampak, Dampak sunyi, Puing & monumen, Daya dampak
				new Row(SKYRIDE, true, EpicSettings::skyRideText, () -> setSkyRide(SkyRide.values()[(skyRide().ordinal() + 1) % SkyRide.values().length]),
						() -> dampakTip("skyride", skyRideText())),
				new Row(IMPACT_FRAMES, true, EpicSettings::impactFramesText, EpicSettings::nextImpactFrames,
						() -> dampakTip("frames", impactFramesText())),
				new Row(SILENT_IMPACT, false, () -> Component.translatable(D + "silent"), () -> setFlag(SILENT_IMPACT, !flag(SILENT_IMPACT)),
						() -> dampakTip("silent", Component.translatable(D + "silent"))),
				new Row(WORLD_FX, false, () -> Component.translatable(D + "worldfx"), () -> setFlag(WORLD_FX, !flag(WORLD_FX)),
						() -> dampakTip("worldfx", Component.translatable(D + "worldfx"))),
				new Row(IMPACT_POWER, true, EpicSettings::impactPowerText, EpicSettings::nextImpactPower,
						() -> dampakTip("power", impactPowerText())),
				toggle(LETTERBOX, "letterbox"),
				toggle(CARDS, "cards"),
				toggle(MUSIC, "music"),
				toggle(SLOWMO, "slowmo"),
				toggle(GEOMETRY, "geometry"),
				toggle(SKY, "sky"),
				new Row(VFX, true, () -> Component.translatable(T + "vfx", vfxText()), EpicSettings::nextVfx,
						() -> tip("vfx", Component.translatable(T + "vfx", vfxText()))),
				toggle(RECORD, "record")));
		if (Language.getInstance().has(F + "lens")) {
			// Phase 30 (30.2.1): Lapisan lensa, Langit kosmik, Waktu beku, right after the camera row
			rows.add(1, new Row(LENS, true, EpicSettings::lensText, EpicSettings::nextLens, () -> filmTip("lens", lensText())));
			rows.add(2, new Row(COSMIC, false, () -> Component.translatable(F + "cosmic"), () -> setFlag(COSMIC, !flag(COSMIC)),
					() -> filmTip("cosmic", Component.translatable(F + "cosmic"))));
			rows.add(3, new Row(BULLET, false, () -> Component.translatable(F + "bullet"), () -> setFlag(BULLET, !flag(BULLET)),
					() -> filmTip("bullet", Component.translatable(F + "bullet"))));
		}
		return List.copyOf(rows);
	}

	// ================================================================ Phase 30 (DESIGN 30.2.1)

	/** The raw stored camera value ({@code penuh}, {@code singkat}, {@code spektakuler} or {@code mati}). */
	public static String cameraValue() {
		JsonElement value = EffectQuality.get(CAMERA);
		if (value != null && value.isJsonPrimitive()) {
			String id = value.getAsString();
			if (SPEKTAKULER.equals(id)) {
				return id;
			}
			for (Camera camera : Camera.values()) {
				if (camera.id().equals(id)) {
					return id;
				}
			}
		}
		return Camera.SINGKAT.id();
	}

	/**
	 * Kamera Spektakuler is in effect: stored, its motion warning confirmed, and "Kurangi gerak" off (which makes it
	 * unavailable, as 23.1.2). Every {@code == PENUH} check of 7b, 10 and 10b keeps working: {@link #camera()} reads it as
	 * Penuh.
	 */
	public static boolean spektakuler() {
		return SPEKTAKULER.equals(cameraValue()) && spektakulerConfirmed() && !EffectQuality.reduceMotion();
	}

	public static boolean spektakulerConfirmed() {
		JsonElement value = EffectQuality.get(SPEKTAKULER_OK);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
	}

	/** "Aktifkan" on the motion warning: remembers the confirmation and switches to Spektakuler. */
	public static void confirmSpektakuler() {
		EffectQuality.put(SPEKTAKULER_OK, new JsonPrimitive(true));
		EffectQuality.put(CAMERA, new JsonPrimitive(SPEKTAKULER));
	}

	/** Switches to Spektakuler when confirmed (true), else opens the motion warning (false). */
	public static boolean requestSpektakuler() {
		if (spektakulerConfirmed()) {
			EffectQuality.put(CAMERA, new JsonPrimitive(SPEKTAKULER));
			return true;
		}
		if (motionWarning != null) {
			motionWarning.run();
		}
		return false;
	}

	/** Set once by the film module: opens the motion warning (the first switch to Spektakuler). */
	public static void motionWarning(Runnable opener) {
		motionWarning = opener;
	}

	/** Singkat, Penuh, Spektakuler, Mati, Singkat...; "Kurangi gerak" skips Spektakuler. */
	private static void nextCamera() {
		switch (cameraValue()) {
			case "singkat" -> setCamera(Camera.PENUH);
			case "penuh" -> {
				if (EffectQuality.reduceMotion() || motionWarning == null && !spektakulerConfirmed()) {
					setCamera(Camera.MATI);
				} else if (!requestSpektakuler()) {
					// the warning decides: "Aktifkan" switches to Spektakuler, "Kembali ke Penuh" keeps Penuh
					setCamera(Camera.PENUH);
				}
			}
			case SPEKTAKULER -> setCamera(Camera.MATI);
			default -> setCamera(Camera.SINGKAT);
		}
	}

	private static Component cameraText() {
		Component value = SPEKTAKULER.equals(cameraValue()) && Language.getInstance().has(F + "camera.spektakuler")
				? Component.translatable(F + "camera.spektakuler") : Component.translatable(T + "camera." + camera().id());
		return Component.translatable(T + "camera", value);
	}

	/** Lapisan lensa: Nyala (default), Ringan (letterbox, vignette, smear, speed lines), Mati. */
	public static LensLayers.Setting lens() {
		JsonElement value = EffectQuality.get(LENS);
		if (value != null && value.isJsonPrimitive()) {
			String id = value.getAsString();
			for (LensLayers.Setting setting : LensLayers.Setting.values()) {
				if (setting.name().toLowerCase(Locale.ROOT).equals(id)) {
					return setting;
				}
			}
		}
		return LensLayers.Setting.NYALA;
	}

	public static void setLens(LensLayers.Setting setting) {
		EffectQuality.put(LENS, new JsonPrimitive(setting.name().toLowerCase(Locale.ROOT)));
	}

	private static void nextLens() {
		LensLayers.Setting[] all = LensLayers.Setting.values();
		setLens(all[(lens().ordinal() + 1) % all.length]);
	}

	private static Component lensText() {
		return Component.translatable(F + "lens", Component.translatable(F + "lens." + lens().name().toLowerCase(Locale.ROOT)));
	}

	/** Langit kosmik (default on). */
	public static boolean cosmic() {
		return flag(COSMIC);
	}

	/** Waktu beku (default on); "Gerak lambat" off also turns bullet time off. */
	public static boolean bullet() {
		return flag(BULLET) && slowmo();
	}

	private static List<Component> filmTip(String id, Component title) {
		return List.of(title, Component.translatable(F + id + ".tip").withStyle(ChatFormatting.GRAY));
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
			if (SPEKTAKULER.equals(id)) {
				// Phase 30: Spektakuler is Penuh plus the film; every Penuh check stays true for it
				return Camera.PENUH;
			}
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

	// ================================================================ Phase 10 (DESIGN 28.11)

	public static SkyRide skyRide() {
		JsonElement value = EffectQuality.get(SKYRIDE);
		if (value != null && value.isJsonPrimitive()) {
			String id = value.getAsString();
			for (SkyRide mode : SkyRide.values()) {
				if (mode.id().equals(id)) {
					return mode;
				}
			}
		}
		return SkyRide.PERTAMA;
	}

	public static void setSkyRide(SkyRide mode) {
		EffectQuality.put(SKYRIDE, new JsonPrimitive(mode.id()));
	}

	/** The stored choice; Penuh only counts once the warning was confirmed (else Lembut). */
	public static ImpactFrames impactFrames() {
		JsonElement value = EffectQuality.get(IMPACT_FRAMES);
		if (value != null && value.isJsonPrimitive()) {
			String id = value.getAsString();
			for (ImpactFrames mode : ImpactFrames.values()) {
				if (mode.id().equals(id)) {
					return mode == ImpactFrames.PENUH && !impactFramesConfirmed() ? ImpactFrames.LEMBUT : mode;
				}
			}
		}
		return ImpactFrames.LEMBUT;
	}

	/** Stores the choice; Penuh is stored only after the photosensitivity warning was confirmed. */
	public static void setImpactFrames(ImpactFrames mode) {
		if (mode == ImpactFrames.PENUH && !impactFramesConfirmed()) {
			return;
		}
		EffectQuality.put(IMPACT_FRAMES, new JsonPrimitive(mode.id()));
	}

	public static boolean impactFramesConfirmed() {
		JsonElement value = EffectQuality.get(IMPACT_FRAMES_OK);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
	}

	/** "Aktifkan" on the warning screen: remembers the confirmation and switches to Penuh. */
	public static void confirmImpactFrames() {
		EffectQuality.put(IMPACT_FRAMES_OK, new JsonPrimitive(true));
		EffectQuality.put(IMPACT_FRAMES, new JsonPrimitive(ImpactFrames.PENUH.id()));
	}

	/** Set once by the client cinema: opens the photosensitivity warning (the first switch to Penuh). */
	public static void impactWarning(Runnable opener) {
		impactWarning = opener;
	}

	/** Dampak sunyi: the climax lands silent and the boom follows by distance (default on). */
	public static boolean silentImpact() {
		return flag(SILENT_IMPACT);
	}

	/** Puing dan monumen: debris displays, lift cells and monuments (off = particles only; default on). */
	public static boolean worldFx() {
		return flag(WORLD_FX);
	}

	/** Daya dampak: 1.0 (default), 1.5 or 0.5; scales debris counts and speeds, ring radii and shake, never above the caps. */
	public static float impactPower() {
		JsonElement value = EffectQuality.get(IMPACT_POWER);
		if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
			float stored = value.getAsFloat();
			for (float step : POWER_STEPS) {
				if (Math.abs(step - stored) < 0.01F) {
					return step;
				}
			}
		}
		return 1.0F;
	}

	public static void setImpactPower(float power) {
		float best = 1.0F;
		for (float step : POWER_STEPS) {
			if (Math.abs(step - power) < Math.abs(best - power)) {
				best = step;
			}
		}
		EffectQuality.put(IMPACT_POWER, new JsonPrimitive(best));
	}

	// ================================================================ Phase 10b (DESIGN 29.2.2)

	/** The stored Film choice of decree {@code skill} (0 Pedang, 1 Tiang, 2 Bintang, 3 Gunung); Penuh by default. */
	public static TitahFilm titahFilm(int skill) {
		if (skill < 0 || skill >= TITAH_SKILLS.length) {
			return TitahFilm.PENUH;
		}
		JsonElement value = EffectQuality.get(TITAH_FILM + TITAH_SKILLS[skill]);
		if (value != null && value.isJsonPrimitive()) {
			String id = value.getAsString();
			for (TitahFilm mode : TitahFilm.values()) {
				if (mode.id().equals(id)) {
					return mode;
				}
			}
		}
		return TitahFilm.PENUH;
	}

	/** Stores the Film choice of decree {@code skill}. */
	public static void setTitahFilm(int skill, TitahFilm mode) {
		if (skill >= 0 && skill < TITAH_SKILLS.length) {
			EffectQuality.put(TITAH_FILM + TITAH_SKILLS[skill], new JsonPrimitive(mode.id()));
		}
	}

	private static Component skyRideText() {
		return Component.translatable(D + "skyride", Component.translatable(D + "skyride." + skyRide().id()));
	}

	private static Component impactFramesText() {
		return Component.translatable(D + "frames", Component.translatable(D + "frames." + impactFrames().id()));
	}

	private static Component impactPowerText() {
		return Component.translatable(D + "power", Math.round(impactPower() * 100.0F) + "%");
	}

	/** Lembut, Mati, Penuh, Lembut...; the first step to Penuh opens the photosensitivity warning instead. */
	private static void nextImpactFrames() {
		switch (impactFrames()) {
			case LEMBUT -> setImpactFrames(ImpactFrames.MATI);
			case MATI -> {
				if (impactFramesConfirmed()) {
					setImpactFrames(ImpactFrames.PENUH);
				} else {
					// "Batal" keeps Lembut (28.16 item 9); "Aktifkan" switches to Penuh
					setImpactFrames(ImpactFrames.LEMBUT);
					if (impactWarning != null) {
						impactWarning.run();
					}
				}
			}
			case PENUH -> setImpactFrames(ImpactFrames.LEMBUT);
		}
	}

	private static void nextImpactPower() {
		float now = impactPower();
		int index = 0;
		for (int i = 0; i < POWER_STEPS.length; i++) {
			if (POWER_STEPS[i] == now) {
				index = i;
			}
		}
		setImpactPower(POWER_STEPS[(index + 1) % POWER_STEPS.length]);
	}

	private static List<Component> dampakTip(String id, Component title) {
		return List.of(title, Component.translatable(D + id + ".tip").withStyle(ChatFormatting.GRAY));
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
		for (String key : new String[]{CAMERA, LETTERBOX, CARDS, MUSIC, SLOWMO, GEOMETRY, SKY, VFX, RECORD, PACK_GEOMETRY, SKYRIDE, IMPACT_FRAMES,
				SILENT_IMPACT, WORLD_FX, IMPACT_POWER, LENS, COSMIC, BULLET}) {
			EffectQuality.put(key, null);
		}
		for (String skill : TITAH_SKILLS) {
			EffectQuality.put(TITAH_FILM + skill, null);
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
