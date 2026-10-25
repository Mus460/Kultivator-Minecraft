package dev.kultivator.dunia.kota42.ragam;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarApi;
import dev.kultivator.dunia.DuniaBudget;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runtime of 42S Sebaran (DESIGN 42.4A.1-42.4A.2, 42.4A.8), called once from {@code KotaHidup.init}. Registers:
 * <ul>
 *   <li>the zone rules reload ({@link Kapasitas}, {@code data/kultivator/tempat/ragam/zona.json});</li>
 *   <li>the 38NL hooks {@code TempatHuni.addPenyeimbang} ({@link Penyeimbang}) and {@code Pemanggil.addPemilihTubuh}
 *       ({@link PemilihTubuh});</li>
 *   <li>a server tick that takes one active place per tick: every {@value #SIKLUS} ticks the occupancy recount and the
 *       targets of the hour, every {@value #LINTAS} ticks the data pass (every resident's activity: the balancer's counts and
 *       the metrics samples of {@link MetrikRagam}); a new day folds the old one into the day's totals;</li>
 *   <li>the test subs {@code uji kota_hidup sebaran ...} and {@code ragam ...} ({@link RagamUji}).</li>
 * </ul>
 * The balancer and the body picker check {@code kotaWarga.sebaranAktif} at run time (off = the 38 choices); the metrics keep
 * measuring either way, so the two can be compared. 42S-R's daily plans, micro-breaks and turns register their own hooks
 * (schedule overrider order 300, {@code TubuhPenduduk.addSelingan}) and report to {@link MetrikRagam#selingan} /
 * {@link MetrikRagam#belokan}.
 */
public final class KotaRagam {

	/** Recount period per place (the 38 caller's cycle). */
	static final long SIKLUS = 20L;
	/** Data pass period per place (12 world minutes). */
	static final long LINTAS = 200L;

	private static boolean initialised;
	private static @Nullable MinecraftServer server;
	private static final Map<Long, Long> SIKLUS_T = new HashMap<>();
	private static final Map<Long, Long> LINTAS_T = new HashMap<>();
	private static final Map<Long, Long> HARI = new HashMap<>();
	private static int giliran;

	/** From {@code KotaHidup.init}. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		Kapasitas.register();
		TempatHuni.addPenyeimbang(Penyeimbang::hook);
		Pemanggil.addPemilihTubuh(PemilihTubuh.HOOK);
		// 42S-R: daily plans (overrider 300), activity pools, micro-breaks, turns and groups
		RencanaHarian.init();
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> lupakan());
		ServerTickEvents.END_SERVER_TICK.register(KotaRagam::tick);
		RagamUji.register();
	}

	private static void lupakan() {
		server = null;
		SIKLUS_T.clear();
		LINTAS_T.clear();
		HARI.clear();
		Penyeimbang.lupakanSemua();
		PetaZona.lupakanSemua();
		RagamUji.lupakan();
	}

	/** The running server, or null. */
	static @Nullable MinecraftServer server() {
		return server;
	}

	/** The overworld day number. */
	static long hari(MinecraftServer s) {
		return Math.floorDiv(s.overworld().getOverworldClockTime(), 24000L);
	}

	/** The overworld game time (0 without a server). */
	static long sekarang() {
		MinecraftServer s = server;
		return s == null ? 0L : s.overworld().getGameTime();
	}

	/** The level of an active place, or null. */
	static @Nullable ServerLevel level(TempatHuni huni) {
		MinecraftServer s = server;
		return s == null ? null : s.getLevel(huni.dimensi);
	}

	/** The hour of a place: its first resident's clock (night cities shift by 12, every resident by its own +-40 minutes). */
	static int jam(TempatHuni huni, long waktu) {
		List<Penduduk> all = huni.penduduk();
		if (all.isEmpty()) {
			return (int) ((Math.floorMod(waktu, 24000L) / 1000L + 6L) % 24L);
		}
		Penduduk p = all.getFirst();
		// the neutral clock of the place: the resident's own shift taken back out
		return Jadwal.saat(p, huni.jenis, waktu - (long) p.geserJadwal()).jam();
	}

	/** The event overriding the place now: an explicit override, else the capital's running event. */
	static @Nullable String acara(TempatHuni huni, long now) {
		String acara = HidupApi.timpaan(huni.situsId, now);
		if (acara == null && huni.jenis.equals("kota/kota_besar")) {
			try {
				acara = KotaBesarApi.acaraAktif(huni.situsId);
			} catch (RuntimeException e) {
				acara = null;
			}
		}
		return acara;
	}

	// ================================================================== the tick

	private static void tick(MinecraftServer s) {
		server = s;
		List<TempatHuni> active = Pemanggil.semua();
		long now = s.overworld().getGameTime();
		if (now % LINTAS == 0L) {
			lupakanTidakAktif(active);
		}
		RagamUji.tick(s, now);
		if (active.isEmpty()) {
			return;
		}
		giliran = Math.floorMod(giliran + 1, active.size());
		TempatHuni huni = active.get(giliran);
		long last = SIKLUS_T.getOrDefault(huni.situsId, Long.MIN_VALUE / 2);
		if (now - last < SIKLUS) {
			return;
		}
		SIKLUS_T.put(huni.situsId, now);
		ServerLevel level = s.getLevel(huni.dimensi);
		if (level == null) {
			return;
		}
		try {
			siklus(level, huni, now);
		} catch (RuntimeException e) {
			Kultivator.LOGGER.warn("Zone balance of place {} failed", huni.situsId, e);
		}
	}

	/** Forgets the state of places that are no longer active. */
	private static void lupakanTidakAktif(List<TempatHuni> active) {
		Set<Long> live = new HashSet<>();
		for (TempatHuni h : active) {
			live.add(h.situsId);
		}
		for (Long situs : List.copyOf(SIKLUS_T.keySet())) {
			if (!live.contains(situs)) {
				SIKLUS_T.remove(situs);
				LINTAS_T.remove(situs);
				Penyeimbang.lupakan(situs);
				PetaZona.lupakan(situs);
			}
		}
	}

	/** One cycle of one place: event, data pass when due, recount and targets. */
	static void siklus(ServerLevel level, TempatHuni huni, long now) {
		PetaZona peta = PetaZona.untuk(level, huni);
		if (peta.jumlah() == 0) {
			return;
		}
		Penyeimbang.Hunian h = Penyeimbang.hunian(huni.situsId);
		long waktu = level.getServer().overworld().getOverworldClockTime();
		int jam = jam(huni, waktu);
		String acara = acara(huni, now);
		h.acara = acara;
		h.hari = Math.floorDiv(waktu, 24000L);
		h.acaraZona = -1;
		if (acara != null && acara.startsWith("tonton:")) {
			int[] xz = Jadwal.xz(acara.substring("tonton:".length()));
			if (xz != null) {
				h.acaraZona = peta.zonaDi(new net.minecraft.core.BlockPos(xz[0], huni.pusat().getY(), xz[1]));
			}
		}
		Penyeimbang.siapkan(h, huni, peta, jam, now);
		long last = LINTAS_T.getOrDefault(huni.situsId, Long.MIN_VALUE / 2);
		if (now - last >= LINTAS) {
			LINTAS_T.put(huni.situsId, now);
			lintasan(level, huni, peta, h, waktu, now);
		}
		Penyeimbang.hitung(h, peta, jam, now);
	}

	/** The data pass: every resident's activity (balancer counts + metrics), the bodies of the place (metrics). */
	private static void lintasan(ServerLevel level, TempatHuni huni, PetaZona peta, Penyeimbang.Hunian h, long waktu, long now) {
		MinecraftServer s = level.getServer();
		long hari = Math.floorDiv(waktu, 24000L);
		Long before = HARI.put(huni.situsId, hari);
		if (before != null && before < hari) {
			MetrikRagam.tutupHari(s, huni.situsId, hari - 1L);
		}
		Set<Integer> present = new HashSet<>();
		for (Penduduk p : huni.penduduk()) {
			present.add(p.id());
			Kegiatan k = huni.kegiatan(p, waktu, now);
			boolean umum = Kapasitas.umum(k.tujuan());
			Penyeimbang.catatData(h, p.id(), umum, umum ? -1 : Penyeimbang.zonaTetap(huni, peta, p, k));
			MetrikRagam.sampelWarga(s, huni.situsId, hari, p, k);
		}
		for (Integer id : List.copyOf(h.bebas)) {
			if (!present.contains(id)) {
				Penyeimbang.lupakanWarga(h, id);
			}
		}
		for (Integer id : List.copyOf(h.zonaTetap.keySet())) {
			if (!present.contains(id)) {
				Penyeimbang.lupakanWarga(h, id);
			}
		}
		List<KultivatorNpc> bodies = tubuh(huni);
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && huni.kotak.inflatedBy(192).isInside(player.blockPosition())) {
				players.add(player);
			}
		}
		MetrikRagam.sampelTempat(s, huni.situsId, hari, peta, h, bodies, players, KotaConfig.warga().jarakMin, bodies.size() * LINTAS / 1200.0D);
	}

	/** The live resident bodies of a place. */
	static List<KultivatorNpc> tubuh(TempatHuni huni) {
		List<KultivatorNpc> out = new ArrayList<>();
		for (Entity e : DuniaBudget.tracked(Pemanggil.BUDGET)) {
			if (e instanceof KultivatorNpc npc && !npc.isRemoved() && npc.situsPenduduk() == huni.situsId && npc.pendudukId() >= 0) {
				out.add(npc);
			}
		}
		return out;
	}

	private KotaRagam() {
	}
}
