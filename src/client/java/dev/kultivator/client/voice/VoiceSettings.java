package dev.kultivator.client.voice;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.kultivator.client.EffectQuality;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The voice settings (module V), stored as {@code voice_mode} and {@code voice_text} in {@code config/kultivator-client.json}
 * through {@link EffectQuality#get} / {@link EffectQuality#put}: "Suara bicara: Semua / Hanya Langit / Mati" and "Teks suara:
 * Nyala / Mati", plus the VOICEVOX credit row (its tooltip names every character, as the VOICEVOX terms require; a click
 * plays a sample line). The volume itself is the vanilla "Voice/Speech" slider. Also "Riuh kota" and "Obrolan warga"
 * (Kota yang Hidup), each 100/75/50/25 % or Mati, stored as {@code riuh_kota} / {@code obrolan_warga}.
 */
public final class VoiceSettings {

	/** Suara bicara: every voice (default), only the voice of Heaven, or none. */
	public enum Mode {
		SEMUA, LANGIT, MATI;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** One row of the "Suara" group: a jade coin ({@code cycle == false}, lit while {@code on}) or a cycling talisman. */
	public record Row(String key, boolean cycle, Supplier<Component> value, Runnable next, Supplier<List<Component>> tooltip, BooleanSupplier on) {
	}

	public static final String MODE = "voice_mode";
	public static final String TEXT = "voice_text";
	/** Kota yang Hidup (docs/progres/suara-kota.md): crowd ambience and townsfolk chatter, 0-100 % (0 = off). */
	public static final String RIUH = "riuh_kota";
	public static final String OBROLAN = "obrolan_warga";
	/** The steps the two cycling rows go through: loud to off, then back to full. */
	private static final int[] PERSEN = {100, 75, 50, 25, 0};
	private static final String T = "kultivator.voice.setting.";
	/** The VOICEVOX credit lines (keys {@code kultivator.voice.credits.0..N-1}). */
	private static final int CREDIT_LINES = 9;

	public static Mode mode() {
		JsonElement e = safeGet(MODE);
		if (e != null && e.isJsonPrimitive()) {
			String s = e.getAsString();
			for (Mode m : Mode.values()) {
				if (m.id().equals(s)) {
					return m;
				}
			}
		}
		return Mode.SEMUA;
	}

	public static void setMode(Mode mode) {
		EffectQuality.put(MODE, new JsonPrimitive(mode.id()));
	}

	public static boolean text() {
		JsonElement e = safeGet(TEXT);
		if (e != null && e.isJsonPrimitive()) {
			try {
				return e.getAsBoolean();
			} catch (RuntimeException ignored) {
				return true;
			}
		}
		return true;
	}

	public static void setText(boolean on) {
		EffectQuality.put(TEXT, new JsonPrimitive(on));
	}

	/** Crowd ambience volume, 0..1 (the vanilla "Ambient/Environment" slider applies on top). */
	public static float riuh() {
		return persen(RIUH) / 100.0F;
	}

	/** Townsfolk chatter volume, 0..1 (the vanilla "Voice/Speech" slider applies on top; "Suara bicara" other than Semua silences it). */
	public static float obrolan() {
		return mode() != Mode.SEMUA ? 0.0F : persen(OBROLAN) / 100.0F;
	}

	private static int persen(String key) {
		JsonElement e = safeGet(key);
		if (e != null && e.isJsonPrimitive()) {
			try {
				return Math.max(0, Math.min(100, e.getAsInt()));
			} catch (RuntimeException ignored) {
				return 100;
			}
		}
		return 100;
	}

	private static void langkah(String key) {
		int now = persen(key);
		int next = PERSEN[0];
		for (int i = 0; i < PERSEN.length; i++) {
			if (PERSEN[i] == now) {
				next = PERSEN[(i + 1) % PERSEN.length];
			}
		}
		EffectQuality.put(key, new JsonPrimitive(next));
	}

	private static Component nilai(String key) {
		int p = persen(key);
		return p == 0 ? Component.translatable(T + "mode.mati") : Component.translatable(T + "persen", p);
	}

	private static Row baris(String key, String lang) {
		return new Row(key, true, () -> Component.translatable(T + lang, nilai(key)), () -> langkah(key),
				() -> List.of(Component.translatable(T + lang, nilai(key)), Component.translatable(T + lang + ".tip").withStyle(ChatFormatting.GRAY)),
				() -> persen(key) > 0);
	}

	private static JsonElement safeGet(String key) {
		try {
			return EffectQuality.get(key);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** The heading key of the group on the Settings page. */
	public static String heading() {
		return T + "heading";
	}

	/** The rows, in order; empty while the texts are not in the language files yet (the page never shows raw keys). */
	public static List<Row> rows() {
		if (!Language.getInstance().has(T + "mode")) {
			return List.of();
		}
		List<Row> rows = new ArrayList<>();
		rows.add(new Row(MODE, true, () -> Component.translatable(T + "mode", Component.translatable(T + "mode." + mode().id())),
				() -> setMode(Mode.values()[(mode().ordinal() + 1) % Mode.values().length]),
				() -> List.of(Component.translatable(T + "mode", Component.translatable(T + "mode." + mode().id())),
						Component.translatable(T + "mode.tip").withStyle(ChatFormatting.GRAY)), () -> mode() != Mode.MATI));
		rows.add(new Row(TEXT, false, () -> Component.translatable(T + "text"), () -> setText(!text()),
				() -> List.of(Component.translatable(T + "text"), Component.translatable(T + "text.tip").withStyle(ChatFormatting.GRAY)),
				VoiceSettings::text));
		if (Language.getInstance().has(T + "riuh")) {
			rows.add(baris(RIUH, "riuh"));
			rows.add(baris(OBROLAN, "obrolan"));
		}
		rows.add(new Row("voice_credits", true, () -> Component.translatable(T + "credits"), VoiceClient::preview, VoiceSettings::credits,
				() -> true));
		return rows;
	}

	/** The credit tooltip: the VOICEVOX characters and the free vocal packs (also in docs/CREDITS.md and the README). */
	public static List<Component> credits() {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(T + "credits"));
		for (int i = 0; i < CREDIT_LINES; i++) {
			String key = "kultivator.voice.credits." + i;
			if (Language.getInstance().has(key)) {
				lines.add(Component.translatable(key).withStyle(i == 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
			}
		}
		lines.add(Component.translatable(T + "credits.tip").withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	private VoiceSettings() {
	}
}
