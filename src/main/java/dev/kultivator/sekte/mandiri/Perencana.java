package dev.kultivator.sekte.mandiri;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.mandiri.api.Dorong;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.mandiri.api.ModeMandiri;
import dev.kultivator.sekte.mandiri.api.Pelaksana;
import dev.kultivator.util.SibukMurid;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The daily planner of Murid Mandiri (DESIGN 39.4): one server-side evaluator for every hall, never a goal per entity.
 * <p>
 * <b>Rhythm.</b> Each loaded disciple is judged once per world hour at its own offset ({@code soulHash mod 1000} ticks into
 * the hour) and whenever its activity block ends; the evaluations wait in a queue and at most
 * {@code mandiri.penilaianPerTick} run per server tick (120 disciples = 0.12 per tick on average, no spikes at the full
 * hour). A catch-up after a long absence ({@link KejarKetinggalan}) has its own queue with the same cap.
 * <p>
 * <b>The hour.</b> Bookkeeping always runs (hour strip, drives, habits, the daily step: goals, temptation, the ten-day path
 * check, pocket money at 06:00). The choice runs only when the planner may take the disciple: a real disciple, the policy
 * is not Patuh, no order pause ({@code bebas}), not following or assisting, not on a hall guard order, not held by another
 * module's activity, not fighting, breaking through, duelling, away or in the tournament. A defiant, badly wounded or
 * deviating disciple gets the restricted list of {@link Skor}.
 * <p>
 * <b>Blocks.</b> The running activity is saved in {@code extra.mandiri.kegiatan} ({@code id, mulai, sampai, tempat, teman,
 * acara}); the executor ({@link Pelaksana}) is stepped on the disciple's tick20; the block ends when it says so, when its
 * time is up, or interrupted by an order, a raid, Patuh, another module's activity, a breakthrough. At the end a
 * non-seated activity gives its "breath while working" XP ({@code 50 x base x K x skalaXp} per hour,
 * {@code MandiriApi.beriXpKegiatan}), a liked activity honours the disciple (at most 3 a day), and a night of tea with a
 * sworn friend counts toward redemption. The body activities 40-60 are never saved, so a reload ends the old record
 * (interrupted) and plans again.
 * <p>
 * <b>The hall.</b> At 05:00 the free disciples line up in the courtyard facing the main hall for the morning salute;
 * a raid cancels every activity except the watches; Patuh ends everything at once.
 */
public final class Perencana {

	private static volatile @Nullable MinecraftServer server;

	/** owner -> soul -> loaded body. */
	private static final Map<UUID, Map<UUID, DiscipleEntity>> DIMUAT = new HashMap<>();
	/** Souls seen since the server started (their first tick20 is the "just loaded" step). */
	private static final Set<UUID> DILIHAT = new HashSet<>();
	/** soul -> the last hour index judged. */
	private static final Map<UUID, Long> JAM_TERAKHIR = new HashMap<>();
	/** soul -> its block position at the last hour (boredom: the same place). */
	private static final Map<UUID, Long> POS_TERAKHIR = new HashMap<>();
	private static final ArrayDeque<DiscipleEntity> ANTRE_NILAI = new ArrayDeque<>();
	private static final Set<UUID> DI_ANTRE = new HashSet<>();
	private static final ArrayDeque<DiscipleEntity> ANTRE_KEJAR = new ArrayDeque<>();
	private static final Set<UUID> DI_KEJAR = new HashSet<>();
	/** owner -> departures in the current world hour {hour index, count}. */
	private static final Map<UUID, long[]> BERANGKAT = new HashMap<>();
	/** owner -> the world day of its last morning salute. */
	private static final Map<UUID, Long> SALAM = new HashMap<>();

	// The budget gauge of 39.9 (uji perguruan mandiri anggaran).
	private static double rataNanos;
	private static long nanosTick;
	private static int nilaiTerakhir;
	private static long totalNilai;
	private static long totalKejar;

