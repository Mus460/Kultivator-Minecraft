package dev.kultivator.dunia.kota42.warga;

import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.JenisUpacara;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.api.KodeWajah;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.ekonomi.HargaKota;
import dev.kultivator.dunia.kota42.sekte.PaktaLindung;
import dev.kultivator.dunia.kota42.tumbuh.Kemakmuran;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.perbaikan.PerbaikanApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongToIntFunction;

/**
 * The mood of residents (DESIGN 42.4.11): {@code suasana = pribadi + tempat + musim}, clamped -100..100, computed lazily on
 * Hari Warga ({@link #harian}) and when a body is called ({@link #nilai}), and the visible face code ({@link #kode}).
 * <ul>
 * <li><b>Personal</b>: temperament (ramah +10, sombong +5, penakut -5, curiga -5), health {@code (k - 60) / 4}, household
 * prosperity {@code (m - 50) / 5}, newly wed +20 and a new baby +15 (both fade over 10 days), a death in the family -50
 * (fades 5 a day), out of work -15, a crowded house -5.</li>
 * <li><b>Place</b>: festival +25, a wedding there +8, a funeral -8, a beast wave or attack -40 (and fear), war -20,
 * protected by a player's sect +5, prosperity {@code (K - 50) / 10}, the 38.R fear meter {@code -takut / 4}, food price
 * factor above 1.2 -10, plus what other modules register ({@link #addPengaruhTempat}: tax above 10 % from 42C-2, the
 * winter soup stall of 42D...).</li>
 * <li><b>Season and weather</b>: winter -5, rain -3, the first snow of winter +5 (a child +15).</li>
 * </ul>
 * Mood events and the "sad since" days are kept in {@link ArsipWarga.Catatan}; weddings, births and deaths are caught from
 * {@link KotaBus} so no caller has to remember them. Effects other modules read: {@link #faktorHarga} (happy -3 %, angry
 * +5 %), {@link #faktorKerja} (happy x1.1), {@link #ikutFestival}.
 */
public final class Suasana {

	/** Temperament bonus by {@code Tokoh.WATAK} index (ramah, curiga, serakah, bijak, penakut, sombong, jujur, licik). */
	static final int[] WATAK = {10, -5, 0, 0, -5, 5, 0, 0};

	/** Ticks a quarrel or theft keeps a resident angry (one world hour). */
	public static final int MARAH_TICK = 1000;

	/** A fading mood event: start value and days to fade to 0. */
	public enum Peristiwa {
		NIKAH(20, 10),
		BAYI(15, 10),
		DUKA(-50, 10),
		SEMBUH(10, 4),
		DIRAMPOK(-25, 5);

		public final int awal;
		public final int lama;

		Peristiwa(int awal, int lama) {
			this.awal = awal;
			this.lama = lama;
		}

		/** The value {@code hariLalu} days after it happened (linear fade). */
		public int nilai(long hariLalu) {
			if (hariLalu < 0L || hariLalu >= lama) {
				return 0;
			}
			return (int) Math.round(awal * (1.0D - hariLalu / (double) lama));
		}
	}

	private static final List<LongToIntFunction> PENGARUH = new CopyOnWriteArrayList<>();
	/** Place layer cache: situs -> {day, value, fear flag}. */
	private static final Map<Long, long[]> CACHE_TEMPAT = new HashMap<>();
	private static boolean ready;

	/** Registers the KotaBus listeners. From {@code KotaWarga.init}; safe twice. */
	public static void init() {
		if (ready) {
			return;
		}
		ready = true;
		KotaBus.NIKAH.register((server, situs, pria, wanita) -> {
			catat(server, pria, Peristiwa.NIKAH);
			catat(server, wanita, Peristiwa.NIKAH);
		});
		KotaBus.LAHIR.register((server, situs, bayi, ibu) -> {
			if (ibu != null) {
				catat(server, ibu, Peristiwa.BAYI);
				Warga ayah = WargaData.get(server).byId(ibu.pasangan);
				if (ayah != null) {
					catat(server, ayah, Peristiwa.BAYI);
				}
			}
		});
		KotaBus.WAFAT.register((server, situs, warga, sebab) -> berduka(server, situs, warga));
		KotaBus.UPACARA.register((server, situs, jenis, id, fase) -> {
			if (fase == 0) {
				CompoundTag t = ArsipWarga.catatan(server).tempat(situs);
				t.putLong("upHari", KotaHidup.hari(server));
				t.putInt("upNilai", jenis == JenisUpacara.MAKAM ? -8 : jenis == JenisUpacara.NIKAH ? 8 : 4);
				ArsipWarga.catatan(server).ubah();
				CACHE_TEMPAT.remove(situs);
			}
		});
		KotaBus.SERBUAN.register((server, situs, mulai, menang) -> {
			ArsipWarga.catatan(server).tempat(situs).putBoolean("serbuan", mulai);
			ArsipWarga.catatan(server).ubah();
			CACHE_TEMPAT.remove(situs);
		});
	}

