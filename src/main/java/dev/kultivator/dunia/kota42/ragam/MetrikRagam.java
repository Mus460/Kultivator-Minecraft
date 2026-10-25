package dev.kultivator.dunia.kota42.ragam;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The success metrics of the even spread and varied days (DESIGN 42.4A.8, contract C42.S1), recorded per place per day in
 * {@code RagamData.METRIK} (7 days) and printed by {@code uji kota_hidup ragam laporan [hari]} with pass/fail.
 * <ul>
 *   <li><b>Days of residents</b> (every data pass, 10 s per active place): an activity signature per resident
 *       ({@link #tanda}: key, clip, variant, props and goal kind; the stall or street turn number left out) appended to its
 *       day in {@code RagamData.RIWAYAT}. Different activities, repeats outside core work and Jaccard similarity with the day
 *       before are computed from these days; a finished day is folded into the day's totals ({@link #tutupHari}).</li>
 *   <li><b>Places</b> (same pass): Gini of occupancy relative to target between the zones in use ({@code T >= 0.5}), share of
 *       empty zones ({@code K >= 4}, {@code T >= 1}, nobody), the zone holding the largest share of the visible bodies
 *       against its fair share (its share of the data residents), and body pairs closer than {@code kotaWarga.jarakMin}
 *       (two seated bodies and walking pairs are interacting and do not count).</li>
 *   <li><b>Counters</b> of 42S-R: micro-breaks ({@link #selingan}) per real minute per body and repeats in a row, turns
 *       ({@link #belokan}) per resident per day.</li>
 * </ul>
 */
public final class MetrikRagam {

	/** Targets of 42.4A.8. */
	public static final double SASARAN_KEGIATAN = 5.0D;
	public static final double SASARAN_KEGIATAN_MIN = 4.0D;
	public static final double SASARAN_STAF = 3.0D;
	public static final double SASARAN_ULANG = 2.0D;
	public static final double SASARAN_JACCARD = 0.6D;
	public static final double SASARAN_SELINGAN_MIN = 2.0D;
	public static final double SASARAN_SELINGAN_MAKS = 7.0D;
	public static final double SASARAN_BELOKAN_MIN = 0.5D;
	public static final double SASARAN_BELOKAN_MAKS = 2.0D;
	public static final double SASARAN_ZONA_RASIO = 1.5D;
	public static final double SASARAN_ZONA_BAGIAN = 0.35D;
	public static final double SASARAN_GINI = 0.35D;
	public static final double SASARAN_ZONA_KOSONG = 0.10D;
	public static final double SASARAN_JARAK = 0.02D;

	/** A resident's day is measured from this many samples (6 world hours of 10 s passes). */
	public static final int SAMPEL_MIN = 30;
	/** Bit of a signature: core work (repeats allowed). */
	static final int INTI = 0x80000000;
	/** Bit of a signature: sleep, home, the walk home (not an activity of the day). */
	static final int RUTIN = 0x40000000;

	/**
	 * One day's metrics of one place; -1 = not measured (no data of that kind).
	 *
	 * @param kegiatan      mean different activities per free resident
	 * @param kegiatanMin   the least of them
	 * @param staf          mean different activities per staff / stationary resident
	 * @param ulang         mean of the largest same-activity repeat outside core work
	 * @param jaccard       mean Jaccard similarity with the day before
	 * @param selingan      micro-breaks per real minute per body
	 * @param selinganUlang share of micro-breaks equal to the one before
	 * @param belokan       turns per resident per day
	 * @param zonaTerbanyak mean share of the visible bodies in the fullest zone (samples with >= 4 visible zones)
	 * @param zonaRasio     mean of that zone's share / its fair share
	 * @param gini          mean Gini of occupancy / target between zones in use
	 * @param zonaKosong    mean share of empty zones that should be used
	 * @param jarak         share of body pairs closer than the minimum distance
	 * @param sampel        residents measured
	 * @param tubuh         body samples
	 */
	public record Laporan(long situs, long hari, double kegiatan, double kegiatanMin, double staf, double ulang, double jaccard, double selingan,
			double selinganUlang, double belokan, double zonaTerbanyak, double zonaRasio, double gini, double zonaKosong, double jarak, int sampel,
			int tubuh) {

		public static final Laporan KOSONG = new Laporan(0L, 0L, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 0);

		/** True when every measured value meets its 42.4A.8 target (false without resident data). */
		public boolean lulus() {
			if (sampel <= 0) {
				return false;
			}
			for (Baris b : baris()) {
				if (b.diukur() && !b.lulus()) {
					return false;
				}
			}
			return true;
		}

		/** The rows of the report in the order of the 42.4A.8 table. */
		public List<Baris> baris() {
			List<Baris> out = new ArrayList<>();
			out.add(new Baris("kegiatan", kegiatan, ">= 5", kegiatan < 0 || kegiatan >= SASARAN_KEGIATAN));
			out.add(new Baris("kegiatan_min", kegiatanMin, ">= 4", kegiatanMin < 0 || kegiatanMin >= SASARAN_KEGIATAN_MIN));
			out.add(new Baris("staf", staf, ">= 3", staf < 0 || staf >= SASARAN_STAF));
			out.add(new Baris("ulang", ulang, "<= 2", ulang < 0 || ulang <= SASARAN_ULANG));
			out.add(new Baris("jaccard", jaccard, "<= 0.6", jaccard < 0 || jaccard <= SASARAN_JACCARD));
			out.add(new Baris("selingan", selingan, "2-7", selingan < 0 || selingan >= SASARAN_SELINGAN_MIN && selingan <= SASARAN_SELINGAN_MAKS));
			out.add(new Baris("selingan_ulang", selinganUlang, "0", selinganUlang < 0 || selinganUlang <= 0.0D));
			out.add(new Baris("belokan", belokan, "0.5-2", belokan < 0 || belokan >= SASARAN_BELOKAN_MIN && belokan <= SASARAN_BELOKAN_MAKS));
			out.add(new Baris("zona_bagian", zonaTerbanyak, "<= 0.35", zonaTerbanyak < 0 || zonaTerbanyak <= SASARAN_ZONA_BAGIAN));
			out.add(new Baris("zona_rasio", zonaRasio, "<= 1.5", zonaRasio < 0 || zonaRasio <= SASARAN_ZONA_RASIO));
			out.add(new Baris("gini", gini, "<= 0.35", gini < 0 || gini <= SASARAN_GINI));
			out.add(new Baris("zona_kosong", zonaKosong, "<= 0.10", zonaKosong < 0 || zonaKosong <= SASARAN_ZONA_KOSONG));
			out.add(new Baris("jarak", jarak, "<= 0.02", jarak < 0 || jarak <= SASARAN_JARAK));
			return out;
		}
	}

	/** One report row: metric id (lang {@code kultivator.kota42.ragam.metrik.<id>}), value (-1 = not measured), target text. */
	public record Baris(String id, double nilai, String sasaran, boolean lulus) {
		public boolean diukur() {
			return nilai >= 0.0D;
		}
	}

	/** Readable names of the signatures seen since the server started ({@code ragam <warga>}). */
	private static final Map<Integer, String> NAMA = new HashMap<>();
	/** The last micro-break per resident ({@code situs:id} -> kind and game time). */
	private static final Map<String, Object[]> SELINGAN_AKHIR = new HashMap<>();

	// ================================================================== signatures

	/** The activity signature of {@code k}: key, clip, variant, props, goal kind; bits {@link #INTI} and {@link #RUTIN}. */
	public static int tanda(Kegiatan k) {
		String goal = k.tujuan();
		int colon = goal.indexOf(':');
		if (goal.startsWith("@") && colon > 0 && !goal.startsWith(Kegiatan.XZ)) {
			goal = goal.substring(0, colon);
		} else if (goal.startsWith(Kegiatan.XZ)) {
			goal = Kegiatan.XZ;
		}
		int h = k.kunci().hashCode();
		h = h * 31 + goal.hashCode();
		h = h * 31 + k.klip();
		h = h * 31 + k.varian();
		h = h * 31 + k.propKanan();
		h = h * 31 + k.propKiri();
		h = h * 31 + k.propPunggung();
		h ^= h >>> 16;
		h &= ~(INTI | RUTIN);
		if (Kegiatan.KERJA.equals(k.tujuan()) || k.kunci().equals("kerja") || k.kunci().startsWith("kerja_")) {
			h |= INTI;
		}
		if (k.dalam() || Kegiatan.KASUR.equals(k.tujuan()) || k.kunci().equals("tidur") || k.kunci().equals("pulang")
				|| k.kunci().equals("basuh_muka")) {
			h |= RUTIN;
		}
		synchronized (NAMA) {
			if (NAMA.size() < 4096) {
				NAMA.putIfAbsent(h, k.kunci() + " (" + goal + (k.klip() != 0 ? " #" + k.klip() : "") + ")");
			}
		}
		return h;
	}

	/** The readable name of a signature, or its hex. */
	public static String nama(int tanda) {
		synchronized (NAMA) {
			String n = NAMA.get(tanda);
			return n != null ? n : String.format("%08x", tanda);
		}
	}

	// ================================================================== one resident's day ([samples, segment...])

	/** Different activities of a day (sleep and home included: they are part of the day, but once). */
	static int beda(int[] day) {
		Set<Integer> seen = new HashSet<>();
		for (int i = 1; i < day.length; i++) {
			seen.add(day[i]);
		}
		return seen.size();
	}

	/** The largest repeat count of one activity outside core work and routine (occurrences - 1). */
	static int ulang(int[] day) {
		Map<Integer, Integer> n = new HashMap<>();
		int worst = 0;
		for (int i = 1; i < day.length; i++) {
			int s = day[i];
			if ((s & (INTI | RUTIN)) != 0) {
				continue;
			}
			int c = n.merge(s, 1, Integer::sum);
			worst = Math.max(worst, c - 1);
		}
		return worst;
	}

	/** Jaccard similarity of the activity sets of two days (routine left out); 0 when both are empty. */
	static double jaccard(int[] a, int[] b) {
		Set<Integer> sa = new HashSet<>();
		Set<Integer> sb = new HashSet<>();
		for (int i = 1; i < a.length; i++) {
			if ((a[i] & RUTIN) == 0) {
				sa.add(a[i]);
			}
		}
		for (int i = 1; i < b.length; i++) {
			if ((b[i] & RUTIN) == 0) {
				sb.add(b[i]);
			}
		}
		if (sa.isEmpty() && sb.isEmpty()) {
			return 0.0D;
		}
		Set<Integer> inter = new HashSet<>(sa);
		inter.retainAll(sb);
		Set<Integer> union = new HashSet<>(sa);
		union.addAll(sb);
		return inter.size() / (double) union.size();
	}

	/** Totals of many resident days: [free n, free sum, free min, staff n, staff sum, repeat sum, jaccard n, jaccard sum]. */
	static final class Jumlah {
		int bebasN;
		double bebasS;
		int bebasMin = Integer.MAX_VALUE;
		int stafN;
		double stafS;
		double ulangS;
		int jacN;
		double jacS;

		void tambah(int[] day, int @Nullable [] kemarin, boolean staf) {
			if (day.length == 0 || day[0] < SAMPEL_MIN) {
				return;
			}
			int b = beda(day);
			if (staf) {
				stafN++;
				stafS += b;
			} else {
				bebasN++;
				bebasS += b;
				bebasMin = Math.min(bebasMin, b);
				ulangS += ulang(day);
			}
			if (kemarin != null && kemarin.length > 0 && kemarin[0] >= SAMPEL_MIN) {
				jacN++;
				jacS += jaccard(day, kemarin);
			}
		}

		void simpan(CompoundTag tag) {
			tag.putInt("fBN", bebasN);
			tag.putDouble("fBS", bebasS);
			tag.putInt("fBMin", bebasN > 0 ? bebasMin : -1);
			tag.putInt("fSN", stafN);
			tag.putDouble("fSS", stafS);
			tag.putDouble("fUS", ulangS);
			tag.putInt("fJN", jacN);
			tag.putDouble("fJS", jacS);
			tag.putBoolean("f", true);
		}

		static Jumlah baca(CompoundTag tag) {
			Jumlah j = new Jumlah();
			j.bebasN = tag.getIntOr("fBN", 0);
			j.bebasS = tag.getDoubleOr("fBS", 0.0D);
			int min = tag.getIntOr("fBMin", -1);
			j.bebasMin = min < 0 ? Integer.MAX_VALUE : min;
			j.stafN = tag.getIntOr("fSN", 0);
			j.stafS = tag.getDoubleOr("fSS", 0.0D);
			j.ulangS = tag.getDoubleOr("fUS", 0.0D);
			j.jacN = tag.getIntOr("fJN", 0);
			j.jacS = tag.getDoubleOr("fJS", 0.0D);
			return j;
		}
	}

	// ================================================================== live sampling

	/** One data-pass sample of a resident. */
	static void sampelWarga(MinecraftServer server, long situs, long hari, Penduduk p, Kegiatan k) {
		RagamData.sampel(server, situs, hari, p.id(), tanda(k), p.peran().stationary);
	}

	/** Folds day {@code hari} of every resident with a history into the day's totals (once). */
	static void tutupHari(MinecraftServer server, long situs, long hari) {
		CompoundTag day = RagamData.metrikHari(server, situs, hari);
		if (day.getBooleanOr("f", false)) {
			return;
		}
		Jumlah j = new Jumlah();
		long today = hari + 1L;
		for (int id : RagamData.wargaRiwayat(server, situs)) {
			// relative to the next day: [1] = hari, [2] = the day before it
			int[][] r = RagamData.riwayat(server, situs, id, today);
			j.tambah(r[1], r[2], r[3][0] == 1);
		}
		j.simpan(day);
		RagamData.get(server).ubah();
	}

	/**
	 * One data-pass sample of a place: zone metrics from the balancer's counts and the bodies (positions, seated flags,
	 * walking pairs) of this place seen by {@code players}.
	 */
	static void sampelTempat(MinecraftServer server, long situs, long hari, PetaZona peta, Penyeimbang.Hunian h, List<KultivatorNpc> bodies,
			List<ServerPlayer> players, double jarakMin, double menitTubuh) {
		CompoundTag day = RagamData.metrikHari(server, situs, hari);
		Zona z = zona(peta, h);
		if (z.gini >= 0) {
			day.putInt("gN", day.getIntOr("gN", 0) + 1);
			day.putDouble("gS", day.getDoubleOr("gS", 0.0D) + z.gini);
		}
		if (z.kosong >= 0) {
			day.putInt("kN", day.getIntOr("kN", 0) + 1);
			day.putDouble("kS", day.getDoubleOr("kS", 0.0D) + z.kosong);
		}
		List<BlockPos> pos = new ArrayList<>(bodies.size());
		int[] perZona = new int[peta.jumlah()];
		for (KultivatorNpc npc : bodies) {
			BlockPos at = npc.blockPosition();
			pos.add(at);
			int zi = peta.zonaDi(at);
			if (zi >= 0) {
				perZona[zi]++;
			}
		}
		Tubuh t = tubuh(peta, h, perZona, players);
		if (t != null) {
			day.putInt("tN", day.getIntOr("tN", 0) + 1);
			day.putDouble("tR", day.getDoubleOr("tR", 0.0D) + t.rasio);
			if (t.empatZona) {
				day.putInt("t4", day.getIntOr("t4", 0) + 1);
				day.putDouble("tB", day.getDoubleOr("tB", 0.0D) + t.bagian);
			}
		}
		int[] pairs = pasangan(bodies, jarakMin);
		day.putLong("pP", day.getLongOr("pP", 0L) + pairs[0]);
		day.putLong("pB", day.getLongOr("pB", 0L) + pairs[1]);
		day.putDouble("sM", day.getDoubleOr("sM", 0.0D) + menitTubuh);
		RagamData.get(server).ubah();
	}

	/** Zone metrics of one moment: Gini of occupancy / target (zones with T >= 0.5) and share of empty zones; -1 = none. */
	record Zona(double gini, double kosong) {
	}

	static Zona zona(PetaZona peta, Penyeimbang.Hunian h) {
		Kapasitas.Sasaran s = h.sasaran();
		int n = Math.min(peta.jumlah(), s.jumlah());
		List<Double> rel = new ArrayList<>();
		int harus = 0;
		int kosong = 0;
		for (int z = 0; z < n; z++) {
			double t = s.target()[z];
			if (t >= 0.5D) {
				rel.add(h.occ(z) / t);
			}
			if (peta.zona(z).kapasitas() >= 4 && t >= 1.0D) {
				harus++;
				if (h.occ(z) == 0) {
					kosong++;
				}
			}
		}
		double[] x = new double[rel.size()];
		for (int i = 0; i < x.length; i++) {
			x[i] = rel.get(i);
		}
		return new Zona(x.length >= 2 ? Kapasitas.gini(x) : -1.0D, harus > 0 ? kosong / (double) harus : -1.0D);
	}

	/** Body spread of one moment: the fullest visible zone's share and its share / fair share; null below 3 bodies or 2 zones. */
	record Tubuh(double bagian, double rasio, boolean empatZona) {
	}

	static @Nullable Tubuh tubuh(PetaZona peta, Penyeimbang.Hunian h, int[] perZona, List<ServerPlayer> players) {
		List<Integer> visible = new ArrayList<>();
		for (int z = 0; z < peta.jumlah(); z++) {
			BlockPos c = peta.zona(z).pusat();
			for (ServerPlayer player : players) {
				double dx = player.getX() - c.getX();
				double dz = player.getZ() - c.getZ();
				if (dx * dx + dz * dz <= 128.0D * 128.0D && (h.occ(z) > 0 || perZona[z] > 0)) {
					visible.add(z);
					break;
				}
			}
		}
		return tubuhDari(visible, perZona, h.occ, h.acaraZona);
	}

	/** The fullest zone among {@code visible} (the event zone left out) against its fair share. */
	static @Nullable Tubuh tubuhDari(List<Integer> visible, int[] perZona, int[] occ, int acaraZona) {
		int bodies = 0;
		int data = 0;
		for (int z : visible) {
			bodies += perZona[z];
			data += z < occ.length ? occ[z] : 0;
		}
		if (bodies < 3 || visible.size() < 2 || data <= 0) {
			return null;
		}
		int best = -1;
		for (int z : visible) {
			if (z != acaraZona && (best < 0 || perZona[z] > perZona[best])) {
				best = z;
			}
		}
		if (best < 0) {
			return null;
		}
		double share = perZona[best] / (double) bodies;
		double fair = Math.max(1, best < occ.length ? occ[best] : 0) / (double) data;
		return new Tubuh(share, share / fair, visible.size() >= 4);
	}

	/** [pairs, pairs closer than {@code min}]: two seated bodies and a walking pair are interacting and never count. */
	static int[] pasangan(List<KultivatorNpc> bodies, double min) {
		int n = bodies.size();
		int bad = 0;
		double m2 = min * min;
		for (int i = 0; i < n; i++) {
			KultivatorNpc a = bodies.get(i);
			KultivatorNpc partner = null;
			try {
				partner = WargaApi.mitraJalan(a);
			} catch (RuntimeException ignored) {
				// no walking pairs yet
			}
			for (int j = i + 1; j < n; j++) {
				KultivatorNpc b = bodies.get(j);
				double dx = a.getX() - b.getX();
				double dz = a.getZ() - b.getZ();
				if (dx * dx + dz * dz >= m2 || Math.abs(a.getY() - b.getY()) >= 2.0D) {
					continue;
				}
				if (a.diamDiTitik() && b.diamDiTitik() || b == partner) {
					continue;
				}
				bad++;
			}
		}
		return new int[]{n * (n - 1) / 2, bad};
	}

	// ================================================================== counters of 42S-R

	/** A micro-break of kind {@code jenis} played by resident {@code warga}'s body (42S-R {@code Selingan}). */
	public static void selingan(long situs, int warga, String jenis) {
		MinecraftServer server = KotaRagam.server();
		if (server == null) {
			return;
		}
		long hari = KotaRagam.hari(server);
		CompoundTag day = RagamData.metrikHari(server, situs, hari);
		day.putInt("sN", day.getIntOr("sN", 0) + 1);
		String key = situs + ":" + warga;
		Object[] last = SELINGAN_AKHIR.get(key);
		if (last != null && jenis.equals(last[0])) {
			day.putInt("sU", day.getIntOr("sU", 0) + 1);
		}
		if (SELINGAN_AKHIR.size() > 8192) {
			SELINGAN_AKHIR.clear();
		}
		SELINGAN_AKHIR.put(key, new Object[]{jenis, server.overworld().getGameTime()});
		RagamData.get(server).ubah();
	}

	/** A spontaneous turn taken by resident {@code warga} (42S-R {@code Belokan}). */
	public static void belokan(long situs, int warga) {
		MinecraftServer server = KotaRagam.server();
		if (server == null) {
			return;
		}
		CompoundTag day = RagamData.metrikHari(server, situs, KotaRagam.hari(server));
		day.putInt("bN", day.getIntOr("bN", 0) + 1);
		RagamData.get(server).ubah();
	}

	/** The last micro-break of a resident: {kind, game time}, or null. */
	public static Object @Nullable [] selinganAkhir(long situs, int warga) {
		return SELINGAN_AKHIR.get(situs + ":" + warga);
	}

	// ================================================================== the report

	/** The metrics of place {@code situs} on world day {@code hari} (today: live, so far). */
	public static Laporan laporan(long situs, long hari) {
		MinecraftServer server = KotaRagam.server();
		if (server == null) {
			return Laporan.KOSONG;
		}
		long today = KotaRagam.hari(server);
		CompoundTag day = RagamData.metrikLihat(server, situs, hari);
		Jumlah j;
		int residents = 0;
		if (hari >= today || !day.getBooleanOr("f", false)) {
			j = new Jumlah();
			for (int id : RagamData.wargaRiwayat(server, situs)) {
				int[][] r = RagamData.riwayat(server, situs, id, today);
				int idx = (int) (today - hari);
				if (idx < 0 || idx > 1) {
					continue;
				}
				j.tambah(r[idx], r[idx + 1], r[3][0] == 1);
			}
		} else {
			j = Jumlah.baca(day);
		}
		residents = j.bebasN + j.stafN;
		int tN = day.getIntOr("tN", 0);
		int t4 = day.getIntOr("t4", 0);
		int gN = day.getIntOr("gN", 0);
		int kN = day.getIntOr("kN", 0);
		long pP = day.getLongOr("pP", 0L);
		double sM = day.getDoubleOr("sM", 0.0D);
		int sN = day.getIntOr("sN", 0);
		int dataResidents = Math.max(1, RagamData.wargaRiwayat(server, situs).size());
		return new Laporan(situs, hari,
				j.bebasN > 0 ? j.bebasS / j.bebasN : -1,
				j.bebasN > 0 ? j.bebasMin : -1,
				j.stafN > 0 ? j.stafS / j.stafN : -1,
				j.bebasN > 0 ? j.ulangS / j.bebasN : -1,
				j.jacN > 0 ? j.jacS / j.jacN : -1,
				sM >= 1.0D && sN > 0 ? sN / sM : -1,
				sN > 0 ? day.getIntOr("sU", 0) / (double) sN : -1,
				day.contains("bN") ? day.getIntOr("bN", 0) / (double) dataResidents : -1,
				t4 > 0 ? day.getDoubleOr("tB", 0.0D) / t4 : -1,
				tN > 0 ? day.getDoubleOr("tR", 0.0D) / tN : -1,
				gN > 0 ? day.getDoubleOr("gS", 0.0D) / gN : -1,
				kN > 0 ? day.getDoubleOr("kS", 0.0D) / kN : -1,
				pP > 0 ? day.getLongOr("pB", 0L) / (double) pP : -1,
				residents, tN);
	}

	private MetrikRagam() {
	}
}
