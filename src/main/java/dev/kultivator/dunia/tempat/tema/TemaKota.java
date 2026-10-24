package dev.kultivator.dunia.tempat.tema;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.Distrik;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.Putusan;
import dev.kultivator.dunia.tempat.PutusanTanah;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatZones;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.TitikTemplat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime lookups of the themed towns (contract in {@code docs/progres/kota-TK.md} sec 2): which town holds a position,
 * the town of a site id, the live towns, districts and the life points of a town's loaded pieces. Pure plan data plus the
 * shared verdict (no chunk is generated); every answer is a suitable town only.
 * <p>
 * <b>Town id</b> = the site id {@code TempatApi.situsRencana(kandidat, "kota_tema/<tema>")}: the DuniaSites id of kind
 * {@code kota_tema/<tema>} at the candidate centre, the same for every rotation variant, published by the
 * {@code kota_tema/situs} anchor ({@link TemaAnchors}).
 */
public final class TemaKota {

	/**
	 * One born themed town.
	 *
	 * @param tema     the theme
	 * @param calon    its base candidate
	 * @param kandidat its suitable rotation variant
	 * @param situsId  the town id (site id of {@code kota_tema/<tema>})
	 * @param pusat    the central district centre at street level ({@code dasarY})
	 * @param dasarY   street floor height of the place (gunung: of the bottom row)
	 * @param putar    the rotation 0..3
	 */
	public record Kota(Tema tema, RencanaKotaTema.Calon calon, Kandidat kandidat, long situsId, BlockPos pusat, int dasarY, int putar) {

		/** The world block of grid offset {@code (gx, gz)} blocks before rotation from the town centre, at height y. */
		public BlockPos dunia(int gx, int gz, int y) {
			int[] w = RencanaKotaTema.dunia(tema, kandidat, gx, gz);
			return new BlockPos(w[0], y, w[1]);
		}

		/** The centre of district {@code id} at its street level (gunung rows included), or null. */
		public @Nullable BlockPos distrik(String id) {
			for (Distrik d : RencanaKotaTema.distrikOf(tema, kandidat)) {
				if (d.id().equals(id)) {
					return new BlockPos(d.pusatX(), dasarY + d.dy(), d.pusatZ());
				}
			}
			return null;
		}

		/** Every district of the town. */
		public List<Distrik> semuaDistrik() {
			return RencanaKotaTema.distrikOf(tema, kandidat);
		}

		/** The whole-town box (no margin). */
		public BoundingBox kotak() {
			return RencanaKotaTema.kotakKota(tema, kandidat);
		}

		/** True when column {@code (x, z)} lies in the town box. */
		public boolean berisi(int x, int z) {
			BoundingBox b = kotak();
			return x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ();
		}

		/** The town's place rotation. */
		public Rotation rotasi() {
			return Rotation.values()[putar & 3];
		}

		/** The town's display name. */
		public Component nama(long seed) {
			return KotaTemaNames.of(seed, calon);
		}

		/** The site kind {@code kota_tema/<tema>}. */
		public String jenis() {
			return tema.jenisSitus();
		}
	}

	/** Spatial index cell (blocks, power of two): candidates are bucketed by their centre. */
	private static final int SEL_SHIFT = 9;

	private record Indeks(long seed, Map<Long, List<RencanaKotaTema.Calon>> sel, Map<Long, RencanaKotaTema.Calon> situs) {
	}

	private static volatile @Nullable Indeks indeks;
	private static final Map<Long, Kota> KOTA = new ConcurrentHashMap<>();
	private static volatile long kotaSeed = Long.MIN_VALUE;
	/** Live towns: site id -> number of loaded anchors (kept by {@link TemaAnchors}). */
	private static final Map<Long, Integer> HIDUP = new LinkedHashMap<>();

	private static Indeks indeks(long seed) {
		Indeks i = indeks;
		if (i != null && i.seed() == seed) {
			return i;
		}
		synchronized (TemaKota.class) {
			i = indeks;
			if (i != null && i.seed() == seed) {
				return i;
			}
			Map<Long, List<RencanaKotaTema.Calon>> sel = new HashMap<>();
			Map<Long, RencanaKotaTema.Calon> situs = new HashMap<>();
			RencanaKotaTema.Plan plan = RencanaKotaTema.plan(seed);
			for (RencanaKotaTema.Calon c : plan.urutan()) {
				sel.computeIfAbsent(cell(c.x() >> SEL_SHIFT, c.z() >> SEL_SHIFT), k -> new ArrayList<>()).add(c);
				Kandidat any = RencanaKotaTema.varian(c).getFirst();
				situs.put(TempatApi.situsRencana(any, c.tema().jenisSitus()), c);
			}
			i = new Indeks(seed, sel, situs);
			indeks = i;
			return i;
		}
	}

