package dev.kultivator.dunia.tempat;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.jalan.RencanaJalan;
import dev.kultivator.dunia.tempat.jalan.SimpulJalan;
import dev.kultivator.dunia.tempat.worldgen.TataLetakStructure;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sekte.langit.npc.LangitNpcPlan;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The capital plan {@code kota_besar} (DESIGN 38.B.2.2; owner 38W). Eleven district starts per candidate on a
 * 144-block grid before rotation (north = -z, main gate south):
 * <pre>
 *             x = -1           x = 0          x = +1          outside
 *  z = -1   [cendekia]       [istana]       [awan]
 *  z =  0   [gilda]          [pusat]        [pasar]         [kumuh] (x + 296, outside the east gate)
 *  z = +1   [hunian_barat]   [gerbang]      [arena]
 *  outside                   [luar]  (z + 296, caravan field outside the Great South Gate)
 * </pre>
 * <b>Candidates</b> ({@link #hitung}), in evaluation order:
 * <ol>
 *   <li>the first ring: 12 candidates at angle {@code turn + i x 30 deg}, radius {@code 1800 + hash x 1400}
 *       ({@code kotaBesar.jarakPertamaMin} / {@code jarakPertamaLebar}); the first one whose terrain verdict is
 *       suitable becomes the first capital, the others of the ring fall;</li>
 *   <li>far cells: one candidate per 4096-block cell (cell (0,0) is centred on the world origin and has none), at a
 *       hashed point in the middle 60 % of the cell, alive with chance {@code kotaBesar.peluangSel} (0.6), taken in
 *       spiral order from the centre out to {@link #JANGKAUAN_SEL} cells (about 26 600 blocks); a far candidate falls
 *       when an earlier capital that is really born lies within {@code kotaBesar.jarakAntar} (2500) blocks.</li>
 * </ol>
 * Every candidate keeps 700 blocks from each NPC sect gate ({@link GerbangPlan}, the one sect list of T12) and 400 from
 * each sky sect palace ({@link LangitNpcPlan}); a candidate that is too close moves 300 blocks straight away from the
 * nearest offender, at most three times, and is dropped when it still collides. The rotation is {@code hash & 3}.
 * <p>
 * <b>Verdicts.</b> {@link #putusan} answers the shared terrain verdict ({@link PutusanTanah}) combined with the ring and
 * spacing rules above; results are cached per seed (one entry per candidate asked), so the eleven district starts, the
 * keep-out mixin, the runtime zones, the road plan and the server simulation agree. New worlds only (DESIGN 38.D.3 is
 * dropped): there is no old-region guard.
 */
public final class RencanaKotaBesar implements Rencana {

	public static final String ID = "kota_besar";
	/** District cell size (blocks). */
	public static final int SEL = 144;
	/** Distance of the two outside districts from the centre (cell 144 + wall 8 + half a cell). */
	public static final int LUAR = 296;
	/** Half extent of one district's pieces (cell 144 + wall 8 + tower 8 = within +-80 of the start chunk). */
	public static final int SETENGAH_DISTRIK = 80;
	/** Keep-out margin around the districts: 3 chunks (38.D.4). */
	public static final int MARGIN = 48;
	/** Size of a far cell (blocks). */
	public static final int SEL_JAUH = 4096;
	/** Far cells are planned out to this many cells from the centre in each axis (13 x 13 cells, 168 far cells). */
	public static final int JANGKAUAN_SEL = 6;
	/** First-ring candidates. */
	public static final int CINCIN = 12;
	/** Least distance to an NPC sect gate and to a sky palace (blocks). */
	public static final int JAUH_GERBANG = 700;
	public static final int JAUH_LANGIT = 400;
	/** One shift step away from an offender (blocks) and the most shifts. */
	public static final int GESER = 300;
	public static final int GESER_MAKS = 3;
	/** Where the roads of the three gates end, before rotation (12 blocks outside the place box, 38.C.6.1). */
	private static final int[][] GERBANG_JALAN = {{0, LUAR + SETENGAH_DISTRIK + 12}, {LUAR + SETENGAH_DISTRIK + 12, 0},
			{-(SEL + SETENGAH_DISTRIK + 12), 0}};

	/** One district of the grid: id and unrotated offset from the city centre. */
	public record Petak(String id, int dx, int dz) {
	}

	/** The eleven districts, in start order. */
	public static final List<Petak> PETAK = List.of(
			new Petak("cendekia", -SEL, -SEL), new Petak("istana", 0, -SEL), new Petak("awan", SEL, -SEL),
			new Petak("gilda", -SEL, 0), new Petak("pusat", 0, 0), new Petak("pasar", SEL, 0),
			new Petak("hunian_barat", -SEL, SEL), new Petak("gerbang", 0, SEL), new Petak("arena", SEL, SEL),
			new Petak("kumuh", LUAR, 0), new Petak("luar", 0, LUAR));

	/**
	 * The plan of one seed: the candidates (evaluation order), their districts, the start-chunk index and, per
	 * candidate index, the earlier candidates within the spacing distance.
	 */
	public record Plan(long seed, List<Kandidat> kandidat, List<Distrik> distrik, Map<Long, Distrik> mulai,
			Map<Integer, Kandidat> perIndeks, Map<Integer, int[]> tetanggaAwal) {

		public @Nullable Kandidat kandidat(int index) {
			return perIndeks.get(index);
		}
	}

	public static final RencanaKotaBesar INSTANCE = new RencanaKotaBesar();

	private static volatile Plan cached;
	private static volatile PutusanTanah.Aturan aturan = PutusanTanah.Aturan.kotaBesar(20);
	private static volatile int jarakMin = 1800;
	private static volatile int jarakLebar = 1400;
	private static volatile double peluangSel = 0.6D;
	private static volatile int jarakAntar = 2500;
	private static volatile boolean aktif = true;
	private static volatile long akhirSeed = Long.MIN_VALUE;
	private static final Map<Integer, Putusan> AKHIR = new ConcurrentHashMap<>();

	/** The plan of {@code seed} (cached per seed, like {@code GerbangPlan}). */
	public static Plan of(long seed) {
		Plan p = cached;
		if (p != null && p.seed() == seed) {
			return p;
		}
		synchronized (RencanaKotaBesar.class) {
			p = cached;
			if (p != null && p.seed() == seed) {
				return p;
			}
			p = build(seed);
			cached = p;
			return p;
		}
	}

	private static Plan build(long seed) {
		List<Kandidat> list = aktif ? hitung(seed) : List.of();
		List<Distrik> districts = new ArrayList<>(list.size() * PETAK.size());
		Map<Long, Distrik> starts = new HashMap<>();
		Map<Integer, Kandidat> byIndex = new HashMap<>();
		for (Kandidat k : list) {
			byIndex.put(k.index(), k);
			for (Distrik d : distrikOf(k)) {
				districts.add(d);
				starts.putIfAbsent(chunkKey(d.chunkX(), d.chunkZ()), d);
			}
		}
		Map<Integer, int[]> earlier = new HashMap<>();
		long limit = (long) jarakAntar * jarakAntar;
		for (int i = 0; i < list.size(); i++) {
			Kandidat k = list.get(i);
			List<Integer> near = new ArrayList<>();
			for (int j = 0; j < i; j++) {
				Kandidat o = list.get(j);
				long dx = o.pusatX() - k.pusatX();
				long dz = o.pusatZ() - k.pusatZ();
				if (dx * dx + dz * dz < limit) {
					near.add(o.index());
				}
			}
			earlier.put(k.index(), near.stream().mapToInt(Integer::intValue).toArray());
		}
		return new Plan(seed, List.copyOf(list), List.copyOf(districts), Map.copyOf(starts), Map.copyOf(byIndex), Map.copyOf(earlier));
	}

	/** Sets the terrain rules ({@code kotaBesar.selisihMaks}, 38KS config) and drops cached verdicts. */
	public static void setAturan(PutusanTanah.Aturan rules) {
		aturan = rules;
		lupakan();
	}

	/**
	 * Sets the candidate rules of the {@code kotaBesar} config section (38KS calls it on load): first ring
	 * {@code jarakPertamaMin} / {@code jarakPertamaLebar}, far-cell chance {@code peluangSel}, spacing
	 * {@code jarakAntar}, and {@code worldgen} (false: no candidate, so no capital start, zone or road node).
	 */
	public static void setJarak(int pertamaMin, int pertamaLebar, double peluang, int antar, boolean worldgen) {
		jarakMin = Math.max(600, pertamaMin);
		jarakLebar = Math.max(0, pertamaLebar);
		peluangSel = Math.clamp(peluang, 0.0D, 1.0D);
		jarakAntar = Math.max(1000, antar);
		aktif = worldgen;
		lupakan();
	}

	/** Forgets the plan and every cached verdict (seed or config change). */
	public static void lupakan() {
		synchronized (RencanaKotaBesar.class) {
			cached = null;
			AKHIR.clear();
			akhirSeed = Long.MIN_VALUE;
		}
		PutusanTanah.lupakan();
		RencanaJalan.lupakan();
		TempatZones.lupakanRencana();
	}

	/** Candidates of {@code seed} in evaluation order: first ring (indices 0-11), then far cells in spiral order. */
	static List<Kandidat> hitung(long seed) {
		List<int[]> offenders = offenders(seed);
		List<Kandidat> out = new ArrayList<>();
		double turn = unit(seed, 0x51A7L, 0, 0) * Math.PI * 2.0D;
		for (int i = 0; i < CINCIN; i++) {
			double angle = turn + Math.toRadians(30.0D * i);
			double r = jarakMin + unit(seed, 0xC1C1L, i, 0) * jarakLebar;
			int[] pos = geser(offenders, (int) Math.round(Math.cos(angle) * r), (int) Math.round(Math.sin(angle) * r), seed, i);
			if (pos != null) {
				out.add(new Kandidat(ID, i, pos[0], pos[1], (int) (hash(seed, 0x9071L, i, 0) & 3), true));
			}
		}
		int index = CINCIN;
		for (int[] cell : spiral(JANGKAUAN_SEL)) {
			int cx = cell[0];
			int cz = cell[1];
			if (unit(seed, 0xCE11L, cx, cz) >= peluangSel) {
				continue;
			}
			int x = cx * SEL_JAUH + (int) Math.round((unit(seed, 0xA0A0L, cx, cz) - 0.5D) * 0.6D * SEL_JAUH);
			int z = cz * SEL_JAUH + (int) Math.round((unit(seed, 0xB0B0L, cx, cz) - 0.5D) * 0.6D * SEL_JAUH);
			int[] pos = geser(offenders, x, z, seed, index);
			if (pos != null) {
				out.add(new Kandidat(ID, index, pos[0], pos[1], (int) (hash(seed, 0x9072L, cx, cz) & 3), false));
			}
			index++;
		}
		return out;
	}

	/** Gate columns (limit 700) and sky palace columns (limit 400) as {x, z, limit}. */
	private static List<int[]> offenders(long seed) {
		List<int[]> out = new ArrayList<>();
		try {
			for (GerbangPlan.Entry e : GerbangPlan.of(seed).entries()) {
				out.add(new int[]{e.blockX(), e.blockZ(), JAUH_GERBANG});
			}
			for (LangitNpcPlan.Istana istana : LangitNpcPlan.of(seed)) {
				out.add(new int[]{istana.column().getX(), istana.column().getZ(), JAUH_LANGIT});
			}
		} catch (RuntimeException e) {
			Kultivator.LOGGER.warn("Kota Besar plan could not read the sect gates", e);
		}
		return out;
	}

	/** Moves {@code (x, z)} away from offenders (at most three 300-block steps); null when it still collides. */
	private static int @Nullable [] geser(List<int[]> offenders, int x, int z, long seed, int salt) {
		for (int step = 0; step <= GESER_MAKS; step++) {
			int[] worst = null;
			double worstD = Double.MAX_VALUE;
			for (int[] o : offenders) {
				double over = Math.hypot(x - o[0], z - o[1]) - o[2];
				if (over < 0.0D && over < worstD) {
					worstD = over;
					worst = o;
				}
			}
			if (worst == null) {
				return new int[]{x, z};
			}
			if (step == GESER_MAKS) {
				return null;
			}
			double dx = x - worst[0];
			double dz = z - worst[1];
			double len = Math.hypot(dx, dz);
			if (len < 1.0D) {
				double a = unit(seed, 0x6E5EL, salt, step) * Math.PI * 2.0D;
				dx = Math.cos(a);
				dz = Math.sin(a);
				len = 1.0D;
			}
			x += (int) Math.round(dx / len * GESER);
			z += (int) Math.round(dz / len * GESER);
		}
		return null;
	}

	/** Cells (excluding (0,0)) in square-ring spiral order out to {@code range}: ring by ring, clockwise from north-west. */
	public static List<int[]> spiral(int range) {
		List<int[]> out = new ArrayList<>();
		for (int ring = 1; ring <= range; ring++) {
			for (int x = -ring; x <= ring; x++) {
				out.add(new int[]{x, -ring});
			}
			for (int z = -ring + 1; z <= ring; z++) {
				out.add(new int[]{ring, z});
			}
			for (int x = ring - 1; x >= -ring; x--) {
				out.add(new int[]{x, ring});
			}
			for (int z = ring - 1; z > -ring; z--) {
				out.add(new int[]{-ring, z});
			}
		}
		return out;
	}

	/** The eleven districts of {@code k}, centres rotated by the place rotation. */
	public static List<Distrik> distrikOf(Kandidat k) {
		List<Distrik> out = new ArrayList<>(PETAK.size());
		Rotation r = Rotation.values()[k.putar()];
		for (Petak p : PETAK) {
			int[] o = TataLetakStructure.rotate(r, p.dx(), p.dz());
			out.add(new Distrik(k, p.id(), k.pusatX() + o[0], k.pusatZ() + o[1]));
		}
		return out;
	}

	/** The road ends of the three gates of {@code k} (south, east, west; rotated), 12 blocks outside the place. */
	public static List<SimpulJalan> gerbangJalan(Kandidat k) {
		List<SimpulJalan> out = new ArrayList<>(GERBANG_JALAN.length);
		Rotation r = Rotation.values()[k.putar()];
		for (int[] g : GERBANG_JALAN) {
			int[] o = TataLetakStructure.rotate(r, g[0], g[1]);
			out.add(new SimpulJalan(k.pusatX() + o[0], k.pusatZ() + o[1], ID, SimpulJalan.KEKAISARAN, k.index()));
		}
		return out;
	}

	/** The whole-capital box (districts + walls, no margin) of {@code k}. */
	public static BoundingBox kotakKota(Kandidat k) {
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (Distrik d : distrikOf(k)) {
			minX = Math.min(minX, d.pusatX() - SETENGAH_DISTRIK);
			minZ = Math.min(minZ, d.pusatZ() - SETENGAH_DISTRIK);
			maxX = Math.max(maxX, d.pusatX() + SETENGAH_DISTRIK);
			maxZ = Math.max(maxZ, d.pusatZ() + SETENGAH_DISTRIK);
		}
		return new BoundingBox(minX, -64, minZ, maxX, 320, maxZ);
	}

	/**
	 * The final verdict of candidate {@code k}: the terrain verdict, then the ring rule (an earlier suitable ring
	 * candidate wins) or the spacing rule (an earlier born capital within {@code jarakAntar}). Cached per seed.
	 */
	public static Putusan akhir(PutusanTanah.Sumber sumber, Kandidat k) {
		if (akhirSeed != sumber.seed()) {
			synchronized (RencanaKotaBesar.class) {
				if (akhirSeed != sumber.seed()) {
					AKHIR.clear();
					akhirSeed = sumber.seed();
				}
			}
		}
		Putusan known = AKHIR.get(k.index());
		if (known != null) {
			return known;
		}
		Putusan result = hitungAkhir(sumber, k);
		AKHIR.put(k.index(), result);
		return result;
	}

	/**
	 * The loose rules: three times the height spread, five more water samples and 40 more blocks above the sea. Rugged
	 * terrain packs (Tectonic, Terralith) failed every first-ring candidate under the strict rules, so a world had no
	 * capital at all; the districts use {@code beard_box}, which levels the ground under them.
	 */
	private static PutusanTanah.Aturan longgar() {
		PutusanTanah.Aturan a = aturan;
		return new PutusanTanah.Aturan(a.langkah(), a.sisi(), a.airMaks() + 5, a.selisihMaks() * 3, a.atasLautMin(), a.atasLautMaks() + 40,
				a.bioma(), a.selDistrik());
	}

	/** The strict verdict, else the loose one (its reason says "longgar"). */
	private static Putusan tanah(PutusanTanah.Sumber sumber, Kandidat k) {
		Putusan strict = PutusanTanah.of(sumber, k, aturan);
		if (strict.layak()) {
			return strict;
		}
		Putusan loose = PutusanTanah.of(sumber, k, longgar());
		return loose.layak() ? new Putusan(true, loose.dasarY(), "longgar") : strict;
	}

	private static Putusan hitungAkhir(PutusanTanah.Sumber sumber, Kandidat k) {
		Plan plan = of(sumber.seed());
		if (k.pertama()) {
			// The first ring: the first strictly suitable candidate wins; when none is, the first loosely suitable one.
			Putusan strict = PutusanTanah.of(sumber, k, aturan);
			boolean anyStrict = strict.layak();
			for (Kandidat o : plan.kandidat()) {
				if (anyStrict || !o.pertama()) {
					break;
				}
				anyStrict = PutusanTanah.of(sumber, o, aturan).layak();
			}
			Putusan raw = anyStrict ? strict : tanah(sumber, k);
			if (!raw.layak()) {
				return raw;
			}
			for (Kandidat o : plan.kandidat()) {
				if (!o.pertama() || o.index() >= k.index()) {
					break;
				}
				if ((anyStrict ? PutusanTanah.of(sumber, o, aturan) : tanah(sumber, o)).layak()) {
					return Putusan.tidak("cincin #" + o.index());
				}
			}
			return raw;
		}
		Putusan raw = tanah(sumber, k);
		if (!raw.layak()) {
			return raw;
		}
		int[] near = plan.tetanggaAwal().getOrDefault(k.index(), new int[0]);
		for (int index : near) {
			Kandidat o = plan.kandidat(index);
			if (o != null && akhir(sumber, o).layak()) {
				return Putusan.tidak("dekat #" + index);
			}
		}
		return raw;
	}

	@Override
	public boolean isStart(long seed, int cx, int cz, String distrik) {
		Distrik d = distrikDi(seed, cx, cz);
		return d != null && d.id().equals(distrik);
	}

	@Override
	public @Nullable Distrik distrikDi(long seed, int cx, int cz) {
		return of(seed).mulai().get(chunkKey(cx, cz));
	}

	@Override
	public List<BoundingBox> zona(long seed) {
		List<BoundingBox> out = new ArrayList<>();
		for (ZonaRencana z : zonaDistrik(seed)) {
			out.add(z.kotak());
		}
		return out;
	}

	@Override
	public List<BoundingBox> zonaAman(long seed) {
		List<BoundingBox> out = new ArrayList<>();
		for (ZonaRencana z : zonaDistrik(seed)) {
			out.add(z.aman());
		}
		return out;
	}

	@Override
	public List<ZonaRencana> zonaDistrik(long seed) {
		List<ZonaRencana> out = new ArrayList<>();
		for (Distrik d : of(seed).distrik()) {
			int h = SETENGAH_DISTRIK + MARGIN;
			int a = SETENGAH_DISTRIK;
			out.add(new ZonaRencana(new BoundingBox(d.pusatX() - h, -64, d.pusatZ() - h, d.pusatX() + h, 320, d.pusatZ() + h),
					new BoundingBox(d.pusatX() - a, -64, d.pusatZ() - a, d.pusatX() + a, 320, d.pusatZ() + a), d));
		}
		return out;
	}

	@Override
	public List<SimpulJalan> simpul(long seed, int selX, int selZ) {
		List<SimpulJalan> out = new ArrayList<>();
		for (Kandidat k : of(seed).kandidat()) {
			if (Math.floorDiv(k.pusatX(), RencanaJalan.SEL) == selX && Math.floorDiv(k.pusatZ(), RencanaJalan.SEL) == selZ) {
				out.addAll(gerbangJalan(k));
			}
		}
		return out;
	}

	@Override
	public boolean simpulSah(PutusanTanah.Sumber sumber, SimpulJalan simpul) {
		Kandidat k = of(sumber.seed()).kandidat((int) simpul.kunci());
		return k != null && akhir(sumber, k).layak();
	}

	@Override
	public List<Distrik> semuaDistrik(long seed) {
		return of(seed).distrik();
	}

	@Override
	public List<Kandidat> kandidat(long seed) {
		return of(seed).kandidat();
	}

	@Override
	public Putusan putusan(PutusanTanah.Sumber sumber, Distrik distrik) {
		return akhir(sumber, distrik.kandidat());
	}

	private static long chunkKey(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}

	static long hash(long seed, long salt, int a, int b) {
		long h = seed ^ (salt * 0x9E3779B97F4A7C15L);
		h ^= a * 0x632BE59BD9B4E019L;
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h ^= b * 0x85157AF5L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		return h ^ (h >>> 31);
	}

	static double unit(long seed, long salt, int a, int b) {
		return (hash(seed, salt, a, b) >>> 11) * 0x1.0p-53;
	}

	private RencanaKotaBesar() {
	}
}
