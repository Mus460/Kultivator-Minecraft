package dev.kultivator.dunia.kota42.ragam;

import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.cakap.Kepribadian;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.PendudukData;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.hidup.TubuhPenduduk;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * A resident's plan for one day, different every day (DESIGN 42.4A.4, contract C42.S1): made lazily on the first evaluation
 * of the day (seed {@code warga ^ hari}); each block of the role schedule gets 2-4 activities of the role's pool
 * ({@code tempat/ragam/peran/<peran>.json}) of 20-90 world minutes, weighted by traits, habits, season, weather, repeats
 * today ({@code 0.25^n}) and yesterday ({@code kotaWarga.ragamPenaltiKemarin}), mood; core work keeps >= 50 % of work time.
 * Week days 0 (big market), 3 (livestock / fish), 6 (rest) and festivals reshape it ({@link IramaMingguan}). Applied as the
 * schedule overrider of order {@link Jadwal#URUT_RENCANA} (after events, ceremonies and habits, before {@code ramai}).
 * <p>
 * How the blocks are found: the role's round ({@link Jadwal#kegiatan}, dry weather, no event) is sampled every ten minutes
 * of the day and each sample classified ({@link #blok}): the role's work, a meal, free time, the evening; sleep, indoors,
 * waking, the wash, opening and closing the stall, school, ceremonies of the round and the themed towns' own routines are
 * never touched. At run time the round is classified again with the real weather: when it no longer matches the planned
 * block (rain sent everyone under a roof) the round stands. Core work planned with the role's own clip ({@code @peran})
 * also lets the round stand, so the shop keeps its calls, trades and hourly rhythm.
 * <p>
 * Yesterday's plan is rebuilt (without its own yesterday) instead of stored: no save data, same answer on every machine.
 */
public final class RencanaHarian {

	/** Samples of the round per day (every ten world minutes). */
	public static final int SAMPEL = 144;
	/** Longest block before it is split (world minutes): "kerja 07-12", "bebas 13-16". */
	private static final int BLOK_MAKS = 300;

	/**
	 * One planned activity.
	 *
	 * @param kegiatan  the activity key ({@code kultivator.hidup.kegiatan.<key>})
	 * @param mulai     start, world minutes after 06:00 (place time, 0-1439)
	 * @param akhir     end (exclusive)
	 * @param titik     the goal ({@link Kegiatan#tujuan})
	 * @param blok      {@link KumpulanKegiatan.Blok#id()}
	 * @param jenis     {@code inti} (core work), {@code ragam} (variation) or {@code belokan} (a spontaneous turn)
	 * @param sig       activity signature (metrics)
	 * @param isi       the activity to play, null = the round stands
	 * @param aktivitas the pool entry (null for a turn or an empty pool)
	 * @param belok     the turn's entry (null unless {@code belokan})
	 */
	public record Langkah(String kegiatan, int mulai, int akhir, String titik, String blok, String jenis, int sig, @Nullable Kegiatan isi,
			KumpulanKegiatan.@Nullable Aktivitas aktivitas, KumpulanKegiatan.@Nullable BelokanDef belok) {

		public int lama() {
			return akhir - mulai;
		}

		/** A copy over another span. */
		Langkah rentang(int dari, int sampai) {
			return new Langkah(kegiatan, dari, sampai, titik, blok, jenis, sig, isi, aktivitas, belok);
		}
	}

	/** One day's plan of one resident ({@code warga} = the Warga id, or {@code -1 - slot} without a stored person). */
	public record Rencana(int warga, long hari, List<Langkah> langkah) {

		/** The step running at {@code menit} (minutes after 06:00), or null. */
		public @Nullable Langkah pada(int menit) {
			for (Langkah l : langkah) {
				if (menit >= l.mulai() && menit < l.akhir()) {
					return l;
				}
			}
			return null;
		}

		/** Index of the step at {@code menit}, -1 when none. */
		public int indeks(int menit) {
			for (int i = 0; i < langkah.size(); i++) {
				if (menit >= langkah.get(i).mulai() && menit < langkah.get(i).akhir()) {
					return i;
				}
			}
			return -1;
		}

		/** Different activities planned (core work counted once). */
		public int ragam() {
			Set<Integer> s = new HashSet<>();
			for (Langkah l : langkah) {
				s.add(l.sig());
			}
			return s.size();
		}
	}

	private record Entri(long hari, Rencana rencana) {
	}

	/** Plans by place, then resident slot (today's; yesterday's are rebuilt when needed). */
	private static final Map<Long, Map<Integer, Entri>> CACHE = new HashMap<>();
	/** Plans made per place per day {@code (situs, hari) -> n} (metrics); the last 3 days. */
	private static final Map<Long, Map<Long, Integer>> DIBUAT = new HashMap<>();
	private static long hariCache = Long.MIN_VALUE / 2;
	private static boolean initialised;

	/**
	 * Registers the daily plan (overrider {@link Jadwal#URUT_RENCANA}), the pools' reload listener, the micro interludes
	 * ({@link Selingan}) and the goal hooks of turns and groups ({@link Belokan}, {@link KegiatanKelompok}). Called from
	 * {@code KotaRagam.init}; safe to call twice.
	 */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		KumpulanKegiatan.register();
		Jadwal.addPenimpa(Jadwal.URUT_RENCANA, RencanaHarian::timpa);
		TubuhPenduduk.addSelingan(Selingan::selingan);
		TempatHuni.addPenyeimbang(KegiatanKelompok::pilih);
		TempatHuni.addPenyeimbang(Belokan::pilih);
	}

	// ================================================================== the overrider

	/** World minutes after 06:00 of overworld time {@code waktu} (place time, 0-1439). */
	public static int menit(long waktu) {
		return (int) (Math.floorMod(waktu, 24000L) * 1440L / 24000L);
	}

	/** {@link Jadwal.PenimpaJadwal}: the planned activity, or null to leave the round (and {@code ramai}). */
	public static @Nullable Kegiatan timpa(TempatHuni huni, Penduduk p, long waktu, int jam, long now) {
		if (!KotaConfig.warga().sebaranAktif || p.tamu() || huni.keadaanDari(p.id()) != PendudukData.SEHAT) {
			return null;
		}
		Jadwal.Cuaca cuaca = huni.cuaca();
		Kegiatan round = Jadwal.kegiatan(p, huni.jenis, waktu, cuaca, null);
		KumpulanKegiatan.Blok blok = blok(round, jam);
		if (blok == null) {
			return null;
		}
		long hari = Math.floorDiv(waktu, 24000L);
		int m = menit(waktu);
		Kegiatan kelompok = KegiatanKelompok.timpa(huni, p, hari, m, blok);
		if (kelompok != null) {
			return kelompok;
		}
		Rencana r = rencanaHemat(huni, p, hari, now);
		if (r == null) {
			// this tick's planning budget is spent (dawn in a big city): the round stands for a moment
			return null;
		}
		int i = r.indeks(m);
		if (i < 0) {
			return null;
		}
		Langkah l = r.langkah().get(i);
		if (!l.blok().equals(blok.id())) {
			return null;
		}
		if (l.belok() != null) {
			Kegiatan turn = Belokan.terapkan(huni, p, r, i, hari);
			if (turn != null) {
				return turn;
			}
			// no attraction near the route after all: the next step starts early
			l = i + 1 < r.langkah().size() ? r.langkah().get(i + 1) : null;
			if (l == null || !l.blok().equals(blok.id())) {
				return null;
			}
		}
		return isi(l, cuaca, m, p);
	}

	/** The step's activity as played now: null for the round, a rotating turn number for strolls and stall fronts. */
	static @Nullable Kegiatan isi(Langkah l, Jadwal.Cuaca cuaca, int menit, Penduduk p) {
		Kegiatan k = l.isi();
		if (k == null) {
			return null;
		}
		KumpulanKegiatan.Aktivitas a = l.aktivitas();
		if ((cuaca.hujan() || cuaca.badai()) && a != null && (a.ada("luar") || a.syarat().cuaca() == 1) && !a.inti()) {
			return null;
		}
		if (k.tujuan().equals("@jalan") || k.tujuan().equals("@depan_lapak")) {
			// a new street spot or stall every twenty minutes, so the street sees people walking place to place
			return k.ke(k.tujuan() + ":" + (menit / 20 + p.id() * 3));
		}
		return k;
	}

	// ================================================================== blocks

	/** Keys of the round that are the role's work. */
	private static final Set<String> KERJA = Set.of("kerja", "angkut", "sidang", "pasar", "patroli", "pasar_malam", "hitung_malam",
			"pasar_ikan", "bazar", "pasar_bulan", "pasar_air", "pasar_gelap", "patroli_siang", "patroli_zona", "patroli_lentera", "tonton_arena");
	/** True for the round keys that are a role's core work (the metrics leave them out of the repeat count). */
	static boolean kunciInti(String kunci) {
		return KERJA.contains(kunci) || INTI_TEMA.contains(kunci);
	}

	/** Duty loops of the themed towns' own routines (JadwalTema): the plan leaves them alone, the metrics call them core work. */
	private static final Set<String> INTI_TEMA = Set.of("ikat_tali", "teropong", "jaga_suar", "tabuh_ikan_kayu", "sapu_kuil", "sapu_biara",
			"doa_subuh", "doa_sore", "doa_senja", "meditasi", "meditasi_malam", "semedi", "ziarah", "menulis");

	/** Keys of the round that are free time the plan may fill. */
	private static final Set<String> SANTAI = Set.of("obrol", "serambi", "minum_teh", "catur", "memancing", "beri_pakan", "bercerita",
			"musik", "kedai", "main", "main_rumah", "bantu", "jalan_jalan", "pandang", "main_air", "teh", "belanja_sore", "lihat_kota", "belanja", "nyanyi_pelaut",
			// the themed towns' evening and rest-hour routines (JadwalTema): leisure the plan may vary
			"semedi", "berdebat", "kedai_racun", "ke_arena", "pesta_kedai", "api_unggun", "karavanserai", "tamu_teh", "teh_jendela",
			"tatap_bulan", "tatap_laut", "obrol_jembatan", "mancing_jembatan", "menyimak_hujan");

	/**
	 * The block a round's activity belongs to at the resident's hour {@code jam}, or null when the plan must not touch it
	 * (sleep, indoors, waking and washing, the stall opening or closing, school, the round's ceremonies and the themed
	 * towns' own routines).
	 */
	public static KumpulanKegiatan.@Nullable Blok blok(Kegiatan round, int jam) {
		if (round.dalam()) {
			return null;
		}
		String k = round.kunci();
		if (KERJA.contains(k)) {
			return KumpulanKegiatan.Blok.KERJA;
		}
		if (k.equals("makan")) {
			return KumpulanKegiatan.Blok.MAKAN;
		}
		if (SANTAI.contains(k)) {
			return jam >= 19 || jam < 5 ? KumpulanKegiatan.Blok.MALAM : KumpulanKegiatan.Blok.BEBAS;
		}
		return null;
	}

	// ================================================================== the plan

	/** The plan of {@code warga} for world day {@code hari}, or null when its place is not active (or it has no slot). */
	public static @Nullable Rencana hari(Warga warga, long hari) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || warga.slot < 0) {
			return null;
		}
		TempatHuni huni = Pemanggil.huni(WargaData.get(server).situsDari(warga.id));
		Penduduk p = huni == null ? null : huni.orang(warga.slot);
		return p == null ? null : rencana(huni, p, hari);
	}

	/** The plan of resident {@code p} of {@code huni} for world day {@code hari} (cached for the day). */
	public static Rencana rencana(TempatHuni huni, Penduduk p, long hari) {
		if (hari != hariCache) {
			hariCache = hari;
			for (Map<Integer, Entri> per : CACHE.values()) {
				per.values().removeIf(e -> e.hari() < hari - 1L);
			}
			CACHE.values().removeIf(Map::isEmpty);
		}
		Map<Integer, Entri> per = CACHE.computeIfAbsent(huni.situsId, s -> new HashMap<>());
		Entri e = per.get(p.id());
		if (e == null || e.hari() != hari) {
			Warga w = WargaApi.warga(huni.situsId, p.id());
			Rencana kemarin = buat(huni, p, w, hari - 1L, null);
			e = new Entri(hari, buat(huni, p, w, hari, kemarin));
			per.put(p.id(), e);
			catatDibuat(huni.situsId, hari);
		}
		return e.rencana();
	}

	/** Plans built per game tick at most from the overrider (each samples the round 288 times, today and yesterday). */
	private static final int BUAT_PER_TIK = 16;
	private static long tikBuat = Long.MIN_VALUE / 2;
	private static int buatTik;

	/**
	 * The cached plan, or a fresh one while this tick's budget lasts ({@value #BUAT_PER_TIK} builds), else null: at dawn a
	 * capital of 650 residents is planned over a couple of seconds instead of in one tick.
	 */
	static @Nullable Rencana rencanaHemat(TempatHuni huni, Penduduk p, long hari, long now) {
		Map<Integer, Entri> per = CACHE.get(huni.situsId);
		Entri e = per == null ? null : per.get(p.id());
		if (e != null && e.hari() == hari) {
			return e.rencana();
		}
		if (now != tikBuat) {
			tikBuat = now;
			buatTik = 0;
		}
		if (buatTik >= BUAT_PER_TIK) {
			return null;
		}
		buatTik++;
		return rencana(huni, p, hari);
	}

	/** Plans made in place {@code situs} on day {@code hari} (metrics). */
	public static int dibuat(long situs, long hari) {
		Map<Long, Integer> per = DIBUAT.get(situs);
		return per == null ? 0 : per.getOrDefault(hari, 0);
	}

	private static void catatDibuat(long situs, long hari) {
		Map<Long, Integer> per = DIBUAT.computeIfAbsent(situs, s -> new HashMap<>());
		per.merge(hari, 1, Integer::sum);
		per.keySet().removeIf(h -> h < hari - 3L);
	}

	/** Drops the cached plans of a place (its residents changed). */
	public static void lupakan(long situs) {
		CACHE.remove(situs);
	}

	/** Everything a plan is weighed by. */
	private record Konteks(TempatHuni huni, Penduduk p, @Nullable Warga w, long hari, int pekan, int musim, boolean libur,
			Kepribadian.Sifat sifat, int suasana, Set<Long> kemarin, Map<Integer, Integer> hariIni, SplittableRandom acak) {
	}

	/** Builds the plan of day {@code hari}; {@code kemarin} = yesterday's plan (null while building yesterday itself). */
	static Rencana buat(TempatHuni huni, Penduduk p, @Nullable Warga w, long hari, @Nullable Rencana kemarin) {
		long seed = (w != null ? w.id : p.benih() ^ huni.situsId * 31L) ^ hari * 0x9E3779B97F4A7C15L;
		Set<Long> lalu = new HashSet<>();
		if (kemarin != null) {
			for (Langkah l : kemarin.langkah()) {
				if (l.jenis().equals("ragam")) {
					lalu.add((long) l.blok().hashCode() << 32 ^ (l.sig() & 0xFFFFFFFFL));
				}
			}
		}
		Konteks c = new Konteks(huni, p, w, hari, IramaMingguan.pekan(hari), IramaMingguan.musim(hari), IramaMingguan.libur(p, hari),
				sifat(w, p), w != null ? w.suasana : 0, lalu, new HashMap<>(), new SplittableRandom(seed));
		List<int[]> segmen = segmen(huni, p, hari);
		List<Langkah> out = new ArrayList<>();
		for (int[] s : segmen) {
			KumpulanKegiatan.Blok blok = KumpulanKegiatan.Blok.values()[s[0]];
			List<KumpulanKegiatan.Aktivitas> pool = saring(KumpulanKegiatan.untuk(p.peran(), huni.jenis, blok), c, s[1], s[2]);
			if (blok == KumpulanKegiatan.Blok.KERJA) {
				isiKerja(out, pool, blok, s[1], s[2], c);
			} else {
				isiBebas(out, pool, blok, s[1], s[2], c);
			}
		}
		if (kemarin != null) {
			Belokan.sisip(huni, p, w, out, c.acak(), hari);
		}
		return new Rencana(w != null ? w.id : -1 - p.id(), hari, List.copyOf(out));
	}

	/** Runs of one block in the sampled round: {@code {blok ordinal, start minute, end minute}}, long runs split. */
	private static List<int[]> segmen(TempatHuni huni, Penduduk p, long hari) {
		int[] slot = new int[SAMPEL];
		long awal = hari * 24000L;
		for (int i = 0; i < SAMPEL; i++) {
			long waktu = awal + (long) i * 24000L / SAMPEL;
			Kegiatan round = Jadwal.kegiatan(p, huni.jenis, waktu, Jadwal.Cuaca.CERAH, null);
			KumpulanKegiatan.Blok b = blok(round, Jadwal.saat(p, huni.jenis, waktu).jam());
			slot[i] = b == null ? -1 : b.ordinal();
		}
		List<int[]> out = new ArrayList<>();
		int i = 0;
		while (i < SAMPEL) {
			int b = slot[i];
			int j = i;
			while (j < SAMPEL && slot[j] == b) {
				j++;
			}
			if (b >= 0) {
				int dari = i * 10;
				int sampai = j * 10;
				int bagian = Math.max(1, (sampai - dari + BLOK_MAKS - 1) / BLOK_MAKS);
				for (int k = 0; k < bagian; k++) {
					int a = dari + (sampai - dari) * k / bagian;
					int z = dari + (sampai - dari) * (k + 1) / bagian;
					out.add(new int[]{b, a, z});
				}
			}
			i = j;
		}
		return out;
	}

	/** The activities of the pool allowed today in a block from {@code dari} to {@code sampai} (minutes after 06:00). */
	private static List<KumpulanKegiatan.Aktivitas> saring(List<KumpulanKegiatan.Aktivitas> pool, Konteks c, int dari, int sampai) {
		List<KumpulanKegiatan.Aktivitas> out = new ArrayList<>();
		int jamDari = (dari / 60 + 6) % 24;
		int jamSampai = ((sampai - 1) / 60 + 6) % 24;
		boolean hujan = c.huni().cuaca().hujan() || c.huni().cuaca().badai();
		boolean dimuat = c.huni().jumlahTitik() >= 24;
		boolean tetap = c.p().peran().stationary;
		for (KumpulanKegiatan.Aktivitas a : pool) {
			KumpulanKegiatan.Syarat s = a.syarat();
			if (!s.musim(c.musim()) || !s.hari(c.pekan()) || !s.jam(jamDari, jamSampai) || s.cuaca() == 1 && hujan || s.cuaca() == 2 && !hujan) {
				continue;
			}
			if (!s.kebiasaan().isEmpty() && (c.w() == null || s.kebiasaan().stream().noneMatch(h -> Kepribadian.punya(c.w(), h)))) {
				continue;
			}
			if (tetap && (a.blok() == KumpulanKegiatan.Blok.KERJA || a.blok() == KumpulanKegiatan.Blok.MAKAN) && !a.ada("di_tempat") && !a.tujuan().equals(Kegiatan.KERJA)
					&& !a.tujuan().equals(Kegiatan.TETAP)) {
				// a stall keeper never leaves the stall in working hours, not even for lunch
				continue;
			}
			if (dimuat && !a.tujuan().startsWith("@") && c.huni().titik(a.tujuan()).isEmpty()) {
				// the place has no such spot (no well, no fishing pier): skip rather than send it home
				continue;
			}
			out.add(a);
		}
		return out;
	}

	/** A work block: 2-4 variations within the variation share, the rest core work spread between them (>= 50 %). */
	private static void isiKerja(List<Langkah> out, List<KumpulanKegiatan.Aktivitas> pool, KumpulanKegiatan.Blok blok, int dari,
			int sampai, Konteks c) {
		List<KumpulanKegiatan.Aktivitas> inti = new ArrayList<>();
		List<KumpulanKegiatan.Aktivitas> ragam = new ArrayList<>();
		for (KumpulanKegiatan.Aktivitas a : pool) {
			(a.inti() ? inti : ragam).add(a);
		}
		int lama = sampai - dari;
		double share = 0.30D + 0.15D * c.sifat().o() / 100.0D - 0.10D * c.sifat().c() / 100.0D;
		if (c.p().peran().stationary) {
			share = Math.min(share, 0.25D);
		}
		if (c.libur()) {
			share = 0.6D;
		}
		int sisa = (int) Math.round(lama * Math.max(0.15D, Math.min(c.libur() ? 0.6D : 0.45D, share)));
		int target = 2 + c.acak().nextInt(3);
		List<KumpulanKegiatan.Aktivitas> pilihan = new ArrayList<>();
		List<Integer> durasi = new ArrayList<>();
		KumpulanKegiatan.Aktivitas sebelum = null;
		for (int n = 0; n < target && lama >= 40; n++) {
			KumpulanKegiatan.Aktivitas a = undi(ragam, blok, c, sebelum);
			if (a == null) {
				break;
			}
			int d = Math.min(a.durMax(), a.durMin() + c.acak().nextInt(a.durMax() - a.durMin() + 1));
			if (d > sisa) {
				if (a.durMin() > sisa) {
					break;
				}
				d = sisa;
			}
			pilihan.add(a);
			durasi.add(d);
			sisa -= d;
			c.hariIni().merge(a.sig(), 1, Integer::sum);
			sebelum = a;
		}
		int ragamLama = 0;
		for (int d : durasi) {
			ragamLama += d;
		}
		int potong = pilihan.size() + 1;
		int intiLama = lama - ragamLama;
		int t = dari;
		for (int k = 0; k < potong; k++) {
			int d = intiLama / potong + (k == 0 ? intiLama % potong : 0);
			if (d >= 5) {
				KumpulanKegiatan.Aktivitas a = undi(inti, blok, c, null);
				out.add(langkah(a, t, t + d, blok, "inti"));
				if (a != null) {
					c.hariIni().merge(a.sig(), 1, Integer::sum);
				}
				t += d;
			} else if (!out.isEmpty() && k > 0) {
				// a sliver of core work: the previous step runs a little longer
				Langkah prev = out.removeLast();
				out.add(prev.rentang(prev.mulai(), prev.akhir() + d));
				t += d;
			} else {
				t += d;
			}
			if (k < pilihan.size()) {
				out.add(langkah(pilihan.get(k), t, t + durasi.get(k), blok, "ragam"));
				t += durasi.get(k);
			}
		}
		if (t != sampai && !out.isEmpty()) {
			Langkah last = out.removeLast();
			out.add(last.rentang(last.mulai(), sampai));
		}
	}

	/** A free, meal or evening block: 1-4 activities (2-4 when it is long enough), scaled to fill the block. */
	private static void isiBebas(List<Langkah> out, List<KumpulanKegiatan.Aktivitas> pool, KumpulanKegiatan.Blok blok, int dari,
			int sampai, Konteks c) {
		int lama = sampai - dari;
		if (pool.isEmpty()) {
			out.add(langkah(null, dari, sampai, blok, "inti"));
			return;
		}
		int n = lama < 30 ? 1 : lama < 60 ? 1 + c.acak().nextInt(2) : 2 + c.acak().nextInt(3);
		n = Math.max(1, Math.min(n, lama / 15));
		List<KumpulanKegiatan.Aktivitas> pilihan = new ArrayList<>();
		List<Integer> mentah = new ArrayList<>();
		KumpulanKegiatan.Aktivitas sebelum = null;
		int jumlah = 0;
		for (int k = 0; k < n; k++) {
			KumpulanKegiatan.Aktivitas a = undi(pool, blok, c, sebelum);
			if (a == null) {
				break;
			}
			int d = a.durMin() + c.acak().nextInt(a.durMax() - a.durMin() + 1);
			pilihan.add(a);
			mentah.add(d);
			jumlah += d;
			c.hariIni().merge(a.sig(), 1, Integer::sum);
			sebelum = a;
		}
		if (pilihan.isEmpty()) {
			out.add(langkah(null, dari, sampai, blok, "inti"));
			return;
		}
		int t = dari;
		for (int k = 0; k < pilihan.size(); k++) {
			int d = k == pilihan.size() - 1 ? sampai - t : Math.max(10, (int) Math.round((double) mentah.get(k) * lama / jumlah));
			d = Math.min(d, sampai - t);
			if (d <= 0) {
				break;
			}
			KumpulanKegiatan.Aktivitas a = pilihan.get(k);
			out.add(langkah(a, t, t + d, blok, a.inti() ? "inti" : "ragam"));
			t += d;
		}
	}

	private static Langkah langkah(KumpulanKegiatan.@Nullable Aktivitas a, int dari, int sampai, KumpulanKegiatan.Blok blok, String jenis) {
		if (a == null) {
			return new Langkah("kerja", dari, sampai, Kegiatan.KERJA, blok.id(), "inti", ("peran/" + blok.id()).hashCode(), null, null, null);
		}
		Kegiatan isi = a.ikutPeran() ? null : a.kegiatan();
		return new Langkah(a.kunci(), dari, sampai, a.tujuan(), blok.id(), jenis, a.sig(), isi, a, null);
	}

	/** A weighted draw (42.4A.4 weights); never the same activity twice in a row. */
	private static KumpulanKegiatan.@Nullable Aktivitas undi(List<KumpulanKegiatan.Aktivitas> pool, KumpulanKegiatan.Blok blok, Konteks c,
			KumpulanKegiatan.@Nullable Aktivitas sebelum) {
		if (pool.isEmpty()) {
			return null;
		}
		double[] w = new double[pool.size()];
		double total = 0.0D;
		for (int i = 0; i < w.length; i++) {
			KumpulanKegiatan.Aktivitas a = pool.get(i);
			// never the same twice in a row, and outside core work never more than twice a day (42.4A.8 "<= 2 kali")
			boolean habis = !a.inti() && c.hariIni().getOrDefault(a.sig(), 0) >= 2;
			w[i] = a == sebelum && pool.size() > 1 || habis ? 0.0D : bobot(a, blok, c);
			total += w[i];
		}
		if (total <= 0.0D) {
			return null;
		}
		double r = c.acak().nextDouble() * total;
		for (int i = 0; i < w.length; i++) {
			r -= w[i];
			if (r < 0.0D) {
				return pool.get(i);
			}
		}
		return pool.getLast();
	}

	/** The weight of one activity for one resident today (42.4A.4). */
	static double bobot(KumpulanKegiatan.Aktivitas a, KumpulanKegiatan.Blok blok, Konteks c) {
		Kepribadian.Sifat s = c.sifat();
		double w = a.bobot();
		if (a.ada("sosial")) {
			w *= Math.max(0.1D, 1.0D + s.x() / 100.0D);
		}
		if (a.inti()) {
			w *= Math.max(0.1D, 1.0D + s.c() / 150.0D);
		}
		if (a.ada("jarang")) {
			w *= Math.max(0.1D, 1.0D + s.o() / 100.0D);
		}
		w *= kebiasaan(a, c.w());
		w *= IramaMingguan.bobot(a, c.hari(), c.libur());
		Jadwal.Cuaca cuaca = c.huni().cuaca();
		if ((cuaca.hujan() || cuaca.badai()) && !a.inti()) {
			if (a.ada("luar")) {
				w *= 0.2D;
			}
			if (a.ada("teduh")) {
				w *= 1.5D;
			}
		}
		w *= Math.pow(0.25D, c.hariIni().getOrDefault(a.sig(), 0));
		if (c.kemarin().contains((long) blok.id().hashCode() << 32 ^ (a.sig() & 0xFFFFFFFFL))) {
			w *= KotaConfig.warga().ragamPenaltiKemarin;
		}
		if (c.suasana() < -30) {
			// sad: keeps to itself
			w *= a.ada("sendiri") ? 1.6D : a.ada("sosial") ? 0.6D : 1.0D;
		} else if (c.suasana() > 30) {
			w *= a.ada("sosial") ? 1.4D : a.ada("sendiri") ? 0.8D : 1.0D;
		}
		return Math.max(0.0D, w);
	}

	/** Habit (42.4.12) -> fragments of activity ids it favours (x2.5). */
	private static final Map<String, List<String>> KEBIASAAN = Map.ofEntries(
			Map.entry("pemabuk", List.of("kedai", "arak", "dadu")),
			Map.entry("kakek_catur", List.of("catur")),
			Map.entry("gosip_sumur", List.of("sumur", "obrol")),
			Map.entry("burung_sangkar", List.of("burung")),
			Map.entry("tai_chi", List.of("tai_chi")),
			Map.entry("pemancing", List.of("pancing", "memancing")),
			Map.entry("adu_jangkrik", List.of("jangkrik")),
			Map.entry("penyair", List.of("syair", "menulis")),
			Map.entry("pemain_erhu", List.of("musik")),
			Map.entry("pendoa_kuil", List.of("kuil", "dupa")),
			Map.entry("layang_layang", List.of("layangan")),
			Map.entry("penyulam", List.of("menyulam")),
			Map.entry("tidur_siang", List.of("tidur")),
			Map.entry("pesolek", List.of("pasar", "toko")),
			Map.entry("dermawan", List.of("bubur", "bantu", "tolong")),
			Map.entry("teman_kucing", List.of("kucing")),
			Map.entry("penjudi", List.of("dadu")),
			Map.entry("pencerita_anak", List.of("cerita", "dongeng")),
			Map.entry("penggemar_opera", List.of("tonton")),
			Map.entry("penyapu_jalan", List.of("sapu")),
			Map.entry("pecinta_teh", List.of("teh")),
			Map.entry("kutu_buku", List.of("baca", "buku", "salin")));

	private static double kebiasaan(KumpulanKegiatan.Aktivitas a, @Nullable Warga w) {
		if (w == null) {
			return 1.0D;
		}
		double f = 1.0D;
		for (int h : new int[]{w.kebiasaan1, w.kebiasaan2}) {
			String id = Kepribadian.kebiasaan(h);
			if (id.equals("pemalas")) {
				f *= a.inti() ? 0.8D : a.ada("santai") ? 1.5D : 1.0D;
			} else if (id.equals("pekerja_keras")) {
				f *= a.inti() ? 1.3D : a.ada("santai") ? 0.7D : 1.0D;
			} else {
				for (String frag : KEBIASAAN.getOrDefault(id, List.of())) {
					if (a.id().contains(frag) || a.kunci().contains(frag)) {
						f *= 2.5D;
						break;
					}
				}
			}
		}
		return f;
	}

	/** Temperament means (Tokoh.WATAK order) as H E X A C O. */
	private static final Map<String, int[]> WATAK = Map.of(
			"ramah", new int[]{10, 10, 40, 40, 0, 10},
			"curiga", new int[]{0, 20, -20, -30, 20, -20},
			"serakah", new int[]{-40, 0, 10, -20, 20, 0},
			"bijak", new int[]{30, -10, 0, 20, 30, 40},
			"penakut", new int[]{10, 50, -30, 10, 10, -10},
			"sombong", new int[]{-30, -10, 30, -30, 10, 0},
			"jujur", new int[]{40, 0, 0, 20, 30, 0},
			"licik", new int[]{-40, 0, 10, -20, -10, 30});

	/**
	 * The six traits the plan weighs by. shortcut: temperament mean + habit shifts + a seeded spread of +-25, not 42K's
	 * stored personality (no public accessor yet); switch to {@code Cakap}'s trait reader when 42K exposes one.
	 */
	static Kepribadian.Sifat sifat(@Nullable Warga w, Penduduk p) {
		int[] v = w != null ? WATAK.getOrDefault(Kepribadian.watak(w), new int[6]).clone() : new int[6];
		long seed = w != null ? w.id * 0x5DEECE66DL : p.benih();
		SplittableRandom r = new SplittableRandom(seed ^ 0x2545F4914F6CDD1DL);
		for (int i = 0; i < 6; i++) {
			v[i] += r.nextInt(51) - 25;
		}
		if (w != null) {
			for (int h : new int[]{w.kebiasaan1, w.kebiasaan2}) {
				switch (Kepribadian.kebiasaan(h)) {
					case "pekerja_keras" -> v[4] += 30;
					case "pemalas" -> v[4] -= 30;
					case "kutu_buku", "penyair" -> v[5] += 25;
					case "gosip_sumur", "pemabuk" -> v[2] += 25;
					case "pengintip" -> v[1] += 15;
					default -> {
					}
				}
			}
		}
		return Kepribadian.Sifat.dari(v);
	}

	// ================================================================== reading (commands, screens)

	/** Minutes after 06:00 as {@code HH:MM}. */
	public static String jam(int menit) {
		int m = (menit + 360) % 1440;
		return String.format("%02d:%02d", m / 60, m % 60);
	}

	/**
	 * The plan of {@code p} as chat lines for {@code uji kota_hidup ragam <warga>} (42S {@code RagamUji}): title, one line per
	 * step, yesterday's variety, the group of the day and the last interlude.
	 */
	public static List<Component> ringkas(TempatHuni huni, Penduduk p, long hari, Component nama) {
		List<Component> out = new ArrayList<>();
		Rencana r = rencana(huni, p, hari);
		out.add(Component.translatable("kultivator.kota.ragam2.rencana.judul", nama, hari,
				Component.translatable(IramaMingguan.kunciHari(hari)), Component.translatable(IramaMingguan.kunciMusim(hari))));
		if (r.langkah().isEmpty()) {
			out.add(Component.translatable("kultivator.kota.ragam2.rencana.kosong", nama));
		}
		for (Langkah l : r.langkah()) {
			out.add(Component.translatable("kultivator.kota.ragam2.rencana.langkah", jam(l.mulai()), jam(l.akhir()),
					Component.translatable("kultivator.hidup.kegiatan." + l.kegiatan()),
					Component.translatable("kultivator.kota.ragam2.blok." + l.blok()),
					Component.translatable("kultivator.kota.ragam2.jenis." + l.jenis()), l.titik()));
		}
		Rencana kemarin = buat(huni, p, WargaApi.warga(huni.situsId, p.id()), hari - 1L, null);
		int variasi = 0;
		for (Langkah l : kemarin.langkah()) {
			if (l.jenis().equals("ragam")) {
				variasi++;
			}
		}
		out.add(Component.translatable("kultivator.kota.ragam2.rencana.kemarin", kemarin.ragam(), variasi));
		KegiatanKelompok.Grup g = KegiatanKelompok.grupDari(huni, p.id(), hari);
		if (g != null) {
			out.add(Component.translatable("kultivator.kota.ragam2.rencana.kelompok", Component.translatable(g.jenis().kunciLang()),
					jam(g.mulai()), jam(g.akhir()), g.anggota().length));
		}
		Selingan.Terakhir t = Selingan.terakhir(huni.situsId, p.id());
		if (t != null) {
			out.add(Component.translatable("kultivator.kota.ragam2.rencana.selingan", Component.translatable(t.def().kunciLang()), t.kali()));
		}
		return out;
	}

	/** The variety statistics line of one place and day ({@code uji kota_hidup ragam laporan}). */
	public static Component statistik(long situs, long hari, String nama) {
		Selingan.Statistik s = Selingan.statistik(situs, hari);
		return Component.translatable("kultivator.kota.ragam2.statistik", nama, hari, dibuat(situs, hari), Belokan.jumlah(situs, hari),
				s.putar(), String.format("%.1f", s.perMenit()), KegiatanKelompok.jumlah(situs, hari));
	}

	private RencanaHarian() {
	}
}