	/** Forgets the cached towns (seed or config change). */
	public static void lupakan() {
		synchronized (TemaKota.class) {
			indeks = null;
			KOTA.clear();
			kotaSeed = Long.MIN_VALUE;
		}
	}

	/** The born town of base candidate {@code c}, or null when it is not suitable. Cached per seed. */
	public static @Nullable Kota dari(PutusanTanah.Sumber sumber, RencanaKotaTema.Calon c) {
		if (kotaSeed != sumber.seed()) {
			synchronized (TemaKota.class) {
				if (kotaSeed != sumber.seed()) {
					KOTA.clear();
					kotaSeed = sumber.seed();
				}
			}
		}
		long key = RencanaKotaTema.kunci(c.tema(), c.index());
		Kota known = KOTA.get(key);
		if (known != null) {
			return known;
		}
		Kandidat k = RencanaKotaTema.layak(sumber, c);
		if (k == null) {
			return null;
		}
		Putusan p = RencanaKotaTema.akhir(sumber, k);
		int[] centre = RencanaKotaTema.pusat(c.tema(), k);
		int dy = c.tema() == Tema.GUNUNG ? RencanaKotaTema.DY_GUNUNG[1] : 0;
		Kota kota = new Kota(c.tema(), c, k, TempatApi.situsRencana(k, c.tema().jenisSitus()), new BlockPos(centre[0], p.dasarY() + dy, centre[1]),
				p.dasarY(), k.putar());
		KOTA.put(key, kota);
		return kota;
	}

