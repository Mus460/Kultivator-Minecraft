package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.Kultivator;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteConfig;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.util.SibukMurid;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * Module 39F, Perseteruan antar-sekte (DESIGN 39.F): the entry of package {@code sekte.mandiri.seteru}, called by
 * {@code sekte.mandiri.Mandiri} (schedule matrix row 11). Disciples quarrel and feud with the named disciples of the NPC
 * sects of T12's single list ({@link Roster}); the feuds climb a branching ladder ({@link Tangga}) from a sideways look to
 * the sect masters' own acts through D ({@code sekte.diplomasi.DiplomasiMandiri}); the scenes play when a player is near
 * ({@link Adegan}, {@link DuelSeteru}, {@link Tawuran}, {@link Patroli}); the master has eight ways to step in
 * ({@link CampurTangan}); friendship and forbidden love cross the sect line ({@link AsmaraLintas}).
 * <p>
 * {@link #registerContent} runs always (K1: payload types); {@link #init} only while {@code umum.mandiri} and
 * {@code mandiri.aktif} are on, and the section switch {@code mandiri_seteru.aktif} gates every step. No death, no war
 * while {@code bolehPerang} is false, no block is ever changed. Budget (39.F.8): no tick per character; encounters only
 * when 39C's journey stages fall due or a bus event happens; at most 4 bodies (AI every 4 ticks), one brawl with at most
 * two duel sessions; heat decays once per world day per hall; scenes waiting to be shown are checked every 40 ticks.
 */
public final class Seteru {

	private static boolean content;
	private static boolean initialised;
	private static double msRata;

	/** Registry content of 39F (always, both physical sides). */
	public static void registerContent() {
		if (content) {
			return;
		}
		content = true;
		SeteruPayloads.daftarJenis();
	}

	/** Runtime behaviour of 39F (server listeners, ticks, executors, commands); only while the module is on. */
	public static void init() {
		registerContent();
		if (initialised) {
			return;
		}
		initialised = true;
		SeteruConfig.load();
		SekteConfig.onReload(() -> SeteruConfig.load());
		MandiriApi.addPertemuanProvider(Pertemuan.PENYEDIA);
		PelaksanaSeteru.daftar();
		SeteruPayloads.daftarPenerima();
		SeteruCommands.init();
		Insiden.init();
		SibukMurid.register(SeteruIds.SIBUK, Adegan::sibuk);
		Halls.addReturnHandler(MandiriIds.AWAY_SETERU, CampurTangan::kembali);
		ServerTickEvents.END_SERVER_TICK.register(Seteru::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> bersihkan());
	}

	/** The module runs: Mandiri on and the section switch on. */
	static boolean aktif() {
		return MandiriApi.aktif() && SeteruConfig.get().aktif;
	}

	private static void bersihkan() {
		TubuhSeteru.bersihkan();
		Adegan.bersihkan();
		DuelSeteru.bersihkan();
		Tawuran.bersihkan();
		Patroli.bersihkan();
	}

	private static void tick(MinecraftServer server) {
		if (!aktif()) {
			return;
		}
		long start = System.nanoTime();
		try {
			Adegan.tick(server);
			TubuhSeteru.tick(server);
			DuelSeteru.tick(server);
			DuelSeteru.tickTetua(server);
			Tawuran.tick(server);
			Patroli.tick(server);
			int t = server.getTickCount();
			if (t % 40 == 21) {
				adegan(server);
				Patroli.tampilkan(server);
			}
			if (t % 100 == 51) {
				Antrean.sapu(server);
			}
			if (t % 200 == 77) {
				long today = Ingatan.today(server);
				SeteruData data = SeteruData.get(server);
				for (Map.Entry<UUID, SeteruData.Aula> e : new ArrayList<>(data.aula.entrySet())) {
					Tangga.hariBaru(server, e.getKey(), e.getValue(), today);
				}
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Feud (39F) tick failed", exception);
		}
		double ms = (System.nanoTime() - start) / 1.0E6D;
		msRata = msRata * 0.98D + ms * 0.02D;
	}

	/** Every 40 ticks: the duels and brawls waiting to be shown live or rolled. */
	private static void adegan(MinecraftServer server) {
		SeteruData data = SeteruData.get(server);
		for (SeteruData.Aula aula : data.aula.values()) {
			for (Perseteruan f : new ArrayList<>(aula.seteru)) {
				if (f.adegan.isEmpty() || !SeteruIds.AKTIF.equals(f.status)) {
					continue;
				}
				switch (f.adegan.getStringOr("jenis", "")) {
					case DuelSeteru.JENIS -> DuelSeteru.periksa(server, f);
					case Tawuran.JENIS -> Tawuran.periksa(server, f);
					default -> f.adegan = new net.minecraft.nbt.CompoundTag();
				}
			}
		}
	}

	/** The average cost of 39F's server tick (ms) for {@code uji ... anggaran} (budget 39.F.8: < 0.4 ms). */
	static double msRataRata() {
		return msRata;
	}

	private Seteru() {
	}
}
