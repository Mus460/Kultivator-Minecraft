package dev.kultivator.dunia.kota42.ragam;

import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.api.JenisHubungan;
import dev.kultivator.dunia.kota42.cakap.Kepribadian;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
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
import java.util.SplittableRandom;

/**
 * Spontaneous turns on the way (DESIGN 42.4A.6): when a resident walks from one planned goal to the next, on
 * {@code kotaWarga.belokanPeluang} (12 %) of the trips it stops at an attraction within 24 blocks of the route: a snack at a
 * stall (the stick of candied haws carried while eating), a town cat, a street show (joining the crowd while it has room), a
 * friend, the notice board, a coin for a beggar (the generous), a peek into a shop (the vain), a bench, the children's
 * splashing at the well. At most {@code kotaWarga.belokanMaks} (3) per resident per day.
 * <p>
 * The turns are drawn into the plan when it is made ({@link #sisip}: a few minutes cut from the start of the next step); the
 * route is checked when the turn comes due ({@link #terapkan}): the nearest attraction of its kind to the segment between
 * the two goals, else the next step starts early. Its goal is that exact spot ({@code @belok:x,y,z,h}), placed by
 * {@link #pilih} (registered as a {@link TempatHuni.Penyeimbang} that answers only this prefix).
 */
public final class Belokan {

	/** Goal prefix of a turn: {@code @belok:<x>,<y>,<z>,<facing 2D>}. */
	public static final String TUJUAN = "@belok:";
	/** Largest distance of the attraction from the route (blocks). */
	public static final double JARAK_RUTE = 24.0D;
	/** People one show's crowd takes in from turns. */
	private static final int KERUMUNAN_MAKS = HidupApi.TONTON_MAKS;

	/** Turns taken per place per day {@code situs -> (hari -> n)}; the last 3 days. */
	private static final Map<Long, Map<Long, Integer>> JUMLAH = new HashMap<>();
	/** Turns already counted ({@code situs, slot, day, start}). */
	private static final Set<String> DIHITUNG = new HashSet<>();
	/** Crowd members per show spot: pos -> (slot -> world day + minute it ends). */
	private static final Map<Long, Map<Integer, Long>> KERUMUNAN = new HashMap<>();

	// ================================================================== planning

	/** Draws the turns of a fresh plan (between contiguous steps with different goals; never for a stall keeper at work). */
	static void sisip(TempatHuni huni, Penduduk p, @Nullable Warga w, List<RencanaHarian.Langkah> langkah, SplittableRandom acak, long hari) {
		List<KumpulanKegiatan.BelokanDef> defs = KumpulanKegiatan.data().belokan();
		double peluang = KotaConfig.warga().belokanPeluang;
		int maks = KotaConfig.warga().belokanMaks;
		if (defs.isEmpty() || peluang <= 0.0D || maks <= 0) {
			return;
		}
		boolean anak = p.peran().kelompok == NpcRole.Kelompok.ANAK;
		boolean sahabat = punyaSahabat(w);
		int musim = IramaMingguan.musim(hari);
		boolean dimuat = huni.jumlahTitik() >= 24;
		int n = 0;
		for (int i = 1; i < langkah.size() && n < maks; i++) {
			RencanaHarian.Langkah prev = langkah.get(i - 1);
			RencanaHarian.Langkah next = langkah.get(i);
			if (prev.akhir() != next.mulai() || prev.titik().equals(next.titik()) || next.lama() < 20) {
				continue;
			}
			if (p.peran().stationary && next.blok().equals(KumpulanKegiatan.Blok.KERJA.id())) {
				continue;
			}
			if (acak.nextDouble() >= peluang) {
				continue;
			}
			double total = 0.0D;
			double[] bobot = new double[defs.size()];
			for (int k = 0; k < defs.size(); k++) {
				KumpulanKegiatan.BelokanDef d = defs.get(k);
				boolean boleh = switch (d.untuk()) {
					case "anak" -> anak;
					case "dewasa" -> !anak;
					default -> true;
				};
				if (!boleh || d.musim() != 0 && (d.musim() >> musim & 1) == 0 || d.sahabat() && !sahabat
						|| dimuat && huni.titik(d.titik()).isEmpty()) {
					continue;
				}
				double b = d.bobot();
				if (w != null) {
					for (String h : d.kebiasaan()) {
						if (Kepribadian.punya(w, h)) {
							b *= 3.0D;
						}
					}
				}
				bobot[k] = b;
				total += b;
			}
			if (total <= 0.0D) {
				continue;
			}
			double r = acak.nextDouble() * total;
			KumpulanKegiatan.BelokanDef pilih = defs.getLast();
			for (int k = 0; k < defs.size(); k++) {
				r -= bobot[k];
				if (r < 0.0D) {
					pilih = defs.get(k);
					break;
				}
			}
			int d = Math.min(next.lama() - 10, pilih.durMin() + acak.nextInt(pilih.durMax() - pilih.durMin() + 1));
			RencanaHarian.Langkah turn = new RencanaHarian.Langkah(pilih.kunci(), next.mulai(), next.mulai() + d, pilih.titik(), next.blok(),
					"belokan", ("belokan/" + pilih.id()).hashCode(), pilih.kegiatan(pilih.titik()), null, pilih);
			langkah.set(i, next.rentang(next.mulai() + d, next.akhir()));
			langkah.add(i, turn);
			i++;
			n++;
		}
	}

