package dev.kultivator.dunia.kota42.ekonomi;

import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.cakap.GosipWarga;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.perbaikan.PerbaikanApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The town stock of DESIGN 42.6.3 ("Langka"): per place and category 0-200 (start 100), the only memory the dynamic prices
 * keep. Every world day a place's stock moves by what its residents make minus what they use (the production table of
 * 42.6.3 by {@code NpcRole}, counted from the live residents whenever the place is active and remembered while it is
 * not), drifts 10 % back toward 100 (merchants restock from the countryside) and is halved in production during a war.
 * Caravans arriving add 20 to the three scarcest goods ({@link #kafilahTiba}); players selling to and buying from the
 * town's shops nudge it ({@code HargaKota.catatJual/catatBeli}). The player's market flooding ({@link #jenuh}) recovers one
 * unit per world hour.
 * <p>
 * Lumbung Penstabil (<i>changping cang</i>): food dearer than 1.3 for three days in a row makes the town purse buy grain
 * from the caravans (at most 200 NB a day, {@code KasKota.ambil}, never below zero) and open the granary: food stock +40
 * (in proportion to what the purse could pay), a gossip "Lumbung dibuka" and a queue of residents with sacks at the
 * {@code lumbung} titik that morning ({@code KotaEkonomi}'s schedule overrider).
 * <p>
 * State in {@code KotaEkonomiData.situs("stok", situs)}: {@code s} stock int[12], {@code h} last day run, {@code p}/{@code c}
 * production and consumption x10 int[12] of the last census, {@code pop} residents, {@code j} flooding units x10 int[12],
 * {@code jj} last hour flooding recovered, {@code t} days food was dear, {@code lb} day the granary opened, {@code f0}
 * price factors x1000 at the day's start (trend and the Papan Harga list).
 */
public final class StokKota {

	public static final int AWAL = 100;
	public static final int MAKS = 200;
	/** Most days caught up at once after a long absence. */
	private static final int KEJAR_HARI = 14;
	/** Share of the gap to {@link #AWAL} closed every day. */
	private static final double KEMBALI = 0.10D;
	/** A caravan's load (42.6.3). */
	public static final int KAFILAH = 20;
	/** Granary: food factor threshold, days, purse NB a day, stock it adds, days between openings. */
	public static final double LUMBUNG_AMBANG = 1.3D;
	public static final int LUMBUNG_HARI = 3;
	public static final long LUMBUNG_NB = 200L;
	public static final int LUMBUNG_STOK = 40;
	public static final int LUMBUNG_JEDA = 7;

	private static final Kategori[] SEMUA = Kategori.values();
	/** Categories with a stock (services, buildings and trade goods price by other rules). */
	private static final List<Kategori> BERSTOK = List.of(Kategori.PANGAN, Kategori.OBAT, Kategori.HERBA, Kategori.SENJATA, Kategori.ZIRAH,
			Kategori.JIMAT, Kategori.BAHAN, Kategori.KAIN, Kategori.MEWAH);

	/** Production per resident per day by role (42.6.3 table; roles of the 34/38/42 towns). */
	private static final Map<NpcRole, double[]> PRODUKSI = new EnumMap<>(NpcRole.class);

	static {
		hasil(NpcRole.PETANI, Kategori.PANGAN, 3.0D, Kategori.HERBA, 0.5D);
		hasil(NpcRole.NELAYAN, Kategori.PANGAN, 2.0D);
		hasil(NpcRole.PENJUAL_IKAN, Kategori.PANGAN, 1.0D);
		hasil(NpcRole.KOKI, Kategori.PANGAN, 1.0D);
		hasil(NpcRole.ALKEMIS, Kategori.OBAT, 2.0D);
		hasil(NpcRole.TABIB, Kategori.OBAT, 1.0D);
		hasil(NpcRole.PENJUAL_OBAT, Kategori.OBAT, 1.0D);
		hasil(NpcRole.PEMETIK_HERBA, Kategori.HERBA, 3.0D);
		hasil(NpcRole.PENJAGA_KEBUN, Kategori.HERBA, 1.0D);
		hasil(NpcRole.EMPU, Kategori.SENJATA, 2.0D, Kategori.ZIRAH, 1.0D);
		hasil(NpcRole.PANDAI_DESA, Kategori.SENJATA, 1.0D, Kategori.ZIRAH, 1.0D);
		hasil(NpcRole.PANDAI_PUSAKA, Kategori.SENJATA, 1.0D);
		hasil(NpcRole.PEDAGANG_SENJATA, Kategori.SENJATA, 0.5D);
		hasil(NpcRole.RAHIB, Kategori.JIMAT, 1.0D);
		hasil(NpcRole.BIKSU_GUNUNG, Kategori.JIMAT, 1.0D);
		hasil(NpcRole.PUSTAKAWAN, Kategori.JIMAT, 1.0D);
		hasil(NpcRole.SASTRAWAN, Kategori.JIMAT, 1.0D);
		hasil(NpcRole.KULI, Kategori.BAHAN, 1.0D);
		hasil(NpcRole.KULI_GUNUNG, Kategori.BAHAN, 1.0D);
		hasil(NpcRole.TUKANG, Kategori.BAHAN, 0.5D);
		hasil(NpcRole.TUKANG_KAPAL, Kategori.BAHAN, 0.5D);
		hasil(NpcRole.PENENUN_SUTRA, Kategori.KAIN, 3.0D);
		hasil(NpcRole.NYONYA_SUTRA, Kategori.KAIN, 1.0D);
		hasil(NpcRole.PEMELIHARA_ULAT, Kategori.KAIN, 1.0D);
		hasil(NpcRole.AHLI_TEH, Kategori.MEWAH, 1.0D);
		hasil(NpcRole.PEMETIK_TEH, Kategori.MEWAH, 1.0D);
		hasil(NpcRole.PEMILIK_TEH, Kategori.MEWAH, 0.5D);
		hasil(NpcRole.PELUKIS_GUA, Kategori.MEWAH, 1.0D);
	}

	private static void hasil(NpcRole peran, Object... pasangan) {
		double[] v = new double[SEMUA.length];
		for (int i = 0; i + 1 < pasangan.length; i += 2) {
			v[((Kategori) pasangan[i]).ordinal()] = (Double) pasangan[i + 1];
		}
		PRODUKSI.put(peran, v);
	}

	// ================================================================== reading

	/** The stock of {@code k} in place {@code situs} (0-200), after running the missed days. */
	public static int stok(MinecraftServer server, long situs, Kategori k) {
		if (!BERSTOK.contains(k)) {
			return AWAL;
		}
		CompoundTag t = harian(server, situs);
		return stokDari(t)[k.ordinal()];
	}

	/** The whole stock row of a place (12 values; services, buildings and trade goods read {@link #AWAL}). */
	public static int[] semua(MinecraftServer server, long situs) {
		return stokDari(harian(server, situs)).clone();
	}

	/** Daily production / consumption of {@code k} in the last census of the place (for the price tooltip). */
	public static double[] imbang(MinecraftServer server, long situs, Kategori k) {
		CompoundTag t = data(server, situs);
		return new double[] {arr(t, "p", 0)[k.ordinal()] / 10.0D, arr(t, "c", 0)[k.ordinal()] / 10.0D};
	}

	/** Residents counted in the last census. */
	public static int penduduk(MinecraftServer server, long situs) {
		return data(server, situs).getIntOr("pop", 0);
	}

	/** The player market flooding factor {@code max(0.6, 1 - 0.01 n)} of category {@code k} (42.6.3 "Banjir pasar"). */
	public static double jenuh(MinecraftServer server, long situs, Kategori k) {
		return Math.max(0.6D, 1.0D - 0.01D * unitJenuh(server, situs, k));
	}

	/** Units of {@code k} players sold to the place in the last day, recovered one per world hour. */
	public static double unitJenuh(MinecraftServer server, long situs, Kategori k) {
		CompoundTag t = data(server, situs);
		pulihJenuh(server, t);
		return arr(t, "j", 0)[k.ordinal()] / 10.0D;
	}

	/** True while the granary of the place opened today (residents queue with sacks in the morning). */
	public static boolean lumbungBuka(MinecraftServer server, long situs) {
		return data(server, situs).getLongOr("lb", Long.MIN_VALUE / 2) == KotaHidup.hari(server);
	}

	// ================================================================== changing

	/** Adds {@code delta} to the stock of {@code k} (clamped 0-200). */
	public static void ubah(MinecraftServer server, long situs, Kategori k, int delta) {
		if (situs == 0L || delta == 0 || !BERSTOK.contains(k)) {
			return;
		}
		CompoundTag t = harian(server, situs);
		int[] s = stokDari(t);
		s[k.ordinal()] = Mth.clamp(s[k.ordinal()] + delta, 0, MAKS);
		t.putIntArray("s", s);
		KotaEkonomiData.get(server).ubah();
	}

	/** Players sold {@code units} of {@code k} to the place: market flooding +units (pattern {@code Dagang.jenuh}). */
	public static void tambahJenuh(MinecraftServer server, long situs, Kategori k, int units) {
		if (situs == 0L || units <= 0) {
			return;
		}
		CompoundTag t = data(server, situs);
		pulihJenuh(server, t);
		int[] j = arr(t, "j", 0);
		j[k.ordinal()] = Math.min(480, j[k.ordinal()] + units * 10);
		t.putIntArray("j", j);
		KotaEkonomiData.get(server).ubah();
	}

	/** A caravan arrived at the place ({@code KafilahJalan}): +20 to each of the three scarcest goods. */
	public static void kafilahTiba(MinecraftServer server, long situs) {
		if (situs == 0L || HargaKota.perang(server, situs)) {
			return;
		}
		CompoundTag t = harian(server, situs);
		int[] s = stokDari(t);
		List<Kategori> urut = new java.util.ArrayList<>(BERSTOK);
		urut.sort((a, b) -> Integer.compare(s[a.ordinal()], s[b.ordinal()]));
		for (int i = 0; i < 3; i++) {
			Kategori k = urut.get(i);
			s[k.ordinal()] = Math.min(MAKS, s[k.ordinal()] + KAFILAH);
		}
		t.putIntArray("s", s);
		KotaEkonomiData.get(server).ubah();
	}

	/**
	 * Counts what the live residents of an active place make and use per day (called every world hour for the active
	 * places). Kept so the stock moves while nobody is near.
	 */
	static void sensus(MinecraftServer server, TempatHuni huni) {
		List<Penduduk> orang = huni.penduduk();
		if (orang.isEmpty()) {
			return;
		}
		double[] p = new double[SEMUA.length];
		double[] c = new double[SEMUA.length];
		int pop = orang.size();
		int penjaga = 0;
		int alkemis = 0;
		int pejabat = 0;
		for (Penduduk each : orang) {
			NpcRole peran = each.peran();
			double[] v = PRODUKSI.get(peran);
			if (v != null) {
				for (int i = 0; i < v.length; i++) {
					p[i] += v[i];
				}
			}
			if (peran.kelompok == NpcRole.Kelompok.PENJAGA) {
				penjaga++;
			}
			if (peran == NpcRole.ALKEMIS) {
				alkemis++;
			}
			if (peran == NpcRole.MENTERI || peran == NpcRole.HAKIM || peran == NpcRole.PANGERAN_KOTA || peran == NpcRole.TUAN_BENTENG) {
				pejabat++;
			}
		}
		c[Kategori.PANGAN.ordinal()] = 0.2D * pop;
		c[Kategori.OBAT.ordinal()] = 0.03D * pop;
		c[Kategori.HERBA.ordinal()] = 2.0D * alkemis;
		c[Kategori.SENJATA.ordinal()] = 0.2D * penjaga;
		c[Kategori.ZIRAH.ordinal()] = 0.2D * penjaga;
		c[Kategori.JIMAT.ordinal()] = 0.02D * pop;
		c[Kategori.BAHAN.ordinal()] = 0.05D * pop;
		c[Kategori.KAIN.ordinal()] = 0.04D * pop;
		c[Kategori.MEWAH.ordinal()] = 0.02D * pop + 0.5D * pejabat;
		CompoundTag t = data(server, huni.situsId);
		t.putIntArray("p", kali10(p));
		t.putIntArray("c", kali10(c));
		t.putInt("pop", pop);
		KotaEkonomiData.get(server).ubah();
	}

	// ================================================================== the day

	/** The place's stock tag with every missed day run (at most {@value #KEJAR_HARI}). */
	static CompoundTag harian(MinecraftServer server, long situs) {
		CompoundTag t = data(server, situs);
		long hari = KotaHidup.hari(server);
		long last = t.getLongOr("h", Long.MIN_VALUE);
		if (last == Long.MIN_VALUE) {
			t.putLong("h", hari);
			t.putIntArray("s", stokDari(t));
			return t;
		}
		if (last >= hari) {
			return t;
		}
		long dari = Math.max(last + 1L, hari - KEJAR_HARI + 1L);
		for (long d = dari; d <= hari; d++) {
			satuHari(server, situs, t, d);
		}
		t.putLong("h", hari);
		KotaEkonomiData.get(server).ubah();
		return t;
	}

	private static void satuHari(MinecraftServer server, long situs, CompoundTag t, long hari) {
		int[] s = stokDari(t);
		int[] p = arr(t, "p", 0);
		int[] c = arr(t, "c", 0);
		boolean perang = HargaKota.perang(server, situs);
		int musim = (int) Math.floorMod(hari / Jadwal.HARI_MUSIM, 4L);
		int pop = t.getIntOr("pop", 0);
		String acara = HargaKota.acara38(situs);
		boolean festival = acara != null && acara.startsWith("festival");
		for (Kategori k : BERSTOK) {
			int i = k.ordinal();
			double make = p[i] / 10.0D * (perang ? 0.5D : 1.0D);
			double use = c[i] / 10.0D;
			if (perang) {
				use += switch (k) {
					case OBAT -> 5.0D;
					case SENJATA -> 10.0D;
					default -> 0.0D;
				};
			}
			if (festival) {
				use += switch (k) {
					case PANGAN -> 0.1D * pop;
					case KAIN, MEWAH -> 3.0D;
					default -> 0.0D;
				};
			}
			if (musim == Jadwal.GUGUR && k == Kategori.PANGAN) {
				make *= 1.5D;
			}
			if (musim == Jadwal.DINGIN) {
				if (k == Kategori.KAIN) {
					use += 0.05D * pop;
				} else if (k == Kategori.BAHAN) {
					use += 0.03D * pop;
				}
			}
			if (k == Kategori.BAHAN && PerbaikanApi.adaKerusakan(situs)) {
				use += 3.0D;
			}
			double v = s[i] + (make - use);
			v += (AWAL - v) * KEMBALI;
			s[i] = Mth.clamp((int) Math.round(v), 0, MAKS);
		}
		t.putIntArray("s", s);
		// the day's starting factors (trend arrows, Papan Harga "biggest changes today")
		double[] f = HargaKota.hitungSemua(server, situs, s);
		int[] f0 = new int[SEMUA.length];
		for (int i = 0; i < f.length; i++) {
			f0[i] = (int) Math.round(f[i] * 1000.0D);
		}
		t.putIntArray("f0", f0);
		lumbung(server, situs, t, hari, f[Kategori.PANGAN.ordinal()]);
	}

	/** Lumbung Penstabil: three dear-food days in a row open the granary, paid by the town purse. */
	private static void lumbung(MinecraftServer server, long situs, CompoundTag t, long hari, double pangan) {
		int tinggi = pangan > LUMBUNG_AMBANG ? t.getIntOr("t", 0) + 1 : 0;
		t.putInt("t", tinggi);
		if (tinggi < LUMBUNG_HARI || hari - t.getLongOr("lb", Long.MIN_VALUE / 2) < LUMBUNG_JEDA || !KotaConfig.ekonomi().aktif) {
			return;
		}
		long paid = KasKota.ambil(server, situs, LUMBUNG_NB);
		if (paid <= 0L) {
			return;
		}
		int[] s = stokDari(t);
		int add = (int) Math.max(1L, LUMBUNG_STOK * paid / LUMBUNG_NB);
		s[Kategori.PANGAN.ordinal()] = Math.min(MAKS, s[Kategori.PANGAN.ordinal()] + add);
		t.putIntArray("s", s);
		t.putLong("lb", hari);
		t.putInt("t", 0);
		try {
			GosipWarga.lahir(server.overworld(), situs, "lumbung_dibuka");
		} catch (RuntimeException ignored) {
			// gossip is 42K's; the granary opens either way
		}
	}

	/** The day's starting factors x1000 (12 values; 1000 when the day has not run yet). */
	static int[] faktorAwal(MinecraftServer server, long situs) {
		return arr(harian(server, situs), "f0", 1000);
	}

	// ================================================================== helpers

	private static CompoundTag data(MinecraftServer server, long situs) {
		return KotaEkonomiData.get(server).situs(KotaEkonomiData.STOK, situs);
	}

	private static void pulihJenuh(MinecraftServer server, CompoundTag t) {
		long jam = jamDunia(server);
		long last = t.getLongOr("jj", jam);
		if (jam > last) {
			int[] j = arr(t, "j", 0);
			int pulih = (int) Math.min(480L, (jam - last) * 10L);
			for (int i = 0; i < j.length; i++) {
				j[i] = Math.max(0, j[i] - pulih);
			}
			t.putIntArray("j", j);
		}
		t.putLong("jj", jam);
	}

	/** World hours since the world began (overworld clock / 1000). */
	static long jamDunia(MinecraftServer server) {
		return Math.floorDiv(server.overworld().getOverworldClockTime(), 1000L);
	}

	private static int[] stokDari(CompoundTag t) {
		return arr(t, "s", AWAL);
	}

	private static int[] arr(CompoundTag t, String key, int isi) {
		int[] v = t.getIntArray(key).orElse(null);
		if (v == null || v.length != SEMUA.length) {
			int[] fresh = new int[SEMUA.length];
			java.util.Arrays.fill(fresh, isi);
			if (v != null) {
				System.arraycopy(v, 0, fresh, 0, Math.min(v.length, fresh.length));
			}
			return fresh;
		}
		return v.clone();
	}

	private static int[] kali10(double[] v) {
		int[] out = new int[v.length];
		for (int i = 0; i < v.length; i++) {
			out[i] = (int) Math.round(v[i] * 10.0D);
		}
		return out;
	}

	private StokKota() {
	}
}
