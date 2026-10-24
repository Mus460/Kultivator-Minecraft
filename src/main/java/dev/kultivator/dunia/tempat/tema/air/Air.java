package dev.kultivator.dunia.tempat.tema.air;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Kota Air (DESIGN 38.T.5): the init of this theme package, called by {@code KotaTema.init()}. Registers, without
 * touching any 38TK inti file: the anchor kinds ({@link AirAnchors}, {@code TemaAnchors.daftar}), the screens
 * {@code tema_sutra} ({@link RumahSutra}), {@code tema_teh} ({@link PaviliunTeh}), {@code tema_bait}
 * ({@link PasanganBait}) through {@code TemaLayar.daftar}, the calendar events {@code lentera_air}
 * ({@link LenteraAir}), {@code perahu_naga} ({@link LombaPerahuNaga}), {@code tikus_air} ({@link TikusAir}) through
 * {@code TemaKalender.daftar}, the quest goals, effects, conditions and services ({@link AirCerita}), the test word
 * {@code perahu} ({@link AirCommands}); and runs the boats ({@link Perahu}) every tick. Shared state in
 * {@code KotaTemaData.bagian("air")} ({@link AirKota}).
 * <p>
 * Behaviour runs only while {@code kotaTema.aktif} and the air worldgen switch are on ({@link AirKota#aktif()}); every
 * registration happens anyway. No block is ever changed here: boats, looms, lanterns and the water gate are entities or
 * client pictures.
 */
public final class Air {

	private static boolean initialised;

	private record Nanti(long tick, Runnable kerja) {
	}

	private static final List<Nanti> NANTI = new ArrayList<>();

	/** From {@code KotaTema.init()} (both physical sides). Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		AirAnchors.init();
		RumahSutra.init();
		PaviliunTeh.init();
		PasanganBait.init();
		LenteraAir.init();
		LombaPerahuNaga.init();
		TikusAir.init();
		AirCerita.init();
		AirCommands.init();
		ServerTickEvents.END_SERVER_TICK.register(Air::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> keluar(handler.getPlayer()));
	}

	private static void reset() {
		synchronized (NANTI) {
			NANTI.clear();
		}
		AirKota.reset();
		AirAnchors.reset();
		Perahu.reset();
		PaviliunTeh.reset();
		PasanganBait.reset();
		LenteraAir.reset();
		LombaPerahuNaga.reset();
		TikusAir.reset();
		AirCerita.reset();
	}

	private static void keluar(ServerPlayer player) {
		Perahu.keluar(player);
		AirAnchors.lupakanPemain(player.getUUID());
		PaviliunTeh.keluar(player.getUUID());
		PasanganBait.keluar(player.getUUID());
		AirLayar.lupakan(player.getUUID());
	}

	/** Runs {@code kerja} on the server thread {@code ticks} ticks from now (scene steps; never saved). */
	static void nanti(MinecraftServer server, int ticks, Runnable kerja) {
		synchronized (NANTI) {
			NANTI.add(new Nanti(server.overworld().getGameTime() + Math.max(1, ticks), kerja));
		}
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		List<Runnable> due = null;
		synchronized (NANTI) {
			for (Iterator<Nanti> it = NANTI.iterator(); it.hasNext(); ) {
				Nanti n = it.next();
				if (n.tick <= now) {
					if (due == null) {
						due = new ArrayList<>();
					}
					due.add(n.kerja);
					it.remove();
				}
			}
		}
		if (due != null) {
			for (Runnable r : due) {
				try {
					r.run();
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.warn("Kota Air scene step failed", exception);
				}
			}
		}
		if (!AirKota.aktif() || AirKota.semua().isEmpty()) {
			return;
		}
		try {
			Perahu.tick(server);
			LombaPerahuNaga.tick(level);
			TikusAir.gerak(level);
			if (now % 20L == 7L) {
				TikusAir.tick(level);
				PaviliunTeh.detik(server);
				AirCerita.tick(level);
				arwahMalam(level);
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kota Air tick failed", exception);
		}
	}

	/** Stage {@code arwah} of the chain: the lost souls rise at night for that player even without the festival. */
	private static void arwahMalam(ServerLevel level) {
		long t = AirKota.jam(level);
		if (t < 13000L || t >= 23000L || !dev.kultivator.dunia.tempat.tema.TemaConfig.inti().bahaya) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (!LenteraAir.tahapArwah(p)) {
				continue;
			}
			AirKota.Hidup kota = AirKota.di(p);
			if (kota != null && !LenteraAir.berjalan(kota.situs)) {
				LenteraAir.arwah(level, kota);
			}
		}
	}

	private Air() {
	}
}