	private static boolean punyaSahabat(@Nullable Warga w) {
		if (w == null) {
			return false;
		}
		for (long bond : w.hubungan) {
			if (JenisHubungan.jenis(bond) == JenisHubungan.SAHABAT) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== at run time

	/**
	 * The turn at step {@code i} of plan {@code r} as played now: its activity aimed at the attraction nearest the route,
	 * or null when none lies within {@link #JARAK_RUTE} (or the show's crowd is full).
	 */
	static @Nullable Kegiatan terapkan(TempatHuni huni, Penduduk p, RencanaHarian.Rencana r, int i, long hari) {
		RencanaHarian.Langkah l = r.langkah().get(i);
		KumpulanKegiatan.BelokanDef def = l.belok();
		if (def == null) {
			return null;
		}
		BlockPos a = perkiraan(huni, p, i > 0 ? r.langkah().get(i - 1) : null);
		BlockPos b = perkiraan(huni, p, i + 1 < r.langkah().size() ? r.langkah().get(i + 1) : null);
		Titik best = null;
		double bestD = JARAK_RUTE * JARAK_RUTE;
		List<Titik> list = new ArrayList<>(huni.titik(def.titik()));
		if (def.depan()) {
			list.addAll(huni.titik("kerja:kios"));
		}
		for (Titik t : list) {
			double d = jarakSegmen2(t.pos(), a, b);
			if (d <= bestD) {
				bestD = d;
				best = t;
			}
		}
		if (best == null) {
			return null;
		}
		long habis = hari * 1440L + l.akhir();
		if (def.kerumunan()) {
			Map<Integer, Long> crowd = KERUMUNAN.computeIfAbsent(best.pos().asLong(), k -> new HashMap<>());
			long kini = hari * 1440L + l.mulai();
			crowd.values().removeIf(t -> t < kini);
			if (!crowd.containsKey(p.id()) && crowd.size() >= KERUMUNAN_MAKS) {
				return null;
			}
			crowd.put(p.id(), habis);
		}
		if (DIHITUNG.add(huni.situsId + ":" + p.id() + ":" + hari + ":" + l.mulai())) {
			Map<Long, Integer> per = JUMLAH.computeIfAbsent(huni.situsId, s -> new HashMap<>());
			per.merge(hari, 1, Integer::sum);
			per.keySet().removeIf(h -> h < hari - 3L);
			MetrikRagam.belokan(huni.situsId, p.id());
			if (DIHITUNG.size() > 20000) {
				DIHITUNG.clear();
			}
		}
		BlockPos at = best.pos();
		return def.kegiatan(TUJUAN + at.getX() + "," + at.getY() + "," + at.getZ() + "," + best.hadap().get2DDataValue() + (def.depan() ? ",d" : ""));
	}

	/** Turns taken in place {@code situs} on day {@code hari} (metrics: 0.5-2 per resident). */
	public static int jumlah(long situs, long hari) {
		Map<Long, Integer> per = JUMLAH.get(situs);
		return per == null ? 0 : per.getOrDefault(hari, 0);
	}

	/**
	 * Where a step's goal roughly is, for the route test. shortcut: the titik of that kind nearest the resident's home (the
	 * built-in pick draws among the four nearest), the place's centre for streets, stall fronts and taverns; good enough for
	 * a 24-block corridor, refine with the reserved titik once 42S exposes its reservations per resident.
	 */
	static BlockPos perkiraan(TempatHuni huni, Penduduk p, RencanaHarian.@Nullable Langkah l) {
		BlockPos home = huni.rumahPos(p);
		if (l == null) {
			return home;
		}
		String to = l.titik();
		if (to.equals(Kegiatan.RUMAH) || to.equals(Kegiatan.KASUR) || to.equals(Kegiatan.TETAP)) {
			return home;
		}
		if (to.equals(Kegiatan.KERJA)) {
			Titik own = huni.kerja(p);
			return own != null ? own.pos() : home;
		}
		if (to.startsWith("@")) {
			return huni.pusat();
		}
		Titik best = null;
		double bestD = Double.MAX_VALUE;
		for (Titik t : huni.titik(to)) {
			double d = t.pos().distSqr(home);
			if (d < bestD) {
				bestD = d;
				best = t;
			}
		}
		return best != null ? best.pos() : home;
	}

	/** Squared horizontal distance of {@code q} from the segment {@code a-b}. */
	static double jarakSegmen2(BlockPos q, BlockPos a, BlockPos b) {
		double ax = a.getX();
		double az = a.getZ();
		double dx = b.getX() - ax;
		double dz = b.getZ() - az;
		double len = dx * dx + dz * dz;
		double t = len <= 0.0D ? 0.0D : Math.max(0.0D, Math.min(1.0D, ((q.getX() - ax) * dx + (q.getZ() - az) * dz) / len));
		double px = ax + t * dx - q.getX();
		double pz = az + t * dz - q.getZ();
		return px * px + pz * pz;
	}

	// ================================================================== the goal

	/** Seats a turn uses itself when free. */
	private static final Set<String> DUDUK = Set.of("kursi", "penonton", "pandang");

	/** {@link TempatHuni.Penyeimbang} for {@code @belok:} goals (null for any other goal). */
	public static TempatHuni.@Nullable Tujuan pilih(TempatHuni huni, ServerLevel level, Penduduk p, Kegiatan k, int jam, boolean kunciUntuk) {
		if (!k.tujuan().startsWith(TUJUAN)) {
			return null;
		}
		String[] part = k.tujuan().substring(TUJUAN.length()).split(",");
		if (part.length < 4) {
			return null;
		}
		BlockPos pos;
		Direction face;
		try {
			pos = new BlockPos(Integer.parseInt(part[0]), Integer.parseInt(part[1]), Integer.parseInt(part[2]));
			face = Direction.from2DDataValue(Integer.parseInt(part[3]));
		} catch (NumberFormatException e) {
			return null;
		}
		boolean depan = part.length > 4 && part[4].equals("d");
		if (depan) {
			// in front of the stall, facing the vendor, shoulder to shoulder with other shoppers
			Direction side = face.getClockWise();
			int shift = Math.floorMod(p.id(), 3) - 1;
			for (int out : new int[]{2, 3, 1}) {
				BlockPos at = berdiri(level, pos.relative(face, out).relative(side, shift));
				if (at != null) {
					return new TempatHuni.Tujuan(at, face.getOpposite(), "lapak", false);
				}
			}
			return null;
		}
		KumpulanKegiatan.BelokanDef def = null;
		for (KumpulanKegiatan.BelokanDef d : KumpulanKegiatan.data().belokan()) {
			if (d.kunci().equals(k.kunci())) {
				def = d;
				break;
			}
		}
		String jenis = def != null ? def.titik() : "belok";
		if (DUDUK.contains(jenis) && (!kunciUntuk || !huni.terkunci(pos, p.id())) && !huni.dipesan(pos, p.id(), level.getGameTime())) {
			return new TempatHuni.Tujuan(pos, face, jenis, true);
		}
		// beside it, facing it (a cat, a beggar, the well, the notice board, a full bench)
		return sekitar(level, huni, p, pos, jenis);
	}

	/** A free standable block one or two blocks round {@code pos}, facing it; null when none. */
	static TempatHuni.@Nullable Tujuan sekitar(ServerLevel level, TempatHuni huni, Penduduk p, BlockPos pos, String jenis) {
		int[][] ring = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
		for (int r = 1; r <= 2; r++) {
			for (int k = 0; k < ring.length; k++) {
				int[] o = ring[(k + p.id()) % ring.length];
				BlockPos at = berdiri(level, pos.offset(o[0] * r, 0, o[1] * r));
				if (at != null && !huni.terkunci(at, p.id())) {
					Direction face = Direction.fromYRot(Math.toDegrees(Math.atan2(-(pos.getX() - at.getX()), pos.getZ() - at.getZ())));
					return new TempatHuni.Tujuan(at, face, jenis, false);
				}
			}
		}
		return null;
	}

	/**
	 * A block a person can stand in at {@code at} or one block up or down (sturdy floor, two free blocks, no fluid), or null;
	 * the same test as the 38 goals (TempatHuni keeps its own package-private).
	 */
	static @Nullable BlockPos berdiri(ServerLevel level, BlockPos at) {
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

	private Belokan() {
	}
}
