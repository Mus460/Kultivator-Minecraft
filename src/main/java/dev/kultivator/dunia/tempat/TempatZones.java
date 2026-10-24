package dev.kultivator.dunia.tempat;

import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.kehancuran.ProtectedZones;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protection and safe ground of every DESIGN 38 place (38.D.5, 38.0 rule 11; owner 38W). One
 * {@code ProtectedZones.addZoneProvider} and one {@code DuniaApi.addSafeGround} provider answer from spatial hashes per
 * level, O(1) per call after the first verdict of a place:
 * <ul>
 *   <li>planned places (capital, sect compounds, themed cities): the district boxes of every plan
 *       ({@code Rencana.zonaDistrik}), built once per seed; a box counts only when its place passes the shared terrain
 *       verdict ({@code Rencana.putusan}, computed from the overworld generator exactly as world generation does), so a
 *       candidate that is never born protects nothing. Plans without district boxes fall back to their plain
 *       {@code zona}/{@code zonaAman} lists. Safe ground follows {@code zonaAman} (38.T.2.1: Kota Iblis answers only its
 *       Zona Perjanjian);</li>
 *   <li>places a module publishes at runtime from its anchors ({@link #daftar}): villages ({@code kampung/lonceng},
 *       protect 48-96), road posts (protect 24), cave puzzle seals, kept until the server stops.</li>
 * </ul>
 * Mode Kehancuran of the player himself still cannot break a capital or a village; damage at the zone edge is recorded
 * by 38.R. A provider that throws protects (the {@code ProtectedZones} rule).
 */
public final class TempatZones {

	private static final int CELL_SHIFT = 8; // 256-block hash cells

	/** An immutable spatial hash of boxes (XZ only; y is ignored). */
	public static final class Indeks {

		private final Map<Long, List<BoundingBox>> cells;

		public Indeks(List<BoundingBox> boxes) {
			Map<Long, List<BoundingBox>> map = new HashMap<>();
			for (BoundingBox box : boxes) {
				if (box == null) {
					continue;
				}
				for (int cx = box.minX() >> CELL_SHIFT; cx <= box.maxX() >> CELL_SHIFT; cx++) {
					for (int cz = box.minZ() >> CELL_SHIFT; cz <= box.maxZ() >> CELL_SHIFT; cz++) {
						map.computeIfAbsent(key(cx, cz), k -> new ArrayList<>(2)).add(box);
					}
				}
			}
			this.cells = map;
		}

		/** True when column {@code (x, z)} lies in a box. */
		public boolean berisi(int x, int z) {
			List<BoundingBox> list = cells.get(key(x >> CELL_SHIFT, z >> CELL_SHIFT));
			if (list == null) {
				return false;
			}
			for (BoundingBox b : list) {
				if (x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ()) {
					return true;
				}
			}
			return false;
		}

		public boolean kosong() {
			return cells.isEmpty();
		}
	}

	/**
	 * One planned box: {@code kotak} the box, {@code rencana} its plan, {@code distrik} the district whose verdict
	 * decides whether the box exists (null for plans without district boxes: always counts).
	 */
	public record Masuk(BoundingBox kotak, Rencana rencana, @Nullable Distrik distrik) {

		boolean berisi(int x, int z) {
			return x >= kotak.minX() && x <= kotak.maxX() && z >= kotak.minZ() && z <= kotak.maxZ();
		}
	}

	/** An immutable spatial hash of planned boxes with their district (verdict asked lazily by the caller). */
	public static final class IndeksRencana {

		private final Map<Long, List<Masuk>> cells = new HashMap<>();

		/**
		 * Boxes of every registered plan: {@code jenis} 0 = keep-out ({@code kotak}), 1 = safe ground ({@code aman}),
		 * 2 = the place itself ({@code aman}, else {@code kotak} for districts that are not safe ground).
		 */
		public static IndeksRencana of(long seed, int jenis) {
			IndeksRencana out = new IndeksRencana();
			for (String id : RencanaRegistry.ids()) {
				Rencana plan = RencanaRegistry.get(id);
				List<ZonaRencana> zones = plan.zonaDistrik(seed);
				if (zones.isEmpty()) {
					for (BoundingBox box : jenis == 0 ? plan.zona(seed) : plan.zonaAman(seed)) {
						out.add(new Masuk(box, plan, null));
					}
					continue;
				}
				for (ZonaRencana z : zones) {
					BoundingBox box = jenis == 0 ? z.kotak() : jenis == 1 ? z.aman() : z.aman() != null ? z.aman() : z.kotak();
					if (box != null) {
						out.add(new Masuk(box, plan, z.distrik()));
					}
				}
			}
			return out;
		}

		private void add(Masuk m) {
			BoundingBox box = m.kotak();
			for (int cx = box.minX() >> CELL_SHIFT; cx <= box.maxX() >> CELL_SHIFT; cx++) {
				for (int cz = box.minZ() >> CELL_SHIFT; cz <= box.maxZ() >> CELL_SHIFT; cz++) {
					cells.computeIfAbsent(key(cx, cz), k -> new ArrayList<>(2)).add(m);
				}
			}
		}

		/** Every planned box containing column {@code (x, z)} (before the verdict). */
		public List<Masuk> di(int x, int z) {
			List<Masuk> list = cells.get(key(x >> CELL_SHIFT, z >> CELL_SHIFT));
			if (list == null) {
				return List.of();
			}
			List<Masuk> out = new ArrayList<>(1);
			for (Masuk m : list) {
				if (m.berisi(x, z)) {
					out.add(m);
				}
			}
			return out;
		}

		/** True when column {@code (x, z)} lies in a box whose place passes its verdict from {@code sumber}. */
		public boolean berisiLahir(int x, int z, PutusanTanah.Sumber sumber) {
			List<Masuk> list = cells.get(key(x >> CELL_SHIFT, z >> CELL_SHIFT));
			if (list == null) {
				return false;
			}
			for (Masuk m : list) {
				if (m.berisi(x, z) && (m.distrik() == null || m.rencana().putusan(sumber, m.distrik()).layak())) {
					return true;
				}
			}
			return false;
		}

		public boolean kosong() {
			return cells.isEmpty();
		}
	}

	/** A runtime place box published by a module (village, road post, puzzle seal). */
	public record Dinamis(long situsId, ResourceKey<Level> dim, BoundingBox kotak, boolean aman, String jenis, int kekuatan) {
	}

	private static volatile long planSeed = Long.MIN_VALUE;
	private static volatile IndeksRencana planZona = new IndeksRencana();
	private static volatile IndeksRencana planAman = new IndeksRencana();
	private static volatile IndeksRencana planTempat = new IndeksRencana();
	private static volatile PutusanTanah.Sumber sumber;
	private static volatile ServerLevel sumberLevel;
	private static final Map<Long, Dinamis> DINAMIS = new ConcurrentHashMap<>();
	private static volatile Map<ResourceKey<Level>, Indeks[]> dinamisIndeks = Map.of();
	private static boolean registered;

	/** From {@code TempatSpine.init()}. */
	public static void init() {
		if (registered) {
			return;
		}
		registered = true;
		ProtectedZones.addZoneProvider(TempatZones::protects);
		DuniaApi.addSafeGround(TempatZones::safe);
	}

	/**
	 * Adds or replaces a runtime place box (village, road post, puzzle seal) under its site id. {@code aman} also makes
	 * it safe ground (no ambush, no enemy spawn).
	 */
	public static void daftar(long situsId, ResourceKey<Level> dim, BoundingBox kotak, boolean aman) {
		daftar(situsId, dim, kotak, aman, "", 0);
	}

	/** {@link #daftar(long, ResourceKey, BoundingBox, boolean)} with the place kind and strength for {@code TempatApi}. */
	public static void daftar(long situsId, ResourceKey<Level> dim, BoundingBox kotak, boolean aman, String jenis, int kekuatan) {
		DINAMIS.put(situsId, new Dinamis(situsId, dim, kotak, aman, jenis == null ? "" : jenis, kekuatan));
		rebuildDynamic();
	}

	/** Removes a runtime place box. */
	public static void hapus(long situsId) {
		if (DINAMIS.remove(situsId) != null) {
			rebuildDynamic();
		}
	}

	/** The runtime boxes of {@code dim} containing column {@code (x, z)}. */
	public static List<Dinamis> dinamisDi(ResourceKey<Level> dim, int x, int z) {
		List<Dinamis> out = new ArrayList<>(1);
		for (Dinamis d : DINAMIS.values()) {
			BoundingBox b = d.kotak();
			if (d.dim() == dim && x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ()) {
				out.add(d);
			}
		}
		return out;
	}

	/** Every runtime box (for {@code TempatApi.dekat} and {@code uji tempat cek}). */
	public static List<Dinamis> semuaDinamis() {
		return List.copyOf(DINAMIS.values());
	}

	/** Forget everything (server stop) and rebuild plan zones on the next query. */
	public static void reset() {
		DINAMIS.clear();
		dinamisIndeks = Map.of();
		planSeed = Long.MIN_VALUE;
		sumber = null;
		sumberLevel = null;
	}

	/** Rebuild the planned indexes on the next query (a plan's config changed). */
	public static void lupakanRencana() {
		planSeed = Long.MIN_VALUE;
		TempatKeepOut.lupakan();
	}

	/** True when {@code pos} is inside a protected 38 place. */
	public static boolean protects(ServerLevel level, BlockPos pos) {
		if (!TempatConfig.get().aktif) {
			return false;
		}
		if (level.dimension() == Level.OVERWORLD) {
			ensurePlans(level.getSeed());
			if (planZona.berisiLahir(pos.getX(), pos.getZ(), sumber(level))) {
				return true;
			}
		}
		Indeks[] dyn = dinamisIndeks.get(level.dimension());
		return dyn != null && dyn[0].berisi(pos.getX(), pos.getZ());
	}

	/** Safe ground (38.0 rule 11): capitals, villages, clan villages, road posts, front courts of Lurus/Netral compounds. */
	public static boolean safe(ServerLevel level, BlockPos pos) {
		if (!TempatConfig.get().aktif) {
			return false;
		}
		if (level.dimension() == Level.OVERWORLD) {
			ensurePlans(level.getSeed());
			if (planAman.berisiLahir(pos.getX(), pos.getZ(), sumber(level))) {
				return true;
			}
		}
		Indeks[] dyn = dinamisIndeks.get(level.dimension());
		return dyn != null && dyn[1].berisi(pos.getX(), pos.getZ());
	}

	/** True when column {@code (x, z)} of {@code level} is inside any 38 place box (38.0 rule 12 ground search). */
	public static boolean inside(ServerLevel level, int x, int z) {
		if (level.dimension() == Level.OVERWORLD) {
			ensurePlans(level.getSeed());
			if (planTempat.berisiLahir(x, z, sumber(level))) {
				return true;
			}
		}
		Indeks[] dyn = dinamisIndeks.get(level.dimension());
		return dyn != null && dyn[0].berisi(x, z);
	}

	/** The planned place boxes (with district) containing column {@code (x, z)} of the overworld, before the verdict. */
	public static List<Masuk> rencanaDi(ServerLevel overworld, int x, int z) {
		ensurePlans(overworld.getSeed());
		return planTempat.di(x, z);
	}

	/**
	 * The verdict source of the server's overworld (generator, random state, biome resolver), made once per server.
	 * Same values as world generation (DESIGN 38.B.2.4).
	 */
	public static PutusanTanah.Sumber sumber(ServerLevel level) {
		ServerLevel overworld = level.getServer().overworld();
		PutusanTanah.Sumber s = sumber;
		if (s == null || sumberLevel != overworld) {
			s = PutusanTanah.Sumber.of(overworld);
			sumber = s;
			sumberLevel = overworld;
		}
		return s;
	}

	private static void ensurePlans(long seed) {
		if (planSeed == seed) {
			return;
		}
		synchronized (TempatZones.class) {
			if (planSeed == seed) {
				return;
			}
			planZona = IndeksRencana.of(seed, 0);
			planAman = IndeksRencana.of(seed, 1);
			planTempat = IndeksRencana.of(seed, 2);
			planSeed = seed;
		}
	}

	private static synchronized void rebuildDynamic() {
		Map<ResourceKey<Level>, List<BoundingBox>> all = new HashMap<>();
		Map<ResourceKey<Level>, List<BoundingBox>> safe = new HashMap<>();
		for (Dinamis d : DINAMIS.values()) {
			all.computeIfAbsent(d.dim(), k -> new ArrayList<>()).add(d.kotak());
			if (d.aman()) {
				safe.computeIfAbsent(d.dim(), k -> new ArrayList<>()).add(d.kotak());
			}
		}
		Map<ResourceKey<Level>, Indeks[]> next = new HashMap<>();
		for (Map.Entry<ResourceKey<Level>, List<BoundingBox>> e : all.entrySet()) {
			next.put(e.getKey(), new Indeks[]{new Indeks(e.getValue()), new Indeks(safe.getOrDefault(e.getKey(), List.of()))});
		}
		dinamisIndeks = Map.copyOf(next);
	}

	private static long key(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}

	private TempatZones() {
	}
}
