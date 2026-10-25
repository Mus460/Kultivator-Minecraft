package dev.kultivator.client.duel.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The client settings of the sword duel (DESIGN 35.8.3), stored in {@code config/kultivator-client.json} through
 * {@link EffectQuality#get} / {@link EffectQuality#put} (enum values as their lower-case id, switches as booleans), and
 * their rows in the hub's Pengaturan groups (C35 U: {@code SettingsRows} asks {@link #rows(int)} per group):
 * <ul>
 * <li>Tampilan &amp; Efek: Percikan duel (Penuh / Ringan / Mati), Jejak bilah (Otomatis / Selalu / Mati), Hit-stop duel,
 * Gerak lambat duel;</li>
 * <li>Kamera &amp; Film: Kamera duel (Mati / Bahu / Penonton), Intro duel;</li>
 * <li>Suara: Kiai (Mati / Pria / Wanita), Sorak penonton;</li>
 * <li>HUD: Bar postur di atas kepala;</li>
 * <li>Kontrol: Masukan duel (Otomatis / Klasik / Ikuti Better Combat / Vanilla), Tangkis di luar duel, Kunci lawan,
 * Animasi Player Animation Library.</li>
 * </ul>
 * The visual and animation modules read the same keys ({@link #CAMERA}, {@link #SPARKS}, {@link #TRAILS}, ...) through
 * the accessors here. A change of {@link #KIAI}, {@link #INPUT}, {@link #INTRO} or {@link #PARRY_OUTSIDE} is sent to the
 * server as {@code DuelPrefs} (by {@code DuelClient}). The duel page has no quick settings of its own any more: its "Atur
 * di Pengaturan" link opens these rows (DESIGN 43.10.1, G49).
 */
public final class DuelSettings {

	public static final String INPUT = "duel_input";
	public static final String PARRY_OUTSIDE = "duel_parry_outside";
	public static final String CAMERA = "duel_camera";
	public static final String INTRO = "duel_intro";
	public static final String SLOWMO = "duel_slowmo";
	public static final String HITSTOP = "duel_hitstop";
	public static final String SPARKS = "duel_sparks";
	public static final String TRAILS = "duel_trails";
	public static final String KIAI = "duel_kiai";
	public static final String HUD_WORLD = "duel_hud_world";
	public static final String CROWD = "duel_crowd";
	public static final String LOCKON = "duel_lockon";
	public static final String PAL = "duel_pal";

	/** The hub's group ids ({@code HubNav.GROUP_*}). */
	public static final int GROUP_TAMPILAN = 0;
	public static final int GROUP_FILM = 1;
	public static final int GROUP_SUARA = 2;
	public static final int GROUP_HUD = 3;
	public static final int GROUP_KONTROL = 4;

	private static final String T = "kultivator.duel.setting.";

	/** Masukan duel (35.3.2). OTOMATIS = IKUTI_BC with Better Combat, else KLASIK inside a formal duel and VANILLA outside. */
	public enum InputMode {
		OTOMATIS, KLASIK, IKUTI_BC, VANILLA;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Kamera duel (35.4.10). */
	public enum Camera {
		MATI, BAHU, PENONTON;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Percikan duel: the spark count factor of the visual module, its one source (audit M4 R5: Ringan 0.5, as it always drew). */
	public enum Sparks {
		PENUH(1.0F), RINGAN(0.5F), MATI(0.0F);

		private final float factor;

		Sparks(float factor) {
			this.factor = factor;
		}

		public float factor() {
			return factor;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Jejak bilah (35.4.4): OTOMATIS = always in a formal duel, outside only for qi swings. */
	public enum Trails {
		OTOMATIS, SELALU, MATI;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** Kiai (35.6.2): the player's own shout, played by the server to everyone near. */
	public enum Kiai {
		MATI, PRIA, WANITA;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * One row for the hub. {@code heading} rows only carry a title ({@code label}); {@code cycle} rows are talismans that
	 * step to the next value; the others are jade coins lit while {@code on}.
	 */
	public record Row(boolean heading, boolean cycle, Supplier<Component> label, @Nullable Runnable next, Supplier<List<Component>> tooltip,
					  BooleanSupplier on) {
	}

	/** Called after a setting the server must know changed (set by {@code DuelClient}). */
	private static Runnable prefsChanged = () -> {
	};

	// ================================================================== accessors

	public static InputMode input() {
		return pick(INPUT, InputMode.values(), InputMode.OTOMATIS);
	}

	public static void setInput(InputMode mode) {
		store(INPUT, mode.id());
		prefsChanged.run();
	}

	public static boolean parryOutside() {
		return flag(PARRY_OUTSIDE, true);
	}

	public static Camera camera() {
		return pick(CAMERA, Camera.values(), Camera.MATI);
	}

	public static void setCamera(Camera camera) {
		store(CAMERA, camera.id());
	}

	/** Steps the duel camera: Mati → Bahu → (Penonton when {@code spectator}) → Mati. */
	public static Camera nextCamera(boolean spectator) {
		Camera next = switch (camera()) {
			case MATI -> Camera.BAHU;
			case BAHU -> spectator ? Camera.PENONTON : Camera.MATI;
			case PENONTON -> Camera.MATI;
		};
		setCamera(next);
		return next;
	}

	public static boolean intro() {
		return flag(INTRO, true);
	}

	/** Duel slow motion: its own switch, the 7b "Gerak lambat" switch and Reduce Motion. */
	public static boolean slowmo() {
		return flag(SLOWMO, true) && EpicSettings.slowmo() && !EffectQuality.reduceMotion();
	}

	/** Duel hit-stop: its own switch, the 7b "Gerak lambat" switch and Reduce Motion (35.4.6). */
	public static boolean hitstop() {
		return flag(HITSTOP, true) && EpicSettings.slowmo() && !EffectQuality.reduceMotion();
	}

	public static Sparks sparks() {
		return pick(SPARKS, Sparks.values(), Sparks.PENUH);
	}

	public static Trails trails() {
		return pick(TRAILS, Trails.values(), Trails.OTOMATIS);
	}

	public static Kiai kiai() {
		return pick(KIAI, Kiai.values(), Kiai.MATI);
	}

	/** False (default): posture bars on the HUD; true: bars over the fighters' heads. */
	public static boolean hudWorld() {
		return flag(HUD_WORLD, false);
	}

	public static boolean crowd() {
		return flag(CROWD, true);
	}

	public static boolean lockon() {
		return flag(LOCKON, false);
	}

	public static boolean pal() {
		return flag(PAL, false);
	}

	/** Set once by {@code DuelClient.init}: sends {@code DuelPrefs}. */
	public static void onPrefsChanged(Runnable action) {
		prefsChanged = action == null ? () -> {
		} : action;
	}

	// ================================================================== rows

	/** The rows of hub group {@code group} (empty for an unknown group). */
	public static List<Row> rows(int group) {
		List<Row> rows = new ArrayList<>();
		switch (group) {
			case GROUP_TAMPILAN -> {
				rows.add(heading("heading"));
				rows.add(cycle("sparks", () -> sparks().id(), () -> store(SPARKS, next(sparks(), Sparks.values()).id())));
				rows.add(cycle("trails", () -> trails().id(), () -> store(TRAILS, next(trails(), Trails.values()).id())));
				rows.add(coin("hitstop", HITSTOP, true, false));
				rows.add(coin("slowmo", SLOWMO, true, false));
			}
			case GROUP_FILM -> {
				rows.add(heading("heading"));
				rows.add(cycle("camera", () -> camera().id(), () -> nextCamera(true)));
				rows.add(coin("intro", INTRO, true, true));
			}
			case GROUP_SUARA -> {
				rows.add(heading("heading"));
				rows.add(cycle("kiai", () -> kiai().id(), () -> {
					store(KIAI, next(kiai(), Kiai.values()).id());
					prefsChanged.run();
				}));
				rows.add(coin("crowd", CROWD, true, false));
			}
			case GROUP_HUD -> {
				rows.add(heading("heading"));
				rows.add(coin("hud_world", HUD_WORLD, false, false));
			}
			case GROUP_KONTROL -> {
				rows.add(heading("heading"));
				rows.add(cycle("input", () -> input().id(), () -> setInput(next(input(), InputMode.values()))));
				rows.add(coin("parry_outside", PARRY_OUTSIDE, true, true));
				rows.add(coin("lockon", LOCKON, false, false));
				rows.add(coin("pal", PAL, false, false));
			}
			default -> {
			}
		}
		return rows;
	}

	private static Row heading(String key) {
		return new Row(true, false, () -> Component.translatable(T + key), null, List::of, () -> false);
	}

	private static Row cycle(String key, Supplier<String> value, Runnable next) {
		Supplier<Component> label = () -> Component.translatable(T + key, Component.translatable(T + key + "." + value.get()));
		return new Row(false, true, label, next, () -> List.of(label.get(),
				Component.translatable(T + key + ".tip").withStyle(ChatFormatting.GRAY),
				Component.translatable(T + key + "." + value.get() + ".tip").withStyle(ChatFormatting.DARK_AQUA)), () -> false);
	}

	private static Row coin(String key, String stored, boolean fallback, boolean server) {
		BooleanSupplier on = () -> flag(stored, fallback);
		return new Row(false, false, () -> Component.translatable(T + key), () -> {
			store(stored, !flag(stored, fallback));
			if (server) {
				prefsChanged.run();
			}
		}, () -> List.of(Component.translatable(T + key), Component.translatable(T + key + ".tip").withStyle(ChatFormatting.GRAY),
				Component.translatable(on.getAsBoolean() ? T + "on" : T + "off").withStyle(on.getAsBoolean() ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_GRAY)),
				on);
	}

	// ================================================================== storage

	private static <E extends Enum<E>> E next(E current, E[] values) {
		return values[(current.ordinal() + 1) % values.length];
	}

	private static <E extends Enum<E>> E pick(String key, E[] values, E fallback) {
		JsonElement value = safeGet(key);
		if (value != null && value.isJsonPrimitive()) {
			String id = value.getAsString();
			for (E e : values) {
				if (e.name().toLowerCase(Locale.ROOT).equals(id)) {
					return e;
				}
			}
		}
		return fallback;
	}

	private static boolean flag(String key, boolean fallback) {
		JsonElement value = safeGet(key);
		if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
			return value.getAsBoolean();
		}
		return fallback;
	}

	private static void store(String key, String value) {
		EffectQuality.put(key, new JsonPrimitive(value));
	}

	private static void store(String key, boolean value) {
		EffectQuality.put(key, new JsonPrimitive(value));
	}

	private static @Nullable JsonElement safeGet(String key) {
		try {
			return EffectQuality.get(key);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private DuelSettings() {
	}
}
