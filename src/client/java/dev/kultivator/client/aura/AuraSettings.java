package dev.kultivator.client.aura;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.kultivator.client.EffectQuality;
import org.jetbrains.annotations.Nullable;

/**
 * The three settings rows of the Aura Kultivasi (DESIGN 31.5), kept in {@code config/kultivator-client.json} through
 * {@link EffectQuality#get}/{@link EffectQuality#put} under {@code aura_mode} ({@code penuh} / {@code sederhana} /
 * {@code mati}), {@code aura_others} and {@code aura_hum}. Read once, then cached; every change is saved at once.
 */
public final class AuraSettings {

	/** "Aura Kultivasi": Penuh (the full aura of 31.4), Sederhana (decal, two glows, the identity quad, particles), Mati. */
	public enum Mode {
		PENUH("penuh"), SEDERHANA("sederhana"), MATI("mati");

		private final String id;

		Mode(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		static Mode byId(@Nullable String id) {
			for (Mode mode : values()) {
				if (mode.id.equals(id)) {
					return mode;
				}
			}
			return PENUH;
		}
	}

	private static final String MODE_KEY = "aura_mode";
	private static final String OTHERS_KEY = "aura_others";
	private static final String HUM_KEY = "aura_hum";

	private static boolean loaded;
	private static Mode mode = Mode.PENUH;
	private static boolean others = true;
	private static boolean hum = true;

	public static Mode mode() {
		load();
		return mode;
	}

	/** Penuh → Sederhana → Mati → Penuh. */
	public static void cycleMode() {
		load();
		mode = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
		EffectQuality.put(MODE_KEY, new JsonPrimitive(mode.id()));
	}

	/** "Aura orang lain": other players' auras and their disciples' (your own disciples always show). */
	public static boolean others() {
		load();
		return others;
	}

	public static void setOthers(boolean on) {
		load();
		others = on;
		EffectQuality.put(OTHERS_KEY, new JsonPrimitive(on));
	}

	/** "Dengung aura": the quiet realm hums and chimes. */
	public static boolean hum() {
		load();
		return hum;
	}

	public static void setHum(boolean on) {
		load();
		hum = on;
		EffectQuality.put(HUM_KEY, new JsonPrimitive(on));
	}

	/** Forgets the cache (the next read takes the file's values again). */
	static void reload() {
		loaded = false;
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		mode = Mode.byId(string(EffectQuality.get(MODE_KEY)));
		others = bool(EffectQuality.get(OTHERS_KEY), true);
		hum = bool(EffectQuality.get(HUM_KEY), true);
	}

	private static @Nullable String string(@Nullable JsonElement element) {
		try {
			return element == null || element.isJsonNull() ? null : element.getAsString();
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private static boolean bool(@Nullable JsonElement element, boolean fallback) {
		try {
			return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private AuraSettings() {
	}
}
