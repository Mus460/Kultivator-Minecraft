package dev.kultivator.dunia.tempat.kompleks;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.Distrik;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.Putusan;
import dev.kultivator.dunia.tempat.PutusanTanah;
import dev.kultivator.dunia.tempat.Rencana;
import dev.kultivator.dunia.tempat.RencanaKotaBesar;
import dev.kultivator.dunia.tempat.TempatZones;
import dev.kultivator.dunia.tempat.ZonaRencana;
import dev.kultivator.dunia.tempat.worldgen.TataLetakStructure;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sekte.langit.npc.LangitNpcPlan;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The NPC sect compound plan {@code kompleks_sekte} (DESIGN 38.C.3.1; owner 38DS). One compound per entry of the single
 * NPC sect list ({@link GerbangPlan}, 38.0 rule 15), laid <b>behind</b> that sect's mountain gate as world generation
 * really places it ({@link RotasiGerbang}).
 * <p>
 * <b>Frame.</b> The compound turns with its gate (same {@link Rotation}); in compound-local coordinates the gate lies
 * toward +z (south) and the compound climbs the mountain toward -z. The local origin is the gate centre moved
 * {@code depth/2 + 12} blocks backwards (local -z of the gate template). District centres (local z) and floor offsets:
 * <pre>
 *   band kecil  : halaman_kecil (3 x 3 lots) z -72  dy  0   (main hall, training ground, summit hall all in one court)
 *   band sedang : halaman       (3 x 3)      z -72  dy  0
 *                 puncak_sedang (3 x 2)      z -192 dy +18
 *   band besar  : halaman       (3 x 3)      z -72  dy  0
 *                 dalam         (3 x 3)      z -216 dy +10
 *                 puncak        (3 x 3)      z -360 dy +18
 * </pre>
 * The district holding the summit hall takes the suffix {@code _langit} when the sect owns a sky palace
 * ({@link LangitNpcPlan}): its summit hall becomes {@code aula_puncak_dermaga} with the cloud pier anchor
 * {@code kompleks/dermaga_awan} that 37 may read through {@code TempatApi.spot(sekte, "dermaga_awan")}.
 * District ids are {@code <aliran>_<distrik>} ({@code lurus_halaman}, {@code iblis_puncak_langit}, ...); the three structure
 * sets {@code kompleks_sekte/halaman|dalam|puncak} (salt 380201-380203) carry every structure of that row, and their
 * placement field {@code distrik} matches the part after the alignment by prefix ({@link #isStart}).
 * <p>
 * <b>Verdict</b> ({@link #akhir}): the gate itself must be born (its biome check, {@code #kultivator:has_structure/sekte_gerbang}
 * at the gate box centre); the ground under the compound may differ by at most {@code kompleksSekte.selisihMaks} (30) over
 * a 5 x 5 sample grid with at most 3 water samples and none at the summit lots; a born capital or an earlier born
 * compound must not overlap. The floor is the gate's rear court ({@code dasarY = gate ground + rear floor y}), so the
 * first court continues the gate's terrace. New worlds only: there is no old-region guard (38.D.3 dropped).
 */
public final class RencanaKompleks implements Rencana {

	public static final String ID = "kompleks_sekte";
	public static final String JENIS = "sekte/kompleks";
	/** District cell (3 lots of 48). */
	public static final int SEL = 144;
	public static final int LOT = 48;
	/**
	 * Gap between the gate's rear edge and the first court. 12, not the 8 of the design: the gate start's chunk middle
	 * projects at most {@code depth/2 + 8} behind the gate centre, so with no keep-out margin on the court's front side
	 * the gate start always stays outside the compound's keep-out box ({@code TempatKeepOut}) and is never refused.
	 */
	public static final int JARAK_GERBANG = 12;
	/** Keep-out margin around the compound (38.D.4: compounds + 2 chunks). */
	public static final int MARGIN = 32;
	/** Alignment names (SekteNpc.LURUS/NETRAL/IBLIS = 0/1/2). */
	public static final List<String> ALIRAN = List.of("lurus", "netral", "iblis");
	/** Rows of the compound sets (placement field {@code distrik}). */
	public static final List<String> BARIS = List.of("halaman", "dalam", "puncak");

	/**
	 * One district of a band: id (without alignment), local centre z, floor offset, lot rows (3 or 2), true for the district
	 * that holds the summit hall.
	 */
	public record Petak(String id, int z, int dy, int baris, boolean puncak) {

		/** Half depth (local z) of the district. */
		public int setengahDalam() {
			return baris * LOT / 2;
		}
	}

	private static final List<List<Petak>> PETAK = List.of(
			List.of(new Petak("halaman_kecil", -72, 0, 3, true)),
			List.of(new Petak("halaman", -72, 0, 3, false), new Petak("puncak_sedang", -192, 18, 2, true)),
			List.of(new Petak("halaman", -72, 0, 3, false), new Petak("dalam", -216, 10, 3, false), new Petak("puncak", -360, 18, 3, true)));

	/** The districts of a gate band. */
	public static List<Petak> petak(int band) {
		return PETAK.get(Math.max(0, Math.min(2, band)));
	}

	/** Local depth of a band's compound (court front edge at local z 0 to the summit's back edge). */
	public static int kedalaman(int band) {
		Petak last = petak(band).getLast();
		return -(last.z() - last.setengahDalam());
	}

	/** One planned compound. */
	public record Kompleks(GerbangPlan.Entry entry, RotasiGerbang.Gerbang gerbang, Kandidat kandidat, List<Distrik> distrik,
			boolean langit, int asalX, int asalZ) {

		public String sekte() {
			return entry.id();
		}

		public String aliran() {
			return ALIRAN.get(Math.floorMod(entry.aliran(), ALIRAN.size()));
		}

		public Rotation putar() {
			return gerbang.putar();
		}

		/** World x/z of compound-local point {@code (x, z)}. */
		public int[] dunia(int x, int z) {
			int[] r = TataLetakStructure.rotate(putar(), x, z);
			return new int[]{asalX + r[0], asalZ + r[1]};
		}

		/** The XZ box of the whole compound (no margin), y spanning the world. */
		public BoundingBox kotak() {
			int[] a = dunia(-SEL / 2, 0);
			int[] b = dunia(SEL / 2 - 1, -kedalaman(entry.band()));
			return new BoundingBox(Math.min(a[0], b[0]), -64, Math.min(a[1], b[1]), Math.max(a[0], b[0]), 320, Math.max(a[1], b[1]));
		}

		/** The district whose id (without alignment) starts with {@code nama}, or null. */
		public @Nullable Distrik distrik(String nama) {
			for (Distrik d : distrik) {
				if (bagian(d.id()).startsWith(nama)) {
					return d;
				}
			}
			return null;
		}
	}

	/** The plan of one seed. */
	public record Plan(long seed, List<Kompleks> kompleks, Map<Long, Distrik> mulai, Map<Integer, Kompleks> perIndeks,
			List<Distrik> distrik) {

		public @Nullable Kompleks kompleks(int index) {
			return perIndeks.get(index);
		}

		/** The compound of NPC sect {@code sekteId} ({@code s<i>}), or null. */
		public @Nullable Kompleks kompleks(@Nullable String sekteId) {
			for (Kompleks k : kompleks) {
				if (k.sekte().equals(sekteId)) {
					return k;
				}
			}
			return null;
		}

		/** The compound whose box (plus {@code margin}) holds column {@code (x, z)}, or null. */
		public @Nullable Kompleks di(int x, int z, int margin) {
			for (Kompleks k : kompleks) {
				BoundingBox b = k.kotak();
				if (x >= b.minX() - margin && x <= b.maxX() + margin && z >= b.minZ() - margin && z <= b.maxZ() + margin) {
					return k;
				}
			}
			return null;
		}
	}

	public static final RencanaKompleks INSTANCE = new RencanaKompleks();

	private static volatile Plan cached;
	private static volatile boolean aktif = true;
	private static volatile int selisihMaks = 30;
	private static volatile long akhirSeed = Long.MIN_VALUE;
	private static final Map<Integer, Putusan> AKHIR = new ConcurrentHashMap<>();

	/** Sets the {@code kompleksSekte} switches (Kompleks.init and config reload) and forgets the plan. */
	public static void atur(boolean worldgen, int selisih) {
		aktif = worldgen;
		selisihMaks = Math.max(8, Math.min(48, selisih));
		lupakan();
	}

	/** Forgets the plan and the cached verdicts. */
	public static void lupakan() {
		synchronized (RencanaKompleks.class) {
			cached = null;
			AKHIR.clear();
			akhirSeed = Long.MIN_VALUE;
		}
		TempatZones.lupakanRencana();
	}

	/** The plan of {@code seed} (cached per seed and per sect list). */
	public static Plan of(long seed) {
		Plan p = cached;
		GerbangPlan.Plan gates = GerbangPlan.of(seed);
		if (p != null && p.seed() == seed && p.kompleks().size() == (aktif && GerbangPlan.enabled() ? gates.entries().size() : 0)) {
			return p;
		}
		synchronized (RencanaKompleks.class) {
			p = cached;
			if (p != null && p.seed() == seed && p.kompleks().size() == (aktif && GerbangPlan.enabled() ? gates.entries().size() : 0)) {
				return p;
			}
			p = build(seed, gates);
			cached = p;
			AKHIR.clear();
			akhirSeed = seed;
			return p;
		}
	}

	private static Plan build(long seed, GerbangPlan.Plan gates) {
		List<Kompleks> list = new ArrayList<>();
		Map<Long, Distrik> starts = new HashMap<>();
		Map<Integer, Kompleks> byIndex = new HashMap<>();
		List<Distrik> all = new ArrayList<>();
		if (aktif && GerbangPlan.enabled()) {
			List<Integer> sky = new ArrayList<>();
			try {
				for (LangitNpcPlan.Istana istana : LangitNpcPlan.of(seed)) {
					sky.add(istana.gate().index());
				}
			} catch (RuntimeException e) {
				Kultivator.LOGGER.warn("Compound plan could not read the sky palaces", e);
			}
			for (GerbangPlan.Entry e : gates.entries()) {
				Kompleks k = kompleksOf(seed, e, sky.contains(e.index()));
				list.add(k);
				byIndex.put(e.index(), k);
				for (Distrik d : k.distrik()) {
					all.add(d);
					starts.putIfAbsent(chunkKey(d.chunkX(), d.chunkZ()), d);
				}
			}
		}
		return new Plan(seed, List.copyOf(list), new Long2ObjectOpenHashMap<>(starts), Map.copyOf(byIndex), List.copyOf(all));
	}

	/** The compound of gate entry {@code e} (pure). */
	public static Kompleks kompleksOf(long seed, GerbangPlan.Entry e, boolean langit) {
		RotasiGerbang.Gerbang g = RotasiGerbang.gerbang(seed, e);
		int[] back = TataLetakStructure.rotate(g.putar(), 0, -(RotasiGerbang.dalam(e.band()) / 2 + JARAK_GERBANG));
		int ox = g.pusatX() + back[0];
		int oz = g.pusatZ() + back[1];
		int depth = kedalaman(e.band());
		int[] mid = TataLetakStructure.rotate(g.putar(), 0, -depth / 2);
		Kandidat kandidat = new Kandidat(ID, e.index(), ox + mid[0], oz + mid[1], g.putar().ordinal(), false);
		String aliran = ALIRAN.get(Math.floorMod(e.aliran(), ALIRAN.size()));
		List<Distrik> districts = new ArrayList<>();
		for (Petak p : petak(e.band())) {
			int[] c = TataLetakStructure.rotate(g.putar(), 0, p.z());
			String id = aliran + "_" + p.id() + (p.puncak() && langit ? "_langit" : "");
			districts.add(new Distrik(kandidat, id, ox + c[0], oz + c[1], p.dy()));
		}
		return new Kompleks(e, g, kandidat, List.copyOf(districts), langit, ox, oz);
	}

	/** The district id without its alignment prefix ({@code halaman_kecil_langit} of {@code iblis_halaman_kecil_langit}). */
	public static String bagian(String distrikId) {
		int u = distrikId.indexOf('_');
		return u < 0 ? distrikId : distrikId.substring(u + 1);
	}

	/** The district plan row of district {@code d} ({@link Petak}), or null. */
	public static @Nullable Petak petakOf(Distrik d, int band) {
		String part = bagian(d.id());
		for (Petak p : petak(band)) {
			if (part.equals(p.id()) || part.equals(p.id() + "_langit")) {
				return p;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------------------------------------ verdict

	/** The final verdict of compound {@code k} (cached per seed and compound). */
	public static Putusan akhir(PutusanTanah.Sumber sumber, Kompleks k) {
		if (akhirSeed != sumber.seed()) {
			synchronized (RencanaKompleks.class) {
				if (akhirSeed != sumber.seed()) {
					AKHIR.clear();
					akhirSeed = sumber.seed();
				}
			}
		}
		Putusan known = AKHIR.get(k.entry().index());
		if (known != null) {
			return known;
		}
		Putusan result;
		try {
			result = hitungAkhir(sumber, k);
		} catch (RuntimeException e) {
			Kultivator.LOGGER.warn("Compound verdict failed for sect {}", k.sekte(), e);
			result = Putusan.tidak("galat");
		}
		AKHIR.put(k.entry().index(), result);
		return result;
	}

	private static Putusan hitungAkhir(PutusanTanah.Sumber sumber, Kompleks k) {
		if (!GerbangPlan.enabled()) {
			return Putusan.tidak("gerbang mati");
		}
		RotasiGerbang.Gerbang g = k.gerbang();
		if (!g.biomaSah(sumber)) {
			return Putusan.tidak("bioma gerbang");
		}
		int band = k.entry().band();
		int depth = kedalaman(band);
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		int water = 0;
		for (int i = 0; i < 5; i++) {
			for (int j = 0; j < 5; j++) {
				int lx = -60 + i * 30;
				int lz = -6 - j * (depth - 12) / 4;
				int[] w = k.dunia(lx, lz);
				int surface = sumber.surface(w[0], w[1]);
				int floor = sumber.floor(w[0], w[1]);
				if (surface > floor) {
					water++;
				}
				min = Math.min(min, floor);
				max = Math.max(max, floor);
			}
		}
		if (water > 3) {
			return Putusan.tidak("air " + water + " > 3");
		}
		if (max - min > selisihMaks) {
			return Putusan.tidak("selisih " + (max - min) + " > " + selisihMaks);
		}
		Petak summit = petak(band).getLast();
		for (int lx = -LOT; lx <= LOT; lx += LOT) {
			for (int row = 0; row < summit.baris(); row++) {
				int lz = summit.z() + summit.setengahDalam() - LOT / 2 - row * LOT;
				int[] w = k.dunia(lx, lz);
				if (sumber.surface(w[0], w[1]) > sumber.floor(w[0], w[1])) {
					return Putusan.tidak("air di puncak");
				}
			}
		}
		int dasar = g.tanah(sumber) + RotasiGerbang.lantaiBelakang(band);
		int top = sumber.height().getMaxY() - 64;
		if (dasar + 18 > top) {
			return Putusan.tidak("terlalu tinggi");
		}
		BoundingBox mine = k.kotak().inflatedBy(MARGIN);
		for (Kandidat c : RencanaKotaBesar.of(sumber.seed()).kandidat()) {
			BoundingBox city = RencanaKotaBesar.kotakKota(c);
			if (city.intersects(mine) && RencanaKotaBesar.akhir(sumber, c).layak()) {
				return Putusan.tidak("ibu kota #" + c.index());
			}
		}
		Plan plan = of(sumber.seed());
		for (Kompleks other : plan.kompleks()) {
			if (other.entry().index() >= k.entry().index()) {
				break;
			}
			if (other.kotak().inflatedBy(MARGIN).intersects(mine) && akhir(sumber, other).layak()) {
				return Putusan.tidak("kompleks #" + other.entry().index());
			}
		}
		return Putusan.ya(dasar);
	}

	// ------------------------------------------------------------------------------------------------ Rencana

	@Override
	public boolean isStart(long seed, int cx, int cz, String distrik) {
		Distrik d = distrikDi(seed, cx, cz);
		return d != null && (distrik.isEmpty() || bagian(d.id()).startsWith(distrik));
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
			if (z.aman() != null) {
				out.add(z.aman());
			}
		}
		return out;
	}

	@Override
	public List<ZonaRencana> zonaDistrik(long seed) {
		List<ZonaRencana> out = new ArrayList<>();
		for (Kompleks k : of(seed).kompleks()) {
			List<Petak> rows = petak(k.entry().band());
			for (int i = 0; i < k.distrik().size(); i++) {
				Distrik d = k.distrik().get(i);
				Petak p = rows.get(i);
				BoundingBox box = kotakDistrik(k, p, 0, 0);
				// no margin toward the gate: the gate's own start must never fall in the compound's keep-out box
				BoundingBox keep = kotakDistrik(k, p, MARGIN, i == 0 ? 0 : MARGIN);
				// 38.0 rule 11: the front court of righteous and neutral compounds is safe ground.
				BoundingBox safe = i == 0 && k.entry().aliran() != 2 ? box : null;
				out.add(new ZonaRencana(keep, safe, d));
			}
		}
		return out;
	}

	/**
	 * The XZ box of district row {@code p} of compound {@code k}, grown by {@code margin} on the sides and the back and by
	 * {@code depan} on its front (toward the gate).
	 */
	public static BoundingBox kotakDistrik(Kompleks k, Petak p, int margin, int depan) {
		int[] a = k.dunia(-SEL / 2 - margin, p.z() + p.setengahDalam() - 1 + depan);
		int[] b = k.dunia(SEL / 2 - 1 + margin, p.z() - p.setengahDalam() - margin);
		return new BoundingBox(Math.min(a[0], b[0]), -64, Math.min(a[1], b[1]), Math.max(a[0], b[0]), 320, Math.max(a[1], b[1]));
	}

	@Override
	public List<Distrik> semuaDistrik(long seed) {
		return of(seed).distrik();
	}

	@Override
	public List<Kandidat> kandidat(long seed) {
		List<Kandidat> out = new ArrayList<>();
		for (Kompleks k : of(seed).kompleks()) {
			out.add(k.kandidat());
		}
		return out;
	}

	@Override
	public Putusan putusan(PutusanTanah.Sumber sumber, Distrik distrik) {
		Kompleks k = of(sumber.seed()).kompleks(distrik.kandidat().index());
		return k == null ? Putusan.TIDAK : akhir(sumber, k);
	}

	private static long chunkKey(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}

	private RencanaKompleks() {
	}
}