	/** Adds a place-layer source (the mood change it gives place {@code situs}; 0 = none), e.g. tax or a soup stall. */
	public static void addPengaruhTempat(LongToIntFunction pengaruh) {
		if (pengaruh != null) {
			PENGARUH.add(pengaruh);
		}
	}

	// ================================================================== events

	/** Records a fading mood event for {@code w} today. */
	public static void catat(MinecraftServer server, Warga w, Peristiwa p) {
		if (w == null) {
			return;
		}
		ArsipWarga.Catatan c = ArsipWarga.catatan(server);
		CompoundTag t = c.warga(w.id);
		t.putLong("ev_" + p.name(), KotaHidup.hari(server));
		c.ubah();
	}

	/** A quarrel or a theft: angry for {@value #MARAH_TICK} ticks from game time {@code now}. */
	public static void marah(MinecraftServer server, Warga w, long now) {
		ArsipWarga.Catatan c = ArsipWarga.catatan(server);
		c.warga(w.id).putLong("marah", now);
		c.ubah();
	}

	/** A death: spouse, parents, children and the household mourn (DUKA). */
	static void berduka(MinecraftServer server, long situs, Warga mati) {
		WargaData data = WargaData.get(server);
		for (int id : new int[]{mati.pasangan, mati.ayah, mati.ibu}) {
			Warga k = data.byId(id);
			if (k != null && k.hidup()) {
				catat(server, k, Peristiwa.DUKA);
			}
		}
		for (int id : mati.anak) {
			Warga k = data.byId(id);
			if (k != null && k.hidup()) {
				catat(server, k, Peristiwa.DUKA);
			}
		}
		WargaData.Situs s = data.peek(situs);
		if (s != null && mati.rumahTangga != 0) {
			for (Warga k : s.warga) {
				if (k != mati && k.hidup() && k.rumahTangga == mati.rumahTangga) {
					catat(server, k, Peristiwa.DUKA);
				}
			}
		}
	}

	/** Sum of the fading events of {@code w} on day {@code hari}. */
	static int peristiwa(@Nullable CompoundTag t, long hari) {
		if (t == null) {
			return 0;
		}
		int sum = 0;
		for (Peristiwa p : Peristiwa.values()) {
			String key = "ev_" + p.name();
			if (t.contains(key)) {
				sum += p.nilai(hari - t.getLongOr(key, Long.MIN_VALUE / 2));
			}
		}
		return sum;
	}

	/** True while a death in the family still weighs (within its 10 days). */
	public static boolean berkabung(@Nullable CompoundTag t, long hari) {
		return t != null && t.contains("ev_DUKA") && hari - t.getLongOr("ev_DUKA", 0L) < Peristiwa.DUKA.lama;
	}

	// ================================================================== the three layers

	/** The personal layer of {@code w} on day {@code hari}. */
	public static int pribadi(MinecraftServer server, long situs, Warga w, long hari) {
		int v = WATAK[Mth.clamp(w.watak, 0, WATAK.length - 1)];
		v += (w.kesehatan - 60) / 4;
		v += (kemakmuranRumahTangga(server, situs, w) - 50) / 5;
		v += peristiwa(ArsipWarga.catatan(server).peekWarga(w.id), hari);
		int umur = WargaApi.umur(w);
		if (umur >= 16 && umur < 65 && !Pekerjaan.bekerja(w)) {
			v -= 15;
		}
		if (Migrasi.sesak(server, situs, w)) {
			v -= 5;
		}
		v += Kebiasaan.suasana(situs, w);
		return v;
	}