	/** The born themed town whose box holds {@code pos} (overworld only), or null. */
	public static @Nullable Kota di(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null || level.dimension() != Level.OVERWORLD) {
			return null;
		}
		PutusanTanah.Sumber sumber = TempatZones.sumber(level);
		Indeks i = indeks(level.getSeed());
		int cx = pos.getX() >> SEL_SHIFT;
		int cz = pos.getZ() >> SEL_SHIFT;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				List<RencanaKotaTema.Calon> list = i.sel().get(cell(cx + dx, cz + dz));
				if (list == null) {
					continue;
				}
				for (RencanaKotaTema.Calon c : list) {
					if (Math.abs(c.x() - pos.getX()) > 320 || Math.abs(c.z() - pos.getZ()) > 320) {
						continue;
					}
					Kota kota = dari(sumber, c);
					if (kota != null && kota.berisi(pos.getX(), pos.getZ())) {
						return kota;
					}
				}
			}
		}
		return null;
	}

	/** The born town with town id {@code situsId}, or null. */
	public static @Nullable Kota dari(MinecraftServer server, long situsId) {
		if (server == null) {
			return null;
		}
		ServerLevel overworld = server.overworld();
		RencanaKotaTema.Calon c = indeks(overworld.getSeed()).situs().get(situsId);
		return c == null ? null : dari(TempatZones.sumber(overworld), c);
	}

	/** The nearest born town of {@code tema} (null = any) within {@code radius} blocks of {@code pos}, or null. */
	public static @Nullable Kota terdekat(ServerLevel level, BlockPos pos, @Nullable Tema tema, int radius) {
		if (level == null || level.dimension() != Level.OVERWORLD) {
			return null;
		}
		PutusanTanah.Sumber sumber = TempatZones.sumber(level);
		Kota best = null;
		double bestSq = (double) radius * radius;
		for (RencanaKotaTema.Calon c : RencanaKotaTema.plan(level.getSeed()).urutan()) {
			if (tema != null && c.tema() != tema) {
				continue;
			}
			double dx = c.x() - pos.getX();
			double dz = c.z() - pos.getZ();
			double d = dx * dx + dz * dz;
			if (d > bestSq) {
				continue;
			}
			Kota kota = dari(sumber, c);
			if (kota != null) {
				bestSq = d;
				best = kota;
			}
		}
		return best;
	}

	/**
	 * The born towns of {@code tema} (null = all) ordered by distance from {@code pos}, at most {@code maks}, within
	 * {@code radius}. Asks the verdict of each candidate in range (cached), so keep the radius sensible (<= 8000).
	 */
	public static List<Kota> sekitar(ServerLevel level, BlockPos pos, @Nullable Tema tema, int radius, int maks) {
		List<Kota> out = new ArrayList<>();
		if (level == null || level.dimension() != Level.OVERWORLD) {
			return out;
		}
		PutusanTanah.Sumber sumber = TempatZones.sumber(level);
		List<RencanaKotaTema.Calon> near = new ArrayList<>();
		for (RencanaKotaTema.Calon c : RencanaKotaTema.plan(level.getSeed()).urutan()) {
			if ((tema == null || c.tema() == tema) && Math.hypot(c.x() - pos.getX(), c.z() - pos.getZ()) <= radius) {
				near.add(c);
			}
		}
		near.sort(java.util.Comparator.comparingDouble(c -> Math.hypot(c.x() - pos.getX(), c.z() - pos.getZ())));
		for (RencanaKotaTema.Calon c : near) {
			Kota kota = dari(sumber, c);
			if (kota != null) {
				out.add(kota);
				if (out.size() >= maks) {
					break;
				}
			}
		}
		return out;
	}

	/** The id of the district whose 96-cell holds {@code pos} in its town, or null. */
	public static @Nullable String distrikDi(ServerLevel level, BlockPos pos) {
		Kota kota = di(level, pos);
		return kota == null ? null : distrikDi(kota, pos);
	}

	/** The id of the district of {@code kota} whose cell (+-48, else +-72) holds {@code pos}, or null. */
	public static @Nullable String distrikDi(Kota kota, BlockPos pos) {
		String near = null;
		for (Distrik d : kota.semuaDistrik()) {
			int ax = Math.abs(d.pusatX() - pos.getX());
			int az = Math.abs(d.pusatZ() - pos.getZ());
			if (ax <= Tema.SEL_DISTRIK / 2 && az <= Tema.SEL_DISTRIK / 2) {
				return d.id();
			}
			if (near == null && ax <= RencanaKotaTema.SETENGAH_DISTRIK && az <= RencanaKotaTema.SETENGAH_DISTRIK) {
				near = d.id();
			}
		}
		return near;
	}

	/**
	 * The life points of the LOADED pieces of {@code kota} of type {@code jenis} ("" = all, {@code "kerja:"} = every work
	 * point). Reads each district's start chunk only when it is loaded; never loads a chunk.
	 */
	public static List<Titik> titik(ServerLevel level, Kota kota, String jenis) {
		List<Titik> out = new ArrayList<>();
		if (level == null || kota == null) {
			return out;
		}
		for (Distrik d : kota.semuaDistrik()) {
			LevelChunk chunk = level.getChunkSource().getChunkNow(d.chunkX(), d.chunkZ());
			if (chunk == null) {
				continue;
			}
			for (StructureStart start : chunk.getAllStarts().values()) {
				if (!start.isValid()) {
					continue;
				}
				for (StructurePiece piece : start.getPieces()) {
					if (!(piece instanceof PoolElementStructurePiece pool) || !(pool.getElement() instanceof SinglePoolElement single)) {
						continue;
					}
					Identifier template;
					try {
						template = single.getTemplateLocation();
					} catch (RuntimeException e) {
						continue;
					}
					if (!Kultivator.MOD_ID.equals(template.getNamespace()) || !template.getPath().startsWith("kota_tema/")) {
						continue;
					}
					for (Titik t : TitikTemplat.dunia(level.getServer(), template, pool.getPosition(), pool.getRotation())) {
						if (jenis.isEmpty() || t.jenis().equals(jenis) || jenis.endsWith(":") && t.jenis().startsWith(jenis)) {
							out.add(t);
						}
					}
				}
			}
		}
		return out;
	}

	// ================================================================================================ live towns

	/** {@link TemaAnchors}: an anchor of town {@code situsId} loaded ({@code +1}) or unloaded ({@code -1}). */
	static synchronized void hitungHidup(long situsId, int delta) {
		int n = HIDUP.getOrDefault(situsId, 0) + delta;
		if (n <= 0) {
			HIDUP.remove(situsId);
		} else {
			HIDUP.put(situsId, n);
		}
	}

	/** Site ids of the towns with at least one loaded anchor now. */
	public static synchronized List<Long> hidupIds() {
		return new ArrayList<>(HIDUP.keySet());
	}

	/** The towns of {@code tema} (null = all) with at least one loaded anchor now. */
	public static List<Kota> hidup(MinecraftServer server, @Nullable Tema tema) {
		List<Kota> out = new ArrayList<>();
		for (long id : hidupIds()) {
			Kota kota = dari(server, id);
			if (kota != null && (tema == null || kota.tema() == tema)) {
				out.add(kota);
			}
		}
		return out;
	}

	/** Server stop: forget the live set. */
	static synchronized void reset() {
		HIDUP.clear();
		lupakan();
	}

	/** Every base candidate of the seed (all themes, global order). */
	public static Collection<RencanaKotaTema.Calon> semuaCalon(long seed) {
		return RencanaKotaTema.plan(seed).urutan();
	}

	private static long cell(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	private TemaKota() {
	}
}
