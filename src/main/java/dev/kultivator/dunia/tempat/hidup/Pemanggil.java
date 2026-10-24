package dev.kultivator.dunia.tempat.hidup;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Dao;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.api.SpiritWeather;
import dev.kultivator.dunia.kota.KotaEntities;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatConfig;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarApi;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The body caller (DESIGN 38.N.1 {@code Pemanggil.isi}, budgets 38.N.7). Every 40 ticks per player the places within
 * reach are found ({@link TempatApi#dekat}); one active place per server tick is then cycled in turn:
 * <ol>
 *   <li>weather, sky and event of the place, the titik refreshed while chunks load;</li>
 *   <li>every resident's activity ({@link Jadwal#kegiatan}) and goal ({@link TempatHuni#tujuan}), cached per hour;</li>
 *   <li>bodies for the residents whose goal is nearest a player (within 48 blocks, at most three new bodies per cycle)
 *       up to the place's cap, {@code tubuhGlobal} and {@code perPemain}; none while the server is overloaded;</li>
 *   <li>bodies released when 56 blocks from every player or gone indoors unseen;</li>
 *   <li>the far crowd (48-128 blocks) and the occupied stands sent to each player ({@link PendudukSnapshot}).</li>
 * </ol>
 * Bodies are {@code noSave} {@link KultivatorNpc}s bound to their resident ({@link KultivatorNpc#setPenduduk}); they appear
 * at their goal, or step out of the nearest door when a player is watching the goal.
 */
public final class Pemanggil {

	/** {@link DuniaBudget} kind of resident bodies. */
	public static final String BUDGET = "npc_hidup";

	static final double CARI = 64.0D;
	static final double PANGGIL = 128.0D;
	static final double LEPAS = 160.0D;
	static final double JAUH = 192.0D;
	static final int SIKLUS = 20;
	/** Pathfinding starts allowed per server tick for all resident bodies (38.N.7). */
	static final int JALUR_PER_TICK = 8;
	/** Places without a player near for this long are dropped. */
	static final long LUPA = 1200L;
	static final int BARU_PER_SIKLUS = 48;

	static final Map<Long, TempatHuni> AKTIF = new LinkedHashMap<>();
	private static final Map<Long, Long> SIKLUS_T = new HashMap<>();
	private static final Map<UUID, Map<Long, Integer>> TERKIRIM = new HashMap<>();
	private static int jalurTick;
	private static boolean ready;

	/** Cached goal of one resident for one activity and hour. */
	record Posisi(String kunci, @Nullable TempatHuni.Tujuan tujuan) {
	}

	private static final Map<Long, Map<Integer, Posisi>> POSISI = new HashMap<>();
	/** {@code uji hidup jam}: place -> {time of day shown, until game time} (the world clock never changes). */
	private static final Map<Long, long[]> PRATINJAU_JAM = new HashMap<>();
	/** {@code uji hidup cuaca}: place -> weather shown until a game time (the weather never changes). */
	private static final Map<Long, Jadwal.Cuaca> PRATINJAU_CUACA = new HashMap<>();
	private static final Map<Long, Long> PRATINJAU_CUACA_T = new HashMap<>();
	/** Residents whose goal changed in the last cycle (walking in the far crowd). */
	private static final Map<Long, java.util.Set<Integer>> BERJALAN = new HashMap<>();

	static void init() {
		if (ready) {
			return;
		}
		ready = true;
		ServerTickEvents.END_SERVER_TICK.register(Pemanggil::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> lupakan());
	}

	static void lupakan() {
		PRATINJAU_JAM.clear();
		PRATINJAU_CUACA.clear();
		PRATINJAU_CUACA_T.clear();
		AKTIF.clear();
		SIKLUS_T.clear();
		TERKIRIM.clear();
		POSISI.clear();
		BERJALAN.clear();
	}

	/** True when one more path search may start this tick (spread over ticks, 38.N.7). */
	static boolean bolehJalur() {
		if (jalurTick >= JALUR_PER_TICK) {
			return false;
		}
		jalurTick++;
		return true;
	}

	/** The active place {@code situs}, or null. */
	public static @Nullable TempatHuni huni(long situs) {
		return AKTIF.get(situs);
	}

	/** Every active place (commands). */
	public static List<TempatHuni> semua() {
		return List.copyOf(AKTIF.values());
	}

	private static boolean aktif() {
		return HidupConfig.get().aktif && TempatConfig.get().aktif;
	}

	// ================================================================== the tick

	private static void tick(MinecraftServer server) {
		jalurTick = 0;
		if (!aktif()) {
			if (!AKTIF.isEmpty()) {
				for (TempatHuni h : AKTIF.values()) {
					lepasSemua(h);
				}
				lupakan();
			}
			return;
		}
		long now = server.overworld().getGameTime();
		DuniaBudget.setCap(BUDGET, HidupConfig.get().tubuhGlobal);
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		for (int i = 0; i < players.size(); i++) {
			ServerPlayer player = players.get(i);
			if ((server.getTickCount() + i * 7) % SIKLUS == 0 && !player.isSpectator()) {
				temukan(player, now);
			}
		}
		if (AKTIF.isEmpty()) {
			return;
		}
		// one place per tick, each at most once per cycle
		for (Iterator<TempatHuni> it = AKTIF.values().iterator(); it.hasNext(); ) {
			TempatHuni huni = it.next();
			long last = SIKLUS_T.getOrDefault(huni.situsId, Long.MIN_VALUE);
			if (now - last < SIKLUS) {
				continue;
			}
			SIKLUS_T.put(huni.situsId, now);
			ServerLevel level = server.getLevel(huni.dimensi);
			if (level == null || now - huni.aktifT > LUPA) {
				lepasSemua(huni);
				it.remove();
				SIKLUS_T.remove(huni.situsId);
				POSISI.remove(huni.situsId);
				BERJALAN.remove(huni.situsId);
				continue;
			}
			try {
				siklus(level, huni, now);
			} catch (RuntimeException e) {
				Kultivator.LOGGER.warn("NPC life cycle of place {} failed", huni.situsId, e);
			}
			break;
		}
	}

	/** Finds the inhabited places near {@code player} and marks them active. */
	private static void temukan(ServerPlayer player, long now) {
		ServerLevel level = player.level();
		for (TempatInfo info : TempatApi.dekat(level, player.blockPosition(), (int) JAUH, "")) {
			if (!PendudukGen.dihuni(info.jenis())) {
				continue;
			}
			if (jarak(info.kotak(), player.position()) > JAUH) {
				continue;
			}
			TempatHuni huni = AKTIF.get(info.situsId());
			if (huni == null) {
				List<Penduduk> residents = PendudukGen.untuk(info.situsId(), info.jenis(), info.kekuatan(), level.getSeed());
				if (residents.isEmpty()) {
					continue;
				}
				huni = new TempatHuni(info, level.dimension(), residents);
				huni.terapkan(PendudukData.get(level.getServer()));
				AKTIF.put(info.situsId(), huni);
			}
			huni.aktifT = now;
		}
	}

	/** Horizontal distance from {@code at} to the box (0 inside). */
	static double jarak(BoundingBox box, Vec3 at) {
		double dx = Math.max(0.0D, Math.max(box.minX() - at.x, at.x - (box.maxX() + 1)));
		double dz = Math.max(0.0D, Math.max(box.minZ() - at.z, at.z - (box.maxZ() + 1)));
		return Math.sqrt(dx * dx + dz * dz);
	}

	// ================================================================== one cycle of one place

	private record Calon(Penduduk p, Kegiatan k, TempatHuni.Tujuan tujuan, double jarak) {
	}

	private static void siklus(ServerLevel level, TempatHuni huni, long now) {
		huni.segarkan(level, now, false);
		Long shown = PRATINJAU_CUACA_T.get(huni.situsId);
		if (shown != null && now < shown) {
			huni.cuaca = PRATINJAU_CUACA.get(huni.situsId);
		} else {
			PRATINJAU_CUACA.remove(huni.situsId);
			PRATINJAU_CUACA_T.remove(huni.situsId);
			huni.cuaca = cuaca(level, huni);
		}
		if (now % 200L < SIKLUS) {
			huni.terapkan(PendudukData.get(level.getServer()));
		}
		long waktu = waktu(huni, level);
		String acara = acara(huni, now);
		PendudukData data = PendudukData.get(level.getServer());
		List<ServerPlayer> dekat = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && jarak(huni.kotak, player.position()) <= JAUH) {
				dekat.add(player);
			}
		}
		Map<Integer, Posisi> cache = POSISI.computeIfAbsent(huni.situsId, k -> new HashMap<>());
		java.util.Set<Integer> berjalan = new java.util.HashSet<>();
		List<Calon> calon = new ArrayList<>();
		Map<ServerPlayer, List<PendudukSnapshot.Entri>> jauh = new HashMap<>();
		BlockPos anchor = huni.pusat();
		huni.jumlahTubuh();
		for (Penduduk p : huni.penduduk()) {
			KultivatorNpc body = huni.tubuh.get(p.id());
			if (data.absen(huni.situsId, p.id(), now)) {
				if (body != null) {
					lepas(huni, body);
				}
				huni.keadaan.remove(p.id());
				continue;
			}
			int state = data.keadaan(huni.situsId, p.id(), p.tamu() ? 0 : p.kelompok(), now);
			if (state == PendudukData.SEHAT) {
				huni.keadaan.remove(p.id());
			} else {
				huni.keadaan.put(p.id(), state);
			}
			Kegiatan k = huni.kegiatan(p, waktu, now);
			int jam = Jadwal.saat(p, huni.jenis, waktu).jam();
			String key = k.kunci() + "|" + k.tujuan() + "|" + jam / 2;
			Posisi pos = cache.get(p.id());
			if (pos == null || !pos.kunci().equals(key)) {
				if (pos != null) {
					berjalan.add(p.id());
				}
				// indoor activities (sleep, meals at home) stand at home too: every house has its people (user request)
				pos = new Posisi(key, huni.tujuan(level, p, k, jam, false));
				cache.put(p.id(), pos);
			}
			if (body != null) {
				ServerPlayer near = terdekat(dekat, body.position());
				boolean jauhSemua = near == null || near.position().distanceTo(body.position()) > LEPAS;
				if (jauhSemua) {
					lepas(huni, body);
				}
				continue;
			}
			if (pos.tujuan() == null || dekat.isEmpty()) {
				continue;
			}
			BlockPos goal = pos.tujuan().pos();
			ServerPlayer near = terdekat(dekat, Vec3.atBottomCenterOf(goal));
			double d = near == null ? Double.MAX_VALUE : near.position().distanceTo(Vec3.atBottomCenterOf(goal));
			if (d <= PANGGIL) {
				calon.add(new Calon(p, k, pos.tujuan(), d));
			} else if (d <= JAUH && HidupConfig.get().impostor) {
				for (ServerPlayer player : dekat) {
					double pd = player.position().distanceTo(Vec3.atBottomCenterOf(goal));
					if (pd > PANGGIL && pd <= JAUH) {
						jauh.computeIfAbsent(player, x -> new ArrayList<>()).add(entri(p, k, pos.tujuan(), anchor, berjalan.contains(p.id()), pd));
					}
				}
			}
		}
		BERJALAN.put(huni.situsId, berjalan);
		panggil(level, huni, calon, now, acara);
		kirim(huni, dekat, jauh, anchor, acara, waktu);
	}

	private static @Nullable ServerPlayer terdekat(List<ServerPlayer> players, Vec3 at) {
		ServerPlayer best = null;
		double bestD = Double.MAX_VALUE;
		for (ServerPlayer p : players) {
			double d = p.position().distanceToSqr(at);
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	/** The overworld clock as the place's residents live it (a {@code uji hidup jam} preview shifts it, nothing else). */
	static long waktu(TempatHuni huni, ServerLevel level) {
		long real = level.getServer().overworld().getOverworldClockTime();
		long[] preview = PRATINJAU_JAM.get(huni.situsId);
		if (preview == null) {
			return real;
		}
		if (level.getGameTime() >= preview[1]) {
			PRATINJAU_JAM.remove(huni.situsId);
			return real;
		}
		return real - Math.floorMod(real, 24000L) + preview[0];
	}

	/** Shows the residents of {@code situs} living hour {@code jam} (0-23) until game time {@code sampai}. */
	static void pratinjauJam(long situs, int jam, long sampai) {
		long tod = Math.floorMod((jam - 6) * 1000L, 24000L);
		PRATINJAU_JAM.put(situs, new long[]{tod, sampai});
		POSISI.remove(situs);
		SIKLUS_T.remove(situs);
	}

	/** Shows the residents of {@code situs} reacting to {@code cuaca} until game time {@code sampai}. */
	static void pratinjauCuaca(long situs, Jadwal.Cuaca cuaca, long sampai) {
		PRATINJAU_CUACA.put(situs, cuaca);
		PRATINJAU_CUACA_T.put(situs, sampai);
		POSISI.remove(situs);
		SIKLUS_T.remove(situs);
	}

	/** The event overriding the place's schedule: an explicit override, else the capital's running event (38KS). */
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

	/** Weather and sky at the place (read only). */
	static Jadwal.Cuaca cuaca(ServerLevel level, TempatHuni huni) {
		BlockPos centre = huni.pusat();
		if (!level.hasChunk(centre.getX() >> 4, centre.getZ() >> 4)) {
			return Jadwal.Cuaca.CERAH;
		}
		Biome biome = level.getBiome(centre).value();
		boolean dingin = biome.coldEnoughToSnow(centre, level.getSeaLevel());
		boolean hujan = level.isRaining() && biome.getPrecipitationAt(centre, level.getSeaLevel()) == Biome.Precipitation.RAIN;
		boolean salju = level.isRaining() && biome.getPrecipitationAt(centre, level.getSeaLevel()) == Biome.Precipitation.SNOW;
		boolean badai = hujan && level.isThundering();
		SpiritWeather qi = DuniaApi.sky().weatherAt(level, centre);
		boolean kabut = qi == SpiritWeather.DENSE_MIST || qi == SpiritWeather.PURPLE_DAWN;
		return new Jadwal.Cuaca(hujan, badai, salju, dingin, kabut, DuniaApi.sky().active(level));
	}

	// ================================================================== bodies

	private static void panggil(ServerLevel level, TempatHuni huni, List<Calon> calon, long now, @Nullable String acara) {
		if (calon.isEmpty() || !dev.kultivator.dunia.DuniaSpine.enabled()) {
			return;
		}
		boolean berat = DuniaBudget.overloaded(level.getServer());
		HidupConfig cfg = HidupConfig.get();
		int cap = cfg.tubuhUntuk(huni.jenis);
		// workplace keepers (stationary roles: cook, healer, smith, innkeeper, banker...) first and outside the caps, so a
		// village's healer or a compound's alchemist is never crowded out by strollers; the rest by distance, capped
		calon.sort(Comparator.comparing((Calon c) -> !c.p().peran().stationary).thenComparingDouble(Calon::jarak));
		int dibuat = 0;
		for (Calon c : calon) {
			boolean staf = c.p().peran().stationary;
			if (dibuat >= BARU_PER_SIKLUS || !staf && (berat || huni.tubuh.size() >= cap || !DuniaBudget.mayAdd(level, BUDGET))) {
				return;
			}
			Vec3 at = Vec3.atBottomCenterOf(c.tujuan().pos());
			if (!staf && !perPemainBoleh(level, at, cfg.perPemain)) {
				continue;
			}
			if (!c.p().peran().petarung() && HidupApi.bahayaDi(level, c.tujuan().pos()) != null) {
				// civilians stay indoors while the danger lasts
				continue;
			}
			KultivatorNpc body = buat(level, huni, c, now);
			if (body != null) {
				dibuat++;
			}
		}
	}

	/** Fewer than {@code max} resident bodies within 96 blocks of every player near {@code at}. */
	private static boolean perPemainBoleh(ServerLevel level, Vec3 at, int max) {
		double r2 = DuniaBudget.PER_PLAYER_RANGE * DuniaBudget.PER_PLAYER_RANGE;
		List<net.minecraft.world.entity.Entity> bodies = DuniaBudget.tracked(BUDGET);
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(at) > r2) {
				continue;
			}
			int n = 0;
			for (net.minecraft.world.entity.Entity e : bodies) {
				if (e.level() == level && e.position().distanceToSqr(player.position()) <= r2) {
					n++;
				}
			}
			if (n >= max) {
				return false;
			}
		}
		return true;
	}

	private static @Nullable KultivatorNpc buat(ServerLevel level, TempatHuni huni, Calon c, long now) {
		BlockPos goal = c.tujuan().pos();
		if (!level.hasChunk(goal.getX() >> 4, goal.getZ() >> 4)) {
			return null;
		}
		// someone watching the goal sees the resident step out of the nearest door instead of popping up there
		BlockPos spawn = goal;
		ServerPlayer watcher = null;
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceTo(Vec3.atBottomCenterOf(goal)) < 20.0D) {
				watcher = player;
				break;
			}
		}
		if (watcher != null) {
			Titik door = null;
			double best = 16.0D * 16.0D;
			for (Titik t : huni.titik("pintu")) {
				double d = t.pos().distSqr(goal);
				if (d < best && watcher.position().distanceTo(Vec3.atBottomCenterOf(t.pos())) > 6.0D) {
					best = d;
					door = t;
				}
			}
			if (door != null) {
				spawn = door.pos();
			}
		}
		KultivatorNpc npc = KotaEntities.NPC.create(level, EntitySpawnReason.EVENT);
		if (npc == null) {
			return null;
		}
		Penduduk p = c.p();
		NpcRole role = p.peran();
		float yaw = c.tujuan().hadap().toYRot();
		npc.snapTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, yaw, 0.0F);
		npc.setup(role, p.look(), HidupApi.realmFor(role, p.look()), Math.floorMod(p.look() >>> 5, 4),
				Dao.values()[Math.floorMod(p.look() >>> 11, Dao.COUNT)], p.suara(), 0, KotaNames.plate(p.look(), role));
		npc.setHome(goal);
		npc.setTown(huni.situsId);
		npc.setAnchorSite(huni.situsId);
		npc.setLonely(64.0D, 400);
		npc.setPenduduk(huni.situsId, p.id());
		npc.setFlag(KultivatorNpc.FLAG_GHOST, role == NpcRole.SISA_JIWA);
		if (role.kelompok == NpcRole.Kelompok.PENJAGA) {
			npc.setFightsMonsters(true);
		}
		if (!level.addFreshEntity(npc)) {
			return null;
		}
		DuniaBudget.track(BUDGET, npc);
		huni.tubuh.put(p.id(), npc);
		TubuhPenduduk.mulai(level, huni, npc, p, c.k(), c.tujuan(), now);
		return npc;
	}

	/** Releases a body (it goes home unseen: no puff, the data stays). */
	static void lepas(TempatHuni huni, KultivatorNpc body) {
		int id = body.pendudukId();
		huni.tubuh.remove(id);
		huni.lepas(id);
		if (!body.isRemoved()) {
			body.discard();
		}
	}

	private static void lepasSemua(TempatHuni huni) {
		for (KultivatorNpc body : List.copyOf(huni.tubuh.values())) {
			lepas(huni, body);
		}
	}

	// ================================================================== the far crowd

	private static PendudukSnapshot.Entri entri(Penduduk p, Kegiatan k, TempatHuni.Tujuan t, BlockPos anchor, boolean jalan, double d) {
		BlockPos pos = t.pos();
		int klip = jalan ? PendudukSnapshot.KLIP_JALAN : poseJauh(k.klip());
		byte yaw = (byte) Math.round(t.hadap().toYRot() / 360.0F * 256.0F);
		return new PendudukSnapshot.Entri((short) ((pos.getX() - anchor.getX()) * 2), (short) ((pos.getY() - anchor.getY()) * 2),
				(short) ((pos.getZ() - anchor.getZ()) * 2), yaw, p.look(), (short) p.peran().ordinal(), (byte) klip);
	}

	/** The far pose family of a clip (sitting, working, cheering or standing). */
	static int poseJauh(int klip) {
		return switch (klip) {
			case GerakId.DUDUK_KURSI, GerakId.DUDUK_LANTAI, GerakId.CATUR,
					GerakId.CERITA, GerakId.MINUM_TEH, GerakId.NAPAS_MEDITASI,
					GerakId.MEMBACA, GerakId.TIDUR_DUDUK, GerakId.TENUN,
					GerakId.SEMEDI_DARAH, GerakId.ASAH_PISAU, GerakId.SIRKULASI_QI,
					GerakId.ASAH_PEDANG, GerakId.MAIN_GUQIN, GerakId.PERIKSA_NADI,
					GerakId.TIDUR_MEJA, GerakId.BERSANDAR -> PendudukSnapshot.KLIP_DUDUK;
			case GerakId.SORAK_ARENA, GerakId.TEPUK_TANGAN, GerakId.MAIN_LENTERA,
					GerakId.MENARI_PANEN -> PendudukSnapshot.KLIP_SORAK;
			case 0, GerakId.BERDIRI_JAGA, GerakId.SIKAP_TANGAN_BELAKANG,
					GerakId.TATAP_LANGIT, GerakId.BICARA_TANGAN, GerakId.SIKAP_LIPAT_TANGAN,
					GerakId.SIKAP_TOMBAK, GerakId.SIKAP_BERKACAK, GerakId.SIKAP_ANGKUH,
					GerakId.SIKAP_SANTAI_L, GerakId.SIKAP_TUA, GerakId.SIKAP_ANAK,
					GerakId.SIKAP_DOA, GerakId.SIKAP_TUNDUK, GerakId.MEMANDANG,
					GerakId.LENGAN_DIGENDONG, GerakId.MENANGIS, GerakId.MAIN_SERULING,
					GerakId.MENGAJAR, GerakId.LIHAT_BARANG -> PendudukSnapshot.KLIP_DIAM;
			default -> PendudukSnapshot.KLIP_KERJA;
		};
	}

	private static void kirim(TempatHuni huni, List<ServerPlayer> dekat, Map<ServerPlayer, List<PendudukSnapshot.Entri>> jauh, BlockPos anchor,
			@Nullable String acara, long waktu) {
		List<PendudukSnapshot.Tribun> tribun = tribun(huni, anchor, acara, waktu);
		HidupConfig cfg = HidupConfig.get();
		for (ServerPlayer player : dekat) {
			List<PendudukSnapshot.Entri> list = jauh.getOrDefault(player, List.of());
			if (list.size() > cfg.impostorMaks) {
				// nearest first: the far edge thins out
				Vec3 at = player.position();
				List<PendudukSnapshot.Entri> sorted = new ArrayList<>(list);
				sorted.sort(Comparator.comparingDouble(e -> Vec3.atBottomCenterOf(anchor).add(e.dx() / 2.0D, e.dy() / 2.0D, e.dz() / 2.0D).distanceToSqr(at)));
				list = sorted.subList(0, cfg.impostorMaks);
			}
			PendudukSnapshot snap = new PendudukSnapshot(huni.situsId, anchor.getX(), anchor.getY(), anchor.getZ(), List.copyOf(list), tribun);
			Map<Long, Integer> sent = TERKIRIM.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
			int hash = snap.hashIsi();
			Integer before = sent.get(huni.situsId);
			if (before != null && before == hash) {
				continue;
			}
			if (before == null && list.isEmpty() && tribun.isEmpty()) {
				continue;
			}
			sent.put(huni.situsId, hash);
			if (ServerPlayNetworking.canSend(player, PendudukSnapshot.TYPE)) {
				ServerPlayNetworking.send(player, snap);
			}
		}
	}

	/**
	 * The stand seats filled during an arena or square event (up to 160), none otherwise: tournaments, the sect meeting,
	 * the lantern festival, the crowd events of the themed towns and the Kota Iblis' nightly bouts
	 * ({@link JadwalTema#tribun}); for a street scene ({@code tonton:<x,z>}) only the seats within 32 blocks of it.
	 */
	private static List<PendudukSnapshot.Tribun> tribun(TempatHuni huni, BlockPos anchor, @Nullable String acara, long waktu) {
		boolean inti = acara != null && (acara.startsWith("turnamen") || acara.equals("pertemuan_sekte") || acara.equals("festival_lentera")
				|| acara.startsWith("tonton:"));
		int jamAsli = (int) ((Math.floorMod(waktu, 24000L) / 1000L + 6L) % 24L);
		if (!inti && !JadwalTema.tribun(huni.jenis, acara, jamAsli)) {
			return List.of();
		}
		int[] scene = acara != null && acara.startsWith("tonton:") ? Jadwal.xz(acara.substring(7)) : null;
		List<Titik> seats = huni.titik("penonton");
		List<PendudukSnapshot.Tribun> out = new ArrayList<>(Math.min(seats.size(), PendudukSnapshot.MAKS_TRIBUN));
		for (Titik t : seats) {
			if (out.size() >= PendudukSnapshot.MAKS_TRIBUN) {
				break;
			}
			if (scene != null) {
				double dx = t.pos().getX() - scene[0];
				double dz = t.pos().getZ() - scene[1];
				if (dx * dx + dz * dz > 32.0D * 32.0D) {
					continue;
				}
			}
			// a few seats stay empty (seat hash), so the stand never looks pasted
			if (Math.floorMod(huni.slot(t), 10) == 0) {
				continue;
			}
			BlockPos p = t.pos();
			out.add(new PendudukSnapshot.Tribun((short) (p.getX() - anchor.getX()), (short) (p.getY() - anchor.getY()), (short) (p.getZ() - anchor.getZ()),
					(byte) Math.round(t.hadap().toYRot() / 360.0F * 256.0F)));
		}
		return out;
	}

	/** The cached goal of a resident this cycle, or null (Saksi, commands). */
	static @Nullable TempatHuni.Tujuan posisi(long situs, int id) {
		Map<Integer, Posisi> cache = POSISI.get(situs);
		Posisi p = cache == null ? null : cache.get(id);
		return p == null ? null : p.tujuan();
	}

	/** Forgets what a player was sent (logout). */
	static void lupakanPemain(UUID player) {
		TERKIRIM.remove(player);
	}

	/** Direction helper for commands. */
	static String arah(Direction d) {
		return d.getSerializedName();
	}

	private Pemanggil() {
	}
}
