package dev.kultivator.dunia.kota42.warga;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.JenisHubungan;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.PendudukGen;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hari Warga, the cheap daily pass (DESIGN 42.4.13): once per world day, at the day change (06:00, tick 0), every place
 * that was ever raised gets one pass, one place per server tick in turn ({@link #antrian}). A pass runs the registered
 * steps in order ({@link #addLangkah}): birthdays and weddings ({@link SiklusHidup}), two meetings per resident
 * ({@link Hubungan}), gossip (42K), mood (42A-2 {@code Suasana}), slot filling (42A-2 {@code Pekerjaan}), departures and
 * arrivals (42A-2 {@code Migrasi}), ceremonies (42D), memory decay (42A-2 {@code Ingatan}) and the place statistics.
 * <p>
 * Missed days (server off, a long time skip) are caught up when the place comes round: the last {@code kotaWarga.kejarHari}
 * days in full, the days before that as summaries (only steps marked {@link Langkah#ringkas}: age, births, deaths), at
 * most {@link #HARI_PER_TICK} full or {@link #RINGKAS_PER_TICK} summary days per tick (the place re-queues itself).
 * <p>
 * The first raising ({@link WargaGen#bangkitkan}) happens here too: an active overworld place of a peopled kind whose
 * residents are not stored yet is raised, one per tick.
 */
public final class HariWarga {

	/** One step of the daily pass of a place. Keep it O(residents) and deterministic for the given random. */
	@FunctionalInterface
	public interface Langkah {
		void jalankan(MinecraftServer server, WargaData data, WargaData.Situs s, long hari, RandomSource random);

		/** True when the step also runs on summary days of the catch-up (age, births, deaths only). */
		default boolean ringkas() {
			return false;
		}
	}

	/** Step orders (lower first). */
	public static final int URUT_SIKLUS = 100;
	public static final int URUT_TEMU = 200;
	public static final int URUT_GOSIP = 300;
	public static final int URUT_SUASANA = 400;
	public static final int URUT_KERJA = 500;
	public static final int URUT_PINDAH = 600;
	public static final int URUT_UPACARA = 700;
	public static final int URUT_INGATAN = 800;
	public static final int URUT_STAT = 900;

	/** Catch-up budget of one place per tick. */
	public static final int HARI_PER_TICK = 8;
	public static final int RINGKAS_PER_TICK = 64;
	/** Days of mood history kept in the place statistics (prosperity reads it, 42.9.4). */
	public static final int RIWAYAT = 14;

	private record Terdaftar(int urutan, String nama, Langkah langkah) {
	}

	private static final List<Terdaftar> LANGKAH = new ArrayList<>();
	private static final ArrayDeque<Long> ANTRIAN = new ArrayDeque<>();
	private static final Set<Long> DIANTRE = new HashSet<>();
	private static long hariDilihat = Long.MIN_VALUE;
	/** True while a summary day runs (server thread only). */
	private static boolean sedangRingkas;

	/** Registers a step; lower {@code urutan} runs first, equal orders in registration order. Call once at init. */
	public static synchronized void addLangkah(int urutan, String nama, Langkah langkah) {
		for (Terdaftar t : LANGKAH) {
			if (t.nama().equals(nama)) {
				return;
			}
		}
		int i = 0;
		while (i < LANGKAH.size() && LANGKAH.get(i).urutan() <= urutan) {
			i++;
		}
		LANGKAH.add(i, new Terdaftar(urutan, nama, langkah));
	}

	/** The registered step names in order (commands). */
	public static synchronized List<String> namaLangkah() {
		List<String> out = new ArrayList<>(LANGKAH.size());
		for (Terdaftar t : LANGKAH) {
			out.add(t.urutan() + ":" + t.nama());
		}
		return out;
	}

	/** Places waiting for their pass (commands). */
	public static int antrian() {
		return ANTRIAN.size();
	}

	/** True while the running pass is a summary day of the catch-up (steps that run on summaries may skip details). */
	public static boolean sedangRingkas() {
		return sedangRingkas;
	}

	/** Forgets the queue (server stop). */
	public static void lupakan() {
		ANTRIAN.clear();
		DIANTRE.clear();
		hariDilihat = Long.MIN_VALUE;
	}

	// ================================================================== the tick

	/** End of every server tick (registered by {@code KotaWarga}). */
	public static void tick(MinecraftServer server) {
		if (!KotaConfig.warga().aktif || server.overworld() == null) {
			return;
		}
		WargaData data = WargaData.get(server);
		long hari = KotaHidup.hari(server);
		List<TempatHuni> aktif = Pemanggil.semua();
		boolean hariBaru = hari != hariDilihat;
		for (TempatHuni huni : aktif) {
			if (!dihuni(huni)) {
				continue;
			}
			WargaData.Situs s = data.peek(huni.situsId);
			if (s == null || !s.bangkit()) {
				bangkit(server, data, huni, hari);
				break;
			}
			if (hariBaru) {
				catatTitik(s, huni);
			}
		}
		if (hariBaru) {
			hariDilihat = hari;
			for (WargaData.Situs s : data.semuaSitus()) {
				if (s.bangkit() && s.hariTerakhir < hari) {
					antre(s.id);
				}
			}
		}
		Long id = ANTRIAN.poll();
		if (id == null) {
			return;
		}
		DIANTRE.remove(id);
		WargaData.Situs s = data.peek(id);
		if (s == null || !s.bangkit()) {
			return;
		}
		try {
			if (!kejar(server, data, s, hari)) {
				antre(id);
			}
		} catch (RuntimeException exception) {
			// one broken place must never stop the others (nor the server tick)
			Kultivator.LOGGER.error("Hari Warga failed for place {}", id, exception);
			s.hariTerakhir = hari;
		}
	}

	private static void antre(long id) {
		if (DIANTRE.add(id)) {
			ANTRIAN.add(id);
		}
	}

	/** True for a place 42 gives stored residents: overworld, a peopled kind, not an NPC sect compound (41 owns those). */
	public static boolean dihuni(TempatHuni huni) {
		return huni.dimensi == Level.OVERWORLD && PendudukGen.dihuni(huni.jenis) && !huni.jenis.startsWith("sekte/");
	}

	/** Raises an active place now (also {@code uji kota_hidup warga daftar} after a reset). */
	public static List<Warga> bangkit(MinecraftServer server, WargaData data, TempatHuni huni, long hari) {
		List<Warga> made = WargaGen.bangkitkan(data, huni.situsId, huni.penduduk(), huni.jumlahAsli(), hari, server.overworld().getSeed());
		catatTitik(data.situs(huni.situsId), huni);
		return made;
	}

	/** Caches the place's gravestone count (titik {@code nisan_<n>}) for deaths while it is not loaded. */
	private static void catatTitik(WargaData.Situs s, TempatHuni huni) {
		int n = 0;
		for (String type : huni.ringkasTitik().keySet()) {
			if (type.startsWith("nisan_")) {
				n++;
			}
		}
		if (n > 0 || !s.stat.contains("nisanN")) {
			s.stat.putInt("nisanN", n);
		}
	}

	/**
	 * Brings place {@code s} up to {@code hari}: the missed days beyond {@code kejarHari} as summaries, the rest in full.
	 * Returns true when done, false when the tick budget ran out (the caller queues the place again).
	 */
	static boolean kejar(MinecraftServer server, WargaData data, WargaData.Situs s, long hari) {
		int kejarHari = Math.max(1, KotaConfig.warga().kejarHari);
		int penuh = 0;
		int ringkas = 0;
		while (s.hariTerakhir < hari) {
			long next = s.hariTerakhir + 1L;
			boolean summary = hari - next >= kejarHari;
			if (summary ? ringkas >= RINGKAS_PER_TICK : penuh >= HARI_PER_TICK) {
				return false;
			}
			jalankan(server, data, s, next, summary);
			if (summary) {
				ringkas++;
			} else {
				penuh++;
			}
		}
		return true;
	}

	/** One pass of every step for place {@code s} on world day {@code hari}. */
	public static void jalankan(MinecraftServer server, WargaData data, WargaData.Situs s, long hari, boolean ringkas) {
		RandomSource random = RandomSource.create(WargaGen.mix(server.overworld().getSeed() ^ s.id * 0x9E3779B97F4A7C15L ^ hari * 0xD1B54A32D192ED03L));
		List<Terdaftar> steps;
		synchronized (HariWarga.class) {
			steps = List.copyOf(LANGKAH);
		}
		sedangRingkas = ringkas;
		try {
			for (Terdaftar t : steps) {
				if (ringkas && !t.langkah().ringkas()) {
					continue;
				}
				try {
					t.langkah().jalankan(server, data, s, hari, random);
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("Hari Warga step {} failed for place {}", t.nama(), s.id, exception);
				}
			}
		} finally {
			sedangRingkas = false;
		}
		s.hariTerakhir = Math.max(s.hariTerakhir, hari);
		data.ubah();
	}

	// ================================================================== built-in steps (registered by KotaWarga)

	/** Step {@value #URUT_SIKLUS}: weddings and birthdays (runs on summary days too). */
	static final Langkah SIKLUS = new Langkah() {
		@Override
		public void jalankan(MinecraftServer server, WargaData data, WargaData.Situs s, long hari, RandomSource random) {
			SiklusHidup.langkahHari(server, data, s, hari, random, sedangRingkas);
		}

		@Override
		public boolean ringkas() {
			return true;
		}
	};

	/** Step {@value #URUT_TEMU}: two meetings per resident. */
	static final Langkah TEMU = (server, data, s, hari, random) -> {
		Map<Integer, Integer> umur = new HashMap<>();
		for (Warga w : s.warga) {
			umur.put(w.id, SiklusHidup.umurPada(w, hari));
		}
		Hubungan.temuHarian(data, s.warga, umur, hari, random);
	};

	/** Step {@value #URUT_STAT}: population, the day's mean mood and its 14-day history, roots, crowding. */
	static final Langkah STAT = (server, data, s, hari, random) -> {
		int hidup = 0;
		int jalan = 0;
		int bayi = 0;
		int anak = 0;
		int tua = 0;
		int berakar = 0;
		int sakit = 0;
		long suasana = 0L;
		for (Warga w : s.warga) {
			if (w.status == Warga.Status.JALAN) {
				jalan++;
				continue;
			}
			if (w.status != Warga.Status.HIDUP) {
				continue;
			}
			hidup++;
			suasana += w.suasana;
			int u = SiklusHidup.umurPada(w, hari);
			if (u < 3) {
				bayi++;
			} else if (u <= 12) {
				anak++;
			} else if (u >= 55) {
				tua++;
			}
			if (w.akar > 0) {
				berakar++;
			}
			if (w.kesehatan < 30) {
				sakit++;
			}
		}
		CompoundTag st = s.stat;
		st.putInt("pop", hidup);
		st.putInt("jalan", jalan);
		st.putInt("bayi", bayi);
		st.putInt("anak", anak);
		st.putInt("tua", tua);
		st.putInt("berakar", berakar);
		st.putInt("sakit", sakit);
		int rata = hidup == 0 ? 0 : (int) (suasana / hidup);
		st.putInt("rata", rata);
		int[] old = st.getIntArray("riwayat").orElse(new int[0]);
		int[] next = new int[Math.min(RIWAYAT, old.length + 1)];
		System.arraycopy(old, Math.max(0, old.length - (next.length - 1)), next, 0, next.length - 1);
		next[next.length - 1] = rata;
		st.putIntArray("riwayat", next);
	};

	// ================================================================== test helpers

	/**
	 * Runs {@code n} days of place {@code s} now and shifts every date back by {@code n} afterwards, so the residents end
	 * up {@code n} days older on the real calendar with every event of those days recorded (never changes world time;
	 * {@code uji kota_hidup warga hari/tahun}). Returns the number of days run.
	 */
	public static int majukan(MinecraftServer server, WargaData data, WargaData.Situs s, int n) {
		long hari = KotaHidup.hari(server);
		kejar(server, data, s, hari);
		while (s.hariTerakhir < hari) {
			jalankan(server, data, s, s.hariTerakhir + 1L, false);
		}
		for (int i = 1; i <= n; i++) {
			jalankan(server, data, s, hari + i, false);
		}
		geser(s, n);
		s.hariTerakhir = hari;
		data.ubah();
		return n;
	}

	/** Moves every stored date of place {@code s} {@code n} days into the past. */
	static void geser(WargaData.Situs s, int n) {
		for (Warga w : s.warga) {
			w.lahir -= n * 10L;
			if (w.menikahHari >= 0L) {
				w.menikahHari -= n;
			}
			if (w.jandaHari >= 0L) {
				w.jandaHari -= n;
			}
			if (w.tunangan != Warga.TANPA) {
				w.hariNikah -= n;
			}
			if (w.datang >= 0L) {
				w.datang -= n;
			}
			if (w.hariStatus != 0L) {
				w.hariStatus -= n;
			}
			if (w.status == Warga.Status.JALAN) {
				w.tiba -= n;
			}
			for (int i = 0; i < w.hubungan.length; i++) {
				long b = w.hubungan[i];
				JenisHubungan jenis = JenisHubungan.jenis(b);
				if (jenis != null) {
					w.hubungan[i] = JenisHubungan.kemas(JenisHubungan.idLain(b), jenis, JenisHubungan.kekuatan(b), JenisHubungan.sejak(b) - n);
				}
			}
		}
		ListTag arsip = s.arsip;
		for (int i = 0; i < arsip.size(); i++) {
			CompoundTag t = arsip.getCompoundOrEmpty(i);
			t.putLong("l", t.getLongOr("l", 0L) - n * 10L);
			t.putLong("w", t.getLongOr("w", 0L) - n);
		}
		long[] wafat = s.stat.getLongArray("wafat").orElse(new long[0]);
		for (int i = 0; i < wafat.length; i++) {
			long day = (wafat[i] & 0xFFFFFFFFL) - n;
			wafat[i] = (wafat[i] & 0xFFFFFFFF00000000L) | (day & 0xFFFFFFFFL);
		}
		s.stat.putLongArray("wafat", wafat);
		CompoundTag duka = s.stat.getCompoundOrEmpty("duka");
		for (String key : duka.keySet()) {
			duka.putLong(key, duka.getLongOr(key, 0L) - n);
		}
	}

	private HariWarga() {
	}
}
