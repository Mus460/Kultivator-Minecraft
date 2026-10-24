package dev.kultivator.dunia.tempat.jalan;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.PutusanTanah;
import dev.kultivator.dunia.tempat.Rencana;
import dev.kultivator.dunia.tempat.RencanaRegistry;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The road plan of DESIGN 38.C.6.1 (owner 38W). Pure data from the seed and the world's structure placements:
 * <ul>
 *   <li><b>Places</b> ({@link Tempat}) of a 2048-block road cell and its eight neighbours: every place plan's road nodes
 *       ({@code Rencana.simpul}: the three gates of a capital, themed-city land gates), the NPC sect gates
 *       ({@link GerbangPlan}), and the starts of the inhabited structure sets of {@link #SUMBER} (old towns, villages,
 *       large villages, clan villages, hermit caves, inheritance tombs, ruin caves) found through the set's own
 *       placement ({@code StructurePlacement.isStructureChunk}, so frequency, exclusion zones and ring rules match world
 *       generation exactly);</li>
 *   <li><b>Edges</b> ({@link Sisi}): a minimum spanning tree (Prim, distance between the nearest gates) over the
 *       non-cave places, plus a spoke from each capital to each old town within 1500 blocks; every cave hangs on its
 *       nearest place within 700 blocks by a footpath. Each edge belongs to the cell holding its midpoint (no double
 *       edges); edges longer than {@link #SISI_MAKS} are dropped. Class: imperial from a capital, footpath to a cave,
 *       village road otherwise. The polyline is two levels of midpoint displacement (+-8 % of the length, hashed)
 *       smoothed by two Chaikin passes; it stops 12 blocks outside each end place;</li>
 *   <li><b>Road-side posts</b> ({@link Pos}): a milestone every 500 blocks of travel, a road shrine every 260 (+-60), a
 *       rest post every 900 (+-100) on edges of 1200 or more, a caravanserai or a great rest post next to each junction
 *       place of degree three or more that is not a town, and a bandit camp on 15 % of the edges in the ring beyond
 *       1000 blocks (24-40 blocks off the road).</li>
 * </ul>
 * An edge is born only when both end places are (their terrain verdict or biome check, {@link #sah}), which the road
 * structure asks with the same generator as world generation, so no road ends half way. Cells are cached (LRU 64 per
 * seed). The placement passes its {@link ChunkGeneratorStructureState} ({@link #pakai}); the server uses
 * {@code ServerChunkCache.getGeneratorState()} ({@link #of(ServerLevel)}).
 */
public final class RencanaJalan {

	/** Road cell size in blocks (38.C.6.1). */
	public static final int SEL = 2048;
	/** Longest edge kept (blocks). */
	public static final int SISI_MAKS = 2600;
	/** Capital-to-town spoke length limit (blocks). */
	public static final int JARI_KOTA = 1500;
	/** A cave hangs on a place at most this far (blocks). */
	public static final int JARI_GUA = 700;
	/** Road ends this far outside the end place's box. */
	public static final int JARAK_UJUNG = 12;
	/** Cache size (cells). */
	public static final int CACHE = 64;

	/** Road widths per class (kekaisaran, desa, setapak). */
	public static final int[] LEBAR = {5, 3, 2};

	/**
	 * One road edge: a polyline of block points from {@code dari} to {@code ke} (both included), road class
	 * {@link SimpulJalan#KEKAISARAN}, {@link SimpulJalan#DESA} or {@link SimpulJalan#SETAPAK}.
	 */
	public record Sisi(long id, SimpulJalan dari, SimpulJalan ke, int kelas, int[] xs, int[] zs) {

		/** Polyline length in blocks. */
		public double panjang() {
			double total = 0.0D;
			for (int i = 1; i < xs.length; i++) {
				total += Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
			}
			return total;
		}

		/** The point {@code s} blocks along the polyline (clamped), as {x, z, dirX*1000, dirZ*1000}. */
		public int[] titikPada(double s) {
			double left = Math.max(0.0D, s);
			for (int i = 1; i < xs.length; i++) {
				double len = Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
				if (left <= len || i == xs.length - 1) {
					double t = len < 1.0E-6D ? 0.0D : Math.min(1.0D, left / len);
					double dx = len < 1.0E-6D ? 1.0D : (xs[i] - xs[i - 1]) / len;
					double dz = len < 1.0E-6D ? 0.0D : (zs[i] - zs[i - 1]) / len;
					return new int[]{(int) Math.round(xs[i - 1] + (xs[i] - xs[i - 1]) * t), (int) Math.round(zs[i - 1] + (zs[i] - zs[i - 1]) * t),
							(int) Math.round(dx * 1000.0D), (int) Math.round(dz * 1000.0D)};
				}
				left -= len;
			}
			return new int[]{xs[0], zs[0], 1000, 0};
		}

		/** Squared horizontal distance from {@code (x, z)} to the polyline. */
		public double jarak2(double x, double z) {
			double best = Double.MAX_VALUE;
			for (int i = 1; i < xs.length; i++) {
				best = Math.min(best, segDist2(x, z, xs[i - 1], zs[i - 1], xs[i], zs[i]));
			}
			return best;
		}
	}

	/**
	 * A road-side building: kind ({@code tonggak}, {@code kuil_jalan}, {@code singgah}, {@code singgah_besar},
	 * {@code dagang}, {@code kemah_begal}), footprint centre, rotation ({@code Rotation} ordinal) that turns the
	 * template's south front toward the road, the edge and the distance along it.
	 */
	public record Pos(String jenis, int x, int z, int putar, long sisiId, int jarak) {

		public int chunkX() {
			return x >> 4;
		}

		public int chunkZ() {
			return z >> 4;
		}
	}

	/** Footprint radius of each post kind (blocks), used to keep it off the road. */
	public static final Map<String, Integer> UKURAN_POS = Map.of("tonggak", 3, "kuil_jalan", 5, "singgah", 21, "singgah_besar", 33,
			"dagang", 23, "kemah_begal", 12);
	/** Every post kind, in placement-salt order (380302-380307). */
	public static final List<String> JENIS_POS = List.of("tonggak", "kuil_jalan", "singgah", "singgah_besar", "dagang", "kemah_begal");

	/** One structure-set source of road nodes: set id (exact, or prefix when ending in '*'), node kind, class, radius. */
	public record Sumber(String set, String jenis, int kelas, int jari) {

		boolean cocok(String id) {
			return set.endsWith("*") ? id.startsWith(set.substring(0, set.length() - 1)) : id.equals(set);
		}
	}

	/** The structure sets whose starts are road nodes (38.C.6.1). Sets that do not exist in a world are skipped. */
	public static final List<Sumber> SUMBER = List.of(
			new Sumber("kultivator:kota/kota_kultivator", "kota", SimpulJalan.DESA, 78),
			new Sumber("kultivator:kampung/desa", "kampung", SimpulJalan.DESA, 72),
			new Sumber("kultivator:kampung/desa_besar", "kampung", SimpulJalan.DESA, 100),
			new Sumber("kultivator:kampung/klan", "klan", SimpulJalan.DESA, 80),
			new Sumber("kultivator:gua/gua_pertapa", "gua", SimpulJalan.SETAPAK, 22),
			new Sumber("kultivator:gua/makam_warisan*", "gua", SimpulJalan.SETAPAK, 18),
			new Sumber("kultivator:reruntuhan/gua_kecil", "gua", SimpulJalan.SETAPAK, 20),
			new Sumber("kultivator:reruntuhan/gua_agung", "gua", SimpulJalan.SETAPAK, 28));

	/**
	 * One place a road can end at: its gates (one for most places, three for a capital), its kind, its class, its box
	 * radius from the first gate (0 when the gates already lie outside the place), and how to check it is born.
	 */
	public record Tempat(long kunci, String jenis, int kelas, List<SimpulJalan> gerbang, int jari, @Nullable String rencana,
			@Nullable Holder<StructureSet> set) {

		boolean gua() {
			return "gua".equals(jenis);
		}
	}

	/** The cached plan of one road cell: owned edges, their chunk index, posts and the places of the neighbourhood. */
	public record Sel(int selX, int selZ, List<Sisi> sisi, Map<Long, List<Sisi>> perChunk, List<Pos> pos, Map<Long, Tempat> tempat) {
	}

	private record Kunci(long seed, int selX, int selZ) {
	}

	private static final Map<Kunci, Sel> CACHE_SEL = new LinkedHashMap<>(CACHE, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Kunci, Sel> eldest) {
			return size() > CACHE;
		}
	};
	private static final Map<Long, Boolean> SAH = new ConcurrentHashMap<>();
	private static volatile ChunkGeneratorStructureState state;
	private static volatile long sahSeed = Long.MIN_VALUE;

	/** Remembers the structure state of the world being generated (the road placement calls it every chunk). */
	public static void pakai(ChunkGeneratorStructureState s) {
		if (state != s) {
			if (state != null && state.getLevelSeed() != s.getLevelSeed()) {
				lupakan();
			}
			state = s;
		}
	}

	/** The remembered structure state of world seed {@code seed}, or null (structure stage of world generation). */
	public static @Nullable ChunkGeneratorStructureState state(long seed) {
		ChunkGeneratorStructureState s = state;
		return s != null && s.getLevelSeed() == seed ? s : null;
	}

	/** The structure state of {@code level}'s generator, remembered for later plan queries. */
	public static ChunkGeneratorStructureState of(ServerLevel level) {
		ChunkGeneratorStructureState s = level.getChunkSource().getGeneratorState();
		pakai(s);
		return s;
	}

	/** True when a road edge crosses chunk {@code (cx, cz)} (placement {@code kultivator:jalan}). */
	public static boolean lewat(ChunkGeneratorStructureState s, int cx, int cz) {
		pakai(s);
		return !sisiDi(s, cx, cz).isEmpty();
	}

	/** Seed-only form: answers from the remembered structure state of that seed (false before any is known). */
	public static boolean lewat(long seed, int cx, int cz) {
		ChunkGeneratorStructureState s = state;
		return s != null && s.getLevelSeed() == seed && lewat(s, cx, cz);
	}

	/** The edges crossing chunk {@code (cx, cz)} (road structure pieces of that chunk). */
	public static List<Sisi> sisiDi(ChunkGeneratorStructureState s, int cx, int cz) {
		int bx = cx * 16 + 8;
		int bz = cz * 16 + 8;
		int selX = Math.floorDiv(bx, SEL);
		int selZ = Math.floorDiv(bz, SEL);
		long key = chunkKey(cx, cz);
		List<Sisi> out = null;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				List<Sisi> here = sel(s, selX + dx, selZ + dz).perChunk().get(key);
				if (here != null) {
					if (out == null) {
						out = new ArrayList<>(here);
					} else {
						out.addAll(here);
					}
				}
			}
		}
		return out == null ? List.of() : out;
	}

	/** Seed-only form of {@link #sisiDi(ChunkGeneratorStructureState, int, int)}. */
	public static List<Sisi> sisiDi(long seed, int cx, int cz) {
		ChunkGeneratorStructureState s = state;
		return s != null && s.getLevelSeed() == seed ? sisiDi(s, cx, cz) : List.of();
	}

	/** Every edge owned by road cell {@code (selX, selZ)} ({@code uji jalan peta}, map overlay). */
	public static List<Sisi> sisiSel(ChunkGeneratorStructureState s, int selX, int selZ) {
		return sel(s, selX, selZ).sisi();
	}

	/** Seed-only form of {@link #sisiSel(ChunkGeneratorStructureState, int, int)}. */
	public static List<Sisi> sisiSel(long seed, int selX, int selZ) {
		ChunkGeneratorStructureState s = state;
		return s != null && s.getLevelSeed() == seed ? sisiSel(s, selX, selZ) : List.of();
	}

	/** Road-side posts of kind {@code jenis} ("" = all) whose footprint centre lies in chunk {@code (cx, cz)}. */
	public static List<Pos> posDi(ChunkGeneratorStructureState s, int cx, int cz, String jenis) {
		int selX = Math.floorDiv(cx * 16 + 8, SEL);
		int selZ = Math.floorDiv(cz * 16 + 8, SEL);
		List<Pos> out = new ArrayList<>(1);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (Pos p : sel(s, selX + dx, selZ + dz).pos()) {
					if (p.chunkX() == cx && p.chunkZ() == cz && (jenis.isEmpty() || p.jenis().equals(jenis))) {
						out.add(p);
					}
				}
			}
		}
		return out;
	}

	/** Every post of road cell {@code (selX, selZ)}. */
	public static List<Pos> posSel(ChunkGeneratorStructureState s, int selX, int selZ) {
		return sel(s, selX, selZ).pos();
	}

	/** The edge with id {@code id} owned by a cell within one cell of block {@code (x, z)}, or null. */
	public static @Nullable Sisi sisi(ChunkGeneratorStructureState s, long id, int x, int z) {
		int selX = Math.floorDiv(x, SEL);
		int selZ = Math.floorDiv(z, SEL);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (Sisi e : sel(s, selX + dx, selZ + dz).sisi()) {
					if (e.id() == id) {
						return e;
					}
				}
			}
		}
		return null;
	}

	/** The nearest edge to block {@code (x, z)} within {@code maks} blocks, or null. */
	public static @Nullable Sisi terdekat(ChunkGeneratorStructureState s, int x, int z, int maks) {
		int selX = Math.floorDiv(x, SEL);
		int selZ = Math.floorDiv(z, SEL);
		Sisi best = null;
		double bestD = (double) maks * maks;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (Sisi e : sel(s, selX + dx, selZ + dz).sisi()) {
					double d = e.jarak2(x, z);
					if (d <= bestD) {
						bestD = d;
						best = e;
					}
				}
			}
		}
		return best;
	}

	/**
	 * True when the place behind road end {@code ujung} of edge {@code e} is really born, checked with the world
	 * generator ({@code sumber}): a plan's verdict, or the biome at the start chunk against the set's structures.
	 * Cached per seed and place.
	 */
	public static boolean sah(PutusanTanah.Sumber sumber, ChunkGeneratorStructureState s, Sisi e) {
		return sahUjung(sumber, s, e.dari()) && sahUjung(sumber, s, e.ke());
	}

	private static boolean sahUjung(PutusanTanah.Sumber sumber, ChunkGeneratorStructureState s, SimpulJalan ujung) {
		if (sahSeed != sumber.seed()) {
			SAH.clear();
			sahSeed = sumber.seed();
		}
		long key = tempatKunci(ujung.jenis(), ujung.kunci());
		Boolean known = SAH.get(key);
		if (known != null) {
			return known;
		}
		boolean ok;
		try {
			ok = hitungSah(sumber, s, ujung);
		} catch (RuntimeException ex) {
			Kultivator.LOGGER.warn("Road end check failed for {} {}", ujung.jenis(), ujung.kunci(), ex);
			ok = false;
		}
		SAH.put(key, ok);
		return ok;
	}

	private static boolean hitungSah(PutusanTanah.Sumber sumber, ChunkGeneratorStructureState s, SimpulJalan ujung) {
		Tempat t = tempatOf(s, ujung);
		if (t == null) {
			return false;
		}
		if (t.rencana() != null) {
			Rencana plan = RencanaRegistry.get(t.rencana());
			return plan.simpulSah(sumber, t.gerbang().getFirst());
		}
		if (t.set() == null) {
			return true;
		}
		SimpulJalan g = t.gerbang().getFirst();
		int y = sumber.surface(g.x(), g.z());
		if (sumber.surface(g.x(), g.z()) > sumber.floor(g.x(), g.z()) && !"gua".equals(t.jenis())) {
			return false;
		}
		Holder<Biome> biome = sumber.biome(g.x(), y, g.z());
		for (StructureSet.StructureSelectionEntry entry : t.set().value().structures()) {
			if (entry.structure().value().biomes().contains(biome)) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable Tempat tempatOf(ChunkGeneratorStructureState s, SimpulJalan ujung) {
		int selX = Math.floorDiv(ujung.x(), SEL);
		int selZ = Math.floorDiv(ujung.z(), SEL);
		long key = tempatKunci(ujung.jenis(), ujung.kunci());
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Tempat t = sel(s, selX + dx, selZ + dz).tempat().get(key);
				if (t != null) {
					return t;
				}
			}
		}
		return null;
	}

	/** Forget cached cells (seed change, plan config change, {@code uji jalan peta segar}). */
	public static void lupakan() {
		synchronized (CACHE_SEL) {
			CACHE_SEL.clear();
		}
		SAH.clear();
	}

	// ------------------------------------------------------------------------------------------------ cell plan

	private static Sel sel(ChunkGeneratorStructureState s, int selX, int selZ) {
		Kunci k = new Kunci(s.getLevelSeed(), selX, selZ);
		synchronized (CACHE_SEL) {
			Sel cached = CACHE_SEL.get(k);
			if (cached != null) {
				return cached;
			}
		}
		Sel built;
		try {
			built = bangun(s, selX, selZ);
		} catch (RuntimeException e) {
			Kultivator.LOGGER.warn("Road plan of cell {},{} failed", selX, selZ, e);
			built = new Sel(selX, selZ, List.of(), Map.of(), List.of(), Map.of());
		}
		synchronized (CACHE_SEL) {
			CACHE_SEL.put(k, built);
		}
		return built;
	}

	private static Sel bangun(ChunkGeneratorStructureState s, int selX, int selZ) {
		long seed = s.getLevelSeed();
		List<Tempat> places = tempatSekitar(s, selX, selZ);
		Map<Long, Tempat> byKey = new HashMap<>();
		for (Tempat t : places) {
			byKey.put(t.kunci(), t);
		}
		List<Tempat> main = new ArrayList<>();
		List<Tempat> caves = new ArrayList<>();
		for (Tempat t : places) {
			(t.gua() ? caves : main).add(t);
		}
		// Prim over the non-cave places (distance between nearest gates), then spokes and cave footpaths.
		List<int[]> pairs = prim(main);
		for (int i = 0; i < main.size(); i++) {
			Tempat a = main.get(i);
			if (!"kota_besar".equals(a.jenis())) {
				continue;
			}
			for (int j = 0; j < main.size(); j++) {
				Tempat b = main.get(j);
				if (!"kota".equals(b.jenis()) || jarak(a, b)[0] > JARI_KOTA || ada(pairs, i, j)) {
					continue;
				}
				pairs.add(new int[]{i, j});
			}
		}
		int[] degree = new int[main.size()];
		for (int[] p : pairs) {
			degree[p[0]]++;
			degree[p[1]]++;
		}
		List<Sisi> edges = new ArrayList<>();
		List<Sisi> all = new ArrayList<>();
		for (int[] p : pairs) {
			Tempat a = main.get(p[0]);
			Tempat b = main.get(p[1]);
			int kelas = "kota_besar".equals(a.jenis()) || "kota_besar".equals(b.jenis()) ? SimpulJalan.KEKAISARAN : SimpulJalan.DESA;
			Sisi e = sisiOf(seed, a, b, kelas);
			if (e == null) {
				continue;
			}
			all.add(e);
			if (milikSel(e, selX, selZ)) {
				edges.add(e);
			}
		}
		for (Tempat cave : caves) {
			Tempat best = null;
			double bestD = JARI_GUA;
			for (Tempat t : main) {
				double d = jarak(cave, t)[0];
				if (d < bestD) {
					bestD = d;
					best = t;
				}
			}
			if (best == null) {
				continue;
			}
			Sisi e = sisiOf(seed, best, cave, SimpulJalan.SETAPAK);
			if (e != null && milikSel(e, selX, selZ)) {
				edges.add(e);
			}
		}
		List<Pos> posts = new ArrayList<>();
		for (Sisi e : edges) {
			posSepanjang(seed, e, posts);
		}
		// Junction posts: next to every place of degree >= 3 that is not a town, on its edge with the smallest id.
		for (int i = 0; i < main.size(); i++) {
			Tempat t = main.get(i);
			if (degree[i] < 3 || t.jenis().startsWith("kota")) {
				continue;
			}
			Sisi first = null;
			for (Sisi e : all) {
				if ((e.dari().kunci() == t.gerbang().getFirst().kunci() && e.dari().jenis().equals(t.jenis()))
						|| (e.ke().kunci() == t.gerbang().getFirst().kunci() && e.ke().jenis().equals(t.jenis()))) {
					if (first == null || e.id() < first.id()) {
						first = e;
					}
				}
			}
			if (first == null || !edges.contains(first) || first.panjang() < 500.0D) {
				continue;
			}
			boolean fromStart = first.dari().jenis().equals(t.jenis()) && first.dari().kunci() == t.gerbang().getFirst().kunci();
			String kind = (mix(seed ^ first.id() ^ 0x5E5EL) & 1) == 0 ? "dagang" : "singgah_besar";
			double s0 = fromStart ? 200.0D : first.panjang() - 200.0D;
			Pos p = posDi(seed, first, kind, s0, 0x7A7AL);
			if (p != null) {
				posts.add(p);
			}
		}
		Map<Long, List<Sisi>> perChunk = new HashMap<>();
		for (Sisi e : edges) {
			for (long c : chunkSisi(e)) {
				perChunk.computeIfAbsent(c, k -> new ArrayList<>(1)).add(e);
			}
		}
		return new Sel(selX, selZ, List.copyOf(edges), Map.copyOf(perChunk), List.copyOf(posts), Map.copyOf(byKey));
	}

	/** The places of the 3 x 3 road cells around {@code (selX, selZ)}. */
	private static List<Tempat> tempatSekitar(ChunkGeneratorStructureState s, int selX, int selZ) {
		long seed = s.getLevelSeed();
		List<Tempat> out = new ArrayList<>();
		// place plans (capital gates, themed-city land gates)
		for (String id : RencanaRegistry.ids()) {
			Rencana plan = RencanaRegistry.get(id);
			Map<Long, List<SimpulJalan>> grouped = new LinkedHashMap<>();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (SimpulJalan g : plan.simpul(seed, selX + dx, selZ + dz)) {
						grouped.computeIfAbsent(g.kunci(), k -> new ArrayList<>()).add(g);
					}
				}
			}
			for (List<SimpulJalan> gates : grouped.values()) {
				SimpulJalan g = gates.getFirst();
				out.add(new Tempat(tempatKunci(g.jenis(), g.kunci()), g.jenis(), g.kelas(), List.copyOf(gates), 0, id, null));
			}
		}
		int minX = (selX - 1) * SEL;
		int minZ = (selZ - 1) * SEL;
		int maxX = (selX + 2) * SEL - 1;
		int maxZ = (selZ + 2) * SEL - 1;
		// NPC sect gates (the one sect list, T12)
		if (GerbangPlan.enabled()) {
			for (GerbangPlan.Entry e : GerbangPlan.of(seed).entries()) {
				int x = e.chunkX() * 16 + 8;
				int z = e.chunkZ() * 16 + 8;
				if (x < minX || x > maxX || z < minZ || z > maxZ) {
					continue;
				}
				Holder<StructureSet> set = setOf(s, "kultivator:sekte_gerbang_" + GerbangPlan.BAND_NAMES[e.band()]);
				SimpulJalan g = new SimpulJalan(x, z, "sekte", SimpulJalan.DESA, e.index());
				out.add(new Tempat(tempatKunci("sekte", e.index()), "sekte", SimpulJalan.DESA, List.of(g), 30 + 8 * e.band(), null, set));
			}
		}
		// inhabited structure sets
		for (Holder<StructureSet> set : s.possibleStructureSets()) {
			String id = set.unwrapKey().map(k -> k.identifier().toString()).orElse("");
			Sumber src = null;
			for (Sumber c : SUMBER) {
				if (c.cocok(id)) {
					src = c;
					break;
				}
			}
			if (src == null || !(set.value().placement() instanceof RandomSpreadStructurePlacement placement)) {
				continue;
			}
			int spacing = placement.spacing();
			int gx0 = Math.floorDiv(minX >> 4, spacing);
			int gz0 = Math.floorDiv(minZ >> 4, spacing);
			int gx1 = Math.floorDiv(maxX >> 4, spacing);
			int gz1 = Math.floorDiv(maxZ >> 4, spacing);
			for (int gx = gx0; gx <= gx1; gx++) {
				for (int gz = gz0; gz <= gz1; gz++) {
					ChunkPos c = placement.getPotentialStructureChunk(seed, gx * spacing, gz * spacing);
					int x = c.getMiddleBlockX();
					int z = c.getMiddleBlockZ();
					if (x < minX || x > maxX || z < minZ || z > maxZ || !placement.isStructureChunk(s, c.x(), c.z())) {
						continue;
					}
					long kunci = ((long) c.x() << 32) ^ (c.z() & 0xFFFFFFFFL) ^ ((long) id.hashCode() << 17);
					SimpulJalan g = new SimpulJalan(x, z, src.jenis(), src.kelas(), kunci);
					out.add(new Tempat(tempatKunci(src.jenis(), kunci), src.jenis(), src.kelas(), List.of(g), src.jari(), null, set));
				}
			}
		}
		out.sort((a, b) -> Long.compare(a.kunci(), b.kunci()));
		return out;
	}

	private static @Nullable Holder<StructureSet> setOf(ChunkGeneratorStructureState s, String id) {
		ResourceKey<StructureSet> key = ResourceKey.create(Registries.STRUCTURE_SET, Identifier.parse(id));
		for (Holder<StructureSet> set : s.possibleStructureSets()) {
			if (set.is(key)) {
				return set;
			}
		}
		return null;
	}

	/** Prim's minimum spanning tree over the places (pairs of indices), skipping pairs longer than {@link #SISI_MAKS}. */
	private static List<int[]> prim(List<Tempat> places) {
		int n = places.size();
		List<int[]> out = new ArrayList<>();
		if (n < 2) {
			return out;
		}
		boolean[] in = new boolean[n];
		double[] best = new double[n];
		int[] from = new int[n];
		java.util.Arrays.fill(best, Double.MAX_VALUE);
		java.util.Arrays.fill(from, -1);
		best[0] = 0.0D;
		for (int step = 0; step < n; step++) {
			int u = -1;
			for (int i = 0; i < n; i++) {
				if (!in[i] && (u < 0 || best[i] < best[u])) {
					u = i;
				}
			}
			if (u < 0) {
				break;
			}
			in[u] = true;
			if (from[u] >= 0 && best[u] <= SISI_MAKS) {
				out.add(new int[]{from[u], u});
			}
			for (int v = 0; v < n; v++) {
				if (in[v]) {
					continue;
				}
				double d = jarak(places.get(u), places.get(v))[0];
				if (d < best[v]) {
					best[v] = d;
					from[v] = u;
				}
			}
		}
		return out;
	}

	private static boolean ada(List<int[]> pairs, int a, int b) {
		for (int[] p : pairs) {
			if (p[0] == a && p[1] == b || p[0] == b && p[1] == a) {
				return true;
			}
		}
		return false;
	}

	/** {distance, gate index of a, gate index of b} between the nearest gates of two places. */
	private static double[] jarak(Tempat a, Tempat b) {
		double best = Double.MAX_VALUE;
		int ia = 0;
		int ib = 0;
		for (int i = 0; i < a.gerbang().size(); i++) {
			for (int j = 0; j < b.gerbang().size(); j++) {
				SimpulJalan ga = a.gerbang().get(i);
				SimpulJalan gb = b.gerbang().get(j);
				double d = Math.hypot(ga.x() - gb.x(), ga.z() - gb.z());
				if (d < best) {
					best = d;
					ia = i;
					ib = j;
				}
			}
		}
		return new double[]{best, ia, ib};
	}

	/** The edge between two places: nearest gates, trimmed to the place boxes, displaced and smoothed. */
	private static @Nullable Sisi sisiOf(long seed, Tempat a, Tempat b, int kelas) {
		double[] d = jarak(a, b);
		if (d[0] > SISI_MAKS || d[0] < 32.0D) {
			return null;
		}
		SimpulJalan ga = a.gerbang().get((int) d[1]);
		SimpulJalan gb = b.gerbang().get((int) d[2]);
		double len = d[0];
		double ux = (gb.x() - ga.x()) / len;
		double uz = (gb.z() - ga.z()) / len;
		double trimA = a.jari() > 0 ? a.jari() + JARAK_UJUNG : 0.0D;
		double trimB = b.jari() > 0 ? b.jari() + JARAK_UJUNG : 0.0D;
		if (trimA + trimB > len - 24.0D) {
			return null;
		}
		double ax = ga.x() + ux * trimA;
		double az = ga.z() + uz * trimA;
		double bx = gb.x() - ux * trimB;
		double bz = gb.z() - uz * trimB;
		long id = edgeId(a.kunci(), b.kunci());
		double[][] pts = garis(id, ax, az, bx, bz);
		int[] xs = new int[pts.length];
		int[] zs = new int[pts.length];
		for (int i = 0; i < pts.length; i++) {
			xs[i] = (int) Math.round(pts[i][0]);
			zs[i] = (int) Math.round(pts[i][1]);
		}
		SimpulJalan from = new SimpulJalan(xs[0], zs[0], a.jenis(), kelas, a.gerbang().getFirst().kunci());
		SimpulJalan to = new SimpulJalan(xs[xs.length - 1], zs[zs.length - 1], b.jenis(), kelas, b.gerbang().getFirst().kunci());
		return new Sisi(id, from, to, kelas, xs, zs);
	}

	/** Two levels of midpoint displacement (+-8 %) then two Chaikin passes; endpoints kept. */
	static double[][] garis(long id, double ax, double az, double bx, double bz) {
		List<double[]> pts = new ArrayList<>();
		pts.add(new double[]{ax, az});
		pts.add(new double[]{bx, bz});
		for (int level = 0; level < 2; level++) {
			List<double[]> next = new ArrayList<>();
			for (int i = 0; i + 1 < pts.size(); i++) {
				double[] p = pts.get(i);
				double[] q = pts.get(i + 1);
				double len = Math.hypot(q[0] - p[0], q[1] - p[1]);
				double nx = -(q[1] - p[1]) / Math.max(1.0E-6D, len);
				double nz = (q[0] - p[0]) / Math.max(1.0E-6D, len);
				double off = (unit(id, level * 16L + i) - 0.5D) * 2.0D * 0.08D * len;
				next.add(p);
				next.add(new double[]{(p[0] + q[0]) * 0.5D + nx * off, (p[1] + q[1]) * 0.5D + nz * off});
			}
			next.add(pts.getLast());
			pts = next;
		}
		for (int pass = 0; pass < 2; pass++) {
			List<double[]> next = new ArrayList<>();
			next.add(pts.getFirst());
			for (int i = 0; i + 1 < pts.size(); i++) {
				double[] p = pts.get(i);
				double[] q = pts.get(i + 1);
				next.add(new double[]{p[0] * 0.75D + q[0] * 0.25D, p[1] * 0.75D + q[1] * 0.25D});
				next.add(new double[]{p[0] * 0.25D + q[0] * 0.75D, p[1] * 0.25D + q[1] * 0.75D});
			}
			next.add(pts.getLast());
			pts = next;
		}
		return pts.toArray(new double[0][]);
	}

	/** Road-side posts along one owned edge (milestones, shrines, rest posts, bandit camp). */
	private static void posSepanjang(long seed, Sisi e, List<Pos> out) {
		double len = e.panjang();
		List<Double> milestones = new ArrayList<>();
		for (double s = 500.0D; s < len - 100.0D; s += 500.0D) {
			milestones.add(s);
			Pos p = posDi(seed, e, "tonggak", s, 0x7001L + (long) s);
			if (p != null) {
				out.add(p);
			}
		}
		int k = 1;
		for (double base = 260.0D; base < len - 60.0D; base += 260.0D, k++) {
			double s = base + (unit(e.id() ^ 0x4B71L, k) - 0.5D) * 120.0D;
			if (s < 60.0D || s > len - 60.0D || dekat(milestones, s, 40.0D)) {
				continue;
			}
			Pos p = posDi(seed, e, "kuil_jalan", s, 0x4B00L + k);
			if (p != null) {
				out.add(p);
			}
		}
		if (len >= 1200.0D) {
			k = 1;
			for (double base = 900.0D; base < len - 300.0D; base += 900.0D, k++) {
				double s = base + (unit(e.id() ^ 0x5199L, k) - 0.5D) * 200.0D;
				if (s < 300.0D || s > len - 300.0D) {
					continue;
				}
				Pos p = posDi(seed, e, "singgah", s, 0x5100L + k);
				if (p != null) {
					out.add(p);
				}
			}
		}
		int[] mid = e.titikPada(len * 0.5D);
		double ring = Math.hypot(mid[0], mid[1]);
		if (e.kelas() != SimpulJalan.SETAPAK && ring >= 1000.0D && unit(e.id() ^ 0xBE6AL, 0) < 0.15D && len > 400.0D) {
			double s = len * (0.35D + unit(e.id() ^ 0xBE6AL, 1) * 0.3D);
			Pos p = posDi(seed, e, "kemah_begal", s, 0xBE60L);
			if (p != null) {
				out.add(p);
			}
		}
	}

	/** A post of {@code jenis} beside edge {@code e} at distance {@code s}: off the road on a hashed side, front to the road. */
	private static @Nullable Pos posDi(long seed, Sisi e, String jenis, double s, long salt) {
		int[] at = e.titikPada(s);
		double dx = at[2] / 1000.0D;
		double dz = at[3] / 1000.0D;
		double nx = -dz;
		double nz = dx;
		boolean left = (mix(seed ^ e.id() ^ salt) & 1) == 0;
		double side = left ? 1.0D : -1.0D;
		int radius = UKURAN_POS.getOrDefault(jenis, 4);
		double off = LEBAR[e.kelas()] / 2.0D + 3.0D + radius;
		if ("kemah_begal".equals(jenis)) {
			off = 24.0D + unit(e.id() ^ salt, 7) * 16.0D + radius;
		}
		int x = (int) Math.round(at[0] + nx * off * side);
		int z = (int) Math.round(at[1] + nz * off * side);
		// front (template south, +z) must face the road: direction from the post to the road is -n * side
		double fx = -nx * side;
		double fz = -nz * side;
		int putar = putarKe(fx, fz);
		return new Pos(jenis, x, z, putar, e.id(), (int) Math.round(s));
	}

	/**
	 * The {@code Rotation} ordinal that turns the template's south (+z) toward {@code (fx, fz)}: NONE south,
	 * CLOCKWISE_90 west, CLOCKWISE_180 north, COUNTERCLOCKWISE_90 east (rotation about the origin, as
	 * {@code StructureTemplate.transform}).
	 */
	public static int putarKe(double fx, double fz) {
		if (Math.abs(fz) >= Math.abs(fx)) {
			return fz >= 0.0D ? 0 : 2;
		}
		return fx < 0.0D ? 1 : 3;
	}

	private static boolean dekat(List<Double> list, double s, double r) {
		for (double v : list) {
			if (Math.abs(v - s) < r) {
				return true;
			}
		}
		return false;
	}

	private static boolean milikSel(Sisi e, int selX, int selZ) {
		int mx = (e.xs()[0] + e.xs()[e.xs().length - 1]) / 2;
		int mz = (e.zs()[0] + e.zs()[e.zs().length - 1]) / 2;
		return Math.floorDiv(mx, SEL) == selX && Math.floorDiv(mz, SEL) == selZ;
	}

	/** Every chunk the band of edge {@code e} (half width + 2) touches. */
	public static java.util.Set<Long> chunkSisi(Sisi e) {
		java.util.Set<Long> out = new java.util.HashSet<>();
		double margin = LEBAR[e.kelas()] / 2.0D + 2.0D;
		int[] xs = e.xs();
		int[] zs = e.zs();
		for (int i = 1; i < xs.length; i++) {
			double len = Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
			int steps = Math.max(1, (int) Math.ceil(len / 2.0D));
			for (int t = 0; t <= steps; t++) {
				double x = xs[i - 1] + (xs[i] - xs[i - 1]) * (double) t / steps;
				double z = zs[i - 1] + (zs[i] - zs[i - 1]) * (double) t / steps;
				for (int ox = -1; ox <= 1; ox++) {
					for (int oz = -1; oz <= 1; oz++) {
						out.add(chunkKey(Math.floorDiv((int) Math.floor(x + ox * margin), 16), Math.floorDiv((int) Math.floor(z + oz * margin), 16)));
					}
				}
			}
		}
		return out;
	}

	public static long chunkKey(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}

	/** True when edge {@code e} (its band, half width + 2) touches chunk {@code (cx, cz)}. */
	public static boolean lewatChunk(Sisi e, int cx, int cz) {
		double margin = LEBAR[e.kelas()] / 2.0D + 2.0D;
		double minX = cx * 16 - margin;
		double minZ = cz * 16 - margin;
		double maxX = cx * 16 + 15 + margin;
		double maxZ = cz * 16 + 15 + margin;
		int[] xs = e.xs();
		int[] zs = e.zs();
		for (int i = 1; i < xs.length; i++) {
			if (Math.max(xs[i - 1], xs[i]) < minX || Math.min(xs[i - 1], xs[i]) > maxX
					|| Math.max(zs[i - 1], zs[i]) < minZ || Math.min(zs[i - 1], zs[i]) > maxZ) {
				continue;
			}
			double len = Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
			int steps = Math.max(1, (int) Math.ceil(len / 4.0D));
			for (int t = 0; t <= steps; t++) {
				double x = xs[i - 1] + (xs[i] - xs[i - 1]) * (double) t / steps;
				double z = zs[i - 1] + (zs[i] - zs[i - 1]) * (double) t / steps;
				if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
					return true;
				}
			}
		}
		return false;
	}

	static double segDist2(double px, double pz, double ax, double az, double bx, double bz) {
		double vx = bx - ax;
		double vz = bz - az;
		double l2 = vx * vx + vz * vz;
		double t = l2 < 1.0E-9D ? 0.0D : Math.clamp(((px - ax) * vx + (pz - az) * vz) / l2, 0.0D, 1.0D);
		double dx = ax + vx * t - px;
		double dz = az + vz * t - pz;
		return dx * dx + dz * dz;
	}

	private static long tempatKunci(String jenis, long kunci) {
		return mix(jenis.hashCode() * 0x9E3779B97F4A7C15L ^ kunci);
	}

	private static long edgeId(long a, long b) {
		long lo = Math.min(a, b);
		long hi = Math.max(a, b);
		return mix(lo * 0x632BE59BD9B4E019L ^ hi) & Long.MAX_VALUE;
	}

	static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	static double unit(long id, long salt) {
		return (mix(id * 0x9E3779B97F4A7C15L ^ salt) >>> 11) * 0x1.0p-53;
	}

	private RencanaJalan() {
	}
}
