package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.jalan.RencanaJalan;
import dev.kultivator.dunia.tempat.jalan.SimpulJalan;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Every call of 39C into DESIGN 38 in one place (contracts C39.K2, K7-K10 of 39.6.12). Each method answers the old-town
 * default when 38 has nothing there (a small town without life points, the wilds without roads) or throws inside: 39
 * never depends on which part of 38 is on. Only 38's public APIs are used; no 38 file is touched.
 */
public final class KontrakKota {

	/** Road class of a stage when no road is near: the wilds. */
	public static final int LIAR = 3;
	/** Event factor of a road class (39.6.2): imperial x0.5, village x1.0, path or wilds x1.5. */
	private static final double[] FAKTOR_JALAN = {0.5D, 1.0D, 1.5D, 1.5D};

	/** The town's site of {@code DuniaSites} (its kind tells capital, themed city, small town), or null. */
	public static @Nullable DuniaSites.Site situs(ServerLevel level, long town) {
		BlockPos centre = Towns.centre(level.getServer(), town);
		if (centre == null) {
			return null;
		}
		try {
			for (DuniaSites.Site site : DuniaSites.near(level, centre, 96, "kota")) {
				String kind = site.kind();
				if (kind.equals(Towns.JENIS_KOTA) || kind.equals(Towns.JENIS_ABADI) || kind.equals(Towns.JENIS_KOTA_BESAR)
						|| kind.startsWith("kota_tema/") && !kind.endsWith("reruntuhan_gurun") && !kind.endsWith("pos_pemburu")) {
					return site;
				}
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.debug("Mandiri: town site lookup failed", exception);
		}
		return null;
	}

	/** Where a traveller arrives in a town: 38's arrival point (capital square, themed city gate), else the centre's ground. */
	public static BlockPos tiba(ServerLevel level, long town) {
		BlockPos centre = Towns.centre(level.getServer(), town);
		if (centre == null) {
			return BlockPos.ZERO;
		}
		DuniaSites.Site site = situs(level, town);
		if (site != null) {
			try {
				BlockPos at = TempatApi.arrival(site);
				if (at != null) {
					return at;
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.debug("Mandiri: arrival lookup failed", exception);
			}
		}
		return tanah(level, centre.getX() + 6, centre.getZ() + 6, centre.getY());
	}

	/** A standing spot at column (x, z): 38's ground rule when it knows the place, else the surface (loaded chunks only). */
	public static BlockPos tanah(ServerLevel level, int x, int z, int nearY) {
		try {
			BlockPos spot = TempatApi.groundSpot(level, x, z, nearY);
			if (spot != null) {
				return spot;
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.debug("Mandiri: ground spot failed", exception);
		}
		if (level.hasChunk(x >> 4, z >> 4)) {
			return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		}
		return new BlockPos(x, nearY, z);
	}

	/** The 38 place at {@code pos} (capital district, village, compound) or null. */
	public static @Nullable TempatInfo tempat(ServerLevel level, BlockPos pos) {
		try {
			return TempatApi.tempatDi(level, pos);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	/**
	 * The life points of kind {@code jenis} ({@code lapak}, {@code meja_makan}, {@code kursi}, {@code obrol},
	 * {@code dupa}) of the 38 place at {@code pos}, nearest first; empty for an old town (the visit then uses the town's
	 * role NPCs).
	 */
	public static List<Titik> titik(ServerLevel level, BlockPos pos, String jenis) {
		TempatInfo info = tempat(level, pos);
		if (info == null) {
			return List.of();
		}
		try {
			List<Titik> all = new java.util.ArrayList<>(TempatApi.titik(level, info.situsId(), jenis));
			all.sort(java.util.Comparator.comparingDouble(t -> t.pos().distSqr(pos)));
			return all;
		} catch (RuntimeException exception) {
			return List.of();
		}
	}

	/** C39.K2: the keeper turns to the town body. */
	public static void tatap(@Nullable KultivatorNpc npc, @Nullable Entity sasaran) {
		if (npc == null) {
			return;
		}
		try {
			HidupApi.tatap(npc, sasaran);
		} catch (RuntimeException ignored) {
			// 38NL off: no head turn
		}
	}

	/** C39.K2: the keeper plays a clip (hands over the goods, calls out). */
	public static void klip(@Nullable KultivatorNpc npc, int klip, int prop, int tick) {
		if (npc == null) {
			return;
		}
		try {
			HidupApi.klip(npc, klip, prop, tick);
		} catch (RuntimeException ignored) {
			// 38NL off: no clip
		}
	}

	/** C39.K8: a rumour of the town ({@code mandiri_<jenis>} templates of {@code tempat/kabar/mandiri.json}). */
	public static void kabar(ServerLevel level, long town, String jenis, Object... isian) {
		try {
			DuniaSites.Site site = situs(level, town);
			CeritaApi.kabar(level, site == null ? null : site.id(), "mandiri_" + jenis, isian);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.debug("Mandiri: rumour failed", exception);
		}
	}

	/** The running capital festival or night market of the town, or null. */
	public static @Nullable String acara(ServerLevel level, long town) {
		DuniaSites.Site site = situs(level, town);
		if (site == null) {
			return null;
		}
		try {
			return KotaBesarApi.acaraAktif(site.id());
		} catch (RuntimeException exception) {
			return null;
		}
	}

	/** True while the capital's shops are shut after a lost beast tide. */
	public static boolean tokoTutup(MinecraftServer server, long town) {
		try {
			return KotaBesarApi.tokoTutup(server, town);
		} catch (RuntimeException exception) {
			return false;
		}
	}

	/** C39.K9: the road class at block (x, z): {@link SimpulJalan#KEKAISARAN}, DESA, SETAPAK or {@link #LIAR}. */
	public static int kelasJalan(ServerLevel level, int x, int z) {
		try {
			RencanaJalan.Sisi edge = RencanaJalan.terdekat(RencanaJalan.of(level), x, z, 48);
			return edge == null ? LIAR : Math.max(SimpulJalan.KEKAISARAN, Math.min(SimpulJalan.SETAPAK, edge.kelas()));
		} catch (RuntimeException exception) {
			return LIAR;
		}
	}

	/** The event factor of a road class. */
	public static double faktorJalan(int kelas) {
		return FAKTOR_JALAN[Math.max(0, Math.min(FAKTOR_JALAN.length - 1, kelas))];
	}

	/** The speed factor of a road class: the Imperial road x1.15 (39.6.2). */
	public static double lajuJalan(int kelas) {
		return kelas == SimpulJalan.KEKAISARAN ? 1.15D : 1.0D;
	}

	/** C39.K9: the nearest bandit camp site within {@code r} blocks of {@code pos}, or null. */
	public static @Nullable DuniaSites.Site kemahBegal(ServerLevel level, BlockPos pos, int r) {
		try {
			List<DuniaSites.Site> list = DuniaSites.near(level, pos, r, "pos/kemah_begal");
			return list.isEmpty() ? null : list.get(0);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	/** C39.K9: a road inn or post within {@code r} blocks (overnight 2 NB instead of 4), or null. */
	public static @Nullable DuniaSites.Site posSinggah(ServerLevel level, BlockPos pos, int r) {
		try {
			List<DuniaSites.Site> list = DuniaSites.near(level, pos, r, "pos/singgah");
			return list.isEmpty() ? null : list.get(0);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private KontrakKota() {
	}
}
