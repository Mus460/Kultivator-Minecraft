package dev.kultivator.dunia.tempat.tema;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.TempatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The themed-town event calendar (matrix row 23 of {@code jadwal-38-39-40.md}): theme agents register their festivals,
 * dangers and schedules here with {@link #daftar} and never edit this class. 38TK inti runs it in W2: once per world
 * minute per live town (tick-spread, no world scans) it asks each event's {@link PemilihHari} whether today is a day,
 * starts the event at {@code dari} (time of day 0-23999), calls {@link Pelaksana#tick} every 20 ticks while a player is
 * within 128 blocks, and ends it at {@code sampai}; it publishes {@code TempatEvents.ACARA(level, situsId,
 * "tema.<acara>", mulai)} and sends {@code TemaNet.FESTIVAL}. {@code uji tema acara <tema> <acara> [mulai|akhir]} calls
 * {@link #paksa}. Events never change the world weather, time or tick rate, and never a block.
 * <p>
 * Day numbers are overworld clock days ({@code floorDiv(getOverworldClockTime(), 24000)}, as {@code Kota.day}).
 * Examples from the design: Hari Dewi Laut {@code setiap(28, 23)} 6000-12000; Kafilah Agung {@code setiap(5, 1)} from
 * 8000; Malam Tari Putar {@code bulan(MoonPhase.FULL_MOON)} 18000-21000; Malam Lentera Air {@code bulan(NEW_MOON)}
 * 13000-18000; Lomba Perahu Naga {@code setiap(14, 6)} 8000-10000; Musim Ziarah {@code rentang(30, 0, 4)};
 * Lelang Gelap {@code setiap(9, 4)} 18000-20000; Fajar Cahaya Budha {@code peluang(0.30, salt)} 23000-1000 (an event
 * whose {@code sampai < dari} runs across midnight).
 */
public final class TemaKalender {

	/** Decides whether an event happens on world day {@code hari} in the town {@code situsId} centred at {@code pusat}. */
	@FunctionalInterface
	public interface PemilihHari {
		boolean cocok(ServerLevel level, long hari, long situsId, BlockPos pusat);
	}

	/** Runs one event in one town. */
	public interface Pelaksana {

		/** The event starts ({@code dipaksa}: forced by a test command or a quest). */
		void mulai(ServerLevel level, long situsId, boolean dipaksa);

		/** Every 20 ticks while running and a player is near; {@code tickKe} counts from the start. */
		default void tick(ServerLevel level, long situsId, int tickKe) {
		}

		/** The event ends (time over, forced, or the town unloaded for good). */
		default void akhir(ServerLevel level, long situsId) {
		}
	}

	/**
	 * One registered event.
	 *
	 * @param tema      theme id or {@code bersama}
	 * @param id        event id, unique per theme ({@code kapal_datang}, {@code badai_pasir}, {@code lentera_air}, ...)
	 * @param dari      start time of day 0-23999
	 * @param sampai    end time of day 0-23999 (smaller than {@code dari}: past midnight)
	 * @param hari      which days
	 * @param pelaksana the behaviour
	 */
	public record Acara(String tema, String id, int dari, int sampai, PemilihHari hari, Pelaksana pelaksana) {

		/** {@code tema.<id>}, the event name on the {@code TempatEvents.ACARA} bus. */
		public String namaBus() {
			return "tema." + id;
		}

		/** True when time of day {@code t} is inside the window. */
		public boolean dalamJam(long t) {
			long d = Math.floorMod(t, 24000L);
			return dari <= sampai ? d >= dari && d < sampai : d >= dari || d < sampai;
		}
	}

	private static final Map<String, Acara> ACARA = new LinkedHashMap<>();

	/**
	 * Registers an event of theme {@code tema}. A second registration of the same {@code tema}/{@code id} replaces the
	 * first (logged).
	 */
	public static void daftar(String tema, String id, int dari, int sampai, PemilihHari hari, Pelaksana pelaksana) {
		if (tema == null || id == null || hari == null || pelaksana == null) {
			return;
		}
		Acara acara = new Acara(tema, id, Math.floorMod(dari, 24000), Math.floorMod(sampai, 24000), hari, pelaksana);
		if (ACARA.put(tema + "/" + id, acara) != null) {
			Kultivator.LOGGER.warn("Themed event {}/{} registered twice; the last one wins", tema, id);
		}
	}

	/** The event {@code tema}/{@code id}, or null. */
	public static @Nullable Acara cari(String tema, String id) {
		return ACARA.get(tema + "/" + id);
	}

	/** Every event of one theme, in registration order. */
	public static List<Acara> acara(String tema) {
		List<Acara> out = new ArrayList<>();
		for (Acara a : ACARA.values()) {
			if (a.tema().equals(tema)) {
				out.add(a);
			}
		}
		return out;
	}

	/** Every registered event. */
	public static List<Acara> semua() {
		return Collections.unmodifiableList(new ArrayList<>(ACARA.values()));
	}

	/**
	 * Forces an event to start ({@code mulai} true) or end in the town {@code situsId}; false when it is unknown. A forced
	 * start runs for the event's own window length from now (whatever the time of day) and is then ended by the runner;
	 * a forced end stops a running event at once. Used by {@code uji tema acara} and the quest effect {@code acara}.
	 */
	public static boolean paksa(ServerLevel level, long situsId, String tema, String id, boolean mulai) {
		Acara acara = cari(tema, id);
		if (acara == null || level == null) {
			return false;
		}
		String key = kunci(situsId, acara);
		if (mulai) {
			Jalan old = JALAN.remove(key);
			if (old != null) {
				akhiri(level, old);
			}
			long now = level.getGameTime();
			Jalan run = new Jalan(acara, situsId, now, now + panjang(acara), true);
			if (!mulai(level, run)) {
				return false;
			}
			JALAN.put(key, run);
			return true;
		}
		Jalan run = JALAN.remove(key);
		if (run != null) {
			akhiri(level, run);
			return true;
		}
		try {
			acara.pelaksana().akhir(level, situsId);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Themed event {}/{} failed", tema, id, exception);
			return false;
		}
		return true;
	}

	/** True while event {@code tema}/{@code id} runs in the town {@code situsId}. */
	public static boolean berjalan(long situsId, String tema, String id) {
		return JALAN.containsKey(situsId + "|" + tema + "/" + id);
	}

	/** The events running now in the town {@code situsId} (hub card, {@code uji tema acara}, screens). */
	public static List<Acara> berjalanDi(long situsId) {
		List<Acara> out = new ArrayList<>();
		for (Jalan run : JALAN.values()) {
			if (run.situsId == situsId) {
				out.add(run.acara);
			}
		}
		return out;
	}

	/** A forecast answer of {@link #berikutnya}: the event, its world day and its start time of day. */
	public record Berikut(Acara acara, long hari, int jam) {
	}

	/**
	 * The next natural start of any event of town {@code kota} within {@code hariMaks} days, or null. Pickers that read
	 * the moon are asked at the town centre; it is a forecast (a forced event or a switched-off theme may change it).
	 */
	public static @Nullable Berikut berikutnya(ServerLevel level, TemaKota.Kota kota, int hariMaks) {
		long today = hari(level);
		long now = jam(level);
		for (int d = 0; d <= hariMaks; d++) {
			Acara best = null;
			for (Acara a : ACARA.values()) {
				if (!a.tema().equals(kota.tema().id()) || d == 0 && a.dari() <= now) {
					continue;
				}
				try {
					if (a.hari().cocok(level, today + d, kota.situsId(), kota.pusat()) && (best == null || a.dari() < best.dari())) {
						best = a;
					}
				} catch (RuntimeException ignored) {
					// a picker that fails simply has no forecast
				}
			}
			if (best != null) {
				return new Berikut(best, today + d, best.dari());
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ runner (38TK inti, W2)

	/** One running event in one town. */
	private static final class Jalan {
		final Acara acara;
		final long situsId;
		final long mulaiTick;
		final long sampaiTick;
		final boolean dipaksa;
		int tickKe;

		Jalan(Acara acara, long situsId, long mulaiTick, long sampaiTick, boolean dipaksa) {
			this.acara = acara;
			this.situsId = situsId;
			this.mulaiTick = mulaiTick;
			this.sampaiTick = sampaiTick;
			this.dipaksa = dipaksa;
		}
	}

	/** Runner period in ticks (once a second; the live towns are spread over it). */
	private static final int PERIODE = 20;
	/** A running event ticks while a player is within this many blocks of the town centre. */
	public static final int JARAK_PEMAIN = 128;
	/** Festival packets reach players within this many blocks (C38.T4). */
	public static final int JARAK_FESTIVAL = 160;

	private static final Map<String, Jalan> JALAN = new LinkedHashMap<>();
	/** "situs|tema/id" -> the world day it was last decided (natural runs start at most once a day). */
	private static final Map<String, Long> TERAKHIR = new HashMap<>();
	private static boolean initialised;

	/** Starts the runner (from {@link KotaTema#init()}). */
	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerTickEvents.END_SERVER_TICK.register(TemaKalender::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ServerLevel overworld = server.overworld();
			for (Jalan run : new ArrayList<>(JALAN.values())) {
				akhiri(overworld, run);
			}
			JALAN.clear();
			TERAKHIR.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		if (ACARA.isEmpty()) {
			return;
		}
		ServerLevel level = server.overworld();
		long gameTime = level.getGameTime();
		int phase = (int) Math.floorMod(gameTime, (long) PERIODE);
		boolean on = TemaConfig.inti().aktif && TemaConfig.inti().acara;
		List<Long> live = TemaKota.hidupIds();
		if (phase == 0 && !JALAN.isEmpty()) {
			Set<Long> liveSet = new HashSet<>(live);
			long t = jam(level);
			for (Iterator<Jalan> it = JALAN.values().iterator(); it.hasNext(); ) {
				Jalan run = it.next();
				Tema tema = Tema.dari(run.acara.tema());
				boolean over = run.dipaksa ? gameTime >= run.sampaiTick : !run.acara.dalamJam(t);
				boolean off = !run.dipaksa && (!on || tema != null && !TemaConfig.aktif(tema));
				if (!liveSet.contains(run.situsId) || over || off) {
					it.remove();
					akhiri(level, run);
				}
			}
		}
		if (live.isEmpty()) {
			return;
		}
		long today = hari(level);
		long t = jam(level);
		for (int i = 0; i < live.size(); i++) {
			if (Math.floorMod(i, PERIODE) != phase) {
				continue;
			}
			TemaKota.Kota kota = TemaKota.dari(server, live.get(i));
			if (kota != null) {
				jalankan(level, kota, gameTime, today, t, on);
			}
		}
	}

	private static void jalankan(ServerLevel level, TemaKota.Kota kota, long gameTime, long today, long t, boolean on) {
		boolean near = TemaMekanisme.pemainDekat(level, Vec3.atCenterOf(kota.pusat()), JARAK_PEMAIN);
		for (Acara acara : ACARA.values()) {
			if (!acara.tema().equals(kota.tema().id()) && !"bersama".equals(acara.tema())) {
				continue;
			}
			String key = kunci(kota.situsId(), acara);
			Jalan run = JALAN.get(key);
			if (run != null) {
				if (near) {
					run.tickKe += PERIODE;
					try {
						acara.pelaksana().tick(level, kota.situsId(), run.tickKe);
					} catch (RuntimeException exception) {
						Kultivator.LOGGER.error("Themed event {}/{} failed on tick", acara.tema(), acara.id(), exception);
					}
				}
				continue;
			}
			if (!on || !TemaConfig.aktif(kota.tema()) || !acara.dalamJam(t)) {
				continue;
			}
			// a window past midnight belongs to the day it started on
			long day = acara.dari() > acara.sampai() && t < acara.sampai() ? today - 1 : today;
			Long last = TERAKHIR.get(key);
			if (last != null && last == day) {
				continue;
			}
			TERAKHIR.put(key, day);
			boolean pick;
			try {
				pick = acara.hari().cocok(level, day, kota.situsId(), kota.pusat());
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Themed event {}/{} day picker failed", acara.tema(), acara.id(), exception);
				pick = false;
			}
			if (!pick) {
				continue;
			}
			long left = Math.floorMod(acara.sampai() - t, 24000L);
			Jalan baru = new Jalan(acara, kota.situsId(), gameTime, gameTime + Math.max(20L, left), false);
			if (mulai(level, baru)) {
				JALAN.put(key, baru);
			}
		}
	}

	private static boolean mulai(ServerLevel level, Jalan run) {
		try {
			run.acara.pelaksana().mulai(level, run.situsId, run.dipaksa);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Themed event {}/{} failed to start", run.acara.tema(), run.acara.id(), exception);
			return false;
		}
		TempatEvents.fireAcara(level, run.situsId, run.acara.namaBus(), true);
		festival(level, run, true);
		return true;
	}

	private static void akhiri(ServerLevel level, Jalan run) {
		try {
			run.acara.pelaksana().akhir(level, run.situsId);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Themed event {}/{} failed to end", run.acara.tema(), run.acara.id(), exception);
		}
		TempatEvents.fireAcara(level, run.situsId, run.acara.namaBus(), false);
		festival(level, run, false);
	}

	private static void festival(ServerLevel level, Jalan run, boolean mulai) {
		TemaKota.Kota kota = TemaKota.dari(level.getServer(), run.situsId);
		if (kota == null) {
			return;
		}
		CompoundTag data = new CompoundTag();
		data.putString("acara", run.acara.id());
		data.putBoolean("mulai", mulai);
		data.putLong("tick", run.mulaiTick);
		data.putLong("akhir", run.sampaiTick);
		TemaNet.kirimSekitar(level, Vec3.atCenterOf(kota.pusat()), JARAK_FESTIVAL, TemaNet.FESTIVAL, run.situsId, data);
	}

	/** Window length of an event in ticks (at least 20; a zero-length window = a whole day). */
	private static long panjang(Acara acara) {
		long len = Math.floorMod(acara.sampai() - acara.dari(), 24000L);
		return Math.max(20L, len == 0 ? 24000L : len);
	}

	private static String kunci(long situsId, Acara acara) {
		return situsId + "|" + acara.tema() + "/" + acara.id();
	}

	/** The overworld clock day of {@code level}'s server. */
	public static long hari(ServerLevel level) {
		return Math.floorDiv(level.getServer().overworld().getOverworldClockTime(), 24000L);
	}

	/** The overworld time of day 0-23999. */
	public static long jam(ServerLevel level) {
		return Math.floorMod(level.getServer().overworld().getOverworldClockTime(), 24000L);
	}

	// ------------------------------------------------------------------ day pickers

	/** Every day. */
	public static PemilihHari selalu() {
		return (level, hari, situsId, pusat) -> true;
	}

	/** Days with {@code hari % mod == sisa} (e.g. Hari Dewi Laut {@code setiap(28, 23)}). */
	public static PemilihHari setiap(int mod, int sisa) {
		int m = Math.max(1, mod);
		return (level, hari, situsId, pusat) -> Math.floorMod(hari, m) == Math.floorMod(sisa, m);
	}

	/** Days with {@code dari <= hari % mod <= sampai} (e.g. Musim Ziarah {@code rentang(30, 0, 4)}). */
	public static PemilihHari rentang(int mod, int dari, int sampai) {
		int m = Math.max(1, mod);
		return (level, hari, situsId, pusat) -> {
			long d = Math.floorMod(hari, m);
			return d >= dari && d <= sampai;
		};
	}

	/** Nights of one moon phase at the town centre (the moon is an environment attribute in 26.3). */
	public static PemilihHari bulan(MoonPhase fase) {
		return (level, hari, situsId, pusat) ->
				level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pusat) == fase;
	}

	/** A deterministic daily chance per town ({@code salt} keeps two events independent). */
	public static PemilihHari peluang(double p, long salt) {
		return (level, hari, situsId, pusat) -> {
			long h = hari * 0x9E3779B97F4A7C15L ^ situsId * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
			h ^= h >>> 31;
			h *= 0xBF58476D1CE4E5B9L;
			h ^= h >>> 29;
			return (h >>> 11) * 0x1.0p-53 < p;
		};
	}

	/** Both pickers. */
	public static PemilihHari dan(PemilihHari a, PemilihHari b) {
		return (level, hari, situsId, pusat) -> a.cocok(level, hari, situsId, pusat) && b.cocok(level, hari, situsId, pusat);
	}

	private TemaKalender() {
	}
}
