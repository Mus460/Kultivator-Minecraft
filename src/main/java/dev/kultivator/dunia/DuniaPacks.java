package dev.kultivator.dunia;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The world generation switches of DESIGN 34.4.5 (contract C34.S4): every module's overworld (and Alam Abadi) structure
 * sets live in its built-in data pack {@code kultivator:dunia_<module>} ({@code resourcepacks/dunia_<module>} in the mod
 * jar), registered {@code DEFAULT_ENABLED} only when the module's {@code worldgen} switch is on. Fabric's built-in pack
 * source adds a new pack to an existing world by itself (verified: {@code BuiltinModPackSource.shouldAddAutomatically}
 * is true and {@code MinecraftServer.configurePackRepository} adds such a pack unless the world disabled it), so old
 * worlds get the structures in their new chunks. On server start the spine only logs a pack that is available but not
 * selected (an admin disabled it with {@code /datapack disable}); it never forces it on.
 */
public final class DuniaPacks {

	private static final Map<String, Boolean> REQUESTED = new LinkedHashMap<>();

	static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(DuniaPacks::check);
		kompatBioma();
	}

	/**
	 * {@code kultivator:kompat_bioma} (tools/gen_kompat_bioma.py): adds the Terralith biomes to the mod's biome tags as
	 * optional entries, so towns, capitals, villages, sect gates and caves also stand in Terralith worlds. Always on;
	 * without Terralith the optional entries resolve to nothing.
	 */
	private static void kompatBioma() {
		Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(Kultivator.MOD_ID);
		if (mod.isEmpty()) {
			return;
		}
		try {
			if (!ResourceLoader.registerBuiltinPack(Kultivator.id("kompat_bioma"), mod.get(),
					Component.translatable("kultivator.dunia.pack.kompat_bioma"), PackActivationType.DEFAULT_ENABLED)) {
				Kultivator.LOGGER.warn("Biome compatibility pack kompat_bioma is missing from the mod");
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Biome compatibility pack kompat_bioma could not be registered", exception);
		}
	}

	/**
	 * Registers {@code kultivator:dunia_<module>} when {@code on} (once per module; later calls are ignored). Returns true
	 * when the pack is registered.
	 */
	public static boolean worldgen(String module, boolean on) {
		if (REQUESTED.containsKey(module)) {
			return REQUESTED.get(module);
		}
		boolean registered = false;
		if (on) {
			Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(Kultivator.MOD_ID);
			if (mod.isPresent()) {
				try {
					registered = ResourceLoader.registerBuiltinPack(packId(module), mod.get(),
							Component.translatable("kultivator.dunia.pack." + module), PackActivationType.DEFAULT_ENABLED);
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.warn("Dunia world generation pack {} could not be registered", module, exception);
				}
			}
			if (!registered) {
				Kultivator.LOGGER.warn("Dunia world generation pack dunia_{} is missing from the mod; its structures will not generate", module);
			}
		}
		REQUESTED.put(module, registered);
		return registered;
	}

	/** {@code kultivator:dunia_<module>}. */
	public static Identifier packId(String module) {
		return Kultivator.id("dunia_" + module);
	}

	/** True when the module's pack was registered this session. */
	public static boolean registered(String module) {
		return REQUESTED.getOrDefault(module, false);
	}

	/** True when the module's pack is registered and selected in this world. */
	public static boolean active(MinecraftServer server, String module) {
		return registered(module) && server.getPackRepository().getSelectedIds().contains(packId(module).toString());
	}

	private static void check(MinecraftServer server) {
		PackRepository repository = server.getPackRepository();
		for (Map.Entry<String, Boolean> entry : REQUESTED.entrySet()) {
			if (!entry.getValue()) {
				continue;
			}
			String id = packId(entry.getKey()).toString();
			if (repository.isAvailable(id) && !repository.getSelectedIds().contains(id)) {
				Kultivator.LOGGER.info("Dunia pack {} is available but disabled in this world; enable it with /datapack enable \"{}\"", id, id);
			}
		}
	}

	private DuniaPacks() {
	}
}
