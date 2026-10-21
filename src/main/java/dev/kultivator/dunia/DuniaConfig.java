package dev.kultivator.dunia;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kultivator.Kultivator;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * {@code config/kultivator-dunia.json} (DESIGN 34.4.5, contract C34.S4): one Gson file with one object per section
 * ({@code umum}, {@code kota}, {@code reruntuhan}, {@code langit}, {@code naga}, {@code dimensi}, {@code kapal},
 * {@code peta}). A module asks for its section once from its init with a POJO class whose fields carry the defaults
 * (public fields with initialisers and a no-argument constructor); missing values get those defaults, the clamp keeps
 * the numbers in range, and the file is rewritten with the result (the {@code KultivatorConfig} rule). A broken file is
 * left alone (the admin may want to fix it) and the defaults run meanwhile.
 * <p>
 * {@link #reload()} ({@code /kultivasi uji jelajah muat}) reads the file again and copies the new values into the very
 * objects the modules already hold, so nobody needs to ask again; {@link #onReload} listeners run afterwards.
 */
public final class DuniaConfig {

	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("kultivator-dunia.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private record Section<T>(String name, Class<T> type, Supplier<T> defaults, UnaryOperator<T> clamp, T instance) {
	}

	private static final Map<String, Section<?>> SECTIONS = new LinkedHashMap<>();
	private static final List<Runnable> RELOAD = new ArrayList<>();
	private static JsonObject root;
	private static boolean readable = true;

	/**
	 * The module's section {@code name} as a {@code type} POJO. Calling it again with the same name returns the same
	 * object. {@code clamp} may change the object in place and return it.
	 */
	public static synchronized <T> T section(String name, Class<T> type, Supplier<T> defaults, UnaryOperator<T> clamp) {
		ensureLoaded();
		Section<?> known = SECTIONS.get(name);
		if (known != null) {
			if (!known.type().equals(type)) {
				throw new IllegalStateException("Dunia config section " + name + " asked with two types");
			}
			return type.cast(known.instance());
		}
		T value = read(name, type, defaults, clamp);
		SECTIONS.put(name, new Section<>(name, type, defaults, clamp, value));
		root.add(name, GSON.toJsonTree(value));
		save();
		return value;
	}

	/** Runs after every {@link #reload()} (a module re-reads derived numbers there). */
	public static synchronized void onReload(Runnable listener) {
		if (listener != null) {
			RELOAD.add(listener);
		}
	}

	/** Reads the file again into every known section; false when the file could not be read (old values stay). */
	public static boolean reload() {
		synchronized (DuniaConfig.class) {
			root = null;
			ensureLoaded();
			if (!readable) {
				return false;
			}
			for (Section<?> section : SECTIONS.values()) {
				refresh(section);
			}
			save();
		}
		for (Runnable listener : List.copyOf(RELOAD)) {
			try {
				listener.run();
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A Dunia config reload listener failed", exception);
			}
		}
		return true;
	}

	/** The config file (for the reload message). */
	public static Path file() {
		return FILE;
	}

	// ================================================================== internals

	private static <T> void refresh(Section<T> section) {
		T fresh = read(section.name(), section.type(), section.defaults(), section.clamp());
		copy(fresh, section.instance(), section.type());
		root.add(section.name(), GSON.toJsonTree(section.instance()));
	}

	private static <T> T read(String name, Class<T> type, Supplier<T> defaults, UnaryOperator<T> clamp) {
		T value = null;
		JsonElement element = root.get(name);
		if (element != null && element.isJsonObject()) {
			try {
				value = GSON.fromJson(element, type);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Dunia config section {} is broken, using its defaults", name, exception);
			}
		}
		if (value == null) {
			value = defaults.get();
		}
		try {
			T clamped = clamp == null ? value : clamp.apply(value);
			return clamped == null ? value : clamped;
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Dunia config section {} could not be clamped, using its defaults", name, exception);
			return defaults.get();
		}
	}

	private static <T> void copy(T from, T into, Class<T> type) {
		if (from == into) {
			return;
		}
		for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
			for (Field field : c.getDeclaredFields()) {
				int mods = field.getModifiers();
				if (Modifier.isStatic(mods) || Modifier.isFinal(mods) || Modifier.isTransient(mods)) {
					continue;
				}
				try {
					field.setAccessible(true);
					field.set(into, field.get(from));
				} catch (ReflectiveOperationException | RuntimeException exception) {
					Kultivator.LOGGER.warn("Dunia config field {}.{} not reloaded", type.getSimpleName(), field.getName());
				}
			}
		}
	}

	private static void ensureLoaded() {
		if (root != null) {
			return;
		}
		root = new JsonObject();
		readable = true;
		try {
			if (Files.exists(FILE)) {
				JsonElement parsed = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8));
				if (parsed != null && parsed.isJsonObject()) {
					root = parsed.getAsJsonObject();
				}
			}
		} catch (IOException | RuntimeException exception) {
			readable = false;
			Kultivator.LOGGER.warn("Could not read config/kultivator-dunia.json, using defaults", exception);
		}
	}

	private static void save() {
		if (!readable) {
			return;
		}
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException exception) {
			Kultivator.LOGGER.warn("Could not write config/kultivator-dunia.json", exception);
		}
	}

	private DuniaConfig() {
	}
}