	/**
	 * Household prosperity 0-100 (42.4.11 "kemakmuran rumah tangga"): working adults, their skill and the place's
	 * prosperity. A household where everyone of age works with good skill in a rich town is near 100.
	 */
	public static int kemakmuranRumahTangga(MinecraftServer server, long situs, Warga w) {
		WargaData.Situs s = WargaData.get(server).peek(situs);
		if (s == null) {
			return 50;
		}
		int dewasa = 0;
		int kerja = 0;
		int ahli = 0;
		for (Warga k : s.warga) {
			if (!k.hidup() || (w.rumahTangga == 0 ? k != w : k.rumahTangga != w.rumahTangga)) {
				continue;
			}
			int umur = WargaApi.umur(k);
			if (umur >= 16 && umur < 65) {
				dewasa++;
				if (Pekerjaan.bekerja(k)) {
					kerja++;
					ahli += k.ahli;
				}
			}
		}
		int kota = Kemakmuran.nilai(situs);
		int kebiasaan = Kebiasaan.kemakmuran(w.rumahTangga == 0 ? List.of(w) : s.rumahTangga(w.rumahTangga));
		if (dewasa == 0) {
			return Mth.clamp((kota + 40) / 2 + kebiasaan, 0, 100);
		}
		double share = kerja / (double) dewasa;
		double skill = kerja == 0 ? 0.0D : ahli / (double) kerja;
		return Mth.clamp((int) Math.round(20.0D + 45.0D * share + skill * 0.2D + (kota - 50) * 0.3D) + kebiasaan, 0, 100);
	}

	/** The place layer of {@code situs} on day {@code hari} at game time {@code now} (cached for the day). */
	public static int tempat(MinecraftServer server, long situs, long hari, long now) {
		long[] c = CACHE_TEMPAT.get(situs);
		if (c != null && c[0] == hari && now - c[3] < 1200L) {
			return (int) c[1];
		}
		int v = 0;
		boolean takut = false;
		String acara = HidupApi.timpaan(situs, now);
		if (acara != null) {
			if (meriah(acara)) {
				v += 25;
			} else if (bahaya(acara)) {
				v -= 40;
				takut = true;
			}
		}
		CompoundTag t = ArsipWarga.catatan(server).tempat(situs);
		if (t.getLongOr("upHari", -9L) == hari) {
			v += t.getIntOr("upNilai", 0);
		}
		if (t.getBooleanOr("serbuan", false)) {
			v -= 20;
			takut = true;
		}
		if (PaktaLindung.pelindung(situs) != null) {
			v += 5;
		}
		v += (Kemakmuran.nilai(situs) - 50) / 10;
		int fear = PerbaikanApi.takut(situs);
		v -= fear / 4;
		takut |= fear >= 40;
		if (HargaKota.faktor(server, situs, Kategori.PANGAN) > 1.2D) {
			v -= 10;
		}
		for (LongToIntFunction p : PENGARUH) {
			try {
				v += p.applyAsInt(situs);
			} catch (RuntimeException ignored) {
				// a broken source never stops the mood pass
			}
		}
		CACHE_TEMPAT.put(situs, new long[]{hari, v, takut ? 1L : 0L, now});
		return v;
	}

	/** True when the place layer says the town is afraid (attack, beast wave, fear meter >= 40). */
	public static boolean takut(MinecraftServer server, long situs, long now) {
		tempat(server, situs, KotaHidup.hari(server), now);
		long[] c = CACHE_TEMPAT.get(situs);
		return c != null && c[2] != 0L;
	}

	/** The season and weather layer for {@code w} (winter, rain, the first snow). */
	public static int musim(long situs, Warga w, long hari) {
		int v = 0;
		int musim = (int) Math.floorMod(hari / Jadwal.HARI_MUSIM, 4L);
		TempatHuni huni = Pemanggil.huni(situs);
		Jadwal.Cuaca cuaca = huni == null ? Jadwal.Cuaca.CERAH : huni.cuaca();
		if (musim == Jadwal.DINGIN) {
			v -= 5;
		}
		if (cuaca.hujan()) {
			v -= 3;
		}
		if (cuaca.salju() && musim == Jadwal.DINGIN && Math.floorMod(hari, (long) Jadwal.HARI_MUSIM) <= 2) {
			v += WargaApi.umur(w) <= 12 ? 15 : 5;
		}
		return v;
	}

	/** The whole mood of {@code w} now. */
	public static int nilai(MinecraftServer server, long situs, Warga w, long now) {
		long hari = KotaHidup.hari(server);
		return Mth.clamp(pribadi(server, situs, w, hari) + tempat(server, situs, hari, now) + musim(situs, w, hari), -100, 100);
	}

	/**
	 * Hari Warga step: stores every living resident's mood in {@code Warga.suasana} and keeps the "sad since" days that
	 * {@link Migrasi} (below -40 for a year) and {@link Pekerjaan} (below -20 for a year) read.
	 */
	public static void harian(MinecraftServer server, WargaData.Situs s, long hari) {
		if (!KotaConfig.warga().aktif) {
			return;
		}
		long now = server.overworld().getGameTime();
		ArsipWarga.Catatan c = ArsipWarga.catatan(server);
		for (Warga w : s.warga) {
			if (w.status != Warga.Status.HIDUP) {
				continue;
			}
			w.suasana = nilai(server, s.id, w, now);
			CompoundTag t = c.warga(w.id);
			sejak(t, "s40", w.suasana < -40, hari);
			sejak(t, "s20", w.suasana < -20, hari);
		}
		c.ubah();
	}

