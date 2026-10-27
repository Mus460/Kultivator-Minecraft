package dev.kultivator.dunia.kota42.ragam;

import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.api.JenisHubungan;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The occupancy balancer and anti-crowding (DESIGN 42.4A.2, contract C42.S1), registered through the 38NL hook
 * {@code TempatHuni.addPenyeimbang}.
 * <ul>
 *   <li><b>General goals</b> ({@code pasar}, {@code kedai}, {@code makan}, {@code jalan_jalan}, {@code obrol}, {@code main},
 *       {@code teh}, {@code kuil}, {@code belanja}: every titik kind of {@code zona.json "umum"} plus {@code @kedai},
 *       {@code @depan_lapak}, {@code @jalan}) become a weighted draw among the titik of that kind in <b>every</b> zone of the
 *       place: {@code w = max(0, C_z - occ_z) / C_z x e^(-distanceFromHome / 220) x preference x (2 when occ_z < L_z)}.
 *       Preference: a friend ({@code SAHABAT}, {@code KEKASIH}, {@code PASANGAN}) already going to that zone x1.5, the
 *       resident's own favourite stall x1.6, an enemy there x0.4, plus every {@link #addPreferensi} factor (42A-2 habits).</li>
 *   <li><b>Reservations</b>: the picked titik is reserved ({@code TempatHuni.pesan}) until the end of the activity (the
 *       two-hour band the 38 caller caches goals by) plus one world minute; the same activity asks again and gets the same
 *       spot, so the data position and the body agree. A titik that holds more than one ({@code obrol} 2, {@code main} 3,
 *       {@code catur} 5, {@code sumur} 4, {@code panggung} 12) takes its extra users on a ring round it; at most
 *       {@value #ANTRE_MAKS} queue in front, then the titik is full.</li>
 *   <li><b>Overflow</b>: no free weight left -> the nearest titik of the same kind in a zone still under its target; in a
 *       busy hour when every zone of the kind is past it, the relatively emptiest zone (occupancy / capacity); no usable
 *       titik at all -> null, so the built-in 38 choice (stand beside, queue) stays.</li>
 *   <li><b>Anti-crowding</b>: spots that are not titik (in front of a stall, a street node, a ring place) keep
 *       {@code kotaWarga.jarakMin} from every other handed-out spot, shifted to a free neighbour cell within 3 blocks.
 *       Stroll routes ({@code @jalan}) prefer quiet street segments through the same capacity weight.</li>
 *   <li><b>Per cycle</b> ({@link #siklus}): occupancy recount from the data (every resident, with or without a body), targets
 *       of the hour ({@link Kapasitas#hitung}); residents of a zone over its cap with the shortest remaining activity are
 *       marked to move: their next pick skips that zone (never mid-clip).</li>
 * </ul>
 * Memory only. The simulation of {@code uji kota_hidup sebaran simulasi} runs the same code on a separate {@link Hunian}.
 */
public final class Penyeimbang {

	/** A preference factor (>= 0, 1 = neutral) of resident {@code p} ({@code w} its stored person, may be null) for titik {@code t}. */
	@FunctionalInterface
	public interface Preferensi {
		double faktor(TempatHuni huni, Penduduk p, @Nullable Warga w, Titik t, String jenisZona);
	}

	private static final List<Preferensi> PREFERENSI = new CopyOnWriteArrayList<>();

	/** Adds a preference factor (42A-2 habits, 42C favourite shop). */
	public static void addPreferensi(Preferensi preferensi) {
		if (preferensi != null) {
			PREFERENSI.add(preferensi);
		}
	}

	/** Distance scale of {@code e^(-d / 220)}. */
	static final double SKALA_JARAK = 220.0D;
	/** People allowed to queue at a full titik before it counts as full. */
	static final int ANTRE_MAKS = 2;
	/** Ticks per world minute. */
	static final long MENIT = 17L;

	/** One pick: the activity key it was made for, the goal, its zone, expiry, and the titik it belongs to. */
	record Pilihan(String kunci, TempatHuni.Tujuan tujuan, int zona, long sampai, long titik) {
	}

	/** Weight factor of a zone clearly under its target / at or over it (on top of the free-share weight, 42.4A.2). */
	static final double UNDER_TARGET = 2.5D;
	static final double OVER_TARGET = 0.4D;

	/** The occupancy state of one place (live) or of one simulation run. */
	public static final class Hunian {
		public final long situs;
		final boolean simulasi;
		/** Picks of the balancer by resident. */
		final Map<Integer, Pilihan> pilihan = new HashMap<>();
		/** Zone of the residents whose goal is not the balancer's (bed, home, own work spot, a duty titik), from the data pass. */
		final Map<Integer, Integer> zonaTetap = new HashMap<>();
		/** Residents whose current goal is a general one (the free residents of the hour). */
		final Set<Integer> bebas = new HashSet<>();
		/** What each free resident wants right now (its general goal without the number): the demand the targets follow. */
		final Map<Integer, String> minat = new HashMap<>();
		/** Residents indoors (asleep, shut in a room): counted in {@link #occ} but they have no body to see. */
		final Set<Integer> dalam = new HashSet<>();
		/** Residents to move away from {@link #pindahDari} at their next activity change. */
		final Map<Integer, Integer> pindahDari = new HashMap<>();
		/** Reservations of a simulation (pos -> resident). */
		final Map<Long, Integer> pesanSim = new HashMap<>();
		int[] occ = new int[0];
		/** {@link #occ} without the residents indoors: the people who can be seen (body spread is judged against it). */
		int[] luar = new int[0];
		int[] tetap = new int[0];
		Kapasitas.Sasaran sasaran = Kapasitas.Sasaran.KOSONG;
		int jam = -1;
		int acaraZona = -1;
		@Nullable String acara;
		/** World day for the week rhythm ({@link Kapasitas#TANPA_HARI} = none). */
		long hari = Kapasitas.TANPA_HARI;
		/** Residents of the place: the free count before the first data pass has run. */
		int perkiraan;
		int petaTitik = -1;

		Hunian(long situs, boolean simulasi) {
			this.situs = situs;
			this.simulasi = simulasi;
		}

		/** Data occupancy of zone {@code z}. */
		public int occ(int z) {
			return z >= 0 && z < occ.length ? occ[z] : 0;
		}

		/** Occupancy of zone {@code z} without the residents indoors. */
		public int luar(int z) {
			return z >= 0 && z < luar.length ? luar[z] : 0;
		}

		public Kapasitas.Sasaran sasaran() {
			return sasaran;
		}

		/** The zone of resident {@code id}'s goal (balancer pick, else the data pass), or -1. */
		public int zonaWarga(int id, long now) {
			Pilihan p = pilihan.get(id);
			if (p != null && p.sampai() > now) {
				return p.zona();
			}
			return zonaTetap.getOrDefault(id, -1);
		}

		/** The live goal of resident {@code id} picked by the balancer, or null. */
		public TempatHuni.@Nullable Tujuan tujuan(int id, long now) {
			Pilihan p = pilihan.get(id);
			return p != null && p.sampai() > now ? p.tujuan() : null;
		}

		/** Residents with a live pick (commands). */
		public int jumlahPilihan(long now) {
			int n = 0;
			for (Pilihan p : pilihan.values()) {
				if (p.sampai() > now) {
					n++;
				}
			}
			return n;
		}

		/** The reserved titik positions with their holder (commands, {@code sebaran titik}). */
		public Map<Long, Integer> pesananTitik(long now) {
			Map<Long, Integer> out = new HashMap<>();
			pilihan.forEach((id, p) -> {
				if (p.sampai() > now && p.tujuan().nyata()) {
					out.put(p.tujuan().pos().asLong(), id);
				}
			});
			return out;
		}

		/** Number of free residents of the last recount. */
		public int bebas() {
			return bebas.size();
		}
	}

	private static final Map<Long, Hunian> HUNIAN = new HashMap<>();

	/** The live state of a place (created on first use). */
	public static Hunian hunian(long situs) {
		return HUNIAN.computeIfAbsent(situs, s -> new Hunian(s, false));
	}

	/** The live state of a place, or null. */
	public static @Nullable Hunian ada(long situs) {
		return HUNIAN.get(situs);
	}

	static void lupakan(long situs) {
		HUNIAN.remove(situs);
	}

	static void lupakanSemua() {
		HUNIAN.clear();
	}

	static Hunian simulasi(long situs) {
		return new Hunian(situs, true);
	}

	// ================================================================== the hook

	/** {@code TempatHuni.Penyeimbang} registered by {@code KotaRagam}. */
	static TempatHuni.@Nullable Tujuan hook(TempatHuni huni, ServerLevel level, Penduduk p, Kegiatan k, int jam, boolean kunciUntuk) {
		if (!KotaConfig.warga().sebaranAktif || !Kapasitas.umum(k.tujuan())) {
			return null;
		}
		PetaZona peta = PetaZona.untuk(level, huni);
		if (peta.jumlah() == 0) {
			return null;
		}
		long now = level.getGameTime();
		long waktu = level.getServer().overworld().getOverworldClockTime();
		Hunian h = hunian(huni.situsId);
		siapkan(h, huni, peta, jam, now);
		return pilih(h, huni, level, peta, p, k, jam, kunciUntuk, now, sampai(p, huni.jenis, waktu, now));
	}

	/**
	 * C42.S1: the titik resident {@code warga} should use for goal {@code jenis} (a titik kind or {@code @kedai},
	 * {@code @depan_lapak:<n>}, {@code @jalan:<n>}), reserved for the activity; null to leave the 38 choice (balancer off,
	 * not a general goal, the place not active).
	 */
	public static TempatHuni.@Nullable Tujuan pilihTitik(TempatHuni huni, Penduduk warga, String jenis) {
		ServerLevel level = KotaRagam.level(huni);
		if (level == null) {
			return null;
		}
		long waktu = level.getServer().overworld().getOverworldClockTime();
		int jam = Jadwal.saat(warga, huni.jenis, waktu).jam();
		return hook(huni, level, warga, Kegiatan.di(jenis.startsWith("@") ? "ragam" : jenis, jenis, 0, 0), jam, false);
	}

	/** Expiry of a pick: the end of the resident's current two-hour band (the 38 caller's goal cache) plus a world minute. */
	static long sampai(Penduduk p, String jenisTempat, long waktu, long now) {
		Jadwal.Saat s = Jadwal.saat(p, jenisTempat, waktu);
		int menitSisa = (s.jam() / 2 * 2 + 2 - s.jam()) * 60 - s.menit();
		return now + Math.max(1, menitSisa) * 1000L / 60L + MENIT;
	}

	/** Targets and occupancy ready for hour {@code jam} (recomputed when the hour or the map changed). */
	static void siapkan(Hunian h, TempatHuni huni, PetaZona peta, int jam, long now) {
		h.perkiraan = huni.penduduk().size();
		if (h.occ.length != peta.jumlah() || h.petaTitik != peta.dariTitik) {
			h.pilihan.clear();
			h.zonaTetap.clear();
			h.pindahDari.clear();
			h.petaTitik = peta.dariTitik;
			hitung(h, peta, jam, now);
		} else if (h.jam != jam) {
			hitung(h, peta, jam, now);
		}
	}

	// ================================================================== the pick

	static TempatHuni.@Nullable Tujuan pilih(Hunian h, TempatHuni huni, @Nullable ServerLevel level, PetaZona peta, Penduduk p, Kegiatan k, int jam,
			boolean kunciUntuk, long now, long sampai) {
		String to = k.tujuan();
		String key = k.kunci() + "|" + to + "|" + jam / 2;
		catatMinat(h, p.id(), true, to);
		Pilihan old = h.pilihan.get(p.id());
		if (old != null && old.kunci().equals(key) && old.sampai() > now
				&& (!kunciUntuk || h.simulasi || !huni.terkunci(old.tujuan().pos(), p.id()))) {
			return old.tujuan();
		}
		boolean depan = to.startsWith(Kegiatan.DEPAN_LAPAK);
		boolean jalan = to.startsWith(Kegiatan.JALAN);
		List<Titik> cands;
		if (Kegiatan.KEDAI.equals(to)) {
			cands = peta.kursiKedai;
			if (cands.isEmpty()) {
				cands = gabung(huni.titik("kursi"), huni.titik("kursi_lantai"));
			}
		} else if (depan) {
			cands = peta.lapak;
		} else if (jalan) {
			cands = huni.titik("jalan");
		} else {
			cands = untukPeran(gabung(huni.titik(to), to.equals(kembar(to)) ? List.of() : huni.titik(kembar(to))), p.peran());
		}
		if (cands.isEmpty()) {
			return null;
		}
		Warga w = null;
		try {
			w = WargaApi.warga(huni.situsId, p.id());
		} catch (RuntimeException ignored) {
			// 42A not raised for this place yet: no friend preference
		}
		Map<Integer, Double> zonaSuka = sukaZona(h, w, now);
		Map<Long, Integer> pemakai = pemakai(h, now, p.id());
		BlockPos home = huni.rumahPos(p);
		Kapasitas.Sasaran s = h.sasaran;
		int avoid = h.pindahDari.getOrDefault(p.id(), -1);
		long langganan = -1L;
		if (!peta.lapak.isEmpty() && (depan || "lapak".equals(to) || "kerja:kios".equals(to))) {
			langganan = peta.lapak.get((int) Math.floorMod(p.benih() >>> 7, (long) peta.lapak.size())).pos().asLong();
		}
		int n = cands.size();
		double[] wt = new double[n];
		double sum = 0.0D;
		for (int i = 0; i < n; i++) {
			Titik t = cands.get(i);
			int z = peta.zonaDi(t.pos());
			if (z < 0 || z >= s.jumlah() || z == avoid) {
				continue;
			}
			int cap = Math.max(1, s.topi()[z]);
			int o = h.occ(z);
			if (old != null && old.zona() == z && old.sampai() > now) {
				// the resident's own place in the count is free for itself
				o = Math.max(0, o - 1);
			}
			double free = Math.max(0, cap - o) / (double) cap;
			if (free <= 0.0D) {
				continue;
			}
			long pos = t.pos().asLong();
			int muat = muat(t, depan, jalan);
			int dipakai = pemakai.getOrDefault(pos, 0);
			if (dipakai >= muat + ANTRE_MAKS) {
				continue;
			}
			if (!depan && !jalan && dipakai == 0 && diambil(h, huni, t.pos(), p.id(), kunciUntuk, now)) {
				if (muat <= 1) {
					continue;
				}
			}
			double x = free * Math.exp(-Math.sqrt(home.distSqr(t.pos())) / SKALA_JARAK);
			if (o < s.lantai()[z]) {
				x *= 2.0D;
			}
			// pull toward the zone's own target: a zone under it draws, a zone at or over it draws less (small towns, where a zone
			// holds one or two people and the flat free-share weight alone scatters the counts)
			x *= o + 1.0D <= s.target()[z] ? UNDER_TARGET : o >= s.target()[z] ? OVER_TARGET : 1.0D;
			if (dipakai >= muat) {
				x *= 0.3D;
			}
			x *= zonaSuka.getOrDefault(z, 1.0D);
			if (pos == langganan) {
				x *= 1.6D;
			}
			if (!PREFERENSI.isEmpty()) {
				String kind = peta.zona(z).jenis();
				for (Preferensi pref : PREFERENSI) {
					x *= Math.max(0.0D, pref.faktor(huni, p, w, t, kind));
				}
			}
			wt[i] = x;
			sum += x;
		}
		int pick = -1;
		if (sum > 0.0D) {
			double r = acak(p.benih() ^ key.hashCode() * 0x9E3779B97F4A7C15L ^ now / 1000L) * sum;
			for (int i = 0; i < n; i++) {
				r -= wt[i];
				if (wt[i] > 0.0D && r <= 0.0D) {
					pick = i;
					break;
				}
			}
			if (pick < 0) {
				for (int i = n - 1; i >= 0; i--) {
					if (wt[i] > 0.0D) {
						pick = i;
						break;
					}
				}
			}
		} else {
			pick = luapan(h, huni, peta, cands, pemakai, home, p.id(), depan, jalan, kunciUntuk, now);
		}
		if (pick < 0) {
			return null;
		}
		Titik t = cands.get(pick);
		int z = peta.zonaDi(t.pos());
		int dipakai = pemakai.getOrDefault(t.pos().asLong(), 0);
		TempatHuni.Tujuan goal = tujuan(h, huni, level, t, depan, jalan, dipakai, p, kunciUntuk, now);
		if (goal == null) {
			return null;
		}
		if (goal.nyata()) {
			if (h.simulasi) {
				h.pesanSim.values().removeIf(id -> id == p.id());
				h.pesanSim.put(goal.pos().asLong(), p.id());
			} else if (!huni.pesan(goal.pos(), p.id(), sampai, now)) {
				return null;
			}
		}
		catat(h, p.id(), new Pilihan(key, goal, z, sampai, t.pos().asLong()));
		h.pindahDari.remove(p.id());
		return goal;
	}

	/** Records a pick and moves the resident's count to its new zone. */
	private static void catat(Hunian h, int id, Pilihan baru) {
		Pilihan old = h.pilihan.put(id, baru);
		int from = old != null ? old.zona() : h.zonaTetap.getOrDefault(id, -1);
		if (from >= 0 && from < h.occ.length) {
			h.occ[from] = Math.max(0, h.occ[from] - 1);
		}
		if (baru.zona() >= 0 && baru.zona() < h.occ.length) {
			h.occ[baru.zona()]++;
		}
		h.zonaTetap.remove(id);
		h.bebas.add(id);
		h.dalam.remove(id);
	}

	/** How many people one titik holds (a stall front: the buyers; a street node: one walker). */
	private static int muat(Titik t, boolean depan, boolean jalan) {
		if (depan) {
			return 1;
		}
		if (jalan) {
			return 1;
		}
		return Math.max(1, Kapasitas.kapasitasTitik(t.jenis()));
	}

	/** True when titik {@code pos} itself is taken by someone else (a body's lock or a reservation). */
	private static boolean diambil(Hunian h, TempatHuni huni, BlockPos pos, int id, boolean kunciUntuk, long now) {
		if (h.simulasi) {
			Integer holder = h.pesanSim.get(pos.asLong());
			return holder != null && holder != id;
		}
		return huni.dipesan(pos, id, now) || kunciUntuk && huni.terkunci(pos, id);
	}

	/** Every titik of {@code cands} that no free weight reached: the nearest one to home in a zone under its target. */
	private static int luapan(Hunian h, TempatHuni huni, PetaZona peta, List<Titik> cands, Map<Long, Integer> pemakai, BlockPos home, int id, boolean depan,
			boolean jalan, boolean kunciUntuk, long now) {
		int best = -1;
		double bestD = Double.MAX_VALUE;
		Kapasitas.Sasaran s = h.sasaran;
		for (int i = 0; i < cands.size(); i++) {
			Titik t = cands.get(i);
			int z = peta.zonaDi(t.pos());
			if (z < 0 || z >= s.jumlah() || h.occ(z) >= Math.max(1.0D, s.target()[z])) {
				continue;
			}
			int muat = muat(t, depan, jalan);
			int dipakai = pemakai.getOrDefault(t.pos().asLong(), 0);
			if (dipakai >= muat + ANTRE_MAKS || muat <= 1 && !depan && !jalan && diambil(h, huni, t.pos(), id, kunciUntuk, now)) {
				continue;
			}
			double d = home.distSqr(t.pos());
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		if (best >= 0) {
			return best;
		}
		// every zone of this kind is past its target (a busy hour): the relatively emptiest zone still spreads them evenly
		double bestFill = Double.MAX_VALUE;
		for (int i = 0; i < cands.size(); i++) {
			Titik t = cands.get(i);
			int z = peta.zonaDi(t.pos());
			if (z < 0 || z >= s.jumlah()) {
				continue;
			}
			int muat = muat(t, depan, jalan);
			int dipakai = pemakai.getOrDefault(t.pos().asLong(), 0);
			if (dipakai >= muat + ANTRE_MAKS || muat <= 1 && !depan && !jalan && diambil(h, huni, t.pos(), id, kunciUntuk, now)) {
				continue;
			}
			double fill = (h.occ(z) + 1.0D) / Math.max(1, peta.zona(z).kapasitas()) + Math.sqrt(home.distSqr(t.pos())) / 4096.0D;
			if (fill < bestFill) {
				bestFill = fill;
				best = i;
			}
		}
		return best;
	}

	/** The goal at titik {@code t}: the titik itself, a ring place round it, the queue, a stall front or a street spot. */
	private static TempatHuni.@Nullable Tujuan tujuan(Hunian h, TempatHuni huni, @Nullable ServerLevel level, Titik t, boolean depan, boolean jalan,
			int dipakai, Penduduk p, boolean kunciUntuk, long now) {
		double min = KotaConfig.warga().jarakMin;
		if (depan) {
			Direction face = t.hadap();
			Direction side = face.getClockWise();
			for (int out : new int[]{2, 3, 1}) {
				for (int shift : new int[]{0, -1, 1, -2, 2}) {
					BlockPos at = berdiri(level, t.pos().relative(face, out).relative(side, shift));
					if (at != null && lapang(h, at, p.id(), min, now)) {
						return new TempatHuni.Tujuan(at, face.getOpposite(), "lapak", false);
					}
				}
			}
			return null;
		}
		if (jalan) {
			BlockPos at = geser(h, level, t.pos(), p.id(), min, now);
			return at == null ? null : new TempatHuni.Tujuan(at, t.hadap(), "jalan", false);
		}
		int muat = Math.max(1, Kapasitas.kapasitasTitik(t.jenis()));
		if (dipakai == 0 && !diambil(h, huni, t.pos(), p.id(), kunciUntuk, now)) {
			return new TempatHuni.Tujuan(t.pos(), t.hadap(), t.jenis(), true);
		}
		if (dipakai < muat) {
			// the others of a chat corner, a play spot, a chess board, a well: a ring round it, facing in
			for (int k = 0; k < SEKITAR.length; k++) {
				int ring = Math.floorMod(p.id() + k, SEKITAR.length);
				BlockPos at = berdiri(level, t.pos().offset(SEKITAR[ring][0], 0, SEKITAR[ring][1]));
				if (at != null && lapang(h, at, p.id(), min, now) && (h.simulasi || !huni.terkunci(at, p.id()))) {
					return new TempatHuni.Tujuan(at, hadapKe(at, t.pos()), t.jenis(), false);
				}
			}
			for (int[] off : SEKITAR) {
				BlockPos at = berdiri(level, t.pos().offset(off[0] * 2, 0, off[1] * 2));
				if (at != null && lapang(h, at, p.id(), min, now)) {
					return new TempatHuni.Tujuan(at, hadapKe(at, t.pos()), t.jenis(), false);
				}
			}
		}
		// the queue: two at most, in a line out from the titik's front
		int place = Math.max(0, dipakai - muat);
		BlockPos queue = berdiri(level, t.pos().relative(t.hadap(), 2 + place * 2));
		return queue == null ? null : new TempatHuni.Tujuan(queue, t.hadap().getOpposite(), t.jenis(), false);
	}

	/** True when no other handed-out spot (not a titik) lies within {@code min} blocks of {@code at}. */
	private static boolean lapang(Hunian h, BlockPos at, int id, double min, long now) {
		double m2 = min * min;
		for (Map.Entry<Integer, Pilihan> e : h.pilihan.entrySet()) {
			Pilihan other = e.getValue();
			if (e.getKey() == id || other.sampai() <= now) {
				continue;
			}
			BlockPos o = other.tujuan().pos();
			double dx = o.getX() - at.getX();
			double dz = o.getZ() - at.getZ();
			if (dx * dx + dz * dz < m2 && Math.abs(o.getY() - at.getY()) < 2) {
				return false;
			}
		}
		return true;
	}

	/** {@code at}, or the nearest free standing cell within 3 blocks that keeps {@code min} from every other spot. */
	private static @Nullable BlockPos geser(Hunian h, @Nullable ServerLevel level, BlockPos at, int id, double min, long now) {
		BlockPos here = berdiri(level, at);
		if (here != null && lapang(h, here, id, min, now)) {
			return here;
		}
		for (int r = 1; r <= 3; r++) {
			for (int k = 0; k < SEKITAR.length; k++) {
				int[] off = SEKITAR[Math.floorMod(id + k, SEKITAR.length)];
				BlockPos c = berdiri(level, at.offset(off[0] * r, 0, off[1] * r));
				if (c != null && lapang(h, c, id, min, now)) {
					return c;
				}
			}
		}
		return null;
	}

	/** The eight directions round a titik (x, z). */
	private static final int[][] SEKITAR = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-1, 1}, {-1, -1}, {1, -1}};

	private static Direction hadapKe(BlockPos from, BlockPos to) {
		return Direction.fromYRot(Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ())));
	}

	/**
	 * A block a person can stand in at {@code at} or one up or down (sturdy floor, two free blocks, no fluid), or null (the
	 * same rule as the 38 caller). Without a level (simulation) the block itself.
	 */
	static @Nullable BlockPos berdiri(@Nullable ServerLevel level, BlockPos at) {
		if (level == null) {
			return at;
		}
		for (int dy : new int[]{0, 1, -1}) {
			BlockPos feet = at.above(dy);
			if (!level.isLoaded(feet)) {
				return null;
			}
			BlockPos floor = feet.below();
			if (level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP) && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
					&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty() && level.getFluidState(feet).isEmpty()) {
				return feet;
			}
		}
		return null;
	}

	/** Live picks per titik position (who already uses each titik), the asking resident left out. */
	private static Map<Long, Integer> pemakai(Hunian h, long now, int id) {
		Map<Long, Integer> out = new HashMap<>();
		for (Map.Entry<Integer, Pilihan> e : h.pilihan.entrySet()) {
			if (e.getKey() != id && e.getValue().sampai() > now) {
				out.merge(e.getValue().titik(), 1, Integer::sum);
			}
		}
		return out;
	}

	/** Zone factors from the resident's bonds: friends and partners going there x1.5, enemies x0.4. */
	private static Map<Integer, Double> sukaZona(Hunian h, @Nullable Warga w, long now) {
		if (w == null || w.hubungan.length == 0) {
			return Map.of();
		}
		Map<Integer, Double> out = new HashMap<>();
		for (long bond : w.hubungan) {
			JenisHubungan jenis = JenisHubungan.jenis(bond);
			if (jenis == null) {
				continue;
			}
			double f = switch (jenis) {
				case SAHABAT, KEKASIH, PASANGAN -> 1.5D;
				case MUSUH -> 0.4D;
				default -> 1.0D;
			};
			if (f == 1.0D) {
				continue;
			}
			Warga other = WargaApi.byId(JenisHubungan.idLain(bond));
			if (other == null || other.slot < 0) {
				continue;
			}
			int z = h.zonaWarga(other.slot, now);
			if (z >= 0) {
				out.merge(z, f, (a, b) -> a * b);
			}
		}
		return out;
	}

	// ================================================================== recount

	/**
	 * Occupancy recount and targets of hour {@code jam} (every cycle of the place): expired picks dropped, {@code occ_z} from
	 * the picks and the data pass, {@code T/C/L} from {@link Kapasitas#hitung}; residents of an over-full zone with the
	 * shortest remaining activity are marked to move at their next change.
	 */
	static void hitung(Hunian h, PetaZona peta, int jam, long now) {
		int n = peta.jumlah();
		h.pilihan.values().removeIf(p -> p.sampai() <= now || p.zona() >= n);
		h.pesanSim.entrySet().removeIf(e -> !h.pilihan.containsKey(e.getValue()));
		int[] occ = new int[n];
		int[] tetap = new int[n];
		for (Pilihan p : h.pilihan.values()) {
			if (p.zona() >= 0) {
				occ[p.zona()]++;
			}
		}
		for (Map.Entry<Integer, Integer> e : h.zonaTetap.entrySet()) {
			int z = e.getValue();
			if (z >= 0 && z < n && !h.pilihan.containsKey(e.getKey())) {
				occ[z]++;
				tetap[z]++;
			}
		}
		int bebas = 0;
		for (int id : h.bebas) {
			if (!h.zonaTetap.containsKey(id) || h.pilihan.containsKey(id)) {
				bebas++;
			}
		}
		if (h.bebas.isEmpty() && h.zonaTetap.isEmpty()) {
			// no data pass yet (the place just woke): assume half of the residents are out and about
			bebas = h.perkiraan / 2;
		}
		int[] kap = new int[n];
		String[] jenis = new String[n];
		for (int z = 0; z < n; z++) {
			kap[z] = peta.zona(z).kapasitas();
			jenis[z] = peta.zona(z).jenis();
		}
		KotaConfig.Warga cfg = KotaConfig.warga();
		int[] luar = occ.clone();
		for (int id : h.dalam) {
			Integer z = h.pilihan.containsKey(id) ? null : h.zonaTetap.get(id);
			if (z != null && z >= 0 && z < n && luar[z] > 0) {
				luar[z]--;
			}
		}
		h.occ = occ;
		h.luar = luar;
		h.tetap = tetap;
		h.jam = jam;
		Map<String, Integer> wish = new HashMap<>();
		for (Map.Entry<Integer, String> e : h.minat.entrySet()) {
			if (h.bebas.contains(e.getKey())) {
				wish.merge(e.getValue(), 1, Integer::sum);
			}
		}
		double[] minat = new double[n];
		for (Map.Entry<String, Integer> e : wish.entrySet()) {
			int[] where = peta.sebaran(e.getKey());
			long all = 0L;
			for (int v : where) {
				all += v;
			}
			for (int z = 0; z < n && all > 0L; z++) {
				minat[z] += e.getValue() * where[z] / (double) all;
			}
		}
		h.sasaran = Kapasitas.hitung(kap, jenis, tetap, Math.max(bebas, h.pilihan.size()), jam, h.acara, h.acaraZona, cfg.kapZona, cfg.lantaiZona,
				h.hari, minat);
		h.pindahDari.clear();
		for (int z = 0; z < n; z++) {
			int over = occ[z] - h.sasaran.topi()[z];
			if (over <= 0 || z == h.acaraZona) {
				continue;
			}
			List<Map.Entry<Integer, Pilihan>> here = new ArrayList<>();
			for (Map.Entry<Integer, Pilihan> e : h.pilihan.entrySet()) {
				if (e.getValue().zona() == z) {
					here.add(e);
				}
			}
			here.sort((a, b) -> Long.compare(a.getValue().sampai(), b.getValue().sampai()));
			for (int i = 0; i < over && i < here.size(); i++) {
				h.pindahDari.put(here.get(i).getKey(), z);
			}
		}
	}

	/**
	 * The data pass of one resident ({@code KotaRagam}, every 10 s per place, and the simulation): where its goal lies when
	 * it is not the balancer's. {@code zona} -1 = unknown (staying put, away).
	 */
	static void catatData(Hunian h, int id, boolean umum, int zona) {
		catatData(h, id, umum, zona, false);
	}

	/** As above; {@code dalam} = the resident is indoors (asleep, shut in a room) and so has no body to be seen. */
	static void catatData(Hunian h, int id, boolean umum, int zona, boolean dalam) {
		if (dalam && !umum) {
			h.dalam.add(id);
		} else {
			h.dalam.remove(id);
		}
		if (umum) {
			h.bebas.add(id);
			h.zonaTetap.remove(id);
		} else {
			h.bebas.remove(id);
			if (zona >= 0) {
				h.zonaTetap.put(id, zona);
			} else {
				h.zonaTetap.remove(id);
			}
			Pilihan old = h.pilihan.remove(id);
			if (old != null && old.tujuan().nyata() && h.simulasi) {
				h.pesanSim.remove(old.tujuan().pos().asLong());
			}
		}
	}

	/** Drops a resident from the counts (left the place, removed). */
	static void lupakanWarga(Hunian h, int id) {
		h.pilihan.remove(id);
		h.zonaTetap.remove(id);
		h.bebas.remove(id);
		h.dalam.remove(id);
		h.minat.remove(id);
		h.pindahDari.remove(id);
	}

	/** Notes what resident {@code id} wants (a general goal) or forgets it (a goal that is not the balancer's). */
	static void catatMinat(Hunian h, int id, boolean umum, String tujuan) {
		if (umum) {
			int colon = tujuan.indexOf(':');
			h.minat.put(id, colon > 0 && tujuan.startsWith("@") ? tujuan.substring(0, colon) : tujuan);
		} else {
			h.minat.remove(id);
		}
	}

	/** True when the resident's activity keeps it indoors (no body to see). */
	static boolean dalam(Kegiatan k) {
		return k.dalam() || Kegiatan.KASUR.equals(k.tujuan());
	}

	/**
	 * The zone of a goal that is not the balancer's: the bed, the house, the own work spot, a scene, a duty titik (the
	 * resident's by seed); -1 when it stays where it stands or nothing is known.
	 */
	static int zonaTetap(TempatHuni huni, PetaZona peta, Penduduk p, Kegiatan k) {
		String to = k.tujuan();
		BlockPos at = null;
		if (k.dalam() || Kegiatan.KASUR.equals(to)) {
			Titik bed = huni.kasur(p);
			at = bed != null ? bed.pos() : huni.rumahPos(p);
		} else if (Kegiatan.RUMAH.equals(to)) {
			at = huni.rumahPos(p);
		} else if (Kegiatan.KERJA.equals(to)) {
			Titik own = huni.kerja(p);
			at = own != null ? own.pos() : null;
		} else if (to.startsWith(Kegiatan.XZ)) {
			int[] xz = Jadwal.xz(to.substring(Kegiatan.XZ.length()));
			at = xz == null ? null : new BlockPos(xz[0], huni.pusat().getY(), xz[1]);
		} else if (!Kegiatan.TETAP.equals(to) && !to.startsWith("@")) {
			List<Titik> list = huni.titik(to);
			if (list.isEmpty()) {
				list = huni.titik(kembar(to));
			}
			at = list.isEmpty() ? null : list.get((int) Math.floorMod(p.benih() >>> 11, (long) list.size())).pos();
		}
		return at == null ? -1 : peta.zonaDi(at);
	}

	// ================================================================== small helpers

	/** The same use written two ways by different generators (the 38 rule, {@code TempatHuni.kembar}). */
	static String kembar(String type) {
		return switch (type) {
			case "meja_tulis" -> "kerja:meja_tulis";
			case "kerja:meja_tulis" -> "meja_tulis";
			case "dupa" -> "kerja:dupa";
			case "kerja:dupa" -> "dupa";
			case "lapak" -> "kerja:kios";
			case "kerja:kios" -> "lapak";
			case "kursi_lantai" -> "kursi";
			case "sumur" -> "sumur_timba";
			case "sumur_timba" -> "sumur";
			default -> type;
		};
	}

	/** Titik built for another role (data {@code peran}) left out, unless that leaves none. */
	private static List<Titik> untukPeran(List<Titik> list, NpcRole peran) {
		List<Titik> out = new ArrayList<>(list.size());
		for (Titik t : list) {
			String tag = t.data().get("peran");
			if (tag == null || tag.isEmpty() || tag.equalsIgnoreCase(peran.name()) || tag.equalsIgnoreCase(peran.id())) {
				out.add(t);
			}
		}
		return out.isEmpty() ? list : out;
	}

	private static List<Titik> gabung(List<Titik> a, List<Titik> b) {
		if (b.isEmpty()) {
			return a;
		}
		if (a.isEmpty()) {
			return b;
		}
		List<Titik> out = new ArrayList<>(a.size() + b.size());
		out.addAll(a);
		out.addAll(b);
		return out;
	}

	/** A uniform number in [0, 1) from a seed (splitmix64). */
	static double acak(long seed) {
		long z = seed + 0x9E3779B97F4A7C15L;
		z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
		z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
		z ^= z >>> 31;
		return (z >>> 11) * 0x1.0p-53;
	}

	private Penyeimbang() {
	}
}
