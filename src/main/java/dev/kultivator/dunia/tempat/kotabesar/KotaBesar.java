package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaPacks;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.util.Advancements;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Init and heartbeat of 38KS, the capital (DESIGN 38.B): config section {@code kotaBesar} (pushed into the capital plan),
 * the built-in world generation pack {@code kultivator:dunia_kotabesar} (new chunks only), the anchor handler of module
 * {@code kotabesar}, the client picture payload, the civil services (curfew {@link JamMalam}, law {@link Hukum},
 * {@link Teleport}, {@link Kuil}, {@link Perpustakaan}, {@link MenaraPengamat}, {@link PetaKota}), the calendar
 * {@link KotaBesarKalender} with its events, and the test commands. Called once from {@code Tempat.init()}.
 * <p>
 * Budget: one pass every 20 ticks over the loaded capitals and the overworld players (no world scan); the drum and bell
 * beats and the teleport channels run every tick but only while something is queued. Registry entries are registered
 * always; {@code kotaBesar.aktif} and the per-system switches only gate behaviour (38.0 rule 17).
 */
public final class KotaBesar {

	private static boolean initialised;
	private static @Nullable MinecraftServer server;
	private static long tick;
	/** Capital advancements already granted this session ("uuid|name"), so the check stays a set lookup. */
	private static final java.util.Set<String> DIBERI = new java.util.HashSet<>();

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		KotaBesarEntities.init();
		KotaBesarConfig.terapkan();
		DuniaPacks.worldgen("kotabesar", KotaBesarConfig.get().worldgen);
		DuniaAnchors.register("kotabesar", KotaBesarAnchors.HANDLER);
		KotaBesarPayload.register();
		Hukum.init();
		GelombangBinatang.init();
		Teleport.init();
		Kuil.init();
		PetaKota.init();
		KotaBesarKalender.init();
		KotaBesarCommands.init();

		ServerLifecycleEvents.SERVER_STARTED.register(started -> {
			server = started;
			tick = 0L;
			KotaBesarConfig.terapkan();
			for (long town : Towns.known(started)) {
				if (Towns.isKotaBesar(started, town)) {
					Kandidat k = KotaBesarKota.dariSitus(started.overworld(), town);
					if (k != null) {
						Teleport.daftarRute(started, k);
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
			server = null;
			lupakan();
		});
		ServerTickEvents.END_SERVER_TICK.register(KotaBesar::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> keluar(handler.getPlayer().getUUID()));
	}

	/** The running server (null outside a session). */
	static @Nullable MinecraftServer server() {
		return server;
	}

	private static void lupakan() {
		KotaBesarAnchors.lupakan();
		JamMalam.lupakan();
		Hukum.lupakan();
		GelombangBinatang.lupakan();
		Teleport.lupakan();
		TurnamenAgung.lupakan();
		Perpustakaan.lupakan();
		MenaraPengamat.lupakan();
		KotaBesarTampil.lupakan();
		DIBERI.clear();
	}

	private static void keluar(UUID id) {
		Hukum.keluar(id);
		Teleport.keluar(id);
		KotaBesarTampil.keluar(id);
	}

	private static void tick(MinecraftServer current) {
		tick++;
		JamMalam.ketukan(current);
		Teleport.tick(current);
		if (!KotaBesarConfig.hidup()) {
			return;
		}
		long phase = tick % 20L;
		if (phase == 0L) {
			JamMalam.tick(current);
		} else if (phase == 5L) {
			Hukum.tick(current);
		} else if (phase == 10L) {
			GelombangBinatang.tick(current);
			KotaBesarKalender.tick(current);
		} else if (phase == 15L) {
			Teleport.cakram(current);
			Perpustakaan.tick(current, tick - 15L);
			MenaraPengamat.tick(current);
			Istana.tick(current);
			ServerLevel overworld = current.overworld();
			for (ServerPlayer p : overworld.players()) {
				KotaBesarTampil.masuk(overworld, p);
				pangkat(p);
			}
		}
		if (tick % 200L == 7L) {
			Kuil.tick(current);
			KotaBesarKalender.gerhana(current);
		}
		if (tick % 600L == 13L) {
			for (KotaBesarAnchors.KotaHidup kota : KotaBesarAnchors.semua()) {
				KotaBesarTampil.poster(current, kota.town);
			}
		}
	}

	/** The capital advancements of rank and standing (granted once; the advancement util ignores repeats). */
	private static void pangkat(ServerPlayer p) {
		Kandidat k = KotaBesarKota.di(p.level(), p.blockPosition());
		if (k == null || !KotaBesarKota.dalamTembok(k, p.blockPosition())) {
			return;
		}
		long town = KotaBesarKota.situs(k);
		beri(p, "tempat/ibu_kota");
		int rank = KotaBesarApi.pangkatNamaBaik(p, town);
		if (rank >= dev.kultivator.dunia.tempat.ui.LembarKunci.PANGKAT_TERHORMAT) {
			beri(p, "tempat/warga_terhormat");
		}
		if (rank >= dev.kultivator.dunia.tempat.ui.LembarKunci.PANGKAT_PAHLAWAN) {
			beri(p, "tempat/pahlawan_kota");
		}
		if (Hukum.buronan(p, town)) {
			beri(p, "tempat/buronan");
		}
	}

	private static void beri(ServerPlayer p, String name) {
		if (DIBERI.add(p.getUUID() + "|" + name)) {
			Advancements.grant(p, name);
		}
	}

	private KotaBesar() {
	}
}
