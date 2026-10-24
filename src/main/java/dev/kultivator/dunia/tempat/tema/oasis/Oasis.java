package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Kota Oasis Gurun (DESIGN 38.T.4): the init of this theme package, called by {@code KotaTema.init()} (both physical
 * sides; nothing here touches a client class). Registers the oasis anchors ({@link OasisAnchors}, module
 * {@code tema_oasis}) and the ruin anchors ({@link Reruntuhan}, through {@code TemaAnchors.daftar}), the calendar events
 * {@code badai_pasir}, {@code kafilah_agung} and {@code tari_putar}, the screens {@code tema_tawar}, {@code tema_kafilah}
 * and {@code tema_mural}, the story hooks of {@link OasisCerita}, the Mata Gurun meditation site ({@link KuilAir}), the
 * test commands ({@code uji tema unta}) and one server tick (every 20 ticks, offset 11: storms, sand finds, caravans,
 * mural billboards). Shared state through {@code KotaTemaData.bagian("oasis")}; never edits 38TK inti files.
 * <p>
 * Every behaviour stops while {@code kotaTema.aktif} or {@code temaOasis.worldgen} is off (switches only gate behaviour;
 * every registry entry stays registered). New worlds only: no migration.
 */
public final class Oasis {

	private static boolean initialised;

	/** From {@code KotaTema.init()} (both physical sides). Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		OasisAnchors.init();
		Reruntuhan.init();
		BadaiPasir.init();
		KuilAir.init();
		Mural.init();
		Tawar.init();
		KafilahUnta.init();
		TariPutar.init();
		OasisCerita.init();
		OasisCommands.init();
		ServerTickEvents.END_SERVER_TICK.register(Oasis::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> lupakan(handler.getPlayer().getUUID()));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 11 || !OasisKota.aktif()) {
			return;
		}
		try {
			BadaiPasir.tick(server);
			BadaiPasir.tickTemuan(server);
			KafilahUnta.tick(server);
			UntaLari.tick(server);
			Mural.tick(server);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kota Oasis tick failed", exception);
		}
	}

	private static void reset() {
		OasisKota.reset();
		OasisAnchors.reset();
		Reruntuhan.reset();
		BadaiPasir.reset();
		KuilAir.reset();
		Mural.reset();
		Tawar.reset();
		KafilahUnta.reset();
		UntaLari.reset();
		TariPutar.reset();
		Bea.reset();
	}

	private static void lupakan(UUID id) {
		OasisLayar.lupakan(id);
		Mural.lupakan(id);
		Tawar.lupakan(id);
		TariPutar.lupakan(id);
		KafilahUnta.lupakan(id);
		UntaLari.lupakan(id);
	}

	private Oasis() {
	}
}