	static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
		ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> kosongkan());
		ServerTickEvents.END_SERVER_TICK.register(Perencana::tick);
		DiscipleHooks.addTick20(Perencana::tick20);
		MandiriApi.addPerintahListener(entity -> batal(entity, true));
		// Raiders driven off count toward MENOLONG (39.3.4).
		DiscipleHooks.addOnKill((entity, victim) -> {
			UUID owner = entity.ownerId();
			if (owner != null && victim instanceof Enemy && MandiriApi.aktif() && Irama.serbuan(owner)) {
				MandiriApi.tambahStatistik(entity.data(), MandiriIds.S_MENOLONG, 1L);
			}
		});
	}

	public static @Nullable MinecraftServer server() {
		return server;
	}

	private static void kosongkan() {
		DIMUAT.clear();
		DILIHAT.clear();
		JAM_TERAKHIR.clear();
		POS_TERAKHIR.clear();
		ANTRE_NILAI.clear();
		DI_ANTRE.clear();
		ANTRE_KEJAR.clear();
		DI_KEJAR.clear();
		BERANGKAT.clear();
		SALAM.clear();
		TempatCache.kosongkan();
		AnggaranGerak.kosongkan();
		server = null;
	}

	// ================================================================== the loaded registry

	/** The loaded, living disciples of {@code owner} the planner knows (refreshed every second). */
	public static List<DiscipleEntity> dimuat(UUID owner) {
		Map<UUID, DiscipleEntity> map = owner == null ? null : DIMUAT.get(owner);
		if (map == null) {
			return List.of();
		}
		List<DiscipleEntity> out = new ArrayList<>(map.size());
		for (DiscipleEntity d : map.values()) {
			if (d.isAlive() && !d.isRemoved()) {
				out.add(d);
			}
		}
		return out;
	}

	public static @Nullable DiscipleEntity cari(UUID owner, UUID soul) {
		Map<UUID, DiscipleEntity> map = owner == null ? null : DIMUAT.get(owner);
		DiscipleEntity d = map == null || soul == null ? null : map.get(soul);
		return d != null && d.isAlive() && !d.isRemoved() ? d : null;
	}

	/** The loaded body that carries {@code data} (the default money book), or null. */
	static @Nullable DiscipleEntity cariData(DiscipleData data) {
		for (Map<UUID, DiscipleEntity> map : DIMUAT.values()) {
			for (DiscipleEntity d : map.values()) {
				if (d.data() == data) {
					return d;
				}
			}
		}
		return null;
	}

	/** Every owner with loaded disciples. */
	static List<UUID> pemilik() {
		return new ArrayList<>(DIMUAT.keySet());
	}

	// ================================================================== the running record

	/** The running planner activity, or null. */
	public static @Nullable Kegiatan berjalan(DiscipleEntity murid) {
		return MandiriApi.kegiatan(murid);
	}

	private static CompoundTag rekam(DiscipleEntity murid) {
		return MandiriApi.bagian(murid.data()).getCompoundOrEmpty(MandiriIds.K_KEGIATAN);
	}

	/** True when the body activity is the planner's own (a running record whose body activity is still in place). */
	public static boolean milikku(DiscipleEntity murid) {
		CompoundTag rec = rekam(murid);
		Kegiatan k = Kegiatan.byId(rec.getStringOr(MandiriIds.KG_ID, ""));
		if (k == null || k == Kegiatan.KOSONG) {
			return false;
		}
		int expected = aktivitasRekam(rec, k);
		return expected == 0 || cocok(k, murid.activity(), expected);
	}

	/**
	 * True when body activity {@code act} belongs to the running {@code k}: its own act, or for BELAJAR (39B) also
	 * reading a slip from the bag (53) and listening to a senior (56) besides studying at the rack (13).
	 */
	private static boolean cocok(Kegiatan k, int act, int expected) {
		return act == expected || k == Kegiatan.BELAJAR
				&& (act == MandiriKegiatan.ACT_MERENUNG || act == MandiriKegiatan.ACT_MEDITASI_MANDIRI);
	}

	/** True while the planner event {@code acara} runs on this disciple. */
	public static boolean acara(DiscipleEntity murid, String acara) {
		return acara.equals(rekam(murid).getStringOr(MandiriIds.KG_ACARA, ""));
	}

	private static int aktivitasRekam(CompoundTag rec, Kegiatan k) {
		String acara = rec.getStringOr(MandiriIds.KG_ACARA, "");
		if (!acara.isEmpty()) {
			return Kegiatan.MERENUNG.act();
		}
		return k.act();
	}

	private static Pelaksana pelaksana(CompoundTag rec, Kegiatan k) {
		String acara = rec.getStringOr(MandiriIds.KG_ACARA, "");
		if (!acara.isEmpty()) {
			Pelaksana p = PelaksanaDasar.acara(acara);
			return p == null ? Pelaksana.KOSONG : p;
		}
		return MandiriKegiatan.pelaksana(k);
	}

	private static Pelaksana.Konteks konteks(DiscipleEntity murid, CompoundTag rec, Kegiatan k) {
		BlockPos tempat = rec.contains(MandiriIds.KG_TEMPAT) ? BlockPos.of(rec.getLongOr(MandiriIds.KG_TEMPAT, 0L)) : null;
		UUID teman = PelaksanaDasar.uuid(rec.getStringOr(MandiriIds.KG_TEMAN, ""));
		return new Pelaksana.Konteks((ServerLevel) murid.level(), murid.ownerId(), k, rec.getLongOr(MandiriIds.KG_MULAI, 0L),
				rec.getLongOr(MandiriIds.KG_SAMPAI, -1L), tempat, teman, MandiriApi.kebijakan(murid));
	}

	// ================================================================== tick20 of each disciple

	private static void tick20(DiscipleEntity murid) {
		if (!(murid.level() instanceof ServerLevel level) || murid.role() != DiscipleEntity.Role.DISCIPLE || !MandiriApi.aktif()) {
			return;
		}
		UUID owner = murid.ownerId();
		if (owner == null) {
			return;
		}
		long t0 = System.nanoTime();
		UUID soul = murid.soulId();
		DIMUAT.computeIfAbsent(owner, key -> new HashMap<>()).put(soul, murid);
		long now = level.getGameTime();
		if (DILIHAT.add(soul)) {
			baruDimuat(murid, now);
		}
		langkah(murid, now);
		long off = Math.floorMod(soul.hashCode(), Irama.JAM_TICK);
		long hour = Math.floorDiv(now - off, Irama.JAM_TICK);
		Long last = JAM_TERAKHIR.put(soul, hour);
		if (last == null || last != hour) {
			antre(murid);
		}
		nanosTick += System.nanoTime() - t0;
	}

	/** The first second of a body this session: queued events, a stale record ended, path and goals, catch-up. */
	private static void baruDimuat(DiscipleEntity murid, long now) {
		CompoundTag rec = rekam(murid);
		if (!rec.isEmpty()) {
			// Activities 40-60 are never saved: the old record ends here, interrupted, and the planner picks again.
			akhiri(murid, true);
		}
		CitaCita.terapkanAntre(murid);
		DiscipleData data = murid.data();
		if (MandiriApi.jalan(data) == null) {
			PilihJalan.periksa(murid, "awal");
			CitaCita.perbarui(murid);
		}
		CompoundTag section = MandiriApi.bagianTulis(data);
		if (KejarKetinggalan.perlu(data, now)) {
			if (DI_KEJAR.add(murid.soulId())) {
				ANTRE_KEJAR.add(murid);
			}
		} else if (section.getLongOr(MandiriIds.K_TERAKHIR, 0L) <= 0L) {
			section.putLong(MandiriIds.K_TERAKHIR, now);
			section.putLong(MandiriIds.K_SAKU_HARI, Irama.hari(murid.level()));
			section.putLong(MandiriIds.K_HARI, Irama.hari(murid.level()));
		}
	}

	/** One step of the running activity: interrupted, ended, or stepped. */
	private static void langkah(DiscipleEntity murid, long now) {
		CompoundTag rec = rekam(murid);
		Kegiatan k = Kegiatan.byId(rec.getStringOr(MandiriIds.KG_ID, ""));
		if (k == null || k == Kegiatan.KOSONG) {
			return;
		}
		if (harusBatal(murid, rec, k)) {
			akhiri(murid, true);
			antre(murid);
			return;
		}
		Pelaksana.Konteks konteks = konteks(murid, rec, k);
		boolean terus;
		try {
			terus = pelaksana(rec, k).jalan(murid, konteks);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Mandiri executor of {} failed", k.id(), exception);
			terus = false;
		}
		long sampai = rec.getLongOr(MandiriIds.KG_SAMPAI, -1L);
		if (!terus || sampai >= 0L && now >= sampai) {
			akhiri(murid, !terus && sampai >= 0L && now < sampai - 20L);
			antre(murid);
		}
	}

	/** Orders, Patuh, raids, another module's activity, a breakthrough or a duel interrupt the planner (39.7.2). */
	private static boolean harusBatal(DiscipleEntity murid, CompoundTag rec, Kegiatan k) {
		long now = murid.level().getGameTime();
		if (PelaksanaDasar.ACARA_TEBUS.equals(rec.getStringOr(MandiriIds.KG_ACARA, ""))) {
			// The master's own ritual: the order pause, FOLLOW and Patuh do not end it; it checks the master itself.
			return murid.data().breakingThrough();
		}
		if (MandiriApi.bebasSampai(murid.data()) > now || murid.mode().follows()) {
			return true;
		}
		if (MandiriApi.kebijakan(murid).mode() == ModeMandiri.PATUH) {
			return true;
		}
		UUID owner = murid.ownerId();
		if (owner != null && Irama.serbuan(owner) && k != Kegiatan.JAGA_GERBANG && k != Kegiatan.JAGA_FORMASI) {
			return true;
		}
		int expected = aktivitasRekam(rec, k);
		if (expected != 0 && !cocok(k, murid.activity(), expected) && !k.perjalanan()) {
			// Another module (or the master) put the body to something else.
			return now - rec.getLongOr(MandiriIds.KG_MULAI, now) > 40L;
		}
		if (murid.data().breakingThrough() && k != Kegiatan.TEROBOSAN && k != Kegiatan.JAGA_TEROBOSAN) {
			return true;
		}
		return k == Kegiatan.JAGA_GERBANG && murid.mode() != DiscipleEntity.Mode.GUARD_HALL;
	}

	// ================================================================== queues and the server tick

	/** Queues an evaluation (the hour turned, a block ended, freed by the master). */
	static void antre(DiscipleEntity murid) {
		if (DI_ANTRE.add(murid.soulId())) {
			ANTRE_NILAI.add(murid);
		}
	}

	/** Judges this disciple at the next server tick (the "Bebaskan" button). */
	public static void segera(DiscipleEntity murid) {
		if (murid != null) {
			JAM_TERAKHIR.remove(murid.soulId());
			antre(murid);
		}
	}

	private static void tick(MinecraftServer srv) {
		if (!MandiriApi.aktif()) {
			return;
		}
		long t0 = System.nanoTime();
		int cap = MandiriConfig.get().penilaianPerTick;
		for (int i = 0; i < cap && !ANTRE_KEJAR.isEmpty(); i++) {
			DiscipleEntity murid = ANTRE_KEJAR.poll();
			DI_KEJAR.remove(murid.soulId());
			if (murid.isAlive() && !murid.isRemoved()) {
				long last = MandiriApi.bagian(murid.data()).getLongOr(MandiriIds.K_TERAKHIR, 0L);
				try {
					KejarKetinggalan.jalankan(murid, murid.level().getGameTime() - last);
					totalKejar++;
					antre(murid);
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("Mandiri catch-up failed for {}", murid.getName().getString(), exception);
				}
			}
		}
		int done = 0;
		while (done < cap && !ANTRE_NILAI.isEmpty()) {
			DiscipleEntity murid = ANTRE_NILAI.poll();
			DI_ANTRE.remove(murid.soulId());
			if (!murid.isAlive() || murid.isRemoved() || DI_KEJAR.contains(murid.soulId())) {
				continue;
			}
			try {
				nilai(murid);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Mandiri planner failed for {}", murid.getName().getString(), exception);
			}
			done++;
		}
		nilaiTerakhir = done;
		totalNilai += done;
		if (srv.getTickCount() % 100 == 0) {
			bersihkan();
			for (UUID owner : pemilik()) {
				salamPagi(owner);
			}
		}
		long spent = System.nanoTime() - t0 + nanosTick;
		nanosTick = 0L;
		rataNanos = rataNanos * 0.98D + spent * 0.02D;
	}

	/** Drops bodies that unloaded (their catch-up starts from {@code terakhir} at the next load). */
	private static void bersihkan() {
		for (Iterator<Map.Entry<UUID, Map<UUID, DiscipleEntity>>> it = DIMUAT.entrySet().iterator(); it.hasNext(); ) {
			Map<UUID, DiscipleEntity> map = it.next().getValue();
			map.values().removeIf(d -> {
				boolean gone = !d.isAlive() || d.isRemoved();
				if (gone) {
					DILIHAT.remove(d.soulId());
					JAM_TERAKHIR.remove(d.soulId());
					POS_TERAKHIR.remove(d.soulId());
					TempatCache.lepas(d.ownerId(), d.soulId());
				}
				return gone;
			});
			if (map.isEmpty()) {
				it.remove();
			}
		}
	}

	// ================================================================== the evaluation

	private static void nilai(DiscipleEntity murid) {
		ServerLevel level = (ServerLevel) murid.level();
		long now = level.getGameTime();
		jamBerlalu(murid, level, now);
		if (!bolehDirencana(murid, now)) {
			return;
		}
		Kegiatan running = berjalan(murid);
		CompoundTag rec = rekam(murid);
		if (running != null && !rec.getStringOr(MandiriIds.KG_ACARA, "").isEmpty()) {
			return;
		}
		UUID owner = murid.ownerId();
		Skor.Hasil hasil = Skor.nilai(murid, running, bebasPergi(owner, now));
		Kegiatan pilih = Skor.pilih(hasil, murid.getRandom());
		if (running != null) {
			long sampai = rec.getLongOr(MandiriIds.KG_SAMPAI, -1L);
			boolean blokHabis = sampai >= 0L && now >= sampai;
			boolean masihAtas = false;
			for (Kegiatan top : hasil.teratas(3)) {
				masihAtas |= top == running;
			}
			if (pilih == running || !blokHabis && masihAtas) {
				return;
			}
			akhiri(murid, !blokHabis);
		}
		if (pilih == Kegiatan.KOSONG) {
			return;
		}
		int ticks = durasi(murid, pilih);
		mulai(murid, pilih, ticks, hasil.tempat[pilih.ordinal()], hasil.teman, null);
		if (pilih == Kegiatan.BERMAIN && hasil.teman != null) {
			// Play is shared: the companion comes along when it is free.
			DiscipleEntity teman = cari(owner, hasil.teman);
			if (teman != null && bebasUntukTeman(teman) && bolehDirencana(teman, now)) {
				BlockPos near = hasil.tempat[pilih.ordinal()];
				paksa(teman, Kegiatan.BERMAIN, Math.max(200, ticks), near == null ? null : near.east(2), murid.soulId());
			}
		}
	}

	/** True when the planner may give this disciple something now (see the class comment). */
	static boolean bolehDirencana(DiscipleEntity murid, long now) {
		if (murid.role() != DiscipleEntity.Role.DISCIPLE || !murid.isAlive() || murid.isReleasing()) {
			return false;
		}
		UUID owner = murid.ownerId();
		if (owner == null || MandiriApi.kebijakan(murid).mode() == ModeMandiri.PATUH) {
			return false;
		}
		if (MandiriApi.bebasSampai(murid.data()) > now || murid.mode().follows()) {
			return false;
		}
		Kegiatan running = berjalan(murid);
		if (murid.mode() == DiscipleEntity.Mode.GUARD_HALL && running != Kegiatan.JAGA_GERBANG) {
			return false;
		}
		int act = murid.activity();
		if (act != 0 && !milikku(murid)) {
			return false;
		}
		DiscipleData data = murid.data();
		if (murid.getTarget() != null || data.breakingThrough() || dev.kultivator.disciple.DuelRules.inDuel(murid)) {
			return false;
		}
		UUID soul = murid.soulId();
		if (SekteApi.turnamen().busy(owner, soul) || SekteApi.misi().away(owner, soul) || MandiriApi.sedangPergi(owner, soul)) {
			return false;
		}
		if (running == null && act == 0) {
			// Other modules' and later modules' busy checks (37, 38, 39F...): only for a disciple that is not ours yet.
			String why = SibukMurid.why(murid);
			if (why != null && !why.endsWith(".membangkang") && !why.endsWith(".luka_berat") && !why.endsWith(".deviasi")
					&& !why.endsWith(".tutup_diri")) {
				return false;
			}
		}
		return true;
	}

	/** A free companion: idle and not busy, or resting, eating, playing or contemplating in the planner. */
	static boolean bebasUntukTeman(DiscipleEntity other) {
		if (other.role() != DiscipleEntity.Role.DISCIPLE || other.getTarget() != null || other.data().breakingThrough()) {
			return false;
		}
		Kegiatan running = berjalan(other);
		if (running == null) {
			return other.activity() == 0 && SibukMurid.why(other) == null;
		}
		return running == Kegiatan.ISTIRAHAT || running == Kegiatan.MAKAN || running == Kegiatan.BERMAIN
				|| running == Kegiatan.MERENUNG && rekam(other).getStringOr(MandiriIds.KG_ACARA, "").isEmpty();
	}

	/** True when the hall may send one more disciple away now: at most 2 departures per world hour and the away share. */
	static boolean bebasPergi(UUID owner, long now) {
		if (owner == null) {
			return false;
		}
		long[] dep = BERANGKAT.get(owner);
		long hour = Math.floorDiv(now, Irama.JAM_TICK);
		if (dep != null && dep[0] == hour && dep[1] >= 2L) {
			return false;
		}
		ModeMandiri mode = MandiriApi.kebijakanAula(owner).mode();
		int maks = MandiriConfig.get().pergiMaks(mode);
		int away = 0;
		List<Halls.AwayView> list = Halls.away(owner);
		for (Halls.AwayView view : list) {
			if (MandiriIds.AWAY_JALAN.equals(view.reason())) {
				away++;
			}
		}
		List<DiscipleEntity> loaded = dimuat(owner);
		for (DiscipleEntity d : loaded) {
			Kegiatan k = berjalan(d);
			if (k != null && k.perjalanan()) {
				away++;
			}
		}
		int total = Math.max(1, list.size() + loaded.size());
		return away * 100 < maks * total;
	}

	// ================================================================== the hour's bookkeeping

	private static void jamBerlalu(DiscipleEntity murid, ServerLevel level, long now) {
		DiscipleData data = murid.data();
		CompoundTag section = MandiriApi.bagianTulis(data);
		long today = Irama.hari(level);
		int jam = Irama.jam(level);
		section.putLong(MandiriIds.K_TERAKHIR, now);

		// The hour strip: roll the day, write this hour's cell.
		int[] strip = section.getIntArray(MandiriIds.K_RIWAYAT).filter(a -> a.length == 24).map(int[]::clone).orElse(new int[24]);
		long stripDay = section.getLongOr(MandiriIds.K_RIWAYAT_HARI, -1L);
		if (stripDay != today) {
			section.putIntArray(MandiriIds.K_KEMARIN, stripDay == today - 1L ? strip : new int[24]);
			strip = new int[24];
			section.putLong(MandiriIds.K_RIWAYAT_HARI, today);
		}
		Kegiatan running = berjalan(murid);
		Kegiatan lalu = running == null ? Kegiatan.KOSONG : running;
		Kegiatan sebelumnya = Kegiatan.byOrdinal(strip[Math.floorMod(jam - 1, 24)]);
		strip[jam] = lalu.ordinal();
		section.putIntArray(MandiriIds.K_RIWAYAT, strip);
		int kaliHariIni = 0;
		for (int cell : strip) {
			if (cell == lalu.ordinal()) {
				kaliHariIni++;
			}
		}

		// Drives, habits, statistics.
		long pos = murid.blockPosition().asLong();
		Long lastPos = POS_TERAKHIR.put(murid.soulId(), pos);
		boolean tempatSama = lastPos != null && BlockPos.of(lastPos).distSqr(murid.blockPosition()) <= 9.0D;
		Dorongan.perJam(murid, lalu, sebelumnya, tempatSama, sendiri(murid), kaliHariIni <= 1);
		if (lalu != Kegiatan.KOSONG) {
			PilihJalan.kebiasaan(data, lalu);
			MandiriApi.tambahStatistik(data, MandiriIds.S_JAM_MANDIRI, 1L);
			if (lalu == Kegiatan.MEDITASI || lalu == Kegiatan.TUTUP_DIRI || lalu == Kegiatan.SERAP_BATU) {
				MandiriApi.tambahStatistik(data, MandiriIds.S_JAM_KULTIVASI, 1L);
			}
		}

		// A realm rise wakes the hall-mates' hunger (+10); awakening, Foundation and a new Dao re-check the path.
		int ranah = data.realm().ordinal();
		int ranahLalu = section.getIntOr(MandiriIds.K_RANAH_LALU, ranah);
		int dao = data.daoIndex();
		int daoLalu = section.getIntOr(MandiriIds.K_DAO_LALU, dao);
		section.putInt(MandiriIds.K_RANAH_LALU, ranah);
		section.putInt(MandiriIds.K_DAO_LALU, dao);
		if (ranah > ranahLalu) {
			for (DiscipleEntity other : dimuat(murid.ownerId())) {
				if (other != murid && other.distanceToSqr(murid) < 48.0D * 48.0D) {
					Dorongan.tambah(other.data(), Dorong.KULTIVASI, 10);
				}
			}
		}
		boolean cekJalan = ranah > ranahLalu && (ranah == Realm.QI_REFINING.ordinal() || ranah == Realm.FOUNDATION.ordinal())
				|| dao != daoLalu && dao >= 0;

		// The daily step.
		long hariLangkah = section.getLongOr(MandiriIds.K_HARI, today);
		if (hariLangkah < today) {
			int lewat = (int) Math.min(30L, today - hariLangkah);
			section.putLong(MandiriIds.K_HARI, today);
			PilihJalan.pudarkan(data, lewat);
			JalanIblis.harian(murid, lewat);
			CitaCita.perbarui(murid);
			if (PilihJalan.jatuhTempo(data, today)) {
				cekJalan = true;
			}
		} else if (!section.contains(MandiriIds.K_HARI)) {
			section.putLong(MandiriIds.K_HARI, today);
		}
		if (cekJalan) {
			PilihJalan.periksa(murid, "jam");
		}
		if (jam >= 6 && section.getLongOr(MandiriIds.K_SAKU_HARI, today) < today) {
			KejarKetinggalan.bayarSaku(murid, (int) Math.min(30L, today - section.getLongOr(MandiriIds.K_SAKU_HARI, today)), false);
		}
	}

	/** No hall-mate within 6 blocks. */
	private static boolean sendiri(DiscipleEntity murid) {
		for (DiscipleEntity other : dimuat(murid.ownerId())) {
			if (other != murid && other.distanceToSqr(murid) < 36.0D) {
				return false;
			}
		}
		return true;
	}

	// ================================================================== starting and ending

	/** A block length for {@code k}: between its shortest and longest, seclusion capped by the mode (Seimbang 2 days). */
	private static int durasi(DiscipleEntity murid, Kegiatan k) {
		if (k.blokMin() < 0) {
			return -1;
		}
		int min = k.blokMin();
		int max = Math.max(min, k.blokMaks());
		if (k == Kegiatan.TUTUP_DIRI && MandiriApi.kebijakan(murid).mode() == ModeMandiri.SEIMBANG) {
			max = Math.min(max, 48_000);
		}
		if (k == Kegiatan.ISTIRAHAT && murid.data().realm().ordinal() < Realm.GOLDEN_CORE.ordinal()
				&& Irama.malam(Irama.jam(murid.level()))) {
			// The young sleep four hours a night.
			return 4000;
		}
		int step = Irama.JAM_TICK / 2;
		int slots = (max - min) / step;
		return min + (slots <= 0 ? 0 : murid.getRandom().nextInt(slots + 1) * step);
	}

	/** Starts {@code k} (or planner event {@code acara}) for {@code ticks} (-1 open) at {@code tempat}. */
	private static void mulai(DiscipleEntity murid, Kegiatan k, int ticks, @Nullable BlockPos tempat, @Nullable UUID teman,
			@Nullable String acara) {
		ServerLevel level = (ServerLevel) murid.level();
		long now = level.getGameTime();
		UUID owner = murid.ownerId();
		CompoundTag rec = new CompoundTag();
		rec.putString(MandiriIds.KG_ID, k.id());
		rec.putLong(MandiriIds.KG_MULAI, now);
		rec.putLong(MandiriIds.KG_SAMPAI, ticks < 0 ? -1L : now + ticks);
		if (tempat != null) {
			rec.putLong(MandiriIds.KG_TEMPAT, tempat.asLong());
		}
		if (teman != null) {
			rec.putString(MandiriIds.KG_TEMAN, teman.toString());
		}
		if (acara != null) {
			rec.putString(MandiriIds.KG_ACARA, acara);
		}
		CompoundTag section = MandiriApi.bagianTulis(murid.data());
		section.put(MandiriIds.K_KEGIATAN, rec);
		CompoundTag kegHari = section.getCompound(MandiriIds.K_KEG_HARI).orElse(null);
		if (kegHari == null) {
			kegHari = new CompoundTag();
			section.put(MandiriIds.K_KEG_HARI, kegHari);
		}
		kegHari.putLong(k.id(), Irama.hari(level));
		if (tempat != null && owner != null) {
			TempatCache.pesan(owner, murid.soulId(), tempat, ticks < 0 ? now + 24_000L : now + ticks + 200L);
			if (!AnggaranGerak.dekat(murid, tempat)) {
				AnggaranGerak.tambah(owner, now);
			}
		}
		if (k == Kegiatan.PERJALANAN && owner != null) {
			long hour = Math.floorDiv(now, Irama.JAM_TICK);
			long[] dep = BERANGKAT.computeIfAbsent(owner, key -> new long[]{hour, 0L});
			if (dep[0] != hour) {
				dep[0] = hour;
				dep[1] = 0L;
			}
			dep[1]++;
		}
		try {
			pelaksana(rec, k).mulai(murid, konteks(murid, rec, k));
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Mandiri executor of {} failed to start", k.id(), exception);
			section.remove(MandiriIds.K_KEGIATAN);
			TempatCache.lepas(owner, murid.soulId());
		}
	}

	/** Ends the running record exactly once: the executor's end, the breath XP, honours, friend nights. */
	private static void akhiri(DiscipleEntity murid, boolean batal) {
		CompoundTag section = MandiriApi.bagianTulis(murid.data());
		CompoundTag rec = section.getCompoundOrEmpty(MandiriIds.K_KEGIATAN);
		Kegiatan k = Kegiatan.byId(rec.getStringOr(MandiriIds.KG_ID, ""));
		if (rec.isEmpty() || k == null) {
			section.remove(MandiriIds.K_KEGIATAN);
			return;
		}
		Pelaksana pelaksana = pelaksana(rec, k);
		Pelaksana.Konteks konteks = konteks(murid, rec, k);
		double kFaktor;
		try {
			kFaktor = pelaksana.K(murid, konteks);
		} catch (RuntimeException exception) {
			kFaktor = 0.0D;
		}
		try {
			pelaksana.selesai(murid, konteks, batal);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Mandiri executor of {} failed to end", k.id(), exception);
		}
		section.remove(MandiriIds.K_KEGIATAN);
		UUID owner = murid.ownerId();
		TempatCache.lepas(owner, murid.soulId());
		ServerLevel level = (ServerLevel) murid.level();
		long now = level.getGameTime();
		long lama = Math.max(0L, now - konteks.mulai());
		if (lama < 100L || k.perjalanan() || duduk(k)) {
			return;
		}
		// Breath while working (39.5.1): 50 x base x K x skalaXp per hour, P's work factor on sect work.
		if (kFaktor > 0.0D) {
			double jam = lama / (double) Irama.JAM_TICK;
			double amount = 50.0D * KejarKetinggalan.baseTanpaMode(murid.data()) * kFaktor * MandiriConfig.get().skalaXp * jam;
			if (k.izin() == dev.kultivator.sekte.mandiri.api.IzinKegiatan.KERJA) {
				amount *= SekteApi.hati().workFactor(murid.data());
			}
			MandiriApi.beriXpKegiatan(murid, amount);
		}
		if (batal) {
			return;
		}
		JalanHidup jalan = MandiriApi.jalan(murid.data());
		if (jalan != null && Skor.jalan(jalan, k) >= 1.8D) {
			hormat(murid, level);
		}
		if (k == Kegiatan.SOSIAL && Irama.jam(level) >= 19 && konteks.teman() != null) {
			JalanIblis.malamBersahabat(murid, cari(owner, konteks.teman()));
		}
	}

	/** Seated activities get their XP from the old meditation tick, not from the breath rule. */
	private static boolean duduk(Kegiatan k) {
		return k == Kegiatan.MEDITASI || k == Kegiatan.TUTUP_DIRI || k == Kegiatan.SERAP_BATU || k == Kegiatan.BELAJAR
				|| k == Kegiatan.JAGA_FORMASI || k == Kegiatan.TEROBOSAN;
	}

	/** A liked activity done: {@code DISCIPLE_HONORED} +1, at most 3 a day (39.10.2). */
	private static void hormat(DiscipleEntity murid, ServerLevel level) {
		long today = Irama.hari(level);
		CompoundTag section = MandiriApi.bagianTulis(murid.data());
		CompoundTag suka = section.getCompound(MandiriIds.K_SUKA).orElse(null);
		if (suka == null) {
			suka = new CompoundTag();
			section.put(MandiriIds.K_SUKA, suka);
		}
		if (suka.getLongOr("hari", -1L) != today) {
			suka.putLong("hari", today);
			suka.putInt("n", 0);
		}
		int n = suka.getIntOr("n", 0);
		if (n < 3) {
			suka.putInt("n", n + 1);
			SekteBus.honored(level.getServer(), murid.ownerId(), murid.soulId(), "mandiri_suka", 1);
		}
	}

	// ================================================================== public doors

	/** Ends the running planner activity ({@code batal}: interrupted). */
	public static void batal(DiscipleEntity murid, boolean batal) {
		if (murid != null && murid.level() instanceof ServerLevel && !rekam(murid).isEmpty()) {
			akhiri(murid, batal);
		}
	}

	/** Patuh: every running activity of the hall ends now. */
	static void batalSemua(UUID owner) {
		for (DiscipleEntity d : dimuat(owner)) {
			batal(d, true);
		}
	}

	/**
	 * Starts {@code k} now outside the hourly choice (MandiriApi.mulaiKegiatan). The place comes from the hall cache when
	 * none is given; the registered executor must accept.
	 */
	public static boolean paksa(DiscipleEntity murid, Kegiatan k, int ticks, @Nullable BlockPos tempat, @Nullable UUID teman) {
		if (!(murid.level() instanceof ServerLevel level) || k == null || k == Kegiatan.KOSONG) {
			return false;
		}
		long now = level.getGameTime();
		if (!bolehDirencana(murid, now) && berjalan(murid) == null) {
			return false;
		}
		BlockPos spot = tempat != null ? tempat : TempatCache.cari(murid, k);
		Pelaksana p = MandiriKegiatan.pelaksana(k);
		Pelaksana.Konteks probe = new Pelaksana.Konteks(level, murid.ownerId(), k, now, ticks < 0 ? -1L : now + ticks, spot, teman,
				MandiriApi.kebijakan(murid));
		try {
			if (!p.bisa(murid, probe)) {
				return false;
			}
		} catch (RuntimeException exception) {
			return false;
		}
		if (!rekam(murid).isEmpty()) {
			akhiri(murid, true);
		}
		mulai(murid, k, ticks, spot, teman, null);
		return true;
	}

	/** Starts a planner event (the salute, the redemption) that borrows the contemplation pose. */
	static void mulaiAcara(DiscipleEntity murid, String acara, BlockPos tempat, int ticks) {
		if (!rekam(murid).isEmpty()) {
			akhiri(murid, true);
		}
		mulai(murid, Kegiatan.MERENUNG, ticks, tempat, null, acara);
	}

	// ================================================================== the morning salute (39.4.4)

	/** 05:00: the free disciples of the hall line up six to a row in front of the main hall, facing it. */
	private static void salamPagi(UUID owner) {
		MinecraftServer srv = server;
		if (srv == null) {
			return;
		}
		List<DiscipleEntity> list = dimuat(owner);
		if (list.isEmpty()) {
			return;
		}
		Halls.HallView hall = Halls.hallOf(owner);
		ServerLevel level = hall == null ? null : srv.getLevel(hall.dim());
		if (level == null || Irama.jam(level) != Irama.JAM_SALAM || Irama.menit(level) > 20) {
			return;
		}
		long today = Irama.hari(level);
		Long done = SALAM.get(owner);
		if (done != null && done == today || Irama.serbuan(owner)) {
			return;
		}
		SALAM.put(owner, today);
		BlockPos base = SectBuild.anchor(owner, "main_hall");
		if (base == null) {
			base = hall.pos();
		}
		long now = level.getGameTime();
		int index = 0;
		for (DiscipleEntity d : list) {
			if (d.level() != level || d.distanceToSqr(base.getX(), base.getY(), base.getZ()) > 96.0D * 96.0D) {
				continue;
			}
			Kegiatan running = berjalan(d);
			boolean free = running == null || running == Kegiatan.ISTIRAHAT || running == Kegiatan.MEDITASI
					|| running == Kegiatan.MAKAN || running == Kegiatan.BERMAIN;
			if (!free || !bolehDirencana(d, now) || MandiriApi.luka(d.data(), now) != JenisLuka.TIDAK || d.data().deviating()) {
				continue;
			}
			int col = index % 6;
			int row = index / 6;
			int x = base.getX() + col * 2 - 5;
			int z = base.getZ() + 4 + row * 2;
			BlockPos column = new BlockPos(x, base.getY(), z);
			int y = level.hasChunkAt(column) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : base.getY();
			if (Math.abs(y - base.getY()) > 4) {
				y = base.getY();
			}
			mulaiAcara(d, PelaksanaDasar.ACARA_SALAM, new BlockPos(x, y, z), PelaksanaDasar.SALAM_TICK + row * 20);
			MandiriApi.bagianTulis(d.data()).putLong(MandiriIds.K_SALAM_HARI, today);
			index++;
		}
		if (index > 0) {
			MandiriApi.kabar(srv, owner, null, "kultivator.sekte.mandiri.inti.kabar.salam", false, String.valueOf(index));
		}
	}

	// ================================================================== the budget gauge (39.9)

	/** Average microseconds per server tick spent by the planner (tick handler plus executor steps). */
	public static double rataMikro() {
		return rataNanos / 1000.0D;
	}

	public static int antreNilai() {
		return ANTRE_NILAI.size();
	}

	public static int antreKejar() {
		return ANTRE_KEJAR.size();
	}

	public static int nilaiTickIni() {
		return nilaiTerakhir;
	}

	public static long totalNilai() {
		return totalNilai;
	}

	public static long totalKejar() {
		return totalKejar;
	}

	/** Loaded disciples the planner knows, all halls. */
	public static int jumlahDimuat() {
		int n = 0;
		for (Map<UUID, DiscipleEntity> map : DIMUAT.values()) {
			n += map.size();
		}
		return n;
	}

	/** The policy used for this hall's journeys and decisions (shortcut for the commands). */
	static Kebijakan kebijakan(UUID owner) {
		return MandiriApi.kebijakanAula(owner);
	}

	private Perencana() {
	}
}