	private static void sejak(CompoundTag t, String key, boolean benar, long hari) {
		if (benar) {
			if (!t.contains(key)) {
				t.putLong(key, hari);
			}
		} else {
			t.remove(key);
		}
	}

	/** Days the mood of {@code w} has been below -40 ({@code "s40"}) or -20 ({@code "s20"}); 0 when it is not. */
	public static long hariMurung(MinecraftServer server, Warga w, String key, long hari) {
		CompoundTag t = ArsipWarga.catatan(server).peekWarga(w.id);
		return t == null || !t.contains(key) ? 0L : hari - t.getLongOr(key, hari);
	}

	// ================================================================== the face

	/**
	 * The visible code of {@code w} at game time {@code now}: fear (attack or fear meter), sickness, anger (within an hour
	 * of a quarrel or theft), drink (the night drinker 21:00-04:00), mourning or low mood, else by value.
	 */
	public static KodeWajah kode(Warga w, long now) {
		MinecraftServer server = KotaHidup.server();
		if (server == null) {
			return w.kesehatan < 30 ? KodeWajah.SAKIT : KodeWajah.dariNilai(w.suasana);
		}
		long situs = WargaData.get(server).situsDari(w.id);
		if (situs != 0L && takut(server, situs, now)) {
			return KodeWajah.TAKUT;
		}
		if (w.kesehatan < 30) {
			return KodeWajah.SAKIT;
		}
		CompoundTag t = ArsipWarga.catatan(server).peekWarga(w.id);
		if (t != null && t.contains("marah") && now - t.getLongOr("marah", 0L) < MARAH_TICK) {
			return KodeWajah.MARAH;
		}
		if (Kebiasaan.punya(w, "pemabuk")) {
			int jam = (int) ((Kota.timeOfDay(server) / 1000L + 6L) % 24L);
			if (jam >= 21 || jam < 4) {
				return KodeWajah.MABUK;
			}
		}
		if (berkabung(t, KotaHidup.hari(server))) {
			return KodeWajah.SEDIH;
		}
		return KodeWajah.dariNilai(w.suasana);
	}

	/** The mean stored mood of a place's living residents (0 when none). */
	public static int tempatRata(long situs) {
		MinecraftServer server = KotaHidup.server();
		WargaData.Situs s = server == null ? null : WargaData.get(server).peek(situs);
		if (s == null) {
			return 0;
		}
		long sum = 0L;
		int n = 0;
		for (Warga w : s.warga) {
			if (w.status == Warga.Status.HIDUP) {
				sum += w.suasana;
				n++;
			}
		}
		return n == 0 ? 0 : (int) (sum / n);
	}

	// ================================================================== effects

	/** Personal price factor of a trading resident by mood: happy -3 %, angry +5 %. */
	public static double faktorHarga(KodeWajah kode) {
		return switch (kode) {
			case SENANG -> 0.97D;
			case MARAH -> 1.05D;
			default -> 1.0D;
		};
	}

	/** Work speed factor (a happy craftsman repairs 10 % faster; sad 10 % slower; sick at half speed). */
	public static double faktorKerja(KodeWajah kode) {
		return switch (kode) {
			case SENANG -> 1.1D;
			case SEDIH, TAKUT -> 0.9D;
			case SAKIT, MABUK -> 0.5D;
			default -> 1.0D;
		};
	}

	/** Chance to join a festival crowd (0.3 sad .. 0.9 happy; fan dancers and opera lovers more, up to 0.95). */
	public static double ikutFestival(Warga w) {
		return Mth.clamp(0.6D + w.suasana / 200.0D + Kebiasaan.tambahFestival(w), 0.3D, 0.95D);
	}

	/** Festive event names of {@code HidupApi.timpaJadwal} / the themed towns. */
	static boolean meriah(String acara) {
		return acara.contains("festival") || acara.contains("hari_raya") || acara.contains("tahun_baru") || acara.contains("perayaan")
				|| acara.contains("pasar_malam") || acara.contains("lomba") || acara.startsWith("tema.") || acara.contains("pesta");
	}

	/** Danger event names (beast wave, raid, siege). */
	static boolean bahaya(String acara) {
		return acara.contains("gelombang") || acara.contains("binatang") || acara.contains("serbuan") || acara.contains("bahaya")
				|| acara.contains("kepung") || acara.contains("serangan");
	}

	/** Forgets the place cache (server stop). */
	public static void lupakan() {
		CACHE_TEMPAT.clear();
	}

	private Suasana() {
	}
}
