package dev.kultivator.client.sekte.langit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kultivator.Kultivator;
import dev.kultivator.client.duel.ui.DuelSettings;
import dev.kultivator.client.screen.ui.Brush;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The client switches of the sky-sect visuals (DESIGN 37.7.9, module 37V), in their own file
 * {@code config/kultivator-langit-client.json} so the main client file is never rewritten by this module. Every switch
 * defaults on; each only hides a layer on this client (the server never knows). {@link #TOGGLES} lists them in display
 * order with their lang keys ({@code kultivator.sekte_langit.visual.<id>} and {@code .<id>.tip}); {@link #rows()} gives
 * them to Pengaturan › Tampilan &amp; Efek (DESIGN 43.10.1; the old settings screen is {@code LangitVisualScreen.java.deactivation}).
 */
public final class LangitClientConfig {

	/** One switch: its JSON key / lang id. */
	public enum Toggle {
		LAUT_AWAN("lautAwan"),
		AIR_TERJUN("airTerjun"),
		SUNGAI_BINTANG("sungaiBintang"),
		HALO("halo"),
		BANGAU("bangau"),
		KELOPAK("kelopak"),
		LENTERA("lentera"),
		CUACA_KEBAL("cuacaKebal"),
		SUARA_AMBIEN("suaraAmbien");

		public final String key;

		Toggle(String key) {
			this.key = key;
		}

		public Component label() {
			return Component.translatable("kultivator.sekte_langit.visual." + key);
		}

		public Component tip() {
			return Component.translatable("kultivator.sekte_langit.visual." + key + ".tip");
		}
	}

	public static final List<Toggle> TOGGLES = List.of(Toggle.values());

	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("kultivator-langit-client.json");
	private static final boolean[] ON = new boolean[Toggle.values().length];

	static {
		java.util.Arrays.fill(ON, true);
	}

	public static boolean on(Toggle toggle) {
		return ON[toggle.ordinal()];
	}

	public static void set(Toggle toggle, boolean value) {
		ON[toggle.ordinal()] = value;
		save();
	}

	public static void flip(Toggle toggle) {
		set(toggle, !on(toggle));
	}

	/**
	 * The rows of these switches for Pengaturan › Tampilan &amp; Efek (DESIGN 43.10.1, pattern of {@code DuelSettings.rows}):
	 * a heading "Langit Sekte" and one jade coin per switch with its explanation as tooltip. Shown with or without a sky
	 * hall; the JSON file stays {@code kultivator-langit-client.json}.
	 */
	public static List<DuelSettings.Row> rows() {
		List<DuelSettings.Row> rows = new ArrayList<>();
		rows.add(new DuelSettings.Row(true, false, () -> Component.translatable("kultivator.gui43.m10.langit.pengaturan"), null, List::of, () -> false));
		for (Toggle toggle : TOGGLES) {
			rows.add(new DuelSettings.Row(false, false, toggle::label, () -> flip(toggle), () -> List.of(Brush.bold(toggle.label()), toggle.tip()),
					() -> on(toggle)));
		}
		return rows;
	}

	public static void load() {
		try {
			if (!Files.exists(FILE)) {
				return;
			}
			JsonObject json = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Toggle toggle : Toggle.values()) {
				JsonElement value = json.get(toggle.key);
				if (value != null && value.isJsonPrimitive()) {
					ON[toggle.ordinal()] = value.getAsBoolean();
				}
			}
		} catch (IOException | RuntimeException exception) {
			Kultivator.LOGGER.warn("Could not read {}, using the defaults", FILE, exception);
		}
	}

	private static void save() {
		JsonObject json = new JsonObject();
		for (Toggle toggle : Toggle.values()) {
			json.addProperty(toggle.key, ON[toggle.ordinal()]);
		}
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, json.toString(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			Kultivator.LOGGER.warn("Could not save {}", FILE, exception);
		}
	}

	private LangitClientConfig() {
	}
}
