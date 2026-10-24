package dev.kultivator.dunia.tempat.tema.air;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.tema.KotaTemaData;
import dev.kultivator.dunia.tempat.tema.Tema;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The live Kota Air towns of this server session (DESIGN 38.T.5) and the shared helpers of the theme package: the town
 * frame (city-local plan coordinates of {@code tools/gen_tempat_tema_air.py} to world coordinates, whatever the town's
 * rotation), the water surface height and the persistent state.
 * <p>
 * A town becomes live when one of its {@code kota_tema/*} anchors of this theme loads ({@link AirAnchors}); it is keyed
 * by its town id {@link Hidup#situs} (the {@code kota_tema/air} site id of {@link TemaKota}: calendar, {@code TemaNet},
 * {@code KotaTemaData.kota}, resident schedule) and knows its Nama Baik town {@link Hidup#town} ({@code kota/kota_tema}
 * of the 34 town system, from {@code Towns.townOf} of its anchors).
 * <p>
 * City-local coordinates: x east, z south before rotation, (0, 0) = the centre of district {@code danau_lentera}; a
 * value {@code l} is continuous (block {@code b} spans {@code b .. b + 1}), so the canal centre line between blocks 47
 * and 48 is 48.0. Template y = 4 (the street floor) lies at world {@code dasarY - 1}; the canal water top block (template
 * y = 3) at {@code dasarY - 2}, so a boat floats at {@link #permukaan}.
 * <p>
 * State in {@code KotaTemaData.bagian("air")}: per town {@link #kota} and per player {@link #pemain}. New worlds only.
 */
public final class AirKota {

	public static final String TEMA = "air";
	public static final String JENIS = "kota_tema/air";
	/** Half side of the walled square (3 x 96 / 2 = 144 plus the 8-block wall band). */
	public static final int SETENGAH_TEMBOK = 152;
	/** A position this far from the centre still belongs to the town. */
	public static final int JANGKAU_KOTA = 230;

	/** One loaded canal town. */
	public static final class Hidup {
		public final long situs;
		public final TemaKota.Kota kota;
		public long town;
		/** Loaded anchors of this town by what ({@code taman}, {@code sutra}, ...). */
		public final Map<String, List<DuniaAnchorEntity>> jangkar = new LinkedHashMap<>();
		private final double ox;
		private final double oz;
		private final int exX;
		private final int exZ;
		private final int ezX;
		private final int ezZ;

		Hidup(TemaKota.Kota kota) {
			this.kota = kota;
			this.situs = kota.situsId();
			BlockPos o = kota.dunia(0, 0, 0);
			BlockPos x1 = kota.dunia(1, 0, 0);
			BlockPos z1 = kota.dunia(0, 1, 0);
			this.ox = o.getX() + 0.5D;
			this.oz = o.getZ() + 0.5D;
			this.exX = x1.getX() - o.getX();
			this.exZ = x1.getZ() - o.getZ();
			this.ezX = z1.getX() - o.getX();
			this.ezZ = z1.getZ() - o.getZ();
		}

		/** World position of the city-local continuous point (lx, lz) at world height y. */
		public Vec3 dunia(double lx, double lz, double y) {
			double a = lx - 0.5D;
			double b = lz - 0.5D;
			return new Vec3(ox + exX * a + ezX * b, y, oz + exZ * a + ezZ * b);
		}

		/** City-local continuous coordinates {x, z} of a world position (inverse of {@link #dunia}). */
		public double[] lokal(Vec3 at) {
			double dx = at.x - ox;
			double dz = at.z - oz;
			// the basis is a rotation (orthonormal, integer): inverse = transpose
			double a = exX * dx + exZ * dz;
			double b = ezX * dx + ezZ * dz;
			return new double[] {a + 0.5D, b + 0.5D};
		}

		/** The world yaw of a city-local heading (degrees, Minecraft convention: 0 = south, 90 = west). */
		public float yaw(double dlx, double dlz) {
			double wx = exX * dlx + ezX * dlz;
			double wz = exZ * dlx + ezZ * dlz;
			return (float) (Math.toDegrees(Math.atan2(-wx, wz)));
		}

		/** The world height of the street floor standing level. */
		public int lantai() {
			return kota.dasarY();
		}

		/** The y a boat floats at (water top block {@code dasarY - 2}, a little sunk). */
		public double permukaan() {
			return kota.dasarY() - 1.15D;
		}

		/** The town centre (district danau_lentera) at street level. */
		public BlockPos pusat() {
			return kota.pusat();
		}

		/** The loaded anchors of one kind (empty list when none). */
		public List<DuniaAnchorEntity> jangkar(String what) {
			List<DuniaAnchorEntity> list = jangkar.get(what);
			if (list == null) {
				return List.of();
			}
			list.removeIf(DuniaAnchorEntity::isRemoved);
			return list;
		}

		/** The first loaded anchor of one kind, or null. */
		public @Nullable DuniaAnchorEntity satu(String what) {
			List<DuniaAnchorEntity> list = jangkar(what);
			return list.isEmpty() ? null : list.getFirst();
		}

		/** True inside the walls, whatever the rotation (the walled town is a square). */
		public boolean dalamTembok(BlockPos pos) {
			return Math.abs(pos.getX() - pusat().getX()) <= SETENGAH_TEMBOK && Math.abs(pos.getZ() - pusat().getZ()) <= SETENGAH_TEMBOK;
		}

		/** Horizontal distance from the centre. */
		public double jarak(Vec3 at) {
			double dx = at.x - (pusat().getX() + 0.5D);
			double dz = at.z - (pusat().getZ() + 0.5D);
			return Math.sqrt(dx * dx + dz * dz);
		}

		/** The district id at a world position, or null. */
		public @Nullable String distrik(BlockPos pos) {
			return TemaKota.distrikDi(kota, pos);
		}
	}

	private static final Map<Long, Hidup> KOTA = new LinkedHashMap<>();

	private record Cache(long tick, List<dev.kultivator.dunia.tempat.Titik> titik) {
	}

	/** Life points of the loaded pieces per town and kind, refreshed every minute (the read walks structure starts). */
	private static final Map<String, Cache> TITIK = new java.util.HashMap<>();
	private static final long TITIK_SEGAR = 1200L;

	/** The life points of kind {@code jenis} of the loaded pieces of a town (cached for a minute). */
	public static List<dev.kultivator.dunia.tempat.Titik> titik(ServerLevel level, Hidup kota, String jenis) {
		String key = kota.situs + "/" + jenis;
		long now = level.getGameTime();
		Cache c = TITIK.get(key);
		if (c == null || now - c.tick > TITIK_SEGAR || c.titik.isEmpty() && now - c.tick > 200L) {
			c = new Cache(now, List.copyOf(TemaKota.titik(level, kota.kota, jenis)));
			TITIK.put(key, c);
		}
		return c.titik;
	}

	// ================================================================== registry

	/** Registers the anchor's town; null when the anchor does not stand in a Kota Air. */
	static @Nullable Hidup daftar(ServerLevel level, DuniaAnchorEntity anchor) {
		TemaKota.Kota born = TemaKota.di(level, anchor.blockPosition());
		if (born == null || born.tema() != Tema.AIR) {
			return null;
		}
		Hidup kota = KOTA.computeIfAbsent(born.situsId(), k -> new Hidup(born));
		if (kota.town == 0L) {
			long town = Towns.townOf(anchor);
			if (town != 0L) {
				kota.town = town;
			}
		}
		List<DuniaAnchorEntity> list = kota.jangkar.computeIfAbsent(anchor.what(), k -> new ArrayList<>());
		if (!list.contains(anchor)) {
			list.add(anchor);
		}
		return kota;
	}

	/** Forgets one anchor (unload); the town stays known. */
	static void lupakan(DuniaAnchorEntity anchor) {
		for (Hidup kota : KOTA.values()) {
			List<DuniaAnchorEntity> list = kota.jangkar.get(anchor.what());
			if (list != null) {
				list.remove(anchor);
			}
		}
	}

	static void reset() {
		KOTA.clear();
		TITIK.clear();
	}

	/** Every town seen this session. */
	public static Collection<Hidup> semua() {
		return Collections.unmodifiableCollection(KOTA.values());
	}

	/** The live town with this place id, or null. */
	public static @Nullable Hidup cari(long situs) {
		return KOTA.get(situs);
	}

	/** The live town whose centre is nearest {@code at} within {@code range}, or null. */
	public static @Nullable Hidup terdekat(ServerLevel level, Vec3 at, double range) {
		if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
			return null;
		}
		Hidup best = null;
		double bestD = range;
		for (Hidup kota : KOTA.values()) {
			double d = kota.jarak(at);
			if (d <= bestD) {
				bestD = d;
				best = kota;
			}
		}
		return best;
	}

	/** The live town {@code player} stands in, or null. */
	public static @Nullable Hidup di(ServerPlayer player) {
		return player.level() instanceof ServerLevel level ? di(level, player.blockPosition()) : null;
	}

	/**
	 * The town around a position, or null: a live one, else the born Kota Air whose box holds {@code pos} (registered now;
	 * the market and the canal houses have no anchor of this theme).
	 */
	public static @Nullable Hidup di(ServerLevel level, BlockPos pos) {
		Hidup near = terdekat(level, Vec3.atCenterOf(pos), JANGKAU_KOTA);
		if (near != null) {
			return near;
		}
		if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
			return null;
		}
		TemaKota.Kota born = TemaKota.di(level, pos);
		if (born == null || born.tema() != Tema.AIR) {
			return null;
		}
		return KOTA.computeIfAbsent(born.situsId(), k -> new Hidup(born));
	}

	// ================================================================== persistent state

	public static CompoundTag bagian(MinecraftServer server) {
		return KotaTemaData.get(server).bagian(TEMA);
	}

	public static CompoundTag kota(MinecraftServer server, long situs) {
		return KotaTemaData.get(server).kota(TEMA, situs);
	}

	/** Per-player state ({@code bagian("air")["p_<uuid>"]}), created on first use. */
	public static CompoundTag pemain(MinecraftServer server, UUID player) {
		CompoundTag part = bagian(server);
		String key = "p_" + player;
		CompoundTag tag = part.getCompoundOrEmpty(key);
		if (!part.contains(key)) {
			part.put(key, tag);
		}
		return tag;
	}

	public static void ubah(MinecraftServer server) {
		KotaTemaData.get(server).ubah();
	}

	/** The world day (overworld clock / 24000). */
	public static long hari(ServerLevel level) {
		return TemaKalender.hari(level);
	}

	/** Time of day 0-23999. */
	public static long jam(ServerLevel level) {
		return TemaKalender.jam(level);
	}

	/** The hour 0-23 of the 38.N.3 schedule (tick 0 = 06:00). */
	public static int jamHari(ServerLevel level) {
		return (int) ((jam(level) / 1000L + 6L) % 24L);
	}

	/** Nama Baik of a player in the town (0 without a reputation town). */
	public static int namaBaik(ServerPlayer player, Hidup kota) {
		long town = kota.town != 0L ? kota.town : kota.situs;
		return KotaData.get(player.level().getServer()).reputation(town, player.getUUID());
	}

	/** Adds Nama Baik in the town; returns the new value. */
	public static int tambahNamaBaik(ServerPlayer player, Hidup kota, int delta) {
		long town = kota.town != 0L ? kota.town : kota.situs;
		return KotaData.get(player.level().getServer()).addReputation(town, player.getUUID(), delta);
	}

	/** A stable 64-bit mix (deterministic per seed, place and salt). */
	public static long hash(long a, long b, long salt) {
		long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		h *= 0x94D049BB133111EBL;
		return h ^ (h >>> 32);
	}

	/** {@link #hash} as a double in [0, 1). */
	public static double acak(long a, long b, long salt) {
		return (hash(a, b, salt) >>> 11) * 0x1.0p-53;
	}

	/** True while the theme's behaviour runs. */
	public static boolean aktif() {
		return TemaConfig.aktif(Tema.AIR);
	}

	public static TemaConfig.Air config() {
		return TemaConfig.air();
	}

	private AirKota() {
	}
}
