package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.dunia.tempat.tema.Tema;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Kota Iblis (DESIGN 38.T.7): the init of this theme package, called by {@code KotaTema.init()} (both physical sides;
 * everything registered here is server behaviour on common events, no client class). Registrations: anchors
 * ({@link IblisAnchors}), the Market Pact ({@link ZonaPerjanjian}), the Skull Gate ({@link Gerbang}), Arena Darah and its
 * betting ({@link ArenaDarah}, {@link Taruhan}), the captive pens ({@link KandangTawanan}, ransom only), Pasar Seribu
 * Bayangan ({@link PasarGelap}) and the black auction ({@link LelangGelap}), the forbidden tower ({@link MenaraTerlarang}),
 * Sarang Kontrak ({@link SarangKontrak}), the hunting hour ({@link JamBerburu}), the righteous hunters, bounties and
 * siege ({@link PemburuLurus}, {@link BuronanIblis}, {@link PenyerbuanLurus}), the Blood Feast ({@link PestaDarah}), the
 * story words of {@code tema_iblis_rantai} ({@link IblisCerita}) and the commands ({@link IblisCommands}). PvE only.
 * <p>
 * Tick budget (one END_SERVER_TICK listener, spread): mechanisms every 5 ticks, hunts and squads every 20, per-player
 * scans every 40 (players spread by id over the 40 ticks), the sky check every 100, the slow per-player checks every 600.
 * Nothing scans the world; every system walks its own small lists. All behaviour sleeps while the theme is switched off
 * ({@code TemaConfig.aktif(IBLIS)}); registrations always happen (K1).
 */
public final class Iblis {

	private static boolean initialised;

	/** From {@code KotaTema.init()} (both physical sides). Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		IblisAnchors.init();
		ZonaPerjanjian.init();
		ArenaDarah.init();
		Taruhan.init();
		KandangTawanan.init();
		PasarGelap.init();
		LelangGelap.init();
		MenaraTerlarang.init();
		SarangKontrak.init();
		JamBerburu.init();
		PemburuLurus.init();
		BuronanIblis.init();
		PenyerbuanLurus.init();
		PestaDarah.init();
		IblisCerita.init();
		IblisCommands.init();
		ServerTickEvents.END_SERVER_TICK.register(Iblis::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer p = handler.getPlayer();
			GulunganTerlarangItem.lepasTopeng(p, false);
			IblisLayar.lupakan(p.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	private static void clear() {
		IblisAnchors.clear();
		Gerbang.clear();
		ArenaDarah.clear();
		Taruhan.clear();
		KandangTawanan.clear();
		LelangGelap.clear();
		MenaraTerlarang.clear();
		SarangKontrak.clear();
		JamBerburu.clear();
		PemburuLurus.clear();
		BuronanIblis.clear();
		PenyerbuanLurus.clear();
		PestaDarah.clear();
		IblisCerita.clear();
		GulunganTerlarangItem.clear();
	}

	private static void tick(MinecraftServer server) {
		if (!TemaConfig.aktif(Tema.IBLIS)) {
			return;
		}
		long t = server.overworld().getGameTime();
		MenaraTerlarang.tickNoda(server);
		if (t % 5L == 0L) {
			Gerbang.tick(server);
			ArenaDarah.tick(server);
			MenaraTerlarang.tick(server);
		}
		if (t % 20L == 7L) {
			Taruhan.bayarTaruhan(server);
			JamBerburu.tick(server);
			PemburuLurus.tick(server);
			PestaDarah.tick(server);
			IblisCerita.tickBos(server);
			GulunganTerlarangItem.tick(server);
		}
		if (t % 40L == 13L) {
			KandangTawanan.tick(server);
			PenyerbuanLurus.tickPelarian(server);
		}
		if (t % 100L == 31L) {
			PestaDarah.ikutiLangit(server);
		}
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		for (ServerPlayer p : players) {
			int slot = Math.floorMod(p.getId(), 40);
			if (Math.floorMod(t, 40L) == slot) {
				pemain40(p);
			}
			if (Math.floorMod(t, 600L) == Math.floorMod(p.getId() * 7, 600)) {
				PemburuLurus.periksa(p);
				IblisCerita.tickPemain(p);
			}
		}
	}

	/** Every 40 ticks per player (spread): the town scans while inside a Kota Iblis, contracts, bounties, the mask hunt. */
	private static void pemain40(ServerPlayer p) {
		if (p.isSpectator()) {
			return;
		}
		TemaKota.Kota kota = IblisKota.di(p);
		if (kota != null) {
			Gerbang.pindai(p, kota);
			MenaraTerlarang.pindai(p, kota);
		}
		SarangKontrak.tickPemain(p);
		BuronanIblis.tickPemain(p);
		IblisCerita.tickTopeng(p);
	}

	private Iblis() {
	}
}
